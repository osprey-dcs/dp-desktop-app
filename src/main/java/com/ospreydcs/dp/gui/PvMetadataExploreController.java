package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.component.HyperlinkListTableCell;
import com.ospreydcs.dp.gui.model.PvMetadataTableRow;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the PV metadata explore view.
 *
 * <p>{@code initialize()} stays dependency-free so {@code ViewLoadSmokeTest} can load it from the
 * classpath; {@code DpApplication} / {@code Stage} / {@code MainController} arrive via setters.
 */
public class PvMetadataExploreController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    @FXML private TextField pvNameField;
    @FXML private ComboBox<PvMetadataExploreViewModel.MatchMode> pvNameMatchModeCombo;
    @FXML private TextField aliasField;
    @FXML private ComboBox<PvMetadataExploreViewModel.MatchMode> aliasMatchModeCombo;
    @FXML private TextField tagsField;
    @FXML private TextField attributeKeyField;
    @FXML private TextField attributeValueField;

    @FXML private Button searchButton;
    @FXML private Button clearButton;
    @FXML private ProgressIndicator searchProgressIndicator;
    @FXML private Label searchStatusLabel;
    @FXML private Label resultCountLabel;
    @FXML private Label resultsStatusLabel;

    @FXML private TableView<PvMetadataTableRow> resultsTable;
    @FXML private TableColumn<PvMetadataTableRow, String> pvNameColumn;
    @FXML private TableColumn<PvMetadataTableRow, String> aliasesColumn;
    @FXML private TableColumn<PvMetadataTableRow, String> tagsColumn;
    @FXML private TableColumn<PvMetadataTableRow, String> attributesColumn;
    @FXML private TableColumn<PvMetadataTableRow, String> descriptionColumn;
    @FXML private TableColumn<PvMetadataTableRow, String> modifiedByColumn;
    @FXML private TableColumn<PvMetadataTableRow, String> updatedTimeColumn;

    private final PvMetadataExploreViewModel viewModel = new PvMetadataExploreViewModel();

    private DpApplication dpApplication;
    private Stage primaryStage;
    private MainController mainController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupMatchModeCombos();
        setupTableColumns();
        bindUIToViewModel();
        logger.debug("PvMetadataExploreController initialized");
    }

    private void setupMatchModeCombos() {
        pvNameMatchModeCombo.setItems(
                FXCollections.observableArrayList(PvMetadataExploreViewModel.MatchMode.values()));
        pvNameMatchModeCombo.getSelectionModel().select(PvMetadataExploreViewModel.MatchMode.CONTAINS);

        aliasMatchModeCombo.setItems(
                FXCollections.observableArrayList(PvMetadataExploreViewModel.MatchMode.values()));
        aliasMatchModeCombo.getSelectionModel().select(PvMetadataExploreViewModel.MatchMode.CONTAINS);
    }

    /**
     * Wires the columns.  The plain columns resolve their row property reflectively by name, so they
     * use the row's {@code PROPERTY_*} constants — a stale string yields a silently blank column
     * rather than a compile error.
     */
    private void setupTableColumns() {
        // The PV name links to the editor.  onClick receives the ROW, not the displayed string,
        // which is what lets the editor be loaded with the canonical record rather than re-resolved
        // from text -- the alias trap this view exists to avoid.
        pvNameColumn.setCellValueFactory(new PropertyValueFactory<>(PvMetadataTableRow.PROPERTY_PV_NAME));
        pvNameColumn.setCellFactory(HyperlinkListTableCell.forSingleValue(
                PvMetadataTableRow::getPvName,
                (row, pvName) -> viewModel.editPvMetadata(row)));

        // Each alias is its own link, searching for that alias.  The values come from the row's
        // list, never from re-splitting the rendered string.
        aliasesColumn.setCellValueFactory(new PropertyValueFactory<>(PvMetadataTableRow.PROPERTY_ALIASES));
        aliasesColumn.setCellFactory(HyperlinkListTableCell.forValues(
                PvMetadataTableRow::getAliasesList,
                (row, alias) -> searchForAlias(alias)));

        tagsColumn.setCellValueFactory(new PropertyValueFactory<>(PvMetadataTableRow.PROPERTY_TAGS));
        attributesColumn.setCellValueFactory(new PropertyValueFactory<>(PvMetadataTableRow.PROPERTY_ATTRIBUTES));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>(PvMetadataTableRow.PROPERTY_DESCRIPTION));
        modifiedByColumn.setCellValueFactory(new PropertyValueFactory<>(PvMetadataTableRow.PROPERTY_MODIFIED_BY));
        updatedTimeColumn.setCellValueFactory(new PropertyValueFactory<>(PvMetadataTableRow.PROPERTY_UPDATED_TIME));
    }

    private void bindUIToViewModel() {
        pvNameField.textProperty().bindBidirectional(viewModel.pvNameTextProperty());
        aliasField.textProperty().bindBidirectional(viewModel.aliasTextProperty());
        tagsField.textProperty().bindBidirectional(viewModel.tagsTextProperty());
        attributeKeyField.textProperty().bindBidirectional(viewModel.attributeKeyProperty());
        attributeValueField.textProperty().bindBidirectional(viewModel.attributeValueProperty());

        pvNameMatchModeCombo.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> viewModel.setPvNameMatchMode(selected));
        aliasMatchModeCombo.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> viewModel.setAliasMatchMode(selected));

        searchStatusLabel.textProperty().bind(viewModel.searchStatusMessageProperty());
        resultCountLabel.textProperty().bind(viewModel.resultCountMessageProperty());
        resultsStatusLabel.textProperty().bind(viewModel.statusMessageProperty());

        searchProgressIndicator.visibleProperty().bind(viewModel.searchInProgressProperty());
        searchButton.disableProperty().bind(viewModel.searchInProgressProperty());

        resultsTable.setItems(viewModel.getSearchResults());
    }

    /** Re-runs the search scoped to one alias, as an exact match. */
    private void searchForAlias(String alias) {
        pvNameField.clear();
        aliasField.setText(alias);
        aliasMatchModeCombo.getSelectionModel().select(PvMetadataExploreViewModel.MatchMode.EXACT);
        viewModel.executeSearch();
    }

    @FXML
    private void onSearch() {
        viewModel.executeSearch();
    }

    @FXML
    private void onClear() {
        viewModel.clearSearch();
        pvNameMatchModeCombo.getSelectionModel().select(PvMetadataExploreViewModel.MatchMode.CONTAINS);
        aliasMatchModeCombo.getSelectionModel().select(PvMetadataExploreViewModel.MatchMode.CONTAINS);
    }

    // Dependency injection methods

    public void setDpApplication(DpApplication dpApplication) {
        this.dpApplication = dpApplication;
        viewModel.setDpApplication(dpApplication);
        logger.debug("DpApplication injected into PvMetadataExploreController");
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        viewModel.setMainController(mainController);
    }

    PvMetadataExploreViewModel getViewModel() {
        return viewModel;
    }
}
