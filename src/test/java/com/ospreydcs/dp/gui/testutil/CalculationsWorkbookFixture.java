package com.ospreydcs.dp.gui.testutil;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Builds the calculations import workbook used to exercise the Annotation Builder's
 * "Import Calculations" path and, downstream of it, the Calculations presence column and
 * fetch-on-click dialog (dp-desktop-app #42, P2.1).
 *
 * Why this exists as committed code rather than a one-off script: the workbook has to satisfy an
 * exact, unforgiving contract (below), and every way of violating it is SILENT.  A sheet with a
 * blank header cell, a row whose cell count differs from the header's, or an unsupported cell type
 * is skipped with a log line and no error -- so a hand-built file can lose a frame, a column, or
 * a row and still look like it imported.  Generating it from code that the test below round-trips
 * through the real DataImportUtility is what makes "this file is valid" a checked claim.
 *
 * The format required by DataImportUtility.importXlsxData():
 * <ul>
 *   <li>row 0 is the header; every header cell from column 2 on must be non-blank</li>
 *   <li>column 0 is epoch seconds and column 1 is nanoseconds, both numeric</li>
 *   <li>columns 2+ are data: numeric, string or boolean</li>
 *   <li>at least 3 columns per sheet, or the sheet is skipped</li>
 *   <li><b>every data row must have exactly the same cell count as the header</b>, or the row is
 *       skipped -- no ragged rows</li>
 *   <li>each sheet becomes one calculation frame, named after the sheet</li>
 * </ul>
 *
 * The workbook is deliberately MULTI-SHEET.  A single-frame annotation opens the frame dialog
 * directly, so a one-sheet file would never reach the multi-frame chooser -- the branch D1 left
 * open and P2.1 implemented.  The sheets also cover all three DataValue types, since a workbook of
 * nothing but doubles would leave the string and boolean branches of createDataValueFromCell()
 * and the dialog's formatDataValue() unexercised.
 *
 * Timestamps derive from a FIXED base instant rather than "now", so regenerating produces an
 * identical file and a checked-in copy does not drift from what this code emits.
 */
public final class CalculationsWorkbookFixture {

    /**
     * Base timestamp for every sheet.  Fixed, so the generated workbook is byte-stable across
     * runs apart from the metadata POI stamps.
     */
    public static final Instant BASE_TIME = Instant.parse("2026-09-12T17:00:00Z");

    /** Sheet names, which become the calculation frame names. */
    public static final String SHEET_BEAM_CURRENT = "beam-current-stats";
    public static final String SHEET_ALARM_SUMMARY = "alarm-summary";
    public static final String SHEET_DRIFT_CORRECTION = "drift-correction";

    /** Data row counts per sheet, asserted by CalculationsWorkbookFixtureTest. */
    public static final int BEAM_CURRENT_ROWS = 10;
    public static final int ALARM_SUMMARY_ROWS = 8;
    public static final int DRIFT_CORRECTION_ROWS = 6;

    /** Alarm severities on the alarm-summary sheet; the string-valued column. */
    static final String[] ALARM_SEVERITIES = {
            "NO_ALARM", "NO_ALARM", "MINOR_ALARM", "MAJOR_ALARM", "NO_ALARM",
            "MINOR_ALARM", "NO_ALARM", "INVALID_ALARM"};

    private CalculationsWorkbookFixture() {
    }

    /**
     * Writes the workbook to the given path, creating parent directories as needed.
     *
     * @return the path written, for convenience in tests
     */
    public static Path write(Path outputPath) throws IOException {
        final Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            final CellStyle headerStyle = workbook.createCellStyle();
            final Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            writeBeamCurrentSheet(workbook, headerStyle);
            writeAlarmSummarySheet(workbook, headerStyle);
            writeDriftCorrectionSheet(workbook, headerStyle);

            try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
                workbook.write(out);
            }
        }

        return outputPath;
    }

    /** Numeric columns: the ordinary case, and what the frame dialog renders as %.3f. */
    private static void writeBeamCurrentSheet(XSSFWorkbook workbook, CellStyle headerStyle) {
        final Sheet sheet = workbook.createSheet(SHEET_BEAM_CURRENT);
        writeHeader(sheet, headerStyle, "seconds", "nanos", "mean", "stddev", "peak");

        for (int i = 0; i < BEAM_CURRENT_ROWS; i++) {
            final Row row = sheet.createRow(i + 1);
            numeric(row, 0, BASE_TIME.getEpochSecond() + i);
            numeric(row, 1, i * 100_000_000L);
            numeric(row, 2, 100.0 + Math.sin(i / 2.0) * 5);
            numeric(row, 3, 0.42 + i * 0.01);
            numeric(row, 4, 110.0 + Math.cos(i / 3.0) * 4);
        }
        autoSize(sheet, 5);
    }

    /**
     * Mixed types: exercises the string and boolean branches of createDataValueFromCell() and of
     * the dialog's formatDataValue(), which a numeric-only workbook would leave untested.
     */
    private static void writeAlarmSummarySheet(XSSFWorkbook workbook, CellStyle headerStyle) {
        final Sheet sheet = workbook.createSheet(SHEET_ALARM_SUMMARY);
        writeHeader(sheet, headerStyle,
                "seconds", "nanos", "severity", "in-tolerance", "excursion-count");

        for (int i = 0; i < ALARM_SEVERITIES.length; i++) {
            final Row row = sheet.createRow(i + 1);
            numeric(row, 0, BASE_TIME.getEpochSecond() + i * 2L);
            numeric(row, 1, 0);
            string(row, 2, ALARM_SEVERITIES[i]);
            bool(row, 3, "NO_ALARM".equals(ALARM_SEVERITIES[i]));
            numeric(row, 4, i % 4);
        }
        autoSize(sheet, 5);
    }

    /** A single data column: the minimum valid sheet width of 3 columns. */
    private static void writeDriftCorrectionSheet(XSSFWorkbook workbook, CellStyle headerStyle) {
        final Sheet sheet = workbook.createSheet(SHEET_DRIFT_CORRECTION);
        writeHeader(sheet, headerStyle, "seconds", "nanos", "correction-mm");

        for (int i = 0; i < DRIFT_CORRECTION_ROWS; i++) {
            final Row row = sheet.createRow(i + 1);
            numeric(row, 0, BASE_TIME.getEpochSecond() + i * 5L);
            numeric(row, 1, 500_000_000L);
            numeric(row, 2, -0.015 + i * 0.004);
        }
        autoSize(sheet, 3);
    }

    private static void writeHeader(Sheet sheet, CellStyle style, String... names) {
        final Row header = sheet.createRow(0);
        for (int i = 0; i < names.length; i++) {
            final Cell cell = header.createCell(i);
            cell.setCellValue(names[i]);
            cell.setCellStyle(style);
        }
    }

    private static void autoSize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static void numeric(Row row, int column, double value) {
        row.createCell(column).setCellValue(value);
    }

    private static void string(Row row, int column, String value) {
        row.createCell(column).setCellValue(value);
    }

    private static void bool(Row row, int column, boolean value) {
        row.createCell(column).setCellValue(value);
    }

    /**
     * Regenerates the workbook at a path given on the command line, for manual verification runs:
     *
     * <pre>
     * mvn -q test-compile exec:java \
     *   -Dexec.classpathScope=test \
     *   -Dexec.mainClass=com.ospreydcs.dp.gui.testutil.CalculationsWorkbookFixture \
     *   -Dexec.args="$HOME/dp/dev/tickets/dp-desktop-app/42/calculations-import.xlsx"
     * </pre>
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: CalculationsWorkbookFixture <output.xlsx>");
            System.exit(2);
        }
        System.out.println("wrote " + write(Path.of(args[0])).toAbsolutePath());
    }
}
