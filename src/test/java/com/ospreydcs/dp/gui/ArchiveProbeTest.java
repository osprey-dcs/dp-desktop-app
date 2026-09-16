package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.result.QueryPvStatsApiResult;
import com.ospreydcs.dp.grpc.v1.query.QueryPvStatsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The launch probe's reading of a result, and in particular its failure policy.
 *
 * <p>The probe answers "does the archive hold anything worth exploring", which #4 made a different
 * question from "did this session ingest".  {@code archiveHasDataFrom()} is static precisely so the
 * policy is assertable without a service ecosystem.
 */
public class ArchiveProbeTest {

    private static QueryPvStatsApiResult resultWithPvCount(int pvCount) {
        final QueryPvStatsResponse.StatsResult.Builder stats =
                QueryPvStatsResponse.StatsResult.newBuilder();
        for (int i = 0; i < pvCount; i++) {
            stats.addPvStats(
                    QueryPvStatsResponse.StatsResult.PvStats.newBuilder()
                            .setPvName("PROBE:PV:" + i)
                            .build());
        }
        return new QueryPvStatsApiResult(
                QueryPvStatsResponse.newBuilder().setStatsResult(stats).build());
    }

    @Test
    @DisplayName("a populated archive reports data")
    public void testPopulatedArchive() {
        assertTrue(DpApplication.archiveHasDataFrom(resultWithPvCount(3)));
    }

    /**
     * The half that keeps the Explore menu honest on a genuinely empty demo archive.  Without it,
     * a probe hardwired to true would pass every other test here while enabling eight views that
     * have nothing to show.
     */
    @Test
    @DisplayName("an empty archive reports no data")
    public void testEmptyArchive() {
        assertFalse(DpApplication.archiveHasDataFrom(resultWithPvCount(0)));
    }

    /**
     * THE FAILURE POLICY, and it is deliberately not the intuitive answer.
     *
     * <p>A probe that cannot reach the service must assume there IS data.  Assuming none disables
     * every Explore view over an archive that may be full -- reproducing the bug the probe exists
     * to fix, precisely when the system is already unhealthy, and leaving the user no way to reach
     * their data. Assuming data at worst opens a view that reports its own emptiness.
     */
    @Test
    @DisplayName("a failed probe assumes the archive HAS data")
    public void testFailedProbeAssumesData() {
        assertTrue(
                DpApplication.archiveHasDataFrom(
                        new QueryPvStatsApiResult(true, "service unavailable")),
                "a failed probe must not disable the Explore menu -- the wrong guess has to be the "
                        + "recoverable one");
    }

    @Test
    @DisplayName("a null result assumes the archive HAS data")
    public void testNullResultAssumesData() {
        assertTrue(DpApplication.archiveHasDataFrom(null));
    }

    // ---- the bound on how long the probe may take ----

    /**
     * THE REASON THE PROBE IS BOUNDED AT ALL.
     *
     * <p>The probe runs from {@code DpApplication.init()}, which JavaFX calls before {@code start()}
     * -- so there is no window yet, and anything slow here is a blank screen with no feedback.  The
     * underlying call cannot bound itself: {@code queryPvStats} goes through dp-service's
     * {@code ApiResponseObserverBase.await()}, whose timeout is <b>60 seconds</b>.  A wedged query
     * service, which the #4 manual verification actually encountered, would therefore hold the
     * launch for a full minute.
     *
     * <p>The probe here never returns, so only a client-side bound can end the wait -- a probe that
     * completed on its own could not distinguish a working bound from a fast query.
     */
    @Test
    @DisplayName("a probe that never answers is abandoned, and assumes the archive HAS data")
    public void testProbeThatNeverAnswersIsBounded() throws Exception {

        final CountDownLatch released = new CountDownLatch(1);
        final long startNanos = System.nanoTime();

        try {
            assertTrue(
                    DpApplication.probeArchiveWithin(
                            () -> {
                                try {
                                    // Far longer than any bound under test; only the timeout ends it.
                                    released.await(60, TimeUnit.SECONDS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                return resultWithPvCount(0);
                            },
                            1),
                    "a probe that does not answer must assume data, not report an empty archive");

            final long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
            assertTrue(elapsedMillis < 15_000,
                    "the probe must give up at its own bound rather than waiting out the 60-second "
                            + "await inside the client; took " + elapsedMillis + "ms");
        } finally {
            released.countDown();
        }
    }

    /**
     * The other half, which stops the bound from being vacuous: a probe that DOES answer in time is
     * still read for its actual result.  A bound implemented as "always return true" would pass the
     * test above and silently enable Explore over an empty archive.
     */
    @Test
    @DisplayName("a probe that answers in time is still read for its result")
    public void testProbeThatAnswersInTimeIsUsed() {
        assertFalse(
                DpApplication.probeArchiveWithin(() -> resultWithPvCount(0), 5),
                "a timely empty result must still report an empty archive");
        assertTrue(DpApplication.probeArchiveWithin(() -> resultWithPvCount(2), 5));
    }

    /**
     * A probe that throws is the transport-failure case, and answers like every other failure.
     */
    @Test
    @DisplayName("a probe that throws assumes the archive HAS data")
    public void testProbeThatThrowsAssumesData() {
        assertTrue(DpApplication.probeArchiveWithin(
                () -> { throw new RuntimeException("channel unavailable"); }, 5));
    }

    /**
     * The probe runs off the calling thread, which is what lets the bound be enforced at all -- a
     * probe invoked inline could not be abandoned.  Pinned because a refactor back to a direct call
     * would still pass the result-reading tests above.
     */
    @Test
    @DisplayName("the probe does not run on the calling thread")
    public void testProbeRunsOffTheCallingThread() {
        final Thread caller = Thread.currentThread();
        final AtomicBoolean ranOnCaller = new AtomicBoolean(false);
        DpApplication.probeArchiveWithin(
                () -> {
                    ranOnCaller.set(Thread.currentThread() == caller);
                    return resultWithPvCount(1);
                },
                5);
        assertFalse(ranOnCaller.get(),
                "the probe must run off the calling thread, or its timeout cannot be enforced");
    }
}
