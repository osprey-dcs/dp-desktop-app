package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.annotation.Annotation;
import com.ospreydcs.dp.gui.component.AttributesListComponent;
import com.ospreydcs.dp.gui.component.TagsListComponent;
import com.ospreydcs.dp.gui.model.DataSetDetail;
import com.ospreydcs.dp.gui.model.DataFrameDetails;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for the Annotation Builder tab functionality.
 * Manages annotation properties, target datasets, tags, attributes, and UI state.
 */
public class AnnotationBuilderViewModel {

    private static final Logger logger = LogManager.getLogger();

    // Annotation properties
    private final StringProperty annotationId = new SimpleStringProperty("");
    private final StringProperty annotationName = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");
    
    // Target datasets
    private final ObservableList<DataSetDetail> dataSets = FXCollections.observableArrayList();
    
    // Calculations data frames
    private final ObservableList<DataFrameDetails> calculationsDataFrames = FXCollections.observableArrayList();
    
    /*
     * Tags and attributes live in the injected components, not here.
     *
     * This ViewModel deliberately holds NO collections for them.  It used to, and they were write-
     * only: loadFromAnnotation() populated these lists while DataExploreController.onSaveAnnotation()
     * read tagsComponent/attributesComponent, so loading an annotation put its tags somewhere the
     * save never looked.  Editing any other field and saving then wrote them back as absent --
     * silently, since saveAnnotation() is a full-replace upsert.  Holding only the component
     * references is the Critical Integration Pattern the other views follow (see PvMetadataViewModel)
     * and makes that divergence unrepresentable rather than merely fixed.
     */
    private TagsListComponent tagsComponent;
    private AttributesListComponent attributesComponent;
    
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
    
    /**
     * Injects the tags component, which owns the annotation's tags.
     *
     * The component's list is observed here so tag edits still drive button state, which is what
     * the ViewModel's own removed list was doing.
     */
    public void setTagsComponent(TagsListComponent tagsComponent) {
        this.tagsComponent = tagsComponent;
        if (tagsComponent != null) {
            tagsComponent.getTags().addListener(
                    (javafx.collections.ListChangeListener<String>) change -> updateButtonStates());
        }
        updateButtonStates();
        logger.debug("Tags component injected into AnnotationBuilderViewModel");
    }
    
    /**
     * Injects the attributes component, which owns the annotation's attributes.  See
     * setTagsComponent() for why the list is observed.
     */
    public void setAttributesComponent(AttributesListComponent attributesComponent) {
        this.attributesComponent = attributesComponent;
        if (attributesComponent != null) {
            attributesComponent.getAttributes().addListener(
                    (javafx.collections.ListChangeListener<String>) change -> updateButtonStates());
        }
        updateButtonStates();
        logger.debug("Attributes component injected into AnnotationBuilderViewModel");
    }
    
    /**
     * The annotation's tags, read from the component that owns them.  Empty when no component has
     * been injected, which is the pre-injection state exercised by ViewLoadSmokeTest.
     */
    public List<String> getTags() {
        return tagsComponent != null ? new ArrayList<>(tagsComponent.getTags()) : new ArrayList<>();
    }
    
    /** The annotation's attributes as "key=value" strings; see getTags(). */
    public List<String> getAttributes() {
        return attributesComponent != null
                ? new ArrayList<>(attributesComponent.getAttributes()) : new ArrayList<>();
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
        description.addListener((obs, oldVal, newVal) -> updateButtonStates());
        
        // tag and attribute changes are observed in setTagsComponent() / setAttributesComponent(),
        // since those lists belong to the components rather than to this ViewModel
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
                            (description.get() != null && !description.get().trim().isEmpty()) ||
                            !getTags().isEmpty() ||
                            !getAttributes().isEmpty() ||
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
        description.set("");
        clearDataSets();
        // clear the components, not a local list: reset previously left the tag and attribute
        // controls populated, so a reset form still carried the prior annotation's metadata into
        // the next save
        if (tagsComponent != null) {
            tagsComponent.clearTags();
        }
        if (attributesComponent != null) {
            attributesComponent.clearAttributes();
        }
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
    
    public StringProperty descriptionProperty() { return description; }
    public String getDescription() { return description.get(); }
    public void setDescription(String description) { this.description.set(description != null ? description : ""); }
    
    public ObservableList<DataSetDetail> getDataSets() { return dataSets; }
    
    public ObservableList<DataFrameDetails> getCalculationsDataFrames() { return calculationsDataFrames; }
    
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
    public void loadFromAnnotation(Annotation annotation) {
        logger.debug("Loading annotation into builder: {}", annotation.getId());
        
        // Clear existing data first
        resetAnnotation();
        
        // Populate form fields
        setAnnotationId(annotation.getId());
        setAnnotationName(annotation.getName());
        setDescription(annotation.getDescription());
        
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
        
        // Load tags and attributes into the components that own them.
        //
        // These MUST go to the components rather than to ViewModel lists.  onSaveAnnotation() reads
        // tagsComponent/attributesComponent, so loading into a ViewModel list would put the loaded
        // metadata where the save never looks -- and since saveAnnotation() is a full-replace
        // upsert, editing any unrelated field and saving would then drop the annotation's tags and
        // attributes silently.  That is the same failure mode as the calculations loss below, and
        // is why this ViewModel holds no collections for them.
        if (tagsComponent != null) {
            tagsComponent.clearTags();
            for (String tag : annotation.getTagsList()) {
                tagsComponent.addTag(tag);
            }
        } else {
            logger.warn("No tags component injected - annotation {} tags not loaded", annotation.getId());
        }
        
        if (attributesComponent != null) {
            attributesComponent.clearAttributes();
            for (com.ospreydcs.dp.grpc.v1.common.Attribute attr : annotation.getAttributesList()) {
                attributesComponent.addAttribute(attr.getName(), attr.getValue());
            }
        } else {
            logger.warn("No attributes component injected - annotation {} attributes not loaded",
                        annotation.getId());
        }
        
        // Load calculations data frames.
        //
        // This reads embedded Calculations content, which is only populated by getAnnotation() --
        // queryAnnotations() returns calculationsId alone as of dp-grpc #132.  The caller
        // (DataExploreController.loadAnnotationIntoBuilder) must therefore keep loading through
        // getAnnotation(): loading through a query result would leave this list empty, and since
        // saveAnnotation() is a full-replace upsert, the next save would destroy the stored
        // calculations without an error.  See plan/tickets/42 P2.3.
        calculationsDataFrames.clear();
        if (annotation.getCalculations() != null && 
            !annotation.getCalculations().getCalculationDataFramesList().isEmpty()) {
            
            for (com.ospreydcs.dp.grpc.v1.annotation.Calculations.CalculationsDataFrame frame : 
                 annotation.getCalculations().getCalculationDataFramesList()) {
                
                calculationsDataFrames.add(DataFrameDetails.fromCalculationsDataFrame(frame));
            }
        }
        
        statusMessage.set("Annotation loaded: " + annotation.getName());
        // tag and attribute counts are read back from the components, so the log reports what was
        // actually loaded into the form rather than what the annotation carried
        logger.info("Successfully loaded annotation: {} with {} datasets, {} tags, {} attributes, {} calculations",
                   annotation.getName(), dataSets.size(), getTags().size(), getAttributes().size(),
                   calculationsDataFrames.size());
    }
}