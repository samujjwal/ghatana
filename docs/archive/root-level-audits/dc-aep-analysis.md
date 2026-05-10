## Data Cloud + Shared Libraries Audit

Target commit: `98a6097b8a163ba06abb000a1c9151d9c43ba6ee`
Repo: `samujjwal/ghatana`

I verified the requested commit exists and is titled `refactor 44`. This audit is based on the repository snapshot and directly inspected product/shared-library code and canonical Data Cloud docs. I did **not** run the build/test suite locally, so build/test pass/fail is marked as **Not verified from current execution**. 

---

# 1. Production Readiness Verdict

**Production ready: No**

**Confidence level: Medium-high** for architecture, code-structure, route, authorization, connector, runtime-truth, UI, and cleanup findings. **Medium** for runtime behavior that would require executing the full suite, since build/tests were not run.

## Main blockers

1. Backend authorization is improved but still not complete or contract-generated across every route/action.
2. Connector credential lifecycle is not production-safe enough.
3. Runtime Truth migration from “capabilities” to “surfaces” is incomplete.
4. Action Plane/AEP boundary is still mixed across routes, UI labels, and runtime composition.
5. Several production-critical dependencies still have local/in-memory/no-op/fallback behavior.
6. Shared-library boundaries are documented well, but enforcement and cleanup are incomplete.
7. Contract duplication and SDK validation still have drift risk.

## Highest-risk area

**Tenant/authorization + connector credential lifecycle.**

The canonical docs position Data Cloud as an AI-native operational data fabric and explicitly state that AEP is the runtime implementation behind the Action Plane, not a separate customer-facing product boundary. The code is moving in that direction, but the implementation has not fully caught up. 

---

# 2. Root Architectural Blockers

## P0-1 — Authorization and tenant isolation are not yet canonical across all routes

**Why it matters:** Data Cloud is an enterprise multi-tenant data layer. Every route must have deterministic tenant resolution, authorization, policy, and audit behavior.

**Root cause:** Authorization is split across endpoint sensitivity classification, a small route/action registry, frontend shell disclosure, path-prefix heuristics, and handler-level annotations/comments.

**Evidence:**
The backend security filter is strong in structure: it authenticates API keys/JWT/cookies, binds tenant context, consults policy for critical routes, audits sensitive/critical routes, and rejects JWT/header tenant mismatch. But it still allows a `default` fallback tenant in non-strict mode and relies on a limited explicit route registry plus path-prefix inference. 

The explicit route/action registry covers only a small set of routes such as governance purge/redact, learning approve/reject, model promote, plugin upgrade, pipeline execute, entity write/delete, and event append. It does **not** comprehensively cover connector enable/disable/rotate/sync/delete, alert remediation, settings, autonomy, context mutation, plugin enable/disable, data-product subscription, or AI action routes. 

The endpoint sensitivity classifier marks some major prefixes as sensitive/critical, but connector, settings, plugin, autonomy, context, and several AI/operations routes are not comprehensively modeled. Unmatched `/api/v1/*` GETs become `INTERNAL`; unmatched writes can fall through to weaker behavior unless caught elsewhere. 

The UI correctly documents that shell roles are only for disclosure, not backend authorization, but shell role is stored client-side and must never be treated as authority. 

**Affected surfaces:** Connectors, settings, plugins, autonomy, alerts, AI operations, context, data products, federated query, route compatibility aliases.

**Correct target pattern:** Generate a complete backend route/action authorization matrix from canonical contracts and route registry. No route should rely only on prefix heuristics.

**Required fix:**
Create a canonical route/action/security registry that includes every route from `DataCloudRouterBuilder`, including method, sensitivity, required role, policy key, audit event type, tenant scope, and privacy classification. Make production profile fail if any route is unclassified.

**Required tests:**
Route matrix tests, tenant mismatch tests, connector admin-only tests, policy fail-closed tests, audit event coverage tests, and “no unclassified route” CI gate.

**Cleanup implication:**
Remove duplicated sensitivity logic and collapse route access, endpoint sensitivity, OpenAPI security declarations, and UI route disclosure into one generated source of truth.

---

## P0-2 — Connector credential handling is not production-safe

**Why it matters:** Data Cloud connectors are the trust boundary between customer systems and the data fabric. Credential handling must be vault-backed, encrypted, auditable, and never persisted as raw request payload.

