package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.criteria.AttributeCriterion;
import com.ospreydcs.dp.client.criteria.TextMatch;
import com.ospreydcs.dp.service.common.config.ConfigurationManager;
import com.ospreydcs.dp.grpc.v1.common.Configuration;
import com.ospreydcs.dp.grpc.v1.common.ConfigurationActivation;
import com.ospreydcs.dp.grpc.v1.common.PvMetadata;
import com.ospreydcs.dp.grpc.v1.common.SampleStatusBucket;
import com.ospreydcs.dp.gui.model.PvDetail;
import com.ospreydcs.dp.gui.model.SampleStatusTableRow;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end verification of the query paths added by issue #39 tasks 3, 4 and 5, against a real
 * in-process service ecosystem and a real MongoDB.
 *
 * These are the claims the unit suite CANNOT check, because each one is about what the SERVER
 * actually does rather than about what this app computes:
 *
 *  - getPvMetadata() resolves aliases, returning the record under its CANONICAL name.  The whole
 *    alias-trap design in task 4 rests on that being true; if it is not, the structural fix solves
 *    a problem that does not exist and a real one is unguarded.
 *  - a sample status query returns buckets whose SamplingClock matches the clock the data was
 *    ingested with, at nanosecond precision.  Misalignment fails SILENTLY -- the save succeeds, the
 *    statuses are stored, and nothing matches at query time.
 *  - boundary buckets come back WHOLE, so a bucket at the edge of the requested window carries
 *    statuses outside it and the client-side trim is load-bearing rather than cosmetic.
 *  - TimeRangeCriterion needs both bounds, and a half-filled range is DROPPED rather than rejected
 *    -- the premise of the refusal in ConfigurationExploreViewModel.
 *  - a query with no criteria matches everything rather than erroring, and a blank TextMatch
 *    contributes no criterion rather than being rejected.
 *
 * SKIPPED, not failed, when MongoDB is unreachable, so CI stays green -- same probe and rationale
 * as AnnotationApiLiveIT, which this is modelled on.
 *
 * This test WRITES TO AND CLEANS UP the configured database (dp-demo).  Records are namespaced with
 * a per-run stamp and removed in @AfterAll; a failure mid-run can leave stamped records behind, and
 * they are inert and identifiable.
 *
 * Note that the app has no remote-gRPC path yet: DpApplication.init() always starts its own
 * in-process ecosystem.  Running services on 50051-50053 therefore neither help nor hinder this
 * test -- it reaches the same MongoDB through its own services.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExploreQueryLiveIT {

    /** Per-run namespace, so concurrent or repeated runs cannot collide. */
    private static final String STAMP = String.valueOf(System.currentTimeMillis());

    private static final int PROBE_TIMEOUT_MILLIS = 1500;
    private static final String DATABASE_NAME = "dp-demo";

    private static final String CANONICAL_PV = "IT:CANONICAL:" + STAMP;
    private static final String ALIAS_PV = "IT:ALIAS:" + STAMP;
    private static final String CONFIG_NAME = "it-config-" + STAMP;
    private static final String CONFIG_CATEGORY = "it-category-" + STAMP;
    private static final String ACTIVATION_ID = "it-activation-" + STAMP;

    /** The ingestion window used for the sample status round trip. */
    private static Instant dataBegin;
    private static Instant dataEnd;
    private static String statusPvName;

    private static DpApplication app;

    private static boolean mongoIsReachable() {
        String host = "localhost";
        int port = 27017;
        try {
            final ConfigurationManager config = ConfigurationManager.getInstance();
            host = config.getConfigString("MongoClient.dbHost", "localhost");
            port = config.getConfigInteger("MongoClient.dbPort", 27017);
        } catch (Exception ignored) {
            // fall back to the defaults above
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MILLIS);
            return true;
        } catch (Exception unreachable) {
            return false;
        }
    }

    @BeforeAll
    static void startEcosystem() throws Exception {
        assumeTrue(mongoIsReachable(),
                "MongoDB is not reachable, so the live explore query tests are skipped; start "
                        + "MongoDB to run them");

        FxToolkitSupport.ensureStarted();

        app = new DpApplication();
        assertTrue(app.init(), "DpApplication.init() failed against a reachable MongoDB");
    }

    @AfterAll
    static void stopEcosystemAndCleanUp() {
        if (app == null) {
            return;
        }
        try {
            deleteStampedRecords();
        } finally {
            app.fini();
        }
    }

    /**
     * Removes this run's records.  Best effort by design, as in AnnotationApiLiveIT: a cleanup
     * failure must not redden the build, and leftover stamped records are inert.
     */
    private static void deleteStampedRecords() {
        final ConfigurationManager config = ConfigurationManager.getInstance();
        final String host = config.getConfigString("MongoClient.dbHost", "localhost");
        final int port = config.getConfigInteger("MongoClient.dbPort", 27017);
        final String user = config.getConfigString("MongoClient.dbUser", "admin");
        final String password = config.getConfigString("MongoClient.dbPassword", "admin");

        final String connectionString =
                "mongodb://" + user + ":" + password + "@" + host + ":" + port + "/";

        try (com.mongodb.client.MongoClient client =
                     com.mongodb.client.MongoClients.create(connectionString)) {

            final com.mongodb.client.MongoDatabase database = client.getDatabase(DATABASE_NAME);
            final var stampedName = com.mongodb.client.model.Filters.regex("name", STAMP);
            final var stampedPv = com.mongodb.client.model.Filters.regex("pvName", STAMP);

            database.getCollection("pvMetadata").deleteMany(stampedPv);
            database.getCollection("sampleStatuses").deleteMany(stampedPv);
            database.getCollection("buckets").deleteMany(stampedPv);
            database.getCollection("pvStats").deleteMany(
                    com.mongodb.client.model.Filters.regex("_id", STAMP));
            database.getCollection("providers").deleteMany(stampedName);
            database.getCollection("configurations").deleteMany(
                    com.mongodb.client.model.Filters.regex("configurationName", STAMP));
            database.getCollection("configurationActivations").deleteMany(
                    com.mongodb.client.model.Filters.regex("clientActivationId", STAMP));

        } catch (Exception cleanupFailed) {
            System.err.println("ExploreQueryLiveIT cleanup failed (records tagged " + STAMP
                    + " may remain, and are inert): " + cleanupFailed);
        }
    }

    // =======================================================================================
    // Task 4 - PV metadata
    // =======================================================================================

    @Test
    @Order(10)
    public void savesAPvMetadataRecordWithAnAlias() {
        final var result = app.savePvMetadata(
                CANONICAL_PV,
                List.of(ALIAS_PV),
                List.of("it-tag-" + STAMP),
                Map.of("it-key", "it-value"),
                "a live IT record",
                "explore-query-live-it");

        assertNotNull(result, "savePvMetadata returned null");
        assertFalse(result.resultStatus.isError,
                "savePvMetadata failed: " + result.resultStatus.msg);
    }

    /**
     * THE ALIAS TRAP, verified rather than assumed.
     *
     * The entire load-for-edit design in task 4 rests on this: looking a record up by an alias
     * returns it under its CANONICAL name, and savePvMetadata() is a full-replace upsert keyed on
     * that name. If the server did not resolve aliases, passing the record to the editor would be
     * solving a problem that does not exist -- and if it resolves them differently than documented,
     * the structural fix is aimed at the wrong thing.
     */
    @Test
    @Order(11)
    public void getPvMetadataResolvesAnAliasToTheCanonicalRecord() {
        final var byAlias = app.getPvMetadata(ALIAS_PV);

        assertNotNull(byAlias, "getPvMetadata returned null for the alias");
        assertFalse(byAlias.resultStatus.isError,
                "looking up by alias failed: " + byAlias.resultStatus.msg);

        assertEquals(CANONICAL_PV, byAlias.pvMetadata.getPvName(),
                "the server must resolve the alias to the CANONICAL name -- this is the premise of "
                        + "passing the resolved record to the editor rather than the typed text");
        assertTrue(byAlias.pvMetadata.getAliasesList().contains(ALIAS_PV),
                "the resolved record must still carry the alias it was found by");
    }

    /** A missing record is a REJECT, not an error -- the distinction existence checks depend on. */
    @Test
    @Order(12)
    public void getPvMetadataReportsAMissingRecordAsAReject() {
        final var missing = app.getPvMetadata("IT:NO:SUCH:PV:" + STAMP);

        assertNotNull(missing, "getPvMetadata returned null for a missing record");
        assertTrue(missing.isReject(),
                "a missing record must be a REJECT so it is distinguishable from an unreachable "
                        + "service, but resultStatus was: " + missing.resultStatus.msg);
    }

    @Test
    @Order(13)
    public void queryPvMetadataFindsTheRecordByEachMatchMode() {
        // EXACT on the canonical name
        final var exact = app.queryPvMetadata(
                new TextMatch(List.of(CANONICAL_PV), null, null), emptyMatch(), null, null);
        assertTrue(containsPv(exact.records, CANONICAL_PV),
                "an exact name match must find the record");

        // PREFIX
        final var prefix = app.queryPvMetadata(
                new TextMatch(null, List.of("IT:CANONICAL:"), null), emptyMatch(), null, null);
        assertTrue(containsPv(prefix.records, CANONICAL_PV),
                "a prefix match must find the record");

        // CONTAINS, on the stamp alone
        final var contains = app.queryPvMetadata(
                new TextMatch(null, null, List.of(STAMP)), emptyMatch(), null, null);
        assertTrue(containsPv(contains.records, CANONICAL_PV),
                "a contains match must find the record");

        // by ALIAS rather than by name
        final var byAlias = app.queryPvMetadata(
                emptyMatch(), new TextMatch(List.of(ALIAS_PV), null, null), null, null);
        assertTrue(containsPv(byAlias.records, CANONICAL_PV),
                "an alias criterion must find the record under its canonical name");
    }

    /**
     * A blank TextMatch must contribute NO criterion rather than being rejected -- the premise of
     * textMatch() returning an all-null match for an unfilled field.
     */
    @Test
    @Order(14)
    public void aQueryWithNoCriteriaSucceedsRatherThanBeingRejected() {
        final var all = app.queryPvMetadata(emptyMatch(), emptyMatch(), null, null);

        assertNotNull(all, "a criteria-free query returned null");
        assertTrue(containsPv(all.records, CANONICAL_PV),
                "a query with no criteria must match everything, including this run's record");
    }

    @Test
    @Order(15)
    public void queryPvMetadataFindsTheRecordByTagAndByAttribute() {
        final var byTag = app.queryPvMetadata(
                emptyMatch(), emptyMatch(), List.of("it-tag-" + STAMP), null);
        assertTrue(containsPv(byTag.records, CANONICAL_PV), "a tag criterion must find the record");

        // key-only existence search, then key+value
        final var byKeyOnly = app.queryPvMetadata(
                emptyMatch(), emptyMatch(), null, List.of(new AttributeCriterion("it-key", null)));
        assertTrue(containsPv(byKeyOnly.records, CANONICAL_PV),
                "a key-only attribute criterion must match any record carrying the key");

        final var byKeyValue = app.queryPvMetadata(
                emptyMatch(), emptyMatch(), null,
                List.of(new AttributeCriterion("it-key", List.of("it-value"))));
        assertTrue(containsPv(byKeyValue.records, CANONICAL_PV),
                "a key+value attribute criterion must find the record");

        final var byWrongValue = app.queryPvMetadata(
                emptyMatch(), emptyMatch(), null,
                List.of(new AttributeCriterion("it-key", List.of("not-the-value"))));
        assertFalse(containsPv(byWrongValue.records, CANONICAL_PV),
                "a non-matching attribute value must EXCLUDE the record -- otherwise the criterion "
                        + "is being dropped rather than applied");
    }

    // =======================================================================================
    // Task 5 - configurations and activations
    // =======================================================================================

    @Test
    @Order(20)
    public void savesAConfigurationAndAnActivation() {
        final var configResult = app.saveConfiguration(
                CONFIG_NAME, CONFIG_CATEGORY, "a live IT configuration", null,
                List.of("it-tag-" + STAMP), Map.of("it-key", "it-value"),
                "explore-query-live-it");

        assertNotNull(configResult, "saveConfiguration returned null");
        assertFalse(configResult.resultStatus.isError,
                "saveConfiguration failed: " + configResult.resultStatus.msg);

        final Instant start = Instant.parse("2030-01-01T00:00:00Z");
        final Instant end = Instant.parse("2030-01-02T00:00:00Z");

        final var activationResult = app.saveConfigurationActivation(
                ACTIVATION_ID, CONFIG_NAME, start, end, "a live IT activation",
                List.of("it-tag-" + STAMP), Map.of("it-key", "it-value"),
                "explore-query-live-it");

        assertNotNull(activationResult, "saveConfigurationActivation returned null");
        assertFalse(activationResult.resultStatus.isError,
                "saveConfigurationActivation failed: " + activationResult.resultStatus.msg);
    }

    @Test
    @Order(21)
    public void queryConfigurationsFindsTheRecord() {
        final var byName = app.queryConfigurations(
                new TextMatch(List.of(CONFIG_NAME), null, null), null, null, null, null);
        assertTrue(containsConfiguration(byName.records, CONFIG_NAME),
                "an exact name match must find the configuration");

        final var byCategory = app.queryConfigurations(
                emptyMatch(), List.of(CONFIG_CATEGORY), null, null, null);
        assertTrue(containsConfiguration(byCategory.records, CONFIG_NAME),
                "a category criterion must find the configuration");

        final var byTag = app.queryConfigurations(
                emptyMatch(), null, List.of("it-tag-" + STAMP), null, null);
        assertTrue(containsConfiguration(byTag.records, CONFIG_NAME),
                "a tag criterion must find the configuration");
    }

    @Test
    @Order(22)
    public void queryConfigurationActivationsFindsTheActivationByName() {
        final var byConfigName = app.queryConfigurationActivations(
                null, null, null, List.of(CONFIG_NAME), null, null, null, null);

        assertTrue(containsActivation(byConfigName.records, ACTIVATION_ID),
                "a configuration-name criterion must find the activation");
    }

    /**
     * A full range narrows; an OVERLAPPING window still matches. The activation covers
     * 2030-01-01 to 2030-01-02, so a window inside it, straddling it, or containing it all match,
     * while a disjoint window does not.
     */
    @Test
    @Order(23)
    public void anOverlappingRangeMatchesAndADisjointOneDoesNot() {
        final var overlapping = app.queryConfigurationActivations(
                null,
                Instant.parse("2030-01-01T06:00:00Z"),
                Instant.parse("2030-01-01T18:00:00Z"),
                List.of(CONFIG_NAME), null, null, null, null);
        assertTrue(containsActivation(overlapping.records, ACTIVATION_ID),
                "a window inside the activation must match it");

        final var disjoint = app.queryConfigurationActivations(
                null,
                Instant.parse("2031-01-01T00:00:00Z"),
                Instant.parse("2031-01-02T00:00:00Z"),
                List.of(CONFIG_NAME), null, null, null, null);
        assertFalse(containsActivation(disjoint.records, ACTIVATION_ID),
                "a window that does not overlap must EXCLUDE the activation -- otherwise the range "
                        + "criterion is being dropped rather than applied");
    }

    /**
     * THE HALF-FILLED RANGE, verified rather than assumed.
     *
     * This is the premise of ConfigurationExploreViewModel refusing such a search. The claim is
     * that the request builder emits NO criterion at all when only one bound is supplied -- so the
     * request SUCCEEDS and silently returns a broader result set, rather than being rejected. If
     * the server rejected it instead, the refusal would be unnecessary; if it narrowed, it would be
     * wrong. Both alternatives are worth knowing about, so this pins the actual behavior.
     */
    @Test
    @Order(24)
    public void aHalfFilledRangeIsSilentlyDroppedRatherThanRejected() {
        // A start bound far AFTER the activation.  Were the bound honored at all, this would
        // exclude the activation; if the criterion is dropped, the activation still comes back.
        final var startOnly = app.queryConfigurationActivations(
                null,
                Instant.parse("2031-01-01T00:00:00Z"),
                null,
                List.of(CONFIG_NAME), null, null, null, null);

        assertNotNull(startOnly, "a half-filled range query returned null");
        assertTrue(containsActivation(startOnly.records, ACTIVATION_ID),
                "a half-filled range must be DROPPED, not applied and not rejected -- the request "
                        + "succeeds and returns a result the user did not ask to be widened, which "
                        + "is exactly why ConfigurationExploreViewModel refuses to send one");
    }

    // =======================================================================================
    // Task 3 - sample statuses
    // =======================================================================================

    /**
     * Ingests data WITH sample statuses, through the same DpApplication call the data-generation
     * view makes. This is the write half of the round trip whose read half follows.
     */
    @Test
    @Order(30)
    public void generatesAndIngestsDataWithSampleStatuses() {
        final var registered = app.registerProvider(
                "it-provider-" + STAMP, "a live IT provider", List.of(), Map.of());
        assertNotNull(registered, "registerProvider returned null");
        assertFalse(registered.isError, "registerProvider failed: " + registered.msg);

        statusPvName = "IT:STATUS:" + STAMP;
        dataBegin = Instant.now().minus(10, ChronoUnit.MINUTES)
                .truncatedTo(ChronoUnit.SECONDS);
        dataEnd = dataBegin.plus(10, ChronoUnit.SECONDS);

        final PvDetail pv = new PvDetail(statusPvName, "float", 10, "1.0", "0.5");

        final var status = app.generateAndIngestData(
                dataBegin, dataEnd, null, List.of(pv), 5, List.of(), true);

        assertNotNull(status, "generateAndIngestData returned null");
        assertFalse(status.isError, "generateAndIngestData failed: " + status.msg);
    }

    /**
     * THE CLOCK ALIGNMENT, verified rather than assumed.
     *
     * A sample status attaches to a sample only by exact (pvName, timestamp) equality at NANOSECOND
     * precision. Misalignment fails silently: the save succeeds, the statuses are stored, and
     * nothing matches at query time. This asserts the statuses come back at all, and that expanding
     * their clock reproduces timestamps inside the window they were written for.
     */
    @Test
    @Order(31)
    public void sampleStatusesComeBackAndTheirClockExpandsIntoTheIngestedWindow() {
        final var buckets = app.querySampleStatusBuckets(
                dataBegin, dataEnd, List.of(statusPvName),
                List.of(DpApplication.SAMPLE_STATUS_DEMO_DOMAIN),
                List.of(DpApplication.SAMPLE_STATUS_DEMO_LAYER));

        assertNotNull(buckets, "querySampleStatusBuckets returned null");
        assertFalse(buckets.records.isEmpty(),
                "no sample status buckets came back for the window they were written for -- a "
                        + "silent clock misalignment looks exactly like this");

        final List<SampleStatusTableRow> rows = new ArrayList<>();
        for (SampleStatusBucket bucket : buckets.records) {
            rows.addAll(SampleStatusTableRow.expand(
                    bucket, dataBegin, dataEnd, SampleStatusExploreViewModel.CODE_LABELS));
        }

        assertFalse(rows.isEmpty(), "buckets expanded to no rows at all");

        // 10 values/second over a 10-second window
        assertEquals(100, rows.size(),
                "expected one status per generated sample across the window");

        for (SampleStatusTableRow row : rows) {
            final Instant timestamp = row.getTimestampInstant();
            assertNotNull(timestamp, "a row has no timestamp");
            assertFalse(timestamp.isBefore(dataBegin),
                    "a trimmed row must not precede the window: " + timestamp);
            assertTrue(timestamp.isBefore(dataEnd),
                    "the range is half-open, so a row must not be at or after the end: " + timestamp);
            assertEquals(statusPvName, row.getPvName());
            assertEquals(DpApplication.SAMPLE_STATUS_DEMO_DOMAIN, row.getDomain());
        }
    }

    /** Every returned code must resolve to a label, since the demo generator writes only these. */
    @Test
    @Order(32)
    public void everyDemoStatusCodeResolvesToALabel() {
        final var buckets = app.querySampleStatusBuckets(
                dataBegin, dataEnd, List.of(statusPvName), null, null);

        final List<SampleStatusTableRow> rows = new ArrayList<>();
        for (SampleStatusBucket bucket : buckets.records) {
            rows.addAll(SampleStatusTableRow.expand(
                    bucket, dataBegin, dataEnd, SampleStatusExploreViewModel.CODE_LABELS));
        }
        assertFalse(rows.isEmpty(), "no rows to check labels on");

        for (SampleStatusTableRow row : rows) {
            assertFalse(row.getStatusLabel().isEmpty(),
                    "code " + row.getRawStatusCode() + " in domain " + row.getDomain()
                            + " resolved to an empty label, but the demo generator writes only "
                            + "epics_alarm codes 0-3");
        }
    }

    /**
     * THE BOUNDARY TRIM, verified rather than assumed.
     *
     * Bucket selection is a TimeRange overlap test and boundary buckets are returned WHOLE, so a
     * narrow window inside a bucket still returns that bucket entire. The client-side trim is what
     * makes the displayed rows match what was asked for -- this asserts the untrimmed expansion
     * really does carry statuses outside the window, so the trim is load-bearing rather than a
     * no-op that happens to look right.
     */
    @Test
    @Order(33)
    public void boundaryBucketsComeBackWholeSoTheTrimIsLoadBearing() {
        // a one-second window in the middle of the ingested range
        final Instant narrowBegin = dataBegin.plus(2, ChronoUnit.SECONDS);
        final Instant narrowEnd = narrowBegin.plus(1, ChronoUnit.SECONDS);

        final var buckets = app.querySampleStatusBuckets(
                narrowBegin, narrowEnd, List.of(statusPvName), null, null);
        assertFalse(buckets.records.isEmpty(), "no buckets came back for the narrow window");

        int untrimmed = 0;
        for (SampleStatusBucket bucket : buckets.records) {
            untrimmed += SampleStatusTableRow.expand(
                    bucket, null, null, SampleStatusExploreViewModel.CODE_LABELS).size();
        }

        final List<SampleStatusTableRow> trimmed = new ArrayList<>();
        for (SampleStatusBucket bucket : buckets.records) {
            trimmed.addAll(SampleStatusTableRow.expand(
                    bucket, narrowBegin, narrowEnd, SampleStatusExploreViewModel.CODE_LABELS));
        }

        assertEquals(10, trimmed.size(),
                "a one-second window at 10 values/second must trim to exactly 10 statuses");
        assertTrue(untrimmed > trimmed.size(),
                "the boundary bucket must come back WHOLE -- if the server already trimmed, the "
                        + "client-side trim would be untested and this assertion documents that "
                        + "the server behavior changed (untrimmed=" + untrimmed
                        + ", trimmed=" + trimmed.size() + ")");

        for (SampleStatusTableRow row : trimmed) {
            assertFalse(row.getTimestampInstant().isBefore(narrowBegin));
            assertTrue(row.getTimestampInstant().isBefore(narrowEnd));
        }
    }

    /** A window with no statuses returns empty rather than erroring. */
    @Test
    @Order(34)
    public void aWindowWithNoStatusesReturnsEmpty() {
        final var buckets = app.querySampleStatusBuckets(
                Instant.parse("2035-01-01T00:00:00Z"),
                Instant.parse("2035-01-01T00:01:00Z"),
                List.of(statusPvName), null, null);

        assertNotNull(buckets, "an empty-window query returned null");

        final List<SampleStatusTableRow> rows = new ArrayList<>();
        for (SampleStatusBucket bucket : buckets.records) {
            rows.addAll(SampleStatusTableRow.expand(
                    bucket,
                    Instant.parse("2035-01-01T00:00:00Z"),
                    Instant.parse("2035-01-01T00:01:00Z"),
                    SampleStatusExploreViewModel.CODE_LABELS));
        }
        assertTrue(rows.isEmpty(), "a window with no statuses must yield no rows");
    }

    // ------------------------------------------------------------------ helpers

    private static TextMatch emptyMatch() {
        return new TextMatch(null, null, null);
    }

    private static boolean containsPv(List<PvMetadata> records, String pvName) {
        return records.stream().anyMatch(record -> pvName.equals(record.getPvName()));
    }

    private static boolean containsConfiguration(List<Configuration> records, String name) {
        return records.stream().anyMatch(record -> name.equals(record.getConfigurationName()));
    }

    private static boolean containsActivation(List<ConfigurationActivation> records, String id) {
        return records.stream().anyMatch(record -> id.equals(record.getClientActivationId()));
    }
}
