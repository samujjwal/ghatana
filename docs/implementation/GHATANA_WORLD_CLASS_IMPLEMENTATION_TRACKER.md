Below is the consolidated implementation task list for YAPPC at commit `a25c5fe75cc35d3f15c550a5c79417bfeed4fdb8`, grouped to minimize verification rounds. I excluded evidence-generation-only work and grouped tasks by files/areas that should be changed and tested together.

# YAPPC Implementation Task Plan

## Verification Round 1 — Phase Packet, Readiness, Run Actions, Backend Correctness

**COMPLETED** — All tasks BE-01 through BE-09 have been implemented and verified. The build is green with 114 passing tests.

---

## Verification Round 2 — Kernel ProductUnitIntent, CLI Scope Safety, Contract Correctness

This round fixes Kernel handoff correctness and removes unsafe scope defaults. The exporter now uses the Kernel `ProductUnitIntentDocument` and contract registry, which is good. The remaining issue is CLI scoping and tenant safety.  

| ID     | Priority | File(s)                                                                                   | What to Change                                                        | Change Details                                                                                                                                                                           | Acceptance Criteria                                                              | Verification                                                |
| ------ | -------: | ----------------------------------------------------------------------------------------- | --------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| KRN-01 |       P0 | `products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/cli/CreateCommand.java` | Remove `default-tenant` default.                                      | Change `private String tenantId = "default-tenant";` to no default. Require `--tenant-id` for `kernel-product-unit`. Current default conflicts with fail-closed tenant boundary rules.   | CLI fails clearly when tenant is missing or `default-tenant`.                    | CLI tests for missing tenant, default tenant, valid tenant. |
| KRN-02 |       P0 | `CreateCommand.java`                                                                      | Validate tenant/workspace/project IDs before building intent.         | Add strict validation for blank, `default-tenant`, invalid characters, and project ID normalizing collisions.                                                                            | ProductUnitIntent scope always contains real tenant/workspace/project.           | CLI validation tests.                                       |
| KRN-03 |       P1 | `CreateCommand.java`                                                                      | Split Kernel ProductUnit create flow from generic project generation. | Extract `KernelProductUnitCreateCommandHandler` or `ProductUnitIntentCommandService` so `CreateCommand` remains a thin CLI command.                                                      | SRP: CLI parsing separate from ProductUnitIntent construction/validation/export. | Unit tests for extracted service + CLI integration test.    |
| KRN-04 |       P1 | `ProductUnitIntentExporter.java`                                                          | Preserve deterministic surface IDs.                                   | Current surface ID is `projectId + "-" + surface`. Ensure this cannot collide and is contract-approved. Add helper method for canonical surface ID.                                      | Surface IDs are stable and valid across repeated exports.                        | Exporter deterministic ID test.                             |
| KRN-05 |       P1 | `ProductUnitIntentExporter.java`                                                          | Add explicit metadata redaction guard.                                | Exporter accepts arbitrary metadata. Add metadata validation/redaction or delegate to validator so secrets are not emitted in ProductUnitIntent.                                         | ProductUnitIntent export rejects or redacts secret-like metadata keys.           | Exporter/validator test with `token`, `password`, `apiKey`. |
| KRN-06 |       P1 | `ProductUnitIntentValidationService.java`                                                 | Ensure validator uses the same contract registry as exporter.         | Keep one canonical contract registry path; do not duplicate provider/profile/surface lists in validator.                                                                                 | Exporter and validator cannot drift.                                             | Contract registry parity test.                              |

**Round 2 verification command set**

```bash
./gradlew :products:yappc:core:scaffold:api:test --tests '*ProductUnitIntent*' --tests '*CreateCommand*'
./gradlew :products:yappc:core:scaffold:api:check
```

---

## Verification Round 3 — Phase Cockpit Frontend Correctness, 0 Cognitive Load, Action Metadata

This round fixes the main user-facing bug and improves 0-cognitive-load behavior. The frontend phase cockpit is now much cleaner and split into shell/container/view/action hook, but one contract mismatch can hide suggestions.  

