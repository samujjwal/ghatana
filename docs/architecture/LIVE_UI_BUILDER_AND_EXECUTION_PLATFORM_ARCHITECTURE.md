# Live UI Builder And Execution Platform Architecture

Reviewed and authored on 2026-04-11 from:
- current hardened design-system and canvas plans
- repo scan across `platform/typescript/*` and `products/yappc/*`
- current local builder, preview, and canvas-overlay implementations
- current official references for React, Vite, Storybook, MDN web platform APIs, and OpenTelemetry

---

## 1. Purpose

This document defines a hardened architecture for a **live UI builder and execution platform** that lets teams:

1. create and customize design systems,
2. design pages, screens, and components visually,
3. see and edit the generated code directly,
4. execute the result immediately in a live preview,
5. and move smoothly between design-system authoring, canvas authoring, configurator editing, and code editing without fragmenting the platform.

The initial runtime targets are:

- **React**
- **vanilla JS/TS + HTML + CSS**

This architecture uses the same first-class requirements established in the design-system and canvas plans:

- AI/ML is pervasive and explicit,
- observability and telemetry are first-class,
- security and privacy are first-class,
- accessibility is first-class,
- complete user visibility is required,
- human interruption is minimized but always possible,
- public API exposure remains minimal and deliberate.

---

## 2. Core Decision

The builder must be a **separate canonical platform capability** that composes:

- the **design system** as the semantic source of tokens, themes, recipes, and components,
- the **canvas platform** as one possible authoring surface,
- the **preview/execution runtime** as the place where output actually runs,
- and the **code projection layer** as the visible editable representation.

It should **not** be implemented by bloating:

- `@ghatana/design-system`,
- `@ghatana/canvas`,
- or product-local YAPPC builder code.

Instead:

- `@ghatana/design-system` remains the runtime design-system facade,
- `@ghatana/canvas` remains the spatial editing capability,
- a new builder capability becomes the orchestration layer that binds them together.

---

## 3. Current Repo Reality

### 3.1 Existing building blocks already in the repo

The repo already contains useful pieces:

- design-system runtime in `platform/typescript/design-system`
- token schema and theme schema work in `platform/typescript/theme/src/schema/*`
- canvas authoring and overlays in `platform/typescript/canvas`
- code editor runtime in `platform/typescript/code-editor`
- early YAPPC page designer in:
  - `products/yappc/frontend/web/src/components/canvas/page/PageDesigner.tsx`
  - `products/yappc/frontend/web/src/components/canvas/page/ComponentRenderer.tsx`
  - `products/yappc/frontend/web/src/components/canvas/page/schemas.ts`
  - `products/yappc/frontend/web/src/components/canvas/page/exportJSX.ts`
- live preview work in:
  - `products/yappc/tools/live-preview-server`
  - `products/yappc/frontend/web/src/components/studio/LivePreviewPanel.tsx`
  - `products/yappc/frontend/web/src/routes/app/project/preview.tsx`
- canvas UI-builder and live component overlays in:
  - `platform/typescript/canvas/src/ui-builder/index.ts`
  - `platform/typescript/canvas/src/react/LiveReactOverlay.tsx`
  - `platform/typescript/canvas/src/react/CodeEditorOverlay.tsx`

### 3.2 Current problems

The current pieces are useful, but fragmented:

- page-designer schemas are too narrow and product-local,
- code export is one-way and React-only,
- live preview is not yet a hardened multi-mode execution model,
- canvas UI-builder is element-oriented rather than design-system-contract-oriented,
- design-system metadata is not yet the canonical source for configurators and builder palettes,
- security, visibility, privacy, and o11y rules are not yet unified across design, preview, and code generation.

### 3.3 Practical implication

We should **reuse** these assets, but only after they are reorganized under a single canonical builder model.

---

## 4. Product Goal

The product goal is to let a team do all of the following in one coherent system:

- define or customize tokens, themes, and component recipes,
- browse a governed library of highly composable components and patterns,
- drag components or patterns onto a page or freeform canvas,
- resize, reposition, and configure them,
- inspect and edit the generated code,
- switch between visual, structured, and code-oriented workflows,
- execute the result immediately,
- and keep design-system intent, runtime behavior, preview output, and source code aligned.

