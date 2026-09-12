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
java -jar target/dp-desktop-app-1.16.0-shaded.jar
```
The main class is `com.ospreydcs.dp.gui.DpDesktopApplicationRunner`, a launcher that does not extend `Application` (the java launcher rejects an `Application` subclass as main class when JavaFX is on the classpath).

### Maven Profiles
- `dev` (default): Development profile
- `ci`: Continuous integration profile  
- `release`: Release profile

### Dependencies
This project depends on:
- `dp-grpc` (v1.16.0): gRPC API definitions
- `dp-service` (v1.16.0): gRPC service implementations
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
The application integrates with external sibling repositories, checked out alongside this one:
- `~/dp/dp-java/dp-service`: gRPC service implementations
- `~/dp/dp-java/dp-grpc`: gRPC API definitions

These track the upstream `osprey-dcs` org and are the checkouts to build from.

> **Note:** a second set of checkouts may exist under `~/dp.fork/dp-java/`, tracking a personal
> fork (`craigmcchesney/*`) rather than the org. Earlier revisions of this file pointed there.
> Building from that tree installs whatever version it happens to sit at, which silently produces
> compile errors against APIs added upstream since — the failure looks like a missing class rather
> than a stale dependency. Use `~/dp/dp-java/` unless you are deliberately working on the fork.

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
Metadata → PV, Machine Configuration
Explore → Data, PVs, Providers, Datasets, Annotations, Data Events
```

**Menu Item Logic:**
- **Generate**: Conditionally enabled (disabled for remote production connections to prevent fake data contamination)
- **Import**: Always enabled (real data import is safe for all environments)
- **Metadata > PV**: Always enabled (creating PV metadata does not depend on session data ingestion); navigates to pv-metadata view for creating/updating PV metadata records
- **Metadata > Machine Configuration**: Always enabled (same rationale); navigates to machine-configuration view for creating machine configuration records and their activation intervals
- **Explore menu items**: Enabled after data ingestion (in-process mode) or immediately (remote production mode), because they browse metadata derived by aggregation over ingested documents and have nothing to show until data exists
- **PVs**: Navigate to pv-explore view for PV discovery and management
- **Providers**: Navigate to provider-explore view for provider discovery and management
- **Datasets**: Navigate to dataset-explore view for dataset discovery and Dataset Builder navigation
- **Annotations**: Navigate to annotation-explore view for annotation discovery and management
- **Data Events**: Navigate to data-event-explore view for data event subscription management and monitoring

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

### Application Lifecycle
- `DpDesktopApplication` (JavaFX Application) manages application lifecycle
- `DpApplication` initialized in `init()` phase before JavaFX UI starts
- MainController coordinates navigation and dependency injection to child controllers
- Child controllers receive DpApplication, Stage, and MainController references for full integration
- Application shutdown handled through `DpApplication.fini()` to properly clean up gRPC services

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
- ✅ Reusable ProviderDetailsComponent and ColumnMetadataComponent for section modularity
- ✅ Component-based architecture for data-generation view enabling code reuse
- ✅ Data import view with Excel file processing and DataImportUtility integration
- ✅ Calculations section with multi-sheet Excel import functionality
- ✅ Data export functionality (CSV, XLSX, HDF5 formats) with automatic file opening
- ✅ Complete data ingestion workflow for both data generation and data import paths
- ✅ Ingest and Reset button functionality in data-import view with proper error handling
- ✅ Critical Integration Pattern implemented across all views using reusable components
- ✅ Column-level metadata (provenance, tags, attributes) applied to every ingested column in both generation and import workflows
- ✅ PV Explore view with dedicated PV discovery, search, and management functionality
- ✅ QueryPvsComponent reusable component for PV list management with individual remove buttons
- ✅ Integrated navigation between data-explore and pv-explore views
- ✅ Custom ListCell implementations for individual item actions (remove buttons)
- ✅ Provider Explore view with dedicated provider discovery, search, and management functionality
- ✅ Provider search functionality with hyperlink PV names for easy addition to query list
- ✅ Integrated QueryPvsComponent in provider-explore for consistent PV management across views
- ✅ Cross-view navigation from pv-explore to provider-explore via Provider Name hyperlinks
- ✅ Automatic provider search execution when navigating from PV results to provider details
- ✅ Dataset Explore view with dedicated dataset discovery, search, and management functionality
- ✅ Dataset ID hyperlink navigation to Dataset Builder tab with automatic dataset loading
- ✅ Protobuf DataSet to DataBlockDetail conversion for form population
- ✅ Annotation Explore view with dedicated annotation discovery, search, and management functionality
- ✅ Annotation search with 7 criteria fields and hyperlink navigation for IDs and calculation frames
- ✅ Reusable CalculationFrameDetailsDialog component shared between data-explore and annotation-explore
- ✅ AnnotationInfoTableRow wrapper for protobuf Annotation objects with calculation frame access
- ✅ Data Event Subscription Details component with reusable subscription management
- ✅ SubscriptionDetailsComponent integrated into data-generate view with auto-submission form
- ✅ SubscriptionDetailsComponent integrated into data-import view with identical patterns
- ✅ Subscription data flow to both DpApplication.generateAndIngestData() and DpApplication.ingestImportedData() APIs for event monitoring
- ✅ Data Event Explore view with comprehensive subscription management and event monitoring
- ✅ Three-section data-event-explore layout with subscription list, builder form, and events table
- ✅ Custom ListCell and TableCell implementations with hyperlinks for cross-view navigation
- ✅ Real-time data event subscription processing with background task integration
- ✅ Event timestamp hyperlinks with automatic query editor navigation and time window setup
- ✅ PV Metadata view for creating/updating PV metadata records via savePvMetadata() (aliases, tags, attributes, description)
- ✅ Machine Configuration view for creating configuration records and activation intervals via saveConfiguration() / saveConfigurationActivation(), with a getConfiguration() overwrite warning
- ✅ Demo sample status generation in the data-generation view via saveSampleStatuses(), plus an unwired querySampleStatuses() read-back wrapper

## GUI Architecture

### MVVM Implementation
The application follows the Model-View-ViewModel pattern:

**Controllers** (`src/main/java/com/ospreydcs/dp/gui/*Controller.java`)
- Handle FXML UI binding and user interactions
- Delegate business logic to ViewModels
- Example: `DataGenerationController`, `DataExploreController`, `DataImportController`, `PvExploreController`, `ProviderExploreController`, `DatasetExploreController`, `AnnotationExploreController`, `DataEventExploreController`, `PvMetadataController`, `MachineConfigurationController`, `MainController`

**ViewModels** (`src/main/java/com/ospreydcs/dp/gui/*ViewModel.java`)
- Contain UI state and business logic
- Use JavaFX properties for data binding
- Example: `DataGenerationViewModel`, `DataExploreViewModel`, `DataImportViewModel`, `PvExploreViewModel`, `ProviderExploreViewModel`, `DatasetExploreViewModel`, `AnnotationExploreViewModel`, `DataEventExploreViewModel`, `PvMetadataViewModel`, `MachineConfigurationViewModel`, `MainViewModel`

**Views** (`src/main/resources/fxml/*.fxml`)
- FXML layout definitions
- Styled with BootstrapFX and custom CSS
- Example: `data-generation.fxml`, `data-explore.fxml`, `data-import.fxml`, `pv-explore.fxml`, `provider-explore.fxml`, `dataset-explore.fxml`, `annotation-explore.fxml`, `data-event-explore.fxml`, `pv-metadata.fxml`, `machine-configuration.fxml`, `main-window.fxml`

### Data Generation Workflow (Implemented)
1. **Provider Registration**: Users fill provider details (name, description, tags, attributes)
2. **Column Metadata Configuration**: Set time range, plus column provenance (source/process), tags, and attributes applied to every generated column
3. **PV Definition**: Always-visible form for adding process variables with automatic submission
4. **PV Form Auto-Submission**: Automatically adds PVs when all fields are filled and user presses Enter or moves focus
5. **Focus Management**: Returns focus to PV Name field after successful addition for rapid multi-PV entry
6. **Sample Status (demo)**: Optional checkbox generating a random EPICS-style alarm status for every sample of every PV
7. **Subscription Configuration**: Data event subscription details with trigger conditions and PV monitoring
8. **Form Validation**: Ensures all required fields are filled and time ranges are valid
9. **Data Generation**: Uses random walk algorithm to generate time-series data
10. **Ingestion**: Calls gRPC API to ingest generated data into MongoDB with subscription details

**Sample status generation is demo-only and off by default.** When checked, each ingestion bucket
is followed immediately by a `saveSampleStatuses()` call for the samples just ingested, in domain
`epics_alarm` / layer `demo_generator` (0=NO_ALARM, 1=MINOR_ALARM, 2=MAJOR_ALARM,
3=INVALID_ALARM, weighted ~85/10/4/1).

**Why the save happens inside the bucket loop rather than batched at the end:** a sample status
attaches to a sample only by exact `(pvName, timestamp)` equality at *nanosecond* precision, so a
status frame's `SamplingClock` must equal the clock the data was ingested with exactly. Building
the frame in the loop lets it reuse the very same `samplingClockStartSeconds` /
`samplingClockStartNanos` / `samplingClockPeriodNanos` / `samplingClockCount` locals already
computed for the ingestion request, so the two clocks cannot drift apart. Accumulating frames and
reconstructing their clocks later reintroduces exactly that risk — and misalignment **fails
silently**: the save succeeds, the statuses are stored, and nothing matches at query time. Saving
per bucket also keeps each request bounded, since the service may enforce a configured batch size
limit.

Switching to an explicit `TimestampList` would not make this safer: the risk is in *recomputing*
timestamps independently of the ingested ones, not in the encoding, and a list would add a second
computation to keep in sync. `SamplingClock` keeps one source of truth and is the shape the
cookbook recommends for dense labeling.

`confidence` and `reasons` are deliberately left unset — each is all-or-nothing (empty, or exactly
one entry per timestamp), and an all-empty `reasons` list must be omitted rather than sent as empty
strings.

**A sample status save failure does not fail the ingestion.** By the time a save is attempted the
bucket's data is already in the archive, and status generation is an opt-in demo extra. Aborting
would skip the `hasIngestedData` / `totalPvsIngested` / `totalBucketsCreated` assignments on the
success path, which are what enable the Explore menu — leaving data that was genuinely ingested
present but unreachable from the UI. Failures are instead collected in `SampleStatusAccumulator`
(first error only, since a failing status service fails identically once per bucket), logged at
WARN, and reported in the success message alongside the count.

**`modifiedBy` falls back rather than passing null.** `sampleStatusModifiedBy()` uses the
registered provider name when set, and `SAMPLE_STATUS_DEMO_SOURCE` otherwise. `providerName` is
only assigned by `registerProvider()` while `generateAndIngestData()` guards on `providerId`, and
`AnnotationClient.saveSampleStatuses()` omits the field entirely when it is null — which would
store the statuses unattributed with nothing indicating that happened.

**The reported count is an upsert count, not an insert count.** Saves are keyed on
`(pvName, timestamp, domain, layer)` and fully replace, so re-generating over the same PVs and time
range reports the same number while replacing rather than adding. The success message and the view
caption both say so.

**The domain registry is not implemented.** `saveSampleStatusDomain()` / `querySampleStatusDomains()`
are reserved in the proto but deferred server-side, so the `epics_alarm` code mapping exists only in
`DpApplication` constants — nothing can resolve code 2 to "MAJOR_ALARM" from the archive.

### Data Import Workflow (Implemented)
1. **Provider Configuration**: Uses reusable ProviderDetailsComponent for name, description, tags, attributes
2. **Column Metadata Configuration**: Uses reusable ColumnMetadataComponent for provenance source/process, tags, and attributes applied to every imported column
3. **File Selection**: Excel file chooser dialog (.xlsx/.xls formats) with validation
4. **Data Processing**: Integration with DataImportUtility.importXlsxData() from dp-service
5. **Frame Display**: Shows imported DataFrameResult objects with human-readable format
6. **Subscription Configuration**: SubscriptionDetailsComponent integrated with data event subscription details
7. **Data Ingestion**: "Ingest" button calls DpApplication.registerProvider() then DpApplication.ingestImportedData() with subscription details
8. **Reset Logic**: "Reset" button clears import details; auto-reset on new file selection
9. **Error Handling**: Comprehensive error handling with status bar feedback and recovery options
10. **Success Flow**: Returns to home view with confirmation, enables Explore menu items
11. **Navigation**: Always-enabled Import menu item (unlike conditional Generate menu)

### Data Explore Workflow (Implemented)
1. **Data Explorer Tools**: Collapsible panel with Query Editor, Dataset Builder, and Annotation Builder tabs
2. **PV Selection**: Navigate to pv-explore view via "Explore PVs" button for PV discovery
3. **Time Range Selection**: Set query begin/end times with date pickers and time spinners
4. **PV Management**: Individual remove buttons (🗑️) next to each PV name in Query Editor
5. **Query Execution**: Execute query and display results in Data Viewer section
6. **Data Viewer**: Collapsible section with dynamic table and interactive chart
7. **Results Display**: Dynamic table with columns for timestamp and selected PVs  
8. **Chart Visualization**: TabPane with Table and Chart views, LineChart with NumberAxis scaling
9. **Interactive Features**: Mouse tracking tooltips, dynamic data sampling for performance

### PV Explore Workflow (Implemented)
1. **Query PVs Component**: Reusable component displaying current PV selection with individual remove buttons
2. **PV Query Editor**: Search form with pattern matching and name list options
3. **Search Execution**: Background task queries PV metadata with loading indicators
4. **Results Display**: TableView with PV details (name, data type, timestamps, sample period)
5. **PV Selection**: Checkbox-based selection with "Select All" header functionality
6. **Bulk Operations**: "Add Selected" button (enabled when checkboxes selected)
7. **Individual Operations**: Hyperlink PV names for direct addition to Query PVs list
8. **Cross-View Navigation**: "Edit Query" button returns to data-explore view with updated PV list
9. **Provider Navigation**: Provider Name hyperlinks navigate to provider-explore view with automatic search
10. **State Synchronization**: PV additions/removals automatically sync with global application state

### Provider Explore Workflow (Implemented)
1. **Query PVs Component**: Reusable component on left side for PV selection management (same as pv-explore)
2. **Provider Query Editor**: Search form with 5 optional fields (Provider ID, Name/Description, Tag Value, Attribute Key, Attribute Value)
3. **Search Execution**: Background task queries provider metadata with loading indicators and status feedback
4. **Results Display**: TableView with provider details - optimized column order for visibility when truncated:
   - ID, Name, Description, **PV Names** (positioned early), Tags, Attributes, Buckets
5. **Interactive PV Names**: Each PV name in results is a hyperlink for direct addition to Query PVs list
6. **Cross-View Navigation**: "Edit Query" button in QueryPvsComponent returns to data-explore view
7. **State Synchronization**: PV additions automatically sync with global application state
8. **API Integration**: Uses `DpApplication.queryProviders()` with null-safe parameter handling

### Dataset Explore Workflow (Implemented)
1. **Dataset Query Editor**: Search form with 4 optional fields (Dataset ID, Owner, Name/Description, PV Name)
2. **Search Execution**: Background task queries dataset metadata with loading indicators and status feedback
3. **Results Display**: TableView with dataset details including ID, name, owner, description, and data blocks count
4. **Interactive Dataset IDs**: Each Dataset ID is a hyperlink that navigates to data-explore view's Dataset Builder tab
5. **Automatic Dataset Loading**: Clicking ID hyperlinks triggers background dataset query and form population
6. **Cross-View Navigation**: Seamless navigation to Dataset Builder with all dataset details loaded
7. **API Integration**: Uses `DpApplication.queryDataSets()` for search and individual dataset loading
8. **Form Population**: Protobuf DataSet objects converted to UI-friendly DataBlockDetail objects

### Annotation Explore Workflow (Implemented)
1. **Annotation Query Editor**: Search form with 7 optional fields (Annotation ID, Owner ID, Name, Description, Tag Value, Attribute Key/Value, Dataset ID)
2. **Search Execution**: Background task queries annotation metadata with loading indicators and status feedback
3. **Results Display**: TableView with 9 columns: ID, owner, related datasets, name, related annotations, description, tags, attributes, calculations
4. **Interactive Annotation IDs**: Each Annotation ID is a hyperlink that navigates to data-explore view's Annotation Builder tab
5. **Calculations Column**: shows *presence*, not frame names — a single "View calculations" hyperlink that fetches the frames on click
6. **Automatic Annotation Loading**: Clicking ID hyperlinks triggers a background `getAnnotation()` and form population
7. **Cross-View Navigation**: Seamless navigation to Annotation Builder with all annotation details loaded
8. **API Integration**: `DpApplication.queryAnnotations()` for search, `getCalculations()` to open calculations, `getAnnotation()` to load into the builder

**The Calculations column cannot list frame names.** `queryAnnotations()` returns `calculationsId`
without Calculations content as of dp-grpc #132 — the denormalization was removed deliberately.
Presence comes from `calculationsId` alone (`AnnotationInfoTableRow.hasCalculations()`); names are
resolved by the `getCalculations()` fetch the hyperlink triggers, and a multi-frame result prompts
for which frame to open. Resolving names per row would rebuild client-side, as serial round trips
from a GUI thread, the N+1 fan-out that #132 removed — worse than the server-side version it
replaced.

**There is no event search criterion, and the search field no longer offers one.** The modernized
`Annotation` has no event field at all — dp-grpc #132 removed the event metadata — and the free-text
box is sent as `TextCriterion`, a collection-level text-index search whose indexed fields are name
and description. The field was labelled "Name / Description / Event" through the rename, which
promised a search that silently matched nothing; it is now "Name / Description"
(`nameDescriptionField` / `nameDescriptionTextProperty`). Restoring event search needs a proto
criterion first, not a UI change.

**`Annotation.comment` is `description` everywhere, including the UI.** dp-grpc #132 renamed the
proto field; the app's view-model properties, `fx:id`s, column headers and field labels followed in
one pass, so the concept has one name end to end.

The hazard in that rename is `AnnotationExploreController.setupTableColumns()`, where every column
is wired with `new PropertyValueFactory<>("someName")` — a **string** resolved against
`AnnotationInfoTableRow` by reflection at render time. A stale string yields a **silently blank
column**, not a compile error, and `ViewLoadSmokeTest` does not catch it either (it proves
`initialize()` ran, never that a row renders). `AnnotationInfoTableRowBindingTest` now asserts each
binding string resolves *to its expected value* — note that asserting non-null alone is not enough,
because `PropertyValueFactory` falls back from `someProperty()` to `getSome()`, so a partial rename
can still resolve. Renaming a row property means updating that test's table too.

**Loading an annotation for editing MUST go through `getAnnotation()`, never `queryAnnotations()`.**
`getAnnotation()` is the only method returning Calculations content inline. Because
`saveAnnotation()` is a full-replace upsert, loading through a query result would populate the
builder with no calculations, and saving any unrelated edit would then destroy the stored
Calculations — no error, no warning, and nothing in the UI indicating a loss. The comment at
`AnnotationBuilderViewModel.loadFromAnnotation()` guards this, since the read there looks like an
ordinary embedded-content read and is only safe because of who calls it.

### Data Event Explore Workflow (Implemented)
1. **Data Event Subscriptions Management**: Left panel ListView displaying active subscriptions with custom ListCell format
2. **Subscription Builder**: Top-right form with PV Name, Trigger Condition, Trigger Value, and PV Data Type fields
3. **Form Validation**: Real-time validation with button enable/disable based on required field completion
4. **Subscription Creation**: Background task processing with comprehensive error handling and status feedback
5. **Subscription Display**: Custom ListCell with hyperlink subscription names and trash button (🗑️) for removal
6. **Event Loading**: Click subscription hyperlinks to load associated events in bottom-right table
7. **Events Display**: TableView with Event Time (hyperlink) and Trigger Value columns
8. **Cross-View Navigation**: Click event timestamp hyperlinks to navigate to data-explore Query Editor
9. **Automatic Query Setup**: Pre-populate PV name and 60-second time window around event timestamp
10. **API Integration**: Uses `DpApplication.subscribeDataEvent()`, `cancelDataEventSubscription()`, and `dataEventsForSubscription()` methods
11. **Menu Integration**: "Data Events" menu item enabled after data ingestion, following established patterns
12. **Background Processing**: All operations use JavaFX Tasks to prevent UI blocking

### PV Metadata Workflow (Implemented)
1. **Navigation**: `Metadata > PV` menu item (always enabled) opens the pv-metadata view via `switchToView()`
2. **Form Entry**: PV Name (required), Description, and Modified By text fields
3. **Aliases**: Reuses `TagsListComponent` (a free-form string list) with `labelText="Aliases:"`
4. **Tags & Attributes**: `TagsListComponent` and `AttributesListComponent` for tags and key-value attributes
5. **Validation**: Save button disabled while PV Name is blank (trimmed, so whitespace-only does not enable it) or a save is in progress
6. **Save Execution**: Background `javafx.concurrent.Task` calls `DpApplication.savePvMetadata()`, keeping the UI responsive
7. **Status Feedback**: View-local status label plus a `ProgressIndicator` bound to an `isSaving` property
8. **Error Surfacing**: Server rejections reported verbatim from `apiResult.resultStatus.msg`
9. **Reset**: Clears all fields and all three list components

**Full-replace upsert:** `savePvMetadata()` replaces the ENTIRE record for a given PV name — aliases, tags, attributes, description and modifiedBy are all overwritten, and omitted fields are not preserved. The view states this in the panel. Loading an existing record before editing (`getPvMetadata()`) is deferred to a follow-up.

**Critical Integration Pattern:** aliases/tags/attributes are read from the injected component instances, never from ViewModel properties. `PvMetadataViewModel` holds no collections for them. The component lists are copied on the FX thread before the background task starts, so the task never touches the observable lists off-thread.

### Machine Configuration Workflow (Implemented)
1. **Navigation**: `Metadata > Machine Configuration` menu item (always enabled) opens the machine-configuration view via `switchToView()`
2. **Section 1 — Configuration**: Configuration Name (required), Category (required), Description, Parent Configuration, Modified By, plus `TagsListComponent` and `AttributesListComponent`
3. **Configuration Validation**: Save button disabled while either required field is blank (trimmed) or a save is in progress
4. **Overwrite Warning**: Before saving, `getConfiguration()` checks for an existing record and prompts for confirmation, since the save is a full-replace upsert
5. **Section 2 — Activations**: Disabled until a configuration is saved in this session; start/end `DatePicker` + hour/minute/second `Spinner`s, Client Activation ID, Description, Modified By, plus its own independent `TagsListComponent` and `AttributesListComponent`
6. **Activation Validation**: Add button disabled until both dates are set; end time must be after start time
7. **Session List**: Activations created this session accumulate in a `ListView` showing the activation id and time interval, via `ConfigurationActivationDetail`
8. **Save Execution**: Background `javafx.concurrent.Task` calls `DpApplication.saveConfiguration()` / `saveConfigurationActivation()`
9. **Status Feedback**: View-local status label plus a `ProgressIndicator` bound to an `isSaving` property
10. **Reset**: Clears both forms, all four list components, the session activation list, and re-disables the activation section

**Why one view and not two:** an activation cannot be created without an existing configuration. The server resolves `configurationName` against the Configuration collection on every activation save and rejects the request outright if it does not resolve (`no Configuration found for configurationName: '<name>'`). Gating section 2 on a configuration saved in this session, and binding it to the name the server returned rather than to the still-editable text field, makes that rejection unreachable through normal use.

**Activation overlap is rejected on same configurationName OR same category.** The Configuration's category is denormalized onto each activation record as `internalCategory`, so two *different* configurations sharing a category cannot have overlapping activations. This is correct server behavior; the view surfaces the message rather than swallowing it.

**Detecting not-found:** `getConfiguration()` reports a missing record as a *rejection*, not an empty successful result. Branch on `ApiResultBase.isReject()` rather than `isError()` — a service that is unreachable also sets `isError`, and treating that as "no existing record" would suppress the overwrite warning. Note that `REJECT` also covers server-side validation failures, so reading it as not-found is only safe once the request itself is known to be valid.

**Full-replace upsert:** both `saveConfiguration()` and `saveConfigurationActivation()` replace the ENTIRE record; omitted fields are not preserved. The view states this in the panel. Loading an existing record before editing is deferred to a follow-up.

**End time is required in this version.** The server supports open-ended activations (absent `endTime`), but one blocks every subsequent activation in its entire category indefinitely. This is a UI-side constraint only — `DpApplication.saveConfigurationActivation()` keeps `endTime` nullable.

**Critical Integration Pattern:** tags/attributes are read from the injected component instances, never from ViewModel properties. There are **four** components, not two — the configuration and the activation carry separate tag and attribute fields on separate records. The component lists are copied on the FX thread before each background task starts.

**The activation date/time controls are owned by the controller, not the ViewModel.** Clearing the
activation form therefore has to call back into the controller, via the
`setActivationTemporalFieldsReset(Runnable)` seam — the same shape as the `OverwriteConfirmation`
seam, and for the same reason (the ViewModel stays free of JavaFX control code). Both the
successful-save path and Reset go through that one callback, so they cannot drift apart. Clearing
only the `DatePicker`s is not enough: the six time spinners hold a time of day that would otherwise
be silently reused by the next activation, and since the server rejects overlapping activations
across an entire *category*, a stale time surfaces as a confusing overlap rejection rather than as
an obviously stale form.

**The session activation list is scoped to the saved configuration name.** Saving a *second*
configuration in the same session clears the list, because its entries describe activations of the
configuration previously named in `savedConfigurationName` and would otherwise be read as
activations of the new one.

**A supplied `clientActivationId` is an upsert key, not a label.** Supplying one that already names
a record replaces that record outright. A collision with an activation created *in this session* is
detected locally and confirmed through `setActivationOverwriteConfirmation(...)`, and the session
list is reconciled in place rather than appended to, so a replacement does not leave a stale row
beside its replacement. A collision with a record this session knows nothing about is **not**
detected: that needs a server round trip, and `AnnotationClient` exposes no wrapper for the
`getConfigurationActivation()` RPC even though the service implements it — see the follow-up issue.
Until that lands the field's prompt text carries the warning.

**Waiting on an FX-thread confirmation from a background task is bounded.** `runOnFxThreadAndWait()`
awaits with a timeout and returns `Boolean` so the caller can tell "declined" from "never answered".
An unbounded await deadlocks the save thread if the FX thread is gone (view navigated away,
application shutting down mid-save), leaving `isSaving` true and the progress indicator spinning
with no way back. A timeout is treated as *do not save* — an unconfirmed overwrite must never go
through.

**The save task reports a typed outcome, not a null sentinel.** Whether a save was skipped because
the user declined, because the existence check failed, or was actually attempted is carried by
`SaveOutcome`/`PreSaveOutcome`. The earlier version distinguished these by prefix-matching the
status message, which both re-introduced the message-sniffing that `isReject()` exists to avoid and
raced with the `Platform.runLater` that sets the message.

### Dataset Builder Workflow (Implemented)
1. **Dataset Configuration**: Enter dataset name (required), description (optional), and auto-generated ID field
2. **Data Block Management**: Collect DataBlockDetail objects from Data Explorer using "Add to Dataset" button
3. **Data Block Operations**: Remove selected data blocks or view their details in Data Explorer
4. **Cross-Tab Navigation**: "View Data" button populates Data Explorer fields and switches tabs
5. **Dataset Persistence**: Save button validates inputs and calls DpApplication.saveDataSet() API
6. **Validation & Feedback**: Real-time validation with status messages and button enable/disable logic
7. **State Management**: Preserve dataset details across save operations and tab switches

### Annotation Builder Workflow (Implemented)

**Tags and attributes belong to the components, and `AnnotationBuilderViewModel` holds no
collections for them** — it holds the injected `TagsListComponent` /
`AttributesListComponent` references, like `PvMetadataViewModel`. It previously held its own
`ObservableList`s, and they were write-only: `loadFromAnnotation()` filled them while
`DataExploreController.onSaveAnnotation()` read the components, so loading an annotation put its
tags and attributes somewhere the save never looked. Editing any other field and saving then wrote
them back as absent — silently, because `saveAnnotation()` is a full-replace upsert. That is the
same failure mode as the calculations loss the `getAnnotation()` load fixes. Holding only the
component references makes the divergence unrepresentable rather than merely fixed, and `reset`
clears the components (it previously left the controls populated, carrying the prior annotation's
metadata into the next save).

1. **Annotation Configuration**: Enter annotation name (required), description, and event name (optional)
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

### DataFrameDetails (`src/main/java/com/ospreydcs/dp/gui/model/DataFrameDetails.java`)
Represents individual calculation frames from Excel import:
- Name (String, typically sheet name from Excel)
- Timestamps (List<Timestamp>, protobuf format)
- Data columns (List<DataColumn>, protobuf format)
- Human-readable toString() format: "Frame name - Column1, Column2, Column3..."
- Created from multi-sheet Excel import using shared DataImportUtility

### PvInfoTableRow (`src/main/java/com/ospreydcs/dp/gui/model/PvInfoTableRow.java`)
Wrapper for protobuf PvInfo in TableView displays:
- PV name, provider name, data type, formatted timestamps and sample periods
- Provider ID access for cross-view navigation (getLastProviderId())
- Selection state management for bulk operations
- Property binding support for JavaFX TableView integration
- Used in pv-explore view for PV discovery and selection with provider navigation

### ProviderInfoTableRow (`src/main/java/com/ospreydcs/dp/gui/model/ProviderInfoTableRow.java`)
Wrapper for protobuf ProviderInfo in TableView displays:
- Provider ID, name, description, formatted tags and attributes
- PV names list extraction for hyperlink functionality
- Property binding support for JavaFX TableView integration
- Used in provider-explore view for provider discovery and PV selection
- Formats attributes as "key1=value1, key2=value2" strings from protobuf Attribute list

### DatasetInfoTableRow (`src/main/java/com/ospreydcs/dp/gui/model/DatasetInfoTableRow.java`)
Wrapper for protobuf DataSet in TableView displays:
- Dataset ID, name, owner, description, and data blocks count
- Property binding support for JavaFX TableView integration
- Used in dataset-explore view for dataset discovery and navigation
- Hyperlink support for Dataset ID column navigation to Dataset Builder

### AnnotationInfoTableRow (`src/main/java/com/ospreydcs/dp/gui/model/AnnotationInfoTableRow.java`)
Wrapper for protobuf Annotation objects in TableView displays:
- Annotation ID, owner, name, description, datasets, tags, attributes, calculations presence (there is no event field — dp-grpc #132 removed the Annotation's event metadata)
- Property binding support for JavaFX TableView integration
- Formats the multi-valued fields (datasets, related annotations, tags, attributes) as comma-separated strings; the Calculations field is a *presence label*, not a frame-name list
- The `PROPERTY_*` constants name the properties the `PropertyValueFactory` column bindings resolve reflectively; `AnnotationExploreController` and `AnnotationInfoTableRowBindingTest` both reference them rather than repeating the literals, so a rename that misses the controller fails to compile instead of silently blanking a column
- Exposes `getCalculationsId()` / `hasCalculations()` for the presence-driven Calculations column; it no longer holds frame content, since `queryAnnotations()` does not return any
- Used in annotation-explore view for annotation discovery and navigation
- Hyperlink support for the Annotation ID column and for the Calculations presence link

### DataEventSubscription (`src/main/java/com/ospreydcs/dp/gui/model/DataEventSubscription.java`)
Wrapper for data event subscription management in data-event-explore view:
- Contains SubscribeDataEventDetail and subscription metadata
- Provides display string formatting for ListView presentation
- Used for subscription lifecycle management (create, display, cancel)
- Integrates with gRPC IngestionStreamService for real-time event monitoring

### SubscribeDataEventDetail (`src/main/java/com/ospreydcs/dp/gui/model/SubscribeDataEventDetail.java`)
Represents data event subscription configuration:
- PV name, trigger condition (EQUAL_TO, GREATER, etc.), trigger value
- Display string formatting for ListView presentation ("pvName > value")
- Used for data event monitoring and notification subscriptions
- Integrated with data generation and import workflows for event-driven data ingestion

### ConfigurationActivationDetail (`src/main/java/com/ospreydcs/dp/gui/model/ConfigurationActivationDetail.java`)
Represents a configuration activation created during the current session:
- Client activation ID, configuration name, start and end time (Instant)
- The activation ID is always the identifier of the saved record: the one supplied in the request, or the one the server generated when the request omitted it — in the latter case it is the caller's only handle on the record, which is why it is displayed
- Display string formatting for ListView presentation: "activation-id: 2026-08-21 09:00:00 -> 2026-08-21 17:00:00"
- A null end time renders as "open-ended"; the view requires an end time, but the model does not impose the restriction the UI does
- Used in machine-configuration view for the session activation list

### Global State Management
`DpApplication` maintains cross-view state with automatic synchronization:
- Provider ID and name after registration
- Data time ranges (begin/end instants) - synced from query UI changes
- List of PV names (List<String>) - unified storage for generated and imported PV names
- Real-time listeners in DataExploreController update global state when UI changes
- Global state is restored when navigating between views
- Used for data generation, data import, query operations, and future annotation/export features

**Critical Implementation Details:**
- Timezone handling uses `java.time.ZoneId.systemDefault()` for consistent UI ↔ global state conversion
- Spinner value commitment via `commitValue()` before reading values to handle JavaFX uncommitted edits
- Initialization order: restore UI from global state BEFORE injecting into ViewModels to prevent listener overwrites

## Shared Utilities Integration

### DataImportUtility
Located in `dp-service` dependency (`~/dp/dp-java/dp-service`):
- **Multi-Sheet Excel Import**: `DataImportUtility.importXlsxData(String filePath)`
- **Input Format**: First two columns must be epoch seconds and nanoseconds
- **Returns**: `DataImportResult` with list of `DataFrameResult` objects (one per sheet)
- **Error Handling**: Skips invalid sheets/rows, continues processing valid data
- **Shared Usage**: Used by Calculations import, Data Import view, and future PV ingestion features
- **Dependencies**: Requires updated `dp-service` to be installed to local Maven repository
- **Integration Pattern**: Import `com.ospreydcs.dp.client.result.DataImportResult` for result handling

### Dependency Updates
`dp-grpc` and `dp-service` are not published to any package registry — they are resolved from the
local Maven repository, so a change in either only reaches this app once it is installed there.
Reinstall after modifying them, **and also after pulling upstream changes that this app needs**:

```bash
cd ~/dp/dp-java/dp-service
mvn clean install -DskipTests
cd ~/dp/dp-java/dp-desktop-app
mvn clean compile
```

The three repos carry the same pom version and are bumped in lockstep, so an installed jar built
before an upstream API was added is indistinguishable by version from one built after it. The
symptom is a compile error naming a class or method that demonstrably exists in the dp-service
source — at which point the fix is to reinstall, not to go looking for the missing code. CI
(`.github/workflows/ci.yml`) sidesteps this entirely by building both siblings from `main` on every
run, which is why a green CI does not prove a local build is current.

### Testing and Development Workflow
```bash
# Build and run application for testing
mvn clean compile javafx:run

# Package application for deployment testing
mvn clean package
java -jar target/dp-desktop-app-1.16.0-shaded.jar

# Update shared utilities workflow (when modifying dp-service dependency)
cd ~/dp/dp-java/dp-service
mvn clean install -DskipTests
cd ~/dp/dp-java/dp-desktop-app
mvn clean compile

# Run the test suite
mvn clean test
```

**Test suite** (added by #29): unit tests for models, components and the `DpApplication` static
helpers, plus JavaFX smoke tests run under a real toolkit. `ViewLoadSmokeTest` enumerates every
FXML under `/fxml` declaring an `fx:controller` **from the classpath rather than a hand-maintained
list**, so a newly added view is covered the moment its file lands — it will fail the build on an
FXML syntax error, an `fx:id` type mismatch, or an `initialize()` that throws. This is why
`initialize()` must stay dependency-free, with `DpApplication` / `Stage` / `MainController`
injected afterward via setters.

Post-injection behavior (button handlers calling `DpApplication`, background tasks, navigation) is
not covered — that needs an injection seam and robot-driven interaction testing.

**Calculations import fixture** (`CalculationsWorkbookFixture`, added by #43): generates the
multi-sheet XLSX used to exercise Annotation Builder → Import Calculations by hand, and through it
the Calculations presence column, the fetch-on-click, and the multi-frame chooser.

It is committed as code rather than as a binary because `DataImportUtility` rejects malformed input
**silently** — a blank header cell skips the whole sheet, a row whose cell count differs from the
header's skips that row, and an unsupported cell type skips the row — each with a log line and no
error. A hand-built workbook can therefore lose a frame, a column or a row and still look like it
imported, which during manual verification presents as "the feature lost my data" when the file was
at fault. `CalculationsWorkbookFixtureTest` round-trips the generated workbook through the real
`DataImportUtility` and asserts every sheet becomes a frame, every row survives, and all three
`DataValue` types (numeric, string, boolean) come back — so the fixture's validity is checked, not
assumed.

Deliberately multi-sheet: a single-frame annotation opens the frame dialog directly, so a one-sheet
file would never reach the multi-frame chooser that P2.1 added. Timestamps derive from a fixed base
instant rather than `now()`, so regenerating yields the same file. Regenerate with:

```bash
mvn -q test-compile exec:java -Dexec.classpathScope=test   -Dexec.mainClass=com.ospreydcs.dp.gui.testutil.CalculationsWorkbookFixture   -Dexec.args="<output>.xlsx"
```

**Live integration test** (`AnnotationApiLiveIT`, added by #43): exercises the Annotation API
end to end against a real in-process ecosystem and a real MongoDB — the `getAnnotation()` vs
`queryAnnotations()` calculations asymmetry, the load-edit-save round trip that must preserve tags
and attributes, `getCalculations()` frame-name resolution, `isReject()` on missing records, and
transparent paging over 120 real datasets. None of it is reachable without a database.

It **skips rather than fails** when MongoDB is unreachable (a JUnit assumption on a socket probe
against the configured `MongoClient.dbHost`/`dbPort`), so CI — which has no database — stays green
while a developer running MongoDB gets the coverage automatically from a plain `mvn test`.

`*IT` is therefore added to the surefire `<includes>` in `pom.xml`. Note that declaring `<includes>`
**replaces** surefire's defaults, so the four default patterns are restated there; dropping them
would silently stop running every unit test. An integration test the build never invokes cannot
fail — it just rots until someone runs it by hand, which is the same trap that made the first
version of the reflective-binding guard worthless.

The test writes to the configured database (`dp-demo`), namespacing every record with a per-run
stamp and deleting them in `@AfterAll`. Cleanup is best-effort by design: a cleanup failure must
not redden the build, and leftover stamped records are inert and identifiable. To run it alone:
`mvn test -Dtest=AnnotationApiLiveIT`. To watch it skip: `mvn test -Ddp.MongoClient.dbPort=1`.

## MongoDB Integration
- Default database: `dp-demo`
- Managed through `InprocessServiceEcosystem`
- Data persistence handled by gRPC service layer
- MongoDB drivers: sync, reactive streams, core, and BSON

## Debugging and Logging
- Log4j2 configuration in `src/main/resources/log4j2.xml` (currently set to DEBUG level)
- Key logger names: `com.ospreydcs.dp.gui.*` for UI components
- Third-party library logging suppressed: `io.grpc.netty` (ERROR), `io.netty.util` (OFF), `org.mongodb.driver` (ERROR)
- JavaFX UI thread operations logged with method entry/exit points
- Global state synchronization extensively logged for troubleshooting
- Component data access patterns extensively logged for debugging Critical Integration Pattern violations

## Critical Architecture Concepts

### Application State Flow
The application maintains state through multiple layers that must be understood for effective development:

1. **DpApplication Layer**: Central state management for cross-view data sharing
   - Manages provider registration state (`providerId`, `providerName`)
   - Tracks ingested data state (`hasIngestedData`, `totalPvsIngested`, `pvNames`)
   - Handles time ranges and operation results for UI synchronization
   - Controls menu enablement through state flags

2. **ViewModel Layer**: View-specific business logic and UI state
   - Contains JavaFX properties for data binding
   - Handles form validation and user interactions
   - Communicates with DpApplication for backend operations
   - Manages background tasks for non-blocking operations

3. **Component Layer**: Reusable UI components with encapsulated state
   - Must be accessed through component methods, not parent ViewModels
   - Handle their own data validation and user interactions
   - Provide property binding for external integration

### Data Ingestion Architecture
Two parallel workflows exist for data ingestion:

**Data Generation Path**: UI Form → PvDetail objects → Random walk generation → IngestionClient.ingestData()
**Data Import Path**: Excel file → DataImportUtility → DataFrameResult objects → IngestionClient.ingestData()

Both paths converge at the same gRPC ingestion API but handle different data sources and processing requirements.

### Cross-View Navigation Patterns
The application uses a hub-and-spoke navigation model with cross-exploration capabilities:
- **Home View**: Central hub with application state display and interactive navigation hints
- **Interactive Hints**: Home view contains clickable hyperlinks for key navigation paths (Ingest→Generate, Ingest→Import, Explore→Data, etc.)
- **State-Dependent Guidance**: Different hint sets based on application state (pre-ingestion vs post-ingestion)
- **Feature Views**: Data generation, import, exploration - all return to home on completion
- **Cross-Exploration**: Direct navigation between pv-explore and provider-explore views via hyperlinks
- **Automatic Search**: Navigation includes automatic search execution with pre-populated parameters
- **State Synchronization**: Global state updates trigger menu enablement and home view updates
- **Background Operations**: Long-running operations use JavaFX Task with UI thread synchronization

### Component Data Access Anti-Pattern (Critical)
**NEVER access reusable component data through ViewModel properties.** This is the most common architectural error in this codebase:

```java
// ❌ WRONG - This pattern will result in empty data being passed to APIs
Map<String, String> attrs = convertAttributesToMap(viewModel.getProviderAttributes()); // Empty!
List<String> tags = List.copyOf(viewModel.getProviderTags()); // Empty!

// ✅ CORRECT - Always get data directly from component instances
Map<String, String> attrs = convertAttributesToMap(providerComponent.getProviderAttributes());
List<String> tags = List.copyOf(providerComponent.getProviderTags());
```

**Why this happens**: Reusable components manage their own internal state. ViewModel properties are only used for property binding, not data storage. The components never populate the ViewModel properties with their data.

**Required pattern**: Always inject component references into ViewModels and access data directly from component instances before API calls.

### Data Event Subscription Architecture
**SubscribeDataEventDetail Model**: Contains PV name, trigger condition (enum), and trigger value for event monitoring
**SubscriptionDetailsComponent**: Reusable component with auto-submission form and ListView management
**Integration Pattern**: Component data flows to `DpApplication.generateAndIngestData()` and `DpApplication.ingestImportedData()` APIs
**Event Processing**: Subscriptions processed before data ingestion to enable real-time monitoring
**Trigger Conditions**: EQUAL_TO, GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL from `DpApplication.TriggerCondition` enum

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

### Creating Reusable Dialog Components
1. **Dialog Structure**: Create both Java controller class and FXML file in `src/main/resources/fxml/components/`
2. **FXML Layout**: Design responsive dialog content with proper spacing and button placement
3. **Controller Logic**: Implement Initializable interface with content formatting and data handling
4. **Static Factory Method**: Provide `showDialog(DataType, Stage)` method for easy instantiation
5. **Error Handling**: Include comprehensive error handling with fallback dialogs for failures
6. **Data Conversion**: Handle protobuf to UI object conversions within the dialog controller
7. **Logging Integration**: Add debug logging for dialog operations and content formatting
8. **Parent Integration**: Replace inline dialog creation with reusable component calls

### Cross-View State Management Pattern
**DpApplication State Architecture:**
1. **Unified PV Names**: Store `List<String> pvNames` instead of view-specific objects
2. **Data Generation Flow**: Extract PV names from PvDetail objects after successful ingestion
3. **Data Import Flow**: Call `dpApplication.setPvNames()` after successful import ingestion
4. **Query View Integration**: Use `dpApplication.getPvNames()` for initialization regardless of data source
5. **Separation of Concerns**: Keep generation-specific PvDetail objects in DataGenerationViewModel
6. **Consistent Timing**: Only update global state after successful operations (generation/ingestion)

**Benefits**: Unified cross-view sharing, clean separation of UI-specific vs. shared state, support for multiple data sources

### Chart Integration
- Use NumberAxis instead of CategoryAxis for time-series data
- Implement `calculateOptimalTickUnit()` for proper axis scaling
- For performance with large datasets, disable symbols: `setCreateSymbols(false)`
- Use mouse tracking tooltips instead of per-point tooltips for better performance
- Implement dynamic data sampling for datasets > 1000 points
- **Tooltip Coordinate Issues**: Use `.chart-content` selector to find proper plot area bounds for accurate mouse-to-data coordinate transformation
- **Time Range Precision**: When calculating query intervals, use nanosecond precision (`Duration.toNanos()`) instead of `toSeconds()` to avoid truncating fractional seconds

### JavaFX Time Handling
- Always use `java.time.ZoneId.systemDefault()` for timezone conversions
- Call `spinner.commitValue()` before reading values to handle uncommitted edits
- Use initialization flags to prevent listeners from firing during UI setup
- **End Time Inclusivity**: Add nanoseconds (e.g., `.plusNanos(999_999_999)`) to end times created from `LocalTime.of()` to ensure full-second coverage in queries

### JavaFX Selection Model Timing Issues
**Problem**: ComboBox or ListView selection operations can cause `IndexOutOfBoundsException` when multiple selection models interact
**Solution**: Defer selection operations using `Platform.runLater()` to avoid timing conflicts:
```java
// ❌ WRONG - Can cause IndexOutOfBoundsException in complex UIs
comboBox.getSelectionModel().clearSelection();

// ✅ CORRECT - Defer to avoid timing conflicts
javafx.application.Platform.runLater(() -> {
    comboBox.getSelectionModel().clearSelection();
});
```
**When to use**: After actions that trigger multiple UI updates (exports, data operations, tab switches)

### FXML Layout Common Issues
- **Static Property Syntax**: Use `hgrow="ALWAYS"` in ColumnConstraints, not `HBox.hgrow="ALWAYS"`
- **Container-Specific Properties**: Static properties like `HBox.hgrow` only apply to child elements within that container type
- **GridPane vs HBox**: ColumnConstraints use `hgrow` directly, while HBox children use `HBox.hgrow` as static property
- **Compilation vs Runtime**: FXML syntax errors typically manifest as `PropertyNotFoundException` during FXML loading

### API Integration Patterns
- Always check `apiResult.resultStatus.isError` before processing API responses
- Use `apiResult.resultStatus.msg` (not `.message`) for error messages
- Handle null responses and exceptional results from gRPC services
- Status messages should provide immediate user feedback during API operations
- Use `ApiResultBase.isReject()` to distinguish a *rejected* request from a service failure. The single-record getters (`getConfiguration()`, and the other getters by the same convention) report a missing record as a rejection rather than as an empty successful result, so an existence check must branch on `isReject()` — `isError()` alone cannot tell "does not exist" from "the service is unreachable". Note `REJECT` also covers server-side validation failures, so reading it as not-found is only safe for a request already known to be valid.

**Query criteria combine with AND; values within one criterion combine with OR.** This holds across
the annotation queries (`queryDataSets`, `queryAnnotations`) as of dp-grpc #132. It replaced an
older two-bucket scheme that ORed some criteria and ANDed others, with different assignments per
method — so **two `TagsCriterion` entries used to mean "either tag" and now mean "both tags"**.
Nothing errors; the result set is simply smaller.

The app is not affected today: `DpApplication.queryDataSets()` / `queryAnnotations()` take single
`String` parameters from single text fields and pass them through `setIfPresent`, so they send one
value per criterion and one criterion per type. The trap is prospective — the natural way to add a
multi-tag search box is a comma-separated field, and "tag A, tag B" reads as "either" to most
people (and *was* "either" before #132). Implemented naively it silently returns fewer results
rather than more.

Note also that the dp-service client params can express at most one value per criterion and one
criterion per type, and expose no `NameCriterion` at all (nor `TagsCriterion` / `AttributesCriterion`
on `queryDataSets`), even though the proto supports all of them. **Multi-value or name-scoped search
therefore needs dp-service client work before any UI for it can be built.** `TextCriterion` is a
collection-level MongoDB text-index search over the record's indexed fields, not a per-field match,
so it cannot be scoped to a named field at query time.

**`queryDataSets()` / `queryAnnotations()` page transparently, up to a cap.** Both became paged in
dp-grpc #132, and **an unset `limit` means the server's default page size, not "everything"**.
`DpApplication` follows `nextPageToken` internally via the static `accumulatePages()` helper, so
the explore views still receive one complete list and need no paging UI. Accumulation stops at
`DpApplication.QUERY_RESULT_CAP` (5000) — without a bound this would just move the unbounded read
from the server to the client, which is what server paging was introduced to prevent.

Both wrappers return `PagedResult<T>` (`records` plus a `truncated` flag) rather than the raw
`ApiResult`, and **a failed page throws `QueryFailedException` rather than returning what had
accumulated** — a partial list presented as a complete one is the bug this fixes, not an acceptable
degradation. For the same reason the `fetchPage` function must signal failure by **throwing, never
by returning null**: a null page is indistinguishable from an empty one inside the loop, so
treating it as the end of the query would hand back a partial accumulation with `truncated=false`.
`accumulatePages()` therefore rejects a null page rather than tolerating it.

**Truncation must reach the user.** The original defect was not incompleteness; it was that the
views stated a count as though it were a total with nothing indicating otherwise. A silent cap
reproduces that at a higher threshold. Both views therefore have *two* labels to keep honest — the
status message (`PagedResult.describeCount()`) and the separate result-count label, which in
annotation-explore is driven by a `searchResults` list listener and in dataset-explore by a
`resultCountMessage` property. A count label bound directly to `records.size()` would read "5000
results" beside a status message saying the result was capped.

`accumulatePages()` is static and takes its pages through functions so the loop is unit-testable
without a service ecosystem (`DpApplicationPagingTest`), the same reasoning as `emptyToNull()` and
`timestampFromInstant()`. The edge cases worth keeping covered: a cap reached exactly on a page
boundary *with* a next page (truncated) versus a result that exactly fills the cap with no next
page (not truncated), and a server returning a token alongside an empty page.

**Sample Status API wrappers** on `DpApplication` (the client wrappers themselves already exist on
`AnnotationClient`, added by dp-service #239 — no dp-service work is needed to use them):
- `saveSampleStatuses(frames, source, modifiedBy)` — batch upsert. Upsert is per individual status
  keyed by `(pvName, timestamp, domain, layer)` and is a **full replace**: re-saving a key with an
  empty confidence/reasons list clears the stored values.
- `querySampleStatuses(beginTime, endTime, pvNames, domains, layers, limit, pageToken)` — unary,
  resumable paging. Takes `Instant` at this boundary and converts inward via `timestampFromInstant()`,
  since `QuerySampleStatusesParams` takes protobuf `Timestamp` (unlike `queryTable()`). **Bucket
  selection is a `TimeRange` overlap test and boundary buckets are returned whole**, so a returned
  bucket may contain statuses outside the requested range — callers counting or displaying
  individual statuses must account for this. Currently has no UI caller; it is groundwork for the
  query view in #39.

`deleteSampleStatuses()` and `querySampleStatusesStream()` are deliberately **not** wrapped yet:
which of unary vs. streaming is wanted depends on the query interface designed in #39, and delete
has a wildcard (an empty `pvNames` deletes the layer's statuses for ALL PVs in the range) that
deserves its own consideration.

**Parameter normalization helpers** (package-private statics on `DpApplication`, unit-tested in `DpApplicationParamsTest`):
- `emptyToNull(String / List / Map)` — a field left blank in the UI is omitted from the request rather than sent as an empty string or empty collection
- `setIfPresent()` / `setIfBothPresent()` — conditional criterion setters for the query wrappers
- `timestampFromInstant(Instant)` — converts to the protobuf `Timestamp`, **mapping null to null**. The null case is the point: `Timestamp` is a message field with real protobuf field presence, so an optional time left unset must reach the request builder as null. A zero-valued `Timestamp` would mark the field present and describe a time at the epoch — for `SaveConfigurationActivationRequest.endTime` that is the difference between an open-ended activation and one that ended in 1970. `TimestampUtility.getTimestampFromInstant()` does the non-null conversion but throws on null, so it cannot be called directly for an optional field.

Prefer these over re-implementing the conversions inline: they are extracted precisely so they are testable without a service ecosystem.

### Debugging Component Data Issues
When tags/attributes don't appear in the database:

1. **Check API calls**: Log the actual parameters being passed to `registerProvider()` and `ingestImportedData()`
2. **Verify component injection**: Ensure ViewModel has non-null component references
3. **Trace data flow**: Components → ViewModel API methods → DpApplication → gRPC services
4. **Common symptoms**: Empty lists/maps in API parameters despite UI having data
5. **Root cause**: Usually accessing `viewModel.getTags()` instead of `component.getTags()`

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

**ColumnMetadataComponent** (`src/main/java/com/ospreydcs/dp/gui/component/ColumnMetadataComponent.java`)
- Reusable component for the Column Metadata section
- Contains provenance source/process fields, column tags, and column attributes
- Uses embedded TagsListComponent and AttributesListComponent
- Property binding: `provenanceSourceProperty()`, `provenanceProcessProperty()`
- Data access: `getColumnMetadata()` returns a built protobuf `ColumnMetadata`, or null when nothing
  has been entered so columns are sent without a metadata field
- Unset provenance fields are omitted rather than sent as empty strings, per the `ColumnProvenance`
  contract in `common.proto`
- Lifecycle method: `clearColumnMetadata()`
- Values apply uniformly to every column in the DataFrame(s) produced by the containing view

**QueryPvsComponent** (`src/main/java/com/ospreydcs/dp/gui/component/QueryPvsComponent.java`)
- Reusable component for PV list management with individual remove buttons
- Displays current PV selection with custom ListCell containing trash can buttons (🗑️)
- Auto-syncs with DpApplication global PV state
- Navigation integration: "Edit Query" button to return to data-explore view
- Used in pv-explore view for displaying and managing selected PVs

**CalculationFrameDetailsDialogController** (`src/main/java/com/ospreydcs/dp/gui/component/CalculationFrameDetailsDialogController.java`)
- Reusable dialog for displaying detailed calculation frame information
- Shows frame name, timestamps count, data columns with sample values
- Static factory method: `showDialog(DataFrameDetails, Stage)` for easy usage
- Formats timestamps using proper epoch seconds/nanoseconds conversion
- Handles all DataValue types (string, numeric, boolean) with appropriate formatting
- Used in both data-explore Annotation Builder and annotation-explore views
- Provides consistent calculation frame viewing experience across the application

**SubscriptionDetailsComponent** (`src/main/java/com/ospreydcs/dp/gui/component/SubscriptionDetailsComponent.java`)
- Reusable component for data event subscription management
- Left panel: ListView displaying subscriptions with context menu removal
- Right panel: Auto-submission form for adding PV name, trigger condition, trigger value
- User-friendly trigger condition display ("Equal to (=)", "Greater than (>)", etc.)
- Auto-submission on Enter/Tab key press with focus management for rapid entry
- Context menu "Remove" option for subscription management
- Data access: `getSubscriptions()` method following Critical Integration Pattern
- Programmatically created to avoid FXML injection issues with custom components

**Critical Integration Pattern Implementation:**
When using reusable components, you MUST inject component references into ViewModels:

```java
// In Controller.initialize()
viewModel.setProviderDetailsComponent(providerDetailsComponent);
viewModel.setColumnMetadataComponent(columnMetadataComponent);

// In ViewModel - add component references
private ProviderDetailsComponent providerDetailsComponent;
private ColumnMetadataComponent columnMetadataComponent;

// In API calls - get data from component instances
var tags = providerDetailsComponent.getProviderTags();
var attributes = providerDetailsComponent.getProviderAttributes();
var columnMetadata = columnMetadataComponent.getColumnMetadata();

// NEVER do this - ViewModel properties stay empty
var tags = viewModel.getTags(); // Empty!
var attributes = viewModel.getAttributes(); // Empty!
```

**Component Binding Pattern:**
```java
// ✅ CORRECT - Only bind simple properties, let components manage complex data
providerDetailsComponent.providerNameProperty().bindBidirectional(viewModel.providerNameProperty());
// Note: Tags and attributes are managed internally by components

// ❌ WRONG - Never try to populate components from ViewModel collections
providerDetailsComponent.setProviderTags(viewModel.getProviderTags()); // Empty!
```

### Data Event Subscription Integration Pattern
**Component Creation (Programmatic)**:
```java
// Avoid FXML injection issues by creating programmatically
private void createSubscriptionDetailsComponent() {
    subscriptionDetailsComponent = new SubscriptionDetailsComponent();
    subscriptionDetailsPlaceholder.getChildren().add(subscriptionDetailsComponent);
}
```

**Data Access Pattern**:
```java
// ✅ CORRECT - Access subscription data from component for data generation
List<SubscribeDataEventDetail> subscriptions = subscriptionDetailsComponent.getSubscriptions();
dpApplication.generateAndIngestData(..., subscriptions);

// ✅ CORRECT - Access subscription data from component for data import
List<SubscribeDataEventDetail> subscriptions = subscriptionDetailsComponent != null ? 
    subscriptionDetailsComponent.getSubscriptions() : new ArrayList<>();
dpApplication.ingestImportedData(..., new ArrayList<>(subscriptions));

// ❌ WRONG - ViewModel properties remain empty
List<SubscribeDataEventDetail> subscriptions = viewModel.getSubscriptions(); // Empty!
```

**Complete Integration Checklist**:
1. Add FXML placeholder: `<VBox fx:id="subscriptionDetailsPlaceholder" />`
2. Import SubscriptionDetailsComponent in Controller
3. Add component field and placeholder field to Controller
4. Create component programmatically in `initialize()` method
5. Inject component reference into ViewModel
6. Add component setter method to ViewModel
7. Update API call methods to use component data instead of empty lists
8. Test auto-submission form and context menu functionality

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

### Custom ListCell Implementation Pattern
**For ListViews with individual item actions (like remove buttons):**
```java
// Custom ListCell class
private class PvNameListCell extends ListCell<String> {
    private HBox content;
    private Label itemLabel;
    private Button actionButton;

    public PvNameListCell() {
        super();
        content = new HBox();
        content.setSpacing(5);
        content.setPadding(new Insets(2, 5, 2, 5));
        
        itemLabel = new Label();
        itemLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(itemLabel, Priority.ALWAYS);
        
        actionButton = new Button("🗑️");
        actionButton.getStyleClass().addAll("btn", "btn-danger", "btn-xs");
        actionButton.setOnAction(e -> {
            String item = getItem();
            if (item != null) {
                // Handle action
            }
        });
        
        content.getChildren().addAll(itemLabel, actionButton);
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        
        if (empty || item == null) {
            setGraphic(null);
            setText(null);
        } else {
            itemLabel.setText(item);
            setGraphic(content);
            setText(null); // Important: clear default text
        }
    }
}

// Critical timing for cell factory setup
private void setupCustomListCell() {
    // Set items first
    listView.setItems(viewModel.getItemList());
    
    // Then set cell factory - timing is critical!
    javafx.application.Platform.runLater(() -> {
        listView.setCellFactory(listView -> new PvNameListCell());
        listView.refresh();
        logger.debug("Custom cell factory applied");
    });
}
```

**Critical implementation notes:**
- Set cell factory AFTER setting ListView items to prevent override
- Use `Platform.runLater()` to ensure proper JavaFX thread timing
- Call `refresh()` to force ListView to recreate cells with new factory
- Always call `setText(null)` when using custom graphics to avoid double display
- Apply during global state initialization when items are actually populated

### Custom TableCell with Hyperlinks Pattern
**For TableView columns with interactive hyperlinks (like PV names):**
```java
// Custom TableCell for hyperlink columns
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
            
            // Get table row for individual data access
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
                        // Action for clicking hyperlink
                        viewModel.addPvNameToQuery(pvName);
                    });
                    
                    content.getChildren().add(pvLink);
                    first = false;
                }
            }
            
            setGraphic(content);
            setText(null); // Important: clear default text
        }
    }
}

// Apply to TableColumn
pvNamesColumn.setCellFactory(column -> new PvNamesTableCell());
```

**Key patterns:**
- Use `getTableRow().getItem()` to access the full row data from within TableCell
- Handle comma separation manually for multiple hyperlinks
- Clear default text with `setText(null)` when using custom graphics
- Apply appropriate CSS classes for styling consistency

### Cross-View Navigation with Automatic Search Pattern
**For implementing navigation between related views with automatic search execution:**
```java
// 1. In source view Controller - create navigation method
private void navigateToTargetView(String searchParameter) {
    if (mainController != null) {
        mainController.navigateToTargetViewWithSearch(searchParameter);
    }
}

// 2. In MainController - add specialized navigation method
public void navigateToTargetViewWithSearch(String searchParameter) {
    try {
        // Load target view
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/target-view.fxml"));
        contentPane.getChildren().clear();
        contentPane.getChildren().add(loader.load());
        
        // Inject dependencies
        TargetViewController controller = (TargetViewController) loader.getController();
        controller.setDpApplication(dpApplication);
        controller.setPrimaryStage(primaryStage);
        controller.setMainController(this);
        controller.initializeView();
        
        // Execute automatic search with parameter
        controller.executeAutomaticSearch(searchParameter);
        
    } catch (Exception e) {
        logger.error("Failed to navigate with automatic search", e);
    }
}

// 3. In target view Controller - add automatic search method
public void executeAutomaticSearch(String searchParameter) {
    // Pre-populate search fields
    searchField.setText(searchParameter);
    
    // Clear other fields for focused search
    otherField1.clear();
    otherField2.clear();
    
    // Execute search via ViewModel
    viewModel.executeSearch();
}

// 4. In hyperlink TableCell - call navigation with data from table row
hyperlink.setOnAction(e -> {
    TableRowType tableRow = getTableRow().getItem();
    if (tableRow != null) {
        // Use specific data from row for search parameter
        String searchParam = tableRow.getSpecificFieldForSearch();
        navigationMethod(searchParam);
    }
});
```

**Implementation Notes:**
- Navigation preserves dependency injection patterns
- Search parameter comes from protobuf data in table rows
- Automatic search clears non-relevant fields for focused results
- Error handling prevents navigation failures from breaking application state
- Status bar provides user feedback during navigation process

**Usage Examples:**
- PV Explore → Provider Explore: Click provider name hyperlink navigates with provider ID search
- Provider Explore → PV Explore: Could be implemented for reverse navigation
- Dataset Builder → Data Explorer: Navigation with pre-populated PV list and time ranges
- Dataset Explore → Dataset Builder: Click Dataset ID hyperlink navigates with automatic dataset loading

### Dataset Loading Pattern
**For implementing dataset loading from external sources into form components:**
```java
// 1. In ViewModel - add dataset loading method
public void loadFromDataSet(com.ospreydcs.dp.grpc.v1.annotation.DataSet dataset) {
    logger.debug("Loading dataset into builder: {}", dataset.getId());
    
    // Clear existing data first
    resetDataset();
    
    // Populate form fields
    setDatasetId(dataset.getId());
    setDatasetName(dataset.getName());
    setDatasetDescription(dataset.getDescription());
    
    // Convert protobuf DataBlocks to UI objects
    dataBlocks.clear();
    for (com.ospreydcs.dp.grpc.v1.annotation.DataBlock dataBlock : dataset.getDataBlocksList()) {
        DataBlockDetail blockDetail = convertDataBlockToDetail(dataBlock);
        dataBlocks.add(blockDetail);
    }
    
    statusMessage.set("Dataset loaded: " + dataset.getName());
}

// 2. In Controller - add background loading with tab switching
public void loadDatasetIntoBuilder(String datasetId) {
    // Switch to target tab first
    javafx.application.Platform.runLater(() -> {
        tabPane.getSelectionModel().select(targetTabIndex);
    });
    
    // Query dataset in background task
    javafx.concurrent.Task<DataSetType> loadTask = new javafx.concurrent.Task<DataSetType>() {
        @Override
        protected DataSetType call() throws Exception {
            ApiResult result = dpApplication.queryDataSets(datasetId, null, null, null);
            if (result.resultStatus.isError) {
                throw new RuntimeException(result.resultStatus.msg);
            }
            return result.dataSets.get(0);
        }
    };
    
    loadTask.setOnSucceeded(e -> {
        javafx.application.Platform.runLater(() -> {
            viewModel.loadFromDataSet(loadTask.getValue());
        });
    });
    
    Thread loadThread = new Thread(loadTask);
    loadThread.setDaemon(true);
    loadThread.start();
}

// 3. Protobuf to UI Object Conversion
private DataBlockDetail convertDataBlockToDetail(DataBlock protobufDataBlock) {
    java.time.Instant beginTime = java.time.Instant.ofEpochSecond(
        protobufDataBlock.getBeginTime().getEpochSeconds(),
        protobufDataBlock.getBeginTime().getNanoseconds()
    );
    
    java.time.Instant endTime = java.time.Instant.ofEpochSecond(
        protobufDataBlock.getEndTime().getEpochSeconds(),
        protobufDataBlock.getEndTime().getNanoseconds()
    );
    
    List<String> pvNames = new ArrayList<>(protobufDataBlock.getPvNamesList());
    return new DataBlockDetail(pvNames, beginTime, endTime);
}
```

**Key Implementation Notes:**
- Use background tasks for API queries to prevent UI blocking
- Switch tabs before starting background operations for immediate user feedback
- Convert protobuf Timestamp objects to Java Instant using epoch seconds and nanoseconds
- Always clear existing data before loading new dataset to prevent data mixing
- Provide status feedback throughout the loading process
- Handle API errors gracefully with user-friendly error messages

### Reusable Component FXML Loading Patterns
**Correct Component Constructor Pattern**:
```java
public MyComponent() {
    // ✅ CORRECT - Load FXML and set controller, then copy properties
    FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/components/my-component.fxml"));
    fxmlLoader.setController(this);
    
    VBox root = fxmlLoader.load();
    this.getChildren().setAll(root.getChildren());
    this.setSpacing(root.getSpacing());
    this.setPadding(root.getPadding());
    this.getStyleClass().setAll(root.getStyleClass());
}
```

**Common FXML Loading Errors**:
- **"Root value already specified"**: Caused by using `setRoot(this)` - remove this call
- **Type injection mismatches**: Use programmatic creation instead of `fx:include` for custom components
- **Timing issues**: Create components before UI binding, inject after component creation