package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.result.GetConfigurationApiResult;
import com.ospreydcs.dp.client.result.SaveConfigurationActivationApiResult;
import com.ospreydcs.dp.client.result.SaveConfigurationApiResult;
import com.ospreydcs.dp.gui.component.AttributesListComponent;
import com.ospreydcs.dp.gui.component.TagsListComponent;
import com.ospreydcs.dp.gui.model.ConfigurationActivationDetail;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * ViewModel for the Machine Configuration view, which creates machine configuration records via
 * saveConfiguration() and their activation intervals via saveConfigurationActivation().
 *
 * Note that tags and attributes are NOT stored in this ViewModel.  They are owned by the reusable
 * list components - four of them, since the configuration and the activation carry separate tag
 * and attribute fields on separate records - and are read directly from those component instances
 * at save time per the Critical Integration Pattern.  The components are injected by the
 * controller.
 */
public class MachineConfigurationViewModel {

    private static final Logger logger = LogManager.getLogger();

    /*
     * How long a background save waits for a confirmation dialog to be answered on the FX thread
     * before giving up.  Generous, since it is bounding a human response, not a service call; it
     * exists only so that an FX thread which will never answer cannot park the save thread forever.
     */
    private static final long FX_CONFIRMATION_TIMEOUT_SECONDS = 300;

    // Configuration form properties
    private final StringProperty configurationName = new SimpleStringProperty("");
    private final StringProperty category = new SimpleStringProperty("");
    private final StringProperty configurationDescription = new SimpleStringProperty("");
    private final StringProperty parentConfigurationName = new SimpleStringProperty("");
    private final StringProperty configurationModifiedBy = new SimpleStringProperty("");

    // Activation form properties
    private final StringProperty clientActivationId = new SimpleStringProperty("");
    private final StringProperty activationDescription = new SimpleStringProperty("");
    private final StringProperty activationModifiedBy = new SimpleStringProperty("");

    /*
     * The configuration name a configuration was actually saved under in this session, as returned
     * by the server, and the flag gating the activation section on it.  These are deliberately not
     * the same as configurationName above: that property tracks the text field, which the user can
     * keep editing after a save, while an activation must reference the name the server resolved.
     */
    private final StringProperty savedConfigurationName = new SimpleStringProperty("");
    private final BooleanProperty configurationSaved = new SimpleBooleanProperty(false);

    // Activations created in this session
    private final ObservableList<ConfigurationActivationDetail> activations =
            FXCollections.observableArrayList();

    // Status properties
    private final StringProperty statusMessage =
            new SimpleStringProperty("Ready to save a machine configuration");
    private final BooleanProperty isSaving = new SimpleBooleanProperty(false);

    // Dependencies
    private DpApplication dpApplication;
    private MainController mainController;

    // Reusable component references - data is read from these, never from this ViewModel
    private TagsListComponent configurationTagsComponent;
    private AttributesListComponent configurationAttributesComponent;
    private TagsListComponent activationTagsComponent;
    private AttributesListComponent activationAttributesComponent;

    /*
     * Asks the user whether to overwrite an existing configuration record.  Supplied by the
     * controller, which owns the dialog; kept as a functional interface so this ViewModel stays
     * testable and free of JavaFX dialog code.  Returns true to proceed with the save.
     */
    private OverwriteConfirmation overwriteConfirmation;

    @FunctionalInterface
    public interface OverwriteConfirmation {
        boolean confirmOverwrite(String configurationName);
    }

    /*
     * Asks the user whether to replace an activation already created in this session under the
     * same client activation id.  Same rationale as OverwriteConfirmation: the controller owns the
     * dialog, this ViewModel stays free of JavaFX dialog code.  Returns true to proceed.
     */
    private ActivationOverwriteConfirmation activationOverwriteConfirmation;

    @FunctionalInterface
    public interface ActivationOverwriteConfirmation {
        boolean confirmOverwrite(String clientActivationId);
    }

    /*
     * Clears the activation date and time controls, which are owned by the controller rather than
     * by this ViewModel.  Supplied by the controller so that clearing the activation form clears
     * the whole form and not just the parts this ViewModel holds.
     */
    private Runnable activationTemporalFieldsReset;

    public MachineConfigurationViewModel() {
        logger.debug("MachineConfigurationViewModel initialized");
    }

