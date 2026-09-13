package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.common.DataColumn;
import com.ospreydcs.dp.grpc.v1.common.DataValue;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.grpc.v1.common.TimestampList;
import com.ospreydcs.dp.grpc.v1.query.ColumnTable;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the Query API V2 decode point -- the transpose of a column-oriented ColumnTable into the
 * row-oriented structure the results table and chart consume.
 *
 * These are pure statics precisely so the transpose is testable without a service ecosystem, for
 * the same reason as accumulatePages() and SampleStatusTableRow.expand().  Every case guarded here
 * produces PLAUSIBLE-LOOKING WRONG OUTPUT rather than an obvious failure: a dropped timestamp
 * column leaves a table that renders and a chart that silently finds no axis, a missing value
 * rendered as text is indistinguishable from a PV that really reported that text, and a transpose
 * that reads its row count from a column truncates every row of the page at once.
 */
public class DataExploreV2DecodeTest {

    private static Timestamp timestampAt(long epochSeconds, int nanos) {
        return Timestamp.newBuilder().setEpochSeconds(epochSeconds).setNanoseconds(nanos).build();
    }

    private static DataValue doubleValue(double value) {
        return DataValue.newBuilder().setDoubleValue(value).build();
    }

    /** The server's encoding for "this PV had no sample at this timestamp": an unset value oneof. */
    private static DataValue unsetValue() {
        return DataValue.newBuilder().build();
    }

    private static ColumnTable tableWith(List<Timestamp> timestamps, DataColumn... columns) {
        final ColumnTable.Builder builder = ColumnTable.newBuilder()
                .setTimestampList(TimestampList.newBuilder().addAllTimestamps(timestamps));
        for (DataColumn column : columns) {
            builder.addDataColumns(column);
        }
        return builder.build();
    }

    // ------------------- column names ---------------------------

    /**
     * The V2 table carries no timestamp column -- the axis is only in timestampList -- while the
     * V1 ROW_MAP table this replaced carried "timestamp" as an ordinary column.  Both the results
     * table and the chart locate the time axis by that literal name, so the reshape must synthesize
     * it.  Without this the chart logs "No timestamp column found" and renders nothing, while the
     * table still displays -- a half-working view rather than an error.
     */
    @Test
    public void theTimestampColumnIsSynthesizedAndComesFirst() {
        final ColumnTable table = tableWith(
                List.of(timestampAt(1_700_000_000L, 0)),
                DataColumn.newBuilder().setName("PV:B").addDataValues(doubleValue(2.0)).build(),
                DataColumn.newBuilder().setName("PV:A").addDataValues(doubleValue(1.0)).build());

        final List<String> columnNames = DataExploreViewModel.columnNamesOf(table);

        assertEquals(List.of(DataExploreViewModel.TIMESTAMP_COLUMN_NAME, "PV:B", "PV:A"), columnNames,
                "the synthesized timestamp column must come first, and the PV columns must keep the "
                        + "server's order -- the chart maps series to columns positionally");
    }

    @Test
    public void aTableWithNoColumnsStillHasTheTimestampColumn() {
        final List<String> columnNames =
                DataExploreViewModel.columnNamesOf(tableWith(List.of(timestampAt(1L, 0))));

        assertEquals(List.of(DataExploreViewModel.TIMESTAMP_COLUMN_NAME), columnNames);
    }

    // ------------------- the transpose ---------------------------

    @Test
    public void columnsAreTransposedAgainstTheTimestampAxis() {
        final ColumnTable table = tableWith(
                List.of(timestampAt(1_700_000_000L, 0),
                        timestampAt(1_700_000_001L, 0),
                        timestampAt(1_700_000_002L, 0)),
                DataColumn.newBuilder().setName("PV:A")
                        .addDataValues(doubleValue(1.0))
                        .addDataValues(doubleValue(2.0))
                        .addDataValues(doubleValue(3.0)).build(),
                DataColumn.newBuilder().setName("PV:B")
                        .addDataValues(doubleValue(10.0))
                        .addDataValues(doubleValue(20.0))
                        .addDataValues(doubleValue(30.0)).build());

        final List<ObservableList<Object>> rows = DataExploreViewModel.reshapePage(table);

        assertEquals(3, rows.size(), "one row per timestamp");
        assertEquals(3, rows.get(0).size(), "timestamp column plus one cell per PV column");

        // column 0 is the timestamp; columns 1 and 2 are PV:A and PV:B in order
        assertEquals(1.0, rows.get(0).get(1));
        assertEquals(10.0, rows.get(0).get(2));
        assertEquals(2.0, rows.get(1).get(1));
        assertEquals(20.0, rows.get(1).get(2));
        assertEquals(3.0, rows.get(2).get(1));
        assertEquals(30.0, rows.get(2).get(2));
    }

    /**
     * The row count comes from the timestamp axis, never from a column.  The server guarantees one
     * DataValue per column per timestamp, but reading the count from a column would silently
     * truncate the WHOLE page if that guarantee were ever broken, whereas over-indexing a short
     * column is caught per cell.
     */
    @Test
    public void aShortColumnCostsOneCellRatherThanTheRestOfThePage() {
        final ColumnTable table = tableWith(
                List.of(timestampAt(1L, 0), timestampAt(2L, 0), timestampAt(3L, 0)),
                // one value where the axis has three
                DataColumn.newBuilder().setName("PV:SHORT").addDataValues(doubleValue(7.0)).build());

        final List<ObservableList<Object>> rows = DataExploreViewModel.reshapePage(table);

        assertEquals(3, rows.size(),
                "the timestamp axis determines the row count -- reading it from the column would "
                        + "have dropped two of the three rows");
        assertEquals(7.0, rows.get(0).get(1));
        assertEquals("", rows.get(1).get(1), "a cell with no value renders blank");
        assertEquals("", rows.get(2).get(1));
    }

