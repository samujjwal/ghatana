Audited target commit `bd6a92f85f64549103e844712f95ecbe5438f7fe`, verified as commit message `refactor 44`. I inspected the YAPPC product docs, canonical architecture/model docs, frontend phase cockpit/services, typed API client, lifecycle/generation/artifact/preview/page-artifact backend controllers, auth filter, and cleanup-related repository artifacts. I did **not** run Gradle, pnpm, Playwright, Docker, or CI, so build/test execution is **Not verified from current execution**. 

# 7.1 Production Readiness Verdict

**Production ready: No**

**Confidence level: High** for static code/doc architecture findings; **medium** for runtime behavior because I did not execute the app or test suite.

**Main blockers:**

1. The target snapshot appears to contain a compile-breaking corruption in `PreviewSessionApiController.java`.
2. YAPPC has authentication and tenant context, but route/action-level authorization is not canonical across all APIs.
3. Lifecycle execution persistence is not trustworthy: it derives `projectId` from `tenantId` / `"default-project"` and persists results fire-and-forget.
4. Preview sessions are not wired with real authorization/audit dependencies in `YappcHttpServer`.
5. Generation/artifact graph APIs still accept project/tenant/actor context from payloads without consistently tying them to authenticated principal scope.
6. Domain and persistence ownership remain split across overlapping Java/Node/Prisma/JDBC models.
7. Frontend phase cockpit is improved, but still brittle around backend entitlements, phase action contracts, and client/backend route parity.
8. Repository still contains progress reports, todo reports, old migration/final-summary docs, and archive material that can keep re-triggering duplicate audits.

**Highest-risk area:** **security + traceability across lifecycle/generation/preview/artifact flows.** The canonical docs require tenant/workspace/project/artifact/actor traceability, preview trust, governed generator decisions, and phase-scoped lifecycle behavior, but several runtime paths still rely on payload-provided IDs, optional/no-op dependencies, or incomplete controller wiring. 

The current YAPPC docs honestly mark the product as “Active Buildout,” with AI-native maturity 3/10, feature completeness 4/10, and production readiness 2/10; that matches the code evidence rather than contradicting it. 

# 7.2 Root Architectural Blockers

## P0-1 Target snapshot likely does not compile because `PreviewSessionApiController.java` is corrupted

**Why it matters:** No production readiness assessment can pass if the target commit cannot compile.

**Root cause:** The preview controller source appears to contain malformed Java in `decodeSessionToken`, including stray text around `firstNonBlank` and `return objectMapper.readValue(...)`.

**Evidence:** `PreviewSessionApiController.java` contains the preview session implementation, but the fetched source shows malformed code inside `decodeSessionToken`, with broken tokens such as `rivate static String firstNonBlank` and `p   return objectMapper.readValue(...)`. 

**Affected surfaces:** Preview sessions, Observe phase, page-builder preview runtime, build pipeline, Java backend CI.

**Correct target pattern:** Every target commit must pass compile/typecheck before deeper product audit.

**Required fix:** Repair `PreviewSessionApiController.java`, run full Java compile, add source formatting/static-analysis gates, and ensure CI blocks corrupted committed Java.

**Required tests:** `./gradlew :products:yappc:build`, preview controller unit tests, preview auth/session expiry tests, CI formatting/static analysis.

**Cleanup implications:** Remove any generated or manually patched source corruption path. Add “no malformed generated source” validation if any AI/refactor scripts touch Java.

---

## P0-2 Authorization is not canonical route/action authorization

**Why it matters:** YAPPC creates, previews, imports, generates, runs, rolls back, and exports product artifacts. Authentication alone is not enough; every action must be authorized by route, role, workspace, project, artifact, phase, and tenant.

**Root cause:** `YappcApiAuthFilter` authenticates API keys/Bearer tokens and attaches a principal/tenant context, but route-specific permission checks are not centralized. Page artifacts have stronger resource-scope authorization, while generation, artifact graph, preview, and lifecycle APIs are less consistently guarded. 

**Evidence:**

* `YappcHttpServer` wraps most routes with `secureVersioned(authFilter, ...)`, but that is authentication/versioning, not a complete action permission registry. 
* `GenerationApiController` reads `projectId` and `actorId` from the request body for review decisions, while audit uses the authenticated principal separately. 
* `ArtifactGraphController` accepts `productId` and `tenantId` from request payloads for graph query/residual analysis. 
* `PageArtifactController` is the stronger pattern: it checks principal tenant against request tenant, requires workspace/project headers, authorizes resource scope, and enforces read/edit permissions. 

**Affected surfaces:** Lifecycle, generation, generated artifact review, run promote/rollback, artifact graph, source import/decompile, preview sessions, page artifacts, operation-log export.

**Correct target pattern:** A canonical route/action authorization registry generated from OpenAPI + route registry + product permissions. Every route declares method, action, required permission, tenant/workspace/project/artifact scope, audit event, and privacy classification.

**Required fix:** Use `Principal` and server-resolved tenant/workspace/project/resource scope as authoritative. Reject mismatched body/header IDs. Move PageArtifact-style authorization into a reusable YAPPC authorization middleware/service and apply it consistently.

**Required tests:** Route permission matrix, tenant mismatch tests, body/header/principal mismatch tests, workspace/project/artifact denial tests, role downgrade tests, support/delegated-access tests, and “no unclassified route” CI gate.

