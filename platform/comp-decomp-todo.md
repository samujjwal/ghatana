Executed against `samujjwal/ghatana` at commit `9b8d12bfd8f7af3f58632f145fa7b8f9d6dcf6ae`.

I inspected the scoped package manifests, public API barrels, core document models, runtime controllers, codegen/import/preview paths, Studio routes/sections, and representative tests. I did **not** run the repo locally; this is a code-level audit based on the target snapshot.

# A. Executive Summary

## Overall verdict

**Final Verdict: Partial foundation, not production-grade feature-complete.**

The four packages show a meaningful platform direction, but they are not yet coherent enough to be considered feature-complete or correctly implemented as a production-grade Ghatana product-development platform.

| Package                   |                                                                         Readiness |   Rating |
| ------------------------- | --------------------------------------------------------------------------------: | -------: |
| `@ghatana/canvas`         |      Broad foundation, unstable public surface, history/runtime correctness risks |   4 / 10 |
| `@ghatana/ui-builder`     |                                Strong ambition, but major document-model fracture |   3 / 10 |
| `@ghatana/ds-generator`   |             Minimal deterministic preset/brand generator, not a full DS generator | 2.5 / 10 |
| `@ghatana/ghatana-studio` | Shell exists, but live canvas/builder/ds-generator workflows are mostly not wired |   3 / 10 |

## Top blockers

1. **`@ghatana/ui-builder` exposes two incompatible BuilderDocument models.** `types.ts` defines a Map-based `BuilderDocument` with `id`, `version`, `name`, `designSystem`, `rootNodes`, and `nodes: ReadonlyMap`, while `builder-document.ts` defines a separate Zod schema with `schemaVersion`, `documentId`, `owner`, `root`, `nodes` as a record, `bindings`, and `layout`. Both are exported from the same core barrel.   

2. **Studio already compensates for that model fracture with unsafe casting.** `PreviewLab.tsx` casts `createBuilderDocument(...) as unknown as PreviewBuilderDocument`, showing the public contracts are not actually compatible. 

3. **Canvas undo/redo is likely incorrect.** The hybrid controller mutates state and then calls `pushHistory`, while `pushHistoryAtom` snapshots the current state. That means history captures the post-mutation state, not the pre-mutation state, for key operations.  

4. **`@ghatana/canvas` still publishes a deprecated `./ui-builder` subpath.** The public API comments say new code should use `@ghatana/ui-builder`, but `package.json` still exposes `./ui-builder`.  

5. **Studio’s `/canvas` route is not an actual canvas authoring experience.** `CanvasPage.tsx` renders artifact graph summaries, residual islands, risk hotspots, semantic references, and evidence IDs, but it does not import or render `@ghatana/canvas`. 

6. **Studio’s Builder Studio is document CRUD, not visual UI building.** It creates, validates, imports, exports, lists, and deletes builder documents using localStorage, but it does not provide a component palette, visual layout editor, prop inspector, canvas scene integration, preview parity, action wiring, or generated-code workflow. 

7. **Preview protocol is not a real production preview runtime yet.** `PreviewHostService.mount()` posts a message and resolves immediately; it does not wait for `MOUNTED`. Studio uses an `about:blank` iframe and no visible preview runtime loader.   

8. **`@ghatana/ds-generator` is too narrow for the intended role.** It supports presets, brand overrides, CSS custom properties, and manifest artifacts, but it does not yet support full semantic tokens, contrast validation, component state generation, Tailwind/React target generation, golden artifacts, or an idempotent file-emission pipeline.    

9. **UI import/decompile is heuristic, not production-grade.** TSX import uses regex scanning, not an AST parser, and JSON import assumes document structures that conflict with the dual BuilderDocument models. 

10. **Testing is not yet aligned to feature completeness.** I found useful tests for the schema-style builder document, but the inspected evidence does not show equivalent coverage for canvas history/runtime, DS generator golden output, preview handshake, visual builder journeys, or Studio end-to-end authoring. 

# B. Package-by-Package Current State

## Package: `@ghatana/canvas`

### Intended responsibility

Reusable canvas/runtime surface for visual authoring, diagramming, artifact interaction, selection, viewport control, tools, plugins, collaboration, export, accessibility, telemetry, and large-canvas performance.

### Actual current responsibility

The package is very broad. Its manifest exposes many subpaths including `./react`, `./types`, `./plugins`, `./hybrid`, `./topology`, `./tools`, `./chrome`, `./ai`, `./export`, `./collaboration`, `./telemetry`, `./testing`, `./flow`, `./public`, and the deprecated `./ui-builder`. 

The public barrel exports plugin systems, hybrid canvas controller/state/hooks, AI provider, telemetry, export, collaboration, React overlays, tools, panels, theme, accessibility, core systems, performance utilities, elements, diagram primitives, semantic zoom, and utilities. 

### What is correct

The package has the right high-level ambition: hybrid canvas, graph/freeform layers, plugins, collaboration, telemetry, export, accessibility, semantic zoom, panels, tools, and elements are all visible at the public API level. 

### What is incomplete or incorrect

The biggest correctness issue is runtime state/history. The controller mutates atoms, then pushes history; the history atom snapshots the current state when called, so undo can capture the already-mutated state.  

The controller uses a singleton Jotai default store and singleton controller instance. That is risky for multiple canvases, embedded canvases, isolated tests, collaboration sessions, and multi-document Studio workflows. 

ID generation uses `Date.now()` plus `Math.random()`, which is not deterministic, not injectable, and not ideal for replay, CRDT, import/export traceability, or golden tests. 

