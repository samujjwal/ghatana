# App-Creator – Page Builder Design & Implementation Plan

> Scope: Page Builder domain, core engine, React integration, and UI shell across `libs/page-builder`, `libs/page-builder-ui`, and `apps/web` (manual test route and future production routes). This plan is aligned with `APP_CREATOR_ARCH_REVIEW.md` and `.github/copilot-instructions.md`.

---

## 1. Goals & Principles

- **Primary goal**
  - Deliver a production-grade Page Builder that lets teams visually compose, configure, and export pages/sections (e.g., DevSecOps surfaces) in a way that is reusable, modular, type-safe, and fully aligned with the canvas and design system.

- **Key principles (anchored to `.github/copilot-instructions.md` and APP_CREATOR_ARCH_REVIEW)**
  - **Reuse-first**
    - Prefer `libs/page-builder`, `libs/page-builder-ui`, `libs/canvas`, `@yappc/ui`, `libs/types` over app-local code.
  - **Layered architecture**
    - Domain & core engine (`libs/page-builder/core`),
    - Application/services (plugins, hierarchy, storage),
    - UI components (`libs/page-builder-ui`),
    - Product integration (`apps/web` routes, DevSecOps templates).
  - **Single sources of truth**
    - Canonical types in `libs/page-builder/src/types.ts` and `libs/types`.
    - Canvas as source of truth for element geometry and metadata.
    - Design tokens centralized in `libs/page-builder/src/designTokens.ts` and shared design system.
  - **No duplication / minimal abstraction leakage**
    - Avoid reimplementing builder logic in `apps/web` when it belongs in `libs/page-builder` or `libs/page-builder-ui`.
    - Avoid parallel “layout editors” or ad-hoc page models.
  - **Type-safe & lint-clean**
    - Remove/avoid `any`; define explicit interfaces (CanvasAPI, plugin contracts, project data, events).

---

## 2. Current Implementation Snapshot

### 2.1 Domain & Core (libs/page-builder)

- **Types & data model – `src/types.ts`**
  - `PageBuilderComponent`: core unit with `tagName`, `classes`, `attributes`, `styles`, content, `components` children, `traits`, optional `events`/`methods`/`lifecycle`/`properties`.
  - `ProjectData`: `pages`, `styles`, `assets`, `symbols`, `dataSources`.
  - `Page`: per-page list of `PageBuilderComponent`.
  - `HierarchyNode` / `HierarchyStack`: context for navigating a hierarchy of canvas documents and page builder workspaces.
  - `PageBuilderWorkspaceConfig`: allowed blocks, device presets, style/asset manager config.
  - `DesignTokens`: design tokens (colors, typography, spacing, radii, shadows, transitions).
  - `PageBuilderConfig` & `PageBuilderDependencies`: configuration and injected dependencies (canvasAPI, eventBus, storage, designTokens).
  - `PageBuilderPlugin` + `PanelDefinition` + `BlockDefinition`: plugin and extension surface.

- **Core engine – `src/core/PageBuilderCore.ts`**
  - Holds `config`, `deps`, `projectData`, `isInitialized`, and a `clipboard` for copy/paste.
  - **Lifecycle**
    - `initialize()`: loads via `deps.storage.load()` when `config.storageManager.autoload` is true; emits `initialized`.
    - `dispose()`: clears listeners, marks not initialized, emits `disposed`.
  - **Data access & persistence**
    - `getProjectData()`, `load()`, `save()`.
  - **Component operations**
    - `addComponent`, `updateComponent`, `removeComponent`, `getComponent`.
    - Clipboard operations: `copyComponent`, `pasteComponent` (new ID via `crypto.randomUUID()`).
  - **Integration helpers**
    - `getDesignTokens()`, `getCanvasAPI()`.
    - `import(ProjectData)`: validates structure, sets `projectData`, optionally saves.
  - **Not yet fully wired**
    - Comments reference managers (ComponentManager, BlockManager, TraitManager, StyleManager, AssetManager, DeviceManager, CommandManager, SelectorManager, StorageManager) but those are not currently instantiated from `PageBuilderCore`.

