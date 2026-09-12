package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.client.result.DataImportResult;
import com.ospreydcs.dp.client.utility.DataImportUtility;
import com.ospreydcs.dp.grpc.v1.common.DataValue;
import com.ospreydcs.dp.gui.testutil.CalculationsWorkbookFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips CalculationsWorkbookFixture through the real DataImportUtility.
 *
 * The fixture exists to be imported by hand during manual verification, and DataImportUtility
 * rejects malformed input SILENTLY: a sheet with a blank header cell, a row whose cell count
 * differs from the header's, or an unsupported cell type is skipped with a log line and no error.
 * A broken fixture would therefore present as "the feature lost my data" during the manual pass --
 * the reviewer would be debugging the app while the workbook was at fault.
 *
 * These assertions are what make the fixture's validity checked rather than assumed: every sheet
 * becomes a frame, every row survives, and each of the three DataValue types round-trips.
 */
public class CalculationsWorkbookFixtureTest {

    @TempDir
    static Path tempDir;

    private static DataImportResult importResult;

    @BeforeAll
    static void generateAndImport() throws Exception {
        final Path workbook = CalculationsWorkbookFixture.write(
                tempDir.resolve("calculations-import.xlsx"));

        importResult = DataImportUtility.importXlsxData(workbook.toString());
        assertNotNull(importResult, "importXlsxData returned null");
        assertFalse(importResult.resultStatus.isError,
                "the fixture workbook failed to import: " + importResult.resultStatus.msg);
    }

    private static DataImportResult.DataFrameResult frame(String sheetName) {
        return importResult.dataFrames.stream()
                .filter(candidate -> sheetName.equals(candidate.sheetName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "sheet '" + sheetName + "' did not import as a frame; DataImportUtility "
                                + "skips an invalid sheet silently, so this means the fixture "
                                + "violates the import contract"));
    }

    /**
     * Every sheet must become a frame.  A skipped sheet is the failure that looks like a bug in
     * the app during manual verification.
     */
    @Test
    public void everySheetImportsAsACalculationFrame() {
        assertEquals(3, importResult.dataFrames.size(),
                "expected one frame per sheet; imported frames: "
                        + importResult.dataFrames.stream().map(f -> f.sheetName).toList());

        assertNotNull(frame(CalculationsWorkbookFixture.SHEET_BEAM_CURRENT));
        assertNotNull(frame(CalculationsWorkbookFixture.SHEET_ALARM_SUMMARY));
        assertNotNull(frame(CalculationsWorkbookFixture.SHEET_DRIFT_CORRECTION));
    }

    /**
     * More than one frame is the point: a single-frame annotation opens the dialog directly, so a
     * one-sheet fixture would never reach the multi-frame chooser that P2.1 added.
     */
    @Test
    public void theFixtureIsMultiFrameSoItExercisesTheFrameChooser() {
        assertTrue(importResult.dataFrames.size() > 1,
                "the fixture must produce multiple frames, or clicking the Calculations link "
                        + "opens a frame directly and the chooser is never exercised");
    }

    /** No row may be dropped: a ragged row is skipped silently by the importer. */
    @Test
    public void everyDataRowSurvivesTheImport() {
        assertEquals(CalculationsWorkbookFixture.BEAM_CURRENT_ROWS,
                frame(CalculationsWorkbookFixture.SHEET_BEAM_CURRENT).timestamps.size(),
                "rows were dropped from " + CalculationsWorkbookFixture.SHEET_BEAM_CURRENT);
        assertEquals(CalculationsWorkbookFixture.ALARM_SUMMARY_ROWS,
                frame(CalculationsWorkbookFixture.SHEET_ALARM_SUMMARY).timestamps.size(),
                "rows were dropped from " + CalculationsWorkbookFixture.SHEET_ALARM_SUMMARY);
        assertEquals(CalculationsWorkbookFixture.DRIFT_CORRECTION_ROWS,
                frame(CalculationsWorkbookFixture.SHEET_DRIFT_CORRECTION).timestamps.size(),
                "rows were dropped from " + CalculationsWorkbookFixture.SHEET_DRIFT_CORRECTION);
    }

