# YAPPC API

Programmatic access to YAPPC scaffold operations for IDE integrations, CI/CD pipelines, and custom tooling.

## Installation

Add the dependency to your project:

```kotlin
// Gradle Kotlin DSL
implementation(project(":products:yappc:core:scaffold:api"))
```

```groovy
// Gradle Groovy DSL
implementation project(':products:yappc:core:scaffold:api')
```

## Quick Start

```java
import com.ghatana.yappc.api.*;
import com.ghatana.yappc.api.model.*;

// Create API instance with defaults
YappcApi yappc = YappcApi.create();

// Create a new project
CreateResult result = yappc.createProject("my-service", "java-service-spring-gradle");

if (result.isSuccess()) {
    System.out.println("Created project at: " + result.getProjectPath());
    System.out.println("Files created: " + result.getFileCount());
}

// Cleanup when done
yappc.shutdown();
```

## API Overview

### Creating the API Instance

```java
// Option 1: Default configuration
YappcApi yappc = YappcApi.create();

// Option 2: Custom configuration
YappcConfig config = YappcConfig.builder()
        .packsPath(Paths.get("/custom/packs/path"))
        .workspacePath(Paths.get("/my/workspace"))
        .enableCache(true)
        .enableTelemetry(false)
        .build();
YappcApi yappc = YappcApi.create(config);

// Option 3: Builder pattern
YappcApi yappc = YappcApi.builder()
        .packsPath(Paths.get("/custom/packs"))
        .enableCache(true)
        .build();
```

### Project Operations

#### Create a New Project

```java
// Simple creation
CreateResult result = yappc.createProject("my-app", "java-service-spring-gradle");

// With variables
CreateResult result = yappc.createProject("my-app", "java-service-spring-gradle", 
    Map.of(
        "packageName", "com.example.myapp",
        "javaVersion", "21",
        "springVersion", "3.2.0"
    )
);

// Full control with builder
CreateResult result = yappc.projects().create(
    CreateRequest.builder()
        .projectName("my-service")
        .packName("java-service-spring-gradle")
        .outputPath(Paths.get("/workspace/projects"))
        .variable("packageName", "com.example")
        .variable("description", "My awesome service")
        .overwrite(false)
        .dryRun(false)
        .build()
);

// Check result
if (result.isSuccess()) {
    System.out.println("Project: " + result.getProjectPath());
    System.out.println("Pack: " + result.getPackName() + " v" + result.getPackVersion());
    System.out.println("Files: " + result.getFilesCreated());
    System.out.println("Duration: " + result.getDurationMs() + "ms");
} else {
    System.err.println("Error: " + result.getErrorMessage());
}

// Check for warnings
if (result.hasWarnings()) {
    result.getWarnings().forEach(w -> System.out.println("Warning: " + w));
}
```

#### Add Features to Existing Projects

```java
// Quick feature addition
AddResult result = yappc.addFeature(
    Paths.get("/workspace/my-service"),
    "database",
    "postgresql"
);

// With full control
AddResult result = yappc.projects().addFeature(
    AddFeatureRequest.builder()
        .projectPath(Paths.get("/workspace/my-service"))
        .feature("database")
        .type("postgresql")
        .variable("dbName", "myapp_db")
        .variable("dbUser", "myapp_user")
        .dryRun(false)
        .force(false)
        .build()
);

if (result.isSuccess()) {
    System.out.println("Feature added: " + result.getFeature());
    System.out.println("Files created: " + result.getFilesCreated());
    System.out.println("Files modified: " + result.getFilesModified());
    System.out.println("Dependencies added: " + result.getDependenciesAdded());
}
```

**Available Features:**
- `database` - Types: `postgresql`, `mysql`, `mongodb`, `h2`
- `auth` - Types: `jwt`, `oauth2`, `basic`
- `observability` - Types: `prometheus`, `opentelemetry`, `micrometer`
- `messaging` - Types: `kafka`, `rabbitmq`, `redis`
- `cache` - Types: `redis`, `caffeine`, `ehcache`

#### Update Projects with Pack Changes

