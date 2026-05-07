# Unified Platform Implementation Plan

**Authored**: 2026-04-11  
**Revised**: 2026-04-11 — AI/ML pervasiveness, full visibility model, complete event taxonomy, DS engine hierarchy, four-layer synchronization, vanilla web target, missing shared apps, canvas capability model, full public API surface  
**Scope**: Canvas Platform + Design System Generator + Live UI Builder  
**Source Documents**:
- `CANVAS_PLATFORM_ENHANCEMENT_AND_IMPLEMENTATION_PLAN.md`
- `DESIGN_SYSTEM_GENERATOR_PLATFORM_ARCHITECTURE_HARDENED.md`
- `LIVE_UI_BUILDER_AND_EXECUTION_PLATFORM_ARCHITECTURE.md`

**Strictly follows**: `.github/copilot-instructions.md` (Ghatana repo conventions)

---

## 0. Non-Negotiable Platform Principles

These principles appear identically across all three source documents. Every task in every phase must satisfy them — they are not a phase or a module; they are the contract.

### 0.1 AI/ML is pervasive, not bolted on

AI/ML must be a first-class citizen of every platform layer, not a feature added at the end.

**Explicit AI** (user-initiated): command bar, assistant panels, prompt-to-page, prompt-to-component, prompt-to-theme, explain-this-canvas, validate-this-design, repair-accessibility, generate-code-comments.

**Implicit AI** (system-initiated, without user prompt):
- Canvas: ghost nodes/edges, auto-layout recommendations, semantic clustering, anomaly highlighting, next-best actions, smart alignment/snapping, contextual help, auto-classification, proactive next-step hints.
- Builder: auto-mapping imported UI to known components, auto-suggesting token usage, spacing/alignment recommendations, accessibility fix suggestions, security and privacy warnings, code cleanup recommendations, pattern extraction from repeated structures.
- Design system: proactive duplicate detection, drift detection, inaccessible-state detection, broken contract detection, governance entropy detection.

Every implicit AI action still exposes a full `AIVisibilityContract` — users are never surprised by a change they cannot see, explain, override, or roll back.

### 0.2 Full visibility — automated, manual, every action

The system must make every action legible regardless of whether it was triggered by AI, the user, or an automated background process.

Users must always be able to answer:
- What is the system doing right now?
- What changed since I last looked?
- What is suggested vs applied?
- What is still syncing or pending?
- What requires my review or approval?
- What did the AI do on my behalf and why?
- What failed, and what can I do about it?
- What can I undo or roll back?

Operators must always be able to answer:
- How is the canvas/builder performing in production?
- Where do failures cluster?
- Where are AI actions overridden most frequently?
- Which products or surfaces are under stress?
- Which features are underused or generating noise?

### 0.3 Minimal human interruption, maximum transparency

The system proceeds autonomously when an action is:
- low-risk,
- reversible,
- explainable,
- within policy scope,
- clearly visible to the user through status and history.

The system escalates to human review only when:
- confidence is below threshold,
- the action is destructive or externally visible,
- policy requires approval,
- sensitive data use requires consent,
- there are materially different outcomes the user must choose among,
- rollback is not possible or not cheap.

### 0.4 Observability is part of the API contract

Telemetry, traces, metrics, and audit records are first-class API obligations, not optional add-ons. Every platform package that performs meaningful operations must expose instrumentation hooks that consumers can wire without forking internals.

### 0.5 Security and privacy are first-class throughout

Every package that handles content, preview, code generation, or AI processing must declare its trust model, sandbox level, data classification, and secret-redaction behavior. These are runtime metadata, not documentation notes.

### 0.6 Human-control-first autonomy model

The platform must support both low-touch autonomous workflows and strict human-only workflows without code forks.

Required operating policies:
- `AUTONOMOUS_ASSISTED`: system can apply low-risk reversible actions automatically under policy.
- `HUMAN_REVIEW_REQUIRED`: system can suggest, but cannot apply until human approval.
- `HUMAN_ONLY`: no implicit AI actions, no auto-apply, no autonomous background mutations.

Required controls:
- global workspace-level mode switch,
- per-session override,
- per-surface override (canvas, builder, preview),
- emergency kill switch to force `HUMAN_ONLY` everywhere.

All policies and mode switches must emit structured events and be visible in UI.

---

## 1. Document Review and Validation

### 1.1 Verification Summary

All three documents were reviewed against the actual repo. The findings below correct inaccuracies and surface integration gaps.

#### Canvas Platform document — findings

| Claim | Status | Correction |
|---|---|---|
| `@ghatana/flow-canvas` is a nested private package | **Confirmed** — `platform/typescript/canvas/flow-canvas/` exists with own `package.json` | No correction needed; migration task required |
| Deprecated facades exist (canvas-core, canvas-react, canvas-plugins, canvas-tools, canvas-chrome) | **Confirmed** — all 5 directories exist under `platform/typescript/` | Deprecation/absorption tasks required |
| `@ghatana/canvas` exports `./core`, `./hybrid`, `./plugins`, `./tools`, `./chrome` as stable | **Confirmed and problematic** — current `package.json` exposes these; docs say they should not be public | Tighten exports map |
| Canvas AI types exist in `src/ai/types.ts` | **Confirmed** as adapter contract, not AI implementation | Good design; needs integration with shared AI visibility primitives |
| Canvas collaboration types exist in `src/collaboration/collab-types.ts` | **Confirmed** as transport-agnostic adapter | Good design; keep |
| YAPPC canvas actions at `src/actions/phase-actions.ts`, `role-actions.ts` | **Confirmed** — these are YAPPC-specific and should NOT be in platform canvas | Must be moved to product-local code |

#### Design System Generator document — findings

| Claim | Status | Correction |
|---|---|---|
| `@ghatana/tokens` exists | **Confirmed** — `platform/typescript/tokens/src/` has full token categories | No correction |
| `@ghatana/theme` exists | **Confirmed** — `platform/typescript/theme/src/` has brand presets, schema, provider | No correction |
| `@ghatana/design-system` behaves as both component library and facade | **Confirmed** — `src/` contains atoms, molecules, organisms, PLUS audit, privacy, security, voice, nlp namespaces | Boundary fixes required |
| `@ghatana/primitives` does not exist | **Confirmed** — no `platform/typescript/primitives/` directory | Must create |
| `@ghatana/ui` does not exist | **Confirmed** — referenced in docs but actual package is `design-system` | Must create canonical `@ghatana/ui` from design-system internals |
| `@ghatana/ds-schema` does not exist | **Confirmed** — no such package | Must create |
| `@ghatana/ds-registry` does not exist | **Confirmed** — no such package | Must create |
| Token model is NOT yet DTCG-aligned | **Confirmed** — `tokens/src/` has categories but no `$type`/`$value`/`$extensions` DTCG schema | Migration task required |

#### Live UI Builder document — findings

| Claim | Status | Correction |
|---|---|---|
| YAPPC `PageDesigner` exists | **Confirmed** — `products/yappc/frontend/web/src/components/canvas/page/PageDesigner.tsx` | Schemas are too narrow and product-local; uses `@ts-nocheck` |
| YAPPC `exportJSX.ts` is React-only and one-way | **Confirmed** | Must align to `CodeProjection` model |
| `live-preview-server` exists as product-local tool | **Confirmed** — `products/yappc/tools/live-preview-server/src/index.ts` — WebSocket-based, product-local | Foundation for `@ghatana/ui-builder/preview` |
| `canvas/src/ui-builder/index.ts` is element-oriented | **Confirmed** — operates on `CanvasElement` freeform elements, not `ComponentContract` design-system model | Must be replaced/elevated |
| `canvas/src/react/LiveReactOverlay.tsx` exists | **Confirmed** | Primitive for builder canvas projection |
| `@ghatana/ui-builder` package does not exist | **Confirmed** — no such package | Must create |
| `@ghatana/code-editor` exists | **Confirmed** — `platform/typescript/code-editor/src/` with Monaco, AST, LSP, bindings | Reuse as-is |

### 1.2 Cross-Document Conflicts Resolved

**Conflict 1 — AI visibility primitives home**  
Canvas doc and builder doc both define `OperationStatus`, `ReviewRequiredBanner`, `ChangeSummary`, `ActivityTimeline` as platform primitives. These must live in one place.  
**Resolution**: They belong in `@ghatana/design-system` (closest to user-facing UI, confirmed by DS generator doc section 9.4.2). Canvas and builder import them from there.

**Conflict 2 — `ui-builder/index.ts` in canvas vs separate `@ghatana/ui-builder`**  
Current `platform/typescript/canvas/src/ui-builder/index.ts` is element-oriented and inside canvas. Builder doc says builder should be separate from canvas.  
**Resolution**: The current `canvas/src/ui-builder/` is an implementation detail for freeform canvas composition and should be renamed to `canvas/src/internal/freeform-composer/` and not promoted. A separate `platform/typescript/ui-builder` package (`@ghatana/ui-builder`) is the design-system-contract-oriented builder orchestration layer.

**Conflict 3 — `@ghatana/ui` vs `@ghatana/design-system`**  
DS generator doc proposes `@ghatana/ui` as the reusable component layer. The current canonical package is `@ghatana/design-system`. Introducing both as public packages causes consumer confusion.  
**Resolution**: `@ghatana/ui` becomes an **internal** workspace package containing component implementations. The public consumer-facing package remains `@ghatana/design-system` as its public facade. This matches the four-layer model.

**Conflict 4 — YAPPC canvas actions in `platform/typescript/canvas/src/actions/`**  
Files `phase-actions.ts` and `role-actions.ts` encode YAPPC lifecycle concepts inside platform canvas.  
**Resolution**: Move to `products/yappc/frontend/web/src/components/canvas/` and replace canvas-level action wiring with product-controlled plugin registration.

**Conflict 5 — Duplicate telemetry event schemas**  
All three domains (canvas, design-system, builder) need structured event emission. Without a shared package, three parallel telemetry schemas will emerge.  
**Resolution**: Introduce `platform/typescript/platform-events` (`@ghatana/platform-events`) as the canonical cross-cutting telemetry schema package. All three domains extend from it.

### 1.3 Additional Gaps Found In This Review

The unified plan is directionally correct, but the review surfaced four execution gaps that would otherwise block a real end-to-end slice.

**Gap 1 — Thin-slice sequencing conflicted with the phase model**  
Phase 0 said "Zero product code changes," while Task 0.6 required YAPPC product integration and depended on packages that do not exist until Phases 1–3.  
**Resolution**: Treat Task 0.6 as **Milestone M1**. It validates the Phase 0 contract, but it executes only after the required Phase 1, Phase 2, and Phase 3 foundations exist.

**Gap 2 — Direct code edits had no explicit reconciliation engine**  
The synchronization model requires code edits to parse, reconcile, and update `BuilderDocument`, but the phased tasks only covered forward code generation.  
**Resolution**: Add Task 3.12 for reverse code import, ownership-aware reconciliation, conflict handling, and approval-aware merge behavior.

**Gap 3 — The slice had no persistence, autosave, or session recovery contract**  
Without save/load/version semantics, the thin slice would be a demo, not a usable product capability.  
**Resolution**: Add Task 3.13 for `BuilderDocument` persistence, autosave, snapshot history, and rollback/session restore primitives; Milestone M1 must wire these into YAPPC.

**Gap 4 — “Consistent and simple” authoring was implied but not enforced as a product requirement**  
The plan described the constituent capabilities but did not require a single entrypoint where users can move between design system, page designer, canvas, preview, and code without losing context.  
**Resolution**: Strengthen Milestone M1 and Phase 5 consistency requirements to require one stable authoring route per product, shared status/autonomy controls, and refresh-safe recovery.

---

## 1B. Design System Engine Hierarchy

The design system platform is not a flat collection of packages. It has a layered engine hierarchy. Every task in Phase 1 and Phase 4 must respect this layer order: a layer may only depend on layers below it, never above.

```
Layer 10  AI/ML assistance and auditing layer
Layer  9  Docs / testing / governance toolchain
Layer  8  Template library
Layer  7  Pattern library
Layer  6  Component implementations
Layer  5  Component recipe engine
Layer  4  Primitive runtime
Layer  3  Theme model
Layer  2  Token model
Layer  1  Foundations (type scales, color ramps, spacing scales, motion, elevation, radii, iconography)
```

**Practical package mapping**:
- Layers 1–2 → `@ghatana/tokens` (DTCG-aligned)
- Layer 3 → `@ghatana/theme`, `@ghatana/ds-schema` (theme types)
- Layer 4 → `@ghatana/primitives` (internal)
- Layer 5 → component contracts in `@ghatana/ds-schema` + `@ghatana/ds-registry`
- Layer 6 → `@ghatana/ui` (internal) → `@ghatana/design-system` (public facade)
- Layer 7 → `@ghatana/patterns` (internal)
- Layer 8 → `@ghatana/ds-generator` (template materialization)
- Layer 9 → `@ghatana/ds-cli`, `@ghatana/ds-governance`, `@ghatana/accessibility-audit`
- Layer 10 → AI hooks in each layer + UI in design-system + AI Review Console shared app

---

## 1C. Four-Layer Synchronization Model

The builder and canvas maintain **four synchronized representations** of the same UI artifact. No single representation is the canonical source of truth — the design-system model and builder document are canonical; the visual and executable representations are projections.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  1. Design-System Representation                                            │
│     DesignSystemModel: tokens, themes, component contracts, recipes, a11y  │
│     Source package: @ghatana/design-system + @ghatana/tokens                │
├─────────────────────────────────────────────────────────────────────────────┤
│  2. Builder Representation                                                  │
│     BuilderDocument (tree) + ConfiguratorSchema (property forms)           │
│     Source package: @ghatana/ui-builder                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│  3. Visual Representation                                                   │
│     SceneProjection: tree editor, artboard, responsive views, canvas        │
│     Source package: @ghatana/canvas (projection only)                       │
├─────────────────────────────────────────────────────────────────────────────┤
│  4. Executable/Code Representation                                          │
│     RuntimeProjection (live preview) + CodeProjection (visible source)     │
│     Source packages: @ghatana/ui-builder/react, /web, /preview              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Synchronization rules:**
- Only representations 1 and 2 are canonical. Representations 3 and 4 are derived projections.
- When representation 2 changes, representations 3 and 4 must update (via projection).
- When representation 4 (code) is edited directly, the system must parse, reconcile, and update representation 2.
- When representation 1 changes (token edit, component recipe change), representations 2, 3, and 4 must all re-validate.
- Sync state must be user-visible at all times (Task 0.2 covers the UI for this).

**Key implication**: Canvas is a **projection**, not the canonical builder. A canvas repositioning operation updates `BuilderDocument`, which then re-projects to canvas and code — not the other way around.

---

## 2. Shared Cross-Cutting Platform Concerns

These six concerns appear identically specified in all three documents. They need **one canonical home** — not three parallel implementations.

### 2.1 Observability / O11y

**Canonical home**: `platform/typescript/platform-events` → `@ghatana/platform-events`

What it must provide:
- Base structured event interface: `PlatformEvent<T>` with correlation ID, actor, source, session ID, and timestamp
- Event families as namespace objects: `CanvasEvents`, `BuilderEvents`, `DesignSystemEvents`
- OTel-compatible span and trace types
- Metric schema types (counters, histograms, gauges)
- Audit record interface with actor, action, changed, rollback fields
- TypeScript `const` assertions for event name registries

**Consumer pattern** (all three domains):
```ts
import type { PlatformEvent, CanvasEvents } from '@ghatana/platform-events';
```

