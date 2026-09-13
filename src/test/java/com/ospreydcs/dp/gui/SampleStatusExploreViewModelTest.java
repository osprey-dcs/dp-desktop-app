package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.common.DataTimestamps;
import com.ospreydcs.dp.grpc.v1.common.SampleStatusBucket;
import com.ospreydcs.dp.grpc.v1.common.SampleStatusColumn;
import com.ospreydcs.dp.grpc.v1.common.SamplingClock;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the sample status search: the row cap, how truncation is reported, and validation.
 *
 * The expansion itself is covered by SampleStatusTableRowTest; what matters here is that the view
 * never presents a capped result as a total, and that the two independent bounds — buckets on the
 * server, rows in the view — are both surfaced.
 */
public class SampleStatusExploreViewModelTest {

    private static final long AWAIT_SECONDS = 10;

    /** A bucket of `count` statuses at 1-second intervals starting at epoch+`startSeconds`. */
    private static SampleStatusBucket bucket(String pvName, long startSeconds, int count) {
        final SampleStatusColumn.Builder column = SampleStatusColumn.newBuilder().setPvName(pvName);
        for (int index = 0; index < count; index++) {
            column.addStatusCodes(index % 4);
        }

        return SampleStatusBucket.newBuilder()
                .setDomain(DpApplication.SAMPLE_STATUS_DEMO_DOMAIN)
                .setLayer("demo_generator")
                .setDataTimestamps(DataTimestamps.newBuilder()
                        .setSamplingClock(SamplingClock.newBuilder()
                                .setStartTime(Timestamp.newBuilder().setEpochSeconds(startSeconds))
                                .setPeriodNanos(1_000_000_000L)
                                .setCount(count)))
                .setStatusColumn(column)
                .build();
    }

    private static final class FakeApplication extends DpApplication {
        private final DpApplication.PagedResult<SampleStatusBucket> result;
        private final RuntimeException failure;

        FakeApplication(DpApplication.PagedResult<SampleStatusBucket> result) {
            this.result = result;
            this.failure = null;
        }

        FakeApplication(RuntimeException failure) {
            this.result = null;
            this.failure = failure;
        }

