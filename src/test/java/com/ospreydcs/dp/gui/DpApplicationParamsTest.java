package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.annotation.Calculations;
import com.ospreydcs.dp.grpc.v1.common.DataColumn;
import com.ospreydcs.dp.grpc.v1.common.DataValue;
import com.ospreydcs.dp.grpc.v1.common.SampleStatusColumn;
import com.ospreydcs.dp.grpc.v1.common.SampleStatusFrame;
import com.ospreydcs.dp.grpc.v1.common.SamplingClock;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.gui.model.DataFrameDetails;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the static parameter-normalization helpers on DpApplication: the empty-to-null
 * conversions that keep blank UI fields out of client requests, the conditional criterion
 * setters used by the query wrappers, the Instant-to-Timestamp conversion used by the
 * configuration activation wrapper, and the calculations builder used by saveAnnotation().
 * All are pure static methods, so no service ecosystem or mocked client is needed.
 */
public class DpApplicationParamsTest {

    // ------------------- emptyToNull ---------------------------

    @Test
    public void emptyToNullStringPassesThroughNonEmptyValue() {
        assertEquals("value", DpApplication.emptyToNull("value"));
    }

    @Test
    public void emptyToNullStringConvertsNullAndEmptyToNull() {
        assertNull(DpApplication.emptyToNull((String) null));
        assertNull(DpApplication.emptyToNull(""));
    }

    @Test
    public void emptyToNullListPassesThroughNonEmptyListAndConvertsNullAndEmpty() {
        List<String> list = List.of("a");
        assertSame(list, DpApplication.emptyToNull(list));
        assertNull(DpApplication.emptyToNull((List<String>) null));
        assertNull(DpApplication.emptyToNull(new ArrayList<String>()));
    }

    @Test
    public void emptyToNullMapPassesThroughNonEmptyMapAndConvertsNullAndEmpty() {
        Map<String, String> map = Map.of("k", "v");
        assertSame(map, DpApplication.emptyToNull(map));
        assertNull(DpApplication.emptyToNull((Map<String, String>) null));
        assertNull(DpApplication.emptyToNull(new HashMap<String, String>()));
    }

    // ------------------- setIfPresent / setIfBothPresent ---------------------------

    @Test
    public void setIfPresentAppliesNonEmptyCriterion() {
        List<String> applied = new ArrayList<>();
        DpApplication.setIfPresent("criterion", applied::add);
        assertEquals(List.of("criterion"), applied);
    }

    @Test
    public void setIfPresentSkipsNullAndEmptyCriterion() {
        List<String> applied = new ArrayList<>();
        DpApplication.setIfPresent(null, applied::add);
        DpApplication.setIfPresent("", applied::add);
        assertEquals(List.of(), applied);
    }

    @Test
    public void setIfBothPresentAppliesCompletePair() {
        Map<String, String> applied = new HashMap<>();
        DpApplication.setIfBothPresent("key", "value", applied::put);
        assertEquals(Map.of("key", "value"), applied);
    }

    @Test
    public void setIfBothPresentSkipsIncompletePairs() {
        Map<String, String> applied = new HashMap<>();
        DpApplication.setIfBothPresent(null, "value", applied::put);
        DpApplication.setIfBothPresent("key", null, applied::put);
        DpApplication.setIfBothPresent("", "value", applied::put);
        DpApplication.setIfBothPresent("key", "", applied::put);
        assertEquals(Map.of(), applied);
    }

    // ------------------- timestampFromInstant ---------------------------

    @Test
    public void timestampFromInstantConvertsSecondsAndNanos() {
        Timestamp timestamp =
                DpApplication.timestampFromInstant(Instant.ofEpochSecond(1_700_000_000L, 123_456_789));

        assertNotNull(timestamp);
        assertEquals(1_700_000_000L, timestamp.getEpochSeconds());
        assertEquals(123_456_789, timestamp.getNanoseconds());
    }

