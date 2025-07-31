package com.ospreydcs.dp.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;



public class MainController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    // FXML injected components
    @FXML private MenuBar menuBar;
    @FXML private StackPane contentPane;
    @FXML private Label statusLabel;
    @FXML private Label connectionStatusLabel;

    // Menu items
    @FXML private MenuItem connectionMenuItem;
    @FXML private MenuItem preferencesMenuItem;
    @FXML private MenuItem exitMenuItem;
    @FXML private MenuItem generateMenuItem;
    @FXML private MenuItem fixedMenuItem;
    @FXML private MenuItem importMenuItem;
    @FXML private MenuItem subscribeMenuItem;
    @FXML private MenuItem dataMenuItem;
    @FXML private MenuItem pvMetadataMenuItem;
    @FXML private MenuItem providerMetadataMenuItem;
    @FXML private MenuItem annotationsMenuItem;
    @FXML private MenuItem annotateMenuItem;
    @FXML private MenuItem exportMenuItem;
    @FXML private MenuItem uploadMenuItem;
    @FXML private MenuItem consoleMenuItem;

    // Dependencies
    private MainViewModel viewModel;
    private DpApplication dpApplication;
    private Stage primaryStage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("MainController initializing...");
        
        // Create the view model
        viewModel = new MainViewModel();
        
        // Bind UI components to view model properties
        bindUIToViewModel();
        
        logger.debug("MainController initialized successfully");
    }

    private void bindUIToViewModel() {
        // Bind status labels
        statusLabel.textProperty().bind(viewModel.statusTextProperty());
        connectionStatusLabel.textProperty().bind(viewModel.connectionStatusTextProperty());
        
        // Bind menu item disabled states (note: disabled is opposite of enabled)
        connectionMenuItem.disableProperty().bind(viewModel.connectionEnabledProperty().not());
        preferencesMenuItem.disableProperty().bind(viewModel.preferencesEnabledProperty().not());
        generateMenuItem.disableProperty().bind(viewModel.generateEnabledProperty().not());
        fixedMenuItem.disableProperty().bind(viewModel.fixedEnabledProperty().not());
        importMenuItem.disableProperty().bind(viewModel.importEnabledProperty().not());
        subscribeMenuItem.disableProperty().bind(viewModel.subscribeEnabledProperty().not());
        dataMenuItem.disableProperty().bind(viewModel.dataEnabledProperty().not());
        pvMetadataMenuItem.disableProperty().bind(viewModel.pvMetadataEnabledProperty().not());
        providerMetadataMenuItem.disableProperty().bind(viewModel.providerMetadataEnabledProperty().not());
        annotationsMenuItem.disableProperty().bind(viewModel.annotationsEnabledProperty().not());
        annotateMenuItem.disableProperty().bind(viewModel.annotateEnabledProperty().not());
        exportMenuItem.disableProperty().bind(viewModel.exportEnabledProperty().not());
        uploadMenuItem.disableProperty().bind(viewModel.uploadEnabledProperty().not());
        consoleMenuItem.disableProperty().bind(viewModel.consoleEnabledProperty().not());
    }

    // Dependency injection methods
    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        if (viewModel != null) {
            viewModel.setDpApplication(dpApplication);
        }
        logger.debug("DpApplication injected into MainController");
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        logger.debug("Primary stage injected into MainController");
    }

    // Menu action handlers - File menu
    @FXML
    private void onConnection() {
        viewModel.handleConnection();
    }

    @FXML
    private void onPreferences() {
        viewModel.handlePreferences();
    }

    @FXML
    private void onExit() {
        logger.info("Exit requested by user");
        Platform.exit();
    }

    // Menu action handlers - Ingest menu
    @FXML
    private void onGenerate() {
        viewModel.handleGenerate();
        switchToView("/fxml/data-generation.fxml");
    }

    @FXML
    private void onFixed() {
        viewModel.handleFixed();
    }

    @FXML
    private void onImport() {
        viewModel.handleImport();
    }

    @FXML
    private void onSubscribe() {
        viewModel.handleSubscribe();
    }

    // Menu action handlers - Query menu
    @FXML
    private void onData() {
        viewModel.handleData();
    }

    @FXML
    private void onPvMetadata() {
        viewModel.handlePvMetadata();
    }

    @FXML
    private void onProviderMetadata() {
        viewModel.handleProviderMetadata();
    }

    @FXML
    private void onAnnotations() {
        viewModel.handleAnnotations();
    }

    // Menu action handlers - Tools menu
    @FXML
    private void onAnnotate() {
        viewModel.handleAnnotate();
    }

    @FXML
    private void onExport() {
        viewModel.handleExport();
    }

    @FXML
    private void onUpload() {
        viewModel.handleUpload();
    }

    @FXML
    private void onConsole() {
        viewModel.handleConsole();
    }

    // Utility methods for view management
    public void switchToView(String fxmlPath) {
        try {
            logger.debug("Loading view: {}", fxmlPath);
            viewModel.updateStatus("Loading view...");
            
            // Load the new FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            contentPane.getChildren().clear();
            contentPane.getChildren().add(loader.load());
            
            // Inject dependencies into the new controller if it needs them
            Object controller = loader.getController();
            if (controller instanceof DataGenerationController) {
                DataGenerationController dgController = (DataGenerationController) controller;
                dgController.setDpApplication(dpApplication);
                dgController.setPrimaryStage(primaryStage);
                dgController.setMainController(this);
            }
            
            viewModel.updateStatus("View loaded successfully");
            logger.debug("Successfully loaded view: {}", fxmlPath);
            
        } catch (Exception e) {
            logger.error("Failed to load view: {}", fxmlPath, e);
            viewModel.updateStatus("Failed to load view: " + e.getMessage());
        }
    }
    
    public void switchToMainView() {
        try {
            logger.debug("Returning to main view");
            viewModel.updateStatus("Loading main view...");
            
            // Load the welcome content back into the content pane
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/welcome-content.fxml"));
            contentPane.getChildren().clear();
            contentPane.getChildren().add(loader.load());
            
            viewModel.updateStatus("Ready");
            logger.debug("Successfully returned to main view");
            
        } catch (Exception e) {
            logger.error("Failed to return to main view", e);
            viewModel.updateStatus("Error returning to main view");
        }
    }
}