- **Canvas adapter – `src/adapters/PageBuilderCanvasAdapter.ts`**
  - Binds `CanvasAPI` (from `@yappc/canvas`) to Page Builder components.
  - Listens to canvas `document:changed`, `selection:changed`, `viewport:changed` and normalizes to `component-*` events.
  - Converts Canvas elements ↔ PageBuilder components via `modelMapper`.
  - Provides helper methods:
    - `syncPageBuilderToCanvas(component)` – upserts a canvas element for a page component.
    - `getPageBuilderComponents()` – derive `PageBuilderComponent[]` from a document.
    - `addComponents(components)` – bulk sync.
    - `removeComponent(componentId)` – delete in canvas and emit component-removed.

- **Model mapper – `src/adapters/modelMapper.ts`**
  - `pageBuilderComponentToCanvasElement` / `canvasElementToPageBuilderComponent` with `metadata.layer === 'page'` and `pageBuilderData` payload.
  - Helpers for `BaseItem` ↔ Canvas elements and `ProjectData` ↔ elements, plus metadata merging and validation.

- **Hierarchy – `src/hierarchy/HierarchyService.ts` + `src/react/HierarchyProvider.tsx`**
  - `HierarchyService` manages `HierarchyStack`, viewport transitions, and document loading using `CanvasAPI`.
  - `HierarchyProvider` wraps React subtree with `HierarchyContextValue` exposing `enterContext`, `exitContext`, `navigateToNode`, `breadcrumb`, `currentNode`, etc.

- **Plugins – `src/plugins/PluginRegistry.ts`**
  - `PluginRegistry` manages plugin lifecycle and metadata (`registerPlugin`, enable/disable, unregister, notifyContextChange, dispose).
  - Baseline plugins: `UndoRedoPlugin`, `KeyboardShortcutsPlugin`, `SelectionPlugin`, `ViewportPlugin` (currently mostly stubs, ready to be expanded).

- **Design tokens – `src/designTokens.ts`**
  - Centralized tokens for colors, typography, spacing, radii, shadows, transitions, plus CSS class constants (`cssClasses`) and helpers (`getToken`, `mergeClasses`).

### 2.2 React integration (libs/page-builder)

- **`src/react/PageBuilderProvider.tsx`**
  - Creates `PageBuilderCore`, `PageBuilderCanvasAdapter`, and `PluginRegistry` and exposes them via React context.
  - Uses a local `StorageAdapter` that persists `ProjectData` to `localStorage`.
  - On init:
    - Instantiates `PluginRegistry` and adapter with debounce/autosave config.
    - Instantiates `PageBuilderCore` with storage config (`storageManager` local, autosave/autoload).
    - Calls `core.initialize()`, loads project data, pushes components to canvas via adapter.
    - Subscribes to adapter selection changes to maintain `selectedComponents` set.
  - Exposes context value (`PageBuilderContextValue`) with `core`, `adapter`, `plugins`, status flags, `projectData`, and selection helpers.
  - Provides hooks: `usePageBuilder`, `usePageBuilderCore`, `useCanvasAdapter`, `usePluginRegistry`.

### 2.3 UI Library (libs/page-builder-ui)

- **`PageBuilder` component – `libs/page-builder-ui/src/components/PageBuilder.tsx`**
  - Receives a `PageLayout` (from `@yappc/page-layout-editor`) and `availableComponents` plus callbacks.
  - Manages:
    - Layout state (positions, sizes, z-order, component placements).
    - Selection and drag/resize interactions.
    - Palette interactions (adding/moving components, drop targets).
  - Renders a classic three-pane WYSIWYG editor:
    - **Component palette**: draggable components, organized by category.
    - **Canvas**: layout grid where components are dropped/moved/resized.
    - **Property panel**: numeric/ID fields (x, y, width, height, zIndex), plus component metadata.
  - Exported via `libs/page-builder-ui/src/index.ts` alongside associated types.

### 2.4 App Integration (apps/web)

