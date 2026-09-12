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
    private final StringProperty resultCountMessage = new SimpleStringProperty("0 provider(s)");
    private final BooleanProperty searchInProgress = new SimpleBooleanProperty(false);
    
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
    public StringProperty resultCountMessageProperty() { return resultCountMessage; }
    public BooleanProperty searchInProgressProperty() { return searchInProgress; }
    
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

        searchInProgress.set(true);
        searchStatusMessage.set("Searching providers...");
        providerResults.clear();
        resultCountMessage.set("0 provider(s)");

        // The task RETURNS its results rather than publishing them from call(): setOnSucceeded
        // already runs on the FX thread, so the handler can touch providerResults directly.  The
        // earlier version mutated the list from inside call() via Platform.runLater and then logged
        // the count in setOnSucceeded -- which ran BEFORE that queued block, so it always logged the
        // pre-search count.
        Task<List<QueryProvidersResponse.ProvidersResult.ProviderInfo>> searchTask =
                new Task<List<QueryProvidersResponse.ProvidersResult.ProviderInfo>>() {
            @Override
            protected List<QueryProvidersResponse.ProvidersResult.ProviderInfo> call() throws Exception {
                return executeProviderSearch();
            }
        };

        searchTask.setOnSucceeded(e -> {
            processSearchResults(searchTask.getValue());
            searchInProgress.set(false);
        });

        searchTask.setOnFailed(e -> {
            logger.error("Provider search failed", searchTask.getException());
            searchStatusMessage.set("Search failed: " + searchTask.getException().getMessage());
            searchInProgress.set(false);
        });

        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }

    private List<QueryProvidersResponse.ProvidersResult.ProviderInfo> executeProviderSearch()
            throws Exception {
        // Get form values (convert empty strings to null for API)
        // DpApplication.emptyToNull() does NOT trim, so trim first: a whitespace-only field must be
        // omitted from the request, not sent as a criterion matching nothing.
        String providerIdParam = DpApplication.emptyToNull(providerId.get().trim());
        String nameDescParam = DpApplication.emptyToNull(nameDescription.get().trim());
        String tagValueParam = DpApplication.emptyToNull(tagValue.get().trim());
        String attributeKeyParam = DpApplication.emptyToNull(attributeKey.get().trim());
        String attributeValueParam = DpApplication.emptyToNull(attributeValue.get().trim());
        
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
        
        // queryProviders() is unbounded and unpaged -- QueryProvidersRequest has no limit or
        // pageToken fields (dp-service #265), so unlike the dataset and annotation searches there is
        // no truncation to report here.
        return apiResult.providerInfos != null ? apiResult.providerInfos : List.of();
    }

    /**
     * Publishes the search results.  Called from setOnSucceeded, which already runs on the FX
     * thread, so no Platform.runLater is needed or wanted -- queueing here would let the caller's
     * searchInProgress reset run before the results appear.
     */
    private void processSearchResults(List<QueryProvidersResponse.ProvidersResult.ProviderInfo> providers) {
        providerResults.clear();

        for (QueryProvidersResponse.ProvidersResult.ProviderInfo providerInfo : providers) {
            providerResults.add(new ProviderInfoTableRow(providerInfo));
        }

        resultCountMessage.set(providerResults.size() + " provider(s)");
        searchStatusMessage.set("Search completed");
        statusMessage.set(String.format("Found %d provider(s)", providerResults.size()));

        logger.info("Provider search completed with {} results", providerResults.size());
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

    public void updateStatus(String message) {
        statusMessage.set(message);
        logger.debug("Status updated: {}", message);
    }
}