The public `CanvasDocument` and element model are TypeScript-only and not backed by a runtime schema, migration registry, serializer/deserializer, or compatibility contract. 

There is also a type-domain mismatch: `CanvasElementType` includes many visual element types but not `"node"` or `"edge"`, while `CanvasNode` and `CanvasEdge` extend `CanvasElement` with `type: "node"` and `type: "edge"`. 

### Production readiness rating

**4 / 10**

The canvas package has breadth, but correctness and stability gates need to come before feature expansion.

---

## Package: `@ghatana/ui-builder`

### Intended responsibility

Canonical visual UI composition model: builder document, component registry/contracts, bindings, actions, validation, preview protocol, scene projection, import/export, and codegen.

### Actual current responsibility

The manifest states it owns document model, bindings, actions, validation, and code generation. It exports core, React, web, preview, testing, and schema JSON subpaths. 

The core barrel exports many important systems: operations, validation, codegen, platform plan, telemetry, scene projection, import, persistence, DS binding, and builder-document schema utilities. 

### What is correct

The package is correctly separated from `@ghatana/canvas` at dependency level. Its manifest depends on DS schema/registry, primitives, platform events, zod, nanoid, and immer, but not canvas. 

It has several right primitives: component instances, bindings, responsive variants, state variants, actions, review status, AI lineage, code ownership, privacy metadata, provenance, validation, code projection, preview config, import, persistence, and DS-binding concepts. 

### What is incomplete or incorrect

The package currently has a severe contract split:

* `types.ts` defines a Map-based `BuilderDocument`.
* `builder-document.ts` defines a separate schema-based `BuilderDocument`.
* `operations.ts`, `validation.ts`, `codegen.ts`, `import.ts`, and `preview/protocol.ts` use the Map-style model.
* `BuilderStudio.tsx` uses the schema-style `createBuilderDocument`.
* `PreviewLab.tsx` casts schema-style docs into preview’s Map-style document type.

This blocks production correctness because consumers cannot know which BuilderDocument is canonical.     

Validation is useful but partial. It checks missing contracts, required prop presence, slot min/max, allowed components, root existence, orphan nodes, responsive prop warnings, action event warnings, and preview trust policy. It does not validate prop types/enums deeply, binding expression safety, action payload shape, acyclic graph/tree constraints, duplicate parentage, conditional rendering rules, form validation semantics, or codegen parity. 

Codegen emits static React/TSX and records loss points for bindings, responsive variants, state variants, user-authored code, protected code, and missing contracts. That is honest, but it means core builder features are modeled but not generated with runtime parity. 

Import/decompile is not production-grade. TSX uses regex scanning, JSON import only checks for `rootNodes` and `nodes`, and HTML import only maps `<ghatana-*>` custom elements. 

### Production readiness rating

**3 / 10**

The most urgent fix is not adding features; it is establishing one canonical BuilderDocument model.

---

## Package: `@ghatana/ds-generator`

### Intended responsibility

Generate and transform design systems: tokens, semantic tokens, themes, component styles, variants, states, accessibility checks, platform targets, deterministic artifacts, and golden-testable outputs.

### Actual current responsibility

The manifest describes it as “Design system preset materialization and brand customization generator.” It exports presets, brand, and package JSON. 

The source barrel exports presets, brand config/application, and generator extension manifest helpers. 

### What is correct

Preset materialization is deterministic. It maps semantic color names, typography scale, radius, and spacing to concrete tokens and can render CSS custom properties. 

The extension manifest sorts extension points, contracts, and artifacts for deterministic output, which is a good foundation for golden tests and reproducible generation. 

### What is incomplete or incorrect

This is not yet a full design-system generator. It lacks:

* Contrast validation.
* Token alias/reference graph.
* Semantic tokens beyond a few colors.
* Motion tokens.
* Shadow/elevation output.
* Border tokens.
* Z-index tokens.
* Component state generation.
* Tailwind config target.
* React component style target.
* JSON token target.
* File-emission pipeline.
* Golden output tests.
* Migration/versioning of generated design-system artifacts.

Brand generation also accepts custom CSS properties verbatim and does not validate CSS variable names or values. It also does not enforce that `brand.basePresetId` matches the preset passed to `applyBrand`. 

### Production readiness rating

**2.5 / 10**

Useful seed package, but much closer to a preset utility than a production DS generator.

---

## Package: `@ghatana/ghatana-studio`

### Intended responsibility

Product-facing Studio experience composing ideation, blueprinting, canvas, builder, DS generation, lifecycle execution, preview, validation, artifact lifecycle, deployment, health, learning, and evolution.

### Actual current responsibility

The manifest positions it as a unified Studio experience and depends on canvas, UI builder, DS generator, kernel lifecycle/artifact/deployment/release packages, product shell, i18n, tokens, theme, platform events, and platform utils. 

`App.tsx` provides a real shell with routes for home, ideas, blueprints, canvas, develop, lifecycle, agents, artifacts, deployments, health, learn, and settings, with capability gating and route ownership metadata. 

### What is correct

The Studio shell direction is strong. It uses product-shell, route capability discovery, lifecycle data, i18n, error boundary, and access guarding. 

### What is incomplete or incorrect

The Canvas route is not actually a canvas. It renders YAPPC artifact/risk/evidence summaries, not the canvas package. 

Builder Studio is not a visual builder. It uses localStorage and document CRUD, with no actual component palette, canvas editor, live preview, DS binding, action wiring, or codegen workflow. 

Preview Lab is not production preview. It uses an `about:blank` iframe, hardcoded localhost trusted origin, permissive sandbox flags, and a type cast between incompatible document models. 

### Production readiness rating

**3 / 10**

