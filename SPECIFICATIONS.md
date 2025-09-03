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

6. I'd like to formalize the flow between the various UI views, and add a place for including "hints" about suggested actions the user might take next, and status information about the last action completed.  Right now, we have the "welcome-content" view.  Would it make sense to make that view more dynamic, to include hints and status information as the user works through various parts of the application?  And maybe we should change the name from "welcome-content" to "home" or something like that.

6.1 For example, when the user starts the application, the dynamic "hints" section might say "Start by using the Ingest->Generate menu to generate sample data for some PVs and ingest it to the MLDP archive."  And the status section could say "Ready" as it currently does.

6.2 When the user successfully generates and ingests data for some PVs, we could return to the "welcome" view and updated the hints area to say something like "Use the Query menu to retrieve PV time-series data, or PV / Provider Metadata."  And the status section would display the number of PVs and buckets ingested (e.g., the same status message displayed at the bottom of the window after generating / ingesting data).

7. Initial Data Query View - Add view for querying PV time-series data with results displayed in tabular format.  The Data Query View is opened from the Query->Data menu item.  The view contains two sections: 1) a section for specifying the query criteria (described in section 7.1 and its subsections), and 2) a section for displaying the query results in tabular format (described in section 7.2 and its subsections).

7.1 Query Specification section - This section contains elements for capturing the query specification including 1) a list of PV names, and 2) "Begin Time" and "End Time" specifying the query time range.  Both elements are required.  The section should include buttons "Cancel" and "Submit".  The Cancel button should return to the home view.  The Submit button is discussed in 7.1.4.

7.1.1 The Query Specifications section includes a toggle for showing or hiding the section, and it is shown by default.  

7.1.2 By default, the list of PV names should include the PV names that were specified in the data-generation view's list of PvDetails (available via DpApplication.pvDetails.  The mechanism for adding additional PV names to the list is described in sections 7.1.2.1 through 7.1.2.5.

7.1.2.1 Adding additional PV names - The list of PV names should include an associated "Add PV" button.  When the button is clicked, a "PV search panel" is displayed for 1) searching the archive for PV names, 2) displaying names matching the search filter, and 3) allowing the user to select one or more PV names to be added to the Query Specification.

7.1.2.2 The panel includes a text field for entering the search filter, and a radio button or some mechanism for toggling between "PV name list" and "PV name pattern" that specify how the entry in the text field should be treated.  

