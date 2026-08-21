package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Loads every FXML view with its declared fx:controller under a running JavaFX toolkit.
 *
 * This catches the failure classes this app has actually hit at the view level, none of which
 * the toolkit-free unit tests can see: FXML syntax errors surfacing as PropertyNotFoundException
 * at load time, fx:id injection type mismatches between FXML and controller fields, and
 * controller initialize() blowing up.  Before these tests, only manually clicking through every
 * view caught such regressions.
 *
 * Loading with the real controller works without any backend because of the app's dependency
 * injection pattern: initialize() runs during FXMLLoader.load() and is dependency-free by
 * design — DpApplication, Stage, and MainController are injected afterward via setters, then
 * initializeView().  A view whose initialize() starts requiring an injected dependency will
 * fail here, which is deliberate: that would also break MainController's navigation flow.
 *
 * Post-injection behavior (button handlers calling DpApplication, background tasks, navigation)
 * is out of scope per issue #29 — that would need an injection seam for DpApplication and
 * robot-driven interaction testing.
 */
public class ViewLoadSmokeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/fxml/main-window.fxml",
            "/fxml/home.fxml",
            "/fxml/data-generation.fxml",
            "/fxml/data-import.fxml",
            "/fxml/data-explore.fxml",
            "/fxml/pv-explore.fxml",
            "/fxml/provider-explore.fxml",
            "/fxml/dataset-explore.fxml",
            "/fxml/annotation-explore.fxml",
            "/fxml/data-event-explore.fxml",
            "/fxml/pv-metadata.fxml",
    })
    public void viewLoadsAndControllerIsInjected(String fxmlPath) throws Exception {
        final URL fxmlUrl = getClass().getResource(fxmlPath);
        assertNotNull(fxmlUrl, "FXML resource not found on classpath: " + fxmlPath);

        // Load on the FX thread, as MainController does in production.
        final FXMLLoader loader = FxToolkitSupport.callOnFxThread(() -> {
            final FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl);
            final Parent root = fxmlLoader.load();
            assertNotNull(root, "FXML load produced no root for " + fxmlPath);
            return fxmlLoader;
        });

        assertNotNull(loader.getController(),
                "no controller was instantiated for " + fxmlPath
                        + " (missing or wrong fx:controller declaration)");
    }
}
