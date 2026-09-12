package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.component.HyperlinkListTableCell;
import com.ospreydcs.dp.gui.component.QueryPvsComponent;
import com.ospreydcs.dp.gui.model.ProviderInfoTableRow;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;

public class ProviderExploreController implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    // FXML components - Query Form
    @FXML private TextField providerIdField;
    @FXML private TextField nameDescriptionField;
    @FXML private TextField tagValueField;
    @FXML private TextField attributeKeyField;
    @FXML private TextField attributeValueField;
    @FXML private Button searchButton;
    @FXML private Label searchStatusLabel;

    // FXML components - Results
    @FXML private TableView<ProviderInfoTableRow> resultsTable;
    @FXML private TableColumn<ProviderInfoTableRow, String> idColumn;
    @FXML private TableColumn<ProviderInfoTableRow, String> nameColumn;
    @FXML private TableColumn<ProviderInfoTableRow, String> descriptionColumn;
    @FXML private TableColumn<ProviderInfoTableRow, String> tagsColumn;
    @FXML private TableColumn<ProviderInfoTableRow, String> attributesColumn;
    @FXML private TableColumn<ProviderInfoTableRow, String> pvNamesColumn;
    @FXML private TableColumn<ProviderInfoTableRow, String> numBucketsColumn;
    @FXML private Label resultCountLabel;
    @FXML private Label resultsStatusLabel;
    @FXML private ProgressIndicator searchProgressIndicator;

    // FXML components - Container for QueryPvsComponent
    @FXML private VBox queryPvsContainer;

    // Dependencies
    private DpApplication dpApplication;
    private Stage primaryStage;
    private MainController mainController;
    private ProviderExploreViewModel viewModel;
    private QueryPvsComponent queryPvsComponent;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("ProviderExploreController initializing...");

        // Initialize ViewModel
        viewModel = new ProviderExploreViewModel();
        
        // Set up table columns
        setupTableColumns();
        
        logger.debug("ProviderExploreController initialized");
    }

    private void setupTableColumns() {
        // Set up standard columns with property binding
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        tagsColumn.setCellValueFactory(new PropertyValueFactory<>("tags"));
        attributesColumn.setCellValueFactory(new PropertyValueFactory<>("attributes"));
        numBucketsColumn.setCellValueFactory(new PropertyValueFactory<>("numBuckets"));
        
        // Set up PV Names column with hyperlinks
        pvNamesColumn.setCellValueFactory(new PropertyValueFactory<>("pvNames"));
        pvNamesColumn.setCellFactory(HyperlinkListTableCell.forValues(
                ProviderInfoTableRow::getPvNamesList,
                (row, pvName) -> viewModel.addPvNameToQuery(pvName)));
        
        // Set table items to ViewModel results
        resultsTable.setItems(viewModel.getProviderResults());
        
        logger.debug("Table columns configured");
    }

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

    public void initializeView() {
        // Create and inject QueryPvsComponent
        queryPvsComponent = new QueryPvsComponent();
        queryPvsComponent.setDpApplication(dpApplication);
        queryPvsComponent.setMainController(mainController);
        
        // Add QueryPvsComponent to container
        queryPvsContainer.getChildren().clear();
        queryPvsContainer.getChildren().add(queryPvsComponent);
        
        // Inject component into ViewModel
        viewModel.setQueryPvsComponent(queryPvsComponent);
        
        // Bind UI to ViewModel
        bindUIToViewModel();
        
        logger.debug("ProviderExploreController view initialized");
    }

    private void bindUIToViewModel() {
        // Bind form fields
        providerIdField.textProperty().bindBidirectional(viewModel.providerIdProperty());
        nameDescriptionField.textProperty().bindBidirectional(viewModel.nameDescriptionProperty());
        tagValueField.textProperty().bindBidirectional(viewModel.tagValueProperty());
        attributeKeyField.textProperty().bindBidirectional(viewModel.attributeKeyProperty());
        attributeValueField.textProperty().bindBidirectional(viewModel.attributeValueProperty());
        
        // Bind status labels
        searchStatusLabel.textProperty().bind(viewModel.searchStatusMessageProperty());
        resultsStatusLabel.textProperty().bind(viewModel.statusMessageProperty());
        
        // Bind result count
        resultCountLabel.textProperty().bind(viewModel.resultCountMessageProperty());
        
        // Bind progress indicator
        searchProgressIndicator.visibleProperty().bind(viewModel.searchInProgressProperty());
        
        logger.debug("UI bound to ViewModel");
    }

    @FXML
    private void onSearch() {
        logger.debug("Search button clicked");
        viewModel.executeSearch();
    }

    /**
     * Execute provider search with a specific provider ID.
     * Used for navigation from other views (like pv-explore).
     */
    public void executeProviderSearch(String providerId) {
        logger.debug("Executing automatic provider search for provider ID: {}", providerId);
        
        // Set the provider ID in the form
        providerIdField.setText(providerId);
        
        // Clear other fields to ensure we're only searching by ID
        nameDescriptionField.clear();
        tagValueField.clear();
        attributeKeyField.clear();
        attributeValueField.clear();
        
        // Execute the search
        viewModel.executeSearch();
        
        logger.debug("Automatic provider search initiated for provider ID: {}", providerId);
    }
}