# YAPPC Scaffold API Integration Plan

> **Version:** 1.0.0  
> **Created:** 2025-12-05  
> **Status:** 📋 PLANNING

## Executive Summary

This plan outlines how to expose YAPPC Scaffold capabilities as APIs for integration with:
- **IDEs:** VS Code extensions, JetBrains plugins
- **CI/CD:** GitHub Actions, GitLab CI, Jenkins
- **Web Services:** SaaS platforms, internal tooling
- **CLIs:** Other command-line tools and automation scripts

---

## 1. API Architecture

### 1.1 Three Integration Layers

```
┌─────────────────────────────────────────────────────────────────┐
│                      Integration Clients                         │
│  VS Code Extension │ JetBrains Plugin │ Web UI │ GitHub Action  │
└────────────────────┬───────────────────┬────────────────────────┘
                     │                   │
         ┌───────────▼───────────────────▼───────────┐
         │              YAPPC API Gateway              │
         │         (REST / gRPC / GraphQL)            │
         └───────────────────┬───────────────────────┘
                             │
         ┌───────────────────▼───────────────────────┐
         │            YAPPC Core Services             │
         ├─────────────┬─────────────┬───────────────┤
         │ PackService │ ScaffoldSvc │ DependencySvc │
         └─────────────┴─────────────┴───────────────┘
                             │
         ┌───────────────────▼───────────────────────┐
         │              Java Library API              │
         │     (Embeddable, No HTTP Required)         │
         └───────────────────────────────────────────┘
```

### 1.2 API Types

| API Type | Use Case | Protocol | Package |
|----------|----------|----------|---------|
| **Java Library** | JVM-based tools, Gradle plugins | Direct method calls | `yappc-api` |
| **REST API** | Web services, language-agnostic | HTTP/JSON | `yappc-server` |
| **gRPC API** | High-performance, streaming | Protocol Buffers | `yappc-grpc` |
| **CLI (JSON mode)** | Scripts, CI/CD pipelines | Stdout JSON | `yappc-cli` |

---

## 2. Java Library API (Embeddable)

### 2.1 Core Interfaces

```java
// Package: com.ghatana.yappc.api

/**
 * Main entry point for YAPPC programmatic access.
 */
public interface YappcApi {
    
    // === Pack Operations ===
    
    /** List all available packs */
    List<PackInfo> listPacks(PackFilter filter);
    
    /** Get detailed pack information */
    PackDetails getPackDetails(String packName);
    
    /** Validate a pack structure */
    ValidationResult validatePack(Path packPath);
    
    // === Project Operations ===
    
    /** Create a new project from a pack */
    CreateResult createProject(CreateRequest request);
    
    /** Add a feature to an existing project */
    AddResult addFeature(AddFeatureRequest request);
    
    /** Check for available updates */
    UpdateCheckResult checkUpdates(Path projectPath);
    
    /** Apply updates to a project */
    UpdateResult applyUpdates(UpdateRequest request);
    
    // === Dependency Operations ===
    
    /** Analyze project dependencies */
    DependencyGraph analyzeDependencies(Path projectPath);
    
    /** Get upgrade recommendations */
    UpgradeRecommendations getUpgradeRecommendations(Path projectPath);
    
    // === Template Operations ===
    
    /** Render a single template */
    String renderTemplate(String templateContent, Map<String, Object> variables);
}
```

### 2.2 Request/Response Models

```java
// === Create Project ===

public record CreateRequest(
    String projectName,
    String packName,
    Path outputPath,
    Map<String, Object> variables,
    boolean dryRun,
    boolean force
) {
    public static Builder builder() { return new Builder(); }
}

public record CreateResult(
    boolean success,
    Path projectPath,
    List<String> generatedFiles,
    List<String> errors,
    List<String> warnings,
    ProjectState state
) {}

// === Add Feature ===

public record AddFeatureRequest(
    String featureName,
    String featureType,      // e.g., "postgresql", "jwt", "otel"
    Path projectPath,
    Map<String, Object> variables,
    boolean dryRun
) {}

public record AddResult(
    boolean success,
    List<String> addedFiles,
    List<String> modifiedFiles,
    List<String> errors,
    Map<String, String> nextSteps
) {}

// === Pack Info ===

public record PackInfo(
    String name,
    String version,
    String description,
    String language,
    String framework,
    PackType type,
    List<String> tags
) {}

public record PackFilter(
    PackType type,
    String language,
    String platform,
    String searchQuery
) {}
```

### 2.3 Builder Pattern for Fluent API

```java
// Fluent project creation
YappcApi yappc = YappcApi.create();

CreateResult result = yappc.createProject()
    .name("my-service")
    .pack("java-service-spring-gradle")
    .outputPath(Path.of("/projects"))
    .variable("packageName", "com.example.myservice")
    .variable("port", 8080)
    .build();

// Fluent feature addition
AddResult authResult = yappc.addFeature()
    .feature("auth")
    .type("jwt")
    .projectPath(result.projectPath())
    .variable("tokenExpiry", "24h")
    .build();
```

