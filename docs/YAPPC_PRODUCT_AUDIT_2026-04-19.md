# YAPPC Product Reality Audit

**Date:** 2026-04-19  
**Scope:** YAPPC product end-to-end — frontend, BFF/API, Java backend services, platform libraries, AI/ML layer, data persistence, security, observability, and CI/CD  
**Context Libraries:** `platform/typescript/*`, `platform/java/*`, `products/aep`, `products/data-cloud`, `shared-services`  
**Auditors:** Principal Product Architect + Staff Full-Stack Engineer + AI/ML Systems Reviewer + Security/Privacy Auditor + Production Readiness Assessor  

---

## A. Executive Verdict

| Dimension | Score | Summary |
|-----------|-------|---------|
| **Overall Maturity** | 3 / 10 | Architecturally intentional; implementation is heavily in-progress |
| **End-to-End Readiness** | 2 / 10 | Critical flows (auth, lifecycle, agents, collaboration) are only partially end-to-end |
| **Production Readiness** | 2 / 10 | Insecure defaults, split runtime ownership, and missing deployment hardening block production trust |
| **AI/ML-First Maturity** | 3 / 10 | Significant AI scaffolding exists; implicit/pervasive automation is incomplete |
| **Automation Maturity** | 3 / 10 | CI exists but lacks YAPPC-specific gates; agent dispatch wired but not all downstream paths proven |
| **UX Simplicity** | 4 / 10 | Modern design system, but significant cognitive overload on canvas; navigation rail duplicated 3x |
| **Cognitive Load** | 3 / 10 | 18+ simultaneous canvas controls; lifecycle phase shown in 3 locations; feature accumulation without hierarchy |
| **Governance/Privacy/Security** | 3 / 10 | RBAC, audit logging, and tenant isolation are structurally present but fragmented with dangerous dev-mode fallbacks |
| **Operability/Resilience** | 2 / 10 | Port mismatch between service and health probes; no unified runtime topology; no rollback-tested path |

### Top Blockers

1. **Split runtime authority**: `YappcLifecycleService` (port 8082), `YappcHttpServer` (separate), and Node Fastify BFF all declare ownership of overlapping API paths — no single authoritative backend.
2. **Auth fragment**: `devAuth.ts` bypass is env-gated but used in CI; previous `_shell.tsx` hardcoded `{ id: 'user-1', name: 'John Doe' }` — auth surface is not fail-closed across all entry points.
3. **Agent module disconnected**: `:products:yappc:core:agents` is not registered in `settings.gradle.kts`; services module has a TODO comment where agent wiring should be — agents cannot be called from the primary runtime.
4. **OpenAPI vs runtime contract drift**: Contract declares `localhost:8080`, service runs on `8082`; CI health checks target wrong port; release evidence cannot confirm startup against the real contract.
5. **Javalin dependency on platform**: `platform/build.gradle.kts:68` retains Javalin despite ADR-004 mandating ActiveJ-only — no clean migration path exists today.
6. **No YAPPC-specific CI gate**: 25 GitHub Actions workflows — none dedicated to building or testing YAPPC; product is not continuously verified.
7. **Deprecated frontend packages still imported**: `@ghatana/yappc-state` and `@ghatana/yappc-graphql` are marked deprecated but remain as active dependencies; `reactflow` v11 and `@xyflow/react` v12 coexist in the same `package.json`.
8. **E2E CI toolchain mismatch**: `e2e-tests.yml` uses Node 18 and `pnpm@8` but workspace requires Node 20+ and `pnpm@10.28.2`.

---

## B. Reconstructed Product Model

### Target Personas

| Persona | Primary Jobs |
|---------|-------------|
| Product Owner / Product Manager | Capture intent, define success criteria, prioritize features |
| Architect / Tech Lead | Shape solution architecture, review gates, approve transitions |
| Developer | Generate code, review AI-produced artifacts, build and debug |
| QA / Test Lead | Validate outputs, manage test plans, approve release gates |
| DevOps / SRE | Deploy, observe, monitor, roll back, manage operations |
| Admin / Security Officer | Govern access, review audit trail, manage policies |

### Jobs to Be Done

1. Turn a product idea into an executable plan with minimal manual work.
2. Generate correct, production-ready code artifacts from a validated intent.
3. Track the project's lifecycle phase, gates, and readiness automatically.
4. Collaborate visually on architecture and product design in real time.
5. Monitor running deployments and surface anomalies without manual log review.
6. Maintain full governance, audit, and policy compliance without extra manual effort.

### Expected AI/ML Role

- **Intent Phase**: NLP-driven extraction of requirements from free-form input; automatic success-criteria derivation.
- **Shape Phase**: Architecture pattern recommendations, tech-stack selection, dependency risk detection.
- **Validate Phase**: Policy/compliance scoring, automated gate pre-check, anomaly flagging.
- **Generate Phase**: Code generation, artifact quality scoring, duplicate/conflict detection.
- **Run Phase**: Build anomaly detection, automated rollback recommendations.
- **Observe Phase**: Anomaly detection, SLO violation prediction, operator guidance summarization.
- **Learn/Evolve**: Feedback loop from outcomes to prompt refinement; adaptive workflow planning.

AI/ML should be **implicit and outcome-oriented** — the user should see outcomes, not LLM prompts, model names, or provider selection screens.

### Justified Human Review Points

- Irreversible lifecycle phase transitions (GENERATE → RUN, RUN → production deploy).
- High-risk code generation where confidence is below threshold.
- Compliance approvals for governed environments.
- Security exceptions and policy overrides.

---

## C. End-to-End Workflow Audit Matrix

### Workflow 1: User Login and Session Establishment

