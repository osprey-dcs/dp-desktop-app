package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.result.DataImportResult;
import com.ospreydcs.dp.client.utility.DataImportUtility;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

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

    private void resetImportDetails() {
        logger.debug("Resetting import details");
        
        // Clear file path
        filePath.set("");
        
        // Clear data frames list
        ingestionDataFrames.clear();
        
        logger.debug("Import details reset completed");
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

    private void updateStatus(String message) {
        logger.debug("Updating status: {}", message);
        statusMessage.set(message);
    }
}