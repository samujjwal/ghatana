# End-to-End Data Cloud TODO List

**Repository:** `samujjwal/ghatana`  
**Target commit/ref:** `0e609f7171965dbfc156fa4b43a77cd5e26b897b`  
**Source audit report:** `products/data-cloud/docs/audits/end-to-end-data-cloud-correctness-shared-libraries-audit.md`  
**Generated requested path:** `products/data-cloud/docs/audits/end-to-end-data-cloud-todo-list.md`

This TODO list contains every required action from the audit report. It is intentionally simple and implementation-oriented.

---

## P0 — Must Fix Before Production

### DC-P0-001 — Fail closed when production/container profile is missing

- [x] Update `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/DataCloudLauncherSettings.java`.
  - Area: production profile safety
  - File(s): `DataCloudLauncherSettings.java`, `DataCloudLauncher.java`, `products/data-cloud/deploy/Dockerfile`, Helm/K8s/Terraform deployment assets
  - Required fix: do not allow container/production-like deployments to silently default to `LOCAL` when `DATACLOUD_PROFILE` is absent.
  - Acceptance criteria: a production/container launch without explicit profile fails before creating clients/resources.
  - Tests required: launcher unit test for missing profile in container mode; container smoke test; Helm/K8s config test.

- [x] Set or enforce `DATACLOUD_PROFILE=production` in production deployment assets.
  - Area: deployment/operations
  - File(s): `products/data-cloud/deploy/**`
  - Required fix: configure production manifests/Helm values/Terraform variables so production profile is explicit.
  - Acceptance criteria: all production manifests contain or require an explicit production/staging profile.
  - Tests required: deployment manifest conformance test.

- [x] Add a production startup regression test that proves local/in-memory stores cannot be used in production profile.
  - Area: production readiness
  - File(s): launcher/bootstrap test suites
  - Required fix: test missing durable settings/DB/Kafka/auth failures in production.
  - Acceptance criteria: tests fail if production startup falls back to local/in-memory.
  - Tests required: production startup dependency tests.

---

## P1 — Must Fix Before Release

### DC-P1-001 — Fix local stack UI path

- [x] Update `products/data-cloud/scripts/run-local-stack.sh`.
  - Area: local developer experience
  - File(s): `products/data-cloud/scripts/run-local-stack.sh`
  - Required fix: change `UI_DIR="${PRODUCT_ROOT}/ui"` to `UI_DIR="${PRODUCT_ROOT}/delivery/ui"`.
  - Acceptance criteria: the documented local stack script starts both backend and Data Cloud UI from a clean checkout.
  - Tests required: shellcheck and smoke test.

- [x] Update `README.md` and `DEVELOPER_MANUAL.md` local-stack instructions if needed.
  - Area: docs
  - File(s): `products/data-cloud/README.md`, `products/data-cloud/DEVELOPER_MANUAL.md`
  - Required fix: ensure all local commands match the actual Gradle and UI paths.
  - Acceptance criteria: docs do not reference stale Data Cloud UI paths.
  - Tests required: doc command/path check.

### DC-P1-002 — Align all docs to the canonical Data Cloud/Action Plane boundary

- [x] Rewrite stale AEP ownership wording in `products/data-cloud/DEVELOPER_MANUAL.md`.
  - Area: product boundary/docs
  - File(s): `products/data-cloud/DEVELOPER_MANUAL.md`
  - Required fix: replace “Data Cloud does not own higher-level agent orchestration. AEP integrates...” wording with “AEP is the Action Plane runtime implementation inside Data Cloud.”
  - Acceptance criteria: active docs consistently describe one product boundary.
  - Tests required: doc truth scan.

- [x] Scan active docs for `Data Cloud + AEP`, `products/aep`, and peer-product AEP wording.
  - Area: docs/source-of-truth
  - File(s): `products/data-cloud/docs/**`, root active docs, CI docs
  - Required fix: update active docs or mark historical docs clearly historical.
  - Acceptance criteria: only historical docs retain old AEP migration wording.
  - Tests required: documentation truth check.

### DC-P1-003 — Make Data Fabric either production-real or explicitly preview-only

