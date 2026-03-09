# Javalin to ActiveJ Migration Guide

## Overview

This guide documents the migration path from Javalin to ActiveJ HTTP for YAPPC scaffold modules, in compliance with **ADR-004** (ActiveJ as the single web framework).

## Current Status

### ✅ Completed
- `platform/build.gradle.kts` — Javalin dependency removed
- `backend/api` — Uses ActiveJ HTTP exclusively
- `services` — Uses ActiveJ HTTP exclusively

### ⚠️ Remaining Work
- `core/scaffold/api` — 17 files still using Javalin

## Affected Files in Scaffold Modules

```
products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/scaffold/api/
├── ScaffoldApiApplication.java          # Main entry point (Javalin app)
├── controller/
│   ├── AnalysisController.java         # Javalin routes
│   ├── BuildController.java            # Javalin routes
│   ├── CodegenController.java          # Javalin routes
│   ├── FrameworkController.java        # Javalin routes
│   ├── HealthController.java           # Javalin routes
│   ├── LanguageController.java         # Javalin routes
│   ├── ProjectController.java          # Javalin routes
│   ├── TemplateController.java         # Javalin routes
│   └── ValidationController.java       # Javalin routes
├── middleware/
│   ├── CorsMiddleware.java             # Javalin middleware
│   ├── ErrorHandler.java               # Javalin error handling
│   └── RequestLogger.java              # Javalin request logging
├── dto/
│   └── (DTOs are framework-agnostic)
└── service/
    └── (Services are framework-agnostic)
```

## Migration Strategy

### Phase 1: Preparation (1-2 days)
1. **Audit dependencies** — Identify all Javalin-specific code
2. **Create ActiveJ equivalents** — Port middleware and error handlers
3. **Update build files** — Add ActiveJ dependencies, mark Javalin as deprecated

### Phase 2: Controller Migration (3-5 days)
1. **Port one controller at a time** — Start with simplest (HealthController)
2. **Test each migration** — Ensure API contracts are preserved
3. **Update integration tests** — Adapt tests to ActiveJ patterns

### Phase 3: Infrastructure Migration (2-3 days)
1. **Port middleware** — CORS, logging, error handling
2. **Update main application** — Replace Javalin app with ActiveJ launcher
3. **Wire dependency injection** — Integrate with ProductionModule

### Phase 4: Cleanup (1 day)
1. **Remove Javalin dependency** — From build.gradle.kts
2. **Delete old code** — Remove Javalin-specific classes
3. **Update documentation** — Reflect new architecture

**Total Estimated Effort:** 7-11 days

## Migration Patterns

### Pattern 1: Simple GET Endpoint

#### Before (Javalin)
```java
app.get("/api/health", ctx -> {
    ctx.json(Map.of("status", "OK"));
});
```

#### After (ActiveJ)
```java
routerBuilder.with(GET, "/api/health", request ->
    HttpResponse.ok200()
        .withJson(Map.of("status", "OK"))
        .toPromise()
);
```

### Pattern 2: POST with Request Body

#### Before (Javalin)
```java
app.post("/api/projects", ctx -> {
    CreateProjectRequest req = ctx.bodyAsClass(CreateProjectRequest.class);
    Project project = projectService.create(req);
    ctx.status(201).json(project);
});
```

#### After (ActiveJ)
```java
routerBuilder.with(POST, "/api/projects", request ->
    request.loadBody()
        .map(body -> JsonUtils.fromJson(body.getString(UTF_8), CreateProjectRequest.class))
        .then(req -> Promise.of(projectService.create(req)))
        .map(project -> HttpResponse.ofCode(201)
            .withJson(JsonUtils.toJson(project))
        )
);
```

### Pattern 3: Path Parameters

#### Before (Javalin)
```java
app.get("/api/projects/:id", ctx -> {
    String id = ctx.pathParam("id");
    Project project = projectService.findById(id);
    ctx.json(project);
});
```

#### After (ActiveJ)
```java
routerBuilder.with(GET, "/api/projects/:id", request -> {
    String id = request.getPathParameter("id");
    return Promise.of(projectService.findById(id))
        .map(project -> HttpResponse.ok200().withJson(JsonUtils.toJson(project)));
});
```

### Pattern 4: Error Handling

#### Before (Javalin)
```java
app.exception(NotFoundException.class, (e, ctx) -> {
    ctx.status(404).json(Map.of("error", e.getMessage()));
});
```

#### After (ActiveJ)
```java
// In GlobalExceptionHandler
private Promise<HttpResponse> handleRequest(HttpRequest request, AsyncServlet next) {
    return next.serve(request)
        .whenException(NotFoundException.class, e ->
            HttpResponse.ofCode(404)
                .withJson(Map.of("error", e.getMessage()))
        );
}
```

### Pattern 5: Middleware (CORS)

#### Before (Javalin)
```java
app.before(ctx -> {
    ctx.header("Access-Control-Allow-Origin", "*");
    ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
});
```

#### After (ActiveJ)
```java
public class CorsMiddleware implements AsyncServlet {
    private final AsyncServlet next;

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        return next.serve(request)
            .map(response -> response
                .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE")
            );
    }
}
```

## Step-by-Step Migration Example

### Migrating HealthController

#### 1. Original Javalin Controller

```java
package com.ghatana.yappc.scaffold.api.controller;

import io.javalin.http.Context;
import java.util.Map;

public class HealthController {
    public static void registerRoutes(Javalin app) {
        app.get("/health", HealthController::health);
        app.get("/health/ready", HealthController::ready);
        app.get("/health/live", HealthController::live);
    }

    private static void health(Context ctx) {
        ctx.json(Map.of("status", "OK"));
    }

    private static void ready(Context ctx) {
        ctx.json(Map.of("status", "READY"));
    }

    private static void live(Context ctx) {
        ctx.json(Map.of("status", "LIVE"));
    }
}
```

