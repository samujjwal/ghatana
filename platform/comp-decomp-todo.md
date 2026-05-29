Executed the audit against `samujjwal/ghatana` commit `2f2b5f6e7b42bbb614b28aba732062eae8dbac7c`.

I audited the full snapshot statically through the GitHub connector. I did **not** execute build/test commands locally, so the scores below are code-and-contract-grounded readiness scores, not runtime test results.

## 1. Executive Summary

**Overall production-readiness score: 2.9 / 5**

Data Cloud is no longer a loose idea: the repo has a strong canonical plane architecture, generated Gradle/module registry, OpenAPI contracts, route-security manifest, UI route registry, production security filter, and meaningful readiness gates. The canonical docs define Data Cloud as one plane-based product, with Action Plane as the governed automation runtime formerly known as AEP. 

However, it is **not production-ready as a coherent, simple, fully usable product suite yet**. The strongest blockers are: incomplete product manifest/conformance registration, incomplete plane migration, many important UI surfaces hidden/preview/non-discoverable, connector/data-fabric fallback behavior returning 503, generic entity-backed collection modeling instead of first-class product concepts, i18n leakage, remaining compatibility routes, and Action Plane/AEP ownership ambiguity.

The canonical product registry marks Data Cloud as a platform provider with backend, web, and SDK surfaces implemented, but its conformance flags are still all false and it has no product manifest registered yet. 

## 2. Scope Inspected

Inspected representative code and docs across:

* Root workspace/build: `package.json`, `pnpm-workspace.yaml`, `settings.gradle.kts`, generated Gradle includes.
* Canonical Data Cloud docs: `PLANE_ARCHITECTURE.md`, unified product vision, route manifest system, Action Plane module inventory.
* Contracts: `contracts/openapi/data-cloud.yaml`, `contracts/openapi/action-plane.yaml`.
* Backend launcher/API: `DataCloudRouterBuilder.java`, `RouteSecurityRegistry.java`, `DataCloudSecurityFilter.java`, `EntityCrudHandler.java`.
* UI: `App.tsx`, `routes.tsx`, `DefaultLayout.tsx`, `RouteSurfaceRegistry.ts`, `feature-gates.ts`, `DataExplorer.tsx`, collection API client.
* Tests/gates: route truth tests, OpenAPI route parity test, root script registry.
* Audio-video: generated product/module registry and multimodal service build file.
* Product registry: `config/canonical-product-registry.json`.

The commit itself appears to be a changelog-only commit for YAPPC, so the audit treats the **full tree at that SHA** as the source of truth, not the diff.

## 3. Current Product Map

Data Cloud has the right intended shape: Experience, Contract, Runtime Truth, Data, Event, Context, Intelligence, Governance, Action, and Operations planes are defined as the target architecture. 

The generated Gradle registry includes the major Data Cloud planes and Action Plane modules, including data/entity, event/core, event/store, intelligence/analytics, governance/core, delivery API/launcher/SDK, integration tests, and many Action Plane modules such as operator-contracts, central-runtime, engine, registry, event-bridge, agent-runtime, observability, orchestrator, server, identity, and compliance. 

Audio-video is present as a shared service with persistence, security, cache, messaging, integration tests, multimodal, STT, TTS, vision, and common libraries.  Its root build is still mostly an aggregate/polyglot build wrapper, not a Data Cloud-integrated modality workflow. 

## 4. Readiness Scorecard

