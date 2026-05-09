# End-to-End Data Cloud TODO List

**Source audit report:** `products/data-cloud/docs/audits/end-to-end-data-cloud-correctness-shared-libraries-audit.md`  
**Requested target commit/ref:** `2c97e146c4b4928c897591d5a08585080fe8253`  
**Status note:** Target commit could not be resolved. Correct/validate commit first, then rerun audit exactly against that ref.

---

## P0 — Must Fix Before Production

- [x] DC-P0-001 — Resolve requested target commit and rerun audit.
  - Area: audit reproducibility
  - File(s): repository ref / CI metadata
  - Required fix: Verify whether `2c97e146c4b4928c897591d5a08585080fe8253` exists in `samujjwal/ghatana`; if wrong, provide correct SHA and rerun audit.
  - Acceptance criteria: Audit report states exact commit and all evidence is from that commit.
  - Tests required: commit fetch/check script.
  - Resolution: Corrected SHA to `e2c97e146c4b4928c897591d5a08585080fe8253` (missing leading '2').

- [x] DC-P0-002 — Fail closed in production when auth providers are missing.
  - Area: security/auth
  - File(s): `DataCloudHttpServer.java`, security filters/config
  - Required fix: Production/sovereign profiles must require API key/JWT/auth provider configuration.
  - Acceptance criteria: Production startup fails with actionable error if auth providers are absent.
  - Tests required: production startup fail-closed test.
  - Status: Already implemented in validateProductionDependencies() with test coverage.

- [x] DC-P0-003 — Fail closed in production when policy engine is missing.
  - Area: governance/policy
  - File(s): `DataCloudHttpServer.java`, policy filter/classes
  - Required fix: Critical routes must fail closed when policy engine is absent.
  - Acceptance criteria: Mutating/sensitive routes cannot execute without policy decision.
  - Tests required: route-level authz/policy tests.
  - Status: Already implemented in validateProductionDependencies() with test coverage.

- [x] DC-P0-004 — Require durable audit service for production critical actions.
  - Area: audit/compliance
  - File(s): `DataCloudHttpServer.java`, all critical handlers
  - Required fix: Remove silent audit skip for production critical operations.
  - Acceptance criteria: Critical actions emit durable audit record or fail.
  - Tests required: audit emission integration tests.
  - Status: Already implemented in validateProductionDependencies() with test coverage.

- [x] DC-P0-005 — Block in-memory idempotency store in production.
  - Area: data integrity
  - File(s): `DataCloudHttpServer.java`, `EntityWriteIdempotencyStore`
  - Required fix: Require durable idempotency store outside local/test.
  - Acceptance criteria: Retried entity writes remain idempotent after process restart.
  - Tests required: restart/idempotency integration test.
  - Status: Already implemented in validateProductionDependencies() with test coverage.

- [x] DC-P0-006 — Block fake/in-memory stores for production profiles.
  - Area: persistence/profile safety
  - File(s): storage provider wiring and profile config
  - Required fix: Production/sovereign profiles require durable entity/event/settings/audit stores.
  - Acceptance criteria: Local/test can use in-memory; production cannot.
  - Tests required: profile matrix startup tests.
  - Status: Already implemented in validateProductionDependencies() and validateSettingsStorageConfiguration() with test coverage.

- [x] DC-P0-007 — Enforce tenant isolation for every API group.
  - Area: tenant isolation/security/privacy
  - File(s): all HTTP handlers and security filters
  - Required fix: Verify and enforce tenant extraction, tenant scoping, role/policy checks, and sensitive-data redaction.
  - Acceptance criteria: Cross-tenant access fails for every route group.
  - Tests required: tenant isolation integration suite.
  - Status: Already implemented via DataCloudHttpServerCriticalRouteTenantEnforcementTest and DataCloudSecurityFilter.

---

## P1 — Must Fix Before Release

- [ ] DC-P1-001 — Rename `CapabilityRegistryHandler` to Runtime Truth terminology.
  - Area: Runtime Truth
  - File(s): `delivery/launcher/.../handlers/CapabilityRegistryHandler.java`
  - Required fix: Rename class and references to `RuntimeTruthHandler` or `SurfaceRegistryHandler`.
  - Acceptance criteria: Active code no longer uses capability-registry naming for canonical Runtime Truth.
  - Tests required: compile + route tests.