```java
// Check for updates
UpdateAvailability updates = yappc.projects().checkUpdates(
    Paths.get("/workspace/my-service")
);

if (updates.updateAvailable()) {
    System.out.println("Update available: " + updates.currentVersion() + 
                       " -> " + updates.latestVersion());
    
    if (updates.breakingChanges()) {
        System.out.println("Warning: Contains breaking changes!");
    }
    
    // Preview changes before applying
    UpdatePreview preview = yappc.projects().previewUpdate(
        UpdateRequest.builder()
            .projectPath(Paths.get("/workspace/my-service"))
            .build()
    );
    
    preview.fileChanges().forEach(change -> 
        System.out.println(change.type() + ": " + change.path())
    );
    
    // Apply update
    UpdateResult result = yappc.projects().update(
        UpdateRequest.builder()
            .projectPath(Paths.get("/workspace/my-service"))
            .backup(true)
            .force(false)
            .build()
    );
    
    if (result.hasConflicts()) {
        System.out.println("Conflicts: " + result.getConflicts());
    }
}
```

#### Get Project Information

```java
Path projectPath = Paths.get("/workspace/my-service");

// Check if YAPPC-managed
boolean isYappc = yappc.projects().isYappcProject(projectPath);

// Get project info
Optional<ProjectInfo> infoOpt = yappc.projects().getInfo(projectPath);
infoOpt.ifPresent(info -> {
    System.out.println("Name: " + info.getProjectName());
    System.out.println("Pack: " + info.getPackName());
    System.out.println("Language: " + info.getLanguage());
    System.out.println("Platform: " + info.getPlatform());
    System.out.println("Features: " + info.getAddedFeatures());
    System.out.println("Update available: " + info.isUpdateAvailable());
});

// Get project state
Optional<ProjectState> stateOpt = yappc.projects().getState(projectPath);
stateOpt.ifPresent(state -> {
    System.out.println("Variables: " + state.getVariables());
    System.out.println("Generated files: " + state.getGeneratedFiles());
});

// Validate project
ProjectValidationResult validation = yappc.projects().validate(projectPath);
if (!validation.isValid()) {
    System.out.println("Errors: " + validation.errors());
    System.out.println("Missing files: " + validation.missingFiles());
}
```

### Pack Operations

#### List and Search Packs

```java
// List all packs
List<PackInfo> allPacks = yappc.packs().list();

// Filter packs
List<PackInfo> javaPacks = yappc.packs().byLanguage("java");
List<PackInfo> backendPacks = yappc.packs().byCategory("backend");
List<PackInfo> serverPacks = yappc.packs().byPlatform("server");

// Search by name/description
List<PackInfo> springPacks = yappc.packs().search("spring");

// Advanced filtering
List<PackInfo> filtered = yappc.packs().list(
    PackListRequest.builder()
        .language("java")
        .category("backend")
        .platform("server")
        .buildSystem("gradle")
        .includeCompositions(true)
        .includeFeaturePacks(false)
        .build()
);
```

#### Get Pack Details

```java
Optional<PackInfo> packOpt = yappc.packs().get("java-service-spring-gradle");
packOpt.ifPresent(pack -> {
    System.out.println("Name: " + pack.getName());
    System.out.println("Version: " + pack.getVersion());
    System.out.println("Description: " + pack.getDescription());
    System.out.println("Language: " + pack.getLanguage());
    System.out.println("Category: " + pack.getCategory());
    System.out.println("Platform: " + pack.getPlatform());
    System.out.println("Build System: " + pack.getBuildSystem());
    System.out.println("Templates: " + pack.getTemplateCount());
    System.out.println("Required Variables: " + pack.getRequiredVariables());
    System.out.println("Optional Variables: " + pack.getOptionalVariables());
    System.out.println("Defaults: " + pack.getDefaults());
    
    if (pack.isComposition()) {
        System.out.println("Composed of: " + pack.getComposedPacks());
    }
});

// Check if pack exists
boolean exists = yappc.packs().exists("java-service-spring-gradle");

// Validate pack structure
PackValidationResult validation = yappc.packs().validate("java-service-spring-gradle");
if (!validation.isValid()) {
    validation.getErrors().forEach(err -> 
        System.out.println("Error: " + err.message() + " in " + err.file())
    );
}
```

#### Discover Available Options

```java
// Get all available languages
List<String> languages = yappc.packs().getAvailableLanguages();
// ["java", "typescript", "rust", "go"]

// Get all available categories
List<String> categories = yappc.packs().getAvailableCategories();
// ["backend", "fullstack", "middleware", "platform", "feature"]

// Get all available platforms
List<String> platforms = yappc.packs().getAvailablePlatforms();
// ["server", "desktop", "mobile", "web"]

// Refresh pack cache
yappc.packs().refresh();
```

