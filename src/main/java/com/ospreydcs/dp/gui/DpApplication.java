package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.*;
import com.ospreydcs.dp.client.criteria.AttributeCriterion;
import com.ospreydcs.dp.client.criteria.TextMatch;
import com.ospreydcs.dp.client.result.*;
import com.ospreydcs.dp.grpc.v1.annotation.Annotation;
import com.ospreydcs.dp.grpc.v1.annotation.Calculations;
import com.ospreydcs.dp.grpc.v1.annotation.DataSet;
import com.ospreydcs.dp.grpc.v1.annotation.ExportDataRequest;
import com.ospreydcs.dp.grpc.v1.common.*;
import com.ospreydcs.dp.grpc.v1.ingestion.RegisterProviderResponse;
import com.ospreydcs.dp.grpc.v1.ingestionstream.PvConditionTrigger;
import com.ospreydcs.dp.grpc.v1.ingestionstream.SubscribeDataEventResponse;
import com.ospreydcs.dp.gui.config.AppConfiguration;
import com.ospreydcs.dp.gui.config.RemoteChannelFactory;
import com.ospreydcs.dp.gui.model.*;
import com.ospreydcs.dp.service.common.model.ResultStatus;
import com.ospreydcs.dp.service.common.protobuf.TimestampUtility;
import com.ospreydcs.dp.service.inprocess.InprocessServiceEcosystem;
import io.grpc.ManagedChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DpApplication {

    // static variables
    private static final Logger logger = LogManager.getLogger();

    // instance variables
    private AppConfiguration configuration = null;
    private InprocessServiceEcosystem inprocessServiceEcosystem = null;
    private List<ManagedChannel> remoteChannels = List.of();
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

    /**
     * Whether the archive already held data when this application launched.
     *
     * <p><b>Distinct from {@code hasIngestedData}, and #4 is what made them distinct.</b>  That
     * flag records that <i>this session</i> ingested; this one records that there is something to
     * explore regardless of who put it there.  Before the demo database stopped being dropped at
     * launch the two were the same statement in demo mode, which is why one flag used to serve
     * both questions.
     *
     * <p>Kept separate rather than folded into {@code hasIngestedData} deliberately: that flag also
     * drives the home view's text and the Data Events gate, so setting it here would make the home
     * view claim this session ingested data it did not ingest -- trading a menu bug for a
     * truthfulness one.
     */
    private boolean archiveHasData = false;
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

    // ------------------- parameter normalization helpers ---------------------------
    //
    // The client request builders in dp-service treat null as "field not supplied" and omit it
    // from the request, while the UI hands this class empty strings and collections for fields
    // the user left blank.  These helpers convert between the two conventions in one place.
    // Package-private and static so they are unit-testable without a service ecosystem.

    /**
     * Returns the value, or null when it is null or empty, so optional request fields left
     * blank in the UI are omitted rather than sent as empty strings.
     */
    static String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    /**
     * Returns the list, or null when it is null or empty.
     */
    static <T> List<T> emptyToNull(List<T> value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    /**
     * Returns the map, or null when it is null or empty.
     */
    static <K, V> Map<K, V> emptyToNull(Map<K, V> value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    /**
     * Applies the setter only when the criterion is non-null and non-empty, so blank search
     * fields are left out of the query entirely.
     */
    static void setIfPresent(String criterion, Consumer<String> setter) {
        if (criterion != null && !criterion.isEmpty()) {
            setter.accept(criterion);
        }
    }

    /**
     * Applies the setter only when BOTH values are non-null and non-empty.  Used for the
     * attribute key/value criterion pair, which is only meaningful as a complete pair.
     */
    static void setIfBothPresent(String first, String second, BiConsumer<String, String> setter) {
        if (first != null && !first.isEmpty() && second != null && !second.isEmpty()) {
            setter.accept(first, second);
        }
    }

    /**
     * Converts an Instant to a protobuf Timestamp, mapping null to null.
     *
     * The null case is the point of this helper.  A Timestamp is a message field with real
     * protobuf field presence, so an optional time left unset must be passed as null rather than
     * as a zero-valued Timestamp: the latter marks the field present and describes a time at the
     * epoch.  For SaveConfigurationActivationRequest.endTime that is the difference between an
     * open-ended activation and one that ended in 1970.
     *
     * TimestampUtility.getTimestampFromInstant() does the non-null conversion but throws on null,
     * so it cannot be called directly for an optional field.
     */
    static Timestamp timestampFromInstant(Instant instant) {
        return (instant == null) ? null : TimestampUtility.getTimestampFromInstant(instant);
    }

    /**
     * Thrown when a page request fails partway through a paged query.
     *
     * Unchecked so it can propagate out of the fetchPage lambda in accumulatePages() without
     * forcing a checked-exception signature onto the generic helper.  A failure must abort the
     * whole accumulation rather than break the loop: returning what had accumulated so far would
     * present a partial result as a complete one, which is the same class of silent-wrong-answer
     * bug transparent paging is being added to fix.
     */
    public static class QueryFailedException extends RuntimeException {
        public QueryFailedException(String message) {
            super(message);
        }
    }

    // ------------------- transparent paging ---------------------------

    /**
     * Maximum number of records accumulated by a paged query before it stops following
     * nextPageToken.
     *
     * queryDataSets() and queryAnnotations() became paged in dp-grpc #132, and an unset limit
     * means the server's default page size rather than "everything".  This class follows
     * nextPageToken internally so the explore views keep receiving one complete list and need no
     * paging UI, but doing that without a bound would move the unbounded read from the server to
     * the client -- the exact thing server-side paging was introduced to prevent.  This cap is
     * what keeps transparent paging from re-creating that problem one layer up.
     *
     * 5000 is large enough that ordinary demo result sets never reach it and small enough to stay
     * well inside that bound.  For comparison, server-side defaults are 10000 for sample statuses
     * and Query V2, which is likely too large for record-shaped results like annotations.
     */
    public static final int QUERY_RESULT_CAP = 5000;

    /**
     * Accumulated result of a paged query: the records retrieved, and whether accumulation stopped
     * at the cap with more records still available.
     *
     * Truncation is carried as data rather than left implicit because the views must say so.  The
     * bug this paging work fixes is not that results were incomplete -- it is that the views
     * reported a count as if it were a total, with nothing indicating anything was missing.  A
     * silent cap would reproduce exactly that bug at a higher threshold.
     */
    public static class PagedResult<T> {

        public final List<T> records;
        public final boolean truncated;

        public PagedResult(List<T> records, boolean truncated) {
            this.records = records;
            this.truncated = truncated;
        }

        /**
         * Formats a result count for a status message, naming truncation when it occurred so a
         * capped result is never presented as a complete total.
         */
        public String describeCount(String noun) {
            if (truncated) {
                return String.format("showing first %d %s(s), more available", records.size(), noun);
            }
            return String.format("found %d %s(s)", records.size(), noun);
        }
    }

    /**
     * Follows nextPageToken, accumulating records until the query is exhausted or the cap is
     * reached.
     *
     * Static and free of any service reference so it is unit-testable without a service ecosystem,
     * for the same reason as emptyToNull() and timestampFromInstant(): the page loop is the part
     * with the edge cases (a cap reached exactly on a page boundary, a server that returns a token
     * with an empty page, a page that overshoots the cap), and nothing requiring a live service
     * has test coverage in this project.
     *
     * @param fetchPage        invoked with a page token -- null for the first page -- returning
     *                         that page's records.  It must signal failure by throwing, NOT by
     *                         returning null: a null page cannot be distinguished from an empty
     *                         one here, so treating it as the end of the query would present a
     *                         partial accumulation as a complete result -- the exact bug the
     *                         QueryFailedException guarantee exists to prevent.  A null return is
     *                         therefore rejected rather than tolerated.
     * @param nextPageTokenOf  reads the nextPageToken from whatever fetchPage returned; an empty
     *                         or null token ends the query
     * @param recordsOf        reads the record list from whatever fetchPage returned
     * @param cap              maximum records to accumulate
     * @return the accumulated records, truncated to the cap, and whether more remained
     */
    static <R, T> PagedResult<T> accumulatePages(
            Function<String, R> fetchPage,
            Function<R, String> nextPageTokenOf,
            Function<R, List<T>> recordsOf,
            int cap
    ) {
        final List<T> accumulated = new ArrayList<>();
        String pageToken = null;

        while (true) {
            final R page = fetchPage.apply(pageToken);
            if (page == null) {
                // a caller that returns null instead of throwing would otherwise have its error
                // silently reported as a complete result; see the fetchPage contract above
                throw new QueryFailedException(
                        "Paged query failed - fetchPage returned null for page token: " + pageToken);
            }

            final List<T> pageRecords = recordsOf.apply(page);
            if (pageRecords != null) {
                for (T record : pageRecords) {
                    if (accumulated.size() >= cap) {
                        // the cap was reached mid-page, so records demonstrably remain
                        return new PagedResult<>(accumulated, true);
                    }
                    accumulated.add(record);
                }
            }

            pageToken = nextPageTokenOf.apply(page);
            if (pageToken == null || pageToken.isEmpty()) {
                // last page: complete regardless of how close to the cap we landed
                return new PagedResult<>(accumulated, false);
            }

            if (accumulated.size() >= cap) {
                // the cap was reached exactly on a page boundary and the server offers another
                // page, so more records remain even though this page fit
                return new PagedResult<>(accumulated, true);
            }
        }
    }

    // ------------------- Sample Status (demo support) ---------------------------

    /*
     * Sample status domain and layer used by the demo sample status generator.
     *
     * domain names the contract for interpreting status codes; layer names the producer stream
     * assigning them.  Together with PV name and timestamp these form the identity key of an
     * individual sample status: (pvName, timestamp, domain, layer).
     *
     * As with EnumColumn, the MLDP stores status codes without validating or interpreting them:
     * the (domain, code) mapping is a contract between producers and consumers.  The domain
     * registry that would document this mapping in the archive itself
     * (saveSampleStatusDomain() / querySampleStatusDomains()) is reserved in the proto but NOT
     * YET IMPLEMENTED server-side, so for now the mapping below is the only definition of it.
     */
    public static final String SAMPLE_STATUS_DEMO_DOMAIN = "epics_alarm";
    public static final String SAMPLE_STATUS_DEMO_LAYER = "demo_generator";
    public static final String SAMPLE_STATUS_DEMO_SOURCE = "dp-desktop-app demo sample status generator";

    // epics_alarm domain code mapping.
    public static final int EPICS_ALARM_NO_ALARM = 0;
    public static final int EPICS_ALARM_MINOR_ALARM = 1;
    public static final int EPICS_ALARM_MAJOR_ALARM = 2;
    public static final int EPICS_ALARM_INVALID_ALARM = 3;

    /*
     * Cumulative thresholds for the demo alarm distribution: ~85% NO_ALARM, ~10% MINOR_ALARM,
     * ~4% MAJOR_ALARM, ~1% INVALID_ALARM.  Weighted so generated data reads as plausible alarm
     * history rather than uniform noise.
     */
    private static final double ALARM_THRESHOLD_NO_ALARM = 0.85;
    private static final double ALARM_THRESHOLD_MINOR_ALARM = 0.95;
    private static final double ALARM_THRESHOLD_MAJOR_ALARM = 0.99;

    /**
     * Generates a list of random EPICS-style alarm status codes, one per sample.
     *
     * Codes are drawn independently with NO_ALARM dominating; see the threshold constants.  This
     * is demo data only and models no real alarm behavior (a real alarm history would be highly
     * autocorrelated rather than independent per sample).
     *
     * @throws IllegalArgumentException if sampleCount is negative.  Returning an empty list would
     *     surface a caller bug later as a count mismatch in buildSampleStatusFrame() instead of
     *     here, which is the very thing that guard exists to avoid.
     */
    static List<Integer> generateRandomAlarmStatusCodes(int sampleCount, Random random) {
        if (sampleCount < 0) {
            throw new IllegalArgumentException("negative sample count: " + sampleCount);
        }

        final List<Integer> statusCodes = new ArrayList<>(sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            final double draw = random.nextDouble();
            if (draw < ALARM_THRESHOLD_NO_ALARM) {
                statusCodes.add(EPICS_ALARM_NO_ALARM);
            } else if (draw < ALARM_THRESHOLD_MINOR_ALARM) {
                statusCodes.add(EPICS_ALARM_MINOR_ALARM);
            } else if (draw < ALARM_THRESHOLD_MAJOR_ALARM) {
                statusCodes.add(EPICS_ALARM_MAJOR_ALARM);
            } else {
                statusCodes.add(EPICS_ALARM_INVALID_ALARM);
            }
        }
        return statusCodes;
    }

    /*
     * Accumulates demo sample status results across every PV and bucket of one generation run.
     *
     * A status save failure is recorded here rather than aborting the run.  By the time a save is
     * attempted the bucket's data is already ingested, and sample status generation is an opt-in
     * demo extra -- failing the whole ingestion because the demo extra failed would strand data
     * that is in the archive but unreachable, since the state tracking that enables the Explore
     * menu is only set on the success path.  Only the first failure is kept; the rest are almost
     * always the same cause repeated once per bucket.
     */
    static final class SampleStatusAccumulator {

        private final Random random;
        private long savedCount = 0;
        private String firstError = null;

        SampleStatusAccumulator(Random random) {
            this.random = random;
        }

        Random random() {
            return random;
        }

        void recordSaved(long count) {
            savedCount += count;
        }

        void recordError(String message) {
            if (firstError == null) {
                firstError = message;
            }
        }

        long savedCount() {
            return savedCount;
        }

        boolean hasError() {
            return firstError != null;
        }

        String firstError() {
            return firstError;
        }
    }

    /*
     * Provenance for the modifiedBy field of a demo sample status save.
     *
     * The registered provider name is the most useful answer to "who did this", but providerName
     * is only assigned by registerProvider() and generateAndIngestData() guards on providerId
     * rather than on it.  Falling through with a null would make AnnotationClient omit the field
     * entirely, storing the statuses with no attribution at all and no indication that happened,
     * so an explicit fallback is used instead.
     */
    static String sampleStatusModifiedBy(String providerName) {
        return (providerName == null || providerName.isBlank())
                ? SAMPLE_STATUS_DEMO_SOURCE
                : providerName;
    }

    /**
     * Builds a dense SampleStatusFrame assigning one status code to every sample on a
     * SamplingClock time axis.
     *
     * The clock parameters are supplied by the caller rather than recomputed here, and that is
     * the entire point of this helper's signature.  Sample statuses attach to samples by exact
     * (pvName, timestamp) equality at nanosecond precision, so a status frame's clock must equal
     * the clock the data was ingested with EXACTLY -- an off-by-one-nanosecond period misses
     * every sample after the first.  Callers pass the same start/period/count values they used to
     * build the ingestion request, so the two clocks cannot drift apart.  Misalignment fails
     * silently: the save succeeds and the statuses are stored, but nothing matches at query time.
     *
     * confidence and reasons are deliberately left unset.  Each is all-or-nothing (empty, or
     * exactly one entry per timestamp) and neither adds anything to an alarm-value demo; an
     * all-empty reasons list is omitted entirely rather than sent as empty strings.
     *
     * @throws IllegalArgumentException if statusCodes does not contain exactly one code per
     *     timestamp, which the service would otherwise reject.
     */
    static SampleStatusFrame buildSampleStatusFrame(
            String pvName,
            String domain,
            String layer,
            long samplingClockStartSeconds,
            long samplingClockStartNanos,
            long samplingClockPeriodNanos,
            int samplingClockCount,
            List<Integer> statusCodes
    ) {
        if (statusCodes == null || statusCodes.size() != samplingClockCount) {
            throw new IllegalArgumentException(
                    "sample status code count " + (statusCodes == null ? "null" : statusCodes.size())
                            + " does not match sampling clock count " + samplingClockCount
                            + " for PV " + pvName);
        }

        final SamplingClock samplingClock = SamplingClock.newBuilder()
                .setStartTime(Timestamp.newBuilder()
                        .setEpochSeconds(samplingClockStartSeconds)
                        .setNanoseconds(samplingClockStartNanos)
                        .build())
                .setPeriodNanos(samplingClockPeriodNanos)
                .setCount(samplingClockCount)
                .build();

        final SampleStatusColumn statusColumn = SampleStatusColumn.newBuilder()
                .setPvName(pvName)
                .addAllStatusCodes(statusCodes)
                .build();

        return SampleStatusFrame.newBuilder()
                .setDomain(domain)
                .setLayer(layer)
                .setDataTimestamps(DataTimestamps.newBuilder()
                        .setSamplingClock(samplingClock)
                        .build())
                .addStatusColumns(statusColumn)
                .build();
    }

    /**
     * Builds the protobuf Calculations message from imported data frames, or returns null when
     * there are none so the request builder omits the calculations field.  Returning null for
     * the no-calculations case matches AnnotationClient.buildSaveAnnotationRequest(), which
     * null-checks the field; previously saveAnnotation() called build() on a builder that was
     * never created and threw NullPointerException for annotations without calculations.
     */
    static Calculations buildCalculations(List<DataFrameDetails> calculationsDataFrameDetails) {
        if (calculationsDataFrameDetails == null || calculationsDataFrameDetails.isEmpty()) {
            return null;
        }
        final Calculations.Builder calculationsBuilder = Calculations.newBuilder();
        for (DataFrameDetails dataFrameDetails : calculationsDataFrameDetails) {
            final TimestampList frameTimestampList =
                    TimestampList.newBuilder().addAllTimestamps(dataFrameDetails.getTimestamps()).build();
            final DataTimestamps frameDataTimestamps =
                    DataTimestamps.newBuilder().setTimestampList(frameTimestampList).build();
            final DataFrame frame =
                    DataFrame.newBuilder()
                            .setDataTimestamps(frameDataTimestamps)
                            .addAllDataColumns(dataFrameDetails.getDataColumns())
                            .build();
            final Calculations.CalculationsDataFrame calculationsDataFrame =
                    Calculations.CalculationsDataFrame.newBuilder()
                            .setName(dataFrameDetails.getName())
                            .setFrame(frame)
                            .build();
            calculationsBuilder.addCalculationDataFrames(calculationsDataFrame);
        }
        return calculationsBuilder.build();
    }

    // Getters for state variables (for use by other views)
    public String getProviderId() { return providerId; }
    public String getProviderName() { return providerName; }
    public Instant getDataBeginTime() { return dataBeginTime; }
    public Instant getDataEndTime() { return dataEndTime; }
    public List<String> getPvNames() { return pvNames; }

    // Getters for application state tracking (for home view)
    public boolean hasIngestedData() { return hasIngestedData; }

    /**
     * Whether the archive held data at launch.  See the field for why this is not
     * {@link #hasIngestedData()}.
     */
    public boolean archiveHasData() { return archiveHasData; }
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

    /**
     * Clears the session state that describes data in the archive, after the demo database has been
     * deleted.
     *
     * <p>Called only from the Tools &gt; Delete Demo Data action, and only after the drop has
     * actually succeeded.  The point is agreement between what the application claims and what the
     * archive holds: {@code hasIngestedData} drives the Explore menu and the home view, so leaving
     * it set after a delete would offer views onto an empty database and report counts for buckets
     * that no longer exist -- which reads as the query paths being broken rather than as the data
     * having been deleted on request.
     *
     * <p><b>Deliberately narrower than "reset everything".</b>  Three pieces of state are left
     * alone, each for its own reason:
     *
     * <ul>
     *   <li>{@code configuration} and the API client -- properties of how the application was
     *       launched, not of the session.  The connection is still live and still correct.</li>
     *   <li>{@code dataEventSubscriptions} -- each holds an open gRPC call.  Clearing the list would
     *       leak those calls rather than end them, and cancelling them here would tear down streams
     *       the user did not ask to stop.  They are left running, and they remain reachable through
     *       {@link #getDataEventSubscriptions()}: the Data Events menu item going disabled hides the
     *       view, it does not stop the subscriptions.  Ending them belongs to
     *       {@code cancelDataEventSubscription()}, which is the only path that closes the call
     *       rather than dropping the reference.</li>
     *   <li>{@code dataBeginTime} / {@code dataEndTime} -- a query window the user chose, which is
     *       still a perfectly good window to query once new data exists.</li>
     * </ul>
     */
    public void resetIngestedDataState() {

        hasIngestedData = false;
        hasPerformedQueries = false;
        totalPvsIngested = 0;
        totalBucketsCreated = 0;

        // The delete emptied the archive, so what the launch probe found is no longer true.  Left
        // set, this would keep every Explore item enabled over an archive that was just dropped --
        // the exact mirror of the bug the probe exists to fix, and the reason the reset must clear
        // it rather than only the session flag.
        archiveHasData = false;

        // The PV list describes PVs that were in the archive.  Note the convention this field
        // carries throughout DpApplication: empty means null, not an empty list -- setPvNames() and
        // removePvName() both collapse to null, and getPvNames() callers are written against that.
        pvNames = null;

        // Provider registration is state held in the database that was just dropped, so the id no
        // longer resolves.  Leaving it set would let a subsequent ingestion attempt reference a
        // provider that does not exist -- generateAndIngestData() guards on providerId being
        // non-null and would sail past that guard into a server rejection.
        providerId = null;
        providerName = null;

        lastOperationResult = null;

        logger.info("session state reset after demo database delete");
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

        configuration = AppConfiguration.fromConfiguration();
        logger.info("initializing application in mode: {}", configuration.describe());

        final boolean channelsReady = configuration.isDeployment()
                ? initRemoteChannels()
                : initInprocessEcosystem();

        if (!channelsReady) {
            return false;
        }

        if (!api.init()) {
            // Release whatever init built before returning.  DpDesktopApplication.init() throws on a
            // false return here, so fini() is never reached -- without this, a failed remote startup
            // leaves all four channels and their event-loop threads alive for the life of the
            // process, and a failed demo startup leaves the ecosystem running.
            logger.error("api client init failed; releasing partially initialized resources");
            fini();
            return false;
        }

        archiveHasData = probeArchiveForData();

        return true;
    }

    /**
     * How long {@link #probeArchiveForData()} waits before giving up and assuming the archive has
     * data.
     *
     * <p><b>Bounded because the probe runs before there is a window to look at.</b>
     * {@code DpApplication.init()} is called from {@code DpDesktopApplication.init()}, which JavaFX
     * runs before {@code start()} -- so anything slow here is a blank screen with no feedback, not a
     * slow view.  The underlying call cannot bound itself: {@code queryPvStats} goes through
     * dp-service's {@code ApiResponseObserverBase.await()}, whose timeout is 60 seconds, which is
     * a minute of nothing on a wedged query service.  The #4 manual verification hit exactly that
     * server state.
     *
     * <p>Five seconds is generous for a single aggregation against a local service and short enough
     * that a user does not conclude the application failed to start.  Timing out costs nothing,
     * because the fallback answer is the same one a failure gets.
     */
    private static final int ARCHIVE_PROBE_TIMEOUT_SECONDS = 5;

    /**
     * Asks the archive whether it already holds anything worth exploring.
     *
     * <p><b>Why this exists.</b>  Before #4 the demo database was dropped on every launch, so
     * "this session ingested" and "there is something to explore" were the same statement and one
     * flag answered both.  Demo data now survives a restart, and the Explore menu was left keyed on
     * the session flag -- so a demo launched on top of a previous session's data showed every
     * Explore item disabled with hundreds of buckets sitting in the database, unreachable from the
     * UI.
     *
     * <p><b>It asks over gRPC, never MongoDB.</b>  Counting documents directly would be cheaper and
     * is the obvious implementation, but it would construct a MongoDB client -- and deployment mode
     * never constructing one is a structural safety property of this release, not an incidental
     * detail.  {@code queryPvStats} is the same read the Explore views themselves perform, so a
     * probe that succeeds is real evidence those views will have something to show.
     *
     * <p><b>A failure means "assume there is data", not "assume there is none".</b>  The two
     * mistakes are not symmetric.  Guessing empty on an unreachable service hides an archive that
     * may be full, reproducing exactly the bug this method fixes and offering no way to reach the
     * data; guessing non-empty at worst opens views that report their own emptiness, which is a
     * far better failure than a menu that cannot be clicked.  Note this also keeps a slow or
     * briefly-unavailable service from silently disabling the application.
     */
    private boolean probeArchiveForData() {

        // Deployment mode never asks the question.  MainViewModel derives
        // exploreEnabled = deploymentMode || archiveHasData || hasIngestedData, so the answer is
        // unused there -- and paying for it means a remote launch waits on the query service
        // before showing a window.  Returning true rather than false keeps the flag consistent with
        // what the menu rule concludes anyway, so a future reader of archiveHasData() in deployment
        // mode is not told the archive is empty.
        if (isDeploymentMode()) {
            logger.info("skipping the archive probe: deployment mode enables the Explore views regardless");
            return true;
        }

        return probeArchiveWithin(() -> queryPvStats(".*"), ARCHIVE_PROBE_TIMEOUT_SECONDS);
    }

    /**
     * Runs an archive probe with a bounded wait, answering "the archive has data" for anything that
     * is not a clean, timely, empty result.
     *
     * <p>Package-private and static, taking the query as a supplier, so the <b>bound</b> is testable
     * without a service ecosystem -- the same reasoning that keeps {@code accumulatePages()} and
     * {@code archiveHasDataFrom()} static.  It is worth pinning separately from
     * {@code archiveHasDataFrom()} because it is a different failure: that method decides what a
     * returned result means, this one decides what happens when no result returns at all.
     *
     * <p><b>Every abnormal outcome answers the same way</b> -- timeout, interruption, and a thrown
     * exception all yield true, for the reason spelled out on {@code archiveHasDataFrom()}: the
     * wrong guess must be the recoverable one.  A timed-out probe is <b>abandoned rather than
     * waited on</b>; it runs on a daemon thread, so an answer that arrives late is simply discarded
     * and cannot hold up shutdown.
     */
    static boolean probeArchiveWithin(
            Supplier<QueryPvStatsApiResult> probeSupplier,
            int timeoutSeconds
    ) {

        final ExecutorService probeExecutor =
                Executors.newSingleThreadExecutor(runnable -> {
                    final Thread thread = new Thread(runnable, "archive-probe");
                    thread.setDaemon(true);
                    return thread;
                });

        try {
            final Future<QueryPvStatsApiResult> probe = probeExecutor.submit(probeSupplier::get);
            return archiveHasDataFrom(probe.get(timeoutSeconds, TimeUnit.SECONDS));

        } catch (TimeoutException e) {
            // The same answer a failed probe gets, for the same reason -- see archiveHasDataFrom().
            logger.warn(
                    "archive probe did not answer within {} seconds; assuming the archive has data "
                            + "so the Explore views stay reachable",
                    timeoutSeconds);
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("archive probe interrupted; assuming the archive has data");
            return true;

        } catch (Exception e) {
            logger.warn(
                    "archive probe threw ({}); assuming the archive has data so the Explore views "
                            + "stay reachable",
                    e.getMessage(), e);
            return true;

        } finally {
            // shutdownNow() rather than shutdown(): on the timeout path the task is still running
            // and its result is no longer wanted.  The thread is a daemon, so an uninterruptible
            // call blocked in gRPC cannot keep the JVM alive either way.
            probeExecutor.shutdownNow();
        }
    }

    /**
     * Reads a probe result into the archive-has-data answer.
     *
     * <p>Package-private and static so the failure policy is testable without a service ecosystem,
     * the same reasoning that keeps {@code accumulatePages()} and {@code emptyToNull()} static.  The
     * policy is the part worth pinning: it is a deliberate asymmetry that reads like a typo.
     *
     * <p><b>A failed probe answers "yes", not "no".</b>  Guessing empty on an unreachable or slow
     * service disables every Explore view over an archive that may be full -- the exact bug the
     * probe exists to fix, with no way for the user to reach the data or to know why.  Guessing
     * non-empty at worst opens views that report their own emptiness.  The wrong guess must be the
     * recoverable one.
     */
    static boolean archiveHasDataFrom(QueryPvStatsApiResult result) {

        if (result == null || result.isError()) {
            final String msg = result == null ? "null result" : result.resultStatus.msg;
            // Deliberately assuming data rather than none -- see the method javadoc.  Logged at
            // WARN with the consequence spelled out, because the Explore menu being enabled over an
            // archive nobody has confirmed is non-empty is a state worth being able to explain.
            logger.warn(
                    "archive probe failed, so the Explore menu is being ENABLED without confirming "
                            + "the archive holds anything (a failed probe must not hide data). "
                            + "Cause: {}",
                    msg);
            return true;
        }

        if (result.queryPvStatsResponse == null) {
            logger.warn("archive probe returned success with no response; assuming the archive has data");
            return true;
        }

        final int pvCount = result.queryPvStatsResponse.getStatsResult().getPvStatsCount();
        logger.info("archive probe found {} PV(s)", pvCount);
        return pvCount > 0;
    }

    /**
     * Demo mode: start the self-contained in-process service ecosystem and point the ApiClient at
     * its four channels.  Unchanged behavior from before issue #4, and the default.
     */
    private boolean initInprocessEcosystem() {

        inprocessServiceEcosystem = new InprocessServiceEcosystem();
        if (!inprocessServiceEcosystem.init()) {
            return false;
        }

        api = new ApiClient(
            inprocessServiceEcosystem.ingestionService.getIngestionChannel(),
            inprocessServiceEcosystem.queryService.getQueryChannel(),
            inprocessServiceEcosystem.annotationService.getChannel(),
            inprocessServiceEcosystem.ingestionStreamService.getChannel()
        );

        return true;
    }

    /**
     * Deployment mode: connect to four already-running remote services.
     *
     * <p>{@code inprocessServiceEcosystem} stays null on this path, and that is the safety
     * property rather than an incidental one.  The in-process ecosystem is the only thing in the
     * application that constructs a MongoDB client, so a deployment-mode launch cannot reach the
     * demo database -- nor the drop path that used to run at launch -- however the rest of the
     * application is later changed.  It is a structural guarantee, not a flag someone can get
     * wrong.
     *
     * <p>{@code ApiClient} takes four plain {@code ManagedChannel}s, so every API call site is
     * already transport-agnostic and none of them differ between the two modes.
     */
    private boolean initRemoteChannels() {

        // Accumulated as they are built, rather than assigned once from a four-argument List.of(),
        // so that a failure on the third connect string still leaves the first two reachable for
        // shutdown below instead of leaking them.
        final List<ManagedChannel> channels = new ArrayList<>();

        try {
            channels.add(RemoteChannelFactory.createChannel(configuration.getIngestionConnectString()));
            channels.add(RemoteChannelFactory.createChannel(configuration.getQueryConnectString()));
            channels.add(RemoteChannelFactory.createChannel(configuration.getAnnotationConnectString()));
            channels.add(RemoteChannelFactory.createChannel(configuration.getIngestionStreamConnectString()));
        } catch (Exception e) {
            // A malformed connect string fails here rather than at first use, where it would
            // surface as an unexplained query failure well after launch.
            logger.error("failed creating remote grpc channels: {}", e.getMessage(), e);
            for (ManagedChannel channel : channels) {
                RemoteChannelFactory.shutdown(channel, "partially created remote service channel");
            }
            return false;
        }

        remoteChannels = channels;

        api = new ApiClient(
                remoteChannels.get(0),
                remoteChannels.get(1),
                remoteChannels.get(2),
                remoteChannels.get(3)
        );

        return true;
    }

    public boolean fini() {

        if (api != null) {
            api.fini();
        }

        if (inprocessServiceEcosystem != null) {
            inprocessServiceEcosystem.fini();
        }

        for (ManagedChannel channel : remoteChannels) {
            RemoteChannelFactory.shutdown(channel, "remote service channel");
        }
        remoteChannels = List.of();

        return true;
    }

    /**
     * The mode and targets this application was launched with.
     *
     * <p>Non-null only after {@link #init()}.  Callers that run before init -- there are none
     * today, since {@code DpDesktopApplication.init()} runs first -- would see null.
     */
    public AppConfiguration getConfiguration() {
        return configuration;
    }

    /** True when running against remote services; see the mode matrix in plan/tickets/4/plan.md. */
    public boolean isDeploymentMode() {
        return configuration != null && configuration.isDeployment();
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
            ColumnMetadata columnMetadata,
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

                // columnMetadata is null when the user entered none.  Only call the setter when it
                // is non-null: params defaults to no metadata, and IngestionRequestParams documents
                // that null is ambiguous between the two setColumnMetadata() overloads.
                final IngestionClient.IngestionRequestParams params = new IngestionClient.IngestionRequestParams(
                        this.providerId,                   // providerId
                        requestId
                );
                if (columnMetadata != null) {
                    params.setColumnMetadata(columnMetadata);
                }

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

    /**
     * Generates random walk data for each PV and ingests it, one request per bucket.
     *
     * When generateSampleStatuses is true, a random EPICS-style alarm status is also generated
     * for every sample of every PV and saved via the Sample Status API, aligned on exactly the
     * timestamps the data was ingested with.  This is demonstration data only; see
     * SAMPLE_STATUS_DEMO_DOMAIN for the code mapping.
     */
    public ResultStatus generateAndIngestData(
            Instant beginTime,
            Instant endTime,
            ColumnMetadata columnMetadata,
            List<PvDetail> pvDetails,
            int bucketSizeSeconds,
            List<SubscribeDataEventDetail> subscriptionDetails,
            boolean generateSampleStatuses
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

            // accumulates sample status counts and any save failure across PVs and buckets
            final SampleStatusAccumulator sampleStatusAccumulator =
                    new SampleStatusAccumulator(new Random());

            // Generate and ingest data for each PV
            for (PvDetail pvDetail : pvDetails) {
                ResultStatus result = generateAndIngestPvData(
                        pvDetail, beginTime, endTime, columnMetadata, bucketSizeSeconds,
                        generateSampleStatuses, sampleStatusAccumulator);
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
            if (generateSampleStatuses) {
                /*
                 * "upserted" rather than "saved": the save is keyed on
                 * (pvName, timestamp, domain, layer) and fully replaces an existing status, so
                 * re-generating over the same PVs and time range reports the same count while
                 * replacing rather than adding.  A sample status save failure is reported here
                 * alongside the successful ingestion rather than as an overall error, since the
                 * data itself is in the archive either way.
                 */
                successMessage = successMessage
                        + ", and upserted " + sampleStatusAccumulator.savedCount()
                        + " sample status(es)";
                if (sampleStatusAccumulator.hasError()) {
                    successMessage = successMessage
                            + " (some sample status saves failed: "
                            + sampleStatusAccumulator.firstError() + ")";
                }
            }
            this.lastOperationResult = successMessage;
            
            return new ResultStatus(false, successMessage);
            
        } catch (Exception e) {
            return new ResultStatus(true, "Error during data generation: " + e.getMessage());
        }
    }
    
    /*
     * Generates and ingests data for a single PV, one ingestion request per bucket.
     *
     * When generateSampleStatuses is true, a demo sample status frame is saved for each bucket
     * immediately after that bucket's data is ingested, and sampleStatusAccumulator collects the
     * counts and any save failure.  See the sample status save below for why the frame is built
     * here rather than batched, and why a save failure does not fail this method.
     */
    private ResultStatus generateAndIngestPvData(
            PvDetail pvDetail, Instant beginTime, Instant endTime,
            ColumnMetadata columnMetadata, int bucketSizeSeconds,
            boolean generateSampleStatuses, SampleStatusAccumulator sampleStatusAccumulator
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
                // See note in ingestImportedData(): only set metadata when the user supplied some.
                if (columnMetadata != null) {
                    params.setColumnMetadata(columnMetadata);
                }
                
                // Call ingestData() API method for this bucket
                final IngestDataApiResult apiResult = api.ingestionClient.ingestData(params, null, null);
                requestCount++;

                if (apiResult.resultStatus.isError) {
                    return apiResult.resultStatus;
                }

                /*
                 * Save demo sample statuses for the samples just ingested.
                 *
                 * This is done here, per bucket, rather than by accumulating frames and saving
                 * once at the end, because a sample status attaches to a sample only by exact
                 * (pvName, timestamp) equality at nanosecond precision.  Building the frame here
                 * lets it reuse the very same sampling clock values used to build the ingestion
                 * request above, so the two clocks cannot drift apart.  Reconstructing the clock
                 * later would reintroduce that risk, and misalignment fails silently: the save
                 * succeeds and the statuses are stored, but nothing matches at query time.
                 *
                 * Saving per bucket also keeps each request naturally bounded, since the service
                 * may enforce a configured batch size limit and reject oversized requests.
                 */
                if (generateSampleStatuses) {
                    final List<Integer> statusCodes = generateRandomAlarmStatusCodes(
                            samplingClockCount, sampleStatusAccumulator.random());

                    final SampleStatusFrame statusFrame = buildSampleStatusFrame(
                            pvDetail.getPvName(),
                            SAMPLE_STATUS_DEMO_DOMAIN,
                            SAMPLE_STATUS_DEMO_LAYER,
                            samplingClockStartSeconds,
                            samplingClockStartNanos,
                            samplingClockPeriodNanos,
                            samplingClockCount,
                            statusCodes);

                    final SaveSampleStatusesApiResult statusResult = saveSampleStatuses(
                            List.of(statusFrame),
                            SAMPLE_STATUS_DEMO_SOURCE,
                            sampleStatusModifiedBy(providerName));

                    /*
                     * A failed status save is recorded and generation continues, rather than
                     * returning an error.  The bucket's data was ingested successfully just
                     * above and is in the archive; aborting here would skip the state tracking
                     * that enables the Explore menu, leaving that data present but unreachable
                     * from the UI -- a worse outcome than a demo extra silently coming up short,
                     * which the success message reports.
                     */
                    if (statusResult.resultStatus.isError) {
                        logger.warn("error saving sample statuses for PV {}: {}",
                                pvDetail.getPvName(), statusResult.resultStatus.msg);
                        sampleStatusAccumulator.recordError(
                                "PV " + pvDetail.getPvName() + ": "
                                        + statusResult.resultStatus.msg);
                    } else {
                        sampleStatusAccumulator.recordSaved(statusResult.savedCount);
                    }
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

    public QueryPvStatsApiResult queryPvStats(List<String> pvNameList) {
        return api.queryClient.queryPvStats(pvNameList);
    }

    public QueryPvStatsApiResult queryPvStats(String pvNamePattern) {
        return api.queryClient.queryPvStats(pvNamePattern);
    }

    /**
     * Queries aligned time-series samples via the Query API V2, returning ONE page.
     *
     * <p><strong>This wrapper is deliberately single-page, unlike the other paged wrappers on this
     * class.</strong>  queryDataSets(), queryPvMetadata() and their siblings follow nextPageToken
     * internally via accumulatePages() because their callers want one complete list.  The data
     * explore view instead displays each page as it arrives -- that incremental display is the
     * behavior the retired 1-minute interval loop provided, and accumulating here would withhold
     * every row until the last page landed.  The caller therefore drives the loop and owns the
     * token; see DataExploreViewModel.executeSamplesQuery().
     *
     * <p><strong>Do not carry a page token across queries.</strong>  A token encodes a position
     * only; nothing binds it to the QuerySpec that produced it beyond a coarse check separating
     * bucket tokens from sample tokens.  Replaying one against an edited time range or PV list
     * yields a well-formed but semantically WRONG result rather than an error.  Pass null to start.
     *
     * <p><strong>limit is deliberately left unset.</strong>  The server does not limit the Mongo
     * cursor -- it drains buckets until its outgoing byte budget trips and then truncates the
     * assembled table -- so a small limit causes repeated near-full re-scans without reducing
     * server work.  Unset selects the server default (10,000 rows); an over-maximum value would be
     * silently clamped rather than rejected.  None of those numbers are hardcoded here because all
     * of them are environment-overridable server-side.
     *
     * <p>A page is bounded by whichever of the row limit and the byte budget trips first, and the
     * byte accounting measures sample values only -- not the timestamp list, column framing, names
     * or the response envelope -- so a page can overshoot the budget by up to one bucket.
     *
     * <p><strong>Two server-side failures are not retryable and must be surfaced verbatim.</strong>
     * A single timestamp whose values across all selected PVs exceed the byte budget is a hard
     * error; narrowing the time range cannot help, since the offending row is one instant, and only
     * a smaller PV set can.  And a non-scalar PV is rejected MID-ASSEMBLY rather than pre-flight
     * (dp-service #194 is still open), so a non-scalar PV with no buckets in the window passes
     * silently and the same PV set can succeed on one page and reject on the next -- after rows are
     * already on screen.
     *
     * <p><strong>A metadata selector that resolves too broadly is a third such failure.</strong>
     * The server caps a resolved selector at maxResolvedPvCount and rejects past it with "narrow
     * the selector".  Unlike the other two this one is reachable from an ordinary-looking UI
     * choice, because an all-empty metadata query is NOT rejected -- it matches every PV in the
     * archive.  See PvSelection, which describes that case explicitly rather than letting it read
     * as a filter.
     *
     * <p>A configuration selector that matches no activations is NOT one of those failures.  It is a
     * well-formed query returning an empty result, deliberately distinguished server-side from a
     * malformed selector, which rejects -- so a mis-built selector is never indistinguishable from
     * "no data in this window".
     *
     * <p><strong>The two optional filters narrow different axes and compose by intersection.</strong>
     * configurationCriteria restricts the TIME axis to the intervals during which matching machine
     * configurations were active; sampleStatusSelector then drops individual samples from what
     * survives.  A status attached to a sample outside the activation intervals has no effect,
     * because that sample is already gone.
     *
     * <p><strong>configurationCriteria must be null for "no restriction", never an empty list.</strong>
     * The two are read differently by the request builder: null or empty means no restriction was
     * asked for and the selector is dropped, but a non-EMPTY list from which no criterion survives
     * emits the empty selector, which the server rejects with "configurationSelector.criteria list
     * must not be empty".  That asymmetry is deliberate upstream -- dropping a criterion the caller
     * filled in would WIDEN the query from "only while configuration X was active" to the whole time
     * range, handing back more data than they asked for with no diagnostic.  ConfigurationFilter
     * returns null rather than an empty list for exactly this reason.
     *
     * <p><strong>sampleStatusSelector is accepted here but rejected on the bucket methods</strong>,
     * which return buckets whole and cannot represent per-sample filtering.  It is unreachable from
     * this app, which queries samples only, but it is why the client's QueryBucketsParams omits the
     * field rather than carrying one the server would refuse.  A filtered-out sample becomes a
     * MISSING VALUE at its (PV, timestamp) position, not a dropped row; a timestamp disappears only
     * when every selected PV is filtered out at it.
     *
     * @param pvSelector which PVs to cover; the server rejects an unset selector, an empty name
     *                   list and a blank pattern
     * @param configurationCriteria optional activation-interval restriction, or <strong>null</strong>
     *                              for none -- never an empty list, per the note above
     * @param sampleStatusSelector optional per-sample status filter, or null for none; the server
     *                             requires a non-blank domain and a specified mode
     * @param beginTime start of the half-open interval [beginTime, endTime)
     * @param endTime  end of that interval
     * @param pageToken a prior result's nextPageToken to continue, or null to start
     */
    public QuerySamplesApiResult querySamples(
            QueryClient.PvSelectorParams pvSelector,
            List<QueryClient.ConfigurationCriterion> configurationCriteria,
            QueryClient.SampleStatusSelectorParams sampleStatusSelector,
            Instant beginTime,
            Instant endTime,
            String pageToken
    ) {
        final QueryClient.QuerySamplesParams params = new QueryClient.QuerySamplesParams(
                new QueryClient.QuerySpecParams(
                        timestampFromInstant(beginTime),
                        timestampFromInstant(endTime),
                        pvSelector,
                        configurationCriteria),
                sampleStatusSelector,
                0,     // limit: server default, per the javadoc above
                pageToken,
                // useSerializedColumns stays off.  Serialized columns cannot be merged across
                // pages, so enabling it would require checking serializedColumnsFragmented before
                // reading the table; an unchecked consumer gets silently misaligned columns rather
                // than an error.
                false);

        return api.queryClient.querySamples(params);
    }

    public QueryProvidersApiResult queryProviders(
            String idCriterion,
            String textCriterion, // search name and description fields
            String tagsCriterion,
            String attributeKeyCriterion,
            String attributeValueCriterion
    ) {
        // create params, omitting criteria left blank in the UI
        QueryClient.QueryProvidersRequestParams params = new QueryClient.QueryProvidersRequestParams();
        setIfPresent(idCriterion, params::setIdCriterion);
        setIfPresent(textCriterion, params::setTextCriterion);
        setIfPresent(tagsCriterion, params::setTagsCriterion);
        setIfBothPresent(attributeKeyCriterion, attributeValueCriterion, params::setAttributesCriterion);

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

        // create API dataset containing datablocks, normalizing a blank id to null
        final AnnotationClient.AnnotationDataSet annotationDataSet =
                new AnnotationClient.AnnotationDataSet(
                        emptyToNull(id),
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

    /**
     * Queries datasets, following nextPageToken internally so the caller receives one complete
     * list up to QUERY_RESULT_CAP.  See accumulatePages() for why paging is transparent here and
     * why the result carries a truncation flag.
     *
     * @throws QueryFailedException if any page request fails, so a partial accumulation is never
     *                              returned as if it were the whole result
     */
    public PagedResult<DataSet> queryDataSets(
            String idCriterion,
            String ownerCriterion,
            String textCriterion, // search name and description fields
            String pvNameCriterion
    ) throws QueryFailedException {
        // create params, omitting criteria left blank in the UI
        AnnotationClient.QueryDataSetsParams params = new AnnotationClient.QueryDataSetsParams();
        setIfPresent(idCriterion, params::setIdCriterion);
        setIfPresent(ownerCriterion, params::setOwnerCriterion);
        setIfPresent(textCriterion, params::setTextCriterion);
        setIfPresent(pvNameCriterion, params::setPvNameCriterion);

        return accumulatePages(
                pageToken -> {
                    params.setPageToken(pageToken);
                    final QueryDataSetsApiResult pageResult = api.annotationClient.queryDataSets(params);
                    if (pageResult == null) {
                        throw new QueryFailedException("Dataset query failed - null response from service");
                    }
                    if (pageResult.resultStatus.isError) {
                        throw new QueryFailedException("Dataset query failed: " + pageResult.resultStatus.msg);
                    }
                    return pageResult;
                },
                pageResult -> pageResult.nextPageToken,
                pageResult -> pageResult.dataSets,
                QUERY_RESULT_CAP);
    }

    public SaveAnnotationApiResult saveAnnotation(
            String id,
            String name,
            List<String> dataSetIds,
            List<String> annotationIds,
            String description,
            List<String> tags,
            Map<String, String> attributeMap,
            List<DataFrameDetails> calculationsDataFrameDetails
    ) {
        // create API calculations (null when none were imported, so the field is omitted)
        final Calculations calculations = buildCalculations(calculationsDataFrameDetails);

        // create API request params
        final AnnotationClient.SaveAnnotationRequestParams params =
                new AnnotationClient.SaveAnnotationRequestParams(
                        id,
                        "demo-user",
                        name,
                        dataSetIds,
                        annotationIds,
                        description,
                        tags,
                        attributeMap,
                        calculations
                );

        // call api method
        return api.annotationClient.saveAnnotation(params);
    }

    /**
     * Queries annotations, following nextPageToken internally so the caller receives one complete
     * list up to QUERY_RESULT_CAP.  See accumulatePages() for why paging is transparent here and
     * why the result carries a truncation flag.
     *
     * The returned Annotations carry calculationsId but NOT Calculations content -- queryAnnotations
     * deliberately does not denormalize it as of dp-grpc #132.  Use getAnnotation() or
     * getCalculations() to retrieve it.
     *
     * @throws QueryFailedException if any page request fails, so a partial accumulation is never
     *                              returned as if it were the whole result
     */
    public PagedResult<Annotation> queryAnnotations(
            String idCriterion,
            String ownerCriterion,
            String dataSetsCriterion,
            String annotationsCriterion,
            String textCriterion, // search name and description fields
            String tagsCriterion,
            String attributeKeyCriterion,
            String attributeValueCriterion

    ) throws QueryFailedException {
        // create params, omitting criteria left blank in the UI
        AnnotationClient.QueryAnnotationsParams params = new AnnotationClient.QueryAnnotationsParams();
        setIfPresent(idCriterion, params::setIdCriterion);
        setIfPresent(ownerCriterion, params::setOwnerCriterion);
        setIfPresent(dataSetsCriterion, params::setDatasetsCriterion);
        setIfPresent(annotationsCriterion, params::setAnnotationsCriterion);
        setIfPresent(textCriterion, params::setTextCriterion);
        setIfPresent(tagsCriterion, params::setTagsCriterion);
        setIfBothPresent(attributeKeyCriterion, attributeValueCriterion, params::setAttributesCriterion);

        return accumulatePages(
                pageToken -> {
                    params.setPageToken(pageToken);
                    final QueryAnnotationsApiResult pageResult = api.annotationClient.queryAnnotations(params);
                    if (pageResult == null) {
                        throw new QueryFailedException("Annotation query failed - null response from service");
                    }
                    if (pageResult.resultStatus.isError) {
                        throw new QueryFailedException("Annotation query failed: " + pageResult.resultStatus.msg);
                    }
                    return pageResult;
                },
                pageResult -> pageResult.nextPageToken,
                pageResult -> pageResult.annotations,
                QUERY_RESULT_CAP);
    }

    /**
     * Retrieves a single DataSet by id.
     *
     * The right RPC for a one-record lookup, in place of emulating one with
     * queryDataSets(id, null, null, null) plus .get(0): the dedicated getter makes structurally
     * true what that pattern could only assume.
     *
     * A missing record is reported as a rejection rather than an empty result, so callers
     * distinguishing "not found" from "service unreachable" must branch on
     * ApiResultBase.isReject() rather than isError().
     */
    public GetDataSetApiResult getDataSet(String dataSetId) {
        return api.annotationClient.getDataSet(dataSetId);
    }

    /**
     * Retrieves a single Annotation by id, with its Calculations content populated inline.
     *
     * This is the ONLY method that returns calculations content within an Annotation --
     * queryAnnotations() returns calculationsId alone as of dp-grpc #132.  Loading an annotation
     * for editing must therefore go through here rather than through queryAnnotations(): because
     * saveAnnotation() is a full-replace upsert, re-saving an annotation that was loaded without
     * its calculations DESTROYS the stored Calculations, with no error and no warning.
     *
     * A missing record is reported as a rejection rather than an empty result, so callers
     * distinguishing "not found" from "service unreachable" must branch on
     * ApiResultBase.isReject() rather than isError().
     */
    public GetAnnotationApiResult getAnnotation(String annotationId) {
        return api.annotationClient.getAnnotation(annotationId);
    }

    /**
     * Retrieves a single Calculations object by id, without loading the owning Annotation.
     *
     * Used to resolve calculations content on demand for annotations obtained from
     * queryAnnotations(), which returns calculationsId without content.  Fetching per user action
     * rather than per row is the point: fetching per row would rebuild client-side, as serial
     * round trips, the N+1 fan-out that dp-grpc #132 removed.
     *
     * A missing record is reported as a rejection rather than an empty result -- see
     * getAnnotation().
     */
    public GetCalculationsApiResult getCalculations(String calculationsId) {
        return api.annotationClient.getCalculations(calculationsId);
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

    /**
     * Creates or updates the PV metadata record for the specified canonical PV name.
     *
     * This is a full-replace upsert: aliases, tags, attributes, description and modifiedBy are all
     * replaced by the values supplied here on every save, and fields omitted are not preserved from
     * an existing record.  Callers updating an existing record must supply the complete desired
     * state rather than only the fields being changed.
     *
     * Optional fields are converted from empty to null so that the client request builder omits
     * them, following the convention used by the other wrappers in this class.  Note that pvName,
     * description and modifiedBy are plain proto3 strings with no field presence, so the server
     * cannot distinguish an unset field from an empty one; do not build behavior that depends on
     * telling the two apart.
     *
     * Server-side rejections (blank pvName, a pvName already registered as another record's alias,
     * or an alias already in use) are returned via resultStatus.isError and resultStatus.msg rather
     * than thrown.
     */
    public SavePvMetadataApiResult savePvMetadata(
            String pvName,
            List<String> aliases,
            List<String> tags,
            Map<String, String> attributeMap,
            String description,
            String modifiedBy
    ) {
        // create params for api call, omitting optional fields that were not supplied
        final AnnotationClient.SavePvMetadataParams params =
                new AnnotationClient.SavePvMetadataParams(
                        pvName,
                        emptyToNull(aliases),
                        emptyToNull(tags),
                        emptyToNull(attributeMap),
                        emptyToNull(description),
                        emptyToNull(modifiedBy)
                );

        // call api method
        return api.annotationClient.savePvMetadata(params);
    }

    /**
     * PV metadata search that follows nextPageToken internally, returning every matching record up
     * to QUERY_RESULT_CAP.  The explore view needs no paging UI, matching queryDataSets() /
     * queryAnnotations().
     *
     * Criteria combine with AND; values within one criterion combine with OR.  The criteria are
     * pvName, aliases, tags and attributes -- there is deliberately no free-text parameter, because
     * this API has no TextCriterion.  A search box bound to one would have nothing to send.
     *
     * TextMatch fields are passed through UNPROCESSED.  The request builder drops blank entries
     * itself, and that guard is the point: a blank prefix compiles to a regex matching EVERYTHING,
     * so pre-filling or padding a field here would silently turn an unset filter into a whole
     * collection scan.
     *
     * A failed page throws QueryFailedException rather than returning what had accumulated, as with
     * the other paged wrappers.
     */
    public PagedResult<PvMetadata> queryPvMetadata(
            TextMatch pvNameMatch,
            TextMatch aliasesMatch,
            List<String> tagsAnyOf,
            List<AttributeCriterion> attributes
    ) {
        return accumulatePages(
                pageToken -> {
                    final AnnotationClient.QueryPvMetadataParams params =
                            new AnnotationClient.QueryPvMetadataParams(
                                    pvNameMatch,
                                    aliasesMatch,
                                    emptyToNull(tagsAnyOf),
                                    emptyToNull(attributes),
                                    0,
                                    emptyToNull(pageToken));

                    final QueryPvMetadataApiResult pageResult =
                            api.annotationClient.queryPvMetadata(params);

                    if (pageResult == null) {
                        throw new QueryFailedException(
                                "PV metadata query failed - null response from service");
                    }
                    if (pageResult.resultStatus.isError) {
                        throw new QueryFailedException(
                                "PV metadata query failed: " + pageResult.resultStatus.msg);
                    }
                    return pageResult;
                },
                pageResult -> pageResult.nextPageToken,
                pageResult -> pageResult.pvMetadata,
                QUERY_RESULT_CAP);
    }

    /**
     * Retrieves one PV metadata record by canonical name OR alias.
     *
     * <strong>The returned record's pvName may differ from what was passed.</strong>  The server
     * resolves aliases, so looking up a historical name returns the record under its CANONICAL name.
     * This matters because savePvMetadata() is a full-replace upsert keyed on pvName: a caller that
     * loads by alias, edits, and then saves using the name the user typed would write a NEW record
     * under the alias rather than updating the one it loaded.  Always save using the pvName carried
     * by the record this returns.
     *
     * A missing record is reported as a REJECTION, not an empty successful result, so an existence
     * check must branch on isReject() rather than isError() -- an unreachable service also sets
     * isError.  REJECT also covers request validation failures, so reading it as not-found is only
     * safe for a request already known to be well formed.
     */
    public GetPvMetadataApiResult getPvMetadata(String pvNameOrAlias) {
        return api.annotationClient.getPvMetadata(pvNameOrAlias);
    }

    /**
     * Queries machine configuration records, following nextPageToken internally.
     *
     * Criteria are ANDed; values within one criterion are ORed.  Every parameter is optional, and
     * an unset one contributes no criterion at all - a query with no criteria matches everything,
     * bounded by QUERY_RESULT_CAP.
     *
     * As with queryPvMetadata(), the TextMatch is passed through UNPROCESSED: the request builder
     * drops blank entries itself, and a blank prefix would otherwise compile to a regex matching
     * everything.
     *
     * A failed page throws QueryFailedException rather than returning what had accumulated.
     */
    public PagedResult<Configuration> queryConfigurations(
            TextMatch nameMatch,
            List<String> categoryAnyOf,
            List<String> tagsAnyOf,
            List<AttributeCriterion> attributes,
            List<String> parentAnyOf
    ) {
        return accumulatePages(
                pageToken -> {
                    final AnnotationClient.QueryConfigurationsParams params =
                            new AnnotationClient.QueryConfigurationsParams(
                                    nameMatch,
                                    emptyToNull(categoryAnyOf),
                                    emptyToNull(tagsAnyOf),
                                    emptyToNull(attributes),
                                    emptyToNull(parentAnyOf),
                                    0,
                                    emptyToNull(pageToken));

                    final QueryConfigurationsApiResult pageResult =
                            api.annotationClient.queryConfigurations(params);

                    if (pageResult == null) {
                        throw new QueryFailedException(
                                "configuration query failed - null response from service");
                    }
                    if (pageResult.resultStatus.isError) {
                        throw new QueryFailedException(
                                "configuration query failed: " + pageResult.resultStatus.msg);
                    }
                    return pageResult;
                },
                pageResult -> pageResult.nextPageToken,
                pageResult -> pageResult.configurations,
                QUERY_RESULT_CAP);
    }

    /**
     * Queries configuration activation records, following nextPageToken internally.
     *
     * Takes Instant at this boundary and converts inward via timestampFromInstant(), which maps null
     * to null - the activation params take protobuf Timestamp, and an optional time left unset must
     * reach the request builder as null rather than as a zero-valued Timestamp.
     *
     * <strong>rangeStart and rangeEnd are all-or-nothing.</strong>  TimeRangeCriterion requires both
     * bounds, and the request builder emits NO criterion when only one is supplied - it does not
     * reject the request.  A half-filled range is therefore silently broader than the user asked
     * for, which is why callers must validate the pair before calling rather than relying on the
     * server to complain.  activeAt is independent and may be combined with a range.
     *
     * Note the server's zero-timestamp idiom: a Timestamp of exactly epoch 0 is treated as
     * unspecified, so a query at Unix epoch 0 cannot be expressed.  This is not reachable through
     * the UI, whose date pickers cannot produce it, but it is why an Instant.EPOCH sentinel must
     * never be used here to mean "unset".
     *
     * A failed page throws QueryFailedException rather than returning what had accumulated.
     */
    public PagedResult<ConfigurationActivation> queryConfigurationActivations(
            Instant activeAt,
            Instant rangeStart,
            Instant rangeEnd,
            List<String> configurationNameAnyOf,
            List<String> clientActivationIdAnyOf,
            List<String> categoryAnyOf,
            List<String> tagsAnyOf,
            List<AttributeCriterion> attributes
    ) {
        return accumulatePages(
                pageToken -> {
                    final AnnotationClient.QueryConfigurationActivationsParams params =
                            new AnnotationClient.QueryConfigurationActivationsParams(
                                    timestampFromInstant(activeAt),
                                    timestampFromInstant(rangeStart),
                                    timestampFromInstant(rangeEnd),
                                    emptyToNull(configurationNameAnyOf),
                                    emptyToNull(clientActivationIdAnyOf),
                                    emptyToNull(categoryAnyOf),
                                    emptyToNull(tagsAnyOf),
                                    emptyToNull(attributes),
                                    0,
                                    emptyToNull(pageToken));

                    final QueryConfigurationActivationsApiResult pageResult =
                            api.annotationClient.queryConfigurationActivations(params);

                    if (pageResult == null) {
                        throw new QueryFailedException(
                                "configuration activation query failed - null response from service");
                    }
                    if (pageResult.resultStatus.isError) {
                        throw new QueryFailedException(
                                "configuration activation query failed: " + pageResult.resultStatus.msg);
                    }
                    return pageResult;
                },
                pageResult -> pageResult.nextPageToken,
                pageResult -> pageResult.configurationActivations,
                QUERY_RESULT_CAP);
    }

    /**
     * Creates or updates the machine configuration record for the specified configuration name.
     *
     * This is a full-replace upsert: every mutable field is replaced by the value supplied here on
     * each save, and a field left blank is not preserved from an existing record.  Callers must
     * therefore supply the complete desired state of the record.
     *
     * configurationName and category are required by the server; the remaining fields are optional
     * and are omitted from the request when blank.  Server-side rejections - a blank name or
     * category, or a category change while activations exist for the configuration - are returned
     * via resultStatus rather than thrown.
     */
    public SaveConfigurationApiResult saveConfiguration(
            String configurationName,
            String category,
            String description,
            String parentConfigurationName,
            List<String> tags,
            Map<String, String> attributeMap,
            String modifiedBy
    ) {
        // create params for api call, omitting optional fields that were not supplied
        final AnnotationClient.SaveConfigurationParams params =
                new AnnotationClient.SaveConfigurationParams(
                        configurationName,
                        category,
                        emptyToNull(description),
                        emptyToNull(parentConfigurationName),
                        emptyToNull(tags),
                        emptyToNull(attributeMap),
                        emptyToNull(modifiedBy)
                );

        // call api method
        return api.annotationClient.saveConfiguration(params);
    }

    /**
     * Creates or updates an activation recording the time interval during which a configuration
     * was active.
     *
     * This is a full-replace upsert keyed by clientActivationId, with the same
     * supply-the-complete-state requirement as saveConfiguration().
     *
     * configurationName and startTime are required by the server, and configurationName must
     * resolve to an existing Configuration.  A blank clientActivationId is omitted, in which case
     * the server generates an identifier and returns it in the result - that value is the caller's
     * only handle on the new record.  A null endTime produces an open-ended activation.
     *
     * Server-side rejections are returned via resultStatus rather than thrown.  These include an
     * endTime not after startTime, a configurationName that does not resolve, and an activation
     * overlapping an existing one.  Note that the overlap check rejects on a matching
     * configuration name OR a matching category, so two different configurations sharing a
     * category cannot have overlapping activations.
     */
    public SaveConfigurationActivationApiResult saveConfigurationActivation(
            String clientActivationId,
            String configurationName,
            Instant startTime,
            Instant endTime,
            String description,
            List<String> tags,
            Map<String, String> attributeMap,
            String modifiedBy
    ) {
        // create params for api call, omitting optional fields that were not supplied.  endTime is
        // converted through timestampFromInstant() so that a null stays null rather than becoming a
        // zero-valued Timestamp, which would describe an activation ending at the epoch instead of
        // an open-ended one.
        final AnnotationClient.SaveConfigurationActivationParams params =
                new AnnotationClient.SaveConfigurationActivationParams(
                        emptyToNull(clientActivationId),
                        configurationName,
                        timestampFromInstant(startTime),
                        timestampFromInstant(endTime),
                        emptyToNull(description),
                        emptyToNull(tags),
                        emptyToNull(attributeMap),
                        emptyToNull(modifiedBy)
                );

        // call api method
        return api.annotationClient.saveConfigurationActivation(params);
    }

    /**
     * Retrieves the machine configuration record for the specified configuration name.
     *
     * Note that a missing record is NOT reported as an empty successful result: the server rejects
     * the request, so a name that does not exist comes back with resultStatus.isError true and
     * apiResultStatus REJECT.  Callers using this as an existence check should branch on
     * isReject() rather than on isError(), so that a service failure is not mistaken for a record
     * that does not exist.
     */
    public GetConfigurationApiResult getConfiguration(String configurationName) {
        return api.annotationClient.getConfiguration(configurationName);
    }

    /**
     * Retrieves the configuration activation record identified by the client-supplied activation
     * id.
     *
     * As with getConfiguration(), a missing record is NOT reported as an empty successful result:
     * the server rejects the request, so an id that does not exist comes back with
     * resultStatus.isError true and apiResultStatus REJECT.  Callers using this as an existence
     * check must branch on isReject() rather than on isError(), so that a service failure is not
     * mistaken for a record that does not exist.  REJECT also covers server-side validation
     * failures, so reading it as not-found is only safe once the request itself is known to be
     * valid - here, that the id is non-blank.
     *
     * The RPC's key is a proto oneof and AnnotationClient exposes it as two named methods.  Only
     * the by-id arm is wrapped here: this application's only use is the activation-id collision
     * check, which always has the id the user typed.  The composite-key arm is deliberately not
     * wrapped rather than overlooked - an unused wrapper is a surface to keep correct for no
     * benefit.
     *
     * The name says ById for that reason: it mirrors the wrapper it delegates to, and leaves
     * getConfigurationActivationByCompositeKey() free to be added later without renaming this one
     * or leaving an ambiguous getConfigurationActivation() beside it.
     */
    public GetConfigurationActivationApiResult getConfigurationActivationById(String clientActivationId) {
        return api.annotationClient.getConfigurationActivationById(clientActivationId);
    }

    /**
     * Batch upsert of sample statuses.
     *
     * Upsert is per individual status keyed by (pvName, timestamp, domain, layer) and is a FULL
     * REPLACE: re-saving an existing key with empty confidence/reasons clears any previously
     * stored values, as there is no patch-style partial update of a status.  To cleanly re-label
     * a time range, delete the range first.
     *
     * source and modifiedBy are request-scoped provenance applying to every frame, so frames from
     * a single producer should be batched per request; mixing producers misattributes provenance.
     */
    public SaveSampleStatusesApiResult saveSampleStatuses(
            List<SampleStatusFrame> frames, String source, String modifiedBy
    ) {
        return api.annotationClient.saveSampleStatuses(frames, source, modifiedBy);
    }

    /**
     * Unary sample status query with resumable paging.
     *
     * Takes Instant at this boundary and converts inward, following the convention of the other
     * query wrappers here; the underlying client params take protobuf Timestamps.  Conversion
     * goes through timestampFromInstant() so a null time stays null rather than becoming a
     * zero-valued Timestamp describing the epoch.
     *
     * pvNames, domains and layers are optional filters combined with logical AND, with values
     * within each combined by OR; an empty or null list matches all values.  An empty pvNames
     * list is how to enumerate which PVs a (domain, layer) has labeled.
     *
     * Note that bucket selection is a TimeRange OVERLAP test and boundary buckets are returned
     * WHOLE, so a returned bucket may contain statuses outside [beginTime, endTime).  Callers
     * that count or display individual statuses need to account for this rather than assuming
     * every returned status falls inside the requested range.
     *
     * An empty result is a success with an empty bucket list, not an error.  Pass a prior
     * result's nextPageToken as pageToken to fetch the next page; an empty nextPageToken on the
     * result indicates the last page.
     */
    public QuerySampleStatusesApiResult querySampleStatuses(
            Instant beginTime,
            Instant endTime,
            List<String> pvNames,
            List<String> domains,
            List<String> layers,
            int limit,
            String pageToken
    ) {
        final AnnotationClient.QuerySampleStatusesParams params =
                new AnnotationClient.QuerySampleStatusesParams(
                        timestampFromInstant(beginTime),
                        timestampFromInstant(endTime),
                        emptyToNull(pvNames),
                        emptyToNull(domains),
                        emptyToNull(layers),
                        limit,
                        emptyToNull(pageToken));

        return api.annotationClient.querySampleStatuses(params);
    }

    /**
     * Sample status query that follows nextPageToken internally, returning every matching bucket up
     * to QUERY_RESULT_CAP.  The explore view uses this rather than the single-page wrapper above, so
     * it needs no paging UI, matching queryDataSets() / queryAnnotations().
     *
     * <strong>The cap counts buckets, not statuses.</strong>  Paging boundaries always fall between
     * whole buckets, so a bucket is the smallest unit that can be capped -- but one bucket can carry
     * thousands of statuses, so a capped bucket list does NOT bound the number of rows a caller
     * derives from it.  A caller displaying individual statuses must bound its own row count and say
     * so; truncated here means "more buckets existed", which is a weaker statement than the
     * record-level truncation the annotation queries report.
     *
     * A failed page throws QueryFailedException rather than returning what had accumulated, for the
     * same reason as the other paged wrappers: a partial list presented as a complete one is the bug
     * this design prevents, not an acceptable degradation.
     */
    public PagedResult<SampleStatusBucket> querySampleStatusBuckets(
            Instant beginTime,
            Instant endTime,
            List<String> pvNames,
            List<String> domains,
            List<String> layers
    ) {
        return accumulatePages(
                pageToken -> {
                    final QuerySampleStatusesApiResult pageResult = querySampleStatuses(
                            beginTime, endTime, pvNames, domains, layers, 0, pageToken);

                    if (pageResult == null) {
                        throw new QueryFailedException(
                                "Sample status query failed - null response from service");
                    }
                    if (pageResult.resultStatus.isError) {
                        throw new QueryFailedException(
                                "Sample status query failed: " + pageResult.resultStatus.msg);
                    }
                    return pageResult;
                },
                pageResult -> pageResult.nextPageToken,
                pageResult -> pageResult.sampleStatusBuckets,
                QUERY_RESULT_CAP);
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
