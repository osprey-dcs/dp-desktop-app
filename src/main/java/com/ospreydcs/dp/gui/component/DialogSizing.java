package com.ospreydcs.dp.gui.component;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Window;

/**
 * Keeps a dialog fitted to content whose size changes after it is first shown.
 *
 * <p><strong>A {@code Dialog} sizes itself once.</strong>  It computes its preferred size when the
 * scene is first laid out and then keeps it, so a dialog whose content grows later ends up with
 * part of that content -- including the button bar, which sits BELOW the content -- outside the
 * window.
 *
 * <p>The two dialogs reach that state by different routes, and a helper written for only one of
 * them would miss the other:
 *
 * <ul>
 *   <li>{@code PvSelectorDialogController} shows one detail pane per mode and sets the others
 *       {@code managed=false}, so every MODE CHANGE alters the preferred height.  It also has a
 *       warning label (below).</li>
 *   <li>{@code QueryFiltersDialogController} keeps both filter panes managed and merely disables
 *       them, so its mode-equivalent costs nothing.  Its height changes only through its WARNING
 *       LABEL.</li>
 * </ul>
 *
 * <p>Both carry a warning label that is {@code managed=false} until it has something to say, so in
 * both dialogs a warning appearing or clearing grows the content exactly as a mode change does.
 * That is why {@link #resizeToFit} is exposed separately from {@link #resizeToFitOnModeChange}:
 * the toggle group is specific to one dialog, the warning is common to both.
 *
 * <p>The failure this fixes was reported as confusion rather than as a bug, which is the part worth
 * remembering.  The PV selector opens on Name list -- by far its smallest mode -- so switching to
 * Metadata criteria grew the content and pushed <em>Apply</em> past the bottom edge.  Because the
 * dialog is resizable the button was still reachable by dragging the window larger, so it read as
 * "this feature is awkward" rather than "this is broken", and the natural next move (typing the
 * criteria and pressing Enter, or closing the dialog) silently discards the edit.
 *
 * <p>Sizing is not clamped to the owner's bounds.  {@code sizeToScene()} grows the window to its
 * content, and a mode whose content is taller than the screen would be mis-sized -- but each of
 * these panes is a handful of rows, and clamping would reintroduce the same class of problem by
 * cutting off the button bar on a small display.  If a pane ever grows past that, it wants a
 * {@code ScrollPane}, not a clamp.
 */
final class DialogSizing {

    private DialogSizing() {
    }

    /**
     * Re-fits {@code dialog} whenever {@code toggleGroup}'s selection changes.
     *
     * <p>The resize is deferred with {@link javafx.application.Platform#runLater}: the toggle
     * listener that swaps pane visibility and this one both fire on the same change, in
     * registration order, and a layout pass has to run in between for the new preferred height to
     * exist.  Sizing synchronously here would measure the content as it was BEFORE the swap, which
     * is the same stale-by-one-frame reading that would leave the dialog sized for the mode the
     * user just left.
     */
    static void resizeToFitOnModeChange(Dialog<?> dialog, ToggleGroup toggleGroup) {
        toggleGroup.selectedToggleProperty().addListener(
                (obs, oldToggle, newToggle) -> resizeToFit(dialog));
    }

    /** Re-fits {@code dialog} to its content on the next pulse, if it is showing. */
    static void resizeToFit(Dialog<?> dialog) {
        javafx.application.Platform.runLater(() -> {
            final Scene scene = dialog.getDialogPane().getScene();
            if (scene == null) {
                return;
            }
            final Window window = scene.getWindow();
            if (window == null) {
                return;
            }
            window.sizeToScene();
        });
    }

    /**
     * Whether {@code dialog}'s window is tall enough to show its whole content.
     *
     * <p>Exists for the tests: the defect is "the button bar is below the window's bottom edge",
     * which is a geometric fact about the laid-out scene rather than anything the controls report
     * about themselves. A test asserting on control state alone would pass against the bug.
     */
    static boolean fitsContent(Dialog<?> dialog) {
        final Scene scene = dialog.getDialogPane().getScene();
        if (scene == null || scene.getWindow() == null) {
            return false;
        }
        // prefHeight(-1) is what the content WANTS; the window height is what it GOT.  A small
        // tolerance absorbs sub-pixel rounding between the two.
        final double required = dialog.getDialogPane().prefHeight(-1);
        return scene.getWindow().getHeight() + 1.0 >= required;
    }
}
