package com.ospreydcs.dp.gui.component;

import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import javafx.collections.FXCollections;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests against a real TagsListComponent instance — FXML loaded, embedded controls live —
 * exercising the public API that TagsListComponentTest cannot reach through the static
 * normalizeTags() helper alone.  Runs on the FX thread via FxToolkitSupport.
 */
public class TagsListComponentInstanceTest {

    @Test
    public void addTagTrimsAndAppendsInOrder() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final TagsListComponent component = new TagsListComponent();
            component.addTag("  alpha ");
            component.addTag("beta");
            assertEquals(List.of("alpha", "beta"), component.getTags());
        });
    }

    @Test
    public void addTagIgnoresBlanksAndDuplicates() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final TagsListComponent component = new TagsListComponent();
            component.addTag("alpha");
            component.addTag(null);
            component.addTag("   ");
            component.addTag(" alpha ");
            assertEquals(List.of("alpha"), component.getTags());
        });
    }

    @Test
    public void removeTagRemovesOnlyThatTag() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final TagsListComponent component = new TagsListComponent();
            component.addTag("alpha");
            component.addTag("beta");
            component.removeTag("alpha");
            assertEquals(List.of("beta"), component.getTags());
        });
    }

    @Test
    public void clearTagsEmptiesTheList() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final TagsListComponent component = new TagsListComponent();
            component.addTag("alpha");
            component.addTag("beta");
            component.clearTags();
            assertEquals(List.of(), component.getTags());
        });
    }

    @Test
    public void setTagsReplacesContentsAndNormalizes() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final TagsListComponent component = new TagsListComponent();
            component.addTag("stale");
            component.setTags(FXCollections.observableArrayList(" alpha ", "", "alpha", "beta"));
            assertEquals(List.of("alpha", "beta"), component.getTags());
        });
    }

    @Test
    public void labelTextPropertyRoundTrips() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final TagsListComponent component = new TagsListComponent();
            component.setLabelText("Aliases:");
            assertEquals("Aliases:", component.getLabelText());
        });
    }
}
