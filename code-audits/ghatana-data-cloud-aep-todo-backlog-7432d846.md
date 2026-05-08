> **HISTORICAL ARTIFACT** — This backlog was produced at commit `7432d846` and has been superseded by the active todo list at `products/data-cloud/docs/audits/end-to-end-data-cloud-todo-list.md`. Do not act on items here; confirm current status against the todo list.

# Data Cloud + AEP TODO Backlog

Repository: `samujjwal/ghatana`  
Commit audited: `7432d84601747ed3e095555c11a5f9471f0f8595`  
Scope: `products/data-cloud`, `products/aep`, and directly related architecture/design/audit docs.

## P0 — Must Fix Before Any Production Release

### DC-P0-001 — Fix Data Cloud analytics async/build correctness
- [ ] Inspect `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AnalyticsHandler.java`.
- [ ] Remove the suspicious `Promise.ofBlocking(...)` wrapper around asynchronous `request.loadBody().then(...)`.
- [ ] Rewrite `handleAnalyticsQuery` as a direct ActiveJ `Promise<HttpResponse>` chain.
- [ ] Ensure no nested `Promise<Promise<HttpResponse>>` or equivalent async shape can compile.
- [ ] Keep blocking work out of the ActiveJ event loop.
- [ ] Run `./gradlew :products:data-cloud:delivery:launcher:compileJava`.
- [ ] Run targeted analytics tests.
- [ ] Add valid-query, invalid-json, missing-query, blank-query, invalid-limit, analytics-engine-absent, and analytics-engine-exception tests.
- [ ] Verify response shape is stable and contract-compliant.

Acceptance criteria:
- Data Cloud launcher compiles.
- `POST /api/v1/analytics/query` behaves deterministically for success, validation, degraded, and failure cases.
- No blocking/async misuse remains.

Required tests:
- Compile test.
- API integration tests.
- Error-contract tests.

### DC-P0-002 — Remove production-path analytics placeholders
- [ ] Remove every production-path `FIXME: Not implemented` from `AnalyticsHandler.java`.
- [ ] Restore or correctly implement analytics broker/contract enrichment.
- [ ] Include trace/correlation metadata in analytics responses.
- [ ] Include cost/execution/capability metadata expected by the UI/API contract.
- [ ] Restore explain-plan metadata or remove the claim from UI/API docs.
- [ ] Replace fake streaming branch with real streaming or remove streaming claims.
- [ ] Update OpenAPI and UI clients to match actual analytics behavior.
- [ ] Add static scan for forbidden production placeholders.

Acceptance criteria:
- No critical production analytics path contains placeholder comments or fake behavior.
- OpenAPI, UI, and backend agree on response shape.
- Large-result behavior is honest: real streaming/pagination or bounded JSON with truncation metadata.

Required tests:
- Static placeholder scan.
- API contract tests.
- UI mapping tests.
- Large-result behavior tests.

## P1 — Must Fix Before Release

### DC-P1-001 — Implement or truthfully disable analytics query cancellation
- [ ] Review `DELETE /api/v1/analytics/queries/{queryId}`.
- [ ] Decide whether cancellation is supported in this release.
- [ ] If supported, implement cancellation in the analytics engine.
- [ ] If unsupported, mark `cancellation.supported=false` in capability registry.
- [ ] Hide/disable SQL Workspace cancel action when unsupported.
- [ ] Update OpenAPI and API documentation.
- [ ] Replace hardcoded live-looking `501 Not Implemented` behavior with capability-consistent unavailable behavior.
- [ ] Add UI messaging for unsupported cancellation.
- [ ] Add tests for supported and unsupported modes.

Acceptance criteria:
- UI, OpenAPI, capability registry, and backend agree.
- Users cannot trigger fake cancellation.

### DC-P1-002 — Production-gate Data Fabric preview/demo surface
- [ ] Review `/fabric` route and Data Fabric components.
- [ ] Find and isolate hardcoded/demo metrics.
- [ ] Ensure Data Fabric is disabled by default in production unless live fabric metrics capability is active.
- [ ] Show explicit preview banner when demo mode is intentionally enabled.
- [ ] Block direct backend access to incomplete fabric metrics in production.
- [ ] Add runtime capability/provenance labels: live, preview, degraded, unavailable.
- [ ] Update docs to state current product truth.

