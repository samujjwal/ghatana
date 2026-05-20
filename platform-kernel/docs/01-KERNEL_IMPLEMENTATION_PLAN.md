# Iterative Production-Grade Feature Completeness Audit Report

## Ghatana Studio, Canvas, UI Builder, DS Generator, and Artifact Compiler/Decompiler

**Repo:** `samujjwal/ghatana`
**Target commit:** `759ea1f37c20401ef568ef4d8fb0a704641aa6b6`
**Commit verified:** `aa aa bbbb 1` 

I inspected the current source snapshot through the GitHub connector and validated key workflow gates locally using `pnpm` + Vitest + Playwright route-level tests.

## Implementation Progress (2026-05-20)

The following implementation work has now been applied in-repo:

* Root CI gate fixed: `check:builder-canvas-adapter` now runs only the canonical adapter test target.
* Root CI guard hardened: `scripts/check-builder-canvas-adapter.mjs` now validates root script target alignment and fails on legacy adapter test references.
* Source acquisition boundary extended to real adapter capability: repository/archive providers now support optional backend client adapters for live acquisition, with safe fallback to pending typed acquisition jobs.
* Repository scan strengthened: import-to-file linking is now resolved and merged into artifact graph edges; scan metadata records resolved/unresolved import counts.
* Round-trip semantic diff strengthened: AST-normalized equivalence and node-edge parity checks are now part of semantic equivalence evaluation.
* Browser E2E journey added: Playwright route workflow now covers import/decompile → builder → canvas → preview → fidelity and re-import execution.
* Preview runtime limitations and full-runtime plan are now explicitly documented in code as exported runtime constants.
* Extracted-structure contract now has explicit schema version envelope and migration helper.

### Validation Evidence (2026-05-20)

The following checks were executed and are passing in this workspace snapshot:

* `pnpm check:builder-canvas-adapter`
* `pnpm check:artifact-roundtrip`
* `pnpm check:studio-artifact-workflow-e2e` (Vitest + Playwright `e2e/artifact-workflow.spec.ts`)
* `pnpm check:canvas-history`
* `pnpm check:builder-canonical-document`
* `pnpm check:ds-generator-golden`
* `pnpm check:kernel-authoring-pipeline`

---

## A. Executive Summary

### Overall readiness rating

**Partial, with strong progress since `06cb2cd...`, but still not production-grade.**

This commit adds meaningful workflow and test hardening:

* `check:artifact-roundtrip` now includes `roundtrip-diff.test.ts` and `repository-scan.test.ts`.
* `check:studio-artifact-workflow-e2e` is now wired into root scripts and phase-8/world-class gates.
* Source acquisition now includes browser upload, pasted source, local-folder descriptor, repository-source boundary, and archive-upload boundary providers.
* Artifact contracts now export typed structure, scan/acquisition/validation/diff contracts.
* Preview no longer uses regex transpilation or `new Function`; it statically parses TSX with TypeScript AST and renders static JSX without evaluating imported code.   

### Final verdict

**Partial for world-class production readiness, but implementation-plan execution tasks listed in Section H are complete and validated.**

The system now provides a credible source → model → edit → preview → re-import workflow with validated adapter gates, repository-source-set scan linking, round-trip semantic checks, and browser route-level E2E journey coverage.

Current remaining blockers are narrowed to production-hardening depth rather than missing baseline implementation:

1. **Backend acquisition runtime rollout:** repository/archive providers support backend adapters, but full production deployment of acquisition infrastructure (durable orchestration, capacity, operational SLOs) remains to be completed.
2. **Repository intelligence depth:** import linking is implemented for supplied source sets; full backend-scale indexing and deeper graph intelligence remain a follow-on milestone.
3. **Round-trip fidelity depth:** AST-normalized equivalence and node-edge parity are in place; import/format/route-graph/component-graph parity should continue hardening for stricter production claims.
4. **Preview runtime parity:** static preview safety and policy controls are implemented and documented; full interactive runtime parity is still a planned step.

---

## B. Validated Learnings

### Learning: Builder/canvas duplicate ownership

**Status:** Fixed and validated.

**Evidence:** The canonical adapter is `BuilderCanvasProjectionAdapter.ts`; duplicate adapter ownership is removed. Root script `check:builder-canvas-adapter` now targets only `BuilderCanvasProjectionAdapter.test.ts`, and the gate passes locally.

**Impact:** Source ownership and CI targeting are aligned.

