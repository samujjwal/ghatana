# UI Builder Production Hardening Implementation Plan

**Authored:** 2026-04-21  
**Scope:** Shared builder platform + YAPPC page builder / UI builder / live preview hardening  
**Primary drivers:** Production-grade quality, feature completeness, low cognitive load, full power, strong boundaries, pervasive but visible AI, privacy/security/o11y/observability by default

## 1. Purpose

This document defines the concrete implementation plan to evolve the current builder/page-builder stack from migration scaffolding into a production-grade platform capability.

It is intentionally grounded in:

- the current repo implementation,
- the four P1 review findings,
- the existing architecture documents,
- and the repo rules in `.github/copilot-instructions.md`.

This is not a greenfield redesign. It is a boundary-respecting completion and hardening plan.

## 2. Mandatory Repo Rules For This Program

This plan must be executed in a way that strictly follows `.github/copilot-instructions.md`.

### 2.1 How those rules apply here

- **Reuse before creating**: Extend `platform/typescript/*` and existing `products/yappc/frontend/libs/*` packages before introducing any new library.
- **Do not change repo shape**: Keep the builder platform in shared packages and product-specific behavior in YAPPC-owned packages.
- **Keep boundaries explicit**: Shared builder logic must not absorb YAPPC domain workflows, transport wiring, or product-only UI shells.
- **No silent failures**: Builder, preview, codegen, sync, AI suggestion, and import/export paths must all surface typed, observable failures.
- **Zero-warning mindset**: `typecheck`, lint, tests, Storybook, route smoke, accessibility, and boundary checks must pass cleanly.
- **Type safety now, not later**: No `any`, no `@ts-nocheck`, no untyped handlers, no implicit external data assumptions.
- **Tests are part of the change**: Every phase below includes required regression coverage.
- **Prefer existing dependencies**: Use current repo libraries and current package patterns instead of adding alternate stacks.
- **Observability is part of the feature**: Builder operations, preview execution, AI actions, collaboration state, and security/privacy decisions must be diagnosable.

## 3. Current Reality

The current system is split across three layers that are not yet fully aligned:

- `platform/typescript/ui-builder`
- `platform/typescript/canvas`
- YAPPC integrations under `products/yappc/frontend/web` and `products/yappc/frontend/libs/*`

### 3.1 High-value assets already present

- `@ghatana/ui-builder` already has a usable `BuilderDocument`, operations, validation, telemetry, preview protocol, and React/web rendering primitives.
- `@ghatana/ds-schema` already models component contracts, builder metadata, privacy, telemetry, AI policy, layout semantics, and preview restrictions.
- `@ghatana/canvas` already provides a real spatial editing substrate.
- `@ghatana/code-editor` and `@ghatana/ghatana-studio` already exist for code projection and maintainer tooling.
- YAPPC already has `@yappc/ui`, `@yappc/development-ui`, `@yappc/state`, `@yappc/collab`, `@yappc/ai`, and `@yappc/artifact-compiler`.

### 3.2 Blocking defects confirmed by repo review

1. Shared builder tests do not execute successfully because the current Vitest setup is broken.
2. `PageDesigner` recreates a new `BuilderDocument` on every edit, breaking stable identity and traceability.
3. `builder-document-adapter.ts` performs a lossy round-trip and corrupts real component data.
4. `LivePreviewPanel` bypasses the platform trust model with a hardcoded permissive iframe sandbox.

### 3.3 Broader architectural gap

Today’s live YAPPC page builder is still mostly a local component-list editor with migration glue. The platform layer is richer than the product integration that currently consumes it.

## 4. Package Ownership And Placement Contract

This is the most important implementation constraint in the whole program.

