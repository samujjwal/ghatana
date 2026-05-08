# End-to-End Data Cloud Correctness + Shared Libraries Audit

**Intended repository path:** `products/data-cloud/docs/audits/end-to-end-data-cloud-correctness-shared-libraries-audit.md`  
**Repository:** `samujjwal/ghatana`  
**Target commit/ref:** `3ac6b0226e48e784e6c4c44e3c426adcb833de7e`  
**Audit mode:** Evidence-backed targeted execution of the requested Data Cloud + shared-libraries prompt.

> Important limitation: I could validate the target commit, canonical Data Cloud docs, key runtime/shared SPI/storage/server/UI files, and major route/runtime-truth surfaces. The GitHub file-search index was not available for full-tree enumeration, and the GitHub API tool could not list a recursive tree directly. Therefore, this report is a strong targeted audit, but not a mathematically complete inventory of every file under `products/data-cloud`. Any area marked **Unknown due to insufficient evidence** must be inspected with a local checkout or repository-wide grep/build/test run.

---

## 1. Executive Summary

### Ratings

| Dimension | Rating | Notes |
|---|---:|---|
| Correctness | 5/10 | Strong architecture intent, but critical implementation gaps exist in entity identity, collection isolation, query behavior, event semantics, AI fallback behavior, and production fail-closed rules. |
| Completeness | 5/10 | Many surfaces exist, but several are optional/fallback/stubbed, and some canonical migrations are incomplete. |
| Production readiness | 4/10 | Not production-safe until P0 blockers are fixed. |
| Mock/stub/shortcut risk | High | AI assist returns 200 heuristic fallbacks when LLM is missing; optional audit/trace/settings paths silently degrade. |
| Reuse/DRY/source-of-truth | 5/10 | Good plane/SPI direction, but duplicated query/filter/status/event abstractions are already drifting. |
| Shared-library boundary | 6/10 | Docs define good rules, but platform-kernel Data Cloud adapter and duplicated event-store abstractions require review/consolidation. |
| Safe for production | **No** | P0 blockers exist. |
| Safe for internal demo | **Yes, with explicit local/preview labeling only** | Must not claim production readiness or complete runtime truth. |

### Commit validation

Target commit `3ac6b0226e48e784e6c4c44e3c426adcb833de7e` exists, but it only changes `products/yappc/CHANGELOG.md`; the Data Cloud audit is therefore an audit of the repository state at that commit, not a Data Cloud-specific diff. Evidence: target commit metadata and file list show only the YAPPC changelog change. fileciteturn3file0

### Top P0 issues

1. **Entity identity is not collection-scoped.** `EntityStore` operations find/delete/exist by tenant + entity id only; in-memory and H2 stores also key by tenant + entity id only. Same `id` in two collections can overwrite, read, or delete the wrong entity. fileciteturn16file0 fileciteturn21file0 fileciteturn25file0 fileciteturn35file0
2. **H2 sovereign query/count ignores filters, sorts, search, projection, and freshness semantics.** Query and count SQL only use tenant + collection + deleted, causing incorrect results and incorrect pagination metadata. fileciteturn25file0
3. **Event envelope and event-store guarantees are incomplete.** Architecture requires tenant, subject, source, schema version, correlation/causation, actor, classification, policy context, provenance, and trace context; current client/SPI exposes only a reduced event entry. H2 idempotency is stored but not enforced; batch append is not atomic despite SPI documentation. fileciteturn8file0 fileciteturn19file0 fileciteturn22file0 fileciteturn26file0
4. **Production-path AI assist can return static heuristic/fallback success.** Bootstrap logs that AI routes return stubs when no LLM backend is configured, and `AiAssistHandler` intentionally returns 200 static heuristic responses with `ai.fallback=true`. fileciteturn31file0 fileciteturn39file0
5. **Production fail-closed is incomplete.** Architecture requires production profiles to fail closed for missing security, policy, audit, durability, and runtime dependencies, but multiple optional paths silently degrade: audit emission can be skipped, settings can use in-memory storage, trace spans can be discarded, and AI can fall back to heuristics. fileciteturn8file0 fileciteturn29file0 fileciteturn32file0