**Root cause:** Connector metadata and credentials are stored through generic entity persistence without a dedicated secret reference model.

**Evidence:**
`DataSourceRegistryHandler` stores connector records in `dc_connections`. On registration, it copies the request payload into persisted data. On credential rotation, if the payload contains `credentials`, it writes that `credentials` field into the connection entity. The response view hides raw credentials, but the persistence path still stores them. 

The same handler validates only basic `name` and `type` fields and supports types such as PostgreSQL, MySQL, MongoDB, S3, REST API, Kafka, Snowflake, BigQuery, and custom. 

When no `DataFabricConnector` is wired, sync can set the connection state to `SYNCING` / `sync_pending` even though there is no verified durable worker available to complete it. 

**Affected surfaces:** `/connectors`, connector registration, test, enable/disable, credential rotation, sync, schema discovery, Data Fabric metrics.

**Correct target pattern:**
Persist only secret references, credential metadata, rotation timestamps, and vault handles. Never store raw credentials in the entity store. Connector sync should create durable jobs only when a real connector runtime and worker are available.

**Required fix:**
Introduce a `ConnectorSecretRef` model, secret provider abstraction, redaction rules, rotation workflow, connector job model, idempotency key, and durable sync status lifecycle.

**Required tests:**
Plaintext credential scan, secret-reference persistence tests, credential redaction tests, connector admin authorization tests, sync job lifecycle tests, fabric-unavailable tests, and audit event tests.

**Cleanup implication:**
Remove direct `credentials` storage from connector entities. Split connector metadata, secrets, runtime state, sync jobs, schema snapshots, and health status.

---

## P0-3 — Runtime Truth migration is incomplete

**Why it matters:** Runtime Truth is supposed to be the authoritative source for live/degraded/unavailable state across planes and surfaces. If the UI, backend, OpenAPI, and SDK disagree, users see incorrect availability and routes can behave unpredictably.

**Root cause:** The codebase is mid-migration from “capabilities” to “surfaces.”

**Evidence:**
The README says the canonical runtime truth endpoint is `GET /api/v1/surfaces` and that `/api/v1/capabilities` has been removed. 

The UI service fetches `/surfaces`, but still contains a compatibility layer named `CapabilityStatus`, `CapabilitySignal`, `fetchCapabilityRegistry`, `useCapabilityRegistry`, and `getCapabilitySignal`. 

The router exposes canonical `/api/v1/surfaces`, but also retains a deprecated `withCapabilityRoutes` method. 

The commit diff moved OpenAPI from the old docs path to `products/data-cloud/contracts/openapi/data-cloud.yaml` and changed response naming from `capabilities` to `surfaces`, but this also means contract/backend/UI parity must be strictly validated. 

**Affected surfaces:** Runtime Truth page, route gates, SDKs, OpenAPI, UI route availability, operations dashboard.

**Correct target pattern:**
One Runtime Truth schema and one generated client model. No capability compatibility types in active product code.

**Required fix:**
Delete or isolate capability compatibility under an explicit migration module. Add a route/surface truth generator that compares OpenAPI, backend route registry, `SurfaceRegistryHandler`, UI gates, and generated SDKs.

**Required tests:**
OpenAPI/backend/UI schema parity, `/surfaces` envelope validation, route-to-surface coverage, no `/capabilities` reference check, generated SDK compile tests.

**Cleanup implication:**
Remove capability terminology from active code after migration; keep only archived docs if needed.

---

## P0-4 — Action Plane / AEP boundary is still mixed

**Why it matters:** The canonical architecture says Data Cloud is one product and AEP is the implementation behind the Action Plane. Product routes and UI should not expose implementation-era boundaries.

**Root cause:** The migration has updated docs faster than runtime/API/UI boundaries.

**Evidence:**
The canonical plane architecture explicitly says not to position the product as “Data Cloud plus AEP,” and defines AEP as the runtime implementation behind the Action Plane. 

`DataCloudRouterBuilder` itself says canonical Action Plane routes should live under `/api/v1/action/*`, but the actual registered routes still include root-level action/runtime surfaces such as pipelines, memory, brain, learning, analytics, models, features, AI operations, workflows, autonomy, and plugins. 

The UI route file still labels a section as “AEP Integration Pages” and exposes pages such as Event Explorer, Memory Plane Viewer, Entity Browser, Context Explorer, Data Fabric, and Agent Catalog. 

