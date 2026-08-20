package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.grpc.v1.query.QueryPvStatsResponse.StatsResult.PvStats;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the PvStats -> PvInfoTableRow conversion: sample period formatting,
 * timestamp formatting, and pass-through fields.
 * The test JVM runs in UTC (see surefire argLine), so expected strings are literal.
 */
public class PvInfoTableRowTest {

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
