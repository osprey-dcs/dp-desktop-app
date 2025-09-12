package com.ospreydcs.dp.gui;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;

public class HomeController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    // FXML injected components
    @FXML private HBox hintsContainer;
    @FXML private Label hintsLabel;
    @FXML private Label statusLabel;
    @FXML private Label detailsLabel;

    // Dependencies
    private HomeViewModel viewModel;
    private DpApplication dpApplication;
    private MainController mainController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("HomeController initializing...");
        
        // Create the view model
        viewModel = new HomeViewModel();
        
        // Bind UI components to view model properties
        bindUIToViewModel();
        
        logger.debug("HomeController initialized successfully");
    }

    private void bindUIToViewModel() {
        // Bind labels to view model properties
        statusLabel.textProperty().bind(viewModel.statusTextProperty());
        detailsLabel.textProperty().bind(viewModel.detailsTextProperty());
        
        // Listen to hints text changes to update hints container
        viewModel.hintsTextProperty().addListener((obs, oldVal, newVal) -> updateHintsContent());
        
        // Initialize hints content
        updateHintsContent();
        
        logger.debug("UI bindings established in HomeController");
    }

    private void updateHintsContent() {
        hintsContainer.getChildren().clear();
        
        String hintsText = viewModel.hintsTextProperty().get();
        if (hintsText == null) return;
        
        // Check if this is the initial state that needs hyperlinks
        if (hintsText.contains("Ingest→Generate") || hintsText.contains("Ingest->Generate") || 
            hintsText.contains("Ingest→Import") || hintsText.contains("Ingest->Import")) {
            
            // Create the text with hyperlinks for initial state
            Text startText = new Text("Start by using the ");
            startText.getStyleClass().add("text-info");
            
            Hyperlink generateLink = new Hyperlink("Ingest→Generate");
            generateLink.getStyleClass().addAll("text-info");
            generateLink.setStyle("-fx-underline: true; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
            generateLink.setOnAction(e -> navigateToDataGeneration());
            
            Text middleText = new Text(" or ");
            middleText.getStyleClass().add("text-info");
            
            Hyperlink importLink = new Hyperlink("Ingest→Import");
            importLink.getStyleClass().addAll("text-info");
            importLink.setStyle("-fx-underline: true; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
            importLink.setOnAction(e -> navigateToDataImport());
            
            Text endText = new Text(" menus to generate or import some PV data and ingest it to the MLDP archive.");
            endText.getStyleClass().add("text-info");
            
            hintsContainer.getChildren().addAll(startText, generateLink, middleText, importLink, endText);
        } else if (hintsText.contains("Explore→Data") || hintsText.contains("Explore→PVs") || hintsText.contains("Explore→Annotations")) {
            
            // Create the text with hyperlinks for post-ingestion state
            Text startText = new Text("Data ingested successfully! Use ");
            startText.getStyleClass().add("text-info");
            
            Hyperlink dataLink = new Hyperlink("Explore→Data");
            dataLink.getStyleClass().addAll("text-info");
            dataLink.setStyle("-fx-underline: true; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
            dataLink.setOnAction(e -> navigateToDataExplore());
            
            Text middleText1 = new Text(" to query time-series data, ");
            middleText1.getStyleClass().add("text-info");
            
            Hyperlink pvsLink = new Hyperlink("Explore→PVs");
            pvsLink.getStyleClass().addAll("text-info");
            pvsLink.setStyle("-fx-underline: true; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
            pvsLink.setOnAction(e -> navigateToPvExplore());
            
            Text middleText2 = new Text(" to browse metadata, or ");
            middleText2.getStyleClass().add("text-info");
            
            Hyperlink annotationsLink = new Hyperlink("Explore→Annotations");
            annotationsLink.getStyleClass().addAll("text-info");
            annotationsLink.setStyle("-fx-underline: true; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
            annotationsLink.setOnAction(e -> navigateToAnnotationExplore());
            
            Text endText = new Text(" to search annotations.");
            endText.getStyleClass().add("text-info");
            
            hintsContainer.getChildren().addAll(startText, dataLink, middleText1, pvsLink, middleText2, annotationsLink, endText);
        } else {
            // For other states, use regular text
            Text regularText = new Text(hintsText);
            regularText.getStyleClass().add("text-info");
            hintsContainer.getChildren().add(regularText);
        }
    }
    
    private void navigateToDataGeneration() {
        if (mainController != null) {
            mainController.switchToView("/fxml/data-generation.fxml");
            logger.info("Navigated to data generation view from home hints");
        }
    }
    
    private void navigateToDataImport() {
        if (mainController != null) {
            mainController.switchToView("/fxml/data-import.fxml");
            logger.info("Navigated to data import view from home hints");
        }
    }
    
    private void navigateToDataExplore() {
        if (mainController != null) {
            mainController.switchToView("/fxml/data-explore.fxml");
            logger.info("Navigated to data explore view from home hints");
        }
    }
    
    private void navigateToPvExplore() {
        if (mainController != null) {
            mainController.switchToView("/fxml/pv-explore.fxml");
            logger.info("Navigated to PV explore view from home hints");
        }
    }
    
    private void navigateToAnnotationExplore() {
        if (mainController != null) {
            mainController.switchToView("/fxml/annotation-explore.fxml");
            logger.info("Navigated to annotation explore view from home hints");
        }
    }

    // Dependency injection methods
    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        if (viewModel != null) {
            viewModel.setDpApplication(dpApplication);
        }
        logger.debug("DpApplication injected into HomeController");
    }
    
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        logger.debug("MainController injected into HomeController");
    }

    // Getter for view model (to allow other controllers to access it)
    public HomeViewModel getViewModel() {
        return viewModel;
    }

    // Methods for updating home view state from other parts of the application
    public void onDataGenerationSuccess(String message) {
        if (viewModel != null) {
            viewModel.onSuccessfulDataGeneration(message);
            logger.info("Home view updated with data generation success: {}", message);
        }
    }

    public void onQuerySuccess(String message) {
        if (viewModel != null) {
            viewModel.onSuccessfulQuery(message);
            logger.info("Home view updated with query success: {}", message);
        }
    }

    public void refreshApplicationState() {
        if (viewModel != null && dpApplication != null) {
            // Check current application state and update view model accordingly
            // This could query the DpApplication for current state information
            logger.debug("Refreshing home view application state");
        }
    }

    public void resetApplicationState() {
        if (viewModel != null) {
            viewModel.resetApplicationState();
            logger.info("Home view application state reset");
        }
    }
}