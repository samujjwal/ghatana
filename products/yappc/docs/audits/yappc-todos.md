Executed the pasted **Concise YAPPC Product Audit Prompt** against `samujjwal/ghatana` at commit:

`0ebf11101100e17adadf5b863d74cd787d1de9e1`

I audited this as the **complete codebase snapshot at that commit**, not as a commit diff, and did not rely on the previous generated audit/TODO document as truth. The prompt source is the uploaded markdown. 

I did not execute the full build/test suite locally; this is a source, contract, docs, and gate-readiness audit based on the repository files available at the target commit.

---

## 1. Executive summary

This commit improves several issues from the prior snapshot: the dashboard sorting crash is fixed, cookie plugin setup moved to startup, route manifest now includes auth/scope comments, release readiness now defaults to execution mode, and Docker/Makefile no longer explicitly skip tests in normal production build paths.     

However, YAPPC is still **not production-ready** at this commit. The biggest remaining blockers are:

1. **Auth/session still has contract and runtime inconsistencies.** Backend login sends only `{ user }`, while the frontend API client still types login as returning required `tokens`. Refresh still returns an access token body even though the design says tokens are httpOnly-cookie-only.  
2. **SessionManager changed `hasSession()` to async, but auth-session provider still treats it synchronously.** This creates a TypeScript/runtime correctness issue in `hasActiveSession()` and `fetchAuthSession()`.  
3. **Platform token exchange still happens from the browser through raw fetch and now uses an empty bearer token in cookie mode.** This breaks the Data Cloud+AEP typed-contract boundary and leaks platform-auth concerns into YAPPC web code. 
4. **Dashboard action classification is still heuristic and fallback-driven.** It still classifies action safety through regex/title inspection and still creates `Resume ${phase} phase` fallback actions. 
5. **Route manifest auth/scope metadata is only comments.** It is not machine-readable and is not validated by the OpenAPI contract test.  
6. **Data Cloud+AEP boundary is documented but still not enforced.** Canonical docs say YAPPC must consume Data Cloud+AEP through typed contracts only, but route manifest and OpenAPI still expose vector/agent/workflow surfaces inside YAPPC.   

---

## 2. Product boundary assessment

The canonical model is correct: **Project** is the persisted workspace-scoped delivery container, **Product** is the business/customer outcome, and **App** is the generated or deployed runtime software. The lifecycle is explicitly defined as Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve. 

The Data Cloud+AEP boundary is also clearly stated in the canonical model: YAPPC must consume the merged Data Cloud+AEP platform through typed contracts only and must not import internal platform runtime, memory, retrieval, analytics, telemetry, or policy modules. Required request context includes tenant, workspace, project, actor, phase, operation, data classification, requested timestamp, and correlation ID; responses must include status, confidence, trace/evidence/policy metadata, degraded state, and run/memory/search IDs where applicable. 

The implementation is still behind the model. YAPPC route manifests and OpenAPI still include vector, agent, workflow, and RAG surfaces under YAPPC API ownership instead of a clearly separated Data Cloud+AEP platform contract boundary.  

---

## 3. Area-by-area findings

### Finding 1 — Login/refresh contract remains inconsistent

**Severity:** Blocker
**Area:** Auth/session
**Owner boundary:** YAPPC
**Evidence:** `products/yappc/frontend/apps/api/src/routes/auth.ts`, `products/yappc/frontend/web/src/lib/api/client.ts`, `products/yappc/docs/api/openapi.yaml`

The backend login route sets httpOnly cookies and sends only `{ user: result.user }`, but the route schema still documents `tokens`, and the frontend client still declares `LoginSessionResponse.tokens` as required.  

Refresh is also inconsistent: the backend returns `{ accessToken, expiresIn }` in the body even though the stated cookie model says tokens should remain in httpOnly cookies. The frontend client still expects `AuthTokenResponse` with `accessToken`, `refreshToken`, and `expiresIn`.  

**Production-grade fix:**
Create two explicit response contracts or, preferably, one cookie-mode contract:

```text
LoginResponse:
  user
  session:
    expiresAt
    authMode: "COOKIE"
```

```text
RefreshTokenResponse:
  expiresAt
  authMode: "COOKIE"
```

Remove access/refresh tokens from web-visible response bodies in cookie mode. Regenerate the OpenAPI client and make runtime responses match the generated types.

---