**Affected surfaces:** Pipelines, agents, learning, reviews, plugins, memory, brain, autonomy, events, API documentation, SDKs, UI navigation.

**Correct target pattern:**
Action Plane is a first-class plane under Data Cloud, with canonical product routes and compatibility redirects where needed.

**Required fix:**
Define canonical Action Plane route namespace, map old routes to new routes, update OpenAPI, SDKs, UI routes, navigation labels, docs, and tests.

**Required tests:**
Route alias tests, API backward-compat tests, OpenAPI route namespace tests, UI navigation tests, no “AEP as product” terminology check.

**Cleanup implication:**
Move AEP-named docs/contracts into compatibility/archive or rename to Action Plane where active.

---

## P0-5 — Production-critical dependencies still fall back to local/in-memory/no-op behavior

**Why it matters:** Enterprise Data Cloud cannot silently run without durable settings, idempotency, audit, metrics, transaction boundaries, or AI provider configuration.

**Root cause:** The server is flexible for local/embedded mode, but production fail-fast enforcement is incomplete.

**Evidence:**
`DataCloudHttpServer` has many optional dependencies and fallback comments: no-op metrics by default, in-memory settings store by default, in-memory idempotency fallback, optional transaction manager where multi-step writes execute without transaction boundaries, optional audit service where audit can be skipped, optional OpenSearch/export/report/AI services, and `strictTenantResolution` defaults to false. 

`AiAssistHandler` supports production fail-closed behavior, but `productionMode` defaults to false and heuristic output is allowed when no completion service is configured unless production mode is explicitly enabled. 

**Affected surfaces:** Entity writes, events, pipelines, reports, AI assist, governance, settings, idempotency, observability.

**Correct target pattern:**
Local profile may use fallbacks. Staging/production must fail fast unless required durable dependencies are configured.

**Required fix:**
Create a production bootstrap validator that rejects production startup when strict tenant resolution, audit, metrics, durable settings, durable idempotency, transaction manager, and required stores are absent.

**Required tests:**
Production bootstrap failure tests, local fallback tests, runtime truth degraded-state tests, no-op dependency detection tests.

**Cleanup implication:**
Centralize all profile-dependent fallback rules and remove scattered optional behavior from handlers.

---

## P1-6 — UI route model has dead-link and compatibility sprawl risk

**Why it matters:** Data Cloud should be low-cognitive-load and outcome-first. Route sprawl and dead routes make the product harder to use and harder to test.

**Root cause:** The UI has canonical routes plus many permanent compatibility routes, while some actions navigate to routes that are not defined.

**Evidence:**
The route file defines canonical routes for Home, Data, Pipelines, Query, Trust, Insights, Alerts, Operations, Events, Memory, Entities, Context, Fabric, Agents, Settings, Plugins, and Connectors. It also keeps many compatibility routes such as dashboard, hub, collections, datasets, lineage, quality, workflows, sql, governance, brain, dashboards, and cost. 

`DataConnectorsPageWrapper` navigates to `/connectors/new` and `/connectors/:id/edit`, but the inspected route tree only defines `path: 'connectors'` without nested create/edit routes. 

**Affected surfaces:** Connectors, navigation, deep links, route tests, onboarding, product shell.

**Correct target pattern:**
One generated route truth matrix that maps canonical routes, redirects, disabled states, runtime surface gates, and all UI actions.

**Required fix:**
Add connector create/edit routes or remove those actions. Convert compatibility routes to explicit redirect records with owners, expiration policy, and tests.

**Required tests:**
Playwright full navigation traversal, dead-link detector, route contract tests, runtime disabled surface tests.

**Cleanup implication:**
Remove compatibility routes that are no longer needed; keep only intentional redirect aliases.

---

## P1-7 — AI/automation is broad but not yet reliably evidence-gated

**Why it matters:** The product vision requires pervasive AI/ML that is transparent, trusted, and only asks humans when needed. Heuristic fallback without strong production gating can create false trust.

**Root cause:** AI assist routes are broad, but fallback, confidence, provenance, and human-review enforcement are not uniformly proven.

**Evidence:**
`AiAssistHandler` states that when AI service is unavailable, suggestions can default to static rule-based heuristics with `ai.fallback=true` and low confidence. Production mode can return 503 instead, but it defaults false. 

The handler includes many AI operation routes and records recommendation metrics/action records when configured, but full end-to-end evidence/provenance from AI suggestion to human approval to applied action was **Not verified from current code/docs**.

