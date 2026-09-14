package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.common.Attribute;
import com.ospreydcs.dp.grpc.v1.common.ConfigurationActivation;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for a protobuf {@link ConfigurationActivation} record in a TableView.
 *
 * <p><strong>An absent endTime is open-ended, not epoch.</strong>  {@code endTime} has real protobuf
 * field presence, and an absent one means the activation has no end — so it is rendered as
 * "open-ended" rather than as a formatted timestamp. Reading it with {@code getEndTime()} without
 * checking {@code hasEndTime()} yields a zero-valued Timestamp, which would display as a date in
 * 1970 and read as an activation that ended before it began. {@code ConfigurationActivationDetail}
 * makes the same distinction for the session list in the editor.
 */
public class ConfigurationActivationTableRow {

    /** Rendered for an activation with no end time, rather than a 1970 timestamp. */
    public static final String OPEN_ENDED = "open-ended";

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    // Names the properties the PropertyValueFactory column bindings resolve reflectively, as above.
    public static final String PROPERTY_CLIENT_ACTIVATION_ID = "clientActivationId";
    public static final String PROPERTY_CONFIGURATION_NAME = "configurationName";
    public static final String PROPERTY_START_TIME = "startTime";
    public static final String PROPERTY_END_TIME = "endTime";
    public static final String PROPERTY_DESCRIPTION = "description";
    public static final String PROPERTY_TAGS = "tags";
    public static final String PROPERTY_ATTRIBUTES = "attributes";
    public static final String PROPERTY_MODIFIED_BY = "modifiedBy";

    private final ConfigurationActivation activation;

    private final StringProperty clientActivationId = new SimpleStringProperty("");
    private final StringProperty configurationName = new SimpleStringProperty("");
    private final StringProperty startTime = new SimpleStringProperty("");
    private final StringProperty endTime = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");
    private final StringProperty tags = new SimpleStringProperty("");
    private final StringProperty attributes = new SimpleStringProperty("");
    private final StringProperty modifiedBy = new SimpleStringProperty("");

    public ConfigurationActivationTableRow(ConfigurationActivation activation) {
        this.activation = activation;

        if (activation == null) {
            return;
        }

        clientActivationId.set(activation.getClientActivationId());
        configurationName.set(activation.getConfigurationName());
        description.set(activation.getDescription());
        tags.set(String.join(", ", activation.getTagsList()));
        modifiedBy.set(activation.getModifiedBy());

        final List<String> formattedAttributes = new ArrayList<>();
        for (Attribute attribute : activation.getAttributesList()) {
            formattedAttributes.add(attribute.getName() + "=" + attribute.getValue());
        }
        attributes.set(String.join(", ", formattedAttributes));

        if (activation.hasStartTime()) {
            startTime.set(formatTimestamp(activation.getStartTime()));
        }

        // presence, not value: an absent endTime is an open-ended interval
        endTime.set(activation.hasEndTime() ? formatTimestamp(activation.getEndTime()) : OPEN_ENDED);
    }

    private static String formatTimestamp(Timestamp timestamp) {
        return TIMESTAMP_FORMATTER.format(
                Instant.ofEpochSecond(timestamp.getEpochSeconds(), timestamp.getNanoseconds()));
    }

    /** The record this row wraps. */
    public ConfigurationActivation getActivation() {
        return activation;
    }

    /** The start instant, unformatted, for callers that need identity rather than display. */
    public Instant getStartInstant() {
        if (activation == null || !activation.hasStartTime()) {
            return null;
        }
        return Instant.ofEpochSecond(
                activation.getStartTime().getEpochSeconds(),
                activation.getStartTime().getNanoseconds());
    }

    /** The end instant, or null when the activation is open-ended. */
    public Instant getEndInstant() {
        if (activation == null || !activation.hasEndTime()) {
            return null;
        }
        return Instant.ofEpochSecond(
                activation.getEndTime().getEpochSeconds(),
                activation.getEndTime().getNanoseconds());
    }

    public StringProperty clientActivationIdProperty() { return clientActivationId; }
    public String getClientActivationId() { return clientActivationId.get(); }

    public StringProperty configurationNameProperty() { return configurationName; }
    public String getConfigurationName() { return configurationName.get(); }

    public StringProperty startTimeProperty() { return startTime; }
    public String getStartTime() { return startTime.get(); }

    public StringProperty endTimeProperty() { return endTime; }
    public String getEndTime() { return endTime.get(); }

    public StringProperty descriptionProperty() { return description; }
    public String getDescription() { return description.get(); }

    public StringProperty tagsProperty() { return tags; }
    public String getTags() { return tags.get(); }

    public StringProperty attributesProperty() { return attributes; }
    public String getAttributes() { return attributes.get(); }

    public StringProperty modifiedByProperty() { return modifiedBy; }
    public String getModifiedBy() { return modifiedBy.get(); }
}
