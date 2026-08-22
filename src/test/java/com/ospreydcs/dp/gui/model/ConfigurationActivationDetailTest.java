package com.ospreydcs.dp.gui.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the display formatting of ConfigurationActivationDetail, which is what the Machine
 * Configuration view's session activation list renders for each entry.
 *
 * Times are built from a local date-time and converted through the system zone, so the assertions
 * hold in any timezone: the model formats in the system default zone, which is what the rest of
 * this app's time handling does.
 */
public class ConfigurationActivationDetailTest {

    private static Instant instantAt(int year, int month, int day, int hour, int minute, int second) {
        return LocalDateTime.of(year, month, day, hour, minute, second)
                .atZone(ZoneId.systemDefault())
                .toInstant();
    }

    @Test
    public void displayStringShowsIdAndInterval() {
        ConfigurationActivationDetail detail = new ConfigurationActivationDetail(
                "activation-1",
                "beamline-a",
                instantAt(2026, 8, 21, 9, 0, 0),
                instantAt(2026, 8, 21, 17, 30, 15));

        assertEquals(
                "activation-1: 2026-08-21 09:00:00 -> 2026-08-21 17:30:15",
                detail.getDisplayString());
    }

    @Test
    public void toStringDelegatesToDisplayString() {
        ConfigurationActivationDetail detail = new ConfigurationActivationDetail(
                "activation-1",
                "beamline-a",
                instantAt(2026, 8, 21, 9, 0, 0),
                instantAt(2026, 8, 21, 17, 30, 15));

        // The ListView renders items via toString(), so the two must not diverge.
        assertEquals(detail.getDisplayString(), detail.toString());
    }

    /**
     * A server-generated activation id is the caller's only handle on the record, so it has to be
     * what the list displays.
     */
    @Test
    public void displayStringShowsServerGeneratedId() {
        ConfigurationActivationDetail detail = new ConfigurationActivationDetail(
                "550e8400-e29b-41d4-a716-446655440000",
                "beamline-a",
                instantAt(2026, 8, 21, 9, 0, 0),
                instantAt(2026, 8, 21, 10, 0, 0));

        assertTrue(detail.getDisplayString().startsWith("550e8400-e29b-41d4-a716-446655440000: "));
    }

    /**
     * The view requires an end time in this version, so this is not reachable through the form -
     * but the model does not impose the restriction the UI does, and an open-ended activation must
     * not render as one ending at the epoch.
     */
    @Test
    public void nullEndTimeRendersAsOpenEnded() {
        ConfigurationActivationDetail detail = new ConfigurationActivationDetail(
                "activation-1", "beamline-a", instantAt(2026, 8, 21, 9, 0, 0), null);

        assertEquals("activation-1: 2026-08-21 09:00:00 -> open-ended", detail.getDisplayString());
    }

    @Test
    public void missingIdAndStartTimeRenderAsPlaceholders() {
        ConfigurationActivationDetail detail =
                new ConfigurationActivationDetail(null, "beamline-a", null, null);

        assertEquals("(no activation id): (no start time) -> open-ended", detail.getDisplayString());
    }

    @Test
    public void blankIdRendersAsPlaceholder() {
        ConfigurationActivationDetail detail = new ConfigurationActivationDetail(
                "", "beamline-a", instantAt(2026, 8, 21, 9, 0, 0), instantAt(2026, 8, 21, 10, 0, 0));

        assertTrue(detail.getDisplayString().startsWith("(no activation id): "));
    }
}
