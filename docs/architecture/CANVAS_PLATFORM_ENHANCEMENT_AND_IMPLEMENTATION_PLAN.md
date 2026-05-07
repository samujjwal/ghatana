# Canvas Platform Enhancement And Implementation Plan

Reviewed and authored on 2026-04-11 from:
- current Ghatana canvas packages under `platform/typescript/canvas*`
- product usage in `products/yappc`, `products/data-cloud/planes/action`, and `products/data-cloud`
- repo governance and ADR material
- current official ecosystem references for React Flow / XYFlow, Yjs, and OpenTelemetry

---

## 1. Purpose

This document defines a hardened plan to organize, enhance, and implement Ghatana’s **canvas platform** as a first-class shared capability.

The goal is not to maintain one canvas implementation for one product. The goal is to provide a **single canvas platform** that can support:

- structured flow editors,
- topology and lineage visualizations,
- hybrid freeform + graph canvases,
- AI-assisted spatial workspaces,
- rich interactive visual authoring,
- and product-specific extensions without fragmenting the platform.

This plan uses the same rigor applied to the design-system architecture:

- AI/ML is a first-class citizen,
- observability and telemetry are first-class citizens,
- complete user visibility is required,
- user involvement is minimized but never hidden,
- customization and extensibility are supported through governed APIs,
- public package exposure remains minimal and deliberate.

---

## 2. Fundamental Requirements

### 2.1 First-class canvas requirements

The canvas platform must treat the following as non-optional:

- **AI/ML-native behavior**
  - explicit and implicit AI-assisted actions,
  - suggestions, generation, validation, ranking, ghost states, and automation,
  - policy-driven review and approval.
- **Observability / o11y**
  - structured telemetry,
  - traces,
  - metrics,
  - audit records,
  - performance instrumentation,
  - operator-visible diagnostics.
- **Complete user visibility**
  - visible operation state,
  - provenance,
  - recent actions,
  - review status,
  - failure/fallback state,
  - what changed and why.
- **Minimal human interruption**
  - involve users only when needed by confidence, impact, permissions, policy, or ambiguity.
- **Customizability**
  - product-specific node and edge types,
  - product-specific chrome and panels,
  - configurable tools, behaviors, and policies.
- **Extensibility**
  - plugin system,
  - registries,
  - domain configuration,
  - controlled subpath APIs,
  - internal extension points without public API sprawl.
- **Accessibility**
  - keyboard navigation,
  - focus behavior,
  - reduced motion,
  - screen-reader support,
  - semantic alternatives for canvas-only interactions.
- **Performance**
  - large graph support,
  - viewport culling,
  - incremental rendering,
  - bounded interaction latency,
  - stable memory behavior.

### 2.2 Non-goals

- Not a product-local YAPPC canvas extracted wholesale into a shared library.
- Not a dumping ground for every spatial or diagram-related experiment.
- Not a second design system with uncontrolled UI chrome inside the root package.
- Not a public package graph that forces product teams to understand internals.
- Not an AI layer that acts invisibly without user-visible status, telemetry, or policy controls.

---

## 3. Current Repo Reality

### 3.1 Existing platform canvas assets

The repo currently contains:

- `platform/typescript/canvas` → `@ghatana/canvas`
- `platform/typescript/canvas-core` → deprecated façade
- `platform/typescript/canvas-react` → deprecated façade
- `platform/typescript/canvas-plugins` → deprecated façade
- `platform/typescript/canvas-tools` → deprecated façade
- `platform/typescript/canvas-chrome` → deprecated façade
- `platform/typescript/canvas/flow-canvas` → private nested package `@ghatana/flow-canvas`

Current platform canvas capabilities include:

- hybrid canvas primitives,
- React/XYFlow integration,
- topology helpers,
- plugin infrastructure,
- collaboration scaffolding,
- AI provider scaffolding,
- export utilities,
- accessibility and performance helpers,
- canvas chrome and panels,
- rich freeform element model,
- flow-canvas wrapper for graph-focused use cases.

### 3.2 Current product usage patterns

