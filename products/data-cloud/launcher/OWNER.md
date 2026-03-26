# Owner: Data-Cloud Launcher

**Team:** Data-Cloud Runtime Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-03-23  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

The deployable HTTP + gRPC server for Data-Cloud Standalone. Responsible for:

- Route wiring (all `/api/v1/**` REST endpoints)
- Middleware stack: CORS → rate-limit → payload-size → content-type → security-filter → routes
- Handler orchestration: `EntityCrudHandler`, `EventHandler`, `AgentRegistryHandler`,
  `MemoryPlaneHandler`, `BrainHandler`, `LearningHandler`, `AnalyticsHandler`, `AiModelHandler`,
  `AiAssistHandler` (DC-E3), `VoiceGatewayHandler` (DC-E4), `DataLifecycleHandler` (DC-E5),
  `SseStreamingHandler`, `HealthHandler`
- Security filter: `DataCloudSecurityFilter` wrapping `ApiKeyAuthFilter` + `TenantIsolationHttpFilter`
  + `PolicyEngine` enforcement

## Route Ownership Matrix

| Handler | Routes |
|---------|--------|
| `EntityCrudHandler` | `/api/v1/entities/**` |
| `EventHandler` | `/api/v1/events/**` |
| `AgentRegistryHandler` | `/api/v1/agents/**`, `/api/v1/pipelines/**`, `/api/v1/checkpoints/**` |
| `MemoryPlaneHandler` | `/api/v1/memory/**` |
| `BrainHandler` | `/api/v1/brain/**` (non-streaming) |
| `LearningHandler` | `/api/v1/learning/**` (non-streaming) |
| `AnalyticsHandler` | `/api/v1/analytics/**`, `/api/v1/reports/**` |
| `AiModelHandler` | `/api/v1/models/**`, `/api/v1/features/**` |
| `AiAssistHandler` | AI-assist overlays on entity/analytics/pipeline/brain |
| `VoiceGatewayHandler` | `/api/v1/voice/**` |
| `DataLifecycleHandler` | `/api/v1/governance/**` |
| `SseStreamingHandler` | `/events/stream`, `*/stream`, `/ws` |
| `HealthHandler` | `/health`, `/ready`, `/live`, `/info`, `/metrics` |

## Dependencies

- `products:data-cloud:platform-launcher` — runtime/domain services
- `platform:java:{http-server,governance,audit,ai-integration,observability}`
- `libs:agent-framework`

## OpenAPI Contract

Single source of truth: `products/data-cloud/docs/openapi.yaml`  
Drift check: `products/data-cloud/scripts/check-openapi-drift.sh` (CI enforced)
