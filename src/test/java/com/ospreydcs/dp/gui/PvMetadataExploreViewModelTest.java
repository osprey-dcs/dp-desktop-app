package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.criteria.TextMatch;
import com.ospreydcs.dp.grpc.v1.common.Attribute;
import com.ospreydcs.dp.grpc.v1.common.PvMetadata;
import com.ospreydcs.dp.gui.model.PvMetadataTableRow;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the PV metadata explore view model's criteria construction and search lifecycle.
 *
 * The criteria half is where the sharp edges are.  textMatch() is pure and static precisely so it
 * is testable without a service ecosystem -- the same reasoning as accumulatePages() and
 * emptyToNull() -- and the cases it handles produce PLAUSIBLE-LOOKING WRONG BEHAVIOR rather than an
 * obvious failure:
 *
 *   - a blank field that emitted a criterion with empty lists is rejected by the server, so an
 *     unfilled optional field would break an otherwise valid search
 *   - a blank field emitted as a PREFIX criterion compiles server-side to a regex matching
 *     everything, turning an unfilled field into a whole-collection scan that returns plausible
 *     results and simply ignores the criteria the user did fill in
 *
 * The lifecycle half mirrors ExploreViewModelSearchTest: state is observed from a listener on
 * searchInProgress at the instant it clears, because asserting on the final table contents would
 * pass against both the correct ordering and the D-2 ordering bug.
 */
public class PvMetadataExploreViewModelTest {

    private static final long AWAIT_SECONDS = 10;

    // ---------------------------------------------------------------------------------------
    // textMatch(): blank handling
    // ---------------------------------------------------------------------------------------

    @Test
    public void aBlankFieldYieldsAMatchThatContributesNoCriterion() {
        for (String blank : new String[] {null, "", "   ", "\t", " , , "}) {
            for (PvMetadataExploreViewModel.MatchMode mode : PvMetadataExploreViewModel.MatchMode.values()) {
                final TextMatch match = PvMetadataExploreViewModel.textMatch(blank, mode);

                assertTrue(match.isEmpty(),
                        "a blank field in " + mode + " mode must contribute no criterion, but was: "
                                + match);
                // asserted individually as well as via isEmpty(), because a criterion carrying an
                // EMPTY list is not the same as one carrying no list: the former is what the server
                // rejects, and isEmpty() is true for both
                assertNull(match.exact(), "blank input must not populate exact, in " + mode + " mode");
                assertNull(match.prefix(), "blank input must not populate prefix, in " + mode + " mode");
                assertNull(match.contains(), "blank input must not populate contains, in " + mode + " mode");
            }
        }
    }

    /**
     * The highest-consequence case in this file.  A blank name field left as a PREFIX match is the
     * one that fails silently rather than loudly: it compiles to a regex matching every record, so
     * the search returns the whole collection while appearing to have honored the other criteria.
     */
    @Test
    public void aBlankPrefixFieldDoesNotBecomeAMatchEverythingRegex() {
        final TextMatch match = PvMetadataExploreViewModel.textMatch(
                "   ", PvMetadataExploreViewModel.MatchMode.PREFIX);

        assertNull(match.prefix(),
                "a blank prefix criterion matches EVERY record server-side -- it must be omitted, "
                        + "not sent as an empty-or-blank prefix value");
        assertTrue(match.isEmpty());
    }

    // ---------------------------------------------------------------------------------------
    // textMatch(): mode routing
    // ---------------------------------------------------------------------------------------

    @Test
    public void eachModeRoutesTheValuesToItsOwnList() {
        final TextMatch exact = PvMetadataExploreViewModel.textMatch(
                "pv-1", PvMetadataExploreViewModel.MatchMode.EXACT);
        assertEquals(List.of("pv-1"), exact.exact());
        assertNull(exact.prefix());
        assertNull(exact.contains());

        final TextMatch prefix = PvMetadataExploreViewModel.textMatch(
                "pv-1", PvMetadataExploreViewModel.MatchMode.PREFIX);
        assertEquals(List.of("pv-1"), prefix.prefix());
        assertNull(prefix.exact());
        assertNull(prefix.contains());

        final TextMatch contains = PvMetadataExploreViewModel.textMatch(
                "pv-1", PvMetadataExploreViewModel.MatchMode.CONTAINS);
        assertEquals(List.of("pv-1"), contains.contains());
        assertNull(contains.exact());
        assertNull(contains.prefix());
    }

