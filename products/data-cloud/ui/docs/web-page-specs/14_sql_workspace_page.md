# 14. SQL Workspace Page – Deep-Dive Spec

> **Status:** Planned page – no concrete implementation in CES UI yet. This spec implements the SQL Workspace ideas from `frontend_todo (1).md`.

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **full-featured SQL workspace** for authoring, running, and managing queries against Data Cloud datasets, with schema-aware assistance and insights.

**Primary goals:**

- Allow users to write and run SQL queries.
- Provide schema-aware autocomplete and helpers.
- Show results in a responsive, virtualized grid.
- Manage saved queries, versions, and history.
- Offer AI-powered query insights and optimization suggestions.

**Non-goals:**

- Full scheduling/orchestration of queries (handled by Workflow Builder).
- Low-level cluster management.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Analyst/Scientist** writing ad-hoc or recurring queries.
- **Data engineer** prototyping transformations before turning them into workflows.
- **Ops/SRE** inspecting queries for performance issues.

**Key scenarios:**

1. **Ad-hoc analysis**
   - User opens SQL Workspace, writes SQL referencing datasets from the catalog, and views results.

2. **Query optimization**
   - User pastes an existing slow query; the workspace highlights anti-patterns and suggests improvements.

3. **Saved query management**
   - User maintains a library of saved queries with versions and descriptions.

---

## 3. Content & Layout Overview

Proposed layout:

- **Header**
  - Name of current query (or "Untitled Query").
  - Buttons: Run, Save, Save As, Explain, Format.

- **Main split view**
  - Left/top: **SQL editor** (Monaco-based) with:
    - Syntax highlighting, linting, and formatting.
    - Schema-aware autocomplete (datasets, columns, functions).
  - Right/bottom: **Results pane** with:
    - Virtualized table for large results.
    - Tabs for Results, Schema, Plan/Explain, Logs.

- **Side panel**
  - Schema browser tied to the metadata catalog.
  - List of saved queries and history.

- **Status area**
  - Query execution status, timing, and row counts.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Responsive editing:**
  - Large queries and results should be manageable without lag.
- **Actionable feedback:**
  - Highlight slow/expensive patterns and suggest indexes/partitions/materializations.
- **Safe defaults:**
  - Guardrails for destructive operations in shared environments.
- **Keyboard-centric:**
  - Rich keyboard shortcuts for running, saving, and navigating.

---

## 5. Completeness and Real-World Coverage

A complete SQL Workspace should:

1. Integrate with Data Cloud’s **compute engine(s)** (Trino, Spark, etc.).
2. Support multiple catalogs/schemas and engines.
3. Store query history and versions in a durable backend.
4. Tie into the **AI Optimizer/Data Brain** for suggestions and plan analysis.
5. Respect security, row/column-level policies, and masking.

---

## 6. Modern UI/UX Nuances and Features

- **Dark/light themes:**
  - Editor and results should respect global theme settings.
- **Inline suggestions:**
  - Chat-like or inline hints for writing better SQL.
- **Result export:**
  - Export options (CSV, Parquet, etc.) with sensible limits.

---

## 7. Coherence with App Creator / Canvas & Platform

- Queries often back **dashboards and apps** built via App Creator; this workspace is where they are prototyped.
- Successful queries can be converted into **workflow nodes** or templates.
- Shares components and patterns with other workspaces (Dataset Explorer, Lineage Explorer).

---

## 8. Links to More Detail & Working Entry Points

- Dataset Explorer: `11_dataset_explorer_list_page.md`, `12_dataset_detail_insights_page.md`.
- Lineage Explorer: `13_lineage_explorer_page.md`.
- AI Assistant (planned): `15_ai_assistant_and_semantic_search.md`.

---

## 9. Gaps & Enhancement Plan

1. **Backend routing & engines:**
   - Decide which engines are supported and how to route queries.

2. **Plan/Explain integration:**
   - Define APIs for retrieving and visualizing query plans and cost estimates.

3. **AI insights pipeline:**
   - Connect telemetry to ML models that power SQL suggestions.

4. **Saved query model:**
   - Design schema for saved queries, versions, and access control.

---

## 10. Mockup / Expected Layout & Content

```text
[ Untitled Query ]                      [ Run ] [ Save ] [ Explain ] [ Format ]

SQL Editor (Monaco)
-----------------------------------------------------------------------------
SELECT *
FROM orders_dataset
WHERE status = 'COMPLETED'
  AND order_date >= CURRENT_DATE - INTERVAL '7' DAY;

[Results] [Schema] [Plan] [Logs]
-----------------------------------------------------------------------------
Results table (virtualized)
- 1,000 rows shown (of 12,345)
Execution time: 1.2s   Engine: Trino   Catalog: data_cloud
```