Acceptance criteria:
- Production users never see demo metrics as live operational data.
- Backend and UI gates agree.

### DC-P1-003 — Add real provider integration tests for workflow execution
- [ ] Locate the real workflow execution provider.
- [ ] Add tests that do not mock `WorkflowExecutionCapability`.
- [ ] Add tests that do not mock critical persistence through `DataCloudClient`.
- [ ] Execute real workflow through `POST /api/v1/pipelines/{pipelineId}/execute`.
- [ ] Verify persisted execution snapshot.
- [ ] Verify persisted logs.
- [ ] Verify list/get execution APIs use persisted data.
- [ ] Verify cancel/retry/rollback semantics.
- [ ] Verify checkpoint/restore semantics.
- [ ] Verify failure state persistence.
- [ ] Verify audit/metrics/log events.

Acceptance criteria:
- Tests prove real execution persistence, not just route shape.

### DC-P1-004 — Prove workflow execution restart durability
- [ ] Run workflow execution in `DATACLOUD_PROFILE=sovereign`.
- [ ] Persist execution snapshots/logs/checkpoints.
- [ ] Stop and restart with same `DATACLOUD_SOVEREIGN_DATA_DIR`.
- [ ] Verify execution history survives restart.
- [ ] Repeat with standard durable provider if available.
- [ ] Document durable vs non-durable profiles.
- [ ] Surface durability mode in operator UI if relevant.

Acceptance criteria:
- Durable profile proves restart-safe execution history.
- Local profile is clearly non-durable.

### DC-P1-005 — Add strict production tenant/auth tests
- [ ] Test missing `X-Tenant-Id` fails outside local/embedded profile.
- [ ] Test invalid tenant fails.
- [ ] Test missing API key/JWT fails outside local/embedded.
- [ ] Test invalid API key/JWT fails.
- [ ] Test cross-tenant isolation for entities.
- [ ] Test cross-tenant isolation for events.
- [ ] Test cross-tenant isolation for workflow executions/checkpoints/logs.
- [ ] Test cross-tenant isolation for analytics/reports.
- [ ] Test cross-tenant isolation for trust/governance actions.
- [ ] Test cross-tenant isolation for memory/context/RAG.
- [ ] Test insecure mode requires loopback-only binding.
- [ ] Add startup fail-closed tests for non-embedded profiles without auth.

Acceptance criteria:
- Production cannot inherit local insecure behavior.

### DC-P1-006 — Capability-gate every optional/degraded Data Cloud UI action
- [ ] Inventory optional surfaces: analytics, federated query, reports, AI assist, voice, learning, plugins, Data Fabric, alerts, memory, context, agents, settings.
- [ ] Map every optional action to `/api/v1/capabilities`.
- [ ] Hide or disable unavailable actions.
- [ ] Show actionable degraded/unavailable messages.
- [ ] Ensure backend returns capability-consistent status.
- [ ] Remove duplicated feature-flag logic where runtime capability should own truth.
- [ ] Add active/inactive capability tests.

Acceptance criteria:
- No unavailable optional feature appears active in UI.

### DC-P1-007 — Sanitize analytics errors
- [ ] Replace `500` responses that append `e.getMessage()`.
- [ ] Return stable error codes/messages.
- [ ] Log detailed server-side errors with correlation ID.
- [ ] Prevent query text, credentials, backend URLs, tenant secrets, or stack details from returning to clients.
- [ ] Update OpenAPI error schema.
- [ ] Update UI error handling.

Acceptance criteria:
- Client errors are safe and actionable; server logs remain diagnostic.

### DC-P1-008 — Enforce analytics row limits and large-result behavior end-to-end
- [ ] Ensure limits are pushed into the analytics engine/query layer, not only applied after rows load.
- [ ] Add default and max row limit config.
- [ ] Add deterministic pagination/sorting where applicable.
- [ ] Implement cursor paging, export, or real streaming for large result sets.
- [ ] Add UI handling for truncated/paged results.
- [ ] Add memory/performance regression tests.