This should work in both:

- **tree/layout-first page building**
- **spatial/canvas-first composition**

with the same canonical model underneath.

---

## 5. Non-Goals

- Not a generic website builder that treats design-system semantics as optional.
- Not a pure whiteboard with code generation bolted on later.
- Not a code-only scaffolder with a thin visual shell.
- Not a builder that hides its actions from users.
- Not a runtime that executes arbitrary untrusted code without sandboxing, policy, and visibility.
- Not a public package surface that forces product teams to understand internal registries, compilers, or preview plumbing.

---

## 6. Fundamental Requirements

### 6.1 Functional requirements

The platform must support:

- component authoring,
- page/screen authoring,
- pattern composition,
- design-system customization,
- drag/drop and insertion,
- resize, reposition, and reflow,
- property editing,
- responsive editing,
- live execution,
- code visibility and code editing,
- import/export,
- React runtime output,
- vanilla JS/TS + HTML + CSS runtime output.

### 6.2 First-class cross-cutting requirements

The platform must treat the following as non-optional:

- **Security**
  - sandboxed previews,
  - trust modes,
  - policy-controlled execution,
  - capability allowlists,
  - CSP-aware rendering,
  - safe host/preview communication.
- **Privacy**
  - data classification,
  - AI data minimization,
  - secret redaction,
  - explicit control over what leaves the editor.
- **Observability / o11y**
  - structured telemetry,
  - traceable long-running operations,
  - audit-friendly action history,
  - operator diagnostics.
- **Accessibility**
  - keyboard authoring,
  - accessible component contracts,
  - accessible previews,
  - screen-reader visibility of state changes.
- **Complete visibility**
  - visible status of generation, synchronization, preview, validation, and AI actions,
  - visible provenance for code and configuration changes.
- **Minimal user interruption**
  - automate low-risk reversible work,
  - escalate only on policy, confidence, ambiguity, or impact thresholds.
- **AI/ML-native assistance**
  - explicit and implicit suggestions,
  - validation,
  - repair,
  - generation,
  - mapping,
  - explanation,
  - migration help.

---

## 7. Canonical First-Class Objects

The builder should revolve around a small set of first-class objects that remain stable across design-system authoring, page building, preview, and code generation.

### 7.1 `DesignSystemModel`

Defines:

- tokens,
- themes,
- component recipes,
- pattern definitions,
- slot and variant rules,
- accessibility and disclosure defaults,
- security/privacy policy defaults.

This model comes from the design-system platform, not from the page builder itself.

### 7.2 `ComponentContract`

Every builder-available component must have a canonical contract defining:

- component identity,
- category,
- runtime targets supported,
- props schema,
- slot schema,
- style schema,
- event schema,
- state variants,
- responsive behavior metadata,
- accessibility contract,
- security and privacy metadata,
- telemetry hooks,
- codegen adapters,
- preview policy.

This becomes the source for:

- palette entries,
- configurator forms,
- Storybook controls,
- canvas nodes,
- runtime renderers,
- code generation templates,
- validation.

### 7.3 `ConfiguratorSchema`

Defines how a component or pattern is configured in the builder UI.

It should describe:

- fields,
- field groups,
- defaults,
- constraints,
- conditional visibility,
- advanced vs common controls,
- inline help,
- review-sensitive fields,
- AI-safe vs AI-sensitive fields.

### 7.4 `BuilderDocument`

The canonical representation of a page, screen, or component composition.

It should include:

- component tree,
- layout constraints,
- responsive variants,
- bindings,
- actions,
- local state references,
- design-system references,
- canvas scene metadata where applicable,
- preview settings,
- audit metadata.

### 7.5 `SceneProjection`

Defines how the same builder document appears in:

- a tree/layout editor,
- a freeform canvas,
- a responsive artboard view,
- a component/pattern workbench.

Canvas is therefore a **projection**, not the canonical source of truth.

### 7.6 `RuntimeProjection`

Defines how the builder document becomes executable output for:

- React,
- vanilla DOM/web runtime,
- isolated preview runtime,
- export/build pipelines.

### 7.7 `CodeProjection`

Defines the visible source representation:

- React TSX,
- HTML/CSS/TS,
- optional structured intermediate files,
- ownership markers for generated regions,
- round-trip metadata.

### 7.8 `BuilderOperation`

Every meaningful system action should be modeled as a visible operation:

- generate layout,
- import component,
- map tokens,
- apply fix,
- rebuild preview,
- migrate code,
- publish pattern.

Each operation should have:

- actor,
- trigger,
- risk level,
- review state,
- rollback semantics,
- telemetry and audit metadata.

---

## 8. Common Model: Four Synchronized Representations

The platform should maintain four synchronized representations of the same UI artifact:

1. **Design-system representation**
   - tokens, themes, recipes, component contracts.
2. **Builder representation**
   - builder document and configurator state.
3. **Visual representation**
   - tree editor, artboards, responsive views, or canvas projection.
4. **Executable/code representation**
   - live runtime preview and visible source code.

The system must avoid making any one visual mode the real source of truth.

The canonical source should be:

- `DesignSystemModel` for semantics,
- `BuilderDocument` for composed UI,
- and explicit projections for canvas and code.

---

## 9. Recommended Package Organization

### 9.1 Canonical package strategy

Introduce one canonical builder package:

- `@ghatana/ui-builder`

Allow a small set of justified public subpaths:

- `@ghatana/ui-builder/react`
- `@ghatana/ui-builder/web`
- `@ghatana/ui-builder/preview`
- `@ghatana/ui-builder/testing`

Everything else should be internal.

### 9.2 Package roles

- `@ghatana/design-system`
  - semantic component runtime facade
- `@ghatana/canvas`
  - spatial authoring surface and related primitives
- `@ghatana/code-editor`
  - code editing runtime
- `@ghatana/ui-builder`
  - orchestration layer for contracts, builder documents, configurators, preview, and code visibility

### 9.3 Shared apps vs shared libraries

To avoid bloat, split the builder ecosystem into:

- **shared runtime libraries**
  - runtime builder APIs that product code depends on
- **shared maintainer apps**
  - Builder Studio
  - Component Workbench
  - Preview Lab
  - Import/Migration Lab
  - Accessibility and Policy Lab
  - AI review/replay tools

The following should live in shared apps, not the runtime library:

- exploratory palettes,
- diagnostics dashboards,
- migration wizards,
- visual benchmark harnesses,
- security/policy replay tools,
- heavy internal inspection UIs.

### 9.4 Public API surface

The root package should stay small. It should expose:

- `BuilderProvider`
- `ComponentContract`
- `ConfiguratorSchema`
- `BuilderDocument`
- `BuilderOperation`
- `createBuilderDocument`
- `registerComponentContract`
- `validateBuilderDocument`
- `renderBuilderDocument`
- `generateCode`

`@ghatana/ui-builder/react` should expose:

- React renderer adapter,
- React contract helpers,
- React preview host integration,
- React import/export helpers.

`@ghatana/ui-builder/web` should expose:

- native DOM/web renderer,
- custom-element integration helpers,
- HTML/CSS/TS import/export helpers.

`@ghatana/ui-builder/preview` should expose:

- preview host/client protocol,
- sandbox profile definitions,
- preview session contracts,
- telemetry and status hooks for preview lifecycle.

`@ghatana/ui-builder/testing` should expose:

- schema fixtures,
- preview harness helpers,
- accessibility contract assertions,
- round-trip and codegen test helpers.

### 9.5 Internal-only modules

Keep these internal:

- compilers,
- serializers,
- diff/merge internals,
- code ownership mapping internals,
- AI orchestration internals,
- preview process control internals,
- import parsers,
- canvas synchronization internals,
- registry storage internals.

---

## 10. Component Contract Strategy

### 10.1 Builder-safe subset first

The first release should support a **builder-safe subset** of components:

- layout primitives,
- typography,
- buttons,
- inputs,
- cards,
- lists,
- navigation shells,
- common feedback and overlay components,
- charts/tables only where contract quality is high enough.

These components must be:

- schema-described,
- visually configurable,
- runtime-safe,
- codegen-safe,
- accessibility-reviewed,
- telemetry-capable.

### 10.2 Contract metadata must drive everything

Each component contract should drive:

- palette categorization,
- drag/drop payloads,
- default layout rules,
- resize handles,
- configurator forms,
- responsive behavior controls,
- generated code,
- preview instrumentation,
- Storybook args/controls generation,
- documentation,
- tests.

### 10.3 Highly composable patterns

Patterns should be first-class compositions of component contracts, not ad hoc templates.

Examples:

- auth form,
- app shell,
- marketing hero,
- dashboard grid,
- settings page,
- table + filter + details split,
- AI review panel,
- activity timeline,
- approval workspace.

Patterns should remain configurable and decomposable.

---

## 11. Renderer Strategy

### 11.1 React target

React should be the first deeply supported target.

The React target should:

- render directly from builder documents,
- map component contracts to `@ghatana/design-system` React components,
- support local state and action bindings,
- support HMR-driven preview,
- generate readable TSX with stable imports and controlled ownership markers.

### 11.2 Vanilla JS/TS + HTML + CSS target

Vanilla support should be a first-class runtime target, not an afterthought.

The recommended approach is:

- use standard DOM output for simple structures,
- use **custom elements** for reusable interactive widgets where encapsulation is needed,
- use CSS variables derived from design tokens,
- use Shadow DOM selectively for encapsulation where it helps more than it hurts,
- avoid forcing the vanilla target to be a React runtime in disguise.

This aligns well with current web platform capabilities:

- custom elements give us framework-neutral reusable tags,
- Shadow DOM gives optional encapsulation,
- token-driven CSS variables keep theming portable.

### 11.3 Do not make raw HTML the primary source of truth

HTML/CSS/JS is an output target and import source, but not the canonical authoring model.

Otherwise we lose:

- structured configurators,
- governed accessibility contracts,
- reliable round-tripping,
- AI-safe automation,
- stable code generation.

---

## 12. Preview And Execution Model

### 12.1 Two preview modes

The platform should support two execution modes.

1. **Trusted local dev preview**
   - for workspace-controlled code,
   - powered by Vite dev server and HMR,
   - optimized for fast iteration.

2. **Sandboxed preview**
   - for imported, generated, or partially untrusted content,
   - runs in restrictive iframe sandbox profiles,
   - uses explicit preview protocols and capability constraints.

### 12.2 Preview host architecture

Use a dedicated preview host rather than embedding execution logic directly in the main builder UI.

The preview host should manage:

- session lifecycle,
- environment bootstrapping,
- hot updates,
- structured preview events,
- error boundaries,
- device and viewport modes,
- reload and rollback,
- telemetry and audit.

### 12.3 Communication model

The builder shell and preview host should communicate using explicit messages rather than implicit global state.

Use:

- iframe boundary when isolation is required,
- `postMessage` contracts,
- typed events,
- correlation IDs,
- explicit trust labels.

### 12.4 Visible preview status

Users must always be able to see:

- preview mode,
- trust level,
- active device frame,
- last successful render time,
- active errors,
- whether code is running from generated output, imported source, or mixed ownership.

---

## 13. Code Visibility And Round-Trip Strategy

### 13.1 Code must be visible by default

The system should not hide generated code behind export-only flows.

Users should be able to:

- inspect the code,
- compare design vs runtime output,
- edit it,
- understand ownership and sync status.

### 13.2 Round-trip fidelity tiers

Every artifact should declare its fidelity tier:

1. **Lossless**
   - builder document and code remain fully synchronized.
2. **Assisted**
   - the system can reconcile most changes but may require review.
3. **Preview-only**
   - execution is possible, but reliable structural round-trip is not guaranteed.

### 13.3 Ownership markers

Generated code should use explicit ownership zones so the system knows:

- which regions are generated,
- which regions are user-authored,
- which regions are protected,
- which regions require manual merge.

### 13.4 Code import strategy

Imports from existing React or HTML/CSS/JS should be classified as:

- exact import,
- partial import,
- semantic import with manual review,
- or preview-only import.

