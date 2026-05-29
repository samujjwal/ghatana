## 1. Executive summary

I audited the `samujjwal/ghatana` snapshot at commit `c5eb1dd4aed6998c8d8bed2475586e78ce0bd23e`. The commit itself only changes `products/yappc/CHANGELOG.md`; the Data Cloud audit therefore reflects the full repository state at that commit, not the small commit diff. 

**Overall production-readiness score: 2.8 / 5**

Data Cloud is moving in the right direction: it now has a canonical plane architecture, Data Cloud owns the Action Plane/AEP boundary, active Gradle modules are classified, and there are meaningful backend/UI/runtime-truth contracts. The product is not production-ready yet because the most important blockers are **contract drift**, **runtime-route mismatch**, **UI/backend auth mismatch**, **partial idempotency**, **preview/demo surfaces still visible as product capabilities**, **i18n/raw text gaps**, and **AEP/audio-video integration gaps**.

Top blockers:

1. **Route/runtime truth drift** between `RouteSurfaceMapping`, `DataCloudRouterBuilder`, generated route manifest, OpenAPI, UI routes, and E2E tests.
2. **UI sends `X-Tenant-ID`**, while production backend tenant resolution rejects tenant headers and requires authenticated identity.
3. **UI expects `X-Correlation-ID`**, while backend error responses emit `X-Request-Id`.
4. **Real-backend E2E tests assert stale/incorrect contracts**, including `/api/v1/surfaces` shape, `/api/v1/pipelines`, `/governance/compliance/summary`, and `/api/v1/sse`.
5. **Entity writes have production durability guards, but idempotency is explicitly incomplete for other mutating routes**.
6. **AEP still exposes a standalone route model that overlaps with Data Cloud’s canonical `/api/v1/action/*` namespace**.
7. **Audio-video documentation/module map does not match active generated modules**.
8. **Data Fabric is explicitly preview/experimental**, yet contains governed migration actions and AI suggestions.
9. **Many UI surfaces contain raw user-visible strings and hard-coded visual styles**, bypassing the i18n/design-system direction.
10. **Powerful surfaces exist but are mostly non-discoverable**, so the product is capable but not yet “0 cognitive load.”

Top strengths:

1. Canonical Data Cloud plane model is clear and current.
2. AEP has been conceptually moved into the Data Cloud Action Plane.
3. Active Data Cloud and Action Plane modules are release-blocking in the module classifier.
4. Runtime truth endpoint exists as `GET /api/v1/surfaces`.
5. Entity write lifecycle has transaction/outbox/audit/idempotency hardening for production-like profiles.
6. Backend has explicit tenant resolution logic with production-safe rejection of spoofed tenant headers.
7. UI uses lazy-loaded routes, route gates, role gates, runtime capability gates, and contract checks.
8. Data Cloud UI has readiness scripts for route manifest, route docs, generated API types, contract tests, and route inventory.
9. AEP server includes health/deep checks, SLO metrics, run ledger, PII scanner, consent, HITL, learning, governance, and policy hooks.
10. Audio-video STT has concrete validation for audio size, language, sample rate, streaming buffer limits, and metrics.

### Implementation Status Update (2026-05-28)

Scope executed in this pass: Group 1 (runtime truth/route contract convergence) and Group 2 (auth/tenant/correlation alignment) only.

- [x] Route surface mapping updated to canonical Action Plane routes and canonical runtime truth endpoints (`/api/v1/surfaces`, `/api/v1/surfaces/schema`).
- [x] UI API client tenant header propagation is now profile-aware: `X-Tenant-ID` is suppressed in production-like profiles.
- [x] UI API error correlation extraction now supports canonical `X-Request-Id` and preserves compatibility with `X-Correlation-ID`/payload trace IDs.
- [x] Real-backend E2E assertions updated to canonical paths and payloads (`/api/v1/action/pipelines`, `/api/v1/governance/compliance/summary`, `/api/v1/alerts/stream`, `data.surfaces[]`).
- [x] Focused tests executed and passing for touched UI contract/auth/runtime files.
- [x] Backend module compile verification executed and passing for touched launcher route mapping.

Verification evidence from this pass:

- `pnpm vitest run src/__tests__/api/client.contract.test.ts src/__tests__/api/runtimeTruth.test.ts src/__tests__/pages/ContractBacked.test.tsx` → 3 files passed, 68 tests passed.
- `pnpm playwright test e2e/critical-path-real-backend.spec.ts --list` → updated spec parses and enumerates tests successfully.
- `./gradlew :products:data-cloud:delivery:launcher:compileJava` → `BUILD SUCCESSFUL`.

Remaining work is still required for Groups 3-10, including full integration/runtime truth generation parity, production security matrix coverage, idempotency expansion, and end-to-end release-blocking verification.

### Implementation Status Update (2026-05-29)

Scope executed in this pass: governance policy idempotency parity (Group 3 continuation) for Data Cloud mutating policy routes.

- [x] Added idempotency replay and response caching for policy mutation handlers:
  - `POST /api/v1/governance/policies`
  - `PUT /api/v1/governance/policies/:id`
  - `DELETE /api/v1/governance/policies/:id`
  - `POST /api/v1/governance/policies/:id/toggle`
- [x] Enforced strict-profile idempotency parity on the same handlers:
  - missing key -> `MISSING_IDEMPOTENCY_KEY` (400)
  - missing durable store -> `IDEMPOTENCY_STORE_REQUIRED` (500)
- [x] Preserved local/dev behavior by keeping strict requirements profile-gated.

Verification evidence from this pass:

- `./gradlew :products:data-cloud:delivery:launcher:test --tests "com.ghatana.datacloud.launcher.http.DataCloudHttpServerGovernanceTest"` -> `BUILD SUCCESSFUL`.
- `./gradlew :products:data-cloud:integration-tests:test --tests "com.ghatana.datacloud.integration.ProductionTenantAuthProfileTest"` -> `BUILD SUCCESSFUL`.

---

## 2. Scope inspected

Inspected representative code and canonical registries across:

| Area                 | Files/directories inspected                                                                    |
| -------------------- | ---------------------------------------------------------------------------------------------- |
| Data Cloud docs      | `products/data-cloud/README.md`, `products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md` |
| Active modules       | `config/generated/settings-gradle-includes.kts`, `scripts/list-data-cloud-active-modules.mjs`  |
| Backend routing      | `DataCloudRouterBuilder.java`, `RouteSurfaceMapping.java`, `SurfaceRegistryHandler.java`       |
| Backend auth/context | `HttpHandlerSupport.java`, `RequestContextResolver.java`                                       |
| Data write lifecycle | `EntityCrudHandler.java`                                                                       |
| UI shell/routes      | `App.tsx`, `routes.tsx`, `DefaultLayout.tsx`, `RouteSurfaceRegistry.ts`                        |
| UI API client        | `lib/api/client.ts`                                                                            |
| Data Fabric UI       | `DataFabricPage.tsx`                                                                           |
| AEP/Action server    | `AepHttpServer.java`                                                                           |
| Audio-video          | generated Gradle module list, `capability-map.md`, STT build/readme/server/service             |
| Tests                | Data Cloud UI real-backend E2E, package readiness scripts                                      |
| Generated contracts  | `products/data-cloud/config/route-manifest.json`                                               |

I did not run Gradle, pnpm, Playwright, or CI locally. This is a deterministic source-code audit pass, not an executed test report.

---

## 3. Current product map

Data Cloud is explicitly positioned as the governed operational data fabric, and the README states that the Action Plane, formerly AEP, now lives inside Data Cloud under `planes/action`, providing automation, pipelines, patterns, agents, runs, reviews, and learning. 

The canonical architecture defines planes for Experience, Contract, Runtime Truth, Data, Event, Context, Intelligence, Governance, Action, and Operations. The Action Plane is described as the governed automation runtime for event-driven agent orchestration, pattern detection and learning, pipeline execution, human review, and observability. 

The generated settings file confirms active Data Cloud modules across shared SPI, Data, Event, Operations, Intelligence, Governance, Runtime Composition, Plugins, API, Launcher, SDK, Contracts, Agent Registry/Catalog, integration tests, feature ingest, event store, and Action Plane modules. 

Audio-video is currently a separate shared service with modules for persistence, security, cache, messaging, integration tests, multimodal, STT, TTS, vision, and shared/common libraries. 

