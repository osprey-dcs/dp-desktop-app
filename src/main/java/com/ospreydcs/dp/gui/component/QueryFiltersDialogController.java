package com.ospreydcs.dp.gui.component;

import com.ospreydcs.dp.gui.PvMetadataExploreViewModel;
import com.ospreydcs.dp.gui.model.ConfigurationFilter;
import com.ospreydcs.dp.gui.model.SampleStatusFilter;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Modal editor for the Query Editor's two optional query filters — {@code configurationSelector}
 * and {@code sampleStatusSelector} on the Query API V2 {@code QuerySpec}.
 *
 * <p><strong>One dialog for both, because they are the same kind of thing.</strong>  Each is an
 * optional restriction on a query whose subject is already chosen by the PV selector, each defaults
 * to off, and the two compose by intersection — the configuration filter narrows the time axis
 * first, then the status filter drops samples from what survives.  Splitting them across two modals
 * would hide that relationship behind two buttons and give the Query Editor three separate
 * selection dialogs to keep consistent.
 *
 * <p><strong>Each filter is gated by its own checkbox rather than inferred from its fields.</strong>
 * Inferring would make the two behave oppositely for the same gesture: a configuration filter with
 * every field blank means "no restriction" (the selector is dropped), while a status filter with a
 * blank domain is a REJECTED request rather than an absent one.  An explicit checkbox says which of
 * those the user meant, and lets a filter be turned off without clearing the criteria that would
 * turn it back on.
 *
 * <p><strong>Status codes are refused when they do not parse, never dropped.</strong>  A dropped
 * code silently widens the filter: in INCLUDE mode dropping the only code turns "samples alarming
 * with code 2" into "samples labeled at all", and in EXCLUDE mode it turns "drop the majors" into
 * "drop everything labeled".  Both return a plausible table, so nothing downstream would say the
 * typed code never reached the server.
 *
 * <p>The summary and warning update on every keystroke, for the same reason as the PV selector's:
 * the cases worth catching are accepted downstream and would otherwise be invisible.
 */
