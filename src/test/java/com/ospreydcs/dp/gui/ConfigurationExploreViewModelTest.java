package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.criteria.AttributeCriterion;
import com.ospreydcs.dp.client.criteria.TextMatch;
import com.ospreydcs.dp.grpc.v1.common.Configuration;
import com.ospreydcs.dp.grpc.v1.common.ConfigurationActivation;
import com.ospreydcs.dp.gui.model.ConfigurationTableRow;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the configuration explore view model's two independent searches.
 *
 * The sharpest edge here is the activation time range. TimeRangeCriterion requires BOTH bounds, and
 * the dp-service request builder responds to a half-filled pair by emitting no criterion at all
 * rather than by rejecting the request -- so a user who fills in only a start date gets a result set
 * silently broader than what they asked for, with every other criterion still applied. It looks like
 * a working search rather than a dropped filter, which is why the view model refuses the search
 * instead of passing it through.
 *
 * The blank-TextMatch cases are the same hazard PvMetadataExploreViewModelTest documents: an
 * empty-list criterion is rejected by the server, while a blank PREFIX compiles to a regex matching
 * everything.
 */
public class ConfigurationExploreViewModelTest {

    private static final long AWAIT_SECONDS = 10;

    // ---------------------------------------------------------------------------------------
    // The half-filled range
    // ---------------------------------------------------------------------------------------

    @Test
    public void aHalfFilledRangeIsReportedRatherThanSilentlyDropped() throws Exception {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        final FakeConfigurationApplication application = new FakeConfigurationApplication();
        viewModel.setDpApplication(application);

        // only a start bound: TimeRangeCriterion needs both, and the request builder would emit
        // NEITHER rather than rejecting, silently widening the search
        viewModel.setActivationTemporalCriteria(null, Instant.parse("2026-01-01T00:00:00Z"), null);
        assertTrue(viewModel.hasPartialRange());

        runRefusedActivationSearch(viewModel, application);

        assertEquals(0, application.activationCallCount,
                "a half-filled range must not reach the service, where the bound would be dropped "
                        + "and the search silently widened");
        assertTrue(viewModel.activationSearchStatusMessageProperty().get().contains("half-filled"),
                "the user must be told why the search did not run, but was: "
                        + viewModel.activationSearchStatusMessageProperty().get());
        assertFalse(viewModel.activationSearchInProgressProperty().get(),
                "a refused search must not leave the progress indicator spinning");
    }

    @Test
    public void anEndOnlyRangeIsAlsoRefused() throws Exception {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        final FakeConfigurationApplication application = new FakeConfigurationApplication();
        viewModel.setDpApplication(application);

        viewModel.setActivationTemporalCriteria(null, null, Instant.parse("2026-01-01T00:00:00Z"));
        assertTrue(viewModel.hasPartialRange());

        runRefusedActivationSearch(viewModel, application);
        assertEquals(0, application.activationCallCount,
                "an end-only range must not reach the service either");
        assertTrue(viewModel.activationSearchStatusMessageProperty().get().contains("half-filled"),
                "the user must be told why the search did not run, but was: "
                        + viewModel.activationSearchStatusMessageProperty().get());
    }

    @Test
    public void bothBoundsOrNeitherIsAcceptedAsAFullRange() {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();

        viewModel.setActivationTemporalCriteria(null, null, null);
        assertFalse(viewModel.hasPartialRange(), "no range at all is not a partial range");

        viewModel.setActivationTemporalCriteria(
                null, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"));
        assertFalse(viewModel.hasPartialRange());
    }

    /** activeAt is an independent criterion, so it alone is a complete search. */
    @Test
    public void activeAtAloneIsNotAPartialRange() throws Exception {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        final FakeConfigurationApplication application = new FakeConfigurationApplication();
        viewModel.setDpApplication(application);

        final Instant activeAt = Instant.parse("2026-01-01T12:00:00Z");
        viewModel.setActivationTemporalCriteria(activeAt, null, null);
        assertFalse(viewModel.hasPartialRange());

        runActivationSearchAndObserve(viewModel);

        assertEquals(1, application.activationCallCount, "activeAt alone is a valid search");
        assertEquals(activeAt, application.activeAt);
        assertNull(application.rangeStart);
        assertNull(application.rangeEnd);
    }

    @Test
    public void anInvertedRangeIsRefused() throws Exception {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        final FakeConfigurationApplication application = new FakeConfigurationApplication();
        viewModel.setDpApplication(application);

        viewModel.setActivationTemporalCriteria(
                null, Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));

        runRefusedActivationSearch(viewModel, application);

        assertEquals(0, application.activationCallCount,
                "an inverted range must not reach the service");
        assertTrue(viewModel.activationSearchStatusMessageProperty().get().contains("after"),
                "an inverted range must say so, but was: "
                        + viewModel.activationSearchStatusMessageProperty().get());
    }