- [x] Review `products/data-cloud/delivery/ui/src/features/data-fabric/**`.
  - Area: Data Fabric UI/product readiness
  - File(s): `FEATURE_INDEX.md`, `README.md`, `INTEGRATION_GUIDE.md`, `TESTING_GUIDE.md`, route/component files
  - Required fix: remove “ready for production” claims unless live backend endpoints and integrated tests exist.
  - Acceptance criteria: Data Fabric documentation truthfully states production or preview status.
  - Tests required: doc truth check.

- [x] Add Runtime Truth/feature gate for `/fabric` and connector/fabric admin actions.
  - Area: UI runtime gating
  - File(s): `delivery/ui/src/routes.tsx`, Data Fabric feature files, capability service/gate definitions
  - Required fix: disable Data Fabric by default in production unless live fabric metrics capability is present.
  - Acceptance criteria: production users cannot see demo fabric metrics as live.
  - Tests required: UI feature-gate tests and production-off route tests.

- [x] If Data Fabric is intended for release, implement and test all claimed backend endpoints.
  - Area: backend/API/contracts
  - File(s): fabric/connector API handlers, OpenAPI, UI services
  - Required fix: implement live endpoints for all documented Data Fabric actions.
  - Acceptance criteria: every documented Data Fabric action has backend, contract, UI, and test coverage.
  - Tests required: API E2E and Playwright Data Fabric journey.

### DC-P1-004 — Normalize role/runtime gating for compatibility routes

- [x] Audit every compatibility alias in `products/data-cloud/delivery/ui/src/routes.tsx`.
  - Area: UI access control
  - File(s): `routes.tsx`
  - Required fix: list canonical route and every alias with required role/capability gates.
  - Acceptance criteria: canonical route and alias have identical access-control behavior.
  - Tests required: route matrix test.

- [x] Wrap aliases with the same `RoleProtectedRoute` and `RuntimeCapabilityRouteGate` as canonical routes.
  - Area: UI runtime truth
  - File(s): `routes.tsx`, route gate helpers
  - Required fix: do not mount protected pages directly through aliases.
  - Acceptance criteria: denied/disabled behavior is identical for canonical and alias routes.
  - Tests required: React route tests and Playwright role/capability tests.

### DC-P1-005 — Harden analytics result limits and timeouts

- [x] Defensively cap analytics submit-path response rows in `AnalyticsHandler`.
  - Area: analytics/performance
  - File(s): `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AnalyticsHandler.java`
  - Required fix: even if `AnalyticsQueryEngine` ignores `_rowLimit`, response rows must be capped to `MAX_ROW_LIMIT` and `truncated=true`.
  - Acceptance criteria: no query response can exceed configured maximum rows.
  - Tests required: large-result API/unit test where engine returns more rows than requested.

- [x] Enforce or remove `QUERY_TIMEOUT_MS`.
  - Area: analytics/operability
  - File(s): `AnalyticsHandler.java`, analytics engine/tracker classes
  - Required fix: implement timeout/cancellation behavior or remove unused constant and documentation claims.
  - Acceptance criteria: long-running analytics queries time out predictably with stable error envelope.
  - Tests required: timeout test and error-envelope test.

- [x] Tighten invalid `limit` validation if OpenAPI requires numeric limit.
  - Area: API validation
  - File(s): `AnalyticsHandler.java`, `contracts/openapi/data-cloud.yaml`
  - Required fix: either reject invalid limit with 400 or document fallback behavior explicitly.
  - Acceptance criteria: backend and OpenAPI agree on invalid limit semantics.
  - Tests required: invalid-limit API contract test.

### DC-P1-006 — Prove analytics UI/OpenAPI/SDK parity

- [x] Add OpenAPI route alignment tests for analytics query/result/plan/aggregate/explain/cancel.
  - Area: contract/API
  - File(s): contract tests, launcher route tests
  - Required fix: verify implemented routes match `data-cloud.yaml`.
  - Acceptance criteria: route drift test fails on missing/mismatched analytics endpoints.
  - Tests required: OpenAPI route alignment test.

- [x] Add UI client mapping tests for analytics responses.
  - Area: frontend/API
  - File(s): `delivery/ui/src/api/**`, query page/services
  - Required fix: verify UI reads `queryId`, rows, rowCount, traceId, limit, truncated, and error envelopes correctly.
  - Acceptance criteria: UI cannot silently break if backend response shape changes.
  - Tests required: Vitest API mapping tests.

