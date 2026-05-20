Executed against latest `samujjwal/ghatana` default branch HEAD:

`b0249d7e2f3d95d1d70565b222311010c2f26b8e`

I used the uploaded audit prompt as the audit contract.

## 1. Executive verdict

| Area                              | Verdict                                                                                                                                 |
| --------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| Production ready                  | **No**                                                                                                                                  |
| Feature complete                  | **No**                                                                                                                                  |
| Architecture aligned              | **Partially**                                                                                                                           |
| UI/UX production quality          | **Not verified from current code/docs/tests**                                                                                           |
| Security/privacy production ready | **No, but materially improved**                                                                                                         |
| Agent runtime production ready    | **No**                                                                                                                                  |
| Shared-library boundaries healthy | **Partially, but stale AEP/shared-boundary references remain**                                                                          |
| Confidence                        | **Medium-high for backend/contracts/security/runtime truth; medium-low for UI because I did not get enough UI implementation evidence** |
| Highest-risk blocker              | **Tenant/auth contract drift + optional production durability/audit behavior**                                                          |
| Most improved area                | **Runtime Truth and security middleware**                                                                                               |
| Most unstable area                | **Action Plane/AEP contract migration and agent-runtime ownership/boundary cleanup**                                                    |

Data Cloud’s canonical docs now clearly define **one customer-facing Data Cloud product**, with **AEP as the runtime implementation behind the Action Plane**, not a standalone product boundary. The README states this directly and lists the plane-based layout, canonical `/api/v1/surfaces`, and the Action Plane contract relationship.  The canonical plane architecture repeats the same model and defines the target layout, forbidden dependencies, and shared-platform split rules.

The implementation is moving in the right direction, but it is **not yet production-grade** because contracts, security assumptions, route namespaces, event envelope guarantees, durability, and agent/runtime ownership still drift from the canonical architecture.

---

## 2. Evidence inventory

### Canonical docs reviewed

| File                                                          | Status                                                                                                                         |
| ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `products/data-cloud/README.md`                               | Reviewed; clear canonical product boundary and plane model.                                                                    |
| `products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md` | Reviewed; canonical target architecture, dependency rules, migration sequence, shared-platform review rules.                   |
| `products/data-cloud/contracts/README.md`                     | Reviewed; partially stale because it still describes `action-plane.yaml` as a target rename item even though the file exists.  |

### Contracts reviewed

| File                                  | Status                                                                                                             |
| ------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `contracts/openapi/data-cloud.yaml`   | Reviewed; stale AEP/external-orchestration language and tenant model drift remain.                                 |
| `contracts/openapi/action-plane.yaml` | Reviewed; still titled/described as **Ghatana AEP**, with AEP standalone servers and tenant header/query wording.  |
| `contracts/openapi/aep.yaml`          | Not directly fetched, but equivalence test covers path parity against `action-plane.yaml`.                         |

### Main code modules reviewed

| Area                      | Evidence                                                                                                                                         |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| Runtime Truth             | `SurfaceRecord`, `SurfaceRegistryHandler`, router surface routes.                                                                                |
| Routing                   | `DataCloudRouterBuilder`, including entities, events, action routes, connectors, governance, plugins, settings, compliance, sovereign routes.    |
| Security                  | `DataCloudSecurityFilter`, `EndpointSensitivity`, `RouteActionAccessRegistry`, security tests.                                                   |
| Entity writes             | `EntityCrudHandler`.                                                                                                                             |
| Event append/query        | `EventHandler`.                                                                                                                                  |
| Agent runtime ownership   | `planes/action/agent-runtime/OWNER.md`; stale AEP ownership/dependency references remain.                                                        |
| Action orchestrator build | Still uses `com.ghatana.aep`, AEP terminology, and several shared-platform dependencies.                                                         |

### Tests and CI reviewed

| Gate                              | Evidence                                                                                                         |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Security/RBAC tests               | `DataCloudSecurityFilterTest` covers public/auth/internal/sensitive/critical/RBAC/tenant mismatch/break-glass.   |
| OpenAPI route parity test         | Exists, but normalizes `/api/v1/action/*` to `/api/v1/*`, which can mask canonical Action Plane contract drift.  |
| Action Plane/AEP equivalence test | Exists, but checks deprecation notice and path parity only, not semantic/security/metadata equivalence.          |
| Data Cloud CI                     | Build, unit test, ArchUnit named test, integration tests, OpenAPI parity, and AEP equivalence jobs exist.        |

---

## 3. Root blockers

### DC-P0-01 — Tenant identity model still drifts between contracts and runtime

