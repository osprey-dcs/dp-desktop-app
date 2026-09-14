package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.QueryClient;
import com.ospreydcs.dp.client.result.QuerySamplesApiResult;
import com.ospreydcs.dp.grpc.v1.common.DataColumn;
import com.ospreydcs.dp.grpc.v1.common.DataValue;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.grpc.v1.common.TimestampList;
import com.ospreydcs.dp.grpc.v1.query.ColumnTable;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two bounds on the Query API V2 paging loop: the display cap, and cancellation.
 *
 * <p>Both protect a failure that is silent rather than loud. The loop follows resume tokens until
 * the server stops issuing them, so an unbounded selection -- which the V2 pattern and metadata
 * arms made reachable, since an all-empty metadata query matches every PV in the archive -- would
 * otherwise accumulate without end into an ObservableList on the FX thread, with a "Cancel" that
 * set a status message and stopped nothing.
 *
 * <p>What is asserted is deliberately not just the final row count. A cap that stopped the loop but
 * reported a bare total would look identical, from the table alone, to a query that genuinely had
 * nothing more to return -- so every test here also asserts that truncation is REPORTED.
 */
class DataExploreQueryBoundsTest {

    private static final long AWAIT_SECONDS = 20;

    @BeforeAll
    static void startToolkit() throws Exception {
        FxToolkitSupport.ensureStarted();
    }

    /** One page carrying {@code rowCount} rows for a single PV. */
    private static ColumnTable page(int rowCount, int startSecond) {
        final TimestampList.Builder timestamps = TimestampList.newBuilder();
        final DataColumn.Builder column = DataColumn.newBuilder().setName("PV:A");
        for (int i = 0; i < rowCount; i++) {
            timestamps.addTimestamps(
                    Timestamp.newBuilder().setEpochSeconds(startSecond + i).setNanoseconds(0));
            column.addDataValues(DataValue.newBuilder().setDoubleValue(i));
        }
        return ColumnTable.newBuilder()
                .setTimestampList(timestamps)
                .addDataColumns(column)
                .build();
    }

    /**
     * Serves pages endlessly, always returning a next-page token.
     *
     * <p>Endless on purpose: a fake that eventually stopped on its own could not distinguish a
     * working cap from a loop that simply ran out of data. Here, only the client bound can end it,
     * so a regression that removes the cap hangs the test rather than passing it.
     */
    private static final class EndlessPagesApplication extends DpApplication {
        private final int rowsPerPage;
        final AtomicInteger pagesServed = new AtomicInteger();
        /** Released once a page has been served, so a test can cancel mid-query deterministically. */
        final CountDownLatch firstPageServed = new CountDownLatch(1);
        /**
         * When true, every page costs a short delay.
         *
         * <p>Needed because an in-memory fake serves 50,000 single-row pages in a fraction of a
         * second, so without it even a one-row page reaches the display cap before a cancel can be
         * observed -- and a cancellation test would then pass against a cancel() that does nothing,
         * which is exactly how the first version of this test fooled itself.
         */
        volatile boolean slowPages = false;

        EndlessPagesApplication(int rowsPerPage) {
            this.rowsPerPage = rowsPerPage;
        }

        @Override
        public QuerySamplesApiResult querySamples(
                QueryClient.PvSelectorParams pvSelector,
                List<QueryClient.ConfigurationCriterion> configurationCriteria,
                QueryClient.SampleStatusSelectorParams sampleStatusSelector,
                Instant beginTime,
                Instant endTime,
                String pageToken
        ) {
            final int served = pagesServed.incrementAndGet();
            firstPageServed.countDown();
            if (slowPages) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new QuerySamplesApiResult(
                    page(rowsPerPage, served * rowsPerPage), "token-" + served);
        }
    }

    private static DataExploreViewModel submittableViewModel(DpApplication application) {
        final DataExploreViewModel viewModel = new DataExploreViewModel();
        viewModel.queryBeginDateProperty().set(LocalDate.of(2026, 1, 1));
        viewModel.queryEndDateProperty().set(LocalDate.of(2026, 1, 2));
        viewModel.addPvName("PV:A");
        viewModel.setDpApplication(application);
        return viewModel;
    }

    /** Submits and waits for isQuerying to go true then false. */
    private static void runQueryToCompletion(DataExploreViewModel viewModel) throws Exception {
        final CountDownLatch finished = new CountDownLatch(1);
        FxToolkitSupport.runOnFxThread(() -> {
            viewModel.isQueryingProperty().addListener((obs, was, is) -> {
                if (was && !is) {
                    finished.countDown();
                }
            });
            viewModel.submitQuery();
        });
        assertTrue(finished.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "the query never finished -- an unbounded paging loop would hang exactly here");
    }

    @Test
    @DisplayName("the paging loop stops at the display cap rather than following tokens forever")
    void pagingStopsAtTheDisplayCap() throws Exception {
        // Pages large enough that the cap is reached in a handful of them, so the test is quick
        // while still crossing the boundary mid-page.
        final int rowsPerPage = 7_000;
        final EndlessPagesApplication application = new EndlessPagesApplication(rowsPerPage);
        final DataExploreViewModel viewModel = FxToolkitSupport.callOnFxThread(
                () -> submittableViewModel(application));

        runQueryToCompletion(viewModel);

        FxToolkitSupport.runOnFxThread(() -> {
            assertEquals(DataExploreViewModel.MAX_DISPLAYED_ROWS,
                    viewModel.totalRowsLoadedProperty().get(),
                    "the loop must stop at exactly the cap, trimming the page that crosses it");
            assertEquals(DataExploreViewModel.MAX_DISPLAYED_ROWS, viewModel.getTableData().size(),
                    "the table must hold what the count claims it holds");

            // The count alone cannot say whether more data existed; without this the cap would be
            // indistinguishable from a query that had nothing more to return.
            assertTrue(viewModel.isResultsTruncated(),
                    "a capped result must be REPORTED as capped, not stated as a total");
            assertTrue(viewModel.describeResult().contains("of more"),
                    "the completion message must say the result is a prefix: "
                            + viewModel.describeResult());
        });
    }