**Cleanup implications:** Remove scattered per-controller security checks after centralizing the canonical policy.

---

## P0-3 Lifecycle execution persistence breaks project traceability

**Why it matters:** YAPPC’s lifecycle model depends on auditable Project → Product → App progression. Persisting lifecycle execution under the wrong project corrupts governance, reporting, learning loops, and user trust.

**Root cause:** `LifecycleApiController.persistExecutionResult` constructs persistence data manually and sets `projectId` from `payload.intentInput().tenantId()` or `"default-project"` rather than a real project identifier. It then posts to the Node API fire-and-forget, logging failures without affecting the lifecycle response. 

**Evidence:** The lifecycle controller executes the linear phase pipeline and emits DAG timing metadata, but persistence is asynchronous, best-effort, and not transactionally tied to the lifecycle result. 

**Affected surfaces:** Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve, activity feed, governance trace, learning/evolution feedback, execution history, dashboards.

**Correct target pattern:** Lifecycle execution must start from explicit tenant, workspace, project, actor, phase, and source artifact context. Persistence must be durable, idempotent, and traceable before returning success or must return an explicit degraded/failure envelope.

**Required fix:** Add `projectId`, `workspaceId`, `actorId`, `correlationId`, and idempotency key to lifecycle execution contracts. Replace fire-and-forget persistence with durable repository/event/outbox flow. Never derive project identity from tenant ID.

**Required tests:** Lifecycle golden-master tests, persistence failure tests, idempotency/retry tests, activity-feed trace tests, project isolation tests, and execution-result schema tests.

**Cleanup implications:** Remove “default-project” fallback and local manual persistence map assembly once canonical lifecycle event/result models exist.

---

## P0-4 Preview trust boundary is not production complete

**Why it matters:** Preview is a security boundary, especially for generated/imported UI, source imports, component execution, clipboard/download permissions, and semi-trusted/untrusted artifacts.

**Root cause:** The canonical model defines preview trust as a policy boundary, but server wiring creates `PreviewSessionApiController` with only an `ObjectMapper` and secret; it does not inject real audit or authorization services.  

**Evidence:**

* The preview controller supports optional authorization only when `authorizationService != null`; otherwise, project/artifact access is not checked inside the controller beyond authentication. 
* `YappcHttpServer` provides the controller using only `ObjectMapper` and `YAPPC_PREVIEW_SESSION_SECRET`, so the optional authorization path is disabled in this wiring. 
* Missing preview secret returns 503 at request time; production startup does not fail fast for missing preview security dependencies. 

**Affected surfaces:** Observe phase, page-builder preview, generated artifact preview, imported artifact preview, preview tokens, telemetry privacy.

**Correct target pattern:** Preview sessions must be issued only after backend authorization and trust classification checks. Preview runtime must enforce sandbox, CSP, scope, expiry, artifact/project allow-list, and privacy restrictions.

**Required fix:** Inject `YappcAuthorizationService` and durable `AuditLogger`; make preview secret, authorization, audit, CSP/sandbox policy, and trust classifier mandatory in production. Include trust level and data classification in the signed token.

**Required tests:** Preview token issue/validate tests, unauthorized artifact denial, cross-project token denial, expired/tampered token tests, download/clipboard scope tests, CSP/sandbox tests, untrusted preview block tests.

**Cleanup implications:** Remove optional/noop preview dependencies from production wiring.

---

## P0-5 Domain and persistence ownership are still split and overlapping

**Why it matters:** YAPPC must be type-safe and traceable across backend, frontend, generated artifacts, and governance. Overlapping domain models and dual persistence stacks make correctness fragile.

**Root cause:** `DOMAIN_MODEL_REGISTRY.md` explicitly states YAPPC has two domain packages and two persistence stacks, with overlapping `Project`, `Incident`, `Compliance`, `SecurityScan`, and `Alert` concepts. It also says Java JDBC owns workflow/AI state while Node + Prisma owns project/collaboration state, with cross-stack access rules. 

**Evidence:** The architecture says Data-Cloud is the canonical persistence layer and every request carries tenant context, but the domain registry still documents Java JDBC and Node/Prisma split ownership.  

**Affected surfaces:** Projects, lifecycle execution, AI workflows, collaboration, governance, artifacts, GraphQL/REST/API clients.

**Correct target pattern:** One canonical public domain contract per bounded context, with explicit mappers only at boundaries and enforced dependency direction.

**Required fix:** Finish the overlapping entity migration. Define canonical `Project`, `Artifact`, `LifecycleExecution`, `WorkflowRun`, `GeneratedArtifact`, `PreviewSession`, and `GovernanceTrace` contracts. Enforce cross-stack boundaries through ports/events, not ad hoc API calls.

**Required tests:** Mapper tests, duplicate-domain check, forbidden import checks, persistence ownership tests, project identity consistency tests.

**Cleanup implications:** Delete deprecated duplicate domain models only after migration and test coverage are complete.

---

## P1-6 Phase cockpit is directionally good but still brittle and partially frontend-orchestrated

**Why it matters:** The cockpit is the core low-cognitive-load YAPPC experience. It should present phase-native truth, not fragile client orchestration.

**Root cause:** The frontend phase cockpit centralizes eight phase routes and typed services, but it still derives key action behavior client-side, depends on backend-provided entitlements without safe loading guards, and has partial phase semantics.  

**Evidence:**

