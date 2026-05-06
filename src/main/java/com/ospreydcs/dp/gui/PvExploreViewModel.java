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
    private final BooleanProperty isSearching = new SimpleBooleanProperty(false);

    // PV Query Results properties
    private final ObservableList<PvInfoTableRow> searchResults = FXCollections.observableArrayList();
    private final ObservableList<PvInfoTableRow> selectedSearchResults = FXCollections.observableArrayList();

    // Status properties
    private final StringProperty statusMessage = new SimpleStringProperty("Ready");

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
    public BooleanProperty isSearchingProperty() { return isSearching; }
    public ObservableList<PvInfoTableRow> getSearchResults() { return searchResults; }
    public ObservableList<PvInfoTableRow> getSelectedSearchResults() { return selectedSearchResults; }
    public StringProperty statusMessageProperty() { return statusMessage; }

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

        isSearching.set(true);
        statusMessage.set("Searching for PV metadata...");
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
            QueryPvStatsApiResult apiResult = searchTask.getValue();
            handlePvMetadataSearchResult(apiResult);
            isSearching.set(false);
        });

        searchTask.setOnFailed(e -> {
            logger.error("PV metadata search failed", searchTask.getException());
            statusMessage.set("PV search failed: " + searchTask.getException().getMessage());
            isSearching.set(false);
        });

        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }

    private void handlePvMetadataSearchResult(QueryPvStatsApiResult apiResult) {
        if (apiResult == null) {
            javafx.application.Platform.runLater(() -> {
                statusMessage.set("Search failed - null response from service");
            });
            return;
        }

        if (apiResult.resultStatus.isError) {
            javafx.application.Platform.runLater(() -> {
                statusMessage.set("Search failed: " + apiResult.resultStatus.toString());
            });
            return;
        }

        QueryPvStatsResponse response = apiResult.queryPvStatsResponse;
        if (response == null) {
            javafx.application.Platform.runLater(() -> {
                statusMessage.set("Search failed - null response from service");
            });
            return;
        }

        if (response.hasExceptionalResult()) {
            javafx.application.Platform.runLater(() -> {
                statusMessage.set("Search failed: " + response.getExceptionalResult().getMessage());
            });
            return;
        }

        if (response.hasStatsResult()) {
            List<PvInfoTableRow> tableRows = new ArrayList<>();
            for (QueryPvStatsResponse.StatsResult.PvStats pvInfo : response.getStatsResult().getPvStatsList()) {
                tableRows.add(new PvInfoTableRow(pvInfo));
            }

            // Update UI on JavaFX Application Thread
            javafx.application.Platform.runLater(() -> {
                searchResults.setAll(tableRows);
                statusMessage.set("Found " + tableRows.size() + " matching PV(s)");
            });
            logger.info("PV metadata search returned {} results", tableRows.size());
        } else {
            javafx.application.Platform.runLater(() -> {
                searchResults.clear();
                statusMessage.set("Search completed but no results found");
            });
        }
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