**Severity:** P0
**Root cause:** Contracts still describe `tenantId` query/header as the tenant isolation mechanism, while the production target requires tenant identity to come from authenticated identity and reject spoofing. `DataCloudSecurityFilter` improves runtime enforcement, but `strictTenantResolution` defaults to false and `DEFAULT_FALLBACK_TENANT = "default"` still exists.  
**Evidence:** `data-cloud.yaml` says endpoints require `tenantId` query or `X-Tenant-ID` header, with bearer token additionally required through a proxy.  `action-plane.yaml` similarly says multi-tenant endpoints accept `X-Tenant-Id` or `tenantId` query.
**Affected surfaces:** All API routes, SDKs, UI clients, connector sync, Action Plane operations, agent runtime, governance.
**Target pattern:** Authenticated principal owns tenant; tenant query/header may only be compatibility hints and must be rejected on mismatch; production/staging/sovereign must fail closed if tenant cannot be derived from auth context.
**Required implementation:** Make strict tenant resolution mandatory outside local/test/embedded profiles, remove default tenant fallback from production paths, update OpenAPI security parameters, and generate route/client behavior from the corrected contract.
**Required tests:** Contract tests for no `tenantId` query requirement in production contracts; real-backend tests for missing tenant claim, mismatched tenant header, tenant query spoofing, API-key principal tenant mismatch, support/delegated access.
**Cleanup:** Deprecate tenant query/header as authoritative identity sources.
**Confidence:** High.

---

### DC-P0-02 — Action Plane contract still presents AEP as standalone product/runtime

**Severity:** P0
**Root cause:** The canonical docs say AEP is implementation behind Data Cloud Action Plane, but `action-plane.yaml` is still titled and described as **Ghatana AEP**, lists AEP standalone server URLs, and routes describe “AEP engine” behavior.
**Evidence:** Product README and plane architecture define Data Cloud → Action Plane → AEP runtime implementation.   `data-cloud.yaml` still says AEP owns broader orchestration outside data-local runtime surfaces, which conflicts with the current canonical product boundary.
**Affected surfaces:** Contracts, SDKs, docs, UI labels, deployment, route parity tests, Action Plane runtime modules.
**Target pattern:** `action-plane.yaml` must be the product-level Action Plane contract. `aep.yaml` must be deprecated compatibility only. No customer-facing route/docs should imply “Data Cloud plus AEP.”
**Required implementation:** Rewrite Action Plane OpenAPI title, description, servers, tags, route summaries, auth model, and examples. Keep AEP only as implementation metadata or compatibility alias.
**Required tests:** Semantic contract lint that forbids standalone AEP product language in canonical docs/contracts except in explicit compatibility/deprecation sections.
**Cleanup:** Remove stale AEP standalone URLs and “AEP owns orchestration outside Data Cloud” wording.
**Confidence:** High.

---

### DC-P1-03 — Action Plane route namespace is only partially canonicalized

**Severity:** P1
**Root cause:** Some route groups are canonicalized under `/api/v1/action/*`, but duplicates or non-action namespaces remain. Pipeline, memory, learning, execution routes are partially migrated with legacy gates.   However, autonomy and plugin routes are registered under both `/api/v1/*` and `/api/v1/action/*` without the same legacy feature-gate pattern, and agent catalog remains `/api/v1/agents/catalog`.
**Affected surfaces:** Pipelines, executions, memory, learning, autonomy, plugins, agents, UI navigation, SDK generation.
**Target pattern:** Action-owned operations live under `/api/v1/action/*`; legacy aliases must be explicit compatibility routes with retirement gates.
**Required implementation:** Normalize all Action Plane routes, introduce a compatibility registry with explicit retirement metadata, and ensure route generation and OpenAPI use the canonical namespace.
**Required tests:** Route namespace tests that fail if new Action Plane routes are registered outside `/api/v1/action/*` without compatibility metadata.
**Cleanup:** Retire or feature-gate `/api/v1/autonomy`, `/api/v1/plugins`, `/api/v1/agents/catalog`, `/api/v1/learning/stream`, `/api/v1/pipelines/*` aliases.
**Confidence:** High.

---

### DC-P1-04 — Route access and sensitivity registry is improved but not exhaustive

**Severity:** P1
**Root cause:** `RouteActionAccessRegistry` has explicit entries for many high-impact routes and normalizes `/api/v1/action/` back to `/api/v1/`, but it is still hand-maintained and incomplete for some Action Plane surfaces such as action memory and agent catalog.  `EndpointSensitivity` has a broad prefix model but does not treat `/api/v1/action/` as a first-class sensitivity prefix; it relies on route registry normalization for many cases.
**Affected surfaces:** Action memory, agents, plugins, autonomy, learning, reviews, governance, settings, connectors.
**Target pattern:** Contract-backed generated route security registry with explicit sensitivity, role, policy, audit, idempotency, and runtime-truth metadata per route.
**Required implementation:** Generate or validate registry entries from OpenAPI extensions. Add missing canonical action routes. Forbid unclassified routes in CI.
**Required tests:** Every router route must have security metadata; negative RBAC tests for each mutation class; policy/audit matrix tests.
**Cleanup:** Remove manual path-prefix heuristics once generated registry is stable.
**Confidence:** Medium-high.

