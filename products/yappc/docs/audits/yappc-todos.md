Below is a **production-grade implementation plan for YAPPC at commit `3dc787e5203034d59132244afc4869eb40a8a880`**, focused on making each area independently shippable while keeping contracts, UI, backend, security, build, and docs coherent.

## Baseline findings from this commit

This commit moves YAPPC toward stricter product/platform boundaries and contract governance. It introduces or changes: structured route-manifest metadata, cookie-session auth schema changes in OpenAPI, direct-platform-import lint enforcement, Docker artifact verification, and Makefile root-resolution fixes. 

The most important new source of truth is now `products/yappc/docs/api/route-manifest.yaml`, which declares each route as structured YAML with `method`, `path`, `auth`, `scopes`, `owner`, `boundary`, and `operationId`.  However, the existing Gradle OpenAPI parity task still parses the old `- GET /path` manifest format, so it will not correctly validate the new manifest shape. 

There is also an auth-contract mismatch: OpenAPI now includes browser-oriented cookie-session auth concepts, while the frontend REST client still models login and refresh as token-pair responses.   The backend API auth filter currently resolves only `X-API-Key` and `Authorization: Bearer ...`, not session cookies. 

## Implementation strategy

Do this in **four layers**:

1. **Contract and governance spine first**: route manifest, OpenAPI, auth mode, generated client, authorization registry, lint gates.
2. **Build and runtime correctness**: Makefile, Dockerfile, CI, health/readiness, artifact checks.
3. **Product capability slices**: lifecycle cockpit, dashboard actions, artifact generation, preview, scaffold, canvas/page builder.
4. **Coherence hardening**: end-to-end tests, docs cleanup, duplicate removal, boundary verification.

---

# Phase 0 — Stabilize the contract spine

## 0.1 Make `route-manifest.yaml` truly executable

**Goal:** `route-manifest.yaml` becomes the canonical machine-readable source for route metadata.

**Current issue:** The manifest is now structured YAML, but `checkYappcOpenApiParity` still uses a regex for old `METHOD /path` lines. 

**Implementation tasks:**

| Task                                | Where                             | Details                                                                                                        |
| ----------------------------------- | --------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| Replace old regex parser            | `products/yappc/build.gradle.kts` | Parse structured manifest entries with `method`, `path`, `auth`, `scopes`, `owner`, `boundary`, `operationId`. |
| Validate required fields            | `checkYappcOpenApiParity`         | Fail if any route lacks `method`, `path`, `auth`, `owner`, `boundary`, or `operationId`.                       |
| Validate auth/scopes consistency    | same task                         | If `auth: required`, require non-empty `scopes`; if `auth: public`, require empty scopes.                      |
| Validate OpenAPI method+path parity | same task                         | Check both path and HTTP method, not path only.                                                                |
| Validate `operationId` parity       | same task                         | OpenAPI route operationId must match manifest `operationId`.                                                   |
| Validate boundary values            | same task                         | Allow only `YAPPC`, `DATA_CLOUD_AEP`, or explicitly approved values.                                           |
| Add regression tests                | Gradle test or build logic test   | Include fixture manifest with structured YAML and confirm failures are detected.                               |

**Acceptance criteria:**

* `./gradlew :products:yappc:checkYappcOpenApiParity --no-configuration-cache` validates all structured manifest entries.
* The task fails on missing path, wrong method, mismatched `operationId`, missing scopes, invalid boundary, or invalid auth mode.
* No route can be added without an owner and boundary.

---

## 0.2 Resolve route path drift across manifest, OpenAPI, frontend, and backend registry

**Goal:** Every route has one canonical path across manifest, OpenAPI, frontend client, backend registration, and authorization registry.

**Status:** ✅ Completed (tasks 0.2.2, 0.2.3, 0.2.4, 0.2.5, 0.2.6)

**Resolution:**
- Fixed phase packet path drift: canonical path is `/api/v1/phase/packet`
- Fixed preview session path drift: canonical paths are `/api/v1/preview/session/create` and `/api/v1/preview/session/validate`
- Added dashboard action registry entries for `/api/v1/dashboard/actions`
- Normalized path param syntax to `{param}` format across all route metadata
- Added contract test for route parity across all layers

