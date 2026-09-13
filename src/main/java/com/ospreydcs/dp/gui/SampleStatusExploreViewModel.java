package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.common.SampleStatusBucket;
import com.ospreydcs.dp.gui.model.SampleStatusTableRow;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Search over stored sample statuses.
 *
 * <p>Follows the explore-view vocabulary settled in #39 T2b: {@code searchStatusMessage} beside the
 * search controls, {@code statusMessage} beside the results table, {@code resultCountMessage} as a
 * String (never an int — it has to be able to say "first N of more"), and {@code searchInProgress}.
 * The search task returns its result from {@code call()} and publishes it in {@code setOnSucceeded},
 * which already runs on the FX thread.
 */
public class SampleStatusExploreViewModel {

    private static final Logger logger = LogManager.getLogger();

    /**
     * Maximum rows rendered, independent of the bucket cap in
     * {@link DpApplication#querySampleStatusBuckets}.
     *
     * <p>The two caps are not redundant. Paging is by whole buckets, so the bucket cap cannot bound
     * the row count: one bucket may carry thousands of statuses, and a query returning a handful of
     * dense buckets can expand to far more rows than a TableView should hold. Without this second
     * bound the view would move the unbounded read from the server to the client, which is what
     * server paging exists to prevent.
     */
    static final int MAX_DISPLAYED_STATUSES = 10_000;

    /**
     * Status code labels, by domain.
     *
     * <p>Only {@code epics_alarm} is known, and only because this application's own demo generator
     * writes it ({@code DpApplication.SAMPLE_STATUS_DEMO_DOMAIN}). <strong>The domain registry is
     * unimplemented server-side</strong> — {@code saveSampleStatusDomain()} /
     * {@code querySampleStatusDomains()} are reserved in the proto and deferred — so nothing can
     * resolve a code to a label from the archive. A status in any other domain therefore renders its
     * raw code with an empty label rather than a guess, and the view says the labels are local.
     */
    static final Map<String, Map<Integer, String>> CODE_LABELS = Map.of(
            DpApplication.SAMPLE_STATUS_DEMO_DOMAIN, Map.of(
                    DpApplication.EPICS_ALARM_NO_ALARM, "NO_ALARM",
                    DpApplication.EPICS_ALARM_MINOR_ALARM, "MINOR_ALARM",
                    DpApplication.EPICS_ALARM_MAJOR_ALARM, "MAJOR_ALARM",
                    DpApplication.EPICS_ALARM_INVALID_ALARM, "INVALID_ALARM"));

    // Query form properties
    private final StringProperty pvNames = new SimpleStringProperty("");
    private final StringProperty domains = new SimpleStringProperty("");
    private final StringProperty layers = new SimpleStringProperty("");

    /**
     * The requested window, owned by the controller's date pickers and time spinners and pushed in
     * before a search, exactly as machine-configuration does for its activation times: the ViewModel
     * stays free of JavaFX control code.
     */
    private Instant beginTime;
    private Instant endTime;

    // Results
    private final ObservableList<SampleStatusTableRow> searchResults = FXCollections.observableArrayList();
    private final StringProperty resultCountMessage = new SimpleStringProperty("0 status(es)");
    private final BooleanProperty searchInProgress = new SimpleBooleanProperty(false);

    // Status
    private final StringProperty statusMessage = new SimpleStringProperty("Ready to search for sample statuses");
    private final StringProperty searchStatusMessage = new SimpleStringProperty("");

    private DpApplication dpApplication;
    private MainController mainController;