| ID    | Priority | File(s)                                                                        | What to Change                                                                            | Change Details                                                                                                                                                                                                     | Acceptance Criteria                                                                                                                   | Verification                                    |
| ----- | -------: | ------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------- |
| FE-01 |       P0 | `products/yappc/frontend/web/src/routes/app/project/phasePacketMappers.ts`     | Fix suggestion metadata key mismatch.                                                     | Backend emits `evidenceIds`; mapper expects `parameters.evidence`. Update mapper to read `evidenceIds`, or support both for transition.                                                                            | Suggested next steps render when backend provides confidence, evidenceIds, riskLevel, applyMode, approvalRequired, rollbackSupported. | `phasePacketMappers.test.ts`.                   |
| FE-02 |       P0 | `phasePacketMappers.ts`                                                        | Make suggestion parsing type-safe.                                                        | Add a small `parseActionSuggestionMetadata` helper with explicit `evidenceIds` validation. Avoid silently dropping actionable suggestions without test coverage.                                                   | Invalid metadata is safely ignored; valid metadata always renders.                                                                    | Unit tests for valid/invalid metadata.          |
| FE-03 |       P1 | `products/yappc/frontend/web/src/routes/app/project/usePhaseActionHandlers.ts` | Stop trusting client-built lifecycle preview.                                             | `phasePacketToPreview(packet)` is sent back to backend actions. Make backend compute/validate preview from packet/action ID; client should send action ID, phase, projectId, and correlation/action context only.  | Backend remains lifecycle truth; client does not provide authoritative readiness preview.                                             | API contract test + hook test.                  |
| FE-04 |       P1 | `usePhaseActionHandlers.ts`                                                    | Use backend disabled reasons as user guidance.                                            | When action disabled, show translated backend disabled reason instead of generic “reviewing action.”                                                                                                               | Disabled actions clearly explain what user must do next.                                                                              | Hook/component test for disabled action.        |
| FE-05 |       P1 | `PhaseCockpitContainer.tsx`                                                    | Do not hide backend-provided run actions solely due to local run context.                 | Current code filters `RUN_CONTEXT_OPERATIONS` when `runContextId` missing. Instead render the action disabled with backend disabled reason.                                                                        | User sees why rollback/promote/retry cannot run, instead of actions disappearing.                                                     | Component test.                                 |
| FE-06 |       P1 | `PhaseCockpitContainer.tsx`                                                    | Reduce fallback/action ambiguity.                                                         | If no primary backend action exists, show a clear no-action state with blockers/next steps. Do not fall back to first action unless backend marks it primary or safe.                                              | Primary card never suggests arbitrary fallback action.                                                                                | Component test.                                 |
| FE-07 |       P2 | `PhaseCockpitContainer.tsx`, `PhaseCockpitView.tsx`                            | Improve 0-cognitive-load summary.                                                         | Keep the current state card, blockers, and one primary next action above fold. Move detailed evidence/governance/action sections below progressive disclosure.                                                     | User sees current state, why blocked, and one next action without scanning.                                                           | Visual regression for all phases.               |
| FE-08 |       P2 | `PhaseCockpitView.tsx`                                                         | Replace inline feedback/result/error/degraded panels with shared common state components. | Current view uses inline Tailwind blocks for info/success/error/details. Use canonical common components to avoid style drift.                                                                                     | Phase cockpit uses shared UI state primitives.                                                                                        | `inventory:components:check` + component tests. |
| FE-09 |       P2 | `PhaseCockpitView.tsx`                                                         | Improve responsive spacing density.                                                       | `p-6 space-y-6` may be too roomy on smaller screens. Use `YappcPageShell`/design token spacing and responsive density.                                                                                             | Less scrolling, better mobile/tablet scanability.                                                                                     | Visual regression.                              |
| FE-10 |       P2 | `PhaseCockpitContainer.tsx`                                                    | Keep i18n consistent.                                                                     | It already uses `@ghatana/i18n`; verify all phase labels, fallback text, disabled reasons, and action labels are translation keys or backend-safe labels.                                                          | No raw user-visible English in phase cockpit path except safe data.                                                                   | `phase-cockpit-i18n.test.ts`.                   |