* `usePhaseCockpitData` deliberately removed fallback tier/flags, but `resolveTenantTier(project)` and `resolveEnabledFlags(project)` throw if backend data is missing. They are called during hook rendering even while `project` can still be undefined. 
* Shape, Observe, Learn, and Evolve primary actions only emit audit events and open supporting surfaces; they do not yet prove complete backend state transitions. 
* Intent primary action navigates locally to a drawer route, while Validate/Generate/Run perform backend actions. 

**Affected surfaces:** `/p/:projectId/:phase`, dashboard actions, phase blockers, suggestions, generate review, run post-actions.

**Correct target pattern:** Phase cockpit should consume one backend phase packet containing persisted state, derived blockers/evidence, suggested actions, governance trace, authorization, feature gates, and executable actions.

**Required fix:** Move phase action availability, entitlement flags, and action contracts into backend phase packet. Frontend should render and invoke typed action IDs, not reconstruct lifecycle rules.

**Required tests:** Eight-phase route traversal, missing entitlement loading tests, partial-data recovery tests, phase packet contract tests, disabled-action tests, role/tier gate tests.

**Cleanup implications:** Collapse duplicated phase config/action logic after canonical phase packet is available.

---

## P1-7 Generate/diff/review contract has drift risk

**Why it matters:** Generated code is one of YAPPC’s most sensitive product flows. Apply/reject/rollback must be contractually precise and provenance-bearing.

**Root cause:** Frontend phase action flow and backend generation controller appear to expose mismatched mental models: the frontend Generate cockpit starts a run and asks for diff review by run ID, while the backend `generate/diff` controller expects a validated spec and existing artifacts object.  

**Evidence:** `PhaseCockpitActionService` calls `yappcApi.generate.run({ projectId, phase: 'GENERATE' })`, then `yappcApi.generate.diff({ runId, diff: 'initial-review' })`. `GenerationApiController.regenerateWithDiff` validates `validatedSpec` and `existingArtifacts` in its request body.  

**Affected surfaces:** Generate cockpit, diff review, generated artifact provenance, apply/reject/rollback, activity/audit trace.

**Correct target pattern:** One generated OpenAPI-backed contract for `generate run`, `diff`, `review`, `apply`, `reject`, `rollback`, and artifact retrieval.

**Required fix:** Align frontend typed client, OpenAPI, backend controller, tests, and UI. Review decisions must use authenticated principal as actor, not body-provided `actorId`.

**Required tests:** Generate run → diff → apply/reject/rollback E2E, client/backend contract tests, provenance tests, actor mismatch denial, rollback idempotency tests.

**Cleanup implications:** Remove legacy diff request shapes and any compatibility wrappers after migration.

---

## P1-8 Artifact graph/compiler APIs trust payload scope too much

**Why it matters:** Artifact compiler/decompiler APIs touch source imports, residual islands, dependency graphs, and semantic merge. These can leak or corrupt cross-tenant/project data if scope is trusted from payload.

**Root cause:** `ArtifactGraphController` is behind authentication but still accepts `productId` and `tenantId` from payloads for query/residual operations without visibly cross-checking the authenticated principal tenant or project/workspace authorization. 

**Evidence:** Canonical YAPPC model requires source imports to validate untrusted input, use governed import APIs, attach residual metadata, block preview/import when runtime health is unavailable, and preserve provenance. 

**Affected surfaces:** Artifact graph ingest/analyze/merge/query, residual analysis, source import, decompile, compiler runtime health, preview trust.

**Correct target pattern:** Artifact graph APIs must resolve tenant/workspace/project from authenticated principal and resource registry, not request body. Compiler/decompiler operations must be job-based, audited, idempotent, provenance-bearing, and trust-classified.

**Required fix:** Add canonical `ArtifactCompilerJob` / `ArtifactGraphScope` model. Require project/artifact authorization, runtime health check, input size/type limits, residual policy, and provenance envelope.

**Required tests:** Tenant mismatch denial, cross-project graph denial, malformed graph input, residual preservation, compiler runtime unavailable, round-trip fidelity, merge conflict tests.

**Cleanup implications:** Replace raw map payload parsing with typed request contracts and generated client schemas.

---

## P1-9 Typed API client is canonical in intent, but migration is incomplete

**Why it matters:** YAPPC must be type-safe end to end. A handwritten “typed” client with raw fetch exceptions and stale concepts can still drift from backend contracts.

**Root cause:** The API client states all new REST calls must use this canonical client, but the same file still contains a raw fetch implementation and broad mixed domains. 

**Evidence:** `client.ts` declares itself the single canonical REST API client and says new REST code must not use raw fetch. In the same file, `projects.updateScoped` uses `fetch` directly. The client also retains `projects.capabilities`, showing older capability terminology remains in active API surface. 

**Affected surfaces:** Frontend services, phase cockpit, projects, lifecycle, generation, canvas, page artifacts, auth.

**Correct target pattern:** Generated OpenAPI client + small product adapters. No ad hoc raw fetch, no stale endpoint names, no untyped `unknown` for production-critical contracts.

**Required fix:** Generate REST types from OpenAPI, split REST/GraphQL ownership, add lint rule forbidding raw fetch outside API infrastructure, migrate stale capability terms.

**Required tests:** Generated client freshness, typecheck, no-raw-fetch lint, OpenAPI route parity, frontend contract tests.

**Cleanup implications:** Shrink `client.ts` into generated clients + typed domain adapters.