| Dimension | Assessment |
|-----------|-----------|
| **Intended Outcome** | Authenticated user reaches the workspace/project dashboard with a valid session |
| **Actual Current Behavior** | `AuthProvider.tsx` calls `/api/auth/me`, maps session to user atom. `devAuth.ts` bypass active in dev/CI. `auth.middleware.ts` is JWT-validated in production paths. |
| **User Burden** | Low — login form exists, JWT token stored in `localStorage` via `auth-session.ts` |
| **AI/ML Role Today** | None |
| **AI/ML Role That Should Exist** | None needed for basic auth; SSO could infer identity context |
| **UI Assessment** | Login / register routes exist. Auth guard via `useAuth()` hook. OK but no visible loading/error state for session rehydration failures. |
| **API Assessment** | `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/api/auth/me` correctly declared in OpenAPI and in `routes/auth.ts`. Refresh token rotation via `UserSession` model with revocation support. |
| **Backend Assessment** | `auth.middleware.ts` validates JWT, populates `request.user`. `rbac.middleware.ts` implements `requirePermission`. `devAuthBypass` is guarded by `assertDevAuthBypassAllowed()` + env check. **Risk: dev bypass can silently activate if `NODE_ENV` is set incorrectly or via env variable leak.** |
| **DB Assessment** | `UserSession` model with sessionToken uniqueness, `revokedAt` field, TTL enforced. Architecture is sound. |
| **Async/Integration** | Auth gateway integration (`/api/auth/me`) crosses BFF to potential Java backend or shared-service. Unclear if `auth-gateway` shared service is the true authority. |
| **Governance/Privacy** | Audit log for auth events exists via `AuditService`. No confirmed audit on failed login attempts. Token stored in `localStorage` (XSS risk — should use httpOnly cookies). |
| **Production Operability** | `JWT_ACCESS_SECRET` environment variable validated at startup. No confirmed secret rotation mechanism documented. |
| **Test Evidence** | `auth-flow.api-e2e.test.ts` and `security-session-ownership.test.ts` exist. Integration test coverage present. |
| **Key Gaps** | localStorage token storage (XSS risk); unclear shared auth-gateway authority; dev bypass env leakage risk |
| **Severity** | High |
| **Files Involved** | [apps/api/src/middleware/auth.middleware.ts](../products/yappc/frontend/apps/api/src/middleware/auth.middleware.ts), [devAuth.ts](../products/yappc/frontend/apps/api/src/middleware/devAuth.ts), [AuthProvider.tsx](../products/yappc/frontend/web/src/providers/AuthProvider.tsx), [auth-session.ts](../products/yappc/frontend/web/src/providers/auth-session.ts) |

---

### Workflow 2: Project Lifecycle Phase Transition

| Dimension | Assessment |
|-----------|-----------|
| **Intended Outcome** | User sees current phase, can advance with gated validation, AI assists with readiness assessment |
| **Actual Current Behavior** | Frontend has `useLifecycleData.ts` with TanStack Query hooks for all lifecycle operations. `lifecycle.tsx` route renders `LifecycleExplorer` with AI insights, readiness anomalies, and automation plan. Java `PhaseGateValidator` runs entry-criteria, artifact-presence, and exit-criteria checks. |
| **User Burden** | Medium — lifecycle phase displayed in 3 locations simultaneously (UX redundancy). Decision-support defaults exist but may not be deeply AI-driven. |
| **AI/ML Role Today** | `useAIInsights`, `useAIRecommendations`, `useReadinessAnomalies`, `useLifecycleAutomationPlan` hooks exist. Backend `AIService` returns insights/predictions from Prisma. Phase timing telemetry emitted. |
| **AI/ML Role That Should Exist** | Proactive gate pre-check with confidence score before user attempts transition; auto-populate artifact checklist from project context; anomaly detection that blocks transitions silently unless user overrides. |
| **UI Assessment** | Phase shown in 3 places: app shell, project shell, canvas. Cognitive overload. `lifecycle.tsx` has correct structure but FOW stage labels and anomaly badges add complexity. |
| **API Assessment** | `POST /api/v1/lifecycle/advance` and `GET /api/v1/lifecycle/phases` declared in OpenAPI. BFF `lifecycle.ts` route proxies to Java backend. Automation planning via `POST /projects/:projectId/automation/plan`. |
| **Backend Assessment** | `YappcLifecycleService` wires `IntentApiController`, `ShapeApiController`, `GenerationApiController`, `ValidationApiController`. `PhaseGateValidator` is well-designed with 3-stage validation. However: server port 8082 vs OpenAPI/CI declaring 8080. |
| **DB Assessment** | Lifecycle phase stored in `Project.lifecyclePhase` Prisma field. Artifact presence checked via `YappcArtifactRepository`. Transitions are DB-backed through DataCloud adapters. |
| **Async/Integration** | `AepEventBridge` publishes phase transition events to AEP. `DurableEventCloudPublisher` wraps publishing with retry. `YappcAgentOrchestrationBootstrapper` wires agent triggers on phase events. |
| **Governance/Privacy** | `ApprovalStateMachine`, `JdbcHumanApprovalService`, and `ApprovalAuditLogger` exist in services-lifecycle. Audit trail for transitions wired. |
| **Production Operability** | Port mismatch (8080 vs 8082) means CI cannot validate health probes correctly. `LifecycleServiceModule` AI runtime config validated via `YappcEnvironmentConfigTest`. |
| **Test Evidence** | `lifecycle-gates.integration.test.ts` and `LifecycleAuthApiContractTest.java` exist. `AICallPathTest.java` validates AI facade routing. **Gap: no confirmed end-to-end test of full INTENT → SHAPE → VALIDATE → GENERATE path through real services.** |
| **Key Gaps** | Port drift; 3x phase navigation redundancy; no complete E2E test across all 7 phases; agent module not wired in services build |
| **Severity** | Critical |
| **Files Involved** | [core/services-lifecycle/](../products/yappc/core/services-lifecycle/), [lifecycle.ts BFF](../products/yappc/frontend/apps/api/src/routes/lifecycle.ts), [lifecycle.tsx route](../products/yappc/frontend/web/src/routes/app/project/lifecycle.tsx), [useLifecycleData.ts](../products/yappc/frontend/web/src/hooks/useLifecycleData.ts) |