**Required canvas event families** (all must be emittable — these are mandatory, not aspirational):
```
canvas.viewport.changed
canvas.selection.changed
canvas.node.created           canvas.node.updated           canvas.node.deleted
canvas.edge.created           canvas.edge.updated           canvas.edge.deleted
canvas.layout.applied
canvas.import.completed       canvas.export.completed
canvas.render.failed
canvas.performance.sampled
canvas.ai.suggestion.shown    canvas.ai.suggestion.accepted  canvas.ai.suggestion.rejected
canvas.ai.action.applied
canvas.ai.review.requested    canvas.ai.review.approved      canvas.ai.review.rejected
canvas.ai.override.invoked
canvas.collaboration.peer.joined  canvas.collaboration.peer.left
canvas.collaboration.conflict.detected  canvas.collaboration.conflict.resolved
```

**Required builder event families** (all must be emittable):
```
builder.document.loaded
builder.component.inserted    builder.component.moved        builder.component.resized
builder.component.configured
builder.pattern.applied
builder.preview.started       builder.preview.updated        builder.preview.failed
builder.codegen.completed     builder.codegen.failed
builder.import.started        builder.import.completed       builder.import.review_required
builder.ai.suggestion.shown   builder.ai.suggestion.accepted  builder.ai.suggestion.rejected
builder.ai.action.applied
builder.review.requested      builder.review.completed
builder.sync.started          builder.sync.completed         builder.sync.conflict
builder.code.edited           builder.code.ownership.changed
```

**Required design-system event families**:
```
ds.token.created              ds.token.updated               ds.token.deprecated
ds.component.registered       ds.component.contract.validated
ds.theme.changed              ds.preset.applied
ds.governance.violation.detected
ds.ai.suggestion.shown        ds.ai.fix.applied
ds.audit.completed
```

### 2.2 AI/ML Visibility Contract

**Canonical home**: `platform/typescript/platform-events` → `@ghatana/platform-events` (types) + `@ghatana/design-system` (AI UX UI primitives)

#### What `@ghatana/platform-events` must provide for AI

Types:
- `AISuggestion` canonical type (currently duplicated in canvas `ai/types.ts` and builder doc)
- `AIAutonomyLevel` enum: `AUTONOMOUS | ASSISTED | SUPERVISED | MANUAL`
- `AIApprovalState` enum: `PENDING | APPROVED | REJECTED | BYPASSED`
- `AIOperationEvent` base type with confidence, actor, reasoning, policy, affected regions
- `AIVisibilityContract` interface with all mandatory fields:

```ts
export interface AIVisibilityContract {
  readonly operationState: 'idle' | 'running' | 'completed' | 'failed';
  readonly operationLabel: string;                          // human-readable: "Applying layout suggestion"
  readonly suggestedChanges: readonly AIChangeDescriptor[];
  readonly appliedChanges: readonly AIChangeDescriptor[];
  readonly pendingChanges: readonly AIChangeDescriptor[];   // applied but awaiting review
  readonly confidenceBand: { readonly low: number; readonly high: number };
  readonly rationale: string;                               // why the AI acted
  readonly evidence: readonly string[];                     // provenance refs / citations
  readonly approvalState: AIApprovalState;
  readonly reviewRequired: boolean;                         // policy-gated
  readonly rollbackAvailable: boolean;
  readonly overrideAvailable: boolean;
  readonly autonomyLevel: AIAutonomyLevel;
  readonly correlationId: CorrelationId;                    // links to audit record
  readonly triggeredBy: 'explicit' | 'implicit';           // user-initiated or system-initiated
}

export interface AIChangeDescriptor {
  readonly region: string;       // component ID, token name, node ID, etc.
  readonly summary: string;      // human-readable
  readonly kind: AIChangeKind;   // 'insert' | 'update' | 'delete' | 'reorder' | 'suggest'
  readonly diff?: string;        // structured diff for code regions
}
```

Policies:
- `AIPolicy` interface: autonomy threshold, approval requirement rules, rollback window, data-use constraints
- `AutonomyThreshold` type: confidence + risk level → action allowed or blocked
- `ApprovalRequirement`: by action kind, cost, data classification, or external visibility

#### What `@ghatana/design-system` must provide for AI UX

Required UI primitives — these must be present in all surfaces to make AI actions legible:

**Atoms:**
- `OperationStatus` — visible running/completed/failed/paused badge with label and correlation ID
- `ConfidenceBadge` — numeric confidence with semantic color band (low/med/high)
- `AILabel` — marks content or region as AI-generated
- `DataUseNotice` — privacy disclosure for AI data use
- `ToolUseDisclosure` — which tool or capability was invoked
- `SyncStatusIndicator` — shows whether a representation is in sync, syncing, or conflicted

**Molecules:**
- `ReviewRequiredBanner` — action requires human review, with approve/reject controls
- `ChangeSummary` — structured diff summary of applied changes with region links
- `ActivityTimeline` — chronological log of AI + user + system actions with context
- `CitationBlock` — AI evidence and provenance with links
- `EvidencePanel` — detailed evidence panel with confidence reasoning
- `ApprovalControls` — approve/reject/override action bar with keyboard shortcuts
- `GhostSuggestionOverlay` — semi-transparent suggested state over current UI state
- `AIAssistantLoop` — prompt input + suggestion display + acceptance controls
- `AIReviewPanel` — `ReviewRequiredBanner` + `ChangeSummary` + `ApprovalControls` + `ActivityTimeline`

**Canvas-specific (stays in `@ghatana/canvas/ai`):**
- `GhostSuggestionLayer` — ghost nodes/edges representing pending suggestions
- `AnomalyHighlight` — overlays flagging detected structural anomalies
- `NextActionHint` — contextual proactive next-step prompt

### 2.3 Security / Trust

**Canonical home**: `platform/typescript/platform-events` → `@ghatana/platform-events` (types)

What it must provide:
- `TrustLevel` enum: `TRUSTED_WORKSPACE | GENERATED_TRUSTED | IMPORTED_REVIEW_REQUIRED | UNTRUSTED`
- `SecurityPolicy` interface: sandbox level, allowed resource types, inline-script restrictions, CSP directives
- `PreviewSecurityProfile` with iframe sandbox flags and capability grants
- `SecurityMetadata` for component contracts

### 2.4 Privacy Classification

**Canonical home**: `platform/typescript/platform-events` → `@ghatana/platform-events` (types)

What it must provide:
- `DataClassification` enum: `PUBLIC | INTERNAL | SENSITIVE | CREDENTIALS | REGULATED`
- `PrivacyPolicy` interface: data minimization flags, redaction rules, external-use consent requirements
- `PrivacyMetadata` for component contracts and builder documents

### 2.5 Accessibility Contracts

**Canonical home**: `platform/typescript/accessibility` → `@ghatana/accessibility`

What it must provide (extend to cover builder and canvas needs):
- `A11yContract` interface per component: role, keyboard model, focus semantics, live-region behavior
- `KeyboardModel` typed union of interaction patterns
- `FocusSemantics` interface with restoration and trap rules
- `ReducedMotionPolicy` type
- Accessibility assertion helpers (currently in `@ghatana/accessibility-audit`, expose through `@ghatana/design-system/testing`)

### 2.6 User Visibility Contract

**Canonical home**: `platform/typescript/platform-events` → `@ghatana/platform-events` (types) + `@ghatana/design-system` (UI primitives)

What it must provide:
- `VisibilityContract` interface: what the system is doing, what changed, what is suggested vs applied, what requires user action, what can be undone, what is still syncing
- `ProvenanceRecord`: source, author, generator version, migration lineage, triggeredBy (explicit/implicit)
- `OperationRecord`: actor, trigger, risk level, review state, rollback semantics, telemetry ref
- `SyncStatus`: the sync state of each of the four synchronized representations (§1C)
- `OwnershipRegion`: which regions of code/canvas are generated vs user-authored vs protected

**Operator visibility** (must also be surfaced, not only user visibility):

In addition to user-facing visibility, platform telemetry must allow operators to answer:
- canvas load time and first-interactive time by product and session
- node/edge count distribution across active sessions
- AI suggestion acceptance vs rejection vs override rates per surface
- where failures cluster (canvas, preview, codegen, import)
- which products or surfaces generate the most override/review events
- which autonomous actions are most frequently rolled back

These are not UI tasks — they are telemetry coverage requirements that must be emitted as structured events from the respective packages.

---

## 2B. Canvas Capability Model

All six capability groups must be explicitly planned and implemented. The canvas platform is not complete until all six are available through the public subpath surface.

### Capability Group A — Foundation
- document model (`CanvasDocument`)
- viewport model (`CanvasViewportState`) with fit/pan/zoom/center
- selection model (`CanvasSelectionState`) including multi-select
- undo/redo history (`CanvasHistory`) with operation batching
- keyboard shortcuts (configurable, product-extendable)
- clipboard (cut/copy/paste nodes and subgraphs)
- import/export (serialization round-trip)
- accessibility primitives (keyboard nav, focus, ARIA)
- telemetry primitives (hook into `CanvasTelemetry`)

### Capability Group B — Graph and Topology
- node/edge rendering with custom node and edge types
- layout integration (hierarchical, force-directed, radial, dagre, elk)
- save/restore (`CanvasDocument` serialization)
- read-only and editable modes
- multi-surface graph overlays (topology metrics, health indicators, status)
- topology health/status rendering alongside graph nodes

### Capability Group C — Hybrid and Freeform
- freeform elements (the renamed `freeform-composer`)
- whiteboard/sketch layer
- frame/grouping/multi-select grouping
- embedded content (video, images, rich blocks where justified)
- hybrid graph + freeform coordinate systems (HybridCanvas)

### Capability Group D — AI-Native
The canvas platform must support all of these AI capabilities natively — not through product-local extensions:
- **Ghost suggestions**: `GhostSuggestionLayer` showing pending edge/node suggestions before acceptance
- **Structural validation**: validate canvas structure against schema or constraint rules
- **Auto-layout recommendations**: AI-suggested layout changes with confidence + one-click apply
- **Semantic clustering**: group related nodes automatically, visible as suggestion
- **Next-best actions**: contextual action hints at canvas level
- **Anomaly highlighting**: `AnomalyHighlight` overlay on nodes/edges with detected issues
- **Code/design generation handoff**: emit projected code from canvas state
- **Approval and review workflows**: integrate `AIVisibilityContract` at canvas level

Requirement: every implicit AI canvas change must emit a `canvas.ai.*` event and produce an `AIVisibilityContract` that is surfaceable through the public `@ghatana/canvas/ai` subpath.

### Capability Group E — Collaboration
- multi-user shared state via Yjs (awareness separate from shared document)
- presence and awareness (cursor position, selection)
- cursor presence rendering
- selection presence rendering
- offline-safe synchronization where required
- policy-aware collaboration data retention (emit `canvas.collaboration.*` events)

### Capability Group F — Observability
- operation metrics (every capability group must emit its required events)
- render latency and frame time metrics
- interaction latency metrics
- trace spans for long-running operations (layout, import/export, AI tasks, sync)
- audit records for autonomous changes
- override and approval telemetry
- correlation IDs connecting AI actions to user-visible state

---

### 3.1 What stays and what changes

```
platform/typescript/
  ─────────────────────────────────────────────────────────────────────
  EXISTING → KEEP AS-IS (may evolve incrementally)
  ─────────────────────────────────────────────────────────────────────
  tokens/           @ghatana/tokens          Token values, transforms, CSS vars (add DTCG schema)
  theme/            @ghatana/theme           Theme runtime, brand presets, provider
  accessibility/    @ghatana/accessibility   A11y contracts + hooks (extend with new contract types)
  accessibility-audit/ @ghatana/accessibility-audit  Audit tooling (unchanged)
  code-editor/      @ghatana/code-editor     Monaco integration (unchanged)
  api/              @ghatana/api             API client + telemetry middleware (unchanged)
  realtime/         @ghatana/realtime        Real-time transport (unchanged)
  sso-client/       @ghatana/sso-client      SSO (unchanged)
  charts/           @ghatana/charts          Charts (unchanged)
  i18n/             @ghatana/i18n            i18n (unchanged)
  forms/            @ghatana/forms           Form utilities (unchanged)
  data-grid/        @ghatana/data-grid       Data grid (unchanged)
  testing/          @ghatana/platform-testing Testing utilities (unchanged)
  foundation/platform-utils/ @ghatana/platform-utils  Utils (unchanged)

  ─────────────────────────────────────────────────────────────────────
  EXISTING → EVOLVES / REFACTORED
  ─────────────────────────────────────────────────────────────────────
  design-system/    @ghatana/design-system   Facade (boundary tightened: remove audit/privacy/security/voice/nlp namespaces from root)
  canvas/           @ghatana/canvas          Harden exports map; restructure src/ into public/ + internal/;
                                             absorb flow-canvas; move YAPPC-specific actions out

  ─────────────────────────────────────────────────────────────────────
  EXISTING → ABSORB INTO CANVAS (deprecated facades)
  ─────────────────────────────────────────────────────────────────────
  canvas-core/      (deprecated)             Remove; migrate any remaining consumers to @ghatana/canvas
  canvas-react/     (deprecated)             Remove; migrate to @ghatana/canvas
  canvas-plugins/   (deprecated)             Remove; migrate to @ghatana/canvas
  canvas-tools/     (deprecated)             Remove; migrate to @ghatana/canvas
  canvas-chrome/    (deprecated)             Remove; migrate to @ghatana/canvas
  canvas/flow-canvas/ (nested private)       Absorb into @ghatana/canvas/flow subpath; remove nested package

  ─────────────────────────────────────────────────────────────────────
  NEW PACKAGES TO CREATE
  ─────────────────────────────────────────────────────────────────────
  platform-events/  @ghatana/platform-events Full event taxonomy (canvas+builder+ds), AIVisibilityContract,
                                             AIPolicy, TrustLevel, DataClassification, VisibilityContract,
                                             ProvenanceRecord, OperationRecord, SyncStatus, SpanRef, AuditRecord
  primitives/       @ghatana/primitives      Internal: layout + interaction primitives (Box, Stack, Inline, Grid, etc.)
  ui/               @ghatana/ui              Internal: reusable generic components (extracted from design-system impl)
  patterns/         @ghatana/patterns        Internal: recurring workflow compositions including AI UX patterns
  ds-schema/        @ghatana/ds-schema       DTCG-aligned token/component/theme schema types + JSON validators + DesignSystemModel
  ds-registry/      @ghatana/ds-registry     Component/token/theme registry with dep graph, deprecation API
  ds-governance/    @ghatana/ds-governance   Policy engine, naming rules, quality gates, AI-powered audit
  ds-cli/           @ghatana/ds-cli          CLI: init, validate, build-tokens, audit, migrate, release
  ds-generator/     @ghatana/ds-generator    Preset materialization, brand brief → AI-assisted token suggestions
  ui-builder/       @ghatana/ui-builder      Builder orchestration: DesignSystemModel (binding), ComponentContract,
                                             ConfiguratorSchema, BuilderDocument, BuilderOperation,
                                             SceneProjection, RuntimeProjection, CodeProjection
                    /react                   React renderer adapter, React codegen, React preview integration
                    /web                     Vanilla DOM/TS/HTML renderer, custom-element integration, vanilla codegen
                    /preview                 Preview host protocol, sandbox profiles, session lifecycle
                    /testing                 Test fixtures, preview harness, a11y assertions, round-trip helpers
```

### 3.2 Canonical dependency graph

```
                           @ghatana/platform-events
                           │  (full event taxonomy, AI contract, security, privacy, visibility)
                           │
              ┌────────────┴───────────────────────────────┐
              ▼                                             ▼
  @ghatana/tokens           @ghatana/accessibility
       │                           │
       ▼                           ▼
  @ghatana/theme      @ghatana/accessibility-audit
       │
       ▼
  @ghatana/primitives  ←─── internal only, not a public consumer import
       │
       ▼
  @ghatana/ui          ←─── internal only
       │
       ├──────────────────────────────────────┐
       ▼                                      ▼
  @ghatana/patterns    @ghatana/ds-schema  @ghatana/ds-registry
       │                   │                   │
       │                   └─────────┬─────────┘
       ▼                             ▼
  @ghatana/design-system      @ghatana/ds-schema provides DesignSystemModel type
  (public facade)              consumed by @ghatana/ui-builder as canonical binding
       │
       ├────────────────────────────────────────────┐
       │                                            │
       ▼                                            ▼
  @ghatana/canvas                            @ghatana/ui-builder
  (spatial editing surface)                   (builder orchestration)
  all 6 capability groups                     DesignSystemModel binding
  public subpaths:                            public subpaths:
    /flow /topology /collaboration              /react /web /preview /testing
    /ai /export /testing
       │                                            │
       └─────────────┬──────────────────────────────┘
                     ▼
  products/yappc, products/data-cloud/planes/action, products/data-cloud, etc.
```

