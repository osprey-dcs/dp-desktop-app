package com.ospreydcs.dp.gui.component;

import com.ospreydcs.dp.client.criteria.AttributeCriterion;
import com.ospreydcs.dp.client.criteria.TextMatch;
import com.ospreydcs.dp.gui.PvMetadataExploreViewModel;
import com.ospreydcs.dp.gui.PvMetadataExploreViewModel.MatchMode;
import com.ospreydcs.dp.gui.model.PvSelection;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Modal editor for the Query Editor's PV selection — the three arms of the Query API V2
 * {@code PvSelector}.
 *
 * <p><strong>The dialog edits a selection and returns a new one; it never writes into the Query
 * Editor's PV name list.</strong>  That list is shared global state (the ListView, DpApplication's
 * PV names, the Dataset Builder, PV Explore, Provider Explore, the data-event hyperlink), and
 * writing a pattern's or a metadata query's resolved PVs into it would make a query-scoped choice
 * silently rewrite state five other flows read.  The name-list mode therefore describes that list
 * without owning it, and the other two modes leave it alone entirely.
 *
 * <p>The summary and warning update on every keystroke rather than on accept.  The case that needs
 * it is the metadata mode: an all-empty metadata query is <em>not</em> rejected by the server — it
 * matches every PV in the archive — so a selection that reads as a filter can quietly be a
 * whole-archive scan.  Showing what will actually be covered, while the fields are still being
 * edited, is what makes that visible before it becomes a rejected or enormous query.
 */
