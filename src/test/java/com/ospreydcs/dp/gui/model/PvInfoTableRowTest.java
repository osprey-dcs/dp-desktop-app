package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.grpc.v1.query.QueryPvStatsResponse.StatsResult.PvStats;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the PvStats -> PvInfoTableRow conversion: sample period formatting,
 * timestamp formatting, and pass-through fields.
 * Timestamps are asserted as literal UTC strings; the class pins the default
 * time zone to UTC in @BeforeAll (surefire also pins the fork) so the expectations
 * hold regardless of how the test is run.
 */
public class PvInfoTableRowTest {
    /*
     * These tests assert literal timestamp strings, and the classes under test format
     * via ZoneId.systemDefault(). Surefire pins the fork to UTC, but that does not apply
     * when the class is run directly from an IDE, so pin it here too and make the tests
     * runner-independent. The previous default is restored afterwards because surefire
     * reuses a single fork across test classes (forkCount=1, reuseForks=true), so an
     * unrestored setDefault would silently leak into every class that runs later.
     */
    private static TimeZone previousDefaultTimeZone;

    @BeforeAll
    public static void pinTimeZoneToUtc() {
        previousDefaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll
    public static void restoreTimeZone() {
        TimeZone.setDefault(previousDefaultTimeZone);
    }


    private static Timestamp timestamp(String isoInstant) {
        Instant instant = Instant.parse(isoInstant);
        return Timestamp.newBuilder()
                .setEpochSeconds(instant.getEpochSecond())
                .setNanoseconds(instant.getNano())
                .build();
    }

    private static PvStats.Builder baseStats() {
        return PvStats.newBuilder()
                .setPvName("pv-1")
                .setLastProviderId("provider-id-1")
                .setLastProviderName("provider-1")
                .setLastBucketDataType("DoubleValue")
                .setLastBucketDataTimestampsType("SamplingClock")
                .setNumBuckets(7);
    }

    @Test
    public void conversionPassesThroughSimpleFields() {
        PvInfoTableRow row = new PvInfoTableRow(baseStats().build());

        assertEquals("pv-1", row.getPvName());
        assertEquals("provider-1", row.getProviderName());
        assertEquals("provider-id-1", row.getLastProviderId());
        assertEquals("DoubleValue", row.getDataType());
        assertEquals("SamplingClock", row.getTimestampsType());
        assertEquals(7, row.getNumBuckets());
    }

    @Test
    public void zeroSamplePeriodIsIrregular() {
        PvInfoTableRow row = new PvInfoTableRow(baseStats().setLastBucketSamplePeriod(0).build());
        assertEquals("Irregular", row.getSamplePeriod());
    }

    @Test
    public void subMillisecondSamplePeriodShownInNanoseconds() {
        PvInfoTableRow row = new PvInfoTableRow(baseStats().setLastBucketSamplePeriod(500).build());
        assertEquals("500 ns", row.getSamplePeriod());
    }

    @Test
    public void subSecondSamplePeriodShownInMilliseconds() {
        PvInfoTableRow row = new PvInfoTableRow(
                baseStats().setLastBucketSamplePeriod(5_000_000L).build());
        assertEquals("5 ms", row.getSamplePeriod());
    }

    /**
     * 1_000_000 ns is the exact ns -> ms boundary. The source branches on
     * "< 1_000_000", so this value must format as ms, not ns. Without this case
     * an off-by-one change of "<" to "<=" would leave every other test green.
     */
    @Test
    public void samplePeriodAtMillisecondBoundaryShownInMilliseconds() {
        PvInfoTableRow row = new PvInfoTableRow(
                baseStats().setLastBucketSamplePeriod(1_000_000L).build());
        assertEquals("1 ms", row.getSamplePeriod());
    }

    /**
     * 1_000_000_000 ns is the exact ms -> s boundary; the source branches on
     * "< 1_000_000_000", so this value must format as s, not ms.
     */
    @Test
    public void samplePeriodAtSecondBoundaryShownInSeconds() {
        PvInfoTableRow row = new PvInfoTableRow(
                baseStats().setLastBucketSamplePeriod(1_000_000_000L).build());
        assertEquals("1 s", row.getSamplePeriod());
    }

    /**
     * Sample periods are rendered with integer division, so a fractional part is
     * truncated rather than rounded: 1.5 ms displays as "1 ms". This pins the
     * current user-visible behavior so a future switch to rounding is a
     * deliberate, visible change rather than a silent one.
     */
    @Test
    public void fractionalSamplePeriodIsTruncatedNotRounded() {
        PvInfoTableRow millis = new PvInfoTableRow(
                baseStats().setLastBucketSamplePeriod(1_500_000L).build());
        assertEquals("1 ms", millis.getSamplePeriod());

        PvInfoTableRow seconds = new PvInfoTableRow(
                baseStats().setLastBucketSamplePeriod(1_500_000_000L).build());
        assertEquals("1 s", seconds.getSamplePeriod());
    }

    @Test
    public void samplePeriodOfSecondsShownInSeconds() {
        PvInfoTableRow row = new PvInfoTableRow(
                baseStats().setLastBucketSamplePeriod(2_000_000_000L).build());
        assertEquals("2 s", row.getSamplePeriod());
    }

    @Test
    public void dataTimestampsAreFormattedAsIsoLocalDateTime() {
        PvInfoTableRow row = new PvInfoTableRow(baseStats()
                .setFirstDataTimestamp(timestamp("2025-08-15T11:03:07Z"))
                .setLastDataTimestamp(timestamp("2025-08-15T11:05:09Z"))
                .build());

        assertEquals("2025-08-15T11:03:07", row.getFirstDataTimestamp());
        assertEquals("2025-08-15T11:05:09", row.getLastDataTimestamp());
    }

    @Test
    public void missingDataTimestampsShowNotAvailable() {
        PvInfoTableRow row = new PvInfoTableRow(baseStats().build());

        assertEquals("N/A", row.getFirstDataTimestamp());
        assertEquals("N/A", row.getLastDataTimestamp());
    }

    @Test
    public void toStringSummarizesNameTypeAndBuckets() {
        PvInfoTableRow row = new PvInfoTableRow(baseStats().build());
        assertEquals("pv-1 (DoubleValue, 7 buckets)", row.toString());
    }
}
