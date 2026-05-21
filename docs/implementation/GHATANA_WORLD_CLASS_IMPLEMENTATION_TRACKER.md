# Iterative Production-Grade Feature Completeness Audit Report

## Ghatana Studio, Canvas, UI Builder, DS Generator, and Artifact Compiler/Decompiler

**Repo:** `samujjwal/ghatana`
**Target commit:** `63dbb50e070c2efc7866acabc40202bfd4e604d8`
**Commit verified:** `fd fdfff fffff` 

I inspected the current snapshot through the GitHub connector. I did **not** run the repo locally with `pnpm`; conclusions below are based on current source, scripts, tests, and package wiring visible at the target commit.

---

## A. Executive Summary

### Overall readiness rating

**Partial, materially improved from the previous audited state.**

This commit adds the exact class of hardening gates that were previously missing: artifact round-trip, canvas history, canonical BuilderDocument, builder/canvas adapter, and DS generator golden checks are now registered in root scripts, and they are included in the broader kernel product boundary and phase-8/world-class readiness gates. 

### Final verdict

**Partial.**

The system is now closer to a production-grade foundation, but it is still not world-class feature-complete. The biggest improvement is that previously aspirational checks are now represented as concrete scripts and Vitest test targets. The remaining blockers are deeper: duplicated builder/canvas adapters, still-shallow artifact round-trip tests, browser-only source acquisition, localStorage-backed Builder persistence, and incomplete true preview/runtime validation.

### Top improvements validated

1. **Root gates now exist.**
   `check:artifact-roundtrip`, `check:canvas-history`, `check:builder-canonical-document`, `check:builder-canvas-adapter`, and `check:ds-generator-golden` are root scripts and are wired into `check:kernel-product-boundary-audit` and `check:phase8`. 

2. **DS Generator now has deterministic generation context and canonical component states.**
   `CANONICAL_COMPONENT_STATES` now includes `default`, `hover`, `active`, `focus`, `focus-visible`, `disabled`, `loading`, `selected`, `error`, `success`, and `warning`; the factory now accepts a `GenerationContext` with injected `clockFn` and `idFn`. 

3. **Artifact compiler has round-trip and protected-region tests.**
   The new round-trip test covers source → model → source → model at a basic level, and the protected-region test verifies `@ghatana-region` markers and ownership annotations.  

4. **React compiler now emits protected region markers.**
   `compileReact` now wraps generated import and user-authored body sections with `@ghatana-region` begin/end comments and ownership markers. 

5. **Studio now has a shared artifact workflow store.**
   `artifactWorkflowStore.ts` centralizes job result, logical model, projected BuilderDocument, preview source, fidelity report, and last decompile timestamp. 

6. **Import → workflow → Canvas/Builder/Fidelity is partially wired.**
   `ImportDecompilePage` now decompiles files, detects residual islands, projects to BuilderDocument, compiles preview source, stores workflow state, and exposes buttons to open Canvas, Builder, and Fidelity Report. 

### Top production blockers still present

1. **Builder/canvas adapter ownership is duplicated.**
   There are now at least two adapters: `BuilderCanvasAdapter.ts` and `BuilderCanvasProjectionAdapter.ts`. They overlap in responsibility: both project BuilderDocument to canvas and both handle builder/canvas mapping.  

2. **CanvasPage still uses an unsafe cast.**
   `CanvasPage` narrows `CanvasNode[]` to `BuilderCanvasNode[]` with `as unknown as BuilderCanvasNode[]`, which violates the “no unsafe casts across package boundaries” goal even though `VisualCanvas` was fixed. 

3. **The artifact round-trip tests are still shallow.**
   The test proves non-empty output and a stable node count, but it does not prove source intent preservation, import preservation, JSX fidelity, formatting preservation, protected-region rehydration, or semantic equality. 

4. **CI scripts include static presence checks.**
   `check:artifact-roundtrip` and `check:canvas-history` mostly inspect file existence and source-string presence before delegating to Vitest. They are useful but not sufficient as production-quality verification.  

5. **Preview is still not a real React/Builder runtime preview.**
   `PreviewPage` reads generated source from the workflow store, but it wraps raw source in an HTML shell and renders it through `srcDoc`; there is no verified transpilation, component runtime, module graph, design-system provider, or preview protocol. 

6. **Source acquisition is still browser-file based.**
   `ImportDecompilePage` still accepts only `.ts` and `.tsx` files through browser file upload with a 1 MB limit; GitHub/GitLab/local folder/archive acquisition is not implemented in this flow. 

---

## B. Validated Learnings

### Learning: UI Builder duplicate BuilderDocument models

**Status:** Mostly fixed, still needs enforcement.

**Evidence:** Previous canonical model work remains, and this commit adds `check:builder-canonical-document` at root. 

**Impact:** The risk is lower, but the current commit still has adapter-level casts from artifact projection into UI Builder `NodeId[]`, especially in `ModelToBuilderAdapter.ts`. 

**Required action:** Keep `BuilderDocument` canonical in `@ghatana/ui-builder`, but add a hard rule that any `NodeId` conversion must go through a named validator/adapter.

**Priority:** P0.

---

### Learning: Canvas undo/redo correctness and multi-canvas isolation

**Status:** Significantly improved, not fully closed.

**Evidence:** The new command-history regression tests cover node add/update/delete, edge add/update/delete, duplicate, group/ungroup, transactions, mixed mutations, redo clearing, and isolation.   Multi-canvas isolation tests now cover element, node, edge, history, selection, viewport, clock, ID provider, and concurrent operations.  

**Impact:** Good hardening progress. The remaining concern is transaction semantics: individual controller methods still push their own history entries, and `CommandTransaction.commit()` also pushes a transaction-level entry.   The tests work around this with comments and loops, but production-grade “one transaction = one history entry” is not fully guaranteed.

**Required action:** Add a suppress-history transaction mode or command-only mutation path so mutations inside transactions do not push individual entries.

**Priority:** P0.

---

### Learning: DS Generator was only preset/brand utility

**Status:** Mostly fixed for generator foundation.

**Evidence:** DS generator now has strict canonical component states and deterministic generation context.  Root gate `check:ds-generator-golden` now runs static checks plus golden tests for `golden.test.ts` and `emit-files.test.ts`. 

**Impact:** DS Generator is now a credible deterministic generation foundation. It still needs strict failure gates for contrast violations, full token graph/alias validation, component state output verification, and generated docs/examples if those are expected outputs.

**Required action:** Make `check:ds-generator-golden` validate generated output semantics, not only file/symbol presence. 

**Priority:** P1.

---

### Learning: Studio routes were not wired to real workflows

**Status:** Partially fixed.

**Evidence:** A shared workflow store now exists for import/decompile/edit/preview/fidelity state.  `ImportDecompilePage` stores decompile result, model, projected BuilderDocument, preview source, fidelity report, and exposes route actions.  `CanvasPage`, `BuilderStudio`, `PreviewPage`, and `FidelityReportPage` now read from that shared workflow state.    

**Impact:** This is a major step. However, workflow persistence is still in-memory Jotai for artifact workflow and localStorage for Builder documents. It is not yet workspace/project durable persistence.

**Required action:** Add durable artifact workflow persistence and E2E tests for import → canvas edit → builder edit → preview → fidelity.