---

### Workflow 3: Canvas-Based Visual Product Design

| Dimension | Assessment |
|-----------|-----------|
| **Intended Outcome** | User creates visual architecture diagrams, gets AI suggestions, collaborates in real time |
| **Actual Current Behavior** | `canvas.tsx` uses `@xyflow/react` (ReactFlow v12) with `CanvasChromeLayout` from `@ghatana/canvas`. 30+ keyboard shortcuts, 7 canvas modes, node context menus, export, minimap, drawing tools. `useAIAssistant` hook provides suggestion/ghost-node overlays via `getCanvasAIService`. CRDT collaboration via Yjs. |
| **User Burden** | High — **18+ simultaneously visible control groups** on the canvas. Mode selector, sketch toolbar, history toolbar, minimap, ReactFlow controls, status bar, left task panel (280px always visible), right drawers (4+ triggers), floating help, command palette trigger. Users see everything at once. |
| **AI/ML Role Today** | `useAIAssistant` detects patterns, suggests nodes/connections/gaps/risks, generates ghost nodes. Confidence scoring (0-1) on suggestions. |
| **AI/ML Role That Should Exist** | Progressive disclosure of suggestions (not all at once); proactive architecture review triggered after N nodes placed; automated pattern detection that runs silently and surfaces only on user request or high-confidence critical gaps. |
| **UI Assessment** | Two parallel canvas implementations: `@ghatana/canvas` (534 files, platform library) and `libs/@yappc/canvas` (37 files, unclear status). `reactflow` v11 AND `@xyflow/react` v12 both declared in `package.json`. MUI + Tailwind + Lucide = 3 simultaneous UI systems with aliases in `vite.config.ts`. |
| **API Assessment** | Canvas state persisted to `CanvasDocument` Prisma model. `canvas.ts` BFF routes handle persistence. `POST /api/canvas` and collaboration endpoints exist. |
| **Backend Assessment** | `RedisCanvasRoomStore.ts` for collab room state. `canvasCollaboration.ts` service. `@ghatana/canvas`'s `CanvasChromeLayout` is imported correctly. |
| **DB Assessment** | `CanvasDocument` model in schema with `userId` relation. Canvas data stored as JSON. No schema validation on stored JSON. |
| **Async/Integration** | Yjs CRDT sync via y-indexeddb (local-first). Server-side sync unclear — `libs/collab` + `libs/canvas/src/collab` + `libs/canvas/src/collaboration` = 3 collaboration implementations. |
| **Governance/Privacy** | No data classification on canvas contents. Architecture diagrams may contain sensitive system topology — no retention controls specific to canvas content. |
| **Production Operability** | Canvas is the most complex feature. No load test evidence for concurrent canvas collaboration sessions. `RedisCanvasRoomStore` requires Redis — no fallback behavior defined. |
| **Test Evidence** | `jest.canvas.config.ts` exists. E2E canvas tests implied. **Gap: no load/concurrent-user tests for collaboration. Two canvas implementations not rationalized.** |
| **Key Gaps** | Control overload (18+ groups); 3 collab implementations; 2 canvas libs; 2 ReactFlow versions; no JSON schema validation on stored canvas data |
| **Severity** | High |
| **Files Involved** | [canvas.tsx](../products/yappc/frontend/web/src/routes/app/project/canvas.tsx), [_canvas/](../products/yappc/frontend/web/src/routes/app/project/_canvas/), [useAIAssistant.ts](../products/yappc/frontend/web/src/hooks/useAIAssistant.ts) |

---

### Workflow 4: AI Code Generation and Scaffolding

| Dimension | Assessment |
|-----------|-----------|
| **Intended Outcome** | User's intent produces production-ready code via AI agents with minimal manual steps |
| **Actual Current Behavior** | `core/scaffold` has significant structure (templates, engine, generators). `core/ai` has LLM integration via langchain4j + Anthropic + OpenAI + Ollama providers. `GenerationApiController` wired in lifecycle service. `AICallPathTest.java` validates the facade. |
| **User Burden** | Unknown — generation UI surface not fully confirmed. Frontend has `useAICommand` and `useAIQuality` hooks but generation UX is not confirmed end-to-end. |
| **AI/ML Role Today** | Real LLM provider wiring exists: `ToolAwareAnthropicCompletionService`, `ToolAwareOpenAICompletionService`, `OllamaCompletionService`. Prompt registry with active-version control and score-driven variant weight rebalancing. |
| **AI/ML Role That Should Exist** | Confidence routing (low-confidence generation surfaces for user review; high-confidence auto-accepts); artifact quality gates before code is presented; cross-artifact consistency checking. |
| **UI Assessment** | No dedicated `/generate` route found in `routes/` listing. Generation may happen via canvas AI suggestions or lifecycle panel. **Gap: no identified primary UI entry point for code generation.** |
| **API Assessment** | `POST /api/v1/yappc/generate` in OpenAPI contract. `GenerationApiController` in Java backend. BFF proxy path to Java unclear. |
| **Backend Assessment** | `core/scaffold/` has templates, engine, and generators as 3 distinct Gradle modules post-consolidation. `:core:agents` module is commented out in `services/build.gradle.kts`. **Critical: agents are disconnected from the primary runtime.** |
| **DB Assessment** | Generated artifacts stored via `YappcArtifactRepository` and DataCloud adapters. Prisma `Page` model holds canvas-generated content. |
| **Async/Integration** | AEP pipeline for generation phase declared in `config/pipelines/lifecycle-management-v1.yaml`. Agent dispatch via `agent-dispatch` platform module — but `:core:agents` registration is incomplete. |
| **Governance/Privacy** | AI safety filters exist in `core/ai/src/main/java/.../safety/`. Content redaction referenced in governed memory. |
| **Production Operability** | LLM provider keys required at startup. `YappcEnvironmentConfigTest` validates config. Fallback behavior (Ollama local) exists. No rate-limiting evidence for LLM calls. |
| **Test Evidence** | `AICallPathTest.java` validates request-to-response routing. **Gap: no integration test confirms user-facing generation produces correct artifact types. Agents module disconnected.** |
| **Key Gaps** | No primary generation UI identified; agents module not wired; no generation E2E test; no LLM rate limiting evidence |
| **Severity** | Critical |
| **Files Involved** | [core/scaffold/](../products/yappc/core/scaffold/), [core/ai/](../products/yappc/core/ai/), [services/build.gradle.kts](../products/yappc/services/build.gradle.kts) |

