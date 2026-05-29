Executed a source-grounded audit for `samujjwal/ghatana` at commit `b847cf40509f134715162192f9ea64d94bad82b4`.

I did **not** run Gradle, pnpm, Playwright, or local services. This is a deterministic code/document audit from the repository snapshot. The target commit itself is a merge commit whose visible file change is only the YAPPC changelog, so the meaningful audit target is the **full tree at that commit**, not the diff. 

## 1. Executive Summary

**Overall production-readiness score: 2.6 / 5**

Data Cloud is moving in the right direction architecturally. The repo has a clear canonical vision: Data Cloud is one governed operational data fabric, organized by planes, with the former AEP integrated as the Data Cloud **Action Plane** for automation, pipelines, agents, reviews, patterns, runs, and learning.  The plane architecture is well stated, including Experience, Contract, Runtime Truth, Data, Event, Context, Intelligence, Governance, Action, and Operations planes. 

The product is **not production-ready yet** because the architecture and module map are ahead of end-to-end product completeness. The repo has many strong pieces: runtime truth, production dependency validation, feature gates, route registry, Action Plane modules, plugin surfaces, Data Cloud UI shell, audio-video modules, and test/gate scripts. But there are still gaps in real E2E workflows, backend-owned product contracts, runtime-discovered capability truth, complete AEP/agent/action capability exposure, audio-video governance, permission enforcement verification, UI i18n consistency, and true integration/E2E testing.

### Top 10 blockers

1. **Runtime surface truth is partly hardcoded and incomplete.** `SurfaceSchemaGenerator` claims to generate a unified schema by discovering SPI implementations, but the inspected code manually constructs capabilities; AEP capability discovery currently exposes only durable EventLog.  
2. **Data Fabric is explicitly preview / not production-ready.** The UI feature index says the capability is disabled by default, live fabric metrics are not exposed by the current launcher API, and the page is preview-only. 
3. **The Data Fabric page mixes production language with preview behavior.** The component comment says it is the “first production consumer” of `@ghatana/canvas/flow`, but the same page renders an explicit preview warning saying it is not recommended for production operational decisions.  
4. **Action Plane capability exposure is narrower than the target product model.** The docs require agents, pipelines, patterns, reviews, runs, and learning as native Data Cloud surfaces, but the schema-level AEP capability list only includes durable EventLog.  
5. **Critical UI journey tests are mostly render/mocked tests, not real E2E verification.** The critical-path test mocks API clients and verifies pages render or text appears; it does not prove controller → service → storage → UI behavior.  
6. **Security is architecturally present but must be proven route-by-route.** OpenAPI says all non-health endpoints require JWT/API key auth, production tenant identity comes from authenticated claims, and critical routes require policy enforcement. The server supports production validation and security filters, but this needs full route-level verification for every handler/action.  
7. **Audio-video tool catalog entries expose high-risk capabilities with empty required roles.** STT, vision, and multimodal tools carry PII/biometric-risk tags, but their local access policy lists no required roles and no blocked action classes.  
8. **UI i18n is inconsistent.** The layout uses `useTranslation`, but route labels/descriptions in `RouteSurfaceRegistry` are raw English strings and the sidebar renders `item.label` directly.  
9. **Many optional capabilities fall back to disabled, degraded, in-memory, or 501/503 behavior.** This is correctly surfaced in places, but it means the product is not yet feature-complete by default.  
10. **Compatibility routes and preview routes preserve continuity but add product-surface complexity.** The UI keeps multiple legacy/deep-link routes indefinitely, which is useful but increases drift risk unless route truth and docs are continuously enforced. 

### Top strengths

