package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.criteria.AttributeCriterion;
import com.ospreydcs.dp.client.criteria.TextMatch;
import com.ospreydcs.dp.grpc.v1.common.Configuration;
import com.ospreydcs.dp.grpc.v1.common.ConfigurationActivation;
import com.ospreydcs.dp.gui.model.ConfigurationActivationTableRow;
import com.ospreydcs.dp.gui.model.ConfigurationTableRow;
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

/**
 * Search over machine configuration records and their activations.
 *
 * <p>This view carries <strong>two independent searches</strong>, not one search with two result
 * tables. A configuration and an activation are separate records with disjoint criteria, and either
 * is useful on its own — "what configurations exist in this category" and "what was active during
 * this window" are different questions. Each search therefore has its own criteria, its own
 * in-progress flag, and its own pair of status labels, following the T2b vocabulary per search
 * rather than per view.
 *
 * <p>Both searches follow the explore-view lifecycle settled in T2b: the task returns its result
 * from {@code call()} and publishes it in {@code setOnSucceeded}, which already runs on the FX
 * thread.
 */
public class ConfigurationExploreViewModel {

    private static final Logger logger = LogManager.getLogger();

    /**
     * Identifies the search whose result may be published -- ONE PER SEARCH, not one per view.
     *
     * <p>Two generations rather than one, for the same reason this view carries two of every other
     * search property: the two searches are independent, so clearing the activations must not
     * discard an in-flight configuration search's results, and vice versa. A single shared
     * generation would make either Clear silently cancel the other search.
     *
     * <p>Each is incremented by its own search and its own clear, so a superseded task drops its
     * result rather than repopulating a table the user just cleared. FX thread only.
     */
    private long configurationSearchGeneration = 0;
    private long activationSearchGeneration = 0;

    /** Whether each temporal criterion was ticked; see setActivationTemporalCriteria(). */
    private boolean activeAtEnabled = false;
    private boolean rangeEnabled = false;

    /**
     * How a typed name is matched, the same three modes the PV metadata explore view offers.
     *
     * <p>Declared here rather than shared with {@link PvMetadataExploreViewModel} because the two
     * views' criteria happen to coincide today rather than by contract — the PV name and
     * configuration name criteria are separate proto messages that merely have the same shape.
     * Hoisting a shared enum would couple two views that are free to diverge.
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

    // ---- Configuration search form ----
    private final StringProperty configurationNameText = new SimpleStringProperty("");
    private final StringProperty configurationCategoryText = new SimpleStringProperty("");
    private final StringProperty configurationTagsText = new SimpleStringProperty("");
    private final StringProperty configurationParentText = new SimpleStringProperty("");
    private final StringProperty configurationAttributeKey = new SimpleStringProperty("");
    private final StringProperty configurationAttributeValue = new SimpleStringProperty("");

    private MatchMode configurationNameMatchMode = MatchMode.CONTAINS;

    // ---- Configuration results ----
    private final ObservableList<ConfigurationTableRow> configurationResults =
            FXCollections.observableArrayList();
    private final StringProperty configurationResultCountMessage =
            new SimpleStringProperty("0 configuration(s)");
    private final BooleanProperty configurationSearchInProgress = new SimpleBooleanProperty(false);
    private final StringProperty configurationSearchStatusMessage = new SimpleStringProperty("");
    private final StringProperty configurationStatusMessage =
            new SimpleStringProperty("Ready to search for configurations");

    // ---- Activation search form ----
    private final StringProperty activationConfigurationNamesText = new SimpleStringProperty("");
    private final StringProperty activationIdsText = new SimpleStringProperty("");
    private final StringProperty activationCategoryText = new SimpleStringProperty("");
    private final StringProperty activationTagsText = new SimpleStringProperty("");
    private final StringProperty activationAttributeKey = new SimpleStringProperty("");
    private final StringProperty activationAttributeValue = new SimpleStringProperty("");

    // Temporal criteria are owned by the controller's date/time controls and handed in per search,
    // the same shape the machine-configuration editor uses for its activation form.
    private Instant activeAt;
    private Instant rangeStart;
    private Instant rangeEnd;

    // ---- Activation results ----
    private final ObservableList<ConfigurationActivationTableRow> activationResults =
            FXCollections.observableArrayList();
    private final StringProperty activationResultCountMessage =
            new SimpleStringProperty("0 activation(s)");
    private final BooleanProperty activationSearchInProgress = new SimpleBooleanProperty(false);
    private final StringProperty activationSearchStatusMessage = new SimpleStringProperty("");
    private final StringProperty activationStatusMessage =
            new SimpleStringProperty("Ready to search for activations");

    private DpApplication dpApplication;
    private MainController mainController;

    public ConfigurationExploreViewModel() {
        logger.debug("ConfigurationExploreViewModel initialized");
    }

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        logger.debug("DpApplication injected into ConfigurationExploreViewModel");
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void setConfigurationNameMatchMode(MatchMode mode) {
        this.configurationNameMatchMode = mode != null ? mode : MatchMode.CONTAINS;
    }

    /**
     * Publishes the activation time criteria, along with whether each was actually ASKED FOR.
     *
     * <p>The two "enabled" flags exist because a null instant is ambiguous on its own: an unticked
     * checkbox and a ticked one whose date picker is still blank both arrive here as null. Those
     * mean opposite things -- the first is "no restriction", the second is an incomplete criterion
     * the user intends to apply -- and treating the second as the first drops the filter silently,
     * returning a result set BROADER than asked for with every other criterion still applied. That
     * reads as a working search rather than as a dropped filter, which is the same failure mode
     * {@link #hasPartialRange()} exists to prevent, one step earlier.
     */
    /**
     * Publishes the activation time criteria, inferring "asked for" from the values themselves.
     *
     * <p>Safe only when a null really does mean "not asked for", which is true of every caller that
     * builds instants directly rather than reading checkbox-gated controls. The view uses the
     * five-argument form, because there a null can also mean "ticked but the date is still blank" —
     * a distinction no value can carry.
     */
    public void setActivationTemporalCriteria(Instant activeAt, Instant rangeStart, Instant rangeEnd) {
        setActivationTemporalCriteria(
                activeAt, rangeStart, rangeEnd,
                activeAt != null, rangeStart != null || rangeEnd != null);
    }