### Finding 2 — Async `hasSession()` breaks sync callers

**Severity:** Blocker
**Area:** Auth/session provider
**Owner boundary:** YAPPC
**Evidence:** `SessionManager.ts`, `auth-session.ts`

`SessionManager.hasSession()` now returns `Promise<boolean>` by calling `/api/auth/me`, which is directionally correct for httpOnly cookies. But `auth-session.ts` still has:

```ts
export function hasActiveSession(): boolean {
  return hasSession();
}
```

and:

```ts
if (!hasSession()) {
  return null;
}
```

Because `hasSession()` now returns a Promise, `hasActiveSession()` returns the wrong type/value, and `if (!hasSession())` will never behave as intended because a Promise is truthy.  

**Production-grade fix:**
Make session probing consistently async:

```ts
export async function hasActiveSession(): Promise<boolean> {
  return hasSession();
}
```

and update all callers to `await hasSession()`. Also add a typecheck test or compile gate that catches this class of sync/async contract drift.

---

### Finding 3 — Browser still performs platform token exchange

**Severity:** High
**Area:** Data Cloud+AEP platform boundary / security
**Owner boundary:** YAPPC + Data Cloud+AEP
**Evidence:** `AuthService.exchangePlatformToken()`

The frontend still calls:

```ts
fetch(`${authGatewayUrl}/auth/exchange`, ...)
```

with `VITE_AUTH_GATEWAY_URL || 'http://localhost:8081'`, then stores `platformToken` in the session object. In cookie mode, `currentSession.token` is often `''`, so this exchange is likely broken. More importantly, platform token exchange belongs in the backend/BFF or Data Cloud+AEP gateway, not the browser. 

**Production-grade fix:**
Delete browser-side platform-token exchange. Add a backend endpoint such as:

```text
POST /api/v1/platform/session/exchange
```

or avoid explicit exchange entirely by using the BFF to call Data Cloud+AEP with server-side credentials. YAPPC web should receive only degraded/status/trace metadata, never platform tokens.

---

### Finding 4 — AuthService still carries token-shaped session model

**Severity:** High
**Area:** Auth/session model
**Owner boundary:** YAPPC
**Evidence:** `AuthService.ts`

`AuthSession` still includes `token`, `refreshToken`, and `platformToken`; `getAuthToken()` and `getPlatformToken()` are still public methods. The current implementation sets token fields to empty strings in cookie mode, but the model still encourages token-based flows and feeds `buildAuthHeaders()` in the typed client.  

**Production-grade fix:**
Split the session model:

```ts
interface BrowserAuthSession {
  user: User;
  expiresAt: string;
  permissions: string[];
  authMode: 'COOKIE';
}
```

Remove `getAuthToken()` and `getPlatformToken()` from browser services. If some migration code still needs tokens, isolate it in a deprecated adapter behind an explicit non-production feature flag.

---

### Finding 5 — Onboarding no longer has literal placeholders, but now silently masks DB failures

**Severity:** Medium
**Area:** Onboarding / production correctness
**Owner boundary:** YAPPC
**Evidence:** `checkUserHasProjects`, `checkUserHasInvitedMembers`

The commit replaced placeholder returns with Prisma queries, which is progress. But both helper functions catch errors, log with `console.error`, and return `false`. That makes operational failures indistinguishable from true “no project/no invite” state and can silently corrupt onboarding status. 

**Production-grade fix:**
Return a degraded response or fail the request with correlation-aware error handling:

```text
onboardingStatus.degraded = true
onboardingStatus.degradedReason = "project_lookup_failed"
```

or return `503` for dependency failure. Use structured logging through the app logger, not `console.error`.

---

### Finding 6 — Dashboard action safety is still inferred from text

**Severity:** High
**Area:** Lifecycle cockpit / dashboard action execution
**Owner boundary:** YAPPC
**Evidence:** `classifyDashboardAction`, `buildProjectDashboardActions`

Dashboard actions are still built from `project.aiNextActions` or fallback lifecycle text, then classified by regex terms such as `block`, `failed`, `review`, `approve`, `rollback`, and `audit`. This means a title controls governance semantics. The fallback `Resume ${routePhase} phase` can become a safe-to-run action even when no authoritative action exists. 

**Production-grade fix:**
Replace this with a backend-owned decision contract:

```text
ProjectDashboardActionDecision:
  id
  kind
  severity
  safeToRun
  requiresReview
  policyDecisionId
  evidenceIds
  reasonCode
  degraded
  degradedReason
  source
```

The UI should display decisions, not infer them.

---

### Finding 7 — Route manifest metadata is not enforceable

**Severity:** High
**Area:** API contracts / access control
**Owner boundary:** Shared Library + YAPPC
**Evidence:** `route-manifest.yaml`, `openapi-contract.test.ts`

The route manifest now includes helpful `# @auth` and `# @scopes` comments, but comments are not schema-validated. The OpenAPI contract tests check required paths, client endpoint coverage, generated artifact provenance, and some wording, but they do not parse or enforce route-manifest auth/scope metadata.  

**Production-grade fix:**
Convert manifest entries to structured YAML:

```yaml
- method: POST
  path: /api/v1/yappc/generate
  auth: required
  scopes:
    - project:write
  owner: yappc-services
  boundary: YAPPC
  operationId: generateArtifacts
```

Then add tests that fail when any route lacks `auth`, `scopes`, `owner`, `boundary`, and OpenAPI `operationId`.

---

### Finding 8 — Data Cloud+AEP boundary remains mixed into YAPPC route surfaces

**Severity:** High
**Area:** Product boundary / platform integration
**Owner boundary:** Data Cloud+AEP + Shared Library
**Evidence:** Canonical model, route manifest, OpenAPI

Canonical docs say YAPPC must consume Data Cloud+AEP through typed contracts and must not import or own internal platform runtime/data modules. But the route manifest and OpenAPI still include vector search, RAG, agent execution, copilot chat, agent prediction, and workflow engine endpoints under YAPPC API surfaces.   

**Production-grade fix:**
Move these to a typed platform contract namespace, for example:

```text
/platform/data-cloud-aep/contracts/*
/platform/data-cloud-aep/client/*
```

YAPPC should call product-level operations such as:

```text
submitLifecycleRun
getRunTrace
searchProjectEvidence
evaluatePolicyDecision
writeApprovedMemory
```

not raw vector/agent/workflow internals.

---

### Finding 9 — OpenAPI still describes JWT/API-key-first semantics while implementation is cookie-first

**Severity:** Medium
**Area:** API contract / auth documentation
**Owner boundary:** YAPPC
**Evidence:** `openapi.yaml`, `auth.ts`

OpenAPI description says all `/api/*` endpoints require API key or Bearer token, while the Node auth implementation has shifted to httpOnly cookie-first behavior. Login and refresh descriptions still speak in token terms.  

**Production-grade fix:**
Update OpenAPI security schemes to include cookie/session auth explicitly. Document browser/BFF cookie auth separately from internal API-key/Bearer service auth.

---

### Finding 10 — Release readiness improved but still has evidence-only gaps

**Severity:** Medium
**Area:** Tests and quality gates
**Owner boundary:** YAPPC
**Evidence:** `verify-release-readiness.mjs`

Release readiness now defaults to execution mode, which is a meaningful improvement. But visual-regression and accessibility gates still have `execute: null`, meaning they remain file-presence checks. The `api-contract` gate executes only `client.telemetry.test.ts`, not the OpenAPI contract test itself. 

**Production-grade fix:**
Split release gates:

```text
verify:release-readiness:fast
verify:release-readiness:full
verify:release-readiness:ci
```

CI/full release must run OpenAPI contract tests, accessibility E2E, and visual regression or explicitly require signed artifact evidence from a prior CI job.

---

### Finding 11 — Makefile path may be wrong for product-local execution

**Severity:** Medium
**Area:** Build/deployment
**Owner boundary:** Docs/Cleanup
**Evidence:** `products/yappc/Makefile`

The Makefile says “via monorepo Gradle” but uses `./gradlew` from inside `products/yappc`. If the wrapper exists only at the repo root, this will fail when running `make build` from the product directory. Earlier style used `../../gradlew`; this should be verified against actual repo layout. 

**Production-grade fix:**
Use a robust root resolver:

```make
REPO_ROOT := $(shell git rev-parse --show-toplevel)
GRADLEW := $(REPO_ROOT)/gradlew
```

Then call `$(GRADLEW)` consistently.

---

### Finding 12 — Dockerfile needs path verification for Gradle outputs