**Rules enforced by dependency graph:**
1. `platform-events` has no Ghatana dependencies (only platform utilities and types)
2. `primitives` and `ui` are internal; they do not import from `design-system`
3. `design-system` is the top of the shared implementation graph; it does not import from product code
4. `canvas` and `ui-builder` both import from `design-system` for semantic contracts and from `platform-events` for AI/telemetry types
5. `ui-builder` may import from `canvas` for scene projection; `canvas` must NOT import from `ui-builder`
6. `ui-builder` binds `DesignSystemModel` from `@ghatana/ds-schema` — it does NOT duplicate component metadata
7. Products import from `design-system`, `canvas`, and `ui-builder`; they do not import implementation packages directly (`primitives`, `ui`, `patterns`)

---

## 4. Phased Implementation Plan

> Each task specifies: what to do, where (file/package paths), why, and what tests to add.  
> Tasks within a phase that are independent can run in parallel.  
> Tasks with ← predecessors are sequential.

---

### Phase 0 — Cross-Cutting Foundation  
**Duration**: 3 weeks  
**Goal**: One canonical place for shared types and UI primitives that all three platform layers depend on.  
**Zero broad product migration changes in this phase.** The only permitted product touch is the thin-slice milestone after its dependencies are ready.

---

#### Task 0.1 — Create `@ghatana/platform-events` package
**Path**: `platform/typescript/platform-events/`  
**Priority**: Blocker for everything else  
**Why**: Prevents three parallel telemetry/AI/security schemas emerging across canvas, design-system, and builder.

Create:
```
platform/typescript/platform-events/
  src/
    index.ts                    (barrel: re-exports all public types)
    events/
      base.ts                   (PlatformEvent<T>, EventSource, CorrelationId)
      canvas-events.ts          (CanvasEvents namespace, all canvas.* event names and payloads)
      builder-events.ts         (BuilderEvents namespace, all builder.* event names and payloads)
      design-system-events.ts   (DesignSystemEvents namespace, all ds.* event names and payloads)
    ai/
      types.ts                  (AISuggestion, AIAutonomyLevel, AIApprovalState, AIOperationEvent, AIVisibilityContract)
      policy.ts                 (AIPolicy, AutonomyThreshold, ApprovalRequirement)
    security/
      types.ts                  (TrustLevel, SecurityPolicy, SecurityMetadata, PreviewSecurityProfile)
    privacy/
      types.ts                  (DataClassification, PrivacyPolicy, PrivacyMetadata)
    visibility/
      types.ts                  (VisibilityContract, ProvenanceRecord, OperationRecord, SyncStatus, OwnershipRegion)
    observability/
      types.ts                  (SpanRef, MetricSchema, AuditRecord, TraceContext, MetricFamily)
    events/
      canvas-events.ts          (all canvas.* event name consts + payload interfaces — full taxonomy from §2.1)
      builder-events.ts         (all builder.* event name consts + payload interfaces — full taxonomy from §2.1)
      design-system-events.ts   (all ds.* event name consts + payload interfaces — full taxonomy from §2.1)
  package.json                  (name: "@ghatana/platform-events", private: false)
  tsconfig.json                 (extends workspace base, strict: true)
  vitest.config.ts
```

Typed contracts to implement (strict TypeScript, no `any`):
```ts
// events/base.ts
export type CorrelationId = string & { readonly brand: unique symbol };
export type SessionId = string & { readonly brand: unique symbol };

export interface PlatformEvent<T = Record<string, unknown>> {
  readonly name: string;
  readonly correlationId: CorrelationId;
  readonly sessionId: SessionId;
  readonly timestamp: string;         // ISO 8601
  readonly source: EventSource;
  readonly actor: 'user' | 'ai' | 'system';   // who triggered this
  readonly triggeredBy: 'explicit' | 'implicit'; // user action or AI/background
  readonly payload: T;
}
export type EventSource = 'canvas' | 'builder' | 'design-system' | 'product';

// ai/types.ts — full AIVisibilityContract (see §2.2 for complete spec)
export type AIAutonomyLevel = 'AUTONOMOUS' | 'ASSISTED' | 'SUPERVISED' | 'MANUAL';
export type AIApprovalState = 'PENDING' | 'APPROVED' | 'REJECTED' | 'BYPASSED';
export type AIChangeKind = 'insert' | 'update' | 'delete' | 'reorder' | 'suggest';

export interface AIChangeDescriptor {
  readonly region: string;
  readonly summary: string;
  readonly kind: AIChangeKind;
  readonly diff?: string;
}

export interface AIVisibilityContract {
  readonly operationState: 'idle' | 'running' | 'completed' | 'failed';
  readonly operationLabel: string;
  readonly suggestedChanges: readonly AIChangeDescriptor[];
  readonly appliedChanges: readonly AIChangeDescriptor[];
  readonly pendingChanges: readonly AIChangeDescriptor[];
  readonly confidenceBand: { readonly low: number; readonly high: number };
  readonly rationale: string;
  readonly evidence: readonly string[];
  readonly approvalState: AIApprovalState;
  readonly reviewRequired: boolean;
  readonly rollbackAvailable: boolean;
  readonly overrideAvailable: boolean;
  readonly autonomyLevel: AIAutonomyLevel;
  readonly correlationId: CorrelationId;
  readonly triggeredBy: 'explicit' | 'implicit';
}

// visibility/types.ts
export interface SyncStatus {
  readonly designSystem: 'synced' | 'syncing' | 'conflict' | 'unknown';
  readonly builderDocument: 'synced' | 'syncing' | 'conflict' | 'unknown';
  readonly visualProjection: 'synced' | 'syncing' | 'stale' | 'unknown';
  readonly codeProjection: 'synced' | 'syncing' | 'conflict' | 'user-modified' | 'unknown';
}

// security/types.ts
export type TrustLevel =
  | 'TRUSTED_WORKSPACE'
  | 'GENERATED_TRUSTED'
  | 'IMPORTED_REVIEW_REQUIRED'
  | 'UNTRUSTED';
```

**Tests** (`src/__tests__/`):
- Type-level tests: ensure all event payloads satisfy `PlatformEvent<T>`
- Zod schema validation tests for each event family across all three event namespaces
- AI visibility contract completeness tests including `pendingChanges` and `triggeredBy`
- Security trust level exhaustiveness check
- `SyncStatus` exhaustiveness check — all 4 representations have distinct states
- Canvas event taxonomy completeness: all 23 required events from §2.1 are present
- Builder event taxonomy completeness: all 22 required events from §2.1 are present

---

#### Task 0.2 — Add AI UX visibility primitives to `@ghatana/design-system`
**Path**: `platform/typescript/design-system/src/atoms/` and `platform/typescript/design-system/src/molecules/`  
**Depends on**: Task 0.1 (for `AIVisibilityContract` type import)  
**Why**: Canvas doc, builder doc, and DS doc all reference the same primitives. One implementation.

Components to create (full list from §2.2):
```
platform/typescript/design-system/src/atoms/
  OperationStatus.tsx         (running/completed/failed/paused status badge with label + correlation ID)
  ConfidenceBadge.tsx         (numeric confidence with semantic color band: low/med/high)
  AILabel.tsx                 (marks content or region as AI-generated)
  SyncStatusIndicator.tsx     (shows whether a representation is synced, syncing, or conflicted)
  DataUseNotice.tsx           (privacy disclosure for AI data use)
  ToolUseDisclosure.tsx       (which tool or capability was invoked)

platform/typescript/design-system/src/molecules/
  ReviewRequiredBanner.tsx    (action requires human review, with approve/reject + keyboard shortcut)
  ChangeSummary.tsx           (structured diff summary with region links, triggeredBy label)
  ActivityTimeline.tsx        (chronological AI + user + system action log with actor icons)
  CitationBlock.tsx           (AI evidence and provenance with external links)
  EvidencePanel.tsx           (detailed evidence panel with confidence reasoning)
  ApprovalControls.tsx        (approve/reject/override action bar with keyboard shortcuts)
  GhostSuggestionOverlay.tsx  (semi-transparent suggested state overlay over current UI state)
  AIAssistantLoop.tsx         (prompt input + suggestion display + acceptance controls)
  AIReviewPanel.tsx           (ReviewRequiredBanner + ChangeSummary + ApprovalControls + ActivityTimeline)
```

Each component must:
- Accept props typed against `@ghatana/platform-events` AI types
- Show `triggeredBy: 'explicit' | 'implicit'` in `ActivityTimeline` entries
- Include `SyncStatus` prop on `SyncStatusIndicator` (the four-layer sync state from §1C)
- Follow WCAG AA: keyboard navigable, screen-reader announced, visible focus
- Export a typed props interface (no implicit any)
- Have co-located `__tests__/` with React Testing Library tests
- Be exported from `design-system/src/index.ts`

**Example OperationStatus props**:
```ts
interface OperationStatusProps {
  state: 'idle' | 'running' | 'completed' | 'failed';
  label: string;
  correlationId?: string;
  onDismiss?: () => void;
  className?: string;
}
```

---

#### Task 0.3 — Migrate `AISuggestion` type to `@ghatana/platform-events`
**Path**: `platform/typescript/canvas/src/ai/types.ts`  
**Depends on**: Task 0.1  
**Why**: `AISuggestion` is currently defined in canvas, but builder and design-system need the same type.

Steps:
1. Move `AISuggestion` and `AISuggestionKind` from `canvas/src/ai/types.ts` to `platform-events/src/ai/types.ts`
2. Update `canvas/src/ai/types.ts` to re-export from `@ghatana/platform-events`
3. Update `canvas/src/ai/ai-canvas-provider.tsx` imports
4. **No behavior change** — only type location changes

**Tests**: Existing canvas AI tests must still pass after import change.

---

#### Task 0.4 — Add `@ghatana/platform-events` to workspace `pnpm-workspace.yaml`
**Path**: `pnpm-workspace.yaml`  
**Why**: New package must be discoverable in the workspace.

```yaml
# Add under platform/typescript packages
packages:
  - platform/typescript/platform-events
```

Also add to `platform/typescript/package.json` workspace list if present, and update `build.config.json` to include the new package in build order (after `platform-utils`, before `design-system`).

---

#### Task 0.5 — Define and ship Human Control Plane (autonomy mode controls)
**Path**:
- `platform/typescript/platform-events/src/ai/policy.ts`
- `platform/typescript/design-system/src/atoms/`
- `platform/typescript/design-system/src/molecules/`
- `platform/typescript/canvas/src/public/react/`

**Why**: The plan already optimizes for minimal interruption. This task makes human-only execution a first-class runtime mode, not an emergency retrofit.

**Sequencing note**: Phase 0 ships the policy types, events, shared UI controls, and canvas enforcement. Builder enforcement is completed once `@ghatana/ui-builder` exists (Tasks 3.12, 3.13, and Milestone M1).

Deliverables:
1. Add `AutonomyExecutionMode` type: `AUTONOMOUS_ASSISTED | HUMAN_REVIEW_REQUIRED | HUMAN_ONLY`.
2. Add `autonomyMode.changed` and `autonomyMode.violation.blocked` events to `@ghatana/platform-events`.
3. Add `AutonomyModeToggle` and `HumanControlBanner` UI primitives in `@ghatana/design-system`.
4. Enforce policy in canvas immediately and in builder once `@ghatana/ui-builder` lands:
  - `HUMAN_ONLY`: block implicit AI actions and auto-apply paths.
  - `HUMAN_REVIEW_REQUIRED`: allow suggestions, require explicit approval to apply.
5. Add global emergency kill switch env flag and runtime config wiring.

**Tests**:
- Policy matrix tests for all 3 modes.
- Integration test proving implicit AI actions are blocked in `HUMAN_ONLY`.
- Audit event tests for mode switches and blocked actions.

---

#### Milestone M1 / Task 0.6 — Deliver first end-to-end thin slice (Canvas + Page Designer + Design System)
**Path**:
- `products/yappc/frontend/web/src/components/canvas/`
- `products/yappc/frontend/web/src/components/canvas/page/`
- `platform/typescript/design-system/`
- `platform/typescript/canvas/`
- `platform/typescript/ui-builder/`

**Why**: The full plan is broad; this task creates an early working vertical slice to de-risk integration and prove consistent UX/API shape before full migration.

**Depends on**: Tasks 1.7, 2.10, 3.7, 3.8, 3.11, 3.12, and 3.13.

Scope for the slice:
1. Use `@ghatana/design-system` components in a `BuilderDocument`-driven `PageDesigner` flow.
2. Project the same document to canvas artboard (`SceneProjection`) and to code (`CodeProjection`).
3. Demonstrate 4-layer sync status end-to-end with `SyncStatusIndicator`.
4. Demonstrate all 3 autonomy modes in the same flow (autonomous-assisted, review-required, human-only).
5. Add one scripted demo route in YAPPC showing create/edit/project/preview/rollback.
6. Make the route a single authoring shell: `Design System -> Page Designer -> Canvas -> Preview -> Code`, with shared status/autonomy controls visible in one place.
7. Back the route with save/load, autosave, version restore, and refresh-safe session recovery using the Phase 3 persistence contract.

Acceptance tests for thin slice:
- Playwright E2E: user can create a page from palette, edit on canvas, preview, inspect generated code, and rollback.
- Human-only E2E: same flow works with implicit AI fully disabled.
- Telemetry E2E: correlation IDs connect UI actions and emitted events across canvas, builder, and design-system.
- Session recovery E2E: refresh or reopen restores `BuilderDocument`, scene projection, code projection, autonomy mode, and pending review state.

---

### Phase 1 — Design System Foundation  
**Duration**: 5 weeks  
**Goal**: Clean boundaries for design-system stack. DTCG token schema. New schema/registry packages. Primitives separated from facade. AI UX primitives live in one place.  
**Prerequisite**: Phase 0 complete.

---

#### Task 1.1 — Audit and extract misplaced concerns from `@ghatana/design-system`
**Path**: `platform/typescript/design-system/src/`  
**Why**: Current package bundles atoms/molecules/organisms + audit + privacy + security + voice + nlp — violates single-responsibility and boundary rules.

Subdirectories to relocate or remove from public export:
- `src/audit/` — evaluate if these belong in `@ghatana/accessibility-audit` or stay internal to design-system
- `src/privacy/` — types should move to `@ghatana/platform-events`; UI components stay if they are generic user-facing
- `src/security/` — types move to `@ghatana/platform-events`; remove if implementation-only glue code
- `src/voice/` — evaluate: if this is a generic AI input mode component, keep in design-system; if YAPPC-specific, move to product
- `src/nlp/` — evaluate: if generic, keep; if product-specific, move to product

**Action per subdirectory**:
1. For each item, check whether it is used only by one product (→ move to product) or truly shared (→ keep or promote)
2. Move types to `@ghatana/platform-events` where they should be canonical cross-cutting types
3. Update all import paths across products
4. Remove from root `src/index.ts` export if relocated

**Tests**: Run full type-check after each move. Product builds must remain green.

---

#### Task 1.2 — Create `@ghatana/ds-schema` package
**Path**: `platform/typescript/ds-schema/`  
**Why**: Canonical types and JSON validators for the DTCG-aligned token model, component anatomy model, and design system governance schema. All tooling (generator, CLI, governance) depends on this.