---

### Workflow 5: Approval Workflow

| Dimension | Assessment |
|-----------|-----------|
| **Intended Outcome** | Human approvals are required for high-risk phase transitions; approvers receive context, can approve/reject with full audit trail |
| **Actual Current Behavior** | `ApprovalStateMachine`, `JdbcHumanApprovalService`, `ApprovalAuditLogger`, `ApprovalRiskScorer`, and `ApprovalNotificationService` exist in `services-lifecycle`. `ApprovalHttpHandlers` provides HTTP endpoints. |
| **User Burden** | Unclear — approvals may appear in the lifecycle route. Decision-support defaults (`approvalMode`, `riskTolerance`) declared in BFF `lifecycle.ts` types. |
| **AI/ML Role Today** | `ApprovalRiskScorer` scores risk for approval decisions. This is a meaningful AI touch point. |
| **AI/ML Role That Should Exist** | Pre-populate approval context with AI summary of what changed, what risk was scored, and what the recommended decision is — human reviews a recommendation, not raw data. |
| **UI Assessment** | `GET /api/v1/approvals/pending` in OpenAPI. No confirmed approval UI in web routes listing. |
| **Backend Assessment** | `JdbcHumanApprovalService` backed by JDBC (Java layer). Persistence appears real. |
| **Governance/Privacy** | `ApprovalAuditLogger` exists. Must confirm all approval state changes are audited. |
| **Production Operability** | Notification service must be wired to real transport (email/Slack/webhook). No confirmed notification transport wiring. |
| **Test Evidence** | No dedicated approval workflow E2E test found. |
| **Key Gaps** | No approval UI confirmed; notification transport not confirmed; no approval E2E test |
| **Severity** | High |
| **Files Involved** | [core/services-lifecycle/](../products/yappc/core/services-lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/) |

---

### Workflow 6: Workspace and Project Management

| Dimension | Assessment |
|-----------|-----------|
| **Intended Outcome** | Users create workspaces, add projects, manage members, and navigate between them with minimal friction |
| **Actual Current Behavior** | `projects.ts` and `workspaces.ts` BFF routes implement full CRUD with RBAC enforcement. Prisma-backed. Audit logging on create/update/delete. `useWorkspaceData` and `useProjectData` hooks in frontend. |
| **User Burden** | Low-Medium — workspace/project creation flows exist. `ProjectSetupSuggestion` AI feature present. |
| **AI/ML Role Today** | Rule-based project setup suggestions based on workspace context. `RelatedProjectRecommendation` returned on project create. |
| **AI/ML Role That Should Exist** | Infer project type from description; pre-populate tech stack from workspace history; detect duplicate projects via LLM similarity. |
| **UI Assessment** | `routes/app/workspaces.tsx` and `routes/app/projects.tsx` exist. Well-structured route hierarchy confirmed. |
| **API Assessment** | Full CRUD at `/api/workspaces` and `/api/projects`. Workspace member management. Contract fully declared. |
| **Backend Assessment** | `requirePermission` enforced on all mutating operations. Audit service called on all state changes. |
| **DB Assessment** | `Workspace`, `Project`, `WorkspaceMember`, `WorkspaceProject` models with proper foreign keys and indexes. `aiSummary` and `aiTags` fields present for future AI enrichment. |
| **Governance/Privacy** | Audit logging present. `tenantId` propagated on API calls. |
| **Production Operability** | Solid — most production-complete workflow. |
| **Test Evidence** | `routes.integration.test.ts` covers workspace/project operations. |
| **Key Gaps** | AI suggestion depth is shallow (rule-based); no pagination tests; no bulk operation support |
| **Severity** | Low |
| **Files Involved** | [projects.ts](../products/yappc/frontend/apps/api/src/routes/projects.ts), [workspaces.ts](../products/yappc/frontend/apps/api/src/routes/workspaces.ts) |

---

## D. UX and Cognitive Load Review

### Information Architecture

**Issues:**
- Lifecycle phase navigation displayed in 3 simultaneous locations: app shell `UnifiedPhaseRail`, project shell `LifecyclePhaseNavigator`, and canvas `LifecyclePhaseIndicator` + `CanvasProgressWidget`. Users cannot identify the authoritative interaction point.
- Dashboard → workspace → project hierarchy is well-structured but route `/app/p/:projectId` branches into 6+ tabs without progressive disclosure.
- UX docs acknowledge these problems ([UX_EXPERT_ANALYSIS.md](../products/yappc/frontend/web/UX_EXPERT_ANALYSIS.md), [UX_IMPROVEMENTS.md](../products/yappc/frontend/web/UX_IMPROVEMENTS.md)) but fixes are proposed rather than confirmed implemented.

**Recommendations:**
- Single lifecycle phase navigation component at the top of the project shell header only.
- Remove lifecycle phase indicators from the canvas entirely — the canvas is for creating, not for tracking progress.

### Canvas Cognitive Overload

**Evidence from `UX_EXPERT_ANALYSIS.md` (self-documented):**
- 18+ visible control groups simultaneously.
- Left task panel (280px) always visible even on canvas, consuming canvas real estate.
- Mode selector at top-left competes with sketch toolbar.
- 4+ separate drawer triggers on the right.
- Floating command palette, floating help button, and AI badges all visible.

