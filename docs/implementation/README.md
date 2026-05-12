Below is a **production-grade implementation plan for YAPPC based on repo snapshot `5e03f330990461913b4b8963dbee39f5ac75143a`**. I am treating this as the **full codebase state at that commit**, not the commit diff. The target commit itself is only a changelog update referencing merge `c504db6`, so the plan is based on the YAPPC snapshot at that ref, not just the changed file. 

# YAPPC Implementation Plan

## Guiding principle

YAPPC should be implemented as **independently production-grade capability areas** that all plug into the same canonical spine:

1. Project / Product / App terminology.
2. Mounted lifecycle: Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve.
3. Artifact model with provenance and review.
4. Canvas → Page Document → Builder Document model.
5. Preview trust as a security boundary.
6. Governance trace across UI edits, generated output, backend actions, Data Cloud+AEP calls.
7. Typed contracts only for Data Cloud+AEP integration.

The canonical model already defines these boundaries and explicitly says YAPPC consumes merged Data Cloud+AEP capabilities through typed/generated contracts, while forbidding imports of internal platform runtime, memory, retrieval, analytics, telemetry, embeddings, and agent internals. 

---

# Phase 0 — Establish the production spine

## 0.1 Freeze commit-scoped implementation tracker

**Goal:** Stop repeating broad audits and convert the current state into a controlled execution tracker.

**Where**

* `products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md`
* `products/yappc/docs/architecture/YAPPC_CANONICAL_MODELS.md`
* `products/yappc/docs/api/route-manifest.yaml`
* `products/yappc/docs/api/openapi.yaml`

**Tasks**

1. Create/update a tracker with these sections:

   * Contract spine.
   * Access/scope.
   * Lifecycle/phase cockpit.
   * Data Cloud+AEP integration.
   * Generation/diff/review.
   * Canvas/page-builder/preview.
   * API client migration.
   * UI/UX dashboard.
   * Testing/quality gates.
   * Cleanup/docs.

2. For every item, track:

   * Area.
   * File/path.
   * Current issue.
   * Required fix.
   * Acceptance criteria.
   * Test coverage.
   * Owner status: `todo`, `in-progress`, `blocked`, `done`.

3. Mark this plan as based on snapshot:

   * `samujjwal/ghatana@5e03f330990461913b4b8963dbee39f5ac75143a`.

**Acceptance criteria**

* No task exists only in chat or scattered audit notes.
* Tracker is canonical and references exact files.
* Old one-off audit docs are not used as implementation source of truth.

---

# Phase 1 — Route, contract, and client spine

This should be first because every area depends on route parity, generated client correctness, and authorization consistency.

## 1.1 Make `route-manifest.yaml` the true API source of truth

The route manifest at this commit is no longer just a flat route list. It declares schema fields such as `method`, `path`, `auth`, `owner`, `boundary`, `operationId`, `scopes`, `auditEventType`, and `privacyClassification`, and states that it validates against OpenAPI and drives generated route authorization. 

**Where**

