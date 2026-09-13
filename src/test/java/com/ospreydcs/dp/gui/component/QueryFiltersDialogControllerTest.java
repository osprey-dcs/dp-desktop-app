package com.ospreydcs.dp.gui.component;

import com.ospreydcs.dp.gui.model.ConfigurationFilter;
import com.ospreydcs.dp.gui.model.SampleStatusFilter;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the query filters modal by loading its real FXML and driving its real controls.
 *
 * <p>What is worth pinning here is that an unticked checkbox really drops its filter regardless of
 * what the fields still hold, and that the two cases which are accepted-but-wrong downstream -- a
 * status filter with no domain, and an unparseable status code -- are refused before they can be
 * applied rather than after the server has rejected or silently widened the query.
 */
class QueryFiltersDialogControllerTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        FxToolkitSupport.ensureStarted();
    }

    private record LoadedDialog(QueryFiltersDialogController controller, VBox root) {

        <T> T lookup(String fxId, Class<T> type) {
            return type.cast(root.lookup("#" + fxId));
        }

        TextField field(String fxId) {
            return lookup(fxId, TextField.class);
        }

        CheckBox check(String fxId) {
            return lookup(fxId, CheckBox.class);
        }

        Label label(String fxId) {
            return lookup(fxId, Label.class);
        }
    }

    private static LoadedDialog load(ConfigurationFilter configuration, SampleStatusFilter status)
            throws Exception {
        final FXMLLoader loader = new FXMLLoader(QueryFiltersDialogController.class.getResource(
                "/fxml/components/query-filters-dialog.fxml"));
        final VBox root = loader.load();
        final QueryFiltersDialogController controller = loader.getController();
        controller.setFilters(configuration, status);
        return new LoadedDialog(controller, root);
    }

    private static LoadedDialog loadDefault() throws Exception {
        return load(ConfigurationFilter.none(), SampleStatusFilter.none());
    }

    @Test
    @DisplayName("both filters open unticked when neither is active")
    void opensWithBothFiltersOff() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = loadDefault();

            assertFalse(dialog.check("configurationEnabledCheck").isSelected());
            assertFalse(dialog.check("sampleStatusEnabledCheck").isSelected());
            assertTrue(dialog.controller().isAcceptable(),
                    "two off filters are a complete, applicable state");
        });
    }

    @Test
    @DisplayName("an existing filter's criteria are populated")
    void existingFiltersArePopulated() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = load(
                    ConfigurationFilter.of(List.of("beamline-a"), null, List.of("optics"),
                            null, "subsystem", List.of("vacuum")),
                    SampleStatusFilter.of("epics_alarm", List.of("demo_generator"), List.of(2, 3),
                            SampleStatusFilter.Mode.EXCLUDE_MATCHING));

            assertTrue(dialog.check("configurationEnabledCheck").isSelected());
            assertEquals("beamline-a", dialog.field("configurationNamesField").getText());
            assertEquals("optics", dialog.field("categoriesField").getText());
            assertEquals("subsystem", dialog.field("configurationAttributeKeyField").getText());
            assertEquals("vacuum", dialog.field("configurationAttributeValueField").getText());

            assertTrue(dialog.check("sampleStatusEnabledCheck").isSelected());
            assertEquals("epics_alarm", dialog.field("statusDomainField").getText());
            assertEquals("demo_generator", dialog.field("statusLayersField").getText());
            assertEquals("2, 3", dialog.field("statusCodesField").getText());
            assertEquals(SampleStatusFilter.Mode.EXCLUDE_MATCHING,
                    dialog.lookup("statusModeCombo", ComboBox.class).getValue());
        });
    }

    /**
     * The checkbox, not the fields, decides whether a filter is sent. Criteria left behind by a
     * previous visit must not be carried into a filter the user turned off.
     */
    @Test
    @DisplayName("an unticked checkbox drops its filter regardless of what the fields hold")
    void untickingDropsTheFilterEvenWithPopulatedFields() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = load(
                    ConfigurationFilter.of(List.of("beamline-a"), null, null, null, null, null),
                    SampleStatusFilter.of("epics_alarm", null, List.of(2),
                            SampleStatusFilter.Mode.INCLUDE_MATCHING));

            dialog.check("configurationEnabledCheck").setSelected(false);
            dialog.check("sampleStatusEnabledCheck").setSelected(false);

            assertEquals("beamline-a", dialog.field("configurationNamesField").getText(),
                    "the criteria stay visible so the filter can be turned back on");
            assertFalse(dialog.controller().getConfigurationFilter().isActive(),
                    "an unticked box must drop the filter even with its fields still populated");
            assertNull(dialog.controller().getConfigurationFilter().toCriteria(),
                    "an off configuration filter must send NO selector, not an empty one");
            assertFalse(dialog.controller().getSampleStatusFilter().isActive());
        });
    }

    /**
     * The server rejects a status selector without a domain, so applying one would break an
     * otherwise valid query with a message about the request rather than about the blank field.
     */
    @Test
    @DisplayName("a ticked status filter with no domain is refused")
    void statusFilterWithoutDomainIsRefused() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = loadDefault();

            dialog.check("sampleStatusEnabledCheck").setSelected(true);

            assertFalse(dialog.controller().isAcceptable(),
                    "a status filter with no domain is rejected by the server");
            assertTrue(dialog.label("warningLabel").isVisible(),
                    "the warning must be painted");
            assertTrue(dialog.label("warningLabel").isManaged(),
                    "a visible-but-unmanaged warning takes no space and cannot be read");

            dialog.field("statusDomainField").setText("epics_alarm");

            assertTrue(dialog.controller().isAcceptable());
            assertFalse(dialog.label("warningLabel").isVisible(),
                    "the warning must clear once the domain is entered");
        });
    }

    /**
     * The sharpest edge in this dialog. A dropped status code silently WIDENS the filter -- in
     * INCLUDE mode "samples with code 2" becomes "samples labeled at all" -- and returns a
     * plausible table, so nothing downstream would say the typed code never reached the server.
     */
    @Test
    @DisplayName("an unparseable status code is refused, not dropped")
    void unparseableStatusCodeIsRefused() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = loadDefault();

            dialog.check("sampleStatusEnabledCheck").setSelected(true);
            dialog.field("statusDomainField").setText("epics_alarm");
            dialog.field("statusCodesField").setText("2, major");

            assertFalse(dialog.controller().isAcceptable(),
                    "a code that does not parse must block the dialog rather than being dropped "
                            + "into a broader filter that returns a plausible result");
            assertTrue(dialog.label("warningLabel").isVisible());

            dialog.field("statusCodesField").setText("2, 3");

            assertTrue(dialog.controller().isAcceptable());
            assertEquals(List.of(2, 3),
                    dialog.controller().getSampleStatusFilter().getStatusCodes());
        });
    }

    @Test
    @DisplayName("parseStatusCodes returns null on a bad entry rather than a partial list")
    void parseStatusCodesRejectsRatherThanTruncates() {
        assertEquals(List.of(), QueryFiltersDialogController.parseStatusCodes(""));
        assertEquals(List.of(2, 3), QueryFiltersDialogController.parseStatusCodes("2, 3"));
        assertNull(QueryFiltersDialogController.parseStatusCodes("2, major"),
                "a partial list would silently widen the filter");
        assertNull(QueryFiltersDialogController.parseStatusCodes("1.5"),
                "a status code is an integer; 1.5 must not truncate to 1");
    }

    /**
     * A ticked configuration filter with no criteria is the empty-selector case the server rejects,
     * and it is NOT the same as the box being unticked.
     */
    @Test
    @DisplayName("a ticked configuration filter with no criteria is refused")
    void tickedConfigurationFilterWithNoCriteriaIsRefused() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = loadDefault();

            dialog.check("configurationEnabledCheck").setSelected(true);

            assertFalse(dialog.controller().isAcceptable(),
                    "an empty criteria list is rejected by the server; the box must be unticked "
                            + "instead to mean 'no restriction'");

            dialog.field("categoriesField").setText("optics");

            assertTrue(dialog.controller().isAcceptable());
        });
    }

    @Test
    @DisplayName("each filter's pane is disabled while its box is unticked")
    void panesFollowTheirCheckboxes() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = loadDefault();

            assertTrue(dialog.lookup("configurationPane", GridPane.class).isDisabled());
            assertTrue(dialog.lookup("sampleStatusPane", GridPane.class).isDisabled());

            dialog.check("configurationEnabledCheck").setSelected(true);

            assertFalse(dialog.lookup("configurationPane", GridPane.class).isDisabled());
            assertTrue(dialog.lookup("sampleStatusPane", GridPane.class).isDisabled(),
                    "ticking one filter must not enable the other");
        });
    }

    @Test
    @DisplayName("the summary updates while the criteria are being typed")
    void summaryUpdatesWhileTyping() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = loadDefault();

            final String before = dialog.label("summaryLabel").getText();

            dialog.check("configurationEnabledCheck").setSelected(true);
            dialog.field("configurationNamesField").setText("beamline-a");

            final String after = dialog.label("summaryLabel").getText();

            org.junit.jupiter.api.Assertions.assertNotEquals(before, after,
                    "the summary must track the fields, not wait for accept");
            assertTrue(after.contains("beamline-a"), after);
        });
    }

    /** An attribute value with no key cannot be expressed by the proto and is dropped. */
    @Test
    @DisplayName("a configuration attribute value with no key is dropped")
    void attributeValueWithoutKeyIsDropped() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = loadDefault();

            dialog.check("configurationEnabledCheck").setSelected(true);
            dialog.field("configurationAttributeValueField").setText("orphan");

            assertFalse(dialog.controller().getConfigurationFilter().isActive(),
                    "a value with no key cannot be expressed and must not make the filter active");
            assertFalse(dialog.controller().isAcceptable(),
                    "and the dialog must say so rather than applying an empty selector");
        });
    }

    /**
     * Blank layers and codes are the common case, and both mean "everything in scope" -- they must
     * not become empty criteria the server reads differently.
     */
    @Test
    @DisplayName("blank optional status fields contribute nothing")
    void blankOptionalStatusFieldsContributeNothing() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = loadDefault();

            dialog.check("sampleStatusEnabledCheck").setSelected(true);
            dialog.field("statusDomainField").setText("epics_alarm");

            final SampleStatusFilter filter = dialog.controller().getSampleStatusFilter();

            assertTrue(filter.isActive());
            assertTrue(filter.getLayers().isEmpty());
            assertTrue(filter.getStatusCodes().isEmpty());
            assertNull(filter.toSelectorParams().layers(),
                    "blank layers must reach the request as absent, meaning every layer");
            assertNull(filter.toSelectorParams().statusCodes(),
                    "blank codes must reach the request as absent, meaning any code");
        });
    }
}
