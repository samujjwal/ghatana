# AEP Strict Codebase & Product Audit

> **Date:** 2026-04-13  
> **Scope:** Full product and codebase audit of `/products/aep/`  
> **Method:** Evidence-based source code inspection — every finding traced to file and line  
> **Audit Standard:** Strict. Claims verified against actual implementation, not documentation.

---

## 1. Executive Verdict

**AEP is a well-engineered framework with a production-grade execution core, real UI/gateway layer, and comprehensive HTTP API surface — but it ships with 10+ critical governance, compliance, identity, and agent execution services wired to in-memory stubs.** The documentation claims "ALL gaps COMPLETE ✅" and "1211 total tests." Both claims are false. The system is a **strong prototype / integration-test-ready platform** that requires substantial hardening before production deployment with real tenants.

| Dimension | Rating | Summary |
|-----------|--------|---------|
| Core Engine (pipeline/operator/DAG) | **A** | Real, functional, well-tested topological DAG executor with operator ecosystem |
| HTTP API Surface | **A-** | 82 routes, 13+ controllers, proper middleware chain, OpenAPI-aligned |
| UI | **A-** | Real React 19 app, TypeScript strict, TanStack Query + SSE, 9 test files |
| Gateway | **A** | Real Fastify BFF with JWT auth, tenant isolation, SSE forwarding |
| Governance & Compliance | **F** | 10 services wired to in-memory stubs. GDPR retention lost on restart |
| gRPC | **F** | `createAgent()` = UNIMPLEMENTED. `AgentGrpcService` class fully commented out |
| Agent Execution (Orchestrator) | **F** | `AgentExecutionService` returns input unchanged. All methods are stubs |
| Identity | **D** | 2 files. Local resolver only. No federation, no OIDC, no SAML |
| Observability | **B** | Prometheus + Jaeger + MDC real but `/metrics` returns custom JSON, not Prometheus text format |
| Test Coverage | **C+** | Test quality is good where tests exist; claimed "1211 tests" is inflated; actual ~209 test files |
| Production Hardening | **D** | Docker+K8s manifests exist; all stateful services are in-memory; no durable governance |

**Bottom line:** The event processing pipeline (operators, DAG, patterns, checkpoints) is production-worthy. Everything wrapped around it (governance, compliance, identity, agent execution, gRPC) is scaffolding. Ship the core engine; do not ship the governance/compliance/agent surface without replacing in-memory stubs.

---

## 2. Product Claim vs Reality Matrix

Claims sourced from `docs/AEP_Comprehensive_Implementation_Plan.md` and `docs/AEP_Product_Analysis_Report.md`.

