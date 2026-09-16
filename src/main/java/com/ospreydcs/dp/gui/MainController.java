package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.service.inprocess.MongoInterface;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.Optional;
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
    // Explore > PV Statistics: the read-only queryPvStats() view (pv-explore).  Named pvStats,
    // not pvMetadata: Explore > PV Metadata is a different view, and Metadata > PV is the editor.
    @FXML private MenuItem pvStatsMenuItem;
    @FXML private MenuItem pvMetadataExploreMenuItem;
    @FXML private MenuItem providerMetadataMenuItem;
    @FXML private MenuItem datasetsMenuItem;
    @FXML private MenuItem annotationsMenuItem;
    @FXML private MenuItem configurationsExploreMenuItem;
    @FXML private MenuItem sampleStatusesMenuItem;
    @FXML private MenuItem dataEventsMenuItem;
    // note: distinct from pvMetadataExploreMenuItem above, which opens the read-only
    // Explore > PV Metadata browser; this one opens the Metadata > PV editor
    @FXML private MenuItem pvMetadataCreateMenuItem;
    @FXML private MenuItem machineConfigCreateMenuItem;
    @FXML private MenuItem deleteDemoDataMenuItem;

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
        importMenuItem.disableProperty().bind(viewModel.importEnabledProperty().not());
        dataMenuItem.disableProperty().bind(viewModel.dataEnabledProperty().not());
        pvStatsMenuItem.disableProperty().bind(viewModel.pvStatsEnabledProperty().not());
        pvMetadataExploreMenuItem.disableProperty().bind(viewModel.pvMetadataExploreEnabledProperty().not());
        providerMetadataMenuItem.disableProperty().bind(viewModel.providerMetadataEnabledProperty().not());
        datasetsMenuItem.disableProperty().bind(viewModel.datasetsEnabledProperty().not());
        annotationsMenuItem.disableProperty().bind(viewModel.annotationsEnabledProperty().not());
        configurationsExploreMenuItem.disableProperty()
                .bind(viewModel.configurationsExploreEnabledProperty().not());
        sampleStatusesMenuItem.disableProperty().bind(viewModel.sampleStatusesEnabledProperty().not());
        dataEventsMenuItem.disableProperty().bind(viewModel.dataEventsEnabledProperty().not());
        // These three were unbound and default-enabled before #4, on the reasoning that creating
        // metadata and importing real data do not depend on this session having ingested anything.
        // That is still true -- but it was never the only question, and deployment mode is where
        // the other one appears: all three write to the archive.  Unbound items are invisible to
        // the mode rule in MainViewModel, so the FXML defaults would have silently survived it.
        pvMetadataCreateMenuItem.disableProperty().bind(viewModel.pvMetadataCreateEnabledProperty().not());
        machineConfigCreateMenuItem.disableProperty().bind(viewModel.machineConfigCreateEnabledProperty().not());
        deleteDemoDataMenuItem.disableProperty().bind(viewModel.deleteDemoDataEnabledProperty().not());
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
    private void onPvStats() {
        viewModel.handlePvStats();
        switchToView("/fxml/pv-explore.fxml");
    }

    /**
     * Opens the PV metadata editor pre-loaded with an existing record, for edit-in-place from the
     * explore view.
     *
     * <p>Takes the resolved record rather than a name, deliberately.  getPvMetadata() resolves
     * aliases, so re-fetching here by whatever string the caller had would risk loading a different
     * record than the one the user clicked -- and savePvMetadata() is a full-replace upsert keyed on
     * pvName, so that divergence would be written, not just displayed.
     */
    public void navigateToPvMetadataEditor(com.ospreydcs.dp.grpc.v1.common.PvMetadata record) {
        try {
            viewModel.updateStatus("Loading PV metadata editor...");

            final FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pv-metadata.fxml"));
            contentPane.getChildren().clear();
            contentPane.getChildren().add(loader.load());

            final PvMetadataController controller = loader.getController();
            controller.setDpApplication(dpApplication);
            controller.setPrimaryStage(primaryStage);
            controller.setMainController(this);
            controller.loadForEditing(record);

            viewModel.updateStatus("Editing PV metadata for " + record.getPvName());

        } catch (Exception e) {
            logger.error("Failed to open the PV metadata editor", e);
            viewModel.updateStatus("Failed to open the PV metadata editor: " + e.getMessage());
        }
    }

    /**
     * Opens the machine configuration editor loaded with an existing record.
     *
     * <p>Takes the resolved record rather than a name, for the same reason as
     * navigateToPvMetadataEditor(): saveConfiguration() is a full-replace upsert keyed on
     * configurationName, so loading anything other than the record the user actually clicked would
     * be written rather than merely displayed.
     */
    public void navigateToConfigurationEditor(com.ospreydcs.dp.grpc.v1.common.Configuration record) {
        try {
            viewModel.updateStatus("Loading machine configuration editor...");

            final FXMLLoader loader =
                    new FXMLLoader(getClass().getResource("/fxml/machine-configuration.fxml"));
            contentPane.getChildren().clear();
            contentPane.getChildren().add(loader.load());

            final MachineConfigurationController controller = loader.getController();
            controller.setDpApplication(dpApplication);
            controller.setPrimaryStage(primaryStage);
            controller.setMainController(this);
            controller.loadForEditing(record);

            viewModel.updateStatus("Editing configuration " + record.getConfigurationName());

        } catch (Exception e) {
            logger.error("Failed to open the machine configuration editor", e);
            viewModel.updateStatus("Failed to open the machine configuration editor: " + e.getMessage());
        }
    }

    @FXML
    private void onConfigurationsExplore() {
        viewModel.handleConfigurationsExplore();
        switchToView("/fxml/configuration-explore.fxml");
    }

    @FXML
    private void onPvMetadataExplore() {
        viewModel.handlePvMetadataExplore();
        switchToView("/fxml/pv-metadata-explore.fxml");
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
    private void onSampleStatuses() {
        viewModel.handleSampleStatuses();
        switchToView("/fxml/sample-status-explore.fxml");
    }

    @FXML
    private void onDataEvents() {
        viewModel.handleDataEvents();
        switchToView("/fxml/data-event-explore.fxml");
    }

    // Menu action handlers - Metadata menu
    @FXML
    private void onCreatePvMetadata() {
        viewModel.handleCreatePvMetadata();
        switchToView("/fxml/pv-metadata.fxml");
    }

    @FXML
    private void onCreateMachineConfiguration() {
        viewModel.handleCreateMachineConfiguration();
        switchToView("/fxml/machine-configuration.fxml");
    }

    // Menu action handlers - Tools menu

    /**
     * Tools &gt; Delete Demo Data: drops the demo database, on confirmation.
     *
     * <p>This exists because #4 removed the drop that used to run at every launch.  The demo now
     * accumulates data across restarts, so there has to be a deliberate way to clear it.
     *
     * <p><b>The mode is re-checked here, not only by the menu binding.</b>  The binding is a UI
     * affordance and a future refactor can break one without any test noticing -- a disabled item
     * that becomes enabled still looks like a working menu.  This check is what makes the action
     * safe: in deployment mode the target database belongs to someone else's archive, the
     * application never constructed a Mongo client for it, and running the drop anyway would be the
     * single most destructive thing in the application.  Defense in depth on exactly one action, and
     * the one that earns it.
     */
    @FXML
    private void onDeleteDemoData() {

        if (dpApplication == null) {
            logger.warn("delete demo data requested before the application was injected; ignoring");
            return;
        }

        // The guard the binding is not trusted to be.
        if (dpApplication.isDeploymentMode()) {
            logger.error("delete demo data requested in deployment mode; refusing");
            viewModel.updateStatus("Delete Demo Data is not available when connected to a deployment.");
            return;
        }

        if (!confirmDeleteDemoData()) {
            viewModel.updateStatus("Ready");
            return;
        }

        // Unbind before disabling: setDisable() on a bound property throws.  The item is disabled
        // for the duration so the drop cannot be issued twice concurrently, which would have the
        // second run against a database the first already removed.
        deleteDemoDataMenuItem.disableProperty().unbind();
        deleteDemoDataMenuItem.setDisable(true);
        viewModel.updateStatus("Deleting demo database " + MongoInterface.DEMO_DATABASE_NAME + "...");

        final Task<Boolean> deleteTask = new Task<>() {
            @Override
            protected Boolean call() {
                return MongoInterface.deleteDemoDatabase();
            }
        };

        // Outcome is applied in setOnSucceeded rather than published from inside call(), the same
        // ordering rule the explore views follow: setOnSucceeded already runs on the FX thread, and
        // a Platform.runLater from the task body would queue behind it.
        deleteTask.setOnSucceeded(e -> {
            final boolean dropped = Boolean.TRUE.equals(deleteTask.getValue());
            if (dropped) {
                onDemoDataDeleted();
            } else {
                // The database is untouched, so the session state must NOT be cleared -- an
                // application showing no data beside a populated archive is worse than one showing
                // a failure.
                viewModel.updateStatus(
                        "Failed to delete the demo database. See the log for details; the data is unchanged.");
            }
            restoreDeleteDemoDataMenuItem();
        });

        deleteTask.setOnFailed(e -> {
            logger.error("demo database delete task failed", deleteTask.getException());
            viewModel.updateStatus(
                    "Failed to delete the demo database: "
                            + (deleteTask.getException() == null
                                    ? "unknown error"
                                    : deleteTask.getException().getMessage()));
            restoreDeleteDemoDataMenuItem();
        });

        final Thread deleteThread = new Thread(deleteTask);
        deleteThread.setDaemon(true);
        deleteThread.start();
    }

    /**
     * Re-binds the menu item after the action completes.
     *
     * <p>The handler disables the item directly for the duration of the drop, which requires
     * unbinding it first -- setDisable() on a bound property throws.  Rebinding rather than simply
     * re-enabling is the point: re-enabling would leave the item permanently detached from
     * deleteDemoDataEnabled, so it would stay enabled through any later state change.
     */
    private void restoreDeleteDemoDataMenuItem() {
        deleteDemoDataMenuItem.disableProperty().bind(viewModel.deleteDemoDataEnabledProperty().not());
    }

    /**
     * Brings the application back to its pre-ingestion state after a successful delete.
     *
     * <p>All three of these follow from the same fact -- the data is gone -- and all three are
     * needed.  Resetting the DpApplication state alone would leave the menus enabled, since they
     * only re-derive when something asks them to; refreshing the menus alone would re-derive them
     * from state that still claims data exists.
     */
    private void onDemoDataDeleted() {

        dpApplication.resetIngestedDataState();

        // Re-derive the Explore menu, which hangs off hasIngestedData and has just become false.
        viewModel.refreshMenuStates();

        // The home view is showing post-ingestion hints and counts for data that no longer exists.
        if (homeController != null) {
            homeController.getViewModel().resetApplicationState();
        }

        viewModel.updateStatus(
                "Deleted demo database " + MongoInterface.DEMO_DATABASE_NAME
                        + ". Use the Ingest menu to generate or import data.");
        logger.info("demo database deleted and session state reset");
    }

    /**
     * Asks whether to drop the demo database, naming it explicitly.
     *
     * <p>The name is in the dialog rather than only in the menu label because "demo data" is a
     * description and {@code dp-demo} is the thing that actually gets dropped -- and a MongoDB
     * instance can hold more than one database, so naming which one is the difference between an
     * informed confirmation and a hopeful one.
     */
    private boolean confirmDeleteDemoData() {

        final Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete demo data?");
        alert.setHeaderText("Drop the MongoDB database \"" + MongoInterface.DEMO_DATABASE_NAME + "\"?");
        alert.setContentText(
                "This permanently deletes everything in it: ingested PV data, providers, PV and "
                        + "machine configuration metadata, datasets, annotations and sample statuses.\n\n"
                        + "This cannot be undone.\n\nDelete it?");

        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }

        final Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == ButtonType.OK;
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
            } else if (controller instanceof PvMetadataController) {
                PvMetadataController pmController = (PvMetadataController) controller;
                pmController.setDpApplication(dpApplication);
                pmController.setPrimaryStage(primaryStage);
                pmController.setMainController(this);
            } else if (controller instanceof MachineConfigurationController) {
                MachineConfigurationController mcController = (MachineConfigurationController) controller;
                mcController.setDpApplication(dpApplication);
                mcController.setPrimaryStage(primaryStage);
                mcController.setMainController(this);
            } else if (controller instanceof PvMetadataExploreController) {
                PvMetadataExploreController pmeController = (PvMetadataExploreController) controller;
                pmeController.setDpApplication(dpApplication);
                pmeController.setPrimaryStage(primaryStage);
                pmeController.setMainController(this);
            } else if (controller instanceof SampleStatusExploreController) {
                SampleStatusExploreController ssController = (SampleStatusExploreController) controller;
                ssController.setDpApplication(dpApplication);
                ssController.setPrimaryStage(primaryStage);
                ssController.setMainController(this);
            } else if (controller instanceof ConfigurationExploreController) {
                ConfigurationExploreController ceController = (ConfigurationExploreController) controller;
                ceController.setDpApplication(dpApplication);
                ceController.setPrimaryStage(primaryStage);
                ceController.setMainController(this);
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