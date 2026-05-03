# YAPPC End-to-End Product Correctness Audit

**Audit Date:** 2026-05-02  
**Auditor:** Principal Product Engineer / Staff Architect (AI-assisted deep audit)  
**Scope Root:** `products/yappc`  
**Audit Doctrine:** Evidence-based, zero-tolerance for production stubs in critical flows

---

## 1. Executive Summary

| Rating Dimension | Score | Notes |
|---|---|---|
| Overall Correctness | 5/10 | Core auth, RBAC, workspace, project, canvas flows are correct. AI Agent GraphQL resolvers and lifecycle advanced features are stubs. |
| Overall Completeness | 4/10 | Phases 0–2 have partial coverage. Phases 3–7 are in active implementation. Many domain model entities are duplicated with no converged canonical. |
| Production Readiness | 2/10 | Consistent with self-assessment in README (2/10). Multiple P0 stubs in critical AI flows, workflow cancellation durability gaps, console logging in production. |
| Mock/Stub Risk | **Critical** | `AIAgentsResolver` uses stub clients verbatim from `src/stubs/ai/agents/api-client.ts`. All four AI agent mutations return "Stub response" to users in production. |

### Top P0 Issues

1. `AIAgentsResolver.ts` — All AI agent GraphQL mutations and the `aiAgents()` query return hardcoded stub data in every environment. `AIAgentClientFactory` from `src/stubs/ai/agents/api-client.ts` is imported and used in production resolvers.
2. `AIAgentsResolver.Query.aiAgents()` — Returns a hardcoded static array of four agents with comment "In production, this would call the Java backend's registry." This is production-reachable fake behavior with no feature-flag guard.
3. `GraphQL context (index.ts)` — GraphQL auth only checks for `authorization` header presence in production; it does not verify token validity at the yoga context level. Authentication for GraphQL relies entirely on `authMiddleware` ordering correctness (correct but fragile).
4. Legacy canvas endpoint `GET /canvas/:projectId/:canvasId?` — No resource-level ownership check (no `preHandler`). Global auth ensures request is authenticated, but any authenticated user can read any project's canvas data by guessing a project ID.
5. Domain model duplication — `Project`, `Incident`, `Compliance`, `SecurityScan`, `Alert` exist in both `backend/api/domain/` and `libs/java/yappc-domain/` with no completed migration. The deprecated API domain versions remain in active use.

### Top P1 Issues

1. `console.error` / `console.log` used throughout production routes (`canvas.ts`, `workspaces.ts`, `lifecycle.ts`) instead of structured Fastify logger (`request.log`).
2. `devAuthBypass` creates a new user in production database if no admin user exists — risky behavior if `ENABLE_DEV_AUTH_BYPASS` is misconfigured (though guarded by `assertDevAuthBypassAllowed`).
3. `embedding-pipeline.ts` falls back to a stub provider (zero vectors) if no `OPENAI_API_KEY` or `OLLAMA_BASE_URL` is set. Zero vectors silently corrupt semantic search results.
4. Backend Implementation Backlog (self-documented): workflow cancel durability, cost-per-project API, conversation retention policy, PII classification on logs — all unfixed.
5. Multiple seed files (`seed.ts`, `seed-basic.ts`, `seed-minimal.ts`, `seed-simple.ts`, `seed-workflows.ts`) with no documented canonical production seed behavior.

### Production Readiness Gate

**Safe to release to production: NO**  
**Safe for internal demo (with feature flags): CONDITIONAL** — AI agent features must be feature-flagged off.  
**Safe for development use: YES**

---

## 2. Scope and Method

### Reviewed

| Area | Paths |
|---|---|
| Backend API (Node.js/TypeScript) | `frontend/apps/api/src/` |
| Frontend (React/TypeScript) | `frontend/web/src/` |
| Database schema and migrations | `frontend/apps/api/prisma/` |
| Stub directory | `frontend/apps/api/src/stubs/` |
| Middleware | `frontend/apps/api/src/middleware/` |
| Services | `frontend/apps/api/src/services/` |
| GraphQL resolvers | `frontend/apps/api/src/graphql/resolvers/` |
| Observability utils | `frontend/apps/api/src/utils/` |
| Product docs | `docs/` including README, RELEASE_READINESS_CHECKLIST, BACKEND_IMPLEMENTATION_BACKLOG, CRITICAL_JOURNEYS, DOMAIN_MODEL_REGISTRY |
| Feature flags | `frontend/web/FEATURE_FLAGS.md` |
| Route tests | `frontend/apps/api/src/__tests__/`, `frontend/apps/api/src/routes/__tests__/` |

### Excluded

- `node_modules/`, `dist/`, `build/`, `.next/`, `coverage/`
- Java core modules (referenced for context only; Java unit tests reviewed at surface level)
- `e2e/`, `k6-tests/` (considered as evidence, not directly re-executed)

---

## 3. Complete Product Inventory

### 3.1 UI Inventory

| UI Item | File(s) | Purpose | User Actions | Completeness Status | Issues |
|---|---|---|---|---|---|
| Login Page | `frontend/web/src/routes/login.tsx` | Authenticate user | Submit credentials, Demo login | Complete | Demo login only available in dev (`isDemoLoginEnabled()` is `DEV && VITE_ENABLE_DEMO_LOGIN=true`) — correct. |
| Workspaces Page | `frontend/web/src/pages/dashboard/` | List/manage workspaces | Create, view, delete workspace | Partial | Create/view exist; delete UX not confirmed |
| Dashboard | `frontend/web/src/pages/dashboard/DashboardPage.tsx` | Project overview | Navigate to phases | Partial | Phase 3-7 views incomplete |
| Phase Overview | `frontend/web/src/pages/dashboard/PhaseOverviewPage.tsx` | Show lifecycle phase details | Transition phase, view artifacts | Partial | Advanced lifecycle gating not fully wired |
| Projects Page | `frontend/web/src/pages/dashboard/ProjectsPage.tsx` | List/manage projects | Create, filter, open project | Partial | Activity feed partially wired |
| Canvas | `frontend/web/src/canvas/` | Visual workspace | Draw, connect nodes, save | Mostly complete | Real-time collaboration via WebSocket; versioning exists |
| Auth Profile | `frontend/web/src/pages/auth/ProfilePage.tsx` | User profile | Edit profile | Partial | Profile update wired to backend |
| SSO Callback | `frontend/web/src/pages/auth/SSOCallbackPage.tsx` | Handle SSO login redirect | — | Present | SSO actual provider integration not confirmed |
| Admin pages | `frontend/web/src/pages/admin/` | Admin management | Manage users/roles | Incomplete | Admin surface is sparse |
| Security pages | `frontend/web/src/pages/security/` | Security dashboard | View scan results | Partial | Relies on DevSecOps routes |
| Operations pages | `frontend/web/src/pages/operations/` | Ops monitoring | View metrics | Partial | Prometheus integration exists server-side |
| Settings | `frontend/web/src/routes/settings.tsx` | Application settings | Update preferences | Partial | Feature flag settings not UI-exposed |
| Onboarding | `frontend/web/src/routes/onboarding.tsx` | New user onboarding | Complete setup steps | Partial | Bootstrapping session model exists but journey completeness unknown |
| Not Found | `frontend/web/src/routes/not-found.tsx` | 404 state | — | Complete | — |
| Error Boundary | `frontend/web/src/components/route/ErrorBoundary.tsx` | Error display | Retry | Present | — |