### 2.4 Maven/Gradle Dependency

```xml
<!-- Maven -->
<dependency>
    <groupId>com.ghatana.yappc</groupId>
    <artifactId>yappc-api</artifactId>
    <version>1.0.0</version>
</dependency>
```

```kotlin
// Gradle Kotlin DSL
implementation("com.ghatana.yappc:yappc-api:1.0.0")
```

---

## 3. REST API

### 3.1 Endpoints

```yaml
openapi: 3.1.0
info:
  title: YAPPC Scaffold API
  version: 1.0.0

paths:
  # === Pack Operations ===
  
  /api/v1/packs:
    get:
      summary: List available packs
      parameters:
        - name: type
          in: query
          schema:
            type: string
            enum: [service, application, feature, fullstack]
        - name: language
          in: query
          schema:
            type: string
        - name: search
          in: query
          schema:
            type: string
      responses:
        200:
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/PackInfo'

  /api/v1/packs/{packName}:
    get:
      summary: Get pack details
      responses:
        200:
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/PackDetails'

  /api/v1/packs/{packName}/validate:
    post:
      summary: Validate pack structure
      responses:
        200:
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ValidationResult'

  # === Project Operations ===
  
  /api/v1/projects:
    post:
      summary: Create a new project
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateRequest'
      responses:
        201:
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CreateResult'

  /api/v1/projects/{projectId}/features:
    post:
      summary: Add feature to project
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/AddFeatureRequest'
      responses:
        200:
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AddResult'

  /api/v1/projects/{projectId}/updates:
    get:
      summary: Check for updates
      responses:
        200:
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UpdateCheckResult'
    post:
      summary: Apply updates
      responses:
        200:
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UpdateResult'

  # === Template Operations ===
  
  /api/v1/templates/render:
    post:
      summary: Render a template
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                template:
                  type: string
                variables:
                  type: object
      responses:
        200:
          content:
            application/json:
              schema:
                type: object
                properties:
                  rendered:
                    type: string

  # === Dependency Operations ===
  
  /api/v1/dependencies/analyze:
    post:
      summary: Analyze dependencies
      requestBody:
        content:
          multipart/form-data:
            schema:
              type: object
              properties:
                files:
                  type: array
                  items:
                    type: string
                    format: binary
      responses:
        200:
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/DependencyGraph'

components:
  schemas:
    PackInfo:
      type: object
      properties:
        name:
          type: string
        version:
          type: string
        description:
          type: string
        language:
          type: string
        framework:
          type: string
        type:
          type: string
        tags:
          type: array
          items:
            type: string

    CreateRequest:
      type: object
      required:
        - projectName
        - packName
      properties:
        projectName:
          type: string
        packName:
          type: string
        variables:
          type: object
        dryRun:
          type: boolean
          default: false
```

### 3.2 Server Implementation Options

| Option | Framework | Pros | Cons |
|--------|-----------|------|------|
| **Spring Boot** | Spring WebFlux | Ecosystem, easy | Heavier footprint |
| **Javalin** | Jetty | Lightweight, fast | Less ecosystem |
| **Helidon** | Netty | Cloud-native, GraalVM | Newer |
| **ActiveJ** | Custom | Existing in codebase | Internal only |

**Recommendation:** Use **Javalin** for standalone server, or provide **Spring Boot Starter** for Spring users.

---

## 4. gRPC API (High Performance)

### 4.1 Protocol Buffer Definitions

```protobuf
syntax = "proto3";

package com.ghatana.yappc.api.v1;

option java_package = "com.ghatana.yappc.api.grpc";
option java_multiple_files = true;

// === Services ===

service PackService {
  rpc ListPacks(ListPacksRequest) returns (ListPacksResponse);
  rpc GetPackDetails(GetPackDetailsRequest) returns (PackDetails);
  rpc ValidatePack(ValidatePackRequest) returns (ValidationResult);
}

service ProjectService {
  rpc CreateProject(CreateProjectRequest) returns (CreateProjectResponse);
  rpc AddFeature(AddFeatureRequest) returns (AddFeatureResponse);
  rpc CheckUpdates(CheckUpdatesRequest) returns (UpdateCheckResponse);
  rpc ApplyUpdates(ApplyUpdatesRequest) returns (stream UpdateProgress);
}

service TemplateService {
  rpc RenderTemplate(RenderTemplateRequest) returns (RenderTemplateResponse);
  rpc RenderBatch(stream RenderTemplateRequest) returns (stream RenderTemplateResponse);
}

// === Messages ===

message ListPacksRequest {
  optional string type = 1;
  optional string language = 2;
  optional string platform = 3;
  optional string search = 4;
}

message ListPacksResponse {
  repeated PackInfo packs = 1;
}

message PackInfo {
  string name = 1;
  string version = 2;
  string description = 3;
  string language = 4;
  string framework = 5;
  string type = 6;
  repeated string tags = 7;
}

message CreateProjectRequest {
  string project_name = 1;
  string pack_name = 2;
  string output_path = 3;
  map<string, string> variables = 4;
  bool dry_run = 5;
  bool force = 6;
}

message CreateProjectResponse {
  bool success = 1;
  string project_path = 2;
  repeated string generated_files = 3;
  repeated string errors = 4;
  repeated string warnings = 5;
}

message UpdateProgress {
  string file = 1;
  string status = 2;  // "pending", "updating", "complete", "error"
  string message = 3;
  float progress = 4; // 0.0 - 1.0
}
```

