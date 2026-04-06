# YAPPC AI-Native Transformation — Master Overview

**Version:** 1.0  
**Date:** 2026-04-05  
**Status:** Planning  
**Total Horizon:** 18 months (4 phases)

---

## 1. Vision Recap

YAPPC's north star is a **self-evolving, AI-native development platform** where AI is not a feature but the invisible operating system of all development workflows. The 8-phase development cycle — `Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve` — must be driven by AI at every phase boundary, not just at explicit "AI buttons."

**Current Maturity:** 3/10 AI-Native, 4/10 Feature Completeness  
**Target Maturity (18mo):** 9/10 AI-Native, 10/10 Feature Completeness

---

## 2. Current Architecture Summary

```
products/yappc/
├── core/                         Java backend modules
│   ├── ai/                       AI routing, adapters, cost tracking, requirements AI
│   ├── agents/runtime/           Agent base, tools, HITL coordinator
│   ├── agents/workflow/          Workflow orchestration
│   ├── agents/[specialists]/     Code, delivery, architecture, testing specialists
│   ├── services-platform/        Facade services for all 8 phases
│   ├── services-lifecycle/       Lifecycle: phase transitions, approval, AEP bridge
│   ├── scaffold/                 Template generation (api, core, templates, engine)
│   ├── refactorer/               Code refactoring engine
│   └── knowledge-graph/          Knowledge graph core + YAPPC graph service
│
├── frontend/                     TypeScript/React
│   ├── apps/web/                 React + Vite SPA shell
│   ├── apps/api/                 Fastify/GraphQL BFF
│   └── libs/
│       ├── yappc-ai/             AI orchestration, ML, agents (frontend)
│       ├── auth/                 OAuth, RBAC lib
│       ├── collab/               CRDT, presence, collaboration
│       ├── yappc-canvas/         Canvas visualization
│       └── [10+ more libs]
│
└── config/
    ├── pipelines/lifecycle-management-v1.yaml
    └── agents/phase-transition-events.yaml
```

---

## 3. Strategic Pillars

### Pillar 1: Foundational Completeness (Months 0-3)
Remove all stubs and hardcoded responses. Every API must be real, every service must persist, every flow must be end-to-end verified.

### Pillar 2: Verified AI Integration (Months 0-3 overlapping)
Every declared AI capability must be traced and verified end-to-end. Confidence scoring, fallback behavior, and AI quality telemetry must be operational before investing in new AI features.

### Pillar 3: Implicit AI Transformation (Months 3-9)
Shift AI from explicit (button-triggered) to implicit (background, proactive, seamless). AI analyzes continuously; it surfaces insights rather than waiting to be asked.

### Pillar 4: Autonomous Intelligence (Months 9-18)
The platform learns, self-optimizes, and autonomously handles routine development decisions. Human intervention becomes the exception for strategic choices only.

---

## 4. P0 Blockers (Must Fix First)

| # | Blocker | Location | Impact |
|---|---------|----------|--------|
| B1 | Approval API slice incomplete beyond list/approve/reject handlers | `core/services-lifecycle/` API layer | Core approval actions work, but workflow coverage is still partial |
| B2 | Approval domain and notifications remain fragmented | `core/services-lifecycle/`, `core/agents/runtime/` | Prevents full approval lifecycle rollout |
| B3 | Java auth baseline exists but product-wide enforcement is not complete | `core/`, `frontend/apps/api/` | Some flows remain outside the unified auth path |
| B4 | AI call path verified, but telemetry/confidence layers are still missing | `core/ai/`, `frontend/apps/api/` | AI features are testable, not yet fully observable |
| B5 | Encryption wired for Data Cloud project env vars; other secret paths still unresolved | `infrastructure/datacloud/`, `frontend/apps/api/` | Sensitive-data ownership is clearer, but not fully consolidated |

---

## 5. Phase Breakdown

### Phase 1: Unblock & Verify (Months 0-3, ~320 hours)