**Severity:** Medium
**Area:** Build/deployment
**Owner boundary:** Docs/Cleanup
**Evidence:** `products/yappc/Dockerfile`

Dockerfile now builds `:core:yappc-services`, `:core:yappc-api`, and `:core:scaffold`, then copies from `/workspace/core/yappc-services/build/libs/*.jar`. This must be verified against actual Gradle project directories. If the modules physically live under `products/yappc/core/...`, the runtime copy path will fail. 

**Production-grade fix:**
Add a Docker build smoke test in CI and avoid hardcoded physical paths by copying from the exact Gradle project buildDir or using a packaging task that produces one known artifact location.

---

## 4. Data Cloud+AEP required enhancements for YAPPC

YAPPC needs a typed Data Cloud+AEP facade for:

1. **Lifecycle intelligence execution**

   * submit run
   * get run status
   * cancel run
   * fetch trace
   * return degraded reason

2. **Evidence and retrieval**

   * search project evidence
   * index approved artifacts
   * attach evidence IDs to Validate/Generate/Review

3. **Memory**

   * read project-scoped memory summaries
   * write approved lifecycle learning only after governance approval

4. **Policy/guardrails**

   * evaluate high-impact operations
   * return policy decision IDs
   * block/review/approve actions explicitly

5. **Telemetry and analytics**

   * emit lifecycle events
   * return model quality/confidence summaries
   * support trace/correlation IDs

The canonical model already defines the required context and response metadata; the implementation now needs typed contracts and enforcement. 

---

## 5. Shared library required enhancements

1. **Structured route manifest schema**

   * Replace comment metadata with typed YAML.
   * Validate `auth`, `scope`, `owner`, `boundary`, and `operationId`.

2. **Generated API client discipline**

   * Stop maintaining duplicated handwritten frontend response types for auth/session.
   * Generate from OpenAPI and use thin product wrappers only.

3. **Platform contract package**

   * Add a shared Data Cloud+AEP client/DTO package.
   * Forbid YAPPC imports from internal platform runtime packages.

4. **Auth/session shared contract**

   * Define cookie-mode browser session DTOs centrally.
   * Remove token-bearing browser session abstractions.

5. **Governance event schema**

   * Standardize actor, tenant, workspace, project, phase, operation, artifact, policy decision, trace ID, and correlation ID.

---

## 6. Cross-cutting architectural risks

The main risk is now **partial fix drift**: several issues have been partially addressed, but implementation, docs, OpenAPI, route manifest, and frontend types do not yet converge into one enforceable contract. Examples include cookie-mode auth, route-scope metadata, dashboard action safety, and Data Cloud+AEP platform boundaries.

The second major risk is **governance by convention**. Auth scopes are comments, dashboard safety is regex-based, platform usage is documented but not enforced, and release gates still allow important categories to pass through file-presence evidence.

---

## 7. Duplicate / DRY / SRP issues

* `AuthService`, `SessionManager`, `auth-session.ts`, and `client.ts` each define overlapping session/auth semantics.
* `ProjectDashboardAction` exists in frontend client and backend route code rather than being generated/shared from OpenAPI.
* Lifecycle phase mapping exists in backend route code and frontend canonical phase service; the compatibility adapter should be centralized or generated from one contract.
* Route auth/scope metadata is duplicated between route code, OpenAPI, and comment-only manifest.

---

## 8. Security/access/governance gaps

* Browser platform-token exchange remains a boundary violation.
* Refresh still returns access token data to the browser despite cookie-mode intent.
* Route manifest scopes are comments only.
* Metrics/internal proxy/catch-all behavior should be reviewed with explicit auth and network-policy tests.
* Dashboard safe action execution is not policy-decision-driven.
* Onboarding DB failures are silently converted to false state.

---

## 9. UI/UX consistency and no-cognitive-load gaps

The dashboard has improved: the sort crash is fixed and degraded dashboard status messaging is better. 

Remaining UX risk: the dashboard still exposes backend-derived/fallback action states that may not be authoritative. A “safe continuation” should be a product decision from backend policy/readiness, not a result of text classification or absence of blockers. 

---

## 10. API/contract mismatches

Highest priority mismatches:

* Login runtime response vs typed `LoginSessionResponse`.
* Refresh runtime response vs typed `AuthTokenResponse`.
* Cookie-based auth implementation vs OpenAPI JWT/API-key wording.
* Route manifest comment metadata vs enforceable OpenAPI/security metadata.
* Data Cloud+AEP platform boundary docs vs vector/agent/workflow routes under YAPPC.

