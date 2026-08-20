package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.client.result.SavePvMetadataApiResult;
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

        final Task<SavePvMetadataApiResult> saveTask = new Task<>() {
            @Override
            protected SavePvMetadataApiResult call() {
                return dpApplication.savePvMetadata(
                        pvNameValue, aliases, tags, attributeMap, descriptionValue, modifiedByValue);
            }
        };

        saveTask.setOnSucceeded(e -> Platform.runLater(() -> {
            isSaving.set(false);
            final SavePvMetadataApiResult apiResult = saveTask.getValue();

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
