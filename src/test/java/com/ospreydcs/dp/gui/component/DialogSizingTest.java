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
 * <p><strong>These do NOT reproduce the reported defect, and must not be read as proof the fix
 * works.</strong>  The bug was observed on macOS: the dialog opened clipped, with Apply below its
 * bottom edge, every time -- until the user resized it once, after which it "remembered" the larger
 * size.  That last detail identifies it as JavaFX's dialog size PERSISTENCE interacting with a
 * first layout that happened before the taller pane was managed, not as content simply outgrowing
 * a window.  Neither half reproduces headlessly: a showing {@code Dialog} here re-lays out on its
 * own (measured at 452px of window for 424px of content, identical with the fix removed), and there
 * is no prior user resize to have been cached.
 *
 * <p>An earlier version of this class claimed to pin the defect and did not.  It drove a bare
 * {@link Stage} -- which, unlike a Dialog, does not auto-fit -- and then called {@code
 * sizeToScene()} ITSELF, so it asserted that JavaFX can resize a window rather than that production
 * asks it to.  It passed with the production listener deleted outright.  The numbers that version
 * reported (275px against 377px) were measured on that stage and do not describe the Dialog.
 *
 * <p>What remains here is worth keeping for what it actually checks: that the wiring is reachable
 * and runs without error, that an all-empty metadata query really does raise the warning that grows
 * the content, and that the dialog is self-consistent after both kinds of change.  Reproducing the
 * platform behaviour itself needs a real window manager -- see
 * {@code plan/tickets/39/manual-verification.md}.
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
     * The premise of the whole fix: the metadata pane really is taller than the name-list pane.
     *
     * <p>This one is a straightforward measurement of the FXML and is unaffected by the Dialog-vs-
     * Stage distinction above -- it compares the content's own preferred height before and after a
     * mode change, and never asserts anything about a window. If it ever stopped holding, there
     * would be nothing for the refit to fix.
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
     * Drives the real dialog through {@link PvSelectorDialogController#prepareDialog}, which wires
     * every listener and stops short of the blocking {@code showAndWait()}.
     *
     * <p>This passes with the production refit removed -- see the class javadoc -- so it is a
     * consistency check, not a regression test.  It still earns its place: it exercises the real
     * wiring end to end, so a listener that threw, or a seam that stopped building the dialog,
     * would fail here.
     */
    @Test
    @DisplayName("the real dialog stays fitted across a mode change")
    void preparedDialogRefitsAfterModeChange() throws Exception {
        final PvSelectorDialogController.PreparedDialog prepared = FxToolkitSupport.callOnFxThread(
                () -> PvSelectorDialogController.prepareDialog(
                        PvSelection.nameList(), List.of("PV:A"), null));

        final javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog =
                prepared.dialog();
        try {
            FxToolkitSupport.runOnFxThread(dialog::show);
            settleFxEvents();

            FxToolkitSupport.runOnFxThread(() -> {
                final VBox content = (VBox) dialog.getDialogPane().getContent();
                ((RadioButton) content.lookup("#metadataRadio")).setSelected(true);
            });

            // The production resize is queued, not immediate -- a layout pass has to run first.
            settleFxEvents();

            final boolean fits = FxToolkitSupport.callOnFxThread(
                    () -> DialogSizing.fitsContent(dialog));

            assertTrue(fits, "the dialog did not re-fit after switching to metadata criteria, so "
                    + "the content below the fold -- including Apply -- is off screen");
        } finally {
            FxToolkitSupport.runOnFxThread(dialog::close);
        }
    }

    /**
     * The warning label changes this dialog's height independently of the mode, and the first
     * version of the fix did not cover it (Copilot review of PR #46, finding 1).
     *
     * <p>The load-bearing assertion here is the one about the WARNING: clearing the last metadata
     * criterion must raise the whole-archive warning.  That is a real behavioural claim, and it is
     * what makes the sizing path reachable at all.
     */
    @Test
    @DisplayName("clearing the last criterion raises the warning that grows the dialog")
    void preparedDialogRefitsWhenWarningAppears() throws Exception {
        final PvSelectorDialogController.PreparedDialog prepared = FxToolkitSupport.callOnFxThread(
                () -> PvSelectorDialogController.prepareDialog(
                        PvSelection.metadata(null, null, List.of("a-tag"), null),
                        List.of("PV:A"), null));

        final javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog =
                prepared.dialog();
        try {
            FxToolkitSupport.runOnFxThread(dialog::show);
            settleFxEvents();

            // Clearing the only criterion makes this an all-empty metadata query, which is valid
            // but covers the whole archive -- so the warning appears and the content grows.
            FxToolkitSupport.runOnFxThread(() -> {
                final VBox content = (VBox) dialog.getDialogPane().getContent();
                ((javafx.scene.control.TextField) content.lookup("#metadataTagsField")).setText("");
            });
            settleFxEvents();

            final boolean warningShown = FxToolkitSupport.callOnFxThread(() -> {
                final VBox content = (VBox) dialog.getDialogPane().getContent();
                return content.lookup("#warningLabel").isManaged();
            });
            assertTrue(warningShown,
                    "an all-empty metadata query must warn, or this test proves nothing");

            final boolean fits = FxToolkitSupport.callOnFxThread(
                    () -> DialogSizing.fitsContent(dialog));

            assertTrue(fits, "the dialog did not re-fit when the warning appeared, so the content "
                    + "below the fold -- including Apply -- is off screen");
        } finally {
            FxToolkitSupport.runOnFxThread(dialog::close);
        }
    }

    /** Drains queued FX events, so a deferred resize has run before anything is asserted. */
    private static void settleFxEvents() throws Exception {
        for (int pass = 0; pass < 5; pass++) {
            FxToolkitSupport.runOnFxThread(() -> {
            });
        }
    }
}