### Template Operations

#### Render Templates

```java
// Render a template string
String template = "package {{packageName}};\n\npublic class {{pascalCase className}} {}";
String result = yappc.templates().render(template, Map.of(
    "packageName", "com.example",
    "className", "my-service"
));
// Result: "package com.example;\n\npublic class MyService {}"

// Render a template file
String content = yappc.templates().renderFile(
    Paths.get("/packs/java-service/templates/App.java.tmpl"),
    Map.of("packageName", "com.example", "className", "App")
);

// Render to file
RenderResult result = yappc.templates().renderToFile(
    Paths.get("/packs/java-service/templates/App.java.tmpl"),
    Paths.get("/output/src/main/java/App.java"),
    Map.of("packageName", "com.example")
);
```

#### Template Helpers

Built-in helpers for template variables:

| Helper | Input | Output |
|--------|-------|--------|
| `lowercase` | `"HELLO"` | `"hello"` |
| `uppercase` | `"hello"` | `"HELLO"` |
| `capitalize` | `"hello"` | `"Hello"` |
| `camelCase` | `"my-service-name"` | `"myServiceName"` |
| `pascalCase` | `"my-service-name"` | `"MyServiceName"` |
| `snakeCase` | `"MyServiceName"` | `"my_service_name"` |
| `kebabCase` | `"MyServiceName"` | `"my-service-name"` |
| `uuid` | (none) | `"550e8400-e29b-41d4-..."` |
| `date` | (none) | `"2025-12-05"` |
| `now` | (none) | `"2025-12-05T10:30:00Z"` |

**Usage in templates:**
```handlebars
package {{packageName}};

/**
 * Generated on {{date}}
 * ID: {{uuid}}
 */
public class {{pascalCase projectName}}Application {
    public static final String NAME = "{{snakeCase projectName}}";
}
```

#### Register Custom Helpers

```java
// Register a custom helper
yappc.templates().registerHelper("reverse", input -> 
    new StringBuilder(input).reverse().toString()
);

// Use in templates: {{reverse myVar}}
String result = yappc.templates().render("{{reverse name}}", Map.of("name", "hello"));
// Result: "olleh"

// List all available helpers
List<String> helpers = yappc.templates().getAvailableHelpers();
```

#### Template Discovery

```java
// List templates in a pack
List<TemplateInfo> templates = yappc.templates().listTemplates("java-service-spring-gradle");
templates.forEach(t -> {
    System.out.println("Template: " + t.name());
    System.out.println("  Target: " + t.targetPath());
    System.out.println("  Variables: " + t.variables());
});

// Get required variables for a pack
List<String> vars = yappc.templates().getPackRequiredVariables("java-service-spring-gradle");
// ["projectName", "packageName", "javaVersion", ...]

// Validate template syntax
boolean valid = yappc.templates().validateSyntax("Hello {{name}}!");
```

### Dependency Operations

#### Analyze Dependencies

```java
// Analyze pack dependencies
DependencyAnalysis packAnalysis = yappc.dependencies().analyzePack("java-service-spring-gradle");
System.out.println("Direct dependencies: " + packAnalysis.directDependencies());
System.out.println("Total count: " + packAnalysis.totalCount());

// Analyze project dependencies
DependencyAnalysis projectAnalysis = yappc.dependencies().analyzeProject(
    Paths.get("/workspace/my-service")
);

// Get dependencies
List<DependencyInfo> deps = yappc.dependencies().getPackDependencies("java-service-spring-gradle");
deps.forEach(dep -> {
    System.out.println(dep.getCoordinates());
    // "org.springframework.boot:spring-boot-starter-web:3.2.0"
});
```

#### Check for Conflicts

```java
// Check conflicts between multiple packs
List<ConflictInfo> conflicts = yappc.dependencies().checkConflicts(
    List.of("java-service-spring-gradle", "java-feature-database-postgresql")
);

conflicts.forEach(conflict -> {
    System.out.println("Conflict: " + conflict.dependencyName());
    System.out.println("  Version 1: " + conflict.version1() + " from " + conflict.source1());
    System.out.println("  Version 2: " + conflict.version2() + " from " + conflict.source2());
    System.out.println("  Resolution: " + conflict.resolution());
});

// Check if adding a pack would cause conflicts
List<ConflictInfo> addConflicts = yappc.dependencies().checkAddConflicts(
    Paths.get("/workspace/my-service"),
    "java-feature-messaging-kafka"
);
```

