package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.model.AnnotationInfoTableRow;
import com.ospreydcs.dp.gui.model.DataFrameDetails;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;

public class AnnotationExploreController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    // FXML components - Query Form
    @FXML private TextField annotationIdField;
    @FXML private TextField ownerField;
    @FXML private TextField relatedDatasetsIdField;
    @FXML private TextField relatedAnnotationsIdField;
    @FXML private TextField nameCommentEventField;
    @FXML private TextField tagValueField;
    @FXML private TextField attributeKeyField;
    @FXML private TextField attributeValueField;
    @FXML private Button searchButton;
    @FXML private Button clearButton;
    @FXML private Label searchStatusLabel;
    @FXML private ProgressIndicator searchProgressIndicator;

    // FXML components - Results
    @FXML private TableView<AnnotationInfoTableRow> resultsTable;
    @FXML private TableColumn<AnnotationInfoTableRow, String> idColumn;
    @FXML private TableColumn<AnnotationInfoTableRow, String> ownerColumn;
    @FXML private TableColumn<AnnotationInfoTableRow, String> relatedDatasetsColumn;
    @FXML private TableColumn<AnnotationInfoTableRow, String> nameColumn;
    @FXML private TableColumn<AnnotationInfoTableRow, String> relatedAnnotationsColumn;
    @FXML private TableColumn<AnnotationInfoTableRow, String> commentColumn;
    @FXML private TableColumn<AnnotationInfoTableRow, String> tagsColumn;
    @FXML private TableColumn<AnnotationInfoTableRow, String> attributesColumn;
    @FXML private TableColumn<AnnotationInfoTableRow, String> calculationsColumn;
    @FXML private Label resultCountLabel;
    @FXML private Label resultsStatusLabel;

    // Dependencies
    private DpApplication dpApplication;
    private Stage primaryStage;
    private MainController mainController;
    private AnnotationExploreViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("AnnotationExploreController initializing...");
        
        // Create the view model
        viewModel = new AnnotationExploreViewModel();
        
        // Set up UI bindings
        setupUIBindings();
        
        // Set up table columns
        setupTableColumns();
        
        logger.debug("AnnotationExploreController initialized successfully");
    }
    
    private void setupUIBindings() {
        // Bind search form fields to ViewModel properties
        annotationIdField.textProperty().bindBidirectional(viewModel.annotationIdProperty());
        ownerField.textProperty().bindBidirectional(viewModel.ownerProperty());
        relatedDatasetsIdField.textProperty().bindBidirectional(viewModel.relatedDatasetsIdProperty());
        relatedAnnotationsIdField.textProperty().bindBidirectional(viewModel.relatedAnnotationsIdProperty());
        nameCommentEventField.textProperty().bindBidirectional(viewModel.nameCommentEventTextProperty());
        tagValueField.textProperty().bindBidirectional(viewModel.tagValueProperty());
        attributeKeyField.textProperty().bindBidirectional(viewModel.attributeKeyProperty());
        attributeValueField.textProperty().bindBidirectional(viewModel.attributeValueProperty());
        
        // Bind UI state properties
        searchStatusLabel.textProperty().bind(viewModel.searchStatusMessageProperty());
        searchProgressIndicator.visibleProperty().bind(viewModel.searchInProgressProperty());
        searchButton.disableProperty().bind(viewModel.searchInProgressProperty());
        resultCountLabel.textProperty().bind(viewModel.resultCountMessageProperty());
        
        // Bind table data
        resultsTable.setItems(viewModel.getSearchResults());
        
        logger.debug("UI bindings established");
    }
    
    private void setupTableColumns() {
        // Set up basic text columns
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        ownerColumn.setCellValueFactory(new PropertyValueFactory<>("owner"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        commentColumn.setCellValueFactory(new PropertyValueFactory<>("comment"));
        tagsColumn.setCellValueFactory(new PropertyValueFactory<>("tags"));
        attributesColumn.setCellValueFactory(new PropertyValueFactory<>("attributes"));
        
        // Set up hyperlink columns
        setupAnnotationIdColumn();
        setupRelatedDatasetsColumn();
        setupRelatedAnnotationsColumn();
        setupCalculationsColumn();
        
        logger.debug("Table columns configured");
    }
    
    private void setupAnnotationIdColumn() {
        idColumn.setCellFactory(column -> new AnnotationIdTableCell());
    }
    
    private void setupRelatedDatasetsColumn() {
        relatedDatasetsColumn.setCellValueFactory(new PropertyValueFactory<>("relatedDatasets"));
        relatedDatasetsColumn.setCellFactory(column -> new DatasetIdsTableCell());
    }
    
    private void setupRelatedAnnotationsColumn() {
        relatedAnnotationsColumn.setCellValueFactory(new PropertyValueFactory<>("relatedAnnotations"));
        relatedAnnotationsColumn.setCellFactory(column -> new AnnotationIdsTableCell());
    }
    
    private void setupCalculationsColumn() {
        calculationsColumn.setCellValueFactory(new PropertyValueFactory<>("calculationsDataFrames"));
        calculationsColumn.setCellFactory(column -> new CalculationsDataFrameTableCell());
    }
    
    // Event handlers
    
    @FXML
    private void onSearch() {
        logger.debug("Search button clicked");
        viewModel.executeSearch();
    }
    
    @FXML 
    private void onClear() {
        logger.debug("Clear button clicked");
        viewModel.clearSearch();
    }
    
    // Custom TableCell implementations for hyperlinks
    
    /**
     * TableCell for Annotation ID column with hyperlink to Annotation Builder.
     */
    private class AnnotationIdTableCell extends TableCell<AnnotationInfoTableRow, String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null || item.trim().isEmpty()) {
                setGraphic(null);
                setText(null);
            } else {
                Hyperlink annotationLink = new Hyperlink(item);
                annotationLink.getStyleClass().addAll("hyperlink-small");
                annotationLink.setOnAction(e -> navigateToAnnotationBuilder(item));
                
                setGraphic(annotationLink);
                setText(null);
            }
        }
    }
    
    /**
     * TableCell for Related Datasets column with hyperlinks to Dataset Builder.
     */
    private class DatasetIdsTableCell extends TableCell<AnnotationInfoTableRow, String> {
        private HBox content;

        public DatasetIdsTableCell() {
            super();
            content = new HBox();
            content.setSpacing(5);
            content.setPadding(new Insets(2, 5, 2, 5));
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null || item.trim().isEmpty()) {
                setGraphic(null);
                setText(null);
            } else {
                content.getChildren().clear();
                
                AnnotationInfoTableRow tableRow = getTableRow().getItem();
                if (tableRow != null) {
                    boolean first = true;
                    for (String datasetId : tableRow.getDataSetIdsList()) {
                        if (!first) {
                            Label separator = new Label(", ");
                            separator.getStyleClass().add("text-muted");
                            content.getChildren().add(separator);
                        }
                        
                        Hyperlink datasetLink = new Hyperlink(datasetId);
                        datasetLink.getStyleClass().addAll("hyperlink-small");
                        datasetLink.setOnAction(e -> navigateToDatasetBuilder(datasetId));
                        
                        content.getChildren().add(datasetLink);
                        first = false;
                    }
                }
                
                setGraphic(content);
                setText(null);
            }
        }
    }
    
    /**
     * TableCell for Related Annotations column with hyperlinks to Annotation Builder.
     */
    private class AnnotationIdsTableCell extends TableCell<AnnotationInfoTableRow, String> {
        private HBox content;

        public AnnotationIdsTableCell() {
            super();
            content = new HBox();
            content.setSpacing(5);
            content.setPadding(new Insets(2, 5, 2, 5));
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null || item.trim().isEmpty()) {
                setGraphic(null);
                setText(null);
            } else {
                content.getChildren().clear();
                
                AnnotationInfoTableRow tableRow = getTableRow().getItem();
                if (tableRow != null) {
                    boolean first = true;
                    for (String annotationId : tableRow.getAnnotationIdsList()) {
                        if (!first) {
                            Label separator = new Label(", ");
                            separator.getStyleClass().add("text-muted");
                            content.getChildren().add(separator);
                        }
                        
                        Hyperlink annotationLink = new Hyperlink(annotationId);
                        annotationLink.getStyleClass().addAll("hyperlink-small");
                        annotationLink.setOnAction(e -> navigateToAnnotationBuilder(annotationId));
                        
                        content.getChildren().add(annotationLink);
                        first = false;
                    }
                }
                
                setGraphic(content);
                setText(null);
            }
        }
    }
    
    /**
     * TableCell for Calculations Data Frames column with hyperlinks to dialog.
     */
    private class CalculationsDataFrameTableCell extends TableCell<AnnotationInfoTableRow, String> {
        private HBox content;

        public CalculationsDataFrameTableCell() {
            super();
            content = new HBox();
            content.setSpacing(5);
            content.setPadding(new Insets(2, 5, 2, 5));
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null || item.trim().isEmpty()) {
                setGraphic(null);
                setText(null);
            } else {
                content.getChildren().clear();
                
                AnnotationInfoTableRow tableRow = getTableRow().getItem();
                if (tableRow != null) {
                    boolean first = true;
                    for (String frameName : tableRow.getCalculationsDataFrameNames()) {
                        if (!first) {
                            Label separator = new Label(", ");
                            separator.getStyleClass().add("text-muted");
                            content.getChildren().add(separator);
                        }
                        
                        Hyperlink frameLink = new Hyperlink(frameName);
                        frameLink.getStyleClass().addAll("hyperlink-small");
                        frameLink.setOnAction(e -> openCalculationFrameDetails(frameName, tableRow));
                        
                        content.getChildren().add(frameLink);
                        first = false;
                    }
                }
                
                setGraphic(content);
                setText(null);
            }
        }
    }
    
    // Navigation methods
    
    private void navigateToAnnotationBuilder(String annotationId) {
        if (mainController != null) {
            logger.debug("Navigating to Annotation Builder with annotation ID: {}", annotationId);
            mainController.navigateToDataExploreWithAnnotation(annotationId);
        } else {
            logger.warn("Cannot navigate to Annotation Builder - MainController not set");
        }
    }
    
    private void navigateToDatasetBuilder(String datasetId) {
        if (mainController != null) {
            logger.debug("Navigating to Dataset Builder with dataset ID: {}", datasetId);
            mainController.navigateToDataExploreWithDataset(datasetId);
        } else {
            logger.warn("Cannot navigate to Dataset Builder - MainController not set");
        }
    }
    
    private void openCalculationFrameDetails(String frameName, AnnotationInfoTableRow tableRow) {
        logger.info("Opening calculation frame details dialog for frame: {}", frameName);
        
        try {
            // Get the DataFrameDetails object from the annotation
            DataFrameDetails frameDetails = tableRow.getCalculationDataFrameByName(frameName);
            
            if (frameDetails != null) {
                // Use the reusable dialog component
                com.ospreydcs.dp.gui.component.CalculationFrameDetailsDialogController.showDialog(frameDetails, primaryStage);
            } else {
                logger.warn("Calculation frame not found: {}", frameName);
                
                // Show error message
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Frame Not Found");
                alert.setHeaderText("Calculation Frame: " + frameName);
                alert.setContentText("The requested calculation frame could not be found in the annotation data.");
                alert.showAndWait();
            }
            
        } catch (Exception e) {
            logger.error("Error opening calculation frame details dialog", e);
            
            // Show error message  
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to open calculation frame details");
            alert.setContentText("An error occurred: " + e.getMessage());
            alert.showAndWait();
        }
    }
    
    // Dependency injection methods
    
    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        if (viewModel != null) {
            viewModel.setDpApplication(dpApplication);
        }
        logger.debug("DpApplication injected into AnnotationExploreController");
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        logger.debug("Primary stage injected into AnnotationExploreController");
    }
    
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        logger.debug("MainController injected into AnnotationExploreController");
    }
    
    public AnnotationExploreViewModel getViewModel() {
        return viewModel;
    }
}