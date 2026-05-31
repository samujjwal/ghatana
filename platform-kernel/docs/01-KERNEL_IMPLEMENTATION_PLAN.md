Below is the implementation-ready task list for YAPPC at commit `021de7bdb28f75503704c1eb4373379627c0145b`, grouped to minimize verification rounds. I am excluding evidence-generation/release-proof tasks and focusing on root-cause product gaps, correctness, feature completeness, UI quality, SRP, abstraction, reuse, and duplicate elimination.

The grouping is intentional: finish all tasks in a group, then run that group’s verification once.

---

# Verification Round 1 — Backend Phase Models, Phase Packet Semantics, and Readiness

**Goal:** Replace generic/proxy phase panels with phase-native backend models. This is the highest-leverage root-cause fix because `PhasePacketAssembler` currently builds all lifecycle panels from shared readiness/evidence/activity inputs, which can make features appear complete without phase-specific product truth. The backend already owns `PhasePacket`, `phasePanels`, readiness, actions, run status, and degraded details, so this group should keep lifecycle truth backend-owned. 

## 1.1 Split `PhasePacketAssembler` into phase-specific providers

| ID           | Priority | File(s)                                                                                                       | What to change                                                                                                                                                                             | Why                                                                                                                                                                            | Verification                                                                                                   |
| ------------ | -------- | ------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------- |
| BE-PHASE-001 | P0       | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketAssembler.java` | Remove the large `buildPhasePanels(...)` method as the single place that builds all lifecycle panels. Keep `PhasePacketAssembler` only responsible for assembling the final `PhasePacket`. | Current assembler builds Intent, Shape, Validate, Generate, Run, Observe, Learn, and Evolve panels using generic signals, which violates SRP and weakens feature correctness.  | Unit test proves assembler delegates to phase panel providers and no longer embeds phase-specific panel logic. |
| BE-PHASE-002 | P0       | New: `IntentPhasePanelProvider.java`                                                                          | Create a provider that builds the Intent panel from a real `IntentPhaseModel`, not `evidence.size()`, `activityFeed.size()`, blockers, or generic readiness.                               | Intent must show actual goals, personas, constraints, success criteria, and missing intent fields. Current intent panel infers these from evidence/activity/blockers.          | Unit test with persisted intent fields shows exact cards and missing fields.                                   |
| BE-PHASE-003 | P0       | New: `ShapePhasePanelProvider.java`                                                                           | Create a provider that builds Shape panel from selected surfaces, runtime choices, architecture model, canvas sync status, dependency graph, and artifact lineage.                         | Shape currently infers surfaces/dependencies/modeling gaps from activity/evidence/blocker counts.                                                                              | Unit test proves selected surfaces and architecture decisions render from shape model.                         |
| BE-PHASE-004 | P1       | New: `ValidatePhasePanelProvider.java`                                                                        | Move validate-specific panel construction out of assembler. Include gate result, failed criteria, policy status, confidence, and remediation from validation model.                        | Validation is more advanced than other phases, but still assembled in the same generic class.                                                                                  | Unit test validates blocked/passed policy combinations.                                                        |
| BE-PHASE-005 | P1       | New: `GeneratePhasePanelProvider.java`                                                                        | Build Generate panel from generated artifacts, assurance status, review state, diff summary, build/test result, and Kernel handoff readiness.                                              | Generate feature completeness must go beyond blockers and readiness.                                                                                                           | Unit test covers generated artifacts ready, review required, failed assurance.                                 |
| BE-PHASE-006 | P1       | New: `RunPhasePanelProvider.java`                                                                             | Build Run panel from `PlatformRunStatus`, run history, retry/rollback/promote availability, target version, target environment, and remediation hints.                                     | Current run panel mainly reflects `platformRunStatus.status()` or runtime health.                                                                                              | Unit test covers no run, running, failed, rollbackable, promotable.                                            |
| BE-PHASE-007 | P1       | New: `ObservePhasePanelProvider.java`                                                                         | Build Observe panel from preview health, runtime health, SLO/incident/trace/dependency diagnostics, and recommended operator action.                                                       | Observe must be operationally useful, not only a preview health card.                                                                                                          | Unit test covers healthy, degraded dependency, incident, trace-linked status.                                  |
| BE-PHASE-008 | P1       | New: `LearnPhasePanelProvider.java`                                                                           | Build Learn panel from durable learned signals, agent governance health, approval state, confidence, rollback path, and source event.                                                      | Learn panel currently exists but must become workflow-backed.                                                                                                                  | Unit test covers no learned signals, pending approval, approved learning, rollback available.                  |
| BE-PHASE-009 | P1       | New: `EvolvePhasePanelProvider.java`                                                                          | Build Evolve panel from proposal, impact summary, diff summary, validation requirements, approval state, rollback path, and rerun target.                                                  | Evolve needs a real product evolution workflow, not only a panel from activity/evidence/governance.                                                                            | Unit test covers proposed, blocked, approved, rerun-ready evolution.                                           |
| BE-PHASE-010 | P1       | New: `PhasePanelProviderRegistry.java`                                                                        | Add registry mapping phase name to provider. `PhasePacketAssembler` asks registry for panel(s).                                                                                            | Prevent future `if/switch` expansion in assembler and keep SRP.                                                                                                                | Test ensures every lifecycle phase has exactly one provider.                                                   |

## 1.2 Add phase-native backend models

| ID           | Priority | File(s)                                                           | What to change                                                                                                                                                 | Why                                                      | Verification                                           |
| ------------ | -------- | ----------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- | ------------------------------------------------------ |
| BE-MODEL-001 | P0       | New: `IntentPhaseModel.java`, `IntentPhaseModelProvider.java`     | Define model fields: goals, personas, user journeys, constraints, success criteria, unresolved fields, version, source artifact IDs.                           | Intent panel must be driven by actual intent state.      | Provider unit test with complete/incomplete intent.    |
| BE-MODEL-002 | P0       | New: `ShapePhaseModel.java`, `ShapePhaseModelProvider.java`       | Define selected surfaces, runtime/framework choices, architecture decisions, canvas document ID, UI builder document ID, dependencies, unresolved design gaps. | Shape phase must represent product architecture truth.   | Unit test with canvas out-of-sync and missing runtime. |
| BE-MODEL-003 | P1       | New: `GeneratePhaseModel.java`, `GeneratePhaseModelProvider.java` | Define generated artifact list, assurance results, diff summary, test/build status, ProductUnitIntent readiness.                                               | Generation quality must be explicit and user-reviewable. | Unit test covers generated app failing assurance.      |
| BE-MODEL-004 | P1       | New: `RunPhaseModel.java`, `RunPhaseModelProvider.java`           | Define latest run, status, target env/version, rollback/promote readiness, remediation, run history.                                                           | Run should not be only latest status.                    | Unit test covers run history and rollback target.      |
| BE-MODEL-005 | P1       | New: `ObservePhaseModel.java`, `ObservePhaseModelProvider.java`   | Define health checks, trace IDs, incidents, SLO status, dependency health, user-facing remediation.                                                            | Observe needs operational depth and 0 cognitive load.    | Unit test covers degraded dependency and incident.     |
| BE-MODEL-006 | P1       | New: `LearnPhaseModel.java`, `LearnPhaseModelProvider.java`       | Define learned signals, source events, confidence, approval state, rollback path, prompt/agent version impact.                                                 | Learn must be durable and governed.                      | Unit test covers pending approval and rollback.        |
| BE-MODEL-007 | P1       | New: `EvolvePhaseModel.java`, `EvolvePhaseModelProvider.java`     | Define proposal, impact analysis, diff summary, validation requirements, approval state, rerun target.                                                         | Evolve must close the loop.                              | Unit test covers approved evolve plan ready for rerun. |

## 1.3 Correct readiness semantics

| ID           | Priority | File(s)                                                        | What to change                                                                                                            | Why                                                                                                                                                              | Verification                                                      |
| ------------ | -------- | -------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| BE-READY-001 | P0       | `PhaseReadinessEvaluator.java`                                 | Replace one generic scoring path with phase-specific readiness policies. Keep common weighted scoring only as a fallback. | Current evaluator applies generic artifacts/evidence/governance/health scoring to all phases, which may block Intent/Shape/Learn/Evolve for non-native reasons.  | Unit tests per phase show correct missing prerequisites.          |
| BE-READY-002 | P0       | New: `PhaseReadinessPolicy.java`                               | Interface: `evaluate(PhaseReadinessInput): PhaseReadiness`.                                                               | Keeps SRP and prevents readiness logic from becoming a monolith.                                                                                                 | Compile + policy registry test.                                   |
| BE-READY-003 | P0       | New: `IntentReadinessPolicy.java`                              | Evaluate goals, personas, constraints, success criteria, and versioned intent completeness.                               | Intent should not depend primarily on preview/generation health.                                                                                                 | Test: incomplete personas blocks Intent with clear message.       |
| BE-READY-004 | P0       | New: `ShapeReadinessPolicy.java`                               | Evaluate selected surfaces, architecture model, runtime choices, canvas sync, dependency decisions.                       | Shape must not be inferred from activity counts.                                                                                                                 | Test: canvas out-of-sync blocks Shape.                            |
| BE-READY-005 | P1       | New: `GenerateReadinessPolicy.java`                            | Evaluate generated artifacts, build/test/assurance, review state, Kernel handoff readiness.                               | Generate readiness must reflect generated code correctness.                                                                                                      | Test: failed build blocks Generate.                               |
| BE-READY-006 | P1       | New: `RunReadinessPolicy.java`                                 | Evaluate Kernel/AEP run status, preview status, retry/rollback/promote state.                                             | Run needs runtime truth.                                                                                                                                         | Test: failed run with rollback target enables rollback readiness. |
| BE-READY-007 | P1       | New: `LearnReadinessPolicy.java`, `EvolveReadinessPolicy.java` | Evaluate learned signal approval and evolve proposal approval/rerun readiness.                                            | Learn/Evolve should become workflow phases.                                                                                                                      | Tests for approval required and rerun target.                     |

**Verification round 1 commands:**

```bash
./gradlew :products:yappc:core:yappc-services:test --tests '*Phase*'
./gradlew :products:yappc:core:yappc-services:test --tests '*Readiness*'
```

---

# Verification Round 2 — Kernel Handoff, ProductUnitIntent, and Run Actions

**Goal:** Finish Kernel-dependent feature correctness without adding release evidence tasks. The exporter/validator are now much better: they use `ProductUnitIntentDocument`, a contract registry, required tenant/workspace/project scope, and metadata redaction.  

## 2.1 ProductUnitIntent command correctness

| ID          | Priority | File(s)                                                      | What to change                                                                                                                        | Why                                                                                                                            | Verification                                                  |
| ----------- | -------- | ------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------- |
| KRN-CMD-001 | P1       | `ProductUnitIntentCommandService.java`                       | Add constructor injection for `ProductUnitIntentExporter`, `ProductUnitIntentValidationService`, and optional logger/output reporter. | Direct construction weakens SRP and makes testing/config injection harder.                                                     | Unit test injects fake exporter/validator.                    |
| KRN-CMD-002 | P1       | `ProductUnitIntentCommandService.java`, `CreateCommand.java` | Add explicit `--source-provider`; do not always set `sourceProvider = runtimeProvider`.                                               | Runtime provider and source provider are separate Kernel contract concepts. Current service sets both from `runtimeProvider`.  | CLI test validates source provider separately.                |
| KRN-CMD-003 | P1       | `CreateCommand.java`                                         | Remove unused `isValidIdentifier` method if validation is fully delegated to `ProductUnitIntentCommandService`.                       | Avoid duplicate validation logic. `CreateCommand` still has a private identifier validator.                                    | Static analysis/no unused method.                             |
| KRN-CMD-004 | P1       | `ProductUnitIntentCommandService.java`                       | Validate `surfaces` through `ProductUnitKernelContractRegistry` before exporter call, so errors are command-friendly.                 | Exporter validates surfaces, but command should report actionable user input errors.                                           | Unit test: unknown surface produces command-level message.    |
| KRN-CMD-005 | P1       | `ProductUnitIntentCommandService.java`                       | Add correlation ID support from CLI/API caller into `ProductUnitIntentExporter.Request`.                                              | Exporter supports correlation ID, command path does not pass it.                                                               | Unit test: generated intent contains provided correlation ID. |

## 2.2 ProductUnitIntent contract freshness and drift prevention

| ID               | Priority | File(s)                                   | What to change                                                                                                                | Why                                                                                                             | Verification                                                   |
| ---------------- | -------- | ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| KRN-CONTRACT-001 | P1       | `ProductUnitKernelContractRegistry.java`  | Add explicit contract source metadata: contract version, generatedAt/sourceCommit if available from resource.                 | Avoid stale `/kernel-product-unit-contract.json`. Registry currently loads resource and validates schema only.  | Unit test reads metadata.                                      |
| KRN-CONTRACT-002 | P1       | `ProductUnitKernelContractRegistry.java`  | Add clearer error for missing product kinds/statuses if contract resource contains empty sets.                                | Secondary constructor allows empty sets, but production contract must not.                                      | Test empty production contract fails.                          |
| KRN-CONTRACT-003 | P1       | `ProductUnitIntentValidationService.java` | Validate `sourceProvider` against `contractRegistry.isSourceProviderKnown`, not only non-empty.                               | Target registry provider is checked; source provider should also be checked.                                    | Unit test rejects unknown source provider.                     |
| KRN-CONTRACT-004 | P1       | `ProductUnitIntentExporter.java`          | Sanitize `canonicalSurfaceId(projectId, surface)` to ensure it stays within Kernel ID rules if surface has unsupported chars. | Current canonical ID is simple concatenation.                                                                   | Test odd surface strings are rejected or canonicalized safely. |

## 2.3 Kernel/AEP run action execution

| ID      | Priority | File(s)                                       | What to change                                                                                                                                 | Why                                                                      | Verification                                                              |
| ------- | -------- | --------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ | ------------------------------------------------------------------------- |
| RUN-001 | P1       | `PhaseActionAuthorizationService.java`        | Extract static action definitions into `PhaseActionCatalog`. Keep authorization service only for evaluation.                                   | Current service hardcodes all actions and evaluates them in one place.   | Unit test: catalog definitions are loaded and evaluator enables/disables. |
| RUN-002 | P1       | New: `PhaseActionCatalog.java`                | Define canonical actions: advance, configure, export, generate.apply/reject/rollback, run.retry/rollback/promote/observe.                      | Prevent duplicate action definitions across backend/frontend.            | Snapshot test of action IDs and operations.                               |
| RUN-003 | P1       | New or existing run action controller/service | Ensure `run.retry`, `run.rollback`, `run.promote` execute through Kernel/AEP adapter with idempotency key and audit type from action contract. | Actions should not be UI-only.                                           | Integration test: action changes run status and refreshes phase packet.   |
| RUN-004 | P1       | `RunActionContext.java`                       | Ensure `RunActionContext.fromPlatformRunStatus(...)` carries latest run ID, rollback target, promote target, risk reason, evidence IDs.        | Frontend depends on backend run context for run actions.                 | Unit test with no run, failed run, successful run.                        |
| RUN-005 | P1       | `PlatformRunStatusService` implementation     | Verify or implement production adapter for latest run status from Kernel/AEP/Data Cloud.                                                       | The port exists, but completeness depends on implementation.             | Integration test using fake Kernel/AEP run store.                         |

**Verification round 2 commands:**

```bash
./gradlew :products:yappc:core:scaffold:api:test --tests '*ProductUnitIntent*'
./gradlew :products:yappc:core:yappc-services:test --tests '*RunAction*'
./gradlew :products:yappc:core:yappc-services:test --tests '*PlatformRunStatus*'
```

---

# Verification Round 3 — Learn and Evolve as Real Workflows

**Goal:** Make Learn and Evolve complete product features, not just typed panels. `PhasePacket` already includes `LearningInsightPanel` and `EvolutionPlanPanel`, and `PhasePacketAssembler` calls `LearningInsightService` and `EvolutionPlanService`.  

## 3.1 Learn workflow

| ID        | Priority | File(s)                                                                | What to change                                                                                                                 | Why                                                      | Verification                               |
| --------- | -------- | ---------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------- | ------------------------------------------ |
| LEARN-001 | P1       | `LearningInsightService.java`                                          | Ensure service reads durable learned signals, not only activity feed or agent governance health.                               | Learn must be workflow-backed.                           | Unit test with durable learned signal.     |
| LEARN-002 | P1       | New: `LearningSignalRepository.java` or existing Data Cloud repository | Persist learned signal with source event, confidence, recommendation, approval state, rollback path, tenant/workspace/project. | Required for real learning loop.                         | Repository tenant-isolation test.          |
| LEARN-003 | P1       | New: `LearningWorkflowService.java`                                    | Add workflow: source event → learned signal → approval required → approved/rejected → rollback path.                           | Learn phase must be actionable.                          | Service test for approval/rejection.       |
| LEARN-004 | P1       | `LearnPhasePanelProvider.java`                                         | Render durable learning state: signal, source event, confidence, approval, rollback.                                           | Replace secondary-signal-driven panel.                   | Panel provider test.                       |
| LEARN-005 | P2       | Frontend Learn phase surface                                           | Add user action for approve/reject learned recommendation when backend action exists.                                          | 0 cognitive load: user sees one clear learning decision. | Component test with approve/reject action. |

## 3.2 Evolve workflow

| ID         | Priority | File(s)                                                               | What to change                                                                                                                  | Why                                              | Verification                                                  |
| ---------- | -------- | --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ | ------------------------------------------------------------- |
| EVOLVE-001 | P1       | `EvolutionPlanService.java`                                           | Ensure service builds plan from durable proposal/impact/diff/approval state, not only readiness/activity/evidence/governance.   | Evolve must close product lifecycle.             | Unit test with durable evolution proposal.                    |
| EVOLVE-002 | P1       | New: `EvolutionPlanRepository.java` or existing Data Cloud repository | Persist evolution proposal, impact summary, diff summary, validation requirements, approval state, rollback path, rerun target. | Durable state is required.                       | Repository tenant-isolation test.                             |
| EVOLVE-003 | P1       | New: `EvolutionWorkflowService.java`                                  | Add workflow: propose → impact analyze → diff review → approve/reject → update intent/shape/artifacts → rerun target.           | Required for complete Evolve feature.            | Workflow service test.                                        |
| EVOLVE-004 | P1       | `EvolvePhasePanelProvider.java`                                       | Render proposal, impact, diff, approval, rollback, rerun target from `EvolvePhaseModel`.                                        | Avoid generic activity/evidence proxies.         | Provider test.                                                |
| EVOLVE-005 | P1       | Kernel handoff adapter / ProductUnitIntent update path                | Add updated ProductUnitIntent or Kernel change-request support for evolved lifecycle-governed products.                         | Evolve must integrate with Kernel when required. | E2E unit/integration test with updated intent/change request. |
| EVOLVE-006 | P2       | Frontend Evolve phase surface                                         | Show one clear next action: approve, revise, rerun validation, or regenerate.                                                   | 0 cognitive load.                                | Component test with each approval state.                      |

**Verification round 3 commands:**

```bash
./gradlew :products:yappc:core:yappc-services:test --tests '*Learning*'
./gradlew :products:yappc:core:yappc-services:test --tests '*Evolution*'
```

---

# Verification Round 4 — Frontend Cockpit, 0 Cognitive Load, UI Consistency

**Goal:** Keep the improved thin route/container/view structure, but reduce cognitive load and remove remaining client-side interpretation. The frontend now has first-class lifecycle routes and uses `YappcPageShell`, `PhaseCockpitContainer`, i18n, and design-system `Alert`.   

## 4.1 Reduce `PhaseCockpitContainer` responsibility

| ID          | Priority | File(s)                                                                        | What to change                                                                                                                     | Why                                                                             | Verification                                             |
| ----------- | -------- | ------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- | -------------------------------------------------------- |
| FE-CONT-001 | P1       | `products/yappc/frontend/web/src/routes/app/project/PhaseCockpitContainer.tsx` | Extract view-model creation to `usePhaseCockpitViewModel.ts`.                                                                      | Container still maps packet, actions, labels, status, degradation, and state.   | Unit test for hook output with packet states.            |
| FE-CONT-002 | P1       | New: `usePhaseCockpitViewModel.ts`                                             | Build all props for `PhaseCockpitView`: primary action, action sections, status, degraded, current state, advanced details labels. | Keeps container thin and view stable.                                           | Hook tests for ready, blocked, degraded, missing packet. |
| FE-CONT-003 | P1       | `PhaseCockpitContainer.tsx`                                                    | Leave only route context, hook calls, guard states, and call to `PhaseCockpitView`.                                                | SRP and easier UI testing.                                                      | Snapshot/component test.                                 |

## 4.2 Make `PhaseCockpitView` lower cognitive load

| ID        | Priority | File(s)                                      | What to change                                                                                                           | Why                                                                                                                                                           | Verification                                                        |
| --------- | -------- | -------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| FE-UX-001 | P1       | `PhaseCockpitView.tsx`                       | Reorder layout so first viewport always shows: current state, one primary next action, top blocker/reason.               | Users should instantly know what to do. Current view renders summary, error, current state, degraded, feedback, actions, and technical details in one stack.  | Visual/unit test verifies primary action and blocker visible first. |
| FE-UX-002 | P1       | `PhaseCockpitView.tsx`, `PhaseCockpitLayout` | Collapse evidence, governance, advanced tools, technical details by default unless degraded or blocked.                  | Reduce clutter and cognitive load.                                                                                                                            | Component test: secondary panels collapsed by default.              |
| FE-UX-003 | P2       | `PhasePacketSummary.tsx`                     | Make summary concise: phase, readiness, blocker count, latest status. Avoid repeated data already in current state card. | Prevent duplicate information.                                                                                                                                | Component snapshot.                                                 |
| FE-UX-004 | P2       | `PhaseActionSection.tsx`                     | Group actions by urgency: primary, review-required, safe secondary, dangerous.                                           | Users should not scan many equivalent buttons.                                                                                                                | Component test with action categories.                              |
| FE-UX-005 | P2       | `PhaseDegradedPacketPanel.tsx`               | Present degraded state as “Dependency unavailable → Impact → Recovery action,” not technical details first.              | Degraded state should be understandable.                                                                                                                      | Component test for Data Cloud/AEP/Kernel degraded states.           |

## 4.3 Remove silent contract normalization

| ID              | Priority | File(s)                                  | What to change                                                                                                                          | Why                                                                          | Verification                                                   |
| --------------- | -------- | ---------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- | -------------------------------------------------------------- |
| FE-CONTRACT-001 | P1       | `usePhasePacket.ts`                      | Stop silently filtering malformed `phasePanels` in production-like test paths. Return clear packet contract error or degraded UI state. | Current hook filters panels by shape, which can hide backend contract bugs.  | Unit test malformed panel triggers explicit error in test/dev. |
| FE-CONTRACT-002 | P1       | `usePhasePacket.ts`                      | Move runtime normalization into generated API contract or explicit adapter with error telemetry.                                        | Avoid hidden UI drift.                                                       | Contract test.                                                 |
| FE-CONTRACT-003 | P2       | `types/phasePacket.ts`, generated client | Ensure `PhasePanelView`, `LearningInsightPanel`, `EvolutionPlanPanel`, and `PhaseAction` match Java `PhasePacket`.                      | Prevent Java/TS manual drift.                                                | `pnpm test:contract`.                                          |

## 4.4 Run context and action handling

| ID            | Priority | File(s)                     | What to change                                                                                                                     | Why                                                        | Verification                                                    |
| ------------- | -------- | --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- | --------------------------------------------------------------- |
| FE-ACTION-001 | P1       | `usePhaseActionHandlers.ts` | Make frontend `actionResult.runId` fallback transient only. For durable run actions, require refreshed backend packet run context. | Current priority allows frontend action result fallback.   | Test: after refresh, run action requires backend packet run ID. |
| FE-ACTION-002 | P1       | `usePhaseActionHandlers.ts` | Replace local operation maps with generated/shared action operation enum.                                                          | Avoid duplicate string maps for `generate.*` and `run.*`.  | Type test/action contract test.                                 |
| FE-ACTION-003 | P2       | `usePhaseActionHandlers.ts` | Translate `disabledReason` keys when they start with `phaseAction.` before composing feedback.                                     | Current feedback can show raw keys in some paths.          | Unit test disabled action feedback.                             |

**Verification round 4 commands:**

```bash
pnpm -C products/yappc/frontend/web test:unit
pnpm -C products/yappc/frontend/web test:integration
pnpm -C products/yappc/frontend/web test:contract
pnpm -C products/yappc/frontend/web inventory:components:check
```

---

# Verification Round 5 — Data Cloud, AEP, and Phase-Native Persistence

**Goal:** Ensure each phase uses real durable product state rather than generic evidence/activity proxies. Architecture states Data Cloud is the single source of truth and cross-product integration is routed through Data Cloud/AEP adapters. 

## 5.1 Data Cloud repositories and model providers

| ID     | Priority | File(s)                                                                             | What to change                                                                                                                    | Why                                                 | Verification                                             |
| ------ | -------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- | -------------------------------------------------------- |
| DC-001 | P0       | Existing Data Cloud adapter area under `products/yappc/infrastructure/datacloud/**` | Add or verify repositories for Intent, Shape, GenerationAssurance, Run, Observe, LearningSignal, EvolutionPlan.                   | Phase panels need phase-native durable data.        | Repository tests with tenant/workspace/project scope.    |
| DC-002 | P0       | `PhaseProjectStateService.java`                                                     | Ensure it returns enough typed lifecycle context for all phase-native providers, not a generic `Map` only.                        | Map-based state encourages proxy logic.             | Unit test returns typed project lifecycle snapshot.      |
| DC-003 | P1       | New: `ProjectLifecycleSnapshot.java`                                                | Introduce typed snapshot with tenant, workspace, project, lifecycle phase, feature flags, status, names, artifacts, runtime refs. | Reduce unsafe map lookups.                          | Compile + serialization test.                            |
| DC-004 | P1       | `PhaseEvidenceService.java`                                                         | Ensure phase evidence query is phase-native and scoped by tenant/workspace/project/phase.                                         | Evidence should support, not replace, phase models. | Unit test prevents cross-workspace evidence.             |
| DC-005 | P1       | `PhaseGovernanceService.java`                                                       | Ensure governance decisions include action/phase/policy IDs and are used by action authorization.                                 | Governance must block actions, not just display.    | Policy denial action test.                               |
| DC-006 | P1       | `PhaseActivityFeedService.java`                                                     | Ensure activity feed is supporting context only; do not use it as primary source for phase card semantics.                        | Activity count should not imply feature completion. | Panel tests no longer use activity count as main status. |

## 5.2 AEP/agent integration

| ID      | Priority | File(s)                          | What to change                                                                                                          | Why                                                          | Verification                                                |
| ------- | -------- | -------------------------------- | ----------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ | ----------------------------------------------------------- |
| AEP-001 | P1       | `PhaseHealthSignalProvider.java` | Ensure `AgentGovernanceHealth` is populated from AEP/agent registry and not left unknown when dependencies are healthy. | Learn phase depends on agent governance health.              | Unit test with healthy/quarantined/approval-required agent. |
| AEP-002 | P1       | `LearningInsightService.java`    | Pull source events from AEP/Data Cloud learning records.                                                                | Learning should be agentic and durable.                      | Unit test with source event.                                |
| AEP-003 | P1       | `EvolutionPlanService.java`      | Include AEP recommendations where relevant, but keep human approval/rollback governed.                                  | Evolve should use agents without becoming unsafe automation. | Unit test approval required.                                |
| AEP-004 | P1       | Run action adapter               | Run retry/rollback/promote must go through the correct Kernel/AEP boundary.                                             | Prevent duplicate execution logic in YAPPC.                  | Adapter integration test.                                   |

**Verification round 5 commands:**

```bash
./gradlew :products:yappc:infrastructure:datacloud:test
./gradlew :products:yappc:core:yappc-services:test --tests '*DataCloud*'
./gradlew :products:yappc:core:yappc-services:test --tests '*Aep*'
./gradlew :products:yappc:core:yappc-services:test --tests '*Governance*'
```

---

# Verification Round 6 — Shared Library Reuse, Duplicate Removal, and Contract Hygiene

**Goal:** Avoid regressions into duplicate YAPPC-specific abstractions. The frontend already depends on shared libraries like `@ghatana/design-system`, `@ghatana/i18n`, `@ghatana/product-shell`, `@ghatana/canvas`, `@ghatana/ui-builder`, `@ghatana/kernel-product-contracts`, and YAPPC shared libs. 

## 6.1 i18n standardization

| ID             | Priority | File(s)                                                                  | What to change                                                                                                                         | Why                                                                   | Verification                                  |
| -------------- | -------- | ------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- | --------------------------------------------- |
| REUSE-I18N-001 | P2       | `PhaseCockpitView.tsx`, `_phaseCockpit.tsx`, `PhaseCockpitContainer.tsx` | Standardize on `@ghatana/i18n` hook/helper. Avoid mixed local `translate` and hook usage unless there is a documented wrapper pattern. | Current code uses both `translate(...)` and `useTranslation(...)`.    | i18n test proves no raw user-visible strings. |
| REUSE-I18N-002 | P2       | `i18n/messages` or shared translation files                              | Add/verify keys for all phase panel summaries, recommendations, degraded states, action disabled reasons.                              | Backend returns many i18n keys; UI must resolve them consistently.    | Contract/i18n test.                           |

## 6.2 Action contract sharing

| ID               | Priority | File(s)                                                             | What to change                                                                                           | Why                                                             | Verification                             |
| ---------------- | -------- | ------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- | ---------------------------------------- |
| REUSE-ACTION-001 | P1       | Backend: `PhaseActionCatalog.java`; Frontend: generated/shared type | Create one canonical list of action IDs, server operations, categories, severity, post-success behavior. | Avoid duplicate backend hardcoding and frontend operation maps. | Backend + frontend action contract test. |
| REUSE-ACTION-002 | P1       | `usePhaseActionHandlers.ts`                                         | Replace `GENERATE_OPERATION_MAP` and `RUN_OPERATION_MAP` with generated/shared enum metadata.            | Prevent drift from backend.                                     | Type-level compile test.                 |

## 6.3 Design-system consistency

| ID           | Priority | File(s)                                                              | What to change                                                                                                                 | Why                                                     | Verification                           |
| ------------ | -------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------- | -------------------------------------- |
| REUSE-UI-001 | `P2`     | `components/phase/**`, `routes/app/project/**`                       | Replace one-off card/panel styling with shared YAPPC/design-system components where available.                                 | Consistent modern UI with fewer custom variants.        | `inventory:components:check`.          |
| REUSE-UI-002 | `P2`     | `PhaseCockpitLayout`, `PhasePrimaryActionCard`, `PhaseActionSection` | Make spacing, border, surface, typography, and CTA hierarchy consistent across all phase pages.                                | 0 cognitive load depends on visual consistency.         | Visual regression and component tests. |
| REUSE-UI-003 | `P2`     | `PhaseEmbeddedSurface.tsx`                                           | Ensure embedded canvas/UI-builder/preview surfaces reuse shared `@ghatana/canvas` / `@ghatana/ui-builder` components directly. | Avoid YAPPC-specific duplicate canvas/builder behavior. | Component import/dependency check.     |

## 6.4 Contract generation/parity

| ID                 | Priority | File(s)                                                         | What to change                                                                                           | Why                                             | Verification                                         |
| ------------------ | -------- | --------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | ----------------------------------------------- | ---------------------------------------------------- |
| REUSE-CONTRACT-001 | P1       | `PhasePacket.java`, TS `types/phasePacket.ts`, generated client | Ensure Java `PhasePacket` and TS `PhaseCockpitPacket` are generated or strictly checked.                 | Manual contract drift is high-risk.             | `pnpm test:contract`; backend serialization test.    |
| REUSE-CONTRACT-002 | P1       | `ProductUnitKernelContractRegistry.java`                        | Ensure Kernel contract resource is generated/imported from Kernel public contract, not manually curated. | Kernel values must remain canonical.            | Contract freshness test.                             |
| REUSE-CONTRACT-003 | P2       | route manifest/OpenAPI/generated client                         | Verify route/action contracts for phase packet, Kernel handoff, run actions, learn/evolve workflows.     | E2E feature completeness requires route parity. | API contract tests only, not release evidence tasks. |

**Verification round 6 commands:**

```bash
pnpm -C products/yappc/frontend/web test:contract
pnpm -C products/yappc/frontend/web inventory:components:check
./gradlew :products:yappc:core:yappc-services:test --tests '*Contract*'
./gradlew :products:yappc:core:scaffold:api:test --tests '*Contract*'
```

---

# Verification Round 7 — End-to-End Product Journeys

**Goal:** Validate user-visible feature completeness after the backend/frontend groups are done. The frontend package already has route inventory, component inventory, unit, integration, contract, regression, a11y, performance, and E2E script structure. 

## 7.1 Journey tests to add or update

| ID      | Priority | File(s)                                                              | What to change                                                                                   | Why                                  | Verification                                           |
| ------- | -------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ | ------------------------------------ | ------------------------------------------------------ |
| E2E-001 | P1       | `products/yappc/frontend/web/e2e/yappc-intent-to-shape.spec.ts`      | Test project opens → Intent complete/incomplete state → Shape navigates with real shape model.   | Verifies first two lifecycle phases. | Playwright.                                            |
| E2E-002 | P1       | `products/yappc/frontend/web/e2e/yappc-validate-generate.spec.ts`    | Test Validate blockers → remediation → Generate assurance state.                                 | Verifies validate/generate flow.     | Playwright with mocked backend packet or test backend. |
| E2E-003 | P1       | `products/yappc/frontend/web/e2e/yappc-kernel-handoff.spec.ts`       | Test Generate ProductUnitIntent → handoff prompt/API → Kernel run status appears in Run/Observe. | Verifies Kernel usage feature.       | Playwright/API mocked Kernel adapter.                  |
| E2E-004 | P1       | `products/yappc/frontend/web/e2e/yappc-run-actions.spec.ts`          | Test failed run → retry/rollback/promote action availability → action result refreshes packet.   | Verifies run action correctness.     | Playwright.                                            |
| E2E-005 | P1       | `products/yappc/frontend/web/e2e/yappc-observe-learn-evolve.spec.ts` | Test Observe issue → Learn recommendation → Approve learning → Evolve proposal → Rerun target.   | Verifies closed loop.                | Playwright.                                            |
| E2E-006 | P1       | `products/yappc/frontend/web/e2e/yappc-access-degraded.spec.ts`      | Test unauthorized user, Data Cloud degraded, AEP degraded, Kernel degraded.                      | Verifies fail-closed UX.             | Playwright.                                            |
| E2E-007 | P2       | `products/yappc/frontend/web/e2e/yappc-zero-cognitive-load.spec.ts`  | Assert first viewport shows phase, current state, primary next action, top blocker/recovery.     | Guards UI quality.                   | Playwright visual/DOM assertions.                      |

## 7.2 Minimal verification command after all implementation groups

```bash
pnpm -C products/yappc/frontend/web test:regression
pnpm -C products/yappc/frontend/web test:e2e
./gradlew :products:yappc:core:yappc-services:test
./gradlew :products:yappc:core:scaffold:api:test
```

---

# Consolidated File-by-File Change Map

Use this section as the direct “what/where” index.

## Backend phase service files

| File                                   | Change set                                                                                                                                         |
| -------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `PhasePacketServiceImpl.java`          | Keep orchestration only; delegate phase-native models/panels/readiness/action evaluation. Reduce nested promise readability issues where possible. |
| `PhasePacketAssembler.java`            | Remove phase-specific panel construction; delegate to `PhasePanelProviderRegistry`; keep final `PhasePacket` creation.                             |
| `PhaseReadinessEvaluator.java`         | Add phase-specific readiness policies; keep generic weighted logic only as fallback/common helper.                                                 |
| `PhaseActionAuthorizationService.java` | Extract hardcoded action catalog to `PhaseActionCatalog`; keep only authorization/evaluation.                                                      |
| `PlatformRunStatusService.java`        | Keep as port; ensure production adapter exists and is covered by tests.                                                                            |
| `DegradedPhasePacketFactory.java`      | Preserve known project/workspace context; improve degraded copy model and impacted feature mapping.                                                |
| `PhasePacket.java`                     | Keep canonical contract; ensure TS/generated client parity for new phase-native fields.                                                            |

## New backend phase model/provider files

| New file                                                      | Purpose                                                         |
| ------------------------------------------------------------- | --------------------------------------------------------------- |
| `IntentPhaseModel.java` / `IntentPhaseModelProvider.java`     | Intent-native goals/personas/constraints/success criteria.      |
| `ShapePhaseModel.java` / `ShapePhaseModelProvider.java`       | Shape-native surfaces/runtime/canvas/architecture/dependencies. |
| `GeneratePhaseModel.java` / `GeneratePhaseModelProvider.java` | Generated artifact assurance and Kernel handoff readiness.      |
| `RunPhaseModel.java` / `RunPhaseModelProvider.java`           | Run history/status/actions/rollback/promote context.            |
| `ObservePhaseModel.java` / `ObservePhaseModelProvider.java`   | Runtime diagnostics, health, incidents, remediation.            |
| `LearnPhaseModel.java` / `LearnPhaseModelProvider.java`       | Durable learned signals and approval status.                    |
| `EvolvePhaseModel.java` / `EvolvePhaseModelProvider.java`     | Evolution proposal/impact/diff/approval/rerun.                  |
| `PhasePanelProvider.java` / `PhasePanelProviderRegistry.java` | SRP boundary for phase panel creation.                          |
| `PhaseReadinessPolicy.java` + per-phase policies              | Phase-native readiness logic.                                   |
| `PhaseActionCatalog.java`                                     | Canonical action definitions.                                   |
| `LearningWorkflowService.java`                                | Durable learning workflow.                                      |
| `EvolutionWorkflowService.java`                               | Durable evolve workflow.                                        |

## Kernel/scaffold files

| File                                      | Change set                                                                                                            |
| ----------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `ProductUnitIntentExporter.java`          | Keep typed contract export; sanitize surface IDs; preserve metadata redaction; include correlation path consistently. |
| `ProductUnitIntentValidationService.java` | Validate source provider against contract registry; improve contract error messages.                                  |
| `ProductUnitKernelContractRegistry.java`  | Add source metadata/freshness and fail on incomplete production contract sets.                                        |
| `CreateCommand.java`                      | Remove duplicate validation helpers; pass source provider/correlation ID; keep CLI parsing only.                      |
| `ProductUnitIntentCommandService.java`    | Add dependency injection; support explicit source provider; improve command-friendly validation.                      |

## Frontend files

| File                           | Change set                                                                                                        |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| `routes.ts`                    | Keep route structure; add/verify E2E journey coverage.                                                            |
| `_phaseCockpit.tsx`            | Keep thin route pattern; avoid adding logic.                                                                      |
| `PhaseCockpitContainer.tsx`    | Extract view model logic into `usePhaseCockpitViewModel`.                                                         |
| `PhaseCockpitView.tsx`         | Reduce first-view content; collapse secondary panels; improve hierarchy.                                          |
| `usePhasePacket.ts`            | Stop silently filtering malformed backend panels; strengthen contract handling.                                   |
| `usePhaseActionHandlers.ts`    | Replace local operation maps with shared/generated action enum; remove durable run fallback from frontend result. |
| `PhasePacketSummary.tsx`       | Simplify summary; avoid duplicate info.                                                                           |
| `PhaseActionSection.tsx`       | Group by urgency/category; ensure consistent action display.                                                      |
| `PhaseDegradedPacketPanel.tsx` | Show dependency → impact → recovery action clearly.                                                               |
| `types/phasePacket.ts`         | Keep parity with Java `PhasePacket`.                                                                              |
| `e2e/*.spec.ts`                | Add lifecycle journey specs listed above.                                                                         |

---

# Recommended Execution Order

1. **Round 1:** Backend phase-native models, panel providers, readiness policies.
2. **Round 2:** Kernel/ProductUnitIntent/run action correctness.
3. **Round 3:** Learn/Evolve durable workflows.
4. **Round 4:** Frontend cockpit decomposition and 0-cognitive-load UI.
5. **Round 5:** Data Cloud/AEP phase-native persistence and adapters.
6. **Round 6:** Reuse, contracts, duplicate cleanup.
7. **Round 7:** E2E lifecycle journeys.

This order minimizes repeated verification because each round changes a coherent slice: backend semantics first, then Kernel/run semantics, then durable learning/evolution, then UI, then persistence/adapter hardening, then contract cleanup, and finally full E2E validation.
