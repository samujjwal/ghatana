# 5. Pipelines Page – List View – Deep-Dive Spec

Related routes & files:

- Canonical route: `/pipelines`
- Compatibility alias: `/workflows`
- Page: `src/pages/WorkflowsPage.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide an **outcome-first review surface for pipelines**, keeping next action and recent run state visible while pushing structural detail into the advanced editor only when needed.

**Primary goals:**

- List pipelines with next action, status, last run, schedule, and owner so operators can review outcome readiness quickly.
- Provide progressive disclosure into advanced pipeline details and AI recommendations only when needed.
- Provide navigation into the advanced pipeline editor for structural changes.
- Encourage creation of new pipelines without turning the page into a workflow-internals dashboard.

**Non-goals:**

- Detailed execution history (belongs to an executions page).
- Canvas editing itself (belongs to the advanced editor page).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Workflow designer / engineer** building and maintaining workflows.
- **Operator** needing to see what workflows exist and their high-level status.

**Key scenarios:**

1. **Scanning workflows**
   - User lands on `/pipelines`.
   - Sees list of pipelines, each with name, status, summary, next action, last run, schedule, and owner.

2. **Jumping into a workflow canvas**
   - Clicking a pipeline card → `/pipelines/:id?mode=advanced` for advanced editing.

3. **Creating a new pipeline**
   - Click `New Pipeline` or `Start a new pipeline` → `/pipelines/new` (Smart Workflow Builder, then advanced designer if needed).

---

## 3. Content & Layout Overview

From `WorkflowsPage.tsx`:
 - Search and status filtering over the canonical pipeline list.
 - Summary cards showing total, needs-attention, recently-run, scheduled, and draft/archived counts.
 - Outcome-framed cards with progressive disclosure into advanced editing and AI recommendations.

This page should stay focused on pipeline discovery and entry points rather than growing into a full execution console.
- Canvas editing itself (belongs to the advanced editor page).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Workflow designer / engineer** building and maintaining workflows.
- **Operator** needing to see what workflows exist and their high-level status.

**Key scenarios:**

1. **Scanning workflows**
   - User lands on `/pipelines`.
   - Sees list of workflows, each with name, status, description, and structure summary.

2. **Jumping into a workflow canvas**
   - Clicking a workflow card → `/pipelines/:id?mode=advanced` for advanced editing.

3. **Creating a new pipeline**
   - Click `Create Pipeline` → `/pipelines/new` (Smart Workflow Builder, then advanced designer if needed).

---

## 3. Content & Layout Overview

From `WorkflowsPage.tsx`:

- State:
  - `workflows`, `loading`, `error` from the canonical `workflowsApi` adapter.

- Layout:
   - Header: `Workflows` + subtitle and `New Pipeline` button.
   - Outcome banner explaining why the list prioritizes actions over internals.
  - Loading state: centered spinner + text.
  - Error state: red banner.
  - Empty state: text + CTA to create first workflow.
   - List of review cards, each showing:
      - Name + status badge (active, paused, draft, archived).
      - Outcome summary.
      - Next-action panel.
      - Summary stats: last run, schedule, flow size, owner.
      - Review and advanced-editor actions.
   - Review modal:
      - Outcome summary and latest-run framing by default.
      - `Show pipeline details` disclosure for schedule, created date, flow size, owner, tags, and AI recommendations.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Status semantics:**
  - Distinct colors for active, paused, draft, and archived states.
- **Calmer review model:**
   - The main list should answer “what needs to happen next?” before “how is this pipeline built?”
- **Clear calls to action:**
   - Primary `New Pipeline` button.
  - Review opens the modal, while advanced-editor actions stay explicit.

---

## 5. Completeness and Real-World Coverage

A full production Workflows page should:

1. Support **filters and search** (by status, owner, tags).
2. Show key runtime metrics (recent failure rate, median duration, last run outcome) at a glance per workflow.
3. Indicate if workflows are **in use** by collections or data-fabric connectors.
4. Provide a future-ready place for curated starter pipelines once launcher-backed template browsing exists.
5. Integrate with **workflow executions views** so users can jump from a workflow card directly into detailed run history or logs.

---

## 6. Modern UI/UX Nuances and Features

- **Responsive card layout:**
  - Cards should be readable on narrow viewports.
- **Progressive disclosure:**
   - Keep AI recommendations and structural detail behind an explicit disclosure inside the review modal.

---

## 7. Coherence with App Creator / Canvas & Platform

- Each workflow maps directly to a **canvas plan** in the advanced editor, but the list page itself is a review surface rather than a structural canvas summary.
- In the broader Workflow Builder vision, this page could eventually act as a **launchpad for curated starter pipelines** once template-browsing routes exist.

---

## 8. Links to More Detail & Working Entry Points

- Workflow Designer: `src/pages/WorkflowDesigner/index.tsx`, `src/components/workflow/WorkflowCanvas.tsx`
- Intelligent Hub: `src/pages/IntelligentHub.tsx`

---

## 9. Gaps & Enhancement Plan

1. **Filters & search:**
   - Add filter controls for status and search by name.

2. **Runtime health indicators:**
   - Surface quick signals like last run state, error rate.

3. **Integration with executions & data models:**
   - Link each workflow to its execution history and collections/entities it touches.

4. **Template selection & discovery:**
   - Add support for selecting from curated starter pipelines only after the launcher exposes template-browsing routes and metadata.

5. **Deeper execution insights on cards:**
   - Enrich cards with summarized metrics (e.g., last 24h success rate, average duration, next scheduled run) without turning this page into a full executions dashboard.

---

## 10. Mockup / Expected Layout & Content

```text
H1: Workflows                                [ Create Pipeline ]
"Design and manage your automated workflows"

[Card] "Nightly ETL"        [Active]
-------------------------------------------------------------------------------
"Extracts data from OLTP DB, transforms, and loads into warehouse."
Nodes: [Start] → [Query DB] → [Transform] → [Load to Warehouse] → [End]
Stats: 5 Nodes   4 Connections   120 Executions   2 Triggers
Last executed: 2025-11-18

[Card] "Customer Sync"      [Paused]
-------------------------------------------------------------------------------
"Syncs customer records from CRM to Data Cloud collections."
Nodes: [Start] → [API Call] → [Decision] → [Notify]
...
```