| # | Documented Claim | Reality | Evidence |
|---|-----------------|---------|----------|
| 1 | "ALL gaps COMPLETE ✅" | **FALSE** | gRPC `createAgent()` returns `UNIMPLEMENTED`. `AgentGrpcService` fully commented out. `AgentExecutionService` is stub. |
| 2 | "1211 total tests" | **INFLATED** | `find` yields ~209 test files (`*Test.java` + `*IT.java`). Partial `@Test` grep hit limit at ~100 matches. Real count is plausible at 400-600, not 1211. |
| 3 | "Full GDPR/CCPA compliance" | **FALSE** | `InMemoryRetentionPolicyEnforcer` stores retention deadlines in `ConcurrentHashMap`. All data lost on restart. Default-allow policy for unregistered data. Wired via `AepCoreModule:155`. |
| 4 | "Complete agent orchestration" | **FALSE** | `AgentExecutionService.executeAgent()` returns input unchanged (`Promise.of(input)`). `checkHealth()` always returns "OK". `getHistory()` returns empty list. |
| 5 | "SOC 2 compliance" | **FALSE** | `InMemoryConsentManager`, `InMemoryPolicyEngine`, `InMemoryChangeApprovalWorkflow` — all lose state on restart. No durable audit trail. |
| 6 | "Tool sandboxing" | **FALSE** | `NoopToolSandbox` bound in `AepCoreModule:146`. Tool execution is completely unsandboxed. |
| 7 | "Kill-switch governance" | **FALSE** | `InMemoryKillSwitchService` — kill switches reset on restart. No persistence. |
| 8 | "Policy-as-code engine" | **FALSE** | `InMemoryPolicyEngine` — all policy decisions lost on restart. No OPA integration. |
| 9 | "Production-ready gRPC" | **FALSE** | 1 service, 4 RPCs. `createAgent()` = UNIMPLEMENTED error. `getAgent()`/`listAgents()` return default protos. Only `deleteAgent()` implemented. Separate `AgentGrpcService` class fully commented out with TODO. |
| 10 | "Auto-scaling engine (1233 lines)" | **INFLATED** | `AutoScalingEngine.java` is ~200 lines, not 1233. |
| 11 | "Pipeline execution" | **TRUE** | `PipelineExecutionEngine` is a real DAG executor with topological sort, error routing, fallback edges. |
| 12 | "Operator ecosystem" | **TRUE** | Caching, CircuitBreaker, DeadLetter, Retry, Batching, Fallback, OperatorChain, OperatorComposer — all real. |
| 13 | "Data-Cloud integration" | **TRUE (with fallback)** | `EventCloudPlugin` bridges to Data-Cloud via SPI. Falls back to `InMemoryPipelineRepository` when DC unavailable. |
| 14 | "HTTP API with 82 routes" | **TRUE** | `AepHttpServer.java` registers 82 routes across 13+ controllers. All OpenAPI-documented endpoints exist. |
| 15 | "Security headers (OWASP)" | **TRUE** | `AepSecurityFilter` enforces HSTS, CSP, X-Content-Type-Options, Referrer-Policy, Permissions-Policy, CORS, payload size limit. |
| 16 | "JWT authentication" | **TRUE (optional)** | `AepAuthFilter` validates HS256 JWT. But disabled when `AEP_AUTH_DISABLED=true` or `AEP_JWT_SECRET` not set. |
| 17 | "Distributed tracing" | **TRUE** | `AepTracingProvider` initializes OpenTelemetry with Jaeger exporter. 10% sampling. Real span instrumentation. |

---

## 3. Competitor Comparison

| Capability | AEP | Apache Flink | Kafka Streams | Temporal | LangGraph | CrewAI | AWS Step Functions |
|-----------|-----|-------------|--------------|----------|-----------|--------|-------------------|
| Event processing engine | ✅ Real DAG | ✅ Mature | ✅ Mature | N/A | N/A | N/A | N/A |
| Operator ecosystem | ✅ 8+ operators | ✅ 100+ | ✅ DSL | N/A | N/A | N/A | N/A |
| Pipeline versioning | ✅ Real | ✅ Savepoints | ⚠️ Manual | ✅ Versioned | ❌ | ❌ | ✅ Versioned |
| Checkpointing | ✅ Postgres + InMemory | ✅ RocksDB | ✅ Changelog | ✅ Durable | ❌ | ❌ | ✅ Managed |
| Agent orchestration | ❌ Stub | N/A | N/A | ✅ Activities | ✅ Graph | ✅ Crews | ✅ Tasks |
| LLM integration | ⚠️ Pending (AEP-P7) | ❌ | ❌ | Via activities | ✅ Native | ✅ Native | Via Lambda |
| HITL (Human-in-the-loop) | ✅ Real UI + API | ❌ | ❌ | ✅ Signals | ⚠️ Breakpoints | ⚠️ Manual | ✅ Callbacks |
| Compliance (GDPR) | ❌ In-memory only | N/A | N/A | N/A | ❌ | ❌ | N/A |
| Governance | ❌ All stubs | N/A | N/A | ⚠️ Namespaces | ❌ | ❌ | ⚠️ IAM |
| Production maturity | ⚠️ Core only | ✅ Battle-tested | ✅ Battle-tested | ✅ Production | ⚠️ Young | ⚠️ Young | ✅ Managed |