- [x] Ensure unsupported cancellation is hidden/disabled in UI and surfaced through Runtime Truth/capability metadata.
  - Area: analytics/runtime truth
  - File(s): capability registry, UI query page, OpenAPI docs
  - Required fix: cancel button appears only when supported.
  - Acceptance criteria: users cannot trigger fake cancellation.
  - Tests required: supported/unsupported cancellation UI and API tests.

### DC-P1-007 — Fold AEP CI/product labels into Data Cloud Action Plane terminology

- [x] Update product-isolated CI labels.
  - Area: CI/product boundary
  - File(s): `.github/workflows/product-isolated-ci.yml`
  - Required fix: rename `aep` product output/labels to `data-cloud-action-plane` or fold into `data-cloud`.
  - Acceptance criteria: CI no longer represents AEP as a separate product boundary.
  - Tests required: CI config/lint test.

- [x] Update Gitea AEP CI naming and image naming if it remains active.
  - Area: CI/deployment
  - File(s): `.gitea/workflows/aep-ci.yml`, `.gitea/workflows/aep-cd.yml`, action-plane Docker/Helm metadata
  - Required fix: make names reflect Data Cloud Action Plane runtime.
  - Acceptance criteria: CI labels/images do not confuse product boundary.
  - Tests required: workflow lint.

### DC-P1-008 — Remove silent integration-test skips

- [x] Remove `|| echo "No integration tests configured, skipping."` from required action-plane/Data Cloud CI gates.
  - Area: CI/test authenticity
  - File(s): `.gitea/workflows/aep-ci.yml`, related CI workflows
  - Required fix: missing required integration tests must fail CI.
  - Acceptance criteria: CI cannot pass because a required integration suite is absent.
  - Tests required: workflow dry run or script-based CI lint.

- [x] Explicitly classify optional test suites behind named feature flags.
  - Area: CI/test policy
  - File(s): CI workflows, test docs
  - Required fix: optional suites need reason, owner, and release impact.
  - Acceptance criteria: optional skips are auditable and not confused with required gates.
  - Tests required: CI policy check.

### DC-P1-009 — Fix CODEOWNERS specificity and contracts ownership

- [x] Reorder `.github/CODEOWNERS` so specific Data Cloud rules come after the general Data Cloud rule.
  - Area: governance/review ownership
  - File(s): `.github/CODEOWNERS`, `.gitea/CODEOWNERS` if present
  - Required fix: ensure `planes/action`, `contracts`, and `planes/shared-spi` ownership rules are not overridden by `/products/data-cloud/`.
  - Acceptance criteria: owner resolution selects intended teams for action, contracts, and shared SPI changes.
  - Tests required: CODEOWNERS resolver test.

- [x] Add explicit ownership for `products/data-cloud/contracts/**`.
  - Area: contract governance
  - File(s): CODEOWNERS files
  - Required fix: contract changes require data/platform/API owner review.
  - Acceptance criteria: contract PRs require correct reviewers.
  - Tests required: CODEOWNERS resolver test.

### DC-P1-010 — Prove Runtime Truth universal consumption

- [x] Add `SurfaceRegistryCompositionTest`.
  - Area: Runtime Truth/backend
  - File(s): runtime truth registry/composition tests
  - Required fix: prove Data Cloud and Action Plane surfaces are registered with live/degraded/disabled/preview states.
  - Acceptance criteria: runtime truth includes all canonical surfaces and dependencies.
  - Tests required: registry composition test.

- [x] Generate or verify route/action gate metadata from Runtime Truth/capability schema.
  - Area: UI/runtime truth
  - File(s): feature-gate generator, UI route gate config
  - Required fix: avoid hand-maintained scattered `canAccess` logic.
  - Acceptance criteria: UI gates are generated or drift-checked against schema.
  - Tests required: feature gate drift test.

- [x] Add Playwright tests for live/degraded/disabled Runtime Truth states.
  - Area: UI E2E
  - File(s): Playwright tests
  - Required fix: verify unavailable surfaces are hidden/disabled with actionable messaging.
  - Acceptance criteria: no unavailable surface renders as live.
  - Tests required: browser E2E.

### DC-P1-011 — Execute and harden durable provider/restart tests

