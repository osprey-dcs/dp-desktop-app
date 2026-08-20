package com.ospreydcs.dp.gui.model;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the user-visible toString() format of DataSetDetail:
 * "ID: [dataset-id] - Dataset name - Description snippet - First data block".
 */
public class DataSetDetailTest {
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


    private static final Instant BEGIN = Instant.parse("2025-08-15T11:03:00Z");
    private static final Instant END = Instant.parse("2025-08-15T11:05:00Z");

    private static DataBlockDetail sampleBlock() {
        return new DataBlockDetail(List.of("pv-1", "pv-2"), BEGIN, END);
    }

    @Test
    public void toStringFormatsAllFields() {
        DataSetDetail dataset = new DataSetDetail(
                "ds-123", "My Dataset", "Short description", List.of(sampleBlock()));
        assertEquals(
                "ID: ds-123 - My Dataset - Short description - "
                        + "pv-1, pv-2: 2025-08-15 11:03:00 -> 2025-08-15 11:05:00",
                dataset.toString());
    }

    @Test
    public void toStringWithNoIdShowsUnsaved() {
        DataSetDetail dataset = new DataSetDetail(null, "My Dataset", "Desc", List.of(sampleBlock()));
        assertEquals(
                "ID: [Unsaved] - My Dataset - Desc - "
                        + "pv-1, pv-2: 2025-08-15 11:03:00 -> 2025-08-15 11:05:00",
                dataset.toString());
    }

    @Test
    public void toStringWithBlankFieldsUsesPlaceholders() {
        DataSetDetail dataset = new DataSetDetail("  ", "", null, null);
        assertEquals("ID: [Unsaved] - Unnamed Dataset - No description - No data blocks",
                dataset.toString());
    }

    @Test
    public void toStringTruncatesLongDescriptionAtThirtyCharacters() {
        String longDescription = "This description is definitely longer than thirty characters";
        DataSetDetail dataset = new DataSetDetail("ds-1", "Name", longDescription, null);
        assertEquals(
                "ID: ds-1 - Name - " + longDescription.substring(0, 30) + "... - No data blocks",
                dataset.toString());
    }

    @Test
    public void toStringShowsOnlyFirstDataBlock() {
        DataBlockDetail second = new DataBlockDetail(List.of("pv-9"), BEGIN, END);
        DataSetDetail dataset = new DataSetDetail("ds-1", "Name", "Desc",
                List.of(sampleBlock(), second));
        assertEquals(
                "ID: ds-1 - Name - Desc - pv-1, pv-2: 2025-08-15 11:03:00 -> 2025-08-15 11:05:00",
                dataset.toString());
    }
}