**Implementation tasks:**

| Task                                  | Where                                                                   | Details                                                                                                                              |
| ------------------------------------- | ----------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| Create route inventory report         | Script under `products/yappc/scripts/`                                  | Compare `route-manifest.yaml`, `openapi.yaml`, backend route registrations, frontend `client.ts`, and authorization registry.        |
| Fix phase packet path                 | `RouteAuthorizationRegistry`, controllers, OpenAPI, frontend API client | Pick one canonical path. Prefer `/api/v1/phase/packet` because manifest and OpenAPI use it.                                          |
| Fix preview path                      | same                                                                    | Prefer `/api/v1/preview/session/create` and `/api/v1/preview/session/validate` unless there is a deliberate `/yappc` namespace rule. |
| Add dashboard action registry entries | `RouteAuthorizationRegistry`                                            | Register GET/POST `/api/v1/dashboard/actions` if backend serves it.                                                                  |
| Normalize path param syntax           | all route metadata                                                      | Manifest/OpenAPI use `{id}`; registry uses `:id`. Add conversion in tooling, but keep manifest/OpenAPI canonical.                    |
| Add contract test                     | backend + frontend                                                      | Assert every frontend-used endpoint exists in OpenAPI and has authorization metadata.                                                |

**Acceptance criteria:**

* No route exists in one source without a matching canonical route in the other sources.
* Route mismatch report is empty.
* Adding a route requires manifest + OpenAPI + auth registry or generated registry metadata.

---

## 0.3 Generate authorization registry from route manifest

**Goal:** Stop manually duplicating route/security metadata.

**Current issue:** The route manifest now has machine-readable `auth`, `scopes`, `owner`, `boundary`, and `operationId`, but `RouteAuthorizationRegistry` still hardcodes route definitions manually.  

**Implementation tasks:**

| Task                                | Where                                        | Details                                                                                       |
| ----------------------------------- | -------------------------------------------- | --------------------------------------------------------------------------------------------- |
| Add manifest model                  | Java shared module or build-generated source | Define `RouteManifest`, `RouteEntry`, `AuthMode`, `Boundary`, `Scope`.                        |
| Generate Java registry data         | Gradle generation task                       | Convert manifest routes into Java route metadata at build time.                               |
| Refactor registry                   | `RouteAuthorizationRegistry.java`            | Load generated route definitions instead of hardcoding.                                       |
| Add public-route bypass             | `RouteAuthorizationRegistry` / auth filter   | `auth: public` must not require `Principal`.                                                  |
| Map scopes to permissions centrally | New mapper                                   | `project:read -> Permission.PROJECT_READ`, `project:write -> Permission.PROJECT_UPDATE`, etc. |
| Add privacy classification          | Manifest or generated default                | Add `privacyClassification` explicitly or derive from route family.                           |

**Acceptance criteria:**

* `RouteAuthorizationRegistry` no longer manually duplicates every route.
* Public routes `/health`, `/ready`, `/api/v1/yappc/info`, and configured public agent health routes work without credentials.
* Required routes fail closed.
* Permission mapping is tested.

---

## 0.4 Fix route pattern matching bug

**Goal:** Parameterized routes should authorize correctly.

**Current issue:** `RoutePattern.extractParameterNames` increments group indices for non-parameter path segments, but the regex only creates capture groups for parameter segments. This can cause incorrect matcher group lookup for parameterized routes like `/api/v1/yappc/generate/runs/:runId/apply`. 

**Implementation tasks:**

| Task                         | Where                                     | Details                                                                                                 |
| ---------------------------- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| Replace parameter extraction | `RouteAuthorizationRegistry.RoutePattern` | Track only parameter capture groups. First parameter is group 1, second is group 2.                     |
| Decode query/path params     | `extractQueryParameters`, path matching   | URL-decode values; reject malformed encodings safely.                                                   |
| Add tests                    | `RouteAuthorizationRegistryTest`          | Cover exact route, single param, multiple params, missing route, query workspaceId, header workspaceId. |
| Add failure tests            | same                                      | Confirm no `IndexOutOfBoundsException` or silent wrong scope.                                           |