    public void setActivationTemporalCriteria(
            Instant activeAt, Instant rangeStart, Instant rangeEnd,
            boolean activeAtEnabled, boolean rangeEnabled) {
        this.activeAt = activeAt;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.activeAtEnabled = activeAtEnabled;
        this.rangeEnabled = rangeEnabled;
    }

    /** Whether a ticked temporal criterion is missing the date it needs. */
    public boolean hasIncompleteTemporalCriteria() {
        return (activeAtEnabled && activeAt == null)
                || (rangeEnabled && (rangeStart == null || rangeEnd == null));
    }

    // ---- Configuration form properties ----
    public StringProperty configurationNameTextProperty() { return configurationNameText; }
    public StringProperty configurationCategoryTextProperty() { return configurationCategoryText; }
    public StringProperty configurationTagsTextProperty() { return configurationTagsText; }
    public StringProperty configurationParentTextProperty() { return configurationParentText; }
    public StringProperty configurationAttributeKeyProperty() { return configurationAttributeKey; }
    public StringProperty configurationAttributeValueProperty() { return configurationAttributeValue; }

    public ObservableList<ConfigurationTableRow> getConfigurationResults() { return configurationResults; }
    public StringProperty configurationResultCountMessageProperty() { return configurationResultCountMessage; }
    public BooleanProperty configurationSearchInProgressProperty() { return configurationSearchInProgress; }
    public StringProperty configurationSearchStatusMessageProperty() { return configurationSearchStatusMessage; }
    public StringProperty configurationStatusMessageProperty() { return configurationStatusMessage; }

    // ---- Activation form properties ----
    public StringProperty activationConfigurationNamesTextProperty() { return activationConfigurationNamesText; }
    public StringProperty activationIdsTextProperty() { return activationIdsText; }
    public StringProperty activationCategoryTextProperty() { return activationCategoryText; }
    public StringProperty activationTagsTextProperty() { return activationTagsText; }
    public StringProperty activationAttributeKeyProperty() { return activationAttributeKey; }
    public StringProperty activationAttributeValueProperty() { return activationAttributeValue; }

