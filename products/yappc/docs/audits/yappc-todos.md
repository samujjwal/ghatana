Executed the uploaded YAPPC audit prompt against `samujjwal/ghatana` at commit `a05b6f697200f2141e0ac87139fc68220bcf5239`, using the full repository snapshot scope requested in the prompt.  

I did not run the full build/test suite locally; this is a source-and-docs audit based on the code, canonical docs, route/API manifests, and test gates visible at the target commit.

## 1. Executive summary

YAPPC has a strong canonical direction, but the implementation is not production-ready yet. The biggest blockers are:

1. **Authentication/session flow is internally inconsistent**: backend cookie mode returns different shapes than the frontend expects, the frontend still restores/saves token-bearing sessions, and the “httpOnly cookie” session manager tries to read httpOnly cookies from `document.cookie`, which cannot work.   
2. **Dashboard has a runtime crash**: `getProjectUpdatedAt(right).i18n.languageCompare(...)` is invalid and will break the home dashboard for non-empty project lists. 
3. **Production placeholders remain in active routes**: onboarding status always returns false for project/member checks because helper functions are placeholders. 
4. **YAPPC/Data Cloud+AEP boundary is defined in docs but not enforced consistently**: canonical docs correctly say YAPPC must consume the merged Data Cloud+AEP platform through typed contracts, but route/API/tooling still expose vector/agent/workflow-like surfaces under YAPPC and ESLint still models `data-cloud` and `aep` as separate product boundaries.   
5. **Release readiness gates are too easy to bypass**: release verification defaults to evidence-presence mode and warns that production releases should use `--execute`; Docker/Makefile paths also skip tests or use stale backend layout.   

## 2. Product boundary assessment

The canonical architecture model is directionally correct. It defines **Project** as the persisted workspace-scoped delivery container, **Product** as the business outcome, and **App** as the generated/deployed runtime software. It also defines the mounted lifecycle as Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve. 

The Data Cloud+AEP boundary is also correctly described: YAPPC should consume platform capabilities only through typed/generated contracts and must not import internal platform runtime, memory, retrieval, analytics, telemetry, or agent modules. The required platform request context includes tenant, workspace, project, actor, phase, operation, data classification, timestamp, and correlation ID; responses must include status, confidence, trace/evidence/policy metadata, degraded state, and timing metadata. 

The gap is enforcement. Current code still has legacy lifecycle storage mappings, direct platform token exchange from the frontend, route surfaces that look like platform internals under YAPPC, and lint rules that still treat `aep` and `data-cloud` as separate products rather than one merged platform boundary.   

## 3. Area-by-area findings

