package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.result.QueryPvMetadataApiResult;
import com.ospreydcs.dp.client.result.QueryTableApiResult;
import com.ospreydcs.dp.grpc.v1.query.QueryPvMetadataResponse;
import com.ospreydcs.dp.grpc.v1.query.QueryTableResponse;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DataExploreViewModel {

    private static final Logger logger = LogManager.getLogger();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Query Specification properties
    private final ObservableList<String> pvNameList = FXCollections.observableArrayList();
    private final ObjectProperty<LocalDate> queryBeginDate = new SimpleObjectProperty<>(LocalDate.now());
    private final IntegerProperty beginHour = new SimpleIntegerProperty(0);
    private final IntegerProperty beginMinute = new SimpleIntegerProperty(0);
    private final IntegerProperty beginSecond = new SimpleIntegerProperty(0);
    
    private final ObjectProperty<LocalDate> queryEndDate = new SimpleObjectProperty<>(LocalDate.now());
    private final IntegerProperty endHour = new SimpleIntegerProperty(0);
    private final IntegerProperty endMinute = new SimpleIntegerProperty(0);
    private final IntegerProperty endSecond = new SimpleIntegerProperty(0);
    
    private final BooleanProperty showQuerySpecificationPanel = new SimpleBooleanProperty(true);
    
    // Query Results Panel properties  
    private final BooleanProperty showQueryResultsPanel = new SimpleBooleanProperty(true);
    
    // PV Search Panel properties
    private final StringProperty pvSearchText = new SimpleStringProperty("");
    private final BooleanProperty searchByNameList = new SimpleBooleanProperty(true); // true = name list, false = pattern
    private final ObservableList<String> searchResultPvNames = FXCollections.observableArrayList();
    private final ObservableList<String> selectedSearchResults = FXCollections.observableArrayList();
    private final BooleanProperty showPvSearchPanel = new SimpleBooleanProperty(false);
    private final BooleanProperty isSearching = new SimpleBooleanProperty(false);
    
    // Query Results properties
    private final ObservableList<String> tableColumnNames = FXCollections.observableArrayList();
    private final ObservableList<ObservableList<Object>> tableData = FXCollections.observableArrayList();
    private final IntegerProperty totalRowsLoaded = new SimpleIntegerProperty(0);
    private final BooleanProperty isQuerying = new SimpleBooleanProperty(false);
    
    // Status properties
    private final StringProperty statusMessage = new SimpleStringProperty("Ready to query data");
    private final BooleanProperty hasQueryResults = new SimpleBooleanProperty(false);
    private final BooleanProperty isQueryValid = new SimpleBooleanProperty(false);

    // Dependencies
    private DpApplication dpApplication;
    private MainController mainController;

    public DataExploreViewModel() {
        logger.debug("DataExploreViewModel initialized");
        
        // Set up listeners to update validation state
        setupValidationListeners();
    }
    
    private void setupValidationListeners() {
        // Listen to PV name list changes
        pvNameList.addListener((javafx.collections.ListChangeListener<String>) change -> updateValidation());
        
        // Listen to date and time changes
        queryBeginDate.addListener((obs, oldVal, newVal) -> updateValidation());
        queryEndDate.addListener((obs, oldVal, newVal) -> updateValidation());
        beginHour.addListener((obs, oldVal, newVal) -> updateValidation());
        beginMinute.addListener((obs, oldVal, newVal) -> updateValidation());
        beginSecond.addListener((obs, oldVal, newVal) -> updateValidation());
        endHour.addListener((obs, oldVal, newVal) -> updateValidation());
        endMinute.addListener((obs, oldVal, newVal) -> updateValidation());
        endSecond.addListener((obs, oldVal, newVal) -> updateValidation());
        
        // Initial validation
        updateValidation();
    }
    
    private void updateValidation() {
        boolean valid = isQueryValid();
        isQueryValid.set(valid);
    }

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        
        // Initialize PV names from previously generated data
        if (dpApplication.getPvDetails() != null) {
            List<String> pvNames = new ArrayList<>();
            dpApplication.getPvDetails().forEach(pvDetail -> pvNames.add(pvDetail.getPvName()));
            pvNameList.setAll(pvNames);
            logger.debug("Initialized PV name list with {} PVs from data generation", pvNames.size());
        }
        
        // Initialize time range from previously used values
        if (dpApplication.getDataBeginTime() != null) {
            LocalDateTime beginDateTime = LocalDateTime.ofInstant(dpApplication.getDataBeginTime(), ZoneId.systemDefault());
            queryBeginDate.set(beginDateTime.toLocalDate());
            beginHour.set(beginDateTime.getHour());
            beginMinute.set(beginDateTime.getMinute());
            beginSecond.set(beginDateTime.getSecond());
        }
        
        if (dpApplication.getDataEndTime() != null) {
            LocalDateTime endDateTime = LocalDateTime.ofInstant(dpApplication.getDataEndTime(), ZoneId.systemDefault());
            queryEndDate.set(endDateTime.toLocalDate());
            endHour.set(endDateTime.getHour());
            endMinute.set(endDateTime.getMinute());
            endSecond.set(endDateTime.getSecond());
        }
        
        logger.debug("DpApplication injected into DataQueryViewModel");
    }
    
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        logger.debug("MainController injected into DataQueryViewModel");
    }

    // Query Specification property getters
    public ObservableList<String> getPvNameList() { return pvNameList; }
    public ObjectProperty<LocalDate> queryBeginDateProperty() { return queryBeginDate; }
    public IntegerProperty beginHourProperty() { return beginHour; }
    public IntegerProperty beginMinuteProperty() { return beginMinute; }
    public IntegerProperty beginSecondProperty() { return beginSecond; }
    public ObjectProperty<LocalDate> queryEndDateProperty() { return queryEndDate; }
    public IntegerProperty endHourProperty() { return endHour; }
    public IntegerProperty endMinuteProperty() { return endMinute; }
    public IntegerProperty endSecondProperty() { return endSecond; }
    public BooleanProperty showQuerySpecificationPanelProperty() { return showQuerySpecificationPanel; }
    public BooleanProperty showQueryResultsPanelProperty() { return showQueryResultsPanel; }

    // PV Search Panel property getters
    public StringProperty pvSearchTextProperty() { return pvSearchText; }
    public BooleanProperty searchByNameListProperty() { return searchByNameList; }
    public ObservableList<String> getSearchResultPvNames() { return searchResultPvNames; }
    public ObservableList<String> getSelectedSearchResults() { return selectedSearchResults; }
    public BooleanProperty showPvSearchPanelProperty() { return showPvSearchPanel; }
    public BooleanProperty isSearchingProperty() { return isSearching; }

    // Query Results property getters
    public ObservableList<String> getTableColumnNames() { return tableColumnNames; }
    public ObservableList<ObservableList<Object>> getTableData() { return tableData; }
    public IntegerProperty totalRowsLoadedProperty() { return totalRowsLoaded; }
    public BooleanProperty isQueryingProperty() { return isQuerying; }

    // Status property getters
    public StringProperty statusMessageProperty() { return statusMessage; }
    public BooleanProperty hasQueryResultsProperty() { return hasQueryResults; }
    public BooleanProperty isQueryValidProperty() { return isQueryValid; }

    // Business logic methods
    public void toggleQuerySpecificationPanel() {
        showQuerySpecificationPanel.set(!showQuerySpecificationPanel.get());
    }
    
    public void toggleQueryResultsPanel() {
        showQueryResultsPanel.set(!showQueryResultsPanel.get());
    }
    
    public void addPvName(String pvName) {
        if (pvName != null && !pvName.trim().isEmpty() && !pvNameList.contains(pvName)) {
            pvNameList.add(pvName);
            logger.debug("Added PV name: {}", pvName);
        }
    }

    public void removePvName(String pvName) {
        pvNameList.remove(pvName);
        logger.debug("Removed PV name: {}", pvName);
    }

    public void showPvSearchPanel() {
        showPvSearchPanel.set(true);
        logger.debug("Showing PV search panel - NOT clearing search results");
        // Don't clear search results when opening panel - preserve any existing results
    }

    public void hidePvSearchPanel() {
        showPvSearchPanel.set(false);
        searchResultPvNames.clear();
        selectedSearchResults.clear();
        pvSearchText.set("");
        logger.debug("Hiding PV search panel");
    }

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
        logger.info("Starting PV metadata search, keeping existing results until new ones arrive");

        // Create background task for search
        Task<QueryPvMetadataApiResult> searchTask = new Task<QueryPvMetadataApiResult>() {
            @Override
            protected QueryPvMetadataApiResult call() throws Exception {
                if (searchByNameList.get()) {
                    // Parse comma-separated list of PV names
                    List<String> pvNames = parseCommaSeparatedList(searchTextValue);
                    return dpApplication.queryPvMetadata(pvNames);
                } else {
                    // Use search text as pattern
                    return dpApplication.queryPvMetadata(searchTextValue);
                }
            }
        };

        searchTask.setOnSucceeded(e -> {
            QueryPvMetadataApiResult apiResult = searchTask.getValue();
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

    private void handlePvMetadataSearchResult(QueryPvMetadataApiResult apiResult) {
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

        QueryPvMetadataResponse response = apiResult.queryPvMetadataResponse;
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

        if (response.hasMetadataResult()) {
            List<String> foundPvNames = new ArrayList<>();
            response.getMetadataResult().getPvInfosList().forEach(pvInfo -> {
                foundPvNames.add(pvInfo.getPvName());
            });
            
            // Update UI on JavaFX Application Thread
            javafx.application.Platform.runLater(() -> {
                searchResultPvNames.setAll(foundPvNames);
                statusMessage.set("Found " + foundPvNames.size() + " matching PV(s)");
            });
            logger.info("PV metadata search returned {} results", foundPvNames.size());
        } else {
            javafx.application.Platform.runLater(() -> {
                statusMessage.set("Search completed but no results found");
            });
        }
    }

    public void addSelectedSearchResultsToPvList() {
        int addedCount = 0;
        for (String pvName : selectedSearchResults) {
            if (!pvNameList.contains(pvName)) {
                pvNameList.add(pvName);
                addedCount++;
            }
        }
        
        selectedSearchResults.clear();
        logger.info("Added {} PV names from search results", addedCount);
        statusMessage.set("Added " + addedCount + " PV name(s) to query list");
    }

    public LocalDateTime getQueryBeginDateTime() {
        LocalTime beginTime = LocalTime.of(beginHour.get(), beginMinute.get(), beginSecond.get());
        return LocalDateTime.of(queryBeginDate.get(), beginTime);
    }

    public LocalDateTime getQueryEndDateTime() {
        LocalTime endTime = LocalTime.of(endHour.get(), endMinute.get(), endSecond.get());
        return LocalDateTime.of(queryEndDate.get(), endTime);
    }

    public void submitQuery() {
        if (!isQueryValidWithMessages()) {
            return;
        }

        if (dpApplication == null) {
            statusMessage.set("DpApplication not initialized");
            return;
        }

        isQuerying.set(true);
        hasQueryResults.set(false);
        tableData.clear();
        tableColumnNames.clear();
        totalRowsLoaded.set(0);
        statusMessage.set("Querying data...");

        // Create background task for query
        Task<Void> queryTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                executeIncrementalQuery();
                return null;
            }
        };

        queryTask.setOnSucceeded(e -> {
            isQuerying.set(false);
            hasQueryResults.set(true);
            
            // Update application state and notify home view
            if (dpApplication != null) {
                dpApplication.setHasPerformedQueries(true);
                String resultMessage = "Successfully queried " + totalRowsLoaded.get() + 
                    " row(s) for " + pvNameList.size() + " PV(s)";
                dpApplication.setLastOperationResult(resultMessage);
            }
            
            if (mainController != null) {
                String resultMessage = "Query completed: " + totalRowsLoaded.get() + 
                    " row(s) for " + pvNameList.size() + " PV(s)";
                mainController.onQuerySuccess(resultMessage);
            }
            
            statusMessage.set("Query completed successfully");
            logger.info("Query completed successfully with {} rows", totalRowsLoaded.get());
        });

        queryTask.setOnFailed(e -> {
            logger.error("Query failed", queryTask.getException());
            statusMessage.set("Query failed: " + queryTask.getException().getMessage());
            isQuerying.set(false);
        });

        Thread queryThread = new Thread(queryTask);
        queryThread.setDaemon(true);
        queryThread.start();
    }

    private void executeIncrementalQuery() throws Exception {
        Instant beginInstant = getQueryBeginDateTime().atZone(ZoneId.systemDefault()).toInstant();
        Instant endInstant = getQueryEndDateTime().atZone(ZoneId.systemDefault()).toInstant();
        
        // Break query into 1-minute intervals to avoid message size limits
        long totalDurationSeconds = java.time.Duration.between(beginInstant, endInstant).toSeconds();
        int intervalSeconds = 60; // 1 minute intervals
        int numberOfIntervals = (int) Math.ceil((double) totalDurationSeconds / intervalSeconds);
        
        boolean firstResponse = true;
        int totalRows = 0;
        
        for (int intervalIndex = 0; intervalIndex < numberOfIntervals; intervalIndex++) {
            Instant intervalBegin = beginInstant.plusSeconds((long) intervalIndex * intervalSeconds);
            Instant intervalEnd = beginInstant.plusSeconds((long) (intervalIndex + 1) * intervalSeconds);
            
            if (intervalEnd.isAfter(endInstant)) {
                intervalEnd = endInstant;
            }
            
            logger.debug("Querying interval {} of {}: {} to {}", 
                intervalIndex + 1, numberOfIntervals, intervalBegin, intervalEnd);
            
            QueryTableApiResult apiResult = dpApplication.queryTable(
                new ArrayList<>(pvNameList), intervalBegin, intervalEnd);
            
            if (apiResult == null) {
                throw new RuntimeException("Query failed - null response from service");
            }
            
            if (apiResult.resultStatus.isError) {
                throw new RuntimeException("Query failed: " + apiResult.resultStatus.toString());
            }
            
            QueryTableResponse response = apiResult.queryTableResponse;
            if (response == null) {
                throw new RuntimeException("Query failed - null response from service");
            }
            
            if (response.hasExceptionalResult()) {
                throw new RuntimeException("Query failed: " + response.getExceptionalResult().getMessage());
            }
            
            if (response.hasTableResult()) {
                processQueryTableResponse(response, firstResponse);
                firstResponse = false;
                
                if (response.getTableResult().hasRowMapTable()) {
                    totalRows += response.getTableResult().getRowMapTable().getRowsCount();
                }
            }
        }
        
        // Update total rows on JavaFX thread
        final int finalTotalRows = totalRows;
        javafx.application.Platform.runLater(() -> {
            totalRowsLoaded.set(finalTotalRows);
        });
    }

    private void processQueryTableResponse(QueryTableResponse response, boolean isFirstResponse) {
        if (!response.getTableResult().hasRowMapTable()) {
            return;
        }
        
        var rowMapTable = response.getTableResult().getRowMapTable();
        
        // Set up column names from first response
        if (isFirstResponse) {
            javafx.application.Platform.runLater(() -> {
                tableColumnNames.setAll(rowMapTable.getColumnNamesList());
            });
        }
        
        // Process rows
        List<ObservableList<Object>> newRows = new ArrayList<>();
        
        for (var dataRow : rowMapTable.getRowsList()) {
            ObservableList<Object> row = FXCollections.observableArrayList();
            
            for (String columnName : rowMapTable.getColumnNamesList()) {
                if (dataRow.containsColumnValues(columnName)) {
                    var value = dataRow.getColumnValuesMap().get(columnName);
                    
                    if (columnName.equals("timestamp")) {
                        // Convert protobuf Timestamp to formatted string
                        if (value.hasTimestampValue()) {
                            var timestamp = value.getTimestampValue();
                            Instant instant = Instant.ofEpochSecond(timestamp.getEpochSeconds(), timestamp.getNanoseconds());
                            LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
                            row.add(dateTime.format(TIMESTAMP_FORMATTER));
                        } else {
                            row.add("N/A");
                        }
                    } else {
                        // Handle other data types
                        if (value.hasIntValue()) {
                            row.add(value.getIntValue());
                        } else if (value.hasLongValue()) {
                            row.add(value.getLongValue());
                        } else if (value.hasDoubleValue()) {
                            row.add(value.getDoubleValue());
                        } else if (value.hasStringValue()) {
                            row.add(value.getStringValue());
                        } else {
                            row.add("N/A");
                        }
                    }
                } else {
                    row.add("N/A");
                }
            }
            
            newRows.add(row);
        }
        
        // Update table data on JavaFX thread
        if (!newRows.isEmpty()) {
            javafx.application.Platform.runLater(() -> {
                tableData.addAll(newRows);
            });
        }
    }

    private boolean isQueryValid() {
        if (pvNameList.isEmpty()) {
            return false;
        }
        
        if (queryBeginDate.get() == null || queryEndDate.get() == null) {
            return false;
        }
        
        try {
            if (!getQueryBeginDateTime().isBefore(getQueryEndDateTime())) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        
        return true;
    }
    
    private boolean isQueryValidWithMessages() {
        if (pvNameList.isEmpty()) {
            statusMessage.set("Please add at least one PV name");
            logger.warn("Query validation failed: no PV names specified");
            return false;
        }
        
        if (queryBeginDate.get() == null || queryEndDate.get() == null) {
            statusMessage.set("Please specify both begin and end times");
            logger.warn("Query validation failed: missing begin or end time");
            return false;
        }
        
        if (!getQueryBeginDateTime().isBefore(getQueryEndDateTime())) {
            statusMessage.set("Begin time must be before end time");
            logger.warn("Query validation failed: begin time {} not before end time {}", 
                getQueryBeginDateTime(), getQueryEndDateTime());
            return false;
        }
        
        return true;
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

    public void cancel() {
        logger.info("Data query cancelled by user");
        statusMessage.set("Operation cancelled");
    }

    public void updateStatus(String message) {
        statusMessage.set(message);
        logger.debug("Status updated: {}", message);
    }
    
    /**
     * Populates the Query Editor fields from a DataBlockDetail object.
     * Used by the "View Data" button in the Dataset Builder.
     */
    public void populateFromDataBlock(com.ospreydcs.dp.gui.model.DataBlockDetail dataBlock) {
        if (dataBlock == null) {
            logger.warn("Cannot populate from null data block");
            return;
        }
        
        logger.info("Populating Query Editor from data block: {}", dataBlock);
        
        // Set PV names
        if (dataBlock.getPvNames() != null) {
            pvNameList.setAll(dataBlock.getPvNames());
            logger.debug("Set {} PV names from data block", dataBlock.getPvNames().size());
        }
        
        // Set time range from data block
        if (dataBlock.getBeginTime() != null) {
            LocalDateTime beginDateTime = LocalDateTime.ofInstant(dataBlock.getBeginTime(), ZoneId.systemDefault());
            queryBeginDate.set(beginDateTime.toLocalDate());
            beginHour.set(beginDateTime.getHour());
            beginMinute.set(beginDateTime.getMinute());
            beginSecond.set(beginDateTime.getSecond());
            logger.debug("Set begin time from data block: {}", beginDateTime);
        }
        
        if (dataBlock.getEndTime() != null) {
            LocalDateTime endDateTime = LocalDateTime.ofInstant(dataBlock.getEndTime(), ZoneId.systemDefault());
            queryEndDate.set(endDateTime.toLocalDate());
            endHour.set(endDateTime.getHour());
            endMinute.set(endDateTime.getMinute());
            endSecond.set(endDateTime.getSecond());
            logger.debug("Set end time from data block: {}", endDateTime);
        }
        
        updateStatus("Query Editor populated from selected data block");
    }
}