Acceptance criteria:
- Large analytics queries cannot exhaust memory.
- UI clearly reports truncation/paging/streaming.

### DC-P1-009 — Complete or disable broader Trust Center policy CRUD lifecycle
- [ ] Inventory Trust Center actions.
- [ ] Identify incomplete policy CRUD.
- [ ] Implement policy create/read/update/delete if intended.
- [ ] Add policy validation.
- [ ] Add role/admin checks.
- [ ] Add audit events.
- [ ] Add dry-run/evaluation.
- [ ] Add rollback/versioning if needed.
- [ ] Hide or disable incomplete controls if not ready.
- [ ] Block direct backend access when unsupported.

Acceptance criteria:
- No incomplete governance CRUD appears live.

### DC-P1-010 — Add purge/redaction/governance audit assertions
- [ ] Test purge preview.
- [ ] Test purge confirmation token.
- [ ] Test redaction audit records.
- [ ] Test compliance refresh state.
- [ ] Test retention classification determinism.
- [ ] Test denied/failed governance actions are audited.
- [ ] Test tenant scoping.
- [ ] Test UI success/failure states match backend.

Acceptance criteria:
- Every destructive/privacy action is auditable.

### DC-P1-011 — Add Data Cloud onboarding E2E
- [ ] Test first-time onboarding.
- [ ] Test onboarding completion persistence.
- [ ] Test onboarding reset.
- [ ] Test role-aware shell mode.
- [ ] Test tenant-aware behavior.
- [ ] Test focus management and keyboard navigation.
- [ ] Test reload/resume.
- [ ] Test onboarding does not block authorized operators unexpectedly.

Acceptance criteria:
- Onboarding is deterministic and accessible.

### DC-P1-012 — Add Data Cloud CRUD journey E2E
- [ ] Test collection create.
- [ ] Verify created collection appears in `/data`.
- [ ] Test detail/read.
- [ ] Test edit/update.
- [ ] Test duplicate-submit prevention.
- [ ] Test validation failure.
- [ ] Test delete/archive if exposed.
- [ ] Test empty/loading/success/error states.
- [ ] Test cache invalidation.
- [ ] Test backend failure recovery.

Acceptance criteria:
- UI-to-backend-to-UI CRUD journey works fully.

### DC-P1-013 — Add Data Cloud entity/event/context/memory privacy tests
- [ ] Cross-tenant entity tests.
- [ ] Cross-tenant event tests.
- [ ] Cross-tenant context/RAG tests.
- [ ] Cross-tenant memory tests.
- [ ] Semantic similarity tenant-scope tests.
- [ ] PII redaction before RAG if applicable.
- [ ] Deleted/redacted records excluded from search/RAG.
- [ ] Memory TTL/deletion tests.
- [ ] Audit records for privacy actions.

Acceptance criteria:
- No tenant leakage across data, events, memory, context, or vector/RAG surfaces.

### DC-P1-014 — Replace stale audit documentation
- [ ] Replace `docs/audits/end-to-end-product-correctness-audit.md` with exact `7432d846...` audit.
- [ ] Ensure reviewed commit matches actual commit.
- [ ] Supersede stale `c4fc61...` findings.
- [ ] Add “last verified commit”.
- [ ] Add exact source evidence list.
- [ ] Add doc-truth CI/checklist.
- [ ] Decide whether shared path should be an index and product-specific paths should hold product audits.

Acceptance criteria:
- Audit docs do not contradict exact source.

### AEP-P1-001 — Complete AEP design-system migration
- [ ] Scan `products/aep/ui/src` for raw controls.
- [ ] Replace raw `<button>` with design-system `Button`.
- [ ] Replace raw `<input>` with `Input`.
- [ ] Replace raw `<select>` with `Select`.
- [ ] Replace checkboxes with `Checkbox`.
- [ ] Replace textareas with `TextArea`.
- [ ] Replace spinners with `Spinner`.
- [ ] Replace badges/tags with `Badge`/`Chip`.
- [ ] Replace tooltips with `Tooltip`.
- [ ] Refactor mobile nav shell button.
- [ ] Add lint rule banning raw interactive controls outside approved primitives.
- [ ] Add visual regression tests.
- [ ] Add accessibility tests.
- [ ] Update design adoption doc.

