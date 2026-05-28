Below is the full implementation task list grouped to minimize verification rounds. The grouping is based on the YAPPC audit at commit `c2fe33114186370ec5965656ed5c61f60e8176c5`.

The most efficient verification order is:

1. **Backend phase packet/action truth**
2. **Frontend phase cockpit simplification**
3. **Kernel ProductUnitIntent contract hardening**
4. **Adapter-seam / reuse-first cleanup**
5. **Learn/Evolve feature completion**
6. **Shell/admin/kernel-health/product-family UI consistency**
7. **Docs and test alignment**

This avoids re-running broad regression checks after every small file change.

## Execution Update (2026-05-27)

Completed in current implementation wave:

- Kernel verification blocker cleared: `:products:yappc:kernel-bridge:test` now passes after stabilizing `YappcKernelDataCloudE2ETest` mapper stubs.
- Frontend cockpit action section generalized around backend action metadata:
	- generic grouped action section rendering in `PhaseCockpitView.tsx`
	- reusable section model in `PhaseActionSection.tsx`
	- backend-driven category grouping and run-context fallbacks in `PhaseCockpitContainer.tsx`
- Frontend verification passed:
	- `pnpm -C products/yappc/frontend/web type-check`
	- `pnpm -C products/yappc/frontend/web test:unit -- src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx`
- Adapter-seam delta completed for runtime module:
	- removed legacy Data-Cloud SPI constructor path from `EventPublisher.java`
	- removed direct `:products:data-cloud:planes:shared-spi` dependency from `core/agents/runtime/build.gradle.kts`
	- removed unused `YappcAepIntegration` facade and isolated test-only coverage to reduce dead direct AEP registry import surface
	- updated `MODULE_CATALOG.md` pending seam table accordingly

Validation notes for the runtime seam delta:

- Main/runtime compilation is green (`:products:yappc:core:agents:runtime:compileJava`, `:products:yappc:core:agents:runtime:classes`).
- Runtime governed integration drift fixed (`memoryStore` and release repository stubs), and full runtime tests now pass:
	- `./gradlew :products:yappc:core:agents:runtime:test` (253 tests, 0 failures)

Additional FE-01 progress completed:

- `usePhaseActionHandlers.ts` now routes auxiliary review/post-run actions through generic `PhaseAction` metadata (`serverOperation` / `actionId`) instead of dedicated phase-specific handlers.
- `PhaseCockpitContainer.tsx` now uses generic action execution for grouped sections, including compatibility fallback actions.
- Updated and validated focused frontend tests:
	- `pnpm vitest src/routes/app/project/__tests__/usePhaseActionHandlers.test.tsx src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx`
	- `pnpm type-check`

Additional backend/frontend hardening completed:

- BE-02 fail-closed tenant tier parsing enforced in `PhasePacketServiceImpl`:
	- missing/invalid/unknown tier values now resolve to `FREE` (no permissive fallback to `PRO`)
	- added regression test: unknown tier string fails closed
	- validation: `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.phase.PhasePacketServiceImplTest`
- BE-05 alignment continued in cockpit rendering:
	- removed compatibility fallback section synthesis in `PhaseCockpitContainer.tsx`
	- route test packet fixtures now provide backend-style `generate.*` and `run.*` actions directly
	- validation re-run: focused cockpit route + handler tests, plus frontend type-check
- FE-02 hardening completed in mapper behavior:
	- `phasePacketMappers.ts` now uses strict metadata parsing for suggestion payloads without synthetic fallback fields
	- added regression test proving suggestions are omitted when backend recommendation metadata is missing
	- validation re-run: `phasePacketMappers`, cockpit routes, action handlers, and frontend type-check

## Task Ledger (2026-05-28)

Legend:

- COMPLETE-VERIFIED: implementation and focused verification completed in this workspace state.
- PARTIAL-VERIFIED: significant implementation exists and validates in focused checks, but one or more acceptance details remain open.
- OPEN: not yet verified complete.

