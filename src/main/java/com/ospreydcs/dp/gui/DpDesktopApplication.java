package com.ospreydcs.dp.gui;

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

    @Override
    public void init() throws Exception {
        super.init();
        
        // Initialize the backend application
        dpApplication = new DpApplication();
        if (!dpApplication.init()) {
            logger.error("Failed to initialize DpApplication");
            throw new RuntimeException("Failed to initialize backend services");
        }
        
        logger.info("DpApplication initialized successfully");
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
        
        // Configure the stage
        stage.setTitle(APPLICATION_TITLE);
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

    public static void main(String[] args) {
        launch(args);
    }
}