        @Override
        public PagedResult<SampleStatusBucket> querySampleStatusBuckets(
                Instant beginTime, Instant endTime,
                List<String> pvNames, List<String> domains, List<String> layers
        ) {
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    /** Runs a search to completion, waiting on searchInProgress going true then false. */
    private static void runSearch(SampleStatusExploreViewModel viewModel) throws Exception {
        final CountDownLatch finished = new CountDownLatch(1);

        FxToolkitSupport.runOnFxThread(() -> {
            viewModel.searchInProgressProperty().addListener((observable, was, is) -> {
                if (was && !is) {
                    finished.countDown();
                }
            });
            viewModel.executeSearch();
        });

        assertTrue(finished.await(AWAIT_SECONDS, TimeUnit.SECONDS), "the search should have completed");
        FxToolkitSupport.runOnFxThread(() -> { });
    }

    private static SampleStatusExploreViewModel viewModelFor(DpApplication application) {
        final SampleStatusExploreViewModel viewModel = new SampleStatusExploreViewModel();
        viewModel.setDpApplication(application);
        viewModel.setTimeRange(Instant.ofEpochSecond(0), Instant.ofEpochSecond(100_000));
        return viewModel;
    }

    @Test
    public void bucketsAreFlattenedIntoOneRowPerStatus() throws Exception {
        final SampleStatusExploreViewModel viewModel = viewModelFor(new FakeApplication(
                new DpApplication.PagedResult<>(
                        List.of(bucket("pv-1", 0, 3), bucket("pv-2", 10, 2)), false)));

        runSearch(viewModel);

        assertEquals(5, viewModel.getSearchResults().size(),
                "two buckets of 3 and 2 statuses are five rows, not two");
        assertEquals("5 status(es)", viewModel.resultCountMessageProperty().get());
        assertTrue(viewModel.statusMessageProperty().get().contains("Found 5"),
                "but was: " + viewModel.statusMessageProperty().get());
    }

    /**
     * The row cap is independent of the bucket cap and is the one that actually bounds the table:
     * paging is by whole buckets, so a handful of dense buckets can expand past any sane row count.
     */
    @Test
    public void theRowCapBoundsTheTableAndIsReportedAsTruncation() throws Exception {
        // one bucket holding more statuses than the view will display
        final int oversized = SampleStatusExploreViewModel.MAX_DISPLAYED_STATUSES + 500;
        final SampleStatusExploreViewModel viewModel = viewModelFor(new FakeApplication(
                new DpApplication.PagedResult<>(List.of(bucket("pv-dense", 0, oversized)), false)));

        runSearch(viewModel);

        assertEquals(SampleStatusExploreViewModel.MAX_DISPLAYED_STATUSES,
                viewModel.getSearchResults().size(),
                "the view must bound its own row count, not just the bucket count");
        assertTrue(viewModel.resultCountMessageProperty().get().startsWith("first "),
                "a capped count must not read as a total, but was: "
                        + viewModel.resultCountMessageProperty().get());
        assertTrue(viewModel.statusMessageProperty().get().contains("of more"),
                "but was: " + viewModel.statusMessageProperty().get());
    }

    /**
     * A bucket-level truncation means more buckets existed on the server. It has a different remedy
     * from a row-level one, so the message says which happened.
     */
    @Test
    public void aBucketTruncationIsReportedDistinctlyFromARowTruncation() throws Exception {
        final SampleStatusExploreViewModel viewModel = viewModelFor(new FakeApplication(
                new DpApplication.PagedResult<>(List.of(bucket("pv-1", 0, 2)), true)));

        runSearch(viewModel);

        assertEquals(2, viewModel.getSearchResults().size());
        assertTrue(viewModel.resultCountMessageProperty().get().startsWith("first "),
                "but was: " + viewModel.resultCountMessageProperty().get());
        assertTrue(viewModel.statusMessageProperty().get().contains("buckets"),
                "a bucket-level cap should say so, but was: "
                        + viewModel.statusMessageProperty().get());
    }

    /** Boundary buckets arrive whole, so the view trims to the window the user asked for. */
    @Test
    public void statusesOutsideTheRequestedWindowAreNotDisplayed() throws Exception {
        final SampleStatusExploreViewModel viewModel = new SampleStatusExploreViewModel();
        viewModel.setDpApplication(new FakeApplication(
                new DpApplication.PagedResult<>(List.of(bucket("pv-1", 100, 10)), false)));
        // ask for [103, 107) against a bucket spanning t=100..109
        viewModel.setTimeRange(Instant.ofEpochSecond(103), Instant.ofEpochSecond(107));

        runSearch(viewModel);

        assertEquals(4, viewModel.getSearchResults().size(),
                "a boundary bucket arrives whole and must be trimmed to the requested range");
        assertEquals("4 status(es)", viewModel.resultCountMessageProperty().get());
    }

    @Test
    public void aMissingTimeRangeIsRejectedBeforeAnyQuery() throws Exception {
        final SampleStatusExploreViewModel viewModel = new SampleStatusExploreViewModel();
        viewModel.setDpApplication(new FakeApplication(
                new DpApplication.PagedResult<>(List.of(bucket("pv-1", 0, 1)), false)));

        FxToolkitSupport.runOnFxThread(viewModel::executeSearch);

        assertFalse(viewModel.searchInProgressProperty().get(), "no search should have started");
        assertTrue(viewModel.searchStatusMessageProperty().get().contains("required"),
                "but was: " + viewModel.searchStatusMessageProperty().get());
        assertTrue(viewModel.getSearchResults().isEmpty());
    }

    @Test
    public void anInvertedTimeRangeIsRejected() throws Exception {
        final SampleStatusExploreViewModel viewModel = new SampleStatusExploreViewModel();
        viewModel.setDpApplication(new FakeApplication(
                new DpApplication.PagedResult<>(List.of(), false)));
        viewModel.setTimeRange(Instant.ofEpochSecond(500), Instant.ofEpochSecond(100));

        FxToolkitSupport.runOnFxThread(viewModel::executeSearch);

        assertFalse(viewModel.searchInProgressProperty().get());
        assertTrue(viewModel.searchStatusMessageProperty().get().contains("after"),
                "but was: " + viewModel.searchStatusMessageProperty().get());
    }

    @Test
    public void aFailedSearchClearsTheFlagAndReportsTheError() throws Exception {
        final SampleStatusExploreViewModel viewModel = viewModelFor(
                new FakeApplication(new RuntimeException("the service is unreachable")));

        runSearch(viewModel);

        assertFalse(viewModel.searchInProgressProperty().get(),
                "a failed search must not leave the progress indicator spinning");
        assertTrue(viewModel.searchStatusMessageProperty().get().contains("the service is unreachable"),
                "but was: " + viewModel.searchStatusMessageProperty().get());
    }

    @Test
    public void anEmptyResultReportsZeroRatherThanKeepingThePreviousRows() throws Exception {
        final SampleStatusExploreViewModel viewModel = viewModelFor(new FakeApplication(
                new DpApplication.PagedResult<>(List.of(bucket("pv-1", 0, 3)), false)));
        runSearch(viewModel);
        assertEquals(3, viewModel.getSearchResults().size(), "precondition: rows are displayed");

        viewModel.setDpApplication(new FakeApplication(
                new DpApplication.PagedResult<>(List.<SampleStatusBucket>of(), false)));
        runSearch(viewModel);

        assertEquals(0, viewModel.getSearchResults().size(),
                "the previous search's rows must not survive an empty result");
        assertEquals("0 status(es)", viewModel.resultCountMessageProperty().get());
    }

    @Test
    public void clearSearchResetsTheFormAndTheResults() throws Exception {
        final SampleStatusExploreViewModel viewModel = viewModelFor(new FakeApplication(
                new DpApplication.PagedResult<>(List.of(bucket("pv-1", 0, 2)), false)));
        FxToolkitSupport.runOnFxThread(() -> viewModel.pvNamesProperty().set("pv-1, pv-2"));
        runSearch(viewModel);

        FxToolkitSupport.runOnFxThread(viewModel::clearSearch);

        assertEquals("", viewModel.pvNamesProperty().get());
        assertTrue(viewModel.getSearchResults().isEmpty());
        assertEquals("0 status(es)", viewModel.resultCountMessageProperty().get());
    }

    /**
     * The demo generator's domain is the only one with local labels; anything else shows a raw code.
     * Pinning the constant here keeps the view's map and DpApplication's generator from drifting.
     */
    @Test
    public void onlyTheDemoDomainCarriesLocalLabels() {
        assertTrue(SampleStatusExploreViewModel.CODE_LABELS
                .containsKey(DpApplication.SAMPLE_STATUS_DEMO_DOMAIN));
        assertEquals(1, SampleStatusExploreViewModel.CODE_LABELS.size(),
                "a second entry here would be a guess: the domain registry is not implemented");
        assertEquals("MAJOR_ALARM",
                SampleStatusExploreViewModel.CODE_LABELS
                        .get(DpApplication.SAMPLE_STATUS_DEMO_DOMAIN)
                        .get(DpApplication.EPICS_ALARM_MAJOR_ALARM));
    }
}