    // ---------------------------------------------------------------------------------------
    // textMatch()
    // ---------------------------------------------------------------------------------------

    @Test
    public void aBlankNameYieldsAMatchThatContributesNoCriterion() {
        for (String blank : new String[] {null, "", "   ", " , , "}) {
            for (ConfigurationExploreViewModel.MatchMode mode
                    : ConfigurationExploreViewModel.MatchMode.values()) {
                final TextMatch match = ConfigurationExploreViewModel.textMatch(blank, mode);

                assertTrue(match.isEmpty(),
                        "a blank name in " + mode + " mode must contribute no criterion");
                // asserted individually as well as via isEmpty(): a criterion carrying an EMPTY list
                // is not the same as one carrying no list, and isEmpty() is true for both
                assertNull(match.exact(), "blank input must not populate exact, in " + mode);
                assertNull(match.prefix(), "blank input must not populate prefix, in " + mode);
                assertNull(match.contains(), "blank input must not populate contains, in " + mode);
            }
        }
    }

    @Test
    public void aBlankPrefixDoesNotBecomeAMatchEverythingRegex() {
        final TextMatch match = ConfigurationExploreViewModel.textMatch(
                "  ", ConfigurationExploreViewModel.MatchMode.PREFIX);

        assertNull(match.prefix(),
                "a blank prefix criterion matches EVERY record server-side -- it must be omitted");
    }

    @Test
    public void eachModeRoutesTheValuesToItsOwnList() {
        assertEquals(List.of("rf"), ConfigurationExploreViewModel.textMatch(
                "rf", ConfigurationExploreViewModel.MatchMode.EXACT).exact());
        assertEquals(List.of("rf"), ConfigurationExploreViewModel.textMatch(
                "rf", ConfigurationExploreViewModel.MatchMode.PREFIX).prefix());
        assertEquals(List.of("rf"), ConfigurationExploreViewModel.textMatch(
                "rf", ConfigurationExploreViewModel.MatchMode.CONTAINS).contains());
        assertEquals(List.of("rf"), ConfigurationExploreViewModel.textMatch("rf", null).contains(),
                "a null mode defaults to CONTAINS rather than throwing");
    }

    @Test
    public void aCommaSeparatedNameBecomesMultipleOredValues() {
        final TextMatch match = ConfigurationExploreViewModel.textMatch(
                " rf-cavity , linac,, ", ConfigurationExploreViewModel.MatchMode.EXACT);

        assertEquals(List.of("rf-cavity", "linac"), match.exact(),
                "values must be split, trimmed, and blanks dropped");
    }

    // ---------------------------------------------------------------------------------------
    // attribute criteria
    // ---------------------------------------------------------------------------------------

    @Test
    public void anAttributeKeyWithNoValueIsAKeyOnlyExistenceSearch() {
        final List<AttributeCriterion> criteria =
                ConfigurationExploreViewModel.attributeCriteria("hall", "  ");

        assertEquals(1, criteria.size());
        assertEquals("hall", criteria.get(0).key());
        assertNull(criteria.get(0).values(),
                "a key-only search must send no values, not an empty list");
    }

    @Test
    public void anAttributeValueWithNoKeyContributesNothing() {
        assertTrue(ConfigurationExploreViewModel.attributeCriteria("", "east").isEmpty(),
                "the key is required; a value alone must be dropped, not promoted to a key");
        assertTrue(ConfigurationExploreViewModel.attributeCriteria(null, "east").isEmpty());
    }

    // ---------------------------------------------------------------------------------------
    // search lifecycle
    // ---------------------------------------------------------------------------------------

