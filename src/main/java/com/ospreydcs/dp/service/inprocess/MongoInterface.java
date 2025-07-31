package com.ospreydcs.dp.service.inprocess;

import com.mongodb.client.MongoDatabase;
import com.ospreydcs.dp.service.common.mongo.MongoClientBase;
import com.ospreydcs.dp.service.common.mongo.MongoSyncClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MongoInterface extends MongoSyncClient {

    // static variables
    private static final Logger logger = LogManager.getLogger();

    // constants
    public static final String DEMO_DATABASE_NAME = "dp-demo";

    @Override
    public boolean init() {

        // override the default database name globally
        logger.info("overriding db name globally to: {}", DEMO_DATABASE_NAME);
        MongoClientBase.setMongoDatabaseName(DEMO_DATABASE_NAME);

        // init so we have database client for dropping existing db
        super.init();
        dropDemoDatabase();
        super.fini();

        // re-initialize to recreate db and collections as needed
        return super.init();
    }

    public void dropDemoDatabase() {
        logger.info("dropping database: {}", DEMO_DATABASE_NAME);
        MongoDatabase database = this.mongoClient.getDatabase(DEMO_DATABASE_NAME);
        database.drop();
    }

    public static void prepareDemoDatabase() {
        MongoInterface mongoInterface = new MongoInterface();
        mongoInterface.init();
    }

}
