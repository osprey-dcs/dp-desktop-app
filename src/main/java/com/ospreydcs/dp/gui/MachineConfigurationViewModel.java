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

        final Task<SaveConfigurationApiResult> saveTask = new Task<>() {
            @Override
            protected SaveConfigurationApiResult call() throws Exception {

                // Warn before clobbering an existing record.  A null return means the user
                // declined; the task reports that through a null result rather than an error.
                if (!confirmOverwriteIfExists(configurationNameValue)) {
                    return null;
                }

                return dpApplication.saveConfiguration(
                        configurationNameValue,
                        categoryValue,
                        descriptionValue,
                        parentValue,
                        tags,
                        attributeMap,
                        modifiedByValue);
            }
        };

        saveTask.setOnSucceeded(e -> Platform.runLater(() -> {
            isSaving.set(false);
            final SaveConfigurationApiResult apiResult = saveTask.getValue();

            if (apiResult == null) {
                // The user declined the overwrite, or a status message was already set explaining
                // why the existence check could not be completed.
                if (!statusMessage.get().startsWith("Save cancelled")
                        && !statusMessage.get().startsWith("Save failed")) {
                    statusMessage.set("Save failed: null response from service");
                    logger.error("saveConfiguration returned a null result");
                }
                return;
            }

            if (apiResult.resultStatus.isError) {
                statusMessage.set("Save failed: " + apiResult.resultStatus.msg);
                logger.error("saveConfiguration failed: {}", apiResult.resultStatus.msg);
                return;
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
    private boolean confirmOverwriteIfExists(String configurationNameValue) throws InterruptedException {

        final GetConfigurationApiResult getResult = dpApplication.getConfiguration(configurationNameValue);

        if (getResult == null) {
            // Treat an unusable existence check as a hard stop rather than silently overwriting.
            Platform.runLater(() -> statusMessage.set(
                    "Save failed: could not check for an existing configuration"));
            logger.error("getConfiguration returned a null result for: {}", configurationNameValue);
            return false;
        }

        if (getResult.isReject()) {
            // No existing record - nothing to overwrite.
            logger.debug("no existing configuration for: {}, saving as new", configurationNameValue);
            return true;
        }

        if (getResult.resultStatus.isError) {
            // A genuine failure, not a not-found.  Do not save.
            Platform.runLater(() -> statusMessage.set(
                    "Save failed: could not check for an existing configuration: "
                            + getResult.resultStatus.msg));
            logger.error("getConfiguration failed for {}: {}",
                    configurationNameValue, getResult.resultStatus.msg);
            return false;
        }

        // A record exists and would be replaced in its entirety.  Ask before proceeding.
        logger.debug("existing configuration found for: {}, confirming overwrite", configurationNameValue);

        if (overwriteConfirmation == null) {
            // No dialog wired up: proceed rather than deadlock, but say so.
            logger.warn("no overwrite confirmation handler set; overwriting {}", configurationNameValue);
            return true;
        }

        final boolean confirmed = runOnFxThreadAndWait(
                () -> overwriteConfirmation.confirmOverwrite(configurationNameValue));

        if (!confirmed) {
            Platform.runLater(() -> statusMessage.set("Save cancelled: existing configuration not replaced"));
            logger.info("user declined to overwrite existing configuration: {}", configurationNameValue);
        }

        return confirmed;
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

            // The id here is the server-generated one when the request omitted it, which is the
            // only handle on the new record - so it goes into the list.
            activations.add(new ConfigurationActivationDetail(
                    apiResult.clientActivationId, configurationNameValue, startTime, endTime));

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
    private static boolean runOnFxThreadAndWait(BooleanSupplier supplier) throws InterruptedException {

        if (Platform.isFxApplicationThread()) {
            return supplier.getAsBoolean();
        }

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final boolean[] result = new boolean[1];

        Platform.runLater(() -> {
            try {
                result[0] = supplier.getAsBoolean();
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        return result[0];
    }

    private static void startDaemon(Task<?> task, String threadName) {
        final Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }
}