| Task | Status | Verification / Evidence |
| --- | --- | --- |
| BE-01 | COMPLETE-VERIFIED | `PhasePacketServiceImplTest` includes invalid-phase fail-closed case; focused Gradle run passed. |
| BE-02 | COMPLETE-VERIFIED | `PhasePacketServiceImplTest` tenant-tier fail-closed tests passed. |
| BE-03 | COMPLETE-VERIFIED | `PhaseActionAuthorizationServiceTest` and `PhasePacketServiceImplTest` dependency-degraded feature flag cases passed. |
| BE-04 | COMPLETE-VERIFIED | `PhasePacket.PhaseAction` includes target metadata fields; frontend action-handler tests passed with generic metadata flow. |
| BE-05 | COMPLETE-VERIFIED | Backend action categories/operations validated by phase action tests and cockpit route tests using backend-style actions. |
| BE-06 | COMPLETE-VERIFIED | `PhaseProjectStateService`, `PhaseEvidenceService`, `PhaseGovernanceService`, `PhaseActivityFeedService` present and used; service tests pass. |
| BE-07 | COMPLETE-VERIFIED | `PhaseBlockerMapper` extracted and exercised in phase packet test suite. |
| BE-08 | COMPLETE-VERIFIED | `ProductUnitIntentExporterTest` validates explicit `sourceProvider` and reject-unknown behavior. |
| FE-01 | COMPLETE-VERIFIED | `usePhaseActionHandlers.test.tsx` and route tests pass with generic action metadata execution. |
| FE-02 | COMPLETE-VERIFIED | `phasePacketMappers.test.ts` passes with strict backend metadata behavior. |
| FE-03 | COMPLETE-VERIFIED | `PhaseCurrentStateCard` + cockpit packet tests pass; duplicate summary handling consolidated. |
| FE-04 | COMPLETE-VERIFIED | `PhaseCockpitView.tsx` extracted; container/view split is present and tested. |
| FE-05 | COMPLETE-VERIFIED | Phase cockpit path no longer depends on local `components/ui/Button`; phase-focused tests pass. |
| FE-06 | COMPLETE-VERIFIED | Generic `PhaseActionSection` present and validated by cockpit route/action tests. |
| FE-07 | COMPLETE-VERIFIED | Degraded recovery-first panel flow present; technical details collapsed behind details block in cockpit view. |
| KRN-01 | COMPLETE-VERIFIED | `ProductUnitIntentDocument` is now owned by `platform:contracts` (`com.ghatana.contracts.kernel`) and consumed by `ProductUnitIntentExporter`; scaffold API contract tests pass. |
| KRN-02 | COMPLETE-VERIFIED | Registry and exporter source-provider validation tests pass (`ProductUnitKernelContractRegistryTest`, `ProductUnitIntentExporterTest`). |
| KRN-03 | COMPLETE-VERIFIED | Exporter validates inferred kind and implementation status against registry; tests pass. |
| KRN-04 | COMPLETE-VERIFIED | Production truth-source guard tests pass (`YappcEnvironmentConfigTest`, `KernelLifecycleEventIngestServiceTest`). |
| ARCH-01 | COMPLETE-VERIFIED | `:products:yappc:infrastructure:aep` exists, is wired in settings/dependencies, and tests pass. |
| ARCH-02 | COMPLETE-VERIFIED | Capability modules in scope are seam-clean, and module catalog is reconciled to adapter-owned Data Cloud/AEP boundaries with no open adapter-seam TODO state. |
| ARCH-03 | COMPLETE-VERIFIED | Deprecated `core:spi` wrapper references are absent in current codebase search. |
| UI-01 | COMPLETE-VERIFIED | `YappcPageShell` applied across phase cockpit/kernel-health/product-family/admin routes (search-verified). |
| UI-02 | COMPLETE-VERIFIED | Typed backend-owned `phasePanels` rendering is canonicalized in `PhaseStatusPanelsCanonical` and tested. |
| UI-03 | COMPLETE-VERIFIED | Canonical phase cockpit routes/components use backend-driven typed panels and shared shell/design primitives; component inventory checks remain clean. |
| UI-04 | COMPLETE-VERIFIED | Compatibility/package consolidation guardrails are enforced via inventory checks and module/package docs; no new compat imports in active cockpit/kernel/admin/product-family surfaces. |
| LE-01 | COMPLETE-VERIFIED | `LearningInsightService` extracted and wired via `PhasePacketAssembler`; focused phase assembler/service tests pass. |
| LE-02 | COMPLETE-VERIFIED | Learn route cockpit integration with backend-owned panel content is covered by route tests. |
| EV-01 | COMPLETE-VERIFIED | `EvolutionPlanService` extracted and wired via `PhasePacketAssembler`; focused phase assembler/service tests pass. |
| EV-02 | COMPLETE-VERIFIED | Guided Evolve phase UI is rendered through canonical typed panel data and covered by focused route/panel integration tests. |
| DOC-01 | COMPLETE-VERIFIED | `ARCHITECTURE.md` and backlog status docs are reconciled to implemented Learn/Evolve hardening state. |
| DOC-02 | COMPLETE-VERIFIED | `MODULE_CATALOG.md` adapter-seam section is reconciled to current resolved boundary ownership state. |

Operational note:

- Existing wave runner task command currently fails early with plan parser mismatch (`Expected 64 plan tasks, found 7`), so this ledger is the authoritative state for this implementation wave.

---

# 1. Backend Phase Packet, Action, Readiness, and Degraded-State Group

## Verification target

Run one backend-focused verification pass after this group:

```bash
./gradlew :products:yappc:core:yappc-services:test
./gradlew :products:yappc:integration:test
```

Also run any focused tests for:

* phase packet construction
* phase action authorization
* phase gate context
* degraded packet behavior
* platform run status
* tenant/feature-flag behavior

## Why group these together

These files all shape the canonical backend-owned `PhasePacket`. The frontend should only render this contract. Fixing these together prevents repeated frontend churn.

---

## Task BE-01 — Replace invalid phase fallback with explicit degraded/invalid phase blocker

