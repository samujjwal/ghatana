# 6. Workflow Designer Page – Canvas – Deep-Dive Spec

Related routes & files:

- Routes:
  - `/workflows/new`
  - `/workflows/:id`
- Page: `src/pages/WorkflowDesigner/index.tsx`
- Canvas component: `src/components/workflow/WorkflowCanvas.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **visual canvas** for creating and editing workflows as node-and-edge diagrams.

**Primary goals:**

- Let users view and manipulate workflow structure:
  - Nodes (start/end, query, transform, decision, approval, API call, notification, etc.).
  - Edges (connections between nodes).
- Support both creating new workflows and editing existing ones.

**Non-goals:**

- Full execution monitoring (belongs to history/executions views).
- Detailed entity-level data browsing.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Workflow designer / architect** building data flows.
- **Developer** mapping technical steps into an executable workflow.

**Key scenarios:**

1. **Creating a new workflow**
   - User opens `/workflows/new`.
   - Canvas shows an empty or template workflow with a `workflowId` like `current`.

2. **Editing an existing workflow**
   - User navigates from Workflows page or Dashboard into `/workflows/:id`.
   - Canvas loads that workflow’s nodes and edges.

---

## 3. Content & Layout Overview

From `WorkflowDesigner/index.tsx`:

- Renders `<WorkflowCanvas workflowId="current" />` (or similar) as the primary page content.

From `WorkflowCanvas.tsx` (current implementation):

- Uses **ReactFlow** to render and edit workflows visually.
- Reads workflow definition from Jotai `workflowAtom` and maps it to:
  - `nodes`: ReactFlow nodes with labels, config payload, positions, and custom node types.
  - `edges`: ReactFlow edges with source/target and optional labels.
- Supports:
  - Creating connections via **drag-to-connect** between nodes.
  - Selecting nodes and edges (clicking sets `selectedNodeAtom` / `selectedEdgeAtom`).
  - Deleting the currently selected node or edge via the `Delete` key.
  - Zoom/pan, background grid, controls, and minimap (via ReactFlow’s `Background`, `Controls`, `MiniMap`).
  - A small **information panel** in the top-left showing "Workflow Canvas" and current node/edge counts.

Planned enhancements (Workflow Builder alignment):

- A richer **node palette** for adding new nodes of various types (Ingest, Transform, ML Training, Publish, Condition, etc.).
- A **configuration drawer/inspector panel** that updates when a node or edge is selected, allowing users to edit properties (SQL, targets, schedules, conditions) inline.
- Visual **execution overlays** that color or badge nodes/edges according to last run status, duration, or SLA.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Drag-and-drop editing:**
  - Ability to add/move/connect nodes visually.
- **Clear node and connection semantics:**
  - Node types should be visually distinct.
  - Edges clearly indicate data/control flow.
- **Undo/redo (future):**
  - Make edits non-destructive and reversible.

- **Configuration clarity (future):**
  - Selecting a node or edge should clearly surface its configuration fields in a properties panel, mirroring how workflows are defined in YAML/JSON.

- **Execution visualization (future):**
  - When integrated with the workflow engine, runs should be overlaid on the canvas (e.g., success/failure colors, tooltips with last run info).

---

## 5. Completeness and Real-World Coverage

For production use, the canvas should:

1. Support **save/publish flows** for workflows.
2. Integrate with CES backend workflow definitions.
3. Show **validation feedback** for invalid or incomplete workflows.
4. Offer a **node palette** aligned with Workflow Builder node types (Ingest, Transform, ML Training, Publish, Condition, etc.).
5. Allow workflows to be instantiated from **templates** (e.g., pre-built ingestion or BI pipelines) and then customized.
6. Provide **execution visualization** for debugging and monitoring (per-node status, timelines, retries) in coordination with dedicated executions pages.

---

## 6. Modern UI/UX Nuances and Features

- **Zoom & pan:**
  - Canvas should support intuitive navigation for large workflows.
- **Keyboard accessibility:**
  - Select, move, and connect nodes via keyboard.
- **Snapping and alignment:**
  - Assist users in clean graph layouts.

---

## 7. Coherence with App Creator / Canvas & Platform

- This page is a direct manifestation of the **design-to-code canvas** vision:
  - Nodes map to steps/functions.
  - Edges map to data/control flow.
- It should ultimately share interaction patterns with App Creator canvases (selection, properties panel, history, etc.).
- Within the broader Workflow Builder experience, this canvas is where **predefined templates** (ingestion, ML, BI) become concrete, editable DAGs, similar to how App Creator turns app templates into editable screens and flows.

---

## 8. Links to More Detail & Working Entry Points

- Workflow list: `src/pages/WorkflowsPage.tsx`
- Dashboard: `src/pages/DashboardPage.tsx`
- Canvas implementation: `src/components/workflow/WorkflowCanvas.tsx`

---

## 9. Gaps & Enhancement Plan

1. **Routing integration for workflowId:**
   - Ensure `workflowId` prop is derived from route params for existing workflows.

2. **Persistence:**
   - Wire canvas edits to backend APIs (fetch/save workflows).

3. **Multi-page integration:**
   - Provide deep-linking from other tools (e.g., AEP, App Creator) into specific workflows/nodes.

4. **Node palette & configuration drawer:**
   - Implement an explicit palette for adding node types (including ingest/transform/ML/publish/condition) and a properties drawer/inspector bound to Jotai state.

5. **Execution-aware overlays:**
   - Integrate run status, duration, and failure information into the canvas as visual overlays, driven by workflow execution APIs and telemetry.

6. **Template-driven initialization:**
   - Allow the Designer to open with a selected template pre-populated, with clear markers of which parts are template defaults vs user customizations.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Workflow Designer (the header may be implicit in app shell)

[ Palette ]      [ Canvas: nodes and edges ]                [ Inspector ]
-------------------------------------------------------------------------------
- Start Node       [Start] → [Query DB] → [Transform] → [Load to Warehouse] → [End]
- Query Node
- Transform Node
- Decision Node
- API Call Node
- Notification

Node selection
-------------------------------------------------------------------------------
Click on a node → Inspector panel shows:
- Name
- Type (query/transform/...)
- Configuration fields (e.g., SQL, target collection)

Actions
-------------------------------------------------------------------------------
- [ Save Draft ]   [ Publish ]   [ Validate ]   [ Run Test ]
```
