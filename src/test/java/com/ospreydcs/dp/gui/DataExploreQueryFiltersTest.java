package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.model.ConfigurationFilter;
import com.ospreydcs.dp.gui.model.SampleStatusFilter;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how the two optional filters interact with the Query Editor's own state.
 *
 * <p>The asymmetry between them is what these tests exist to pin. The status filter has a
 * client-checkable rule -- the server rejects a blank domain -- so an incomplete one must disable
 * Submit. The configuration filter has none, because its inactive form is expressed by sending no
 * selector at all, so there is no incomplete state for it to be in; adding a rule there would
 * disable Submit for a query the server would happily run.
 */
class DataExploreQueryFiltersTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        FxToolkitSupport.ensureStarted();
    }

    /** A view model that is valid but for whatever the test changes. */
    private static DataExploreViewModel submittableViewModel() {
        final DataExploreViewModel viewModel = new DataExploreViewModel();
        viewModel.queryBeginDateProperty().set(LocalDate.of(2026, 1, 1));
        viewModel.queryEndDateProperty().set(LocalDate.of(2026, 1, 2));
        viewModel.addPvName("PV:A");
        return viewModel;
    }

    @Test
    @DisplayName("both filters default to restricting nothing")
    void filtersDefaultToInactive() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final DataExploreViewModel viewModel = submittableViewModel();

            assertFalse(viewModel.getConfigurationFilter().isActive(),
                    "a query must not be silently restricted before the user asks for it");
            assertFalse(viewModel.getSampleStatusFilter().isActive());
            assertTrue(viewModel.isQueryValidProperty().get());
        });
    }

    @Test
    @DisplayName("an active configuration filter does NOT affect query validity")
    void configurationFilterDoesNotGateValidity() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final DataExploreViewModel viewModel = submittableViewModel();

            viewModel.setConfigurationFilter(ConfigurationFilter.of(
                    List.of("beamline-a"), null, null, null, null, null));

            assertTrue(viewModel.isQueryValidProperty().get(),
                    "the configuration filter has no client-checkable rule -- gating Submit on one "
                            + "would refuse a query the server would run");
        });
    }

    /**
     * The one client-checkable rule among the two filters. The server rejects a status selector
     * with a blank domain, so sending one breaks an otherwise valid query.
     */
    @Test
    @DisplayName("a status filter with no domain makes the query invalid")
    void statusFilterWithoutDomainIsInvalid() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final DataExploreViewModel viewModel = submittableViewModel();

            viewModel.setSampleStatusFilter(SampleStatusFilter.of(
                    "  ", null, null, SampleStatusFilter.Mode.INCLUDE_MATCHING));

            assertFalse(viewModel.isQueryValidProperty().get(),
                    "the server rejects a status selector without a domain");

            viewModel.setSampleStatusFilter(SampleStatusFilter.of(
                    "epics_alarm", null, null, SampleStatusFilter.Mode.INCLUDE_MATCHING));

            assertTrue(viewModel.isQueryValidProperty().get());
        });
    }

    @Test
    @DisplayName("a null filter restores the inactive default rather than leaving it null")
    void nullFiltersRestoreTheDefault() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final DataExploreViewModel viewModel = submittableViewModel();

            viewModel.setConfigurationFilter(null);
            viewModel.setSampleStatusFilter(null);

            assertFalse(viewModel.getConfigurationFilter().isActive());
            assertFalse(viewModel.getSampleStatusFilter().isActive());
            assertTrue(viewModel.isQueryValidProperty().get(),
                    "a null filter must restore 'no restriction', not leave the query unbuildable");
        });
    }

    /**
     * The scope description drives the success message, so an active filter that does not appear in
     * it would report a filtered row count as though it were unfiltered.
     */
    @Test
    @DisplayName("the scope description names every active filter")
    void scopeDescriptionNamesActiveFilters() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final DataExploreViewModel viewModel = submittableViewModel();

            assertFalse(viewModel.describeQueryScope().contains("only while"),
                    "an inactive filter must not appear in the scope description: "
                            + viewModel.describeQueryScope());

            viewModel.setConfigurationFilter(ConfigurationFilter.of(
                    List.of("beamline-a"), null, null, null, null, null));
            viewModel.setSampleStatusFilter(SampleStatusFilter.of(
                    "epics_alarm", null, List.of(2), SampleStatusFilter.Mode.EXCLUDE_MATCHING));

            final String scope = viewModel.describeQueryScope();

            assertTrue(scope.contains("1 PV by name"),
                    "the scope must still name the PV selection: " + scope);
            assertTrue(scope.contains("beamline-a"),
                    "an active configuration filter must appear in the scope: " + scope);
            assertTrue(scope.contains("epics_alarm"),
                    "an active status filter must appear in the scope: " + scope);
        });
    }

    /**
     * Turning a filter off must actually drop it. An inactive configuration filter that still
     * produced criteria would send the empty selector the server rejects.
     */
    @Test
    @DisplayName("turning the configuration filter off restores null criteria")
    void turningTheConfigurationFilterOffDropsTheSelector() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final DataExploreViewModel viewModel = submittableViewModel();

            viewModel.setConfigurationFilter(ConfigurationFilter.of(
                    List.of("beamline-a"), null, null, null, null, null));
            assertTrue(viewModel.getConfigurationFilter().isActive());

            viewModel.setConfigurationFilter(ConfigurationFilter.none());

            assertFalse(viewModel.getConfigurationFilter().isActive());
            org.junit.jupiter.api.Assertions.assertNull(
                    viewModel.getConfigurationFilter().toCriteria(),
                    "an off filter must send NO selector; an empty one is rejected by the server");
        });
    }
}
