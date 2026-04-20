# AEP — Agentic Event Processor: Deep End-to-End Product Audit

**Audit Date:** April 19, 2026  
**Auditor:** Principal Product Architect · Staff Engineer Review (GitHub Copilot)  
**Scope:** Full product reality audit — AEP + relevant platform UI/backend libraries  
**Evidence Base:** Direct code exploration of 50,000+ lines, 18 HTTP controllers, 14+ UI pages, OpenAPI contracts, test suites, docs, prior audit findings (AUDIT_REPORT_2026-04-18.md + REMEDIATION_TRACKER.md), platform/typescript and platform/java library surfaces  
**Prior Audit Baseline:** AUDIT_REPORT_2026-04-18.md (Score: 6.5/10 overall)

---

## A. Executive Verdict

### Overall Product Maturity: **7.2/10** — Significantly Improved Since April 18 Audit

| Dimension | Score (Apr 18) | Score Today | Delta | Assessment |
|-----------|---------------|-------------|-------|------------|
| **End-to-End Working Reality** | 5/10 | 7/10 | +2 | Event processing fully implemented; EventController stub removed; batch processing functional |
| **AI/ML-First Maturity** | 6/10 | 7/10 | +1 | LLM fact extraction (✅), AI suggestions (✅), ML operator generation (✅); still missing in core pipeline UX |
| **Automation Maturity** | 5/10 | 7/10 | +2 | Pipeline DAG execution, stage suggestion, NL pipeline creation all completed per remediation tracker |
| **UX Simplicity** | 7/10 | 7/10 | 0 | 14–15 clean pages but design system adoption is only ~3% (2 of ~66 raw elements migrated) |
| **Cognitive Load** | 6/10 | 6/10 | 0 | Manual configuration burden persists; limited contextual help; no inline AI guidance |
| **Governance/Privacy/Security/Visibility** | 8/10 | 8.5/10 | +0.5 | Strong kill-switch, SOC2, GDPR, CCPA, audit trails; consent service wiring still shallow |
| **Production Readiness** | 5/10 | 7/10 | +2 | Real event processing + pattern detection; Prometheus metrics; Redis/DB health; still in-memory runs buffer by default |
| **Operability/Resilience** | 6/10 | 7/10 | +1 | Deep health checks, graceful degradation, SLO metrics; no disaster-recovery testing evidence |

### Top Remaining Blockers

1. **Design system adoption is a stub (3%)** — 64+ raw `<button>`, `<input>`, `<select>` elements instead of `@ghatana/design-system` components; inconsistent component behavior across pages.
2. **In-memory runs buffer is not production-safe** — `recentRuns` Deque capped at 1,000 entries and lost on restart; no durable run history without Data Cloud.
3. **Test coverage proves reachability not outcome correctness** — `AepGoldenPathSystemTest` now has better assertions, but most controller tests are still shallow.
4. **No production deployment configuration evidence** — Helm charts and k8s configs exist but no CI/CD pipeline definition; no rollback validation test.
5. **AI suggestions measure nothing** — `AiSuggestionsController` surfaces anomaly-ranked suggestions but no click-through metrics, no A/B framework, no evaluation of suggestion quality.
6. **Consent service integration is shallow** — `DefaultConsentService` exists but is not wired into event processing; events can be processed without consent validation.

---

## B. Reconstructed Product Model

### Target Personas

| Persona | Primary Journeys | Cognitive Level |
|---------|-----------------|-----------------|
| **Platform/DevOps Engineer** | Deploy AEP, monitor health, activate kill-switch, manage deployments | High — sees all 15 pages |
| **Pipeline Author** | Visually build pipelines, validate, publish, monitor runs | Medium — uses Build + Operate areas |
| **Agent Developer** | Register agents, invoke execution, inspect memory | Medium — uses Catalog + Learn areas |
| **Compliance/Security Officer** | SOC2 reports, GDPR erasure, audit trail review | Low — uses Govern area only |
| **Data Scientist** | Design patterns, review anomalies, inspect learning episodes | High — uses Build + Learn areas |
| **HITL Reviewer** | Review pending decisions, approve/reject with rationale | Low — focused on Operate/Reviews |

### Jobs to Be Done

1. **Submit events and trust that they are processed correctly** — primary value delivery
2. **Build pipelines visually without deep technical knowledge** — low-friction authoring
3. **Detect anomalous patterns automatically** — AI/ML-pervasive surveillance
4. **Review AI decisions that need human judgment** — governed escalation
5. **Learn from outcomes and improve automatically** — self-improving runtime
6. **Demonstrate compliance and audit evidence** — governance and reporting
7. **Operate safely in production with quick recovery** — reliability

### Expected AI/ML Role (Ideal State)

- **Implicit event intent classification** → auto-route events without user configuration
- **Smart pipeline defaults** → 80%+ parameters prefilled, not entered
- **Natural language pipeline creation** → "detect fraud patterns on payment events"
- **LLM-backed fact extraction from episodes** → ✅ implemented
- **Anomaly-driven AI suggestions** → ✅ partially implemented
- **Predictive governance** → not yet implemented

### Justified Human Review Points

- Kill-switch activation (✅ always requires human)
- HITL low-confidence decisions (✅ implemented)
- Policy promotion from learning (✅ approval required)
- GDPR erasure confirmation (✅ implemented)
- New agent registration security scan (⚠️ stated in design, not enforced)

---

## C. End-to-End Workflow Audit Matrix