7.1.2.3 The panel includes a "Search" button.  When the button is clicked, the view calls one of two variants of the method DpApplication.queryPvMetadata().  If the user selected "PV name list", the view calls the method variant accepting the list of PV name strings (obtained by parsing the view's input text field for comma separated list of strings).  If the user selected "PV name pattern", the view calls the variant accepting a single pattern string.  Both method variants return a protobuf QueryPvMetdataResponse.  If the response object is null, or contains an ExceptionalResult, the API method invocation failed and the ExceptionalResult contains the corresponding error message, which should be displayed in the status bar.

7.1.2.4 If the API method succeeds, the QueryPvMetdataResponse contains a list of MetadataResult objects, one for each PV whose name matches the search filter.  The list of PV names should be displayed in the search panel's list of matching PV names.  

7.1.2.5 The PV search panel's list of matching PV names provides a mechanism for selecting multiple PV names from the search result list and adding them to the Query Specification list of PV names.  Duplicate PV names selected in the search panel's list of matching PV names that are already contained in the Query Specification's list of PV names should be ignored.

7.1.2.6 The PV search panel can be used for multiple searches, so it provides a mechanism for closing the panel (instead of closing it when items are added to the Query Specification list of PVs).

7.1.3 The "Begin Time" and "End Time" should use the same form elements as in the data-generation view for picking the date and time. The default values should be the values that were provided in the data-generation view (if non-null etc) that are held in DpApplication member variables dataBeginTime and dataEndTime.

7.1.4 The Submit button - When the Query Specifications section's Submit button is clicked, validation is performed to ensure that required information is provided, including that the list of PV names is not empty and both Begin Time and End Time are specified.  If the input is invalid, an error message is displayed in the status bar.  

7.1.5 There is a concern about the amount of data returned by a PV time-series data query.  In order to avoid response messages that exceed the message size limit of 4 megabytes, the API query method is called in a loop. In the interest of responsiveness, the view breaks the overall query time range into one minute intervals and invokes DpApplication.queryTable() for each interval with the list of PV names contained in the Query Specification.  

7.1.6 The queryTable() method invokes the queryTable() API method with the list of PV names, begin and end time, and specifies TABLE_FORMAT_ROW_MAP for a row-oriented result.  

7.1.7 Each call to the queryTable() method returns a QueryTableResponse object.  If the response object is null, or contains an ExceptionalResult, the API method invocation failed and the ExceptionalResult contains the corresponding error message.

7.2 Query Results section - The results from calling queryTable() are displayed in the Query Results section in tabular format.  Each call to queryTable() returns a QueryTableResponse containing a TableResult object wrapping a RowMapTable object containing the query results in a row-oriented data structure.  The result for each response object is added to the tabular query results view in an incremental fashion so that responsiveness is maintained.  The logic for building the tabular view from the query responses is described in sections 7.2.1 through 7.2.3.

7.2.1 The column names for the result table header are obtained via the RowMapTable columnNames field.  The columns for each reponse object are assumed to be the same, since the query specifies the same list of PV names in each queryTable() invocation.  The result table should show the column names in the table header row, and the header should not scroll when the table is scrolled.  

7.2.2 The data from each QueryTableResponse object should be incrementally added to the query result table in a way that maintains responsiveness.  The TableResult object contained in each QueryTableResponse message includes a RowMapTable with the data values for the range of table rows corresponding to the time range of the associated queryTable() invocation.  The RowMapTable's "rows" field is a list of DataRow map objects containing the data values for a table row, containing a DataRow map object per table data row.  The DataRow map keys are the column names, and the map values are the column data values for that row.  The column names in the map match the column names in the header row, and this is used to determine the column index for each data value.

7.2.3 The first column of the table contains the timestamp for each table row.  The first column in RowMapTable.columnNames is labeled "timestamp".  The data map for each row includes an entry for the "timestamp" column, whose value is a protobuf Timestamp object containing epoch seconds and nanoseconds.  The protobuf Timestamp should be converted to a Java Instant and displayed in the first column using a format like ISO for displaying the date and time in a human-readable format.

7.2.4 The next step in the development plan is to add a plotting mechanism to the tabular data in the Query Results section, so please favor design decisions that facilitate use of a standard Java plotting library in the Query Results section. 

8. This section lays out the tasks for adding a new "Dataset Builder" tab in the data-query view.  Tasks are defined below.

8.1 The first task is to make some changes to the existing data-query view so we can add the new "Dataset Builder" tab, including:
8.1.1 Make the "Query Results" section collapsible like the "Query Specification" section.
8.1.2 Rename the "Query Specification" section to "Query Editor / Dataset Builder".
8.1.3 Change the "Query Editor / Dataset Builder" section to contain two tabs labeled "Query Editor" and "Dataset Builder".
8.1.4 Move the existing content of the "Query Specification" section to the new "Query Editor".

8.2 The second task is to add content to the new "Dataset Builder" tab, including:
8.2.1 The new "Dataset Builder" tab should include the following gui elements:
8.2.1.1 "Name" field (brief String, required)
8.2.1.2 "Description" (scrollable String, optional)
8.2.1.3 List of "Data Blocks", each of which is a DataBlockDetail object that includes a list of String PV names and a time range with begin and end time.  The display string for each DataBlockDetail shown in the list of Data Blocks includes the list of PV names and the time range in human readable format e.g., "pv-1, pv-2, pv-3: 2025-08-15 11:03:00 -> 2025-08-15 11:05:00".
8.2.2 The "DataSet Builder" section includes a set of buttons labeled "Reset", "Save", and "Load".  These should be initially disabled and the handling for the buttons will be defined as a follow on task.
8.2.3 When the list of "Data Blocks" is empty, the "Dataset Builder" tab shows the status message: "Use the Query Editor tab to add Data Blocks.".

8.3 The third task is to add a mechanism for adding data blocks using the query specification details in the "Query Editor" tab to the "Dataset Builder" list of data blocks.
8.3.1 Add a new button labeled "Add to Dataset" to the "Query Editor" tab next to the cancel button.
8.3.2 The new button should only be enabled when the Query Editor contents are valid (e.g., non-empty list of PV names, both begin/end time are specified, end time is after begin time), otherwise it should be disabled.
8.3.3 When the "Add to Dataset" button is clicked, a "DataBlockDetail" object is created for the list of PV Names and Begin/End Time from the Query Editor tab and added to the list of DataBlockDetail objects in the "Dataset Builder" tab, and the "Dataset Builder" tab is displayed along with the new entry in the list of Data Blocks.

8.4 The 4th task is to add handling for the "Save" button on the "Dataset Builder".  
8.4.1 When the button is clicked, validation is performed that 1) the name is a non-blank String and 2) the list of DataBlockDetails is non-empty.  If the validation fails, an error message is displayed in the status bar.  
8.4.2 If validation succeeds, the method DpApplication.saveDataSet() is invoked.  That method returns a SaveDataSetApiResult object, which contains a ResultStatus object indicating success or failure of the operation.  
8.4.3 If the isError flag of the ResultStatus object is set, the error message from the ResultStatus object should be displayed in the status bar.  
8.4.4 If the isError flag is not set, the API method call was successful and the SaveDataSetApiResult contains a String "id", which is the unique identifier for the Dataset saved to the database.  It should be displayed in the Dataset Builder's read-only "id" field.  
8.4.5 The values in the "Dataset Builder" view components should be preserved in case there are subsequent saves to the Dataset.  
8.4.6 Logic for enabling / disabling the "Save" button is unchanged, it should be enabled when the view contents are valid.