---

## 11. Test gaps

* Add compile/typecheck coverage for async `hasSession()` call sites.
* Add auth contract tests for login/refresh runtime shape.
* Add no-token-in-browser tests for login, refresh, localStorage, and session restore.
* Add route manifest schema validation tests.
* Add policy-backed dashboard action tests.
* Add Data Cloud+AEP boundary lint/architecture tests.
* Add Docker build smoke test and Makefile path test.
* Make release readiness execute OpenAPI contract and a11y/visual gates in CI/full mode.

---

## 12. Documentation/cleanup tasks

* Update OpenAPI auth wording to reflect cookie-mode web auth and internal service auth separately.
* Remove or clearly archive audit docs that contain prior generated findings.
* Replace split Data Cloud/AEP wording wherever it remains in product docs.
* Convert route manifest comments into structured schema.
* Update build docs to clarify root vs product-local commands.

---

## 13. Prioritized implementation roadmap

### P0 — Fix runtime correctness and auth/session contracts

1. Fix async `hasSession()` call sites.
2. Align login/refresh runtime responses, OpenAPI, generated client, and AuthService.
3. Remove browser platform-token exchange.
4. Remove token-shaped browser session model.

### P1 — Make governance and scope enforceable

1. Convert route manifest metadata to structured schema.
2. Add OpenAPI/manifest auth-scope parity tests.
3. Replace dashboard regex action classification with backend policy/readiness decisions.
4. Add tenant/workspace/project scope validation across project/dashboard routes.

### P2 — Enforce Data Cloud+AEP boundary

1. Create typed Data Cloud+AEP platform contracts.
2. Move vector/agent/workflow/RAG surfaces out of YAPPC ownership.
3. Add import-boundary lint/ArchUnit rules.

### P3 — Harden release/build/test gates

1. Run release readiness in CI/full mode.
2. Add Docker and Makefile smoke tests.
3. Execute accessibility, visual, and OpenAPI contract gates in production release flow.

---

## 14. Detailed TODO list

```text
ID: YAPPC-P0-001
Title: Fix async session contract drift
Severity: Blocker
Area: Auth/session
Owner boundary: YAPPC
Problem:
  SessionManager.hasSession() now returns Promise<boolean>, but auth-session.ts still treats it as boolean.
Evidence:
  products/yappc/frontend/web/src/services/session/SessionManager.ts
  products/yappc/frontend/web/src/providers/auth-session.ts
Implementation:
  Make hasActiveSession async, await hasSession in fetchAuthSession, update all callers, and add typecheck coverage.
Files to change:
  products/yappc/frontend/web/src/providers/auth-session.ts
  products/yappc/frontend/web/src/services/session/SessionManager.ts
  all callers of hasActiveSession/hasSession
Contracts affected:
  Browser session provider contract
Tests required:
  Typecheck test, fetchAuthSession no-cookie test, expired-session test, successful /api/auth/me probe test
Acceptance criteria:
  No Promise<boolean> is used as boolean; session probing behaves correctly with httpOnly cookies.
```

```text
ID: YAPPC-P0-002
Title: Align login response contract with cookie-mode runtime
Severity: Blocker
Area: Auth/API contracts
Owner boundary: YAPPC
Problem:
  Backend sends { user } for login, while frontend client and OpenAPI still model token-bearing LoginResponse.
Evidence:
  products/yappc/frontend/apps/api/src/routes/auth.ts
  products/yappc/frontend/web/src/lib/api/client.ts
  products/yappc/docs/api/openapi.yaml
Implementation:
  Define cookie-mode LoginResponse with user + session metadata only. Regenerate client. Remove token requirement from LoginSessionResponse.
Files to change:
  products/yappc/frontend/apps/api/src/routes/auth.ts
  products/yappc/docs/api/openapi.yaml
  products/yappc/frontend/web/src/lib/api/client.ts
  generated OpenAPI client files
Contracts affected:
  LoginRequest, LoginResponse, UserInfo
Tests required:
  Login route contract test, frontend login test, generated-client parity test
Acceptance criteria:
  Runtime login response, OpenAPI, generated client, and AuthService all agree.
```

