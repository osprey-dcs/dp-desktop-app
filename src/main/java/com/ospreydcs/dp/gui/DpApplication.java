package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.AnnotationClient;
import com.ospreydcs.dp.client.ApiClient;
import com.ospreydcs.dp.client.IngestionClient;
import com.ospreydcs.dp.client.QueryClient;
import com.ospreydcs.dp.client.result.*;
import com.ospreydcs.dp.grpc.v1.annotation.ExportDataRequest;
import com.ospreydcs.dp.grpc.v1.common.CalculationsSpec;
import com.ospreydcs.dp.grpc.v1.ingestion.RegisterProviderResponse;
import com.ospreydcs.dp.grpc.v1.query.QueryTableRequest;
import com.ospreydcs.dp.gui.model.DataBlockDetail;
import com.ospreydcs.dp.gui.model.PvDetail;
import com.ospreydcs.dp.service.common.model.ResultStatus;
import com.ospreydcs.dp.service.common.protobuf.EventMetadataUtility;
import com.ospreydcs.dp.service.inprocess.InprocessServiceEcosystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DpApplication {

    // static variables
    private static final Logger logger = LogManager.getLogger();

    // instance variables
    private InprocessServiceEcosystem inprocessServiceEcosystem = null;
    private ApiClient api = null;
    
    // state variables for cross-view usage
    private String providerId = null;
    private String providerName = null;
    private Instant dataBeginTime = null;
    private Instant dataEndTime = null;
    private List<String> pvNames = null;
    
    // application state tracking for home view
    private boolean hasIngestedData = false;
    private boolean hasPerformedQueries = false;
    private String lastOperationResult = null;
    private int totalPvsIngested = 0;
    private int totalBucketsCreated = 0;

    public enum ExportOutputFileFormat {
        CSV,
        XLSX,
        HDF5
    }

    // Getters for state variables (for use by other views)
    public String getProviderId() { return providerId; }
    public String getProviderName() { return providerName; }
    public Instant getDataBeginTime() { return dataBeginTime; }
    public Instant getDataEndTime() { return dataEndTime; }
    public List<String> getPvNames() { return pvNames; }

    // Getters for application state tracking (for home view)
    public boolean hasIngestedData() { return hasIngestedData; }
    public boolean hasPerformedQueries() { return hasPerformedQueries; }
    public String getLastOperationResult() { return lastOperationResult; }
    public int getTotalPvsIngested() { return totalPvsIngested; }
    public int getTotalBucketsCreated() { return totalBucketsCreated; }

    // Methods for updating application state (for use by other operations)
    public void setHasPerformedQueries(boolean hasPerformed) {
        this.hasPerformedQueries = hasPerformed;
    }

    public void setLastOperationResult(String result) {
        this.lastOperationResult = result;
    }
    
    // Methods for updating query state (for use by query view)
    public void setQueryPvNames(List<String> pvNames) {
        // Store PV names directly for cross-view usage
        if (pvNames != null && !pvNames.isEmpty()) {
            this.pvNames = new java.util.ArrayList<>(pvNames);
        } else {
            this.pvNames = null;
        }
    }
    
    // General method for setting PV names (for use by data import and other workflows)
    public void setPvNames(List<String> pvNames) {
        if (pvNames != null && !pvNames.isEmpty()) {
            this.pvNames = new java.util.ArrayList<>(pvNames);
        } else {
            this.pvNames = null;
        }
    }
    
    public void setQueryTimeRange(Instant beginTime, Instant endTime) {
        this.dataBeginTime = beginTime;
        this.dataEndTime = endTime;
    }

    public boolean init() {

        // create InprocessServiceEcosystem with default local grpc targets
        inprocessServiceEcosystem = new InprocessServiceEcosystem();
        if (!inprocessServiceEcosystem.init()) {
            return false;
        }

        // initialize ApiClient with grpc targets from default inprocess service ecosystem
        api = new ApiClient(
            inprocessServiceEcosystem.ingestionService.getIngestionChannel(),
            inprocessServiceEcosystem.queryService.getQueryChannel(),
            inprocessServiceEcosystem.annotationService.getChannel()
        );
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
        final RegisterProviderApiResult apiResult = api.ingestionClient.registerProvider(params);

        if (apiResult.resultStatus.isError) {
            // there was an error handling the API call
            return apiResult.resultStatus;

        } else {
            // API call was successful;

            final RegisterProviderResponse response = apiResult.registerProviderResponse;

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

            } else {
                // Shouldn't reach here, but handle unexpected response structure
                return new ResultStatus(true, "Unexpected response structure from provider registration");
            }
        }
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
        // Extract PV names from PvDetail objects for cross-view sharing
        this.pvNames = new java.util.ArrayList<>();
        for (PvDetail pvDetail : pvDetails) {
            this.pvNames.add(pvDetail.getPvName());
        }
        
        try {
            int totalBuckets = 0;
            
            // Generate and ingest data for each PV
            for (PvDetail pvDetail : pvDetails) {
                ResultStatus result = generateAndIngestPvData(pvDetail, beginTime, endTime, tags, attributes, bucketSizeSeconds);
                logger.debug("generating pv: {} values per second: {}", pvDetail.getPvName(), pvDetail.getValuesPerSecond());
                if (result.isError) {
                    return result; // Return first error encountered
                }
                
                // Count buckets created for this PV
                long totalDurationSeconds = java.time.Duration.between(beginTime, endTime).toSeconds();
                int pvBuckets = (int) Math.ceil((double) totalDurationSeconds / bucketSizeSeconds);
                totalBuckets += pvBuckets;
            }
            
            // Update application state tracking
            this.hasIngestedData = true;
            this.totalPvsIngested = pvDetails.size();
            this.totalBucketsCreated = totalBuckets;
            
            String successMessage = "Successfully generated and ingested data for " + pvDetails.size() + 
                " PVs in " + totalBuckets + " bucket(s)";
            this.lastOperationResult = successMessage;
            
            return new ResultStatus(false, successMessage);
            
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
            int valuesPerSecond = pvDetail.getValuesPerSecond();
            long samplePeriodNanos = 1_000_000_000L / valuesPerSecond; // nanoseconds per sample
            int totalSampleCount = (int) (totalDurationSeconds * valuesPerSecond);
            List<Object> allDataValues = generateRandomWalkData(pvDetail, totalSampleCount);
            
            // Calculate how many samples per bucket
            int samplesPerBucket = valuesPerSecond * bucketSizeSeconds;
            
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
                
                // Calculate sample count for this bucket - use exact samplesPerBucket for full buckets
                int bucketSampleCount = samplesPerBucket;
                
                // For the last bucket, adjust if it's shorter than a full second
                if (bucketIndex == numberOfBuckets - 1) {
                    long remainingDurationSeconds = totalDurationSeconds - (bucketIndex * bucketSizeSeconds);
                    if (remainingDurationSeconds < bucketSizeSeconds) {
                        bucketSampleCount = (int) (remainingDurationSeconds * valuesPerSecond);
                    }
                }
                
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
                final IngestDataApiResult apiResult = api.ingestionClient.ingestData(params, null, null);
                requestCount++;

                if (apiResult.resultStatus.isError) {
                    return apiResult.resultStatus;
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

    public QueryPvMetadataApiResult queryPvMetadata(List<String> pvNameList) {
        return api.queryClient.queryPvMetadata(pvNameList);
    }

    public QueryPvMetadataApiResult queryPvMetadata(String pvNamePattern) {
        return api.queryClient.queryPvMetadata(pvNamePattern);
    }

    public QueryTableApiResult queryTable(List<String> pvNameList, Instant beginTime, Instant endTime) {

        // build params for api call
        final QueryClient.QueryTableRequestParams params =
                new QueryClient.QueryTableRequestParams(
                        QueryTableRequest.TableResultFormat.TABLE_FORMAT_ROW_MAP,
                        pvNameList,
                        null,
                        beginTime.getEpochSecond(),
                        Integer.toUnsignedLong(beginTime.getNano()),
                        endTime.getEpochSecond(),
                        Integer.toUnsignedLong(endTime.getNano()));

        // call api method
        return api.queryClient.queryTable(params);
    }

    public SaveDataSetApiResult saveDataSet(
            String id, String name, String description, List<DataBlockDetail> dataBlockDetails) {

        // create API data blocks
        final List<AnnotationClient.AnnotationDataBlock> annotationDataBlocks = new ArrayList<>();
        for (DataBlockDetail dataBlockDetail : dataBlockDetails) {
            annotationDataBlocks.add(new AnnotationClient.AnnotationDataBlock(
                    dataBlockDetail.getBeginTime().getEpochSecond(),
                    dataBlockDetail.getBeginTime().getNano(),
                    dataBlockDetail.getEndTime().getEpochSecond(),
                    dataBlockDetail.getEndTime().getNano(),
                    dataBlockDetail.getPvNames()
            ));
        }

        // create API dataset containing datablocks
        String annotationId;
        // make sure id is either null or non-empty string
        if (id != null && id.isEmpty()) {
            annotationId = null;
        } else {
            annotationId = id;
        }
        final AnnotationClient.AnnotationDataSet annotationDataSet =
                new AnnotationClient.AnnotationDataSet(
                        annotationId,
                        name,
                        "demo-user",
                        description,
                        annotationDataBlocks
                );

        // create params for api call with dataset
        final AnnotationClient.SaveDataSetParams saveDataSetParams =
                new AnnotationClient.SaveDataSetParams(annotationDataSet);

        // call api method
        return api.annotationClient.saveDataSet(saveDataSetParams);
    }

    public SaveAnnotationApiResult saveAnnotation(
            String id,
            String name,
            List<String> dataSetIds,
            List<String> annotationIds,
            String comment,
            List<String> tags,
            Map<String, String> attributeMap,
            String eventName
    ) {
        // create API event params
        EventMetadataUtility.EventMetadataParams eventParams = null;
        if (eventName != null) {
            eventParams = new EventMetadataUtility.EventMetadataParams(
                    eventName, null, null, null, null);
        }

        // create API request params
        final AnnotationClient.SaveAnnotationRequestParams params =
                new AnnotationClient.SaveAnnotationRequestParams(
                        id,
                        "demo-user",
                        name,
                        dataSetIds,
                        annotationIds,
                        comment,
                        tags,
                        attributeMap,
                        eventParams,
                        null
                );

        // call api method
        return api.annotationClient.saveAnnotation(params);
    }

    public ExportDataApiResult exportData(
            String datasetId,
            CalculationsSpec calculationsSpec,
            ExportOutputFileFormat outputFileFormat
    ) {
        // get API enum value for application enum value for output format
        ExportDataRequest.ExportOutputFormat apiOutputFormat = null;
        switch (outputFileFormat) {
            case CSV:
                apiOutputFormat = ExportDataRequest.ExportOutputFormat.EXPORT_FORMAT_CSV;
                break;
            case XLSX:
                apiOutputFormat = ExportDataRequest.ExportOutputFormat.EXPORT_FORMAT_XLSX;
                break;
            case HDF5:
                apiOutputFormat = ExportDataRequest.ExportOutputFormat.EXPORT_FORMAT_HDF5;
                break;
        }
        Objects.requireNonNull(apiOutputFormat);

        // create API request params
        AnnotationClient.ExportDataRequestParams params =
                new AnnotationClient.ExportDataRequestParams(datasetId, calculationsSpec, apiOutputFormat);

        // call api method
        return api.annotationClient.exportData(params);
    }

}