Studio is a solid shell, but it does not yet orchestrate the scoped packages into a complete authoring workflow.

# C. Cross-Package Integration Review

## Dependency direction

Mostly correct at manifest level:

```text
@ghatana/ghatana-studio
  → @ghatana/canvas
  → @ghatana/ui-builder
  → @ghatana/ds-generator
  → kernel/platform packages

@ghatana/ui-builder
  → @ghatana/ds-schema
  → @ghatana/ds-registry
  → @ghatana/primitives

@ghatana/ds-generator
  → @ghatana/ds-schema
  → @ghatana/tokens
```

The root scripts already include governance checks such as production readiness, architecture boundaries, circular dependency checks, Studio/kernel API checks, design-system conformance, and production-stub checks. 

## Main integration gap

The packages exist together, but they do not yet form a coherent end-to-end product-development loop:

```text
Prompt / import
  → logical artifact model
  → canvas scene
  → UI builder document
  → design-system-aware rendering
  → preview
  → validate
  → codegen/export
  → re-import/decompile
  → diff/version/traceability
```

The strongest blocker is the UI-builder document fracture. Until there is one canonical BuilderDocument, canvas scene projection, preview, persistence, import, codegen, and Studio workflows will continue to require adapters, unsafe casts, or duplicated logic.

# D. Feature Completeness Matrix

| Capability                  | Canvas            | UI Builder                  | DS Generator             | Studio                | Current Status | Gap                                                   | Owner Package             | Priority |
| --------------------------- | ----------------- | --------------------------- | ------------------------ | --------------------- | -------------- | ----------------------------------------------------- | ------------------------- | -------- |
| Canvas document model       | Partial           | N/A                         | N/A                      | Not wired             | Partial        | TS-only, no schema/migration                          | Canvas                    | P1       |
| Nodes/edges                 | Partial           | Scene projection partial    | N/A                      | Not wired             | Partial        | Type mismatch and no full validation                  | Canvas                    | P1       |
| Viewport/pan/zoom/grid      | Partial           | N/A                         | N/A                      | Not wired             | Partial        | Needs journey tests and instance store                | Canvas                    | P1       |
| Selection/multiselect       | Partial           | Scene deltas partial        | N/A                      | Not wired             | Partial        | Needs keyboard/marquee/edge tests                     | Canvas                    | P1       |
| Undo/redo                   | Incorrect         | Partial undo stack exported | N/A                      | Not wired             | Incorrect      | Canvas snapshots are pushed after mutation            | Canvas                    | P0       |
| Plugin model                | Partial           | Component registry partial  | Manifest partial         | Not exposed           | Partial        | Needs stable contracts and lifecycle                  | Canvas / UI Builder       | P1       |
| Serialization/migration     | Missing/partial   | Incorrect dual models       | N/A                      | Import/export partial | Incorrect      | One canonical schema required                         | UI Builder                | P0       |
| Component registry          | N/A               | Partial via contracts       | N/A                      | Not exposed           | Partial        | Needs runtime registry UX                             | UI Builder                | P1       |
| Prop schemas                | N/A               | Partial                     | N/A                      | Not exposed           | Partial        | Required prop only, weak type validation              | UI Builder                | P1       |
| Layout model                | N/A               | Partial                     | N/A                      | Not visual            | Partial        | Needs visual layout engine and constraints            | UI Builder                | P1       |
| Responsive variants         | N/A               | Model only                  | Token support minimal    | Not exposed           | Partial        | Not generated/rendered fully                          | UI Builder                | P1       |
| State variants              | N/A               | Model only                  | Missing state tokens     | Not exposed           | Partial        | Not generated/rendered fully                          | UI Builder / DS Generator | P1       |
| Data binding                | N/A               | Model only                  | N/A                      | Not exposed           | Partial        | No safe binding runtime                               | UI Builder                | P1       |
| Actions/events              | N/A               | Model only                  | N/A                      | Not exposed           | Partial        | No action executor/validator                          | UI Builder                | P1       |
| Codegen                     | N/A               | Static TSX partial          | N/A                      | Export partial        | Partial        | Bindings/actions/responsive not emitted               | UI Builder                | P1       |
| Import/decompile            | N/A               | Regex heuristic             | N/A                      | JSON import only      | Incorrect      | Needs AST parser and ownership-aware reconciliation   | UI Builder                | P0       |
| Preview protocol            | N/A               | Partial                     | N/A                      | Partial lab           | Incorrect      | No ack promise/runtime/secure iframe                  | UI Builder / Studio       | P0       |
| Token generation            | Theme exports     | Consumes DS contracts       | Partial                  | Not exposed           | Partial        | Missing semantic/full token graph                     | DS Generator              | P1       |
| Contrast validation         | A11y utilities    | Contract checks partial     | Missing                  | Not exposed           | Missing        | WCAG gate required                                    | DS Generator              | P0       |
| Component style generation  | N/A               | Consumes contracts          | Missing                  | Not exposed           | Missing        | Variants/states/components not generated              | DS Generator              | P1       |
| Tailwind/React/JSON targets | N/A               | N/A                         | Missing/partial CSS only | Not exposed           | Missing        | Target adapters required                              | DS Generator              | P1       |
| Studio canvas workflow      | Available package | N/A                         | N/A                      | Missing               | Missing        | `/canvas` is evidence dashboard                       | Studio                    | P0       |
| Studio builder workflow     | N/A               | Available package           | N/A                      | Partial CRUD          | Partial        | No visual authoring                                   | Studio                    | P0       |
| Studio DS workflow          | N/A               | N/A                         | Available package        | Missing               | Missing        | No DS generation route/flow                           | Studio                    | P1       |
| E2E artifact lifecycle      | Partial APIs      | Partial APIs                | Partial APIs             | Missing               | Missing        | No complete source→model→preview→export→reimport path | All                       | P0       |

