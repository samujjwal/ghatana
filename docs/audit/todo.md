Fresh current-head review target: `main` at commit `1577a8559f3ae6a973452465a2cedf8d52c0a0ef` (`refactor 37`). 
I treated the prior audit file as non-authoritative and reviewed the current vision/architecture/code state directly. Also, `main` is one commit ahead of `b26...`, but the diff contains no Data Cloud file changes, so the Data Cloud head state is the same Data Cloud code/doc state as `b26...`.

Canonical unified remaining-items tracker: `docs/audit/unified-remaining-tracker.md`.

Data Cloud’s intended product shape is clear: it is an AI-native operational data fabric organized by planes, with Runtime Truth as the live/degraded/unavailable state model, `/api/v1/surfaces` as the target Runtime Truth endpoint, `/api/v1/capabilities` only as compatibility, and strict dependency boundaries between planes.   Data Cloud also owns entity/event/analytics/reporting/governance/memory services, feature store/model metadata, plugin lifecycle, and public contracts, while broader AEP orchestration must remain outside the Data Cloud boundary. 

# Single Detailed TODO List — Data Cloud Current Head

## Progress Updates (2026-05-08)

- Status legend:
    - DONE: implemented and validated with targeted tests
    - PARTIAL: implementation started, additional scope/tests still required
    - TODO: not started in this pass

- P0.1 (per-tenant offsets): DONE
    - Implemented tenant-scoped offset allocation and storage migration in `H2SovereignEventLogStore`.
    - Added regression test coverage in `H2SovereignEventLogStoreBehaviorTest`.

- P0.2 (idempotency race-safety): DONE
    - Added tenant-scoped unique idempotency constraint and conflict-safe insert path.
    - Added regression test ensuring duplicate idempotency key returns existing offset and does not duplicate rows.

- P0.3 (canonical envelope enforcement): DONE (runtime composition path)
    - Added non-local profile envelope validation in `DataCloud.appendEvent`.
    - Added tests in `DataCloudEventEnvelopeValidationTest`.

- P0.4 (production AI fail-closed): DONE (launcher AI routes)
    - Updated `AiAssistHandler` so production mode returns `503` when provider calls fail, instead of returning heuristic success.
    - Kept heuristic fallback behavior for local mode.
    - Added production integration coverage in `DataCloudHttpServerAiAssistTest` for both no-completion and provider-error scenarios.

- P0.5 (production preflight hardening): DONE (current gate expansion)
    - Expanded `DataCloudHttpServer.validateProductionDependencies(...)` to require a wired idempotency store in production mode.
    - Added coverage in `DataCloudHttpServerProductionDependencyTest` for missing idempotency-store startup failure.

- P0.7 (appendBatch atomicity across offset/idempotency): DONE (H2 path)
    - Single and batch append now run transactionally with tenant offset state update + insert atomicity.
    - Validated via existing `H2SovereignEventLogStoreContractTest` and new behavior tests.

- P1.16 (AI fallback policy consistency): PARTIAL
    - Runtime behavior now distinguishes production fail-closed vs local heuristic fallback for core AI assist flows.
    - Runtime Truth UI migration advanced: first-party delivery UI imports/mocks now resolve through canonical `surfaces.service` instead of `capabilities.service` paths.
    - Verified no direct `api/capabilities.service` imports remain under `products/data-cloud/delivery/ui/src/**`.
    - Updated SQL workspace runtime-truth/degradation and AI-assist test expectations to match current fail-closed/degraded UX semantics and canonical capability snapshot shape.
    - Re-validated targeted suites: `SqlWorkspacePage.test.tsx`, `InsightsPage.test.tsx`, runtime-route gate generator, and capability/surface service tests (29/29 passing).
    - Aligned remaining workflow advisory copy with fail-closed semantics: workflow hints now describe advisory-only results, and AI operations service docs now describe advisory routes as fail-closed in the current launcher profile.
    - Validation evidence for advisory-copy delta: `WorkflowsPage.test.tsx` (`5/5`).
    - Remaining work: align secondary AI advisory route messaging/semantics with the same production fail-closed policy language across docs and operator UX.

- P1.18 (durability requirements in non-local profiles): PARTIAL
    - Production startup now blocks when idempotency store is missing, in addition to existing audit/policy/settings guards.
    - Expanded launcher preflight matrix: production now also fails closed on missing auth wiring, missing durable event-log store wiring, missing explicit metrics collector configuration, and missing trace export service.
    - Added production fail-closed checks for non-durable core backing: launcher now rejects production startup when DataCloudClient entity/event stores resolve to in-memory implementations.
    - Runtime capability `_meta` now includes `runtimePosture` fields for production-like profile readiness (auth/settings/idempotency/entity/event/audit/policy/metrics/trace posture), improving bootstrap/runtime-truth parity.
    - Added/updated coverage in `DataCloudHttpServerProductionDependencyTest` and `DataCloudHttpServerAiAssistTest` to validate the expanded preflight in production-mode startup paths.
    - Canonical launcher runtime-truth route parity added: `/api/v1/surfaces` now serves the same snapshot contract as `/api/v1/capabilities` (single supplier path) so runtime posture metadata remains identical across both endpoints.
    - Added regression coverage in `CapabilityRegistryHandlerTest` and `DataCloudHttpServerCapabilityTest` to keep `/surfaces` and `/capabilities` behaviorally equivalent.
    - Added canonical schema parity route: `/api/v1/surfaces/schema` now mirrors `/api/v1/capabilities/schema` from the same handler path.
    - Updated runtime guidance strings in launcher handlers to point operators to canonical Runtime Truth endpoint `/api/v1/surfaces`.
    - Remaining work: complete end-to-end profile posture parity checks across docs/UI and migrate remaining first-party capability callers to canonical surface-first naming.