8.5 The 5th task is to add "Remove" and "View Data" buttons to the "Dataset Builder".  
8.5.1 Add two buttons labeled "Remove" and "View Data" positioned to the right of the "Data Blocks" list in the "Dataset Builder".  
8.5.2 The buttons should be enabled when there is a selection in the "Data Blocks" list, otherwise they should be disabled.  
8.5.3 When the "Remove" button is clicked, the selected data block should be removed from the "Data Blocks" list.  
8.5.4 When the "View Data" button is clicked, the list of PV names and begin / end times for the item selected in the "Data Blocks" list should be set as the models for the corresponding fields in the "Query Editor", and the view should change to display the tab for the "Query Editor" displaying the PV names and time range for the selected data block.

9. We are going to add a new tab to the data-query view labeled "Annotation Builder".  It should be positioned to the right of the "Dataset Builder" tab.  The purpose of the tab is to present a form for creating an annotation that targets one or more datasets, providing a description of the dataset(s) and relationship between them.  In addition to descriptive fields, an annotation can also contain user-defined calculations that are derived or somehow related to the target dataset(s).

9.1 "Annotation Builder" form view.  The Annotation Builder tab should follow the pattern of the "Dataset" builder for look and feel.  The tabl content should be scrollable when the window is too small to show all the content.  The form contains the following elements, in the order presented:
9.1.1 ID: (system generated, read only) - displays the Annotation id after saving it (or loading an Annotation from database)
9.1.2 name (required) - brief String identifying the Annotation to its owner
9.1.3 comment (optional) - longer String in scrollable editor
9.1.4 list of DataSetDetail objects (required), using a human-readable display string.  The display string should include 1) the Dataset name, 2) the first few characters of the Dataset description, and 3) the display string for the first DataBlockDetail object in the Dataset's list of data blocks.
9.1.5 list of tags (optional) - this includes three elements: 1) a text field for entering keywords (tags) 2) a list to display the tags entered by the user, and 3) a button for adding the tag to the list
9.1.6 list of key-value attributes (optional) - similar to the list of tags but includes: 1) two input fields, one for entering the key, one for entering the value, 2) a button for adding the key-value pair, and 3) a list showing the pairs added.
9.1.7 event name (optional): A short String field for identifying the name of an accosciated event or experiment.
9.1.8 at the bottom of the form, there are two buttons labeled "Reset", "Save", and a combobox labeled "Other actions...".  These should be styled and colored in the style of the corresponding itmes on the Dataset Builder tab.  Handling for the buttons and actions for the combobox will be defined in a subsequent task.

