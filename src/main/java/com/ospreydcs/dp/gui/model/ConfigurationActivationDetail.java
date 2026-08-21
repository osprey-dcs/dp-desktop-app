package com.ospreydcs.dp.gui.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Model class representing a configuration activation created during this session, as displayed in
 * the Machine Configuration view's activation list.
 *
 * The clientActivationId here is always the identifier of the saved record: either the one supplied
 * in the request, or, when the request omitted one, the identifier the server generated and
 * returned.  In the latter case this is the caller's only handle on the record, which is why it is
 * displayed rather than kept internal.
 */
public class ConfigurationActivationDetail {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public final String clientActivationId;
    public final String configurationName;
    public final Instant startTime;
    public final Instant endTime;

    public ConfigurationActivationDetail(
            String clientActivationId,
            String configurationName,
            Instant startTime,
            Instant endTime
    ) {
        this.clientActivationId = clientActivationId;
        this.configurationName = configurationName;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * Returns a display string for ListView showing the activation id and its time interval.
     * Format: "activation-id: 2026-08-21 09:00:00 -> 2026-08-21 17:00:00"
     *
     * A null endTime renders as "open-ended".  The view requires an end time in this version, so
     * that case is not reachable through the form today, but the model does not impose the
     * restriction the UI does.
     */
    public String getDisplayString() {
        final StringBuilder sb = new StringBuilder();

        sb.append(clientActivationId == null || clientActivationId.isEmpty()
                ? "(no activation id)" : clientActivationId);
        sb.append(": ");

        sb.append(startTime == null ? "(no start time)" : format(startTime));
        sb.append(" -> ");
        sb.append(endTime == null ? "open-ended" : format(endTime));

        return sb.toString();
    }

    private static String format(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DISPLAY_FORMATTER);
    }

    @Override
    public String toString() {
        return getDisplayString();
    }
}