    public ObservableList<ConfigurationActivationTableRow> getActivationResults() { return activationResults; }
    public StringProperty activationResultCountMessageProperty() { return activationResultCountMessage; }
    public BooleanProperty activationSearchInProgressProperty() { return activationSearchInProgress; }
    public StringProperty activationSearchStatusMessageProperty() { return activationSearchStatusMessage; }
    public StringProperty activationStatusMessageProperty() { return activationStatusMessage; }

    // ---------------------------------------------------------------------------------------
    // Configuration search
    // ---------------------------------------------------------------------------------------

    public void executeConfigurationSearch() {
        if (dpApplication == null) {
            configurationSearchStatusMessage.set("DpApplication not initialized");
            return;
        }

        final TextMatch nameMatch = textMatch(configurationNameText.get(), configurationNameMatchMode);
        final List<String> categories = parseCommaSeparatedList(configurationCategoryText.get());
        final List<String> tags = parseCommaSeparatedList(configurationTagsText.get());
        final List<String> parents = parseCommaSeparatedList(configurationParentText.get());
        final List<AttributeCriterion> attributes = attributeCriteria(
                configurationAttributeKey.get(), configurationAttributeValue.get());

        final long generation = ++configurationSearchGeneration;

        configurationSearchInProgress.set(true);
        configurationSearchStatusMessage.set("Searching for configurations...");
        configurationResults.clear();
        configurationResultCountMessage.set("0 configuration(s)");

        final Task<DpApplication.PagedResult<Configuration>> searchTask =
                new Task<DpApplication.PagedResult<Configuration>>() {
            @Override
            protected DpApplication.PagedResult<Configuration> call() throws Exception {
                return dpApplication.queryConfigurations(
                        nameMatch, categories, tags, attributes, parents);
            }
        };

        searchTask.setOnSucceeded(event -> {
            if (generation != configurationSearchGeneration) {
                logger.debug("Discarding results of a superseded configuration search");
                return;
            }
            publishConfigurationResults(searchTask.getValue());
            configurationSearchInProgress.set(false);
        });

        searchTask.setOnFailed(event -> {
            final Throwable failure = searchTask.getException();
            logger.error("configuration search failed", failure);
            if (generation != configurationSearchGeneration) {
                return;
            }
            configurationSearchStatusMessage.set("Search failed: " + failure.getMessage());
            configurationStatusMessage.set("Search failed: " + failure.getMessage());
            configurationSearchInProgress.set(false);
        });

        startDaemon(searchTask, "configuration-search");
    }

    private void publishConfigurationResults(DpApplication.PagedResult<Configuration> pagedResult) {
        final List<ConfigurationTableRow> rows = new ArrayList<>();
        for (Configuration record : pagedResult.records) {
            rows.add(new ConfigurationTableRow(record));
        }
        configurationResults.setAll(rows);

        configurationResultCountMessage.set(pagedResult.truncated
                ? "first " + rows.size() + " configuration(s)"
                : rows.size() + " configuration(s)");
        configurationSearchStatusMessage.set("Search completed");
        configurationStatusMessage.set(capitalize(pagedResult.describeCount("configuration")));

        logger.info("configuration search completed - {} records (truncated={})",
                rows.size(), pagedResult.truncated);
    }

    // ---------------------------------------------------------------------------------------
    // Activation search
    // ---------------------------------------------------------------------------------------

    /**
     * True when exactly one of the two range bounds is set.
     *
     * <p>This must be reported to the user rather than passed through. {@code TimeRangeCriterion}
     * requires both bounds, and the request builder responds to a half-filled pair by emitting
     * <strong>no criterion at all</strong> — it does not reject the request. So a user who fills in
     * only a start date gets a result set silently broader than what they asked for, with every
     * other criterion still applied, which looks like a working search rather than a dropped filter.
     */
    public boolean hasPartialRange() {
        return (rangeStart == null) != (rangeEnd == null);
    }