**Assessment:** The team has documented this problem accurately. The canvas has accumulated features without a unifying visual hierarchy. The proposed `progressiveDisclosure` approach in `UX_IMPROVEMENTS.md` is the right direction but shows no evidence of implementation.

### Form/Input Minimization

**Positive:** Project creation has `ProjectSetupSuggestion` that can pre-populate setup based on workspace context and description.  
**Gaps:** Intent capture still requires manual free-form entry with no confirmed NLP-driven requirement extraction from natural language. The 8-phase lifecycle implies significant user-supplied artifact creation at each phase — no evidence that AI pre-populates artifact templates.

### Error/Loading/Empty States

**Positive:** `RouteErrorBoundary` and `RouteLoadingSpinner` exist.  
**Gap:** Auth session rehydration failure handling in `AuthProvider.tsx` silently sets `currentUser = null` and logs a console warning — user sees no feedback on session expiry or auth failure.

### Onboarding/Discoverability

**Gap:** Prior version had onboarding redirect disabled with a code comment rather than a feature flag. Current version should confirm this is re-enabled with a proper feature flag guard.

---

## E. AI/ML Pervasive Automation Review

### Where AI/ML Is Correctly First-Class

- `ApprovalRiskScorer` — meaningful AI risk scoring for approvals.
- `PhaseGateValidator` — automated artifact/gate checking before transitions.
- `useAIAssistant` — confidence-scored canvas suggestions with ghost-node previews.
- Prompt registry with active-version control and score-driven variant weight rebalancing.
- `ResilienceAIService` with circuit breaker, fallback, and provider health tracking.
- `useReadinessAnomalies` — AI-detected anomalies in lifecycle readiness.

### Where AI/ML Is Too Shallow

- **Intent Phase**: Intent capture is free-form text. No confirmed NLP-driven requirement extraction producing structured output from unstructured input.
- **Shape Phase**: Architecture modeling is primarily manual (drag-and-drop canvas). AI suggestions are reactive (user-triggered), not proactive (system-initiated).
- **Project Setup**: `ProjectSetupSuggestion` returns rule-based recommendations, not LLM-driven.
- **Workspace AI Summary**: `aiSummary` and `aiTags` fields exist in the schema but enrichment is not confirmed active in code paths reviewed.

### Where AI/ML Should Automate More

- Artifact pre-population at phase entry (generate draft artifact from project context automatically).
- Lifecycle gate pre-check: run gate evaluation proactively and show users their readiness score before they try to advance.
- Code review summarization: when agents produce generated code, surface a quality summary, not raw code.
- Build failure root-cause suggestion: when RUN phase fails, surface agent-generated hypothesis.

### Where AI/ML Is Overexposed

- Canvas suggestions show all suggestion types simultaneously. Users should see only the highest-priority insight.
- `FOW_STAGE_LABELS` and lifecycle phase both visible in the lifecycle view — creates dual-complexity.

### Where Trust/Fallback Is Strong

- `ResilienceAIService` circuit breaker pattern is well-designed.
- LLM provider fallback chain (OpenAI → Anthropic → Ollama) exists.
- Confidence scoring on canvas suggestions (0-1).

### Where Trust/Fallback Is Weak

- No confirmed fallback UI for when AI suggestions are entirely unavailable — does the lifecycle view degrade gracefully?
- `enrichment-worker.service.ts` background enrichment has no confirmed alerting on enrichment lag or backpressure.

---

## F. API / Backend / DB / Integration Review

### API Contract Issues

| Issue | File | Severity |
|-------|------|----------|
| OpenAPI declares `localhost:8080` but service runs on port 8082 | `docs/api/openapi.yaml`, `YappcLifecycleService.java` | Critical |
| `reactflow` v11 + `@xyflow/react` v12 in same `package.json` — incompatible | `frontend/package.json` | High |
| `/api/*` and `/api/v1/*` prefixes both in use — migration-era dual-prefix not resolved | Multiple routes | Medium |
| 21 unregistered agent definitions (215 defined, 194 registered in `registry.yaml`) | `config/agents/` | Medium |
| swagger-parser version drift: 2.1.20 vs 2.1.22 across Gradle modules | `services/build.gradle.kts`, `backend/api/build.gradle.kts` | Medium |

### Backend Issues

| Issue | File | Severity |
|-------|------|----------|
| `:products:yappc:core:agents` not registered in `settings.gradle.kts` | `services/build.gradle.kts:55-56` | Critical |
| Javalin dependency in `platform/build.gradle.kts` — ADR-004 violation | `platform/build.gradle.kts:68` | High |
| Testcontainers in `implementation` scope (not `testImplementation`) | `backend/api/build.gradle.kts:66-67` | Medium |
| Two parallel Java HTTP surfaces: `YappcLifecycleService` and `YappcHttpServer` | `core/services-lifecycle/`, `core/yappc-services/` | High |
| Duplicate agent/runtime trees: `core/agents` and `core/yappc-agents` | `core/agents/`, `core/yappc-agents/` | Medium |
| Insecure defaults: `dev-key`, `default-tenant`, `change-me-in-production` in service config | `core/services-lifecycle/config/`, `core/yappc-services/` | Critical |

### Database/Query Issues

| Issue | File | Severity |
|-------|------|----------|
| `YappcDataCloudRepository` and `ProjectRepository` have identified query correctness issues | `infrastructure/datacloud/` | High |
| No JSON schema validation on stored `CanvasDocument` data (JSONB in Postgres) | `schema.prisma`, canvas routes | Medium |
| Direct `products:data-cloud:*` dependencies in YAPPC core modules bypass the adapter seam | `core/yappc-services/build.gradle.kts` | Medium |

### Orchestration Issues