- P1.12 (Runtime Truth migration and compatibility retirement): PARTIAL
    - Canonical backend Runtime Truth endpoints now include both `/api/v1/surfaces` and `/api/v1/surfaces/schema`, with compatibility endpoints still served.
    - Canonical frontend implementation is now centralized in `surfaces.service` (surface-first), with `capabilities.service` reduced to a compatibility shim that re-exports mapped legacy APIs.
    - First-party delivery UI source/tests/hooks/pages were migrated from `api/capabilities.service` imports to `api/surfaces.service` imports.
    - Added regression coverage for surfaces-schema handler path and capability-client fallback behavior.
    - Validated migration with `pnpm run type-check` in `products/data-cloud/delivery/ui` (passing).
    - Focused vitest suites for runtime truth service/routes/pages pass after expectation realignment (`capabilities.service`, runtime route gates, `InsightsPage`, `SqlWorkspacePage`).
    - Additional API-layer regression sweep executed during remaining-task run: `src/__tests__/api/**` now passes (25 files, 233 tests), including updated governance lifecycle assertions aligned to live policy CRUD endpoints.
    - Additional routing/navigation/adapter validation completed: `src/__tests__/routing/**`, `src/__tests__/accessibility/**`, and `src/__tests__/components|layouts|hooks|lib/**` are passing in current chunked sweep after route-alias inventory sync and route-gate capability alias alignment.
    - Runtime route gates were aligned with canonical capability names for event and plugin surfaces (while preserving compatibility aliases), and corresponding route metadata regression fixtures were updated.
    - Feature-gate regression expectations were aligned with current policy where Data Fabric is opt-in (explicit feature flag required even in non-strict profiles).
    - Broad page-suite sweep completed across all `src/__tests__/pages/*.test.tsx` files via chunked execution; drift fixes applied for current boundary-mode UX (Data Fabric/WorkflowList redirect semantics), updated Entity Browser API-call mocking, and Trust Center access-review boundary copy.
    - Current page-level validation status in this pass: all targeted batches passing (`57 + 129 + 51` tests across 29 page test files).
    - Centralized MSW Runtime Truth coverage added in shared handlers for `/api/v1/surfaces` and `/api/v1/capabilities` using schema-compatible envelope shape to eliminate unhandled-request warning noise across page tests.
    - Post-fix verification run passed for warning-heavy suites: `AlertsPage`, `GovernancePage`, `TrustCenter`, and `AnalyticsPage` (`29/29` tests passing) without Runtime Truth unhandled-request warnings.
    - Runtime Truth dashboard copy was shifted to surface-first wording for user-facing status/loading/error messaging while keeping compatibility symbols intact in the service layer.
    - Route inventory regression fixture updated for current RuntimeCapabilityRouteGate footprint (events/plugins aliases), and `src/__tests__/routes/routeInventory.test.ts` now passes (`95/95`).
    - Schema loader retirement sequencing started in first-party utility path: `lib/capabilities.ts` now fetches canonical `/api/v1/surfaces/schema` first and falls back to `/api/v1/capabilities/schema` only when needed, with explicit compatibility warning when fallback is used.
    - Added regression coverage for schema endpoint sequencing in `src/__tests__/lib/capabilities.schema-loader.test.ts` (canonical-first + compatibility-fallback paths).
    - Runtime-truth compatibility route sequencing was centralized in mock adapters: deprecation mapping now explicitly tracks `/api/v1/capabilities -> /api/v1/surfaces` and `/api/v1/capabilities/schema -> /api/v1/surfaces/schema`, with compatibility warning headers emitted by MSW and Playwright mocks.
    - Added mock-governance validation for runtime-truth mapping in `src/__tests__/mocks/deprecatedRoutes.test.ts` and `src/__tests__/mocks/openApiDrivenMocks.test.ts`.
    - Continued surface-first naming cleanup in user-facing copy: Insights loading states and SQL Workspace runtime-truth helper text now reference runtime surfaces/surface registry language.
    - Insights overview truth panel label/description now use surface-first terminology (`Runtime Surface Truth`) to keep operator copy aligned with canonical endpoint naming.
    - Additional runtime wording cleanup completed in boundary/gating surfaces: runtime-boundary AI-assist detail strings, `RuntimeCapabilityErrorBoundary` status text, and `CapabilityGated` loading copy now use runtime surface terminology.
    - Updated impacted test expectations/descriptions to match surface-first wording (`InsightsPage`, `CapabilityGated`, `useCapabilityGate`).
    - Validation evidence for this delta: `CapabilityGated.test.tsx` + `useCapabilityGate.test.ts` + `InsightsPage.test.tsx` (`31/31`).
    - Compatibility shim retirement advanced: removed `api/index.ts` re-export of `capabilities.service` and deleted `src/api/capabilities.service.ts` after confirming no first-party source imports remained.
    - Kept compatibility behavior validated at canonical service layer (`surfaces.service`) via existing compatibility-focused API tests.
    - Validation evidence for shim retirement delta: `capabilities.service.test.ts` + `runtimeTruth.test.ts` (`10/10`).
    - Additional naming-debt cleanup completed for runtime-truth terminology: updated remaining runtime-boundary/component/hook wording and stale test labels from capability-registry phrasing to runtime-surface-registry phrasing.
    - Validation evidence for naming cleanup delta: `CapabilityGated.test.tsx` + `SqlWorkspacePage.test.tsx` + `ContractBacked.test.tsx` (`75/75`).
    - Continued surface-first terminology cleanup in the Data Cloud UI: `InsightsPage.tsx` now uses surface-boundary / surface-request operator copy, `RouteCapabilityRegistry.ts` comments now say Route Surface Registry, and the runtime-truth metadata/E2E test titles were updated to surface-first wording.
    - Validation evidence for this delta: `InsightsPage.test.tsx` (`9/9`) and `routeCapabilityMetadata.test.ts` (`10/10`).
    - Continued the same copy cleanup into shared runtime-truth UI components: `CapabilityTruthPanel.tsx` now counts surfaces, `RuntimeCapabilityRouteGate.tsx` docs say runtime surface gate, and `RuntimeCapabilityErrorBoundary.tsx` docs say runtime surface truth / runtime-surface-aware error boundary.
    - SDK/OpenAPI compatibility retirement sequencing advanced: canonical Runtime Truth endpoints `/api/v1/surfaces` and `/api/v1/surfaces/schema` are now present in Data Cloud OpenAPI alongside compatibility `/api/v1/capabilities*` aliases (explicitly marked as compatibility aliases).
    - Contract coverage was tightened to require both canonical and compatibility runtime-truth paths in OpenAPI-backed assertions (`ContractBacked.test.tsx` and `openApiDrivenMocks.test.ts`).
    - Validation evidence for OpenAPI sequencing delta: `ContractBacked.test.tsx` + `openApiDrivenMocks.test.ts` (`57/57`).
    - Validation evidence for this pass: `capabilities.schema-loader.test.ts` + `InsightsPage.test.tsx` (`11/11`), `SqlWorkspacePage.test.tsx` (`15/15`), `deprecatedRoutes.test.ts` (`3/3`), `openApiDrivenMocks.test.ts` (`4/4`).
    - Remaining work: execute post-migration removal phase for `/api/v1/capabilities*` compatibility aliases (once consumers are fully cut over), and finish low-priority residual surface-first wording cleanup in non-critical labels/comments.

