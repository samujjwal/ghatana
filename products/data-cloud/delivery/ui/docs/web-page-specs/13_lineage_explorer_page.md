# 13. Lineage Preview in Data Explorer – Deep-Dive Spec

> **Status:** Partially implemented. Current lineage access is a canonical `/data?view=lineage` preview inside Data Explorer, while the richer standalone explorer below remains a longer-term expansion path.

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Show **end-to-end data lineage** for the selected collection inside Data Explorer, then expand toward a richer standalone graph workspace only when the product genuinely needs it.

**Primary goals:**

- Visualize upstream and downstream lineage for a selected dataset, pipeline, or derived insight surface.
- Let users navigate the graph and inspect node/edge details.
- Integrate with glossary and governance concepts (business terms, PII, policies).

**Non-goals:**

- Detailed execution log viewing (handled in workflow/execution UIs).
- Direct editing of workflows (handled in Workflow Designer).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Analytics engineer / data engineer** understanding breakages and impact.
- **Analyst** wanting to see where numbers come from.
- **Governance/compliance** checking data flows for sensitive data.

**Key scenarios:**

1. **Impact analysis before changes**
   - User selects a dataset they plan to deprecate.
   - Lineage Explorer shows downstream workflows and reports.

2. **Root cause analysis after a failure**
  - An operator insight or query result shows wrong numbers.
  - User opens lineage preview from the data view and navigates upstream to find problematic inputs.

3. **Governance checks**
   - User inspects lineage paths carrying PII to verify policy compliance.

---

## 3. Content & Layout Overview

Core layout components:

- **Current shipped entry point**
  - User starts in Data Explorer and switches the view toggle to `Lineage`.
  - `/lineage` is a compatibility handoff that redirects to `/data?view=lineage`.
  - The selected collection drives the lineage and downstream-impact requests.

- **Header**
  - Title stays inside the Data Explorer detail panel as `Lineage preview`.
  - Current focus node follows the selected collection.
  - Future controls may add depth, direction, and filters if the preview grows into a larger workspace.

- **Graph canvas** (D3/Cytoscape-style)
  - Nodes representing datasets, workflows, external systems, and reports.
  - Edges representing data flows or dependencies.
  - Zoom, pan, and fit-to-view controls.

- **Node details panel**
  - Appears when a node is selected.
  - Shows type, description, owners, tags, health, and key links (to Dataset Detail, advanced pipeline editor, and query surfaces).

- **Legend & filters**
  - Legend explaining node and edge shapes/colors.
  - Filters by type, domain, system, tag, or PII.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Readable at different scales:**
  - Support collapsing/expanding sections of the graph.
- **Smooth navigation:**
  - Intuitive zoom and pan, keyboard shortcuts for moving focus.
- **Context preservation:**
  - Breadcrumbs or a mini-map for orientation in large graphs.
- **Accessible information:**
  - Textual summaries for users who can’t or don’t want to interpret dense graphs.

---

## 5. Completeness and Real-World Coverage

A complete lineage experience should:

1. Integrate with the **metadata catalog’s lineage model** (table/column/workflow-level).
2. Represent external sources and sinks (APIs, streams, lakes, BI tools).
3. Support time-aware lineage (e.g., choose a point-in-time snapshot).
4. Surface governance attributes (PII, policies) directly on nodes/edges.
5. Provide APIs for other UIs (SQL workspace, Dataset Detail) to deep-link into a specific node or subgraph.

---

## 6. Modern UI/UX Nuances and Features

- **Animated transitions:**
  - Smooth expansion/collapse and focus changes.
- **Search within graph:**
  - Find specific nodes by name and highlight them.
- **Path highlighting:**
  - Emphasize the path from a selected source to a selected sink.

---

## 7. Coherence with App Creator / Canvas & Platform

- Shares interaction patterns with Workflow Designer and other canvases (selection, pan/zoom, mini-map).
- Nodes representing pipelines can link into the advanced pipeline editor for editing.
- Datasets and derived outputs connect back to Dataset Detail, Query, and operator insight pages rather than a dedicated BI product.

---

## 8. Links to More Detail & Working Entry Points

- Dataset Detail: `12_dataset_detail_insights_page.md`.
- Pipelines: `05_workflows_page.md`, `06_workflow_designer_canvas.md`.
- Governance UIs (future): policies, PII reports.

---

## 9. Gaps & Enhancement Plan

1. **Lineage model & APIs:**
   - Define and implement the lineage schema and services feeding this view.

2. **Performance considerations:**
   - Strategies for large graphs (pruning, sampling, virtualization).

3. **Integration points:**
   - Standardize how other UIs pass a lineage context (dataset ID, workflow ID).

---

## 10. Mockup / Expected Layout & Content

```text
Data Explorer → View: [Overview] [Schema] [Lineage] [Quality]

Lineage preview
- Current collection: orders
- Upstream/downstream graph snapshot
- Downstream impact summary
- Links: [Open Query] [Related Pipelines] [Return to Data]
```
