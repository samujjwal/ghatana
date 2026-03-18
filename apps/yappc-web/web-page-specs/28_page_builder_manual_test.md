# 28. Page Builder Manual Test – Deep-Dive Spec

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 6. Login & Page Builder](../APP_CREATOR_PAGE_SPECS.md#6-login--page-builder)

**Code files:**

- `src/routes/PageBuilderManualTest.lazy.tsx`
- `src/routes/PageBuilderManualTest.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **manual testing surface for the Page Builder**, wired to canvas and PageBuilder providers, with robust lazy-loading and error handling.

**Primary goals:**

- Allow developers to interactively test Page Builder behavior.
- Integrate Page Builder core with a canvas and simple UI.
- Handle loading and error states cleanly.

**Non-goals:**

- Serve as a user-facing entry point for non-technical users (currently dev/test only).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- Frontend and platform engineers working on Page Builder and canvas.
- QA engineers running manual scenarios beyond E2E automation.

**Key scenarios:**

1. **Manual regression testing**
   - Engineer opens `/page-builder-manual-test`.
   - Uses toolbar and properties panel to exercise core flows.

2. **Investigating integration issues**
   - Dev uses manual test to debug interactions between PageBuilder and Canvas.

---

## 3. Content & Layout Overview

### 3.1 Lazy wrapper (`PageBuilderManualTest.lazy.tsx`)

- Uses React.lazy and Suspense to load `PageBuilderManualTest`.
- Wraps content in a custom ErrorBoundary.
- Handles Chrome extension import errors by trying a fallback path.

### 3.2 Manual test route (`PageBuilderManualTest.tsx`)

- Integrates:
  - `CanvasProvider` / `createDefaultDocument` from `@yappc/canvas`.
  - `PageBuilderProvider` from `@yappc/page-builder`.
- Manages local state for:
  - `pages`, `devices`, `selectedComponent`.
  - `LayerNode` model for canvas items.
- Defines a local `CanvasAPI` interface and implementation for testing.
- UI includes:
  - Toolbar for adding components, changing device, refresh/save/zoom.
  - Canvas area for dropping and arranging components.
  - Properties panel with fields driven by `componentProperties` per type.

---

## 4. UX Requirements – User-Friendly and Valuable (for Devs)

- **Clear grouping:**
  - Separate toolbar, canvas, and property panel regions.
- **Discoverable controls:**
  - Buttons labeled clearly (Add Header, Save Layout, etc.).

---

## 5. Completeness and Real-World Coverage

Manual test should support:

1. Core Page Builder flows (add/update/remove components).
2. Multiple device sizes.
3. Basic persistence via local state and storage adapter.

---

## 6. Modern UI/UX Nuances and Features

- **Error handling:**
  - ErrorBoundary should show helpful messages if dynamic import fails.
- **Loading indicator:**
  - Suspense fallback shows clear loading state.

---

## 7. Coherence and Consistency Across the App

- Should eventually be replaced or wrapped by a canonical `PageBuilderShell` from shared libs.
- Contracts (CanvasAPI, events) must match Page Builder and canvas architectures.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#6-login--page-builder`
- Lazy wrapper: `src/routes/PageBuilderManualTest.lazy.tsx`
- Manual test implementation: `src/routes/PageBuilderManualTest.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Decide whether to expose a user-facing Page Builder entry point or keep this test-only.
2. Refactor to use a canonical `PageBuilderShell` shared component.
3. Replace local CanvasAPI stub with canvas adapter path where feasible.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Page Builder Manual Test
Subtitle: Developer-focused surface for testing Page Builder behaviour.

[ Toolbar ]
- [ Add Header ] [ Add Section ] [ Add Button ]  |  Device: [ Desktop ⌄ ]
- [ Undo ] [ Redo ] [ Save Layout ] [ Reset ]

Layout
-------------------------------------------------------------------------------
| Canvas (left/center)                         | Properties (right)           |
-------------------------------------------------------------------------------
| +----------------------------------------+   | Selected component: Header   |
| | [Header: "Welcome to Acme"]           |   | Text: "Welcome to Acme"     |
| |                                        |   | Background: #2563EB         |
| | [Section: Hero + CTA]                  |   | Padding: 32px               |
| |  └─ [Button: "Get Started"]          |   |                              |
| +----------------------------------------+   | [Delete] [Duplicate]        |
-------------------------------------------------------------------------------

Interactions (for manual testing):
- Drag components from toolbar/palette into the canvas.
- Select components to edit properties in the right-hand panel.
- Switch device between Desktop / Tablet / Mobile and verify responsive layout.
- Use Save Layout to persist the current `LayerNode` structure.
```