1. Data Cloud has a coherent canonical product vision and plane model. 
2. AEP has been conceptually integrated as the Action Plane rather than a separate product. 
3. The workspace and Gradle includes show Data Cloud as a first-class platform-provider with many plane modules. 
4. Runtime Truth has a typed backend `SurfaceRecord` with state, dependencies, probes, evidence, actions, and runtime posture. 
5. Production startup validation is strict for auth, audit, policy, durable stores, idempotency, metrics, trace export, and tenant resolution. 
6. The UI has a route registry, role-aware discoverability, runtime capability gates, lazy loading, error boundaries, and disabled-surface pages.  
7. The layout is close to a real product shell: navigation, search, keyboard shortcuts, notifications, AI assistant, operations context, WebSocket state, and active operations bar.  
8. Audio-video has Java/Rust proto compatibility tests and multimodal contract interop tests.  
9. The repo contains broad quality gates and maturity scripts for architecture, i18n, a11y, runtime dependency failures, agent lifecycle contracts, circular dependencies, and production readiness.  
10. OpenAPI contract quality is strong in intent: auth, tenant model, security requirements, major surfaces, and stable endpoint prefixing are documented. 

## 2. Scope Inspected

I inspected these representative areas:

| Area                                    | Evidence                                                                                                                            |
| --------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| Root monorepo scripts and package gates | `package.json` scripts for architecture, production readiness, i18n, a11y, route truth, Data Cloud maturity, kernel/plugin checks.  |
| pnpm workspace shape                    | Data Cloud UI/libs/action UI/gateway and Audio-Video apps/libs/module packages.                                                     |
| Gradle module graph                     | Platform Java modules, platform-kernel, platform plugins, generated product includes.                                               |
| Data Cloud generated Gradle includes    | Data, Event, Intelligence, Governance, Action, Delivery, Contracts, Extensions, Integration Tests.                                  |
| Canonical Data Cloud docs               | README, plane architecture, product HLD.                                                                                            |
| Data Cloud UI                           | routes, layout, route surface registry, Data Fabric page, critical journey test.                                                    |
| Runtime Truth backend                   | `SurfaceRegistryHandler`, `SurfaceRecord`, `DataCloudHttpServer` surface snapshot and production validation.                        |
| Action Plane / AEP                      | registry README, central runtime README, agent registry contract, capability schema.                                                |
| Audio-video                             | Gradle/workspace presence, Action Plane tool catalog registration, proto compatibility tests, multimodal contract interop test.     |
| API contracts                           | `products/data-cloud/contracts/openapi/data-cloud.yaml`.                                                                            |

## 3. Current Product Map

### Data Cloud

Current Data Cloud is organized as a plane-based product. The generated Gradle includes show `shared-spi`, Data Plane, Event Plane, Operations, Intelligence, Governance, Delivery, Contracts, Extensions, Integration Tests, and a large Action Plane hierarchy. 

**Assessment:** Architecture is coherent. Implementation breadth is high. Production readiness is uneven because many features are present as handlers, optional plugins, preview surfaces, or runtime-gated capabilities rather than fully complete journeys.

### AEP / Action Plane

AEP is documented as Data Cloud’s Action Plane and no longer a separate product boundary. The Action Plane target includes event-driven agent orchestration, pattern detection, pipeline execution, HITL review, learning loops, and runtime observability. 

The registry module says it is the authoritative source of truth for known agents, pipelines, and pattern repositories.  The central runtime says it is the single lookup point for agent or pipeline resolution. 

**Assessment:** Direction is right, but there are multiple `AepCentralRegistryService` locations in search results, which should be consolidated or proven as intentional adapters. 

### Agents

Agent registry contracts are correctly abstracted through `AgentRegistryContracts`, decoupling external products such as YAPPC from the concrete central registry implementation. 

**Assessment:** Good SPI direction. Missing production proof: lifecycle governance, approval gates, policy-backed tool access, execution isolation, and audit traces across real agent actions.

### Audio-video

Audio-video is a shared-service product family in the generated Gradle includes with persistence, security, cache, messaging, integration tests, multimodal service, STT, TTS, vision, and common libraries.  It is also registered into the Action Plane agent tool catalog with STT, TTS, vision analysis, and multimodal inference tools. 

**Assessment:** Strong contract/test beginnings, but not yet proven as a governed Data Cloud modality with ingestion, indexing, retention, consent, lineage, search, observability, and UI journeys.

### Shared libraries

