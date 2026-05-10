## Scope and evidence note

I executed this as a **current-snapshot audit** of `samujjwal/ghatana` at commit `0f4b6eb1c28f13fd9ae3341b15fb6659a436d595`, focused on `products/data-cloud` and shared/platform libraries used by Data Cloud. The commit itself is a YAPPC changelog-only commit, so the audit treats the SHA as the full repository state at that point, not as a diff-only review. 

This was a source-level GitHub audit. I did **not** run the build, tests, UI, containers, or deployment stack.

---

# 7.1 Production Readiness Verdict

**Production ready: No**

**Confidence level:** Medium-high for architecture/code-level findings; medium for runtime behavior because tests/build were not executed.

**Direction of travel:** The implementation is clearly improving toward the intended Data Cloud architecture. The canonical docs now define Data Cloud as an AI-native operational data fabric, organized by planes, with AEP explicitly positioned as the Action Plane runtime rather than a separate customer-facing product.  The canonical plane architecture also defines product planes, runtime truth, target layout, dependency rules, and shared-platform migration rules. 

**Main blockers:**

1. Tenant/scope/authorization enforcement is still not proven end to end.
2. Mutating workflows are not consistently atomic or idempotent across planes.
3. Production-critical services can fall back to no-op, in-memory, heuristic, or disabled behavior.
4. Runtime truth, route registry, OpenAPI, generated clients, and UI surfaces are not yet one canonical system.
5. Product/shared-library boundaries are better documented than fully enforced.
6. Connector lifecycle is still metadata/control-plane heavy and not yet a durable ingestion pipeline.
7. Stale audit/docs and compatibility artifacts remain and will keep causing repeated re-audits.

**Highest-risk area:** Backend-enforced tenant/scope/authorization combined with non-atomic entity/event/index/automation writes.

**Shared libraries verdict:** Partially reusable, but not yet cleanly bounded. The repo has many shared platform modules and Data Cloud-specific plane modules included together in the Gradle graph, while the canonical architecture still calls out platform modules that should be kept generic, split, or moved into Data Cloud when they encode Data Cloud/Action Plane semantics.  

---

# 7.2 Root Architectural Blockers

## P0-1 Canonical tenant, scope, and authorization enforcement is not proven end to end

**Why it matters:** Data Cloud’s core value depends on tenant isolation, data ownership, sovereignty, privacy, and governed automation. Any route accepting tenant/scope from spoofable request input is a production blocker.

**Root cause:** Tenant resolution and handler-level authorization are still distributed. `HttpHandlerSupport` can resolve tenant from request metadata, principal, header, query parameter, or safe-mode fallback. Handlers then manually call `requireTenantIdOrFail`. Middleware rate-limits by tenant from request header/query. A security filter and access registry exist by path, but this audit did not verify that every registered route is forced through them.    

**Evidence:**

* `HttpHandlerSupport` resolves tenant from principal, header, query, and local/test/development fallback. 
* `DataCloudMiddleware` extracts tenant from header/query for tenant rate limiting. 
* `DataSourceRegistryHandler` uses `@RequiresRole("ADMIN")` and manually resolves tenant per handler. 
* `EntityCrudHandler` is annotated `@Secured`, but handler logic still manually resolves tenant from the HTTP helper. 
* `DataCloudHttpServer` has optional API key/JWT/policy components and comments that the security filter is not activated when the resolver is absent. 

**Affected surfaces:** All APIs, connectors, entities, events, query, analytics, governance, action, export, AI assist, voice, admin/settings, pipeline execution.

**Correct target pattern:** A single immutable `RequestContext` created before routing from authenticated identity, delegated support context, workspace/org/project scope, roles, tier, policy context, and audit context. Handlers should receive trusted context, not parse tenant/scope directly.

**Required fix:**

* Make production/staging fail closed unless JWT/API key/auth context, tenant, workspace/org where required, roles, and policy engine are configured.
* Remove tenant query parameter support from production.
* Allow tenant header only when cryptographically bound to authenticated identity or gateway metadata.
* Generate route/action/permission matrix from a canonical route registry.
* Add support-access delegation with redaction and audit evidence.

**Required tests:**

* Spoofed `X-Tenant-Id` blocked.
* Spoofed `tenantId` query blocked.
* Tenant mismatch between token and header blocked.
* Every route covered by permission matrix.
* Platform/support users cannot access customer data unless delegated.
* Same route tested for admin/editor/viewer/operator/support/client roles.

**Cleanup implications:** Centralize tenant/scope/auth code; delete per-handler tenant parsing except for context extraction tests.

---

## P0-2 Mutating workflows are not consistently atomic, idempotent, or replay-safe

**Why it matters:** Data Cloud writes often affect entity state, event log, indexes, provenance, audit, automation, and downstream retrieval. Partial writes create untrustworthy data and break provenance.

**Root cause:** Transaction and idempotency are optional or limited. `EntityCrudHandler` only implements idempotency for entity save/batch and explicitly notes that other mutating routes still need idempotency or explicit non-idempotent documentation. The transaction manager is optional, and the non-transactional path saves entity, appends event, broadcasts WebSocket, and indexes semantically as separate steps. 

**Evidence:**

* `EntityCrudHandler` comments identify missing idempotency coverage for pipelines, events, governance, analytics, and other mutating routes. 
* `DataCloudHttpServer` says generic/entity idempotency stores may fall back to in-memory and that absent transaction manager means multi-step writes execute without transaction boundaries. 
* `DataCloudClient.Event` has validation as a method rather than enforcing required envelope fields at construction/append boundaries. 