---

## P1-10 Observability and audit are uneven

**Why it matters:** YAPPC’s governance model depends on auditable decisions across AI suggestions, generated artifacts, page edits, preview sessions, and lifecycle transitions.

**Root cause:** Some controllers have audit hooks, but defaults often use no-op audit or best-effort logging.

**Evidence:**

* `GenerationApiController` defaults to `AuditLogger.noop()` unless explicitly injected. 
* `PreviewSessionApiController` defaults to `AuditLogger.noop()` and server wiring does not inject a real audit logger.  
* `LifecycleApiController` persists lifecycle execution via fire-and-forget HTTP call and logs persistence failure without failing the lifecycle response. 
* `PageArtifactController` shows a stronger atomic save-with-audit pattern. 

**Affected surfaces:** Generate, preview, lifecycle, page artifacts, Observe, Learn, Evolve, operation logs.

**Correct target pattern:** Production startup should fail if durable audit/metrics/tracing are not configured for governed actions.

**Required fix:** Replace no-op production defaults with explicit local/test-only profiles. Add an audit envelope and correlation ID propagation across all lifecycle/generation/preview/page-artifact flows.

**Required tests:** Missing audit startup failure, correlation ID propagation, audit write failure behavior, generated artifact provenance audit, preview session audit, page operation replay.

**Cleanup implications:** Centralize audit logger provisioning and remove controller-level no-op defaults from production constructors.

---

## P2-11 Accessibility and i18n readiness are partial

**Why it matters:** YAPPC’s canvas, phase cockpit, review panels, and preview surfaces must support keyboard navigation, screen readers, locale-aware text, and accessible complex interactions.

**Root cause:** Some UI surfaces include semantic labels and test IDs, but text is embedded directly in TSX/services and full i18n/a11y coverage is not verified.

**Evidence:** Phase cockpit components include ARIA labels, data recovery panels, and clear action states, but phase copy is hardcoded in the route file. Timestamp formatting uses `toLocaleString`, but translation extraction and full locale strategy are not shown.  

**Affected surfaces:** Phase cockpit, canvas, builder, palette, property inspector, modal/review actions, preview.

**Correct target pattern:** All user-facing text externalized; keyboard-accessible canvas actions; drag/drop alternatives; screen-reader-friendly builder model; locale-aware dates/numbers/messages.

**Required tests:** axe checks, keyboard route tests, builder non-pointer interaction tests, i18n extraction coverage, locale snapshot tests.

**Cleanup implications:** Move embedded copy into typed content/i18n resources.

---

## P2-12 Repository cleanup is still required to stop repeated audit churn

**Why it matters:** Stale progress reports, todo trackers, final-summary docs, migration docs, and archive material make every audit rediscover old context and confuse source of truth.

**Root cause:** YAPPC has canonical docs, but still contains progress/todo/archive/migration artifacts at product root and under docs. Search results show root artifact compiler progress/plan docs, todo-report files, archive reports, migration docs, and final summary docs.     

**Correct target pattern:** Minimal canonical docs, explicit archive policy, no active audit/todo/progress docs in source-of-truth paths.

**Required fix:** Apply cleanup matrix below.

# 7.3 Migration / Completeness Matrix

