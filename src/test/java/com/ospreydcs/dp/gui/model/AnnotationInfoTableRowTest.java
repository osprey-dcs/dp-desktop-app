package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.annotation.Annotation;
import com.ospreydcs.dp.grpc.v1.annotation.Calculations;
import com.ospreydcs.dp.grpc.v1.common.Attribute;
import com.ospreydcs.dp.grpc.v1.common.DataColumn;
import com.ospreydcs.dp.grpc.v1.common.DataFrame;
import com.ospreydcs.dp.grpc.v1.common.DataTimestamps;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.grpc.v1.common.TimestampList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                .setFrame(DataFrame.newBuilder()
                        .setDataTimestamps(DataTimestamps.newBuilder()
                                .setTimestampList(TimestampList.newBuilder().addTimestamps(TS)))
                        .addDataColumns(DataColumn.newBuilder().setName(name + "-col")))
                .build();
    }

    private static Annotation.Builder baseAnnotation() {
        return Annotation.newBuilder()
                .setId("ann-1")
                .setOwnerId("owner-1")
                .setName("annotation-1")
                .setDescription("a comment");
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

    /**
     * Calculations presence comes from calculationsId, not from embedded content: queryAnnotations()
     * returns the id alone as of dp-grpc #132, so a row built from a query result has a
     * calculationsId and no Calculations message at all.
     */
    @Test
    public void calculationsPresenceIsDrivenByCalculationsId() {
        AnnotationInfoTableRow row = new AnnotationInfoTableRow(baseAnnotation()
                .setCalculationsId("calc-1")
                .build());

        assertTrue(row.hasCalculations());
        assertEquals("calc-1", row.getCalculationsId());
        assertEquals(AnnotationInfoTableRow.CALCULATIONS_PRESENT_LABEL, row.getCalculationsDataFrames());
    }

    @Test
    public void absentCalculationsIdMeansNoCalculations() {
        AnnotationInfoTableRow row = new AnnotationInfoTableRow(baseAnnotation().build());

        assertFalse(row.hasCalculations());
        assertEquals("", row.getCalculationsId());
        assertEquals("", row.getCalculationsDataFrames());
    }

    /**
     * The presence indicator must not depend on embedded content.  An annotation carrying
     * Calculations content but no id would be a malformed record, and one carrying an id but no
     * content is the normal shape of every query result -- so the id alone decides.
     */
    @Test
    public void calculationsContentDoesNotAffectPresence() {
        AnnotationInfoTableRow row = new AnnotationInfoTableRow(baseAnnotation()
                .setCalculations(Calculations.newBuilder()
                        .addCalculationDataFrames(frame("frame-1")))
                .build());

        assertFalse(row.hasCalculations());
        assertEquals("", row.getCalculationsDataFrames());
    }

    /**
     * The frame conversion the dialog depends on now happens against a fetched Calculations rather
     * than against the row, so it is exercised here at its new home.
     */
    @Test
    public void calculationFrameIsConvertedToDataFrameDetails() {
        DataFrameDetails details = DataFrameDetails.fromCalculationsDataFrame(frame("frame-1"));

        assertNotNull(details);
        assertEquals("frame-1", details.getName());
        assertEquals(List.of(TS), details.getTimestamps());
        assertEquals(1, details.getDataColumns().size());
        assertEquals("frame-1-col", details.getDataColumns().get(0).getName());
        assertNull(DataFrameDetails.fromCalculationsDataFrame(null));
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
        assertEquals("", row.getCalculationsId());
        assertFalse(row.hasCalculations());
    }
}
