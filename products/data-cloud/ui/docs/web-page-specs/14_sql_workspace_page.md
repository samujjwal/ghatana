# 14. SQL Workspace Page – Deep-Dive Spec

> **Status:** Partially implemented. The current canonical route is `/query`, with live analytics execution, runtime capability truth, recommendation guidance, explain-plan review, and partial NLQ assistance; this spec still describes the fuller target state beyond the shipped surface.

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **full-featured SQL workspace** for authoring, running, and managing queries against Data Cloud datasets, with schema-aware assistance and insights.

**Primary goals:**

- Allow users to write and run SQL queries against the canonical analytics route.
- Provide schema-aware helpers plus runtime-aware engine recommendations.
- Show results in a responsive table and expose live capability truth before optional paths are used.
- Manage saved queries, versions, and history.
- Offer AI-powered query suggestions with explicit low-confidence clarification instead of pretending every NL request is unambiguous.

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
  - User pastes an existing slow or cross-source query; the workspace recommends direct, federated, or review-first execution.

3. **Saved query management**
   - User maintains a library of saved queries with versions and descriptions.

---

## 3. Content & Layout Overview

Proposed layout:

- **Header**
  - Current query workspace heading.
  - Buttons: Run, Save, AI Assist, engine toggle, and lightweight formatting controls.

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

- **Runtime truth area**
  - Capability truth panel for analytics, Trino/federation, and AI assist.
  - Recommendation banner for direct vs federated vs review-first execution.
  - Explain-plan review showing query type, data sources, estimated cost, and execution guardrails before optional paths are used.
  - Optional dependency warning when launcher capability truth is degraded or unavailable.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Responsive editing:**
  - Large queries and results should be manageable without lag.
- **Actionable feedback:**
  - Highlight slow/expensive patterns, recommend the supported execution path, expose explain-plan guardrails, and ask for clarification when collection scope is ambiguous.
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

- Queries often back **operator insights, derived views, and apps** built elsewhere in the platform; this workspace is where they are prototyped.
- In the current product, this route is the canonical query workbench rather than one member of a larger analytics suite.
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
  - The workspace now calls the canonical explain route and surfaces a review-first plan summary; richer plan visualization and cost breakdown still remain open.

3. **Clarification workflow expansion:**
  - Extend the current low-confidence scope prompts into richer guided clarification when multiple collections or time windows are plausible.

4. **AI insights pipeline:**
   - Connect telemetry to ML models that power SQL suggestions.

5. **Saved query model:**
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
