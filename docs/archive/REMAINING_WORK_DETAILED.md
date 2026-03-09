# Remaining Work Backlog (YAPPC + AEP + Data Cloud + Shared)

Last updated: 2026-02-27

## Scope and status
- This backlog covers **remaining production-path work** for UI/backend/integration stability and “integration-complete” readiness.
- Already addressed in recent passes (not repeated below):
  - Gradle config-time resolution and BOM/versioning blockers.
  - YAPPC workspace/auth runtime placeholder removal in backend API.
  - AEP deployment HTTP path wired to real orchestrator calls.
  - AEP launcher pipeline endpoints moved from ad-hoc map to repository+validator flow.
  - AEP launcher wired to Data Cloud `EventLogStore` provider path.

## Priority 0 (must complete before integration-complete claim)

### P0-1: Implement real AEP gRPC agent service (currently placeholder)
- Area: AEP backend integration
- File: `products/aep/platform/src/main/java/com/ghatana/orchestrator/grpc/AgentGrpcService.java`
- Current gap:
  - Class is compile-only placeholder with no runtime behavior.
- Required work:
  - Implement actual gRPC service handlers for agent lifecycle/management.
  - Wire service into launcher/runtime bootstrap and health checks.
  - Add integration tests covering success, not-found, and error paths.
- Acceptance:
  - gRPC endpoints callable in local runtime; tests verify real responses and status codes.

### P0-2: Replace AEP event-log conversion TODOs in core store adapter
- Area: AEP/Data Cloud event integration
- File: `products/aep/platform/src/main/java/com/ghatana/eventlog/EventLogStore.java`
- Current gap:
  - Conversion methods return `null` with TODO markers.
- Required work:
  - Implement bidirectional event conversion and schema-safe mapping.
  - Add validation for malformed/missing fields.
  - Add tests for conversion round-trip and invalid input behavior.
- Acceptance:
  - No `null` conversion paths; round-trip conversion test passes.

### P0-3: Remove simplified emitter path in orchestration event publishing
- Area: AEP orchestration observability
- File: `products/aep/platform/src/main/java/com/ghatana/orchestrator/executor/AgentEventEmitter.java`
- Current gap:
  - “Temporarily simplified” TODO indicates non-final event path.
- Required work:
  - Replace temporary payload shaping with canonical event contract.
  - Ensure emitted events are persisted/queryable through event cloud pipeline.
  - Add integration test from agent step execution to persisted event verification.
- Acceptance:
  - End-to-end step execution emits canonical event record observed in EventCloud.

## Priority 1 (high-impact production correctness)

### P1-1: Implement tenant permission enforcement in pattern compiler validation
- Area: AEP security/validation
- File: `products/aep/platform/src/main/java/com/ghatana/pattern/compiler/ValidationEngine.java`
- Current gap:
  - Tenant permission validation is TODO; available event types are placeholder list.
- Required work:
  - Integrate permission checks (tenant + actor + operation).
  - Resolve event types via real event schema registry source.
  - Add tests for allow/deny outcomes.
- Acceptance:
  - Unauthorized pattern actions are rejected with deterministic validation errors.

### P1-2: Complete connector runtime behavior for queue/http pipeline connectors
- Area: AEP pipeline runtime integration
- Files:
  - `products/aep/platform/src/main/java/com/ghatana/pipeline/registry/connector/QueueSinkConnector.java`
  - `products/aep/platform/src/main/java/com/ghatana/pipeline/registry/connector/HttpIngressConnector.java`
- Current gap:
  - Multiple TODOs for publish, health checks, start/stop lifecycle, and event routing.
- Required work:
  - Implement adapter-backed queue publish and connection validation.
  - Implement HTTP listener lifecycle (start/pause/stop) and event handoff.
  - Add failure handling, retries/metrics hooks, and health probe coverage.
- Acceptance:
  - Connectors can process live traffic in local integration tests.

### P1-3: Replace Data Cloud analytics transitional query implementation
- Area: Data Cloud query engine
- File: `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/analytics/AnalyticsQueryEngine.java`
- Current gap:
  - Query/aggregation/federation paths are transitional TODOs.
- Required work:
  - Execute query plans against `StorageConnector` implementations.
  - Implement aggregation and multi-source join path.
  - Add correctness and performance tests on realistic datasets.
- Acceptance:
  - Analytics endpoints return connector-backed results (no transitional fallback logic).

### P1-4: Replace placeholder semantic ranking in context gateway
- Area: Data Cloud AI retrieval quality
- File: `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/client/DefaultContextGateway.java`
- Current gap:
  - Placeholder keyword scoring is used instead of semantic similarity.
