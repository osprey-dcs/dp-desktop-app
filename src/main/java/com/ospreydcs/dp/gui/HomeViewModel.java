package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.service.inprocess.MongoInterface;
import javafx.beans.property.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HomeViewModel {

    private static final Logger logger = LogManager.getLogger();

    // Properties for dynamic content
    private final StringProperty hintsText = new SimpleStringProperty();
    private final StringProperty statusText = new SimpleStringProperty();
    private final StringProperty detailsText = new SimpleStringProperty();

    // Application state tracking
    private final BooleanProperty hasIngestedData = new SimpleBooleanProperty(false);
    private final BooleanProperty hasPerformedQueries = new SimpleBooleanProperty(false);
    private final StringProperty lastOperationResult = new SimpleStringProperty();

    private DpApplication dpApplication;

    /**
     * The hint shown in deployment mode, in place of every ingestion-oriented hint below.
     *
     * <p>It is a distinct string rather than a variation because {@code HomeController} dispatches
     * on substrings of the hint to decide which hyperlinks to build.  A deployment hint that
     * happened to contain "Explore→Data" would be rendered by the post-ingestion branch, which
     * builds its sentence from its own literals and would therefore display text this view model
     * never wrote.
     */
    static final String DEPLOYMENT_HINT =
            "Connected to a deployment. Use Explore→Data to query the archive, "
                    + "or the other Explore menus to browse its metadata.";

    static final String DEPLOYMENT_DETAILS =
            "Ingestion and metadata authoring are disabled when connected to a deployment. "
                    + "The Explore views are available immediately.";

    /**
     * Shown in demo mode before this session has ingested anything.
     *
     * <p>It deliberately does not say the archive is empty, which is what it said before #4.  The
     * demo database is no longer dropped at launch, so "nothing ingested <i>this session</i>" and
     * "nothing in the database" stopped being the same statement -- and this is the screen a user
     * reads right after starting the app on top of a previous session's data.
     *
     * <p>Naming the database is the other half: {@code dp-demo} is what Tools &gt; Delete Demo Data
     * actually drops, and this is the only place in the UI that says so.
     */
    static final String DEMO_DETAILS_NO_SESSION_DATA =
            "Nothing has been ingested in this session. The demo database ("
                    + MongoInterface.DEMO_DATABASE_NAME
                    + ") keeps data between runs, so it may still hold data from a previous one -- "
                    + "use Explore to look, or Tools \u2192 Delete Demo Data to clear it.";

    /**
     * Whether the application is pointed at remote services.  Fixed at launch, like
     * {@code MainViewModel}'s copy, and for the same reason it is a plain field rather than a
     * property.
     */
    private boolean deploymentMode = false;

    public HomeViewModel() {
        initializeDefaultContent();
        setupPropertyListeners();
        logger.debug("HomeViewModel initialized");
    }

    private void initializeDefaultContent() {
        if (deploymentMode) {
            hintsText.set(DEPLOYMENT_HINT);
            detailsText.set(DEPLOYMENT_DETAILS);
        } else {
            hintsText.set("Start by using the Ingest→Generate or Ingest→Import menus to generate or import some PV data and ingest it to the MLDP archive.");
            detailsText.set(DEMO_DETAILS_NO_SESSION_DATA);
        }
        statusText.set("Ready");
    }

    private void setupPropertyListeners() {
        // Update hints and details based on application state
        hasIngestedData.addListener((obs, oldVal, newVal) -> updateContent());
        hasPerformedQueries.addListener((obs, oldVal, newVal) -> updateContent());
        lastOperationResult.addListener((obs, oldVal, newVal) -> updateContent());
    }

    private void updateContent() {
        // Mode is checked first, and short-circuits the rest.  Every branch below instructs the
        // user to use a menu that deployment mode disables, so falling through to them would hand
        // out directions to greyed-out items -- which reads as the app being broken rather than as
        // the mode being different.
        if (deploymentMode) {
            hintsText.set(DEPLOYMENT_HINT);
            detailsText.set(DEPLOYMENT_DETAILS);
            if (lastOperationResult.get() != null && !lastOperationResult.get().trim().isEmpty()) {
                statusText.set(lastOperationResult.get());
            }
            return;
        }

        if (hasIngestedData.get()) {
            if (hasPerformedQueries.get()) {
                hintsText.set("Data ingestion and queries completed. Use the Tools menu to annotate data or export results.");
                detailsText.set("You have ingested data and performed queries. Explore the Tools menu for additional functionality.");
            } else {
                hintsText.set("Data ingested successfully! Use Explore→Data to query time-series data, Explore→PVs to browse metadata, or Explore→Annotations to search annotations.");
                detailsText.set("Data is available in the archive. Try querying some PV data or exploring metadata.");
            }
        } else {
            hintsText.set("Start by using the Ingest→Generate or Ingest→Import menus to generate or import some PV data and ingest it to the MLDP archive.");
            detailsText.set(DEMO_DETAILS_NO_SESSION_DATA);
        }

        // Update status with last operation result if available
        if (lastOperationResult.get() != null && !lastOperationResult.get().trim().isEmpty()) {
            statusText.set(lastOperationResult.get());
        }
    }

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        if (dpApplication != null) {
            setDeploymentMode(dpApplication.isDeploymentMode());
        }
        logger.debug("DpApplication injected into HomeViewModel");
    }

    /**
     * Also the test seam for the mode, so the hint branching is assertable without a
     * {@link DpApplication}.
     */
    void setDeploymentMode(boolean deploymentMode) {
        this.deploymentMode = deploymentMode;
        updateContent();
    }

    public boolean isDeploymentMode() {
        return deploymentMode;
    }

    // Property getters
    public StringProperty hintsTextProperty() { return hintsText; }
    public StringProperty statusTextProperty() { return statusText; }
    public StringProperty detailsTextProperty() { return detailsText; }

    // State property getters
    public BooleanProperty hasIngestedDataProperty() { return hasIngestedData; }
    public BooleanProperty hasPerformedQueriesProperty() { return hasPerformedQueries; }
    public StringProperty lastOperationResultProperty() { return lastOperationResult; }

    // Public methods for updating state
    public void updateDataIngestedState(boolean hasData) {
        hasIngestedData.set(hasData);
        logger.debug("Data ingested state updated: {}", hasData);
    }

    public void updateQueriesPerformedState(boolean hasPerformed) {
        hasPerformedQueries.set(hasPerformed);
        logger.debug("Queries performed state updated: {}", hasPerformed);
    }

    public void updateLastOperationResult(String result) {
        lastOperationResult.set(result);
        logger.debug("Last operation result updated: {}", result);
    }

    public void onSuccessfulDataGeneration(String result) {
        updateDataIngestedState(true);
        updateLastOperationResult(result);
        logger.info("Data generation success recorded in home view: {}", result);
    }

    public void onSuccessfulQuery(String result) {
        updateQueriesPerformedState(true);
        updateLastOperationResult(result);
        logger.info("Query success recorded in home view: {}", result);
    }

    /**
     * Resets the session state.  Deliberately does NOT reset {@code deploymentMode}: the mode is a
     * property of how the application was launched, not of the session, and clearing it here would
     * make a reset silently re-advertise the ingestion menus that deployment mode disables.
     */
    public void resetApplicationState() {
        hasIngestedData.set(false);
        hasPerformedQueries.set(false);
        lastOperationResult.set("");
        initializeDefaultContent();
        logger.info("Application state reset in home view");
    }
}