---

### DC-P1-05 — Entity write consistency is optional, not production-mandatory

**Severity:** P1
**Root cause:** `EntityCrudHandler` supports idempotency, transaction manager, schema validation, quota checks, semantic indexing, and outbox wiring, but several production-critical dependencies are optional. It falls back to in-memory idempotency and direct non-transactional behavior when durable stores/transaction manager/outbox are absent.  
**Evidence:** The code comment says the outbox entry should be persisted in the transaction for production, but the current path adds it to an in-memory processor when available.
**Affected surfaces:** Entity CRUD, CDC, event consistency, audit consistency, semantic index consistency, replay, WebSocket notifications.
**Target pattern:** Production profile requires durable transaction manager, idempotency store, event outbox, audit sink, and semantic-index outbox.
**Required implementation:** Fail startup in production/staging/sovereign if durable transaction/idempotency/outbox/audit dependencies are missing. Make non-transactional direct side effects local/test only.
**Required tests:** Entity save retry golden test, partial failure test, transaction rollback test, outbox replay test, semantic index failure test, entity-event-audit consistency test.
**Cleanup:** Restrict in-memory idempotency/outbox to local/test with explicit runtime truth degraded state.
**Confidence:** High.

---

### DC-P1-06 — Event envelope completeness is not enforced at HTTP boundary

**Severity:** P1
**Root cause:** `EventHandler.handleAppendEvent` accepts a minimal `{type, payload|data}` shape, builds `DataCloudClient.Event` with type, payload, and source, and returns offset/type/timestamp.  The audit target requires richer envelope fields such as eventId, tenantId, workspaceId, subject, actor, classification, policyContext, provenance, traceContext, correlationId, and causationId.
**Affected surfaces:** Events, replay, provenance, Action Plane bridge, agent audit, lineage, governance, compliance evidence.
**Target pattern:** Canonical event envelope validated at the boundary, enriched server-side, and persisted durably.
**Required implementation:** Introduce canonical `EventEnvelope` DTO/schema; reject malformed events; enrich server-owned fields; persist classification, actor, trace, policy, provenance, and causation.
**Required tests:** Envelope validation, replay determinism, tenant isolation, ordering/offset, provenance, event-to-action handoff, malformed/partial envelope negative tests.
**Cleanup:** Remove minimal event append contract or mark as compatibility-only.
**Confidence:** High for HTTP boundary; lower for deeper store internals not inspected.

---

### DC-P1-07 — Runtime Truth exists, but full posture/action gating is not proven end-to-end

**Severity:** P1
**Root cause:** `SurfaceRecord` is a good typed model and prevents LIVE without at least one dependency probe.  `SurfaceRegistryHandler` exposes canonical `/api/v1/surfaces` and `/api/v1/surfaces/schema`, and the router removes `/api/v1/capabilities` aliases.   But full runtime posture metadata—auth, durability, audit, policy, metrics, tracing, event store, idempotency—and UI/SDK enforcement were not verified from current code.
**Affected surfaces:** UI navigation, SDK feature gates, route availability, disabled/degraded action states, operations dashboard.
**Target pattern:** `/api/v1/surfaces` is the single canonical runtime truth source and gates UI/SDK/actions from the same typed model.
**Required implementation:** Add posture fields explicitly or formalize them in `evidence`; wire UI and SDK to `SurfaceRecord`; fail CI if surfaces are visible without runtime truth.
**Required tests:** Surface state golden test, dependency failure/degraded test, UI route visibility test, SDK gate test, action disabled-reason test.
**Cleanup:** Remove remaining raw capability language and any UI fallback that does not read runtime truth.
**Confidence:** Medium.

---

### DC-P1-08 — Contract parity checks exist but can mask canonical route drift

**Severity:** P1
**Root cause:** `OpenApiRouteParity_DC_CON_001_Test` verifies OpenAPI/router route parity, but its normalization deliberately converts `/api/v1/action/*` to `/api/v1/*`.  That helps compatibility, but it can hide whether Action Plane routes are actually contracted under canonical Action Plane paths.
**Evidence:** `ActionPlaneAepEquivalence_DC_CON_002_Test` checks `aep.yaml` and `action-plane.yaml` path parity and deprecation notice, but not semantic descriptions, security metadata, tenant identity model, operation IDs, route namespaces, or server URLs.
**Affected surfaces:** SDK generation, contracts, route docs, compatibility lifecycle.
**Target pattern:** Canonical contract parity must preserve namespace semantics; compatibility aliases must be tested separately.
**Required implementation:** Split canonical route parity from compatibility alias parity. Compare route metadata, security, idempotency, runtime-truth extensions, and error envelopes.
**Required tests:** Contract semantic lint, route-security metadata parity, Action Plane namespace parity, AEP alias retirement test.
**Cleanup:** Remove route normalization that hides `/api/v1/action/*` canonicality.
**Confidence:** High.

