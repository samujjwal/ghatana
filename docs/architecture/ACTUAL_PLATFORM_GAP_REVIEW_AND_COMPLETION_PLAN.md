# Actual Platform Gap Review And Completion Plan

**Authored**: 2026-04-12  
**Scope**: Actual repo state review for Design System + Canvas + Live UI Builder + Studio integration  
**Primary Inputs**:
- `docs/architecture/DESIGN_SYSTEM_GENERATOR_PLATFORM_ARCHITECTURE_HARDENED.md`
- `docs/architecture/CANVAS_PLATFORM_ENHANCEMENT_AND_IMPLEMENTATION_PLAN.md`
- `docs/architecture/LIVE_UI_BUILDER_AND_EXECUTION_PLATFORM_ARCHITECTURE.md`
- `docs/architecture/UNIFIED_PLATFORM_IMPLEMENTATION_PLAN.md`
- `docs/architecture/UNIFIED_PLATFORM_IMPLEMENTATION_TASK_TRACKER.md`

---

## 1. Purpose

This document reviews the **actual implementation currently present in the repo**, identifies gaps against the intended platform architecture, and defines the concrete work needed to make the system:

- accurate and correct,
- simple to consume,
- minimal in its shared-library surface,
- consistent across design system, canvas, builder, preview, and studio,
- AI-native but human-visible,
- observable, governable, and production-ready.

This is a **completion and hardening plan**, not a greenfield architecture proposal.

---

## 2. Current State Summary

The repo is significantly ahead of the original architecture documents. The following packages and tracks already exist:

- `platform/typescript/ui-builder`
- `platform/typescript/ds-schema`
- `platform/typescript/ds-registry`
- `platform/typescript/ds-governance`
- `platform/typescript/ghatana-studio`
- `platform/typescript/canvas`
- YAPPC integrations and migration adapters

There is also already a broad umbrella plan:

- `docs/architecture/UNIFIED_PLATFORM_IMPLEMENTATION_PLAN.md`
- `docs/architecture/UNIFIED_PLATFORM_IMPLEMENTATION_TASK_TRACKER.md`

That said, the actual repo state still has important issues:

- public API boundaries are not yet clean,
- products still import internal-like subpaths,
- builder/test/runtime behavior is not fully hardened,
- some package claims in tracker/docs are ahead of the quality of actual implementation,
- design-system contracts are not yet rich enough to fully support the intended builder/canvas/AI/visibility model,
- preview, import, and runtime trust models need stronger policy completeness,
- several implementations use placeholders that will not scale to real product use.

---

## 3. Repo-Based Findings

### 3.1 Public API boundaries are still leaky

`@ghatana/ui-builder` currently exports:

- `.`
- `./react`
- `./web`
- `./preview`
- `./testing`
- `./core`

`./core` is being consumed directly by products:

- `products/yappc/frontend/web/src/components/canvas/page/PageDesigner.tsx`
- `products/yappc/frontend/web/src/components/canvas/page/builder-document-adapter.ts`
- `products/yappc/frontend/web/src/components/studio/LivePreviewPanel.tsx`

This weakens the facade model. It means the package is nominally layered but still structurally encouraging internal coupling.

`@ghatana/canvas` also still exports a very broad surface including:

- `./core`
- `./ui-builder`
- `./plugins`
- `./hybrid`
- `./topology`
- `./tools`
- `./chrome`
- `./public`

This is materially broader than the intended “minimal shared surface” model.

### 3.2 Builder domain model is still too thin for the stated goals

`platform/typescript/ui-builder/src/core/types.ts` defines a usable first-pass `BuilderDocument`, but it is still underspecified for the product goals discussed across the architecture docs.

Missing or too-light areas include:

- explicit responsive variants / breakpoints,
- action graphs and event wiring semantics,
- explicit layout model beyond ad hoc props/position,
- richer scene metadata for drag/resize/reposition workflows,
- review state / pending state / approval state on document changes,
- security/privacy/data-classification metadata,
- AI suggestion lineage and explainability metadata,
- richer round-trip provenance beyond basic ownership/loss points,
- stable product-safe persistence metadata for save/resume/collaboration/versioning.