The platform includes many reusable Java modules: observability, security, testing, workflow, AI integration, governance, agent-core, runtime, audit, policy-as-code, messaging, data-governance, identity, and more.  The Data Cloud plane architecture explicitly warns to keep only genuinely cross-product abstractions in platform and move/split Data Cloud-specific semantics back into Data Cloud. 

**Assessment:** Strong reuse potential, but boundary audit remains necessary to prevent shared libraries from becoming product-semantic dumping grounds.

## 4. Readiness Scorecard

| Dimension                            | Score | Rationale                                                                                                |
| ------------------------------------ | ----: | -------------------------------------------------------------------------------------------------------- |
| Product coherence                    |   3.5 | Strong canonical vision and plane model; still uneven implementation coherence.                          |
| Feature completeness                 |   2.3 | Many modules/surfaces exist, but several are optional, preview, degraded, or disabled.                   |
| E2E workflow completeness            |   2.1 | UI flows exist, but current critical-path tests are mostly mocked render tests.                          |
| Data Cloud core architecture         |   3.2 | Plane model, contracts, runtime truth, production validation are strong.                                 |
| AEP architecture/integration         |   2.8 | Action Plane integration is clear, but capability exposure is incomplete.                                |
| Agent architecture/integration       |   2.5 | Registry contracts exist; runtime governance and tool safety need proof.                                 |
| Audio-video architecture/integration |   2.2 | Modules/tests exist; Data Cloud modality integration is incomplete.                                      |
| Shared library quality               |   3.0 | Good module taxonomy; still needs product-semantic leakage audit.                                        |
| UI/UX simplicity/consistency         |   3.0 | Good shell/navigation; preview/compat routes and raw labels add cognitive/drift risk.                    |
| Backend/API correctness              |   3.0 | Good contracts and production validation; route-by-route enforcement still needs proof.                  |
| Plugin/extensibility model           |   2.7 | Plugin lifecycle and tool catalog exist; versioning/isolation/policy not fully proven.                   |
| Security/authorization               |   2.8 | Strong production validation and OpenAPI intent; full permission matrix not verified.                    |
| Privacy/governance                   |   2.6 | Governance concepts exist; AV/agent privacy/consent not complete.                                        |
| Observability/operations             |   3.0 | Runtime posture, metrics/tracing validation, operations pages exist; async/action traces need E2E proof. |
| Reliability/failure handling         |   2.8 | Fail-closed startup validation exists; many optional capabilities degrade/disable.                       |
| Performance/scalability              |   2.4 | Some performance tests/gates exist; large data/AV/agent throughput not proven.                           |
| i18n readiness                       |   2.3 | Translation is used, but route labels/descriptions and many UI strings are raw.                          |
| Accessibility readiness              |   2.5 | Some aria/focus patterns exist; full a11y journey proof absent.                                          |
| Test quality/coverage                |   2.6 | Many tests/gates exist; critical journeys rely heavily on mocks/render checks.                           |
| Developer experience                 |   3.4 | Strong scripts, generated includes, docs, and module structure.                                          |
| Configuration/feature flags          |   3.0 | Runtime truth and flags exist; schema/gates are not fully runtime-discovered.                            |
| Docs/code alignment                  |   2.5 | Docs are strong but ahead of implementation; Data Fabric docs/code conflict.                             |
| Production deployment readiness      |   2.7 | Startup validation is strong; product journeys still incomplete.                                         |
| Maintainability/SRP/DRY              |   2.8 | Canonical boundaries exist; multiple registries/services and hardcoded sets remain.                      |
| Overall production readiness         |   2.6 | Promising platform/product foundation, not yet GA-ready.                                                 |

## 5. Feature Completeness Matrix

