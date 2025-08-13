package com.ospreydcs.dp.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
// CategoryAxis import removed - using NumberAxis for both axes
import javafx.scene.chart.NumberAxis;
import javafx.scene.Node;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ResourceBundle;

public class DataQueryController implements Initializable {

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
    @FXML private Button cancelQueryButton;
    @FXML private Label queryStatusLabel;
    
    // Query Results FXML components
    @FXML private VBox queryResultsSection;
    @FXML private Label rowCountLabel;
    @FXML private TabPane resultsTabPane;
    @FXML private TableView<ObservableList<Object>> resultsTable;
    @FXML private LineChart<Number, Number> resultsChart;
    @FXML private NumberAxis chartXAxis;
    @FXML private NumberAxis chartYAxis;
    @FXML private Label chartPlaceholder;
    @FXML private Label resultsStatusLabel;
    @FXML private ProgressIndicator queryProgressIndicator;

    // Dependencies
    private DataQueryViewModel viewModel;
    private DpApplication dpApplication;
    private Stage primaryStage;
    private MainController mainController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("DataQueryController initializing...");
        
        // Create the view model
        viewModel = new DataQueryViewModel();
        
        // Initialize UI components
        initializeSpinners();
        initializeRadioButtons();
        initializeChart();
        
        // Bind UI components to view model properties
        bindUIToViewModel();
        
        // Set up event handlers
        setupEventHandlers();
        
        logger.debug("DataQueryController initialized successfully");
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
        resultsChart.setCreateSymbols(false); // Disable symbols for better performance
        resultsChart.setLegendSide(javafx.geometry.Side.RIGHT);
        
        // Configure axes
        chartXAxis.setLabel("Time (seconds from start)");
        chartYAxis.setLabel("Value");
        
        // Note: Initial visibility is set in FXML (chart hidden, placeholder visible)
        
        logger.debug("Chart initialized with title and axis labels");
    }

    private void bindUIToViewModel() {
        // Query Specification bindings
        pvNamesList.setItems(viewModel.getPvNameList());
        specificationContent.visibleProperty().bind(viewModel.showQuerySpecificationPanelProperty());
        specificationContent.managedProperty().bind(viewModel.showQuerySpecificationPanelProperty());
        
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
        submitQueryButton.disableProperty().bind(viewModel.isQueryingProperty());
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
                }
            });
            contextMenu.getItems().add(deleteItem);
            
            cell.setContextMenu(contextMenu);
            return cell;
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
        
        // Tooltips disabled for performance reasons
        // setupChartTooltipsWithRetry(0);
        
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
            logger.debug("Status listener established between DataQueryViewModel and MainController");
        }
    }

    // Dependency injection methods
    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        if (viewModel != null) {
            viewModel.setDpApplication(dpApplication);
        }
        logger.debug("DpApplication injected into DataQueryController");
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        logger.debug("Primary stage injected into DataQueryController");
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        
        // Inject MainController into ViewModel for home view updates
        if (viewModel != null) {
            viewModel.setMainController(mainController);
        }
        
        // Set up status message forwarding
        setupStatusListener();
        
        logger.debug("MainController injected into DataQueryController");
    }

    // Event handler methods
    @FXML
    private void onToggleQuerySpecification() {
        viewModel.toggleQuerySpecificationPanel();
        
        // Update button text based on panel visibility
        boolean isVisible = viewModel.showQuerySpecificationPanelProperty().get();
        toggleSpecificationButton.setText(isVisible ? "📋 Query Specification" : "📋 Query Specification (Hidden)");
        
        logger.debug("Query specification panel toggled: {}", isVisible ? "visible" : "hidden");
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
        
        logger.info("Added {} selected PVs to query list", selectedItems.size());
    }
    
    @FXML
    private void onSubmitQuery() {
        logger.info("Query submission requested");
        viewModel.submitQuery();
    }
    
    @FXML
    private void onCancelQuery() {
        logger.info("Query cancelled by user");
        viewModel.cancel();
        
        // Navigate back to main window
        if (mainController != null) {
            mainController.switchToMainView();
        } else {
            logger.warn("MainController reference is null, cannot navigate back");
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