The platform should not pretend every imported artifact can be losslessly round-tripped.

---

## 14. Canvas Integration Strategy

### 14.1 Canvas is a projection, not the canonical builder

Canvas should be used where it adds value:

- freeform exploration,
- artboard composition,
- responsive comparisons,
- visual grouping,
- drag/drop and repositioning,
- hybrid diagram + UI flows.

But the canonical source remains the builder document and design-system contracts.

### 14.2 What canvas should render for builder work

Canvas should support:

- artboards/pages,
- component instances,
- slot outlines,
- grid and spacing overlays,
- responsive breakpoint comparisons,
- AI ghost suggestions,
- live React overlays for interactive components,
- code-editor overlays where code is embedded in context.

### 14.3 What stays out of shared canvas runtime

Do not move all builder logic into `@ghatana/canvas`.

Keep out:

- design-system registries,
- builder compilers,
- preview host logic,
- code ownership and reconciliation,
- framework-specific codegen.

Canvas should stay an authoring surface and projection layer.

---

## 15. Design System Integration Strategy

### 15.1 Design system remains the semantic source

The builder should consume the design-system platform for:

- tokens,
- themes,
- component contracts,
- recipes,
- accessibility defaults,
- disclosure defaults,
- policy defaults.

### 15.2 Design-system editing should also use the builder model

Users should be able to use the same platform to:

- edit tokens,
- preview theme changes,
- inspect component contracts,
- compose patterns,
- publish updated presets.

### 15.3 Do not duplicate design-system metadata inside the builder

The builder may cache and project metadata, but it should not become a second source of truth for:

- token values,
- component anatomy,
- variant names,
- accessibility contracts.

---

## 16. Storybook And Workbench Strategy

Storybook should be treated as a **component workbench and contract surface**, not the live product runtime.

It is useful for:

- documenting args and controls,
- interaction tests,
- accessibility checks,
- visual regression flows,
- contract examples.

The builder should be able to derive Storybook-friendly control metadata from component contracts and configurator schemas.

This gives us:

- one source for configurators,
- one source for docs,
- one source for component testing controls.

---

## 17. Security Model

### 17.1 Trust modes

Support explicit trust modes:

- **trusted workspace code**
- **generated trusted code**
- **imported review-required code**
- **untrusted preview content**

Every preview and generation flow must declare its trust mode.

### 17.2 Sandboxed preview requirements

Sandboxed preview should use:

- restrictive iframe sandbox profiles,
- restrictive CSP defaults,
- explicit capability grants for scripts/forms/network where needed,
- preview-origin separation where feasible,
- structured message protocols rather than open DOM reach-through.

### 17.3 Execution restrictions

The platform should not allow arbitrary code execution in the main authoring shell.

Default rule:

- execution happens in a preview host,
- not inside the builder shell itself.

### 17.4 Security as first-class builder metadata

Each component contract should include:

- allowed external resource types,
- inline-script restrictions,
- required sandbox level,
- privileged action requirements,
- unsafe content warnings.

---

## 18. Privacy Model

The platform must classify builder data before it is used by AI or external services.

Minimum categories:

- public design metadata,
- internal product metadata,
- sensitive content,
- secrets/credentials,
- regulated data.

The system should:

- redact secrets before preview logging or AI use,
- avoid sending full documents externally by default,
- send only the minimal structural context needed for AI tasks,
- log when external AI or services are used,
- surface user-visible disclosure for sensitive operations.

---

## 19. Accessibility Model

### 19.1 Builder accessibility

The builder itself must support:

- keyboard-driven selection and insertion,
- keyboard resize/move alternatives,
- focus-visible controls,
- screen-reader announcements for major operations,
- reduced-motion compatibility.

### 19.2 Built artifact accessibility

Every component contract and pattern should declare:

- semantic role expectations,
- keyboard behavior,
- label requirements,
- focus model,
- color-contrast requirements,
- motion/disclosure behavior.

### 19.3 Accessibility review as default automation

The platform should proactively surface:

- missing labels,
- focus traps,
- invalid heading structure,
- contrast problems,
- inaccessible dynamic-state behavior,
- layout issues across breakpoints.

