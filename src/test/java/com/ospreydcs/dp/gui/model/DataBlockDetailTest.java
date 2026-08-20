package com.ospreydcs.dp.gui.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests for the user-visible toString() format of DataBlockDetail.
 * The test JVM runs in UTC (see surefire argLine), so expected strings are literal.
 */
public class DataBlockDetailTest {

    private static final Instant BEGIN = Instant.parse("2025-08-15T11:03:00Z");
    private static final Instant END = Instant.parse("2025-08-15T11:05:00Z");

    @Test
    public void toStringFormatsPvNamesAndTimeRange() {
        DataBlockDetail block = new DataBlockDetail(List.of("pv-1", "pv-2", "pv-3"), BEGIN, END);
        assertEquals("pv-1, pv-2, pv-3: 2025-08-15 11:03:00 -> 2025-08-15 11:05:00", block.toString());
    }

    @Test
    public void toStringWithNullPvNamesReportsNoPvs() {
        DataBlockDetail block = new DataBlockDetail(null, BEGIN, END);
        assertEquals("No PVs selected", block.toString());
    }

    @Test
    public void toStringWithEmptyPvNamesReportsNoPvs() {
        DataBlockDetail block = new DataBlockDetail(List.of(), BEGIN, END);
        assertEquals("No PVs selected", block.toString());
    }

    @Test
    public void toStringWithMissingTimeReportsNoTimeRange() {
        DataBlockDetail block = new DataBlockDetail(List.of("pv-1"), BEGIN, null);
        assertEquals("pv-1: No time range specified", block.toString());
    }

    @Test
    public void equalsAndHashCodeUseAllFields() {
        DataBlockDetail a = new DataBlockDetail(List.of("pv-1"), BEGIN, END);
        DataBlockDetail b = new DataBlockDetail(List.of("pv-1"), BEGIN, END);
        DataBlockDetail c = new DataBlockDetail(List.of("pv-2"), BEGIN, END);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