9.2 The next task for the "Annotation Builder" is a mechanism for adding datasets from the "Dataset Builder" to the "Annotation Builder".  We will add a button labeled "Add to Annotation" at the bottom of the "Dataset Builder" tab form, positioned between the "Save" button and "Other actions..." combobox.
9.2.1 The button should be enabled when the Dataset that is the subject of the Dataset Builder has a non-null Dataset ID (e.g., it has been saved to the database).
9.2.2 When the button is clicked, a DataSetDetail should be created for the contents of the Dataset Builder and added to the "Target Datasets" list in the "Annotation Builder".  The view should change so that the "Annotation Builder" tab is displayed.
9.2.3 If there is not an id field in DataSetDetail, please add one.

9.3 The next task is to implement handling for the "Annotation Builder" "Save" button.
9.3.1 When the button is clicked, validation is performed that 1) the Annotation name is a non-blank String and 2) the list of Target Datasets is non-empty.  If the validation fails, an error message is displayed in the status bar.  
9.3.2 If validation succeeds, the method DpApplication.saveAnnotation() is invoked.  Null values should be passed for any method parameters that don't have a value, instead of empty Strings or lists.  The method returns a SaveAnnotationApiResult object, which contains a ResultStatus object indicating success or failure of the operation.  
9.3.3 If the isError flag of the ResultStatus object is set, the operation failed and the error message from the ResultStatus object should be displayed in the status bar.  
9.3.4 If the isError flag is not set, the API method call was successful and the SaveDataSetApiResult contains a String "id", which is the unique identifier for the Dataset saved to the database.  It should be displayed in the Annotation Builder's read-only "id" field.  
9.3.5 The values in the "Annotation Builder" view components should be preserved in case there are subsequent saves to the Dataset.  
9.3.6 Logic for enabling / disabling the "Save" button is unchanged, it should be enabled when the view contents are valid.

10.0 The next task is to add support for exporting data to the Dataset Builder, using the client API exportData() method via the wrapper method DpApplication.exportData().  
10.0.1 The feature will be accessed via the "Export" option in the Dataset Builder's "Other actions..." comboxbox.  The "Export" option should be changed to include 3 sub-menu items labeled "CSV", "XLSX", and "HDF5".  The "Export" option should be enabled in the "Other actions..." combobox when the Dataset Builder contains a non-null value in the "ID" field (this is the same logic as for enabling the Dataset Builder's "Add to Annotation" button).
10.0.2 Selecting any of the "Export" sub-menu items should invoke the method DpApplication.exportData() with the following parameters: 1) ID for the Dataset Builder's Dataset, 2) null for CalculationsSpec (support will be added later), and 3) the enum value from DpApplication.ExportOutputFileFormat corresponding to the selected sub-menu label.  
10.0.3 DpApplication.exportData() returns an ExportDataApiResult object, containing a ResultStats indicating success or failure of the operation.  
10.0.4 If the ResultStatus isError flag is set, the operation failed, and the ResultStatus errorMsg should be displayed in the application status bar.  
10.0.5 If the isError flag is not set, the operation succeeded and the ExportDataApiResult contains the result from the API method in its exportDataResult member.  The ExportDataResult contains the path to the output file in its filePath member.  
10.0.6 The file path should be displayed in a message to the application status bar.  
10.0.7 The application should try to launch the file using the native application using the JavaFX Desktop class methods, using 1) isDesktopSupported() to determine if we should try to open the file, 2) creating a Java File object from the filePath, and 33) using Desktop.getDesktop().open() to open the Java file.
10.0.8 the working directories are 1) ~/dp.fork/dp-java/dp-grpc for the API definition including things like CalculationsSpec and ExportOutputFormat and 2) ~/dp.fork/dp-java/dp-service for the
client API definition including things like ExportDataApiResult and AnnotationClient.

