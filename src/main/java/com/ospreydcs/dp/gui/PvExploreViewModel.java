package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.result.QueryPvStatsApiResult;
import com.ospreydcs.dp.grpc.v1.query.QueryPvStatsResponse;
import com.ospreydcs.dp.gui.component.QueryPvsComponent;
import com.ospreydcs.dp.gui.model.PvInfoTableRow;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PvExploreViewModel {

    private static final Logger logger = LogManager.getLogger();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // PV Query Editor properties
    private final StringProperty pvSearchText = new SimpleStringProperty("");
    private final BooleanProperty searchByNameList = new SimpleBooleanProperty(true); // true = name list, false = pattern
    private final BooleanProperty searchInProgress = new SimpleBooleanProperty(false);

    // PV Query Results properties
    private final ObservableList<PvInfoTableRow> searchResults = FXCollections.observableArrayList();
    private final ObservableList<PvInfoTableRow> selectedSearchResults = FXCollections.observableArrayList();

    // Status properties
    private final StringProperty statusMessage = new SimpleStringProperty("Ready");
    /**
     * Status shown beside the search controls, as in the other three explore views.  This view had
     * only the results-area statusMessage, so its search progress and its result count competed for
     * one label and the search state was overwritten by the result summary.
     */
    private final StringProperty searchStatusMessage = new SimpleStringProperty("");
    private final StringProperty resultCountMessage = new SimpleStringProperty("0 PV(s)");

    // Dependencies
    private DpApplication dpApplication;
    private MainController mainController;
    private QueryPvsComponent queryPvsComponent;

    public PvExploreViewModel() {
        logger.debug("PvExploreViewModel initialized");
    }

    // Dependency injection methods
    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        logger.debug("DpApplication injected into PvExploreViewModel");
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        logger.debug("MainController injected into PvExploreViewModel");
    }

    public void setQueryPvsComponent(QueryPvsComponent queryPvsComponent) {
        this.queryPvsComponent = queryPvsComponent;
        logger.debug("QueryPvsComponent injected into PvExploreViewModel");
    }

    // Property getters
    public StringProperty pvSearchTextProperty() { return pvSearchText; }
    public BooleanProperty searchByNameListProperty() { return searchByNameList; }
    public BooleanProperty searchInProgressProperty() { return searchInProgress; }
    public ObservableList<PvInfoTableRow> getSearchResults() { return searchResults; }
    public ObservableList<PvInfoTableRow> getSelectedSearchResults() { return selectedSearchResults; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public StringProperty searchStatusMessageProperty() { return searchStatusMessage; }
    public StringProperty resultCountMessageProperty() { return resultCountMessage; }

    // Business logic methods
    public void searchPvMetadata() {
        if (dpApplication == null) {
            statusMessage.set("DpApplication not initialized");
            return;
        }

        String searchTextValue = pvSearchText.get();
        if (searchTextValue == null || searchTextValue.trim().isEmpty()) {
            statusMessage.set("Please enter search text");
            return;
        }

        searchInProgress.set(true);
        searchStatusMessage.set("Searching for PV metadata...");
        resultCountMessage.set("0 PV(s)");
        logger.info("Starting PV metadata search");

        // Create background task for search
        Task<QueryPvStatsApiResult> searchTask = new Task<QueryPvStatsApiResult>() {
            @Override
            protected QueryPvStatsApiResult call() throws Exception {
                if (searchByNameList.get()) {
                    // Parse comma-separated list of PV names
                    List<String> pvNames = parseCommaSeparatedList(searchTextValue);
                    return dpApplication.queryPvStats(pvNames);
                } else {
                    // Use search text as pattern
                    return dpApplication.queryPvStats(searchTextValue);
                }
            }
        };

        searchTask.setOnSucceeded(e -> {
            handlePvMetadataSearchResult(searchTask.getValue());
            searchInProgress.set(false);
        });

        searchTask.setOnFailed(e -> {
            logger.error("PV metadata search failed", searchTask.getException());
            searchStatusMessage.set("Search failed");
            statusMessage.set("PV search failed: " + searchTask.getException().getMessage());
            searchInProgress.set(false);
        });

        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }

    /**
     * Publishes the search result.  Called from setOnSucceeded, which already runs on the FX thread:
     * the earlier version wrapped each of its six outcomes in its own Platform.runLater, which
     * queued work behind the caller's searchInProgress reset rather than ordering it, so the flag
     * cleared before the results or the error message appeared.
     */
    private void handlePvMetadataSearchResult(QueryPvStatsApiResult apiResult) {
        if (apiResult == null) {
            failSearch("Search failed - null response from service");
            return;
        }

        if (apiResult.resultStatus.isError) {
            failSearch("Search failed: " + apiResult.resultStatus.toString());
            return;
        }

        QueryPvStatsResponse response = apiResult.queryPvStatsResponse;
        if (response == null) {
            failSearch("Search failed - null response from service");
            return;
        }

        if (response.hasExceptionalResult()) {
            failSearch("Search failed: " + response.getExceptionalResult().getMessage());
            return;
        }

        if (!response.hasStatsResult()) {
            searchResults.clear();
            resultCountMessage.set("0 PV(s)");
            searchStatusMessage.set("Search completed");
            statusMessage.set("Search completed but no results found");
            return;
        }

        List<PvInfoTableRow> tableRows = new ArrayList<>();
        for (QueryPvStatsResponse.StatsResult.PvStats pvInfo : response.getStatsResult().getPvStatsList()) {
            tableRows.add(new PvInfoTableRow(pvInfo));
        }

        searchResults.setAll(tableRows);
        resultCountMessage.set(tableRows.size() + " PV(s)");
        searchStatusMessage.set("Search completed");
        statusMessage.set("Found " + tableRows.size() + " matching PV(s)");
        logger.info("PV metadata search returned {} results", tableRows.size());
    }

    /**
     * Reports a failed search without clearing the previous results, matching the other three views:
     * a failure says nothing about what was already displayed.
     */
    private void failSearch(String message) {
        searchStatusMessage.set("Search failed");
        statusMessage.set(message);
        logger.warn("PV metadata search unsuccessful: {}", message);
    }

    public void addSelectedResultsToPvList() {
        if (queryPvsComponent == null) {
            logger.warn("QueryPvsComponent not available for adding PVs");
            return;
        }

        int addedCount = 0;
        for (PvInfoTableRow tableRow : selectedSearchResults) {
            String pvName = tableRow.getPvName();
            // DpApplication.addPvName already checks for duplicates
            queryPvsComponent.addPvName(pvName);
            addedCount++;
        }

        selectedSearchResults.clear();
        logger.info("Added {} PV names from search results", addedCount);
        statusMessage.set("Added " + addedCount + " PV name(s) to query list");
    }

    public void addPvNameToQueryList(String pvName) {
        if (queryPvsComponent != null) {
            queryPvsComponent.addPvName(pvName);
            logger.debug("Added PV name {} to query list", pvName);
            statusMessage.set("Added " + pvName + " to query list");
        }
    }

    private List<String> parseCommaSeparatedList(String input) {
        List<String> result = new ArrayList<>();
        if (input != null && !input.trim().isEmpty()) {
            String[] parts = input.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    public void updateStatus(String message) {
        statusMessage.set(message);
        logger.debug("Status updated: {}", message);
    }
}