### 3.2 User Action Inventory

| Action | UI Source | Expected Result | Actual Handler | Backend/API Called | Complete? | Issues |
|---|---|---|---|---|---|---|
| Login | `routes/login.tsx` | JWT session, redirect | `authService.login()` → `ProxyAuthService` → Java lifecycle service | `POST /auth/login` | Yes | Cookie plugin registration falls back silently |
| Logout | `services/auth/AuthService.ts` | Session cleared | `authService.logout()` | `POST /auth/logout` | Yes | — |
| Create Workspace | `routes/workspaces.ts` | Workspace created + member record | `POST /workspaces` → Prisma | DB write | Yes | — |
| Create Project | `routes/projects.ts` | Project created | `POST /projects` → Prisma | DB write + audit | Yes | — |
| Transition Lifecycle Phase | `routes/lifecycle.ts` | Phase advances with gate check | `PUT /lifecycle/:projectId/phase` | DB write + audit + AI recommendation | Partial | AI persona recommendation falls back silently on error |
| Execute AI Agent (GraphQL) | GraphQL | Agent executes, returns result | `AIAgentsResolver.Mutation.*` | **Stub — returns "Stub response"** | **NO — P0** | Stub client in production |
| Save Canvas | `routes/canvas.ts` | Canvas persisted with version | `PUT /projects/:projectId/canvas` | DB write + version record | Yes | console.error instead of structured log |
| Restore Canvas Version | `routes/canvas.ts` | Canvas reverted to version | `POST /canvas/restore/:projectId` | DB write | Yes | — |
| View Project Activity | `routes/projects.ts` | Activity events from lifecycle + audit | `GET /projects/:projectId/activity` | DB read + lifecycle service | Partial | Lifecycle service call may fail silently |
| Request Approval | GraphQL `requirements-approvals.resolver.ts` | Approval request created | Resolver → Prisma | DB write | Yes | — |
| Cancel Workflow | `services/` | Workflow cancelled durably | `WorkflowService.cancelWorkflow()` | Flag write to DB **only** | **NO — P1** | AEP not notified; durability gap (BACKEND_IMPLEMENTATION_BACKLOG item C-Y2) |
| Demo Login | `routes/login.tsx` | Dev user session | `authService.demoLogin()` | Dev bypass | Dev-only gated correctly | Not reachable in production |

### 3.3 API and Backend Inventory

| Backend Item | File(s) | Expected Behavior | Auth/AuthZ | Validation | DB Access | Complete? | Issues |
|---|---|---|---|---|---|---|---|
| `POST /auth/login` | `routes/auth.ts` | Proxy to Java lifecycle service | Public | Schema-validated body | Via Java lifecycle | Yes | Cookie plugin fallback logs warning |
| `GET /workspaces` | `routes/workspaces.ts` | List user's workspaces | JWT required | None | Prisma + tenant scoping | Yes | `console.error` for logging |
| `POST /workspaces` | `routes/workspaces.ts` | Create workspace + default project | JWT required + `workspace:create` RBAC | Schema | Prisma transaction | Yes | — |
| `GET /projects` | `routes/projects.ts` | List projects in workspace | JWT required + `requireProjectReadable` | Query params | Prisma | Yes | — |
| `POST /projects` | `routes/projects.ts` | Create project + canvas + default lifecycle | JWT + `project:create` | Schema | Prisma | Yes | — |
| `GET /projects/:id/activity` | `routes/projects.ts` | Combined audit + lifecycle events | JWT + `requireProjectReadable` | Params | Prisma + lifecycle proxy | Partial | Lifecycle proxy failure silently returns empty |
| `GET /projects/:id/canvas` | `routes/canvas.ts` | Load canvas data | JWT + `requireCanvasReadable` | Params | Prisma | Yes | — |
| `PUT /projects/:id/canvas` | `routes/canvas.ts` | Save canvas + version | JWT + RBAC + `requireCanvasWritable` | Body type | Prisma | Yes | — |
| `GET /canvas/:projectId/:canvasId?` (legacy) | `routes/canvas.ts` | Load canvas by ID | JWT (global) | Params | Prisma | Partial | **No resource-auth check — any authenticated user can read any canvas by project ID** |
| `PUT /lifecycle/:projectId/phase` | `routes/lifecycle.ts` | Advance lifecycle phase with gate | JWT + RBAC | Params + body | Prisma + audit | Partial | AI persona recommendation error falls back silently with `console.error` |
| `Query aiAgents` (GraphQL) | `graphql/resolvers/AIAgentsResolver.ts` | Return registered agents from Java registry | JWT (via yoga context) | None | None | **NO — P0** | Hardcoded static list, no registry call |
| `Mutation executeCopilotAgent` (GraphQL) | `graphql/resolvers/AIAgentsResolver.ts` | Execute real AI inference | JWT | Type-safe input | `agentExecution` log | **NO — P0** | Stub client returns "Stub response" always |
| `Mutation executeQueryParserAgent` (GraphQL) | `graphql/resolvers/AIAgentsResolver.ts` | Parse natural language query | JWT | Type-safe input | `agentExecution` log | **NO — P0** | Stub client returns fake intent |
| `Mutation executePredictionAgent` (GraphQL) | `graphql/resolvers/AIAgentsResolver.ts` | Run ML prediction | JWT | Type-safe input | `agentExecution` log | **NO — P0** | Stub client returns `predictions: []` always |
| `Mutation executeAnomalyDetector` (GraphQL) | `graphql/resolvers/AIAgentsResolver.ts` | Detect anomalies | JWT | Type-safe input | `agentExecution` log | **NO — P0** | Stub client returns `anomalies: []` always |
| `POST /ai/suggest-artifacts` | `routes/ai.ts` | Rule-based heuristic artifact suggestions | JWT | Typed body | None | Yes | Explicitly labeled as `rule_based_heuristic` — acceptable |
| `GET /security-scans` | `routes/security-scans.ts` | DevSecOps scan results | JWT | Query | Prisma | Partial | Unknown if scans are triggered by real CI |
| `GET /metrics` | `index.ts` | Prometheus metrics | JWT or internal IP or API key | — | None | Yes | — |
| `GET /health` | `index.ts` | DB + Java backend reachability | Public | — | DB ping | Yes | — |
| `GET /ready` | `index.ts` | Strict readiness probe | Public | — | DB ping + Java ping | Yes | — |
| Embedding Pipeline Job | `jobs/embedding-pipeline.ts` | Generate embeddings for semantic search | N/A (background) | — | Prisma | Partial | Falls back to zero-vector stub if no `OPENAI_API_KEY` / `OLLAMA_BASE_URL` — silently corrupts semantic search |

### 3.4 Database Inventory