**Round 3 verification command set**

```bash
cd products/yappc/frontend/web
pnpm test:unit
pnpm test:integration
pnpm test:contract
pnpm inventory:components:check
```

---

## Verification Round 4 — Learn and Evolve Feature Completeness

This round strengthens the late lifecycle phases. Routes exist for Learn and Evolve, including context and proposal approve/reject operations, but the full guided flow needs confirmation and hardening. 

| ID    | Priority | File(s)                                                                                                                     | What to Change                                | Change Details                                                                                                                              | Acceptance Criteria                                                                          | Verification                      |
| ----- | -------: | --------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- | --------------------------------- |
| LE-01 |       P1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/LearnApiController.java` and related service files  | Verify/complete Learn with context flow.      | Ensure `learn` and `learn/with-context` consume run/evidence/governance context and produce typed learning insights.                        | Learn phase shows cause, signal, recommendation, confidence, approval need, and next action. | Learn controller/service tests.   |
| LE-02 |       P1 | `products/yappc/frontend/web/src/routes/app/project/learn.tsx`, phase panel components                                      | Add/verify guided Learn panel.                | Learn should not be a generic detail panel. It should show insight summary, root cause, evidence, confidence, and “apply to evolve” action. | User understands what was learned in one screen.                                             | Learn route component test + E2E. |
| EV-01 |       P1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/EvolveApiController.java` and related service files | Verify/complete evolution proposal lifecycle. | Ensure `evolve`, `evolve/with-constraints`, approve, reject produce durable proposal state and next lifecycle actions.                      | Evolve proposal has impact, diff, risk, rollback, approval state, next phase.                | Evolve service tests.             |
| EV-02 |       P1 | `products/yappc/frontend/web/src/routes/app/project/evolve.tsx`, phase panel components                                     | Add/verify guided Evolve panel.               | Show impact analysis, diff summary, risk, approval controls, and next lifecycle action in the Evolve phase.                                 | User can understand and approve/reject evolution without hunting through evidence.           | Evolve route test + E2E.          |
| EV-03 |       P1 | Learn/Evolve services + `PhasePacketServiceImpl.java`                                                                       | Connect Learn output to Evolve input.         | Evolve should be able to start from a learning insight, failed run, validation blocker, or user feedback.                                   | A Learn insight can create an Evolve proposal with traceability.                             | Learn→Evolve E2E.                 |
| EV-04 |       P2 | frontend phase panels                                                                                                       | Standardize Learn/Evolve panel layouts.       | Use same pattern as Run/Generate: current state, recommendation, evidence, actions, advanced details.                                       | Learn/Evolve are visually consistent with other phases.                                      | Visual regression.                |

**Round 4 verification command set**

```bash
./gradlew :products:yappc:core:yappc-services:test --tests '*Learn*' --tests '*Evolve*'
cd products/yappc/frontend/web
pnpm test:integration
pnpm test:e2e -- --grep "learn|evolve"
```

---

## Verification Round 5 — Shape, Canvas, Artifact Lineage, Generate Assurance

This round validates the product-creation core: shape → generate → artifact graph → generated output. The route manifest includes artifact graph, residual analysis, source import, generate, ProductUnitIntent, and diff routes.  

