# Data-Cloud Router Builder Pattern

## Overview

The Data-Cloud HTTP server uses a `DataCloudRouterBuilder` to construct the routing servlet with domain-specific route groups. This pattern improves maintainability by organizing 200+ route registrations into logical domain groups instead of a monolithic builder chain.

## Architecture

### Before (Monolithic Pattern)

```java
RoutingServlet router = RoutingServlet.builder(eventloop)
    // Health endpoints
    .with(HttpMethod.GET, "/health", healthHandler::handleHealth)
    .with(HttpMethod.GET, "/health/detail", healthHandler::handleHealthDetail)
    // ... 200+ more routes in one method
    .build();
```

**Problems:**
- 200+ route registrations in a single method
- Difficult to understand route organization
- Hard to add new routes without conflicts
- Poor maintainability

### After (Domain-Specific Builder Pattern)

```java
RoutingServlet router = new DataCloudRouterBuilder(eventloop)
    .withHealthRoutes(healthHandler)
    .withEntityRoutes(entityHandler, sseHandler, semanticSearchHandler, exportHandler, anomalyHandler, validationHandler)
    .withEventRoutes(eventHandler)
    .withPipelineRoutes(pipelineCheckpointHandler, workflowExecutionHandler)
    .withCheckpointRoutes(pipelineCheckpointHandler)
    .withAlertRoutes(alertingHandler, sseHandler)
    .withMemoryRoutes(memoryHandler)
    .withBrainRoutes(brainHandler, sseHandler)
    .withLearningRoutes(learningHandler)
    .withAnalyticsRoutes(analyticsHandler)
    .withReportingRoutes(analyticsHandler, workflowExecutionHandler)
    .withModelRoutes(aiModelHandler)
    .withFeatureRoutes(aiModelHandler)
    .withSseRoutes(sseHandler)
    .withWebSocketRoutes(sseHandler)
    .withAiAssistRoutes(aiAssistHandler)
    .withVoiceRoutes(voiceHandler)
    .withGovernanceRoutes(dataLifecycleHandler)
    .withCapabilityRoutes(capabilityRegistryHandler)
    .withLineageRoutes(lineageHandler)
    .withContextRoutes(contextLayerHandler, collectionContextHandler, semanticSearchHandler)
    .withMcpRoutes(mcpToolsHandler)
    .withDataProductRoutes(dataProductHandler)
    .withAutonomyRoutes(autonomyHandler)
    .withAgentCatalogRoutes(agentCatalogHandler)
    .withPluginRoutes(pluginInstallHandler)
    .withStorageCostRoutes(storageCostHandler, httpSupport)
    .withFederatedQueryRoutes(federatedQueryHandler, httpSupport)
    .withTierMigrationRoutes(tierMigrationHandler, httpSupport)
    .build();
```

**Benefits:**
- Each domain has its own builder method
- Easy to understand route organization
- Simple to add new routes to specific domains
- Better maintainability and testability

## Domain Route Groups

### 1. Health Routes (`withHealthRoutes`)
**Handler:** `HealthHandler`
**Routes:**
- `GET /health` - Basic health check
- `GET /health/detail` - Detailed health status
- `GET /health/deep` - Deep health diagnostics
- `GET /ready` - Readiness probe
- `GET /live` - Liveness probe
- `GET /info` - Service information
- `GET /metrics` - Prometheus metrics

### 2. Entity Routes (`withEntityRoutes`)
**Handlers:** `EntityCrudHandler`, `SseStreamingHandler`, `SemanticSearchHandler`, `EntityExportHandler`, `EntityAnomalyHandler`, `EntityValidationHandler`
**Routes:**
- CRUD operations for entities
- Full-text search
- Semantic similarity search
- Bulk operations
- Export and anomaly detection
- Schema validation

### 3. Event Routes (`withEventRoutes`)
**Handler:** `EventHandler`
**Routes:**
- `POST /api/v1/events` - Append event
- `GET /api/v1/events` - Query events
- `GET /api/v1/events/:offset` - Get event by offset

### 4. Pipeline Routes (`withPipelineRoutes`)
**Handlers:** `PipelineCheckpointHandler`, `WorkflowExecutionHandler`
**Routes:**
- Pipeline CRUD operations
- Pipeline execution management
- Execution history and cancellation

### 5. Checkpoint Routes (`withCheckpointRoutes`)
**Handler:** `PipelineCheckpointHandler`
**Routes:**
- Checkpoint management for pipeline state

