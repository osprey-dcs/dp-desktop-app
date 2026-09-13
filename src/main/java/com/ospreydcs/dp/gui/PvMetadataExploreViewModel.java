package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.criteria.AttributeCriterion;
import com.ospreydcs.dp.client.criteria.TextMatch;
import com.ospreydcs.dp.grpc.v1.common.PvMetadata;
import com.ospreydcs.dp.gui.model.PvMetadataTableRow;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Search over PV metadata records.
 *
 * <p>Follows the explore-view vocabulary settled in #39 T2b, and like the other explore views the
 * search task returns its result from {@code call()} and publishes it in {@code setOnSucceeded},
 * which already runs on the FX thread.
 *
 * <p><strong>There is deliberately no free-text field.</strong> This API's criteria are pvName,
 * aliases, tags and attributes — it has no {@code TextCriterion}, unlike the dataset and annotation
 * queries. A "search everything" box would have nothing to bind to, and offering one would promise a
 * search that silently matched nothing, which is the trap the annotation view's event field fell
 * into before it was removed.
 */
public class PvMetadataExploreViewModel {

    private static final Logger logger = LogManager.getLogger();

    /**
     * How a typed name is matched.
     *
     * <p>Exposed as an explicit choice rather than guessed from the input, because the three modes
     * return very different result sets and a hidden heuristic would make the difference look like a
     * server inconsistency.
     */
    public enum MatchMode {
        CONTAINS("Contains"),
        PREFIX("Starts with"),
        EXACT("Exact");

        private final String label;

        MatchMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // Query form
    private final StringProperty pvNameText = new SimpleStringProperty("");
    private final StringProperty aliasText = new SimpleStringProperty("");
    private final StringProperty tagsText = new SimpleStringProperty("");
    private final StringProperty attributeKey = new SimpleStringProperty("");
    private final StringProperty attributeValue = new SimpleStringProperty("");

    private MatchMode pvNameMatchMode = MatchMode.CONTAINS;
    private MatchMode aliasMatchMode = MatchMode.CONTAINS;

    // Results
    private final ObservableList<PvMetadataTableRow> searchResults = FXCollections.observableArrayList();
    private final StringProperty resultCountMessage = new SimpleStringProperty("0 PV(s)");
    private final BooleanProperty searchInProgress = new SimpleBooleanProperty(false);

    // Status
    private final StringProperty statusMessage = new SimpleStringProperty("Ready to search for PV metadata");
    private final StringProperty searchStatusMessage = new SimpleStringProperty("");

    private DpApplication dpApplication;
    private MainController mainController;

    public PvMetadataExploreViewModel() {
        logger.debug("PvMetadataExploreViewModel initialized");
    }

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        logger.debug("DpApplication injected into PvMetadataExploreViewModel");
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void setPvNameMatchMode(MatchMode mode) {
        this.pvNameMatchMode = mode != null ? mode : MatchMode.CONTAINS;
    }

    public void setAliasMatchMode(MatchMode mode) {
        this.aliasMatchMode = mode != null ? mode : MatchMode.CONTAINS;
    }

    public StringProperty pvNameTextProperty() { return pvNameText; }
    public StringProperty aliasTextProperty() { return aliasText; }
    public StringProperty tagsTextProperty() { return tagsText; }
    public StringProperty attributeKeyProperty() { return attributeKey; }
    public StringProperty attributeValueProperty() { return attributeValue; }