**Affected surfaces:** Query assist, schema inference, pipeline draft/refine, connector mapping, governance recommendations, next-best action, AI advisories.

**Correct target pattern:**
AI outputs must carry confidence, evidence, provenance, source context, human-review requirement, and explicit action state.

**Required fix:**
Add AI action lifecycle model: suggested → reviewed → approved/rejected → applied → rolled back. Require evidence and policy checks before mutating actions.

**Required tests:**
No heuristic output in production, confidence threshold tests, evidence-required tests, human override tests, AI action audit tests, rollback tests.

**Cleanup implication:**
Separate advisory AI from mutating automation. Do not let heuristic helpers sit in production mutation paths.

---

## P1-8 — Contract and generated-client architecture still has duplication/drift risk

**Why it matters:** Data Cloud depends on stable contracts for SDKs, UI, backend, integrations, and shared libraries.

**Root cause:** Canonical contracts have moved, but old or duplicate OpenAPI copies still exist in test/resource locations.

**Evidence:**
The README identifies canonical contracts under `products/data-cloud/contracts/openapi/data-cloud.yaml` and `action-plane.yaml`, with `aep.yaml` as a compatibility contract. 

Search results show additional OpenAPI copies under `products/data-cloud/delivery/api-contract-tests/openapi.yaml` and `platform/contracts/src/test/resources/data-cloud-openapi.yaml`, creating drift risk unless they are generated snapshots or explicitly validated.   

The commit changed SDK validation to check generated artifact existence and run SDK module checks, but the diff indicates the previous generated Java SDK compile validation was removed from that CI step. 

**Affected surfaces:** OpenAPI, SDKs, UI API types, contract tests, platform contracts, Action Plane compatibility.

**Correct target pattern:**
One canonical OpenAPI source. All copies are generated artifacts with drift checks, or removed.

**Required fix:**
Make `contracts/openapi/data-cloud.yaml` the only editable source. Generate all test resources and SDKs from it. Compile Java/TS/Python SDKs, not just verify files exist.

**Required tests:**
OpenAPI drift check, generated SDK compile/typecheck/import tests, UI generated-client usage tests, compatibility contract equivalence tests.

**Cleanup implication:**
Delete or mark generated all duplicate specs; remove hand-maintained OpenAPI copies.

---

## P1-9 — Shared-library boundary is documented, but enforcement is incomplete

**Why it matters:** Shared libraries should be reusable abstractions, not a dumping ground for Data Cloud-specific logic or stale AEP integration code.

**Root cause:** The architecture documents the right boundary rules, but code enforcement and cleanup are still in progress.

**Evidence:**
The SPI/platform guide states that `platform/java/*` must be product-agnostic with zero `com.ghatana.datacloud` references, `planes/shared-spi` must contain contracts only and never implementations, and runtime composition must be a leaf wiring module. 

The plane architecture identifies platform modules requiring review/split decisions, including agent-core, workflow, messaging, AI integration, data governance, and platform contracts. 

The UI package itself contains a note that dependency audit is required to remove unused workspace dependencies and avoid library sprawl. 

**Affected surfaces:** Platform Java modules, shared SPI, product UI dependencies, contracts, workflow/action/agent abstractions.

**Correct target pattern:**
Architecture tests enforce product/shared boundaries. Shared libraries are generic, minimal, and independently tested.

**Required fix:**
Add ArchUnit/dependency graph checks for Java and import-boundary checks for TS. Audit every shared dependency used by Data Cloud UI/runtime.

**Required tests:**
No Data Cloud imports in platform modules, no implementations in shared SPI, no UI imports from backend internals, no unused workspace dependencies.

**Cleanup implication:**
Move Data Cloud/Action-specific types out of platform modules; delete unused workspace dependencies.

---

## P1-10 — Repository cleanup is still needed to avoid repeated audit noise

**Why it matters:** Repeated audits rediscover stale docs, generated artifacts, old TODO files, and compatibility leftovers.

**Root cause:** Canonical docs are now clear, but stale artifacts still exist.

**Evidence:**
The README lists a canonical doc set, which is good. 

Search results show archived audit/implementation docs, a root-level `dc-aep-analysis.md`, and a committed UI `test_results.txt`.  

**Affected surfaces:** Docs, tests, generated artifacts, audits, onboarding, developer workflow.