11. The next task is to add a mechanism to the "Annotation Builder" for importing user-defined Calculations from Excel xlsx spreadsheet files.

11.1 First we will modify the view to include elements for importing and displaying Calculations on the "Annotation Builder". 
A new section will be added to the Annotation Builder for importing and viewing details for the Annotation's Calculations.  The new section will appear below the "Tags" and "Attributes" components, and above the button panel. 
The model for the Calculations section will be the new class CalculationsDetails, which should have fields for 1) a String id and 2) a list of DataFrameDetails objects. The new class DataFrameDetails should include fields for 1) a String name, 2) a list of protobuf Timestamp objects (working directory for API objects is ~/dp.fork/dp-java/dp-grpc), and 3) a list of protobuf DataColumn objects.
The new section will include a list box labeled "Calculations Data Frames" and marked as required.  The field will display the list of DataFrameDetails from the Builder's CalculationsDetails using the display string for each item in the list.  The display string for each DataFrameDetails should include frame name plus an abbreviated or truncated list of the frame's DataColumn names.
To the right of the list box should be a panel of vertically arranged buttons labeled "Import", "View", and "Remove".  The "Remove" button should remove DataFrameDetails from the list and should only be enabled when the list selection is non-null.  Handling for the other buttons will be defined in the subsequent task.

12. We are going to do a bit of refactoring to the data-generation view so that common elements can be shared with a new data-import view.

12.1 Change the "Tags" and "Attributes" components in the "Provider Details" and "Request Details" sections to use the re-usable Tabs and Attributes components that we created.  Change the labels in the "Provider Details" section to "Provider Tags" and "Provider Attributes".  In the "Request Details" section, change the labels to "Data Tags" and "Data Arributes" to avoid confusion between the Provider and Request elements.

12.2 Change the "Event Name" field in the request details section to be an input field instead of a menu of pre-defined choices.

12.3 To the data-generation view, add a new section "Generation Details" below the "Request Details" section, and move the "Data Begin Time", "Begin Time", "Data End Time", and "End Time" to the new section.
12.3.1 Add the existing content for the list of "Process Variables (PVs)" and the corresponding data entry form to the "Generation details" section.  Make the labels for process variable elements smaller to be consistent with the labels within other sections.

12.4 In the interest of sharing them with the new "data-import" view, make the sections for "Provider Details" and "Request Details" re-usable elements that can used in the new view in addition to the data-generation view.

13. The next task is to implement a new "data-import" view for reading data from an Excel xlsx file and sending it to the Data Platform ingestion API.  We will re-use some existing components for this task.  For the view, we will re-use the provider-details-component and request-details-component that were refactored from the data-generation view.  For the model, we will use the import utility in the dp-service project, src/main/java/com/ospreydcs/dp/client/utility/DataImportUtility.java importXlsxData().  Please use ~/dp.fork/dp-java/dp-service as the working directory for that project.

