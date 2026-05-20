# Production-Grade Feature Completeness Audit Report

## Ghatana Studio, Canvas, UI Builder, DS Generator, and Artifact Compiler/Decompiler

Target commit: `1d010f24f02ec0d7cd57ef945b32fe407aa10e7c`

Audit basis: I inspected the current GitHub snapshot at the target commit through package manifests, public barrels, core source files, Studio routes/components, kernel artifact contracts, and representative test/file discovery. I did **not** run the repo locally, so command success/failure is not claimed.

---

## A. Executive Summary

### Overall readiness rating

| Area                         |   Rating | Verdict                                                                                                                                                     |
| ---------------------------- | -------: | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `@ghatana/canvas`            |   5 / 10 | Broad foundation, but public surface, schema/runtime contract, composite history, and multi-canvas isolation need hardening.                                |
| `@ghatana/ui-builder`        | 5.5 / 10 | Significantly improved canonical model direction, but compatibility bridges, casts, import/codegen gaps, and Studio round-trip integration remain blockers. |
| `@ghatana/ds-generator`      |   3 / 10 | Deterministic preset/brand utility, not yet a full design-system generator.                                                                                 |
| `@ghatana/ghatana-studio`    |   4 / 10 | Shell and routes exist, but real authoring workflows are incomplete or disconnected.                                                                        |
| Artifact compiler/decompiler |   2 / 10 | Artifact lifecycle contracts exist; production compiler/decompiler pipeline is not first-class yet.                                                         |

### Final verdict

**No — partial foundations exist, but these areas are not yet feature-complete or production-grade for a world-class Ghatana product-development platform.**

### Top production blockers

1. **Artifact compiler/decompiler is not a first-class implemented capability.** I found `@ghatana/kernel-artifacts` for lifecycle artifact manifests, fingerprints, registry/resolver/validator/storage, but not a full scan → parse/decompile → logical model → edit → compile → re-import → fidelity pipeline. The stale `platform/comp-decomp-todo.md` is explicitly from an older commit and should not be treated as target-snapshot truth.   
2. **Studio does not yet provide one coherent end-to-end workflow.** `App.tsx` wires product-shell routes for ideas, blueprints, canvas, develop, lifecycle, agents, artifacts, deployments, health, learn, and settings, but the Builder Studio workflow is not part of the main route tree inspected, and `/canvas` is an artifact-intelligence visualization, not a full authoring workspace.  
3. **Canvas and UI Builder have improved, but still rely on compatibility bridges and unsafe casts.** UI Builder now declares a canonical schema-backed `BuilderDocument`, but compatibility adapters still support legacy `id`, `version`, `name`, `rootNodes`, Map-to-Record behavior, and some casts remain in operations/import/Studio projection.    
4. **DS Generator is a preset/brand materializer, not a full DS generator.** The manifest itself describes preset materialization and brand customization, and exports only core preset/brand/extension surfaces.  
5. **Real round-trip evidence is incomplete.** UI Builder has AST-based TSX import and React codegen with fidelity/loss-point reporting, which is good, but this is still component-level import/codegen rather than repository-level artifact compiler/decompiler with source provenance, residual islands, durable evidence packs, and Studio review queues.  

### Recommended next milestone

**Milestone 1: Canonical Artifact + Builder + Canvas Contracts.**
Before adding more UI, define and enforce canonical contracts for:

```text
ArtifactSource
ArtifactGraph
LogicalArtifactModel
BuilderDocument
CanvasDocument
DesignSystemDocument
CompileResult
DecompileResult
FidelityReport
ResidualIslandReport
EvidencePack
```

Then wire one thin Studio workflow through those contracts.

### Recommended first implementation PR

**PR-1: “Canonical artifact contracts and BuilderDocument boundary cleanup.”**

Scope:

1. Add shared artifact compiler/decompiler contract package.
2. Make UI Builder canonical document usage strict.
3. Remove or quarantine legacy compatibility APIs behind explicit adapters.
4. Add architecture tests preventing unsafe `as unknown as` casts across package boundaries.
5. Add one minimal source → model → generated source → re-import fidelity test.

---

## B. Validated Learnings

### Learning: UI Builder had incompatible BuilderDocument models

**Status: Partially fixed**

**Verified current state:**
`builder-document.ts` now defines `CURRENT_SCHEMA_VERSION`, a Zod-backed `BuilderDocumentSchema`, and a canonical `BuilderDocument` type with `schemaVersion`, `documentId`, `owner`, `root`, `nodes` as a record, `bindings`, `layout`, and metadata.  

**Remaining risk:**
The same file still includes compatibility support for legacy-style `id`, `version`, `name`, `rootNodes`, and Map-like node accessors. `types.ts` explicitly says the old `BuilderDocument` location is deprecated, but compatibility remains.  

**Impact:**
Better than previous state, but still risky for production because consumers can continue to depend on compatibility behavior rather than the canonical schema.

**Required action:**
Keep only the schema-backed model as public canonical. Move legacy compatibility into `@ghatana/ui-builder/legacy-adapters` or internal migration utilities.

**Priority:** P0 Blocker

---

### Learning: Canvas had broad public API surface

**Status: Still true**

**Verified current state:**
`@ghatana/canvas` exposes many subpaths from `package.json`, including `./react`, `./types`, `./plugins`, `./hybrid`, `./tools`, `./state`, `./chrome`, `./ai`, `./export`, `./collaboration`, `./telemetry`, `./testing`, `./flow`, and `./public`. 

The public barrel exports plugin registries, hybrid controller/state/hooks, AI, telemetry, domain injection, export, collaboration, React overlays, tools, chrome/panels, theme, accessibility, core systems, performance helpers, elements, semantic zoom, and diagram primitives.  

**Impact:**
A broad public API makes production stabilization harder and increases backward-compatibility burden before the core document/runtime contracts are stable.

**Required action:**
Split public API into stable, experimental, internal, and deprecated surfaces. Export only stable APIs from `"."`.

**Priority:** P1 Required

---

### Learning: Deprecated UI Builder compatibility surfaces may still be exposed from Canvas

**Status: Mostly fixed at package/public-barrel level**

**Verified current state:**
The Canvas public barrel explicitly states that Canvas does not own builder-domain abstractions and that the deprecated UI Builder export was intentionally removed. The current `package.json` exports shown do not include `./ui-builder`.  

**Remaining risk:**
The package still has a very broad public API, and internal remnants were not exhaustively inspected file-by-file.

**Required action:**
Add a governance check that rejects imports from deprecated Canvas builder compatibility paths and validates package exports against an allowlist.

**Priority:** P1 Required

---

### Learning: Hybrid canvas controller may rely on singleton/global store state

**Status: Partially fixed**

**Verified current state:**
`HybridCanvasController` now accepts an injected Jotai store and defaults to `createStore()`, which supports instance-scoped controllers. The deprecated `getHybridCanvasController()` now returns a new controller rather than a singleton.  

**Remaining risk:**
`hybridCanvasStateAtom`, `historyAtom`, and `hybridCanvasStore` remain exported as global atoms/store facade, and public hooks read those global atoms.  

**Impact:**
Consumers using public hooks/store directly can still create cross-canvas bleed unless isolated by a Jotai provider/store discipline.

**Required action:**
Introduce `createHybridCanvasStore()` and `HybridCanvasProvider`; mark global `hybridCanvasStore` as legacy/testing-only.

**Priority:** P1 Required

---

### Learning: Canvas undo/redo may capture state after mutation

**Status: Partially fixed, still risky for composite operations**

