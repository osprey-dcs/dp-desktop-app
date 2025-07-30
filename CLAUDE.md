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
The main class is configured in pom.xml as `com.ospreydcs.dp.service.ingest.server.IngestionGrpcServer` (shaded JAR), though the actual GUI application main class will be in `com.ospreydcs.dp.gui.*` when implemented.

### Maven Profiles
- `dev` (default): Development profile
- `ci`: Continuous integration profile  
- `release`: Release profile

### Dependencies
This project depends on:
- `dp-grpc` (v1.11.0): gRPC API definitions
- `dp-service` (v1.11.0): gRPC service implementations
- MongoDB drivers for data persistence
- JavaFX (planned) for GUI framework
- BootstrapFX (planned) for styling

## Architecture

### Core Components

**DpApplication** (`src/main/java/com/ospreydcs/dp/gui/DpApplication.java`)
- High-level application model that abstracts gRPC API invocations
- Manages in-process service ecosystem and API client
- Entry point for GUI interactions with backend services

**InprocessServiceEcosystem** (`src/main/java/com/ospreydcs/dp/service/inprocess/InprocessServiceEcosystem.java`)
- Container for in-process gRPC service implementations
- Manages: IngestionService, QueryService, AnnotationService, IngestionStreamService
- Initializes MongoDB client interface (uses `dp-test` database)
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
- ✅ Basic API client structure (IngestionClient implemented)
- ✅ High-level application model
- 🔄 GUI skeleton with JavaFX/FXML (planned)
- 🔄 Data ingestion UI (planned)
- 🔄 Query and visualization UI (planned)