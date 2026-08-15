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
 * Reusable component for managing a list of key-value attributes with add/remove functionality.
 * Supports free-form text entry (unconstrained mode).
 * Attributes are stored as "key=value" strings.
 */
public class AttributesListComponent extends VBox implements Initializable {

    private static final Logger logger = LogManager.getLogger();

    // FXML components
    @FXML private Label componentLabel;
    @FXML private TextField keyInput;
    @FXML private TextField valueInput;
    @FXML private Button addAttributeButton;
    @FXML private ListView<String> attributesList;

    // Properties for external binding and configuration
    private final ObservableList<String> attributes = FXCollections.observableArrayList();
    private final StringProperty labelText = new SimpleStringProperty("Attributes:");
    private final BooleanProperty componentDisabled = new SimpleBooleanProperty(false);

    public AttributesListComponent() {
        // Load FXML and set this as controller only (not root)
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/components/attributes-list-component.fxml"));
        fxmlLoader.setController(this);

        try {
            VBox root = fxmlLoader.load();
            this.getChildren().setAll(root.getChildren());
            this.setSpacing(root.getSpacing());
        } catch (IOException exception) {
            logger.error("Failed to load AttributesListComponent FXML", exception);
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.debug("AttributesListComponent initialized");

        // Bind attributes list to ListView
        attributesList.setItems(attributes);

        // Bind label text property
        componentLabel.textProperty().bind(labelText);

        // Bind disabled state
        disableProperty().bind(componentDisabled);

        // Set up context menu for removing attributes
        setupContextMenu();

        // Set up button event handler
        addAttributeButton.setOnAction(e -> onAddAttribute());

        // Enable Enter key to add attribute (from either input field)
        keyInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onAddAttribute();
            }
        });
        
        valueInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onAddAttribute();
            }
        });

        logger.debug("AttributesListComponent setup complete");
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem removeItem = new MenuItem("Remove");
        removeItem.setOnAction(e -> {
            String selectedAttribute = attributesList.getSelectionModel().getSelectedItem();
            if (selectedAttribute != null) {
                removeAttribute(selectedAttribute);
            }
        });
        contextMenu.getItems().add(removeItem);

        attributesList.setContextMenu(contextMenu);

        // Only show context menu when there's a selection
        attributesList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            contextMenu.getItems().get(0).setDisable(newVal == null);
        });
    }

    @FXML
    private void onAddAttribute() {
        String key = keyInput.getText();
        String value = valueInput.getText();
        
        if (key != null && !key.trim().isEmpty() && value != null && !value.trim().isEmpty()) {
            addAttribute(key.trim(), value.trim());
            keyInput.clear();
            valueInput.clear();
        }
    }

    // Public API methods

    /**
     * Adds a key-value pair as an attribute if it's not already present.
     * Stored as "key=value" string format.
     */
    public void addAttribute(String key, String value) {
        if (key != null && !key.trim().isEmpty() && value != null && !value.trim().isEmpty()) {
            String attribute = key.trim() + "=" + value.trim();
            if (!attributes.contains(attribute)) {
                attributes.add(attribute);
                logger.debug("Added attribute: {}", attribute);
            }
        }
    }

    /**
     * Removes an attribute from the list.
     */
    public void removeAttribute(String attribute) {
        if (attributes.remove(attribute)) {
            logger.debug("Removed attribute: {}", attribute);
        }
    }

    /**
     * Clears all attributes from the list.
     */
    public void clearAttributes() {
        int count = attributes.size();
        attributes.clear();
        logger.debug("Cleared {} attributes", count);
    }

    /**
     * Parses a "key=value" string and returns the key part.
     */
    public static String getKeyFromAttribute(String attribute) {
        if (attribute != null && attribute.contains("=")) {
            return attribute.substring(0, attribute.indexOf("="));
        }
        return attribute;
    }

    /**
     * Parses a "key=value" string and returns the value part.
     */
    public static String getValueFromAttribute(String attribute) {
        if (attribute != null && attribute.contains("=")) {
            return attribute.substring(attribute.indexOf("=") + 1);
        }
        return "";
    }

    // Property accessors for external binding

    /**
     * Returns the ObservableList of attributes for binding to parent components.
     */
    public ObservableList<String> getAttributes() {
        return attributes;
    }

    /**
     * Sets the initial attributes list (replaces existing attributes).
     *
     * Applies the same normalization as addAttribute(): entries are expected in "key=value" form,
     * and those that are null, lack a separator, or parse to a blank key are dropped.  Key and value
     * are trimmed and duplicates collapsed, so the list is guaranteed to hold only parseable
     * attributes regardless of how it was populated.
     */
    public void setAttributes(ObservableList<String> attributes) {
        this.attributes.setAll(normalizeAttributes(attributes));
    }

    /**
     * Drops entries that do not parse to a non-blank key, trims key and value, and removes
     * duplicates, preserving order.
     */
    private List<String> normalizeAttributes(List<String> sourceAttributes) {
        final List<String> normalized = new ArrayList<>();
        if (sourceAttributes == null) {
            return normalized;
        }
        for (String attribute : sourceAttributes) {
            if (attribute == null || !attribute.contains("=")) {
                logger.warn("ignoring attribute not in key=value form: {}", attribute);
                continue;
            }
            final String key = getKeyFromAttribute(attribute).trim();
            if (key.isEmpty()) {
                logger.warn("ignoring attribute with blank key: {}", attribute);
                continue;
            }
            final String normalizedAttribute = key + "=" + getValueFromAttribute(attribute).trim();
            if (!normalized.contains(normalizedAttribute)) {
                normalized.add(normalizedAttribute);
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