**Verified current state:**
The history atom requires pre-mutation snapshots. The controller now captures `elements`, `nodes`, and `edges` before individual add/update/delete operations and passes those snapshots to history.  

**Remaining risk:**
Composite operations such as `duplicateSelected`, `groupSelected`, and `ungroupSelected` call multiple mutating operations, producing multiple history entries or pushing a snapshot after several mutations. `ungroupSelected()` specifically updates children, deletes group elements, then captures state and pushes an “Ungroup” history entry. 

**Impact:**
Single-step undo/redo will not reliably match user intent for group/ungroup/duplicate flows.

**Required action:**
Replace ad hoc snapshot pushes with a command model: `execute(command)`, `undo(command)`, `redo(command)`, transaction boundaries, and composite command grouping.

**Priority:** P0 Blocker

---

### Learning: Canvas document model may lack runtime schema, migrations, and round-trip guarantees

**Status: Still true based on inspected files**

**Verified current state:**
The hybrid Canvas model is TypeScript-interface based (`HybridCanvasState`, `CanvasElement`, `CanvasNode`, `CanvasEdge`) with no runtime schema/migration contract evidenced in the inspected model/state files. 

**Impact:**
No production-grade persisted canvas document guarantee, no versioned migrations, no schema validation, and no round-trip serialization acceptance gate.

**Required action:**
Add `CanvasDocumentSchema`, `CanvasDocumentV1`, migration registry, serializer/deserializer, and golden round-trip tests.

**Priority:** P0 Blocker

---

### Learning: DS Generator may be closer to preset/brand utility than full generator

**Status: Still true**

**Verified current state:**
The package description is “Design system preset materialization and brand customization generator.” The source exports presets, brand config/application, and generator extension manifest helpers.  

**Impact:**
It is useful, but not yet a full design-system generator capable of semantic token graphs, component state generation, Tailwind/React outputs, docs, golden files, and accessibility validation.

**Required action:**
Promote it from preset utility to a pipeline-based generator with explicit targets and tests.

**Priority:** P1 Required

---

### Learning: Studio shell exists but routes may not wire real package capabilities

**Status: Still true**

**Verified current state:**
`App.tsx` wires Studio shell routes. `/canvas` renders a static artifact graph canvas derived from `semanticArtifactReferences` and uses no-op change handlers. `BuilderStudio.tsx` exists as a richer localStorage-based builder section, but it is not imported in the inspected `App.tsx` route tree.   

**Impact:**
The Studio shell is not yet the real end-to-end authoring/product-development workflow.

**Required action:**
Create routed workflows for Canvas Authoring, Builder Studio, DS Generation, Artifact Import/Decompile, Preview, Export, and Fidelity Review.

**Priority:** P0 Blocker

---

### Learning: Artifact compiler/decompiler is crucial and currently immature

**Status: Still true**

**Verified current state:**
The current artifact package is lifecycle-manifest oriented: artifact types, packaging, fingerprints, trust state, signatures, SBOM refs, attestations, retention, source/provenance refs, and manifest generation/validation. 

**Gap:**
I did not find a first-class compiler/decompiler package implementing repository scanning, AST parsing across codebases, logical model creation, protected regions, residual island tracking, fidelity scoring, compile/decompile workers, or Studio review queues. Search surfaced `platform/comp-decomp-todo.md`, but that file states it was executed against a different older commit and should not be used as current-state truth.  

**Required action:**
Create artifact compiler/decompiler as a first-class platform area with contracts, TS adapters, backend/Java worker services, and Studio UX.

**Priority:** P0 Blocker

---

## C. Package-by-Package Current State

## Package: `@ghatana/canvas`

### Intended Responsibility

Reusable platform canvas engine for visual authoring, artifact graph display, nodes/edges/elements, selection, grouping, history, viewport, plugins, accessibility, export, telemetry, collaboration readiness, and large-canvas performance.

### Actual Current Responsibility

The package is a broad platform canvas package with hybrid renderer, ReactFlow integration, plugin systems, AI integration, telemetry, export, collaboration, React overlays, tools, chrome/panels, accessibility, performance, element primitives, diagram primitives, and semantic zoom.  

### What Exists

* Hybrid state and controller.
* Viewport, pan, zoom, fit-to-content, center-on.
* Selection and select-all.
* Freeform elements.
* Graph nodes and edges.
* Grid config.
* Undo/redo atoms.
* Group/ungroup/duplicate utilities.
* Plugin and registry exports.
* Collaboration, telemetry, export, AI, accessibility, panels, and diagram exports.

### What Is Correct

* Controller dependency injection is improved: store, clock, and ID provider can be injected. 
* Individual add/update/delete operations capture pre-mutation snapshots. 
* Deprecated singleton controller was replaced with a new-instance helper. 

### What Is Incomplete

* No runtime-validatable `CanvasDocument` schema evidenced.
* No versioned migrations evidenced.
* No round-trip serialization contract evidenced.
* Public API is too broad for production stability.
* Composite history is not command-based.
* Multi-canvas isolation is still fragile via exported global atoms/store.

### What Is Incorrect

* Default ID provider uses `Date.now()` and `Math.random()` with an inline note that it should be replaced by a proper UUID generator in production. 
* Composite operations produce non-atomic undo/redo semantics. 

### Public API Review

Public API should be reduced and tiered:

```text
stable:
  CanvasDocument
  CanvasProvider
  CanvasController
  commands
  serializer/migrations
  viewport/selection APIs

experimental:
  AI integration
  collaboration
  semantic zoom
  panels/chrome

internal:
  atoms
  low-level registries
  implementation utilities
```

### Production Readiness Rating

**5 / 10**

### Required Fixes

1. Add schema-backed `CanvasDocument`.
2. Add command-based history.
3. Add `CanvasProvider` and store factory.
4. Move global atoms/store to internal or testing.
5. Reduce public exports.
6. Add golden serialization and multi-canvas isolation tests.

---

## Package: `@ghatana/ui-builder`

### Intended Responsibility

Canonical UI builder model, component registry/contracts, layout, bindings, actions, validation, import/export, preview, codegen, scene projection, and round-trip fidelity.

### Actual Current Responsibility

The package owns document model, bindings, actions, validation, code generation, React/web/preview/testing exports, and JSON schema export. 

### What Exists

* Canonical schema-backed `BuilderDocument`.
* Compatibility adapters for legacy document access.
* Operations for insert, move, delete, update props, bindings, reorder, resize, reposition, responsive variants, actions, batch updates, undo stack.
* AST-based TSX import using TypeScript compiler API.
* HTML import for `<ghatana-*>` custom elements.
* JSON import.
* React codegen with ownership markers and round-trip loss points.
* Preview host protocol with acknowledgement-based mount/update/teardown.

### What Is Correct

* `BuilderDocumentSchema` provides a real runtime schema foundation. 
* `types.ts` explicitly directs consumers to import canonical `BuilderDocument` from `builder-document.ts`. 
* TSX import uses the TypeScript compiler API instead of regex for JSX parsing. 
* Preview protocol now waits for matching acknowledgements for mount/update/teardown. 
* Codegen computes loss points and confidence rather than silently claiming perfect round-trip. 

### What Is Incomplete

* JSON import still checks for legacy `rootNodes` even though canonical structure is layout-based. 
* HTML import remains best-effort and regex-based for custom tags. 
* React codegen does not fully encode bindings, responsive variants, state variants, custom/protected code, or unknown contracts; it records these as loss points. 
* No repository-level artifact decompiler exists here; this is UI-component import/codegen only.

