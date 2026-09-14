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

    /**
     * Maximum rows rendered from a sample query, independent of any server-side bound.
     *
     * <p><strong>The server's page bound cannot stand in for this one.</strong>  It bounds a single
     * PAGE -- by a row count and a byte budget, whichever trips first -- and then hands back a
     * resume token, so following that token to exhaustion is an unbounded read no matter how small
     * each page is.  Without a client bound this view would move the unbounded read from the server
     * to the client, which is precisely what server paging exists to prevent; the same reasoning
     * gives {@link SampleStatusExploreViewModel#MAX_DISPLAYED_STATUSES} and
     * {@link DpApplication#QUERY_RESULT_CAP} their caps.
     *
     * <p>The bound matters more here than it did before the V2 migration.  The retired V1 path was
     * bounded in practice because the only way to choose PVs was to type them into the name list:
     * the row count was the user's own PV list times their own time range.  The pattern and
     * metadata selector arms remove that implicit ceiling, and an all-empty metadata query is NOT
     * rejected server-side -- it matches every PV in the archive (see {@link PvSelection}).  So the
     * one query path that can now resolve to the whole archive is the one that most needs a limit.
     *
     * <p>The server's {@code maxResolvedPvCount} catches only the extreme of that: a selector
     * resolving to just under the limit over a wide window is accepted and would otherwise
     * accumulate without end into an ObservableList on the FX thread.
     *
     * <p>Rows are capped rather than pages, because a page is a server-side accounting unit whose
     * size the client does not control; only a row count bounds what the table actually holds.
     */
    static final int MAX_DISPLAYED_ROWS = 50_000;

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

    /**
     * Whether the displayed rows are a prefix of the result rather than the whole of it, because
     * {@link #MAX_DISPLAYED_ROWS} tripped or the user cancelled.
     *
     * <p>Kept so the completion message can never state a bare count for a partial result.  A
     * truncated table reported as a total is the same defect transparent paging exists to prevent,
     * and here it is worse than a wrong number: a query capped mid-archive looks exactly like one
     * that genuinely had nothing more to return.
     */
    private final BooleanProperty resultsTruncated = new SimpleBooleanProperty(false);

    /**
     * The task running the current query, so {@link #cancel()} can actually stop it.
     *
     * <p>Held rather than discarded because the paging loop is otherwise uninterruptible: it
     * follows resume tokens until the server stops issuing them, and a whole-archive selection can
     * keep issuing them for a long time.  Written and read on the FX thread only (submitQuery(),
     * cancel() and the task's own completion handlers all run there), so it needs no
     * synchronization; the background loop reads the task's own {@code isCancelled()} flag, which
     * is thread-safe by contract.
     */
    private Task<QueryOutcome> runningQueryTask;
    
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

    /** Whether the displayed rows are a prefix of the result -- capped, or cancelled part way. */
    public BooleanProperty resultsTruncatedProperty() { return resultsTruncated; }
    public boolean isResultsTruncated() { return resultsTruncated.get(); }

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
        resultsTruncated.set(false);
        statusMessage.set("Querying data...");

        // The outcome is RETURNED from call() and applied here, never published from inside the
        // loop via Platform.runLater -- see QueryOutcome for why that ordering is load-bearing.
        Task<QueryOutcome> queryTask = new Task<QueryOutcome>() {
            @Override
            protected QueryOutcome call() throws Exception {
                return executeSamplesQuery(this);
            }
        };
        runningQueryTask = queryTask;

        queryTask.setOnSucceeded(e -> {
            runningQueryTask = null;

            // The RESULT is applied before the in-progress flag clears, never after.  Setting a
            // JavaFX property notifies its listeners synchronously, so anything watching
            // isQuerying -- the progress indicator, the row-count label, a test observing the
            // transition -- runs at that moment and must not see a stale count or a truncation
            // flag left over from the previous query.  This is the same ordering the explore view
            // models fixed as D-2, in its other direction.
            final QueryOutcome outcome = queryTask.getValue();
            totalRowsLoaded.set(outcome.totalRows());
            resultsTruncated.set(outcome.truncated());

            hasQueryResults.set(true);
            isQuerying.set(false);

            // describeResult(), not a bare row count: a capped or cancelled table reported as a
            // total reads exactly like a query that had nothing more to return.
            final String resultMessage = describeResult();

            // Update application state and notify home view
            if (dpApplication != null) {
                dpApplication.setHasPerformedQueries(true);
                dpApplication.setLastOperationResult(resultMessage);
            }

            if (mainController != null) {
                mainController.onQuerySuccess("Query completed: " + resultMessage);
            }

            statusMessage.set(resultsTruncated.get()
                    ? "Query stopped early - " + resultMessage
                    : "Query completed successfully");
            logger.info("Query completed with {} rows (truncated: {})",
                    totalRowsLoaded.get(), resultsTruncated.get());
        });

        queryTask.setOnFailed(e -> {
            runningQueryTask = null;
            logger.error("Query failed", queryTask.getException());

            // A failure can arrive after pages have already been displayed (a non-scalar PV is
            // rejected mid-assembly, so a later page can fail once rows are on screen).  Those rows
            // are real but incomplete, so they are counted and flagged rather than left reading as
            // a whole result -- and, as above, before the in-progress flag clears.
            totalRowsLoaded.set(tableData.size());
            resultsTruncated.set(!tableData.isEmpty());

            statusMessage.set("Query failed: " + queryTask.getException().getMessage());
            isQuerying.set(false);
        });

        // A cancelled query keeps the rows it already displayed -- they are real data for a real
        // sub-range of the request -- but must never present them as the complete result, which is
        // what resultsTruncated records.
        queryTask.setOnCancelled(e -> {
            runningQueryTask = null;

            // A cancelled task discards call()'s return value, so the count comes from the table
            // itself -- the one place that is authoritative either way, since every page published
            // to it did so via runLater and those blocks have all run by the time this handler does.
            //
            // Applied BEFORE isQuerying clears, for the same reason as the success path: a listener
            // on that flag runs synchronously at the moment it changes.
            totalRowsLoaded.set(tableData.size());
            resultsTruncated.set(true);
            hasQueryResults.set(tableData.size() > 0);

            statusMessage.set("Query cancelled - " + describeResult());
            isQuerying.set(false);
            logger.info("Query cancelled after {} row(s)", tableData.size());
        });

        Thread queryThread = new Thread(queryTask);
        queryThread.setDaemon(true);
        queryThread.start();
    }

    /**
     * Describes the result for the completion message, naming truncation and its cause.
     *
     * <p>Never states a bare count for a partial result: a capped table presented as a total is the
     * defect transparent paging exists to prevent, and a cancelled one presented as a total is the
     * same defect with a different cause.  The scope is always included, because a row count quoted
     * without the active filters reads as an unfiltered result -- and a status filter is exactly
     * the thing that makes a small count expected rather than suspicious.
     */
    String describeResult() {
        final int rows = totalRowsLoaded.get();
        if (resultsTruncated.get()) {
            return "showing first " + rows + " row(s) of more for " + describeQueryScope()
                    + " - narrow the time range or the PV selection for the rest";
        }
        return rows + " row(s) for " + describeQueryScope();
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
     *
     * <p><strong>The loop is bounded twice, and neither bound is the server's.</strong>  It stops at
     * {@link #MAX_DISPLAYED_ROWS}, because following resume tokens to exhaustion is an unbounded
     * read however small each page is; and it stops when the task is cancelled, because a
     * whole-archive selection can keep issuing tokens for longer than a user is willing to wait.
     * Both stops set {@code resultsTruncated}, so the rows already displayed are never reported as
     * a complete result.
     *
     * <p>Cancellation is checked at the top of each iteration rather than mid-page: a page is one
     * unary round trip that cannot be interrupted once issued, so the finest honest granularity is
     * per page.  The rows from a page already received are kept rather than discarded -- they are
     * real data for a real sub-range of the request.
     *
     * @param task the running task, polled for cancellation between pages
     */
    private QueryOutcome executeSamplesQuery(Task<?> task) throws Exception {
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
        boolean truncated = false;

        do {
            // Between pages, not mid-page: a page is one unary round trip that cannot be
            // interrupted once issued.  Checking before the call means a cancel that lands while
            // the previous page was in flight costs no further round trip.
            if (task != null && task.isCancelled()) {
                logger.info("Query cancelled after {} page(s), {} row(s)", pageCount, totalRows);
                truncated = true;
                break;
            }

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

            List<ObservableList<Object>> pageRows = reshapePage(columnTable);

            // Trim the page that crosses the cap rather than dropping it whole: the rows before the
            // boundary are as real as any other, and discarding them would under-report the count
            // for no benefit.
            if (totalRows + pageRows.size() >= MAX_DISPLAYED_ROWS) {
                pageRows = pageRows.subList(0, MAX_DISPLAYED_ROWS - totalRows);
                truncated = true;
            }

            totalRows += pageRows.size();

            if (!pageRows.isEmpty()) {
                final List<ObservableList<Object>> rowsToPublish = pageRows;
                javafx.application.Platform.runLater(() -> tableData.addAll(rowsToPublish));
            }

            pageToken = apiResult.nextPageToken;
            logger.debug("Page {} yielded {} row(s); nextPageToken present: {}",
                    pageCount, pageRows.size(), pageToken != null && !pageToken.isEmpty());

            if (truncated) {
                logger.info("Query stopped at the {}-row display cap with more rows available",
                        MAX_DISPLAYED_ROWS);
                break;
            }

        } while (pageToken != null && !pageToken.isEmpty());

        return new QueryOutcome(totalRows, truncated);
    }

    /**
     * What a completed paging loop produced: how many rows reached the table, and whether they are
     * the whole result.
     *
     * <p>Returned from the task's {@code call()} and applied in {@code setOnSucceeded}, never
     * published from inside the loop via {@code Platform.runLater}.  That distinction is the D-2
     * defect this codebase already fixed once in the explore view models: {@code setOnSucceeded}
     * runs on the FX thread and therefore runs BEFORE a block queued from the background thread, so
     * a completion handler reading state published that way sees the pre-query values -- here, a
     * row count of zero and a truncation flag of false, which is precisely the "capped result
     * reported as a total" this cap exists to prevent.
     */
    private record QueryOutcome(int totalRows, boolean truncated) {
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
            // uint32 widens into a long without loss, so it stays a Number and the chart plots it
            // directly.  uint64 cannot: its upper half exceeds Long.MAX_VALUE, and there is no
            // Java integral type to hold it, so it is rendered as an unsigned decimal STRING.
            // That is a deliberate asymmetry, not an oversight -- the alternatives are worse.
            // Returning the signed long would display a large reading as a negative number, and
            // converting to double would silently round past 2^53, turning an exact archived
            // reading into a nearby wrong one.  The chart's parseNumericValue() still parses the
            // string, so such a column plots (as a double, with that rounding) while the TABLE --
            // which is what a reading is actually read from -- keeps every digit exact.
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


    /**
     * Stops a running query.
     *
     * <p>This used to set a status message and nothing else, so a query in flight kept following
     * resume tokens after the user had asked it to stop -- and the message claimed otherwise.  That
     * mattered little while the only way to choose PVs was to type them, but the V2 selector arms
     * made "cancel" the natural remedy for a pattern or metadata selection that turned out to cover
     * far more of the archive than intended, and it was the one remedy that did not work.
     *
     * <p>The task's own {@code setOnCancelled} handler owns the resulting state, so cancelling
     * twice, or cancelling when nothing is running, is harmless.
     */
    public void cancel() {
        final Task<QueryOutcome> task = runningQueryTask;
        if (task == null || task.isDone()) {
            logger.debug("Cancel requested with no query running");
            statusMessage.set("Operation cancelled");
            return;
        }

        logger.info("Data query cancelled by user");
        // The loop polls isCancelled() between pages; it cannot abandon a round trip already in
        // flight, so the last page issued still arrives and is discarded by the task.
        task.cancel(false);
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