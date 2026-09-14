package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.common.Attribute;
import com.ospreydcs.dp.grpc.v1.common.PvMetadata;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.gui.model.PvMetadataTableRow;
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
 * Asserts that the PV metadata results table, as wired by the REAL controller against the REAL
 * FXML, renders each column's value.
 *
 * The column bindings are PropertyValueFactory strings resolved reflectively at render time, so a
 * name that does not resolve produces a silently blank column rather than an error.
 * ViewLoadSmokeTest does not catch it -- it proves initialize() ran, never that a row renders.
 * Using the row's PROPERTY_* constants makes a rename a compile error; this covers what constants
 * cannot, namely a column bound to the wrong one of them or not bound at all.
 *
 * Modelled on SampleStatusExploreColumnBindingTest and AnnotationExploreColumnBindingTest, for the
 * same reasons documented there.
 */
public class PvMetadataExploreColumnBindingTest {

    private static final String FXML_PATH = "/fxml/pv-metadata-explore.fxml";

    /** Keyed by header text, which is what identifies a column to the user. */
    private static Map<String, String> expectedCellValues() {
        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("PV Name", "CANONICAL:NAME");
        expected.put("Aliases", "OLD:NAME, LEGACY:NAME");
        expected.put("Tags", "critical, vacuum");
        expected.put("Attributes", "unit=volts, system=rf");
        expected.put("Description", "a described PV");
        expected.put("Modified By", "an-operator");
        // "Updated" is asserted separately: its rendering is zone-dependent, so a literal here
        // would fail wherever the suite runs outside the author's timezone.
        return expected;
    }

    private static PvMetadataTableRow sampleRow() {
        return new PvMetadataTableRow(PvMetadata.newBuilder()
                .setPvName("CANONICAL:NAME")
                .addAliases("OLD:NAME")
                .addAliases("LEGACY:NAME")
                .addTags("critical")
                .addTags("vacuum")
                .addAttributes(Attribute.newBuilder().setName("unit").setValue("volts"))
                .addAttributes(Attribute.newBuilder().setName("system").setValue("rf"))
                .setDescription("a described PV")
                .setModifiedBy("an-operator")
                .setUpdatedTime(Timestamp.newBuilder().setEpochSeconds(1_700_000_000L))
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

            final TableView<PvMetadataTableRow> resultsTable = findResultsTable(loader);
            final PvMetadataTableRow row = sampleRow();

            for (TableColumn<PvMetadataTableRow, ?> column : resultsTable.getColumns()) {
                final String header = column.getText();

                assertNotNull(column.getCellValueFactory(),
                        "column \"" + header + "\" has no cellValueFactory, so it renders blank -- "
                                + "PvMetadataExploreController.setupTableColumns() does not bind it");

                if ("Updated".equals(header)) {
                    // rendering is zone-dependent; assert it resolved to something rather than a
                    // literal that would fail outside the author's timezone
                    final Object cellData = column.getCellData(row);
                    assertNotNull(cellData, "the Updated column binding does not resolve");
                    assertTrue(cellData.toString().startsWith("20"),
                            "the Updated column should render a formatted date, but was: " + cellData);
                    continue;
                }

                assertTrue(expected.containsKey(header),
                        "results table has an unexpected column \"" + header + "\" -- add it to "
                                + "this test's expected values so its binding is guarded too");

                assertEquals(expected.get(header), column.getCellData(row),
                        "column \"" + header + "\" renders the wrong value, so its binding in "
                                + "PvMetadataExploreController.setupTableColumns() names the wrong "
                                + "PvMetadataTableRow property");
            }

            // a column dropped from the FXML would otherwise pass the loop above vacuously
            assertEquals(expected.size() + 1, resultsTable.getColumns().size(),
                    "results table column count changed; expected columns: " + expected.keySet()
                            + " plus Updated");
        });
    }

    /**
     * The PV Name and Aliases columns are rendered by HyperlinkListTableCell, which resolves its
     * links from the row's list accessors rather than from the joined display string.  A row whose
     * aliases contain a comma would otherwise split into bogus links, each mislabelled and each
     * navigating to a name that does not exist.
     */
    @Test
    public void theAliasesListAccessorIsIndependentOfTheDisplayString() {
        final PvMetadataTableRow row = sampleRow();

        assertEquals(2, row.getAliasesList().size(),
                "the link source must be the record's alias list, not the rendered string");
        assertEquals("OLD:NAME", row.getAliasesList().get(0));
        assertEquals("LEGACY:NAME", row.getAliasesList().get(1));

        // a value containing the join separator is exactly what re-splitting the display string
        // would get wrong
        final PvMetadataTableRow commaBearing = new PvMetadataTableRow(PvMetadata.newBuilder()
                .setPvName("pv-1")
                .addAliases("legacy, provisional")
                .build());

        assertEquals(1, commaBearing.getAliasesList().size(),
                "one alias containing a comma is still ONE alias -- splitting the display string "
                        + "would produce two links, neither of which names a real alias");
    }

    /** A row wrapping no record must render blanks rather than throwing during virtualization. */
    @Test
    public void aRowWithNoRecordRendersBlanks() {
        final PvMetadataTableRow row = new PvMetadataTableRow(null);

        assertEquals("", row.getPvName());
        assertEquals("", row.getAliases());
        assertTrue(row.getAliasesList().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static TableView<PvMetadataTableRow> findResultsTable(FXMLLoader loader) {
        final Object resultsTable = loader.getNamespace().get("resultsTable");
        assertNotNull(resultsTable, "no fx:id \"resultsTable\" in " + FXML_PATH);
        assertTrue(resultsTable instanceof TableView,
                "fx:id \"resultsTable\" is not a TableView in " + FXML_PATH);
        return (TableView<PvMetadataTableRow>) resultsTable;
    }
}