---

### DC-P1-09 — Audit/security is improved, but fail-closed audit durability is not guaranteed

**Severity:** P1
**Root cause:** Security middleware enforces auth, RBAC, tenant mismatch rejection, policy checks for CRITICAL routes, and audit for sensitive/critical routes.   However, audit emission is fire-and-forget and does not block response path if recording fails.
**Affected surfaces:** Governance, retention, redaction, settings, connector credentials, model promotion, learning review, Action Plane execution.
**Target pattern:** Critical mutations must be durably audited or rejected; sensitive reads must emit reliable audit evidence in regulated profiles.
**Required implementation:** Add production audit sink readiness/startup gate and critical-route audit persistence guarantee. For non-critical sensitive reads, expose degraded posture if audit sink is unavailable.
**Required tests:** Audit sink unavailable startup test, audit write failure on critical mutation, audit event content test, policy-deny audit test.
**Cleanup:** Avoid manual JSON error body construction; standardize error envelopes through `ApiResponse`.
**Confidence:** Medium-high.

---

### DC-P1-10 — Agent runtime still has stale AEP ownership and dependency metadata

**Severity:** P1
**Root cause:** `planes/action/agent-runtime/OWNER.md` still identifies the owner as **AEP Agent Runtime**, parent **AEP**, team **AEP Team**, and dependency `products:aep:aep-operator-contracts`.  The orchestrator build file also retains AEP naming, group `com.ghatana.aep`, AEP comments, and broad platform dependencies.
**Affected surfaces:** Agent runtime, orchestrator, operator contracts, shared-library boundary, docs, CI ownership, on-call, generated docs.
**Target pattern:** Product-specific agent runtime belongs to Data Cloud Action Plane. Only generic primitives belong in `platform/java/agent-core`.
**Required implementation:** Update ownership metadata, Gradle descriptions/group naming strategy, dependency comments, and stale `products:aep` references. Split generic vs product-specific agent runtime semantics.
**Required tests:** Architecture test forbidding `products:aep` dependency references in active Data Cloud modules; package/ownership lint; shared-boundary ArchUnit checks.
**Cleanup:** Remove stale owner files and AEP references unless explicitly marked compatibility/deprecated.
**Confidence:** High.

---

### DC-P2-11 — Shared-library boundaries are defined in docs but not fully proven by code evidence

**Severity:** P2
**Root cause:** Plane architecture gives clear rules for keeping/splitting platform modules, including `agent-core`, `workflow`, `messaging`, `ai-integration`, `data-governance`, and `platform/contracts`.  But current Action orchestrator still depends on several shared modules while carrying AEP/Data Cloud Action semantics.
**Affected surfaces:** Agent runtime, workflows, messaging/event bridge, governance, AI integration, contracts.
**Target pattern:** Shared modules contain only cross-product primitives; Data Cloud plane behavior stays inside `products/data-cloud`.
**Required implementation:** Run module-by-module usage analysis and move/split product-specific semantics.
**Required tests:** Architecture boundary tests for Data/Event/Context/Governance not importing Action internals; shared module semantic lint; no Data Cloud types in platform contracts.
**Cleanup:** Move Data Cloud/OpenAPI contracts out of `platform/contracts` if duplicates exist.
**Confidence:** Medium.

---

### DC-P2-12 — CI exists, but release gates are incomplete for production readiness

**Severity:** P2
**Root cause:** Data Cloud CI runs build/test, integration tests, route parity, AEP equivalence, and one named ArchUnit test.  Missing or not verified: UI real-backend E2E, accessibility, i18n, production profile fail-closed, tenant/security golden matrix, connector evidence, agent governance, runtime truth UI gating, docs truth checks.
**Affected surfaces:** Release process, production deployment, UI/SDK, operations.
**Target pattern:** Every production invariant must be a release gate.
**Required implementation:** Add CI jobs for security/tenant, runtime truth, connector evidence, agent governance, a11y/i18n, UI real-backend E2E, docs truth, no stale AEP boundary references.
**Required tests:** See release gates below.
**Cleanup:** Remove checked-in `test_results.txt` if it is generated evidence rather than source.
**Confidence:** Medium.

---

## 4. Feature completeness matrix

Legend: ✅ complete, 🟡 partial, 🔴 missing, ❓ not verified.

