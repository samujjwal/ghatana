# YAPPC Production Readiness Audit — `samujjwal/ghatana`

Target commit audited: `f302e89c8e7116e8821a7957b4a06a5d7dff81e` — verified as commit message `dd ff gg 1`. 

This is a static code-and-doc audit based on repository files accessible through GitHub at the target commit. I did not execute tests locally because the container could not clone GitHub due DNS/network resolution failure; the report therefore marks runtime/test execution as unverified.

---

## 1. Executive Summary

YAPPC is directionally correct but not production-ready. The product vision is explicitly an AI-powered product/app creation, visibility, health, and evolution platform with the lifecycle **Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve**, and the README itself reports current maturity as AI-native **3/10**, feature completeness **4/10**, and production readiness **2/10**. 

The architecture is more mature than the implementation. The build wiring includes YAPPC modules, agents, scaffold, refactorer, knowledge graph, Data Cloud, AEP action-plane modules, auth gateway, platform Kernel modules, and shared platform libraries, which is the right direction. 

The critical blocker is that several “production” paths still contain local-only, fallback, simplified, or placeholder behavior. `PhasePacketServiceImpl` calls Data Cloud, platform evidence, policy, capability, audit, and preview health, but still has `platformRunStatus = null`, empty feature flags, simplified readiness scoring, hardcoded preview ID derivation, and “for now / in production” comments.  

Kernel integration is partially real but not production-complete. `ProductUnitIntentExporter`, `ProductUnitIntentValidationService`, and `CreateCommand` exist, but the flow is still CLI/file-oriented, uses random workspace IDs, defaults surfaces to `web-api`, and validates against local hardcoded provider/profile lists instead of canonical Kernel contracts.   

Frontend IA is mostly correct: project routes expose the eight lifecycle phases as first-class routes, plus Kernel health and admin pages.  But the phase cockpit contains hardcoded English copy, frontend helper gating, and derived preview data; it is not fully i18n/backend-contract-driven yet. 

---

## 2. Deterministic Progress Summary

| Area                      |  Progress State | Summary                                                                                                                                                         |
| ------------------------- | --------------: | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Product vision            | PARTIALLY_FIXED | Vision is clear and documented; implementation still uneven by phase.                                                                                           |
| Build/module architecture | PARTIALLY_FIXED | Good module inclusion and shared/platform dependency direction, but several API/impl splits are explicitly deferred.                                            |
| Data Cloud integration    | PARTIALLY_FIXED | Some repositories are Data Cloud-backed and tenant-scoped; not all lifecycle state/evidence/run/learning paths are durable.                                     |
| AEP/agent integration     | PARTIALLY_FIXED | Agent modules and Data Cloud action-plane dependencies are wired, but lifecycle phase execution/learning loops are not fully end-to-end.                        |
| Kernel integration        | PARTIALLY_FIXED | ProductUnitIntent exporter/validator/CLI exist; production Data Cloud/Event Cloud/Kernel API ingestion remains future/local-only.                               |
| Frontend lifecycle IA     | PARTIALLY_FIXED | Eight phase routes exist; UI still has hardcoded copy and local action logic.                                                                                   |
| Production operations     | PARTIALLY_FIXED | Commit adds stricter release gates for SLO/cost/domain invariants/OpenAPI breaking changes, but YAPPC-specific executable proof is still not complete.          |
| Testing                   | PARTIALLY_FIXED | Frontend test scripts exist, including readiness, E2E, a11y, visual, performance, and canvas tests, but execution was not verified and coverage is not proven.  |

---

## 3. Top Production Blockers

1. **Phase packet is not a full production contract yet** — `platformRunStatus` is null; readiness and feature flags are simplified; health IDs are derived rather than sourced from runtime state.
2. **Kernel visibility remains local-filesystem-first** — `KernelLifecycleEventIngestService` reads `.kernel/out/products/**`; Data Cloud/Event Cloud/Kernel API ingestion is documented as future. 
3. **Docs contradict implementation state** — Kernel visibility doc claims “existing and executable,” but its implementation status table still marks core items pending. 
4. **ProductUnitIntent validation is not contract-backed** — provider/profile lists are hardcoded locally instead of pulled from Kernel public contracts.
5. **CreateCommand generates Kernel intent with random workspace ID** — not tied to actual workspace/project/tenant state.
6. **Data Cloud/AEP evidence is partial** — `PhasePacketServiceImpl` queries evidence/policy but uses null workspace IDs and swallows failures into empty lists.
7. **Frontend cockpit still has hardcoded English and derived action logic** — not fully i18n/backend-contract-driven.
8. **Feature flags and entitlements are placeholders** — `determineEnabledFlags` returns `Set.of()`.
9. **Degraded behavior is explicit but incomplete** — degraded packets lose platform run status, evidence, governance, required artifacts, and actionable recovery metadata.
10. **Executable E2E proof is unverified** — no successful test run evidence was available in this audit.