- [ ] DC-P1-002 — Replace Runtime Truth response key `capabilities` with `surfaces`.
  - Area: API contract/runtime truth
  - File(s): Runtime Truth handler, OpenAPI, UI surface service
  - Required fix: Emit canonical envelope with `surfaces`.
  - Acceptance criteria: `/api/v1/surfaces` response is structurally surface-based.
  - Tests required: OpenAPI contract test + UI parsing test.

- [ ] DC-P1-003 — Replace `CapabilityRegistryEnvelopeSchema` with `SurfaceRegistryEnvelopeSchema`.
  - Area: frontend contracts
  - File(s): `delivery/ui/src/api/surfaces.service.ts`, `delivery/ui/src/contracts/schemas`
  - Required fix: Define/generated `SurfaceRegistryEnvelopeSchema`.
  - Acceptance criteria: UI services do not parse `/surfaces` with capability schema.
  - Tests required: schema parsing tests.

- [ ] DC-P1-004 — Remove or isolate `useCapabilityRegistry` compatibility API.
  - Area: frontend deprecation cleanup
  - File(s): `delivery/ui/src/api/surfaces.service.ts`
  - Required fix: Replace consumers with `useSurfaceRegistry`; if compatibility remains, mark deprecated and test no canonical routes use it.
  - Acceptance criteria: No canonical UI code depends on capability helpers.
  - Tests required: import scan/dependency lint.

- [ ] DC-P1-005 — Make OpenAPI drift check mandatory in CI.
  - Area: contracts/CI
  - File(s): `products/data-cloud/scripts/check-openapi-drift.sh`, `.github/workflows/data-cloud-ci.yml`
  - Required fix: Run without `--warn-only`; fail on drift.
  - Acceptance criteria: New route without OpenAPI update fails CI.
  - Tests required: CI workflow validation.

- [ ] DC-P1-006 — Generate or validate frontend API types from OpenAPI.
  - Area: contracts/frontend DRY
  - File(s): OpenAPI, UI API services, SDK
  - Required fix: Remove duplicated hand-maintained API response types or validate them against generated types.
  - Acceptance criteria: UI type drift is caught.
  - Tests required: type generation/parity test.

- [ ] DC-P1-007 — Finalize canonical Action Plane route namespace.
  - Area: API design/deprecation
  - File(s): OpenAPI contracts, `DataCloudRouterBuilder.java`
  - Required fix: Decide whether canonical action routes live under `/api/v1/action/*`; migrate or document current root routes.
  - Acceptance criteria: No ambiguous AEP/action/product route ownership.
  - Tests required: OpenAPI route parity tests.

- [ ] DC-P1-008 — Remove or deadline compatibility UI routes.
  - Area: UI routing/deprecation
  - File(s): `delivery/ui/src/routes.tsx`
  - Required fix: Classify each alias as redirect/remove/keep.
  - Acceptance criteria: Route truth matrix marks canonical vs deprecated.
  - Tests required: Playwright route redirect tests.

- [ ] DC-P1-009 — Fix duplicate rendering risk in nested plugin route.
  - Area: UI routing
  - File(s): `delivery/ui/src/routes.tsx`
  - Required fix: Do not render `PluginsPage` as both parent element and index child unless intentionally using outlet layout.
  - Acceptance criteria: `/plugins` and `/plugins/:id` render expected component tree.
  - Tests required: route rendering tests.

- [ ] DC-P1-010 — Enforce production metrics provider.
  - Area: observability
  - File(s): `DataCloudHttpServer.java`
  - Required fix: No-op metrics only in local/test.
  - Acceptance criteria: Production starts only with real metrics collector.
  - Tests required: production profile startup test.

- [ ] DC-P1-011 — Surface AI heuristic fallback as degraded/preview behavior.
  - Area: AI/ML correctness
  - File(s): AI assist handlers, Runtime Truth supplier, UI AI surfaces
  - Required fix: API/UI expose `fallback=true`, model/source, confidence, and degraded state.
  - Acceptance criteria: No fake AI success state.
  - Tests required: AI fallback contract and UI tests.