    public void executeActivationSearch() {
        if (dpApplication == null) {
            activationSearchStatusMessage.set("DpApplication not initialized");
            return;
        }

        if (hasPartialRange()) {
            // refuse rather than silently dropping the bound the user did supply
            activationSearchStatusMessage.set(
                    "Set both range start and range end, or neither - a half-filled range is ignored");
            return;
        }

        if (hasIncompleteTemporalCriteria()) {
            // A ticked-but-blank criterion would reach the request as null, i.e. as no criterion at
            // all -- silently widening the search rather than failing it.
            activationSearchStatusMessage.set(
                    "Fill in the date for each ticked time criterion, or untick it");
            return;
        }

        if (rangeStart != null && rangeEnd != null && !rangeEnd.isAfter(rangeStart)) {
            activationSearchStatusMessage.set("Range end must be after range start");
            return;
        }

        final Instant activeAtCriterion = activeAt;
        final Instant rangeStartCriterion = rangeStart;
        final Instant rangeEndCriterion = rangeEnd;
        final List<String> configurationNames = parseCommaSeparatedList(activationConfigurationNamesText.get());
        final List<String> activationIds = parseCommaSeparatedList(activationIdsText.get());
        final List<String> categories = parseCommaSeparatedList(activationCategoryText.get());
        final List<String> tags = parseCommaSeparatedList(activationTagsText.get());
        final List<AttributeCriterion> attributes = attributeCriteria(
                activationAttributeKey.get(), activationAttributeValue.get());

        final long generation = ++activationSearchGeneration;

        activationSearchInProgress.set(true);
        activationSearchStatusMessage.set("Searching for activations...");
        activationResults.clear();
        activationResultCountMessage.set("0 activation(s)");

        final Task<DpApplication.PagedResult<ConfigurationActivation>> searchTask =
                new Task<DpApplication.PagedResult<ConfigurationActivation>>() {
            @Override
            protected DpApplication.PagedResult<ConfigurationActivation> call() throws Exception {
                return dpApplication.queryConfigurationActivations(
                        activeAtCriterion, rangeStartCriterion, rangeEndCriterion,
                        configurationNames, activationIds, categories, tags, attributes);
            }
        };

        searchTask.setOnSucceeded(event -> {
            if (generation != activationSearchGeneration) {
                logger.debug("Discarding results of a superseded activation search");
                return;
            }
            publishActivationResults(searchTask.getValue());
            activationSearchInProgress.set(false);
        });

        searchTask.setOnFailed(event -> {
            final Throwable failure = searchTask.getException();
            logger.error("activation search failed", failure);
            if (generation != activationSearchGeneration) {
                return;
            }
            activationSearchStatusMessage.set("Search failed: " + failure.getMessage());
            activationStatusMessage.set("Search failed: " + failure.getMessage());
            activationSearchInProgress.set(false);
        });

        startDaemon(searchTask, "activation-search");
    }

    private void publishActivationResults(
            DpApplication.PagedResult<ConfigurationActivation> pagedResult
    ) {
        final List<ConfigurationActivationTableRow> rows = new ArrayList<>();
        for (ConfigurationActivation record : pagedResult.records) {
            rows.add(new ConfigurationActivationTableRow(record));
        }
        activationResults.setAll(rows);

        activationResultCountMessage.set(pagedResult.truncated
                ? "first " + rows.size() + " activation(s)"
                : rows.size() + " activation(s)");
        activationSearchStatusMessage.set("Search completed");
        activationStatusMessage.set(capitalize(pagedResult.describeCount("activation")));

        logger.info("activation search completed - {} records (truncated={})",
                rows.size(), pagedResult.truncated);
    }

    // ---------------------------------------------------------------------------------------
    // Cross-search navigation
    // ---------------------------------------------------------------------------------------

    /**
     * Fills the activation criteria with a configuration's name and runs the activation search.
     *
     * <p>Other criteria are cleared, so the result is the activations of that configuration rather
     * than that configuration filtered by whatever happened to be left in the form.
     */
    public void searchActivationsForConfiguration(ConfigurationTableRow row) {
        if (row == null || row.getConfiguration() == null) {
            return;
        }

        activationConfigurationNamesText.set(row.getConfiguration().getConfigurationName());
        activationIdsText.set("");
        activationCategoryText.set("");
        activationTagsText.set("");
        activationAttributeKey.set("");
        activationAttributeValue.set("");

        executeActivationSearch();
    }

    /**
     * Opens the machine configuration editor loaded with this record.
     *
     * <p>The record carries the canonical {@code configurationName}, which is what the editor must
     * save under: {@code saveConfiguration()} is a full-replace upsert keyed on that name.
     */
    public void editConfiguration(ConfigurationTableRow row) {
        if (row == null || row.getConfiguration() == null) {
            return;
        }
        if (mainController == null) {
            logger.warn("MainController is null, cannot open the machine configuration editor");
            return;
        }
        mainController.navigateToConfigurationEditor(row.getConfiguration());
    }

