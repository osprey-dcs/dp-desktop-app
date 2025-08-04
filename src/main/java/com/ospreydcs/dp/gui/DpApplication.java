package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.ApiClient;
import com.ospreydcs.dp.client.IngestionClient;
import com.ospreydcs.dp.grpc.v1.ingestion.IngestDataResponse;
import com.ospreydcs.dp.grpc.v1.ingestion.RegisterProviderResponse;
import com.ospreydcs.dp.gui.model.PvDetail;
import com.ospreydcs.dp.service.common.model.ResultStatus;
import com.ospreydcs.dp.service.inprocess.InprocessServiceEcosystem;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class DpApplication {

    // instance variables
    private InprocessServiceEcosystem inprocessServiceEcosystem = null;
    private ApiClient api = null;
    
    // state variables for cross-view usage
    private String providerId = null;
    private String providerName = null;
    private Instant dataBeginTime = null;
    private Instant dataEndTime = null;
    private List<PvDetail> pvDetails = null;

    public boolean init() {

        // create InprocessServiceEcosystem with default local grpc targets
        inprocessServiceEcosystem = new InprocessServiceEcosystem();
        if (!inprocessServiceEcosystem.init()) {
            return false;
        }

        // initialize ApiClient with grpc targets from default inprocess service ecosystem
        api = new ApiClient(inprocessServiceEcosystem.ingestionService.getIngestionChannel());
        if (!api.init()) {
            return false;
        }

        return true;
    }

    public boolean fini() {
        api.fini();
        inprocessServiceEcosystem.fini();
        return true;
    }

    public ResultStatus registerProvider(
            String name, String description,
            List<String> tags,
            Map<String, String> attributes
    ) {
        // Create params object for provider registration
        final IngestionClient.RegisterProviderRequestParams params = 
            new IngestionClient.RegisterProviderRequestParams(name, description, tags, attributes);

        // Call registerProvider() API method
        final RegisterProviderResponse response = api.ingestionClient.sendRegisterProvider(params);

        // Handle null response (indicates error in IngestionClient)
        if (response == null) {
            return new ResultStatus(true, "Failed to register provider - null response from service");
        }

        // Check if response contains an exceptional result (error)
        if (response.hasExceptionalResult()) {
            return new ResultStatus(true, "Provider registration failed: " + 
                response.getExceptionalResult().getMessage());
        }

        // Handle successful registration
        if (response.hasRegistrationResult()) {
            RegisterProviderResponse.RegistrationResult registrationResult = response.getRegistrationResult();
            
            // Save providerId and name to member variables for use from views
            this.providerId = registrationResult.getProviderId();
            this.providerName = registrationResult.getProviderName();
            
            String successMsg = registrationResult.getIsNewProvider() 
                ? "New provider registered successfully" 
                : "Existing provider updated successfully";
                
            return new ResultStatus(false, successMsg);
        }

        // Shouldn't reach here, but handle unexpected response structure
        return new ResultStatus(true, "Unexpected response structure from provider registration");
    }

    public ResultStatus generateAndIngestData(
            Instant beginTime,
            Instant endTime,
            List<String> tags,
            Map<String, String> attributes,
            List<PvDetail> pvDetails,
            int bucketSizeSeconds
    ) {
        if (providerId == null) {
            return new ResultStatus(true, "Provider must be registered before ingesting data");
        }
        
        // Save state variables for use from other views
        this.dataBeginTime = beginTime;
        this.dataEndTime = endTime;
        this.pvDetails = pvDetails;
        
        try {
            // Generate and ingest data for each PV
            for (PvDetail pvDetail : pvDetails) {
                ResultStatus result = generateAndIngestPvData(pvDetail, beginTime, endTime, tags, attributes, bucketSizeSeconds);
                if (result.isError) {
                    return result; // Return first error encountered
                }
            }
            
            return new ResultStatus(false, "Successfully generated and ingested data for " + pvDetails.size() + " PVs");
            
        } catch (Exception e) {
            return new ResultStatus(true, "Error during data generation: " + e.getMessage());
        }
    }
    
    private ResultStatus generateAndIngestPvData(
            PvDetail pvDetail, Instant beginTime, Instant endTime,
            List<String> tags, Map<String, String> attributes, int bucketSizeSeconds
    ) {
        try {
            // Calculate total duration and number of buckets
            long totalDurationSeconds = java.time.Duration.between(beginTime, endTime).toSeconds();
            int numberOfBuckets = (int) Math.ceil((double) totalDurationSeconds / bucketSizeSeconds);
            
            // Generate all data values for the entire time range first
            long samplePeriodNanos = pvDetail.getSamplePeriod() * 1_000_000L; // convert ms to ns
            long totalDurationNanos = java.time.Duration.between(beginTime, endTime).toNanos();
            int totalSampleCount = (int) (totalDurationNanos / samplePeriodNanos) + 1;
            List<Object> allDataValues = generateRandomWalkData(pvDetail, totalSampleCount);
            
            // Calculate how many samples per bucket
            long bucketDurationNanos = bucketSizeSeconds * 1_000_000_000L; // convert seconds to nanoseconds
            int samplesPerBucket = (int) (bucketDurationNanos / samplePeriodNanos);
            
            // Prepare common parameters
            List<String> columnNames = java.util.Arrays.asList(pvDetail.getPvName());
            IngestionClient.IngestionDataType dataType = pvDetail.getDataType().equals("integer") ? 
                IngestionClient.IngestionDataType.INT : 
                IngestionClient.IngestionDataType.DOUBLE;
            
            int requestCount = 0;
            
            // Create and send multiple requests, one for each bucket
            for (int bucketIndex = 0; bucketIndex < numberOfBuckets; bucketIndex++) {
                // Calculate bucket start time
                Instant bucketStartTime = beginTime.plusSeconds((long) bucketIndex * bucketSizeSeconds);
                
                // Calculate bucket end time (don't exceed the original end time)
                Instant bucketEndTime = beginTime.plusSeconds((long) (bucketIndex + 1) * bucketSizeSeconds);
                if (bucketEndTime.isAfter(endTime)) {
                    bucketEndTime = endTime;
                }
                
                // Calculate sample count for this bucket
                long bucketDurationActualNanos = java.time.Duration.between(bucketStartTime, bucketEndTime).toNanos();
                int bucketSampleCount = (int) (bucketDurationActualNanos / samplePeriodNanos) + 1;
                
                // Extract the data values for this bucket
                int startIndex = bucketIndex * samplesPerBucket;
                int endIndex = Math.min(startIndex + bucketSampleCount, allDataValues.size());
                
                if (startIndex >= allDataValues.size()) {
                    break; // No more data to process
                }
                
                List<Object> bucketDataValues = allDataValues.subList(startIndex, endIndex);
                if (bucketDataValues.isEmpty()) {
                    continue; // Skip empty buckets
                }
                
                // Create request parameters for this bucket
                String requestId = java.util.UUID.randomUUID().toString();
                Long samplingClockStartSeconds = bucketStartTime.getEpochSecond();
                Long samplingClockStartNanos = (long) bucketStartTime.getNano();
                Long samplingClockPeriodNanos = samplePeriodNanos;
                Integer samplingClockCount = bucketDataValues.size();
                List<List<Object>> values = java.util.Arrays.asList(bucketDataValues);
                
                IngestionClient.IngestionRequestParams params = new IngestionClient.IngestionRequestParams(
                    this.providerId,                    // providerId
                    requestId,                          // requestId
                    null,                              // snapshotStartTimestampSeconds
                    null,                              // snapshotStartTimestampNanos
                    null,                              // timestampsSecondsList
                    null,                              // timestampNanosList
                    samplingClockStartSeconds,         // samplingClockStartSeconds
                    samplingClockStartNanos,           // samplingClockStartNanos
                    samplingClockPeriodNanos,          // samplingClockPeriodNanos
                    samplingClockCount,                // samplingClockCount
                    columnNames,                       // columnNames
                    dataType,                          // dataType
                    values,                            // values
                    tags,                              // tags
                    attributes,                        // attributes
                    null,                              // eventDescription
                    null,                              // eventStartSeconds
                    null,                              // eventStartNanos
                    null,                              // eventStopSeconds
                    null,                              // eventStopNanos
                    false                              // useSerializedDataColumns
                );
                
                // Call ingestData() API method for this bucket
                final IngestDataResponse response = api.ingestionClient.sendIngestData(params);
                requestCount++;
                
                // Handle response
                if (response == null) {
                    return new ResultStatus(true, "Failed to ingest data for PV " + pvDetail.getPvName() + 
                        " bucket " + (bucketIndex + 1) + " - null response");
                }
                
                if (response.hasExceptionalResult()) {
                    return new ResultStatus(true, "Data ingestion failed for PV " + pvDetail.getPvName() + 
                        " bucket " + (bucketIndex + 1) + ": " + response.getExceptionalResult().getMessage());
                }
            }
            
            return new ResultStatus(false, "Successfully ingested data for PV " + pvDetail.getPvName() + 
                " in " + requestCount + " bucket(s) of " + bucketSizeSeconds + " second(s) each");
            
        } catch (Exception e) {
            return new ResultStatus(true, "Error ingesting data for PV " + pvDetail.getPvName() + ": " + e.getMessage());
        }
    }
    
    private List<Object> generateRandomWalkData(PvDetail pvDetail, int sampleCount) {
        List<Object> values = new java.util.ArrayList<>();
        java.util.Random random = new java.util.Random();
        
        // Parse initial value and max step
        double currentValue;
        double maxStep;
        
        try {
            currentValue = Double.parseDouble(pvDetail.getInitialValue());
            maxStep = Double.parseDouble(pvDetail.getMaxStepMagnitude());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric values in PV " + pvDetail.getPvName());
        }
        
        // Generate random walk values
        for (int i = 0; i < sampleCount; i++) {
            // Add current value to list
            if (pvDetail.getDataType().equals("integer")) {
                values.add((int) Math.round(currentValue));
            } else {
                values.add(currentValue);
            }
            
            // Calculate next value using random walk
            if (i < sampleCount - 1) { // Don't update after last sample
                double stepSize = (random.nextDouble() - 0.5) * 2 * maxStep; // Random step in range [-maxStep, +maxStep]
                currentValue += stepSize;
            }
        }
        
        return values;
    }
    
    // Getters for state variables (for use by other views)
    public String getProviderId() { return providerId; }
    public String getProviderName() { return providerName; }
    public Instant getDataBeginTime() { return dataBeginTime; }
    public Instant getDataEndTime() { return dataEndTime; }
    public List<PvDetail> getPvDetails() { return pvDetails; }
}