| Severity    | Area                          | Finding                                                                                                                                                                                           | Evidence                                                                                  | Production-grade fix                                                                                                                                                              |                           |                                                                                                                                      |
| ----------- | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| **Blocker** | Auth/session                  | Cookie login returns `{ user }`, while frontend `AuthService` expects `authData.tokens.accessToken` and `refreshToken`. This breaks cookie-mode login.                                            | Backend cookie branch sends user-only; frontend creates session from `authData.tokens`.   | Define one OpenAPI-backed `LoginResponse` for cookie mode and token mode. Prefer cookie mode returning user/session metadata only; update frontend to never require tokens in JS. |                           |                                                                                                                                      |
| **Blocker** | Dashboard UX shell            | Dashboard sorting calls `.i18n.languageCompare` on a string. This is a direct runtime crash.                                                                                                      | `recentProjects` sort expression in `dashboard.tsx`.                                      | Replace with `getProjectUpdatedAt(right).localeCompare(getProjectUpdatedAt(left))`; add regression test for non-empty project list.                                               |                           |                                                                                                                                      |
| **High**    | Security/privacy              | Auth cookie setup uses optional plugin registration, `@ts-ignore`, a default secret `change-me-in-production`, and fallback token-in-response mode.                                               | Auth route plugin setup.                                                                  | In production, fail startup if cookie plugin/secret/secure config is missing. Remove token-in-response fallback outside explicit migration mode.                                  |                           |                                                                                                                                      |
| **High**    | Session management            | SessionManager claims httpOnly cookies but reads `document.cookie`; httpOnly cookies are not readable from JavaScript, so `hasSession()` is unreliable.                                           | `getAccessTokenFromCookie`, `getRefreshTokenFromCookie`, and `hasSession`.                | Replace cookie inspection with server-backed `/api/auth/me` probe and in-memory session metadata.                                                                                 |                           |                                                                                                                                      |
| **High**    | Auth/platform boundary        | Frontend performs raw `fetch` to `VITE_AUTH_GATEWAY_URL                                                                                                                                           |                                                                                           | [http://localhost:8081`](http://localhost:8081`) for platform token exchange, stores `platformToken`, and bypasses typed platform contracts.                                      | `exchangePlatformToken`.  | Move platform token exchange to backend/BFF through typed Data Cloud+AEP auth contract; never expose platform tokens to web runtime. |
| **High**    | Production placeholders       | Active onboarding route uses helper functions that always return `false`.                                                                                                                         | `checkUserHasProjects` and `checkUserHasInvitedMembers`.                                  | Replace with Prisma-backed queries or remove the route until implemented behind a disabled feature flag.                                                                          |                           |                                                                                                                                      |
| **High**    | Workspace/project access      | Project route can infer ownership when `workspaceId` is missing, and dashboard action logic handles included projects read-only in one path but access/capability enforcement is still scattered. | Project route access model and dashboard execute flow.                                    | Require workspace scope for project reads/mutations; use one backend capability resolver for all routes and UI rendering.                                                         |                           |                                                                                                                                      |
| **High**    | Lifecycle cockpit             | Backend still maps mounted phases to legacy persisted phases like `SHAPE → CONTEXT`, `GENERATE → EXECUTE`, `RUN → VERIFY`, `EVOLVE → INSTITUTIONALIZE`.                                           | `normalizeLifecyclePhase`.                                                                | Introduce canonical persisted lifecycle enum or one explicit compatibility adapter at the boundary; remove scattered legacy phase semantics.                                      |                           |                                                                                                                                      |
| **High**    | Dashboard action intelligence | Dashboard actions are regex-classified from titles and fallback to `Resume ${phase} phase`; this is not authoritative governance/action readiness.                                                | `classifyDashboardAction` and `buildProjectDashboardActions`.                             | Backend should return a typed `ProjectDashboardActionDecision` with reason, capability, policy decision, degraded status, and execution eligibility.                              |                           |                                                                                                                                      |
| **High**    | API contracts                 | OpenAPI tests check path/method presence and frontend client endpoints, but route manifest lacks auth/scope metadata and many manifest routes are outside the critical method matrix.             | OpenAPI contract test and route manifest.                                                 | Extend route manifest to include owner, auth requirement, required scope, mutation/read classification, and OpenAPI operation ID; validate full manifest parity.                  |                           |                                                                                                                                      |
| **High**    | Data Cloud+AEP integration    | Canonical docs require typed platform contracts and metadata, but route manifest exposes vector/agent/workflow routes under `yappc-api` instead of a clear platform contract boundary.            | Platform boundary doc and route manifest.                                                 | Move platform-like surfaces behind Data Cloud+AEP contract client/server adapter; YAPPC should only call lifecycle-oriented platform APIs.                                        |                           |                                                                                                                                      |
| **Medium**  | Generation/diff/review        | Frontend model has provenance/diff fields, but response fields are optional and not tied to required platform trace/evidence/policy metadata.                                                     | Generate response/diff types.                                                             | Make generation responses provenance-complete, review-gated, and linked to platform run/trace/evidence/policy IDs.                                                                |                           |                                                                                                                                      |
| **Medium**  | Canvas/page builder/preview   | Canonical model is documented, but route inventory still classifies `/canvas`, `/preview`, `/deploy`, and `/lifecycle` as legacy; large latent page surface exists under `pages/**`.              | Mounted route inventory.                                                                  | Keep only mounted/canonical routes in shipped scope; archive or unexport latent pages unless they are explicitly mounted and contract-tested.                                     |                           |                                                                                                                                      |
| **Medium**  | Tests/quality gates           | Release readiness script defaults to evidence presence and only executes tests with `--execute`.                                                                                                  | Release-readiness script.                                                                 | CI/release mode must run execution mode by default; evidence-only mode should be local/fast only.                                                                                 |                           |                                                                                                                                      |
| **Medium**  | Build/deployment              | Dockerfile and Makefile skip tests; Dockerfile references stale backend module layout.                                                                                                            | Dockerfile and Makefile.                                                                  | Align Docker/Makefile with current YAPPC modules; remove `-x test` from production builds.                                                                                        |                           |                                                                                                                                      |

## 4. Data Cloud+AEP enhancements required for YAPPC

YAPPC needs a typed, product-facing Data Cloud+AEP contract layer for:

* **Intelligence execution**: create lifecycle runs, poll status, fetch trace, cancel run.
* **Evidence/retrieval**: search evidence, fetch evidence artifacts, index approved artifacts.
* **Memory**: query project memory, write approved lifecycle knowledge, summarize project context.
* **Telemetry/analytics**: emit lifecycle events and read project/lifecycle/model-quality summaries.
* **Policy/guardrails**: evaluate high-impact operations and fetch policy decisions.

The contract must carry the required YAPPC context: `tenantId`, `workspaceId`, `projectId`, `actorId`, `phase`, `operation`, `dataClassification`, `requestedAt`, and `correlationId`; responses must return trace/evidence/policy/degraded metadata. 

## 5. Shared library enhancements required

1. **Generated contract client**: replace hand-maintained DTO drift with generated OpenAPI/typed clients plus a small product wrapper.
2. **Route manifest schema**: promote route ownership/auth/scope metadata into a shared validation format.
3. **Boundary lint update**: model Data Cloud+AEP as one merged platform boundary, not separate `aep` and `data-cloud` products.
4. **HTTP client consolidation**: enforce no raw REST fetch outside API infrastructure; migrate AuthService/platform exchange into typed BFF/backend calls.
5. **Governance/audit schema**: centralize event fields for actor, tenant, workspace, project, artifact, phase, operation, policy decision, and correlation ID.

## 6. Cross-cutting architectural risks

The largest systemic risk is **contract drift**. YAPPC has canonical docs and some parity tests, but implementation still has old lifecycle aliases, multiple auth/session models, optional cookie fallback, handwritten API types, route manifest gaps, and production placeholders. The next risk is **boundary leakage**: YAPPC code and routes are still carrying platform-runtime concerns that should live behind Data Cloud+AEP typed contracts.

## 7. Prioritized implementation roadmap

**P0 — Stop production blockers**

Fix auth/session contract, dashboard crash, unsafe cookie fallback/default secret, onboarding placeholders, and production build/test bypasses.

**P1 — Make scope/access/contracts enforceable**

Add route manifest auth/scope metadata, backend capability read model, full OpenAPI/generated client enforcement, canonical lifecycle persistence/adapter, and dashboard action contracts.

**P2 — Complete YAPPC production spine**

Implement phase packets, authoritative readiness/blockers/governance/evidence, generated artifact review/rollback idempotency, preview trust model, page-builder operation logs, and platform degraded states.

**P3 — Consolidate and clean**

Remove stale docs, old audit/session docs, latent unmounted UI pages, duplicate HTTP/runtime clients, stale Docker/Makefile paths, and split Data Cloud/AEP wording.

## 8. Detailed TODO list

```text
ID: YAPPC-P0-001
Title: Fix cookie-mode auth response and frontend session contract
Severity: Blocker
Area: Auth/session
Owner boundary: YAPPC
Problem: Backend cookie mode sends { user }, while frontend AuthService expects authData.tokens.accessToken and refreshToken.
Evidence: products/yappc/frontend/apps/api/src/routes/auth.ts; products/yappc/frontend/web/src/services/auth/AuthService.ts
Implementation: Define a single OpenAPI LoginResponse for cookie mode; update AuthService to use /api/auth/me after login and never require JS-readable tokens.
Files to change:
- products/yappc/frontend/apps/api/src/routes/auth.ts
- products/yappc/frontend/web/src/services/auth/AuthService.ts
- products/yappc/frontend/web/src/providers/auth-session.ts
- products/yappc/docs/api/openapi.yaml
Contracts affected: LoginResponse, RefreshTokenResponse, AuthUser/UserInfo
Tests required: auth route contract tests, frontend login test, reload/session persistence test, 401 refresh test
Acceptance criteria: Login works in cookie mode; no access/refresh token is required in frontend state; OpenAPI and generated types match runtime response.
```

```text
ID: YAPPC-P0-002
Title: Remove JS-readable token storage from web auth
Severity: Blocker
Area: Security/privacy
Owner boundary: YAPPC
Problem: AuthService restores/saves token-bearing sessions in localStorage and SessionManager tries to read httpOnly cookies through document.cookie.
Evidence: products/yappc/frontend/web/src/services/auth/AuthService.ts; products/yappc/frontend/web/src/services/session/SessionManager.ts
Implementation: Replace token-bearing localStorage session with server-probed session state; keep only non-sensitive user/session metadata in memory.
Files to change:
- products/yappc/frontend/web/src/services/auth/AuthService.ts
- products/yappc/frontend/web/src/services/session/SessionManager.ts
- products/yappc/frontend/web/src/providers/auth-session.ts
Contracts affected: Auth session provider contract
Tests required: XSS-safety unit test, no-token-localStorage test, reload auth test
Acceptance criteria: No accessToken, refreshToken, platformToken, auth_token, or api_key is persisted or read from localStorage/document.cookie.
```

```text
ID: YAPPC-P0-003
Title: Fail closed on unsafe auth cookie configuration
Severity: High
Area: Security/operations
Owner boundary: YAPPC
Problem: Cookie plugin registration is optional, default secret is change-me-in-production, and token response fallback remains in production path.
Evidence: products/yappc/frontend/apps/api/src/routes/auth.ts
Implementation: Register @fastify/cookie at app bootstrap; require COOKIE_SECRET in production; fail startup if missing; gate token fallback behind explicit migration flag.
Files to change:
- products/yappc/frontend/apps/api/src/index.ts
- products/yappc/frontend/apps/api/src/routes/auth.ts
- products/yappc/docs/operations/*
Contracts affected: Auth deployment config
Tests required: startup guard tests, production config tests, auth fallback disabled test
Acceptance criteria: Production cannot start with missing cookie plugin, weak cookie secret, or unintended token-in-response mode.
```

```text
ID: YAPPC-P0-004
Title: Fix dashboard runtime crash
Severity: Blocker
Area: Dashboard and UX shell
Owner boundary: YAPPC
Problem: Dashboard uses getProjectUpdatedAt(right).i18n.languageCompare(...), which is invalid for strings.
Evidence: products/yappc/frontend/web/src/routes/dashboard.tsx
Implementation: Replace with localeCompare/date-safe sort helper and extract tested utility.
Files to change:
- products/yappc/frontend/web/src/routes/dashboard.tsx
- products/yappc/frontend/web/src/routes/__tests__/dashboard.test.tsx
Contracts affected: None
Tests required: dashboard renders with owned projects, included projects, invalid updatedAt fallback, empty state
Acceptance criteria: Dashboard renders reliably for guest, empty, owned-project, included-project, and mixed-project states.
```

```text
ID: YAPPC-P0-005
Title: Remove onboarding placeholder logic
Severity: High
Area: Workspace/project onboarding
Owner boundary: YAPPC
Problem: checkUserHasProjects and checkUserHasInvitedMembers always return false in active production route.
Evidence: products/yappc/frontend/apps/api/src/routes/auth.ts
Implementation: Replace placeholders with Prisma queries scoped by user/workspace; if unsupported, remove from active route or feature-flag as unavailable.
Files to change:
- products/yappc/frontend/apps/api/src/routes/auth.ts
- products/yappc/frontend/apps/api/src/__tests__/auth-onboarding-status.test.ts
Contracts affected: OnboardingStatusResponse
Tests required: no workspace, workspace only, project exists, invited member exists, unauthorized
Acceptance criteria: Onboarding status reflects real persisted state and contains no placeholder branch.
```

```text
ID: YAPPC-P1-006
Title: Canonicalize lifecycle phase persistence and compatibility
Severity: High
Area: Lifecycle cockpit
Owner boundary: YAPPC
Problem: Mounted phases are mapped into legacy persisted phases, creating route/API/DB/doc drift.
Evidence: products/yappc/frontend/apps/api/src/routes/projects.ts
Implementation: Use canonical lifecycle enum for persisted and API state, with a single compatibility adapter for old values.
Files to change:
- products/yappc/frontend/apps/api/src/routes/projects.ts
- products/yappc/frontend/web/src/services/phase/CanonicalPhaseService.ts
- products/yappc/docs/api/openapi.yaml
- Prisma/schema or migration files where lifecyclePhase is persisted
Contracts affected: Project.lifecyclePhase, UpdateProjectRequest.lifecyclePhase, ProjectDashboardAction.routePhase
Tests required: old alias compatibility, update lifecycle phase, dashboard routing, phase cockpit routes
Acceptance criteria: New records persist canonical mounted phases; old aliases map only at compatibility boundary.
```

```text
ID: YAPPC-P1-007
Title: Replace heuristic dashboard actions with authoritative backend decision contract
Severity: High
Area: Dashboard and governance
Owner boundary: YAPPC
Problem: Dashboard action kind/safety is inferred from title regex and fallback labels.
Evidence: products/yappc/frontend/apps/api/src/routes/projects.ts; products/yappc/frontend/web/src/routes/dashboard.tsx
Implementation: Create ProjectDashboardActionDecision contract with action kind, capability, reason, policyDecisionId, evidenceIds, degradedReason, safeToRun, and target phase.
Files to change:
- products/yappc/frontend/apps/api/src/routes/projects.ts
- products/yappc/frontend/web/src/lib/api/client.ts
- products/yappc/frontend/web/src/routes/dashboard.tsx
- products/yappc/docs/api/openapi.yaml
Contracts affected: ProjectDashboardAction, ProjectDashboardActionsResponse
Tests required: blocker/review/safe matrix, viewer/read-only matrix, unavailable/degraded matrix, execute audit test
Acceptance criteria: UI never infers safety from text; backend remains source of action eligibility.
```

```text
ID: YAPPC-P1-008
Title: Add auth/scope metadata to route manifest and enforce full OpenAPI parity
Severity: High
Area: API contracts
Owner boundary: YAPPC | Shared Library
Problem: Current route manifest lists method/path but not auth, required scope, owner boundary, or mutation classification.
Evidence: products/yappc/docs/api/route-manifest.yaml; products/yappc/frontend/apps/api/src/__tests__/openapi-contract.test.ts
Implementation: Extend manifest schema and parity test to validate every route has operationId, auth requirement, required scope, request context, and OpenAPI match.
Files to change:
- products/yappc/docs/api/route-manifest.yaml
- products/yappc/docs/api/openapi.yaml
- products/yappc/frontend/apps/api/src/__tests__/openapi-contract.test.ts
- build/Gradle route parity task
Contracts affected: Route manifest schema
Tests required: manifest schema validation, missing auth metadata failure, missing OpenAPI operation failure
Acceptance criteria: Unknown protected routes deny by default; every route declares scope and auth policy.
```

```text
ID: YAPPC-P1-009
Title: Move platform auth/execution concerns behind Data Cloud+AEP typed contracts
Severity: High
Area: Data Cloud+AEP integration
Owner boundary: Data Cloud+AEP | YAPPC
Problem: Frontend directly exchanges platform token through raw fetch and route manifest exposes vector/agent/workflow surfaces under YAPPC.
Evidence: products/yappc/frontend/web/src/services/auth/AuthService.ts; products/yappc/docs/api/route-manifest.yaml
Implementation: Define Data Cloud+AEP platform client/server adapter and remove frontend platform-token ownership.
Files to change:
- products/yappc/frontend/web/src/services/auth/AuthService.ts
- products/yappc/frontend/apps/api/src/routes/*
- products/yappc/docs/api/openapi.yaml
- platform/data-cloud-aep contract package path selected by repo conventions
Contracts affected: PlatformAuthExchange, PlatformRunRequest, PlatformRunStatus, EvidenceSearchRequest, PolicyEvaluateRequest
Tests required: platform unavailable, degraded response, trace/evidence/policy metadata, no raw frontend platform token
Acceptance criteria: YAPPC uses typed platform contracts only and never stores platform tokens in browser state.
```

```text
ID: YAPPC-P2-010
Title: Make generation responses provenance-complete and review-gated
Severity: High
Area: Generation, diff, review, rollback
Owner boundary: YAPPC | Data Cloud+AEP
Problem: Generation response fields are optional and not guaranteed to include platform run, trace, evidence, policy, degraded, or review metadata.
Evidence: products/yappc/frontend/web/src/lib/api/client.ts; products/yappc/docs/architecture/YAPPC_CANONICAL_MODELS.md
Implementation: Require runId, status, reviewRequired, diff provenance, traceId, evidenceIds, policyDecisionId, degraded/degradedReason; prevent apply for degraded outputs.
Files to change:
- products/yappc/frontend/web/src/lib/api/client.ts
- products/yappc/docs/api/openapi.yaml
- products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/generate/*
Contracts affected: GenerateArtifactsResponse, GeneratedFileDiff, GenerateDiffRegion, GenerateReviewDecisionResponse
Tests required: generate success, degraded generate, apply/reject/rollback idempotency, provenance missing failure
Acceptance criteria: No generated artifact can be applied without provenance and review state.
```

```text
ID: YAPPC-P2-011
Title: Convert release readiness from evidence-presence to execution enforcement
Severity: Medium
Area: Tests and quality gates
Owner boundary: YAPPC
Problem: Release readiness script defaults to checking evidence files and only executes tests with --execute.
Evidence: products/yappc/frontend/web/scripts/verify-release-readiness.mjs
Implementation: Make CI/release command run execution mode by default; keep evidence-only mode as explicit --evidence-only local command.
Files to change:
- products/yappc/frontend/web/scripts/verify-release-readiness.mjs
- products/yappc/frontend/web/package.json
- products/yappc/docs/RELEASE_READINESS_CHECKLIST.md
Contracts affected: Release gate contract
Tests required: script mode tests, CI command validation
Acceptance criteria: Production release gate fails when tests are absent or failing, not merely when evidence files are absent.
```

```text
ID: YAPPC-P2-012
Title: Align Docker, Makefile, and docs with current YAPPC modules
Severity: Medium
Area: Build/deployment/docs cleanup
Owner boundary: Docs/Cleanup
Problem: Dockerfile references stale backend layout and skips tests; Makefile build skips tests.
Evidence: products/yappc/Dockerfile; products/yappc/Makefile
Implementation: Update build paths to current modules, remove production -x test, pin pnpm/Java/Node versions consistently, document one production build path.
Files to change:
- products/yappc/Dockerfile
- products/yappc/Makefile
- products/yappc/docs/DEVELOPER_GUIDE.md
- products/yappc/docs/OPERATIONS.md
Contracts affected: Build/release process
Tests required: Docker build smoke test, Makefile build test, release readiness test
Acceptance criteria: The documented production path builds the same artifact that CI validates.
```

```text
ID: YAPPC-P3-013
Title: Clean latent pages, legacy routes, and old audit/session documents
Severity: Medium
Area: Documentation and cleanup
Owner boundary: Docs/Cleanup
Problem: Mounted route inventory says large latent pages under web/src/pages/** are not shipped behavior; legacy routes remain mounted; old audit/todo docs can be confused with source of truth.
Evidence: products/yappc/docs/architecture/YAPPC_MOUNTED_ROUTE_INVENTORY_2026-04-20.md
Implementation: Archive non-authoritative audits, remove or explicitly unmount latent pages, convert route inventory into generated check, and keep one canonical tracker.
Files to change:
- products/yappc/docs/audits/*
- products/yappc/docs/todo-reports/*
- products/yappc/frontend/web/src/pages/**
- products/yappc/frontend/web/src/routes.ts
Contracts affected: Route inventory, docs index
Tests required: route inventory generation test, no unmounted shipped pages check
Acceptance criteria: No stale audit doc is treated as implementation source of truth; mounted product surface is small and validated.
```
