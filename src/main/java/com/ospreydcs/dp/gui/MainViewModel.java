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
    private final BooleanProperty generateEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty fixedEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty importEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty subscribeEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty dataEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty pvMetadataEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty providerMetadataEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty annotationsEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty annotateEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty exportEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty uploadEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty consoleEnabled = new SimpleBooleanProperty(false);

    private DpApplication dpApplication;

    public MainViewModel() {
        logger.debug("MainViewModel initialized");
    }

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
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

    public BooleanProperty pvMetadataEnabledProperty() {
        return pvMetadataEnabled;
    }

    public BooleanProperty providerMetadataEnabledProperty() {
        return providerMetadataEnabled;
    }

    public BooleanProperty annotationsEnabledProperty() {
        return annotationsEnabled;
    }

    public BooleanProperty annotateEnabledProperty() {
        return annotateEnabled;
    }

    public BooleanProperty exportEnabledProperty() {
        return exportEnabled;
    }

    public BooleanProperty uploadEnabledProperty() {
        return uploadEnabled;
    }

    public BooleanProperty consoleEnabledProperty() {
        return consoleEnabled;
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

    public void handlePvMetadata() {
        logger.info("PV Metadata action triggered");
        updateStatus("Opening PV metadata browser...");
    }

    public void handleProviderMetadata() {
        logger.info("Provider Metadata action triggered");
        updateStatus("Opening provider metadata browser...");
    }

    public void handleAnnotations() {
        logger.info("Annotations query action triggered");
        updateStatus("Opening annotations query...");
    }

    public void handleAnnotate() {
        logger.info("Annotate action triggered");
        updateStatus("Opening annotation tool...");
    }

    public void handleExport() {
        logger.info("Export action triggered");
        updateStatus("Opening export tool...");
    }

    public void handleUpload() {
        logger.info("Upload action triggered");
        updateStatus("Opening calculation upload...");
    }

    public void handleConsole() {
        logger.info("Console action triggered");
        updateStatus("Opening console...");
    }
}