**Affected surfaces:** Entity CRUD, batch writes, event append, pipeline creation/execution, governance mutations, AI actions, connector syncs, reports, exports, learning review actions.

**Correct target pattern:** Canonical mutation command runtime with idempotency, optimistic concurrency, transaction/outbox, audit, provenance, retries, and replay semantics.

**Required fix:**

* Create a shared Data Cloud mutation runtime.
* Require idempotency keys for retryable mutating routes or explicitly mark routes non-idempotent.
* Use durable idempotency stores in all non-local profiles.
* Wrap entity + event + provenance + audit + index scheduling in transaction/outbox.
* Move semantic indexing/WebSocket notifications to after-commit/outbox consumers.

**Required tests:**

* Retry same command does not duplicate data.
* Event append failure does not leave untracked entity mutation.
* Index failure does not corrupt canonical store.
* Outbox replay is deterministic.
* All mutating routes have idempotency/retry tests.

**Cleanup implications:** Remove route-local idempotency implementations and replace with shared command middleware.

---

## P0-3 Production-critical dependencies can silently degrade to no-op, in-memory, heuristic, or disabled behavior

**Why it matters:** Production trust requires clear fail-closed startup and truthful runtime status. Optional production-critical services make the product appear live while core correctness, audit, AI, metrics, transactions, or security are degraded.

**Root cause:** `DataCloudHttpServer` centralizes many optional dependencies and falls back to local/embedded behavior in several critical areas. 

**Evidence:**

* Metrics collector defaults to no-op unless configured. 
* AI assist uses heuristic fallback when `CompletionService` is absent. 
* Audit service may be null and audit emission skipped. 
* Settings and idempotency can fall back to in-memory. 
* Security filter is not activated when API-key resolver is null; JWT is disabled when provider is null. 
* Connector test/sync returns pending/degraded when fabric connector is absent. 

**Affected surfaces:** Security, observability, AI, connectors, settings, idempotency, transactions, audit, runtime truth.

**Correct target pattern:** Profile-based runtime composition with hard production requirements and explicit degraded states.

**Required fix:**

* Define `local`, `test`, `preview`, `staging`, `production`, and `sovereign` profiles.
* Fail startup in staging/production if security, audit, metrics, durable idempotency, durable settings, transaction/outbox, and tenant resolver are absent.
* Allow preview/degraded only behind explicit runtime truth states.
* Surface every missing dependency through `/api/v1/surfaces`, readiness, and operations UI.

**Required tests:**

* Production startup fails when required components are absent.
* Local profile permits safe fallbacks with warnings.
* Runtime truth reports missing dependencies accurately.
* UI hides/disables degraded surfaces without fake success.

**Cleanup implications:** Remove silent fallbacks from production code paths; move safe fallbacks to local/test modules.

---

## P1-4 Runtime truth, route registry, and route compatibility are still mixed

**Why it matters:** Runtime truth is supposed to tell users what is live, degraded, disabled, preview, or unavailable. If routes, UI gates, OpenAPI, and runtime state drift, the UI can expose dead or unsafe actions.

**Root cause:** The repo has canonical runtime truth direction, but older “capability” vocabulary and route aliases remain. The router registers canonical `/api/v1/action/*` routes alongside legacy root-level action routes such as `/api/v1/pipelines`, `/api/v1/memory`, and `/api/v1/learning`. 

**Evidence:**

* README says `/api/v1/surfaces` is canonical and `/api/v1/capabilities` compatibility endpoint was removed. 
* Plane architecture says to avoid capability-area language and use planes/surfaces/runtime truth. 
* UI/source search still shows `useCapabilityGate.ts`, action `useCapabilities.ts`, and generated `aep-client.ts`.   
* Router has duplicate canonical and non-canonical routes for action surfaces. 

**Affected surfaces:** UI navigation, Action Plane, pipelines, memory, learning, contracts, generated clients, docs, tests.

**Correct target pattern:** One canonical route/surface/action registry generated from OpenAPI and runtime truth metadata.

**Required fix:**

* Make `/api/v1/surfaces` the single runtime truth source.
* Generate UI gates, route registry, SDK clients, and route tests from contracts.
* Deprecate legacy routes behind explicit compatibility flag with removal date.
* Rename remaining capability vocabulary where it is not explicitly compatibility-only.

**Required tests:**

* Every UI route maps to a runtime surface.
* Every runtime surface maps to an OpenAPI operation.
* Every OpenAPI operation maps to a handler.
* Legacy routes disabled by default in production.

**Cleanup implications:** Remove duplicate route registrations once compatibility window closes.

---

## P1-5 Product/shared-library boundaries remain documented but not fully enforced

**Why it matters:** Shared libraries should provide reusable infrastructure, not hidden Data Cloud product behavior. Otherwise Data Cloud becomes coupled to platform internals and other products inherit Data Cloud-specific semantics.

**Root cause:** The canonical plane architecture correctly defines keep/move/split rules, but the Gradle graph still includes many shared platform modules alongside Data Cloud plane modules.  

**Evidence:**