**Priority:** P0.

---

### Learning: Artifact compiler/decompiler needs first-class production architecture

**Status:** Improved foundation, still not production-grade.

**Evidence:** `check:artifact-roundtrip` is now registered and backed by round-trip/protected-region tests.   `compileReact` now emits protected region markers. 

**Impact:** The gap is now narrower but still significant. There is still no repo-scale scanner, acquisition service, route graph, real module resolver, import manager, formatter, protected-region rehydration, generated-file diff, or backend/Java heavy-analysis service.

**Required action:** Promote artifact compiler/decompiler from file-level TSX proof to repository-level artifact intelligence pipeline.

**Priority:** P0.

---

## C. Package-by-Package Current State

## Package: `@ghatana/canvas`

### Intended responsibility

Generic, product-agnostic canvas runtime: nodes, edges, groups, layers, selections, viewport, pan/zoom, undo/redo, command history, serialization, multi-canvas isolation, tools, plugins, a11y, observability, and collaboration readiness.

### Actual current responsibility

The package now has stronger command-history and isolation tests. The controller still uses snapshot-based history, and individual mutators still push history entries directly. 

### What exists

* `HybridCanvasController`
* Command model and executor
* Command transaction type
* Pre-mutation snapshot handling
* Group/ungroup/duplicate history behavior
* Multi-canvas isolation tests

### What is correct

* Add/update/delete for nodes, edges, and elements now have explicit undo/redo regression tests. 
* Group/ungroup and transactions are covered. 
* Isolation tests cover state, history, selection, viewport, dependency injection, separate stores, and concurrent operations.  

### What is incomplete

* Transaction internals still allow individual mutators to push entries inside a transaction. 
* No verified runtime canvas document schema/migration system from this audit.
* No verified serialization round-trip tests for the complete canvas document.
* No verified a11y interaction tests for keyboard canvas workflows.

### Production readiness rating

**70 / 100**
Improved significantly, but still not fully production-grade because command/transaction history semantics need a true no-double-entry design.

---

## Package: `@ghatana/ui-builder`

### Intended responsibility

Canonical BuilderDocument model, operations, validation, component registry integration, import/export, persistence, preview/codegen, and scene/canvas projection adapters.

### Actual current responsibility

UI Builder remains the canonical document package, and Studio now has adapters that project artifact models and canvas state into/out of BuilderDocument.  

### What exists

* Canonical document gate in root scripts.
* Model-to-Builder adapter.
* Builder Studio reads imported workflow document.
* Builder Studio syncs edits back to workflow store when editing an imported artifact. 
* Builder palette is now registry-backed through `@ghatana/ds-registry`, not hard-coded. 

### What is incorrect or risky

* `ModelToBuilderAdapter.ts` performs nested casts into `NodeId[]` when transferring projected slots. 
* The model-to-builder projection loses artifact-specific provenance and ownership unless separately stored in metadata.
* Builder persistence is still localStorage-backed for Studio documents. 

### Production readiness rating

**68 / 100**
The canonical document direction is good; the remaining gaps are durable persistence, provenance preservation, and no unsafe conversions.

---

## Package: `@ghatana/ds-generator`

### Intended responsibility

Deterministic design-system generation with token graph, semantic aliases, component variants/states, contrast validation, multi-target file emission, golden tests, and accessibility gates.

### Actual current responsibility

The DS model now includes canonical states and deterministic generation context.  Golden tests are wired into root commands. 

### What exists

* Strict canonical component state enum.
* Deterministic `GenerationContext`.
* Golden snapshot test wiring.
* Multi-target emitter checks through `check:ds-generator-golden`.

### What is correct

* The previous non-deterministic timestamp issue is fixed through context injection. 
* The previous arbitrary component state string issue is fixed through `z.enum(CANONICAL_COMPONENT_STATES)`. 

### What is incomplete

* `check:ds-generator-golden.mjs` is still largely a static file/symbol presence gate. 
* Need contrast failure enforcement as part of generation, not only helper availability.
* Need generated docs/examples if those are part of target production requirements.
* Need golden tests for component states and semantic alias resolution, not only output target existence.

### Production readiness rating

**78 / 100**
Best-improved package in this commit. Needs semantic golden tests and contrast/a11y enforcement to be production-grade.

---

## Package: `@ghatana/ghatana-studio`

### Intended responsibility

Product-facing orchestration layer for import/decompile, canvas visualization/editing, UI Builder editing, DS generation, validation, preview, export, re-import, diff/fidelity, and traceability.

### Actual current responsibility

Studio now has a real cross-route workflow store and imports/decompile output can flow into Canvas, Builder, Preview, and Fidelity Report.  

### What exists

* `ArtifactWorkflowStore`
* Import/decompile route writes workflow state.
* Canvas reads projected BuilderDocument and writes position updates.
* Builder reads projected BuilderDocument and syncs edits back.
* Preview reads workflow preview source.
* Fidelity Report reads workflow fidelity report.
* Builder palette now uses DS registry.

### What is correct

* Studio is now meaningfully orchestrating platform packages instead of only showing isolated route shells.
* Import route now exposes route actions to open Canvas, Builder, and Fidelity. 

### What is incomplete or risky

* Artifact workflow state is Jotai in-memory, not durable.
* Builder document persistence remains localStorage-backed.
* `CanvasPage` still has an unsafe `as unknown as BuilderCanvasNode[]` cast. 
* Preview is still a raw iframe `srcDoc` view of generated source, not a true React runtime preview. 
* Import/decompile is still browser upload only and limited to `.ts/.tsx` files under 1 MB. 

### Production readiness rating

**62 / 100**
Substantially improved workflow wiring, but not production durable or fully validated.

---

## Package: Artifact Compiler/Decompiler

### Intended responsibility

Repository acquisition, scanning, parsing, logical model creation, provenance, projection, editing bridge, compilation, protected regions, residual islands, fidelity, validation, preview, re-import, diff, and evidence packs.

### Actual current responsibility

This commit improves proof of round-trip and protected region behavior, but the current implementation is still TS/TSX file-level rather than repository-level artifact intelligence.

### What exists

* Root `check:artifact-roundtrip`.
* Round-trip tests.
* Protected-region tests.
* Protected-region compiler markers.
* Model-to-Builder adapter.
* Workflow store integration from import/decompile to Studio routes.

### What is correct

* Protected region markers are now emitted into compiled React output. 
* Tests verify the marker format and ownership annotations. 

### What is incomplete

* No repo scanning.
* No GitHub/GitLab/local folder/archive acquisition.
* No dependency graph resolver.
* No route graph extraction.
* No real JSX tree preservation.
* No import preservation.
* No protected-region rehydration proof.
* No generated source diff.
* No backend/Java candidate service.
* No durable evidence pipeline.

### Production readiness rating

**45 / 100**
Improved from foundation to early workflow proof, but not production-grade compiler/decompiler yet.

---

## D. Artifact Compiler/Decompiler Deep Review

### Current locations

