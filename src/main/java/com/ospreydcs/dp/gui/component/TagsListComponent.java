package com.ospreydcs.dp.gui.component;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Reusable component for managing a list of tags with add/remove functionality.
 * Supports free-form text entry (unconstrained mode).
 */
public class TagsListComponent extends VBox implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    // FXML components
    @FXML private Label componentLabel;
    @FXML private TextField tagInput;
    @FXML private Button addTagButton;
    @FXML private ListView<String> tagsList;

    // Properties for external binding and configuration
    private final ObservableList<String> tags = FXCollections.observableArrayList();
    private final StringProperty labelText = new SimpleStringProperty("Tags:");
    private final BooleanProperty componentDisabled = new SimpleBooleanProperty(false);

    public TagsListComponent() {
        // Load FXML and set this as controller only (not root)
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/components/tags-list-component.fxml"));
        fxmlLoader.setController(this);

        try {
            VBox root = fxmlLoader.load();
            this.getChildren().setAll(root.getChildren());
            this.setSpacing(root.getSpacing());
        } catch (IOException exception) {
            logger.error("Failed to load TagsListComponent FXML", exception);
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("TagsListComponent initialized");

        // Bind tags list to ListView
        tagsList.setItems(tags);

        // Bind label text property
        componentLabel.textProperty().bind(labelText);

        // Bind disabled state
        disableProperty().bind(componentDisabled);

        // Set up context menu for removing tags
        setupContextMenu();

        // Set up button event handler
        addTagButton.setOnAction(e -> onAddTag());

        // Enable Enter key to add tag
        tagInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onAddTag();
            }
        });

        logger.debug("TagsListComponent setup complete");
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem removeItem = new MenuItem("Remove");
        removeItem.setOnAction(e -> {
            String selectedTag = tagsList.getSelectionModel().getSelectedItem();
            if (selectedTag != null) {
                removeTag(selectedTag);
            }
        });
        contextMenu.getItems().add(removeItem);

        tagsList.setContextMenu(contextMenu);

        // Only show context menu when there's a selection
        tagsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            contextMenu.getItems().get(0).setDisable(newVal == null);
        });
    }

    @FXML
    private void onAddTag() {
        String tagText = tagInput.getText();
        if (tagText != null && !tagText.trim().isEmpty()) {
            addTag(tagText.trim());
            tagInput.clear();
        }
    }

    // Public API methods

    /**
     * Adds a tag to the list if it's not already present.
     */
    public void addTag(String tag) {
        if (tag != null && !tag.trim().isEmpty() && !tags.contains(tag.trim())) {
            tags.add(tag.trim());
            logger.debug("Added tag: {}", tag.trim());
        }
    }

    /**
     * Removes a tag from the list.
     */
    public void removeTag(String tag) {
        if (tags.remove(tag)) {
            logger.debug("Removed tag: {}", tag);
        }
    }

    /**
     * Clears all tags from the list.
     */
    public void clearTags() {
        int count = tags.size();
        tags.clear();
        logger.debug("Cleared {} tags", count);
    }

    // Property accessors for external binding

    /**
     * Returns the ObservableList of tags for binding to parent components.
     */
    public ObservableList<String> getTags() {
        return tags;
    }

    /**
     * Sets the initial tags list (replaces existing tags).
     *
     * Applies the same normalization as addTag(): null and blank entries are dropped, remaining
     * entries are trimmed, and duplicates are collapsed, so the list is guaranteed to hold only
     * usable tags regardless of how it was populated.
     */
    public void setTags(ObservableList<String> tags) {
        this.tags.setAll(normalizeTags(tags));
    }

    /**
     * Drops null/blank entries, trims the rest, and removes duplicates, preserving order.
     * Static (and package-private) so the normalization rules are unit-testable in isolation,
     * without the FX toolkit; instance-level behavior is covered separately by
     * TagsListComponentInstanceTest, which runs under FxToolkitSupport.
     */
    static List<String> normalizeTags(List<String> sourceTags) {
        final List<String> normalized = new ArrayList<>();
        if (sourceTags == null) {
            return normalized;
        }
        for (String tag : sourceTags) {
            if (tag == null || tag.trim().isEmpty()) {
                logger.warn("ignoring blank tag");
                continue;
            }
            final String trimmed = tag.trim();
            if (!normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    /**
     * Property for the component label text.
     */
    public StringProperty labelTextProperty() {
        return labelText;
    }

    public String getLabelText() {
        return labelText.get();
    }

    public void setLabelText(String labelText) {
        this.labelText.set(labelText);
    }

    /**
     * Property for disabling/enabling the entire component.
     */
    public BooleanProperty componentDisabledProperty() {
        return componentDisabled;
    }

    public boolean isComponentDisabled() {
        return componentDisabled.get();
    }

    public void setComponentDisabled(boolean disabled) {
        this.componentDisabled.set(disabled);
    }
}