* Plane architecture says keep platform modules only when generic/cross-product and move Data Cloud/Action semantics into `products/data-cloud`. 
* `settings.gradle.kts` includes many platform modules such as workflow, AI integration, governance, security, agent-core, messaging, data-governance, and tool-runtime, plus many Data Cloud Action Plane modules. 
* `DataCloudClient` explicitly uses a product SPI EventLogStore and bridges to platform EventLogStore through adapters. 
* Both `products:data-cloud:planes:action:kernel-bridge` and `products:data-cloud:extensions:kernel-bridge` are included, creating a boundary/ownership review candidate. 

**Affected surfaces:** Shared SPI, contracts, workflow, messaging, AI integration, governance, Action Plane, kernel bridge, platform contracts.

**Correct target pattern:** Strict dependency direction enforced by ArchUnit/build checks, with product semantics only in Data Cloud planes/extensions.

**Required fix:**

* Produce module ownership matrix.
* Move Data Cloud-specific workflow/action/memory/review/governance behavior into Data Cloud planes.
* Keep only generic primitives in platform modules.
* Add forbidden-dependency checks for product-to-product and Data/Event/Governance-to-Action internals.

**Required tests:**

* Build fails on forbidden imports.
* Shared modules do not import Data Cloud product packages.
* Product code does not duplicate platform abstractions.

**Cleanup implications:** Split, move, or delete modules that exist only because Data Cloud and AEP were previously separate product boundaries.

---

## P1-6 Connector lifecycle is not yet a durable ingestion/extraction runtime

**Why it matters:** Data Cloud’s data-source onboarding must lead to trusted, replayable, observable ingestion with source evidence, schema discovery, sync jobs, retries, and lineage.

**Root cause:** Connector registry is currently centered on metadata records in `dc_connections`; fabric operations are optional. When fabric is absent, test/sync report pending/degraded rather than executing. `handleEnableConnection` can set a connector active/healthy by state update, and `updateConnectionState` can synthesize a missing record. 

**Evidence:**

* `DataSourceRegistryHandler` stores connector metadata in `dc_connections`. 
* Raw credentials are removed/rejected and `secretRef` is preferred, which is good, but actual secret-vault integration was not verified from fetched code. 
* Fabric connector is optional; schema returns 503 when absent, test/sync return pending/degraded. 
* Enable/disable state mutation is separated from live connector validation. 

**Affected surfaces:** Connectors, data source onboarding, sync, schema discovery, health, lineage, data quality, ingestion jobs.

**Correct target pattern:** Connector control plane + ingestion job runtime + secret manager + schema registry + evidence ledger + retry/dead-letter pipeline.

**Required fix:**

* Split connector metadata from sync/job runtime.
* Require real fabric connector implementation for production connector enablement.
* Do not mark active/healthy without successful validation.
* Introduce sync job IDs, idempotency, row/source evidence, retry policy, DLQ, and provenance.
* Bind credentials to external secret manager reference only.

**Required tests:**

* Enable fails if connector cannot validate.
* Missing connector cannot be synthesized by state update.
* Sync retry is idempotent.
* Source row traces to canonical record and search/report result.
* Raw credentials never persisted or returned.

**Cleanup implications:** Replace metadata-only lifecycle with canonical connector runtime.

---

## P1-7 Contract, API, and UI parity is not yet one source of truth

**Why it matters:** Data Cloud needs stable APIs, generated clients, runtime truth, and UI actions to remain aligned. Manual router registration increases drift risk.

**Root cause:** Canonical OpenAPI files exist, but routes are hand-registered in `DataCloudRouterBuilder`; UI services/hooks and generated AEP clients also coexist.  

**Evidence:**

* README identifies canonical contracts under `products/data-cloud/contracts/openapi/data-cloud.yaml` and `action-plane.yaml`, plus compatibility `aep.yaml`. 
* Router contains hand-coded route registration across many domains. 
* Search found canonical Data Cloud OpenAPI plus a platform test resource copy of `data-cloud-openapi.yaml`, which is a drift risk unless it is generated/validated.  
* UI/action paths still include capability hooks and generated `aep-client.ts`.  

**Affected surfaces:** OpenAPI, generated SDK, UI API clients, route handlers, Action Plane compatibility, tests.

**Correct target pattern:** Contract-first route manifest with generated clients and route coverage tests.

**Required fix:**

* Generate route registry from OpenAPI operation IDs.
* Generate SDK/UI clients from canonical contracts only.
* Remove platform duplicate contract resources or generate them during tests.
* Add OpenAPI drift checks to CI.

**Required tests:**

* Every handler route exists in OpenAPI.
* Every OpenAPI route has handler coverage.
* UI imports generated client only for production API calls.
* Compatibility `aep.yaml` equals `action-plane.yaml` until retired.

**Cleanup implications:** Remove hand-maintained duplicated route/client/contract artifacts.

---

## P1-8 Provenance, lineage, and trust metadata are present but not enforced as mandatory

**Why it matters:** Data Cloud should be provenance-first. Every derived result, AI action, report, search result, and automation should trace back to source evidence.

**Root cause:** Data/event contracts include provenance-capable fields, but many are optional and validation is caller-driven. 

**Evidence:**

* `DataCloudClient.Event` includes optional source, subject, schema version, correlation, causation, actor, classification, policy context, provenance, and trace context. 
* `Event.validate()` requires `source`, but validation is not enforced in the record constructor. 
* `EntityCrudHandler` adds provenance and emits CDC events on save, but this is handler-specific rather than a globally enforced append/write contract. 
* Lineage handler/plugin paths exist, but implementation completeness was not verified from fetched content. 

