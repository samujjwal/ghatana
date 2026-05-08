# End-to-End Data Cloud TODO List

**Intended repository path:** `products/data-cloud/docs/audits/end-to-end-data-cloud-todo-list.md`  
**Source audit report:** `products/data-cloud/docs/audits/end-to-end-data-cloud-correctness-shared-libraries-audit.md`  
**Repository:** `samujjwal/ghatana`  
**Target commit/ref:** `3ac6b0226e48e784e6c4c44e3c426adcb833de7e`

---

## P0 — Must Fix Before Production

- [x] `DC-P0-001` — Make entity identity collection-scoped everywhere.
  - Area: Data Plane / Entity Store / API
  - File(s): `products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/spi/EntityStore.java`, `products/data-cloud/delivery/runtime-composition/src/main/java/com/ghatana/datacloud/DataCloud.java`, `products/data-cloud/delivery/runtime-composition/src/main/java/com/ghatana/datacloud/storage/H2SovereignEntityStore.java`, `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityCrudHandler.java`
  - Required fix: Introduce canonical `EntityRef` including tenant, optional workspace, collection, and entity id. Update SPI methods, client methods, H2 schema primary key, in-memory keys, delete/find/exists/batch APIs, and API responses.
  - Acceptance criteria: Same entity id can exist in multiple collections under the same tenant without overwrite, wrong read, wrong delete, or wrong update.
  - Tests required: Unit, H2 integration, API E2E, tenant/workspace isolation tests.

- [x] `DC-P0-002` — Fix H2 sovereign entity query correctness.
  - Area: Data Plane / Query
  - File(s): `products/data-cloud/delivery/runtime-composition/src/main/java/com/ghatana/datacloud/storage/H2SovereignEntityStore.java`
  - Required fix: Implement filters, sorts, search, projections, deterministic pagination, count with filters, and unsupported-option validation.
  - Acceptance criteria: Query result and count match QuerySpec for every supported operator.
  - Tests required: H2 integration tests for eq/ne/gt/gte/lt/lte/like/in/null, sort, pagination, count, invalid query.

- [x] `DC-P0-003` — Define and enforce canonical event envelope.
  - Area: Event Plane / Contracts / SPI
  - File(s): `products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/DataCloudClient.java`, `products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/spi/EventLogStore.java`, `products/data-cloud/contracts/openapi/data-cloud.yaml`
  - Required fix: Add typed fields for eventId, tenantId, subjectType, subjectId, source, schemaVersion, correlationId, causationId, actor, classification, policyContext, timestamp, provenance, and traceContext.
  - Acceptance criteria: Events missing required envelope fields are rejected before persistence.
  - Tests required: Contract tests, unit validation tests, H2/in-memory append tests, API tests.

- [x] `DC-P0-004` — Enforce event idempotency.
  - Area: Event Plane / Storage
  - File(s): `products/data-cloud/delivery/runtime-composition/src/main/java/com/ghatana/datacloud/storage/H2SovereignEventLogStore.java`
  - Required fix: Add unique constraint for `(tenant_id, idempotency_key)` where idempotency key is present and return stable prior result for duplicate keys.
  - Acceptance criteria: Repeating the same idempotency key does not append duplicate events.
  - Tests required: H2 integration tests for duplicate and conflicting idempotency keys.

- [x] `DC-P0-005` — Make `appendBatch` atomic.
  - Area: Event Plane / Storage
  - File(s): `products/data-cloud/delivery/runtime-composition/src/main/java/com/ghatana/datacloud/storage/H2SovereignEventLogStore.java`
  - Required fix: Wrap batch insert in one transaction with rollback on any failed entry.
  - Acceptance criteria: Partial batch writes cannot occur.
  - Tests required: Batch success and rollback tests.

- [x] `DC-P0-006` — Fail closed for missing audit writer in production.
  - Area: Governance / Audit / Production profile
  - File(s): `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java`, `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/bootstrap/DataCloudHttpLauncherBootstrap.java`
  - Required fix: Require audit service for sensitive mutation routes in non-embedded profiles.
  - Acceptance criteria: Production startup or sensitive mutation fails when audit writer is missing.
  - Tests required: Startup validation tests and mutation API tests.

- [x] `DC-P0-007` — Remove production-path fake AI success.
  - Area: Intelligence Plane / AI Assist
  - File(s): `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/bootstrap/DataCloudHttpLauncherBootstrap.java`, `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AiAssistHandler.java`
  - Required fix: In production, disable AI assist routes or return explicit unavailable/runtime-truth response when no real completion service exists. Allow heuristic fallback only in local/preview mode with clear UI labeling.
  - Acceptance criteria: Production cannot return static heuristic AI responses as successful AI output.
  - Tests required: API tests for local, preview, and production profiles; UI runtime truth tests.