| ID     | Priority | File(s)                                           | What to Change                                                       | Change Details                                                                                                                                 | Acceptance Criteria                                              | Verification                        |
| ------ | -------: | ------------------------------------------------- | -------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- | ----------------------------------- |
| SH-01  |       P1 | Shape controller/service files, shape route files | Verify shape persistence as canonical Data Cloud state.              | Ensure canvas/UI-builder state is not frontend-only; it must persist to canonical shape/artifact model.                                        | Refreshing the page restores exact shape from backend state.     | Shape persistence integration test. |
| SH-02  |       P1 | shape/canvas/frontend mapper files                | Add traceability from intent → shape.                                | Every shape artifact should reference source intent/version/evidence.                                                                          | Shape view shows what intent drove the architecture.             | Intent→Shape integration test.      |
| SH-03  |       P1 | artifact graph services/controllers               | Ensure shape → artifact graph connection.                            | Generated artifacts should link to shape nodes and model choices.                                                                              | Artifact graph query can answer “why was this generated?”        | Artifact graph lineage test.        |
| GEN-01 |       P1 | Generation controller/service files               | Verify generation assurance output.                                  | Generated code should include validation/assurance summary: compile/test/static checks, known limitations, generated files, and rollback plan. | Generate phase shows generated output quality clearly.           | Generate assurance test.            |
| GEN-02 |       P1 | Generate frontend panels                          | Add canonical generated artifact review panel if missing/incomplete. | Show generated files, changed files, validation status, diff, apply/reject/rollback actions.                                                   | User can review generated output without leaving cockpit.        | Generate review component/E2E test. |
| GEN-03 |       P2 | Artifact compiler/decompiler integration          | Verify reuse of `yappc-artifact-compiler` and config compiler.       | Avoid product-local duplicate compilers. Use existing frontend/backend compiler abstractions.                                                  | No duplicate artifact/config compiler logic in YAPPC route code. | Component/import inventory check.   |

**Round 5 verification command set**

```bash
./gradlew :products:yappc:core:yappc-services:test --tests '*Shape*' --tests '*Generation*' --tests '*Artifact*'
cd products/yappc/frontend/web
pnpm test:e2e:canvas
pnpm test:integration
```

---

## Verification Round 6 — Admin, Product Family, Shell Consistency, Modern UI

This round improves modern UI consistency and low cognitive load across non-phase surfaces. YAPPC routes now include `kernel-health`, `product-family`, admin prompt versions, A/B testing, feature flags, and admin observability. 

| ID    | Priority | File(s)                                                         | What to Change                                                        | Change Details                                                                                                                           | Acceptance Criteria                                                             | Verification                    |
| ----- | -------: | --------------------------------------------------------------- | --------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- | ------------------------------- |
| UI-01 |       P2 | `products/yappc/frontend/web/src/routes/app/admin/*.tsx`        | Standardize admin routes under one admin shell.                       | Prompt versions, A/B tests, feature flags, and observability should share page title, description, summary cards, table/action patterns. | Admin pages feel like one product, not separate tools.                          | Admin visual regression.        |
| UI-02 |       P2 | `products/yappc/frontend/web/src/routes/app/product-family.tsx` | Standardize product-family page with YAPPC shell/card/table patterns. | Product-family should use same layout density, cards, actions, empty/loading/error states.                                               | Product-family page has 0 cognitive load and consistent styling.                | Product-family component test.  |
| UI-03 |       P2 | `kernel-health*.tsx` routes                                     | Align Kernel health pages with phase cockpit visual language.         | Use same health status, evidence, action, and degraded patterns.                                                                         | Kernel health feels native to YAPPC.                                            | Kernel health visual test.      |
| UI-04 |       P2 | shared route state components                                   | Consolidate empty/loading/error/unauthorized/degraded states.         | Use common state components everywhere instead of page-local blocks.                                                                     | Same empty/loading/error UX across phase, admin, product-family, kernel-health. | Component inventory check.      |
| UI-05 |       P2 | tables/lists in admin/product-family/kernel-health              | Use one table/list abstraction.                                       | Any tabular data should use the canonical table/list component with consistent sorting, actions, empty states.                           | No duplicate one-off tables.                                                    | Component inventory + UI tests. |
| UI-06 |       P2 | global shell/navigation                                         | Ensure navigation is obvious and minimal.                             | Keep top-level items focused: dashboard/projects, product family, kernel health/admin only where authorized.                             | User never has to guess where to go next.                                       | Navigation E2E.                 |

**Round 6 verification command set**

```bash
cd products/yappc/frontend/web
pnpm inventory:components:check
pnpm test:e2e:visual
pnpm test:a11y
```

---

## Verification Round 7 — Route/API/Contract Parity and SRP Cleanup

This round should be last because earlier backend/frontend changes may affect contracts. The route manifest is broad and detailed, and the server routes appear aligned for major lifecycle endpoints.  