### Workflow 1: Event Ingestion and Processing

| Aspect | Assessment |
|--------|-----------|
| **Intended Outcome** | Event submitted → processed → patterns detected → results returned with evidence |
| **Current Actual Behavior** | ✅ **FIXED since Apr 18** — `AepHttpServer.handleProcessEvent()` fully implemented: parses payload, validates tenant/type, calls `engine.process()`, records run, increments SLO metrics, returns `{eventId, success, detections, timestamp}` |
| **Evidence** | [server/src/main/java/.../AepHttpServer.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java#L769) |
| **AI/ML Role Today** | Pattern detection via `PatternDetectionAgentAdapter`; heuristic + ML operator generation |
| **AI/ML Missing** | Event intent classification; smart routing by payload semantics; auto-schema inference |
| **API Assessment** | ✅ POST /api/v1/events and /api/v1/events/batch both functional with validation |
| **Backend Assessment** | ✅ `AepEngine.process()` delegates to registered `PatternDetector` chain; `EventCloud` stores events |
| **DB Assessment** | ⚠️ `recentRuns` is in-memory Deque (1,000 cap, lost on restart); durable via `EventCloudRunLedger` only when Data Cloud configured |
| **Governance/Security** | ✅ `AepInputValidator` validates tenantId, eventType, payload; rate limiting via `AepRateLimiter`; audit logging present |
| **Consent** | ❌ `DefaultConsentService` is not wired into `handleProcessEvent`; events processed regardless of consent |
| **Test Evidence** | ✅ `EndToEndEventProcessingTest` tests real pattern detection; `AepGoldenPathSystemTest` verifies event-to-run-list flow |
| **Key Gaps** | No consent gate; no event schema validation against registered schemas; in-memory run history |
| **Severity** | P1 (consent), P2 (schema validation, durable history) |

### Workflow 2: Visual Pipeline Authoring → Execution

| Aspect | Assessment |
|--------|-----------|
| **Intended Outcome** | Drag stages → auto-suggest → validate → publish → pipeline executes with DAG ordering |
| **Current Actual Behavior** | ✅ UI: Full drag-and-drop builder with undo/redo, property panels, validation feedback. ✅ Backend: CRUD, DAG validation, topological sort, publish/rollback versioning. ⚠️ Execution: DAG execution implemented per remediation tracker but integration test depth is unclear |
| **Evidence** | [ui/src/pages/PipelineBuilderPage.tsx](products/aep/ui/src/pages/PipelineBuilderPage.tsx) · [controllers/PipelineController.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/PipelineController.java) |
| **AI/ML Role Today** | `StageSuggestionService` with rule-based heuristics and event type categorization (remediation tracker P1-9) |
| **AI/ML Missing** | LLM-driven stage recommendation; configuration prefill depth not validated |
| **UI Assessment** | ✅ Excellent: @xyflow/react canvas, Monaco editor for spec, Jotai atoms, full error boundary; ⚠️ Raw HTML form elements not migrated to design system |
| **API Assessment** | ✅ Full CRUD + versioning + publish/rollback. Pipeline validation endpoint exists |
| **Async/SSE** | ✅ `useLivePipelineRuns` uses SSE for real-time run updates |
| **DB Assessment** | ✅ Durable via `DataCloudPipelineStore` when DC configured; falls back to `InMemoryPipelineRepository` |
| **Test Evidence** | `AepHttpServerPipelineVersioningTest` exists; `PipelineBuilderPage.test.tsx` UI tests present |
| **Key Gaps** | No natural-language pipeline creation E2E test; AI suggestion effectiveness unmeasured |
| **Severity** | P2 |

### Workflow 3: Agent Registration and Execution

| Aspect | Assessment |
|--------|-----------|
| **Intended Outcome** | Agent registered → invoked → executes → returns result with memory update |
| **Current Actual Behavior** | ✅ List/get/deregister via `EventCloudAgentStore`; execution via `POST /api/v1/agents/:id/execute` dispatches to `engine.process()`; memory via `dc_memory` collection |
| **Evidence** | [controllers/AgentController.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/AgentController.java#L39) |
| **Graceful Degradation** | ✅ Returns empty list with `"configured": false` message when Data Cloud absent |
| **Memory Model** | ✅ episodic, semantic, procedural, preference memory types; separate endpoints per type |
| **AI/ML Role** | Agent execution outcomes feed learning pipeline for LLM fact extraction |
| **Test Evidence** | `AepHttpServerAgentTest.java`; marketplace tests present |
| **Key Gaps** | Execution outcome not end-to-end verified; no automatic agent capability discovery |
| **Severity** | P2 |

### Workflow 4: HITL (Human-in-the-Loop) Review

| Aspect | Assessment |
|--------|-----------|
| **Intended Outcome** | Low-confidence decisions queued → SSE notified → human reviews → approve/reject/escalate |
| **Current Actual Behavior** | ✅ **COMPLETE** — Full queue with SSE push notifications, approve/reject/escalate with rationale capture, audit trail |
| **Evidence** | [ui/src/pages/HitlReviewPage.tsx](products/aep/ui/src/pages/HitlReviewPage.tsx) · [controllers/HitlController.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/HitlController.java) |
| **UI Assessment** | ✅ Side-panel review detail, policy diff viewer, confidence badge |
| **AI/ML Role** | Confidence scores drive queue priority; ⚠️ no automated pre-classification or similar-case suggestion |
| **Test Evidence** | ✅ `AepHttpServerHitlTest.java`, `AepHttpServerHitlEscalationTest.java`, `useHitlQueue.test.tsx` |
| **Severity** | ✅ COMPLETE with enhancement opportunity (AI pre-classification) |

### Workflow 5: Learning and Policy Promotion

| Aspect | Assessment |
|--------|-----------|
| **Intended Outcome** | Episodes stored → LLM extracts facts → policies generated → approved → promoted |
| **Current Actual Behavior** | ✅ `DefaultLLMFactExtractor` makes real LLM calls via `LLMGateway`; SPO triple extraction; `EpisodeLearningPipeline` with `CompositeEvaluationGate`; policy approval before promotion |
| **Evidence** | [consolidation/DefaultLLMFactExtractor.java](products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/learning/consolidation/DefaultLLMFactExtractor.java) |
| **LLM Quality** | ✅ Virtual thread pool for blocking LLM calls; 0.2 temperature; max 1024 tokens; error recovery returns empty list (non-blocking) |
| **AI/ML Role** | ✅ Real LLM calls; structured SPO prompt; confidence scores on extracted facts |
| **Missing** | LLM token usage not metered; no evaluation of fact extraction quality over time |
| **Scheduler** | ✅ `LearningScheduler` runs periodic reflection when Data Cloud configured |
| **Test Evidence** | `AepHttpServerLearningTest.java`; unit tests on consolidation pipeline |
| **Severity** | P2 (quality metrics, cost tracking) |

### Workflow 6: Governance and Kill-Switch

| Aspect | Assessment |
|--------|-----------|
| **Intended Outcome** | Monitor runtime → detect policy violations → kill-switch or degrade → audit trail |
| **Current Actual Behavior** | ✅ **COMPLETE** — `KillSwitchService`, `GracefulDegradationManager`, `PolicyAsCodeEngine`, `EgressMonitor`, `PromptInjectionDetector` all wired; SOC2 framework |
| **Evidence** | [controllers/GovernanceController.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/GovernanceController.java) |
| **Security Analytics** | ✅ Egress monitoring; regex-based prompt injection detection |
| **SOC2** | ✅ `AepSoc2ControlFramework` with hardcoded controls; ⚠️ controls are static, not dynamically evaluated |
| **UI Assessment** | ✅ GovernancePage has section nav (Policies/Compliance/Tenancy/Audit); feature flags control section visibility |
| **AI/ML Missing** | No predictive governance; no ML-based anomaly threshold for kill-switch recommendation |
| **Test Evidence** | ✅ `AepHttpServerGovernanceTest.java` |
| **Severity** | ✅ COMPLETE (with predictive governance as P3 enhancement) |

### Workflow 7: Analytics and Anomaly Detection

| Aspect | Assessment |
|--------|-----------|
| **Intended Outcome** | Detect anomalies, generate forecasts, surface AI suggestions to dashboard |
| **Current Actual Behavior** | ✅ `AnalyticsController` with detect/query anomalies, forecast, KPI, metrics, query, aggregate. `AiSuggestionsController` derives suggestions from anomalies via `DataCloudAnalyticsStore` |
| **AI/ML Role** | `AiSuggestionsPanel` on `MonitoringDashboardPage`; suggestions scored by severity; ⚠️ `NaiveForecastingEngine` for forecasts (naive, not ML) |
| **Fallback** | ✅ When DataCloud absent, falls back to SLO metrics signals |
| **Test Evidence** | `AiSuggestionsIntegrationTest.java` present |
| **Key Gap** | No effectiveness tracking for suggestions; naive forecasting engine |
| **Severity** | P2 |

### Workflow 8: Compliance (GDPR/CCPA/SOC2)

| Aspect | Assessment |
|--------|-----------|
| **Intended Outcome** | Process GDPR access/erasure/portability, CCPA opt-out, export SOC2 report |
| **Current Actual Behavior** | ✅ All compliance endpoints implemented; `AepComplianceService` backed by Data Cloud |
| **Evidence** | [controllers/ComplianceController.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/ComplianceController.java) |
| **Privacy** | ✅ GDPR erasure endpoint; CCPA opt-out; ⚠️ actual data purge depth unclear |
| **Test Evidence** | `AepHttpServerComplianceTest.java` |
| **Severity** | P2 (data purge validation) |

---

## D. UX and Cognitive Load Review

### Information Architecture

**Current State:** 5 outcome-oriented navigation areas  
```
Operate  → Monitoring Dashboard, HITL Reviews, Run Detail, Cost Dashboard
Build    → Pipeline Builder, Pipeline List, Pattern Studio, Workflow Catalog
Learn    → Learning Episodes, Memory Explorer
Govern   → Governance Dashboard
Catalog  → Agent Registry, Agent Detail, Agent Marketplace
```

**Assessment:**
- ✅ Organized by user goal, not system component
- ⚠️ 15 distinct pages — some overlap could be collapsed
- ⚠️ No breadcrumbs feature-flagged off by default (`BREADCRUMBS: false`)
- ⚠️ No command palette feature-flagged off (`COMMAND_PALETTE: false`) — reduces discoverability

### Design System Adoption Gap (Critical)

**Evidence:** [ui/DESIGN_SYSTEM_ADOPTION.md](products/aep/ui/DESIGN_SYSTEM_ADOPTION.md)

| Metric | Status |
|--------|--------|
| Design system imports | 2 components (ErrorBoundary, ThemeProvider) |
| Raw `<button>` elements | ~64 needing migration |
| `@ghatana/design-system` adoption | ~3% |
| Consistent interaction patterns | ❌ Not guaranteed |

This is a significant consistency and accessibility risk. The `ThemeProvider` is wired, but raw HTML form elements bypass the design token system, creating visual inconsistency and potentially breaking theming, keyboard behavior, and accessibility.

### Form and Input Minimization

| Workflow | Current Manual Steps | Target | Gap |
|----------|---------------------|--------|-----|
| Create pipeline | 7+ (name → stages → params → validate → test → save → deploy) | 3 | Missing AI-assisted template |
| Configure stage | 5–10 field entries | 1–2 (AI prefill) | `ConfigurationPrefillService` implemented but depth unverified |
| Pattern creation | 6+ | 2 (describe → AI generates) | NLQ endpoint exists; UI integration unclear |
| Register agent | SDK-based, manual | Auto-discovery | Not implemented |

### Cognitive Load Issues

1. **Empty states not actionable** — Agent Registry shows empty list when Data Cloud absent with a message but no guided next step
2. **No inline contextual help** — Pipeline Builder stages lack inline docs for parameters
3. **Scattered run context** — Run detail requires navigation away from Monitoring Dashboard
4. **Feature flags default-off for discoverability features** — Breadcrumbs and command palette gated with no path to enable in prod

### Accessibility

- ✅ Playwright a11y tests exist (`e2e/a11y.spec.ts`) with axe-core WCAG 2.1 AA checks
- ⚠️ Raw HTML elements bypass design system accessibility guarantees
- ✅ Semantic HTML structure present in key pages (role="dialog" on HITL detail panel)

---

## E. AI/ML Pervasive Automation Review

### Where AI/ML is Correctly First-Class ✅

| Feature | Quality | Evidence |
|---------|---------|----------|
| **LLM Fact Extraction** | Production-ready — virtual thread pool, structured prompt, confidence scores, error recovery | [DefaultLLMFactExtractor.java](products/aep/aep-agent-runtime/src/main/java/com/ghatana/agent/learning/consolidation/DefaultLLMFactExtractor.java) |
| **Anomaly-Driven Suggestions** | Real analytics data, severity-ranked, fallback to SLO signals | [AiSuggestionsController.java](products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/AiSuggestionsController.java) |
| **ML Operator Generation** | `MLOperatorGenerator` uses LLMGateway to generate OperatorSpec with fallback to heuristics | Remediation tracker P1-7 |
| **Natural Language Pipeline Creation** | `NaturalLanguagePipelineService` with rule-based parsing | Remediation tracker P1-13 |

### Where AI/ML is Too Shallow ⚠️

| Feature | Current | Needed |
|---------|---------|--------|
| **Event Schema Inference** | Not implemented | Auto-detect schema from payload samples |
| **Forecasting** | `NaiveForecastingEngine` — simple extrapolation | ML time-series model |
| **HITL Routing** | Manual queue, confidence-sorted | Intelligent routing by reviewer expertise |
| **Suggestion Effectiveness** | Suggestions shown but unmeasured | Click-through rate, adoption rate, A/B testing |
| **Pipeline Stage Config** | `ConfigurationPrefillService` implemented | Needs validation that 80% prefill claimed is real |

### Where AI/ML is Over-Exposed

- **`AiSuggestionsPanel`** on the monitoring dashboard is a visible explicit panel — better as inline contextual hints embedded where the issue appears
- **NLQ (`/api/v1/nlp/parse`)** is behind a feature flag but should be the primary pipeline creation interface, not a secondary option

### Where Automation Needs Stronger Governance

- **Auto-promoted learning policies** — require confidence threshold enforcement (not just single approval)
- **LLM calls** — no token budget enforcement; cost could spiral in production
- **Anomaly-triggered suggestions** — no throttling; could flood dashboard with low-quality suggestions

---

## F. API / Backend / DB / Integration Review

### API Contract Completeness

| Endpoint Family | OpenAPI Documented | Implemented | Contract Test |
|----------------|-------------------|-------------|---------------|
| Events | ✅ | ✅ | ✅ `AepOpenApiSurfaceDriftTest` |
| Patterns | ✅ | ✅ | ✅ |
| Agents + Memory | ✅ | ✅ | ✅ |
| HITL | ✅ | ✅ | ✅ |
| Learning | ✅ | ✅ | ✅ |
| Governance | ✅ | ✅ | ✅ |
| Compliance | ✅ | ✅ | ✅ |
| Lifecycle (change approval, recertification) | ✅ | ✅ | `AepHttpServerLifecycleTest` |
| NLQ (`/api/v1/nlp/parse`) | ⚠️ Present in server, not in canonical openapi.yaml | ✅ | ❌ |
| AI Suggestions | ⚠️ Not in canonical openapi.yaml | ✅ | ✅ `AiSuggestionsIntegrationTest` |
| Capabilities (`/admin/capabilities/*`) | ⚠️ Not in canonical openapi.yaml | ✅ | ❌ |

**Finding:** 3 endpoint families are implemented and tested but not in the canonical `contracts/openapi.yaml`. This causes contract drift and OpenAPI documentation to misrepresent the full API surface.

### Backend Architecture Observations

- ✅ **ActiveJ Promise chains** correctly used throughout — no blocking on event loop
- ✅ **Constructor injection** throughout all controllers
- ✅ **Null-safe patterns** — `@Nullable` annotations on optional dependencies with graceful fallback
- ✅ **`AepSecurityFilter`** wraps all routes — OWASP headers, CORS, rate limiting, payload size
- ✅ **`AepAuthFilter`** enforces JWT when `AEP_JWT_SECRET` is set; public endpoints bypass
- ⚠️ **`AepHttpServer` constructor overload explosion** — 9 constructors for the same class; violates clean DI; should be a builder pattern
- ⚠️ **`new PipelineController(...)` discarded** at line ~1370 — `PipelineController` is instantiated but its reference immediately dropped; pipeline routes are handled directly in `AepHttpServer` inline methods; dead code / architectural inconsistency

### Persistence / Data Lifecycle

| Concern | Status |
|---------|--------|
| Tenant isolation on all repository operations | ✅ |
| Pipeline versioning (draft → published → archived) | ✅ |
| Run history durability | ⚠️ In-memory by default; lost on restart |
| Event persistence to Data Cloud | ✅ via `EventCloud` when DC configured |
| Memory type classification | ✅ episodic / semantic / procedural / preference |
| Audit trails on decisions | ✅ timestamp + actor + rationale |
| Data retention/deletion (GDPR erasure) | ⚠️ Endpoint exists; actual deep-delete depth unclear |
| Event deduplication | ❌ Not visible in event processing path |

### Cross-Product Integration

| Integration | Status |
|-------------|--------|
| AEP → Data Cloud (event log, entity store, memory) | ✅ `DataCloudClient` dependency; graceful degradation when absent |
| Data Cloud → AEP | ✅ One-way; Data Cloud does not import AEP at compile time |
| AEP → Platform Security (`PolicyAsCodeEngine`, `KillSwitchService`) | ✅ Injected via constructor |
| AEP → Platform Observability (Prometheus, Micrometer) | ✅ Optional Prometheus registry |
| AEP → Platform Toolruntime (ChangeApproval, Recertification) | ✅ Lifecycle controller wired |
| AEP UI → `@ghatana/realtime` (SSE) | ✅ `useSSESubscription` from `@ghatana/realtime` |
| AEP UI → `@ghatana/canvas` (pipeline builder) | ✅ Canvas plugin architecture |
| AEP UI → `@ghatana/design-system` | ⚠️ Only 2 components used (critical gap) |

---

## G. Governance / Privacy / Security / Visibility Review

### Governance

| Control | Status | Evidence |
|---------|--------|----------|
| Kill-switch (manual, immediate) | ✅ | `KillSwitchService` wired |
| Graceful degradation modes | ✅ | `GracefulDegradationManager` |
| Policy-as-code evaluation | ✅ | `PolicyAsCodeEngine` |
| Lifecycle change approval workflow | ✅ | `LifecycleController` |
| Recertification pipeline | ✅ | `RecertificationPipeline` |
| SOC2 compliance framework | ✅ | `AepSoc2ControlFramework` (hardcoded) |
| Policy auto-promotion threshold | ❌ | Single approval required but no confidence threshold |
| Predictive incident governance | ❌ | Reactive only |

### Privacy

| Control | Status |
|---------|--------|
| GDPR access, erasure, portability | ✅ |
| CCPA opt-out | ✅ |
| Consent service (`DefaultConsentService`) | ⚠️ Implemented but not wired into event processing path |
| Data retention policy enforcement | ❌ No automated retention/expiry visible |
| PII detection in event payloads | ❌ No PII scanning before event storage |

### Security

| Control | Status |
|---------|--------|
| JWT authentication via `AepAuthFilter` | ✅ Conditional on `AEP_JWT_SECRET` env var |
| OWASP security headers | ✅ `AepSecurityFilter` |
| CORS with configurable origins | ✅ `AEP_CORS_ORIGINS` env var |
| Rate limiting | ✅ `AepRateLimiter` |
| Input validation (tenantId, eventType, payload) | ✅ `AepInputValidator` |
| Prompt injection detection | ✅ `RegexPromptInjectionDetector` |
| Egress monitoring | ✅ `DefaultEgressMonitor` |
| Agent registration security scan | ❌ Stated in design docs; not enforced in `AgentController` |
| Secret handling | ⚠️ JWT secret via env var; Redis/DB credentials via env vars — no secret store integration visible |

### Observability

| Signal | Status |
|--------|--------|
| `/health` (shallow) | ✅ |
| `/health/deep` (dependency state) | ✅ Database, Redis, Data Cloud, pipeline storage |
| `/metrics` (Prometheus) | ✅ When `PrometheusMeterRegistry` configured |
| `/metrics/slo` (SLO counter snapshots) | ✅ |
| Structured logging (SLF4J + MDC) | ✅ |
| Correlation ID propagation | ⚠️ Not visible in event processing; no `X-Correlation-ID` extraction |
| LLM token usage metrics | ❌ Cost is invisible |
| AI suggestion effectiveness metrics | ❌ `AISuggestionMetricsCollector` claimed in tracker but no verification |
| Distributed tracing (OpenTelemetry) | ❌ No OTel spans on critical paths |

---

## H. Production Operability Review

### Deployment Infrastructure

| Aspect | Status |
|--------|--------|
| Dockerfile | ✅ `products/aep/Dockerfile` exists |
| Helm charts | ✅ `products/aep/helm/` exists |
| Kubernetes manifests | ✅ `products/aep/k8s/` exists |
| CI/CD pipeline definition | ❌ Not visible in workspace |
| Migration scripts | ❌ No DB migration tooling (Flyway/Liquibase) visible |
| Rollback strategy | ✅ Pipeline versioning supports rollback; ⚠️ service-level rollback not tested |
| Environment variable management | ⚠️ Via env vars; no Vault/secret store integration |

### Resilience and Failure Handling

| Aspect | Status |
|--------|--------|
| Graceful degradation when Data Cloud absent | ✅ All controllers null-safe |
| Kill-switch → 503 propagation | ✅ `GracefulDegradationManager` |
| Retry behavior on Data Cloud calls | ❌ Not visible in `DataCloudClient` usage |
| Timeout configuration on LLM calls | ⚠️ Virtual thread pool with no explicit timeout |
| Idempotent event processing | ❌ No deduplication key visible |
| Backpressure on SSE streaming | ⚠️ `SseController` heartbeat; no flow control visible |
| Concurrent request limits | ✅ Rate limiting present |

### Startup and Shutdown

- ✅ `CountDownLatch` for graceful shutdown with 2-second timeout
- ✅ `LearningScheduler.stop()` called on shutdown
- ✅ `SseController.shutdown()` called
- ⚠️ No readiness probe delay — server reports `/ready = up` as soon as it starts, before Data Cloud connectivity is confirmed
- ⚠️ `start()` throws checked `Exception` — wide exception type; specific failure modes are opaque

### Monitoring Stack (Per Repo docs)

- Grafana: http://localhost:3001
- Prometheus: http://localhost:9090
- Jaeger: http://localhost:16686
- Loki: http://localhost:3100

**Gap:** No AEP-specific Grafana dashboards visible in `monitoring/grafana/`. AEP metrics are scrape-able but not pre-visualized.

---

## I. Testing and Evidence Gaps

### Test Coverage Reality

| Layer | Tests Present | Quality |
|-------|--------------|---------|
| HTTP controller routes | ✅ 15+ test classes | ⚠️ Mostly reachability (HTTP 200 checks) |
| Event processing e2e | ✅ `EndToEndEventProcessingTest` | ✅ Good — real pattern detection |
| Golden path system | ✅ `AepGoldenPathSystemTest` | ✅ Improved — verifies run list, SLO metrics |
| Pipeline versioning | ✅ | ✅ |
| HITL full flow | ✅ `AepHttpServerHitlTest` | ✅ |
| Governance endpoints | ✅ | ✅ |
| Frontend components | ✅ Vitest + React Testing Library | ⚠️ |
| Frontend e2e | ✅ Playwright login + a11y | ⚠️ No full workflow e2e |
| Contract drift | ✅ `AepOpenApiSurfaceDriftTest` | ✅ |
| AI suggestion effectiveness | ✅ `AiSuggestionsIntegrationTest` | Unverified depth |
| LLM fact extraction accuracy | ❌ No accuracy benchmark | Critical gap |
| Data retention/erasure depth | ❌ | Critical gap |
| Cross-product flow (AEP + Data Cloud) | ✅ `AepHttpServerDataCloudIntegrationTest` | ✅ |

### Missing / Weak Tests

| Test Needed | Risk Level |
|-------------|------------|
| Event deduplication — duplicate event IDs produce one run | High |
| Consent gate — events rejected without consent | High |
| LLM fact extraction produces valid SPO triples (accuracy ≥ threshold) | High |
| GDPR erasure — data actually deleted from all stores | High |
| Kill-switch → all processing requests return 503 | Medium |
| LLM token budget enforcement | Medium |
| Readiness probe accuracy under Data Cloud disconnection | Medium |
| Pipeline builder UI — user can create and run a pipeline without errors | High |
| Session management — session expiry forces re-authentication | Medium |

---

## J. Prioritized Remediation Plan

### P0: Must Fix Before Production Trust

| # | Issue | Why It Matters | Fix |
|---|-------|---------------|-----|
| 1 | **Consent service not wired into event processing** | Events processed without user consent checks — GDPR/privacy violation | Wire `DefaultConsentService.checkConsent()` in `handleProcessEvent` before `engine.process()` |
| 2 | **`new PipelineController(...)` instantiated and immediately discarded** | Dead code signals architectural confusion; pipeline routes are duplicated in server | Either use `PipelineController` for all pipeline routes or delete the instantiation |
| 3 | **In-memory run history lost on restart** | No production run history without Data Cloud; operators cannot diagnose post-restart | Document prominently that Data Cloud is required for production; add startup warning |
| 4 | **No event deduplication** | Duplicate events can trigger duplicate pattern detections and runs | Add idempotency key checking in `handleProcessEvent` |

### P1: Required for Production Trust

| # | Issue | Why It Matters | Fix |
|---|-------|---------------|-----|
| 5 | **Design system adoption at 3%** | Inconsistent UX, broken theming, accessibility risks across 64+ raw elements | Complete design system migration of all raw `<button>`, `<input>`, `<select>` elements |
| 6 | **NLQ and AI suggestions endpoints missing from openapi.yaml** | Contract drift; API consumers don't know these exist | Add `/api/v1/nlp/parse`, `/api/v1/ai/suggestions`, `/admin/capabilities/*` to canonical OpenAPI spec |
| 7 | **Correlation IDs not propagated** | Cannot trace a request across AEP, Data Cloud, and LLM calls | Extract `X-Correlation-ID` header in `AepHttpServer`; propagate via MDC |
| 8 | **LLM calls have no timeout** | Long-running LLM calls can starve the virtual thread pool | Add `Duration` timeout to `LLMGateway.complete()` calls in `DefaultLLMFactExtractor` |
| 9 | **Agent registration security scan not enforced** | Malicious agents can be registered without review | Add security scan step to `AgentController.handleRegisterAgent` |
| 10 | **GDPR erasure depth unverified** | Compliance obligation not verified by tests | Add integration test verifying data is deleted from `dc_memory`, `EventLogStore`, and all caches |

### P2: Simplification and Automation Hardening

| # | Issue | Why It Matters | Fix |
|---|-------|---------------|-----|
| 11 | **AI suggestion effectiveness unmeasured** | Cannot improve or trust suggestions | Implement and activate `AISuggestionMetricsCollector`; surface CTR in monitoring dashboard |
| 12 | **`AepHttpServer` has 9 constructor overloads** | Maintainability and DI clarity nightmare | Refactor to builder pattern; single canonical construction path |
| 13 | **`NaiveForecastingEngine`** | Forecasts are unreliable extrapolations, not ML | Replace with time-series model backed by historical Data Cloud data |
| 14 | **Command palette and breadcrumbs feature-flagged off** | Users cannot discover features or navigate efficiently | Enable breadcrumbs by default; add command palette for power users |
| 15 | **No AEP-specific Grafana dashboards** | Operators blind to AEP-specific metrics | Create AEP dashboard: event throughput, pattern detections, HITL queue depth, LLM latency |
| 16 | **LLM token usage not tracked** | Cost invisibility in production | Add token count to LLM call logging; emit as metric |

### P3: Strategic Improvements

| # | Issue | Why It Matters | Fix |
|---|-------|---------------|-----|
| 17 | **Predictive governance** | Reactive-only means incidents happen before detection | ML-based anomaly threshold recommendation for kill-switch |
| 18 | **PII detection in event payloads** | Events may contain PII processed without awareness | Add PII scanner using regex/ML before event persistence |
| 19 | **Natural language as primary pipeline creation path** | Current NLQ is secondary; NL should be the default for most users | Promote NLQ input to pipeline builder's primary creation mode |
| 20 | **Agent capability auto-discovery** | Agents must be manually registered | Auto-scan registered agents and infer capability taxonomy |
| 21 | **Secret store integration (Vault)** | Env-var secrets have poor rotation, auditing, and access control | Integrate `platform:java:security` secret resolution |

---

## K. Simplicity and Automation Blueprint

### Screens to Merge or Consolidate

| Current State | Proposed Change | Rationale |
|--------------|----------------|-----------|
| `AgentRegistryPage` + `AgentDetailPage` | Single browsable directory with inline slide-out detail | Reduces navigation depth by 1 |
| `PatternStudioPage` + `LearningPage` | Tabbed experience within one page | Pattern authoring and episode learning are tightly coupled |
| `GovernancePage` sections | Progressive disclosure — show only active incidents by default; expand for SOC2 | Most operators don't need SOC2 detail daily |
| `WorkflowCatalogPage` + `AgentMarketplacePage` | Merge into unified Catalog with type filter | Reduce duplication |

### Steps to Eliminate

| Workflow | Today | Blueprint |
|----------|-------|-----------|
| Create pipeline | 7+ manual steps | 1: Describe intent → AI generates → review → one-click deploy |
| Configure stage parameters | 5–10 manual entries | 1: AI prefills; user reviews |
| Detect patterns | Manually define pattern NFA | 0: Auto-detected from event analysis; user promotes |
| Monitor for anomalies | Check dashboard periodically | 0: Push notification via SSE with suggested action |

### AI/ML Interventions to Add

1. **Inline stage suggestions** — "Events matching `payment.*` pattern often use Filter + Aggregate stages" shown inline in canvas, not a separate panel
2. **Auto-schema detection** — First 10 events of a new type auto-infer schema; user reviews
3. **Reviewer matching** — Route HITL items to reviewer with most approved decisions on similar `skillId`
4. **Policy confidence threshold** — Only auto-present policies for promotion when extraction confidence ≥ 0.85

### Governance Points to Preserve

1. Kill-switch: Always require human reason string
2. Policy promotion: Keep approval gate; add confidence threshold guard
3. GDPR erasure: Always synchronous confirmation response + audit event
4. New agent registration: Security scan required for external agents

---

## L. Product System Architecture Corrections

### Structural Issues Found

| Issue | Correction |
|-------|-----------|
| `AepHttpServer` handles pipeline routes inline while `PipelineController` class exists | Migrate pipeline route handlers to `PipelineController` and wire like other controllers |
| 9-overload constructor anti-pattern in `AepHttpServer` | Builder pattern with sensible defaults |
| `NaiveForecastingEngine` in production path | Replace or clearly mark as `@DevelopmentOnly` |
| SOC2 controls hardcoded in `AepSoc2ControlFramework` | Move to configurable policy evaluation via `PolicyAsCodeEngine` |
| `DefaultConsentService` exists but not wired | Wire into `handleProcessEvent` at request validation stage |

### Dependency Direction — Current vs Required

```
Current:
  AEP ──depends on──► Data Cloud public contracts ✅
  AEP ──depends on──► platform/java/{security,observability,toolruntime} ✅
  Data Cloud ──does NOT depend on──► AEP ✅
  AEP UI ──depends on──► @ghatana/canvas, @ghatana/realtime ✅
  AEP UI ──BARELY uses──► @ghatana/design-system ❌ (must fix)

Required:
  AEP UI ──depends on──► @ghatana/design-system (all form elements) ✅ needed
```

### OpenAPI Contract Completeness

The canonical `contracts/openapi.yaml` is missing:
- `/api/v1/nlp/parse`
- `/api/v1/ai/suggestions`
- `/admin/capabilities/*`
- `/lifecycle/*` (change approval + recertification)
- `/api/v1/costs/summary`

The `AepOpenApiSurfaceDriftTest` partially guards this, but the drift guard needs to run against the canonical YAML, not just check paths are present in the server.

---

## M. Final Truth Statement

### What Truly Works End-to-End Today

| Feature | Status | Evidence |
|---------|--------|----------|
| Event ingestion → processing → pattern detection → response | ✅ | `EndToEndEventProcessingTest` |
| Pipeline CRUD with tenant isolation and versioning | ✅ | `AepHttpServerPipelineVersioningTest` |
| HITL review with SSE push notifications | ✅ | `AepHttpServerHitlTest` |
| LLM-based fact extraction from episodes | ✅ | `DefaultLLMFactExtractor` with real LLM calls |
| Governance kill-switch and degradation | ✅ | `AepHttpServerGovernanceTest` |
| GDPR/CCPA compliance endpoints | ✅ | `AepHttpServerComplianceTest` |
| Health + Prometheus metrics | ✅ | `/health/deep`, `/metrics` |
| Agent registry with graceful degradation | ✅ | Empty response when DC absent |
| Real-time SSE run updates in UI | ✅ | `useLivePipelineRuns` + `useSSESubscription` |
| Lifecycle change approval + recertification | ✅ | `AepHttpServerLifecycleTest` |

### What Works Only Partially

| Feature | What's Missing |
|---------|---------------|
| AI suggestions | Real suggestions but no effectiveness measurement |
| Pipeline execution | DAG execution implemented; full E2E with verifiable output not deeply tested |
| Learning policy promotion | Works but no confidence threshold guard; effectiveness not measured |
| Natural language pipeline creation | Service implemented; UI integration path unclear |
| Forecasting | Naive algorithm, not ML |
| Design system | ThemeProvider wired; 97% of form elements still raw HTML |

### What is Misleading

| Claim | Reality |
|-------|---------|
| `DESIGN_SYSTEM_ADOPTION.md` implies migration is underway | Only 2 components imported; 64+ raw elements remain |
| Remediation tracker shows 100% complete (all 44 tasks ✅) | Many completions reference "rule-based" implementations for AI tasks; actual AI depth is lower than stated |
| `ConfigurationPrefillService` claims "80% of stage parameters auto-prefilled" | No validation test proves this claim |
| `NaturalLanguagePipelineService` is marked complete | No E2E test from UI NLQ input → created pipeline exists |

### What Creates Unjustified User Burden

1. **Manual stage parameter configuration** — even with `ConfigurationPrefillService` claimed complete
2. **Empty registry states** — no guided "next step" when Data Cloud absent
3. **Manual pattern definition** — NFA-based patterns require technical expertise
4. **Scattered operational context** — must navigate between 3 pages to understand one run
5. **Feature flags off by default** — breadcrumbs and command palette not available without explicit env config

### What Blocks Production Trust Right Now

1. **Consent service not wired** — GDPR/privacy violation potential
2. **In-memory run history** — data loss on restart without Data Cloud
3. **No event deduplication** — duplicate processing risk
4. **No correlation ID propagation** — cannot diagnose multi-service failures
5. **LLM calls without timeout** — outage risk in learning pipeline

### What Must Change for AEP to Become Truly Production-Grade

| Priority | Change | Impact |
|----------|--------|--------|
| P0 | Wire consent service into event processing | Privacy compliance |
| P0 | Fix discarded `PipelineController` instance | Architecture integrity |
| P0 | Document/enforce Data Cloud requirement for run history | Operational honesty |
| P1 | Complete design system migration (64+ elements) | UX consistency, a11y |
| P1 | Add correlation ID propagation | Diagnosability |
| P1 | Add LLM call timeouts | Resilience |
| P1 | GDPR erasure depth test | Compliance verification |
| P2 | AI suggestion effectiveness metrics | Trustworthy automation |
| P2 | Replace `NaiveForecastingEngine` with ML | Credible analytics |
| P2 | AEP Grafana dashboard | Operational visibility |
| P3 | Predictive governance | Proactive safety |

---

## Summary

AEP has made substantial progress since the April 18, 2026 audit. The critical P0 blocker — the EventController stub — was resolved: event processing now calls the real engine, wires pattern detection, records SLO metrics, and returns evidence. The HITL and Governance workflows are solid and production-ready. The LLM-backed learning pipeline is a genuine AI capability.

The remaining blockers are not fundamental architecture gaps but concrete implementation gaps: a consent service that exists but isn't wired, a design system that exists but is barely used, a forecasting engine that is naive, and LLM calls that lack timeouts. These are fixable in days to weeks, not months.

The product is at **production-capable but not production-trustworthy** maturity. It will not embarrass you in a demo. It will potentially surprise you in an incident.

**Recommended next sprint focus:**
1. Wire consent service (hours)
2. Add correlation ID propagation (hours)
3. Add LLM timeout (1 day)
4. Complete design system migration (1–2 weeks)
5. Add AEP Grafana dashboard (1 day)
6. Write missing privacy and deduplication tests (2–3 days)

After those six items, AEP reaches the bar where it can be trusted by real operators in real production.