- [x] `DC-P0-008` — Add production profile preflight for durable stores, policy, audit, and observability.
  - Area: Operations / Runtime profile
  - File(s): `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/DataCloudConfigValidator.java`, `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/DataCloudLauncher.java`
  - Required fix: Production profile must validate auth, durable entity store, durable event store, policy engine, audit writer, metrics, traces, and required dependencies before accepting traffic.
  - Acceptance criteria: Production startup fails with actionable messages when trust-critical dependencies are absent.
  - Tests required: Config validator tests and launcher startup tests.

---

## P1 — Must Fix Before Release

- [x] `DC-P1-001` — Complete Runtime Truth migration from `/capabilities` to `/surfaces`.
  - Area: Runtime Truth / UI / Contracts
  - File(s): `products/data-cloud/delivery/ui/src/api/capabilities.service.ts`, `products/data-cloud/README.md`, `products/data-cloud/contracts/openapi/data-cloud.yaml`
  - Required fix: Create a `surfaces.service.ts` client, normalize LIVE/DEGRADED/DISABLED/PREVIEW/UNAVAILABLE/MISCONFIGURED, and retire direct UI use of `/capabilities`.
  - Acceptance criteria: UI no longer calls `/capabilities` except through temporary compatibility tests.
  - Tests required: API client tests, UI gate tests, contract tests.

- [x] `DC-P1-002` — Prevent optional surfaces from rendering before runtime truth loads.
  - Area: UI / Runtime gating
  - File(s): `products/data-cloud/delivery/ui/src/components/security/RuntimeCapabilityRouteGate.tsx`
  - Required fix: Replace “render children while loading” with safe skeleton/disabled state for optional surfaces.
  - Acceptance criteria: Disabled/unavailable surfaces never flash visible during loading.
  - Tests required: React component tests.

- [x] `DC-P1-003` — Runtime-gate all optional/contextual surfaces consistently.
  - Area: UI routing
  - File(s): `products/data-cloud/delivery/ui/src/routes.tsx`
  - Required fix: Add runtime truth gates to Events, Plugins, and any optional role-disclosed surfaces that can be disabled/degraded.
  - Acceptance criteria: Route accessibility matches Runtime Truth Registry.
  - Tests required: Route access tests.

- [x] `DC-P1-004` — Fix route fallback component typing.
  - Area: UI / Routing
  - File(s): `products/data-cloud/delivery/ui/src/routes.tsx`
  - Required fix: Do not pass inline function components into `withSuspense` if it expects lazy components. Render `DisabledSurfacePage` directly or create typed lazy wrappers.
  - Acceptance criteria: TypeScript build passes without route fallback type errors.
  - Tests required: UI typecheck/build.

- [x] `DC-P1-005` — Bind API keys to tenants/scopes.
  - Area: Security / Auth
  - File(s): `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/bootstrap/DataCloudHttpLauncherBootstrap.java`
  - Required fix: Replace static `service` tenant principal with configured tenant/scope bindings, or require JWT tenant claims for production.
  - Acceptance criteria: API key cannot access arbitrary `X-Tenant-ID`.
  - Tests required: Authz and cross-tenant API tests.

- [x] `DC-P1-006` — Remove hardcoded public DNS resolver.
  - Area: Sovereignty / Network config
  - File(s): `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/bootstrap/DataCloudHttpLauncherBootstrap.java`
  - Required fix: Make DNS resolver configurable and forbid public DNS in sovereign profile unless explicitly allowed.
  - Acceptance criteria: Sovereign profile never defaults to `8.8.8.8`.
  - Tests required: Unit/startup tests.

- [x] `DC-P1-007` — Require persistent settings storage outside embedded profiles.
  - Area: Settings / Operations
  - File(s): `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java`
  - Required fix: Non-embedded profile must require persistent `SettingsStore`.
  - Acceptance criteria: Production cannot silently use `InMemorySettingsStore`.
  - Tests required: Startup and settings persistence tests.

- [x] `DC-P1-008` — Make idempotency durable for entity writes.
  - Area: Data Plane / Idempotency
  - File(s): `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityCrudHandler.java`
  - Required fix: Replace process-local idempotency map with durable tenant-scoped idempotency store for production.
  - Acceptance criteria: Restart does not lose idempotency guarantees.
  - Tests required: Integration restart/idempotency tests.