**Affected surfaces:** Entity writes, events, connector sync, semantic index, search, analytics, reports, AI actions, governance, exports.

**Correct target pattern:** Mandatory provenance envelope at write/append boundaries.

**Required fix:**

* Enforce source, actor, tenant, correlation, classification, policy context, and provenance at append/write boundaries.
* Make AI-generated/derived records carry source evidence and confidence.
* Require reports/search/export to include lineage references or explicit “not available” trust state.
* Disallow authoritative UI display when provenance is missing.

**Required tests:**

* Event append without source/provenance fails in production.
* Report row traces to canonical/source record.
* AI suggestion carries evidence and confidence.
* Export includes provenance or explicit redaction/trust metadata.

**Cleanup implications:** Remove deprecated `Event.of` from production paths.

---

## P1-9 UI/product surfaces still have mock/deprecated route risk

**Why it matters:** User trust depends on the UI exposing only real backend-backed capabilities with accurate degraded states. Mocks and deprecated routes must not leak into production.

**Root cause:** UI mock/deprecated files and test API mocks remain in the Data Cloud UI tree, while canonical docs require UI to use generated clients and frontend adapters, not backend internals. 

**Evidence:**

* `delivery/ui/src/mocks/deprecatedRoutes.ts` exists. 
* `delivery/ui/e2e/helpers/api-mocks.ts` exists. 
* Real-backend E2E specs also exist by path, which is positive, but their completeness was not verified from content.  

**Affected surfaces:** UI route gating, onboarding, dashboard, Data, Events, Query, Pipelines, Trust, Operations, tests.

**Correct target pattern:** Mocks test-only; production UI consumes generated clients and runtime truth.

**Required fix:**

* Ensure mocks are excluded from production bundles.
* Replace deprecated route mappings with runtime truth driven redirects or compatibility UI banners.
* Add production build guard that fails on imports from `src/mocks`.

**Required tests:**

* Production build contains no mock imports.
* UI route map is generated or validated against `/api/v1/surfaces`.
* Every visible action has a real backend E2E path.

**Cleanup implications:** Move mocks under test-only folders or delete deprecated route mocks after compatibility removal.

---

## P2-10 Observability and resilience are present but not enforced as production gates

**Why it matters:** Data Cloud’s operational trust requires metrics, traces, audit events, structured errors, readiness, degraded state, and recovery paths.

**Root cause:** Observability hooks exist, but production enforcement is optional in several places. 

**Evidence:**

* `HttpHandlerSupport` provides request IDs, structured error envelopes, and response helpers. 
* Middleware provides rate limiting, payload limits, and content-type enforcement. 
* Metrics collector defaults to no-op if not explicitly configured. 
* Operations/runbook and validation scripts exist by path, but execution was not verified.  

**Required fix:** Production profile should require real metrics, traces, audit, health probes, alerting, backup/restore validation, and runtime truth checks.

---

## P2-11 Accessibility and i18n readiness are not verified from inspected implementation

**Why it matters:** Data Cloud is intended as enterprise-grade product UX. Accessibility and i18n cannot be retrofitted later without high cost.

**Root cause:** UI docs and tests exist by path, but this audit did not verify actual UI component implementation for keyboard, screen reader, focus, chart accessibility, translation, locale formatting, or timezone correctness.

**Evidence:** UI architecture/design/user manual docs exist by path, but content and UI implementation were not fully fetched or executed.   

**Status:** Not verified from current code/docs inspected.

---

## P2-12 Repository/document cleanup debt remains high

**Why it matters:** The prompt’s core goal is to stop repeated audits caused by stale docs, legacy audit artifacts, duplicate systems, and compatibility confusion.

**Root cause:** Canonical docs are improving, but old audit/analysis/implementation docs remain in archive and root-level locations. Some stale docs are archived correctly, but root-level audit/analysis files still create noise.    

---

# 7.3 Migration / Completeness Matrix