- [ ] DC-P1-012 — Ensure optional 501/503 services are represented in Runtime Truth.
  - Area: Runtime Truth/UI gating
  - File(s): router, runtime snapshot supplier, UI route gates
  - Required fix: Search/export/anomaly/report/model/feature/federated/tier migration surfaces disabled/degraded when missing.
  - Acceptance criteria: UI never shows enabled action for unavailable backend.
  - Tests required: runtime surface gating E2E.

- [ ] DC-P1-013 — Add entity CRUD durable/data-integrity tests.
  - Area: Data Plane
  - File(s): entity handlers/store/tests
  - Required fix: Test create/update/query/delete/history/idempotency/tenant boundaries.
  - Acceptance criteria: Entity behavior proven against durable store.
  - Tests required: DB integration tests.

- [ ] DC-P1-014 — Add event append/replay/ordering/tenant-isolation tests.
  - Area: Event Plane
  - File(s): event handlers/store/tests
  - Required fix: Prove append-only behavior, offset ordering, replay, stream tenant scope.
  - Acceptance criteria: Event log is durable and isolated.
  - Tests required: event integration/load tests.

- [ ] DC-P1-015 — Add governance redaction/retention/legal-hold tests.
  - Area: Governance Plane
  - File(s): governance handlers/services/tests
  - Required fix: Verify data, event, audit, and derived-index effects.
  - Acceptance criteria: Redaction/retention is correct and auditable.
  - Tests required: governance integration tests.

---

## Product Boundary and Plane Fixes

- [ ] DC-BND-001 — Add architecture dependency checks for forbidden plane imports.
  - Area: architecture boundaries
  - Required fix: Data/Event/Context/Governance/Intelligence planes must not import Action implementation internals.
  - Acceptance criteria: Forbidden import fails CI.
  - Tests required: ArchUnit/dependency test.

- [ ] DC-BND-002 — Audit `planes/action/helm/aep` naming.
  - Area: product boundary/deployment naming
  - Required fix: Rename or mark as internal Action Plane runtime chart, not separate product.
  - Acceptance criteria: Customer-facing deployment names use Data Cloud Action Plane.
  - Tests required: doc/path naming scan.

- [ ] DC-BND-003 — Move Data Cloud/Action-specific semantics out of generic platform modules.
  - Area: shared platform cleanup
  - Required fix: Split generic primitives from product semantics.
  - Acceptance criteria: Platform modules remain truly cross-product.
  - Tests required: dependency usage audit.

---

## Contract/API/SDK Fixes

- [ ] DC-CON-001 — Make OpenAPI the only API source of truth.
  - Required fix: Generate REST docs from OpenAPI or reduce docs to links/explanations.
  - Acceptance criteria: No manually duplicated route docs can drift.
  - Tests required: docs generation check.

- [ ] DC-CON-002 — Verify `action-plane.yaml` and retire `aep.yaml`.
  - Required fix: Confirm equivalence, then remove/deprecate `aep.yaml` with deadline.
  - Acceptance criteria: One canonical Action Plane public contract.
  - Tests required: schema diff/equivalence test until removed.

- [ ] DC-CON-003 — Add response/error envelope consistency tests.
  - Required fix: Standardize success/error envelopes across route groups.
  - Acceptance criteria: All APIs return documented envelope.
  - Tests required: API contract tests.

---

## Backend/Domain/Data/Event/Storage Fixes

- [ ] DC-BE-001 — Centralize Runtime Truth status taxonomy.
  - Required fix: One enum/schema for `LIVE`, `DEGRADED`, `DISABLED`, `PREVIEW`, `UNAVAILABLE`, `MISCONFIGURED`.
  - Acceptance criteria: No duplicate status enum drift.
  - Tests required: compile/type parity tests.

- [ ] DC-BE-002 — Ensure mutating routes are idempotent or explicitly non-idempotent.
  - Required fix: Add idempotency keys or documented non-idempotent behavior.
  - Acceptance criteria: Retries do not corrupt data.
  - Tests required: retry tests.

- [ ] DC-BE-003 — Add transaction boundaries for multi-step writes.
  - Required fix: Wrap write + event + audit flows atomically where required.
  - Acceptance criteria: Partial writes cannot leave invalid state.
  - Tests required: failure injection tests.