```text
ID: YAPPC-P0-003
Title: Remove access token from refresh response body
Severity: High
Area: Auth/security
Owner boundary: YAPPC
Problem:
  Refresh route sets httpOnly cookies but also returns accessToken in response body.
Evidence:
  products/yappc/frontend/apps/api/src/routes/auth.ts
Implementation:
  Return only non-sensitive session metadata from refresh. Keep tokens exclusively in httpOnly cookies.
Files to change:
  products/yappc/frontend/apps/api/src/routes/auth.ts
  products/yappc/docs/api/openapi.yaml
  products/yappc/frontend/web/src/services/auth/AuthService.ts
Contracts affected:
  RefreshTokenResponse
Tests required:
  Refresh response shape test, no-token-body test, cookie-set test
Acceptance criteria:
  No accessToken or refreshToken is visible to browser JavaScript in cookie mode.
```

```text
ID: YAPPC-P0-004
Title: Delete browser-side platform token exchange
Severity: High
Area: Data Cloud+AEP integration/security
Owner boundary: YAPPC | Data Cloud+AEP
Problem:
  AuthService exchanges platform tokens from browser using raw fetch and stores platformToken in session.
Evidence:
  products/yappc/frontend/web/src/services/auth/AuthService.ts
Implementation:
  Move platform exchange to backend/BFF or eliminate it behind server-side Data Cloud+AEP client.
Files to change:
  products/yappc/frontend/web/src/services/auth/AuthService.ts
  products/yappc/frontend/apps/api/src/routes/platform-session.ts or equivalent
  Data Cloud+AEP typed contract package
Contracts affected:
  Platform session/execution contract
Tests required:
  No platform token in browser test, backend platform call test, degraded platform unavailable test
Acceptance criteria:
  Browser never receives or stores platform tokens.
```

```text
ID: YAPPC-P1-005
Title: Replace dashboard regex classification with backend policy decision contract
Severity: High
Area: Dashboard/lifecycle governance
Owner boundary: YAPPC
Problem:
  Dashboard action kind and safety are inferred from action title text and fallback lifecycle labels.
Evidence:
  products/yappc/frontend/apps/api/src/routes/projects.ts
Implementation:
  Introduce ProjectDashboardActionDecision with policyDecisionId, reasonCode, evidenceIds, safeToRun, requiresReview, degraded fields.
Files to change:
  products/yappc/frontend/apps/api/src/routes/projects.ts
  products/yappc/docs/api/openapi.yaml
  products/yappc/frontend/web/src/lib/api/client.ts
  products/yappc/frontend/web/src/routes/dashboard.tsx
Contracts affected:
  ProjectDashboardAction, ProjectDashboardActionsResponse
Tests required:
  Blocker/review/safe matrix, no-fallback-safe-action test, included-read-only action test
Acceptance criteria:
  UI never infers action safety from text.
```

```text
ID: YAPPC-P1-006
Title: Make route manifest metadata machine-readable and enforced
Severity: High
Area: API contracts/access control
Owner boundary: Shared Library | YAPPC
Problem:
  Auth/scope metadata exists only as YAML comments and is not validated.
Evidence:
  products/yappc/docs/api/route-manifest.yaml
  products/yappc/frontend/apps/api/src/__tests__/openapi-contract.test.ts
Implementation:
  Convert route entries to structured objects and validate auth/scope/owner/boundary/operationId parity with OpenAPI.
Files to change:
  products/yappc/docs/api/route-manifest.yaml
  products/yappc/frontend/apps/api/src/__tests__/openapi-contract.test.ts
  Gradle OpenAPI parity task
Contracts affected:
  Route manifest schema
Tests required:
  Missing auth metadata failure, missing scope failure, OpenAPI operationId mismatch failure
Acceptance criteria:
  Every route has enforced auth/scope metadata.
```

```text
ID: YAPPC-P1-007
Title: Stop silently masking onboarding dependency failures
Severity: Medium
Area: Onboarding/operations
Owner boundary: YAPPC
Problem:
  Onboarding helper DB failures are logged to console and returned as false, hiding operational failure.
Evidence:
  products/yappc/frontend/apps/api/src/routes/auth.ts
Implementation:
  Use structured logger and return degraded status or 503 on dependency failure.
Files to change:
  products/yappc/frontend/apps/api/src/routes/auth.ts
Contracts affected:
  OnboardingStatusResponse
Tests required:
  DB failure test, degraded response test, no-project true-empty test
Acceptance criteria:
  Onboarding state distinguishes empty state from dependency failure.
```