- **Manual builder route – `apps/web/src/routes/PageBuilderManualTest.tsx`**
  - A MUI-based manual playground integrating:
    - `CanvasProvider`/`createDefaultDocument` from `@yappc/canvas`.
    - `PageBuilderProvider` from `@yappc/page-builder`.
  - Maintains its own `pages`, `devices`, `selectedComponent`, and mock `LayerNode` model.
  - Defines a local `CanvasAPI` interface and implementation that:
    - Adds/removes/updates components by manipulating local React state.
    - Defines `executeCommand` as a stub logging to console.
  - Provides panels:
    - **Toolbar**: add components, change device, refresh/save/zoom actions (using MUI icons and Buttons).
    - **Canvas area**: droppable container for components.
    - **Properties panel**: auto-generated controls based on `componentProperties` per type.
  - Page Builder core is present (via provider), but much of the UI logic lives outside `libs/page-builder-ui` and bypasses `PageLayout` types.

- **Lazy wrapper – `apps/web/src/routes/PageBuilderManualTest.lazy.tsx`**
  - Lazy-loads the manual test route and wraps it in a simple error boundary and loading fallback.

- **Routing – `apps/web/src/router/routes.tsx` & `apps/web/src/routes.ts`**
  - Exposes `/page-builder-manual-test` as a dev/testing route under the main app router.
  - Not yet integrated into DevSecOps routes or workspace flows.

- **Legacy/demo – `libs/page-builder/src/demo/LandingPageBuilderDemo.tsx`**
  - A separate in-library demo that implements its own mini “section-oriented” page builder (hero/features/testimonials/cta/footer), with its own toolbar, canvas, and properties panel and HTML export.

### 2.5 High-Level Assessment

- **Strengths**
  - Clear separation of concerns: core engine, canvas adapter, plugin system, React context, and UI library.
  - Strong alignment with design tokens and canvas layer abstraction.
  - Existing manual test route provides a realistic playground for experimentation.

- **Issues / risks**
  - **Duplication / divergence**
    - `libs/page-builder-ui/PageBuilder`, `LandingPageBuilderDemo`, and `PageBuilderManualTest` each define their own UI + state patterns for page editing.
  - **Incomplete wiring**
    - `PageBuilderCore` references managers that are not fully wired into the constructor/initialize flow.
    - Manual test uses a local `CanvasAPI` stub instead of the canonical canvas adapter path.
  - **Type gaps**
    - Several places rely on `any` (event shapes, CanvasAPI, plugin APIs), which conflicts with the strict type-safety requirements.
  - **No dedicated Page Builder design doc**
    - Current architecture docs mention canvas & DevSecOps, but not a cohesive Page Builder design & UX spec.

---

## 3. Target Experience & Wireframes (Textual)

### 3.1 Primary Page: "Page Builder Studio" (Editor)

**Route(s)**

- `/page-builder` (generic playground / workspace-level editor).
- `/devsecops/templates/:templateId/builder` (domain-specific surface – DevSecOps template builder) using the same core shell.

**Wireframe (textual)**

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ Top Toolbar                                                                │
│ [Project / Template Name] [Breadcrumb]                [Device Switcher]    │
│ [Undo] [Redo] [Zoom -] [Zoom 100%] [Zoom +] [Preview] [Save] [Publish]    │
└─────────────────────────────────────────────────────────────────────────────┘
┌───────────────┬──────────────────────────────────────────────┬─────────────┐
│ Left Panel    │                 Canvas                       │ Right Panel │
│ (Palette +    │ (Page viewport with grid, snap, guides)      │ (Properties │
│  Hierarchy)   │                                              │  + Styles)  │
│               │                                              │             │
│ [Tabs:        │ [Draggable/resizable components, selection   │ [Component  │
│  Components | │ outlines, drag handles, selection box]       │  props]     │
│  Layers]      │                                              │ [Layout]    │
│  - Search     │                                              │ [Typography]│
│  - Filter by  │                                              │ [Styles]    │
│    category   │                                              │ [Data]      │
└───────────────┴──────────────────────────────────────────────┴─────────────┘
│ Bottom Bar: [Status: autosave, errors] [Code View] [Plugin panels]        │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Key interactions**

- Drag components from **Component Palette** into the **Canvas** (creates `PageBuilderComponent` + CanvasElement).
- Select components via click or bounding-box selection.
- Adjust layout (x/y/width/height/zIndex) via handles or numeric properties.
- Switch devices (desktop/tablet/mobile) to change viewport and responsive presets.
- Toggle code view (HTML/JSON/React) using `CodeManager` and `Js/Html/Json` generator modules.
- Save/Load via storage adapter (local or remote, depending on workspace).

