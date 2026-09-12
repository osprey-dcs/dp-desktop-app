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
 * property is renamed, this test fails and names the binding that must move with it.
 *
 * The names come from the AnnotationInfoTableRow.PROPERTY_* constants the controller itself passes
 * to PropertyValueFactory, NOT from literals duplicated here.  An independent copy would not guard
 * anything: reverting the controller to "comment" would leave a test full of correct-looking
 * strings passing.  Sharing the constants means a rename that misses the controller fails to
 * compile, and one that misses the row property fails here.
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
            new String[]{AnnotationInfoTableRow.PROPERTY_ID, "ann-1"},
            new String[]{AnnotationInfoTableRow.PROPERTY_OWNER, "owner-1"},
            new String[]{AnnotationInfoTableRow.PROPERTY_NAME, "annotation-1"},
            new String[]{AnnotationInfoTableRow.PROPERTY_DESCRIPTION, "a description"},
            new String[]{AnnotationInfoTableRow.PROPERTY_TAGS, "alpha"},
            new String[]{AnnotationInfoTableRow.PROPERTY_ATTRIBUTES, ""},
            new String[]{AnnotationInfoTableRow.PROPERTY_RELATED_DATASETS, "ds-1"},
            new String[]{AnnotationInfoTableRow.PROPERTY_RELATED_ANNOTATIONS, "ann-2"},
            new String[]{AnnotationInfoTableRow.PROPERTY_CALCULATIONS_DATA_FRAMES,
                    AnnotationInfoTableRow.CALCULATIONS_PRESENT_LABEL});

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
                new PropertyValueFactory<>(AnnotationInfoTableRow.PROPERTY_DESCRIPTION);

        assertEquals("a description", factory.call(
                new javafx.scene.control.TableColumn.CellDataFeatures<>(null, null, row)).getValue());
    }
}
