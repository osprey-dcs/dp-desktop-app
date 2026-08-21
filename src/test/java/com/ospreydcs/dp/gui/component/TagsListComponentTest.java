package com.ospreydcs.dp.gui.component;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the static tag normalization rules on TagsListComponent: drop null/blank,
 * trim, and collapse duplicates while preserving order.
 * Only the static method is exercised — instantiating the component would load FXML
 * and JavaFX controls, which is out of scope for unit tests (issue #29).
 */
public class TagsListComponentTest {

    @Test
    public void normalizationPreservesCleanTagsInOrder() {
        assertEquals(List.of("alpha", "beta", "gamma"),
                TagsListComponent.normalizeTags(List.of("alpha", "beta", "gamma")));
    }

    @Test
    public void nullListNormalizesToEmptyList() {
        assertEquals(List.of(), TagsListComponent.normalizeTags(null));
    }

    @Test
    public void nullAndBlankEntriesAreDropped() {
        assertEquals(List.of("alpha", "beta"),
                TagsListComponent.normalizeTags(Arrays.asList(null, "alpha", "", "   ", "beta")));
    }

    @Test
    public void entriesAreTrimmed() {
        assertEquals(List.of("alpha", "beta"),
                TagsListComponent.normalizeTags(List.of("  alpha ", "beta  ")));
    }

    @Test
    public void duplicatesAreCollapsedKeepingFirstOccurrence() {
        assertEquals(List.of("alpha", "beta"),
                TagsListComponent.normalizeTags(List.of("alpha", "beta", "alpha")));
    }

    @Test
    public void duplicatesThatDifferOnlyByWhitespaceAreCollapsed() {
        assertEquals(List.of("alpha"),
                TagsListComponent.normalizeTags(List.of("alpha", " alpha", "alpha  ")));
    }
}