---

## 20. Observability / O11y Model

### 20.1 Required event families

At minimum, instrument:

- `builder.document.loaded`
- `builder.component.inserted`
- `builder.component.moved`
- `builder.component.resized`
- `builder.component.configured`
- `builder.pattern.applied`
- `builder.preview.started`
- `builder.preview.updated`
- `builder.preview.failed`
- `builder.codegen.completed`
- `builder.codegen.failed`
- `builder.import.started`
- `builder.import.completed`
- `builder.import.review_required`
- `builder.ai.suggestion.shown`
- `builder.ai.suggestion.accepted`
- `builder.ai.suggestion.rejected`
- `builder.ai.action.applied`
- `builder.review.requested`
- `builder.review.completed`

### 20.2 Trace model

Create traceable spans for:

- preview boot and reload,
- code generation,
- import/parse/reconcile,
- responsive validation,
- AI generation and repair,
- publish/export flows.

### 20.3 Visibility model

Users must be able to see:

- what the builder is doing now,
- what changed,
- what is still syncing,
- which updates are AI-generated,
- which items need review,
- which preview is authoritative,
- which code is safe to edit directly.

---

## 21. AI/ML As A First-Class Builder Concern

### 21.1 Explicit AI capabilities

The platform should support:

- prompt-to-page,
- prompt-to-component,
- prompt-to-theme,
- prompt-to-pattern,
- explain-this-screen,
- convert-this-layout-to-design-system-components,
- repair accessibility,
- repair responsiveness,
- generate code comments and migration notes.

### 21.2 Implicit AI capabilities

The platform should also support:

- auto-mapping imported UI to known components,
- auto-suggesting token usage,
- responsive layout suggestions,
- spacing/alignment recommendations,
- accessibility fix suggestions,
- security and privacy warnings,
- code cleanup and simplification recommendations,
- pattern extraction suggestions from repeated structures.

### 21.3 User-involvement policy

The system should proceed autonomously when the action is:

- low-risk,
- reversible,
- explainable,
- within policy,
- and clearly visible.

The system should interrupt for review when the action is:

- destructive,
- externally visible,
- structurally ambiguous,
- low-confidence,
- privacy-sensitive,
- or security-relevant.

### 21.4 AI visibility contract

For every AI-assisted action, show:

- intent,
- rationale,
- confidence,
- affected regions,
- review state,
- rollback path,
- telemetry/audit link.

---

## 22. Recommended Shared Apps

To keep the runtime libraries small, build these as shared apps/tools:

1. **Builder Studio**
   - the main authoring shell.
2. **Component Workbench**
   - Storybook-backed component/pattern validation and args/configurator inspection.
3. **Preview Lab**
   - sandbox profiles, device modes, performance checks, preview diagnostics.
4. **Import/Migration Lab**
   - code import, round-trip analysis, diff inspection, migration review.
5. **Accessibility And Policy Lab**
   - rule visualization, issue triage, remediation flows.
6. **AI Review Console**
   - history, evidence, approvals, overrides, audit replay.

---

## 23. Concrete Implementation Plan

### 23.1 Phase 0: Contract freeze and inventory

Do first:

- inventory all builder-like code in platform and products,
- freeze the first-class object model,
- identify which current code can be promoted, adapted, or retired,
- define the initial builder-safe component subset.

### 23.2 Phase 1: Canonical contracts

Build:

- `ComponentContract`
- `ConfiguratorSchema`
- `BuilderDocument`
- validation and migration schemas
- minimal registry implementation

Definition of done:

- one component contract can drive palette, configurator, preview, and codegen.

### 23.3 Phase 2: React-first builder runtime

Build:

- `@ghatana/ui-builder`
- React renderer adapter
- code visibility pane using `@ghatana/code-editor`
- preview host contracts
- high-quality React TSX generation for the builder-safe subset

Definition of done:

- a builder-authored React page can be edited visually, previewed live, and exported as readable TSX.

### 23.4 Phase 3: Preview hardening

Build:

- trusted Vite-backed preview path,
- sandboxed iframe preview path,
- preview telemetry,
- preview error/status UI,
- device and responsive controls,
- explicit preview trust labeling.