### What Is Incorrect

* Unsafe casts still exist in production paths: operations and import both cast through `unknown`; Studio VisualCanvas casts selection node IDs across package boundaries.   

### Production Readiness Rating

**5.5 / 10**

### Required Fixes

1. Make canonical document strict.
2. Move legacy compatibility to explicit migration adapters.
3. Replace unsafe casts with typed adapters.
4. Fix JSON import to accept canonical schema directly.
5. Add semantic validation for graph/tree constraints, duplicate parentage, binding expressions, action payloads, and prop schemas.
6. Add round-trip tests for TSX/JSON/codegen/preview.

---

## Package: `@ghatana/ds-generator`

### Intended Responsibility

Full design-system generation pipeline: token graph, semantic tokens, aliases, component variants/states, accessibility, CSS/JSON/Tailwind/React outputs, deterministic file emission, docs/examples, migrations, and golden tests.

### Actual Current Responsibility

Preset materialization and brand customization. 

### What Exists

* Built-in presets.
* Color, typography, radius, density, elevation, shadow, motion, and z-index materialization.
* CSS custom property rendering for presets.
* Brand overrides.
* CSS safety validation for custom property names/values.
* Hex validation for brand color overrides.
* Extension manifest model.   

### What Is Correct

* Deterministic preset output.
* Basic safety validation for brand custom properties.
* Brand `basePresetId` validation against the supplied preset. 

### What Is Incomplete

* No semantic token graph.
* No alias token references.
* No contrast validation.
* No component state style generation.
* No Tailwind target.
* No React theme provider target.
* No JSON token target as a first-class output.
* No file emission pipeline.
* No golden output fixtures evidenced.
* No Storybook/docs/examples generator evidenced.

### What Is Incorrect

`renderBrandToCss()` does not emit the full token set emitted by `renderPresetToCss`; it omits several generated token categories such as shadow, motion, and z-index from the rendered brand CSS path.  

### Production Readiness Rating

**3 / 10**

### Required Fixes

1. Introduce `DesignSystemDocument`.
2. Add token graph and semantic/alias token validation.
3. Add WCAG contrast validation.
4. Add target adapters: CSS variables, JSON tokens, Tailwind, React theme, docs.
5. Add deterministic file emission.
6. Add golden tests.

---

## Package: `@ghatana/ghatana-studio`

### Intended Responsibility

Product-facing Studio experience that orchestrates package capabilities without duplicating internals.

### Actual Current Responsibility

A unified Studio shell with route gating and lifecycle-aware navigation. It depends on Canvas, UI Builder, DS Generator, kernel lifecycle/artifacts/deployment/release, product shell, design system, i18n, platform utils/events, theme, and tokens. 

### What Exists

* Product shell route structure.
* Route access guard.
* Lifecycle capability state.
* Error boundary.
* i18n usage.
* Canvas page.
* Artifacts page.
* Builder Studio component/section.
* VisualCanvas bridge component.
* LocalStorage-backed builder persistence.

### What Is Correct

* Studio shell direction is coherent.
* Route gating and ownership metadata exist.
* Builder Studio uses UI Builder public APIs for create, validate, serialize, deserialize, insert, update, and persistence. 

### What Is Incomplete

* `BuilderStudio.tsx` is not routed in inspected `App.tsx`.
* `/canvas` is static artifact graph visualization, not full authoring.
* Canvas node positions use `Math.random()` each render, so layout is nondeterministic. 
* `VisualCanvas` projects BuilderDocument → Canvas, but does not persist Canvas edits back to BuilderDocument. 
* Component palette is hardcoded to Button/Input/Card/Typography rather than loaded from the design-system/component registry. 
* Artifact page displays artifact lifecycle/intelligence metadata but is not an import/decompile/compile review workflow. 

### Production Readiness Rating

**4 / 10**

### Required Fixes

1. Add real Studio workflows:

   * import/decompile
   * artifact model review
   * canvas visualization/editing
   * visual builder editing
   * DS generation
   * validation
   * preview
   * compile/export
   * re-import/fidelity review
2. Remove random layout.
3. Replace localStorage-only persistence with workspace/project-backed persistence.
4. Route Builder Studio.
5. Add E2E workflows.

---

## D. Artifact Compiler/Decompiler Deep Review

## Artifact Compiler/Decompiler

### Current Locations

| Location                                                          | Current Role                                                                                                                                                |
| ----------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `platform/typescript/kernel-artifacts`                            | Artifact lifecycle manifest system: artifact entries, packaging, fingerprints, trust, signatures, SBOM, attestation, registry/resolver/validator/storage.   |
| `platform/typescript/ui-builder/src/core/import.ts`               | UI-level import from JSON, TSX, HTML into `BuilderDocument`; includes fidelity/loss points.                                                                 |
| `platform/typescript/ui-builder/src/core/codegen.ts`              | React code generation from `BuilderDocument`; includes ownership regions and round-trip fidelity.                                                           |
| `platform/typescript/ghatana-studio/src/routes/ArtifactsPage.tsx` | Artifact manifest/intelligence display, not decompile/compile workflow.                                                                                     |
| `platform/comp-decomp-todo.md`                                    | Stale prior audit output from a different commit; exclude as current-state truth.                                                                           |

### Current Capabilities

* Artifact lifecycle manifests.
* Fingerprint/trust/signature/SBOM/attestation/retention metadata.
* Source/provenance references in artifact manifests.
* UI Builder TSX import via TypeScript compiler API.
* UI Builder JSON/HTML import.
* UI Builder React codegen.
* Loss-point/confidence reporting for UI Builder codegen/import.

### Missing Capabilities

* GitHub/GitLab/local/archive acquisition.
* Repository scanning.
* File classification.
* Dependency graph.
* Component graph.
* Route graph.
* API graph.
* Multi-language AST parsing beyond UI-level TSX/HTML/JSON.
* Logical artifact model.
* Provenance model with source-span traceability.
* Ownership/protected region system across files.
* Residual island detection.
* Fidelity scoring at repository/workspace level.
* Compile/decompile evidence packs.
* Human review queue.
* Studio visual diff.
* Backend/Java long-running scan/index services.
* Round-trip CI gates.

### Platform vs Product Boundary

**Correct platform/shared ownership:**

* Artifact contracts.
* Source/provenance/fidelity/residual/evidence models.
* Compiler/decompiler result contracts.
* Validation result contracts.
* Adapter interfaces.

**Correct TypeScript ownership:**

* Studio UX orchestration.
* UI Builder projection.
* Canvas projection.
* DS projection.
* Browser-side lightweight artifact import.
* Preview orchestration.
* UI codegen adapters.

**Correct Java/backend ownership:**

* Large repo scanning.
* Long-running parsing/indexing.
* Multi-language dependency graphs.
* Durable evidence pipelines.
* Batch compile/decompile.
* Repository-level diffing.
* Heavy validation and test execution orchestration.

### Capability Matrix

