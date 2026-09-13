package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.common.Attribute;
import com.ospreydcs.dp.grpc.v1.common.Configuration;
import com.ospreydcs.dp.grpc.v1.common.ConfigurationActivation;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.gui.model.ConfigurationActivationTableRow;
import com.ospreydcs.dp.gui.model.ConfigurationTableRow;
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
 * Asserts that both result tables in the configuration explore view, as wired by the REAL controller
 * against the REAL FXML, render each column's value.
 *
 * The column bindings are PropertyValueFactory strings resolved reflectively at render time, so a
 * name that does not resolve produces a silently blank column rather than an error. Using the rows'
 * PROPERTY_* constants makes a rename a compile error; this covers what constants cannot -- a column
 * bound to the wrong one of them, or not bound at all.
 */
public class ConfigurationExploreColumnBindingTest {

    private static final String FXML_PATH = "/fxml/configuration-explore.fxml";

    private static Map<String, String> expectedConfigurationValues() {
        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("Name", "rf-cavity");
        expected.put("Category", "accelerator");
        expected.put("Parent", "site-root");
        expected.put("Description", "the RF cavity configuration");
        expected.put("Tags", "critical, vacuum");
        expected.put("Attributes", "hall=east, owner=rf-group");
        expected.put("Modified By", "an-operator");
        // "Updated" is asserted separately: its rendering is zone-dependent, so a literal here would
        // fail wherever the suite runs outside the author's timezone.
        return expected;
    }

    private static Map<String, String> expectedActivationValues() {
        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("Activation ID", "evt-1");
        expected.put("Configuration", "rf-cavity");
        expected.put("Description", "a maintenance window");
        expected.put("Tags", "maintenance");
        expected.put("Attributes", "shift=night");
        expected.put("Modified By", "an-operator");
        // Start and End are zone-dependent, and End has its own open-ended test below.
        return expected;
    }

    private static ConfigurationTableRow configurationRow() {
        return new ConfigurationTableRow(Configuration.newBuilder()
                .setConfigurationName("rf-cavity")
                .setCategory("accelerator")
                .setParentConfigurationName("site-root")
                .setDescription("the RF cavity configuration")
                .addTags("critical")
                .addTags("vacuum")
                .addAttributes(Attribute.newBuilder().setName("hall").setValue("east"))
                .addAttributes(Attribute.newBuilder().setName("owner").setValue("rf-group"))
                .setModifiedBy("an-operator")
                .setUpdatedTime(Timestamp.newBuilder().setEpochSeconds(1_700_000_000L))
                .build());
    }

    private static ConfigurationActivationTableRow activationRow() {
        return new ConfigurationActivationTableRow(ConfigurationActivation.newBuilder()
                .setClientActivationId("evt-1")
                .setConfigurationName("rf-cavity")
                .setStartTime(Timestamp.newBuilder().setEpochSeconds(1_700_000_000L))
                .setEndTime(Timestamp.newBuilder().setEpochSeconds(1_700_003_600L))
                .setDescription("a maintenance window")
                .addTags("maintenance")
                .addAttributes(Attribute.newBuilder().setName("shift").setValue("night"))
                .setModifiedBy("an-operator")
                .build());
    }

    @Test
    public void everyConfigurationColumnRendersItsValue() throws Exception {
        final URL fxmlUrl = getClass().getResource(FXML_PATH);
        assertNotNull(fxmlUrl, "FXML resource not found on classpath: " + FXML_PATH);

        final Map<String, String> expected = expectedConfigurationValues();

        FxToolkitSupport.runOnFxThread(() -> {
            final FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.load();
            assertNotNull(loader.getController(), "no controller instantiated for " + FXML_PATH);

            final TableView<ConfigurationTableRow> table =
                    findTable(loader, "configurationResultsTable");
            final ConfigurationTableRow row = configurationRow();

            for (TableColumn<ConfigurationTableRow, ?> column : table.getColumns()) {
                final String header = column.getText();

                assertNotNull(column.getCellValueFactory(),
                        "column \"" + header + "\" has no cellValueFactory, so it renders blank -- "
                                + "setupConfigurationColumns() does not bind it");

                if ("Updated".equals(header)) {
                    final Object cellData = column.getCellData(row);
                    assertNotNull(cellData, "the Updated column binding does not resolve");
                    assertTrue(cellData.toString().startsWith("20"),
                            "the Updated column should render a formatted date, but was: " + cellData);
                    continue;
                }

                assertTrue(expected.containsKey(header),
                        "configurations table has an unexpected column \"" + header
                                + "\" -- add it to this test's expected values");

                assertEquals(expected.get(header), column.getCellData(row),
                        "column \"" + header + "\" renders the wrong value, so its binding in "
                                + "setupConfigurationColumns() names the wrong ConfigurationTableRow "
                                + "property");
            }

            assertEquals(expected.size() + 1, table.getColumns().size(),
                    "configurations table column count changed; expected: " + expected.keySet()
                            + " plus Updated");
        });
    }