- P1.21 (`Event.of(...)` bypass): COMPLETE
    - Marked `DataCloudClient.Event.of(...)` as deprecated and enforced non-local append validation.
    - Completed production-path migration of Data Cloud call sites to explicit source-populated builders across launcher and action-plane server modules (`EventHandler`, `AlertingHandler`, `EntityCrudHandler`, `AiAssistHandler`, `DataProductHandler`, `AnalyticsController`, `AepHttpServer`).
    - Verified no remaining production `DataCloudClient.Event.of(...)` usages under `products/data-cloud/**/src/main/**`.
    - Migrated residual Data Cloud test call sites to builder usage across runtime-composition, launcher, and shared-spi test modules.
    - Verification sweep: no remaining `DataCloudClient.Event.of(...)` call sites under `products/data-cloud/**`.
    - Validated compilation with `:products:data-cloud:delivery:launcher:compileJava` and `:products:data-cloud:planes:action:server:compileJava`.
    - Validated focused test coverage for migrated runtime-composition and launcher suites (passing); note `:products:data-cloud:planes:shared-spi:test` still reports pre-existing unrelated `DataCloudClientValueTypesTest` filter-operator assertion failures.
    - Added static enforcement guardrail `checkDataCloudNoDeprecatedEventFactory` in root `build.gradle.kts`, wired into standard `check`, to fail on any future `DataCloudClient.Event.of(...)` usage under `products/data-cloud/**/src/main|src/test/**`.
    - Validated guardrail task execution with `./gradlew checkDataCloudNoDeprecatedEventFactory`.

- P1.22 (`queryEvents` store-level filter pushdown): PARTIAL
    - Added single-type pushdown path to `readByType` in `DataCloud.queryEvents`.
    - Extended `DataCloudClient.EventQuery` with `fromOffset` semantics (backward-compatible constructor preserved), and wired `DataCloud.queryEvents` to push `fromOffset` into store-level `read`/`readByType` calls.
    - Updated launcher `EventHandler` to pass offset+limit into `EventQuery` (instead of skipping in-memory after fetch), and to prefer persisted `_x_dc_offset` headers for response offsets when available.
    - Added multi-type + time-range pushdown path in `DataCloud.queryEvents`: when multiple event types are provided with a time window, the client now queries each type via store-level `readByType(...)`, then merges/sorts/limits in memory.
    - Added regression tests in `DataCloudClientTest` for type-filtered and all-event offset queries.
    - Added regression test `should apply multi-type time-range query with store-level type pushdown` in `DataCloudClientTest`.
    - Validated launcher compatibility routes via targeted tests (`DataCloudHttpServerEventTest`, `EventAppendTest`, `EventHandlerTenantEnforcementTest`) after offset pushdown wiring.
    - Remaining work: introduce native store SPI support for multi-type/time-range querying to avoid N-per-type reads and in-memory merge in high-cardinality scenarios.

- P1.23 (`TailRequest.fromLatest` semantics): PARTIAL
    - Implemented latest-offset snapshot resolution in H2 tail subscription initialization.
    - Fixed H2 polling-tail loop to execute deterministic synchronous reads in its scheduler thread (eliminates missed callbacks from async completion context mismatch).
    - Added deterministic regression test `tail from latest receives only events appended after subscription` in `H2SovereignEventLogStoreBehaviorTest`.
    - Added provider parity behavior in `InMemoryEventLogStoreProvider`: tail subscriptions now receive future appends and `Offset.of(-1)` starts from latest without replay; cancellation removes listeners.
    - Added provider regression test `tailFromLatestReceivesOnlyNewEvents` in `InMemoryEventLogStoreProviderTest`.
    - Implemented Kafka provider parity: `KafkaEventLogStore.tail(...)` now resolves `Offset.latest()` (`-1`) to end-of-log before polling so subscription receives only post-subscription events.
    - Added Kafka conformance test coverage `tail from latest receives only events appended after subscription` in `KafkaEventLogStoreConformanceIT`.
    - Implemented warm-tier provider parity: `WarmTierEventLogStore.tail(...)` now resolves `Offset.latest()` (`-1`) to latest+1 at subscription start, preventing historical replay.
    - Added warm-tier integration test coverage `from latest delivers only events appended after subscription` in `WarmTierEventLogStoreTest`.
    - Remaining work: execute Kafka/Warm-tier parity under live container-backed harness in CI/local Docker-enabled environment (current local run skipped due `@Testcontainers(disabledWithoutDocker = true)`).