Create:
```
platform/typescript/ds-schema/
  src/
    index.ts
    token/
      token-types.ts            (GhatanaToken, TokenCategory, TokenLayer, TokenLifecycle)
      token-schema.ts           (Zod schema for GhatanaToken, validates DTCG $type/$value/$extensions)
      token-validators.ts       (validateToken, validateTokenSet, validateTokenReference)
    component/
      component-types.ts        (GhatanaComponent, ComponentAnatomy, ComponentSlot, ComponentA11y)
      component-schema.ts       (Zod schema for component contract)
      component-validators.ts
    theme/
      theme-types.ts            (GhatanaTheme, ThemeDimension, ThemeLayer)
      theme-schema.ts
      theme-validators.ts
    pattern/
      pattern-types.ts
      pattern-schema.ts
    template/
      template-types.ts
      template-schema.ts
    compat/
      migration-types.ts        (SchemaVersion, BreakingChange, MigrationManifest)
      compat-validators.ts
  package.json                  (name: "@ghatana/ds-schema", private: false)
```

DTCG-aligned token type:
```ts
// token/token-types.ts
export type TokenCategory =
  | 'color' | 'typography' | 'spacing' | 'sizing' | 'radius'
  | 'border' | 'elevation' | 'opacity' | 'shadow' | 'motion-duration'
  | 'motion-easing' | 'z-index' | 'breakpoint' | 'icon-size'
  | 'stroke-width' | 'grid';

export type TokenLayer =
  | 'global' | 'semantic' | 'component' | 'mode' | 'brand' | 'density';

export interface GhatanaTokenExtensions {
  readonly layer: TokenLayer;
  readonly mode?: string;
  readonly brand?: string;
  readonly platform?: readonly string[];
  readonly deprecated: boolean;
  readonly introduced?: string;
  readonly removedAfter?: string;
  readonly replacement?: string;
}

export interface GhatanaToken {
  readonly id: string;
  readonly $type: TokenCategory;
  readonly $value: string | number;
  readonly description?: string;
  readonly $extensions: { readonly ghatana: GhatanaTokenExtensions };
}
```

**Tests** (`src/__tests__/`):
- Valid token parses correctly
- Invalid token fails schema validation with helpful messages
- Token reference (`{color.blue.600}`) resolves correctly
- Component anatomy schema validates Button, Input, Dialog
- Migration manifest validates forward-compatible changes
- Breaking change detection test

---

#### Task 1.3 — Create `@ghatana/ds-registry` package
**Path**: `platform/typescript/ds-registry/`  
**Why**: Centralized catalog of all components, tokens, themes, patterns, and templates. Generator, CLI, governance, and builder palette all read from this registry.

Create:
```
platform/typescript/ds-registry/
  src/
    index.ts
    registry.ts                 (DesignSystemRegistry class)
    component-registry.ts       (register, lookup, list, deprecate component entries)
    token-registry.ts           (register, lookup, list, deprecate token entries)
    theme-registry.ts           (register, lookup, brand presets, modes)
    pattern-registry.ts         (register, lookup, list patterns)
    dependency-graph.ts         (resolve dependency order, detect circular deps)
    deprecation.ts              (mark deprecated, list deprecated, compute replacement paths)
    compat.ts                   (compatibility check: can package A use package B version X)
  package.json                  (name: "@ghatana/ds-registry")
  tsconfig.json
  vitest.config.ts
```

**Tests**:
- Register component, retrieve by name, retrieve by category
- Circular dependency detection
- Deprecation lookup returns replacement path
- Compatibility check: same major version passes, cross-major fails appropriately

---

#### Task 1.4 — Harden `@ghatana/tokens` to DTCG schema
**Path**: `platform/typescript/tokens/src/`  
**Depends on**: Task 1.2 (`@ghatana/ds-schema` for `GhatanaToken` type)  
**Why**: Current token files use category-per-file TypeScript objects. They must pass DTCG schema validation and carry provenance metadata.

Steps per token file (e.g., `colors.ts`, `spacing.ts`, `typography.ts`):
1. Add DTCG `$type`, `$value`, `$extensions.ghatana` fields to each exported token constant
2. Add lifecycle fields: `introduced`, `deprecated` (false by default), `platform: ['web']`
3. Add `validation.ts` Zod validation pass using `@ghatana/ds-schema` validators
4. Add `registry.ts` self-registration using `@ghatana/ds-registry`

**Tests**: 
- All existing token files pass `GhatanaToken` schema validation
- CSS variable generation still produces correct output
- No consumer product builds break (run YAPPC + AEP + data-cloud type-checks)

---

#### Task 1.5 — Create `@ghatana/primitives` package (internal)
**Path**: `platform/typescript/primitives/`  
**Why**: Low-level layout and interaction primitives currently live inside `design-system`. They need their own package so the dependency graph flows downward. This is an **internal** package — not directly imported by product teams.

Create primitives (token-driven, no hardcoded values):
```
platform/typescript/primitives/src/
  index.ts
  layout/
    Box.tsx                     (spacing, display, flex, grid props mapped to design tokens)
    Stack.tsx                   (vertical stack with gap tokens)
    Inline.tsx                  (horizontal inline stack with gap tokens)
    Grid.tsx                    (CSS grid with token-mapped gap/columns)
    Flex.tsx                    (flexbox with token-mapped props)
    Container.tsx               (max-width constrained, responsive)
    Surface.tsx                 (background surface with elevation token)
  typography/
    Text.tsx                    (semantic text with typography token variants)
    Heading.tsx                 (heading levels h1–h6 with token variants)
    Anchor.tsx                  (accessible link primitive)
  interaction/
    Pressable.tsx               (accessible click/keyboard trigger primitive)
    FocusRing.tsx               (visible focus ring using token ring-width/color)
    VisuallyHidden.tsx          (screen-reader visible, visually hidden)
  utility/
    Portal.tsx                  (React portal for overlays)
    ScrollArea.tsx              (scrollable container with overflow control)
    Separator.tsx               (horizontal/vertical divider)
    AspectRatio.tsx             (aspect ratio constraint wrapper)
    Slot.tsx                    (Radix-style composition slot)
  Icon.tsx                      (icon primitive wrapping SVG/icon font)
```

**Rules for each primitive**:
- No hardcoded CSS values (use design token CSS variables)
- Explicit prop types — no implicit `any`
- Forward ref where applicable
- Accessibility props: `role`, `aria-*` pass-through
- Co-located `__tests__/` with RTL tests + accessibility assertions

---

#### Task 1.6 — Create `@ghatana/ui` package (internal)
**Path**: `platform/typescript/ui/`  
**Depends on**: Task 1.5 (`primitives`), Task 1.4 (`tokens`)  
**Why**: Generic reusable components extracted from `design-system` implementation. This is an **internal** package. `@ghatana/design-system` is the public facade that re-exports from here.

Migration strategy:
1. Create `platform/typescript/ui/src/` with same component taxonomy (atoms, molecules, organisms)
2. Move component implementations from `design-system/src/atoms/`, `molecules/`, `organisms/` to `ui/src/`
3. Update `design-system/src/index.ts` to re-export from `@ghatana/ui`
4. No product code changes needed (they still import from `@ghatana/design-system`)

**Do NOT break the public API** of `@ghatana/design-system`. This is an internal restructuring.

**Tests**: All existing `design-system/__tests__/` must still pass. Run full product type-check.

---

#### Task 1.7 — Add component contract metadata to design-system components
**Path**: `platform/typescript/ui/src/` (after Task 1.6)  
**Depends on**: Task 1.2 (`@ghatana/ds-schema` for `GhatanaComponent` type), Task 1.6  
**Why**: Builder palette, configurator forms, Storybook controls, and code generation all derive from component contracts. Without contracts, the builder cannot be driven by metadata.

For each public component, create a co-located `*.contract.ts` file:
```ts
// platform/typescript/ui/src/atoms/Button.contract.ts
import type { GhatanaComponent } from '@ghatana/ds-schema';

export const ButtonContract: GhatanaComponent = {
  name: 'Button',
  category: 'input',
  anatomy: ['root', 'label', 'iconLeading', 'iconTrailing', 'spinner'],
  variants: ['primary', 'secondary', 'tertiary', 'danger', 'link'],
  sizes: ['xs', 'sm', 'md', 'lg'],
  states: ['default', 'hover', 'pressed', 'focusVisible', 'disabled', 'loading'],
  a11y: {
    role: 'button',
    keyboard: ['Enter', 'Space'],
    focus: 'required-visible',
  },
  tokens: ['button.primary.bg', 'button.primary.text', 'button.focus.ring'],
  security: { sandboxLevel: 'none', allowedResources: [] },
  privacy: { dataClassification: 'PUBLIC' },
  telemetryHooks: ['click', 'focus', 'blur'],
  codegen: { react: true, vanilla: true },
  migration: { introduced: '1.0.0', status: 'stable' },
};
```

Priority components for Phase 1 (builder-safe subset):
- Button, IconButton, Input, TextArea, Select, Checkbox, Radio, Switch
- Card, Badge, Avatar, Spinner, Skeleton, Tooltip, Alert, Banner, Toast
- Dialog, Drawer, Popover, Tabs, Menu, Breadcrumbs, Pagination
- Table, List, Form, Field

**Tests**: Each contract validates against `GhatanaComponent` Zod schema. Completeness test for required fields.

---

#### Task 1.8 — Create `@ghatana/patterns` package (internal)
**Path**: `platform/typescript/patterns/`  
**Depends on**: Task 1.6 (`@ghatana/ui`)  
**Why**: Recurring compositions that appear across multiple products (auth flow, dashboard, AI review panel, approval flow) need a canonical home.

Initial patterns to implement:
```
platform/typescript/patterns/src/
  index.ts
  auth/
    AuthForm.tsx
    AuthForm.contract.ts
  feedback/
    EmptyState.tsx
    LoadingState.tsx
    ErrorState.tsx
    SuccessState.tsx
  ai-ux/
    AIReviewPanel.tsx           (ReviewRequiredBanner + ChangeSummary + ApprovalControls)
    AIAssistantLoop.tsx         (prompt input + suggestion display + acceptance controls)
    ActivityTimeline.tsx        (imported from design-system; re-exported here as part of pattern)
  approvals/
    ApprovalWorkspace.tsx
  search/
    SearchFilterBar.tsx
  dashboard/
    DashboardGrid.tsx
    DashboardWidget.tsx
  settings/
    SettingsPage.tsx
    SettingsSection.tsx
```

**Tests**: Each pattern has RTL tests for happy path and keyboard accessibility.

---

#### Task 1.9 — Create `@ghatana/ds-governance` package
**Path**: `platform/typescript/ds-governance/`  
**Depends on**: Tasks 1.2, 1.3  
**Why**: Policy engine that enforces naming rules, detects duplicates, validates semantic contracts, and gates contributions.

Create:
```
platform/typescript/ds-governance/
  src/
    index.ts
    policies/
      naming-policy.ts          (enforce kebab-case, canonical names, no deprecated imports)
      duplication-policy.ts     (detect duplicate component contracts and token definitions)
      compat-policy.ts          (semver compatibility checks)
      a11y-policy.ts            (enforce a11y contract completeness on all new components)
    gates/
      contribution-gate.ts      (validates PR additions: must have contract, tests, docs)
      build-gate.ts             (Gradle/Turbo build check integration)
    reports/
      audit-report.ts           (generate JSON audit report)
```

---

### Phase 2 — Canvas Platform Hardening  
**Duration**: 5 weeks  
**Goal**: Clean public API, absorbed facades, structured source layout, telemetry, and product migration.  
**Prerequisite**: Phase 0 complete. Phase 1 preferred (for shared AI/platform-events types).

---

#### Task 2.1 — Restructure canvas source layout
**Path**: `platform/typescript/canvas/src/`  
**Why**: All current source is flat by concern. Public API and internal implementation must be separated to support controlled exports.

Target layout:
```
platform/typescript/canvas/src/
  public/
    index.ts                    (re-exports public canvas root)
    flow/
      index.ts                  (FlowCanvas, FlowControls, node/edge types, registration hooks)
    topology/
      index.ts                  (BaseTopologyNode, BaseTopologyEdge, useTopology, layout helpers)
    collaboration/
      index.ts                  (CollaborationProvider, presence API, document sync hooks)
    ai/
      index.ts                  (AIProvider interface, ghost node/edge, suggestion models, review state)
                                (imports AISuggestion from @ghatana/platform-events)
    export/
      index.ts                  (export/import APIs, snapshot, render-to-image)
    testing/
      index.ts                  (test render helpers, a11y assertions, round-trip fixtures)
  internal/                     (not exported via package.json exports)
    engine/                     (from core/)
    renderer/
    registries/                 (from plugins/plugin-registry.ts)
    layout/
    hybrid/                     (HybridCanvas internals)
    chrome/                     (chrome.tsx split)
    telemetry/
    adapters/
    serializers/
    performance/
    freeform-composer/          (RENAMED from ui-builder/ — element-oriented freeform composition)
```

Steps:
1. Create `public/` directory with appropriate index files
2. Create `internal/` directory
3. Move each module to correct location, updating all imports
4. Remove `@ts-nocheck` anywhere it exists
5. Move YAPPC-specific `src/actions/phase-actions.ts` and `role-actions.ts` to `products/yappc/` (see Task 2.11)

**Tests**: All existing canvas tests must pass after reorganization. No behavior changes.

---

#### Task 2.2 — Tighten `@ghatana/canvas` exports map
**Path**: `platform/typescript/canvas/package.json`  
**Depends on**: Task 2.1  
**Why**: Remove public exposure of internal modules.

Target `exports` field:
```json
{
  "exports": {
    ".": {
      "types": "./dist/public/index.d.ts",
      "import": "./dist/public/index.js"
    },
    "./flow": {
      "types": "./dist/public/flow/index.d.ts",
      "import": "./dist/public/flow/index.js"
    },
    "./topology": {
      "types": "./dist/public/topology/index.d.ts",
      "import": "./dist/public/topology/index.js"
    },
    "./collaboration": {
      "types": "./dist/public/collaboration/index.d.ts",
      "import": "./dist/public/collaboration/index.js"
    },
    "./ai": {
      "types": "./dist/public/ai/index.d.ts",
      "import": "./dist/public/ai/index.js"
    },
    "./export": {
      "types": "./dist/public/export/index.d.ts",
      "import": "./dist/public/export/index.js"
    },
    "./testing": {
      "types": "./dist/public/testing/index.d.ts",
      "import": "./dist/public/testing/index.js"
    },
    "./package.json": "./package.json"
  }
}
```

**Remove from exports**: `./core`, `./hybrid`, `./plugins`, `./tools`, `./chrome`, `./react`, `./ui-builder`, `./core/canvas-renderer`

Verify no product code deep-imports removed paths before removing. If found, migrate those imports first.

---

#### Task 2.3 — Absorb `@ghatana/flow-canvas` into `@ghatana/canvas/flow`
**Path**: `platform/typescript/canvas/flow-canvas/` and `platform/typescript/canvas/src/public/flow/`  
**Why**: `@ghatana/flow-canvas` is a nested private package that duplicates functionality. Consumers should use `@ghatana/canvas/flow`.

Steps:
1. Audit `flow-canvas/src/` for any APIs not already in `canvas/src/`
2. Merge any missing APIs into `canvas/src/public/flow/index.ts`
3. Update `canvas/src/public/flow/index.ts` to fully replace `@ghatana/flow-canvas` consumer API
4. Update Data-Cloud `EventCloudTopology.tsx` imports: `from '@ghatana/flow-canvas'` → `from '@ghatana/canvas/flow'`
5. Update Data-Cloud `LineageGraph` imports similarly
6. Remove `flow-canvas/` directory
7. Update workspace `pnpm-workspace.yaml` to remove `flow-canvas`

