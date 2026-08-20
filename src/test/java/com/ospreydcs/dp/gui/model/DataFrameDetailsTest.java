package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.common.DataColumn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the user-visible toString() format of DataFrameDetails:
 * "Frame name - Column1, Column2, Column3..." with truncation past three columns.
 */
public class DataFrameDetailsTest {

    private static DataColumn column(String name) {
        return DataColumn.newBuilder().setName(name).build();
    }

    @Test
    public void toStringFormatsNameAndColumns() {
        DataFrameDetails frame = new DataFrameDetails(
                "Sheet1", List.of(), List.of(column("col-a"), column("col-b")));
        assertEquals("Sheet1 - col-a, col-b", frame.toString());
    }

    @Test
    public void toStringTruncatesPastThreeColumns() {
        DataFrameDetails frame = new DataFrameDetails("Sheet1", List.of(),
                List.of(column("c1"), column("c2"), column("c3"), column("c4"), column("c5")));
        assertEquals("Sheet1 - c1, c2, c3... (5 columns)", frame.toString());
    }

    @Test
    public void toStringWithBlankNameUsesPlaceholder() {
        DataFrameDetails frame = new DataFrameDetails("  ", List.of(), List.of(column("col-a")));
        assertEquals("Unnamed Frame - col-a", frame.toString());
    }

    @Test
    public void toStringWithNoColumnsReportsNoColumns() {
        DataFrameDetails frame = new DataFrameDetails("Sheet1", List.of(), null);
        assertEquals("Sheet1 - No columns", frame.toString());
    }

    @Test
    public void toStringNamesUnnamedColumnsByPosition() {
        DataFrameDetails frame = new DataFrameDetails(
                "Sheet1", List.of(), List.of(column(""), column("col-b")));
        assertEquals("Sheet1 - Column1, col-b", frame.toString());
    }
}
