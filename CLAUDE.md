# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is dp-desktop-app, a JavaFX desktop GUI application that demonstrates the capabilities of the Machine Learning Data Platform (MLDP) gRPC services. The application supports data ingestion, query, annotation, and subscription through both in-process and remote gRPC communication.

## Build and Development Commands

### Build
```bash
mvn clean compile
mvn package
```

### Run Application
```bash
mvn javafx:run
```
Or run the shaded JAR:
```bash
java -jar target/dp-desktop-app-1.11.0-shaded.jar
```
The main class is `com.ospreydcs.dp.gui.DpDesktopApplication`.

### Maven Profiles
- `dev` (default): Development profile
- `ci`: Continuous integration profile  
- `release`: Release profile

### Dependencies
This project depends on:
- `dp-grpc` (v1.11.0): gRPC API definitions
- `dp-service` (v1.11.0): gRPC service implementations
- MongoDB drivers for data persistence
- JavaFX 21.0.2 for GUI framework
- BootstrapFX 0.4.0 for styling
- Log4j2 2.23.1 for logging
- Apache POI 5.3.0 for Excel export
- FastCSV 3.3.1 for CSV processing

## Architecture

### Core Components

**DpApplication** (`src/main/java/com/ospreydcs/dp/gui/DpApplication.java`)
- High-level application model that abstracts gRPC API invocations
- Manages in-process service ecosystem and API client
- Entry point for GUI interactions with backend services
- Implements provider registration and data generation/ingestion workflows
- Stores state variables (providerId, providerName, time ranges, PV details) for cross-view usage

**InprocessServiceEcosystem** (`src/main/java/com/ospreydcs/dp/service/inprocess/InprocessServiceEcosystem.java`)
- Container for in-process gRPC service implementations
- Manages: IngestionService, QueryService, AnnotationService, IngestionStreamService
- Initializes MongoDB client interface (uses `dp-demo` database by default)
- Provides service channels for API client connections

### Service Layer
Located in `src/main/java/com/ospreydcs/dp/service/inprocess/`:
- `InprocessServiceBase.java`: Base class for service implementations
- `InprocessIngestionService.java`: Handles data ingestion
- `InprocessQueryService.java`: Handles data queries
- `InprocessAnnotationService.java`: Handles data annotation
- `InprocessIngestionStreamService.java`: Handles real-time data streams

### Design Patterns
- **MVVM**: Model-View-ViewModel pattern for UI organization
- **FXML**: Used for UI layout definitions
- **In-process gRPC**: Default communication model (remote gRPC planned)

### External Dependencies
The application integrates with external repositories:
- `~/dp.fork/dp-java/dp-service`: gRPC service implementations
- `~/dp.fork/dp-java/dp-grpc`: gRPC API definitions

## Planned Application Features

### Data Operations
- **Ingestion**: Generate random data, upload fixed data, import CSV files
- **Query**: Retrieve time-series data with tabular display
- **Annotation**: Add descriptive metadata to datasets
- **Export**: Export data to CSV/Excel formats
- **Visualization**: Data plotting with visualization library (TBD)
- **Subscriptions**: Real-time data event monitoring

### GUI Navigation Structure
```
File → Connection, Preferences, Exit
Ingest → Generate, Import (Fixed and Subscribe removed)
Explore → Data, PV Metadata, Provider Metadata, Datasets, Annotations
```

**Menu Item Logic:**
- **Generate**: Conditionally enabled (disabled for remote production connections to prevent fake data contamination)
- **Import**: Always enabled (real data import is safe for all environments)
- **Data/Metadata menus**: Enabled after data ingestion

## Development Guidelines

### Technology Stack
- Java 21 (source and target compatibility)
- Maven for build management
- JavaFX for GUI framework
- BootstrapFX for styling
- Log4j2 for logging
- MongoDB for data persistence

