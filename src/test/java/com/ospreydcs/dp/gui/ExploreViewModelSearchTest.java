package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.result.QueryProvidersApiResult;
import com.ospreydcs.dp.grpc.v1.annotation.DataSet;
import com.ospreydcs.dp.grpc.v1.query.QueryProvidersResponse;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the search lifecycle shared by the explore view models after T2b normalized it: results are
 * returned from the task's call() and published in setOnSucceeded, which already runs on the FX
 * thread.
 *
 * The defect these pin down (D-2 in the #39 triage plan) was an ordering bug, not a wrong value.
 * Provider and Dataset published their rows from inside call() via Platform.runLater and then read
 * the count in setOnSucceeded — which runs BEFORE that queued block — so the completion log and any
 * observer of the in-progress flag saw the pre-search state.  A test that only checked the final
 * table contents would pass against both versions, so these assert what is true AT THE MOMENT the
 * flag clears.
 */
public class ExploreViewModelSearchTest {

    private static final long AWAIT_SECONDS = 10;

    /** Waits for a search to finish by observing searchInProgress go true then false. */
    private static final class SearchWatcher {
        private final CountDownLatch finished = new CountDownLatch(1);
        private final AtomicReference<Integer> rowCountWhenFlagCleared = new AtomicReference<>();
        private final AtomicReference<String> countMessageWhenFlagCleared = new AtomicReference<>();
    }

    private static QueryProvidersResponse.ProvidersResult.ProviderInfo provider(String id) {
        return QueryProvidersResponse.ProvidersResult.ProviderInfo.newBuilder()
                .setId(id)
                .setName("provider-" + id)
                .build();
    }

    private static final class FakeProviderApplication extends DpApplication {
        private final QueryProvidersApiResult result;
        private final RuntimeException failure;

        FakeProviderApplication(QueryProvidersApiResult result) {
            this.result = result;
            this.failure = null;
        }

        FakeProviderApplication(RuntimeException failure) {
            this.result = null;
            this.failure = failure;
        }

        @Override
        public QueryProvidersApiResult queryProviders(
                String idCriterion, String textCriterion, String tagsCriterion,
                String attributeKeyCriterion, String attributeValueCriterion
        ) {
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class FakeDatasetApplication extends DpApplication {
        private final PagedResult<DataSet> result;

        FakeDatasetApplication(PagedResult<DataSet> result) {
            this.result = result;
        }

        @Override
        public PagedResult<DataSet> queryDataSets(
                String idCriterion, String ownerCriterion, String textCriterion, String pvNameCriterion
        ) {
            return result;
        }
    }

    /**
     * Runs a search and captures the state observed at the instant searchInProgress clears.
     *
     * The observation happens in a listener on the flag itself rather than after the fact, which is
     * what makes this a real ordering guard: reading the same values once the queue has drained
     * cannot distinguish "published then flag cleared" from "flag cleared then published".
     */
    private static SearchWatcher runSearchAndObserve(
            Runnable startSearch,
            javafx.beans.property.BooleanProperty searchInProgress,
            java.util.function.Supplier<Integer> rowCount,
            java.util.function.Supplier<String> countMessage
    ) throws Exception {
        final SearchWatcher watcher = new SearchWatcher();

        FxToolkitSupport.runOnFxThread(() -> {
            searchInProgress.addListener((observable, wasSearching, isSearching) -> {
                if (wasSearching && !isSearching) {
                    watcher.rowCountWhenFlagCleared.set(rowCount.get());
                    watcher.countMessageWhenFlagCleared.set(countMessage.get());
                    watcher.finished.countDown();
                }
            });
            startSearch.run();
        });

        assertTrue(watcher.finished.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "the search should have completed and cleared its in-progress flag");

        // Drain anything still queued on the FX thread, so a stray runLater would have landed by
        // the time the caller asserts on the final state.
        FxToolkitSupport.runOnFxThread(() -> { });
        return watcher;
    }

    @Test
    public void providerResultsArePublishedBeforeTheInProgressFlagClears() throws Exception {
        final ProviderExploreViewModel viewModel = new ProviderExploreViewModel();
        viewModel.setDpApplication(new FakeProviderApplication(
                new QueryProvidersApiResult(List.of(provider("p1"), provider("p2"), provider("p3")))));

        final SearchWatcher watcher = runSearchAndObserve(
                viewModel::executeSearch,
                viewModel.searchInProgressProperty(),
                () -> viewModel.getProviderResults().size(),
                () -> viewModel.resultCountMessageProperty().get());

        assertEquals(3, watcher.rowCountWhenFlagCleared.get(),
                "rows must be in the list before searchInProgress clears, not queued behind it");
        assertEquals("3 provider(s)", watcher.countMessageWhenFlagCleared.get(),
                "the count label must be current when the flag clears");
        assertEquals(3, viewModel.getProviderResults().size());
        assertFalse(viewModel.searchInProgressProperty().get());
    }

    @Test
    public void datasetResultsArePublishedBeforeTheInProgressFlagClears() throws Exception {
        final DatasetExploreViewModel viewModel = new DatasetExploreViewModel();
        final List<DataSet> datasets = List.of(
                DataSet.newBuilder().setId("d1").setName("first").build(),
                DataSet.newBuilder().setId("d2").setName("second").build());
        viewModel.setDpApplication(new FakeDatasetApplication(
                new DpApplication.PagedResult<>(datasets, false)));

        final SearchWatcher watcher = runSearchAndObserve(
                viewModel::executeSearch,
                viewModel.searchInProgressProperty(),
                () -> viewModel.getDatasetResults().size(),
                () -> viewModel.resultCountMessageProperty().get());

        assertEquals(2, watcher.rowCountWhenFlagCleared.get(),
                "rows must be in the list before searchInProgress clears, not queued behind it");
        assertEquals("2 dataset(s)", watcher.countMessageWhenFlagCleared.get());
    }

    /**
     * A truncated result must never be labelled as a total — in either of the two labels the view
     * keeps, since a count label bound to the record count would read "N" beside a status message
     * saying the query was capped.
     */
    @Test
    public void aTruncatedDatasetResultSaysSoInBothLabels() throws Exception {
        final DatasetExploreViewModel viewModel = new DatasetExploreViewModel();
        final List<DataSet> datasets = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            datasets.add(DataSet.newBuilder().setId("d" + i).build());
        }
        viewModel.setDpApplication(new FakeDatasetApplication(
                new DpApplication.PagedResult<>(datasets, true)));

        runSearchAndObserve(
                viewModel::executeSearch,
                viewModel.searchInProgressProperty(),
                () -> viewModel.getDatasetResults().size(),
                () -> viewModel.resultCountMessageProperty().get());

        assertEquals("first 5 dataset(s)", viewModel.resultCountMessageProperty().get(),
                "the count label must name the truncation");
        assertTrue(viewModel.statusMessageProperty().get().contains("more available"),
                "the status message must name the truncation too, but was: "
                        + viewModel.statusMessageProperty().get());
    }

    @Test
    public void aFailedProviderSearchClearsTheFlagAndReportsTheError() throws Exception {
        final ProviderExploreViewModel viewModel = new ProviderExploreViewModel();
        viewModel.setDpApplication(new FakeProviderApplication(
                new RuntimeException("the service is unreachable")));

        final SearchWatcher watcher = runSearchAndObserve(
                viewModel::executeSearch,
                viewModel.searchInProgressProperty(),
                () -> viewModel.getProviderResults().size(),
                () -> viewModel.resultCountMessageProperty().get());

        assertEquals(0, watcher.rowCountWhenFlagCleared.get());
        assertFalse(viewModel.searchInProgressProperty().get(),
                "a failed search must not leave the progress indicator spinning");
        assertTrue(viewModel.searchStatusMessageProperty().get().contains("the service is unreachable"),
                "the failure must reach the search status label, but was: "
                        + viewModel.searchStatusMessageProperty().get());
    }

    /**
     * A search with no criteria still clears the previous results rather than leaving them beside a
     * "0 found" count.
     */
    @Test
    public void anEmptyProviderResultClearsThePreviousRows() throws Exception {
        final ProviderExploreViewModel viewModel = new ProviderExploreViewModel();
        viewModel.setDpApplication(new FakeProviderApplication(
                new QueryProvidersApiResult(List.of(provider("p1")))));

        runSearchAndObserve(
                viewModel::executeSearch,
                viewModel.searchInProgressProperty(),
                () -> viewModel.getProviderResults().size(),
                () -> viewModel.resultCountMessageProperty().get());
        assertEquals(1, viewModel.getProviderResults().size(), "precondition: one row is displayed");

        viewModel.setDpApplication(new FakeProviderApplication(
                new QueryProvidersApiResult(List.<QueryProvidersResponse.ProvidersResult.ProviderInfo>of())));

        runSearchAndObserve(
                viewModel::executeSearch,
                viewModel.searchInProgressProperty(),
                () -> viewModel.getProviderResults().size(),
                () -> viewModel.resultCountMessageProperty().get());

        assertEquals(0, viewModel.getProviderResults().size(),
                "the previous search's rows must not survive an empty result");
        assertEquals("0 provider(s)", viewModel.resultCountMessageProperty().get());
    }

    /**
     * The four views now spell the same three concepts the same way.  Asserting the accessors exist
     * is what keeps a fifth or sixth view (#39 tasks 3-5) from inventing a fourth vocabulary: a
     * rename that misses one view fails to compile here.
     */
    @Test
    public void allFourExploreViewModelsExposeTheSameSearchVocabulary() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final PvExploreViewModel pv = new PvExploreViewModel();
            final ProviderExploreViewModel provider = new ProviderExploreViewModel();
            final DatasetExploreViewModel dataset = new DatasetExploreViewModel();
            final AnnotationExploreViewModel annotation = new AnnotationExploreViewModel();

            // searchInProgress: drives the progress indicator and the disabled search button
            assertFalse(pv.searchInProgressProperty().get());
            assertFalse(provider.searchInProgressProperty().get());
            assertFalse(dataset.searchInProgressProperty().get());
            assertFalse(annotation.searchInProgressProperty().get());

            // searchStatusMessage: status beside the search controls
            assertEquals("", pv.searchStatusMessageProperty().get());
            assertEquals("", provider.searchStatusMessageProperty().get());
            assertEquals("", dataset.searchStatusMessageProperty().get());

            // statusMessage: status beside the results table, the app-wide name
            assertTrue(pv.statusMessageProperty().get().length() > 0);
            assertTrue(provider.statusMessageProperty().get().length() > 0);
            assertTrue(dataset.statusMessageProperty().get().length() > 0);
            assertTrue(annotation.statusMessageProperty().get().length() > 0);

            // resultCountMessage: a STRING in every view, because an int cannot say "first N of more"
            assertEquals("0 PV(s)", pv.resultCountMessageProperty().get());
            assertEquals("0 provider(s)", provider.resultCountMessageProperty().get());
            assertEquals("0 dataset(s)", dataset.resultCountMessageProperty().get());
            assertEquals("0 results", annotation.resultCountMessageProperty().get());
        });
    }
}