- Required work:
  - Integrate embedding/vector similarity search.
  - Add deterministic fallback behavior when embeddings unavailable.
  - Validate ranking quality with fixture-based tests.
- Acceptance:
  - Retrieval order driven by semantic score, not keyword-only placeholder logic.

### P1-5: Implement graph storage adapter persistence paths
- Area: Data Cloud knowledge graph plugin
- File: `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/knowledgegraph/storage/DataCloudGraphStorageAdapter.java`
- Current gap:
  - CRUD and query paths are TODO stubs.
- Required work:
  - Implement create/read/update/delete/query against Data Cloud entity storage.
  - Add schema/index initialization and migration handling.
  - Add integration tests for graph CRUD/query operations.
- Acceptance:
  - Graph plugin operates on persisted Data Cloud data (not TODO placeholders).

## Priority 2 (stability hardening and auth/runtime correctness)

### P2-1: Secure Data Cloud audit identity source
- Area: Data Cloud security/audit
- File: `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/infrastructure/audit/DataCloudAuditLogger.java`
- Current gap:
  - Actor defaults to `SYSTEM` placeholder.
- Required work:
  - Pull actor from request/security context.
  - Ensure fallback behavior is explicit and audited.
- Acceptance:
  - Audit records consistently include authenticated actor identity.

### P2-2: Complete storage connector operational TODOs
- Area: Data Cloud storage reliability
- File: `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/infrastructure/storage/PostgresJsonbConnector.java`
- Current gap:
  - Truncate operation and health check TODOs remain.
- Required work:
  - Implement safe truncate semantics with guardrails.
  - Implement real connection pool liveness/readiness checks.
- Acceptance:
  - Admin/storage operations pass integration tests for truncate + health.

### P2-3: Close orchestration checkpoint TODOs
- Area: AEP state/checkpoint reliability
- File: `products/aep/platform/src/main/java/com/ghatana/statestore/checkpoint/CheckpointCoordinatorImpl.java`
- Current gap:
  - Barrier injection and metric update paths still placeholder/demo logic.
- Required work:
  - Implement real stream barrier propagation.
  - Replace mock snapshot behavior with actual snapshot coordination.
- Acceptance:
  - Recovery tests validate checkpoint correctness under failure scenarios.

## Priority 3 (UI/API runtime completion)

### P3-1: Remove mock/stub runtime paths in YAPPC frontend API app
- Area: YAPPC UI/backend integration
- Files (primary):
  - `products/yappc/frontend/apps/api/src/services/RealTimeService.ts`
  - `products/yappc/frontend/apps/api/src/graphql/resolvers/index.ts`
  - `products/yappc/frontend/apps/api/src/jobs/embedding-pipeline.ts`
  - `products/yappc/frontend/apps/api/mock-server.ts`
- Current gap:
  - TODOs for auth validation/persistence/context resolution; stub AI provider imports; mock server usage risk.
- Required work:
  - Enforce token validation + persistence in realtime service.
  - Resolve user/context from auth middleware in resolvers.
  - Replace stub provider in embedding pipeline with real provider wiring.
  - Gate mock servers to non-production only and document startup modes.
- Acceptance:
  - Frontend API runtime uses authenticated real paths end-to-end.

### P3-2: Replace TODO mutation/action placeholders in canvas workspace
- Area: YAPPC UI integration
- File: `products/yappc/frontend/apps/web/src/components/canvas/CanvasWorkspace.tsx`
- Current gap:
  - Multiple TODO actions still indicate non-wired mutation/service calls.
- Required work:
  - Wire artifact/task/comment/blocker/AI actions to actual backend endpoints.
  - Remove TODO fallbacks and verify optimistic update/error handling paths.
- Acceptance:
  - Canvas lifecycle actions persist through API and survive refresh.

## Cross-cutting validation gates (required before “stable/complete”)

1. Integration tests
- Add/expand integration suites for:
  - AEP gRPC + deployment + pipeline CRUD + connector runtime.
  - Data Cloud analytics/context/graph persistence.
  - YAPPC frontend API auth + realtime + canvas mutation flows.

2. End-to-end smoke checks
- Minimal E2E scenarios:
  - create workspace/project -> register pipeline -> deploy -> process events -> surface analytics/suggestions.

3. Production safety checks
- No production-path classes with TODO placeholders that affect control flow, auth, persistence, or transport protocols.

## Suggested execution order
1. P0-1, P0-2, P0-3
2. P1-1, P1-2, P1-3
3. P1-4, P1-5, P2-1
4. P2-2, P2-3
5. P3-1, P3-2
6. Cross-cutting validation gates
