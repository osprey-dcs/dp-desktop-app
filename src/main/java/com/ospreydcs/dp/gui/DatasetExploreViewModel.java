package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.annotation.DataSet;
import com.ospreydcs.dp.gui.model.DatasetInfoTableRow;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class DatasetExploreViewModel {

    private static final Logger logger = LogManager.getLogger();

    // Query form properties
    private final StringProperty datasetId = new SimpleStringProperty("");
    private final StringProperty owner = new SimpleStringProperty("");
    private final StringProperty nameDescription = new SimpleStringProperty("");
    private final StringProperty pvName = new SimpleStringProperty("");
    
    // Results properties
    private final ObservableList<DatasetInfoTableRow> datasetResults = FXCollections.observableArrayList();
    
    /**
     * The result count as displayed, which says "first N" rather than a bare count when the query
     * stopped at the cap.  A count presented as a total when it is not is the bug transparent
     * paging exists to fix, so the label cannot be a plain rendering of the record count.  This
     * replaced a separate IntegerProperty that nothing bound: the two counts could disagree, and the
     * int could not express truncation at all.
     */
    private final StringProperty resultCountMessage = new SimpleStringProperty("0 dataset(s)");
    private final BooleanProperty searchInProgress = new SimpleBooleanProperty(false);
    
    // Status properties
    private final StringProperty statusMessage = new SimpleStringProperty("Ready to search for datasets");
    private final StringProperty searchStatusMessage = new SimpleStringProperty("");
    
    // Dependencies
    private DpApplication dpApplication;
    private MainController mainController;

    public DatasetExploreViewModel() {
        logger.debug("DatasetExploreViewModel initialized");
    }
    
    // Dependency injection methods
    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        logger.debug("DpApplication injected into DatasetExploreViewModel");
    }
    
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        logger.debug("MainController injected into DatasetExploreViewModel");
    }

    // Property getters for form fields
    public StringProperty datasetIdProperty() { return datasetId; }
    public StringProperty ownerProperty() { return owner; }
    public StringProperty nameDescriptionProperty() { return nameDescription; }
    public StringProperty pvNameProperty() { return pvName; }
    
    // Property getters for results
    public ObservableList<DatasetInfoTableRow> getDatasetResults() { return datasetResults; }
    public StringProperty resultCountMessageProperty() { return resultCountMessage; }
    public BooleanProperty searchInProgressProperty() { return searchInProgress; }
    
    // Property getters for status
    public StringProperty statusMessageProperty() { return statusMessage; }
    public StringProperty searchStatusMessageProperty() { return searchStatusMessage; }

    /**
     * Execute dataset search with current form parameters
     */
    public void executeSearch() {
        if (dpApplication == null) {
            searchStatusMessage.set("DpApplication not initialized");
            return;
        }

        searchInProgress.set(true);
        searchStatusMessage.set("Searching datasets...");
        datasetResults.clear();
        resultCountMessage.set("0 dataset(s)");

        // The task RETURNS its result rather than publishing it from call(): setOnSucceeded already
        // runs on the FX thread.  The earlier version mutated datasetResults from inside call() via
        // Platform.runLater and then logged the count in setOnSucceeded -- which ran BEFORE that
        // queued block, so it always logged the pre-search count.
        Task<DpApplication.PagedResult<DataSet>> searchTask =
                new Task<DpApplication.PagedResult<DataSet>>() {
            @Override
            protected DpApplication.PagedResult<DataSet> call() throws Exception {
                return executeDatasetSearch();
            }
        };

        searchTask.setOnSucceeded(e -> {
            processSearchResults(searchTask.getValue());
            searchInProgress.set(false);
        });

        searchTask.setOnFailed(e -> {
            logger.error("Dataset search failed", searchTask.getException());
            searchStatusMessage.set("Search failed: " + searchTask.getException().getMessage());
            searchInProgress.set(false);
        });

        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }

    private DpApplication.PagedResult<DataSet> executeDatasetSearch() throws Exception {
        // Get form values (convert empty strings to null for API)
        // DpApplication.emptyToNull() does NOT trim, so trim first: a whitespace-only field must be
        // omitted from the request, not sent as a criterion matching nothing.
        String datasetIdParam = DpApplication.emptyToNull(datasetId.get().trim());
        String ownerParam = DpApplication.emptyToNull(owner.get().trim());
        String nameDescParam = DpApplication.emptyToNull(nameDescription.get().trim());
        String pvNameParam = DpApplication.emptyToNull(pvName.get().trim());
        
        logger.debug("Searching datasets with parameters: datasetId={}, owner={}, nameDescription={}, pvName={}", 
            datasetIdParam, ownerParam, nameDescParam, pvNameParam);

        // queryDataSets() follows nextPageToken internally and reports whether it stopped at the
        // cap.  A failed page throws rather than returning a partial list, so there is no
        // partial-success case to check here.
        return dpApplication.queryDataSets(
            datasetIdParam, ownerParam, nameDescParam, pvNameParam);
    }

    /**
     * Publishes the search results.  Called from setOnSucceeded, which already runs on the FX
     * thread, so no Platform.runLater is needed or wanted -- queueing here would let the caller's
     * searchInProgress reset run before the results appear.
     */
    private void processSearchResults(DpApplication.PagedResult<DataSet> pagedResult) {
        datasetResults.clear();

        for (DataSet dataset : pagedResult.records) {
            datasetResults.add(new DatasetInfoTableRow(dataset));
        }

        resultCountMessage.set(pagedResult.truncated
                ? "first " + datasetResults.size() + " dataset(s)"
                : datasetResults.size() + " dataset(s)");
        // report the count as a total only when the query was not capped, so a truncated
        // result is never presented as a complete one
        searchStatusMessage.set("Search completed");
        statusMessage.set(capitalize(pagedResult.describeCount("dataset")));

        logger.info("Dataset search completed with {} results (truncated={})",
                    datasetResults.size(), pagedResult.truncated);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public void updateStatus(String message) {
        statusMessage.set(message);
        logger.debug("Status updated: {}", message);
    }
}