#### Dependency Upgrades

```java
// Find outdated dependencies
List<DependencyInfo> outdated = yappc.dependencies().findOutdated(
    Paths.get("/workspace/my-service")
);

// Get upgrade suggestions
List<DependencyService.DependencyUpgrade> upgrades = yappc.dependencies().suggestUpgrades(
    Paths.get("/workspace/my-service")
);

upgrades.forEach(upgrade -> {
    System.out.println(upgrade.name() + ": " + upgrade.currentVersion() + 
                       " -> " + upgrade.suggestedVersion());
    if (upgrade.breaking()) {
        System.out.println("  ⚠️ Breaking change!");
    }
});
```

## Complete Example: CI/CD Pipeline Integration

```java
import com.ghatana.yappc.api.*;
import com.ghatana.yappc.api.model.*;
import java.nio.file.*;
import java.util.*;

public class CIPipelineExample {
    
    public static void main(String[] args) {
        // Initialize API
        YappcApi yappc = YappcApi.builder()
                .packsPath(Paths.get(System.getenv("YAPPC_PACKS_PATH")))
                .enableCache(true)
                .build();
        
        try {
            String projectName = System.getenv("PROJECT_NAME");
            String packName = System.getenv("PACK_NAME");
            Path outputDir = Paths.get(System.getenv("OUTPUT_DIR"));
            
            // Validate pack exists
            if (!yappc.packs().exists(packName)) {
                System.err.println("Pack not found: " + packName);
                System.exit(1);
            }
            
            // Check for dependency conflicts
            PackInfo pack = yappc.packs().get(packName).orElseThrow();
            if (!pack.getSupportedPacks().isEmpty()) {
                List<ConflictInfo> conflicts = yappc.dependencies().checkConflicts(
                    pack.getSupportedPacks()
                );
                if (!conflicts.isEmpty()) {
                    System.err.println("Dependency conflicts detected!");
                    conflicts.forEach(c -> System.err.println("  - " + c.dependencyName()));
                    System.exit(1);
                }
            }
            
            // Create project
            CreateResult result = yappc.projects().create(
                CreateRequest.builder()
                    .projectName(projectName)
                    .packName(packName)
                    .outputPath(outputDir)
                    .variable("packageName", "com." + projectName.replace("-", "."))
                    .variable("version", "1.0.0-SNAPSHOT")
                    .overwrite(true)
                    .build()
            );
            
            if (!result.isSuccess()) {
                System.err.println("Failed to create project: " + result.getErrorMessage());
                System.exit(1);
            }
            
            System.out.println("✅ Project created: " + result.getProjectPath());
            System.out.println("   Pack: " + result.getPackName() + " v" + result.getPackVersion());
            System.out.println("   Files: " + result.getFileCount());
            System.out.println("   Duration: " + result.getDurationMs() + "ms");
            
            // Add database if requested
            if (Boolean.parseBoolean(System.getenv("ADD_DATABASE"))) {
                AddResult dbResult = yappc.addFeature(
                    result.getProjectPath(),
                    "database",
                    System.getenv("DATABASE_TYPE")
                );
                
                if (dbResult.isSuccess()) {
                    System.out.println("✅ Database added: " + dbResult.getType());
                }
            }
            
            // Validate the created project
            ProjectValidationResult validation = yappc.projects().validate(result.getProjectPath());
            if (!validation.isValid()) {
                System.err.println("Project validation failed!");
                validation.errors().forEach(e -> System.err.println("  - " + e));
                System.exit(1);
            }
            
            System.out.println("✅ Project validation passed");
            
        } finally {
            yappc.shutdown();
        }
    }
}
```

## Error Handling

```java
YappcApi yappc = YappcApi.create();

try {
    // API operations
    CreateResult result = yappc.createProject("my-app", "nonexistent-pack");
    
    if (!result.isSuccess()) {
        // Handle application-level errors
        System.err.println("Creation failed: " + result.getErrorMessage());
    }
    
} catch (IllegalStateException e) {
    // API not initialized or already shut down
    System.err.println("API error: " + e.getMessage());
    
} catch (RuntimeException e) {
    // Unexpected errors (IO, etc.)
    System.err.println("Unexpected error: " + e.getMessage());
    
} finally {
    yappc.shutdown();
}
```