| Capability                    | Current Location                  | Current Status  | Correct Owner                            | TS / Java / Backend / Shared Contract | Gap                                     | Priority |
| ----------------------------- | --------------------------------- | --------------- | ---------------------------------------- | ------------------------------------- | --------------------------------------- | -------- |
| Source acquisition            | Not evidenced                     | Missing         | Artifact compiler platform               | Backend + shared contract             | No repo/folder/archive acquisition      | P0       |
| Repository scanning           | Not evidenced                     | Missing         | Artifact compiler platform               | Java/backend                          | No scanner/indexer                      | P0       |
| File classification           | Not evidenced                     | Missing         | Artifact compiler platform               | Java/backend + TS view                | No classifier                           | P0       |
| Dependency graph              | Not evidenced                     | Missing         | Artifact compiler platform               | Java/backend                          | No graph builder                        | P0       |
| Component graph               | Partial in UI import              | Partial         | Artifact compiler + UI Builder adapter   | Shared + TS                           | Only JSX element extraction             | P0       |
| Route graph                   | Not evidenced                     | Missing         | Artifact compiler platform               | Java/backend                          | No route parser                         | P0       |
| TS/TSX parsing                | `ui-builder/import.ts`            | Partial         | UI Builder adapter / compiler TS adapter | TS                                    | Component-level only                    | P1       |
| HTML/CSS parsing              | `ui-builder/import.ts`            | Partial         | Compiler adapters                        | TS + backend                          | HTML regex, no CSS model                | P1       |
| Config parsing                | Not evidenced                     | Missing         | Compiler platform                        | Backend                               | No package/vite/tsconfig graph          | P1       |
| Design-token discovery        | Not evidenced                     | Missing         | DS adapter                               | TS + backend                          | No token usage scanner                  | P1       |
| Logical model creation        | Not evidenced                     | Missing         | Shared artifact contracts                | Shared                                | No canonical logical model              | P0       |
| Provenance tracking           | Artifact manifest refs only       | Partial         | Shared contracts                         | Shared                                | No source span mapping                  | P0       |
| Ownership markers             | UI codegen only                   | Partial         | Shared + UI Builder                      | Shared + TS                           | Not repo-wide                           | P0       |
| Residual island detection     | Studio metadata display only      | Partial/Missing | Compiler platform + Studio               | Shared + TS                           | No detector                             | P0       |
| Fidelity scoring              | UI import/codegen only            | Partial         | Shared + adapters                        | Shared + TS                           | Not repo-wide                           | P0       |
| Canvas projection             | Studio static projection          | Partial         | Canvas adapter                           | TS                                    | One-way/no persistence                  | P1       |
| UI-builder projection         | `VisualCanvas` one-way            | Partial         | UI Builder adapter                       | TS                                    | No bidirectional sync                   | P0       |
| DS-generator projection       | Not evidenced                     | Missing         | DS adapter                               | TS                                    | No DS model bridge                      | P1       |
| Code generation               | UI Builder React codegen          | Partial         | UI Builder/codegen adapters              | TS                                    | No repo-level compiler                  | P1       |
| Protected region preservation | UI ownership only                 | Partial         | Compiler platform                        | Shared + TS/backend                   | No file-level region preservation       | P0       |
| Formatting/import management  | Minimal in codegen                | Partial         | Compiler TS adapter                      | TS/backend                            | No formatter/import manager             | P1       |
| Re-import                     | UI Builder import                 | Partial         | Compiler + UI Builder                    | TS                                    | No source → model → source → model gate | P0       |
| Diff                          | Not evidenced                     | Missing         | Compiler platform + Studio               | Backend + TS                          | No visual/model diff                    | P1       |
| Validation                    | Artifact manifest + UI validation | Partial         | Shared + package validators              | TS/backend                            | No cross-pipeline validation            | P1       |
| Preview                       | UI Builder protocol               | Partial         | UI Builder + Studio                      | TS                                    | No full runtime parity flow             | P1       |
| Test generation               | Not evidenced                     | Missing         | Compiler platform                        | TS/backend                            | No generated tests                      | P2       |
| Evidence pack creation        | Kernel artifact refs only         | Partial         | Kernel/artifact platform                 | Backend                               | No compiler evidence pack               | P0       |

### Production-Grade Target Architecture

```text
@ghatana/artifact-contracts
  SourceRef
  SourceFile
  SourceSpan
  ArtifactGraph
  LogicalArtifactModel
  ProvenanceRecord
  OwnershipRegion
  CompileResult
  DecompileResult
  FidelityReport
  ResidualIslandReport
  ValidationResult
  EvidencePack

@ghatana/artifact-compiler-ts
  TS/TSX/JSX/HTML/CSS lightweight parsers
  UI artifact codegen
  BuilderDocument projection
  Canvas projection
  DS projection

@ghatana/artifact-compiler-service
  Java/backend worker
  repo acquisition
  large scan/index
  dependency graph
  route/API graph
  durable evidence
  batch compile/decompile
  validation orchestration

@ghatana/ui-builder
  BuilderDocument
  UI projection
  UI codegen adapters
  preview protocol

@ghatana/canvas
  CanvasDocument
  graph/scene visualization
  command-based editing

@ghatana/ds-generator
  DesignSystemDocument
  token/component output adapters

@ghatana/ghatana-studio
  import/decompile workflow
  residual review
  visual diff
  canvas/builder/DS editing
  preview
  export/re-import/fidelity report
```

---

## E. End-to-End Workflow Review

| Workflow Step                  | Current Implementation                               | Owner                       | Status  | Gap                                         | Required Fix                                                | Tests                         |
| ------------------------------ | ---------------------------------------------------- | --------------------------- | ------- | ------------------------------------------- | ----------------------------------------------------------- | ----------------------------- |
| 1. Source acquisition          | Not evidenced                                        | Missing                     | Missing | No GitHub/GitLab/folder/archive acquisition | Add `SourceAcquisitionProvider` contracts + backend workers | Acquisition provider tests    |
| 2. Scan/read                   | Not evidenced                                        | Missing                     | Missing | No repo scan/index                          | Java/backend scanner                                        | Large repo scan integration   |
| 3. Parse/decompile             | UI Builder TSX/HTML/JSON import                      | UI Builder                  | Partial | UI-level only, not repo-level               | Shared artifact decompiler adapters                         | TSX/HTML/JSON decompile tests |
| 4. Logical model               | Not evidenced                                        | Missing                     | Missing | No canonical artifact model                 | Add `LogicalArtifactModel`                                  | Schema/golden tests           |
| 5. Canvas projection           | `CanvasPage` static artifact nodes                   | Studio/Canvas               | Partial | Random layout, no persistence               | ArtifactGraph → CanvasDocument adapter                      | Projection determinism tests  |
| 6. UI-builder projection       | `VisualCanvas` one-way projection                    | Studio/UI Builder           | Partial | No bidirectional sync                       | BuilderDocument ↔ CanvasDocument adapter                    | Bidirectional sync tests      |
| 7. Design-system binding       | DS generator exists, not workflow-bound              | DS Generator/Studio         | Partial | No DS workflow                              | DS document + Studio route                                  | DS binding tests              |
| 8. Validation                  | UI Builder validation + artifact manifest validation | UI Builder/kernel-artifacts | Partial | No cross-workflow validation                | Unified validation result contract                          | Contract tests                |
| 9. Preview                     | UI Builder preview protocol                          | UI Builder                  | Partial | Protocol exists, Studio runtime not proven  | Routed preview runtime                                      | Handshake + parity tests      |
| 10. User modification          | Builder local workflow; Canvas static/no-op          | Studio                      | Partial | Canvas edits not persisted                  | Real editor state pipeline                                  | E2E authoring tests           |
| 11. Compile/generate           | UI Builder React codegen                             | UI Builder                  | Partial | Not repo-level                              | Artifact compiler TS/backend                                | Codegen golden tests          |
| 12. Export/save                | Builder JSON export; kernel artifact manifests       | Studio/kernel-artifacts     | Partial | No project persistence                      | Workspace-backed persistence                                | Save/export tests             |
| 13. Re-import                  | UI Builder import                                    | UI Builder                  | Partial | No full round-trip gate                     | Source → model → source → model tests                       | Round-trip tests              |
| 14. Diff/fidelity report       | UI Builder loss points; Studio metadata display      | UI Builder/Studio           | Partial | No visual diff/review                       | Fidelity and residual review UI                             | Fidelity regression tests     |
| 15. Regression test generation | Not evidenced                                        | Missing                     | Missing | No generated tests                          | Test generation adapter                                     | Generated test golden tests   |

