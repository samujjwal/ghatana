# YAPPC Service Organization

## Overview

The YAPPC development environment is organized around a **single JVM deployment model** where Canvas AI and AEP are integrated as libraries into the main YAPPC backend, rather than running as separate services.

## Service Architecture

### Core YAPPC Services (Single Deployment Unit)

```
YAPPC Java Backend (port 7003) [Single JVM Process]
├── YAPPC REST API
├── Canvas AI Service (integrated as library, not separate gRPC service)
└── AEP (Agentic Event Processor) in LIBRARY MODE
    └── Embedded in Java process
    └── No external HTTP service required for dev
    └── Managed by AepClientFactory based on AEP_MODE=library
```

**Key Implementation Files:**

- Backend build configuration: `backend/api/build.gradle.kts`
  - Canvas AI Service dependency: `implementation(project(":products:yappc:canvas-ai-service"))`
- Java backend AEP integration: `backend/api/src/main/java/com/ghatana/yappc/api/aep/`
  - `AepConfig.java` - Configuration management
  - `AepClient.java` - Unified interface
  - `AepService.java` - Service layer for injection
  - `AepClientFactory.java` - Creates appropriate client based on AEP_MODE

### API Gateway (Separate Node.js Process)

```
Node.js API Gateway (port 7002) [Separate Process]
├── GraphQL API (GraphQL Yoga + Fastify)
├── REST routing to Java Backend
└── Database integration (PostgreSQL via Prisma)
```

**Key Implementation Files:**

- API server: `app-creator/apps/api/package.json`
- Main entry point: `app-creator/apps/api/src/index.ts`
- Framework: Fastify + GraphQL Yoga + Prisma

### React Frontend

```
React Frontend (port 7001) [Separate Process]
├── App Creator UI
├── Vite dev server
└── HMR (Hot Module Replacement) enabled
```

**Key Implementation Files:**

- Frontend config: `app-creator/apps/web/package.json`
- Build tools: Vite
- Canvas implementation: `app-creator/libs/canvas/`

### Infrastructure Services (Docker Containers)

```
Shared Infrastructure (Docker)
├── PostgreSQL (port 5432)
├── Redis (port 6379)
└── MinIO (port 9000)
```

## Why This Architecture?

### Problem with Separate Services

- **Canvas AI as separate gRPC service**: Adds network latency, complexity, and requires gRPC-to-REST translation
- **AEP as external HTTP service**: Unnecessary process overhead in development, coupling issues
- **Separate service processes**: Harder to debug, more memory usage, complex startup orchestration

### Benefits of Integrated Approach

- **Single JVM**: All backend logic runs in one process
  - Simplified debugging with shared call stacks
  - Lower memory overhead (~800MB total for 3 services)
  - Faster startup (30-40 seconds)
  - No inter-process communication latency
- **AEP Library Mode**:
  - Direct Java-to-Java calls, no HTTP overhead
  - Environment variable driven (`AEP_MODE=library`)
  - Seamless production transition with `AEP_MODE=service`
- **Canvas AI Integration**:
  - gRPC endpoints available through YAPPC backend HTTP server
  - Same JVM = shared thread pool, memory management
  - Easier testing with unified dependency injection

## Development Startup Flow

### Using `run-dev.sh`

```bash
#!/bin/bash
[1/4] Cleanup YAPPC ports (7001-7003)
      └─ Kill any existing YAPPC Java Backend processes
      └─ Kill any existing Node.js API Gateway processes
      └─ Kill any existing React Frontend processes

[2/4] Verify/Start Shared Services (Docker)
      ├─ PostgreSQL (port 5432)
      ├─ Redis (port 6379)
      └─ MinIO (port 9000)

[3/4] Start YAPPC Java Backend (with Canvas AI + AEP integrated)
      ├─ Set AEP_MODE=library environment variable
      ├─ Build: ./gradlew :products:yappc:backend:api:run
      ├─ Canvas AI service loaded via build dependency
      ├─ AEP runs as embedded library
      └─ Wait for startup confirmation

[4/4] Start Node.js API Gateway + React Frontend (parallel)
      ├─ Node.js API (port 7002)
      │  ├─ GraphQL Yoga endpoint
      │  └─ REST routing to backend
      └─ React Frontend (port 7001)
         ├─ Vite dev server
         └─ HMR enabled for hot reloading
```

**Total startup time:** ~30-40 seconds
**Memory usage:** ~800MB (1 JVM + Node.js + React dev server)

## Production Considerations

### AEP Mode Switching

```java
// Library Mode (Development)
export AEP_MODE=library
export AEP_LIBRARY_PATH=./aep-lib.jar

// Service Mode (Production)
export AEP_MODE=service
export AEP_SERVICE_HOST=aep-service.production:8080
```