**Correct target pattern:**
Minimal canonical docs, archived docs clearly excluded from source of truth, no committed test output, no root scratch files.

**Required fix:**
Apply repository cleanup plan below.

**Required tests:**
Documentation truth check, no generated test output check, no root scratch markdown check, no stale audit references.

---

# 3. Migration / Completeness Matrix

| Surface                       | Product Boundary | Shared Abstraction | Tenant/Auth | Contract | Runtime Truth | Privacy/Security | AI/Automation | Tests | Cleanup | Status                                           |
| ----------------------------- | ---------------: | -----------------: | ----------: | -------: | ------------: | ---------------: | ------------: | ----: | ------: | ------------------------------------------------ |
| App shell / Home              |               🟡 |                 🟡 |          🟡 |       🟡 |            🟡 |               🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial                                       |
| Data Explorer / entities      |               🟡 |                 🟡 |          🟡 |       🟡 |            🟡 |               🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial                                       |
| Connectors                    |               🟡 |                 🟡 |          🔴 |       🟡 |            🟡 |               🔴 |            🟡 |    🟡 |      🟡 | 🔴 Missing critical hardening                    |
| Credential rotation           |               🟡 |                 🟡 |          🔴 |       🟡 |             ⚫ |               🔴 |             ⚫ |    🔴 |      🟡 | 🔴 Not production-ready                          |
| Ingestion/sync                |               🟡 |                 🟡 |          🟡 |       🟡 |            🟡 |               🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial                                       |
| Pipelines / Action Plane      |               🟡 |                 🟡 |          🟡 |       🟡 |            🟡 |               🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial                                       |
| Runtime Truth `/surfaces`     |               🟡 |                 🟡 |          🟡 |       🟡 |            🟡 |               🟡 |             ⚫ |    🟡 |      🟡 | 🟡 Partial                                       |
| Governance / Trust            |               🟡 |                 🟡 |          🟡 |       🟡 |            🟡 |               🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial                                       |
| AI assist / automation        |               🟡 |                 🟡 |          🟡 |       🟡 |            🟡 |               🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial                                       |
| Search / retrieval / indexing |               🟡 |                 🟡 |          🟡 |       🟡 |            🟡 |               🟡 |            🟡 |    🔴 |      🟡 | 🟡 Partial; full correctness not verified        |
| Provenance / lineage          |               🟡 |                 🟡 |          🟡 |       🟡 |            🟡 |               🟡 |            🟡 |    🔴 |      🟡 | 🟡 Partial; end-to-end traceability not verified |
| UI route/navigation           |               🟡 |                 🟡 |          🟡 |       🟡 |            🟡 |               🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial                                       |
| Shared contracts              |               🟡 |                 🟡 |           ⚫ |       🟡 |            🟡 |               🟡 |             ⚫ |    🟡 |      🟡 | 🟡 Partial                                       |
| Shared libraries              |               🟡 |                 🟡 |           ⚫ |       🟡 |             ⚫ |               🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial                                       |
| Repo cleanup                  |               🟡 |                 🟡 |           ⚫ |       🟡 |             ⚫ |                ⚫ |             ⚫ |    🟡 |      🔴 | 🟡 Partial                                       |

---

# 4. File-Level Gaps