| Capability      | Current state                                                                | Gap                                                                                | Severity | Action                                                                          |
| --------------- | ---------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- | -------: | ------------------------------------------------------------------------------- |
| Runtime Truth   | Typed records, dependencies, probes, posture, `/api/v1/surfaces`.            | Surface schema still partially hardcoded; Action Plane capabilities under-modeled. |       P0 | Generate schema from canonical route/action/plugin registries and prove parity. |
| Data Explorer   | Canonical `/data` route and compatibility routes exist.                      | Need full create/import/schema/quality/lineage/governance E2E proof.               |       P0 | Add backend-backed E2E through entity/schema/lineage/policy.                    |
| Events          | `/events` route gated by runtime capability.                                 | Replay/tail/correlation/pattern links not fully proven.                            |       P1 | Verify event append/tail/replay/correlation journey.                            |
| Query/Analytics | `/query`, analytics APIs, cancellation unavailable in single process.        | Need confidence/freshness/governance semantics and distributed cancellation story. |       P1 | Backend-owned query result contract with lineage/freshness/policy.              |
| Pipelines       | Routes and workflow execution handler exist.                                 | Draft→validate→publish→run→observe lifecycle not fully proven.                     |       P0 | Implement/verify lifecycle contract and run ledger UI.                          |
| Patterns        | Registry says pattern repositories exist.                                    | Pattern UI/API/lifecycle/learning not clearly exposed in runtime schema.           |       P1 | Add Pattern surface contract and E2E tests.                                     |
| HITL Reviews    | Target journey documented.                                                   | Real review queue and approve/reject/escalate evidence loop not proven.            |       P0 | Implement review ledger/evidence/audit/learning E2E.                            |
| Agents          | Central registry and contracts exist.                                        | Agent execution, policy, approval, memory, tool access not fully proven.           |       P0 | Governed agent runtime pass.                                                    |
| Audio-video     | Shared-service modules and Action Plane tool catalog exist.                  | No complete Data Cloud ingestion/index/search/retention/consent workflow.          |       P1 | Treat AV as first-class Data Cloud modality.                                    |
| Data Fabric     | UI and API client exist, but preview-only.                                   | No production connector implementation; metrics preview.                           |    P0/P1 | Either keep hidden or finish backend connector/runtime contract.                |
| Plugins         | `/plugins` route and plugin install handler exist.                           | Isolation, upgrade safety, rollback, policy enforcement need proof.                |       P1 | Plugin lifecycle hardening.                                                     |
| Security        | OpenAPI and startup validation are strong.                                   | Need permission matrix tests per route/action/tool.                                |       P0 | Add route/action authorization matrix.                                          |
| Observability   | Runtime posture, metrics/tracing config, request observation filter exist.   | Need trace/run IDs across action, agent, pipeline, AV workflows.                   |       P1 | Observability/audit event pass.                                                 |
| Tests           | Broad gates and render tests exist.                                          | Need fewer mocked journeys and more real API/service/storage tests.                |       P0 | Add deterministic integration/E2E suite.                                        |

## 6. End-to-End Journey Analysis

| Journey                        | Current implemented path                                      | Missing / risk                                                                                                            | Severity |
| ------------------------------ | ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- | -------: |
| Create/connect data source     | Connectors UI route and Data Source Registry handler exist.   | Handler is created with `null /* no DataFabricConnector implementation yet */`; production connector workflow incomplete. |       P0 |
| Ingest dataset/stream          | Entity/event handlers and SSE routes are wired.               | Need real import mapping, validation, lineage, and idempotency E2E.                                                       |       P0 |
| Define schema/contract         | Schema validator attach point exists.                         | Requests without registered schema pass through; need governed schema lifecycle.                                          |       P1 |
| Run quality checks             | Data quality/trust route exists.                              | Need backend-owned quality rule execution and failure remediation flow.                                                   |       P1 |
| Build/run pipeline             | Pipeline/workflow routes and handler wired.                   | Draft/version/publish/rollback/run history not fully proven.                                                              |       P0 |
| Detect event patterns          | Registry claims pattern repositories.                         | Pattern runtime not clearly surfaced through `/api/v1/surfaces` schema.                                                   |       P1 |
| Adaptive feedback              | Learning APIs and mastery controller exist.                   | Need HITL → learning → policy/mastery update loop proof.                                                                  |       P0 |
| Trigger agent workflow         | Agent catalog route and registry contract exist.              | Tool safety, memory, approvals, and execution isolation not proven.                                                       |       P0 |
| Process audio/video            | AV tools registered remotely.                                 | Consent, retention, media lineage, streaming, and UI workflow missing.                                                    |       P1 |
| Search/catalog/discover        | Semantic search handler and data products route wired.        | Catalog UX and permission-aware discovery need E2E proof.                                                                 |       P1 |
| Govern/access/share            | Governance docs/API/security exist.                           | Route/action/tool permission matrix incomplete.                                                                           |       P0 |
| Observe/debug failed job       | Operations routes and runtime truth exist.                    | Need run/job trace correlation and failure recovery workflow.                                                             |       P1 |
| Administer plugins/config      | Plugin/settings routes exist.                                 | Plugin isolation/version/rollback and production settings storage proof needed.                                           |       P1 |
| Normal-user low cognitive load | Outcome-first nav exists.                                     | Hidden previews, compatibility routes, and technical terms still leak.                                                    |       P1 |