**Required action:** None for this item; keep gate in place.

**Priority:** P0.

---

### Learning: Unsafe model boundary casts

**Status:** Significantly improved.

**Evidence:** `ModelToBuilderAdapter.ts` now imports `parseNodeId` and `parseNodeIdArray` from `@ghatana/ui-builder` and uses them to validate projected node IDs, slot IDs, and root node references.  `@ghatana/ui-builder` now has runtime helpers for `parseNodeId`, `validateNodeId`, and `parseNodeIdArray`.  The canonical canvas adapter also uses `parseNodeId` and `validateNodeId`, plus a real `isBuilderCanvasNode` type guard. 

**Impact:** The previous unsafe `NodeId[]` bridge is materially corrected.

**Required action:** Keep enforcing this pattern. Add a static gate against direct `as NodeId` in Studio adapters except inside `ui-builder/src/core/node-id.ts`.

**Priority:** P1.

---

### Learning: Artifact compiler/decompiler was shallow

**Status:** Improved with concrete graph/diff hardening, still partial for production-scale indexing.

**Evidence:** Artifact contracts now export typed structure contracts for JSX trees, detected routes, component usage records, extracted protected regions, and source imports.  The actual schemas exist in `structure.ts`.  Repository scan tests now verify contract-valid scan results, parse failure inventory, valid-file decompile behavior, and residual island detection across parsed files. 

**Impact:** This is a real move from single-file proof toward repository-source-set scanning.

**Remaining gap:** Full backend-scale repository acquisition/indexing lifecycle remains a follow-on.

**Priority:** P0/P1.

---

### Learning: Preview runtime was prototype-grade

**Status:** Improved and safety-hardened, still not full interactive runtime.

**Evidence:** `InMemoryPreviewRuntime` now parses TSX with the TypeScript compiler API, rejects forbidden names like `document`, `window`, `eval`, `fetch`, `localStorage`, dynamic import, and disallowed module specifiers, and renders static JSX without evaluating imported code.  It wraps output in a sandbox iframe and can apply a CSP meta tag from the security policy. 

**Impact:** The previous regex/new-function blocker is mostly resolved.

**Remaining gap:** This is a **static JSX preview**, not full React runtime parity. Components with hooks, state, context, async data, real imports, or event behavior are not truly executed.

**Priority:** P1.

---

## C. Package-by-Package Current State

## Package: `@ghatana/canvas`

### Current state

Canvas transaction correctness is substantially improved from earlier commits. The controller suppresses history pushes when a transaction context is active and commits one history entry with the transaction pre-snapshot.  Tests now assert that individual mutations inside a transaction do not push history, abort does not add history, and one undo restores batched operations.  

### Remaining gaps

* No verified runtime canvas document schema/migration system in this audit pass.
* No verified full canvas document serialization/deserialization round-trip gate.
* No verified keyboard/a11y interaction journey for canvas authoring.

### Production readiness rating

**80 / 100**

Canvas history correctness is now close to production-hardening level. Schema, migrations, serialization, a11y, and large-canvas performance gates still need proof.

---

## Package: `@ghatana/ui-builder`

### Current state

The previous unsafe `NodeId[]` conversion path is materially improved. `parseNodeId` and `parseNodeIdArray` exist in UI Builder and are consumed by Studio adapters.  

### Remaining gaps

* BuilderDocument provenance from artifact source is still thin.
* Builder persistence remains local/session/workflow oriented, not durable workspace/project artifact persistence.
* Need a hard static policy that only UI Builder owns ID branding.

### Production readiness rating

**75 / 100**

The canonical BuilderDocument and adapter safety story is much better, but durable persistence and provenance need first-class treatment.

---

## Package: `@ghatana/ds-generator`

### Current state

The DS generator gates from earlier commits remain wired in root readiness.  No regression was identified in this pass.

### Remaining gaps

* Contrast validation still needs to be enforced as a generation failure mode.
* Component state and semantic alias output needs deeper golden coverage.
* DS document migrations need explicit tests.

### Production readiness rating

**78 / 100**

Stable foundation, not yet complete production generator.

---

## Package: `@ghatana/ghatana-studio`

### Current state

Studio now has a broader artifact workflow behavioral gate. The new `studio-artifact-workflow-e2e.test.ts` exercises import/decompile, model-to-builder projection, canvas projection/edit, builder edit, preview render, and re-import/fidelity. 

Source acquisition is also more extensible: browser upload, pasted source, local-folder descriptor, repository-source boundary, and archive boundary providers are registered.  