### 3.3 Design-system contract metadata is not yet rich enough

`platform/typescript/ds-schema/src/components/contract.ts` already includes a useful first version of props, slots, events, styles, dependencies, builder metadata, and examples.

However, it is still insufficient as the canonical contract for:

- AI-safe auto-configuration,
- full configurator generation,
- security/privacy review,
- telemetry declaration,
- visibility and review UX,
- policy-aware preview execution,
- trust-sensitive code generation,
- advanced canvas editing semantics.

Important missing or too-light metadata:

- data sensitivity and privacy classification per prop/event/slot,
- secret-bearing or review-required fields,
- telemetry contract declaration,
- observability hooks expected from component usage,
- AI policy hints and permitted implicit actions,
- editable capabilities by mode,
- layout and constraint semantics,
- accessibility coverage granularity beyond the current basic fields,
- runtime restrictions for preview/import/export.

### 3.4 Product integrations still use placeholders

YAPPC’s builder adapter currently creates a `BuilderDocument` with:

- empty `componentContracts`,
- basic prop pass-through,
- no real design-system binding,
- no strong ownership or reconciliation semantics,
- local component schemas that are too narrow and still `@ts-nocheck`.

Relevant files:

- `products/yappc/frontend/web/src/components/canvas/page/builder-document-adapter.ts`
- `products/yappc/frontend/web/src/components/canvas/page/schemas.ts`
- `products/yappc/frontend/web/src/components/canvas/page/PageDesigner.tsx`

This is acceptable for migration scaffolding, but not for the long-term shared-platform contract.

### 3.5 Runtime hardening is incomplete

Actual test execution shows that packages exist, but some are not yet production-hardened.

Observed issues:

- `@ghatana/ui-builder`
  - missing React test dependency for renderer tests
  - code generation edge cases are failing
  - import behavior is not fully aligned to expected review-required behavior
  - web renderer API/behavior mismatch exists between tests and implementation
- `@ghatana/ds-schema`
  - schema validation is not strongly rejecting invalid token values and empty component names
- `@ghatana/ds-governance`
  - test tooling/setup is inconsistent (`vitest` unavailable when invoked)

### 3.6 Preview trust model exists but needs policy completion

`platform/typescript/ui-builder/src/preview/trust.ts` provides strong primitives:

- trusted origin checks,
- protocol validation,
- sandbox/CSP generation,
- device state,
- fallback states,
- preview telemetry.

But the current implementation is still permissive and helper-oriented rather than policy-complete. In particular:

- sandbox defaults still tend toward script + same-origin enablement,
- there is no clearly enforced trusted/untrusted preview profile matrix,
- policy is not yet visibly tied to autonomy/privacy/AI mode,
- preview telemetry exists but is not clearly unified with broader visibility UX.

### 3.7 Canvas cleanup is incomplete despite broad progress

The tracker shows only two explicit open items in Phase 2:

- `2.8 Harden @ghatana/canvas/testing with render, interaction, telemetry, and AI contract helpers.`
- `2.9 Migrate AEP and YAPPC to public canvas APIs only.`

The repo also still contains platform-level YAPPC-specific action files in canvas:

- `platform/typescript/canvas/src/actions/phase-actions.ts`
- `platform/typescript/canvas/src/actions/role-actions.ts`

That means the package boundary is still not fully clean even where the tracker reports strong progress.

### 3.8 Studio exists, but it is still more showcase than governing control plane

`platform/typescript/ghatana-studio` already provides the right major sections:

- Builder Studio
- Theme Studio
- Component Playground
- Canvas Diagnostics
- AI Review Console
- Import/Migration Lab
- Preview Lab

This is a strong start. The remaining gap is that the studio still needs to become the authoritative maintainer environment for:

- contract authoring,
- builder/canvas integration verification,
- trust-policy inspection,
- AI action visibility and approval,
- preview policy validation,
- migration readiness,
- release gates and governance workflows.

---

## 4. Gap Review By Area

### 4.1 Design System

#### What is good

- `@ghatana/ds-schema` exists.
- `@ghatana/ds-registry` exists and its tests pass.
- `@ghatana/ds-governance` exists structurally.
- shared design-system visibility primitives already have an architectural home.

#### Gaps

- component contract schema is not yet sufficient as the one canonical design-system-to-builder/canvas/runtime API,
- validation rules are too weak in key places,
- public/internal layering still needs stricter enforcement,
- story/contract/runtime parity is not yet fully guaranteed,
- design-system metadata does not yet encode enough for AI/ML, privacy, observability, or trust.

#### Required outcome

The design system must be the **single declarative contract source** for:

- component capabilities,
- configurator generation,
- safe editing semantics,
- preview/runtime constraints,
- import/export compatibility,
- telemetry/visibility requirements,
- AI-allowed operations and review thresholds.

### 4.2 Canvas

#### What is good

- `@ghatana/canvas` is large and real.
- AI, telemetry, testing, collaboration, flow, topology, hybrid, and React layers already exist.
- public/internal restructuring work has begun.

#### Gaps

- package surface is still too broad,
- product-specific logic still exists in platform canvas,
- public API discipline is not fully enforced in consumers,
- testing helpers are not yet the canonical quality gate across products,
- canvas-to-builder contract still needs stronger normalization.

#### Required outcome

Canvas must expose only the reusable authoring primitives that every product can share. Product semantics, product actions, and domain workflows must stay outside the shared canvas package.

### 4.3 UI Builder

#### What is good

- the package exists with core/react/web/preview/testing structure,
- codegen/import/persistence/scene projection already exist,
- preview protocol and trust helpers exist,
- vanilla web target exists.

#### Gaps

- boundary leak through `./core`,
- root API is not yet the only intended consumption route,
- domain model needs richer semantics,
- runtime/test behavior is not yet reliable enough,
- generated/imported representations are still only partially aligned with intended live-editing depth.

#### Required outcome

`@ghatana/ui-builder` must become the canonical high-level facade for:

- authoring documents,
- rendering/editing,
- importing/exporting code,
- previewing execution,
- projecting into canvas,
- persistence and recovery,
- AI-assisted authoring with visibility.

Consumers should not need internal subpath knowledge except for carefully curated targets like `react`, `web`, `preview`, and `testing`.

### 4.4 Live Editing / Execution

#### What is good

- live preview server exists,
- preview protocol exists,
- React and vanilla web targets both exist.

#### Gaps

- end-to-end live editing contract is not yet standardized,
- direct code edits, drag/drop edits, configurator edits, and canvas edits are not yet clearly governed under one change pipeline,
- approval and visibility semantics are not consistently attached to generated or imported changes,
- runtime sandboxing does not yet expose enough operator and user-facing policy state.

#### Required outcome

All editing modes must feed one shared change model with:

- provenance,
- confidence,
- review requirement,
- rollback,
- telemetry,
- visibility in UI,
- policy-aware execution.

### 4.5 AI/ML, Visibility, O11y, Security, Privacy

#### What is good

- the architecture already treats these as first-class,
- platform-events and design-system visibility primitives are already positioned well,
- preview and canvas have telemetry foundations.

#### Gaps

- actual package contracts do not yet carry these concerns deeply enough,
- AI action visibility is not yet systematically bound to every builder/canvas/design-system mutation path,
- policy and trust are not yet encoded tightly enough at contract level,
- observability is still more capability than enforcement.

#### Required outcome

Every significant operation in builder, canvas, preview, and design-system authoring must produce:

- structured event emission,
- visible status in UI,
- provenance and actor information,
- confidence / risk / review markers where applicable,
- privacy / trust / policy context,
- rollback path where feasible.

### 4.6 Product Integration

#### What is good