| File / Area                      | Gap                                                                                                                 | Root Blocker | Required Fix                                                                                  | Required Tests                                              |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------- | ------------ | --------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| `DataCloudSecurityFilter.java`   | Non-strict fallback tenant and optional audit/policy modes create production risk.                                  | P0-1 / P0-5  | Fail production startup unless strict tenant, auth, audit, and policy are configured.         | Production bootstrap, tenant mismatch, audit-required tests |
| `EndpointSensitivity.java`       | Sensitivity map is not exhaustive for connector/settings/plugin/autonomy/context/AI operations.                     | P0-1         | Generate sensitivity from route contract.                                                     | No-unclassified-route test                                  |
| `RouteActionAccessRegistry.java` | Explicit access registry covers only a subset of high-impact routes.                                                | P0-1         | Generate method/path/action permissions from OpenAPI.                                         | Full route permission matrix                                |
| `DataSourceRegistryHandler.java` | Persists `credentials` in connector entity data and supports pending/sync behavior when no fabric connector exists. | P0-2         | Replace credentials with secret refs; durable connector jobs only.                            | Secret redaction, sync lifecycle, admin-only tests          |
| `DataCloudHttpServer.java`       | Monolithic composition with many optional null/in-memory/no-op fallbacks.                                           | P0-5         | Production profile validator and plane-specific composition modules.                          | Bootstrap failure tests                                     |
| `DataCloudRouterBuilder.java`    | Action Plane routes still mostly root-level despite comment recommending `/api/v1/action/*`.                        | P0-4         | Define and migrate canonical Action Plane routes.                                             | Route alias and namespace tests                             |
| `surfaces.service.ts`            | `/surfaces` is canonical but capability compatibility APIs remain.                                                  | P0-3         | Remove or isolate compatibility layer.                                                        | No capability reference check                               |
| `routes.tsx`                     | Connector create/edit navigation paths are not defined in inspected route tree.                                     | P1-6         | Add nested connector routes or remove actions.                                                | Playwright route traversal                                  |
| `session.ts`                     | Client-controlled shell role and legacy tenant localStorage remain.                                                 | P0-1 / P1-6  | Keep only as UI disclosure; ensure backend never trusts it. Remove legacy tenant if possible. | UI disclosure + backend auth tests                          |
| `package.json`                   | Explicit dependency-audit note remains; many workspace dependencies may be unused.                                  | P1-9         | Run dependency audit and remove unused workspace deps.                                        | Dependency usage check                                      |
| OpenAPI copies                   | Canonical spec coexists with test/resource copies.                                                                  | P1-8         | Generate or delete duplicate specs.                                                           | OpenAPI drift test                                          |
| `test_results.txt`               | Generated test output appears committed.                                                                            | P1-10        | Delete and add ignore/check.                                                                  | No generated test artifact check                            |

---

# 5. Prioritized Implementation Sequence

## 1. Canonical route/security registry

**Goal:** Every backend route has explicit tenant, role, policy, audit, privacy, and sensitivity metadata.

**Main files:** `DataCloudRouterBuilder.java`, `EndpointSensitivity.java`, `RouteActionAccessRegistry.java`, OpenAPI contracts.

**Acceptance criteria:** No route can be registered without security metadata.

---

## 2. Production bootstrap hardening

**Goal:** Prevent production startup with local/no-op/in-memory dependencies.

**Main files:** `DataCloudHttpServer.java`, runtime composition modules, deployment profiles.

**Acceptance criteria:** Production fails fast unless durable settings, idempotency, transaction manager, audit, metrics, tenant resolver, and policy engine are configured.

---

## 3. Connector secret and sync lifecycle hardening

**Goal:** Make connector onboarding safe, durable, auditable, and replayable.

**Main files:** `DataSourceRegistryHandler.java`, `DataFabricConnector`, connector contracts, secret provider.

**Acceptance criteria:** No raw credentials persisted; sync creates durable jobs only when a real connector runtime exists.

---

## 4. Runtime Truth completion

**Goal:** Finish `/surfaces` migration and remove active capability terminology.

**Main files:** `SurfaceRegistryHandler`, `surfaces.service.ts`, OpenAPI, SDKs, router.

**Acceptance criteria:** Backend, OpenAPI, UI schema, route gates, and SDKs all agree on one surface schema.

---

## 5. Action Plane route/product boundary migration

**Goal:** Align runtime routes with plane architecture.

**Main files:** `DataCloudRouterBuilder.java`, UI routes, OpenAPI `action-plane.yaml`, SDKs.

**Acceptance criteria:** Action Plane has canonical product namespace and compatibility redirects are explicit.

---

## 6. Shared-library boundary enforcement

**Goal:** Keep shared libraries generic and move Data Cloud semantics into Data Cloud.

**Main files:** platform Java modules, `planes/shared-spi`, `delivery/runtime-composition`, UI workspace dependencies.

**Acceptance criteria:** Automated checks enforce no product-specific imports in platform modules and no implementations in shared SPI.

---

## 7. AI/automation trust model

**Goal:** Make AI outputs evidence-backed, reviewable, and fail-closed in production.

**Main files:** `AiAssistHandler.java`, AI action audit models, learning/review handlers.

**Acceptance criteria:** Production never returns heuristic AI output as authoritative; all automation has confidence, evidence, review state, and rollback path.

---

## 8. UI route and navigation cleanup

**Goal:** Remove dead links and reduce route clutter.

**Main files:** `routes.tsx`, connector pages, route truth matrix, navigation registry.

