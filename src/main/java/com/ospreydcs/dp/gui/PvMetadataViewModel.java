package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.result.GetPvMetadataApiResult;
import com.ospreydcs.dp.client.result.SavePvMetadataApiResult;
import com.ospreydcs.dp.grpc.v1.common.Attribute;
import com.ospreydcs.dp.grpc.v1.common.PvMetadata;
import com.ospreydcs.dp.gui.component.AttributesListComponent;
import com.ospreydcs.dp.gui.component.TagsListComponent;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * ViewModel for the PV Metadata editor view, which creates or updates PV metadata records via
 * savePvMetadata().
 *
 * Note that aliases, tags and attributes are NOT stored in this ViewModel.  They are owned by the
 * reusable list components, and are read directly from those component instances at save time per
 * the Critical Integration Pattern.  The components are injected by the controller.
 */
public class PvMetadataViewModel {

    private static final Logger logger = LogManager.getLogger();

    // Form properties
    private final StringProperty pvName = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");
    private final StringProperty modifiedBy = new SimpleStringProperty("");

    // Status properties
    private final StringProperty statusMessage = new SimpleStringProperty("Ready to save PV metadata");
    private final BooleanProperty isSaving = new SimpleBooleanProperty(false);

    /*
     * Asks the user whether to replace an existing record.  The controller owns the dialog, so this
     * ViewModel stays free of JavaFX dialog code -- the same seam as
     * MachineConfigurationViewModel.OverwriteConfirmation, and for the same reason.  Returns true
     * to proceed.
     */
    private OverwriteConfirmation overwriteConfirmation;

    @FunctionalInterface
    public interface OverwriteConfirmation {
        boolean confirmOverwrite(String pvName);
    }

    /*
     * How long a background save waits for the FX thread to answer the confirmation.  Held in a
     * field so a test can shorten it; stalling the FX thread for the production timeout is not an
     * option in a unit suite, and the timeout branch is otherwise unreachable.
     */
    private long fxConfirmationTimeoutSeconds = 300;

    // Dependencies
    private DpApplication dpApplication;
    private MainController mainController;

    // Reusable component references - data is read from these, never from this ViewModel
    private TagsListComponent aliasesComponent;
    private TagsListComponent tagsComponent;
    private AttributesListComponent attributesComponent;

    public PvMetadataViewModel() {
        logger.debug("PvMetadataViewModel initialized");
    }

    // Dependency injection methods

    public void setOverwriteConfirmation(OverwriteConfirmation overwriteConfirmation) {
        this.overwriteConfirmation = overwriteConfirmation;
    }