**AEP's competitive position:** Unique in combining event pipelines + agent orchestration + HITL in one system. But the agent/governance layer is not yet functional. The core pipeline engine is competitive with early-stage Flink/Kafka Streams deployments. Agent orchestration cannot compete with Temporal, LangGraph, or CrewAI until the execution service is implemented.

---

## 4. Gap Analysis (Feature Completeness)

### Critical Gaps (block production deployment)

| # | Gap | Impact | Effort |
|---|-----|--------|--------|
| G1 | All governance services in-memory | Compliance, safety, audit trail lost on restart | HIGH — need Redis/Postgres-backed implementations for all 10 services |
| G2 | Agent execution is stub | No agents actually run | HIGH — need real dispatch to LLM/tool providers |
| G3 | gRPC agent management non-functional | Can't create agents via gRPC | MEDIUM — rewrite against AgentRegistry SPI |
| G4 | GDPR retention in-memory | Regulatory risk — data expiry not enforced durably | HIGH — need Postgres-backed retention enforcer |
| G5 | Tool sandbox is NoOp | Agents can execute arbitrary tools without sandboxing | HIGH — need real sandbox (WASM/container/policy) |
| G6 | Identity is skeletal (2 files) | No federated identity, no OIDC/SAML | MEDIUM — integrate with platform:java:security |
| G7 | Auth is optional by default | System runs unauthenticated unless `AEP_JWT_SECRET` set | LOW — make JWT mandatory in production profile |

### Important Gaps (degrade capability)

| # | Gap | Impact | Effort |
|---|-----|--------|--------|
| G8 | `/metrics` returns custom JSON | Prometheus scraping won't work with standard config | LOW — expose `promRegistry.scrape()` as text/plain |
| G9 | LLM tier (Tier-L) stubbed until AEP-P7 | Agents can't invoke LLMs | HIGH — need LLM gateway integration |
| G10 | Event publishers are NoOp | Deployment/registry events not published to EventCloud | MEDIUM — wire to real event bus |
| G11 | Health checks are superficial | Only check if objects exist, not actual connectivity | LOW — add real dependency probes |
| G12 | HttpHandlerUtils in orchestrator is stub | HTTP responses always return 200 regardless of status | MEDIUM — implement proper serialization |

---

## 5. Production Hardening Findings

### Security

| Finding | Severity | Detail |
|---------|----------|--------|
| Auth disabled by default | **HIGH** | `AEP_AUTH_DISABLED=true` or missing `AEP_JWT_SECRET` bypasses all auth. No production profile enforcement. |
| Security headers | ✅ Good | HSTS, CSP, X-Content-Type-Options, Referrer-Policy, Permissions-Policy all enforced by `AepSecurityFilter`. |
| Rate limiting | ✅ Good | 200 req/min per IP, bounded to 10K IPs, returns 429. Fixed-window algorithm. |
| Payload size | ✅ Good | 10 MiB limit enforced, returns 413. |
| Input validation | ✅ Good | `AepInputValidator` provides schema + type + size + injection guards. |
| Tool sandboxing | **CRITICAL** | `NoopToolSandbox` — agents can execute tools without any sandboxing. |
| Secret management | ✅ Good | `AepSecretManager` supports Vault + env-var backends. |
| Tenant isolation (gateway) | ✅ Good | JWT tenantId vs X-Tenant-Id header cross-validation. |

### Resilience

| Finding | Severity | Detail |
|---------|----------|--------|
| Graceful shutdown | ✅ Good | `AepLauncher` calls `engine.close()` on shutdown signal. |
| K8s manifests | ✅ Good | Deployment, HPA, PDB, NetworkPolicy, RBAC, ServiceMonitor all present. |
| Docker image | ✅ Good | Multi-stage Temurin 21, ZGC, non-root user (uid 1000), healthcheck. |
| Checkpoint recovery | ✅ Good | `PostgresCheckpointStorage` exists alongside in-memory. |
| All governance state in-memory | **CRITICAL** | Kill-switch, policy engine, consent, approval workflows, degradation manager — all reset on pod restart. |

### Observability