| DB Model | File(s) | Purpose | Constraints | Indexes | Complete? | Issues |
|---|---|---|---|---|---|---|
| `User` | `prisma/schema.prisma` | User identity | `email @unique` | `email` | Yes | `passwordHash` nullable — supports SSO users |
| `UserSession` | `prisma/schema.prisma` | JWT refresh token session | `sessionToken @unique` | `userId`, `sessionToken`, `userId+revokedAt` | Yes | — |
| `Workspace` | `prisma/schema.prisma` | Workspace container | `ownerId` required | `ownerId` | Yes | — |
| `WorkspaceMember` | `prisma/schema.prisma` | Membership | `@@unique([userId, workspaceId])` | `workspaceId` | Yes | — |
| `Project` | `prisma/schema.prisma` | Project container | `ownerWorkspaceId` FK | — | Yes | Duplicate concept in Java domain (`libs/java/yappc-domain/`) |
| `CanvasDocument` | `prisma/schema.prisma` | Canvas data | `projectId` FK | — | Yes | Versioning via `CanvasVersion` |
| `LifecycleHub` | `prisma/schema.prisma` | Project lifecycle state | `projectId @unique` | — | Yes | Phase transition history tracked |
| `Requirement` | `prisma/schema.prisma` | Project requirements | `projectId` FK | — | Yes | — |
| `ApprovalRequest` | `prisma/schema.prisma` | Approval workflow | `projectId`, `requesterId` FKs | — | Yes | — |
| `AgentRun` | `prisma/schema.prisma` | Agent execution record | `projectId` FK | — | Yes | — |
| `AgentExecution` | `prisma/schema.prisma` | AI agent invocation log | — | — | Yes | Used by `AIAgentsResolver` for audit even when stub returns fake data |
| `AuditLogEntry` | `prisma/schema.prisma` | HTTP-level audit log | — | — | Yes | Sensitive fields redacted |
| Migrations | `prisma/migrations/` | Schema evolution | 6 migrations tracked | — | Yes | `migration_lock.toml` present |
| Seeds | `prisma/seed*.ts` | Test/dev data | — | — | **Partial** | 5 seed files with no canonical production seed documented |

### 3.5 Test Inventory

| Test | File | Type | Feature Covered | Real or Mocked | Valid? | Gaps |
|---|---|---|---|---|---|---|
| Routes integration | `__tests__/routes.integration.test.ts` | Integration (Prisma mocked) | Workspace, project, canvas CRUD | Prisma mocked via fixture | Yes — invokes real route handlers | Tenant isolation assertions present |
| Auth flow E2E | `__tests__/auth-flow.api-e2e.test.ts` | API E2E | Login, token refresh, logout | Calls real server instance | Yes | — |
| Security session ownership | `__tests__/security-session-ownership.test.ts` | Security | JWT ownership across users | Real route + mocked Prisma | Yes | Good cross-user isolation tests |
| API versioning | `__tests__/api-versioning.test.ts` | Integration | Versioned API headers | Real server | Yes | — |
| Lifecycle gates | `__tests__/lifecycle-gates.integration.test.ts` | Integration | Lifecycle phase gating | Real handlers | Yes | — |
| Phase preview routes | `__tests__/lifecycle-phase-preview-routes.test.ts` | Unit | Phase preview API | Real handlers | Yes | — |
| AI routes | `__tests__/ai-routes.test.ts` | Integration | AI suggestion endpoints | Real handlers | Partial | Tests `rule_based_heuristic` routes only — stub GraphQL AI agents not tested for correctness |
| OpenAPI contract | `__tests__/openapi-contract.test.ts` | Contract | OpenAPI schema compliance | Real server | Yes | — |
| Migration prefix routes | `__tests__/migration-prefix-routes.test.ts` | Regression | `/api` → `/api/v1` deprecation | Real server | Yes | — |
| Phase 2b critical routes | `__tests__/phase2b-critical-routes.integration.test.ts` | Integration | Phase 2b endpoints | Real handlers | Yes | — |
| Requirements approvals | `graphql/resolvers/__tests__/requirements-approvals.resolver.test.ts` | Unit | Approval resolver logic | vi.stubEnv for OPENAI_API_KEY | Yes | Properly stubs env, not production code |
| DevSecOps routes | `routes/__tests__/devsecops.test.ts` | Unit | DevSecOps API | Real handler | Yes | — |
| Projects activity | `routes/__tests__/projects.activity.test.ts` | Unit | Activity endpoint | Real handler | Yes | — |
| Projects audit | `routes/__tests__/projects.audit.test.ts` | Unit | Audit log behavior | Real handler | Yes | — |
| Workspace audit | `routes/__tests__/workspaces.audit.test.ts` | Unit | Workspace audit | Real handler | Yes | — |
| **MISSING** | — | Integration | `AIAgentsResolver` mutations against stub | — | — | **No test verifies that stub returns are unacceptable** |
| **MISSING** | — | Integration | Legacy canvas endpoint cross-user access | — | — | **No test verifies resource-auth gap on `/canvas/:projectId/:canvasId?`** |
| **MISSING** | — | Integration | Embedding pipeline with zero-vector fallback detection | — | — | **Silent data corruption path untested** |

---

## 4. Product Behavior Map

| Capability | Expected UX | Expected Backend Behavior | Expected Data Behavior | Status |
|---|---|---|---|---|
| Authenticated workspace management | User creates/views/manages workspaces; RBAC enforced | JWT auth → workspace CRUD with membership enforcement | Workspace + member rows; cascade delete | Mostly complete |
| Project lifecycle orchestration | 8-phase lifecycle (Intent→Evolve) with gating, AI suggestions, evidence artifacts | Phase transition with gate evaluation, audit trail | `LifecycleHub` phase state, `AuditLogEntry` | Phases 0–2 partial, 3–7 in progress |
| Visual canvas | Miro-like canvas with nodes, edges, real-time collab | Canvas CRUD + WebSocket broadcast + versioning | `CanvasDocument` + `CanvasVersion` | Mostly complete |
| AI agent execution | User submits task to copilot/query-parser/prediction/anomaly agents; real AI inference returns | GraphQL mutation → Java AI backend via `AIAgentClientFactory` | `AgentExecution` log | **Stub — P0** |
| AI artifact suggestions | Rule-based suggestions per lifecycle phase | Deterministic heuristic scoring; explicit confidence labels | No DB write | Complete — correctly labeled |
| Semantic search / embeddings | Natural language search over codebase/artifacts | Embedding pipeline background job via OpenAI or Ollama | Vector embeddings stored in DB | Partial — zero-vector fallback corrupts data |
| Security scanning | DevSecOps scan results and vulnerability tracking | Scan triggers from CI, results stored | `SecurityScan` / scan records | Partial |
| Requirements and approvals | Create requirements, request approvals, LLM-enriched analysis | Prisma CRUD + optional LLM call for enrichment | `Requirement` + `ApprovalRequest` | Yes — LLM gated by env var |
| Real-time collaboration | Collaborative canvas editing | WebSocket rooms via `RealTimeService` + Redis store | Event broadcast | Present |
| Observability | Prometheus metrics, OTLP tracing, audit logs | All HTTP requests instrumented; traces exported to Jaeger | Prometheus registry + audit DB table | Mostly complete |
| Auth | Login, refresh, logout | Proxy to Java lifecycle service (single auth authority) | `UserSession` table for refresh token rotation | Complete |

---

## 5. Requirement-to-Implementation Traceability Matrix