### Key Service Interactions
1. Initialize `InprocessServiceEcosystem` to start all gRPC services
2. Create `ApiClient` with service channels from ecosystem
3. Use `DpApplication` wrapper methods to invoke gRPC APIs
4. Handle MongoDB connections through the shared `MongoInterface`

### Current Implementation Status
- ✅ In-process service ecosystem container
- ✅ API client structure (IngestionClient and QueryClient implemented)
- ✅ High-level application model with provider registration and data generation
- ✅ JavaFX/FXML GUI framework with BootstrapFX styling
- ✅ Main window with navigation and welcome screen
- ✅ Data generation UI with form validation and PV management
- ✅ Random walk data generation algorithm
- ✅ Provider registration and data ingestion workflows
- ✅ Data explore UI with PV search functionality and tabular results display
- ✅ PV search panel supporting search by name list and pattern matching
- ✅ Query results table with dynamic column expansion
- ✅ Interactive LineChart with time-series visualization and mouse tracking tooltips
- ✅ Dynamic data sampling and NumberAxis-based chart scaling
- ✅ Global state synchronization between views for query parameters
- ✅ Dataset Builder with data block management and save functionality
- ✅ Cross-tab data transfer between Data Explorer and Dataset Builder
- ✅ Annotation Builder UI with dataset targeting, tags, attributes, and save functionality
- ✅ Cross-tab data transfer between Dataset Builder and Annotation Builder
- ✅ Reusable TagsListComponent and AttributesListComponent for form inputs
- ✅ Reusable ProviderDetailsComponent and RequestDetailsComponent for section modularity
- ✅ Component-based architecture for data-generation view enabling code reuse
- ✅ Data import view with Excel file processing and DataImportUtility integration
- ✅ Calculations section with multi-sheet Excel import functionality
- ✅ Data export functionality (CSV, XLSX, HDF5 formats) with automatic file opening

## GUI Architecture

### MVVM Implementation
The application follows the Model-View-ViewModel pattern:

**Controllers** (`src/main/java/com/ospreydcs/dp/gui/*Controller.java`)
- Handle FXML UI binding and user interactions
- Delegate business logic to ViewModels
- Example: `DataGenerationController`, `DataExploreController`, `DataImportController`, `MainController`

**ViewModels** (`src/main/java/com/ospreydcs/dp/gui/*ViewModel.java`)
- Contain UI state and business logic
- Use JavaFX properties for data binding
- Example: `DataGenerationViewModel`, `DataExploreViewModel`, `DataImportViewModel`, `MainViewModel`

**Views** (`src/main/resources/fxml/*.fxml`)
- FXML layout definitions
- Styled with BootstrapFX and custom CSS
- Example: `data-generation.fxml`, `data-explore.fxml`, `data-import.fxml`, `main-window.fxml`

### Data Generation Workflow (Implemented)
1. **Provider Registration**: Users fill provider details (name, description, tags, attributes)
2. **Request Configuration**: Set time range, tags, attributes, event name
3. **PV Definition**: Always-visible form for adding process variables with automatic submission
4. **PV Form Auto-Submission**: Automatically adds PVs when all fields are filled and user presses Enter or moves focus
5. **Focus Management**: Returns focus to PV Name field after successful addition for rapid multi-PV entry
6. **Form Validation**: Ensures all required fields are filled and time ranges are valid
7. **Data Generation**: Uses random walk algorithm to generate time-series data
8. **Ingestion**: Calls gRPC API to ingest generated data into MongoDB

### Data Import Workflow (Implemented)
1. **Provider Configuration**: Uses reusable ProviderDetailsComponent for name, description, tags, attributes
2. **Request Configuration**: Uses reusable RequestDetailsComponent for tags, attributes, event name
3. **File Selection**: Excel file chooser dialog (.xlsx/.xls formats) with validation
4. **Data Processing**: Integration with DataImportUtility.importXlsxData() from dp-service
5. **Frame Display**: Shows imported DataFrameResult objects with human-readable format
6. **Reset Logic**: Clears import details when selecting new files (section 13.1.9)
7. **Error Handling**: Comprehensive error handling with status bar feedback
8. **Navigation**: Always-enabled Import menu item (unlike conditional Generate menu)