### 4.2 Streaming Use Cases

- **Large project creation:** Stream file generation progress
- **Batch template rendering:** Process multiple templates in parallel
- **Dependency analysis:** Stream vulnerability findings as discovered

---

## 5. CLI JSON Mode

### 5.1 JSON Output Flag

```bash
# Enable JSON output for any command
yappc --output json packs
yappc --output json create my-app --pack java-service-spring-gradle
yappc --output json add database --type postgresql
```

### 5.2 JSON Output Format

```json
// yappc --output json packs --language java
{
  "success": true,
  "data": {
    "packs": [
      {
        "name": "java-service-spring-gradle",
        "version": "1.0.0",
        "description": "Spring Boot 3 service with Gradle",
        "language": "java",
        "framework": "spring-boot",
        "type": "service"
      }
    ]
  },
  "metadata": {
    "count": 1,
    "executionTimeMs": 45
  }
}

// yappc --output json create my-app --pack java-service-spring-gradle
{
  "success": true,
  "data": {
    "projectPath": "/projects/my-app",
    "generatedFiles": [
      "build.gradle.kts",
      "settings.gradle.kts",
      "src/main/java/com/example/Application.java"
    ],
    "state": {
      "packName": "java-service-spring-gradle",
      "packVersion": "1.0.0",
      "createdAt": "2025-12-05T10:00:00Z"
    }
  },
  "errors": [],
  "warnings": []
}
```

---

## 6. SDK Generation

### 6.1 Language SDKs to Generate

| Language | Generator | Package |
|----------|-----------|---------|
| TypeScript | OpenAPI Generator | `@yappc/sdk` |
| Python | OpenAPI Generator | `yappc-sdk` |
| Go | OpenAPI Generator | `github.com/ghatana/yappc-go` |
| Rust | OpenAPI Generator | `yappc-sdk` |

### 6.2 TypeScript SDK Example

```typescript
// Auto-generated from OpenAPI spec
import { YappcClient } from '@yappc/sdk';

const yappc = new YappcClient({
  baseUrl: 'http://localhost:8080/api/v1',
  apiKey: process.env.YAPPC_API_KEY
});

// List packs
const packs = await yappc.packs.list({ language: 'java' });

// Create project
const result = await yappc.projects.create({
  projectName: 'my-service',
  packName: 'java-service-spring-gradle',
  variables: {
    packageName: 'com.example.myservice'
  }
});

// Add feature
await yappc.projects.addFeature(result.projectId, {
  feature: 'database',
  type: 'postgresql'
});
```

---

## 7. Integration Examples

### 7.1 VS Code Extension

```typescript
// VS Code extension using REST API
import * as vscode from 'vscode';
import { YappcClient } from '@yappc/sdk';

export function activate(context: vscode.ExtensionContext) {
  const yappc = new YappcClient({ baseUrl: getServerUrl() });

  // Command: Create New Project
  context.subscriptions.push(
    vscode.commands.registerCommand('yappc.createProject', async () => {
      const packs = await yappc.packs.list();
      
      const selected = await vscode.window.showQuickPick(
        packs.map(p => ({ label: p.name, description: p.description, pack: p }))
      );
      
      if (!selected) return;
      
      const name = await vscode.window.showInputBox({ prompt: 'Project name' });
      
      const result = await yappc.projects.create({
        projectName: name,
        packName: selected.pack.name,
        outputPath: vscode.workspace.workspaceFolders[0].uri.fsPath
      });
      
      vscode.window.showInformationMessage(`Created ${result.generatedFiles.length} files`);
    })
  );
}
```

### 7.2 GitHub Action