---

## 4. Readiness scorecard

| Dimension                      |   Score | Rationale                                                                        |
| ------------------------------ | ------: | -------------------------------------------------------------------------------- |
| Product coherence              |     3.2 | Plane model is coherent, but UI/API/runtime truth still drift.                   |
| Feature completeness           |     2.7 | Many capabilities exist, but several are preview, partial, or disconnected.      |
| E2E workflow completeness      |     2.4 | Real-backend E2E exists but asserts stale contracts.                             |
| Data Cloud core architecture   |     3.3 | Strong plane/SPI direction; routing/domain contract needs consolidation.         |
| AEP architecture/integration   |     3.0 | Action Plane is active, but standalone AEP routes still overlap.                 |
| Agent architecture/integration |     2.8 | Agent catalog/runtime surfaces exist; full governed lifecycle is incomplete.     |
| Audio-video integration        |     2.3 | Audio-video service exists, but Data Cloud modality integration is not coherent. |
| Shared library quality         |     3.0 | Reuse is intentional; dependency sprawl is acknowledged.                         |
| UI/UX simplicity               |     2.8 | Good IA direction, but powerful surfaces are hidden and raw strings remain.      |
| Backend/API correctness        |     2.8 | Strong handler structure; route and response contracts drift.                    |
| Plugin/extensibility model     |     2.9 | Plugin routes and modules exist; lifecycle/version/isolation needs hardening.    |
| Security/authorization         |     2.8 | Tenant resolver is strong; UI/client/test assumptions conflict with it.          |
| Privacy/governance             |     3.0 | Governance, consent, PII, retention concepts exist; enforcement parity unclear.  |
| Observability/operations       |     3.1 | Health, metrics, SLO, trace/run-ledger concepts exist; not uniform yet.          |
| Reliability/failure handling   |     2.7 | Some production guards exist; many in-memory fallbacks remain.                   |
| Performance/scalability        |     2.6 | Streaming/backpressure exists in places; product-wide load behavior not proven.  |
| i18n readiness                 |     2.1 | Many raw user-visible strings remain.                                            |
| Accessibility readiness        |     2.7 | Some ARIA/test hooks exist; not uniformly enforced.                              |
| Test quality/coverage          |     2.7 | Good readiness scripts, but stale tests reduce trust.                            |
| Developer experience           |     3.3 | Module scripts and generated manifests are helpful.                              |
| Config/feature flags           |     3.0 | Feature gates exist; semantics still drift between backend/UI/tests.             |
| Docs/code alignment            |     2.3 | Data Cloud docs align better; audio-video docs do not match module layout.       |
| Deployment readiness           |     2.6 | Production guards exist but not complete across planes.                          |
| Maintainability/SRP/DRY        |     2.9 | Router decomposition improved; large AEP constructor/server remains heavy.       |
| Overall                        | **2.8** | Directionally strong, not yet production-grade.                                  |

---

## 5. Highest-impact findings

### Finding 1 — Runtime truth and route contracts are not single-source yet

`RouteSurfaceMapping` claims to map HTTP routes to surface IDs used by runtime truth, UI gating, authorization metadata, audit event types, OpenAPI metadata, and SDK feature flags.  But the map still contains root/legacy paths such as `/api/v1/pipelines`, `/api/v1/executions`, `/api/v1/plugins`, `/api/v1/autonomy`, `/api/v1/surfaces/typed`, and `/api/v1/surfaces/{id}`. 

Meanwhile, `DataCloudRouterBuilder` says canonical Action Plane routes should live under `/api/v1/action/*`, and pipeline, execution, memory, autonomy, agent catalog, and plugin routes are increasingly registered there.    

**Impact:** runtime truth, route manifest, OpenAPI, SDK, UI gating, and tests can disagree even when each individual file looks correct.

**Fix:** make `DataCloudRouterBuilder + RouteSecurityRegistry` the generator input for route manifest, OpenAPI route metadata, surface mapping, SDK flags, and UI route capabilities. Delete hand-maintained legacy surface mappings unless they are generated with explicit `legacyStatus`.

---

### Finding 2 — `/api/v1/surfaces` contract drift will break tests/UI consumers

