package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.common.DataTimestamps;
import com.ospreydcs.dp.grpc.v1.common.SampleStatusBucket;
import com.ospreydcs.dp.grpc.v1.common.SampleStatusColumn;
import com.ospreydcs.dp.grpc.v1.common.SamplingClock;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.gui.model.SampleStatusTableRow;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that the sample status results table, as wired by the REAL controller against the REAL
 * FXML, renders each column's value.
 *
 * The column bindings are PropertyValueFactory strings resolved reflectively at render time, so a
 * name that does not resolve produces a silently blank column rather than an error.
 * ViewLoadSmokeTest does not catch it — it proves initialize() ran, never that a row renders. Using
 * the row's PROPERTY_* constants makes a rename a compile error; this covers what constants cannot,
 * namely a column bound to the wrong one of them or not bound at all.
 *
 * Modelled on AnnotationExploreColumnBindingTest, for the same reasons documented there.
 */
public class SampleStatusExploreColumnBindingTest {

    private static final String FXML_PATH = "/fxml/sample-status-explore.fxml";

    /** Keyed by header text, which is what identifies a column to the user. */
    private static Map<String, String> expectedCellValues() {
        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("PV Name", "pv-42");
        expected.put("Domain", DpApplication.SAMPLE_STATUS_DEMO_DOMAIN);
        expected.put("Layer", "demo_generator");
        expected.put("Code", "2");
        expected.put("Label", "MAJOR_ALARM");
        expected.put("Confidence", "0.75");
        expected.put("Reason", "out of range");
        expected.put("Source", "a-source");
        expected.put("Modified By", "a-user");
        // Timestamp is asserted separately: its rendering is zone-dependent, so a literal here
        // would fail wherever the suite runs outside the author's timezone.
        return expected;
    }

    private static SampleStatusTableRow sampleRow() {
        final SampleStatusBucket bucket = SampleStatusBucket.newBuilder()
                .setDomain(DpApplication.SAMPLE_STATUS_DEMO_DOMAIN)
                .setLayer("demo_generator")
                .setDataTimestamps(DataTimestamps.newBuilder()
                        .setSamplingClock(SamplingClock.newBuilder()
                                .setStartTime(Timestamp.newBuilder().setEpochSeconds(1_700_000_000L))
                                .setPeriodNanos(1_000_000_000L)
                                .setCount(1)))
                .setStatusColumn(SampleStatusColumn.newBuilder()
                        .setPvName("pv-42")
                        .addStatusCodes(DpApplication.EPICS_ALARM_MAJOR_ALARM)
                        .addConfidence(0.75f)
                        .addReasons("out of range"))
                .setSource("a-source")
                .setModifiedBy("a-user")
                .build();

        final List<SampleStatusTableRow> rows = SampleStatusTableRow.expand(
                bucket, null, null, SampleStatusExploreViewModel.CODE_LABELS);
        assertEquals(1, rows.size(), "fixture should expand to exactly one row");
        return rows.get(0);
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

            final TableView<SampleStatusTableRow> resultsTable = findResultsTable(loader);
            final SampleStatusTableRow row = sampleRow();

            for (TableColumn<SampleStatusTableRow, ?> column : resultsTable.getColumns()) {
                final String header = column.getText();

                assertNotNull(column.getCellValueFactory(),
                        "column \"" + header + "\" has no cellValueFactory, so it renders blank -- "
                                + "SampleStatusExploreController.setupTableColumns() does not bind it");

                if ("Timestamp".equals(header)) {
                    // rendering is zone-dependent; assert it resolved to something rather than a
                    // literal that would fail outside the author's timezone
                    final Object cellData = column.getCellData(row);
                    assertNotNull(cellData, "the Timestamp column binding does not resolve");
                    assertTrue(cellData.toString().startsWith("20"),
                            "the Timestamp column should render a formatted date, but was: " + cellData);
                    continue;
                }

                assertTrue(expected.containsKey(header),
                        "results table has an unexpected column \"" + header + "\" -- add it to "
                                + "this test's expected values so its binding is guarded too");

                assertEquals(expected.get(header), column.getCellData(row),
                        "column \"" + header + "\" renders the wrong value, so its binding in "
                                + "SampleStatusExploreController.setupTableColumns() names the wrong "
                                + "SampleStatusTableRow property");
            }

            // a column dropped from the FXML would otherwise pass the loop above vacuously
            assertEquals(expected.size() + 1, resultsTable.getColumns().size(),
                    "results table column count changed; expected columns: " + expected.keySet()
                            + " plus Timestamp");
        });
    }

    @SuppressWarnings("unchecked")
    private static TableView<SampleStatusTableRow> findResultsTable(FXMLLoader loader) {
        final Object resultsTable = loader.getNamespace().get("resultsTable");
        assertNotNull(resultsTable, "no fx:id \"resultsTable\" in " + FXML_PATH);
        assertTrue(resultsTable instanceof TableView,
                "fx:id \"resultsTable\" is not a TableView in " + FXML_PATH);
        return (TableView<SampleStatusTableRow>) resultsTable;
    }
}