**Tests**: Data-Cloud topology components must pass `vitest` after migration.

---

#### Task 2.4 — Remove deprecated canvas facade packages
**Path**: `platform/typescript/canvas-core/`, `canvas-react/`, `canvas-plugins/`, `canvas-tools/`, `canvas-chrome/`  
**Depends on**: Confirm no remaining imports of these packages in any product or platform code  
**Why**: Deprecated facades add confusion and maintenance cost.

Steps:
1. `grep -r '@ghatana/canvas-core\|@ghatana/canvas-react\|@ghatana/canvas-plugins\|@ghatana/canvas-tools\|@ghatana/canvas-chrome'` across all products
2. For each found import, migrate to the appropriate `@ghatana/canvas` or `@ghatana/canvas/<subpath>` equivalent
3. After all imports migrated, remove each directory
4. Remove from workspace `pnpm-workspace.yaml`
5. Run full workspace type-check to confirm clean

**Tests**: Full TypeScript type-check and build must pass.

---

#### Task 2.5 — Move YAPPC-specific canvas actions to product-local
**Path**: `platform/typescript/canvas/src/actions/phase-actions.ts`, `role-actions.ts`  
**Target**: `products/yappc/frontend/web/src/components/canvas/lifecycle/`  
**Why**: YAPPC lifecycle phases and role awareness are product domain concerns, not platform canvas concerns.

Steps:
1. Move `phase-actions.ts` and `role-actions.ts` to `products/yappc/frontend/web/src/components/canvas/lifecycle/`
2. Update all imports within YAPPC canvas components
3. Update `platform/typescript/canvas/src/actions/action-initializer.ts` to not reference these files
4. Remove YAPPC-specific conditional logic from canvas area

**Tests**: YAPPC canvas must still work. Canvas platform tests must still pass.

---

#### Task 2.6 — Integrate shared AI types into canvas AI subpath and implement AI-native capability group
**Path**: `platform/typescript/canvas/src/public/ai/index.ts`  
**Depends on**: Task 0.1, Task 0.3, Task 2.1  
**Why**: Canvas AI module must surface the full AI-native capability group (§2B Group D). Currently it has a minimal stub.

Update `canvas/src/public/ai/index.ts` to:
- Export `CanvasAIAdapter` (adapter contract for product AI implementations)
- Export `CanvasAIContext`, `CanvasAIPolicy`, `CanvasAutonomyThreshold`
- Export `GhostSuggestionLayer` component — renders pending AI edge/node suggestions as ghost overlay before acceptance
- Export `AnomalyHighlight` component — overlays detected anomalies on canvas nodes/edges with severity + explanation
- Export `NextActionHint` component — contextual hint overlay tied to selection or viewport state
- Export `CanvasStructureValidator` — validates canvas structure against schema; emits `canvas.ai.validation.completed`
- Export `AutoLayoutRecommender` — suggests layout changes with confidence band; emits `canvas.ai.layout.recommended`
- Export `SemanticClusteringOverlay` — groups related nodes; emits `canvas.ai.clustering.suggested`
- Re-export `AIVisibilityContract`, `AIChangeDescriptor`, `AIAutonomyLevel`, `AIApprovalState`, `AIChangeKind` from `@ghatana/platform-events`
- Re-export `CanvasAITelemetryEvent` (canvas-specific AI event shape extending `PlatformEvent`)

Every implicit AI canvas action must:
1. Emit a `canvas.ai.*` event (from §2.1 taxonomy) via `CanvasTelemetry`
2. Produce an `AIVisibilityContract` instance surfaced to the relevant `@ghatana/canvas/ai` consumer
3. Allow `overrideAvailable: true` so the user can always reject or override the AI suggestion
4. Set `triggeredBy: 'implicit'` in the emitted event

---

#### Task 2.7 — Add telemetry event emission to canvas operations
**Path**: `platform/typescript/canvas/src/internal/telemetry/`  
**Depends on**: Task 0.1, Task 2.1  
**Why**: Canvas operations must emit structured events for observability.

Create `canvas/src/internal/telemetry/canvas-telemetry.ts`:
```ts
import type { PlatformEvent, CorrelationId } from '@ghatana/platform-events';
import { CanvasEvents } from '@ghatana/platform-events';

export interface CanvasTelemetryConfig {
  readonly onEvent: (event: PlatformEvent) => void;
  readonly correlationId: CorrelationId;
}

export class CanvasTelemetry {
  constructor(private readonly config: CanvasTelemetryConfig) {}
  emitNodeCreated(nodeId: string, nodeType: string): void { ... }
  emitLayoutApplied(layoutAlgorithm: string, nodeCount: number): void { ... }
  emitAISuggestionShown(suggestionId: string, kind: string): void { ... }
  // all 23 required canvas event families from §2.1
}
```

Wire telemetry emission into `CanvasProvider` via optional config prop.

**Tests**: Unit tests for each emission method. Integration test verifying events are emitted with correct correlation IDs.

---

#### Task 2.8 — Harden `@ghatana/canvas/testing` subpath
**Path**: `platform/typescript/canvas/src/public/testing/`  
**Depends on**: Task 2.1  
**Why**: Products writing canvas tests need test helpers for RTL rendering, keyboard testing, and serialization round-trips.

Create:
```ts
// public/testing/index.ts
export { renderCanvas } from './render-canvas';          // Wraps CanvasProvider in test environment
export { createTestNode, createTestEdge } from './fixtures';  // Test data factories
export { pressKey, dragNode } from './interactions';      // Test interaction helpers
export { assertA11y } from './a11y-assertions';           // Accessibility violation check
export { assertRoundTrip } from './serialization';        // Export → import round-trip
export { assertTelemetryEmitted } from './telemetry-assertions'; // Check events emitted
export { assertAIContractSatisfied } from './ai-assertions';   // Verify AIVisibilityContract fulfilled
```

**Tests**: Self-referential — the testing subpath has its own tests verifying the helpers work.

---

#### Task 2.9 — Migrate AEP and YAPPC to public canvas APIs only
**Paths**:
- `products/data-cloud/planes/action/ui/src/` — verify no raw source path imports
- `products/yappc/frontend/web/src/components/canvas/` — verify no raw source path imports, no `@ghatana/canvas/hybrid` etc.

Steps:
1. Search for any `from '@ghatana/canvas/core'`, `from '@ghatana/canvas/hybrid'`, `from '@ghatana/canvas/plugins'`, `from '@ghatana/canvas/tools'`, `from '@ghatana/canvas/chrome'`
2. For each found import, migrate to appropriate public subpath or request the API be added to public surface
3. Verify YAPPC `CanvasReactFlowSurface.tsx` and `CanvasWorkspaceProvider.tsx` only import from public paths

**Tests**: YAPPC and AEP canvas-related tests must pass after migration.

---

#### Task 2.10 — Canvas multi-mode operating modes and public API completeness
**Path**: `platform/typescript/canvas/src/public/`  
**Depends on**: Task 2.1, Task 2.7  
**Why**: Canvas must support 6 distinct operating modes. Products must be able to select the mode without patching internals. Additionally, the full public React API surface must be exported.

**Operating modes**:
```ts
export type CanvasMode =
  | 'graph-flow'         // directed/undirected graph, node/edge editing
  | 'topology'           // infrastructure/system topology, health overlays
  | 'hybrid'             // mixed graph + freeform spatial elements
  | 'rich-spatial'       // whiteboard/ideation, freeform dominant
  | 'read-only'          // view-only, no mutation, full interaction disabled
  | 'collaboration';     // multi-user live editing with presence
```

**Full public React API surface** — all of the following must be exported from `@ghatana/canvas` (`.` subpath):
```ts
// Canvas composition root
export { CanvasProvider } from './public/react/CanvasProvider';
export { CanvasRoot } from './public/react/CanvasRoot';

// Viewport, chrome, toolbar
export { CanvasViewport } from './public/react/CanvasViewport';
export { CanvasToolbar } from './public/react/CanvasToolbar';
export { CanvasStatus } from './public/react/CanvasStatus';        // sync status, telemetry badge
export { CanvasChrome } from './public/react/CanvasChrome';        // tab bar, minimap, panel slots
export { CanvasEmptyState } from './public/react/CanvasEmptyState'; // first-interaction empty state

// State and history
export { CanvasHistory } from './public/react/CanvasHistory';
export { CanvasSelection } from './public/react/CanvasSelection';

// Layers
export { CanvasLayer } from './public/react/CanvasLayer';

// Hooks
export { useCanvasMode, useCanvasHistory, useCanvasSelection, useCanvasTelemetry } from './public/hooks';
```

Additionally export mode-switching API (`setMode`, `getMode`, `useCanvasMode` hook) from `/react` subpath.

**Tests**:
- Mode switching test: each mode applies correct input constraints
- Public API completeness: each export is present in package.json exports map
- CanvasChrome renders slot content at correct positions
- CanvasEmptyState renders and calls first-action callback

---

### Phase 3 — UI Builder Foundation  
**Duration**: 5 weeks  
**Goal**: Create `@ghatana/ui-builder` package. Define canonical first-class objects including `DesignSystemModel`. Migrate YAPPC `PageDesigner`. Establish preview host pattern. Implement vanilla DOM subpath.  
**Prerequisite**: Phase 0 and Phase 1 (Task 1.7 for component contracts) complete.

---

#### Task 3.1 — Create `@ghatana/ui-builder` package scaffold
**Path**: `platform/typescript/ui-builder/`  
**Why**: The builder orchestration layer does not exist. It binds design-system contracts, canvas projection, code projection, and preview.

Create:
```
platform/typescript/ui-builder/
  src/
    index.ts                    (public root: BuilderProvider, ComponentContract, BuilderDocument,
                                 DesignSystemModel, SceneProjection, RuntimeProjection, CodeProjection, etc.)
    types/
      design-system-model.ts    (DesignSystemModel — binding of ds token map, theme set, component registry,
                                 pattern definitions, slot/variant rules, a11y defaults, security/privacy defaults)
      component-contract.ts     (ComponentContract interface + Zod schema)
      configurator-schema.ts    (ConfiguratorSchema + field types)
      builder-document.ts       (BuilderDocument + component tree node types + bindings + actions + localStateRefs)
      builder-operation.ts      (BuilderOperation with actor, risk, review, rollback, telemetry)
      scene-projection.ts       (SceneProjection for canvas adapter)
      runtime-projection.ts     (RuntimeProjection for React + vanilla)
      code-projection.ts        (CodeProjection with fidelity tiers and ownership markers)
    contracts/
      document-store.ts         (createBuilderDocument, validateBuilderDocument, diffBuilderDocument)
      contract-registry.ts      (registerComponentContract, listContracts, lookupContract)
      operation-log.ts          (recordOperation, listOperations, rollback)
      ds-model-binder.ts        (bindDesignSystemModel, validateDSModelBinding)
    components/
      BuilderProvider.tsx       (context + registry + DesignSystemModel initialization)
    react/                      (content for @ghatana/ui-builder/react subpath)
      index.ts
      ReactRendererAdapter.tsx
      ReactContractHelpers.ts
      ReactPreviewHostIntegration.tsx
    web/                        (content for @ghatana/ui-builder/web subpath — vanilla DOM/custom elements)
      index.ts
      VanillaRendererAdapter.ts (renders BuilderDocument to custom elements / plain DOM)
      VanillaCodegen.ts         (generates HTML+TS+CSS from BuilderDocument)
      WebComponentBridge.ts     (wraps React components as custom elements where justified)
    preview/                    (content for @ghatana/ui-builder/preview subpath)
      index.ts
      PreviewProtocol.ts        (typed postMessage contracts for host/client)
      SandboxProfiles.ts        (iframe sandbox attribute combinations per TrustLevel)
      PreviewSessionState.ts    (session lifecycle types)
    testing/                    (content for @ghatana/ui-builder/testing subpath)
      index.ts
      schema-fixtures.ts
      preview-harness.ts
      a11y-contract-assertions.ts
      round-trip-helpers.ts
      codegen-test-helpers.ts
  package.json                  (subpath exports: ".", "./react", "./web", "./preview", "./testing")
  tsconfig.json
  vitest.config.ts
```

---

#### Task 3.2 — Implement `DesignSystemModel` and `ComponentContract` types
**Path**: `platform/typescript/ui-builder/src/types/design-system-model.ts` and `component-contract.ts`  
**Depends on**: Task 1.2 (`ds-schema`), Task 1.7 (component contracts), Task 0.1 (security/privacy types)

`DesignSystemModel` is the first-class binding that connects a versioned design-system instance to the builder. It is the canonical data source — the builder, canvas, and code generators all read from it.

```ts
import type { TokenMap, ThemeSet, PatternDefinition, SlotVariantRules } from '@ghatana/ds-schema';
import type { A11yDefaults } from '@ghatana/accessibility';
import type { SecurityDefaults, PrivacyDefaults } from '@ghatana/platform-events';

export interface DesignSystemModel {
  readonly version: string;
  readonly tokenMap: TokenMap;                    // flat DTCG token map
  readonly themes: ThemeSet;                      // brand + mode matrix
  readonly componentRegistry: ComponentRegistry;  // name → ComponentContract
  readonly patternDefinitions: readonly PatternDefinition[];
  readonly slotVariantRules: SlotVariantRules;
  readonly a11yDefaults: A11yDefaults;
  readonly securityDefaults: SecurityDefaults;
  readonly privacyDefaults: PrivacyDefaults;
}
```

`ComponentContract` bridges the design-system component model to builder metadata:
```ts
import type { GhatanaComponent } from '@ghatana/ds-schema';
import type { SecurityMetadata, PrivacyMetadata, TrustLevel } from '@ghatana/platform-events';
import type { A11yContract } from '@ghatana/accessibility';

export interface ComponentContract extends GhatanaComponent {
  readonly runtimeTargets: readonly ('react' | 'vanilla')[];
  readonly propsSchema: z.ZodTypeAny;
  readonly slotSchema: Record<string, z.ZodTypeAny>;
  readonly styleSchema: z.ZodTypeAny;
  readonly eventSchema: Record<string, z.ZodTypeAny>;
  readonly a11y: A11yContract;
  readonly security: SecurityMetadata;
  readonly privacy: PrivacyMetadata;
  readonly configuratorSchema: ConfiguratorSchema;
  readonly codegenAdapters: ReadonlyMap<'react' | 'vanilla', CodegenAdapter>;
  readonly previewPolicy: { readonly trustLevel: TrustLevel };
}
```

---

#### Task 3.3 — Implement `BuilderDocument` type and operations
**Path**: `platform/typescript/ui-builder/src/types/builder-document.ts` and `src/contracts/document-store.ts`

```ts
export interface BuilderDocumentNode {
  readonly id: string;
  readonly contractName: string;
  readonly props: Record<string, unknown>;
  readonly slots: Record<string, readonly BuilderDocumentNode[]>;
  readonly layoutConstraints: LayoutConstraints;
  readonly responsiveVariants?: Record<string, Partial<BuilderDocumentNode>>;
}

export interface BuilderDocumentBinding {
  readonly nodeId: string;
  readonly propPath: string;
  readonly source: 'state' | 'context' | 'prop';
  readonly expression: string;
}

export interface BuilderDocumentAction {
  readonly nodeId: string;
  readonly event: string;
  readonly handler: string;           // references a localStateRef function or product callback
}

export interface BuilderDocument {
  readonly id: string;
  readonly version: string;
  readonly designSystemRef: string;           // @ghatana/design-system version
  readonly designSystemModelId: string;       // binding to DesignSystemModel instance
  readonly tree: readonly BuilderDocumentNode[];
  readonly bindings: readonly BuilderDocumentBinding[];   // data bindings
  readonly actions: readonly BuilderDocumentAction[];     // event→handler mappings
  readonly localStateRefs: Record<string, unknown>;       // local state shape
  readonly canvasSceneMetadata?: CanvasSceneMetadata;
  readonly previewSettings: PreviewSettings;
  readonly auditMetadata: ProvenanceRecord;  // from @ghatana/platform-events
}
```