`SurfaceRegistryHandler` returns an envelope containing `surfaces`, `count`, and `generatedAt`.  But the real-backend E2E test calls `/api/v1/surfaces` and asserts `data.data?.capabilities`, which does not match the handler shape. 

**Impact:** readiness tests can pass/fail for the wrong reason, and UI runtime truth consumers can drift from the backend.

**Fix:** update E2E, UI runtime truth service, generated API types, OpenAPI schema, and contract tests to assert `data.surfaces[]` with typed `SurfaceRecord`.

---

### Finding 3 — UI auth/tenant model conflicts with production backend rules

The backend production resolver rejects `X-Tenant-Id` and `tenantId` query parameters in production/staging/sovereign profiles and requires tenant identity from authenticated JWT/API key.  The UI API client unconditionally sets `X-Tenant-ID` when `SessionBootstrap.getTenantId()` returns a value. 

The real-backend E2E tests also use `X-Tenant-Id` heavily for collections, pipelines, governance, and SSE.  

**Impact:** production-like deployments can reject the UI/test traffic with 403 even if the product works in local mode.

**Fix:** make tenant header injection environment-aware and default-off for production-like profiles. Use cookie/JWT/API-key tenant claims. Update E2E to have two modes: local header fallback and production authenticated identity.

---

### Finding 4 — UI error correlation ID parsing is wrong

The backend error response emits `X-Request-Id`.  The UI error parser reads only `X-Correlation-ID`. 

**Impact:** users/operators cannot reliably correlate frontend errors to backend logs.

**Fix:** parse `X-Request-Id`, `X-Correlation-ID`, and response body `traceId`; normalize as `correlationId`.

---

### Finding 5 — Entity writes are hardened, but product-wide idempotency is incomplete

`EntityCrudHandler` has production validation requiring durable idempotency store, transaction manager, audit service, and outbox processor for production/staging/sovereign profiles.  But the same file explicitly documents that idempotency is only implemented for entity POST/batch and that pipelines, events, governance, and analytics still require idempotency or explicit non-idempotent documentation. 

**Impact:** retries can duplicate events, pipelines, governance operations, analytics jobs, and AI/agent actions.

**Fix:** add canonical idempotency middleware/helper for every mutating route, with operation-specific idempotency semantics.

---

### Finding 6 — AEP is integrated conceptually, but route/runtime boundaries remain split

The AEP server still describes itself as a standalone HTTP server for event processing, pattern management, anomaly detection, and forecasting.  It has many standalone root routes for `/api/v1/patterns`, `/api/v1/pipelines`, `/api/v1/agents`, `/api/v1/runs`, HITL, learning, AI suggestions, governance, and compliance. 

The Data Cloud architecture says Action Plane is now part of Data Cloud and old AEP modules map into `planes/action/*`. 

**Impact:** two product personalities exist: Data Cloud Action Plane and standalone AEP. That increases cognitive load and contract drift.

**Fix:** either make AEP server an internal Action Plane runtime behind Data Cloud `/api/v1/action/*`, or explicitly mark standalone AEP as dev/embedded compatibility with generated deprecation/alias contracts.

---

### Finding 7 — AEP still has in-memory fallbacks that need production closure

AEP uses Data Cloud stores when `agentDataCloud` is configured, but otherwise falls back to in-memory pipeline repository and in-memory run history with warnings; production run history fails closed unless an override is set.  It also falls back to in-memory change approval, recertification, kill switch, degradation manager, policy engine, consent store, and idempotency store when durable dependencies are not supplied. 

**Impact:** good for local dev, but not enough for production-grade Action Plane unless every fallback is profile-gated and exposed through runtime truth.

**Fix:** centralize Action Plane runtime profile validation and expose every degraded/ephemeral dependency through `/api/v1/surfaces`.

---

### Finding 8 — Data Fabric is explicitly not production-ready

`DataFabricPage` contains a preview banner saying the surface is experimental, disabled in production by default, and not recommended for production operational decisions.  The page also computes a frontend heuristic placement recommendation when backend AI advisories are absent. 

It asks for a migration reason in the UI, but the mutation only sends collection and target tier, not the reason.  

**Impact:** this is a powerful operational action surface, but it is still preview-grade and can lose audit context.

