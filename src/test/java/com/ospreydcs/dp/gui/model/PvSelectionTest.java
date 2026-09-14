package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.client.QueryClient;
import com.ospreydcs.dp.client.criteria.AttributeCriterion;
import com.ospreydcs.dp.client.criteria.TextMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the Query Editor's PV selection: which arm of the V2 PvSelector it builds, and what it
 * says it covers.
 *
 * <p>Every case here fails quietly rather than loudly if it regresses. A selection that builds the
 * wrong selector arm returns a well-formed table for PVs the user did not ask about; a name list
 * copied into the selection drifts from the shared list the rest of the app writes; and a metadata
 * query with no criteria is accepted by the server as a whole-archive scan rather than rejected, so
 * only the description makes it visible.
 */
class PvSelectionTest {

    @Test
    @DisplayName("the default selection is the name list, so nothing else in the app has to change")
    void defaultIsNameList() {
        final PvSelection selection = PvSelection.nameList();

        assertEquals(PvSelection.Mode.NAME_LIST, selection.getMode());
        assertTrue(selection.isNameList());
    }

    @Test
    @DisplayName("the name-list arm reads the live list rather than a copy taken when it was built")
    void nameListReadsTheLiveList() {
        final PvSelection selection = PvSelection.nameList();
        final List<String> names = new ArrayList<>(List.of("PV:A"));

        // The rest of the app adds PVs through PV Explore, Provider Explore, the Dataset Builder
        // and the data-event hyperlink, all of them AFTER a selection exists.  A selection holding
        // its own copy would query the PV set as it stood when the dialog was last opened.
        names.add("PV:B");

        final QueryClient.PvSelectorParams params = selection.toSelectorParams(names);
        final QueryClient.PvNameListSelector nameList =
                assertInstanceOf(QueryClient.PvNameListSelector.class, params);

        assertEquals(List.of("PV:A", "PV:B"), nameList.pvNames(),
                "a PV added after the selection was built must still be queried");
    }

    @Test
    @DisplayName("mutating the list handed to the selector does not reach back into the caller's list")
    void nameListIsCopiedIntoTheRequest() {
        final List<String> names = new ArrayList<>(List.of("PV:A"));
        final QueryClient.PvSelectorParams params = PvSelection.nameList().toSelectorParams(names);
        final QueryClient.PvNameListSelector nameList =
                (QueryClient.PvNameListSelector) params;

        nameList.pvNames().add("PV:INJECTED");

        assertEquals(List.of("PV:A"), names,
                "the selector must not alias the observable list the ListView is bound to");
    }

    @Test
    @DisplayName("the pattern arm sends the pattern, not a name list built from it")
    void patternBuildsThePatternArm() {
        final PvSelection selection = PvSelection.namePattern("^S:.*:CURRENT$");

        assertFalse(selection.isNameList());

        final QueryClient.PvNamePatternSelector pattern = assertInstanceOf(
                QueryClient.PvNamePatternSelector.class,
                // a populated name list is passed deliberately: the pattern arm must ignore it
                selection.toSelectorParams(List.of("PV:A", "PV:B")));

        assertEquals("^S:.*:CURRENT$", pattern.pattern());
    }

    @Test
    @DisplayName("the metadata arm carries every criterion through to the selector")
    void metadataBuildsTheMetadataArm() {
        final TextMatch pvName = new TextMatch(null, null, List.of("CURRENT"));
        final TextMatch aliases = new TextMatch(List.of("OLD:NAME"), null, null);
        final PvSelection selection = PvSelection.metadata(
                pvName, aliases, List.of("beamline"),
                List.of(new AttributeCriterion("subsystem", List.of("vacuum"))));

        final QueryClient.PvMetadataSelector metadata = assertInstanceOf(
                QueryClient.PvMetadataSelector.class,
                selection.toSelectorParams(List.of("PV:A")));

        assertEquals(pvName, metadata.pvName());
        assertEquals(aliases, metadata.aliases());
        assertEquals(List.of("beamline"), metadata.tagsAnyOf());
        assertEquals(1, metadata.attributes().size());
        assertEquals("subsystem", metadata.attributes().get(0).key());
    }

    @Test
    @DisplayName("only the name-list mode can become a data block")
    void onlyNameListIsADataBlock() {
        // The Dataset Builder branches on this.  A data block IS a PV name list, and the name list
        // stays populated in the other two modes because it is shared state -- so reading it
        // regardless would save a block describing PVs the query never covered.
        assertTrue(PvSelection.nameList().isNameList());
        assertFalse(PvSelection.namePattern(".*").isNameList());
        assertFalse(PvSelection.metadata(null, null, null, null).isNameList());
    }

    @Test
    @DisplayName("the name-list description counts the live list, singular and plural")
    void nameListDescriptionCountsTheLiveList() {
        assertEquals("1 PV by name", PvSelection.nameList().describe(List.of("PV:A")));
        assertEquals("2 PVs by name", PvSelection.nameList().describe(List.of("PV:A", "PV:B")));
        assertEquals("0 PVs by name", PvSelection.nameList().describe(List.of()));
    }

    @Test
    @DisplayName("match-all is described as the whole archive, not as an ordinary pattern")
    void matchAllIsDescribedAsTheWholeArchive() {
        final String description =
                PvSelection.namePattern(PvSelection.MATCH_ALL_PATTERN).describe(List.of());

        assertTrue(description.contains("every PV in the archive"),
                "'.*' reads as a filter but covers the archive; the description must say so, "
                        + "because the server accepts it up to maxResolvedPvCount rather than "
                        + "rejecting it outright: " + description);
    }

    @Test
    @DisplayName("a metadata query with NO criteria is described as covering everything")
    void emptyMetadataQueryIsDescribedAsCoveringEverything() {
        // This is the sharpest case in the class.  An all-empty metadata selector is NOT an error
        // server-side -- unlike an empty configurationSelector, which is rejected -- so nothing
        // downstream will complain.  The description is the only thing standing between a user who
        // opened the metadata tab, typed nothing, and a whole-archive query.
        final String description =
                PvSelection.metadata(new TextMatch(null, null, null), new TextMatch(null, null, null),
                        List.of(), List.of()).describe(List.of("PV:A"));

        assertTrue(description.contains("every PV in the archive"),
                "an unfilled metadata query must not read as a narrow filter: " + description);
        assertFalse(description.contains("PV:A"),
                "the name list is irrelevant to a metadata query and must not appear in its "
                        + "description: " + description);
    }

    @Test
    @DisplayName("a populated metadata query names the criteria that are actually set")
    void populatedMetadataQueryNamesItsCriteria() {
        final String description = PvSelection.metadata(
                new TextMatch(null, null, List.of("CURRENT")),
                new TextMatch(null, null, null),
                List.of("beamline"),
                List.of()).describe(List.of());

        assertTrue(description.contains("name"), description);
        assertTrue(description.contains("tags"), description);
        assertFalse(description.contains("alias"),
                "an unfilled alias criterion must not be described as applied: " + description);
        assertFalse(description.contains("every PV in the archive"),
                "a query with real criteria is not a whole-archive scan: " + description);
    }

    @Test
    @DisplayName("a selection is immutable, so a cancelled dialog cannot leave one half-applied")
    void metadataCollectionsAreDefensivelyCopied() {
        final List<String> tags = new ArrayList<>(List.of("beamline"));
        final PvSelection selection = PvSelection.metadata(null, null, tags, null);

        tags.add("added-after");

        assertEquals(List.of("beamline"), selection.getMetadataTagsAnyOf());
        assertNotNull(selection.getMetadataAttributes());
    }
}
