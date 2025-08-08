package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.model.PvDetail;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

public class DataGenerationViewModel {

    private static final Logger logger = LogManager.getLogger();

    // Provider Details properties
    private final StringProperty providerName = new SimpleStringProperty();
    private final StringProperty providerDescription = new SimpleStringProperty();
    private final ObservableList<String> providerTags = FXCollections.observableArrayList();
    private final ObservableList<String> providerAttributes = FXCollections.observableArrayList();

    // Request Details properties
    private final ObjectProperty<LocalDate> dataBeginDate = new SimpleObjectProperty<>(LocalDate.now());
    private final IntegerProperty beginHour = new SimpleIntegerProperty(0);
    private final IntegerProperty beginMinute = new SimpleIntegerProperty(0);
    private final IntegerProperty beginSecond = new SimpleIntegerProperty(0);
    
    private final ObjectProperty<LocalDate> dataEndDate = new SimpleObjectProperty<>(LocalDate.now());
    private final IntegerProperty endHour = new SimpleIntegerProperty(0);
    private final IntegerProperty endMinute = new SimpleIntegerProperty(0);
    private final IntegerProperty endSecond = new SimpleIntegerProperty(0);
    
    private final ObservableList<String> requestTags = FXCollections.observableArrayList();
    private final ObservableList<String> requestAttributes = FXCollections.observableArrayList();
    private final StringProperty eventName = new SimpleStringProperty();

    // PV Details properties
    private final ObservableList<PvDetail> pvDetails = FXCollections.observableArrayList();
    
    // Current PV entry properties
    private final StringProperty currentPvName = new SimpleStringProperty();
    private final StringProperty currentPvDataType = new SimpleStringProperty("integer");
    private final IntegerProperty currentPvValuesPerSecond = new SimpleIntegerProperty(10);
    private final StringProperty currentPvInitialValue = new SimpleStringProperty();
    private final StringProperty currentPvMaxStep = new SimpleStringProperty();

    // UI state properties
    private final BooleanProperty showPvEntryPanel = new SimpleBooleanProperty(false);
    
    // Status properties
    private final StringProperty statusMessage = new SimpleStringProperty("Ready to generate data");
    private final BooleanProperty isGenerating = new SimpleBooleanProperty(false);

    // Attribute key/value mappings
    private final Map<String, ObservableList<String>> providerAttributeOptions = new HashMap<>();
    private final Map<String, ObservableList<String>> requestAttributeOptions = new HashMap<>();

    private DpApplication dpApplication;
    private MainController mainController;

    public DataGenerationViewModel() {
        initializeAttributeOptions();
        logger.debug("DataGenerationViewModel initialized");
    }