| Field               | Details                                                                                                                                                                                                                |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P0                                                                                                                                                                                                                     |
| File                | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketServiceImpl.java`                                                                                                        |
| Current issue       | `queryPhaseBlockers` catches an invalid phase and defaults to `PhaseType.INTENT`. This can silently validate the wrong lifecycle phase.                                                                                |
| Change              | Replace fallback-to-INTENT with explicit invalid-phase degraded behavior. Return a blocker such as `INVALID_PHASE`, severity `CRITICAL`, resolvable `false`, and include phase/project/workspace/correlation metadata. |
| Why                 | Correctness: invalid input must fail closed, not run intent gates.                                                                                                                                                     |
| Acceptance criteria | Invalid phase never uses INTENT gates. Packet indicates degraded/blocked state with clear recovery message.                                                                                                            |
| Tests               | Add/adjust `PhasePacketServiceImplTest` or equivalent: invalid phase returns critical blocker and `readiness.canAdvance=false`.                                                                                        |

---

## Task BE-02 — Fail closed on tenant tier parse/lookup failure

| Field               | Details                                                                                                                                                                                      |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P0                                                                                                                                                                                           |
| File                | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketServiceImpl.java`                                                                              |
| Current issue       | `determineTenantTier` defaults to `PRO` on exception.                                                                                                                                        |
| Change              | Replace fallback with `FREE` or a degraded tier state that disables privileged actions. Prefer explicit `FREE` plus governance/degraded record unless the domain supports an `UNKNOWN` tier. |
| Why                 | Security/correctness: a tier error must not expand permissions.                                                                                                                              |
| Acceptance criteria | Tier parse failure does not enable PRO/ENTERPRISE actions.                                                                                                                                   |
| Tests               | Add tenant-tier failure test: malformed tier disables `phase.advance`, `phase.governance.configure`, and report export unless explicitly entitled elsewhere.                                 |

---

## Task BE-03 — Make feature flag dependency failure explicit, not silently empty

| Field               | Details                                                                                                                                                                                                                                                  |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P0                                                                                                                                                                                                                                                       |
| File                | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhaseFeatureFlagProvider.java`                                                                                                                                        |
| Current issue       | `queryEnabledTenantFeatureFlags` logs a Data Cloud error and returns an empty list.                                                                                                                                                                      |
| Change              | Return a typed degraded feature-flag result or annotate project state with `featureFlagsDegraded=true` and `featureFlagsDegradedReason`. Then `PhaseActionAuthorizationService` must disable entitlement-dependent actions with a clear disabled reason. |
| Why                 | Empty flags can look like normal “not entitled” state; production users need a clear dependency-degraded state.                                                                                                                                          |
| Acceptance criteria | Data Cloud flag query failure creates deterministic disabled actions with `phaseAction.disabled.featureFlagDependencyUnavailable` or equivalent.                                                                                                         |
| Tests               | Add feature-flag dependency failure test for PRO/ENTERPRISE user.                                                                                                                                                                                        |

---

## Task BE-04 — Add backend action target metadata so frontend does not special-case intent/validate

| Field               | Details                                                                                                                                                                                                                                                                                                      |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Priority            | P0                                                                                                                                                                                                                                                                                                           |
| Files               | `PhaseActionAuthorizationService.java`, `PhasePacket.java`, `PhasePacketServiceImpl.java`                                                                                                                                                                                                                    |
| Current issue       | Frontend special-cases intent by navigating to `?drawer=idea`, and validate by checking lifecycle preview locally.                                                                                                                                                                                           |
| Change              | Extend `PhasePacket.PhaseAction` with explicit action target metadata if not already enough: `targetType`, `targetRoute`, `targetDrawer`, `requiresPreview`, `serverOperation`, `postSuccessBehavior`. Populate for intent drawer, validate transition, generate review, run retry/rollback/promote/observe. |
| Why                 | Backend must own lifecycle/action truth. Frontend should only render/dispatch action metadata.                                                                                                                                                                                                               |
| Acceptance criteria | No phase-specific branching remains in frontend action handlers.                                                                                                                                                                                                                                             |
| Tests               | Backend action contract tests for each phase action. Frontend tests verify generic action dispatch.                                                                                                                                                                                                          |

---

## Task BE-05 — Convert generate review and run post-actions into backend-provided `PhaseAction`s

| Field               | Details                                                                                                                                                                                                                                                   |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P1                                                                                                                                                                                                                                                        |
| Files               | `PhaseActionAuthorizationService.java`, `PhasePacketServiceImpl.java`, `PhasePacket.java`                                                                                                                                                                 |
| Current issue       | Generate review actions and run retry/rollback/promote are rendered as separate frontend-only blocks.                                                                                                                                                     |
| Change              | Add action categories and operation IDs for: `generate.apply`, `generate.reject`, `generate.rollback`, `run.retry`, `run.rollback`, `run.promote`, `run.observe`. Add `reviewRequired`, `rollbackSupported`, `dangerLevel`, and `requiresActor` metadata. |
| Why                 | One canonical action model reduces UI complexity and prevents action drift.                                                                                                                                                                               |
| Acceptance criteria | Generate/run secondary action panels can be deleted or replaced by one generic action section.                                                                                                                                                            |
| Tests               | Action authorization tests for generate and run actions under allowed, denied, degraded, and missing-run contexts.                                                                                                                                        |

---

## Task BE-06 — Extract project state, evidence, governance, and activity queries from `PhasePacketServiceImpl`

| Field               | Details                                                                                                                                                                                                                             |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P1                                                                                                                                                                                                                                  |
| File                | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketServiceImpl.java`                                                                                                                     |
| Add files           | `PhaseProjectStateService.java`, `PhaseEvidenceService.java`, `PhaseGovernanceService.java`, `PhaseActivityFeedService.java` under same package                                                                                     |
| Current issue       | `PhasePacketServiceImpl` is still a large orchestration class even after collaborator extraction. It owns project lookup, evidence query, governance query, activity mapping, blocker conversion, packet assembly orchestration.    |
| Change              | Keep `PhasePacketServiceImpl` as a thin orchestrator. Move query/mapping responsibilities into focused services.                                                                                                                    |
| Why                 | SRP, testability, and fewer regressions.                                                                                                                                                                                            |
| Acceptance criteria | `PhasePacketServiceImpl` reads as orchestration only; each collaborator has dedicated tests.                                                                                                                                        |
| Tests               | Move existing tests to collaborator-level tests; keep one high-level assembly test.                                                                                                                                                 |

