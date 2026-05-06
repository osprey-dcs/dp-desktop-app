package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.grpc.v1.query.QueryPvStatsResponse;
import javafx.beans.property.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Table row model for PV metadata information in the pv-explore view.
 * Wraps protobuf PvInfo with JavaFX properties for table binding.
 */
public class PvInfoTableRow {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final StringProperty pvName = new SimpleStringProperty();
    private final StringProperty providerName = new SimpleStringProperty();
    private final StringProperty dataType = new SimpleStringProperty();
    private final StringProperty timestampsType = new SimpleStringProperty();
    private final StringProperty samplePeriod = new SimpleStringProperty();
    private final StringProperty firstDataTimestamp = new SimpleStringProperty();
    private final StringProperty lastDataTimestamp = new SimpleStringProperty();
    private final IntegerProperty numBuckets = new SimpleIntegerProperty();
    
    private final QueryPvStatsResponse.StatsResult.PvStats pvInfo;

    public PvInfoTableRow(QueryPvStatsResponse.StatsResult.PvStats pvInfo) {
        this.pvInfo = pvInfo;
        this.pvName.set(pvInfo.getPvName());
        this.providerName.set(pvInfo.getLastProviderName());
        this.dataType.set(pvInfo.getLastBucketDataType());
        this.timestampsType.set(pvInfo.getLastBucketDataTimestampsType());
        
        // Format sample period (convert from nanoseconds to more readable format)
        long samplePeriodNanos = pvInfo.getLastBucketSamplePeriod();
        if (samplePeriodNanos == 0) {
            this.samplePeriod.set("Irregular");
        } else if (samplePeriodNanos < 1_000_000) {
            this.samplePeriod.set(samplePeriodNanos + " ns");
        } else if (samplePeriodNanos < 1_000_000_000) {
            this.samplePeriod.set((samplePeriodNanos / 1_000_000) + " ms");
        } else {
            this.samplePeriod.set((samplePeriodNanos / 1_000_000_000) + " s");
        }
        
        // Format timestamps
        if (pvInfo.hasFirstDataTimestamp()) {
            Instant firstInstant = Instant.ofEpochSecond(
                pvInfo.getFirstDataTimestamp().getEpochSeconds(),
                pvInfo.getFirstDataTimestamp().getNanoseconds()
            );
            LocalDateTime firstDateTime = LocalDateTime.ofInstant(firstInstant, ZoneId.systemDefault());
            this.firstDataTimestamp.set(firstDateTime.format(TIMESTAMP_FORMATTER));
        } else {
            this.firstDataTimestamp.set("N/A");
        }
        
        if (pvInfo.hasLastDataTimestamp()) {
            Instant lastInstant = Instant.ofEpochSecond(
                pvInfo.getLastDataTimestamp().getEpochSeconds(),
                pvInfo.getLastDataTimestamp().getNanoseconds()
            );
            LocalDateTime lastDateTime = LocalDateTime.ofInstant(lastInstant, ZoneId.systemDefault());
            this.lastDataTimestamp.set(lastDateTime.format(TIMESTAMP_FORMATTER));
        } else {
            this.lastDataTimestamp.set("N/A");
        }
        
        this.numBuckets.set(pvInfo.getNumBuckets());
    }

    // Property getters for table binding
    public BooleanProperty selectedProperty() { return selected; }
    public StringProperty pvNameProperty() { return pvName; }
    public StringProperty providerNameProperty() { return providerName; }
    public StringProperty dataTypeProperty() { return dataType; }
    public StringProperty timestampsTypeProperty() { return timestampsType; }
    public StringProperty samplePeriodProperty() { return samplePeriod; }
    public StringProperty firstDataTimestampProperty() { return firstDataTimestamp; }
    public StringProperty lastDataTimestampProperty() { return lastDataTimestamp; }
    public IntegerProperty numBucketsProperty() { return numBuckets; }

    // Value getters
    public boolean isSelected() { return selected.get(); }
    public void setSelected(boolean selected) { this.selected.set(selected); }
    public String getPvName() { return pvName.get(); }
    public String getProviderName() { return providerName.get(); }
    public String getDataType() { return dataType.get(); }
    public String getTimestampsType() { return timestampsType.get(); }
    public String getSamplePeriod() { return samplePeriod.get(); }
    public String getFirstDataTimestamp() { return firstDataTimestamp.get(); }
    public String getLastDataTimestamp() { return lastDataTimestamp.get(); }
    public int getNumBuckets() { return numBuckets.get(); }
    
    // Access to original PvInfo for additional fields
    public String getLastProviderId() { return pvInfo.getLastProviderId(); }
    public QueryPvStatsResponse.StatsResult.PvStats getPvInfo() { return pvInfo; }

    @Override
    public String toString() {
        return String.format("%s (%s, %d buckets)", getPvName(), getDataType(), getNumBuckets());
    }
}