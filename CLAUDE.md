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
Ingest → Generate, Fixed, Import, Subscribe  
Query → Data, PV Metadata, Provider Metadata, Annotations
Tools → Annotate, Export, Upload, Console
```

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
- ✅ Data query UI with PV search functionality and tabular results display
- ✅ PV search panel supporting search by name list and pattern matching
- ✅ Query results table with dynamic column expansion
- ✅ Interactive LineChart with time-series visualization and mouse tracking tooltips
- ✅ Dynamic data sampling and NumberAxis-based chart scaling
- ✅ Global state synchronization between views for query parameters
- 🔄 Annotation UI (planned)
- 🔄 Data export functionality (planned)

## GUI Architecture

### MVVM Implementation
The application follows the Model-View-ViewModel pattern:

**Controllers** (`src/main/java/com/ospreydcs/dp/gui/*Controller.java`)
- Handle FXML UI binding and user interactions
- Delegate business logic to ViewModels
- Example: `DataGenerationController`, `MainController`

**ViewModels** (`src/main/java/com/ospreydcs/dp/gui/*ViewModel.java`)
- Contain UI state and business logic
- Use JavaFX properties for data binding
- Example: `DataGenerationViewModel`, `MainViewModel`

**Views** (`src/main/resources/fxml/*.fxml`)
- FXML layout definitions
- Styled with BootstrapFX and custom CSS
- Example: `data-generation.fxml`, `main-window.fxml`

### Data Generation Workflow (Implemented)
1. **Provider Registration**: Users fill provider details (name, description, tags, attributes)
2. **Request Configuration**: Set time range, tags, attributes, event name
3. **PV Definition**: Add process variables with data types, sample periods, initial values, step magnitudes
4. **Form Validation**: Ensures all required fields are filled and time ranges are valid
5. **Data Generation**: Uses random walk algorithm to generate time-series data
6. **Ingestion**: Calls gRPC API to ingest generated data into MongoDB

### Data Query Workflow (Implemented)
1. **Query Specification**: Collapsible panel for configuring query parameters
2. **PV Selection**: Add PV names manually or via search panel with pattern matching
3. **Time Range Selection**: Set query begin/end times with date pickers and time spinners
4. **PV Search Panel**: Search existing PVs by name list or pattern matching
5. **Query Execution**: Execute query and display results in expandable table and interactive chart
6. **Results Display**: Dynamic table with columns for timestamp and selected PVs
7. **Chart Visualization**: TabPane with Table and Chart views, LineChart with NumberAxis scaling
8. **Interactive Features**: Mouse tracking tooltips, dynamic data sampling for performance

### Key UI Components
- **Spinner Binding**: Custom binding logic for time spinners to avoid JavaFX binding issues
- **Dynamic ComboBoxes**: Attribute value combos populate based on selected keys
- **Context Menus**: Right-click to remove items from lists
- **Form Validation**: Real-time validation with status messages
- **Responsive Layout**: GridPane with proper column constraints for label visibility
- **Interactive Charts**: LineChart with NumberAxis, mouse tracking tooltips, dynamic data sampling
- **TabPane Results**: Table and Chart views with shared data model and synchronized updates

## Data Model

### PvDetail (`src/main/java/com/ospreydcs/dp/gui/model/PvDetail.java`)
Represents process variable configuration:
- PV name, data type (integer/float)
- Sample period in milliseconds
- Initial value and maximum step magnitude for random walk

### Global State Management
`DpApplication` maintains cross-view state with automatic synchronization:
- Provider ID and name after registration
- Data time ranges (begin/end instants) - synced from query UI changes
- List of configured PV details - synced from query PV selections
- Real-time listeners in DataQueryController update global state when UI changes
- Global state is restored when navigating between views
- Used for data generation, query operations, and future annotation/export features

**Critical Implementation Details:**
- Timezone handling uses `java.time.ZoneId.systemDefault()` for consistent UI ↔ global state conversion
- Spinner value commitment via `commitValue()` before reading values to handle JavaFX uncommitted edits
- Initialization order: restore UI from global state BEFORE injecting into ViewModels to prevent listener overwrites

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