## 7. UI/UX Findings

The UI shell is a strong foundation: route registry, sidebar, global search, keyboard shortcuts, notifications, AI assistant, operations context, active operations bar, and route-level error handling are present.  

Main UI gaps:

1. **Raw English strings remain in route metadata and pages.** `RouteSurfaceRegistry` stores labels/descriptions as raw strings, and the sidebar renders `item.label` directly. This violates the “one i18n way” rule and will create translation drift.  
2. **Preview surfaces are routable but not discoverable.** This is acceptable for progressive disclosure, but tests must prove unavailable surfaces never appear as broken promises. 
3. **Data Fabric is visually powerful but too technical for a default product journey.** HOT/WARM/COOL/COLD topology, manual migration, AI advisory, and canvas are useful for operators, but should remain role-gated and framed around outcomes. 
4. **Compatibility routes preserve users but expand cognitive and verification load.** Keep them, but verify redirects and doc truth continuously. 
5. **Tests validate rendering more than usability.** The critical path tests verify pages render and contain keywords; they do not verify full user decision flows, drilldowns, error recovery, or backend truth.  

## 8. Architecture and Boundary Findings

The target architecture has the right rules: Data/Event/Context/Governance planes must not depend on Action Plane internals; Action Plane may consume public contracts/SPI; UI must use generated clients/adapters; contracts must not depend on implementation modules. 

Key architecture gaps:

1. **Surface schema is not yet the real single source of truth.** The backend runtime records are good, but the schema generator is manually enumerating capabilities and does not yet reflect all Action Plane/runtime/plugin surfaces.  
2. **AEP naming remains in packages and modules.** Docs intentionally allow Java package names like `com.ghatana.aep.*` in the first migration pass, but this should be tracked to avoid permanent dual mental models. 
3. **Data Cloud HTTP server is too central.** It wires many handlers, runtime validation, feature surfaces, plugin manager, AI, voice, governance, agent catalog, Data Fabric, settings, compliance, and conformance. This is workable now but should be split by plane composition modules to preserve SRP.  
4. **Shared platform boundaries need enforcement.** The architecture doc explicitly says platform modules should remain shared only when used by three or more unrelated products; Data Cloud or Action Plane semantics should move back into Data Cloud. 

## 9. Security, Privacy, Governance Findings

Security posture is better than many early products. The OpenAPI contract documents JWT/API-key auth, tenant identity from authenticated principal, compatibility-only tenant headers, critical route policy enforcement, sensitive route audit events, and break-glass requirements. 

Production startup validation is strong: production requires auth, audit, policy engine, durable idempotency, durable event/entity stores, metrics, trace export, and tenant resolver under strict tenant resolution. 

Main risks:

1. **Authorization needs route/action/tool proof, not just startup validation.**
2. **Audio-video tools have PII/biometric-risk policy tags but empty local `requiredRoles`.** This must be backed by central policy enforcement, consent, audit, and purpose limitation before any production use. 
3. **Preview/admin surfaces must never be unlocked by frontend-only state.** UI shell role and product view mode are useful for disclosure, but backend enforcement must remain authoritative. 
4. **Local/test fallbacks must stay impossible in production.** The code is moving in this direction, but all fallback paths must be covered by tests. 

