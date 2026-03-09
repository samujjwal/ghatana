# Software-Org Launcher

Main application entry point for the Software-Org product.

## Overview

The launcher module provides a single entry point for starting the entire Software-Org application. It handles:

- Configuration loading from YAML files
- Virtual-app framework initialization
- HTTP API server startup
- Department and agent bootstrapping
- Graceful shutdown

## Building

```bash
# Build the module
./gradlew :products:software-org:launcher:build

# Build fat JAR
./gradlew :products:software-org:launcher:fatJar
```

## Running

### Default Configuration

```bash
java -jar launcher/build/libs/launcher-all.jar
```

### Custom Configuration

```bash
# Custom config path
java -jar launcher-all.jar --config-path=/path/to/configs

# Custom port
java -jar launcher-all.jar --port=9090

# Custom environment
java -jar launcher-all.jar --env=production

# Enable hot-reload
java -jar launcher-all.jar --enable-hot-reload

# Disable metrics
java -jar launcher-all.jar --no-metrics

# Combined
java -jar launcher-all.jar \
  --config-path=/path/to/configs \
  --port=9090 \
  --env=production \
  --enable-hot-reload
```

### Environment Variables

```bash
# Config path
export SOFTWARE_ORG_CONFIG_PATH=/path/to/configs

# API port
export SOFTWARE_ORG_API_PORT=9090

# Environment
export SOFTWARE_ORG_ENVIRONMENT=production

# Run
java -jar launcher-all.jar
```

## Configuration

### Default Paths

- **Config Base**: `libs/java/software-org/src/main/resources`
- **API Port**: `8080`
- **Environment**: `development`

### Configuration Structure

```
resources/
├── config/
│   └── org.yaml              # Organization metadata
├── devsecops/
│   ├── personas/             # Persona definitions
│   ├── departments/          # Department configurations
│   ├── phases/               # Development phases
│   ├── stages/               # Stage mappings
│   ├── agents/               # Agent definitions
│   ├── services/             # Service configurations
│   ├── integrations/         # Integration configs
│   ├── flows/                # Flow definitions
│   └── kpis/                 # KPI definitions
├── workflows/                # Workflow definitions
└── operations/
    └── operators/            # Operator definitions
```

## API Endpoints

Once started, the following endpoints are available:

### Health & Metrics

- `GET /health` - Health check
- `GET /metrics` - Prometheus metrics

### Configuration

- `GET /api/config/org` - Get organization configuration
- `POST /api/config/reload` - Reload all configurations

### Entities

- `GET /api/personas` - List all personas
- `GET /api/personas/:id` - Get persona by ID
- `POST /api/personas` - Create persona
- `PUT /api/personas/:id` - Update persona
- `DELETE /api/personas/:id` - Delete persona

Similar endpoints exist for:

- `/api/departments`
- `/api/agents`
- `/api/workflows`
- `/api/phases`
- `/api/stages`
- `/api/operators`
- `/api/services`
- `/api/integrations`
- `/api/flows`
- `/api/kpis`

## Logging

Logs are written to:

- **Console**: All INFO and above
- **File**: `logs/software-org.log` (rotated daily, 30 days retention)

### Log Levels

- `com.ghatana.softwareorg.*` - DEBUG
- `com.ghatana.virtualorg.*` - INFO
- `com.ghatana.http.*` - INFO
- Third-party libraries - WARN

## Development

### Project Structure

```
launcher/
├── build.gradle.kts
├── README.md
└── src/
    ├── main/
    │   ├── java/com/ghatana/softwareorg/launcher/
    │   │   ├── SoftwareOrgLauncher.java      # Main entry point
    │   │   ├── LauncherConfig.java           # Configuration
    │   │   ├── ConfigurationLoader.java      # YAML loader
    │   │   ├── OrgConfiguration.java         # Config model
    │   │   ├── VirtualAppBootstrap.java      # Framework init
    │   │   └── ApiServer.java                # HTTP server
    │   └── resources/
    │       └── logback.xml                   # Logging config
    └── test/
        └── java/com/ghatana/softwareorg/launcher/
            └── ...                           # Unit tests
```

### Adding New Configuration Types

1. Update `ConfigurationLoader.loadAll()` to load new config directory
2. Add field to `OrgConfiguration` and builder
3. Add API endpoint in `ApiServer` (TODO)
4. Update documentation

### Testing

```bash
# Run tests
./gradlew :products:software-org:launcher:test

# Run with coverage
./gradlew :products:software-org:launcher:test jacocoTestReport
```

## Deployment

### Docker

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY launcher-all.jar /app/launcher.jar
COPY resources/ /app/resources/
WORKDIR /app
EXPOSE 8080
CMD ["java", "-jar", "launcher.jar", "--config-path=/app/resources"]
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: software-org
spec:
  replicas: 1
  selector:
    matchLabels:
      app: software-org
  template:
    metadata:
      labels:
        app: software-org
    spec:
      containers:
        - name: software-org
          image: ghatana/software-org:latest
          ports:
            - containerPort: 8080
          env:
            - name: SOFTWARE_ORG_ENVIRONMENT
              value: "production"
          volumeMounts:
            - name: config
              mountPath: /app/resources
      volumes:
        - name: config
          configMap:
            name: software-org-config
```

## Troubleshooting

### Config Files Not Found

```
Error: Config directory does not exist: /path/to/configs
```

**Solution**: Ensure config path is correct and accessible.

### Port Already in Use

```
Error: Port 8080 is already in use
```

**Solution**: Use a different port with `--port=9090` or stop the conflicting service.

### Out of Memory

```
Error: Java heap space
```

**Solution**: Increase heap size:

```bash
java -Xmx2g -jar launcher-all.jar
```

## Next Steps

See [RESTRUCTURING_PLAN.md](../docs/RESTRUCTURING_PLAN.md) for:

- Phase 2: Config Service implementation
- Phase 3: Core module consolidation
- Phase 4: Department consolidation
- Phase 5: Fastify backend integration

## License

Copyright © 2025 Ghatana. All rights reserved.
