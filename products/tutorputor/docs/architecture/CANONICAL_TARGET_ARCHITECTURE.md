# TutorPutor Canonical Target Architecture

TutorPutor uses a deliberately composed hybrid runtime with one product authority:

1. `services/tutorputor-platform` is the primary backend. It owns public product API behavior, persistence, tenant scoping, compliance, telemetry ingest, analytics, assessment orchestration, LTI, credentials, and policy enforcement.
2. Java/ActiveJ agent libraries and content-generation services are worker runtimes. They may generate content, run agent workflows, or expose gRPC worker endpoints, but they do not own public product routes or product persistence.
3. `apps/api-gateway` is a thin edge gateway for routing, cross-cutting HTTP concerns, and compatibility. It must delegate to platform-owned contracts and must not fork product behavior.
4. Shared contracts in `contracts/v1` and `api/tutorputor-api.openapi.yaml` are the public contract surface. Route owners are declared in `docs/architecture/API_ROUTE_OWNERS.json`.
5. Frontend clients must consume typed/generated contract clients. Hand-maintained route shapes are allowed only as migration shims when listed in `API_ROUTE_OWNERS.json` and covered by tests.

Any new public API route must add:

- one OpenAPI path
- one canonical backend owner
- one typed/generated client owner
- one contract file owner
- one test owner

The conformance gate is `scripts/validate-product-traceability.mjs`.