---

## 2. Scope and Method

### Included evidence

- Commit metadata for `3ac6b0226e48e784e6c4c44e3c426adcb833de7e`
- `products/data-cloud/README.md`
- `products/data-cloud/OWNER.md`
- `products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md`
- `products/data-cloud/docs/product/01_data_cloud_unified_vision_market_positioning.md`
- `products/data-cloud/docs/product/02_data_cloud_unified_detailed_architecture.md`
- `products/data-cloud/docs/api/REST_API_DOCUMENTATION.md`
- Data Cloud shared SPI:
  - `DataCloudClient.java`
  - `EntityStore.java`
  - `EventLogStore.java`
  - `TenantContext.java`
- Runtime composition:
  - `DataCloud.java`
  - `H2SovereignEntityStore.java`
  - `H2SovereignEventLogStore.java`
- Launcher/server:
  - `DataCloudLauncher.java`
  - `DataCloudLauncherSettings.java`
  - `DataCloudConfigValidator.java`
  - `DataCloudHttpLauncherBootstrap.java`
  - `DataCloudHttpServer.java`
  - `EntityCrudHandler.java`
  - `AiAssistHandler.java`
- UI:
  - `App.tsx`
  - `routes.tsx`
  - `RoleProtectedRoute.tsx`
  - `RuntimeCapabilityRouteGate.tsx`
  - `capabilities.service.ts`
  - `api/client.ts`

### Excluded / unknown

The following must be completed with a local checkout or full repository tree search:

- Full recursive inventory of all `products/data-cloud/**` files.
- Full OpenAPI parity check for `data-cloud.yaml`, `action-plane.yaml`, and `aep.yaml`.
- Full Gradle dependency graph / ArchUnit boundary validation.
- Full test execution.
- Full UI compile/typecheck.
- Full shared-library import graph across `platform-kernel`, `@ghatana/theme`, `@ghatana/canvas`, and platform Java libraries.
- Full route registration parity between `DataCloudHttpServer`, OpenAPI, SDK, and REST docs.

---

## 3. Data Cloud Vision and Capability Map

Canonical docs define Data Cloud as an AI-native operational data fabric that unifies trusted data, durable events, governed context, intelligence, policy, and action. AEP is not a separate customer-facing product; it is the runtime implementation behind the Action Plane. fileciteturn4file0

| Vision / Use Case | Expected Capability | Current Status | Gap |
|---|---|---|---|
| Trusted operational entities | Tenant/workspace/collection-safe entity CRUD/query/history | Partially implemented | Entity identity is tenant+id only in stores; collection-scoped semantics are broken. |
| Durable event history | Append-only event envelope with replay, idempotency, trace/provenance | Partially implemented | Envelope lacks required typed fields; idempotency not enforced; batch append not atomic. |
| Runtime Truth | `/api/v1/surfaces` target; `/api/v1/capabilities` compatibility only | Partially implemented | UI still calls `/capabilities`; capability naming persists. |
| Governed AI/action | AI assistance and automation with HITL, evidence, review, fallback transparency | Partially implemented | Heuristic/static fallback returns 200 in production path; must be gated or disabled. |
| Multi-tenant sovereignty | Tenant/workspace/region/policy isolation and fail-closed production profiles | Partially implemented | Workspace not propagated broadly; production fail-closed does not cover audit/policy/durability comprehensively. |
| Operator visibility | health/readiness/metrics/runtime truth/audit/alerts | Partially implemented | Optional audit/trace/settings paths silently degrade. |
| Reusable product contracts | OpenAPI/SDK/SPI as canonical source | Partially implemented | Duplicated filters/status/envelope/client abstractions drift. |

---

## 4. Plane Architecture Audit

The canonical architecture defines ten planes and explicitly forbids Data/Event/Context/Governance/Intelligence planes from depending on Action implementation internals. fileciteturn6file0