    public SampleStatusExploreViewModel() {
        logger.debug("SampleStatusExploreViewModel initialized");
    }

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        logger.debug("DpApplication injected into SampleStatusExploreViewModel");
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        logger.debug("MainController injected into SampleStatusExploreViewModel");
    }

    public void setTimeRange(Instant beginTime, Instant endTime) {
        this.beginTime = beginTime;
        this.endTime = endTime;
    }

    public Instant getBeginTime() { return beginTime; }
    public Instant getEndTime() { return endTime; }

    public StringProperty pvNamesProperty() { return pvNames; }
    public StringProperty domainsProperty() { return domains; }
    public StringProperty layersProperty() { return layers; }

    public ObservableList<SampleStatusTableRow> getSearchResults() { return searchResults; }
    public StringProperty resultCountMessageProperty() { return resultCountMessage; }
    public BooleanProperty searchInProgressProperty() { return searchInProgress; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public StringProperty searchStatusMessageProperty() { return searchStatusMessage; }

    /**
     * Runs the search.  Both times are required: an unbounded sample status query would scan the
     * whole archive, and the server takes a TimeRange rather than treating an absent range as
     * match-all.
     */
    public void executeSearch() {
        if (dpApplication == null) {
            searchStatusMessage.set("DpApplication not initialized");
            return;
        }
        if (beginTime == null || endTime == null) {
            searchStatusMessage.set("Start and end time are required");
            return;
        }
        if (!endTime.isAfter(beginTime)) {
            searchStatusMessage.set("End time must be after start time");
            return;
        }

        final Instant requestedBegin = beginTime;
        final Instant requestedEnd = endTime;
        final List<String> pvNameList = parseCommaSeparatedList(pvNames.get());
        final List<String> domainList = parseCommaSeparatedList(domains.get());
        final List<String> layerList = parseCommaSeparatedList(layers.get());

        searchInProgress.set(true);
        searchStatusMessage.set("Searching for sample statuses...");
        searchResults.clear();
        resultCountMessage.set("0 status(es)");

        final Task<SearchOutcome> searchTask = new Task<SearchOutcome>() {
            @Override
            protected SearchOutcome call() throws Exception {
                final DpApplication.PagedResult<SampleStatusBucket> buckets =
                        dpApplication.querySampleStatusBuckets(
                                requestedBegin, requestedEnd, pvNameList, domainList, layerList);

                // Expand and trim off the FX thread: a dense bucket set is thousands of rows, and
                // building them in the success handler would stall the UI for the whole decode.
                return expandBuckets(buckets, requestedBegin, requestedEnd);
            }
        };

        searchTask.setOnSucceeded(event -> {
            publishSearchResults(searchTask.getValue());
            searchInProgress.set(false);
        });

        searchTask.setOnFailed(event -> {
            final Throwable failure = searchTask.getException();
            logger.error("Sample status search failed", failure);
            searchStatusMessage.set("Search failed: " + failure.getMessage());
            statusMessage.set("Search failed: " + failure.getMessage());
            searchInProgress.set(false);
        });

        final Thread searchThread = new Thread(searchTask, "sample-status-search");
        searchThread.setDaemon(true);
        searchThread.start();
    }

    /**
     * Flattens the returned buckets into display rows, dropping statuses outside the requested
     * window and stopping at the row cap.
     *
     * <p>Package-private and static so the trimming and cap behavior are testable without a service
     * ecosystem — the same reasoning as {@code DpApplication.accumulatePages()}. Boundary trimming is
     * exactly the kind of off-by-one that produces plausible-looking wrong counts rather than an
     * obvious failure.
     */
    static SearchOutcome expandBuckets(
            DpApplication.PagedResult<SampleStatusBucket> buckets,
            Instant rangeBegin,
            Instant rangeEnd
    ) {
        final List<SampleStatusTableRow> rows = new ArrayList<>();
        boolean rowsTruncated = false;

        for (SampleStatusBucket bucket : buckets.records) {
            final List<SampleStatusTableRow> bucketRows =
                    SampleStatusTableRow.expand(bucket, rangeBegin, rangeEnd, CODE_LABELS);

            for (SampleStatusTableRow row : bucketRows) {
                if (rows.size() >= MAX_DISPLAYED_STATUSES) {
                    rowsTruncated = true;
                    break;
                }
                rows.add(row);
            }

            if (rowsTruncated) {
                break;
            }
        }

        // Either bound can truncate, and they mean different things -- more buckets existed on the
        // server, or more statuses existed in the buckets fetched.  Both are reported as truncation
        // because the distinction does not change what the user must know: this is not a total.
        return new SearchOutcome(rows, buckets.truncated, rowsTruncated);
    }

    private void publishSearchResults(SearchOutcome outcome) {
        searchResults.setAll(outcome.rows);

        final int count = outcome.rows.size();
        resultCountMessage.set(outcome.truncated()
                ? "first " + count + " status(es)"
                : count + " status(es)");

        searchStatusMessage.set("Search completed");
        statusMessage.set(describe(outcome));

        logger.info("Sample status search completed - {} statuses displayed "
                        + "(bucketsTruncated={}, rowsTruncated={})",
                count, outcome.bucketsTruncated, outcome.rowsTruncated);
    }

    /**
     * Builds the results-area status message, naming truncation and its cause.
     *
     * <p>A capped result presented as a total is the defect transparent paging exists to prevent, so
     * this never states a bare count when either bound tripped.
     */
    private static String describe(SearchOutcome outcome) {
        final int count = outcome.rows.size();

        if (outcome.rowsTruncated) {
            return String.format(
                    "Showing first %d status(es) of more - narrow the time range or PV list", count);
        }
        if (outcome.bucketsTruncated) {
            return String.format(
                    "Showing %d status(es) from the first %d buckets; more buckets available",
                    count, DpApplication.QUERY_RESULT_CAP);
        }
        return String.format("Found %d status(es)", count);
    }

    public void clearSearch() {
        pvNames.set("");
        domains.set("");
        layers.set("");
        searchResults.clear();
        resultCountMessage.set("0 status(es)");
        searchStatusMessage.set("Search cleared");
        statusMessage.set("Ready to search for sample statuses");
    }

    /**
     * The outcome of one search: the rows to display and why the result may be incomplete.
     *
     * <p>The two truncation flags are kept separate rather than collapsed into one boolean because
     * they have different remedies — more buckets on the server means a narrower filter, while more
     * statuses in the fetched buckets means a narrower time range.
     */
    static final class SearchOutcome {
        final List<SampleStatusTableRow> rows;
        final boolean bucketsTruncated;
        final boolean rowsTruncated;

        SearchOutcome(List<SampleStatusTableRow> rows, boolean bucketsTruncated, boolean rowsTruncated) {
            this.rows = rows;
            this.bucketsTruncated = bucketsTruncated;
            this.rowsTruncated = rowsTruncated;
        }

        boolean truncated() {
            return bucketsTruncated || rowsTruncated;
        }
    }

    /**
     * Splits a comma-separated field into criterion values, dropping blanks.
     *
     * <p>Returns an empty list for a blank field, which the wrapper converts to an omitted criterion
     * rather than an empty one — an empty pvNames list is how the API enumerates every PV a
     * (domain, layer) has labeled.
     */
    private static List<String> parseCommaSeparatedList(String input) {
        final List<String> values = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return values;
        }
        for (String part : input.split(",")) {
            final String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }
}