| Area                       | Location                                                                                 | Status                  |
| -------------------------- | ---------------------------------------------------------------------------------------- | ----------------------- |
| Shared workflow state      | `platform/typescript/ghatana-studio/src/state/artifactWorkflowStore.ts`                  | New and useful          |
| Import/decompile UI        | `platform/typescript/ghatana-studio/src/routes/ImportDecompilePage.tsx`                  | Partially wired         |
| Model → Builder adapter    | `platform/typescript/ghatana-studio/src/adapters/ModelToBuilderAdapter.ts`               | Exists, but casts slots |
| Compiler protected regions | `platform/typescript/artifact-compiler-ts/src/compile/react.ts`                          | Improved                |
| Round-trip tests           | `platform/typescript/artifact-compiler-ts/src/__tests__/roundtrip.test.ts`               | Exists, shallow         |
| Protected region tests     | `platform/typescript/artifact-compiler-ts/src/__tests__/react-protected-regions.test.ts` | Exists                  |
| Root gate                  | `scripts/check-artifact-roundtrip.mjs`                                                   | Static + Vitest gate    |

### Capability matrix

| Capability            |    Current Status | Correct Owner                  | TS / Java / Backend / Shared Contract | Gap                                       | Priority |
| --------------------- | ----------------: | ------------------------------ | ------------------------------------- | ----------------------------------------- | -------- |
| Source acquisition    |           Partial | Studio + backend               | Studio + backend                      | Browser upload only                       | P0       |
| Repo scanning         |           Missing | Artifact backend service       | Java/backend candidate                | No repo scanner/indexer                   | P0       |
| File classification   |           Partial | TS compiler adapter            | TS                                    | Path/heuristic-based                      | P1       |
| Dependency graph      |           Partial | Compiler/backend graph service | TS + backend                          | No resolver                               | P0       |
| Component graph       |           Partial | Compiler                       | TS                                    | No full JSX tree model                    | P0       |
| Route graph           |           Missing | Compiler/backend               | TS + backend                          | No route extraction                       | P0       |
| Provenance            |           Partial | Contracts + compiler           | Shared + TS                           | Source refs not complete enough           | P0       |
| Ownership markers     |           Partial | Compiler                       | TS                                    | Markers emitted, but no rehydration proof | P0       |
| Protected regions     |           Partial | Compiler                       | TS                                    | Emission only; preservation not proven    | P0       |
| Residual islands      |           Partial | Compiler + Studio              | TS + Studio                           | Shallow UI triage                         | P1       |
| Fidelity scoring      |           Partial | Contracts/compiler             | Shared + TS                           | No semantic equality                      | P0       |
| Canvas projection     |           Partial | Studio adapter                 | TS                                    | Duplicated adapters and unsafe cast       | P0       |
| UI Builder projection |           Partial | Studio adapter                 | TS                                    | Provenance loss and casts                 | P0       |
| DS projection         |           Partial | Compiler/DS                    | TS                                    | Token discovery missing                   | P1       |
| Code generation       |           Partial | Compiler                       | TS                                    | Generic output, no import preservation    | P0       |
| Re-import             | Partial test only | Compiler + Studio              | TS                                    | No route workflow                         | P0       |
| Diff                  |           Missing | Compiler/backend + Studio      | Backend + Studio                      | No diff/fidelity UI beyond report         | P1       |
| Evidence pack         | Partial contracts | Shared + backend               | Shared + backend                      | No durable evidence pipeline              | P0       |

---

## E. End-to-End Workflow Review

| Workflow Step         | Current Implementation                              | Owner             |  Status | Gap                                  | Required Fix                                | Tests                      |
| --------------------- | --------------------------------------------------- | ----------------- | ------: | ------------------------------------ | ------------------------------------------- | -------------------------- |
| Source acquisition    | Browser `.ts/.tsx` upload, 1 MB limit               | Studio            | Partial | No repo/folder/archive/GitHub/GitLab | Add source acquisition service              | Acquisition contract tests |
| Scan/read             | FileReader in browser                               | Studio            | Partial | No indexing/scanning                 | Backend/Java scanner                        | Scanner integration tests  |
| Parse/decompile       | `decompileTsx` via lazy import                      | Artifact compiler | Partial | Limited extraction                   | Expand AST extraction                       | TSX fixture tests          |
| Logical model         | Stored in workflow atom                             | Studio/contracts  | Partial | Shallow model                        | Add route/component/API/token graph         | Model schema tests         |
| Canvas projection     | `builderToCanvas` in `CanvasPage`                   | Studio adapter    | Partial | Duplicate adapters, unsafe cast      | Consolidate adapter                         | Adapter tests              |
| UI Builder projection | `projectModelToBuilderDocument`                     | Studio adapter    | Partial | Casts and provenance loss            | Canonical validated adapter                 | Projection tests           |
| DS binding            | DS generator exists, not wired to artifact workflow | DS/Studio         | Partial | No token discovery bridge            | Add DS projection workflow                  | DS binding tests           |
| Validation            | New checks + basic validation panels                | CI/Studio         | Partial | Static checks too shallow            | Add behavior gates                          | Workflow E2E               |
| Preview               | workflow source → `iframe srcDoc`                   | Studio            | Partial | Not real React preview               | Add preview runtime/protocol                | Preview tests              |
| User modification     | Canvas/Builder can update projected doc             | Studio            | Partial | No durable persistence               | Workspace artifact state                    | E2E                        |
| Compile/generate      | `compileReact` during import                        | Compiler          | Partial | Not regenerated after Builder edits  | Compile from current Builder/artifact state | Compile tests              |
| Export/save           | Browser/localStorage                                | Studio            | Partial | Not production persistence           | Artifact save/export service                | Export parity tests        |
| Re-import             | Round-trip test only                                | Compiler          | Partial | Not Studio workflow                  | Add re-import route/action                  | Round-trip E2E             |
| Diff/fidelity report  | Fidelity route reads workflow atom                  | Studio            | Partial | No diff view                         | Add source/model diff                       | Diff tests                 |
| Regression tests      | New gates                                           | Root/CI           | Partial | Static checks too shallow            | Improve semantic assertions                 | CI gate tests              |

---

## F. Feature Completeness Matrix

| Capability                  | Canvas   | UI Builder   | DS Generator | Studio                 | Artifact Compiler/Decompiler | Current Status | Correct Owner           | Priority |
| --------------------------- | -------- | ------------ | ------------ | ---------------------- | ---------------------------- | -------------: | ----------------------- | -------- |
| Canonical BuilderDocument   | N/A      | Stronger     | N/A          | Uses it                | Projects into it             |        Partial | UI Builder              | P0       |
| Canvas undo/redo            | Stronger | N/A          | N/A          | Uses canvas            | N/A                          |        Partial | Canvas                  | P0       |
| Multi-canvas isolation      | Stronger | N/A          | N/A          | Not fully E2E          | N/A                          |        Partial | Canvas                  | P1       |
| Builder/canvas adapter      | N/A      | Partial      | N/A          | Duplicated             | N/A                          |      Duplicate | Studio/platform adapter | P0       |
| DS deterministic generation | N/A      | N/A          | Stronger     | Partial UI             | N/A                          |        Partial | DS Generator            | P1       |
| Artifact round-trip         | N/A      | Partial      | N/A          | Partial                | Partial                      |        Partial | Compiler + Studio       | P0       |
| Protected regions           | N/A      | N/A          | N/A          | Not surfaced           | Partial                      |        Partial | Compiler                | P0       |
| Preview runtime             | N/A      | Partial      | N/A          | Weak                   | Partial                      |        Partial | Studio + Builder        | P1       |
| Source acquisition          | N/A      | N/A          | N/A          | Browser only           | Input only                   |        Partial | Backend + Studio        | P0       |
| Repo scanning               | N/A      | N/A          | N/A          | Missing                | Missing                      |        Missing | Backend/Java            | P0       |
| Golden tests                | N/A      | Some         | Stronger     | Some                   | Some                         |        Partial | Package owners          | P1       |
| Durable persistence         | N/A      | localStorage | N/A          | in-memory/localStorage | Missing                      |        Partial | Studio/backend          | P0       |

