# Data Cloud, AEP, Audio-Video Task-by-Task Implementation Plan

Date: 2026-04-17
Author: Codex
Scope: `products/data-cloud`, `products/aep`, `products/audio-video`, shared security/auth surfaces

## Goal

Turn the current system from broad, partially misleading product surface area into a narrower, truthful, hardened platform with credible end-to-end proof.

## Planning assumptions

- `Data Cloud` is the primary product bet.
- `AEP` remains the execution/runtime companion product.
- `audio-video` should be treated as experimental until real production proof exists.
- Tasks are ordered by trust impact, not by ease.

## Status Snapshot

### Completed before this pass

- `0.1` Auth gateway rejects missing or weak non-local `PLATFORM_JWT_SECRET` values and has regression coverage.
- `0.2` AEP OpenAPI, runtime copy, drift tests, and AEP operator-facing docs were aligned with the real HTTP surface.
- `0.5` AI Voice stem separation no longer fabricates successful separated stems when dependencies are missing.
- `1.1` Data Cloud workflow execution snapshots and logs are persisted through Data Cloud storage and survive restart.
- `1.2` Data Cloud documentation now distinguishes metadata CRUD, launcher runtime execution, and durable orchestration boundaries.
- `1.3` Data Cloud end-to-end workflow coverage now exercises the real runtime plugin path instead of an in-test fake engine.
- `1.4` AEP golden-path coverage now treats core surfaces as reachable `200`/valid runtime behavior rather than accepting broad placeholder failures.
- `2.1` AEP governance UI panels are backend-backed for compliance, tenancy, and audit summaries.
- `2.2` AEP run detail UI now uses backend-backed lineage, decision, and policy evidence arrays.
- `2.3` AEP README and API docs were rewritten around the execution-runtime product identity.
- `4.2` AI Voice desktop package exposes reproducible unit, e2e, and integration test entrypoints.

### Completed in this pass

- `3.1` Data Cloud governance proof was verified as already backed by real deletion, redaction, audit, and tenant-isolation evidence; the plan had lagged the repo state.
- `3.2` Data Cloud capability registry truth is now normalized and surfaced in operator-facing Insights and SQL workspace panels, with degraded and unavailable query dependencies called out explicitly.
- `3.3` Data Cloud README now uses an evidence-linked implementation matrix instead of broad binary readiness labels.
- `3.4` Added explicit frontend capability-registry contract coverage and a dedicated workspace script for Data Cloud UI contract checks.
- `4.1` AI Voice documentation now states the long-term boundary clearly: product-scoped experimental creator app, not a stable shared platform contract.
- `4.3` AI Voice Studio now carries an explicit runtime mode model (`production` / `degraded` / `demo`) through desktop state and blocks export outside production mode.
- `5.1` Scoped artifact hygiene audit found no tracked `dist/`, `build/`, or `node_modules/` paths under `products/data-cloud`, `products/aep`, or `products/audio-video`; no removals were needed in scope.
- `5.3` Runtime capability-truth dashboards now appear in Data Cloud operator-facing UI surfaces.
- `0.3` Data Cloud user-facing truthfulness gaps were closed for plugin details, smart workflow builder, SQL AI assist wording, settings unavailable states, autonomy timeline, and spotlight surfaces.
- `0.4` AI Voice docs were downgraded from unsupported production-readiness language to experimental/internal preview wording.
- `5.2` Repo governance now includes a shared product truthfulness policy linked from the main governance documentation.

### Still open

- No remaining open items were identified inside the scoped five-phase plan after reconciling plan drift against the current repository state.

---

## Phase 0: Truthfulness, correctness, and security blockers

### Task 0.1: Remove default platform JWT fallback in auth-gateway
- Area: `shared-services/auth-gateway`
- Problem: Cross-product token issuance still falls back to a default secret.
- Evidence:
  - `shared-services/auth-gateway/src/main/java/com/ghatana/services/auth/AuthGatewayLauncher.java`
- Implementation:
  - Require `PLATFORM_JWT_SECRET` in all non-local profiles.
  - Fail startup if the value is missing or weak.
  - Add profile-aware validation matching `JWT_SECRET` rigor.
  - Update launcher tests to cover fail-fast behavior.
- Done when:
  - No default secret remains in runtime code.
  - Startup fails in staging/production profiles without explicit secret configuration.
  - Tests prove rejection of missing/weak secret.

### Task 0.2: Rewrite AEP OpenAPI to match the real server
- Area: `products/aep/server`
- Problem: Published contract still describes an older event/pattern product.
- Evidence:
  - `products/aep/server/src/main/resources/openapi.yaml`
  - `products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java`
