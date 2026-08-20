package com.ospreydcs.dp.gui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the static "key=value" parsing helpers on AttributesListComponent.
 * Only the static methods are exercised — instantiating the component would load FXML
 * and JavaFX controls, which is out of scope for unit tests (issue #29).
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
}
