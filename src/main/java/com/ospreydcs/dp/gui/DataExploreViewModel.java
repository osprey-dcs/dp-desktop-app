package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.QueryClient;
import com.ospreydcs.dp.client.result.QueryPvStatsApiResult;
import com.ospreydcs.dp.client.result.QuerySamplesApiResult;
import com.ospreydcs.dp.grpc.v1.common.DataColumn;
import com.ospreydcs.dp.grpc.v1.common.DataValue;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.grpc.v1.query.ColumnTable;
import com.ospreydcs.dp.grpc.v1.query.QueryPvStatsResponse;
import com.ospreydcs.dp.gui.model.ConfigurationFilter;
import com.ospreydcs.dp.gui.model.PvSelection;
import com.ospreydcs.dp.gui.model.SampleStatusFilter;
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

    /**
     * Name of the synthesized time-axis column.
     *
     * A V2 ColumnTable has no timestamp column -- the axis lives only in its timestampList -- but
     * the results table sizes this column specially and the chart locates its X axis by this
     * literal name.  Naming it once here keeps the reshape and those two consumers from drifting.
     */
    static final String TIMESTAMP_COLUMN_NAME = "timestamp";

    // Query Specification properties
    private final ObservableList<String> pvNameList = FXCollections.observableArrayList();

    /**
     * How the query chooses its PVs.
     *
     * <p>Defaults to name-list, and {@link #pvNameList} above remains the identity path: it is the
     * list bound to the PV ListView, synchronized with DpApplication's global PV state, and written
     * by the Dataset Builder, the PV Explore view, the Provider Explore view and the data-event
     * hyperlink.  The pattern and metadata modes are strictly additive -- they change what the
     * query SENDS and never write back into that list, so none of those five flows has to know this
     * property exists.
     *
     * <p>Kept in the list's own {@link #pvNameList} rather than copied into the selection, so there
     * is one source of truth for the names; see PvSelection.
     */
    private final ObjectProperty<PvSelection> pvSelection =
            new SimpleObjectProperty<>(PvSelection.nameList());

    /**
     * Optional restriction to the intervals during which matching machine configurations were
     * active.  Narrows the TIME axis, so it composes with {@link #pvSelection} rather than
     * competing with it, and defaults to no restriction.
     */
    private final ObjectProperty<ConfigurationFilter> configurationFilter =
            new SimpleObjectProperty<>(ConfigurationFilter.none());

    /**
     * Optional restriction to samples carrying (or not carrying) a matching sample status.  Narrows
     * individual samples out of whatever the other two leave, and defaults to no restriction.
     */
    private final ObjectProperty<SampleStatusFilter> sampleStatusFilter =
            new SimpleObjectProperty<>(SampleStatusFilter.none());

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

        // The selection decides WHETHER the name list is what validity depends on
        pvSelection.addListener((obs, oldVal, newVal) -> updateValidation());

        // Only the status filter has a client-checkable rule (a required domain); the configuration
        // filter is listened to anyway so that a future rule cannot be added without the validation
        // silently not running.
        configurationFilter.addListener((obs, oldVal, newVal) -> updateValidation());
        sampleStatusFilter.addListener((obs, oldVal, newVal) -> updateValidation());
        
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
        
        // Initialize PV names from previously ingested data (generated or imported)
        if (dpApplication.getPvNames() != null && !dpApplication.getPvNames().isEmpty()) {
            pvNameList.setAll(dpApplication.getPvNames());
            logger.debug("Initialized PV name list with {} PVs from global state", dpApplication.getPvNames().size());
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
    public ObjectProperty<PvSelection> pvSelectionProperty() { return pvSelection; }
    public PvSelection getPvSelection() { return pvSelection.get(); }

    /**
     * Replaces the PV selection.  A null selection restores the name-list default rather than
     * leaving the query with no selector, which the server rejects.
     */
    public void setPvSelection(PvSelection selection) {
        pvSelection.set(selection == null ? PvSelection.nameList() : selection);
        updateValidation();
        logger.debug("PV selection set to {}", pvSelection.get().describe(pvNameList));
    }

    /** A one-line description of what the current selection covers, for labels and status text. */
    public String describePvSelection() {
        return pvSelection.get().describe(pvNameList);
    }

    public ObjectProperty<ConfigurationFilter> configurationFilterProperty() { return configurationFilter; }
    public ConfigurationFilter getConfigurationFilter() { return configurationFilter.get(); }

    /**
     * Replaces the configuration filter.  A null filter restores "no restriction", which is the
     * only safe default: the alternative empty form is rejected by the server rather than ignored.
     */
    public void setConfigurationFilter(ConfigurationFilter filter) {
        configurationFilter.set(filter == null ? ConfigurationFilter.none() : filter);
        updateValidation();
        logger.debug("Configuration filter set to {}", configurationFilter.get().describe());
    }

    public ObjectProperty<SampleStatusFilter> sampleStatusFilterProperty() { return sampleStatusFilter; }
    public SampleStatusFilter getSampleStatusFilter() { return sampleStatusFilter.get(); }

    /** Replaces the sample status filter.  A null filter restores "no restriction". */
    public void setSampleStatusFilter(SampleStatusFilter filter) {
        sampleStatusFilter.set(filter == null ? SampleStatusFilter.none() : filter);
        updateValidation();
        logger.debug("Sample status filter set to {}", sampleStatusFilter.get().describe());
    }

    /**
     * A one-line description of the whole query scope -- PVs, then whichever filters are active.
     *
     * <p>Inactive filters are omitted rather than described as "no filter", so the sentence stays
     * readable in the common unfiltered case while an active filter is always visible.
     */
    public String describeQueryScope() {
        final StringBuilder description = new StringBuilder(describePvSelection());
        if (configurationFilter.get().isActive()) {
            description.append(", ").append(configurationFilter.get().describe());
        }
        if (sampleStatusFilter.get().isActive()) {
            description.append(", ").append(sampleStatusFilter.get().describe());
        }
        return description.toString();
    }

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




    public LocalDateTime getQueryBeginDateTime() {
        LocalTime beginTime = LocalTime.of(beginHour.get(), beginMinute.get(), beginSecond.get());
        return LocalDateTime.of(queryBeginDate.get(), beginTime);
    }

    public LocalDateTime getQueryEndDateTime() {
        LocalTime endTime = LocalTime.of(endHour.get(), endMinute.get(), endSecond.get());
        LocalDateTime endDateTime = LocalDateTime.of(queryEndDate.get(), endTime);
        // Add 999,999,999 nanoseconds (almost 1 full second) to make the end time truly inclusive
        // This ensures we capture data up to XX:XX:XX.999999999 instead of truncating at XX:XX:XX.000000000
        return endDateTime.plusNanos(999_999_999);
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
                executeSamplesQuery();
                return null;
            }
        };

        queryTask.setOnSucceeded(e -> {
            isQuerying.set(false);
            hasQueryResults.set(true);
            
            // Update application state and notify home view
            if (dpApplication != null) {
                dpApplication.setHasPerformedQueries(true);
                // describeQueryScope(), not describePvSelection(): a row count reported without
                // the active filters reads as an unfiltered result, and a status filter is exactly
                // the thing that makes a small count expected rather than suspicious.
                String resultMessage = "Successfully queried " + totalRowsLoaded.get()
                    + " row(s) for " + describeQueryScope();
                dpApplication.setLastOperationResult(resultMessage);
            }
            
            if (mainController != null) {
                String resultMessage = "Query completed: " + totalRowsLoaded.get()
                    + " row(s) for " + describeQueryScope();
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

    /**
     * Runs the query as a sequence of Query API V2 sample pages, publishing each page as it
     * arrives.
     *
     * <p><strong>The 1-minute interval chopping this replaced is gone, not relocated.</strong>  It
     * existed only to dodge the gRPC message size limit by making each request small enough to fit.
     * The server now bounds a page itself -- by a row count and an outgoing byte budget, whichever
     * trips first -- and hands back a resume token, so the client no longer has to guess a window
     * size.  Guessing was also wrong in both directions: a minute of a fast PV could still overflow,
     * while a minute of a slow one cost a round trip to return nothing.
     *
     * <p>The page token is never carried in from a previous query.  It encodes a position only, and
     * replaying one against a changed time range or PV list yields a well-formed but wrong result
     * rather than an error, so each query starts from null.
     */
    private void executeSamplesQuery() throws Exception {
        final Instant beginInstant = getQueryBeginDateTime().atZone(ZoneId.systemDefault()).toInstant();
        final Instant endInstant = getQueryEndDateTime().atZone(ZoneId.systemDefault()).toInstant();
        // Read on the FX thread's behalf before the loop: the selection and the observable name
        // list both belong to the UI, and the loop below runs on a background thread.
        final QueryClient.PvSelectorParams pvSelector =
                pvSelection.get().toSelectorParams(new ArrayList<>(pvNameList));
        // Null, not an empty list, when no configuration restriction is asked for -- an empty list
        // is a rejected request rather than an unfiltered one.  ConfigurationFilter.toCriteria()
        // encodes that; this call site must not "helpfully" substitute List.of().
        final List<QueryClient.ConfigurationCriterion> configurationCriteria =
                configurationFilter.get().toCriteria();
        final QueryClient.SampleStatusSelectorParams statusSelector =
                sampleStatusFilter.get().toSelectorParams();
        final String selectionDescription = describeQueryScope();

        logger.debug("Query time range: {} to {} selecting {}",
                beginInstant, endInstant, selectionDescription);

        String pageToken = null;
        boolean firstPage = true;
        int totalRows = 0;
        int pageCount = 0;

        do {
            final QuerySamplesApiResult apiResult =
                    dpApplication.querySamples(pvSelector, configurationCriteria, statusSelector,
                            beginInstant, endInstant, pageToken);

            if (apiResult == null) {
                throw new RuntimeException("Query failed - null response from service");
            }

            if (apiResult.resultStatus.isError) {
                // Surfaced verbatim and never retried.  The two server-side failures worth naming
                // here -- an oversized single row, and a non-scalar PV rejected mid-assembly -- are
                // both retry-proof, and the message is the only thing that says which PV or which
                // timestamp is at fault.  A non-scalar rejection can also arrive on a LATER page,
                // after rows are already displayed, which is why this reports the rows kept so far
                // rather than implying the table is empty.
                throw new RuntimeException(pageCount == 0
                        ? "Query failed: " + apiResult.resultStatus.msg
                        : "Query failed after " + totalRows + " row(s) on page " + pageCount
                                + ": " + apiResult.resultStatus.msg);
            }

            final ColumnTable columnTable = apiResult.columnTable;
            if (columnTable == null) {
                throw new RuntimeException("Query failed - null table in response");
            }

            pageCount++;

            if (firstPage) {
                final List<String> columnNames = columnNamesOf(columnTable);
                javafx.application.Platform.runLater(() -> tableColumnNames.setAll(columnNames));
                firstPage = false;
            }

            final List<ObservableList<Object>> pageRows = reshapePage(columnTable);
            totalRows += pageRows.size();

            if (!pageRows.isEmpty()) {
                javafx.application.Platform.runLater(() -> tableData.addAll(pageRows));
            }

            pageToken = apiResult.nextPageToken;
            logger.debug("Page {} yielded {} row(s); nextPageToken present: {}",
                    pageCount, pageRows.size(), pageToken != null && !pageToken.isEmpty());

        } while (pageToken != null && !pageToken.isEmpty());

        final int finalTotalRows = totalRows;
        javafx.application.Platform.runLater(() -> totalRowsLoaded.set(finalTotalRows));
    }

    /**
     * The displayed column names for a V2 sample table: the synthesized timestamp column, then the
     * PV columns in the order the server returned them (bare PV names, sorted ascending and
     * deduped).
     *
     * <p><strong>The timestamp column is synthesized, not received.</strong>  A V2 ColumnTable has
     * no timestamp column -- the axis lives only in its timestampList -- whereas the V1 ROW_MAP
     * table this replaced carried "timestamp" as an ordinary column.  The table and chart both
     * locate the time axis by that literal name, so the reshape prepends it rather than changing
     * them.
     */
    static List<String> columnNamesOf(ColumnTable columnTable) {
        final List<String> columnNames = new ArrayList<>();
        columnNames.add(TIMESTAMP_COLUMN_NAME);
        for (DataColumn dataColumn : columnTable.getDataColumnsList()) {
            columnNames.add(dataColumn.getName());
        }
        return columnNames;
    }

    /**
     * Reshapes one column-oriented V2 page into the row-oriented structure the table and chart
     * consume, transposing the columns against the page's timestamp axis.
     *
     * <p>Static and free of any JavaFX or service reference so the transpose is unit-testable
     * without a service ecosystem, for the same reason as accumulatePages() and
     * SampleStatusTableRow.expand(): this is the point where a wrong answer looks plausible rather
     * than failing loudly.
     *
     * <p><strong>A missing value renders as blank, not "N/A".</strong>  The server encodes "this PV
     * had no sample at this timestamp" as an UNSET DataValue oneof, which is a real distinction the
     * V1 path could not make -- there, a column absent from a row map and a value the decoder did
     * not recognize both became the string "N/A", so genuinely missing data was indistinguishable
     * from a decode gap.  Blank says the former; anything else appearing in a cell now means the
     * latter.
     *
     * <p><strong>The row count comes from the timestamp axis, not from the columns.</strong>  The
     * server guarantees exactly one DataValue per column per timestamp, but reading a length from a
     * column would silently truncate every row of the page if that guarantee were ever broken,
     * whereas indexing past a short column is caught here and rendered blank.
     */
    static List<ObservableList<Object>> reshapePage(ColumnTable columnTable) {
        final List<Timestamp> timestamps = columnTable.getTimestampList().getTimestampsList();
        final List<DataColumn> dataColumns = columnTable.getDataColumnsList();

        final List<ObservableList<Object>> rows = new ArrayList<>(timestamps.size());

        for (int rowIndex = 0; rowIndex < timestamps.size(); rowIndex++) {
            final ObservableList<Object> row = FXCollections.observableArrayList();

            final Timestamp timestamp = timestamps.get(rowIndex);
            final Instant instant =
                    Instant.ofEpochSecond(timestamp.getEpochSeconds(), timestamp.getNanoseconds());
            row.add(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER));

            for (DataColumn dataColumn : dataColumns) {
                if (rowIndex >= dataColumn.getDataValuesCount()) {
                    // short column -- see the row-count note above
                    row.add("");
                    continue;
                }
                row.add(renderDataValue(dataColumn.getDataValues(rowIndex)));
            }

            rows.add(row);
        }

        return rows;
    }

    /**
     * Renders one sample for display, returning a Number where the value is numeric so the chart
     * can plot it without reparsing a string.
     *
     * <p>An unset value oneof -- the server's encoding for a PV with no sample at this timestamp --
     * renders as blank.  So does a value this decoder has no scalar rendering for; the samples path
     * rejects non-scalar PVs server-side, so such a value should not reach here at all.
     */
    static Object renderDataValue(DataValue dataValue) {
        return switch (dataValue.getValueCase()) {
            case STRINGVALUE -> dataValue.getStringValue();
            case BOOLEANVALUE -> dataValue.getBooleanValue();
            case UINTVALUE -> Integer.toUnsignedLong(dataValue.getUintValue());
            case ULONGVALUE -> Long.toUnsignedString(dataValue.getUlongValue());
            case INTVALUE -> dataValue.getIntValue();
            case LONGVALUE -> dataValue.getLongValue();
            case FLOATVALUE -> dataValue.getFloatValue();
            case DOUBLEVALUE -> dataValue.getDoubleValue();
            case TIMESTAMPVALUE -> {
                final Timestamp nested = dataValue.getTimestampValue();
                yield LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(nested.getEpochSeconds(), nested.getNanoseconds()),
                        ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER);
            }
            default -> "";
        };
    }

    /**
     * Whether the non-name-list part of the selection can be sent.
     *
     * <p>Only the pattern mode has a client-checkable requirement: the server rejects a blank
     * pattern, and a blank one here is an unfilled field rather than an intent to match nothing.
     * The metadata mode deliberately has none -- an all-empty metadata query is NOT an error
     * server-side, it matches every PV in the archive -- so refusing it here would invent a rule the
     * server does not have.  PvSelection.describe() states that case in words instead, which is the
     * honest treatment of a selection that is valid but far broader than it looks.
     */
    private boolean isPvSelectionValid() {
        final PvSelection selection = pvSelection.get();
        if (selection.getMode() == PvSelection.Mode.NAME_PATTERN) {
            final String pattern = selection.getNamePattern();
            return pattern != null && !pattern.isBlank();
        }
        return true;
    }

    private boolean isQueryValid() {
        // Only the name-list mode depends on the list.  A pattern or metadata selection resolves
        // server-side, so requiring names there would disable Submit for a perfectly valid query.
        if (pvSelection.get().isNameList() && pvNameList.isEmpty()) {
            return false;
        }

        if (!isPvSelectionValid()) {
            return false;
        }

        if (!sampleStatusFilter.get().isComplete()) {
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
        if (pvSelection.get().isNameList() && pvNameList.isEmpty()) {
            statusMessage.set("Please add at least one PV name");
            logger.warn("Query validation failed: no PV names specified");
            return false;
        }

        if (!isPvSelectionValid()) {
            statusMessage.set("Please enter a PV name pattern, or switch back to a name list");
            logger.warn("Query validation failed: blank PV name pattern");
            return false;
        }

        // The status filter's domain is the only client-checkable rule among the two filters.  The
        // configuration filter has none: its inactive form is expressed by sending no selector at
        // all, so there is no incomplete state for it to be in.
        if (!sampleStatusFilter.get().isComplete()) {
            statusMessage.set("Please enter a sample status domain, or turn the status filter off");
            logger.warn("Query validation failed: sample status filter with no domain");
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