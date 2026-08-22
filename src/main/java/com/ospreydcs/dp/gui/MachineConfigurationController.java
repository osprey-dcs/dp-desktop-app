package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.component.AttributesListComponent;
import com.ospreydcs.dp.gui.component.TagsListComponent;
import com.ospreydcs.dp.gui.model.ConfigurationActivationDetail;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller for the Machine Configuration view, reached via the Metadata > Machine Configuration
 * menu item.
 */
public class MachineConfigurationController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    // FXML components - configuration form
    @FXML private TextField configurationNameField;
    @FXML private TextField categoryField;
    @FXML private TextArea configurationDescriptionArea;
    @FXML private TextField parentConfigurationNameField;
    @FXML private TextField configurationModifiedByField;
    @FXML private TagsListComponent configurationTagsComponent;
    @FXML private AttributesListComponent configurationAttributesComponent;
    @FXML private Button saveConfigurationButton;
    @FXML private Button resetButton;

    // FXML components - activation form
    @FXML private VBox activationsSection;
    @FXML private Label activationConfigurationNameLabel;
    @FXML private DatePicker startDatePicker;
    @FXML private Spinner<Integer> startHourSpinner;
    @FXML private Spinner<Integer> startMinuteSpinner;
    @FXML private Spinner<Integer> startSecondSpinner;
    @FXML private DatePicker endDatePicker;
    @FXML private Spinner<Integer> endHourSpinner;
    @FXML private Spinner<Integer> endMinuteSpinner;
    @FXML private Spinner<Integer> endSecondSpinner;
    @FXML private TextField clientActivationIdField;
    @FXML private TextArea activationDescriptionArea;
    @FXML private TextField activationModifiedByField;
    @FXML private TagsListComponent activationTagsComponent;
    @FXML private AttributesListComponent activationAttributesComponent;
    @FXML private Button addActivationButton;
    @FXML private ListView<ConfigurationActivationDetail> activationsListView;

    // FXML components - status
    @FXML private ProgressIndicator saveProgressIndicator;
    @FXML private Label statusLabel;

    // Dependencies
    private DpApplication dpApplication;
    private Stage primaryStage;
    private MainController mainController;
    private MachineConfigurationViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("MachineConfigurationController initializing...");

        // Initialize ViewModel
        viewModel = new MachineConfigurationViewModel();

        // Inject component references into the ViewModel so it can read tags and attributes
        // directly from the components at save time.  The configuration and the activation each
        // have their own pair: they are separate fields on separate records.
        viewModel.setConfigurationTagsComponent(configurationTagsComponent);
        viewModel.setConfigurationAttributesComponent(configurationAttributesComponent);
        viewModel.setActivationTagsComponent(activationTagsComponent);
        viewModel.setActivationAttributesComponent(activationAttributesComponent);

        // Supply the overwrite confirmation dialog, which the ViewModel raises when a save would
        // replace an existing configuration record.
        viewModel.setOverwriteConfirmation(this::confirmOverwrite);

        // Same for an activation whose client activation id collides with one created this session.
        viewModel.setActivationOverwriteConfirmation(this::confirmActivationOverwrite);

        // The activation date and time controls live here, not in the ViewModel, so clearing the
        // activation form has to come back through this callback to reach them.
        viewModel.setActivationTemporalFieldsReset(this::clearActivationTemporalFields);

        // Bind UI to ViewModel
        bindUIToViewModel();

        logger.debug("MachineConfigurationController initialized");
    }

    private void bindUIToViewModel() {
        // Bind configuration form fields
        configurationNameField.textProperty().bindBidirectional(viewModel.configurationNameProperty());
        categoryField.textProperty().bindBidirectional(viewModel.categoryProperty());
        configurationDescriptionArea.textProperty()
                .bindBidirectional(viewModel.configurationDescriptionProperty());
        parentConfigurationNameField.textProperty()
                .bindBidirectional(viewModel.parentConfigurationNameProperty());
        configurationModifiedByField.textProperty()
                .bindBidirectional(viewModel.configurationModifiedByProperty());

        // Bind activation form fields
        clientActivationIdField.textProperty().bindBidirectional(viewModel.clientActivationIdProperty());
        activationDescriptionArea.textProperty()
                .bindBidirectional(viewModel.activationDescriptionProperty());
        activationModifiedByField.textProperty()
                .bindBidirectional(viewModel.activationModifiedByProperty());

        // Bind status label and progress indicator
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        saveProgressIndicator.visibleProperty().bind(viewModel.isSavingProperty());

        // Save is disabled while either required field is blank or a save is in progress.  The
        // blank checks trim first, so a whitespace-only entry does not present an enabled button
        // for input the ViewModel would reject anyway.
        final BooleanBinding requiredFieldIsBlank = Bindings.createBooleanBinding(
                () -> isBlank(configurationNameField.getText()) || isBlank(categoryField.getText()),
                configurationNameField.textProperty(), categoryField.textProperty());
        saveConfigurationButton.disableProperty()
                .bind(requiredFieldIsBlank.or(viewModel.isSavingProperty()));

        // Reset is disabled while a save is in progress
        resetButton.disableProperty().bind(viewModel.isSavingProperty());

        // The activation section stays disabled until a configuration has been saved in this
        // session.  That is what keeps the server's "no Configuration found" rejection from being
        // reachable through normal use of this view.
        //
        // It is also disabled while a save is in flight: a successful activation save clears this
        // form, so input typed into it during the request would be discarded on completion.
        activationsSection.disableProperty().bind(
                viewModel.configurationSavedProperty().not().or(viewModel.isSavingProperty()));
        activationConfigurationNameLabel.textProperty().bind(viewModel.savedConfigurationNameProperty());

        // Add is disabled until both dates are set, and while a save is in progress.  The times
        // themselves default to 00:00:00 in the spinners, so only the dates need checking.
        final BooleanBinding datesNotSet = Bindings.createBooleanBinding(
                () -> startDatePicker.getValue() == null || endDatePicker.getValue() == null,
                startDatePicker.valueProperty(), endDatePicker.valueProperty());
        addActivationButton.disableProperty().bind(datesNotSet.or(viewModel.isSavingProperty()));

        // Bind the session activation list
        activationsListView.setItems(viewModel.getActivations());

        logger.debug("UI bound to MachineConfigurationViewModel");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Asks the user whether to replace an existing configuration record.  Called on the FX thread
     * by the ViewModel when its pre-save existence check finds a record for this name.
     */
    private boolean confirmOverwrite(String configurationName) {
        final Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Replace existing configuration?");
        alert.setHeaderText("A configuration named \"" + configurationName + "\" already exists.");
        alert.setContentText(
                "Saving replaces the entire existing record: category, description, parent "
                        + "configuration, tags, attributes and modified by are all overwritten, and "
                        + "anything left blank is not preserved.\n\nReplace it?");

        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }

        final Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == ButtonType.OK;
    }

    /**
     * Asks the user whether to replace an activation already created in this session under the same
     * client activation id.  Called on the FX thread by the ViewModel before the save is issued.
     */
    private boolean confirmActivationOverwrite(String clientActivationId) {
        final Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Replace existing activation?");
        alert.setHeaderText(
                "An activation with client activation id \"" + clientActivationId
                        + "\" was already created in this session.");
        alert.setContentText(
                "Saving replaces that entire activation record: interval, description, tags, "
                        + "attributes and modified by are all overwritten, and anything left blank "
                        + "is not preserved.\n\nLeave the id blank to add a new activation "
                        + "instead.\n\nReplace it?");

        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }

        final Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == ButtonType.OK;
    }

    /**
     * Clears the activation date pickers and resets the six time spinners to zero.
     *
     * The spinners are reset explicitly: clearing only the dates would leave a previously entered
     * time of day in place, so the next activation would silently reuse it.
     */
    private void clearActivationTemporalFields() {
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);

        resetSpinners(List.of(
                startHourSpinner, startMinuteSpinner, startSecondSpinner,
                endHourSpinner, endMinuteSpinner, endSecondSpinner));
    }

    private void resetSpinners(List<Spinner<Integer>> spinners) {
        for (Spinner<Integer> spinner : spinners) {
            final SpinnerValueFactory<Integer> valueFactory = spinner.getValueFactory();
            if (valueFactory != null) {
                valueFactory.setValue(0);
            }
        }
    }

    /**
     * Reads a date picker and its three time spinners into an Instant, or null when no date is set.
     *
     * Spinner values are committed first: an edit typed into an editable spinner without pressing
     * Enter is not otherwise reflected in getValue(), so the time read here would silently be the
     * previous one.
     */
    private Instant instantFrom(
            DatePicker datePicker,
            Spinner<Integer> hourSpinner,
            Spinner<Integer> minuteSpinner,
            Spinner<Integer> secondSpinner
    ) {
        final LocalDate date = datePicker.getValue();
        if (date == null) {
            return null;
        }

        commitSpinnerValues(List.of(hourSpinner, minuteSpinner, secondSpinner));

        final LocalTime time = LocalTime.of(
                hourSpinner.getValue(), minuteSpinner.getValue(), secondSpinner.getValue());

        return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant();
    }

    private void commitSpinnerValues(List<Spinner<Integer>> spinners) {
        // Force commit any pending edits in spinners by calling commitValue()
        try {
            for (Spinner<Integer> spinner : spinners) {
                spinner.commitValue();
            }
        } catch (Exception e) {
            logger.warn("Error committing spinner values: {}", e.getMessage());
        }
    }

    // Dependency injection methods

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        viewModel.setDpApplication(dpApplication);
        logger.debug("DpApplication injected");
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        logger.debug("Primary stage injected");
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        viewModel.setMainController(mainController);
        logger.debug("MainController injected");
    }

    // Action handlers

    @FXML
    private void onSaveConfiguration() {
        logger.debug("Save configuration action triggered");
        viewModel.saveConfiguration();
    }

    @FXML
    private void onAddActivation() {
        logger.debug("Add activation action triggered");

        final Instant startTime =
                instantFrom(startDatePicker, startHourSpinner, startMinuteSpinner, startSecondSpinner);
        final Instant endTime =
                instantFrom(endDatePicker, endHourSpinner, endMinuteSpinner, endSecondSpinner);

        viewModel.addActivation(startTime, endTime);
    }

    @FXML
    private void onReset() {
        logger.debug("Reset machine configuration form action triggered");

        // The temporal controls are cleared through resetForm() below, which calls back into
        // clearActivationTemporalFields() - the same path a successful activation save takes, so
        // the two cannot drift apart.
        viewModel.resetForm();
    }
}