---

## F. Feature Completeness Matrix

| Capability                 | Canvas          | UI Builder            | DS Generator        | Studio             | Artifact Compiler/Decompiler | Current Status  | Correct Owner              | Priority |
| -------------------------- | --------------- | --------------------- | ------------------- | ------------------ | ---------------------------- | --------------- | -------------------------- | -------- |
| Canvas document model      | Partial         | N/A                   | N/A                 | Consumes partial   | Missing for artifacts        | Partial         | Canvas                     | P0       |
| Runtime schema             | Missing         | Partial/exists        | Partial for presets | Consumes           | Missing                      | Partial         | Package owners             | P0       |
| Versioned migrations       | Missing         | Minimal               | Missing             | Missing            | Missing                      | Missing         | Package owners             | P0       |
| Undo/redo                  | Partial         | Partial undo stack    | N/A                 | Not integrated     | N/A                          | Partial         | Canvas/UI Builder          | P0       |
| Multi-canvas isolation     | Partial         | N/A                   | N/A                 | Not proven         | N/A                          | Partial         | Canvas                     | P1       |
| BuilderDocument            | N/A             | Partial canonical     | N/A                 | Consumes           | Needs adapter                | Partial         | UI Builder                 | P0       |
| Component registry         | N/A             | Partial               | N/A                 | Hardcoded palette  | Needs discovery              | Partial         | UI Builder + DS Registry   | P1       |
| Bindings/actions           | N/A             | Partial               | N/A                 | Not exposed        | Needs model mapping          | Partial         | UI Builder                 | P1       |
| Scene projection           | Partial         | Partial               | N/A                 | One-way            | Missing                      | Partial         | UI Builder/Canvas adapters | P0       |
| Codegen                    | N/A             | Partial React codegen | N/A                 | Export JSON only   | Missing repo-level           | Partial         | UI Builder + compiler      | P1       |
| DS tokens                  | N/A             | Consumes DS contracts | Partial             | Not workflow-bound | Needs token discovery        | Partial         | DS Generator               | P1       |
| Component state generation | N/A             | Models states         | Missing             | Missing            | Missing                      | Partial/Missing | DS Generator/UI Builder    | P1       |
| Accessibility validation   | Partial exports | Partial model         | Missing contrast    | Not full flow      | Missing                      | Partial         | All                        | P1       |
| Preview runtime            | N/A             | Protocol exists       | N/A                 | Not full workflow  | Missing                      | Partial         | UI Builder/Studio          | P1       |
| Source acquisition         | N/A             | N/A                   | N/A                 | Missing            | Missing                      | Missing         | Compiler backend           | P0       |
| Repository scanning        | N/A             | N/A                   | N/A                 | Missing            | Missing                      | Missing         | Java/backend               | P0       |
| Logical artifact model     | N/A             | N/A                   | N/A                 | Missing            | Missing                      | Missing         | Shared contracts           | P0       |
| Fidelity/residual reports  | N/A             | Partial               | N/A                 | Displays metadata  | Missing repo-level           | Partial         | Shared/compiler/Studio     | P0       |
| Evidence packs             | N/A             | N/A                   | N/A                 | Displays refs      | Kernel artifact refs only    | Partial         | Kernel/compiler backend    | P0       |

---

## G. File-by-File Findings

### `package.json`

Current role: Root monorepo scripts and governance gates.

Finding: Strong governance script surface exists, including production readiness, production stubs, architecture boundaries, circular deps, design-system conformance, Studio kernel API, deprecated imports/packages, and phase gates. 

Impact: Good foundation; missing compiler/decompiler-specific gate.

Required change: Add `check:artifact-compiler-contracts`, `check:artifact-roundtrip`, `check:artifact-fidelity`, and `check:studio-authoring-workflows`.

Correct owner: Root governance scripts.

Priority: P1

Tests required: Script unit tests and CI workflow inclusion.

---

### `pnpm-workspace.yaml`

Finding: Scoped packages are in workspace: `platform/typescript/*`, `platform/typescript/canvas/*`, and `platform/typescript/ghatana-studio`. 

Required change: Add first-class artifact compiler/decompiler packages once created.

Priority: P0

---

### `platform/typescript/canvas/package.json`

Finding: Broad exports and dependencies prove Canvas is a large platform surface. 

Required change: Stabilize export policy and mark experimental/internal subpaths.

Priority: P1

---

### `platform/typescript/canvas/src/public/index.ts`

Finding: Public barrel is too broad, exporting many implementation-level systems. It does correctly state Canvas should not own UI Builder abstractions.  

Required change: Split stable/experimental/internal public surfaces.

Priority: P1

---

### `platform/typescript/canvas/src/hybrid/types.ts`

Finding: Canvas model is TypeScript-interface based, not runtime schema-backed in inspected files. 

Required change: Add `CanvasDocumentSchema`, migrations, and serializer.

Priority: P0

---

### `platform/typescript/canvas/src/hybrid/state.ts`

Finding: Global atoms and `hybridCanvasStore` facade exist; history restores only elements/nodes/edges.  

Required change: Add instance-scoped store factory and full document history.

Priority: P1

---

### `platform/typescript/canvas/src/hybrid/hybrid-canvas-controller.ts`

Finding: Instance-scoped controller exists, but default ID generation is nondeterministic, and composite group/ungroup/duplicate flows are not command-atomic.  

Required change: Add deterministic ID provider by default for imported/generated documents and command transactions.

Priority: P0

---

### `platform/typescript/ui-builder/package.json`

Finding: Correct package ownership is declared for document model, bindings, actions, validation, and code generation. 

Required change: Add explicit artifact adapter exports only after contracts stabilize.

Priority: P1

---

### `platform/typescript/ui-builder/src/core/builder-document.ts`

Finding: Canonical schema exists, but compatibility adapters remain central.  

Required change: Enforce canonical model in public APIs; move compatibility to migrations.

Priority: P0

---

### `platform/typescript/ui-builder/src/core/types.ts`

Finding: Deprecated note redirects `BuilderDocument` to `builder-document.ts`; this is good but transitional. 

Required change: Remove deprecated document ambiguity after migration phase.

Priority: P1

---

### `platform/typescript/ui-builder/src/core/operations.ts`

Finding: Operations use canonical document and normalization, but include unsafe casts and snapshot undo stack.  

Required change: Replace casts with typed helpers and add command/event model.

Priority: P0

---

### `platform/typescript/ui-builder/src/core/import.ts`

Finding: TSX import is AST-based, but JSON import still requires `rootNodes`; HTML import is best-effort and regex-based for custom elements.  

Required change: Align JSON import with canonical schema and create separate compiler adapter for HTML/TSX.

Priority: P0

---

### `platform/typescript/ui-builder/src/core/codegen.ts`

Finding: React codegen is honest about loss points and confidence, but many modeled features are not fully generated. 

Required change: Add golden output tests and support bindings/responsive/state variants or explicitly block production claims.

Priority: P1

---