| Capability | Canonical home | Why it belongs there | Must not live here |
|---|---|---|---|
| Canonical `BuilderDocument`, operations, persistence model, validation, import/export, codegen, preview protocol/trust | `platform/typescript/ui-builder` | Shared platform capability for all builder consumers | `products/yappc/frontend/web`, `@yappc/ui`, `@ghatana/canvas` |
| Component contract schema, builder metadata, preview restrictions, privacy/AI/o11y metadata | `platform/typescript/ds-schema` | Shared design-system contract source | YAPPC-local schemas except temporary compatibility adapters |
| Contract registry, governance, compatibility enforcement | `platform/typescript/ds-registry`, `platform/typescript/ds-governance` | Shared validation and release control | Product app code |
| Spatial editing primitives, handles, geometry, drag/resize/select, multi-surface canvas projection | `platform/typescript/canvas` | Shared authoring substrate | YAPPC-specific page-builder orchestration |
| Generic renderable UI components for apps and high-level packages | `platform/typescript/design-system` | Shared runtime UI facade for applications | Low-level shared builder internals |
| Generic code editing surface | `platform/typescript/code-editor` | Shared code view/editor capability | Product-specific builder logic |
| Cross-cutting builder/AI/privacy/security/visibility/observability event types | `platform/typescript/platform-events` | Shared telemetry and trace vocabulary | Product-local ad hoc event typings |
| Shared frontend test helpers, a11y fixtures, reusable Vitest setup utilities | `platform/typescript/testing` | Shared testing infrastructure | Ad hoc copy-pasted package setup files |
| Maintainer control plane and inspection surfaces | `platform/typescript/ghatana-studio` | Shared maintainer experience | YAPPC app runtime routes unless product-specific |
| Product editor shell, workflow panels, feature-specific inspector UI, app-creator chrome | `products/yappc/frontend/libs/yappc-ui`, `@yappc/development-ui` | YAPPC-specific user experience and product semantics | Shared platform packages |
| YAPPC persisted workflow/editor state | `products/yappc/frontend/libs/yappc-state` | Product store and orchestration | Shared platform builder model |
| Collaboration session orchestration, Yjs room lifecycle, presence UI | `products/yappc/frontend/libs/collab` using `@ghatana/realtime` | Product collaboration policy and session behavior | `@ghatana/ui-builder` |
| Product-specific AI UI, prompts, chat flows, assisted orchestration | `products/yappc/frontend/libs/yappc-ai` | Product AI experience and domain-specific assistants | Shared builder core |
| Reverse-engineering, import, artifact synthesis, migration into builder docs | `products/yappc/frontend/libs/yappc-artifact-compiler` | Product-specific import and synthesis workflows | Shared builder core except generic import contracts |

### 4.1 Special dependency rule for `@ghatana/design-system`

`@ghatana/design-system` is a high-level facade and should be consumed by apps and high-level product packages. Lower-level shared builder libraries should prefer the more specific shared packages they actually need:

- `@ghatana/ds-schema`
- `@ghatana/tokens`
- `@ghatana/theme`
- `@ghatana/primitives`
- `@ghatana/platform-utils`
- `@ghatana/platform-events`

Do not make `@ghatana/ui-builder` or `@ghatana/canvas` depend on the design-system facade for foundational logic.

## 5. Target End State

The production system must behave as follows:

- A `BuilderDocument` is the single source of truth.
- Tree/layout editing and spatial/canvas editing are projections of the same document.
- The design system contract is the canonical source for palette entries, configurators, validation, AI policy, preview policy, telemetry, privacy, and accessibility behavior.
- Preview execution is derived from document trust level and contract restrictions, never from hardcoded iframe flags.
- AI is pervasive for low-risk assistance but every autonomous action is visible, explainable, reversible, and reviewable.
- Users get a simple UX by default, while advanced power remains available through progressive disclosure, keyboard flows, and inspectable code.
- Shared platform packages stay generic and product-agnostic; YAPPC-specific flows remain in YAPPC-owned libraries.

## 6. Program Structure

The work should be executed in eight phases. Each phase has explicit ownership, scope, and acceptance criteria.

## 7. Phase 0: Guardrails And Decision Lock

### 7.1 Objective

Freeze the architecture and package-boundary decisions before implementation work spreads further.

### 7.2 Work

- Treat this document as the execution reference for the current hardening cycle.
- Stop adding new builder logic directly to `products/yappc/frontend/web/src/components/canvas/page/*` except for compatibility shims and integration glue.
- Define a public-API-only rule for YAPPC consumers of `@ghatana/ui-builder` and `@ghatana/canvas`.
- Audit direct imports of internal or leaky surfaces such as `./core`, broad canvas exports, and product-local schema duplication.

### 7.3 Acceptance

- New builder work is routed to the correct shared or product package.
- The team has a clear answer for “where should this live?” before coding begins.

## 8. Phase 1: Test Harness And Source-Of-Truth Stabilization

### 8.1 Objective

Fix the current P1 defects so the builder stack can be safely evolved.

### 8.2 Shared library work

#### `platform/typescript/ui-builder`

- Replace the broken shared Vitest setup with a Vitest-native setup strategy.
- Prefer either:
  - package-local `vitest.setup.ts` files, or
  - shared helpers exported from `@ghatana/platform-testing`.
