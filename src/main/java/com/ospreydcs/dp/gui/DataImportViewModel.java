package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.result.DataImportResult;
import com.ospreydcs.dp.client.utility.DataImportUtility;
import com.ospreydcs.dp.service.common.model.ResultStatus;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DataImportViewModel {

    private static final Logger logger = LogManager.getLogger();

    // Provider Details properties - only used for property binding, actual data comes from components
    private final StringProperty providerName = new SimpleStringProperty("");
    private final StringProperty providerDescription = new SimpleStringProperty("");

    // Import Details properties
    private final StringProperty filePath = new SimpleStringProperty("");
    private final ObservableList<DataImportResult.DataFrameResult> ingestionDataFrames = FXCollections.observableArrayList();

    // Status properties
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final BooleanProperty isIngesting = new SimpleBooleanProperty(false);

    // Dependencies
    private DpApplication dpApplication;
    private MainController mainController;
    
    // Component references for accessing component data
    private com.ospreydcs.dp.gui.component.ProviderDetailsComponent providerDetailsComponent;
    private com.ospreydcs.dp.gui.component.ColumnMetadataComponent columnMetadataComponent;
    private com.ospreydcs.dp.gui.component.SubscriptionDetailsComponent subscriptionDetailsComponent;

    public DataImportViewModel() {
        logger.debug("DataImportViewModel created");
    }

    // Provider Details property methods
    public StringProperty providerNameProperty() {
        return providerName;
    }

    public StringProperty providerDescriptionProperty() {
        return providerDescription;
    }


    // Import Details property methods
    public StringProperty filePathProperty() {
        return filePath;
    }

    public ObservableList<DataImportResult.DataFrameResult> getIngestionDataFrames() {
        return ingestionDataFrames;
    }

    // Status property methods
    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public BooleanProperty isIngestingProperty() {
        return isIngesting;
    }

    // Dependency injection
    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        logger.debug("DpApplication injected into DataImportViewModel");
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        logger.debug("MainController injected into DataImportViewModel");
    }
    
    // Component injection methods
    public void setProviderDetailsComponent(com.ospreydcs.dp.gui.component.ProviderDetailsComponent component) {
        this.providerDetailsComponent = component;
        logger.debug("ProviderDetailsComponent injected into DataImportViewModel");
    }
    
    public void setColumnMetadataComponent(com.ospreydcs.dp.gui.component.ColumnMetadataComponent component) {
        this.columnMetadataComponent = component;
        logger.debug("ColumnMetadataComponent injected into DataImportViewModel");
    }
    
    public void setSubscriptionDetailsComponent(com.ospreydcs.dp.gui.component.SubscriptionDetailsComponent component) {
        this.subscriptionDetailsComponent = component;
        logger.debug("SubscriptionDetailsComponent injected into DataImportViewModel");
    }

    // Business logic methods
    public void importFromFile(File file) {
        logger.info("Importing data from file: {}", file.getAbsolutePath());
        
        try {
            // Reset import details for subsequent imports (section 13.1.9)
            resetImportDetails();
            
            // Call DataImportUtility to import the Excel file
            DataImportResult importResult = DataImportUtility.importXlsxData(file.getAbsolutePath());
            
            if (importResult.resultStatus.isError) {
                // Import failed
                logger.error("Import failed: {}", importResult.resultStatus.msg);
                updateStatus("Import failed: " + importResult.resultStatus.msg);
            } else {
                // Import succeeded
                logger.info("Import succeeded, {} data frames loaded", importResult.dataFrames.size());
                
                // Set the file path
                filePath.set(file.getAbsolutePath());
                
                // Add all data frames to the list
                ingestionDataFrames.addAll(importResult.dataFrames);
                
                updateStatus("Successfully imported " + importResult.dataFrames.size() + " data frames from " + file.getName());
                
                logger.debug("Import completed successfully");
            }
            
        } catch (Exception e) {
            logger.error("Exception during file import", e);
            updateStatus("Error importing file: " + e.getMessage());
        }
    }


    public void clearAllFields() {
        logger.debug("Clearing all fields");
        
        // Clear provider details
        providerName.set("");
        providerDescription.set("");
        if (providerDetailsComponent != null) {
            providerDetailsComponent.clearProviderDetails();
        }
        
        // Clear column metadata
        if (columnMetadataComponent != null) {
            columnMetadataComponent.clearColumnMetadata();
        }
        
        // Clear import details
        resetImportDetails();
        
        // Clear status
        statusMessage.set("");
        isIngesting.set(false);
        
        logger.debug("All fields cleared");
    }

    public void ingestImportedData() {
        if (dpApplication == null) {
            updateStatus("DpApplication not initialized");
            return;
        }

        // Validation
        if (!isIngestValid()) {
            return;
        }

        // Set ingesting state
        isIngesting.set(true);
        updateStatus("Registering provider...");

        // Create background task for ingestion
        Task<Void> ingestTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // Step 1: Register provider (section 13.2.1)
                ResultStatus registerResult = registerProvider();
                if (registerResult.isError) {
                    javafx.application.Platform.runLater(() -> {
                        updateStatus("Provider registration failed: " + registerResult.msg);
                        isIngesting.set(false);
                    });
                    return null;
                }

                // Step 2: Ingest imported data (section 13.2.3)
                javafx.application.Platform.runLater(() -> {
                    updateStatus("Ingesting imported data...");
                });

                ResultStatus ingestResult = performDataIngestion();
                if (ingestResult.isError) {
                    javafx.application.Platform.runLater(() -> {
                        updateStatus("Data ingestion failed: " + ingestResult.msg);
                        isIngesting.set(false);
                    });
                    return null;
                }

                // Success - update UI and return to home view (section 13.2.5)
                javafx.application.Platform.runLater(() -> {
                    updateStatus("Data ingestion completed successfully");
                    isIngesting.set(false);
                    
                    // Note: Application state (hasIngestedData, etc.) is updated in DpApplication.ingestImportedData()
                    
                    // Notify main controller and return to home view
                    if (mainController != null) {
                        mainController.onDataGenerationSuccess(ingestResult.msg + ". Navigate to Data Explorer to query the imported data.");
                        mainController.switchToMainView();
                    }
                    
                    logger.info("Data import and ingestion completed successfully");
                });
                
                return null;
            }
        };

        ingestTask.setOnFailed(e -> {
            logger.error("Data ingestion task failed", ingestTask.getException());
            updateStatus("Data ingestion failed: " + ingestTask.getException().getMessage());
            isIngesting.set(false);
        });

        Thread ingestThread = new Thread(ingestTask);
        ingestThread.setDaemon(true);
        ingestThread.start();
    }

    private boolean isIngestValid() {
        // Validate components are available
        if (providerDetailsComponent == null || columnMetadataComponent == null) {
            updateStatus("Component references not set - cannot access form data");
            return false;
        }
        
        // Validate provider name is not empty (section 13.2.1 requirement)
        String providerNameValue = providerDetailsComponent.getProviderName();
        if (providerNameValue == null || providerNameValue.trim().isEmpty()) {
            updateStatus("Provider name is required for ingestion");
            return false;
        }

        // Validate that we have imported data frames to ingest
        if (ingestionDataFrames.isEmpty()) {
            updateStatus("No imported data available for ingestion. Please import an Excel file first.");
            return false;
        }

        return true;
    }

    private ResultStatus registerProvider() {
        // Get data directly from ProviderDetailsComponent (Critical Integration Pattern)
        var providerTags = providerDetailsComponent.getProviderTags();
        var providerAttributes = providerDetailsComponent.getProviderAttributes();
        
        // Convert provider attributes list to map
        Map<String, String> attributesMap =
            com.ospreydcs.dp.gui.component.AttributesListComponent.attributesToMap(providerAttributes);
        
        return dpApplication.registerProvider(
            providerDetailsComponent.getProviderName(),
            providerDetailsComponent.getProviderDescription(),
            List.copyOf(providerTags),
            attributesMap
        );
    }

    private ResultStatus performDataIngestion() {
        // Get data directly from ColumnMetadataComponent (Critical Integration Pattern)
        com.ospreydcs.dp.grpc.v1.common.ColumnMetadata columnMetadata =
            columnMetadataComponent.getColumnMetadata();

        // Get subscription details from component (Critical Integration Pattern)
        java.util.List<com.ospreydcs.dp.gui.model.SubscribeDataEventDetail> subscriptions = 
            subscriptionDetailsComponent != null ? 
                subscriptionDetailsComponent.getSubscriptions() : 
                new ArrayList<>();
        
        return dpApplication.ingestImportedData(
            columnMetadata,
            List.copyOf(ingestionDataFrames),
            new ArrayList<>(subscriptions)
        );
    }

    public void resetImportDetails() {
        logger.debug("Resetting import details (section 13.3)");
        
        // Clear file path
        filePath.set("");
        
        // Clear data frames list
        ingestionDataFrames.clear();
        
        // Update status
        updateStatus("Import details reset");
        
        logger.debug("Import details reset completed");
    }

    private void updateStatus(String message) {
        logger.debug("Updating status: {}", message);
        statusMessage.set(message);
    }
}