**Acceptance criteria:**

* Parameterized routes extract correct `runId`, `artifactId`, `workflowId`, `jobId`, etc.
* Authorization failures return clean 403/401 style errors, not server exceptions.
* Route matching is deterministic and tested.

---

# Phase 1 — Auth/session contract alignment

## 1.1 Choose canonical browser auth mode

**Recommendation:** Use **httpOnly cookie sessions for browser UI** and **API key/Bearer tokens for service-to-service**.

OpenAPI now includes `CookieAuth` and session-shaped login/refresh responses.  The backend filter currently supports only API key or Bearer.  The frontend client still expects token responses. 

**Implementation tasks:**

| Task                        | Where                                        | Details                                                                                                                      |
| --------------------------- | -------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Update backend auth filter  | `YappcApiAuthFilter.java`                    | Support session cookie extraction and validation.                                                                            |
| Add session resolver        | governance/security/auth module              | Resolve `session` cookie into `Principal`.                                                                                   |
| Keep service auth           | same                                         | Preserve `X-API-Key` and Bearer for internal/service use.                                                                    |
| Update frontend auth types  | `frontend/web/src/lib/api/client.ts`         | Replace `AuthTokenResponse` for browser login with `LoginSessionResponse { user, session }`.                                 |
| Update AuthService          | `frontend/web/src/services/auth/AuthService` | Stop relying on local token storage for cookie-session mode.                                                                 |
| Update refresh/logout calls | frontend client + backend                    | Refresh should use cookie; logout invalidates session.                                                                       |
| Update OpenAPI text         | `openapi.yaml`                               | Make descriptions precise: browser endpoints use `CookieAuth`; internal service endpoints can use `ApiKeyAuth`/`BearerAuth`. |

**Acceptance criteria:**

* Browser login does not expose access/refresh tokens to JS.
* Existing frontend flows work with `credentials: 'same-origin'`.
* Service-to-service calls still support API keys.
* OpenAPI, frontend client, backend auth filter, and tests agree.

---

## 1.2 Normalize scope propagation

**Goal:** Every project/artifact/lifecycle call carries tenant/workspace/project scope consistently.

**Implementation tasks:**

| Task                        | Where                                | Details                                                                           |
| --------------------------- | ------------------------------------ | --------------------------------------------------------------------------------- |
| Add scoped request helper   | `frontend/web/src/lib/api/client.ts` | Central helper to add `X-Workspace-ID`, `X-Project-ID`, optionally `X-Tenant-ID`. |
| Remove ad hoc scope logic   | frontend client methods              | Replace scattered query/header choices with one policy.                           |
| Make OpenAPI explicit       | `openapi.yaml`                       | Required header/query scope per route should match backend extraction policy.     |
| Backend extraction policy   | `RouteAuthorizationRegistry`         | Path > query > header is okay, but document and test it.                          |
| Controller-level validation | backend controllers                  | Controllers validate body scope matches authorized scope if body includes ids.    |

**Acceptance criteria:**

* No route can authorize one project and mutate another from body mismatch.
* Workspace/project access failures are precise and actionable.
* UI no longer produces recurring “access denied” due to missing workspace context.

---

# Phase 2 — Build, Docker, and local developer workflow

## 2.1 Fix Dockerfile stage verification

**Current issue:** The runtime stage runs `RUN ls -la /workspace/...` before `COPY --from=backend-builder`. `/workspace` does not exist in the runtime image, so this verification is likely invalid in that stage. 

**Implementation tasks:**