### Data Explore Workflow (Implemented)
1. **Data Explorer Tools**: Collapsible panel with Query Editor, Dataset Builder, and Annotation Builder tabs
2. **PV Selection**: Add PV names manually or via search panel with pattern matching
3. **Time Range Selection**: Set query begin/end times with date pickers and time spinners
4. **PV Search Panel**: Search existing PVs by name list or pattern matching
5. **Query Execution**: Execute query and display results in Data Viewer section
6. **Data Viewer**: Collapsible section with dynamic table and interactive chart
7. **Results Display**: Dynamic table with columns for timestamp and selected PVs  
8. **Chart Visualization**: TabPane with Table and Chart views, LineChart with NumberAxis scaling
9. **Interactive Features**: Mouse tracking tooltips, dynamic data sampling for performance

### Dataset Builder Workflow (Implemented)
1. **Dataset Configuration**: Enter dataset name (required), description (optional), and auto-generated ID field
2. **Data Block Management**: Collect DataBlockDetail objects from Data Explorer using "Add to Dataset" button
3. **Data Block Operations**: Remove selected data blocks or view their details in Data Explorer
4. **Cross-Tab Navigation**: "View Data" button populates Data Explorer fields and switches tabs
5. **Dataset Persistence**: Save button validates inputs and calls DpApplication.saveDataSet() API
6. **Validation & Feedback**: Real-time validation with status messages and button enable/disable logic
7. **State Management**: Preserve dataset details across save operations and tab switches

### Annotation Builder Workflow (Implemented)
1. **Annotation Configuration**: Enter annotation name (required), comment, and event name (optional)
2. **Target Dataset Management**: Add datasets from Dataset Builder using "Add to Annotation" button
3. **Dataset Operations**: Remove selected target datasets from annotation
4. **Tags & Attributes**: Use reusable components for free-form tag and key-value attribute entry
5. **Calculations Import**: Import user-defined calculations from Excel files (multi-sheet support)
6. **Cross-Tab Navigation**: Automatic tab switching when adding datasets from Dataset Builder
7. **Annotation Persistence**: Save button validates inputs and calls DpApplication.saveAnnotation() API
8. **Validation & Feedback**: Real-time validation requiring both name and target datasets
9. **State Management**: Preserve annotation details and auto-generated ID after successful saves

### Calculations Import Workflow (Implemented)
1. **Excel File Selection**: File chooser dialog supporting .xlsx and .xls formats
2. **Multi-Sheet Processing**: Automatically imports all sheets as separate DataFrameDetails
3. **Data Validation**: Validates minimum column requirements (seconds, nanoseconds, data columns)
4. **Timestamp Format**: Expects first two columns as epoch seconds and nanoseconds
5. **Data Frame Creation**: Creates DataFrameDetails objects with protobuf DataColumn structures
6. **Error Handling**: Graceful handling of invalid sheets while processing valid ones
7. **List Management**: View, remove, and manage imported calculation data frames

### Data Export Workflow (Implemented)
1. **Dataset Requirement**: Dataset must be saved first to obtain a valid dataset ID
2. **Export Formats**: Support for CSV, XLSX, and HDF5 output formats
3. **Format Selection**: "Other actions..." ComboBox in Dataset Builder provides export options
4. **Export Processing**: Background API call to DpApplication.exportData() method
5. **File Generation**: Service creates export file and returns file path
6. **Automatic Opening**: Exported files are automatically opened with native applications
7. **Status Feedback**: Real-time status updates during export process and completion confirmation