- Implementation:
  - Inventory all routes registered in `AepHttpServer`.
  - Replace stale spec sections with real route families:
    - agents
    - marketplace
    - runs
    - hitl
    - learning
    - governance/compliance
    - deployments
    - analytics/reporting
  - Align request and response schemas with actual controller payloads.
  - Add a strict route/spec drift test that fails on missing documented routes.
- Done when:
  - OpenAPI covers all public routes.
  - Drift test fails on omissions in either direction.
  - UI/generated clients can rely on the spec.

### Task 0.3: Remove mock operational data from Data Cloud production UI paths
- Area: `products/data-cloud/ui`
- Problem: Live-looking UI uses mock data in shipped product code.
- Evidence:
  - `products/data-cloud/ui/src/components/brain/AutonomyTimeline.tsx`
  - `products/data-cloud/ui/src/components/brain/SpotlightRing.tsx`
  - `products/data-cloud/ui/src/pages/SettingsPage.tsx`
- Implementation:
  - Replace mock arrays and delayed promise shims with real API-backed hooks.
  - If backend support is missing, show explicit unavailable/degraded states.
  - Remove demo tenant labels and fake timestamps from product code.
  - Add UI tests asserting unavailable states instead of simulated live data.
- Done when:
  - No mock operational truth remains in non-test frontend code.
  - Missing backend capabilities render honest empty/error states.

### Task 0.4: Reclassify audio-video AI Voice Studio maturity
- Area: `products/audio-video/modules/intelligence/ai-voice`
- Problem: Docs claim production readiness without credible runtime proof.
- Evidence:
  - `products/audio-video/modules/intelligence/ai-voice/README.md`
  - `products/audio-video/modules/intelligence/ai-voice/apps/desktop/package.json`
- Implementation:
  - Change status language from production-ready to experimental/internal preview.
  - Add a capability matrix distinguishing:
    - real ML-backed
    - fallback/degraded
    - placeholder/not releaseable
  - Remove unsupported performance claims unless backed by reproducible benchmarks.
- Done when:
  - Docs match actual runtime maturity.
  - Release claims are evidence-backed only.

### Task 0.5: Eliminate fake stem-separation success paths
- Area: `products/audio-video/modules/intelligence/ai-voice`
- Problem: Fallback stem separation can copy the full input into every stem.
- Evidence:
  - `products/audio-video/modules/intelligence/ai-voice/apps/desktop/src-tauri/python/stem_separator.py`
  - `products/audio-video/modules/intelligence/ai-voice/apps/desktop/src/components/views/StudioView.tsx`
- Implementation:
  - Remove fallback that presents duplicated input as successful separated stems.
  - Replace with one of:
    - hard failure with actionable dependency guidance
    - explicit preview/demo mode with blocked export/publish
  - Update UI warning and command responses to make degraded mode unmistakable.
  - Add tests asserting failure or explicit degraded-mode metadata.
- Done when:
  - The system never misrepresents duplicated audio as valid separation.
  - Users cannot mistake fallback output for production output.

---

## Phase 1: Real workflow completion and durability

### Task 1.1: Make Data Cloud workflow execution durable
- Area: `products/data-cloud/launcher`, `platform-launcher`, workflow plugin path
- Problem: Workflow execution is stored in in-memory maps and completes immediately.
- Evidence:
  - `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandler.java`
  - `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/plugins/DataCloudRuntimePluginManager.java`
- Implementation:
  - Introduce persistent execution records and execution log storage.
  - Track non-terminal states: queued, running, completed, failed, cancelled.
  - Persist node-level progress and timestamps.
  - Implement true cancellation semantics.
  - Add retry/idempotency model for execute requests.
  - Expose dependency and readiness requirements clearly at startup.
- Done when:
  - Executions survive restart.
  - Execution detail, listing, logs, and cancel behavior are durable and coherent.
  - A restart/resume test passes.

### Task 1.2: Define canonical Data Cloud workflow capability boundary
- Area: `products/data-cloud`
- Problem: README markets workflow execution as ready even though capability is optional.
- Evidence:
  - `products/data-cloud/README.md`
- Implementation:
  - Split workflow capability status into:
    - metadata CRUD
    - runtime execution
    - durable orchestration
  - Document required providers/configs for each tier.
  - Reflect capability presence in `/capabilities` and UI feature gating.
- Done when:
  - Docs and UI no longer imply universal execution readiness.