---

## 4. Product Vision vs Implementation Reality

| Product Capability | Vision                                                     | Current Evidence                                                                                   | Gap                                                                                  | Progress State  | Priority |
| ------------------ | ---------------------------------------------------------- | -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ | --------------- | -------- |
| Intent             | Capture product goals and success criteria with AI support | README says intent is medium maturity; API manifest includes intent capture/analyze/read routes.   | Need verify durable Data Cloud schema, versioning, audit, AI grounding, and UI flow. | PARTIALLY_FIXED | P1       |
| Shape              | Architecture/product modeling, canvas, surfaces, runtime   | README and frontend routes expose shape/canvas concepts.                                           | Advanced modeling and artifact graph linkage not proven.                             | PARTIALLY_FIXED | P1       |
| Validate           | Gates, policies, evidence, blockers                        | `PhasePacketServiceImpl` uses `PhaseGateValidator` and platform policy.                            | Conditions passed to gate validator are empty; readiness is simplified.              | PARTIALLY_FIXED | P0       |
| Generate           | Code/scaffold/ProductUnitIntent                            | Exporter, validator, create CLI exist.                                                             | Intent not backed by canonical Kernel contract registry; workspace ID is random.     | PARTIALLY_FIXED | P0       |
| Run                | Preview/runtime/Kernel handoff                             | Health signals and Kernel filesystem ingestion exist.                                              | `platformRunStatus` null; production ingestion future; local-only Kernel output.     | PARTIALLY_FIXED | P0       |
| Observe            | Health, metrics, dashboard                                 | Kernel health routes exist; ingest and health service exist.                                       | No Data Cloud/Event Cloud-backed runtime truth proven.                               | PARTIALLY_FIXED | P1       |
| Learn              | Feedback and adaptive improvement                          | README says prompt registry supports versioning/rollback.                                          | End-to-end learning loop not proven in inspected code.                               | PARTIALLY_FIXED | P1       |
| Evolve             | Closed loop change/evolution                               | Evolve route exists.                                                                               | Change-request flow to Kernel and regression validation not proven.                  | PARTIALLY_FIXED | P1       |

---

## 5. Lifecycle Phase Audit

### Intent

Current implementation has route manifest entries for intent capture/analyze/read with required auth scopes and privacy classification. 

Gaps:

* Need prove backend handlers persist intent to Data Cloud with tenant/workspace/project scoping.
* Need versioning/history and audit evidence for intent changes.
* Need ensure AI interpretation is not rules-only and is labeled correctly.
* Need UI i18n and error/degraded states.

Required implementation:

* Wire intent capture/analyze/read to a Data Cloud-backed `IntentRepository`.
* Add `IntentVersionRepository` or use canonical artifact versioning.
* Emit audit/evidence records for create/analyze/update.
* Add integration tests for tenant isolation and missing workspace/project.

### Shape

Current route IA includes `shape` and legacy `canvas`, with shared canvas/UI builder dependencies present in frontend package.  

Gaps:

* Need prove shape artifacts are persisted as Data Cloud artifacts, not only canvas/client state.
* Need validate runtime/framework choices through shared registries.
* Need model-to-code traceability.

Required implementation:

* Use canonical artifact graph for shape outputs.
* Connect canvas edits to Data Cloud state and audit events.
* Add shape-to-generate traceability tests.

### Validate

Current implementation calls `PhaseGateValidator`, but uses empty conditions and simplified blocker/readiness mapping.  

Gaps:

* Gate conditions do not include project state, completed artifacts, policy results, evidence, feature flags, or tenant tier.
* Completeness score is binary-like: `1.0` if no blockers, `0.5` otherwise.
* Policy results are converted to governance display records, but not clearly enforced before actions.

Required implementation:

* Build `PhaseGateContext` from Data Cloud project state, artifacts, policy, evidence, runtime health, and tenant entitlements.
* Replace simplified readiness with weighted readiness model from config.
* Block actions server-side when policy/gate fails.

### Generate

Exporter and validator exist, but are local-map based.  

Gaps:

* Contract is not generated/imported from Kernel product contracts.
* Validation provider/profile lists are hardcoded.
* No proof generated code is compiled/tested before handoff.

Required implementation:

* Replace local ProductUnitIntent maps with typed Kernel contract DTOs.
* Validate provider/profile/surface against Kernel public registry contracts.
* Add generated artifact verification gate.

### Run

Current run support is partial: health signals are derived from a preview runtime service, and Kernel ingest reads local manifests.  

Gaps:

* `platformRunStatus` is null.
* Preview IDs are derived from `projectId + phase`.
* Kernel event ingestion is local filesystem only.
* No rollback/promote flow proven.

Required implementation:

* Add `PlatformRunStatusService` backed by AEP/Data Cloud/Kernel event stream.
* Persist run IDs and preview IDs in project/runtime state.
* Add run/rollback/promote API tests.

### Observe

Kernel health routes exist in frontend routing. 

Gaps:

* Local filesystem ingestion cannot support production multi-tenant runtime truth.
* Health snapshots are untyped maps.
* No SLO-driven remediation flow proven.

Required implementation:

* Add typed Kernel health read models.
* Consume Kernel public events or Data Cloud/Event Cloud stream.
* Add SLO/cost/domain invariant dashboard evidence.

### Learn

Gaps:

* Prompt registry claims are not fully verified in inspected code.
* No end-to-end learning record from failed generation/run to updated suggestion/prompt/agent state was proven.
* Human approval learning queue not verified.

Required implementation:

* Add learning event schema in Data Cloud.
* Connect agent outcomes, approvals/rejections, and Kernel gate failures to learning evidence.
* Add promotion/rollback/quarantine tests.

### Evolve

Gaps:

* Evolve route exists but closed-loop change request to Kernel was not proven.
* No diff review/regression re-validation flow proven.

Required implementation:

* Add `EvolutionPlanService`.
* Persist evolution proposal, diff, impact analysis, approvals, and generated ProductUnitIntent update.
* Re-enter validate/generate/run with traceability.

---

## 6. Architecture and Boundary Findings

| ID       | Progress State  | Area                | Finding                                                                                      | Severity | Files                                                                     | Why It Matters                                                             | Fix                                                                             |
| -------- | --------------- | ------------------- | -------------------------------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| ARCH-001 | STILL_OPEN      | Docs vs code        | Kernel visibility doc says “existing and executable” but table marks key components pending. | High     | `products/yappc/docs/architecture/KERNEL_VISIBILITY_AND_CONTROL_PLANE.md` | Misleads implementation planning.                                          | Update doc from code evidence and use progress states.                          |
| ARCH-002 | PARTIALLY_FIXED | Module wiring       | Data Cloud/AEP/shared modules are included in standalone build.                              | Medium   | `products/yappc/settings.gradle.kts`                                      | Correct foundation, but inclusion does not prove full runtime integration. | Add integration tests proving each critical module is used in production paths. |
| ARCH-003 | STILL_OPEN      | Kernel boundary     | Kernel ingestion is local filesystem-first and production Data Cloud/Event Stream is future. | High     | `KernelLifecycleEventIngestService`                                       | Local FS is not production multi-tenant runtime truth.                     | Add event/API/Data Cloud-backed ingestion provider.                             |
| ARCH-004 | PARTIALLY_FIXED | API source of truth | Route manifest defines route/auth/scope/privacy schema.                                      | Medium   | `route-manifest.yaml`                                                     | Good direction, but parity must be executable.                             | Run/extend manifest↔OpenAPI↔backend↔frontend parity checks.                     |

---

## 7. Backend Findings

| ID     | Progress State  | File                                     | Class/Method                     | Problem                                                                  | Required Fix                                                                                     | Tests                                                                                        |
| ------ | --------------- | ---------------------------------------- | -------------------------------- | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------- |
| BE-001 | PARTIALLY_FIXED | `PhasePacketServiceImpl.java`            | `buildPhasePacket`               | Aggregates real services but leaves `platformRunStatus` null.            | Implement `PlatformRunStatusService` using AEP/Data Cloud/Kernel events.                         | Unit + integration: phase packet includes real run status for running/succeeded/failed runs. |
| BE-002 | STILL_OPEN      | `PhasePacketServiceImpl.java`            | `queryPhaseBlockers`             | Calls gate validator with `Map.of()` conditions.                         | Build full gate context from project state, artifacts, policy, runtime health, and entitlements. | Gate tests for missing artifacts, denied policy, degraded runtime.                           |
| BE-003 | STILL_OPEN      | `PhasePacketServiceImpl.java`            | `determineEnabledFlags`          | Always returns `Set.of()`.                                               | Query GrowthBook/entitlement Data Cloud state or canonical feature service.                      | Role/tier/flag tests for enabled/disabled actions.                                           |
| BE-004 | STILL_OPEN      | `PhasePacketServiceImpl.java`            | `calculatePhaseReadiness`        | Completeness score is simplified.                                        | Use configured phase required artifacts, gate severity, evidence confidence, policy outcome.     | Readiness golden tests per phase.                                                            |
| BE-005 | STILL_OPEN      | `PhasePacketServiceImpl.java`            | `queryGovernanceRecords`         | Policy is displayed as governance but not clearly used to block actions. | Enforce policy before action generation and phase transition.                                    | Denied policy prevents primary action.                                                       |
| BE-006 | PARTIALLY_FIXED | `AgentStateRepository.java`              | `resolveTenantId`                | Good tenant fail-closed behavior exists.                                 | Extend same pattern to all lifecycle/evidence/prompt/conversation repos.                         | Tenant isolation integration tests.                                                          |
| BE-007 | STILL_OPEN      | `KernelLifecycleEventIngestService.java` | static `BLOCKING_EXECUTOR`       | Static cached executor lacks lifecycle/shutdown ownership.               | Inject managed blocking executor from platform runtime.                                          | Resource lifecycle test.                                                                     |
| BE-008 | STILL_OPEN      | `CreateCommand.java`                     | `executeKernelProductUnitCreate` | Uses random workspace ID and default surface.                            | Require real workspace/project context or explicit CLI args; fail if missing.                    | CLI tests for missing workspace and multi-surface inputs.                                    |