- P1.24 (H2 tail polling configurability/observability): PARTIAL
        - Added explicit polling controls to `H2SovereignEventLogStore` via `TailPollingConfig` (`pollIntervalMs`, `maxSubscribers`, `maxBatchSize`, `maxBackoffMs`) with bounded defaults.
        - Wired sovereign factory construction to pass tail config from `DataCloudConfig.customConfig` keys:
            `sovereign.tail.pollIntervalMs`, `sovereign.tail.maxSubscribers`, `sovereign.tail.maxBatchSize`, `sovereign.tail.maxBackoffMs`.
        - Added tail runtime telemetry in `H2SovereignEventLogStore` (`activeSubscribers`, `totalPolls`, `pollErrors`, `lastPollDurationMs`) and surfaced as `tailRuntimeSnapshot()`.
        - Added non-H2 parity telemetry in `InMemoryEventLogStoreProvider` via `tailRuntimeSnapshot()` (`activeSubscribers`, `totalSubscriptions`, `totalNotifications`, mode/store metadata).
        - Added Kafka non-H2 telemetry snapshot via `KafkaEventLogStore.tailRuntimeSnapshot()` (`activeSubscribers`, `totalSubscriptions`, `totalPolls`, `pollErrors`, `eventsDispatched`).
        - Added warm-tier non-H2 telemetry snapshot via `WarmTierEventLogStore.tailRuntimeSnapshot()` (`activeSubscribers`, `totalSubscriptions`, `totalPolls`, `pollErrors`, `eventsDispatched`, poll mode/batch metadata).
        - Added subscriber-cap enforcement in tail subscriptions (`maxSubscribers`) with fail-fast rejection.
        - Exposed event-tail posture under Runtime Truth `_meta.runtimePosture.eventTail` in launcher capability snapshots (reflection-based to avoid hard coupling).
        - Added/updated regression tests:
            - `H2SovereignEventLogStoreBehaviorTest` for tail runtime snapshot config + active subscriber counters.
            - `H2SovereignEventLogStoreBehaviorTest` for max-subscriber limit rejection behavior.
            - `DataCloudFactoryTest` for sovereign tail config propagation from custom config.
            - `DataCloudHttpServerCapabilityTest` for Runtime Truth event-tail metadata presence.
            - `InMemoryEventLogStoreProviderTest` for in-memory tail runtime snapshot subscriber/notification telemetry.
            - `KafkaEventLogStoreConformanceIT` for tail runtime snapshot presence/active-subscriber metrics (execution currently gated by Docker availability).
            - `WarmTierEventLogStoreTest` for tail runtime snapshot telemetry shape and active-subscriber visibility (execution currently gated by Docker/Testcontainers availability).
        - Remaining work: extend equivalent telemetry exposure to additional non-H2 providers (e.g., Kafka/warm-tier) and add load-oriented assertions for subscriber-limit behavior.

    - P1.8 (collection-scoped `EntityStore` methods fail-fast): PARTIAL
        - Updated SPI defaults so `findByRef`, `deleteByRef`, and `existsByRef` fail fast with `UnsupportedOperationException` instead of silently delegating to unsafe ID-only methods.
        - Implemented collection-scoped overrides in `PostgresEntityStore` (`findByRef`, `deleteByRef`, `existsByRef`) to remain compliant with the stricter SPI contract.
        - Verified legacy in-memory integration test stubs remain compatible (they now fail fast for collection-scoped calls unless explicitly overridden).
        - Added dedicated regression test `EntityStoreCollectionScopedDefaultsTest` to enforce fail-fast default behavior.
        - Validated targeted modules with `:products:data-cloud:planes:shared-spi:test --tests com.ghatana.datacloud.spi.EntityStoreCollectionScopedDefaultsTest` and `:products:data-cloud:extensions:plugins:compileJava`.
        - Migrated action event-bridge adapter usage to collection-scoped references in `EventCloudAgentStore` (`findByRef`, `deleteByRef`, `existsByRef`) and updated `EventCloudAgentStoreTest` accordingly.
        - Updated `ProviderConformanceSuite` entity checks to use collection-scoped references (`findByRef`/`deleteByRef`) instead of deprecated ID-only calls.
        - Migrated governance/entity read-delete paths in `DataLifecycleHandler` to collection-scoped references (`findByRef`/`deleteByRef`) for redaction, policy lookup, and policy delete/get flows.
        - Migrated retention purge batch delete in `DataLifecycleHandler` from deprecated ID-only `deleteBatch` to collection-scoped `deleteByRefs`.
        - Validated with `:products:data-cloud:planes:action:event-bridge:test` (all passing).
        - Validated runtime conformance compilation with `:products:data-cloud:delivery:runtime-composition:compileJava`.
        - Re-validated with `:products:data-cloud:delivery:launcher:compileJava` and `:products:data-cloud:delivery:launcher:test --tests DataCloudHttpServerCapabilityTest` (passing).
        - Verification sweep: no remaining deprecated `EntityStore` ID-only call sites in `products/data-cloud/**/src/main/**`.
        - Remaining work: run full integration suite after existing unrelated integration compile blockers are resolved.

## P0 — Must fix before production

1. **Make H2 event offsets truly per-tenant.**
   Area: Event Plane / H2 Event Store
   Evidence: ADR-027 says per-tenant offsets are canonical and each `(tenantId, offset)` addresses an event inside a tenant-private stream.  Current H2 event schema still uses `offset_value BIGINT AUTO_INCREMENT PRIMARY KEY`, with `tenant_id` only as a column/filter. 
   Required fix: Replace global `AUTO_INCREMENT` with tenant-scoped offset allocation. Use `PRIMARY KEY (tenant_id, offset_value)` or equivalent. Allocate next offset per tenant transactionally. Update `append`, `appendBatch`, `read`, `tail`, `getLatestOffset`, `getEarliestOffset`, migration logic, and tests.
   Acceptance criteria: Tenant A and Tenant B can both have offset `1`; one tenant’s event rate cannot be inferred from another tenant’s offsets; all event reads/tails are tenant-relative.
   Tests required: H2 contract test for interleaved tenant appends, tenant-local latest/earliest offsets, tenant-local read from offset, tenant-local tail from beginning/latest, migration test for existing global-offset DB.

