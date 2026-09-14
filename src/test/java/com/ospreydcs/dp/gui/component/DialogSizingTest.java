package com.ospreydcs.dp.gui.component;

import com.ospreydcs.dp.gui.model.PvSelection;
import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the dialog re-fitting that {@link DialogSizing} performs.
 *
 * <p>These assert on GEOMETRY rather than on control state, because the defect they guard was
 * invisible to control state: switching the PV selector to Metadata criteria grew the content past
 * the bottom of a window that had already sized itself for Name list, so Apply was enabled,
 * visible, managed and simply <em>off screen</em>. Every assertion about the button itself passed
 * while the button could not be reached.
 *
 * <p>A real {@link Stage} stands in for the {@code Dialog} here. A Dialog only acquires a window
 * when it is shown, and showing one blocks the FX thread, so the test drives the same
 * size-to-scene path against a stage holding the same FXML -- the property under test is whether
 * the window tracks the content's preferred height across a mode change, which is identical.
 */
class DialogSizingTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        FxToolkitSupport.ensureStarted();
    }

    /** How much taller the metadata mode is than the name-list mode, in pixels. */
    private static double contentGrowthOnModeChange(VBox root, RadioButton metadataRadio) {
        final double nameListHeight = root.prefHeight(-1);
        metadataRadio.setSelected(true);
        root.applyCss();
        root.layout();
        return root.prefHeight(-1) - nameListHeight;
    }

    /**
     * The premise of the whole fix: the metadata pane really is taller than the name-list pane, so
     * a window sized for the latter cannot show the former. If this ever stopped being true the
     * other assertions here would pass vacuously.
     */
    @Test
    @DisplayName("switching to metadata criteria grows the content")
    void metadataModeIsTallerThanNameList() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final FXMLLoader loader = new FXMLLoader(PvSelectorDialogController.class.getResource(
                    "/fxml/components/pv-selector-dialog.fxml"));
            final VBox root = loader.load();
            final PvSelectorDialogController controller = loader.getController();
            controller.setSelection(PvSelection.nameList(), List.of("PV:A"));

            final Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.show();
            try {
                final double growth = contentGrowthOnModeChange(
                        root, (RadioButton) root.lookup("#metadataRadio"));

                assertTrue(growth > 0,
                        "the metadata pane must be taller than the name-list pane, or a window "
                                + "sized for name list would have shown it all along; growth was "
                                + growth);
            } finally {
                stage.close();
            }
        });
    }

    /**
     * The regression itself. Without a re-fit the window keeps the height it was first laid out
     * for, so the grown content -- and the button bar beneath it -- extends past the bottom edge.
     */
    @Test
    @DisplayName("the window still fits its content after a mode change")
    void windowRefitsAfterModeChange() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final FXMLLoader loader = new FXMLLoader(PvSelectorDialogController.class.getResource(
                    "/fxml/components/pv-selector-dialog.fxml"));
            final VBox root = loader.load();
            final PvSelectorDialogController controller = loader.getController();
            controller.setSelection(PvSelection.nameList(), List.of("PV:A"));

            final Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
            try {
                ((RadioButton) root.lookup("#metadataRadio")).setSelected(true);
                root.applyCss();
                root.layout();

                // What DialogSizing.resizeToFit() does once a layout pass has run.
                stage.sizeToScene();

                assertTrue(stage.getHeight() + 1.0 >= root.prefHeight(-1),
                        "the window is " + stage.getHeight() + "px for content wanting "
                                + root.prefHeight(-1) + "px, so the content below the fold -- "
                                + "including the button bar -- is off screen");
            } finally {
                stage.close();
            }
        });
    }

    /**
     * Opening directly on metadata mode must fit too. The reported path was name-list -> metadata,
     * but a selection restored from a previous visit opens on its own mode with no toggle change
     * to trigger a re-fit, so the initial sizing has to be correct on its own.
     */
    @Test
    @DisplayName("opening directly on metadata criteria fits without a mode change")
    void openingOnMetadataModeFits() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final FXMLLoader loader = new FXMLLoader(PvSelectorDialogController.class.getResource(
                    "/fxml/components/pv-selector-dialog.fxml"));
            final VBox root = loader.load();
            final PvSelectorDialogController controller = loader.getController();
            controller.setSelection(
                    PvSelection.metadata(null, null, List.of("a-tag"), null), List.of("PV:A"));

            final Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
            try {
                assertTrue(stage.getHeight() + 1.0 >= root.prefHeight(-1),
                        "a dialog opened on metadata mode must fit its own content: window is "
                                + stage.getHeight() + "px for content wanting "
                                + root.prefHeight(-1) + "px");
            } finally {
                stage.close();
            }
        });
    }
}