### `platform/typescript/ui-builder/src/preview/protocol.ts`

Finding: Acknowledgement-based preview protocol exists. 

Required change: Wire this into a real Studio preview runtime and test mount/update/error/timeout flows.

Priority: P1

---

### `platform/typescript/ds-generator/package.json`

Finding: The package is intentionally scoped as preset/brand customization today. 

Required change: Expand to full DS generation pipeline.

Priority: P1

---

### `platform/typescript/ds-generator/src/presets/index.ts`

Finding: Deterministic token materialization exists for colors, typography, radius, spacing, elevation, shadow, motion, and z-index. 

Required change: Add semantic/alias token graph, contrast validation, component tokens.

Priority: P1

---

### `platform/typescript/ds-generator/src/brand/index.ts`

Finding: Brand validation exists, but brand CSS output is not as complete as preset CSS output. 

Required change: Emit complete token categories for brand output and add golden tests.

Priority: P1

---

### `platform/typescript/ghatana-studio/src/App.tsx`

Finding: Main shell routes exist, but Builder Studio is not routed in inspected route tree. 

Required change: Add routed authoring workflows.

Priority: P0

---

### `platform/typescript/ghatana-studio/src/routes/CanvasPage.tsx`

Finding: Static artifact graph visualization with random positions and no-op handlers, not production canvas authoring. 

Required change: Replace with deterministic artifact graph/canvas workspace.

Priority: P0

---

### `platform/typescript/ghatana-studio/src/sections/BuilderStudio.tsx`

Finding: Useful local builder workflow exists, but persistence is localStorage-based and palette is hardcoded.  

Required change: Route it, use registry-backed components, and persist to workspace/project backend.

Priority: P0

---

### `platform/typescript/ghatana-studio/src/components/builder/VisualCanvas.tsx`

Finding: One-way BuilderDocument → Canvas projection; selection cast crosses package types via `as unknown as`. 

Required change: Create explicit `BuilderCanvasProjectionAdapter`.

Priority: P0

---

### `platform/typescript/ghatana-studio/src/routes/ArtifactsPage.tsx`

Finding: Displays artifact manifests/intelligence metadata but is not a compiler/decompiler workflow. 

Required change: Add import/decompile job list, evidence pack, residual review, fidelity report, and recompile actions.

Priority: P0

---

### `platform/typescript/kernel-artifacts/src/domain/ArtifactManifest.ts`

Finding: Strong artifact lifecycle manifest foundation exists, including fingerprints, trust, signatures, SBOM, attestation, retention, sourceRef, provenanceRef. 

Required change: Extend or pair with compiler/decompiler evidence contracts.

Priority: P0

---

## H. Iterative Production-Grade Implementation Plan

## Phase 1: Canonical contracts and ownership cleanup

Goal: Establish stable shared contracts and package boundaries.

Scope:

* UI Builder canonical model.
* Canvas document model.
* Artifact compiler/decompiler contracts.
* DS document model.
* Public API export tiers.

Files to modify:

* `platform/typescript/ui-builder/src/core/builder-document.ts`
* `platform/typescript/ui-builder/src/core/types.ts`
* `platform/typescript/canvas/src/hybrid/types.ts`
* `platform/typescript/canvas/src/public/index.ts`
* `platform/typescript/ds-generator/src/index.ts`
* root package governance scripts

Files to create:

* `platform/typescript/artifact-contracts/package.json`
* `platform/typescript/artifact-contracts/src/index.ts`
* `platform/typescript/artifact-contracts/src/source.ts`
* `platform/typescript/artifact-contracts/src/model.ts`
* `platform/typescript/artifact-contracts/src/provenance.ts`
* `platform/typescript/artifact-contracts/src/fidelity.ts`
* `platform/typescript/artifact-contracts/src/evidence.ts`

Detailed tasks:

1. Define `LogicalArtifactModel`.
2. Define `SourceRef`, `SourceFile`, `SourceSpan`.
3. Define `OwnershipRegion`.
4. Define `ResidualIslandReport`.
5. Define `FidelityReport`.
6. Define `CompileResult` and `DecompileResult`.
7. Move UI Builder legacy compatibility into explicit adapter module.
8. Add no-unsafe-cast package-boundary check.
9. Add public export allowlists.

Validation:

* Typecheck all platform packages.
* Run architecture boundaries.
* Run no deprecated imports/packages.
* Add contract schema tests.

Done criteria:

* One canonical BuilderDocument.
* One CanvasDocument contract.
* One artifact contract package.
* No ambiguous public compatibility exports.

---

## Phase 2: Canvas runtime correctness

Goal: Make Canvas safe for persisted, multi-document authoring.

Scope:

* Canvas document schema.
* Store isolation.
* Command history.
* Deterministic IDs.
* Serialization.

Files to modify:

* `platform/typescript/canvas/src/hybrid/state.ts`
* `platform/typescript/canvas/src/hybrid/hybrid-canvas-controller.ts`
* `platform/typescript/canvas/src/hybrid/types.ts`
* `platform/typescript/canvas/src/public/index.ts`

Files to create:

* `platform/typescript/canvas/src/document/canvas-document.ts`
* `platform/typescript/canvas/src/document/migrations.ts`
* `platform/typescript/canvas/src/document/serialization.ts`
* `platform/typescript/canvas/src/commands/*`

Detailed tasks:

1. Add `CanvasDocumentSchema`.
2. Add migration registry.
3. Add `createCanvasStore()`.
4. Add `CanvasProvider`.
5. Replace global store usage in public workflows.
6. Add command model.
7. Wrap group/ungroup/duplicate as composite commands.
8. Persist full document state in history, not just arrays.

Tests:

* Undo/redo command tests.
* Group/ungroup atomicity tests.
* Multi-canvas isolation tests.
* Serialization/migration golden tests.

---

## Phase 3: UI Builder canonical model and operations

Goal: Remove BuilderDocument ambiguity and make operations production-safe.

Files to modify:

* `platform/typescript/ui-builder/src/core/builder-document.ts`
* `platform/typescript/ui-builder/src/core/types.ts`
* `platform/typescript/ui-builder/src/core/operations.ts`
* `platform/typescript/ui-builder/src/core/import.ts`
* `platform/typescript/ui-builder/src/core/codegen.ts`
* `platform/typescript/ui-builder/src/core/validation.ts`

Detailed tasks:

1. Remove public reliance on `rootNodes`.
2. Make JSON import canonical schema-first.
3. Replace unsafe casts with typed transformation helpers.
4. Add graph/tree invariants.
5. Validate binding expressions and action payloads.
6. Add explicit migration adapters for old docs.
7. Add codegen golden outputs.

Tests:

* BuilderDocument canonical schema tests.
* Migration tests.
* Operation invariant tests.
* Import/codegen round-trip tests.

---

## Phase 4: Artifact compiler/decompiler foundation

Goal: Make artifact compiler/decompiler a first-class platform capability.

Files to create:

* `platform/typescript/artifact-compiler-ts/package.json`
* `platform/typescript/artifact-compiler-ts/src/index.ts`
* `platform/typescript/artifact-compiler-ts/src/decompile/tsx.ts`
* `platform/typescript/artifact-compiler-ts/src/compile/react.ts`
* `platform/typescript/artifact-compiler-ts/src/projection/builder.ts`
* `platform/typescript/artifact-compiler-ts/src/projection/canvas.ts`
* `platform/typescript/artifact-compiler-ts/src/projection/ds.ts`
* backend/Java service package for scanning/indexing if repo layout supports it

