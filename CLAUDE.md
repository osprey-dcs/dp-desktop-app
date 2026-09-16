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
Explore → Data, PV Statistics, PV Metadata, Providers, Datasets, Annotations, Machine Configurations, Sample Statuses, Data Events
Tools → Delete Demo Data
```

**Menu Item Logic:**
- **Generate**: Conditionally enabled (disabled for remote production connections to prevent fake data contamination)
- **Import**: Always enabled (real data import is safe for all environments)
- **Metadata > PV**: Always enabled (creating PV metadata does not depend on session data ingestion); navigates to pv-metadata view for creating/updating PV metadata records
- **Metadata > Machine Configuration**: Always enabled (same rationale); navigates to machine-configuration view for creating machine configuration records and their activation intervals
- **Explore menu items**: Enabled after data ingestion (in-process mode) or immediately (remote production mode), because they browse metadata derived by aggregation over ingested documents and have nothing to show until data exists
- **PV Statistics**: Navigate to pv-explore view for PV discovery and management — the *derived* per-PV statistics (data type, first/last timestamp, sample period), aggregated over ingested buckets
- **PV Metadata**: Navigate to pv-metadata-explore view for searching *curated* PV metadata records (aliases, tags, attributes, description) and loading one for editing
- **Providers**: Navigate to provider-explore view for provider discovery and management
- **Datasets**: Navigate to dataset-explore view for dataset discovery and Dataset Builder navigation
- **Annotations**: Navigate to annotation-explore view for annotation discovery and management
- **Machine Configurations**: Navigate to configuration-explore view for searching configuration records and their activation intervals, and loading a configuration for editing
- **Sample Statuses**: Navigate to sample-status-explore view for querying stored sample statuses
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
- ✅ Machine Configuration view for creating configuration records and activation intervals via saveConfiguration() / saveConfigurationActivation(), with getConfiguration() and getConfigurationActivationById() overwrite warnings
- ✅ Demo sample status generation in the data-generation view via saveSampleStatuses(), with the read-back now wired to the Sample Status Explore view via querySampleStatusBuckets()
- ✅ PV Metadata Explore view with search by name/alias (contains, prefix or exact), tags and attributes via queryPvMetadata(), and load-for-edit into the PV Metadata editor
- ✅ Modal PV selector in the Query Editor (Name list / Name pattern / Metadata criteria) driving the V2 `PvSelector`, with the PV name list preserved as the default and identity path
- ✅ Modal query filters in the Query Editor (machine configuration activations, sample status) driving the V2 `configurationSelector` and `sampleStatusSelector`, both off by default
- ✅ Configuration Explore view with two independent searches (configurations and activations) via queryConfigurations() / queryConfigurationActivations(), and load-for-edit into the Machine Configuration editor

## GUI Architecture

### MVVM Implementation
The application follows the Model-View-ViewModel pattern:

**Controllers** (`src/main/java/com/ospreydcs/dp/gui/*Controller.java`)
- Handle FXML UI binding and user interactions
- Delegate business logic to ViewModels
- Example: `DataGenerationController`, `DataExploreController`, `DataImportController`, `PvExploreController`, `ProviderExploreController`, `DatasetExploreController`, `AnnotationExploreController`, `DataEventExploreController`, `PvMetadataController`, `PvMetadataExploreController`, `SampleStatusExploreController`, `ConfigurationExploreController`, `MachineConfigurationController`, `MainController`

**ViewModels** (`src/main/java/com/ospreydcs/dp/gui/*ViewModel.java`)
- Contain UI state and business logic
- Use JavaFX properties for data binding
- Example: `DataGenerationViewModel`, `DataExploreViewModel`, `DataImportViewModel`, `PvExploreViewModel`, `ProviderExploreViewModel`, `DatasetExploreViewModel`, `AnnotationExploreViewModel`, `DataEventExploreViewModel`, `PvMetadataViewModel`, `PvMetadataExploreViewModel`, `SampleStatusExploreViewModel`, `ConfigurationExploreViewModel`, `MachineConfigurationViewModel`, `MainViewModel`

**Views** (`src/main/resources/fxml/*.fxml`)
- FXML layout definitions
- Styled with BootstrapFX and custom CSS
- Example: `data-generation.fxml`, `data-explore.fxml`, `data-import.fxml`, `pv-explore.fxml`, `provider-explore.fxml`, `dataset-explore.fxml`, `annotation-explore.fxml`, `data-event-explore.fxml`, `pv-metadata.fxml`, `pv-metadata-explore.fxml`, `sample-status-explore.fxml`, `configuration-explore.fxml`, `machine-configuration.fxml`, `main-window.fxml`

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

**The query runs on Query API V2 (`querySamples`), migrated from `queryTable` in #39 task 6.**
`DpApplication.querySamples()` returns ONE page; `DataExploreViewModel.executeSamplesQuery()` drives
the `nextPageToken` loop and publishes each page as it arrives. That per-page display is deliberate
and is why this wrapper does not use `accumulatePages()` like every other paged wrapper on
`DpApplication`: accumulating would withhold every row until the last page landed, where the
retired code displayed incrementally.

**The query chooses its PVs three ways, via the modal PV selector** (#39 task 6 part 2). The
"Select PVs..." button opens `PvSelectorDialogController`, which edits a `PvSelection` — the app's
counterpart to the client's sealed `PvSelectorParams` — covering the three arms of the V2
`PvSelector`: **Name list** (the default), **Name pattern** (a regex, not a glob) and **Metadata
criteria** (resolved against curated `PvMetadata`).

**The name list stays the identity path, and the other two modes never write into it.** That
`ObservableList<String>` is wired into six flows — global-state restore, `populateFromDataBlock`,
`QueryPvsComponent`, `DpApplication.get/setPvNames()`, the data-event-explore hyperlink and
`DataExploreController`'s PV cell — every one of them a *name-list* flow. A selector that resolved a
pattern or a metadata query into that list would make a query-scoped choice silently rewrite state
five other views read and write. So `PvSelection` does not even hold the names: name-list mode reads
the live list at `toSelectorParams()` time, which also means a PV added after the dialog was last
closed is still queried.

**The PV ListView stays visible in every mode, so the Query Editor states which mode is active.**
The list is populated in all three modes because it is shared state, so without the summary label a
pattern query would run beside a list of PVs it had nothing to do with, and that list would read as
the query's scope. The label says so explicitly ("the list below is not used by this query").

**Add to Dataset is refused for a pattern or metadata selection.** A `DataBlock` *is* a PV name
list, and this view does not resolve a selector client-side. The refusal is the point: the name list
is still populated in those modes, so an unguarded read would build a data block out of PVs the
query never covered and save it with no error at all — the same silent-wrong-data shape as the
Annotation Builder's calculations loss.

**An empty metadata query is NOT an error — it matches every PV in the archive.** This is the
opposite of `configurationSelector`, whose empty form the server rejects, and it is the sharpest
edge in the selector. A user who opens the metadata tab and types nothing gets a whole-archive scan
that *looks* like a filter. Nothing downstream complains, so `PvSelection.describe()` names the case
in words and the dialog shows a live warning while the fields are still being edited. Query
validation deliberately does **not** refuse it: inventing a client-side rule the server does not
have would make the app disagree with the service about what is a legal query. A whole-archive query
is instead rejected only if it resolves past `maxResolvedPvCount`.

**A blank pattern IS refused**, because the server rejects one and a blank field is an unfilled
control rather than an intent to match nothing. It is the only client-checkable rule of the three.

**Two optional filters narrow the query further** (#39 task 6 part 3), reached from the
"Query Filters..." button and edited in one modal (`QueryFiltersDialogController`). Both default to
off, and they narrow **different axes**, composing by intersection: `ConfigurationFilter` restricts
the TIME axis to the intervals during which matching machine configuration activations were live,
and `SampleStatusFilter` then drops individual samples from whatever survives. A status attached to
a sample outside the activation intervals has no effect, because that sample is already gone.

**An empty configuration filter must send NO selector, never an empty one — the exact inverse of
the empty metadata PV selector.** The server *accepts* an empty metadata query (whole archive) and
*rejects* an empty configuration selector ("configurationSelector.criteria list must not be empty").
Worse, the client wrapper reads the two empty forms differently on purpose: null or an empty list
means "no restriction asked for" and drops the selector, while a **non-empty** list from which no
criterion survives emits the empty selector for the server to reject. That asymmetry is the #243
rule with its sign flipped — dropping a criterion the user filled in would *widen* the query from
"only while configuration X was active" to the whole time range, handing back more data than they
asked for. `ConfigurationFilter.toCriteria()` therefore returns **null**, not `List.of()`, and the
`querySamples()` call site must not substitute one.

**Each configuration criterion sets exactly one arm.** The proto criterion is a oneof, and the
client's builder returns null for a multi-arm criterion, which `buildQuerySpec` turns into a
rejected request rather than a silently preferred arm. The filter emits **one criterion per
populated field**, which is also the semantics a user expects: criteria AND, values within one OR.

**The sample status mode is not a polarity switch, and the difference is in the UNLABELED samples.**
A status labels a sample only by exact `(pvName, timestamp)` equality at nanosecond precision, so
most samples are unlabeled. `INCLUDE` returns matching samples and **excludes** unlabeled ones;
`EXCLUDE` drops matching samples and **returns** unlabeled ones. The two are therefore not
complements over a partially-labeled archive, and choosing wrong is silent: `INCLUDE` over a sparsely
labeled PV returns a nearly empty table that reads as "no data in this window" rather than as
"almost nothing here is labeled". `SampleStatusFilter.Mode`'s labels and `describe()` both state the
unlabeled behavior in words for exactly this reason.

**A filtered-out sample becomes a missing VALUE, not a missing row.** The server blanks it at its
`(PV, timestamp)` position; a timestamp disappears only when every selected PV is filtered out at
it. Those blanks render exactly like genuinely-missing V2 values, which is why the summary label is
load-bearing — nothing else in the view says a filter was applied.

**Only the status filter has a client-checkable rule**: the server rejects a blank domain. The
configuration filter has none, because its inactive form is *sending no selector*, so there is no
incomplete state for it to be in — adding a rule there would disable Submit for a query the server
would happily run. A ticked-but-empty configuration filter is refused **in the dialog** (it is the
empty-selector case), not by query validation.

**Each filter is gated by its own checkbox rather than inferred from its fields**, because
inferring would make the two behave oppositely for the same gesture — all-blank means "no
restriction" for one and "rejected request" for the other. An unticked box drops its filter
regardless of what the fields still hold, so criteria left by a previous visit cannot leak into a
filter the user turned off.

**Status codes are refused when they do not parse, never dropped.** A dropped code silently widens
the filter — in `INCLUDE` mode "samples with code 2" becomes "samples labeled at all" — and returns
a plausible table, so nothing downstream would say the typed code never reached the server.

**Add to Dataset is also refused for an active configuration filter**, on the time axis rather than
the PV axis. A `DataBlock` is a **contiguous** `[beginTime, endTime)`, while the filter resolves to
the union of matching activation intervals intersected with that range — normally fragmented, always
narrower. The block built here carries the OUTER range, so the saved dataset would claim intervals
the query deliberately excluded. The *status* filter is deliberately not refused: it blanks samples
inside the block rather than changing which interval the block covers.

**A configuration selector matching no activations is a well-formed empty result, not a rejection.**
The server distinguishes it from a malformed selector on purpose, so a mis-built selector is never
indistinguishable from "no data in this window".

**Each mode reads only its own controls.** Text left in the pattern field by an earlier visit is
ignored while metadata is selected, rather than being carried into a selection the user abandoned —
the same reasoning that clears the machine-configuration activation spinners rather than reusing
whatever time they hold.

`PvMetadataExploreViewModel.textMatch()` and `parseCommaSeparatedList()` are **public** and reused by
the dialog rather than reimplemented. Two copies would be free to drift on exactly the blank-field
handling those methods exist to pin down: a blank field emitted as an empty-list criterion is
rejected by the server, while a blank *prefix* compiles to a regex matching everything.

**The 1-minute interval chopping is gone, not relocated.** It existed only to keep each response
under the gRPC message size limit by guessing a window small enough to fit. The server now bounds a
page itself — by a row count and an outgoing byte budget, whichever trips first — and returns a
resume token. Guessing was wrong in both directions anyway: a minute of a fast PV could still
overflow, while a minute of a slow one cost a round trip to return nothing.

**The timestamp column is synthesized, not received.** A V2 `ColumnTable` has no timestamp column —
the axis lives only in `timestampList` — whereas the V1 ROW_MAP table carried `"timestamp"` as an
ordinary column. Both the results table and the chart locate the time axis by that literal name, so
`columnNamesOf()` prepends it rather than changing them. The name is
`DataExploreViewModel.TIMESTAMP_COLUMN_NAME`, referenced by all four consumer sites in
`DataExploreController`, so a rename cannot silently orphan the chart — whose failure mode is a
logged "No timestamp column found" and an empty chart beside a table that still renders.

**A missing value renders BLANK, not "N/A".** V2 encodes "this PV had no sample at this timestamp"
as an **unset** `DataValue` oneof — a real distinction the V1 path could not make, where a column
absent from a row map and a value the decoder did not recognize both became the string `"N/A"`.
Blank now means genuinely missing; anything else in a cell means a decode gap.

**`uint32` / `uint64` are widened rather than rendered signed.** They are unsigned on the wire and
signed in Java, so the signed accessors would display a large unsigned reading as a negative number
— a wrong value that looks like a real one.

The two are **deliberately asymmetric**, and it is not an oversight: `uint32` widens into a `long`
without loss and stays a `Number`, but `uint64` has no Java integral type that can hold its upper
half, so it is rendered as an unsigned decimal **String**. Both alternatives are worse — the signed
long displays a large reading as negative, and a `double` silently rounds past 2^53, turning an
exact archived reading into a nearby wrong one. The chart's `parseNumericValue()` still parses the
string, so such a column plots (as a double, with that rounding) while the **table** — which is what
a reading is actually read from — keeps every digit exact.

**The row count comes from the timestamp axis, never from a column.** The server guarantees one
`DataValue` per column per timestamp, but reading the count from a column would silently truncate
the whole page if that guarantee broke, whereas over-indexing a short column is caught per cell.

**Three server-side failures are surfaced verbatim and never retried**: a single row whose values
across all PVs exceed the byte budget (narrowing the *time range* cannot help — only fewer PVs);
a non-scalar PV, rejected **mid-assembly** rather than pre-flight (dp-service #194 is open), so a
non-scalar PV with no buckets in the window passes silently and the same PV set can succeed on one
page and reject on the next, **after rows are already displayed**; and a selector resolving past
`maxResolvedPvCount`. `executeSamplesQuery()` reports the rows already kept when a later page fails,
rather than implying the table is empty.

**A page token is never carried across queries.** It encodes a position only, and nothing binds it
to the `QuerySpec` that produced it beyond a coarse bucket-vs-sample kind check, so replaying one
against an edited time range or PV list yields a well-formed but **semantically wrong** result
rather than an error. Every query starts from null.

**The paging loop is bounded twice, and neither bound is the server's.** It stops at
`DataExploreViewModel.MAX_DISPLAYED_ROWS` (50,000), and it stops when the task is cancelled. The
server's page bound cannot stand in for either: it bounds a single *page* and then hands back a
resume token, so following tokens to exhaustion is an unbounded read however small each page is.

**The V2 selector arms are what made that reachable.** The retired V1 path was bounded in practice
because the only way to choose PVs was to type them into the name list — the row count was the
user's own PV list times their own time range. A pattern or metadata selection removes that implicit
ceiling, and an all-empty metadata query is *not* rejected: it matches every PV in the archive. The
server's `maxResolvedPvCount` catches only the extreme; a selector resolving to just under it over a
wide window is accepted, and would otherwise accumulate without end into an `ObservableList` on the
FX thread.

**Rows are capped, not pages**, because a page is a server-side accounting unit whose size the
client does not control. The page crossing the boundary is **trimmed rather than dropped whole** —
the rows before the boundary are as real as any other.

**`cancel()` now actually cancels.** It previously set a status message and nothing else, so a query
in flight kept following resume tokens after the user asked it to stop, and the message claimed
otherwise. Cancellation is polled **between pages**: a page is one unary round trip that cannot be
interrupted once issued, so the finest honest granularity is per page, and the rows from a page
already received are kept rather than discarded. The Query Editor carries a **Stop Query** button,
visible only while a query runs and distinct from **Cancel**, which leaves the view entirely.

**Both bounds, and a mid-query failure, set `resultsTruncated`** — and the completion message is
built by `describeResult()`, which never states a bare count for a partial result. A capped table
reported as a total is indistinguishable, from the table alone, from a query that genuinely had
nothing more to return; that is the same defect transparent paging exists to prevent.

**The outcome is RETURNED from `call()` and applied in `setOnSucceeded`, never published from inside
the loop via `Platform.runLater`** — and applied *before* `isQuerying` clears. Both halves are the
D-2 ordering rule (see the explore-view search vocabulary above) in this view: `setOnSucceeded` runs
on the FX thread and therefore runs *before* a block queued from the background thread, and setting
a JavaFX property notifies its listeners synchronously, so anything watching `isQuerying` — the
progress indicator, the row-count label — would otherwise read a stale count and a stale truncation
flag. `DataExploreQueryBoundsTest` observes the count at the moment the flag clears rather than
asserting the final value, which would pass against both the fixed and the broken version.

**The chart rebuild is coalesced, not run per page.** `updateChart()` re-reads every accumulated row
and rebuilds every series, so running it once per arriving page makes the total work quadratic in
the number of pages, on the FX thread, while rows are still being added. It cannot be made
incremental instead: the dynamic sample interval is computed from the *total* row count, so
appending one page's points to series sampled for a smaller total would mix two sampling rates in
one line. `requestChartUpdate()` collapses the rebuilds queued within a pulse into one, against the
fully accumulated data.

**`limit` is deliberately left unset.** The server does not `.limit()` the Mongo cursor — it drains
buckets until the byte budget trips and then truncates — so a small limit causes repeated near-full
re-scans without reducing server work. Unset selects the server default; an over-maximum value is
silently clamped, not rejected. None of those numbers are hardcoded, since all are
environment-overridable server-side.

**`useSerializedColumns` stays off.** Serialized columns cannot be merged across pages, so enabling
it would require checking `serializedColumnsFragmented` before reading the table; a consumer that
ignored the flag would get silently misaligned columns rather than an error.

**The in-process channel's `maxInboundMessageSize` is raised above gRPC's 4 MB default**
(`InprocessServiceBase`). The server's own per-page budget is 4,096,000 bytes and is measured on
sample **values only** — excluding the timestamp list, column framing, names and the response
envelope — and accounts per whole bucket, so a page can overshoot by up to one bucket. A client at
the default has no headroom, and the failure is a `RESOURCE_EXHAUSTED` on the overshooting page with
nothing identifying the cause.

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

**pv-explore gained a search status row in T2b.** It previously had only `resultsStatusLabel`, so
search progress and the result summary competed for one label and the search state was overwritten by
the result. The view now carries `searchStatusLabel`, `resultCountLabel` and `searchProgressIndicator`
like the other three.

**The "Add Selected" button state is driven by exactly one listener, registered in
`bindUIToViewModel()`.** `resultsTable.setItems(viewModel.getSearchResults())` makes the table's
item list and the ViewModel's `searchResults` the same `ObservableList` instance, so a listener
registered on either sees every change. `onSearch()` deliberately registers nothing: it previously
added a second listener on each click, which was never removed, so every search left another
permanently-retained copy re-doing work the first listener already did. That copy was also attached
*after* `searchPvMetadata()` started its background task, so it could never observe the results of
the search that registered it — the visible behavior was correct only because the
`bindUIToViewModel()` listener was doing the job all along.

**The explore views share one search vocabulary** (normalized by #39 T2b). A new explore view
should use these names rather than inventing another spelling:

| Concept | Property | Bound to |
|---|---|---|
| status beside the search controls | `searchStatusMessage` | `searchStatusLabel` |
| status beside the results table | `statusMessage` | `resultsStatusLabel` |
| result count as displayed | `resultCountMessage` | `resultCountLabel` |
| a search is running | `searchInProgress` | progress indicator + disabled search button |

`statusMessage` keeps the app-wide name rather than being folded into `searchStatusMessage`: eleven
other view models expose `statusMessageProperty()`, and four controllers forward it to
`MainViewModel.updateStatus()` to drive the application status bar. The two status properties are
**two distinct labels**, not two spellings of one — every explore FXML declares both.

**The vocabulary scopes to a SEARCH, not to a view.** `ConfigurationExploreViewModel` hosts two
independent searches and therefore carries two of each property, prefixed by search
(`configurationSearchInProgress` / `activationSearchInProgress`, and so on). Sharing one set between
them would let either search clear the other's results or overwrite its count.

`resultCountMessage` is a `String` in every view, never an `IntegerProperty`. A count label bound
to a bare int cannot say "first N of more", so it would read "5000 results" beside a status message
saying the query was capped — the exact dishonesty transparent paging exists to prevent.

**Clear supersedes an in-flight search.** Each search carries a generation counter, incremented by
both the search and its Clear, and a completion handler whose generation no longer matches drops its
result. Without it, Clear pressed during a slow search empties the table and sets "Search cleared",
and then the old query's rows arrive and repopulate it — which reads as a bug in Clear rather than in
the search. The Search button is disabled during a search but Clear deliberately is not: abandoning a
slow search is exactly when it is reached for.

**The counter is per SEARCH, not per view**, for the same reason the T2b vocabulary is:
`ConfigurationExploreViewModel` hosts two independent searches, so one shared counter would let
either Clear silently discard the other search's results.

**Search results are returned from `call()` and published in `setOnSucceeded`, never from a
`Platform.runLater` inside the task.** `setOnSucceeded` and `setOnFailed` already run on the FX
thread. Publishing from inside `call()` via `runLater` does not order anything — it *queues* the
update behind whatever is already pending, including the handler's own `searchInProgress` reset. That
was a live defect (D-2): Provider and Dataset mutated their result lists from a queued block and then
read the count in `setOnSucceeded`, which runs first, so the completion log always reported the
pre-search count. `ExploreViewModelSearchTest` pins the ordering by observing state from a listener on
`searchInProgress` at the moment it clears — asserting on the final table contents instead would pass
against both the fixed and broken versions.

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

### Configuration Explore Workflow (Implemented)
1. **Navigation**: `Explore > Machine Configurations` (enabled after ingestion, like the other explore views)
2. **Two tabs, two searches**: Configurations and Activations, each with its own criteria, progress indicator and status labels
3. **Configuration criteria**: name (Contains / Starts-with / Exact), category, parent, tags, attribute key + optional value
4. **Activation criteria**: configuration names, activation ids, category, tags, attributes, plus two optional time criteria
5. **Search Execution**: background `Task`s calling `DpApplication.queryConfigurations()` / `queryConfigurationActivations()`, both following `nextPageToken` internally — no paging UI
6. **Load for Edit**: a configuration name is a hyperlink that opens the machine-configuration editor populated with that record
7. **Cross-search navigation**: an activation row's configuration name resolves through `getConfiguration()` and opens the editor

**Two independent searches, not one search with two result tables.** A `Configuration` and a
`ConfigurationActivation` are separate records with disjoint criteria, and either question is useful
on its own — "what configurations exist in this category" and "what was active during this window"
are asked separately. Each therefore carries the full T2b vocabulary (`searchStatusMessage`,
`statusMessage`, `resultCountMessage`, `searchInProgress`) **per search rather than per view**, so
one search cannot clear the other's results or overwrite its count.

**A half-filled time range is refused, not passed through.** This is the sharpest edge in the view.
`TimeRangeCriterion` requires **both** bounds, and the dp-service request builder responds to a
half-filled pair by emitting **no criterion at all** rather than by rejecting the request
(`AnnotationClient.buildQueryConfigurationActivationsRequest`). So a user who fills in only a start
date gets a result set silently *broader* than what they asked for, with every other criterion still
applied — which reads as a working search rather than a dropped filter. `hasPartialRange()` refuses
the search and says why. The view states the constraint beneath the controls too, but the refusal is
what enforces it.

`activeAt` is an independent criterion and may be combined with a range, so `activeAt` alone is a
complete search rather than half of one.

**A ticked-but-blank temporal criterion is refused too, and it is a distinct case from the half-filled
range.** An unticked checkbox and a ticked one whose date picker is still blank both reach the view
model as `null`, but they mean opposite things — "no restriction" versus an incomplete criterion the
user intends to apply — and a null criterion is simply dropped, silently widening the search with
every other criterion still applied. The controller therefore passes the checkbox states alongside
the instants, and `hasIncompleteTemporalCriteria()` refuses the search. `hasPartialRange()` is checked
**first**, because it names the more specific problem when both apply.

**Clearing resets those enabled flags with the instants.** Leaving them set makes the next search
look like it has two ticked-but-blank criteria and be refused — a cleared form that will not search,
which is the stale-state bug the flags exist to prevent, reintroduced one level up.

**The configurations table has an Activations column**, a fixed action link that populates and runs
the activation search for that configuration and switches to its tab. It is a separate column rather
than a second action on the name, because the name already means "edit this record" and one link
cannot carry both meanings. Before it existed, `searchActivationsForConfiguration()` was unreachable
and the panel's help text promised an action nothing could invoke.

**The temporal controls are read only when their checkbox is ticked.** An unticked criterion is
published as null rather than as whatever its date picker happens to hold, so a date left behind by
an earlier search cannot silently narrow the next one. Clearing resets the spinners explicitly as
well as the dates, for the same reason the activation editor does: clearing only the dates leaves a
time of day to be silently reused.

**An absent `endTime` is open-ended, not epoch.** `ConfigurationActivation.endTime` has real protobuf
field presence, and `getEndTime()` on an absent field returns a zero-valued `Timestamp` — which
renders as a 1970 date and reads as an activation that ended before it began.
`ConfigurationActivationTableRow` branches on `hasEndTime()` and renders `OPEN_ENDED` instead.
`getEndInstant()` reports absence as null for the same reason, so the display string
(`getEndTime()`, which can be the literal "open-ended") is never mistaken for a value.

**An activation carries only its configuration's name, not the record.** So the activations table's
configuration link resolves the name through `getConfiguration()` before navigating, branching on
`isReject()` for not-found — `isError()` alone cannot tell a missing record from an unreachable
service. A name that no longer resolves is reported rather than opening an empty editor, which would
invite creating a new record under a name the user believed already existed.

**There is deliberately no free-text field**, for the same reason as the PV metadata explore view:
these criteria are name / category / parent / tags / attributes, with no `TextCriterion`.

### Sample Status Explore Workflow (Implemented)
1. **Navigation**: `Explore > Sample Statuses` (enabled after ingestion, like the other explore views)
2. **Query Editor**: required start/end time (date picker + hour/minute/second spinners), plus optional comma-separated PV names, domains, and layers
3. **Search Execution**: background `Task` calling `DpApplication.querySampleStatusBuckets()`, which follows `nextPageToken` internally — no paging UI
4. **Results Display**: one row per *status*, with PV name, timestamp, domain, layer, raw code, label, confidence, reason, source, modified-by
5. **Clear**: resets the criteria and restores the default one-hour window

**A bucket is not a row.** `querySampleStatuses()` returns `SampleStatusBucket`s, each holding
statuses for one PV in one `(domain, layer)` over a contiguous period. `SampleStatusTableRow.expand()`
flattens each bucket into one row per status; rendering buckets directly would show a row count
unrelated to the number of statuses.

**The time axis is usually a `SamplingClock`, not a list of timestamps.**
`SampleStatusBucket.dataTimestamps` is a full `DataTimestamps` — a oneof of `SamplingClock` or
`TimestampList` — and the demo generator writes a clock, because dense labeling of a regularly-sampled
range is exactly what a clock is for. So there is frequently **no timestamp list to read** and the
clock must be expanded arithmetically. Both arms are handled, since either can arrive from a producer
this app did not write.

Expansion computes `start + index * periodNanos` in integer nanoseconds, never by accumulating onto a
running `Instant`. Status identity is exact `(pvName, timestamp)` equality at nanosecond precision, so
a timestamp that drifts even one nanosecond silently fails to match the sample it labels — the same
hazard documented for the *save* side, in the other direction.

**The selected end second is INCLUDED.** The spinners select whole seconds, but the trim is
half-open, so passing the selected end straight through would drop every status stamped within the
second the user just named. The controller extends it to `.999999999`, the same adjustment
`DataExploreViewModel.getQueryEndDateTime()` makes, so the two views agree on what an end time means.

**Boundary trimming is required, not cosmetic.** Bucket selection is a `TimeRange` overlap test and
boundary buckets are returned **whole**, so a bucket at either edge of the requested window carries
statuses outside it. `expand()` drops statuses outside `[begin, end)` — half-open, so a status exactly
at `endTime` is excluded. Without the trim the view would display statuses the user did not ask for
and report a count that does not match the query.

**There are two independent caps, and they mean different things.**
`DpApplication.querySampleStatusBuckets()` caps *buckets* at `QUERY_RESULT_CAP`, because paging
boundaries fall between whole buckets — but one bucket can hold thousands of statuses, so a capped
bucket list does **not** bound the row count. `SampleStatusExploreViewModel.MAX_DISPLAYED_STATUSES`
(10,000) is the bound that actually limits the table. Either tripping is reported as truncation, and
the status message names which: more buckets on the server wants a narrower filter, more statuses in
the fetched buckets wants a narrower time range.

**Status code labels are resolved locally, for `epics_alarm` only.** The domain registry is
unimplemented server-side (`saveSampleStatusDomain()` / `querySampleStatusDomains()` are reserved and
deferred), so **nothing can resolve a code to a label from the archive**. The mapping in
`SampleStatusExploreViewModel.CODE_LABELS` covers only the domain this app's own demo generator
writes. A status in any other domain renders its raw code with an **empty** label rather than a guess,
and the view states this beneath the table — a blank Label column would otherwise read as missing data
rather than as an unknown domain.

**`confidence` and `reasons` are optional parallel arrays** — each either empty or exactly one entry
per timestamp. `expand()` checks the length against the status count rather than assuming presence:
indexing blindly throws on the common codes-only case, and rendering a *partial* array positionally
would attach the wrong confidence to a status, which is worse than omitting it.

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

**Full-replace upsert:** `savePvMetadata()` replaces the ENTIRE record for a given PV name — aliases, tags, attributes, description and modifiedBy are all overwritten, and omitted fields are not preserved. The view states this in the panel — which is why loading an existing record before editing matters, and `loadFromPvMetadata()` now provides it (see the PV Metadata Explore Workflow below).

**An overwrite is confirmed before it happens.** `savePvMetadata()` is a full-replace upsert, so
after a load-for-edit, clearing any field and saving erases that stored metadata with no error — the
likeliest way to lose data in this view. The save now runs the same pre-save existence check as the
machine-configuration editor: `getPvMetadata()`, branching on `isReject()` for not-found (an
unreachable service sets `isError`, and reading that as "no existing record" would suppress the
warning exactly when the system is unhealthy), then an FX-thread confirmation raised through a
bounded wait, with a failed check aborting the save.

**The confirmation names the record that was FOUND, not what was typed.** `getPvMetadata()` resolves
aliases, so looking up a historical name returns the record under its canonical one — while the save
targets the typed name, which would create a NEW record rather than replace the one found. Showing
the found name is what lets a user notice that.

**Critical Integration Pattern:** aliases/tags/attributes are read from the injected component instances, never from ViewModel properties. `PvMetadataViewModel` holds no collections for them. The component lists are copied on the FX thread before the background task starts, so the task never touches the observable lists off-thread.

### PV Metadata Explore Workflow (Implemented)
1. **Navigation**: `Explore > PV Metadata` (enabled after ingestion, like the other explore views)
2. **Query Editor**: PV Name and Alias, each with its own Contains / Starts-with / Exact mode, plus comma-separated tags and an attribute key + optional value
3. **Search Execution**: background `Task` calling `DpApplication.queryPvMetadata()`, which follows `nextPageToken` internally — no paging UI
4. **Results Display**: PV name, aliases, tags, attributes, description, modified-by, updated time
5. **Load for Edit**: the PV name is a hyperlink that opens the pv-metadata editor populated with that record
6. **Clear**: resets every criterion and the results

**This is not the same thing as `Explore > PV Statistics`.** pv-explore shows per-PV statistics
*derived* by aggregation over ingested buckets (data type, first/last timestamp, sample period); this
view shows the *curated* metadata record someone authored. A PV can appear in one and not the other.
The menu item was named "PVs" before #39 task 4 and is now "PV Statistics", which is why the
controller's fields are `pvStatsMenuItem` / `pvStatsEnabledProperty` / `onPvStats` — a rename that
missed one of them would fail at view-load time, which `ViewLoadSmokeTest` covers.

**There is deliberately no free-text search box.** This API's criteria are pvName, aliases, tags and
attributes — it has **no `TextCriterion`**, unlike the dataset and annotation queries. A
"search everything" field would have nothing to bind to, so offering one would promise a search that
silently matched nothing. That is exactly the trap the annotation view's event field fell into before
dp-grpc #132's removal was reflected in the UI.

**A blank field must contribute NO criterion, not an empty one.** `textMatch()` returns an all-null
`TextMatch` for a blank field, and the distinction is not cosmetic in either direction: a criterion
whose lists are present but *empty* is rejected by the server, so an unfilled optional field would
break an otherwise valid search — while a blank **prefix** value that reached the server compiles to
a regex matching **everything**, silently turning an unfilled field into a whole-collection scan that
appears to have honored the criteria the user did fill in. `PvMetadataExploreViewModelTest` asserts
each of the three lists is null individually rather than only checking `TextMatch.isEmpty()`, because
`isEmpty()` is true for both the correct all-null match and the broken empty-list one.

**The match mode is an explicit choice, never inferred from the input.** The three modes return very
different result sets, and a hidden heuristic (treating a trailing `*` as a prefix, say) would make
the difference look like a server inconsistency rather than a setting the user controls.

**An attribute value with no key is dropped, not promoted to a key.** `AttributeCriterion` requires
the key; a value alone cannot be expressed. A key with no value is a legitimate key-only existence
search, which the criterion supports directly.

**The editor is handed the resolved record, not a PV name.** This is the alias trap, and it is the
sharpest edge in the view. `getPvMetadata()` **resolves aliases**, so searching by a historical name
returns the record under its **canonical** name — and `savePvMetadata()` is a full-replace upsert
keyed on `pvName`. A load path that re-resolved from the text the user typed would let an edit of
`OLD:NAME` write a **new** record under the alias while leaving the original untouched, or overwrite
a different record entirely. `MainController.navigateToPvMetadataEditor()` therefore takes a
`PvMetadata`, and `PvMetadataViewModel.loadFromPvMetadata()` populates the form from **that record's**
canonical name, which makes the mistake unrepresentable rather than merely avoided. The view also
states the consequence beneath the results table.

**`loadFromPvMetadata()` writes aliases, tags and attributes into the COMPONENTS.** This is the
Critical Integration Pattern in its load direction, and getting it wrong is silent: the save reads
those three fields from the component instances, so a load that populated ViewModel properties would
be write-only, and editing any unrelated field would then write all three back as **absent**. That is
precisely the Annotation Builder defect documented above, in a view with the same shape.
`PvMetadataLoadForEditTest` uses real component instances rather than stubs, since the point is the
integration.

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

**Why one view and not two:** an activation cannot be created without an existing configuration. The server resolves `configurationName` against the Configuration collection on every activation save and rejects the request outright if it does not resolve (`no Configuration found for configurationName: '<name>'`). Gating section 2 on the server being known to hold a Configuration under `savedConfigurationName`, and binding it to that name rather than to the still-editable text field, makes that rejection unreachable through normal use. **The gate's invariant is existence, not provenance**: #39 task 5 added `loadFromConfiguration()`, and a record loaded from the server satisfies it exactly as a record saved here does — leaving the section disabled after a load would deny the one operation the loaded record makes safe.

**Activation overlap is rejected on same configurationName OR same category.** The Configuration's category is denormalized onto each activation record as `internalCategory`, so two *different* configurations sharing a category cannot have overlapping activations. This is correct server behavior; the view surfaces the message rather than swallowing it.

**Detecting not-found:** `getConfiguration()` reports a missing record as a *rejection*, not an empty successful result. Branch on `ApiResultBase.isReject()` rather than `isError()` — a service that is unreachable also sets `isError`, and treating that as "no existing record" would suppress the overwrite warning. Note that `REJECT` also covers server-side validation failures, so reading it as not-found is only safe once the request itself is known to be valid.

**Full-replace upsert:** both `saveConfiguration()` and `saveConfigurationActivation()` replace the ENTIRE record; omitted fields are not preserved. The view states this in the panel. Loading a configuration for editing is provided by `loadFromConfiguration()`, reached from the Configuration Explore view; an *activation* still has no load-for-edit path.

**Saving after a load raises the overwrite confirmation, and that is not spurious.** The record does exist, and the save really does replace all of it — so the prompt is the last chance to notice that a field cleared during editing will be cleared in the archive too.

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
a record replaces that record outright, so a collision is detected and confirmed before the save.
The check has **two stages, and the ordering matters**:

1. **Session list, on the FX thread.** `findSessionActivation()` matches an activation this session
   created. It reads `activations`, an observable list bound to the view, which is why it stays on
   the FX thread — the same reasoning that copies the component lists before the task starts.
2. **Server, inside the background task.** `confirmActivationOverwriteIfExists()` calls
   `DpApplication.getConfigurationActivationById()` for a record this session knows nothing about — one
   from an earlier session, or another client. It runs in the task body because it is a network
   round trip, and raises its dialog through the same bounded `runOnFxThreadAndWait()` seam the
   configuration save uses. **Do not add a second waiting mechanism.**

Stage 2 is **skipped** when stage 1 matched (the user has already answered for that record) and when
the id is blank (a blank id asks the server to generate one, so nothing can collide). Both skips are
pinned by tests, because a refactor that turns every add into a round trip — or that asks the same
question twice — is invisible otherwise.

A failed existence check **aborts the save**, exactly as `confirmOverwriteIfExists()` does for
configurations: proceeding on an unverifiable check reproduces the silent-replacement bug precisely
when the system is unhealthy. The two paths deliberately behave identically here; if the policy is
ever judged too strict it should change for both in one ticket rather than diverging.

After either stage confirms, the session list is reconciled in place rather than appended to, so a
replacement does not leave a stale row beside its replacement.

Note that `DpApplication.getConfigurationActivationById()` wraps only the **by-id** arm of the RPC.
dp-service #243 exposed the proto's `oneof key` as two named methods so that "both keys supplied"
and "neither supplied" cannot arise client-side; this app always has the id the user typed, so
`getConfigurationActivationByCompositeKey()` is deliberately unwrapped rather than overlooked.

**Waiting on an FX-thread confirmation from a background task is bounded.** `runOnFxThreadAndWait()`
awaits with a timeout and returns `Boolean` so the caller can tell "declined" from "never answered".
An unbounded await deadlocks the save thread if the FX thread is gone (view navigated away,
application shutting down mid-save), leaving `isSaving` true and the progress indicator spinning
with no way back. A timeout is treated as *do not save* — an unconfirmed overwrite must never go
through, and specifically not as a decline the user made: the status says the confirmation timed
out. The timeout is held in a field with a package-private
`setFxConfirmationTimeoutSecondsForTesting()` seam, which is the only way to reach that branch in a
test — stalling the FX thread for the production five minutes is not an option in a unit suite, and
an untested branch here would let a change back to an unbounded await through, whose symptom is a
permanently hung save rather than a failing assertion.

**The save task reports a typed outcome, not a null sentinel.** Whether a save was skipped because
the user declined, because the existence check failed, or was actually attempted is carried by
`SaveOutcome`/`PreSaveOutcome`. The earlier version distinguished these by prefix-matching the
status message, which both re-introduced the message-sniffing that `isReject()` exists to avoid and
raced with the `Platform.runLater` that sets the message.

`SaveOutcome<T>` is generic over the API result type and `PreSaveOutcome` is result-type-agnostic,
so **both** saves in this view share one wrapper. Two near-identical wrapper classes in one file is
the duplication that invites them to drift.

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

### PvMetadataTableRow (`src/main/java/com/ospreydcs/dp/gui/model/PvMetadataTableRow.java`)
Wrapper for a protobuf `PvMetadata` record in TableView displays:
- PV name, aliases, tags, attributes, description, modified-by, formatted updated time
- Formats the multi-valued fields as comma-separated strings for display, while `getAliasesList()` exposes the underlying list so `HyperlinkListTableCell` links each alias from the **list**, never by re-splitting the rendered string — an alias containing a comma would otherwise split into bogus links
- `getPvMetadata()` returns the wrapped record, which is what the editor must be loaded from: its `pvName` is the **canonical** name, and `savePvMetadata()` is a full-replace upsert keyed on it
- The `PROPERTY_*` constants name the properties the `PropertyValueFactory` column bindings resolve reflectively, so a rename that misses the controller fails to compile instead of silently blanking a column; `PvMetadataExploreColumnBindingTest` covers what constants cannot — a column bound to the *wrong* constant, or not bound at all
- Used in pv-metadata-explore; see the workflow section above for the alias-resolution rationale

### ConfigurationTableRow (`src/main/java/com/ospreydcs/dp/gui/model/ConfigurationTableRow.java`)
Wrapper for a protobuf `Configuration` record in TableView displays:
- Configuration name, category, description, parent, tags, attributes, modified-by, formatted updated time
- `getConfiguration()` returns the wrapped record, which is what the editor is loaded from: `saveConfiguration()` is a full-replace upsert keyed on `configurationName`
- The `PROPERTY_*` constants name the reflectively-resolved `PropertyValueFactory` bindings; `ConfigurationExploreColumnBindingTest` covers what constants cannot — a column bound to the *wrong* constant, or not bound at all
- Used in configuration-explore

### ConfigurationActivationTableRow (`src/main/java/com/ospreydcs/dp/gui/model/ConfigurationActivationTableRow.java`)
Wrapper for a protobuf `ConfigurationActivation` record in TableView displays:
- Activation id, configuration name, formatted start/end, description, tags, attributes, modified-by
- **An absent `endTime` renders as `OPEN_ENDED` ("open-ended"), never as a formatted timestamp.** `endTime` has real protobuf field presence, so reading it without `hasEndTime()` yields a zero-valued `Timestamp` that displays as 1970 — an activation that appears to have ended before it began. `ConfigurationActivationDetail` makes the same distinction for the editor's session list
- `getStartInstant()` / `getEndInstant()` expose the unformatted values, reporting absence as null, so the display string is never mistaken for a value
- The `PROPERTY_*` constants name the reflectively-resolved bindings, as above
- Used in configuration-explore

### SampleStatusTableRow (`src/main/java/com/ospreydcs/dp/gui/model/SampleStatusTableRow.java`)
One sample status — a single `(pvName, timestamp, domain, layer)` identity — flattened out of a `SampleStatusBucket`:
- PV name, formatted timestamp, domain, layer, raw status code, resolved label, confidence, reason, source, modified-by
- `expand(bucket, rangeBegin, rangeEnd, codeLabels)` is the decode: it expands the time axis (`SamplingClock` **or** `TimestampList`), trims to the half-open range, and resolves labels only for known domains
- `getTimestampInstant()` / `getRawStatusCode()` expose the unformatted values for callers that need identity rather than display
- The `PROPERTY_*` constants name the properties the `PropertyValueFactory` column bindings resolve reflectively, so a rename that misses the controller fails to compile instead of silently blanking a column; `SampleStatusExploreColumnBindingTest` covers what constants cannot — a column bound to the *wrong* constant, or not bound at all
- Used in sample-status-explore; see the workflow section above for the clock-expansion, trimming, and labelling rationale

### PvSelection (`src/main/java/com/ospreydcs/dp/gui/model/PvSelection.java`)
How a Query API V2 request chooses which PVs it covers — the app-side counterpart of the client's
sealed `QueryClient.PvSelectorParams`:
- Three modes: `NAME_LIST` (the default), `NAME_PATTERN`, `METADATA`
- `toSelectorParams(pvNames)` converts to the sealed client form; `describe(pvNames)` is the
  one-line summary shown in the Query Editor, the modal and the status messages
- **Does not hold the PV names.** Name-list mode takes the Query Editor's live list at conversion
  time, so there is one source of truth for the app's most widely shared state and a PV added after
  the selection was built is still queried
- `isNameList()` is what the Dataset Builder branches on — a `DataBlock` is a name list by
  definition and cannot represent the other two modes
- Immutable, so a cancelled modal leaves nothing half-applied
- Covered by `PvSelectionTest`; see the Data Explore Workflow for the empty-metadata-query hazard

### ConfigurationFilter (`src/main/java/com/ospreydcs/dp/gui/model/ConfigurationFilter.java`)
An optional restriction of a V2 query to the intervals during which matching machine configurations
were active — the app-side counterpart of `QuerySpec.configurationSelector`:
- Narrows the **time** axis, not the PV set, so it composes with `PvSelection` rather than
  overlapping it
- `toCriteria()` returns **null**, never an empty list, when nothing is filled in — the two are read
  oppositely by the client wrapper, and an empty list turns an unfiltered query into a rejected one
- Emits **one criterion per populated field**: the proto criterion is a oneof, and a multi-arm
  criterion is rejected at build time rather than resolved by preference order
- `isActive()` is what the Dataset Builder branches on — a `DataBlock` is one contiguous range and
  cannot represent the fragmented intervals an activation filter resolves to
- Immutable; covered by `ConfigurationFilterTest`

### SampleStatusFilter (`src/main/java/com/ospreydcs/dp/gui/model/SampleStatusFilter.java`)
An optional restriction of a V2 sample query to samples carrying (or not carrying) a matching sample
status — the join between the query view and the Sample Status API:
- **The mode is not a polarity switch**: `INCLUDE` excludes unlabeled samples, `EXCLUDE` returns
  them, so the two are not complements over a partially-labeled archive. Both `Mode`'s labels and
  `describe()` state the unlabeled behavior, because that is what decides whether a sparse result
  means "no data" or "nothing labeled"
- `MODE_UNSPECIFIED` is deliberately unmapped, making the server's "mode must be specified"
  rejection unrepresentable rather than merely avoided
- Empty layers means every layer in the domain and empty codes means any code; both reach the
  request as **absent** fields rather than empty ones
- `isComplete()` covers the one client-checkable rule — the server rejects a blank domain
- Accepted by the sample-oriented methods only; immutable; covered by `SampleStatusFilterTest`

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

**Sample status decode tests** (`SampleStatusTableRowTest`, `SampleStatusExploreViewModelTest`): the
clock expansion, boundary trimming and cap behavior are pure and static precisely so they are testable
without a service ecosystem — the same reasoning as `accumulatePages()` and `emptyToNull()`. These are
the cases that produce *plausible-looking wrong output* rather than an obvious failure: an off-by-one
at a bucket edge yields a believable count, and a drifting clock yields timestamps that look right but
match no sample.

**Configuration explore tests** (`ConfigurationExploreViewModelTest`, `ConfigurationLoadForEditTest`,
`ConfigurationExploreColumnBindingTest`): the half-filled range is the case worth the most here,
because both the failure and the symptom are invisible — the server accepts the request, returns
plausible results, and nothing indicates a bound was dropped.

Note what the mutation check exposed in the *tests* rather than the code: `executeActivationSearch()`
returns as soon as it has started a background thread, so asserting on a fake's call count straight
after `runOnFxThread()` reads it in a race. One refusal test passed against the broken version by
luck. A refused search starts no task and therefore has no in-progress transition to await — awaiting
the flag would hang on the correct behavior — so `runRefusedActivationSearch()` polls for a wrongly
started call instead. Both tests then failed with `expected: <0> but was: <1>`.

**PV metadata explore tests** (`PvMetadataExploreViewModelTest`, `PvMetadataLoadForEditTest`,
`PvMetadataExploreColumnBindingTest`): `textMatch()` is pure and static for the same reason as
`accumulatePages()` and `emptyToNull()` — so the criteria construction is testable without a service
ecosystem. Every case guarded here fails *silently* rather than loudly: a blank field emitted as an
empty-list criterion is rejected by the server, a blank field emitted as a prefix scans the whole
collection while looking like it worked, a load that populates ViewModel properties instead of the
components erases metadata on the next save, and a column bound to the wrong `PROPERTY_*` constant
renders a plausible value from the wrong field.

Each of those three guards was mutation-checked against the defect it claims to catch, because a
guard that cannot fail is worse than no guard — it reads as coverage. The blank-criterion guard
distinguishes `null` from `[]` (`expected: <null> but was: <[]>`), which is the distinction
`TextMatch.isEmpty()` alone cannot make, since it reports true for both.

**Live explore-query test** (`ExploreQueryLiveIT`, added by #39): verifies the query paths of tasks
3, 4 and 5 against a real ecosystem and a real MongoDB. Each assertion pins a claim about what the
**server** does, which the unit suite cannot check because the unit suite supplies the server's
answers:

- `getPvMetadata()` really does resolve an alias to the **canonical** record. Task 4's whole
  load-for-edit design rests on this; were it false, the structural fix would address a
  non-problem while a real hazard went unguarded.
- sample statuses written by `generateAndIngestData(..., true)` really do come back for the window
  they were written for, expanding to exactly one status per generated sample. A clock misalignment
  fails *silently* — the save succeeds and nothing matches at query time — so an empty result here
  is the only signal that would ever appear.
- boundary buckets really do come back **whole**: the untrimmed expansion of a one-second query
  window carried 30 statuses where the trim yields 10, so the client-side trim is load-bearing
  rather than a no-op that happens to look right.
- a **half-filled** activation range really is dropped rather than rejected — a start bound a year
  after the activation still returned it. That is the premise of
  `ConfigurationExploreViewModel.hasPartialRange()` refusing to send one.

Also pinned: a missing record is a REJECT not an error, a criteria-free query matches everything
rather than erroring, and a non-matching attribute value **excludes** the record (proving the
criterion is applied rather than dropped — the negative assertions are what stop the positive ones
from passing vacuously).

Skips rather than fails without MongoDB, and cleans up its stamped records in `@AfterAll`, exactly
as `AnnotationApiLiveIT` does. Note that `DpApplication.init()` always starts its **own in-process
ecosystem** — the app has no remote-gRPC path yet — so separately running services on 50051-50053
neither help nor hinder this test; it reaches the same database through its own services.

Run it alone with `mvn test -Dtest=ExploreQueryLiveIT`; watch it skip with
`mvn test -Dtest=ExploreQueryLiveIT -Ddp.MongoClient.dbPort=1`.

**Query API V2 decode tests** (`DataExploreV2DecodeTest`): `columnNamesOf()`, `reshapePage()` and
`renderDataValue()` are pure statics precisely so the transpose is testable without a service
ecosystem, the same reasoning as `accumulatePages()` and `SampleStatusTableRow.expand()`. Every case
guarded produces *plausible-looking wrong output* rather than an obvious failure: a dropped timestamp
column leaves a table that still renders beside a chart that silently finds no axis, a missing value
rendered as text is indistinguishable from a PV that really reported that text, a signed accessor
turns a large unsigned reading into a negative one, and a transpose that reads its row count from a
column truncates the whole page at once. All four were mutation-checked against the defect they
claim to catch.

**Search supersede tests** (`ExploreSearchSupersedeTest`): pins that Clear pressed during a slow
search discards that search's results, and — the half that stops the guard from being vacuous — that
an ordinary search still publishes. A generation check that never matched would make every search
silently return nothing, which no test asserting only the superseded case would catch. The fake gates
the service call so the race is deterministic: without the gate the search finishes before Clear runs
and the test passes whether or not the guard exists. Mutation-checked by removing the guard, which
failed with "a superseded search repopulated the table after Clear".

**Query bounds tests** (`DataExploreQueryBoundsTest`): covers the display cap, cancellation, and the
truncation reporting that makes either honest. The fake serves pages **endlessly**, always returning
a next-page token, so only a client-side bound can end the loop — a fake that stopped on its own
could not distinguish a working cap from a query that simply ran out of data, and a regression that
removes the cap hangs the test rather than passing it.

All four guards were mutation-checked, and the cancellation one **failed that check on its first
version**, which is worth recording. It used ten-row pages, and an in-memory fake serves 50,000
single-row pages in a fraction of a second — so the query ended at the *cap* whether or not
`cancel()` did anything, and the test passed against a `cancel()` that only set a status message:
precisely the defect it claims to catch. It now uses one row per page *and* a per-page delay, which
puts the cap over sixteen minutes out of reach, so only a working cancel can end the loop. The
lesson generalizes: when two bounds can end the same loop, a test for one of them must put the other
out of reach or it proves nothing.

**Live V2 test** (`QuerySamplesLiveIT`): pins what the unit suite structurally cannot — the shape the
**server** actually returns. That a `ColumnTable` carries its axis only in `timestampList` with no
timestamp column (so the synthesized one is required, not redundant); that every resolved PV gets a
column even with no data; that there is exactly one `DataValue` per column per timestamp; that paging
**terminates** and **accumulates**; and that an empty window is a success carrying an empty table
rather than a rejection.

Two things this test's own construction had to get right, both found by mutation-checking it:

- **A single-page result cannot distinguish accumulation from stopping early.** The first version
  queried 100 rows, which fit in one page, so a loop mutated to stop after page one still passed. It
  now also queries a set sized past the server's default page and asserts `pageCount > 1`, so the
  row-total assertion exercises a real page boundary.
- **`generateAndIngestData()` returning success does not mean the data is queryable.** Ingestion is
  asynchronous, and a `querySamples()` issued immediately after reliably returns the right COLUMNS
  with an EMPTY timestamp list — the same shape a legitimately empty window produces, which is why it
  reads as a query defect rather than as a race. Measured: the first query saw 0 rows and every query
  from ~500 ms on saw all 100. `awaitIngestedDataVisible()` polls for the condition rather than
  sleeping, so a growing lag fails loudly instead of becoming flaky again.

It also pins the two selector arms that resolve **server-side**, which is precisely what the unit
suite structurally cannot check — it asserts which arm is *built*, and an arm built correctly that
resolves to nothing returns a well-formed empty table rather than an error, indistinguishable from a
window with no data. The name-pattern arm must resolve PVs the test never lists and must **not**
reach one outside the pattern; each metadata criterion (tag, alias, attribute) is queried on its own
so a selector that dropped one and returned the PV via another cannot pass. The negative case — a
tag no PV carries — is what stops all of those from passing vacuously, and mutation-checking
confirmed it: a selector with its criteria removed fails with "a tag no PV carries resolved … anyway,
so the criterion is being dropped rather than applied".

Run it alone with `mvn test -Dtest=QuerySamplesLiveIT`; watch it skip with
`-Ddp.MongoClient.dbPort=1`.

**PV selector tests** (`PvSelectionTest`, `DataExplorePvSelectionTest`,
`PvSelectorDialogControllerTest`): `PvSelection` is a plain value class so which selector arm it
builds is testable without a service ecosystem, the same reasoning as `accumulatePages()` and
`SampleStatusTableRow.expand()`. The dialog test loads the **real FXML** and drives the real
controls, because the two things worth pinning are integration facts: that each mode reads only its
own controls, and that the live warning fires.

Every guard here was mutation-checked, and each protects a failure that is silent rather than loud:
a selection that copies the PV name list instead of reading it queries a stale PV set; a pattern arm
falling back to the name list returns the wrong PVs in a well-formed table; a metadata description
that omits "every PV in the archive" lets a whole-archive scan read as a filter; a validation rule
requiring names in every mode disables Submit for a valid query; and a warning label left
`visible` but not `managed` takes no space and cannot be read.

**Query filter tests** (`ConfigurationFilterTest`, `SampleStatusFilterTest`,
`DataExploreQueryFiltersTest`, `QueryFiltersDialogControllerTest`): every guard here protects a
failure that is silent rather than loud — an empty criteria list turning an unfiltered query into a
rejected one, a multi-arm criterion rejected at build time, the two status modes swapped (which
returns a plausible table either way), a dropped status code widening an `INCLUDE` filter to
"labeled at all", an over-strict client rule disabling Submit for a query the server would run, and
a scope description that omits an active filter so a filtered row count reads as unfiltered.

The live tests carry the weight the unit suite structurally cannot, exactly as for the PV selector
arms: the unit tests assert which selector is *built*, while a selector the server silently drops
returns the **full, well-formed table with no error at all**. `QuerySamplesLiveIT` therefore fixes
an activation covering only part of the data window and asserts the filtered row count is strictly
smaller than the unfiltered one; asserts that a configuration selector matching nothing is an empty
**success** rather than a rejection; and asserts both status modes against the same fully-labeled PV,
since an ignored selector returns the same table for both and each mode alone has a plausible result.
A separate test pins that the status **codes** are applied and not just the domain — without it, a
selector matching every status regardless of code passes the mode test. All of this was
mutation-checked: dropping the criteria failed with "the configuration selector returned 100 of 100
rows, so it is being DROPPED rather than applied", and neutering the codes failed with "the CODES are
being ignored and only the domain is applied -- which silently widens every INCLUDE filter".

**Demo database lifecycle test** (`DemoDatabaseLifecycleLiveIT`, added by #4 task 3): pins the
behavior change that the demo database is no longer dropped at launch. Every claim in it is about
what MongoDB still holds after a call returns, so none of it is reachable without a database:
ingested data survives an ecosystem restart; this run's buckets are in `dp-demo` and **not** in
dp-service's default `dp`; `deleteDemoDatabase()` actually removes the database; and the session
reset clears what the delete invalidated.

**It drops the configured database**, unlike the other live ITs, which avoid that by stamping
individual records and cleaning them up. Here the drop *is* the subject. Running it wipes whatever
demo data is present.

**It therefore requires an explicit opt-in (`-Ddp.test.allowDemoDatabaseDrop=true`), not merely a
reachable MongoDB.** Every other live IT here is safe to run against a developer's database because
it only touches its own stamped records; this one is not. Gating it on reachability alone would make
a routine `mvn test` silently destroy accumulated demo data — and since #4 that data survives
restarts and is worth keeping, which is exactly what makes reachability insufficient consent. Before
#4 a relaunch would have dropped it anyway.

Two of its guards failed their mutation check on the first version, both the same shape — asserting
a condition that already held:

- the name-override guard asserted that the database `dp-demo` **exists**, which a previous run had
  already made true. With the override removed it passed while 41 buckets went to `dp`. It now
  counts documents per database, and asserts zero in `dp` — the negative half is what stops the
  positive one passing on a leftover database.
- the session-reset guard asserted `providerId` was null without ever registering a provider, so it
  passed against a reset that cleared nothing. It now sets every field it asserts, and checks each
  precondition first.

Note also that the restart assertion deliberately does **not** poll: the data is confirmed visible
*before* the restart, so anything less than an immediate hit afterward means the restart removed it.
Polling there would mask the exact failure the test exists to catch. The initial ingest does gate on
visibility, for the documented asynchronous-ingestion lag.

Run it with `mvn test -Dtest=DemoDatabaseLifecycleLiveIT -Ddp.test.allowDemoDatabaseDrop=true`;
without that property it skips, as it does with `-Ddp.MongoClient.dbPort=1`.

Its central assertion changed with the rebuild: the invariant is **the data is gone and the schema is
intact**, not "the database name no longer exists". The name is present again by the time the delete
returns, so asserting its absence would be asserting the bug the rebuild prevents. Both index guards
were mutation-checked against a rebuild-less delete, failing with `expected: <3> but was: <0>`.

**What live coverage still does not reach**: FXML rendering, clicks, navigation between views, and
the editor forms. Those need the manual scenario in `plan/tickets/39/manual-verification.md`.

**Remote deployment targets are not reachable by any automated test** (#4), and cannot be: CI has no
running dp-service instances. `plan/tickets/4/manual-verification.md` carries that scenario, along
with the launch recipes.

**Selecting the mode at launch.** Prefer the `--mode` argument, which works everywhere:

```bash
java -jar target/dp-desktop-app-1.16.0-shaded.jar --mode=deployment
mvn javafx:run -Djavafx.args=--mode=deployment
```

It is also what an IDE run configuration should carry, in the program arguments of a
`DpDesktopApplicationRunner` configuration.

**`mvn javafx:run -Ddp.DpDesktopApp.mode=deployment` silently launches DEMO mode** -- the plugin
forks a JVM that does not inherit Maven's system properties. The same `-D` *is* correct on the
shaded jar, which is what makes the two easy to confuse. `DP.CONFIG=<file>` (with an `env` prefix in
zsh) is the third working path and the shape a real install uses.

**The four connect strings and the mode are environment-overridable**
(`DP_GRPC_CLIENT_QUERY_CONNECT_STRING`, `DP_DP_DESKTOP_APP_MODE`, and so on), which is the shape a
container install uses. This is not automatic: all four connect-string keys are *also* defined in
dp-service's `application.yml`, and **this app's file shadows that one on the classpath**, so the
`${VAR:default}` forms have to be repeated here. Written as plain literals they compile fine and the
`DP_GRPC_CLIENT_*` variables that work for every other Data Platform component are silently ignored
— a deployment configured that way connects to `localhost` instead of the host it was told to use.
Precedence is `-Ddp.<key>` > environment variable > the file's default.

`--mode` is translated into that same system property in `DpDesktopApplication.init()` rather than
parsed locally, so there is ONE mode-resolution path: the typo rule (an unrecognized value resolves
to DEMO, never DEPLOYMENT) therefore applies to the command line too. A local parser would be free
to drift from it, and the direction it would drift is a misspelling becoming a connection attempt
against production. An explicit `-D` wins over the argument, so a launcher script that always
appends `--mode=demo` cannot override what an operator set deliberately. Both rules are
mutation-checked in `ModeArgumentTest`.

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
- Managed through `InprocessServiceEcosystem` — **demo mode only**. Deployment mode never constructs
  a MongoDB client, so `MongoInterface` is unreachable there (see the Application Modes section)

**The deployment status label names the QUERY target, not the ingestion one.** `describe()` feeds the
status bar, the window title and the startup log, and in deployment mode it is the only thing in the
UI answering "which archive am I pointed at". Deployment mode disables every ingestion path, so the
ingestion host is the one service the application never calls there — naming it would print a host
whose correctness has no observable consequence, beside results fetched from a host the label never
mentions. `AppConfigurationTest` gives the four targets distinct hosts for exactly this reason; a
test using one host for all four would pass either way.
- Data persistence handled by gRPC service layer
- MongoDB drivers: sync, reactive streams, core, and BSON

### The demo database is not dropped at launch (changed in #4)

`MongoInterface.init()` used to do **two** unrelated things in one call: override the database name
globally, and drop the database. Issue #4 removed only the drop.

**This is the first release in which demo data survives a restart.** A demo that always started
clean can now start with a previous session's providers, buckets, sample statuses and metadata.
`Tools → Delete Demo Data` clears it on request, with a confirmation dialog naming the database.

**The override is now VERIFIED, not just performed.** `prepareDemoDatabase()` returns a boolean and
`InprocessServiceEcosystem.init()` aborts on false: after applying the override it re-reads
`getMongoDatabaseName()` and refuses to start demo mode unless the effective name is `dp-demo`.
"We called the method that sets it" and "it is actually set" are different claims, and the gap
between them is silent — every read and write would agree on the WRONG database and nothing would
error. Mutation-checked: removing the override now makes `DpApplication.init()` return false with
`REFUSING TO START DEMO MODE: the effective database name is 'dp', not 'dp-demo'`, so no data
reaches the deployment's database at all. That is prevention rather than the after-the-fact
detection the bucket-location assertions provide.

**The ordering requirement is real and was measured.** Moving one service's init ahead of
`prepareDemoDatabase()` binds that service's Mongo clients to `dp` while the rest get `dp-demo` — a
split-brain ecosystem, ingestion writing to one database while queries read another. Mutation-
checked: two clients bound to `dp`, and all five tests in `DemoDatabaseLifecycleLiveIT` failed.
`prepareDemoDatabase()` must stay the first statement in that method.

**The demo status label names its database** (`Demo (in-process) — dp-demo`), for the same reason
the deployment label names its host: "which archive am I looking at" has to be answerable from the
UI in both modes. A demo label that named nothing meant a demo pointed at the wrong database looked
exactly like a correct one.

**The name override must stay in `MongoInterface.init()`**, and this is the hazard in the split
rather than the drop. `MongoClientBase.setMongoDatabaseName()` is `protected static`, so only a
subclass can call it — which is why the two operations shared a method in the first place. Removing
the override along with the drop would silently point the demo at dp-service's default database
name (`dp`), which in a real installation is **production**, and **nothing would error**: ingestion
and query would both work, in the wrong database, because reads and writes would agree on the wrong
name. `DemoDatabaseLifecycleLiveIT` counts buckets *per database* rather than asserting the demo
name merely exists — an earlier version of that guard asserted existence and passed against a
removed override, because a previous run's `dp-demo` was still on the server.

**`hasIngestedData` and "the database is empty" stopped being the same statement.** The home view's
pre-ingestion details used to say "No data has been ingested yet"; they now scope the claim to the
session and name the database, because the archive may hold a previous run's data while
`hasIngestedData` is false. For the same reason `Tools → Delete Demo Data` is gated on the **mode
alone**, never on `hasIngestedData` — gating it on ingestion would leave exactly that leftover data
undeletable.

**The Explore menu needed the same correction, and did not get it in the first pass.** Gating it on
`hasIngestedData` left a demo relaunched on a populated database with every Explore item disabled
over hundreds of buckets — data in the archive, unreachable from the UI. `DpApplication` now probes
the archive once at init and exposes `archiveHasData()`, so the rule is:

```
exploreEnabled = deploymentMode || archiveHasData || hasIngestedData
```

**The probe asks over gRPC (`queryPvStats`), never MongoDB.** Counting documents directly would be
cheaper and is the obvious implementation, but it would construct a MongoDB client — and deployment
mode never constructing one is a structural safety property, not an incidental detail.

**The probe is skipped entirely in deployment mode, and bounded at 5 seconds in demo mode.** It runs
from `DpApplication.init()`, which JavaFX calls before `start()` — so there is no window yet and
anything slow there is a blank screen with no feedback. The underlying call cannot bound itself:
`queryPvStats` goes through dp-service's `ApiResponseObserverBase.await()`, whose timeout is **60
seconds**, so a wedged query service (which the #4 manual verification actually hit) would hold the
launch for a full minute. Deployment mode does not ask the question at all, because
`exploreEnabled = deploymentMode || …` short-circuits and the answer is unused there. Timing out
costs nothing, since the fallback answer is the same one a failure gets.

`probeArchiveWithin()` is static and takes the query as a `Supplier` so the **bound** is testable
without a service ecosystem, separately from `archiveHasDataFrom()`: that method decides what a
returned result means, this one decides what happens when no result returns at all. Mutation-checked
— unbounded, `ArchiveProbeTest` takes 60 seconds instead of 1 and the assertion fails.

**A failed probe assumes the archive HAS data.** The asymmetry is deliberate and reads like a typo:
guessing empty on an unreachable or slow service disables every Explore view over an archive that
may be full — the exact bug the probe fixes, arriving precisely when the system is already
unhealthy. Guessing non-empty at worst opens a view that reports its own emptiness. The wrong guess
must be the recoverable one. `archiveHasDataFrom()` is static so that policy is testable without a
service ecosystem (`ArchiveProbeTest`), and the mutation that flips it is caught by two tests —
it escaped the suite entirely until the decision was extracted from the private probe method.

**`resetIngestedDataState()` clears `archiveHasData` too.** Leaving it set would keep every Explore
item enabled over the database the delete just dropped — the mirror image of the bug the probe
fixes. `archiveHasData` is kept separate from `hasIngestedData` rather than folded into it, because
that flag also drives the home view's text and the Data Events gate: setting it at launch would make
the home view claim this session ingested data it never touched, trading a menu bug for a
truthfulness one. Data Events accordingly does **not** follow `archiveHasData` — leftover archive
data says nothing about whether this session has subscriptions.

**The delete action re-checks the mode at invocation**, in addition to its menu binding. This is the
one action that earns defense in depth: a broken binding is invisible (a disabled item that becomes
enabled still looks like a working menu), and the consequence in deployment mode would be a drop
against someone else's archive. Note also that `MongoInterface.init()` sets the database name
*globally* for the process, so reaching `deleteDemoDatabase()` in deployment mode would repoint the
whole process even before the drop.

**A failed drop must not clear the session state.** `deleteDemoDatabase()` returns false when the
database is untouched, and the UI reports the failure rather than resetting — an application showing
a pre-ingestion home view over a fully populated archive is worse than one showing an error.

**The drop is followed by a schema REBUILD, and that is not optional.** Dropping a database destroys
its collections *and every index on them* — including the unique indexes on `pvMetadata`,
`configurations` and `configurationActivations` — while the services that are still running hold
`MongoCollection` handles bound at their own init and create indexes only there. MongoDB silently
recreates a collection on the next write, so without the rebuild the session continues against an
**unindexed** database: ingestion, query and PV stats all keep returning success and nothing in the
UI says anything. Measured against the plain drop: `buckets` fell from 3 indexes to 1 and
`pvMetadata` from 5 to 0, and a subsequent ingest still reported success.

Before #4 the drop only ever ran at launch, *ahead* of service init, so the indexes were always
rebuilt immediately afterward; moving the drop to a menu item is what opened this gap.
`rebuildDemoSchema()` closes it with a second `init()` against the now-empty database — `init()` is
what creates every collection, runs migrations and creates every index, so the rebuild cannot drift
from the real schema the way a hand-written copy would. A failed rebuild is reported as a **failed
delete**: the data really is gone by then, so that is not strictly accurate, but reporting success
would leave the user on a silently degraded database with nothing to act on, while reporting failure
sends them to the log and to a restart, which is what repairs it.

**`MongoInterface.fini()` overrides an inherited no-op, to actually close the client — a workaround
for dp-service #282, to be removed when that lands.**
`MongoClientBase.fini()` logs and returns true; nothing anywhere in dp-service calls
`MongoClient.close()`. That is tolerable for the long-lived clients the services hold, which live as
long as the process, but not for the short-lived ones this class constructs — one per demo launch in
`prepareDemoDatabase()` and two per delete — each carrying its own connection pool and monitoring
threads. `mongoClient` is `protected` on `MongoSyncClient`, so a subclass is the only place this can
be fixed without changing dp-service.

## Releases

Tagged as `rel-<version>`. `release.yml` publishes the shaded JAR and its SHA-256 checksum, built
against `rel-<version>` of dp-grpc and dp-service — the three repos are tagged in lockstep.

Release notes are version-controlled under `doc/release-notes/`, one document per release
(`rel-<version>.md`), starting with 1.16.0; earlier releases were documented on the GitHub release
itself. A release note is organized by issue ticket rather than by PR, since a ticket often spans
several PRs, and a breaking release leads with an "Upgrading from <previous>" checklist that calls
out silent behavior changes separately from compile errors. Add each new document to the table in
the `## Release Notes` section of `README.md`.

`release.yml` publishes `doc/release-notes/rel-<version>.md` as the GitHub release body via
`body_path`, and fails the job **before the build** if the file is not present on the tagged
commit. Write the notes and merge them **before** pushing the `rel-*` tag.

**Cross-file links in a release note must be absolute, pinned to the release tag.** The notes are
published verbatim as the release body, and GitHub does not resolve a relative link there — it emits
the href unchanged and the browser resolves it against `/releases/tag/<tag>`, so
`](../../README.md#x)` 404s. This was verified against dp-grpc's published `rel-1.16.0` body, where
it is live. Use `https://github.com/osprey-dcs/dp-desktop-app/blob/rel-<version>/README.md#x`,
pinned to the tag rather than `main` so an old release's notes point at the README it shipped with.
Same-document anchors are unaffected. dp-grpc and dp-service still carry the relative form and are
broken the same way; fixing dp-grpc needs the stored release body edited, not just the file.

**The early check matters more here than in the sibling repos.** This job builds dp-grpc and
dp-service from source before it builds the app, so leaving the missing-notes failure to
`action-gh-release` would surface it three builds late.

**The notes path is derived from `VERSION`, not from `GITHUB_REF_NAME`** as it is in dp-grpc and
dp-service. Those workflows have no `workflow_dispatch` path, so for them the two are always the
same; here a manual dispatch runs from a branch, and `GITHUB_REF_NAME` would resolve to
`doc/release-notes/main.md`.

**A dry run warns rather than failing.** A manual dispatch defaults to `dry_run: true` and exists
to rehearse the build *before* a release is ready — which is exactly when the notes do not exist
yet. Failing there would block the rehearsal the dry run is for. Both publishing paths (a `rel-*`
tag push, and a dispatch with `dry_run: false`) fail hard.

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

**PV Metadata API wrappers** on `DpApplication`:
- `queryPvMetadata(pvNameMatch, aliasesMatch, tagsAnyOf, attributes)` — pages transparently via
  `accumulatePages()` up to `QUERY_RESULT_CAP`, returning `PagedResult<PvMetadata>`. Criteria are
  `TextMatch` (exact / prefix / contains, all ORed within and across the lists) and
  `AttributeCriterion` (key required, values optional); there is **no `TextCriterion`** on this API.
  Both criteria types moved to `com.ospreydcs.dp.client.criteria` in dp-service #244.
- `getPvMetadata(pvName)` — retrieves one record **by canonical name OR alias**. The returned
  record's `pvName` may therefore differ from what was passed, and callers must save using the name
  the record carries; see the alias trap in the PV Metadata Explore Workflow above.

**Machine Configuration API wrappers** on `DpApplication`:
- `queryConfigurations(nameMatch, categoryAnyOf, tagsAnyOf, attributes, parentAnyOf)` — pages
  transparently via `accumulatePages()` up to `QUERY_RESULT_CAP`, returning
  `PagedResult<Configuration>`.
- `queryConfigurationActivations(activeAt, rangeStart, rangeEnd, configurationNameAnyOf,
  clientActivationIdAnyOf, categoryAnyOf, tagsAnyOf, attributes)` — same shape, returning
  `PagedResult<ConfigurationActivation>`. Takes `Instant` at this boundary and converts inward via
  `timestampFromInstant()`, since the params take protobuf `Timestamp`.

  **`rangeStart` and `rangeEnd` are all-or-nothing, and a half-filled pair is dropped rather than
  rejected.** `TimeRangeCriterion` requires both bounds, so the request builder emits no criterion at
  all when only one is supplied — silently widening the search. Callers must validate the pair before
  calling; `ConfigurationExploreViewModel.hasPartialRange()` is what does that for the UI. `activeAt`
  is independent and may be combined with a range.

  Note the server's **zero-timestamp idiom**: a `Timestamp` of exactly epoch 0 is treated as
  unspecified and rejected, so a query at Unix epoch 0 cannot be expressed. Not reachable through the
  date pickers, but it is why an `Instant.EPOCH` sentinel must never be used to mean "unset".

**Query API V2 wrapper** on `DpApplication`:
- `querySamples(pvSelector, beginTime, endTime, pageToken)` — returns **ONE page** of aligned samples
  as a `QuerySamplesApiResult`, deliberately unlike every other paged wrapper here. The data explore
  view displays each page as it arrives, so the caller drives the loop and owns the token; see the
  Data Explore Workflow above for the full rationale and the server-side caveats.
  Takes the client's sealed `QueryClient.PvSelectorParams` rather than a name list, so all three
  selector arms reach it and the invalid "two arms set" combination does not compile. A third
  non-retryable rejection joins the two named above: a selector resolving past `maxResolvedPvCount`.
  Unlike those two it is reachable from an ordinary-looking UI choice, since an all-empty metadata
  query resolves to the whole archive rather than being rejected.
  Also takes the two optional filters. **`configurationCriteria` must be null for "no restriction",
  never an empty list** — the builder reads null/empty as "none asked for" and drops the selector,
  but a *non-empty* list yielding no usable criterion emits the empty selector the server rejects.
  `sampleStatusSelector` is accepted here but rejected on the bucket methods, which is why
  `QueryBucketsParams` omits the field entirely.
- The V1 `queryTable()` wrapper was **removed** by #39 task 6 once its only caller migrated. Leaving
  a dead wrapper would have invited a future view onto the retired path.

**Sample Status API wrappers** on `DpApplication` (the client wrappers themselves already exist on
`AnnotationClient`, added by dp-service #239 — no dp-service work is needed to use them):
- `saveSampleStatuses(frames, source, modifiedBy)` — batch upsert. Upsert is per individual status
  keyed by `(pvName, timestamp, domain, layer)` and is a **full replace**: re-saving a key with an
  empty confidence/reasons list clears the stored values.
- `querySampleStatuses(beginTime, endTime, pvNames, domains, layers, limit, pageToken)` — unary,
  resumable paging. Takes `Instant` at this boundary and converts inward via `timestampFromInstant()`,
  since `QuerySampleStatusesParams` takes protobuf `Timestamp` (like `querySamples()`, and unlike the
`queryTable()` wrapper that #39 task 6 removed). **Bucket
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

**HyperlinkListTableCell** (`src/main/java/com/ospreydcs/dp/gui/component/HyperlinkListTableCell.java`)
- Renders a row's multi-valued field as a comma-separated run of `Hyperlink`s, one per value
- Replaces four hand-written cells: provider PV names (`ProviderExploreController`), an annotation's
  related datasets and related annotations plus its ID column (`AnnotationExploreController`), and
  the PV-name / provider-name cells (`PvExploreController`)
- Two factories: `forValues(valuesExtractor, onClick)` for a list, `forSingleValue(valueExtractor,
  onClick)` for one link per row — the single-value case is the same widget with a one-element list
- `onClick` receives **the row and the clicked value**, because some links are labelled with one
  field and navigate by another: a provider-name link navigates by the row's provider *id*
- `AnnotationExploreController.CalculationsDataFrameTableCell` deliberately does **not** use it — it
  renders a *presence* link, not a value list (see the Calculations column note above), but it
  **does** use the shared `HyperlinkListTableCell.resolveRow(cell)` static, so the index-authoritative
  resolution below is not reimplemented per cell. It had the same virtualization hazard: its link
  captured the row via `getTableRow().getItem()`, so a recycled cell could fetch and open some other
  annotation's calculation frames

**Values come from the row's list accessor, never from the cell's display string.** The cell item is
the already-joined ", " string, and re-splitting it to recover the values breaks on any value
*containing* a comma — silently, producing extra links that are each mislabelled and each navigate
to an id that does not exist. Emptiness is likewise decided by the extracted list, not by the item
string: the hand-written cells returned early on a blank item, so a row whose display string was
empty but whose list was not rendered no links at all.

**The cell's index is authoritative, not its `TableRow`.** When a virtualized table recycles a cell
it sets the new index and delivers the new item immediately but repoints the cell's `TableRow` in a
*later* pass. Every cell this replaced read `getTableRow().getItem()` directly, so during that window
it rendered the **previous** row's links while holding the new row's index — no error, nothing
visibly wrong, until a link navigates somewhere unrelated to the row it appears on. `resolveRow()`
resolves by index against `getTableView().getItems()` and falls back to the `TableRow` only when the
index is out of range. `HyperlinkListTableCellTest` pins this by driving `updateIndex()`, which is
what the table itself does to recycle a cell; the guard was mutation-checked against the pre-fix
behavior.

**PvSelectorDialogController** (`src/main/java/com/ospreydcs/dp/gui/component/PvSelectorDialogController.java`)
- Modal editor for the Query Editor's PV selection — the three arms of the V2 `PvSelector`
- Static factory: `showDialog(PvSelection, List<String> pvNames, Stage)`, returning the edited
  selection or **null** when cancelled
- Edits and returns a `PvSelection`; **never** writes into the PV name list it is handed, which is
  read for display only — see the Data Explore Workflow above for why that list must stay the
  identity path
- Summary and warning update on every keystroke, because the case that needs it (an unfilled
  metadata query covering the whole archive) is accepted downstream and would otherwise be silent
- Reuses `PvMetadataExploreViewModel.textMatch()` / `parseCommaSeparatedList()` rather than
  reimplementing the criteria construction
- **Apply is disabled for a blank pattern**, the one arm the server rejects. The warning previously
  said so while Apply stayed enabled, so the selection was accepted here and failed at the server on
  the next submit — a warning naming a rule that nothing enforced. The metadata mode deliberately
  gets no such rule: an all-empty metadata query is valid and matches every PV in the archive, so
  refusing it would invent a rule the server does not have

**QueryFiltersDialogController** (`src/main/java/com/ospreydcs/dp/gui/component/QueryFiltersDialogController.java`)
- Modal editor for the Query Editor's two optional filters — `configurationSelector` and
  `sampleStatusSelector` on the V2 `QuerySpec`
- Static factory: `showDialog(ConfigurationFilter, SampleStatusFilter, Stage)`, returning a
  `Filters` record or **null** when cancelled
- **One dialog for both**, because they are the same kind of thing: optional restrictions on a query
  whose subject is already chosen by the PV selector, both off by default, composing by intersection
- **Each filter is gated by its own checkbox**, never inferred from its fields — all-blank means
  "no restriction" for one and "rejected request" for the other, so only an explicit tick says which
  the user meant. An unticked box drops its filter regardless of what the fields hold
- **Apply is disabled while the dialog is unacceptable** rather than validated on accept: a rejected
  apply that silently returned the previous filters would look like the dialog ignored the edit
- Status codes that do not parse are **refused, not dropped** — a dropped code silently widens the
  filter and returns a plausible table

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