| Plane | Status | Key finding |
|---|---|---|
| Experience | Partial | UI exists with outcome-first routes; some routes use runtime capability gates, but gates still use compatibility `/capabilities` and allow children while loading. |
| Contract | Unknown / Partial | README identifies canonical contracts, but OpenAPI parity was not fully verified. |
| Runtime Truth | Partial | Target `/surfaces` exists in docs; UI still consumes `/capabilities`. |
| Data | Incorrect | Entity identity and query semantics are broken in core storage providers. |
| Event | Incorrect / Partial | Event envelope, idempotency, atomic append, and offset semantics need hardening. |
| Context | Unknown / Partial | Routes and docs exist; implementation not fully audited. |
| Intelligence | Partial | AI assist exists but falls back to heuristics/stubs. |
| Governance | Partial | TenantContext exists; enforcement/audit fail-closed is incomplete. |
| Action | Unknown / Partial | Pipelines/action surfaces exist but deeper Action Plane runtime was not fully audited. |
| Operations | Partial | Health/metrics/runtime signals exist, but trace/audit can silently degrade. |

---

## 5. Complete Inventory

### 5.1 Documentation inventory

| Document | Purpose | Status |
|---|---|---|
| `README.md` | Canonical product overview, plane model, runtime truth and contracts | Mostly aligned, but `/capabilities` migration still incomplete. |
| `OWNER.md` | Product ownership and boundary | Good; boundary score already 6/10. |
| `docs/architecture/PLANE_ARCHITECTURE.md` | Canonical target architecture | Strong; implementation does not fully meet it. |
| `docs/product/01...vision...md` | Vision/market/product boundary | Strong; implementation gaps remain. |
| `docs/product/02...architecture.md` | Detailed architecture/runtime/governance | Strong; implementation gaps remain. |
| `docs/api/REST_API_DOCUMENTATION.md` | Human-readable API inventory | Useful but likely stale in parts; claims strict tenant/audit behavior not proven by code. |

### 5.2 Contract inventory

| Contract | Status |
|---|---|
| `contracts/openapi/data-cloud.yaml` | Identified as canonical, parity not fully verified. |
| `contracts/openapi/action-plane.yaml` | Identified as canonical, parity not fully verified. |
| `contracts/openapi/aep.yaml` | Compatibility contract; equivalence not verified. |
| UI schema contracts | `capabilities.service.ts` parses capability envelope, but still uses compatibility naming and endpoint. |

### 5.3 UI inventory

| UI route/surface | Status |
|---|---|
| `/` Home | Present |
| `/data` + nested data routes | Present |
| `/pipelines` + nested pipeline routes | Present |
| `/query` | Present |
| `/trust`, `/insights`, `/operations`, `/alerts` | Present, role-protected |
| `/events`, `/memory`, `/entities`, `/context`, `/fabric`, `/agents` | Present; some runtime gated |
| `/settings`, `/plugins`, `/connectors` | Present; settings/connectors gated, plugins not runtime-gated |
| Compatibility aliases (`/dashboard`, `/collections`, `/workflows`, etc.) | Present |

### 5.4 Backend/API inventory

The REST docs list routes for probes, entities/search, events, pipelines/checkpoints, memory/brain/learning, analytics/reports/models/features, governance/lineage/context/data-products, capabilities/plugins/autonomy/agents, operations/streaming, voice, federation, and migration. fileciteturn34file0

High-risk backend flows inspected:

- Entity CRUD and batch operations.
- Event append/query/tail.
- Runtime truth/capability registry client path.
- AI assist.
- Launcher startup, auth, trace, audit, settings, production validation.

### 5.5 Data/Event/Storage inventory

| Item | Status | Finding |
|---|---|---|
| `DataCloudClient` | Public facade | Too thin for required event envelope and query semantics. |
| `EntityStore` SPI | Shared SPI | Missing collection in identity operations. |
| `EventLogStore` SPI | Shared SPI | Missing typed required envelope fields and idempotency semantics. |
| `InMemoryEntityStore` | Local/testing provider | Collection overwrite/delete risk. |
| `InMemoryEventLogStore` | Local/testing provider | Offset semantics need conformance tests. |
| `H2SovereignEntityStore` | File-backed provider | Collection isolation and query correctness broken. |
| `H2SovereignEventLogStore` | File-backed provider | Batch atomicity/idempotency incomplete; offsets global. |