    // Dependency injection methods

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        logger.debug("DpApplication injected into MachineConfigurationViewModel");
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        logger.debug("MainController injected into MachineConfigurationViewModel");
    }

    public void setConfigurationTagsComponent(TagsListComponent component) {
        this.configurationTagsComponent = component;
        logger.debug("Configuration tags component injected into MachineConfigurationViewModel");
    }

    public void setConfigurationAttributesComponent(AttributesListComponent component) {
        this.configurationAttributesComponent = component;
        logger.debug("Configuration attributes component injected into MachineConfigurationViewModel");
    }

    public void setActivationTagsComponent(TagsListComponent component) {
        this.activationTagsComponent = component;
        logger.debug("Activation tags component injected into MachineConfigurationViewModel");
    }

    public void setActivationAttributesComponent(AttributesListComponent component) {
        this.activationAttributesComponent = component;
        logger.debug("Activation attributes component injected into MachineConfigurationViewModel");
    }

    public void setOverwriteConfirmation(OverwriteConfirmation overwriteConfirmation) {
        this.overwriteConfirmation = overwriteConfirmation;
    }

    public void setActivationOverwriteConfirmation(
            ActivationOverwriteConfirmation activationOverwriteConfirmation) {
        this.activationOverwriteConfirmation = activationOverwriteConfirmation;
    }

    public void setActivationTemporalFieldsReset(Runnable activationTemporalFieldsReset) {
        this.activationTemporalFieldsReset = activationTemporalFieldsReset;
    }

    // Property getters

    public StringProperty configurationNameProperty() { return configurationName; }
    public StringProperty categoryProperty() { return category; }
    public StringProperty configurationDescriptionProperty() { return configurationDescription; }
    public StringProperty parentConfigurationNameProperty() { return parentConfigurationName; }
    public StringProperty configurationModifiedByProperty() { return configurationModifiedBy; }

    public StringProperty clientActivationIdProperty() { return clientActivationId; }
    public StringProperty activationDescriptionProperty() { return activationDescription; }
    public StringProperty activationModifiedByProperty() { return activationModifiedBy; }

    public StringProperty savedConfigurationNameProperty() { return savedConfigurationName; }
    public BooleanProperty configurationSavedProperty() { return configurationSaved; }
    public ObservableList<ConfigurationActivationDetail> getActivations() { return activations; }

    public StringProperty statusMessageProperty() { return statusMessage; }
    public BooleanProperty isSavingProperty() { return isSaving; }

    /**
     * Saves the machine configuration record on a background thread.
     *
     * Because saveConfiguration() is a full-replace upsert, an existing record for this name would
     * be silently overwritten, so this first checks for one and asks the user to confirm.  That
     * check is itself a service call, so it runs on the background task too; the confirmation
     * dialog is then raised back on the FX thread.
     *
     * Tags and attributes are read from the injected component instances rather than from this
     * ViewModel, since the components own that state.
     */
    public void saveConfiguration() {

        if (dpApplication == null) {
            statusMessage.set("DpApplication not initialized");
            return;
        }

        final String configurationNameValue =
                configurationName.get() == null ? "" : configurationName.get().trim();
        if (configurationNameValue.isEmpty()) {
            statusMessage.set("Configuration Name is required");
            return;
        }

        final String categoryValue = category.get() == null ? "" : category.get().trim();
        if (categoryValue.isEmpty()) {
            statusMessage.set("Category is required");
            return;
        }

        // Read the list data from the components on the FX thread, before handing off to the
        // background task, so the task does not touch the observable lists off-thread.
        final List<String> tags = configurationTagsComponent != null
                ? new ArrayList<>(configurationTagsComponent.getTags()) : new ArrayList<>();
        final Map<String, String> attributeMap = configurationAttributesComponent != null
                ? AttributesListComponent.attributesToMap(configurationAttributesComponent.getAttributes())
                : new LinkedHashMap<>();

        final String descriptionValue =
                configurationDescription.get() == null ? "" : configurationDescription.get().trim();
        final String parentValue =
                parentConfigurationName.get() == null ? "" : parentConfigurationName.get().trim();
        final String modifiedByValue =
                configurationModifiedBy.get() == null ? "" : configurationModifiedBy.get().trim();

        logger.debug(
                "Saving configuration, name: {}, category: {}, tags: {}, attributes: {}",
                configurationNameValue, categoryValue, tags.size(), attributeMap.size());

        isSaving.set(true);
        statusMessage.set("Saving configuration...");

        final Task<SaveOutcome> saveTask = new Task<>() {
            @Override
            protected SaveOutcome call() throws Exception {

                // Warn before clobbering an existing record.  The pre-save check reports its own
                // outcome as a typed value rather than as a null result, so the success handler can
                // tell "the user declined" from "the service returned nothing" without inspecting
                // the status message.
                final PreSaveOutcome preSave = confirmOverwriteIfExists(configurationNameValue);
                if (preSave != PreSaveOutcome.PROCEED) {
                    return SaveOutcome.notAttempted(preSave);
                }

                return SaveOutcome.attempted(dpApplication.saveConfiguration(
                        configurationNameValue,
                        categoryValue,
                        descriptionValue,
                        parentValue,
                        tags,
                        attributeMap,
                        modifiedByValue));
            }
        };

        saveTask.setOnSucceeded(e -> Platform.runLater(() -> {
            isSaving.set(false);
            final SaveOutcome outcome = saveTask.getValue();

            if (outcome == null) {
                // Not reachable through call() above, which always returns a value; handled so a
                // future change cannot turn this into a silent no-op.
                statusMessage.set("Save failed: no outcome reported");
                logger.error("saveConfiguration task produced a null outcome");
                return;
            }

            if (!outcome.wasAttempted()) {
                // The save was deliberately not attempted.  confirmOverwriteIfExists() has already
                // set the status message explaining which case this was.
                logger.debug("saveConfiguration not attempted: {}", outcome.preSaveOutcome);
                return;
            }

            final SaveConfigurationApiResult apiResult = outcome.apiResult;

            if (apiResult == null) {
                statusMessage.set("Save failed: null response from service");
                logger.error("saveConfiguration returned a null result");
                return;
            }

            if (apiResult.resultStatus.isError) {
                statusMessage.set("Save failed: " + apiResult.resultStatus.msg);
                logger.error("saveConfiguration failed: {}", apiResult.resultStatus.msg);
                return;
            }

            /*
             * The session activation list describes activations of the configuration named in
             * savedConfigurationName.  When a save switches that name, entries created under the
             * previous configuration no longer belong to what the section now shows, so they are
             * discarded rather than left to be read as activations of the new configuration.
             */
            final String previousName = savedConfigurationName.get();
            if (previousName != null && !previousName.isEmpty()
                    && !previousName.equals(apiResult.configurationName)
                    && !activations.isEmpty()) {
                logger.debug(
                        "clearing {} session activation(s) recorded under previous configuration: {}",
                        activations.size(), previousName);
                activations.clear();
            }

            // Bind the activation section to the name the server actually saved under, not to the
            // text field, which the user may keep editing.
            savedConfigurationName.set(apiResult.configurationName);
            configurationSaved.set(true);

            statusMessage.set("Configuration saved: " + apiResult.configurationName);
            logger.info("Configuration saved: {}", apiResult.configurationName);
        }));

        saveTask.setOnFailed(e -> Platform.runLater(() -> {
            isSaving.set(false);
            final Throwable exception = saveTask.getException();
            final String exceptionMessage = exception != null ? exception.getMessage() : "unknown error";
            statusMessage.set("Save failed with exception: " + exceptionMessage);
            logger.error("saveConfiguration threw an exception", exception);
        }));

        startDaemon(saveTask, "save-configuration");
    }

    /**
     * Returns true when the save should proceed: either no record exists for this name, or the user
     * confirmed overwriting the one that does.
     *
     * getConfiguration() reports a missing record as a rejection rather than as an empty successful
     * result, so this branches on isReject() rather than isError().  That distinction matters: a
     * service that is simply unreachable also sets isError, and treating that as "no existing
     * record" would suppress the very warning this method exists to raise.
     *
     * Note that REJECT also covers a request that failed server-side validation.  Reading a reject
     * as not-found is safe here only because the caller has already validated that the name is
     * non-blank, which is the sole validation getConfiguration() performs.
     *
     * Runs on the background task; the dialog itself is raised on the FX thread and waited on.
     */
    private PreSaveOutcome confirmOverwriteIfExists(String configurationNameValue)
            throws InterruptedException {

        final GetConfigurationApiResult getResult = dpApplication.getConfiguration(configurationNameValue);

        if (getResult == null) {
            // Treat an unusable existence check as a hard stop rather than silently overwriting.
            Platform.runLater(() -> statusMessage.set(
                    "Save failed: could not check for an existing configuration"));
            logger.error("getConfiguration returned a null result for: {}", configurationNameValue);
            return PreSaveOutcome.CHECK_FAILED;
        }

        if (getResult.isReject()) {
            // No existing record - nothing to overwrite.
            logger.debug("no existing configuration for: {}, saving as new", configurationNameValue);
            return PreSaveOutcome.PROCEED;
        }

        if (getResult.resultStatus.isError) {
            // A genuine failure, not a not-found.  Do not save.
            Platform.runLater(() -> statusMessage.set(
                    "Save failed: could not check for an existing configuration: "
                            + getResult.resultStatus.msg));
            logger.error("getConfiguration failed for {}: {}",
                    configurationNameValue, getResult.resultStatus.msg);
            return PreSaveOutcome.CHECK_FAILED;
        }

        // A record exists and would be replaced in its entirety.  Ask before proceeding.
        logger.debug("existing configuration found for: {}, confirming overwrite", configurationNameValue);

        if (overwriteConfirmation == null) {
            // No dialog wired up: proceed rather than deadlock, but say so.
            logger.warn("no overwrite confirmation handler set; overwriting {}", configurationNameValue);
            return PreSaveOutcome.PROCEED;
        }

        final Boolean confirmed = runOnFxThreadAndWait(
                () -> overwriteConfirmation.confirmOverwrite(configurationNameValue));

        if (confirmed == null) {
            // The FX thread never answered - see runOnFxThreadAndWait().  Do not save: the whole
            // point of this check is that an unconfirmed overwrite must not go through.
            Platform.runLater(() -> statusMessage.set(
                    "Save failed: timed out waiting for the overwrite confirmation"));
            logger.error("timed out waiting for overwrite confirmation for: {}", configurationNameValue);
            return PreSaveOutcome.CHECK_FAILED;
        }

        if (!confirmed) {
            Platform.runLater(() -> statusMessage.set("Save cancelled: existing configuration not replaced"));
            logger.info("user declined to overwrite existing configuration: {}", configurationNameValue);
            return PreSaveOutcome.DECLINED;
        }

        return PreSaveOutcome.PROCEED;
    }

    /**
     * Why a configuration save did or did not go ahead, as decided before the save call is made.
     * Typed so the success handler can distinguish these cases without reading the status message.
     */
    private enum PreSaveOutcome {
        /** No existing record, or the user confirmed replacing the one that exists. */
        PROCEED,
        /** An existing record was found and the user declined to replace it. */
        DECLINED,
        /** The existence check could not be completed, so the save was not attempted. */
        CHECK_FAILED
    }

    /**
     * The result of the configuration save task: either the save was attempted and carries the API
     * result, or it was deliberately not attempted and carries the reason.
     */
    private static final class SaveOutcome {

        final PreSaveOutcome preSaveOutcome;
        final SaveConfigurationApiResult apiResult;

        private SaveOutcome(PreSaveOutcome preSaveOutcome, SaveConfigurationApiResult apiResult) {
            this.preSaveOutcome = preSaveOutcome;
            this.apiResult = apiResult;
        }

        static SaveOutcome attempted(SaveConfigurationApiResult apiResult) {
            return new SaveOutcome(PreSaveOutcome.PROCEED, apiResult);
        }

        static SaveOutcome notAttempted(PreSaveOutcome preSaveOutcome) {
            return new SaveOutcome(preSaveOutcome, null);
        }

        boolean wasAttempted() {
            return preSaveOutcome == PreSaveOutcome.PROCEED;
        }
    }

    /**
     * Saves a configuration activation on a background thread and appends it to the session list.
     *
     * The configuration name is taken from the name the server saved, so the reference is always to
     * a configuration known to exist - which is what keeps the server's "no Configuration found"
     * rejection unreachable through normal use of this view.
     */
    public void addActivation(Instant startTime, Instant endTime) {

        if (dpApplication == null) {
            statusMessage.set("DpApplication not initialized");
            return;
        }

        if (!configurationSaved.get()) {
            statusMessage.set("Save a configuration before adding activations");
            return;
        }

        if (startTime == null) {
            statusMessage.set("Start Date and Time are required");
            return;
        }

        if (endTime == null) {
            statusMessage.set("End Date and Time are required");
            return;
        }

        // The server rejects this too, but checking here turns a round trip into immediate feedback.
        if (!endTime.isAfter(startTime)) {
            statusMessage.set("End time must be after start time");
            return;
        }

        final String configurationNameValue = savedConfigurationName.get();

        // Read the list data from the components on the FX thread, as above.
        final List<String> tags = activationTagsComponent != null
                ? new ArrayList<>(activationTagsComponent.getTags()) : new ArrayList<>();
        final Map<String, String> attributeMap = activationAttributesComponent != null
                ? AttributesListComponent.attributesToMap(activationAttributesComponent.getAttributes())
                : new LinkedHashMap<>();

        final String clientActivationIdValue =
                clientActivationId.get() == null ? "" : clientActivationId.get().trim();
        final String descriptionValue =
                activationDescription.get() == null ? "" : activationDescription.get().trim();
        final String modifiedByValue =
                activationModifiedBy.get() == null ? "" : activationModifiedBy.get().trim();

        /*
         * saveConfigurationActivation() is a full-replace upsert keyed by clientActivationId, so a
         * supplied id that already names a record replaces it outright.  When that record is one
         * this session created, the collision is detectable here and is confirmed before the call,
         * so the user is not silently editing an activation they believe they are adding.
         *
         * A supplied id may of course collide with a record this session knows nothing about.
         * Catching that needs a server round trip, and AnnotationClient currently exposes no
         * wrapper for the getConfigurationActivation() RPC - see the follow-up issue.  Until then
         * the field carries a warning that a supplied id replaces any existing activation.
         */
        if (findSessionActivation(clientActivationIdValue) != null) {
            if (activationOverwriteConfirmation == null) {
                logger.warn("no activation overwrite confirmation handler set; replacing {}",
                        clientActivationIdValue);
            } else if (!activationOverwriteConfirmation.confirmOverwrite(clientActivationIdValue)) {
                statusMessage.set("Add cancelled: existing activation not replaced");
                logger.info("user declined to replace existing activation: {}", clientActivationIdValue);
                return;
            }
        }

        logger.debug(
                "Saving activation for configuration: {}, start: {}, end: {}",
                configurationNameValue, startTime, endTime);

        isSaving.set(true);
        statusMessage.set("Saving activation...");

        final Task<SaveConfigurationActivationApiResult> saveTask = new Task<>() {
            @Override
            protected SaveConfigurationActivationApiResult call() {
                return dpApplication.saveConfigurationActivation(
                        clientActivationIdValue,
                        configurationNameValue,
                        startTime,
                        endTime,
                        descriptionValue,
                        tags,
                        attributeMap,
                        modifiedByValue);
            }
        };

        saveTask.setOnSucceeded(e -> Platform.runLater(() -> {
            isSaving.set(false);
            final SaveConfigurationActivationApiResult apiResult = saveTask.getValue();

            if (apiResult == null) {
                statusMessage.set("Save failed: null response from service");
                logger.error("saveConfigurationActivation returned a null result");
                return;
            }

            if (apiResult.resultStatus.isError) {
                // This is where the overlap rejection surfaces, verbatim.
                statusMessage.set("Save failed: " + apiResult.resultStatus.msg);
                logger.error("saveConfigurationActivation failed: {}", apiResult.resultStatus.msg);
                return;
            }

            /*
             * The id here is the server-generated one when the request omitted it, which is the
             * only handle on the new record - so it goes into the list.
             *
             * Reconciled rather than appended: when the save replaced an activation this session
             * already listed, the list must show the replacement in place of the stale row, not
             * both.  Matching is on the id the server reports, which is the record's actual key.
             */
            final ConfigurationActivationDetail savedActivation = new ConfigurationActivationDetail(
                    apiResult.clientActivationId, configurationNameValue, startTime, endTime);

            final ConfigurationActivationDetail replaced =
                    findSessionActivation(apiResult.clientActivationId);

            if (replaced != null) {
                activations.set(activations.indexOf(replaced), savedActivation);
                logger.debug("replaced session activation row for id: {}", apiResult.clientActivationId);
            } else {
                activations.add(savedActivation);
            }

            clearActivationForm();

            statusMessage.set("Activation saved: " + apiResult.clientActivationId);
            logger.info("Activation saved: {}", apiResult.clientActivationId);
        }));

        saveTask.setOnFailed(e -> Platform.runLater(() -> {
            isSaving.set(false);
            final Throwable exception = saveTask.getException();
            final String exceptionMessage = exception != null ? exception.getMessage() : "unknown error";
            statusMessage.set("Save failed with exception: " + exceptionMessage);
            logger.error("saveConfigurationActivation threw an exception", exception);
        }));

        startDaemon(saveTask, "save-configuration-activation");
    }

    /**
     * Returns the activation this session already recorded under the given client activation id, or
     * null when there is none.  Ids are compared exactly: they are server-side record keys, not
     * user-facing text.
     */
    private ConfigurationActivationDetail findSessionActivation(String clientActivationIdValue) {
        if (clientActivationIdValue == null || clientActivationIdValue.isEmpty()) {
            return null;
        }
        for (ConfigurationActivationDetail activation : activations) {
            if (clientActivationIdValue.equals(activation.clientActivationId)) {
                return activation;
            }
        }
        return null;
    }

    /**
     * Clears the activation entry fields after a successful save, so the next activation is entered
     * from a clean form.  The activation list and the saved configuration binding are preserved.
     */
    private void clearActivationForm() {
        clientActivationId.set("");
        activationDescription.set("");
        activationModifiedBy.set("");

        if (activationTagsComponent != null) {
            activationTagsComponent.clearTags();
        }
        if (activationAttributesComponent != null) {
            activationAttributesComponent.clearAttributes();
        }

        /*
         * The date pickers and time spinners belong to the controller, not to this ViewModel, so
         * clearing them has to go back through it.  Without this the next activation silently
         * reuses the previous interval's time of day - which, given that the server rejects
         * overlapping activations across an entire category, surfaces as a confusing overlap
         * rejection rather than as an obviously stale form.
         */
        if (activationTemporalFieldsReset != null) {
            activationTemporalFieldsReset.run();
        }
    }

    /**
     * Resets the whole view: both forms, all four list components, the session activation list, and
     * the saved-configuration state that gates the activation section.
     */
    public void resetForm() {
        configurationName.set("");
        category.set("");
        configurationDescription.set("");
        parentConfigurationName.set("");
        configurationModifiedBy.set("");

        clearActivationForm();

        if (configurationTagsComponent != null) {
            configurationTagsComponent.clearTags();
        }
        if (configurationAttributesComponent != null) {
            configurationAttributesComponent.clearAttributes();
        }

        activations.clear();
        savedConfigurationName.set("");
        configurationSaved.set(false);

        statusMessage.set("Ready to save a machine configuration");
        logger.debug("Machine configuration form reset");
    }

    /**
     * Runs the supplier on the FX thread and blocks the calling background thread for its result.
     * Used for the overwrite confirmation, which has to be raised on the FX thread but whose answer
     * decides whether the background task proceeds.
     */
    private static Boolean runOnFxThreadAndWait(BooleanSupplier supplier) throws InterruptedException {

        if (Platform.isFxApplicationThread()) {
            return supplier.getAsBoolean();
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = new boolean[1];

        Platform.runLater(() -> {
            try {
                result[0] = supplier.getAsBoolean();
            } finally {
                latch.countDown();
            }
        });

        /*
         * Bounded rather than indefinite.  If the FX thread is gone - the view was navigated away
         * from, or the application is shutting down mid-save - the runnable above never executes
         * and the latch is never counted down.  An unbounded await() would park this thread
         * forever, leaving isSaving true and the progress indicator spinning with no way back.
         * The caller treats a null return as "no answer" and declines to save.
         */
        if (!latch.await(FX_CONFIRMATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return null;
        }

        return result[0];
    }

    private static void startDaemon(Task<?> task, String threadName) {
        final Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }
}