---

## G. File-by-File Findings

### `package.json`

**Finding:** New gates are added and wired into broader readiness commands. 
**Impact:** Strong improvement.
**Required change:** Add a single aggregate `check:studio-artifact-workflow` that runs import/canvas/builder/preview/fidelity E2E, not only unit/static gates.
**Priority:** P0.

---

### `scripts/check-artifact-roundtrip.mjs`

**Finding:** Useful guard, but mostly file/symbol/string presence validation. 
**Impact:** Prevents missing files, not semantic regressions.
**Required change:** Move more validation into executable fixtures and AST-level assertions.
**Priority:** P1.

---

### `scripts/check-canvas-history.mjs`

**Finding:** Checks existence of mutators, atoms, deprecated global store marking, and test files. 
**Impact:** Good CI tripwire, but cannot prove pre-mutation ordering by string checks.
**Required change:** Depend primarily on behavior tests; use static script only for structural policy.
**Priority:** P1.

---

### `scripts/check-builder-canvas-adapter.mjs`

**Finding:** The script focuses on `BuilderCanvasProjectionAdapter.ts`, but a second adapter `BuilderCanvasAdapter.ts` also exists.  
**Impact:** The gate unintentionally allows duplicate adapter ownership.
**Required change:** Enforce one canonical adapter and fail on duplicate builder/canvas projection implementations.
**Priority:** P0.

---

### `scripts/check-ds-generator-golden.mjs`

**Finding:** It checks source files, symbols, token graph presence, contrast module presence, and target emitter symbols. 
**Impact:** Helpful but not enough to prove generator correctness.
**Required change:** Add semantic golden verification for component states, contrast failures, semantic aliases, and emitted file determinism.
**Priority:** P1.

---

### `platform/typescript/ds-generator/src/model/design-system-document.ts`

**Finding:** Determinism and canonical component states were added. 
**Impact:** Prior DS generator blocker is mostly resolved.
**Required change:** Add migration support and golden coverage for all canonical states.
**Priority:** P1.

---

### `platform/typescript/artifact-compiler-ts/src/compile/react.ts`

**Finding:** Protected region markers were added for generated imports and user-authored body. 
**Impact:** Important progress toward safe regeneration.
**Required change:** Add decompiler support to parse and preserve existing region bodies during regeneration.
**Priority:** P0.

---

### `platform/typescript/artifact-compiler-ts/src/__tests__/roundtrip.test.ts`

**Finding:** Basic source → model → source → model tests exist. 
**Impact:** Good first proof, but still shallow.
**Required change:** Assert semantic equality, import preservation, JSX shape, protected-region rehydration, and residual/fidelity details.
**Priority:** P0.

---

### `platform/typescript/artifact-compiler-ts/src/__tests__/react-protected-regions.test.ts`

**Finding:** Tests verify region markers, owner annotations, function signature, residual marker, unique region IDs, and output shape. 
**Impact:** Useful compiler-marker regression coverage.
**Required change:** Add test that modifies a protected body region, recompiles, and proves user content is preserved.
**Priority:** P0.

---

### `platform/typescript/ghatana-studio/src/state/artifactWorkflowStore.ts`

**Finding:** Central workflow state exists for decompile result, model, projected BuilderDocument, preview source, fidelity report, and timestamp. 
**Impact:** Major Studio integration improvement.
**Required change:** Add persistence adapter and lifecycle/job ID model so state survives reload and can be audited.
**Priority:** P0.

---

### `platform/typescript/ghatana-studio/src/routes/ImportDecompilePage.tsx`

**Finding:** Import route now writes model, projected builder document, preview source, and fidelity report to workflow store, and exposes navigation actions. 
**Impact:** Good cross-route workflow improvement.
**Required change:** Replace browser-only upload with source acquisition abstraction and add GitHub/GitLab/local/archive providers.
**Priority:** P0.

---

### `platform/typescript/ghatana-studio/src/routes/CanvasPage.tsx`

**Finding:** Canvas route reads projected BuilderDocument from workflow state and writes node changes back, but uses `as unknown as BuilderCanvasNode[]`. 
**Impact:** Workflow is improved, but type safety is still not clean.
**Required change:** Normalize canvas change event contract or add a runtime validator before converting to builder nodes.
**Priority:** P0.

---

### `platform/typescript/ghatana-studio/src/adapters/BuilderCanvasAdapter.ts`

**Finding:** Provides typed pure functions for BuilderDocument → canvas projection, selection validation, and geometry delta reconciliation. 
**Impact:** Good adapter shape, but overlaps with `BuilderCanvasProjectionAdapter.ts`.
**Required change:** Merge into the canonical adapter and delete the duplicate.
**Priority:** P0.

---

### `platform/typescript/ghatana-studio/src/adapters/BuilderCanvasProjectionAdapter.ts`

**Finding:** Also projects BuilderDocument to canvas and canvas positions back to BuilderDocument. 
**Impact:** Duplicate ownership risk.
**Required change:** Consolidate with `BuilderCanvasAdapter.ts` under one file and one test suite.
**Priority:** P0.

---

### `platform/typescript/ghatana-studio/src/adapters/ModelToBuilderAdapter.ts`

**Finding:** Bridges `LogicalArtifactModel` to canonical BuilderDocument but uses casts for slot IDs. 
**Impact:** Good ownership direction, but must avoid unsafe casts.
**Required change:** Add a validated `toNodeId()` conversion or UI Builder helper.
**Priority:** P0.

---

### `platform/typescript/ghatana-studio/src/sections/BuilderStudio.tsx`

**Finding:** Builder Studio now reads workflow projected documents, syncs edits back to workflow state, and uses DS registry starter contracts.  
**Impact:** Major improvement.
**Required change:** Replace localStorage-only persistence with workspace/project artifact persistence.
**Priority:** P0.

---

### `platform/typescript/ghatana-studio/src/routes/PreviewPage.tsx`

**Finding:** Preview reads workflow preview source but still renders raw source through iframe `srcDoc`. 
**Impact:** Not yet a real preview runtime.
**Required change:** Add actual preview host that transpiles/renders React output with DS/theme providers and security policy.
**Priority:** P1.

---

### `platform/typescript/ghatana-studio/src/routes/FidelityReportPage.tsx`

**Finding:** Fidelity route now reads from workflow atom, falling back to legacy router state. 
**Impact:** Good route integration.
**Required change:** Add residual island triage and diff view, not only loss point list.
**Priority:** P1.

---

## H. Iterative Production-Grade Implementation Plan

## Phase 1: Consolidate ownership and remove duplicate adapters

