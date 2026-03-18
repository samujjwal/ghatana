# 3. Implement (Canvas) – Design-to-Code Canvas – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Updated with implementation details

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 2.2 Implement (Canvas)](../APP_CREATOR_PAGE_SPECS.md#22-implement-canvas----canvas--canvas-new-canvasroute--canvasscene)

**Code files:**

| File | Purpose |
|------|---------|
| `src/routes/app/project/canvas.tsx` | Project-scoped canvas route |
| `src/routes/canvas.tsx` | Top-level canvas entry |
| `src/routes/canvas.new.tsx` | New canvas creation |
| `src/routes/canvas.demo.tsx` | Demo/onboarding canvas |
| `src/routes.tsx` | Route configuration |

**Routes:**

| Route | Purpose |
|-------|---------|
| `/app/w/:workspaceId/p/:projectId/canvas` | Project canvas |
| `/canvas` | Standalone canvas entry |
| `/canvas/new` | Create new canvas |
| `/canvas/demo` | Demo mode |

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **visual design-to-code canvas** where users can compose components, screens, and flows, and eventually generate production-ready code and configurations.

**Primary goals:**

- Let users **visually design** application pages and flows.
- Maintain a clear mapping between **canvas elements** and **underlying project artifacts** (components, pages, pipelines).
- Support **iterative editing** with history, undo/redo, and comments.
- Act as the core of the App Creator experience, connected to backlog, builds, deploys, and monitoring.

**Non-goals:**

- Replace all textual configuration or coding; some advanced settings will always be code-first.
- Act as a stand-alone diagramming tool unrelated to real projects.
- Implement DevSecOps-specific dashboards (those live under `/devsecops`).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Frontend / Full-stack Engineer:** Uses the canvas to sketch out UI flows and wire basic behavior.
- **Product Designer:** Collaborates on flows and layout, focusing on structure and states.
- **Platform Engineer:** Defines reusable templates and blocks that others can drag onto the canvas.
- **Tech Lead:** Reviews designs and leaves comments or guidance.

**Key scenarios:**

1. **Designing a new feature flow**
   - User opens the Implement (Canvas) tab for a project.
   - Drags components from the **ComponentPalette** onto the canvas.
   - Connects nodes to represent navigation or data flow.
   - Saves changes and links them to backlog items or DevSecOps templates (future behavior).

2. **Refining an existing page**
   - User opens a page canvas that already has components.
   - Adjusts layout, properties, or connections.
   - Uses **undo/redo** to experiment safely.

3. **Review & discussion**
   - Tech lead opens the canvas.
   - Uses the **CommentsPanel** to leave feedback; mentions specific nodes.
   - Later, developers address comments and resolve them.

---

## 3. Content & Layout Overview

### 3.1 `CanvasRoute.tsx` – Context Providers

- Wraps the `CanvasScene` with:
  - **React Flow provider** for graph editing.
  - **DndContext / drag-and-drop providers** for palette interactions.
- Purpose: ensure `CanvasScene` can focus on UI and interactions rather than wiring providers.

### 3.2 `CanvasScene.tsx` – Main Canvas Experience

Key elements (based on current implementation):

- **ComponentPalette:**
  - Lists available block types (components, containers, I/O, etc.).
  - Drag-and-drop onto canvas to create nodes.

- **Canvas area (React Flow):**
  - Nodes representing components or logical steps.
  - Edges representing navigation or data flow.
  - Features: grid background, pan/zoom, mini-map.

- **HistoryToolbar:**
  - Buttons for undo, redo, fit view, zoom controls.
  - May include helpers like "Reset viewport".

- **CommentsPanel:**
  - Panel for adding and viewing comments tied to canvas elements.

- **Inspector / properties panel (where present):**
  - Shows properties for the selected node (label, variant, bindings, etc.).

- **E2E helpers and test hooks:**
  - Data attributes and test helpers for automated interaction with canvas.

**Layout concept:**

- Left: Component palette.
- Center: Canvas.
- Right: Properties and comments (collapsible).
- Top: History/toolbar.

---

## 4. UX Requirements – User-Friendly and Valuable

### 4.1 Discoverability & Learnability

- **Clear grouping of components:**
  - Palette categories should use simple language (Layout, Inputs, Data, Navigation).
- **Onboarding hints:**
  - When canvas is empty, show short guidance text like "Drag components from the left to start designing your page.".

### 4.2 Editing Experience

- **Smooth drag-and-drop:**
  - Components should feel responsive when dragged onto the canvas.
- **Accidental prevention:**
  - Require a deliberate action for destructive operations (delete node confirmation or easy undo).
- **Keyboard support:**
  - Arrows for nudging selected nodes.
  - Delete key to remove selected nodes (with undo support).

### 4.3 Feedback and History

- **Undo/redo visibility:**
  - Show when there is something to undo/redo via enabled/disabled states.
- **Viewport feedback:**
  - Fit view and zoom controls should give immediate visual confirmation.

---

## 5. Completeness and Real-World Coverage

The canvas should support:

1. **Simple page layouts:**
   - Basic landing pages, dashboards, and forms.
2. **Branching flows:**
   - Multiple screens with navigation edges between them.
3. **Iterative refinement:**
   - Multiple save cycles, with undo/redo to support experimentation.
4. **Future data binding:**
   - Traits/properties that allow binding to project data and DevSecOps metrics.

---

## 6. Modern UI/UX Nuances and Features

- **Performance:**
  - Canvas interactions must remain smooth with a moderate number of nodes.
- **Visual hierarchy:**
  - Nodes and edges should be readable and not overly dense.
- **Zoom & pan affordances:**
  - Mouse wheel zoom, drag-to-pan, and visible mini-map.
- **Comments UX:**
  - Comment threads should be clearly tied to specific nodes or regions.

---

## 7. Coherence and Consistency Across the App

- **Design system integration:**
  - UI around the canvas (panels, buttons) should use `@ghatana/ui` primitives where possible.
- **Shared semantics:**
  - Node status/labels should align with backlog items and DevSecOps entities.
- **Navigation links:**
  - From canvas, users should be able to jump to related backlog items or builds (future enhancement).

---

## 8. Links to More Detail & Working Entry Points

**Docs & inventory:**

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#22-implement-canvas----canvas--canvas-new-canvasroute--canvasscene`

**Code entry points:**

- Canvas route wrapper: `src/routes/app/project/canvas/CanvasRoute.tsx`
- Canvas scene implementation: `src/routes/app/project/canvas/CanvasScene.tsx`
- Canvas demo/test routes: `src/routes/canvas-test.tsx`, `src/routes/canvas-comprehensive-demo.tsx`, `src/routes/canvas-onboarding.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. **Explicit "Generate code" pathway:**
   - Add clear actions like "Generate React code" or "Open generated page" from the canvas.

2. **Backlog & DevSecOps linkage:**
   - Allow nodes to link to backlog items or DevSecOps entities.

3. **Better layman-friendly labels:**
   - Rename node types and properties to be understandable for non-experts.

4. **Template support:**
   - Provide starter layouts (templates) users can load onto the canvas.

5. **State persistence & versions:**
   - Ensure canvas state is persisted and tied into project versions/snapshots.

---

## 10. Mockup / Expected Layout & Content

```text
Project Shell Header (from _shell.tsx)
-------------------------------------------------------------------------------
[Back to Workspace]   [Avatar: 🔷]  E‑commerce Checkout Flow  (p‑checkout‑web)
Status: In Progress • Last Deploy: 45m ago to Staging • 1 low incident

Tab strip: [Overview] [Implement (Canvas)] [Backlog] [Design] [Build] [...]
                                  ▲
                                  │
                         (current tab active)

Implement (Canvas) Layout
-------------------------------------------------------------------------------
[ History Toolbar ]   [Undo] [Redo] [Fit] [Zoom‑] [Zoom+] [Center on Home Page]

+----------------------+---------------------------------------------+---------+
| Component Palette    | Canvas Area                                 | Details |
| (left rail)          | (center, React Flow surface)                | (right  |
|                      |                                             | panel) |
|  Layout              |   +-------------------------------------+   |         |
|   • Page Section     |   |  [Home Page]                        |   |  Node  |
|   • Two‑column       |   |   ┌─────────────┐                   |   |  Info  |
|  Navigation          |   |   │  Hero Card  │ --link--> [Login] |   |  ----- |
|   • Nav Bar          |   |   └─────────────┘ \                 |   |  Type: |
|   • Breadcrumbs      |   |                     \               |   |  Page  |
|  Inputs              |   |                      > [Order Conf] |   |  Title:|
|   • Button           |   +-------------------------------------+   |  "Home"|
|   • Text Field       |                                             |  URL:  |
|  Data & Visuals      |                                             |  /     |
|   • Data Table       |   Mini‑map (bottom‑right overlay)          |         |
|   • Metric Tile      |   • Shows entire flow graph                |         |
|                      |                                             |  Props:|
|  [Search components] |   Canvas interactions:                      |  Layout|
|  [Filter by type]    |   • Drag nodes to reposition               |  Spacing|
+----------------------+   • Drag from palette to create nodes      |         |
                         • Click node to select and open Details    +---------+

Comments & Backlog Links (collapsed drawer along right edge)
-------------------------------------------------------------------------------
► Comments   (3)   |   ► Linked Backlog Items   (2)

When expanded:

- Comments
  • "Confirm login CTA matches design spec" – by design-lead, 2h ago
  • "Break out order summary into separate node" – by tech-lead, 1d ago

- Linked Backlog Items
  • PROJ‑124  "Implement new checkout header"   [Open in Backlog]
  • PROJ‑131  "Add A/B test for hero layout"    [Open in Backlog]

Key interactions from this mockup
-------------------------------------------------------------------------------
- Drag "Hero Card" and "Data Table" from Component Palette into [Home Page].
- Connect [Home Page] → [Login] → [Order Conf] using edges.
- Select [Login] node to edit its title and route in the Details panel.
- Open Comments drawer to review feedback and mark threads as resolved.
- Use the toolbar Fit/Zoom controls before taking a snapshot or running builds.
```

This mockup should be used as the baseline contract for how the Implement (Canvas) tab behaves and how it fits into the broader App Creator workflow.
