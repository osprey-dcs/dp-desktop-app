package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.component.HyperlinkListTableCell;
import com.ospreydcs.dp.gui.model.ConfigurationActivationTableRow;
import com.ospreydcs.dp.gui.model.ConfigurationTableRow;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Configuration Explore view, reached via Explore &gt; Machine Configurations.
 *
 * <p>Hosts two independent searches in a TabPane — configurations and their activations — each with
 * its own criteria, progress indicator and status labels. See
 * {@link ConfigurationExploreViewModel} for why they are not one search.
 */
public class ConfigurationExploreController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    @FXML private TabPane searchTabPane;

    // ---- Configuration search ----
    @FXML private TextField configurationNameField;
    @FXML private ComboBox<ConfigurationExploreViewModel.MatchMode> configurationNameMatchModeCombo;
    @FXML private TextField configurationCategoryField;
    @FXML private TextField configurationParentField;
    @FXML private TextField configurationTagsField;
    @FXML private TextField configurationAttributeKeyField;
    @FXML private TextField configurationAttributeValueField;
    @FXML private Button configurationSearchButton;
    @FXML private ProgressIndicator configurationSearchProgressIndicator;
    @FXML private Label configurationSearchStatusLabel;
    @FXML private Label configurationResultCountLabel;
    @FXML private Label configurationResultsStatusLabel;

    @FXML private TableView<ConfigurationTableRow> configurationResultsTable;
    @FXML private TableColumn<ConfigurationTableRow, String> configurationNameColumn;
    @FXML private TableColumn<ConfigurationTableRow, String> configurationCategoryColumn;
    @FXML private TableColumn<ConfigurationTableRow, String> configurationParentColumn;
    @FXML private TableColumn<ConfigurationTableRow, String> configurationDescriptionColumn;
    @FXML private TableColumn<ConfigurationTableRow, String> configurationTagsColumn;
    @FXML private TableColumn<ConfigurationTableRow, String> configurationAttributesColumn;
    @FXML private TableColumn<ConfigurationTableRow, String> configurationModifiedByColumn;
    @FXML private TableColumn<ConfigurationTableRow, String> configurationUpdatedTimeColumn;

    // ---- Activation search ----
    @FXML private TextField activationConfigurationNamesField;
    @FXML private TextField activationIdsField;
    @FXML private TextField activationCategoryField;
    @FXML private TextField activationTagsField;
    @FXML private TextField activationAttributeKeyField;
    @FXML private TextField activationAttributeValueField;

    @FXML private CheckBox activeAtEnabledCheckBox;
    @FXML private DatePicker activeAtDatePicker;
    @FXML private Spinner<Integer> activeAtHourSpinner;
    @FXML private Spinner<Integer> activeAtMinuteSpinner;
    @FXML private Spinner<Integer> activeAtSecondSpinner;

    @FXML private CheckBox rangeEnabledCheckBox;
    @FXML private DatePicker rangeStartDatePicker;
    @FXML private Spinner<Integer> rangeStartHourSpinner;
    @FXML private Spinner<Integer> rangeStartMinuteSpinner;
    @FXML private DatePicker rangeEndDatePicker;
    @FXML private Spinner<Integer> rangeEndHourSpinner;
    @FXML private Spinner<Integer> rangeEndMinuteSpinner;

    @FXML private Button activationSearchButton;
    @FXML private ProgressIndicator activationSearchProgressIndicator;
    @FXML private Label activationSearchStatusLabel;
    @FXML private Label activationResultCountLabel;
    @FXML private Label activationResultsStatusLabel;

    @FXML private TableView<ConfigurationActivationTableRow> activationResultsTable;
    @FXML private TableColumn<ConfigurationActivationTableRow, String> activationIdColumn;
    @FXML private TableColumn<ConfigurationActivationTableRow, String> activationConfigurationNameColumn;
    @FXML private TableColumn<ConfigurationActivationTableRow, String> activationStartTimeColumn;
    @FXML private TableColumn<ConfigurationActivationTableRow, String> activationEndTimeColumn;
    @FXML private TableColumn<ConfigurationActivationTableRow, String> activationDescriptionColumn;
    @FXML private TableColumn<ConfigurationActivationTableRow, String> activationTagsColumn;
    @FXML private TableColumn<ConfigurationActivationTableRow, String> activationAttributesColumn;
    @FXML private TableColumn<ConfigurationActivationTableRow, String> activationModifiedByColumn;

    // Dependencies
    private DpApplication dpApplication;
    private Stage primaryStage;
    private MainController mainController;
    private ConfigurationExploreViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("ConfigurationExploreController initializing...");

        viewModel = new ConfigurationExploreViewModel();

        setupTimeSpinners();
        setupMatchModeCombo();
        setupConfigurationColumns();
        setupActivationColumns();
        bindUIToViewModel();

        logger.debug("ConfigurationExploreController initialized");
    }

    private void setupMatchModeCombo() {
        configurationNameMatchModeCombo.setItems(
                FXCollections.observableArrayList(ConfigurationExploreViewModel.MatchMode.values()));
        configurationNameMatchModeCombo.getSelectionModel()
                .select(ConfigurationExploreViewModel.MatchMode.CONTAINS);
        configurationNameMatchModeCombo.valueProperty().addListener(
                (observable, oldMode, newMode) -> viewModel.setConfigurationNameMatchMode(newMode));
    }

    private void setupTimeSpinners() {
        configureSpinner(activeAtHourSpinner, 23);
        configureSpinner(activeAtMinuteSpinner, 59);
        configureSpinner(activeAtSecondSpinner, 59);
        configureSpinner(rangeStartHourSpinner, 23);
        configureSpinner(rangeStartMinuteSpinner, 59);
        configureSpinner(rangeEndHourSpinner, 23);
        configureSpinner(rangeEndMinuteSpinner, 59);
    }

    private static void configureSpinner(Spinner<Integer> spinner, int max) {
        spinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, max, 0));
    }

    /**
     * Binds the result table columns.
     *
     * <p>These are PropertyValueFactory strings resolved reflectively at render time, so they use
     * the row's {@code PROPERTY_*} constants — a stale string yields a silently blank column rather
     * than a compile error.
     */
    private void setupConfigurationColumns() {
        configurationNameColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationTableRow.PROPERTY_CONFIGURATION_NAME));
        // the name is the link to the editor, and onClick receives the ROW, so the editor is loaded
        // from the record rather than from the displayed string
        configurationNameColumn.setCellFactory(HyperlinkListTableCell.forSingleValue(
                ConfigurationTableRow::getConfigurationName,
                (row, name) -> viewModel.editConfiguration(row)));

        configurationCategoryColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationTableRow.PROPERTY_CATEGORY));
        configurationParentColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationTableRow.PROPERTY_PARENT_CONFIGURATION_NAME));
        configurationDescriptionColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationTableRow.PROPERTY_DESCRIPTION));
        configurationTagsColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationTableRow.PROPERTY_TAGS));
        configurationAttributesColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationTableRow.PROPERTY_ATTRIBUTES));
        configurationModifiedByColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationTableRow.PROPERTY_MODIFIED_BY));
        configurationUpdatedTimeColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationTableRow.PROPERTY_UPDATED_TIME));
    }

    private void setupActivationColumns() {
        activationIdColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationActivationTableRow.PROPERTY_CLIENT_ACTIVATION_ID));
        activationConfigurationNameColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationActivationTableRow.PROPERTY_CONFIGURATION_NAME));
        // links by configuration name, which is what an activation carries; the editor is opened by
        // resolving that name through getConfiguration()
        activationConfigurationNameColumn.setCellFactory(HyperlinkListTableCell.forSingleValue(
                ConfigurationActivationTableRow::getConfigurationName,
                (row, name) -> openConfigurationByName(name)));

        activationStartTimeColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationActivationTableRow.PROPERTY_START_TIME));
        activationEndTimeColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationActivationTableRow.PROPERTY_END_TIME));
        activationDescriptionColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationActivationTableRow.PROPERTY_DESCRIPTION));
        activationTagsColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationActivationTableRow.PROPERTY_TAGS));
        activationAttributesColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationActivationTableRow.PROPERTY_ATTRIBUTES));
        activationModifiedByColumn.setCellValueFactory(
                new PropertyValueFactory<>(ConfigurationActivationTableRow.PROPERTY_MODIFIED_BY));
    }

    private void bindUIToViewModel() {
        // configuration form
        configurationNameField.textProperty()
                .bindBidirectional(viewModel.configurationNameTextProperty());
        configurationCategoryField.textProperty()
                .bindBidirectional(viewModel.configurationCategoryTextProperty());
        configurationParentField.textProperty()
                .bindBidirectional(viewModel.configurationParentTextProperty());
        configurationTagsField.textProperty()
                .bindBidirectional(viewModel.configurationTagsTextProperty());
        configurationAttributeKeyField.textProperty()
                .bindBidirectional(viewModel.configurationAttributeKeyProperty());
        configurationAttributeValueField.textProperty()
                .bindBidirectional(viewModel.configurationAttributeValueProperty());

        configurationResultsTable.setItems(viewModel.getConfigurationResults());
        configurationSearchStatusLabel.textProperty()
                .bind(viewModel.configurationSearchStatusMessageProperty());
        configurationResultCountLabel.textProperty()
                .bind(viewModel.configurationResultCountMessageProperty());
        configurationResultsStatusLabel.textProperty()
                .bind(viewModel.configurationStatusMessageProperty());
        configurationSearchProgressIndicator.visibleProperty()
                .bind(viewModel.configurationSearchInProgressProperty());
        configurationSearchButton.disableProperty()
                .bind(viewModel.configurationSearchInProgressProperty());

        // activation form
        activationConfigurationNamesField.textProperty()
                .bindBidirectional(viewModel.activationConfigurationNamesTextProperty());
        activationIdsField.textProperty().bindBidirectional(viewModel.activationIdsTextProperty());
        activationCategoryField.textProperty()
                .bindBidirectional(viewModel.activationCategoryTextProperty());
        activationTagsField.textProperty().bindBidirectional(viewModel.activationTagsTextProperty());
        activationAttributeKeyField.textProperty()
                .bindBidirectional(viewModel.activationAttributeKeyProperty());
        activationAttributeValueField.textProperty()
                .bindBidirectional(viewModel.activationAttributeValueProperty());

        activationResultsTable.setItems(viewModel.getActivationResults());
        activationSearchStatusLabel.textProperty()
                .bind(viewModel.activationSearchStatusMessageProperty());
        activationResultCountLabel.textProperty()
                .bind(viewModel.activationResultCountMessageProperty());
        activationResultsStatusLabel.textProperty()
                .bind(viewModel.activationStatusMessageProperty());
        activationSearchProgressIndicator.visibleProperty()
                .bind(viewModel.activationSearchInProgressProperty());
        activationSearchButton.disableProperty()
                .bind(viewModel.activationSearchInProgressProperty());

        // The temporal controls are only read when their checkbox is ticked, so an unticked
        // criterion cannot contribute a stale date left over from an earlier search.
        activeAtDatePicker.disableProperty().bind(activeAtEnabledCheckBox.selectedProperty().not());
        activeAtHourSpinner.disableProperty().bind(activeAtEnabledCheckBox.selectedProperty().not());
        activeAtMinuteSpinner.disableProperty().bind(activeAtEnabledCheckBox.selectedProperty().not());
        activeAtSecondSpinner.disableProperty().bind(activeAtEnabledCheckBox.selectedProperty().not());

        rangeStartDatePicker.disableProperty().bind(rangeEnabledCheckBox.selectedProperty().not());
        rangeStartHourSpinner.disableProperty().bind(rangeEnabledCheckBox.selectedProperty().not());
        rangeStartMinuteSpinner.disableProperty().bind(rangeEnabledCheckBox.selectedProperty().not());
        rangeEndDatePicker.disableProperty().bind(rangeEnabledCheckBox.selectedProperty().not());
        rangeEndHourSpinner.disableProperty().bind(rangeEnabledCheckBox.selectedProperty().not());
        rangeEndMinuteSpinner.disableProperty().bind(rangeEnabledCheckBox.selectedProperty().not());
    }

    // ---------------------------------------------------------------------------------------
    // Action handlers
    // ---------------------------------------------------------------------------------------

    @FXML
    private void onConfigurationSearch() {
        viewModel.executeConfigurationSearch();
    }

    @FXML
    private void onConfigurationClear() {
        viewModel.clearConfigurationSearch();
        configurationNameMatchModeCombo.getSelectionModel()
                .select(ConfigurationExploreViewModel.MatchMode.CONTAINS);
    }

    @FXML
    private void onActivationSearch() {
        publishTemporalCriteria();
        viewModel.executeActivationSearch();
    }

    @FXML
    private void onActivationClear() {
        viewModel.clearActivationSearch();
        clearTemporalFields();
    }

    /**
     * Reads the date/time controls into the ViewModel.
     *
     * <p>A criterion whose checkbox is unticked is published as null rather than as whatever its
     * controls happen to hold, so a date left behind by an earlier search cannot silently narrow the
     * next one.
     */
    private void publishTemporalCriteria() {
        final Instant activeAt = activeAtEnabledCheckBox.isSelected()
                ? instantFrom(activeAtDatePicker, activeAtHourSpinner, activeAtMinuteSpinner,
                        activeAtSecondSpinner)
                : null;

        final Instant rangeStart = rangeEnabledCheckBox.isSelected()
                ? instantFrom(rangeStartDatePicker, rangeStartHourSpinner, rangeStartMinuteSpinner, null)
                : null;

        final Instant rangeEnd = rangeEnabledCheckBox.isSelected()
                ? instantFrom(rangeEndDatePicker, rangeEndHourSpinner, rangeEndMinuteSpinner, null)
                : null;

        viewModel.setActivationTemporalCriteria(activeAt, rangeStart, rangeEnd);
    }

    private void clearTemporalFields() {
        activeAtEnabledCheckBox.setSelected(false);
        rangeEnabledCheckBox.setSelected(false);
        activeAtDatePicker.setValue(null);
        rangeStartDatePicker.setValue(null);
        rangeEndDatePicker.setValue(null);

        // The spinners are reset explicitly: clearing only the dates would leave a previously
        // entered time of day in place for the next search to reuse silently.
        resetSpinners(List.of(
                activeAtHourSpinner, activeAtMinuteSpinner, activeAtSecondSpinner,
                rangeStartHourSpinner, rangeStartMinuteSpinner,
                rangeEndHourSpinner, rangeEndMinuteSpinner));
    }

    private static void resetSpinners(List<Spinner<Integer>> spinners) {
        for (Spinner<Integer> spinner : spinners) {
            final SpinnerValueFactory<Integer> valueFactory = spinner.getValueFactory();
            if (valueFactory != null) {
                valueFactory.setValue(0);
            }
        }
    }

    /**
     * Reads a date picker and its time spinners into an Instant, or null when no date is set.
     *
     * <p>Spinner values are committed first: an edit typed into an editable spinner without pressing
     * Enter is not otherwise reflected in getValue(), so the time read here would silently be the
     * previous one. The second spinner is optional, since the range controls carry only hours and
     * minutes.
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

        commitSpinnerValues(secondSpinner == null
                ? List.of(hourSpinner, minuteSpinner)
                : List.of(hourSpinner, minuteSpinner, secondSpinner));

        final LocalTime time = LocalTime.of(
                hourSpinner.getValue(),
                minuteSpinner.getValue(),
                secondSpinner == null ? 0 : secondSpinner.getValue());

        return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant();
    }

    private void commitSpinnerValues(List<Spinner<Integer>> spinners) {
        try {
            for (Spinner<Integer> spinner : spinners) {
                spinner.commitValue();
            }
        } catch (Exception e) {
            logger.warn("Error committing spinner values: {}", e.getMessage());
        }
    }

    /**
     * Opens the configuration editor for a name carried by an activation row.
     *
     * <p>Unlike the configurations table, an activation holds only its configuration's <em>name</em>
     * — the record itself is not embedded — so this resolves the name through
     * {@code getConfiguration()} before navigating. A name that no longer resolves is reported
     * rather than opening an empty editor, which would invite creating a new record under a name the
     * user believed already existed.
     */
    private void openConfigurationByName(String configurationName) {
        if (configurationName == null || configurationName.isBlank() || dpApplication == null) {
            return;
        }

        final javafx.concurrent.Task<com.ospreydcs.dp.client.result.GetConfigurationApiResult> loadTask =
                new javafx.concurrent.Task<>() {
            @Override
            protected com.ospreydcs.dp.client.result.GetConfigurationApiResult call() {
                return dpApplication.getConfiguration(configurationName);
            }
        };

        loadTask.setOnSucceeded(event -> {
            final var result = loadTask.getValue();

            // isReject() is not-found; isError() alone cannot tell that from an unreachable service
            if (result == null || result.isReject()) {
                viewModel.activationStatusMessageProperty().set(
                        "No configuration found named \"" + configurationName + "\"");
                return;
            }
            if (result.resultStatus.isError) {
                viewModel.activationStatusMessageProperty().set(
                        "Could not load configuration: " + result.resultStatus.msg);
                return;
            }
            if (mainController != null) {
                mainController.navigateToConfigurationEditor(result.configuration);
            }
        });

        loadTask.setOnFailed(event -> {
            final Throwable failure = loadTask.getException();
            logger.error("failed to load configuration {}", configurationName, failure);
            viewModel.activationStatusMessageProperty().set(
                    "Could not load configuration: " + failure.getMessage());
        });

        final Thread loadThread = new Thread(loadTask, "load-configuration");
        loadThread.setDaemon(true);
        loadThread.start();
    }

    // ---------------------------------------------------------------------------------------
    // Dependency injection
    // ---------------------------------------------------------------------------------------

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        viewModel.setDpApplication(dpApplication);
        logger.debug("DpApplication injected");
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        viewModel.setMainController(mainController);
        logger.debug("MainController injected");
    }

    /** Exposed for tests, which assert on criteria construction rather than driving the controls. */
    ConfigurationExploreViewModel getViewModel() {
        return viewModel;
    }
}