    public ObservableList<PvMetadataTableRow> getSearchResults() { return searchResults; }
    public StringProperty resultCountMessageProperty() { return resultCountMessage; }
    public BooleanProperty searchInProgressProperty() { return searchInProgress; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public StringProperty searchStatusMessageProperty() { return searchStatusMessage; }

    public void executeSearch() {
        if (dpApplication == null) {
            searchStatusMessage.set("DpApplication not initialized");
            return;
        }

        final TextMatch pvNameMatch = textMatch(pvNameText.get(), pvNameMatchMode);
        final TextMatch aliasMatch = textMatch(aliasText.get(), aliasMatchMode);
        final List<String> tags = parseCommaSeparatedList(tagsText.get());
        final List<AttributeCriterion> attributes = attributeCriteria();

        searchInProgress.set(true);
        searchStatusMessage.set("Searching for PV metadata...");
        searchResults.clear();
        resultCountMessage.set("0 PV(s)");

        final Task<DpApplication.PagedResult<PvMetadata>> searchTask =
                new Task<DpApplication.PagedResult<PvMetadata>>() {
            @Override
            protected DpApplication.PagedResult<PvMetadata> call() throws Exception {
                return dpApplication.queryPvMetadata(pvNameMatch, aliasMatch, tags, attributes);
            }
        };

        searchTask.setOnSucceeded(event -> {
            publishSearchResults(searchTask.getValue());
            searchInProgress.set(false);
        });

        searchTask.setOnFailed(event -> {
            final Throwable failure = searchTask.getException();
            logger.error("PV metadata search failed", failure);
            searchStatusMessage.set("Search failed: " + failure.getMessage());
            statusMessage.set("Search failed: " + failure.getMessage());
            searchInProgress.set(false);
        });

        final Thread searchThread = new Thread(searchTask, "pv-metadata-search");
        searchThread.setDaemon(true);
        searchThread.start();
    }

    private void publishSearchResults(DpApplication.PagedResult<PvMetadata> pagedResult) {
        final List<PvMetadataTableRow> rows = new ArrayList<>();
        for (PvMetadata record : pagedResult.records) {
            rows.add(new PvMetadataTableRow(record));
        }
        searchResults.setAll(rows);

        resultCountMessage.set(pagedResult.truncated
                ? "first " + rows.size() + " PV(s)"
                : rows.size() + " PV(s)");
        searchStatusMessage.set("Search completed");
        // report the count as a total only when the query was not capped, so a truncated result is
        // never presented as a complete one
        statusMessage.set(capitalize(pagedResult.describeCount("PV")));

        logger.info("PV metadata search completed - {} records (truncated={})",
                rows.size(), pagedResult.truncated);
    }

    /**
     * Builds the {@link TextMatch} for one field.
     *
     * <p><strong>A blank field yields an all-null TextMatch, which contributes no criterion at
     * all.</strong> This is the single most important thing about this method: the request builder
     * drops blank entries, and a criterion whose lists are all empty is rejected by the server —
     * while a blank <em>prefix</em> that did reach the server would compile to a regex matching
     * everything. Passing the raw field through, unpadded and unprocessed, is what keeps an unfilled
     * optional field from becoming either a rejection or a whole-collection scan.
     *
     * <p>Public rather than package-private because the Query Editor's PV selector dialog
     * (com.ospreydcs.dp.gui.component.PvSelectorDialogController) builds the same criteria for the
     * metadata arm of a V2 PvSelector.  Reimplementing it there would let the two copies drift on
     * precisely the blank-field handling this javadoc exists to pin down.
     */
    public static TextMatch textMatch(String text, MatchMode mode) {
        if (text == null || text.isBlank()) {
            return new TextMatch(null, null, null);
        }

        final List<String> values = parseCommaSeparatedList(text);
        if (values.isEmpty()) {
            return new TextMatch(null, null, null);
        }

        return switch (mode == null ? MatchMode.CONTAINS : mode) {
            case EXACT -> new TextMatch(values, null, null);
            case PREFIX -> new TextMatch(null, values, null);
            case CONTAINS -> new TextMatch(null, null, values);
        };
    }

    /**
     * Builds the attribute criteria.
     *
     * <p>A key with no value is a key-only existence search, which the criterion supports directly.
     * A value with no key cannot be expressed at all — the key is required — so it is dropped rather
     * than silently searching for the value as a key.
     */
    private List<AttributeCriterion> attributeCriteria() {
        final String key = attributeKey.get();
        if (key == null || key.isBlank()) {
            return List.of();
        }

        final List<String> values = parseCommaSeparatedList(attributeValue.get());
        return List.of(new AttributeCriterion(key.trim(), values.isEmpty() ? null : values));
    }

    /** True when the form would send no criteria at all, which returns the whole collection. */
    public boolean hasNoCriteria() {
        return textMatch(pvNameText.get(), pvNameMatchMode).isEmpty()
                && textMatch(aliasText.get(), aliasMatchMode).isEmpty()
                && parseCommaSeparatedList(tagsText.get()).isEmpty()
                && attributeCriteria().isEmpty();
    }

    public void clearSearch() {
        pvNameText.set("");
        aliasText.set("");
        tagsText.set("");
        attributeKey.set("");
        attributeValue.set("");
        searchResults.clear();
        resultCountMessage.set("0 PV(s)");
        searchStatusMessage.set("Search cleared");
        statusMessage.set("Ready to search for PV metadata");
    }

    /**
     * Opens the PV metadata editor loaded with this record.
     *
     * <p>The record carries the <strong>canonical</strong> pvName, which is what the editor must save
     * under: a search by alias returns the record under its canonical name, and
     * {@code savePvMetadata()} is a full-replace upsert keyed on that name.
     */
    public void editPvMetadata(PvMetadataTableRow row) {
        if (row == null || row.getPvMetadata() == null) {
            return;
        }
        if (mainController == null) {
            logger.warn("MainController is null, cannot open the PV metadata editor");
            return;
        }
        mainController.navigateToPvMetadataEditor(row.getPvMetadata());
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Splits a comma-separated field into trimmed, non-blank values.  Public alongside {@link
     * #textMatch}, and for the same reason: the PV selector dialog splits tag and attribute-value
     * fields the same way, and two copies would be free to disagree about blanks.
     */
    public static List<String> parseCommaSeparatedList(String input) {
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