| Requirement / Capability | UI | API/Backend | Service/Domain | DB | Tests | Status |
|---|---|---|---|---|---|---|
| Authentication (Login/Logout/Refresh) | `routes/login.tsx` | `routes/auth.ts` | `proxy-auth.service.ts` → Java | `UserSession` | `auth-flow.api-e2e.test.ts` | **Complete and correct** |
| Workspace CRUD | Dashboard pages | `routes/workspaces.ts` | Inline service logic | `Workspace`, `WorkspaceMember` | `routes.integration.test.ts` | **Complete** |
| Project CRUD | Projects page | `routes/projects.ts` | Inline | `Project`, `LifecycleHub` | `routes.integration.test.ts` | **Complete** |
| Canvas save/restore/version | Canvas UI | `routes/canvas.ts` | — | `CanvasDocument`, `CanvasVersion` | Integration tests present | **Mostly complete** — legacy endpoint missing resource-auth |
| Lifecycle phase transition | Phase Overview | `routes/lifecycle.ts` | `lifecycle-taxonomy` domain | `LifecycleHub` | `lifecycle-gates.integration.test.ts` | **Partially implemented** (phases 3-7) |
| AI Agent execution | — | `graphql/resolvers/AIAgentsResolver.ts` | `stubs/ai/agents/api-client.ts` | `AgentExecution` | **No test for stub correctness** | **STUB — P0** |
| AI artifact suggestions | Phase UI | `routes/ai.ts` | Rule-based heuristics | None | `ai-routes.test.ts` | **Complete and correctly labeled** |
| Semantic search embeddings | — | `jobs/embedding-pipeline.ts` | Configurable provider (OpenAI/Ollama/stub) | Vector fields | **No test for zero-vector corruption** | **Partial — P1** |
| Requirements / Approvals | Phase UI | `graphql/resolvers/requirements-approvals.resolver.ts` | LLM enrichment (conditional) | `Requirement`, `ApprovalRequest` | `requirements-approvals.resolver.test.ts` | **Mostly complete** |
| DevSecOps / Security scans | Security pages | `routes/security-scans.ts`, `routes/devsecops.ts` | — | Scan models | `devsecops.test.ts` | **Partial** |
| Workflow cancellation (durable) | — | `core/workflow/` (Java) | `WorkflowService.cancelWorkflow()` | `workflow_events` (planned) | **No integration test** | **Missing — P1** |
| Cost tracking per project | — | **Not implemented** | `CostTrackingService` (planned) | — | — | **Missing — P1** |
| Conversation retention policy | — | **Not implemented** | `ConversationRetentionJob` (planned) | — | — | **Missing — P1** |
| PII classification on logs | — | **Not implemented** | — | — | — | **Missing — P1** |
| RBAC enforcement | All protected routes | `rbac.middleware.ts`, `resource-auth.middleware.ts` | `permissions.ts` | — | `security-session-ownership.test.ts` | **Complete** |
| Observability (metrics/tracing/audit) | — | `utils/metrics.ts`, `utils/tracing.ts`, `middleware/audit.middleware.ts` | — | `AuditLogEntry` | — | **Mostly complete** |
| Multi-tenant isolation | All routes | `tenant-scoping.middleware.ts` | `workspaceMember` scoping | — | `routes.integration.test.ts` | **Partial** — `tenantId` optional in JWT |

---

## 6. End-to-End Journey Audit

| Journey | Entry Point | Expected Outcome | Actual Behavior | Correct? | Mock/Stub Risk | Gaps | Severity |
|---|---|---|---|---|---|---|---|
| Login → Workspace | `/login` | Auth → workspace list | Works via Java proxy auth | Yes | None | Cookie plugin fallback silent | Low |
| Create Workspace → Add Project | Workspace dashboard | Workspace + default project | Fully wired with Prisma | Yes | None | — | — |
| Open Project → View Lifecycle Phase | Project view | Phase overview with AI suggestions | Rule-based suggestions work; phase details complete for phases 0-2 | Partial | None (AI suggestions are real heuristics) | Phases 3-7 incomplete | P1 |
| Execute AI Agent (Copilot) | GraphQL playground / UI | AI responds with inference result | Returns "Stub response" always | **NO** | **Critical** | Stub in production path | **P0** |
| Canvas edit → Save → Version restore | Canvas UI | Canvas saved, version created, restore works | All three paths wired to real DB | Yes | None | Legacy endpoint missing resource-auth | P1 |
| Approve Requirement | Phase UI | Approval request created, LLM enrichment applied | LLM call conditional on `OPENAI_API_KEY` | Yes | None if no key is set — enrichment simply skipped with error logged | — | P2 |
| Lifecycle Phase Transition (gate check) | Phase Overview | Phase advances if gates pass | Gate check from lifecycle taxonomy; audit trail written | Partial | None | Phases 3-7 gates partially defined | P1 |
| View Metrics Dashboard | `/metrics` endpoint | Prometheus metrics | Protected by JWT / IP / API key | Yes | None | — | — |
| First-time user (onboarding) | `/onboarding` | Guided setup creates workspace + project | Onboarding route present; bootstrapping session model exists | Partial | None | Full onboarding journey not end-to-end verified | P2 |
| Permission denied | Any protected route without token | 401 response | `authMiddleware` returns 401 | Yes | None | — | — |
| Admin governance | `/admin/*` | Admin management UI | Admin pages exist; backend routes have RBAC | Partial | None | Admin surface sparse | P2 |

---

## 7. UI/UX Completeness Audit

| UI Area | File(s) | Finding | Severity | Required Fix |
|---|---|---|---|---|
| Demo login button visibility | `routes/login.tsx` | Demo login only shown when `DEV && VITE_ENABLE_DEMO_LOGIN=true`. Correctly hidden in production. | None | — |
| Loading/empty states | Various pages | Loading states present via TanStack Query `isLoading`. Empty states partially implemented for projects list. | P2 | Audit all pages for consistent empty state components |
| Error states | `components/route/ErrorBoundary.tsx` | Route-level error boundary exists. Form-level errors returned from client actions. | P2 | Verify all API error shapes surface meaningfully to users |
| AI agent UI | Not confirmed in frontend | If UI exists for AI agent execution, it displays stub results to users. | **P0** | AI agent UI must be feature-flagged off or show "coming soon" correctly until real backend wired |
| Phase 3-7 UI completeness | `pages/operations/`, `pages/development/` | Phase 3-7 capabilities incomplete per README. UI may expose incomplete features. | P1 | Audit each phase's UI against what is feature-flagged |
| Canvas legacy endpoint accessibility | — | Legacy canvas endpoint exposed without resource-auth guard | P1 | Add `preHandler: requireCanvasReadable()` to legacy canvas GET route |
| Responsive design | All pages | Not audited — no browser test run. | Unknown | Run Lighthouse or Playwright visual audit |

---

## 8. Frontend Action, State, and Data Flow Audit