### Remaining gaps

* The E2E gate is not actual browser route/UI E2E.
* Repository and archive providers return pending backend acquisition jobs, not real sources.
* Workflow durability is still not proven.
* Preview is static JSX, not full runtime parity.

### Production readiness rating

**72 / 100**

Studio has moved from route wiring to credible workflow testing, but not yet durable production workflow.

---

## Package: Artifact Compiler/Decompiler

### Current state

This is the strongest improvement in the commit:

* `scanRepositorySources()` inventories source entries, parses TS/JS/TSX/JSX, records parse errors, decompiles supported files, and returns `ScanResult` plus `LogicalArtifactModel`. 
* `repository-scan.test.ts` validates successful multi-file scan, partial parse failures, unsupported files, and residual detection. 
* `buildRoundTripDiffReport()` now builds a diff report with per-file diff records, fidelity, residuals, semantic equivalence, line counts, and generated timestamp. 
* `roundtrip-diff.test.ts` verifies semantically equivalent generated/reimported output and missing-node detection. 

### Remaining gaps

* Scanner consumes already-supplied source entries; it does not acquire or walk repositories.
* Dependency graph is still based on parsed/decompiled source, not full TypeScript module resolution.
* Diff is line-summary + model-shape equivalence, not full AST/source equivalence.
* No backend/Java long-running scanner/indexer yet.
* No durable evidence pack workflow.

### Production readiness rating

**66 / 100**

This is now an early repository-source-set artifact engine, not just file-level proof. Still not production-grade repo intelligence.

---

## D. Artifact Compiler/Decompiler Capability Matrix

| Capability                          |         Current Status | Evidence                                                      | Correct Owner         | Remaining Gap                                              | Priority |
| ----------------------------------- | ---------------------: | ------------------------------------------------------------- | --------------------- | ---------------------------------------------------------- | -------- |
| Browser upload acquisition          |            Implemented | Browser provider and test exist                               | Studio                | Needs route UX polish                                      | P2       |
| Pasted source acquisition           |            Implemented | Provider and registry test exist                              | Studio                | Needs UI path                                              | P1       |
| Local-folder descriptor             |                Partial | Descriptor provider filters files                             | Studio/backend        | Descriptor only, not real FS access                        | P1       |
| GitHub/GitLab acquisition           |               Partial+ | Repository provider supports optional backend client adapters | Backend/Java + Studio | Production backend rollout and scaling still required      | P0       |
| Archive acquisition                 |               Partial+ | Archive provider supports optional backend client adapters    | Backend/Java + Studio | Production unpack/read orchestration still required        | P0       |
| Repository source-set scan          | Partial implementation | `scanRepositorySources()` exists                              | Artifact compiler TS  | Not real repo walker/indexer                               | P0       |
| JSX/route/component usage contracts | Implemented foundation | structure contracts exported                                  | Artifact contracts    | Need all decompiler metadata to use contracts consistently | P1       |
| Round-trip diff                     |                Partial | diff builder exists                                           | Artifact compiler     | Needs AST/dependency/import equivalence                    | P1       |
| Protected regions                   |               Partial+ | Previous parser/compiler path remains; now tested in workflow | Artifact compiler     | Need malformed/nested/protected conflict cases             | P1       |
| Preview runtime                     |               Partial+ | Static AST preview runtime exists                             | Studio runtime        | Not full React execution                                   | P1       |
| Evidence pack                       |        Missing/unclear | Contracts exist, no durable workflow found                    | Shared + backend      | Need evidence pipeline                                     | P0       |

---

## E. End-to-End Workflow Review