**Fix:** either keep Data Fabric fully preview/hidden or make tier migration backend-owned with reason, approval, audit, policy simulation, idempotency, and runtime truth.

---

### Finding 9 — UI is not fully i18n/design-system compliant

Routes and UI pages contain raw strings such as `Loading...`, `Failed to load page`, `Reload Page`, disabled-surface text, Data Fabric labels, preview notices, migration labels, and empty states.   

`DefaultLayout` also has static nav labels like `Home`, `Data`, `Pipelines`, `Query`, `Trust`, and `Operations`; only section headings are translated.  

**Impact:** product cannot claim i18n readiness, and UI consistency will degrade as more surfaces are added.

**Fix:** enforce one i18n path for all user-visible strings, including route metadata, disabled states, errors, banners, actions, page titles, table labels, and AI suggestions.

---

### Finding 10 — Audio-video is real but not coherently integrated with Data Cloud

The generated active module registry lists audio-video modules under `products/audio-video/modules/...`.  But the audio-video capability map references paths like `products/audio-video/apps/stt-service`, `infrastructure/stt`, and `core/stt`, which do not match the active generated module layout. 

The actual STT service exists and has a clear README, gRPC entry point, external Whisper dependency, observability note, and test command.   The implementation validates empty audio, max audio size, sample rate bounds, language codes, and streaming backpressure.  

**Impact:** audio-video appears production-oriented as a shared service, but it is not yet a first-class Data Cloud modality with catalog, ingestion jobs, retention, consent, lineage, search, and agent/AEP workflow integration.

**Fix:** define a Data Cloud media modality contract and integrate STT/TTS/vision/multimodal jobs into Data Cloud datasets/events/lineage/governance/runtime truth.

---

## 6. End-to-end journey analysis

| Journey                       | Current state                                                                                            | Gap                                                                                | Severity |
| ----------------------------- | -------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- | -------- |
| Connect data source           | Connector routes exist with canonical `/api/v1/connectors` and compatibility `/data-fabric/connectors`.  | Compatibility aliases remain; route/surface mapping must be generated.             | High     |
| Ingest dataset/entity         | Entity CRUD exists with validation, search, history, stream, batch.                                      | Entity model is stronger than dataset/catalog model; idempotency not universal.    | High     |
| Define schema/contract        | Entity validation endpoints exist.                                                                       | Need schema contract lifecycle, versioning, compatibility, UI flow.                | High     |
| Run quality checks            | Data-quality trust score route exists.                                                                   | Needs unified quality rules, drilldowns, lineage tie-in.                           | Medium   |
| Build/run pipeline            | Canonical `/api/v1/action/pipelines` exists.                                                             | E2E still uses `/api/v1/pipelines`; AEP standalone also uses root pipeline routes. | High     |
| Detect event patterns         | AEP pattern routes exist.                                                                                | Data Cloud UI/runtime truth integration needs consolidation.                       | High     |
| Adaptive feedback/learning    | Learning routes exist in both Data Cloud Action Plane and AEP.                                           | Needs one governed lifecycle and one route namespace.                              | High     |
| Trigger agent workflow        | AEP agent routes exist; Data Cloud action agent catalog routes exist.                                    | Runtime execution, policy, approval, memory, audit need one contract.              | High     |
| Process audio/video           | Audio-video STT service exists.                                                                          | No Data Cloud media ingestion/catalog/governance journey found.                    | High     |
| Search/catalog/discover       | Data Explorer exists; routes exist.                                                                      | Catalog/search journey needs backend-owned capability cards and coherent IA.       | Medium   |
| Govern/access/share           | Trust/governance routes exist.                                                                           | UI role switching can imply access changes client-side.                            | High     |
| Observe/debug failed job      | Operations/job/runtime truth pages exist.                                                                | Must unify job/run/execution semantics across Data Cloud and AEP.                  | High     |
| Administer plugins            | Plugin routes exist under canonical Action Plane.                                                        | Plugin isolation/versioning/conformance must be end-to-end.                        | Medium   |
| Normal user, 0 cognitive load | Main nav is simplified.                                                                                  | Advanced capabilities are hidden or preview; user journey is not yet self-evident. | Medium   |

---

## 7. Consolidated task plan grouped to minimize verification passes