### Task 1.3: Replace synthetic Data Cloud end-to-end workflow tests
- Area: `products/data-cloud/integration-tests`
- Problem: named E2E workflow proof uses an in-test fake engine.
- Evidence:
  - `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/EndToEndWorkflowTest.java`
- Implementation:
  - Replace `WorkflowEngine` test double with real product wiring.
  - Run collection/entity/pipeline/event workflow against launcher/runtime.
  - Include persistence assertions through real stores.
  - Add restart/resilience validation.
- Done when:
  - The test exercises the real system, not a local simulation.

### Task 1.4: Tighten AEP golden-path tests so partial availability is failure where appropriate
- Area: `products/aep/server`
- Problem: key system tests accept `501/503` for core surfaces.
- Evidence:
  - `products/aep/server/src/test/java/com/ghatana/aep/server/AepGoldenPathSystemTest.java`
- Implementation:
  - Define mandatory vs optional capabilities by profile.
  - For mandatory profile tests, fail on `501/503`.
  - Keep degraded-mode tests separate and explicit.
  - Add a production-profile golden path that proves a real viable deployment.
- Done when:
  - “Golden path” means working system, not non-crashing system.

---

## Phase 2: AEP product completion

### Task 2.1: Implement AEP GovernancePage backend-backed panels
- Area: `products/aep/ui`, `products/aep/server`
- Problem: governance UI is largely placeholder content.
- Evidence:
  - `products/aep/ui/src/pages/GovernancePage.tsx`
- Implementation:
  - Implement real panels for:
    - compliance reports
    - tenant management summary
    - audit log view
  - Add missing server endpoints if needed.
  - Ensure feature flags only gate real features, not TODO shells.
- Done when:
  - GovernancePage provides usable operational value.

### Task 2.2: Implement AEP RunDetail panels
- Area: `products/aep/ui`, `products/aep/server`
- Problem: event lineage, agent decisions, and policy references are missing.
- Evidence:
  - `products/aep/ui/src/pages/RunDetailPage.tsx`
- Implementation:
  - Build real API endpoints and UI tabs for:
    - event lineage for a run
    - decision ledger for a run
    - applied policy references
  - Reuse run ledger and compliance/audit stores where possible.
  - Add unit and browser tests for populated, empty, and error states.
- Done when:
  - RunDetail is an operator-grade debugging screen, not a placeholder shell.

### Task 2.3: Reconcile AEP README, docs, and product identity
- Area: `products/aep/docs`, `products/aep/README.md`
- Problem: repo history shows drift between event processor identity and agent runtime identity.
- Implementation:
  - Rewrite README around the actual product:
    - execution runtime
    - pipelines
    - agent management
    - HITL
    - compliance/governance
  - Archive or clearly label legacy event-pattern framing.
  - Ensure all product docs reference the same capability model.
- Done when:
  - A new engineer can infer the correct product shape from docs alone.

---

## Phase 3: Data Cloud hardening and proof

### Task 3.1: Strengthen governance proof against real persistence
- Area: `products/data-cloud/launcher`, integration suites
- Problem: governance code exists, but confidence is still uneven.
- Evidence:
  - Current tests include solid handler coverage, but proof remains mixed.
- Implementation:
  - Add integration tests against real persistence providers for:
    - purge token flow
    - actual deletion effects
    - redact effects
    - audit trail emission
    - tenant isolation under governance operations
  - Add failure-mode tests for partial deletion and retry safety.
- Done when:
  - Governance claims are backed by real provider integration tests.

### Task 3.2: Audit and formalize optional dependency degraded modes
- Area: `products/data-cloud`
- Problem: many capabilities depend on optional services, but degraded behavior is inconsistently surfaced.
- Implementation:
  - Inventory all optional integrations:
    - analytics
    - reports
    - feature store
    - AI assist
    - voice
    - workflow execution
  - Standardize capability registration, startup logs, health/readiness semantics, and UI empty states.
  - Reject ambiguous half-enabled states.
- Done when:
  - Operators can tell exactly what is live, unavailable, or degraded.

### Task 3.3: Convert README “Ready” claims into evidence-linked readiness matrix
- Area: `products/data-cloud`
- Problem: product claims are broader than current proof.
- Implementation:
  - Replace binary “Ready” language with:
    - implemented
    - verified locally
    - verified in integration
    - deployment-validated
  - Link each major claim to tests/runbooks/evidence.
- Done when:
  - Product docs become trustworthy operational artifacts.