Acceptance criteria:
- AEP UI uses Ghatana design system consistently.

### AEP-P1-002 — Add AEP gateway integration suite
- [ ] Test `/health`.
- [ ] Test `/ready` success and backend unreachable.
- [ ] Test `/api/*` missing token.
- [ ] Test `/api/*` invalid token.
- [ ] Test tenant mismatch.
- [ ] Test valid proxy path.
- [ ] Verify forwarded auth, tenant, gateway, and correlation headers.
- [ ] Test backend unreachable 502 with correlation ID.
- [ ] Test SSE missing token.
- [ ] Test SSE invalid token.
- [ ] Test SSE tenant mismatch.
- [ ] Test SSE success forwarding.
- [ ] Test WS missing token.
- [ ] Test WS invalid token.
- [ ] Test WS header forwarding.
- [ ] Test WS backend failure.
- [ ] Test all error paths include correlation ID.
- [ ] Test CORS allowed/rejected origins.

Acceptance criteria:
- HTTP/SSE/WS gateway security and correlation are fully proven.

### AEP-P1-003 — Add AEP EventCloud production fail-closed tests
- [ ] Test `Aep.create(AepConfig.defaults())` fails without durable provider.
- [ ] Test actionable failure message.
- [ ] Test `AepConfig.forTesting()` allows in-memory.
- [ ] Test explicit allow flag works only for dev/test scenario.
- [ ] Test production launcher rejects accidental in-memory fallback.
- [ ] Test ServiceLoader provider success.
- [ ] Test deep health reports provider state.
- [ ] Test run history dependency state.

Acceptance criteria:
- Production cannot accidentally start with in-memory EventCloud.

### AEP-P1-004 — Prove AEP durable run-history restart behavior
- [ ] Configure AEP with Data Cloud/EventLogStore.
- [ ] Execute a run.
- [ ] Verify run list.
- [ ] Verify run detail.
- [ ] Stop AEP.
- [ ] Restart with same durable backend.
- [ ] Verify history/evidence/decisions/lineage remain.
- [ ] Verify in-memory mode reports non-durable state.
- [ ] Add UI runtime truth test.

Acceptance criteria:
- Production run history survives restart.

### AEP-P1-005 — Verify AEP pipeline update concurrency
- [ ] Inspect `PUT /api/v1/pipelines/{pipelineId}` controller.
- [ ] Verify `version`, `expectedVersion`, or `If-Match` is required.
- [ ] Test missing token returns `428 PIPELINE_VERSION_REQUIRED`.
- [ ] Test stale token returns `409 PIPELINE_VERSION_CONFLICT`.
- [ ] Ensure UI always sends version.
- [ ] Add conflict UI.
- [ ] Add retry/refetch flow.
- [ ] Verify publish/rollback versioning.
- [ ] Add concurrent edit tests.

Acceptance criteria:
- No stale pipeline write can overwrite current version.

### AEP-P1-006 — Verify AEP governance kill-switch MFA/role/audit
- [ ] Inspect governance controller/service.
- [ ] Require admin role.
- [ ] Require MFA/step-up in production.
- [ ] Gate degradation mode.
- [ ] Tenant-scope policy evaluation.
- [ ] Audit every kill-switch/degrade action.
- [ ] Audit denied attempts.
- [ ] Hide dangerous actions from unauthorized UI users.
- [ ] Require confirmation/reason.

Acceptance criteria:
- Critical governance controls are role/MFA/audit protected.

### AEP-P1-007 — Verify AEP HITL queue and escalation
- [ ] Test `configured=false` when HITL is absent.
- [ ] Test configured pending queue.
- [ ] Test `thresholdSeconds`.
- [ ] Test `autoEscalate`.
- [ ] Test tenant-specific timeout policies.
- [ ] Test manual escalation metadata.
- [ ] Test approve.
- [ ] Test reject.
- [ ] Test escalation audit event.
- [ ] Test UI empty state.
- [ ] Test UI permission denied.
- [ ] Test SLA breach state.