## Thread Safety

The `YappcApi` and all services are thread-safe and can be shared across threads:

```java
YappcApi yappc = YappcApi.create();

// Use from multiple threads
ExecutorService executor = Executors.newFixedThreadPool(4);

List<Future<CreateResult>> futures = projectNames.stream()
    .map(name -> executor.submit(() -> 
        yappc.createProject(name, "java-service-spring-gradle")
    ))
    .toList();

// Wait for all to complete
for (Future<CreateResult> future : futures) {
    CreateResult result = future.get();
    System.out.println("Created: " + result.getProjectPath());
}

executor.shutdown();
yappc.shutdown();
```

## Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `packsPath` | `$YAPPC_PACKS_PATH` or `~/.yappc/packs` | Path to pack definitions |
| `workspacePath` | Current directory | Default output directory |
| `enableCache` | `true` | Cache pack metadata |
| `enableTelemetry` | `false` | Send usage telemetry |
| `cacheMaxSize` | `100` | Max cached packs |
| `cacheTtlSeconds` | `3600` | Cache TTL in seconds |

---

# YAPPC HTTP API

RESTful and WebSocket API for remote access to YAPPC scaffold operations.

## HTTP Server Installation

Add the HTTP module dependency:

```kotlin
// Gradle Kotlin DSL
implementation(project(":products:yappc:core:scaffold:api:http"))
```

## Starting the Server

```java
import com.ghatana.yappc.api.http.*;

// Start with defaults (port 8080)
YappcServer server = YappcServer.create().start();

// Start with custom configuration
YappcServerConfig config = YappcServerConfig.builder()
        .port(9090)
        .host("localhost")
        .enableCors(true)
        .corsOrigin("https://app.example.com")
        .enableWebSocket(true)
        .build();

YappcServer server = YappcServer.create(config).start();

// Stop server
server.stop();
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `YAPPC_PORT` | `8080` | Server port |
| `YAPPC_PACKS_PATH` | `~/.yappc/packs` | Path to packs |

## REST API Endpoints

Base URL: `http://localhost:8080/api/v1`

### Health Check

```bash
# Check server health
curl http://localhost:8080/health
```

Response:
```json
{
  "status": "ok",
  "version": "1.0.0"
}
```

### Pack Operations

```bash
# List all packs
curl http://localhost:8080/api/v1/packs

# List packs with filtering
curl "http://localhost:8080/api/v1/packs?language=java&category=backend"

# Get pack details
curl http://localhost:8080/api/v1/packs/java-service-spring-gradle

# Get available languages
curl http://localhost:8080/api/v1/packs/languages

# Get available categories
curl http://localhost:8080/api/v1/packs/categories

# Get available platforms
curl http://localhost:8080/api/v1/packs/platforms

# Validate a pack
curl http://localhost:8080/api/v1/packs/java-service-spring-gradle/validate

# Get pack variables
curl http://localhost:8080/api/v1/packs/java-service-spring-gradle/variables

# Refresh pack cache
curl -X POST http://localhost:8080/api/v1/packs/refresh
```

### Project Operations

```bash
# Create a new project
curl -X POST http://localhost:8080/api/v1/projects \
  -H "Content-Type: application/json" \
  -d '{
    "packName": "java-service-spring-gradle",
    "projectName": "my-service",
    "targetPath": "/home/user/projects",
    "variables": {
      "groupId": "com.example",
      "artifactId": "my-service"
    }
  }'

# Add a feature to existing project
curl -X POST http://localhost:8080/api/v1/projects/add-feature \
  -H "Content-Type: application/json" \
  -d '{
    "projectPath": "/home/user/projects/my-service",
    "packName": "java-feature-kafka",
    "featureName": "kafka-producer"
  }'

# Get project info
curl "http://localhost:8080/api/v1/projects/info?path=/home/user/projects/my-service"

# Get project state
curl "http://localhost:8080/api/v1/projects/state?path=/home/user/projects/my-service"

# Validate project
curl "http://localhost:8080/api/v1/projects/validate?path=/home/user/projects/my-service"

# Check for updates
curl "http://localhost:8080/api/v1/projects/check-updates?path=/home/user/projects/my-service"

# Get project features
curl "http://localhost:8080/api/v1/projects/features?path=/home/user/projects/my-service"

# Export project state
curl -X POST http://localhost:8080/api/v1/projects/export \
  -H "Content-Type: application/json" \
  -d '{"projectPath": "/home/user/projects/my-service"}'
```

