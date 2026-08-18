package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.component.AttributesListComponent;
import com.ospreydcs.dp.gui.component.TagsListComponent;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the PV Metadata editor view, reached via the Metadata > PV menu item.
 */
public class PvMetadataController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    // FXML components - form fields
    @FXML private TextField pvNameField;
    @FXML private TextArea descriptionArea;
    @FXML private TextField modifiedByField;

    // FXML components - reusable list components
    @FXML private TagsListComponent aliasesComponent;
    @FXML private TagsListComponent tagsComponent;
    @FXML private AttributesListComponent attributesComponent;

    // FXML components - actions and status
    @FXML private Button saveButton;
    @FXML private Button resetButton;
    @FXML private ProgressIndicator saveProgressIndicator;
    @FXML private Label statusLabel;

    // Dependencies
    private DpApplication dpApplication;
    private Stage primaryStage;
    private MainController mainController;
    private PvMetadataViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("PvMetadataController initializing...");

        // Initialize ViewModel
        viewModel = new PvMetadataViewModel();

        // Inject component references into the ViewModel so it can read aliases, tags and
        // attributes directly from the components at save time
        viewModel.setAliasesComponent(aliasesComponent);
        viewModel.setTagsComponent(tagsComponent);
        viewModel.setAttributesComponent(attributesComponent);

        // Bind UI to ViewModel
        bindUIToViewModel();

        logger.debug("PvMetadataController initialized");
    }

    private void bindUIToViewModel() {
        // Bind form fields
        pvNameField.textProperty().bindBidirectional(viewModel.pvNameProperty());
        descriptionArea.textProperty().bindBidirectional(viewModel.descriptionProperty());
        modifiedByField.textProperty().bindBidirectional(viewModel.modifiedByProperty());

        // Bind status label and progress indicator
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        saveProgressIndicator.visibleProperty().bind(viewModel.isSavingProperty());

        // Save is disabled while PV Name is blank or a save is already in progress.  The blank
        // check trims first, so a whitespace-only name does not present an enabled button for
        // input the ViewModel would reject anyway.
        final BooleanBinding pvNameIsBlank = Bindings.createBooleanBinding(
                () -> pvNameField.getText() == null || pvNameField.getText().trim().isEmpty(),
                pvNameField.textProperty());
        saveButton.disableProperty().bind(pvNameIsBlank.or(viewModel.isSavingProperty()));

        // Reset is disabled while a save is in progress
        resetButton.disableProperty().bind(viewModel.isSavingProperty());

        logger.debug("UI bound to PvMetadataViewModel");
    }

    // Dependency injection methods

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        viewModel.setDpApplication(dpApplication);
        logger.debug("DpApplication injected");
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        logger.debug("Primary stage injected");
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        viewModel.setMainController(mainController);
        logger.debug("MainController injected");
    }

    // Action handlers

    @FXML
    private void onSave() {
        logger.debug("Save PV metadata action triggered");
        viewModel.savePvMetadata();
    }

    @FXML
    private void onReset() {
        logger.debug("Reset PV metadata form action triggered");
        viewModel.resetForm();
    }
}