- YAPPC already has migration adapters,
- studio-facing preview integration exists,
- the repo has visible adoption momentum.

#### Gaps

- product integrations still rely on placeholders and local schemas,
- public surface discipline is not fully enforced,
- there is still too much adaptation happening in product code,
- design-system-backed configurator generation is not yet the default.

#### Required outcome

Products should consume a small, stable shared API. They should not need to know platform internals, internal package layering, or builder document implementation details.

---

## 5. Shared-Library Minimality Model

This section defines the target organization to prevent shared-library bloat.

### 5.1 Public packages that products may consume

- `@ghatana/design-system`
- `@ghatana/canvas`
- `@ghatana/ui-builder`
- `@ghatana/platform-events`
- `@ghatana/tokens`
- `@ghatana/theme`

Optional public maintainer packages:

- `@ghatana/ds-cli`
- `@ghatana/ds-generator`

### 5.2 Internal platform packages that products should not consume directly

- `@ghatana/primitives`
- `@ghatana/ui`
- `@ghatana/patterns`
- raw schema/governance internals except where explicitly intended for maintainer flows
- any `core`, `internal`, `private`, or migration-only surfaces

### 5.3 Public API principle

Each public package should expose:

- one obvious root import for common use,
- only a small number of clearly justified subpaths,
- no internal model leakage unless intentionally promoted and documented,
- stable semantics independent of internal implementation structure.

### 5.4 Target public surfaces

#### `@ghatana/design-system`

Public:

- components,
- theming provider and theme hooks,
- visibility/status primitives,
- stable component-level contracts where needed by consumers.

Hidden:

- primitive implementation mechanics,
- composition recipes,
- internal pattern libraries,
- audit/governance implementation details.

#### `@ghatana/canvas`

Public:

- root canvas components and hooks,
- `react`,
- `ai`,
- `testing`,
- `flow`,
- `telemetry`,
- carefully selected collaboration/topology entrypoints if truly cross-product.

Hidden:

- product action implementations,
- legacy/internal facades,
- internal layer mechanics,
- internal freeform-composer/ui-builder glue.

#### `@ghatana/ui-builder`

Public:

- root facade for document operations, validation, import/export, persistence, and scene projection,
- `react`,
- `web`,
- `preview`,
- `testing`.

Hidden:

- `core` as a direct consumer dependency,
- internal reconciliation and serialization details,
- implementation-specific helper internals.

#### `@ghatana/platform-events`

Public:

- canonical cross-cutting event, visibility, AI, privacy, security, trust, and ownership contracts.

Hidden:

- any adapter-specific implementation or product-local extensions.

---

## 6. Feature Completeness Review

### 6.1 Design System

| Area | Status | Notes |
|---|---|---|
| Token schema foundation | Partial | Exists, but validation hardening still needed |
| Component contract schema | Partial | Good start, not rich enough for full platform goals |
| Registry | Good | Existing tests pass |
| Governance | Partial | Package exists, but tooling/runtime completeness is uneven |
| Internal/public layering | Partial | Directionally correct, not yet fully enforced |
| Story/contract/runtime parity | Partial | Needs stronger contract-driven enforcement |

### 6.2 UI Builder

| Area | Status | Notes |
|---|---|---|
| Package structure | Good | Root + targeted subpaths exist |
| Public API cleanliness | Partial | `./core` leak still active |
| Builder document | Partial | Usable, still too thin |
| Code generation | Partial | Implemented, but edge-case correctness gaps remain |
| Import / reconciliation | Partial | Implemented, but heuristic and incomplete |
| Persistence / recovery | Good | Exists, needs integration maturity |
| Vanilla web target | Partial | Exists, but contract/tests are not aligned |
| React target | Partial | Exists, but test/tooling gaps remain |
| Preview protocol | Good | Strong foundational shape |
| Preview trust policy | Partial | Needs stronger enforcement matrix |

### 6.3 Canvas