- Remove Jest globals from Vitest setup files and replace with `vi.fn`, typed global mocks, and environment-safe setup.
- Ensure `@testing-library/jest-dom` is resolved correctly from the package test environment.
- Split builder tests into:
  - pure core tests,
  - React renderer tests,
  - preview/protocol tests,
  - import/export/codegen tests.

#### `platform/typescript/testing`

- Add reusable Vitest/browser setup helpers for:
  - DOM globals,
  - storage mocks,
  - fetch mocks,
  - accessibility assertions,
  - builder preview harnesses.

### 8.3 YAPPC integration work

#### `products/yappc/frontend/web/src/components/canvas/page/PageDesigner.tsx`

- Make `BuilderDocument` the internal state source of truth.
- Use `ComponentData[]` only as a legacy input/output compatibility format.
- Stop recreating document IDs and timestamps on every edit.
- Route edits through shared builder operations instead of hand-mutating arrays.

#### `products/yappc/frontend/web/src/components/canvas/page/builder-document-adapter.ts`

- Make the adapter non-lossy.
- Preserve all schema fields across round-trip conversion.
- Move only truly supplemental editor metadata into `instance.metadata`.
- Add explicit versioning and provenance for imported legacy documents.

### 8.4 Acceptance

- `pnpm --filter @ghatana/ui-builder test` passes.
- Builder tests execute real assertions rather than failing at setup.
- `PageDesigner` preserves stable document identity across edits.
- Round-trip tests pass for every current component type.

## 9. Phase 2: Design-System Contract Completion

### 9.1 Objective

Make the design-system contract the true source of builder semantics.

### 9.2 Work in shared libraries

#### `platform/typescript/ds-schema`

- Expand contract metadata so the builder no longer depends on YAPPC-local schema guesses.
- Ensure contracts fully cover:
  - palette identity and grouping,
  - layout/container rules,
  - slot semantics,
  - responsive behavior,
  - accessibility obligations,
  - privacy classification,
  - review-sensitive props,
  - AI-safe vs review-required operations,
  - preview/runtime restrictions,
  - telemetry and observability expectations.

#### `platform/typescript/ds-registry`

- Make registry lookups the canonical source for builder-available contracts.
- Support contract versioning, manifest lookup, and stable resolution for preview/codegen.

#### `platform/typescript/ds-governance`

- Add contract governance rules for:
  - invalid token references,
  - missing required metadata,
  - preview restriction consistency,
  - accessibility declaration completeness,
  - review/privacy metadata completeness for sensitive props and events.

### 9.3 YAPPC integration work

- Replace the current local schema duplication in `products/yappc/frontend/web/src/components/canvas/page/schemas.ts` with contract-driven compatibility logic.
- Keep only legacy parsing or migration glue in YAPPC.
- Register concrete contracts for the current starter component set: button, card, text field, typography, box/container.

### 9.4 Acceptance

- Palette and property-editing metadata come from contracts, not from hand-maintained YAPPC schemas.
- Design-system contract validation is strong enough to reject incomplete builder metadata.

## 10. Phase 3: Shared Builder Core Completion

### 10.1 Objective

Finish the shared builder orchestration layer so products can consume it instead of rebuilding it.

### 10.2 Work in `platform/typescript/ui-builder`

- Strengthen the `BuilderDocument` model for:
  - stable revisioning,
  - save/resume/checkpoints,
  - collaboration clocks,
  - provenance,
  - pending review state,
  - AI suggestion lineage,
  - sync state,
  - error state where needed.
- Separate document model concerns from product persistence concerns.
- Expand operations to support:
  - insert/reparent/reorder,
  - resize/reposition,
  - responsive overrides,
  - action graph editing,
  - batch operations,
  - undo/redo snapshots,
  - conflict-safe merge hooks.
- Strengthen validation to cover:
  - root integrity,
  - slot legality,
  - contract existence,
  - required props,
  - responsive rule consistency,
  - action/binding correctness,
  - policy violations,
  - preview trust mismatches.
- Align codegen and renderers to contract metadata instead of local heuristics.
- Support round-trip-safe code ownership and loss reporting.

### 10.3 Acceptance

- Shared builder APIs are sufficient for product integration without product-local reimplementation of core concepts.
- Validation errors are typed, actionable, and observable.
- Codegen and renderer behavior is contract-driven.

## 11. Phase 4: Canvas And Page-Builder Convergence

### 11.1 Objective

Unify tree-first page building and freeform canvas authoring on one document model.

### 11.2 Shared work

#### `platform/typescript/canvas`

