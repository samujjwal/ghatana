# Owner: AEP Server

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

HTTP server entry point for AEP. Provides REST API, gRPC services, and administrative endpoints.

- HTTP REST API for pipeline management
- gRPC service endpoints
- OpenAPI specification serving
- Health and observability endpoints (`/health`, `/ready`, `/metrics`)
- Disaster recovery endpoints
- Learning pipeline HTTP interface

## Key Components

| Component | Purpose |
|-----------|---------|
| `AepHttpServer` | Main HTTP server |
| `AepGrpcServer` | gRPC service handler |
| `HealthController` | Health and readiness probes |
| `AepOpenApiSurfaceDriftTest` | OpenAPI contract parity validation |
| `EpisodeLearningPipeline` | Learning loop HTTP interface |

## Critical Tests

| Test | Purpose | CI Status |
|------|---------|-----------|
| `AepOpenApiSurfaceDriftTest` | OpenAPI spec parity enforcement | **PINNED** (AEP-A2, AEP-A8) |
| `AepHttpServerObservabilityTest` | Observability endpoint validation | PASS |
| `AepDisasterRecoveryServiceTest` | DR service validation | PASS |

## API Contracts

- OpenAPI spec: `server/src/main/resources/openapi.yaml`
- Contract validation: `:products:aep:contracts:validateAepSpec`

## Dependencies

- `platform:java:http`
- `platform:java:observability`
- `products:aep:orchestrator`
- `products:aep:aep-engine`

## Audit Status

- Last audited: 2026-04-29
- AepOpenApiSurfaceDriftTest: PASS, pinned in CI
- Source files: 133 | Test files: 63
