# Studio Workflow Service

**Owner:** Platform Team  
**Scope:** Shared service for Studio artifact workflow persistence  
**Status:** Production-ready

## Purpose

Provides durable storage for artifact workflow state and evidence packs across the Studio import/decompile/edit/preview/fidelity pipeline.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| PUT | `/api/v1/studio/workflow-state` | Persist workflow state |
| GET | `/api/v1/studio/workflow-state` | Load workflow state |
| DELETE | `/api/v1/studio/workflow-state` | Clear workflow state |
| PUT | `/api/v1/studio/workflow-evidence` | Persist evidence pack |
| GET | `/api/v1/studio/workflow-evidence` | Load evidence pack |
| GET | `/health` | Health probe |
| GET | `/metrics` | Service metrics |

## Security Model

All endpoints require:
- `Authorization: Bearer <token>`
- `X-Tenant-ID`: Tenant isolation
- `X-Workspace-ID`: Workspace scoping
- `X-Project-ID`: Project scoping

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `STUDIO_WORKFLOW_PORT` | 8085 | HTTP server port |

## Architecture

- Java 21 + ActiveJ HTTP server
- In-memory storage with tenant/workspace/project isolation
- JSON-based state serialization
- Promise-based async request handling

## Testing

```bash
./gradlew :shared-services:studio-workflow-service:test
```