#### 2. Migrated ActiveJ Controller

```java
package com.ghatana.yappc.scaffold.api.controller;

import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import java.util.Map;

import static io.activej.http.HttpMethod.GET;

public class HealthController {
    public static void registerRoutes(RoutingServlet.Builder routerBuilder) {
        routerBuilder
            .with(GET, "/health", request ->
                HttpResponse.ok200()
                    .withJson(Map.of("status", "OK"))
                    .toPromise()
            )
            .with(GET, "/health/ready", request ->
                HttpResponse.ok200()
                    .withJson(Map.of("status", "READY"))
                    .toPromise()
            )
            .with(GET, "/health/live", request ->
                HttpResponse.ok200()
                    .withJson(Map.of("status", "LIVE"))
                    .toPromise()
            );
    }
}
```

#### 3. Update Main Application

```java
// Before (Javalin)
public class ScaffoldApiApplication {
    public static void main(String[] args) {
        Javalin app = Javalin.create();
        HealthController.registerRoutes(app);
        app.start(7003);
    }
}

// After (ActiveJ)
public class ScaffoldApiApplication extends UnifiedApplicationLauncher {
    @Override
    protected void setupService(ModuleBuilder builder) {
        // DI setup
    }

    @Override
    protected HttpServer createHttpServer(Injector injector) {
        Eventloop eventloop = injector.getInstance(Eventloop.class);
        
        RoutingServlet.Builder routerBuilder = RoutingServlet.builder(eventloop);
        HealthController.registerRoutes(routerBuilder);
        
        return HttpServer.builder(eventloop, routerBuilder.build())
            .withListenPort(7003)
            .build();
    }
}
```

## Testing Strategy

### 1. Contract Tests
Ensure API contracts are preserved:

```bash
# Run contract tests before migration
./test-contracts.sh --base-url http://localhost:7003

# Run contract tests after migration
./test-contracts.sh --base-url http://localhost:7003
```

### 2. Integration Tests
Update integration tests to use ActiveJ patterns:

```java
// Before (Javalin)
@Test
void testHealthEndpoint() {
    HttpResponse response = client.get("/health");
    assertEquals(200, response.statusCode());
}

// After (ActiveJ)
@Test
void testHealthEndpoint() {
    Promise<HttpResponse> responsePromise = client.get("/health");
    HttpResponse response = responsePromise.getResult();
    assertEquals(200, response.getCode());
}
```

## Rollout Plan

### Week 1: Preparation & Simple Controllers
- Day 1-2: Audit and prepare
- Day 3: Migrate HealthController
- Day 4: Migrate LanguageController
- Day 5: Migrate FrameworkController

### Week 2: Complex Controllers & Middleware
- Day 1-2: Migrate AnalysisController
- Day 3: Migrate BuildController
- Day 4: Migrate CodegenController
- Day 5: Port middleware (CORS, logging, error handling)

### Week 3: Remaining Controllers & Cleanup
- Day 1: Migrate ProjectController
- Day 2: Migrate TemplateController
- Day 3: Migrate ValidationController
- Day 4: Update main application and DI
- Day 5: Remove Javalin dependency, final testing

## Risk Mitigation

### Risk 1: Breaking API Contracts
**Mitigation:** Run contract tests before and after each controller migration

### Risk 2: Performance Regression
**Mitigation:** Benchmark endpoints before and after migration

### Risk 3: Incomplete Error Handling
**Mitigation:** Port error handlers first, test error scenarios explicitly

### Risk 4: DI Integration Issues
**Mitigation:** Migrate controllers one at a time, test DI wiring incrementally

## Success Criteria

- ✅ All 17 files migrated to ActiveJ
- ✅ Zero Javalin dependencies in scaffold modules
- ✅ All contract tests passing
- ✅ Performance benchmarks within 5% of baseline
- ✅ Integration tests updated and passing
- ✅ Documentation updated

## References

- [ADR-004: ActiveJ as Single Web Framework](../../../docs/adr/ADR-004-activej-single-framework.md)
- [ActiveJ HTTP Documentation](https://activej.io/http)
- [Javalin Documentation](https://javalin.io/documentation)
- [YAPPC Backend API](../backend/api/src/main/java/com/ghatana/yappc/api/ApiApplication.java) (reference implementation)

## Appendix: Quick Reference

### Common Javalin → ActiveJ Mappings

| Javalin | ActiveJ |
|---------|---------|
| `ctx.json(obj)` | `HttpResponse.ok200().withJson(JsonUtils.toJson(obj))` |
| `ctx.status(201)` | `HttpResponse.ofCode(201)` |
| `ctx.pathParam("id")` | `request.getPathParameter("id")` |
| `ctx.queryParam("q")` | `request.getQueryParameter("q")` |
| `ctx.bodyAsClass(T.class)` | `JsonUtils.fromJson(body.getString(UTF_8), T.class)` |
| `ctx.header("X-Custom")` | `response.withHeader(HttpHeaders.of("X-Custom"), value)` |
| `app.before(handler)` | Middleware pattern (AsyncServlet wrapper) |
| `app.exception(E.class, handler)` | `promise.whenException(E.class, handler)` |

### Useful ActiveJ Patterns

```java
// Async operation
Promise.of(() -> service.doWork())
    .map(result -> HttpResponse.ok200().withJson(result));

// Error handling
promise.whenException(NotFoundException.class, e ->
    HttpResponse.ofCode(404).withJson(Map.of("error", e.getMessage()))
);

// Multiple async operations
Promises.all(promise1, promise2, promise3)
    .map(results -> HttpResponse.ok200().withJson(results));
```
