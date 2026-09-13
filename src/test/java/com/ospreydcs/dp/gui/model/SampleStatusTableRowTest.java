package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.common.DataTimestamps;
import com.ospreydcs.dp.grpc.v1.common.SampleStatusBucket;
import com.ospreydcs.dp.grpc.v1.common.SampleStatusColumn;
import com.ospreydcs.dp.grpc.v1.common.SamplingClock;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.grpc.v1.common.TimestampList;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the bucket-to-row decode: SamplingClock expansion, boundary trimming, and code labelling.
 *
 * No JavaFX toolkit and no service ecosystem — the decode is deliberately static and pure so its
 * edge cases are reachable in a plain unit test. These are exactly the cases that produce
 * plausible-looking wrong output rather than an obvious failure: an off-by-one at a bucket edge
 * yields a believable count, and a drifting clock yields timestamps that look right but match no
 * sample.
 */
public class SampleStatusTableRowTest {

    private static final String DOMAIN = "epics_alarm";
    private static final String LAYER = "demo_generator";

    private static final Map<String, Map<Integer, String>> LABELS = Map.of(
            DOMAIN, Map.of(0, "NO_ALARM", 1, "MINOR_ALARM", 2, "MAJOR_ALARM"));

    private static Timestamp timestamp(long epochSeconds, long nanos) {
        return Timestamp.newBuilder().setEpochSeconds(epochSeconds).setNanoseconds(nanos).build();
    }

    /** A bucket whose axis is a SamplingClock, as the demo generator writes. */
    private static SampleStatusBucket clockBucket(
            long startSeconds, long startNanos, long periodNanos, int... codes
    ) {
        final SampleStatusColumn.Builder column = SampleStatusColumn.newBuilder().setPvName("pv-1");
        for (int code : codes) {
            column.addStatusCodes(code);
        }

        return SampleStatusBucket.newBuilder()
                .setDomain(DOMAIN)
                .setLayer(LAYER)
                .setDataTimestamps(DataTimestamps.newBuilder()
                        .setSamplingClock(SamplingClock.newBuilder()
                                .setStartTime(timestamp(startSeconds, startNanos))
                                .setPeriodNanos(periodNanos)
                                .setCount(codes.length)))
                .setStatusColumn(column)
                .setSource("test-source")
                .setModifiedBy("test-user")
                .build();
    }

    @Test
    public void aSamplingClockExpandsToOneRowPerStatus() {
        // 5 statuses at 1-second intervals from epoch+100
        final SampleStatusBucket bucket = clockBucket(100, 0, 1_000_000_000L, 0, 1, 2, 1, 0);

        final List<SampleStatusTableRow> rows =
                SampleStatusTableRow.expand(bucket, null, null, LABELS);

        assertEquals(5, rows.size(), "a bucket is not a row: one row per status");
        assertEquals(Instant.ofEpochSecond(100), rows.get(0).getTimestampInstant());
        assertEquals(Instant.ofEpochSecond(104), rows.get(4).getTimestampInstant());
        assertEquals("pv-1", rows.get(0).getPvName());
        assertEquals(DOMAIN, rows.get(0).getDomain());
        assertEquals(LAYER, rows.get(0).getLayer());
    }

    /**
     * Status identity is exact (pvName, timestamp) equality at nanosecond precision, so a clock
     * expansion that accumulates rounding drift silently fails to match the samples it labels.
     * A sub-millisecond period over many samples is where a running-add implementation diverges.
     */
    @Test
    public void clockExpansionIsExactAcrossManySamples() {
        final int count = 1000;
        final long periodNanos = 1_234_567L;   // deliberately not a round number
        final int[] codes = new int[count];

        final SampleStatusBucket bucket = clockBucket(500, 999_000_000L, periodNanos, codes);

        final List<SampleStatusTableRow> rows =
                SampleStatusTableRow.expand(bucket, null, null, LABELS);

        assertEquals(count, rows.size());

        final Instant start = Instant.ofEpochSecond(500, 999_000_000L);
        for (int index = 0; index < count; index++) {
            assertEquals(start.plusNanos((long) index * periodNanos),
                    rows.get(index).getTimestampInstant(),
                    "timestamp drift at index " + index + " would break status identity");
        }
    }