### 5.6 Shared library inventory

| Library / surface | Status |
|---|---|
| `planes/shared-spi` | Product-owned shared SPI; needs stronger canonical modeling. |
| `delivery/sdk` | Not audited; must be checked against OpenAPI. |
| `platform-kernel/kernel-core/.../datacloud` | Search found direct Data Cloud adapter types; surface not deeply audited. |
| platform governance/security | Used by launcher/server; must be audited for authz/tenant enforcement. |
| platform AI integration | Used by LLM bootstrap; includes hardcoded DNS concern. |
| `@ghatana/theme` | Used by UI provider; not deeply audited. |
| `@ghatana/canvas` | Listed as jointly owned in OWNER, but no direct UI usage was validated in this run. |

---

## 6. Requirement-to-Implementation Traceability Matrix

| Capability | UI | API/Backend | Data/Event | Tests | Status |
|---|---|---|---|---|---|
| Entity save/read/query/delete | `/data`, `/entities` | `EntityCrudHandler`, `DataCloudClient` | EntityStore/H2/InMemory | Unknown | **Complete but incorrect** |
| Event append/query/tail | `/events` | Event handler/client | EventLogStore/H2/InMemory | Unknown | **Partial/incorrect** |
| Runtime truth | Runtime gates | `/surfaces` target, `/capabilities` compatibility | Capability registry | Unknown | **Partial/migration incomplete** |
| AI assist | Query/pipeline/entity assist | `AiAssistHandler` | Optional AI action audit | Unknown | **Stub/fallback risk** |
| Governance/privacy/audit | `/trust` | governance routes, optional audit | TenantContext, policy/audit optional | Unknown | **Partial** |
| Settings | `/settings` | Settings handler/store | InMemory/JDBC | Unknown | **Partial; production persistence risk** |
| Plugins/connectors | `/plugins`, `/connectors` | plugin/connectors handlers | plugin manager | Unknown | **Partial** |
| Operations | `/operations`, `/alerts` | health/metrics/trace | optional trace/audit | Unknown | **Partial** |

---

## 7. End-to-End Journey Audit

| Journey | Actual behavior | Severity | Required fix |
|---|---|---:|---|
| Save entity in two collections with same id | Underlying stores key by tenant+id; one collection can overwrite another. | P0 | Make entity identity tenant/workspace/collection/id across SPI, stores, APIs, tests. |
| Query entities with filters/sorts in sovereign profile | H2 query ignores filters/sorts/search/projections; count ignores filters. | P0 | Implement full QuerySpec or reject unsupported query options with 400/422. |
| Delete entity by collection/id | Handler validates collection, but store delete ignores collection. | P0 | Collection-scoped delete and find. |
| Append event with required envelope | Client/SPI do not require canonical envelope fields. | P0 | Introduce canonical event envelope and validation. |
| Idempotent event append | H2 stores idempotency key but does not enforce it. | P0 | Unique constraint and idempotent replay response. |
| Batch append | SPI says atomic; H2 loops append without transaction. | P0 | Single transaction with rollback. |
| AI suggest without LLM | Returns static heuristic 200 fallback. | P0/P1 | Disable/gate in production; return explicit unavailable or preview state. |
| Runtime truth route gating | UI still fetches `/capabilities`; gate renders children while loading. | P1 | Move to `/surfaces`; deny/placeholder while loading for non-core optional routes. |
| Production startup | Validates selected env toggles, not all required production trust deps. | P0 | Profile-aware production preflight enforcing auth, policy, audit, durable stores, trace/metrics. |

---

## 8. UI/UX Audit

### Strengths

- The route map mostly follows the outcome-first navigation model: Home, Data, Pipelines, Query, Trust, Operations, plus contextual surfaces. fileciteturn42file0
- RoleProtectedRoute explicitly separates shell disclosure from backend authorization, which is a good security/UX distinction. fileciteturn43file0
- SessionBootstrap rejects reserved tenant IDs such as `default` and `default-tenant`, reducing accidental ambiguous tenant context. fileciteturn44file0