```text
ID: YAPPC-P1-008
Title: Enforce Data Cloud+AEP product boundary
Severity: High
Area: Product boundary/platform integration
Owner boundary: Data Cloud+AEP | Shared Library | YAPPC
Problem:
  YAPPC docs require typed platform contracts, but route manifest/OpenAPI expose vector, RAG, agent, and workflow internals under YAPPC.
Evidence:
  products/yappc/docs/architecture/YAPPC_CANONICAL_MODELS.md
  products/yappc/docs/api/route-manifest.yaml
  products/yappc/docs/api/openapi.yaml
Implementation:
  Move platform-like APIs behind Data Cloud+AEP typed contract facade and forbid internal imports from YAPPC.
Files to change:
  products/yappc/docs/api/route-manifest.yaml
  products/yappc/docs/api/openapi.yaml
  Data Cloud+AEP contract/client package
  lint/architecture rules
Contracts affected:
  PlatformRunRequest, PlatformRunStatus, EvidenceSearchRequest, PolicyEvaluateRequest
Tests required:
  Boundary lint test, platform degraded test, typed contract parity test
Acceptance criteria:
  YAPPC consumes Data Cloud+AEP only through typed/generated product-facing contracts.
```

```text
ID: YAPPC-P2-009
Title: Update OpenAPI security model for cookie-mode browser auth
Severity: Medium
Area: API documentation/contracts
Owner boundary: YAPPC
Problem:
  OpenAPI still describes JWT/API-key auth as the general /api model while implementation is cookie-first for browser auth.
Evidence:
  products/yappc/docs/api/openapi.yaml
  products/yappc/frontend/apps/api/src/routes/auth.ts
Implementation:
  Add cookie/session auth scheme and separate browser auth from internal service auth.
Files to change:
  products/yappc/docs/api/openapi.yaml
Contracts affected:
  securitySchemes, auth endpoint descriptions
Tests required:
  OpenAPI security scheme test, auth route contract test
Acceptance criteria:
  Auth docs accurately match runtime behavior.
```

```text
ID: YAPPC-P2-010
Title: Strengthen release readiness full gate
Severity: Medium
Area: Tests/quality gates
Owner boundary: YAPPC
Problem:
  Release readiness defaults to execution mode, but visual/accessibility gates remain execute:null and API contract gate does not execute openapi-contract.test.ts.
Evidence:
  products/yappc/frontend/web/scripts/verify-release-readiness.mjs
Implementation:
  Add full/CI mode that executes OpenAPI, accessibility, visual, and performance gates or validates signed CI artifacts.
Files to change:
  products/yappc/frontend/web/scripts/verify-release-readiness.mjs
  products/yappc/frontend/web/package.json
  CI workflow files
Contracts affected:
  Release readiness gate
Tests required:
  Script mode tests, CI command tests
Acceptance criteria:
  Full release cannot pass by evidence presence for critical UX/security/contract gates.
```

```text
ID: YAPPC-P2-011
Title: Make product Makefile root-safe
Severity: Medium
Area: Build/deployment
Owner boundary: Docs/Cleanup
Problem:
  Makefile uses ./gradlew for monorepo Gradle commands from products/yappc; this may fail if wrapper is only at repo root.
Evidence:
  products/yappc/Makefile
Implementation:
  Resolve repo root with git rev-parse and call $(REPO_ROOT)/gradlew.
Files to change:
  products/yappc/Makefile
Tests required:
  make build smoke test from products/yappc and repo root
Acceptance criteria:
  Product-local and repo-root build commands both work.
```

```text
ID: YAPPC-P2-012
Title: Add Docker build artifact path verification
Severity: Medium
Area: Build/deployment
Owner boundary: YAPPC
Problem:
  Dockerfile builds :core:* Gradle projects and copies /workspace/core/yappc-services/build/libs/*.jar; path must be verified against actual Gradle project dirs.
Evidence:
  products/yappc/Dockerfile
Implementation:
  Add packaging task that emits one known runtime JAR path or update copy path to actual Gradle buildDir.
Files to change:
  products/yappc/Dockerfile
  Gradle packaging task
  CI Docker build workflow
Tests required:
  docker build smoke test
Acceptance criteria:
  Docker image builds reproducibly from clean checkout.
```
