package com.ospreydcs.dp.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mode matrix from the #4 plan, asserted per menu item in both modes.
 *
 * <p>This is the table that stops a future menu item from being added with no mode rule -- which is
 * exactly how Import and the two Metadata create items came to be unbound and default-enabled
 * before #4.  An unbound item is invisible to {@code MainViewModel}: it keeps whatever the FXML
 * gave it, in every mode, and nothing fails.
 *
 * <p>No {@link DpApplication} and therefore no service ecosystem is involved.  The mode rules do
 * not need one, and {@code hasIngestedData} reads false without one, which is the pre-ingestion
 * state half this table is about.  These are also plain JavaFX properties rather than live
 * bindings, so no toolkit is required.
 */
public class MenuGatingTest {

    private static MainViewModel demoMode() {
        final MainViewModel viewModel = new MainViewModel();
        viewModel.setDeploymentModeForTesting(false);
        return viewModel;
    }

    private static MainViewModel deploymentMode() {
        final MainViewModel viewModel = new MainViewModel();
        viewModel.setDeploymentModeForTesting(true);
        return viewModel;
    }

    // ---- write features: enabled in demo, disabled in deployment ----

    @Test
    @DisplayName("write menus are enabled in demo mode")
    public void testWriteMenusEnabledInDemoMode() {
        final MainViewModel viewModel = demoMode();
        assertTrue(viewModel.generateEnabledProperty().get(), "Ingest > Generate");
        assertTrue(viewModel.importEnabledProperty().get(), "Ingest > Import");
        assertTrue(viewModel.pvMetadataCreateEnabledProperty().get(), "Metadata > PV");
        assertTrue(viewModel.machineConfigCreateEnabledProperty().get(), "Metadata > Machine Configuration");
    }

    @Test
    @DisplayName("write menus are disabled in deployment mode")
    public void testWriteMenusDisabledInDeploymentMode() {
        final MainViewModel viewModel = deploymentMode();
        assertFalse(viewModel.generateEnabledProperty().get(), "Ingest > Generate");
        assertFalse(viewModel.importEnabledProperty().get(), "Ingest > Import");
        assertFalse(viewModel.pvMetadataCreateEnabledProperty().get(), "Metadata > PV");
        assertFalse(viewModel.machineConfigCreateEnabledProperty().get(), "Metadata > Machine Configuration");
    }

    /**
     * Import and the two Metadata items were previously unbound, so they would have kept the FXML's
     * enabled default in BOTH modes.  A test that only checked deployment mode would pass against
     * an implementation that disabled them everywhere, so each is asserted in both directions
     * above; this one pins that the three are gated at all.
     */
    @Test
    @DisplayName("the three previously-unbound items actually differ between modes")
    public void testPreviouslyUnboundItemsAreModeSensitive() {
        final MainViewModel demo = demoMode();
        final MainViewModel deployment = deploymentMode();

        assertTrue(demo.importEnabledProperty().get() != deployment.importEnabledProperty().get(),
                "Ingest > Import must depend on the mode");
        assertTrue(demo.pvMetadataCreateEnabledProperty().get()
                        != deployment.pvMetadataCreateEnabledProperty().get(),
                "Metadata > PV must depend on the mode");
        assertTrue(demo.machineConfigCreateEnabledProperty().get()
                        != deployment.machineConfigCreateEnabledProperty().get(),
                "Metadata > Machine Configuration must depend on the mode");
    }

    // ---- explore features ----

    @Test
    @DisplayName("explore menus are disabled before ingestion in demo mode")
    public void testExploreMenusDisabledBeforeIngestionInDemoMode() {
        final MainViewModel viewModel = demoMode();
        assertFalse(viewModel.dataEnabledProperty().get(), "Explore > Data");
        assertFalse(viewModel.pvStatsEnabledProperty().get(), "Explore > PV Statistics");
        assertFalse(viewModel.pvMetadataExploreEnabledProperty().get(), "Explore > PV Metadata");
        assertFalse(viewModel.providerMetadataEnabledProperty().get(), "Explore > Providers");
        assertFalse(viewModel.datasetsEnabledProperty().get(), "Explore > Datasets");
        assertFalse(viewModel.annotationsEnabledProperty().get(), "Explore > Annotations");
        assertFalse(viewModel.configurationsExploreEnabledProperty().get(), "Explore > Machine Configurations");
        assertFalse(viewModel.sampleStatusesEnabledProperty().get(), "Explore > Sample Statuses");
    }