| Area               | Code | Runtime wired | Contracted | UI surfaced | Tenant-safe | Durable | Observable | Tests |           Docs | Status                                      |
| ------------------ | ---: | ------------: | ---------: | ----------: | ----------: | ------: | ---------: | ----: | -------------: | ------------------------------------------- |
| Product shell      |   🟡 |            🟡 |         🟡 |           ❓ |          🟡 |       ⚫ |          ❓ |     ❓ |              ✅ | 🟡 Partial                                  |
| Runtime Truth      |    ✅ |             ✅ |         🟡 |           ❓ |          🟡 |       ⚫ |         🟡 |    🟡 |              ✅ | 🟡 Partial                                  |
| Contracts/SDK      |   🟡 |            🟡 |         🟡 |           ❓ |          🔴 |       ⚫ |          ⚫ |    🟡 |             🟡 | 🔴 Blocked by drift                         |
| Data Plane         |   🟡 |             ✅ |         🟡 |           ❓ |          🟡 |      🟡 |         🟡 |    🟡 |              ✅ | 🟡 Partial                                  |
| Event Plane        |   🟡 |             ✅ |         🟡 |           ❓ |          🟡 |       ❓ |         🟡 |    🟡 |              ✅ | 🟡 Partial                                  |
| Context Plane      |    ❓ |             ❓ |          ❓ |           ❓ |           ❓ |       ❓ |          ❓ |     ❓ |              ✅ | ❓ Not verified                              |
| Intelligence Plane |   🟡 |            🟡 |         🟡 |           ❓ |           ❓ |       ❓ |          ❓ |     ❓ |              ✅ | 🟡 Partial                                  |
| Governance Plane   |   🟡 |             ✅ |         🟡 |           ❓ |          🟡 |      🟡 |         🟡 |    🟡 |              ✅ | 🟡 Partial                                  |
| Action Plane/AEP   |   🟡 |            🟡 |         🔴 |           ❓ |          🟡 |       ❓ |          ❓ |    🟡 |             🟡 | 🔴 Contract/boundary blocked                |
| Agent runtime      |   🟡 |             ❓ |         🟡 |           ❓ |           ❓ |       ❓ |          ❓ |     ❓ | 🔴 stale OWNER | 🔴 Not production-ready                     |
| Connectors         |   🟡 |            🟡 |         🟡 |           ❓ |           ❓ |       ❓ |          ❓ |     ❓ |             🟡 | 🟡 Partial                                  |
| Plugins            |   🟡 |            🟡 |         🟡 |           ❓ |          🟡 |       ❓ |          ❓ |     ❓ |             🟡 | 🟡 Partial                                  |
| Operations         |   🟡 |            🟡 |         🟡 |           ❓ |           ⚫ |       ❓ |         🟡 |    🟡 |             🟡 | 🟡 Partial                                  |
| Deployment         |   🟡 |             ❓ |          ⚫ |           ⚫ |           ❓ |       ❓ |          ❓ |     ❓ |             🟡 | ❓ Not verified                              |
| Tests              |   🟡 |   ✅ CI exists |         🟡 |           ❓ |          🟡 |       ❓ |          ❓ |    🟡 |              ⚫ | 🟡 Partial                                  |
| Docs               |    ✅ |             ⚫ |         🟡 |           ⚫ |          🟡 |       ⚫ |          ⚫ |     ⚫ |             🟡 | 🟡 Canonical docs good, contract docs drift |
| Shared libraries   |   🟡 |            🟡 |         🟡 |           ⚫ |           ❓ |       ❓ |          ❓ |    🟡 |             🟡 | 🟡 Needs split verification                 |

---

## 5. Route/workflow validation matrix

