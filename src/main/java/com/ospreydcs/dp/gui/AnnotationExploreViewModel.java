package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.annotation.Annotation;
import com.ospreydcs.dp.gui.model.AnnotationInfoTableRow;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ViewModel for the Annotation Explore functionality.
 * Manages annotation search criteria, executes queries, and maintains search results.
 */
public class AnnotationExploreViewModel {

    private static final Logger logger = LogManager.getLogger();
    
    // Dependencies
    private DpApplication dpApplication;
    
    // Search criteria properties
    private final StringProperty annotationId = new SimpleStringProperty("");
    private final StringProperty owner = new SimpleStringProperty("");
    private final StringProperty relatedDatasetsId = new SimpleStringProperty("");
    private final StringProperty relatedAnnotationsId = new SimpleStringProperty("");
    private final StringProperty nameCommentEventText = new SimpleStringProperty("");
    private final StringProperty tagValue = new SimpleStringProperty("");
    private final StringProperty attributeKey = new SimpleStringProperty("");
    private final StringProperty attributeValue = new SimpleStringProperty("");
    
    // Search results
    private final ObservableList<AnnotationInfoTableRow> searchResults = FXCollections.observableArrayList();
    
    // UI state properties
    private final BooleanProperty searchInProgress = new SimpleBooleanProperty(false);
    private final StringProperty searchStatusMessage = new SimpleStringProperty("Ready to search for annotations");
    private final StringProperty resultCountMessage = new SimpleStringProperty("0 results");
    private final BooleanProperty hasResults = new SimpleBooleanProperty(false);