| Dimension                       | Score | Rationale                                                                                                                            |
| ------------------------------- | ----: | ------------------------------------------------------------------------------------------------------------------------------------ |
| Product coherence               |   3.2 | Strong canonical vision, but product registry/conformance and UI discoverability still lag.                                          |
| Feature completeness            |   2.7 | Many surfaces/routes exist, but several product journeys are generic, preview, hidden, or fallback-only.                             |
| E2E workflow completeness       |   2.6 | Backend has broad routes; UI journeys are not yet uniformly first-class from source → ingest → quality → lineage → action.           |
| Data Cloud core architecture    |   3.4 | Plane architecture is strong; migration is incomplete.                                                                               |
| AEP / Action Plane architecture |   3.1 | Canonical Action Plane namespace exists, but module inventory still says several AEP semantic modules are temporarily co-located.    |
| Agent architecture              |   2.8 | Agent catalog/memory/routes exist, but UX and runtime lifecycle are not fully discoverable/productized.                              |
| Audio-video integration         |   2.0 | Modules exist, but I found no clear first-class Data Cloud audio-video ingestion/catalog/action journey.                             |
| Shared library quality          |   2.8 | Reuse is extensive, but Data Cloud UI package explicitly notes dependency-sprawl audit is still required.                            |
| UI/UX simplicity                |   2.7 | Primary IA is simplified, but advanced/important surfaces are hidden and many strings/styles are page-local.                         |
| Backend/API correctness         |   3.4 | Router, OpenAPI, route security, auth, tenant, policy, and audit foundations are strong.                                             |
| Plugin/extensibility            |   3.0 | Plugin routes and modules exist, but lifecycle and product UX are still not fully coherent.                                          |
| Security/authorization          |   3.8 | Fail-closed security metadata, tenant/JWT checks, access levels, policy checks, and blocking audit are strong.                       |
| Privacy/governance              |   3.2 | Retention, redaction, policies, compliance routes exist; end-user journeys remain incomplete.                                        |
| Observability/operations        |   3.1 | Health, metrics, runtime truth, operations, audit, and Action Plane observability exist, but async workflow observability is uneven. |
| Reliability/failure handling    |   3.0 | Production requirements exist for entity writes; several optional engines still degrade to 503.                                      |
| Performance/scalability         |   2.7 | Query limits, caching, async patterns exist, but large data/audio-video scale is not proven.                                         |
| i18n                            |   2.2 | i18n is initialized, but many raw user-visible strings remain in routes/layout/pages.                                                |
| Accessibility                   |   2.7 | Some ARIA labels and a11y scripts exist, but not consistently across all surfaces.                                                   |
| Test quality                    |   3.1 | Good route/contract tests exist; behavior-level journey tests are still insufficient.                                                |
| Developer experience            |   3.3 | Many scripts and gates exist; command surface is large and release-evidence-heavy.                                                   |
| Configuration/feature flags     |   3.0 | Central feature gates exist; some production defaults hide important surfaces.                                                       |
| Docs/code alignment             |   2.8 | Strong docs, but target architecture and migration inventory confirm work remains.                                                   |
| Deployment readiness            |   2.6 | Registry says Data Cloud lifecycle execution is disabled and conformance flags are false.                                            |
| Maintainability/SRP/DRY         |   3.0 | Router split is better, but route, UI, and compatibility surfaces remain broad.                                                      |
| Overall                         |   2.9 | Strong foundation, not yet a production-grade coherent product suite.                                                                |

## 5. Top Blockers

1. **Data Cloud is marked “ready” in lifecycle metadata while core conformance flags are false.** This is a governance/readiness mismatch: product registry says backend/web/SDK/kernel bridge are ready, but manifest, observability, security, dataAccess, bridge, agent definitions, mastery bindings, evaluation packs, and runtime module conformance are false. 

2. **Action Plane ownership is still transitional.** The inventory says several AEP semantic modules are active but temporarily co-located, and temporary compatibility modules are release-blocking. 

3. **Default UI hides too much of the product.** The canonical architecture says default navigation should include Home, Data, Events, Query, Pipelines, Trust, and Operations.  The actual sidebar only includes Home, Data, Pipelines, Query, Trust, and Operations; Events is routeable but non-discoverable. 

4. **Several important surfaces are preview/non-discoverable.** Events, alerts, memory, entities, context, fabric, agents, plugins, connectors, insights, and settings are either non-discoverable, preview, or boundary surfaces in the route registry. 

5. **Connector/Data Fabric journey can be routeable but unavailable.** Connector routes are registered, but if the handler is missing or the feature flag is disabled, the router returns 503 for canonical and compatibility connector routes. 

