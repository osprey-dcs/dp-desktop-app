package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.model.DataSetDetail;
import com.ospreydcs.dp.gui.model.DataFrameDetails;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ViewModel for the Annotation Builder tab functionality.
 * Manages annotation properties, target datasets, tags, attributes, and UI state.
 */
public class AnnotationBuilderViewModel {

    private static final Logger logger = LogManager.getLogger();

    // Annotation properties
    private final StringProperty annotationId = new SimpleStringProperty("");
    private final StringProperty annotationName = new SimpleStringProperty("");
    private final StringProperty comment = new SimpleStringProperty("");
    private final StringProperty eventName = new SimpleStringProperty("");
    
    // Target datasets
    private final ObservableList<DataSetDetail> dataSets = FXCollections.observableArrayList();
    
    // Calculations data frames
    private final ObservableList<DataFrameDetails> calculationsDataFrames = FXCollections.observableArrayList();
    
    // Tags and attributes - managed by reusable components
    private final ObservableList<String> tags = FXCollections.observableArrayList();
    private final ObservableList<String> attributes = FXCollections.observableArrayList();
    
    // UI state properties
    private final BooleanProperty hasDataSets = new SimpleBooleanProperty(false);
    private final BooleanProperty isAnnotationValid = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("Ready to build annotation");
    
    // Button state properties
    private final BooleanProperty resetButtonEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty saveButtonEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty annotationActionsEnabled = new SimpleBooleanProperty(false);

    public AnnotationBuilderViewModel() {
        logger.debug("AnnotationBuilderViewModel initialized");
        
        // Set up listeners to update button states and validation
        setupPropertyListeners();
    }
    
    private void setupPropertyListeners() {
        // Update hasDataSets property when dataSets list changes
        dataSets.addListener((javafx.collections.ListChangeListener<DataSetDetail>) change -> {
            boolean hasDataSets = !dataSets.isEmpty();
            this.hasDataSets.set(hasDataSets);
            updateAnnotationValidation();
            updateButtonStates();
            logger.debug("Data sets list changed. Count: {}", dataSets.size());
        });
        
        // Update validation when annotation name changes
        annotationName.addListener((obs, oldVal, newVal) -> {
            updateAnnotationValidation();
            updateButtonStates();
        });
        
        // Update button states when other properties change
        comment.addListener((obs, oldVal, newVal) -> updateButtonStates());
        eventName.addListener((obs, oldVal, newVal) -> updateButtonStates());
        
        // Listen to tags and attributes changes for button state updates
        tags.addListener((javafx.collections.ListChangeListener<String>) change -> updateButtonStates());
        attributes.addListener((javafx.collections.ListChangeListener<String>) change -> updateButtonStates());
    }
    
    private void updateAnnotationValidation() {
        boolean nameValid = annotationName.get() != null && !annotationName.get().trim().isEmpty();
        boolean dataSetsValid = !dataSets.isEmpty();
        boolean isValid = nameValid && dataSetsValid;
        
        isAnnotationValid.set(isValid);
        
        if (!nameValid && !dataSetsValid) {
            statusMessage.set("Annotation name and target datasets are required");
        } else if (!nameValid) {
            statusMessage.set("Annotation name is required");
        } else if (!dataSetsValid) {
            statusMessage.set("Target datasets are required");
        } else {
            statusMessage.set("Ready to build annotation");
        }
    }
    
    private void updateButtonStates() {
        boolean hasName = annotationName.get() != null && !annotationName.get().trim().isEmpty();
        boolean hasDataSets = !dataSets.isEmpty();
        boolean hasContent = hasName || 
                            (comment.get() != null && !comment.get().trim().isEmpty()) ||
                            (eventName.get() != null && !eventName.get().trim().isEmpty()) ||
                            !tags.isEmpty() || 
                            !attributes.isEmpty() || 
                            hasDataSets;
        
        // Enable buttons based on content and validation state
        resetButtonEnabled.set(hasContent);
        saveButtonEnabled.set(hasName && hasDataSets); // Both name and datasets required for save
        // Annotation actions combo will be enabled in future implementation
        annotationActionsEnabled.set(false);
        
        logger.debug("Button states updated - Reset: {}, Save: {}, Actions: {}", 
                    resetButtonEnabled.get(), saveButtonEnabled.get(), annotationActionsEnabled.get());
    }
    
    // Public methods for business logic
    
    public void addDataSet(DataSetDetail dataSet) {
        if (dataSet != null && !dataSets.contains(dataSet)) {
            dataSets.add(dataSet);
            logger.debug("Added dataset: {}", dataSet);
        }
    }
    
    public void removeDataSet(DataSetDetail dataSet) {
        if (dataSets.remove(dataSet)) {
            logger.debug("Removed dataset: {}", dataSet);
        }
    }
    
