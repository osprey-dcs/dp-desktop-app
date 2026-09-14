package com.ospreydcs.dp.gui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MainViewModel {

    private static final Logger logger = LogManager.getLogger();

    // Observable properties for UI binding
    private final StringProperty statusText = new SimpleStringProperty("Ready");
    // Replaced by AppConfiguration.describe() as soon as DpApplication is injected.  The initial
    // value is deliberately not "In-Process Mode": the label is bound in initialize(), which runs
    // before injection, so a mode-specific default would state something false in the other mode.
    private final StringProperty connectionStatusText = new SimpleStringProperty("Connecting...");
    private final BooleanProperty isConnected = new SimpleBooleanProperty(true);
    
    // Menu item enabled states
    private final BooleanProperty connectionEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty preferencesEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty generateEnabled = new SimpleBooleanProperty(true);
    private final BooleanProperty fixedEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty importEnabled = new SimpleBooleanProperty(true);
    private final BooleanProperty subscribeEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty dataEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty pvStatsEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty pvMetadataExploreEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty configurationsExploreEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty providerMetadataEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty datasetsEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty annotationsEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty sampleStatusesEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty dataEventsEnabled = new SimpleBooleanProperty(false);

    // Metadata menu.  These two, and Ingest > Import above, were unbound and default-enabled before
    // #4 -- "always enabled" was true while the only mode was demo, and stopped being true when
    // deployment mode arrived.  They are the items a mode rule is most likely to miss, since
    // nothing in the controller mentioned them; MenuGatingTest asserts all three per mode for that
    // reason.  The initializers below are superseded by the constructor's derivation; they match
    // what it produces in the default (demo) mode so the two cannot disagree.
    private final BooleanProperty pvMetadataCreateEnabled = new SimpleBooleanProperty(true);
    private final BooleanProperty machineConfigCreateEnabled = new SimpleBooleanProperty(true);

    /**
     * Tools &gt; Delete Demo Data.  Demo-mode only, and it is the one item whose binding is not the
     * last line of defense: {@code MainController.onDeleteDemoData()} re-checks the mode before
     * acting, so a refactor that breaks this binding cannot turn the action into a delete against a
     * production archive.
     */
    private final BooleanProperty deleteDemoDataEnabled = new SimpleBooleanProperty(true);

    /**
     * Whether the application is pointed at remote services rather than the in-process demo
     * ecosystem.  Held as a plain field rather than a property: it is fixed at launch (File >
     * Connection, which would change it at runtime, is a disabled stub), and a property would
     * suggest otherwise.
     */
    private boolean deploymentMode = false;

    private DpApplication dpApplication;

    public MainViewModel() {
        // Derive once up front so the properties are self-consistent before anything binds to them.
        // MainController.initialize() binds every menu item before DpApplication is injected, so
        // whatever these hold at construction is what the menu bar shows in that window.  Demo is
        // the default mode, so the write menus are correctly enabled here; deployment mode disables
        // them the moment the mode is known, which is still before the stage is shown.
        updateMenuStatesFromApplicationState();
        logger.debug("MainViewModel initialized");
    }

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        // Read the mode BEFORE deriving the menu states, since every rule below depends on it.
        // Deriving first and correcting afterward would leave the write menus briefly enabled in
        // deployment mode, which is exactly the window a user can click through.
        if (dpApplication != null) {
            this.deploymentMode = dpApplication.isDeploymentMode();
            updateConnectionStatus(dpApplication.getConfiguration().describe());
        }
        updateMenuStatesFromApplicationState();
        logger.debug("DpApplication injected into MainViewModel, deploymentMode={}", deploymentMode);
    }

    /**
     * Test seam for the mode, so {@code MenuGatingTest} can assert the matrix in both modes without
     * a {@link DpApplication} and therefore without a service ecosystem -- the same reasoning that
     * keeps {@code accumulatePages()} and {@code emptyToNull()} static.
     */
    void setDeploymentModeForTesting(boolean deploymentMode) {
        this.deploymentMode = deploymentMode;
        updateMenuStatesFromApplicationState();
    }

    public boolean isDeploymentMode() {
        return deploymentMode;
    }

    // Property getters for UI binding
    public StringProperty statusTextProperty() {
        return statusText;
    }

    public StringProperty connectionStatusTextProperty() {
        return connectionStatusText;
    }

    public BooleanProperty isConnectedProperty() {
        return isConnected;
    }

    // Menu item enabled property getters
    public BooleanProperty connectionEnabledProperty() {
        return connectionEnabled;
    }

    public BooleanProperty preferencesEnabledProperty() {
        return preferencesEnabled;
    }

    public BooleanProperty generateEnabledProperty() {
        return generateEnabled;
    }

    public BooleanProperty fixedEnabledProperty() {
        return fixedEnabled;
    }

    public BooleanProperty importEnabledProperty() {
        return importEnabled;
    }

    public BooleanProperty subscribeEnabledProperty() {
        return subscribeEnabled;
    }

    public BooleanProperty dataEnabledProperty() {
        return dataEnabled;
    }

    public BooleanProperty pvStatsEnabledProperty() {
        return pvStatsEnabled;
    }

    public BooleanProperty pvMetadataExploreEnabledProperty() {
        return pvMetadataExploreEnabled;
    }

    public BooleanProperty configurationsExploreEnabledProperty() {
        return configurationsExploreEnabled;
    }

    public BooleanProperty providerMetadataEnabledProperty() {
        return providerMetadataEnabled;
    }

    public BooleanProperty datasetsEnabledProperty() {
        return datasetsEnabled;
    }

    public BooleanProperty sampleStatusesEnabledProperty() {
        return sampleStatusesEnabled;
    }

    public BooleanProperty annotationsEnabledProperty() {
        return annotationsEnabled;
    }

    public BooleanProperty dataEventsEnabledProperty() {
        return dataEventsEnabled;
    }

    public BooleanProperty pvMetadataCreateEnabledProperty() {
        return pvMetadataCreateEnabled;
    }

    public BooleanProperty machineConfigCreateEnabledProperty() {
        return machineConfigCreateEnabled;
    }

    public BooleanProperty deleteDemoDataEnabledProperty() {
        return deleteDemoDataEnabled;
    }

    // Business logic methods
    public void updateStatus(String status) {
        statusText.set(status);
        logger.debug("Status updated to: {}", status);
    }

    public void updateConnectionStatus(String connectionStatus) {
        connectionStatusText.set(connectionStatus);
        logger.debug("Connection status updated to: {}", connectionStatus);
    }

    public void setConnected(boolean connected) {
        isConnected.set(connected);
        logger.debug("Connection state changed to: {}", connected);
    }
    
    /**
     * Derives every menu item's enabled state from the two inputs that determine it: the mode, and
     * whether this session has ingested anything.
     *
     * <p>The whole matrix reduces to two rules, deliberately named once here rather than repeated
     * per item:
     *
     * <pre>
     *   exploreEnabled = deploymentMode || hasIngestedData
     *   writeEnabled   = !deploymentMode
     * </pre>
     *
     * <p><b>{@code hasIngestedData} means two different things, and only one of them is what the
     * Explore menu wants.</b>  It records that <i>this session</i> ingested, which in deployment
     * mode is permanently false -- the archive was populated by other processes long before this
     * app launched.  Gating the Explore views on it directly would leave every one of them disabled
     * forever against a live deployment, which is the whole point of connecting to one.  So the
     * question the Explore items actually ask is "is there anything to explore", and in deployment
     * mode the answer is yes from launch.
     *
     * <p><b>Data Events is the one exception, and it is deliberate.</b>  Subscribing is a read, so
     * it would otherwise belong with its Explore siblings -- but the view was built against
     * subscriptions created during this session's ingestion, and that premise is unverified against
     * a live archive.  It therefore keeps {@code hasIngestedData && !deploymentMode}, which is
     * neither rule.  Re-enabling it is a named follow-on rather than an oversight.
     *
     * <p>This runs without a {@link DpApplication}: the mode rules do not need one, and the
     * ingestion rules read false, which is the correct pre-injection state.  An early return on a
     * null application would leave the write menus enabled in deployment mode until something else
     * happened to call this.
     */
    public void updateMenuStatesFromApplicationState() {

        final boolean hasIngestedData = dpApplication != null && dpApplication.hasIngestedData();

        // The archive has something worth browsing: either this session put it there, or we are
        // pointed at a deployment that was already populated.
        final boolean exploreEnabled = deploymentMode || hasIngestedData;

        // Writes to the archive.  Deployment mode admits no ingestion and no metadata authoring;
        // dataset save, annotation save and export stay enabled and live inside data-explore rather
        // than on this menu bar, so they are not gated here.
        final boolean writeEnabled = !deploymentMode;

        // Ingest menu
        generateEnabled.set(writeEnabled);
        // Import is disabled in deployment mode despite importing real rather than generated data.
        // "No archive writes in deployment mode" is a rule that can be stated and checked; "no
        // writes except import" is a rule whose safety depends on what the user happens to import.
        importEnabled.set(writeEnabled);

        // Metadata menu -- curated records, authored here, so writes
        pvMetadataCreateEnabled.set(writeEnabled);
        machineConfigCreateEnabled.set(writeEnabled);

        // Tools menu.  Deleting the demo database is only meaningful when there is one -- in
        // deployment mode no Mongo client is ever constructed, so the action has no target at all.
        // It follows writeEnabled rather than carrying its own rule because it IS a write, and the
        // most destructive one the application offers.
        deleteDemoDataEnabled.set(writeEnabled);

        // Explore menu
        dataEnabled.set(exploreEnabled);
        pvStatsEnabled.set(exploreEnabled);
        pvMetadataExploreEnabled.set(exploreEnabled);

        // Configurations are curated records rather than ingestion-derived ones, so this view
        // has something to show before any data exists.  It is gated with its Explore siblings
        // anyway, for one menu with one rule rather than a lone exception -- the Metadata menu
        // is where the always-enabled entry points live.
        configurationsExploreEnabled.set(exploreEnabled);
        providerMetadataEnabled.set(exploreEnabled);
        datasetsEnabled.set(exploreEnabled);
        annotationsEnabled.set(exploreEnabled);

        // Sample statuses are written by the demo generator during ingestion, so in demo mode this
        // has nothing to show until data exists; a real deployment may carry statuses from any
        // producer.
        sampleStatusesEnabled.set(exploreEnabled);

        // See the class note above: neither rule, on purpose.
        dataEventsEnabled.set(hasIngestedData && !deploymentMode);

        logger.debug(
                "Menu states updated - deploymentMode: {}, hasIngestedData: {}, "
                        + "exploreEnabled: {}, writeEnabled: {}, dataEventsEnabled: {}",
                deploymentMode, hasIngestedData, exploreEnabled, writeEnabled, dataEventsEnabled.get());
    }

    /**
     * Public method to refresh menu states - can be called when application state changes
     */
    public void refreshMenuStates() {
        updateMenuStatesFromApplicationState();
    }

    // Future method stubs for menu actions
    public void handleConnection() {
        logger.info("Connection action triggered");
        updateStatus("Configuring connection...");
    }

    public void handlePreferences() {
        logger.info("Preferences action triggered");
        updateStatus("Opening preferences...");
    }

    public void handleGenerate() {
        logger.info("Generate action triggered");
        updateStatus("Opening data generation...");
    }

    public void handleFixed() {
        logger.info("Fixed data action triggered");
        updateStatus("Opening fixed data input...");
    }

    public void handleImport() {
        logger.info("Import action triggered");
        updateStatus("Opening data import...");
    }

    public void handleSubscribe() {
        logger.info("Subscribe action triggered");
        updateStatus("Opening subscription manager...");
    }

    public void handleData() {
        logger.info("Data query action triggered");
        updateStatus("Opening data query...");
    }

    public void handlePvStats() {
        logger.info("PV Statistics action triggered");
        updateStatus("Opening PV statistics browser...");
    }

    public void handlePvMetadataExplore() {
        logger.info("PV Metadata explore action triggered");
        updateStatus("Opening PV metadata browser...");
    }

    public void handleConfigurationsExplore() {
        logger.info("Machine Configurations explore action triggered");
        updateStatus("Opening machine configuration browser...");
    }

    public void handleProviderMetadata() {
        logger.info("Provider Metadata action triggered");
        updateStatus("Opening provider metadata browser...");
    }

    public void handleDatasets() {
        logger.info("Datasets action triggered");
        updateStatus("Opening datasets browser...");
    }

    public void handleSampleStatuses() {
        updateStatus("Opening sample status browser...");
    }

    public void handleAnnotations() {
        logger.info("Annotations query action triggered");
        updateStatus("Opening annotations query...");
    }
    
    public void handleDataEvents() {
        logger.info("Data events action triggered");
        updateStatus("Opening data event subscriptions manager...");
    }

    public void handleCreatePvMetadata() {
        logger.info("Create PV metadata action triggered");
        updateStatus("Opening PV metadata editor...");
    }

    public void handleCreateMachineConfiguration() {
        logger.info("Create machine configuration action triggered");
        updateStatus("Opening machine configuration editor...");
    }
}