- [x] Run entity store durable provider tests at exact commit.
  - Area: data persistence
  - File(s): `extensions/plugins`, entity store tests
  - Required fix: prove PostgreSQL entity storage is tenant-isolated and durable.
  - Acceptance criteria: test artifact exists for exact commit.
  - Tests required: Testcontainers PostgreSQL tests.

- [x] Run event store durable provider tests at exact commit.
  - Area: event persistence
  - File(s): event store/provider tests
  - Required fix: prove Kafka/EventLog append/query/replay/stream durability.
  - Acceptance criteria: event data survives restart and remains tenant-scoped.
  - Tests required: Testcontainers Kafka tests.

- [x] Add/re-run workflow execution restart tests.
  - Area: workflow/action data durability
  - File(s): workflow execution capability tests
  - Required fix: prove snapshots/logs/checkpoints survive process restart in durable profile.
  - Acceptance criteria: workflow run history remains readable after restart.
  - Tests required: durable restart integration test.

### DC-P1-012 — Make Playwright E2E cross-platform

- [x] Replace Windows-only Playwright backend command with a cross-platform script.
  - Area: browser E2E
  - File(s): `products/data-cloud/delivery/ui/playwright.config.ts`
  - Required fix: use `node`/shell script or platform-independent Gradle invocation.
  - Acceptance criteria: Playwright runs on Linux CI, macOS, and Windows.
  - Tests required: Linux CI Playwright run.

- [x] Add production-like Playwright project using auth/durable backend where feasible.
  - Area: E2E authenticity
  - File(s): Playwright config/tests
  - Required fix: do not rely only on local in-memory profile for release gates.
  - Acceptance criteria: release E2E validates auth and runtime-truth gating.
  - Tests required: authenticated E2E.

### DC-P1-013 — Expand production mock/stub/shortcut scan

- [x] Extend `scripts/scan-production-placeholders.sh`.
  - Area: production readiness/static analysis
  - File(s): `scripts/scan-production-placeholders.sh`, allowlist config
  - Required fix: scan for `return []`, `return null`, `return true`, `console.log`, hardcoded demo/static live data, production imports from fixtures/test helpers, and mock flags in production UI builds.
  - Acceptance criteria: scanner covers the full no-mocks/stubs rubric.
  - Tests required: scanner fixture tests.

- [x] Add owner/expiry metadata for every allowlist entry.
  - Area: governance
  - File(s): placeholder/stub allowlists
  - Required fix: no permanent broad allowlist entries.
  - Acceptance criteria: CI fails on expired or ownerless allowlist entries.
  - Tests required: allowlist validation test.

---

## Product Boundary and Plane Fixes

- [x] Add architecture/import checks that Data/Event/Context/Governance/Intelligence planes do not import Action Plane implementation internals.
  - Area: architecture boundaries
  - File(s): ArchUnit tests, dependency scripts
  - Required fix: enforce plane dependency rules from `PLANE_ARCHITECTURE.md`.
  - Acceptance criteria: forbidden dependency direction fails CI.
  - Tests required: ArchUnit/cross-workspace dependency tests.

- [ ] Audit platform shared modules for Data Cloud/Action-specific semantics.
  - Area: shared library boundaries
  - File(s): `platform/java/agent-*`, `platform/java/workflow*`, `platform/java/messaging`, `platform/java/ai-integration`, `platform/java/data-governance`, `platform/contracts`
  - Required fix: move Data Cloud plane semantics into `products/data-cloud`; keep only generic primitives in platform.
  - Acceptance criteria: platform libraries remain genuinely reusable infrastructure.
  - Tests required: dependency graph and package-boundary tests.

- [x] Update active build/kernel docs that still show standalone AEP as a product.
  - Area: docs/product boundary
  - File(s): `gradle/PRODUCT_BUILD_GUIDE.md`, kernel/build docs, active onboarding docs
  - Required fix: describe Action Plane under Data Cloud, or mark historical.
  - Acceptance criteria: active docs match one product boundary.
  - Tests required: doc truth scan.

---

## Contract/API/SDK Fixes

- [x] Add retirement plan for `contracts/openapi/aep.yaml`.
  - Area: contracts
  - File(s): `products/data-cloud/contracts/README.md`, contract build
  - Required fix: define compatibility timeline and callers to migrate to `action-plane.yaml`.
  - Acceptance criteria: AEP compatibility spec is temporary and tracked.
  - Tests required: OpenAPI sync test.