public class QueryFiltersDialogController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    @FXML private CheckBox configurationEnabledCheck;
    @FXML private GridPane configurationPane;
    @FXML private TextField configurationNamesField;
    @FXML private TextField activationIdsField;
    @FXML private TextField categoriesField;
    @FXML private TextField configurationTagsField;
    @FXML private TextField configurationAttributeKeyField;
    @FXML private TextField configurationAttributeValueField;

    @FXML private CheckBox sampleStatusEnabledCheck;
    @FXML private GridPane sampleStatusPane;
    @FXML private ComboBox<SampleStatusFilter.Mode> statusModeCombo;
    @FXML private TextField statusDomainField;
    @FXML private TextField statusLayersField;
    @FXML private TextField statusCodesField;

    @FXML private Label summaryLabel;
    @FXML private Label warningLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        statusModeCombo.getItems().setAll(SampleStatusFilter.Mode.values());
        statusModeCombo.getSelectionModel().select(SampleStatusFilter.Mode.INCLUDE_MATCHING);

        configurationEnabledCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            updatePaneEnablement();
            updateSummary();
        });
        sampleStatusEnabledCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            updatePaneEnablement();
            updateSummary();
        });

        for (TextInputControl field : List.of(
                configurationNamesField, activationIdsField, categoriesField, configurationTagsField,
                configurationAttributeKeyField, configurationAttributeValueField,
                statusDomainField, statusLayersField, statusCodesField)) {
            field.textProperty().addListener((ObservableValue<? extends String> obs,
                                              String oldVal, String newVal) -> updateSummary());
        }
        statusModeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateSummary());

        updatePaneEnablement();
        updateSummary();

        logger.debug("QueryFiltersDialogController initialized");
    }

    /**
     * Populates the dialog from the current filters.
     *
     * <p>An inactive filter leaves its fields blank rather than pre-filling a suggestion: a
     * pre-filled domain the user never chose would be sent the moment they ticked the checkbox.
     */
    public void setFilters(ConfigurationFilter configuration, SampleStatusFilter sampleStatus) {
        final ConfigurationFilter config = configuration == null
                ? ConfigurationFilter.none() : configuration;
        configurationEnabledCheck.setSelected(config.isActive());
        configurationNamesField.setText(String.join(", ", config.getConfigurationNames()));
        activationIdsField.setText(String.join(", ", config.getActivationIds()));
        categoriesField.setText(String.join(", ", config.getCategories()));
        configurationTagsField.setText(String.join(", ", config.getTags()));
        configurationAttributeKeyField.setText(
                config.getAttributeKey() == null ? "" : config.getAttributeKey());
        configurationAttributeValueField.setText(String.join(", ", config.getAttributeValues()));

        final SampleStatusFilter status = sampleStatus == null
                ? SampleStatusFilter.none() : sampleStatus;
        sampleStatusEnabledCheck.setSelected(status.isActive());
        statusModeCombo.getSelectionModel().select(status.getMode());
        statusDomainField.setText(status.getDomain() == null ? "" : status.getDomain());
        statusLayersField.setText(String.join(", ", status.getLayers()));
        statusCodesField.setText(joinCodes(status.getStatusCodes()));

        updatePaneEnablement();
        updateSummary();
    }

    /**
     * Reads back the edited configuration filter.
     *
     * <p>An unticked checkbox yields {@link ConfigurationFilter#none()} regardless of what the
     * fields hold, so criteria left behind by a previous visit cannot be carried into a filter the
     * user turned off — the same reasoning as the PV selector reading only its selected mode's
     * controls.
     */
    public ConfigurationFilter getConfigurationFilter() {
        if (!configurationEnabledCheck.isSelected()) {
            return ConfigurationFilter.none();
        }
        return ConfigurationFilter.of(
                splitValues(configurationNamesField),
                splitValues(activationIdsField),
                splitValues(categoriesField),
                splitValues(configurationTagsField),
                textOf(configurationAttributeKeyField),
                splitValues(configurationAttributeValueField));
    }

    /**
     * Reads back the edited sample status filter, or {@link SampleStatusFilter#none()} when the
     * checkbox is unticked or the status codes do not parse.
     *
     * <p>A parse failure returns the inactive filter rather than a filter with the bad codes
     * dropped, and {@link #isAcceptable()} refuses the dialog before it can be applied — so this
     * return value is never what actually reaches the query.
     */
    public SampleStatusFilter getSampleStatusFilter() {
        if (!sampleStatusEnabledCheck.isSelected()) {
            return SampleStatusFilter.none();
        }
        final List<Integer> codes = parseStatusCodes(textOf(statusCodesField));
        if (codes == null) {
            return SampleStatusFilter.none();
        }
        return SampleStatusFilter.of(
                textOf(statusDomainField),
                splitValues(statusLayersField),
                codes,
                statusModeCombo.getValue());
    }

    /**
     * Whether the dialog may be applied.
     *
     * <p>Two conditions, each protecting a silent failure rather than a loud one: a status filter
     * with no domain is REJECTED by the server (so applying it would break an otherwise valid
     * query), and an unparseable status code would otherwise be dropped into a broader filter that
     * returns a plausible result.
     */
    public boolean isAcceptable() {
        return warningFor() == null;
    }

    /** The reason the dialog cannot be applied, or null when it can. */
    private String warningFor() {
        if (sampleStatusEnabledCheck.isSelected()) {
            if (textOf(statusDomainField).isBlank()) {
                return "Enter a sample status domain — the server rejects a status filter without one.";
            }
            if (parseStatusCodes(textOf(statusCodesField)) == null) {
                return "Status codes must be whole numbers. Leave the field blank to match any code.";
            }
        }
        if (configurationEnabledCheck.isSelected() && !getConfigurationFilter().isActive()) {
            return "Enter at least one configuration criterion, or untick the box to query the "
                    + "whole time range.";
        }
        return null;
    }

    /**
     * Parses the comma-separated status codes, returning null when any entry is not an integer.
     *
     * <p>Null rather than a partial list: dropping the unparseable entries would widen the filter
     * silently, and in INCLUDE mode dropping the only code changes "samples with code 2" into
     * "samples labeled at all" — a result that looks fine.
     */
    static List<Integer> parseStatusCodes(String text) {
        final List<Integer> codes = new ArrayList<>();
        for (String value : PvMetadataExploreViewModel.parseCommaSeparatedList(text)) {
            try {
                codes.add(Integer.valueOf(value.trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return codes;
    }

    private void updatePaneEnablement() {
        // Disabled rather than hidden: the criteria stay visible so turning a filter back on shows
        // what it will send, and the dialog does not resize as boxes are ticked.
        configurationPane.setDisable(!configurationEnabledCheck.isSelected());
        sampleStatusPane.setDisable(!sampleStatusEnabledCheck.isSelected());
    }

    private void updateSummary() {
        final ConfigurationFilter config = getConfigurationFilter();
        final SampleStatusFilter status = getSampleStatusFilter();

        final List<String> parts = new ArrayList<>();
        parts.add(config.isActive() ? config.describe() : "the whole time range");
        parts.add(status.isActive() ? status.describe() : "all samples");
        summaryLabel.setText("This query will cover " + String.join(", ", parts) + ".");

        final String warning = warningFor();
        warningLabel.setText(warning == null ? "" : warning);
        // Both flags, always together: visible-but-unmanaged takes no space and cannot be read,
        // and managed-but-invisible leaves a gap where the warning would be.
        warningLabel.setVisible(warning != null);
        warningLabel.setManaged(warning != null);
    }

    private static List<String> splitValues(TextField field) {
        return PvMetadataExploreViewModel.parseCommaSeparatedList(textOf(field));
    }

    private static String textOf(TextField field) {
        return field.getText() == null ? "" : field.getText();
    }

    private static String joinCodes(List<Integer> codes) {
        final List<String> parts = new ArrayList<>();
        for (Integer code : codes) {
            parts.add(String.valueOf(code));
        }
        return String.join(", ", parts);
    }

    /** The pair of filters an accepted dialog produces. */
    public record Filters(ConfigurationFilter configuration, SampleStatusFilter sampleStatus) {
    }

    /**
     * Shows the dialog and returns the edited filters, or null if it was cancelled.
     *
     * <p>Applying is blocked while {@link #isAcceptable()} is false, so an incomplete filter cannot
     * be returned and then silently rejected by the server on the next query.
     *
     * @param configuration the configuration filter to edit
     * @param sampleStatus  the sample status filter to edit
     * @param ownerStage    owner for modality, may be null
     */
    public static Filters showDialog(
            ConfigurationFilter configuration, SampleStatusFilter sampleStatus, Stage ownerStage) {
        try {
            final Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Query Filters");
            dialog.setResizable(true);

            final javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    QueryFiltersDialogController.class.getResource(
                            "/fxml/components/query-filters-dialog.fxml"));

            final VBox content = loader.load();
            final QueryFiltersDialogController controller = loader.getController();
            controller.setFilters(configuration, sampleStatus);

            dialog.getDialogPane().setContent(content);
            final ButtonType applyButton = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(applyButton, ButtonType.CANCEL);

            // Disable Apply while the dialog is not acceptable, rather than validating on accept:
            // a rejected apply that silently returned the previous filters would look like the
            // dialog had ignored the edit.
            final javafx.scene.Node applyNode = dialog.getDialogPane().lookupButton(applyButton);
            applyNode.setDisable(!controller.isAcceptable());
            controller.summaryLabel.textProperty().addListener(
                    (obs, oldVal, newVal) -> applyNode.setDisable(!controller.isAcceptable()));
            controller.warningLabel.textProperty().addListener(
                    (obs, oldVal, newVal) -> applyNode.setDisable(!controller.isAcceptable()));

            // Re-fit when the warning appears or disappears.  This dialog keeps both filter panes
            // MANAGED and merely disables them, so its content height is stable in a way the PV
            // selector's is not -- but the warning label is managed=false until it has something to
            // say, so it grows the content exactly like a mode change does.  Same fix, same reason:
            // a Dialog sizes itself once, and what sits below the content is the button bar.
            controller.warningLabel.managedProperty().addListener(
                    (obs, oldVal, newVal) -> DialogSizing.resizeToFit(dialog));

            if (ownerStage != null) {
                dialog.initOwner(ownerStage);
            }

            final ButtonType result = dialog.showAndWait().orElse(ButtonType.CANCEL);
            if (result != applyButton) {
                logger.debug("Query filters dialog cancelled");
                return null;
            }

            final Filters filters = new Filters(
                    controller.getConfigurationFilter(), controller.getSampleStatusFilter());
            logger.info("Query filters applied: {} / {}",
                    filters.configuration().describe(), filters.sampleStatus().describe());
            return filters;

        } catch (Exception e) {
            logger.error("Failed to show query filters dialog", e);
            return null;
        }
    }
}