    /** A null mode is the CONTAINS default rather than an NPE, since the combo can start unset. */
    @Test
    public void aNullModeDefaultsToContains() {
        final TextMatch match = PvMetadataExploreViewModel.textMatch("pv-1", null);
        assertEquals(List.of("pv-1"), match.contains());
    }

    @Test
    public void aCommaSeparatedFieldBecomesMultipleOredValues() {
        final TextMatch match = PvMetadataExploreViewModel.textMatch(
                " pv-1 , pv-2,pv-3 ", PvMetadataExploreViewModel.MatchMode.EXACT);

        assertEquals(List.of("pv-1", "pv-2", "pv-3"), match.exact(),
                "values must be split on commas and trimmed; an untrimmed value would match nothing "
                        + "exactly and would be silently dropped as blank by the request builder");
    }

    @Test
    public void blankEntriesAmongRealValuesAreDropped() {
        final TextMatch match = PvMetadataExploreViewModel.textMatch(
                "pv-1, ,, pv-2,", PvMetadataExploreViewModel.MatchMode.CONTAINS);

        assertEquals(List.of("pv-1", "pv-2"), match.contains(),
                "a trailing or doubled comma must not contribute a blank value");
    }

    // ---------------------------------------------------------------------------------------
    // attribute criteria
    // ---------------------------------------------------------------------------------------

    /** A key with no value is a key-only existence search, which the criterion supports directly. */
    @Test
    public void anAttributeKeyWithNoValueIsAKeyOnlyExistenceSearch() {
        final PvMetadataExploreViewModel viewModel = new PvMetadataExploreViewModel();
        viewModel.attributeKeyProperty().set("unit");

        assertFalse(viewModel.hasNoCriteria(),
                "a key-only attribute search is a real criterion, so the form is not empty");
    }

    /**
     * A value with no key cannot be expressed -- the key is required -- so it must be dropped rather
     * than being silently used AS the key, which would search a field nobody named.
     */
    @Test
    public void anAttributeValueWithNoKeyContributesNothing() {
        final PvMetadataExploreViewModel viewModel = new PvMetadataExploreViewModel();
        viewModel.attributeValueProperty().set("volts");

        assertTrue(viewModel.hasNoCriteria(),
                "an attribute value with no key cannot be expressed as a criterion and must be "
                        + "dropped, not promoted to a key");
    }

    @Test
    public void anEmptyFormReportsThatItHasNoCriteria() {
        final PvMetadataExploreViewModel viewModel = new PvMetadataExploreViewModel();
        assertTrue(viewModel.hasNoCriteria());

        viewModel.pvNameTextProperty().set("   ");
        assertTrue(viewModel.hasNoCriteria(),
                "a whitespace-only field is not a criterion");

        viewModel.pvNameTextProperty().set("pv-1");
        assertFalse(viewModel.hasNoCriteria());
    }

    @Test
    public void clearSearchRestoresTheEmptyForm() throws Exception {
        final PvMetadataExploreViewModel viewModel = new PvMetadataExploreViewModel();
        viewModel.setDpApplication(new FakePvMetadataApplication(
                new DpApplication.PagedResult<>(List.of(record("pv-1")), false)));

        runSearchAndObserve(viewModel);
        assertEquals(1, viewModel.getSearchResults().size());

        viewModel.pvNameTextProperty().set("pv-1");
        viewModel.tagsTextProperty().set("a-tag");
        viewModel.attributeKeyProperty().set("unit");

        FxToolkitSupport.runOnFxThread(viewModel::clearSearch);

        assertTrue(viewModel.hasNoCriteria(), "clear must leave no criteria behind");
        assertEquals(0, viewModel.getSearchResults().size());
        assertEquals("0 PV(s)", viewModel.resultCountMessageProperty().get());
    }

    // ---------------------------------------------------------------------------------------
    // search lifecycle
    // ---------------------------------------------------------------------------------------