| Surface                       | Route Registry | Scope Resolver | Permission Guard | Typed API | Canonical Model | Builder Runtime | Compiler/Decompiler | Preview Runtime | Persistence | Privacy/Security | i18n/a11y | Tests | Cleanup | Status                                       |
| ----------------------------- | -------------: | -------------: | ---------------: | --------: | --------------: | --------------: | ------------------: | --------------: | ----------: | ---------------: | --------: | ----: | ------: | -------------------------------------------- |
| Product README / architecture |             🟡 |              ⚫ |                ⚫ |         ⚫ |              🟡 |               ⚫ |                  🟡 |              🟡 |          🟡 |               🟡 |        🟡 |    🟡 |      🟡 | 🟡 Partial                                   |
| Project / Product / App model |             🟡 |             🟡 |               🟡 |        🟡 |              🟡 |               ⚫ |                   ⚫ |               ⚫ |          🔴 |               🟡 |         ⚫ |    🟡 |      🟡 | 🟡 Partial                                   |
| Phase cockpit routes          |             🟡 |             🟡 |               🟡 |        🟡 |              🟡 |               ⚫ |                   ⚫ |               ⚫ |          🟡 |               🟡 |        🟡 |    🟡 |      🟡 | 🟡 Partial                                   |
| Intent phase                  |             🟡 |             🟡 |               🟡 |        🟡 |              🟡 |               ⚫ |                   ⚫ |               ⚫ |          🟡 |               🟡 |        🟡 |    🟡 |      🟡 | 🟡 Partial                                   |
| Shape / canvas phase          |             🟡 |             🟡 |               🟡 |        🟡 |              🟡 |              🟡 |                   ⚫ |              🟡 |          🟡 |               🟡 |        🟡 |    🟡 |      🟡 | 🟡 Partial                                   |
| Validate phase                |             🟡 |             🟡 |               🟡 |        🟡 |              🟡 |               ⚫ |                   ⚫ |               ⚫ |          🟡 |               🟡 |        🟡 |    🟡 |      🟡 | 🟡 Partial                                   |
| Generate phase                |             🟡 |             🔴 |               🔴 |        🟡 |              🟡 |               ⚫ |                  🟡 |              🟡 |          🟡 |               🔴 |        🟡 |    🟡 |      🟡 | 🔴 Not production-ready                      |
| Run phase                     |             🟡 |             🔴 |               🔴 |        🟡 |              🟡 |               ⚫ |                   ⚫ |              🟡 |          🟡 |               🔴 |        🟡 |    🟡 |      🟡 | 🔴 Not production-ready                      |
| Observe / preview             |             🟡 |             🔴 |               🔴 |        🟡 |              🟡 |              🟡 |                  🟡 |              🔴 |          🟡 |               🔴 |        🟡 |    🔴 |      🟡 | 🔴 Not production-ready                      |
| Learn / Evolve                |             🟡 |             🟡 |               🟡 |        🟡 |              🟡 |               ⚫ |                   ⚫ |               ⚫ |          🟡 |               🟡 |        🟡 |    🟡 |      🟡 | 🟡 Partial                                   |
| Page artifacts                |             🟡 |              ✅ |                ✅ |        🟡 |              🟡 |              🟡 |                  🟡 |              🟡 |           ✅ |                ✅ |        🟡 |    🟡 |      🟡 | 🟡 Strongest area, still needs consolidation |
| Artifact graph/compiler       |             🟡 |             🔴 |               🔴 |        🟡 |              🟡 |               ⚫ |                  🟡 |              🟡 |          🟡 |               🔴 |         ⚫ |    🟡 |      🟡 | 🔴 Needs scope hardening                     |
| Preview sessions              |             🟡 |             🔴 |               🔴 |        🟡 |              🟡 |               ⚫ |                   ⚫ |              🔴 |           ⚫ |               🔴 |         ⚫ |    🔴 |      🟡 | 🔴 Compile/security blocker                  |
| API client/contracts          |             🟡 |             🟡 |               🟡 |        🟡 |              🟡 |              🟡 |                  🟡 |              🟡 |          🟡 |               🟡 |        🟡 |    🟡 |      🟡 | 🟡 Partial                                   |
| Domain/persistence            |             🟡 |             🟡 |               🟡 |        🟡 |              🔴 |               ⚫ |                   ⚫ |               ⚫ |          🔴 |               🟡 |         ⚫ |    🟡 |      🟡 | 🔴 Root migration incomplete                 |
| Audit/observability           |             🟡 |             🟡 |               🟡 |         ⚫ |              🟡 |              🟡 |                  🟡 |              🟡 |          🔴 |               🟡 |         ⚫ |    🟡 |      🟡 | 🟡 Uneven                                    |
| Repo cleanup/docs             |              ⚫ |              ⚫ |                ⚫ |         ⚫ |              🟡 |               ⚫ |                  🟡 |               ⚫ |           ⚫ |                ⚫ |         ⚫ |     ⚫ |      🔴 | 🔴 Needs cleanup phase                       |

# 7.4 File-Level Gaps