13.1 First, build the new "data-import" view.  
13.1.1 Follow the pattern of the "data-generation" view for including the two re-useable components provider-details-component and  request-details-component, showing the Provider Details section with the Request Details section below it.  
13.1.2 Below the Provider Details section, create a new section labeled "Import Details".  In that section, add a read-only String field labeled "File:" (and marked as required) for displaying the file path for the selected xlsx file, with a button labeled "Import" positioned to the right of it.  
13.1.3 Below the "File" field and "Import" button, add a list labeled "Ingestion Data Frames" that shows the list of DataFrameResult object returned in the DataImportResult object by DataImportUtility.importXlsxData().  The list should use display strings like the "Calculations Data Frames" list in the "Annotation Builder" tab.  
13.1.4 When the view is opened, the "File" field is empty and the "Import" button is enabled.  The "Ingestion Data Frames" list is empty and shows the message "Use the Import button to import PV time-series data from an Excel file.".
13.1.5 When the button is clicked, 1) a dialog is presented for selecting the xlsx file, and 2) the DataImportUtility.importXlsxData() method is invoked to read the file.  The "isError" flag of the ResultStatus object contained in the DataImportResult object returned by the method indicates success or failure of the operation.  
13.1.5.1 If the flag is true, the operation failed and the ResultStatus error message should be displayed in the status bar.  
13.1.5.2 If the flag is false, the operation succeeded and the path for the selected file should be displayed in the "File" field, with the DataFrameResult objects returned in the DataImportResult returned by the method.  
13.1.6 At the bottom of the view, add horizontally arranged buttons labeled "Ingest" and "Cancel" (again, follow the pattern of the data-ingestion view for the look and style).  
13.1.7 When the "Cancel" button is clicked, the "home" view is displayed.  
13.1.8 I will specify handling for the "Ingest" button as a follow on task, please just show a "action not implemented" message for now when the "Ingest" button is clicked.
13.1.9 If the "Import" button is clicked after the initial import and a new file selected to import, the contents of the "Import Details" section including the list of data frames should be reset.
13.1.10 Navigation to the data-import view is via the Ingest->Import menu item, which should now be always enabled.  From the Ingest menu, please remove the items "Fixed" and "Subscribe".

13.2 Add handling for the data-import view's "Ingest" button.  The implementation should follow the pattern of DataGenerationViewModel.generateData().
13.2.1 When the "Ingest" button is clicked, call DpApplication.registerProvider().
13.2.2 If the call to registerProvider() fails as indicated by the isError flag in the ResultStatus object returned by that method, display the error message from the ResultStatus in the status bar.
13.2.3 If the call to registerProvider() succeeds, invoke the new method DpApplication.ingestImportedData() with the appropriate values from the view.
13.2.4 If the call to ingestImportedData() fails as indicated by the isError flag in the ResultStatus object returned by that method, display the error message from the ResultStatus in the status bar.  Continue to display the data-import view.
13.2.5 If the call to ingestImportedData() succeeds, return to the home view and display confirmation that ingestion of the imported data succeeded, and suggesting navigation to the "Data Explorer" tool.

13.3 Add a "Reset" button in the data-import view's "Import Details" section, so that if there is an error ingesting the imported data, the user can reset the Import Details.  When the button is clicked, clear the "File" field and list of Import Data Frames in the Import Details section.

14. Create the "pv-explore" view which contains 3 sections, each described in more detail below.

14.1 "pv-explore" view menu navigation
14.1.1 Rename Explore->"PV Metadata" to "PVs", and rename Explore->"Provider Metadata" to "Providers"
14.1.2 Navigation to the "pv-explore" view is via the Explore->"PVs" menu.

14.2 "Query PVs" component: This will be a re-usable component, so please implement it as a standalone component instead of embedded in the view.  It will probably be included in the provider-explore view.  This component should be positioned to the left side of the pv-explore view, running the length of the window vertically.
14.2.1 At the top of the "Query PVs" component, add the label "Query PVs".
14.2.2 Below the label, include a listbox that will display the list of PV names selected for query.  The list should be scrollable.  The list should be initialized from the DpApplication.pvNames member.  
14.2.3 When PV names are added to the list in the view, they should also be added to the DpApplication.pvNames list.  Add methods for adding and removing items from DpApplication.pvNames when the view list is modified.  Please also change DpApplication.pvNames to be a sorted list.
14.2.4 Each item in the list should include a remove button icon with trash can icon or emoji as its label for removing the PV name from the list (or a "remove" button or Hyperlink).  When the remove button icon is clicked, the PV name is removed from the view's list AND DpApplication.pvNames.
14.2.5 Below the PV names listbox, should be a button labeled "Edit Query".  The button should always be visible (not scrolled away from).  The button should always be enabled.  When the button is clicked, the view should change to the "Query Editor" tab of the data-explore view.  Because we've kept DpApplication.pvNames in sync with the view's list of PV names, the Query Editor should display the list of pvNames as modified in the pv-explore view's "Query PVs" component.

