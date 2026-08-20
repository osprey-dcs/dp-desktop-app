package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.annotation.Calculations;
import com.ospreydcs.dp.grpc.v1.annotation.QueryAnnotationsResponse.AnnotationsResult.Annotation;
import com.ospreydcs.dp.grpc.v1.common.Attribute;
import com.ospreydcs.dp.grpc.v1.common.DataColumn;
import com.ospreydcs.dp.grpc.v1.common.DataTimestamps;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.grpc.v1.common.TimestampList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the Annotation -> AnnotationInfoTableRow conversion: comma-separated
 * formatting of complex fields and calculation frame extraction.
 */
public class AnnotationInfoTableRowTest {

    private static final Timestamp TS =
            Timestamp.newBuilder().setEpochSeconds(1_700_000_000L).setNanoseconds(500).build();

    private static Calculations.CalculationsDataFrame frame(String name) {
        return Calculations.CalculationsDataFrame.newBuilder()
                .setName(name)
                .setDataTimestamps(DataTimestamps.newBuilder()
                        .setTimestampList(TimestampList.newBuilder().addTimestamps(TS)))
                .addDataColumns(DataColumn.newBuilder().setName(name + "-col"))
                .build();
    }

    private static Annotation.Builder baseAnnotation() {
        return Annotation.newBuilder()
                .setId("ann-1")
                .setOwnerId("owner-1")
                .setName("annotation-1")
                .setComment("a comment");
    }

    @Test
    public void conversionPassesThroughSimpleFields() {
        AnnotationInfoTableRow row = new AnnotationInfoTableRow(baseAnnotation().build());

        assertEquals("ann-1", row.getId());
        assertEquals("owner-1", row.getOwner());
        assertEquals("annotation-1", row.getName());
        assertEquals("a comment", row.getComment());
    }

    @Test
    public void listFieldsAreJoinedWithCommas() {
        AnnotationInfoTableRow row = new AnnotationInfoTableRow(baseAnnotation()
                .addDataSetIds("ds-1").addDataSetIds("ds-2")
                .addAnnotationIds("ann-2").addAnnotationIds("ann-3")
                .addTags("alpha").addTags("beta")
                .addAttributes(Attribute.newBuilder().setName("k1").setValue("v1"))
                .addAttributes(Attribute.newBuilder().setName("k2").setValue("v2"))
                .build());

        assertEquals("ds-1, ds-2", row.getRelatedDatasets());
        assertEquals("ann-2, ann-3", row.getRelatedAnnotations());
        assertEquals("alpha, beta", row.getTags());
        assertEquals("k1=v1, k2=v2", row.getAttributes());
        assertEquals(List.of("ds-1", "ds-2"), row.getDataSetIdsList());
        assertEquals(List.of("ann-2", "ann-3"), row.getAnnotationIdsList());
    }

    @Test
    public void emptyListFieldsFormatAsEmptyStrings() {
        AnnotationInfoTableRow row = new AnnotationInfoTableRow(baseAnnotation().build());

        assertEquals("", row.getRelatedDatasets());
        assertEquals("", row.getRelatedAnnotations());
        assertEquals("", row.getTags());
        assertEquals("", row.getAttributes());
        assertEquals("", row.getCalculationsDataFrames());
    }

    @Test
    public void calculationFrameNamesAreJoinedWithCommas() {
        AnnotationInfoTableRow row = new AnnotationInfoTableRow(baseAnnotation()
                .setCalculations(Calculations.newBuilder()
                        .setId("calc-1")
                        .addCalculationDataFrames(frame("frame-1"))
                        .addCalculationDataFrames(frame("frame-2")))
                .build());

        assertEquals("frame-1, frame-2", row.getCalculationsDataFrames());
        assertEquals(List.of("frame-1", "frame-2"), row.getCalculationsDataFrameNames());
    }

    @Test
    public void calculationFrameIsConvertedToDataFrameDetailsByName() {
        AnnotationInfoTableRow row = new AnnotationInfoTableRow(baseAnnotation()
                .setCalculations(Calculations.newBuilder()
                        .setId("calc-1")
                        .addCalculationDataFrames(frame("frame-1")))
                .build());

        DataFrameDetails details = row.getCalculationDataFrameByName("frame-1");
        assertNotNull(details);
        assertEquals("frame-1", details.getName());
        assertEquals(List.of(TS), details.getTimestamps());
        assertEquals(1, details.getDataColumns().size());
        assertEquals("frame-1-col", details.getDataColumns().get(0).getName());
    }

    @Test
    public void unknownCalculationFrameNameReturnsNull() {
        AnnotationInfoTableRow row = new AnnotationInfoTableRow(baseAnnotation()
                .setCalculations(Calculations.newBuilder()
                        .addCalculationDataFrames(frame("frame-1")))
                .build());

        assertNull(row.getCalculationDataFrameByName("no-such-frame"));
        assertNull(new AnnotationInfoTableRow(baseAnnotation().build())
                .getCalculationDataFrameByName("frame-1"));
    }

    @Test
    public void nullAnnotationYieldsEmptyFields() {
        AnnotationInfoTableRow row = new AnnotationInfoTableRow(null);

        assertEquals("", row.getId());
        assertEquals("", row.getOwner());
        assertEquals("", row.getName());
        assertEquals("", row.getComment());
        assertEquals("", row.getRelatedDatasets());
        assertEquals(List.of(), row.getDataSetIdsList());
        assertEquals(List.of(), row.getCalculationsDataFrameNames());
        assertNull(row.getCalculationDataFrameByName("frame-1"));
    }
}