- [x] Prove Data Cloud SDK generation uses `contracts/openapi/data-cloud.yaml`.
  - Area: SDK/contracts
  - File(s): `products/data-cloud/delivery/sdk/build.gradle.kts`, SDK tests
  - Required fix: ensure SDKs are generated from canonical Data Cloud contract, not stale aliases.
  - Acceptance criteria: SDK drift test fails if generated client diverges.
  - Tests required: SDK generation/drift test.

- [x] Ensure platform contract copies are non-canonical.
  - Area: contract source of truth
  - File(s): `platform/contracts/**`, product contract sync scripts
  - Required fix: prevent platform copies from becoming source of truth.
  - Acceptance criteria: product contract is canonical; platform copy is generated/synced.
  - Tests required: canonical contract check.

---

## Backend/Domain/Data/Event/Storage Fixes

- [x] Add defensive row capping for analytics result retrieval and submit paths consistently.
  - Area: backend analytics
  - File(s): `AnalyticsHandler.java`
  - Required fix: centralize row-limit application logic.
  - Acceptance criteria: both submit and get-result endpoints produce consistent `limit`, `rowCount`, and `truncated`.
  - Tests required: submit/get-result parity tests.

- [x] Add stable API error envelope tests.
  - Area: backend/API
  - File(s): all Data Cloud handlers
  - Required fix: response errors must include expected code/message/correlation/tenant/surface/retryable fields where contract requires.
  - Acceptance criteria: error shape is consistent across critical endpoints.
  - Tests required: API error-contract tests.

- [ ] Add governance destructive-operation E2E.
  - Area: governance/audit
  - File(s): trust/governance handlers, audit store
  - Required fix: dry-run/confirm/audit retention/redaction/purge flows.
  - Acceptance criteria: destructive actions require confirmation and emit audit evidence.
  - Tests required: API E2E + audit assertions.

---

## UI/UX and Frontend Fixes

- [x] Add route inventory test covering every route, alias, gate, role, and Runtime Truth state.
  - Area: UI routing
  - File(s): `routes.tsx`, route tests
  - Required fix: route matrix is executable.
  - Acceptance criteria: route drift or ungated aliases fail tests.
  - Tests required: Vitest route matrix.

- [x] Add user-facing degraded-state messaging for disabled/preview surfaces.
  - Area: UX/runtime truth
  - File(s): route gate components, page empty/degraded states
  - Required fix: unavailable surfaces should explain dependency and next action.
  - Acceptance criteria: no fake success state for unavailable surfaces.
  - Tests required: component + Playwright tests.

- [x] Ensure Data Cloud UI is included in root workspace build/test/typecheck commands.
  - Area: monorepo UI gates
  - File(s): root `package.json`, pnpm workspace config
  - Required fix: root scripts must include nested `products/data-cloud/delivery/ui`.
  - Acceptance criteria: `pnpm build/test/typecheck` from root covers Data Cloud UI.
  - Tests required: script/workspace conformance test.

---

## Shared Library, Abstraction, DRY, and Source-of-Truth Fixes

- [ ] Inventory duplicate DTO/enums/status values across Java, TypeScript, OpenAPI, Runtime Truth, and Zod/client code.
  - Area: DRY/contracts
  - File(s): contracts, UI types, backend DTOs
  - Required fix: choose canonical source and generate/adapt from it.
  - Acceptance criteria: no manually drift-prone duplicate status models.
  - Tests required: schema/client drift test.

- [ ] Move reusable pure UI components into `products/data-cloud/libs/ui-components` only when they have no app routing/store/service dependency.
  - Area: UI shared libraries
  - File(s): `delivery/ui`, `libs/ui-components`
  - Required fix: separate app-connected components from pure reusable components.
  - Acceptance criteria: reusable library has clean dependency direction.
  - Tests required: dependency/import lint.

- [ ] Keep Data Cloud product-specific logic out of generic platform/shared libraries.
  - Area: shared abstraction
  - File(s): platform modules and Data Cloud planes
  - Required fix: move product semantics into planes/extensions.
  - Acceptance criteria: platform packages are reusable by unrelated products.
  - Tests required: architecture boundary tests.

---

## Security, Privacy, Governance, and Tenant-Isolation Fixes