    public AnnotationExploreViewModel() {
        logger.debug("AnnotationExploreViewModel initialized");
        
        // Set up listeners for search results
        searchResults.addListener((javafx.collections.ListChangeListener<AnnotationInfoTableRow>) change -> {
            int count = searchResults.size();
            hasResults.set(count > 0);
            if (count == 0) {
                resultCountMessage.set("0 results");
            } else if (count == 1) {
                resultCountMessage.set("1 result");
            } else {
                resultCountMessage.set(count + " results");
            }
            logger.debug("Search results updated: {} annotations", count);
        });
    }
    
    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        logger.debug("DpApplication injected into AnnotationExploreViewModel");
    }
    
    /**
     * Execute annotation search with current search criteria.
     */
    public void executeSearch() {
        if (dpApplication == null) {
            logger.warn("Cannot execute search - DpApplication not set");
            searchStatusMessage.set("Error: Application not properly initialized");
            return;
        }
        
        logger.debug("Executing annotation search with criteria: annotationId='{}', owner='{}', " +
                    "relatedDatasetsId='{}', relatedAnnotationsId='{}', nameCommentEventText='{}', " +
                    "tagValue='{}', attributeKey='{}', attributeValue='{}'",
                    annotationId.get(), owner.get(), relatedDatasetsId.get(), relatedAnnotationsId.get(),
                    nameCommentEventText.get(), tagValue.get(), attributeKey.get(), attributeValue.get());
        
        searchInProgress.set(true);
        searchStatusMessage.set("Searching for annotations...");
        searchResults.clear();
        
        // Create background task for annotation search
        Task<java.util.List<Annotation>> searchTask = 
            new Task<java.util.List<Annotation>>() {
                
            @Override
            protected java.util.List<Annotation> call() throws Exception {
                logger.debug("Background annotation search task started");
                
                // Convert empty strings to null for API call
                String idCriterion = nullIfEmpty(annotationId.get());
                String ownerCriterion = nullIfEmpty(owner.get());
                String dataSetsCriterion = nullIfEmpty(relatedDatasetsId.get());
                String annotationsCriterion = nullIfEmpty(relatedAnnotationsId.get());
                String textCriterion = nullIfEmpty(nameCommentEventText.get());
                String tagsCriterion = nullIfEmpty(tagValue.get());
                String attributeKeyCriterion = nullIfEmpty(attributeKey.get());
                String attributeValueCriterion = nullIfEmpty(attributeValue.get());
                
                // Call DpApplication.queryAnnotations() with search criteria
                com.ospreydcs.dp.client.result.QueryAnnotationsApiResult apiResult = 
                    dpApplication.queryAnnotations(
                        idCriterion, 
                        ownerCriterion,
                        dataSetsCriterion,
                        annotationsCriterion, 
                        textCriterion, 
                        tagsCriterion, 
                        attributeKeyCriterion, 
                        attributeValueCriterion
                    );
                
                if (apiResult == null) {
                    throw new RuntimeException("Annotation query failed - null response from service");
                }
                
                if (apiResult.resultStatus.isError) {
                    throw new RuntimeException("Annotation query failed: " + apiResult.resultStatus.msg);
                }
                
                if (apiResult.annotations == null) {
                    logger.warn("Annotation query returned null annotations list");
                    return java.util.List.of();
                }
                
                logger.debug("Annotation search completed successfully - {} annotations found", 
                           apiResult.annotations.size());
                return apiResult.annotations;
            }
        };
        
        searchTask.setOnSucceeded(e -> {
            java.util.List<Annotation> annotations = searchTask.getValue();
            
            javafx.application.Platform.runLater(() -> {
                // Convert protobuf objects to table row objects
                for (Annotation annotation : annotations) {
                    AnnotationInfoTableRow tableRow = new AnnotationInfoTableRow(annotation);
                    searchResults.add(tableRow);
                }
                
                searchInProgress.set(false);
                searchStatusMessage.set("Search completed successfully");
                logger.info("Annotation search completed - {} annotations displayed", annotations.size());
            });
        });
        
        searchTask.setOnFailed(e -> {
            Throwable exception = searchTask.getException();
            String errorMessage = "Search failed: " + exception.getMessage();
            logger.error("Annotation search failed", exception);
            
            javafx.application.Platform.runLater(() -> {
                searchInProgress.set(false);
                searchStatusMessage.set(errorMessage);
            });
        });
        
        // Run the background task
        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }
    
    /**
     * Clear all search criteria and results.
     */
    public void clearSearch() {
        logger.debug("Clearing annotation search criteria and results");
        
        // Clear search criteria
        annotationId.set("");
        owner.set("");
        relatedDatasetsId.set("");
        relatedAnnotationsId.set("");
        nameCommentEventText.set("");
        tagValue.set("");
        attributeKey.set("");
        attributeValue.set("");
        
        // Clear results
        searchResults.clear();
        searchStatusMessage.set("Search cleared");
    }
    
    private String nullIfEmpty(String value) {
        return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
    }
    
    // Property getters for data binding
    
    public StringProperty annotationIdProperty() { return annotationId; }
    public String getAnnotationId() { return annotationId.get(); }
    public void setAnnotationId(String id) { annotationId.set(id != null ? id : ""); }
    
    public StringProperty ownerProperty() { return owner; }
    public String getOwner() { return owner.get(); }
    public void setOwner(String owner) { this.owner.set(owner != null ? owner : ""); }
    
    public StringProperty relatedDatasetsIdProperty() { return relatedDatasetsId; }
    public String getRelatedDatasetsId() { return relatedDatasetsId.get(); }
    public void setRelatedDatasetsId(String id) { relatedDatasetsId.set(id != null ? id : ""); }
    
    public StringProperty relatedAnnotationsIdProperty() { return relatedAnnotationsId; }
    public String getRelatedAnnotationsId() { return relatedAnnotationsId.get(); }
    public void setRelatedAnnotationsId(String id) { relatedAnnotationsId.set(id != null ? id : ""); }
    
    public StringProperty nameCommentEventTextProperty() { return nameCommentEventText; }
    public String getNameCommentEventText() { return nameCommentEventText.get(); }
    public void setNameCommentEventText(String text) { nameCommentEventText.set(text != null ? text : ""); }
    
    public StringProperty tagValueProperty() { return tagValue; }
    public String getTagValue() { return tagValue.get(); }
    public void setTagValue(String value) { tagValue.set(value != null ? value : ""); }
    
    public StringProperty attributeKeyProperty() { return attributeKey; }
    public String getAttributeKey() { return attributeKey.get(); }
    public void setAttributeKey(String key) { attributeKey.set(key != null ? key : ""); }
    
    public StringProperty attributeValueProperty() { return attributeValue; }
    public String getAttributeValue() { return attributeValue.get(); }
    public void setAttributeValue(String value) { attributeValue.set(value != null ? value : ""); }
    
    public ObservableList<AnnotationInfoTableRow> getSearchResults() { return searchResults; }
    
    public BooleanProperty searchInProgressProperty() { return searchInProgress; }
    public boolean isSearchInProgress() { return searchInProgress.get(); }
    
    public StringProperty searchStatusMessageProperty() { return searchStatusMessage; }
    public String getSearchStatusMessage() { return searchStatusMessage.get(); }
    
    public StringProperty resultCountMessageProperty() { return resultCountMessage; }
    public String getResultCountMessage() { return resultCountMessage.get(); }
    
    public BooleanProperty hasResultsProperty() { return hasResults; }
    public boolean hasResults() { return hasResults.get(); }
}