package com.ospreydcs.dp.gui;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.ospreydcs.dp.client.result.QueryPvStatsApiResult;
import com.ospreydcs.dp.gui.model.PvDetail;
import com.ospreydcs.dp.service.common.config.ConfigurationManager;
import com.ospreydcs.dp.service.common.mongo.MongoClientBase;
import com.ospreydcs.dp.service.inprocess.MongoInterface;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The demo database lifecycle after issue #4 task 3, against a real MongoDB.
 *
 * <p>Task 3 changed one thing, and it is a behavior change rather than a refactor: the demo database
 * is no longer dropped at launch.  <b>Every claim below is only checkable against a real database</b>,
 * because each is about what MongoDB still holds after a call returns:
 *
 * <ul>
 *   <li>Data ingested by one run must still be there after a restart.  The drop and its absence are
 *       both silent; the only observable difference is what a later query returns.</li>
 *   <li>The database name override must still be applied.  Dropping it along with the drop would
 *       have pointed the demo at dp-service's default database name -- which in a real installation
 *       is production -- and <b>nothing would error</b>: ingestion and query would both work, in the
 *       wrong database.  This is the hazard in the split, and it is why the split needed care rather
 *       than a deletion.</li>
 *   <li>{@link MongoInterface#deleteDemoDatabase()} must actually remove the database.  A drop that
 *       returned true while doing nothing is the failure the UI structurally cannot detect: the
 *       action clears the session state on a true return, so the application would present a
 *       pre-ingestion home view over a fully populated archive.</li>
 * </ul>
 *
 * <p>SKIPPED rather than failed when MongoDB is unreachable, so CI (which has no database) stays
 * green -- the same gating as {@code AnnotationApiLiveIT} and {@code ExploreQueryLiveIT}.
 *
 * <p><b>This test drops the configured database, unlike the other live ITs.</b>  They avoid that by
 * stamping individual records and deleting them in {@code @AfterAll}; here the drop IS the subject,
 * so it cannot be avoided.  Running this wipes whatever demo data is present.  The methods are
 * ordered because they are one narrative: ingest, restart, verify survival, delete, verify removal.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DemoDatabaseLifecycleLiveIT {

    private static final String STAMP = String.valueOf(System.currentTimeMillis());

    private static final int PROBE_TIMEOUT_MILLIS = 1500;

    /** See awaitIngestedPvVisible() for why a readiness gate is needed at all. */
    private static final long VISIBILITY_TIMEOUT_MILLIS = 30_000;
    private static final long VISIBILITY_POLL_MILLIS = 250;

    /**
     * The collection ingested time-series buckets land in.  Taken from dp-service's own constant
     * rather than repeated as a literal, so a rename there fails to compile here instead of turning
     * this guard into one that counts zero in every database and passes vacuously.
     */
    private static final String BUCKETS_COLLECTION = MongoClientBase.COLLECTION_NAME_BUCKETS;

    /**
     * dp-service's database name when nothing overrides it.  Named here because it is what the demo
     * would silently fall back to -- and in a real installation it is the production database.
     */
    private static final String DEFAULT_DATABASE_NAME = MongoClientBase.MONGO_DATABASE_NAME;

    private static DpApplication app;

    private static String pvName;

    // ------------------------------------------------------------------ gating

    private static boolean mongoIsReachable() {
        final String host;
        final int port;
        try {
            final ConfigurationManager config = ConfigurationManager.getInstance();
            host = config.getConfigString("MongoClient.dbHost", "localhost");
            port = config.getConfigInteger("MongoClient.dbPort", 27017);
        } catch (Exception configUnavailable) {
            return false;
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MILLIS);
            return true;
        } catch (IOException | RuntimeException unreachable) {
            return false;
        }
    }

    @BeforeAll
    public static void checkPrerequisites() {
        assumeTrue(mongoIsReachable(),
                "MongoDB is not reachable; skipping the demo database lifecycle IT");
    }

    @AfterAll
    public static void tearDown() {
        stopApplication();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Starts a fresh application, exactly as a launch does.
     *
     * <p>Each call is a separate run of the demo: a new in-process ecosystem, which calls
     * {@code MongoInterface.prepareDemoDatabase()} on the way up.  Restarting is the mechanism under
     * test -- this is the point at which the pre-#4 code dropped the database.
     */
    private static void startApplication() {
        app = new DpApplication();
        assertTrue(app.init(), "DpApplication.init() failed");
        assertFalse(app.isDeploymentMode(), "this IT assumes demo mode, which is the default");
    }

    private static void stopApplication() {
        if (app != null) {
            app.fini();
            app = null;
        }
    }

    /**
     * Whether this run's PV is visible in the archive.
     *
     * <p>Uses queryPvStats rather than queryPvMetadata deliberately: PV stats are DERIVED by
     * aggregation over ingested buckets, so they exist precisely because data was ingested.  Curated
     * PvMetadata records are authored separately and this test never writes one, so that query would
     * return empty in every case -- passing the "deleted" assertion vacuously while failing the
     * "survived" one.
     */
    private static boolean ingestedPvIsPresent() {
        final QueryPvStatsApiResult result = app.queryPvStats(List.of(pvName));
        assertNotNull(result, "queryPvStats returned null");
        if (result.isError()) {
            // An error is not an answer to "is the data present", so fail rather than read it as
            // absence -- which would let a broken query masquerade as a successful delete.
            throw new AssertionError("queryPvStats failed: " + result.resultStatus.msg);
        }
        assertNotNull(result.queryPvStatsResponse, "queryPvStatsResponse was null");
        return result.queryPvStatsResponse.getStatsResult().getPvStatsCount() > 0;
    }

    /**
     * Waits until this run's PV is queryable.
     *
     * <p><b>generateAndIngestData() returning success does not mean the data can be read back.</b>
     * Ingestion is asynchronous, so a query issued immediately afterwards reliably reports nothing --
     * the same answer a genuinely empty archive gives, which is exactly the answer this test is
     * trying to distinguish.  Without this gate the survival assertion would be testing the
     * ingestion lag rather than the drop.
     *
     * <p>Polls rather than sleeping a fixed interval, following QuerySamplesLiveIT: a growing lag
     * then fails loudly on the timeout instead of turning the test flaky.
     */
    private static void awaitIngestedPvVisible() throws InterruptedException {
        final long deadline = System.currentTimeMillis() + VISIBILITY_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (ingestedPvIsPresent()) {
                return;
            }
            Thread.sleep(VISIBILITY_POLL_MILLIS);
        }
        throw new AssertionError(
                "ingested PV " + pvName + " was not queryable within "
                        + VISIBILITY_TIMEOUT_MILLIS + "ms of a successful ingest");
    }

    /**
     * Ingests one bucket for the named PV and waits until it is queryable.
     *
     * <p>Used to put data back after the delete, so the launch probe has something to find.  Sets
     * {@code pvName} so {@link #awaitIngestedPvVisible()} polls for this PV rather than the one the
     * earlier tests ingested and the delete removed.
     */
    private static void ingestOneBucket(String newPvName) throws InterruptedException {

        final var registered = app.registerProvider(
                "reingest-provider-" + STAMP, "session reset IT re-ingest", List.of(), Map.of());
        assertFalse(registered.isError, "re-ingest registerProvider failed: " + registered.msg);

        pvName = newPvName;
        final Instant dataBegin =
                Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        final Instant dataEnd = dataBegin.plus(5, ChronoUnit.SECONDS);
        final PvDetail pv = new PvDetail(pvName, "float", 10, "1.0", "0.5");

        final var status = app.generateAndIngestData(
                dataBegin, dataEnd, null, List.of(pv), 5, List.of(), false);
        assertFalse(status.isError, "re-ingest failed: " + status.msg);

        awaitIngestedPvVisible();
    }

    /** Whether the named database currently exists on the configured server. */
    private static boolean databaseExists(String databaseName) {
        try (MongoClient client = MongoClients.create(mongoConnectString())) {
            for (String name : client.listDatabaseNames()) {
                if (databaseName.equals(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * How many bucket documents in the named database carry this run's PV.
     *
     * <p>Counted directly through the Mongo driver rather than through the application, because the
     * question is <b>which database the data landed in</b> -- and the application cannot answer that:
     * it reads back through whatever name the global override left in place, so it agrees with itself
     * whether or not the override was applied.
     */
    private static long bucketCountForThisRun(String databaseName) {
        try (MongoClient client = MongoClients.create(mongoConnectString())) {
            return client.getDatabase(databaseName)
                    .getCollection(BUCKETS_COLLECTION)
                    .countDocuments(new org.bson.Document("pvName", pvName));
        } catch (RuntimeException absent) {
            // A database or collection that does not exist counts as zero rather than as an error:
            // "the data is not here" is exactly the answer being asked for.
            return 0;
        }
    }

    /** The same connection settings the application itself uses, rather than a hardcoded default. */
    private static String mongoConnectString() {
        final ConfigurationManager config = ConfigurationManager.getInstance();
        final String host = config.getConfigString("MongoClient.dbHost", "localhost");
        final int port = config.getConfigInteger("MongoClient.dbPort", 27017);
        final String user = config.getConfigString("MongoClient.dbUser", "admin");
        final String password = config.getConfigString("MongoClient.dbPassword", "admin");
        return "mongodb://" + user + ":" + password + "@" + host + ":" + port + "/";
    }

    // ------------------------------------------------------------------ the narrative

    @Test
    @Order(10)
    @DisplayName("ingest data into the demo database")
    public void ingestsData() throws InterruptedException {

        startApplication();

        final var registered = app.registerProvider(
                "lifecycle-provider-" + STAMP, "demo database lifecycle IT", List.of(), Map.of());
        assertNotNull(registered, "registerProvider returned null");
        assertFalse(registered.isError, "registerProvider failed: " + registered.msg);

        pvName = "IT:LIFECYCLE:" + STAMP;
        final Instant dataBegin =
                Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        final Instant dataEnd = dataBegin.plus(5, ChronoUnit.SECONDS);

        final PvDetail pv = new PvDetail(pvName, "float", 10, "1.0", "0.5");

        final var status = app.generateAndIngestData(
                dataBegin, dataEnd, null, List.of(pv), 5, List.of(), false);

        assertNotNull(status, "generateAndIngestData returned null");
        assertFalse(status.isError, "generateAndIngestData failed: " + status.msg);
        assertTrue(app.hasIngestedData(), "hasIngestedData should be set after a successful ingest");

        awaitIngestedPvVisible();
    }

    /**
     * THE BEHAVIOR CHANGE, verified rather than assumed.
     *
     * <p>Before #4 this would have failed: {@code prepareDemoDatabase()} dropped the database on the
     * way up, so the second ecosystem started against an empty one.  A regression here is silent in
     * the worst way -- demo data disappears on restart with no error anywhere, indistinguishable
     * from never having ingested it.
     */
    @Test
    @Order(20)
    @DisplayName("ingested data survives an application restart")
    public void dataSurvivesRestart() {

        assertTrue(ingestedPvIsPresent(),
                "precondition: the ingested PV is visible before the restart");

        stopApplication();
        startApplication();

        assertFalse(app.hasIngestedData(),
                "precondition: the new session has ingested nothing, so what follows is the "
                        + "ARCHIVE's content rather than this session's");

        // Read immediately, with no readiness gate: the data was already confirmed visible before
        // the restart, so anything less than an immediate hit means the restart removed it.  Polling
        // here would mask exactly the failure this test exists to catch.
        assertTrue(ingestedPvIsPresent(),
                "the demo database must NOT be dropped at launch -- data ingested by a previous "
                        + "session has to still be there");
    }

    /**
     * The name override, which is the hazard in the split rather than in the drop.
     *
     * <p>The survival assertion above cannot catch this on its own: if the override were dropped,
     * the write and the read would both use the same wrong database name and agree perfectly.  Only
     * naming the expected database catches it.
     */
    @Test
    @Order(30)
    @DisplayName("the data lands in the demo database, so the name override is still applied")
    public void databaseNameIsStillOverridden() {

        assertTrue(bucketCountForThisRun(MongoInterface.DEMO_DATABASE_NAME) > 0,
                "this run's buckets must be in \"" + MongoInterface.DEMO_DATABASE_NAME + "\" -- "
                        + "without the name override the services bind to dp-service's default "
                        + "database name instead, and nothing errors because reads and writes then "
                        + "agree on the wrong name");

        // The negative half, and the half that stops the positive one from passing on a leftover
        // database: an earlier run's dp-demo is still on the server, so merely asserting that the
        // NAME exists says nothing about where THIS run's data went.
        assertEquals(0L, bucketCountForThisRun(DEFAULT_DATABASE_NAME),
                "this run's buckets must NOT be in \"" + DEFAULT_DATABASE_NAME + "\", dp-service's "
                        + "default database -- in a real installation that is the production one");

        // The invariant itself, stated directly rather than inferred from where documents landed.
        // The assertions above prove where THIS run's data went; this one proves what the process
        // would use for any FUTURE client, which is what a reordering would break first.
        assertEquals(MongoInterface.DEMO_DATABASE_NAME, MongoInterface.effectiveDatabaseName(),
                "the process-global database name must be the demo one while demo mode is running");
    }

    /**
     * The delete action's actual effect, read back from the server rather than taken from the
     * return value.
     */
    @Test
    @Order(40)
    @DisplayName("delete removes the demo database")
    public void deleteRemovesTheDemoDatabase() {

        assertTrue(ingestedPvIsPresent(), "precondition: there is data to delete");

        // Issued while the application still holds open connections to the database, exactly as it
        // does when the user picks the menu item mid-session.
        assertTrue(MongoInterface.deleteDemoDatabase(), "deleteDemoDatabase() reported failure");

        assertFalse(databaseExists(MongoInterface.DEMO_DATABASE_NAME),
                "the demo database must be gone after the delete");
    }

    /**
     * The session-state reset the delete action pairs with.
     *
     * <p>Exercised here rather than only in a unit test because the two halves have to agree: the
     * menus and the home view are driven by this state, so a delete that cleared the database
     * without clearing it would offer Explore views onto nothing.
     */
    @Test
    @Order(50)
    @DisplayName("the session state reset clears what the delete invalidated")
    public void resetClearsSessionState() throws InterruptedException {

        stopApplication();
        startApplication();

        // Re-establish the state a delete invalidates.  EVERY field asserted below must actually
        // be set here first: a fresh DpApplication already has them at their cleared values, so
        // asserting against an untouched application passes whether or not the reset does anything.
        // That is not hypothetical -- an earlier version of this test omitted the registration
        // below, and a reset mutated to leave the provider set passed it.
        final var registered = app.registerProvider(
                "reset-provider-" + STAMP, "session reset IT", List.of(), Map.of());
        assertFalse(registered.isError, "registerProvider failed: " + registered.msg);
        assertNotNull(app.getProviderId(), "precondition: a provider is registered");
        assertNotNull(app.getProviderName(), "precondition: the provider name is set");

        app.setLastOperationResult("ingested something");
        app.setHasPerformedQueries(true);
        app.setPvNames(List.of("IT:SOME:PV"));
        assertNotNull(app.getPvNames(), "precondition: pvNames is populated");
        assertTrue(app.hasPerformedQueries(), "precondition: hasPerformedQueries is set");
        assertNotNull(app.getLastOperationResult(), "precondition: lastOperationResult is set");

        // The launch probe ran against the database test 40 had just dropped, so it correctly found
        // nothing -- which is worth asserting in its own right, since it is the half of the probe
        // that keeps Explore disabled on a genuinely empty demo archive.
        assertFalse(app.archiveHasData(),
                "the probe must report an empty archive as empty, or Explore would be enabled over "
                        + "a database with nothing in it");

        // Now put data back, so archiveHasData is genuinely set before the reset is asked to clear
        // it.  Asserting the reset against an already-false flag would pass whether or not the
        // reset touches it -- exactly the vacuous-guard shape the provider registration above
        // exists to avoid.
        ingestOneBucket("IT:RESET:" + STAMP);
        stopApplication();
        startApplication();
        assertTrue(app.archiveHasData(),
                "precondition: the probe found the data just ingested, so archiveHasData is set");

        app.resetIngestedDataState();

        assertFalse(app.archiveHasData(),
                "the delete emptied the archive, so archiveHasData must clear too -- leaving it set "
                        + "keeps every Explore item enabled over a dropped database, the mirror of "
                        + "the bug the launch probe fixes");
        assertFalse(app.hasIngestedData(), "hasIngestedData");
        assertFalse(app.hasPerformedQueries(), "hasPerformedQueries");
        assertNull(app.getPvNames(),
                "pvNames must collapse to null, which is this class's convention for empty");
        assertNull(app.getProviderId(),
                "the provider registration lived in the dropped database, so its id no longer "
                        + "resolves -- leaving it set would sail past generateAndIngestData()'s "
                        + "guard into a server rejection");
        assertNull(app.getProviderName(), "providerName");
        assertNull(app.getLastOperationResult(), "lastOperationResult");
        assertEquals(0, app.getTotalPvsIngested(), "totalPvsIngested");
        assertEquals(0, app.getTotalBucketsCreated(), "totalBucketsCreated");

        // The connection is untouched: the mode is a property of the launch, not of the session,
        // and the ecosystem is still running.
        assertNotNull(app.getConfiguration(), "configuration must survive the reset");
    }
}