### Issues

| Issue | Severity | Evidence | Required fix |
|---|---:|---|---|
| UI runtime truth still uses `/capabilities`, not `/surfaces`. | P1 | `capabilities.service.ts` calls `/capabilities`; README says `/surfaces` is target and `/capabilities` is compatibility. | Create `surfaces.service.ts`, migrate gates and UI labels to Runtime Truth terminology. |
| Runtime gate renders children while capabilities are loading. | P1 | `RuntimeCapabilityRouteGate` returns children when loading without data. | Render safe disabled/loading shell for optional surfaces until runtime truth loads. |
| Some optional surfaces are not runtime-gated. | P1 | `events` and `plugins` routes are role-protected but not capability-gated. | Apply runtime truth gates consistently. |
| API cache invalidation is too narrow. | P2 | Mutation invalidates exact URL only. | Invalidate collection/list/detail query families after writes. |
| Potential type issue in route fallback lambdas. | P1/P2 | `withSuspense` expects lazy component but is passed inline function in fallback. | Replace with direct fallback element or typed lazy component. |

---

## 9. Frontend Action/State/Data Flow Audit

| Flow | Finding | Severity | Fix |
|---|---|---:|---|
| API client tenant propagation | Adds `X-Tenant-ID` from SessionBootstrap; good. | — | Keep. |
| API error correlation | Parses `X-Correlation-ID`; good. | — | Keep. |
| Cache invalidation | Exact-url invalidation can leave list/detail data stale. | P2 | Add query-key based invalidation per domain operation. |
| Runtime gating | Uses compatibility capability registry and allow-while-loading behavior. | P1 | Move to surfaces and safe-loading behavior. |
| Shell role | UI disclosure only, not backend auth; good if backend enforcement is real. | — | Add integration tests proving backend denies unauthorized access regardless shell role. |

---

## 10. Backend/API/Domain/Event/Storage Audit

### Entity model and storage

**Finding P0 — collection identity is broken.**

- `EntityStore.findById`, `delete`, `exists`, and related methods accept tenant + entity id only, not collection. fileciteturn21file0
- `DataCloudClient.findById/delete` accept collection at API level, but pass only id into the store. fileciteturn16file0
- `H2SovereignEntityStore` primary key is `(tenant_id, entity_id)`, and select/update/delete use only tenant + id. fileciteturn25file0
- `EntityCrudHandler` validates collection, then calls `client.findById` and `client.delete`, which currently cannot enforce collection-scoped identity. fileciteturn35file0

### Query model

**Finding P0 — H2 QuerySpec is not honored.**

`H2SovereignEntityStore.query` and `count` only use tenant, collection, deleted flag, limit, and offset; filters, sorts, search, projections, consistency, and freshness hints are ignored. fileciteturn25file0

### Event model

**Finding P0 — event envelope is too weak.**

The architecture requires a full event envelope with tenant, event id, subject, source, schema version, correlation/causation, actor, classification, policy context, timestamp, provenance, and trace context. Current `DataCloudClient.Event` and `EventLogStore.EventEntry` do not model most of those as mandatory fields. fileciteturn8file0 fileciteturn19file0 fileciteturn22file0

### Production profile

**Finding P0/P1 — production fail-closed is incomplete.**

Docs require production profiles to fail closed for missing security, policy, audit, durability, and runtime dependencies. Config validation covers selected env variables but not all production trust dependencies. Optional audit/trace/settings/AI paths can silently degrade. fileciteturn8file0 fileciteturn29file0 fileciteturn32file0

---

## 11. Contract/API/SDK/Runtime Truth Audit

| Area | Status | Finding |
|---|---|---|
| `data-cloud.yaml` | Unknown | Must be checked against runtime route inventory. |
| `action-plane.yaml` | Unknown | Must be checked against action routes and `aep.yaml`. |
| `/api/v1/surfaces` | Partial | Target exists in docs, but UI still consumes `/capabilities`. |
| `/api/v1/capabilities` | Compatibility path | Still primary UI dependency. |
| Runtime state naming | Partial | Docs say avoid capability truth/registry; implementation still uses capability types. |
| SDK generation | Unknown | Must verify generated SDK uses product-level contracts and UI uses generated/adapted clients. |

