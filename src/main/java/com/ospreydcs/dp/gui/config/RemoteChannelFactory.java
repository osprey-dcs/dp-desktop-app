package com.ospreydcs.dp.gui.config;

import com.ospreydcs.dp.service.common.config.ConfigurationManager;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Builds the remote gRPC channels used in {@link AppMode#DEPLOYMENT}.
 *
 * <p>The channel shape follows dp-service's {@code IngestionServiceClientUtility}: a connect
 * string with {@link InsecureChannelCredentials} plus the three {@code GrpcClient.keepAlive*}
 * settings this application's {@code application.yml} already carries.  Only ingestion has such a
 * utility upstream, so rather than use it for one service and hand-roll the other three, all four
 * are built here in one place with identical settings.
 *
 * <p><b>Plaintext only.</b> TLS is a named follow-on to issue #4, not an oversight.
 */
public class RemoteChannelFactory {

    private static final Logger logger = LogManager.getLogger();

    // configuration keys and defaults, matching IngestionServiceClientUtility
    private static final String CFG_KEY_KEEP_ALIVE_TIME_SECONDS = "GrpcClient.keepAliveTimeSeconds";
    private static final int DEFAULT_KEEP_ALIVE_TIME_SECONDS = 45;
    private static final String CFG_KEY_KEEP_ALIVE_TIMEOUT_SECONDS = "GrpcClient.keepAliveTimeoutSeconds";
    private static final int DEFAULT_KEEP_ALIVE_TIMEOUT_SECONDS = 20;
    private static final String CFG_KEY_KEEP_ALIVE_WITHOUT_CALLS = "GrpcClient.keepAliveWithoutCalls";
    private static final boolean DEFAULT_KEEP_ALIVE_WITHOUT_CALLS = true;

    /**
     * Raised above gRPC's 4 MB default for the same reason the in-process channels raise it (see
     * {@code InprocessServiceBase}): the server's outgoing budget for a Query API V2 sample page
     * is 4,096,000 bytes, and that budget is measured on sample VALUES only -- it excludes the
     * timestamp list, per-column framing, column names and the response envelope, and it accounts
     * per whole bucket, so a page can overshoot it by up to one bucket.
     *
     * <p>That reasoning is a property of the SERVER's page budget, not of the transport, so it
     * applies to remote channels exactly as it does to in-process ones.  A remote channel left at
     * the default has no headroom at all, and the failure mode is a RESOURCE_EXHAUSTED on whichever
     * page overshoots, with nothing identifying the cause -- which presents as "Query V2 is broken
     * against real deployments" rather than as a client setting.  The server's budget is
     * environment-overridable, so this is deliberately generous rather than tuned to match it.
     */
    public static final int MAX_INBOUND_MESSAGE_SIZE_BYTES = 64 * 1024 * 1024;

    /** How long {@link #shutdown} waits for a channel to terminate before giving up on it. */
    public static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    protected static ConfigurationManager configMgr() {
        return ConfigurationManager.getInstance();
    }

    /** Builds a plaintext channel to the service at the given connect string ("host:port"). */
    public static ManagedChannel createChannel(String connectString) {

        final int keepAliveTimeSeconds = configMgr().getConfigInteger(
                CFG_KEY_KEEP_ALIVE_TIME_SECONDS, DEFAULT_KEEP_ALIVE_TIME_SECONDS);
        final int keepAliveTimeoutSeconds = configMgr().getConfigInteger(
                CFG_KEY_KEEP_ALIVE_TIMEOUT_SECONDS, DEFAULT_KEEP_ALIVE_TIMEOUT_SECONDS);
        final boolean keepAliveWithoutCalls = configMgr().getConfigBoolean(
                CFG_KEY_KEEP_ALIVE_WITHOUT_CALLS, DEFAULT_KEEP_ALIVE_WITHOUT_CALLS);

        logger.info("creating remote grpc channel to: {}", connectString);

        return Grpc.newChannelBuilder(connectString, InsecureChannelCredentials.create())
                .keepAliveTime(keepAliveTimeSeconds, TimeUnit.SECONDS)
                .keepAliveTimeout(keepAliveTimeoutSeconds, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(keepAliveWithoutCalls)
                .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE_BYTES)
                .build();
    }

    /**
     * Shuts a channel down, waiting a bounded time for it to terminate.
     *
     * <p>The wait is bounded because this runs on the JavaFX shutdown path: an unreachable or
     * wedged server must not keep the application alive.  A channel that does not terminate in
     * time is logged and abandoned -- the process is exiting regardless.
     */
    public static void shutdown(ManagedChannel channel, String description) {

        if (channel == null) {
            return;
        }

        try {
            channel.shutdown();
            if (!channel.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("remote grpc channel did not terminate within {} seconds: {}",
                        SHUTDOWN_TIMEOUT_SECONDS, description);
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("interrupted shutting down remote grpc channel: {}", description);
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