---

## Task BE-07 — Normalize phase blocker conversion into a dedicated mapper

| Field               | Details                                                                                                                                                            |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Priority            | P1                                                                                                                                                                 |
| File                | `PhasePacketServiceImpl.java`                                                                                                                                      |
| Add file            | `PhaseBlockerMapper.java`                                                                                                                                          |
| Current issue       | Blocker severity/title/type mapping is inline in `queryPhaseBlockers`.                                                                                             |
| Change              | Create `PhaseBlockerMapper` that maps validator blockers to packet blockers. Use config or structured blocker type where possible instead of string-prefix checks. |
| Why                 | Avoid brittle string logic.                                                                                                                                        |
| Acceptance criteria | No string-prefix mapping remains in service.                                                                                                                       |
| Tests               | Mapper tests for missing artifact, prior exit criterion, entry criterion, policy denied, dependency degraded.                                                      |

---

## Task BE-08 — Make source provider explicit and contract-validated in ProductUnitIntent request flow

| Field               | Details                                                                                                                                                       |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P1                                                                                                                                                            |
| File                | `ProductUnitIntentExporter.java`                                                                                                                              |
| Current issue       | `TargetProvidersDocument` hardcodes source provider as `ghatana-file-registry`.                                                                               |
| Change              | Add `sourceProvider` to `ProductUnitIntentExporter.Request`, validate it via Kernel contract registry, and require explicit source provider in CLI/API flows. |
| Why                 | Kernel handoff should support multiple source providers and avoid hidden defaults.                                                                            |
| Acceptance criteria | Unknown source provider fails validation.                                                                                                                     |
| Tests               | Exporter tests for valid/invalid source provider.                                                                                                             |

---

# 2. Frontend Phase Cockpit, 0 Cognitive Load, and Action UI Group

## Verification target

Run one frontend-focused verification pass after this group:

```bash
pnpm -C products/yappc/frontend/web type-check
pnpm -C products/yappc/frontend/web test:unit
pnpm -C products/yappc/frontend/web test:integration
pnpm -C products/yappc/frontend/web test:contract
pnpm -C products/yappc/frontend/web inventory:components:check
```

## Why group these together

These files make up the same user-facing phase cockpit. Change them together so the UI stabilizes in one pass.

---

## Task FE-01 — Remove phase-specific logic from `usePhaseActionHandlers`

| Field               | Details                                                                                                                                                                    |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P0                                                                                                                                                                         |
| File                | `products/yappc/frontend/web/src/routes/app/project/usePhaseActionHandlers.ts`                                                                                             |
| Depends on          | BE-04, BE-05                                                                                                                                                               |
| Current issue       | The hook hardcodes intent navigation and validate lifecycle preview checks.                                                                                                |
| Change              | Replace special cases with a generic executor that reads `PhaseAction` metadata: route target, drawer target, server operation, review operation, post-success navigation. |
| Why                 | Frontend must not own lifecycle/action truth.                                                                                                                              |
| Acceptance criteria | `handlePrimaryAction` and related handlers contain no `if (phase === ...)` logic except purely presentational behavior.                                                    |
| Tests               | Update `usePhaseActionHandlers.test.tsx`: generic route action, drawer action, server action, disabled action, degraded action.                                            |

---

## Task FE-02 — Replace hardcoded suggestion metadata in `phasePacketMappers`

| Field               | Details                                                                                                                                     |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P1                                                                                                                                          |
| File                | `products/yappc/frontend/web/src/routes/app/project/phasePacketMappers.ts`                                                                  |
| Current issue       | `mapPacketSuggestions` hardcodes `confidence: 0.5`, `evidence: []`, `riskLevel`, `applyMode`, `approvalRequired`, and `rollbackSupported`.  |
| Change              | Consume backend-provided recommendation/action metadata. Do not synthesize confidence/evidence/risk in frontend.                            |
| Why                 | User trust and correctness. Fake confidence/evidence is misleading.                                                                         |
| Acceptance criteria | No hardcoded confidence, empty evidence, or rollback flag in mapper.                                                                        |
| Tests               | Update `phasePacketMappers.test.ts` to assert backend metadata is preserved.                                                                |

---

## Task FE-03 — Merge `PhasePacketSummary` and phase contract summary into one current-state card

| Field               | Details                                                                                                                                                                             |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P1                                                                                                                                                                                  |
| File                | `products/yappc/frontend/web/src/routes/app/project/PhaseCockpitContainer.tsx`                                                                                                      |
| Related files       | `PhasePacketSummary.tsx`, possibly add `PhaseCurrentStateCard.tsx`                                                                                                                  |
| Current issue       | The container renders `PhasePacketSummary`, `PhaseDegradedPacketPanel`, then a separate `phase-contract-summary` card.                                                              |
| Change              | Create one `PhaseCurrentStateCard` showing: project, readiness, evidence count, governance outcome, primary next action, degraded marker if any. Remove duplicate summary sections. |
| Why                 | 0 cognitive load. The user should see one state summary, not multiple overlapping cards.                                                                                            |
| Acceptance criteria | One top state card per phase. Evidence/governance details remain available in lower panels or collapsible sections.                                                                 |
| Tests               | Update phase cockpit component packet tests and snapshots/assertions.                                                                                                               |