**Goal:** Eliminate duplicate builder/canvas ownership.

**Files to modify:**

* `platform/typescript/ghatana-studio/src/adapters/BuilderCanvasAdapter.ts`
* `platform/typescript/ghatana-studio/src/adapters/BuilderCanvasProjectionAdapter.ts`
* `scripts/check-builder-canvas-adapter.mjs`
* `platform/typescript/ghatana-studio/src/components/builder/VisualCanvas.tsx`
* `platform/typescript/ghatana-studio/src/routes/CanvasPage.tsx`

**Tasks:**

* Pick one canonical adapter.
* Move all projection, selection validation, and geometry reconciliation into it.
* Delete the duplicate adapter.
* Fail CI if another builder/canvas projection adapter appears.
* Replace `as unknown as BuilderCanvasNode[]` in `CanvasPage`.

**Done criteria:** One adapter, no unsafe boundary casts, all tests pass.

---

## Phase 2: Make canvas transactions truly atomic

**Goal:** Ensure one logical transaction produces one undo entry.

**Files to modify:**

* `platform/typescript/canvas/src/hybrid/hybrid-canvas-controller.ts`
* `platform/typescript/canvas/src/commands/types.ts`
* `platform/typescript/canvas/src/commands/executor.ts`
* `platform/typescript/canvas/src/hybrid/__tests__/command-history-regression.test.ts`

**Tasks:**

* Add history suppression mode inside transaction execution.
* Ensure mutators can operate without pushing individual history when transaction context is active.
* Assert exact history stack depth, not only visible state.
* Add tests for transaction abort rollback or explicitly document non-rollback behavior.

**Done criteria:** Group, ungroup, duplicate, and transaction each create exactly one undo entry.

---

## Phase 3: Harden artifact round-trip semantics

**Goal:** Move from “non-empty output” to “source intent preserved.”

**Files to modify:**

* `platform/typescript/artifact-compiler-ts/src/decompile/tsx.ts`
* `platform/typescript/artifact-compiler-ts/src/compile/react.ts`
* `platform/typescript/artifact-compiler-ts/src/__tests__/roundtrip.test.ts`
* `platform/typescript/artifact-compiler-ts/src/__tests__/react-protected-regions.test.ts`

**Tasks:**

* Preserve import declarations.
* Preserve component names, prop interfaces, JSX root shape, and exported symbols.
* Parse `@ghatana-region` markers during decompile.
* Preserve user-authored protected body regions during recompile.
* Add golden fixtures for source → model → source → model semantic equality.

**Done criteria:** Round-trip tests fail on source intent loss.

---

## Phase 4: Replace browser-only source acquisition

**Goal:** Add real source acquisition abstraction.

**Files to create:**

* `platform/typescript/artifact-contracts/src/acquisition.ts`
* `platform/typescript/ghatana-studio/src/adapters/SourceAcquisitionAdapter.ts`
* Backend/Java service boundary docs/contracts for repo scanning.

**Tasks:**

* Define `SourceAcquisitionDescriptor`.
* Support file upload as one provider.
* Add GitHub/GitLab/local/archive provider contracts.
* Add long-running job model for large repo scan/index.

**Done criteria:** Import route uses provider abstraction, not direct FileReader-only logic.

---

## Phase 5: Make Studio workflow durable

**Goal:** Persist artifact workflow state.

**Files to modify/create:**

* `artifactWorkflowStore.ts`
* new `ArtifactWorkflowPersistenceAdapter.ts`
* Studio route tests

**Tasks:**

* Persist job result, model, builder document, preview source, fidelity report, and residual report.
* Add reload recovery.
* Add audit metadata and timestamps.
* Preserve imported artifacts separately from local demo docs.

**Done criteria:** User can refresh Studio and continue the artifact workflow.

---

## Phase 6: Real preview runtime

**Goal:** Preview compiled UI, not raw generated source.

**Files to modify:**

* `PreviewPage.tsx`
* UI Builder preview/runtime package
* Artifact compiler output contract

**Tasks:**

* Add preview document protocol.
* Transpile/render compiled component output safely.
* Inject DS/theme provider.
* Add sandbox policy tests.
* Add preview parity tests.

**Done criteria:** Preview renders actual UI and fails safely.

---

## Phase 7: DS generator semantic hardening

**Goal:** Make DS generation production-grade.

**Tasks:**

* Add contrast-failure gate.
* Add semantic alias golden tests.
* Add component state output tests for all canonical states.
* Add migration tests for DS document schema.
* Validate token graph cycles and missing references.

**Done criteria:** `check:ds-generator-golden` proves output semantics, not only symbols.

---

## I. Exact TODO List

* [ ] `platform/typescript/ghatana-studio/src/adapters/BuilderCanvasAdapter.ts` — merge with `BuilderCanvasProjectionAdapter.ts`.

  * Why: Duplicate ownership.
  * Validation: one canonical adapter only.
  * Priority: P0.

* [ ] `platform/typescript/ghatana-studio/src/routes/CanvasPage.tsx` — remove `as unknown as BuilderCanvasNode[]`.

  * Why: Unsafe package-boundary cast.
  * Validation: runtime validator or typed canvas event contract.
  * Priority: P0.

* [ ] `scripts/check-builder-canvas-adapter.mjs` — fail when multiple builder/canvas adapters exist.

  * Why: Current check allows duplicates.
  * Validation: script test with duplicate fixture.
  * Priority: P0.

* [ ] `platform/typescript/canvas/src/hybrid/hybrid-canvas-controller.ts` — add transaction history suppression.

  * Why: Avoid individual entries inside transaction.
  * Validation: exact stack-depth tests.
  * Priority: P0.

* [ ] `platform/typescript/artifact-compiler-ts/src/decompile/tsx.ts` — parse protected-region markers and preserve source spans.

  * Why: Protected region emission alone is insufficient.
  * Validation: modify protected body → recompile → body preserved.
  * Priority: P0.

* [ ] `platform/typescript/artifact-compiler-ts/src/__tests__/roundtrip.test.ts` — replace weak node-count assertions with semantic equivalence checks.

  * Why: Node count does not prove fidelity.
  * Validation: golden model/source comparison.
  * Priority: P0.

* [ ] `platform/typescript/ghatana-studio/src/routes/ImportDecompilePage.tsx` — replace direct FileReader acquisition with source acquisition provider.

  * Why: Need GitHub/GitLab/folder/archive support.
  * Validation: provider contract tests.
  * Priority: P0.

* [ ] `platform/typescript/ghatana-studio/src/state/artifactWorkflowStore.ts` — add persistence adapter.

  * Why: In-memory workflow state is not production-grade.
  * Validation: reload recovery tests.
  * Priority: P0.

* [ ] `platform/typescript/ghatana-studio/src/routes/PreviewPage.tsx` — replace raw `srcDoc` source rendering with real preview runtime.

  * Why: Current preview is not runtime parity.
  * Validation: preview protocol and sandbox tests.
  * Priority: P1.

* [ ] `scripts/check-ds-generator-golden.mjs` — add semantic golden validations.

  * Why: Current static checks are insufficient.
  * Validation: canonical states, aliases, contrast failure tests.
  * Priority: P1.

---

## J. Commands to Validate