Definition of done:

- execution is fast in trusted mode and safe in sandboxed mode, with clear user visibility in both.

### 23.5 Phase 4: Design-system alignment

Build:

- direct consumption of design-system contracts,
- token/theme editing hooks,
- component contract registry backed by design-system metadata,
- Storybook/control generation alignment.

Definition of done:

- builder palettes and configurators come from governed design-system contracts instead of duplicated local schemas.

### 23.6 Phase 5: Vanilla web target

Build:

- native DOM/web renderer,
- custom-element-backed complex widgets where justified,
- HTML/CSS/TS output generation,
- preview path for the web target,
- import/export and validation flows for the supported subset.

Definition of done:

- a builder-authored artifact can execute as standards-based web output without React.

### 23.7 Phase 6: Canvas projection

Build:

- builder-to-canvas projection,
- artboards,
- drag/drop placement,
- move/resize handles,
- overlay integration for live React and code panels,
- responsive comparison canvases.

Definition of done:

- canvas becomes a first-class editing surface without becoming the canonical source of truth.

### 23.8 Phase 7: AI, security, privacy, and o11y hardening

Build:

- AI suggestion and repair flows,
- policy-aware automation,
- preview trust model,
- privacy classification and redaction,
- accessibility review automation,
- telemetry and audit dashboards.

Definition of done:

- the builder can act proactively with full visibility, measurable behavior, and policy control.

### 23.9 Phase 8: Import and round-trip maturity

Build:

- React import classification,
- HTML/CSS/JS import classification,
- ownership markers,
- diff and reconciliation flows,
- review-required states and merge tooling.

Definition of done:

- the platform can honestly distinguish lossless, assisted, and preview-only flows.

---

## 24. Recommended Next Artifacts

1. **UI Builder Public Contract Spec**
   - exact exports, allowed import paths, approved symbols, forbidden deep imports.

2. **Component Contract Spec**
   - schema for props, slots, events, state variants, accessibility, security, privacy, and telemetry metadata.

3. **Builder Document Spec**
   - canonical page/component composition model, responsive variants, scene metadata, migration rules.

4. **Preview Security Spec**
   - trust modes, iframe sandbox profiles, CSP defaults, postMessage protocol, preview capabilities.

5. **Code Projection And Round-Trip Spec**
   - ownership markers, lossless vs assisted vs preview-only tiers, React and web-target codegen rules.

6. **Builder AI Autonomy Spec**
   - review thresholds, approval modes, visibility contract, rollback behavior.

7. **Builder Studio Plan**
   - how Builder Studio, Component Workbench, Preview Lab, and AI Review Console are split from runtime libraries.

---

## 25. External References

- React docs:
  - https://react.dev/
  - https://react.dev/reference/react-dom/client/createRoot
- Vite JavaScript API:
  - https://vite.dev/guide/api-javascript
- Vite HMR API:
  - https://vite.dev/guide/api-hmr
- Storybook args:
  - https://storybook.js.org/docs/8.6/writing-stories/args
- Storybook controls:
  - https://storybook.js.org/docs/api/doc-blocks/doc-block-controls
- Storybook interaction tests:
  - https://storybook.js.org/docs/7/writing-tests/interaction-testing
- MDN iframe:
  - https://developer.mozilla.org/docs/Web/HTML/Reference/Elements/iframe
- MDN custom elements:
  - https://developer.mozilla.org/docs/Web/API/Web_components/Using_custom_elements
- MDN CustomElementRegistry:
  - https://developer.mozilla.org/en-US/docs/Web/API/CustomElementRegistry/define
- MDN Shadow DOM:
  - https://developer.mozilla.org/en-US/docs/Web/API/Web_components/Using_shadow_DOM
- MDN CSP overview:
  - https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP
- MDN `Window.postMessage()`:
  - https://developer.mozilla.org/en-US/docs/Web/API/Window/postMessage
- OpenTelemetry docs:
  - https://opentelemetry.io/docs/
- OpenTelemetry JavaScript docs:
  - https://opentelemetry.io/docs/languages/js/