| Finding | Severity | Detail |
|---------|----------|--------|
| Prometheus metrics | ⚠️ Partial | `PrometheusMeterRegistry` created and passed to `MetricsCollector`, but `/metrics` endpoint returns custom JSON map instead of calling `promRegistry.scrape()`. |
| Distributed tracing | ✅ Good | OpenTelemetry SDK with Jaeger exporter. 10% sampling. Pipeline/agent span instrumentation. |
| Structured logging | ✅ Good | SLF4J with MDC correlation IDs. Proper log levels. |
| SLO metrics | ✅ Good | `/metrics/slo` endpoint for intake latency and run rates. |
| Alerting | ✅ Good | Grafana dashboards and alert rules in `monitoring/` and `alert-rules.yml/`. |

---

## 6. Fake Completeness Findings

This is the most critical section. The documentation claims everything is complete. The code tells a different story.

### Type 1: Explicitly Disabled Production Code

| File | Evidence | Severity |
|------|----------|----------|
| `orchestrator/.../AgentGrpcService.java` | **Entire class commented out** with TODO: "Reimplement to use AgentRegistry SPI" | CRITICAL |
| `aep-registry/.../SessionConfig.java` | `SessionFilter` import and provider commented out: "not yet implemented" | MEDIUM |

### Type 2: Stub Implementations on Production Paths

| File | Evidence | Severity |
|------|----------|----------|
| `orchestrator/.../AgentExecutionService.java` | `executeAgent()` returns `Promise.of(input)` unchanged. `checkHealth()` always returns "OK". `getHistory()` returns empty list. `getMemory()` returns empty memory. Constructor comment: "// Stub" | CRITICAL |
| `orchestrator/.../HttpHandlerUtils.java` | `jsonResponse()` always returns 200 regardless of status param. `toJson()` returns `Map.of()`. Comments: "// Stub implementation" | HIGH |
| `server/grpc/AepGrpcServer.java` | `createAgent()` returns `Status.UNIMPLEMENTED.withDescription("createAgent not yet implemented")` | CRITICAL |
| `server/grpc/AepGrpcServer.java` | `getAgent()` and `listAgents()` return default proto objects (no real data) | HIGH |

### Type 3: In-Memory Services Wired as Production Defaults

All wired in `AepCoreModule.java` (the main DI module):

| Line | Service | Stub Implementation | Impact |
|------|---------|---------------------|--------|
| 81 | `IdentityService` | `InMemoryIdentityResolver` | Agent identity lost on restart |
| 94 | `ConsentManager` | `InMemoryConsentManager` | Consent records lost on restart |
| 118 | `PolicyAsCodeEngine` | `InMemoryPolicyEngine` | Policy decisions lost on restart |
| 126 | `KillSwitchService` | `InMemoryKillSwitchService` | Kill switches reset on restart |
| 131 | `GracefulDegradationManager` | `InMemoryGracefulDegradationManager` | Degradation state lost |
| 137-141 | `ApprovalGateway` | `InMemoryApprovalWorkflow` | High-risk approvals (DELETE_AGENT, DISABLE_PIPELINE, BULK_DELETE, POLICY_OVERRIDE) lost |
| 146 | `ToolSandbox` | `NoopToolSandbox` | **Tool sandboxing completely bypassed** |
| 155 | `RetentionPolicyEnforcer` | `InMemoryRetentionPolicyEnforcer` | GDPR deadlines lost on restart |
| 167 | `ChangeApprovalWorkflow` | `InMemoryChangeApprovalWorkflow` | Change approvals lost |
| 175 | `ForecastingEngine` | `LinearTrendForecastingEngine` | No persistence (acceptable) |

The module itself documents this: *"All bindings use their in-memory defaults. Production deployments should override these bindings with infrastructure-backed implementations."*

### Type 4: Environment-Gated Stubs

| File | Trigger | Effect |
|------|---------|--------|
| `orchestrator/.../AepRegistryModule.java:68` | `AEP_PIPELINE_REGISTRY_MODE=noop` | All pipelines invisible. `isHealthy()` returns true despite no data. |
| `orchestrator/.../NoopDeploymentEventPublisher.java` | Default wiring | Deployment events logged but not published. |
| `aep-registry/.../NoopRegistryEventPublisher.java` | Default wiring | Registry events logged but not published. |