| Workflow          | Runtime route evidence                                                                                     | Contract status                                                 | Security/runtime truth status                                                                                     | Verdict    |
| ----------------- | ---------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- | ---------- |
| Entities          | Entity routes in router; handler validates tenant/payload and supports idempotency/transaction optional.   | Contract exists but tenant model stale.                         | RBAC/sensitivity exists; durability optional.                                                                     | 🟡 Partial |
| Events            | `/api/v1/events` append/query/get by offset.                                                               | Contract exists but Action Plane event wording is AEP-centric.  | Minimal envelope; idempotency optional.                                                                           | 🟡 Partial |
| Query/analytics   | Analytics routes present.                                                                                  | Contracted in `data-cloud.yaml`.                                | Runtime truth/UI gating not verified.                                                                             | 🟡 Partial |
| Connectors        | `/api/v1/connectors` and `/data-fabric/connectors` aliases present; feature-flagged.                       | Contract not deeply verified.                                   | Credential/evidence/sync durability not verified.                                                                 | 🟡 Partial |
| Pipelines         | Canonical `/api/v1/action/pipelines`; legacy `/api/v1/pipelines` gated.                                    | Contract parity test normalizes action namespace.               | Route security registry covers several pipeline actions.                                                          | 🟡 Partial |
| Executions        | Canonical `/api/v1/action/executions/*`; legacy gated.                                                     | Namespace masking risk.                                         | Some registry entries.                                                                                            | 🟡 Partial |
| Reviews/learning  | Canonical `/api/v1/action/learning/*`; legacy gated for some routes.                                       | AEP/action equivalence only path-based.                         | Approve/reject critical logic present.                                                                            | 🟡 Partial |
| Agents            | `/api/v1/agents/catalog` exists, not action-namespaced.                                                    | Not verified.                                                   | Agent runtime stale owner.                                                                                        | 🔴 Blocked |
| Governance        | Governance routes present.                                                                                 | Contract tenant model stale.                                    | CRITICAL policy/audit/RBAC improved.                                                                              | 🟡 Partial |
| Settings/API keys | Settings routes present.                                                                                   | Not deeply verified.                                            | Registry marks key operations admin.                                                                              | 🟡 Partial |
| Plugins           | Both `/api/v1/plugins` and `/api/v1/action/plugins` always registered.                                     | Not deeply verified.                                            | Registry covers some plugin actions.                                                                              | 🟡 Partial |
| Runtime Truth     | `/api/v1/surfaces`, `/api/v1/surfaces/schema`; `/api/v1/capabilities` removed from router.                 | Contract not fully verified.                                    | Good typed model, UI/SDK gate not verified.                                                                       | 🟡 Partial |
| Operations        | Health/ready/live/metrics routes present.                                                                  | Not deeply verified.                                            | `/health/deep` is not public in `EndpointSensitivity.PUBLIC_PATHS`, likely intentional but should be documented.  | 🟡 Partial |

---

## 6. Shared library boundary matrix

| Module                                            | Current role                                     | Keep/move/split/delete                          | Evidence                                                                                                                            | Required action                                                      |
| ------------------------------------------------- | ------------------------------------------------ | ----------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| `platform/java/agent-core`                        | Generic agent primitives                         | **Split/keep minimal generic**                  | Plane architecture says keep minimal generic interfaces; move action-specific runtime/memory/dispatch/review into `planes/action`.  | Audit usages; remove Action Plane behavior from platform.            |
| `platform/java/workflow`                          | Workflow primitives + possible product semantics | **Split**                                       | Same shared-platform guidance.                                                                                                      | Keep generic workflow only; move pipeline semantics to Action Plane. |
| `platform/java/messaging`                         | Generic messaging                                | **Keep generic, move Data Cloud event routing** | Plane architecture says move Data Cloud event routing and Action Plane bridges into planes.                                         | Verify no Data Cloud-specific bridge semantics remain shared.        |
| `platform/java/ai-integration`                    | Provider abstraction + AI features               | **Split**                                       | Docs say keep providers, move query assist/schema inference/recommendations/action suggestions into Intelligence.                   | Move product behavior to `planes/intelligence`.                      |
| `platform/java/data-governance`                   | Generic policy + product governance              | **Split**                                       | Docs say move retention/redaction/provenance/evidence into Governance Plane.                                                        | Keep reusable policy primitives only.                                |
| `platform/contracts`                              | Shared contracts                                 | **Move/split**                                  | Docs say move Data Cloud and Action Plane OpenAPI/schemas into `products/data-cloud/contracts`.                                     | Add no duplicate Data Cloud contract gate.                           |
| `products/data-cloud/planes/action/orchestrator`  | Action orchestration                             | **Keep in Data Cloud, clean AEP naming/deps**   | Build file has AEP group/comments and broad platform deps.                                                                          | Rename metadata; verify dependency direction.                        |
| `products/data-cloud/planes/action/agent-runtime` | Product-specific agent runtime                   | **Keep in Data Cloud Action Plane**             | OWNER still says AEP parent/team/dependency.                                                                                        | Update owner/dependencies and split generic behavior.                |

---

## 7. File-level implementation TODOs

### P0 — Fix tenant/auth contract and runtime enforcement

1. **`products/data-cloud/contracts/openapi/data-cloud.yaml`**
   Change tenant/auth sections so tenant is derived from authenticated identity in production. Mark `X-Tenant-ID` and `tenantId` query as compatibility/debug hints only, never authoritative. Add security schemes and response examples for tenant mismatch, missing tenant claim, delegated support access, and sovereign profile denial. Validate with OpenAPI lint and negative auth tests.

2. **`products/data-cloud/contracts/openapi/action-plane.yaml`**
   Apply the same tenant/auth correction. Remove “AEP standalone” server and customer-facing AEP title/description. Rename to Data Cloud Action Plane terminology while preserving compatibility metadata for AEP alias retirement.

3. **`products/data-cloud/delivery/launcher/src/main/java/.../DataCloudSecurityFilter.java`**
   Make `strictTenantResolution` mandatory for non-local/test/embedded. Remove production use of `DEFAULT_FALLBACK_TENANT`. Add startup/profile validation so production cannot boot with permissive tenant resolution, missing audit sink, or missing policy engine for critical routes.