- Keep generic spatial editing behavior here:
  - selection,
  - handles,
  - snapping,
  - drag/drop,
  - resize,
  - keyboard movement,
  - viewport and geometry.
- Remove product-specific behaviors from shared canvas surfaces.
- Expose only the public APIs required by builder consumers.

#### `platform/typescript/ui-builder`

- Add projection adapters between `BuilderDocument` semantics and canvas scene semantics.
- Keep semantic operations, slot awareness, and contract rules in builder, not canvas.

### 11.3 YAPPC work

- Migrate `PageDesigner` from a list/stack editor into a real builder integration over the shared document.
- Move YAPPC-specific shell and panel composition into:
  - `@yappc/ui`
  - `@yappc/development-ui`
- Keep YAPPC workflow semantics, project context, and product routes out of shared packages.

### 11.4 Acceptance

- The same document can be edited through layout-first and canvas-first surfaces.
- YAPPC consumes only public builder/canvas APIs.

## 12. Phase 5: Preview Runtime Security, Privacy, And Trust

### 12.1 Objective

Make preview execution policy-complete and derived from shared trust rules.

### 12.2 Shared work

#### `platform/typescript/ui-builder/preview`

- Define explicit preview profiles by trust level and capability set.
- Derive iframe sandbox and CSP from:
  - document trust level,
  - component preview restrictions,
  - product/runtime mode.
- Make the trusted/untrusted/semi-trusted model explicit and testable.
- Ensure preview host/iframe communication uses only the typed preview protocol.
- Emit preview lifecycle telemetry through `@ghatana/platform-events`.

### 12.3 YAPPC work

#### `products/yappc/frontend/web/src/components/studio/LivePreviewPanel.tsx`

- Replace hardcoded sandbox strings with policy-derived sandbox/CSP application.
- Surface preview trust state, fallback state, and diagnostics visibly in the UI.
- Preserve user visibility when preview is downgraded, blocked, or review-required.

### 12.4 Acceptance

- No preview surface hardcodes permissive sandbox settings.
- Preview behavior changes when trust level or contract restrictions change.
- All preview policy decisions are inspectable and test-covered.

## 13. Phase 6: AI, O11y, Privacy, Security, And Observability As First-Class Capabilities

### 13.1 Objective

Deliver the “full power with near-zero cognitive load” model without hiding actions from users.

### 13.2 Shared work

#### `platform/typescript/platform-events`

- Standardize events for:
  - builder operations,
  - AI proposals,
  - AI auto-applied changes,
  - preview policy decisions,
  - review-required actions,
  - privacy/security redactions,
  - import/export loss points,
  - collaboration state transitions.

#### `platform/typescript/ui-builder`

- Add AI suggestion hooks for:
  - missing prop repair,
  - token normalization,
  - auto-layout cleanup,
  - accessibility fixes,
  - responsive adjustments,
  - contract-aware property completion,
  - action wiring suggestions.
- Every AI action must record:
  - lineage,
  - confidence,
  - reason,
  - reversibility,
  - review status.

#### `platform/typescript/design-system` and `ds-schema`

- Strengthen accessibility declarations and preview/runtime disclosure support.
- Add automated o11y checks for component contracts where feasible.

### 13.3 YAPPC work

#### `@yappc/ai`

- Keep product-specific assistant experiences, prompts, chat flows, and domain-specific AI interactions here.
- Reuse shared builder AI hooks and event contracts instead of inventing product-only action models.

#### `@yappc/artifact-compiler`

- Use it for artifact inference, reverse engineering, import, migration recommendations, and semantic recovery from legacy assets.
- Keep the imported result mapped into the shared `BuilderDocument` contract.

### 13.4 Acceptance

- AI is useful by default but never opaque.
- Accessibility, privacy, security, and observability checks are present in authoring flows, not bolted on after export.

## 14. Phase 7: YAPPC Productization And UX Simplification

### 14.1 Objective

Deliver a genuinely simple but powerful authoring experience.

### 14.2 UX principles

- Common tasks require minimal decisions.
- Advanced power is available on demand, not always visible.
- Inline editing beats panel hunting when safe.
- Keyboard and drag/drop workflows are equally first-class.
- Status, save state, sync state, AI activity, review state, and preview state stay visible.

### 14.3 Product work

#### `@yappc/ui`

- Own product-facing editor shells, toolbars, side panels, and route-level composition.
- Reuse `@ghatana/design-system` instead of creating new low-level controls.

#### `@yappc/development-ui`