    @Test
    public void resultsArePublishedBeforeTheInProgressFlagClears() throws Exception {
        final PvMetadataExploreViewModel viewModel = new PvMetadataExploreViewModel();
        viewModel.setDpApplication(new FakePvMetadataApplication(
                new DpApplication.PagedResult<>(
                        List.of(record("pv-1"), record("pv-2"), record("pv-3")), false)));

        final Observed observed = runSearchAndObserve(viewModel);

        assertEquals(3, observed.rowCount,
                "rows must be in the list before searchInProgress clears, not queued behind it");
        assertEquals("3 PV(s)", observed.countMessage,
                "the count label must be current when the flag clears");
        assertFalse(viewModel.searchInProgressProperty().get());
    }

    /**
     * A truncated result must never be presented as a total, in EITHER label -- the count label
     * bound to the record count would otherwise read "5000 PV(s)" beside a status message saying
     * the query was capped.
     */
    @Test
    public void aTruncatedResultSaysSoInBothLabels() throws Exception {
        final PvMetadataExploreViewModel viewModel = new PvMetadataExploreViewModel();
        final List<PvMetadata> records = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            records.add(record("pv-" + i));
        }
        viewModel.setDpApplication(new FakePvMetadataApplication(
                new DpApplication.PagedResult<>(records, true)));

        runSearchAndObserve(viewModel);