---

## 12. Production Mock/Stub/Shortcut Audit

| Evidence | Production reachable? | Severity | Required action |
|---|---:|---:|---|
| `DataCloudHttpLauncherBootstrap` warns AI routes will return stubs when no backend configured. | Yes | P0/P1 | Disable/gate AI routes in production when no real completion service exists. |
| `AiAssistHandler` returns static heuristic fallback with 200 and `ai.fallback=true`. | Yes | P0/P1 | Return explicit unavailable or preview-only response unless route is configured as non-critical preview. |
| `AuditService` null means audit emission silently skipped. | Yes | P0 | Production must fail closed for audit-required mutations. |
| `TraceExportService` null means spans generated but discarded. | Yes | P1 | Production runtime truth must mark traces degraded/unavailable, or fail closed for production SLO mode. |
| `SettingsStore` null means in-memory settings store. | Yes | P1 | Production must require persistent settings storage. |
| Batch entity save skips semantic indexing to avoid null promise issues. | Yes | P1 | Implement batch semantic indexing or explicitly disable semantic features for batch with runtime truth and tests. |
| In-memory entity idempotency store for writes. | Yes | P1 | Use durable idempotency store in production. |

---

## 13. Reusability, Abstraction, and DRY Audit

| Duplicate / drift area | Severity | Evidence | Required fix |
|---|---:|---|---|
| Entity identity split between API collection-aware methods and store collection-unaware methods | P0 | `DataCloudClient` vs `EntityStore` vs H2 schema. | Introduce canonical `EntityRef(tenant, workspace, collection, id)`. |
| Query/filter models duplicated | P1 | `DataCloudClient.Filter` uses strings; `EntityStore.Filter` uses enum and more operators. | Generate/adapt from canonical query contract. |
| Event store abstractions duplicated | P1 | `DataCloudClient` has pending migration note; Bootstrap adapts Data Cloud event store to platform event store. | Decide canonical event-store SPI and remove legacy duplication. |
| Runtime truth vs capability naming | P1 | Docs say runtime truth; UI still `CapabilityRegistry`. | Rename service/types and migrate endpoint. |
| Optional subsystem behavior scattered | P1 | Server fields independently decide 501/503/fallback/silent skip. | Central Runtime Truth Registry should own availability and UI/API gating. |

---

## 14. Shared Library Boundary and Enhancement Audit

### Findings

1. `planes/shared-spi` is the correct product-owned location for Data Cloud public SPI, but it needs stronger contracts for entity identity, query semantics, event envelope, idempotency, tenant/workspace propagation, and production conformance.
2. Platform kernel has Data Cloud adapter types found in `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/adapter/datacloud/...`; these must be reviewed to ensure Data Cloud-specific behavior does not leak into generic kernel libraries.
3. `@ghatana/theme` is appropriately consumed by UI provider, but no deep design-system audit was completed.
4. `@ghatana/canvas` is listed as jointly owned in OWNER but direct Data Cloud usage was not validated in this run.

### Required enhancements

| Enhancement | Owner |
|---|---|
| Canonical `EntityRef` / `CollectionRef` / `TenantWorkspaceContext` | `planes/shared-spi` |
| Canonical event envelope and idempotency semantics | `planes/shared-spi` + contracts |
| QuerySpec conformance suite for all stores | `delivery/runtime-composition/conformance` or `planes/shared-spi/testing` |
| Runtime Truth client and UI gate package | `delivery/ui` + `libs/ui-components` if reusable |
| Production profile validation framework | `planes/operations/config` |
| Durable idempotency/audit/settings abstractions | shared SPI / operations/governance planes |

---

## 15. Security, Privacy, Governance, and Sovereignty Audit

