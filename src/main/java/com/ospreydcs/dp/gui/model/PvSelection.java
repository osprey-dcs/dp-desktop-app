package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.client.QueryClient;
import com.ospreydcs.dp.client.criteria.AttributeCriterion;
import com.ospreydcs.dp.client.criteria.TextMatch;

import java.util.ArrayList;
import java.util.List;

/**
 * How a Query API V2 request chooses which PVs it covers.
 *
 * <p>This is the app-side counterpart of {@code QueryClient.PvSelectorParams}, which is a sealed
 * hierarchy of three records.  It is a single class with a mode rather than a mirror of that
 * hierarchy because the Query Editor has to hold a <em>partially edited</em> selection while the
 * modal is open -- a pattern typed but not yet applied, metadata criteria half filled in -- and a
 * sealed hierarchy makes exactly that state unrepresentable.  The conversion to the sealed form
 * happens once, at {@link #toSelectorParams()}, where the choice is final.
 *
 * <p><strong>The name-list mode does not carry the names.</strong>  They stay in the Query Editor's
 * own {@code ObservableList<String>}, which is the list bound to the PV ListView, synchronized with
 * {@code DpApplication}'s global PV state, and populated by the Dataset Builder, the PV Explore
 * view, the Provider Explore view and the data-event hyperlink.  Copying them in here would create
 * a second source of truth for the app's most widely shared piece of state, and the two would drift
 * the moment any of those flows updated one and not the other.  {@link #toSelectorParams(List)}
 * takes the live list instead.
 *
 * <p>Instances are immutable; the modal builds a new one rather than mutating the current selection,
 * so cancelling leaves nothing half-applied.
 */
public final class PvSelection {

    /** Which arm of the PV selector this selection describes. */
    public enum Mode {
        NAME_LIST("Name list"),
        NAME_PATTERN("Name pattern"),
        METADATA("Metadata criteria");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Selects every PV in the archive.  Stated explicitly rather than as an empty metadata query. */
    public static final String MATCH_ALL_PATTERN = ".*";

    private final Mode mode;
    private final String namePattern;
    private final TextMatch metadataPvName;
    private final TextMatch metadataAliases;
    private final List<String> metadataTagsAnyOf;
    private final List<AttributeCriterion> metadataAttributes;

    private PvSelection(
            Mode mode,
            String namePattern,
            TextMatch metadataPvName,
            TextMatch metadataAliases,
            List<String> metadataTagsAnyOf,
            List<AttributeCriterion> metadataAttributes
    ) {
        this.mode = mode;
        this.namePattern = namePattern;
        this.metadataPvName = metadataPvName;
        this.metadataAliases = metadataAliases;
        this.metadataTagsAnyOf = metadataTagsAnyOf == null
                ? List.of() : List.copyOf(metadataTagsAnyOf);
        this.metadataAttributes = metadataAttributes == null
                ? List.of() : List.copyOf(metadataAttributes);
    }

    /** The default selection: the Query Editor's PV name list, exactly as before this existed. */
    public static PvSelection nameList() {
        return new PvSelection(Mode.NAME_LIST, null, null, null, null, null);
    }

    public static PvSelection namePattern(String pattern) {
        return new PvSelection(Mode.NAME_PATTERN, pattern, null, null, null, null);
    }

    public static PvSelection metadata(
            TextMatch pvName,
            TextMatch aliases,
            List<String> tagsAnyOf,
            List<AttributeCriterion> attributes
    ) {
        return new PvSelection(Mode.METADATA, null, pvName, aliases, tagsAnyOf, attributes);
    }

    public Mode getMode() {
        return mode;
    }

    public String getNamePattern() {
        return namePattern;
    }

    public TextMatch getMetadataPvName() {
        return metadataPvName;
    }

    public TextMatch getMetadataAliases() {
        return metadataAliases;
    }

    public List<String> getMetadataTagsAnyOf() {
        return metadataTagsAnyOf;
    }

    public List<AttributeCriterion> getMetadataAttributes() {
        return metadataAttributes;
    }

    /**
     * True when this selection names its PVs explicitly, which is the only mode that can be turned
     * into a {@code DataBlock}.
     *
     * <p>A data block is a PV name list by definition, so the Dataset Builder cannot represent a
     * pattern or a metadata query.  Callers must branch on this rather than reading the name list
     * regardless: the list is still populated in the other two modes (it is shared global state),
     * so an unguarded read would build a data block from PVs the query did not cover and save it
     * without any error.
     */
    public boolean isNameList() {
        return mode == Mode.NAME_LIST;
    }

    /**
     * Converts to the client's sealed selector form.
     *
     * @param pvNames the Query Editor's live PV name list, used only in {@link Mode#NAME_LIST}
     */
    public QueryClient.PvSelectorParams toSelectorParams(List<String> pvNames) {
        return switch (mode) {
            case NAME_LIST -> new QueryClient.PvNameListSelector(
                    pvNames == null ? List.of() : new ArrayList<>(pvNames));
            case NAME_PATTERN -> new QueryClient.PvNamePatternSelector(namePattern);
            case METADATA -> new QueryClient.PvMetadataSelector(
                    metadataPvName, metadataAliases, metadataTagsAnyOf, metadataAttributes);
        };
    }

    /**
     * A one-line description of what this selection covers, for the Query Editor summary label and
     * the status messages.
     *
     * @param pvNames the Query Editor's live PV name list, used only in {@link Mode#NAME_LIST}
     */
    public String describe(List<String> pvNames) {
        return switch (mode) {
            case NAME_LIST -> {
                final int count = pvNames == null ? 0 : pvNames.size();
                yield count == 1 ? "1 PV by name" : count + " PVs by name";
            }
            case NAME_PATTERN -> MATCH_ALL_PATTERN.equals(namePattern)
                    ? "every PV in the archive (pattern " + MATCH_ALL_PATTERN + ")"
                    : "PVs matching pattern " + namePattern;
            case METADATA -> "PVs matching " + describeMetadataCriteria();
        };
    }

    private String describeMetadataCriteria() {
        final List<String> parts = new ArrayList<>();
        if (metadataPvName != null && !metadataPvName.isEmpty()) {
            parts.add("name");
        }
        if (metadataAliases != null && !metadataAliases.isEmpty()) {
            parts.add("alias");
        }
        if (!metadataTagsAnyOf.isEmpty()) {
            parts.add("tags");
        }
        if (!metadataAttributes.isEmpty()) {
            parts.add("attributes");
        }
        // An all-empty metadata query is not rejected by the server -- it matches every PV in the
        // archive.  Saying so is the point: a selection that LOOKS like a filter and silently
        // covers everything is the failure this description exists to make visible.
        return parts.isEmpty() ? "no metadata criteria (every PV in the archive)"
                : String.join(" + ", parts);
    }
}