    /** The first two columns are consumed as the timestamp, so data columns are header minus 2. */
    @Test
    public void dataColumnsAreNamedAfterTheHeaderRow() {
        final List<String> beamColumns = new ArrayList<>();
        frame(CalculationsWorkbookFixture.SHEET_BEAM_CURRENT).columns
                .forEach(column -> beamColumns.add(column.getName()));
        assertEquals(List.of("mean", "stddev", "peak"), beamColumns);

        final List<String> driftColumns = new ArrayList<>();
        frame(CalculationsWorkbookFixture.SHEET_DRIFT_CORRECTION).columns
                .forEach(column -> driftColumns.add(column.getName()));
        assertEquals(List.of("correction-mm"), driftColumns,
                "the minimum-width sheet must still yield its single data column");
    }

    /**
     * All three DataValue types must round-trip.  A numeric-only fixture would leave the string
     * and boolean branches of createDataValueFromCell() -- and of the frame dialog's
     * formatDataValue() -- unexercised during the manual pass.
     */
    @Test
    public void allThreeDataValueTypesRoundTrip() {
        final var alarmFrame = frame(CalculationsWorkbookFixture.SHEET_ALARM_SUMMARY);

        final var severity = alarmFrame.columns.get(0);
        assertEquals("severity", severity.getName());
        assertEquals(DataValue.ValueCase.STRINGVALUE, severity.getDataValues(0).getValueCase(),
                "the severity column must import as string values");
        assertEquals("NO_ALARM", severity.getDataValues(0).getStringValue());

        final var inTolerance = alarmFrame.columns.get(1);
        assertEquals("in-tolerance", inTolerance.getName());
        assertEquals(DataValue.ValueCase.BOOLEANVALUE, inTolerance.getDataValues(0).getValueCase(),
                "the in-tolerance column must import as boolean values");
        assertTrue(inTolerance.getDataValues(0).getBooleanValue());
        assertFalse(inTolerance.getDataValues(2).getBooleanValue(),
                "row 3 is MINOR_ALARM, so in-tolerance should be false there");

        final var excursionCount = alarmFrame.columns.get(2);
        assertEquals("excursion-count", excursionCount.getName());
        assertEquals(DataValue.ValueCase.DOUBLEVALUE, excursionCount.getDataValues(0).getValueCase(),
                "the excursion-count column must import as numeric values");
    }

    /** Timestamps come from the fixed base instant, so the fixture does not drift run to run. */
    @Test
    public void timestampsStartAtTheFixedBaseInstant() {
        final var first = frame(CalculationsWorkbookFixture.SHEET_BEAM_CURRENT).timestamps.get(0);
        assertEquals(CalculationsWorkbookFixture.BASE_TIME.getEpochSecond(),
                first.getEpochSeconds(),
                "the fixture must be generated from the fixed base time, not from now()");
        assertEquals(0, first.getNanoseconds());

        // the drift sheet carries a sub-second offset, so nanoseconds are covered too
        final var drift = frame(CalculationsWorkbookFixture.SHEET_DRIFT_CORRECTION).timestamps.get(0);
        assertEquals(500_000_000, drift.getNanoseconds(),
                "the drift-correction sheet should carry its half-second offset");
    }

    /**
     * The conversion the app performs on every imported frame, so the fixture is known to survive
     * the path from importer to the list the Annotation Builder displays.
     */
    @Test
    public void importedFramesConvertToTheModelTheBuilderDisplays() {
        for (DataImportResult.DataFrameResult result : importResult.dataFrames) {
            final DataFrameDetails details = new DataFrameDetails(
                    result.sheetName, result.timestamps, result.columns);

            assertEquals(result.sheetName, details.getName());
            assertFalse(details.getTimestamps().isEmpty(),
                    "frame '" + result.sheetName + "' converted with no timestamps");
            assertFalse(details.getDataColumns().isEmpty(),
                    "frame '" + result.sheetName + "' converted with no data columns");
            assertTrue(details.toString().startsWith(result.sheetName),
                    "the builder's list entry should lead with the frame name, got: " + details);
        }
    }
}