    @Test
    public void anExplicitTimestampListIsUsedWhenPresent() {
        final SampleStatusBucket bucket = SampleStatusBucket.newBuilder()
                .setDomain(DOMAIN)
                .setLayer(LAYER)
                .setDataTimestamps(DataTimestamps.newBuilder()
                        .setTimestampList(TimestampList.newBuilder()
                                .addTimestamps(timestamp(10, 0))
                                .addTimestamps(timestamp(30, 500))))
                .setStatusColumn(SampleStatusColumn.newBuilder()
                        .setPvName("pv-sparse")
                        .addStatusCodes(2)
                        .addStatusCodes(3))
                .build();

        final List<SampleStatusTableRow> rows =
                SampleStatusTableRow.expand(bucket, null, null, LABELS);

        assertEquals(2, rows.size(), "sparse labeling uses a TimestampList, not a clock");
        assertEquals(Instant.ofEpochSecond(10), rows.get(0).getTimestampInstant());
        assertEquals(Instant.ofEpochSecond(30, 500), rows.get(1).getTimestampInstant());
    }

    /**
     * The central hazard: bucket selection is a TimeRange OVERLAP test and boundary buckets are
     * returned WHOLE, so a bucket at the edge of the window carries statuses outside it. Displaying
     * or counting the raw bucket reports statuses the user did not ask for.
     */
    @Test
    public void statusesOutsideTheRequestedRangeAreTrimmedFromABoundaryBucket() {
        // statuses at t=100..109; the caller asked for [103, 107)
        final SampleStatusBucket bucket =
                clockBucket(100, 0, 1_000_000_000L, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2);

        final List<SampleStatusTableRow> rows = SampleStatusTableRow.expand(
                bucket, Instant.ofEpochSecond(103), Instant.ofEpochSecond(107), LABELS);

        assertEquals(4, rows.size(), "only t=103,104,105,106 fall inside [103,107)");
        assertEquals(Instant.ofEpochSecond(103), rows.get(0).getTimestampInstant());
        assertEquals(Instant.ofEpochSecond(106), rows.get(3).getTimestampInstant());
    }

    /** The range is half-open, so a status exactly at endTime is outside it. */
    @Test
    public void theRangeEndIsExclusiveAndTheRangeBeginIsInclusive() {
        final SampleStatusBucket bucket = clockBucket(100, 0, 1_000_000_000L, 0, 1, 2);

        final List<SampleStatusTableRow> rows = SampleStatusTableRow.expand(
                bucket, Instant.ofEpochSecond(100), Instant.ofEpochSecond(102), LABELS);

        assertEquals(2, rows.size(), "[100,102) includes 100 and 101, excludes 102");
        assertEquals(Instant.ofEpochSecond(100), rows.get(0).getTimestampInstant());
        assertEquals(Instant.ofEpochSecond(101), rows.get(1).getTimestampInstant());
    }

    @Test
    public void knownDomainCodesAreLabelledAndTheRawCodeIsAlwaysKept() {
        final SampleStatusBucket bucket = clockBucket(0, 0, 1_000_000_000L, 0, 2);

        final List<SampleStatusTableRow> rows =
                SampleStatusTableRow.expand(bucket, null, null, LABELS);

        assertEquals("NO_ALARM", rows.get(0).getStatusLabel());
        assertEquals("0", rows.get(0).getStatusCode());
        assertEquals("MAJOR_ALARM", rows.get(1).getStatusLabel());
        assertEquals("2", rows.get(1).getStatusCode());
        assertEquals(2, rows.get(1).getRawStatusCode());
    }

    /**
     * The domain registry is unimplemented server-side, so a code in an unknown domain cannot be
     * resolved to a label. Guessing with another domain's mapping would assert something false about
     * the data; the raw code is the only honest rendering.
     */
    @Test
    public void anUnknownDomainRendersTheRawCodeWithNoLabel() {
        final SampleStatusBucket bucket = SampleStatusBucket.newBuilder()
                .setDomain("ml_anomaly")
                .setLayer("model_v3")
                .setDataTimestamps(DataTimestamps.newBuilder()
                        .setSamplingClock(SamplingClock.newBuilder()
                                .setStartTime(timestamp(0, 0))
                                .setPeriodNanos(1_000_000_000L)
                                .setCount(1)))
                .setStatusColumn(SampleStatusColumn.newBuilder()
                        .setPvName("pv-1")
                        .addStatusCodes(2))
                .build();

        final List<SampleStatusTableRow> rows =
                SampleStatusTableRow.expand(bucket, null, null, LABELS);

        assertEquals(1, rows.size());
        assertEquals("2", rows.get(0).getStatusCode());
        assertEquals("", rows.get(0).getStatusLabel(),
                "a code in an unregistered domain must not borrow another domain's label");
    }