    // ---------------------------------------------------------------------------------------
    // Clearing
    // ---------------------------------------------------------------------------------------

    public void clearConfigurationSearch() {
        // Supersede an in-flight configuration search only; the activation search is independent.
        configurationSearchGeneration++;
        configurationSearchInProgress.set(false);

        configurationNameText.set("");
        configurationCategoryText.set("");
        configurationTagsText.set("");
        configurationParentText.set("");
        configurationAttributeKey.set("");
        configurationAttributeValue.set("");
        configurationResults.clear();
        configurationResultCountMessage.set("0 configuration(s)");
        configurationSearchStatusMessage.set("Search cleared");
        configurationStatusMessage.set("Ready to search for configurations");
    }

    public void clearActivationSearch() {
        // Supersede an in-flight activation search only; the configuration search is independent.
        activationSearchGeneration++;
        activationSearchInProgress.set(false);

        activationConfigurationNamesText.set("");
        activationIdsText.set("");
        activationCategoryText.set("");
        activationTagsText.set("");
        activationAttributeKey.set("");
        activationAttributeValue.set("");
        activeAt = null;
        rangeStart = null;
        rangeEnd = null;
        // The "asked for" flags are cleared WITH the instants. Leaving them set would make the
        // next search look like it had two ticked-but-blank criteria and be refused as incomplete
        // -- a cleared form refusing to search, which is the stale-state bug these flags exist to
        // prevent, reintroduced one level up.
        activeAtEnabled = false;
        rangeEnabled = false;
        activationResults.clear();
        activationResultCountMessage.set("0 activation(s)");
        activationSearchStatusMessage.set("Search cleared");
        activationStatusMessage.set("Ready to search for activations");
    }

    // ---------------------------------------------------------------------------------------
    // Criteria construction
    // ---------------------------------------------------------------------------------------

    /**
     * Builds the {@link TextMatch} for one field.
     *
     * <p><strong>A blank field yields an all-null TextMatch, which contributes no criterion at
     * all.</strong> A criterion whose lists are all empty is rejected by the server, while a blank
     * <em>prefix</em> that reached the server would compile to a regex matching everything. Same
     * reasoning as {@code PvMetadataExploreViewModel.textMatch()}.
     */
    static TextMatch textMatch(String text, MatchMode mode) {
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
     * Builds the attribute criteria for one key/value pair.
     *
     * <p>A key with no value is a key-only existence search. A value with no key cannot be expressed
     * — the key is required — so it is dropped rather than silently searched for as a key.
     */
    static List<AttributeCriterion> attributeCriteria(String key, String value) {
        if (key == null || key.isBlank()) {
            return List.of();
        }

        final List<String> values = parseCommaSeparatedList(value);
        return List.of(new AttributeCriterion(key.trim(), values.isEmpty() ? null : values));
    }

    /** True when the configuration form would send no criteria at all. */
    public boolean configurationSearchHasNoCriteria() {
        return textMatch(configurationNameText.get(), configurationNameMatchMode).isEmpty()
                && parseCommaSeparatedList(configurationCategoryText.get()).isEmpty()
                && parseCommaSeparatedList(configurationTagsText.get()).isEmpty()
                && parseCommaSeparatedList(configurationParentText.get()).isEmpty()
                && attributeCriteria(configurationAttributeKey.get(),
                        configurationAttributeValue.get()).isEmpty();
    }

    /** True when the activation form would send no criteria at all. */
    public boolean activationSearchHasNoCriteria() {
        return activeAt == null
                && rangeStart == null
                && rangeEnd == null
                && parseCommaSeparatedList(activationConfigurationNamesText.get()).isEmpty()
                && parseCommaSeparatedList(activationIdsText.get()).isEmpty()
                && parseCommaSeparatedList(activationCategoryText.get()).isEmpty()
                && parseCommaSeparatedList(activationTagsText.get()).isEmpty()
                && attributeCriteria(activationAttributeKey.get(),
                        activationAttributeValue.get()).isEmpty();
    }

    private static void startDaemon(Task<?> task, String name) {
        final Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

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