---

## 8. Frontend Findings

| ID     | Progress State  | File                | Component/Hook/Route   | Problem                                                              | Required Fix                                                       | Tests                                                             |
| ------ | --------------- | ------------------- | ---------------------- | -------------------------------------------------------------------- | ------------------------------------------------------------------ | ----------------------------------------------------------------- |
| FE-001 | PARTIALLY_FIXED | `routes.ts`         | project routes         | Eight phase routes exist.                                            | Ensure every route is backed by phase packet and capability gates. | Route inventory + E2E phase traversal.                            |
| FE-002 | STILL_OPEN      | `_phaseCockpit.tsx` | `PHASE_DETAIL_COPY`    | Hardcoded English strings.                                           | Move to `@ghatana/i18n` keys.                                      | i18n conformance test.                                            |
| FE-003 | STILL_OPEN      | `_phaseCockpit.tsx` | `phasePacketToPreview` | Derives preview snapshot client-side with null estimates/confidence. | Backend must provide transition preview/readiness prediction.      | Contract test ensuring no client-only lifecycle truth.            |
| FE-004 | STILL_OPEN      | `usePhasePacket.ts` | helper gating          | `isActionEnabled` only checks `canRead` and local flags.             | Use backend `availableActions` and capability decision only.       | Unit test: disabled backend action cannot be enabled client-side. |
| FE-005 | PARTIALLY_FIXED | `package.json`      | dependencies/scripts   | Good shared dependency set and many tests exist.                     | Verify scripts run in CI and are not test theater.                 | CI evidence and coverage thresholds.                              |

---

## 9. Data Cloud / AEP / Agent Findings

| ID      | Progress State  | Area            | Current Behavior                                                                   | Gap                                                   | Required Fix                                                 | Files                         |
| ------- | --------------- | --------------- | ---------------------------------------------------------------------------------- | ----------------------------------------------------- | ------------------------------------------------------------ | ----------------------------- |
| DC-001  | PARTIALLY_FIXED | Agent state     | Data Cloud-backed `AgentStateRepository` with tenant context exists.               | Need prove all agent runtime/workflow outputs use it. | Wire agent runtime execution state through this repo.        | `AgentStateRepository.java`   |
| DC-002  | STILL_OPEN      | Phase evidence  | `PhasePacketServiceImpl` calls platform evidence search with `workspaceId = null`. | Workspace scoping missing.                            | Include workspace ID and tenant-aware evidence query.        | `PhasePacketServiceImpl.java` |
| DC-003  | STILL_OPEN      | Lifecycle state | Project state lookup is Data Cloud-backed but returns reduced map.                 | Loses full shape/phase/runtime/evidence fields.       | Add typed `ProjectLifecycleState` mapper.                    | `PhasePacketServiceImpl.java` |
| AEP-001 | STILL_OPEN      | Platform run    | `platformRunStatus` is null.                                                       | AEP workflow status not surfaced.                     | Query AEP workflow/run state and include trace/evidence IDs. | `PhasePacketServiceImpl.java` |
| AEP-002 | STILL_OPEN      | Learning        | No inspected end-to-end learn/evolve loop.                                         | Agent outcomes not proven to feed learning.           | Add `LearningEvidenceRepository` and promotion workflow.     | Data Cloud + agent modules    |

---

## 10. Kernel Integration Findings

