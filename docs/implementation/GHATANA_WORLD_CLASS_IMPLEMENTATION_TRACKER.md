## 1. Executive Summary

I audited the repository at commit `495d21bcd79a5cdb8b6af3197ce981b086344895` using the repo connector. I did **not** run Gradle, pnpm, Playwright, or CI locally, so this is a deterministic source-code/docs/config audit, not an executed test report. The commit was resolved successfully from `samujjwal/ghatana`. 

**Overall production-readiness score: 2.4 / 5.0**

Data Cloud has a serious foundation: plane-based architecture, generated module registration, runtime surface concepts, UI route registry, role-disclosed navigation, typed backend surface records, tenant-context resolution, and broad Data Cloud/AEP/agent modules. But it is **not production-ready yet** because key contracts drift, product boundary decisions conflict, security/observability/reliability are still optional in critical places, and multiple important surfaces are preview/boundary/gated rather than complete end-to-end.

### Top blockers

1. **Runtime Truth API contract is broken between backend and frontend.** Backend returns `surfaces` as a list of `SurfaceRecord` maps, while the UI schema expects `surfaces` as a record/object map. This can break surface gating and route availability.  
2. **Canonical product boundary is inconsistent.** README and plane architecture say AEP is separate and Data Cloud must not own AEP semantics, while the unified product vision says AEP is no longer a separate customer-facing product and is the Action Plane runtime.   
3. **Production security is structurally present but too dependent on optional wiring.** API-key resolver, JWT provider, audit service, policy engine, transaction manager, idempotency stores, metrics, and trace export are optional or default to local/no-op/in-memory behavior.   
4. **UI shell role is explicitly disclosure-only**, so every backend route must independently enforce authorization; the audit found good request-context infrastructure, but not enough proof that every action route is policy-enforced end-to-end. 
5. **Several major product surfaces are preview/boundary rather than complete.** Agents, memory, entities, context, fabric, alerts are preview; settings is boundary.  
6. **Action Plane API direction is documented but not fully normalized.** The HLD says canonical Action Plane APIs should be `/api/v1/action/*`, while legacy AEP-backed endpoints may remain temporarily. 
7. **Observability exists as a design goal, but runtime export is optional.** Metrics default to no-op unless configured, and trace export can generate spans but discard them if no service is attached.  
8. **Audio-video is moduleized but not proven as a first-class Data Cloud modality.** The generated registry includes audio-video modules, and STT/multimodal services depend on governance/security/audit/observability pieces, but inspected files do not prove integration into Data Cloud catalog, events, pipelines, agents, or AEP workflows.   
9. **Docs and module classification drift.** README says API contract and integration-test modules are advisory, but the active-module script has an empty advisory set and marks those modules release-blocking.  
10. **i18n is incomplete.** The UI still has raw labels/descriptions and raw loading text in route/app code.  

---

## 2. Scope Inspected

Inspected representative canonical docs, module registries, UI routing/runtime truth code, backend runtime truth/security/server composition code, and audio-video module build files:

* Data Cloud README and canonical plane/product docs.  
* Generated Gradle module registry and active-module classifier.  
* UI package, routes, route registry, feature gates, API client, session/token storage, and Runtime Truth client schema.   
* Backend HTTP server, Runtime Truth handler, `SurfaceRecord`, request context resolver, and handler support.    
* Audio-video root, multimodal, and STT build files.   

---

## 3. Current Product Map

Data Cloud is documented as Ghatana’s governed operational data fabric, with trusted operational data, metadata, schemas, durable storage-plane events, governed context, intelligence substrate, policy evidence, and pluggable persistence. 

Current generated modules show Data Cloud has core planes for shared SPI, data/entity, event/core/store, operations/config, intelligence/analytics/feature-ingest, governance/core, delivery runtime/API/launcher/SDK/UI/contracts, integration tests, agent registry/catalog, kernel bridge, and a large Action Plane/AEP set under `products:data-cloud:planes:action:*`. 

Audio-video is registered separately as a shared-service product with infrastructure, multimodal, STT, TTS, vision, common libs, and integration-test modules. 

---

## 4. Readiness Scorecard