---

## Task FE-04 — Extract `PhaseCockpitView` from `PhaseCockpitContainer`

| Field               | Details                                                                                                     |
| ------------------- | ----------------------------------------------------------------------------------------------------------- |
| Priority            | P1                                                                                                          |
| File                | `PhaseCockpitContainer.tsx`                                                                                 |
| Add file            | `PhaseCockpitView.tsx`                                                                                      |
| Current issue       | Container still fetches data, handles guards, builds actions, maps data, and assembles large JSX.           |
| Change              | Keep container responsible for data, guards, and handlers. Move presentational JSX into `PhaseCockpitView`. |
| Why                 | SRP and easier UI verification.                                                                             |
| Acceptance criteria | Container <200 lines if practical; view receives typed props and is mostly pure.                            |
| Tests               | Add `PhaseCockpitView.test.tsx`; simplify container tests to state/guard behavior.                          |

---

## Task FE-05 — Replace local `Button` usage with canonical design-system/YAPPC UI button

| Field               | Details                                                                                                                                                                                                  |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P1                                                                                                                                                                                                       |
| Files               | `PhaseCockpitContainer.tsx`, `PhasePacketErrorPanel.tsx`, related phase components                                                                                                                       |
| Current issue       | Phase cockpit imports local `components/ui/Button`.                                                                                                                                                      |
| Change              | Replace with canonical component from `@ghatana/design-system` or `yappc-ui`, whichever is the approved project standard. Update styling via tokens/variants, not manual border/background/text classes. |
| Why                 | Beautiful and consistent modern UI; no duplicate primitives.                                                                                                                                             |
| Acceptance criteria | No phase cockpit imports from local `components/ui/Button`; visual states remain equivalent or improved.                                                                                                 |
| Tests               | `inventory:components:check`; component tests for disabled/loading/focus states.                                                                                                                         |

---

## Task FE-06 — Create one generic backend-driven `PhaseActionSection`

| Field               | Details                                                                                                      |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Priority            | P1                                                                                                           |
| Files               | `PhaseCockpitContainer.tsx`, add `PhaseActionSection.tsx`                                                    |
| Depends on          | BE-05                                                                                                        |
| Current issue       | Generate review and run post-actions are separate hardcoded UI sections.                                     |
| Change              | Render all secondary/review/danger/post actions from `packet.availableActions` grouped by category/priority. |
| Why                 | One consistent mental model for actions.                                                                     |
| Acceptance criteria | No explicit generate/run action blocks in cockpit container.                                                 |
| Tests               | Component tests for action categories: primary, review, danger, post-run, disabled.                          |

---

## Task FE-07 — Collapse degraded dependency UX into one recovery-focused panel

| Field               | Details                                                                                                                                                                                            |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P2                                                                                                                                                                                                 |
| Files               | `PhaseDegradedPacketPanel.tsx`, `PhaseCockpitContainer.tsx`, `PhasePacketErrorPanel.tsx`                                                                                                           |
| Current issue       | Degraded info can appear as degraded panel, blocker, evidence, governance, completed artifact, activity feed, and disabled reason. Backend returns degraded records for multiple dependencies.     |
| Change              | Top-level `PhaseDegradedPacketPanel` should summarize dependency, impact, safe state, and recovery action. Detail panels should show collapsed technical detail only on demand.                    |
| Why                 | Failure states must be simple and actionable.                                                                                                                                                      |
| Acceptance criteria | One visible degraded recovery card; technical records are collapsed.                                                                                                                               |
| Tests               | Degraded packet render test.                                                                                                                                                                       |

---

# 3. Kernel ProductUnitIntent and Contract Hardening Group

## Verification target

Run one Kernel-contract-focused pass:

```bash
./gradlew :products:yappc:core:scaffold:api:test
./gradlew :products:yappc:kernel-bridge:test
```

Also run any route/contract parity tasks if they exist.

## Why group these together

These changes affect Kernel handoff and contract validity. Do them together to avoid repeated contract updates.

---

## Task KRN-01 — Replace local ProductUnitIntent DTO records with generated/imported Kernel Java contracts

| Field               | Details                                                                                                                                               |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P1                                                                                                                                                    |
| File                | `products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/kernel/ProductUnitIntentExporter.java`                                              |
| Current issue       | Exporter defines local records (`ProductUnitIntentDocument`, `ProductUnitScopeDocument`, `ProducerDocument`, etc.) to mirror Kernel shape.            |
| Change              | Use generated/imported Java DTOs from Kernel public contracts. If Java DTO does not exist, create a generated contract module and wire it into YAPPC. |
| Why                 | Prevent contract drift and duplicate schema ownership.                                                                                                |
| Acceptance criteria | ProductUnitIntent export compiles against Kernel-owned DTOs.                                                                                          |
| Tests               | Existing `ProductUnitIntentExporterTest`; add contract round-trip test against Kernel schema resource.                                                |

---

## Task KRN-02 — Add source provider to Kernel contract registry

| Field               | Details                                                                                                                                                                               |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P1                                                                                                                                                                                    |
| Files               | `ProductUnitKernelContractRegistry.java`, `kernel-product-unit-contract.json`, `ProductUnitIntentExporter.java`                                                                       |
| Current issue       | Registry validates providers, lifecycle profiles, surfaces, product unit kinds, and implementation statuses, but source provider validation is not explicit in fetched registry API.  |
| Change              | Extend contract resource and registry with `sourceProviders`. Use it in exporter validation.                                                                                          |
| Why                 | Hardcoded source provider is still duplicate truth.                                                                                                                                   |
| Acceptance criteria | Source provider comes from Kernel contract resource.                                                                                                                                  |
| Tests               | Registry tests for valid/invalid source providers.                                                                                                                                    |

