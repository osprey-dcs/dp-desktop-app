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
    private final StringProperty connectionStatusText = new SimpleStringProperty("In-Process Mode");
    private final BooleanProperty isConnected = new SimpleBooleanProperty(true);
    
    // Menu item enabled states
    private final BooleanProperty connectionEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty preferencesEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty generateEnabled = new SimpleBooleanProperty(true);
    private final BooleanProperty fixedEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty importEnabled = new SimpleBooleanProperty(false);
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

    private DpApplication dpApplication;

    public MainViewModel() {
        logger.debug("MainViewModel initialized");
    }

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        updateMenuStatesFromApplicationState();
        logger.debug("DpApplication injected into MainViewModel");
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
     * Updates menu item enabled states based on current application state
     */
    public void updateMenuStatesFromApplicationState() {
        if (dpApplication != null) {
            // Enable data query menu if data has been ingested
            dataEnabled.set(dpApplication.hasIngestedData());
            
            // Enable PV and Provider metadata menus if data has been ingested
            pvStatsEnabled.set(dpApplication.hasIngestedData());
            pvMetadataExploreEnabled.set(dpApplication.hasIngestedData());

            // Configurations are curated records rather than ingestion-derived ones, so this view
            // has something to show before any data exists.  It is gated with its Explore siblings
            // anyway, for one menu with one rule rather than a lone exception -- the Metadata menu
            // is where the always-enabled entry points live.
            configurationsExploreEnabled.set(dpApplication.hasIngestedData());
            providerMetadataEnabled.set(dpApplication.hasIngestedData());
            
            // Enable datasets menu if data has been ingested
            datasetsEnabled.set(dpApplication.hasIngestedData());
            
            // Enable annotations menu if data has been ingested
            annotationsEnabled.set(dpApplication.hasIngestedData());
            
            // Sample statuses are written by the demo generator during ingestion, so like the
            // other explore views this has nothing to show until data exists
            sampleStatusesEnabled.set(dpApplication.hasIngestedData());

            // Enable data events menu if data has been ingested
            dataEventsEnabled.set(dpApplication.hasIngestedData());
            
            logger.debug("Menu states updated - dataEnabled: {}, pvStatsEnabled: {}, providerMetadataEnabled: {}, datasetsEnabled: {}, annotationsEnabled: {}, dataEventsEnabled: {}", 
                dataEnabled.get(), pvStatsEnabled.get(), providerMetadataEnabled.get(), datasetsEnabled.get(), annotationsEnabled.get(), dataEventsEnabled.get());
        }
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