| Dimension                      |   Score | Rationale                                                                                     |
| ------------------------------ | ------: | --------------------------------------------------------------------------------------------- |
| Product coherence              |     3.0 | Strong plane model, but AEP boundary conflicts across canonical docs.                         |
| Feature completeness           |     2.5 | Many modules exist, but several user-facing surfaces are preview/boundary.                    |
| E2E workflow completeness      |     2.3 | Journeys are well designed, but code still has optional dependencies and contract drift.      |
| Data Cloud core architecture   |     3.0 | Planes and generated modules are credible; runtime truth contract mismatch blocks readiness.  |
| AEP architecture/integration   |     2.2 | Action modules exist, but ownership semantics conflict.                                       |
| Agent architecture/integration |     2.0 | Agent catalog/runtime surfaces exist, but UI is preview and lifecycle evidence is incomplete. |
| Audio-video integration        |     1.8 | Services exist, but Data Cloud modality integration is not proven.                            |
| Shared library quality         |     2.5 | Reuse rules exist, but dependency sprawl and product-boundary leakage remain risks.           |
| UI/UX simplicity               |     3.0 | Unified routes and gates exist, but many preview surfaces and raw strings remain.             |
| Backend/API correctness        |     2.4 | Broad handlers exist, but `/surfaces` contract drift is P0.                                   |
| Plugin/extensibility           |     2.7 | Plugin routes/modules exist; lifecycle/isolation/versioning still needs hardening.            |
| Security/authorization         |     2.4 | Good resolver design, but production enforcement depends on optional wiring.                  |
| Privacy/governance             |     2.2 | Governance modules exist; audit/policy can be optional.                                       |
| Observability/operations       |     2.5 | Runtime truth exists; metrics/traces can be no-op/discarded.                                  |
| Reliability/failure handling   |     2.2 | Idempotency, transactions, outbox are optional/fallback.                                      |
| Performance/scalability        |     2.1 | Rate limiting and streaming concepts exist; load/backpressure proof is limited.               |
| i18n readiness                 |     1.7 | Raw UI strings remain in app/routes.                                                          |
| Accessibility readiness        |     2.5 | A11y scripts/deps exist; execution not verified here.                                         |
| Test quality/coverage          |     2.5 | Test scripts/modules exist; contract drift proves gaps remain.                                |
| Developer experience           |     3.2 | Generated registry, scripts, docs, Gradle tasks are strong.                                   |
| Config/feature flags           |     3.0 | Feature gates have production-safe defaults, but behavior must align with runtime truth.      |
| Docs/code alignment            |     1.8 | Major AEP boundary and module-classification mismatches.                                      |
| Deployment readiness           |     2.0 | Production-critical services are optional by default.                                         |
| Maintainability/SRP/DRY        |     2.5 | Handler extraction exists, but `DataCloudHttpServer` is still a large composition hotspot.    |
| Overall readiness              | **2.4** | Not production-ready; ready for a hardening pass.                                             |

---

## 5. Feature Completeness Matrix

| Capability          | Current state                                                  | Gap                                                                                      | Severity |
| ------------------- | -------------------------------------------------------------- | ---------------------------------------------------------------------------------------- | -------- |
| Runtime Truth       | Backend has typed `SurfaceRecord`; UI has runtime gate/client. | Backend returns list; frontend expects record map.                                       | P0       |
| Data/entity         | Entity APIs, validation, export, anomaly hooks exist.          | Several capabilities depend on optional services; transaction/idempotency not mandatory. | P0/P1    |
| Events              | Event plane/modules and event routes exist.                    | AEP/EventCloud ownership unclear; replay/tail/pattern E2E not proven.                    | P1       |
| Query/analytics     | Analytics/query surfaces and handlers exist.                   | Trino/OpenSearch can fall back or be absent; degraded behavior needs UX/API guarantees.  | P1       |
| Pipelines/workflows | UI routes and workflow execution capability exist.             | DATA_LOCAL vs AEP_AGENTIC lifecycle not fully proven end-to-end.                         | P1       |
| AEP/patterns        | Action Plane modules exist.                                    | PatternSpec/EPL/adaptive learning ownership conflicts with Data Cloud docs.              | P0/P1    |
| Agents              | Agent catalog/registry modules and preview UI exist.           | Runtime governance, memory, HITL, approval, and audit loops need completion.             | P1       |
| Audio-video         | STT, multimodal, vision, infra modules exist.                  | Not integrated as Data Cloud dataset/event/pipeline/agent modality.                      | P1       |
| Governance/security | Request context resolver is strong.                            | Optional auth/policy/audit wiring blocks production trust.                               | P0       |
| UI shell            | Unified routing, lazy pages, role gates, runtime gates exist.  | Preview/boundary surfaces, raw strings, disabled behavior, route/contract drift.         | P1       |
| Tests               | Unit/integration/e2e/a11y scripts exist.                       | Minimum contract/security/E2E tests are missing or insufficient.                         | P0/P1    |

