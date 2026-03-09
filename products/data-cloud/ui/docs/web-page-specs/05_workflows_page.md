# 5. Workflows Page – List View – Deep-Dive Spec

Related routes & files:

- Route: `/workflows`
- Page: `src/pages/WorkflowsPage.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **catalog view of all workflows**, including structure and basic stats, as an entry point into workflow design and execution monitoring.

**Primary goals:**

- List workflows with status, description, and structural overview (nodes/edges/triggers).
- Provide navigation into Workflow Designer for each workflow.
- Encourage creation of new workflows.
- Act as the **catalog of workflow DAGs** in Data Cloud, where users can discover, filter, and select workflows (including future templates) before opening them in the visual builder.

**Non-goals:**

- Detailed execution history (belongs to an executions page).
- Canvas editing itself (belongs to Workflow Designer page).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Workflow designer / engineer** building and maintaining workflows.
- **Operator** needing to see what workflows exist and their high-level status.

**Key scenarios:**

1. **Scanning workflows**
   - User lands on `/workflows`.
   - Sees list of workflows, each with name, status, description, and structure summary.

2. **Jumping into a workflow canvas**
   - Clicking a workflow card → `/workflows/:id` (Workflow Designer) for editing.

3. **Creating a new workflow**
   - Click `Create Workflow` → `/workflows/new` (Workflow Designer, new workflow).

---

## 3. Content & Layout Overview

From `WorkflowsPage.tsx`:

- State:
  - `workflows`, `loading`, `error` from `mockApiClient.getWorkflows()`.

- Layout:
  - Header: `Workflows` + subtitle and `Create Workflow` button.
  - Loading state: centered spinner + text.
  - Error state: red banner.
  - Empty state: text + CTA to create first workflow.
  - List of `WorkflowCard` components, each showing:
    - Name + status badge (active, paused, draft, archived).
    - Description.
    - A **chips-style visualization** of workflow nodes in sequence (badges with type-based colors and status icons).
    - Stats: node count, connection count, execution count, triggers count.
    - Last execution date (if available).
  - **Planned expansion (Workflow Builder alignment):**
    - Additional controls for choosing a **workflow template** (e.g., ingestion, ML, BI refresh) when creating a new workflow.
    - Optional inline indicators of **recent run health** (e.g., success/failure badge, last run duration) derived from the executions system.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Status semantics:**
  - Distinct colors for active, paused, draft, and archived states.
- **Compact visualization:**
  - Node chips (start, query, transform, decision, etc.) give a quick sense of workflow shape.
- **Clear calls to action:**
  - Primary `Create Workflow` button.
  - Entire card clickable to go to the Designer.

---

## 5. Completeness and Real-World Coverage

A full production Workflows page should:

1. Support **filters and search** (by status, owner, tags).
2. Show key runtime metrics (recent failure rate, median duration, last run outcome) at a glance per workflow.
3. Indicate if workflows are **in use** by collections or data-fabric connectors.
4. Provide a structured way to browse and instantiate **workflow templates** (ingestion pipelines, ML workflows, BI refresh jobs) that open pre-configured DAGs in the Workflow Designer.
5. Integrate with **workflow executions views** so users can jump from a workflow card directly into detailed run history or logs.

---

## 6. Modern UI/UX Nuances and Features

- **Responsive card layout:**
  - Cards should be readable on narrow viewports.
- **Overflow handling:**
  - Node chip row is horizontally scrollable.
- **Tooltips:**
  - Consider tooltips on node chips explaining type and role.

---

## 7. Coherence with App Creator / Canvas & Platform

- Each workflow maps directly to a **canvas plan** in Workflow Designer:
  - Nodes correspond to blocks on the canvas.
  - Edges correspond to connections.
- This page is a textual/summary complement to the visual canvas.
- In the broader Workflow Builder vision, this page also acts as a **launchpad for templates** and curated best-practice DAGs, similar to how App Creator might surface app templates before opening a canvas.

---

## 8. Links to More Detail & Working Entry Points

- Workflow Designer: `src/pages/WorkflowDesigner/index.tsx`, `src/components/workflow/WorkflowCanvas.tsx`
- Dashboard metrics: `src/pages/DashboardPage.tsx`

---

## 9. Gaps & Enhancement Plan

1. **Filters & search:**
   - Add filter controls for status and search by name.

2. **Runtime health indicators:**
   - Surface quick signals like last run state, error rate.

3. **Integration with executions & data models:**
   - Link each workflow to its execution history and collections/entities it touches.

4. **Template selection & discovery:**
   - Add support for selecting from pre-defined workflow templates (ingestion, ML, BI) when clicking `Create Workflow`, and exposing template metadata (description, recommended use cases).

5. **Deeper execution insights on cards:**
   - Enrich cards with summarized metrics (e.g., last 24h success rate, average duration, next scheduled run) without turning this page into a full executions dashboard.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Workflows                                [ Create Workflow ]
"Design and manage your automated workflows"

[Card] "Nightly ETL"        [Active]
-------------------------------------------------------------------------------
"Extracts data from OLTP DB, transforms, and loads into warehouse."
Nodes: [Start] → [Query DB] → [Transform] → [Load to Warehouse] → [End]
Stats: 5 Nodes   4 Connections   120 Executions   2 Triggers
Last executed: 2025-11-18

[Card] "Customer Sync"      [Paused]
-------------------------------------------------------------------------------
"Syncs customer records from CRM to CES collections."
Nodes: [Start] → [API Call] → [Decision] → [Notify]
...
```
