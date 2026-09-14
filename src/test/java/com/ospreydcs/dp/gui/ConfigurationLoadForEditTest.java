package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.common.Attribute;
import com.ospreydcs.dp.grpc.v1.common.Configuration;
import com.ospreydcs.dp.gui.component.AttributesListComponent;
import com.ospreydcs.dp.gui.component.TagsListComponent;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests loading an existing configuration record into the machine configuration editor.
 *
 * Three things are guarded, and all three are silent failures:
 *
 *   1. The Critical Integration Pattern. The save reads tags and attributes from the injected
 *      COMPONENTS. A load that wrote them into ViewModel properties instead would be write-only, and
 *      editing any unrelated field and saving -- a full-replace upsert -- would write both back as
 *      absent.
 *
 *   2. The activation section gate. That section is bound to configurationSaved, whose real
 *      invariant is "the server holds a Configuration under savedConfigurationName" -- the server
 *      rejects an activation whose configuration name does not resolve. A loaded record establishes
 *      that invariant, so leaving the section disabled after a load would deny the one operation the
 *      load makes safe.
 *
 *   3. The section is bound to the RECORD's name, not to the still-editable text field. A user who
 *      loads "rf-cavity", retypes the name, and then adds an activation must get an activation of
 *      "rf-cavity" -- which is what the server will accept.
 */
public class ConfigurationLoadForEditTest {

    private static Configuration fullRecord() {
        return Configuration.newBuilder()
                .setConfigurationName("rf-cavity")
                .setCategory("accelerator")
                .setDescription("the RF cavity configuration")
                .setParentConfigurationName("site-root")
                .addTags("critical")
                .addTags("vacuum")
                .addAttributes(Attribute.newBuilder().setName("hall").setValue("east"))
                .addAttributes(Attribute.newBuilder().setName("owner").setValue("rf-group"))
                .setModifiedBy("an-operator")
                .build();
    }

    private record Fixture(
            MachineConfigurationViewModel viewModel,
            TagsListComponent tags,
            AttributesListComponent attributes
    ) { }

    private static Fixture loadedFixture(Configuration record) throws Exception {
        final Fixture[] holder = new Fixture[1];

        FxToolkitSupport.runOnFxThread(() -> {
            // real components, because the load path drives their addTag()/addAttribute() APIs
            final TagsListComponent tags = new TagsListComponent();
            final AttributesListComponent attributes = new AttributesListComponent();

            final MachineConfigurationViewModel viewModel = new MachineConfigurationViewModel();
            viewModel.setConfigurationTagsComponent(tags);
            viewModel.setConfigurationAttributesComponent(attributes);

            viewModel.loadFromConfiguration(record);
            holder[0] = new Fixture(viewModel, tags, attributes);
        });

        return holder[0];
    }

    @Test
    public void loadingPopulatesTheComponentsThatTheSaveActuallyReads() throws Exception {
        final Fixture fixture = loadedFixture(fullRecord());

        assertEquals(List.of("critical", "vacuum"), List.copyOf(fixture.tags().getTags()),
                "tags must land in the component, which is where the save reads them");
        assertEquals(List.of("hall=east", "owner=rf-group"),
                List.copyOf(fixture.attributes().getAttributes()),
                "attributes must land in the component, which is where the save reads them");
    }

    @Test
    public void loadingPopulatesTheScalarFields() throws Exception {
        final Fixture fixture = loadedFixture(fullRecord());

        assertEquals("rf-cavity", fixture.viewModel().configurationNameProperty().get());
        assertEquals("accelerator", fixture.viewModel().categoryProperty().get());
        assertEquals("the RF cavity configuration",
                fixture.viewModel().configurationDescriptionProperty().get());
        assertEquals("site-root", fixture.viewModel().parentConfigurationNameProperty().get());
        assertEquals("an-operator", fixture.viewModel().configurationModifiedByProperty().get());
    }