---

## 6. End-to-End Journey Analysis

| Journey                       | Status  | Main gap                                                                                    |
| ----------------------------- | ------- | ------------------------------------------------------------------------------------------- |
| Create/connect data source    | Partial | Connectors route exists, but connector lifecycle/plugin validation is not proven.           |
| Ingest dataset/stream         | Partial | Entity/event modules exist; ingestion workflow is not fully user-journey complete.          |
| Define schema/contract        | Partial | Contracts exist, but frontend/backend generated contract alignment is incomplete.           |
| Run quality checks            | Partial | Quality concepts exist; production quality lifecycle and UI action path need hardening.     |
| Build/run pipeline            | Partial | Pipeline UI/routes exist; run ledger, validation, rollback, policy impact need full E2E.    |
| Detect event patterns         | Partial | AEP/action modules exist; ownership and public Action API remain unsettled.                 |
| Adaptive feedback             | Partial | Docs define learning loops; implementation proof is incomplete.                             |
| Trigger agent workflow        | Partial | Agent catalog/runtime exists, but preview UI and governance lifecycle gaps remain.          |
| Process audio/video asset     | Weak    | Audio-video services exist but Data Cloud workflow integration is not demonstrated.         |
| Search/catalog/discover data  | Partial | Data/search surfaces exist; OpenSearch is optional and may return unavailable behavior.     |
| Govern/access/share data      | Partial | Request context exists; policy/audit must be fail-closed everywhere.                        |
| Observe/debug failed job      | Partial | Runtime Truth and operations UI exist; metrics/traces can be no-op/discarded.               |
| Administer plugins/config     | Partial | Plugins active; settings boundary; plugin lifecycle needs production hardening.             |
| Normal user, 0 cognitive load | Partial | IA is improving, but preview/boundary concepts and raw route strings still leak complexity. |

The target journeys are well articulated in the HLD, especially data exploration, query-to-action, pipeline creation, HITL review, and degraded-surface investigation.  

---

## 7. UI/UX Findings

The UI has a solid foundation: React Router, lazy-loaded pages, route guards, runtime capability gates, and compatibility routes.  

Key gaps:

* **Runtime gating may fail** because `/surfaces` shape mismatches the UI schema and client normalization.  
* **Disabled feature behavior is not always user-friendly.** `featureGatedRoute` returns NotFound when disabled, which hides whether a feature is unavailable, disabled, or not permitted. 
* **Route registry still exposes raw labels/descriptions** instead of i18n keys. 
* **Several important surfaces are preview**, which is acceptable for development but not for a production-grade, low-cognitive-load product. 
* **Settings is a boundary route**, so admin/configuration is not yet production-complete. 

---

## 8. Architecture and Boundary Findings

The architecture has the right vocabulary: planes, surfaces, modules, runtime truth, and explicit dependency rules. The canonical plane doc forbids Data/Event/Context/Governance from importing Action/AEP implementation internals and forbids Data Cloud from importing PatternSpec/EPL/EventOperator runtime semantics. 

However:

* **AEP boundary is unresolved.** One doc says AEP is separate; another says AEP is the Action Plane runtime inside Data Cloud. This must be resolved before more code movement.  
* **Action modules are still embedded under Data Cloud**, including engine, registry, analytics, security, event bridge, agent runtime, orchestrator, server, identity, compliance, and kernel bridge. 
* **Some kernel bridge modules are included manually outside the canonical registry**, which weakens the “generated registry is source of truth” story. 
* **Shared-platform rules are documented but need enforcement.** The plane architecture explicitly calls out `agent-core`, `workflow`, `messaging`, `ai-integration`, `data-governance`, and `platform:contracts` as candidates needing review/splitting. 

---

## 9. Security, Privacy, Governance Findings