| Area | Status | Notes |
|---|---|---|
| Core authoring capability | Good | Substantial implementation exists |
| Public API minimality | Partial | Exports still too broad |
| Product separation | Partial | Product-specific actions still present |
| Testing helpers | Partial | Exists, but tracker still marks hardening incomplete |
| Builder integration | Partial | Needs stricter canonical boundary |

### 6.4 Studio / Product Adoption

| Area | Status | Notes |
|---|---|---|
| Unified maintainer app | Good | Exists with right major labs |
| Governance control plane | Partial | Needs stronger operational authority |
| YAPPC adoption | Partial | Real progress, still placeholder-heavy |
| Cross-product API discipline | Partial | Not yet fully enforced |

### 6.5 Cross-Cutting First-Class Requirements

| Area | Status | Notes |
|---|---|---|
| AI/ML as first class | Partial | Architectural intent is strong; contract embedding incomplete |
| Human visibility | Partial | Primitives exist; end-to-end mutation visibility incomplete |
| Telemetry / O11y | Partial | Good foundations, not yet strict enough as contract |
| Security / privacy / trust | Partial | Helpers exist; policy enforcement matrix incomplete |
| Minimal human intervention with step-in ability | Partial | Needs deeper policy wiring in runtime flows |

---

## 7. Detailed Implementation Plan

## 7.1 Workstream A: Public API And Package Boundary Cleanup

### Objective

Make the shared platform easy to consume, minimal, and structurally safe.

### Tasks

1. Define and publish canonical public import surfaces for:
   - `@ghatana/design-system`
   - `@ghatana/canvas`
   - `@ghatana/ui-builder`
   - `@ghatana/platform-events`
2. Remove or deprecate direct product consumption of `@ghatana/ui-builder/core`.
3. Re-export all intended builder root APIs from `@ghatana/ui-builder`.
4. Add package-level lint or repo rules that reject imports from internal/unsupported subpaths in product code.
5. Tighten `@ghatana/canvas` exports to only intentionally public surfaces.
6. Move or finally delete product-specific platform code from shared canvas.
7. Document the “public vs internal” policy in each package README and in the architecture docs.

### Acceptance Criteria

- no product imports unsupported subpaths,
- root imports are sufficient for standard product consumption,
- internal package structure can change without product breakage,
- shared package exports are auditable and minimal.

## 7.2 Workstream B: Design-System Contract Hardening

### Objective

Make the design-system contract the canonical source for builder, canvas, preview, configurators, AI, and governance.

### Tasks

1. Strengthen token validation so invalid token values fail deterministically.
2. Require non-empty component identifiers and other foundational invariants.
3. Extend `ComponentContractSchema` with:
   - data classification metadata,
   - privacy and secret-handling metadata,
   - review-required and approval-sensitive fields,
   - telemetry declaration,
   - observability hooks,
   - AI policy hints,
   - configurator generation hints,
   - layout/constraint/editability metadata,
   - richer accessibility coverage and requirements,
   - preview/runtime trust restrictions.
4. Define which contract fields are product-consumable and which are maintainer-only.
5. Require registry-backed contract validation for all builder-usable components.
6. Add story/preview/runtime parity verification in governance and studio workflows.

### Acceptance Criteria

- a component contract alone is sufficient to generate a basic safe configurator,
- builder can determine editability, preview safety, and AI automation limits from contracts,
- invalid or incomplete contracts fail before product use,
- contract metadata covers first-class requirements rather than leaving them to conventions.

## 7.3 Workstream C: Builder Document V2

### Objective

Upgrade `BuilderDocument` from a structural representation into a complete live-authoring model.

### Tasks

1. Extend document/node metadata with:
   - layout constraints,
   - responsive variants,
   - state variants,
   - interaction/action definitions,
   - provenance,
   - review status,
   - pending/sync status,
   - privacy/trust classification,
   - AI action lineage,
   - collaboration/version metadata.
2. Normalize slot/child relationships and scene metadata to support drag, drop, resize, reposition, and reorder without ad hoc product logic.
3. Clarify the contract between:
   - document model,
   - scene projection,
   - code projection,
   - runtime preview projection.