| Surface                             | Product Boundary | Shared Abstraction | Route/API Registry | Tenant Resolver | Permission Guard | Data Contract | Connector Runtime | Pipeline Runtime | Provenance | Privacy | Security | AI/Automation | Human Override | Observability | i18n | a11y | Tests | Cleanup | Status |
| ----------------------------------- | ---------------: | -----------------: | -----------------: | --------------: | ---------------: | ------------: | ----------------: | ---------------: | ---------: | ------: | -------: | ------------: | -------------: | ------------: | ---: | ---: | ----: | ------: | -----: |
| Data Cloud app shell/dashboard      |               🟡 |                 🟡 |                 🟡 |              🟡 |               🟡 |            🟡 |                 ⚫ |                ⚫ |         🟡 |      🟡 |       🟡 |            🟡 |             🟡 |            🟡 |   🔴 |   🔴 |    🟡 |      🟡 |     🟡 |
| Data source onboarding              |               🟡 |                 🟡 |                 🟡 |              🟡 |               🟡 |            🟡 |                🟡 |               🔴 |         🟡 |      🟡 |       🟡 |            🟡 |             🔴 |            🟡 |   🔴 |   🔴 |    🟡 |      🟡 |     🟡 |
| Connector registry                  |               🟡 |                 🟡 |                 🟡 |              🟡 |               🟡 |            🟡 |                🟡 |               🔴 |         🟡 |      🟡 |       🟡 |            🔴 |             🔴 |            🟡 |   🔴 |   🔴 |    🟡 |      🟡 |     🟡 |
| Credential/secret handling          |               🟡 |                 🟡 |                 🟡 |              🟡 |               🟡 |            🟡 |                🟡 |                ⚫ |         🟡 |      🟡 |       🟡 |             ⚫ |              ⚫ |            🟡 |    ⚫ |    ⚫ |    🟡 |      🟡 |     🟡 |
| Ingestion/extraction/transformation |               🟡 |                 🟡 |                 🟡 |              🟡 |               🟡 |            🟡 |                🔴 |               🔴 |         🟡 |      🟡 |       🟡 |            🟡 |             🔴 |            🟡 |   🔴 |   🔴 |    🟡 |      🟡 |     🔴 |
| Canonical data/fact model           |               🟡 |                 🟡 |                 🟡 |              🟡 |               🟡 |            🟡 |                 ⚫ |                ⚫ |         🟡 |      🟡 |       🟡 |             ⚫ |              ⚫ |            🟡 |   🔴 |   🔴 |    🟡 |      🟡 |     🟡 |
| Events/event log                    |               🟡 |                 🟡 |                 🟡 |              🟡 |               🟡 |            🟡 |                 ⚫ |               🟡 |         🟡 |      🟡 |       🟡 |            🟡 |              ⚫ |            🟡 |   🔴 |   🔴 |    🟡 |      🟡 |     🟡 |
| Search/retrieval/indexing           |               🟡 |                 🟡 |                 🟡 |              🟡 |               🟡 |            🟡 |                 ⚫ |                ⚫ |         🟡 |      🟡 |       🟡 |            🟡 |              ⚫ |            🟡 |   🔴 |   🔴 |    🟡 |      🟡 |     🟡 |
| Governance/trust/privacy            |               🟡 |                 🟡 |                 🟡 |              🟡 |               🟡 |            🟡 |                 ⚫ |                ⚫ |         🟡 |      🟡 |       🟡 |            🟡 |             🟡 |            🟡 |   🔴 |   🔴 |    🟡 |      🟡 |     🟡 |
| Action Plane / automation           |               🟡 |                 🟡 |                 🟡 |              🟡 |               🟡 |            🟡 |                 ⚫ |               🟡 |         🟡 |      🟡 |       🟡 |            🟡 |             🟡 |            🟡 |   🔴 |   🔴 |    🟡 |      🟡 |     🟡 |
| Runtime truth `/api/v1/surfaces`    |               🟡 |                 🟡 |                 🟡 |               ⚫ |                ⚫ |            🟡 |                 ⚫ |                ⚫ |         🟡 |      🟡 |       🟡 |            🟡 |             🟡 |            🟡 |    ⚫ |    ⚫ |    🟡 |      🟡 |     🟡 |
| Shared contracts                    |               🟡 |                 🟡 |                 🟡 |               ⚫ |                ⚫ |            🟡 |                 ⚫ |                ⚫ |         🟡 |      🟡 |       🟡 |             ⚫ |              ⚫ |             ⚫ |    ⚫ |    ⚫ |    🟡 |      🟡 |     🟡 |
| Shared runtime libraries            |               🟡 |                 🟡 |                  ⚫ |               ⚫ |                ⚫ |            🟡 |                 ⚫ |               🟡 |         🟡 |      🟡 |       🟡 |            🟡 |              ⚫ |            🟡 |    ⚫ |    ⚫ |    🟡 |      🟡 |     🟡 |
| UI/design-system usage              |               🟡 |                 🟡 |                 🟡 |               ⚫ |               🟡 |            🟡 |                 ⚫ |                ⚫ |         🟡 |      🟡 |       🟡 |            🟡 |             🟡 |            🟡 |   🔴 |   🔴 |    🟡 |      🟡 |     🟡 |
| Test/validation gates               |               🟡 |                 🟡 |                 🟡 |              🟡 |               🟡 |            🟡 |                🟡 |               🟡 |         🟡 |      🟡 |       🟡 |            🟡 |             🟡 |            🟡 |   🔴 |   🔴 |    🟡 |      🟡 |     🟡 |

Legend: ✅ Complete, 🟡 Partial, 🔴 Missing/unverified blocker, ⚫ Not applicable.

---

# 7.4 File-Level Gaps