    /**
     * confidence and reasons are optional parallel arrays: empty, or exactly one entry per
     * timestamp. Indexing them without checking would throw on the common case of codes only.
     */
    @Test
    public void absentConfidenceAndReasonsRenderEmptyRatherThanThrowing() {
        final SampleStatusBucket bucket = clockBucket(0, 0, 1_000_000_000L, 1, 1);

        final List<SampleStatusTableRow> rows =
                SampleStatusTableRow.expand(bucket, null, null, LABELS);

        assertEquals(2, rows.size());
        assertEquals("", rows.get(0).getConfidence());
        assertEquals("", rows.get(0).getReason());
    }

    @Test
    public void presentConfidenceAndReasonsAreRenderedPerStatus() {
        final SampleStatusBucket bucket = SampleStatusBucket.newBuilder()
                .setDomain(DOMAIN)
                .setLayer(LAYER)
                .setDataTimestamps(DataTimestamps.newBuilder()
                        .setSamplingClock(SamplingClock.newBuilder()
                                .setStartTime(timestamp(0, 0))
                                .setPeriodNanos(1_000_000_000L)
                                .setCount(2)))
                .setStatusColumn(SampleStatusColumn.newBuilder()
                        .setPvName("pv-1")
                        .addStatusCodes(1).addStatusCodes(2)
                        .addConfidence(0.5f).addConfidence(0.9f)
                        .addReasons("drifting").addReasons("out of range"))
                .build();

        final List<SampleStatusTableRow> rows =
                SampleStatusTableRow.expand(bucket, null, null, LABELS);

        assertEquals("0.5", rows.get(0).getConfidence());
        assertEquals("drifting", rows.get(0).getReason());
        assertEquals("0.9", rows.get(1).getConfidence());
        assertEquals("out of range", rows.get(1).getReason());
    }

    /**
     * A partial parallel array violates the proto's "empty or one per timestamp" rule. Rendering it
     * positionally would attach the wrong confidence to a status, which is worse than omitting it.
     */
    @Test
    public void aPartialConfidenceArrayIsIgnoredRatherThanMisaligned() {
        final SampleStatusBucket bucket = SampleStatusBucket.newBuilder()
                .setDomain(DOMAIN)
                .setLayer(LAYER)
                .setDataTimestamps(DataTimestamps.newBuilder()
                        .setSamplingClock(SamplingClock.newBuilder()
                                .setStartTime(timestamp(0, 0))
                                .setPeriodNanos(1_000_000_000L)
                                .setCount(3)))
                .setStatusColumn(SampleStatusColumn.newBuilder()
                        .setPvName("pv-1")
                        .addStatusCodes(1).addStatusCodes(1).addStatusCodes(1)
                        .addConfidence(0.5f))  // one value for three statuses
                .build();

        final List<SampleStatusTableRow> rows =
                SampleStatusTableRow.expand(bucket, null, null, LABELS);

        assertEquals(3, rows.size());
        for (SampleStatusTableRow row : rows) {
            assertEquals("", row.getConfidence(),
                    "a malformed parallel array must not be rendered positionally");
        }
    }

    @Test
    public void aBucketWithNoStatusColumnYieldsNoRows() {
        final SampleStatusBucket bucket = SampleStatusBucket.newBuilder()
                .setDomain(DOMAIN)
                .setLayer(LAYER)
                .build();

        assertTrue(SampleStatusTableRow.expand(bucket, null, null, LABELS).isEmpty());
        assertTrue(SampleStatusTableRow.expand(null, null, null, LABELS).isEmpty());
    }

    /**
     * A bucket whose axis is shorter than its code list is malformed. Rendering the statuses that do
     * have a timestamp is better than discarding the bucket or inventing timestamps for the rest.
     */
    @Test
    public void aCodeListLongerThanTheAxisRendersOnlyTheTimestampedStatuses() {
        final SampleStatusBucket bucket = SampleStatusBucket.newBuilder()
                .setDomain(DOMAIN)
                .setLayer(LAYER)
                .setDataTimestamps(DataTimestamps.newBuilder()
                        .setSamplingClock(SamplingClock.newBuilder()
                                .setStartTime(timestamp(0, 0))
                                .setPeriodNanos(1_000_000_000L)
                                .setCount(2)))
                .setStatusColumn(SampleStatusColumn.newBuilder()
                        .setPvName("pv-1")
                        .addStatusCodes(1).addStatusCodes(1).addStatusCodes(1))
                .build();

        assertEquals(2, SampleStatusTableRow.expand(bucket, null, null, LABELS).size());
    }
}