### Group 1 — Canonical route/runtime truth/API contract pass

**Goal:** eliminate route, surface, OpenAPI, SDK, UI, and test drift in one pass.

**Change:**

* Generate `RouteSurfaceMapping` from `DataCloudRouterBuilder + RouteSecurityRegistry`.
* Remove stale `/api/v1/surfaces/typed` and `/api/v1/surfaces/{id}` surface mappings unless actual routes are restored.
* Normalize all Action Plane routes to `/api/v1/action/*`.
* Update `route-manifest.json`, OpenAPI, generated UI contracts, SDK flags, and route tests.
* Update E2E to assert `/api/v1/surfaces` returns `data.surfaces[]`, not `data.capabilities`.

**Files/areas:**

* `products/data-cloud/delivery/launcher/.../DataCloudRouterBuilder.java`
* `RouteSurfaceMapping.java`
* `RouteSecurityRegistry.java`
* `products/data-cloud/config/route-manifest.json`
* `products/data-cloud/contracts/openapi/*.yaml`
* `products/data-cloud/delivery/ui/src/contracts/generated/*`
* `products/data-cloud/delivery/ui/e2e/*`

**Verify:**

```bash
node scripts/list-data-cloud-active-modules.mjs --validate
cd products/data-cloud/delivery/ui
pnpm run check:route-manifest
pnpm run docs:routes:check
pnpm run check:api-types
pnpm run test:readiness
```

**Acceptance:** every route has one canonical path, one surface ID, one security policy, one generated client shape, and one matching test.

---

### Group 2 — Auth, tenant, request context, and correlation pass

**Goal:** make browser, backend, and tests production-auth compatible.

**Change:**

* Stop sending `X-Tenant-ID` from the UI in production-like profiles.
* Support local/test tenant header fallback only when runtime profile allows it.
* Update E2E to use authenticated principal/JWT/API-key tenant claim for production mode.
* Parse `X-Request-Id`, `X-Correlation-ID`, and body `traceId` in UI errors.
* Fix `HttpHandlerSupport.resolveTraceContext()` to parse W3C `traceparent` correctly.
* Validate workspace/project headers more strictly or bind them to authenticated context.

**Files/areas:**

* `products/data-cloud/delivery/ui/src/lib/api/client.ts`
* `HttpHandlerSupport.java`
* `RequestContextResolver.java`
* `SessionBootstrap`
* real-backend E2E tests

**Verify:**

```bash
cd products/data-cloud/delivery/ui
pnpm run test:readiness
pnpm run test:e2e
```

**Acceptance:** production-like profile rejects spoofed tenant headers, UI still works with authenticated identity, and every error shown to users includes a usable correlation ID.

---

### Group 3 — Mutating route idempotency, audit, and durability pass

**Goal:** make retries safe across the product, not only entity writes.

**Change:**

* Extend canonical idempotency to:

  * `POST /api/v1/events`
  * `POST/PUT/DELETE /api/v1/action/pipelines`
  * execution cancel/retry/rollback/restore
  * governance policy CRUD/toggle/simulate
  * connector sync/test/enable/disable/rotate
  * AI suggestion apply/feedback
  * plugin enable/disable/upgrade/validate/conformance
  * Data Fabric tier migration
* Require durable idempotency store, audit service, transaction/outbox where production mutation side effects exist.
* Add explicit non-idempotent documentation only where truly unavoidable.

**Files/areas:**

* `EntityCrudHandler.java`
* `EventHandler`
* `PipelineCheckpointHandler`
* `WorkflowExecutionHandler`
* `DataLifecycleHandler`
* `DataSourceRegistryHandler`
* `AiAssistHandler`
* `PluginInstallHandler`
* `TierMigrationHandler`
* shared idempotency helper/store

**Verify:** route-specific retry tests plus contract tests for idempotency headers.

**Acceptance:** retrying a mutating request cannot duplicate durable records, jobs, audit events, or side effects.

---

### Group 4 — UI/UX simplification and i18n/design-system pass

**Goal:** make the UI powerful but simple, consistent, and translatable.

**Change:**