| Task                                               | Where                       | Details                                                                      |
| -------------------------------------------------- | --------------------------- | ---------------------------------------------------------------------------- |
| Move builder verification into builder stages only | `products/yappc/Dockerfile` | Keep `RUN test -f` inside backend-builder/frontend-builder.                  |
| Remove runtime `/workspace` checks                 | runtime stage               | Runtime stage should only verify `/app/yappc-backend.jar` and `/app/public`. |
| Fix jar path if needed                             | Dockerfile                  | Ensure Gradle module path matches actual built jar location under repo root. |
| Add image smoke test                               | CI                          | `docker build` then `docker run` and hit `/health` and `/ready`.             |
| Add non-root write checks                          | runtime image               | Ensure app has access only to required writable dirs.                        |

**Acceptance criteria:**

* `docker build -f products/yappc/Dockerfile .` succeeds from repo root.
* Runtime image starts with non-root user.
* `/health` and `/ready` work in container.
* No build verification depends on files unavailable in that stage.

---

## 2.2 Harden Makefile commands

The Makefile now resolves `REPO_ROOT` and `GRADLEW`, which is good for running commands from subdirectories.  Continue hardening it.

**Implementation tasks:**

| Task                           | Where                     | Details                                                                        |
| ------------------------------ | ------------------------- | ------------------------------------------------------------------------------ |
| Fix duplicated port docs       | `products/yappc/Makefile` | `8082` is listed for both Domain and Lifecycle; decide canonical mapping.      |
| Make setup root-safe           | Makefile                  | Use `$(REPO_ROOT)` for scripts like `tools/scripts/verify-dev-environment.sh`. |
| Make docker commands path-safe | Makefile                  | Use explicit compose file path if required.                                    |
| Add `make contract-check`      | Makefile                  | Runs route manifest parity, OpenAPI tests, generated-client checks.            |
| Add `make production-check`    | Makefile                  | Runs build, tests, lint, typecheck, Docker smoke if available.                 |

**Acceptance criteria:**

* `make build`, `make test`, `make lint`, `make typecheck`, `make docker-up` work from repo root and `products/yappc`.
* Port allocation is coherent.
* New contributors can run `make quick-start` successfully.

---

# Phase 3 — Frontend architecture gates

## 3.1 Make direct-platform-import lint rule real and enforced

This commit adds a rule intended to block YAPPC from directly importing platform modules that should go through Data Cloud + AEP contract facades.  The frontend ESLint config currently loads custom local rules from `eslint-local-rules/dist/index.js` and enforces broad restrictions, direct fetch bans, and design-system rules. 

**Implementation tasks:**

| Task                         | Where                                                  | Details                                                                    |
| ---------------------------- | ------------------------------------------------------ | -------------------------------------------------------------------------- |
| Verify custom rule packaging | `products/yappc/frontend/eslint-local-rules`           | Ensure source builds to `dist/index.js`.                                   |
| Add test fixtures            | local ESLint rule tests                                | Positive/negative cases for `@ghatana/agent-core`, `@ghatana/vector`, etc. |
| Wire rule in frontend config | `eslint.config.mjs`                                    | Ensure rule name matches plugin namespace actually loaded.                 |
| Add CI gate                  | frontend package scripts                               | `pnpm lint:architecture` or include in `pnpm lint`.                        |
| Define allowed facades       | new `@yappc/platform-contracts` or existing API facade | Provide approved import targets so developers have a clear replacement.    |

**Acceptance criteria:**

* Direct platform imports fail lint.
* Imports through YAPPC-approved contract facades pass.
* Rule works in CI, local dev, and pre-commit.
* There is no ambiguous “blocked but no replacement” developer experience.

---

## 3.2 Migrate hand-coded REST client toward generated OpenAPI client

**Current issue:** The frontend client is hand-coded and still has token-oriented auth responses. 

**Implementation tasks:**

| Task                             | Where                       | Details                                                                               |
| -------------------------------- | --------------------------- | ------------------------------------------------------------------------------------- |
| Generate TS client from OpenAPI  | frontend build tooling      | Use one generated client under `libs/yappc-api` or `web/src/lib/generated`.           |
| Keep adapter layer               | `web/src/lib/api/client.ts` | Preserve ergonomic `yappcApi.projects.list()` style but delegate to generated client. |
| Remove duplicate types           | client.ts and domain files  | Replace local response/request interfaces with generated schemas where stable.        |
| Add OpenAPI client contract test | frontend tests              | Assert all used operations exist in generated client.                                 |
| Add migration map                | docs/api                    | Document old method → generated operation mapping.                                    |