| Issue | Severity |
|-------|----------|
| AEP event pipeline defined in YAML config but agent execution disconnected (`:core:agents` unregistered) | Critical |
| `DurableEventCloudPublisher` retry path not confirmed working with real AEP integration | High |
| `enrichment-worker.service.ts` background job with no confirmed error monitoring or dead-letter queue | Medium |

---

## G. Governance / Privacy / Security / Visibility Review

### Security Gaps

| Gap | Evidence | Severity |
|-----|----------|----------|
| JWT stored in `localStorage` — vulnerable to XSS | `auth-session.ts` - `localStorage.getItem('auth-session')` | High |
| `devAuth.ts` bypass activated by `NODE_ENV` — env leakage risk in CI | `devAuth.ts`, `dev-auth-config.ts` | High |
| `change-me-in-production` API keys present in service config | `core/services-lifecycle/config/` | Critical |
| `default-tenant` fallback in tenant context filter — tenant isolation can silently degrade | `TenantContextFilter` pattern | High |
| Auth ownership split across Java lifecycle, Node BFF — no single auth authority | Multiple | High |
| `JwtAuthFilter` exists in Java layer but Node auth middleware is independent — same JWT secret not confirmed | `auth.middleware.ts`, Java `JwtAuthFilter` | Critical |

### Privacy Gaps

| Gap | Severity |
|-----|----------|
| Canvas documents may contain sensitive architecture topology — no data classification or retention policy specific to canvas content | High |
| AI-generated content (code, requirements) stored without content classification | Medium |
| `aiSummary` and `aiTags` on workspace may aggregate private project information — access control not verified | Medium |
| `enrichment-worker.service.ts` creates embeddings of project content — no confirmed data boundary between tenants in vector store | High |

### Audit Gaps

| Gap | Severity |
|-----|----------|
| Auth failure events (failed login, token rejection) not confirmed in `AuditService` | High |
| Agent execution audit trail not confirmed end-to-end | Medium |
| Canvas collaboration events (who edited what, when) not audited | Medium |

### Observability

**Positive:**
- Prometheus + Micrometer wired. `prometheus.yappc.yml` exists.
- Grafana dashboards in `monitoring/grafana`.
- `AIMetricsCollector` emits provider latency, fallback counts.
- DAG execution metadata with per-phase timing emitted from `LifecycleApiController`.

**Gaps:**
- No confirmed production alerting on lifecycle phase transition failures.
- No SLO definitions found.
- Node BFF observability (Fastify request metrics, error rates) not confirmed emitted to Prometheus.

---

## H. Production Operability Review

### Deployment/Config Issues

| Issue | Severity |
|-------|----------|
| Port mismatch: service runs on 8082, Helm probes and CI check 8080 | Critical |
| `_shared-build.yml` defaults to Java 17 but project requires Java 21 | Medium |
| `e2e-tests.yml` uses Node 18 and pnpm@8; workspace requires Node 20+ and pnpm@10.28.2 | High |
| No dedicated YAPPC CI workflow — product not continuously verified | Critical |
| `run-dev.sh` and `run-yappc.sh` exist but not confirmed to match Docker Compose startup sequence | Medium |

### Resilience / Failure Handling

| Issue | Severity |
|-------|----------|
| Redis required for canvas collaboration — no Redis fallback strategy defined | High |
| LLM provider circuit breaker exists in Java layer but no confirmed circuit breaker in Node BFF AI calls | Medium |
| `enrichment-worker.service.ts` — no dead-letter queue, no backpressure | Medium |
| DataCloud query correctness issues may cause silent data errors | High |

### Migration/Rollback

| Issue | Severity |
|-------|----------|
| Prisma migrations exist but no confirmed rollback plan for failed migrations | Medium |
| Dual `/api/*` and `/api/v1/*` prefix migration in progress — no completion target visible | Medium |

### Backup/DR

- No backup/restore strategy documented for PostgreSQL (canvas documents, projects, users, sessions).
- No confirmed export/import functionality for project data.

---

## I. Testing and Evidence Gaps

### Missing or Insufficient Tests

| Gap | Risk |
|-----|------|
| No complete INTENT → GENERATE end-to-end test through real services | Cannot confirm the primary value-delivery workflow works |
| No YAPPC-specific CI workflow | Changes may break YAPPC undetected |
| Approval workflow has no dedicated E2E test | Core governance capability unproven |
| Canvas collaboration has no concurrent-user load test | Race conditions in real-world usage unexposed |
| Agent orchestration has no integration test because `:core:agents` is not wired | Entire agent capability is untested in integration |
| `routes.integration.test.ts` uses synthetic JWTs (base64 concatenation, not proper HMAC) | Tests may not catch JWT validation regressions |
| AI safety filter not covered by tests found in review | Unsafe AI output not protected |
| DataCloud query correctness issues not confirmed fixed since prior audit | Data integrity unproven |

### Test Evidence Assessment

The **Release Readiness Checklist** ([RELEASE_READINESS_CHECKLIST.md](../products/yappc/docs/RELEASE_READINESS_CHECKLIST.md)) is well-written and references the right test paths. However, **the checklist cannot be satisfied today** because:
1. No `yappc-ci.yml` workflow exists to produce the `yappc-release-evidence-bundle`.
2. The agent module is unregistered, making agent execution tests unreliable.
3. Port mismatch means startup diagnostics check the wrong endpoint.

---

## J. Prioritized Remediation Plan

### P0 — Must Fix Immediately (Production Blockers)