    /**
     * An Instant on a whole second must still convert, with a zero nanosecond component.  This is
     * the ordinary case for a time entered through the view's date picker and hour/minute/second
     * spinners, which have no sub-second field.
     */
    @Test
    public void timestampFromInstantConvertsWholeSecond() {
        Timestamp timestamp = DpApplication.timestampFromInstant(Instant.ofEpochSecond(1_700_000_000L));

        assertNotNull(timestamp);
        assertEquals(1_700_000_000L, timestamp.getEpochSeconds());
        assertEquals(0, timestamp.getNanoseconds());
    }

    /**
     * The case this helper exists for.  A Timestamp is a message field with real protobuf field
     * presence, so an optional time left unset has to reach the request builder as null.  Returning
     * a zero-valued Timestamp instead would mark the field present, turning an open-ended
     * activation into one that ended at the epoch.
     */
    @Test
    public void timestampFromInstantMapsNullToNull() {
        assertNull(DpApplication.timestampFromInstant(null));
    }

    // ------------------- buildCalculations ---------------------------

    private static DataFrameDetails frame(String name) {
        Timestamp timestamp =
                Timestamp.newBuilder().setEpochSeconds(1_700_000_000L).setNanoseconds(500).build();
        DataColumn column = DataColumn.newBuilder()
                .setName(name + "-col")
                .addDataValues(DataValue.newBuilder().setDoubleValue(1.5))
                .build();
        return new DataFrameDetails(name, List.of(timestamp), List.of(column));
    }

    /**
     * Regression test: saveAnnotation() previously threw NullPointerException for
     * annotations without calculations, because it called build() on a builder that was
     * only created when data frames were present.  No calculations must map to null,
     * which AnnotationClient.buildSaveAnnotationRequest() treats as "omit the field".
     */
    @Test
    public void noCalculationsBuildsNullNotException() {
        assertNull(DpApplication.buildCalculations(null));
        assertNull(DpApplication.buildCalculations(List.of()));
    }

    @Test
    public void calculationsFramesAreConvertedToProtobuf() {
        Calculations calculations =
                DpApplication.buildCalculations(List.of(frame("frame-1"), frame("frame-2")));

        assertNotNull(calculations);
        assertEquals(2, calculations.getCalculationDataFramesCount());

        Calculations.CalculationsDataFrame first = calculations.getCalculationDataFrames(0);
        assertEquals("frame-1", first.getName());
        assertEquals(1, first.getDataTimestamps().getTimestampList().getTimestampsCount());
        assertEquals(1_700_000_000L,
                first.getDataTimestamps().getTimestampList().getTimestamps(0).getEpochSeconds());
        assertEquals(1, first.getDataColumnsCount());
        assertEquals("frame-1-col", first.getDataColumns(0).getName());
        assertEquals(1.5, first.getDataColumns(0).getDataValues(0).getDoubleValue());

        assertEquals("frame-2", calculations.getCalculationDataFrames(1).getName());
    }
    // ------------------- sample status ---------------------------

    private static final long CLOCK_START_SECONDS = 1_700_000_000L;
    private static final long CLOCK_START_NANOS = 123_456_789L;
    private static final long CLOCK_PERIOD_NANOS = 100_000_000L; // 10 Hz

    private static SampleStatusFrame frameWithCodes(int count, List<Integer> codes) {
        return DpApplication.buildSampleStatusFrame(
                "test-pv",
                DpApplication.SAMPLE_STATUS_DEMO_DOMAIN,
                DpApplication.SAMPLE_STATUS_DEMO_LAYER,
                CLOCK_START_SECONDS,
                CLOCK_START_NANOS,
                CLOCK_PERIOD_NANOS,
                count,
                codes);
    }