4. Add explicit approval/visibility hooks for system-suggested or system-applied mutations.
5. Version the document model so migration logic is explicit rather than incidental.

### Acceptance Criteria

- one document model can support configurator edits, canvas edits, code edits, and AI edits,
- provenance and review state survive round-trip flows,
- products do not need local parallel schemas for common builder use cases.

## 7.4 Workstream D: Runtime, Import, Codegen, And Preview Hardening

### Objective

Make live editing and execution accurate, trustworthy, and predictable.

### Tasks

1. Fix `@ghatana/ui-builder` test/tooling gaps:
   - install missing renderer test dependencies,
   - align test fixtures to actual `ComponentInstance` shape,
   - fix renderer API mismatches,
   - fix codegen recursion/cycle handling,
   - fix nested slot emission expectations or implementation,
   - fix import review-required semantics.
2. Standardize builder runtime behavior across:
   - configurator edits,
   - drag/drop/canvas edits,
   - import edits,
   - direct code edits.
3. Define a strict preview trust-profile matrix:
   - trusted local dev,
   - trusted controlled workspace,
   - semi-trusted preview,
   - untrusted imported content.
4. Bind preview policies to:
   - autonomy mode,
   - privacy classification,
   - AI mode,
   - user/operator visibility.
5. Ensure preview telemetry feeds shared visibility components and operator observability.
6. Add deterministic rollback and fallback UX for preview/import/codegen failures.

### Acceptance Criteria

- UI builder tests pass reliably,
- preview policy is explicit and explainable,
- code generation and import behavior are correct for supported cases and visibly degraded for unsupported cases,
- runtime safety is policy-driven, not convention-driven.

## 7.5 Workstream E: Canvas-Builder Unification

### Objective

Use canvas as a first-class visual authoring surface without turning it into the source of truth.

### Tasks

1. Finalize `BuilderDocument` to `SceneProjection` contract.
2. Ensure drag, resize, reposition, selection, and layout operations reconcile cleanly back into the builder document.
3. Separate shared visual authoring primitives from product workflow semantics.
4. Harden `@ghatana/canvas/testing` as the canonical cross-product test harness for:
   - render expectations,
   - interaction semantics,
   - telemetry assertions,
   - AI contract expectations.
5. Finish migration of AEP and YAPPC to public canvas APIs only.
6. Remove or quarantine platform-level product-specific actions.

### Acceptance Criteria

- canvas edits are document-backed and auditable,
- products use public canvas APIs only,
- platform canvas contains no product workflow logic,
- canvas testing helpers are sufficient for product adoption verification.

## 7.6 Workstream F: AI/ML, Visibility, O11y, Security, Privacy

### Objective

Make the “first-class citizen” principles real in implementation, not just in planning.

### Tasks

1. Define mandatory event emission and visibility records for every meaningful mutation path.
2. Require every AI-initiated action to include:
   - actor,
   - reason,
   - confidence,
   - policy basis,
   - changed objects,
   - rollback path,
   - review requirement.
3. Encode privacy/trust policy at contract level and document level.
4. Ensure design-system components expose enough metadata for:
   - visibility/status UI,
   - privacy disclosure,
   - tool-use disclosure,
   - review-required UX,
   - failure/fallback UX.
5. Bind all major flows into `@ghatana/platform-events` instead of local parallel schemas.
6. Define operator dashboards and maintainer diagnostics expected in studio.

### Acceptance Criteria

- no meaningful AI action occurs invisibly,
- no trusted/untrusted runtime action occurs without policy context,
- observability is consistently emitted and consumable across domains,
- user step-in points are minimal but always available when risk warrants them.

## 7.7 Workstream G: Product Migration And Simplification

### Objective

Move products from migration scaffolding to clean shared-platform consumption.

### Tasks