| # | Issue | Why It Matters | Fix |
|---|-------|---------------|-----|
| P0-1 | Port mismatch (8080 vs 8082) | CI cannot validate startup; release evidence invalid | Align OpenAPI `servers[].url`, Helm probes, CI health checks, and all docs to port 8082 |
| P0-2 | Insecure defaults: `dev-key`, `default-tenant`, `change-me-in-production` | Auth/tenant isolation silently degrades in production | Fail startup if these placeholder values are detected in non-dev environments; validate via `YappcEnvironmentConfigTest` |
| P0-3 | JWT secret alignment between Node BFF and Java service | Credential confusion leads to auth bypass | Document single JWT authority; confirm same secret source and same algorithm |
| P0-4 | `:core:agents` not registered in `settings.gradle.kts` | Agent orchestration — the primary differentiator — cannot execute | Register module; add integration test; mark as critical in CI |
| P0-5 | Create `yappc-ci.yml` dedicated CI workflow | Product is not continuously verified | Add Gradle + frontend build + critical journey E2E run; upload `yappc-release-evidence-bundle` |

### P1 — Required for Production Trust

| # | Issue | Why It Matters | Fix |
|---|-------|---------------|-----|
| P1-1 | JWT in `localStorage` (XSS risk) | Token theft via injected scripts | Migrate to httpOnly secure cookie for access token; keep refresh token server-side only |
| P1-2 | `devAuth.ts` bypass — env leakage risk | Auth bypass in CI/staging if env misconfigured | Gate bypass on `NODE_ENV === 'development'` AND required `ALLOW_DEV_AUTH=true` env var; never activate in test or staging |
| P1-3 | Complete Javalin removal from `platform/build.gradle.kts` | ADR-004 violation | Migrate remaining Javalin routes to `platform:java:http`; remove dependency |
| P1-4 | `e2e-tests.yml` toolchain alignment | CI E2E tests fail silently with wrong Node/pnpm versions | Use `corepack enable && corepack prepare pnpm@10.28.2 --activate`; use `node: '20'` |
| P1-5 | DataCloud query correctness in `YappcDataCloudRepository` and `ProjectRepository` | Silent data corruption | Review and fix query semantics identified in prior audit; add integration tests |
| P1-6 | `Testcontainers` in `implementation` scope | Test infra in production classpath increases attack surface and JAR size | Move to `testImplementation` |
| P1-7 | `default-tenant` fallback in `TenantContextFilter` | Tenant data leakage | Reject requests with no tenant header in non-dev environments — no silent fallback |

### P2 — Simplification and Automation Hardening

| # | Issue | Why It Matters | Fix |
|---|-------|---------------|-----|
| P2-1 | Remove phase navigation redundancy (3 locations) | Cognitive overload; users confused about which is authoritative | Single lifecycle phase bar in project shell header only; remove from canvas and app shell |
| P2-2 | Remove `reactflow` v11 | Incompatible with `@xyflow/react` v12; potential runtime errors | Remove legacy v11 package; confirm all canvas code uses v12 API |
| P2-3 | Rationalize two canvas libraries | Unclear ownership, potential duplication | Audit `libs/@yappc/canvas` usage; delete if it duplicates `@ghatana/canvas` |
| P2-4 | Remove MUI from canvas peer dependencies | 3 UI systems is unsustainable | Complete MUI removal; update `canvas/package.json` peer deps |
| P2-5 | Replace rule-based project setup suggestion with LLM-based | Current AI assistance is too shallow | Wire `ProjectSetupSuggestion` through `AIService` → LLM provider; score confidence |
| P2-6 | Delete deprecated packages `@ghatana/yappc-state` and `@ghatana/yappc-graphql` | Deprecated packages add confusion and unused bundle weight | Remove import references; delete packages |
| P2-7 | Unify dual API prefix `/api/*` and `/api/v1/*` | Contract confusion for consumers | Migrate all routes to `/api/v1/*`; remove `/api/*` shims |

### P3 — Strategic Improvements

| # | Issue | Why It Matters | Fix |
|---|-------|---------------|-----|
| P3-1 | Proactive lifecycle gate pre-check | Users don't know their readiness before attempting transition | Run `PhaseGateValidator` in background every 5 minutes; surface readiness score passively |
| P3-2 | Canvas AI suggestions progressive disclosure | All suggestion types visible at once creates noise | Show only the single highest-priority suggestion; user can expand for more |
| P3-3 | Artifact pre-population at phase entry | Users manually create artifacts that AI could draft | On phase entry, AI generates draft artifacts using project context; user reviews, not creates |
| P3-4 | Dedicated YAPPC operations runbook | Incident diagnosis relies on scattered docs | Single `OPERATIONS.md` covering startup, health verification, common failures, rollback |
| P3-5 | PostgreSQL backup/restore strategy | No defined RPO/RTO | Define PG backup schedule, point-in-time recovery, and test restore procedure |
| P3-6 | Consolidate collaboration implementations | 3 parallel collab codepaths creates maintenance burden | Pick `libs/collab` + Yjs as canonical; remove `libs/canvas/src/collab` and `libs/canvas/src/collaboration` |
| P3-7 | `enrichment-worker` dead-letter queue | Silent enrichment failures degrade AI capabilities | Add DLQ and alerting; surface enrichment health in `/health/readiness` |

---

## K. Simplicity and Automation Blueprint

### Workflows to Simplify

1. **Intent Capture**: Replace free-form text field with a guided 3-question conversation (what problem, who benefits, what success looks like). Derive structured requirements automatically via NLP.
2. **Phase Transition**: Eliminate manual "advance phase" decision. System runs gate pre-check continuously; when gates are clear, present a single notification with one-click confirmation. Human decision only for governance-required approvals.
3. **Canvas Setup**: Replace 18+ visible controls with 3 primary actions: "Draw", "AI Assist", "Zoom". Everything else accessible via Cmd+K or contextual right-click.

### Inputs to Infer/Auto-Populate

- Project type from description text (NLP classification).
- Tech stack from workspace history and similar projects.
- Artifact templates from phase entry context.
- Approval risk score automatically from `ApprovalRiskScorer`.
- Suggested gates from project type and prior workspace patterns.

### AI/ML Interventions to Add

- Intent NLP extraction: structured `RequirementConfig` from free-form input.
- Proactive gate readiness score (background, not user-triggered).
- Code generation confidence display: e.g., "Generated with 94% confidence in production-ready output."
- Build failure root-cause agent on RUN phase failures.
- Operator guidance summarization: "Your project is in GENERATE phase with 2 unresolved artifacts and 1 critical anomaly."

