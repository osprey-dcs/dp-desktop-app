package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.result.DataImportResult;
import com.ospreydcs.dp.client.utility.DataImportUtility;
import com.ospreydcs.dp.gui.component.ProviderDetailsComponent;
import com.ospreydcs.dp.gui.component.RequestDetailsComponent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class DataImportController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    // Provider and Request Details components
    @FXML private ProviderDetailsComponent providerDetailsComponent;
    @FXML private RequestDetailsComponent requestDetailsComponent;

    // Import Details FXML components
    @FXML private TextField filePathField;
    @FXML private Button importButton;
    @FXML private ListView<DataImportResult.DataFrameResult> ingestionDataFramesList;

    // Action buttons
    @FXML private Button ingestButton;
    @FXML private Button cancelButton;

    // Dependencies
    private DataImportViewModel viewModel;
    private DpApplication dpApplication;
    private Stage primaryStage;
    private MainController mainController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("DataImportController initializing...");
        
        // Create the view model
        viewModel = new DataImportViewModel();
        
        // Bind UI components to view model properties
        bindUIToViewModel();
        
        // Set up event handlers
        setupEventHandlers();
        
        logger.debug("DataImportController initialized successfully");
    }

    private void bindUIToViewModel() {
        // Provider Details component bindings
        providerDetailsComponent.providerNameProperty().bindBidirectional(viewModel.providerNameProperty());
        providerDetailsComponent.providerDescriptionProperty().bindBidirectional(viewModel.providerDescriptionProperty());
        providerDetailsComponent.setProviderTags(viewModel.getProviderTags());
        providerDetailsComponent.setProviderAttributes(viewModel.getProviderAttributes());

        // Request Details component bindings
        requestDetailsComponent.setRequestTags(viewModel.getRequestTags());
        requestDetailsComponent.setRequestAttributes(viewModel.getRequestAttributes());
        requestDetailsComponent.eventNameProperty().bindBidirectional(viewModel.eventNameProperty());

        // Import Details bindings
        filePathField.textProperty().bindBidirectional(viewModel.filePathProperty());
        ingestionDataFramesList.setItems(viewModel.getIngestionDataFrames());
        
        // Set up custom cell factory for data frames display
        ingestionDataFramesList.setCellFactory(listView -> new ListCell<DataImportResult.DataFrameResult>() {
            @Override
            protected void updateItem(DataImportResult.DataFrameResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    // Create display string similar to Calculations Data Frames
                    StringBuilder displayText = new StringBuilder(item.sheetName);
                    if (item.columns != null && !item.columns.isEmpty()) {
                        displayText.append(" - ");
                        int maxColumns = Math.min(3, item.columns.size());
                        for (int i = 0; i < maxColumns; i++) {
                            if (i > 0) displayText.append(", ");
                            displayText.append(item.columns.get(i).getName());
                        }
                        if (item.columns.size() > 3) {
                            displayText.append("...");
                        }
                    }
                    setText(displayText.toString());
                }
            }
        });
        
        // Show placeholder message when list is empty
        ingestionDataFramesList.setPlaceholder(new Label("Use the Import button to import PV time-series data from an Excel file."));
        
        // Button state bindings
        ingestButton.disableProperty().bind(viewModel.isIngestingProperty());
    }

    private void setupEventHandlers() {
        // Set up status listener for MainController communication
        setupStatusListener();
    }
    
    private void setupStatusListener() {
        // Connect ViewModel status messages to MainController status display
        if (viewModel != null && mainController != null) {
            viewModel.statusMessageProperty().addListener((obs, oldStatus, newStatus) -> {
                if (newStatus != null && !newStatus.trim().isEmpty()) {
                    mainController.getViewModel().updateStatus(newStatus);
                }
            });
            logger.debug("Status listener established between DataImportViewModel and MainController");
        }
    }

    // Dependency injection methods
    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        if (viewModel != null) {
            viewModel.setDpApplication(dpApplication);
        }
        logger.debug("DpApplication injected into DataImportController");
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        logger.debug("Primary stage injected into DataImportController");
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        
        // Inject MainController into ViewModel for home view updates
        if (viewModel != null) {
            viewModel.setMainController(mainController);
        }
        
        // Set up status message forwarding
        setupStatusListener();
        
        logger.debug("MainController injected into DataImportController");
    }

    // Action handlers
    @FXML
    private void onImport() {
        logger.info("Import button clicked");
        
        // Create file chooser for Excel files
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Excel File to Import");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        // Show file chooser dialog
        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile != null) {
            viewModel.importFromFile(selectedFile);
        }
    }

    @FXML
    private void onIngest() {
        logger.info("Ingest button clicked");
        // TODO: Implement data ingestion - placeholder for now
        if (mainController != null) {
            mainController.getViewModel().updateStatus("Ingest action not implemented yet");
        }
    }

    @FXML
    private void onCancel() {
        logger.info("Cancel button clicked");
        // Navigate back to main window
        if (mainController != null) {
            mainController.switchToMainView();
        } else {
            logger.warn("MainController reference is null, cannot navigate back");
        }
    }
}