#### YAPPC

YAPPC uses canvas as a **full spatial workbench**:

- large React Flow-based project canvas,
- mode-aware workspace and lifecycle-driven UX,
- sketch layer,
- AI overlays and command bar,
- status bar and history UI,
- persistence and lifecycle orchestration,
- many custom nodes and panels,
- canvas-specific performance helpers,
- canvas-local adapters between React Flow and internal document/state models.

Implication:

- YAPPC exercises the broadest feature set,
- but much of that breadth is still product-specific and should not be promoted directly.

#### AEP

AEP uses canvas as a **specialized pipeline editor**:

- stage palette,
- connector/stage node types,
- drag/drop onto graph canvas,
- edge creation,
- keyboard deletion,
- Jotai state synchronization,
- product plugin registration.

Implication:

- AEP needs a simpler, graph-centric API,
- it should not need direct `@xyflow/react` wiring once the platform surface is mature.

#### Data-Cloud

Data-Cloud uses canvas for **topology and lineage visualization**:

- `EventCloudTopology` built on `@ghatana/canvas/topology` and `@ghatana/flow-canvas`,
- `LineageGraph` built on `@ghatana/flow-canvas`,
- custom node rendering and topology-specific data models,
- some separate direct `<canvas>` rendering for force-directed plugin graphs.

Implication:

- Data-Cloud validates the need for reusable topology and graph-view APIs,
- but also shows that specialized visualization cases may need lower-level engine hooks or separate visualization modules.

---

## 4. Current Problems And Risks

### 4.1 Package-boundary drift

The current canvas stack has real structural drift:

- `@ghatana/canvas` is the canonical package,
- deprecated canvas sub-libraries still exist as facades,
- `@ghatana/flow-canvas` exists as a nested private package,
- product code uses Vite aliases to raw source paths,
- library docs are inconsistent and partially stale.

This creates:

- API ambiguity,
- migration confusion,
- testing and build inconsistency,
- accidental reliance on internals.

### 4.2 Root-package bloat

`@ghatana/canvas` currently exports a very broad root surface:

- hybrid systems,
- element classes,
- ui-builder,
- tools,
- core internals,
- theme,
- chrome,
- AI,
- collaboration,
- export,
- topology,
- plugin registries,
- low-level renderer internals.

This is too much for a stable consumer contract.

### 4.3 Product/platform leakage

YAPPC-specific behavior and assumptions appear close to the shared layer:

- lifecycle-driven canvas modes,
- workspace-level orchestration,
- role/phase content,
- AI overlays tied to YAPPC flow,
- large canvas-specific product chrome.

These are valuable references, but not all should graduate into the platform.

### 4.4 Inconsistent implementation model

The current stack mixes:

- direct `@xyflow/react` usage,
- `@ghatana/flow-canvas`,
- `@ghatana/canvas/topology`,
- custom `<canvas>` rendering,
- hybrid freeform + graph systems,
- product-local adapters and wrappers.

That is acceptable internally, but the external story is not clean enough yet.

### 4.5 AI/visibility/o11y under-specification

The current codebase shows AI overlays and telemetry hooks, but the canvas platform does not yet define a unified contract for:

- autonomy levels,
- user visibility,
- approval thresholds,
- observability schema,
- auditability,
- operator dashboards.

---

## 5. External Guidance And Research Alignment

The plan should align with current official guidance:

- React Flow / XYFlow
  - custom nodes and edges are core extension points,
  - save/restore should treat nodes, edges, and viewport as canonical serializable state,
  - state management should be externalized cleanly for larger apps,
  - SSR/static generation needs explicit dimensions and handle metadata.
- Yjs
  - shared document state and awareness/presence should be treated separately,
  - awareness is optional and should be scoped to meaningful presence signals rather than noisy over-sharing.
- OpenTelemetry
  - traces, metrics, and logs should be correlated through shared context,
  - browser instrumentation is possible but should be deliberate and privacy-aware.

These align well with Ghatana’s existing needs:

- React Flow remains a strong fit for structured graph and topology editing,
- Yjs is appropriate where true collaborative editing matters,
- OpenTelemetry-compatible instrumentation should be the platform baseline for observability.

---

## 6. Core Principles

### 6.1 One canvas platform, multiple modes

There should be one platform canvas capability with multiple governed operating modes:

- graph/flow mode,
- topology mode,
- hybrid freeform + graph mode,
- rich spatial workspace mode,
- read-only visualization mode,
- collaboration-enabled mode.

### 6.2 Public surface must stay small

Product teams should consume a small stable set of public entry points and not import canvas internals directly.

### 6.3 AI/ML is pervasive, not bolted on

Canvas should support both:

- **explicit AI**
  - command bar, assistant panels, generation actions,
- **implicit AI**
  - ranking, suggestions, ghost nodes, auto-layout help, validation, anomaly detection, contextual next-best actions.

### 6.4 Less human interaction, more visibility

The platform should reduce interruption, not reduce transparency.

If the system acts implicitly, users must still see:

- active operation status,
- what changed,
- why it changed,
- what is suggested vs applied,
- confidence and uncertainty,
- how to override or review.

### 6.5 Observability is part of the API contract

Canvas APIs should expose telemetry, tracing, and audit semantics as first-class concerns, not optional add-ons.

### 6.6 Product-specific behavior stays product-local until proven

A feature becomes shared only after:

- at least two credible product consumers,
- stable semantics,
- acceptable accessibility behavior,
- acceptable performance profile,
- acceptable observability and AI policy coverage.

### 6.7 Extensibility without root-package sprawl

The platform should be extensible through:

- plugins,
- registries,
- domain configuration,
- additional node and edge types,
- controlled subpath exports.

It should not be extensible through:

- deep internal imports,
- product-specific public namespaces,
- ad hoc root exports.

---

## 7. Recommended Organization Model

### 7.1 Four-layer canvas organization

1. **Public canvas facade**
   - what products import
   - example: `@ghatana/canvas`
2. **Internal shared implementation**
   - reusable engine, topology internals, registries, adapters, serializers
3. **Shared apps and maintainer tools**
   - demos, playgrounds, diagnostics, performance lab, collaboration inspector
4. **Product-local canvas code**
   - YAPPC workflow UX, AEP pipeline specifics, Data-Cloud domain-specific nodes

### 7.2 Public package strategy

Keep one canonical package:

- `@ghatana/canvas`

Allow a small set of justified public subpaths:

- `@ghatana/canvas/flow`
- `@ghatana/canvas/topology`
- `@ghatana/canvas/collaboration`
- `@ghatana/canvas/ai`
- `@ghatana/canvas/export`
- `@ghatana/canvas/testing`

Everything else should be internal.

### 7.3 Internal-only modules

These can exist as internal modules or private workspace packages, but should not be default public consumer APIs:

- engine/rendering core,
- plugin registries,
- serialization internals,
- layout internals,
- performance internals,
- domain injection internals,
- chrome internals,
- telemetry internals,
- AI orchestration internals.

### 7.4 Shared libraries vs shared apps

To avoid shared-library bloat, canvas platform work must be split across:

- **shared runtime library**
  - reusable runtime APIs consumed by product code in production,
  - stable public contracts only,
  - no maintainer-only tools or debugging surfaces.
- **shared maintainer apps**
  - playgrounds,
  - benchmark labs,
  - accessibility harnesses,
  - telemetry explorers,
  - AI replay/review inspectors,
  - migration codemods and diagnostics.

The following belong in shared apps, not the runtime library by default:

- demo-only chrome,
- visual debugging overlays,
- performance stress harnesses,
- telemetry inspection dashboards,
- migration assistants,
- internal authoring workbenches,
- experimental element galleries.

Rule:

- if a capability is mainly for platform maintainers, reviewers, or migration work, it should ship as a shared app/tool, not as part of `@ghatana/canvas`.

---

## 8. Recommended Public API Surface

### 8.1 Root package: `@ghatana/canvas`

The root package should optimize for everyday usage.

It should expose:

- `CanvasProvider`
- `CanvasRoot`
- `CanvasViewport`
- `CanvasToolbar`
- `CanvasStatus`
- `CanvasHistory`
- `CanvasSelection`
- `CanvasLayer`
- `CanvasChrome`
- `CanvasEmptyState`
- `OperationStatus`
- `ReviewRequiredBanner`
- `ChangeSummary`
- `ActivityTimeline`
- core public types:
  - `CanvasDocument`
  - `CanvasNode`
  - `CanvasEdge`
  - `CanvasViewportState`
  - `CanvasSelectionState`
  - `CanvasOperation`
  - `CanvasTelemetryEvent`

### 8.2 Flow subpath: `@ghatana/canvas/flow`

Expose graph/flow-focused APIs:

- `FlowCanvas`
- `FlowControls`
- controlled node/edge state helpers
- stable graph types
- additional-node/additional-edge registration hooks

This should absorb the current `@ghatana/flow-canvas` role over time.

### 8.3 Topology subpath: `@ghatana/canvas/topology`

Expose:

- `BaseTopologyNode`
- `BaseTopologyEdge`
- `useTopology`
- layout helpers
- topology config and type contracts

For:

- Data-Cloud topology,
- lineage views,
- infrastructure diagrams,
- dependency graphs.

### 8.4 Collaboration subpath: `@ghatana/canvas/collaboration`

Expose:

- collaboration provider,
- presence/awareness API,
- conflict-safe document sync hooks,
- cursor and presence UI primitives,
- collaboration event contracts.

### 8.5 AI subpath: `@ghatana/canvas/ai`

Expose:

- AI provider interfaces,
- ghost node/ghost edge primitives,
- suggestion models,
- review state models,
- AI visibility primitives,
- AI telemetry event schemas,
- autonomy and approval policy types.

### 8.6 Export subpath: `@ghatana/canvas/export`

Expose:

- export/import APIs,
- snapshot APIs,
- render-to-image and document export helpers,
- persistence serialization contracts.

### 8.7 Testing subpath: `@ghatana/canvas/testing`

Expose:

- test render helpers,
- keyboard and accessibility assertions,
- serialization round-trip fixtures,
- performance and viewport test helpers,
- telemetry contract test helpers.

### 8.8 What should not be public

Do not publish as stable consumer API:

- raw renderer internals,
- internal registries,
- action registry internals,
- domain injection internals,
- private React Flow wrappers,
- internal chrome panels,
- product lifecycle modes,
- product-local node content implementations,
- deprecated sub-library facades.

### 8.9 Consumer simplicity model

Most consumers should only need one of these import patterns:

- `@ghatana/canvas`
- `@ghatana/canvas/flow`
- `@ghatana/canvas/topology`
- `@ghatana/canvas/collaboration`
- `@ghatana/canvas/ai`

Consumer expectations:

- product application teams should not need to understand renderer choice, registry internals, or serialization internals,
- advanced consumers may register nodes, edges, tools, and policies, but only through governed public contracts,
- no consumer should import from `src/*`, `dist/*`, or raw workspace paths.

### 8.10 Target exports map

The target package `exports` map should look conceptually like:

```json
{
  "exports": {
    ".": "./dist/public/index.js",
    "./flow": "./dist/public/flow/index.js",
    "./topology": "./dist/public/topology/index.js",
    "./collaboration": "./dist/public/collaboration/index.js",
    "./ai": "./dist/public/ai/index.js",
    "./export": "./dist/public/export/index.js",
    "./testing": "./dist/public/testing/index.js",
    "./package.json": "./package.json"
  }
}
```

There should be no stable exports for:

- `./core`
- `./hybrid`
- `./plugins`
- `./tools`
- `./chrome`
- `./internal/*`

If a capability is important enough to be public, it should graduate into one of the governed public entry points above rather than remain a low-level namespace.

---

## 9. Capability Model

### 9.1 Foundation capabilities

- document model,
- viewport model,
- selection model,
- undo/redo history,
- keyboard shortcuts,
- clipboard,
- import/export,
- accessibility primitives,
- telemetry primitives.

