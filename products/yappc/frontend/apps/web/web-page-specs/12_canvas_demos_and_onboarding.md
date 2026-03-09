# 12. Canvas Demos & Onboarding – Deep-Dive Spec

Related inventory entries: [APP_CREATOR_PAGE_SPECS.md – 3. Canvas Demos & Onboarding](../APP_CREATOR_PAGE_SPECS.md#3-canvas-demos--onboarding)

**Code files (representative):**

- `src/routes.ts` (aliases & demo routes – `/diagram`, `/canvas`, `/canvas/new`, `/app/project/canvas-new`, `/canvas-poc`, `/canvas-test`, `/canvas/demo`)
- `src/routes/canvas-comprehensive-demo.tsx`
- `src/routes/canvas-onboarding.tsx`
- `src/routes/canvas-test.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide **playground and onboarding surfaces** for the canvas so we can experiment, run tests, and teach new users without impacting real projects.

**Primary goals:**

- Offer multiple entry points for testing canvas features.
- Provide an onboarding experience that guides new users through core canvas actions.
- Support E2E testing by exposing specific routes and behaviors.

**Non-goals:**

- Act as primary user-facing project canvases (those live under `/app/.../canvas`).
- Replace formal documentation.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Engineers & QA:** Use demos to test new features and run E2E scenarios.
- **New App Creator users:** Follow onboarding steps to learn the canvas.

**Key scenarios:**

1. **Feature prototyping**
   - Developer uses `/canvas-poc` or `/canvas/demo` to try new canvas interactions.

2. **Automated testing**
   - E2E tests open `/canvas-test` and use test helpers to validate behaviors.

3. **Onboarding**
   - New user opens `/canvas/onboarding`.
   - A checklist guides them through drag/drop, editing, and saving.

---

## 3. Content & Layout Overview

### 3.1 Canvas aliases & demos

- Routes like `/diagram`, `/canvas`, `/canvas/new`, `/app/project/canvas-new`, `/canvas-poc`, `/canvas-test`, `/canvas/demo`:
  - Ultimately mount `CanvasRoute` or specialized test/demo scenes.
  - Provide different configurations (e.g., minimal vs comprehensive demo).

### 3.2 `/canvas-comprehensive-demo` (via lazy route)

- Rich demo showing many canvas features at once.
- Useful for manual exploration and visual testing.

### 3.3 `/canvas/onboarding`

- Lazy-loads `OnboardingChecklist`.
- Shows a centered container with checklist items and a spinner during load.

### 3.4 `/canvas-test`

- Test bed for interactive canvas features:
  - Viewport manipulation.
  - Item selection, dragging, rotation, distribution.
  - Options for turning specific feature flags on/off.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Onboarding clarity:**
  - Steps should be written in clear, non-technical language.
  - Cover the core loop: design → link tasks → run build → monitor.
- **Demo safety:**
  - Make it clear that demo routes do **not** affect real projects.

---

## 5. Completeness and Real-World Coverage

Demos and onboarding should:

1. Exercise key canvas interactions (drag/drop, zoom, pan, select, edit).
2. Cover typical layouts and flows.
3. Be robust enough for automated regression tests.

---

## 6. Modern UI/UX Nuances and Features

- **Loading feedback:**
  - Lazy-loaded demos and onboarding must show a spinner or clear loading state.
- **Guided experience:**
  - Onboarding should use checklists or progress indicators.
- **Testability:**
  - Data attributes/hooks for E2E should be stable and well named.

---

## 7. Coherence and Consistency Across the App

- Onboarding terminology should match what users see in the main canvas (node, edge, component, page).
- Demo routes should be discoverable to internal users but not clutter primary navigation.

---

## 8. Links to More Detail & Working Entry Points

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#3-canvas-demos--onboarding`
- Route config: `src/routes.ts`
- Demo implementations: `src/routes/canvas-comprehensive-demo.tsx`, `src/routes/canvas-test.tsx`, `src/routes/canvas-onboarding.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. Add a "Canvas Playground" landing page explaining each demo route.
2. Gate purely-internal/test routes behind flags or auth.
3. Track onboarding completion per user.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Canvas Onboarding
Subtitle: Learn the basics of the App Creator canvas.

Onboarding screen
-------------------------------------------------------------------------------
| ✅  Getting Started with the Canvas                                         |
-------------------------------------------------------------------------------
| [ ] Step 1: Drag a component from the palette to the canvas                 |
| [ ] Step 2: Connect two components with a flow                              |
| [ ] Step 3: Rename a component and edit its properties                      |
| [ ] Step 4: Save your changes and create a snapshot                         |
-------------------------------------------------------------------------------
| [ Open training canvas ]     [ Skip for now ]                               |
-------------------------------------------------------------------------------

Canvas Playground landing (conceptual)
-------------------------------------------------------------------------------
H1: Canvas Playground
Subtitle: Try out different canvas demos and test scenarios.

- Tile: "Comprehensive Demo" – full feature demo (zoom, comments, complex graph)
  [Open /canvas-comprehensive-demo]

- Tile: "E2E Test Surface" – minimal canvas used by automated tests
  [Open /canvas-test]
```
