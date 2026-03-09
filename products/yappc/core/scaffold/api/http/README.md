# YAPPC HTTP API Module

RESTful and WebSocket API server for YAPPC scaffold operations.

## Features

- **REST API**: Full CRUD operations for packs, projects, templates, and dependencies
- **WebSocket**: Real-time progress updates during project generation
- **JSON API**: All responses in JSON format
- **CORS Support**: Configurable cross-origin support for web clients
- **Lightweight**: Built on Javalin 6 for minimal footprint

## Quick Start

```java
import com.ghatana.yappc.api.http.*;

// Start server with defaults
YappcServer server = YappcServer.create().start();

// Server is now running on http://localhost:8080
// Swagger UI at http://localhost:8080/swagger

// Stop when done
server.stop();
```

## Custom Configuration

```java
YappcServerConfig config = YappcServerConfig.builder()
        .port(9090)
        .host("localhost")
        .packsPath(Paths.get("/custom/packs"))
        .workspacePath(Paths.get("/custom/workspace"))
        .enableCors(true)
        .corsOrigin("https://app.example.com")
        .enableWebSocket(true)
        .maxRequestSize(20 * 1024 * 1024) // 20MB
        .requestTimeoutMs(60000) // 60 seconds
        .build();

YappcServer server = YappcServer.create(config).start();
```

## API Endpoints

### Health
- `GET /health` - Server health check

### Packs
- `GET /api/v1/packs` - List all packs (with filtering)
- `GET /api/v1/packs/{name}` - Get pack details
- `GET /api/v1/packs/languages` - List available languages
- `GET /api/v1/packs/categories` - List available categories
- `GET /api/v1/packs/platforms` - List available platforms
- `GET /api/v1/packs/{name}/validate` - Validate pack
- `GET /api/v1/packs/{name}/variables` - Get pack variables
- `POST /api/v1/packs/refresh` - Refresh pack cache

### Projects
- `POST /api/v1/projects` - Create new project
- `POST /api/v1/projects/add-feature` - Add feature to project
- `POST /api/v1/projects/update` - Update project
- `GET /api/v1/projects/info?path=...` - Get project info
- `GET /api/v1/projects/state?path=...` - Get project state
- `GET /api/v1/projects/validate?path=...` - Validate project
- `GET /api/v1/projects/check-updates?path=...` - Check for updates
- `GET /api/v1/projects/features?path=...` - List project features
- `POST /api/v1/projects/export` - Export project state
- `POST /api/v1/projects/import` - Import project state

### Templates
- `POST /api/v1/templates/render` - Render template
- `GET /api/v1/templates/helpers` - List available helpers
- `POST /api/v1/templates/validate` - Validate template syntax

### Dependencies
- `GET /api/v1/dependencies/analyze/pack/{name}` - Analyze pack dependencies
- `POST /api/v1/dependencies/analyze/project` - Analyze project dependencies
- `POST /api/v1/dependencies/conflicts` - Check pack conflicts
- `POST /api/v1/dependencies/add-conflicts` - Check conflicts when adding pack

### WebSocket
- `WS /ws/progress?sessionId=...` - Real-time progress updates

## Example Usage

### Create a Project
```bash
curl -X POST http://localhost:8080/api/v1/projects \
  -H "Content-Type: application/json" \
  -d '{
    "packName": "java-service-spring-gradle",
    "projectName": "my-service",
    "targetPath": "/home/user/projects",
    "variables": {"groupId": "com.example"}
  }'
```

### List Packs
```bash
curl "http://localhost:8080/api/v1/packs?language=java"
```

### Render Template
```bash
curl -X POST http://localhost:8080/api/v1/templates/render \
  -H "Content-Type: application/json" \
  -d '{
    "template": "Hello, {{name}}!",
    "variables": {"name": "World"}
  }'
```

## WebSocket Client Example

```javascript
const ws = new WebSocket('ws://localhost:8080/ws/progress?sessionId=my-session');

ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  if (msg.type === 'progress') {
    console.log(`Progress: ${msg.data.current}/${msg.data.total}`);
  } else if (msg.type === 'complete') {
    console.log('Operation complete!', msg.result);
  }
};
```

## Docker

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY build/libs/*.jar /app/server.jar
ENV YAPPC_PORT=8080
EXPOSE 8080
CMD ["java", "-jar", "/app/server.jar"]
```

## License

Apache License 2.0
