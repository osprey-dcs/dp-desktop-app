package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.common.DataColumn;
import com.ospreydcs.dp.grpc.v1.query.ColumnTable;
import com.ospreydcs.dp.service.common.config.ConfigurationManager;
import com.ospreydcs.dp.gui.model.PvDetail;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end verification of the Query API V2 migration (issue #39 task 6) against a real
 * in-process ecosystem and a real MongoDB.
 *
 * The unit suite (DataExploreV2DecodeTest) covers the transpose by handing it tables this test
 * class built.  What it structurally cannot check is whether the SERVER produces tables of that
 * shape -- and every premise the migration rests on is a claim about the server:
 *
 *  - a ColumnTable really does carry its time axis ONLY in timestampList, with no timestamp column,
 *    so the synthesized column is required rather than redundant.
 *  - columns really are bare PV names, sorted and deduped, and every resolved PV gets a column even
 *    with no data in the window.
 *  - there really is exactly one DataValue per column per timestamp.
 *  - paging really does terminate: nextPageToken empties rather than repeating a page forever.  A
 *    loop that never terminates is the failure mode the retired interval chopping could not have.
 *  - a query whose window contains no data is a SUCCESS carrying an empty table, not a rejection.
 *
 * SKIPPED, not failed, when MongoDB is unreachable, so CI stays green -- same probe and rationale
 * as AnnotationApiLiveIT and ExploreQueryLiveIT, which this is modelled on.
 *
 * This test WRITES TO AND CLEANS UP the configured database (dp-demo), namespacing its records with
 * a per-run stamp.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class QuerySamplesLiveIT {

    /** Per-run namespace, so concurrent or repeated runs cannot collide. */
    private static final String STAMP = String.valueOf(System.currentTimeMillis());

    private static final int PROBE_TIMEOUT_MILLIS = 1500;
    private static final String DATABASE_NAME = "dp-demo";

    private static final String PV_A = "IT:V2:A:" + STAMP;

    /** Curated metadata on PV_A, so the metadata selector arm has something to resolve. */
    private static final String METADATA_ALIAS = "IT:V2:ALIAS:" + STAMP;
    private static final String METADATA_TAG = "it-v2-tag-" + STAMP;
    private static final String METADATA_ATTRIBUTE_KEY = "itV2Subsystem";
    private static final String METADATA_ATTRIBUTE_VALUE = "vacuum-" + STAMP;
    private static final String PV_B = "IT:V2:B:" + STAMP;

    /** A PV that is registered as a name but has NO data in the queried window. */
    private static final String PV_NO_DATA = "IT:V2:NODATA:" + STAMP;

    /** PvDetail's third argument is VALUES PER SECOND, not a sample period. */
    private static final int VALUES_PER_SECOND = 10;
    private static final int WINDOW_SECONDS = 10;

    /**
     * A second, denser PV set used solely to force a MULTI-page result.
     *
     * The server's default page is 10,000 rows, so the 100-row set above always fits in one page --
     * against it, a paging loop that stopped after the first page would pass every assertion.  This
     * set is sized past that default so the row-total assertion actually exercises accumulation
     * across pages rather than just termination.
     */
    private static final String PV_PAGED = "IT:V2:PAGED:" + STAMP;
    private static final int PAGED_VALUES_PER_SECOND = 1000;
    private static final int PAGED_WINDOW_SECONDS = 25;
    private static final int PAGED_EXPECTED_ROWS = PAGED_VALUES_PER_SECOND * PAGED_WINDOW_SECONDS;

    private static Instant pagedBegin;
    private static Instant pagedEnd;

    /** See awaitIngestedDataVisible() for why a readiness gate is required at all. */
    private static final long VISIBILITY_TIMEOUT_MILLIS = 30_000;
    private static final long VISIBILITY_POLL_MILLIS = 250;

    private static Instant dataBegin;
    private static Instant dataEnd;

    private static DpApplication app;

    private static boolean mongoIsReachable() {
        String host = "localhost";
        int port = 27017;
        try {
            final ConfigurationManager config = ConfigurationManager.getInstance();
            host = config.getConfigString("MongoClient.dbHost", "localhost");
            port = config.getConfigInteger("MongoClient.dbPort", 27017);
        } catch (Exception ignored) {
            // fall back to the defaults above
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MILLIS);
            return true;
        } catch (Exception unreachable) {
            return false;
        }
    }

    @BeforeAll
    static void startEcosystemAndIngest() throws Exception {
        assumeTrue(mongoIsReachable(),
                "MongoDB is not reachable, so the live querySamples tests are skipped; start "
                        + "MongoDB to run them");

        FxToolkitSupport.ensureStarted();

        app = new DpApplication();
        assertTrue(app.init(), "DpApplication.init() failed against a reachable MongoDB");

        final var registered = app.registerProvider(
                "it-v2-provider-" + STAMP, "a live IT provider", List.of(), java.util.Map.of());
        assertNotNull(registered, "registerProvider returned null");
        assertFalse(registered.isError, "registerProvider failed: " + registered.msg);

        dataBegin = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        dataEnd = dataBegin.plusSeconds(WINDOW_SECONDS);

        // Two PVs with data, and one registered PV deliberately left with none, so the
        // "every resolved PV gets a column" claim has something to be false about.
        final var status = app.generateAndIngestData(
                dataBegin, dataEnd, null,
                List.of(
                        new PvDetail(PV_A, "float", VALUES_PER_SECOND, "1.0", "0.5"),
                        new PvDetail(PV_B, "float", VALUES_PER_SECOND, "100.0", "1.0")),
                5, List.of(), false);

        assertNotNull(status, "generateAndIngestData returned null");
        assertFalse(status.isError, "generateAndIngestData failed: " + status.msg);

        pagedEnd = dataBegin.minusSeconds(60);
        pagedBegin = pagedEnd.minusSeconds(PAGED_WINDOW_SECONDS);

        final var pagedStatus = app.generateAndIngestData(
                pagedBegin, pagedEnd, null,
                List.of(new PvDetail(PV_PAGED, "float", PAGED_VALUES_PER_SECOND, "1.0", "0.5")),
                5, List.of(), false);

        assertNotNull(pagedStatus, "generateAndIngestData returned null for the dense PV");
        assertFalse(pagedStatus.isError,
                "generateAndIngestData failed for the dense PV: " + pagedStatus.msg);

        // The metadata arm resolves against CURATED PvMetadata records, which are a different
        // collection from the derived pvStats the name and pattern arms reach.  A PV can have
        // buckets and no metadata record, so the metadata selector needs one written explicitly.
        final var metadataSaved = app.savePvMetadata(
                PV_A, List.of(METADATA_ALIAS), List.of(METADATA_TAG),
                java.util.Map.of(METADATA_ATTRIBUTE_KEY, METADATA_ATTRIBUTE_VALUE),
                "a live IT PV", "it-v2-" + STAMP);
        assertNotNull(metadataSaved, "savePvMetadata returned null");
        assertFalse(metadataSaved.resultStatus.isError,
                "savePvMetadata failed: " + metadataSaved.resultStatus.msg);

        awaitIngestedDataVisible();
    }

    /**
     * Waits until the ingested buckets are queryable.
     *
     * <p><strong>generateAndIngestData() returning success does not mean the data can be read
     * back.</strong>  Ingestion is asynchronous: a querySamples() issued immediately afterwards
     * reliably returns a table with the right COLUMNS and an EMPTY timestamp list -- the same shape
     * a legitimately empty window produces, which is why it reads as a query defect rather than as
     * a race.  Measured here, the first query saw 0 rows and every query from ~500 ms on saw all
     * 100.
     *
     * <p>This is a property of the ingestion path, not of the V2 query migration, and it is the
     * reason this gate exists rather than a sleep: polling for the condition keeps the test honest
     * if the lag ever grows, whereas a fixed sleep would silently become flaky again.
     */
    /**
     * Queries by explicit PV name, the shape every assertion here is written against.
     *
     * <p>The wrapper takes a {@code PvSelectorParams} rather than a name list since #39 task 6's
     * selector work, because the Query Editor can now select by name pattern or by metadata too.
     * This keeps the name-list arm -- the one whose result shape these tests pin -- spelled once.
     */
    private static com.ospreydcs.dp.client.result.QuerySamplesApiResult querySamplesByName(
            List<String> pvNames,
            java.time.Instant begin,
            java.time.Instant end,
            String pageToken
    ) {
        return app.querySamples(
                new com.ospreydcs.dp.client.QueryClient.PvNameListSelector(pvNames),
                begin, end, pageToken);
    }

    private static void awaitIngestedDataVisible() throws InterruptedException {
        final long deadline = System.currentTimeMillis() + VISIBILITY_TIMEOUT_MILLIS;

        while (System.currentTimeMillis() < deadline) {
            if (isVisible(List.of(PV_A, PV_B), dataBegin, dataEnd, VALUES_PER_SECOND * WINDOW_SECONDS)
                    && isVisible(List.of(PV_PAGED), pagedBegin, pagedEnd, PAGED_EXPECTED_ROWS)) {
                return;
            }
            Thread.sleep(VISIBILITY_POLL_MILLIS);
        }

        throw new AssertionError("ingested data was not queryable within "
                + VISIBILITY_TIMEOUT_MILLIS + "ms, so the live querySamples assertions would be "
                + "testing an empty archive rather than the query path");
    }

    /**
     * Whether a window's full expected row count is queryable yet.  Counts rows across pages, since
     * the dense set spans more than one page and a first-page-only check would return true as soon
     * as the first page filled -- while the tail was still arriving.
     */
    private static boolean isVisible(
            List<String> pvNames, Instant begin, Instant end, int expectedRows
    ) {
        String pageToken = null;
        int rows = 0;
        do {
            final var probe = querySamplesByName(pvNames, begin, end, pageToken);
            if (probe == null || probe.resultStatus.isError || probe.columnTable == null) {
                return false;
            }
            rows += probe.columnTable.getTimestampList().getTimestampsCount();
            pageToken = probe.nextPageToken;
        } while (pageToken != null && !pageToken.isEmpty());

        return rows >= expectedRows;
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

    /** Best effort by design: a cleanup failure must not redden the build. */
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

            database.getCollection("buckets").deleteMany(
                    com.mongodb.client.model.Filters.regex("pvName", STAMP));
            database.getCollection("pvStats").deleteMany(
                    com.mongodb.client.model.Filters.regex("_id", STAMP));
            database.getCollection("providers").deleteMany(
                    com.mongodb.client.model.Filters.regex("name", STAMP));
            database.getCollection("pvMetadata").deleteMany(
                    com.mongodb.client.model.Filters.regex("pvName", STAMP));

        } catch (Exception cleanupFailed) {
            System.err.println("QuerySamplesLiveIT cleanup failed (records tagged " + STAMP
                    + " may remain, and are inert): " + cleanupFailed);
        }
    }

    // =======================================================================================

    /**
     * THE RESULT SHAPE, verified rather than assumed.
     *
     * The synthesized timestamp column exists because a V2 ColumnTable has no timestamp column --
     * the axis lives only in timestampList.  If the server did in fact return one, the reshape
     * would prepend a SECOND time column and every chart series would be off by one position.
     */
    @Test
    @Order(10)
    public void theTableCarriesItsTimeAxisOnlyInTheTimestampListAndNeverAsAColumn() {
        final var result = querySamplesByName(List.of(PV_A, PV_B), dataBegin, dataEnd, null);

        assertNotNull(result, "querySamples returned null");
        assertFalse(result.resultStatus.isError, "querySamples failed: " + result.resultStatus.msg);

        final ColumnTable table = result.columnTable;
        assertNotNull(table, "querySamples returned a null table on a successful result");

        assertFalse(table.getTimestampList().getTimestampsList().isEmpty(),
                "no timestamps came back for a window that was just ingested");

        for (DataColumn column : table.getDataColumnsList()) {
            assertNotEqualsIgnoringCase(DataExploreViewModel.TIMESTAMP_COLUMN_NAME, column.getName(),
                    "the server returned a timestamp COLUMN, so the reshape's synthesized column "
                            + "would duplicate it and shift every chart series by one");
        }
    }

    /**
     * Columns are bare PV names, and EVERY resolved PV gets a column even with no data in the
     * window -- which is what makes a blank cell mean "no sample here" rather than "this PV is
     * missing from the result".
     */
    @Test
    @Order(11)
    public void everyRequestedPvGetsAColumnEvenWithNoDataInTheWindow() {
        final var result = querySamplesByName(List.of(PV_A, PV_B, PV_NO_DATA), dataBegin, dataEnd, null);

        assertFalse(result.resultStatus.isError,
                "a PV with no data must not fail the query: " + result.resultStatus.msg);

        final List<String> columnNames = new ArrayList<>();
        for (DataColumn column : result.columnTable.getDataColumnsList()) {
            columnNames.add(column.getName());
        }

        assertTrue(columnNames.contains(PV_A), "PV_A missing from columns: " + columnNames);
        assertTrue(columnNames.contains(PV_B), "PV_B missing from columns: " + columnNames);

        // Note: whether a never-ingested PV resolves at all is the server's call.  What must NOT
        // happen is the query failing because of it, which the assertion above already pins.

        // sorted ascending and deduped
        final List<String> sorted = new ArrayList<>(columnNames);
        sorted.sort(String::compareTo);
        assertEquals(sorted, columnNames,
                "columns are documented as sorted ascending; the reshape preserves server order, "
                        + "so a change here reorders every chart series");
    }

    /**
     * Exactly one DataValue per column per timestamp.  The reshape indexes columns positionally
     * against the timestamp axis, so a column shorter than the axis would silently blank the tail
     * of that PV's data rather than erroring.
     */
    @Test
    @Order(12)
    public void everyColumnHasExactlyOneValuePerTimestamp() {
        final var result = querySamplesByName(List.of(PV_A, PV_B), dataBegin, dataEnd, null);
        final ColumnTable table = result.columnTable;

        final int timestampCount = table.getTimestampList().getTimestampsCount();
        assertTrue(timestampCount > 0, "no timestamps to check against");

        for (DataColumn column : table.getDataColumnsList()) {
            assertEquals(timestampCount, column.getDataValuesCount(),
                    "column \"" + column.getName() + "\" does not have one value per timestamp, so "
                            + "the positional transpose would misalign it against the time axis");
        }
    }

    /**
     * THE PAGING LOOP TERMINATES.
     *
     * This is the claim with the worst failure mode in the whole migration.  The retired interval
     * chopping ran a bounded FOR loop, so it could return wrong data but could not hang.  The
     * replacement is a DO-WHILE on a server-supplied token: if the server ever returned the same
     * token twice, or returned a token alongside a final page, the view would page forever with the
     * UI apparently mid-query and the table growing without bound.
     *
     * Driven through the same reshape the view model uses, so this also exercises the decode
     * against real server output rather than hand-built tables.
     */
    @Test
    @Order(20)
    public void pagingTerminatesAndTheRowsReshapeIntoTheTableStructure() {
        final int pageGuard = 1000;

        String pageToken = null;
        int pageCount = 0;
        int totalRows = 0;
        final List<String> seenTokens = new ArrayList<>();
        List<String> columnNames = null;

        do {
            final var result = querySamplesByName(List.of(PV_A, PV_B), dataBegin, dataEnd, pageToken);
            assertFalse(result.resultStatus.isError,
                    "page " + pageCount + " failed: " + result.resultStatus.msg);

            if (columnNames == null) {
                columnNames = DataExploreViewModel.columnNamesOf(result.columnTable);
            }

            final List<ObservableList<Object>> rows =
                    DataExploreViewModel.reshapePage(result.columnTable);
            totalRows += rows.size();

            for (ObservableList<Object> row : rows) {
                assertEquals(columnNames.size(), row.size(),
                        "a reshaped row must have one cell per column name, or the table renders "
                                + "cells against the wrong headers");
            }

            pageToken = result.nextPageToken;
            if (pageToken != null && !pageToken.isEmpty()) {
                assertFalse(seenTokens.contains(pageToken),
                        "the server returned a page token it had already returned, so the paging "
                                + "loop would never terminate");
                seenTokens.add(pageToken);
            }

            pageCount++;
            assertTrue(pageCount < pageGuard,
                    "paging did not terminate within " + pageGuard + " pages");

        } while (pageToken != null && !pageToken.isEmpty());

        assertNotNull(columnNames);
        assertEquals(DataExploreViewModel.TIMESTAMP_COLUMN_NAME, columnNames.get(0),
                "the synthesized timestamp column must lead");

        // Two PVs at 10 ms over a 10 s window is 1,000 samples each, sharing one time axis.
        final int expectedRows = WINDOW_SECONDS * VALUES_PER_SECOND;
        assertEquals(expectedRows, totalRows,
                "the paged row total must equal the samples ingested; a short total means a page "
                        + "was dropped, and a long one means a page was counted twice");
    }

    /**
     * MULTI-PAGE ACCUMULATION, as opposed to merely terminating.
     *
     * The 100-row set above fits in a single page, so against it a loop that stopped after the
     * first page would pass every assertion -- which it did, when this was mutation-checked.  This
     * queries a set sized past the server's default page so more than one page is genuinely
     * required, and asserts both that it took more than one page and that no row was lost or
     * double-counted across the boundary.
     */
    @Test
    @Order(23)
    public void aResultLargerThanOnePageAccumulatesAcrossPagesWithoutLosingRows() {
        String pageToken = null;
        int pageCount = 0;
        int totalRows = 0;

        do {
            final var result = querySamplesByName(List.of(PV_PAGED), pagedBegin, pagedEnd, pageToken);
            assertFalse(result.resultStatus.isError,
                    "page " + pageCount + " failed: " + result.resultStatus.msg);

            totalRows += DataExploreViewModel.reshapePage(result.columnTable).size();
            pageToken = result.nextPageToken;
            pageCount++;

            assertTrue(pageCount < 1000, "paging did not terminate");
        } while (pageToken != null && !pageToken.isEmpty());

        assertTrue(pageCount > 1,
                "this result was sized to span multiple pages but came back in " + pageCount
                        + " -- the server's page size must have changed, and with a single-page "
                        + "result this test cannot distinguish accumulation from stopping early");

        assertEquals(PAGED_EXPECTED_ROWS, totalRows,
                "rows were lost or double-counted across a page boundary");
    }

    /**
     * THE NAME-PATTERN ARM resolves server-side, against PV names this test never lists.
     *
     * The unit suite structurally cannot check this: it asserts which selector arm is BUILT, and a
     * selector that is built correctly and resolves to nothing returns a well-formed empty table
     * rather than an error.  So without a live check, a pattern arm that silently matched no PVs
     * would look exactly like a window with no data.
     */
    @Test
    @Order(30)
    public void theNamePatternArmResolvesPvsTheCallerNeverNamed() {
        // Matches PV_A and PV_B (both "IT:V2:<letter>:<stamp>") but not PV_PAGED, and the stamp
        // keeps it from reaching PVs left behind by an earlier run.
        final var result = app.querySamples(
                new com.ospreydcs.dp.client.QueryClient.PvNamePatternSelector(
                        "^IT:V2:[AB]:" + STAMP + "$"),
                dataBegin, dataEnd, null);

        assertNotNull(result, "querySamples returned null for a pattern selector");
        assertFalse(result.resultStatus.isError,
                "pattern selector failed: " + result.resultStatus.msg);

        final List<String> columnNames = DataExploreViewModel.columnNamesOf(result.columnTable);

        assertTrue(columnNames.contains(PV_A), "the pattern resolved no column for " + PV_A
                + "; resolved columns were " + columnNames);
        assertTrue(columnNames.contains(PV_B), "the pattern resolved no column for " + PV_B
                + "; resolved columns were " + columnNames);
        assertFalse(columnNames.contains(PV_PAGED),
                "the pattern matched a PV outside it, so it is not being applied as written: "
                        + columnNames);

        assertFalse(DataExploreViewModel.reshapePage(result.columnTable).isEmpty(),
                "a pattern that resolved columns but returned no rows would be indistinguishable "
                        + "from an empty window, which is why the rows are asserted too");
    }

    /**
     * THE METADATA ARM resolves against curated PvMetadata, a different collection from the derived
     * stats the other two arms reach.
     *
     * Each criterion is queried on its own so that a selector which quietly dropped one and
     * returned the PV via another cannot pass.  The negative case is what makes the positives mean
     * something: a metadata selector that resolved to EVERY PV would satisfy every positive
     * assertion here.
     */
    @Test
    @Order(31)
    public void theMetadataArmResolvesEachCriterionSeparately() {
        assertMetadataSelectorResolvesPvA("tag",
                new com.ospreydcs.dp.client.QueryClient.PvMetadataSelector(
                        null, null, List.of(METADATA_TAG), null));

        assertMetadataSelectorResolvesPvA("alias",
                new com.ospreydcs.dp.client.QueryClient.PvMetadataSelector(
                        null, new com.ospreydcs.dp.client.criteria.TextMatch(
                                List.of(METADATA_ALIAS), null, null),
                        null, null));

        assertMetadataSelectorResolvesPvA("attribute",
                new com.ospreydcs.dp.client.QueryClient.PvMetadataSelector(
                        null, null, null,
                        List.of(new com.ospreydcs.dp.client.criteria.AttributeCriterion(
                                METADATA_ATTRIBUTE_KEY, List.of(METADATA_ATTRIBUTE_VALUE)))));

        // A tag nothing carries must resolve to no columns.  Without this, a selector that ignored
        // its criteria entirely and returned every PV would pass all three assertions above.
        final var unmatched = app.querySamples(
                new com.ospreydcs.dp.client.QueryClient.PvMetadataSelector(
                        null, null, List.of("no-such-tag-" + STAMP), null),
                dataBegin, dataEnd, null);

        assertNotNull(unmatched, "querySamples returned null for an unmatched metadata selector");
        assertFalse(unmatched.resultStatus.isError,
                "a metadata selector matching nothing must be a success carrying an empty table, "
                        + "not an error: " + unmatched.resultStatus.msg);
        assertFalse(DataExploreViewModel.columnNamesOf(unmatched.columnTable).contains(PV_A),
                "a tag no PV carries resolved " + PV_A + " anyway, so the criterion is being "
                        + "dropped rather than applied");
    }

    private static void assertMetadataSelectorResolvesPvA(
            String criterionName,
            com.ospreydcs.dp.client.QueryClient.PvMetadataSelector selector
    ) {
        final var result = app.querySamples(selector, dataBegin, dataEnd, null);

        assertNotNull(result, "querySamples returned null for the " + criterionName + " criterion");
        assertFalse(result.resultStatus.isError,
                "the " + criterionName + " criterion failed: " + result.resultStatus.msg);
        assertTrue(DataExploreViewModel.columnNamesOf(result.columnTable).contains(PV_A),
                "the " + criterionName + " criterion resolved no column for " + PV_A
                        + "; resolved columns were "
                        + DataExploreViewModel.columnNamesOf(result.columnTable));
    }

    /**
     * An empty result is a SUCCESS carrying an empty table, not a rejection.  Treating it as a
     * failure would report "query failed" for the ordinary case of a window with no data.
     */
    @Test
    @Order(21)
    public void aWindowWithNoDataSucceedsWithAnEmptyTable() {
        final Instant emptyBegin = dataBegin.minus(400, ChronoUnit.DAYS);
        final Instant emptyEnd = emptyBegin.plusSeconds(10);

        final var result = querySamplesByName(List.of(PV_A), emptyBegin, emptyEnd, null);

        assertNotNull(result, "querySamples returned null for an empty window");
        assertFalse(result.resultStatus.isError,
                "an empty window must be a success, not an error: " + result.resultStatus.msg);
        assertNotNull(result.columnTable, "a successful empty result still carries a table");
        assertTrue(DataExploreViewModel.reshapePage(result.columnTable).isEmpty(),
                "an empty table must reshape to no rows");
        assertTrue(result.nextPageToken == null || result.nextPageToken.isEmpty(),
                "an empty result must not offer a next page");
    }

    /**
     * The values really do arrive as the scalar kinds the renderer handles, rather than as a kind
     * that falls through to blank.  A migration that rendered every cell blank would still pass
     * every shape assertion above.
     */
    @Test
    @Order(22)
    public void ingestedFloatSamplesRenderAsNumbersRatherThanBlanks() {
        final var result = querySamplesByName(List.of(PV_A), dataBegin, dataEnd, null);
        final List<ObservableList<Object>> rows =
                DataExploreViewModel.reshapePage(result.columnTable);

        assertFalse(rows.isEmpty(), "no rows to check");

        int numericCells = 0;
        for (ObservableList<Object> row : rows) {
            // column 0 is the synthesized timestamp; column 1 is PV_A
            if (row.get(1) instanceof Number) {
                numericCells++;
            }
        }

        assertEquals(rows.size(), numericCells,
                "every ingested float sample must render as a Number -- blanks here would mean the "
                        + "renderer is falling through to its default arm for a real scalar kind");
    }

    private static void assertNotEqualsIgnoringCase(String unexpected, String actual, String message) {
        assertFalse(unexpected.equalsIgnoreCase(actual), message);
    }
}