| Action/State Flow | File(s) | Expected | Actual | Production Mock/Stub? | Required Fix |
|---|---|---|---|---|---|
| Login form submit | `routes/login.tsx` → `AuthService.login()` | POST to `/api/v1/auth/login` | Correct — uses `fetch` via `ProxyAuthService` chain | No | — |
| Session storage | `services/auth/AuthService.ts` | Tokens stored securely | `sessionStorage` / `localStorage` used for session — **not httpOnly cookies from frontend** | No | Use httpOnly cookie from server response; avoid localStorage for tokens |
| MSW in production | `mocks/browser.ts` | MSW disabled in production | Correctly gated by `VITE_ENABLE_MSW=true` | No (correctly gated) | — |
| Feature flags (GrowthBook) | `providers/FeatureFlagProvider.tsx` | GrowthBook SDK fetches flags from CDN | Falls back to defaults if `VITE_GROWTHBOOK_CLIENT_KEY` not set | No | Document required env vars for each deployment environment |
| State management | `state/` (Jotai atoms), TanStack Query | Canonical atoms for shared state | Pattern present and consistent with `@ghatana/state` pattern | No | — |
| AI agent GraphQL call | Frontend GraphQL client | Mutation returns real AI inference | Returns stub data — but the stub surface is server-side, not frontend | **Yes (server-side stub)** | Replace stub clients in `AIAgentsResolver` with real Java backend integration |

---

## 9. Backend/API/Domain Logic Audit

| Backend Flow | File(s) | Expected Behavior | Actual Behavior | Correct? | Mock/Stub? | Security Risk | Required Fix |
|---|---|---|---|---|---|---|---|
| Auth proxy | `services/auth/proxy-auth.service.ts` | All auth ops delegated to Java lifecycle service | `login`, `validateAccessToken`, `refreshTokens`, `logout`, `register` all proxy correctly | Yes | No | HTTP error bodies not parsed (only status code) | Add response body parsing for error shape on auth failures |
| JWT validation | `middleware/auth.middleware.ts` | Verify token on every non-public request | Global `onRequest` hook; `verifyToken` via `jsonwebtoken` | Yes | No | `tenantId` and `workspaceId` optional in payload — downstream may receive undefined | Enforce `tenantId` in JWT where multi-tenancy is critical |
| RBAC enforcement | `middleware/rbac.middleware.ts` | Role → permission check before handler | `isAllowed()` matrix from `services/auth/permissions.ts` | Yes | No | None identified | — |
| Resource auth (project/canvas) | `middleware/resource-auth.middleware.ts` | DB membership check | `checkWorkspaceMembership`, `checkProjectAccess` via Prisma | Yes | No | — | Apply to legacy canvas endpoint |
| Legacy canvas GET | `routes/canvas.ts` lines 130-155 | Read canvas with ownership check | Reads any canvas by project ID for any authenticated user | **No** | No | Cross-project data leak | Add `requireCanvasReadable()` preHandler |
| AI Agent copilot mutation | `graphql/resolvers/AIAgentsResolver.ts` | Execute real AI model via Java | `copilotClient.processMessage()` returns `{ message: 'Stub response', ... }` | **No** | **Yes — P0** | Users receive fake AI responses silently | Implement real `AIAgentClientFactory` backed by Java HTTP client |
| AI Agent registry query | `graphql/resolvers/AIAgentsResolver.ts` | Return live agent registry | Hardcoded array of 4 agents with no Java registry call | **No** | **Yes — P0** | None | Call Java agent registry endpoint |
| Embedding pipeline | `jobs/embedding-pipeline.ts` | Generate real semantic embeddings | Falls back to zero-vector stub when no LLM provider configured | Partial | **Yes (fallback)** | Corrupts semantic search | Log a prominent WARNING; do not insert zero-vector rows — skip instead |
| Audit logging middleware | `middleware/audit.middleware.ts` | Fire-and-forget audit log per request | Correctly uses `onResponse` hook; sensitive fields redacted | Yes | No | None | — |
| Tenant scoping middleware | `middleware/tenant-scoping.middleware.ts` | Scope all queries by user's workspaces | Queries `workspaceMember` and sets `request.tenantContext` | Yes | No | Not applied globally to all routes | Document which routes require `tenantScopingMiddleware` preHandler explicitly |
| Dev auth bypass | `middleware/devAuth.ts` | Dev-only auth bypass | `assertDevAuthBypassAllowed()` prevents production use; `ENABLE_DEV_AUTH_BYPASS=true` + `NODE_ENV=development` required | Yes | Dev-only (correctly gated) | Would create DB admin user in non-dev if misconfigured | — |
| Correlation ID | `index.ts` (onRequest hook) | X-Correlation-ID propagated; required in production | Mandatory in production; generated in development | Yes | No | — | — |

---

## 10. Database and Persistence Audit

| DB Operation/Model | File(s) | Expected Data Rule | Actual Behavior | Correct? | Integrity Risk | Required Fix |
|---|---|---|---|---|---|---|
| User creation | `prisma/schema.prisma`, routes | Email unique; password hashed | `email @unique` enforced; `passwordHash` stored (hashing done by Java lifecycle service) | Yes | None | — |
| Session rotation | `UserSession` model | Refresh token revoked after use | Session tracking in DB; `revokedAt` field | Yes | None | Verify Java lifecycle service revokes old session on `refreshTokens` |
| Workspace cascade delete | `Workspace` model | Delete workspace cascades to members + projects | `onDelete: Cascade` on `WorkspaceMember`; `Project` has cascade from ownerWorkspace | Yes | None | — |
| Canvas versioning | `CanvasVersion` model | Every save creates a version | `CanvasVersion.create` on each PUT | Yes | None | Pagination of version history should be bounded |
| Agent execution log | `AgentExecution` model | Every AI agent call logged | `logAgentExecution()` called even for stub results — logs fake success as `SUCCESS` | **No** | Data accuracy | Log stub invocations as `STUB` status or suppress until real implementation |
| Lifecycle phase constraint | `LifecyclePhase` enum | Only valid phases accepted | `normalizeLifecyclePhaseId()` in domain normalizes legacy/alias phases | Yes | None | — |
| Multiple seeds | `prisma/seed*.ts` | Single canonical seed | 5 seed files with no documented canonical | **No** | Dev/test data in production risk | Document which seed is used for what environment; remove unused seeds |
| Approval request | `ApprovalRequest` model | Requests linked to project + requester | FKs present | Yes | None | — |
| Domain model duplication | `backend/api/domain/`, `libs/java/yappc-domain/` | Single canonical domain model | `Project`, `Incident`, `Compliance`, `SecurityScan`, `Alert` exist in both; migration incomplete | **No** | Drift between models | Complete migration per `DOMAIN_MODEL_REGISTRY.md` plan |

---

## 11. Production Mock/Stub/Shortcut Audit