| ID     | Priority | File(s)                                                                               | What to Change                                                        | Change Details                                                                                                                                | Acceptance Criteria                                        | Verification                                     |
| ------ | -------: | ------------------------------------------------------------------------------------- | --------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- | ------------------------------------------------ |
| API-01 |       P1 | `products/yappc/docs/api/route-manifest.yaml`, `openapi.yaml`, `YappcHttpServer.java` | Reconcile manifest ↔ OpenAPI ↔ server route parity.                   | Ensure every route in manifest exists in server/OpenAPI and every server route is in manifest.                                                | No missing or extra lifecycle/admin/product-family routes. | `RouteManifestParityTest`, OpenAPI parity check. |
| API-02 |       P1 | generated frontend API client                                                         | Regenerate/verify client after backend route/action contract changes. | Ensure `LifecycleService`, phase packet, run actions, Learn/Evolve clients match OpenAPI.                                                     | Frontend compiles with generated client only.              | API contract tests.                              |
| API-03 |       P1 | `PhasePacket` type definitions Java/TS                                                | Align action metadata contract.                                       | Ensure Java `PhaseAction.parameters` and TS `PhaseAction.parameters` agree on `evidenceIds`, run context, target version/environment.         | No TS mapper guessing.                                     | Java/TS contract test.                           |
| SRP-01 |       P2 | `PhasePacketServiceImpl.java` + DI module                                             | Move default dependency construction out of service constructors.     | The service should orchestrate only; module/factory should wire defaults.                                                                     | Single clean constructor for service.                      | DI construction test.                            |
| SRP-02 |       P2 | `YappcHttpServer.java`                                                                | Consider route registration extraction.                               | `YappcHttpServer` registers many routes; extract lifecycle/admin/product-family route modules if ActiveJ style allows without hiding clarity. | Server route setup remains readable and grouped.           | Route parity test.                               |
| SRP-03 |       P2 | frontend project route files                                                          | Keep thin routes.                                                     | `_phaseCockpit.tsx` is now thin; enforce same pattern across admin/product-family/kernel-health.                                              | Route files delegate to containers/views.                  | Component architecture test or lint rule.        |

**Round 7 verification command set**

```bash
./gradlew :products:yappc:core:yappc-services:test --tests '*RouteManifest*' --tests '*Authorization*'
cd products/yappc/frontend/web
pnpm test:contract
pnpm typecheck
```

---

# Minimal Verification Strategy

To minimize verification rounds, implement in this order:

1. **Round 1 + Round 2 together if backend-focused engineer is available**
   Phase packet/action/readiness + Kernel CLI contract.
   This catches most P0 correctness issues.

2. **Round 3 immediately after Round 1**
   Frontend phase cockpit depends on backend action metadata, so verify after backend changes.

3. **Round 4 + Round 5 together if feature-completeness pass is desired**
   Learn/Evolve + Shape/Generate lineage.
   These are lifecycle completeness features.

4. **Round 6 after UI behavior stabilizes**
   UI consistency pass across phase/admin/product-family/kernel-health.

5. **Round 7 last**
   Route/API/contract/SRP cleanup after all contracts settle.

# Final Ordered Task List

| Order | Task IDs                                       | Verification Round |
| ----: | ---------------------------------------------- | ------------------ |
|     1 | BE-01, BE-02, BE-03, BE-04, BE-05              | Round 1            |
|     2 | BE-06, BE-07, BE-08, BE-09                     | Round 1            |
|     3 | KRN-01, KRN-02, KRN-03, KRN-04, KRN-05, KRN-06 | Round 2            |
|     4 | FE-01, FE-02, FE-03, FE-04, FE-05, FE-06       | Round 3            |
|     5 | FE-07, FE-08, FE-09, FE-10                     | Round 3            |
|     6 | LE-01, LE-02, EV-01, EV-02, EV-03, EV-04       | Round 4            |
|     7 | SH-01, SH-02, SH-03, GEN-01, GEN-02, GEN-03    | Round 5            |
|     8 | UI-01, UI-02, UI-03, UI-04, UI-05, UI-06       | Round 6            |
|     9 | API-01, API-02, API-03, SRP-01, SRP-02, SRP-03 | Round 7            |