### 9.2 Graph and topology capabilities

- node/edge rendering,
- custom nodes and handles,
- custom edges,
- layout integration,
- save/restore,
- read-only and editable modes,
- multi-surface graph overlays,
- metrics overlays,
- topology health/status rendering.

### 9.3 Hybrid and freeform capabilities

- freeform elements,
- whiteboard/sketch layer,
- frame/grouping,
- embedded content,
- rich content blocks where justified,
- hybrid graph + freeform coordinate systems.

### 9.4 AI-native capabilities

- ghost suggestions,
- structural validation,
- auto-layout recommendations,
- semantic clustering,
- next-best actions,
- anomaly highlighting,
- code/design generation handoff,
- approval and review workflows.

### 9.5 Collaboration capabilities

- multi-user shared state,
- presence and awareness,
- cursor presence,
- selection presence,
- offline-safe synchronization where required,
- policy-aware collaboration data retention.

### 9.6 Observability capabilities

- operation metrics,
- render latency,
- interaction latency,
- trace spans for long-running actions,
- audit records for autonomous changes,
- override and approval telemetry,
- correlation IDs between AI actions and UI-visible state.

---

## 10. AI/ML As A First-Class Canvas Concern

### 10.1 Explicit and implicit AI modes

Canvas must support:

1. **Explicit AI**
   - assistant panel,
   - command bar,
   - generate-from-prompt,
   - explain-this-canvas,
   - validate-this-design.

2. **Implicit AI**
   - precomputed suggestions,
   - ghost nodes/edges,
   - smart alignment and snapping recommendations,
   - anomaly warnings,
   - contextual help,
   - auto-classification,
   - proactive next-step hints.

### 10.2 User-involvement policy

Involve the user only when:

- confidence is below threshold,
- action is destructive or externally visible,
- policy requires approval,
- sensitive data use requires consent or acknowledgement,
- there are materially different outcomes the user must choose among,
- rollback is not possible or not cheap.

Proceed autonomously when:

- the action is low-risk,
- reversible,
- explainable,
- within permission scope,
- and visible to the user through status and history.

### 10.3 Visibility contract

Every AI-assisted canvas flow must define:

- current operation state,
- suggested vs applied changes,
- confidence or uncertainty band,
- rationale summary,
- provenance/evidence,
- approval state,
- override/rollback availability,
- recent AI action timeline.

### 10.4 AI-specific canvas UI primitives

The platform should support reusable primitives such as:

- `OperationStatus`
- `ReviewRequiredBanner`
- `ChangeSummary`
- `ActivityTimeline`
- `GhostSuggestionLayer`
- `ConfidenceBadge`
- `EvidencePanel`
- `ApprovalControls`
- `FallbackNotice`

---

## 11. Observability / O11y / Audit Model

### 11.1 Telemetry is mandatory

Every meaningful canvas interaction should be able to emit structured events.

Required event families:

- `canvas.viewport.changed`
- `canvas.selection.changed`
- `canvas.node.created`
- `canvas.node.updated`
- `canvas.node.deleted`
- `canvas.edge.created`
- `canvas.edge.updated`
- `canvas.edge.deleted`
- `canvas.layout.applied`
- `canvas.import.completed`
- `canvas.export.completed`
- `canvas.render.failed`
- `canvas.performance.sampled`
- `canvas.ai.suggestion.shown`
- `canvas.ai.suggestion.accepted`
- `canvas.ai.suggestion.rejected`
- `canvas.ai.action.applied`
- `canvas.ai.review.requested`
- `canvas.ai.review.approved`
- `canvas.ai.review.rejected`
- `canvas.ai.override.invoked`

### 11.2 Trace model

Long-running canvas operations should produce traces and spans, including:

- import/export,
- large layout runs,
- collaborative sync operations,
- AI generation or validation tasks,
- server-assisted persistence and snapshotting.

The plan should be OpenTelemetry-compatible.

### 11.3 Metrics model

Required metrics should cover:

- canvas load time,
- first interactive time,
- node/edge count bands,
- render frame time,
- interaction latency,
- layout duration,
- export duration,
- collaboration sync latency,
- AI suggestion acceptance/rejection rate,
- override rate,
- error rate,
- memory pressure indicators where available.

### 11.4 Audit model

Autonomous or semi-autonomous actions must generate audit-friendly records for:

- who initiated the action,
- whether it was explicit or implicit,
- what changed,
- why it changed,
- whether approval was required,
- approval result,
- rollback outcome if attempted.

---

## 12. Accessibility And User Visibility Requirements

### 12.1 Accessibility baseline

Canvas must support:

- keyboard navigation across nodes and panels,
- keyboard alternatives to drag-only interactions,
- visible focus treatment,
- reduced motion support,
- screen-reader summaries for graph state,
- textual alternatives for canvas content where feasible,
- accessible selection, status, and error announcements.

### 12.2 Visibility baseline

Users must be able to answer:

- what is on this canvas,
- what is selected,
- what just changed,
- what the system is doing now,
- which actions require review,
- which AI suggestions are pending,
- which operations failed,
- what can be undone or retried.

### 12.3 Operator visibility baseline

Operators should be able to answer:

- how canvas performance behaves in production,
- where failures cluster,
- where AI actions are overridden,
- which products or surfaces are most problematic,
- which features are underused or too noisy.

---

## 13. Package And API Cleanup Plan

### 13.1 Canonical direction

Keep `@ghatana/canvas` as the only canonical public canvas package.

Deprecate or absorb:

- `@ghatana/canvas-core`
- `@ghatana/canvas-react`
- `@ghatana/canvas-plugins`
- `@ghatana/canvas-tools`
- `@ghatana/canvas-chrome`
- `@ghatana/flow-canvas`

### 13.2 Public subpaths to keep

Target public subpaths:

- `.`
- `./flow`
- `./topology`
- `./collaboration`
- `./ai`
- `./export`
- `./testing`

### 13.3 Current-package issues to fix

1. `@ghatana/canvas` root exports are too broad.
2. `@ghatana/flow-canvas` is private, nested, and not aligned cleanly with the workspace.
3. Some products alias raw source paths instead of depending on stable package surfaces.
4. Some docs still refer to old paths or even old package names.
5. The package surface does not yet communicate which APIs are stable vs internal.

### 13.4 Recommended source layout

```text
platform/typescript/canvas/
  src/
    public/
      index.ts
      flow/
      topology/
      collaboration/
      ai/
      export/
      testing/
    internal/
      engine/
      renderer/
      registries/
      layout/
      hybrid/
      chrome/
      telemetry/
      adapters/
      serializers/
      performance/
```

### 13.5 Export policy

Use explicit `exports` maps so product code cannot deep-import internals accidentally.

No public exports for:

- `./internal/*`
- low-level `./core/*`
- product-specific adapters
- product lifecycle modes

### 13.6 Boundary rules for correctness and long-term simplicity

1. The root package must stay product-neutral.
   It can expose shared orchestration primitives, but not YAPPC-, AEP-, or Data-Cloud-specific workflows.

2. React Flow / XYFlow details should be scoped.
   Flow- and topology-specific APIs may intentionally expose graph concepts, but the root package should not force every consumer to depend on `@xyflow/react` types.

3. AI adapters stay product-local unless proven shared.
   The platform can expose `CanvasAIAdapter` contracts and reusable visibility/review primitives, but product-specific adapters like YAPPC’s HTTP bridge remain product-owned until at least one more product needs the same semantics.

4. Shared node content must be truly shared.
   Product-specific node renderers, pipeline cards, workflow rails, and domain panels stay out of the platform package unless they have stable semantics across products.

5. No raw-source aliasing in product builds.
   Vite, Vitest, and tsconfig aliases must not point at `platform/typescript/canvas/src` or nested private package source once the canonical exports are in place.