2. **Make event idempotency race-safe and enforce it with a real constraint.**
   Area: Event Plane / Idempotency
   Evidence: Current H2 append checks existing idempotency key before insert, but schema creates a normal index, not a unique constraint. Concurrent requests can both pass the lookup and insert duplicates. 
   Required fix: Add unique tenant-scoped idempotency enforcement, ideally `UNIQUE (tenant_id, idempotency_key)` with proper null handling or a separate transactional idempotency table. Insert idempotency claim first or use database-conflict handling.
   Acceptance criteria: Two concurrent appends with the same `(tenantId, idempotencyKey)` return the same offset/result and never create duplicates.
   Tests required: Duplicate idempotency test, concurrent append test, conflicting payload test, batch idempotency rollback test.

3. **Enforce canonical event envelope before persistence.**
   Area: Event Plane / SPI / Contract Plane
   Evidence: `DataCloudClient.Event` defines envelope fields and a `validate()` method requiring source, but `DataCloud.appendEvent` does not call `event.validate()`. It only copies optional fields into headers before storing.   `EventLogStore.EventEntry` still models most envelope fields as generic headers rather than first-class required fields. 
   Required fix: Call validation in `appendEvent`, define production-required envelope fields explicitly, and make storage/API behavior consistent. Required fields should include at least source, schema version, correlation/trace context, subject reference where applicable, classification/policy/provenance for governed data.
   Acceptance criteria: Invalid events are rejected consistently at API, client, and store boundaries before persistence; canonical envelope fields round-trip without loss.
   Tests required: API contract tests, `DataCloudClient.Event.validate()` tests, append rejection tests, H2/in-memory round-trip tests, OpenAPI schema parity test.

4. **Remove production heuristic AI success on provider failure.**
   Area: Intelligence Plane / AI Assist
   Evidence: The handler has a production-mode check for missing `CompletionService`, but downstream `callAi` failure paths still return heuristic responses. The class-level docs still say AI unavailability returns `200` and never `5xx`, which conflicts with production-grade no-fake-success behavior. 
   Required fix: In production, if AI is configured but unavailable/failing, return explicit `503`, degraded Runtime Truth, or a review-required/unavailable state. Keep heuristic fallback only for local/preview mode and label it clearly.
   Acceptance criteria: Production AI endpoints never present static heuristic output as successful AI output.
   Tests required: Production no-completion test, production provider-error test, local fallback test, preview fallback UI test, Runtime Truth degraded-AI test.

5. **Prove production fail-closed behavior for trust-critical dependencies.**
   Area: Operations / Governance / Production Profile
   Evidence: `DataCloudHttpServer` still documents several optional/fallback dependencies: audit service can be null, settings store falls back to in-memory, idempotency can be in-memory, trace export can be absent, and completion service can be absent.  Bootstrap wires some of these when database/event-store dependencies exist, but the full fail-closed production gate must be proven end-to-end. 
   Required fix: Add a single production preflight gate that refuses traffic unless auth, tenant binding, durable entity store, durable event store, durable idempotency, persistent settings, audit writer, policy engine, metrics, and required trace posture are configured or explicitly waived by a production-safe policy.
   Acceptance criteria: Production startup fails with actionable errors when any trust-critical dependency is missing.
   Tests required: Production startup matrix tests for each missing dependency; sensitive mutation test proving missing audit fails closed.

6. **Make the four-tier event-log responsibility real or explicitly staged.**
   Area: Event Plane / Operations Plane
   Evidence: Owner doc says Data Cloud manages the four-tier event log: journal, hot, warm, cold.  Current inspected H2 event implementation is a single table plus polling tail, not a four-tier lifecycle implementation. 
   Required fix: Either implement clear tier lifecycle interfaces and transitions, or mark tiers beyond current H2 as unavailable/preview through Runtime Truth and docs.
   Acceptance criteria: Product surfaces do not imply four-tier durability unless journal/hot/warm/cold behavior exists and is tested.
   Tests required: Tier state Runtime Truth tests, retention/migration tests, compaction/archive tests, documentation parity test.

7. **Make event `appendBatch` atomic across idempotency and offsets, not just inserts.**
   Area: Event Plane / Batch Writes
   Evidence: H2 `appendBatch` now uses a transaction around appends, but the underlying idempotency strategy is still select-before-insert and offsets are global. 
   Required fix: Rework batch append after fixing tenant-scoped offsets and unique idempotency so the whole batch is all-or-nothing, with deterministic returned offsets.
   Acceptance criteria: A failing event in a batch leaves no new offsets, idempotency records, or partial rows.
   Tests required: Batch duplicate-key rollback, invalid-event rollback, concurrent batch append test.

## P1 — Must fix before release

8. **Make collection-scoped `EntityStore` methods mandatory or fail-fast.**
   Area: Data Plane / Shared SPI
   Evidence: `EntityStore` adds `EntityRef`, but default `findByRef`, `deleteByRef`, and `existsByRef` delegate to deprecated unsafe ID-only methods. This lets future providers appear collection-scoped while actually using unsafe legacy behavior. 
   Required fix: Make collection-scoped methods abstract in the next breaking SPI cleanup, or make defaults throw `UnsupportedOperationException`. Keep deprecated ID-only methods only behind explicit legacy adapter classes.
   Acceptance criteria: No provider can pass silently without implementing collection-scoped identity.
   Tests required: Provider conformance test with intentionally incomplete provider must fail.