| Root Blocker | File / Path                                                                                               | What is wrong or missing                                                                                                                        | Required fix                                                                                                           | Required tests                                                 |
| ------------ | --------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| P0-1 / P0-4  | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/PreviewSessionApiController.java` | Source appears syntactically corrupted; controller defaults to noop audit and optional authorization.                                           | Repair Java source; require authz/audit injection; fail startup when preview secret/authz/audit missing in production. | Compile, preview token, authz, expiry, tamper, audit tests.    |
| P0-2 / P0-4  | `YappcHttpServer.java`                                                                                    | Routes are authenticated but not backed by a route/action permission registry; preview controller is provided without authz/audit dependencies. | Add canonical route/action registry and production bootstrap validator.                                                | No-unclassified-route test, startup dependency matrix.         |
| P0-2         | `YappcApiAuthFilter.java`                                                                                 | Good fail-closed credential setup, but resolves roles without enforcing route-specific RBAC.                                                    | Integrate permission registry and principal scope enforcement.                                                         | Role/action allow/deny tests.                                  |
| P0-3         | `LifecycleApiController.java`                                                                             | Persists project execution result using tenant ID/default project and fire-and-forget HTTP call.                                                | Add explicit project/workspace/actor/idempotency contract and durable persistence/outbox.                              | Lifecycle trace, persistence failure, retry/idempotency tests. |
| P1-7 / P1-10 | `GenerationApiController.java`                                                                            | Defaults to noop audit, review body accepts actor ID, artifact retrieval lacks visible tenant/project authz.                                    | Use principal actor, resource authorization, durable audit.                                                            | Actor mismatch, artifact scope, audit failure tests.           |
| P1-8         | `ArtifactGraphController.java`                                                                            | Accepts tenant/product IDs from body without visible principal cross-check.                                                                     | Resolve tenant/scope from principal/resource registry; typed request contracts.                                        | Tenant mismatch, graph access, malformed input tests.          |
| P1-6         | `usePhaseCockpitData.ts`                                                                                  | Throws when backend tier/flags are missing, likely even before project load.                                                                    | Gate entitlement resolution until project loaded; render explicit missing-entitlement degraded state.                  | Loading, missing entitlement, degraded-state tests.            |
| P1-6         | `_phaseCockpit.tsx`                                                                                       | Strong UI structure, but phase actions remain mixed: local navigation, audit-only surfaces, backend actions.                                    | Move action availability/execution semantics to backend phase packet.                                                  | Eight-phase cockpit E2E and action-state tests.                |
| P1-9         | `frontend/web/src/lib/api/client.ts`                                                                      | Claims canonical no-raw-fetch policy but still contains raw fetch and stale capability terminology.                                             | Generate client from OpenAPI; lint no raw fetch; remove stale terms.                                                   | Generated-client freshness, lint, typecheck.                   |
| P0-5         | `DOMAIN_MODEL_REGISTRY.md` + domain packages                                                              | Active registry documents overlapping domain models and dual persistence stacks.                                                                | Finish model consolidation and enforce boundaries.                                                                     | Duplicate-domain/import-boundary tests.                        |
| P2-12        | Root/docs todo/progress/archive files                                                                     | Stale progress/audit/todo docs can pollute future audits.                                                                                       | Merge/archive/delete per cleanup plan.                                                                                 | Docs source-of-truth check.                                    |

# 7.5 Prioritized Implementation Sequence

## 1. Build integrity and source corruption fix

**Goal:** Ensure target commit can compile before product hardening.

**Main files:** `PreviewSessionApiController.java`, Java backend build config, CI.

**Expected outcome:** No malformed committed source; backend build passes.

**Required tests:** Java compile, unit tests, static analysis, formatting.

**Acceptance criteria:** `./gradlew :products:yappc:build` passes from clean checkout.

---

## 2. Canonical route/action authorization

**Goal:** Convert authentication-only route protection into permission-aware, scope-aware authorization.

**Main files:** `YappcHttpServer.java`, `YappcApiAuthFilter.java`, controllers, OpenAPI, authz service.

**Expected outcome:** Every route has method/action/scope/permission/audit metadata.

**Required tests:** Route matrix, role matrix, tenant mismatch, project/artifact denial tests.

**Acceptance criteria:** No YAPPC route can be registered without classification.

---

## 3. Lifecycle identity and persistence hardening

**Goal:** Make lifecycle execution traceable and durable.

**Main files:** `LifecycleApiController.java`, lifecycle service/repository, Node API integration, Data-Cloud/outbox adapter.

**Expected outcome:** Lifecycle result is persisted under correct tenant/workspace/project/actor/correlation ID.

**Required tests:** Golden lifecycle execution, persistence failure, retry, idempotency.

**Acceptance criteria:** No `default-project` or tenant-as-project fallback remains.

---

## 4. Phase cockpit contract hardening

**Goal:** Make cockpit fully backend-packet-driven with safe degraded states.

**Main files:** `_phaseCockpit.tsx`, `usePhaseCockpitData.ts`, phase services, backend phase packet API.

**Expected outcome:** UI renders safely during loading/partial data and invokes typed backend action IDs.

**Required tests:** Eight phases, missing entitlement, disabled actions, degraded preview/activity.

**Acceptance criteria:** No phase action depends on untrusted frontend-only lifecycle rules.

---

## 5. Generate/review/run hardening

**Goal:** Align generate/diff/apply/reject/rollback contracts.

**Main files:** `PhaseCockpitActionService.ts`, `GenerationApiController.java`, `client.ts`, OpenAPI.

**Expected outcome:** Generate flow is end-to-end typed, provenance-bearing, auditable, and actor-safe.

**Required tests:** Generate run → diff → apply/reject/rollback, actor mismatch denial, rollback idempotency.

**Acceptance criteria:** Frontend and backend agree on request/response shapes.

---

## 6. Preview trust/session security

**Goal:** Make preview a real policy boundary.

**Main files:** `PreviewSessionApiController.java`, `YappcHttpServer.java`, preview runtime, page artifact model.

**Expected outcome:** Preview sessions require authz, trust classification, signed scope, expiry, audit, and sandbox policy.

**Required tests:** Token tamper/expiry, cross-project denial, untrusted preview block, CSP/sandbox.

**Acceptance criteria:** Production cannot start with preview enabled but missing secret/authz/audit/sandbox policy.

---

## 7. Page artifact and builder runtime consolidation

**Goal:** Use PageArtifactController’s stronger atomic/audit/scope model as canonical for builder persistence.

**Main files:** `PageArtifactController.java`, page artifact repositories, frontend builder adapters.

**Expected outcome:** Builder document saves, review decisions, rollbacks, and operation log exports are all governed and replayable.

**Required tests:** Optimistic concurrency, rollback metadata, operation-log replay, governance decision tests.

**Acceptance criteria:** All page-builder mutations use atomic save-with-audit.

---

## 8. Artifact compiler/decompiler governance

**Goal:** Make artifact graph/source import/decompile safe and traceable.

**Main files:** `ArtifactGraphController.java`, artifact graph service, source import APIs, compiler runtime health.

**Expected outcome:** Tenant/project scope comes from authenticated resource context; residuals/provenance/trust metadata are preserved.

**Required tests:** Scope mismatch denial, residual preservation, compiler health unavailable, round-trip fidelity.

**Acceptance criteria:** No compiler endpoint trusts body-provided tenant/product identity.

---

## 9. Domain/persistence consolidation

**Goal:** Remove duplicate domain and persistence ambiguity.

**Main files:** `DOMAIN_MODEL_REGISTRY.md`, `libs/java/yappc-domain`, backend/api/domain, Prisma schema, Java JDBC models.

**Expected outcome:** One canonical contract per bounded context with explicit mappers at boundaries.

**Required tests:** Import-boundary tests, mapper tests, duplicate entity check.

**Acceptance criteria:** Deprecated duplicate entities have migration tickets, tests, and removal plan.

---

## 10. Typed API / OpenAPI / generated client migration

**Goal:** End handwritten drift.

**Main files:** `client.ts`, OpenAPI docs/contracts, backend controllers, frontend services.

**Expected outcome:** Generated clients + small adapters; no raw fetch outside infrastructure.

**Required tests:** OpenAPI validation, generated-client compile, no-raw-fetch lint.

**Acceptance criteria:** Every UI production REST call maps to contract-generated method.

---

## 11. Privacy/security/i18n/a11y/observability gates

**Goal:** Make cross-cutting concerns native, not afterthoughts.

**Expected outcome:** Privacy redaction, audit, metrics, traces, i18n extraction, keyboard/screen-reader coverage.

**Required tests:** axe, keyboard, i18n extraction, audit/trace propagation, privacy redaction.

---

## 12. Repository cleanup and canonical docs consolidation

**Goal:** Remove audit noise and stale context.

**Expected outcome:** Minimal canonical doc set; stale reports archived/deleted; generated scratch outputs removed.

**Required tests:** Docs source-of-truth check, no root progress/todo report check, dead-code scan.

# 7.6 Regression and Release Gates

Minimum gates before production readiness:

* [ ] Target commit compiles: `./gradlew :products:yappc:build`.
* [ ] Frontend typecheck/lint/test passes.
* [ ] Full route traversal from YAPPC entry point and `/p/:projectId/{intent,shape,validate,generate,run,observe,learn,evolve}`.
* [ ] Workspace/project/role/tier/access matrix.
* [ ] Principal/header/body tenant mismatch denial tests.
* [ ] Project/artifact isolation tests.
* [ ] Lifecycle execution golden-master with correct project/workspace/actor trace.
* [ ] Lifecycle persistence failure and idempotent retry tests.
* [ ] Generate → diff → apply/reject/rollback E2E tests.
* [ ] Run → promote/rollback/observe handoff tests.
* [ ] Preview token issue/validate/expire/tamper tests.
* [ ] Preview trust-level block tests for semi-trusted/untrusted artifacts.
* [ ] Page artifact save/load/review/export operation-log tests.
* [ ] Artifact graph tenant/project mismatch tests.
* [ ] Source import/decompile residual preservation tests.
* [ ] No noop audit logger in production profile.
* [ ] No raw fetch outside API infrastructure.
* [ ] No fake/demo/stub production path unless explicitly feature-flagged.
* [ ] i18n extraction coverage for user-facing copy.
* [ ] Accessibility coverage for cockpit, modals, builder interactions, keyboard flows.
* [ ] Dead-code/stale-doc cleanup validation.
* [ ] No duplicate domain model or persistence ownership violations.

# Repository Cleanup Plan

| Priority | Classification          | Path                                                                                                      | Reason                                                                | Evidence                                                                       | Safe Fix                                                                                                          | Tests/Validation                           |
| -------- | ----------------------- | --------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- | ------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| P0       | Replace                 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/PreviewSessionApiController.java` | Source appears malformed and preview security wiring is incomplete.   | Fetched source shows corrupted Java and optional authz/audit.                  | Repair source; inject authz/audit; add production startup guard.                                                  | Compile + preview security tests.          |
| P0       | Replace                 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/LifecycleApiController.java`      | Wrong project trace and fire-and-forget persistence.                  | Lifecycle persistence uses tenant/default project and async HTTP persistence.  | Add durable lifecycle execution repository/outbox.                                                                | Lifecycle golden-master and failure tests. |
| P0       | Merge/Replace           | `YappcHttpServer.java` route wiring                                                                       | Authenticated routes lack canonical route/action permission matrix.   | Server registers many secured routes but preview is weakly wired.              | Introduce route/action registry and boot validator.                                                               | No-unclassified-route test.                |
| P1       | Merge                   | `products/yappc/DOMAIN_MODEL_REGISTRY.md`                                                                 | Active doc confirms overlapping domain models and persistence stacks. | Domain registry documents duplicate bounded contexts.                          | Keep temporarily as migration tracker; merge final rules into canonical architecture/domain docs after migration. | Duplicate-domain check.                    |
| P1       | Archive/Merge           | `products/yappc/ARTIFACT_COMPILER_PROGRESS.md`                                                            | Progress doc at product root should not be source of truth.           | Search result confirms root progress doc.                                      | Merge durable decisions into artifact compiler design; archive progress.                                          | Docs source-of-truth check.                |
| P1       | Archive/Merge           | `products/yappc/ARTIFACT_COMPILER_IMPLEMENTATION_PLAN_REVIEW.md`                                          | Review/plan doc at product root creates audit noise.                  | Search result confirms root implementation-plan review doc.                    | Merge unresolved decisions into canonical artifact compiler doc; archive/delete after validation.                 | Docs lint.                                 |
| P1       | Archive/Delete          | `products/yappc/docs/todo-reports/hardening-1.md`                                                         | Todo report should not be active source of truth.                     | Search result confirms todo report.                                            | Convert open items into issue tracker or canonical implementation tracker, then archive/delete.                   | No active todo-report check.               |
| P1       | Archive/Delete          | `products/yappc/docs/todo-reports/hardening-1-tracker.md`                                                 | Tracker duplicates source-of-truth planning.                          | Search result confirms tracker.                                                | Move active tasks to canonical backlog, archive/delete file.                                                      | No active todo-report check.               |
| P2       | Archive                 | `products/yappc/docs/archive/YAPPC_BUILD_FIX_FINAL_REPORT.md`                                             | Already archive; ensure excluded from canonical docs.                 | Search result confirms archive report.                                         | Keep in archive only, excluded from audit source-of-truth.                                                        | Docs source filter.                        |
| P2       | Merge/Archive           | `products/yappc/FLOWSTAGE_MIGRATION.md`                                                                   | Migration doc may be stale after lifecycle model standardization.     | Search result confirms migration doc.                                          | Merge relevant mapping into canonical lifecycle docs; archive once complete.                                      | Lifecycle terminology check.               |
| P2       | Review / Keep or Delete | `products/yappc/scripts/reduce-todos.py`, `products/yappc/scripts/fix-agent-imports.sh`                   | Migration/cleanup helper scripts may be stale.                        | Search results show these scripts.                                             | Keep only if wired to current maintenance workflow; otherwise delete after reference scan.                        | Script reference scan + CI.                |
| P2       | Archive                 | `products/yappc/tools/vscode-extension/YAPPC_FINAL_SUMMARY.md`                                            | Final-summary docs often become stale audit context.                  | Search result confirms final summary doc.                                      | Archive or merge stable content into extension README.                                                            | Docs source-of-truth check.                |

## Canonical Docs Matrix

| Doc                                                          |        Keep |   Merge | Archive |            Delete | Notes                                                                                                         |
| ------------------------------------------------------------ | ----------: | ------: | ------: | ----------------: | ------------------------------------------------------------------------------------------------------------- |
| `products/yappc/README.md`                                   |           ✅ |         |         |                   | Keep as product entry/status doc, but align module status table with “active buildout” reality.               |
| `products/yappc/docs/ARCHITECTURE.md`                        |           ✅ |         |         |                   | Keep canonical architecture; update once route/action auth and persistence are fixed.                         |
| `products/yappc/docs/architecture/YAPPC_CANONICAL_MODELS.md` |           ✅ |         |         |                   | Keep as canonical model reference; expand with route/action contract and preview trust implementation status. |
| `products/yappc/DOMAIN_MODEL_REGISTRY.md`                    | ✅ temporary | ✅ later |         |                   | Keep until duplicate domain migration completes, then merge into architecture/domain docs.                    |
| `docs/API_REFERENCE.md` / OpenAPI contracts                  |           ✅ |         |         |                   | Must become source for generated frontend clients and backend route parity.                                   |
| `docs/security/*`                                            |           ✅ |       ✅ |         |                   | Consolidate security/privacy into one canonical product security doc plus operational runbook.                |
| `docs/operations/*`                                          |           ✅ |       ✅ |         |                   | Keep ops/runbook docs, remove overlapping status reports.                                                     |
| `docs/testing/*`                                             |           ✅ |       ✅ |         |                   | Keep canonical test strategy and release gates.                                                               |
| `ARTIFACT_COMPILER_PROGRESS.md`                              |             |       ✅ |       ✅ |                   | Merge stable design decisions, archive progress log.                                                          |
| `ARTIFACT_COMPILER_IMPLEMENTATION_PLAN_REVIEW.md`            |             |       ✅ |       ✅ |                   | Merge actionable gaps into canonical artifact compiler design/backlog.                                        |
| `FLOWSTAGE_MIGRATION.md`                                     |             |       ✅ |       ✅ |                   | Merge terminology into lifecycle docs; archive after route/model migration.                                   |
| `docs/todo-reports/*`                                        |             |       ✅ |       ✅ | ✅ after migration | Move open tasks to tracker/issues; remove from source-of-truth docs.                                          |
| `docs/archive/*`                                             |             |         |       ✅ |                   | Keep only as archive; explicitly exclude from future audits.                                                  |
| `tools/vscode-extension/YAPPC_FINAL_SUMMARY.md`              |             |       ✅ |       ✅ |                   | Merge stable extension details into extension README; archive stale final report.                             |

## Final Cleanup Checklist

* [ ] Legacy/stale YAPPC code removed or explicitly justified.
* [ ] Malformed preview controller fixed and compile gate added.
* [ ] Temporary/generated/progress files removed or archived.
* [ ] Old audit/TODO docs removed from active source-of-truth paths.
* [ ] Canonical docs consolidated.
* [ ] Duplicate domain models migration plan executed.
* [ ] Duplicate API client and raw fetch paths removed.
* [ ] Dead routes/components/services removed.
* [ ] Production stubs/noop audit/demo paths removed or feature-flagged.
* [ ] Obsolete migrations archived.
* [ ] Tests updated after cleanup.
* [ ] Build/lint/typecheck/test pass after cleanup.
* [ ] No hidden fallback runtime paths remain.
* [ ] No duplicate lifecycle execution persistence paths remain.
* [ ] No duplicate builder/preview/compiler contract models remain.
* [ ] No product-specific logic leaks into shared/platform layers without explicit adapter seam.

Bottom line: YAPPC is moving in the right architectural direction, especially around canonical lifecycle phases, phase cockpit structure, page artifact governance, and explicit implementation-status honesty. It is **not** production-ready at this commit. The next improvement pass should not create another broad TODO list; it should fix the P0 build/security/traceability blockers first, then consolidate contracts and cleanup stale repository artifacts.