```bash
pnpm install
pnpm lint
pnpm typecheck
pnpm test
pnpm build

pnpm check:circular-deps
pnpm check:architecture-boundaries
pnpm check:design-system-conformance
pnpm check:studio-kernel-api
pnpm check:production-stubs
pnpm check:artifact-roundtrip
pnpm check:canvas-history
pnpm check:builder-canonical-document
pnpm check:builder-canvas-adapter
pnpm check:ds-generator-golden
pnpm check:kernel-authoring-pipeline
pnpm check:phase8
pnpm check:world-class-platform-readiness
```

Package-specific commands:

```bash
pnpm --dir platform/typescript/canvas exec vitest run src/hybrid/__tests__/command-history-regression.test.ts src/hybrid/__tests__/multi-canvas-isolation.test.ts
pnpm --dir platform/typescript/ui-builder exec vitest run src/core/__tests__/builder-document.test.ts
pnpm --dir platform/typescript/ghatana-studio exec vitest run src/adapters/__tests__/BuilderCanvasProjectionAdapter.test.ts src/adapters/__tests__/BuilderCanvasAdapter.test.ts
pnpm --dir platform/typescript/artifact-compiler-ts exec vitest run src/__tests__/roundtrip.test.ts src/__tests__/react-protected-regions.test.ts
pnpm --dir platform/typescript/ds-generator exec vitest run src/__tests__/golden.test.ts src/__tests__/emit-files.test.ts
pnpm --dir platform/typescript/ghatana-studio test:e2e
pnpm --dir platform/typescript/ghatana-studio test:a11y
```

---

## K. Final Production Readiness Gate

Before these areas can be called production-grade, the following must be true:

* One canonical BuilderDocument.
* One canonical Builder ↔ Canvas adapter.
* No unsafe `as unknown as` package-boundary conversions.
* Runtime-validatable canvas document with migrations.
* True command-based undo/redo with atomic transaction history.
* Real Studio import → canvas → builder → compile → preview → fidelity workflow.
* Durable workflow persistence.
* Source acquisition providers beyond browser upload.
* Repository scanner/indexer boundary.
* Source → model → source → model semantic equality tests.
* Protected-region rehydration and preservation tests.
* Fidelity and residual island triage UI.
* Golden tests for generated DS and code outputs.
* Real preview runtime parity.
* Accessibility, i18n, privacy, security, and observability gates.
* No localStorage-only production persistence.
* No duplicated schemas or duplicate ownership.
* No test theater/static-only production claims.

---

## Final Verdict

```markdown
Final Verdict: Partial

Are these areas feature-complete and correctly implemented for a production-grade, world-class Ghatana product-development platform?

No, but this commit is a meaningful improvement.

Reason:
The commit adds important readiness gates, deterministic DS generation context, canonical DS component states, artifact round-trip tests, protected-region markers, a Studio artifact workflow store, and partial import → Canvas/Builder/Preview/Fidelity wiring. However, the implementation is still not production-grade because adapter ownership is duplicated, CanvasPage still has an unsafe boundary cast, round-trip tests are shallow, preview is not a real runtime preview, source acquisition is browser-upload only, and persistence remains in-memory/localStorage rather than durable workspace/project artifact storage.

Required minimum work before production:
Consolidate BuilderCanvas adapters, remove unsafe casts, make canvas transactions truly atomic, harden artifact round-trip tests to semantic equality, add protected-region rehydration, add source acquisition providers, make workflow state durable, and replace raw srcDoc preview with a real preview runtime.

Recommended next milestone:
“Production Round-Trip Authoring Workflow v1” — a complete, tested path from source upload/acquisition → decompile → model → canvas/builder edit → compile → preview → re-import → fidelity/diff.

Recommended first implementation PR:
Consolidate BuilderCanvasAdapter + BuilderCanvasProjectionAdapter into one canonical adapter and remove the unsafe CanvasPage cast.

Recommended parallel workstreams:
1. Canvas transaction/history correctness.
2. Artifact compiler protected-region rehydration and semantic round-trip tests.
3. Studio durable artifact workflow persistence.
4. Real preview runtime protocol.
5. Source acquisition and backend/Java scanner boundary.
6. DS generator semantic golden and contrast gates.
```
# Deep Snapshot Audit — Artifact Authoring Stack

**Repo:** `samujjwal/ghatana`
**Target commit:** `f53ba36eb9987a75d14cf50e904106771c4435df`
**Commit verified:** `build fix 5-21-1` 

I audited the current snapshot for the scoped artifact authoring areas: Ghatana Studio, Canvas, UI Builder, DS Generator, and artifact compiler/decompiler. I did not run the repo locally; findings are grounded in repository files, scripts, and tests visible at this commit.

---

## A. Executive Summary

### Overall readiness rating

**Partial, stronger than `f629d27...`, but still not production-grade.**

This commit mainly adds cross-product interaction checks and tests, but the artifact-authoring stack also carries forward important improvements from the previous commit:

* `check:artifact-roundtrip`, `check:studio-artifact-workflow-e2e`, `check:canvas-history`, `check:builder-canonical-document`, `check:builder-canvas-adapter`, and `check:ds-generator-golden` remain wired in root scripts. The broader phase-8 gate now also includes new kernel/product interaction checks. 
* `ImportDecompilePage` no longer uses only `defaultProviderRegistry`; it resolves a provider registry through `resolveProviderRegistryForEnv(env)`, with fallback to `defaultProviderRegistry`. 
* `source-acquisition.ts` now has a production acquisition client capable of GitHub/GitLab archive download, ZIP deflate support, TAR parsing, and TAR.GZ decompression using `DecompressionStream`.  
* Workflow persistence now supports a `KernelWorkflowPersistenceAdapter` with workflow-state and evidence-pack persistence via kernel API endpoints when enabled by environment configuration. 
* Playwright E2E selectors were stabilized using `data-testid`, and repository/archive tests now explicitly validate backend-boundary behavior when backend acquisition is unavailable. 
* Round-trip diff now includes AST semantic signatures, imports, exports, JSX nodes/attributes, calls, event handlers, bindings, style references, and import graph parity. 

### Final verdict

**Partial. Closer to production, not world-class production-ready yet.**

The previous top blockers are no longer in the same state:

* The Playwright `Branch/Ref` mismatch is fixed by using `data-testid="repository-ref"`.
* The default-vs-production provider issue is partially addressed by `resolveProviderRegistryForEnv`.
* TAR/TAR.GZ support is no longer just a placeholder.
* Workflow persistence has a kernel-backed option, not only localStorage.
* Round-trip diff is much deeper than before.

Remaining production blockers:

1. **Production acquisition is env-gated and browser-runtime constrained.** `resolveProviderRegistryForEnv()` only uses the production registry when `VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION === 'true'`; otherwise repository/archive providers still return pending-job boundaries. 

2. **Repository acquisition uses browser `fetch` to GitHub/GitLab archive APIs.** This may be useful for public repositories, but production-grade private repo access, auth, rate limits, large repository handling, and durable backend job execution remain unproven. 

3. **Archive unpacking is improved but still browser-bound.** It relies on `DecompressionStream`, manual ZIP/TAR parsing, and throws when ZIP data descriptors are present. Many real-world ZIP files use data descriptors, central directory structures, symlinks, or path normalization concerns that need hardening. 