### Template Operations

```bash
# Render a template
curl -X POST http://localhost:8080/api/v1/templates/render \
  -H "Content-Type: application/json" \
  -d '{
    "template": "Hello, {{name}}! Welcome to {{project}}.",
    "variables": {
      "name": "Developer",
      "project": "YAPPC"
    }
  }'

# Get available helpers
curl http://localhost:8080/api/v1/templates/helpers

# Validate template syntax
curl -X POST http://localhost:8080/api/v1/templates/validate \
  -H "Content-Type: application/json" \
  -d '{"template": "{{#if condition}}valid{{/if}}"}'
```

### Dependency Operations

```bash
# Analyze pack dependencies
curl http://localhost:8080/api/v1/dependencies/analyze/pack/java-service-spring-gradle

# Analyze project dependencies
curl -X POST http://localhost:8080/api/v1/dependencies/analyze/project \
  -H "Content-Type: application/json" \
  -d '{"projectPath": "/home/user/projects/my-service"}'

# Check for conflicts between packs
curl -X POST http://localhost:8080/api/v1/dependencies/conflicts \
  -H "Content-Type: application/json" \
  -d '{"packNames": ["java-feature-kafka", "java-feature-rabbitmq"]}'
```

## WebSocket API

Connect to real-time progress updates during project operations.

### Connection

```javascript
const ws = new WebSocket('ws://localhost:8080/ws/progress?sessionId=my-session');

ws.onopen = () => {
  console.log('Connected to YAPPC progress stream');
};

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  console.log('Progress:', message);
};
```

### Message Types

**Connection confirmed:**
```json
{
  "type": "connected",
  "sessionId": "my-session",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Progress update:**
```json
{
  "type": "progress",
  "data": {
    "step": "Creating files",
    "current": 5,
    "total": 10,
    "file": "src/main/java/App.java"
  },
  "timestamp": "2025-01-15T10:30:01Z"
}
```

**Operation complete:**
```json
{
  "type": "complete",
  "result": {
    "success": true,
    "filesCreated": 15
  },
  "timestamp": "2025-01-15T10:30:05Z"
}
```

### Client Commands

```javascript
// Ping/Pong
ws.send(JSON.stringify({ type: 'ping' }));

// Subscribe to channel
ws.send(JSON.stringify({ type: 'subscribe', channel: 'project-123' }));

// Unsubscribe
ws.send(JSON.stringify({ type: 'unsubscribe', channel: 'project-123' }));
```

## gRPC API

High-performance RPC interface for language-agnostic access to YAPPC operations with streaming support.

### Installation

Add the gRPC module to your project:

```kotlin
// Gradle Kotlin DSL
implementation(project(":products:yappc:core:scaffold:api:grpc"))
```

### Starting the gRPC Server

```java
import com.ghatana.yappc.api.grpc.*;

// Create with defaults (port 50051)
YappcGrpcServer server = YappcGrpcServer.create().start();

// Create with custom configuration
YappcGrpcServerConfig config = YappcGrpcServerConfig.builder()
        .port(50051)
        .packsPath(Paths.get("/packs"))
        .workspacePath(Paths.get("/workspace"))
        .enableReflection(true)  // Enables grpcurl and GUI clients
        .maxMessageSize(16 * 1024 * 1024)  // 16MB
        .build();

YappcGrpcServer server = YappcGrpcServer.create(config).start();

// Shutdown hook
Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
server.blockUntilShutdown();
```

### Available Services

| Service | Description | Key RPCs |
|---------|-------------|----------|
| `PackService` | Pack discovery and management | `ListPacks`, `GetPack`, `SearchPacks`, `ValidatePack` |
| `ProjectService` | Project lifecycle operations | `CreateProject`, `AddFeature`, `UpdateProject`, `GetInfo` |
| `TemplateService` | Template rendering | `ListTemplates`, `GetTemplate`, `RenderTemplate` |
| `DependencyService` | Dependency analysis | `AnalyzePack`, `AnalyzeProject`, `CheckConflicts` |

### Proto Definitions

```protobuf
syntax = "proto3";
package yappc.api.grpc;