### 3.2 Supporting Page: "Template Gallery & Entry Points"

**Route(s)**

- `/page-builder/templates` or integrated into `/devsecops/templates`.

**Wireframe (textual)**

```text
┌───────────────────────────── Templates ─────────────────────────────┐
│ [New Template] [Import] [Filter by product/domain] [Search]        │
├─────────────────────────────────────────────────────────────────────┤
│ [Card: Template A]  [Card: Template B]  [Card: Template C] ...     │
│  - Name             - Domain (DevSecOps / Reports / Generic)       │
│  - Last modified    - Used by N pages                              │
│  - Actions: [Edit in Builder] [Duplicate] [Archive]               │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.3 Supporting Surface: "Code & Asset Export"

**Route/Surface**

- Modal or drawer over the main Page Builder Studio.

**Wireframe (textual)**

```text
┌──────────────────── Export / Code View ─────────────────────┐
│ Tabs: [HTML] [React JSX] [JSON Project] [Assets]            │
│                                                             │
│ [Scrollable <pre> with syntax-highlighted code]             │
│                                                             │
│ [Copy to Clipboard] [Download as .zip] [Close]              │
└─────────────────────────────────────────────────────────────┘
```

### 3.4 Component-Level Wireframe (Key React Components)

- **`PageBuilderShell` (conceptual)**
  - Composes Toolbar, Left Panel, Canvas, Right Panel, Bottom Bar.
  - Responsible for routing integration and gluing context providers (CanvasProvider, PageBuilderProvider, HierarchyProvider, app state atoms).

- **`ComponentPalette`**
  - Lists `AvailableComponent` definitions (from registry/metadata), grouped by category.
  - Provides drag handles and search/filter.

- **`LayoutCanvas`** (can be backed by `@yappc/canvas` + `PageBuilderCanvasAdapter`)
  - Renders components, selection overlays, guides, and device frame.
  - Emits events for selection, move/resize, drop.

- **`PropertiesPanel`**
  - Renders fields for geometry (x/y/width/height/zIndex) and domain-specific traits.
  - Binds to `PageBuilderComponent.properties` / traits and design tokens.

- **`Toolbar`**
  - Houses actions (undo/redo, zoom, device switcher, preview, save/publish) mapped to PageBuilderCore + plugin commands.

- **`BreadcrumbBar` + `HierarchyControls`**
  - Visualizes `HierarchyNode` trail; calls into `HierarchyService` for navigation.

---

## 4. Target Architecture (Deep Dive)

### 4.1 Layered View

- **Domain Layer (Page Builder domain)**
  - Types in `libs/page-builder/src/types.ts`.
  - Invariants: valid `ProjectData`, component tree consistency, traits and properties semantics.

- **Application Layer (Core & Services)**
  - `PageBuilderCore` orchestrating project data, persistence, and component lifecycle.
  - Managers (component, block, trait, style, asset, device, command, selector, storage) wired into `PageBuilderCore`.
  - `HierarchyService` for context navigation.
  - Plugin system (`PluginRegistry`, `PageBuilderPlugin` implementations).

- **UI Layer**
  - `libs/page-builder-ui/PageBuilder` and derivative components.
  - Domain-specific shells (DevSecOps template builder, generic builder) leveraging the same core UI primitives.

- **Integration/Infrastructure Layer**
  - Storage adapters (local, canvas-based, remote API) implementing `StorageAdapter`.
  - Canvas integration via `PageBuilderCanvasAdapter`.
  - API endpoints (e.g., `/api/page-builder/projects`) and MSW mocks.

### 4.2 Domain Model Alignment & Reuse

- **Canonical models**
  - Treat `PageBuilderComponent`, `ProjectData`, `Page`, `Style`, `Asset`, `Symbol`, `DataSource` as the canonical Page Builder domain.
  - Avoid introducing parallel “page layout” models in product code; instead adapt existing models to these types.

- **Registry integration**
  - Use the registry pattern from `apps/web/src/services/registry/*` (and the forthcoming shared `RegistryTypes` module) to register:
    - Page builder block definitions (`BlockDefinition`).
    - Component types (`ComponentType`/`ComponentDefinition`).
    - Plugin definitions (`PageBuilderPlugin`).

- **Design system**
  - Use `designTokens` and `cssClasses` consistently across Page Builder UI, ensuring alignment with `@ghatana/ui` and Tailwind tokens.

### 4.3 Canvas as Source of Truth

- **Canvas integration path**
  - Page Builder UI manipulates `PageBuilderComponent` models.
  - Changes are applied via `PageBuilderCore` and synced using `PageBuilderCanvasAdapter` to `@yappc/canvas` documents.
  - `modelMapper` is the single place that knows how to encode/decode `pageBuilderData` and harmonize metadata.

- **Hierarchy & multi-document context**
  - `HierarchyService` + `HierarchyProvider` manage navigation between multiple canvas documents representing:
    - Different pages in a project.
    - Nested components or symbols.

### 4.4 Plugin Architecture

- **Plugin responsibilities**
  - Baseline/core plugins:
    - Undo/Redo, Keyboard Shortcuts, Selection, Viewport.
  - Domain plugins:
    - DevSecOps template library (domain-specific blocks, data binding helpers).
    - Analytics/experiments plugin (A/B test variants on sections).

- **Plugin lifecycle**
  - Registered via `PluginRegistry.registerPlugin`.
  - Receives `register`, `onContextChange`, `dispose` hooks.
  - May contribute UI panels (`PanelDefinition`), blocks, and commands.

### 4.5 State Management & Persistence

- **Current**
  - `PageBuilderProvider` uses internal React state + local storage.
  - Manual test route uses custom React state for `pages`, `devices`, `selectedComponent`.

- **Target**
  - Use Jotai-based state atoms in `@yappc/ui/state` for:
    - Current Page Builder project (ID, ProjectData snapshot, dirty flags).
    - Selection, device mode, zoom level.
    - Plugin configuration and enabled states.
  - Persist via a **storage layer** that can switch between:
    - Local storage (for dev/test).
    - Canvas-backed project storage (via `CanvasStorageAdapter`).
    - Remote API (workspace/project-scoped persistence).

---

## 5. Page Builder–Specific Gaps & Issues

- **Duplication & divergence**
  - `PageBuilderManualTest`, `LandingPageBuilderDemo`, and `libs/page-builder-ui/PageBuilder` each implement overlapping editor behavior.
  - Some flows operate exclusively on local React state rather than going through `PageBuilderCore` + Canvas adapter.

- **Core engine wiring**
  - Managers under `libs/page-builder/src/modules/*` (e.g., CodeManager, LayerManager, TraitManager) are not clearly wired into `PageBuilderCore.initialize()` or documented.

- **Type safety**
  - `CanvasAPI`, event payloads, and plugin operations are partially typed as `any`.
  - Storage adapters and event buses are loosely typed.

- **Lack of dedicated docs**
  - No single design doc tying together engine, UI, plugins, and app integration.

---

## 6. Production-Grade Enhancement & Implementation Plan

### 6.1 Phase 0 – Hardening & Type Safety

- **6.1.1 Canvas & Page Builder contracts**
  - Define a strong `CanvasAPI` interface in `@yappc/canvas` or `libs/page-builder` shared types.
  - Replace `any` event payloads with discriminated unions for canvas and page builder events.

- **6.1.2 Storage & event bus typing**
  - Tighten `StorageAdapter` types (parameterized by `ProjectData`).
  - Wrap `EventEmitter` usage with typed event maps (e.g., `PageBuilderEvents`).

- **6.1.3 Lint rules**
  - Ensure `@typescript-eslint/no-explicit-any` is enforced for Page Builder packages or justify the few exceptions explicitly.

### 6.2 Phase 1 – Core Engine & Manager Wiring

- **6.2.1 Wire managers into `PageBuilderCore`**
  - Instantiate modules such as `ComponentManager`, `BlockManager`, `TraitManager`, `StyleManager`, `AssetManager`, `DeviceManager`, `CommandManager`, `SelectorManager`, `CodeManager`.
  - Expose a coherent public API on `PageBuilderCore` for operations that delegate to these managers.

- **6.2.2 Storage strategy**
  - Introduce a `CanvasStorageAdapter` implementation (if not already present) that stores `ProjectData` in a canvas document.
  - Decouple storage concerns from UI so that the same engine can be used with local or remote storage.

- **6.2.3 Events & telemetry**
  - Make all significant operations (`component:*`, `import`, `load`, `save`, plugin events) emit strongly-typed events for observability.

### 6.3 Phase 2 – UI Consolidation & Shell

- **6.3.1 Canonical UI shell**
  - Treat `libs/page-builder-ui/PageBuilder` as the canonical editor widget for page layout.
  - Factor out a `PageBuilderShell` that:
    - Wraps `CanvasProvider`, `PageBuilderProvider`, `HierarchyProvider`.
    - Renders `PageBuilder` (UI) with toolbar, palette, canvas, properties, and plugin panels.

- **6.3.2 Refactor manual test route**
  - Replace ad-hoc MUI layout and state in `PageBuilderManualTest.tsx` with:
    - The shared `PageBuilderShell`.
    - Jotai atoms for project/selection/device/zoom.
    - Canvas and Page Builder integration through the adapter, not a local `CanvasAPI` stub.

- **6.3.3 Align with design system**
  - Replace raw MUI layout with `@ghatana/ui` components where appropriate.
  - Use `designTokens` and `cssClasses` for consistent styling; avoid inline styles except in demo code.

### 6.4 Phase 3 – Template Gallery & DevSecOps Integration

- **6.4.1 Template gallery**
  - Implement `/page-builder/templates` (or DevSecOps-integrated gallery) listing templates from a registry/storage.
  - Allow creating/opening templates in the builder, saving back to storage.

- **6.4.2 DevSecOps templates**
  - Define DevSecOps-specific block definitions and data sources aligned with `libs/types/src/devsecops` and DevSecOps UX doc.
  - Embed Page Builder into `/devsecops/templates` routes with domain-aware palettes and presets.

### 6.5 Phase 4 – Data Binding & Code Export

- **6.5.1 Data binding**
  - Extend `DataSource` modeling and traits so components can bind to domain data (KPIs, alerts, etc.), reusing existing query hooks and types.

- **6.5.2 Code generation**
  - Use `CodeManager` + `HtmlGenerator`/`JsGenerator`/`JsonGenerator` to implement the Export / Code View surface.
  - Provide robust export options (HTML, React, JSON) with type-safe mapping from `ProjectData`.

### 6.6 Phase 5 – Observability, Performance, and UX Polish

- **6.6.1 Observability**
  - Instrument core operations with metrics and logs, using `core/observability` patterns where appropriate.

- **6.6.2 Performance**
  - Address large-project scenarios: virtualization for long lists, canvas rendering optimization, debounced saves, plugin performance guidelines.

- **6.6.3 UX refinements**
  - Device preview polish, snapping & alignment tools, multi-select behavior, keyboard shortcuts (wired via `KeyboardShortcutsPlugin`).

---

## 7. Incremental Work Breakdown (Aligned with Main Plan)

This section maps concrete increments to the broader cleanup plan from `APP_CREATOR_ARCH_REVIEW.md`.

- **Increment A – Contracts & Type Safety**
  - Finalize `CanvasAPI` and Page Builder event types.
  - Tighten `StorageAdapter` and plugin typing; remove `any` where feasible.
  - Add tests for `modelMapper` and adapter behavior.

- **Increment B – Core Wiring & Storage**
  - Wire managers into `PageBuilderCore` and expose a cohesive API.
  - Implement and test `CanvasStorageAdapter` and local storage adapter selection.

- **Increment C – Canonical UI Shell & Manual Test Refactor**
  - Introduce `PageBuilderShell` and migrate `PageBuilderManualTest` to use it.
  - Replace local state with shared atoms where appropriate.

- **Increment D – Template Gallery & DevSecOps Integration**
  - Implement template gallery routes and connect to DevSecOps templates as a first domain integration.

- **Increment E – Data Binding & Export**
  - Implement data-binding traits and robust export UX.

- **Increment F – Observability & Performance Pass**
  - Add metrics/logging and address performance hotspots.

---

## 8. Review & PR Checklist (Page Builder–Specific)

Use this checklist, in addition to the global architecture checklist, for Page Builder PRs:

- [ ] **Core alignment**: Changes reuse `PageBuilderCore`, adapters, and canonical types; no new parallel “page builder” abstractions were added.
- [ ] **Canvas as source of truth**: All geometry/state mutations flow through canvas + adapter, not ad-hoc DOM or local-only models.
- [ ] **UI reuse**: New editor UIs compose `libs/page-builder-ui/PageBuilder` instead of reimplementing builder shells.
- [ ] **Plugin-friendly**: New features that could be plugins are exposed via the plugin system rather than hard-coded.
- [ ] **Type-safe**: No new `any` usages; `CanvasAPI`, events, and adapters are fully typed.
- [ ] **Docs updated**: This plan and any relevant architecture docs (canvas, DevSecOps, design system) were updated alongside code changes.
- [ ] **Tests added/updated**: Unit/integration tests cover key flows: drag/drop, resize, save/load, undo/redo, device switching, export, and DevSecOps-specific templates where applicable.

## 9. Day-by-Day Execution Plan (Page Builder)

> This is a Page Builder–focused breakdown. It is meant to be used alongside the main day-by-day plan in `APP_CREATOR_ARCH_REVIEW.md`.  
> Day labels are prefixed with `PB-` to avoid confusion with global Day 1–7.

### Day PB-1 – Contracts & Canvas API (Increment A)

- [ ] **Inventory current types & call sites**
  - [ ] List all usages of `CanvasAPI`, canvas events, and Page Builder events across `libs/page-builder`, `libs/canvas`, and `apps/web` (manual test + demos).
  - [ ] Identify all `any` and untyped event payloads related to Page Builder and canvas.
- [ ] **Define canonical contracts**
  - [ ] Add/align a shared `CanvasAPI` interface and event map in either `libs/page-builder/src/types.ts` or a small shared `libs/canvas-types` module.
  - [ ] Define `PageBuilderEvents` as a typed event map for `PageBuilderCore` (init, load, save, component:_, plugin:_, selection, viewport).
- [ ] **Spike tests**
  - [ ] Add/extend unit tests for `modelMapper` and `PageBuilderCanvasAdapter` that exercise the new types.

### Day PB-2 – Event Typing & Lint Guardrails (Increment A)

- [ ] **Apply event typing**
  - [ ] Replace `any` event payloads on `PageBuilderCore`, `PageBuilderCanvasAdapter`, and plugins with discriminated unions.
  - [ ] Update baseline plugins to use the strongly-typed events.
- [ ] **Add lint guardrails**
  - [ ] Enforce `no-explicit-any` for `libs/page-builder` and `libs/canvas` (or document specific, narrow exceptions).
  - [ ] Add a small README snippet in this file and/or package-level `README` describing the expected contracts.

### Day PB-3 – Wire Managers into Core (Increment B)

- [ ] **Wire module instances**
  - [ ] Instantiate `ComponentManager`, `BlockManager`, `TraitManager`, `StyleManager`, `AssetManager`, `DeviceManager`, `CommandManager`, `SelectorManager`, and `CodeManager` in `PageBuilderCore`.
  - [ ] Ensure they are configured via `PageBuilderConfig` / `PageBuilderDependencies` rather than ad-hoc flags.
- [ ] **Expose cohesive API**
  - [ ] Add public methods on `PageBuilderCore` that delegate to those managers instead of leaking manager instances broadly.
  - [ ] Update call sites in React integration and UI to use the new methods.
- [ ] **Tests**
  - [ ] Add/extend tests for at least component add/update/remove and code generation stubs using the wired managers.

### Day PB-4 – Storage Strategy & CanvasStorageAdapter (Increment B)

- [ ] **Design storage selection**
  - [ ] Define a small `StorageMode` / adapter selection config for `PageBuilderCore` (e.g., localStorage, canvas, remote API).
- [ ] **Implement CanvasStorageAdapter**
  - [ ] Implement a `CanvasStorageAdapter` that persists `ProjectData` into a canvas document, using `modelMapper` for conversion.
  - [ ] Add tests for save/load round-trips.
- [ ] **Integrate with React**
  - [ ] Update `PageBuilderProvider` to accept a storage mode and wire to the correct adapter.
  - [ ] Keep default behavior backward compatible (local storage for manual test / demo).

### Day PB-5 – Canonical UI Shell (Increment C)

- [ ] **Define `PageBuilderShell`**
  - [ ] Create a shell component under `libs/page-builder-ui` that wires `CanvasProvider`, `PageBuilderProvider`, and `HierarchyProvider`.
  - [ ] Compose the existing `PageBuilder` editor widget together with toolbar, left/right panels, bottom status bar, and plugin panels.
- [ ] **Align props and context**
  - [ ] Ensure `PageBuilderShell` props map cleanly to domain concepts (project id, initial template, storage mode, entry route).
  - [ ] Use design tokens and `@ghatana/ui` primitives for layout (no inline one-off styling where avoidable).

### Day PB-6 – Refactor Manual Test to Shell (Increment C)

- [ ] **Replace ad-hoc layout**
  - [ ] Refactor `apps/web/src/routes/PageBuilderManualTest.tsx` to render `PageBuilderShell` instead of its local MUI-based layout.
  - [ ] Remove duplicated state for pages, devices, and selection in favor of `PageBuilderProvider` + Jotai atoms (where available).
- [ ] **Use canonical Canvas integration**
  - [ ] Replace the local `CanvasAPI` stub with the shared canvas integration path (`CanvasProvider` + `PageBuilderCanvasAdapter`).
- [ ] **Smoke tests / screenshots**
  - [ ] Manually verify all key flows (drag/drop, resize, save/load, device switch, selection) still work.
  - [ ] Capture before/after notes or screenshots for the docs.

### Day PB-7 – Template Gallery Skeleton (Increment D)

- [ ] **Routes & navigation**
  - [ ] Add a `/page-builder/templates` route (or DevSecOps-integrated entry) in `apps/web` consistent with the main router patterns.
- [ ] **Template listing**
  - [ ] Implement a simple gallery view backed by hard-coded or MSW-powered templates, using canonical types for templates/projects.
- [ ] **Open-in-builder**
  - [ ] Wire “Edit in Builder” actions to open `PageBuilderShell` with the chosen template.

### Day PB-8 – DevSecOps Integration (Increment D)

- [ ] **DevSecOps blocks & data sources**
  - [ ] Define DevSecOps-specific block definitions for key surfaces (dashboards, reports, alerts), reusing types from `libs/types/src/devsecops`.
  - [ ] Ensure data binding contracts align with existing DevSecOps query hooks and view models.
- [ ] **Routes & UX**
  - [ ] Integrate the builder into relevant DevSecOps routes (e.g., `/devsecops/templates/:templateId/builder`) using the same shell.
- [ ] **Mocks**
  - [ ] Add/align MSW handlers and fixtures for DevSecOps templates to keep local dev consistent.

### Day PB-9 – Data Binding & Export UX (Increment E)

- [ ] **Data binding traits**
  - [ ] Extend traits / properties on `PageBuilderComponent` and related managers to support binding to data sources.
  - [ ] Provide a basic UI in the properties panel for selecting data fields / queries.
- [ ] **Export surface**
  - [ ] Implement the Export / Code View modal or drawer in the shell (tabs for HTML, React JSX, JSON, Assets).
  - [ ] Wire to `CodeManager` and generators to produce output from `ProjectData`.
- [ ] **Verification**
  - [ ] Add tests and a few golden-file fixtures for exports.

### Day PB-10 – Observability, Performance, & Polish (Increment F)

- [ ] **Observability**
  - [ ] Add metrics/logging for core operations (load/save, drag/drop, undo/redo, plugin lifecycle) using existing observability patterns.
- [ ] **Performance**
  - [ ] Profile large projects and add virtualization or batching where needed (palette, layers, properties).
  - [ ] Ensure autosave and plugin callbacks are debounced appropriately.
- [ ] **UX polish**
  - [ ] Refine snapping, alignment guides, keyboard shortcuts (hooked through `KeyboardShortcutsPlugin`), and device preview.