### Task 3.4: Add strict release gates for OpenAPI, UI contract, and capability drift
- Area: `products/data-cloud`
- Problem: broad surface area invites silent drift.
- Implementation:
  - Make route/spec drift checks mandatory in CI.
  - Add frontend contract coverage for all public service clients.
  - Fail CI on undocumented public route additions.
- Done when:
  - Contract drift becomes difficult to merge accidentally.

---

## Phase 4: Audio-video truth, product definition, and operability

### Task 4.1: Decide whether audio-video is a platform capability set or a product
- Area: `products/audio-video`
- Problem: current scope mixes libraries, services, and a creator desktop app.
- Implementation:
  - Choose one primary frame:
    - shared media capability platform
    - internal multimodal service suite
    - standalone creator-facing product
  - Move non-core pieces behind explicit experimental boundaries.
  - Update docs and ownership accordingly.
- Done when:
  - Buyer, operator, and engineering scope are clear.

### Task 4.2: Add real runnable test entrypoints for AI Voice Studio
- Area: `products/audio-video/modules/intelligence/ai-voice/apps/desktop`
- Problem: README claims extensive testing, but package scripts do not expose it.
- Implementation:
  - Add package scripts for unit, e2e, and integration runs.
  - Add environment validation for Python/Rust/model prerequisites.
  - Document pass/fail criteria and artifact locations.
- Done when:
  - Claimed test workflows are reproducible from the package itself.

### Task 4.3: Separate degraded/demo mode from production mode in AI Voice Studio
- Area: `products/audio-video/modules/intelligence/ai-voice`
- Problem: fallback behavior is entangled with normal operation.
- Implementation:
  - Introduce explicit runtime mode classification:
    - production
    - degraded
    - demo
  - Disable unsafe exports or completion states in degraded/demo mode.
  - Surface mode prominently in UI and logs.
- Done when:
  - No user can confuse a demo/degraded path for real production output.

---

## Phase 5: Shared operational quality improvements

### Task 5.1: Remove committed build artifacts and vendored product dependencies
- Area: repo hygiene
- Problem: product directories contain `dist/`, `build/`, and `node_modules/` artifacts that pollute auditability.
- Implementation:
  - Remove committed generated artifacts where appropriate.
  - Update ignore rules.
  - Keep only necessary generated source checked in, with rationale.
- Done when:
  - Product directories reflect source of truth, not local machine state.

### Task 5.2: Add a repo-wide product truthfulness policy
- Area: docs/governance
- Problem: readiness and implementation claims are inconsistent across products.
- Implementation:
  - Introduce a standard for using:
    - implemented
    - verified
    - production-ready
    - experimental
  - Require evidence references for production-readiness claims.
  - Add CI lint/check for disallowed unsupported readiness phrases in scoped docs.
- Done when:
  - Marketing language cannot drift far ahead of implementation evidence.

### Task 5.3: Add capability truth dashboards for operators
- Area: `data-cloud`, `aep`
- Problem: operators need live truth, not docs.
- Implementation:
  - Expose active capability matrix in health/info/capabilities surfaces.
  - Include dependency status and degraded-mode reasons.
  - Mirror this in UI admin/operator screens.
- Done when:
  - Runtime truth is inspectable without reading source code.

---

## Priority order

### Immediate priority
1. Task 0.1
2. Task 0.2
3. Task 0.3
4. Task 0.5
5. Task 1.1
6. Task 1.3
7. Task 1.4

### Next priority
1. Task 2.1
2. Task 2.2
3. Task 3.1
4. Task 3.2
5. Task 4.2

### Strategic priority
1. Task 4.1
2. Task 2.3
3. Task 3.3
4. Task 5.2
5. Task 5.3

---

## Suggested ownership split

- Security/platform team:
  - 0.1
  - 5.2
  - 5.3
- AEP team:
  - 0.2
  - 1.4
  - 2.1
  - 2.2
  - 2.3
- Data Cloud team:
  - 0.3
  - 1.1
  - 1.2
  - 1.3
  - 3.1
  - 3.2
  - 3.3
  - 3.4
- Audio-video team:
  - 0.4
  - 0.5
  - 4.1
  - 4.2
  - 4.3
- Repo/platform ops:
  - 5.1

---

## Exit criteria for a credible next audit

- No default security secrets in runtime code.
- No major public contract drift between docs and server routes.
- No mock operational truth in shipped product code.
- Data Cloud workflow execution is durable, restart-safe, and honestly documented.
- AEP operator UI reflects real data for governance and run debugging.
- Audio-video either has real production proof or is explicitly labeled experimental.
- “End-to-end” tests hit the real product, not local simulations.
