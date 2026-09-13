package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.common.Attribute;
import com.ospreydcs.dp.grpc.v1.common.PvMetadata;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for a protobuf {@link PvMetadata} record in a TableView.
 *
 * <p>The multi-valued fields (aliases, tags, attributes) are rendered as comma-separated strings for
 * display, while {@link #getAliasesList()} exposes the underlying list so
 * {@code HyperlinkListTableCell} can link each alias individually rather than re-splitting the
 * rendered string — a value containing a comma would otherwise split into bogus links.
 */
public class PvMetadataTableRow {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    // Names the properties the PropertyValueFactory column bindings resolve reflectively.  The
    // controller and the binding test both reference these rather than repeating the literals, so a
    // rename that misses the controller fails to compile instead of silently blanking a column.
    public static final String PROPERTY_PV_NAME = "pvName";
    public static final String PROPERTY_ALIASES = "aliases";
    public static final String PROPERTY_TAGS = "tags";
    public static final String PROPERTY_ATTRIBUTES = "attributes";
    public static final String PROPERTY_DESCRIPTION = "description";
    public static final String PROPERTY_MODIFIED_BY = "modifiedBy";
    public static final String PROPERTY_UPDATED_TIME = "updatedTime";

    private final PvMetadata pvMetadata;

    private final StringProperty pvName = new SimpleStringProperty("");
    private final StringProperty aliases = new SimpleStringProperty("");
    private final StringProperty tags = new SimpleStringProperty("");
    private final StringProperty attributes = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");
    private final StringProperty modifiedBy = new SimpleStringProperty("");
    private final StringProperty updatedTime = new SimpleStringProperty("");

    public PvMetadataTableRow(PvMetadata pvMetadata) {
        this.pvMetadata = pvMetadata;

        if (pvMetadata == null) {
            return;
        }

        pvName.set(pvMetadata.getPvName());
        aliases.set(String.join(", ", pvMetadata.getAliasesList()));
        tags.set(String.join(", ", pvMetadata.getTagsList()));
        description.set(pvMetadata.getDescription());
        modifiedBy.set(pvMetadata.getModifiedBy());

        final List<String> formattedAttributes = new ArrayList<>();
        for (Attribute attribute : pvMetadata.getAttributesList()) {
            formattedAttributes.add(attribute.getName() + "=" + attribute.getValue());
        }
        attributes.set(String.join(", ", formattedAttributes));

        if (pvMetadata.hasUpdatedTime()) {
            updatedTime.set(formatTimestamp(pvMetadata.getUpdatedTime()));
        }
    }

    private static String formatTimestamp(Timestamp timestamp) {
        return TIMESTAMP_FORMATTER.format(
                Instant.ofEpochSecond(timestamp.getEpochSeconds(), timestamp.getNanoseconds()));
    }

    /**
     * The record this row wraps, for loading it into the editor.
     *
     * <p>Callers must save using this record's {@code pvName}: it is the <em>canonical</em> name, and
     * {@code savePvMetadata()} is a full-replace upsert keyed on it.
     */
    public PvMetadata getPvMetadata() {
        return pvMetadata;
    }

    /** The aliases as a list, so each can be linked individually. */
    public List<String> getAliasesList() {
        return pvMetadata != null ? pvMetadata.getAliasesList() : List.of();
    }

    public StringProperty pvNameProperty() { return pvName; }
    public String getPvName() { return pvName.get(); }

    public StringProperty aliasesProperty() { return aliases; }
    public String getAliases() { return aliases.get(); }

    public StringProperty tagsProperty() { return tags; }
    public String getTags() { return tags.get(); }

    public StringProperty attributesProperty() { return attributes; }
    public String getAttributes() { return attributes.get(); }

    public StringProperty descriptionProperty() { return description; }
    public String getDescription() { return description.get(); }

    public StringProperty modifiedByProperty() { return modifiedBy; }
    public String getModifiedBy() { return modifiedBy.get(); }

    public StringProperty updatedTimeProperty() { return updatedTime; }
    public String getUpdatedTime() { return updatedTime.get(); }
}