        assertEquals("first 5 PV(s)", viewModel.resultCountMessageProperty().get(),
                "the count label must name the truncation");
        assertTrue(viewModel.statusMessageProperty().get().contains("more available"),
                "the status message must name the truncation too, but was: "
                        + viewModel.statusMessageProperty().get());
    }

    @Test
    public void aFailedSearchClearsTheFlagAndReportsTheError() throws Exception {
        final PvMetadataExploreViewModel viewModel = new PvMetadataExploreViewModel();
        viewModel.setDpApplication(new FakePvMetadataApplication(
                new RuntimeException("the service is unreachable")));

        final Observed observed = runSearchAndObserve(viewModel);

        assertEquals(0, observed.rowCount);
        assertFalse(viewModel.searchInProgressProperty().get(),
                "a failed search must not leave the progress indicator spinning");
        assertTrue(viewModel.searchStatusMessageProperty().get().contains("the service is unreachable"),
                "the failure must reach the search status label, but was: "
                        + viewModel.searchStatusMessageProperty().get());
    }

    /**
     * The criteria the view model builds must actually reach the API call.  Without this, every
     * assertion above tests a method the search could be ignoring.
     */
    @Test
    public void theBuiltCriteriaReachTheApiCall() throws Exception {
        final PvMetadataExploreViewModel viewModel = new PvMetadataExploreViewModel();
        final FakePvMetadataApplication application = new FakePvMetadataApplication(
                new DpApplication.PagedResult<>(List.of(), false));
        viewModel.setDpApplication(application);

        viewModel.pvNameTextProperty().set("pv-1");
        viewModel.setPvNameMatchMode(PvMetadataExploreViewModel.MatchMode.PREFIX);
        viewModel.aliasTextProperty().set("");
        viewModel.tagsTextProperty().set("a-tag, b-tag");
        viewModel.attributeKeyProperty().set("unit");
        viewModel.attributeValueProperty().set("volts");

        runSearchAndObserve(viewModel);

        assertNotNull(application.pvNameMatch);
        assertEquals(List.of("pv-1"), application.pvNameMatch.prefix(),
                "the chosen match mode must reach the request");
        assertTrue(application.aliasesMatch.isEmpty(),
                "an unfilled alias field must reach the request as a match contributing no criterion");
        assertEquals(List.of("a-tag", "b-tag"), application.tags);
        assertEquals(1, application.attributes.size());
        assertEquals("unit", application.attributes.get(0).key());
        assertEquals(List.of("volts"), application.attributes.get(0).values());
    }

    // ---------------------------------------------------------------------------------------
    // editing
    // ---------------------------------------------------------------------------------------

    /**
     * The alias trap.  A search by alias returns the record under its CANONICAL name, and
     * savePvMetadata() is a full-replace upsert keyed on that name, so the editor must be handed the
     * resolved record rather than anything derived from what the user typed.
     */
    @Test
    public void editingNavigatesWithTheResolvedRecordNotTheTypedText() {
        final PvMetadata canonical = PvMetadata.newBuilder()
                .setPvName("CANONICAL:NAME")
                .addAliases("OLD:NAME")
                .build();

        final AtomicReference<PvMetadata> navigatedWith = new AtomicReference<>();
        final PvMetadataExploreViewModel viewModel = new PvMetadataExploreViewModel();
        viewModel.setMainController(new RecordingMainController(navigatedWith));

        // the user searched by alias, so the form holds the alias, not the canonical name
        viewModel.pvNameTextProperty().set("OLD:NAME");
        viewModel.editPvMetadata(new PvMetadataTableRow(canonical));

        assertNotNull(navigatedWith.get(), "editing must navigate to the editor");
        assertEquals("CANONICAL:NAME", navigatedWith.get().getPvName(),
                "the editor must receive the record's canonical name -- saving under the alias the "
                        + "user typed would create a SECOND record instead of updating this one");
    }

    @Test
    public void editingANullRowDoesNothing() {
        final AtomicReference<PvMetadata> navigatedWith = new AtomicReference<>();
        final PvMetadataExploreViewModel viewModel = new PvMetadataExploreViewModel();
        viewModel.setMainController(new RecordingMainController(navigatedWith));

        viewModel.editPvMetadata(null);
        viewModel.editPvMetadata(new PvMetadataTableRow(null));

        assertNull(navigatedWith.get(), "a row with no record must not open the editor");
    }

    // ---------------------------------------------------------------------------------------
    // support
    // ---------------------------------------------------------------------------------------

    private static PvMetadata record(String pvName) {
        return PvMetadata.newBuilder()
                .setPvName(pvName)
                .addAttributes(Attribute.newBuilder().setName("unit").setValue("volts"))
                .build();
    }

    /** State captured at the instant searchInProgress cleared. */
    private record Observed(int rowCount, String countMessage) { }

    private static Observed runSearchAndObserve(PvMetadataExploreViewModel viewModel)
            throws Exception {
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicReference<Integer> rowCount = new AtomicReference<>();
        final AtomicReference<String> countMessage = new AtomicReference<>();

        FxToolkitSupport.runOnFxThread(() -> {
            viewModel.searchInProgressProperty().addListener((observable, wasSearching, isSearching) -> {
                if (wasSearching && !isSearching) {
                    rowCount.set(viewModel.getSearchResults().size());
                    countMessage.set(viewModel.resultCountMessageProperty().get());
                    finished.countDown();
                }
            });
            viewModel.executeSearch();
        });

        assertTrue(finished.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "the search should have completed and cleared its in-progress flag");

        // drain anything still queued on the FX thread, so a stray runLater would have landed by
        // the time the caller asserts on the final state
        FxToolkitSupport.runOnFxThread(() -> { });
        return new Observed(rowCount.get(), countMessage.get());
    }

    private static final class FakePvMetadataApplication extends DpApplication {
        private final PagedResult<PvMetadata> result;
        private final RuntimeException failure;

        private TextMatch pvNameMatch;
        private TextMatch aliasesMatch;
        private List<String> tags;
        private List<com.ospreydcs.dp.client.criteria.AttributeCriterion> attributes;

        FakePvMetadataApplication(PagedResult<PvMetadata> result) {
            this.result = result;
            this.failure = null;
        }

        FakePvMetadataApplication(RuntimeException failure) {
            this.result = null;
            this.failure = failure;
        }

        @Override
        public PagedResult<PvMetadata> queryPvMetadata(
                TextMatch pvNameMatch,
                TextMatch aliasesMatch,
                List<String> tagsAnyOf,
                List<com.ospreydcs.dp.client.criteria.AttributeCriterion> attributes
        ) {
            this.pvNameMatch = pvNameMatch;
            this.aliasesMatch = aliasesMatch;
            this.tags = tagsAnyOf;
            this.attributes = attributes;

            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    /** Captures the record the view model hands to the editor. */
    private static final class RecordingMainController extends MainController {
        private final AtomicReference<PvMetadata> navigatedWith;

        RecordingMainController(AtomicReference<PvMetadata> navigatedWith) {
            this.navigatedWith = navigatedWith;
        }

        @Override
        public void navigateToPvMetadataEditor(PvMetadata record) {
            navigatedWith.set(record);
        }
    }
}