| File | Evidence | Production Reachable? | Critical Flow? | Feature Flagged? | Severity | Required Action |
|---|---|---|---|---|---|---|
| `src/stubs/ai/agents/api-client.ts` | `AIAgentClientFactory.createCopilotClient().processMessage()` returns `{ message: 'Stub response', ... }`. All four agent methods are stubs. | **Yes** — imported in `AIAgentsResolver.ts` | **Yes** — GraphQL AI agent mutations | **No** | **P0** | Implement real HTTP client against Java AI backend (`JAVA_AI_BACKEND_URL`). Do not use this factory in production until real implementation exists. Add feature flag to disable AI agent mutations. |
| `src/stubs/ai/providers.ts` | `createProviderFactory()` returns `stub-provider` with `embed()` returning `new Array(1536).fill(0)`. | **Yes** — imported in `jobs/embedding-pipeline.ts` | Partial (background job) | Partially (real providers used if env vars set) | **P1** | When no LLM provider configured, **skip** embedding insertion instead of inserting zero vectors. Log `WARN` with actionable message. |
| `graphql/resolvers/AIAgentsResolver.ts` | `async aiAgents()` — comment "In production, this would call the Java backend's registry" — returns hardcoded array | **Yes** | **Yes** | **No** | **P0** | Call Java agent registry endpoint. Remove hardcoded list. |
| `graphql/resolvers/AIAgentsResolver.ts` | `agentExecution` log records stub responses as `status: 'SUCCESS'` with `tokensUsed: 0` | **Yes** | Audit integrity | **No** | **P1** | Log stub invocations with a distinct status (e.g. `STUB`) or suppress until real implementation |
| Multiple seed files | `prisma/seed*.ts` — 5 files, no documented canonical seed for production | Yes (build artifact risk) | Low | No | **P2** | Declare canonical seed, delete unused files, document which seed is for which environment |
| `middleware/devAuth.ts` comment | "DO NOT USE IN PRODUCTION" — correctly gated by `assertDevAuthBypassAllowed()` | **No** (properly gated) | N/A | Yes (env var required) | None | Current guards are correct. No action required. |
| `routes/login.tsx` demo login | `isDemoLoginEnabled()` — requires `DEV && VITE_ENABLE_DEMO_LOGIN=true` | **No** (correctly gated) | N/A | Yes | None | — |

---

## 12. Duplicate and Source-of-Truth Audit

| Duplicate Area | Files | Why Duplicate | Risk | Required Fix |
|---|---|---|---|---|
| `Project` domain model | `backend/api/domain/Project.java` + `libs/java/yappc-domain/Project.java` (Java) + `prisma/schema.prisma` (TypeScript) | Historical layering; migration incomplete | Model drift between API responses and domain truth | Complete Java migration per `DOMAIN_MODEL_REGISTRY.md`: delete `api.domain.Project`, use `yappc-domain.model.Project` |
| `Incident` domain model | `backend/api/domain/Incident.java` + `libs/java/yappc-domain/Incident.java` | Same as above | — | Delete `api.domain.Incident`; use `yappc-domain.model.Incident` |
| `Compliance` / `ComplianceAssessment` | `api.domain.Compliance` + `yappc-domain.ComplianceAssessment` | Different names, same concept | Mapper missing | Create mapper; delete deprecated `api.domain.Compliance` |
| `SecurityScan` / `ScanJob`+`ScanFinding` | `api.domain.SecurityScan` + `yappc-domain.ScanJob`+`ScanFinding` | Flattened vs. normalized model | API responses may miss granular scan data | Complete migration per registry plan |
| `Alert` / `SecurityAlert` | `api.domain.Alert` + `yappc-domain.SecurityAlert` | Name mismatch | — | Create mapper; delete deprecated `api.domain.Alert` |
| Auth service | `services/auth/auth.service.ts` + `services/auth/proxy-auth.service.ts` | Proxy service wraps original; both exist | `authService` export points to proxy (correct); original file can be removed after migration | Delete `auth.service.ts` if only proxy is used; remove dead code |
| Multiple seed files | `prisma/seed.ts`, `seed-basic.ts`, `seed-minimal.ts`, `seed-simple.ts`, `seed-workflows.ts`, `seed-ai.ts` | Iterative development without cleanup | Risk of wrong seed in production | Keep only `seed.ts` as canonical; delete others or move to `prisma/seeds/dev/` |

---

## 13. Security, Privacy, and Permission Audit

| Area | File(s) | Risk | Actual Behavior | Severity | Required Fix |
|---|---|---|---|---|---|
| Legacy canvas endpoint auth | `routes/canvas.ts:130-155` | Any authenticated user can read any project's canvas by guessing project ID | No `requireCanvasReadable()` preHandler on `GET /canvas/:projectId/:canvasId?` | **P1** | Add `requireCanvasReadable()` preHandler to legacy endpoint or deprecate and remove it |
| Token storage in frontend | `services/auth/AuthService.ts` | Tokens in `sessionStorage`/`localStorage` vulnerable to XSS | Session stored in browser storage | **P1** | Ensure tokens use httpOnly cookies from server; if sessionStorage is used, document XSS mitigations |
| CORS config | `index.ts` | Misconfigured CORS allows any origin | `ALLOWED_ORIGINS` env var controls; defaults to `http://localhost:5173` | Low in production if env set | Document required production `ALLOWED_ORIGINS` in deployment guide |
| Production env var defaults | `index.ts` | Startup aborts if `DATABASE_URL`, `JWT_ACCESS_SECRET` not set | Correctly throws at startup | None | — |
| Dev cookie secret default | `routes/auth.ts` | `COOKIE_SECRET || 'change-me-in-production'` | Production startup check rejects default value | None | — |
| GraphQL auth in production | `index.ts` | GraphQL requires auth | Yoga context throws if no `authorization` header in production — but this is weak (no token validation at context level) | **P1** | Validate JWT in Yoga context, not just check header presence; or rely on `authMiddleware` ordering (verify it runs before Yoga handler) |
| `tenantId` optional in JWT | `middleware/auth.middleware.ts` | Tenant isolation requires `tenantId` | `tenantId` optional in `JWTUserPayload`; downstream queries may not scope by tenant | **P1** | Require `tenantId` in JWT for multi-tenant deployments; enforce in `tenantScopingMiddleware` |
| PII in logs | Production routes | Prompt payloads and AI responses logged without PII classification | `console.error` with error details; audit middleware redacts known sensitive fields but not conversation content | **P1** | Implement PII classification on AI/conversation logs per BACKEND_IMPLEMENTATION_BACKLOG item F-Y034 |
| Metrics endpoint | `index.ts` | `/metrics` should not be public | Protected by JWT or internal IP or API key | Acceptable | Ensure `METRICS_API_KEY` is set in production deployments |
| Audit logging | `middleware/audit.middleware.ts` | Critical actions auditable | HTTP-level audit + service-level audit via `getAuditService()` | Good | Verify audit service writes are not fire-and-forget on critical mutations |
| No hardcoded secrets found | All production files | No secrets in code | Confirmed: all secrets via env vars | None | — |

---

## 14. Observability and Operability Audit