### Canvas AI Service

In production:

- Can be deployed as separate gRPC service if needed
- But for microservices, integration into backend is recommended
- Use `AepMode.SERVICE` for external AEP communication

## File Structure

```
yappc/
├── backend/
│   └── api/
│       ├── build.gradle.kts                    # Canvas AI dependency added
│       └── src/main/java/com/ghatana/yappc/api/
│           ├── aep/                            # AEP Java backend integration
│           │   ├── AepMode.java
│           │   ├── AepConfig.java
│           │   ├── AepClient.java
│           │   ├── AepService.java
│           │   ├── AepClientFactory.java
│           │   └── AEP_INTEGRATION_GUIDE.md
│           └── ApiApplication.java             # Main backend entry point
├── canvas-ai-service/
│   ├── build.gradle.kts
│   ├── src/main/java/com/ghatana/yappc/canvas/ai/
│   │   └── CanvasAIServer.java                 # Integrated into backend JVM
│   └── (Canvas AI implementation)
├── app-creator/
│   ├── apps/
│   │   ├── api/                                # Node.js GraphQL API
│   │   │   ├── package.json
│   │   │   ├── src/index.ts
│   │   │   └── (GraphQL implementation)
│   │   └── web/                                # React Frontend
│   │       ├── package.json
│   │       └── (Vite + React implementation)
│   └── libs/
│       └── canvas/                             # Canvas library
├── docker-compose.yml                          # PostgreSQL, Redis, MinIO
├── run-dev.sh                                  # Development startup script
├── SERVICE_ORGANIZATION.md                     # This file
├── RUN_DEV_GUIDE.md                           # Detailed startup guide
└── (other project files)
```

## Integration Points

### Backend to AEP Integration

```java
// In any YAPPC backend controller/service:
@Inject
private AepService aepService;

// Publish event
aepService.publishEvent("canvas.action", actionData);

// Execute action
ActionResult result = aepService.executeAction("render.canvas", params);

// Listen to events
aepService.subscribeToEvents("canvas.*", eventHandler);
```

### Backend to Canvas AI

```java
// Canvas AI gRPC endpoints available through YAPPC backend
// Example: http://localhost:7003/canvas/generate
// The Canvas AI service runs in the same JVM, registered with ActiveJ HTTP
```

### Frontend to Backend

```typescript
// React frontend calls YAPPC backend REST API
fetch("http://localhost:7003/api/canvas/actions", {
  method: "POST",
  body: JSON.stringify(canvasAction),
});

// Or through Node.js API Gateway GraphQL
fetch("http://localhost:7002/graphql", {
  method: "POST",
  body: JSON.stringify({ query: "..." }),
});
```

### Node.js API to Backend

```typescript
// GraphQL resolvers can call backend API
const response = await fetch("http://localhost:7003/api/...");
```

## Troubleshooting

### Canvas AI Not Loading

- Check that `build.gradle.kts` includes Canvas AI dependency
- Verify Canvas AI is being compiled: `./gradlew :products:yappc:backend:api:build`
- Check logs: `tail -f /tmp/yappc-backend.log`

### AEP Events Not Being Processed

- Check `AEP_MODE` environment variable is set to `library`
- Verify `AepService` is injected into controller
- Check that event listeners are registered before publishing

### Slow Backend Startup

- Current baseline: ~20-30 seconds for full compilation
- First build will be slower (dependency resolution)
- Subsequent runs should be faster if using Gradle daemon
- Check system resources (RAM, CPU) if startup > 60 seconds

## Next Steps

1. **Verify Canvas AI Integration**
   - Run `./run-dev.sh` to start the environment
   - Check that Canvas AI gRPC endpoints are available through backend
   - Verify no separate Canvas AI service is started

2. **Test AEP Library Mode**
   - Publish events through `AepService`
   - Verify events are processed without external AEP service
   - Test production mode transition with `AEP_MODE=service`

3. **Monitor Performance**
   - Measure startup time with integrated Canvas AI
   - Monitor JVM memory usage during operation
   - Compare with previous service-based architecture

4. **Update Documentation**
   - Update deployment guides if service architecture changes
   - Document how to scale individual services if needed
   - Create runbooks for common operations

## References

- [RUN_DEV_GUIDE.md](RUN_DEV_GUIDE.md) - Detailed startup guide with troubleshooting
- [backend/api/src/main/java/com/ghatana/yappc/api/aep/AEP_INTEGRATION_GUIDE.md](backend/api/src/main/java/com/ghatana/yappc/api/aep/AEP_INTEGRATION_GUIDE.md) - Java backend AEP integration
- [run-dev.sh](run-dev.sh) - Development startup script
