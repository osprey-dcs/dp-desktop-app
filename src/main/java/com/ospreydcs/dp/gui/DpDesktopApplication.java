package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.config.AppConfiguration;
import com.ospreydcs.dp.gui.config.AppMode;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.kordamp.bootstrapfx.BootstrapFX;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class DpDesktopApplication extends Application {

    private static final Logger logger = LogManager.getLogger();
    private static final String APPLICATION_TITLE = "Machine Learning Data Platform (MLDP)";
    private static final double INITIAL_WIDTH = 1200;
    private static final double INITIAL_HEIGHT = 800;

    private DpApplication dpApplication;
    private Stage primaryStage;

    /**
     * Command-line form of {@code -Ddp.DpDesktopApp.mode=...}, e.g. {@code --mode=deployment}.
     *
     * <p>JavaFX parses {@code --name=value} arguments into named parameters, so the launcher passes
     * {@code args} straight through to {@code Application.launch()} and they arrive here.
     */
    static final String MODE_PARAMETER_NAME = "mode";

    /**
     * The per-key override {@code ConfigurationManager} reads: its {@code dp.} prefix plus
     * {@link AppConfiguration#CFG_KEY_MODE}.  Built from that constant rather than spelled out, so
     * renaming the config key cannot leave this silently pointing at a key nothing reads.
     */
    static final String DP_MODE_PROPERTY = "dp." + AppConfiguration.CFG_KEY_MODE;

    @Override
    public void init() throws Exception {
        super.init();

        applyModeParameter(getParameters() == null ? null : getParameters().getNamed().get(MODE_PARAMETER_NAME));

        // Initialize the backend application
        dpApplication = new DpApplication();
        if (!dpApplication.init()) {
            logger.error("Failed to initialize DpApplication");
            throw new RuntimeException("Failed to initialize backend services");
        }
        
        logger.info("DpApplication initialized successfully");
    }

    /**
     * Translates {@code --mode=<value>} into the system property {@code AppConfiguration} already
     * reads, so there is exactly ONE mode-resolution path rather than two that can disagree.
     *
     * <p>Deliberately NOT parsed here: the value is handed to {@code ConfigurationManager} as-is and
     * resolved by {@link AppMode#fromConfigValue(String)} like any other source, which is what keeps
     * the typo rule ("deploymnt" resolves to DEMO, never DEPLOYMENT) applying to the command line
     * too.  A second parser here would be free to drift from that rule, and the direction it would
     * drift in is a misspelling silently becoming a connection attempt against production.
     *
     * <p><b>An explicit {@code -D} wins.</b>  {@code -Ddp.DpDesktopApp.mode} is the documented
     * mechanism and the one a deployment install scripts; a launcher script that always appended
     * {@code --mode=demo} would otherwise override what an operator deliberately set on the command
     * line, and nothing would say so.  The argument fills the property in only when it is absent.
     *
     * <p>Returns the value applied, or null when nothing was applied; package-private for tests.
     */
    static String applyModeParameter(String modeParameter) {

        if (modeParameter == null || modeParameter.isBlank()) {
            return null;
        }

        final String existing = System.getProperty(DP_MODE_PROPERTY);
        if (existing != null && !existing.isBlank()) {
            logger.info(
                    "--mode={} ignored: {} is already set to '{}', and an explicit system property "
                            + "wins over the argument",
                    modeParameter, DP_MODE_PROPERTY, existing);
            return null;
        }

        // Note this sets the property rather than returning a mode: ConfigurationManager is a
        // process-global singleton read later by AppConfiguration, so the property must be in place
        // BEFORE anything resolves configuration -- which is why this runs at the top of init().
        System.setProperty(DP_MODE_PROPERTY, modeParameter);
        logger.info("applying --mode={} as {}", modeParameter, DP_MODE_PROPERTY);
        return modeParameter;
    }

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        
        // Load the main FXML layout
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/main-window.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), INITIAL_WIDTH, INITIAL_HEIGHT);
        
        // Apply BootstrapFX styling
        scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        
        // Apply custom application styling
        scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
        
        // Get the controller and inject the DpApplication
        MainController controller = fxmlLoader.getController();
        controller.setDpApplication(dpApplication);
        controller.setPrimaryStage(primaryStage);
        
        // Configure the stage.  The mode goes in the title as well as the status bar because the
        // title is what identifies a window among several -- running a demo beside a deployment is
        // exactly when confusing the two is costly, and it is the case the window list has to
        // disambiguate.
        stage.setTitle(APPLICATION_TITLE + " -- " + dpApplication.getConfiguration().describe());
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        
        // Handle application close
        // TODO: stop() also gets called, so I'm not sure we need to do this, or at least be prepared for cleanup() to be called twice.
        stage.setOnCloseRequest(event -> {
            logger.info("Application closing...");
            cleanup();
        });
        
        stage.show();
        logger.info("JavaFX application started successfully");
    }

    @Override
    public void stop() throws Exception {
        cleanup();
        super.stop();
    }

    private void cleanup() {
        if (dpApplication != null) {
            dpApplication.fini();
            dpApplication = null;
            logger.info("DpApplication cleanup completed");
        }
    }
}