- [x] Add strict profile tests for missing tenant/auth across all critical Data Cloud endpoints.
  - Area: security
  - File(s): launcher/handler tests
  - Required fix: production/staging profiles reject missing/invalid auth and tenant context.
  - Acceptance criteria: all critical endpoints fail closed.
  - Tests required: API security tests.

- [x] Verify insecure mode cannot bind externally in production-like profiles.
  - Area: security/startup
  - File(s): launcher bootstrap/settings tests
  - Required fix: enforce loopback-only local insecure mode.
  - Acceptance criteria: external bind with insecure mode fails.
  - Tests required: startup config tests.

- [x] Add no-secrets-in-logs/traces tests for analytics, governance, connectors, plugins, and auth failures.
  - Area: privacy/observability
  - File(s): logging tests
  - Required fix: sanitize error responses and logs.
  - Acceptance criteria: secrets/PII do not appear in responses/logs.
  - Tests required: log capture tests.

---

## Observability, Operations, and Runtime Truth Fixes

- [ ] Add runtime truth evidence to every critical action response or diagnosable path where applicable.
  - Area: observability/runtime truth
  - File(s): handlers, UI, SDK
  - Required fix: surface state, dependency state, and correlation IDs are visible.
  - Acceptance criteria: operators can diagnose degraded/disabled actions.
  - Tests required: API/UI runtime-truth tests.

- [ ] Run and archive durable load suite output for exact commit.
  - Area: performance/ops
  - File(s): `products/data-cloud/scripts/run-durable-load-suite.sh`
  - Required fix: produce JSON metrics artifact in CI.
  - Acceptance criteria: load suite output is attached to build.
  - Tests required: durable load suite.

- [ ] Add recovery runbook validation smoke tests.
  - Area: operations
  - File(s): runbook tests/scripts
  - Required fix: commands in runbook are executable and current.
  - Acceptance criteria: runbook command drift fails CI.
  - Tests required: docs command smoke.

---

## Performance and Scalability Fixes

- [x] Add analytics large-result memory/backpressure tests.
  - Area: performance
  - File(s): analytics tests/perf tests
  - Required fix: prove bounded response/memory behavior under large result sets.
  - Acceptance criteria: no unbounded list serialization for large results.
  - Tests required: large-result benchmark/perf test.

- [ ] Add event append/replay/load tests per tenant.
  - Area: event scalability
  - File(s): event store tests/load suite
  - Required fix: validate append/replay throughput and tenant isolation.
  - Acceptance criteria: load results meet documented target or are clearly tracked.
  - Tests required: Kafka/EventLog load tests.

- [ ] Add UI route lazy-load and bundle budget checks.
  - Area: frontend performance
  - File(s): UI build config/CI
  - Required fix: ensure broad route surface does not produce unacceptable bundle growth.
  - Acceptance criteria: route chunks and bundle budgets are tracked.
  - Tests required: bundle analysis CI gate.

---

## Test Additions and Fixes

+ [x] Raise launcher/critical handler coverage above 50%.
  - Area: test coverage
  - File(s): `products/data-cloud/delivery/launcher/build.gradle.kts`
  - Required fix: raise critical module coverage thresholds and add branch coverage.
  - Acceptance criteria: critical code coverage gate matches production readiness bar.
  - Tests required: Jacoco verification.

- [x] Add API E2E tests for create/read/update/delete entity journey.
  - Area: end-to-end correctness
  - File(s): API E2E tests
  - Required fix: prove UI-relevant entity journey with real storage provider where possible.
  - Acceptance criteria: entity data persists and remains tenant scoped.
  - Tests required: API E2E + DB verification.

- [x] Add API E2E tests for event append/query/replay/stream journey.
  - Area: event correctness
  - File(s): API E2E tests
  - Required fix: prove event lifecycle.
  - Acceptance criteria: appended event can be queried/replayed/streamed with tenant isolation.
  - Tests required: API E2E + Kafka/EventLog verification.

- [ ] Add Playwright E2E for Home → Data → Query → Pipeline → Trust core journey.
  - Area: browser E2E
  - File(s): `delivery/ui/e2e/**`
  - Required fix: validate core no-mock journey against live backend.
  - Acceptance criteria: E2E uses `VITE_USE_MSW=false` and live API.
  - Tests required: Playwright E2E.