14.3 To the right of the "Query PVs" component are two sections arranged vertically.  The top section is labeled "PV Query Editor" and contains a form for entering a PV query.  The bottom section is labeled "PV Query Results" and contains a list (or tabular) view of the query results.  These will follow the pattern of the "PV Search Panel" in the data-explore view's "Query Editor" tab.  You can copy those elements instead of worrying about re-use, since the plan is to remove the "PV Search Panel" once we get the pv-explore view working correctly.
14.3.1 The "PV Query Editor"  section should include the "Search:" field, "PV name list" / "PV name pattern" radio buttons, and "Search" buttons from the "PV Search Panel".  The "Close" button is not needed.
14.3.2 The "PV Query Results" section should display a list of the PvInfo items returned in the QueryPvMetadataResponse (whose metadataResult member contains a list of PvInfo objects) returned in the QueryPvMetadataApiResult.  In the "PV Search Panel" we only display the PV names in the results list.  But in the new "PV Query Results" section, make a display string for each PvInfo that includes pvName, lastBucketDataType, lastBucketDataTimestampsType, lastBucketSamplePeriod, first/lastDataTimestamp, and numBuckets.  If you think this would work better as a table with columns for the values than as a listbox with display strings, suggest that in your implementation plan.
14.3.2.1 The results list in the "PV Query Results" section should be scrollable.
14.3.2.2 Each item in the list should include a selection checkbox, with a header box for selecting all items in the results list.
14.3.2.3 Below the scrollable results list should be an always-visible button panel, with a button labeled "Add Selected".  That button should be enabled when any of the PV checkboxes is selected, or disabled when no PV checkboxes are selected. When the "Add Selected" button is clicked, all the items whose checkbox is selected in the list of PVs are added to the "Query PVs" component's list of PV names AND the DpApplication.pvNames list.
14.3.2.4 Each item in the list should include a Hyperlink (or button) labeled "add".  When the item's add link/button is clicked, the item's PV name should be added to the "Query PVs" component's list of PV names AND the DpApplication.pvNames list.

14.4 When the user clicks the "Search" button in the "PV Query Editor", call one of  the DpApplication.queryPvMetadata() variants, following the pattern of DataExploreViewModel.searchPvMetadata().  The return value from queryPvMetadata() contains a ResultStatus indicating success or failure of the operation.  
14.4.1 If ResultStatus.isError flag is set, the operation failed, and the ResultStatus.errorMsg should be displayed in the status bar.  
14.4.2 If the method succeeds (isError flag is false), display the results in the "PV Query Results" section.

14.5 Navigation to the pv-explore view from the data-explore view's Query Editor.  We are going to remove the embedded "PV Search Panel" in the Query Editor tab since that functionality is now provided in the pv-explore view, change the handling for the "Add PV" button in the Query Editor, remove the "Remove" button from the Query Editor, and add a button to each PV name in the list of PVs for removing that PV.  Details for each are below.
14.5.1 Remove the "PV Search Panel" from the data-query view's "Query Editor" tab that appears when the "Add PV" button is selected.  The functionality of the search panel is now provided by the pv-explore view.
14.5.2 Change the label for the Query Editor's "Add PV" button to "Explore PVs".  Change the handling for that button to navigate to the pv-explore view when the button is clicked.  The button should always be enabled.
14.5.3 Remove the "Remove" button from the Query Editor.
14.5.4 Add a remove button next to each name in the Query Editor's PV Names list using a trash can icon label.  The button look and feel should match the remove button used for PV names contained in the re-usable "Query PVs" component list.