### Key UI Components
- **Spinner Binding**: Custom binding logic for time spinners to avoid JavaFX binding issues
- **Dynamic ComboBoxes**: Attribute value combos populate based on selected keys
- **Context Menus**: Right-click to remove items from lists
- **Form Validation**: Real-time validation with status messages
- **Responsive Layout**: GridPane with proper column constraints for label visibility
- **Interactive Charts**: LineChart with NumberAxis, mouse tracking tooltips, dynamic data sampling
- **TabPane Architecture**: Multi-level TabPane structure with Data Explorer Tools and Data Viewer (Table/Chart views)
- **Cross-Tab Data Transfer**: "Add to Dataset" and "View Data" buttons for seamless data flow between tabs
- **Selection-Based UI**: ListView selections drive button enable/disable state using property binding
- **Auto-Submission Forms**: PV entry form automatically submits on Enter/focus loss and returns focus for rapid data entry

## Data Model

### PvDetail (`src/main/java/com/ospreydcs/dp/gui/model/PvDetail.java`)
Represents process variable configuration:
- PV name, data type (integer/float)
- Sample period in milliseconds
- Initial value and maximum step magnitude for random walk

### DataBlockDetail (`src/main/java/com/ospreydcs/dp/gui/model/DataBlockDetail.java`)
Represents a data block in the Dataset Builder:
- List of PV names (List<String>)
- Begin and end time (Instant objects)
- Human-readable toString() format: "pv-1, pv-2, pv-3: 2025-08-15 11:03:00 -> 2025-08-15 11:05:00"
- Used for dataset composition and cross-tab data transfer

### DataSetDetail (`src/main/java/com/ospreydcs/dp/gui/model/DataSetDetail.java`)
Represents a dataset in the Annotation Builder:
- Dataset ID (String, auto-generated on save)
- Dataset name, description (String)
- List of data blocks (List<DataBlockDetail>)
- Human-readable toString() format: "ID: [dataset-id] - Dataset name - Description snippet - First data block"
- Used for annotation targeting and cross-tab data transfer

### CalculationsDetails (`src/main/java/com/ospreydcs/dp/gui/model/CalculationsDetails.java`)
Container for calculation data imported from Excel files:
- ID (String, for calculations identification)
- List of data frames (List<DataFrameDetails>)
- Used in Annotation Builder for calculations management

### DataFrameDetails (`src/main/java/com/ospreydcs/dp/gui/model/DataFrameDetails.java`)
Represents individual calculation frames from Excel import:
- Name (String, typically sheet name from Excel)
- Timestamps (List<Timestamp>, protobuf format)
- Data columns (List<DataColumn>, protobuf format)
- Human-readable toString() format: "Frame name - Column1, Column2, Column3..."
- Created from multi-sheet Excel import using shared DataImportUtility

### Global State Management
`DpApplication` maintains cross-view state with automatic synchronization:
- Provider ID and name after registration
- Data time ranges (begin/end instants) - synced from query UI changes
- List of configured PV details - synced from query PV selections
- Real-time listeners in DataExploreController update global state when UI changes
- Global state is restored when navigating between views
- Used for data generation, query operations, and future annotation/export features

**Critical Implementation Details:**
- Timezone handling uses `java.time.ZoneId.systemDefault()` for consistent UI ↔ global state conversion
- Spinner value commitment via `commitValue()` before reading values to handle JavaFX uncommitted edits
- Initialization order: restore UI from global state BEFORE injecting into ViewModels to prevent listener overwrites

## Shared Utilities Integration

### DataImportUtility
Located in `dp-service` dependency (`~/dp.fork/dp-java/dp-service`):
- **Multi-Sheet Excel Import**: `DataImportUtility.importXlsxData(String filePath)`
- **Input Format**: First two columns must be epoch seconds and nanoseconds
- **Returns**: `DataImportResult` with list of `DataFrameResult` objects (one per sheet)
- **Error Handling**: Skips invalid sheets/rows, continues processing valid data
- **Shared Usage**: Used by Calculations import, Data Import view, and future PV ingestion features
- **Dependencies**: Requires updated `dp-service` to be installed to local Maven repository
- **Integration Pattern**: Import `com.ospreydcs.dp.client.result.DataImportResult` for result handling