| ID      | Progress State  | Area                         | Current Behavior                                   | Boundary Risk                             | Required Fix                                                                                                    | Files                                     |
| ------- | --------------- | ---------------------------- | -------------------------------------------------- | ----------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ----------------------------------------- |
| KRN-001 | PARTIALLY_FIXED | ProductUnitIntent export     | Exports YAML/JSON intent map.                      | Local map may drift from Kernel contract. | Use typed Kernel public contract DTO/schema.                                                                    | `ProductUnitIntentExporter.java`          |
| KRN-002 | STILL_OPEN      | ProductUnitIntent validation | Hardcoded providers/profiles.                      | Duplicate Kernel registry truth.          | Resolve provider/profile/surface through Kernel public contracts.                                               | `ProductUnitIntentValidationService.java` |
| KRN-003 | STILL_OPEN      | CLI handoff                  | Random workspace ID and default `web-api` surface. | Invalid/non-traceable ProductUnitIntent.  | Require workspace/project/surface/lifecycle profile from Data Cloud/project config.                             | `CreateCommand.java`                      |
| KRN-004 | STILL_OPEN      | Kernel observe               | Reads `.kernel/out/products/**`.                   | Local-only, not production multi-tenant.  | Add `KernelLifecycleEventProvider` with filesystem dev provider and Data Cloud/Event Cloud production provider. | `KernelLifecycleEventIngestService.java`  |
| KRN-005 | STILL_OPEN      | Docs                         | Doc says components pending while code exists.     | Confusing progress tracking.              | Replace pending table with verified states: implemented/local-only/production-backed.                           | `KERNEL_VISIBILITY_AND_CONTROL_PLANE.md`  |

---

## 11. Shared Library Reuse Findings

| ID      | Progress State  | Duplicate/Bypass               | Canonical Shared Library                                   | YAPPC File                                | Fix                                                                         |
| ------- | --------------- | ------------------------------ | ---------------------------------------------------------- | ----------------------------------------- | --------------------------------------------------------------------------- |
| SHR-001 | STILL_OPEN      | Hardcoded UI copy              | `@ghatana/i18n`                                            | `_phaseCockpit.tsx`                       | Replace strings with translation keys.                                      |
| SHR-002 | STILL_OPEN      | Local capability helper        | `yappc-auth` / backend capabilities                        | `usePhasePacket.ts`                       | Remove client-side capability inference except display filtering.           |
| SHR-003 | PARTIALLY_FIXED | Canvas/UI builder dependencies | `@ghatana/canvas`, `@ghatana/ui-builder`                   | `package.json`                            | Verify actual route/component usage and eliminate legacy local canvas code. |
| SHR-004 | STILL_OPEN      | Kernel validation constants    | `@ghatana/kernel-product-contracts` / Kernel Java contract | `ProductUnitIntentValidationService.java` | Import/generate from canonical Kernel contract.                             |
| SHR-005 | STILL_OPEN      | Static executor                | platform runtime/executor service                          | `KernelLifecycleEventIngestService.java`  | Inject managed executor.                                                    |

---

## 12. API and Contract Parity Findings

| Route/Contract              | Manifest                                                                                       | OpenAPI             | Backend                         | Frontend                | Issue                                      | Fix                                                               |
| --------------------------- | ---------------------------------------------------------------------------------------------- | ------------------- | ------------------------------- | ----------------------- | ------------------------------------------ | ----------------------------------------------------------------- |
| Intent capture/analyze/read | Present with auth/scope/privacy                                                                | Not fully inspected | Not fully inspected             | Not fully inspected     | Parity unverified.                         | Run `checkYappcOpenApiParity` and add handler/client route tests. |
| Phase packet                | Manifest mentions phase packet event types; frontend calls `LifecycleService.getPhasePacket`.  | Not fully inspected | `PhasePacketServiceImpl` exists | `usePhasePacket` exists | GET/POST parity comment says still a task. | Add manifest/OpenAPI/generated-client/backend parity test.        |
| Kernel health               | Frontend routes exist                                                                          | Not fully inspected | Kernel services exist           | Routes exist            | Production data provider local-only.       | Add typed contract and Data Cloud/Event Cloud provider.           |
| Admin prompt/AB/flags       | Routes exist                                                                                   | Not fully inspected | Not fully inspected             | Routes exist            | Capability gating not proven.              | Add route auth/capability E2E.                                    |

---

## 13. Security, Privacy, Governance, Tenancy, Audit

The strongest security evidence found is `AgentStateRepository.resolveTenantId`, which fails closed if tenant context is missing or `default-tenant` is used.  This pattern should become mandatory across all YAPPC repositories.

Gaps:

* `PhasePacketServiceImpl` builds actor context from `principal.getName()` and first role; this is not enough for fine-grained RBAC.
* `determineAvailableActions` uses simple role checks (`ADMIN`, `OWNER`, `EDITOR`) instead of canonical permission decisions.
* Policy decisions are converted into governance records but not clearly enforced server-side before actions.
* Route manifest privacy classification exists, but audit/authorization parity across OpenAPI/backend/frontend was not fully verified.
* Degraded packet has safe no-actions behavior, but loses recovery detail and evidence.

P0 fix:

* Introduce a server-side `PhaseActionAuthorizationService` that combines capability evaluation, policy decision, tenant tier, feature flags, and phase gate outcome. Frontend must only render backend-returned actions.

---

## 14. UI/UX, i18n, a11y, Design System

Frontend strengths:

* Eight phase IA is explicit.
* Phase cockpit uses structured panels for blockers, evidence, governance, action cards, and status surfaces.
* Package dependencies include design system, i18n, product shell, realtime, canvas, and UI builder. 

Gaps:

* Hardcoded phase copy and labels in `_phaseCockpit.tsx`.
* Phase summary content is English and not translation-keyed.
* Some UI behavior derives lifecycle state from packet client-side instead of receiving backend-ready view models.
* A11y scripts exist, but execution and coverage were not verified.

P1 fix:

* Create `phaseCockpit.i18n.ts` key map and replace inline text.
* Add tests that no visible phase cockpit copy bypasses i18n.
* Ensure every empty/loading/error/degraded/unauthorized state has deterministic copy and ARIA semantics.

---

## 15. Testing and CI Gaps

Evidence:

* Commit adds stricter CI gates for product SLO budgets, cost budgets, product domain invariants, and OpenAPI breaking changes. 
* YAPPC web package includes readiness, smoke, canvas unit/integration/performance, E2E, a11y, visual, and performance-memory scripts. 

Gaps:

* I could not execute tests in this environment.
* Need verify YAPPC-specific CI actually runs all critical scripts.
* Need add tests around current gaps: phase packet, policy blocking, feature flags, Kernel ingestion provider, ProductUnitIntent typed contract, i18n, and backend/frontend parity.

---

## 16. Production Readiness Scorecard

|  # | Dimension                           | Score | Progress State  |
| -: | ----------------------------------- | ----: | --------------- |
|  1 | Product vision alignment            |     4 | PARTIALLY_FIXED |
|  2 | Lifecycle completeness              |     2 | PARTIALLY_FIXED |
|  3 | Intent phase                        |     3 | PARTIALLY_FIXED |
|  4 | Shape phase                         |     3 | PARTIALLY_FIXED |
|  5 | Validate phase                      |     2 | STILL_OPEN      |
|  6 | Generate phase                      |     3 | PARTIALLY_FIXED |
|  7 | Run phase                           |     2 | STILL_OPEN      |
|  8 | Observe phase                       |     2 | STILL_OPEN      |
|  9 | Learn phase                         |     2 | STILL_OPEN      |
| 10 | Evolve phase                        |     2 | STILL_OPEN      |
| 11 | Data Cloud integration              |     3 | PARTIALLY_FIXED |
| 12 | AEP/agent integration               |     2 | PARTIALLY_FIXED |
| 13 | Kernel integration                  |     2 | PARTIALLY_FIXED |
| 14 | Shared library reuse                |     3 | PARTIALLY_FIXED |
| 15 | Artifact compiler/decompiler        |     2 | UNVERIFIED      |
| 16 | Canvas/UI builder                   |     3 | PARTIALLY_FIXED |
| 17 | CLI/scaffold/generation             |     3 | PARTIALLY_FIXED |
| 18 | Backend correctness                 |     2 | STILL_OPEN      |
| 19 | Frontend correctness                |     3 | PARTIALLY_FIXED |
| 20 | API/contract parity                 |     2 | STILL_OPEN      |
| 21 | Security                            |     3 | PARTIALLY_FIXED |
| 22 | Privacy                             |     2 | STILL_OPEN      |
| 23 | Governance                          |     2 | STILL_OPEN      |
| 24 | Auth/RBAC/capability gates          |     2 | STILL_OPEN      |
| 25 | Multi-tenancy                       |     3 | PARTIALLY_FIXED |
| 26 | Auditability                        |     3 | PARTIALLY_FIXED |
| 27 | Observability                       |     3 | PARTIALLY_FIXED |
| 28 | Reliability/degraded behavior       |     3 | PARTIALLY_FIXED |
| 29 | Performance                         |     2 | UNVERIFIED      |
| 30 | Scalability                         |     2 | STILL_OPEN      |
| 31 | Async/concurrency correctness       |     2 | STILL_OPEN      |
| 32 | i18n                                |     2 | STILL_OPEN      |
| 33 | a11y                                |     3 | UNVERIFIED      |
| 34 | UX simplicity                       |     3 | PARTIALLY_FIXED |
| 35 | Testing completeness                |     2 | UNVERIFIED      |
| 36 | CI/release readiness                |     3 | PARTIALLY_FIXED |
| 37 | Documentation accuracy              |     2 | STILL_OPEN      |
| 38 | Operational readiness               |     2 | STILL_OPEN      |
| 39 | Data integrity                      |     2 | STILL_OPEN      |
| 40 | Learning/adaptation                 |     2 | STILL_OPEN      |
| 41 | Prompt/version/evaluation lifecycle |     2 | UNVERIFIED      |
| 42 | Human approval workflows            |     2 | UNVERIFIED      |
| 43 | Admin/config surfaces               |     2 | PARTIALLY_FIXED |
| 44 | Dependency hygiene                  |     3 | PARTIALLY_FIXED |
| 45 | Code minimality/DRY/SRP             |     3 | PARTIALLY_FIXED |
| 46 | Boundary correctness                |     2 | STILL_OPEN      |
| 47 | Production deployment readiness     |     2 | STILL_OPEN      |

