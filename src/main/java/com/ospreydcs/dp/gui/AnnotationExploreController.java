package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.result.GetCalculationsApiResult;
import com.ospreydcs.dp.grpc.v1.annotation.Calculations;
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
import java.util.List;
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
                if (tableRow != null && tableRow.hasCalculations()) {
                    // one link per row rather than one per frame: queryAnnotations() no longer
                    // returns frame names, so they are resolved by the fetch this link triggers
                    Hyperlink calculationsLink = new Hyperlink(item);
                    calculationsLink.getStyleClass().addAll("hyperlink-small");
                    calculationsLink.setOnAction(e -> openCalculations(tableRow));
                    
                    content.getChildren().add(calculationsLink);
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
    
    /**
     * Fetches this annotation's calculations and opens a frame from them.
     *
     * The fetch happens here, on user action, rather than per row at query time: queryAnnotations()
     * returns calculationsId without content as of dp-grpc #132, and resolving names per row would
     * rebuild client-side the N+1 fan-out that change removed.  One request per click, none per
     * row.  See plan/tickets/42 D1.
     */
    private void openCalculations(AnnotationInfoTableRow tableRow) {
        final String calculationsId = tableRow.getCalculationsId();
        logger.info("Fetching calculations {} for annotation {}", calculationsId, tableRow.getId());
        
        if (dpApplication == null) {
            logger.warn("Cannot fetch calculations - DpApplication not set");
            return;
        }
        
        // fetch off the FX thread: this is a service round trip, not a local lookup as it was when
        // the content arrived denormalized in the query result
        javafx.concurrent.Task<Calculations> fetchTask = new javafx.concurrent.Task<Calculations>() {
            @Override
            protected Calculations call() throws Exception {
                final GetCalculationsApiResult apiResult = dpApplication.getCalculations(calculationsId);
                
                if (apiResult == null) {
                    throw new RuntimeException("null response from service");
                }
                
                // a missing record is a rejection rather than an empty result, so it is
                // distinguished from a service failure rather than reported as one
                if (apiResult.isReject()) {
                    throw new RuntimeException("calculations not found: " + calculationsId);
                }
                
                if (apiResult.resultStatus.isError) {
                    throw new RuntimeException(apiResult.resultStatus.msg);
                }
                
                if (apiResult.calculations == null) {
                    throw new RuntimeException("calculations not found: " + calculationsId);
                }
                
                return apiResult.calculations;
            }
        };
        
        fetchTask.setOnSucceeded(e -> showCalculationsFrames(fetchTask.getValue()));
        
        fetchTask.setOnFailed(e -> {
            final Throwable exception = fetchTask.getException();
            logger.error("Failed to fetch calculations {}", calculationsId, exception);
            
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to load calculations");
            alert.setContentText("An error occurred: "
                    + (exception != null ? exception.getMessage() : "unknown error"));
            alert.showAndWait();
        });
        
        Thread fetchThread = new Thread(fetchTask);
        fetchThread.setDaemon(true);
        fetchThread.start();
    }
    
    /**
     * Opens the frame detail dialog for fetched calculations, prompting for a frame first when
     * there is more than one.  The frame names are only known at this point -- they are what the
     * fetch resolved.
     */
    private void showCalculationsFrames(Calculations calculations) {
        final List<Calculations.CalculationsDataFrame> frames =
                calculations.getCalculationDataFramesList();
        
        if (frames.isEmpty()) {
            logger.warn("Calculations {} contains no data frames", calculations.getId());
            
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Data Frames");
            alert.setHeaderText("Calculations: " + calculations.getId());
            alert.setContentText("This annotation's calculations contain no data frames.");
            alert.showAndWait();
            return;
        }
        
        if (frames.size() == 1) {
            openFrameDialog(frames.get(0));
            return;
        }
        
        // more than one frame, so let the user pick which to open
        final List<String> frameNames = frames.stream()
                .map(Calculations.CalculationsDataFrame::getName)
                .collect(java.util.stream.Collectors.toList());
        
        ChoiceDialog<String> chooser = new ChoiceDialog<>(frameNames.get(0), frameNames);
        chooser.setTitle("Calculation Data Frames");
        chooser.setHeaderText(frames.size() + " data frames");
        chooser.setContentText("Select a frame to view:");
        
        chooser.showAndWait().ifPresent(selectedName -> frames.stream()
                .filter(frame -> selectedName.equals(frame.getName()))
                .findFirst()
                .ifPresent(this::openFrameDialog));
    }
    
    private void openFrameDialog(Calculations.CalculationsDataFrame frame) {
        logger.info("Opening calculation frame details dialog for frame: {}", frame.getName());
        
        try {
            // the dialog consumes DataFrameDetails rather than protobuf, so it is unaffected by
            // the #132 nesting change
            com.ospreydcs.dp.gui.component.CalculationFrameDetailsDialogController.showDialog(
                    DataFrameDetails.fromCalculationsDataFrame(frame), primaryStage);
            
        } catch (Exception e) {
            logger.error("Error opening calculation frame details dialog", e);
            
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