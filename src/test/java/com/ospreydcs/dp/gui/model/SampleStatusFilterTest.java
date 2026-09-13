package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.client.QueryClient;
import com.ospreydcs.dp.grpc.v1.query.SampleStatusSelector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link SampleStatusFilter}, whose hazard is the mode: the two arms are not complements
 * over a partially-labeled archive, and the wrong one returns a plausible table.
 */
public class SampleStatusFilterTest {

    @Test
    public void theDefaultRestrictsNothing() {
        final SampleStatusFilter filter = SampleStatusFilter.none();

        assertFalse(filter.isActive());
        assertNull(filter.toSelectorParams(),
                "an inactive filter must produce no selector");
        assertTrue(filter.isComplete(),
                "an inactive filter is always complete -- there is nothing for it to be missing");
    }

    /**
     * The server rejects a blank domain with "sampleStatusSelector.domain must be specified", so
     * an active filter without one would break an otherwise valid query.
     */
    @Test
    public void anActiveFilterWithoutADomainIsIncomplete() {
        assertFalse(SampleStatusFilter.of("   ", null, null,
                        SampleStatusFilter.Mode.INCLUDE_MATCHING).isComplete(),
                "a blank domain must be refused -- the server rejects it");
        assertTrue(SampleStatusFilter.of("epics_alarm", null, null,
                        SampleStatusFilter.Mode.INCLUDE_MATCHING).isComplete());
    }

    @Test
    public void eachModeMapsToItsProtoArm() {
        assertEquals(SampleStatusSelector.Mode.MODE_INCLUDE_MATCHING,
                SampleStatusFilter.Mode.INCLUDE_MATCHING.getProtoMode());
        assertEquals(SampleStatusSelector.Mode.MODE_EXCLUDE_MATCHING,
                SampleStatusFilter.Mode.EXCLUDE_MATCHING.getProtoMode());
    }

    /**
     * MODE_UNSPECIFIED is deliberately unmapped, so the server's "mode must be specified"
     * rejection is unrepresentable rather than merely avoided.
     */
    @Test
    public void noModeMapsToTheUnspecifiedArm() {
        for (SampleStatusFilter.Mode mode : SampleStatusFilter.Mode.values()) {
            assertNotEquals(SampleStatusSelector.Mode.MODE_UNSPECIFIED, mode.getProtoMode(),
                    "an unspecified mode is rejected by the server and must not be reachable");
        }
    }

    @Test
    public void theSelectorCarriesEveryField() {
        final QueryClient.SampleStatusSelectorParams params = SampleStatusFilter.of(
                "epics_alarm", List.of("demo_generator"), List.of(2, 3),
                SampleStatusFilter.Mode.EXCLUDE_MATCHING).toSelectorParams();

        assertNotNull(params);
        assertEquals("epics_alarm", params.domain());
        assertEquals(List.of("demo_generator"), params.layers());
        assertEquals(List.of(2, 3), params.statusCodes());
        assertEquals(SampleStatusSelector.Mode.MODE_EXCLUDE_MATCHING, params.mode());
    }

    /**
     * Empty layers means "every layer in the domain" and empty codes means "any code", both of
     * which the server expresses as an absent field rather than an empty one.
     */
    @Test
    public void emptyOptionalListsAreSentAsNullNotEmpty() {
        final QueryClient.SampleStatusSelectorParams params = SampleStatusFilter.of(
                "epics_alarm", List.of(), List.of(),
                SampleStatusFilter.Mode.INCLUDE_MATCHING).toSelectorParams();

        assertNull(params.layers(), "an empty layers list must be sent as absent");
        assertNull(params.statusCodes(), "an empty status-code list must be sent as absent");
    }

    /**
     * The description must state what happens to UNLABELED samples, because that is the half of
     * the behavior the mode names omit and the half that decides whether a sparse result reads as
     * "no data" or as "nothing labeled".
     */
    @Test
    public void theDescriptionStatesTheUnlabeledBehaviorForBothModes() {
        final String include = SampleStatusFilter.of("epics_alarm", null, List.of(2),
                SampleStatusFilter.Mode.INCLUDE_MATCHING).describe();
        final String exclude = SampleStatusFilter.of("epics_alarm", null, List.of(2),
                SampleStatusFilter.Mode.EXCLUDE_MATCHING).describe();

        assertTrue(include.contains("unlabeled samples dropped"),
                "INCLUDE mode drops unlabeled samples and the description must say so: " + include);
        assertTrue(exclude.contains("unlabeled samples kept"),
                "EXCLUDE mode keeps unlabeled samples and the description must say so: " + exclude);
        assertNotEquals(include, exclude,
                "the two modes must not describe themselves identically");
    }

    @Test
    public void anEmptyCodeListIsDescribedAsAnyCode() {
        final String description = SampleStatusFilter.of("epics_alarm", null, List.of(),
                SampleStatusFilter.Mode.INCLUDE_MATCHING).describe();

        assertTrue(description.contains("any status code"),
                "an empty code list matches any code, which is 'labeled at all': " + description);
    }

    /**
     * A blank domain is reachable while the dialog is open, and the literal "null" would read as a
     * domain by that name rather than as an unfilled field.
     */
    @Test
    public void aBlankDomainIsDescribedAsUnspecifiedNotAsNull() {
        final String description = SampleStatusFilter.of("   ", null, null,
                SampleStatusFilter.Mode.INCLUDE_MATCHING).describe();

        assertFalse(description.contains("null"),
                "a blank domain must not render as the literal 'null': " + description);
        assertTrue(description.contains("unspecified domain"), description);
    }

    @Test
    public void anInactiveFilterDescribesItselfAsCoveringEverything() {
        assertTrue(SampleStatusFilter.none().describe().contains("all samples"));
    }

    @Test
    public void theModeLabelsNameTheUnlabeledBehavior() {
        assertTrue(SampleStatusFilter.Mode.INCLUDE_MATCHING.getLabel().contains("unlabeled"),
                "the combo box label must carry the unlabeled behavior; the proto name omits it");
        assertTrue(SampleStatusFilter.Mode.EXCLUDE_MATCHING.getLabel().contains("unlabeled"));
    }
}