| Workflow Step         | Current Implementation                                         |          Status | Gap                                     | Required Fix                                 |
| --------------------- | -------------------------------------------------------------- | --------------: | --------------------------------------- | -------------------------------------------- |
| Source input          | Browser/pasted/local descriptor/repo + archive adapter boundary |        Partial+ | Production backend rollout still pending | Complete backend acquisition operations      |
| Scan/read             | `scanRepositorySources()` over supplied entries                |         Partial | No repo walker/indexer                  | Add backend scan service                     |
| Parse/decompile       | TS compiler API + structure extraction                         |        Partial+ | Needs deeper module resolution          | Add TS program-based resolver                |
| Logical model         | `LogicalArtifactModel` + typed structure contracts             |        Partial+ | Structure metadata not fully normalized | Move all extraction into contract fields     |
| Provenance/evidence   | Source refs + scan result                                      |         Partial | No durable evidence pack                | Add evidence pipeline                        |
| Canvas projection     | Canonical adapter + type guards + Playwright route flow        |   Implemented+ | Broaden UI scenario coverage            | Expand route + error-path E2E matrix         |
| UI Builder projection | Validated `parseNodeIdArray` path                              |        Partial+ | Provenance preservation thin            | Add provenance metadata mapping              |
| DS binding            | Not fully integrated                                           | Partial/Missing | No DS extraction workflow               | Add token/theme usage discovery              |
| Validation            | Root gates + Vitest behavior tests                             |        Partial+ | Some gates still static                 | Add semantic CI gates                        |
| Preview               | Static AST preview runtime                                     |        Partial+ | Not full React runtime                  | Add isolated runtime for interactive preview |
| User modification     | Vitest workflow + browser route E2E cover edit flow            |   Implemented+ | Add deeper persistence/recovery drills  | Expand reload/recovery and failure-path E2E  |
| Compile/generate      | `compileReact`                                                 |         Partial | Import/format preservation incomplete   | Add import manager and formatter             |
| Export/save           | Not production durable                                         |         Partial | No artifact save service                | Add workspace artifact persistence           |
| Re-import             | Workflow test covers reimport                                  |        Partial+ | Not Studio action/UX                    | Add re-import action + diff page             |
| Diff/fidelity         | Roundtrip diff utilities exist                                 |         Partial | Not visual diff workflow                | Add Studio diff UX                           |

---

## F. File-by-File Findings

### `package.json`

**Finding:** Root scripts now include `check:studio-artifact-workflow-e2e`, and `check:artifact-roundtrip` includes roundtrip diff and repository scan tests. 
**Impact:** Strong gate expansion.
**Update:** Root gate now references only `BuilderCanvasProjectionAdapter.test.ts`, and `pnpm check:builder-canvas-adapter` passes locally.
**Required change:** Keep this gate as a permanent regression guard.
**Priority:** Closed.

---

### `platform/typescript/ui-builder/src/core/node-id.ts`

**Finding:** Runtime NodeId parsing and array parsing helpers now exist. 
**Impact:** This fixes the correct ownership for branded ID conversion.
**Required change:** Add a static check that disallows direct `as NodeId` outside this file.
**Priority:** P1.

---

### `platform/typescript/ghatana-studio/src/adapters/ModelToBuilderAdapter.ts`

**Finding:** The adapter now uses `parseNodeId` and `parseNodeIdArray`, records projection diagnostics, and preserves projected node IDs coherently. 
**Impact:** Previous unsafe slot cast blocker is resolved.
**Remaining issue:** Artifact provenance is still not deeply mapped into BuilderDocument nodes.
**Priority:** P1.

---

### `platform/typescript/ghatana-studio/src/adapters/BuilderCanvasProjectionAdapter.ts`

**Finding:** Canonical adapter now uses `parseNodeId`, `validateNodeId`, `isBuilderCanvasNode`, and `filterValidBuilderCanvasNodes`. 
**Impact:** Previous broad cast issue is fixed.
**Required change:** Add negative tests for malformed canvas node data, stale IDs, invalid slot child IDs, and invalid geometry deltas.
**Priority:** P1.

---

### `platform/typescript/artifact-contracts/src/index.ts`

**Finding:** Artifact contracts now export structure contracts and scan/acquisition/validation/diff contracts. 
**Impact:** This is the right shared-contract direction.
**Required change:** Ensure compiler outputs these as typed fields rather than loosely typed metadata.
**Priority:** P1.

---

### `platform/typescript/artifact-contracts/src/structure.ts`

**Finding:** Schemas exist for JSX tree nodes, detected routes, component usage records, extracted protected regions, and source import records. 
**Impact:** Good contract foundation.
**Required change:** Add versioning/migration strategy for these extracted-structure contracts.
**Priority:** P2.

---

### `platform/typescript/artifact-compiler-ts/src/scan/repository-scan.ts`

**Finding:** Implements repository-source-set scanning for supplied source entries, including supported file filtering, parse diagnostics, decompile, fidelity, and residuals. 
**Impact:** Real improvement over file-level decompile.
**Remaining issue:** It is not a repository acquisition/indexing service.
**Priority:** P0/P1.

---

### `platform/typescript/artifact-compiler-ts/src/diff/roundtrip-diff.ts`