6. **Collections are still modeled through generic entity storage.** The UI collection API is backed by `/api/v1/entities/dc_collections`, not first-class collection/dataset contracts. Create/update are implemented as generic entity POST/upsert. 

7. **Data Explorer has incomplete action behavior.** “More actions” currently only stops propagation and does not open a real action menu. 

8. **i18n is not complete.** i18n is initialized at app startup, but route loading errors, Data Explorer headings, filters, empty/error states, and many labels are raw strings.   

9. **Idempotency is incomplete across mutating routes.** Entity handler comments explicitly state idempotency is implemented for entity POST/batch, while events, governance, analytics, and pipeline routes still need idempotency support or explicit non-idempotent documentation. 

10. **Audio-video is not yet a first-class Data Cloud modality.** Audio-video modules exist and multimodal service depends on persistence, STT, vision, agent core, governance, and audit, but I did not find a clear Data Cloud UI/API journey for ingesting, cataloging, governing, indexing, and triggering Action Plane processing for audio/video assets. 

## 6. Key Strengths

1. Clear plane architecture and target repository layout. 
2. Strong product thesis: Data Cloud as unified AI-native operational data fabric; AEP no longer a separate customer-facing product. 
3. Canonical route manifest system ties backend routing, security, OpenAPI, UI gating, SDK metadata, and docs. 
4. Route manifest includes auth, tenant, policy, blocking audit, access, idempotency, lifecycle, and runtime surface metadata. 
5. Backend security filter enforces auth, tenant isolation, policy checks, and audit emission. 
6. Production-like profiles fail closed when route security metadata is missing. 
7. Critical routes can require blocking audit and fail if audit persistence fails. 
8. Entity write production requirements enforce durable idempotency, transactions, audit, and outbox in production/staging/sovereign profiles. 
9. OpenAPI route parity tests verify OpenAPI ↔ router alignment, critical-route security, runtime truth extensions, and idempotency metadata. 
10. UI has meaningful route gating and disabled-surface UX rather than silent 404s. 

## 7. Feature Completeness Matrix

| Capability                  | Current state                                                | Gap                                                     | Severity | Action                                                                                                              |
| --------------------------- | ------------------------------------------------------------ | ------------------------------------------------------- | -------: | ------------------------------------------------------------------------------------------------------------------- |
| Data source/connectors      | Routes exist, gated by feature flag and handler availability | 503 fallback means routeable but not necessarily usable |       P0 | Make connectors a production-supported surface with durable registry, sync jobs, health, credentials, and UI state. |
| Dataset/collection registry | Generic entity-backed `dc_collections`                       | Not a first-class domain contract                       |       P0 | Introduce canonical collection/dataset API and migrate UI client off generic entity assumptions.                    |
| Event log                   | Append/query routes exist                                    | UI Events surface is non-discoverable and gated         |       P1 | Promote Events into default IA once stable.                                                                         |
| Pipelines                   | Action pipeline routes exist                                 | UX and execution lifecycle need stronger user journey   |       P1 | Consolidate Pipeline Center around run lifecycle, retries, logs, checkpoints, lineage, policy.                      |
| AEP/patterns                | Action modules exist                                         | AEP semantic ownership still transitional               |       P0 | Finish split/ownership cleanup per module inventory.                                                                |
| Agents                      | Agent catalog/memory routes exist                            | Agent UX is preview/non-discoverable                    |       P1 | Productize agent catalog, lifecycle, approvals, memory, and observability.                                          |
| Audio-video                 | Modules exist                                                | No visible Data Cloud modality journey found            |       P1 | Add AV asset ingestion/catalog/index/action-plane integration.                                                      |
| Governance                  | Retention, redaction, policy, compliance routes exist        | Needs coherent Trust Center workflows                   |       P1 | Make policy simulation, retention, redaction, audit, data subject controls one journey.                             |
| Observability               | Health/metrics/runtime truth/operations exist                | Async workflow visibility uneven                        |       P1 | One run/job/operation ledger across ingestion, connectors, pipelines, agents, AV.                                   |
| UI simplicity               | Primary IA simplified                                        | Too many valuable surfaces hidden; raw strings remain   |       P1 | Move toward backend-owned navigation with progressive disclosure.                                                   |
| i18n/a11y                   | Tools and partial labels exist                               | Raw strings and page-local styles remain                |       P1 | Single i18n pass across all pages/components/actions/states.                                                        |
| Tests                       | Route/contract/readiness tests exist                         | Need true E2E journey tests                             |       P1 | Add journey tests grouped with implementation passes.                                                               |

