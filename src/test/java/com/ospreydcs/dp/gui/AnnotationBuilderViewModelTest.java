package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.grpc.v1.annotation.Annotation;
import com.ospreydcs.dp.grpc.v1.common.Attribute;
import com.ospreydcs.dp.gui.component.AttributesListComponent;
import com.ospreydcs.dp.gui.component.TagsListComponent;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the tag and attribute round trip through AnnotationBuilderViewModel.
 *
 * These guard a silent data-loss defect, not a formatting detail.  loadFromAnnotation() used to
 * populate ViewModel-owned lists while DataExploreController.onSaveAnnotation() read the injected
 * components, so a loaded annotation's tags and attributes landed where the save never looked.
 * Because saveAnnotation() is a full-replace upsert, editing any unrelated field and saving then
 * wrote them back as absent -- no error, no warning, and nothing in the UI showing a loss.
 *
 * The ViewModel now holds no collections for them, so what these tests really pin down is that
 * load and save share one source of truth: what getTags() / getAttributes() report after a load is
 * exactly what the controller sends to the API.
 *
 * Bodies run on the FX thread because the components are live JavaFX controls.
 */
public class AnnotationBuilderViewModelTest {

    private static Annotation annotationWithMetadata() {
        return Annotation.newBuilder()
                .setId("ann-1")
                .setName("annotation-1")
                .setDescription("a description")
                .addTags("alpha")
                .addTags("beta")
                .addAttributes(Attribute.newBuilder().setName("key-1").setValue("value-1").build())
                .addAttributes(Attribute.newBuilder().setName("key-2").setValue("value-2").build())
                .addDataSetIds("ds-1")
                .build();
    }

    /**
     * A ViewModel wired to components the way DataExploreController.initializeAnnotationBuilder()
     * wires it.
     */
    private static AnnotationBuilderViewModel viewModelWithComponents(
            TagsListComponent tags, AttributesListComponent attributes) {

        final AnnotationBuilderViewModel viewModel = new AnnotationBuilderViewModel();
        viewModel.setTagsComponent(tags);
        viewModel.setAttributesComponent(attributes);
        return viewModel;
    }

    /**
     * The core guard: a loaded annotation's tags and attributes must be visible where the save
     * reads them.  Asserting against the COMPONENTS is the point -- that is the source
     * onSaveAnnotation() uses, so a load that populated anything else fails here.
     */
    @Test
    public void loadingAnAnnotationPopulatesTheComponentsTheSaveReads() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final TagsListComponent tags = new TagsListComponent();
            final AttributesListComponent attributes = new AttributesListComponent();
            final AnnotationBuilderViewModel viewModel = viewModelWithComponents(tags, attributes);

            viewModel.loadFromAnnotation(annotationWithMetadata());

            assertEquals(List.of("alpha", "beta"), List.copyOf(tags.getTags()),
                    "loaded tags are not in the tags component, so onSaveAnnotation() would save "
                            + "the annotation with its tags dropped");
            assertEquals(List.of("key-1=value-1", "key-2=value-2"),
                    List.copyOf(attributes.getAttributes()),
                    "loaded attributes are not in the attributes component, so onSaveAnnotation() "
                            + "would save the annotation with its attributes dropped");
        });
    }

    /**
     * The same assertion stated as the save sees it: the ViewModel's accessors and the components
     * must agree, since the ViewModel no longer keeps a second copy that could diverge.
     */
    @Test
    public void loadedMetadataIsReportedByTheViewModelAccessors() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final TagsListComponent tags = new TagsListComponent();
            final AttributesListComponent attributes = new AttributesListComponent();
            final AnnotationBuilderViewModel viewModel = viewModelWithComponents(tags, attributes);

            viewModel.loadFromAnnotation(annotationWithMetadata());

            assertEquals(List.of("alpha", "beta"), viewModel.getTags());
            assertEquals(List.of("key-1=value-1", "key-2=value-2"), viewModel.getAttributes());
        });
    }

    /**
     * Loading a second annotation must not leave the first one's metadata behind: the save is a
     * full replace, so a leftover tag is written to the newly loaded annotation as though it had
     * always been there.
     */
    @Test
    public void loadingAnotherAnnotationDoesNotRetainThePreviousMetadata() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final TagsListComponent tags = new TagsListComponent();
            final AttributesListComponent attributes = new AttributesListComponent();
            final AnnotationBuilderViewModel viewModel = viewModelWithComponents(tags, attributes);

            viewModel.loadFromAnnotation(annotationWithMetadata());
            viewModel.loadFromAnnotation(Annotation.newBuilder()
                    .setId("ann-2")
                    .setName("annotation-2")
                    .addTags("gamma")
                    .addDataSetIds("ds-2")
                    .build());

            assertEquals(List.of("gamma"), viewModel.getTags(),
                    "the previous annotation's tags survived the load");
            assertEquals(List.of(), viewModel.getAttributes(),
                    "the previous annotation's attributes survived the load");
        });
    }

    /**
     * Reset must clear the components, not just ViewModel state.  It previously left the controls
     * populated, so a reset form still carried the prior annotation's metadata into the next save.
     */
    @Test
    public void resetClearsTheTagAndAttributeComponents() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final TagsListComponent tags = new TagsListComponent();
            final AttributesListComponent attributes = new AttributesListComponent();
            final AnnotationBuilderViewModel viewModel = viewModelWithComponents(tags, attributes);

            viewModel.loadFromAnnotation(annotationWithMetadata());
            viewModel.resetAnnotation();

            assertTrue(tags.getTags().isEmpty(),
                    "reset left tags in the component, which the next save would pick up");
            assertTrue(attributes.getAttributes().isEmpty(),
                    "reset left attributes in the component, which the next save would pick up");
            assertEquals("", viewModel.getAnnotationName());
            assertEquals("", viewModel.getDescription());
        });
    }

    /**
     * Tag and attribute edits must still drive the Reset button, which is what the ViewModel's own
     * removed lists were being observed for.
     */
    @Test
    public void componentEditsStillDriveButtonState() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final TagsListComponent tags = new TagsListComponent();
            final AttributesListComponent attributes = new AttributesListComponent();
            final AnnotationBuilderViewModel viewModel = viewModelWithComponents(tags, attributes);

            assertEquals(false, viewModel.resetButtonEnabledProperty().get(),
                    "an untouched form should have nothing to reset");

            tags.addTag("alpha");

            assertEquals(true, viewModel.resetButtonEnabledProperty().get(),
                    "adding a tag left the Reset button disabled, so the component's list is not "
                            + "observed for button state");
        });
    }

    /**
     * Without components injected, a load must not throw.  FXML loading constructs the ViewModel
     * before the controller injects anything (ViewLoadSmokeTest exercises exactly that state), so
     * the accessors have to tolerate it.
     */
    @Test
    public void loadWithoutInjectedComponentsIsTolerated() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final AnnotationBuilderViewModel viewModel = new AnnotationBuilderViewModel();

            viewModel.loadFromAnnotation(annotationWithMetadata());

            assertEquals(List.of(), viewModel.getTags());
            assertEquals(List.of(), viewModel.getAttributes());
            assertEquals("annotation-1", viewModel.getAnnotationName());
        });
    }
}