9. **Harden H2 query field validation before using JSON paths.**
   Area: Data Plane / Query Security
   Evidence: H2 builds JSON_VALUE expressions from filter/sort fields and only strips single quotes from field names. 
   Required fix: Validate field names against a strict allowlist/regex before building SQL expressions. Reject nested or unsafe JSON paths unless intentionally supported by a parser.
   Acceptance criteria: Invalid or malicious field names return a clear 400/422 and never reach SQL construction.
   Tests required: Query injection tests, invalid field-name tests, allowed nested-field tests if nested fields are supported.

10. **Unify `DataCloudClient.Query`, `EntityStore.QuerySpec`, OpenAPI, and UI query models.**
    Area: Contract Plane / Data Plane / SDK / UI
    Evidence: `EntityStore.QuerySpec` supports filters, sorts, search, projections, consistency level, and freshness hint.  `DataCloudClient.Query` exposes only filters, sorts, offset, and limit. 
    Required fix: Make one canonical query model and generate/adapt all public API, Java client, TypeScript client, UI schemas, and store SPI from it.
    Acceptance criteria: No query option is accepted in one layer and silently dropped in another.
    Tests required: Contract drift tests, Java/TypeScript type parity tests, OpenAPI generated-client tests.

11. **Implement or explicitly reject projections, consistency, freshness, and search across all entity providers.**
    Area: Data Plane / Provider Conformance
    Evidence: `EntityStore.QuerySpec` includes projections, consistency, freshness, and search. H2 implements search but always returns full entity rows; consistency/freshness are accepted but not enforced.  
    Required fix: For every store, implement each option or reject it with a clear unsupported-feature error.
    Acceptance criteria: No QuerySpec option is silently ignored.
    Tests required: Provider conformance tests for each QuerySpec field.

12. **Complete Runtime Truth migration and retire compatibility naming.**
    Area: Runtime Truth Plane / Experience Plane / Contract Plane
    Evidence: README says `/api/v1/surfaces` is target and `/api/v1/capabilities` is compatibility only.  Architecture says to use Runtime Truth / surface state terminology and avoid capability-truth/registry language except temporarily.  Current server still has `CapabilityRegistryHandler` and compatibility naming. 
    Required fix: Make `/surfaces` primary everywhere, keep `/capabilities` as a tested compatibility adapter only, and define a removal gate/date. Rename UI services/components/hooks away from compatibility-era names where they represent runtime truth.
    Acceptance criteria: UI and docs primarily use Runtime Truth terminology; compatibility endpoint is clearly isolated.
    Tests required: Runtime Truth API tests, UI route-gate tests, compatibility endpoint parity tests.

13. **Prove plane dependency boundaries in CI.**
    Area: Architecture / Build Governance
    Evidence: Architecture forbids Data/Event/Context/Governance planes from importing Action implementation internals and forbids contracts from depending on runtime implementation. 
    Required fix: Add/verify ArchUnit or Gradle dependency checks for all plane rules and shared-library boundary rules.
    Acceptance criteria: Build fails on forbidden dependency direction.
    Tests required: Boundary tests with negative fixture or explicit forbidden import assertions.

14. **Review and split Data Cloud-specific platform/kernel abstractions.**
    Area: Shared Libraries / Kernel Boundary
    Evidence: Architecture says shared platform modules should remain shared only when genuinely cross-product infrastructure; Data Cloud-specific plane/action semantics should move into `products/data-cloud`.  Owner lists `data-cloud/spi`, `data-cloud/platform-api`, and `data-cloud/kernel-bridge` as product-owned shared libraries. 
    Required fix: Generate a dependency/import inventory for platform/kernel modules used by Data Cloud. Move product-specific behavior into Data Cloud extensions/SPI and keep only generic primitives in platform.
    Acceptance criteria: Platform/kernel has no Data Cloud product behavior except stable generic interfaces or approved adapters.
    Tests required: Dependency report, boundary test, API compatibility tests.

15. **Make Action Plane/AEP boundary enforceable, not only documented.**
    Area: Action Plane / AEP Integration
    Evidence: Owner says Data Cloud may publish work requests and persist definitions/memory/checkpoints/results but must not import AEP modules; AEP consumes public Data Cloud APIs/contracts. 
    Required fix: Add build-time dependency rule preventing non-action planes and shared SPI from importing AEP implementation modules. Keep AEP-named compatibility only under clearly isolated Action Plane/runtime adapter modules.
    Acceptance criteria: No circular dependency or hidden AEP product dependency exists.
    Tests required: Gradle dependency check, ArchUnit import check, action-plane contract parity test.

16. **Make production AI fallback policy consistent across docs, code, Runtime Truth, and UI.**
    Area: Intelligence Plane / UX / Operations
    Evidence: Handler comments still advertise 200 heuristic fallback on AI unavailability, while production-grade audit doctrine requires no fake success. 
    Required fix: Define exact modes: local heuristic preview, staging degraded test mode, production fail-closed or review-required. Apply consistently in `AiAssistHandler`, Runtime Truth, UI banners, API docs, and tests.
    Acceptance criteria: Users/operators can always tell whether AI output came from a real provider, heuristic preview, or unavailable provider.
    Tests required: API response tests and UI state tests for all modes.

17. **Make tenant/workspace isolation a first-class `TenantWorkspaceContext`.**
    Area: Governance Plane / Shared SPI
    Evidence: README and architecture emphasize tenant/workspace isolation and governance, but inspected entity/event APIs primarily pass tenant string or `TenantContext`; workspace propagation is not clearly universal.  
    Required fix: Define canonical context containing tenant, workspace, actor, session, roles/permissions, request/correlation/trace IDs, sovereignty region, and policy context. Use it across API, storage, audit, events, query, AI, and plugins.
    Acceptance criteria: Every critical operation has a complete context and tests prove tenant/workspace boundaries.
    Tests required: Cross-tenant, cross-workspace, API key/JWT tenant binding, audit context tests.

