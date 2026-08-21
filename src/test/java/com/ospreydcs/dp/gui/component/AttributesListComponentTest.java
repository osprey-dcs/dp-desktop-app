package com.ospreydcs.dp.gui.component;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the static "key=value" parsing helpers on AttributesListComponent.
 * Only the static methods are exercised here, so this class stays toolkit-free;
 * the instance API (add/remove/set against a live component) is covered by
 * AttributesListComponentInstanceTest, which runs under the JavaFX toolkit.
 */
public class AttributesListComponentTest {

    @Test
    public void keyAndValueAreSplitAtFirstEquals() {
        assertEquals("key", AttributesListComponent.getKeyFromAttribute("key=value"));
        assertEquals("value", AttributesListComponent.getValueFromAttribute("key=value"));
    }

    @Test
    public void valueMayItselfContainEquals() {
        assertEquals("key", AttributesListComponent.getKeyFromAttribute("key=a=b"));
        assertEquals("a=b", AttributesListComponent.getValueFromAttribute("key=a=b"));
    }

    @Test
    public void missingEqualsYieldsWholeStringAsKeyAndEmptyValue() {
        assertEquals("no-separator", AttributesListComponent.getKeyFromAttribute("no-separator"));
        assertEquals("", AttributesListComponent.getValueFromAttribute("no-separator"));
    }

    @Test
    public void emptyKeyOrValueSidesArePreserved() {
        assertEquals("", AttributesListComponent.getKeyFromAttribute("=value"));
        assertEquals("value", AttributesListComponent.getValueFromAttribute("=value"));
        assertEquals("key", AttributesListComponent.getKeyFromAttribute("key="));
        assertEquals("", AttributesListComponent.getValueFromAttribute("key="));
    }

    @Test
    public void nullAttributeIsHandled() {
        assertNull(AttributesListComponent.getKeyFromAttribute(null));
        assertEquals("", AttributesListComponent.getValueFromAttribute(null));
    }

    // --- attributesToMap: the shared converter used by every view -------------
    //
    // These pin the contract deliberately, because three views previously had their
    // own copy of this conversion and they disagreed on trimming, on malformed input,
    // and on nulls -- identical user input could persist differently depending on
    // which view submitted it.

    @Test
    public void attributesToMapParsesKeyValuePairs() {
        Map<String, String> map = AttributesListComponent.attributesToMap(
                Arrays.asList("k1=v1", "k2=v2"));
        assertEquals(2, map.size());
        assertEquals("v1", map.get("k1"));
        assertEquals("v2", map.get("k2"));
    }

    @Test
    public void attributesToMapTrimsKeysAndValues() {
        Map<String, String> map = AttributesListComponent.attributesToMap(
                Collections.singletonList("  k  =  v  "));
        assertEquals(Map.of("k", "v"), map);
    }

    @Test
    public void attributesToMapPreservesInsertionOrder() {
        Map<String, String> map = AttributesListComponent.attributesToMap(
                Arrays.asList("b=2", "a=1", "c=3"));
        assertEquals(Arrays.asList("b", "a", "c"), List.copyOf(map.keySet()));
    }

    @Test
    public void attributesToMapKeepsOnlyTheValueAfterTheFirstEquals() {
        // Values may legitimately contain "=", so only the first one separates key from value.
        Map<String, String> map = AttributesListComponent.attributesToMap(
                Collections.singletonList("url=http://h/?a=b"));
        assertEquals("http://h/?a=b", map.get("url"));
    }

    @Test
    public void attributesToMapSkipsEntriesNotInKeyValueForm() {
        Map<String, String> map = AttributesListComponent.attributesToMap(
                Arrays.asList("noequals", "k=v"));
        assertEquals(Map.of("k", "v"), map);
    }

    @Test
    public void attributesToMapSkipsBlankKeys() {
        // A blank key must never reach the map: it would be sent to the backend as a
        // meaningless empty-string attribute key.
        Map<String, String> map = AttributesListComponent.attributesToMap(
                Arrays.asList("=v", "   =v2", "k=v"));
        assertEquals(Map.of("k", "v"), map);
    }

    @Test
    public void attributesToMapSkipsNullEntriesWithoutThrowing() {
        // One previous implementation threw NPE here and another produced a null map key.
        Map<String, String> map = AttributesListComponent.attributesToMap(
                Arrays.asList(null, "k=v"));
        assertEquals(Map.of("k", "v"), map);
    }

    @Test
    public void attributesToMapAllowsEmptyValues() {
        Map<String, String> map = AttributesListComponent.attributesToMap(
                Collections.singletonList("k="));
        assertEquals(1, map.size());
        assertEquals("", map.get("k"));
    }

    @Test
    public void attributesToMapReturnsEmptyMapForNullOrEmptyInput() {
        assertTrue(AttributesListComponent.attributesToMap(null).isEmpty());
        assertTrue(AttributesListComponent.attributesToMap(Collections.emptyList()).isEmpty());
    }

    @Test
    public void attributesToMapLastEntryWinsOnDuplicateKeys() {
        Map<String, String> map = AttributesListComponent.attributesToMap(
                Arrays.asList("k=first", "k=second"));
        assertEquals(1, map.size());
        assertEquals("second", map.get("k"));
    }
}
