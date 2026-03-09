# AEP Integration Guide - YAPPC Backend

## Overview

All AEP (Agentic Event Processor) communication happens in the **Java backend**, NOT in the TypeScript frontend.

The TypeScript frontend is completely **agnostic** to how AEP operates:

- It just calls REST endpoints (e.g., `/api/shapes`, `/api/ai/auto-layout`)
- The backend handles AEP integration transparently
- Frontend never knows if AEP is running as a library or service

## Architecture

```
TypeScript Frontend (app-creator)
    ↓ REST API calls
    ↓
Java Backend (YAPPC)
    ├─ LIBRARY MODE (dev): AEP embedded in Java process
    └─ SERVICE MODE (prod): HTTP calls to external AEP service
```

## Configuration

### Environment Variables

Set these to configure AEP mode:

```bash
# Development (Library Mode - Default)
export AEP_MODE=library
export AEP_LIBRARY_PATH=./aep-lib.jar

# Production/Staging (Service Mode)
export AEP_MODE=service
export AEP_SERVICE_HOST=aep-service.example.com
export AEP_SERVICE_PORT=7004
export AEP_SERVICE_TIMEOUT_MS=10000
```

## Integration in Dependency Injection

### Update ProductionModule.java

Add these providers to `ProductionModule`:

```java
package com.ghatana.yappc.api.config;

import com.ghatana.yappc.api.aep.AepClient;
import com.ghatana.yappc.api.aep.AepClientFactory;
import com.ghatana.yappc.api.aep.AepConfig;
import com.ghatana.yappc.api.aep.AepException;
import com.ghatana.yappc.api.aep.AepService;
import io.activej.inject.annotation.Provides;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProductionModule extends SharedBaseModule {
  private static final Logger logger = LoggerFactory.getLogger(ProductionModule.class);
  private final String environment;

  public ProductionModule(String environment, MetricsCollector metricsCollector) {
    this.environment = environment;
    this.metricsCollector = metricsCollector;
  }

  // ========== AEP Integration ==========

  /**
   * Provides AEP configuration based on environment.
   * Reads from environment variables or uses environment-specific defaults.
   */
  @Provides
  AepConfig aepConfig() {
    logger.info("Loading AEP configuration for environment: {}", environment);
    return AepConfig.fromEnvironment(environment);
  }

  /**
   * Creates AEP client based on configuration.
   * - Library mode: in-process client
   * - Service mode: HTTP client to external AEP service
   */
  @Provides
  AepClient aepClient(AepConfig config) throws AepException {
    logger.info("Creating AEP client with config: {}", config);
    return AepClientFactory.create(config);
  }

  /**
   * Provides AEP service for dependency injection into controllers/services.
   */
  @Provides
  AepService aepService(AepClient client) {
    return new AepService(client);
  }

  // ========== End AEP Integration ==========
}
```

### Update DevelopmentModule.java

Similarly, add to `DevelopmentModule`:

```java
package com.ghatana.yappc.api.config;

import com.ghatana.yappc.api.aep.AepClient;
import com.ghatana.yappc.api.aep.AepClientFactory;
import com.ghatana.yappc.api.aep.AepConfig;
import com.ghatana.yappc.api.aep.AepException;
import com.ghatana.yappc.api.aep.AepService;
import io.activej.inject.annotation.Provides;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DevelopmentModule extends SharedBaseModule {
  private static final Logger logger = LoggerFactory.getLogger(DevelopmentModule.class);

  @Override
  protected void configure() {
    logger.info("Configuring YAPPC API with DEVELOPMENT services");
    super.configure();
  }

  // ========== AEP Integration ==========

  @Provides
  AepConfig aepConfig() {
    logger.info("Using LIBRARY mode for development");
    // Development always defaults to library mode unless overridden
    return AepConfig.fromEnvironment("development");
  }

  @Provides
  AepClient aepClient(AepConfig config) throws AepException {
    logger.info("Creating AEP client: {}", config);
    return AepClientFactory.create(config);
  }

  @Provides
  AepService aepService(AepClient client) {
    return new AepService(client);
  }

  // ========== End AEP Integration ==========
}
```

## Usage in Controllers

### Example: Shape Controller

```java
package com.ghatana.yappc.api.controller;

import com.ghatana.yappc.api.aep.AepException;
import com.ghatana.yappc.api.aep.AepService;
import io.activej.inject.annotation.Inject;
import io.activej.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShapeController {
  private static final Logger logger = LoggerFactory.getLogger(ShapeController.class);

  @Inject
  private AepService aepService;

  /**
   * Create a new shape and publish event to AEP.
   */
  public HttpResponse createShape(String shapeJson) {
    try {
      // Create shape (your logic here)
      Shape shape = parseShape(shapeJson);
      saveShape(shape);

      // Publish event to AEP (library or service mode - doesn't matter)
      String eventId = aepService.publishShapeCreatedEvent(shapeJson);
      logger.info("Published shape.created event: {}", eventId);

      return HttpResponse.ok200()
          .withJson("{ \"id\": \"" + shape.getId() + "\", \"eventId\": \"" + eventId + "\" }");

    } catch (AepException e) {
      logger.error("Failed to publish shape created event", e);
      // Handle gracefully - don't fail the request if AEP is unavailable
      return HttpResponse.ok200()
          .withJson("{ \"id\": \"" + shape.getId() + "\", \"warning\": \"AEP unavailable\" }");
    }
  }

  /**
   * Modify a shape and publish event to AEP.
   */
  public HttpResponse modifyShape(String shapeId, String modificationJson) {
    try {
      Shape shape = loadShape(shapeId);
      applyModification(shape, modificationJson);
      save(shape);

      String eventId = aepService.publishShapeModifiedEvent(modificationJson);
      logger.info("Published shape.modified event: {}", eventId);

      return HttpResponse.ok200().withJson(toJson(shape));

    } catch (AepException e) {
      logger.error("AEP event publishing failed (non-critical)", e);
      // Still return success - shape was modified
      return HttpResponse.ok200().withJson(toJson(shape));
    }
  }
}
```

