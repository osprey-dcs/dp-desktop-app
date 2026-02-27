package com.ospreydcs.dp.gui.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Wrapper class for protobuf Annotation objects to support JavaFX TableView display.
 * Provides property binding support and formatted display strings for complex fields.
 */
public class AnnotationInfoTableRow {

    private final com.ospreydcs.dp.grpc.v1.annotation.QueryAnnotationsResponse.AnnotationsResult.Annotation annotation;
    
    // Properties for TableView binding
    private final StringProperty id;
    private final StringProperty owner;
    private final StringProperty relatedDatasets;
    private final StringProperty name;
    private final StringProperty relatedAnnotations;
    private final StringProperty comment;
    private final StringProperty tags;
    private final StringProperty attributes;
    private final StringProperty event;
    private final StringProperty calculationsDataFrames;

    public AnnotationInfoTableRow(com.ospreydcs.dp.grpc.v1.annotation.QueryAnnotationsResponse.AnnotationsResult.Annotation annotation) {
        this.annotation = annotation;
        
        // Initialize properties from protobuf object
        this.id = new SimpleStringProperty(annotation != null ? annotation.getId() : "");
        this.owner = new SimpleStringProperty(annotation != null ? annotation.getOwnerId() : "");
        this.name = new SimpleStringProperty(annotation != null ? annotation.getName() : "");
        this.comment = new SimpleStringProperty(annotation != null ? annotation.getComment() : "");
        
        // Format complex fields as comma-separated strings
        this.relatedDatasets = new SimpleStringProperty(formatDatasetIds(annotation));
        this.relatedAnnotations = new SimpleStringProperty(formatAnnotationIds(annotation));
        this.tags = new SimpleStringProperty(formatTags(annotation));
        this.attributes = new SimpleStringProperty(formatAttributes(annotation));
        this.event = new SimpleStringProperty(formatEvent(annotation));
        this.calculationsDataFrames = new SimpleStringProperty(formatCalculationsDataFrames(annotation));
    }
    
    // Formatting methods for complex fields
    
    private String formatDatasetIds(com.ospreydcs.dp.grpc.v1.annotation.QueryAnnotationsResponse.AnnotationsResult.Annotation annotation) {
        if (annotation == null || annotation.getDataSetIdsList().isEmpty()) {
            return "";
        }
        return String.join(", ", annotation.getDataSetIdsList());
    }
    
    private String formatAnnotationIds(com.ospreydcs.dp.grpc.v1.annotation.QueryAnnotationsResponse.AnnotationsResult.Annotation annotation) {
        if (annotation == null || annotation.getAnnotationIdsList().isEmpty()) {
            return "";
        }
        return String.join(", ", annotation.getAnnotationIdsList());
    }
    
    private String formatTags(com.ospreydcs.dp.grpc.v1.annotation.QueryAnnotationsResponse.AnnotationsResult.Annotation annotation) {
        if (annotation == null || annotation.getTagsList().isEmpty()) {
            return "";
        }
        return String.join(", ", annotation.getTagsList());
    }
    
    private String formatAttributes(com.ospreydcs.dp.grpc.v1.annotation.QueryAnnotationsResponse.AnnotationsResult.Annotation annotation) {
        if (annotation == null || annotation.getAttributesList().isEmpty()) {
            return "";
        }
        
        return annotation.getAttributesList().stream()
            .map(attr -> attr.getName() + "=" + attr.getValue())
            .collect(Collectors.joining(", "));
    }
    
    private String formatEvent(com.ospreydcs.dp.grpc.v1.annotation.QueryAnnotationsResponse.AnnotationsResult.Annotation annotation) {
//        if (annotation == null ||
//            annotation.getEventMetadata() == null ||
//            annotation.getEventMetadata().getDescription() == null) {
//            return "";
//        }
//        return annotation.getEventMetadata().getDescription();
        return "";
    }
    
    private String formatCalculationsDataFrames(com.ospreydcs.dp.grpc.v1.annotation.QueryAnnotationsResponse.AnnotationsResult.Annotation annotation) {
        if (annotation == null || 
            annotation.getCalculations() == null ||
            annotation.getCalculations().getCalculationDataFramesList().isEmpty()) {
            return "";
        }
        
        return annotation.getCalculations().getCalculationDataFramesList().stream()
            .map(frame -> frame.getName())
            .collect(Collectors.joining(", "));
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
    
    public StringProperty commentProperty() { return comment; }
    public String getComment() { return comment.get(); }
    
    public StringProperty tagsProperty() { return tags; }
    public String getTags() { return tags.get(); }
    
    public StringProperty attributesProperty() { return attributes; }
    public String getAttributes() { return attributes.get(); }
    
    public StringProperty eventProperty() { return event; }
    public String getEvent() { return event.get(); }
    
    public StringProperty calculationsDataFramesProperty() { return calculationsDataFrames; }
    public String getCalculationsDataFrames() { return calculationsDataFrames.get(); }
    
    // Access to underlying protobuf object and its lists for hyperlink functionality
    
    public com.ospreydcs.dp.grpc.v1.annotation.QueryAnnotationsResponse.AnnotationsResult.Annotation getAnnotation() { 
        return annotation; 
    }
    
    public List<String> getDataSetIdsList() {
        return annotation != null ? annotation.getDataSetIdsList() : List.of();
    }
    
    public List<String> getAnnotationIdsList() {
        return annotation != null ? annotation.getAnnotationIdsList() : List.of();
    }
    
    public List<String> getCalculationsDataFrameNames() {
        if (annotation == null || 
            annotation.getCalculations() == null ||
            annotation.getCalculations().getCalculationDataFramesList().isEmpty()) {
            return List.of();
        }
        
        return annotation.getCalculations().getCalculationDataFramesList().stream()
            .map(frame -> frame.getName())
            .collect(Collectors.toList());
    }
    
    /**
     * Gets a specific calculation data frame by name and converts it to DataFrameDetails.
     * Returns null if the frame is not found.
     */
    public DataFrameDetails getCalculationDataFrameByName(String frameName) {
        if (annotation == null || 
            annotation.getCalculations() == null ||
            annotation.getCalculations().getCalculationDataFramesList().isEmpty()) {
            return null;
        }
        
        for (com.ospreydcs.dp.grpc.v1.annotation.Calculations.CalculationsDataFrame frame : 
             annotation.getCalculations().getCalculationDataFramesList()) {
            if (frameName.equals(frame.getName())) {
                // Convert to DataFrameDetails
                return new DataFrameDetails(
                    frame.getName(),
                    frame.getDataTimestamps().getTimestampList().getTimestampsList(),
                    frame.getDataColumnsList()
                );
            }
        }
        
        return null;
    }
    
    @Override
    public String toString() {
        return String.format("Annotation[id=%s, name=%s, owner=%s]", getId(), getName(), getOwner());
    }
}