- [x] `DC-P1-009` — Implement batch semantic indexing or disable it explicitly.
  - Area: Data Plane / Semantic indexing
  - File(s): `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityCrudHandler.java`
  - Required fix: Remove shortcut that skips semantic indexing for batch saves, or mark semantic indexing unavailable for batch through runtime truth.
  - Acceptance criteria: Batch behavior is consistent with single-save behavior or explicitly unavailable.
  - Tests required: Batch semantic indexing tests.

- [x] `DC-P1-010` — Align port defaults and local storage documentation.
  - Area: Documentation / Launcher config
  - File(s): `products/data-cloud/README.md`, `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/DataCloudLauncher.java`, `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/DataCloudLauncherSettings.java`, `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/DataCloudConfigValidator.java`
  - Required fix: Align default HTTP port and local profile storage claims.
  - Acceptance criteria: Docs, validator, launcher, and README agree.
  - Tests required: Config/docs consistency test.

---

## Product Boundary and Plane Fixes

- [x] `DC-BND-001` — Add dependency-boundary enforcement.
  - Area: Architecture
  - File(s): Gradle/ArchUnit configuration under Data Cloud modules
  - Required fix: Enforce that Data/Event/Context/Governance/Intelligence planes do not import Action implementation internals.
  - Acceptance criteria: Build fails on forbidden dependencies.
  - Tests required: ArchUnit/Gradle dependency tests.

- [x] `DC-BND-002` — Review platform-kernel Data Cloud adapter types.
  - Area: Shared libraries / Kernel boundary
  - File(s): `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/adapter/datacloud/**`
  - Required fix: Keep only generic kernel abstractions in platform; move Data Cloud-specific semantics into `products/data-cloud`.
  - Acceptance criteria: No product-specific Data Cloud behavior is owned by generic kernel modules.
  - Tests required: Dependency and API compatibility tests.

---

## Contract/API/SDK Fixes

- [x] `DC-CON-001` — Validate OpenAPI route parity.
  - Area: Contract Plane
  - File(s): `products/data-cloud/contracts/openapi/data-cloud.yaml`, `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java`
  - Required fix: Generate route inventory and compare against OpenAPI.
  - Acceptance criteria: Every runtime route has matching OpenAPI and every OpenAPI route is implemented.
  - Tests required: CI contract parity test.

- [x] `DC-CON-002` — Validate `action-plane.yaml` and `aep.yaml` equivalence.
  - Area: Contract Plane
  - File(s): `products/data-cloud/contracts/openapi/action-plane.yaml`, `products/data-cloud/contracts/openapi/aep.yaml`
  - Required fix: Add equivalence check until `aep.yaml` is retired.
  - Acceptance criteria: CI fails on drift.
  - Tests required: Contract diff test.

- [x] `DC-CON-003` — Generate UI/API client types from canonical contracts.
  - Area: SDK / UI
  - File(s): `products/data-cloud/delivery/sdk/**`, `products/data-cloud/delivery/ui/src/**`
  - Required fix: Remove duplicated hand-maintained DTOs where generated types are available.
  - Acceptance criteria: UI uses generated/adapted clients for canonical APIs.
  - Tests required: SDK generation and UI typecheck.

---

## Backend/Domain/Data/Event/Storage Fixes

- [x] `DC-BE-001` — Make `deleteBatch` return actual affected row count.
  - Area: Data Plane / H2 store
  - File(s): `products/data-cloud/delivery/runtime-composition/src/main/java/com/ghatana/datacloud/storage/H2SovereignEntityStore.java`
  - Required fix: Use `executeUpdate()` result instead of returning requested ids size.
  - Acceptance criteria: Missing ids are reported accurately.
  - Tests required: Batch delete tests.

- [x] `DC-BE-002` — Add workspace propagation across API/storage paths.
  - Area: Multi-tenancy
  - File(s): `TenantContext.java`, HTTP tenant extraction, stores
  - Required fix: Include workspace where applicable in context, keys, queries, audit, and API.
  - Acceptance criteria: Workspace isolation works inside a tenant.
  - Tests required: Workspace isolation tests.

- [x] `DC-BE-003` — Define offset semantics as per-tenant or global and enforce consistently.
  - Area: Event Plane
  - File(s): `H2SovereignEventLogStore.java`, `EventLogStore.java`
  - Required fix: Either make offsets per-tenant or document global offsets and prevent metadata leakage.
  - Acceptance criteria: Read/tail/latest semantics are deterministic and tested.
  - Tests required: Offset/read/tail tests.

