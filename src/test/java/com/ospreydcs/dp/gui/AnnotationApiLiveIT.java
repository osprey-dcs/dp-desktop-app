package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.annotation.Annotation;
import com.ospreydcs.dp.grpc.v1.common.DataColumn;
import com.ospreydcs.dp.grpc.v1.common.DataValue;
import com.ospreydcs.dp.grpc.v1.common.Timestamp;
import com.ospreydcs.dp.gui.component.AttributesListComponent;
import com.ospreydcs.dp.gui.component.TagsListComponent;
import com.ospreydcs.dp.gui.model.DataBlockDetail;
import com.ospreydcs.dp.gui.model.DataFrameDetails;
import com.ospreydcs.dp.gui.model.PvDetail;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import com.ospreydcs.dp.service.common.config.ConfigurationManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end verification of the Annotation API paths modernized for issue #42, against a real
 * in-process service ecosystem and a real MongoDB.
 *
 * Everything here was unreachable by the unit suite, and every defect these tests pin down was
 * one that compiled and ran while silently doing the wrong thing:
 *
 *  - getAnnotation() must return Calculations CONTENT inline while queryAnnotations() returns only
 *    calculationsId.  That asymmetry is the whole reason the builder loads through getAnnotation();
 *    loading through a query result plus a full-replace save destroys the stored calculations.
 *  - loading an annotation must put its tags and attributes where the save reads them.  When load
 *    and save disagreed, editing any unrelated field dropped the metadata permanently.
 *  - queryDataSets() / queryAnnotations() must follow nextPageToken, because an unset limit means
 *    the server's default page size rather than "everything".
 *  - a missing record must be distinguishable from an unreachable service (isReject, not isError).
 *
 * SKIPPED, not failed, when MongoDB is unreachable, so CI (which has no database) stays green --
 * see mongoIsReachable().  Named *IT so it is also excluded from a surefire run configured to
 * match only *Test, should the build ever adopt that convention.
 *
 * This test WRITES TO AND CLEANS UP the configured database (dp-demo by default).  Records are
 * namespaced with a per-run stamp and removed in @AfterAll, so a failure mid-run can leave stamped
 * records behind; they are inert and identifiable.
 *
 * Methods are ordered because they form one narrative: seed data, then the reads and the
 * load-edit-save round trip that depend on it.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AnnotationApiLiveIT {

    /** Per-run namespace, so concurrent or repeated runs cannot collide. */
    private static final String STAMP = String.valueOf(System.currentTimeMillis());

    private static final int PROBE_TIMEOUT_MILLIS = 1500;

    /**
     * The database the in-process ecosystem uses.  Set in code by
     * MongoInterface.prepareDemoDatabase() rather than in application.yml, so it cannot be read
     * from ConfigurationManager the way the connection settings can.
     */
    private static final String DATABASE_NAME = "dp-demo";

    /** Datasets created by the paging probe; enough to exceed any plausible default page size. */
    private static final int PAGING_PROBE_DATASETS = 120;

    private static DpApplication app;

    private static String pvA;
    private static String pvB;
    private static Instant dataBegin;
    private static Instant dataEnd;
    private static String datasetId;
    private static String annotationId;
    private static String calculationsId;

    // ------------------------------------------------------------------ gating

    /**
     * Whether the configured MongoDB accepts a connection.
     *
     * A plain socket probe against the host and port the app itself will use, read from
     * ConfigurationManager rather than hardcoded so a non-default configuration is honored.  The
     * probe is deliberately cheap and answers only "is anything listening": the alternative,
     * letting DpApplication.init() fail, takes far longer and reports a database outage as a test
     * failure rather than as an absent prerequisite.
     */
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
    static void startEcosystem() throws Exception {
        assumeTrue(mongoIsReachable(),
                "MongoDB is not reachable, so the live Annotation API tests are skipped; start "
                        + "MongoDB to run them");

        // the tag and attribute components are live JavaFX controls
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
     * Removes this run's records.
     *
     * Best effort by design: a cleanup failure must not fail the build.  The assertions have
     * already been reported by this point, and leftover records are inert and identifiable by the
     * run stamp -- turning a cleanup hiccup into a red build would report a housekeeping problem
     * as a code defect.
     *
     * Cleanup goes through the driver rather than the API because DpApplication exposes no delete
     * wrappers for datasets or annotations (the service implements no such RPC), and the ecosystem
     * does not expose its Mongo client.  Everything created here carries STAMP in its name, so the
     * deletes cannot touch a developer's own data.
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

            // names carry the stamp; ids and pv names do too, in their own fields
            final var stamped = com.mongodb.client.model.Filters.regex("name", STAMP);

            database.getCollection("dataSets").deleteMany(stamped);
            database.getCollection("annotations").deleteMany(stamped);
            database.getCollection("providers").deleteMany(stamped);
            database.getCollection("buckets").deleteMany(
                    com.mongodb.client.model.Filters.regex("pvName", STAMP));
            database.getCollection("pvStats").deleteMany(
                    com.mongodb.client.model.Filters.regex("_id", STAMP));

            // Calculations records are reached through the annotations that referenced them, which
            // are already gone by here, so they are matched on the id captured during the run.
            if (calculationsId != null && !calculationsId.isEmpty()) {
                database.getCollection("calculations").deleteMany(
                        com.mongodb.client.model.Filters.eq("_id",
                                new org.bson.types.ObjectId(calculationsId)));
            }

        } catch (Exception cleanupFailed) {
            System.err.println("AnnotationApiLiveIT cleanup failed (records tagged " + STAMP
                    + " may remain, and are inert): " + cleanupFailed);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static DataFrameDetails frameDetails(String name) {
        return new DataFrameDetails(
                name,
                List.of(Timestamp.newBuilder()
                        .setEpochSeconds(Instant.now().getEpochSecond())
                        .setNanoseconds(0)
                        .build()),
                List.of(DataColumn.newBuilder()
                        .setName(name + "-col")
                        .addDataValues(DataValue.newBuilder().setDoubleValue(1.5))
                        .build()));
    }

    private static DataBlockDetail seedBlock() {
        return new DataBlockDetail(List.of(pvA, pvB), dataBegin, dataEnd);
    }

    // ------------------------------------------------------------------ [0] seed

    /**
     * Ingests real data, because saveDataSet() rejects a data block naming PVs that have no
     * metadata ("no PV metadata found for names: ...").  PV metadata is derived by aggregation
     * over ingested buckets, so there is no way to create a referenceable dataset without it.
     */
    @Test
    @Order(0)
    public void seedDataIsIngestedAndPvMetadataBecomesAvailable() throws Exception {
        pvA = "it-pv-a-" + STAMP;
        pvB = "it-pv-b-" + STAMP;

        final var registration = app.registerProvider(
                "it-provider-" + STAMP, "annotation API live IT",
                List.of("integration-test"), Map.of("run", STAMP));
        assertNotNull(registration, "registerProvider returned null");
        assertFalse(registration.isError, "registerProvider failed: " + registration.msg);

        dataEnd = Instant.now().minusSeconds(5);
        dataBegin = dataEnd.minusSeconds(10);

        final var ingestion = app.generateAndIngestData(
                dataBegin, dataEnd, null,
                List.of(new PvDetail(pvA, "float", 10, "1.0", "0.5"),
                        new PvDetail(pvB, "float", 10, "2.0", "0.5")),
                5, new ArrayList<>(), false);
        assertNotNull(ingestion, "generateAndIngestData returned null");
        assertFalse(ingestion.isError, "generateAndIngestData failed: " + ingestion.msg);

        // PV metadata appears a moment after ingestion returns, so wait for it rather than racing
        // it -- an immediate saveDataSet() is rejected for PVs whose metadata has not landed yet.
        String lastRejection = "never attempted";
        for (int attempt = 0; attempt < 40; attempt++) {
            final var probe = app.saveDataSet(
                    null, "it-metadata-probe-" + STAMP + "-" + attempt, "metadata probe",
                    List.of(seedBlock()));
            if (probe != null && !probe.resultStatus.isError) {
                return;
            }
            lastRejection = probe == null ? "null result" : probe.resultStatus.msg;
            Thread.sleep(500);
        }
        throw new AssertionError(
                "PV metadata for the ingested PVs did not become available within 20s; last "
                        + "saveDataSet rejection: " + lastRejection);
    }

    // ------------------------------------------------------------------ [1] getDataSet (P3.2)

    @Test
    @Order(1)
    public void getDataSetReturnsTheSavedDatasetWithItsDataBlocks() {
        final var save = app.saveDataSet(
                null, "it-ds-" + STAMP, "annotation API live IT dataset", List.of(seedBlock()));
        assertNotNull(save, "saveDataSet returned null");
        assertFalse(save.resultStatus.isError, "saveDataSet failed: " + save.resultStatus.msg);
        datasetId = save.datasetId;

        final var got = app.getDataSet(datasetId);
        assertNotNull(got, "getDataSet returned null");
        assertFalse(got.resultStatus.isError, "getDataSet failed: " + got.resultStatus.msg);
        assertNotNull(got.dataSet, "getDataSet returned no dataSet");

        assertEquals("it-ds-" + STAMP, got.dataSet.getName());
        assertEquals(1, got.dataSet.getDataBlocksCount(), "data block count");
        assertTrue(got.dataSet.getDataBlocks(0).getPvNamesList().contains(pvA),
                "data block lost its PV names: " + got.dataSet.getDataBlocks(0).getPvNamesList());
    }

    // -------------------------------------------- [2] getAnnotation vs queryAnnotations (P2.3)

    /**
     * The asymmetry the whole load path depends on: getAnnotation() returns Calculations content
     * inline, queryAnnotations() returns calculationsId alone.
     *
     * If getAnnotation() ever stopped returning content, the builder would load with no
     * calculations and the next full-replace save would destroy them silently -- which is exactly
     * the defect repaired for #42.  If queryAnnotations() started returning content again, the
     * presence column and fetch-on-click would still work, but the N+1 the API change removed
     * would be back.
     */
    @Test
    @Order(2)
    public void getAnnotationReturnsCalculationsInlineWhileQueryAnnotationsReturnsOnlyTheId() {
        assertNotNull(datasetId, "the dataset from the previous test is required");

        final var save = app.saveAnnotation(
                null, "it-ann-" + STAMP, List.of(datasetId), null,
                "annotation API live IT annotation",
                List.of("it-tag-alpha", "it-tag-beta"),
                Map.of("it-key-1", "it-value-1"),
                List.of(frameDetails("it-frame-one"), frameDetails("it-frame-two")));
        assertNotNull(save, "saveAnnotation returned null");
        assertFalse(save.resultStatus.isError,
                "saveAnnotation failed: " + save.resultStatus.msg);
        annotationId = save.annotationId;

        final var got = app.getAnnotation(annotationId);
        assertNotNull(got, "getAnnotation returned null");
        assertFalse(got.resultStatus.isError, "getAnnotation failed: " + got.resultStatus.msg);
        assertNotNull(got.annotation, "getAnnotation returned no annotation");

        final Annotation annotation = got.annotation;
        calculationsId = annotation.getCalculationsId();

        assertTrue(annotation.hasCalculations(),
                "getAnnotation returned no Calculations content; loading this into the builder "
                        + "would drop the calculations and the next save would destroy them");
        assertEquals(2, annotation.getCalculations().getCalculationDataFramesCount(),
                "calculation frame count from getAnnotation");
        assertFalse(annotation.getCalculationsId().isEmpty(),
                "getAnnotation returned no calculationsId");

        // the comment -> description rename (P3.1) through a real round trip
        assertEquals("annotation API live IT annotation", annotation.getDescription(),
                "description did not survive the round trip");

        final var queried =
                app.queryAnnotations(annotationId, null, null, null, null, null, null, null);
        assertNotNull(queried, "queryAnnotations returned null");
        assertEquals(1, queried.records.size(), "queryAnnotations record count");

        final Annotation fromQuery = queried.records.get(0);
        assertEquals(0, fromQuery.getCalculations().getCalculationDataFramesCount(),
                "queryAnnotations returned Calculations content; the builder must not load "
                        + "through it, and resolving frame names per row would rebuild the N+1 "
                        + "fan-out dp-grpc #132 removed");
        assertFalse(fromQuery.getCalculationsId().isEmpty(),
                "queryAnnotations returned no calculationsId, so the Calculations presence "
                        + "column would render blank");
    }

    // ------------------------------- [3] the review fix: load-edit-save preserves metadata

    /**
     * The defect this guards: loadFromAnnotation() populated ViewModel-owned lists while the save
     * read the injected components, so a loaded annotation's tags and attributes went where the
     * save never looked.  With a full-replace upsert, editing one unrelated field wrote them back
     * as absent -- no error, nothing in the UI.
     *
     * This drives the same sequence the controller does (load into the builder, edit one field,
     * save from the component values) and then reads the archive back, which is the only place
     * the loss was ever visible.
     */
    @Test
    @Order(3)
    public void loadEditSavePreservesTagsAttributesAndCalculations() throws Exception {
        assertNotNull(annotationId, "the annotation from the previous test is required");

        final var loaded = app.getAnnotation(annotationId);
        assertNotNull(loaded, "getAnnotation returned null");
        assertNotNull(loaded.annotation, "getAnnotation returned no annotation");

        // load into a builder wired exactly as DataExploreController wires it, then read back the
        // component values the save path reads
        final List<String> componentTags = new ArrayList<>();
        final List<String> componentAttributes = new ArrayList<>();
        final List<DataFrameDetails> builderCalculations = new ArrayList<>();
        final String[] editedDescription = new String[1];

        FxToolkitSupport.runOnFxThread(() -> {
            final TagsListComponent tags = new TagsListComponent();
            final AttributesListComponent attributes = new AttributesListComponent();
            final AnnotationBuilderViewModel viewModel = new AnnotationBuilderViewModel();
            viewModel.setTagsComponent(tags);
            viewModel.setAttributesComponent(attributes);

            viewModel.loadFromAnnotation(loaded.annotation);

            // the user edits one unrelated field
            viewModel.setDescription("edited by annotation API live IT");

            componentTags.addAll(tags.getTags());
            componentAttributes.addAll(attributes.getAttributes());
            builderCalculations.addAll(viewModel.getCalculationsDataFrames());
            editedDescription[0] = viewModel.getDescription();
        });

        assertTrue(componentTags.containsAll(List.of("it-tag-alpha", "it-tag-beta")),
                "loaded tags did not reach the component the save reads (got " + componentTags
                        + "), so the save would drop them");
        assertTrue(componentAttributes.contains("it-key-1=it-value-1"),
                "loaded attributes did not reach the component the save reads (got "
                        + componentAttributes + ")");
        assertEquals(2, builderCalculations.size(), "loaded calculation frame count");

        final Map<String, String> attributeMap = new LinkedHashMap<>();
        for (String attribute : componentAttributes) {
            attributeMap.put(
                    AttributesListComponent.getKeyFromAttribute(attribute),
                    AttributesListComponent.getValueFromAttribute(attribute));
        }

        final var resave = app.saveAnnotation(
                annotationId, "it-ann-" + STAMP, List.of(datasetId), null,
                editedDescription[0],
                componentTags.isEmpty() ? null : componentTags,
                attributeMap.isEmpty() ? null : attributeMap,
                builderCalculations);
        assertNotNull(resave, "re-save returned null");
        assertFalse(resave.resultStatus.isError,
                "re-save failed: " + resave.resultStatus.msg);

        // read the archive back: the only place the loss was visible
        final var after = app.getAnnotation(annotationId);
        assertNotNull(after, "getAnnotation after re-save returned null");
        assertNotNull(after.annotation, "getAnnotation after re-save returned no annotation");

        assertTrue(after.annotation.getTagsList().containsAll(List.of("it-tag-alpha", "it-tag-beta")),
                "tags did NOT survive the edit-and-save round trip (archived: "
                        + after.annotation.getTagsList() + ") -- this is the silent data loss");
        assertEquals(1, after.annotation.getAttributesCount(),
                "attributes did NOT survive the edit-and-save round trip (archived: "
                        + after.annotation.getAttributesList() + ")");
        assertEquals("it-key-1", after.annotation.getAttributes(0).getName());
        assertEquals(2, after.annotation.getCalculations().getCalculationDataFramesCount(),
                "calculations did NOT survive the edit-and-save round trip");
        assertEquals("edited by annotation API live IT", after.annotation.getDescription(),
                "the edit itself was not applied");

        // saveAnnotation() is a full-replace upsert, so the re-save allocated a NEW Calculations
        // record; a calculationsId held across a save is stale.  Re-read it for the next test.
        final String reallocated = after.annotation.getCalculationsId();
        assertFalse(reallocated.isEmpty(), "no calculationsId after re-save");
        calculationsId = reallocated;
    }

    // ------------------------------------------------- [4] getCalculations fetch (P2.1)

    /**
     * Backs the Calculations presence hyperlink: frame names are not available at query time, so
     * the column shows presence and this fetch is what resolves the names on click.
     */
    @Test
    @Order(4)
    public void getCalculationsResolvesFrameNamesAndUnwrapsTheNestedDataFrame() {
        assertNotNull(calculationsId, "the calculationsId from the previous test is required");
        assertFalse(calculationsId.isEmpty(), "calculationsId is empty");

        final var got = app.getCalculations(calculationsId);
        assertNotNull(got, "getCalculations returned null");
        assertFalse(got.resultStatus.isError,
                "getCalculations failed: " + got.resultStatus.msg);
        assertNotNull(got.calculations, "getCalculations returned no calculations");

        assertEquals(2, got.calculations.getCalculationDataFramesCount(),
                "fetched calculation frame count");

        final List<String> frameNames = new ArrayList<>();
        got.calculations.getCalculationDataFramesList()
                .forEach(frame -> frameNames.add(frame.getName()));
        assertTrue(frameNames.containsAll(List.of("it-frame-one", "it-frame-two")),
                "frame names did not resolve from the fetch: " + frameNames);

        // the P1.3 nesting unwrap: a CalculationsDataFrame is now a name plus a nested DataFrame
        final DataFrameDetails details = DataFrameDetails.fromCalculationsDataFrame(
                got.calculations.getCalculationDataFrames(0));
        assertNotNull(details, "fromCalculationsDataFrame returned null");
        assertFalse(details.getDataColumns().isEmpty(),
                "nested DataFrame unwrap lost the data columns");
        assertFalse(details.getTimestamps().isEmpty(),
                "nested DataFrame unwrap lost the timestamps");
    }

    // ------------------------------------------------- [5] isReject distinguishes not-found

    /**
     * The single-record getters report a missing record as a REJECTION, not as an empty successful
     * result and not as an error.  Every load path branches on that, and isError alone cannot tell
     * "does not exist" from "the service is unreachable".
     */
    @Test
    @Order(5)
    public void missingRecordsAreRejectedRatherThanReportedAsErrors() {
        final String missingId = "000000000000000000000000";

        final var dataSet = app.getDataSet(missingId);
        assertNotNull(dataSet, "getDataSet returned null");
        assertTrue(dataSet.isReject(),
                "getDataSet on a missing id did not report a rejection (isError="
                        + dataSet.resultStatus.isError + ")");

        final var annotation = app.getAnnotation(missingId);
        assertNotNull(annotation, "getAnnotation returned null");
        assertTrue(annotation.isReject(),
                "getAnnotation on a missing id did not report a rejection (isError="
                        + annotation.resultStatus.isError + ")");

        final var calculations = app.getCalculations(missingId);
        assertNotNull(calculations, "getCalculations returned null");
        assertTrue(calculations.isReject(),
                "getCalculations on a missing id did not report a rejection (isError="
                        + calculations.resultStatus.isError + ")");
    }

    // ------------------------------------------------------------ [6] transparent paging (P2.2)

    /**
     * The defect: the queries became paged and an unset limit means the server's DEFAULT PAGE
     * SIZE, not "everything", so the explore views reported a page count as though it were a
     * total.  This creates more datasets than any plausible default page size and asserts they all
     * come back in one list.
     */
    @Test
    @Order(6)
    public void pagedQueryFollowsNextPageTokenAndReturnsEveryMatchingRecord() {
        final String probeName = "it-page-probe-" + STAMP;

        for (int i = 0; i < PAGING_PROBE_DATASETS; i++) {
            final var save = app.saveDataSet(
                    null, probeName + "-" + i, "paging probe", List.of(seedBlock()));
            assertNotNull(save, "saveDataSet returned null on probe " + i);
            assertFalse(save.resultStatus.isError,
                    "paging probe dataset " + i + " failed: " + save.resultStatus.msg);
        }

        // nameDescription is the text criterion; the probe name is distinctive enough to isolate
        final var paged = app.queryDataSets(null, null, probeName, null);
        assertNotNull(paged, "queryDataSets returned null");

        assertTrue(paged.records.size() >= PAGING_PROBE_DATASETS,
                "paged query returned " + paged.records.size() + " of " + PAGING_PROBE_DATASETS
                        + " records; a smaller number means nextPageToken is not being followed "
                        + "and the explore views would state a wrong total");
        assertFalse(paged.truncated,
                "a result well below QUERY_RESULT_CAP (" + DpApplication.QUERY_RESULT_CAP
                        + ") was marked truncated");
        assertTrue(paged.describeCount("dataset").startsWith("found "),
                "an untruncated result should describe a definite count, got: "
                        + paged.describeCount("dataset"));
    }
}
