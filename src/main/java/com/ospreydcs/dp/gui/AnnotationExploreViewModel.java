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
    /**
     * Free-text search over the annotation's name and description.
     *
     * Named for what it actually searches.  It was "name / description / event" through the
     * comment -> description rename, but the modernized Annotation has no event field at all
     * (dp-grpc #132 removed the event metadata), and this value is sent as TextCriterion, a
     * collection-level text-index search whose indexed fields are name and description.  An event
     * term entered here matched nothing while the label promised otherwise.
     */
    private final StringProperty nameDescriptionText = new SimpleStringProperty("");
    private final StringProperty tagValue = new SimpleStringProperty("");
    private final StringProperty attributeKey = new SimpleStringProperty("");
    private final StringProperty attributeValue = new SimpleStringProperty("");
    
    // Search results
    private final ObservableList<AnnotationInfoTableRow> searchResults = FXCollections.observableArrayList();
    
    // UI state properties
    private final BooleanProperty searchInProgress = new SimpleBooleanProperty(false);
    private final StringProperty searchStatusMessage = new SimpleStringProperty("Ready to search for annotations");
    private final StringProperty resultCountMessage = new SimpleStringProperty("0 results");
    /**
     * Status shown beside the results table, as in the other three explore views.  This view had
     * only searchStatusMessage, which is why annotation-explore.fxml declared a resultsStatusLabel
     * that nothing ever bound -- it rendered its static FXML text forever.
     */
    private final StringProperty statusMessage = new SimpleStringProperty("Ready");
    
    /**
     * Whether the most recent search stopped at the query cap.  Read by the searchResults listener
     * that formats the count label, and set before the results are added so the listener sees the
     * value belonging to the batch it is reacting to.  FX thread only.
     */
    private boolean lastResultTruncated = false;

    public AnnotationExploreViewModel() {
        logger.debug("AnnotationExploreViewModel initialized");
        
        // Set up listeners for search results
        searchResults.addListener((javafx.collections.ListChangeListener<AnnotationInfoTableRow>) change -> {
            int count = searchResults.size();
            // "first N of more" rather than a bare count when the query was capped: a count
            // presented as a total when it is not is exactly the bug transparent paging fixes, and
            // this label sits beside the status message that already says so
            if (lastResultTruncated) {
                resultCountMessage.set("first " + count + " results");
            } else if (count == 0) {
                resultCountMessage.set("0 results");
            } else if (count == 1) {
                resultCountMessage.set("1 result");
            } else {
                resultCountMessage.set(count + " results");
            }
            logger.debug("Search results updated: {} annotations (truncated={})", count, lastResultTruncated);
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
                    "relatedDatasetsId='{}', relatedAnnotationsId='{}', nameDescriptionText='{}', " +
                    "tagValue='{}', attributeKey='{}', attributeValue='{}'",
                    annotationId.get(), owner.get(), relatedDatasetsId.get(), relatedAnnotationsId.get(),
                    nameDescriptionText.get(), tagValue.get(), attributeKey.get(), attributeValue.get());
        
        searchInProgress.set(true);
        searchStatusMessage.set("Searching for annotations...");
        // reset before clearing, so the emptied list is not labelled with the previous search's
        // truncation state
        lastResultTruncated = false;
        searchResults.clear();
        
        // Create background task for annotation search
        Task<DpApplication.PagedResult<Annotation>> searchTask =
            new Task<DpApplication.PagedResult<Annotation>>() {
                
            @Override
            protected DpApplication.PagedResult<Annotation> call() throws Exception {
                logger.debug("Background annotation search task started");
                
                // Convert empty strings to null for API call
                String idCriterion = trimmedOrNull(annotationId.get());
                String ownerCriterion = trimmedOrNull(owner.get());
                String dataSetsCriterion = trimmedOrNull(relatedDatasetsId.get());
                String annotationsCriterion = trimmedOrNull(relatedAnnotationsId.get());
                String textCriterion = trimmedOrNull(nameDescriptionText.get());
                String tagsCriterion = trimmedOrNull(tagValue.get());
                String attributeKeyCriterion = trimmedOrNull(attributeKey.get());
                String attributeValueCriterion = trimmedOrNull(attributeValue.get());
                
                // Call DpApplication.queryAnnotations(), which follows nextPageToken internally
                // and reports whether it stopped at the cap.  A failed page throws rather than
                // returning a partial list, so there is no partial-success case to check here.
                DpApplication.PagedResult<Annotation> pagedResult =
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
                
                logger.debug("Annotation search completed successfully - {} annotations found (truncated={})",
                           pagedResult.records.size(), pagedResult.truncated);
                return pagedResult;
            }
        };
        
        // setOnSucceeded / setOnFailed already run on the FX thread, so neither handler wraps its
        // work in Platform.runLater.  Queueing there did not order anything -- it deferred the whole
        // update behind whatever was already on the queue.
        searchTask.setOnSucceeded(e -> {
            DpApplication.PagedResult<Annotation> pagedResult = searchTask.getValue();

            // set before adding, so the searchResults listener formatting the count label sees
            // the truncation state of the batch it is reacting to
            lastResultTruncated = pagedResult.truncated;

            // Convert protobuf objects to table row objects
            for (Annotation annotation : pagedResult.records) {
                searchResults.add(new AnnotationInfoTableRow(annotation));
            }

            // report the count as a total only when the query was not capped, so a truncated
            // result is never presented as a complete one
            searchStatusMessage.set("Search completed - " + pagedResult.describeCount("annotation"));
            statusMessage.set(capitalize(pagedResult.describeCount("annotation")));
            searchInProgress.set(false);
            logger.info("Annotation search completed - {} annotations displayed (truncated={})",
                        pagedResult.records.size(), pagedResult.truncated);
        });

        searchTask.setOnFailed(e -> {
            Throwable exception = searchTask.getException();
            logger.error("Annotation search failed", exception);
            searchStatusMessage.set("Search failed: " + exception.getMessage());
            statusMessage.set("Search failed: " + exception.getMessage());
            searchInProgress.set(false);
        });
        
        // Run the background task
        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }
    
    /**
     * Trims then omits: DpApplication.emptyToNull() deliberately does not trim, but a
     * whitespace-only search field must be omitted from the request rather than sent as a criterion
     * that matches nothing.
     */
    private static String trimmedOrNull(String value) {
        return value == null ? null : DpApplication.emptyToNull(value.trim());
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
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
        nameDescriptionText.set("");
        tagValue.set("");
        attributeKey.set("");
        attributeValue.set("");
        
        // Clear results
        lastResultTruncated = false;
        searchResults.clear();
        searchStatusMessage.set("Search cleared");
        statusMessage.set("Ready");
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
    
    public StringProperty nameDescriptionTextProperty() { return nameDescriptionText; }
    public String getNameDescriptionText() { return nameDescriptionText.get(); }
    public void setNameDescriptionText(String text) { nameDescriptionText.set(text != null ? text : ""); }
    
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
    public StringProperty statusMessageProperty() { return statusMessage; }
    public String getResultCountMessage() { return resultCountMessage.get(); }
    
}