package com.ospreydcs.dp.gui;

import javafx.beans.property.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HomeViewModel {

    private static final Logger logger = LogManager.getLogger();

    // Properties for dynamic content
    private final StringProperty hintsText = new SimpleStringProperty();
    private final StringProperty statusText = new SimpleStringProperty();
    private final StringProperty detailsText = new SimpleStringProperty();

    // Application state tracking
    private final BooleanProperty hasIngestedData = new SimpleBooleanProperty(false);
    private final BooleanProperty hasPerformedQueries = new SimpleBooleanProperty(false);
    private final StringProperty lastOperationResult = new SimpleStringProperty();

    private DpApplication dpApplication;

    public HomeViewModel() {
        initializeDefaultContent();
        setupPropertyListeners();
        logger.debug("HomeViewModel initialized");
    }

    private void initializeDefaultContent() {
        hintsText.set("Start by using the Ingest→Generate or Ingest→Import menus to generate or import some PV data and ingest it to the MLDP archive.");
        statusText.set("Ready");
        detailsText.set("No data has been ingested yet.");
    }

    private void setupPropertyListeners() {
        // Update hints and details based on application state
        hasIngestedData.addListener((obs, oldVal, newVal) -> updateContent());
        hasPerformedQueries.addListener((obs, oldVal, newVal) -> updateContent());
        lastOperationResult.addListener((obs, oldVal, newVal) -> updateContent());
    }

    private void updateContent() {
        if (hasIngestedData.get()) {
            if (hasPerformedQueries.get()) {
                hintsText.set("Data ingestion and queries completed. Use the Tools menu to annotate data or export results.");
                detailsText.set("You have ingested data and performed queries. Explore the Tools menu for additional functionality.");
            } else {
                hintsText.set("Data ingested successfully! Use Explore→Data to query time-series data, Explore→PVs to browse metadata, or Explore→Annotations to search annotations.");
                detailsText.set("Data is available in the archive. Try querying some PV data or exploring metadata.");
            }
        } else {
            hintsText.set("Start by using the Ingest→Generate or Ingest→Import menus to generate or import some PV data and ingest it to the MLDP archive.");
            detailsText.set("No data has been ingested yet. Generate some sample data to get started.");
        }

        // Update status with last operation result if available
        if (lastOperationResult.get() != null && !lastOperationResult.get().trim().isEmpty()) {
            statusText.set(lastOperationResult.get());
        }
    }

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        logger.debug("DpApplication injected into HomeViewModel");
    }

    // Property getters
    public StringProperty hintsTextProperty() { return hintsText; }
    public StringProperty statusTextProperty() { return statusText; }
    public StringProperty detailsTextProperty() { return detailsText; }

    // State property getters
    public BooleanProperty hasIngestedDataProperty() { return hasIngestedData; }
    public BooleanProperty hasPerformedQueriesProperty() { return hasPerformedQueries; }
    public StringProperty lastOperationResultProperty() { return lastOperationResult; }

    // Public methods for updating state
    public void updateDataIngestedState(boolean hasData) {
        hasIngestedData.set(hasData);
        logger.debug("Data ingested state updated: {}", hasData);
    }

    public void updateQueriesPerformedState(boolean hasPerformed) {
        hasPerformedQueries.set(hasPerformed);
        logger.debug("Queries performed state updated: {}", hasPerformed);
    }

    public void updateLastOperationResult(String result) {
        lastOperationResult.set(result);
        logger.debug("Last operation result updated: {}", result);
    }

    public void onSuccessfulDataGeneration(String result) {
        updateDataIngestedState(true);
        updateLastOperationResult(result);
        logger.info("Data generation success recorded in home view: {}", result);
    }

    public void onSuccessfulQuery(String result) {
        updateQueriesPerformedState(true);
        updateLastOperationResult(result);
        logger.info("Query success recorded in home view: {}", result);
    }

    public void resetApplicationState() {
        hasIngestedData.set(false);
        hasPerformedQueries.set(false);
        lastOperationResult.set("");
        initializeDefaultContent();
        logger.info("Application state reset in home view");
    }
}