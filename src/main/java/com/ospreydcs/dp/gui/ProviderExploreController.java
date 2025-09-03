package com.ospreydcs.dp.gui;

import com.ospreydcs.dp.gui.component.QueryPvsComponent;
import com.ospreydcs.dp.gui.model.ProviderInfoTableRow;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
        pvNamesColumn.setCellFactory(column -> new PvNamesTableCell());
        
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
        resultCountLabel.textProperty().bind(viewModel.resultCountProperty().asString().concat(" provider(s)"));
        
        // Bind progress indicator
        searchProgressIndicator.visibleProperty().bind(viewModel.isSearchingProperty());
        
        logger.debug("UI bound to ViewModel");
    }

    @FXML
    private void onSearch() {
        logger.debug("Search button clicked");
        viewModel.executeSearch();
    }

    // Custom TableCell for PV Names column with hyperlinks
    private class PvNamesTableCell extends TableCell<ProviderInfoTableRow, String> {
        private HBox content;

        public PvNamesTableCell() {
            super();
            content = new HBox();
            content.setSpacing(5);
            content.setPadding(new Insets(2, 5, 2, 5));
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null || item.trim().isEmpty()) {
                setGraphic(null);
                setText(null);
            } else {
                content.getChildren().clear();
                
                // Get the table row to access individual PV names
                ProviderInfoTableRow tableRow = getTableRow().getItem();
                if (tableRow != null) {
                    boolean first = true;
                    for (String pvName : tableRow.getPvNamesList()) {
                        if (!first) {
                            Label separator = new Label(", ");
                            separator.getStyleClass().add("text-muted");
                            content.getChildren().add(separator);
                        }
                        
                        Hyperlink pvLink = new Hyperlink(pvName);
                        pvLink.getStyleClass().addAll("hyperlink-small");
                        pvLink.setOnAction(e -> {
                            viewModel.addPvNameToQuery(pvName);
                        });
                        
                        content.getChildren().add(pvLink);
                        first = false;
                    }
                    
                    // Ensure content can grow
                    HBox.setHgrow(content, Priority.ALWAYS);
                }
                
                setGraphic(content);
                setText(null);
            }
        }
    }
}