Acceptance criteria:
- HITL never fakes a queue and all decisions are auditable.

### AEP-P1-008 — Verify AEP agent execution security and marketplace rollback
- [ ] Inspect agent registration API.
- [ ] Inspect agent execution API.
- [ ] Verify security scan strength.
- [ ] Validate execution input.
- [ ] Tenant-scope agent memory.
- [ ] Verify marketplace install rollback.
- [ ] Prevent partial failed install state.
- [ ] Audit agent execution.
- [ ] Enforce restricted agent permissions.
- [ ] Add UI failure handling.

Acceptance criteria:
- Agent install/execute flows are secure, tenant-scoped, auditable, and rollback-safe.

## P2 — Hardening / Quality / Completeness

### DC-P2-001 — Improve analytics observability
- [ ] Structured logs with handler, tenant, queryId, traceId.
- [ ] Metrics for submitted/succeeded/failed/truncated queries.
- [ ] Metrics for unsupported cancellation.
- [ ] Latency by query type.
- [ ] Result size metrics.
- [ ] Sensitive-data logging test.
- [ ] Dashboard/runbook guidance.

### DC-P2-002 — Add Data Cloud health/readiness profile tests
- [ ] Test `/health`.
- [ ] Test `/ready`.
- [ ] Test `/live`.
- [ ] Test `/info`.
- [ ] Test `/metrics`.
- [ ] Verify degraded optional dependencies.
- [ ] Verify missing production dependency is not healthy.
- [ ] Verify capability registry aligns with readiness.

### DC-P2-003 — Add Data Cloud event stream soak/backpressure tests
- [ ] Concurrent SSE clients.
- [ ] Client disconnect cleanup.
- [ ] Stale connection cleanup.
- [ ] Backpressure behavior.
- [ ] Stream auth/tenant isolation.
- [ ] Stream metrics.

### DC-P2-004 — Improve workflow observability
- [ ] Metrics for started/completed/failed/cancelled/retried/rolled-back.
- [ ] Log correlation per execution ID.
- [ ] Audit events for execution actions.
- [ ] Checkpoint metrics.
- [ ] Retry/rollback metrics.
- [ ] Operator dashboard hooks.

### DC-P2-005 — Clarify workflow terminology and ownership
- [ ] Create terminology ADR.
- [ ] Data Cloud = data-local plugin execution.
- [ ] AEP = agentic orchestration/runtime.
- [ ] Update Data Cloud UI labels.
- [ ] Update AEP UI labels.
- [ ] Update README/API docs.
- [ ] Add route/contract naming tests.

### DC-P2-006 — Strengthen connector lifecycle
- [ ] Inventory connector routes/actions.
- [ ] Verify create.
- [ ] Verify edit.
- [ ] Verify delete or hide delete.
- [ ] Validate credentials.
- [ ] Secure secret storage.
- [ ] Prevent failed connector test from persisting bad state.
- [ ] Add connector audit events.
- [ ] Add capability-driven disabled states.

### DC-P2-007 — Add deterministic Data Explorer sorting/filtering/pagination
- [ ] Deterministic sort.
- [ ] Pagination for large collections.
- [ ] Filter validation.
- [ ] Empty/loading/error states.
- [ ] Large collection performance test.

### DC-P2-008 — Add semantic search/RAG safety tests
- [ ] Tenant-scoped vector search.
- [ ] No hidden-field leakage.
- [ ] PII redaction before RAG if applicable.
- [ ] Deleted/redacted records excluded.
- [ ] Low-confidence/no-result behavior.
- [ ] Retrieval provenance.

### DC-P2-009 — Harden settings storage
- [ ] Block in-memory settings in strict/production profiles.
- [ ] Audit settings writes.
- [ ] Validate settings.
- [ ] Add rollback/versioning if needed.
- [ ] Mask sensitive values.
- [ ] Add API/UI tests.

