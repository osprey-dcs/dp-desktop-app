package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.query.QueryProvidersResponse;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Table row wrapper for ProviderInfo protobuf objects.
 * Provides JavaFX property binding support for TableView display.
 */
public class ProviderInfoTableRow {
    
    private final QueryProvidersResponse.ProvidersResult.ProviderInfo providerInfo;
    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty description = new SimpleStringProperty();
    private final StringProperty tags = new SimpleStringProperty();
    private final StringProperty attributes = new SimpleStringProperty();
    private final StringProperty pvNames = new SimpleStringProperty();
    private final StringProperty numBuckets = new SimpleStringProperty();
    
    public ProviderInfoTableRow(QueryProvidersResponse.ProvidersResult.ProviderInfo providerInfo) {
        this.providerInfo = providerInfo;
        
        // Set property values from ProviderInfo
        this.id.set(providerInfo.getId());
        this.name.set(providerInfo.getName());
        this.description.set(providerInfo.getDescription());
        
        // Format tags as comma-separated string
        if (!providerInfo.getTagsList().isEmpty()) {
            this.tags.set(String.join(", ", providerInfo.getTagsList()));
        } else {
            this.tags.set("");
        }
        
        // Format attributes as "key1=value1, key2=value2" string
        if (!providerInfo.getAttributesList().isEmpty()) {
            String attributesStr = providerInfo.getAttributesList().stream()
                .map(attribute -> attribute.getName() + "=" + attribute.getValue())
                .collect(Collectors.joining(", "));
            this.attributes.set(attributesStr);
        } else {
            this.attributes.set("");
        }
        
        // Format PV names as comma-separated string
        if (providerInfo.hasProviderMetadata() && !providerInfo.getProviderMetadata().getPvNamesList().isEmpty()) {
            this.pvNames.set(String.join(", ", providerInfo.getProviderMetadata().getPvNamesList()));
        } else {
            this.pvNames.set("");
        }
        
        // Format number of buckets
        if (providerInfo.hasProviderMetadata()) {
            this.numBuckets.set(String.valueOf(providerInfo.getProviderMetadata().getNumBuckets()));
        } else {
            this.numBuckets.set("0");
        }
    }
    
    // Getters for the original ProviderInfo and individual PV names
    public QueryProvidersResponse.ProvidersResult.ProviderInfo getProviderInfo() {
        return providerInfo;
    }
    
    public List<String> getPvNamesList() {
        if (providerInfo.hasProviderMetadata()) {
            return providerInfo.getProviderMetadata().getPvNamesList();
        }
        return List.of();
    }
    
    // JavaFX Property getters for TableView binding
    public StringProperty idProperty() { return id; }
    public String getId() { return id.get(); }
    
    public StringProperty nameProperty() { return name; }
    public String getName() { return name.get(); }
    
    public StringProperty descriptionProperty() { return description; }
    public String getDescription() { return description.get(); }
    
    public StringProperty tagsProperty() { return tags; }
    public String getTags() { return tags.get(); }
    
    public StringProperty attributesProperty() { return attributes; }
    public String getAttributes() { return attributes.get(); }
    
    public StringProperty pvNamesProperty() { return pvNames; }
    public String getPvNames() { return pvNames.get(); }
    
    public StringProperty numBucketsProperty() { return numBuckets; }
    public String getNumBuckets() { return numBuckets.get(); }
    
    @Override
    public String toString() {
        return String.format("Provider[id=%s, name=%s, pvCount=%d]", 
            getId(), getName(), getPvNamesList().size());
    }
}