Strong pieces exist. `RequestContextResolver` rejects tenant spoofing from headers/query params in production/staging/sovereign profiles and requires tenant identity from authenticated principal in those profiles.  

But production hardening is incomplete:

* API-key resolver is optional; if not called, the security filter is not activated. 
* JWT provider is optional. 
* Audit service can be null, and audit emission is silently skipped. 
* Token storage currently supports memory/sessionStorage token storage, while the file itself says httpOnly SameSite cookies are the recommended migration target. 
* Shell role is explicitly not backend authorization, so backend policy tests must cover every mutating/read-sensitive route. 

---

## 10. Observability, Reliability, Performance Findings

Runtime Truth is conceptually strong. `SurfaceRecord` includes state, owner plane, dependencies, probe results, tenant scope, runtime profile, evidence, action gates, and runtime posture. 

Production gaps:

* Metrics default to no-op unless explicitly configured. 
* Trace export can be absent, causing spans to be generated but not persisted. 
* Idempotency stores can fall back to in-memory. 
* Transaction manager is optional; without it, multi-step writes run without transaction boundaries. 
* Search, reports, AI model registry, feature store, and federated query capabilities rely on optional services or fallbacks.   

---

## 11. Test and Verification Gap Analysis

Existing test infrastructure is promising: the UI package has unit, integration, E2E, a11y, contract, readiness, route-doc, API-type, and Storybook scripts. 

Minimum missing verification:

1. Java + TypeScript contract test for `/api/v1/surfaces`.
2. UI E2E test proving runtime-gated routes render correctly for LIVE, PREVIEW, DEGRADED, DISABLED, MISCONFIGURED, and UNAVAILABLE.
3. Backend production-profile security tests for rejected `X-Tenant-ID`, rejected `tenantId` query param, missing JWT/API key, support-access delegation, and policy-denied routes.
4. Agent/AEP lifecycle tests for pattern → run → review → approve/reject → learning/audit.
5. Audio-video integration tests for asset ingest → metadata/transcript/event extraction → Data Cloud catalog/search → agent/pipeline action.
6. i18n raw-string scan for Data Cloud UI route registry/pages.
7. Observability tests proving audit, trace, metric, and correlation IDs exist for success and failure paths.

---

## 12. Consolidated Task Plan Grouped to Minimize Verification Passes

### Group 1 — Runtime Truth contract unification

**Goal:** Fix the P0 `/surfaces` backend/frontend contract drift.

**Change:**

* Choose one canonical shape:

  * preferred: `surfaces: SurfaceRecord[]`, because backend already has typed records; or
  * alternative: `surfaces: Record<string, SurfaceRecord>`.
* Update `products/data-cloud/contracts/openapi/data-cloud.yaml`.
* Update `SurfaceRegistryHandler`.
* Update `SurfaceRegistryEnvelopeSchema`.
* Update `surfaces.service.ts`.
* Update `RuntimeCapabilityRouteGate` tests.

**Files:**

* `products/data-cloud/delivery/launcher/.../SurfaceRegistryHandler.java`
* `products/data-cloud/delivery/launcher/.../SurfaceRecord.java`
* `products/data-cloud/delivery/ui/src/contracts/schemas.ts`
* `products/data-cloud/delivery/ui/src/api/surfaces.service.ts`
* `products/data-cloud/contracts/openapi/data-cloud.yaml`

**Verification:**

```bash
./gradlew :products:data-cloud:delivery:launcher:test
pnpm --dir products/data-cloud/delivery/ui run test:contract
pnpm --dir products/data-cloud/delivery/ui run test:readiness
```

**Acceptance:** UI route gating works from real `/api/v1/surfaces` response without shape adapters or mocks.

---

### Group 2 — Product boundary and AEP/Action Plane decision pass

**Goal:** Make one canonical decision: AEP is either separate with SPI-only integration, or Action Plane runtime inside Data Cloud.

**Change:**

* Reconcile README, `PLANE_ARCHITECTURE.md`, and unified product docs.
* If AEP is Action Plane runtime, remove “separate product” language.
* If AEP is separate, remove “merged product boundary” language.
* Normalize public APIs to `/api/v1/action/*`.
* Add architecture tests for forbidden imports.

**Files:**