## 10. Observability, Reliability, Performance Findings

Strengths:

* Runtime Truth records include dependencies, probes, actions, evidence, limitations, and posture. 
* The server validates metrics and trace export for production. 
* Request observation filter is applied around the root servlet. 
* Runtime posture captures auth, durability, audit, policy, metrics, tracing, event store, idempotency, context store, and transaction mode. 

Gaps:

* Pipeline, agent, review, pattern, and AV workflows need one shared run/trace/audit correlation model.
* Query cancellation is explicitly unavailable in single-process mode. 
* Trace export can be absent outside production; spans may be generated but discarded. 
* AV service performance, large-media handling, streaming backpressure, retries, and privacy-safe observability are not yet proven.

## 11. Test and Verification Gap Analysis

Current tests/gates are broad, but the critical user journey tests are not enough for production readiness because they mock services and mostly assert rendering. 

Minimum additions:

| Test layer    | Needed                                                                                                                               |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| Unit          | Surface schema generator, runtime truth mapping, policy decision mapping, plugin lifecycle state, AV policy tag validation.          |
| Integration   | Entity/schema/event/lineage/governance; pipeline lifecycle; review lifecycle; agent tool execution; Data Fabric connector lifecycle. |
| API contract  | OpenAPI vs router parity for `/api/v1/surfaces`, `/api/v1/action/*`, connectors, agents, plugins, AV tools.                          |
| UI/component  | No raw i18n strings, route registry labels by translation keys, runtime disabled/degraded/preview states.                            |
| E2E           | Browser-level journeys with real backend profile and deterministic fixtures.                                                         |
| Security      | Role/tier/tenant matrix for every route/action/tool.                                                                                 |
| Observability | Every mutating/action workflow emits audit, trace, metrics, and failure event.                                                       |
| Performance   | Event throughput, pipeline run latency, agent tool timeout/retry, AV large-file/streaming boundaries.                                |

## 12. Consolidated Task Plan Grouped to Minimize Verification Passes

### Group 1 — Runtime Truth and Capability Registry Consolidation

**Goal:** Make `/api/v1/surfaces` and `/api/v1/surfaces/schema` the actual backend-owned truth for all Data Cloud, Action Plane, plugin, agent, and AV surfaces.

**Change:**

* `products/data-cloud/delivery/launcher/.../SurfaceSchemaGenerator.java`
* `products/data-cloud/delivery/launcher/.../SurfaceRegistryHandler.java`
* `products/data-cloud/delivery/launcher/.../DataCloudHttpServer.java`
* `products/data-cloud/delivery/ui/src/api/surfaces.service.ts`
* `products/data-cloud/delivery/ui/src/lib/routing/RouteSurfaceRegistry.ts`

**Exact work:**

* Replace manual schema enumeration with generated/registered capability providers.
* Add Action Plane capabilities for pipelines, patterns, runs, reviews, agents, learning, deployments, and reports.
* Add AV tool surfaces with policy tags, consent requirements, endpoint health, timeout/retry metadata.
* Add parity test: route registry ↔ OpenAPI ↔ backend runtime truth ↔ UI gates.

**Tests/verification:**

* `./gradlew :products:data-cloud:delivery:launcher:test`
* `./gradlew :products:data-cloud:delivery:api-contract-tests:test`
* `pnpm check:truth-surfaces`
* `pnpm --filter @ghatana/data-cloud-ui test`

**Acceptance:** No UI surface is shown unless backend runtime truth says live/degraded/preview with actionable details.

---

### Group 2 — Backend Capability Contracts and Permission Enforcement

**Goal:** Prove every Data Cloud route/action/tool is tenant-safe, permission-aware, and policy-backed.

**Change:**

