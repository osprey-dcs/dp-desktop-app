package com.ospreydcs.dp.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
// CategoryAxis import removed - using NumberAxis for both axes
import javafx.scene.chart.NumberAxis;
import javafx.scene.Node;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

import com.ospreydcs.dp.gui.model.DataSetDetail;
import com.ospreydcs.dp.gui.model.DataFrameDetails;
import com.ospreydcs.dp.grpc.v1.common.DataColumn;
import com.ospreydcs.dp.grpc.v1.common.DataValue;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.client.utility.DataImportUtility;
import com.ospreydcs.dp.client.result.DataImportResult;


public class DataExploreController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    // Query Specification FXML components
    @FXML private VBox querySpecificationSection;
    @FXML private Button toggleSpecificationButton;
    @FXML private Label specificationStatusLabel;
    @FXML private VBox specificationContent;
    @FXML private ListView<String> pvNamesList;
    @FXML private Button addPvButton;
    @FXML private Button removePvButton;
    
    // PV Search Panel FXML components
    @FXML private VBox pvSearchPanel;
    @FXML private TextField pvSearchTextField;
    @FXML private RadioButton searchByNameListRadio;
    @FXML private RadioButton searchByPatternRadio;
    @FXML private Button searchPvButton;
    @FXML private Button closePvSearchButton;
    @FXML private Label searchStatusLabel;
    @FXML private ListView<String> searchResultsList;
    @FXML private Button addSelectedPvButton;
    @FXML private Label searchResultCountLabel;
    
    // Time Range FXML components
    @FXML private DatePicker queryBeginDatePicker;
    @FXML private Spinner<Integer> beginHourSpinner;
    @FXML private Spinner<Integer> beginMinuteSpinner;
    @FXML private Spinner<Integer> beginSecondSpinner;
    @FXML private DatePicker queryEndDatePicker;
    @FXML private Spinner<Integer> endHourSpinner;
    @FXML private Spinner<Integer> endMinuteSpinner;
    @FXML private Spinner<Integer> endSecondSpinner;
    
    // Action Buttons FXML components
    @FXML private Button submitQueryButton;
    @FXML private Button addToDatasetButton;
    @FXML private Button cancelQueryButton;
    @FXML private Label queryStatusLabel;
    
    // Query Results FXML components
    @FXML private VBox queryResultsSection;
    @FXML private Button toggleResultsButton;
    @FXML private VBox resultsContent;
    @FXML private Label rowCountLabel;
    @FXML private Label resultsStatusLabel2;
    @FXML private TabPane resultsTabPane;
    @FXML private TableView<ObservableList<Object>> resultsTable;
    @FXML private LineChart<Number, Number> resultsChart;
    @FXML private NumberAxis chartXAxis;
    @FXML private NumberAxis chartYAxis;
    @FXML private Label chartPlaceholder;
    @FXML private Label resultsStatusLabel;
    @FXML private ProgressIndicator queryProgressIndicator;
    
    // Editor Tab FXML components
    @FXML private TabPane editorTabPane;
    
    // Dataset Builder FXML components
    @FXML private TextField datasetIdField;
    @FXML private TextField datasetNameField;
    @FXML private TextArea datasetDescriptionField;
    @FXML private ListView<com.ospreydcs.dp.gui.model.DataBlockDetail> dataBlocksList;
    @FXML private Button removeDataBlockButton;
    @FXML private Button viewDataBlockButton;
    @FXML private Button resetDatasetButton;
    @FXML private Button saveDatasetButton;
    @FXML private Button addToAnnotationButton;
    @FXML private ComboBox<String> datasetActionsCombo;
    @FXML private Label datasetStatusLabel;
    
    // Annotation Builder FXML components
    @FXML private TextField annotationIdField;
    @FXML private TextField annotationNameField;
    @FXML private TextArea annotationCommentField;
    @FXML private TextField annotationEventNameField;
    @FXML private ListView<com.ospreydcs.dp.gui.model.DataSetDetail> targetDatasetsList;
    @FXML private Button removeTargetDatasetButton;
    @FXML private Button resetAnnotationButton;
    @FXML private Button saveAnnotationButton;
    @FXML private ComboBox<String> annotationActionsCombo;
    @FXML private Label annotationStatusLabel;
    
    // Calculations FXML components
    @FXML private ListView<com.ospreydcs.dp.gui.model.DataFrameDetails> calculationsDataFramesList;
    @FXML private Button importCalculationsButton;
    @FXML private Button viewCalculationsButton;
    @FXML private Button removeCalculationsButton;
    
    // Tags and Attributes container
    @FXML private HBox tagsAttributesContainer;
    
    // Programmatically created components
    private com.ospreydcs.dp.gui.component.TagsListComponent tagsComponent;
    private com.ospreydcs.dp.gui.component.AttributesListComponent attributesComponent;

    // Dependencies
    private DataExploreViewModel viewModel;
    private DatasetBuilderViewModel datasetBuilderViewModel;
    private AnnotationBuilderViewModel annotationBuilderViewModel;
    private DpApplication dpApplication;
    private Stage primaryStage;
    private MainController mainController;
    
    // Flag to prevent listener interference during initialization
    private boolean isInitializingFromGlobalState = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("DataExploreController initializing...");
        
        // Create the view models
        viewModel = new DataExploreViewModel();
        datasetBuilderViewModel = new DatasetBuilderViewModel();
        annotationBuilderViewModel = new AnnotationBuilderViewModel();
        
        // Initialize UI components
        initializeSpinners();
        initializeRadioButtons();
        initializeChart();
        initializeDatasetBuilder();
        initializeAnnotationBuilder();
        
        // Bind UI components to view model properties
        bindUIToViewModel();
        
        // Set up event handlers
        setupEventHandlers();
        setupDatasetActionsCombo();
        
        logger.debug("DataExploreController initialized successfully");
    }
    
    private void initializeSpinners() {
        // Initialize time spinners with proper value factories
        beginHourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 0));
        beginMinuteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));
        beginSecondSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));
        
        endHourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 0));
        endMinuteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));
        endSecondSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));
    }
    
    private void initializeRadioButtons() {
        // Create toggle group for search type radio buttons
        ToggleGroup searchTypeToggleGroup = new ToggleGroup();
        searchByNameListRadio.setToggleGroup(searchTypeToggleGroup);
        searchByPatternRadio.setToggleGroup(searchTypeToggleGroup);
        searchByNameListRadio.setSelected(true); // Default selection
    }
    
    private void initializeChart() {
        // Configure chart properties
        resultsChart.setTitle("PV Time-Series Data");
        resultsChart.setCreateSymbols(false); // Disable symbols for performance - even small queries create too many symbols
        resultsChart.setLegendSide(javafx.geometry.Side.RIGHT);
        
        // Configure axes
        chartXAxis.setLabel("Time (seconds from start)");
        chartYAxis.setLabel("Value");
        
        // Note: Initial visibility is set in FXML (chart hidden, placeholder visible)
        
        logger.debug("Chart initialized with title and axis labels");
    }
    
    private void initializeDatasetBuilder() {
        // Set up the Data Blocks ListView
        dataBlocksList.setItems(datasetBuilderViewModel.getDataBlocks());
        
        // Populate the Dataset Actions ComboBox
        datasetActionsCombo.getItems().addAll("Export CSV", "Export XLSX", "Export HDF5");
        
        logger.debug("Dataset Builder initialized");
    }
    
    private void initializeAnnotationBuilder() {
        // Set up the Target Datasets ListView
        targetDatasetsList.setItems(annotationBuilderViewModel.getDataSets());
        
        // Populate the Annotation Actions ComboBox
        annotationActionsCombo.getItems().addAll("Delete", "Export");
        
        // Create and initialize reusable components programmatically
        tagsComponent = new com.ospreydcs.dp.gui.component.TagsListComponent();
        attributesComponent = new com.ospreydcs.dp.gui.component.AttributesListComponent();
        
        // Bind component data to ViewModel
        tagsComponent.setTags(annotationBuilderViewModel.getTags());
        attributesComponent.setAttributes(annotationBuilderViewModel.getAttributes());
        
        // Add components to the container with proper sizing
        HBox.setHgrow(tagsComponent, Priority.ALWAYS);
        HBox.setHgrow(attributesComponent, Priority.ALWAYS);
        tagsAttributesContainer.getChildren().addAll(tagsComponent, attributesComponent);
        
        logger.debug("Annotation Builder initialized with programmatic components");
    }

    private void bindUIToViewModel() {
        // Query Specification bindings
        pvNamesList.setItems(viewModel.getPvNameList());
        specificationContent.visibleProperty().bind(viewModel.showQuerySpecificationPanelProperty());
        specificationContent.managedProperty().bind(viewModel.showQuerySpecificationPanelProperty());
        
        // Query Results bindings
        resultsContent.visibleProperty().bind(viewModel.showQueryResultsPanelProperty());
        resultsContent.managedProperty().bind(viewModel.showQueryResultsPanelProperty());
        
        // Fix layout issue: when Query Results section is hidden, remove VGrow constraint so upper section can expand
        viewModel.showQueryResultsPanelProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                // Query Results visible: set VGrow to ALWAYS so it expands
                VBox.setVgrow(queryResultsSection, Priority.ALWAYS);
            } else {
                // Query Results hidden: remove VGrow so upper section can use the space
                VBox.setVgrow(queryResultsSection, Priority.NEVER);
            }
            
            // Force layout recalculation to ensure space is properly redistributed
            // This is critical for window resize scenarios
            queryResultsSection.getParent().requestLayout();
        });
        
        // Initialize the VGrow state based on current visibility
        if (viewModel.showQueryResultsPanelProperty().get()) {
            VBox.setVgrow(queryResultsSection, Priority.ALWAYS);
        } else {
            VBox.setVgrow(queryResultsSection, Priority.NEVER);
        }
        
        // Time range bindings
        queryBeginDatePicker.valueProperty().bindBidirectional(viewModel.queryBeginDateProperty());
        queryEndDatePicker.valueProperty().bindBidirectional(viewModel.queryEndDateProperty());
        
        // Set up spinner bindings
        setupSpinnerBinding(beginHourSpinner, viewModel.beginHourProperty(), "beginHour");
        setupSpinnerBinding(beginMinuteSpinner, viewModel.beginMinuteProperty(), "beginMinute");
        setupSpinnerBinding(beginSecondSpinner, viewModel.beginSecondProperty(), "beginSecond");
        setupSpinnerBinding(endHourSpinner, viewModel.endHourProperty(), "endHour");
        setupSpinnerBinding(endMinuteSpinner, viewModel.endMinuteProperty(), "endMinute");
        setupSpinnerBinding(endSecondSpinner, viewModel.endSecondProperty(), "endSecond");
        
        // PV Search Panel bindings
        pvSearchPanel.visibleProperty().bind(viewModel.showPvSearchPanelProperty());
        pvSearchPanel.managedProperty().bind(viewModel.showPvSearchPanelProperty());
        pvSearchTextField.textProperty().bindBidirectional(viewModel.pvSearchTextProperty());
        // Handle radio button selection properly without binding conflicts
        searchByNameListRadio.selectedProperty().bindBidirectional(viewModel.searchByNameListProperty());
        // Don't bind the second radio button - let the ToggleGroup handle mutual exclusion
        
        // Update ViewModel when pattern radio button is selected
        searchByPatternRadio.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                viewModel.searchByNameListProperty().set(false);
            }
        });
        searchResultsList.setItems(viewModel.getSearchResultPvNames());
        
        
        // Button state bindings
        submitQueryButton.disableProperty().bind(viewModel.isQueryingProperty().or(viewModel.isQueryValidProperty().not()));
        addToDatasetButton.disableProperty().bind(viewModel.isQueryingProperty().or(viewModel.isQueryValidProperty().not()));
        searchPvButton.disableProperty().bind(viewModel.isSearchingProperty());
        addPvButton.disableProperty().bind(viewModel.isQueryingProperty());
        removePvButton.disableProperty().bind(viewModel.isQueryingProperty());
        addSelectedPvButton.disableProperty().bind(viewModel.isSearchingProperty());
        
        // Status and progress bindings
        queryStatusLabel.textProperty().bind(viewModel.statusMessageProperty());
        searchStatusLabel.textProperty().bind(viewModel.statusMessageProperty());
        resultsStatusLabel.textProperty().bind(viewModel.statusMessageProperty());
        queryProgressIndicator.visibleProperty().bind(viewModel.isQueryingProperty());
        
        // Results table bindings
        resultsTable.setItems(viewModel.getTableData());
        
        // Row count binding
        viewModel.totalRowsLoadedProperty().addListener((obs, oldVal, newVal) -> {
            rowCountLabel.setText(newVal.intValue() + " rows");
        });
        
        // Search result count binding
        viewModel.getSearchResultPvNames().addListener((javafx.collections.ListChangeListener<String>) change -> {
            int size = viewModel.getSearchResultPvNames().size();
            searchResultCountLabel.setText(size + " result(s) found");
        });
        
        // Dataset Builder bindings
        datasetIdField.textProperty().bindBidirectional(datasetBuilderViewModel.datasetIdProperty());
        datasetNameField.textProperty().bindBidirectional(datasetBuilderViewModel.datasetNameProperty());
        datasetDescriptionField.textProperty().bindBidirectional(datasetBuilderViewModel.datasetDescriptionProperty());
        datasetStatusLabel.textProperty().bind(datasetBuilderViewModel.statusMessageProperty());
        
        // Button state bindings
        resetDatasetButton.disableProperty().bind(datasetBuilderViewModel.resetButtonEnabledProperty().not());
        saveDatasetButton.disableProperty().bind(datasetBuilderViewModel.saveButtonEnabledProperty().not());
        addToAnnotationButton.disableProperty().bind(datasetBuilderViewModel.datasetIdProperty().isEmpty());
        // Enable Dataset Actions ComboBox when dataset has a non-null ID (same logic as Add to Annotation button)
        datasetActionsCombo.disableProperty().bind(datasetBuilderViewModel.datasetIdProperty().isEmpty());
        
        // Data blocks control buttons - enable when there's a selection
        removeDataBlockButton.disableProperty().bind(dataBlocksList.getSelectionModel().selectedItemProperty().isNull());
        viewDataBlockButton.disableProperty().bind(dataBlocksList.getSelectionModel().selectedItemProperty().isNull());
        
        // Target datasets control buttons - enable when there's a selection
        removeTargetDatasetButton.disableProperty().bind(targetDatasetsList.getSelectionModel().selectedItemProperty().isNull());
        
        // Annotation Builder bindings
        annotationIdField.textProperty().bindBidirectional(annotationBuilderViewModel.annotationIdProperty());
        annotationNameField.textProperty().bindBidirectional(annotationBuilderViewModel.annotationNameProperty());
        annotationCommentField.textProperty().bindBidirectional(annotationBuilderViewModel.commentProperty());
        annotationEventNameField.textProperty().bindBidirectional(annotationBuilderViewModel.eventNameProperty());
        annotationStatusLabel.textProperty().bind(annotationBuilderViewModel.statusMessageProperty());
        
        // Annotation Button state bindings
        resetAnnotationButton.disableProperty().bind(annotationBuilderViewModel.resetButtonEnabledProperty().not());
        saveAnnotationButton.disableProperty().bind(annotationBuilderViewModel.saveButtonEnabledProperty().not());
        annotationActionsCombo.disableProperty().bind(annotationBuilderViewModel.annotationActionsEnabledProperty().not());
        
        // Calculations bindings
        calculationsDataFramesList.setItems(annotationBuilderViewModel.getCalculationsDataFrames());
        viewCalculationsButton.disableProperty().bind(calculationsDataFramesList.getSelectionModel().selectedItemProperty().isNull());
        removeCalculationsButton.disableProperty().bind(calculationsDataFramesList.getSelectionModel().selectedItemProperty().isNull());
    }

    private void setupEventHandlers() {
        // Set up context menu for PV names list
        pvNamesList.setCellFactory(listView -> {
            ListCell<String> cell = new ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            
            ContextMenu contextMenu = new ContextMenu();
            MenuItem deleteItem = new MenuItem("Remove");
            deleteItem.setOnAction(e -> {
                if (cell.getItem() != null) {
                    viewModel.removePvName(cell.getItem());
                    // Update global state when PV is removed
                    updateGlobalPvNames();
                }
            });
            contextMenu.getItems().add(deleteItem);
            
            cell.setContextMenu(contextMenu);
            return cell;
        });
        
        // Listen to PV list changes to update global state
        viewModel.getPvNameList().addListener((javafx.collections.ListChangeListener<String>) change -> {
            updateGlobalPvNames();
        });
        
        // Listen to time range changes to update global state  
        queryBeginDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!isInitializingFromGlobalState) updateGlobalTimeRange();
        });
        queryEndDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!isInitializingFromGlobalState) updateGlobalTimeRange();
        });
        beginHourSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!isInitializingFromGlobalState) updateGlobalTimeRange();
        });
        beginMinuteSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!isInitializingFromGlobalState) updateGlobalTimeRange();
        });
        beginSecondSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!isInitializingFromGlobalState) updateGlobalTimeRange();
        });
        endHourSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!isInitializingFromGlobalState) updateGlobalTimeRange();
        });
        endMinuteSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!isInitializingFromGlobalState) updateGlobalTimeRange();
        });
        endSecondSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!isInitializingFromGlobalState) updateGlobalTimeRange();
        });
        
        // Set up selection handling for search results
        searchResultsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        searchResultsList.getSelectionModel().getSelectedItems().addListener(
            (javafx.collections.ListChangeListener<String>) change -> {
                viewModel.getSelectedSearchResults().setAll(
                    searchResultsList.getSelectionModel().getSelectedItems());
            });
        
        // Set up table columns when column names are available
        viewModel.getTableColumnNames().addListener((javafx.collections.ListChangeListener<String>) change -> {
            if (change.next() && change.wasAdded()) {
                setupTableColumns();
                setupChart();
            }
        });
        
        // Set up chart data updates when table data changes
        viewModel.getTableData().addListener((javafx.collections.ListChangeListener<ObservableList<Object>>) change -> {
            updateChart();
        });
    }
    
    private void setupTableColumns() {
        resultsTable.getColumns().clear();
        
        ObservableList<String> columnNames = viewModel.getTableColumnNames();
        for (int i = 0; i < columnNames.size(); i++) {
            final int columnIndex = i;
            String columnName = columnNames.get(i);
            
            TableColumn<ObservableList<Object>, Object> column = new TableColumn<>(columnName);
            column.setCellValueFactory(param -> {
                ObservableList<Object> row = param.getValue();
                if (row != null && columnIndex < row.size()) {
                    return new javafx.beans.property.SimpleObjectProperty<>(row.get(columnIndex));
                }
                return new javafx.beans.property.SimpleObjectProperty<>("N/A");
            });
            
            column.setPrefWidth(columnName.equals("timestamp") ? 180 : 100);
            resultsTable.getColumns().add(column);
        }
        
        logger.debug("Table columns set up for {} columns", columnNames.size());
    }
    
    private void setupChart() {
        logger.debug("setupChart() called with column names: {}", viewModel.getTableColumnNames());
        resultsChart.getData().clear();
        
        ObservableList<String> columnNames = viewModel.getTableColumnNames();
        if (columnNames.isEmpty()) {
            logger.debug("No column names available, skipping chart setup");
            return;
        }
        
        // Create a series for each PV (skip timestamp column)
        int seriesCount = 0;
        for (String columnName : columnNames) {
            if (!columnName.equals("timestamp")) {
                XYChart.Series<Number, Number> series = new XYChart.Series<>();
                series.setName(columnName);
                resultsChart.getData().add(series);
                seriesCount++;
                logger.debug("Added series for PV: {}", columnName);
            }
        }
        
        logger.debug("Chart set up for {} PV series out of {} total columns", seriesCount, columnNames.size());
    }
    
    private void updateChart() {
        logger.debug("updateChart() called - chart has {} series", resultsChart.getData().size());
        
        if (resultsChart.getData().isEmpty()) {
            logger.debug("Chart data is empty, skipping update");
            return;
        }
        
        ObservableList<String> columnNames = viewModel.getTableColumnNames();
        ObservableList<ObservableList<Object>> tableData = viewModel.getTableData();
        
        logger.debug("updateChart() - columnNames: {}, tableData rows: {}", columnNames, tableData.size());
        
        if (columnNames.isEmpty() || tableData.isEmpty()) {
            logger.debug("No column names or table data, showing placeholder");
            showChartPlaceholder(true);
            return;
        }
        
        // Clear existing data points
        for (XYChart.Series<Number, Number> series : resultsChart.getData()) {
            series.getData().clear();
        }
        
        // Reset the NumberAxis range for proper scaling
        chartXAxis.setAutoRanging(true);
        chartYAxis.setAutoRanging(true);
        
        // Find timestamp column index
        int timestampIndex = -1;
        for (int i = 0; i < columnNames.size(); i++) {
            if (columnNames.get(i).equals("timestamp")) {
                timestampIndex = i;
                break;
            }
        }
        
        if (timestampIndex == -1) {
            logger.warn("No timestamp column found for chart");
            showChartPlaceholder(true);
            return;
        }
        
        // Calculate dynamic sample interval based on time range and data density
        int totalRows = tableData.size();
        int sampleInterval = calculateDynamicSampleInterval(tableData, timestampIndex, totalRows);
        
        logger.info("Processing {} rows with sample interval {}, timestamp column at index {}", totalRows, sampleInterval, timestampIndex);
        
        // Debug first few timestamps to understand data structure
        if (totalRows > 0) {
            for (int i = 0; i < Math.min(5, totalRows); i++) {
                ObservableList<Object> row = tableData.get(i);
                if (row.size() > timestampIndex) {
                    Object timestampObj = row.get(timestampIndex);
                    Double parsedSeconds = parseTimestampToSeconds(timestampObj);
                    logger.info("Sample row {}: timestamp = {}, parsed as {} seconds", i, timestampObj, parsedSeconds);
                }
            }
        }
        
        // Find the start time for relative time calculation
        Double startTimeSeconds = null;
        if (totalRows > 0) {
            ObservableList<Object> firstRow = tableData.get(0);
            if (firstRow.size() > timestampIndex) {
                startTimeSeconds = parseTimestampToSeconds(firstRow.get(timestampIndex));
            }
        }
        
        if (startTimeSeconds == null) {
            logger.warn("Could not determine start time for chart");
            showChartPlaceholder(true);
            return;
        }
        
        int dataPointsAdded = 0;
        // Populate chart with sampled data
        for (int rowIndex = 0; rowIndex < totalRows; rowIndex += sampleInterval) {
            ObservableList<Object> row = tableData.get(rowIndex);
            if (row.size() <= timestampIndex) {
                continue;
            }
            
            Double timeSeconds = parseTimestampToSeconds(row.get(timestampIndex));
            if (timeSeconds == null) {
                continue;
            }
            
            // Calculate relative time from start
            double relativeTimeSeconds = timeSeconds - startTimeSeconds;
            
            // Debug first few data points to see relative times
            if (dataPointsAdded < 5) {
                logger.info("Data point {}: absolute time={}, start time={}, relative time={} seconds", 
                           dataPointsAdded, timeSeconds, startTimeSeconds, relativeTimeSeconds);
            }
            
            // Add data points for each PV series
            int seriesIndex = 0;
            for (int colIndex = 0; colIndex < columnNames.size(); colIndex++) {
                if (colIndex == timestampIndex) {
                    continue; // Skip timestamp column
                }
                
                if (seriesIndex < resultsChart.getData().size() && colIndex < row.size()) {
                    Object value = row.get(colIndex);
                    Number numericValue = parseNumericValue(value);
                    
                    if (numericValue != null) {
                        XYChart.Series<Number, Number> series = resultsChart.getData().get(seriesIndex);
                        XYChart.Data<Number, Number> dataPoint = new XYChart.Data<>(relativeTimeSeconds, numericValue);
                        
                        // Store original data for tooltip
                        Object originalTimestamp = row.get(timestampIndex);
                        dataPoint.setExtraValue(new DataPointInfo(originalTimestamp, value, columnNames.get(colIndex)));
                        
                        series.getData().add(dataPoint);
                        dataPointsAdded++;
                    }
                }
                seriesIndex++;
            }
        }
        
        // Configure tick units for better alignment after data is added
        configureAxisTicks(startTimeSeconds, totalRows);
        
        showChartPlaceholder(false);
        
        // Set up chart area mouse tracking for tooltips (no symbols needed)
        setupChartMouseTracking();
        
        logger.debug("Chart updated with {} sampled data points", totalRows / Math.max(1, sampleInterval));
    }
    
    private String formatTimestampForChart(Object timestampValue) {
        if (timestampValue == null) {
            return "N/A";
        }
        
        String timestamp = timestampValue.toString();
        // Simplify timestamp format for chart display (show only time part if same date)
        if (timestamp.contains("T")) {
            String[] parts = timestamp.split("T");
            if (parts.length > 1) {
                return parts[1].substring(0, Math.min(8, parts[1].length())); // HH:mm:ss
            }
        }
        
        return timestamp;
    }
    
    private Number parseNumericValue(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Number) {
            return (Number) value;
        }
        
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private Double parseTimestampToSeconds(Object timestampValue) {
        if (timestampValue == null) {
            return null;
        }
        
        String timestamp = timestampValue.toString();
        try {
            // Try to parse ISO format: 2024-01-01T10:30:45.123456
            if (timestamp.contains("T")) {
                LocalDateTime dateTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                // Include fractional seconds for sub-second precision
                long epochSecond = dateTime.toEpochSecond(ZoneOffset.UTC);
                int nanoOfSecond = dateTime.getNano();
                return epochSecond + (nanoOfSecond / 1_000_000_000.0);
            }
            
            // Fallback: try to parse as double (epoch seconds)
            return Double.parseDouble(timestamp);
        } catch (Exception e) {
            logger.warn("Could not parse timestamp '{}': {}", timestamp, e.getMessage());
            return null;
        }
    }
    
    private void configureAxisTicks(Double startTimeSeconds, int totalRows) {
        if (startTimeSeconds == null || totalRows == 0) {
            return;
        }
        
        // Configure X-axis (time) tick units based on time range
        ObservableList<ObservableList<Object>> tableData = viewModel.getTableData();
        if (tableData.size() > 1) {
            // Find the time range in seconds
            ObservableList<Object> lastRow = tableData.get(tableData.size() - 1);
            ObservableList<String> columnNames = viewModel.getTableColumnNames();
            
            int timestampIndex = -1;
            for (int i = 0; i < columnNames.size(); i++) {
                if (columnNames.get(i).equals("timestamp")) {
                    timestampIndex = i;
                    break;
                }
            }
            
            if (timestampIndex != -1 && lastRow.size() > timestampIndex) {
                Double endTimeSeconds = parseTimestampToSeconds(lastRow.get(timestampIndex));
                if (endTimeSeconds != null) {
                    double timeRangeSeconds = endTimeSeconds - startTimeSeconds;
                    
                    // Set appropriate tick units based on time range
                    double xTickUnit = calculateOptimalTickUnit(timeRangeSeconds, 8); // Target ~8 ticks
                    chartXAxis.setTickUnit(xTickUnit);
                    chartXAxis.setAutoRanging(false);
                    chartXAxis.setLowerBound(0);
                    chartXAxis.setUpperBound(timeRangeSeconds);
                    
                    logger.info("Configured X-axis: range={} seconds, tick unit={}, showing {} actual data points", 
                               timeRangeSeconds, xTickUnit, totalRows);
                }
            }
        }
        
        // Configure Y-axis tick units based on data range
        configureYAxisTicks();
    }
    
    private double calculateOptimalTickUnit(double range, int targetTicks) {
        if (range <= 0 || targetTicks <= 0) {
            return 1.0;
        }
        
        double roughTickUnit = range / targetTicks;
        
        // For very short ranges (< 10 seconds), use smaller tick units
        if (range <= 10.0) {
            // Use fractional seconds for very short time ranges
            if (roughTickUnit < 0.1) {
                return 0.1; // 100ms ticks
            } else if (roughTickUnit < 0.2) {
                return 0.2; // 200ms ticks
            } else if (roughTickUnit < 0.5) {
                return 0.5; // 500ms ticks
            } else {
                return 1.0; // 1s ticks
            }
        }
        
        // Round to nice numbers (1, 2, 5, 10, 20, 50, 100, etc.) for longer ranges
        double magnitude = Math.pow(10, Math.floor(Math.log10(roughTickUnit)));
        double normalizedUnit = roughTickUnit / magnitude;
        
        double niceUnit;
        if (normalizedUnit <= 1.0) {
            niceUnit = 1.0;
        } else if (normalizedUnit <= 2.0) {
            niceUnit = 2.0;
        } else if (normalizedUnit <= 5.0) {
            niceUnit = 5.0;
        } else {
            niceUnit = 10.0;
        }
        
        return niceUnit * magnitude;
    }
    
    private void configureYAxisTicks() {
        // Find the Y-axis data range
        double minY = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;
        
        for (XYChart.Series<Number, Number> series : resultsChart.getData()) {
            for (XYChart.Data<Number, Number> dataPoint : series.getData()) {
                double value = dataPoint.getYValue().doubleValue();
                minY = Math.min(minY, value);
                maxY = Math.max(maxY, value);
            }
        }
        
        if (minY != Double.MAX_VALUE && maxY != Double.MIN_VALUE && maxY > minY) {
            double range = maxY - minY;
            double yTickUnit = calculateOptimalTickUnit(range, 6); // Target ~6 ticks
            
            // Add some padding to the range
            double padding = range * 0.1;
            double lowerBound = minY - padding;
            double upperBound = maxY + padding;
            
            chartYAxis.setTickUnit(yTickUnit);
            chartYAxis.setAutoRanging(false);
            chartYAxis.setLowerBound(lowerBound);
            chartYAxis.setUpperBound(upperBound);
            
            logger.debug("Configured Y-axis: range={} to {}, tick unit={}", lowerBound, upperBound, yTickUnit);
        }
    }
    
    private int calculateDynamicSampleInterval(ObservableList<ObservableList<Object>> tableData, int timestampIndex, int totalRows) {
        if (totalRows <= 1000) {
            logger.info("Dynamic sampling: {} total rows <= 1000, showing all data points (interval=1)", totalRows);
            return 1; // Show all data points for small datasets
        }
        
        // Calculate time range to determine appropriate sampling
        ObservableList<Object> firstRow = tableData.get(0);
        ObservableList<Object> lastRow = tableData.get(totalRows - 1);
        
        if (firstRow.size() <= timestampIndex || lastRow.size() <= timestampIndex) {
            return Math.max(1, totalRows / 1000); // Fallback to row-based sampling
        }
        
        Double startTime = parseTimestampToSeconds(firstRow.get(timestampIndex));
        Double endTime = parseTimestampToSeconds(lastRow.get(timestampIndex));
        
        if (startTime == null || endTime == null) {
            return Math.max(1, totalRows / 1000); // Fallback to row-based sampling
        }
        
        double timeRangeSeconds = endTime - startTime;
        
        // Dynamic sampling based on time range:
        // - For short ranges (< 60s): show more detail
        // - For medium ranges (1-10 min): moderate sampling
        // - For long ranges (> 10 min): more aggressive sampling
        int targetPoints;
        if (timeRangeSeconds < 60) {
            targetPoints = 2000; // High detail for sub-minute ranges
        } else if (timeRangeSeconds < 600) { // < 10 minutes
            targetPoints = 1500; // Medium detail
        } else if (timeRangeSeconds < 3600) { // < 1 hour
            targetPoints = 1000; // Standard detail
        } else {
            targetPoints = 500; // Lower detail for long ranges
        }
        
        int sampleInterval = Math.max(1, totalRows / targetPoints);
        logger.info("Dynamic sampling: {} seconds range, {} total rows, {} target points, interval={} (will show ~{} points)", 
                    timeRangeSeconds, totalRows, targetPoints, sampleInterval, totalRows / sampleInterval);
        
        return sampleInterval;
    }
    
    private void showChartPlaceholder(boolean show) {
        chartPlaceholder.setVisible(show);
        chartPlaceholder.setManaged(show);
        resultsChart.setVisible(!show);
        resultsChart.setManaged(!show);
    }
    
    private void setupSpinnerBinding(Spinner<Integer> spinner, javafx.beans.property.IntegerProperty viewModelProperty, String name) {
        if (spinner.getValueFactory() == null) {
            logger.error("{} spinner value factory is null!", name);
            return;
        }
        
        logger.debug("Setting up binding for {} spinner", name);
        
        // Initialize ViewModel property from spinner value
        viewModelProperty.set(spinner.getValue());
        
        // Listen for changes in spinner and update ViewModel
        spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                viewModelProperty.set(newVal);
            }
        });
        
        // Listen for changes in ViewModel and update spinner
        viewModelProperty.addListener((obs, oldVal, newVal) -> {
            if (!newVal.equals(spinner.getValue())) {
                spinner.getValueFactory().setValue(newVal.intValue());
            }
        });
    }
    
    private void setupStatusListener() {
        // Connect ViewModel status messages to MainController status display
        if (viewModel != null && mainController != null) {
            viewModel.statusMessageProperty().addListener((obs, oldStatus, newStatus) -> {
                if (newStatus != null && !newStatus.trim().isEmpty()) {
                    mainController.getViewModel().updateStatus(newStatus);
                }
            });
            logger.debug("Status listener established between DataExploreViewModel and MainController");
        }
    }

    // Dependency injection methods
    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        
        // Initialize UI from existing global state BEFORE injecting into ViewModel
        // This prevents the listeners from overwriting the global state during initialization
        logger.debug("CRAIG DEBUG: About to call initializeUIFromGlobalState()");
        initializeUIFromGlobalState();
        logger.debug("CRAIG DEBUG: Finished calling initializeUIFromGlobalState()");
        
        if (viewModel != null) {
            viewModel.setDpApplication(dpApplication);
        }
        
        logger.debug("DpApplication injected into DataExploreController");
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        logger.debug("Primary stage injected into DataExploreController");
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        
        // Inject MainController into ViewModel for home view updates
        if (viewModel != null) {
            viewModel.setMainController(mainController);
        }
        
        // Set up status message forwarding
        setupStatusListener();
        
        logger.debug("MainController injected into DataExploreController");
    }

    // Event handler methods
    @FXML
    private void onToggleQuerySpecification() {
        viewModel.toggleQuerySpecificationPanel();
        
        // Update button text based on panel visibility
        boolean isVisible = viewModel.showQuerySpecificationPanelProperty().get();
        toggleSpecificationButton.setText(isVisible ? "📋 Data Explorer Tools" : "📋 Data Explorer Tools (Hidden)");
        
        logger.debug("Query specification panel toggled: {}", isVisible ? "visible" : "hidden");
    }
    
    @FXML
    private void onToggleQueryResults() {
        viewModel.toggleQueryResultsPanel();
        
        // Update button text based on panel visibility
        boolean isVisible = viewModel.showQueryResultsPanelProperty().get();
        toggleResultsButton.setText(isVisible ? "📊 Data Viewer" : "📊 Data Viewer (Hidden)");
        
        logger.debug("Query results panel toggled: {}", isVisible ? "visible" : "hidden");
    }
    
    @FXML
    private void onAddPv() {
        viewModel.showPvSearchPanel();
        logger.debug("PV search panel opened");
    }
    
    @FXML
    private void onRemovePv() {
        String selectedPv = pvNamesList.getSelectionModel().getSelectedItem();
        if (selectedPv != null) {
            viewModel.removePvName(selectedPv);
            logger.debug("Removed PV from query list: {}", selectedPv);
        } else {
            viewModel.updateStatus("Please select a PV to remove");
        }
    }
    
    @FXML
    private void onSearchPv() {
        logger.info("PV metadata search requested");
        viewModel.searchPvMetadata();
    }
    
    @FXML
    private void onClosePvSearch() {
        viewModel.hidePvSearchPanel();
        logger.debug("PV search panel closed");
    }
    
    @FXML
    private void onAddSelectedPv() {
        ObservableList<String> selectedItems = searchResultsList.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) {
            viewModel.updateStatus("Please select PV names from the search results");
            return;
        }
        
        viewModel.getSelectedSearchResults().setAll(selectedItems);
        viewModel.addSelectedSearchResultsToPvList();
        
        // Update global state after adding PVs (the listener will handle this automatically)
        logger.info("Added {} selected PVs to query list", selectedItems.size());
    }
    
    @FXML
    private void onSubmitQuery() {
        logger.info("Query submission requested");
        
        // Update global state before executing query
        updateGlobalQueryState();
        
        viewModel.submitQuery();
    }
    
    @FXML
    private void onAddToDataset() {
        logger.info("Add to Dataset requested");
        
        // Update global state and validate query data
        updateGlobalQueryState();
        
        // Create DataBlockDetail from current form data
        com.ospreydcs.dp.gui.model.DataBlockDetail dataBlock = createDataBlockFromQueryEditor();
        if (dataBlock != null) {
            // Add to dataset builder
            datasetBuilderViewModel.addDataBlock(dataBlock);
            
            // Switch to Dataset Builder tab
            editorTabPane.getSelectionModel().select(1); // Switch to index 1 (Dataset Builder tab)
            
            viewModel.updateStatus("Data block added to dataset");
            logger.info("Successfully added data block to dataset: {}", dataBlock);
        } else {
            viewModel.updateStatus("Failed to create data block - please check your input");
            logger.warn("Failed to create data block from query editor form");
        }
    }
    
    @FXML
    private void onCancelQuery() {
        logger.info("Query cancelled by user");
        
        // Commit any pending spinner edits and update global state before cancelling
        logger.debug("CRAIG DEBUG: About to call updateGlobalQueryState()");
        updateGlobalQueryState();
        logger.debug("CRAIG DEBUG: Finished calling updateGlobalQueryState()");
        
        viewModel.cancel();
        
        // Navigate back to main window
        if (mainController != null) {
            mainController.switchToMainView();
        } else {
            logger.warn("MainController reference is null, cannot navigate back");
        }
    }
    
    // Dataset Builder event handlers
    @FXML
    private void onResetDataset() {
        logger.info("Dataset reset requested");
        datasetBuilderViewModel.resetDataset();
    }
    
    @FXML
    private void onSaveDataset() {
        logger.info("Dataset save requested");
        
        // Step 1: Validation
        String name = datasetBuilderViewModel.getDatasetName();
        if (name == null || name.trim().isEmpty()) {
            datasetBuilderViewModel.statusMessageProperty().set("Dataset name is required");
            logger.warn("Dataset save validation failed: name is empty");
            return;
        }
        
        if (datasetBuilderViewModel.getDataBlocks().isEmpty()) {
            datasetBuilderViewModel.statusMessageProperty().set("Data blocks are required - use Query Editor to add data blocks");
            logger.warn("Dataset save validation failed: no data blocks");
            return;
        }
        
        // Step 2: Extract dataset details
        String id = datasetBuilderViewModel.getDatasetId();
        String description = datasetBuilderViewModel.getDatasetDescription();
        var dataBlocks = new java.util.ArrayList<>(datasetBuilderViewModel.getDataBlocks());
        
        logger.info("Saving dataset: id={}, name={}, description={}, dataBlocks={}", 
                   id, name, description, dataBlocks.size());
        
        // Step 3: Call DpApplication.saveDataSet() method
        try {
            com.ospreydcs.dp.client.result.SaveDataSetApiResult apiResult = 
                dpApplication.saveDataSet(id, name, description, dataBlocks);
            
            if (apiResult == null) {
                datasetBuilderViewModel.statusMessageProperty().set("Save failed - null response from service");
                logger.error("Dataset save failed: null API result");
                return;
            }
            
            // Step 4: Handle API result
            if (apiResult.resultStatus.isError) {
                // Error case
                String errorMessage = "Save failed: " + apiResult.resultStatus.msg;
                datasetBuilderViewModel.statusMessageProperty().set(errorMessage);
                logger.error("Dataset save failed: {}", apiResult.resultStatus.msg);
            } else {
                // Success case - extract the dataset ID and update the field
                if (apiResult.datasetId != null && !apiResult.datasetId.trim().isEmpty()) {
                    datasetBuilderViewModel.setDatasetId(apiResult.datasetId);
                    
                    String successMessage = "Dataset saved successfully with ID: " + apiResult.datasetId;
                    datasetBuilderViewModel.statusMessageProperty().set(successMessage);
                    logger.info("Dataset save completed successfully: {}", apiResult.datasetId);
                } else {
                    // This shouldn't happen for successful saves, but handle gracefully
                    String successMessage = "Dataset saved successfully";
                    datasetBuilderViewModel.statusMessageProperty().set(successMessage);
                    logger.warn("Dataset save completed but no ID returned");
                }
            }
            
        } catch (Exception e) {
            String errorMessage = "Save failed with exception: " + e.getMessage();
            datasetBuilderViewModel.statusMessageProperty().set(errorMessage);
            logger.error("Dataset save failed with exception", e);
        }
    }
    
    @FXML
    private void onAddToAnnotation() {
        logger.info("Add to Annotation requested");
        
        // Extract current Dataset Builder form values
        String id = datasetBuilderViewModel.getDatasetId();
        String name = datasetBuilderViewModel.getDatasetName();
        String description = datasetBuilderViewModel.getDatasetDescription();
        var dataBlocks = new ArrayList<>(datasetBuilderViewModel.getDataBlocks());
        
        logger.info("Creating DataSetDetail: id={}, name={}, description={}, dataBlocks={}", 
                   id, name, description, dataBlocks.size());
        
        // Create new DataSetDetail object
        com.ospreydcs.dp.gui.model.DataSetDetail dataSetDetail = 
            new com.ospreydcs.dp.gui.model.DataSetDetail(id, name, description, dataBlocks);
        
        // Check for duplicates by ID and handle appropriately
        boolean isDuplicate = false;
        for (int i = 0; i < annotationBuilderViewModel.getDataSets().size(); i++) {
            com.ospreydcs.dp.gui.model.DataSetDetail existing = annotationBuilderViewModel.getDataSets().get(i);
            if (existing.getId() != null && existing.getId().equals(id)) {
                // Update existing entry instead of adding duplicate
                annotationBuilderViewModel.getDataSets().set(i, dataSetDetail);
                isDuplicate = true;
                logger.info("Updated existing dataset in Annotation Builder: {}", id);
                break;
            }
        }
        
        // Add to Annotation Builder Target Datasets if not a duplicate
        if (!isDuplicate) {
            annotationBuilderViewModel.getDataSets().add(dataSetDetail);
            logger.info("Added new dataset to Annotation Builder: {}", id);
        }
        
        // Switch to Annotation Builder tab (index 2 = third tab)
        editorTabPane.getSelectionModel().select(2);
        logger.info("Switched to Annotation Builder tab");
    }
    
    @FXML
    private void onRemoveDataBlock() {
        com.ospreydcs.dp.gui.model.DataBlockDetail selectedBlock = dataBlocksList.getSelectionModel().getSelectedItem();
        if (selectedBlock != null) {
            datasetBuilderViewModel.removeDataBlock(selectedBlock);
            logger.info("Removed data block: {}", selectedBlock);
            datasetBuilderViewModel.statusMessageProperty().set("Data block removed");
        } else {
            logger.warn("Remove data block requested but no selection");
        }
    }
    
    @FXML
    private void onViewDataBlock() {
        com.ospreydcs.dp.gui.model.DataBlockDetail selectedBlock = dataBlocksList.getSelectionModel().getSelectedItem();
        if (selectedBlock != null) {
            logger.info("View data requested for data block: {}", selectedBlock);
            
            // Populate Query Editor with data block details
            viewModel.populateFromDataBlock(selectedBlock);
            
            // Switch to Query Editor tab (index 0)
            editorTabPane.getSelectionModel().select(0);
            
            datasetBuilderViewModel.statusMessageProperty().set("Data block loaded in Query Editor");
        } else {
            logger.warn("View data block requested but no selection");
        }
    }
    
    // Annotation Builder event handlers
    
    @FXML
    private void onRemoveTargetDataset() {
        com.ospreydcs.dp.gui.model.DataSetDetail selectedDataset = targetDatasetsList.getSelectionModel().getSelectedItem();
        if (selectedDataset != null) {
            // Store the current selected index before removal
            int selectedIndex = targetDatasetsList.getSelectionModel().getSelectedIndex();
            
            // Remove the dataset
            annotationBuilderViewModel.getDataSets().remove(selectedDataset);
            logger.info("Removed target dataset: {} (ID: {})", selectedDataset.getName(), selectedDataset.getId());
            
            // Manage focus to prevent unwanted scrolling
            if (!annotationBuilderViewModel.getDataSets().isEmpty()) {
                // If there are remaining items, select the next logical item
                int newIndex = Math.min(selectedIndex, annotationBuilderViewModel.getDataSets().size() - 1);
                targetDatasetsList.getSelectionModel().select(newIndex);
                // Keep focus on the ListView to prevent focus transfer
                targetDatasetsList.requestFocus();
            } else {
                // If no items remain, clear selection but keep focus on ListView
                targetDatasetsList.getSelectionModel().clearSelection();
                targetDatasetsList.requestFocus();
            }
        } else {
            logger.warn("Remove target dataset requested but no dataset selected");
        }
    }
    
    @FXML
    private void onResetAnnotation() {
        logger.info("Annotation reset requested");
        annotationBuilderViewModel.resetAnnotation();
    }
    
    @FXML
    private void onSaveAnnotation() {
        logger.info("Annotation save requested");
        
        // Step 1: Validation
        String name = annotationBuilderViewModel.getAnnotationName();
        if (name == null || name.trim().isEmpty()) {
            annotationBuilderViewModel.statusMessageProperty().set("Annotation name is required");
            logger.warn("Annotation save validation failed: name is empty");
            return;
        }
        
        if (annotationBuilderViewModel.getDataSets().isEmpty()) {
            annotationBuilderViewModel.statusMessageProperty().set("Target datasets are required");
            logger.warn("Annotation save validation failed: no target datasets");
            return;
        }
        
        // Step 2: Extract annotation details
        String id = annotationBuilderViewModel.getAnnotationId();
        String comment = annotationBuilderViewModel.getComment();
        String eventName = annotationBuilderViewModel.getEventName();
        var dataSets = new java.util.ArrayList<>(annotationBuilderViewModel.getDataSets());
        var tags = new java.util.ArrayList<>(tagsComponent.getTags());
        var attributes = new java.util.ArrayList<>(attributesComponent.getAttributes());
        
        logger.info("Saving annotation: id={}, name={}, comment={}, eventName={}, dataSets={}, tags={}, attributes={}", 
                   id, name, comment, eventName, dataSets.size(), tags.size(), attributes.size());
        
        // Step 3: Convert attributes list to Map<String, String>
        Map<String, String> attributeMap = new HashMap<>();
        for (String attribute : attributes) {
            if (attribute != null && attribute.contains("=")) {
                String key = com.ospreydcs.dp.gui.component.AttributesListComponent.getKeyFromAttribute(attribute);
                String value = com.ospreydcs.dp.gui.component.AttributesListComponent.getValueFromAttribute(attribute);
                if (key != null && !key.trim().isEmpty()) {
                    attributeMap.put(key.trim(), value != null ? value.trim() : "");
                }
            }
        }
        
        // Step 4: Convert DataSetDetail objects to dataset IDs
        List<String> dataSetIds = dataSets.stream()
            .map(DataSetDetail::getId)
            .filter(Objects::nonNull)
            .filter(datasetId -> !datasetId.trim().isEmpty())
            .collect(java.util.stream.Collectors.toList());
        
        // Convert empty strings to null for optional fields
        String commentToSave = (comment != null && !comment.trim().isEmpty()) ? comment.trim() : null;
        String eventNameToSave = (eventName != null && !eventName.trim().isEmpty()) ? eventName.trim() : null;
        List<String> tagsToSave = tags.isEmpty() ? null : new ArrayList<>(tags);
        Map<String, String> attributesToSave = attributeMap.isEmpty() ? null : attributeMap;
        
        logger.info("Calling DpApplication.saveAnnotation with: dataSetIds={}, tags={}, attributes={}", 
                   dataSetIds.size(), tagsToSave != null ? tagsToSave.size() : 0, 
                   attributesToSave != null ? attributesToSave.size() : 0);
        
        // Step 5: Call DpApplication.saveAnnotation() method
        try {
            com.ospreydcs.dp.client.result.SaveAnnotationApiResult apiResult = 
                dpApplication.saveAnnotation(
                    id, // existing annotation ID (null for new annotations)
                    name,
                    dataSetIds,
                    null, // annotationIds - not used in current implementation
                    commentToSave,
                    tagsToSave,
                    attributesToSave,
                    eventNameToSave
                );
            
            if (apiResult == null) {
                annotationBuilderViewModel.statusMessageProperty().set("Save failed - null response from service");
                logger.error("Annotation save failed: null API result");
                return;
            }
            
            // Step 6: Handle API result
            if (apiResult.resultStatus.isError) {
                // Error case
                String errorMessage = "Save failed: " + apiResult.resultStatus.msg;
                annotationBuilderViewModel.statusMessageProperty().set(errorMessage);
                logger.error("Annotation save failed: {}", apiResult.resultStatus.msg);
            } else {
                // Success case - extract the annotation ID and update the field
                if (apiResult.annotationId != null && !apiResult.annotationId.trim().isEmpty()) {
                    annotationBuilderViewModel.setAnnotationId(apiResult.annotationId);
                    
                    String successMessage = "Annotation saved successfully with ID: " + apiResult.annotationId;
                    annotationBuilderViewModel.statusMessageProperty().set(successMessage);
                    logger.info("Annotation save completed successfully: {}", apiResult.annotationId);
                } else {
                    // This shouldn't happen for successful saves, but handle gracefully
                    String successMessage = "Annotation saved successfully";
                    annotationBuilderViewModel.statusMessageProperty().set(successMessage);
                    logger.warn("Annotation save completed but no ID returned");
                }
            }
            
        } catch (Exception e) {
            String errorMessage = "Save failed with exception: " + e.getMessage();
            annotationBuilderViewModel.statusMessageProperty().set(errorMessage);
            logger.error("Annotation save failed with exception", e);
        }
    }
    
    @FXML
    private void onImportCalculations() {
        logger.info("Import calculations requested");
        
        // Create file chooser for Excel files
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Excel File for Calculations Import");
        fileChooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );
        
        // Show file dialog
        java.io.File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile != null) {
            logger.info("Excel file selected for import: {}", selectedFile.getAbsolutePath());
            
            try {
                // Use the shared DataImportUtility to import Excel data
                DataImportResult importResult = DataImportUtility.importXlsxData(selectedFile.getAbsolutePath());
                
                if (importResult.resultStatus.isError) {
                    String errorMessage = "Failed to import calculations: " + importResult.resultStatus.msg;
                    annotationBuilderViewModel.statusMessageProperty().set(errorMessage);
                    logger.error("Failed to import calculations from Excel file: {}", importResult.resultStatus.msg);
                } else {
                    // Create DataFrameDetails from all imported sheets
                    List<DataFrameDetails> importedFrames = new ArrayList<>();
                    
                    for (DataImportResult.DataFrameResult frameResult : importResult.dataFrames) {
                        DataFrameDetails frame = new DataFrameDetails(
                            frameResult.sheetName, 
                            frameResult.timestamps, 
                            frameResult.columns
                        );
                        importedFrames.add(frame);
                    }
                    
                    // Add all imported frames to the list
                    annotationBuilderViewModel.getCalculationsDataFrames().addAll(importedFrames);
                    
                    String successMessage = String.format("Successfully imported %d calculation frame(s) from %s", 
                        importedFrames.size(),
                        selectedFile.getName());
                    annotationBuilderViewModel.statusMessageProperty().set(successMessage);
                    logger.info("Successfully imported {} calculation frame(s) from {}", 
                        importedFrames.size(), selectedFile.getName());
                        
                    // Log details for each frame
                    for (DataFrameDetails frame : importedFrames) {
                        logger.debug("  Frame '{}': {} timestamps, {} columns", 
                            frame.getName(),
                            frame.getTimestamps() != null ? frame.getTimestamps().size() : 0,
                            frame.getDataColumns() != null ? frame.getDataColumns().size() : 0);
                    }
                }
                
            } catch (Exception e) {
                String errorMessage = "Failed to import calculations: " + e.getMessage();
                annotationBuilderViewModel.statusMessageProperty().set(errorMessage);
                logger.error("Failed to import calculations from Excel file: {}", selectedFile.getName(), e);
            }
        } else {
            logger.info("Excel file import cancelled by user");
        }
    }
    
    @FXML
    private void onViewCalculations() {
        DataFrameDetails selectedFrame = calculationsDataFramesList.getSelectionModel().getSelectedItem();
        if (selectedFrame != null) {
            logger.info("View calculations requested for frame: {}", selectedFrame.getName());
            
            // Create and show a dialog with calculation details
            showCalculationsDetailsDialog(selectedFrame);
        } else {
            logger.warn("View calculations requested but no frame selected");
        }
    }
    
    @FXML
    private void onRemoveCalculations() {
        DataFrameDetails selectedFrame = calculationsDataFramesList.getSelectionModel().getSelectedItem();
        if (selectedFrame != null) {
            // Store the current selected index before removal
            int selectedIndex = calculationsDataFramesList.getSelectionModel().getSelectedIndex();
            
            // Remove the data frame
            annotationBuilderViewModel.getCalculationsDataFrames().remove(selectedFrame);
            logger.info("Removed calculations data frame: {}", selectedFrame.getName());
            
            // Manage focus to prevent unwanted scrolling
            if (!annotationBuilderViewModel.getCalculationsDataFrames().isEmpty()) {
                // If there are remaining items, select the next logical item
                int newIndex = Math.min(selectedIndex, annotationBuilderViewModel.getCalculationsDataFrames().size() - 1);
                calculationsDataFramesList.getSelectionModel().select(newIndex);
                // Keep focus on the ListView to prevent focus transfer
                calculationsDataFramesList.requestFocus();
            } else {
                // If no items remain, clear selection but keep focus on ListView
                calculationsDataFramesList.getSelectionModel().clearSelection();
                calculationsDataFramesList.requestFocus();
            }
        } else {
            logger.warn("Remove calculations requested but no frame selected");
        }
    }
    
    private void setupChartTooltipsWithRetry(int attemptCount) {
        final int MAX_ATTEMPTS = 10;
        final int DELAY_MS = 100;
        
        if (attemptCount >= MAX_ATTEMPTS) {
            logger.warn("Failed to set up tooltips after {} attempts - nodes still not available", MAX_ATTEMPTS);
            return;
        }
        
        javafx.application.Platform.runLater(() -> {
            boolean allNodesReady = true;
            int availableNodes = 0;
            int totalDataPoints = 0;
            
            // Check if all nodes are available
            for (XYChart.Series<Number, Number> series : resultsChart.getData()) {
                for (XYChart.Data<Number, Number> dataPoint : series.getData()) {
                    totalDataPoints++;
                    if (dataPoint.getNode() != null) {
                        availableNodes++;
                    } else {
                        allNodesReady = false;
                    }
                }
            }
            
            logger.debug("Tooltip setup attempt {}: {}/{} nodes available", attemptCount + 1, availableNodes, totalDataPoints);
            
            if (allNodesReady && totalDataPoints > 0) {
                // All nodes are ready, set up tooltips
                setupChartTooltips();
            } else {
                // Retry after a delay
                javafx.concurrent.Task<Void> delayTask = new javafx.concurrent.Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        Thread.sleep(DELAY_MS);
                        return null;
                    }
                };
                delayTask.setOnSucceeded(e -> setupChartTooltipsWithRetry(attemptCount + 1));
                new Thread(delayTask).start();
            }
        });
    }
    
    private void setupChartTooltips() {
        logger.debug("Setting up chart tooltips for {} series", resultsChart.getData().size());
        
        int tooltipCount = 0;
        // Set up tooltips for each series
        for (XYChart.Series<Number, Number> series : resultsChart.getData()) {
            for (XYChart.Data<Number, Number> dataPoint : series.getData()) {
                Node node = dataPoint.getNode();
                if (node != null && dataPoint.getExtraValue() instanceof DataPointInfo) {
                    DataPointInfo info = (DataPointInfo) dataPoint.getExtraValue();
                    String tooltipText = formatTooltip(info);
                    
                    Tooltip tooltip = new Tooltip(tooltipText);
                    tooltip.setStyle("-fx-font-size: 12px; -fx-background-color: rgba(0,0,0,0.8); -fx-text-fill: white;");
                    Tooltip.install(node, tooltip);
                    tooltipCount++;
                } else {
                    logger.debug("Skipping tooltip - node: {}, extraValue type: {}", 
                               node != null ? "exists" : "null", 
                               dataPoint.getExtraValue() != null ? dataPoint.getExtraValue().getClass().getSimpleName() : "null");
                }
            }
        }
        
        logger.info("Installed {} tooltips on chart data points", tooltipCount);
    }
    
    private String formatTooltip(DataPointInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("PV: ").append(info.pvName).append("\n");
        sb.append("Value: ").append(info.value).append("\n");
        sb.append("Time: ").append(formatTimestampForTooltip(info.timestamp));
        return sb.toString();
    }
    
    private String formatTimestampForTooltip(Object timestampValue) {
        if (timestampValue == null) {
            return "N/A";
        }
        
        String timestamp = timestampValue.toString();
        // Format timestamp for tooltip display
        if (timestamp.contains("T")) {
            String[] parts = timestamp.split("T");
            if (parts.length > 1) {
                String datePart = parts[0];
                String timePart = parts[1].substring(0, Math.min(8, parts[1].length())); // HH:mm:ss
                return datePart + " " + timePart;
            }
        }
        
        return timestamp;
    }
    
    private Tooltip mouseTrackingTooltip;
    
    private void setupChartMouseTracking() {
        // Create a single tooltip that we'll reuse and reposition
        if (mouseTrackingTooltip == null) {
            mouseTrackingTooltip = new Tooltip();
            mouseTrackingTooltip.setStyle("-fx-font-size: 12px; -fx-background-color: rgba(0,0,0,0.8); -fx-text-fill: white;");
            mouseTrackingTooltip.setAutoHide(false);
        }
        
        // Remove any existing mouse handlers
        resultsChart.setOnMouseMoved(null);
        resultsChart.setOnMouseExited(null);
        
        // Add mouse tracking to the chart
        resultsChart.setOnMouseMoved(event -> {
            try {
                DataPointInfo nearestPoint = findNearestDataPoint(event.getX(), event.getY());
                if (nearestPoint != null) {
                    String tooltipText = formatTooltip(nearestPoint);
                    mouseTrackingTooltip.setText(tooltipText);
                    
                    // Position tooltip near mouse cursor
                    if (!mouseTrackingTooltip.isShowing()) {
                        mouseTrackingTooltip.show(resultsChart, event.getScreenX() + 10, event.getScreenY() - 10);
                    } else {
                        mouseTrackingTooltip.setAnchorX(event.getScreenX() + 10);
                        mouseTrackingTooltip.setAnchorY(event.getScreenY() - 10);
                    }
                } else {
                    mouseTrackingTooltip.hide();
                }
            } catch (Exception e) {
                logger.debug("Error in mouse tracking: {}", e.getMessage());
                mouseTrackingTooltip.hide();
            }
        });
        
        // Hide tooltip when mouse leaves chart
        resultsChart.setOnMouseExited(event -> {
            if (mouseTrackingTooltip != null) {
                mouseTrackingTooltip.hide();
            }
        });
        
        logger.debug("Chart mouse tracking enabled");
    }
    
    private DataPointInfo findNearestDataPoint(double mouseX, double mouseY) {
        if (resultsChart.getData().isEmpty()) {
            return null;
        }
        
        try {
            // Convert mouse coordinates to chart value coordinates
            Number xNum = chartXAxis.getValueForDisplay(mouseX - chartXAxis.getLayoutX());
            Number yNum = chartYAxis.getValueForDisplay(mouseY - chartYAxis.getLayoutY());
            
            if (xNum == null || yNum == null) {
                return null;
            }
            
            double xValue = xNum.doubleValue();
            double yValue = yNum.doubleValue();
            
            DataPointInfo nearest = null;
            double minDistance = Double.MAX_VALUE;
            
            // Search through all series for the nearest point
            for (XYChart.Series<Number, Number> series : resultsChart.getData()) {
                for (XYChart.Data<Number, Number> dataPoint : series.getData()) {
                    if (dataPoint.getExtraValue() instanceof DataPointInfo) {
                        double pointX = dataPoint.getXValue().doubleValue();
                        double pointY = dataPoint.getYValue().doubleValue();
                        
                        // Calculate distance (prioritize X-axis distance for time-series)
                        double deltaX = (pointX - xValue);
                        double deltaY = (pointY - yValue);
                        double distance = Math.sqrt(deltaX * deltaX * 4 + deltaY * deltaY); // Weight X more heavily
                        
                        if (distance < minDistance) {
                            minDistance = distance;
                            nearest = (DataPointInfo) dataPoint.getExtraValue();
                        }
                    }
                }
            }
            
            // Only return if reasonably close (within chart bounds)
            if (minDistance < 5.0) { // Adjust sensitivity as needed
                return nearest;
            }
            
        } catch (Exception e) {
            logger.debug("Error finding nearest data point: {}", e.getMessage());
        }
        
        return null;
    }
    
    // Methods for updating global state in DpApplication
    private void updateGlobalQueryState() {
        logger.debug("CRAIG DEBUG: updateGlobalQueryState() called");
        
        if (dpApplication == null) {
            logger.warn("DpApplication reference is null, cannot update global query state");
            return;
        }
        
        // Commit any pending spinner edits before updating global state
        logger.debug("CRAIG DEBUG: About to call commitSpinnerValues()");
        commitSpinnerValues();
        
        logger.debug("CRAIG DEBUG: About to call updateGlobalPvNames()");
        updateGlobalPvNames();
        
        logger.debug("CRAIG DEBUG: About to call updateGlobalTimeRange()");
        updateGlobalTimeRange();
        
        logger.debug("CRAIG DEBUG: Updated global query state in DpApplication");
    }
    
    private void updateGlobalPvNames() {
        if (dpApplication == null || viewModel == null) return;
        
        ObservableList<String> pvNames = viewModel.getPvNameList();
        if (pvNames != null && !pvNames.isEmpty()) {
            java.util.List<String> pvNameList = new java.util.ArrayList<>(pvNames);
            dpApplication.setQueryPvNames(pvNameList);
            logger.debug("Updated global PV names: {}", pvNameList);
        } else {
            dpApplication.setQueryPvNames(null);
            logger.debug("Cleared global PV names (empty list)");
        }
    }
    
    private void updateGlobalTimeRange() {
        if (dpApplication == null || viewModel == null) return;
        
        try {
            java.time.Instant beginTime = getBeginTimeFromUI();
            java.time.Instant endTime = getEndTimeFromUI();
            
            if (beginTime != null && endTime != null) {
                dpApplication.setQueryTimeRange(beginTime, endTime);
                logger.debug("Updated global time range: {} to {}", beginTime, endTime);
            }
        } catch (Exception e) {
            logger.warn("Failed to update global time range: {}", e.getMessage());
        }
    }
    
    private java.time.Instant getBeginTimeFromUI() {
        if (queryBeginDatePicker.getValue() == null) {
            logger.debug("getBeginTimeFromUI: date picker is null");
            return null;
        }
        
        LocalDate date = queryBeginDatePicker.getValue();
        int hour = beginHourSpinner.getValue();
        int minute = beginMinuteSpinner.getValue();
        int second = beginSecondSpinner.getValue();
        
        logger.debug("getBeginTimeFromUI: date={}, time={}:{}:{}", date, hour, minute, second);
        
        LocalDateTime dateTime = LocalDateTime.of(date, java.time.LocalTime.of(hour, minute, second));
        java.time.Instant result = dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
        
        logger.debug("getBeginTimeFromUI: result={}", result);
        return result;
    }
    
    private java.time.Instant getEndTimeFromUI() {
        if (queryEndDatePicker.getValue() == null) {
            logger.debug("getEndTimeFromUI: date picker is null");
            return null;
        }
        
        LocalDate date = queryEndDatePicker.getValue();
        int hour = endHourSpinner.getValue();
        int minute = endMinuteSpinner.getValue();
        int second = endSecondSpinner.getValue();
        
        logger.debug("getEndTimeFromUI: date={}, time={}:{}:{}", date, hour, minute, second);
        
        LocalDateTime dateTime = LocalDateTime.of(date, java.time.LocalTime.of(hour, minute, second));
        java.time.Instant result = dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
        
        logger.debug("getEndTimeFromUI: result={}", result);
        return result;
    }
    
    private com.ospreydcs.dp.gui.model.DataBlockDetail createDataBlockFromQueryEditor() {
        try {
            // Get PV names from the list
            ObservableList<String> pvNames = viewModel.getPvNameList();
            if (pvNames == null || pvNames.isEmpty()) {
                logger.warn("Cannot create data block: no PV names specified");
                return null;
            }
            
            // Get time range from UI
            java.time.Instant beginTime = getBeginTimeFromUI();
            java.time.Instant endTime = getEndTimeFromUI();
            
            if (beginTime == null || endTime == null) {
                logger.warn("Cannot create data block: invalid time range");
                return null;
            }
            
            if (!endTime.isAfter(beginTime)) {
                logger.warn("Cannot create data block: end time must be after begin time");
                return null;
            }
            
            // Create and return the data block
            java.util.List<String> pvNameList = new java.util.ArrayList<>(pvNames);
            com.ospreydcs.dp.gui.model.DataBlockDetail dataBlock = 
                new com.ospreydcs.dp.gui.model.DataBlockDetail(pvNameList, beginTime, endTime);
            
            logger.debug("Created data block: {} PVs, time range {} to {}", 
                        pvNameList.size(), beginTime, endTime);
            
            return dataBlock;
        } catch (Exception e) {
            logger.error("Error creating data block from query editor: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private void commitSpinnerValues() {
        // Force commit any pending edits in spinners by calling commitValue()
        try {
            beginHourSpinner.commitValue();
            beginMinuteSpinner.commitValue();
            beginSecondSpinner.commitValue();
            endHourSpinner.commitValue();
            endMinuteSpinner.commitValue();
            endSecondSpinner.commitValue();
            
            logger.debug("Committed all spinner values - Begin: {}:{}:{}, End: {}:{}:{}", 
                beginHourSpinner.getValue(), beginMinuteSpinner.getValue(), beginSecondSpinner.getValue(),
                endHourSpinner.getValue(), endMinuteSpinner.getValue(), endSecondSpinner.getValue());
        } catch (Exception e) {
            logger.warn("Error committing spinner values: {}", e.getMessage());
        }
    }
    
    private void initializeUIFromGlobalState() {
        if (dpApplication == null) return;
        
        // Set flag to prevent listeners from interfering during initialization
        isInitializingFromGlobalState = true;
        
        try {
            // Initialize PV names from global state
            if (dpApplication.getPvDetails() != null && !dpApplication.getPvDetails().isEmpty()) {
                java.util.List<String> globalPvNames = new java.util.ArrayList<>();
                for (com.ospreydcs.dp.gui.model.PvDetail pvDetail : dpApplication.getPvDetails()) {
                    if (pvDetail.getPvName() != null && !pvDetail.getPvName().trim().isEmpty()) {
                        globalPvNames.add(pvDetail.getPvName());
                    }
                }
                
                if (!globalPvNames.isEmpty()) {
                    viewModel.getPvNameList().setAll(globalPvNames);
                    logger.debug("Initialized UI with {} PV names from global state", globalPvNames.size());
                }
            }
            
            // Initialize time range from global state
            if (dpApplication.getDataBeginTime() != null && dpApplication.getDataEndTime() != null) {
                java.time.Instant beginTime = dpApplication.getDataBeginTime();
                java.time.Instant endTime = dpApplication.getDataEndTime();
                
                // Convert to LocalDateTime using system default timezone for UI components
                LocalDateTime beginDateTime = LocalDateTime.ofInstant(beginTime, java.time.ZoneId.systemDefault());
                LocalDateTime endDateTime = LocalDateTime.ofInstant(endTime, java.time.ZoneId.systemDefault());
                
                logger.debug("Setting UI from global state: begin={} ({}), end={} ({})", 
                    beginTime, beginDateTime, endTime, endDateTime);
                
                // Set date pickers
                queryBeginDatePicker.setValue(beginDateTime.toLocalDate());
                queryEndDatePicker.setValue(endDateTime.toLocalDate());
                
                // Set time spinners
                beginHourSpinner.getValueFactory().setValue(beginDateTime.getHour());
                beginMinuteSpinner.getValueFactory().setValue(beginDateTime.getMinute());
                beginSecondSpinner.getValueFactory().setValue(beginDateTime.getSecond());
                
                endHourSpinner.getValueFactory().setValue(endDateTime.getHour());
                endMinuteSpinner.getValueFactory().setValue(endDateTime.getMinute());
                endSecondSpinner.getValueFactory().setValue(endDateTime.getSecond());
                
                logger.debug("Initialized UI time range - Begin: {}:{}:{}, End: {}:{}:{}", 
                    beginDateTime.getHour(), beginDateTime.getMinute(), beginDateTime.getSecond(),
                    endDateTime.getHour(), endDateTime.getMinute(), endDateTime.getSecond());
            }
            
        } finally {
            // Always clear the flag, even if there's an exception
            isInitializingFromGlobalState = false;
        }
    }
    
    private void setupDatasetActionsCombo() {
        // Set up action handler for Dataset Actions ComboBox
        datasetActionsCombo.setOnAction(event -> {
            String selectedAction = datasetActionsCombo.getSelectionModel().getSelectedItem();
            if (selectedAction != null) {
                logger.info("Dataset action selected: {}", selectedAction);
                
                // Handle export actions
                switch (selectedAction) {
                    case "Export CSV":
                        handleExportAction(DpApplication.ExportOutputFileFormat.CSV);
                        break;
                    case "Export XLSX":
                        handleExportAction(DpApplication.ExportOutputFileFormat.XLSX);
                        break;
                    case "Export HDF5":
                        handleExportAction(DpApplication.ExportOutputFileFormat.HDF5);
                        break;
                    default:
                        logger.warn("Unknown dataset action: {}", selectedAction);
                        datasetBuilderViewModel.statusMessageProperty().set("Unknown action: " + selectedAction);
                        break;
                }
                
                // Reset ComboBox selection after action
                datasetActionsCombo.getSelectionModel().clearSelection();
            }
        });
        
        logger.debug("Dataset Actions ComboBox event handler configured");
    }
    
    private void handleExportAction(DpApplication.ExportOutputFileFormat format) {
        logger.info("Export requested for format: {}", format);
        
        // Step 1: Get dataset ID and validate
        String datasetId = datasetBuilderViewModel.getDatasetId();
        if (datasetId == null || datasetId.trim().isEmpty()) {
            String errorMessage = "Export failed: Dataset must be saved first (no ID found)";
            datasetBuilderViewModel.statusMessageProperty().set(errorMessage);
            logger.error("Export failed: no dataset ID");
            return;
        }
        
        // Step 2: Update status to show operation in progress
        datasetBuilderViewModel.statusMessageProperty().set("Exporting dataset to " + format + "...");
        logger.info("Starting export: datasetId={}, format={}", datasetId, format);
        
        try {
            // Step 3: Call DpApplication.exportData() method
            com.ospreydcs.dp.client.result.ExportDataApiResult apiResult = 
                dpApplication.exportData(datasetId, null, format);
            
            if (apiResult == null) {
                String errorMessage = "Export failed - null response from service";
                datasetBuilderViewModel.statusMessageProperty().set(errorMessage);
                logger.error("Export failed: null API result");
                return;
            }
            
            // Step 4: Handle API result
            if (apiResult.resultStatus.isError) {
                // Error case - display error message
                String errorMessage = "Export failed: " + apiResult.resultStatus.msg;
                datasetBuilderViewModel.statusMessageProperty().set(errorMessage);
                logger.error("Export failed: {}", apiResult.resultStatus.msg);
            } else {
                // Success case - get file path and display success message
                if (apiResult.exportDataResult != null && 
                    apiResult.exportDataResult.getFilePath() != null && 
                    !apiResult.exportDataResult.getFilePath().trim().isEmpty()) {
                    
                    String filePath = apiResult.exportDataResult.getFilePath();
                    String successMessage = "Export completed successfully: " + filePath;
                    datasetBuilderViewModel.statusMessageProperty().set(successMessage);
                    logger.info("Export completed successfully: {}", filePath);
                    
                    // Step 5: Try to open the file with native application
                    openFileWithNativeApplication(filePath);
                    
                } else {
                    // Shouldn't happen for successful exports, but handle gracefully
                    String successMessage = "Export completed successfully";
                    datasetBuilderViewModel.statusMessageProperty().set(successMessage);
                    logger.warn("Export completed but no file path returned");
                }
            }
            
        } catch (Exception e) {
            String errorMessage = "Export failed with exception: " + e.getMessage();
            datasetBuilderViewModel.statusMessageProperty().set(errorMessage);
            logger.error("Export failed with exception", e);
        }
    }
    
    private void openFileWithNativeApplication(String filePath) {
        // Run file opening in background thread to avoid blocking UI
        javafx.concurrent.Task<Void> fileOpenTask = new javafx.concurrent.Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Check if Desktop is supported on this platform
                    if (!Desktop.isDesktopSupported()) {
                        logger.info("Desktop operations not supported on this platform, cannot open file: {}", filePath);
                        return null;
                    }
                    
                    // Create File object from the file path
                    File file = new File(filePath);
                    if (!file.exists()) {
                        logger.warn("Export file does not exist, cannot open: {}", filePath);
                        return null;
                    }
                    
                    // Wait for file to be fully written and stable
                    if (!waitForFileStable(file)) {
                        logger.warn("File may not be fully written yet, skipping auto-open: {}", filePath);
                        return null;
                    }
                    
                    // Try to open with native application
                    Desktop desktop = Desktop.getDesktop();
                    if (desktop.isSupported(Desktop.Action.OPEN)) {
                        logger.info("Attempting to open file with native application: {}", filePath);
                        desktop.open(file);
                        logger.info("Successfully initiated file opening with native application: {}", filePath);
                    } else {
                        logger.info("OPEN action not supported by Desktop, cannot open file: {}", filePath);
                    }
                    
                } catch (Exception e) {
                    // Don't let file opening errors interfere with export success
                    logger.warn("Failed to open exported file with native application: {} - {}", filePath, e.getMessage());
                }
                return null;
            }
        };
        
        // Handle task completion (success or failure) on JavaFX thread
        fileOpenTask.setOnSucceeded(event -> {
            logger.debug("File opening task completed successfully");
        });
        
        fileOpenTask.setOnFailed(event -> {
            Throwable exception = fileOpenTask.getException();
            logger.warn("File opening task failed: {}", exception != null ? exception.getMessage() : "Unknown error");
        });
        
        // Run the task in a daemon thread so it doesn't prevent app shutdown
        Thread fileOpenThread = new Thread(fileOpenTask);
        fileOpenThread.setDaemon(true);
        fileOpenThread.setName("FileOpenTask-" + System.currentTimeMillis());
        fileOpenThread.start();
        
        logger.debug("Started background thread to open file: {}", filePath);
    }
    
    private boolean waitForFileStable(File file) {
        try {
            // Wait for file to be stable (not changing in size) for a short period
            long lastSize = -1;
            long stableTime = 0;
            final long REQUIRED_STABLE_TIME = 500; // 500ms of stability required
            final long MAX_WAIT_TIME = 5000; // Maximum 5 seconds to wait
            final long CHECK_INTERVAL = 100; // Check every 100ms
            
            long startTime = System.currentTimeMillis();
            
            while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME) {
                long currentSize = file.length();
                
                if (currentSize == lastSize && currentSize > 0) {
                    // File size is stable
                    stableTime += CHECK_INTERVAL;
                    if (stableTime >= REQUIRED_STABLE_TIME) {
                        logger.info("File is stable ({}ms stable period): {} bytes", stableTime, currentSize);
                        return true;
                    }
                } else {
                    // File size changed, reset stability timer
                    stableTime = 0;
                    lastSize = currentSize;
                    logger.debug("File size changed to: {} bytes", currentSize);
                }
                
                Thread.sleep(CHECK_INTERVAL);
            }
            
            logger.warn("File did not stabilize within {}ms, current size: {} bytes", MAX_WAIT_TIME, file.length());
            return false;
            
        } catch (Exception e) {
            logger.warn("Error waiting for file stability: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Shows a dialog with details about the selected calculation frame.
     */
    private void showCalculationsDetailsDialog(DataFrameDetails frame) {
        // Create dialog
        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Calculation Frame Details");
        dialog.setHeaderText("Details for: " + frame.getName());
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        
        // Create content
        javafx.scene.control.TextArea contentArea = new javafx.scene.control.TextArea();
        contentArea.setEditable(false);
        contentArea.setWrapText(true);
        contentArea.setPrefSize(600, 400);
        
        StringBuilder content = new StringBuilder();
        content.append("Frame Name: ").append(frame.getName()).append("\n\n");
        
        // Add timestamp information
        if (frame.getTimestamps() != null && !frame.getTimestamps().isEmpty()) {
            content.append("Timestamps: ").append(frame.getTimestamps().size()).append(" entries\n");
            content.append("First timestamp: ").append(formatTimestamp(frame.getTimestamps().get(0))).append("\n");
            if (frame.getTimestamps().size() > 1) {
                content.append("Last timestamp: ").append(formatTimestamp(frame.getTimestamps().get(frame.getTimestamps().size() - 1))).append("\n");
            }
        } else {
            content.append("Timestamps: None\n");
        }
        content.append("\n");
        
        // Add column information
        if (frame.getDataColumns() != null && !frame.getDataColumns().isEmpty()) {
            content.append("Data Columns (").append(frame.getDataColumns().size()).append("):\n");
            for (int i = 0; i < frame.getDataColumns().size(); i++) {
                DataColumn column = frame.getDataColumns().get(i);
                content.append(String.format("  %d. %s (%d values)\n", 
                    i + 1, 
                    column.getName(), 
                    column.getDataValuesCount()));
                
                // Show first few values as sample
                if (column.getDataValuesCount() > 0) {
                    content.append("     Sample values: ");
                    int sampleCount = Math.min(3, column.getDataValuesCount());
                    for (int j = 0; j < sampleCount; j++) {
                        if (j > 0) content.append(", ");
                        content.append(formatDataValue(column.getDataValues(j)));
                    }
                    if (column.getDataValuesCount() > 3) {
                        content.append("...");
                    }
                    content.append("\n");
                }
            }
        } else {
            content.append("Data Columns: None\n");
        }
        
        contentArea.setText(content.toString());
        dialog.getDialogPane().setContent(contentArea);
        
        // Show dialog
        dialog.showAndWait();
    }
    
    /**
     * Formats a timestamp for display.
     */
    private String formatTimestamp(Timestamp timestamp) {
        try {
            java.time.Instant instant = java.time.Instant.ofEpochSecond(
                timestamp.getEpochSeconds(), 
                timestamp.getNanoseconds()
            );
            return instant.toString();
        } catch (Exception e) {
            return "Invalid timestamp";
        }
    }
    
    /**
     * Formats a DataValue for display.
     */
    private String formatDataValue(DataValue dataValue) {
        try {
            switch (dataValue.getValueCase()) {
                case STRINGVALUE:
                    return "\"" + dataValue.getStringValue() + "\"";
                case DOUBLEVALUE:
                    return String.format("%.3f", dataValue.getDoubleValue());
                case BOOLEANVALUE:
                    return String.valueOf(dataValue.getBooleanValue());
                case INTVALUE:
                    return String.valueOf(dataValue.getIntValue());
                case ULONGVALUE:
                    return String.valueOf(dataValue.getUlongValue());
                case UINTVALUE:
                    return String.valueOf(dataValue.getUintValue());
                default:
                    return "Unknown";
            }
        } catch (Exception e) {
            return "Error";
        }
    }
    
    // Helper class to store original data point information for tooltips
    private static class DataPointInfo {
        final Object timestamp;
        final Object value;
        final String pvName;
        
        DataPointInfo(Object timestamp, Object value, String pvName) {
            this.timestamp = timestamp;
            this.value = value;
            this.pvName = pvName;
        }
    }
}