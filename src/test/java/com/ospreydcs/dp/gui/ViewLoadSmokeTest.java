package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Loads every FXML file that declares an fx:controller under a running JavaFX toolkit.
 *
 * This catches the failure classes this app has actually hit at the view level, none of which
 * the toolkit-free unit tests can see: FXML syntax errors surfacing as PropertyNotFoundException
 * at load time, fx:id injection type mismatches between FXML and controller fields, and
 * controller initialize() blowing up.  Before these tests, only manually clicking through every
 * view caught such regressions.
 *
 * The FXML files are enumerated from the classpath rather than hand-listed, so a newly added
 * view (or dialog) is covered the moment its file lands in src/main/resources/fxml — no test
 * update required, and no silent coverage gap when the "Adding New Views" workflow is followed.
 * Component FXMLs without fx:controller are excluded on purpose: those are fx:root layouts
 * whose components load them with setController() in their constructors, and construction is
 * exercised by the component instance/construction smoke tests.
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

    /**
     * Every FXML resource under /fxml (recursively) that declares an fx:controller, as a
     * classpath path.  During tests the resources live in target/classes as plain files, so
     * the classpath directory can be walked directly.  JUnit fails the parameterized test
     * outright if this returns no arguments, so an enumeration bug cannot silently skip
     * all coverage.
     */
    static Stream<String> controllerBearingFxmlPaths() throws Exception {
        final URL fxmlDirUrl = ViewLoadSmokeTest.class.getResource("/fxml");
        assertNotNull(fxmlDirUrl, "fxml resource directory not found on classpath");
        final Path fxmlDir = Path.of(fxmlDirUrl.toURI());

        final List<Path> fxmlFiles;
        try (Stream<Path> walk = Files.walk(fxmlDir)) {
            fxmlFiles = walk.filter(file -> file.toString().endsWith(".fxml")).sorted().toList();
        }

        final List<String> classpathPaths = new ArrayList<>();
        for (Path fxmlFile : fxmlFiles) {
            if (Files.readString(fxmlFile).contains("fx:controller")) {
                classpathPaths.add(
                        "/fxml/" + fxmlDir.relativize(fxmlFile).toString().replace('\\', '/'));
            }
        }
        return classpathPaths.stream();
    }

    @ParameterizedTest
    @MethodSource("controllerBearingFxmlPaths")
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