    public void clearDataSets() {
        int count = dataSets.size();
        dataSets.clear();
        logger.debug("Cleared {} datasets", count);
    }
    
    public void resetAnnotation() {
        annotationId.set("");
        annotationName.set("");
        comment.set("");
        eventName.set("");
        clearDataSets();
        tags.clear();
        attributes.clear();
        statusMessage.set("Annotation reset");
        logger.debug("Annotation reset completed");
    }
    
    // Property getters for data binding
    
    public StringProperty annotationIdProperty() { return annotationId; }
    public String getAnnotationId() { return annotationId.get(); }
    public void setAnnotationId(String id) { annotationId.set(id != null ? id : ""); }
    
    public StringProperty annotationNameProperty() { return annotationName; }
    public String getAnnotationName() { return annotationName.get(); }
    public void setAnnotationName(String name) { annotationName.set(name != null ? name : ""); }
    
    public StringProperty commentProperty() { return comment; }
    public String getComment() { return comment.get(); }
    public void setComment(String comment) { this.comment.set(comment != null ? comment : ""); }
    
    public StringProperty eventNameProperty() { return eventName; }
    public String getEventName() { return eventName.get(); }
    public void setEventName(String eventName) { this.eventName.set(eventName != null ? eventName : ""); }
    
    public ObservableList<DataSetDetail> getDataSets() { return dataSets; }
    
    public ObservableList<DataFrameDetails> getCalculationsDataFrames() { return calculationsDataFrames; }
    
    public ObservableList<String> getTags() { return tags; }
    public ObservableList<String> getAttributes() { return attributes; }
    
    public BooleanProperty hasDataSetsProperty() { return hasDataSets; }
    public boolean hasDataSets() { return hasDataSets.get(); }
    
    public BooleanProperty isAnnotationValidProperty() { return isAnnotationValid; }
    public boolean isAnnotationValid() { return isAnnotationValid.get(); }
    
    public StringProperty statusMessageProperty() { return statusMessage; }
    public String getStatusMessage() { return statusMessage.get(); }
    
    public BooleanProperty resetButtonEnabledProperty() { return resetButtonEnabled; }
    public BooleanProperty saveButtonEnabledProperty() { return saveButtonEnabled; }
    public BooleanProperty annotationActionsEnabledProperty() { return annotationActionsEnabled; }
    
    /**
     * Load annotation data from a protobuf Annotation object.
     * Populates the form fields and related data from the loaded annotation.
     */
    public void loadFromAnnotation(com.ospreydcs.dp.grpc.v1.annotation.QueryAnnotationsResponse.AnnotationsResult.Annotation annotation) {
        logger.debug("Loading annotation into builder: {}", annotation.getId());
        
        // Clear existing data first
        resetAnnotation();
        
        // Populate form fields
        setAnnotationId(annotation.getId());
        setAnnotationName(annotation.getName());
        setComment(annotation.getComment());
        
//        // Set event name from event metadata
//        if (annotation.getEventMetadata() != null &&
//            annotation.getEventMetadata().getDescription() != null) {
//            setEventName(annotation.getEventMetadata().getDescription());
//        }
        
        // Convert protobuf target datasets to DataSetDetails
        dataSets.clear();
        for (String datasetId : annotation.getDataSetIdsList()) {
            // For now, create a simple DataSetDetail with just the ID
            // In a full implementation, we would query for the full dataset details
            DataSetDetail datasetDetail = new DataSetDetail(datasetId, "Dataset " + datasetId, "Loaded from annotation", java.util.List.of());
            dataSets.add(datasetDetail);
        }
        
        // Load tags
        tags.clear();
        tags.addAll(annotation.getTagsList());
        
        // Convert protobuf attributes to "key=value" strings
        attributes.clear();
        for (com.ospreydcs.dp.grpc.v1.common.Attribute attr : annotation.getAttributesList()) {
            attributes.add(attr.getName() + "=" + attr.getValue());
        }
        
        // Load calculations data frames
        calculationsDataFrames.clear();
        if (annotation.getCalculations() != null && 
            !annotation.getCalculations().getCalculationDataFramesList().isEmpty()) {
            
            for (com.ospreydcs.dp.grpc.v1.annotation.Calculations.CalculationsDataFrame frame : 
                 annotation.getCalculations().getCalculationDataFramesList()) {
                
                DataFrameDetails frameDetail = new DataFrameDetails(
                    frame.getName(),
                    frame.getDataTimestamps().getTimestampList().getTimestampsList(),
                    frame.getDataColumnsList()
                );
                calculationsDataFrames.add(frameDetail);
            }
        }
        
        statusMessage.set("Annotation loaded: " + annotation.getName());
        logger.info("Successfully loaded annotation: {} with {} datasets, {} tags, {} attributes, {} calculations", 
                   annotation.getName(), dataSets.size(), tags.size(), attributes.size(), calculationsDataFrames.size());
    }
}