Detailed tasks:

1. Add source acquisition contracts.
2. Add TS lightweight decompiler adapter.
3. Add logical model builder.
4. Add residual island detection.
5. Add fidelity scorer.
6. Add protected region model.
7. Add evidence pack generator.
8. Add Java/backend scanner design and first implementation for local folder/archive.

Tests:

* Source fixture decompile tests.
* Logical model golden tests.
* Residual island tests.
* Fidelity scoring tests.

---

## Phase 5: Design-system generator completeness

Goal: Promote DS Generator from preset utility to full generator.

Files to modify:

* `platform/typescript/ds-generator/src/index.ts`
* `platform/typescript/ds-generator/src/presets/index.ts`
* `platform/typescript/ds-generator/src/brand/index.ts`

Files to create:

* `platform/typescript/ds-generator/src/model/design-system-document.ts`
* `platform/typescript/ds-generator/src/tokens/token-graph.ts`
* `platform/typescript/ds-generator/src/validation/contrast.ts`
* `platform/typescript/ds-generator/src/targets/css.ts`
* `platform/typescript/ds-generator/src/targets/json.ts`
* `platform/typescript/ds-generator/src/targets/tailwind.ts`
* `platform/typescript/ds-generator/src/targets/react-theme.ts`

Detailed tasks:

1. Add token graph.
2. Add alias/semantic token resolution.
3. Add WCAG contrast validation.
4. Add component variants/states.
5. Add deterministic file emission.
6. Add golden tests.

---

## Phase 6: Studio real workflow integration

Goal: Turn Studio from shell into working product-development workflow.

Files to modify:

* `platform/typescript/ghatana-studio/src/App.tsx`
* `platform/typescript/ghatana-studio/src/routes/CanvasPage.tsx`
* `platform/typescript/ghatana-studio/src/routes/ArtifactsPage.tsx`
* `platform/typescript/ghatana-studio/src/sections/BuilderStudio.tsx`
* `platform/typescript/ghatana-studio/src/components/builder/VisualCanvas.tsx`

Files to create:

* `src/routes/ImportDecompilePage.tsx`
* `src/routes/BuilderPage.tsx`
* `src/routes/DesignSystemPage.tsx`
* `src/routes/PreviewPage.tsx`
* `src/routes/FidelityReportPage.tsx`
* `src/adapters/BuilderCanvasProjectionAdapter.ts`
* `src/adapters/ArtifactStudioWorkflowAdapter.ts`

Detailed tasks:

1. Route Builder Studio.
2. Replace random canvas layout with deterministic layout.
3. Add import/decompile workflow.
4. Add residual review queue.
5. Add fidelity report page.
6. Add DS generator workflow.
7. Add preview runtime.
8. Add export/re-import loop.

Tests:

* Studio route tests.
* End-to-end authoring journey.
* Import → edit → preview → export → re-import journey.

---

## Phase 7: Round-trip and preview hardening

Goal: Prove fidelity.

Detailed tasks:

1. Add source → model → source test.
2. Add model → source → model test.
3. Add preview mount/update/error/timeout tests.
4. Add ownership/protected region tests.
5. Add residual island review workflow.

Done criteria:

* Round-trip cannot silently lose source intent.
* Every loss point is surfaced to user.
* Preview runtime matches generated output.

---

## Phase 8: Testing, CI, and regression gates

Goal: Enforce production readiness.

Add gates:

```bash
pnpm check:artifact-compiler-contracts
pnpm check:artifact-roundtrip
pnpm check:builder-canonical-document
pnpm check:canvas-document-roundtrip
pnpm check:studio-authoring-workflows
pnpm check:ds-generator-golden
```

---

## Phase 9: Documentation, examples, and developer experience

Goal: Make adoption safe.

Create:

* Canvas document guide.
* UI Builder canonical model guide.
* Artifact compiler/decompiler architecture.
* Studio workflow guide.
* DS generator target guide.
* Example source repos and round-trip fixtures.

---

## I. Exact TODO List

### Phase 1 — Canonical contracts

* [ ] `platform/typescript/artifact-contracts/package.json` — create shared contract package.

  * Why: Compiler/decompiler needs stable shared contracts.
  * Tests: package build/typecheck.
  * Priority: P0

* [ ] `platform/typescript/artifact-contracts/src/model.ts` — define `LogicalArtifactModel`, `ArtifactNode`, `ArtifactEdge`, `ArtifactKind`.

  * Why: Required for source → model → visual editing.
  * Tests: schema validation tests.
  * Priority: P0

* [ ] `platform/typescript/artifact-contracts/src/provenance.ts` — define `SourceRef`, `SourceFile`, `SourceSpan`, `ProvenanceRecord`.

  * Why: No source intent should be silently dropped.
  * Tests: provenance serialization tests.
  * Priority: P0

* [ ] `platform/typescript/ui-builder/src/core/types.ts` — remove deprecated BuilderDocument ambiguity from public docs after migration.

  * Why: One canonical model.
  * Tests: import API tests.
  * Priority: P0

* [ ] `scripts/check-builder-document-boundaries.mjs` — reject unsafe cross-package casts and legacy document imports.

  * Why: Prevent regression.
  * Tests: script fixture tests.
  * Priority: P0

### Phase 2 — Canvas correctness

* [ ] `platform/typescript/canvas/src/document/canvas-document.ts` — add `CanvasDocumentSchema`.

  * Why: Runtime validation.
  * Tests: valid/invalid schema tests.
  * Priority: P0

* [ ] `platform/typescript/canvas/src/commands/history.ts` — implement command-based undo/redo.

  * Why: Composite operations must undo atomically.
  * Tests: group/ungroup/duplicate undo tests.
  * Priority: P0

* [ ] `platform/typescript/canvas/src/hybrid/hybrid-canvas-controller.ts` — replace default random ID provider.

  * Why: Deterministic imported/generated documents.
  * Tests: deterministic ID tests.
  * Priority: P1

* [ ] `platform/typescript/canvas/src/hybrid/state.ts` — move global store facade behind provider/factory.

  * Why: Multi-canvas isolation.
  * Tests: two-canvas isolation test.
  * Priority: P1

### Phase 3 — UI Builder

* [ ] `platform/typescript/ui-builder/src/core/import.ts` — make JSON import canonical-layout based, not `rootNodes` based.

  * Why: Current canonical model uses layout/root.
  * Tests: canonical JSON import test.
  * Priority: P0

* [ ] `platform/typescript/ui-builder/src/core/operations.ts` — remove `as unknown as` casts.

  * Why: Package-boundary type safety.
  * Tests: strict typecheck and operation tests.
  * Priority: P0

* [ ] `platform/typescript/ui-builder/src/core/validation.ts` — add duplicate parentage, cycle, binding/action payload validation.

  * Why: Prevent invalid documents.
  * Tests: negative validation tests.
  * Priority: P1

* [ ] `platform/typescript/ui-builder/src/core/codegen.ts` — add golden output tests for all supported component patterns.

  * Why: Deterministic generation.
  * Tests: golden snapshots.
  * Priority: P1

### Phase 4 — Artifact compiler/decompiler

* [ ] `platform/typescript/artifact-compiler-ts/src/decompile/tsx.ts` — move UI TSX import into compiler adapter.

  * Why: UI Builder should not become repo decompiler.
  * Tests: TSX fixture tests.
  * Priority: P0

* [ ] `platform/typescript/artifact-compiler-ts/src/fidelity/scorer.ts` — create fidelity scorer.

  * Why: Round-trip trust.
  * Tests: scoring fixtures.
  * Priority: P0