**Acceptance criteria:** Every visible action navigates to a valid route; compatibility routes are tested redirects.

---

## 9. Contract and SDK hardening

**Goal:** Make OpenAPI and SDKs trustworthy.

**Main files:** `contracts/openapi/data-cloud.yaml`, `action-plane.yaml`, SDK Gradle tasks, UI API type generation.

**Acceptance criteria:** Java, TypeScript, and Python SDKs compile/typecheck from canonical spec.

---

## 10. Repository cleanup and docs consolidation

**Goal:** Stop repeated audits caused by stale docs/artifacts.

**Main files:** docs tree, archived audit docs, root scratch docs, generated outputs.

**Acceptance criteria:** Only canonical docs are active; generated artifacts are removed or clearly generated/validated.

---

# 6. Regression and Release Gates

Minimum gates before production readiness:

* [ ] Full backend route/action security matrix generated and validated.
* [ ] No unclassified API route.
* [ ] No production startup without strict tenant resolution.
* [ ] No production startup without durable settings/idempotency/transaction manager where required.
* [ ] Connector credentials never stored as raw entity fields.
* [ ] Connector sync cannot enter phantom pending/syncing state without durable execution.
* [ ] `/surfaces` backend/OpenAPI/UI/SDK parity test passes.
* [ ] No active `/capabilities` compatibility code outside explicit migration shim.
* [ ] Action Plane route namespace and compatibility redirects validated.
* [ ] Tenant mismatch tests pass for header, query, JWT, and API key paths.
* [ ] Policy fail-closed tests pass for critical routes.
* [ ] Audit event coverage exists for every sensitive/critical route.
* [ ] UI route traversal has no dead links.
* [ ] Connector create/edit routes work or actions are removed.
* [ ] AI production mode fails closed when provider is unavailable.
* [ ] AI suggestion lifecycle has evidence, confidence, audit, review, and rollback coverage.
* [ ] Generated Java/TypeScript/Python SDKs compile/typecheck.
* [ ] Duplicate OpenAPI copies are generated or removed.
* [ ] i18n extraction/check runs for UI strings.
* [ ] Accessibility checks cover shell, tables, modals, disabled surfaces, and route errors.
* [ ] Dead code, generated output, stale docs, and old audit artifacts cleanup check passes.

---

# Repository Cleanup Plan

| Priority | Classification  | Path                                                                       | Reason                                                                  | Evidence                                                                                | Safe Fix                                                        | Tests/Validation               |
| -------- | --------------- | -------------------------------------------------------------------------- | ----------------------------------------------------------------------- | --------------------------------------------------------------------------------------- | --------------------------------------------------------------- | ------------------------------ |
| P0       | Replace         | `products/data-cloud/delivery/launcher/.../EndpointSensitivity.java`       | Route sensitivity is manually curated and incomplete.                   | Missing many active route families compared with router.                                | Generate from route/security contract.                          | No-unclassified-route test     |
| P0       | Replace         | `products/data-cloud/delivery/launcher/.../RouteActionAccessRegistry.java` | Access registry covers too few high-impact routes.                      | Registry contains only limited explicit entries.                                        | Generate permission map from OpenAPI/route registry.            | Permission matrix test         |
| P0       | Replace         | `products/data-cloud/delivery/launcher/.../DataSourceRegistryHandler.java` | Raw credentials can be persisted in connector entity data.              | Handler stores `credentials` from payload.                                              | Store secret references only.                                   | Plaintext credential scan      |
| P1       | Merge / Replace | `products/data-cloud/delivery/api-contract-tests/openapi.yaml`             | Potential duplicate OpenAPI source.                                     | Duplicate OpenAPI path exists.                                                          | Generate from canonical contract or delete.                     | OpenAPI drift check            |
| P1       | Merge / Replace | `platform/contracts/src/test/resources/data-cloud-openapi.yaml`            | Data Cloud contract copy under platform test resources risks drift.     | Duplicate platform test resource exists.                                                | Generate test resource or remove.                               | Contract parity test           |
| P1       | Delete          | `products/data-cloud/delivery/ui/test_results.txt`                         | Generated test result file should not be source-controlled.             | File exists in UI tree.                                                                 | Delete and add ignore/check.                                    | No generated test output check |
| P1       | Move / Archive  | `dc-aep-analysis.md`                                                       | Root-level AEP/Data Cloud analysis conflicts with canonical plane docs. | Root doc exists.                                                                        | Move under archive or merge into canonical docs if still valid. | Documentation truth check      |
| P1       | Merge           | Active capability compatibility code                                       | Product now uses surfaces; compatibility code remains.                  | `surfaces.service.ts` still exposes capability shim.                                    | Remove after migration or isolate.                              | No capability reference check  |
| P1       | Replace         | UI connector route actions                                                 | Actions navigate to routes not defined in inspected route config.       | `/connectors/new` and edit navigation exist, but route tree only defines `connectors`.  | Add routes or remove actions.                                   | Playwright dead-link test      |
| P2       | Audit / Delete  | UI workspace dependencies                                                  | Package declares dependency audit is still needed.                      | `package.json` note explicitly says dependency audit required.                          | Remove unused shared deps.                                      | Dependency usage scan          |