    @Test
    public void everyActivationColumnRendersItsValue() throws Exception {
        final URL fxmlUrl = getClass().getResource(FXML_PATH);
        assertNotNull(fxmlUrl, "FXML resource not found on classpath: " + FXML_PATH);

        final Map<String, String> expected = expectedActivationValues();

        FxToolkitSupport.runOnFxThread(() -> {
            final FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.load();

            final TableView<ConfigurationActivationTableRow> table =
                    findTable(loader, "activationResultsTable");
            final ConfigurationActivationTableRow row = activationRow();

            for (TableColumn<ConfigurationActivationTableRow, ?> column : table.getColumns()) {
                final String header = column.getText();

                assertNotNull(column.getCellValueFactory(),
                        "column \"" + header + "\" has no cellValueFactory, so it renders blank -- "
                                + "setupActivationColumns() does not bind it");

                if ("Start".equals(header) || "End".equals(header)) {
                    final Object cellData = column.getCellData(row);
                    assertNotNull(cellData, "the " + header + " column binding does not resolve");
                    assertTrue(cellData.toString().startsWith("20"),
                            "the " + header + " column should render a formatted date, but was: "
                                    + cellData);
                    continue;
                }

                assertTrue(expected.containsKey(header),
                        "activations table has an unexpected column \"" + header
                                + "\" -- add it to this test's expected values");

                assertEquals(expected.get(header), column.getCellData(row),
                        "column \"" + header + "\" renders the wrong value, so its binding in "
                                + "setupActivationColumns() names the wrong "
                                + "ConfigurationActivationTableRow property");
            }

            assertEquals(expected.size() + 2, table.getColumns().size(),
                    "activations table column count changed; expected: " + expected.keySet()
                            + " plus Start and End");
        });
    }

    /**
     * An absent endTime is an OPEN-ENDED interval, not epoch. Reading it without checking presence
     * yields a zero-valued Timestamp, which renders as a 1970 date -- an activation that appears to
     * have ended before it began.
     */
    @Test
    public void anAbsentEndTimeRendersAsOpenEndedRatherThanEpoch() throws Exception {
        final URL fxmlUrl = getClass().getResource(FXML_PATH);

        final ConfigurationActivationTableRow openEnded = new ConfigurationActivationTableRow(
                ConfigurationActivation.newBuilder()
                        .setClientActivationId("evt-open")
                        .setConfigurationName("rf-cavity")
                        .setStartTime(Timestamp.newBuilder().setEpochSeconds(1_700_000_000L))
                        .build());

        assertEquals(ConfigurationActivationTableRow.OPEN_ENDED, openEnded.getEndTime(),
                "an absent endTime must render as open-ended, never as a 1970 timestamp");
        assertEquals(null, openEnded.getEndInstant(),
                "the unformatted accessor must report absence as null rather than as epoch");

        FxToolkitSupport.runOnFxThread(() -> {
            final FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.load();

            final TableView<ConfigurationActivationTableRow> table =
                    findTable(loader, "activationResultsTable");

            for (TableColumn<ConfigurationActivationTableRow, ?> column : table.getColumns()) {
                if ("End".equals(column.getText())) {
                    assertEquals(ConfigurationActivationTableRow.OPEN_ENDED,
                            column.getCellData(openEnded),
                            "the End column must render open-ended through the real binding too");
                    return;
                }
            }
            throw new AssertionError("no \"End\" column found in the activations table");
        });
    }

    /** A row wrapping no record must render blanks rather than throwing during virtualization. */
    @Test
    public void rowsWithNoRecordRenderBlanks() {
        final ConfigurationTableRow configuration = new ConfigurationTableRow(null);
        assertEquals("", configuration.getConfigurationName());
        assertEquals("", configuration.getTags());

        final ConfigurationActivationTableRow activation = new ConfigurationActivationTableRow(null);
        assertEquals("", activation.getClientActivationId());
        assertEquals(null, activation.getStartInstant());
        assertEquals(null, activation.getEndInstant());
    }

    @SuppressWarnings("unchecked")
    private static <T> TableView<T> findTable(FXMLLoader loader, String fxId) {
        final Object table = loader.getNamespace().get(fxId);
        assertNotNull(table, "no fx:id \"" + fxId + "\" in " + FXML_PATH);
        assertTrue(table instanceof TableView,
                "fx:id \"" + fxId + "\" is not a TableView in " + FXML_PATH);
        return (TableView<T>) table;
    }
}