# E. File-by-File Findings

## `platform/typescript/canvas/package.json`

Current role: Package manifest and public export map.

Finding: Exposes a very broad package surface, including deprecated `./ui-builder`. 

Impact: New consumers can still import the wrong builder abstractions from canvas.

Required change: Keep compatibility if needed, but mark `./ui-builder` as deprecated with a runtime warning/dev-time lint rule and remove from all internal usage.

Priority: **P1**

---

## `platform/typescript/canvas/src/public/index.ts`

Current role: Public API barrel.

Finding: The file declares itself the single authoritative public API, but exports many internals and compatibility surfaces. It also explicitly says canvas should not own UI-builder abstractions and that `@ghatana/canvas/ui-builder` is deprecated. 

Impact: Public API stability is weak. Consumers can bind to internals that should remain private.

Required change: Split into stable public API, experimental API, internal API, and compatibility API.

Priority: **P1**

---

## `platform/typescript/canvas/src/types/index.ts`

Current role: Canvas document and element type model.

Finding: Provides TS-only document types without runtime schema, migrations, serializer, or compatibility checks. It also defines `CanvasNode.type = "node"` and `CanvasEdge.type = "edge"` while `CanvasElementType` does not include those values. 

Impact: Serialization, persistence, import/export, and runtime validation are fragile.

Required change: Add `CanvasDocumentSchema`, `CanvasElementSchema`, `CanvasNodeSchema`, `CanvasEdgeSchema`, schema versioning, migrations, and round-trip tests.

Priority: **P1**

---

## `platform/typescript/canvas/src/hybrid/hybrid-canvas-controller.ts`

Current role: Main hybrid canvas imperative API.

Finding: Uses singleton store/controller, non-deterministic IDs, mutation-after-history pattern, partial command semantics, and no runtime validation at operation boundaries. 

Impact: Undo/redo, collaboration, tests, multi-canvas embedding, replay, and persistence are unsafe.

Required change: Replace direct mutations with command transactions: `execute(command)`, `undo(command)`, `redo(command)`, `validate(command)`, `serialize(command)`. Inject `idProvider`, `clock`, `store`, and `telemetry`.

Priority: **P0**

---

## `platform/typescript/canvas/src/hybrid/state.ts`

Current role: Jotai atoms and history state.

Finding: `pushHistoryAtom` snapshots the current canvas state. Since controller operations call it after mutation, the history entry records the wrong state for undo. 

Impact: Basic authoring trust is compromised.

Required change: Store inverse command or pre-mutation snapshot. Add transaction batching for multi-step operations like duplicate/group/ungroup.

Priority: **P0**

---

## `platform/typescript/ui-builder/src/core/index.ts`

Current role: Public core API barrel.

Finding: Exports both `./types` and `./builder-document`, which contain incompatible BuilderDocument concepts. 

Impact: Public API consumers receive conflicting types and workflows.

Required change: Establish `CanonicalBuilderDocument`. Export legacy adapters under explicit compatibility paths only.

Priority: **P0**

---

## `platform/typescript/ui-builder/src/core/types.ts`

Current role: Map-style builder model.

Finding: Defines the rich builder model used by operations, validation, codegen, import, and preview protocol. 

Impact: This is probably closer to the intended rich authoring model, but it lacks a runtime schema and conflicts with the schema-style model.

Required change: Either make this the canonical internal model with DTO serializers, or migrate all operations to the Zod schema model.

Priority: **P0**

---

## `platform/typescript/ui-builder/src/core/builder-document.ts`

Current role: Zod schema-style builder document.

Finding: Defines a separate BuilderDocument shape and exposes creation, validation, migration, serialization, and helper functions. The creation options include design-system fields but the returned document does not actually include a design-system model. 

Impact: Studio can create documents that are valid for this schema but incompatible with the rest of UI-builder runtime.

Required change: Merge this schema with the canonical rich builder model or rename this to `SerializedBuilderDocumentV1` and require explicit adapters.

Priority: **P0**

---

## `platform/typescript/ui-builder/src/core/operations.ts`

Current role: Immutable operations over Map-style documents.

Finding: Uses Map-style `document.nodes` and emits events with `document.id`; incompatible with schema-style `documentId`. 

Impact: Operations cannot safely run against documents created by `createBuilderDocument`.

Required change: Align document identity and node storage model; add precondition validation and contract validation per operation.

Priority: **P0**

---

## `platform/typescript/ui-builder/src/core/validation.ts`

Current role: Contract-aware validation over Map-style BuilderDocument.

Finding: Good baseline checks exist, but validation is not deep enough for production builder correctness. 

Impact: Invalid props, unsafe bindings, invalid action payloads, and cyclic/ambiguous structure can pass.

Required change: Add prop type validation, action payload validation, binding expression policy, duplicate parent detection, cycle detection, form validation checks, and generated-output parity checks.

Priority: **P1**

---

## `platform/typescript/ui-builder/src/core/codegen.ts`

Current role: React/TSX code generator.

Finding: It correctly identifies many loss points, but generated code does not preserve core features such as bindings, state variants, responsive variants, action wiring, and user-authored regions. 

Impact: Generated output cannot be treated as production-equivalent to the builder model.

Required change: Split codegen into static export, runtime export, and round-trip-safe export. Add golden tests.

Priority: **P1**

---

## `platform/typescript/ui-builder/src/core/import.ts`

