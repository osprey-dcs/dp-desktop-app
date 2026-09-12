package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.component.QueryPvsComponent;
import com.ospreydcs.dp.gui.model.PvInfoTableRow;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;

public class PvExploreController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    // FXML injected components - Query PVs Component
    @FXML private QueryPvsComponent queryPvsComponent;

    // FXML injected components - PV Query Editor
    @FXML private TextField pvSearchTextField;
    @FXML private RadioButton nameListRadio;
    @FXML private RadioButton namePatternRadio;
    @FXML private ToggleGroup searchModeToggle;
    @FXML private Button searchButton;

    // FXML injected components - PV Query Results
    @FXML private TableView<PvInfoTableRow> resultsTable;
    @FXML private TableColumn<PvInfoTableRow, Boolean> selectColumn;
    @FXML private TableColumn<PvInfoTableRow, String> pvNameColumn;
    @FXML private TableColumn<PvInfoTableRow, String> providerNameColumn;
    @FXML private TableColumn<PvInfoTableRow, String> dataTypeColumn;
    @FXML private TableColumn<PvInfoTableRow, String> timestampsTypeColumn;
    @FXML private TableColumn<PvInfoTableRow, String> samplePeriodColumn;
    @FXML private TableColumn<PvInfoTableRow, String> firstDataTimestampColumn;
    @FXML private TableColumn<PvInfoTableRow, String> lastDataTimestampColumn;
    @FXML private TableColumn<PvInfoTableRow, Integer> numBucketsColumn;
    @FXML private Button addSelectedButton;
    @FXML private Label resultsStatusLabel;

    // Dependencies
    private PvExploreViewModel viewModel;
    private DpApplication dpApplication;
    private Stage primaryStage;
    private MainController mainController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("PvExploreController initializing...");

        // Create view model
        viewModel = new PvExploreViewModel();

        // Inject QueryPvsComponent into view model
        viewModel.setQueryPvsComponent(queryPvsComponent);

        // Set up table columns
        setupTableColumns();

        // Bind UI components to view model
        bindUIToViewModel();

        logger.debug("PvExploreController initialized successfully");
    }

    private void setupTableColumns() {
        // Set up column cell value factories
        selectColumn.setCellValueFactory(new PropertyValueFactory<>("selected"));
        pvNameColumn.setCellValueFactory(new PropertyValueFactory<>("pvName"));
        providerNameColumn.setCellValueFactory(new PropertyValueFactory<>("providerName"));
        dataTypeColumn.setCellValueFactory(new PropertyValueFactory<>("dataType"));
        timestampsTypeColumn.setCellValueFactory(new PropertyValueFactory<>("timestampsType"));
        samplePeriodColumn.setCellValueFactory(new PropertyValueFactory<>("samplePeriod"));
        firstDataTimestampColumn.setCellValueFactory(new PropertyValueFactory<>("firstDataTimestamp"));
        lastDataTimestampColumn.setCellValueFactory(new PropertyValueFactory<>("lastDataTimestamp"));
        numBucketsColumn.setCellValueFactory(new PropertyValueFactory<>("numBuckets"));

        // Set up checkbox column
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
        selectColumn.setEditable(true);

        // Set up PV name column as hyperlinks
        pvNameColumn.setCellFactory(createPvNameHyperlinkCellFactory());
        
        // Set up Provider name column as hyperlinks
        providerNameColumn.setCellFactory(createProviderNameHyperlinkCellFactory());

        // Make table editable for checkboxes
        resultsTable.setEditable(true);

        // Set up checkbox change handling for "Add Selected" button
        // We'll set up listeners for checkbox changes in bindUIToViewModel()

        // Set up "select all" functionality in header
        CheckBox headerCheckBox = new CheckBox();
        headerCheckBox.setOnAction(e -> {
            boolean selectAll = headerCheckBox.isSelected();
            for (PvInfoTableRow row : resultsTable.getItems()) {
                row.setSelected(selectAll);
            }
            updateAddSelectedButtonState();
        });
        selectColumn.setGraphic(headerCheckBox);
    }

    private Callback<TableColumn<PvInfoTableRow, String>, TableCell<PvInfoTableRow, String>> createPvNameHyperlinkCellFactory() {
        return column -> new TableCell<PvInfoTableRow, String>() {
            private final Hyperlink hyperlink = new Hyperlink();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    hyperlink.setText(item);
                    hyperlink.setOnAction(e -> {
                        // Add this PV name to the Query PVs list
                        viewModel.addPvNameToQueryList(item);
                    });
                    setGraphic(hyperlink);
                }
            }
        };
    }

    private Callback<TableColumn<PvInfoTableRow, String>, TableCell<PvInfoTableRow, String>> createProviderNameHyperlinkCellFactory() {
        return column -> new TableCell<PvInfoTableRow, String>() {
            private final Hyperlink hyperlink = new Hyperlink();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null || item.trim().isEmpty()) {
                    setGraphic(null);
                } else {
                    PvInfoTableRow tableRow = getTableRow().getItem();
                    if (tableRow != null) {
                        hyperlink.setText(item);
                        hyperlink.setOnAction(e -> {
                            // Navigate to provider-explore view and search for this provider
                            navigateToProviderExplore(tableRow.getLastProviderId());
                        });
                        setGraphic(hyperlink);
                    }
                }
            }
        };
    }

    private void bindUIToViewModel() {
        // Bind search UI to view model
        pvSearchTextField.textProperty().bindBidirectional(viewModel.pvSearchTextProperty());
        nameListRadio.selectedProperty().bindBidirectional(viewModel.searchByNameListProperty());
        searchButton.disableProperty().bind(viewModel.isSearchingProperty());
        
        // Bind results table
        resultsTable.setItems(viewModel.getSearchResults());
        resultsStatusLabel.textProperty().bind(viewModel.statusMessageProperty());
        
        // Set up listeners for checkbox changes to enable/disable "Add Selected" button
        viewModel.getSearchResults().addListener((javafx.collections.ListChangeListener<PvInfoTableRow>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    // Add listeners to new items
                    for (PvInfoTableRow row : change.getAddedSubList()) {
                        row.selectedProperty().addListener((obs, oldVal, newVal) -> {
                            updateAddSelectedButtonState();
                        });
                    }
                }
            }
            updateAddSelectedButtonState();
        });
        
        // Initial state
        updateAddSelectedButtonState();
    }

    private void updateAddSelectedButtonState() {
        // Enable "Add Selected" button when there are selected items
        boolean hasSelectedItems = resultsTable.getItems().stream().anyMatch(PvInfoTableRow::isSelected);
        addSelectedButton.setDisable(!hasSelectedItems);
    }

    // Dependency injection methods
    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        
        // Inject into view model and query component
        if (viewModel != null) {
            viewModel.setDpApplication(dpApplication);
        }
        if (queryPvsComponent != null) {
            queryPvsComponent.setDpApplication(dpApplication);
        }
        
        logger.debug("DpApplication injected into PvExploreController");
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        logger.debug("Primary stage injected into PvExploreController");
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        
        // Inject into view model and query component
        if (viewModel != null) {
            viewModel.setMainController(mainController);
        }
        if (queryPvsComponent != null) {
            queryPvsComponent.setMainController(mainController);
        }
        
        logger.debug("MainController injected into PvExploreController");
    }

    public PvExploreViewModel getViewModel() {
        return viewModel;
    }
    
    private void navigateToProviderExplore(String providerId) {
        logger.debug("Navigating to provider-explore view with provider ID: {}", providerId);
        
        if (mainController != null) {
            // Navigate to provider-explore view and trigger search
            mainController.navigateToProviderExploreWithSearch(providerId);
        } else {
            logger.warn("MainController is null, cannot navigate to provider-explore view");
        }
    }

    // FXML action handlers
    @FXML
    private void onSearch() {
        /*
         * The "Add Selected" button state is kept current by the single listener registered in
         * bindUIToViewModel(), which watches the same ObservableList this table is backed by --
         * resultsTable.setItems(viewModel.getSearchResults()) makes them one instance.
         *
         * Nothing is registered here.  A listener added on each click would never be removed, so
         * every search would leave another permanently-retained copy doing work the existing one
         * already does; it would also be attached AFTER searchPvMetadata() starts its background
         * task, so it could not see the results of the search that registered it.
         */
        viewModel.searchPvMetadata();
    }

    @FXML
    private void onAddSelected() {
        // Collect selected items
        viewModel.getSelectedSearchResults().clear();
        for (PvInfoTableRow row : resultsTable.getItems()) {
            if (row.isSelected()) {
                viewModel.getSelectedSearchResults().add(row);
            }
        }
        
        // Add them to query list
        viewModel.addSelectedResultsToPvList();
        
        // Update button state
        updateAddSelectedButtonState();
    }
}