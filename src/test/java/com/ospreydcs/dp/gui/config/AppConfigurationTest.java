package com.ospreydcs.dp.gui.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mode resolution and target defaults for issue #4.
 *
 * <p>Pure and free of a ConfigurationManager singleton by design, in the style of
 * {@code DpApplicationParamsTest} -- the singleton is process-global and initialized once from
 * system properties, so a test that set {@code dp.DpDesktopApp.mode} would either come too late
 * or leak into every other test in the JVM.
 */
public class AppConfigurationTest {

    @Test
    public void testModeExplicitValues() {
        assertEquals(AppMode.DEMO, AppMode.fromConfigValue("demo"));
        assertEquals(AppMode.DEPLOYMENT, AppMode.fromConfigValue("deployment"));
    }

    @Test
    public void testModeIsCaseInsensitiveAndTrimmed() {
        // The value can arrive from YAML or from a -Ddp.DpDesktopApp.mode command line, so it is
        // matched leniently rather than exactly.
        assertEquals(AppMode.DEPLOYMENT, AppMode.fromConfigValue("DEPLOYMENT"));
        assertEquals(AppMode.DEPLOYMENT, AppMode.fromConfigValue("Deployment"));
        assertEquals(AppMode.DEPLOYMENT, AppMode.fromConfigValue("  deployment  "));
        assertEquals(AppMode.DEMO, AppMode.fromConfigValue("Demo"));
    }

    @Test
    public void testAbsentModeIsDemo() {
        // An installation that never heard of this key keeps behaving as it does today.
        assertEquals(AppMode.DEMO, AppMode.fromConfigValue(null));
        assertEquals(AppMode.DEMO, AppMode.fromConfigValue(""));
        assertEquals(AppMode.DEMO, AppMode.fromConfigValue("   "));
    }

    @Test
    public void testUnrecognizedModeIsDemoNotDeployment() {
        // The direction matters: resolving a typo to DEPLOYMENT would turn a misspelling into a
        // connection attempt against whatever hosts the connect strings name.  Failing toward DEMO
        // is visible and harmless.
        assertEquals(AppMode.DEMO, AppMode.fromConfigValue("deploy"));
        assertEquals(AppMode.DEMO, AppMode.fromConfigValue("deployement"));
        assertEquals(AppMode.DEMO, AppMode.fromConfigValue("production"));
        assertEquals(AppMode.DEMO, AppMode.fromConfigValue("live"));
        assertEquals(AppMode.DEMO, AppMode.fromConfigValue("true"));
    }

    @Test
    public void testModePredicates() {
        assertTrue(AppMode.DEMO.isDemo());
        assertFalse(AppMode.DEMO.isDeployment());
        assertTrue(AppMode.DEPLOYMENT.isDeployment());
        assertFalse(AppMode.DEPLOYMENT.isDemo());
    }

    @Test
    public void testConfigurationExposesModeAndTargets() {

        final AppConfiguration deployment = new AppConfiguration(
                AppMode.DEPLOYMENT, "ingest:1", "query:2", "annotation:3", "stream:4");

        assertTrue(deployment.isDeployment());
        assertFalse(deployment.isDemo());
        assertEquals("ingest:1", deployment.getIngestionConnectString());
        assertEquals("query:2", deployment.getQueryConnectString());
        assertEquals("annotation:3", deployment.getAnnotationConnectString());
        assertEquals("stream:4", deployment.getIngestionStreamConnectString());
    }

    @Test
    public void testDescribeNamesTheTargetInDeploymentMode() {

        // describe() is the status bar, window title and startup log line, and in deployment mode
        // it is the only thing in the UI answering "which archive am I pointed at".  A label that
        // said only "Deployment" would leave that unanswerable.
        final AppConfiguration deployment = new AppConfiguration(
                AppMode.DEPLOYMENT, "archive.example.org:50051", "q:50052", "a:50053", "s:50054");
        assertTrue(deployment.describe().contains("archive.example.org:50051"));

        final AppConfiguration demo = new AppConfiguration(
                AppMode.DEMO, "localhost:50051", "localhost:50052", "localhost:50053", "localhost:50054");
        assertTrue(demo.describe().toLowerCase().contains("demo"));
        // The demo label must not name a connect string -- demo mode does not connect to one, and
        // showing "localhost:50051" there would claim a remote target that is not in use.
        assertFalse(demo.describe().contains("50051"));
    }

    @Test
    public void testDefaultConnectStringsMatchTheServicePorts() {
        // These are the ports application.yml assigns to the four servers.  A default that drifted
        // from them would connect a fresh deployment install to the wrong service and fail as a
        // protocol error rather than as a configuration mistake.
        assertEquals("localhost:50051", AppConfiguration.DEFAULT_INGESTION_CONNECT_STRING);
        assertEquals("localhost:50052", AppConfiguration.DEFAULT_QUERY_CONNECT_STRING);
        assertEquals("localhost:50053", AppConfiguration.DEFAULT_ANNOTATION_CONNECT_STRING);
        assertEquals("localhost:50054", AppConfiguration.DEFAULT_INGESTION_STREAM_CONNECT_STRING);
    }

    @Test
    public void testIngestionConnectStringKeyMatchesDpServiceConvention() {
        // The key dp-service's IngestionServiceClientUtility already defines.  Diverging would
        // leave two competing ways to address the same service.
        assertEquals(
                "GrpcClient.ingestionConnectString",
                AppConfiguration.CFG_KEY_INGESTION_CONNECT_STRING);
    }
}