    /**
     * The load enables the activation section, because the record's existence is exactly the
     * invariant the gate protects.
     */
    @Test
    public void loadingEnablesTheActivationSection() throws Exception {
        final Fixture fixture = loadedFixture(fullRecord());

        assertTrue(fixture.viewModel().configurationSavedProperty().get(),
                "a loaded record proves the server holds a Configuration under this name, which is "
                        + "what the activation section is gated on -- leaving it disabled would deny "
                        + "the one operation the load makes safe");
        assertEquals("rf-cavity", fixture.viewModel().savedConfigurationNameProperty().get(),
                "the activation section must be bound to the record's name");
    }

    /**
     * Retyping the name field must not re-point the activation section. The server resolves an
     * activation's configuration name on every save, so an activation built against an unsaved name
     * would be rejected -- and the session list would describe activations of a configuration that
     * does not exist.
     */
    @Test
    public void retypingTheNameFieldDoesNotRepointTheActivationSection() throws Exception {
        final Fixture fixture = loadedFixture(fullRecord());

        FxToolkitSupport.runOnFxThread(() ->
                fixture.viewModel().configurationNameProperty().set("something-else-entirely"));

        assertEquals("rf-cavity", fixture.viewModel().savedConfigurationNameProperty().get(),
                "the activation section stays bound to the name the server actually holds, not to "
                        + "the editable text field");
    }

    @Test
    public void loadingASecondRecordDoesNotCarryOverTheFirstOnesMetadata() throws Exception {
        final Fixture fixture = loadedFixture(fullRecord());

        final Configuration second = Configuration.newBuilder()
                .setConfigurationName("linac")
                .setCategory("accelerator")
                .addTags("secondary")
                .build();

        FxToolkitSupport.runOnFxThread(() -> fixture.viewModel().loadFromConfiguration(second));

        assertEquals("linac", fixture.viewModel().configurationNameProperty().get());
        assertEquals(List.of("secondary"), List.copyOf(fixture.tags().getTags()),
                "the first record's tags must not survive into the second, where a save would write "
                        + "them onto a record that never had them");
        assertTrue(fixture.attributes().getAttributes().isEmpty(),
                "the first record's attributes must not survive into the second");
        assertEquals("", fixture.viewModel().configurationDescriptionProperty().get(),
                "a field absent from the second record must be cleared, not inherited");
        assertEquals("", fixture.viewModel().parentConfigurationNameProperty().get());
        assertEquals("linac", fixture.viewModel().savedConfigurationNameProperty().get(),
                "the activation section must re-point to the newly loaded record");
    }

    @Test
    public void loadingAMinimalRecordLeavesTheListComponentsEmpty() throws Exception {
        final Fixture fixture = loadedFixture(Configuration.newBuilder()
                .setConfigurationName("bare")
                .setCategory("misc")
                .build());

        assertEquals("bare", fixture.viewModel().configurationNameProperty().get());
        assertTrue(fixture.tags().getTags().isEmpty());
        assertTrue(fixture.attributes().getAttributes().isEmpty());
        assertTrue(fixture.viewModel().configurationSavedProperty().get());
    }

    @Test
    public void loadingANullRecordIsANoOp() throws Exception {
        final Fixture fixture = loadedFixture(fullRecord());

        FxToolkitSupport.runOnFxThread(() -> fixture.viewModel().loadFromConfiguration(null));

        assertEquals("rf-cavity", fixture.viewModel().configurationNameProperty().get(),
                "a null record must leave the loaded form untouched rather than clearing it");
        assertEquals(2, fixture.tags().getTags().size());
        assertTrue(fixture.viewModel().configurationSavedProperty().get());
    }

    /** Reset must undo a load completely, including the gate the load opened. */
    @Test
    public void resettingAfterALoadClosesTheActivationGate() throws Exception {
        final Fixture fixture = loadedFixture(fullRecord());
        assertTrue(fixture.viewModel().configurationSavedProperty().get());

        FxToolkitSupport.runOnFxThread(() -> fixture.viewModel().resetForm());

        assertFalse(fixture.viewModel().configurationSavedProperty().get(),
                "reset must re-disable the activation section, which the load enabled");
        assertEquals("", fixture.viewModel().savedConfigurationNameProperty().get());
        assertEquals("", fixture.viewModel().configurationNameProperty().get());
        assertTrue(fixture.tags().getTags().isEmpty());
    }
}