// Pack Service - Discovery and management
service PackService {
  rpc ListPacks(ListPacksRequest) returns (PackListResponse);
  rpc GetPack(GetPackRequest) returns (PackInfo);
  rpc SearchPacks(SearchPacksRequest) returns (PackListResponse);
  rpc ValidatePack(ValidatePackRequest) returns (PackValidationResult);
  rpc GetPacksByLanguage(GetPacksByLanguageRequest) returns (PackListResponse);
  rpc GetPacksByCategory(GetPacksByCategoryRequest) returns (PackListResponse);
  rpc CheckUpdates(CheckUpdatesRequest) returns (UpdateAvailability);
}

// Project Service - Lifecycle operations with streaming
service ProjectService {
  rpc CreateProject(CreateProjectRequest) returns (CreateResult);
  rpc CreateProjectWithProgress(CreateProjectRequest) returns (stream ProgressUpdate);
  rpc AddFeature(AddFeatureRequest) returns (AddResult);
  rpc AddFeatureWithProgress(AddFeatureRequest) returns (stream ProgressUpdate);
  rpc UpdateProject(UpdateProjectRequest) returns (UpdateResult);
  rpc GetProjectInfo(GetProjectInfoRequest) returns (ProjectInfo);
  rpc GetProjectState(GetProjectStateRequest) returns (ProjectState);
  rpc ValidateProject(ValidateProjectRequest) returns (ProjectValidationResult);
  rpc IsYappcProject(IsYappcProjectRequest) returns (IsYappcProjectResponse);
  rpc ListGeneratedFiles(ListGeneratedFilesRequest) returns (FileListResponse);
}

// Template Service - Template operations
service TemplateService {
  rpc ListTemplates(ListTemplatesRequest) returns (TemplateListResponse);
  rpc GetTemplate(GetTemplateRequest) returns (TemplateInfo);
  rpc RenderTemplate(RenderTemplateRequest) returns (RenderResult);
  rpc PreviewRender(RenderTemplateRequest) returns (RenderPreview);
}

// Dependency Service - Analysis and conflict detection
service DependencyService {
  rpc AnalyzePack(AnalyzePackRequest) returns (DependencyAnalysis);
  rpc AnalyzeProject(AnalyzeProjectRequest) returns (DependencyAnalysis);
  rpc CheckConflicts(CheckConflictsRequest) returns (ConflictListResponse);
  rpc CheckAddConflicts(CheckAddConflictsRequest) returns (ConflictListResponse);
}
```

### Client Examples

#### Java (gRPC-Java)

```java
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.ghatana.yappc.api.grpc.*;

// Create channel
ManagedChannel channel = ManagedChannelBuilder
        .forAddress("localhost", 50051)
        .usePlaintext()
        .build();

// Create stubs
PackServiceGrpc.PackServiceBlockingStub packService = 
        PackServiceGrpc.newBlockingStub(channel);
ProjectServiceGrpc.ProjectServiceBlockingStub projectService = 
        ProjectServiceGrpc.newBlockingStub(channel);

// List packs
PackListResponse packs = packService.listPacks(
        ListPacksRequest.newBuilder()
                .setLanguage("java")
                .setCategory("backend")
                .build());

System.out.println("Found " + packs.getPacksCount() + " packs");
packs.getPacksList().forEach(pack -> 
    System.out.println(" - " + pack.getName() + " v" + pack.getVersion()));

// Create project with streaming progress
Iterator<ProgressUpdate> progress = projectService.createProjectWithProgress(
        CreateProjectRequest.newBuilder()
                .setProjectName("my-service")
                .setPackName("java-service-spring-gradle")
                .putVariables("packageName", "com.example.myservice")
                .build());

while (progress.hasNext()) {
    ProgressUpdate update = progress.next();
    System.out.printf("[%d%%] %s%n", update.getPercentage(), update.getMessage());
}

// Cleanup
channel.shutdown();
```

#### Go (gRPC-Go)

```go
package main

import (
    "context"
    "io"
    "log"

    "google.golang.org/grpc"
    "google.golang.org/grpc/credentials/insecure"
    pb "path/to/yappc/proto"
)

