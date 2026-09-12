package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.annotation.Annotation;
import javafx.scene.control.cell.PropertyValueFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Guards the reflective TableView bindings in AnnotationExploreController.
 *
 * Each column there is wired with `new PropertyValueFactory<>("someName")`, a STRING resolved
 * against AnnotationInfoTableRow by reflection at render time.  Renaming a row property without
 * updating the matching string produces a silently blank column -- no compile error, and
 * ViewLoadSmokeTest does not catch it either, because it only proves initialize() ran and never
 * populates a row.
 *
 * That is exactly how the comment -> description rename (dp-grpc #132, plan/tickets/42 P3.1) could
 * have gone wrong, so the property names are asserted here rather than left to inspection.  If a
 * property is renamed, this test fails and names the string in the controller that must move with
 * it.
 */
public class AnnotationInfoTableRowBindingTest {

    /**
     * The property names passed to PropertyValueFactory in
     * AnnotationExploreController.setupTableColumns(), each paired with the value it must resolve
     * to for the row built below.
     *
     * The expected value matters: PropertyValueFactory returns null only when a name resolves to
     * NOTHING, and it falls back from someProperty() to getSome() -- so asserting non-null alone
     * would pass even for a binding reading the wrong field.  Asserting the value is what makes
     * this a real guard.
     */
    private static final List<String[]> BOUND_PROPERTIES = List.of(
            new String[]{"id", "ann-1"},
            new String[]{"owner", "owner-1"},
            new String[]{"name", "annotation-1"},
            new String[]{"description", "a description"},
            new String[]{"tags", "alpha"},
            new String[]{"attributes", ""},
            new String[]{"relatedDatasets", "ds-1"},
            new String[]{"relatedAnnotations", "ann-2"},
            new String[]{"calculationsDataFrames", AnnotationInfoTableRow.CALCULATIONS_PRESENT_LABEL});

    private static AnnotationInfoTableRow sampleRow() {
        return new AnnotationInfoTableRow(Annotation.newBuilder()
                .setId("ann-1")
                .setOwnerId("owner-1")
                .setName("annotation-1")
                .setDescription("a description")
                .addTags("alpha")
                .addDataSetIds("ds-1")
                .addAnnotationIds("ann-2")
                .setCalculationsId("calc-1")
                .build());
    }

    private static String resolve(String propertyName, AnnotationInfoTableRow row) {
        // resolve exactly as the TableView does at render time
        final PropertyValueFactory<AnnotationInfoTableRow, String> factory =
                new PropertyValueFactory<>(propertyName);

        final javafx.beans.value.ObservableValue<String> value = factory.call(
                new javafx.scene.control.TableColumn.CellDataFeatures<>(null, null, row));

        assertNotNull(value,
                "PropertyValueFactory(\"" + propertyName + "\") resolved to nothing against "
                        + "AnnotationInfoTableRow -- the row property was renamed without updating "
                        + "the binding string in AnnotationExploreController.setupTableColumns()");

        return value.getValue();
    }

    @Test
    public void everyBoundPropertyNameResolvesToItsRowValue() {
        final AnnotationInfoTableRow row = sampleRow();

        for (String[] binding : BOUND_PROPERTIES) {
            final String propertyName = binding[0];
            final String expected = binding[1];

            assertEquals(expected, resolve(propertyName, row),
                    "column bound to \"" + propertyName + "\" renders the wrong value");
        }
    }

    /**
     * A name matching no property at all resolves to null, which is what a stale binding string
     * looks like and what the assertion above relies on to detect one.
     */
    @Test
    public void anUnresolvableNameYieldsNullRatherThanThrowing() {
        final PropertyValueFactory<AnnotationInfoTableRow, String> factory =
                new PropertyValueFactory<>("noSuchProperty");

        assertNull(factory.call(new javafx.scene.control.TableColumn.CellDataFeatures<>(
                null, null, sampleRow())));
    }

    /**
     * The description column specifically: it is the one the #132 rename touched, and a blank
     * column is the failure it would have produced.
     */
    @Test
    public void descriptionBindingReadsTheAnnotationDescription() {
        final AnnotationInfoTableRow row = new AnnotationInfoTableRow(Annotation.newBuilder()
                .setDescription("a description")
                .build());

        final PropertyValueFactory<AnnotationInfoTableRow, String> factory =
                new PropertyValueFactory<>("description");

        assertEquals("a description", factory.call(
                new javafx.scene.control.TableColumn.CellDataFeatures<>(null, null, row)).getValue());
    }
}