6. Deprecated facades must be temporary.
   `@ghatana/canvas-core`, `@ghatana/canvas-react`, `@ghatana/canvas-plugins`, `@ghatana/canvas-tools`, `@ghatana/canvas-chrome`, and `@ghatana/flow-canvas` should exist only as migration shims with explicit removal milestones.

7. Visibility and o11y are part of feature completeness.
   A canvas feature is not done if it lacks visible state, telemetry semantics, and failure/override behavior.

---

## 14. Product-Fit Guidance

### 14.1 YAPPC

Keep product-local:

- lifecycle phase orchestration,
- persona and workflow overlays,
- YAPPC-specific left/right rails,
- YAPPC project/task context,
- YAPPC-specific node content types,
- YAPPC-specific AI authoring flows.

Promote to shared platform only if proven reusable:

- viewport culling and performance helpers,
- reusable status/toolbar patterns,
- ghost suggestion layer,
- generalized canvas history and persistence contracts,
- generic unified-node patterns if they become domain-neutral.

### 14.2 AEP

AEP should migrate toward:

- `@ghatana/canvas/flow` for graph editing primitives,
- platform plugin registration for stage/connector node types,
- platform-managed persistence and keyboard behaviors,
- platform telemetry/audit hooks for pipeline editing and AI review states.

Avoid long-term direct `@xyflow/react` dependency in product code where platform wrappers can provide the stable contract.

### 14.3 Data-Cloud

Data-Cloud should continue using shared topology/flow surfaces, with focus on:

- reusable topology configuration,
- lineage graph model adapters,
- metrics overlays,
- live topology update patterns,
- read-only visualization and drill-down APIs.

Custom direct `<canvas>` visualizations should stay product-local unless they become reusable beyond Data-Cloud.

---

## 15. Concrete Implementation Plan

### 15.1 Phase 0: Contract freeze and inventory

Do first:

- freeze the intended public canvas entry points,
- inventory all current imports in products,
- identify all raw-source aliases and deep imports,
- classify current exports into:
  - public-keep,
  - public-deprecate,
  - internalize,
  - product-localize.

### 15.2 Phase 1: Public API cleanup

Do:

- define `@ghatana/canvas` public contract,
- add explicit `exports` map,
- fold `@ghatana/flow-canvas` role into `@ghatana/canvas/flow`,
- mark deprecated sub-libraries for fix-forward migration,
- create consumer docs with allowed import paths only,
- define and publish forbidden import patterns,
- remove product aliases to raw canvas source from build configs as part of migration.

Definition of done:

- no new public deep imports,
- public surface fits on one reference page,
- import guidance is unambiguous.

### 15.3 Phase 2: Internal reorganization

Do:

- move root exports behind `src/public/*`,
- isolate internal engine/renderer/layout/registry code under `src/internal/*`,
- separate flow, topology, hybrid, collaboration, AI, and export contracts cleanly,
- keep product compatibility via facades during migration,
- move maintainer-only utilities into shared apps/tools instead of runtime exports.

Definition of done:

- products can migrate without understanding internals,
- internal modules are not accidentally importable.

### 15.3.1 Shared apps and tooling track

Build a parallel maintainers-only track for:

- canvas playground,
- topology playground,
- large-graph performance lab,
- collaboration inspector,
- AI review and replay console,
- accessibility harness,
- telemetry explorer.

Definition of done:

- maintainers can validate features without bloating the runtime package,
- product teams get better examples and diagnostics without inheriting internal tooling APIs.

### 15.4 Phase 3: Observability and visibility foundation

Do:

- define canonical canvas telemetry events,
- define trace/span model for long-running operations,
- add audit contracts for AI-assisted actions,
- add visibility primitives:
  - `OperationStatus`
  - `ReviewRequiredBanner`
  - `ChangeSummary`
  - `ActivityTimeline`

Definition of done:

- all AI-enabled and long-running canvas flows can expose visible status and structured telemetry by default.

### 15.5 Phase 4: Product migrations

YAPPC:

- keep product-local workflow chrome,
- migrate reusable pieces to stable platform APIs only where justified.

AEP:

- migrate from direct `@xyflow/react` product wiring toward `@ghatana/canvas/flow`.

