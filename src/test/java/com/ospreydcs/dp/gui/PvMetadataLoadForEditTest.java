package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.common.Attribute;
import com.ospreydcs.dp.grpc.v1.common.PvMetadata;
import com.ospreydcs.dp.gui.component.AttributesListComponent;
import com.ospreydcs.dp.gui.component.TagsListComponent;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests loading an existing PV metadata record into the editor.
 *
 * Two defects are guarded here, and BOTH are silent -- savePvMetadata() is a full-replace upsert, so
 * either one destroys stored data with no error, no warning, and nothing in the UI indicating a
 * loss:
 *
 *   1. The Critical Integration Pattern.  Aliases, tags and attributes are owned by the injected
 *      list components, and the save reads them from those instances.  A load that wrote them into
 *      ViewModel properties instead would put them somewhere the save never looks -- so editing any
 *      unrelated field and saving would write all three back as ABSENT.  That is precisely the
 *      Annotation Builder defect documented in CLAUDE.md, in a view that has the same shape.
 *
 *   2. The alias trap.  getPvMetadata() resolves aliases, so loading by a historical name returns
 *      the record under its CANONICAL name.  The form must carry that canonical name, because a save
 *      under the alias the user typed creates a SECOND record rather than updating the one loaded.
 *
 * The components are real instances rather than fakes: they load their own FXML, and the load path
 * calls addTag()/addAttribute() on them, so substituting a stub would test the test rather than the
 * integration.
 */
public class PvMetadataLoadForEditTest {

    /** A record with every multi-valued field populated, under a canonical name plus an alias. */
    private static PvMetadata fullRecord() {
        return PvMetadata.newBuilder()
                .setPvName("CANONICAL:NAME")
                .addAliases("OLD:NAME")
                .addAliases("LEGACY:NAME")
                .addTags("critical")
                .addTags("vacuum")
                .addAttributes(Attribute.newBuilder().setName("unit").setValue("volts"))
                .addAttributes(Attribute.newBuilder().setName("system").setValue("rf"))
                .setDescription("a described PV")
                .setModifiedBy("an-operator")
                .build();
    }

    private record Fixture(
            PvMetadataViewModel viewModel,
            TagsListComponent aliases,
            TagsListComponent tags,
            AttributesListComponent attributes
    ) { }

    private static Fixture loadedFixture(PvMetadata record) throws Exception {
        final Fixture[] holder = new Fixture[1];

        FxToolkitSupport.runOnFxThread(() -> {
            // real components, because the load path drives their addTag()/addAttribute() APIs
            final TagsListComponent aliases = new TagsListComponent();
            final TagsListComponent tags = new TagsListComponent();
            final AttributesListComponent attributes = new AttributesListComponent();

            final PvMetadataViewModel viewModel = new PvMetadataViewModel();
            viewModel.setAliasesComponent(aliases);
            viewModel.setTagsComponent(tags);
            viewModel.setAttributesComponent(attributes);

            viewModel.loadFromPvMetadata(record);
            holder[0] = new Fixture(viewModel, aliases, tags, attributes);
        });

        return holder[0];
    }

    /**
     * The Critical Integration Pattern.  The save reads these three fields from the COMPONENTS, so a
     * load that populated anything else would be write-only -- and the next save would silently
     * erase all three from the stored record.
     */
    @Test
    public void loadingPopulatesTheComponentsThatTheSaveActuallyReads() throws Exception {
        final Fixture fixture = loadedFixture(fullRecord());

        assertEquals(List.of("OLD:NAME", "LEGACY:NAME"), List.copyOf(fixture.aliases().getTags()),
                "aliases must land in the aliases component, which is where the save reads them");
        assertEquals(List.of("critical", "vacuum"), List.copyOf(fixture.tags().getTags()),
                "tags must land in the tags component, which is where the save reads them");
        assertEquals(List.of("unit=volts", "system=rf"), List.copyOf(fixture.attributes().getAttributes()),
                "attributes must land in the attributes component, which is where the save reads them");
    }

    /**
     * The alias trap.  The form must carry the record's canonical name, not whatever the user
     * searched by, because the save is keyed on the name in this field.
     */
    @Test
    public void loadingCarriesTheCanonicalNameFromTheRecord() throws Exception {
        final Fixture fixture = loadedFixture(fullRecord());

        assertEquals("CANONICAL:NAME", fixture.viewModel().pvNameProperty().get(),
                "the form must hold the record's canonical name -- saving under an alias would "
                        + "create a second record rather than updating the one loaded");
        assertEquals("a described PV", fixture.viewModel().descriptionProperty().get());
        assertEquals("an-operator", fixture.viewModel().modifiedByProperty().get());
    }

    /** The user must be told that the save will replace the whole record they just loaded. */
    @Test
    public void loadingSaysThatSavingReplacesTheWholeRecord() throws Exception {
        final Fixture fixture = loadedFixture(fullRecord());

        final String status = fixture.viewModel().statusMessageProperty().get();
        assertTrue(status.contains("CANONICAL:NAME"),
                "the status must name the record being edited, but was: " + status);
        assertTrue(status.contains("replaces the entire record"),
                "the status must state that the save is a full replace, but was: " + status);
    }

    /**
     * Loading a second record must not leave the first one's metadata behind.  Because the save is a
     * full-replace upsert, a leftover alias or tag would be written onto the SECOND record as though
     * it had always belonged there.
     */
    @Test
    public void loadingASecondRecordDoesNotCarryOverTheFirstOnesMetadata() throws Exception {
        final Fixture fixture = loadedFixture(fullRecord());

        final PvMetadata second = PvMetadata.newBuilder()
                .setPvName("OTHER:NAME")
                .addAliases("OTHER:ALIAS")
                .build();

        FxToolkitSupport.runOnFxThread(() -> fixture.viewModel().loadFromPvMetadata(second));

        assertEquals("OTHER:NAME", fixture.viewModel().pvNameProperty().get());
        assertEquals(List.of("OTHER:ALIAS"), List.copyOf(fixture.aliases().getTags()),
                "the first record's aliases must not survive into the second");
        assertTrue(fixture.tags().getTags().isEmpty(),
                "the first record's tags must not survive into the second, where a save would write "
                        + "them onto a record that never had them");
        assertTrue(fixture.attributes().getAttributes().isEmpty(),
                "the first record's attributes must not survive into the second");
        assertEquals("", fixture.viewModel().descriptionProperty().get(),
                "a field absent from the second record must be cleared, not inherited");
        assertEquals("", fixture.viewModel().modifiedByProperty().get());
    }

    /** A record with no aliases, tags or attributes must load cleanly rather than throwing. */
    @Test
    public void loadingAMinimalRecordLeavesTheListComponentsEmpty() throws Exception {
        final Fixture fixture = loadedFixture(
                PvMetadata.newBuilder().setPvName("BARE:NAME").build());

        assertEquals("BARE:NAME", fixture.viewModel().pvNameProperty().get());
        assertTrue(fixture.aliases().getTags().isEmpty());
        assertTrue(fixture.tags().getTags().isEmpty());
        assertTrue(fixture.attributes().getAttributes().isEmpty());
    }

    @Test
    public void loadingANullRecordIsANoOp() throws Exception {
        final Fixture fixture = loadedFixture(fullRecord());

        FxToolkitSupport.runOnFxThread(() -> fixture.viewModel().loadFromPvMetadata(null));

        assertEquals("CANONICAL:NAME", fixture.viewModel().pvNameProperty().get(),
                "a null record must leave the loaded form untouched rather than clearing it");
        assertEquals(2, fixture.aliases().getTags().size());
    }
}
