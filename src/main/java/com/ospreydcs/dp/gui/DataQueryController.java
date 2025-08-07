package com.ospreydcs.dp.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.time.LocalDate;
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
    @FXML private TableView<ObservableList<Object>> resultsTable;
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
        searchByNameListRadio.selectedProperty().bindBidirectional(viewModel.searchByNameListProperty());
        searchByPatternRadio.selectedProperty().bind(viewModel.searchByNameListProperty().not());
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
            }
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
}