---

## Task KRN-03 — Validate ProductUnit kind and implementation status in exporter

| Field               | Details                                                                                                                      |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P1                                                                                                                           |
| Files               | `ProductUnitIntentExporter.java`, `ProductUnitKernelContractRegistry.java`                                                   |
| Current issue       | Registry exposes product unit kinds and implementation statuses, but exporter primarily validates surface/provider/profile.  |
| Change              | Validate inferred `kind` and surface `implementationStatus="planned"` against registry.                                      |
| Why                 | Full contract correctness.                                                                                                   |
| Acceptance criteria | Unknown kind/status fails export.                                                                                            |
| Tests               | Exporter tests for invalid kind/status.                                                                                      |

---

## Task KRN-04 — Add production wiring assertion for Kernel lifecycle truth provider

| Field               | Details                                                                                                                                       |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P1                                                                                                                                            |
| Files               | `KernelLifecycleEventIngestService.java`, service module/bootstrap that constructs it                                                         |
| Current issue       | Local filesystem provider is guarded against production, but production wiring still needs explicit verification.                             |
| Change              | Add startup assertion: production runtime must inject `DataCloudKernelLifecycleTruthSource` or other production `KernelLifecycleTruthSource`. |
| Why                 | Prevent accidental local filesystem truth in production.                                                                                      |
| Acceptance criteria | Production profile fails fast if local provider is configured.                                                                                |
| Tests               | `YappcEnvironmentConfigTest` / ingest service production profile test.                                                                        |

---

# 4. Adapter-Seam and Reuse-First Backend Boundary Group

## Verification target

Run architecture/boundary verification after this group:

```bash
./gradlew :products:yappc:build
./gradlew :products:yappc:tools:validation-tests:test
```

Also run import/boundary checks if available.

## Why group these together

Adapter seams touch build files and multiple modules. They should be moved in one pass to avoid broken dependency graphs.

---

## Task ARCH-01 — Create or complete `infrastructure:aep` adapter module

| Field               | Details                                                                                                                  |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| Priority            | P1                                                                                                                       |
| Files               | `products/yappc/settings.gradle.kts`, `products/yappc/build.gradle.kts`, new `products/yappc/infrastructure/aep/**`      |
| Current issue       | Module catalog says an `infrastructure:aep` adapter module should own AEP imports; direct AEP deps are still scattered.  |
| Change              | Add adapter module for AEP ports, move direct AEP imports from capability modules into adapter implementation.           |
| Why                 | SRP and boundary correctness.                                                                                            |
| Acceptance criteria | Capability modules depend on YAPPC ports, not AEP internals.                                                             |
| Tests               | Adapter tests and architecture import tests.                                                                             |

---

## Task ARCH-02 — Remove direct Data Cloud imports from capability modules marked with `TODO(ADAPTER-SEAM)`

| Field               | Details                                                                                                   |
| ------------------- | --------------------------------------------------------------------------------------------------------- |
| Priority            | P1                                                                                                        |
| Files               | `core/agents/workflow`, `core/knowledge-graph`, related build files                                       |
| Current issue       | Module catalog marks direct data-cloud deps in workflow and knowledge graph modules.                      |
| Change              | Define YAPPC port interfaces in contract/domain modules and implement them in `infrastructure/datacloud`. |
| Why                 | Capability modules should not bind directly to storage product internals.                                 |
| Acceptance criteria | No direct `products:data-cloud:*` dependency in capability modules except allowed SPI seam.               |
| Tests               | Build + architecture dependency tests.                                                                    |

---

## Task ARCH-03 — Remove or retire `core:spi` compatibility wrapper

| Field               | Details                                                                                                            |
| ------------------- | ------------------------------------------------------------------------------------------------------------------ |
| Priority            | P2                                                                                                                 |
| Files               | `products/yappc/core/spi/**`, settings/build files                                                                 |
| Current issue       | Module catalog lists `core:spi` as a deprecated compatibility wrapper.                                             |
| Change              | Find imports, migrate to `core:yappc-shared` or correct canonical module, then delete wrapper if no longer needed. |
| Why                 | Remove duplicate/compat surface.                                                                                   |
| Acceptance criteria | No active module imports deprecated `core:spi`.                                                                    |
| Tests               | Build and dependency checks.                                                                                       |

---

# 5. Frontend Reuse, UI Consistency, and Package Consolidation Group

## Verification target

Run:

```bash
pnpm -C products/yappc/frontend/web inventory:components:check
pnpm -C products/yappc/frontend/web test:regression
pnpm -C products/yappc/frontend/web test:a11y
```

## Why group these together

These tasks affect visual consistency across the YAPPC app. Verify once through component inventory, regression, and a11y.

---

## Task UI-01 — Standardize top-level page shells