1. Replace product-local narrow schemas with design-system-driven contracts where appropriate.
2. Update YAPPC imports to use the final public `@ghatana/ui-builder` surface.
3. Replace placeholder empty `componentContracts` wiring with registry-backed design-system models.
4. Remove `@ts-nocheck` from builder-related product files and restore typed safety.
5. Standardize preview and builder adoption patterns across YAPPC, AEP, and Data-Cloud.
6. Keep product code focused on domain workflows, not shared rendering/authoring mechanics.

### Acceptance Criteria

- products consume a small stable set of public APIs,
- product adapters become thin and intentional,
- platform owns shared behavior and products own domain semantics.

## 7.8 Workstream H: Studio As Maintainer Control Plane

### Objective

Turn Ghatana Studio into the authoritative place to build, inspect, validate, and release the platform.

### Tasks

1. Add contract inspection and validation workflows for `ds-schema` and registry data.
2. Add preview policy/trust inspection tooling.
3. Add AI review and visibility inspection tied to real platform events.
4. Add import/codegen fidelity inspection and diff visualization.
5. Add release-readiness gates for:
   - contract completeness,
   - story parity,
   - preview policy coverage,
   - accessibility coverage,
   - event/telemetry coverage.
6. Make studio the place where maintainers can validate package boundaries and product consumption patterns.

### Acceptance Criteria

- maintainers can verify platform quality without digging through scattered scripts,
- studio reflects real contracts and runtime policies,
- new shared-platform capabilities are proven in studio before broad product adoption.

---

## 8. Immediate Priority Fixes

These should happen before broader enhancement work because they block confidence.

### Priority 0

- fix `@ghatana/ui-builder` failing tests and contract drift,
- fix `@ghatana/ds-schema` validation gaps,
- fix `@ghatana/ds-governance` test/runtime tooling consistency,
- stop new product imports from `@ghatana/ui-builder/core`.

### Priority 1

- replace placeholder empty builder `componentContracts` wiring in YAPPC,
- remove product-specific actions from shared canvas,
- finalize public API surface decisions and document them.

### Priority 2

- extend component contracts for privacy/trust/AI/telemetry/editability,
- upgrade `BuilderDocument` model,
- finish canvas testing and public API migration work.

---

## 9. Recommended Sequencing

### Milestone A: Stabilize The Shared Surfaces

- Workstream A
- Priority 0 fixes
- package export cleanup
- import policy enforcement

### Milestone B: Harden The Contracts

- Workstream B
- contract validation
- registry/governance parity
- contract richness expansion

### Milestone C: Harden The Runtime

- Workstream C
- Workstream D
- builder model v2
- import/codegen/preview correctness

### Milestone D: Complete Canvas And Product Adoption

- Workstream E
- Workstream G
- public canvas migrations
- product cleanup

### Milestone E: Operationalize The First-Class Principles

- Workstream F
- Workstream H
- studio control-plane maturity
- enforceable visibility and observability

---

## 10. Definition Of Done

The platform is considered complete enough for broad product adoption when all of the following are true:

- products consume only the documented public package surfaces,
- shared packages expose minimal stable APIs,
- builder/canvas/preview/design-system use one coherent contract model,
- AI/ML, visibility, telemetry, privacy, and trust are encoded in actual contracts and emitted in actual runtime flows,
- product-local adapters are thin and type-safe,
- shared platform tests pass reliably,
- unsupported cases fail visibly and recoverably rather than silently,
- Ghatana Studio can validate the platform end-to-end as a maintainer environment.

---

## 11. Concrete Next Actions

1. Tighten package exports and remove direct product reliance on `@ghatana/ui-builder/core`.
2. Fix the current failing `@ghatana/ui-builder`, `@ghatana/ds-schema`, and `@ghatana/ds-governance` quality issues.
3. Replace YAPPC placeholder builder document wiring with registry-backed design-system contracts.
4. Expand `ComponentContractSchema` and `BuilderDocument` to carry first-class AI, visibility, trust, privacy, and observability metadata.
5. Complete canvas testing hardening and public-only product migration.
6. Promote Ghatana Studio from lab collection to authoritative maintainer control plane.

