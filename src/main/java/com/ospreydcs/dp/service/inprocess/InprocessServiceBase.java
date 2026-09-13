package com.ospreydcs.dp.service.inprocess;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public abstract class InprocessServiceBase<T extends BindableService> {

    // static variables
    private static final Logger logger = LogManager.getLogger();

    /** See the channel construction in init() for why the gRPC default is not enough. */
    private static final int MAX_INBOUND_MESSAGE_SIZE_BYTES = 64 * 1024 * 1024;

    // common instance variables
    protected T service;
    protected T serviceMock;
    protected ManagedChannel channel;

    protected abstract boolean initService();
    protected abstract void finiService();
    protected abstract T createServiceMock(T service);

    public boolean init() {

        if (!initService()) {
            logger.error("initService() failed for: {}", getServiceName());
            return false;
        }

        serviceMock = createServiceMock(service);

        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();

        // Create a server, add service, start, and register for automatic graceful shutdown.
        try {
            getGrpcCleanupRule().register(InProcessServerBuilder
                    .forName(serverName).directExecutor().addService(serviceMock).build().start());
        } catch (IOException e) {
            logger.error("IOException creating grpc server for: {}", getServiceName());
            return false;
        }

        // Create a client channel and register for automatic graceful shutdown.
        //
        // maxInboundMessageSize is raised above gRPC's 4 MB default because the server's own
        // outgoing budget for a Query API V2 sample page is 4,096,000 bytes, and that budget is
        // measured on sample VALUES only -- it excludes the timestamp list, per-column framing,
        // column names and the response envelope, and it accounts per whole bucket, so a page can
        // overshoot it by up to one bucket.  A client left at the default therefore has no
        // headroom at all, and the failure mode is a RESOURCE_EXHAUSTED on the page that overshoots
        // rather than anything identifying the cause.  The server's budget is environment-
        // overridable, so this is deliberately generous rather than tuned to match it.
        channel = getGrpcCleanupRule().register(
                InProcessChannelBuilder.forName(serverName)
                        .directExecutor()
                        .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE_BYTES)
                        .build());

        return true;
    }

    public void fini() {
        logger.info("shutting down service: {}", getServiceName());
        finiService();
        service = null;
        serviceMock = null;
        channel = null;
    }

    public ManagedChannel getChannel() {
        return this.channel;
    }

    protected abstract GrpcCleanupRule getGrpcCleanupRule();
    protected abstract String getServiceName();

}
