# UI Builder Production Hardening To-Do List

**Created:** 2026-04-21  
**Source documents:**
- `docs/architecture/UI_BUILDER_PRODUCTION_HARDENING_IMPLEMENTATION_PLAN_2026-04-21.md`
- `docs/architecture/LIVE_UI_BUILDER_AND_EXECUTION_PLATFORM_ARCHITECTURE.md`
- `.github/copilot-instructions.md`

<!-- COMPLETED PHASES REMOVED: 0 (Guardrails), 1 (Test Harness), 2 (ds-registry builder queries + schemas.ts migration), 3 (round-trip loss reporting: codegen stateVariants/responsiveVariants/protected/confidence-decay tests), 4 (canvas public API narrowing), 5 (Preview Trust), 6 (AI hooks in ui-builder + platform-events, useBuilderAI in yappc-ai, toBuilderDocument in yappc-artifact-compiler), 7 (YAPPC shell + diagnostics + collab + state). -->

## How To Use This List

- Keep this file as a to-do list only.
- When a task is completed, delete it from this file instead of marking it done.
- Do not add progress notes, owner fields, or status markers here.
- Keep shared platform work in `platform/typescript/*` and product-specific work in `products/yappc/frontend/libs/*` and `products/yappc/frontend/web`.

## Canonical Placement Rules

- `platform/typescript/ui-builder`: canonical home for `BuilderDocument`, builder operations, validation, import/export, codegen, preview protocol, and trust policy.
- `platform/typescript/ds-schema`: canonical home for component contracts, builder metadata, preview restrictions, accessibility/privacy/AI metadata, and runtime restrictions.
- `platform/typescript/ds-registry`: canonical home for contract lookup, manifest lookup, versioning, and stable contract resolution.
- `platform/typescript/ds-governance`: canonical home for contract quality and compatibility rules.
- `platform/typescript/canvas`: canonical home for generic spatial editing, geometry, selection, snapping, drag/drop, resize, and viewport behavior.
- `platform/typescript/platform-events`: canonical home for shared builder, preview, AI, privacy, security, and observability event contracts.
- `platform/typescript/testing` (`@ghatana/platform-testing`): canonical home for shared frontend test helpers and Vitest setup utilities.
- `platform/typescript/design-system`: high-level runtime facade for apps and high-level product packages, not the foundational home for builder internals.
- `products/yappc/frontend/libs/yappc-ui`: canonical home for YAPPC product-facing editor shell and authoring UX.
- `products/yappc/frontend/libs/yappc-development-ui`: canonical home for YAPPC developer diagnostics, governance surfaces, and preview-policy inspection UI.
- `products/yappc/frontend/libs/yappc-state`: canonical home for YAPPC editor session, autosave, and workspace orchestration state.
- `products/yappc/frontend/libs/collab`: canonical home for YAPPC collaboration session lifecycle and presence behavior.
- `products/yappc/frontend/libs/yappc-ai`: canonical home for YAPPC product-specific AI assistant flows.
- `products/yappc/frontend/libs/yappc-artifact-compiler`: canonical home for YAPPC import, reverse engineering, artifact synthesis, and migration into shared builder documents.
- `products/yappc/frontend/web/src/components/canvas/page/*`: keep only compatibility shims and integration glue here while shared builder ownership is completed.

## Phase 2 - Design-System Contract Completion

- `platform/typescript/ds-schema`: cover accessibility obligations in shared component contracts.
- `platform/typescript/ds-schema`: cover AI-safe versus review-required operations in shared component contracts.
- `platform/typescript/ds-schema`: cover telemetry and observability expectations in shared component contracts.
- `platform/typescript/ds-registry`: support contract versioning, manifest lookup, and stable preview/codegen resolution.
- Keep only legacy parsing and migration glue in YAPPC page-builder schema code.
- Register shared starter contracts for button, card, text field, typography, and box/container components.

## Phase 3 - Shared Builder Core Completion

- `platform/typescript/ui-builder`: align codegen with contract metadata instead of local heuristics.
- `platform/typescript/ui-builder`: align renderers with contract metadata instead of local heuristics.

## Phase 4 - Canvas And Page-Builder Convergence

- `platform/typescript/canvas`: keep shared canvas behavior limited to generic spatial editing primitives.
- `platform/typescript/canvas`: isolate or remove product-specific behavior from shared canvas surfaces.
- `products/yappc/frontend/web/src/components/canvas/page/PageDesigner.tsx`: migrate from list-first editing to a real shared-builder integration.
- Remove shared foundational builder logic from YAPPC route-level and page-level components.

## Phase 6 - AI, Accessibility, Privacy, Security, And Observability

- `platform/typescript/design-system`: strengthen accessibility declarations needed by builder and preview flows.
- `platform/typescript/ds-schema`: strengthen preview and runtime disclosure support for component contracts.
- `platform/typescript/design-system` and `platform/typescript/ds-schema`: add automated accessibility checks for component contracts where feasible.

## Phase 7 - YAPPC Productization And UX Simplification

- `products/yappc/frontend/libs/collab`: use `@ghatana/realtime` for generic transport helpers instead of parallel networking utilities.
- Simplify default authoring flows so common tasks require minimal decisions.
- Keep advanced capability available through progressive disclosure rather than always-visible complexity.
- Keep inline editing, keyboard workflows, and drag/drop workflows first-class in the editor UX.

## Execution Order

- Refactor `PageDesigner` to preserve a stable `BuilderDocument`.
- Fix lossy adapter behavior and add round-trip regression tests.
- Enrich shared design-system contracts until YAPPC local schemas are compatibility-only.
- Expand shared builder operations, validation, and document projections.
- Converge canvas and page-builder surfaces on the same document model.
- Layer in accessibility, privacy, and security completion.
- Finish YAPPC UX simplification.
- Run the full CI and governance release gates.