    private void initializeAttributeOptions() {
        // Provider attribute options
        providerAttributeOptions.put("sector", FXCollections.observableArrayList("1", "2", "3", "4"));
        providerAttributeOptions.put("subsystem", FXCollections.observableArrayList("vacuum", "power", "RF", "mechanical"));
        
        // Request attribute options
        requestAttributeOptions.put("status", FXCollections.observableArrayList("normal", "abnormal"));
        requestAttributeOptions.put("mode", FXCollections.observableArrayList("live", "batch"));
    }

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        logger.debug("DpApplication injected into DataGenerationViewModel");
    }
    
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        logger.debug("MainController injected into DataGenerationViewModel");
    }

    // Provider Details property getters
    public StringProperty providerNameProperty() { return providerName; }
    public StringProperty providerDescriptionProperty() { return providerDescription; }
    public ObservableList<String> getProviderTags() { return providerTags; }
    public ObservableList<String> getProviderAttributes() { return providerAttributes; }

    // Request Details property getters
    public ObjectProperty<LocalDate> dataBeginDateProperty() { return dataBeginDate; }
    public IntegerProperty beginHourProperty() { return beginHour; }
    public IntegerProperty beginMinuteProperty() { return beginMinute; }
    public IntegerProperty beginSecondProperty() { return beginSecond; }
    
    public ObjectProperty<LocalDate> dataEndDateProperty() { return dataEndDate; }
    public IntegerProperty endHourProperty() { return endHour; }
    public IntegerProperty endMinuteProperty() { return endMinute; }
    public IntegerProperty endSecondProperty() { return endSecond; }
    
    public ObservableList<String> getRequestTags() { return requestTags; }
    public ObservableList<String> getRequestAttributes() { return requestAttributes; }
    public StringProperty eventNameProperty() { return eventName; }

    // PV Details property getters
    public ObservableList<PvDetail> getPvDetails() { return pvDetails; }
    public StringProperty currentPvNameProperty() { return currentPvName; }
    public StringProperty currentPvDataTypeProperty() { return currentPvDataType; }
    public IntegerProperty currentPvValuesPerSecondProperty() { return currentPvValuesPerSecond; }
    public StringProperty currentPvInitialValueProperty() { return currentPvInitialValue; }
    public StringProperty currentPvMaxStepProperty() { return currentPvMaxStep; }

    // UI state property getters
    public BooleanProperty showPvEntryPanelProperty() { return showPvEntryPanel; }

    // Status property getters
    public StringProperty statusMessageProperty() { return statusMessage; }
    public BooleanProperty isGeneratingProperty() { return isGenerating; }

    // Attribute options getters
    public ObservableList<String> getProviderAttributeValues(String key) {
        return providerAttributeOptions.getOrDefault(key, FXCollections.observableArrayList());
    }

    public ObservableList<String> getRequestAttributeValues(String key) {
        return requestAttributeOptions.getOrDefault(key, FXCollections.observableArrayList());
    }

    // Business logic methods
    public void addProviderTag(String tag) {
        if (tag != null && !tag.trim().isEmpty() && !providerTags.contains(tag)) {
            providerTags.add(tag);
            logger.debug("Added provider tag: {}", tag);
        }
    }

    public void removeProviderTag(String tag) {
        providerTags.remove(tag);
        logger.debug("Removed provider tag: {}", tag);
    }

    public void addProviderAttribute(String key, String value) {
        if (key != null && value != null && !key.trim().isEmpty() && !value.trim().isEmpty()) {
            String attribute = key + ": " + value;
            if (!providerAttributes.contains(attribute)) {
                providerAttributes.add(attribute);
                logger.debug("Added provider attribute: {}", attribute);
            }
        }
    }

    public void removeProviderAttribute(String attribute) {
        providerAttributes.remove(attribute);
        logger.debug("Removed provider attribute: {}", attribute);
    }

    public void addRequestTag(String tag) {
        if (tag != null && !tag.trim().isEmpty() && !requestTags.contains(tag)) {
            requestTags.add(tag);
            logger.debug("Added request tag: {}", tag);
        }
    }

    public void removeRequestTag(String tag) {
        requestTags.remove(tag);
        logger.debug("Removed request tag: {}", tag);
    }

    public void addRequestAttribute(String key, String value) {
        if (key != null && value != null && !key.trim().isEmpty() && !value.trim().isEmpty()) {
            String attribute = key + ": " + value;
            if (!requestAttributes.contains(attribute)) {
                requestAttributes.add(attribute);
                logger.debug("Added request attribute: {}", attribute);
            }
        }
    }

    public void removeRequestAttribute(String attribute) {
        requestAttributes.remove(attribute);
        logger.debug("Removed request attribute: {}", attribute);
    }

    public void showPvEntryPanel() {
        clearCurrentPvEntry();
        showPvEntryPanel.set(true);
        logger.debug("Showing PV entry panel");
    }

    public void hidePvEntryPanel() {
        showPvEntryPanel.set(false);
        logger.debug("Hiding PV entry panel");
    }

    public void addCurrentPvDetail() {
        if (isCurrentPvDetailValid()) {
            PvDetail pvDetail = new PvDetail(
                currentPvName.get(),
                currentPvDataType.get(),
                currentPvValuesPerSecond.get(),
                currentPvInitialValue.get(),
                currentPvMaxStep.get()
            );
            
            if (!pvDetails.contains(pvDetail)) {
                pvDetails.add(pvDetail);
                logger.info("Added PV detail: {}", pvDetail.getPvName());
                hidePvEntryPanel();
            } else {
                logger.warn("PV with name {} already exists", pvDetail.getPvName());
                statusMessage.set("PV with this name already exists");
            }
        } else {
            logger.warn("Current PV detail is not valid");
            statusMessage.set("Please fill in all required PV fields");
        }
    }

    public void removePvDetail(PvDetail pvDetail) {
        pvDetails.remove(pvDetail);
        logger.info("Removed PV detail: {}", pvDetail.getPvName());
    }

    private boolean isCurrentPvDetailValid() {
        return currentPvName.get() != null && !currentPvName.get().trim().isEmpty() &&
               currentPvDataType.get() != null && !currentPvDataType.get().trim().isEmpty() &&
               currentPvInitialValue.get() != null && !currentPvInitialValue.get().trim().isEmpty() &&
               currentPvMaxStep.get() != null && !currentPvMaxStep.get().trim().isEmpty();
    }

    private void clearCurrentPvEntry() {
        currentPvName.set("");
        currentPvDataType.set("integer");
        currentPvValuesPerSecond.set(10);
        currentPvInitialValue.set("");
        currentPvMaxStep.set("");
    }

    public LocalDateTime getBeginDateTime() {
        LocalTime beginTime = LocalTime.of(beginHour.get(), beginMinute.get(), beginSecond.get());
        return LocalDateTime.of(dataBeginDate.get(), beginTime);
    }

    public LocalDateTime getEndDateTime() {
        LocalTime endTime = LocalTime.of(endHour.get(), endMinute.get(), endSecond.get());
        return LocalDateTime.of(dataEndDate.get(), endTime);
    }
    
    public int getBucketSizeSeconds() {
        return 1; // Always use 1 second bucket size
    }

    public void generateData() {
        if (!isFormValid()) {
            statusMessage.set("Please fill in all required fields");
            return;
        }
        
        if (dpApplication == null) {
            statusMessage.set("DpApplication not initialized");
            return;
        }

        isGenerating.set(true);
        statusMessage.set("Registering provider...");
        
        try {
            logger.info("Starting data generation for {} PVs", pvDetails.size());
            logger.info("Provider: {}", providerName.get());
            logger.info("Time range: {} to {}", getBeginDateTime(), getEndDateTime());
            
            // Step 1: Register provider (5.2.2)
            Map<String, String> providerAttributesMap = convertAttributesToMap(providerAttributes);
            com.ospreydcs.dp.service.common.model.ResultStatus registerResult = dpApplication.registerProvider(
                providerName.get(),
                providerDescription.get(),
                new java.util.ArrayList<>(providerTags),
                providerAttributesMap
            );
            
            if (registerResult.isError) {
                statusMessage.set("Provider registration failed: " + registerResult.msg);
                logger.error("Provider registration failed: {}", registerResult.msg);
                return;
            }
            
            logger.info("Provider registered successfully: {}", registerResult.msg);
            statusMessage.set("Generating and ingesting data...");
            
            // Step 2: Generate and ingest data (5.2.3)
            Map<String, String> requestAttributesMap = convertAttributesToMap(requestAttributes);
            java.time.Instant beginInstant = getBeginDateTime().atZone(java.time.ZoneId.systemDefault()).toInstant();
            java.time.Instant endInstant = getEndDateTime().atZone(java.time.ZoneId.systemDefault()).toInstant();
            
            com.ospreydcs.dp.service.common.model.ResultStatus ingestResult = dpApplication.generateAndIngestData(
                beginInstant,
                endInstant,
                new java.util.ArrayList<>(requestTags),
                requestAttributesMap,
                new java.util.ArrayList<>(pvDetails),
                getBucketSizeSeconds()
            );
            
            if (ingestResult.isError) {
                statusMessage.set("Data generation failed: " + ingestResult.msg);
                logger.error("Data generation failed: {}", ingestResult.msg);
                return;
            }
            
            // Success!
            statusMessage.set("Data generation completed successfully: " + ingestResult.msg);
            logger.info("Data generation completed successfully: {}", ingestResult.msg);
            
            // Notify home view of successful data generation
            if (mainController != null) {
                mainController.onDataGenerationSuccess(ingestResult.msg);
                
                // Navigate back to home view after successful operation
                javafx.application.Platform.runLater(() -> {
                    try {
                        Thread.sleep(2000); // Brief delay to show success message
                        mainController.switchToMainView();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("Interrupted while waiting to navigate to home view", e);
                        mainController.switchToMainView();
                    }
                });
            }
            
        } catch (Exception e) {
            logger.error("Error during data generation", e);
            statusMessage.set("Error during data generation: " + e.getMessage());
        } finally {
            isGenerating.set(false);
        }
    }
    
    private Map<String, String> convertAttributesToMap(ObservableList<String> attributeList) {
        Map<String, String> attributeMap = new java.util.HashMap<>();
        for (String attribute : attributeList) {
            String[] parts = attribute.split(":", 2); // Split into at most 2 parts
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                attributeMap.put(key, value);
            } else {
                logger.warn("Invalid attribute format: {}", attribute);
            }
        }
        return attributeMap;
    }

    private boolean isFormValid() {
        // Validate Provider Details section (5.2.1.1)
        if (providerName.get() == null || providerName.get().trim().isEmpty()) {
            logger.warn("Provider name is required");
            return false;
        }
        
        // Validate Request Details section (5.2.1.2)
        if (dataBeginDate.get() == null) {
            logger.warn("Data begin date is required");
            return false;
        }
        
        if (dataEndDate.get() == null) {
            logger.warn("Data end date is required");
            return false;
        }
        
        
        if (!getBeginDateTime().isBefore(getEndDateTime())) {
            logger.warn("Begin time: {} must be before end time: {}", getBeginDateTime(), getEndDateTime());
            return false;
        }
        
        // Validate PV Details section (5.2.1.3)
        if (pvDetails.isEmpty()) {
            logger.warn("At least one PV detail is required");
            return false;
        }
        
        // Validate that all PV details have required fields
        for (PvDetail pvDetail : pvDetails) {
            if (!isPvDetailValid(pvDetail)) {
                logger.warn("PV detail {} is missing required fields", pvDetail.getPvName());
                return false;
            }
        }
        
        return true;
    }
    
    private boolean isPvDetailValid(PvDetail pvDetail) {
        return pvDetail.getPvName() != null && !pvDetail.getPvName().trim().isEmpty() &&
               pvDetail.getDataType() != null && !pvDetail.getDataType().trim().isEmpty() &&
               pvDetail.getValuesPerSecond() > 0 &&
               pvDetail.getInitialValue() != null && !pvDetail.getInitialValue().trim().isEmpty() &&
               pvDetail.getMaxStepMagnitude() != null && !pvDetail.getMaxStepMagnitude().trim().isEmpty();
    }

    public void cancel() {
        logger.info("Data generation cancelled by user");
        statusMessage.set("Operation cancelled");
    }

    public void updateStatus(String message) {
        statusMessage.set(message);
        logger.debug("Status updated: {}", message);
    }
}