18. **Make settings, idempotency, and audit storage durable outside local/embedded mode.**
    Area: Operations Plane / Governance Plane
    Evidence: `DataCloudHttpServer` comments still describe in-memory/fallback behavior for settings and idempotency, and optional audit service. 
    Required fix: Enforce persistent stores in all non-local profiles. Local mode can use in-memory only with Runtime Truth degraded/local labeling.
    Acceptance criteria: Non-local startup fails if durable settings/idempotency/audit stores are absent.
    Tests required: Startup matrix tests and restart persistence tests.

19. **Make entity batch writes explicitly transactional or explicitly partial.**
    Area: Data Plane / Batch Semantics
    Evidence: H2 entity store `saveBatch` loops over each entity and returns success; no transaction boundary is visible around the whole batch. 
    Required fix: Decide semantics. For production-grade correctness, prefer transactional all-or-nothing for single-collection batch or documented partial-success with exact per-item errors.
    Acceptance criteria: Partial failure cannot be reported as full success.
    Tests required: Batch success, batch partial failure, rollback or per-item error tests.

20. **Normalize event envelope storage away from opaque headers.**
    Area: Event Plane / Queryability / Governance
    Evidence: `DataCloud.appendEvent` writes envelope fields into `x-dc-*` headers, and `EventLogStore.EventEntry` stores headers as a string map.  
    Required fix: Promote important envelope fields to first-class columns/fields where they are needed for filtering, policy, lineage, replay, and audit. Keep headers for extensibility only.
    Acceptance criteria: Event queries can filter by source, subject, schema version, correlation ID, classification, and trace/provenance without parsing opaque header JSON.
    Tests required: Event query/filter tests and migration tests.

21. **Fix `Event.of(...)` backward compatibility so it cannot bypass production validation.**
    Area: Event Plane / API Safety
    Evidence: `Event.of(type, payload)` builds an event without source, while `validate()` says source is required for production use. 
    Required fix: Either deprecate `Event.of`, require source in factory methods, or make append reject source-less events except in local/test mode.
    Acceptance criteria: Production code cannot append source-less events by convenience factory.
    Tests required: Compile/deprecation check and append rejection tests.

22. **Make `DataCloudClient.queryEvents` honor offset and type filters at the store layer.**
    Area: Event Plane / Query Correctness
    Evidence: `DataCloudClient.queryEvents` reads from offset zero when no time range is supplied, then filters types in memory.  The store already has `readByType`. 
    Required fix: Extend `EventQuery` with offset/page/tail semantics and push event type filtering into store-level queries.
    Acceptance criteria: Large tenant event streams do not require reading generic events then filtering in memory.
    Tests required: Query by type with pagination and large-stream tests.

23. **Fix `TailRequest.fromLatest()` semantics for H2 and in-memory stores.**
    Area: Event Plane / Streaming
    Evidence: `TailRequest.fromLatest()` uses `-1`, while H2 polling normalizes offset with `Math.max(0, parseOffset(from))`, which turns `-1` into `0`, causing latest to behave like beginning.  
    Required fix: Resolve latest to current tenant latest offset + 1 at subscription start.
    Acceptance criteria: A new tail-from-latest subscription receives only new events appended after subscription.
    Tests required: H2 and in-memory tail-from-latest tests.

24. **Make H2 tail polling configurable and observable.**
    Area: Event Plane / Operations / Performance
    Evidence: H2 tail uses a scheduler polling every 250ms with backoff on error. 
    Required fix: Expose poll interval, max subscribers, max batch size, and backoff settings through config and Runtime Truth. Emit metrics for active subscribers, lag, errors, and poll duration.
    Acceptance criteria: Operators can detect and control tail pressure.
    Tests required: Tail load test and metrics test.

25. **Complete Context Plane implementation beyond placeholder boundary.**
    Area: Context Plane / Product Vision
    Evidence: README marks `planes/context/` as “Context Plane placeholder and ownership boundary,” while architecture expects lineage, provenance, freshness, semantic context, memory, RAG, and retrieval grounding.  
    Required fix: Turn the placeholder into a real plane with contracts, APIs, storage model, provenance graph, freshness scoring, retrieval grounding, and UI surfaces.
    Acceptance criteria: Context APIs and UI can answer “where did this data come from, how fresh is it, what policy applies, and what evidence supports it?”
    Tests required: Context retrieval, provenance, freshness, RAG grounding, and permission tests.

26. **Implement Data Quality and Trust as canonical Data/Governance contracts, not only UI pages.**
    Area: Data Plane / Governance Plane / UX
    Evidence: Architecture defines data quality/history/queryable records in Data Plane and trust/audit/policy/privacy in Governance Plane. 
    Required fix: Define data-quality score, trust score, policy posture, lineage completeness, freshness, schema drift, and anomaly signals in contracts and backend services. Make UI consume real data.
    Acceptance criteria: Trust/Data Quality pages show real computed state, not derived placeholders.
    Tests required: Data-quality computation tests, trust API tests, UI integration tests.

27. **Complete Feature Store and Model Registry production flow.**
    Area: Intelligence Plane / AI/ML Substrate
    Evidence: Owner says Data Cloud hosts feature store and model metadata.  Bootstrap wires AI model manager and feature store only when AI/database features are enabled. 
    Required fix: Validate full model lifecycle: register, version, promote, rollback, feature ingest, feature retrieval, tenant/workspace isolation, audit, and Runtime Truth states.
    Acceptance criteria: Feature/model APIs are production-backed in enabled profiles and unavailable/degraded otherwise.
    Tests required: Model registry API tests, feature store integration tests, tenant isolation tests, Runtime Truth tests.

28. **Make plugins/connectors lifecycle production-grade.**
    Area: Extensions / Operations / Security
    Evidence: Data Cloud owns plugin lifecycle and connector/extension modules in README/OWNER.  
    Required fix: Add plugin install/enable/disable/upgrade/rollback contracts, signature/trust checks, sandbox policy, audit events, failure recovery, and runtime visibility.
    Acceptance criteria: Plugin/connector actions are governed, auditable, reversible, and visible in Runtime Truth.
    Tests required: Plugin lifecycle tests, connector config tests, security/sandbox tests, audit tests.