- [x] Add route alias security regression tests.
  - Area: UI security
  - File(s): UI tests
  - Required fix: direct deep links cannot bypass role/runtime gates.
  - Acceptance criteria: alias and canonical paths enforce same gates.
  - Tests required: route/security tests.

---

## Documentation and Runbook Fixes

- [ ] Commit this audit report under `products/data-cloud/docs/audits/end-to-end-data-cloud-correctness-shared-libraries-audit.md`.
  - Area: documentation/audit
  - File(s): new audit report
  - Required fix: replace old broad Data Cloud + AEP audit with exact-commit Data Cloud-scoped audit.
  - Acceptance criteria: audit path exists and names reviewed commit.
  - Tests required: doc truth check.

- [ ] Commit this TODO list under `products/data-cloud/docs/audits/end-to-end-data-cloud-todo-list.md`.
  - Area: implementation planning
  - File(s): new TODO list
  - Required fix: keep all audit tasks in one simple checklist.
  - Acceptance criteria: every actionable audit finding appears in TODO.
  - Tests required: audit TODO burndown check.

- [x] Mark older `docs/audits/end-to-end-product-correctness-audit.md` and `code-audits/ghatana-data-cloud-aep-*7432d846.md` as superseded or historical.
  - Area: documentation truth
  - File(s): old audit docs
  - Required fix: avoid stale broad-scope audit being mistaken for current state.
  - Acceptance criteria: old docs point to current Data Cloud scoped audit.
  - Tests required: doc truth scan.

- [x] Update Developer Manual documentation map if paths contain duplicated `docs/api/docs/api` or `docs/operations/docs/operations`.
  - Area: docs usability
  - File(s): `DEVELOPER_MANUAL.md`, docs directory structure
  - Required fix: ensure documented paths are accurate and canonical.
  - Acceptance criteria: all doc links resolve.
  - Tests required: link checker.

---

## P2 — Hardening

- [ ] Add a contract retirement issue for `GET /api/v1/capabilities`.
  - Area: Runtime Truth compatibility
  - Required fix: define migration to `/api/v1/surfaces`.
  - Acceptance criteria: compatibility endpoint has owner/date/removal criteria.
  - Tests required: compatibility tests.

- [ ] Add design-system/lint/a11y gates for all Data Cloud UI pages and Action Plane UI if present.
  - Area: UI consistency
  - Required fix: prevent raw/inconsistent controls from spreading.
  - Acceptance criteria: UI gate blocks nonconforming components.
  - Tests required: lint/a11y/visual tests.

- [x] Add import-boundary lint for `delivery/ui` not importing backend internals.
  - Area: frontend/backend boundary
  - Required fix: UI must use generated clients/adapters.
  - Acceptance criteria: direct backend imports fail CI.
  - Tests required: import lint.

- [ ] Add generated client drift checks for TypeScript, Java, and Python SDK outputs.
  - Area: SDK correctness
  - Required fix: generated clients match canonical OpenAPI.
  - Acceptance criteria: SDK drift fails build.
  - Tests required: generated-client drift check.

- [ ] Add docs lint for “ready for production,” “verified,” and “validated” claims without test evidence.
  - Area: documentation quality
  - Required fix: stop unsupported production-readiness claims.
  - Acceptance criteria: doc lint flags unsupported claims.
  - Tests required: doc lint tests.

---

## P3 — Future Enhancements

- [ ] Add one dashboard showing Runtime Truth, test-gate status, deployment profile, durable provider status, and recent audit evidence.
  - Area: operations UX
  - Acceptance criteria: operators have one place to verify release/runtime truth.

- [ ] Generate UI route/action gates directly from a canonical Runtime Truth/capability schema.
  - Area: UI automation/DRY
  - Acceptance criteria: no scattered hand-authored route capability mapping.

- [ ] Add automated architectural scorecard for Data Cloud planes and shared-library boundaries.
  - Area: architecture governance
  - Acceptance criteria: PRs show plane-boundary, dependency, contract, and runtime-truth scores.

- [ ] Add Data Cloud “evidence-first automation” UI pattern library.
  - Area: UX/shared components
  - Acceptance criteria: all automation/agentic actions expose why, data used, confidence, policy, audit, and override controls consistently.