4. **Kernel persistence is implemented as a client adapter, but backend endpoint existence is not proven in this audit.** The adapter calls `/api/v1/studio/workflow-state` and `/api/v1/studio/workflow-evidence`, but this audit did not find/verify the server handlers. 

5. **Round-trip diff is improved but still not complete source-intent preservation.** AST signatures are useful, but a signature-based check can still miss source-level intent such as exact control-flow semantics, complex expressions, comments outside protected regions, generated import ordering requirements, formatter stability, and route/config object equivalence. 

---

## B. Validated Learnings

### Learning: Studio route was not using production source acquisition

**Status:** Partially fixed.

**Evidence:** `ImportDecompilePage` now builds `providerRegistry` using `resolveProviderRegistryForEnv(env) ?? defaultProviderRegistry`.  `resolveProviderRegistryForEnv()` enables production acquisition only when `VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION` is set to `true`; otherwise it returns the default registry. 

**Impact:** The architecture is now configurable, but production acquisition is not guaranteed unless runtime environment is correct.

**Required action:** Add a deployment/profile gate proving production Studio builds have `VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION=true` or use a server-backed acquisition API by default.

**Priority:** P0.

---

### Learning: Archive support was incomplete

**Status:** Partially fixed.

**Evidence:** Archive handling now detects ZIP, TAR.GZ, and TAR; ZIP deflate is supported through `DecompressionStream('deflate-raw')`; TAR.GZ uses gzip decompression and TAR parsing. 

**Remaining issue:** ZIP data descriptors are explicitly unsupported, and `DecompressionStream` availability is required for gzip/deflate. 

**Priority:** P1.

---

### Learning: Playwright E2E was brittle/mismatched

**Status:** Mostly fixed.

**Evidence:** The E2E now uses stable `data-testid` selectors for provider, pasted path/content, repository URL/ref, acquisition button, archive input, and acquisition status. It also separates local pasted-source flow from repository/archive backend-boundary tests. 

**Remaining issue:** The main route E2E no longer verifies actual canvas node dragging or builder prop editing; it navigates through Canvas/Builder/Preview/Fidelity and verifies key visibility, preview sandbox, and re-import. That makes it more stable, but less complete as a full interaction E2E. 

**Priority:** P1.

---

### Learning: Workflow persistence was localStorage only

**Status:** Partially fixed.

**Evidence:** `KernelWorkflowPersistenceAdapter` persists workflow state to `/api/v1/studio/workflow-state`, loads/deletes the same endpoint, and can persist evidence packs to `/api/v1/studio/workflow-evidence`. It is selected by `resolvePersistenceAdapterForEnv()` when kernel persistence is enabled and required runtime identity is present. 

**Remaining issue:** It falls back to localStorage when identity/env is incomplete. Server endpoint existence and contract validation are not proven here. 

**Priority:** P0/P1.

---

### Learning: Round-trip diff was shallow

**Status:** Improved.

**Evidence:** `roundtrip-diff.ts` now imports TypeScript, normalizes ASTs, builds semantic signatures for imports, exports, JSX nodes/attributes, call expressions, event handlers, bindings, and style references, and checks import graph parity. 

**Remaining issue:** This is still a semantic signature, not full AST diff or executable equivalence. It is a strong improvement, not a complete fidelity proof.

**Priority:** P1.

---

## C. Package-by-Package Current State

## `@ghatana/canvas`

### Current rating: **80 / 100**

No regression found in this pass. Canvas history and multi-canvas checks remain part of root gates. 

### Remaining gaps

* Runtime canvas document schema/migration not proven.
* Full canvas document serialization/deserialization not proven.
* Current Playwright artifact workflow no longer verifies canvas drag/edit behavior deeply. 

---

## `@ghatana/ui-builder`

### Current rating: **78 / 100**

The safe NodeId conversion work from prior commits remains valid. No regression found in the inspected current files.

### Remaining gaps

* Need static enforcement that only UI Builder brands `NodeId`.
* Need stronger source provenance mapping into BuilderDocument nodes.
* Need production persistence/restore semantics for Builder documents beyond workflow state.

---

## `@ghatana/ds-generator`

### Current rating: **78 / 100**

No new DS-specific changes found in this pass. DS golden gate remains wired. 

### Remaining gaps

* WCAG contrast should be a hard generator gate.
* Semantic token alias and component-state golden tests need depth.
* DS document migrations need explicit proof.

---

## `@ghatana/ghatana-studio`

### Current rating: **79 / 100**

Studio improved most in this area:

* Environment-resolved source acquisition registry.
* Stable E2E selectors.
* Acquisition status surface.
* Kernel-backed workflow persistence adapter.
* Evidence pack persistence path.
* Fidelity Report with diff summary from prior work.

### Remaining gaps

* Production acquisition is opt-in by env.
* Backend kernel persistence endpoints are not verified in this audit.
* Browser E2E is stable but no longer deep on actual canvas/builder editing interactions.
* Repository and archive acquisition remain browser-runtime constrained if using `ProductionSourceAcquisitionBackendClient` from Studio.

---

## Artifact Compiler/Decompiler

### Current rating: **73 / 100**

The compiler/decompiler stack is improving steadily:

* Source-set repository scan remains.
* Round-trip diff now uses AST semantic signatures and import graph parity.
* Evidence pack and workflow reporting are integrated through Studio.
* Archive/source acquisition has a real client path.

### Remaining gaps

* Still not a backend repository intelligence service.
* No TypeScript Program-level full project module graph verified here.
* No full route config object / API graph / design token graph parity guarantee in this audit.
* No executable/generated artifact build verification in the round-trip path.

---

## D. Capability Matrix

| Capability           |         Current Status | Evidence                           | Remaining Gap                                 | Priority |
| -------------------- | ---------------------: | ---------------------------------- | --------------------------------------------- | -------- |
| Browser upload       |            Implemented | Route + providers                  | Large folder/perf                             | P2       |
| Pasted source        |            Implemented | E2E uses it                        | Good local path                               | P2       |
| GitHub acquisition   | Partial implementation | Production client fetches zipball  | Env-gated, auth/rate-limit/backend not proven | P0       |
| GitLab acquisition   | Partial implementation | Production client fetches archive  | Env-gated, auth/rate-limit/backend not proven | P0       |
| Archive ZIP          |               Partial+ | Stored/deflate support             | Data descriptors unsupported                  | P1       |
| TAR/TAR.GZ           |               Partial+ | TAR parser + gzip decompression    | Browser API/runtime constraints               | P1       |
| Workflow persistence |               Partial+ | Kernel adapter exists              | Endpoint/server contract unverified           | P0/P1    |
| Evidence persistence |                Partial | Kernel adapter forwards evidence   | Backend durability unverified                 | P1       |
| Playwright workflow  |               Partial+ | Stable data-testid E2E             | Shallow canvas/builder interaction depth      | P1       |
| Round-trip diff      |               Partial+ | AST signatures/import graph parity | Not full AST/source/executable parity         | P1       |
| Repo scanner         |                Partial | Source-set scanner                 | No full repo indexer                          | P0/P1    |

---

## E. Critical Findings

### P0 — Production acquisition is still deployment-config dependent