### AEP-P2-001 — Add correlation ID to every gateway error
- [ ] Add `x-correlation-id` header to all errors.
- [ ] Add `correlationId` field to JSON errors.
- [ ] Add correlation in WS logs/close handling where practical.
- [ ] Test 401/403/502/503/SSE/WS errors.

### AEP-P2-002 — Add AEP gateway metrics
- [ ] HTTP proxy request counts by status.
- [ ] Auth failure counts by reason.
- [ ] Tenant mismatch counts.
- [ ] SSE accepted/rejected counts.
- [ ] WS accepted/rejected/closed counts.
- [ ] Backend latency.
- [ ] Backend unreachable failures.
- [ ] Metrics exposure/integration.

### AEP-P2-003 — Stream/cap large gateway proxy responses
- [ ] Replace unbounded `backendRes.text()` for large responses or cap size.
- [ ] Support streaming where needed.
- [ ] Preserve content type.
- [ ] Preserve correlation headers.
- [ ] Add large-response load/memory tests.

### AEP-P2-004 — Add WS heartbeat/idle timeout/backpressure
- [ ] Backend/client heartbeat.
- [ ] Idle timeout.
- [ ] Max message size.
- [ ] Backpressure handling.
- [ ] Cleanup on backend close.
- [ ] Cleanup on client close.
- [ ] Soak test.

### AEP-P2-005 — Verify cost dashboard provenance
- [ ] Inspect data source.
- [ ] Prevent synthetic fallback as live.
- [ ] Label actual/estimated/unavailable.
- [ ] Empty state.
- [ ] Backend failure state.
- [ ] Budget threshold tests.
- [ ] UI tests.

### AEP-P2-006 — Verify learning/pattern studio correctness
- [ ] Inspect `PatternStudioPage`.
- [ ] Verify `/learn/episodes` redirect preserves intended tab.
- [ ] Ensure learning episodes are real or clearly unavailable.
- [ ] Verify auto-promotion requires evaluation gate.
- [ ] Verify low-confidence review items.
- [ ] Add route/data/empty/failure tests.

### AEP-P2-007 — Verify operation center long-running action behavior
- [ ] Inspect `OperationCenterPage`.
- [ ] Verify cancel/retry actions.
- [ ] Verify refresh for long-running operations.
- [ ] Prevent duplicate actions.
- [ ] Backend failure state.
- [ ] Permission denied state.
- [ ] Active/historical operation tests.

### AEP-P2-008 — Add AEP route and command-palette tests
- [ ] Canonical route render tests.
- [ ] Backward-compat redirect tests.
- [ ] Command-palette navigation.
- [ ] Breadcrumbs.
- [ ] Feature flags for breadcrumbs/command palette.
- [ ] 404 page.
- [ ] Mobile nav open/close.

### AEP-P2-009 — Add AEP privacy request E2E
- [ ] Create privacy request.
- [ ] Export/access request.
- [ ] Delete/erase request.
- [ ] Denied/invalid request.
- [ ] Audit event.
- [ ] Tenant boundary.
- [ ] UI success/failure states.

## P3 — Future Enhancements

### DC-P3-001 — Distributed workflow orchestration roadmap
- [ ] Document current single-process plugin limitation.
- [ ] Define distributed scheduler requirements.
- [ ] Define multi-worker coordination.
- [ ] Define lease/lock model.
- [ ] Define retry/dead-letter model.
- [ ] Define HA topology.
- [ ] Decide Data Cloud vs AEP ownership.

### DC-P3-002 — Promote Data Fabric to production
- [ ] Real fabric metrics API.
- [ ] Connector health model.
- [ ] Lineage/freshness model.
- [ ] Topology diffing.
- [ ] Anomaly/failure overlays.
- [ ] Governance overlays.
- [ ] Fabric metrics E2E.

### DC-P3-003 — Generate UI gates from capability schema
- [ ] Canonical capability schema.
- [ ] Generated TypeScript types.
- [ ] Generated route/action gate config.
- [ ] Generated OpenAPI docs.
- [ ] CI drift detection.