---

## Canonical Docs Matrix

| Doc                                                               | Keep | Merge | Archive | Delete | Notes                                                          |
| ----------------------------------------------------------------- | ---: | ----: | ------: | -----: | -------------------------------------------------------------- |
| `products/data-cloud/README.md`                                   |    ✅ |       |         |        | Good canonical entry point.                                    |
| `docs/architecture/PLANE_ARCHITECTURE.md`                         |    ✅ |       |         |        | Canonical architecture source of truth.                        |
| `docs/architecture/SPI_VS_PLATFORM_BOUNDARY_GUIDE.md`             |    ✅ |       |         |        | Keep and enforce with tests.                                   |
| `docs/product/01_data_cloud_unified_vision_market_positioning.md` |    ✅ |       |         |        | Product vision.                                                |
| `docs/product/02_data_cloud_unified_detailed_architecture.md`     |    ✅ |       |         |        | Detailed architecture.                                         |
| `docs/product/03_data_cloud_unified_high_level_design.md`         |    ✅ |       |         |        | UX/API/migration design.                                       |
| `contracts/README.md`                                             |    ✅ |       |         |        | Contract ownership.                                            |
| `contracts/openapi/data-cloud.yaml`                               |    ✅ |       |         |        | Canonical editable OpenAPI.                                    |
| `contracts/openapi/action-plane.yaml`                             |    ✅ |       |         |        | Canonical Action Plane contract.                               |
| `contracts/openapi/aep.yaml`                                      |      |     ✅ |       ✅ |        | Keep only as explicitly time-bounded compatibility.            |
| `delivery/api-contract-tests/openapi.yaml`                        |      |     ✅ |         |      ✅ | Generate or delete.                                            |
| `platform/contracts/src/test/resources/data-cloud-openapi.yaml`   |      |     ✅ |         |      ✅ | Generate or delete.                                            |
| `dc-aep-analysis.md`                                              |      |     ✅ |       ✅ |        | Move out of root or merge if still current.                    |
| Archived audit/implementation docs                                |      |       |       ✅ |        | Keep only under archive with clear non-source-of-truth notice. |
| `test_results.txt`                                                |      |       |         |      ✅ | Delete generated artifact.                                     |

---

## Final Cleanup Checklist

* [ ] Legacy AEP-as-product terminology removed from active product surfaces.
* [ ] Capability terminology removed from active Runtime Truth code or isolated as explicit migration shim.
* [ ] Duplicate OpenAPI files deleted or generated from canonical contract.
* [ ] Connector credentials moved to secret references.
* [ ] Route/action security registry generated and complete.
* [ ] Default tenant fallback disabled in production.
* [ ] In-memory/no-op production dependencies fail startup.
* [ ] UI dead connector routes fixed.
* [ ] UI dependency audit completed.
* [ ] Root scratch docs moved or merged.
* [ ] Generated test outputs removed.
* [ ] Old audit/TODO docs excluded from canonical source of truth.
* [ ] Architecture boundary tests added for platform/shared/Data Cloud imports.
* [ ] SDK compile/typecheck gates restored for Java, TypeScript, and Python.
* [ ] Build/lint/typecheck/test pass after cleanup.

**Bottom line:** the repository is clearly improving toward the intended Data Cloud plane architecture, but it is not production-ready yet. The next meaningful improvement should be a focused hardening pass on **route/security registry + connector secret lifecycle + production bootstrap fail-fast + Runtime Truth parity**, followed by cleanup of duplicate contracts and stale artifacts.