Current role: Import/decompile TSX, HTML, JSON into BuilderDocument.

Finding: TSX import is regex-based and JSON import assumes structural compatibility that does not hold across the dual document models. 

Impact: Existing-code ingestion is not reliable enough for the artifact compiler/decompiler goal.

Required change: Replace TSX regex with TypeScript AST parser. Add ownership-aware reconciliation, unsupported-pattern capture, nested slot recovery, and round-trip tests.

Priority: **P0**

---

## `platform/typescript/ui-builder/src/preview/protocol.ts`

Current role: Preview iframe host protocol.

Finding: Mount/update send messages but resolve immediately; teardown clears handlers immediately; target origin can fall back to `*`; blank-origin events are specially accepted.  

Impact: Preview correctness and security are not production-grade.

Required change: Add correlation-based promises, timeout handling, strict origin policy, CSP integration, sandbox profile enforcement, and preview runtime bootstrap contract.

Priority: **P0**

---

## `platform/typescript/ds-generator/src/presets/index.ts`

Current role: Preset schema, built-in presets, token materialization, CSS rendering.

Finding: Useful deterministic seed, but limited token model. Elevation is accepted in preset schema but not materialized into output tokens. 

Impact: DS output cannot fully drive UI-builder, canvas, or product UI consistency.

Required change: Add semantic/base/alias token graph and materialize all accepted preset dimensions.

Priority: **P1**

---

## `platform/typescript/ds-generator/src/brand/index.ts`

Current role: Brand overrides.

Finding: Duplicates radius/density maps from presets, does not validate base preset match, and emits custom CSS properties verbatim. 

Impact: Drift, unsafe CSS output, and incorrect brand application are possible.

Required change: Centralize token maps, validate custom property names/values, enforce base preset compatibility, and run contrast checks.

Priority: **P1**

---

## `platform/typescript/ds-generator/src/extensions/index.ts`

Current role: Deterministic manifest artifact generator.

Finding: Good deterministic manifest, but artifacts are docs/examples/tests/builder-bindings strings only; tests are JSON specs, not executable test suites. 

Impact: The generator does not yet generate production-ready target packages.

Required change: Add target adapters and file emitters for CSS variables, Tailwind config, React theme provider, JSON tokens, docs, Storybook, and executable tests.

Priority: **P1**

---

## `platform/typescript/ghatana-studio/src/App.tsx`

Current role: Studio shell and routes.

Finding: Good shell, route guard, product-shell usage, and lifecycle-aware navigation. 

Impact: This is a good foundation for orchestration.

Required change: Wire real package workflows into routes.

Priority: **P1**

---

## `platform/typescript/ghatana-studio/src/routes/CanvasPage.tsx`

Current role: Canvas route.

Finding: Displays artifact intelligence summaries, not an actual canvas. 

Impact: Users cannot visually author, edit, inspect, or manipulate artifacts on this route.

Required change: Compose `@ghatana/canvas` with artifact graph data, selection, drill-down, validation overlays, and UI-builder scene projection.

Priority: **P0**

---

## `platform/typescript/ghatana-studio/src/sections/BuilderStudio.tsx`

Current role: Builder document management UI.

Finding: LocalStorage CRUD and import/export shell only. It hardcodes `studio-user`, does not use backend persistence, and does not provide actual visual editing. 

Impact: Not a production UI-builder workflow.

Required change: Replace with real builder workspace: component palette, tree/outline, property inspector, canvas scene, validation panel, preview, export, and persistence.

Priority: **P0**

---

## `platform/typescript/ghatana-studio/src/sections/PreviewLab.tsx`

Current role: Preview sandbox lab.

Finding: Uses type casts, `about:blank`, hardcoded localhost trusted origin, and iframe sandbox flags. 

Impact: Preview does not prove runtime parity or production safety.

Required change: Load a real preview runtime app, use strict origin/CSP, remove unsafe casts, and wait for protocol acknowledgements.

Priority: **P0**

# F. Production-Grade Implementation Plan

## Phase 1: Boundary cleanup and ownership correction

Goal: Stop package boundary drift before adding features.

Files to modify:

```text
platform/typescript/canvas/package.json
platform/typescript/canvas/src/public/index.ts
platform/typescript/ui-builder/src/core/index.ts
platform/typescript/ui-builder/src/core/types.ts
platform/typescript/ui-builder/src/core/builder-document.ts
platform/typescript/ui-builder/src/core/operations.ts
platform/typescript/ui-builder/src/core/validation.ts
platform/typescript/ui-builder/src/core/codegen.ts
platform/typescript/ui-builder/src/core/import.ts
platform/typescript/ui-builder/src/preview/protocol.ts
```

Tasks:

* Define exactly one canonical `BuilderDocument`.
* Rename the non-canonical one to `SerializedBuilderDocumentV1` or `LegacyBuilderDocument`.
* Add explicit adapters if both internal and persisted forms are needed.
* Remove or isolate deprecated canvas `./ui-builder` usage.
* Add package-boundary tests preventing new imports from deprecated surfaces.

Done criteria:

* No unsafe `as unknown as PreviewBuilderDocument` remains.
* All UI-builder operations, validation, codegen, import, preview, and Studio sections accept the same canonical document or explicit DTO adapters.

## Phase 2: Shared contracts and schemas

Goal: Make documents, canvas artifacts, bindings, actions, and generated outputs runtime-validatable.

Files to create:

```text
platform/typescript/canvas/src/schema/canvas-document.schema.ts
platform/typescript/canvas/src/schema/canvas-migrations.ts
platform/typescript/ui-builder/src/core/canonical-builder-document.ts
platform/typescript/ui-builder/src/core/builder-document.schema.ts
platform/typescript/ui-builder/src/core/builder-document-adapter.ts
platform/typescript/ui-builder/src/core/action.schema.ts
platform/typescript/ui-builder/src/core/binding.schema.ts
platform/typescript/ds-generator/src/schema/token.schema.ts
platform/typescript/ds-generator/src/schema/generator-output.schema.ts
```

Tasks:

* Add Zod schemas for all persisted documents.
* Add migrations and version detection.
* Emit JSON schemas as part of build.
* Add round-trip tests.

Done criteria:

* Canvas document, builder document, and DS generated artifacts can be serialized/deserialized/migrated with deterministic behavior.

## Phase 3: Canvas correctness and runtime hardening

Goal: Make visual authoring reliable.

Files to modify:

```text
platform/typescript/canvas/src/hybrid/hybrid-canvas-controller.ts
platform/typescript/canvas/src/hybrid/state.ts
platform/typescript/canvas/src/hybrid/hooks.ts
platform/typescript/canvas/src/hybrid/types.ts
platform/typescript/canvas/src/core/keyboard-shortcuts.ts
platform/typescript/canvas/src/core/element-operations.ts
```

Files to create:

```text
platform/typescript/canvas/src/core/commands.ts
platform/typescript/canvas/src/core/history.ts
platform/typescript/canvas/src/core/id-provider.ts
platform/typescript/canvas/src/core/canvas-store.ts
platform/typescript/canvas/src/core/canvas-document-serializer.ts
platform/typescript/canvas/src/hybrid/__tests__/history.test.ts
platform/typescript/canvas/src/hybrid/__tests__/selection.test.ts
platform/typescript/canvas/src/hybrid/__tests__/multi-canvas-isolation.test.ts
platform/typescript/canvas/src/schema/__tests__/canvas-document-roundtrip.test.ts
```

Tasks:

* Replace post-mutation history with command transactions.
* Add precondition validation.
* Make store instance-scoped.
* Inject ID provider and clock.
* Add batch history for group/ungroup/duplicate.
* Add keyboard and selection tests.

Done criteria:

* Undo/redo passes for add, update, delete, duplicate, group, ungroup, node deletion with connected edges, and batch operations.

## Phase 4: UI-builder feature completeness

Goal: Make the builder model executable, previewable, exportable, and re-importable.

Files to modify:

```text
platform/typescript/ui-builder/src/core/validation.ts
platform/typescript/ui-builder/src/core/codegen.ts
platform/typescript/ui-builder/src/core/import.ts
platform/typescript/ui-builder/src/core/scene-projection.ts
platform/typescript/ui-builder/src/core/platform-plan.ts
platform/typescript/ui-builder/src/preview/protocol.ts
```

Files to create:

```text
platform/typescript/ui-builder/src/core/component-registry.ts
platform/typescript/ui-builder/src/core/prop-validator.ts
platform/typescript/ui-builder/src/core/action-runtime.ts
platform/typescript/ui-builder/src/core/binding-runtime.ts
platform/typescript/ui-builder/src/core/conditional-rendering.ts
platform/typescript/ui-builder/src/core/form-runtime.ts
platform/typescript/ui-builder/src/core/tsx-ast-importer.ts
platform/typescript/ui-builder/src/core/__tests__/canonical-document-contract.test.ts
platform/typescript/ui-builder/src/core/__tests__/operation-validation.test.ts
platform/typescript/ui-builder/src/core/__tests__/codegen-runtime-parity.test.tsx
platform/typescript/ui-builder/src/core/__tests__/import-ast-roundtrip.test.ts
platform/typescript/ui-builder/src/preview/__tests__/protocol-ack.test.ts
```

Tasks:

* Validate prop types against DS contracts.
* Execute bindings/actions through safe runtime policies.
* Support conditional rendering and form validation.
* Replace regex importer with AST importer.
* Add round-trip fidelity gates.

Done criteria:

* Builder document → preview → codegen → import has measured, test-backed fidelity.

## Phase 5: Design-system generator correctness

Goal: Turn preset utilities into a real DS generation pipeline.

Files to modify:

```text
platform/typescript/ds-generator/src/presets/index.ts
platform/typescript/ds-generator/src/brand/index.ts
platform/typescript/ds-generator/src/extensions/index.ts
platform/typescript/ds-generator/src/index.ts
```

Files to create:

```text
platform/typescript/ds-generator/src/tokens/token-graph.ts
platform/typescript/ds-generator/src/tokens/semantic-tokens.ts
platform/typescript/ds-generator/src/tokens/contrast.ts
platform/typescript/ds-generator/src/targets/css-vars.ts
platform/typescript/ds-generator/src/targets/tailwind.ts
platform/typescript/ds-generator/src/targets/react-theme.ts
platform/typescript/ds-generator/src/targets/json-tokens.ts
platform/typescript/ds-generator/src/pipeline/generate-design-system.ts
platform/typescript/ds-generator/src/__tests__/contrast.test.ts
platform/typescript/ds-generator/src/__tests__/golden/css-vars.test.ts
platform/typescript/ds-generator/src/__tests__/golden/tailwind.test.ts
platform/typescript/ds-generator/src/__tests__/idempotency.test.ts
```

Tasks:

* Add full token graph.
* Add WCAG contrast checks.
* Generate component states and variants.
* Add target adapters.
* Add golden tests.

Done criteria:

* Same input always produces byte-identical outputs.
* Invalid contrast or invalid token references fail generation.

## Phase 6: Studio workflow integration

Goal: Expose real package capabilities through simple, low-cognitive-load Studio flows.