---

## 17. Deterministic Implementation Plan

| ID       | Progress State  | Priority | Owner Boundary       | Category                             | File(s)                                                                     | What to Change                                                                                                         | Why                                                                | Dependencies                          | Acceptance Criteria                                          | Tests                                            |
| -------- | --------------- | -------- | -------------------- | ------------------------------------ | --------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ | ------------------------------------- | ------------------------------------------------------------ | ------------------------------------------------ |
| TODO-001 | PARTIALLY_FIXED | P0       | YAPPC/Data Cloud/AEP | Phase packet correctness             | `PhasePacketServiceImpl.java`                                               | Replace `platformRunStatus = null` with real AEP/Data Cloud/Kernel run status.                                         | Run phase cannot be production-ready without executable run truth. | AEP run status contract.              | Phase packet includes runId/status/trace/evidence.           | Unit + integration for running/succeeded/failed. |
| TODO-002 | PARTIALLY_FIXED | P0       | YAPPC                | Gate correctness                     | `PhasePacketServiceImpl.queryPhaseBlockers`                                 | Pass full `PhaseGateContext`, not `Map.of()`.                                                                          | Empty gate input causes false readiness.                           | Project/artifact/evidence model.      | Missing required artifact blocks phase.                      | Gate validation tests per phase.                 |
| TODO-003 | PARTIALLY_FIXED | P0       | YAPPC/Auth           | Authorization                        | `PhasePacketServiceImpl.determineAvailableActions`                          | Replace role-string logic with capability/policy/feature/tier decision service.                                        | Prevent unauthorized phase/action exposure.                        | Capability service, policy service.   | Denied policy disables actions server-side.                  | RBAC/capability matrix tests.                    |
| TODO-004 | PARTIALLY_FIXED | P0       | Kernel/YAPPC         | Kernel contract                      | `ProductUnitIntentExporter.java`, `ProductUnitIntentValidationService.java` | Use typed Kernel public contract DTO/schema instead of local maps/constants.                                           | Prevent contract drift.                                            | Kernel product contracts.             | Generated intent validates against Kernel contract.          | Contract compatibility tests.                    |
| TODO-005 | PARTIALLY_FIXED | P0       | YAPPC                | CLI correctness                      | `CreateCommand.java`                                                        | Require workspace ID/project ID/surfaces/lifecycle profile; remove random workspace default.                           | Random workspace breaks traceability.                              | CLI arg/schema update.                | Missing workspace fails clearly.                             | CLI tests.                                       |
| TODO-006 | PARTIALLY_FIXED | P0       | Kernel/Data Cloud    | Kernel observe                       | `KernelLifecycleEventIngestService.java`                                    | Split into `KernelLifecycleEventProvider` with dev filesystem provider and production Data Cloud/Event Cloud provider. | Local FS cannot be production runtime truth.                       | Data Cloud/Event Cloud contract.      | Production profile does not read `.kernel` directly.         | Provider selection integration tests.            |
| TODO-007 | PARTIALLY_FIXED | P0       | Shared Platform      | Async/resource lifecycle             | `KernelLifecycleEventIngestService.java`                                    | Replace static cached executor with injected managed executor.                                                         | Avoid resource leaks and unmanaged threads.                        | Platform runtime executor.            | Executor shutdown is owned by service lifecycle.             | Resource lifecycle test.                         |
| TODO-008 | PARTIALLY_FIXED | P1       | YAPPC/Data Cloud     | Readiness                            | `PhasePacketServiceImpl.calculatePhaseReadiness`                            | Implement weighted readiness from stage config, artifacts, evidence, policy, health.                                   | Current 1.0/0.5 score is not meaningful.                           | Stage/artifact/evidence models.       | Score explains missing requirements.                         | Golden readiness tests.                          |
| TODO-009 | PARTIALLY_FIXED | P1       | YAPPC/Data Cloud     | Feature flags                        | `PhasePacketServiceImpl.determineEnabledFlags`                              | Query canonical entitlement/feature-flag state.                                                                        | Current empty set blocks feature-aware UI.                         | Feature service/Data Cloud schema.    | Phase packet includes effective flags.                       | Flag/tier tests.                                 |
| TODO-010 | PARTIALLY_FIXED | P1       | YAPPC/Data Cloud     | Evidence scoping                     | `PhasePacketServiceImpl.queryPhaseEvidence`                                 | Include workspace ID and typed evidence filters.                                                                       | Evidence search is not workspace-scoped.                           | Evidence query contract.              | Evidence cannot leak across workspace/tenant.                | Tenant/workspace isolation tests.                |
| TODO-011 | PARTIALLY_FIXED | P1       | Frontend/i18n        | i18n                                 | `_phaseCockpit.tsx`                                                         | Move all user-visible copy to `@ghatana/i18n`.                                                                         | Current UI is not production i18n-ready.                           | Translation keys.                     | No hardcoded phase cockpit labels.                           | i18n conformance test.                           |
| TODO-012 | PARTIALLY_FIXED | P1       | Frontend/YAPPC       | Backend-driven UI                    | `usePhasePacket.ts`, `_phaseCockpit.tsx`                                    | Remove client-side lifecycle/action inference; consume backend action/readiness contract.                              | Prevent frontend-only truth.                                       | TODO-003.                             | UI cannot enable unavailable backend action.                 | Component tests.                                 |
| TODO-013 | PARTIALLY_FIXED | P1       | Data Cloud           | Tenant consistency                   | all YAPPC Data Cloud repos                                                  | Apply `AgentStateRepository` fail-closed tenant pattern everywhere.                                                    | Prevent tenant leakage/default tenant use.                         | Repo inventory.                       | All durable repos reject missing/default tenant.             | Tenant isolation integration tests.              |
| TODO-014 | PARTIALLY_FIXED | P1       | YAPPC/AEP            | Learn loop                           | learning/prompt/agent modules                                               | Persist learning evidence from gate failures, approvals, generation, run.                                              | Learn/Evolve phases are not closed-loop.                           | AEP learning contracts.               | Failed run produces learning recommendation with provenance. | E2E learn-from-failure test.                     |
| TODO-015 | PARTIALLY_FIXED | P1       | YAPPC/Kernel         | Evolve loop                          | evolve route/services                                                       | Add evolution proposal → diff → approval → ProductUnitIntent update → validate/generate/run loop.                      | Evolve is not executable end-to-end.                               | Kernel change request contract.       | Evolve produces traceable validated change.                  | E2E evolve flow.                                 |
| TODO-016 | STILL_OPEN      | P2       | Docs                 | Accuracy                             | `KERNEL_VISIBILITY_AND_CONTROL_PLANE.md`, README                            | Replace stale pending/complete claims with verified progress states.                                                   | Prevent audit confusion.                                           | Current code inventory.               | Docs match implementation.                                   | Doc-claims-evidence check.                       |
| TODO-017 | UNVERIFIED      | P2       | CI/Test              | Release proof                        | package/workflow scripts                                                    | Ensure YAPPC-specific build/test/e2e/a11y/contract tests run in CI.                                                    | Scripts exist but execution not verified.                          | CI workflow update.                   | CI produces YAPPC evidence artifacts.                        | Workflow evidence check.                         |
| TODO-018 | STILL_OPEN      | P2       | Observability        | SLO/cost/domain invariant visibility | frontend + backend metrics                                                  | Surface commit-added SLO/cost/domain invariant gates in YAPPC observe/admin UI.                                        | Gates exist but YAPPC visibility must show them.                   | Kernel/Data Cloud evidence artifacts. | UI shows latest gate status and evidence.                    | UI + API integration tests.                      |