**Acceptance criteria:**

* No frontend API method drifts from OpenAPI.
* Auth/session changes are generated and reflected in types.
* Hand-coded endpoints are gradually removed, not duplicated.

---

# Phase 4 — Backend authorization and governance

## 4.1 Public vs required auth semantics

**Current issue:** `RouteAuthorizationRegistry` currently asks for a `Principal` before authorization logic. That conflicts with manifest routes marked `auth: public`, such as `/health`.   Also, `YappcApiAuthFilter.secure` rejects missing credentials before a route can be classified as public. 

**Implementation tasks:**

| Task                                              | Where                                  | Details                                                                |
| ------------------------------------------------- | -------------------------------------- | ---------------------------------------------------------------------- |
| Split auth filter from authorization              | `YappcApiAuthFilter`, route middleware | Public routes bypass credential requirement.                           |
| Add route metadata lookup before auth enforcement | middleware                             | Determine auth mode from manifest/registry before requiring principal. |
| Add anonymous principal only if needed            | auth filter                            | Optional for telemetry; do not require permissions.                    |
| Test public routes                                | backend tests                          | `/health`, `/ready`, public info routes.                               |
| Test required routes                              | backend tests                          | Missing credentials returns 401; insufficient scope returns 403.       |

**Acceptance criteria:**

* Public routes are public.
* Required routes are fail-closed.
* Optional auth routes, if any, behave consistently.

---

## 4.2 Canonical audit and privacy classification

**Implementation tasks:**