| Root blocker        | Path                                                                                                                     | Gap                                                                                                     | Required fix                                                                                        | Required tests                                                           |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| Auth/scope          | `products/data-cloud/delivery/launcher/.../HttpHandlerSupport.java`                                                      | Tenant can be resolved from principal, header, query, or local fallback.                                | Replace with canonical authenticated `RequestContext`; production rejects query/header-only tenant. | Tenant spoofing, token/header mismatch, production fallback disabled.    |
| Auth/scope          | `products/data-cloud/delivery/launcher/.../DataCloudMiddleware.java`                                                     | Tenant rate limit extracts tenant from header/query and middleware is not the authorization layer.      | Rate limit after authenticated context; separate unauthenticated IP limits from tenant limits.      | Rate limit cannot be bypassed/spoofed by changing tenant header.         |
| Route/runtime truth | `DataCloudRouterBuilder.java`                                                                                            | Canonical `/api/v1/action/*` and legacy `/api/v1/*` action routes coexist.                              | Generate route registry from OpenAPI/runtime truth; flag legacy routes.                             | Route-contract parity, legacy disabled in production.                    |
| Atomicity           | `EntityCrudHandler.java`                                                                                                 | Transaction/idempotency coverage is partial and route-local.                                            | Shared mutation command runtime with durable idempotency/outbox.                                    | Retry, rollback, event failure, index failure tests.                     |
| Connector lifecycle | `DataSourceRegistryHandler.java`                                                                                         | Metadata-state lifecycle can report pending/degraded and enable state without full runtime validation.  | Connector job runtime with validation, sync job, secret manager, provenance.                        | Enable requires live validation; sync idempotency; raw secret rejection. |
| Production profile  | `DataCloudHttpServer.java`                                                                                               | Many critical dependencies are optional/no-op/in-memory/heuristic.                                      | Profile-validated composition that fails closed in production.                                      | Production profile startup matrix.                                       |
| Event contract      | `DataCloudClient.java`                                                                                                   | Event provenance/security fields are optional and validation is caller-driven.                          | Enforce canonical event envelope at append boundary.                                                | Append without source/provenance fails in production.                    |
| Shared boundary     | `settings.gradle.kts`                                                                                                    | Platform/shared and Data Cloud Action modules still require ownership review.                           | Module ownership matrix and dependency rules.                                                       | Forbidden import/build graph tests.                                      |
| Contract drift      | `products/data-cloud/contracts/openapi/data-cloud.yaml`, `platform/contracts/src/test/resources/data-cloud-openapi.yaml` | Canonical and copied/test contract resources can drift.                                                 | Generate copies or remove duplicate source.                                                         | OpenAPI drift check in CI.                                               |
| UI mock risk        | `delivery/ui/src/mocks/deprecatedRoutes.ts`, `delivery/ui/e2e/helpers/api-mocks.ts`                                      | Mock/deprecated route code remains in UI tree.                                                          | Move to test-only or remove after compatibility migration.                                          | Production bundle mock-import guard.                                     |

---

# 7.5 Prioritized Implementation Sequence

## 1. Canonical product/shared-library boundary hardening

**Goal:** Make Data Cloud product semantics live inside `products/data-cloud`, with shared libraries limited to generic primitives.

**Main areas:** `settings.gradle.kts`, `platform:java:*`, `products/data-cloud/planes/*`, kernel bridge modules.

**Acceptance criteria:**

* Product/shared ownership matrix exists.
* Forbidden dependency checks enforce plane rules.
* Platform modules contain no Data Cloud plane semantics.
* Duplicate kernel bridge ownership is resolved.

---

## 2. Canonical tenant/scope/authorization model

**Goal:** Create one trusted request/scope model for all routes.

**Main areas:** `HttpHandlerSupport`, `DataCloudSecurityFilter`, `RouteActionAccessRegistry`, middleware, handlers.

**Acceptance criteria:**

* Tenant comes from authenticated identity or trusted gateway context.
* Header/query tenant is blocked in production.
* Every route has action, sensitivity, permission, tenant/workspace requirement, and audit policy.
* Support/delegated access is explicit and audited.

---

## 3. Data source and connector runtime hardening

**Goal:** Move from connector metadata registry to durable connector lifecycle.

**Main areas:** `DataSourceRegistryHandler`, `DataFabricConnector`, connector jobs, secret handling, sync status.

**Acceptance criteria:**

* Enable requires successful live validation.
* Sync creates durable job with idempotency and row-level evidence.
* Raw credentials are never persisted or returned.
* Missing fabric runtime means surface is unavailable, not half-operational.

---

## 4. Ingestion/extraction/transformation pipeline hardening

**Goal:** Make ingestion deterministic, replayable, observable, and provenance-first.

**Acceptance criteria:**

* Source row → canonical record → index/report/export lineage exists.
* Retries are idempotent.
* Partial failure uses DLQ/poison record handling.
* Backfills and incremental syncs are test-covered.

---

## 5. Canonical data model, contracts, and schema evolution

**Goal:** Make entity/event/query/schema models consistent across backend, contracts, SDK, and UI.

**Acceptance criteria:**

* OpenAPI is canonical.
* Generated clients are used in UI/SDK.
* Event envelope fields are enforced at append boundary.
* Schema evolution tests cover incompatible changes.

---

## 6. Retrieval/search/indexing correctness

**Goal:** Ensure retrieval is tenant-aware, provenance-aware, and never stale without disclosure.

**Acceptance criteria:**

* Search/index operations are authorized by tenant/workspace/source.
* Index invalidation/rebuild is deterministic.
* Results include provenance/trust/freshness metadata.

---

## 7. Provenance, lineage, trust, and governance layer

**Goal:** Make trust explicit across every derived result.

**Acceptance criteria:**

* Every derived result has lineage.
* Every AI/automation action has evidence and confidence.
* Missing provenance blocks authoritative display.

---

## 8. AI/ML-native automation with human override

**Goal:** Make AI implicit, evidence-backed, interruptible, and safe.

**Acceptance criteria:**

* Low-confidence actions require review.
* Human takeover/override is supported.
* AI suggestions/actions are audited and reversible where applicable.

---

## 9. Data Cloud UI/UX and design-system consolidation

**Goal:** Ensure simple, information-rich, no-mock, no-dead-route UI.

**Acceptance criteria:**