| Field               | Details                                                                                                                                                                                  |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P2                                                                                                                                                                                       |
| Files               | `routes/app/kernel-health.tsx`, `routes/app/kernel-health-product.tsx`, `routes/app/product-family.tsx`, `routes/app/admin/*.tsx`, `routes/app/project/PhaseCockpitContainer.tsx`        |
| Current issue       | README hardening focus explicitly calls for shell consistency across phase cockpit, kernel health, product-family, and admin surfaces.  Architecture repeats this as a hardening focus.  |
| Change              | Introduce or reuse a canonical `YappcPageShell` / `YappcPageHeader` / `YappcCardGrid` pattern. Apply to all top-level YAPPC pages.                                                       |
| Why                 | Beautiful, consistent, modern UI.                                                                                                                                                        |
| Acceptance criteria | Top-level pages share same title/subtitle/actions/loading/error/empty layout.                                                                                                            |
| Tests               | Visual regression and component inventory.                                                                                                                                               |

---

## Task UI-02 — Consolidate phase status panel rendering around backend-owned typed panels

| Field               | Details                                                                                                                                    |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| Priority            | P1                                                                                                                                         |
| Files               | `PhaseStatusPanelsCanonical.tsx`, `PhaseEmbeddedSurface.tsx`, phase panel components, `PhasePacket` backend                                |
| Current issue       | Current focus says backend-owned typed phase panel migration is still a hardening item.                                                    |
| Change              | Ensure every panel rendered in phase cockpit comes from typed `packet.phasePanels`; delete or quarantine heuristic local status rendering. |
| Why                 | Backend-owned rendering model and no duplicate UI logic.                                                                                   |
| Acceptance criteria | No phase status is inferred from frontend-only rules.                                                                                      |
| Tests               | `PhaseStatusPanelsCanonical.test.tsx`; contract tests for all phase panel types.                                                           |

---

## Task UI-03 — Migrate local phase components to canonical YAPPC/shared UI primitives

| Field               | Details                                                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| Priority            | P2                                                                                                                                   |
| Files               | `components/phase/*`, `components/common/*`, `components/ui/*`, `frontend/libs/yappc-ui/*`                                           |
| Current issue       | Frontend declares canonical shared packages, but local app components still exist and are used.                                      |
| Change              | Move reusable phase/common components into `frontend/libs/yappc-ui` or shared design-system wrapper. Keep app route components thin. |
| Why                 | Reuse-first and no duplicates.                                                                                                       |
| Acceptance criteria | Route-level code imports UI primitives from canonical package, not local duplicate primitives.                                       |
| Tests               | `inventory:components:check`, unit tests migrated with components.                                                                   |

---

## Task UI-04 — Clean up frontend compatibility packages and product-specific libs

| Field               | Details                                                                                                                                                                                 |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P2                                                                                                                                                                                      |
| Files               | `frontend/compat/*`, `frontend/libs/*`, package manifests                                                                                                                               |
| Current issue       | Module catalog lists many compat packages and active product-specific libraries that should eventually consolidate.                                                                     |
| Change              | Inventory imports. For each compat package, either delete if unused or redirect through canonical package. Consolidate product-specific libraries into canonical packages where reused. |
| Why                 | Package sprawl creates duplicate abstractions and inconsistent UI/state.                                                                                                                |
| Acceptance criteria | No new imports from `frontend/compat/*`; active product libs have ownership docs and migration target.                                                                                  |
| Tests               | Package import check.                                                                                                                                                                   |

---

# 6. Learn and Evolve Product Feature Completion Group

## Verification target

Run focused E2E/user journey checks after this group:

```bash
pnpm -C products/yappc/frontend/web test:integration
pnpm -C products/yappc/frontend/web test:e2e
```

Backend tests depend on touched service files.

## Why group these together

Learn and Evolve should be completed as product journeys, not scattered backend/UI cards.

---

## Task LE-01 — Define a backend-owned `LearningInsightPanel` packet model

| Field               | Details                                                                                                                      |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P2                                                                                                                           |
| Files               | `PhasePacket.java`, `PhasePacketServiceImpl.java`, new `LearningInsightService.java`                                         |
| Current issue       | Learn route exists and docs claim learning support, but the user-facing Learn phase needs a clear learning-to-action model.  |
| Change              | Add typed panel(s) for: learned signal, source event, confidence, recommendation, required approval, rollback path.          |
| Why                 | Learn should be actionable, governed, and understandable.                                                                    |
| Acceptance criteria | Learn phase shows “what was learned, why it matters, what action is safe.”                                                   |
| Tests               | Learn phase packet test and UI render test.                                                                                  |

---

## Task LE-02 — Implement guided Learn phase UI

| Field               | Details                                                                                          |
| ------------------- | ------------------------------------------------------------------------------------------------ |
| Priority            | P2                                                                                               |
| Files               | `routes/app/project/learn.tsx`, `PhaseStatusPanelsCanonical.tsx`, Learn-specific panel component |
| Change              | Render learning insights using backend packet panels. Avoid generic status cards only.           |
| Why                 | Feature completeness for Learn.                                                                  |
| Acceptance criteria | Learn route has clear empty/loading/insight/approval/degraded states.                            |
| Tests               | Learn route integration test.                                                                    |

---

## Task EV-01 — Define an `EvolutionPlanPanel` backend packet model

| Field               | Details                                                                                                                          |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| Priority            | P2                                                                                                                               |
| Files               | `PhasePacket.java`, `PhasePacketServiceImpl.java`, new `EvolutionPlanService.java`                                               |
| Current issue       | Evolve route exists, but impact-analysis/diff-review E2E coverage remains a hardening focus.                                     |
| Change              | Add typed panel model for proposal, impact, diff summary, validation requirements, approval state, rollback path, re-run target. |
| Why                 | Evolve needs to be a guided change workflow.                                                                                     |
| Acceptance criteria | Evolve packet includes enough data to guide proposal → impact → diff → approval → revalidate.                                    |
| Tests               | Evolve packet test.                                                                                                              |