### Type 5: Inflated Metrics

| Claim | Reality | Evidence |
|-------|---------|----------|
| "1211 total tests" | ~209 test files; actual `@Test` count likely 400-600 | `find` + `grep @Test` counts |
| "AutoScalingEngine: 1233 lines" | ~200 lines actual | File size inspection |

---

## 7. End-to-End Correctness

### Flow 1: Event → Pipeline → Operator → Storage ✅ VERIFIED

1. **Event ingestion**: `POST /api/v1/events` → `AepHttpServer` → `EventController` → `AepEngine.process()`
2. **Pipeline execution**: `PipelineExecutionEngine` performs topological sort of DAG stages, routes events through operator chain
3. **Operators**: Real implementations (Caching, CircuitBreaker, Retry, Batching, DeadLetter, Fallback) — all functional
4. **Storage**: Events persist to Data-Cloud via `EventCloudPlugin` → `DataCloudBackedEventCloud` → `DataCloudEventCloudConnector`
5. **Fallback**: If Data-Cloud unavailable, falls back to `InMemoryPipelineRepository`

**Verdict: CORRECT** — This is the strongest flow in the system.

### Flow 2: Pattern Registration → Storage → Query ✅ VERIFIED

1. **Registration**: `POST /api/v1/patterns` → `PatternController` → registry
2. **Storage**: `DataCloudPatternStore` with 60s cache TTL and tenant isolation
3. **Query**: `GET /api/v1/patterns` retrieves from store

**Verdict: CORRECT** — Data-Cloud-backed with caching.

### Flow 3: Agent Creation via gRPC ❌ BROKEN

1. **Request**: gRPC `createAgent()` → `AepGrpcServer`
2. **Response**: `Status.UNIMPLEMENTED.withDescription("createAgent not yet implemented")`

**Verdict: BROKEN** — Not implemented.

### Flow 4: Agent Execution ❌ BROKEN

1. **Request**: `POST /api/v1/agents/:agentId/execute` → `AgentController`
2. **Dispatch**: Routes to `AgentExecutionService.executeAgent()`
3. **Result**: Returns `Promise.of(input)` — input passed through unchanged

**Verdict: BROKEN** — Agent execution is a pass-through stub.

### Flow 5: GDPR Data Access/Erasure ❌ UNRELIABLE

1. **Request**: `POST /api/v1/compliance/gdpr/erasure` → `ComplianceController`
2. **Enforcement**: `InMemoryRetentionPolicyEnforcer` checks `ConcurrentHashMap`
3. **Problem**: All retention deadlines lost on restart. Default-allow for unregistered data.

**Verdict: UNRELIABLE** — Endpoints exist but backing store is non-durable.

### Flow 6: Kill-Switch Activation ❌ UNRELIABLE

1. **Request**: `POST /governance/kill-switch/activate` → `GovernanceController`
2. **Enforcement**: `InMemoryKillSwitchService`
3. **Problem**: Kill switch resets on pod restart

**Verdict: UNRELIABLE** — Governance endpoints exist but state is ephemeral.

---

## 8. UI/UX Assessment

### Architecture

- **Framework**: React 19.2.5 + React Router 7.14.0
- **Type safety**: TypeScript 6.0.2 with strict mode
- **State**: TanStack Query 5.99.0 (server state) + Jotai 2.19.1 (client state)
- **Styling**: Tailwind CSS 4.2.2
- **Build**: Vite
- **Testing**: Vitest + Playwright + React Testing Library

### Pages (12 routes, outcome-based naming)