Operations in `document-store.ts`:
- `createBuilderDocument(dsRef: string): BuilderDocument`
- `insertNode(doc, node, path): BuilderDocument` (pure, returns new doc)
- `updateNode(doc, nodeId, patch): BuilderDocument`
- `removeNode(doc, nodeId): BuilderDocument`
- `validateBuilderDocument(doc): ValidationResult`
- `diffBuilderDocument(prev, next): readonly ChangeRecord[]`

**Tests**: Immutable operations tests. Validation tests for schema-valid and invalid docs. Binding/action round-trip.

---

#### Task 3.4 — Implement `CodeProjection` with ownership markers
**Path**: `platform/typescript/ui-builder/src/types/code-projection.ts`  
**Why**: Code visibility and round-trip fidelity require explicit ownership zones.

```ts
export type CodeOwnership = 'generated' | 'user-authored' | 'protected' | 'manual-merge-required';

export type RoundTripFidelity = 'lossless' | 'assisted' | 'preview-only';

export interface CodeOwnershipMarker {
  readonly startLine: number;
  readonly endLine: number;
  readonly ownership: CodeOwnership;
  readonly generationRef?: string;    // correlates to BuilderOperation
}

export interface CodeProjection {
  readonly language: 'tsx' | 'html' | 'css' | 'ts';
  readonly source: string;
  readonly fidelity: RoundTripFidelity;
  readonly ownershipMap: readonly CodeOwnershipMarker[];
  readonly roundTripMetadata: Record<string, string>;
}
```

---

#### Task 3.5 — Implement React code generation from `BuilderDocument`
**Path**: `platform/typescript/ui-builder/src/react/`  
**Depends on**: Tasks 3.2, 3.3, 3.4

`ReactCodegen.ts` must:
- Walk the `BuilderDocument` tree
- For each node, look up its `ComponentContract` from the registry
- Invoke the node's `codegenAdapters.get('react')`
- Produce readable TSX with stable `@ghatana/design-system` imports
- Mark generated regions with `CodeOwnershipMarker`
- Return a `CodeProjection` with fidelity `lossless` for simple trees, `assisted` for bindings

**Tests**:
- Simple button tree → expected TSX snapshot
- Nested form → expected TSX snapshot
- Round-trip test: parse generated TSX back to `BuilderDocument`, verify structural equality

---

#### Task 3.6 — Implement preview host protocol
**Path**: `platform/typescript/ui-builder/src/preview/`  
**Depends on**: Task 0.1 (security/trust types), Task 3.1

Based on the pattern from `products/yappc/tools/live-preview-server/`, elevate the concept to a platform-level typed protocol:

```ts
// preview/PreviewProtocol.ts
export type PreviewMessage =
  | { type: 'PREVIEW_INIT'; sessionId: string; trustLevel: TrustLevel }
  | { type: 'PREVIEW_UPDATE'; patch: BuilderDocumentPatch; correlationId: string }
  | { type: 'PREVIEW_READY'; sessionId: string }
  | { type: 'PREVIEW_ERROR'; error: { message: string; phase: 'init' | 'render' }; correlationId: string }
  | { type: 'PREVIEW_EVENT'; event: PlatformEvent; correlationId: string };
```

`SandboxProfiles.ts` — maps `TrustLevel` to iframe sandbox attribute strings:
```ts
export const SANDBOX_PROFILES: Record<TrustLevel, string> = {
  TRUSTED_WORKSPACE: 'allow-scripts allow-same-origin allow-forms',
  GENERATED_TRUSTED: 'allow-scripts',
  IMPORTED_REVIEW_REQUIRED: 'allow-scripts',
  UNTRUSTED: '',  // no permissions
} as const;
```

**Tests**: Preview message type exhaustiveness. Sandbox profile immutability tests.

---

#### Task 3.7 — Migrate YAPPC `PageDesigner` to align with `BuilderDocument` model
**Path**: `products/yappc/frontend/web/src/components/canvas/page/`  
**Depends on**: Tasks 3.3, 3.5  
**Why**: Current `PageDesigner.tsx` uses `@ts-nocheck`, has narrow product-local schemas, and one-way JSX export. It should use the platform `BuilderDocument` model.

Steps:
1. Replace `schemas.ts` narrow Zod types with `ComponentContract` from `@ghatana/ui-builder`
2. Replace `ComponentData` internal type with `BuilderDocumentNode`
3. Update `PageDesigner.tsx` to use `createBuilderDocument`, `insertNode`, `updateNode` in place of local state mutations
4. Update `exportJSX.ts` to use `generateCode` from `@ghatana/ui-builder/react` instead of custom codegen
5. Remove `@ts-nocheck` — fix all type errors revealed
6. Keep `PageDesigner` as a product-local component (it is YAPPC-specific UI); it uses platform APIs but stays in products/yappc

**Tests**: Re-enable and fix `page/__tests__/` tests against updated component.

---

#### Task 3.8 — Harden YAPPC `live-preview-server` to use platform protocol
**Path**: `products/yappc/tools/live-preview-server/src/`  
**Depends on**: Task 3.6  
**Why**: The existing WebSocket server uses ad hoc message types. It should use the platform `PreviewMessage` protocol.

Steps:
1. Replace `types.ts` local `PreviewMessage` with the one from `@ghatana/ui-builder/preview`
2. Update `index.ts` message handling to use typed `PreviewMessage` discriminated union
3. This is a product tool — it stays in `products/yappc/tools/` (not promoted to platform)

**Tests**: Existing server tests must still pass.

---

#### Task 3.9 — Add builder operation telemetry
**Path**: `platform/typescript/ui-builder/src/contracts/operation-log.ts`  
**Depends on**: Task 0.1  
**Why**: All builder operations must be observable and auditable.

```ts
import type { PlatformEvent, BuilderEvents, OperationRecord } from '@ghatana/platform-events';

export interface BuilderOperationLog {
  record(operation: BuilderOperation): void;
  list(): readonly BuilderOperation[];
  rollback(operationId: string): BuilderDocument | null;
  export(): readonly OperationRecord[];
}
```

Wire into `BuilderProvider` — operations emit `PlatformEvent<BuilderEvents.*>` via configurable `onEvent` handler.

---

#### Task 3.10 — Implement `@ghatana/ui-builder/web` vanilla DOM target
**Path**: `platform/typescript/ui-builder/src/web/`  
**Depends on**: Tasks 3.2, 3.3, 3.4  
**Why**: Not all deployment targets use React. Builder must support vanilla DOM/custom elements/HTML+CSS+TS output. This is the `/web` subpath (§3.1 package map).

Deliverables:
- `VanillaRendererAdapter.ts` — renders a `BuilderDocument` tree to DOM using custom elements or plain HTML. Must produce the same visual result as the React renderer for the same document.
- `VanillaCodegen.ts` — generates HTML template + TS class + CSS variables output from a `BuilderDocument`. Output must be importable into standard web projects without a React dependency.
- `WebComponentBridge.ts` — optional: wrap an existing `@ghatana/design-system` React component as a `customElement`. Only implemented for components where the bridge is justified by product use (not for all components).
- CSS variable output: every `ComponentContract` token reference maps to a CSS custom property using `@ghatana/tokens` variable names.

Exports from `/web`:
```ts
export { VanillaRendererAdapter } from './VanillaRendererAdapter';
export { VanillaCodegen } from './VanillaCodegen';
export { WebComponentBridge } from './WebComponentBridge';
export type { VanillaRenderTarget, VanillaCodegenOutput } from './types';
```

**Tests**:
- Render test: given a `BuilderDocument` with 3+ component types, vanilla output matches expected HTML structure
- Codegen test: generated HTML+TS is valid and parseable
- CSS variable test: token references resolve to correct CSS variable names
- Round-trip test: import generated HTML → re-parse → BuilderDocument matches original

---

#### Task 3.11 — `SceneProjection` canvas integration (builder-to-canvas artboard)
**Path**: `platform/typescript/ui-builder/src/types/scene-projection.ts` and wiring into canvas  
**Depends on**: Tasks 2.10 (canvas multi-mode), 3.3 (BuilderDocument), 3.4 (CodeProjection)  
**Why**: The builder needs a spatial canvas surface (artboard) for visual editing. `SceneProjection` is the derived layer (§1C) that maps a `BuilderDocument` tree to canvas nodes and edges. Canvas is a projection sink, not a canonical source.

```ts
import type { CanvasDocument, CanvasNode, CanvasEdge } from '@ghatana/canvas';

export interface SceneProjection {
  readonly canvasDocument: CanvasDocument;
  readonly nodeMap: ReadonlyMap<string, string>;   // BuilderDocumentNode.id → CanvasNode.id
  readonly reverseMap: ReadonlyMap<string, string>; // CanvasNode.id → BuilderDocumentNode.id
}

export function projectDocumentToScene(
  builderDocument: BuilderDocument,
  previousProjection?: SceneProjection,
): SceneProjection { ... }

export function applySceneEditToDocument(
  edit: CanvasSelectionEdit,
  builderDocument: BuilderDocument,
  projection: SceneProjection,
): BuilderDocument { ... }
```

Rules:
1. `SceneProjection` is always re-derived from `BuilderDocument` — it is never the same as the document
2. Canvas drag/resize/reorder edits flow back to `BuilderDocument` via `applySceneEditToDocument` — the canonical update
3. Canvas emits `canvas.selection.changed` and `canvas.node.moved` events; the builder listens and applies edits to the document
4. `CanvasMode` must be `'rich-spatial'` when used as an artboard (prevents graph topology assumptions)

**Tests**:
- Projection test: a `BuilderDocument` with 5 nodes produces a `SceneProjection` with 5 canvas nodes
- Edit round-trip: drag a canvas node → `SceneProjection` update → `BuilderDocument` update → correct prop change
- Stability test: projecting twice with the same document produces the same canvas node IDs

---

#### Task 3.12 — Implement code import and reconciliation engine
**Path**:
- `platform/typescript/ui-builder/src/reconciliation/`
- `platform/typescript/ui-builder/src/contracts/code-reconciler.ts`
- `platform/typescript/ui-builder/src/testing/`

**Depends on**: Tasks 3.4, 3.5, 3.10  
**Why**: The four-layer synchronization model is not credible unless direct edits to the code representation can be parsed, reconciled, and pushed back into `BuilderDocument` in a controlled way.

Deliverables:
1. `CodeReconciler` for TSX and HTML inputs that produces `BuilderDocumentPatch` results rather than mutating state directly.
2. Ownership-aware merge behavior using `CodeOwnershipMarker` so generated, protected, and user-authored regions are handled differently.
3. `ReconciliationResult` with explicit outcomes: `applied`, `review-required`, `conflict`, `unsupported`.
4. Conflict records that surface through `AIVisibilityContract` and `SyncStatusIndicator` instead of silently failing.
5. Event emission for `builder.code.edited`, `builder.sync.started`, `builder.sync.completed`, `builder.sync.conflict`, and `builder.import.review_required`.
6. Policy enforcement: in `HUMAN_ONLY`, uncertain reconciliations never auto-apply; in `HUMAN_REVIEW_REQUIRED`, medium-confidence merges queue for review.

**Tests**:
- Generated TSX manual edit round-trip updates `BuilderDocument` correctly
- Protected-region edit produces `manual-merge-required` conflict state
- Low-confidence parse produces `review-required` result instead of auto-apply
- HTML import round-trip preserves ownership markers and sync status

---

#### Task 3.13 — Implement `BuilderDocument` persistence, autosave, and version restore
**Path**:
- `platform/typescript/ui-builder/src/persistence/`
- `platform/typescript/ui-builder/src/contracts/document-store.ts`
- `platform/typescript/ui-builder/src/contracts/session-store.ts`

**Depends on**: Tasks 3.3, 3.9, 3.11, 3.12  
**Why**: Without persistence and recovery semantics, the thin slice is only a transient demo and cannot support minimal human intervention or reliable human-only workflows.

Deliverables:
1. `BuilderDocumentStore` interface with `save`, `load`, `listVersions`, and `restoreVersion` operations.
2. `AutosaveCoordinator` with dirty-state tracking, debounce policy, and explicit last-saved metadata.
3. `SessionRestoreState` that captures `BuilderDocument`, scene projection reference, code projection reference, autonomy mode, and pending review state.
4. Version snapshot model used by rollback, preview resume, and crash recovery.
5. Sync checkpoints so restore operations can re-derive canvas and code projections deterministically instead of storing divergent projections.

**Tests**:
- Save/load round-trip preserves `BuilderDocument` and provenance metadata
- Autosave only writes on dirty changes and records last successful checkpoint
- Restore-version re-derives scene and code projections with `SyncStatus` returning to `synced`
- Crash-recovery test restores the last durable session state after simulated reload

---

### Phase 4 — Design System Generator  
**Duration**: 4 weeks  
**Goal**: CLI + generator for token build, preset materialization, audit, migration, and governance gates. Includes preview hardening and DS alignment sub-phases.  
**Prerequisite**: Phase 1 complete.

---

#### Task 4.1 — Create `@ghatana/ds-cli` package
**Path**: `platform/typescript/ds-cli/`  
**Why**: Enables teams to operate the design system through a typed, governed CLI.

Commands to implement in Phase 4:
```
ghatana-ds init [--preset <name>]    → scaffold a new theme config, generates token overrides
ghatana-ds validate                  → validate all tokens/components against @ghatana/ds-schema
ghatana-ds build-tokens              → emit CSS vars, TS, JSON from token sources
ghatana-ds audit                     → detect duplicates, a11y gaps, governance violations
ghatana-ds migrate                   → apply migration manifest, run codemods
ghatana-ds generate-template         → scaffold a starter app from preset + brand config
ghatana-ds release                   → validate, build, tag, and publish with semver
```

**Tests**: CLI command integration tests using a temp fixture design system.

---

#### Task 4.2 — Implement `build-tokens` command (DTCG output)
**Path**: `platform/typescript/ds-cli/src/commands/build-tokens.ts`  
**Depends on**: Task 1.4 (DTCG token schema), Task 1.2 (`ds-schema`)  
**Why**: Teams need deterministic, governed token build output.

Outputs:
- CSS custom properties per token layer (global, semantic, component, mode)
- TypeScript `as const` token object
- JSON file (DTCG-compatible format)
- Source map for tooling

**Tests**: Given input token set → verify CSS output matches expected custom properties. Lifecycle fields correctly omitted from runtime output.

---

#### Task 4.3 — Implement `validate` command
**Depends on**: Task 1.2, Task 1.3  
**Why**: CI gate for token and component contract correctness.

Validates:
- All tokens pass `GhatanaToken` schema
- All component contracts pass `GhatanaComponent` schema
- No circular token references
- No deprecated tokens in use (warns unless `--allow-deprecated`)
- Theme completeness: all semantic tokens have a value in each published mode

---

#### Task 4.4 — Implement `audit` command
**Depends on**: Task 1.9 (`ds-governance`)  
**Why**: Proactive detection of design system health issues.

Checks:
- Duplicate component definitions (same anatomy/behavior, different names)
- A11y contract completeness (keyboard model missing = fail)
- Governance violations (naming policy, package boundary, deprecated import)
- Token drift (semantic token using global value directly without semantic alias)
- Coverage: components without contracts

---

#### Task 4.5 — Create `@ghatana/ds-generator` preset materialization
**Path**: `platform/typescript/ds-generator/`  
**Depends on**: Tasks 1.2, 1.3, 1.4, 4.1  
**Why**: Teams choosing a preset design system should get a complete working output.

Generator capabilities:
- Load preset definition (token overrides + theme config + component recipe overrides)
- Materialize to `platform/typescript/tokens/src/` overrides
- Generate theme pack in `platform/typescript/theme/src/brandPresets.ts`
- Output brand-customized CSS variable file
- Record generation provenance in token `$extensions.ghatana`

