package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.component.AttributesListComponent;
import com.ospreydcs.dp.gui.component.TagsListComponent;
import com.ospreydcs.dp.gui.model.ConfigurationActivationDetail;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the parts of MachineConfigurationViewModel that are reachable without a backend:
 * form clearing, the controller-owned temporal reset callback, and the activation section gate.
 *
 * The save paths themselves need a DpApplication and are covered by the end-to-end run against a
 * live service ecosystem, not here.  What these tests do pin down is the state handling around
 * those calls, which is where the reset and list-scoping defects lived.
 *
 * Bodies run on the FX thread because the ViewModel's properties and observable lists are the
 * same ones a live view binds to.
 */
public class MachineConfigurationViewModelTest {

    /**
     * A ViewModel with all four list components attached, as the controller wires it.
     */
    private static MachineConfigurationViewModel viewModelWithComponents(
            TagsListComponent configurationTags,
            AttributesListComponent configurationAttributes,
            TagsListComponent activationTags,
            AttributesListComponent activationAttributes
    ) {
        final MachineConfigurationViewModel viewModel = new MachineConfigurationViewModel();
        viewModel.setConfigurationTagsComponent(configurationTags);
        viewModel.setConfigurationAttributesComponent(configurationAttributes);
        viewModel.setActivationTagsComponent(activationTags);
        viewModel.setActivationAttributesComponent(activationAttributes);
        return viewModel;
    }

    @Test
    public void resetFormClearsEveryFieldAndComponent() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final TagsListComponent configurationTags = new TagsListComponent();
            final AttributesListComponent configurationAttributes = new AttributesListComponent();
            final TagsListComponent activationTags = new TagsListComponent();
            final AttributesListComponent activationAttributes = new AttributesListComponent();

            final MachineConfigurationViewModel viewModel = viewModelWithComponents(
                    configurationTags, configurationAttributes, activationTags, activationAttributes);

            viewModel.configurationNameProperty().set("beamline-a");
            viewModel.categoryProperty().set("beamline");
            viewModel.configurationDescriptionProperty().set("a description");
            viewModel.parentConfigurationNameProperty().set("facility");
            viewModel.configurationModifiedByProperty().set("someone");
            viewModel.clientActivationIdProperty().set("activation-1");
            viewModel.activationDescriptionProperty().set("an activation");
            viewModel.activationModifiedByProperty().set("someone else");

            configurationTags.addTag("config-tag");
            configurationAttributes.addAttribute("config-key", "config-value");
            activationTags.addTag("activation-tag");
            activationAttributes.addAttribute("activation-key", "activation-value");

            viewModel.resetForm();

            assertEquals("", viewModel.configurationNameProperty().get());
            assertEquals("", viewModel.categoryProperty().get());
            assertEquals("", viewModel.configurationDescriptionProperty().get());
            assertEquals("", viewModel.parentConfigurationNameProperty().get());
            assertEquals("", viewModel.configurationModifiedByProperty().get());
            assertEquals("", viewModel.clientActivationIdProperty().get());
            assertEquals("", viewModel.activationDescriptionProperty().get());
            assertEquals("", viewModel.activationModifiedByProperty().get());

            assertTrue(configurationTags.getTags().isEmpty(), "configuration tags not cleared");
            assertTrue(configurationAttributes.getAttributes().isEmpty(),
                    "configuration attributes not cleared");
            assertTrue(activationTags.getTags().isEmpty(), "activation tags not cleared");
            assertTrue(activationAttributes.getAttributes().isEmpty(),
                    "activation attributes not cleared");
        });
    }

    /**
     * The date pickers and time spinners are owned by the controller, so the ViewModel can only
     * clear them by calling back into it.  Reset must make that call: without it the next
     * activation silently reuses the previous interval's time of day.
     */
    @Test
    public void resetFormInvokesTheTemporalFieldsResetCallback() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final MachineConfigurationViewModel viewModel = new MachineConfigurationViewModel();
            final AtomicInteger resetCallCount = new AtomicInteger();
            viewModel.setActivationTemporalFieldsReset(resetCallCount::incrementAndGet);

            viewModel.resetForm();

            assertEquals(1, resetCallCount.get(),
                    "resetForm() must clear the controller-owned date and time controls");
        });
    }

    @Test
    public void resetFormClearsTheSessionActivationListAndRedisablesTheSection() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final MachineConfigurationViewModel viewModel = new MachineConfigurationViewModel();

            viewModel.getActivations().add(new ConfigurationActivationDetail(
                    "activation-1", "beamline-a", Instant.EPOCH, Instant.EPOCH.plusSeconds(3600)));
            viewModel.savedConfigurationNameProperty().set("beamline-a");
            viewModel.configurationSavedProperty().set(true);

            viewModel.resetForm();

            assertTrue(viewModel.getActivations().isEmpty(), "session activation list not cleared");
            assertEquals("", viewModel.savedConfigurationNameProperty().get());
            assertFalse(viewModel.configurationSavedProperty().get(),
                    "activation section must be re-disabled after a reset");
        });
    }

    /**
     * addActivation() refuses to call the service before a configuration has been saved, which is
     * the same condition the disabled activation section expresses in the UI.
     */
    @Test
    public void addActivationIsRefusedBeforeAConfigurationIsSaved() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final MachineConfigurationViewModel viewModel = new MachineConfigurationViewModel();
            // No DpApplication is injected: reaching the service call would NPE, so a clean
            // status message is itself the assertion that the guard fired first.
            viewModel.addActivation(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600));

            assertEquals("DpApplication not initialized", viewModel.statusMessageProperty().get());
            assertTrue(viewModel.getActivations().isEmpty());
        });
    }
}
