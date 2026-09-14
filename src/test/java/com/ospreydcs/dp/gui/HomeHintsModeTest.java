package com.ospreydcs.dp.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The home view's hint text branches on mode, because every other branch instructs the user to use
 * a menu that deployment mode disables.
 *
 * <p>The failure this guards is quiet rather than loud: the hints would still render, still look
 * like guidance, and simply point at greyed-out items -- which reads as the application being
 * broken rather than as the mode being different.
 */
public class HomeHintsModeTest {

    @Test
    @DisplayName("demo mode hints name the ingestion menus")
    public void testDemoHintsNameIngestionMenus() {
        final HomeViewModel viewModel = new HomeViewModel();
        viewModel.setDeploymentMode(false);
        assertTrue(viewModel.hintsTextProperty().get().contains("Ingest→Generate"),
                "demo mode should still direct the user to ingest something first");
    }

    @Test
    @DisplayName("deployment mode hints never name a disabled menu")
    public void testDeploymentHintsNameNoDisabledMenu() {
        final HomeViewModel viewModel = new HomeViewModel();
        viewModel.setDeploymentMode(true);

        final String hints = viewModel.hintsTextProperty().get();
        assertFalse(hints.contains("Ingest→Generate"), "Ingest > Generate is disabled in deployment mode");
        assertFalse(hints.contains("Ingest→Import"), "Ingest > Import is disabled in deployment mode");
        assertTrue(hints.contains("Explore→Data"), "the Explore views are what IS available");
    }

    /**
     * The mode wins over every ingestion-derived branch, including the post-ingestion one.  In
     * deployment mode {@code hasIngestedData} is expected to stay false, but nothing structurally
     * prevents it from being set, and the branch it would select advertises menus the mode
     * disables.
     */
    @Test
    @DisplayName("deployment hints survive an ingestion state change")
    public void testDeploymentHintsSurviveIngestionState() {
        final HomeViewModel viewModel = new HomeViewModel();
        viewModel.setDeploymentMode(true);
        final String before = viewModel.hintsTextProperty().get();

        viewModel.updateDataIngestedState(true);
        assertEquals(before, viewModel.hintsTextProperty().get(),
                "an ingestion state change must not restore the demo hints in deployment mode");

        viewModel.updateQueriesPerformedState(true);
        assertEquals(before, viewModel.hintsTextProperty().get(),
                "a query state change must not restore the demo hints in deployment mode");
    }

    /**
     * Reset is session-scoped; the mode is a property of how the application was launched.  A reset
     * that cleared it would silently re-advertise the ingestion menus.
     */
    @Test
    @DisplayName("resetting the session state keeps the mode")
    public void testResetKeepsMode() {
        final HomeViewModel viewModel = new HomeViewModel();
        viewModel.setDeploymentMode(true);

        viewModel.resetApplicationState();

        assertTrue(viewModel.isDeploymentMode(), "reset must not clear the mode");
        assertFalse(viewModel.hintsTextProperty().get().contains("Ingest→Generate"),
                "reset must not restore the ingestion hints in deployment mode");
    }

    /**
     * HomeController renders the deployment hint by matching the WHOLE string and then building a
     * sentence out of its own literals -- so the two must say the same thing, and the coupling is
     * invisible to the compiler.  If the hint here is edited without updating the controller, the
     * match fails and the guidance falls through to the controller's plain-text branch: it still
     * renders, but the Explore link is silently reduced to unclickable text.
     *
     * <p>Comparing the view model's output against the view model's own constant would be vacuous,
     * since the two move together.  This reads the controller's literals out of its source instead,
     * which is the only place the drift is observable without a JavaFX toolkit.
     */
    @Test
    @DisplayName("HomeController's rendered deployment sentence matches the hint verbatim")
    public void testControllerRendersTheDeploymentHintVerbatim() throws Exception {
        final String controllerSource = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/ospreydcs/dp/gui/HomeController.java"));

        final int branch = controllerSource.indexOf("HomeViewModel.DEPLOYMENT_HINT.equals(hintsText)");
        assertTrue(branch > 0,
                "HomeController must still match the deployment hint by its whole string -- a "
                        + "substring match would let another hint be rendered as this sentence");

        // The Text / Hyperlink literals the branch assembles, in order.
        final String branchBody = controllerSource.substring(
                branch, controllerSource.indexOf("// Check if this is the initial state", branch));
        final java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("new (?:Text|Hyperlink)\\(\"((?:[^\"\\\\]|\\\\.)*)\"\\)")
                        .matcher(branchBody);

        final StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            rendered.append(matcher.group(1).replace("\\u2192", "\u2192"));
        }

        assertTrue(rendered.length() > 0, "failed to locate the rendered literals in HomeController");
        assertEquals(HomeViewModel.DEPLOYMENT_HINT, rendered.toString(),
                "the sentence HomeController renders must match the hint HomeViewModel publishes");
    }
}