---

## 18. Stop Conditions and Unverified Claims

Unverified:

* I ran focused local `./gradlew` test slices for touched YAPPC phase and kernel visibility services, but did not run full-repo `./gradlew`/`pnpm`/Vitest/Jest/Playwright/CI-gate suites end-to-end.
* The prior repo-level Gradle version-catalog parse blocker (`resilience4j-circuitbreaker-lib` bundle shape) was resolved by adding cataloged resilience4j library aliases + array bundle wiring and switching kernel-core to the catalog bundle.
* Focused verification now passes for the targeted evolve/learn handoff slice: `EvolutionServiceTest`, `DataCloudEvolutionExecutionHandoffServiceTest`, `EvolutionExecutionHandoffDispatcherTest`, and `EvolutionHandoffLifecycleIntegrationTest`.
* I did not fully inspect every backend handler corresponding to every route in `route-manifest.yaml`.
* I did not fully inspect `openapi.yaml`, so API parity findings are based on route manifest + frontend hook evidence, not full schema diff.
* Prompt registry/versioning/rollback claims from README were not fully validated in implementation.
* Human approval workflow implementation was not fully inspected.
* Artifact compiler/decompiler implementation was not fully inspected.
* AEP runtime execution path was not fully traced end-to-end.
* Frontend shared library usage was verified by dependency declarations and selected route files, not by complete component tree traversal.

Bottom line: YAPPC is making deterministic progress, but at commit `f302e89c8e7116e8821a7957b4a06a5d7dff81e`, the highest-value next work is to make **phase packet truth, Kernel handoff/observe, Data Cloud/AEP evidence, server-side authorization, and contract parity** production-real rather than partial/local/simplified.