| Stream | Work | Owner Role |
|--------|------|-----------|
| Auth | Real JWT + RBAC end-to-end (Java + BFF + frontend) | Platform Eng |
| Approval | Wire JDBC approval service to API controller | Backend Eng |
| AI Verification | Trace + test all LLM call paths; add telemetry | AI Eng |
| Code Gen | LLM-powered generation replacing all template stubs | AI/Backend Eng |
| Requirements | AI-assisted writing wired through to persistence | AI/Backend Eng |

**Exit Criteria:**
- Zero hardcoded responses in any controller
- All auth flows tested end-to-end (login → JWT → RBAC-protected action)
- Every AI workflow has a passing integration test that calls a real (or test-double) LLM
- Confidence scoring on all AI outputs
- AI quality metrics visible in Grafana

### Phase 2: Core Intelligence (Months 3-6, ~280 hours)

| Stream | Work | Owner Role |
|--------|------|-----------|
| Phase Transitions | AI-powered gate evaluation replacing if/else chains | AI/Backend Eng |
| Knowledge Graph | Production-scale entity extraction + continuous update | AI/Backend Eng |
| Collab | CRDT conflict resolution complete; AI-assisted merge suggestions | Frontend Eng |
| Implicit AI | Background code analysis, proactive suggestions | AI/Frontend Eng |
| Testing AI | AI-generated test cases from code changes | AI/QA Eng |

**Exit Criteria:**
- Phase transitions evaluated by AI gate agents, not hardcoded logic
- Knowledge graph updates automatically on every code commit
- CRDT conflict resolution handles all test scenarios without data loss
- Proactive AI suggestions appear in UI without user action
- AI generates test cases for every new requirement

### Phase 3: Predictive Intelligence (Months 6-12, ~400 hours)

| Stream | Work | Owner Role |
|--------|------|-----------|
| Predictive Errors | AI predicts and surfaces likely errors before commit | AI Eng |
| Architecture Advisor | AI proposes architecture improvements continuously | AI Eng |
| Adaptive Workflows | Workflows route and prioritize dynamically via AI | AI/Backend Eng |
| KG Intelligence | Cross-project knowledge sharing + auto-discovery | AI/Backend Eng |
| Autonomous Testing | Test suites self-optimize based on AI coverage analysis | AI/QA Eng |
| AI-Ops | AI-configured monitoring, predictive scaling | Platform Eng |

**Exit Criteria:**
- AI-predicted errors prevent ≥30% of bugs before they reach review
- Architecture advisories fired proactively on pattern violations
- Workflows adapt routing automatically based on context
- Knowledge graph spans multiple projects and learns continuously

### Phase 4: Autonomous Platform (Months 12-18, ~360 hours)

| Stream | Work | Owner Role |
|--------|------|-----------|
| Self-Learning | Platform learns from interaction data; models improve | AI Eng |
| Autonomous Workflows | 80% of routine dev decisions made autonomously | All |
| Policy Governance | AI enforces architectural governance automatically | AI/Platform Eng |
| Ecosystem Intelligence | AI selects tools, enforces best practices, monitors compliance | AI/Platform Eng |

**Exit Criteria:**
- Platform AI-Native maturity score ≥ 9/10
- 80% of routine decisions handled autonomously
- Human intervention only for strategic architectural choices

---

## 6. Technology Stack (As-Is with Gaps Noted)

### Java Backend

| Layer | Technology | Status |
|-------|-----------|--------|
| Framework | ActiveJ (event loop) | ✅ Established |
| LLM Routing | `AIModelRouter` + Ollama/OpenAI/Anthropic adapters | ✅ Exists, call path verified |
| Agent Framework | `YAPPCAgentBase`, `YappcAgentSystem` | ✅ Framework exists |
| Lifecycle | `YappcLifecycleService`, `GateEvaluator` | ✅ Implemented |
| Persistence | JDBC (`JdbcHumanApprovalService`) | ⚠️ Not all services wired |
| Auth | Product-level JWT/auth filters in AI API + hardened BFF JWT config | ⚠️ Baseline wired, not complete product-wide |
| Knowledge Graph | `KnowledgeGraph`, `YAPPCGraphService` | ⚠️ Thin, no scale |
| Cost Tracking | `CostTrackingService` | ✅ Exists, end-to-end unverified |
| Semantic Cache | `SemanticCacheService`, `SemanticCache` | ✅ Exists |