* `products/data-cloud/README.md`
* `products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md`
* `products/data-cloud/docs/product/*.md`
* `products/data-cloud/contracts/openapi/action-plane.yaml`
* `products/data-cloud/planes/action/**`
* architecture/boundary test scripts

**Verification:**

```bash
node scripts/check-data-cloud-boundary-language.mjs
node scripts/check-dependency-boundaries.mjs
./gradlew :products:data-cloud:contracts:build
```

**Acceptance:** No canonical docs disagree on AEP ownership; boundary checks prevent regression.

---

### Group 3 — Production security/governance fail-closed pass

**Goal:** Make production mode safe by construction.

**Change:**

* Production/staging/sovereign profiles must require JWT or API key resolver.
* Audit service must be mandatory for sensitive/mutating routes.
* Policy engine must fail closed for governed routes.
* Replace browser token storage path with httpOnly cookie-backed production mode.
* Add endpoint permission matrix tests.

**Files:**

* `DataCloudHttpServer.java`
* `RequestContextResolver.java`
* `DataCloudSecurityFilter.java`
* `HttpHandlerSupport.java`
* `tokenStorage.ts`
* backend security tests

**Verification:**

```bash
./gradlew :products:data-cloud:delivery:launcher:test
./gradlew :products:data-cloud:integration-tests:test
```

**Acceptance:** No production profile starts with unauthenticated critical routes, missing audit, missing policy, or tenant fallback.

---

### Group 4 — Backend capability and workflow completeness pass

**Goal:** Complete the main Data Cloud journeys with backend-owned state and contracts.

**Change:**

* Entities: schema, quality, lineage, versions, import/export, idempotent writes.
* Events: append/query/replay/tail, correlation IDs, replay safety.
* Query: SQL/NL query, freshness, policy scope, degraded result explanation.
* Pipelines: draft/validate/publish/run/retry/cancel/rollback.
* Governance: retention/redaction/audit/classification as first-class backend flows.

**Files:**

* `planes/data/**`
* `planes/event/**`
* `planes/governance/**`
* `planes/intelligence/**`
* `delivery/api/**`
* `delivery/launcher/**`

**Verification:**

```bash
./gradlew :products:data-cloud:delivery:api:test
./gradlew :products:data-cloud:delivery:launcher:test
./gradlew :products:data-cloud:integration-tests:test
```

**Acceptance:** Each major journey has backend contract, state transition, audit, idempotency, and failure behavior.

---

### Group 5 — UI shell, navigation, i18n, and 0-cognitive-load pass

**Goal:** Make the UI powerful but simple.

**Change:**

* Replace raw route labels/descriptions/loading text with i18n keys.
* Replace disabled feature → NotFound with Disabled/Unavailable surface page.
* Use one table/list/card/action pattern.
* Hide preview/boundary surfaces unless runtime truth and feature flags allow them.
* Align route registry lifecycle with backend Runtime Truth states.

**Files:**

* `delivery/ui/src/lib/routing/RouteCapabilityRegistry.ts`
* `delivery/ui/src/routes.tsx`
* `delivery/ui/src/lib/feature-gates.ts`
* `delivery/ui/src/pages/**`
* `delivery/ui/src/i18n/**`

**Verification:**

```bash
pnpm --dir products/data-cloud/delivery/ui run lint
pnpm --dir products/data-cloud/delivery/ui run type-check
pnpm --dir products/data-cloud/delivery/ui run test:unit
pnpm --dir products/data-cloud/delivery/ui run test:e2e:a11y
```

**Acceptance:** No raw user-visible strings, no fake live surfaces, no broken disabled states.

---

### Group 6 — Agent/AEP lifecycle hardening pass

**Goal:** Make agents and adaptive event processing production workflows, not preview demos.

**Change:**

* Define canonical pattern, agent, run, review, learning, approval, and audit lifecycle.
* Enforce governance before execution.
* Add HITL approve/reject/escalate flows.
* Emit learning/audit events on decisions.
* Normalize Action Plane APIs.

**Files:**

* `planes/action/engine/**`
* `planes/action/agent-runtime/**`
* `planes/action/orchestrator/**`
* `planes/action/registry/**`
* `extensions/agent-registry/**`
* `extensions/agent-catalog/**`

**Verification:**

```bash
./gradlew :products:data-cloud:planes:action:engine:test
./gradlew :products:data-cloud:planes:action:agent-runtime:test
./gradlew :products:data-cloud:planes:action:orchestrator:test
./gradlew :products:data-cloud:integration-tests:test
```