Data-Cloud:

- standardize topology and lineage consumption on `@ghatana/canvas/flow` and `@ghatana/canvas/topology`.

### 15.6 Phase 5: AI-native platform completion

Do:

- standardize AI provider interface,
- add autonomy level and approval policy types,
- support ghost suggestion and review primitives,
- wire telemetry and audit for implicit AI actions,
- add dashboards and diagnostics for AI-assisted canvas flows.

### 15.7 Phase 6: Collaboration and persistence hardening

Do:

- define canonical document schema for graph and hybrid modes,
- separate collaboration state from awareness state,
- support presence/cursor APIs,
- define persistence snapshot format and versioning rules,
- define import/export migration behavior.

### 15.8 Phase 7: Quality and governance enforcement

Add gates for:

- public API review,
- accessibility review,
- performance thresholds,
- telemetry contract coverage,
- audit contract coverage for autonomous actions,
- package-boundary linting,
- deprecation tracking.

---

## 16. Quality Gates

Every public canvas capability should ship with:

- schema and type contract,
- docs,
- usage examples,
- accessibility notes,
- telemetry semantics,
- error and fallback behavior,
- tests,
- migration notes if API changes.

Every AI-enabled canvas capability should also ship with:

- visibility behavior,
- approval mode,
- override behavior,
- audit expectations,
- telemetry coverage,
- privacy notes.

Release should fail if:

- public APIs change without migration notes,
- required telemetry contracts are missing,
- autonomous actions lack visibility or audit coverage,
- accessibility regressions exist,
- package-boundary rules are violated.

---

## 17. Recommended Next Artifacts

1. **Canvas Public Contract Spec**
   - exact public entry points, exports map, approved symbol list, forbidden imports.

2. **Canvas Document Model Spec**
   - canonical graph/hybrid document schema, persistence, import/export, migration.

3. **Canvas AI Autonomy Spec**
   - autonomy levels, approval modes, visibility rules, rollback rules.

4. **Canvas Telemetry And O11y Spec**
   - event taxonomy, trace model, metric model, dashboards, audit record shape.

5. **Canvas Product Migration Plan**
   - YAPPC, AEP, Data-Cloud migration sequence with compatibility shims and lint enforcement.

6. **Canvas Shared Apps Plan**
   - playgrounds, benchmark harnesses, diagnostics, accessibility lab, AI replay tools, and ownership boundaries versus runtime library code.

7. **Live UI Builder And Execution Platform Architecture**
   - how canvas acts as one governed editing surface for page/component builders while the canonical source of truth remains builder documents and design-system contracts.

---

## 18. External References

- React Flow overview:
  - https://reactflow.dev/docs/concepts/introduction/
- React Flow custom nodes:
  - https://reactflow.dev/learn/customization/custom-nodes
- React Flow custom edges:
  - https://reactflow.dev/learn/customization/custom-edges
- React Flow save and restore:
  - https://reactflow.dev/examples/interaction/save-and-restore/
- React Flow state management:
  - https://reactflow.dev/learn/advanced-use/state-management
- React Flow SSR / static generation:
  - https://reactflow.dev/learn/advanced-use/ssr-ssg-configuration
- React Flow stress example:
  - https://reactflow.dev/examples/nodes/stress
- Yjs introduction:
  - https://docs.yjs.dev/
- Yjs awareness and presence:
  - https://docs.yjs.dev/getting-started/adding-awareness
- Yjs awareness API:
  - https://docs.yjs.dev/api/about-awareness
- OpenTelemetry docs:
  - https://opentelemetry.io/docs/
- OpenTelemetry JavaScript browser instrumentation:
  - https://opentelemetry.io/docs/languages/js/getting-started/browser/
- W3C accessibility standards overview:
  - https://www.w3.org/WAI/standards-guidelines/
- WAI-ARIA overview:
  - https://www.w3.org/WAI/standards-guidelines/aria/
- WAI-ARIA Graphics Module:
  - https://www.w3.org/TR/graphics-aria-1.0/
