package com.ospreydcs.dp.gui.model;

import com.ospreydcs.dp.client.QueryClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link ConfigurationFilter}, whose whole job is the null-versus-empty distinction the
 * client wrapper reads oppositely.
 */
public class ConfigurationFilterTest {

    @Test
    public void theDefaultRestrictsNothing() {
        final ConfigurationFilter filter = ConfigurationFilter.none();

        assertFalse(filter.isActive(), "the default filter must restrict nothing");
        assertNull(filter.toCriteria(),
                "an inactive filter must produce NULL criteria; an empty list is a rejected "
                        + "request rather than an unfiltered one");
    }

    /**
     * The single most important assertion in this class.  An empty list means "a restriction was
     * asked for and none of it was usable", which the server rejects with
     * "configurationSelector.criteria list must not be empty" -- so returning one to mean "no
     * filter" would break every unfiltered query.
     */
    @Test
    public void anAllBlankFilterIsNullCriteriaNotEmptyCriteria() {
        final ConfigurationFilter filter = ConfigurationFilter.of(
                List.of("  ", ""), List.of(), null, List.of("   "), "  ", List.of("x"));

        assertFalse(filter.isActive(),
                "a filter whose every field is blank restricts nothing");
        assertNull(filter.toCriteria(),
                "blank-only criteria must collapse to null, not to an empty list the server rejects");
    }

    /**
     * One criterion per populated field, never one criterion carrying several arms: the proto
     * criterion is a oneof, and the client returns null from its builder for a multi-arm criterion,
     * which buildQuerySpec turns into a rejected request.
     */
    @Test
    public void eachPopulatedFieldBecomesItsOwnSingleArmCriterion() {
        final ConfigurationFilter filter = ConfigurationFilter.of(
                List.of("config-1"), List.of("act-1"), List.of("cat-1"), List.of("tag-1"),
                "key-1", List.of("value-1"));

        final List<QueryClient.ConfigurationCriterion> criteria = filter.toCriteria();
        assertNotNull(criteria);
        assertEquals(5, criteria.size(),
                "five populated fields must yield five criteria, one arm each");

        for (QueryClient.ConfigurationCriterion criterion : criteria) {
            assertEquals(1, populatedArms(criterion),
                    "a criterion with more than one arm is rejected at build time: " + criterion);
        }
    }

    @Test
    public void eachArmCarriesItsOwnValues() {
        final ConfigurationFilter filter = ConfigurationFilter.of(
                List.of("config-1", "config-2"), null, null, null, null, null);

        final List<QueryClient.ConfigurationCriterion> criteria = filter.toCriteria();
        assertEquals(1, criteria.size());
        assertEquals(List.of("config-1", "config-2"), criteria.get(0).configurationNameAnyOf(),
                "values within one criterion are ORed and must all be carried");
    }

    /**
     * A key with no values is a key-only existence search, which the criterion supports directly.
     * The values must be NULL rather than an empty list -- the two are not the same request.
     */
    @Test
    public void anAttributeKeyWithNoValuesIsAKeyOnlyExistenceSearch() {
        final ConfigurationFilter filter = ConfigurationFilter.of(
                null, null, null, null, "subsystem", List.of());

        final List<QueryClient.ConfigurationCriterion> criteria = filter.toCriteria();
        assertEquals(1, criteria.size());
        assertEquals("subsystem", criteria.get(0).attribute().key());
        assertNull(criteria.get(0).attribute().values(),
                "an absent values list must be null, not empty");
    }

    /** An attribute value with no key cannot be expressed, so it is dropped rather than promoted. */
    @Test
    public void anAttributeValueWithNoKeyIsDropped() {
        final ConfigurationFilter filter = ConfigurationFilter.of(
                null, null, null, null, "  ", List.of("orphan"));

        assertFalse(filter.isActive(),
                "a value with no key cannot be expressed and must not make the filter active");
        assertNull(filter.toCriteria());
    }

    @Test
    public void blankValuesAreDroppedFromWithinACriterion() {
        final ConfigurationFilter filter = ConfigurationFilter.of(
                List.of("config-1", "   ", "config-2"), null, null, null, null, null);

        assertEquals(List.of("config-1", "config-2"), filter.getConfigurationNames(),
                "blank values inside a populated criterion are dropped, not forwarded");
    }

    @Test
    public void collectionsAreCopiedDefensively() {
        final List<String> names = new ArrayList<>(List.of("config-1"));
        final ConfigurationFilter filter = ConfigurationFilter.of(names, null, null, null, null, null);

        names.add("injected");

        assertEquals(List.of("config-1"), filter.getConfigurationNames(),
                "the filter must not alias the list it was built from");
    }

    @Test
    public void anInactiveFilterDescribesItselfAsCoveringTheWholeRange() {
        assertTrue(ConfigurationFilter.none().describe().contains("whole time range"),
                "an inactive filter must say it restricts nothing");
    }

    @Test
    public void anActiveFilterNamesItsCriteria() {
        final String description = ConfigurationFilter.of(
                List.of("beamline-a"), null, List.of("optics"), null, null, null).describe();

        assertTrue(description.contains("beamline-a"), "the description must name the configuration");
        assertTrue(description.contains("optics"), "the description must name the category");
    }

    private static int populatedArms(QueryClient.ConfigurationCriterion criterion) {
        int arms = 0;
        if (criterion.configurationNameAnyOf() != null && !criterion.configurationNameAnyOf().isEmpty()) arms++;
        if (criterion.clientActivationIdAnyOf() != null && !criterion.clientActivationIdAnyOf().isEmpty()) arms++;
        if (criterion.categoryAnyOf() != null && !criterion.categoryAnyOf().isEmpty()) arms++;
        if (criterion.tagsAnyOf() != null && !criterion.tagsAnyOf().isEmpty()) arms++;
        if (criterion.attribute() != null) arms++;
        return arms;
    }
}