---

#### Task 4.6 — Preview hardening: trusted and sandboxed preview host
**Path**: `platform/typescript/ui-builder/src/preview/`  
**Depends on**: Tasks 3.6, 0.1 (TrustLevel types)  
**Why**: Preview is a security boundary. Any user-composed component tree could execute arbitrary code in the preview context. The preview host must enforce trust levels, sandbox profiles, and audit every session lifecycle event.

Deliverables:
- Expand `SandboxProfiles.ts` to cover all four `TrustLevel` values with distinct iframe sandbox attribute strings:
  - `TRUSTED_WORKSPACE` — `allow-scripts allow-same-origin` (Vite dev server, full HMR)
  - `GENERATED_TRUSTED` — `allow-scripts` (sandboxed trusted output)
  - `IMPORTED_REVIEW_REQUIRED` — `allow-scripts` (sandboxed, review banner shown, no storage)
  - `UNTRUSTED` — no scripts (static HTML only, no execution)
- `PreviewTelemetry.ts` — emit `builder.preview.*` events for: `session.started`, `session.ended`, `error.thrown`, `trust.escalation.attempted`, `device.profile.changed`
- Add responsive device controls to the preview protocol: `device: 'mobile' | 'tablet' | 'desktop' | 'custom'`, `viewport: { width, height }`
- Add `TrustLabel` UI shown inside preview chrome when `trustLevel` is not `TRUSTED_WORKSPACE`
- `FallbackNotice` component — shown when preview cannot be rendered due to trust restrictions; links to review flow
- `PreviewPerformanceSampler` — sample `LCP`, `FID`, `CLS` metrics from inside the sandbox and surface to the outside via postMessage

**Tests**:
- Trust level to sandbox attribute mapping: all 4 levels produce correct strings
- `UNTRUSTED` level: no script execution allowed
- Telemetry tests: session.started and session.ended are emitted on mount/unmount
- Responsive controls test: device profile changes update viewport constraint

---

#### Task 4.7 — Design System alignment: direct consumption of DS contracts in builder
**Path**: `platform/typescript/ui-builder/src/contracts/ds-model-binder.ts`  
**Depends on**: Tasks 1.2, 1.7, 3.2  
**Why**: The builder must not have a stale copy of design-system component metadata. It must directly consume `DesignSystemModel` as its single source of truth and re-derive its component registry from it.

Deliverables:
- `bindDesignSystemModel(version: string): Promise<DesignSystemModel>` — loads the named design-system version, validates it against `@ghatana/ds-schema`, and returns the full `DesignSystemModel`
- `validateDSModelBinding(model: DesignSystemModel, registry: ComponentRegistry): ValidationResult` — asserts that every contract in the registry has a corresponding design-system component; reports missing or mismatched entries
- Storybook alignment: every `ComponentContract` must produce a matching Storybook story. Task 4.7 validates that the contract's `configuratorSchema` fields match the control types in the story.
- Emit `ds.` events (from §2.1 taxonomy) when a DS model is bound, when a version conflict is detected, or when a token/component reference cannot be resolved in the model.

**Tests**:
- DS model binding test: loading a valid version produces `DesignSystemModel` with non-empty registry
- Mismatch detection test: a component contract referencing a non-existent DS component produces a validation error
- Storybook alignment test: each contract produces one story (contract-story parity)

---

### Phase 5 — Product Migration and Adoption  
**Duration**: 4 weeks  
**Goal**: All three primary products fully on canonical platform APIs. Vanilla web target adopted where justified.

---

#### Task 5.1 — AEP full migration to canonical canvas/flow + design-system
**Path**: `products/data-cloud/planes/action/ui/src/`

Steps:
1. Verify `aep-pipeline-plugin.ts` correctly uses `PluginManager` from `@ghatana/canvas` — it already does ✓
2. Remove any remaining direct `@xyflow/react` imports from AEP canvas components; replace with `@ghatana/canvas/flow`
3. Migrate AEP components importing from `@ghatana/canvas/plugins`, `@ghatana/canvas/hybrid` → canonical paths
4. Adopt AI visibility primitives from `@ghatana/design-system` where AEP shows operation state

**Tests**: AEP canvas `vitest` suite must pass. AEP Playwright E2E must pass.

---

#### Task 5.2 — YAPPC canvas full product-platform boundary cleanup
**Path**: `products/yappc/frontend/web/src/components/canvas/`

Steps:
1. Complete Task 2.5 (move phase/role actions)
2. Remove raw canvas source path Vite aliases from YAPPC `vite.config.ts`
3. Replace all `@ghatana/canvas/core`, `@ghatana/canvas/hybrid`, `@ghatana/canvas/plugins` imports (after Task 2.2)
4. Ensure `CanvasWorkspaceProvider.tsx` uses `CanvasProvider` from `@ghatana/canvas` root
5. Adopt `OperationStatus`, `ReviewRequiredBanner`, `ActivityTimeline` from `@ghatana/design-system` in canvas AI overlay
6. Verify `CollaborativeCanvas.tsx` uses only `@ghatana/canvas/collaboration`

**Tests**: YAPPC canvas full vitest suite. YAPPC E2E canvas interaction tests.

---

#### Task 5.3 — Data-Cloud topology and lineage migration
**Path**: `products/data-cloud/delivery/ui/src/components/visualizations/`

Steps:
1. Complete Task 2.3 — migrate `EventCloudTopology.tsx` from `@ghatana/flow-canvas` to `@ghatana/canvas/flow`
2. Migrate `EventCloudLiveTopology.tsx` similarly
3. If `LineageGraph` uses `@ghatana/flow-canvas` → migrate to `@ghatana/canvas/flow`
4. Verify topology types from `@ghatana/canvas/topology` are sufficient; add any missing topology-specific types needed by data-cloud

**Tests**: Data-Cloud UI vitest topology tests. Storybook stories for topology must render.

---

#### Task 5.4 — All products: adopt shared AI visibility primitives
**Paths**: YAPPC, AEP, Data-Cloud UI

For each product:
1. Find components rendering operation status, AI suggestion state, or approval UI using product-local implementations
2. Replace with `OperationStatus`, `ReviewRequiredBanner`, `ChangeSummary`, `ActivityTimeline` from `@ghatana/design-system`
3. Wire event emission to use `PlatformEvent` from `@ghatana/platform-events`

---

#### Task 5.5 — All products: adopt `@ghatana/platform-events` types
**Paths**: YAPPC, AEP, Data-Cloud observability hooks

For each product:
1. Find any locally-defined telemetry event types that duplicate `PlatformEvent` shape
2. Migrate to import from `@ghatana/platform-events`
3. Correlate product-level telemetry with correlation IDs

---

#### Task 5.6 — Adopt vanilla web target in products where justified
**Path**: Products that deploy to non-React web contexts  
**Depends on**: Task 3.10 (`@ghatana/ui-builder/web`)  
**Why**: Builder-generated output is not always consumed in React applications. Some product surfaces embed static HTML, micro-frontends, or web components.

Steps:
1. Identify one product surface that is a valid candidate (approved by product owner + architect)
2. Integrate `@ghatana/ui-builder/web` as that surface's renderer
3. Output vanilla HTML/TS/CSS from `BuilderDocument` through `VanillaCodegen`
4. Validate that the generated output meets accessibility and security requirements
5. Record performance baseline and compare with React-based rendering for the same document

---

#### Task 5.7 — Product UX consistency pass (single-path authoring experience)
**Path**: YAPPC, AEP, Data-Cloud UI surfaces using canvas and page designer

**Why**: Migration can still leave fragmented user journeys. This pass enforces a consistent and simple authoring flow across products.

Consistency requirements:
1. Same top-level authoring steps in each product: `Design System -> Page Designer -> Canvas -> Preview -> Code`.
2. Same shared status primitives (`OperationStatus`, `SyncStatusIndicator`, `ReviewRequiredBanner`, `ActivityTimeline`).
3. Same autonomy controls surfaced in a consistent location and behavior.
4. Same rollback and override UX semantics.
5. One stable authoring entrypoint per product with refresh-safe recovery and no context loss between steps.

Validation:
- Cross-product heuristic review checklist.
- One golden E2E path per product with identical checkpoints.
- Usability pass with platform maintainers and product reps.

---

### Phase 6 — Shared Maintainer Apps  
**Duration**: 3 weeks  
**Goal**: Builder Studio, Theme Studio, Canvas Diagnostics, Component Playground, AI Review Console, Import/Migration Lab, Preview Lab. These are maintainer apps — not product runtime libraries.

---

#### Task 6.1 — Create Builder Studio app
**Path**: `apps/builder-studio/` (at repo root `apps/`)  
**Type**: Shared maintainer app  
**Depends on**: Phase 3 complete  
**Why**: Platform maintainers need a visual environment to exercise the builder, inspect builder documents, and validate component contracts.

Features:
- Component palette from `@ghatana/ds-registry`
- Drag-drop onto `BuilderDocument` tree (uses `SceneProjection` from Task 3.11)
- Canvas artboard via `@ghatana/canvas` in `'rich-spatial'` mode
- Code view via `@ghatana/code-editor`
- Preview iframe using `@ghatana/ui-builder/preview` protocol (task 4.6 hardened)
- Operation timeline showing `BuilderOperation` log with AI/user entries distinguished by `triggeredBy`
- `SyncStatusIndicator` showing four-layer sync state from §1C

---

#### Task 6.2 — Create Theme Studio app
**Path**: `apps/theme-studio/`  
**Depends on**: Phase 1 complete  
**Why**: Token and theme authoring, visualization, and preset customization.

Features:
- Token editor per DTCG category
- Theme dimension picker (mode, brand, density)
- Live component preview responding to token edits
- Accessibility invariant checker (contrast, focus ring)
- Export to `@ghatana/tokens` format

---

#### Task 6.3 — Create Component Playground app
**Path**: `apps/component-playground/`  
**Depends on**: Phase 1 complete  
**Why**: Component workbench for docs, interaction tests, and visual regression.

Features:
- Storybook-style story surface
- Component contract viewer  
- Accessibility checklist
- Responsive breakpoint preview
- AI UX primitives demonstration (showing `AIReviewPanel`, `ActivityTimeline`, `GhostSuggestionOverlay`)

---

#### Task 6.4 — Create Canvas Diagnostics app
**Path**: `apps/canvas-diagnostics/`  
**Depends on**: Phase 2 complete  
**Why**: Platform maintainers debugging canvas performance, telemetry, and collaboration behavior.

Features:
- Real-time telemetry event inspector for all 23 canvas event families (§2.1)
- Performance metric plots (render time, interaction latency)
- Collaboration presence simulation
- AI suggestion replay and override tooling showing `AIVisibilityContract` state per event

---

#### Task 6.5 — Create AI Review Console app
**Path**: `apps/ai-review-console/`  
**Depends on**: Tasks 0.1, 0.2, Phase 2 complete, Phase 3 complete  
**Why**: Cross-surface AI audit and review. Platform maintainers, QA, and compliance teams need to inspect all autonomous AI actions across canvas, builder, and design-system — including their evidence, approval state, and rollback records.

Features:
- Session-based timeline: all `PlatformEvent` entries where `actor: 'ai'` — across all sources (canvas, builder, ds)
- For each entry: `AIVisibilityContract` state, confidence band, evidence, `triggeredBy`, pending/applied/suggested changes
- Approval queue: pending AI changes requiring human review (`approvalState: 'PENDING'`), with approve/reject/override controls
- Override history: replay of human overrides with the `AIChangeDescriptor` diffs
- Autonomy level breakdown: pie/bar chart of `AIAutonomyLevel` distribution over time
- Rollback interface: for any `rollbackAvailable: true` entry, initiate rollback and emit `*.ai.*.rolledback` event
- Audit export: download structured audit log (JSON, CSV) for compliance

---

#### Task 6.6 — Create Import/Migration Lab app
**Path**: `apps/import-migration-lab/`  
**Depends on**: Tasks 3.4 (CodeProjection), 3.5 (React codegen), 3.10 (vanilla codegen)  
**Why**: Maintainers need to test round-trip fidelity of code import, migration from external design tools, and code ownership analysis — without impacting production environments.

Features:
- Code import workspace: paste or upload TSX/HTML/CSS, produce `BuilderDocument` (reverse codegen)
- Round-trip analysis: `BuilderDocument → codegen → re-import`, show diff and fidelity level
- Ownership map visualization: inline ownership markers on code editor surface
- Migration review: step-by-step view of automated codemods applied by `ghatana-ds migrate`
- Figma/Stitch import connector (stub for external tool import, not the full adapter)
- Import diff inspector: side-by-side view of source and generated code

---

#### Task 6.7 — Create Preview Lab app
**Path**: `apps/preview-lab/`  
**Depends on**: Task 4.6 (preview hardening)  
**Why**: Maintainers need to test sandbox profiles, device modes, performance measurement, and preview diagnostics in a controlled environment.

Features:
- Sandbox profile selector: choose one of 4 `TrustLevel` values, see which sandbox attributes apply, verify execution behavior
- Device profile picker: mobile/tablet/desktop/custom with real viewport constraints
- Performance dashboard: LCP, FID, CLS sampled from inside sandboxes via Task 4.6 `PreviewPerformanceSampler`
- Preview diagnostics: show `builder.preview.*` events from §2.1 in real time
- Fault injection: simulate preview error, trust escalation attempt, session timeout — verify `FallbackNotice` renders correctly
- Telemetry stream viewer: live view of all `builder.preview.*` events

---

## 5. Test Coverage Requirements

### 5.1 Coverage standards per layer

| Layer | Minimum Coverage | Priority |
|---|---|---|
| `@ghatana/platform-events` — type validators | 95% line coverage | P0 |
| `@ghatana/ds-schema` — Zod validators | 95% line coverage | P0 |
| `@ghatana/ds-registry` — core operations | 90% line coverage | P0 |
| `@ghatana/tokens` — DTCG validation | 90% line coverage | P0 |
| `@ghatana/canvas` — public subpath APIs incl. 6 capability groups | 85% line coverage | P1 |
| `@ghatana/canvas` — AI-native capability group (Group D) | 90% line coverage | P0 |
| `@ghatana/ui-builder` — document ops, codegen, vanilla web, scene projection | 90% line coverage | P0 |
| `@ghatana/design-system` — AI UX primitives | 85% line coverage | P1 |
| Product migrations (YAPPC, AEP, Data-Cloud) | No regression in existing coverage | P0 |

### 5.2 Required test types per package

| Package | Unit | Integration | A11y | Snapshot | E2E |
|---|---|---|---|---|---|
| `platform-events` | ✓ | — | — | ✓ (type shapes) | — |
| `ds-schema` | ✓ | — | — | ✓ | — |
| `ds-registry` | ✓ | ✓ | — | — | — |
| `tokens` | ✓ | ✓ | — | ✓ (CSS vars) | — |
| `canvas` public subpaths | ✓ | ✓ | ✓ | — | — |
| `canvas` AI group (GhostSuggestionLayer, AnomalyHighlight) | ✓ | ✓ | ✓ | ✓ | — |
| `ui-builder` | ✓ | ✓ | ✓ | ✓ (codegen) | — |
| `ui-builder/web` (vanilla) | ✓ | ✓ | ✓ | ✓ (HTML output) | — |
| `ui-builder` scene projection | ✓ | ✓ | — | — | — |
| `design-system` AI primitives | ✓ | — | ✓ | — | — |
| Builder Studio app | — | ✓ | ✓ | — | ✓ (Playwright) |
| AI Review Console app | — | ✓ | ✓ | — | ✓ (Playwright) |
| Import/Migration Lab | — | ✓ | ✓ | ✓ (round-trip) | — |
| Preview Lab | — | ✓ | ✓ | — | ✓ (Playwright) |

