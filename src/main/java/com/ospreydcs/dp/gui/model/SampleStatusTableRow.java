package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.common.SampleStatusBucket;
import com.ospreydcs.dp.grpc.v1.common.SampleStatusColumn;
import com.ospreydcs.dp.grpc.v1.common.SamplingClock;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.grpc.v1.common.TimestampList;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One sample status — a single {@code (pvName, timestamp, domain, layer)} identity — flattened out
 * of a {@link SampleStatusBucket} for display in a TableView.
 *
 * <p><strong>A bucket is not a row.</strong> A bucket holds statuses for one PV in one
 * {@code (domain, layer)} over a contiguous period, with a time axis and one status code per
 * timestamp. Rendering buckets directly would show a row count unrelated to the number of statuses,
 * so {@link #expand} flattens each bucket into one row per status.
 *
 * <p><strong>The time axis may be a SamplingClock rather than a list of timestamps.</strong>
 * {@code SampleStatusBucket.dataTimestamps} is a full {@code DataTimestamps}, a oneof of
 * {@code SamplingClock} or {@code TimestampList}. The demo generator writes a {@code SamplingClock}
 * (dense labeling of a regularly-sampled range is what a clock is for), so there is frequently no
 * timestamp list to read and the clock must be expanded arithmetically. Both arms are handled here
 * because either can arrive from a producer this application did not write.
 *
 * <p><strong>Timestamps are computed in integer nanoseconds, never in floating point.</strong>
 * Status identity is exact {@code (pvName, timestamp)} equality at nanosecond precision, so a
 * timestamp that drifts by one nanosecond silently fails to match the sample it labels.
 */
public class SampleStatusTableRow {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS").withZone(ZoneId.systemDefault());

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /** Rendered when a bucket carries no confidence or reasons array, which is the common case. */
    private static final String ABSENT = "";

    private final StringProperty pvName = new SimpleStringProperty("");
    private final StringProperty timestamp = new SimpleStringProperty("");
    private final StringProperty domain = new SimpleStringProperty("");
    private final StringProperty layer = new SimpleStringProperty("");
    private final StringProperty statusCode = new SimpleStringProperty("");
    private final StringProperty statusLabel = new SimpleStringProperty("");
    private final StringProperty confidence = new SimpleStringProperty(ABSENT);
    private final StringProperty reason = new SimpleStringProperty(ABSENT);
    private final StringProperty source = new SimpleStringProperty("");
    private final StringProperty modifiedBy = new SimpleStringProperty("");

    private final Instant timestampInstant;
    private final int rawStatusCode;

    private SampleStatusTableRow(
            String pvNameValue,
            Instant timestampValue,
            String domainValue,
            String layerValue,
            int statusCodeValue,
            String statusLabelValue,
            String confidenceValue,
            String reasonValue,
            String sourceValue,
            String modifiedByValue
    ) {
        this.timestampInstant = timestampValue;
        this.rawStatusCode = statusCodeValue;

        this.pvName.set(pvNameValue != null ? pvNameValue : "");
        this.timestamp.set(timestampValue != null ? TIMESTAMP_FORMATTER.format(timestampValue) : "");
        this.domain.set(domainValue != null ? domainValue : "");
        this.layer.set(layerValue != null ? layerValue : "");
        this.statusCode.set(Integer.toString(statusCodeValue));
        this.statusLabel.set(statusLabelValue);
        this.confidence.set(confidenceValue);
        this.reason.set(reasonValue);
        this.source.set(sourceValue != null ? sourceValue : "");
        this.modifiedBy.set(modifiedByValue != null ? modifiedByValue : "");
    }

    /**
     * Flattens one bucket into one row per status, keeping only statuses inside
     * {@code [rangeBegin, rangeEnd)}.
     *
     * <p><strong>The trim is required, not cosmetic.</strong> Bucket selection is a {@code TimeRange}
     * overlap test and boundary buckets are returned <em>whole</em>, so a bucket at either edge of
     * the requested window carries statuses outside it. Counting or displaying the raw bucket
     * contents reports statuses the user did not ask for and a count that does not match the query.
     *
     * <p>Pass a null {@code rangeBegin}/{@code rangeEnd} to keep everything, which is what an
     * unbounded query means.
     *
     * @param codeLabels {@code (domain -> (code -> label))} for domains whose code semantics this
     *                   application knows. A domain absent from the map renders its raw code with no
     *                   label, which is the only honest rendering: the domain registry is
     *                   unimplemented server-side, so a label for an unknown domain would be a guess.
     */
    public static List<SampleStatusTableRow> expand(
            SampleStatusBucket bucket,
            Instant rangeBegin,
            Instant rangeEnd,
            Map<String, Map<Integer, String>> codeLabels
    ) {
        final List<SampleStatusTableRow> rows = new ArrayList<>();
        if (bucket == null || !bucket.hasStatusColumn()) {
            return rows;
        }

        final SampleStatusColumn column = bucket.getStatusColumn();
        final List<Instant> timestamps = expandTimestamps(bucket);
        final int statusCount = column.getStatusCodesCount();

        // confidence and reasons are optional parallel arrays: each is either empty or has exactly
        // one entry per timestamp.  Checking the length rather than assuming presence is what keeps
        // a bucket with only status codes -- the common case -- from throwing here.
        final boolean hasConfidence = column.getConfidenceCount() == statusCount;
        final boolean hasReasons = column.getReasonsCount() == statusCount;

        final Map<Integer, String> labelsForDomain =
                codeLabels != null ? codeLabels.get(bucket.getDomain()) : null;

        // The axis and the codes should be the same length; if a producer disagrees, render the
        // statuses that have a timestamp rather than throwing away the whole bucket or inventing one.
        final int renderable = Math.min(statusCount, timestamps.size());

        for (int index = 0; index < renderable; index++) {
            final Instant statusTime = timestamps.get(index);

            if (rangeBegin != null && statusTime.isBefore(rangeBegin)) {
                continue;
            }
            if (rangeEnd != null && !statusTime.isBefore(rangeEnd)) {
                // half-open [begin, end): a status exactly at endTime is outside the range
                continue;
            }

            final int code = column.getStatusCodes(index);
            final String label = labelsForDomain != null
                    ? labelsForDomain.getOrDefault(code, "")
                    : "";

            rows.add(new SampleStatusTableRow(
                    column.getPvName(),
                    statusTime,
                    bucket.getDomain(),
                    bucket.getLayer(),
                    code,
                    label,
                    hasConfidence ? Float.toString(column.getConfidence(index)) : ABSENT,
                    hasReasons ? column.getReasons(index) : ABSENT,
                    bucket.getSource(),
                    bucket.getModifiedBy()));
        }

        return rows;
    }

    /**
     * Produces the bucket's per-status timestamps from whichever arm of the {@code DataTimestamps}
     * oneof it carries.
     *
     * <p>Clock expansion is integer arithmetic on nanoseconds since the clock's start: computing
     * {@code start + index * periodNanos} in floating point, or by repeatedly adding to a running
     * Instant, accumulates error that breaks the exact-equality matching status identity depends on.
     */
    private static List<Instant> expandTimestamps(SampleStatusBucket bucket) {
        final List<Instant> timestamps = new ArrayList<>();

        if (!bucket.hasDataTimestamps()) {
            return timestamps;
        }

        if (bucket.getDataTimestamps().hasTimestampList()) {
            final TimestampList list = bucket.getDataTimestamps().getTimestampList();
            for (Timestamp protoTimestamp : list.getTimestampsList()) {
                timestamps.add(toInstant(protoTimestamp));
            }
            return timestamps;
        }

        if (bucket.getDataTimestamps().hasSamplingClock()) {
            final SamplingClock clock = bucket.getDataTimestamps().getSamplingClock();
            final Instant start = toInstant(clock.getStartTime());
            final long periodNanos = clock.getPeriodNanos();
            final int count = clock.getCount();

            for (int index = 0; index < count; index++) {
                // multiply rather than accumulate: a running add drifts, and these timestamps are
                // identity, not display
                timestamps.add(start.plusNanos(Math.multiplyExact((long) index, periodNanos)));
            }
        }

        return timestamps;
    }

    private static Instant toInstant(Timestamp protoTimestamp) {
        return Instant.ofEpochSecond(protoTimestamp.getEpochSeconds(), protoTimestamp.getNanoseconds());
    }

    /** The exact status timestamp, for callers that need the instant rather than its rendering. */
    public Instant getTimestampInstant() {
        return timestampInstant;
    }

    /** The raw status code, whose meaning is defined by the bucket's domain. */
    public int getRawStatusCode() {
        return rawStatusCode;
    }

    // Property accessors for TableView binding.  The PROPERTY_* constants name what the
    // PropertyValueFactory column bindings resolve reflectively, so a rename that misses the
    // controller fails to compile instead of silently blanking a column.
    public static final String PROPERTY_PV_NAME = "pvName";
    public static final String PROPERTY_TIMESTAMP = "timestamp";
    public static final String PROPERTY_DOMAIN = "domain";
    public static final String PROPERTY_LAYER = "layer";
    public static final String PROPERTY_STATUS_CODE = "statusCode";
    public static final String PROPERTY_STATUS_LABEL = "statusLabel";
    public static final String PROPERTY_CONFIDENCE = "confidence";
    public static final String PROPERTY_REASON = "reason";
    public static final String PROPERTY_SOURCE = "source";
    public static final String PROPERTY_MODIFIED_BY = "modifiedBy";

    public StringProperty pvNameProperty() { return pvName; }
    public String getPvName() { return pvName.get(); }

    public StringProperty timestampProperty() { return timestamp; }
    public String getTimestamp() { return timestamp.get(); }

    public StringProperty domainProperty() { return domain; }
    public String getDomain() { return domain.get(); }

    public StringProperty layerProperty() { return layer; }
    public String getLayer() { return layer.get(); }

    public StringProperty statusCodeProperty() { return statusCode; }
    public String getStatusCode() { return statusCode.get(); }

    public StringProperty statusLabelProperty() { return statusLabel; }
    public String getStatusLabel() { return statusLabel.get(); }

    public StringProperty confidenceProperty() { return confidence; }
    public String getConfidence() { return confidence.get(); }

    public StringProperty reasonProperty() { return reason; }
    public String getReason() { return reason.get(); }

    public StringProperty sourceProperty() { return source; }
    public String getSource() { return source.get(); }

    public StringProperty modifiedByProperty() { return modifiedBy; }
    public String getModifiedBy() { return modifiedBy.get(); }
}