* Use generated route registry for navigation labels, descriptions, icons, roles, lifecycle, and discoverability.
* Replace all raw strings in routes, layout, Data Fabric, errors, banners, buttons, empty states, and disabled surfaces with i18n keys.
* Replace hard-coded colors/classes with design-system tokens/components where practical.
* Add progressive disclosure for Events, Agents, Memory, Fabric, Plugins, Runtime Truth, and Operations.
* Make AI suggestions outcome-oriented, not model/tool-oriented.
* Ensure every page has consistent loading, empty, error, unauthorized, disabled, and degraded states.

**Files/areas:**

* `routes.tsx`
* `DefaultLayout.tsx`
* `RouteSurfaceRegistry.ts`
* `DataFabricPage.tsx`
* common page shell/card/table/action components
* i18n resource files

**Verify:**

```bash
cd products/data-cloud/delivery/ui
pnpm run lint
pnpm run type-check
pnpm run test:unit
pnpm run test:e2e:a11y
```

**Acceptance:** no raw user-visible strings, no broken icon fallback for discoverable routes, and route/card/action UI is backend-contract-driven.

---

### Group 5 — Action Plane/AEP consolidation pass

**Goal:** make AEP the Data Cloud Action Plane instead of a parallel product surface.

**Change:**

* Make standalone `AepHttpServer` dev/embedded-only or route it behind Data Cloud `/api/v1/action/*`.
* Generate compatibility aliases with deprecation metadata if legacy AEP paths must remain.
* Move pattern, learning, HITL, runs, agents, marketplace, governance, and AI suggestion routes into one Action Plane contract.
* Expose every durable/ephemeral/degraded dependency through runtime truth.
* Profile-gate all in-memory fallbacks.

**Files/areas:**

* `products/data-cloud/planes/action/server/.../AepHttpServer.java`
* Action Plane controllers
* Data Cloud router/action handlers
* Action Plane OpenAPI
* UI generated AEP client
* Action Plane E2E tests

**Verify:** Action Plane API contract tests and critical UI journey from Events → Pattern → Agent → Review → Run → Observe.

**Acceptance:** users see one Action Plane product experience, not separate Data Cloud and AEP personalities.

---

### Group 6 — Audio-video as Data Cloud modality pass

**Goal:** make audio-video first-class in Data Cloud.

**Change:**

* Fix audio-video capability-map paths to match active modules.
* Define Data Cloud media asset/domain contract:

  * media source
  * media asset
  * transcript
  * frame/object detection
  * multimodal process
  * consent/retention
  * lineage
  * derived events
  * search/index output
* Add Data Cloud ingestion/job APIs for STT/TTS/vision/multimodal.
* Add runtime truth surfaces and UI entry points for media workflows.
* Emit Data Cloud events from media jobs for AEP pattern detection and agents.

**Files/areas:**

* `products/audio-video/docs/capability-map.md`
* `products/audio-video/modules/*`
* Data Cloud contracts/OpenAPI
* Data Cloud event/entity models
* Data Cloud UI route registry/pages
* integration tests

**Verify:** audio/video ingest → transcription/analysis → Data Cloud catalog → lineage → AEP event → agent action test.

**Acceptance:** audio-video is no longer an isolated shared service; it becomes a governed Data Cloud data modality.

---

### Group 7 — Data Fabric hardening pass

**Goal:** decide whether Data Fabric is preview-only or production operational UI.

**Change:**

* If preview: keep hidden by runtime truth and remove production-looking migration actions.
* If production: backend owns placement recommendations, migration reason, approval, policy simulation, idempotency, audit, status, rollback, and observability.
* Send `migrationReason` to backend and persist it in audit/job history.
* Remove frontend heuristic placement fallback from production mode.

**Files/areas:**

* `DataFabricPage.tsx`
* `cost.service.ts`
* `TierMigrationHandler`
* governance policy simulation
* operations job center
* audit/event stores

**Verify:** governed migration E2E with reason, approval, audit, job status, rollback, and runtime truth state.

**Acceptance:** no operational action can happen without backend policy/audit/idempotency.

---

### Group 8 — Shared library and plugin boundary pass

**Goal:** reduce duplication and make extensibility real.

**Change:**