### Visibility/Audit Controls to Strengthen

- Emit structured audit event on every lifecycle phase transition (who, when, gate verdicts, risk score).
- Add Node BFF request metrics to Prometheus (via `fastify-metrics` plugin).
- Add SLO definitions for: lifecycle transition latency, AI suggestion latency, canvas save latency.
- Surface `enrichment-worker` health in `/health/readiness` check.

---

## L. Product System Architecture Corrections

### Runtime Topology Decision Required

YAPPC has three Java HTTP surfaces and one Node BFF competing as the primary backend. **Recommended canonical topology:**

```
Browser → Node Fastify BFF (auth/session/proxy only)
                → Java Lifecycle Service (domain logic, agents, events)  [port 8082]
                         → DataCloud (persistence via adapter seam only)
                         → AEP (event pipeline)
```

`YappcHttpServer` purpose must be clarified — deprecate or merge into `YappcLifecycleService`.

### Dependency Direction Corrections

- `infrastructure/datacloud` adapter must be the ONLY access path to DataCloud — remove direct `products:data-cloud:*` dependencies from `core/yappc-services`.
- `core/agents` and `core/yappc-agents` — rationalize to one canonical agent facade.
- Node BFF (`frontend/apps/api`) must own session/auth layer only; domain logic belongs in Java lifecycle service.

### Contract Normalization

- Single OpenAPI spec at `docs/api/openapi.yaml` — currently the canonical location. Automate validation via `yappc-contract-tests.yml` (referenced in checklist but not confirmed to exist).
- Align all Node BFF route registrations with OpenAPI spec paths.
- Set `servers[].url` in OpenAPI to port 8082.

---

## M. Final Truth Statement

### What Truly Works End-to-End Today

- **Workspace and Project Management**: Prisma-backed CRUD, RBAC enforcement, audit logging — the most production-complete surface.
- **JWT Auth (Production Path)**: `auth.middleware.ts` + `rbac.middleware.ts` + `UserSession` with refresh token rotation is architecturally sound.
- **Phase Gate Validation (Java)**: `PhaseGateValidator` with entry-criteria, artifact-presence, and exit-criteria checks is well-implemented.
- **AI Resilience Layer**: Circuit breaker, provider fallback chain, confidence scoring — well-designed.
- **Canvas Drawing and Persistence**: `@xyflow/react` v12 + `CanvasChromeLayout` + Prisma `CanvasDocument` — functional.

### What Works Only Partially

- **Lifecycle Phase Transition**: Backend gates and approvals exist but agents module is disconnected. AEP event pipeline integration is architecturally wired but not confirmed end-to-end.
- **AI Code Generation**: LLM providers wired in Java; scaffold engine exists; but `:core:agents` unregistered means the generation orchestration chain is broken in the primary runtime.
- **Collaboration**: Yjs CRDT works locally; 3 collab implementations exist; server-side sync unclear; no concurrent-user testing.
- **AI/ML Assistance (Canvas)**: Suggestion types and ghost nodes exist; not confirmed to reach production LLM providers in all paths.

### What Is Misleading or Overstated

- **Release Readiness Checklist** lists critical journey evidence paths (`yappc-ci.yml`, `yappc-contract-tests.yml`) that do not exist or cannot pass due to port mismatch.
- **Agent catalog**: 194 registered agents in YAML, but the agents module is not wired into the services build — the catalog is aspirational, not operational.
- **AI-Native Maturity 3/10** per self-assessment in `README.md` is honest — implicit AI automation is incomplete across the lifecycle.

### What Creates User Burden

1. Three simultaneous phase navigation controls — users cannot tell which is authoritative.
2. 18+ canvas controls visible at once — overwhelming for new and experienced users alike.
3. Sidebar always visible (224px), consuming canvas real estate even when canvas needs full width.
4. Manual intent capture — no NLP assistance to reduce form-filling burden.
5. Manual artifact creation at each lifecycle phase — no AI pre-population.

### What Must Change for YAPPC to Become Production-Grade

1. **Establish a single runtime authority** — canonical backend at `YappcLifecycleService` on port 8082; Node BFF as pure proxy/session layer.
2. **Fix the port, fix the CI** — align all health probes, OpenAPI, Helm, and CI to port 8082; add `yappc-ci.yml`.
3. **Wire the agents** — register `:core:agents` in `settings.gradle.kts`; add integration test.
4. **Harden auth** — move JWT to httpOnly cookie; confirm single JWT authority across Node and Java; remove insecure defaults.
5. **Reduce canvas cognitive load** — implement progressive disclosure; remove phase navigation redundancy.
6. **Deepen AI automation** — NLP intent extraction; proactive gate pre-check; artifact pre-population.
7. **Fix E2E CI toolchain** — Node 20+, pnpm@10.28.2, Playwright with correct versions.
8. **Resolve DataCloud query correctness** — fix identified query issues in `YappcDataCloudRepository`.
9. **Add production SLOs and backup strategy** — define, instrument, and enforce.
10. **Rationalize library sprawl** — remove deprecated packages, consolidate canvas/collab implementations, complete MUI removal.

---

*Audit completed: 2026-04-19*  
*Evidence base: `products/yappc/` (6,913+ items), `platform/java/` (25 modules), `platform/typescript/` (10 packages), `shared-services/`, `products/aep/`, `products/data-cloud/`*  
*Prior audits reviewed: [YAPPC_PRODUCTION_GRADE_AUDIT_2026-04-13.md](../products/yappc/docs/audits/YAPPC_PRODUCTION_GRADE_AUDIT_2026-04-13.md), [YAPPC_ECOSYSTEM_AUDIT.md](../products/yappc/docs/YAPPC_ECOSYSTEM_AUDIT.md)*