- [x] `DC-BE-004` — Replace silent close/error swallowing.
  - Area: Runtime lifecycle
  - File(s): `DataCloud.java`
  - Required fix: Log close failures with structured context or surface through lifecycle diagnostics.
  - Acceptance criteria: Shutdown failures are diagnosable.
  - Tests required: Lifecycle error tests.

---

## UI/UX and Frontend Fixes

- [x] `DC-UI-001` — Add safe unavailable states for all gated surfaces.
  - Area: UI
  - File(s): `DisabledSurfacePage`, runtime gates, routes
  - Required fix: Standardize empty/loading/degraded/unavailable/preview states.
  - Acceptance criteria: Every optional surface has clear runtime-truth UI.
  - Tests required: Component tests.

- [x] `DC-UI-002` — Make API cache invalidation domain-aware.
  - Area: UI data flow
  - File(s): `products/data-cloud/delivery/ui/src/lib/api/client.ts`
  - Required fix: Invalidate list/detail/search/cache families after writes.
  - Acceptance criteria: Entity create/update/delete refreshes affected views.
  - Tests required: API client cache tests.

- [x] `DC-UI-003` — Add backend-denied permission UX.
  - Area: UI / Security
  - File(s): Route error boundaries, API error handlers
  - Required fix: Distinguish shell-role denial from backend authz denial.
  - Acceptance criteria: 401/403 show actionable recovery and correlation id.
  - Tests required: UI error flow tests.

---

## Shared Library, Abstraction, DRY, and Source-of-Truth Fixes

- [x] `DC-DRY-001` — Consolidate query/filter/status models.
  - Area: Shared SPI / Contracts / UI
  - File(s): `DataCloudClient.java`, `EntityStore.java`, OpenAPI, UI schemas
  - Required fix: Use one canonical QuerySpec/filter/operator model and generated/adapted types.
  - Acceptance criteria: No divergent operator/status definitions.
  - Tests required: Contract/type drift tests.

- [x] `DC-DRY-002` — Canonicalize event-store abstraction.
  - Area: Event Plane / Shared platform
  - File(s): `DataCloudClient.java`, `EventLogStore.java`, event adapters
  - Required fix: Decide canonical Data Cloud vs platform event-store SPI and remove legacy duplication.
  - Acceptance criteria: No pending migration note or dual incompatible event abstractions.
  - Tests required: API compatibility and adapter tests.

- [x] `DC-DRY-003` — Create provider conformance suite for all stores.
  - Area: Shared SPI testing
  - File(s): `ProviderConformanceSuite.java` and store tests
  - Required fix: Cover identity, query, event append, idempotency, tail, delete, batch, tenant/workspace isolation.
  - Acceptance criteria: In-memory, H2, and future providers pass the same suite.
  - Tests required: Provider conformance tests.

---

## Security, Privacy, Governance, and Tenant-Isolation Fixes

- [x] `DC-SEC-001` — Add server-side tenant authorization tests independent of shell role.
  - Area: Security
  - File(s): Security filter tests, HTTP API tests
  - Required fix: Prove UI shell role changes cannot grant backend access.
  - Acceptance criteria: Backend denies unauthorized tenant/role regardless frontend state.
  - Tests required: Security integration tests.

- [x] `DC-SEC-002` — Add export/redaction fail-closed checks.
  - Area: Privacy / Governance
  - File(s): Export/privacy handlers
  - Required fix: Sensitive exports require classification/redaction policy.
  - Acceptance criteria: Missing redaction policy blocks export.
  - Tests required: Privacy/export tests.

- [x] `DC-SEC-003` — Add audit evidence to all sensitive mutations.
  - Area: Governance / Audit
  - File(s): Entity, event, governance, pipeline, settings, plugin handlers
  - Required fix: Emit durable audit event with tenant, actor, trace id, action, result.
  - Acceptance criteria: Audit trail covers all critical mutations.
  - Tests required: Audit integration tests.

---

## Observability, Operations, and Runtime Truth Fixes

- [x] `DC-OPS-001` — Make trace export runtime-truth visible.
  - Area: Observability
  - File(s): `DataCloudHttpLauncherBootstrap.java`, Runtime Truth Registry
  - Required fix: Mark traces degraded/unavailable when exporter missing; optionally fail in production SLO mode.
  - Acceptance criteria: Operators can see trace export state.
  - Tests required: Runtime truth tests.

- [x] `DC-OPS-002` — Add route-level metrics coverage.
  - Area: Metrics
  - File(s): HTTP handlers
  - Required fix: Emit counters/latency/error metrics for critical routes.
  - Acceptance criteria: Dashboards can be built from emitted metrics.
  - Tests required: Metrics assertion tests.