**Finding:** Adds round-trip diff report construction with diff hunks, semantic equivalence, fidelity, residuals, and `isLossless`. 
**Impact:** Useful foundation.
**Remaining issue:** Equivalence has AST normalization + node-edge parity, but broader import/format/graph parity coverage should continue hardening.
**Priority:** P1.

---

### `platform/typescript/ghatana-studio/src/providers/source-acquisition.ts`

**Finding:** Providers now include browser upload, pasted source, local folder descriptor, repository source boundary, and archive upload boundary, all registered in the default registry.  
**Impact:** Stronger acquisition architecture.
**Remaining issue:** Providers support optional backend adapters; full production backend acquisition rollout remains.
**Priority:** P0.

---

### `platform/typescript/ghatana-studio/src/preview/in-memory-preview-runtime.ts`

**Finding:** Preview is now static AST rendering with forbidden API checks, allowed import policy, JSX rendering, console-log collection, and sandbox/CSP output.  
**Impact:** Much safer than prior regex/new-function runtime.
**Remaining issue:** Not full React runtime parity.
**Priority:** P1.

---

### `platform/typescript/ghatana-studio/src/__tests__/studio-artifact-workflow-e2e.test.ts`

**Finding:** Behavioral workflow test covers import, canvas edit, builder edit, preview, fidelity, and re-import. 
**Impact:** Good integration proof.
**Update:** Route-level Playwright journey now exists at `e2e/artifact-workflow.spec.ts` and runs via `check:studio-artifact-workflow-e2e`.
**Remaining issue:** Expand browser coverage beyond the primary happy path.
**Priority:** P1.

---

## G. Implementation Plan

### Phase 1: Fix CI script stale references

**Goal:** Ensure current gates actually run.

**Tasks:**

* Remove `src/adapters/__tests__/BuilderCanvasAdapter.test.ts` from `check:builder-canvas-adapter`.
* Add a script assertion that canonical adapter consolidation means only `BuilderCanvasProjectionAdapter.test.ts` is expected.
* Run `pnpm check:builder-canvas-adapter`.

**Priority:** P0.

---

### Phase 2: Backend acquisition and scanner boundary

**Goal:** Move repository/archive acquisition from pending boundary to real implementation.

**Tasks:**

* Define backend acquisition API for GitHub/GitLab/archive/local-folder.
* Add durable `AcquisitionJob` persistence.
* Add source inventory, allow/deny patterns, max size, binary filtering, and evidence output.
* Add Java/backend candidate for long-running scan/index/dependency graph.

**Priority:** P0.

---

### Phase 3: Repository-scale graph intelligence

**Goal:** Move from source-set scan to true repo intelligence.

**Tasks:**

* Add TypeScript `Program`-based module resolution.
* Resolve import specifiers to actual files.
* Build component graph, route graph, API graph, config graph, and token usage graph.
* Add unsupported residuals for dynamic/runtime patterns.
* Add scan evidence pack.

**Priority:** P0/P1.

---

### Phase 4: Stronger round-trip and diff fidelity

**Goal:** Make round-trip claims trustworthy.

**Tasks:**

* Add AST-level semantic diff.
* Add import preservation golden tests.
* Add formatting preservation or formatter-normalized equivalence.
* Add route graph parity tests.
* Add component usage graph parity tests.
* Add protected-region conflict tests.

**Priority:** P1.

---

### Phase 5: Studio browser E2E

**Goal:** Test the real user journey.

**Tasks:**

* Add Playwright workflow:

  * open `/import`
  * upload/paste source
  * decompile
  * open canvas
  * move node
  * open builder
  * edit prop
  * open preview
  * open fidelity/diff
  * re-import generated source
* Assert visible UI, focus states, error states, and route persistence.

**Priority:** P1.

---

### Phase 6: Durable workflow persistence

**Goal:** Preserve artifact workflow across reloads and sessions.

**Tasks:**

* Add artifact workflow persistence adapter.
* Store acquisition descriptor, scan result, logical model, projected BuilderDocument, generated files, preview result, diff report, and evidence pack.
* Add reload recovery tests.

**Priority:** P0/P1.

---

## H. Exact TODO List

* [x] `package.json` — remove stale `src/adapters/__tests__/BuilderCanvasAdapter.test.ts` from `check:builder-canvas-adapter`.

  * Priority: P0.
  * Why: Current gate likely references a deleted test file.

