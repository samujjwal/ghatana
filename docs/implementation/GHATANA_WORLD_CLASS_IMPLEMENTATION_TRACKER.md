Below is the grouped implementation task list. I grouped tasks by files and related verification passes so you can make changes in one area, then run one focused validation round for that area instead of repeatedly retesting the whole product.

## Verification Round 1 â€” Phase action correctness and run/generate context

This is the highest-priority group because it affects correctness of Generate and Run phase actions. Current frontend action handling depends on `actionResult.runId` after an action is executed, while the backend packet already carries platform run status and backend action metadata. The action handler also supplies frontend defaults such as `previous-stable` and `staging`, which should come from backend action parameters instead.  

| Task  | File(s)                                                                                        | What to change                                                       | Details                                                                                                                                                                                                                                                                           | Acceptance criteria                                                                                                              |
| ----- | ---------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
**Focused verification:** `pnpm -C products/yappc/frontend/web test:unit` and the specific `usePhaseActionHandlers` test file.

---

## Verification Round 2 â€” Backend phase action contract, i18n keys, and evidence-backed actions

Backend authorization is now centralized in `PhaseActionAuthorizationService`, which is good, but some action labels/descriptions are raw English strings while others are keys. Suggestion parameters also contain an empty evidence list.   

| Task  | File(s)                                                                                                                  | What to change                                        | Details                                                                                                                                                                                                             | Acceptance criteria                                                                                                                 |
| ----- | ------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| P1-01 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhaseActionAuthorizationService.java` | Replace hardcoded labels/descriptions with i18n keys. | Change labels such as `"Apply recommendation"`, `"Reject recommendation"`, `"Retry run"`, `"Rollback run"`, `"Promote run"`, `"Open observe phase"` to keys like `phaseAction.generateApply.label`.                 | Every `PhaseAction.label`, `description`, and `disabledReason` is either a translation key or intentionally machine-readable value. |
| P1-02 | `PhaseActionAuthorizationService.java`                                                                                   | Add action evidence.                                  | Change `suggestionParameters(...)` to accept evidence IDs from phase evidence, governance, blockers, and platform run status. Add `parameters.evidenceIds`, `parameters.supportTrace`, and `parameters.riskReason`. | Suggested actions explain why they are recommended and what evidence supports them.                                                 |
| P1-03 | `PhaseActionAuthorizationService.java`                                                                                   | Backend-provided run targets.                         | For run rollback/promote actions, populate `parameters.targetVersion` and `parameters.targetEnvironment` only when known; otherwise disable action with a precise reason.                                           | Frontend can execute actions without inventing deployment target details.                                                           |
| P1-04 | `PhaseActionAuthorizationService.java`                                                                                   | Remove generic unauthorized reasons.                  | Replace broad `phaseAction.disabled.unauthorized` for phase mismatch with a more precise key such as `phaseAction.disabled.notAvailableForCurrentPhase`.                                                            | UI explains why actions are unavailable without sounding like a permission error.                                                   |
| P1-05 | `products/yappc/core/yappc-services/src/test/java/.../PhaseActionAuthorizationServiceTest.java`                          | Add tests.                                            | Cover generate actions, run post-actions, feature flag dependency degraded, policy denied, missing target version/env, and evidence parameter propagation.                                                          | Authorization contract is deterministic and backend-owned.                                                                          |

**Focused verification:** backend unit tests for `PhaseActionAuthorizationService`.

---

## Verification Round 3 â€” Backend phase packet assembly and lifecycle panel completeness

`PhasePacket` now supports backend-owned `PhasePanelView`, `LearningInsightPanel`, and `EvolutionPlanPanel`, but `PhasePacketAssembler` only returns panels for Generate, Run, Observe, Learn, and Evolve. Intent, Shape, and Validate still lack equivalent typed backend panel coverage.   

| Task  | File(s)                                                                                                       | What to change                                                           | Details                                                                                                                                                                    | Acceptance criteria                                           |
| ----- | ------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| P1-06 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketAssembler.java` | Add Intent panel.                                                        | Add `PhasePanelView` for intent with status, summary, recommendation, goals/personas/constraints/success criteria cards if available from evidence/activity/project state. | `phasePanels` is non-empty for Intent.                        |
| P1-07 | `PhasePacketAssembler.java`                                                                                   | Add Shape panel.                                                         | Add cards for selected surfaces, architecture shape, canvas/artifact graph status, dependencies, and modeling gaps.                                                        | Shape cockpit can render backend-owned shape status.          |
| P1-08 | `PhasePacketAssembler.java`                                                                                   | Add Validate panel.                                                      | Add cards for gate result, missing artifacts, policy outcome, confidence, and exact remediation.                                                                           | Validate cockpit clearly shows what is blocked and why.       |
| P1-09 | `PhasePacketAssembler.java`                                                                                   | Replace backend English strings with semantic keys or structured values. | Current panel summaries and recommendations are raw English strings. Move to keys or structured `messageKey + values` pattern.                                             | Frontend can localize panel content consistently.             |
| P1-10 | `products/yappc/core/yappc-services/src/test/java/.../PhasePacketAssemblerTest.java`                          | Add tests for all eight phases.                                          | Verify every lifecycle phase returns typed panel content with stable owner/support trace/card IDs.                                                                         | Intent/Shape/Validate no longer fall through to empty panels. |