4. **Tests**
   Add production-profile tests for missing tenant claim, `tenantId` query spoofing, mismatched `X-Tenant-ID`, API-key tenant mismatch, support/delegated access, and strict startup failures.

### P0/P1 — Make entity/event/audit/idempotency durable in production

5. **`EntityCrudHandler.java`**
   Make durable transaction manager, durable idempotency store, durable outbox, and audit sink mandatory in production profiles. Keep in-memory idempotency/outbox local/test only. Remove direct WebSocket/semantic side effects from production write path.

6. **`EventHandler.java`**
   Require canonical event envelope at append boundary. Enrich server-owned fields and reject missing/invalid required fields. Persist idempotency response in durable store for production.

7. **Tests**
   Add golden tests for entity save retry, event append retry, partial failure rollback, outbox replay, entity-event-audit consistency, event replay ordering, and semantic index failure recovery.

### P1 — Fix Action Plane route/contract migration

8. **`DataCloudRouterBuilder.java`**
   Move Action-owned routes consistently under `/api/v1/action/*`. Put legacy aliases behind one explicit compatibility feature with retirement metadata. Fix always-duplicated plugin/autonomy route groups and non-action agent catalog route.

9. **`OpenApiRouteParity_DC_CON_001_Test.java`**
   Stop normalizing `/api/v1/action/*` to `/api/v1/*` for canonical parity. Split canonical parity from compatibility-alias parity.

10. **`ActionPlaneAepEquivalence_DC_CON_002_Test.java`**
    Extend beyond path parity: compare operation IDs, security, request/response schemas, error envelope, idempotency metadata, route namespace, and deprecation metadata.

### P1 — Generate route/security/runtime-truth metadata

11. **`EndpointSensitivity.java` and `RouteActionAccessRegistry.java`**
    Replace hand-maintained partial coverage with generated or contract-validated metadata. Ensure every route has sensitivity, access level, policy requirement, audit requirement, idempotency semantics, runtime surface, and retirement status.

12. **`SurfaceRecord.java` / `SurfaceRegistryHandler.java`**
    Add explicit runtime posture fields or typed posture object. Include auth, durability, audit, policy, metrics, tracing, event store, idempotency, and dependency health. Ensure UI/SDK consume it.

### P1 — Agent runtime boundary cleanup

13. **`products/data-cloud/planes/action/agent-runtime/OWNER.md`**
    Replace AEP parent/team/on-call/dependency references with Data Cloud Action Plane ownership. Remove `products:aep:aep-operator-contracts` references unless marked historical/deprecated.

14. **`products/data-cloud/planes/action/orchestrator/build.gradle.kts`**
    Clean AEP comments/group/descriptions. Verify only generic shared-platform modules remain shared; move product-specific workflow/agent semantics into Action Plane modules.

### P2 — UI/UX and release gates

15. **`products/data-cloud/delivery/ui/**`**
    Not verified from current code/docs/tests. Add a UI audit pass for route coverage, runtime-truth gating, no mocks/fakes, a11y/i18n, keyboard/focus states, degraded/empty/error states, and real-backend E2E.

16. **`products/data-cloud/.github/workflows/ci.yml`**
    Add gates for UI real-backend E2E, a11y, i18n, production profile fail-closed, security/tenant golden matrix, connector evidence, agent governance, docs truth, and stale AEP reference lint. Current CI has build/test/integration/OpenAPI/AEP equivalence, but not all production gates.

---

## 8. Prioritized implementation sequence

1. **Canonical request context and tenant/scope enforcement**
   Files: `DataCloudSecurityFilter`, `HttpHandlerSupport`, OpenAPI contracts.
   Acceptance: production cannot accept spoofed/missing tenant identity.

2. **Contract-backed route/security/surface registry**
   Files: `data-cloud.yaml`, `action-plane.yaml`, `RouteActionAccessRegistry`, `EndpointSensitivity`.
   Acceptance: no route without explicit security/surface/idempotency metadata.

3. **Runtime Truth canonical typed surface model**
   Files: `SurfaceRecord`, `SurfaceRegistryHandler`, UI/SDK adapters.
   Acceptance: UI/SDK/actions are gated only by `/api/v1/surfaces`.

4. **Production fail-closed gates**
   Files: launcher/runtime composition/security filter/CI.
   Acceptance: prod/staging/sovereign fail startup without policy/audit/durable stores.

5. **Entity/event/audit/idempotency consistency**
   Files: `EntityCrudHandler`, `EventHandler`, stores/outbox.
   Acceptance: golden tests prove retry/rollback/replay consistency.

6. **Connector durable ingestion/evidence pipeline**
   Files: connector handlers/extensions.
   Acceptance: credential redaction, durable sync jobs, row-level evidence, retries, DLQ.