## 8. End-to-End Journey Findings

### 8.1 Connect data source → ingest → observe sync

Expected: user opens Connectors, registers source, tests credentials, maps schema, triggers sync, sees job state, errors, quality impact, lineage, and resulting dataset.

Current: connector routes exist for list/register/update/delete/test/enable/disable/rotate credentials/health/schema/sync/status, but they are feature-gated and can return 503 if handler unavailable or disabled. 

Gap: this journey is not safe as a default production path.

### 8.2 Manage collections/datasets

Expected: first-class collection/dataset registry with lifecycle, schema, owner, quality, lineage, retention, permissions, and drilldown.

Current: UI lists collections from `/entities/dc_collections` and maps generic backend entity data into collection objects. 

Gap: this works as a pragmatic bridge, but not as a robust product contract.

### 8.3 Quality and lineage drilldown

Current: Data Explorer supports table, lineage, quality, and schema views; lineage fetches backend graph and impact analysis when selected. 

Gap: detail panel only appears when `selectedCollection` is set in component state. Navigating directly to `/data/:id?view=lineage` risks showing list without loading the selected detail unless the page handles the route param elsewhere; from inspected code, the primary selected collection path is click-state based. 

### 8.4 Action Plane / pipeline execution

Current: canonical routes exist under `/api/v1/action/pipelines`, `/executions`, `/learning`, `/memory`, `/plugins`, and `/autonomy`.  

Gap: canonical route shape is strong, but AEP semantic modules are still in temporary co-location/migration state. 

### 8.5 Agent workflow

Current: agent catalog and memory routes are registered and protected.  

Gap: Agents are preview/non-discoverable in UI route registry, so normal users do not have a coherent, discoverable agent journey. 

### 8.6 Audio-video

Current: audio-video is included in generated product modules and has multimodal/STT/TTS/vision modules.  The multimodal service depends on common AV library, agent core, vision, persistence, STT, governance, and audit. 

Gap: no inspected Data Cloud API/UI route showed first-class AV ingestion, catalog, consent, retention, processing job, search/index, or Action Plane trigger integration.

## 9. UI/UX Findings

The UI has moved in the right direction: route config explicitly says it simplified IA around Data Explorer, Pipeline Center, Insights, and Trust Center. 

But the product is still not “0 cognitive load”:

* Default navigation is too narrow and omits Events despite canonical docs listing Events as default navigation.  
* Many route surfaces are non-discoverable, including connectors, insights, events, agents, plugins, and settings. 
* Feature gates disable many surfaces in production/staging unless explicitly enabled; Fabric is disabled by default in all profiles unless explicitly enabled. 
* Data Explorer has raw strings, page-local visual styles, and an incomplete “More actions” button. 
* Data Explorer quality view exposes raw JSON for retention and lineage, which is useful for developers but too high-cognitive-load for normal users. 

## 10. Architecture and Boundary Findings

The architecture direction is correct, but migration is incomplete.

The canonical architecture explicitly forbids Data/Event/Context/Governance planes from depending on Action Plane implementation internals and says contracts must not depend on runtime implementation. 

The Action Plane inventory still says AEP-owned semantic modules are temporarily co-located and several compatibility/migration modules remain release-blocking. 

Shared platform rules are also explicit: keep modules in platform only when generic and reused by three or more unrelated products; move/split into Data Cloud when the module primarily describes Data Cloud or Action Plane behavior. 

## 11. Security, Privacy, Governance Findings