```yaml
# .github/workflows/scaffold.yml
name: Scaffold Project

on:
  workflow_dispatch:
    inputs:
      project_name:
        description: 'Project name'
        required: true
      pack_name:
        description: 'Pack to use'
        required: true
        default: 'java-service-spring-gradle'

jobs:
  scaffold:
    runs-on: ubuntu-latest
    steps:
      - uses: ghatana/yappc-action@v1
        with:
          command: create
          project-name: ${{ inputs.project_name }}
          pack: ${{ inputs.pack_name }}
          variables: |
            packageName=com.example.${{ inputs.project_name }}
            port=8080

      - name: Commit scaffolded project
        run: |
          git config user.name github-actions
          git config user.email github-actions@github.com
          git add .
          git commit -m "Scaffold ${{ inputs.project_name }} from ${{ inputs.pack_name }}"
          git push
```

### 7.3 Gradle Plugin

```kotlin
// build.gradle.kts
plugins {
    id("com.ghatana.yappc") version "1.0.0"
}

yappc {
    packsPath = file("packs")
}

tasks.register("scaffoldModule") {
    doLast {
        yappc.createProject {
            name = "new-module"
            pack = "java-library-gradle"
            outputPath = file("modules/new-module")
            variables = mapOf(
                "packageName" to "com.example.newmodule"
            )
        }
    }
}
```

---

## 8. Implementation Phases

### Phase 1: Java Library API (Week 1-2)

| Task | Deliverable |
|------|-------------|
| Define API interfaces | `com.ghatana.yappc.api.*` |
| Implement `YappcApi` facade | `DefaultYappcApi.java` |
| Create request/response models | Records with builders |
| Write unit tests | 80%+ coverage |
| Publish to Maven | `yappc-api:1.0.0` |

### Phase 2: REST API Server (Week 3-4)

| Task | Deliverable |
|------|-------------|
| Set up Javalin server | `yappc-server` module |
| Implement REST endpoints | All CRUD operations |
| Add OpenAPI documentation | Swagger UI at `/docs` |
| Add authentication | API key + JWT |
| Dockerize server | `ghcr.io/ghatana/yappc-server` |

### Phase 3: gRPC API (Week 5)

| Task | Deliverable |
|------|-------------|
| Define protobuf schemas | `contracts/yappc-api.proto` |
| Generate Java stubs | gRPC codegen |
| Implement services | Streaming support |
| Add to server | Same port, different protocol |

### Phase 4: CLI JSON Mode (Week 6)

| Task | Deliverable |
|------|-------------|
| Add `--output json` flag | All commands |
| Structured JSON responses | Consistent schema |
| Error handling | JSON error format |
| Documentation | Updated CLI help |

### Phase 5: SDK Generation (Week 7-8)

| Task | Deliverable |
|------|-------------|
| TypeScript SDK | `@yappc/sdk` on npm |
| Python SDK | `yappc-sdk` on PyPI |
| Go SDK | GitHub module |
| SDK documentation | Usage examples |

### Phase 6: Integrations (Week 9-10)

| Task | Deliverable |
|------|-------------|
| VS Code extension | Marketplace |
| GitHub Action | `ghatana/yappc-action` |
| Gradle plugin | Gradle Plugin Portal |
| JetBrains plugin | IntelliJ Marketplace |

---

## 9. Security Considerations

### 9.1 Authentication

```java
// API Key authentication
public record ApiKeyAuth(String apiKey) implements Authentication {}

// JWT for user sessions
public record JwtAuth(String token, String userId, Set<String> roles) implements Authentication {}
```

### 9.2 Authorization

| Role | Permissions |
|------|-------------|
| `reader` | List packs, render templates |
| `creator` | Create projects, add features |
| `admin` | Manage packs, upload custom packs |

### 9.3 Rate Limiting

```yaml
rate_limits:
  anonymous:
    requests_per_minute: 10
    project_creates_per_hour: 2
  authenticated:
    requests_per_minute: 100
    project_creates_per_hour: 50
  admin:
    requests_per_minute: 1000
    project_creates_per_hour: unlimited
```

---

## 10. Monitoring & Observability

### 10.1 Metrics

```java
// Micrometer metrics
Counter projectsCreated = Counter.builder("yappc.projects.created")
    .tag("pack", packName)
    .tag("language", language)
    .register(registry);

Timer templateRenderTime = Timer.builder("yappc.template.render")
    .register(registry);
```

### 10.2 Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/health` | Kubernetes liveness probe |
| `/ready` | Kubernetes readiness probe |
| `/metrics` | Prometheus metrics |
| `/info` | Version and build info |

---

## Summary

This plan provides a comprehensive strategy for exposing YAPPC Scaffold as an API:

1. **Java Library API** - Direct embedding for JVM tools
2. **REST API** - Universal HTTP access
3. **gRPC API** - High-performance streaming
4. **CLI JSON Mode** - Scripting and CI/CD
5. **Language SDKs** - TypeScript, Python, Go, Rust

The phased implementation ensures incremental value delivery while building toward a complete integration ecosystem.
