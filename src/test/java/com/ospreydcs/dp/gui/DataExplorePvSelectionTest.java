package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.model.PvSelection;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how the PV selection interacts with the Query Editor's own state: query validity, and the
 * name list staying untouched by the other two modes.
 *
 * <p>Validity is what enables the Submit button, so getting it wrong is visible in one direction
 * and invisible in the other. Requiring a name list for a pattern query disables Submit for a valid
 * query, which at least looks broken. Dropping the requirement for the name-list mode instead sends
 * an empty name list, which the server rejects with a message about the request rather than about
 * the empty PV list the user is looking at.
 */
class DataExplorePvSelectionTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        FxToolkitSupport.ensureStarted();
    }

    /** A view model with a valid time range, so only the PV selection decides validity. */
    private static DataExploreViewModel viewModelWithValidTimeRange() {
        final DataExploreViewModel viewModel = new DataExploreViewModel();
        viewModel.queryBeginDateProperty().set(LocalDate.of(2026, 1, 1));
        viewModel.queryEndDateProperty().set(LocalDate.of(2026, 1, 2));
        return viewModel;
    }

    @Test
    @DisplayName("the name-list mode still requires at least one PV name")
    void nameListModeRequiresNames() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final DataExploreViewModel viewModel = viewModelWithValidTimeRange();

            assertFalse(viewModel.isQueryValidProperty().get(),
                    "an empty name list must not be submittable -- the server rejects the empty "
                            + "selector with a message about the request, not about the PV list");

            viewModel.addPvName("PV:A");

            assertTrue(viewModel.isQueryValidProperty().get());
        });
    }

    @Test
    @DisplayName("a pattern query is valid with an EMPTY PV name list")
    void patternModeDoesNotRequireNames() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final DataExploreViewModel viewModel = viewModelWithValidTimeRange();

            // This is the behavior change. The PV list is empty and stays empty -- the pattern
            // resolves server-side -- so requiring names here would leave Submit disabled for a
            // perfectly valid query with nothing on screen explaining why.
            viewModel.setPvSelection(PvSelection.namePattern("^S:.*$"));

            assertTrue(viewModel.getPvNameList().isEmpty());
            assertTrue(viewModel.isQueryValidProperty().get(),
                    "a pattern query resolves its own PVs and must not be gated on the name list");
        });
    }

    @Test
    @DisplayName("a BLANK pattern is refused, because the server rejects one")
    void blankPatternIsRefused() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final DataExploreViewModel viewModel = viewModelWithValidTimeRange();
            viewModel.addPvName("PV:A");

            viewModel.setPvSelection(PvSelection.namePattern("   "));

            assertFalse(viewModel.isQueryValidProperty().get(),
                    "a blank pattern is an unfilled field, not an intent to match nothing; the "
                            + "populated name list must not rescue it, since the pattern arm "
                            + "ignores that list entirely");
        });
    }

    @Test
    @DisplayName("a metadata query with no criteria is VALID, because the server accepts it")
    void emptyMetadataQueryIsValid() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final DataExploreViewModel viewModel = viewModelWithValidTimeRange();

            // Deliberately not refused. An all-empty metadata selector matches every PV in the
            // archive rather than erroring, so refusing it here would invent a client-side rule the
            // server does not have -- and the app would disagree with the service about what is a
            // legal query. The selection's description is what warns instead.
            viewModel.setPvSelection(PvSelection.metadata(null, null, null, null));

            assertTrue(viewModel.isQueryValidProperty().get());
            assertTrue(viewModel.describePvSelection().contains("every PV in the archive"),
                    "if it is submittable it must at least say what it covers: "
                            + viewModel.describePvSelection());
        });
    }

    @Test
    @DisplayName("switching away from the name list leaves the list itself untouched")
    void otherModesDoNotWriteBackIntoTheNameList() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final DataExploreViewModel viewModel = viewModelWithValidTimeRange();
            viewModel.addPvName("PV:A");
            viewModel.addPvName("PV:B");

            viewModel.setPvSelection(PvSelection.namePattern(".*"));
            viewModel.setPvSelection(PvSelection.metadata(null, null, null, null));
            viewModel.setPvSelection(PvSelection.nameList());

            // The list is shared state: DpApplication's global PV names, the PV Explore and
            // Provider Explore views, the Dataset Builder and the data-event hyperlink all read or
            // write it. A selector that resolved into it would make a query-scoped choice rewrite
            // what five other flows see.
            assertEquals(java.util.List.of("PV:A", "PV:B"), viewModel.getPvNameList(),
                    "the PV name list must survive a round trip through the other two modes");
            assertTrue(viewModel.isQueryValidProperty().get(),
                    "returning to the name-list mode must restore the original query unchanged");
        });
    }

    @Test
    @DisplayName("a null selection restores the name-list default rather than clearing the selector")
    void nullSelectionFallsBackToNameList() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final DataExploreViewModel viewModel = viewModelWithValidTimeRange();
            viewModel.setPvSelection(PvSelection.namePattern(".*"));

            viewModel.setPvSelection(null);

            // The server rejects a query with no selector at all, so there is no safe "none" state.
            assertTrue(viewModel.getPvSelection().isNameList());
        });
    }
}