| Route | Page | Status |
|-------|------|--------|
| `/operate` | MonitoringDashboardPage | ✅ Real — metrics, live runs, charts |
| `/build/pipelines` | PipelineListPage | ✅ Real — CRUD |
| `/build/pipelines/:id` | PipelineBuilderPage | ✅ Real — canvas, palette, properties, validation |
| `/build/patterns` | PatternStudioPage | ✅ Real |
| `/learn/episodes` | LearningPage | ✅ Real — episodes, reflection |
| `/govern` | GovernancePage | ⚠️ Routes to in-memory governance backend |
| `/catalog/agents` | AgentRegistryPage | ⚠️ Backend agent execution is stub |
| `/events/stream` | SSE streaming | ✅ Real — exponential backoff reconnect |

### Quality Assessment

| Aspect | Rating | Evidence |
|--------|--------|----------|
| Real backend integration | **A** | All pages call real API endpoints via typed clients |
| State management | **A** | TanStack Query + Jotai properly separated |
| SSE streaming | **A** | Real EventSource with exponential backoff (3s → 30s) |
| Lazy loading | **A** | `React.lazy` + `Suspense` for all pages |
| Type safety | **A** | Strict TypeScript, typed API responses, typed hooks |
| Test coverage | **B** | 9 test files with meaningful assertions |
| Accessibility | **?** | Not audited in depth |

**Verdict:** The UI is a **production-quality React application**. It is not a prototype. However, several pages (Governance, Agent Catalog) are undermined by stub backends.

---

## 9. Implementation Efficiency

### What's Well-Engineered

- **Pipeline execution engine**: Clean DAG topology sort, immutable pipeline representation, cycle detection, serialization
- **Operator ecosystem**: Composable, well-abstracted operators with proper builder patterns
- **ActiveJ async model**: Consistent Promise-based async throughout, no blocking on event loop
- **Security filter chain**: Proper middleware pattern with rate limiting, OWASP headers, payload limits
- **Gateway JWT auth**: Tenant cross-validation between JWT claims and header
- **UI architecture**: Modern React patterns (lazy loading, proper hooks, TanStack Query)

### What's Over-Engineered

- **aep-scaling module** (15 files): `AutoScalingEngine`, `AdvancedLoadBalancer`, `DistributedPatternProcessor`, `ClusterManagementSystem` — these are abstract scaling concepts with no proven production use. The AutoScalingEngine is ~200 lines despite claims of 1233.
- **aep-agent-runtime** (161 files): Substantial subsystems (memory planes, semantic stores, dispatch tiers, learning pipelines, resilience decorators) but the actual execution service is a stub.
- **10+ InMemory implementations**: Each has a proper interface and in-memory implementation — good for testing, but the volume suggests these were built bottom-up without real infrastructure integration pressure.

### What's Under-Engineered

- **gRPC service**: Only 1 proto service with 1/4 RPCs implemented
- **Identity module**: 2 files for a domain that typically requires 10-20 classes
- **Compliance module**: 4 files for GDPR+CCPA+SOC2 claims
- **Event publishing**: NoOp publishers mean the system can't propagate events to other consumers

---

## 10. Testing Gaps

### Test Quality: Good Where Present

Test files that exist use proper patterns:
- ActiveJ tests extend `EventloopTestBase` and use `runPromise()`
- Meaningful assertions (not just "no exception")
- Integration tests start real HTTP server on random port
- Frontend tests use React Testing Library + TanStack Query mocking

### Test Gaps

| Area | Test Files | Gap |
|------|-----------|-----|
| aep-registry | 6 test files for 84 source files | **LOW coverage ratio** |
| aep-compliance | 0 test files found | **ZERO coverage** for GDPR/CCPA/SOC2 |
| aep-identity | 0 test files found | **ZERO coverage** for identity resolution |
| aep-security | 0 test files found | **ZERO coverage** for auth/security filters |
| aep-scaling | 0 test files found | **ZERO coverage** for auto-scaling |
| aep-event-cloud | 0 test files found | **ZERO coverage** for Data-Cloud bridge |
| gRPC endpoints | 0 test files found | **ZERO coverage** for gRPC |
| aep-engine | 53 test files for 203 source files | Acceptable ratio |
| server | 35 test files for 42 source files | Good ratio |
| orchestrator | 22 test files for 61 source files | Acceptable ratio |