### 5.3 No new `@ts-nocheck` allowed

All new code must be fully typed. Existing `@ts-nocheck` (found in YAPPC `PageDesigner.tsx` and `schemas.ts`) must be removed as part of their respective migration tasks.

---

## 6. Governance and Enforcement

### 6.1 ESLint rules to add (via `eslint-rules/ghatana-architecture-rules.js`)

```js
// No deep imports of canvas internals
'no-restricted-imports': ['error', {
  patterns: [
    '@ghatana/canvas/core*',
    '@ghatana/canvas/hybrid*',
    '@ghatana/canvas/plugins*',
    '@ghatana/canvas/tools*',
    '@ghatana/canvas/chrome*',
    '@ghatana/canvas/internal*',
    '@ghatana/canvas/src*',
    '@ghatana/flow-canvas',
    '@ghatana/canvas-core',
    '@ghatana/canvas-react',
    '@ghatana/canvas-plugins',
    '@ghatana/canvas-tools',
    '@ghatana/canvas-chrome',
  ]
}]
```

### 6.2 Build rules to add (via Gradle or Turbo)

- `canvas` build fails if any file in `src/internal/` is referenced from `src/public/exports`
- `design-system` build fails if any component exports a `@ts-nocheck`-containing file  
- `ui-builder` build fails if `preview/` subpath imports from non-`platform-events` security types
- Dependency cruiser rule: product code must not import from `@ghatana/primitives` or `@ghatana/ui` directly (only via `@ghatana/design-system`)

### 6.3 Token governance enforcement

After Task 1.4 (DTCG tokens), add to `ds-governance`:
- CI fails if any new token file does not pass `GhatanaToken` schema validation
- CI warns if any new component token does not have a semantic alias parent

### 6.4 Documentation gates

Each new public package must have:
- `README.md` in canonical format (package name, purpose, usage, API reference)
- All exported types have JSDoc `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern` tags
- `CHANGELOG.md` initialized

---

## 7. Phase Timeline and Sequencing

```
Week 1–3   Phase 0  Cross-cutting foundation
           └── 0.1 Create @ghatana/platform-events
           └── 0.2 Add AI UX visibility primitives to design-system
           └── 0.3 Migrate AISuggestion to platform-events
           └── 0.4 Workspace registration
           └── 0.5 Human Control Plane (autonomy mode controls)

Week 4–8   Phase 1  Design System Foundation
           └── 1.1 Audit design-system boundary
           └── 1.2 Create @ghatana/ds-schema
           └── 1.3 Create @ghatana/ds-registry
           └── 1.4 Harden @ghatana/tokens to DTCG
           └── 1.5 Create @ghatana/primitives (internal)
           └── 1.6 Create @ghatana/ui (internal)
           └── 1.7 Add component contracts to components
           └── 1.8 Create @ghatana/patterns
           └── 1.9 Create @ghatana/ds-governance

Week 9–13  Phase 2  Canvas Platform Hardening
           └── 2.1 Restructure canvas src/public + src/internal
           └── 2.2 Tighten package.json exports map
           └── 2.3 Absorb flow-canvas into canvas/flow
           └── 2.4 Remove deprecated canvas facades
           └── 2.5 Move YAPPC-specific canvas actions
           └── 2.6 Canvas AI-native capability group (GhostSuggestionLayer, AnomalyHighlight, NextActionHint, etc.)
           └── 2.7 Add canvas telemetry emission (all 23 event families)
           └── 2.8 Create canvas/testing subpath (incl. assertAIContractSatisfied)
           └── 2.9 Migrate products to public canvas APIs
           └── 2.10 Canvas multi-mode (CanvasMode enum, full public API surface)

Week 14–18 Phase 3  UI Builder Foundation
           └── 3.1 Create @ghatana/ui-builder scaffold (with /react /web /preview /testing subpaths)
           └── 3.2 Implement DesignSystemModel + ComponentContract
           └── 3.3 Implement BuilderDocument (bindings, actions, localStateRefs)
           └── 3.4 Implement CodeProjection
           └── 3.5 Implement React codegen
           └── 3.6 Implement preview host protocol
           └── 3.7 Migrate YAPPC PageDesigner
           └── 3.8 Migrate YAPPC live-preview-server
           └── 3.9 Add builder operation telemetry
           └── 3.10 Implement @ghatana/ui-builder/web (vanilla DOM/HTML/TS codegen)
           └── 3.11 SceneProjection canvas integration (artboard, drag/resize round-trip)
           └── 3.12 Code import and reconciliation engine
           └── 3.13 BuilderDocument persistence, autosave, and version restore

Week 19    Milestone M1  First usable YAPPC authoring slice
           └── 0.6 End-to-end thin slice (single authoring shell, autonomy controls, save/resume, rollback)

Week 20–23 Phase 4  Design System Generator + Preview Hardening + DS Alignment
           └── 4.1 Create @ghatana/ds-cli
           └── 4.2 build-tokens command
           └── 4.3 validate command
           └── 4.4 audit command
           └── 4.5 Create @ghatana/ds-generator
           └── 4.6 Preview hardening (TrustLevel sandbox profiles, FallbackNotice, telemetry, device controls)
           └── 4.7 DS alignment (DesignSystemModel binding, Storybook alignment, DS event emission)

Week 24–27 Phase 5  Product Migration and Adoption
           └── 5.1 AEP canvas migration
           └── 5.2 YAPPC canvas boundary cleanup
           └── 5.3 Data-Cloud topology migration
           └── 5.4 All products: AI visibility primitives
           └── 5.5 All products: platform-events types
           └── 5.6 Vanilla web target adoption (one justified product surface)
           └── 5.7 Product UX consistency pass (single-path authoring)

Week 28–31 Phase 6  Shared Maintainer Apps
           └── 6.1 Builder Studio (SceneProjection artboard, SyncStatusIndicator, op timeline with triggeredBy)
           └── 6.2 Theme Studio
           └── 6.3 Component Playground (AI UX primitives demo)
           └── 6.4 Canvas Diagnostics (all 23 canvas events, AIVisibilityContract viewer)
           └── 6.5 AI Review Console (cross-surface AI audit, approval queue, override history, rollback)
           └── 6.6 Import/Migration Lab (round-trip fidelity, ownership map, codemod review)
           └── 6.7 Preview Lab (sandbox profiles, device modes, performance, fault injection)
```

---

## 8. Non-Negotiables Per Repo Rules

The following must be true for every task in this plan, no exceptions:

1. **No `any` types** in any new TypeScript code. Use `unknown` + type guards where needed.
2. **All new public TypeScript types have explicit annotations** — function parameters, return types, prop interfaces.
3. **Tests are part of the change** — every task that adds behavior adds tests.
4. **Public Java equivalents (if any)** must carry `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern` tags.
5. **No new dependencies without justification** — check `platform/typescript/` existing packages before adding anything.
6. **Product code must not import from internal platform packages** (`@ghatana/primitives`, `@ghatana/ui`, schema internals).
7. **Lint must pass zero warnings** after each task.
8. **No `@ts-nocheck`** in any new file. Remove it from migrated files.
9. **Observability is part of every feature** — any user-facing flow must emit `PlatformEvent` events.
10. **Boundaries are explicit** — package.json `exports` maps prevent deep imports.

---

## 9. Quick Reference: Which Package Owns What

| Concern | Package | Phase |
|---|---|---|
| Token values and transforms | `@ghatana/tokens` | Existing (DTCG-aligned Phase 1) |
| Theme runtime and brand presets | `@ghatana/theme` | Existing (unchanged) |
| Layout and interaction primitives | `@ghatana/primitives` | Internal, new Phase 1 |
| Generic reusable components | `@ghatana/ui` | Internal; `design-system` is facade, Phase 1 |
| Public product component facade | `@ghatana/design-system` | Existing (includes AI UX primitives Phase 0) |
| Token/component/theme schema types | `@ghatana/ds-schema` | New Phase 1 |
| Component/token/theme registry | `@ghatana/ds-registry` | New Phase 1 |
| Cross-cutting events, AI contract, security, privacy types | `@ghatana/platform-events` | New Phase 0 |
| Accessibility contracts and hooks | `@ghatana/accessibility` | Existing (extended Phase 0) |
| Accessibility audit tooling | `@ghatana/accessibility-audit` | Existing (unchanged) |
| Code editor (Monaco) | `@ghatana/code-editor` | Existing (unchanged) |
| AI UX visibility atoms | `@ghatana/design-system` (`/atoms`) | Phase 0 — `OperationStatus`, `ConfidenceBadge`, `AILabel`, `SyncStatusIndicator`, `DataUseNotice`, `ToolUseDisclosure` |
| AI UX visibility molecules | `@ghatana/design-system` (`/molecules`) | Phase 0 — `ReviewRequiredBanner`, `ChangeSummary`, `ActivityTimeline`, `CitationBlock`, `EvidencePanel`, `ApprovalControls`, `GhostSuggestionOverlay`, `AIAssistantLoop`, `AIReviewPanel` |
| Canvas AI-native primitives (spatial) | `@ghatana/canvas/ai` | Phase 2 — `GhostSuggestionLayer`, `AnomalyHighlight`, `NextActionHint`, validators, recommenders |
| Spatial editing + graph/topology canvas | `@ghatana/canvas` | Phase 2 (hardened, 6 capability groups) |
| Graph/flow-focused canvas APIs | `@ghatana/canvas/flow` | Phase 2 (absorbs flow-canvas) |
| Topology visualization | `@ghatana/canvas/topology` | Phase 2 (tightened) |
| Collaboration primitives | `@ghatana/canvas/collaboration` | Phase 2 (tightened) |
| Canvas AI integration | `@ghatana/canvas/ai` | Phase 2 (integrates platform-events + AI-native group) |
| Canvas multi-mode operating modes | `@ghatana/canvas` | Phase 2 — `CanvasMode` type + `useCanvasMode` hook |
| Canvas public React API | `@ghatana/canvas` (`.`) | Phase 2 — `CanvasProvider`, `CanvasRoot`, `CanvasViewport`, etc. |
| Design System binding (first-class object) | `@ghatana/ui-builder` — `DesignSystemModel` | New Phase 3 |
| Builder orchestration | `@ghatana/ui-builder` | New Phase 3 |
| Builder React renderer + codegen | `@ghatana/ui-builder/react` | Phase 3 |
| Builder vanilla DOM / custom elements / HTML+TS codegen | `@ghatana/ui-builder/web` | Phase 3 (Task 3.10) |
| Builder canvas artboard / SceneProjection | `@ghatana/ui-builder` internal + `@ghatana/canvas` | Phase 3 (Task 3.11) |
| Code import / reconciliation back to `BuilderDocument` | `@ghatana/ui-builder` | Phase 3 (Task 3.12) |
| Builder persistence, autosave, version restore | `@ghatana/ui-builder` | Phase 3 (Task 3.13) |
| Live preview protocol + sandbox | `@ghatana/ui-builder/preview` | Phase 3/4 (hardened Task 4.6) |
| Preview `FallbackNotice` component | `@ghatana/ui-builder/preview` + `@ghatana/design-system` | Phase 4 (Task 4.6) |
| Design system CLI | `@ghatana/ds-cli` | Phase 4 |
| Preset materializer | `@ghatana/ds-generator` | Phase 4 |
| Governance policy engine | `@ghatana/ds-governance` | Phase 1/4 |
| Recurring UI workflow patterns (incl. AI UX) | `@ghatana/patterns` | Internal, Phase 1 |
| Cross-surface AI audit console | `apps/ai-review-console` | Phase 6 (Task 6.5) |
| Code import and migration review | `apps/import-migration-lab` | Phase 6 (Task 6.6) |
| Preview sandbox and diagnostics | `apps/preview-lab` | Phase 6 (Task 6.7) |

---

## 10. Definition of Done for This Plan

The plan is complete when all of the following are true:

- [ ] `@ghatana/platform-events` is published with zero `any` types and 95%+ test coverage; full event taxonomy for all three domains (§2.1)
- [ ] `AutonomyExecutionMode` is fully enforced with 3 modes (`AUTONOMOUS_ASSISTED`, `HUMAN_REVIEW_REQUIRED`, `HUMAN_ONLY`) and policy tests are passing
- [ ] `@ghatana/ds-schema` validates all token and component inputs via Zod; includes `DesignSystemModel` type
- [ ] `@ghatana/tokens` passes DTCG schema validation in CI
- [ ] `@ghatana/canvas` package.json `exports` has no `./core`, `./hybrid`, `./plugins`, `./tools`, `./chrome` entries
- [ ] All five deprecated canvas facades (canvas-core, canvas-react, canvas-plugins, canvas-tools, canvas-chrome) removed
- [ ] `@ghatana/flow-canvas` nested package removed; all consumers on `@ghatana/canvas/flow`
- [ ] Canvas public API surface complete: `CanvasProvider`, `CanvasRoot`, `CanvasViewport`, `CanvasToolbar`, `CanvasStatus`, `CanvasHistory`, `CanvasSelection`, `CanvasLayer`, `CanvasChrome`, `CanvasEmptyState` all exported
- [ ] Canvas multi-mode: `CanvasMode` type and `useCanvasMode` hook exported
- [ ] Canvas AI-native group: `GhostSuggestionLayer`, `AnomalyHighlight`, `NextActionHint` exported from `@ghatana/canvas/ai`
- [ ] All 23 canvas event families and 22 builder event families defined in `@ghatana/platform-events` with Zod-validated payload types
- [ ] `@ghatana/ui-builder` with `DesignSystemModel`, `ComponentContract`, `BuilderDocument`, `SceneProjection`, `CodeProjection`, React codegen, vanilla codegen, and preview protocol published
- [ ] `@ghatana/ui-builder/web` vanilla subpath working: `VanillaRendererAdapter`, `VanillaCodegen`, HTML+TS+CSS output
- [ ] `BuilderDocument` includes `bindings`, `actions`, `localStateRefs` fields
- [ ] Code import and reconciliation are implemented for TSX and HTML with explicit `applied` / `review-required` / `conflict` outcomes
- [ ] `BuilderDocument` persistence, autosave, version restore, and refresh-safe session recovery are implemented and tested
- [ ] End-to-end thin slice is running in YAPPC: Canvas + Page Designer + Design System + Preview + Code + Rollback in one flow
- [ ] The YAPPC thin slice runs through one stable authoring route with shared autonomy/status controls and no context loss between steps
- [ ] Human-only slice is fully usable: same end-to-end flow with implicit AI blocked and no automation dependency
- [ ] YAPPC `PageDesigner` uses `BuilderDocument` API; `@ts-nocheck` removed
- [ ] YAPPC canvas imports only from `@ghatana/canvas` public subpaths
- [ ] AEP canvas uses `@ghatana/canvas/flow`; no raw XYFlow imports
- [ ] Data-Cloud topology uses `@ghatana/canvas/flow` and `@ghatana/canvas/topology`; no `@ghatana/flow-canvas` imports
- [ ] All three products use `OperationStatus`, `ReviewRequiredBanner`, `ActivityTimeline`, `SyncStatusIndicator` from `@ghatana/design-system`
- [ ] `@ghatana/ds-cli` `validate` and `audit` commands pass in CI
- [ ] ESLint rules block deprecated canvas and design-system deep imports
- [ ] All new packages have `README.md` and pass `@ghatana/ds-governance` contribution gate
- [ ] Zero `@ts-nocheck` in platform packages
- [ ] Zero TypeScript errors across full workspace type-check
- [ ] Cross-product authoring consistency pass completed with one golden E2E path in YAPPC, AEP, and Data-Cloud
- [ ] AI Review Console app (`apps/ai-review-console`) deployed; shows full approval queue and rollback interface
- [ ] Import/Migration Lab app deployed; round-trip fidelity test passes for a real `BuilderDocument`
- [ ] Preview Lab app deployed; all 4 sandbox profiles verified