`ImportDecompilePage` uses `resolveProviderRegistryForEnv`, but production behavior depends on `VITE_STUDIO_ENABLE_PRODUCTION_ACQUISITION=true`. If the env is missing, repository/archive flows still use the pending-boundary default registry.  

**Required fix:** Add a CI/deployment gate that proves production Studio profiles enable production acquisition or route repository/archive acquisition to a server API.

---

### P0/P1 — Kernel workflow persistence adapter exists, but server side is not proven

The adapter calls `/api/v1/studio/workflow-state` and `/api/v1/studio/workflow-evidence`, but this audit only verified the client adapter, not backend handlers, authorization, tenant/workspace/project scoping, idempotency, or evidence immutability. 

**Required fix:** Add backend contracts and tests for these endpoints.

---

### P1 — Archive handling is better but still not industrial strength

ZIP data descriptors are unsupported, and decompression depends on `DecompressionStream`. 

**Required fix:** Use a proven archive library or move archive extraction to backend/Java service.

---

### P1 — E2E is stable but less deep

The E2E no longer performs real canvas drag or builder property editing; it navigates and checks workflow surfaces, preview sandbox, fidelity report, and re-import. 

**Required fix:** Add a second deeper interaction E2E for canvas movement and builder prop updates with stable test contracts.

---

### P1 — Round-trip diff still cannot prove complete source intent preservation

AST semantic signatures are useful, but not sufficient for complex source equivalence, protected comments, formatting, route config parity, data-binding semantics, or executable output parity. 

**Required fix:** Add structured AST diff + generated project build/typecheck/test validation.

---

## F. File-by-File TODOs

### `platform/typescript/ghatana-studio/src/routes/ImportDecompilePage.tsx`

* [ ] Add visible production-acquisition mode indicator.

  * Why: The route can silently use pending default registry when env is absent.
  * Priority: P1.

* [ ] Fail closed or disable GitHub/GitLab/archive options in production when production acquisition is not enabled.

  * Why: Avoid user-facing dead-end pending jobs.
  * Priority: P0.

### `platform/typescript/ghatana-studio/src/providers/source-acquisition.ts`

* [ ] Replace browser archive extraction with backend extraction for production profiles.

  * Why: Browser archive parsing has unsupported ZIP cases and runtime limitations.
  * Priority: P1.

* [ ] Add tests for ZIP data-descriptor rejection, deflated ZIP success, TAR success, TAR.GZ success, path traversal prevention, symlink handling, hidden file filtering, and max size enforcement.

  * Priority: P1.

* [ ] Add auth/token support for GitHub/GitLab APIs.

  * Priority: P0/P1.

### `platform/typescript/ghatana-studio/src/state/artifactWorkflowStore.ts`

* [ ] Add contract tests for `/api/v1/studio/workflow-state`.

  * Priority: P0.

* [ ] Add contract tests for `/api/v1/studio/workflow-evidence`.

  * Priority: P0/P1.

* [ ] Verify tenant/workspace/project scoping and authorization.

  * Priority: P0.

### `platform/typescript/ghatana-studio/e2e/artifact-workflow.spec.ts`

* [ ] Keep current stable smoke flow.

  * Priority: Done.

* [ ] Add a second deep interaction test for canvas move and builder prop edit.

  * Priority: P1.

### `platform/typescript/artifact-compiler-ts/src/diff/roundtrip-diff.ts`

* [ ] Add structured AST diff output, not only semantic signature equality.

  * Priority: P1.

* [ ] Add route graph, component graph, import graph, and DS token parity sections in diff report.

  * Priority: P1.

### `platform/typescript/artifact-compiler-ts/src/scan/repository-scan.ts`

* [ ] Add TypeScript Program-based module resolution.

  * Priority: P0/P1.

* [ ] Add generated project build/typecheck validation stage.

  * Priority: P1.

---

## G. Recommended Next Implementation Phases

### Phase 1 — Production profile enforcement

Ensure production Studio cannot expose repository/archive acquisition unless production acquisition is actually enabled and verified.

### Phase 2 — Backend acquisition service

Move GitHub/GitLab/archive acquisition to backend/Java or a server-side provider with auth, retries, rate-limit handling, streaming, and durable jobs.

### Phase 3 — Workflow persistence backend

Implement and test kernel API endpoints for workflow state and workflow evidence, including authorization and tenant/workspace/project scope.

### Phase 4 — Deep E2E interaction coverage

Add stable test contracts for actual canvas node movement and builder property mutation.

### Phase 5 — AST/graph/build round-trip validation

Add structured AST diff, graph parity, import preservation, and generated source typecheck/build validation.

---

## H. Commands to Validate

```bash
pnpm install
pnpm typecheck
pnpm lint
pnpm test
pnpm build

pnpm check:artifact-roundtrip
pnpm check:studio-artifact-workflow-e2e
pnpm check:canvas-history
pnpm check:builder-canonical-document
pnpm check:builder-canvas-adapter
pnpm check:ds-generator-golden
pnpm check:phase8
pnpm check:world-class-platform-readiness
```

Focused commands:

```bash
pnpm --dir platform/typescript/ghatana-studio exec playwright test e2e/artifact-workflow.spec.ts

pnpm --dir platform/typescript/ghatana-studio exec vitest run \
  src/providers/__tests__/source-acquisition.test.ts \
  src/state/__tests__/artifactWorkflowStore.test.ts \
  src/routes/__tests__/ImportDecompilePage.test.tsx \
  src/routes/__tests__/FidelityReportPage.test.tsx

pnpm --dir platform/typescript/artifact-compiler-ts exec vitest run \
  src/__tests__/repository-scan.test.ts \
  src/__tests__/roundtrip-diff.test.ts \
  src/__tests__/roundtrip.test.ts
```

---

## Final Verdict

```markdown
Final Verdict: Partial

Are these areas feature-complete and correctly implemented for a production-grade, world-class Ghatana product-development platform?

No, but commit f53ba36eb9987a75d14cf50e904106771c4435df is a meaningful hardening step.

Reason:
The commit carries forward artifact workflow gates, fixes E2E selector stability, adds environment-resolved production acquisition wiring, improves archive support, introduces kernel workflow/evidence persistence adapters, and deepens round-trip diff with AST semantic signatures and import graph parity. However, production acquisition remains env-gated, server-side acquisition and persistence endpoints are not proven in this audit, browser archive extraction remains limited, E2E is now more stable but less interaction-deep, and round-trip fidelity still lacks full AST/graph/build parity.

Required minimum work before production:
Add production profile enforcement, implement backend acquisition and persistence contracts, harden archive handling, add deep canvas/builder interaction E2E, and validate generated artifacts through AST/graph/build/typecheck gates.

Recommended next milestone:
Production Acquisition, Persistence, and Fidelity Enforcement v1.

Recommended first implementation PR:
Add production profile enforcement for source acquisition: in production, repository/archive providers must use a backend-backed registry or be disabled with a clear unavailable state.

Recommended parallel workstreams:
1. Backend repository/archive acquisition service.
2. Kernel workflow/evidence persistence API and tests.
3. Deep Studio interaction E2E for Canvas and Builder.
4. Structured AST/graph diff and generated build validation.
5. Archive extraction hardening and security validation.
```