* UI uses generated clients and runtime truth.
* Mocks are test-only.
* Every visible action has real backend coverage.
* Unsupported/degraded states are explicit.

---

## 10. Privacy/security/i18n/a11y/observability gates

**Goal:** Make cross-cutting requirements release-blocking.

**Acceptance criteria:**

* Production fails without auth, audit, metrics, durable stores, and policy engine.
* i18n and a11y tests exist and run.
* Security tests cover route matrix and data leakage.

---

## 11. Test hardening and golden-master validation

**Goal:** Replace test presence with meaningful test proof.

**Acceptance criteria:**

* Connector onboarding E2E.
* Tenant isolation E2E.
* Entity/event/outbox golden-master.
* Route/OpenAPI/UI parity tests.
* Production profile startup tests.
* No mock imports in prod bundle.

---

## 12. Repository cleanup and architectural consolidation

**Goal:** Remove legacy artifacts that keep causing repeated audits.

**Acceptance criteria:**

* Canonical docs matrix complete.
* Stale root audit docs moved/deleted.
* Archived docs clearly excluded from canonical truth.
* Duplicate routes/contracts/constants removed or intentionally feature-flagged.

---

# 7.6 Regression and Release Gates

Minimum production gates before readiness:

* [ ] Full Data Cloud app traversal from primary route.
* [ ] Route/action/permission matrix for all registered routes.
* [ ] Tenant spoofing tests for header, query, token mismatch, workspace mismatch.
* [ ] Support/delegated-access audit and redaction tests.
* [ ] Connector onboarding → test → schema → sync → canonical record E2E.
* [ ] Secret handling test proving raw credentials are rejected and not persisted.
* [ ] Entity save/update/delete golden-master with entity + event + provenance + outbox.
* [ ] Idempotency tests for every retryable mutating route.
* [ ] Event append rejects missing source/provenance in production.
* [ ] Search/retrieval/index authorization tests.
* [ ] Report/export/search result provenance parity tests.
* [ ] Runtime truth `/api/v1/surfaces` parity with OpenAPI and UI navigation.
* [ ] Legacy route compatibility disabled by default in production.
* [ ] AI suggestion evidence/confidence/human-review tests.
* [ ] Human override/interruption tests for automation.
* [ ] Privacy redaction and cross-tenant leakage tests.
* [ ] Accessibility test suite for keyboard/focus/screen reader/chart/table surfaces.
* [ ] i18n test suite for strings, dates, numbers, time zones, and locale fallback.
* [ ] Production profile startup fails without security/audit/metrics/durable stores/policy engine.
* [ ] OpenAPI drift check.
* [ ] No production imports from UI mocks.
* [ ] Build/lint/typecheck/unit/integration/E2E pass.
* [ ] Dead-code and duplicate-contract checks pass.
* [ ] Repository cleanup checklist complete.

---

# Repository Cleanup Plan

| Priority | Classification             | Path                                                                                                               | Reason                                                                                           | Evidence | Safe Fix                                                                              | Tests/Validation                    |
| -------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ | -------- | ------------------------------------------------------------------------------------- | ----------------------------------- |
| P0       | Replace                    | `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java` | Still acts as a large composition/god class with many optional production-critical dependencies. |          | Move profile validation and composition into smaller runtime composition modules.     | Production profile startup matrix.  |
| P0       | Replace                    | `HttpHandlerSupport.java` tenant resolution                                                                        | Tenant can come from principal/header/query/local fallback.                                      |          | Central authenticated `RequestContext`; block query/header-only tenant in production. | Tenant spoofing tests.              |
| P0       | Replace                    | `DataCloudMiddleware.java` tenant extraction                                                                       | Tenant rate limiting uses header/query before trusted auth context.                              |          | Tenant rate limits after auth context; unauthenticated IP limit separate.             | Rate-limit spoof tests.             |
| P0       | Replace                    | `EntityCrudHandler.java` mutation flow                                                                             | Idempotency and transaction support are partial/optional.                                        |          | Canonical command runtime with durable idempotency and outbox.                        | Retry/rollback/golden-master tests. |
| P1       | Merge/Replace              | `DataCloudRouterBuilder.java`                                                                                      | Canonical and legacy action routes coexist; route registry is hand-coded.                        |          | Generate route registry from OpenAPI/runtime truth; feature-flag legacy aliases.      | Route-contract parity tests.        |
| P1       | Replace                    | `DataSourceRegistryHandler.java`                                                                                   | Connector lifecycle is metadata-first; enable/sync can be degraded without durable runtime.      |          | Connector control plane + job runtime + secret manager + evidence ledger.             | Connector E2E and secret tests.     |
| P1       | Move/Split                 | Platform workflow/agent/messaging/AI/governance modules                                                            | Plane doc says to split/move Data Cloud/Action-specific behavior.                                |          | Module ownership matrix; move product behavior into Data Cloud.                       | Forbidden dependency checks.        |
| P1       | Merge                      | `products:data-cloud:planes:action:kernel-bridge` and `products:data-cloud:extensions:kernel-bridge`               | Two Data Cloud kernel bridge modules need ownership clarification.                               |          | Keep one canonical bridge boundary or split by runtime vs extension with docs.        | Build graph and dependency tests.   |
| P1       | Replace                    | `platform/contracts/src/test/resources/data-cloud-openapi.yaml`                                                    | Potential duplicate of canonical product contract.                                               |          | Generate test resource from canonical OpenAPI or delete duplicate after verification. | OpenAPI drift check.                |
| P1       | Move/Archive               | `dc-aep-analysis.md`                                                                                               | Root-level historical analysis conflicts with canonical plane model.                             |          | Move under archive or delete if superseded.                                           | Docs index check.                   |
| P1       | Move/Archive               | `ghatana-data-cloud-aep-end-to-end-audit.md`                                                                       | Root-level old audit artifact creates repeated audit noise.                                      |          | Move to archive with noncanonical marker or delete.                                   | Docs truth check.                   |
| P2       | Keep but mark noncanonical | `docs/archive/data-cloud-audit-legacy/*`                                                                           | Archive exists, but should not influence current audits.                                         |          | Keep only if clearly excluded from canonical docs.                                    | Documentation truth checker.        |
| P2       | Move/Delete                | `delivery/ui/src/mocks/deprecatedRoutes.ts`                                                                        | Mock/deprecated route risk in UI source tree.                                                    |          | Move to test-only or remove after compatibility migration.                            | Production bundle import guard.     |
| P2       | Move                       | `delivery/ui/e2e/helpers/api-mocks.ts`                                                                             | Acceptable for tests, but must never leak to production.                                         |          | Keep under test-only with import restrictions.                                        | Prod build mock check.              |
| P2       | Merge                      | Duplicate bodyless mutation route constants                                                                        | Constants appear in both server and middleware.                                                  |          | Single route metadata registry.                                                       | Middleware route coverage tests.    |

