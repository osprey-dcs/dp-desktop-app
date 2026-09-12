package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.annotation.Annotation;
import com.ospreydcs.dp.gui.model.AnnotationInfoTableRow;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that the annotation results table, as wired by the REAL AnnotationExploreController
 * against the REAL FXML, renders each column's value.
 *
 * This is the guard AnnotationInfoTableRowBindingTest cannot be.  That test resolves property
 * names against the row model, which proves the model has the properties but not that the
 * controller binds to them -- with the names duplicated in the test, reverting the controller's
 * binding to a stale string left it passing.  The names are now shared constants, so a stale
 * binding is a compile error; this test covers what constants cannot, namely a column the
 * controller forgets to bind at all, or binds to the wrong one of them.  A blank column is the
 * failure mode in every case, and it is invisible to ViewLoadSmokeTest, which never populates a
 * row.
 *
 * The column values are read through TableColumn.getCellData(), which is exactly what the
 * TableView calls per cell at render time, so a binding that resolves to nothing shows up here
 * as the null it would render as.
 */
public class AnnotationExploreColumnBindingTest {

    private static final String FXML_PATH = "/fxml/annotation-explore.fxml";

    /**
     * The value each column header must render for the row below.  Keyed by header text because
     * that is what identifies a column to the user and is stable across the columns being private
     * controller fields.
     */
    private static Map<String, String> expectedCellValues() {
        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("ID", "ann-1");
        expected.put("Owner", "owner-1");
        expected.put("Related Datasets", "ds-1");
        expected.put("Name", "annotation-1");
        expected.put("Related Annotations", "ann-2");
        expected.put("Description", "a description");
        expected.put("Tags", "alpha");
        expected.put("Attributes", "key-1=value-1");
        expected.put("Calculations", AnnotationInfoTableRow.CALCULATIONS_PRESENT_LABEL);
        return expected;
    }

    private static AnnotationInfoTableRow sampleRow() {
        return new AnnotationInfoTableRow(Annotation.newBuilder()
                .setId("ann-1")
                .setOwnerId("owner-1")
                .setName("annotation-1")
                .setDescription("a description")
                .addTags("alpha")
                .addAttributes(com.ospreydcs.dp.grpc.v1.common.Attribute.newBuilder()
                        .setName("key-1")
                        .setValue("value-1")
                        .build())
                .addDataSetIds("ds-1")
                .addAnnotationIds("ann-2")
                .setCalculationsId("calc-1")
                .build());
    }

    @Test
    public void everyResultsTableColumnRendersItsValue() throws Exception {
        final URL fxmlUrl = getClass().getResource(FXML_PATH);
        assertNotNull(fxmlUrl, "FXML resource not found on classpath: " + FXML_PATH);

        final Map<String, String> expected = expectedCellValues();

        FxToolkitSupport.runOnFxThread(() -> {
            // load the real view, so the columns under test are the ones the controller's
            // initialize() -> setupTableColumns() actually configured
            final FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.load();
            assertNotNull(loader.getController(), "no controller instantiated for " + FXML_PATH);

            final TableView<AnnotationInfoTableRow> resultsTable = findResultsTable(loader);
            final AnnotationInfoTableRow row = sampleRow();

            for (TableColumn<AnnotationInfoTableRow, ?> column : resultsTable.getColumns()) {
                final String header = column.getText();

                assertTrue(expected.containsKey(header),
                        "results table has an unexpected column \"" + header + "\" -- add it to "
                                + "this test's expected values so its binding is guarded too");

                assertNotNull(column.getCellValueFactory(),
                        "column \"" + header + "\" has no cellValueFactory, so it renders blank -- "
                                + "AnnotationExploreController.setupTableColumns() does not bind it");

                assertEquals(expected.get(header), column.getCellData(row),
                        "column \"" + header + "\" renders the wrong value, so its binding in "
                                + "AnnotationExploreController.setupTableColumns() names the wrong "
                                + "AnnotationInfoTableRow property");
            }

            // a column dropped from the FXML would otherwise pass the loop above vacuously
            assertEquals(expected.size(), resultsTable.getColumns().size(),
                    "results table column count changed; expected columns: " + expected.keySet());
        });
    }

    @SuppressWarnings("unchecked")
    private static TableView<AnnotationInfoTableRow> findResultsTable(FXMLLoader loader) {
        final Object resultsTable = loader.getNamespace().get("resultsTable");
        assertNotNull(resultsTable, "no fx:id \"resultsTable\" in " + FXML_PATH);
        assertTrue(resultsTable instanceof TableView,
                "fx:id \"resultsTable\" is not a TableView");
        return (TableView<AnnotationInfoTableRow>) resultsTable;
    }
}