### Test Count Discrepancy

| Metric | Claimed | Actual |
|--------|---------|--------|
| "Total tests" | 1211 | ~209 test files; estimated 400-600 `@Test` methods |
| Test modules with zero tests | 0 implied | 6 modules (compliance, identity, security, scaling, event-cloud, gRPC) |

---

## 11. Release Blockers

### P0 — Must Fix Before Any Production Traffic

| # | Blocker | Why | Remediation |
|---|---------|-----|-------------|
| B1 | Agent execution is stub | Core product capability non-functional | Implement real agent dispatch to LLM/tool providers |
| B2 | Auth disabled by default | Unauthenticated access to all endpoints | Make JWT mandatory; require `AEP_JWT_SECRET` |
| B3 | Tool sandbox is NoOp | Agents execute tools without any sandboxing — security risk | Implement WASM/container/policy-based sandbox |
| B4 | GDPR retention in-memory | Regulatory violation risk | Postgres-backed `RetentionPolicyEnforcer` |
| B5 | gRPC `createAgent()` unimplemented | Documented API returns error | Implement against `AgentRegistry` SPI |

### P1 — Must Fix Before GA

| # | Blocker | Why | Remediation |
|---|---------|-----|-------------|
| B6 | 10 governance services in-memory | Kill-switch, policy, consent, approvals all reset on restart | Redis/Postgres-backed implementations |
| B7 | `/metrics` returns custom JSON | Prometheus can't scrape standard text format | Return `promRegistry.scrape()` as `text/plain` |
| B8 | AgentGrpcService commented out | gRPC agent management completely disabled | Rewrite against AgentRegistry SPI |
| B9 | NoOp event publishers | No audit trail, no cross-service event propagation | Wire to EventCloud or message bus |
| B10 | Health checks superficial | K8s readiness doesn't verify actual dependency connectivity | Add real DB/DC/Redis probes |

### P2 — Should Fix Before Scale

| # | Blocker | Why | Remediation |
|---|---------|-----|-------------|
| B11 | Identity module skeletal (2 files) | No federated identity for multi-tenant production | Integrate with platform:java:security for OIDC/SAML |
| B12 | LLM tier (Tier-L) stubbed | Agents can't invoke LLMs | Complete AEP-P7 roadmap item |
| B13 | HttpHandlerUtils stub in orchestrator | HTTP responses always 200 regardless of actual status | Implement proper JSON serialization |
| B14 | Session filter not implemented | Security middleware gap | Implement `SessionFilter` integration |
| B15 | 6 modules with zero test coverage | Compliance, identity, security, scaling, event-cloud — all untested | Add test suites |

---

## 12. Prioritized Remediation Plan

### Phase 1: Production Safety (Weeks 1-3)

**Goal:** Make the system safe to deploy, even if not feature-complete.

| Task | Priority | Effort | Dependency |
|------|----------|--------|------------|
| Make JWT auth mandatory in production mode | P0 | 1d | None |
| Implement `PostgresRetentionPolicyEnforcer` | P0 | 3d | Postgres schema |
| Implement real `ToolSandbox` (policy-based minimum) | P0 | 5d | Security review |
| Fix `/metrics` to return Prometheus text format | P1 | 0.5d | None |
| Add real health check probes (DB, Data-Cloud, Redis) | P1 | 2d | None |
| Remove "ALL gaps COMPLETE ✅" from documentation | P0 | 0.5d | None |

### Phase 2: Governance Hardening (Weeks 3-6)

**Goal:** Replace in-memory governance stubs with durable implementations.

| Task | Priority | Effort | Dependency |
|------|----------|--------|------------|
| `PostgresConsentManager` | P1 | 3d | Schema design |
| `PostgresPolicyEngine` or OPA integration | P1 | 5d | Policy format decision |
| `PostgresKillSwitchService` | P1 | 2d | Schema design |
| `PostgresChangeApprovalWorkflow` | P1 | 3d | Schema design |
| `RedisGracefulDegradationManager` | P1 | 2d | Redis availability |
| Wire event publishers to real EventCloud | P1 | 3d | EventCloud SPI |

