package com.ospreydcs.dp.gui.component;

import com.ospreydcs.dp.grpc.v1.common.ColumnMetadata;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import javafx.collections.FXCollections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ColumnMetadataComponent.getColumnMetadata(), whose contract is that a component
 * with nothing entered returns null — so ingested columns carry no metadata field at all
 * rather than an empty one — and that unset provenance fields are omitted rather than sent
 * as empty strings, per the ColumnProvenance contract in common.proto.  Needs a live
 * component instance (the fields live in embedded child components), so it runs on the FX
 * thread via FxToolkitSupport.
 */
public class ColumnMetadataComponentTest {

    @Test
    public void freshComponentReturnsNullMetadata() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final ColumnMetadataComponent component = new ColumnMetadataComponent();
            assertNull(component.getColumnMetadata());
        });
    }

    @Test
    public void whitespaceOnlyProvenanceStillReturnsNullMetadata() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final ColumnMetadataComponent component = new ColumnMetadataComponent();
            component.setProvenanceSource("   ");
            component.setProvenanceProcess("   ");
            assertNull(component.getColumnMetadata());
        });
    }

    @Test
    public void sourceOnlyBuildsProvenanceWithProcessOmitted() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final ColumnMetadataComponent component = new ColumnMetadataComponent();
            component.setProvenanceSource("  ioc-42 ");

            final ColumnMetadata metadata = component.getColumnMetadata();
            assertNotNull(metadata);
            assertTrue(metadata.hasProvenance());
            assertEquals("ioc-42", metadata.getProvenance().getSource());
            // proto3 plain string: unset means empty, and the builder must not have set it
            assertEquals("", metadata.getProvenance().getProcess());
            assertEquals(0, metadata.getTagsCount());
            assertEquals(0, metadata.getAttributesCount());
        });
    }

    @Test
    public void tagsAndAttributesArePropagatedWithoutProvenance() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final ColumnMetadataComponent component = new ColumnMetadataComponent();
            component.setColumnTags(FXCollections.observableArrayList("raw", "calibrated"));
            component.setColumnAttributes(FXCollections.observableArrayList("facility=SNS"));

            final ColumnMetadata metadata = component.getColumnMetadata();
            assertNotNull(metadata);
            assertFalse(metadata.hasProvenance());
            assertEquals(2, metadata.getTagsCount());
            assertEquals("raw", metadata.getTags(0));
            assertEquals("calibrated", metadata.getTags(1));
            assertEquals(1, metadata.getAttributesCount());
            assertEquals("facility", metadata.getAttributes(0).getName());
            assertEquals("SNS", metadata.getAttributes(0).getValue());
        });
    }

    @Test
    public void clearColumnMetadataRestoresNullContract() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final ColumnMetadataComponent component = new ColumnMetadataComponent();
            component.setProvenanceSource("ioc-42");
            component.setProvenanceProcess("archiver");
            component.setColumnTags(FXCollections.observableArrayList("raw"));
            component.setColumnAttributes(FXCollections.observableArrayList("facility=SNS"));
            assertNotNull(component.getColumnMetadata());

            component.clearColumnMetadata();
            assertNull(component.getColumnMetadata());
        });
    }
}