* `DataCloudSecurityFilter`
* `HttpHandlerSupport`
* all Data Cloud handlers wired through `DataCloudRouterBuilder`
* Action Plane agent/tool handlers
* AV catalog capability YAML
* OpenAPI security sections

**Exact work:**

* Create route/action permission matrix.
* Require policy checks for critical mutations, agent tool calls, AV PII/biometric tools, plugin install/upgrade, exports, deletion, model promotion, and tier migration.
* Replace empty AV `requiredRoles` with explicit roles or policy expressions.
* Ensure audit reason/break-glass is enforced server-side.

**Tests/verification:**

* API tests for authenticated/unauthenticated/wrong-tenant/wrong-role cases.
* AV tool access denial tests.
* Agent tool policy tests.
* Break-glass audit tests.

**Acceptance:** No route relies on frontend role state for enforcement.

---

### Group 3 — Data Cloud Core E2E Completion

**Goal:** Finish the core data journey: connect → ingest → validate schema → store → emit event → inspect lineage/context → query/search → govern/export.

**Change:**

* Entity/schema/event handlers
* Data source registry
* lineage/context handlers
* semantic search
* export/reporting
* Data Explorer UI

**Exact work:**

* Implement real connector contract or keep connectors hidden until ready.
* Add import/ingest job status.
* Ensure entity writes produce event, audit, lineage/context update, idempotency record, and observability trace.
* Add server-backed empty/error/degraded states.

**Tests/verification:**

* Integration test with durable local fixture store.
* UI E2E against backend.
* Contract test for import/ingest status and lineage.

**Acceptance:** A user can ingest data and see consistent data/event/lineage/trust/query results.

---

### Group 4 — Action Plane Lifecycle Completion

**Goal:** Make pipelines, patterns, agents, runs, reviews, and learning work as one governed Action Plane.

**Change:**

* `products/data-cloud/planes/action/*`
* central runtime/registry/orchestrator/agent-runtime/server/API
* UI pages: Pipelines, Agents, Reviews, Patterns, Operations

**Exact work:**

* Consolidate duplicate registry service entry points or document exact adapter ownership.
* Implement canonical lifecycle: draft → validate → publish → run → observe → retry/cancel/rollback.
* Add HITL queue: pending → approve/reject/escalate → learned.
* Add pattern lifecycle: define → deploy → match → explain → feedback → version.
* Emit run ledger/audit/trace for each action.

**Tests/verification:**

* `:products:data-cloud:planes:action:*:test`
* API tests for `/api/v1/action/*`
* UI E2E for pipeline + review + agent flow.

**Acceptance:** Action Plane is product-native, not only module-native.

---

### Group 5 — Audio-Video as First-Class Data Cloud Modality

**Goal:** Move AV from remote agent tools to governed Data Cloud modality support.

**Change:**

* `products/audio-video/*`
* `products/data-cloud/planes/action/agent-catalog/capabilities/audio-video-capabilities.yaml`
* Data Cloud contracts and UI
* AV service Docker/CI/test fixtures

**Exact work:**

* Add media asset model: source, consent, retention, classification, transcript, extracted events, embeddings, lineage.
* Add ingestion workflow and status.
* Add search/index integration.
* Enforce PII/biometric policy before STT/vision/multimodal calls.
* Add AV operation audit and trace IDs.

**Tests/verification:**

* AV contract interop tests.
* Agent tool policy tests.
* Media ingestion integration test.
* Large file/stream timeout tests.

**Acceptance:** Audio/video is safely usable as data, not just remote tooling.

---

### Group 6 — UI/UX, i18n, and Cognitive Load Pass

**Goal:** Make the product simple, powerful, beautiful, consistent, and low-cognitive-load.

**Change:**

* `DefaultLayout.tsx`
* `RouteSurfaceRegistry.ts`
* all Data Cloud pages/components
* i18n resources
* shared table/card/action components

**Exact work:**

* Replace route labels/descriptions with i18n keys.
* Standardize page headers, cards, tables, actions, filters, drilldowns, empty/loading/error states.
* Keep preview surfaces non-discoverable unless role/runtime truth allows.
* Make AI/agent/AEP labels outcome-based: “Review decisions,” “Automate pipeline,” “Explain anomaly,” not raw technical labels.
* Add a11y coverage for keyboard and screen-reader states.

