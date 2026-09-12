package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.annotation.Annotation;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Wrapper class for protobuf Annotation objects to support JavaFX TableView display.
 * Provides property binding support and formatted display strings for complex fields.
 */
public class AnnotationInfoTableRow {

    /** Column text for an annotation that has calculations, in place of the frame name list. */
    public static final String CALCULATIONS_PRESENT_LABEL = "View calculations";

    /*
     * Property names used by the TableView column bindings in
     * AnnotationExploreController.setupTableColumns().
     *
     * PropertyValueFactory resolves a property by STRING via reflection at render time, so a
     * renamed property with a stale binding string yields a silently blank column -- no compile
     * error, and ViewLoadSmokeTest does not catch it either, since it never populates a row.  That
     * is exactly how the comment -> description rename (dp-grpc #132) could have gone wrong.
     *
     * Naming them here, beside the properties they refer to, is what makes a rename a compile-time
     * concern: the controller and AnnotationInfoTableRowBindingTest both reference these constants
     * rather than repeating the literals, so the test guards the binding the controller actually
     * uses instead of a copy of it.
     */
    public static final String PROPERTY_ID = "id";
    public static final String PROPERTY_OWNER = "owner";
    public static final String PROPERTY_NAME = "name";
    public static final String PROPERTY_DESCRIPTION = "description";
    public static final String PROPERTY_TAGS = "tags";
    public static final String PROPERTY_ATTRIBUTES = "attributes";
    public static final String PROPERTY_RELATED_DATASETS = "relatedDatasets";
    public static final String PROPERTY_RELATED_ANNOTATIONS = "relatedAnnotations";
    public static final String PROPERTY_CALCULATIONS_DATA_FRAMES = "calculationsDataFrames";

    private final Annotation annotation;
    
    // Properties for TableView binding
    private final StringProperty id;
    private final StringProperty owner;
    private final StringProperty relatedDatasets;
    private final StringProperty name;
    private final StringProperty relatedAnnotations;
    private final StringProperty description;
    private final StringProperty tags;
    private final StringProperty attributes;
    private final StringProperty calculationsDataFrames;

    public AnnotationInfoTableRow(Annotation annotation) {
        this.annotation = annotation;
        
        // Initialize properties from protobuf object
        this.id = new SimpleStringProperty(annotation != null ? annotation.getId() : "");
        this.owner = new SimpleStringProperty(annotation != null ? annotation.getOwnerId() : "");
        this.name = new SimpleStringProperty(annotation != null ? annotation.getName() : "");
        this.description = new SimpleStringProperty(annotation != null ? annotation.getDescription() : "");
        
        // Format complex fields as comma-separated strings
        this.relatedDatasets = new SimpleStringProperty(formatDatasetIds(annotation));
        this.relatedAnnotations = new SimpleStringProperty(formatAnnotationIds(annotation));
        this.tags = new SimpleStringProperty(formatTags(annotation));
        this.attributes = new SimpleStringProperty(formatAttributes(annotation));
        this.calculationsDataFrames = new SimpleStringProperty(formatCalculationsDataFrames(annotation));
    }
    
    // Formatting methods for complex fields
    
    private String formatDatasetIds(Annotation annotation) {
        if (annotation == null || annotation.getDataSetIdsList().isEmpty()) {
            return "";
        }
        return String.join(", ", annotation.getDataSetIdsList());
    }
    
    private String formatAnnotationIds(Annotation annotation) {
        if (annotation == null || annotation.getAnnotationIdsList().isEmpty()) {
            return "";
        }
        return String.join(", ", annotation.getAnnotationIdsList());
    }
    
    private String formatTags(Annotation annotation) {
        if (annotation == null || annotation.getTagsList().isEmpty()) {
            return "";
        }
        return String.join(", ", annotation.getTagsList());
    }
    
    private String formatAttributes(Annotation annotation) {
        if (annotation == null || annotation.getAttributesList().isEmpty()) {
            return "";
        }
        
        return annotation.getAttributesList().stream()
            .map(attr -> attr.getName() + "=" + attr.getValue())
            .collect(Collectors.joining(", "));
    }
    
    /**
     * Formats the Calculations column as a presence indicator rather than a list of frame names.
     *
     * As of dp-grpc #132, queryAnnotations() returns calculationsId without Calculations content,
     * so frame names are not available here and cannot be obtained without a fetch per row -- the
     * N+1 fan-out the API change exists to remove.  calculationsId is enough to say whether an
     * annotation has calculations at all; the names are resolved when the user opens them.  See
     * plan/tickets/42 D1.
     */
    private String formatCalculationsDataFrames(Annotation annotation) {
        if (annotation == null || annotation.getCalculationsId().isEmpty()) {
            return "";
        }
        
        return CALCULATIONS_PRESENT_LABEL;
    }
    
    // Property getters for TableView binding
    
    public StringProperty idProperty() { return id; }
    public String getId() { return id.get(); }
    
    public StringProperty ownerProperty() { return owner; }
    public String getOwner() { return owner.get(); }
    
    public StringProperty relatedDatasetsProperty() { return relatedDatasets; }
    public String getRelatedDatasets() { return relatedDatasets.get(); }
    
    public StringProperty nameProperty() { return name; }
    public String getName() { return name.get(); }
    
    public StringProperty relatedAnnotationsProperty() { return relatedAnnotations; }
    public String getRelatedAnnotations() { return relatedAnnotations.get(); }
    
    public StringProperty descriptionProperty() { return description; }
    public String getDescription() { return description.get(); }
    
    public StringProperty tagsProperty() { return tags; }
    public String getTags() { return tags.get(); }
    
    public StringProperty attributesProperty() { return attributes; }
    public String getAttributes() { return attributes.get(); }
    
    public StringProperty calculationsDataFramesProperty() { return calculationsDataFrames; }
    public String getCalculationsDataFrames() { return calculationsDataFrames.get(); }
    
    // Access to underlying protobuf object and its lists for hyperlink functionality
    
    public Annotation getAnnotation() { 
        return annotation; 
    }
    
    public List<String> getDataSetIdsList() {
        return annotation != null ? annotation.getDataSetIdsList() : List.of();
    }
    
    public List<String> getAnnotationIdsList() {
        return annotation != null ? annotation.getAnnotationIdsList() : List.of();
    }
    
    /**
     * The id of this annotation's Calculations, or "" when it has none.
     *
     * This is what queryAnnotations() returns in place of the Calculations content it used to
     * denormalize.  Pass it to DpApplication.getCalculations() to fetch the frames on demand.
     */
    public String getCalculationsId() {
        return annotation != null ? annotation.getCalculationsId() : "";
    }
    
    /**
     * Whether this annotation has calculations, determined from calculationsId alone so it holds
     * for rows obtained from queryAnnotations(), which does not return calculations content.
     */
    public boolean hasCalculations() {
        return !getCalculationsId().isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("Annotation[id=%s, name=%s, owner=%s]", getId(), getName(), getOwner());
    }
}