---

## Canonical Docs Matrix

| Doc                                                               | Keep | Merge | Archive | Delete | Notes                                                                              |
| ----------------------------------------------------------------- | ---: | ----: | ------: | -----: | ---------------------------------------------------------------------------------- |
| `products/data-cloud/README.md`                                   |    ✅ |       |         |        | Canonical product index and module map.                                            |
| `products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md`     |    ✅ |       |         |        | Canonical architecture and migration rules.                                        |
| `docs/product/01_data_cloud_unified_vision_market_positioning.md` |    ✅ |       |         |        | Keep as canonical product/market doc.                                              |
| `docs/product/02_data_cloud_unified_detailed_architecture.md`     |    ✅ |       |         |        | Keep as detailed architecture.                                                     |
| `docs/product/03_data_cloud_unified_high_level_design.md`         |    ✅ |       |         |        | Keep as UX/API/migration design.                                                   |
| `products/data-cloud/contracts/README.md`                         |    ✅ |       |         |        | Contract Plane ownership.                                                          |
| `products/data-cloud/contracts/openapi/data-cloud.yaml`           |    ✅ |       |         |        | Canonical Data Cloud API.                                                          |
| `products/data-cloud/contracts/openapi/action-plane.yaml`         |    ✅ |       |         |        | Canonical Action Plane API.                                                        |
| `products/data-cloud/contracts/openapi/aep.yaml`                  |    ✅ |       |         |        | Keep only as temporary compatibility contract.                                     |
| `products/data-cloud/docs/api/REST_API_DOCUMENTATION.md`          |    ✅ |    🟡 |         |        | Should be generated/validated from OpenAPI.                                        |
| UI architecture/design docs                                       |   🟡 |     ✅ |         |        | Merge into one canonical frontend/design-system architecture.                      |
| `products/data-cloud/docs/DISTRIBUTED_WORKFLOW_ROADMAP.md`        |      |     ✅ |      🟡 |        | Merge into Action Plane implementation roadmap.                                    |
| `docs/archive/data-cloud-audit-legacy/*`                          |      |       |       ✅ |        | Keep only with noncanonical disclaimer.                                            |
| `docs/archive/data-cloud-implementation-legacy/*`                 |      |       |       ✅ |        | Keep only if referenced as historical archive.                                     |
| `dc-aep-analysis.md`                                              |      |       |       ✅ |     🟡 | Root-level historical artifact; archive/delete after confirming no canonical refs. |
| `ghatana-data-cloud-aep-end-to-end-audit.md`                      |      |       |       ✅ |     🟡 | Root-level audit artifact; archive/delete after confirming no canonical refs.      |

---

## Final Cleanup Checklist

* [ ] Legacy root-level Data Cloud/AEP audit docs moved to archive or deleted.
* [ ] Archive docs explicitly excluded from canonical source of truth.
* [ ] Canonical docs index points only to current product/architecture/design/API/ops docs.
* [ ] Product/shared module ownership matrix created.
* [ ] Product-specific logic removed from shared platform libraries.
* [ ] Duplicate kernel bridge ownership resolved.
* [ ] Duplicate OpenAPI/test contract copies removed or generated.
* [ ] Duplicate route constants merged into canonical route metadata.
* [ ] Legacy action route aliases feature-flagged or removed.
* [ ] Capability vocabulary removed except explicit compatibility code.
* [ ] UI mocks isolated to test-only folders.
* [ ] Production build fails on mock imports.
* [ ] Production profile fails without security, audit, metrics, durable stores, and policy engine.
* [ ] Entity/event/provenance/idempotency mutation runtime centralized.
* [ ] Connector lifecycle upgraded from metadata registry to durable job runtime.
* [ ] Build/lint/typecheck/unit/integration/E2E pass after cleanup.
* [ ] No hidden fallback runtime paths remain in production profile.
* [ ] No duplicate route runtimes remain.
* [ ] No duplicate contract truth remains.
