package com.ospreydcs.dp.gui.component;

import com.ospreydcs.dp.client.criteria.AttributeCriterion;
import com.ospreydcs.dp.client.criteria.TextMatch;
import com.ospreydcs.dp.gui.model.PvSelection;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the PV selector modal by loading its real FXML and driving its real controls.
 *
 * <p>Stubbing the controls would defeat the point: the two things worth pinning here are that each
 * mode reads only its OWN controls (so text left behind by a previous visit cannot leak into a
 * selection the user did not choose) and that the live warning fires for the selections the server
 * accepts but that cover far more than they appear to.
 */
class PvSelectorDialogControllerTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        FxToolkitSupport.ensureStarted();
    }

    /** A loaded dialog, with its controller, exactly as showDialog() builds it. */
    private record LoadedDialog(PvSelectorDialogController controller, VBox root) {

        <T> T lookup(String fxId, Class<T> type) {
            return type.cast(root.lookup("#" + fxId));
        }

        TextField field(String fxId) {
            return lookup(fxId, TextField.class);
        }

        RadioButton radio(String fxId) {
            return lookup(fxId, RadioButton.class);
        }

        Label label(String fxId) {
            return lookup(fxId, Label.class);
        }
    }

    private static LoadedDialog load(PvSelection selection, List<String> pvNames) throws Exception {
        final FXMLLoader loader = new FXMLLoader(PvSelectorDialogController.class.getResource(
                "/fxml/components/pv-selector-dialog.fxml"));
        final VBox root = loader.load();
        final PvSelectorDialogController controller = loader.getController();
        controller.setSelection(selection, pvNames);
        return new LoadedDialog(controller, root);
    }

    @Test
    @DisplayName("the dialog opens on the current selection's own mode")
    void opensOnTheCurrentMode() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            assertTrue(load(PvSelection.nameList(), List.of("PV:A"))
                    .radio("nameListRadio").isSelected());
            assertTrue(load(PvSelection.namePattern("^S:.*$"), List.of())
                    .radio("namePatternRadio").isSelected());
            assertTrue(load(PvSelection.metadata(null, null, null, null), List.of())
                    .radio("metadataRadio").isSelected());
        });
    }

    @Test
    @DisplayName("an existing pattern is shown for editing rather than discarded")
    void existingPatternIsPopulated() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = load(PvSelection.namePattern("^S:.*:CURRENT$"), List.of());

            assertEquals("^S:.*:CURRENT$", dialog.field("patternField").getText());
        });
    }

    @Test
    @DisplayName("an existing TextMatch recovers its mode from whichever list is populated")
    void existingTextMatchRecoversItsMode() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            // A TextMatch encodes its mode structurally -- exact/prefix/contains are three separate
            // lists, not a mode field -- so reopening the dialog has to read the mode back out of
            // the shape. Getting this wrong silently downgrades an exact search to a contains one,
            // which returns a superset and looks like it worked.
            final LoadedDialog dialog = load(PvSelection.metadata(
                    new TextMatch(List.of("EXACT:NAME"), null, null),
                    new TextMatch(null, List.of("PREFIX:"), null),
                    List.of(), List.of()), List.of());

            @SuppressWarnings("unchecked")
            final ComboBox<Object> pvNameMode =
                    (ComboBox<Object>) dialog.root().lookup("#pvNameMatchModeCombo");
            @SuppressWarnings("unchecked")
            final ComboBox<Object> aliasMode =
                    (ComboBox<Object>) dialog.root().lookup("#aliasMatchModeCombo");

            assertEquals("EXACT", ((Enum<?>) pvNameMode.getValue()).name());
            assertEquals("PREFIX", ((Enum<?>) aliasMode.getValue()).name());
            assertEquals("EXACT:NAME", dialog.field("metadataPvNameField").getText());
            assertEquals("PREFIX:", dialog.field("metadataAliasField").getText());
        });
    }

    @Test
    @DisplayName("each mode reads only its own controls, so stale text cannot leak into the result")
    void eachModeReadsOnlyItsOwnControls() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = load(PvSelection.namePattern("^LEFTOVER$"), List.of("PV:A"));

            // The user typed a pattern, then changed their mind and chose metadata. The pattern
            // field still holds the text -- switching a radio button does not clear controls -- and
            // reading it regardless would send a pattern query the user abandoned.
            dialog.radio("metadataRadio").setSelected(true);
            dialog.field("metadataTagsField").setText("beamline");

            final PvSelection result = dialog.controller().getSelection();

            assertEquals(PvSelection.Mode.METADATA, result.getMode());
            assertEquals(List.of("beamline"), result.getMetadataTagsAnyOf());

            // and back the other way
            dialog.radio("nameListRadio").setSelected(true);
            assertTrue(dialog.controller().getSelection().isNameList());
        });
    }

    @Test
    @DisplayName("the Match all button states the whole-archive query as an explicit pattern")
    void matchAllSetsTheExplicitPattern() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = load(PvSelection.namePattern(""), List.of());

            dialog.field("patternField").setText(PvSelection.MATCH_ALL_PATTERN);
            final PvSelection result = dialog.controller().getSelection();

            // Stated as a pattern rather than as an empty metadata query, because a pattern of ".*"
            // is visible in the request while an unfilled metadata query is not.
            assertEquals(PvSelection.Mode.NAME_PATTERN, result.getMode());
            assertEquals(".*", result.getNamePattern());
        });
    }

    @Test
    @DisplayName("an empty metadata query WARNS, because the server accepts it as a whole-archive scan")
    void emptyMetadataQueryWarns() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = load(PvSelection.metadata(null, null, null, null), List.of());
            final Label warning = dialog.label("warningLabel");

            assertTrue(warning.isVisible(), "an unfilled metadata query must warn: it is not "
                    + "rejected downstream, so nothing else will say anything");
            assertTrue(warning.isManaged(), "an invisible-but-managed warning leaves a blank gap; "
                    + "a visible-but-unmanaged one takes no space and cannot be read");
            assertTrue(warning.getText().contains("every PV in the archive"), warning.getText());

            // filling in any criterion clears it
            dialog.field("metadataTagsField").setText("beamline");
            assertFalse(warning.isVisible(),
                    "a query with real criteria must not keep warning: " + warning.getText());
        });
    }

    @Test
    @DisplayName("a blank pattern warns that it will be rejected, matching the disabled Submit button")
    void blankPatternWarns() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = load(PvSelection.namePattern(""), List.of("PV:A"));
            final Label warning = dialog.label("warningLabel");

            assertTrue(warning.isVisible());

            dialog.field("patternField").setText("^S:.*$");

            assertFalse(warning.isVisible(),
                    "a real pattern is not a warning case: " + warning.getText());
        });
    }

    @Test
    @DisplayName("the summary updates while typing, not only on accept")
    void summaryUpdatesWhileTyping() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = load(PvSelection.namePattern(""), List.of());
            final Label summary = dialog.label("summaryLabel");

            dialog.field("patternField").setText("^S:.*$");

            assertTrue(summary.getText().contains("^S:.*$"),
                    "the summary is the only place the effective query is spelled out before it "
                            + "runs, so it has to track the fields: " + summary.getText());
        });
    }

    @Test
    @DisplayName("only the selected mode's pane occupies space")
    void onlyTheSelectedPaneIsManaged() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = load(PvSelection.nameList(), List.of("PV:A"));

            assertTrue(dialog.lookup("nameListPane", VBox.class).isManaged());
            assertFalse(dialog.lookup("metadataPane", VBox.class).isManaged(),
                    "an unmanaged-but-hidden pane still reserves its layout space, which for the "
                            + "metadata grid is most of the dialog");

            dialog.radio("metadataRadio").setSelected(true);

            assertTrue(dialog.lookup("metadataPane", VBox.class).isManaged());
            assertFalse(dialog.lookup("nameListPane", VBox.class).isManaged());
        });
    }

    @Test
    @DisplayName("an attribute value with no key is dropped rather than promoted to a key")
    void attributeValueWithoutKeyIsDropped() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = load(PvSelection.metadata(null, null, null, null), List.of());
            dialog.radio("metadataRadio").setSelected(true);

            dialog.field("metadataAttributeValueField").setText("vacuum");

            // AttributeCriterion requires the key; a value alone cannot be expressed. Promoting the
            // value to a key would search a different field and return unrelated PVs.
            assertTrue(dialog.controller().getSelection().getMetadataAttributes().isEmpty());

            dialog.field("metadataAttributeKeyField").setText("subsystem");
            final List<AttributeCriterion> attributes =
                    dialog.controller().getSelection().getMetadataAttributes();

            assertEquals(1, attributes.size());
            assertEquals("subsystem", attributes.get(0).key());
            assertEquals(List.of("vacuum"), attributes.get(0).values());
        });
    }

    @Test
    @DisplayName("a key with no value is a legitimate key-only existence search")
    void keyWithoutValueIsKept() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = load(PvSelection.metadata(null, null, null, null), List.of());
            dialog.radio("metadataRadio").setSelected(true);
            dialog.field("metadataAttributeKeyField").setText("subsystem");

            final List<AttributeCriterion> attributes =
                    dialog.controller().getSelection().getMetadataAttributes();

            assertEquals(1, attributes.size());
            assertEquals(null, attributes.get(0).values(),
                    "an absent value must be null, not an empty list -- the server rejects a "
                            + "criterion whose value list is present but empty");
        });
    }

    @Test
    @DisplayName("blank metadata fields contribute no criterion at all")
    void blankMetadataFieldsContributeNothing() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final LoadedDialog dialog = load(PvSelection.metadata(null, null, null, null), List.of());
            dialog.radio("metadataRadio").setSelected(true);

            dialog.field("metadataPvNameField").setText("   ");
            dialog.field("metadataTagsField").setText(" , , ");

            final PvSelection result = dialog.controller().getSelection();

            // A blank field emitted as an empty-list criterion is rejected by the server; emitted
            // as a blank PREFIX it compiles to a regex matching everything while looking like a
            // filter. Contributing nothing is the only correct option, and it is checked here
            // rather than assumed because the dialog splits its own fields.
            assertTrue(result.getMetadataPvName().isEmpty());
            assertTrue(result.getMetadataTagsAnyOf().isEmpty());
            assertInstanceOf(TextMatch.class, result.getMetadataPvName());
        });
    }
}