* [ ] `platform/typescript/artifact-compiler-ts/src/residual/residual-islands.ts` — implement residual island report.

  * Why: Unsupported source intent must be visible.
  * Tests: unsupported pattern tests.
  * Priority: P0

* [ ] backend/Java scanner package — implement local folder/archive scanner.

  * Why: Large repo scanning belongs outside browser UI.
  * Tests: integration scan fixture.
  * Priority: P0

### Phase 5 — DS Generator

* [ ] `platform/typescript/ds-generator/src/model/design-system-document.ts` — add schema-backed DS document.

  * Why: Full DS generation needs canonical input.
  * Tests: schema tests.
  * Priority: P1

* [ ] `platform/typescript/ds-generator/src/validation/contrast.ts` — add WCAG contrast validation.

  * Why: Accessibility gate.
  * Tests: contrast matrix tests.
  * Priority: P1

* [ ] `platform/typescript/ds-generator/src/targets/tailwind.ts` — add Tailwind config target.

  * Why: Required platform target.
  * Tests: golden output.
  * Priority: P1

* [ ] `platform/typescript/ds-generator/src/brand/index.ts` — emit complete brand CSS token set.

  * Why: Brand output currently less complete than preset output.
  * Tests: brand CSS golden test.
  * Priority: P1

### Phase 6 — Studio

* [ ] `platform/typescript/ghatana-studio/src/App.tsx` — route Builder, DS, Preview, Import/Decompile, Fidelity pages.

  * Why: Capabilities must be reachable.
  * Tests: route tests.
  * Priority: P0

* [ ] `platform/typescript/ghatana-studio/src/routes/CanvasPage.tsx` — remove random layout and no-op handlers.

  * Why: Deterministic, editable workspace.
  * Tests: deterministic layout + interaction tests.
  * Priority: P0

* [ ] `platform/typescript/ghatana-studio/src/components/builder/VisualCanvas.tsx` — replace one-way projection with adapter.

  * Why: Visual edits must update BuilderDocument.
  * Tests: bidirectional sync tests.
  * Priority: P0

* [ ] `platform/typescript/ghatana-studio/src/routes/ArtifactsPage.tsx` — add decompile jobs, residual queue, fidelity view.

  * Why: Artifact compiler/decompiler workflow.
  * Tests: E2E import/decompile/fidelity test.
  * Priority: P0

---

## J. Commands to Validate

Root commands available from current root scripts include build, test, lint, typecheck, circular dependency checks, architecture boundaries, design-system conformance, Studio kernel API, and production-stub checks. 

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
pnpm check:production-readiness
pnpm check:deprecated-imports
pnpm check:deprecated-packages
```

Package-specific commands:

```bash
# Canvas
pnpm --dir platform/typescript/canvas build
pnpm --dir platform/typescript/canvas type-check
pnpm --dir platform/typescript/canvas test
pnpm --dir platform/typescript/canvas docs:api

# UI Builder
pnpm --dir platform/typescript/ui-builder build
pnpm --dir platform/typescript/ui-builder type-check
pnpm --dir platform/typescript/ui-builder test

# DS Generator
pnpm --dir platform/typescript/ds-generator build
pnpm --dir platform/typescript/ds-generator type-check
pnpm --dir platform/typescript/ds-generator test

# Ghatana Studio
pnpm --dir platform/typescript/ghatana-studio build
pnpm --dir platform/typescript/ghatana-studio type-check
pnpm --dir platform/typescript/ghatana-studio lint
pnpm --dir platform/typescript/ghatana-studio test
pnpm --dir platform/typescript/ghatana-studio test:e2e
pnpm --dir platform/typescript/ghatana-studio test:a11y

# Kernel artifacts
pnpm --dir platform/typescript/kernel-artifacts build
pnpm --dir platform/typescript/kernel-artifacts typecheck
pnpm --dir platform/typescript/kernel-artifacts test
pnpm --dir platform/typescript/kernel-artifacts lint
```

New commands to add:

```bash
pnpm check:builder-canonical-document
pnpm check:canvas-document-roundtrip
pnpm check:artifact-compiler-contracts
pnpm check:artifact-roundtrip
pnpm check:artifact-fidelity
pnpm check:ds-generator-golden
pnpm check:studio-authoring-workflows
```

---

## K. Final Production Readiness Gate

These areas cannot be called production-grade until all of the following are true:

* [ ] One canonical `BuilderDocument`.
* [ ] Runtime-validatable `CanvasDocument`.
* [ ] Versioned Canvas and Builder migrations.
* [ ] Correct command-based canvas undo/redo.
* [ ] Instance-scoped canvas stores only; no global-state bleed.
* [ ] Real Studio canvas workflow.
* [ ] Real Studio visual UI Builder workflow.
* [ ] Real DS generation workflow.
* [ ] Real artifact compiler/decompiler workflow.
* [ ] Source → model → edit → generate → re-import round-trip test.
* [ ] Fidelity and residual island reporting.
* [ ] Golden tests for generated outputs.
* [ ] Preview runtime parity.
* [ ] Accessibility, i18n, privacy, security, and observability gates.
* [ ] No mocks/stubs/placeholders in production code.
* [ ] No unsafe casts across package boundaries.
* [ ] No deprecated import usage.
* [ ] No duplicated schemas or duplicate ownership.
* [ ] No random/non-deterministic layout or generation in production paths.
* [ ] No localStorage-only persistence for production Studio workflows.

---

## Final Answer Required

```markdown
Final Verdict: No

Are these areas feature-complete and correctly implemented for a production-grade, world-class Ghatana product-development platform?

No. The codebase has meaningful foundations: Canvas has a broad hybrid runtime, UI Builder has moved toward a canonical schema-backed model, DS Generator has deterministic preset/brand materialization, Studio has a real shell, and kernel-artifacts has lifecycle manifest contracts. However, the full product-development loop is not production-grade yet because artifact compiler/decompiler is not first-class, Studio does not yet wire real end-to-end authoring workflows, Canvas lacks schema/migration/command-hardening, DS Generator is not a full design-system generator, and round-trip/fidelity evidence is incomplete.

Reason:
The current snapshot proves partial foundations, not feature completeness. The most important missing capability is a real artifact compiler/decompiler platform that can scan source, produce a logical model, preserve provenance, project into Canvas/UI Builder/DS, compile back to source, re-import, and report fidelity/residual islands.

Required minimum work before production:
1. Canonical shared artifact contracts.
2. Strict canonical BuilderDocument.
3. Runtime-validatable CanvasDocument.
4. Command-based Canvas history.
5. Full DS generator targets and contrast validation.
6. Routed Studio workflows for import/decompile, canvas, builder, DS, preview, export, re-import, and fidelity.
7. Source → model → source → model round-trip gates.
8. Golden tests and CI gates.

Recommended next milestone:
Milestone 1 — Canonical contracts and ownership cleanup across Artifact, Builder, Canvas, DS, and Studio.

Recommended first implementation PR:
Create `@ghatana/artifact-contracts`, harden canonical `BuilderDocument`, introduce `CanvasDocumentSchema`, and add governance checks for unsafe casts/deprecated compatibility paths.

Recommended parallel workstreams:
1. Canvas runtime correctness.
2. UI Builder canonical model and import/codegen hardening.
3. Artifact compiler/decompiler foundation with TS adapters and Java/backend scanner.
4. DS Generator full target pipeline.
5. Studio workflow integration and E2E authoring tests.
```