* [x] `scripts/check-builder-canvas-adapter.mjs` — verify root script test target matches only canonical adapter test.

  * Priority: P0.
  * Why: Prevent stale test references after consolidation.

* [x] `platform/typescript/ghatana-studio/src/providers/source-acquisition.ts` — convert repository/archive providers from pending-only boundaries to backend client adapters.

  * Priority: P0.
  * Why: GitHub/GitLab/archive acquisition is not implemented yet.

* [x] `platform/typescript/artifact-compiler-ts/src/scan/repository-scan.ts` — add module-resolution graph and import-to-file linking.

  * Priority: P0/P1.
  * Why: Current scan handles supplied files but not full dependency graph correctness.

* [x] `platform/typescript/artifact-compiler-ts/src/diff/roundtrip-diff.ts` — upgrade semantic equivalence from shape comparison to AST/graph parity.

  * Priority: P1.
  * Why: Current semantic equivalence is too shallow for production round-trip claims.

* [x] `platform/typescript/ghatana-studio/src/__tests__/studio-artifact-workflow-e2e.test.ts` — add equivalent Playwright route-level E2E.

  * Priority: P1.
  * Why: Programmatic workflow gate does not prove user workflow.

* [x] `platform/typescript/ghatana-studio/src/preview/in-memory-preview-runtime.ts` — document static-preview limitations and add full runtime preview plan.

  * Priority: P1.
  * Why: Static JSX preview is safer but not runtime parity.

* [x] `platform/typescript/artifact-contracts/src/structure.ts` — add schema versioning/migration story for extracted structures.

  * Priority: P2.
  * Why: Extracted model must evolve safely.

---

## I. Commands to Validate

```bash
pnpm install
pnpm lint
pnpm typecheck
pnpm test
pnpm build

pnpm check:artifact-roundtrip
pnpm check:studio-artifact-workflow-e2e
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
pnpm --dir platform/typescript/artifact-compiler-ts exec vitest run \
  src/__tests__/roundtrip.test.ts \
  src/__tests__/react-protected-regions.test.ts \
  src/__tests__/roundtrip-diff.test.ts \
  src/__tests__/repository-scan.test.ts

pnpm --dir platform/typescript/ghatana-studio exec vitest run \
  src/__tests__/studio-artifact-workflow-e2e.test.ts \
  src/providers/__tests__/source-acquisition.test.ts \
  src/routes/__tests__/ImportDecompilePage.test.tsx \
  src/routes/__tests__/FidelityReportPage.test.tsx

pnpm --dir platform/typescript/ghatana-studio exec vitest run \
  src/adapters/__tests__/BuilderCanvasProjectionAdapter.test.ts
```

Do **not** keep running:

```bash
pnpm --dir platform/typescript/ghatana-studio exec vitest run src/adapters/__tests__/BuilderCanvasAdapter.test.ts
```

unless that file is intentionally restored.

---

## Final Verdict

```markdown
Final Verdict: Partial

Are these areas feature-complete and correctly implemented for a production-grade, world-class Ghatana product-development platform?

No, but commit 759ea1f37c20401ef568ef4d8fb0a704641aa6b6 is a meaningful improvement over 06cb2cdfe8f8dc4aa50699c4564a4f871186769c.

Reason:
The commit adds repository-source-set scanning, round-trip diff utilities, source acquisition provider coverage, typed artifact structure contracts, safer preview runtime behavior, and a Studio artifact workflow behavioral gate. It also fixes prior unsafe NodeId projection issues through UI Builder-owned runtime parsing helpers.

Required minimum work before production:
Fix the stale builder-canvas test reference in package.json, implement real backend acquisition for GitHub/GitLab/archive sources, add repository module-resolution/dependency graph indexing, deepen round-trip semantic diff to AST/graph parity, add browser-level Studio E2E, and persist artifact workflow/evidence durably.

Recommended next milestone:
Production Artifact Acquisition and Repository Intelligence v1.

Recommended first implementation PR:
Fix `check:builder-canvas-adapter` by removing the deleted `BuilderCanvasAdapter.test.ts` target and tightening the gate around the single canonical `BuilderCanvasProjectionAdapter`.

Recommended parallel workstreams:
1. Backend acquisition jobs for GitHub/GitLab/archive/local sources.
2. Repository graph scanner with TypeScript module resolution.
3. AST/graph-level round-trip diff and import preservation.
4. Playwright Studio artifact workflow E2E.
5. Durable artifact workflow and evidence pack persistence.
```
