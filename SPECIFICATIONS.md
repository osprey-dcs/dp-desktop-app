## purpose

The desktop GUI application will demonstrate the capabilities of the gRPC services built as part of the Machine Learning Data Platform (MLDP) project.  The MLDP gRPC services support data ingestion, query, annotation, and subscription.

## approach

Features will be added incrementally, according to the development plan given below.

## technology stack

- JavaFX - frontend GUI framework
- visualization library TBD - e.g., ChartFX, FXGL, JFreeChart

## communication model

By default, the application will utilize in-process grpc communication, though it should also have the ability to connect to remote gRPC services.  The initial implementation will support only in-process grpc targets, support for remote targets will be provided incrementally.

## GUI organization patterns

The project will use FXML for UI layout, and the MVVM design pattern for organizing UI-related code.

The implementation should prefer swapping the root JavaFX scene for changing the content of the application window, over creating enteriely new scene objects for every view transition.

## GUI styling

The project will use the BootstrapFX library for styling the application.  And it better look nice.

## existing class frameworks

- com.ospreydcs.dp.gui.DpApplication: (dp-desktop-app repository) Top-level application model provides high-level methods that hide the underlying gRPC API method invocations.  Uses InprocessServiceEcosystem to manage the in-process gRPC service implementations.  Uses ApiClient to invoke API methods on the gRPC targets for the services.  Additional wrapper methods for invoking gRPC API methods will be added incrementally as they are needed for the application.
- com.ospreydcs.dp.client.ApiClient: (dp-service repository) Simple client containing service-specific gRPC API clients for the Ingestion, Query, Annotation, and IngestionStream Services, used by DpApplication to invoke gRPC APIs.  Currently only an IngestionClient is implemented.  Implementations will be incrementally added including QueryClient, AnnotationClient, and IngestionStreamClient and methods incrementally added to those implementations as they are needed by the application.
- com.ospreydcs.dp.service.inprocess.InprocessServiceEcosystem: (dp-desktop-app repository) Provides a container for in-process implementations of the Ingestion, Query, Annotation, and IngestionStreamServices.  DpApplication uses this class to manage the in-process services and obtain service channels for passing to the ApiClient constructor for use as gRPC targets of the API client implementations.

## version control

The project will be created in the existing dp-desktop-app github repository whose pom.xml includes dependencies on the dp-service repository with the MLDP service implementations and the dp-grpc repository with the gRPC API definition as dependencies.

## directory structure

The dp-desktop-app repository working directory is ~/dp.fork/dp-java/dp-desktop-app.

Within the dp-desktop-app repository, the top-level directory for the gui application will be src/main/java/com/ospreydcs/dp/gui.

## other working directories

The local directory for the dp-service dependency, containing the gRPC service implementations, is ~/dp.fork/dp-java/dp-service.

The local directory for the dp-grpc repository dependency, containing the gRPC API definition, is ~/dp.fork/dp-java/dp-grpc.

## build

The project repository already uses Maven, which will also be used to build the new GUI application.

## packaging

Use jpackage to create native installers.

## application features

### data ingestion

The application uses the MLDP Ingestion Service to upload data to the archive.  There are 3 options for ingesting data:

- generating random data for a set of PVs over a specified time range
- uploading fixed data for a set of PVs
- importing data from a csv file

### data query

The application uses the MLDP Query Service to retrieve data for a specified list of PVs over a range of time.

### data visualization

The application uses a standard visualization library for data plots.

### data annotation

The application uses the MLDP Annotation Service to annotate datasets in the archive.

### annotation query

The application uses the MLDP Annotation Service to search and retrieve annotations in the archive.

### data export

The application uses the MLDP Annotation Service to export data from the archive to common file formats.

### calculations upload

The application uses the MLDP Annotation Service to upload user calculations to the archive.

### data event subscription

The application uses the MLDP Ingestion Stream Service to subscribe for data event monitoring and notification.

## application navigation

The application uses a menu bar with the following options:

- File
  - Connection: Allows choosing either in-process or remote gRPC targets.
  - Preferences:
  - Exit:
- Ingest
  - Generate: Allows uploading generated data to archive.
  - Fixed: Allows uploading fixed data to archive.
  - Import: Allows selecting a CSV file for upload to archive.
  - Subscribe: Utilized for subscribing to receive notification of data events in the ingestion stream.
- Query
  - Data: Utilized for entering time-series data query criteria and viewing results in tabular form.
  - PV Metadata: Used for exploring the PVs available in the archive.
  - Provider Metadata: Used for exploring the providers of PV data in the archive.
  - Annotations: Utilized for entering annotation query criteria and viewing results.
- Tools
  - Annotate: Utilized for identifying target data and capturing descriptive information.
  - Export: Utilized for exporting tabular query result to CSV or Excel file. 
  - Upload: Utilized for uploading user-defined calculations to the archive.
  - Console: Displays console exposing application log messages.

## development plan

Development of the demo GUI application will proceed according to the following plan:

1. (done - InprocessServiceEcosystem etc) Build container for in-process gRPC service implementations.

2. (done - ApiClient) Build container for service-specific API client implementations, with single concrete implementation of IngestionClient.

3. (done - DpApplication) Build high-level application model for use by JavaFX ViewModels to invoke gRPC API methods supporting both in-process and remote gRPC targets.

4. Create skeleton application including FXML view and MVVM ViewModel, with a main application launcher.  The skeleton view should include disabled menu items for the navigation menus described above, except for the Exit menu item, which should close the application.

5. Add simple data ingestion mechanism with support for generated PV time-series data.  Provides GUI elements for specifying parameters such as name, data type, and sample period for each PV to be generated, plus GUI elements applying to all the PVs such as begin time, provider name, etc.  The application will invoke the registerProvider() API method to register the specified provider name, and the ingestData() API method for uploading the generated data.

6. Add simple PV time-series data query mechanism with results displayed in tabular format.  Provides GUI elements for specifying query criteria including list of PV names or PV name pattern, begin time, end time, and page size (in nanoseconds) for breaking the overall query time range into pages suitable for calls to queryTable() API method, and paging through the query results.  Query results are displayed in tabular format.

