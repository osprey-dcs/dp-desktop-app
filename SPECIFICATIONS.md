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

4. (done - DpDesktopApplication) Create skeleton application including FXML view and MVVM ViewModel, with a main application launcher.  The skeleton view should include disabled menu items for the navigation menus described above, except for the Exit menu item, which should close the application.

5. Add a simple mechanism for generating Process Variable (PV) time-series data and uploading it to the archive via the MLDP Ingestion Service API, triggered by the Ingest->Generate menu item.  Provides GUI elements for specifying parameters about the request and for each PV for which data is to be generated.  Uses details entered in the GUI elements to generate random data for a collection of PVs (described in 5.1).  Uses DpApplication model to invoke Ingestion Service API methods to register provider and ingest the generated data (described in 5.2).  

5.1 Add a new scene for generating and ingestion data for sample Process Variables (PV).  This view allows the user to specify details needed to generate and ingest data for sample PVs.  It includes three sections 1) provider details, 2) request details, and 3) PV details.  It also includes buttons labeled "Generate" and "Cancel".  Each section is described in more detail below.

5.1.1 The Provider Details section includes elements for 1) Name (brief string), 2) Description (string), 3) Tags (list of strings selected from menu of options), and 4) Attributes (list of string key/value pairs selected from two synchronized menus, one for selecting the attribute key and the other for selecting the attribute value).  Choices for the Tags menu should be preopulated with options: "IOC", "application", "batch".  The choices for the attribute keys menu are "sector" and "subsystem" and the corresponding choices for the attribute values menu for each key include: ((sector: [1, 2, 3, 4]), (subsystem: [vacuum, power, RF, mechanical])).

5.1.2 The Request Details section includes elements for 1) Data Begin Time and Data End Time (each expressed as a date/time value with hours/minutes/seconds), 2) Tags (list of strings with menu for selecting multiple values, options include "commissioning", "outage", "experiment"), 3) Attributes (list of key/value string pairs with two menus for selecting as described in 5.1.1, attribute key/value options include ((status: [normal, abnormal], mode: [live, batch]), and 4) Event Name (single string with menu for selecting from options "Commission-1", "Commission-2", "Experiment-1", "Experiment-2").

5.1.3 The PV Details section includes a list of PvDetail items with options for adding and removing items from the list.  When the user selects the option for adding a new PvDetail, a panel is displayed for entering 1) PV name (brief string), 2) Data Type (menu of string choices including "integer", "float"), 3) Sample Period in milliseconds, 4) Initial Value (integer or float), and 5) Max Step Magnitude (interger or float value indicating the maximum step size if generating the values using a random walk algo).  The panel for capturing each PV details includes buttons "Add" and "Cancel".  If the user clicks "Add", a PvDetail object containing details entered by the user in the panel is added to the PvDetail list.

5.2 Add handling for the "Generate" button in the data-generation view. This consists of implementing the DataGenerationViewModel.generateData():237 method to 1) validate that all required form data is provided, 2) call the DpApplication.registerProvider() method with form provider data (described in section 5.2.2), and 3) call DpApplication.generateData() with form request and PV data (described in section 5.2.3).

5.2.1 Validate that all required form values are provided.
5.2.1.1 Provider Details section required fields: Name.
5.2.1.2 Request Details section required fields: Data Begin Time, Data End Time.
5.2.1.3 PV Details required fields in form for creating new PvDetail: Name, Data Type, Sample Period, Initial Value, Max Step Magnitude

5.2.2 The form's "Provider Details" section includes fields for name, description, tags, and attributes.  Use those values to call DpApplication.registerProvider() which will generate a RegisterProviderRequestParams object and invoke IngestionClient.sendRegisterProvider().  Save the providerId returned by the API call in a new DpApplication member variable for use in calling ingestData() and from other views in the application.

5.2.3 Invoke DpApplication.generateAndIngestData() with form data from the Request Details and PV Details section.  From the Request Details section, that includes beginTime, endTime, tags, and attributes.  From the PV Details section, that includes the list of PvDetail objects.  The generateAndIngestData() method will iterate through the PvDetail objects, generating random data values using a "random walk" scheme, and creating and sending IngestDataRequest objects via IngestionClient.sendIngestData() for each PV in the list.  Save information like data begin / end time and PvDetail list in member variables for use from other views in the application.

5.2.4 I want to make some changes to the code that generates the API requests for data ingestion and add a couple of new user interface elements for specifying associated options.  Data are stored in MongoDB "BucketDocuments" as packaged in the data ingestion requests, where each BucketDocument contains the data values for a given PV and the specified time range.  Instead of sending large data ingestion requests spanning a larger range of time, we organize the ingestion requests so that they contain data for a single second or single minute, so that it is easier to return the data for a time-series data query for a particular PV and time range.

5.2.4.1 Let's add a menu for selecting "Bucket Size" to the "Request Details" section of the data-generation view with options "1 second" and "1 minute", with "1 second" being the default option.

5.2.4.2 Add a parameter "bucketSizeSeconds" to DpApplication.generateAndIngestData() for passing the option selected in the "Bucket Size" menu to the generation code.  Add a corresponding parameter to generateAndIngestPvData() for passing the parameter to the code for generating the data and ingestion request for a particular PV.  generateAndIngestPvData() should be changed to create IngestionRequestParams with the time range for each limited to the specified bucketSizeSeconds.  So if the time range (begin time to end time) for a request is 10 minutes, and the specified bucket size option is 1 second, then we create and send 600 IngestionRequestParams for each PV.  If the specified bucket size is 1 minute, then we send 10 ingestData() requests.

6. Add simple PV time-series data query mechanism with results displayed in tabular format.  Provides GUI elements for specifying query criteria including list of PV names or PV name pattern, begin time, end time, and page size (in nanoseconds) for breaking the overall query time range into pages suitable for calls to queryTable() API method, and paging through the query results.  Query results are displayed in tabular format.

