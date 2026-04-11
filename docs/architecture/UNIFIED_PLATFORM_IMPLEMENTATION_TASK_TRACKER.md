# Unified Platform Implementation Task Tracker

One-line task tracker derived from the unified plan. Use this file as the implementation checklist and the main plan for scope, dependencies, and acceptance details.

## Phase 0

- [ ] 0.1 Create `@ghatana/platform-events` as the canonical telemetry, AI, security, privacy, visibility, and observability package.
- [ ] 0.2 Add shared AI visibility atoms and molecules to `@ghatana/design-system`.
- [ ] 0.3 Move `AISuggestion` and related AI types from canvas into `@ghatana/platform-events`.
- [ ] 0.4 Register `@ghatana/platform-events` in workspace and build configuration.
- [ ] 0.5 Ship the Human Control Plane contract, events, UI controls, canvas enforcement, and kill switch wiring.

## Phase 1

- [ ] 1.1 Audit `@ghatana/design-system` and extract misplaced audit/privacy/security/voice/NLP concerns.
- [ ] 1.2 Create `@ghatana/ds-schema` for DTCG-aligned token, component, theme, pattern, and compatibility schemas.
- [ ] 1.3 Create `@ghatana/ds-registry` for component, token, theme, and pattern registration and compatibility checks.
- [ ] 1.4 Harden `@ghatana/tokens` to the DTCG schema and registry-backed validation flow.
- [ ] 1.5 Create internal `@ghatana/primitives` for token-driven layout and interaction primitives.
- [ ] 1.6 Create internal `@ghatana/ui` and re-export it through `@ghatana/design-system` without breaking the public API.
- [ ] 1.7 Add validated component contract metadata for the builder-safe design-system subset.
- [ ] 1.8 Create internal `@ghatana/patterns` for shared workflow and AI UX compositions.
- [ ] 1.9 Create `@ghatana/ds-governance` for naming, duplication, compatibility, and contribution gates.

## Phase 2

- [ ] 2.1 Restructure canvas into explicit `src/public` and `src/internal` layers.
- [ ] 2.2 Tighten `@ghatana/canvas` exports to public subpaths only.
- [ ] 2.3 Absorb `@ghatana/flow-canvas` into `@ghatana/canvas/flow` and remove the nested package.
- [ ] 2.4 Remove deprecated canvas facade packages after migrating all remaining consumers.
- [ ] 2.5 Move YAPPC-specific canvas actions out of platform canvas and into product-local code.
- [ ] 2.6 Implement the full AI-native canvas capability group on `@ghatana/canvas/ai`.
- [ ] 2.7 Add structured telemetry emission for all required canvas operations and AI flows.
- [ ] 2.8 Harden `@ghatana/canvas/testing` with render, interaction, telemetry, and AI contract helpers.
- [ ] 2.9 Migrate AEP and YAPPC to public canvas APIs only.
- [ ] 2.10 Complete canvas multi-mode support and the full public React API surface.

## Phase 3

- [ ] 3.1 Create the `@ghatana/ui-builder` package with `/react`, `/web`, `/preview`, and `/testing` subpaths.
- [ ] 3.2 Implement `DesignSystemModel` and `ComponentContract` as canonical builder bindings.
- [ ] 3.3 Implement `BuilderDocument`, bindings, actions, validation, and immutable document operations.
- [ ] 3.4 Implement `CodeProjection` with ownership markers and round-trip fidelity metadata.
- [ ] 3.5 Implement React code generation from `BuilderDocument`.
- [ ] 3.6 Implement the typed preview host protocol and sandbox profile mapping.
- [ ] 3.7 Migrate YAPPC `PageDesigner` onto `BuilderDocument` and platform codegen.
- [ ] 3.8 Migrate the YAPPC live preview server to the platform preview protocol.
- [ ] 3.9 Add builder operation telemetry and rollback export support.
- [ ] 3.10 Implement the `@ghatana/ui-builder/web` vanilla DOM/HTML/TS target.
- [ ] 3.11 Implement `SceneProjection` for builder-to-canvas artboard projection and edit round-trip.
- [ ] 3.12 Implement code import and ownership-aware reconciliation back into `BuilderDocument`.
- [ ] 3.13 Implement `BuilderDocument` persistence, autosave, version restore, and session recovery.

## Milestone M1

- [ ] M1 / 0.6 Deliver the first usable YAPPC authoring slice with one route for Design System, Page Designer, Canvas, Preview, Code, rollback, autonomy control, save, and resume.

## Phase 4

- [ ] 4.1 Create the governed `@ghatana/ds-cli` package.
- [ ] 4.2 Implement the `build-tokens` command for DTCG-compliant outputs.
- [ ] 4.3 Implement the `validate` command for tokens, contracts, references, and themes.
- [ ] 4.4 Implement the `audit` command for duplicate detection, a11y coverage, and governance checks.
- [ ] 4.5 Create `@ghatana/ds-generator` for preset materialization and brand customization.
- [ ] 4.6 Harden preview trust, sandboxing, device controls, fallback UX, and preview telemetry.
- [ ] 4.7 Bind the builder directly to validated design-system models and enforce story/contract parity.

## Phase 5

- [ ] 5.1 Complete AEP migration to canonical canvas/flow and shared design-system primitives.
- [ ] 5.2 Complete YAPPC canvas boundary cleanup and public API adoption.
- [ ] 5.3 Complete Data-Cloud topology and lineage migration onto canonical canvas packages.
- [ ] 5.4 Replace product-local AI status/review UI with shared design-system visibility primitives.
- [ ] 5.5 Replace duplicate product telemetry type definitions with `@ghatana/platform-events`.
- [ ] 5.6 Adopt the vanilla web target in one justified non-React product surface.
- [ ] 5.7 Run the cross-product UX consistency pass and enforce one stable authoring entrypoint per product.

## Phase 6

- [ ] 6.1 Create the Builder Studio maintainer app.
- [ ] 6.2 Create the Theme Studio maintainer app.
- [ ] 6.3 Create the Component Playground maintainer app.
- [ ] 6.4 Create the Canvas Diagnostics maintainer app.
- [ ] 6.5 Create the AI Review Console maintainer app.
- [ ] 6.6 Create the Import/Migration Lab maintainer app.
- [ ] 6.7 Create the Preview Lab maintainer app.