package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.model.SampleStatusTableRow;
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
 * Controller for the sample status explore view.
 *
 * <p>Follows the established initialization contract: {@code initialize()} stays dependency-free so
 * {@code ViewLoadSmokeTest} can load every view from the classpath, and {@code DpApplication} /
 * {@code Stage} / {@code MainController} arrive afterward through setters.
 *
 * <p>The date pickers and time spinners are owned here rather than by the ViewModel, matching
 * machine-configuration: the ViewModel stays free of JavaFX control code, and the assembled
 * {@code Instant}s are pushed in before each search.
 */
public class SampleStatusExploreController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    /** Default window offered on first load, so the form is runnable without typing dates. */
    private static final long DEFAULT_WINDOW_HOURS = 1;

    @FXML private DatePicker startDatePicker;
    @FXML private Spinner<Integer> startHourSpinner;
    @FXML private Spinner<Integer> startMinuteSpinner;
    @FXML private Spinner<Integer> startSecondSpinner;

    @FXML private DatePicker endDatePicker;
    @FXML private Spinner<Integer> endHourSpinner;
    @FXML private Spinner<Integer> endMinuteSpinner;
    @FXML private Spinner<Integer> endSecondSpinner;

    @FXML private TextField pvNamesField;
    @FXML private TextField domainsField;
    @FXML private TextField layersField;

    @FXML private Button searchButton;
    @FXML private Button clearButton;
    @FXML private ProgressIndicator searchProgressIndicator;
    @FXML private Label searchStatusLabel;
    @FXML private Label resultCountLabel;
    @FXML private Label resultsStatusLabel;

    @FXML private TableView<SampleStatusTableRow> resultsTable;
    @FXML private TableColumn<SampleStatusTableRow, String> pvNameColumn;
    @FXML private TableColumn<SampleStatusTableRow, String> timestampColumn;
    @FXML private TableColumn<SampleStatusTableRow, String> domainColumn;
    @FXML private TableColumn<SampleStatusTableRow, String> layerColumn;
    @FXML private TableColumn<SampleStatusTableRow, String> statusCodeColumn;
    @FXML private TableColumn<SampleStatusTableRow, String> statusLabelColumn;
    @FXML private TableColumn<SampleStatusTableRow, String> confidenceColumn;
    @FXML private TableColumn<SampleStatusTableRow, String> reasonColumn;
    @FXML private TableColumn<SampleStatusTableRow, String> sourceColumn;
    @FXML private TableColumn<SampleStatusTableRow, String> modifiedByColumn;

    private final SampleStatusExploreViewModel viewModel = new SampleStatusExploreViewModel();

    private DpApplication dpApplication;
    private Stage primaryStage;
    private MainController mainController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        setupDefaultTimeRange();
        bindUIToViewModel();
        logger.debug("SampleStatusExploreController initialized");
    }

    /**
     * Wires the columns to row properties by name.
     *
     * <p>The binding strings are resolved reflectively at render time, so a stale one yields a
     * silently blank column rather than a compile error. Referencing the row's {@code PROPERTY_*}
     * constants instead of repeating literals makes a rename that misses this file fail to compile —
     * the same guard {@code AnnotationExploreController} uses.
     */
    private void setupTableColumns() {
        pvNameColumn.setCellValueFactory(new PropertyValueFactory<>(SampleStatusTableRow.PROPERTY_PV_NAME));
        timestampColumn.setCellValueFactory(new PropertyValueFactory<>(SampleStatusTableRow.PROPERTY_TIMESTAMP));
        domainColumn.setCellValueFactory(new PropertyValueFactory<>(SampleStatusTableRow.PROPERTY_DOMAIN));
        layerColumn.setCellValueFactory(new PropertyValueFactory<>(SampleStatusTableRow.PROPERTY_LAYER));
        statusCodeColumn.setCellValueFactory(new PropertyValueFactory<>(SampleStatusTableRow.PROPERTY_STATUS_CODE));
        statusLabelColumn.setCellValueFactory(new PropertyValueFactory<>(SampleStatusTableRow.PROPERTY_STATUS_LABEL));
        confidenceColumn.setCellValueFactory(new PropertyValueFactory<>(SampleStatusTableRow.PROPERTY_CONFIDENCE));
        reasonColumn.setCellValueFactory(new PropertyValueFactory<>(SampleStatusTableRow.PROPERTY_REASON));
        sourceColumn.setCellValueFactory(new PropertyValueFactory<>(SampleStatusTableRow.PROPERTY_SOURCE));
        modifiedByColumn.setCellValueFactory(new PropertyValueFactory<>(SampleStatusTableRow.PROPERTY_MODIFIED_BY));
    }

    /**
     * Seeds the form with the last hour, so the view is runnable without typing a date.
     *
     * <p>Deliberately not wired to the application's global time range: that range describes ingested
     * data, while statuses are queried independently and may exist outside it.
     */
    private void setupDefaultTimeRange() {
        final Instant now = Instant.now();
        final Instant windowStart = now.minusSeconds(DEFAULT_WINDOW_HOURS * 3600);

        applyInstant(windowStart, startDatePicker, startHourSpinner, startMinuteSpinner, startSecondSpinner);
        applyInstant(now, endDatePicker, endHourSpinner, endMinuteSpinner, endSecondSpinner);
    }

    private static void applyInstant(
            Instant instant,
            DatePicker datePicker,
            Spinner<Integer> hourSpinner,
            Spinner<Integer> minuteSpinner,
            Spinner<Integer> secondSpinner
    ) {
        final java.time.LocalDateTime local = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        datePicker.setValue(local.toLocalDate());
        hourSpinner.getValueFactory().setValue(local.getHour());
        minuteSpinner.getValueFactory().setValue(local.getMinute());
        secondSpinner.getValueFactory().setValue(local.getSecond());
    }

    private void bindUIToViewModel() {
        pvNamesField.textProperty().bindBidirectional(viewModel.pvNamesProperty());
        domainsField.textProperty().bindBidirectional(viewModel.domainsProperty());
        layersField.textProperty().bindBidirectional(viewModel.layersProperty());

        searchStatusLabel.textProperty().bind(viewModel.searchStatusMessageProperty());
        resultCountLabel.textProperty().bind(viewModel.resultCountMessageProperty());
        resultsStatusLabel.textProperty().bind(viewModel.statusMessageProperty());

        searchProgressIndicator.visibleProperty().bind(viewModel.searchInProgressProperty());
        searchButton.disableProperty().bind(viewModel.searchInProgressProperty());

        resultsTable.setItems(viewModel.getSearchResults());
    }

    @FXML
    private void onSearch() {
        // The controller owns the temporal controls, so it assembles the range and pushes it in
        // before each search rather than having the ViewModel reach into JavaFX controls.
        // The END is extended to the last nanosecond of the selected second, because the trim in
        // SampleStatusTableRow.expand() is HALF-OPEN: without this, an end of 12:00:00 would drop
        // every status stamped within that second, including one exactly at 12:00:00 -- the second
        // the user just named. The data query's getQueryEndDateTime() makes the same adjustment for
        // the same reason, so the two views agree on what an end time means.
        viewModel.setTimeRange(
                instantFrom(startDatePicker, startHourSpinner, startMinuteSpinner, startSecondSpinner),
                inclusiveEnd(
                        instantFrom(endDatePicker, endHourSpinner, endMinuteSpinner, endSecondSpinner)));
        viewModel.executeSearch();
    }

    @FXML
    private void onClear() {
        viewModel.clearSearch();
        setupDefaultTimeRange();
    }

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

    /**
     * Extends a selected end time to the last nanosecond of that second.
     *
     * <p>The spinners select whole seconds, but the range is compared against status timestamps at
     * NANOSECOND precision and trimmed half-open, so the selected second would otherwise be
     * excluded entirely rather than included — the same adjustment, and the same reasoning, as
     * {@code DataExploreViewModel.getQueryEndDateTime()}.
     *
     * <p>Null passes through, so a missing end date is still reported as missing rather than
     * becoming a time just after the epoch.
     */
    private static Instant inclusiveEnd(Instant end) {
        return end == null ? null : end.plusNanos(999_999_999);
    }

    private void commitSpinnerValues(List<Spinner<Integer>> spinners) {
        // Commit pending editor text: a value typed but not committed is otherwise not seen here,
        // and the search would silently use the previous value.
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
        logger.debug("DpApplication injected into SampleStatusExploreController");
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        viewModel.setMainController(mainController);
    }

    SampleStatusExploreViewModel getViewModel() {
        return viewModel;
    }
}
