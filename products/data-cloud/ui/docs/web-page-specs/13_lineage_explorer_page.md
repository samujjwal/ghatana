# 13. Lineage Explorer Page – Deep-Dive Spec

> **Status:** Planned page – no concrete implementation in CES UI yet. This spec describes the lineage explorer called out in `frontend_todo (1).md`.

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Show **end-to-end data lineage** across datasets, workflows, and reports as an interactive graph, helping users understand upstream/downstream dependencies and impact.

**Primary goals:**

- Visualize upstream and downstream lineage for a selected dataset, workflow, or report.
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
   - A critical dashboard shows wrong numbers.
   - User opens lineage from the report and navigates upstream to find problematic inputs.

3. **Governance checks**
   - User inspects lineage paths carrying PII to verify policy compliance.

---

## 3. Content & Layout Overview

Core layout components:

- **Header**
  - Title: `Lineage Explorer`.
  - Current focus node (e.g., `orders_dataset`).
  - Controls to adjust depth, direction (upstream/downstream), and filters.

- **Graph canvas** (D3/Cytoscape-style)
  - Nodes representing datasets, workflows, external systems, and reports.
  - Edges representing data flows or dependencies.
  - Zoom, pan, and fit-to-view controls.

- **Node details panel**
  - Appears when a node is selected.
  - Shows type, description, owners, tags, health, and key links (to Dataset Detail, Workflow Designer, dashboards).

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

A complete Lineage Explorer should:

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
- Nodes representing workflows can link into the Workflow Designer for editing.
- Datasets and reports connect back to Dataset Detail and dashboard pages.

---

## 8. Links to More Detail & Working Entry Points

- Dataset Detail: `12_dataset_detail_insights_page.md`.
- Workflows: `05_workflows_page.md`, `06_workflow_designer_canvas.md`.
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
H1: Lineage Explorer
Focus: orders_dataset            [ Depth: 2 ▾ ] [ Direction: Up+Down ▾ ]

[ Graph canvas showing upstream sources and downstream reports ]

Node details (right panel):
- Type: Dataset
- Name: orders_dataset
- Owners: Data Platform Team
- Tags: [pii] [gold]
- Health: 92/100
- Links: [Open Dataset Detail] [Open Lineage in SQL Workspace] [Related Workflows]
```