7. **Action Plane namespace and AEP boundary cleanup**
   Files: router, contracts, OWNER files, Gradle metadata.
   Acceptance: no active stale `products:aep` references except deprecated compatibility docs.

8. **Agent runtime governance/memory/mastery/durability hardening**
   Files: `planes/action/agent-runtime`, `agent-core`, registries.
   Acceptance: governed dispatch, kill switch, durable memory/mastery registry, audit trail.

9. **UI/SDK/product-shell consolidation**
   Files: `delivery/ui`, `delivery/sdk`.
   Acceptance: no backend-internal imports, no production mocks, runtime-truth gated surfaces.

10. **Shared-library split/move/keep cleanup**
    Files: `platform/java/*`, `platform/contracts`, Data Cloud planes.
    Acceptance: platform contains only generic cross-product primitives.

11. **Test architecture hardening**
    Acceptance: unit/integration/API/E2E/security/a11y/i18n/ops are layered without duplicate test theater.

12. **Docs/canonical source cleanup**
    Acceptance: README, Plane Architecture, contracts, and docs agree.

13. **CI/release gates**
    Acceptance: all gates below pass before release.

---

## 9. Release gates to add or enforce

Required before production release:

```text
build passes
lint passes
typecheck passes
unit tests pass
integration tests pass
API contract tests pass
architecture boundary tests pass
UI real-backend E2E tests pass
security/tenant isolation tests pass
Runtime Truth tests pass
connector evidence tests pass
agent governance tests pass
a11y tests pass
i18n tests pass
production profile fail-closed tests pass
docs truth checks pass
no stale product-boundary references
no production mocks/stubs/fake data
no in-memory production registry/store
no duplicate Runtime Truth/capability registry
no duplicate OpenAPI contract semantics
```

Current CI covers a useful subset—build/test, integration tests, an ArchUnit named test, OpenAPI parity, and AEP equivalence—but not the full production gate set.

---

## 10. Repository cleanup plan

| Priority | Classification        | Path                                                         | Reason                                                                                   | Safe fix                                                                          |
| -------- | --------------------- | ------------------------------------------------------------ | ---------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| P0       | Replace               | `contracts/openapi/action-plane.yaml`                        | Still describes standalone AEP and AEP server URLs.                                      | Rewrite as Action Plane contract; keep AEP only in deprecated compatibility text. |
| P0       | Replace               | `contracts/openapi/data-cloud.yaml`                          | Tenant/auth and AEP boundary language conflict with canonical docs.                      | Align with Data Cloud + Action Plane model and authenticated tenant identity.     |
| P1       | Deprecate behind flag | Legacy Action aliases in `DataCloudRouterBuilder`            | Some legacy routes are gated, but plugins/autonomy are duplicated without same pattern.  | One compatibility registry with retirement metadata.                              |
| P1       | Replace               | `RouteActionAccessRegistry`                                  | Hand-maintained and not exhaustive.                                                      | Generate/validate from OpenAPI metadata.                                          |
| P1       | Replace               | `EndpointSensitivity`                                        | Prefix heuristics can miss canonical action routes.                                      | Contract-backed sensitivity map.                                                  |
| P1       | Move/merge            | `planes/action/agent-runtime/OWNER.md`                       | Stale AEP ownership and `products:aep` dependency.                                       | Rewrite owner metadata to Data Cloud Action Plane.                                |
| P1       | Replace               | `planes/action/orchestrator/build.gradle.kts` comments/group | AEP terminology remains in active build metadata.                                        | Rename metadata and verify boundaries.                                            |
| P1       | Deprecate local-only  | In-memory idempotency/outbox paths                           | Local fallback exists in production-capable handlers.                                    | Fail closed in production; local/test only.                                       |
| P2       | Replace               | OpenAPI parity normalization                                 | Masks canonical `/api/v1/action/*` drift.                                                | Separate canonical parity from compatibility parity.                              |
| P2       | Extend                | AEP equivalence test                                         | Path parity only, not semantic/security equivalence.                                     | Compare full contract semantics.                                                  |

---

## 11. Final assessment

The latest HEAD shows **real architectural progress**: plane-based layout exists, Runtime Truth has a typed endpoint, `/api/v1/capabilities` is no longer registered in the router, security middleware has stronger tenant/RBAC/policy/audit behavior, route parity and AEP equivalence tests exist, and CI runs several Data Cloud gates.

But the product is **not production-ready** because the canonical Data Cloud/Action Plane model is not yet consistently enforced across contracts, active route namespaces, ownership metadata, tenant/auth semantics, event envelope guarantees, and durable production dependencies. The next implementation pass should focus less on adding new capabilities and more on making the current planes **contract-backed, tenant-safe, durable, runtime-truth-gated, and boundary-clean**.