    /** Test seam: shortens the confirmation wait so the timeout branch is reachable. */
    void setFxConfirmationTimeoutSecondsForTesting(long seconds) {
        this.fxConfirmationTimeoutSeconds = seconds;
    }

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        logger.debug("DpApplication injected into PvMetadataViewModel");
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        logger.debug("MainController injected into PvMetadataViewModel");
    }

    public void setAliasesComponent(TagsListComponent aliasesComponent) {
        this.aliasesComponent = aliasesComponent;
        logger.debug("Aliases component injected into PvMetadataViewModel");
    }

    public void setTagsComponent(TagsListComponent tagsComponent) {
        this.tagsComponent = tagsComponent;
        logger.debug("Tags component injected into PvMetadataViewModel");
    }

    public void setAttributesComponent(AttributesListComponent attributesComponent) {
        this.attributesComponent = attributesComponent;
        logger.debug("Attributes component injected into PvMetadataViewModel");
    }

    // Property getters

    public StringProperty pvNameProperty() { return pvName; }
    public StringProperty descriptionProperty() { return description; }
    public StringProperty modifiedByProperty() { return modifiedBy; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public BooleanProperty isSavingProperty() { return isSaving; }

    /**
     * Saves the PV metadata record on a background thread.
     *
     * Aliases, tags and attributes are read from the injected component instances rather than from
     * this ViewModel, since the components own that state.
     */
    public void savePvMetadata() {

        if (dpApplication == null) {
            statusMessage.set("DpApplication not initialized");
            return;
        }

        final String pvNameValue = pvName.get() == null ? "" : pvName.get().trim();
        if (pvNameValue.isEmpty()) {
            statusMessage.set("PV Name is required");
            return;
        }

        // Read the list data from the components on the FX thread, before handing off to the
        // background task, so the task does not touch the observable lists off-thread.
        final List<String> aliases = aliasesComponent != null
                ? new ArrayList<>(aliasesComponent.getTags()) : new ArrayList<>();
        final List<String> tags = tagsComponent != null
                ? new ArrayList<>(tagsComponent.getTags()) : new ArrayList<>();
        final Map<String, String> attributeMap = attributesComponent != null
                ? AttributesListComponent.attributesToMap(attributesComponent.getAttributes()) : new LinkedHashMap<>();

        final String descriptionValue = description.get() == null ? "" : description.get().trim();
        final String modifiedByValue = modifiedBy.get() == null ? "" : modifiedBy.get().trim();

        logger.debug(
                "Saving PV metadata, pvName: {}, aliases: {}, tags: {}, attributes: {}",
                pvNameValue, aliases.size(), tags.size(), attributeMap.size());

        isSaving.set(true);
        statusMessage.set("Saving PV metadata...");

        final Task<SaveOutcome> saveTask = new Task<>() {
            @Override
            protected SaveOutcome call() throws InterruptedException {
                // Warn before clobbering an existing record.  savePvMetadata() is a FULL-REPLACE
                // upsert keyed on pvName, so a field cleared here is cleared in the archive -- and
                // after a load-for-edit that is the likeliest way to lose stored metadata without
                // any error appearing.
                final PreSaveOutcome preSave = confirmOverwriteIfExists(pvNameValue);
                if (preSave != PreSaveOutcome.PROCEED) {
                    return SaveOutcome.notAttempted(preSave);
                }

                return SaveOutcome.attempted(dpApplication.savePvMetadata(
                        pvNameValue, aliases, tags, attributeMap, descriptionValue, modifiedByValue));
            }
        };

        saveTask.setOnSucceeded(e -> Platform.runLater(() -> {
            isSaving.set(false);
            final SaveOutcome outcome = saveTask.getValue();

            if (outcome == null) {
                statusMessage.set("Save failed: no result from the save task");
                logger.error("savePvMetadata task produced a null outcome");
                return;
            }

            if (!outcome.wasAttempted()) {
                // Deliberately not attempted; confirmOverwriteIfExists() already set the message
                // saying which case this was.
                logger.debug("savePvMetadata not attempted: {}", outcome.preSaveOutcome);
                return;
            }

            final SavePvMetadataApiResult apiResult = outcome.apiResult;

            if (apiResult == null) {
                statusMessage.set("Save failed: null response from service");
                logger.error("savePvMetadata returned a null result");
                return;
            }

            if (apiResult.resultStatus.isError) {
                statusMessage.set("Save failed: " + apiResult.resultStatus.msg);
                logger.error("savePvMetadata failed: {}", apiResult.resultStatus.msg);
                return;
            }

            statusMessage.set("PV metadata saved successfully for PV: " + apiResult.pvName);
            logger.info("PV metadata saved successfully for PV: {}", apiResult.pvName);
        }));

        saveTask.setOnFailed(e -> Platform.runLater(() -> {
            isSaving.set(false);
            final Throwable exception = saveTask.getException();
            final String exceptionMessage = exception != null ? exception.getMessage() : "unknown error";
            statusMessage.set("Save failed with exception: " + exceptionMessage);
            logger.error("savePvMetadata threw an exception", exception);
        }));

        final Thread saveThread = new Thread(saveTask);
        saveThread.setDaemon(true);
        saveThread.start();
    }

    /**
     * Checks for an existing record and asks before replacing it.
     *
     * <p><strong>The check is by the name being SAVED, and the answer is about that name.</strong>
     * getPvMetadata() resolves aliases, so looking up "OLD:NAME" can return the record whose
     * canonical name is something else -- and a save would then target the typed name, creating a
     * NEW record rather than replacing the one found. The confirmation therefore names the record
     * that was actually found, so a user who typed an alias sees which record is at stake rather
     * than being asked about a name that will not be written.
     *
     * <p>Detecting not-found branches on {@code isReject()}, not {@code isError()}: a missing record
     * is reported as a rejection, while an unreachable service sets isError -- and treating that as
     * "no existing record" would suppress the warning exactly when the system is unhealthy.
     */
    private PreSaveOutcome confirmOverwriteIfExists(String pvNameValue) throws InterruptedException {
        final GetPvMetadataApiResult getResult = dpApplication.getPvMetadata(pvNameValue);

        if (getResult == null) {
            // An unusable existence check is a hard stop rather than a silent overwrite.
            Platform.runLater(() -> statusMessage.set(
                    "Save failed: could not check for existing PV metadata"));
            logger.error("getPvMetadata returned a null result for: {}", pvNameValue);
            return PreSaveOutcome.CHECK_FAILED;
        }

        if (getResult.isReject()) {
            logger.debug("no existing PV metadata for: {}, saving as new", pvNameValue);
            return PreSaveOutcome.PROCEED;
        }

        if (getResult.resultStatus.isError) {
            Platform.runLater(() -> statusMessage.set(
                    "Save failed: could not check for existing PV metadata: "
                            + getResult.resultStatus.msg));
            logger.error("getPvMetadata failed for {}: {}", pvNameValue, getResult.resultStatus.msg);
            return PreSaveOutcome.CHECK_FAILED;
        }

        if (overwriteConfirmation == null) {
            // No dialog wired: proceed rather than blocking the save. The view always wires one;
            // this keeps a ViewModel used without a controller (as in tests) usable.
            logger.debug("no overwrite confirmation wired, proceeding for: {}", pvNameValue);
            return PreSaveOutcome.PROCEED;
        }

        // Name the record that was FOUND, which an alias lookup makes different from what was typed.
        final String existingName = getResult.pvMetadata != null
                && !getResult.pvMetadata.getPvName().isEmpty()
                ? getResult.pvMetadata.getPvName()
                : pvNameValue;

        final Boolean confirmed = runOnFxThreadAndWait(
                () -> overwriteConfirmation.confirmOverwrite(existingName));

        if (confirmed == null) {
            // Never answered -- see runOnFxThreadAndWait(). An unconfirmed overwrite must not go
            // through, and must not be reported as a decision the user made.
            Platform.runLater(() -> statusMessage.set(
                    "Save cancelled: the overwrite confirmation timed out"));
            logger.error("timed out waiting for PV metadata overwrite confirmation for: {}",
                    existingName);
            return PreSaveOutcome.CHECK_FAILED;
        }

        if (!confirmed) {
            Platform.runLater(() -> statusMessage.set("Save cancelled - existing record kept"));
            logger.debug("user declined to overwrite PV metadata for: {}", existingName);
            return PreSaveOutcome.DECLINED;
        }

        return PreSaveOutcome.PROCEED;
    }

    /**
     * Runs a confirmation on the FX thread and waits, bounded, for its answer.
     *
     * <p>Bounded rather than indefinite: if the FX thread is gone -- the view was navigated away
     * from, or the application is shutting down mid-save -- an unbounded await would park the save
     * thread forever with isSaving true and the progress indicator spinning. Null means "no
     * answer", which the caller treats as do-not-save without reporting it as a decline.
     */
    private Boolean runOnFxThreadAndWait(BooleanSupplier supplier) throws InterruptedException {
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

        if (!latch.await(fxConfirmationTimeoutSeconds, TimeUnit.SECONDS)) {
            return null;
        }
        return result[0];
    }

    /** Why a save was or was not attempted. */
    private enum PreSaveOutcome {
        /** No existing record, or the user confirmed replacing the one that exists. */
        PROCEED,
        /** An existing record was found and the user declined to replace it. */
        DECLINED,
        /** The existence check could not be completed, so the save was not attempted. */
        CHECK_FAILED
    }

    /**
     * The result of a save task: either attempted, carrying the API result, or deliberately not
     * attempted, carrying the reason.
     *
     * <p>A typed outcome rather than a null sentinel, so the success handler can tell "the user
     * declined" from "the service returned nothing" without inspecting the status message -- which
     * would re-introduce message-sniffing and race the Platform.runLater that sets it.
     */
    private static final class SaveOutcome {
        final SavePvMetadataApiResult apiResult;
        final PreSaveOutcome preSaveOutcome;

        private SaveOutcome(SavePvMetadataApiResult apiResult, PreSaveOutcome preSaveOutcome) {
            this.apiResult = apiResult;
            this.preSaveOutcome = preSaveOutcome;
        }

        static SaveOutcome attempted(SavePvMetadataApiResult apiResult) {
            return new SaveOutcome(apiResult, PreSaveOutcome.PROCEED);
        }

        static SaveOutcome notAttempted(PreSaveOutcome preSaveOutcome) {
            return new SaveOutcome(null, preSaveOutcome);
        }

        boolean wasAttempted() {
            return preSaveOutcome == PreSaveOutcome.PROCEED;
        }
    }

    /**
     * Loads an existing record into the form for editing.
     *
     * <p><strong>The record's pvName is the canonical name, and that is what a subsequent save
     * targets.</strong>  getPvMetadata() resolves aliases, so a record loaded by looking up a
     * historical name comes back under its canonical name.  Because savePvMetadata() is a
     * full-replace upsert keyed on pvName, populating the form from the record -- rather than from
     * whatever the user typed to find it -- is what stops an edit of "OLD:NAME" from silently
     * rewriting, or worse creating, a different record.
     *
     * <p>Aliases, tags and attributes are written into the injected COMPONENTS, not into ViewModel
     * properties: the components are where savePvMetadata() reads them from, and this ViewModel
     * holds no collections for them.  Filling properties instead would load a record whose metadata
     * the save never sees, and the save would then write those fields back as absent -- silently,
     * because the upsert is a full replace.  That is the same defect the Annotation Builder had.
     */
    public void loadFromPvMetadata(PvMetadata record) {
        if (record == null) {
            return;
        }

        resetForm();

        pvName.set(record.getPvName());
        description.set(record.getDescription());
        modifiedBy.set(record.getModifiedBy());

        if (aliasesComponent != null) {
            for (String alias : record.getAliasesList()) {
                aliasesComponent.addTag(alias);
            }
        }
        if (tagsComponent != null) {
            for (String tag : record.getTagsList()) {
                tagsComponent.addTag(tag);
            }
        }
        if (attributesComponent != null) {
            for (Attribute attribute : record.getAttributesList()) {
                attributesComponent.addAttribute(attribute.getName(), attribute.getValue());
            }
        }

        statusMessage.set("Editing " + record.getPvName()
                + " - saving replaces the entire record under this name");
        logger.debug("Loaded PV metadata record for editing: {}", record.getPvName());
    }

    /**
     * Resets the form, including the injected list components.
     */
    public void resetForm() {
        pvName.set("");
        description.set("");
        modifiedBy.set("");

        if (aliasesComponent != null) {
            aliasesComponent.clearTags();
        }
        if (tagsComponent != null) {
            tagsComponent.clearTags();
        }
        if (attributesComponent != null) {
            attributesComponent.clearAttributes();
        }

        statusMessage.set("Ready to save PV metadata");
        logger.debug("PV metadata form reset");
    }

}
