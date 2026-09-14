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
    public static void prepareDemoDatabase() {
        MongoInterface mongoInterface = new MongoInterface();
        mongoInterface.init();
    }

    /**
     * Drops the demo database, managing the client lifecycle around the drop.
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
            return true;
        } catch (Exception e) {
            logger.error("error dropping demo database: {}", e.getMessage(), e);
            return false;
        } finally {
            mongoInterface.fini();
        }
    }

}