| Task                           | Where                 | Details                                                                          |
| ------------------------------ | --------------------- | -------------------------------------------------------------------------------- |
| Add audit metadata to manifest | `route-manifest.yaml` | Add `auditEventType` and `privacyClassification`, or generate from route family. |
| Use metadata in registry       | generated route model | No more hardcoded audit event strings.                                           |
| Standardize privacy classes    | backend + docs        | `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `RESTRICTED`.                              |
| Add audit tests                | backend               | Critical routes create audit records with tenant/workspace/project/actor/phase.  |

**Acceptance criteria:**

* Every mutation route has an audit event type.
* Sensitive generation/preview/import routes are confidential or restricted.
* Audit records are queryable for lifecycle review.

---

# Phase 5 — Product capability implementation plan

## Workstream A — Lifecycle cockpit

**Scope:** Intent, Shape, Validate, Generate, Run, Observe, Learn, Evolve cockpit.

**Tasks:**

1. Make `/api/v1/phase/packet` the canonical cockpit read model.
2. Add GET and POST parity between manifest, OpenAPI, backend, and frontend.
3. Include project snapshot, phase readiness, blockers, required artifacts, activity, suggestions, and governance actions.
4. Remove route-local phase calculations from UI where backend packet should own classification.
5. Add E2E route test for each mounted phase.
6. Add role/tier gating tests for read-only, editor, admin, owner.

**Acceptance criteria:**

* Every phase page can load from the same packet contract.
* No phase page invents its own backend contract.
* Blockers/review/safe-to-continue actions are consistent between dashboard and phase cockpit.

---

## Workstream B — Dashboard actions

**Scope:** Workspace dashboard action groups: blocked work, review required, safe to continue.

**Tasks:**

1. Decide whether canonical endpoint is `/api/projects/dashboard-actions` or `/api/v1/dashboard/actions`; remove duplicate semantics.
2. Generate backend-derived actions only; mark degraded/fallback explicitly if client-derived actions remain.
3. Route every action to `/p/:projectId/:phase`.
4. Ensure execute action records audit and enforces scope.
5. Add UI states: empty, loading, unauthorized, partial degradation, action failed.

**Acceptance criteria:**

* Dashboard tells user exactly what to do next with no cognitive load.
* No hidden action requires manual route guessing.
* Actions are permission-aware and audit-backed.

---

## Workstream C — Artifact generation/diff/review/rollback

**Scope:** `/api/v1/yappc/generate`, `/generate/diff`, apply/reject/rollback.

**Tasks:**

1. Persist generation runs and generated files with provenance.
2. Replace placeholder/fallback generated content with deterministic fallback artifacts only when explicitly marked degraded.
3. Implement real diff regions with line ranges and ownership.
4. Add review decision model: apply, reject, rollback.
5. Preserve user edits and provenance across review.
6. Add rollback safety checks.
7. Add frontend review UI with side-by-side diffs, risk, provenance, and action buttons.

**Acceptance criteria:**

* Generated output is not anonymous file blobs.
* Every generated artifact has requirement/source/canvas/actor provenance.
* Rollback is tested.
* Review-required states are visible to operator.

---

## Workstream D — Preview/session security

**Scope:** Preview session create/validate and preview trust policy.

**Tasks:**

1. Align preview route paths across manifest, OpenAPI, registry, and frontend.
2. Implement artifact/project/workspace scope enforcement.
3. Support preview trust levels: trusted local, trusted controlled, semi-trusted, untrusted.
4. Add sandbox policy for semi/untrusted artifacts.
5. Add session expiry, revocation, and audit.
6. Add UI states for blocked preview, degraded preview, and policy-required review.

**Acceptance criteria:**

* Untrusted artifacts never execute directly.
* Preview sessions cannot cross tenant/workspace/project/artifact boundaries.
* Policy blocks appear in Observe/governance surfaces, not hidden debug panels.

---

## Workstream E — Scaffold/packs/templates/dependencies

**Scope:** Pack catalogue, scaffold project creation, feature add, update, template render, dependency conflict detection.

**Tasks:**

1. Keep scaffold routes under `scaffold-api` owner in manifest.
2. Add generated API client coverage for scaffold routes.
3. Validate pack metadata schema.
4. Add dependency conflict result model with actionable resolution.
5. Add dry-run mode for create/update/add-feature.
6. Add UI flow: choose pack → configure variables → preview generated files → confirm → generate.
7. Add tests with real pack fixtures.

**Acceptance criteria:**

* Scaffold operations are deterministic and previewable.
* No destructive operation runs without explicit confirmation or safe mode.
* Dependency conflicts are explained, not just listed.

---

## Workstream F — Data Cloud + AEP boundary facades

**Scope:** Vector, agents, workflows, RAG, copilot.

**Current state:** Manifest marks vector/agent/workflow routes as `boundary: DATA_CLOUD_AEP`. 

**Tasks:**

1. Create typed YAPPC-facing facades for Data Cloud + AEP.
2. Move direct imports behind those facades.
3. Keep route ownership clear while migration is in progress.
4. Add lint rule enforcement and fixtures.
5. Add boundary tests: YAPPC cannot import internal Data Cloud/AEP runtime modules.
6. Add contract tests for facade request/response models.

**Acceptance criteria:**

* YAPPC consumes platform capabilities through typed contracts only.
* No direct platform runtime import exists in YAPPC.
* Agent/vector/workflow failures degrade gracefully in the UI.

---

# Phase 6 — Testing and CI gates

## Required test tiers

| Tier          | Required coverage                                                                      |
| ------------- | -------------------------------------------------------------------------------------- |
| Unit          | Route parser, auth scope extraction, permission mapping, generated client adapters     |
| Contract      | route manifest ↔ OpenAPI ↔ frontend client ↔ backend registry                          |
| Integration   | auth/session, project/workspace scope, phase packet, dashboard actions                 |
| E2E           | dashboard → phase cockpit → generate → review → preview/run                            |
| Security      | public vs required routes, cross-tenant denial, missing scope, wrong role              |
| Build/runtime | Makefile, Docker build, container health/readiness                                     |
| Architecture  | no direct platform imports, no raw fetch outside API infra, no deleted compat packages |

## CI pipeline order

1. Format/check generated files.
2. Route manifest schema validation.
3. OpenAPI parity validation.
4. Generate TypeScript API client.
5. Typecheck frontend.
6. Lint frontend architecture rules.
7. Backend unit/integration tests.
8. Frontend unit/component tests.
9. E2E smoke tests.
10. Docker build and health smoke.
11. Repo cleanup/dead-code check.

---

# Phase 7 — Documentation and cleanup

## Canonical docs to update

| Doc                                           | Update                                                            |
| --------------------------------------------- | ----------------------------------------------------------------- |
| `products/yappc/docs/api/route-manifest.yaml` | Add strict schema docs and examples.                              |
| `products/yappc/docs/api/openapi.yaml`        | Align auth/session, route paths, operationIds, security schemes.  |
| YAPPC canonical architecture docs             | Document route manifest as source of truth.                       |
| Frontend API docs                             | Document generated-client migration.                              |
| Security docs                                 | Browser cookie session vs service API key/Bearer policy.          |
| Developer guide                               | `make quick-start`, `make contract-check`, Docker smoke workflow. |

## Cleanup tasks

* Remove stale route formats from docs and scripts.
* Remove duplicate route paths with and without `/yappc` prefix unless explicitly supported.
* Remove token-oriented browser auth docs once cookie session is canonical.
* Remove or archive old audit docs that are no longer canonical.
* Add “source of truth” section to each major doc so future audits do not repeat old findings.

---

# Suggested execution order

## Sprint 1 — Contract spine and auth correctness

1. Fix structured route manifest parsing.
2. Add manifest schema validation.
3. Fix OpenAPI path/method/operationId parity.
4. Align cookie-session auth across OpenAPI, backend filter, frontend client.
5. Fix public route behavior.
6. Fix route pattern parameter extraction.
7. Add route/auth contract tests.

## Sprint 2 — Frontend/backend client coherence

1. Generate TS client from OpenAPI.
2. Refactor `client.ts` into adapter over generated client.
3. Remove token-response assumptions from browser login.
4. Add scoped request helper.
5. Add lint and test gates for no raw fetch and no direct platform imports.
6. Add route drift report.

## Sprint 3 — Lifecycle and dashboard productionization

1. Canonicalize phase packet endpoint.
2. Canonicalize dashboard actions endpoint.
3. Build complete role/tier/workspace-scoped dashboard behavior.
4. Add phase cockpit E2E for all mounted phases.
5. Add degraded/fallback action semantics.

## Sprint 4 — Generate, preview, scaffold hardening

1. Productionize generation runs/diffs/provenance.
2. Implement apply/reject/rollback workflow.
3. Align preview session routes and trust policy.
4. Harden scaffold pack/template/dependency flows.
5. Add Docker image smoke test.

## Sprint 5 — Boundary and cleanup

1. Introduce Data Cloud + AEP typed facades.
2. Remove direct platform imports.
3. Clean stale docs and duplicated route references.
4. Run full production-check gate.
5. Create final implementation tracker with remaining capability-specific tasks.

---

# Highest-priority TODO list

1. **Fix `checkYappcOpenApiParity` to parse structured YAML.**
2. **Resolve auth mismatch: OpenAPI cookie session vs frontend token client vs backend API-key/Bearer-only filter.**
3. **Fix public route handling; `/health` must not require credentials.**
4. **Fix parameterized route matching in `RouteAuthorizationRegistry`.**
5. **Align `/api/v1/phase/packet`, preview session paths, and dashboard action paths across all layers.**
6. **Generate authorization registry metadata from `route-manifest.yaml` instead of duplicating routes manually.**
7. **Move Docker runtime-stage `/workspace` verification into builder stages.**
8. **Enforce direct-platform-import rule with tests and approved Data Cloud + AEP facades.**
9. **Migrate frontend REST client toward generated OpenAPI client.**
10. **Add end-to-end lifecycle smoke: dashboard → phase packet → generate → review → preview/run.**

This sequence keeps YAPPC moving toward production grade without fragmenting the product: every area can be tackled independently, but all work is forced through the same route manifest, OpenAPI, auth, scope, audit, design, and test gates.