    /**
     * The central claim of the mode rule.  {@code hasIngestedData} records that THIS SESSION
     * ingested, which in deployment mode is permanently false -- so gating the Explore views on it
     * directly would leave every one of them disabled forever against a live archive, which is the
     * entire point of connecting to one.
     */
    @Test
    @DisplayName("explore menus are enabled at launch in deployment mode, with nothing ingested")
    public void testExploreMenusEnabledAtLaunchInDeploymentMode() {
        final MainViewModel viewModel = deploymentMode();
        assertTrue(viewModel.dataEnabledProperty().get(), "Explore > Data");
        assertTrue(viewModel.pvStatsEnabledProperty().get(), "Explore > PV Statistics");
        assertTrue(viewModel.pvMetadataExploreEnabledProperty().get(), "Explore > PV Metadata");
        assertTrue(viewModel.providerMetadataEnabledProperty().get(), "Explore > Providers");
        assertTrue(viewModel.datasetsEnabledProperty().get(), "Explore > Datasets");
        assertTrue(viewModel.annotationsEnabledProperty().get(), "Explore > Annotations");
        assertTrue(viewModel.configurationsExploreEnabledProperty().get(), "Explore > Machine Configurations");
        assertTrue(viewModel.sampleStatusesEnabledProperty().get(), "Explore > Sample Statuses");
    }

    // ---- data events: neither rule ----

    @Test
    @DisplayName("Data Events is disabled in deployment mode even though its siblings are enabled")
    public void testDataEventsDisabledInDeploymentMode() {
        final MainViewModel viewModel = deploymentMode();
        assertTrue(viewModel.dataEnabledProperty().get(),
                "precondition: the other Explore items are enabled in deployment mode");
        assertFalse(viewModel.dataEventsEnabledProperty().get(),
                "Explore > Data Events must NOT follow its Explore siblings -- the view assumes "
                        + "subscriptions created during this session's ingestion, which is unverified "
                        + "against a live archive");
    }

    @Test
    @DisplayName("Data Events is disabled before ingestion in demo mode")
    public void testDataEventsDisabledBeforeIngestionInDemoMode() {
        assertFalse(demoMode().dataEventsEnabledProperty().get(), "Explore > Data Events");
    }

    // ---- tools menu ----

    /**
     * Tools &gt; Delete Demo Data drops a database.  In deployment mode there is no demo database to
     * drop -- no Mongo client is ever constructed -- so the item is gated with the write features.
     *
     * <p>Asserted in both directions on purpose: an implementation that disabled it unconditionally
     * would pass a deployment-only assertion while removing the action entirely, which is how the
     * stale demo data this release introduces would become permanent.
     */
    @Test
    @DisplayName("Delete Demo Data is enabled in demo mode and disabled in deployment mode")
    public void testDeleteDemoDataFollowsTheMode() {
        assertTrue(demoMode().deleteDemoDataEnabledProperty().get(),
                "Tools > Delete Demo Data must be available in demo mode -- the database is no "
                        + "longer dropped at launch, so this is the only way to clear it");
        assertFalse(deploymentMode().deleteDemoDataEnabledProperty().get(),
                "Tools > Delete Demo Data must be disabled in deployment mode");
    }

    /**
     * The delete action is gated on the mode alone, never on whether this session ingested.  Demo
     * data now survives a restart, so the database can hold a previous session's data while
     * {@code hasIngestedData} is false -- gating on it would leave exactly that data undeletable.
     */
    @Test
    @DisplayName("Delete Demo Data is enabled before this session has ingested anything")
    public void testDeleteDemoDataDoesNotDependOnIngestion() {
        final MainViewModel viewModel = demoMode();
        assertFalse(viewModel.dataEnabledProperty().get(),
                "precondition: nothing has been ingested in this session");
        assertTrue(viewModel.deleteDemoDataEnabledProperty().get(),
                "a previous session's data can still be in the database, so the delete action must "
                        + "not depend on this session having ingested");
    }

    // ---- mode is readable, and is not a property that something can flip ----

    @Test
    @DisplayName("mode is reported as set")
    public void testModeIsReported() {
        assertFalse(demoMode().isDeploymentMode());
        assertTrue(deploymentMode().isDeploymentMode());
    }

    /**
     * Derivation must not depend on a DpApplication having been injected.  An early return on a
     * null application would leave the write menus at their enabled defaults in deployment mode
     * until something else happened to call the derivation -- a window a user can click through.
     */
    @Test
    @DisplayName("menu states derive without a DpApplication")
    public void testDerivationWithoutApplication() {
        final MainViewModel viewModel = new MainViewModel();
        viewModel.setDeploymentModeForTesting(true);
        assertFalse(viewModel.generateEnabledProperty().get(),
                "write menus must be disabled in deployment mode before any application is injected");
    }
}
