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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataImportViewModel {

    private static final Logger logger = LogManager.getLogger();

    // Provider Details properties
    private final StringProperty providerName = new SimpleStringProperty("");
    private final StringProperty providerDescription = new SimpleStringProperty("");
    private final ObservableList<String> providerTags = FXCollections.observableArrayList();
    private final ObservableList<String> providerAttributes = FXCollections.observableArrayList();

    // Request Details properties
    private final ObservableList<String> requestTags = FXCollections.observableArrayList();
    private final ObservableList<String> requestAttributes = FXCollections.observableArrayList();
    private final StringProperty eventName = new SimpleStringProperty("");

    // Import Details properties
    private final StringProperty filePath = new SimpleStringProperty("");
    private final ObservableList<DataImportResult.DataFrameResult> ingestionDataFrames = FXCollections.observableArrayList();

    // Status properties
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final BooleanProperty isIngesting = new SimpleBooleanProperty(false);

    // Dependencies
    private DpApplication dpApplication;
    private MainController mainController;

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

    public ObservableList<String> getProviderTags() {
        return providerTags;
    }

    public ObservableList<String> getProviderAttributes() {
        return providerAttributes;
    }

    // Request Details property methods
    public ObservableList<String> getRequestTags() {
        return requestTags;
    }

    public ObservableList<String> getRequestAttributes() {
        return requestAttributes;
    }

    public StringProperty eventNameProperty() {
        return eventName;
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
        providerTags.clear();
        providerAttributes.clear();
        
        // Clear request details
        requestTags.clear();
        requestAttributes.clear();
        eventName.set("");
        
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
        // Validate provider name is not empty (section 13.2.1 requirement)
        String providerNameValue = providerName.get();
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
        // Convert provider attributes list to map
        Map<String, String> attributesMap = convertAttributesToMap(providerAttributes);
        
        return dpApplication.registerProvider(
            providerName.get(),
            providerDescription.get(),
            List.copyOf(providerTags),
            attributesMap
        );
    }

    private ResultStatus performDataIngestion() {
        // Convert request attributes list to map
        Map<String, String> requestAttributesMap = convertAttributesToMap(requestAttributes);
        
        return dpApplication.ingestImportedData(
            List.copyOf(requestTags),
            requestAttributesMap,
            eventName.get().trim().isEmpty() ? null : eventName.get(),
            List.copyOf(ingestionDataFrames)
        );
    }

    private Map<String, String> convertAttributesToMap(ObservableList<String> attributesList) {
        Map<String, String> attributesMap = new HashMap<>();
        for (String attribute : attributesList) {
            if (attribute != null && attribute.contains("=")) {
                String[] parts = attribute.split("=", 2);
                if (parts.length == 2) {
                    attributesMap.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return attributesMap;
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