    @Test
    public void configurationResultsArePublishedBeforeTheInProgressFlagClears() throws Exception {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        viewModel.setDpApplication(FakeConfigurationApplication.withConfigurations(
                new DpApplication.PagedResult<>(
                        List.of(configuration("a"), configuration("b"), configuration("c")), false)));

        final Observed observed = runConfigurationSearchAndObserve(viewModel);

        assertEquals(3, observed.rowCount(),
                "rows must be in the list before the flag clears, not queued behind it");
        assertEquals("3 configuration(s)", observed.countMessage());
    }

    @Test
    public void aTruncatedConfigurationResultSaysSoInBothLabels() throws Exception {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        final List<Configuration> records = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            records.add(configuration("c" + i));
        }
        viewModel.setDpApplication(FakeConfigurationApplication.withConfigurations(
                new DpApplication.PagedResult<>(records, true)));

        runConfigurationSearchAndObserve(viewModel);

        assertEquals("first 4 configuration(s)",
                viewModel.configurationResultCountMessageProperty().get(),
                "the count label must name the truncation");
        assertTrue(viewModel.configurationStatusMessageProperty().get().contains("more available"),
                "the status message must name the truncation too, but was: "
                        + viewModel.configurationStatusMessageProperty().get());
    }

    @Test
    public void aFailedConfigurationSearchClearsTheFlagAndReportsTheError() throws Exception {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        viewModel.setDpApplication(
                new FakeConfigurationApplication(new RuntimeException("the service is unreachable")));

        final Observed observed = runConfigurationSearchAndObserve(viewModel);

        assertEquals(0, observed.rowCount());
        assertFalse(viewModel.configurationSearchInProgressProperty().get(),
                "a failed search must not leave the progress indicator spinning");
        assertTrue(viewModel.configurationSearchStatusMessageProperty().get()
                        .contains("the service is unreachable"));
    }

    @Test
    public void activationResultsArePublishedBeforeTheInProgressFlagClears() throws Exception {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        viewModel.setDpApplication(FakeConfigurationApplication.withActivations(
                new DpApplication.PagedResult<>(List.of(activation("a1"), activation("a2")), false)));

        final Observed observed = runActivationSearchAndObserve(viewModel);

        assertEquals(2, observed.rowCount());
        assertEquals("2 activation(s)", observed.countMessage());
    }

    /**
     * The two searches are independent: one must not disturb the other's results or status, since
     * they answer different questions and either is useful on its own.
     */
    @Test
    public void theTwoSearchesDoNotDisturbEachOther() throws Exception {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        viewModel.setDpApplication(new FakeConfigurationApplication(
                new DpApplication.PagedResult<>(List.of(configuration("a")), false),
                new DpApplication.PagedResult<>(List.of(activation("a1"), activation("a2")), false)));

        runConfigurationSearchAndObserve(viewModel);
        assertEquals(1, viewModel.getConfigurationResults().size());
        assertEquals(0, viewModel.getActivationResults().size());

        runActivationSearchAndObserve(viewModel);
        assertEquals(1, viewModel.getConfigurationResults().size(),
                "the activation search must not clear the configuration results");
        assertEquals(2, viewModel.getActivationResults().size());
        assertEquals("1 configuration(s)",
                viewModel.configurationResultCountMessageProperty().get(),
                "each search keeps its own count label");
        assertEquals("2 activation(s)", viewModel.activationResultCountMessageProperty().get());
    }

    @Test
    public void clearingOneSearchLeavesTheOtherAlone() throws Exception {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        viewModel.setDpApplication(new FakeConfigurationApplication(
                new DpApplication.PagedResult<>(List.of(configuration("a")), false),
                new DpApplication.PagedResult<>(List.of(activation("a1")), false)));

        runConfigurationSearchAndObserve(viewModel);
        runActivationSearchAndObserve(viewModel);

        FxToolkitSupport.runOnFxThread(viewModel::clearActivationSearch);

        assertEquals(1, viewModel.getConfigurationResults().size(),
                "clearing the activation search must not clear the configuration results");
        assertEquals(0, viewModel.getActivationResults().size());
    }

    /**
     * Clearing the activation search must drop the temporal criteria too. They live in fields rather
     * than in bound properties, so a clear that missed them would leave a stale window silently
     * narrowing the next search.
     */
    @Test
    public void clearingTheActivationSearchDropsTheTemporalCriteria() throws Exception {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        final FakeConfigurationApplication application = new FakeConfigurationApplication();
        viewModel.setDpApplication(application);

        viewModel.setActivationTemporalCriteria(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));
        assertFalse(viewModel.activationSearchHasNoCriteria());

        FxToolkitSupport.runOnFxThread(viewModel::clearActivationSearch);

        assertTrue(viewModel.activationSearchHasNoCriteria(),
                "a cleared form must carry no temporal criteria into the next search");

        runActivationSearchAndObserve(viewModel);
        assertNull(application.activeAt, "a cleared activeAt must not reach the service");
        assertNull(application.rangeStart);
        assertNull(application.rangeEnd);
    }

    // ---------------------------------------------------------------------------------------
    // criteria reaching the call
    // ---------------------------------------------------------------------------------------

    @Test
    public void theBuiltConfigurationCriteriaReachTheApiCall() throws Exception {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        final FakeConfigurationApplication application = new FakeConfigurationApplication();
        viewModel.setDpApplication(application);

        viewModel.configurationNameTextProperty().set("rf-cavity");
        viewModel.setConfigurationNameMatchMode(ConfigurationExploreViewModel.MatchMode.PREFIX);
        viewModel.configurationCategoryTextProperty().set("accelerator, cryo");
        viewModel.configurationTagsTextProperty().set("critical");
        viewModel.configurationParentTextProperty().set("site-root");
        viewModel.configurationAttributeKeyProperty().set("hall");
        viewModel.configurationAttributeValueProperty().set("east");

        runConfigurationSearchAndObserve(viewModel);

        assertNotNull(application.nameMatch);
        assertEquals(List.of("rf-cavity"), application.nameMatch.prefix(),
                "the chosen match mode must reach the request");
        assertEquals(List.of("accelerator", "cryo"), application.categories);
        assertEquals(List.of("critical"), application.tags);
        assertEquals(List.of("site-root"), application.parents);
        assertEquals(1, application.attributes.size());
        assertEquals("hall", application.attributes.get(0).key());
        assertEquals(List.of("east"), application.attributes.get(0).values());
    }

    @Test
    public void theBuiltActivationCriteriaReachTheApiCall() throws Exception {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        final FakeConfigurationApplication application = new FakeConfigurationApplication();
        viewModel.setDpApplication(application);

        final Instant start = Instant.parse("2026-01-01T00:00:00Z");
        final Instant end = Instant.parse("2026-01-02T00:00:00Z");
        viewModel.setActivationTemporalCriteria(null, start, end);
        viewModel.activationConfigurationNamesTextProperty().set("rf-cavity, linac");
        viewModel.activationIdsTextProperty().set("evt-1");
        viewModel.activationCategoryTextProperty().set("accelerator");
        viewModel.activationTagsTextProperty().set("maintenance");

        runActivationSearchAndObserve(viewModel);

        assertEquals(start, application.rangeStart);
        assertEquals(end, application.rangeEnd);
        assertEquals(List.of("rf-cavity", "linac"), application.configurationNames);
        assertEquals(List.of("evt-1"), application.activationIds);
        assertEquals(List.of("accelerator"), application.categories);
        assertEquals(List.of("maintenance"), application.tags);
    }

    /**
     * Listing a configuration's activations must replace the other activation criteria rather than
     * filtering within whatever was left in the form, which would silently show a subset.
     */
    @Test
    public void listingActivationsForAConfigurationClearsTheOtherCriteria() throws Exception {
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        final FakeConfigurationApplication application = new FakeConfigurationApplication();
        viewModel.setDpApplication(application);

        viewModel.activationTagsTextProperty().set("some-stale-tag");
        viewModel.activationIdsTextProperty().set("stale-id");

        final CountDownLatch finished = new CountDownLatch(1);
        FxToolkitSupport.runOnFxThread(() -> {
            viewModel.activationSearchInProgressProperty().addListener((obs, was, is) -> {
                if (was && !is) {
                    finished.countDown();
                }
            });
            viewModel.searchActivationsForConfiguration(
                    new ConfigurationTableRow(configuration("rf-cavity")));
        });
        assertTrue(finished.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        FxToolkitSupport.runOnFxThread(() -> { });

        assertEquals(List.of("rf-cavity"), application.configurationNames);
        assertTrue(application.tags == null || application.tags.isEmpty(),
                "a stale tag must not narrow the activation listing");
        assertTrue(application.activationIds == null || application.activationIds.isEmpty(),
                "a stale activation id must not narrow the activation listing");
    }

    @Test
    public void editingNavigatesWithTheResolvedRecord() {
        final AtomicReference<Configuration> navigatedWith = new AtomicReference<>();
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        viewModel.setMainController(new RecordingMainController(navigatedWith));

        // the form holds something else entirely; the editor must get the ROW's record
        viewModel.configurationNameTextProperty().set("whatever-was-typed");
        viewModel.editConfiguration(new ConfigurationTableRow(configuration("rf-cavity")));

        assertNotNull(navigatedWith.get());
        assertEquals("rf-cavity", navigatedWith.get().getConfigurationName(),
                "the editor must receive the clicked record, since saveConfiguration() is a "
                        + "full-replace upsert keyed on its name");
    }

    @Test
    public void editingANullRowDoesNothing() {
        final AtomicReference<Configuration> navigatedWith = new AtomicReference<>();
        final ConfigurationExploreViewModel viewModel = new ConfigurationExploreViewModel();
        viewModel.setMainController(new RecordingMainController(navigatedWith));

        viewModel.editConfiguration(null);
        viewModel.editConfiguration(new ConfigurationTableRow(null));

        assertNull(navigatedWith.get());
    }

    // ---------------------------------------------------------------------------------------
    // support
    // ---------------------------------------------------------------------------------------

    private static Configuration configuration(String name) {
        return Configuration.newBuilder()
                .setConfigurationName(name)
                .setCategory("accelerator")
                .build();
    }

    private static ConfigurationActivation activation(String id) {
        return ConfigurationActivation.newBuilder()
                .setClientActivationId(id)
                .setConfigurationName("rf-cavity")
                .build();
    }

    /**
     * Runs an activation search that is expected to be REFUSED, and returns only once any task it
     * might wrongly have started has had the chance to run.
     *
     * A refused search starts no task, so there is no in-progress transition to await -- but simply
     * returning from runOnFxThread() would read the call count in a race, since executeActivationSearch()
     * returns as soon as it has STARTED a background thread. Waiting on the flag instead would hang
     * forever on the correct behavior. So this waits for the flag to settle false with a bounded
     * poll: a search that wrongly ran will have set it true and then cleared it, and its call will
     * have been recorded by the time the poll sees false again.
     */
    private static void runRefusedActivationSearch(
            ConfigurationExploreViewModel viewModel,
            FakeConfigurationApplication application
    ) throws Exception {
        final int callsBefore = application.activationCallCount;
        FxToolkitSupport.runOnFxThread(viewModel::executeActivationSearch);

        // give a wrongly-started search time to reach the fake and complete
        final long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline
                && application.activationCallCount == callsBefore) {
            Thread.sleep(20);
            FxToolkitSupport.runOnFxThread(() -> { });
        }
        FxToolkitSupport.runOnFxThread(() -> { });
    }

    private record Observed(int rowCount, String countMessage) { }

    private static Observed runConfigurationSearchAndObserve(ConfigurationExploreViewModel viewModel)
            throws Exception {
        return runAndObserve(viewModel::executeConfigurationSearch,
                viewModel.configurationSearchInProgressProperty(),
                () -> viewModel.getConfigurationResults().size(),
                () -> viewModel.configurationResultCountMessageProperty().get());
    }

    private static Observed runActivationSearchAndObserve(ConfigurationExploreViewModel viewModel)
            throws Exception {
        return runAndObserve(viewModel::executeActivationSearch,
                viewModel.activationSearchInProgressProperty(),
                () -> viewModel.getActivationResults().size(),
                () -> viewModel.activationResultCountMessageProperty().get());
    }

    /**
     * Runs a search and captures the state observed at the instant its flag clears.
     *
     * The observation happens in a listener on the flag itself rather than after the fact, which is
     * what makes this an ordering guard: reading the same values once the queue has drained cannot
     * distinguish "published then flag cleared" from "flag cleared then published".
     */
    private static Observed runAndObserve(
            Runnable startSearch,
            javafx.beans.property.BooleanProperty searchInProgress,
            java.util.function.Supplier<Integer> rowCount,
            java.util.function.Supplier<String> countMessage
    ) throws Exception {
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicReference<Integer> observedCount = new AtomicReference<>();
        final AtomicReference<String> observedMessage = new AtomicReference<>();

        FxToolkitSupport.runOnFxThread(() -> {
            searchInProgress.addListener((observable, wasSearching, isSearching) -> {
                if (wasSearching && !isSearching) {
                    observedCount.set(rowCount.get());
                    observedMessage.set(countMessage.get());
                    finished.countDown();
                }
            });
            startSearch.run();
        });

        assertTrue(finished.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "the search should have completed and cleared its in-progress flag");

        FxToolkitSupport.runOnFxThread(() -> { });
        return new Observed(observedCount.get(), observedMessage.get());
    }

    private static final class FakeConfigurationApplication extends DpApplication {
        private final PagedResult<Configuration> configurationResult;
        private final PagedResult<ConfigurationActivation> activationResult;
        private final RuntimeException failure;

        private TextMatch nameMatch;
        private List<String> categories;
        private List<String> tags;
        private List<String> parents;
        private List<AttributeCriterion> attributes;

        private Instant activeAt;
        private Instant rangeStart;
        private Instant rangeEnd;
        private List<String> configurationNames;
        private List<String> activationIds;

        private int configurationCallCount;
        private int activationCallCount;

        FakeConfigurationApplication() {
            this(new PagedResult<>(List.of(), false), new PagedResult<>(List.of(), false));
        }

        /** Named rather than overloaded: erasure makes the two one-arg forms ambiguous. */
        static FakeConfigurationApplication withConfigurations(
                PagedResult<Configuration> configurationResult
        ) {
            return new FakeConfigurationApplication(
                    configurationResult, new PagedResult<>(List.of(), false));
        }

        static FakeConfigurationApplication withActivations(
                PagedResult<ConfigurationActivation> activationResult
        ) {
            return new FakeConfigurationApplication(
                    new PagedResult<>(List.of(), false), activationResult);
        }

        FakeConfigurationApplication(
                PagedResult<Configuration> configurationResult,
                PagedResult<ConfigurationActivation> activationResult
        ) {
            this.configurationResult = configurationResult;
            this.activationResult = activationResult;
            this.failure = null;
        }

        FakeConfigurationApplication(RuntimeException failure) {
            this.configurationResult = null;
            this.activationResult = null;
            this.failure = failure;
        }

        @Override
        public PagedResult<Configuration> queryConfigurations(
                TextMatch nameMatch,
                List<String> categoryAnyOf,
                List<String> tagsAnyOf,
                List<AttributeCriterion> attributes,
                List<String> parentAnyOf
        ) {
            this.configurationCallCount++;
            this.nameMatch = nameMatch;
            this.categories = categoryAnyOf;
            this.tags = tagsAnyOf;
            this.attributes = attributes;
            this.parents = parentAnyOf;

            if (failure != null) {
                throw failure;
            }
            return configurationResult;
        }

        @Override
        public PagedResult<ConfigurationActivation> queryConfigurationActivations(
                Instant activeAt,
                Instant rangeStart,
                Instant rangeEnd,
                List<String> configurationNameAnyOf,
                List<String> clientActivationIdAnyOf,
                List<String> categoryAnyOf,
                List<String> tagsAnyOf,
                List<AttributeCriterion> attributes
        ) {
            this.activationCallCount++;
            this.activeAt = activeAt;
            this.rangeStart = rangeStart;
            this.rangeEnd = rangeEnd;
            this.configurationNames = configurationNameAnyOf;
            this.activationIds = clientActivationIdAnyOf;
            this.categories = categoryAnyOf;
            this.tags = tagsAnyOf;
            this.attributes = attributes;

            if (failure != null) {
                throw failure;
            }
            return activationResult;
        }
    }

    /** Captures the record the view model hands to the editor. */
    private static final class RecordingMainController extends MainController {
        private final AtomicReference<Configuration> navigatedWith;

        RecordingMainController(AtomicReference<Configuration> navigatedWith) {
            this.navigatedWith = navigatedWith;
        }

        @Override
        public void navigateToConfigurationEditor(Configuration record) {
            navigatedWith.set(record);
        }
    }
}
