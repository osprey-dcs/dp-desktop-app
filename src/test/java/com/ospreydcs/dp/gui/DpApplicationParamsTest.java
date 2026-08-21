package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.annotation.Calculations;
import com.ospreydcs.dp.grpc.v1.common.DataColumn;
import com.ospreydcs.dp.grpc.v1.common.DataValue;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.gui.model.DataFrameDetails;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for the static parameter-normalization helpers on DpApplication: the empty-to-null
 * conversions that keep blank UI fields out of client requests, the conditional criterion
 * setters used by the query wrappers, and the calculations builder used by saveAnnotation().
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
}