- [ ] DC-BE-004 — Standardize deletion lifecycle.
  - Required fix: Define hard delete vs soft delete vs archive vs retention purge.
  - Acceptance criteria: Deletes are deterministic and auditable.
  - Tests required: delete lifecycle tests.

---

## UI/UX and Frontend Fixes

- [ ] DC-UI-001 — Create canonical Data Cloud route truth matrix.
  - Required fix: Generate matrix from route config.
  - Acceptance criteria: Route docs cannot drift.
  - Tests required: route docs check.

- [ ] DC-UI-002 — Ensure every unavailable surface has actionable disabled UI.
  - Required fix: Show dependency, status, next action, and remediation/runbook link.
  - Acceptance criteria: No dead button or fake success for unavailable feature.
  - Tests required: Playwright disabled-surface tests.

- [ ] DC-UI-003 — Remove local duplicate buttons/cards/badges when reusable component exists.
  - Required fix: Use `@data-cloud/ui-components` and design system consistently.
  - Acceptance criteria: No duplicated presentational primitives.
  - Tests required: import/lint scan.

- [ ] DC-UI-004 — Ensure UI services use generated/validated clients.
  - Required fix: Replace ad hoc response types with generated/contract-validated types.
  - Acceptance criteria: UI cannot compile against stale API types.
  - Tests required: type/contract tests.

---

## Shared Library, Abstraction, DRY, and Source-of-Truth Fixes

- [ ] DC-SHARED-001 — Enforce `@data-cloud/ui-components` has no app dependencies.
  - Required fix: Add dependency check blocking imports from routing, stores, services, app pages.
  - Acceptance criteria: Component library remains presentational.
  - Tests required: dependency boundary test.

- [ ] DC-SHARED-002 — Validate direct shared UI/platform dependencies are actually needed.
  - Required fix: Remove unused workspace dependencies and avoid library sprawl.
  - Acceptance criteria: Dependency list is minimal and justified.
  - Tests required: unused dependency scan.

- [ ] DC-SHARED-003 — Move Data Cloud-specific utilities out of generic shared libraries.
  - Required fix: Product-specific semantics should live under `products/data-cloud`.
  - Acceptance criteria: Shared libraries are reusable across unrelated products.
  - Tests required: dependency/API audit.

---

## Security, Privacy, Governance, and Tenant-Isolation Fixes

- [ ] DC-SEC-001 — Add full tenant isolation test matrix.
  - Required fix: Verify every route group rejects cross-tenant access.
  - Acceptance criteria: No cross-tenant read/write/stream/replay.
  - Tests required: integration suite.

- [ ] DC-SEC-002 — Add secrets-in-logs/client-bundle scan.
  - Required fix: Prevent secrets/PII from logs/traces/client JS.
  - Acceptance criteria: CI scan passes.
  - Tests required: static scan.

- [ ] DC-SEC-003 — Add connector/plugin credential and sandbox tests.
  - Required fix: Validate credential redaction, residency policy, sandbox limits, audit.
  - Acceptance criteria: Plugins/connectors cannot leak secrets or bypass policy.
  - Tests required: security integration tests.

---

## Observability, Operations, and Runtime Truth Fixes

- [ ] DC-OBS-001 — Add dependency-rich Runtime Truth snapshot.
  - Required fix: Include plane, surface, dependency, owner, state, evidence, last checked timestamp.
  - Acceptance criteria: Operators can diagnose why a surface is degraded.
  - Tests required: contract tests.

- [ ] DC-OBS-002 — Consolidate validation scripts into a product quality gate.
  - Required fix: Create one CI entrypoint for contracts, docs, stubs, deps, tests, route truth, runbooks.
  - Acceptance criteria: One command validates release readiness.
  - Tests required: script smoke test.

- [ ] DC-OBS-003 — Verify backup/restore drills.
  - Required fix: Test backup/restore scripts against durable profile.
  - Acceptance criteria: Restore drill passes and is documented.
  - Tests required: backup drill test.

---

## Performance and Scalability Fixes

- [ ] DC-PERF-001 — Add event append/replay load tests.
  - Required fix: Validate throughput, ordering, memory, replay latency.
  - Acceptance criteria: Baseline thresholds documented.
  - Tests required: load suite.