| Flow | Logs | Metrics | Traces | Audit Events | Gaps | Required Fix |
|---|---|---|---|---|---|---|
| HTTP requests | Fastify structured logger (all routes) | `yappc_api_http_request_duration_seconds`, `yappc_api_http_requests_total` | OTLP spans via manual Fastify instrumentation | `AuditLogEntry` per request | `console.error` in some route handlers (not Fastify logger) | Replace `console.error` with `request.log.error()` in all routes |
| AI agent execution | `console.error` on log failure | None specific to AI agents | None | `AgentExecution` DB log (logs stub results as SUCCESS) | No AI-specific metrics; stub responses indistinguishable from real | Add `ai_agent_call_total{agent, status}` counter; add stub detection |
| Lifecycle phase transition | `console.error` on AI recommendation failure | None | None | `AuditLogEntry` | Phase transition has no dedicated metric | Add `lifecycle_phase_transition_total{from, to, status}` |
| Embedding pipeline | `console.log` only | None | None | None | No visibility into embedding job health; zero-vector fallback silent | Add job execution metric; emit structured log on fallback |
| WebSocket real-time | Not confirmed | `yappc_api_websocket_connections` gauge | None | None | WebSocket disconnect/reconnect not observable | Add WebSocket event metrics |
| Database queries | Prisma logging not confirmed | `yappc_api_db_query_duration_seconds` | None | None | No slow query alerts | Enable Prisma `log: ['warn', 'error']` in production |
| Health/readiness | — | — | — | — | Readiness checks DB + Java backend — **correct** | — |
| Correlation ID propagation | All requests via `X-Correlation-ID` hook | — | Spans correlatable via correlation ID | — | Good | — |

---

## 15. Performance and Scalability Audit

| Area | Risk | Evidence | Impact | Required Fix |
|---|---|---|---|---|
| Canvas version history unbounded | High | `CanvasVersion` created on every save; no limit or pagination | Canvas with many saves accumulates unbounded rows | Add pagination to version history endpoint; add TTL or max-version limit |
| N+1 query risk in activity feed | Medium | `GET /projects/:id/activity` fetches from both Prisma and lifecycle proxy in parallel — but audit events fetched without pagination | Could return large result set | Add `take`/`skip` pagination to activity endpoint |
| Embedding pipeline blocking | Medium | Background job uses `for await` loop over all projects without batch size limit | Could OOM on large datasets | Add batch size config and progress logging |
| WebSocket connection scalability | Medium | `RedisCanvasRoomStore` exists — suggests Redis-backed room storage | Redis dependency for horizontal scaling | Document Redis configuration requirement |
| Bundle size | Unknown | `.size-limit.json` present in frontend | Not audited in this run | Run `pnpm size-limit` and verify limits are respected |
| Rate limiting | Good | `apiRateLimitMiddleware` and `aiRateLimitMiddleware` registered on all non-health endpoints | — | Verify rate limit values match production expectations |

---

## 16. Test Correctness and Coverage Audit

| Capability/Flow | Existing Tests | Missing Tests | Invalid Tests | Required Tests | Priority |
|---|---|---|---|---|---|
| Login / Auth proxy | `auth-flow.api-e2e.test.ts` | Test auth proxy failure (Java lifecycle service down) | None identified | Java lifecycle service timeout → 503 response test | P1 |
| AI Agent mutations | `ai-routes.test.ts` (rule-based only) | **All AI agent GraphQL mutations** | None | Integration test that asserts stub client is NOT used in production path; or test that mutations return real data | **P0** |
| Legacy canvas resource auth | None | Cross-user canvas access via legacy endpoint | None | Test that User A cannot read User B's canvas via `GET /canvas/:projectId` | **P1** |
| Embedding pipeline zero-vector | None | Zero-vector fallback detection | None | Test that pipeline skips DB insert (not inserts zeros) when no provider configured | **P1** |
| Workflow cancel durability | None | AEP notification after DB flag write | None | Integration test: simulate process crash after flag, verify background worker re-notifies AEP | P1 |
| Session token reuse attack | `security-session-ownership.test.ts` | Refresh token reuse after logout | None | Test that reusing a revoked refresh token returns 401 | P1 |
| CORS policy | None | CORS rejection for unlisted origin | None | Test that request from unlisted origin is rejected | P2 |
| GraphQL auth in production | None | Unauthenticated GraphQL request in production mode | None | Test that GraphQL returns 401 without valid token | P1 |
| Phase gate enforcement | `lifecycle-gates.integration.test.ts` | Phase 3-7 gate conditions | None | Gate rejection test for each phase transition | P1 |
| Metrics endpoint auth | None | Unauthenticated `/metrics` in production mode | None | Verify 401 for unauthenticated external access | P2 |

---

## 17. Prioritized Remediation Plan

| Priority | Area | Issue | Evidence/File(s) | Required Fix | Acceptance Criteria | Tests Required |
|---|---|---|---|---|---|---|
| **P0** | AI Agent GraphQL | All 4 AI agent mutations return hardcoded stub data ("Stub response") | `graphql/resolvers/AIAgentsResolver.ts`, `stubs/ai/agents/api-client.ts` | Implement real `AIAgentClientFactory` backed by HTTP calls to `JAVA_AI_BACKEND_URL`. OR feature-flag disable all AI agent mutations behind a clearly named flag disabled in production. | AI agent mutations invoke Java backend and return real inference results; or are inaccessible when feature flag is off | Integration test calling real (or Testcontainers-mocked) Java endpoint; test that stub file is no longer imported in production resolvers |
| **P0** | AI Agent registry | `aiAgents()` query returns hardcoded static list with no registry call | `graphql/resolvers/AIAgentsResolver.ts:220-260` | Call Java agent registry endpoint (`GET /api/v1/agents` on AEP) | Agent list reflects registered agents from AEP registry | Test against Testcontainers or WireMock of AEP registry |
| **P1** | Legacy canvas endpoint | `GET /canvas/:projectId/:canvasId?` has no resource-auth preHandler | `routes/canvas.ts:130-155` | Add `{ preHandler: requireCanvasReadable() }` to the legacy endpoint or remove the endpoint | Test shows User A gets 403 trying to read User B's canvas | New test: `legacy-canvas-resource-auth.test.ts` |
| **P1** | Embedding pipeline | Zero-vector fallback silently inserts corrupt data | `jobs/embedding-pipeline.ts`, `stubs/ai/providers.ts` | On stub fallback: skip DB insert, log structured `WARN` with `action: 'embedding_skipped'`, do not insert zero vectors | Zero vectors never appear in `canvasDocument.embedding` in integration test | Test: no zero-vector rows after pipeline run without LLM provider |
| **P1** | Structured logging | `console.error`/`console.log` used in production route handlers | `routes/canvas.ts`, `routes/workspaces.ts`, `routes/lifecycle.ts` | Replace with `request.log.error()` / `request.log.warn()` from Fastify logger | No `console.*` calls in non-test production route files | Lint rule or grep CI check |
| **P1** | GraphQL auth | Yoga context only checks `authorization` header presence, not validity | `index.ts:yoga context` | Extract `request.user` populated by `authMiddleware` into Yoga context; throw `GraphQLError` with code `UNAUTHENTICATED` if not set in production | Unauthenticated GraphQL request in production returns `UNAUTHENTICATED` error | API test in production mode config |
| **P1** | Token storage | Frontend may store JWT in `sessionStorage`/`localStorage` | `services/auth/AuthService.ts` | Prefer httpOnly cookie returned by server; document XSS mitigations if localStorage is used | Session tokens not readable by JavaScript from localStorage in production | Security audit + automated XSS header check |
| **P1** | Workflow cancel durability | `cancelWorkflow()` writes DB flag but does not notify AEP | `BACKEND_IMPLEMENTATION_BACKLOG.md` C-Y2 | Implement durable cancel: write `workflow_events` row, background worker calls AEP, mark cancelled after confirmation | Round-trip cancel test passes with simulated crash | `WorkflowCancellationDurabilityIT.java` |
| **P1** | PII in logs | Conversation/prompt payloads logged without PII classification | `BACKEND_IMPLEMENTATION_BACKLOG.md` F-Y034 | Add PII classification layer; redact `content`, `prompt`, `message` fields in AI-related log entries | No conversation content appears in Elasticsearch/Loki in sanitized test | PII log audit test |
| **P1** | Domain model duplication | 5 entity duplicates between `api.domain.*` and `yappc-domain.*` with migration incomplete | `DOMAIN_MODEL_REGISTRY.md` | Delete deprecated `api.domain.*` entities; update all usages to `yappc-domain`; create missing mappers | No `api.domain.Project/Incident/Compliance/SecurityScan/Alert` imports remain in active production code | Compile-time check; tests with `yappc-domain` entities |
| **P2** | Multiple seed files | 5+ seed files with no canonical documented | `prisma/seed*.ts` | Designate `seed.ts` as canonical; move dev-only seeds to `prisma/seeds/dev/`; delete or archive unused seeds | `prisma db seed` uses only canonical seed | Document in `DEVELOPMENT.md` |
| **P2** | Agent execution audit log | Stub responses logged as `SUCCESS` in `AgentExecution` | `graphql/resolvers/AIAgentsResolver.ts` | Log stub invocations as `STUB` status until real implementation; or suppress log | Audit log accurately reflects execution status | Unit test checking status field |
| **P2** | Cost tracking API | `CostTrackingService` has no project/tenant aggregate API | `BACKEND_IMPLEMENTATION_BACKLOG.md` C-Y14 | Implement `GET /api/projects/{projectId}/ai-cost` and tenant-scoped admin endpoint | Cockpit cost tile renders real data from API | `CostTrackingServiceTest.java` with tenant isolation |
| **P2** | Canvas version pagination | `CanvasVersion` rows unbounded | `routes/canvas.ts` | Add `take: 50` limit to version history query; add cursor-based pagination | Version list endpoint returns max 50 items by default | Test with >50 versions |

