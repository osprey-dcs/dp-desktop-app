package com.ospreydcs.dp.gui.config;

import com.ospreydcs.dp.service.common.config.ConfigurationManager;
import com.ospreydcs.dp.service.inprocess.MongoInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The application's launch-time configuration: which mode to run in, and in deployment mode,
 * where the four remote services are.
 *
 * <p>Values are read through dp-service's {@link ConfigurationManager}, which already supports a
 * whole-file override ({@code -Ddp.config=<path>} or the {@code DP.CONFIG} environment variable)
 * and per-key overrides ({@code -Ddp.DpDesktopApp.mode=deployment}).  So a deployment install
 * ships its own {@code application.yml} and needs no build changes.
 *
 * <p>This class is a plain value holder once constructed.  {@link #fromConfiguration()} does the
 * reading; the string-taking constructor exists so mode and target resolution are unit-testable
 * without a {@code ConfigurationManager} singleton, in the style of {@code DpApplication}'s
 * package-private static helpers.
 */
public class AppConfiguration {

    private static final Logger logger = LogManager.getLogger();

    // configuration keys and defaults

    public static final String CFG_KEY_MODE = "DpDesktopApp.mode";

    /**
     * Connect strings rather than hostname + port, following the one precedent that exists:
     * dp-service's {@code IngestionServiceClientUtility} already defines
     * {@code GrpcClient.ingestionConnectString}.  Note that {@code GrpcClient.hostname} in
     * application.yml is NOT that precedent -- nothing in dp-service reads it to build a client
     * channel.  A connect string also expresses targets a host+port pair cannot, such as
     * DNS-based name resolution.
     */
    public static final String CFG_KEY_INGESTION_CONNECT_STRING = "GrpcClient.ingestionConnectString";
    public static final String CFG_KEY_QUERY_CONNECT_STRING = "GrpcClient.queryConnectString";
    public static final String CFG_KEY_ANNOTATION_CONNECT_STRING = "GrpcClient.annotationConnectString";
    public static final String CFG_KEY_INGESTION_STREAM_CONNECT_STRING = "GrpcClient.ingestionStreamConnectString";

    public static final String DEFAULT_INGESTION_CONNECT_STRING = "localhost:50051";
    public static final String DEFAULT_QUERY_CONNECT_STRING = "localhost:50052";
    public static final String DEFAULT_ANNOTATION_CONNECT_STRING = "localhost:50053";
    public static final String DEFAULT_INGESTION_STREAM_CONNECT_STRING = "localhost:50054";

    // instance variables
    private final AppMode mode;
    private final String ingestionConnectString;
    private final String queryConnectString;
    private final String annotationConnectString;
    private final String ingestionStreamConnectString;

    public AppConfiguration(
            AppMode mode,
            String ingestionConnectString,
            String queryConnectString,
            String annotationConnectString,
            String ingestionStreamConnectString
    ) {
        this.mode = mode;
        this.ingestionConnectString = ingestionConnectString;
        this.queryConnectString = queryConnectString;
        this.annotationConnectString = annotationConnectString;
        this.ingestionStreamConnectString = ingestionStreamConnectString;
    }

    /** Reads the application configuration through {@link ConfigurationManager}. */
    public static AppConfiguration fromConfiguration() {

        final ConfigurationManager configMgr = ConfigurationManager.getInstance();

        final AppConfiguration configuration = new AppConfiguration(
                AppMode.fromConfigValue(configMgr.getConfigString(CFG_KEY_MODE, null)),
                configMgr.getConfigString(CFG_KEY_INGESTION_CONNECT_STRING, DEFAULT_INGESTION_CONNECT_STRING),
                configMgr.getConfigString(CFG_KEY_QUERY_CONNECT_STRING, DEFAULT_QUERY_CONNECT_STRING),
                configMgr.getConfigString(CFG_KEY_ANNOTATION_CONNECT_STRING, DEFAULT_ANNOTATION_CONNECT_STRING),
                configMgr.getConfigString(
                        CFG_KEY_INGESTION_STREAM_CONNECT_STRING, DEFAULT_INGESTION_STREAM_CONNECT_STRING)
        );

        logger.info("application configuration: {}", configuration.describe());

        return configuration;
    }

    public AppMode getMode() {
        return mode;
    }

    public boolean isDemo() {
        return mode.isDemo();
    }

    public boolean isDeployment() {
        return mode.isDeployment();
    }

    public String getIngestionConnectString() {
        return ingestionConnectString;
    }

    public String getQueryConnectString() {
        return queryConnectString;
    }

    public String getAnnotationConnectString() {
        return annotationConnectString;
    }

    public String getIngestionStreamConnectString() {
        return ingestionStreamConnectString;
    }

    /**
     * One-line summary naming the archive the application is pointed at, for the status bar, the
     * window title and the startup log.  "Which archive am I looking at" is otherwise unanswerable
     * from the UI, and in deployment mode it is the question that matters most.
     *
     * <p><b>The QUERY connect string stands in for the target as a whole, not the ingestion one.</b>
     * The four services are expected to belong to one deployment, and a label carrying all four
     * would not fit the status bar -- so the label names the service whose target the user can
     * actually verify from what they see.  Deployment mode disables every ingestion path, so the
     * ingestion target is the one service the application never calls there: a label naming it
     * would be a host whose correctness has no observable consequence, printed beside results
     * fetched from a different host that the label never mentions.  A mismatched set is a
     * configuration error the startup log shows in full.
     */
    public String describe() {
        return switch (mode) {
            // Names the database for the same reason deployment names its host: "which archive am I
            // looking at" must be answerable from the UI in BOTH modes.  A demo label that named
            // nothing meant a demo pointed at the wrong database looked exactly like a correct one.
            case DEMO -> "Demo (in-process) — " + MongoInterface.DEMO_DATABASE_NAME;
            case DEPLOYMENT -> "Deployment — " + queryConnectString;
        };
    }
}