func main() {
    conn, err := grpc.NewClient("localhost:50051",
        grpc.WithTransportCredentials(insecure.NewCredentials()))
    if err != nil {
        log.Fatalf("Failed to connect: %v", err)
    }
    defer conn.Close()

    packClient := pb.NewPackServiceClient(conn)
    projectClient := pb.NewProjectServiceClient(conn)

    // List packs
    resp, _ := packClient.ListPacks(context.Background(), &pb.ListPacksRequest{
        Language: "java",
    })
    log.Printf("Found %d packs", len(resp.GetPacks()))

    // Create project with streaming
    stream, _ := projectClient.CreateProjectWithProgress(context.Background(),
        &pb.CreateProjectRequest{
            ProjectName: "my-service",
            PackName:    "java-service-spring-gradle",
            Variables:   map[string]string{"packageName": "com.example"},
        })

    for {
        update, err := stream.Recv()
        if err == io.EOF {
            break
        }
        log.Printf("[%d%%] %s", update.GetPercentage(), update.GetMessage())
    }
}
```

#### Node.js (gRPC-Node)

```javascript
const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');

const packageDefinition = protoLoader.loadSync('yappc.proto');
const yappc = grpc.loadPackageDefinition(packageDefinition).yappc.api.grpc;

const client = new yappc.PackService('localhost:50051',
    grpc.credentials.createInsecure());

// List packs
client.listPacks({ language: 'java' }, (err, response) => {
    console.log(`Found ${response.packs.length} packs`);
    response.packs.forEach(pack => {
        console.log(` - ${pack.name} v${pack.version}`);
    });
});

// Create project with streaming (using ProjectService)
const projectClient = new yappc.ProjectService('localhost:50051',
    grpc.credentials.createInsecure());

const stream = projectClient.createProjectWithProgress({
    projectName: 'my-service',
    packName: 'java-service-spring-gradle',
    variables: { packageName: 'com.example' }
});

stream.on('data', (update) => {
    console.log(`[${update.percentage}%] ${update.message}`);
});

stream.on('end', () => {
    console.log('Project creation complete!');
});
```

#### Command Line with grpcurl

```bash
# List all services (requires reflection enabled)
grpcurl -plaintext localhost:50051 list

# Describe a service
grpcurl -plaintext localhost:50051 describe yappc.api.grpc.PackService

# List packs
grpcurl -plaintext -d '{"language": "java"}' \
    localhost:50051 yappc.api.grpc.PackService/ListPacks

# Get pack details
grpcurl -plaintext -d '{"packName": "java-service-spring-gradle"}' \
    localhost:50051 yappc.api.grpc.PackService/GetPack

# Create project
grpcurl -plaintext -d '{
  "projectName": "my-service",
  "packName": "java-service-spring-gradle",
  "outputPath": "/workspace/projects",
  "variables": {"packageName": "com.example"}
}' localhost:50051 yappc.api.grpc.ProjectService/CreateProject

# Create project with streaming progress
grpcurl -plaintext -d '{
  "projectName": "my-service",
  "packName": "java-service-spring-gradle"
}' localhost:50051 yappc.api.grpc.ProjectService/CreateProjectWithProgress

# Analyze dependencies
grpcurl -plaintext -d '{"packName": "java-service-spring-gradle"}' \
    localhost:50051 yappc.api.grpc.DependencyService/AnalyzePack
```

### gRPC Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `port` | `50051` | gRPC server port |
| `packsPath` | `~/.yappc/packs` | Path to pack definitions |
| `workspacePath` | Current dir | Default workspace directory |
| `enableReflection` | `false` | Enable gRPC reflection service |
| `maxMessageSize` | `16MB` | Maximum message size |

## HTTP Server Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `port` | `8080` | Server port |
| `host` | `0.0.0.0` | Bind address |
| `enableSwagger` | `true` | Enable Swagger UI |
| `enableWebSocket` | `true` | Enable WebSocket support |
| `enableCors` | `true` | Enable CORS |
| `corsOrigin` | `*` | Allowed CORS origins |
| `maxRequestSize` | `10MB` | Max request body size |
| `requestTimeoutMs` | `30000` | Request timeout |

## Docker Deployment

```dockerfile
FROM eclipse-temurin:21-jre-alpine

COPY build/libs/yappc-http-*.jar /app/yappc-http.jar

ENV YAPPC_PORT=8080
ENV YAPPC_PACKS_PATH=/packs

EXPOSE 8080

CMD ["java", "-jar", "/app/yappc-http.jar"]
```

```bash
# Build and run
docker build -t yappc-server .
docker run -p 8080:8080 -v /path/to/packs:/packs yappc-server
```

## License

Apache License 2.0 - See [LICENSE](../../../../LICENSE) for details.

