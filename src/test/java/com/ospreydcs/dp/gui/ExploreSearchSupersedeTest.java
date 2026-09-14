package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.criteria.AttributeCriterion;
import com.ospreydcs.dp.client.criteria.TextMatch;
import com.ospreydcs.dp.grpc.v1.common.PvMetadata;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Clear pressed while a search is still running.
 *
 * <p>The failure this guards is silent and looks like a bug in Clear rather than in the search: the
 * table empties, the status says "Search cleared", and then the old query's rows arrive and
 * repopulate it. The Search button is disabled during a search, but Clear is not -- and disabling
 * Clear would be the wrong remedy, since abandoning a slow search is exactly when a user reaches for
 * it.
 *
 * <p>Asserting only the final table contents would not pin this down, because the whole question is
 * WHEN the results land. Each test therefore holds the search open until after Clear has run, so a
 * result that is going to arrive late has already been made to arrive late.
 */
class ExploreSearchSupersedeTest {

    private static final long AWAIT_SECONDS = 20;

    @BeforeAll
    static void startToolkit() throws Exception {
        FxToolkitSupport.ensureStarted();
    }

    /**
     * Serves one PV metadata record, but only after the test releases it.
     *
     * <p>The gate is what makes the race deterministic: without it the search would usually finish
     * before Clear ran, and the test would pass whether or not the guard existed.
     */
    private static final class GatedPvMetadataApplication extends DpApplication {
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch started = new CountDownLatch(1);

        @Override
        public PagedResult<PvMetadata> queryPvMetadata(
                TextMatch pvNameMatch, TextMatch aliasesMatch,
                List<String> tagsAnyOf, List<AttributeCriterion> attributes) {
            started.countDown();
            try {
                release.await(AWAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new PagedResult<>(
                    List.of(PvMetadata.newBuilder().setPvName("PV:FROM:OLD:SEARCH").build()),
                    false);
        }
    }

    @Test
    @DisplayName("clearing during a PV metadata search discards that search's results")
    void clearSupersedesAnInFlightPvMetadataSearch() throws Exception {
        final GatedPvMetadataApplication application = new GatedPvMetadataApplication();
        final PvMetadataExploreViewModel viewModel = FxToolkitSupport.callOnFxThread(() -> {
            final PvMetadataExploreViewModel vm = new PvMetadataExploreViewModel();
            vm.setDpApplication(application);
            vm.pvNameTextProperty().set("PV");
            return vm;
        });

        // Observe the in-progress flag so the test can tell when the task has fully settled,
        // rather than sleeping and hoping.
        final CountDownLatch settled = new CountDownLatch(1);
        FxToolkitSupport.runOnFxThread(() -> {
            viewModel.searchInProgressProperty().addListener((obs, was, is) -> {
                if (was && !is) {
                    settled.countDown();
                }
            });
            viewModel.executeSearch();
        });

        assertTrue(application.started.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "the search never reached the service");

        // Clear while the search is still parked in the service call.
        FxToolkitSupport.runOnFxThread(viewModel::clearSearch);
        assertTrue(settled.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "clearing should settle the in-progress flag immediately");

        // Now let the search finish. Its results must not land on the cleared state.
        application.release.countDown();

        // Give the completion handler every chance to run before asserting it did nothing.
        Thread.sleep(300);
        FxToolkitSupport.runOnFxThread(() -> { });

        FxToolkitSupport.runOnFxThread(() -> {
            assertEquals(0, viewModel.getSearchResults().size(),
                    "a superseded search repopulated the table after Clear");
            assertEquals("Search cleared", viewModel.searchStatusMessageProperty().get(),
                    "a superseded search overwrote the cleared status message");
            assertFalse(viewModel.searchInProgressProperty().get());
        });
    }

    @Test
    @DisplayName("a normal search still publishes its results")
    void anUnsupersededSearchStillPublishes() throws Exception {
        // The guard must not be so eager that it discards ordinary results -- a generation check
        // that never matched would make every search silently return nothing.
        final GatedPvMetadataApplication application = new GatedPvMetadataApplication();
        application.release.countDown();

        final PvMetadataExploreViewModel viewModel = FxToolkitSupport.callOnFxThread(() -> {
            final PvMetadataExploreViewModel vm = new PvMetadataExploreViewModel();
            vm.setDpApplication(application);
            vm.pvNameTextProperty().set("PV");
            return vm;
        });

        final CountDownLatch settled = new CountDownLatch(1);
        FxToolkitSupport.runOnFxThread(() -> {
            viewModel.searchInProgressProperty().addListener((obs, was, is) -> {
                if (was && !is) {
                    settled.countDown();
                }
            });
            viewModel.executeSearch();
        });

        assertTrue(settled.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        FxToolkitSupport.runOnFxThread(() ->
                assertEquals(1, viewModel.getSearchResults().size(),
                        "an ordinary search must still publish its results"));
    }
}