* `products/yappc/docs/api/route-manifest.yaml`
* `products/yappc/docs/api/openapi.yaml`
* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/generated/*`
* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/RouteAuthorizationRegistry.java`
* `products/yappc/frontend/web/src/clients/generated/api/*`

**Tasks**

1. Validate every manifest route has:

   * Matching OpenAPI path.
   * Matching HTTP method.
   * Matching `operationId`.
   * Matching auth/security scheme.
   * Matching request/response schema.
   * Matching generated client operation.
   * Matching generated authorization entry.

2. Fix operation naming consistency:

   * `operationId` should be lower camelCase or PascalCase consistently.
   * Current manifest comments say PascalCase, but examples and entries include lower camelCase such as `captureIntent`; choose one and enforce it.

3. Make route generation deterministic:

   * Build task reads manifest.
   * Generates `GeneratedRouteRegistry`.
   * Generates/validates OpenAPI route skeleton.
   * Generates client method coverage test.
   * Fails build on drift.

4. Add manifest validation rules:

   * Required fields present.
   * Scope format valid.
   * `auth=public` has empty scopes.
   * `auth=required` has non-empty scopes.
   * `boundary=DATA_CLOUD_AEP` never appears in YAPPC internal imports.
   * `privacyClassification=RESTRICTED` requires audit event.

**Acceptance criteria**

* A build fails if a route exists in controller code but not manifest.
* A build fails if manifest route does not exist in OpenAPI.
* A build fails if frontend client calls a route missing from OpenAPI.
* No route can bypass auth by being missing from the registry.

---

## 1.2 Complete generated client migration

The frontend API client already imports generated OpenAPI services and says it is an adapter layer for backward compatibility, but it still contains manual fetch helpers, scoped helper wrappers, and old compatibility methods. 

**Where**

* `products/yappc/frontend/web/src/lib/api/client.ts`
* `products/yappc/frontend/web/src/clients/generated/api/*`
* `products/yappc/frontend/apps/api/src/__tests__/openapi-contract.test.ts`
* `products/yappc/frontend/eslint-local-rules/*`
* `products/yappc/frontend/eslint.config.mjs`

**Tasks**

1. Split `client.ts` into:

   * `generatedClientAdapter.ts`
   * `legacyClientAdapter.ts`
   * `scopeHeaders.ts`
   * `errorMapper.ts`
   * `index.ts`

2. Migrate endpoint groups in order:

   * Auth.
   * Workspaces.
   * Projects.
   * Lifecycle.
   * Phase packet.
   * Dashboard actions.
   * Generate/diff/review.
   * Preview session.
   * Artifact import/review.
   * Audit/telemetry.

3. Delete manual endpoint wrappers once generated coverage exists.

4. Enforce no raw fetch:

   * Allowed only in HTTP infrastructure.
   * Disallowed in route components, hooks, page services, and random libs.

5. Normalize auth mode:

   * Current adapter bridges cookie-session generated types with old token-based expectations.
   * Pick final mode: cookie-session for web.
   * Remove fake empty token compatibility once consumers are migrated.

**Acceptance criteria**

* `client.ts` becomes a thin compatibility barrel or is removed.
* All frontend REST calls are generated-client-backed.
* No new raw fetch can be introduced outside approved infra.
* Contract tests fail for missing OpenAPI paths.

---

# Phase 2 — Access, scope, authorization, and permissions

This should run immediately after Phase 1 because current failures likely appear as inconsistent “access denied” behavior.

## 2.1 Make scope extraction consistent end to end

The route authorization registry now loads from generated route registry and supports exact + parameterized route matching. It extracts scope in priority order from path parameters, query params, and headers, while request body extraction is intentionally not done at auth filter level. 

**Where**

* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/RouteAuthorizationRegistry.java`
* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/RouteAuthorizationFilter.java`
* `products/yappc/frontend/web/src/lib/api/client.ts`
* `products/yappc/frontend/web/src/services/phase/PhaseCockpitDataService.ts`
* all controllers accepting `workspaceId`, `projectId`, `artifactId`, `runId`

**Tasks**

1. Define canonical scope transport:

   * Path params when part of route.
   * Query params for read routes.
   * Headers only for cross-cutting scope, not primary resource identity.
   * Body scope only for controller-level validation after authorization.

2. Remove mixed conventions:

   * `workspaceId` query in some calls.
   * `X-Workspace-Id` in others.
   * `scope` query helper that does not map to actual resource identity.

3. Add a shared scope DTO:

   * Java: `RequestScopeContext`.
   * TypeScript: `ScopeContext`.

4. Add tests for all permutations:

   * workspace read.
   * project read/write.
   * artifact read/write.
   * preview session create/validate.
   * generation review/apply/reject/rollback.
   * dashboard action execute.

5. Fix the existing TODO path:

   * `fetchProjectSnapshot` currently throws `"Workspace context is required - project access must be scoped (TODO-001)"`.
   * Replace with typed route-level requirement and user-facing error handling. 

**Acceptance criteria**

* Every project-scoped frontend call includes `workspaceId`.
* Backend rejects missing scope with a clear RFC-7807-style error.
* UI shows an actionable access/scope message, not a generic crash.
* No project data can be accessed without tenant + workspace + project validation.

---

## 2.2 Replace role heuristics with capability service

`PhasePacketServiceImpl` currently derives role from the first principal role and computes booleans like `canEdit`, `isAdmin`, and Enterprise-only rollback locally. 

**Where**

* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketServiceImpl.java`
* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/YappcAuthorizationService.java`
* `products/yappc/frontend/web/src/services/workspace/accessControl.ts`
* `products/yappc/frontend/libs/yappc-auth/src/*`

**Tasks**

1. Introduce backend `CapabilityEvaluationService`.
2. Evaluate capabilities from:

   * Tenant.
   * Workspace membership.
   * Project role.
   * Artifact ownership.
   * Subscription tier.
   * Feature flag.
   * Policy decision.
3. Return capability model in phase packet.
4. UI renders actions only from capability model.
5. Do not duplicate authorization logic in React components.

**Acceptance criteria**

* Capability decisions are backend-derived.
* Frontend does not infer sensitive permissions.
* Viewer/editor/admin/owner paths are tested.
* Cross-workspace access is explicitly denied and audited.

---

# Phase 3 — Lifecycle cockpit and phase packet productionization

## 3.1 Make phase packet fully real, no default/sample data

`PhasePacketServiceImpl` is labelled production, but several methods still return fallback/default/sample values: missing project returns generated `Project-{id}`, evidence and governance return empty lists with TODOs, activity feed returns a sample entry, health signals default to healthy, and next-phase logic is hardcoded. 

**Where**

* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketServiceImpl.java`
* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/*`
* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/gate/*`
* `products/yappc/frontend/web/src/services/phase/*`
* `products/yappc/frontend/web/src/routes/app/project/_phaseCockpit.tsx`

**Tasks**

1. Replace fake project fallback:

   * If project is missing, return 404.
   * If Data Cloud is degraded, return explicit degraded packet with `degradedReason`.
   * Do not synthesize project names/tier.

2. Replace hardcoded next phase:

   * Use transition config / lifecycle DAG.
   * Support legacy aliases only through compatibility adapter.
   * Return blocked/review/advance state from gate validator.

3. Replace empty evidence:

   * Query typed Data Cloud+AEP evidence endpoint.
   * Include `evidenceIds`, `traceId`, confidence, source artifact references.
   * Show evidence in UI where decisions are made.

4. Replace empty governance:

   * Query policy decision/audit records.
   * Include who/what/why/when.
   * Show policy blocks and review-required states in cockpit.

5. Replace sample activity:

   * Add audit query API.
   * Return real activity feed.
   * Include actor, phase, operation, outcome, timestamp, severity.

6. Replace default healthy signals:

   * Preview health from preview runtime.
   * Generation health from generation service/repository.
   * Runtime health from run/observe subsystem.
   * Data Cloud+AEP health included when phase needs it.

**Acceptance criteria**

* No sample/default project/activity/health/evidence appears in production mode.
* Phase cockpit can render degraded states explicitly.
* Every action shown has a backend reason and capability decision.
* Phase packet has contract tests and e2e UI tests.

---

## 3.2 Align frontend phase data with backend phase packet

The frontend still loads project snapshot and phase transition preview separately through `fetchProjectSnapshot` and `fetchPhaseTransitionPreview`.  The canonical model says lifecycle packet inputs include project snapshot, activity feed, readiness preview, backend dashboard classification, and typed phase actions. 

**Where**

* `products/yappc/frontend/web/src/services/phase/PhaseCockpitDataService.ts`
* `products/yappc/frontend/web/src/services/phase/usePhaseCockpitData.ts`
* `products/yappc/frontend/web/src/services/phase/PhaseCockpitContractBuilder.ts`
* `products/yappc/frontend/web/src/routes/app/project/_phaseCockpit.tsx`

**Tasks**

1. Make `/api/v1/phase/packet` the primary cockpit read model.
2. Keep old project/activity/transition calls only as transitional fallback.
3. Remove duplicated normalization once packet is complete.
4. Add typed frontend `PhasePacket` model generated from OpenAPI.
5. Render:

   * readiness.
   * blockers.
   * required/completed artifacts.
   * evidence.
   * governance.
   * actions.
   * health.
   * activity.
   * degraded platform status.

**Acceptance criteria**

* Cockpit route needs one primary packet load.
* UI does not recompute business readiness.
* All phases render from same packet shape.
* Missing/degraded fields render visibly and safely.

---

# Phase 4 — Data Cloud+AEP integration boundary

The canonical model now explicitly says Data Cloud+AEP is merged platform capability and YAPPC must use typed contracts only. It defines required YAPPC request context and required platform response metadata. 

## 4.1 Create typed platform client boundary

**Where**

* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/platform/*`
* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/*`
* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/generate/*`
* `products/yappc/docs/api/openapi.yaml`
* `products/yappc/docs/api/route-manifest.yaml`

**Tasks**

1. Define Java interfaces:

   * `PlatformExecutionClient`
   * `PlatformEvidenceClient`
   * `PlatformMemoryClient`
   * `PlatformTelemetryClient`
   * `PlatformPolicyClient`

2. Define common request context:

   * `tenantId`
   * `workspaceId`
   * `projectId`
   * `actorId`
   * `phase`
   * `operation`
   * `dataClassification`
   * `requestedAt`
   * `correlationId`
   * optional `artifactId`, `canvasNodeId`, `generationRunId`

3. Define common response metadata:

   * `status`
   * `confidence`
   * `confidenceReason`
   * `traceId`
   * `evidenceIds`
   * `policyDecisionId`
   * `degraded`
   * `degradedReason`
   * `createdAt`
   * `completedAt`
   * optional `runId`, `memoryRecordIds`, `searchResultIds`

4. Add adapters:

   * Real DataCloud client adapter.
   * AEP execution adapter.
   * Test fake only in test source set.

5. Add architecture rule:

   * YAPPC may import platform client contracts.
   * YAPPC may not import platform internals.

**Acceptance criteria**

* No YAPPC service imports internal AEP/Data Cloud modules directly.
* Platform failure returns degraded metadata, not silent empty list.
* Contract tests enforce required request/response fields.

---

# Phase 5 — Generation, diff, review, rollback

Generation has improved at this commit: it persists a generation run, adds explicit context, supports AI degraded fallback, records review decisions, and performs rollback safety checks. But it still has production gaps: TODO for loading actual intent, handcrafted JSON serialization, content references without content loading, and simplified diff logic that cannot compute actual line diffs from content refs. 

## 5.1 Persist real artifact content and provenance

**Where**

* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/generate/GenerationServiceImpl.java`
* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/GenerationRunRepository.java`
* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/YappcArtifactRepository.java`
* `products/yappc/docs/api/openapi.yaml`
* frontend generation review route/components

**Tasks**

1. Introduce `GeneratedArtifactContentRepository`.
2. Store actual content with:

   * artifactId.
   * contentHash.
   * path.
   * MIME/language.
   * size.
   * source prompt hash.
   * generator version.
   * AI/degraded mode.
   * provenance metadata.
3. Make `contentRef` resolvable.
4. Add endpoint to fetch content safely.
5. Add content redaction rules for restricted artifacts.

**Acceptance criteria**

* Generated files are not anonymous content references.
* UI can load actual generated content.
* Audit records can prove which input produced which output.

---

## 5.2 Replace simplified diff with real diff engine

**Where**

* `GenerationServiceImpl.computeDiff`
* `ArtifactDiff`
* generated OpenAPI schemas
* frontend diff viewer

**Tasks**

1. Load old/new content by contentRef.
2. Use proper line diff library or internal deterministic diff utility.
3. Compute:

   * added/deleted/modified regions.
   * line ranges.
   * ownership: AI/user/system.
   * provenance per region.
4. Preserve user edits across review.
5. Store diff snapshot with generation run.

**Acceptance criteria**

* Diff displays actual line-level content.
* Applying edited diffs preserves provenance.
* Rollback can restore previous content.
* Tests cover added, deleted, modified, renamed, empty, binary/unrenderable files.

---

## 5.3 Productionize review decisions

**Where**

* `GenerationServiceImpl.reviewDecision`
* `GenerationRunRepository`
* frontend review UI
* audit events

**Tasks**

1. Require:

   * actorId.
   * projectId.
   * workspaceId.
   * tenantId.
   * runId.
   * reason for reject/rollback.
   * review provenance.
2. Apply decision states:

   * `REVIEW_PENDING`
   * `APPROVED`
   * `REJECTED`
   * `ROLLED_BACK`
   * `APPLY_FAILED`
   * `ROLLBACK_FAILED`
3. Make review action idempotent:

   * duplicate apply does not reapply.
   * duplicate rollback does not corrupt state.
4. Store user edits as structured JSON using ObjectMapper, not manual string building.
5. Emit audit and metric events.

**Acceptance criteria**

* Review state machine is explicit and tested.
* User edits are queryable and validated.
* Rollback is safe, auditable, and reversible where possible.

---

# Phase 6 — Canvas, page builder, artifact import, and preview trust

The canonical builder model separates Canvas node, Page document, and Builder document. It also says imported artifacts/components must declare preview trust and data classification, and untrusted inputs must not mutate canvas/page documents before validation. 

## 6.1 Harden page artifact persistence

**Where**

* page artifact API/controllers
* frontend page builder libs
* canvas node/page document adapters
* `products/yappc/docs/api/openapi.yaml`

**Tasks**

1. Define `PageArtifactDocument` contract:

   * artifactId.
   * projectId/workspaceId/tenantId.
   * builderDocument.
   * registry version.
   * operation log.
   * sync state.
   * preview trust.
   * data classification.
2. Add optimistic concurrency:

   * documentVersion.
   * etag.
   * conflict response.
3. Add operation log entries:

   * save.
   * import.
   * reload.
   * overwrite.
   * migration.
   * governance decision.
4. Add migration path for old builder documents.

**Acceptance criteria**

* No builder save can overwrite silently.
* Every mutation has operation log.
* UI can resolve conflicts with clear choices.

---

## 6.2 Productionize import/decompile/residual islands

**Where**

* `/api/v1/yappc/artifact/import-source`
* artifact compiler
* frontend import UI
* residual island review APIs
* registry candidate APIs

**Tasks**

1. Add source import job lifecycle:

   * submitted.
   * validating.
   * decompiling.
   * mapping.
   * residual-review-required.
   * completed.
   * failed.
2. Validate untrusted input before document mutation.
3. Require runtime health check before import.
4. Map known components to registry.
5. Create residual islands for unknown/untrusted components.
6. Add review flows:

   * accept residual.
   * map to registry candidate.
   * reject/remove.
   * quarantine.
7. Attach preview trust to every imported node.

**Acceptance criteria**

* Import never executes untrusted code directly.
* Residual islands are visible and reviewable.
* Preview is blocked or isolated when trust is insufficient.

---

# Phase 7 — UI/UX: no cognitive load, full visibility

## 7.1 Rebuild dashboard around backend action classification

**Where**

* `products/yappc/frontend/web/src/routes/dashboard.tsx`
* dashboard action services/components
* `projects.dashboardActions`
* phase cockpit route links

**Tasks**

1. Dashboard sections:

   * blocked work.
   * review required.
   * safe to continue.
   * active generation/runs.
   * recent activity.
   * degraded integrations.
2. Every card must answer:

   * What is happening?
   * Why does it matter?
   * What is the safest next action?
   * Who/what is blocking it?
3. Cards route to exact phase cockpit.
4. Avoid AI-branded clutter; show outcome, confidence, evidence.

**Acceptance criteria**

* User can start from dashboard and reach every required phase/action.
* No page requires understanding internal AI/system concepts.
* Every action is permission-gated from backend capabilities.

---

## 7.2 Make phase cockpit consistent across all phases

**Where**

* `frontend/web/src/routes/app/project/_phaseCockpit.tsx`
* `frontend/web/src/services/phase/*`
* shared UI components

**Tasks**

1. Standard layout:

   * header: phase, readiness, health.
   * primary action.
   * blockers.
   * evidence.
   * required/completed artifacts.
   * governance.
   * activity.
   * advanced details collapsed.
2. Use same interaction model for all phases.
3. Avoid phase-specific UI forks unless behavior truly differs.
4. Add accessibility and keyboard coverage.

**Acceptance criteria**

* Intent/Shape/Validate/Generate/Run/Observe/Learn/Evolve all render with same shell.
* Missing data surfaces as degraded/empty states, not broken UI.
* Phase actions are clear and minimal.

---

# Phase 8 — Scaffold, packs, templates, and generated app quality

## 8.1 Treat scaffold as production generator, not template copier

**Where**

* `products/yappc/core/scaffold/api/*`
* `products/yappc/core/scaffold/packs/*`
* `products/yappc/docs/api/route-manifest.yaml`
* frontend scaffold/generate UI

**Tasks**

1. Define pack contract:

   * metadata.
   * language.
   * platform.
   * build system.
   * variables.
   * required features.
   * dependency graph.
   * license compatibility.
   * generated files.
   * tests.
2. Validate pack before use.
3. Validate variables with schema.
4. Add generated output validation:

   * compile.
   * test.
   * lint.
   * dependency audit.
   * license check.
5. Add provenance from lifecycle artifacts to generated scaffold files.

**Acceptance criteria**

* A generated project builds/tests in CI.
* Pack conflicts are detected before write.
* Generated output has provenance and rollback metadata.

---

# Phase 9 — Observability, audit, and operations

## 9.1 Make observability consistent across services

**Where**

* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/common/ServiceObservability.java`
* generation/lifecycle/run/observe/phase services
* `products/yappc/prometheus.yappc.yml`
* ops docs

**Tasks**

1. Standard metric tags:

   * tenantId.
   * workspaceId.
   * projectId.
   * phase.
   * operation.
   * outcome.
   * degraded.
2. Standard audit event shape:

   * actor.
   * target.
   * phase.
   * operation.
   * data classification.
   * preview trust.
   * correlationId.
   * traceId.
3. Add dashboards:

   * phase packet latency.
   * generation success/fallback/failure.
   * preview blocks.
   * policy decisions.
   * route authorization denials.
   * Data Cloud+AEP degraded states.

**Acceptance criteria**

* Every critical mutation has audit event.
* Every external/platform call has metrics.
* Degraded mode is visible in UI and ops.

---

# Phase 10 — Security and preview runtime

## 10.1 Enforce preview trust as policy

**Where**

* `PreviewSecurityPolicy`
* preview session APIs
* page builder preview UI
* artifact import flow
* route manifest/openapi

**Tasks**

1. Require preview context:

   * tenantId.
   * workspaceId.
   * projectId.
   * artifactId.
   * userId.
   * previewTrust.
   * dataClassification.
2. Block untrusted execution.
3. Isolate semi-trusted preview.
4. Require explicit acknowledgement for restricted data.
5. Audit preview session creation/validation.

**Acceptance criteria**

* No untrusted imported code executes directly.
* Preview errors and policy blocks show in Observe.
* Preview tokens expire and are validated.

---

# Phase 11 — Testing and quality gates

## 11.1 Required test suites per area

**Where**

* Java unit/integration tests under `products/yappc/core/**/src/test`
* frontend Vitest tests
* Playwright e2e tests
* OpenAPI/route parity tests
* architecture fitness tests

**Tasks**

1. Contract tests:

   * manifest ↔ OpenAPI.
   * OpenAPI ↔ generated client.
   * manifest ↔ generated route registry.
   * frontend used routes ↔ OpenAPI.
2. Authorization tests:

   * public route.
   * missing principal.
   * missing workspaceId/projectId/artifactId.
   * unauthorized workspace.
   * role/tier restrictions.
3. Phase packet tests:

   * all phases.
   * blockers.
   * evidence.
   * governance.
   * degraded platform.
4. Generation tests:

   * AI success.
   * AI degraded fallback.
   * diff.
   * review apply/reject/rollback.
   * user edits.
5. UI e2e:

   * dashboard → phase cockpit.
   * phase action → backend call.
   * generate → review → apply.
   * import → residual review.
   * preview trust block.
6. Build gates:

   * typecheck.
   * lint.
   * no raw fetch.
   * no forbidden imports.
   * no production TODO/fallback markers.

**Acceptance criteria**

* No touched area ships without meaningful unit + integration/contract/e2e coverage.
* Tests verify behavior, not just rendering.
* CI fails on contract drift and forbidden dependencies.

---

# Phase 12 — Cleanup and consolidation

## 12.1 Remove stale, duplicate, and compatibility code

**Where**

* `products/yappc/docs/archive/*`
* old audit docs.
* old generated clients.
* legacy API wrappers.
* invalid imports.
* dead packages.

**Tasks**

1. Keep only canonical docs:

   * vision.
   * architecture.
   * API/contracts.
   * security.
   * operations.
   * testing.
   * implementation tracker.
2. Archive or delete old one-off audit reports.
3. Remove compatibility adapters after generated client migration.
4. Remove fake sample data and production TODOs.
5. Enforce package boundaries:

   * `@ghatana/*` only for platform generic code.
   * `@yappc/*` only for YAPPC product-specific code.
   * app imports through app barrels where required.

**Acceptance criteria**

* No stale docs drive implementation.
* No duplicate clients or route definitions.
* No generated/sample/fake data remains in production paths.

---

# Suggested execution order

## Sprint 1 — Contract and access spine

1. Route manifest validation.
2. OpenAPI parity.
3. Generated route registry parity.
4. Generated frontend client migration plan.
5. Scope model and authorization tests.
6. Fix `TODO-001` workspace scoping path.

## Sprint 2 — Phase packet as canonical read model

1. Backend phase packet contract.
2. Replace project fallback/default data.
3. Real lifecycle transition config.
4. Real capability model.
5. Frontend cockpit consumes packet.
6. Phase packet e2e tests.

## Sprint 3 — Data Cloud+AEP typed integration

1. Platform client contracts.
2. Evidence retrieval.
3. Policy decisions.
4. Degraded state handling.
5. Memory/telemetry write hooks.
6. Forbidden import architecture tests.

## Sprint 4 — Generation/review/rollback

1. Artifact content repository.
2. Real diff engine.
3. Review state machine.
4. Structured user edits.
5. Rollback restore path.
6. UI review flow.

## Sprint 5 — Canvas/page/import/preview

1. Page artifact contract.
2. Operation log.
3. Import job lifecycle.
4. Residual island review.
5. Preview trust enforcement.
6. Canvas/page e2e.

## Sprint 6 — UI polish, observability, cleanup

1. Dashboard simplification.
2. Phase cockpit consistency.
3. Audit/metrics dashboards.
4. Security hardening.
5. Remove legacy code/docs.
6. Final regression suite.

---

# Highest-priority TODO list

| Priority | TODO                                                                                      | Where                                                                   |
| -------- | ----------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| P0       | Make route manifest/OpenAPI/generated client/generated auth registry fully parity-checked | `docs/api`, `core/yappc-services`, `frontend/web/src/clients/generated` |
| P0       | Normalize tenant/workspace/project/artifact scope transport                               | `RouteAuthorizationRegistry.java`, `client.ts`, controllers             |
| P0       | Replace phase packet fake/default/sample data with real/degraded states                   | `PhasePacketServiceImpl.java`                                           |
| P0       | Make phase cockpit consume canonical phase packet                                         | `frontend/web/src/services/phase`, `_phaseCockpit.tsx`                  |
| P1       | Complete generated frontend client migration and remove manual fetch wrappers             | `frontend/web/src/lib/api/client.ts`                                    |
| P1       | Add platform typed clients for Data Cloud+AEP boundary                                    | `core/yappc-services/src/main/java/com/ghatana/yappc/platform`          |
| P1       | Persist generated artifact content and real provenance                                    | `GenerationServiceImpl.java`, artifact repositories                     |
| P1       | Replace simplified diff with real content-backed diff                                     | generation service + frontend diff viewer                               |
| P1       | Make review/apply/reject/rollback a tested state machine                                  | generation service, repository, UI                                      |
| P2       | Harden canvas/page document/preview trust/import flows                                    | page builder, artifact import, preview APIs                             |
| P2       | Replace frontend permission inference with backend capabilities                           | phase packet, dashboard, action rendering                               |
| P2       | Add no-raw-fetch and forbidden-platform-import lint/architecture rules                    | frontend eslint + Java ArchUnit                                         |
| P3       | Consolidate docs and remove legacy/temporary audit clutter                                | `products/yappc/docs`                                                   |

The practical first move is **not** to start with UI or generation. Start with the **contract/access spine**, because current YAPPC already has the right canonical direction, but the implementation still has adapter seams, compatibility paths, simplified/default service behavior, and scope inconsistencies that will cause every other area to regress if left unresolved.
