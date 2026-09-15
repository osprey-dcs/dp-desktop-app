package com.ospreydcs.dp.service.inprocess;

import com.ospreydcs.dp.service.common.config.ConfigurationManager;
import io.grpc.ManagedChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InprocessServiceEcosystem {

    // static variables
    private static final Logger logger = LogManager.getLogger();

    // constants

    // instance variables
    public InprocessIngestionService ingestionService = new InprocessIngestionService();
    public InprocessQueryService queryService = new InprocessQueryService();
    public InprocessAnnotationService annotationService = new InprocessAnnotationService();
    public InprocessIngestionStreamService ingestionStreamService = new InprocessIngestionStreamService();

    protected static ConfigurationManager configMgr() {
        return ConfigurationManager.getInstance();
    }

    public boolean init() {

        logger.debug("InprocessGrpcServiceEcosystem init");

        // Globally changes the default database name to dp-demo, and VERIFIES that it took.
        // This must stay the first thing that happens here: the override is process-global, so any
        // service that inits ahead of it binds to the deployment's default database name instead.
        // A false return means the name is not dp-demo, and starting anyway would run the demo
        // against the deployment's data with nothing in the UI indicating it.
        if (!MongoInterface.prepareDemoDatabase()) {
            logger.error("aborting in-process ecosystem init: demo database could not be prepared");
            return false;
        }

        // init ingestion service
        if (!ingestionService.init()) {
            return false;
        }
        ManagedChannel ingestionChannel = ingestionService.getIngestionChannel();

        // init query service
        if (!queryService.init()) {
            return false;
        }

        // init annotation service
        if (!annotationService.init()) {
            return false;
        }

        // init ingestion stream service
        if (!ingestionStreamService.init(ingestionChannel)) {
            return false;
        }

        return true;
    }

    public void fini() {

        logger.debug("InprocessGrpcServiceEcosystem tearDown");

        ingestionStreamService.fini();
        annotationService.fini();
        queryService.fini();
        ingestionService.fini();
    }

}