### AEP-P3-001 — Improve gateway deployment architecture
- [ ] Rate limiting.
- [ ] Request size limits.
- [ ] Structured logging.
- [ ] OpenTelemetry traces.
- [ ] Backend circuit breaker.
- [ ] Safe retry/backoff.
- [ ] Deployment runbook.

### AEP-P3-002 — Strengthen agent security validation
- [ ] Policy-based scanner.
- [ ] Dependency/license checks.
- [ ] Sandbox compatibility checks.
- [ ] Runtime permission model.
- [ ] Signed package verification.
- [ ] Install provenance.

## Cross-Product / Architecture / CI TODOs

### ARCH-P1-001 — Enforce no direct Data Cloud ↔ AEP peer imports
- [ ] Run ArchUnit cross-product tests.
- [ ] Run `scripts/check-cross-workspace-deps.mjs`.
- [ ] Assert Data Cloud does not import AEP.
- [ ] Assert AEP consumes Data Cloud only through public contracts/API/SPI/client.
- [ ] Document allowed integration points.
- [ ] Fix violations.

### ARCH-P1-002 — Confirm public contracts are canonical
- [ ] Validate Data Cloud OpenAPI.
- [ ] Validate AEP contracts OpenAPI.
- [ ] Validate AEP server OpenAPI.
- [ ] Ensure UI clients are generated or checked against OpenAPI.
- [ ] Remove stale local DTOs.
- [ ] Add contract compatibility gate.
- [ ] Add generated-client drift check.

### ARCH-P1-003 — Add production mock/stub scan to CI
- [ ] Scan production code for `mock`, `stub`, `fake`, `dummy`, `sample`, `fixture`, `placeholder`, `TODO`, `FIXME`, `HACK`, `TEMP`, `not implemented`, `coming soon`, `demo`, `hardcoded`, `return []`, `return null`, `return true`, `console.log`, `System.out.println`.
- [ ] Exempt tests/docs/approved dev paths.
- [ ] Fail CI for critical production placeholders.
- [ ] Produce report by product/severity.
- [ ] Add allowlist with owner and expiry.

### ARCH-P1-004 — Add release-truth checklist to PR template
- [ ] No production mocks/stubs.
- [ ] Optional features are capability-gated.
- [ ] Tenant/auth boundary tests included.
- [ ] Observability/correlation included.
- [ ] UI/backend/OpenAPI contract aligned.
- [ ] Restart durability covered if persistence touched.
- [ ] Design-system usage covered if UI touched.

### ARCH-P2-001 — Add documentation truth checks
- [ ] Audit docs include reviewed commit.
- [ ] Stale audit docs marked superseded.
- [ ] README claims match evidence level.
- [ ] Preview features marked preview.
- [ ] Production limitations explicit.
- [ ] Docs lint for absolute local paths.
- [ ] Docs lint for “verified” claims without test reference.

## Suggested Execution Order

1. Fix Data Cloud analytics P0s.
2. Run compile and targeted analytics tests.
3. Update OpenAPI/UI analytics contract.
4. Add workflow real-provider and restart tests.
5. Gate Data Fabric and optional UI actions.
6. Add tenant/auth strict profile tests.
7. Harden AEP gateway tests.
8. Add AEP EventCloud production tests.
9. Migrate AEP UI design-system controls.
10. Replace stale audit docs and add CI guards.
11. Run full backend, UI, E2E, migration, and mock/stub verification.

## Minimum Release Exit Criteria

- [ ] No P0 tasks remain.
- [ ] All P1 tasks complete or explicitly waived with owner/date/risk.
- [ ] `./gradlew :products:data-cloud:build` passes.
- [ ] `./gradlew :products:aep:build` passes.
- [ ] Data Cloud analytics contract tests pass.
- [ ] Data Cloud workflow provider/restart tests pass.
- [ ] Data Cloud production tenant/auth tests pass.
- [ ] AEP gateway integration tests pass.
- [ ] AEP EventCloud production fail-closed tests pass.
- [ ] AEP design-system lint/visual/a11y gates pass.
- [ ] OpenAPI compatibility gates pass.
- [ ] Migration/fresh-schema tests pass.
- [ ] Production mock/stub scan passes.
- [ ] Audit docs reflect exact current commit.