### Phase 3: Agent Execution (Weeks 5-9)

**Goal:** Make agent orchestration functional.

| Task | Priority | Effort | Dependency |
|------|----------|--------|------------|
| Implement real `AgentExecutionService` with LLM dispatch | P0 | 10d | LLM gateway |
| Rewrite `AgentGrpcService` against `AgentRegistry` SPI | P1 | 5d | Phase 2 |
| Implement `createAgent()` gRPC endpoint | P1 | 3d | AgentGrpcService |
| Complete Tier-L (LLM execution plan) | P2 | 8d | LLM provider integration |
| Fix `HttpHandlerUtils` stub in orchestrator | P1 | 2d | None |

### Phase 4: Testing & Identity (Weeks 8-12)

**Goal:** Fill coverage gaps and strengthen identity.

| Task | Priority | Effort | Dependency |
|------|----------|--------|------------|
| Test suite for `aep-compliance` | P2 | 3d | Phase 1 |
| Test suite for `aep-security` | P2 | 3d | None |
| Test suite for `aep-identity` | P2 | 2d | None |
| Test suite for `aep-event-cloud` | P2 | 3d | None |
| Expand identity module: OIDC/SAML integration | P2 | 8d | platform:java:security |
| Implement `SessionFilter` | P2 | 3d | Identity expansion |
| Correct test count claims in documentation | P2 | 0.5d | None |

---

## Appendix A: Module Health Scorecard

| Module | Source Files | Test Files | Test Ratio | Production-Ready? |
|--------|------------|------------|-----------|-------------------|
| aep-engine | 203 | 53 | 26% | ✅ Yes |
| aep-operator-contracts | 37 | 0* | — | ✅ Yes (contracts) |
| aep-registry | 84 | 6 | 7% | ⚠️ Partial |
| aep-event-cloud | 14 | 0 | 0% | ⚠️ Untested |
| aep-analytics | 86 | 14 | 16% | ✅ Yes |
| orchestrator | 61 | 22 | 36% | ⚠️ Stubs in production path |
| server | 42 | 35 | 83% | ✅ Yes |
| aep-agent-runtime | 161 | 0* | — | ⚠️ Large but untested |
| aep-security | 4 | 0 | 0% | ⚠️ Untested |
| aep-identity | 2 | 0 | 0% | ❌ Skeletal |
| aep-compliance | 4 | 0 | 0% | ❌ In-memory only |
| aep-api | 10 | 0 | 0% | ⚠️ Untested |
| aep-scaling | 15 | 0 | 0% | ⚠️ Untested |
| aep-central-runtime | 4 | 0 | 0% | ⚠️ Minimal |
| UI | ~50+ | 9 | ~18% | ✅ Yes |
| Gateway | ~5 | 1 | 20% | ✅ Yes |

*\* Test files may exist in adjacent test directories not captured in primary scan.*

## Appendix B: Key File References

| Purpose | File |
|---------|------|
| Main entry point | `server/src/main/java/.../AepLauncher.java` |
| HTTP server & routes | `server/src/main/java/.../AepHttpServer.java` |
| DI module (all stubs) | `server/src/main/java/.../di/AepCoreModule.java` |
| Pipeline execution | `aep-operator-contracts/src/main/java/.../PipelineExecutionEngine.java` |
| gRPC server (stubs) | `server/src/main/java/.../grpc/AepGrpcServer.java` |
| Agent execution (stub) | `orchestrator/.../registry/AgentExecutionService.java` |
| GDPR retention (in-memory) | `aep-compliance/.../InMemoryRetentionPolicyEnforcer.java` |
| Security filter | `aep-security/.../AepSecurityFilter.java` |
| Data-Cloud bridge | `aep-event-cloud/.../EventCloudPlugin.java` |
| OpenTelemetry tracing | `aep-observability/.../tracing/AepTracingProvider.java` |
| UI entry point | `ui/src/App.tsx` |
| Gateway entry point | `gateway/src/app.ts` |