---

## 18. Production Readiness Gate

| Gate | Status | Details |
|---|---|---|
| **Ready for production** | **NO** | P0 AI Agent stub resolvers serve fake data to users. Legacy canvas endpoint has resource-auth gap. |
| **Ready for internal demo** | **CONDITIONAL** | AI agent features must be hidden or feature-flagged off. Canvas, workspace, project, lifecycle (phases 0-2), and observability are usable. |
| **Ready behind feature flag** | **YES** for AI agents | Wrap AI agent GraphQL mutations behind a GrowthBook feature flag disabled in production. All other features are usable with P1 issues tracked. |

### Critical Blockers Before Production Release

1. **P0-A** — Replace stub `AIAgentClientFactory` in `AIAgentsResolver.ts` with real Java backend HTTP client or feature-flag all AI agent mutations off.
2. **P0-B** — Replace hardcoded `aiAgents()` static list with real AEP registry call.
3. **P1-A** — Add resource-auth `preHandler` to legacy canvas GET endpoint.
4. **P1-B** — Fix embedding pipeline zero-vector fallback to skip instead of insert.
5. **P1-C** — Validate GraphQL auth properly rejects unauthenticated requests.

### Minimum Required Fixes Before Release

All 5 blockers above plus:
- Replace `console.error` with structured Fastify logger in all production routes.
- Document required production env vars (`ALLOWED_ORIGINS`, `METRICS_API_KEY`, `LIFECYCLE_SERVICE_URL`, `JAVA_AI_BACKEND_URL`, `OTEL_EXPORTER_OTLP_ENDPOINT`).
- Run `pnpm typecheck` and `pnpm lint` clean.
- Pass existing test suite without skipped tests (no `it.skip` on main branch).

---

## 19. Final Checklist

| Category | Item | Status |
|---|---|---|
| **Correctness** | Authentication flow (login, refresh, logout) | ✅ Correct |
| **Correctness** | RBAC enforcement on all protected routes | ✅ Correct |
| **Correctness** | Workspace/Project CRUD | ✅ Correct |
| **Correctness** | Canvas CRUD and versioning | ✅ Mostly correct (legacy endpoint resource-auth gap) |
| **Correctness** | AI Agent execution (GraphQL) | ❌ Stub — returns fake data |
| **Correctness** | AI artifact suggestions (REST) | ✅ Correct (rule-based heuristics, correctly labeled) |
| **Correctness** | Lifecycle phase transitions (phases 0-2) | ✅ Mostly correct |
| **Correctness** | Lifecycle phase transitions (phases 3-7) | ⚠️ Partial — in active development |
| **Completeness** | All routes have auth guards | ⚠️ Legacy canvas GET missing resource-auth |
| **Completeness** | All critical user journeys wired end-to-end | ⚠️ AI journeys are stub-only |
| **No production mocks/stubs** | AI agent GraphQL resolvers | ❌ Stubs in production path |
| **No production mocks/stubs** | Embedding pipeline fallback | ⚠️ Zero-vector fallback (inserts corrupt data) |
| **No production mocks/stubs** | Dev auth bypass | ✅ Correctly gated |
| **No production mocks/stubs** | MSW mock service worker | ✅ Correctly gated |
| **UI/UX** | Loading/empty/error states | ⚠️ Partially consistent |
| **UI/UX** | Demo login gated to dev | ✅ Correct |
| **UI/UX** | AI feature UX if stub is visible | ❌ Unclear — confirm UI shows real/stub distinction |
| **Backend/API** | Structured error responses | ✅ Consistent |
| **Backend/API** | Correlation ID propagation | ✅ Present |
| **Backend/API** | Rate limiting | ✅ Present |
| **DB/Data integrity** | Migrations tracked | ✅ 6 migrations |
| **DB/Data integrity** | Domain model canonicalized | ❌ 5 overlapping entities unmigrated |
| **DB/Data integrity** | Seed files canonical | ❌ 5 seed files, no documented canonical |
| **Security/Privacy** | No hardcoded secrets | ✅ All via env vars |
| **Security/Privacy** | PII redaction in audit logs | ⚠️ HTTP audit redacts fields; AI conversation content not classified |
| **Security/Privacy** | No dev tools in production | ✅ Correctly gated |
| **Observability** | Prometheus metrics exposed | ✅ |
| **Observability** | OTLP tracing | ✅ |
| **Observability** | Audit log | ✅ |
| **Observability** | Structured logging everywhere | ❌ `console.*` calls in route handlers |
| **Performance** | Canvas version history bounded | ❌ Unbounded |
| **Tests** | No test-theater (object-literal assertions) | ✅ Tests invoke real route handlers |
| **Tests** | AI agent stub behavior tested | ❌ No test for stub |
| **Tests** | Legacy canvas cross-user access | ❌ No test |
| **Tests** | Embedding zero-vector fallback | ❌ No test |
| **Documentation** | RELEASE_READINESS_CHECKLIST current | ✅ Present and detailed |
| **Documentation** | BACKEND_IMPLEMENTATION_BACKLOG current | ✅ Present — honest about gaps |
| **Documentation** | Module catalog matches settings.gradle | ⚠️ Needs verification |