---

## Task EV-02 — Implement guided Evolve phase UI

| Field               | Details                                                                                            |
| ------------------- | -------------------------------------------------------------------------------------------------- |
| Priority            | P2                                                                                                 |
| Files               | `routes/app/project/evolve.tsx`, `PhaseStatusPanelsCanonical.tsx`, Evolve-specific panel component |
| Change              | Render the evolution workflow as a clear stepper/checklist with one next action.                   |
| Why                 | 0 cognitive load and feature completeness.                                                         |
| Acceptance criteria | User sees exactly where they are in the evolution loop and what to do next.                        |
| Tests               | Evolve route integration test.                                                                     |

---

# 7. Documentation / Backlog Alignment Group

## Verification target

Run docs checks only after code groups stabilize. Since you said not to focus on evidence generation now, this group is lower priority.

```bash
# later iteration
pnpm check:doc-claims-evidence
```

## Why group these together

Docs should reflect implementation after code is stabilized, not drive another implementation loop now.

---

## Task DOC-01 — Update architecture hardening status after code changes

| Field               | Details                                                                                                               |
| ------------------- | --------------------------------------------------------------------------------------------------------------------- |
| Priority            | P3                                                                                                                    |
| Files               | `products/yappc/docs/ARCHITECTURE.md`, `products/yappc/README.md`, `products/yappc/docs/YAPPC_BACKLOG_PROGRESS.md`    |
| Current issue       | Docs say remaining work includes cockpit decomposition, typed panel migration, shell consistency, and docs cleanup.   |
| Change              | After code changes, update these statements to reflect actual remaining hardening gaps.                               |
| Why                 | Prevent future audit confusion.                                                                                       |
| Acceptance criteria | Docs match code reality.                                                                                              |

---

## Task DOC-02 — Update module catalog after adapter-seam cleanup

| Field               | Details                                                                                         |
| ------------------- | ----------------------------------------------------------------------------------------------- |
| Priority            | P3                                                                                              |
| File                | `products/yappc/docs/MODULE_CATALOG.md`                                                         |
| Current issue       | Catalog still lists `TODO(ADAPTER-SEAM)` and product-specific package consolidation notes.      |
| Change              | Update module roles after adapters are introduced and compat packages are removed/consolidated. |
| Why                 | Keeps dependency rules authoritative.                                                           |
| Acceptance criteria | No stale TODOs remain for completed adapter moves.                                              |

---

# Minimal Verification Rounds

## Round 1 — Backend phase/action correctness

Tasks:

* BE-01
* BE-02
* BE-03
* BE-04
* BE-05
* BE-06
* BE-07

Verify:

```bash
./gradlew :products:yappc:core:yappc-services:test
./gradlew :products:yappc:integration:test
```

## Round 2 — Frontend cockpit and 0 cognitive load

Tasks:

* FE-01
* FE-02
* FE-03
* FE-04
* FE-05
* FE-06
* FE-07

Verify:

```bash
pnpm -C products/yappc/frontend/web type-check
pnpm -C products/yappc/frontend/web test:unit
pnpm -C products/yappc/frontend/web test:integration
pnpm -C products/yappc/frontend/web test:contract
pnpm -C products/yappc/frontend/web inventory:components:check
```

## Round 3 — Kernel contract and ProductUnitIntent

Tasks:

* KRN-01
* KRN-02
* KRN-03
* KRN-04
* BE-08

Verify:

```bash
./gradlew :products:yappc:core:scaffold:api:test
./gradlew :products:yappc:kernel-bridge:test
```

## Round 4 — Adapter seams and reuse-first backend boundary

Tasks:

* ARCH-01
* ARCH-02
* ARCH-03

Verify:

```bash
./gradlew :products:yappc:build
./gradlew :products:yappc:tools:validation-tests:test
```

## Round 5 — Shared UI / shell consistency / package consolidation

Tasks:

* UI-01
* UI-02
* UI-03
* UI-04

Verify:

```bash
pnpm -C products/yappc/frontend/web inventory:components:check
pnpm -C products/yappc/frontend/web test:regression
pnpm -C products/yappc/frontend/web test:a11y
```

## Round 6 — Learn/Evolve feature completion

Tasks:

* LE-01
* LE-02
* EV-01
* EV-02

Verify:

```bash
pnpm -C products/yappc/frontend/web test:integration
pnpm -C products/yappc/frontend/web test:e2e
./gradlew :products:yappc:core:yappc-services:test
```

## Round 7 — Documentation sync

Tasks:

* DOC-01
* DOC-02

Verify later when evidence work resumes.

---

# Highest-Value Execution Order

Start with these 10 tasks first:

1. **BE-04** — backend action target metadata.
2. **BE-05** — generate/run actions as backend `PhaseAction`s.
3. **FE-01** — remove frontend phase-specific branching.
4. **FE-06** — generic `PhaseActionSection`.
5. **FE-03** — merge phase summaries into one state card.
6. **BE-01** — invalid phase fail-closed.
7. **BE-02** — tenant tier fail-closed.
8. **FE-02** — remove fake suggestion metadata.
9. **KRN-01** — Kernel DTO import/generation.
10. **ARCH-01** — AEP adapter seam.

That order gives the fastest path to **correctness + 0 cognitive load + proper abstraction** without spreading changes across unrelated areas.
