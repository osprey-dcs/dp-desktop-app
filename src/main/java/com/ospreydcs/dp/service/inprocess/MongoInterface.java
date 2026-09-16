package com.ospreydcs.dp.service.inprocess;

import com.mongodb.client.MongoDatabase;
import com.ospreydcs.dp.service.common.mongo.MongoClientBase;
import com.ospreydcs.dp.service.common.mongo.MongoSyncClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The demo mode's handle on the local MongoDB database.
 *
 * <p>This class does two unrelated things, and issue #4 separated them because they used to be one
 * call:
 *
 * <ol>
 *   <li><b>Overriding the database name</b>, which must happen before any service starts, on every
 *       demo launch.</li>
 *   <li><b>Dropping the database</b>, which now happens only when the user asks for it through
 *       Tools &gt; Delete Demo Data.</li>
 * </ol>
 *
 * <p>Before #4 the drop ran on every launch as a side effect of {@link #init()}, so the demo always
 * started clean.  <b>This is the first release in which demo data survives a restart</b> -- a
 * previous session's providers, buckets and sample statuses are all still there.  That is the whole
 * reason the delete action exists as a labeled menu item.
 *
 * <p>The name override cannot be lifted out of this class: {@code MongoClientBase.setMongoDatabaseName()}
 * is {@code protected static}, so only a subclass can call it.  That is precisely why the two
 * operations lived in one method to begin with, and why separating them needs care rather than a
 * deletion -- see {@link #prepareDemoDatabase()}.
 */
public class MongoInterface extends MongoSyncClient {

    // static variables
    private static final Logger logger = LogManager.getLogger();

    // constants
    public static final String DEMO_DATABASE_NAME = "dp-demo";

    /**
     * Overrides the database name globally, then initializes normally.
     *
     * <p><b>Deliberately does NOT drop the database.</b>  This override exists only for the name
     * override; dropping is {@link #dropDemoDatabase()}, reached from the Tools menu.  A drop here
     * would be back on the launch path, which is what #4 removed.
     */
    @Override
    public boolean init() {

        // override the default database name globally
        logger.info("overriding db name globally to: {}", DEMO_DATABASE_NAME);
        MongoClientBase.setMongoDatabaseName(DEMO_DATABASE_NAME);

        // Creates the database and its collections if they do not already exist, and leaves any
        // existing contents alone.
        return super.init();
    }

    /**
     * Closes the underlying MongoDB client, then finishes normally.
     *
     * <p><b>{@code MongoClientBase.fini()} is a no-op</b> -- it logs and returns true, and nothing
     * anywhere in dp-service ever calls {@code MongoClient.close()}.  That is tolerable for the
     * long-lived clients the services hold, which live as long as the process, but not for the
     * short-lived ones this class constructs: {@link #prepareDemoDatabase()} builds one on every
     * demo launch and {@link #deleteDemoDatabase()} two more per delete, each carrying its own
     * connection pool and monitoring threads.  Without this override every one of them leaks for
     * the life of the process, and a test that starts the application repeatedly accumulates them.
     *
     * <p>{@code mongoClient} is {@code protected} on {@code MongoSyncClient}, so a subclass is the
     * only place this can be fixed without changing dp-service.  Closing is idempotent and safe on
     * a client that was never connected, so this needs no guard beyond the null check.
     *
     * <p><b>This is a workaround in the wrong repo, and it is tracked upstream as dp-service #282.</b>
     * The leak is dp-service's: every current and future subclass needs this same override, which is
     * exactly the duplication {@code MongoClientBase} exists to prevent.  Measured there at 2 threads
     * per unclosed client plus its connection pool.  Remove this override once #282 lands and closes
     * the client in {@code MongoSyncClient.fini()}.
     */
    @Override
    public boolean fini() {

        if (mongoClient != null) {
            try {
                mongoClient.close();
            } catch (Exception e) {
                // Nothing the caller can do about it, and every caller is on a path where the
                // meaningful work has already succeeded or failed on its own terms.
                logger.warn("error closing mongo client: {}", e.getMessage(), e);
            }
            mongoClient = null;
        }

        return super.fini();
    }

    /**
     * Drops the demo database outright.  Every provider, bucket, dataset, annotation and sample
     * status in it is gone, irreversibly.
     *
     * <p>Requires an initialized client, since it reads {@code mongoClient}.  Callers that own the
     * lifecycle should use {@link #deleteDemoDatabase()} rather than managing init/fini themselves.
     */
    public void dropDemoDatabase() {
        logger.info("dropping database: {}", DEMO_DATABASE_NAME);
        MongoDatabase database = this.mongoClient.getDatabase(DEMO_DATABASE_NAME);
        database.drop();
    }

    /**
     * Establishes the demo database name and readies the database for the in-process services.
     *
     * <p>Called from {@link InprocessServiceEcosystem#init()} before any service starts, and it must
     * stay there: the name override is global and process-wide, so a service that initializes ahead
     * of it binds to the default (production) database name instead.  <b>Removing this call while
     * removing the drop would have pointed the demo at the production database</b> -- a worse bug
     * than the stale data it was fixing, and an easy one to make while the two operations shared a
     * method.
     */
    public static boolean prepareDemoDatabase() {

        final MongoInterface mongoInterface = new MongoInterface();

        // The client is closed on EVERY path below, including the two early returns.  It exists
        // only to apply the name override and create the collections; the services that follow
        // build their own.  See fini() for why the inherited no-op is not enough.
        try {
            if (!mongoInterface.init()) {
                logger.error("demo database preparation failed: mongo client init returned false");
                return false;
            }

            // Verify the invariant rather than trusting that init() applied it.  The override is a
            // process-global static set inside init(), so "we called the method that sets it" and "it
            // is actually set" are different claims -- and the gap between them is silent: every read
            // and write would agree on the WRONG database and nothing would error.
            final String effective = getMongoDatabaseName();
            if (!DEMO_DATABASE_NAME.equals(effective)) {
                logger.error(
                        "REFUSING TO START DEMO MODE: the effective database name is '{}', not '{}'. "
                                + "Demo mode would be reading and writing the deployment's database.",
                        effective, DEMO_DATABASE_NAME);
                return false;
            }

            logger.info("demo database prepared, effective database name verified as: {}", effective);
            return true;

        } finally {
            mongoInterface.fini();
        }
    }

    /**
     * The database name the process will actually use, for callers outside this package.
     *
     * <p>{@code MongoClientBase.getMongoDatabaseName()} is {@code protected static}, so this class
     * is one of the few places that can read it at all -- the same access that lets it perform the
     * override in the first place.  Exposed so the invariant is assertable from a test rather than
     * inferred from a log line.
     */
    public static String effectiveDatabaseName() {
        return getMongoDatabaseName();
    }

    /**
     * Drops the demo database and rebuilds its empty schema, managing the client lifecycle.
     *
     * <p>The entry point for Tools &gt; Delete Demo Data.  It is a static with its own short-lived
     * client rather than a method on the running ecosystem's interface because
     * {@link InprocessServiceEcosystem} does not retain one -- {@link #prepareDemoDatabase()}
     * discards its instance after init.
     *
     * <p>Returns true when the drop was issued and acknowledged.  A false return means the database
     * is untouched, and the caller must say so rather than reporting a delete that did not happen:
     * a UI that cleared its session state on a failed drop would show an empty application beside a
     * populated archive.
     *
     * <p><b>The rebuild is not optional, and it is why this method is more than a drop.</b>  The
     * services that are still running hold {@code MongoCollection} handles bound at their own init,
     * and they create indexes only there.  Dropping the database destroys the collections <i>and
     * every index on them</i> -- including the unique indexes on pvMetadata, configurations and
     * configurationActivations -- while MongoDB silently recreates a collection on the next write.
     * So without this rebuild the session continues against an unindexed database: ingestion, query
     * and PV stats all keep returning success, nothing errors, and nothing in the UI says anything.
     * Measured before the rebuild existed: buckets fell from 3 indexes to 1 and pvMetadata from 5 to
     * 0, and a subsequent ingest still reported success.  Before #4 the drop only ever ran at launch,
     * <i>ahead</i> of service init, so the indexes were always rebuilt immediately afterward; moving
     * the drop to a menu item is what opened this gap.
     *
     * <p>{@link #init()} is what does the rebuilding -- it creates every collection, runs schema
     * migrations and creates every index -- so the rebuild is a second {@code init()} against the
     * now-empty database rather than a separate copy of the schema.  A copy would be free to drift
     * from the real one, and the drift would be invisible for the same reason the missing indexes
     * were.
     *
     * <p><b>A failed rebuild is reported as a failed delete.</b>  The data really is gone by then, so
     * this is not strictly accurate -- but the alternative is worse in the direction that matters:
     * reporting success would leave the user on a silently degraded database with nothing to act on,
     * while reporting failure sends them to the log and to a restart, which is exactly what repairs
     * it.  A restart rebuilds the schema through the ordinary launch path.
     *
     * <p><b>The caller is responsible for establishing that the application is in demo mode</b>, and
     * {@code MainController.onDeleteDemoData()} re-checks it immediately before calling here.  This
     * method carries no mode check of its own because it has no access to the application's
     * configuration; a check it could not actually perform would read as a safeguard while providing
     * none.
     *
     * <p>Note what {@link #init()} does on the way in: it sets the database name <b>globally</b>, for
     * the whole process.  In demo mode that is already the value, so it is a no-op.  Reached in
     * deployment mode it would not be -- which is the real reason the caller's mode check matters,
     * beyond the drop itself.
     */
    public static boolean deleteDemoDatabase() {

        final MongoInterface mongoInterface = new MongoInterface();

        // init() applies the name override and connects; without it mongoClient is null and the
        // drop throws a NullPointerException rather than reporting a failure.
        if (!mongoInterface.init()) {
            logger.error("unable to initialize mongo client for demo database delete");
            return false;
        }

        try {
            mongoInterface.dropDemoDatabase();
        } catch (Exception e) {
            logger.error("error dropping demo database: {}", e.getMessage(), e);
            return false;
        } finally {
            mongoInterface.fini();
        }

        return rebuildDemoSchema();
    }

    /**
     * Recreates the demo database's collections and indexes after a drop, leaving it empty.
     *
     * <p>A fresh client rather than the one that performed the drop: that client's collection
     * handles refer to collections the drop destroyed, which is the very condition being repaired.
     * A new {@link #init()} binds to the recreated collections and creates every index on them.
     *
     * <p>See {@link #deleteDemoDatabase()} for why skipping this is not an option.
     */
    private static boolean rebuildDemoSchema() {

        final MongoInterface rebuildInterface = new MongoInterface();

        try {
            if (!rebuildInterface.init()) {
                logger.error(
                        "demo database was dropped but its schema could NOT be rebuilt; the running "
                                + "services are now bound to an unindexed database. Restart the "
                                + "application to repair it.");
                return false;
            }
            logger.info("demo database schema rebuilt (collections and indexes recreated, empty)");
            return true;
        } catch (Exception e) {
            logger.error(
                    "demo database was dropped but its schema could NOT be rebuilt ({}); the running "
                            + "services are now bound to an unindexed database. Restart the "
                            + "application to repair it.",
                    e.getMessage(), e);
            return false;
        } finally {
            rebuildInterface.fini();
        }
    }

}