public class PvSelectorDialogController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    @FXML private ToggleGroup modeToggleGroup;
    @FXML private RadioButton nameListRadio;
    @FXML private RadioButton namePatternRadio;
    @FXML private RadioButton metadataRadio;

    @FXML private VBox nameListPane;
    @FXML private Label nameListCountLabel;

    @FXML private VBox namePatternPane;
    @FXML private TextField patternField;

    @FXML private VBox metadataPane;
    @FXML private ComboBox<MatchMode> pvNameMatchModeCombo;
    @FXML private TextField metadataPvNameField;
    @FXML private ComboBox<MatchMode> aliasMatchModeCombo;
    @FXML private TextField metadataAliasField;
    @FXML private TextField metadataTagsField;
    @FXML private TextField metadataAttributeKeyField;
    @FXML private TextField metadataAttributeValueField;

    @FXML private Label summaryLabel;
    @FXML private Label warningLabel;

    /** The Query Editor's live PV name list, for describing the name-list mode. Never mutated. */
    private List<String> pvNames = List.of();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        pvNameMatchModeCombo.getItems().setAll(MatchMode.values());
        pvNameMatchModeCombo.getSelectionModel().select(MatchMode.CONTAINS);
        aliasMatchModeCombo.getItems().setAll(MatchMode.values());
        aliasMatchModeCombo.getSelectionModel().select(MatchMode.CONTAINS);

        modeToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            updatePaneVisibility();
            updateSummary();
        });

        for (TextInputControl field : List.of(
                patternField, metadataPvNameField, metadataAliasField, metadataTagsField,
                metadataAttributeKeyField, metadataAttributeValueField)) {
            field.textProperty().addListener((ObservableValue<? extends String> obs,
                                              String oldVal, String newVal) -> updateSummary());
        }
        pvNameMatchModeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateSummary());
        aliasMatchModeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateSummary());

        logger.debug("PvSelectorDialogController initialized");
    }

    /**
     * Populates the dialog from the current selection and the Query Editor's PV name list.
     *
     * @param selection the selection to edit; null is treated as the name-list default
     * @param pvNames   the Query Editor's live PV name list, read for display only
     */
    public void setSelection(PvSelection selection, List<String> pvNames) {
        this.pvNames = pvNames == null ? List.of() : List.copyOf(pvNames);

        final PvSelection current = selection == null ? PvSelection.nameList() : selection;

        switch (current.getMode()) {
            case NAME_LIST -> nameListRadio.setSelected(true);
            case NAME_PATTERN -> {
                namePatternRadio.setSelected(true);
                patternField.setText(current.getNamePattern() == null ? "" : current.getNamePattern());
            }
            case METADATA -> {
                metadataRadio.setSelected(true);
                applyTextMatch(current.getMetadataPvName(), pvNameMatchModeCombo, metadataPvNameField);
                applyTextMatch(current.getMetadataAliases(), aliasMatchModeCombo, metadataAliasField);
                metadataTagsField.setText(String.join(", ", current.getMetadataTagsAnyOf()));
                if (!current.getMetadataAttributes().isEmpty()) {
                    final AttributeCriterion attribute = current.getMetadataAttributes().get(0);
                    metadataAttributeKeyField.setText(attribute.key());
                    final List<String> values = attribute.values();
                    metadataAttributeValueField.setText(
                            values == null || values.isEmpty() ? "" : String.join(", ", values));
                }
            }
        }

        nameListCountLabel.setText(this.pvNames.isEmpty()
                ? "The Query Editor's PV list is empty — add at least one PV before querying."
                : this.pvNames.size() + " PV(s) currently listed: " + String.join(", ", this.pvNames));

        updatePaneVisibility();
        updateSummary();
    }

    /**
     * Reads back the edited selection.
     *
     * <p>Each mode reads only its own controls.  Text left behind in the pattern field by a
     * previous visit to that mode is therefore ignored while metadata is selected, rather than
     * being carried into a selection the user did not choose — the same reason the machine
     * configuration view clears its spinners rather than reusing whatever time they happen to hold.
     */
    public PvSelection getSelection() {
        if (namePatternRadio.isSelected()) {
            return PvSelection.namePattern(textOf(patternField));
        }
        if (metadataRadio.isSelected()) {
            return PvSelection.metadata(
                    PvMetadataExploreViewModel.textMatch(textOf(metadataPvNameField),
                            pvNameMatchModeCombo.getValue()),
                    PvMetadataExploreViewModel.textMatch(textOf(metadataAliasField),
                            aliasMatchModeCombo.getValue()),
                    PvMetadataExploreViewModel.parseCommaSeparatedList(textOf(metadataTagsField)),
                    attributeCriteria());
        }
        return PvSelection.nameList();
    }

    @FXML
    private void onMatchAll() {
        patternField.setText(PvSelection.MATCH_ALL_PATTERN);
    }

    private void updatePaneVisibility() {
        setShown(nameListPane, nameListRadio.isSelected());
        setShown(namePatternPane, namePatternRadio.isSelected());
        setShown(metadataPane, metadataRadio.isSelected());
    }

    /**
     * Hides a pane for layout as well as for painting.  Setting only {@code visible} leaves the
     * pane occupying its space, so the dialog would keep a gap the size of the metadata grid
     * whichever mode was chosen.
     */
    private static void setShown(VBox pane, boolean shown) {
        pane.setVisible(shown);
        pane.setManaged(shown);
    }

    private void updateSummary() {
        final PvSelection selection = getSelection();
        summaryLabel.setText(selection.describe(pvNames));

        final String warning = warningFor(selection);
        warningLabel.setText(warning == null ? "" : warning);
        warningLabel.setVisible(warning != null);
        warningLabel.setManaged(warning != null);
    }

    /**
     * The message shown beneath the summary, or null when there is nothing to warn about.
     *
     * <p>Both cases are selections the server accepts.  A blank pattern is the exception it does
     * reject, and it is called out here because the Submit button is disabled for it, which would
     * otherwise look like the dialog rather than the field.
     */
    /**
     * Whether the dialog may be applied.
     *
     * <p>Only the blank pattern is refused, and it is the one arm the SERVER rejects. The metadata
     * mode deliberately has no rule — an all-empty metadata query is valid and matches every PV in
     * the archive — so refusing it here would invent a rule the server does not have; its warning
     * says what it will cover instead of blocking it.
     */
    public boolean isAcceptable() {
        final PvSelection selection = getSelection();
        if (selection.getMode() != PvSelection.Mode.NAME_PATTERN) {
            return true;
        }
        final String pattern = selection.getNamePattern();
        return pattern != null && !pattern.isBlank();
    }

    private static String warningFor(PvSelection selection) {
        return switch (selection.getMode()) {
            case NAME_PATTERN -> {
                final String pattern = selection.getNamePattern();
                if (pattern == null || pattern.isBlank()) {
                    yield "Enter a pattern — the server rejects a blank one.";
                }
                yield PvSelection.MATCH_ALL_PATTERN.equals(pattern)
                        ? "This selects every PV in the archive, and will be rejected if that "
                                + "resolves past the server's limit."
                        : null;
            }
            case METADATA -> selection.getMetadataPvName().isEmpty()
                    && selection.getMetadataAliases().isEmpty()
                    && selection.getMetadataTagsAnyOf().isEmpty()
                    && selection.getMetadataAttributes().isEmpty()
                    ? "No criteria entered — this matches every PV in the archive rather than "
                            + "nothing, and will be rejected if that resolves past the server's limit."
                    : null;
            case NAME_LIST -> null;
        };
    }

    private void applyTextMatch(TextMatch match, ComboBox<MatchMode> modeCombo, TextField field) {
        if (match == null || match.isEmpty()) {
            return;
        }
        // A TextMatch carries its mode in WHICH list is populated, so the mode is recovered from
        // the populated one rather than stored alongside it.
        if (match.exact() != null && !match.exact().isEmpty()) {
            modeCombo.getSelectionModel().select(MatchMode.EXACT);
            field.setText(String.join(", ", match.exact()));
        } else if (match.prefix() != null && !match.prefix().isEmpty()) {
            modeCombo.getSelectionModel().select(MatchMode.PREFIX);
            field.setText(String.join(", ", match.prefix()));
        } else if (match.contains() != null && !match.contains().isEmpty()) {
            modeCombo.getSelectionModel().select(MatchMode.CONTAINS);
            field.setText(String.join(", ", match.contains()));
        }
    }

    /**
     * An attribute criterion needs its key; a value alone cannot be expressed by the proto and is
     * dropped rather than promoted to a key.  A key with no value is a legitimate key-only
     * existence search.
     */
    private List<AttributeCriterion> attributeCriteria() {
        final String key = textOf(metadataAttributeKeyField);
        if (key.isBlank()) {
            return List.of();
        }
        final List<String> values = PvMetadataExploreViewModel.parseCommaSeparatedList(textOf(metadataAttributeValueField));
        return List.of(new AttributeCriterion(key.trim(), values.isEmpty() ? null : values));
    }

    private static String textOf(TextField field) {
        return field.getText() == null ? "" : field.getText();
    }

    /**
     * Shows the dialog and returns the edited selection, or null if it was cancelled.
     *
     * @param current    the selection to edit
     * @param pvNames    the Query Editor's live PV name list, read for display only
     * @param ownerStage owner for modality, may be null
     */
    public static PvSelection showDialog(PvSelection current, List<String> pvNames, Stage ownerStage) {
        try {
            final Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Select PVs");
            dialog.setResizable(true);

            final javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    PvSelectorDialogController.class.getResource(
                            "/fxml/components/pv-selector-dialog.fxml"));

            final VBox content = loader.load();
            final PvSelectorDialogController controller = loader.getController();
            controller.setSelection(current, pvNames);

            dialog.getDialogPane().setContent(content);
            final ButtonType applyButton = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(applyButton, ButtonType.CANCEL);

            // Apply is disabled while the selection cannot be sent, matching the query filters
            // dialog.  Without this the warning said a blank pattern would be rejected while Apply
            // stayed enabled, so the selection was accepted here and failed at the SERVER on the
            // next submit -- the warning naming a rule that nothing enforced.
            final javafx.scene.Node applyNode = dialog.getDialogPane().lookupButton(applyButton);
            applyNode.setDisable(!controller.isAcceptable());
            // The summary is rewritten on every edit in every mode, so it is the one signal that
            // always fires; the warning is watched too for the edit that only changes the warning.
            controller.summaryLabel.textProperty().addListener(
                    (obs, oldVal, newVal) -> applyNode.setDisable(!controller.isAcceptable()));
            controller.warningLabel.textProperty().addListener(
                    (obs, oldVal, newVal) -> applyNode.setDisable(!controller.isAcceptable()));
            controller.modeToggleGroup.selectedToggleProperty().addListener(
                    (obs, oldVal, newVal) -> applyNode.setDisable(!controller.isAcceptable()));

            // Re-fit the window when the mode changes.  Switching modes swaps which detail pane is
            // MANAGED, which changes the content's preferred height -- but a Dialog sizes itself
            // once, when first shown, so it keeps the height it was laid out for.  Name list is by
            // far the smallest mode and is the default, so opening there and switching to metadata
            // grew the content past the bottom of the window and pushed APPLY OFF SCREEN.  The
            // dialog is resizable, so the user could drag it larger -- which is precisely why this
            // presented as a confusing feature rather than as an obvious bug: the button was
            // reachable, just invisible until you thought to resize.
            DialogSizing.resizeToFitOnModeChange(dialog, controller.modeToggleGroup);

            if (ownerStage != null) {
                dialog.initOwner(ownerStage);
            }

            final ButtonType result = dialog.showAndWait().orElse(ButtonType.CANCEL);
            if (result != applyButton) {
                logger.debug("PV selector dialog cancelled");
                return null;
            }

            final PvSelection selection = controller.getSelection();
            logger.info("PV selection applied: {}", selection.describe(pvNames));
            return selection;

        } catch (Exception e) {
            logger.error("Failed to show PV selector dialog", e);
            return null;
        }
    }
}