* Run dependency audit for `@data-cloud/ui`; remove unused workspace libraries.
* Consolidate route registry, icon registry, feature gates, table/list patterns, and runtime surface types.
* Define canonical plugin interfaces for connectors, transforms, quality rules, pattern detectors, agents, media processors, policies, storage, and observability exporters.
* Add versioning, conformance, lifecycle, sandbox/isolation, error reporting, and policy checks.

**Files/areas:**

* `products/data-cloud/extensions/plugins`
* `products/data-cloud/planes/shared-spi`
* shared UI packages
* route and plugin registries
* conformance tests

**Verify:** plugin conformance test suite and one real connector/plugin/media processor end-to-end.

**Acceptance:** new plugins can be registered, validated, enabled, observed, upgraded, disabled, and tested through one lifecycle.

---

### Group 9 — Observability, reliability, and operations pass

**Goal:** make failures debuggable and recoverable.

**Change:**

* Normalize correlation/request/trace IDs across UI, Data Cloud, AEP, audio-video.
* Ensure every async job/run has status, logs, metrics, audit events, retry, cancel, rollback where applicable.
* Expose runtime truth for every dependency: durable, ephemeral, disabled, degraded, misconfigured.
* Add structured audit for AI/agent/plugin/governance/media actions.
* Gate in-memory fallbacks from production unless explicitly overridden with runtime truth warnings.

**Verify:** failure-injection tests and operations UI journey.

**Acceptance:** an operator can answer: what failed, for whom, why, what data was affected, what action is safe next.

---

### Group 10 — Minimal verification pass

**Goal:** keep verification cost low while covering the risk.

Run after Groups 1–9:

```bash
node scripts/list-data-cloud-active-modules.mjs --validate

cd products/data-cloud/delivery/ui
pnpm run lint
pnpm run type-check
pnpm run test:readiness
pnpm run test:e2e:a11y

# Then run release-blocking Gradle modules selected by the generated module classifier.
./gradlew $(node scripts/list-data-cloud-active-modules.mjs --scope=release-blocking --task=test --format=shell)
```

Add separate real-backend E2E only after the backend is running with the intended profile:

```bash
cd products/data-cloud/delivery/ui
DATACLOUD_BASE_URL=http://localhost:8080 pnpm run test:e2e
```

---

## 8. Priority roadmap

### P0 — Production blockers

1. Route/runtime truth/API contract consolidation.
2. UI/backend tenant and correlation mismatch.
3. Stale real-backend E2E contract fixes.
4. Product-wide idempotency/audit for mutating routes.
5. AEP standalone vs Data Cloud Action Plane boundary decision.

Expected readiness lift: **2.8 → 3.5**

### P1 — Coherent product completeness

1. Data source → dataset/entity → schema/contract → quality → lineage journey.
2. Action Plane pattern/agent/learning/HITL journey.
3. Runtime truth page backed by actual dependency posture.
4. Data Fabric either preview-hidden or production-hardened.

Expected readiness lift: **3.5 → 4.0**

### P2 — Extensibility and modality hardening

1. Audio-video as Data Cloud modality.
2. Plugin lifecycle/conformance model.
3. Shared UI table/action/page shell consolidation.
4. Full governance/privacy/consent/retention parity.

Expected readiness lift: **4.0 → 4.3**

### P3 — Polish and optimization

1. Full i18n/a11y cleanup.
2. Performance/load/backpressure test coverage.
3. UI polish and route discoverability.
4. Developer-experience script cleanup.

Expected readiness lift: **4.3 → 4.6+**

---

## 9. Final recommendation

Data Cloud is **not production-ready yet**, but it is no longer just a loose collection of libraries. The current architecture is directionally strong: Data Cloud owns the backbone, Action Plane/AEP is being pulled into it, runtime truth exists, and the product has real UI/backend/test structure.

The next implementation pass should focus on **Group 1 + Group 2 together**: route/runtime truth/API contract consolidation and auth/tenant/correlation alignment. Those two groups will remove the largest source of false positives, broken E2E tests, and confusing UI/backend behavior.

Do **not** spend the next pass generating release evidence. Also do not polish preview surfaces before the route/auth/runtime truth foundations are consistent.

The minimum path to a coherent production-grade Data Cloud suite is:

**canonical contracts → production-safe auth/context → idempotent/audited mutations → Action Plane consolidation → media modality integration → UI/i18n/a11y polish.**