**Acceptance:** Pattern → run → review → decision → learning → audit is tested end-to-end.

---

### Group 7 — Audio-video modality integration pass

**Goal:** Make audio-video a first-class Data Cloud modality.

**Change:**

* Add Data Cloud asset/catalog model for audio/video.
* Ingest transcripts, metadata, frames, embeddings, and events.
* Connect STT/TTS/vision/multimodal services to Data Cloud events and pipelines.
* Add consent, retention, redaction, and audit for media.
* Add agent tools for media analysis with policy gates.

**Files:**

* `products/audio-video/**`
* `products/data-cloud/planes/data/**`
* `products/data-cloud/planes/event/**`
* `products/data-cloud/planes/governance/**`
* `products/data-cloud/planes/action/**`

**Verification:**

```bash
./gradlew :products:audio-video:modules:integration-tests:test
./gradlew :products:audio-video:modules:speech:stt-service:smokeTestMainClass
./gradlew :products:audio-video:modules:intelligence:multimodal-service:smokeTestMainClass
./gradlew :products:data-cloud:integration-tests:test
```

**Acceptance:** Media asset ingest produces searchable governed data, events, audit trail, and optional agent/pipeline actions.

---

### Group 8 — Shared library cleanup and dependency-sprawl pass

**Goal:** Keep shared libraries truly shared.

**Change:**

* Review `agent-core`, `workflow`, `messaging`, `ai-integration`, `data-governance`, `platform:contracts`.
* Move Data Cloud or AEP-specific semantics out of generic platform modules.
* Remove unused UI workspace dependencies.
* Add dependency boundary tests.

**Files:**

* `platform/java/**`
* `platform/contracts/**`
* `products/data-cloud/**`
* `products/data-cloud/delivery/ui/package.json`

**Verification:**

```bash
node scripts/check-dependency-boundaries.mjs
pnpm --dir products/data-cloud/delivery/ui run lint
./gradlew test
```

**Acceptance:** No product-specific semantics leak into shared modules without explicit SPI.

---

## 13. Priority Roadmap

### P0 — Production blockers

* Fix `/surfaces` contract mismatch.
* Resolve AEP/Data Cloud boundary.
* Make production auth/audit/policy/idempotency/transaction config fail closed.
* Add minimum contract/security tests.

Expected score improvement: **2.4 → 3.1**

### P1 — Coherent product completeness

* Complete entity/event/query/pipeline/governance journeys.
* Normalize `/api/v1/action/*`.
* Complete agent/AEP review/learning/audit loop.
* Replace preview/boundary UI leakage with runtime-truth-driven progressive disclosure.

Expected score improvement: **3.1 → 3.7**

### P2 — Extensibility and modality hardening

* Audio-video as first-class Data Cloud modality.
* Plugin lifecycle/versioning/isolation.
* Shared library cleanup.
* Observability and operations hardening.

Expected score improvement: **3.7 → 4.2**

### P3 — Polish and optimization

* Full i18n/a11y polish.
* Performance/load tests.
* UI visual consistency and 0-cognitive-load refinements.
* Developer-experience cleanup.

Expected score improvement: **4.2 → 4.6+**

---

## 14. Final Recommendation

Data Cloud at this commit is **not production-ready**, but it has enough structure to become production-grade through a focused hardening sequence.

The **next implementation pass should start with Group 1 and Group 2 together**: fix Runtime Truth contract drift and settle the Data Cloud/AEP boundary. Those two issues affect every route, every Action Plane feature, every UI gate, every test, and every production-readiness score.

Do **not** prioritize release evidence generation yet. Also do not expand new features until the Runtime Truth contract, product boundary, production security defaults, and end-to-end journey tests are stable.

Minimum path to a coherent production-grade Data Cloud product suite:

1. Fix `/surfaces` contract.
2. Resolve AEP/Action Plane ownership.
3. Enforce production fail-closed security/governance/observability dependencies.
4. Complete Data/Event/Query/Pipeline/Governance journeys.
5. Harden Agent/AEP lifecycle.
6. Integrate audio-video as a governed Data Cloud modality.
7. Finish UI/i18n/a11y/test passes grouped around the same verification runs.
