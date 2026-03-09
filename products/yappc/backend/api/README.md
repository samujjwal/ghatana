# YAPPC API Module

> **Yet Another Product Planning Copilot** - Backend API for requirements management, AI suggestions, and architecture analysis.

## Overview

The YAPPC API module provides a high-performance RESTful API built on ActiveJ for the dashboard frontend. It implements the hybrid backend strategy: Java/ActiveJ for core domain logic, with multi-tenant isolation and RBAC authorization.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         YAPPC API Layer                              │
├──────────────┬──────────────┬───────────────┬───────────────────────┤
│ AuditCtrl    │ VersionCtrl  │ RequirementsCtrl │ AISuggestionsCtrl  │
│ AuthCtrl     │ ArchitectureCtrl │ HealthCtrl    │ MetricsCtrl       │
├──────────────┴──────────────┴───────────────┴───────────────────────┤
│                      Service Layer                                   │
│  RequirementService │ AISuggestionService │ ArchitectureAnalysisService │
├──────────────────────────────────────────────────────────────────────┤
│                     Repository Layer                                 │
│  InMemory* (Dev) │ Jdbc* (Prod) │ Cache Layer (Redis)               │
├──────────────────────────────────────────────────────────────────────┤
│                   Infrastructure                                     │
│  PostgreSQL │ Redis │ Prometheus │ Grafana                          │
└──────────────────────────────────────────────────────────────────────┘
```

## Quick Start

### Development Mode

```bash
# Run with Gradle
./gradlew :products:yappc:backend:api:run

# Or with Docker Compose
cd products/yappc/backend/api
docker-compose up -d
```

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Basic health check |
| `/health/ready` | GET | Kubernetes readiness probe |
| `/health/live` | GET | Kubernetes liveness probe |
| `/metrics` | GET | Prometheus metrics |
| `/api/audit/*` | * | Audit event logging |
| `/api/version/*` | * | Entity version control |
| `/api/auth/*` | * | Authorization & personas |
| `/api/requirements/*` | * | Requirements CRUD |
| `/api/ai/*` | * | AI suggestion inbox |
| `/api/architecture/*` | * | Impact analysis |

### Authentication

All `/api/*` endpoints require:
- `Authorization: Bearer <token>` header
- `X-Tenant-Id: <tenant>` header for multi-tenancy
- `X-Persona: <persona>` header for persona-based RBAC

## Project Structure

```
api/
├── src/main/java/com/ghatana/yappc/api/
│   ├── ApiApplication.java          # Main entry point
│   ├── config/
│   │   ├── DevelopmentModule.java   # Dev DI config (in-memory)
│   │   └── ProductionModule.java    # Prod DI config (real services)
│   ├── controller/
│   │   ├── HealthController.java    # Health endpoints
│   │   └── MetricsController.java   # Prometheus metrics
│   ├── domain/
│   │   ├── Requirement.java         # Requirement entity
│   │   └── AISuggestion.java        # AI suggestion entity
│   ├── repository/
│   │   ├── RequirementRepository.java    # Interface
│   │   ├── AISuggestionRepository.java   # Interface
│   │   ├── InMemory*.java                # Dev implementations
│   │   └── jpa/Jdbc*.java                # Prod implementations
│   ├── service/
│   │   ├── RequirementService.java
│   │   ├── AISuggestionService.java
│   │   └── ArchitectureAnalysisService.java
│   ├── audit/AuditController.java
│   ├── version/VersionController.java
│   ├── auth/AuthorizationController.java
│   ├── requirements/RequirementsController.java
│   ├── ai/AISuggestionsController.java
│   └── architecture/ArchitectureController.java
├── src/test/java/.../
│   ├── controller/              # Controller unit tests
│   └── integration/             # Testcontainers integration tests
├── Dockerfile                   # Multi-stage build
├── docker-compose.yml           # Full dev stack
├── prometheus.yml               # Metrics config
├── init-db.sql                  # Database schema
└── openapi.yaml                 # API specification
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | HTTP server port |
| `DB_URL` | - | PostgreSQL JDBC URL |
| `DB_USERNAME` | - | Database username |
| `DB_PASSWORD` | - | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `LOG_LEVEL` | `INFO` | Log verbosity |

### JVM Options

Production containers use:
```
-XX:+UseContainerSupport
-XX:MaxRAMPercentage=75.0
-XX:+UseG1GC
-XX:+HeapDumpOnOutOfMemoryError
```

## Development

### Building

```bash
./gradlew :products:yappc:backend:api:build
```

### Testing

```bash
# Unit tests
./gradlew :products:yappc:backend:api:test

# Integration tests (requires Docker)
./gradlew :products:yappc:backend:api:test --tests "*IntegrationTest"
```

### Code Formatting

```bash
./gradlew :products:yappc:backend:api:spotlessApply
```

## Database

### Schema

The database schema is in `init-db.sql` and includes:

- `yappc.requirements` - Requirements with quality metrics
- `yappc.ai_suggestions` - AI suggestion inbox
- `yappc.audit_events` - Audit trail
- `yappc.entity_versions` - Version history
- `yappc.user_permissions` - User permissions

### Migrations

For production, use Flyway or Liquibase. The `init-db.sql` is for development seeding.

## Observability

### Health Checks

- `/health` - Basic UP/DOWN status
- `/health/ready` - Ready to accept traffic
- `/health/live` - Process is alive
- `/health/startup` - Initialization complete
- `/health/detailed` - Memory, threads, checks

### Metrics

Prometheus metrics available at `/metrics`:

```
yappc_http_requests_total
yappc_http_errors_total
yappc_http_request_duration_seconds
yappc_audit_events_total
yappc_ai_suggestions_total
yappc_db_query_duration_seconds
jvm_memory_used_bytes
jvm_threads_current
```

### Grafana Dashboard

Import the dashboard from `grafana/dashboards/` or use the docker-compose Grafana at `http://localhost:3000`.

## API Documentation

Full OpenAPI 3.0 specification is in `openapi.yaml`.

### Generate Client

```bash
# TypeScript client
npx @openapitools/openapi-generator-cli generate \
  -i openapi.yaml \
  -g typescript-axios \
  -o ../../../frontend/src/api/generated
```

## Deployment

### Docker

```bash
# Build image
docker build -t yappc-api:latest .

# Run container
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host:5432/yappc \
  -e DB_USERNAME=yappc \
  -e DB_PASSWORD=secret \
  yappc-api:latest
```

### Kubernetes

See `infra/kubernetes/yappc-api/` for Helm charts and manifests.

## License

Copyright (c) 2025 Ghatana Technologies