29. **Make UI use generated clients or typed frontend adapters only.**
    Area: Experience Plane / Contract Plane
    Evidence: README architecture rules say UI must use generated clients and frontend adapters, not backend internals. 
    Required fix: Audit `delivery/ui/src/**` for hand-maintained DTOs and API clients. Replace with generated SDK types or explicit adapters generated/validated from OpenAPI.
    Acceptance criteria: UI API shapes cannot drift from contract.
    Tests required: SDK generation test, UI typecheck, contract-drift test.

30. **Validate OpenAPI route parity against runtime server routes.**
    Area: Contract Plane / Delivery API
    Evidence: Contracts are canonical under `products/data-cloud/contracts/openapi/data-cloud.yaml` and `action-plane.yaml`; runtime implementations live in delivery modules.  
    Required fix: Generate runtime route inventory from `DataCloudRouterBuilder`/server handlers and compare to OpenAPI. Fail CI on drift.
    Acceptance criteria: Every runtime route is documented, and every documented stable route is implemented.
    Tests required: OpenAPI route parity test in CI.

31. **Validate `aep.yaml` and `action-plane.yaml` equivalence until compatibility contract is retired.**
    Area: Contract Plane / Action Plane Compatibility
    Evidence: README says `aep.yaml` and `action-plane.yaml` must remain equivalent until the AEP-named contract is retired. 
    Required fix: Add contract diff/equivalence check and define retirement plan.
    Acceptance criteria: CI fails when compatibility and target action-plane contracts drift.
    Tests required: Contract equivalence test.

32. **Make docs and code agree on `/api/v1/surfaces`, `/api/v1/capabilities`, and Runtime Truth states.**
    Area: Docs / API / UI
    Evidence: README defines Runtime Truth states as live, degraded, disabled, preview, unavailable, or misconfigured. 
    Required fix: Confirm OpenAPI, server response, UI schemas, and docs all use the same state enum and naming.
    Acceptance criteria: No duplicate or mismatched runtime-state enum exists across Java, TypeScript, and OpenAPI.
    Tests required: Enum drift test and UI schema test.

33. **Add release gate that rejects stale audit/TODO target refs.**
    Area: Documentation / Release Governance
    Evidence: Existing audit/TODO artifacts in the repo can become stale relative to current head; this audit explicitly avoids relying on them.
    Required fix: Add a CI/doc check that audit and TODO metadata match the reviewed commit or clearly say they are historical.
    Acceptance criteria: New release cannot ship with stale “current audit” docs.
    Tests required: Docs metadata validation script.

34. **Run full Data Cloud Java test suite from current head and triage failures.**
    Area: Validation
    Required fix: Execute product-scoped Gradle checks for Data Cloud modules, including shared SPI, runtime composition, launcher, contracts, and plane tests.
    Acceptance criteria: All Data Cloud Java tests pass at `1577a855...`, or failures are converted into explicit TODOs.
    Tests required: Existing full Java suite.

35. **Run full Data Cloud UI typecheck/test/e2e suite from current head and triage failures.**
    Area: Experience Plane / Validation
    Required fix: Execute UI typecheck, unit tests, route tests, runtime truth tests, accessibility checks, and Playwright flows if configured.
    Acceptance criteria: Data Cloud UI passes current-head gates or produces concrete remediation tasks.
    Tests required: Existing UI suite.

36. **Create a provider conformance matrix for every EntityStore and EventLogStore implementation.**
    Area: Shared SPI / Provider Quality
    Required fix: List in-memory, H2, future Postgres/Kafka/RocksDB/object-store providers and run the same conformance suite against each.
    Acceptance criteria: Every provider declares supported profile, durability level, query features, event semantics, offset model, idempotency behavior, and limitations.
    Tests required: Shared provider conformance suite.

37. **Define production vs local vs sovereign profile capabilities explicitly in Runtime Truth.**
    Area: Operations Plane / Sovereignty
    Required fix: Runtime Truth must show not only “route enabled” but also storage durability, AI provider, external network allowance, trace/audit status, data residency, and tier availability for each profile.
    Acceptance criteria: Operators can inspect whether a deployment is local/preview/sovereign/production-safe from `/surfaces`.
    Tests required: Runtime Truth profile tests.

38. **Add end-to-end journey tests for critical Data Cloud flows.**
    Area: Product E2E
    Required fix: Cover entity create/read/query/delete, event append/replay/tail, Runtime Truth gating, AI unavailable behavior, tenant isolation, audit trail, plugin lifecycle, model/feature lifecycle, and data-quality/trust views.
    Acceptance criteria: Critical journeys are proven from UI/API to backend/store/audit/observability.
    Tests required: API E2E and browser E2E tests against real backend.

39. **Add observability coverage for every critical route and background operation.**
    Area: Operations Plane
    Required fix: Emit structured logs, metrics, traces, correlation IDs, and audit events consistently across entity, event, query, AI, governance, plugin, model, feature, and settings flows.
    Acceptance criteria: Every P0/P1 flow is diagnosable without code-level debugging.
    Tests required: Metrics assertion tests, trace/audit correlation tests, log redaction tests.

40. **Remove or isolate all remaining compatibility/deprecated APIs before production release.**
    Area: DRY / Source of Truth
    Evidence: `EntityStore` keeps deprecated ID-only methods and Runtime Truth still has compatibility capabilities language.  
    Required fix: Create a compatibility retirement plan. For anything retained, isolate behind adapter modules and tests.
    Acceptance criteria: Production paths do not depend on deprecated APIs except through explicit compatibility adapters.
    Tests required: Deprecation usage scan and adapter-only enforcement test.
