package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.common.Attribute;
import com.ospreydcs.dp.grpc.v1.common.Configuration;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for a protobuf {@link Configuration} record in a TableView.
 *
 * <p>The multi-valued fields (tags, attributes) are rendered as comma-separated strings for display,
 * while {@link #getConfiguration()} exposes the record itself so the editor is loaded from the
 * record rather than from anything re-parsed out of the rendered strings.
 */
public class ConfigurationTableRow {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    // Names the properties the PropertyValueFactory column bindings resolve reflectively.  The
    // controller and the binding test both reference these rather than repeating the literals, so a
    // rename that misses the controller fails to compile instead of silently blanking a column.
    public static final String PROPERTY_CONFIGURATION_NAME = "configurationName";
    public static final String PROPERTY_CATEGORY = "category";
    public static final String PROPERTY_DESCRIPTION = "description";
    public static final String PROPERTY_PARENT_CONFIGURATION_NAME = "parentConfigurationName";
    public static final String PROPERTY_TAGS = "tags";
    public static final String PROPERTY_ATTRIBUTES = "attributes";
    public static final String PROPERTY_MODIFIED_BY = "modifiedBy";
    public static final String PROPERTY_UPDATED_TIME = "updatedTime";

    private final Configuration configuration;

    private final StringProperty configurationName = new SimpleStringProperty("");
    private final StringProperty category = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");
    private final StringProperty parentConfigurationName = new SimpleStringProperty("");
    private final StringProperty tags = new SimpleStringProperty("");
    private final StringProperty attributes = new SimpleStringProperty("");
    private final StringProperty modifiedBy = new SimpleStringProperty("");
    private final StringProperty updatedTime = new SimpleStringProperty("");

    public ConfigurationTableRow(Configuration configuration) {
        this.configuration = configuration;

        if (configuration == null) {
            return;
        }

        configurationName.set(configuration.getConfigurationName());
        category.set(configuration.getCategory());
        description.set(configuration.getDescription());
        parentConfigurationName.set(configuration.getParentConfigurationName());
        tags.set(String.join(", ", configuration.getTagsList()));
        modifiedBy.set(configuration.getModifiedBy());

        final List<String> formattedAttributes = new ArrayList<>();
        for (Attribute attribute : configuration.getAttributesList()) {
            formattedAttributes.add(attribute.getName() + "=" + attribute.getValue());
        }
        attributes.set(String.join(", ", formattedAttributes));

        if (configuration.hasUpdatedTime()) {
            updatedTime.set(formatTimestamp(configuration.getUpdatedTime()));
        }
    }

    private static String formatTimestamp(Timestamp timestamp) {
        return TIMESTAMP_FORMATTER.format(
                Instant.ofEpochSecond(timestamp.getEpochSeconds(), timestamp.getNanoseconds()));
    }

    /**
     * The record this row wraps, for loading it into the editor.
     *
     * <p>Callers must save using this record's {@code configurationName}, since
     * {@code saveConfiguration()} is a full-replace upsert keyed on it.
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    public StringProperty configurationNameProperty() { return configurationName; }
    public String getConfigurationName() { return configurationName.get(); }

    public StringProperty categoryProperty() { return category; }
    public String getCategory() { return category.get(); }

    public StringProperty descriptionProperty() { return description; }
    public String getDescription() { return description.get(); }

    public StringProperty parentConfigurationNameProperty() { return parentConfigurationName; }
    public String getParentConfigurationName() { return parentConfigurationName.get(); }

    public StringProperty tagsProperty() { return tags; }
    public String getTags() { return tags.get(); }

    public StringProperty attributesProperty() { return attributes; }
    public String getAttributes() { return attributes.get(); }

    public StringProperty modifiedByProperty() { return modifiedBy; }
    public String getModifiedBy() { return modifiedBy.get(); }

    public StringProperty updatedTimeProperty() { return updatedTime; }
    public String getUpdatedTime() { return updatedTime.get(); }
}