15. Create the "provider-explore" view which contains 3 sections, each described in more detail below.  The view will closely follow the patterns used to build the "pv-explore" view, with the primary difference being that we replace the "PV Query Editor" and "PV Query Results" with "Provider Query Editor" and "Provider Query Results".

15.1 "provider-explore" view menu navigation
15.1.2 Navigation to the "provider-explore" view is via the Explore->"Providers" menu.

15.2 "Query PVs" component: The re-usable "Query PVs" component should be positioned to the left side of the provider-explore view, running the length of the window vertically.  The same behavior is desired for the provider-explore view as the pv-explore view in this regard (section 14.2.5 should already be reflected in the implementation of the re-usable component).

15.3 To the right of the "Query PVs" component are two sections arranged vertically.  The top section is labeled "Provider Query Editor" and contains a form for entering a Provider query.  The bottom section is labeled "Provider Query Results" and contains a tabular view of the query results.

15.3.1 The "Provider Query Editor" section should contain a form for entering a Provider query including the following fields (all brief String input fields): 
15.3.1.1 "Provider ID", 
15.3.1.2 "Name / Description"
15.3.1.3 "Tag Value"
15.3.1.4 two fields for specifying "Attribute Key" and "Attribute Value".

15.3.2 The "Provider Query Results" section should display a table of the ProviderInfo items returned in the QueryProvidersResponse (whose providersResult member contains a list of ProviderInfo objects) returned in the QueryProvidersApiResult object returned by DpApplication.queryProviders().  The table should include the following columns (including the mapping between the column and field in the ProviderInfo object and other details):
15.3.2.1 "id" - displays ProviderInfo.id.
15.3.2.2 "name" - displays ProviderInfo.name.
15.3.2.3 "description" - displays ProviderInfo.description.
15.3.2.4 "PV names" - displays ProviderInfo.providerMetadata.pvNames.  Display a comma-separated list of the PV names, including a hyperlink for each name as described in section 15.3.2.9.
15.3.2.5 "tags" - displays ProviderInfo.tags.  Display a comma-separated list of the tag values.
15.3.2.6 "attributes" - displays ProviderInfo.attributes. Display each attribute key/value pair in a comma-separated list like "key1=value1, key2=value2" etc.
15.3.2.7 "numBuckets" - displays ProviderInfo.providerMetadata.numBuckets.

15.3.2.8 The table in the "Provider Query Results" section should be scrollable.
15.3.2.9 Each PV name in the table's "PV names" column should include a hyperlink.  When the PV name's hyperlink is clicked, the PV name corresponding to the link should be added to the "Query PVs" component's list of PV names AND the DpApplication.pvNames list.

15.4 When the user clicks the "Search" button in the "Provider Query Editor", call the DpApplication.queryProviders() method, passing the value for each of the Provider Query Editor's input fields to the corresponding method parameter.  When the view element is empty, pass null value to the queryProviders method for the corresponding parameter.  Mapping of view elements to method parameters is below:

15.4.0.1 pass value in "Provider ID" field to method's providerId parameter
15.4.0.2 pass value in "Name / Description" field to method's providerText parameter
15.4.0.3 pass value in "Tag Value" field to method's tagValue parameter
15.4.0.4 pass value in "Attribute Key" field to method's "attributeKey" parameter; pass value in "Attribute Value" field to method's "attributeValue parameter".

15.4.1 The return value from queryProviders() contains a ResultStatus indicating success or failure of the operation.  
15.4.1.1 If ResultStatus.isError flag is set, the operation failed, and the ResultStatus.errorMsg should be displayed in the status bar.  
15.4.1.2 If the method succeeds (isError flag is false), display the results in the "Provider Query Results" section's table.