Files to modify:

```text
platform/typescript/ghatana-studio/src/routes/CanvasPage.tsx
platform/typescript/ghatana-studio/src/sections/BuilderStudio.tsx
platform/typescript/ghatana-studio/src/sections/PreviewLab.tsx
platform/typescript/ghatana-studio/src/App.tsx
platform/typescript/ghatana-studio/src/navigation/studioNavigation.ts
platform/typescript/ghatana-studio/src/i18n/studioTranslations.ts
```

Files to create:

```text
platform/typescript/ghatana-studio/src/sections/CanvasWorkspace.tsx
platform/typescript/ghatana-studio/src/sections/BuilderWorkspace.tsx
platform/typescript/ghatana-studio/src/sections/DesignSystemWorkspace.tsx
platform/typescript/ghatana-studio/src/sections/ArtifactLifecycleWorkspace.tsx
platform/typescript/ghatana-studio/src/data/StudioArtifactRepository.ts
platform/typescript/ghatana-studio/src/__tests__/canvasWorkflow.test.tsx
platform/typescript/ghatana-studio/src/__tests__/builderWorkflow.test.tsx
platform/typescript/ghatana-studio/src/__tests__/designSystemWorkflow.test.tsx
platform/typescript/ghatana-studio/tests/e2e/canvas-builder-preview.spec.ts
```

Tasks:

* Replace CanvasPage’s static evidence cards with actual canvas workspace.
* Use artifact summaries as overlays, not as the canvas replacement.
* Replace localStorage-only BuilderStudio with real persistence adapter.
* Add DS generation flow.
* Wire preview runtime with trusted origin and CSP.

Done criteria:

* User can create/import artifact, view it on canvas, edit UI model, validate, preview, export, and re-import.

## Phase 7: Testing and regression gates

Required tests:

```text
platform/typescript/canvas/src/hybrid/__tests__/history.test.ts
platform/typescript/canvas/src/hybrid/__tests__/selection-marquee.test.ts
platform/typescript/canvas/src/hybrid/__tests__/keyboard-shortcuts.test.ts
platform/typescript/canvas/src/hybrid/__tests__/multi-canvas-isolation.test.ts
platform/typescript/ui-builder/src/core/__tests__/canonical-document-contract.test.ts
platform/typescript/ui-builder/src/core/__tests__/operation-validation.test.ts
platform/typescript/ui-builder/src/core/__tests__/codegen-runtime-parity.test.tsx
platform/typescript/ui-builder/src/core/__tests__/import-ast-roundtrip.test.ts
platform/typescript/ui-builder/src/preview/__tests__/preview-handshake.test.ts
platform/typescript/ds-generator/src/__tests__/contrast.test.ts
platform/typescript/ds-generator/src/__tests__/golden-output.test.ts
platform/typescript/ghatana-studio/tests/e2e/create-edit-preview-export.spec.ts
```

Done criteria:

* Unit, integration, and E2E tests prove the full journey.
* No test only checks rendering without behavior.
* No generated output ships without golden tests.

# G. Exact TODO List

```markdown
- [ ] `platform/typescript/ui-builder/src/core/index.ts` — stop exporting two incompatible BuilderDocument systems.
  - Why: This is the biggest correctness blocker.
  - Implementation detail: Export one `CanonicalBuilderDocument`; move the other to an explicit compatibility/DTO adapter.
  - Validation: Typecheck all consumers.
  - Tests: `canonical-document-contract.test.ts`.
  - Priority: P0

- [ ] `platform/typescript/ui-builder/src/core/builder-document.ts` — rename or merge schema-style document with canonical model.
  - Why: Current schema document is incompatible with operations/codegen/preview.
  - Implementation detail: Convert to persisted DTO schema or make it the canonical model and update all operations.
  - Validation: No `as unknown as` casts in Studio.
  - Tests: serialization, migration, operation compatibility.
  - Priority: P0

- [ ] `platform/typescript/ui-builder/src/core/operations.ts` — align with canonical document identity and storage.
  - Why: Uses `document.id` and Map APIs that do not match schema-style documents.
  - Implementation detail: Use canonical selectors/mutators and explicit document identity helper.
  - Validation: insert/move/delete/update works against canonical doc.
  - Tests: operation-validation test suite.
  - Priority: P0

- [ ] `platform/typescript/canvas/src/hybrid/state.ts` — fix history snapshot timing.
  - Why: Undo/redo likely restores post-action state.
  - Implementation detail: Push pre-action snapshot or inverse command before mutation.
  - Validation: undo add/delete/update/group/ungroup/duplicate.
  - Tests: `history.test.ts`.
  - Priority: P0

- [ ] `platform/typescript/canvas/src/hybrid/hybrid-canvas-controller.ts` — replace singleton mutable controller with instance-scoped command controller.
  - Why: Multi-canvas and test isolation are unsafe.
  - Implementation detail: Inject store, clock, id provider, telemetry, validator.
  - Validation: two canvas instances cannot share selection/history.
  - Tests: `multi-canvas-isolation.test.ts`.
  - Priority: P0

- [ ] `platform/typescript/ui-builder/src/core/import.ts` — replace regex TSX import with AST importer.
  - Why: Existing-code ingestion cannot be reliable with regex.
  - Implementation detail: Use TypeScript parser, preserve slots, props, ownership regions, unsupported patterns.
  - Validation: TSX → BuilderDocument → TSX round-trip.
  - Tests: `import-ast-roundtrip.test.ts`.
  - Priority: P0

- [ ] `platform/typescript/ui-builder/src/preview/protocol.ts` — make preview protocol acknowledgement-based.
  - Why: `mount()` returns after send, not after render.
  - Implementation detail: correlation map, timeout, strict origin, mounted/updated/error resolution.
  - Validation: failed iframe/runtime times out with actionable error.
  - Tests: `preview-handshake.test.ts`.
  - Priority: P0

- [ ] `platform/typescript/ghatana-studio/src/routes/CanvasPage.tsx` — replace static evidence dashboard with live canvas workspace.
  - Why: `/canvas` currently does not use `@ghatana/canvas`.
  - Implementation detail: Render `HybridCanvas`; show evidence/risk as overlays/panels.
  - Validation: user can select/edit artifact nodes.
  - Tests: Canvas workflow component and E2E tests.
  - Priority: P0

- [ ] `platform/typescript/ghatana-studio/src/sections/BuilderStudio.tsx` — replace localStorage CRUD with real visual builder.
  - Why: Current section is not feature-complete UI building.
  - Implementation detail: add component palette, tree, canvas scene, inspector, validation, preview/export.
  - Validation: create/edit/preview/export a document.
  - Tests: builder workflow tests.
  - Priority: P0

- [ ] `platform/typescript/ghatana-studio/src/sections/PreviewLab.tsx` — remove unsafe document cast and about:blank preview.
  - Why: Current preview does not prove runtime parity.
  - Implementation detail: load real preview runtime and use canonical BuilderDocument.
  - Validation: preview renders real document and reports mounted/updated.
  - Tests: preview E2E test.
  - Priority: P0

- [ ] `platform/typescript/ds-generator/src/presets/index.ts` — materialize all preset dimensions.
  - Why: `elevation` exists but is not emitted.
  - Implementation detail: add elevation/shadow/border/motion/z-index tokens.
  - Validation: generated token snapshot includes all accepted dimensions.
  - Tests: golden token tests.
  - Priority: P1

- [ ] `platform/typescript/ds-generator/src/brand/index.ts` — validate brand safety and base preset compatibility.
  - Why: custom CSS is emitted verbatim and base preset ID is unchecked.
  - Implementation detail: validate custom property names/values and assert `brand.basePresetId === preset.id`.
  - Validation: unsafe CSS and mismatched preset fail.
  - Tests: brand validation tests.
  - Priority: P1

- [ ] `platform/typescript/ds-generator/src/tokens/contrast.ts` — add WCAG contrast validation.
  - Why: Design-system generation must enforce accessibility.
  - Implementation detail: validate foreground/background semantic pairs.
  - Validation: invalid palettes fail generation.
  - Tests: contrast test suite.
  - Priority: P0
```