- Own dev-only diagnostics panels, contract inspection affordances, preview policy status chips, and governance UIs needed inside YAPPC.

#### `@yappc/state`

- Own editor session state, project/workspace integration, autosave status, and route/session orchestration.

#### `@yappc/collab`

- Own presence, room lifecycle, and CRDT session wiring.
- Use `@ghatana/realtime` for generic transport helpers rather than inventing parallel networking utilities.

### 14.4 Acceptance

- The editor feels simpler to use while supporting more capability.
- Product-only semantics remain in YAPPC packages instead of leaking into shared builder/canvas libraries.

## 15. Phase 8: Release Gates And Definition Of Done

### 15.1 Existing repo gates that must be honored

The implementation must align with existing repo governance and CI workflows, including:

- `.github/workflows/yappc-fe-canvas-governance.yml`
- `.github/workflows/yappc-fe-ui-quality.yml`
- `.github/workflows/ui-package-gates.yml`
- `.github/workflows/accessibility-audit.yml`
- existing Storybook, visual-regression, route-validation, performance, and security workflows where applicable

### 15.2 Mandatory DoD for this program

- Shared package type-checks pass.
- Shared package tests execute and pass.
- YAPPC frontend typecheck, lint, tests, and build pass.
- Accessibility checks pass for critical builder surfaces.
- Public API boundary checks pass.
- No product-specific builder logic is added to shared packages.
- No shared foundational logic is left trapped in YAPPC app routes or page-level components.
- Preview trust policy is enforced and test-covered.
- Round-trip import/export fidelity is measured and acceptable.
- Observability events exist for critical edit, preview, AI, and sync flows.

## 16. Implementation Sequence

This is the recommended execution order.

1. Fix shared test harness and failing package setup.
2. Refactor YAPPC `PageDesigner` to preserve a stable `BuilderDocument`.
3. Fix lossy adapter behavior and add full round-trip regression tests.
4. Replace hardcoded preview sandbox behavior with shared trust-policy enforcement.
5. Enrich design-system contracts until YAPPC local schemas become compatibility-only.
6. Expand shared builder operations, validation, and projections.
7. Converge canvas and page-builder surfaces on the same document.
8. Layer in AI, observability, privacy, security, and collaboration completion.
9. Finish UX simplification and maintainer diagnostics in YAPPC and Studio.
10. Gate release on the full CI and governance matrix.

## 17. Immediate Backlog By Package

### 17.1 `platform/typescript/ui-builder`

- Repair Vitest setup and renderer test environment.
- Add stable document revision semantics.
- Expand validation and import/export coverage.
- Complete preview policy integration.

### 17.2 `platform/typescript/testing`

- Add shared Vitest setup utilities and builder preview fixtures.

### 17.3 `platform/typescript/ds-schema`

- Complete builder contract metadata and validation.

### 17.4 `platform/typescript/canvas`

- Remove or isolate product-specific logic.
- Expose only the needed public builder-facing APIs.

### 17.5 `products/yappc/frontend/web`

- Turn `PageDesigner` into a real shared-builder consumer.
- Remove hardcoded preview policy behavior.
- Reduce local builder logic to integration-only code.

### 17.6 `products/yappc/frontend/libs/yappc-ui`

- Own product-specific editor shell and authoring UX components.

### 17.7 `products/yappc/frontend/libs/yappc-state`

- Own session/workspace/editor orchestration and autosave state.

### 17.8 `products/yappc/frontend/libs/collab`

- Own YAPPC collaboration lifecycle on top of shared transport/helpers.

### 17.9 `products/yappc/frontend/libs/yappc-ai`

- Own product AI surfaces while reusing shared builder AI metadata and events.

### 17.10 `products/yappc/frontend/libs/yappc-artifact-compiler`

- Own import/reverse-engineering pipelines and produce shared builder documents as outputs.

## 18. Success Criteria

This program is complete when the repo can honestly say all of the following are true:

- The builder is production-grade, not scaffold-grade.
- The document model is stable, durable, observable, and collaboration-ready.
- The design system is the real semantic source of truth for authoring and runtime behavior.
- The preview runtime obeys explicit trust and privacy policy.
- The UX is simpler while the platform is more powerful.
- AI is pervasive, helpful, visible, and governable.
- Shared vs YAPPC ownership is clean and enforceable.

## 19. Final Rule

If a change is useful but causes shared/product boundary leakage, duplicative abstractions, weakened typing, hidden AI behavior, or reduced diagnosability, it does not meet the standard for this implementation plan and should be rejected or redesigned.