    @Test
    @DisplayName("cancelling actually stops the loop, keeping the rows already displayed")
    void cancelStopsTheRunningQuery() throws Exception {
        // ONE row per page, so the 50,000-row cap is unreachable within this test's lifetime.
        // That isolation is the whole point: with larger pages the loop ends at the cap whether or
        // not cancel works, and an earlier version of this test passed against a cancel() that did
        // nothing but set a status message -- the exact defect it claims to catch.
        final EndlessPagesApplication application = new EndlessPagesApplication(1);
        // Slow pages as well, so the cap stays out of reach for the duration of the test: at one
        // row per page and 20ms per page, reaching 50,000 rows would take over 16 minutes.
        application.slowPages = true;

        final DataExploreViewModel viewModel = FxToolkitSupport.callOnFxThread(
                () -> submittableViewModel(application));

        final CountDownLatch finished = new CountDownLatch(1);
        FxToolkitSupport.runOnFxThread(() -> {
            viewModel.isQueryingProperty().addListener((obs, was, is) -> {
                if (was && !is) {
                    finished.countDown();
                }
            });
            viewModel.submitQuery();
        });

        assertTrue(application.firstPageServed.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "the fake never served a page");

        FxToolkitSupport.runOnFxThread(viewModel::cancel);

        assertTrue(finished.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "cancel did not stop the query -- with one row per page the cap cannot end it, so "
                        + "only a working cancel can");

        final int pagesAfterCancel = application.pagesServed.get();

        FxToolkitSupport.runOnFxThread(() -> {
            assertTrue(viewModel.isResultsTruncated(),
                    "a cancelled result must never be presented as complete");
            assertFalse(viewModel.isQueryingProperty().get());
            assertTrue(viewModel.describeResult().contains("of more"),
                    "the message must say rows are missing: " + viewModel.describeResult());
        });

        // The loop polls between pages, so it cannot abandon the round trip already in flight --
        // but it must not start many more. An unstopped loop serves thousands in this window.
        Thread.sleep(300);
        assertTrue(application.pagesServed.get() <= pagesAfterCancel + 1,
                "the loop kept paging after cancel: served " + application.pagesServed.get()
                        + " pages, was at " + pagesAfterCancel);
    }

    @Test
    @DisplayName("an uncapped, uncancelled query reports a plain total with no truncation")
    void ordinaryQueryIsNotReportedAsTruncated() throws Exception {
        // A single-page fake: the ordinary case must not inherit the truncation wording, or the
        // warning would become noise that users learn to ignore.
        final DpApplication application = new DpApplication() {
            @Override
            public QuerySamplesApiResult querySamples(
                    QueryClient.PvSelectorParams pvSelector,
                    List<QueryClient.ConfigurationCriterion> configurationCriteria,
                    QueryClient.SampleStatusSelectorParams sampleStatusSelector,
                    Instant beginTime, Instant endTime, String pageToken) {
                return new QuerySamplesApiResult(page(25, 0), "");
            }
        };

        final DataExploreViewModel viewModel = FxToolkitSupport.callOnFxThread(
                () -> submittableViewModel(application));

        runQueryToCompletion(viewModel);

        FxToolkitSupport.runOnFxThread(() -> {
            assertEquals(25, viewModel.totalRowsLoadedProperty().get());
            assertFalse(viewModel.isResultsTruncated(),
                    "a complete result must not be reported as truncated");
            assertFalse(viewModel.describeResult().contains("of more"),
                    "a complete result must read as a total: " + viewModel.describeResult());
        });
    }

    @Test
    @DisplayName("the row count is applied before the completion handler reads it (D-2 ordering)")
    void rowCountIsVisibleToTheCompletionHandler() throws Exception {
        // The count is RETURNED from call() and applied in setOnSucceeded, never published from
        // inside the loop via Platform.runLater -- which runs AFTER setOnSucceeded and would make
        // every completion message report zero rows. Asserting the final value would pass against
        // both versions, so this observes the value at the moment isQuerying clears.
        final DpApplication application = new DpApplication() {
            @Override
            public QuerySamplesApiResult querySamples(
                    QueryClient.PvSelectorParams pvSelector,
                    List<QueryClient.ConfigurationCriterion> configurationCriteria,
                    QueryClient.SampleStatusSelectorParams sampleStatusSelector,
                    Instant beginTime, Instant endTime, String pageToken) {
                return new QuerySamplesApiResult(page(12, 0), "");
            }
        };

        final DataExploreViewModel viewModel = FxToolkitSupport.callOnFxThread(
                () -> submittableViewModel(application));

        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicInteger countWhenFlagCleared = new AtomicInteger(-1);
        final AtomicBoolean sawFlagClear = new AtomicBoolean(false);

        FxToolkitSupport.runOnFxThread(() -> {
            viewModel.isQueryingProperty().addListener((obs, was, is) -> {
                if (was && !is) {
                    sawFlagClear.set(true);
                    countWhenFlagCleared.set(viewModel.totalRowsLoadedProperty().get());
                    finished.countDown();
                }
            });
            viewModel.submitQuery();
        });

        assertTrue(finished.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        assertTrue(sawFlagClear.get());
        assertEquals(12, countWhenFlagCleared.get(),
                "the completion handler saw a stale row count -- the outcome is being published "
                        + "from inside the loop rather than returned from call()");
    }
}
