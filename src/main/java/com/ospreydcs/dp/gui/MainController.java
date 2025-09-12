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
    @FXML private MenuItem importMenuItem;
    @FXML private MenuItem dataMenuItem;
    @FXML private MenuItem pvMetadataMenuItem;
    @FXML private MenuItem providerMetadataMenuItem;
    @FXML private MenuItem datasetsMenuItem;
    @FXML private MenuItem annotationsMenuItem;
    @FXML private MenuItem dataEventsMenuItem;

    // Dependencies
    private MainViewModel viewModel;
    private DpApplication dpApplication;
    private Stage primaryStage;
    private HomeController homeController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("MainController initializing...");
        
        // Create the view model
        viewModel = new MainViewModel();
        
        // Bind UI components to view model properties
        bindUIToViewModel();
        
        // Note: Don't load home view here - wait until dpApplication is injected
        
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
        // importMenuItem is now always enabled (no binding needed)
        dataMenuItem.disableProperty().bind(viewModel.dataEnabledProperty().not());
        pvMetadataMenuItem.disableProperty().bind(viewModel.pvMetadataEnabledProperty().not());
        providerMetadataMenuItem.disableProperty().bind(viewModel.providerMetadataEnabledProperty().not());
        datasetsMenuItem.disableProperty().bind(viewModel.datasetsEnabledProperty().not());
        annotationsMenuItem.disableProperty().bind(viewModel.annotationsEnabledProperty().not());
        dataEventsMenuItem.disableProperty().bind(viewModel.dataEventsEnabledProperty().not());
    }

    // Dependency injection methods
    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        if (viewModel != null) {
            viewModel.setDpApplication(dpApplication);
        }
        
        // Now that dpApplication is available, load the home view
        loadHomeView();
        
        logger.debug("DpApplication injected into MainController");
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        logger.debug("Primary stage injected into MainController");
    }
    
    public MainViewModel getViewModel() {
        return viewModel;
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
    private void onImport() {
        viewModel.handleImport();
        switchToView("/fxml/data-import.fxml");
    }

    // Menu action handlers - Explore menu
    @FXML
    private void onData() {
        viewModel.handleData();
        switchToView("/fxml/data-explore.fxml");
    }

    @FXML
    private void onPvMetadata() {
        viewModel.handlePvMetadata();
        switchToView("/fxml/pv-explore.fxml");
    }

    @FXML
    private void onProviderMetadata() {
        viewModel.handleProviderMetadata();
        switchToView("/fxml/provider-explore.fxml");
    }

    @FXML
    private void onDatasets() {
        viewModel.handleDatasets();
        switchToView("/fxml/dataset-explore.fxml");
    }

    @FXML
    private void onAnnotations() {
        viewModel.handleAnnotations();
        switchToView("/fxml/annotation-explore.fxml");
    }
    
    @FXML
    private void onDataEvents() {
        viewModel.handleDataEvents();
        switchToView("/fxml/data-event-explore.fxml");
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
            } else if (controller instanceof DataExploreController) {
                DataExploreController dqController = (DataExploreController) controller;
                dqController.setDpApplication(dpApplication);
                dqController.setPrimaryStage(primaryStage);
                dqController.setMainController(this);
            } else if (controller instanceof DataImportController) {
                DataImportController diController = (DataImportController) controller;
                diController.setDpApplication(dpApplication);
                diController.setPrimaryStage(primaryStage);
                diController.setMainController(this);
            } else if (controller instanceof PvExploreController) {
                PvExploreController peController = (PvExploreController) controller;
                peController.setDpApplication(dpApplication);
                peController.setPrimaryStage(primaryStage);
                peController.setMainController(this);
            } else if (controller instanceof ProviderExploreController) {
                ProviderExploreController prController = (ProviderExploreController) controller;
                prController.setDpApplication(dpApplication);
                prController.setPrimaryStage(primaryStage);
                prController.setMainController(this);
                prController.initializeView();
            } else if (controller instanceof DatasetExploreController) {
                DatasetExploreController dsController = (DatasetExploreController) controller;
                dsController.setDpApplication(dpApplication);
                dsController.setPrimaryStage(primaryStage);
                dsController.setMainController(this);
            } else if (controller instanceof AnnotationExploreController) {
                AnnotationExploreController aeController = (AnnotationExploreController) controller;
                aeController.setDpApplication(dpApplication);
                aeController.setPrimaryStage(primaryStage);
                aeController.setMainController(this);
            } else if (controller instanceof DataEventExploreController) {
                DataEventExploreController deeController = (DataEventExploreController) controller;
                deeController.setDpApplication(dpApplication);
                deeController.setPrimaryStage(primaryStage);
                deeController.setMainController(this);
            }
            
            viewModel.updateStatus("View loaded successfully");
            logger.debug("Successfully loaded view: {}", fxmlPath);
            
        } catch (Exception e) {
            logger.error("Failed to load view: {}", fxmlPath, e);
            viewModel.updateStatus("Failed to load view: " + e.getMessage());
        }
    }
    
    public void navigateToProviderExploreWithSearch(String providerId) {
        try {
            logger.debug("Loading provider-explore view with automatic search for provider ID: {}", providerId);
            viewModel.updateStatus("Loading provider explorer...");
            
            // Load the provider-explore FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/provider-explore.fxml"));
            contentPane.getChildren().clear();
            contentPane.getChildren().add(loader.load());
            
            // Get the controller and inject dependencies
            ProviderExploreController prController = (ProviderExploreController) loader.getController();
            prController.setDpApplication(dpApplication);
            prController.setPrimaryStage(primaryStage);
            prController.setMainController(this);
            prController.initializeView();
            
            // Execute search with the provided ID
            prController.executeProviderSearch(providerId);
            
            viewModel.updateStatus("Provider search executed successfully");
            logger.debug("Successfully loaded provider-explore view with search for provider ID: {}", providerId);
            
        } catch (Exception e) {
            logger.error("Failed to load provider-explore view with search", e);
            viewModel.updateStatus("Failed to load provider explorer: " + e.getMessage());
        }
    }
    
    public void navigateToDataExploreWithDataset(String datasetId) {
        try {
            logger.debug("Loading data-explore view with dataset ID: {}", datasetId);
            viewModel.updateStatus("Loading data explorer...");
            
            // Load the data-explore FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/data-explore.fxml"));
            contentPane.getChildren().clear();
            contentPane.getChildren().add(loader.load());
            
            // Get the controller and inject dependencies
            DataExploreController deController = (DataExploreController) loader.getController();
            deController.setDpApplication(dpApplication);
            deController.setPrimaryStage(primaryStage);
            deController.setMainController(this);
            
            // Load dataset into Dataset Builder tab
            deController.loadDatasetIntoBuilder(datasetId);
            
            viewModel.updateStatus("Data explorer loaded with dataset");
            logger.debug("Successfully loaded data-explore view and initiated dataset loading for ID: {}", datasetId);
            
        } catch (Exception e) {
            logger.error("Failed to load data-explore view with dataset", e);
            viewModel.updateStatus("Failed to load data explorer: " + e.getMessage());
        }
    }
    
    public void navigateToDataExploreWithAnnotation(String annotationId) {
        try {
            logger.debug("Loading data-explore view with annotation ID: {}", annotationId);
            viewModel.updateStatus("Loading data explorer...");
            
            // Load the data-explore FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/data-explore.fxml"));
            contentPane.getChildren().clear();
            contentPane.getChildren().add(loader.load());
            
            // Get the controller and inject dependencies
            DataExploreController deController = (DataExploreController) loader.getController();
            deController.setDpApplication(dpApplication);
            deController.setPrimaryStage(primaryStage);
            deController.setMainController(this);
            
            // Load annotation into Annotation Builder tab
            deController.loadAnnotationIntoBuilder(annotationId);
            
            viewModel.updateStatus("Data explorer loaded with annotation");
            logger.debug("Successfully loaded data-explore view and initiated annotation loading for ID: {}", annotationId);
            
        } catch (Exception e) {
            logger.error("Failed to load data-explore view with annotation", e);
            viewModel.updateStatus("Failed to load data explorer: " + e.getMessage());
        }
    }
    
    private void loadHomeView() {
        try {
            logger.debug("Loading home view");
            
            // Load the home FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/home.fxml"));
            contentPane.getChildren().clear();
            contentPane.getChildren().add(loader.load());
            
            // Get the home controller and inject dependencies
            homeController = loader.getController();
            homeController.setDpApplication(dpApplication);
            homeController.setMainController(this);
            
            // Refresh home view with current application state
            refreshHomeView();
            
            logger.debug("Successfully loaded home view");
            
        } catch (Exception e) {
            logger.error("Failed to load home view", e);
            viewModel.updateStatus("Error loading home view");
        }
    }
    
    public void switchToMainView() {
        try {
            logger.debug("Returning to home view");
            viewModel.updateStatus("Loading home view...");
            
            loadHomeView();
            
            viewModel.updateStatus("Ready");
            logger.debug("Successfully returned to home view");
            
        } catch (Exception e) {
            logger.error("Failed to return to home view", e);
            viewModel.updateStatus("Error returning to home view");
        }
    }
    
    public void switchToDataExploreView() {
        switchToView("/fxml/data-explore.fxml");
    }
    
    private void refreshHomeView() {
        if (homeController != null && dpApplication != null) {
            // Update home view with current application state
            HomeViewModel homeViewModel = homeController.getViewModel();
            homeViewModel.updateDataIngestedState(dpApplication.hasIngestedData());
            homeViewModel.updateQueriesPerformedState(dpApplication.hasPerformedQueries());
            if (dpApplication.getLastOperationResult() != null) {
                homeViewModel.updateLastOperationResult(dpApplication.getLastOperationResult());
            }
            logger.debug("Home view refreshed with current application state");
        }
    }
    
    public void onDataGenerationSuccess(String message) {
        if (homeController != null) {
            homeController.onDataGenerationSuccess(message);
            logger.info("Home view notified of data generation success: {}", message);
        }
        
        // Refresh menu states since data has been ingested
        if (viewModel != null) {
            viewModel.refreshMenuStates();
            logger.debug("Menu states refreshed after data generation success");
        }
    }
    
    public void onQuerySuccess(String message) {
        if (homeController != null) {
            homeController.onQuerySuccess(message);
            logger.info("Home view notified of query success: {}", message);
        }
        
        // Refresh menu states since queries have been performed
        if (viewModel != null) {
            viewModel.refreshMenuStates();
            logger.debug("Menu states refreshed after query success");
        }
    }
}