    @Test
    public void anEmptyPageYieldsNoRows() {
        final List<ObservableList<Object>> rows = DataExploreViewModel.reshapePage(
                tableWith(List.of(),
                        DataColumn.newBuilder().setName("PV:A").build()));

        assertTrue(rows.isEmpty(),
                "an empty result is a success carrying an empty table, not a failure");
    }

    @Test
    public void theTimestampCellIsFormattedFromTheAxisIncludingNanoseconds() {
        final ColumnTable table = tableWith(
                List.of(timestampAt(1_700_000_000L, 123_456_789)),
                DataColumn.newBuilder().setName("PV:A").addDataValues(doubleValue(1.0)).build());

        final Object timestampCell = DataExploreViewModel.reshapePage(table).get(0).get(0);

        assertInstanceOf(String.class, timestampCell);
        final String rendered = (String) timestampCell;
        // the exact rendering is zone-dependent, so assert the shape the chart's parser requires
        // rather than a literal that would fail outside the author's timezone
        assertTrue(rendered.contains("T"),
                "DataExploreController.parseTimestampToSeconds() parses ISO_LOCAL_DATE_TIME and "
                        + "keys on the 'T'; without it every chart point is silently skipped");
        assertTrue(rendered.contains(".123456789"),
                "sub-second precision must survive the format, since the chart plots fractional "
                        + "seconds -- but was: " + rendered);
    }

    // ------------------- value rendering ---------------------------

    /**
     * The distinction this test pins did not exist on the V1 path.  There, a column absent from a
     * row map and a DataValue the decoder did not recognize BOTH became the string "N/A", so
     * genuinely missing data was indistinguishable from a decode gap.  V2 encodes "no sample here"
     * as an unset oneof, and rendering it blank is what makes that distinction visible.
     */
    @Test
    public void anUnsetValueRendersBlankRatherThanAsText() {
        assertEquals("", DataExploreViewModel.renderDataValue(unsetValue()),
                "a missing sample must not render as text -- 'N/A' in a cell is indistinguishable "
                        + "from a string PV that really reported \"N/A\"");
    }

    @Test
    public void aMissingValueInTheMiddleOfAColumnIsBlankWhileItsNeighboursAreNot() {
        final ColumnTable table = tableWith(
                List.of(timestampAt(1L, 0), timestampAt(2L, 0), timestampAt(3L, 0)),
                DataColumn.newBuilder().setName("PV:A")
                        .addDataValues(doubleValue(1.0))
                        .addDataValues(unsetValue())
                        .addDataValues(doubleValue(3.0)).build());

        final List<ObservableList<Object>> rows = DataExploreViewModel.reshapePage(table);

        assertEquals(1.0, rows.get(0).get(1));
        assertEquals("", rows.get(1).get(1));
        assertEquals(3.0, rows.get(2).get(1));
    }

    /**
     * Numeric values are returned as Numbers, not as strings.  DataExploreController.parseNumericValue()
     * would reparse a string successfully, so a regression to string rendering would still plot --
     * but it would also make every numeric cell sort and format as text in the results table.
     */
    @Test
    public void numericValuesAreRenderedAsNumbers() {
        assertInstanceOf(Double.class,
                DataExploreViewModel.renderDataValue(doubleValue(1.5)));
        assertInstanceOf(Float.class,
                DataExploreViewModel.renderDataValue(DataValue.newBuilder().setFloatValue(1.5f).build()));
        assertInstanceOf(Integer.class,
                DataExploreViewModel.renderDataValue(DataValue.newBuilder().setIntValue(-3).build()));
        assertInstanceOf(Long.class,
                DataExploreViewModel.renderDataValue(DataValue.newBuilder().setLongValue(-4L).build()));
    }

    @Test
    public void stringAndBooleanValuesRenderAsThemselves() {
        assertEquals("hello",
                DataExploreViewModel.renderDataValue(DataValue.newBuilder().setStringValue("hello").build()));
        assertEquals(true,
                DataExploreViewModel.renderDataValue(DataValue.newBuilder().setBooleanValue(true).build()));
    }

    /**
     * uint32 and uint64 are unsigned on the wire but signed in Java.  Rendering them with the
     * signed accessors would display a large unsigned reading as a negative number -- a wrong value
     * that looks like a real one.
     */
    @Test
    public void unsignedValuesAreWidenedRatherThanRenderedAsNegatives() {
        final Object uint = DataExploreViewModel.renderDataValue(
                DataValue.newBuilder().setUintValue(-1).build());  // 0xFFFFFFFF
        assertEquals(4_294_967_295L, uint,
                "an unsigned int at its maximum must not display as -1");

        final Object ulong = DataExploreViewModel.renderDataValue(
                DataValue.newBuilder().setUlongValue(-1L).build());
        assertEquals("18446744073709551615", ulong,
                "an unsigned long beyond Long.MAX_VALUE must not display as -1");
    }

    /**
     * A non-scalar value should never reach the decoder -- the samples path rejects non-scalar PVs
     * server-side -- but if one did, a blank cell is the honest rendering.  What must NOT happen is
     * a protobuf toString() leaking into the table.
     */
    @Test
    public void anUnhandledValueKindRendersBlankRatherThanLeakingProtobufText() {
        final Object rendered = DataExploreViewModel.renderDataValue(
                DataValue.newBuilder().setByteArrayValue(
                        com.google.protobuf.ByteString.copyFrom(new byte[]{1, 2, 3})).build());

        assertEquals("", rendered);
        assertFalse(rendered.toString().contains("byteArrayValue"),
                "a protobuf debug string must never reach a table cell");
    }
}