### Dependency Updates
When modifying shared utilities in `dp-service`:
```bash
cd ~/dp.fork/dp-java/dp-service
mvn clean install -DskipTests
cd ~/dp.fork/dp-java/dp-desktop-app
mvn clean compile
```

## MongoDB Integration
- Default database: `dp-demo`
- Managed through `InprocessServiceEcosystem`
- Data persistence handled by gRPC service layer
- MongoDB drivers: sync, reactive streams, core, and BSON

## Debugging and Logging
- Log4j2 configuration in `src/main/resources/log4j2.xml`
- Change log level to DEBUG for detailed component debugging
- Key logger names: `com.ospreydcs.dp.gui.*` for UI components
- JavaFX UI thread operations logged with method entry/exit points
- Global state synchronization extensively logged for troubleshooting

## Common Development Patterns

### Adding New Views
1. Create FXML layout in `src/main/resources/fxml/`
2. Create Controller class extending JavaFX controller patterns
3. Create ViewModel class with JavaFX properties for data binding
4. Add navigation integration in MainController
5. Inject DpApplication dependency for service access
6. Follow initialization order: UI restoration before ViewModel injection

### Creating Reusable Components
1. **Component Structure**: Create both Java class and FXML file in `src/main/resources/fxml/components/`
2. **Extend VBox**: Component class extends VBox and implements Initializable
3. **FXML Loading**: Use FXMLLoader in constructor to load component's FXML and copy properties
4. **Property Binding**: Create StringProperty fields for external binding (e.g., `eventNameProperty()`)
5. **Data Access Methods**: Provide getter/setter methods for embedded components (e.g., `getProviderTags()`)
6. **Component APIs**: Always access data through component methods, not parent ViewModel
7. **Lifecycle Methods**: Provide clear() methods to reset component state
8. **Controller Integration**: Update parent controllers to bind to component properties instead of direct FXML fields

### Chart Integration
- Use NumberAxis instead of CategoryAxis for time-series data
- Implement `calculateOptimalTickUnit()` for proper axis scaling
- For performance with large datasets, disable symbols: `setCreateSymbols(false)`
- Use mouse tracking tooltips instead of per-point tooltips for better performance
- Implement dynamic data sampling for datasets > 1000 points

### JavaFX Time Handling
- Always use `java.time.ZoneId.systemDefault()` for timezone conversions
- Call `spinner.commitValue()` before reading values to handle uncommitted edits
- Use initialization flags to prevent listeners from firing during UI setup

### API Integration Patterns
- Always check `apiResult.resultStatus.isError` before processing API responses
- Use `apiResult.resultStatus.msg` (not `.message`) for error messages
- Handle null responses and exceptional results from gRPC services
- Status messages should provide immediate user feedback during API operations

### TabPane and Multi-View Coordination
- Use `TabPane.getSelectionModel().select(tab)` for programmatic tab switching
- Implement populateFromDataBlock() pattern for cross-view data transfer
- ListView selection binding: `listView.getSelectionModel().selectedItemProperty()` for button states
- Use shared ViewModel methods for coordinating data between tabs

### Reusable UI Components
**TagsListComponent** (`src/main/java/com/ospreydcs/dp/gui/component/TagsListComponent.java`)
- Free-form tag entry with add/remove functionality
- Stores tags as ObservableList<String>
- Access data via `getTags()` method, not through parent ViewModel