**Tests/verification:**

* `pnpm check:i18n-conformance`
* `pnpm check:a11y-maturity`
* UI component tests.
* Playwright journey tests.

**Acceptance:** A normal user can navigate without understanding planes, AEP, runtime internals, or plugin architecture.

---

### Group 7 — Shared Library Boundary Cleanup

**Goal:** Preserve reusable platform libraries and move Data Cloud semantics back into Data Cloud.

**Change:**

* `platform/java/*`
* `platform-kernel/*`
* `products/data-cloud/planes/*`
* boundary tests/scripts

**Exact work:**

* Audit platform modules against the “three unrelated products” rule.
* Move Data Cloud-specific policy, event, agent, governance, and runtime semantics out of generic platform modules.
* Add architecture tests for forbidden imports.

**Tests/verification:**

* `pnpm check:architecture-boundaries`
* Gradle module tests.
* Circular dependency check.

**Acceptance:** Shared libraries are truly shared and Data Cloud remains product-owned.

---

### Group 8 — Deterministic Real E2E Test Pass

**Goal:** Replace test theater with production-relevant tests.

**Change:**

* Data Cloud integration tests
* UI E2E tests
* API contract tests
* deterministic fixtures

**Exact work:**

* Keep mocked render tests, but do not treat them as production proof.
* Add backend-backed tests for core journeys.
* Add one fixture profile: tenant, roles, data source, entities, events, pipeline, review item, agent, AV sample metadata.
* Add verification matrix mapping each readiness dimension to tests.

**Tests/verification:**

* `./gradlew :products:data-cloud:integration-tests:test`
* `./gradlew :products:data-cloud:delivery:api-contract-tests:test`
* `pnpm --filter @ghatana/data-cloud-ui test`
* Playwright E2E suite.

**Acceptance:** A failed route/action/data contract is caught before release.

## 13. Priority Roadmap

### P0 — Production blockers

* Runtime Truth/capability registry consolidation.
* Security/permission/policy matrix.
* Core Data Cloud E2E journey.
* Action Plane lifecycle proof.
* Replace mocked-only critical path with backend-backed integration/E2E.

**Expected score movement:** 2.6 → 3.4.

### P1 — Coherent product completeness

* Data Fabric connector backend or keep fully hidden.
* HITL review/learning loop.
* Pattern lifecycle.
* Agent tool governance.
* AV modality ingestion/consent/index/search.
* i18n route/page cleanup.

**Expected score movement:** 3.4 → 4.0.

### P2 — Hardening and extensibility

* Plugin install/upgrade/rollback isolation.
* Performance tests for events, pipelines, agents, AV.
* Observability run ledger and trace correlation.
* Shared library boundary cleanup.

**Expected score movement:** 4.0 → 4.4.

### P3 — Polish and optimization

* Visual consistency.
* Advanced progressive disclosure.
* Better empty/degraded recovery suggestions.
* Operator dashboards.
* Docs/code drift cleanup.

**Expected score movement:** 4.4 → 4.7.

## 14. Final Recommendation

Data Cloud is **not production-ready now**, but it has a credible foundation. The next implementation pass should focus on **runtime truth + permission enforcement + real E2E core journeys**, not more release evidence generation.

Do **not** spend the next pass on more documentation, scorecard scripts, or release evidence. The repo already has many gates and evidence-oriented scripts. The highest-return work is to make the product’s actual behavior match the canonical architecture:

1. One backend-owned Runtime Truth contract.
2. One route/action permission model.
3. One complete Data Cloud journey.
4. One complete Action Plane journey.
5. One governed AV/agent tool journey.
6. One real E2E verification profile.

The highest ROI task group is **Group 1 + Group 2 together**: Runtime Truth consolidation and permission enforcement. Without that, every UI, plugin, agent, AEP, and audio-video feature can appear “available” before it is actually safe, governed, observable, and production-ready.