    private static List<Integer> zeroCodes(int count) {
        List<Integer> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            codes.add(DpApplication.EPICS_ALARM_NO_ALARM);
        }
        return codes;
    }

    /**
     * The sampling clock of a status frame must equal the clock the data was ingested with
     * exactly: statuses attach to samples by exact (pvName, timestamp) equality at nanosecond
     * precision, so an altered start or period silently matches nothing at query time.  This
     * asserts the builder passes the caller's clock values through untouched rather than
     * recomputing them.
     */
    @Test
    public void sampleStatusFrameClockMatchesSuppliedValuesExactly() {
        SampleStatusFrame frame = frameWithCodes(3, zeroCodes(3));

        SamplingClock clock = frame.getDataTimestamps().getSamplingClock();
        assertEquals(CLOCK_START_SECONDS, clock.getStartTime().getEpochSeconds());
        assertEquals(CLOCK_START_NANOS, clock.getStartTime().getNanoseconds());
        assertEquals(CLOCK_PERIOD_NANOS, clock.getPeriodNanos());
        assertEquals(3, clock.getCount());
    }

    @Test
    public void sampleStatusFrameCarriesPvNameDomainAndLayer() {
        SampleStatusFrame frame = frameWithCodes(2, zeroCodes(2));

        assertEquals(DpApplication.SAMPLE_STATUS_DEMO_DOMAIN, frame.getDomain());
        assertEquals(DpApplication.SAMPLE_STATUS_DEMO_LAYER, frame.getLayer());
        assertEquals(1, frame.getStatusColumnsCount());
        assertEquals("test-pv", frame.getStatusColumns(0).getPvName());
    }

    @Test
    public void sampleStatusFrameCarriesOneCodePerTimestamp() {
        List<Integer> codes = List.of(
                DpApplication.EPICS_ALARM_NO_ALARM,
                DpApplication.EPICS_ALARM_MAJOR_ALARM,
                DpApplication.EPICS_ALARM_INVALID_ALARM);

        SampleStatusColumn column = frameWithCodes(3, codes).getStatusColumns(0);

        assertEquals(3, column.getStatusCodesCount());
        assertEquals(DpApplication.EPICS_ALARM_NO_ALARM, column.getStatusCodes(0));
        assertEquals(DpApplication.EPICS_ALARM_MAJOR_ALARM, column.getStatusCodes(1));
        assertEquals(DpApplication.EPICS_ALARM_INVALID_ALARM, column.getStatusCodes(2));
    }

    /*
     * confidence and reasons are all-or-nothing: each must be empty or carry exactly one entry
     * per timestamp.  The demo supplies neither, and an all-empty reasons list must be omitted
     * entirely rather than sent as empty strings.
     */
    @Test
    public void sampleStatusFrameLeavesConfidenceAndReasonsEmpty() {
        SampleStatusColumn column = frameWithCodes(3, zeroCodes(3)).getStatusColumns(0);

        assertEquals(0, column.getConfidenceCount());
        assertEquals(0, column.getReasonsCount());
    }

    /*
     * The service rejects a column whose code count differs from the timestamp count.  Failing
     * here surfaces the mismatch at its origin instead of as a server rejection.
     */
    @Test
    public void sampleStatusFrameRejectsCodeCountMismatch() {
        assertThrows(IllegalArgumentException.class, () -> frameWithCodes(3, zeroCodes(2)));
        assertThrows(IllegalArgumentException.class, () -> frameWithCodes(2, zeroCodes(3)));
        assertThrows(IllegalArgumentException.class, () -> frameWithCodes(2, null));
    }

    @Test
    public void generatedAlarmCodesAreAllWithinTheDomain() {
        List<Integer> codes = DpApplication.generateRandomAlarmStatusCodes(500, new Random(42));

        assertEquals(500, codes.size());
        for (Integer code : codes) {
            assertTrue(
                    code == DpApplication.EPICS_ALARM_NO_ALARM
                            || code == DpApplication.EPICS_ALARM_MINOR_ALARM
                            || code == DpApplication.EPICS_ALARM_MAJOR_ALARM
                            || code == DpApplication.EPICS_ALARM_INVALID_ALARM,
                    "unexpected alarm status code: " + code);
        }
    }

    /*
     * The distribution is weighted so demo data reads as plausible alarm history rather than
     * uniform noise.  Asserted loosely -- that NO_ALARM dominates and every code is reachable --
     * so the test pins the intent without becoming brittle about the exact ratios.
     */
    @Test
    public void generatedAlarmCodesAreWeightedTowardNoAlarm() {
        List<Integer> codes = DpApplication.generateRandomAlarmStatusCodes(5_000, new Random(1));

        Map<Integer, Integer> counts = new HashMap<>();
        for (Integer code : codes) {
            counts.merge(code, 1, Integer::sum);
        }

        int noAlarmCount = counts.getOrDefault(DpApplication.EPICS_ALARM_NO_ALARM, 0);
        assertTrue(noAlarmCount > 5_000 / 2,
                "NO_ALARM should dominate the distribution, saw " + noAlarmCount + " of 5000");

        for (int code : List.of(
                DpApplication.EPICS_ALARM_NO_ALARM,
                DpApplication.EPICS_ALARM_MINOR_ALARM,
                DpApplication.EPICS_ALARM_MAJOR_ALARM,
                DpApplication.EPICS_ALARM_INVALID_ALARM)) {
            assertTrue(counts.getOrDefault(code, 0) > 0, "code never generated: " + code);
        }
    }

    @Test
    public void generatedAlarmCodesHandleZeroSampleCount() {
        assertEquals(0, DpApplication.generateRandomAlarmStatusCodes(0, new Random(1)).size());
    }

    /*
     * A negative count is a caller bug.  Throwing here surfaces it at its origin; returning an
     * empty list would defer it to a count mismatch in buildSampleStatusFrame(), which is exactly
     * what that guard exists to prevent.
     */
    @Test
    public void generatedAlarmCodesRejectNegativeSampleCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DpApplication.generateRandomAlarmStatusCodes(-1, new Random(1)));
    }

    // ------------------- sample status provenance and accumulation ---------------------------

    /*
     * The registered provider name is the useful answer for modifiedBy, but providerName is only
     * set by registerProvider() while generateAndIngestData() guards on providerId.  A null would
     * make the client omit the field entirely and store the statuses unattributed with no
     * indication, so an unset name falls back to the demo source rather than through.
     */
    @Test
    public void sampleStatusModifiedByUsesProviderNameWhenSet() {
        assertEquals("test-provider", DpApplication.sampleStatusModifiedBy("test-provider"));
    }

    @Test
    public void sampleStatusModifiedByFallsBackWhenProviderNameIsMissing() {
        assertEquals(
                DpApplication.SAMPLE_STATUS_DEMO_SOURCE,
                DpApplication.sampleStatusModifiedBy(null));
        assertEquals(
                DpApplication.SAMPLE_STATUS_DEMO_SOURCE,
                DpApplication.sampleStatusModifiedBy("   "));
    }

    @Test
    public void sampleStatusAccumulatorSumsSavedCounts() {
        DpApplication.SampleStatusAccumulator accumulator =
                new DpApplication.SampleStatusAccumulator(new Random(1));

        assertEquals(0, accumulator.savedCount());
        assertFalse(accumulator.hasError());

        accumulator.recordSaved(20);
        accumulator.recordSaved(22);

        assertEquals(42, accumulator.savedCount());
        assertFalse(accumulator.hasError());
    }

    /*
     * Only the first failure is kept: a failing status service fails once per bucket, and
     * repeating the same cause adds nothing to the message the user sees.
     */
    @Test
    public void sampleStatusAccumulatorKeepsOnlyTheFirstError() {
        DpApplication.SampleStatusAccumulator accumulator =
                new DpApplication.SampleStatusAccumulator(new Random(1));

        accumulator.recordError("first failure");
        accumulator.recordError("second failure");

        assertTrue(accumulator.hasError());
        assertEquals("first failure", accumulator.firstError());
    }

    /*
     * A save failure must not discard counts already accumulated: the run continues past a failed
     * bucket, so successes on either side of it are still reported.
     */
    @Test
    public void sampleStatusAccumulatorKeepsCountsAlongsideAnError() {
        DpApplication.SampleStatusAccumulator accumulator =
                new DpApplication.SampleStatusAccumulator(new Random(1));

        accumulator.recordSaved(10);
        accumulator.recordError("bucket 2 failed");
        accumulator.recordSaved(10);

        assertEquals(20, accumulator.savedCount());
        assertTrue(accumulator.hasError());
    }
}
