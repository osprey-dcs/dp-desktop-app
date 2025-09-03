package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.result.QueryProvidersApiResult;
import com.ospreydcs.dp.grpc.v1.query.QueryProvidersResponse;
import com.ospreydcs.dp.gui.component.QueryPvsComponent;
import com.ospreydcs.dp.gui.model.ProviderInfoTableRow;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class ProviderExploreViewModel {

    private static final Logger logger = LogManager.getLogger();

    // Query form properties
    private final StringProperty providerId = new SimpleStringProperty("");
    private final StringProperty nameDescription = new SimpleStringProperty("");
    private final StringProperty tagValue = new SimpleStringProperty("");
    private final StringProperty attributeKey = new SimpleStringProperty("");
    private final StringProperty attributeValue = new SimpleStringProperty("");
    
    // Results properties
    private final ObservableList<ProviderInfoTableRow> providerResults = FXCollections.observableArrayList();
    private final IntegerProperty resultCount = new SimpleIntegerProperty(0);
    private final BooleanProperty isSearching = new SimpleBooleanProperty(false);
    
    // Status properties
    private final StringProperty statusMessage = new SimpleStringProperty("Ready to search for providers");
    private final StringProperty searchStatusMessage = new SimpleStringProperty("");
    
    // Dependencies
    private DpApplication dpApplication;
    private MainController mainController;
    private QueryPvsComponent queryPvsComponent;

    public ProviderExploreViewModel() {
        logger.debug("ProviderExploreViewModel initialized");
    }
    
    // Dependency injection methods
    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        logger.debug("DpApplication injected into ProviderExploreViewModel");
    }
    
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        logger.debug("MainController injected into ProviderExploreViewModel");
    }
    
    public void setQueryPvsComponent(QueryPvsComponent queryPvsComponent) {
        this.queryPvsComponent = queryPvsComponent;
        logger.debug("QueryPvsComponent injected into ProviderExploreViewModel");
    }

    // Property getters for form fields
    public StringProperty providerIdProperty() { return providerId; }
    public StringProperty nameDescriptionProperty() { return nameDescription; }
    public StringProperty tagValueProperty() { return tagValue; }
    public StringProperty attributeKeyProperty() { return attributeKey; }
    public StringProperty attributeValueProperty() { return attributeValue; }
    
    // Property getters for results
    public ObservableList<ProviderInfoTableRow> getProviderResults() { return providerResults; }
    public IntegerProperty resultCountProperty() { return resultCount; }
    public BooleanProperty isSearchingProperty() { return isSearching; }
    
    // Property getters for status
    public StringProperty statusMessageProperty() { return statusMessage; }
    public StringProperty searchStatusMessageProperty() { return searchStatusMessage; }

    /**
     * Execute provider search with current form parameters
     */
    public void executeSearch() {
        if (dpApplication == null) {
            searchStatusMessage.set("DpApplication not initialized");
            return;
        }

        isSearching.set(true);
        searchStatusMessage.set("Searching providers...");
        providerResults.clear();
        resultCount.set(0);

        // Create background task for search
        Task<Void> searchTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                executeProviderSearch();
                return null;
            }
        };

        searchTask.setOnSucceeded(e -> {
            isSearching.set(false);
            searchStatusMessage.set("Search completed");
            logger.info("Provider search completed with {} results", resultCount.get());
        });

        searchTask.setOnFailed(e -> {
            logger.error("Provider search failed", searchTask.getException());
            searchStatusMessage.set("Search failed: " + searchTask.getException().getMessage());
            isSearching.set(false);
        });

        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }

    private void executeProviderSearch() throws Exception {
        // Get form values (convert empty strings to null for API)
        String providerIdParam = providerId.get().trim().isEmpty() ? null : providerId.get().trim();
        String nameDescParam = nameDescription.get().trim().isEmpty() ? null : nameDescription.get().trim();
        String tagValueParam = tagValue.get().trim().isEmpty() ? null : tagValue.get().trim();
        String attributeKeyParam = attributeKey.get().trim().isEmpty() ? null : attributeKey.get().trim();
        String attributeValueParam = attributeValue.get().trim().isEmpty() ? null : attributeValue.get().trim();
        
        logger.debug("Searching providers with parameters: providerId={}, nameDescription={}, tagValue={}, attributeKey={}, attributeValue={}", 
            providerIdParam, nameDescParam, tagValueParam, attributeKeyParam, attributeValueParam);

        QueryProvidersApiResult apiResult = dpApplication.queryProviders(
            providerIdParam, nameDescParam, tagValueParam, attributeKeyParam, attributeValueParam);
        
        if (apiResult == null) {
            throw new RuntimeException("Provider search failed - null response from service");
        }
        
        if (apiResult.resultStatus.isError) {
            throw new RuntimeException("Provider search failed: " + apiResult.resultStatus.msg);
        }
        
        if (apiResult.providerInfos != null) {
            processSearchResults(apiResult.providerInfos);
        }
    }

    private void processSearchResults(List<QueryProvidersResponse.ProvidersResult.ProviderInfo> providers) {
        // Create table rows from provider info on JavaFX thread
        javafx.application.Platform.runLater(() -> {
            providerResults.clear();
            
            for (QueryProvidersResponse.ProvidersResult.ProviderInfo providerInfo : providers) {
                ProviderInfoTableRow tableRow = new ProviderInfoTableRow(providerInfo);
                providerResults.add(tableRow);
            }
            
            resultCount.set(providerResults.size());
            statusMessage.set(String.format("Found %d provider(s)", providerResults.size()));
            
            logger.debug("Processed {} provider search results", providerResults.size());
        });
    }

    /**
     * Add PV name to the Query PVs component and global state
     */
    public void addPvNameToQuery(String pvName) {
        if (queryPvsComponent != null) {
            queryPvsComponent.addPvName(pvName);
            logger.debug("Added PV '{}' to query from provider results", pvName);
        }
    }

    public void cancel() {
        logger.info("Provider search cancelled by user");
        statusMessage.set("Operation cancelled");
    }

    public void updateStatus(String message) {
        statusMessage.set(message);
        logger.debug("Status updated: {}", message);
    }
}