package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.model.DatasetInfoTableRow;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;

public class DatasetExploreController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    // FXML components - Query Form
    @FXML private TextField datasetIdField;
    @FXML private TextField ownerField;
    @FXML private TextField nameDescriptionField;
    @FXML private TextField pvNameField;
    @FXML private Button searchButton;
    @FXML private Label searchStatusLabel;
    @FXML private ProgressIndicator searchProgressIndicator;

    // FXML components - Results
    @FXML private TableView<DatasetInfoTableRow> resultsTable;
    @FXML private TableColumn<DatasetInfoTableRow, String> idColumn;
    @FXML private TableColumn<DatasetInfoTableRow, String> nameColumn;
    @FXML private TableColumn<DatasetInfoTableRow, String> ownerColumn;
    @FXML private TableColumn<DatasetInfoTableRow, String> descriptionColumn;
    @FXML private TableColumn<DatasetInfoTableRow, String> dataBlocksColumn;
    @FXML private Label resultCountLabel;
    @FXML private Label resultsStatusLabel;

    // Dependencies
    private DpApplication dpApplication;
    private Stage primaryStage;
    private MainController mainController;
    private DatasetExploreViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("DatasetExploreController initializing...");

        // Initialize ViewModel
        viewModel = new DatasetExploreViewModel();
        
        // Set up table columns
        setupTableColumns();
        
        // Bind UI to ViewModel
        bindUIToViewModel();
        
        logger.debug("DatasetExploreController initialized");
    }

    private void setupTableColumns() {
        // Set up standard columns with property binding
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        ownerColumn.setCellValueFactory(new PropertyValueFactory<>("owner"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        dataBlocksColumn.setCellValueFactory(new PropertyValueFactory<>("dataBlocks"));
        
        // Set up ID column with hyperlinks
        idColumn.setCellFactory(createIdHyperlinkCellFactory());
        
        // Set table items to ViewModel results
        resultsTable.setItems(viewModel.getDatasetResults());
        
        logger.debug("Table columns configured");
    }

    private Callback<TableColumn<DatasetInfoTableRow, String>, TableCell<DatasetInfoTableRow, String>> createIdHyperlinkCellFactory() {
        return column -> new TableCell<DatasetInfoTableRow, String>() {
            private final Hyperlink hyperlink = new Hyperlink();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null || item.trim().isEmpty()) {
                    setGraphic(null);
                } else {
                    DatasetInfoTableRow tableRow = getTableRow().getItem();
                    if (tableRow != null) {
                        hyperlink.setText(item);
                        hyperlink.setOnAction(e -> {
                            // Navigate to data-explore view's Dataset Builder tab
                            navigateToDatasetBuilder(tableRow.getId());
                        });
                        setGraphic(hyperlink);
                    }
                }
            }
        };
    }
    
    private void navigateToDatasetBuilder(String datasetId) {
        logger.debug("Navigating to Dataset Builder with dataset ID: {}", datasetId);
        
        if (mainController != null) {
            // Navigate to data-explore view and load dataset in Dataset Builder
            mainController.navigateToDataExploreWithDataset(datasetId);
        } else {
            logger.warn("MainController is null, cannot navigate to Dataset Builder");
        }
    }

    private void bindUIToViewModel() {
        // Bind form fields
        datasetIdField.textProperty().bindBidirectional(viewModel.datasetIdProperty());
        ownerField.textProperty().bindBidirectional(viewModel.ownerProperty());
        nameDescriptionField.textProperty().bindBidirectional(viewModel.nameDescriptionProperty());
        pvNameField.textProperty().bindBidirectional(viewModel.pvNameProperty());
        
        // Bind status labels
        searchStatusLabel.textProperty().bind(viewModel.searchStatusMessageProperty());
        resultsStatusLabel.textProperty().bind(viewModel.statusMessageProperty());
        
        // Bind result count
        // bound to the formatted message rather than to the raw count, so a capped result reads
        // "first N dataset(s)" instead of stating a wrong total
        resultCountLabel.textProperty().bind(viewModel.resultCountMessageProperty());
        
        // Bind progress indicator
        searchProgressIndicator.visibleProperty().bind(viewModel.searchInProgressProperty());
        
        logger.debug("UI bound to ViewModel");
    }

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

    @FXML
    private void onSearch() {
        logger.debug("Search button clicked");
        viewModel.executeSearch();
    }

    public DatasetExploreViewModel getViewModel() {
        return viewModel;
    }
}