### 6. Alert Routes (`withAlertRoutes`)
**Handlers:** `AlertingHandler`, `SseStreamingHandler`
**Routes:**
- Alert listing and management
- Alert acknowledgment and resolution
- Alert rules management
- SSE streaming for real-time alerts

### 7. Memory Routes (`withMemoryRoutes`)
**Handler:** `MemoryPlaneHandler`
**Routes:**
- Agent memory storage and retrieval
- Memory search and retention

### 8. Brain Routes (`withBrainRoutes`)
**Handlers:** `BrainHandler`, `SseStreamingHandler`
**Routes:**
- Brain health and configuration
- Attention and salience management
- Pattern matching
- Workspace streaming

### 9. Learning Routes (`withLearningRoutes`)
**Handler:** `LearningHandler`
**Routes:**
- Learning trigger and status
- Review queue management
- Review approval/rejection

### 10. Analytics Routes (`withAnalyticsRoutes`)
**Handler:** `AnalyticsHandler`
**Routes:**
- Analytics query execution
- Query plan inspection
- Aggregation and explanation

### 11. Reporting Routes (`withReportingRoutes`)
**Handlers:** `AnalyticsHandler`, `WorkflowExecutionHandler`
**Routes:**
- Report creation and management
- Execution tracking

### 12. Model Routes (`withModelRoutes`)
**Handler:** `AiModelHandler`
**Routes:**
- AI model registry CRUD
- Model promotion

### 13. Feature Routes (`withFeatureRoutes`)
**Handler:** `AiModelHandler`
**Routes:**
- Feature ingestion and retrieval

### 14. SSE Routes (`withSseRoutes`)
**Handler:** `SseStreamingHandler`
**Routes:**
- Server-Sent Events for real-time updates

### 15. WebSocket Routes (`withWebSocketRoutes`)
**Handler:** `SseStreamingHandler`
**Routes:**
- WebSocket endpoint for real-time notifications

### 16. AI Assist Routes (`withAiAssistRoutes`)
**Handler:** `AiAssistHandler`
**Routes:**
- AI-powered suggestions
- Pipeline drafting and optimization
- Brain explanation

### 17. Voice Routes (`withVoiceRoutes`)
**Handler:** `VoiceGatewayHandler`
**Routes:**
- Voice intent classification
- Intent listing

### 18. Governance Routes (`withGovernanceRoutes`)
**Handler:** `DataLifecycleHandler`
**Routes:**
- Data retention and purge
- Privacy redaction
- Compliance reporting

### 19. Capability Routes (`withCapabilityRoutes`)
**Handler:** `CapabilityRegistryHandler`
**Routes:**
- Runtime capability listing

### 20. Lineage Routes (`withLineageRoutes`)
**Handler:** `LineageHandler`
**Routes:**
- Entity lineage graph
- Impact analysis

### 21. Context Routes (`withContextRoutes`)
**Handlers:** `ContextLayerHandler`, `CollectionContextHandler`, `SemanticSearchHandler`
**Routes:**
- Tenant-scoped context layer
- Collection context management
- RAG (Retrieval-Augmented Generation)

### 22. MCP Routes (`withMcpRoutes`)
**Handler:** `McpToolsHandler`
**Routes:**
- MCP tool discovery and invocation

### 23. Data Product Routes (`withDataProductRoutes`)
**Handler:** `DataProductHandler`
**Routes:**
- Data product catalog management

### 24. Autonomy Routes (`withAutonomyRoutes`)
**Handler:** `AutonomyHandler`
**Routes:**
- Autonomy level management
- Domain-level controls
- Autonomy logs

### 25. Agent Catalog Routes (`withAgentCatalogRoutes`)
**Handler:** `AgentCatalogHandler`
**Routes:**
- Agent catalog listing
- Agent details

### 26. Plugin Routes (`withPluginRoutes`)
**Handler:** `PluginInstallHandler`
**Routes:**
- Plugin lifecycle management (install, enable, disable, upgrade)

### 27. Storage Cost Routes (`withStorageCostRoutes`)
**Handler:** `StorageCostHandler`
**Routes:**
- Query cost estimation
- Collection cost reports

### 28. Federated Query Routes (`withFederatedQueryRoutes`)
**Handler:** `FederatedQueryHandler`
**Routes:**
- Federated Trino query execution

### 29. Tier Migration Routes (`withTierMigrationRoutes`)
**Handler:** `TierMigrationHandler`
**Routes:**
- Manual storage-tier migration

## Adding New Routes

### Step 1: Identify the Domain Group

