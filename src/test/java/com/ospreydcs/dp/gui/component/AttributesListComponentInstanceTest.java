package com.ospreydcs.dp.gui.component;

import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import javafx.collections.FXCollections;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests against a real AttributesListComponent instance — FXML loaded, embedded controls
 * live — exercising the add/remove/set API that AttributesListComponentTest cannot reach
 * through the static parsing helpers alone.  Runs on the FX thread via FxToolkitSupport.
 */
public class AttributesListComponentInstanceTest {

    @Test
    public void addAttributeStoresTrimmedKeyEqualsValue() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final AttributesListComponent component = new AttributesListComponent();
            component.addAttribute(" facility ", " SNS ");
            component.addAttribute("sector", "3");
            assertEquals(List.of("facility=SNS", "sector=3"), component.getAttributes());
        });
    }

    @Test
    public void addAttributeIgnoresBlankKeyOrValueAndDuplicates() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final AttributesListComponent component = new AttributesListComponent();
            component.addAttribute("facility", "SNS");
            component.addAttribute("   ", "SNS");
            component.addAttribute("facility", "   ");
            component.addAttribute(null, "SNS");
            component.addAttribute("facility", null);
            component.addAttribute(" facility ", " SNS ");
            assertEquals(List.of("facility=SNS"), component.getAttributes());
        });
    }

    @Test
    public void removeAttributeRemovesOnlyThatAttribute() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final AttributesListComponent component = new AttributesListComponent();
            component.addAttribute("facility", "SNS");
            component.addAttribute("sector", "3");
            component.removeAttribute("facility=SNS");
            assertEquals(List.of("sector=3"), component.getAttributes());
        });
    }

    @Test
    public void clearAttributesEmptiesTheList() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final AttributesListComponent component = new AttributesListComponent();
            component.addAttribute("facility", "SNS");
            component.clearAttributes();
            assertEquals(List.of(), component.getAttributes());
        });
    }

    @Test
    public void setAttributesReplacesContentsAndNormalizes() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final AttributesListComponent component = new AttributesListComponent();
            component.addAttribute("stale", "entry");
            component.setAttributes(FXCollections.observableArrayList(
                    " facility = SNS ", "no-separator", "=blank-key", "facility=SNS"));
            assertEquals(List.of("facility=SNS"), component.getAttributes());
        });
    }
}