| Area | Finding | Severity | Required fix |
|---|---|---:|---|
| Tenant isolation | Entity storage is tenant-scoped but not collection/workspace-scoped. | P0 | Include workspace/collection in identity and storage keys. |
| Auth | Non-embedded deployments require API keys or JWT unless insecure mode. | Good baseline | Add tests and explicit production profile gates. |
| API key tenant binding | API key resolver maps all keys to tenant `service`; X-Tenant-ID may still select tenant. | P1 | Bind keys to allowed tenants/scopes or require JWT tenant claim for production. |
| Audit | Audit service can be null and skipped. | P0 | Fail closed for sensitive mutations. |
| Sovereignty | LLM bootstrap uses hardcoded DNS `8.8.8.8`. | P1 | Use configurable DNS/resolver; disallow public DNS in sovereign/production unless explicit. |
| Trace/privacy | Trace export can be absent and silently discarded. | P1 | Runtime truth + production profile gating. |

---

## 16. Observability and Operability Audit

| Flow | Finding | Severity | Required fix |
|---|---|---:|---|
| Health/readiness | Health handlers and subsystem probes exist. | — | Keep and expand. |
| Runtime truth | `/surfaces` target documented but UI not migrated. | P1 | Complete migration and retirement plan. |
| Traces | Trace service optional; absence discards spans. | P1 | Production runtime truth/fail-closed. |
| Audit | Optional audit service means critical action evidence can be missing. | P0 | Require audit writer for sensitive routes. |
| Metrics | Metrics collector wired, but no full SLO/dashboard parity verified. | P2 | Add route/flow metrics coverage tests. |
| Runbooks | RUNBOOK exists but not fully audited. | P2 | Verify runbook against launcher profiles and health endpoints. |

---

## 17. Performance and Scalability Audit

| Area | Risk | Severity | Required fix |
|---|---|---:|---|
| H2 query | Ignores filters/sorts and lacks indexes for query operators. | P0/P1 | Implement query planner/SQL builder and indexes. |
| In-memory local store | Unbounded tenant/entity/event maps. | P2 | Add local limits, warnings, and test-only constraints. |
| Event tailing H2 | Polling every 250ms per subscription. | P2 | Add scalable subscription model or bounded polling controls. |
| UI cache | Exact URL invalidation can stale data. | P2 | Domain-aware invalidation. |
| Batch writes | Per-item saves/events without transaction. | P1 | Define atomic/best-effort semantics and implement accordingly. |
| AI prompt body | Request load body limited by token heuristic; good baseline. | — | Add payload redaction tests. |

---

## 18. Test Correctness and Coverage Audit

### Required tests

| Capability/Flow | Required test |
|---|---|
| Collection-scoped identity | Save same id in two collections; verify read/delete/update isolation in in-memory and H2. |
| H2 query semantics | Filters, sorts, search, projection, count, hasMore, invalid operators. |
| Event envelope | Append rejects missing required envelope fields; stores trace/provenance/classification. |
| Event idempotency | Repeated idempotency key returns same offset/result. |
| Event appendBatch | Atomic success and rollback on one invalid/failing entry. |
| Production startup | Production refuses missing durable entity/event stores, auth, policy, audit, metrics/traces where required. |
| AI fallback | Production with no LLM disables/gates AI routes; local may return explicit preview fallback. |
| Runtime Truth UI | Optional surfaces do not render until `/surfaces` confirms availability. |
| Contract parity | OpenAPI route inventory equals `DataCloudHttpServer` registrations. |
| SDK parity | UI generated/adapted clients match OpenAPI. |
| Tenant auth | Shell role changes do not grant backend access. |
| Audit evidence | Sensitive mutations emit durable audit events. |
| Cache invalidation | Entity writes invalidate list/detail/search queries. |

---

## 19. Prioritized Remediation Plan