### Example: AI/Auto-Layout Controller

```java
package com.ghatana.yappc.api.controller;

import com.ghatana.yappc.api.aep.AepException;
import com.ghatana.yappc.api.aep.AepService;
import io.activej.inject.annotation.Inject;
import io.activej.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiController {
  private static final Logger logger = LoggerFactory.getLogger(AiController.class);

  @Inject
  private AepService aepService;

  /**
   * Request auto-layout suggestion from AEP.
   */
  public HttpResponse autoLayout(String canvasStateJson) {
    try {
      // Check if AEP is healthy
      if (!aepService.isHealthy()) {
        return HttpResponse.ok200()
            .withJson("{ \"status\": \"unavailable\", \"reason\": \"AEP service unhealthy\" }");
      }

      // Execute action through AEP (library or service)
      String resultJson = aepService.executeAction("auto-layout", canvasStateJson);
      logger.info("Auto-layout execution completed");

      return HttpResponse.ok200().withJson(resultJson);

    } catch (AepException e) {
      logger.error("Auto-layout request failed", e);
      return HttpResponse.ofCode(503)
          .withJson("{ \"error\": \"AI service temporarily unavailable\" }");
    }
  }

  /**
   * Request pattern detection from AEP.
   */
  public HttpResponse detectPatterns(String canvasStateJson) {
    try {
      String resultJson = aepService.executeAction("detect-patterns", canvasStateJson);
      return HttpResponse.ok200().withJson(resultJson);
    } catch (AepException e) {
      logger.error("Pattern detection failed", e);
      return HttpResponse.ofCode(503)
          .withJson("{ \"error\": \"Pattern detection service unavailable\" }");
    }
  }
}
```

## Frontend Integration

The **frontend does NOT change**. It continues to call backend APIs:

```typescript
// Frontend calls backend endpoint (NOT AEP directly)
const response = await fetch("/api/ai/auto-layout", {
  method: "POST",
  body: JSON.stringify(canvasState),
});

// Backend handles AEP integration internally
// Frontend doesn't care if AEP is library or service mode
```

## Error Handling

### Non-Critical AEP Failures

Some AEP operations are non-critical (e.g., event publishing):

```java
try {
  aepService.publishEvent(...);
} catch (AepException e) {
  logger.warn("Failed to publish event (non-critical)", e);
  // Continue - don't fail the request
}
```

### Critical AEP Failures

Some operations are critical (e.g., auto-layout):

```java
try {
  String result = aepService.executeAction("auto-layout", ...);
  return HttpResponse.ok200().withJson(result);
} catch (AepException e) {
  logger.error("Critical action failed", e);
  // Return error to client
  return HttpResponse.ofCode(503)
      .withJson("{ \"error\": \"Service unavailable\" }");
}
```

## Running in Different Modes

### Development (Library Mode)

```bash
cd /path/to/yappc/backend
# AEP runs embedded in same Java process
./gradlew run
```

### Production (Service Mode)

```bash
# Start AEP service separately
docker run -p 7004:7004 aep-service:latest

# Start YAPPC backend (connects to AEP service)
export AEP_MODE=service
export AEP_SERVICE_HOST=aep-service
export AEP_SERVICE_PORT=7004
./gradlew run
```

## Debugging

### Check which mode is active

```java
// In any service with @Inject AepConfig
if (config.getMode() == AepMode.LIBRARY) {
  logger.info("Using AEP LIBRARY mode");
} else {
  logger.info("Using AEP SERVICE mode at {}", config.getServiceUrl());
}
```

### Check AEP health

```java
// In any service with @Inject AepService
if (aepService.isHealthy()) {
  logger.info("AEP is healthy");
} else {
  logger.warn("AEP is unhealthy or unreachable");
}
```

## Key Principles

1. ✅ **Backend owns AEP integration** - TypeScript never touches AEP
2. ✅ **Frontend agnostic to mode** - Works with library or service mode
3. ✅ **Clear separation of concerns** - Backend handles service logic
4. ✅ **Graceful degradation** - API continues even if AEP fails (when non-critical)
5. ✅ **Single configuration point** - Environment variables control mode

## Files Created

- `AepMode.java` - Enum for library/service modes
- `AepConfig.java` - Configuration management from environment
- `AepClient.java` - Interface for AEP communication
- `AepException.java` - Exception type
- `AepClientFactory.java` - Factory for creating clients
- `AepService.java` - Service layer for controllers
- `package-info.java` - Package documentation with architecture diagrams

All in: `backend/api/src/main/java/com/ghatana/yappc/api/aep/`