**AttributesListComponent** (`src/main/java/com/ospreydcs/dp/gui/component/AttributesListComponent.java`)
- Key-value attribute entry with add/remove functionality
- Stores attributes as "key=value" strings in ObservableList<String>
- Access data via `getAttributes()` method, not through parent ViewModel
- Convert to Map<String,String> using `getKeyFromAttribute()` and `getValueFromAttribute()` static methods

**ProviderDetailsComponent** (`src/main/java/com/ospreydcs/dp/gui/component/ProviderDetailsComponent.java`)
- Reusable component for Provider Details section
- Contains provider name, description, tags, and attributes
- Uses embedded TagsListComponent and AttributesListComponent
- Property binding: `providerNameProperty()`, `providerDescriptionProperty()`
- Data access: `getProviderTags()`, `getProviderAttributes()`
- Lifecycle method: `clearProviderDetails()`

**RequestDetailsComponent** (`src/main/java/com/ospreydcs/dp/gui/component/RequestDetailsComponent.java`)
- Reusable component for Request Details section
- Contains request tags, attributes, and event name field
- Uses embedded TagsListComponent and AttributesListComponent
- Property binding: `eventNameProperty()`
- Data access: `getRequestTags()`, `getRequestAttributes()`
- Lifecycle method: `clearRequestDetails()`

**Critical Integration Pattern:**
When using reusable components, get data from component instances directly:
```java
// Correct - get from component instances
var tags = tagsComponent.getTags();
var attributes = attributesComponent.getAttributes();
var providerTags = providerDetailsComponent.getProviderTags();
var eventName = requestDetailsComponent.getEventName();

// Incorrect - parent ViewModels return empty lists
var tags = viewModel.getTags(); // Empty!
var attributes = viewModel.getAttributes(); // Empty!
```

### Excel Import Integration
**Using DataImportUtility for multi-sheet Excel processing:**
```java
// Import Excel data with error handling
DataImportResult importResult = DataImportUtility.importXlsxData(selectedFile.getAbsolutePath());
if (!importResult.resultStatus.isError) {
    List<DataFrameDetails> importedFrames = new ArrayList<>();
    for (DataImportResult.DataFrameResult frameResult : importResult.dataFrames) {
        DataFrameDetails frame = new DataFrameDetails(
            frameResult.sheetName, 
            frameResult.timestamps, 
            frameResult.columns
        );
        importedFrames.add(frame);
    }
    // Add to UI model
    viewModel.getCalculationsDataFrames().addAll(importedFrames);
}
```

**Excel File Format Requirements:**
- Column 0: Epoch seconds (long)
- Column 1: Nanoseconds (long) 
- Columns 2+: Data values (numeric, string, or boolean)
- Headers in row 0 for all columns
- Minimum 3 columns required per sheet

### Auto-Submission Form Pattern
**Implementing automatic form submission for rapid data entry:**
```java
// Set up auto-submission handlers in Controller
private void setupPvFormAutoSubmission() {
    // Auto-submit when user presses Enter in any text field
    pvNameField.setOnAction(e -> attemptPvFormSubmission());
    pvInitialValueField.setOnAction(e -> attemptPvFormSubmission());
    pvMaxStepField.setOnAction(e -> attemptPvFormSubmission());
    
    // Auto-submit when user moves focus away from the last required field
    pvMaxStepField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
        if (wasFocused && !isFocused) { // Lost focus
            attemptPvFormSubmission();
        }
    });
}

private void attemptPvFormSubmission() {
    // Only auto-submit if all required fields are filled
    if (/* all fields valid */) {
        viewModel.addCurrentPvDetail();
        
        // Return focus to first field for next entry
        if (pvNameField.getText() == null || pvNameField.getText().trim().isEmpty()) {
            javafx.application.Platform.runLater(() -> {
                pvNameField.requestFocus();
            });
        }
    }
}
```

**Key principles:**
- Always-visible forms eliminate button clicks
- Auto-submission on Enter/focus-loss reduces user actions
- Focus management enables rapid sequential entry
- Validation prevents invalid submissions