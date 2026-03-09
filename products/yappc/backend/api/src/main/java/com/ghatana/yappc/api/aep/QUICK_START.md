# AEP Integration - Quick Start

## 1 Minute Setup

### Copy Configuration to ProductionModule

```java
@Provides
AepConfig aepConfig() {
  return AepConfig.fromEnvironment("production");
}

@Provides
AepClient aepClient(AepConfig config) throws AepException {
  return AepClientFactory.create(config);
}

@Provides
AepService aepService(AepClient client) {
  return new AepService(client);
}
```

### Use in Controller

```java
@Inject AepService aepService;

public HttpResponse createShape(String shapeJson) {
  // Your code...
  aepService.publishShapeCreatedEvent(shapeJson);
  return ok();
}
```

### Set Environment Variable

```bash
# Development (default - library mode)
# No env var needed, uses defaults

# Production
export AEP_MODE=service
export AEP_SERVICE_HOST=aep-service
export AEP_SERVICE_PORT=7004
```

## Files Reference

| File | Purpose |
|------|---------|
| `AepMode.java` | LIBRARY or SERVICE enum |
| `AepConfig.java` | Configuration from environment |
| `AepClient.java` | Interface for pub/sub/execute |
| `AepService.java` | Service layer for controllers |
| `AepClientFactory.java` | Creates appropriate client |

## Architecture

```
Frontend → Backend REST API → AepService →┐
                                            ├─→ AepClient Interface
                                            │
                                    ┌───────┴────────┐
                                    │                │
                            Library Mode      Service Mode
                            (dev)             (prod)
                            │                 │
                            └─ In-Process ─ HTTP to external
```

## Error Handling

```java
try {
  aepService.publishEvent(...);
} catch (AepException e) {
  logger.warn("AEP unavailable", e);
  // For non-critical ops, continue anyway
}
```

## That's It!

- Frontend is completely unchanged ✅
- Backend handles all AEP complexity ✅
- Works in dev (library) and prod (service) ✅
- No TypeScript configuration needed ✅

See `AEP_INTEGRATION_GUIDE.md` for full details.
