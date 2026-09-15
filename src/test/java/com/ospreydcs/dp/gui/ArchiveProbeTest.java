package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.result.QueryPvStatsApiResult;
import com.ospreydcs.dp.grpc.v1.query.QueryPvStatsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