### TypeScript / BFF

| Layer | Technology | Status |
|-------|-----------|--------|
| BFF Framework | Fastify + GraphQL (type-graphql) | ✅ Established |
| ORM | Prisma | ✅ Established |
| AI Lib | `yappc-ai` (multi-provider, 8+ agents) | ✅ Rich, core call path verified |
| Auth | OAuth + RBAC middleware, JWT | ⚠️ JWT secrets hardened; session/rotation work remains |
| Collab | CRDT + WebSocket + presence | ⚠️ Framework ok; conflict resolution incomplete |
| State | Jotai atoms | ✅ Established |
| Realtime | `RealTimeService` + `WebSocketService` | ⚠️ Exists, fragile at scale |

### Missing Infrastructure

| Gap | Required Technology | Plan File |
|-----|-------------------|-----------|
| AI quality telemetry | Prometheus custom metrics + Grafana dashboards | [06-ai-llm-integration.md](./06-ai-llm-integration.md) |
| Confidence scoring | Custom scoring layer on `AIModelRouter` output | [06-ai-llm-integration.md](./06-ai-llm-integration.md) |
| Background AI analysis | Event-driven pipeline (AEP + ActiveJ) | [07-implicit-ai-layer.md](./07-implicit-ai-layer.md) |
| Cross-project KG | Graph DB (Neo4j or compatible) or labeled adjacency in JDBC | [04-knowledge-graph.md](./04-knowledge-graph.md) |

---

## 7. Dependency Graph

```
Auth (01) ─────────────────────────────────┐
                                           │
Approval (02) + AI Foundation (06) ────────┼──► Phase Transitions (03)
                                           │         │
Code Generation (08) ◄─────────────────────┤         ▼
                                           │    Requirements (09)
Knowledge Graph (04) ◄─────────────────────┤         │
         │                                 │         ▼
         └──────────► Implicit AI (07) ◄───┘    Testing & QA (10)
                              │                       │
                              ▼                       ▼
                     Collab (05)             Deployment & Ops (11)
```

---

## 8. Success Metrics

| Metric | Month 3 | Month 6 | Month 12 | Month 18 |
|--------|---------|---------|---------|---------|
| AI-Native Maturity | 5/10 | 7/10 | 8/10 | 9/10 |
| Feature Completeness | 8/10 | 9/10 | 10/10 | 10/10 |
| Production Readiness | 7/10 | 9/10 | 10/10 | 10/10 |
| P0 Blockers | 0 | 0 | 0 | 0 |
| Hardcoded Stubs | 0 | 0 | 0 | 0 |
| AI Quality Telemetry | ✅ | ✅ | ✅ | ✅ |
| Development Velocity | +0% | +20% | +40% | +60% |
| Defect Density | baseline | -20% | -40% | -60% |

---

## 9. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| LLM call latency degrades UX | High | High | Streaming responses + semantic cache |
| CRDT merge loses data at scale | Medium | Critical | Chaos testing + CRDT property tests |
| AI confidence scores uncalibrated | High | Medium | Calibration dataset + shadow scoring |
| Knowledge graph performance at scale | High | High | Pagination + lazy graph traversal + cache |
| Auth bypass in dev mode leaking to prod | Medium | Critical | `devAuth.ts` gated behind `NODE_ENV === 'development'` check + CI lint rule |
| Approval JDBC service not transactional | Medium | High | DB transactions required on all approval writes |

---

## 10. Links

- [Feature Analysis](../YAPPC_AI_NATIVE_FEATURE_ANALYSIS.md)
- [Audit Report V4.1](../../YAPPC_V4.1_COMPREHENSIVE_AUDIT_REPORT.md)
- [LLM Integration Guide](../LLM_INTEGRATION_GUIDE.md)
- [AEP Engine Conventions](../../../../docs/AEP_ENGINE_CONVENTIONS.md)
- [Phase → Agent Matrix](../03-lifecycle-phase-to-agent-matrix.md)
- [Lifecycle Pipeline Config](../../config/pipelines/lifecycle-management-v1.yaml)