| Priority | Area | Issue | Evidence/File(s) | Required Fix | Acceptance Criteria | Tests Required |
|---|---|---|---|---|---|---|
| P0 | Data Plane | Entity identity not collection-scoped | `DataCloud.java`, `EntityStore.java`, `H2SovereignEntityStore.java`, `EntityCrudHandler.java` | Introduce canonical `EntityRef` and update stores/schema/API | Same id can exist safely in multiple collections | Unit + integration + API E2E |
| P0 | Data Plane | H2 query ignores filters/sorts/search | `H2SovereignEntityStore.java` | Implement full QuerySpec or reject unsupported options | Query/count match requested filters/sorts | H2 integration tests |
| P0 | Event Plane | Missing canonical event envelope | `DataCloudClient.java`, `EventLogStore.java` | Add typed envelope and validation | Missing required fields rejected | Contract + unit + integration |
| P0 | Event Plane | H2 idempotency and batch atomicity missing | `H2SovereignEventLogStore.java` | Enforce unique idempotency and transactional appendBatch | Duplicate idempotency stable; rollback works | H2 integration tests |
| P0 | Governance/Ops | Audit can be skipped | `DataCloudHttpServer.java` | Require audit writer for sensitive production routes | Sensitive mutation fails without audit | Startup + API tests |
| P0/P1 | Intelligence | AI routes return heuristic/stub 200 | `DataCloudHttpLauncherBootstrap.java`, `AiAssistHandler.java` | Gate/disable in production unless real provider configured | No fake AI success in production | API + UI gate tests |
| P1 | Runtime Truth | UI uses `/capabilities` | `capabilities.service.ts` | Migrate to `/surfaces` | `/capabilities` not used by UI | UI/API tests |
| P1 | UI | Runtime gate shows children while loading | `RuntimeCapabilityRouteGate.tsx` | Render safe loading/disabled placeholder | Disabled surfaces never flash | UI tests |
| P1 | Security | API key principal not tenant-bound | `DataCloudHttpLauncherBootstrap.java` | Bind keys to tenant/scope or require JWT tenant claim | Cross-tenant access denied | Security tests |
| P1 | Sovereignty | Hardcoded DNS `8.8.8.8` | `DataCloudHttpLauncherBootstrap.java` | Make DNS configurable and forbidden in sovereign profile | Sovereign never uses public DNS | Unit + startup tests |
| P1 | Settings | In-memory settings default can be production-reachable | `DataCloudHttpServer.java` | Require persistent settings in non-embedded profiles | Production refuses in-memory settings | Startup tests |
| P2 | Frontend | Cache invalidation narrow | `api/client.ts` | Domain-aware invalidation | UI refreshes after writes | UI/service tests |
| P2 | Docs | Port/profile/default docs drift | README, Launcher, Validator | Align default ports and local storage claims | Docs match code | Documentation check |

---

## 20. Final Production Readiness Gate

| Gate | Decision |
|---|---|
| Ready for production | **No** |
| Ready for internal demo | **Yes, local/preview only** |
| Ready behind feature flag | **Partially; critical Data/Event correctness still must be fixed** |
| Minimum before release | Fix entity identity, H2 query, event envelope/idempotency/atomicity, production fail-closed, AI fallback gating, Runtime Truth migration. |
| Shared-library refactors required | Yes: EntityRef, event envelope, QuerySpec, Runtime Truth types, event store canonicalization. |
| Contract migrations required | Yes: `/surfaces` contract, event envelope, entity identity, action-plane/aep equivalence, generated SDK parity. |

---

## 21. Final Checklist

- [ ] Data Cloud product boundary verified with dependency graph.
- [ ] No Data/Event/Context/Governance/Intelligence imports from Action implementation.
- [ ] OpenAPI contracts validate.
- [ ] Runtime route inventory matches OpenAPI.
- [ ] UI uses generated/adapted clients, not duplicated hand-written contract types where avoidable.
- [ ] `/surfaces` replaces `/capabilities` in UI.
- [ ] Entity identity includes tenant/workspace/collection/id everywhere.
- [ ] Event envelope includes required governance/provenance/trace fields.
- [ ] H2 and in-memory stores pass conformance suite.
- [ ] Production profile fails closed for auth, policy, audit, durable stores, observability.
- [ ] No production AI/static heuristic fake success.
- [ ] Audit evidence exists for sensitive mutations.
- [ ] No in-memory settings/idempotency in production.
- [ ] UI optional surfaces do not render before runtime truth.
- [ ] Tests cover success, failure, permission, tenant isolation, concurrency, and degraded paths.
- [ ] Docs, route truth matrix, contracts, SDK, and UI are internally consistent.