- [ ] DC-PERF-002 — Add SSE/WebSocket backpressure/reconnect tests.
  - Required fix: Verify reconnect, heartbeat, queue overflow, tenant filtering.
  - Acceptance criteria: Streaming stable under realistic load.
  - Tests required: streaming load tests.

- [ ] DC-PERF-003 — Add UI bundle budget gate.
  - Required fix: Make bundle budget check mandatory in CI.
  - Acceptance criteria: Oversized chunks fail CI.
  - Tests required: bundle check.

---

## Test Additions and Fixes

- [ ] DC-TEST-001 — Add no-production-mocks/stubs/in-memory scan for Data Cloud.
  - Required fix: Scan production paths for mock/stub/fake/TODO/HACK/in-memory/no-op fallbacks.
  - Acceptance criteria: Production-reachable placeholders fail CI.
  - Tests required: scan test.

- [ ] DC-TEST-002 — Add generated SDK parity tests.
  - Required fix: Verify SDK generated from current contracts.
  - Acceptance criteria: SDK drift fails CI.
  - Tests required: SDK generation test.

- [ ] DC-TEST-003 — Add Playwright E2E for critical Data Cloud journeys.
  - Required fix: Cover Home/Data/Events/Query/Pipelines/Trust/Operations/Runtime Truth.
  - Acceptance criteria: Critical journeys pass against real backend or controlled integration environment.
  - Tests required: Playwright suite.

---

## Documentation and Runbook Fixes

- [ ] DC-DOC-001 — Consolidate docs into canonical set.
  - Required fix: Keep canonical product, architecture, design, API, operations, audit docs; archive/remove duplicates.
  - Acceptance criteria: `docs/README.md` lists only canonical active docs and archives.
  - Tests required: documentation truth check.

- [ ] DC-DOC-002 — Archive completed migration docs.
  - Required fix: Move historical/completed migration docs to archive or delete after extracting still-relevant content.
  - Acceptance criteria: No stale migration doc appears canonical.
  - Tests required: docs index check.

- [ ] DC-DOC-003 — Generate API docs from OpenAPI.
  - Required fix: Replace duplicate route docs with generated docs or thin references.
  - Acceptance criteria: API docs cannot drift from OpenAPI.
  - Tests required: docs generation test.

- [ ] DC-DOC-004 — Remove customer-facing “Data Cloud + AEP” wording.
  - Required fix: Use “Data Cloud Action Plane powered by AEP runtime implementation.”
  - Acceptance criteria: Active customer-facing docs do not position AEP as separate product.
  - Tests required: terminology scan.

---

## Project Files Organization and Cleanup

- [ ] DC-CLEAN-001 — Remove backup file `delivery/sdk/build.gradle.kts.backup`.
  - Required fix: Delete or move to archive if needed.
  - Acceptance criteria: No `.backup` files in active source tree.
  - Tests required: file pattern scan.

- [ ] DC-CLEAN-002 — Organize Data Cloud scripts into subfolders.
  - Required fix: Group into `dev`, `ci`, `audits`, `docs`, `operations`, `migration`.
  - Acceptance criteria: Script README explains purpose and owner.
  - Tests required: script smoke test.

- [ ] DC-CLEAN-003 — Remove obsolete compatibility/deprecation artifacts.
  - Required fix: Remove or archive artifacts after canonical replacements exist.
  - Acceptance criteria: No active deprecated artifact without owner/deadline.
  - Tests required: deprecation scan.

- [ ] DC-CLEAN-004 — Add active-tree temp/backup/deprecated file scan.
  - Required fix: Fail CI on `.backup`, `.tmp`, `old`, `deprecated`, `legacy` in active product tree unless allowlisted.
  - Acceptance criteria: Active tree remains clean.
  - Tests required: scan script.

---

## P2 — Hardening

- [ ] DC-P2-001 — Improve disabled-surface remediation UX.
- [ ] DC-P2-002 — Add structured log redaction tests.
- [ ] DC-P2-003 — Add route-level accessibility tests.
- [ ] DC-P2-004 — Add documentation ownership metadata.

---

## P3 — Future Enhancements

- [ ] DC-P3-001 — Add richer operator remediation suggestions from Runtime Truth.
- [ ] DC-P3-002 — Add visual plane dependency graph in Operations.
- [ ] DC-P3-003 — Add audit/TODO burndown dashboard.