Determine which domain your new route belongs to. For example:
- If adding a new entity operation → use `withEntityRoutes`
- If adding a new analytics feature → use `withAnalyticsRoutes`
- If adding a completely new domain → create a new `withXxxRoutes` method

### Step 2: Add Route to Existing Domain Group

If the domain already exists, add the route to the corresponding builder method:

```java
public DataCloudRouterBuilder withEntityRoutes(
        EntityCrudHandler entityHandler,
        SseStreamingHandler sseHandler,
        SemanticSearchHandler semanticSearchHandler,
        EntityExportHandler exportHandler,
        EntityAnomalyHandler anomalyHandler,
        EntityValidationHandler validationHandler) {
    builder
        // ... existing routes ...
        .with(HttpMethod.POST, "/api/v1/entities/:collection/new-feature", entityHandler::handleNewFeature);
    return this;
}
```

### Step 3: Create New Domain Group

If adding a completely new domain, create a new builder method:

```java
/**
 * Adds new domain endpoints.
 */
public DataCloudRouterBuilder withNewDomainRoutes(NewDomainHandler newDomainHandler) {
    builder
        .with(HttpMethod.GET, "/api/v1/new-domain", newDomainHandler::handleList)
        .with(HttpMethod.POST, "/api/v1/new-domain", newDomainHandler::handleCreate)
        .with(HttpMethod.GET, "/api/v1/new-domain/:id", newDomainHandler::handleGet);
    return this;
}
```

Then update the usage in `DataCloudHttpServer`:

```java
RoutingServlet router = new DataCloudRouterBuilder(eventloop)
    // ... existing domains ...
    .withNewDomainRoutes(newDomainHandler)
    .build();
```

### Step 4: Update Handler

Add the corresponding handler method to the handler class:

```java
public Promise<HttpResponse> handleNewFeature(HttpRequest request) {
    // Implementation
}
```

### Step 5: Add Tests

Add integration tests to verify the new route is registered:

```java
@Test
@DisplayName("New domain routes are registered")
void newDomainRoutesAreRegistered() {
    Eventloop eventloop = Eventloop.create();
    NewDomainHandler mockHandler = createMockHandler();
    
    DataCloudRouterBuilder builder = new DataCloudRouterBuilder(eventloop);
    RoutingServlet servlet = builder.withNewDomainRoutes(mockHandler).build();
    
    assertThat(servlet).isNotNull();
}
```

## Testing

### Unit Tests

Test individual builder methods:

```java
@Test
void withHealthRoutesRegistersAllHealthEndpoints() {
    Eventloop eventloop = Eventloop.create();
    HealthHandler mockHandler = createMockHealthHandler();
    
    DataCloudRouterBuilder builder = new DataCloudRouterBuilder(eventloop);
    RoutingServlet servlet = builder.withHealthRoutes(mockHandler).build();
    
    assertThat(servlet).isNotNull();
}
```

### Integration Tests

Test the full router construction:

```java
@Test
void routerBuilderConstructsValidRoutingServlet() {
    Eventloop eventloop = Eventloop.create();
    DataCloudRouterBuilder builder = new DataCloudRouterBuilder(eventloop);
    
    RoutingServlet servlet = builder
        .withHealthRoutes(mockHealthHandler)
        .withEntityRoutes(mockEntityHandler, ...)
        // ... all domains
        .build();
    
    assertThat(servlet).isNotNull();
}
```

## Best Practices

1. **Domain Cohesion:** Keep related routes in the same domain group
2. **Handler Separation:** Each domain should have its own handler class
3. **Method Chaining:** All `withXxxRoutes` methods should return `this` for chaining
4. **Null Safety:** Handle null handlers gracefully with fallback responses
5. **Documentation:** Document each domain group with its purpose and routes
6. **Testing:** Add tests for each new domain group
7. **Consistent Naming:** Use `withXxxRoutes` naming convention for domain methods

## Migration Notes

This refactoring was completed as part of task P3-002. The monolithic router builder in `DataCloudHttpServer.start()` was extracted into `DataCloudRouterBuilder` with 25 domain-specific builder methods.

**Files Changed:**
- Created: `DataCloudRouterBuilder.java`
- Modified: `DataCloudHttpServer.java` (replaced monolithic builder with DataCloudRouterBuilder)
- Created: `DataCloudRouterBuilderIntegrationTest.java`
- Created: `DATA_CLOUD_ROUTER_BUILDER.md` (this document)

**Benefits:**
- Improved maintainability
- Better code organization
- Easier to add new routes
- Enhanced testability