# H. Commands to Validate

From the root package scripts: 

```bash
pnpm install
pnpm lint
pnpm typecheck
pnpm typecheck:workspace
pnpm test
pnpm build
pnpm check:circular-deps
pnpm check:architecture-boundaries
pnpm check:design-system-conformance
pnpm check:studio-kernel-api
pnpm check:production-stubs
pnpm check:phase1
pnpm check:world-class-platform-readiness
```

Package-specific:

```bash
pnpm --dir platform/typescript/canvas type-check
pnpm --dir platform/typescript/canvas test
pnpm --dir platform/typescript/canvas build

pnpm --dir platform/typescript/ui-builder type-check
pnpm --dir platform/typescript/ui-builder test
pnpm --dir platform/typescript/ui-builder build

pnpm --dir platform/typescript/ds-generator type-check
pnpm --dir platform/typescript/ds-generator test
pnpm --dir platform/typescript/ds-generator build

pnpm --dir platform/typescript/ghatana-studio type-check
pnpm --dir platform/typescript/ghatana-studio lint
pnpm --dir platform/typescript/ghatana-studio test
pnpm --dir platform/typescript/ghatana-studio test:a11y
pnpm --dir platform/typescript/ghatana-studio test:e2e
pnpm --dir platform/typescript/ghatana-studio build
```

# I. Final Production Readiness Gate

These packages should not be considered production-grade until all of the following are true:

1. There is exactly one canonical `BuilderDocument`.
2. Canvas undo/redo is command-based and proven by tests.
3. Canvas supports schema-backed serialization, deserialization, and migration.
4. UI-builder import/decompile uses AST-backed parsing and ownership-aware reconciliation.
5. Codegen output has golden tests and runtime parity tests.
6. Preview protocol waits for mounted/updated/error acknowledgements.
7. DS generator validates contrast and emits full deterministic target artifacts.
8. Studio `/canvas` renders a real canvas workspace.
9. Studio builder provides visual editing, not only document CRUD.
10. Studio DS generation is exposed as a real workflow.
11. Full E2E proves: create/import → canvas → builder → preview → validate → export → re-import.
12. No production code depends on deprecated canvas UI-builder compatibility exports.
13. No unsafe `as unknown as` casts are needed between scoped packages.
14. Package boundary checks prevent recurrence.

```markdown
Final Verdict: Partial

Reason:
The packages contain meaningful foundations and good architectural intent, but the current snapshot is not feature-complete or correctly integrated for production. The biggest blocker is the incompatible UI-builder document models, followed by canvas history correctness, incomplete Studio wiring, weak preview runtime, and limited DS generator capability.

Required minimum work before production:
Canonicalize BuilderDocument, fix canvas history/runtime, replace heuristic import/decompile, harden preview, expand DS generator, and wire real Studio canvas/builder/design-system workflows.

Recommended next milestone:
Milestone 1 should be “Canonical Model + Runtime Correctness”: fix UI-builder document ownership, canvas command/history, preview protocol acknowledgement, and one Studio happy-path flow from builder document creation to preview.
```