Security is one of the strongest areas.

The security filter authenticates via API key or JWT, extracts tenant identity, rejects missing/mismatched tenant claims, enforces route access level, evaluates policy for critical routes, and audits sensitive/critical routes. 

Critical routes fail closed when policy engine is missing in enforcing mode. 

Remaining security/governance gaps:

* Not all mutating routes have fully implemented idempotency behavior, even if metadata checks exist. 
* Data Explorer and collections still rely on generic entities; permissions around first-class dataset/collection lifecycle need to be enforced through explicit backend domain contracts, not just generic entity access.
* Audio-video consent, retention, PII/biometric handling, and large-file governance were not visible as a coherent Data Cloud journey.

## 12. Test and Verification Gap Analysis

Strong existing gates:

* UI has route manifest, route docs, API types, contract-backed tests, route truth tests, and critical journey contract test in `test:readiness`. 
* Route truth tests assert canonical routes, preview routes, runtime capability gates, and role protected routes. 
* Backend OpenAPI parity tests check OpenAPI ↔ router parity, route count, security schemes, runtime truth extensions, and idempotency metadata. 

Missing or weak verification areas:

* Source connector full journey: create → test → sync → job status → dataset created → quality/lineage updated.
* Direct deep-link route tests for `/data/:id?view=lineage|quality|schema`.
* Pipeline run lifecycle: draft → save → execute → checkpoint → retry/cancel/rollback → logs.
* Agent lifecycle: catalog → enable/configure → run → memory write/search/delete/retain → audit.
* Audio-video lifecycle: upload/ingest → metadata → processing job → transcript/frame index → pattern/agent trigger → retention/audit.
* UI i18n raw text enforcement for all Data Cloud pages.
* Accessibility tests for all route surfaces, not only selected happy paths.
* Runtime truth behavior when services degrade or return 503.

## 13. Consolidated Task Plan Grouped to Minimize Verification Passes

### Group 1 — Product registry, conformance, and lifecycle truth

**Goal:** Stop readiness contradictions.

**Change:**

* `config/canonical-product-registry.json`
* `products/data-cloud/product-manifest.yaml` or equivalent manifest path
* `products/data-cloud/lifecycle/*`
* Product registry generation/validation scripts

**Tasks:**

* Add a real Data Cloud product manifest or explicitly model Data Cloud as a platform-provider with a different conformance schema.
* Replace false conformance flags with concrete pass/fail gates tied to current code.
* Remove “ready” status if conformance is false, or define what “platform-provider ready” actually means.

**Tests/verification:**

* `pnpm check:product-registry`
* Data Cloud platform-provider readiness check
* Registry drift check

**Acceptance:** Product registry no longer reports ready while conformance is disabled/false.

---

### Group 2 — Plane/module boundary cleanup

**Goal:** Finish Data Cloud vs AEP/Action Plane ownership cleanup.

**Change:**

* `products/data-cloud/planes/action/*`
* `products/data-cloud/docs/architecture/ACTION_PLANE_MODULE_INVENTORY.md`
* Gradle generated includes
* Architecture boundary tests

**Tasks:**

* Split or reclassify temporary compatibility modules.
* Move AEP-owned semantic modules to the intended AEP location or explicitly document a final co-location decision.
* Keep Data Cloud-owned persistence/governance/audit/observability modules in Data Cloud.
* Add dependency rules preventing Data/Event/Context/Governance from depending on Action internals.

**Tests/verification:**

* Gradle build for affected modules
* boundary/dependency tests
* generated module inventory check

**Acceptance:** No “temporary compatibility module” remains release-blocking without a concrete migration ticket.

---

### Group 3 — First-class Data Cloud domain contracts

**Goal:** Replace generic entity-backed product concepts with canonical product APIs.

**Change:**

* `products/data-cloud/contracts/openapi/data-cloud.yaml`
* `delivery/launcher/.../DataCloudRouterBuilder.java`
* `delivery/launcher/.../handlers/*`
* `delivery/ui/src/lib/api/collections.ts`
* `delivery/ui/src/pages/DataExplorer.tsx`