- [x] `DC-OPS-003` — Verify and update runbook against real profiles.
  - Area: Operations docs
  - File(s): `products/data-cloud/docs/operations/RUNBOOK.md`
  - Required fix: Ensure startup, health, degraded, backup/restore, auth, trace, audit, and failure procedures match code.
  - Acceptance criteria: Runbook commands work on local checkout.
  - Tests required: Smoke/runbook validation script.

---

## Performance and Scalability Fixes

- [x] `DC-PERF-001` — Add indexes for real H2 query patterns.
  - Area: Storage
  - File(s): `H2SovereignEntityStore.java`
  - Required fix: Add indexes for tenant+collection+fields used by filters/sorts where feasible.
  - Acceptance criteria: Query plans are acceptable for realistic tenant data sizes.
  - Tests required: Query benchmark tests.

- [x] `DC-PERF-002` — Bound local in-memory stores.
  - Area: Local/runtime safety
  - File(s): `DataCloud.java`
  - Required fix: Add configurable local limits and warnings for entity/event counts.
  - Acceptance criteria: Local mode cannot accidentally consume unbounded memory silently.
  - Tests required: Limit tests.

- [x] `DC-PERF-003` — Rework H2 event tail polling.
  - Area: Event streaming
  - File(s): `H2SovereignEventLogStore.java`
  - Required fix: Add bounded polling configuration, backoff, error reporting, or a better notification mechanism.
  - Acceptance criteria: Many subscribers do not create uncontrolled scheduler load.
  - Tests required: Load/concurrency tests.

---

## Test Additions and Fixes

- [x] `DC-TEST-001` — Add full entity store conformance tests.
- [x] `DC-TEST-002` — Add full event store conformance tests.
- [x] `DC-TEST-003` — Add OpenAPI route parity tests.
- [x] `DC-TEST-004` — Add generated SDK parity tests.
- [x] `DC-TEST-005` — Add production startup fail-closed tests.
- [x] `DC-TEST-006` — Add AI fallback/gating profile tests.
- [x] `DC-TEST-007` — Add Runtime Truth UI gate tests.
- [x] `DC-TEST-008` — Add tenant/workspace/authz tests.
- [x] `DC-TEST-009` — Add audit evidence tests.
- [x] `DC-TEST-010` — Add UI typecheck/build tests for all routes.
- [x] `DC-TEST-011` — Add cache invalidation tests.
- [x] `DC-TEST-012` — Add performance/load tests for query, event append, and tail.

---

## Documentation and Runbook Fixes

- [x] `DC-DOC-001` — Update REST API docs after `/surfaces` migration.
- [x] `DC-DOC-002` — Update Data Cloud README with accurate local/sovereign storage behavior.
- [x] `DC-DOC-003` — Add architecture decision for per-tenant vs global event offsets.
- [x] `DC-DOC-004` — Add shared-library boundary guide for Data Cloud SPI vs platform/kernel.
- [x] `DC-DOC-005` — Add production profile checklist for auth, audit, policy, durable stores, trace, metrics.
- [x] `DC-DOC-006` — Add AI fallback policy explaining local preview vs production behavior.
- [x] `DC-DOC-007` — Update route truth matrix after route/runtime-truth alignment.

---

## P2 — Hardening

- [x] `DC-P2-001` — Add structured logs for all critical actions.
- [x] `DC-P2-002` — Add user-facing correlation id to all error states.
- [x] `DC-P2-003` — Add degraded-mode banners for unavailable optional systems.
- [x] `DC-P2-004` — Add local developer warnings for in-memory/non-durable profile.
- [x] `DC-P2-005` — Add schema/projection support or explicit unsupported-option errors in all providers.
- [x] `DC-P2-006` — Add migration scripts for H2 schema changes.
- [x] `DC-P2-007` — Review accessibility and keyboard navigation for every route.
- [x] `DC-P2-008` — Add release checklist enforcement in CI.

---

## P3 — Future Enhancements

- [x] `DC-P3-001` — Add first-class Data Cloud CLI for runtime truth, health, route inventory, contract drift, and provider conformance.
- [x] `DC-P3-002` — Add visual Runtime Truth dashboard with plane/surface/dependency drilldown.
- [x] `DC-P3-003` — Add data-quality/trust scoring as a canonical Data Plane contract.
- [x] `DC-P3-004` — Add policy simulation mode for governance changes.
- [x] `DC-P3-005` — Add tenant-level cost and resource governance views.
