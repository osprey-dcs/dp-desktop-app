package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.client.QueryClient;
import com.ospreydcs.dp.client.criteria.AttributeCriterion;

import java.util.ArrayList;
import java.util.List;

/**
 * An optional restriction of a Query API V2 query to the intervals during which matching machine
 * configurations were active -- the app-side counterpart of {@code QuerySpec.configurationSelector}.
 *
 * <p>The server resolves the criteria to {@code ConfigurationActivation} records, unions their
 * active intervals, intersects that union with the query's time range, and retrieves data only
 * inside the resulting (possibly fragmented) intervals.  So this narrows the <em>time axis</em>,
 * not the PV set -- it composes with {@link PvSelection} rather than overlapping it.
 *
 * <p><strong>An empty filter is "no restriction" and must produce NO selector at all, never an
 * empty one.</strong>  This is the exact inverse of {@link PvSelection}'s metadata arm, and the
 * asymmetry is the single most important thing about this class:
 *
 * <table border="1">
 *   <caption>The two empty forms resolve oppositely</caption>
 *   <tr><th></th><th>empty metadata PV selector</th><th>empty configuration selector</th></tr>
 *   <tr><td>server</td><td>accepted: every PV in the archive</td><td>rejected: "criteria list must
 *       not be empty"</td></tr>
 *   <tr><td>risk</td><td>a whole-archive scan that looks like a filter</td><td>a rejected query
 *       that looks like an unfiltered one</td></tr>
 * </table>
 *
 * <p>{@link #toCriteria()} therefore returns <strong>null</strong> rather than an empty list when
 * nothing is filled in.  The client wrapper distinguishes the two: null or empty means no
 * restriction was asked for and the selector is dropped, while a non-empty list from which no
 * criterion survives emits the empty selector for the server to reject.  Passing an empty list to
 * mean "no restriction" would turn an unfiltered query into a rejected one.
 *
 * <p><strong>Each criterion sets exactly one arm.</strong>  The proto criterion is a oneof, and the
 * client returns null from its criterion builder when more than one arm is populated -- which
 * {@code buildQuerySpec} then turns into a rejected request rather than a silently preferred arm.
 * This class never builds a multi-arm criterion: it emits one criterion per populated field, which
 * is also the semantics the user expects, since criteria are ANDed and values within one are ORed.
 *
 * <p>Instances are immutable, so a cancelled modal leaves nothing half-applied.
 */
public final class ConfigurationFilter {

    private final List<String> configurationNames;
    private final List<String> activationIds;
    private final List<String> categories;
    private final List<String> tags;
    private final String attributeKey;
    private final List<String> attributeValues;

    private ConfigurationFilter(
            List<String> configurationNames,
            List<String> activationIds,
            List<String> categories,
            List<String> tags,
            String attributeKey,
            List<String> attributeValues
    ) {
        this.configurationNames = copyOf(configurationNames);
        this.activationIds = copyOf(activationIds);
        this.categories = copyOf(categories);
        this.tags = copyOf(tags);
        this.attributeKey = attributeKey == null || attributeKey.isBlank() ? null : attributeKey.trim();
        this.attributeValues = copyOf(attributeValues);
    }

    private static List<String> copyOf(List<String> values) {
        if (values == null) {
            return List.of();
        }
        final List<String> copy = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                copy.add(value.trim());
            }
        }
        return List.copyOf(copy);
    }

    /** The filter that restricts nothing -- the default, and the state the Query Editor starts in. */
    public static ConfigurationFilter none() {
        return new ConfigurationFilter(null, null, null, null, null, null);
    }

    public static ConfigurationFilter of(
            List<String> configurationNames,
            List<String> activationIds,
            List<String> categories,
            List<String> tags,
            String attributeKey,
            List<String> attributeValues
    ) {
        return new ConfigurationFilter(
                configurationNames, activationIds, categories, tags, attributeKey, attributeValues);
    }

    public List<String> getConfigurationNames() { return configurationNames; }
    public List<String> getActivationIds() { return activationIds; }
    public List<String> getCategories() { return categories; }
    public List<String> getTags() { return tags; }
    public String getAttributeKey() { return attributeKey; }
    public List<String> getAttributeValues() { return attributeValues; }

    /** Whether any criterion is filled in.  An inactive filter contributes no selector. */
    public boolean isActive() {
        return !configurationNames.isEmpty()
                || !activationIds.isEmpty()
                || !categories.isEmpty()
                || !tags.isEmpty()
                || attributeKey != null;
    }

    /**
     * The criteria to hand to {@code QuerySpecParams}, or <strong>null</strong> when nothing is
     * filled in.
     *
     * <p>Null is not a stylistic choice.  The client wrapper reads null/empty as "no restriction
     * asked for" and drops the selector, but reads a non-empty list whose criteria are all unusable
     * as "a restriction was asked for and could not be expressed" and emits the empty selector for
     * the server to reject.  Returning an empty list here to mean "no filter" would therefore turn
     * every unfiltered query into a rejected one.
     *
     * <p>One criterion per populated field, never one criterion carrying several arms: the proto
     * criterion is a oneof and a multi-arm criterion is rejected at build time.  Separate criteria
     * AND together, which is what a user filling in both a category and a tag means.
     */
    public List<QueryClient.ConfigurationCriterion> toCriteria() {
        if (!isActive()) {
            return null;
        }

        final List<QueryClient.ConfigurationCriterion> criteria = new ArrayList<>();
        if (!configurationNames.isEmpty()) {
            criteria.add(new QueryClient.ConfigurationCriterion(
                    configurationNames, null, null, null, null));
        }
        if (!activationIds.isEmpty()) {
            criteria.add(new QueryClient.ConfigurationCriterion(
                    null, activationIds, null, null, null));
        }
        if (!categories.isEmpty()) {
            criteria.add(new QueryClient.ConfigurationCriterion(
                    null, null, categories, null, null));
        }
        if (!tags.isEmpty()) {
            criteria.add(new QueryClient.ConfigurationCriterion(
                    null, null, null, tags, null));
        }
        if (attributeKey != null) {
            // values may be empty -- that is a key-only existence search, which the criterion
            // supports directly.  Null rather than an empty list, for the reason AttributeCriterion
            // documents: an empty values list is not the same request as an absent one.
            criteria.add(new QueryClient.ConfigurationCriterion(
                    null, null, null, null,
                    new AttributeCriterion(attributeKey,
                            attributeValues.isEmpty() ? null : attributeValues)));
        }
        return criteria;
    }

    /** The one-line summary shown in the Query Editor and in status messages. */
    public String describe() {
        if (!isActive()) {
            return "the whole time range (no configuration filter)";
        }
        final List<String> parts = new ArrayList<>();
        if (!configurationNames.isEmpty()) parts.add("configuration " + String.join("/", configurationNames));
        if (!activationIds.isEmpty()) parts.add("activation " + String.join("/", activationIds));
        if (!categories.isEmpty()) parts.add("category " + String.join("/", categories));
        if (!tags.isEmpty()) parts.add("tags " + String.join("/", tags));
        if (attributeKey != null) {
            parts.add(attributeValues.isEmpty()
                    ? "attribute " + attributeKey
                    : "attribute " + attributeKey + "=" + String.join("/", attributeValues));
        }
        return "only while " + String.join(" and ", parts) + " was active";
    }
}
