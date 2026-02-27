package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.*;
import com.ospreydcs.dp.client.result.*;
import com.ospreydcs.dp.grpc.v1.annotation.Calculations;
import com.ospreydcs.dp.grpc.v1.annotation.ExportDataRequest;
import com.ospreydcs.dp.grpc.v1.common.*;
import com.ospreydcs.dp.grpc.v1.ingestion.RegisterProviderResponse;
import com.ospreydcs.dp.grpc.v1.ingestionstream.PvConditionTrigger;
import com.ospreydcs.dp.grpc.v1.ingestionstream.SubscribeDataEventResponse;
import com.ospreydcs.dp.grpc.v1.query.QueryTableRequest;
import com.ospreydcs.dp.gui.model.*;
import com.ospreydcs.dp.service.common.model.ResultStatus;
import com.ospreydcs.dp.service.common.protobuf.TimestampUtility;
import com.ospreydcs.dp.service.inprocess.InprocessServiceEcosystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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
    private List<DataEventSubscription> dataEventSubscriptions = new ArrayList<>();
    
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

    public enum TriggerCondition {
        EQUAL_TO,
        GREATER,
        GREATER_OR_EQUAL,
        LESS,
        LESS_OR_EQUAL,
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
            Collections.sort(this.pvNames);
        } else {
            this.pvNames = null;
        }
    }
    
    // General method for setting PV names (for use by data import and other workflows)
    public void setPvNames(List<String> pvNames) {
        if (pvNames != null && !pvNames.isEmpty()) {
            this.pvNames = new java.util.ArrayList<>(pvNames);
            Collections.sort(this.pvNames);
        } else {
            this.pvNames = null;
        }
    }

    // Time range management methods (for data-event-explore navigation)
    public void setDataBeginTime(Instant beginTime) {
        this.dataBeginTime = beginTime;
    }

    public void setDataEndTime(Instant endTime) {
        this.dataEndTime = endTime;
    }

    // Data event subscriptions access method
    public List<DataEventSubscription> getDataEventSubscriptions() {
        return new ArrayList<>(dataEventSubscriptions);
    }
    
    // Individual PV name management methods (for pv-explore view)
    public void addPvName(String pvName) {
        if (pvName == null || pvName.trim().isEmpty()) {
            return;
        }
        
        if (this.pvNames == null) {
            this.pvNames = new ArrayList<>();
        }
        
        // Check for duplicates and ignore if already exists
        if (!this.pvNames.contains(pvName.trim())) {
            this.pvNames.add(pvName.trim());
            Collections.sort(this.pvNames);
        }
    }
    
    public void removePvName(String pvName) {
        if (this.pvNames != null && pvName != null) {
            this.pvNames.remove(pvName.trim());
            if (this.pvNames.isEmpty()) {
                this.pvNames = null;
            }
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
            inprocessServiceEcosystem.annotationService.getChannel(),
            inprocessServiceEcosystem.ingestionStreamService.getChannel()
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

    public ResultStatus ingestImportedData(
            List<String> tags,
            Map<String, String> attributes,
            String eventName,
            List<DataImportResult.DataFrameResult> dataFrames,
            List<SubscribeDataEventDetail> subscriptionDetails
    ) {
        if (providerId == null) {
            return new ResultStatus(true, "Provider must be registered before ingesting data");
        }

        // create map of PV data type by PV name for convenience
        final Map<String, IngestionClient.IngestionDataType> pvDataTypeMap = new HashMap<>();
        for (var dataFrame : dataFrames) {
            for (var dataColumn : dataFrame.columns) {
                IngestionClient.IngestionDataType pvDataType = null;
                switch (dataColumn.getDataValues(0).getValueCase()) {
                    case STRINGVALUE -> {
                        pvDataType = IngestionClient.IngestionDataType.STRING;
                    }
                    case BOOLEANVALUE -> {
                        pvDataType = IngestionClient.IngestionDataType.BOOLEAN;
                    }
                    case UINTVALUE -> {
                        pvDataType = IngestionClient.IngestionDataType.UINT;
                    }
                    case ULONGVALUE -> {
                        pvDataType = IngestionClient.IngestionDataType.ULONG;
                    }
                    case INTVALUE -> {
                        pvDataType = IngestionClient.IngestionDataType.INT;
                    }
                    case LONGVALUE -> {
                        pvDataType = IngestionClient.IngestionDataType.LONG;
                    }
                    case FLOATVALUE -> {
                        pvDataType = IngestionClient.IngestionDataType.FLOAT;
                    }
                    case DOUBLEVALUE -> {
                        pvDataType = IngestionClient.IngestionDataType.DOUBLE;
                    }
                    case BYTEARRAYVALUE -> {
                        pvDataType = IngestionClient.IngestionDataType.BYTE_ARRAY;
                    }
                    case ARRAYVALUE -> {
                        pvDataType = IngestionClient.IngestionDataType.ARRAY;
                    }
                    case STRUCTUREVALUE -> {
                        pvDataType = IngestionClient.IngestionDataType.STRUCTURE;
                    }
                    case IMAGEVALUE -> {
                        pvDataType = IngestionClient.IngestionDataType.IMAGE;
                    }
                    case TIMESTAMPVALUE -> {
                        pvDataType = IngestionClient.IngestionDataType.TIMESTAMP;
                    }
                    case VALUE_NOT_SET -> {
                        return new ResultStatus(
                                true,
                                "DataValue type not set for column: " + dataColumn.getName());
                    }
                }
                pvDataTypeMap.put(dataColumn.getName(), pvDataType);
            }
        }

        // process data event subscriptions
        for (SubscribeDataEventDetail subscriptionDetail : subscriptionDetails) {

            // get PV data type
            final IngestionClient.IngestionDataType pvDataType = pvDataTypeMap.get(subscriptionDetail.pvName);
            if (pvDataType == null) {
                return new ResultStatus(
                        true,
                        "unknown PV name in subscription: " + subscriptionDetail.pvName);
            }

            // call subscribeDataEvent for each subscription
            final ResultStatus subscriptionStatus =
                    subscribeDataEvent(subscriptionDetail, pvDataType);
            if (subscriptionStatus.isError) {
                return new ResultStatus(
                        true,
                        "error handling subscription: " + subscriptionStatus.msg);
            }
        }

        try {
            // send an ingestData() request for each frame
            final Set<String> pvNames = new HashSet<>();
            Instant minBeginInstant = null;
            Instant maxEndInstant = null;
            int requestCount = 0;
            for (DataImportResult.DataFrameResult frame : dataFrames) {

                final String requestId = UUID.randomUUID().toString();

                final IngestionClient.IngestionRequestParams params = new IngestionClient.IngestionRequestParams(
                        this.providerId,                   // providerId
                        requestId
                );

                // Call ingestData() API method
                final IngestDataApiResult apiResult = api.ingestionClient.ingestData(
                        params,
                        frame.timestamps,
                        frame.columns);
                requestCount++;

                if (apiResult.resultStatus.isError) {
                    return apiResult.resultStatus;
                }

                // add pv names for frame to list of unique pv names ingested for imported file
                pvNames.addAll(frame.columns.stream().map(col -> col.getName()).collect(Collectors.toList()));

                // update min begin / max end times ingested for imported file
                final Instant frameBeginInstant = TimestampUtility.instantFromTimestamp(frame.timestamps.getFirst());
                if (minBeginInstant == null || frameBeginInstant.isBefore(minBeginInstant)) {
                    minBeginInstant = frameBeginInstant;
                }
                final Instant frameEndInstant = TimestampUtility.instantFromTimestamp(frame.timestamps.getLast());
                if (maxEndInstant == null || frameEndInstant.isAfter(maxEndInstant)) {
                    maxEndInstant = frameEndInstant;
                }
            }

            final List<String> sortedPvNames = pvNames.stream().sorted().collect(Collectors.toList());

            setPvNames(sortedPvNames);
            this.dataBeginTime = minBeginInstant;
            this.dataEndTime = maxEndInstant;
            
            // Update application state tracking (enables Explore menu items)
            this.hasIngestedData = true;
            this.totalPvsIngested = sortedPvNames.size();
            this.totalBucketsCreated = requestCount; // Each imported frame becomes a "bucket"

            String successMessage = "Successfully ingested imported data for PVs: " + sortedPvNames
                    + " in " + requestCount + " ingestData() requests begin time: "
                    + minBeginInstant + " and end time: " + maxEndInstant;
            this.lastOperationResult = successMessage;

            return new ResultStatus(false, successMessage);

        } catch (Exception e) {
            return new ResultStatus(true, "Error during data generation: " + e.getMessage());
        }
    }

    public ResultStatus generateAndIngestData(
            Instant beginTime,
            Instant endTime,
            List<String> tags,
            Map<String, String> attributes,
            String eventName,
            List<PvDetail> pvDetails,
            int bucketSizeSeconds,
            List<SubscribeDataEventDetail> subscriptionDetails
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

        // create map of PvDetail by PV name for convenience
        final Map<String, PvDetail> pvDetailMap = pvDetails.stream()
                .collect(Collectors.toMap(PvDetail::getPvName, pvDetail -> pvDetail));

        // process data event subscriptions
        for (SubscribeDataEventDetail subscriptionDetail : subscriptionDetails) {

            // determine PV data type for subscription
            final PvDetail pvDetail = pvDetailMap.get(subscriptionDetail.pvName);
            if (pvDetail == null) {
                return new ResultStatus(
                        true,
                        "unknown subscription PV name: " + subscriptionDetail.pvName);
            }

            // get PV data type
            final String pvDataTypeName = pvDetail.getDataType();

            // get data type enum value for PV
            IngestionClient.IngestionDataType pvDataType;
            if (pvDataTypeName.equals("integer")) {
                pvDataType = IngestionClient.IngestionDataType.INT;
            } else {
                pvDataType = IngestionClient.IngestionDataType.DOUBLE;
            }

            // call subscribeDataEvent for each subscription
            final ResultStatus subscriptionStatus =
                    subscribeDataEvent(subscriptionDetail, pvDataType);
            if (subscriptionStatus.isError) {
                return new ResultStatus(
                        true,
                        "error handling subscription: " + subscriptionStatus.msg);
            }
        }

        try {
            int totalBuckets = 0;
            
            // Generate and ingest data for each PV
            for (PvDetail pvDetail : pvDetails) {
                ResultStatus result = generateAndIngestPvData(pvDetail, beginTime, endTime, tags, attributes, eventName, bucketSizeSeconds);
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
            List<String> tags, Map<String, String> attributes, String eventName, int bucketSizeSeconds
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
                    values
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

    public QueryProvidersApiResult queryProviders(
            String idCriterion,
            String textCriterion, // search name and description fields
            String tagsCriterion,
            String attributeKeyCriterion,
            String attributeValueCriterion
    ) {
        // create params
        QueryClient.QueryProvidersRequestParams params = new QueryClient.QueryProvidersRequestParams();
        if (idCriterion != null && !idCriterion.isEmpty()) {
            params.setIdCriterion(idCriterion);
        }
        if (textCriterion != null && !textCriterion.isEmpty()) {
            params.setTextCriterion(textCriterion);
        }
        if (tagsCriterion != null && !tagsCriterion.isEmpty()) {
            params.setTagsCriterion(tagsCriterion);
        }
        if (attributeKeyCriterion != null && !attributeKeyCriterion.isEmpty() && attributeValueCriterion != null && !attributeValueCriterion.isEmpty()) {
            params.setAttributesCriterion(attributeKeyCriterion, attributeValueCriterion);
        }

        return api.queryClient.queryProviders(params);
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

    public QueryDataSetsApiResult queryDataSets(
            String idCriterion,
            String ownerCriterion,
            String textCriterion, // search name and description fields
            String pvNameCriterion
    ) {
        // create params
        AnnotationClient.QueryDataSetsParams params = new AnnotationClient.QueryDataSetsParams();
        if (idCriterion != null && !idCriterion.isEmpty()) {
            params.setIdCriterion(idCriterion);
        }
        if (ownerCriterion != null && !ownerCriterion.isEmpty()) {
            params.setOwnerCriterion(ownerCriterion);
        }
        if (textCriterion != null && !textCriterion.isEmpty()) {
            params.setTextCriterion(textCriterion);
        }
        if (pvNameCriterion != null && !pvNameCriterion.isEmpty()) {
            params.setPvNameCriterion(pvNameCriterion);
        }

        return api.annotationClient.queryDataSets(params);
    }

    public SaveAnnotationApiResult saveAnnotation(
            String id,
            String name,
            List<String> dataSetIds,
            List<String> annotationIds,
            String comment,
            List<String> tags,
            Map<String, String> attributeMap,
            String eventName,
            List<DataFrameDetails> calculationsDataFrameDetails
    ) {
        // create API calculations
        Calculations.Builder calculationsBuilder = null;
        if (calculationsDataFrameDetails != null && !calculationsDataFrameDetails.isEmpty()) {
            calculationsBuilder = Calculations.newBuilder();
            for (DataFrameDetails dataFrameDetails : calculationsDataFrameDetails) {
                final TimestampList frameTimestampList =
                        TimestampList.newBuilder().addAllTimestamps(dataFrameDetails.getTimestamps()).build();
                final DataTimestamps frameDataTimestamps = DataTimestamps.newBuilder().setTimestampList(frameTimestampList).build();
                final Calculations.CalculationsDataFrame calculationsDataFrame = Calculations.CalculationsDataFrame.newBuilder()
                        .setName(dataFrameDetails.getName())
                        .setDataTimestamps(frameDataTimestamps)
                        .addAllDataColumns(dataFrameDetails.getDataColumns())
                        .build();
                calculationsBuilder.addCalculationDataFrames(calculationsDataFrame);
            }
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
                        calculationsBuilder.build()
                );

        // call api method
        return api.annotationClient.saveAnnotation(params);
    }

    public QueryAnnotationsApiResult queryAnnotations(
            String idCriterion,
            String ownerCriterion,
            String dataSetsCriterion,
            String annotationsCriterion,
            String textCriterion, // search name, comment, event description fields
            String tagsCriterion,
            String attributeKeyCriterion,
            String attributeValueCriterion

    ) {
        // create params
        AnnotationClient.QueryAnnotationsParams params = new AnnotationClient.QueryAnnotationsParams();
        if (idCriterion != null && !idCriterion.isEmpty()) {
            params.setIdCriterion(idCriterion);
        }
        if (ownerCriterion != null && !ownerCriterion.isEmpty()) {
            params.setOwnerCriterion(ownerCriterion);
        }
        if (dataSetsCriterion != null && !dataSetsCriterion.isEmpty()) {
            params.setDatasetsCriterion(dataSetsCriterion);
        }
        if (annotationsCriterion != null && !annotationsCriterion.isEmpty()) {
            params.setAnnotationsCriterion(annotationsCriterion);
        }
        if (textCriterion != null && !textCriterion.isEmpty()) {
            params.setTextCriterion(textCriterion);
        }
        if (tagsCriterion != null && !tagsCriterion.isEmpty()) {
            params.setTagsCriterion(tagsCriterion);
        }
        if (attributeKeyCriterion != null && !attributeKeyCriterion.isEmpty() && attributeValueCriterion != null && !attributeValueCriterion.isEmpty()) {
            params.setAttributesCriterion(attributeKeyCriterion, attributeValueCriterion);
        }

        return api.annotationClient.queryAnnotations(params);
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

    public ResultStatus subscribeDataEvent(
            SubscribeDataEventDetail subscriptionDetail,
            IngestionClient.IngestionDataType dataType
    ) {
        // create protobuf DataValue for specified dataType and triggerValue
        DataValue triggerValue = null;
        switch (dataType) {
            case UINT -> {
                triggerValue = DataValue.newBuilder()
                        .setIntValue(Integer.valueOf(subscriptionDetail.triggerValue))
                        .build();
            }
            case ULONG -> {
                triggerValue = DataValue.newBuilder()
                        .setLongValue(Long.valueOf(subscriptionDetail.triggerValue))
                        .build();
            }
            case INT -> {
                triggerValue = DataValue.newBuilder()
                        .setIntValue(Integer.valueOf(subscriptionDetail.triggerValue))
                        .build();
            }
            case LONG -> {
                triggerValue = DataValue.newBuilder()
                        .setLongValue(Long.valueOf(subscriptionDetail.triggerValue))
                        .build();
            }
            case FLOAT -> {
                triggerValue = DataValue.newBuilder()
                        .setFloatValue(Float.valueOf(subscriptionDetail.triggerValue))
                        .build();
            }
            case DOUBLE -> {
                triggerValue = DataValue.newBuilder()
                        .setDoubleValue(Double.valueOf(subscriptionDetail.triggerValue))
                        .build();
            }
            default -> {
                return new ResultStatus(
                        true,
                        "unsupported data event subscription type: " + dataType.name());
            }
        }
        Objects.requireNonNull(triggerValue);

        // create protobuf trigger condition from specified TriggerCondition enum
        PvConditionTrigger.PvCondition pvCondition = null;
        switch (subscriptionDetail.triggerCondition) {
            case EQUAL_TO -> {
                pvCondition = PvConditionTrigger.PvCondition.PV_CONDITION_EQUAL_TO;
            }
            case GREATER -> {
                pvCondition = PvConditionTrigger.PvCondition.PV_CONDITION_GREATER;
            }
            case GREATER_OR_EQUAL -> {
                pvCondition = PvConditionTrigger.PvCondition.PV_CONDITION_GREATER_EQ;
            }
            case LESS -> {
                pvCondition = PvConditionTrigger.PvCondition.PV_CONDITION_LESS;
            }
            case LESS_OR_EQUAL -> {
                pvCondition = PvConditionTrigger.PvCondition.PV_CONDITION_LESS_EQ;
            }
        }
        Objects.requireNonNull(pvCondition);

        // create protobuf PvConditionTrigger
        final PvConditionTrigger trigger = PvConditionTrigger.newBuilder()
                .setPvName(subscriptionDetail.pvName)
                .setCondition(pvCondition)
                .setValue(triggerValue)
                .build();

        // create API request params
        final IngestionStreamClient.SubscribeDataEventRequestParams params =
                new IngestionStreamClient.SubscribeDataEventRequestParams(
                        List.of(trigger), null, null, null);

        // call API method
        SubscribeDataEventApiResult result =
                api.ingestionStreamClient.subscribeDataEvent(params, 25);

        // if successful, manage new subscription
        if ( ! result.resultStatus.isError) {
            dataEventSubscriptions.add(new DataEventSubscription(subscriptionDetail, result.subscribeDataEventCall));
        }

        return result.resultStatus;
    }

    public ResultStatus cancelDataEventSubscription(DataEventSubscription subscription) {

        // cancel the subscription
        api.ingestionStreamClient.cancelSubscribeDataEventCall(subscription.subscribeDataEventCall);

        // un-manage the subscription, whether unsubscribe succeeded or failed
        dataEventSubscriptions.remove(subscription);

        // return error status
        final IngestionStreamClient.SubscribeDataEventResponseObserver responseObserver =
                subscription.subscribeDataEventCall.responseObserver();
        Objects.requireNonNull(responseObserver);
        if (responseObserver.isError()) {
            return new ResultStatus(true, responseObserver.getErrorMessage());
        } else {
            return new ResultStatus(false, "");
        }
    }

    public List<SubscribeDataEventResponse.Event> dataEventsForSubscription(DataEventSubscription subscription) {
        // return list of events contained in responseObserver
        final IngestionStreamClient.SubscribeDataEventResponseObserver responseObserver =
                subscription.subscribeDataEventCall.responseObserver();
        Objects.requireNonNull(responseObserver);
        return(responseObserver.getEventList());
    }

}