**Tasks:**

* Introduce first-class Collection/Dataset/DataSource contracts.
* Keep generic entity storage as implementation detail.
* Add backend-owned lifecycle, owner, quality, retention, lineage, and permission fields.
* Update UI client to use canonical endpoints.
* Add direct deep-link loading for `/data/:id`.

**Tests/verification:**

* OpenAPI parity tests
* Collection API integration tests
* Data Explorer direct deep-link component/E2E tests

**Acceptance:** Data Explorer does not need generic `/entities/dc_collections` knowledge.

---

### Group 4 — Connectors and Data Fabric production path

**Goal:** Make source connection and ingestion usable end-to-end.

**Change:**

* Connector handlers/services
* `withConnectorRoutes`
* `DataConnectorsPage`
* operations/job center
* route manifest/OpenAPI

**Tasks:**

* Ensure connector handler is production-wired.
* Replace generic 503-only behavior with runtime truth states surfaced in UI.
* Add credential rotation, health, schema preview, sync job, and sync status.
* Link connector sync output to dataset/collection registry.

**Tests/verification:**

* API contract tests
* connector journey E2E
* degraded-service UI test

**Acceptance:** A user can create/test/sync a connector and see resulting data and job state.

---

### Group 5 — UI IA and 0-cognitive-load pass

**Goal:** Make the product simple, discoverable, and coherent.

**Change:**

* `RouteSurfaceRegistry.ts`
* `DefaultLayout.tsx`
* `routes.tsx`
* primary pages: Data, Events, Pipelines, Query, Trust, Operations, Insights, Agents, Plugins, Connectors

**Tasks:**

* Align sidebar with canonical navigation: Home, Data, Events, Query, Pipelines, Trust, Operations.
* Use progressive disclosure for advanced surfaces rather than hiding them completely.
* Fix `buildNavFromRegistry` vs static `navSections` split.
* Add route cards/action cards from backend/runtime truth contracts.
* Replace raw developer JSON panels with human-friendly summaries and expandable technical details.

**Tests/verification:**

* route truth tests
* global search tests
* navigation E2E
* role/feature-flag matrix tests

**Acceptance:** All core product capabilities are findable without memorizing URLs.

---

### Group 6 — Data Explorer action and drilldown correctness

**Goal:** Ensure drilldown preserves meaning and adds detail.

**Change:**

* `DataExplorer.tsx`
* collection detail components
* lineage/quality/schema components
* collection API client

**Tasks:**

* Load selected collection from route param, not only click-state.
* Make each collection row action deterministic: view, edit, quality, lineage, schema, delete/archive where permitted.
* Implement “More actions” menu or remove it.
* Ensure clicking quality value opens quality detail, lineage opens lineage detail, records opens data records.
* Fix advisory route mismatch text.

**Tests/verification:**

* `/data/:id?view=quality|lineage|schema` E2E
* click-through tests from cards/rows to details
* API mock contract tests

**Acceptance:** Drilldown never changes meaning; it shows a deeper version of the clicked concept.

---

### Group 7 — Action Plane/AEP lifecycle hardening

**Goal:** Make Action Plane a governed automation product surface.

**Change:**

* Action pipeline/execution handlers
* agent runtime
* learning/review handlers
* memory handlers
* plugin handlers
* Action Plane OpenAPI

**Tasks:**

* Define pipeline lifecycle states and transitions.
* Add idempotency to mutating Action Plane routes.
* Add execution run ledger with logs, checkpoints, retries, rollback, cancellation, policy decisions.
* Wire learning review approve/reject into policy promotion and audit.
* Make agents and memory first-class UI surfaces with governance.

**Tests/verification:**

* action-plane API lifecycle tests
* pipeline E2E
* policy/audit tests
* agent memory lifecycle tests

**Acceptance:** Pipeline/agent action lifecycle is executable, observable, auditable, and reversible.

---

### Group 8 — Audio-video as Data Cloud modality