**Focused verification:** backend unit tests for `PhasePacketAssembler` plus phase packet serialization tests.

---

## Verification Round 4 â€” Readiness evaluator configuration and correctness

Readiness is now weighted and uses artifacts, blockers, evidence, governance, and health signals. That is much better than the prior simplified logic, but the weights and thresholds are hardcoded. 

| Task  | File(s)                                                                                                          | What to change               | Details                                                                                                                                                               | Acceptance criteria                                                   |
| ----- | ---------------------------------------------------------------------------------------------------------------- | ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| P2-01 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhaseReadinessEvaluator.java` | Move weights to config.      | Replace hardcoded weights `artifactScore * 0.40`, `blockerScore * 0.25`, etc. with phase-specific config from lifecycle/stage config.                                 | Readiness weights are configurable per phase.                         |
| P2-02 | `PhaseReadinessEvaluator.java`                                                                                   | Make threshold configurable. | Replace `completenessScore >= 0.90` with configured threshold.                                                                                                        | Validate/Run can have stricter threshold than Intent/Shape if needed. |
| P2-03 | `PhaseReadinessEvaluator.java`                                                                                   | Improve degraded semantics.  | `isDegraded` currently depends on completed artifact query unavailability; include degraded evidence, governance, health, and project state degradation consistently. | `readiness.isDegraded` accurately reflects any degraded dependency.   |
| P2-04 | `products/yappc/core/yappc-services/src/test/java/.../PhaseReadinessEvaluatorTest.java`                          | Add config-driven tests.     | Cover all phase weights, missing artifacts, degraded evidence, denied governance, unhealthy runtime, inactive project.                                                | Readiness score and missing prerequisites are deterministic.          |

**Focused verification:** backend unit tests for readiness only.

---

## Verification Round 5 â€” Phase cockpit frontend cleanup and 0-cognitive-load panels

The phase route is now thin and uses `YappcPageShell` and `PhaseCockpitContainer`, which is a strong abstraction improvement. However, shell copy is still hardcoded, and `PhaseEmbeddedSurface` has static placeholder panels for several phases.  

| Task  | File(s)                                                                                                 | What to change                                                   | Details                                                                                                              | Acceptance criteria                                                                            |
| ----- | ------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| P1-11 | `products/yappc/frontend/web/src/routes/app/project/_phaseCockpit.tsx`                                  | Move phase labels/title/description to i18n or backend metadata. | Replace `PHASE_LABEL` and hardcoded description with translation keys or packet metadata.                            | No hardcoded visible shell copy in phase route.                                                |
| P1-12 | `products/yappc/frontend/web/src/routes/app/project/PhaseEmbeddedSurface.tsx`                           | Replace Intent placeholder.                                      | Render `IntentWorkspacePanel` backed by phase packet/panel data.                                                     | Intent advanced area is useful, not explanatory text.                                          |
| P1-13 | `PhaseEmbeddedSurface.tsx`                                                                              | Replace Validate placeholder.                                    | Render `ValidateGateReviewPanel`.                                                                                    | Validate advanced area shows gate and policy details.                                          |
| P1-14 | `PhaseEmbeddedSurface.tsx`                                                                              | Replace Learn placeholder.                                       | Render `LearningInsightPanelView` using `PhasePanelView.learningInsight`.                                            | Learn phase has visible learning signal, confidence, approval, rollback.                       |
| P1-15 | `PhaseEmbeddedSurface.tsx`                                                                              | Replace Evolve placeholder.                                      | Render `EvolutionPlanPanelView` using `PhasePanelView.evolutionPlan`.                                                | Evolve phase shows proposal, impact, diff, validation, approval state, rollback, rerun target. |
| P2-05 | `products/yappc/frontend/web/src/routes/app/project/PhaseCockpitView.tsx`                               | i18n â€œTechnical details.â€                                        | Replace hardcoded â€œTechnical detailsâ€ with key and improve label to â€œDependency detailsâ€ or â€œWhy this is degraded.â€  | Degraded detail disclosure is human-friendly and localized.                                    |
| P2-06 | `products/yappc/frontend/web/src/routes/app/project/__tests__/phase-cockpit-i18n.test.ts`               | Extend i18n checks.                                              | Include `_phaseCockpit.tsx`, `PhaseCockpitView.tsx`, `PhaseEmbeddedSurface.tsx`.                                     | Static test catches visible hardcoded strings in phase cockpit.                                |
| P2-07 | `products/yappc/frontend/web/src/routes/app/project/__tests__/phase-cockpit-component-packets.test.tsx` | Add packet-driven panel tests.                                   | Render all eight phases with typed `phasePanels`; assert no placeholder text remains.                                | Phase UI is backend-packet driven.                                                             |

**Focused verification:** `pnpm -C products/yappc/frontend/web test:unit` and `pnpm -C products/yappc/frontend/web test:contract`.

---

## Verification Round 6 â€” Dashboard SRP, visual consistency, and 0-cognitive-load home

The dashboard currently mixes data loading, action ranking, mutations, action registry integration, and large UI markup in one route file. It also uses custom button-as-card patterns and many hardcoded strings.   

| Task  | File(s)                                                                               | What to change                                                     | Details                                                                                                                                                                             | Acceptance criteria                                     |
| ----- | ------------------------------------------------------------------------------------- | ------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------- |
| P1-16 | `products/yappc/frontend/web/src/routes/dashboard.tsx`                                | Split route into container + sections.                             | Keep route as orchestration only. Extract `DashboardHero`, `DashboardQuickActions`, `DashboardDecisionBrief`, `DashboardActionStatusGrid`, `RecentProjectsPanel`, `WorkspacePanel`. | `dashboard.tsx` is small and readable.                  |
| P1-17 | new `products/yappc/frontend/web/src/components/dashboard/DashboardDecisionBrief.tsx` | Extract decision brief.                                            | Move `buildDashboardDecisionBrief` output rendering into presentational component.                                                                                                  | Decision brief has isolated props and tests.            |
| P1-18 | new `DashboardActionStatusGrid.tsx` / `DashboardActionStatusCard.tsx`                 | Extract action status cards.                                       | Use shared card/status/action primitives instead of local tone classes where available.                                                                                             | Blocked/review/safe cards are visually consistent.      |
| P1-19 | `dashboard.tsx` and new dashboard components                                          | Move visible text to i18n.                                         | Replace strings such as â€œWorkspace Home,â€ â€œResume work without detours,â€ â€œBlocked Work,â€ â€œReview Required,â€ etc.                                                                    | Dashboard passes i18n conformance.                      |
| P1-20 | dashboard components                                                                  | Replace `window.location.reload()` retry behavior.                 | Use query invalidation/refetch from workspace/dashboard action hook.                                                                                                                | Retry refreshes only dashboard data, not whole app.     |
| P1-21 | new `useDashboardDecision.ts`                                                         | Move decision logic into hook/service.                             | Move `buildDashboardDecisionBrief`, project action filtering, next action calculation.                                                                                              | Logic has unit tests independent of DOM.                |
| P1-22 | new `useDashboardActions.ts` or existing workspace hook                               | Centralize dashboard action execution.                             | Keep `executeDashboardAction` mutation out of route markup.                                                                                                                         | Action execution is reused by cards and decision brief. |
| P2-08 | dashboard components                                                                  | Remove â€œsemantic color as intentâ€ card backgrounds where possible. | Make intent explicit via title/icon/status text, not background color alone.                                                                                                        | Cards are accessible and visually consistent.           |
| P2-09 | `products/yappc/frontend/web/src/routes/app/__tests__/dashboard.test.tsx`             | Add dashboard component tests.                                     | Cover loading, guest, empty, degraded backend, blocked/review/safe action states, retry.                                                                                            | Dashboard changes verify in one test pass.              |

**Focused verification:** dashboard unit/component tests, i18n test, design-system consistency script.

---

## Verification Round 7 â€” Shell and design-system consistency

`YappcPageShell` is a good central abstraction, but its title uses muted text and the app still mixes several token vocabularies across surfaces. 

| Task  | File(s)                                                                   | What to change                        | Details                                                                                                                 | Acceptance criteria                           |
| ----- | ------------------------------------------------------------------------- | ------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- | --------------------------------------------- |
| P2-10 | `products/yappc/frontend/web/src/components/layout/YappcPageShell.tsx`    | Fix heading hierarchy.                | Change title from muted foreground to primary foreground token. Ensure description uses muted token.                    | Page title is visually primary.               |
| P2-11 | `YappcPageShell.tsx`                                                      | Add optional eyebrow/breadcrumb slot. | Use for phase/dashboard/admin surfaces consistently instead of ad hoc eyebrow text.                                     | All major pages use the same header pattern.  |
| P2-12 | dashboard, phase, admin, kernel-health routes                             | Standardize token namespace.          | Pick canonical DS tokens and stop mixing `text-fg-*`, `text-text-*`, `bg-bg-*`, `bg-surface-*` within the same surface. | Design consistency script passes.             |
| P2-13 | `products/yappc/frontend/web/scripts/check-design-system-consistency.mjs` | Extend rule coverage.                 | Add checks for raw semantic color card backgrounds and mixed token namespaces in route files.                           | New script catches inconsistent card styling. |

**Focused verification:** `pnpm -C products/yappc/frontend/web inventory:components:check`.

---

## Verification Round 8 â€” Kernel handoff and lifecycle truth hardening

Kernel handoff is now strong: `ProductUnitIntentExporter` uses `ProductUnitIntentDocument`, requires tenant/workspace, validates surfaces/providers/lifecycle profile against `ProductUnitKernelContractRegistry`, and no longer relies on local ad hoc maps.   Kernel runtime truth also has a Data Cloud-backed source with malformed-record degradation. 

| Task  | File(s)                                    | What to change                        | Details                                                                                                                         | Acceptance criteria                                               |
| ----- | ------------------------------------------ | ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| P1-23 | `ProductUnitKernelContractRegistry.java`   | Add contract freshness guard.         | Ensure `/kernel-product-unit-contract.json` is generated/imported from Kernel public contract source and cannot silently drift. | Build fails if local contract diverges.                           |
| P1-24 | `ProductUnitIntentExporter.java`           | Keep contract DTO path only.          | Add regression test proving output is converted from `ProductUnitIntentDocument`, not hand-built ad hoc map.                    | ProductUnitIntent shape remains contract-owned.                   |
| P2-14 | Kernel health UI/services                  | Display truth source.                 | Show whether Kernel truth source is Data Cloud/Event Cloud/local-dev.                                                           | Operator can identify whether runtime truth is production-backed. |
| P2-15 | `DataCloudKernelLifecycleTruthSource.java` | Improve malformed record diagnostics. | Include field-level malformed reason and recovery hint in degraded record.                                                      | UI can show exactly what lifecycle truth data is malformed.       |
| P2-16 | Kernel visibility tests                    | Add production guard test.            | Verify production profile rejects filesystem/local/mock truth source.                                                           | No accidental production use of `.kernel/out/products/**`.        |

**Focused verification:** Kernel bridge/backend unit tests only.

---

## Verification Round 9 â€” Route/API parity and feature coverage, without evidence-generation work

The route manifest is broad and covers product family, admin, intent, shape, validate, generate, run, observe, learn, evolve, artifact graph, preview session, capabilities, phase packet, and dashboard actions.    This pass should not focus on generating release evidence, only feature correctness and parity.

| Task  | File(s)                                                                    | What to change                                    | Details                                                                                                                     | Acceptance criteria                                                     |
| ----- | -------------------------------------------------------------------------- | ------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| P1-25 | `products/yappc/docs/api/route-manifest.yaml` and generated client mapping | Verify user-facing feature coverage.              | Confirm every lifecycle phase route has frontend use, backend handler, generated client method, and typed request/response. | No orphan lifecycle route.                                              |
| P1-26 | frontend generated API adapter/client                                      | Remove stale parity comments.                     | `usePhasePacket.ts` still references GET/POST parity task. If done, remove/replace with concise contract comment.           | Comments reflect current state.                                         |
| P1-27 | `products/yappc/frontend/web/src/lib/api/__tests__/apiContract.test.ts`    | Add phase route parity assertions.                | Validate phase packet, run retry/rollback/promote, evolve approve/reject, ProductUnitIntent generation.                     | Contract test catches missing client/route mismatch.                    |
| P2-17 | E2E matrix script                                                          | Ensure feature journeys, not evidence generation. | Validate journey coverage list but do not produce release evidence bundle.                                                  | Matrix ensures feature routes are reachable and actions map to backend. |

**Focused verification:** API contract tests and E2E matrix check, not release evidence bundle.

---

## Recommended execution order

1. **Round 1:** Fix run/generate context correctness first.
2. **Round 2:** Fix backend action contract/i18n/evidence parameters.
3. **Round 3:** Add complete backend panels for all lifecycle phases.
4. **Round 5:** Replace frontend placeholders with guided phase panels.
5. **Round 6 + 7:** Refactor dashboard and shell/design consistency together.
6. **Round 4:** Make readiness configurable after phase panel semantics stabilize.
7. **Round 8:** Add Kernel truth visibility and drift guard.
8. **Round 9:** Run route/API/feature parity checks.

This grouping keeps verification tight: backend action contract, backend phase packet, frontend phase cockpit, dashboard/UI consistency, Kernel truth, and API parity each get one focused validation round.