**Goal:** Integrate AV into Data Cloud rather than leaving it as parallel shared service.

**Change:**

* `products/audio-video/*`
* Data Cloud contracts
* Data Cloud UI route/surface registry
* Action Plane bridge

**Tasks:**

* Add AV asset/entity model: file, stream, transcript, frame index, metadata, consent, retention.
* Add ingestion and processing job APIs.
* Connect multimodal/STT/vision outputs to Data Cloud search/catalog/context.
* Add Action Plane trigger: AV event → pattern/agent/pipeline.
* Add AV governance: retention, redaction, legal hold, consent.

**Tests/verification:**

* AV ingest API tests
* multimodal processing integration tests
* UI journey tests
* privacy/retention tests

**Acceptance:** Audio/video can be ingested, processed, searched, governed, and used by agents/patterns.

---

### Group 9 — i18n/a11y/design-system consolidation

**Goal:** Remove raw strings and page-local visual drift.

**Change:**

* `delivery/ui/src/**/*.tsx`
* translation files
* design-system/shared UI components

**Tasks:**

* Replace raw strings with i18n keys.
* Move badges, tables, cards, filters, buttons, empty/error/loading states into shared components.
* Ensure focus, keyboard, screen-reader, and contrast coverage.
* Remove page-local color intent where explicit labels/status should carry meaning.

**Tests/verification:**

* i18n raw-text scan
* axe tests
* component tests for common states

**Acceptance:** No user-visible raw strings in Data Cloud UI except allowed test/dev strings.

---

### Group 10 — Observability and operations ledger

**Goal:** One operational truth for every async workflow.

**Change:**

* operations plane
* runtime truth registry
* job center
* connector/pipeline/agent/AV handlers
* audit events

**Tasks:**

* Add unified operation/run model.
* Emit structured audit/domain events for every mutation.
* Surface retries, failures, dead-letter/recovery, policy decisions, and runtime dependencies.
* Link UI pages to operation details.

**Tests/verification:**

* operation ledger integration tests
* error-path tests
* runtime truth degraded/unavailable tests

**Acceptance:** Operators can debug failed connector sync, pipeline run, agent action, or AV job from one place.

---

## 14. Priority Roadmap

**P0 — Production blockers**

* Fix product registry/conformance mismatch.
* Finish Action Plane/AEP ownership boundary.
* Replace generic collection registry with first-class contracts.
* Make connector ingestion production-usable.
* Add idempotency to remaining mutating routes or explicitly classify non-idempotent routes.

Expected readiness improvement: **2.9 → 3.5**

**P1 — Coherent product completeness**

* Align UI navigation with canonical architecture.
* Promote Events, Connectors, Agents, Plugins, and Insights into coherent progressive-disclosure surfaces.
* Harden Data Explorer drilldowns.
* Add Action Plane lifecycle E2E.
* Add audio-video Data Cloud modality journey.

Expected readiness improvement: **3.5 → 4.1**

**P2 — Hardening and extensibility**

* Plugin lifecycle conformance.
* Runtime truth + operations ledger for all async workflows.
* Security/privacy matrix tests.
* Cross-plane architecture enforcement.

Expected readiness improvement: **4.1 → 4.5**

**P3 — Polish and optimization**

* UI visual polish.
* Performance budgets.
* Advanced search/discovery.
* Storybook/design-system completion.
* Developer command simplification.

Expected readiness improvement: **4.5 → 4.8+**

## Final Recommendation

Data Cloud at this commit is **not production-ready**, but it has a strong production-grade foundation.

The next implementation pass should focus on:

1. **Product truth and boundary cleanup**
2. **First-class Data Cloud collection/source/dataset contracts**
3. **Connector → ingest → dataset → quality/lineage → operations E2E journey**
4. **UI IA/navigation/discoverability cleanup**
5. **Action Plane lifecycle hardening**

Do **not** spend the next pass generating more release evidence artifacts. The code already has many readiness/evidence scripts; the gap is product completeness, deterministic journeys, UI simplicity, and reducing transitional architecture.
