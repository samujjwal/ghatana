# 15. AI Assistant & Semantic Search – Deep-Dive Spec

> **Status:** Planned cross-cutting feature – no concrete implementation in CES UI yet. This spec captures the AI Assistant and semantic search capabilities from `frontend_todo (1).md`.

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide an **AI-powered assistant** that helps users discover datasets, write SQL, design workflows, and understand lineage through natural language.

**Primary goals:**

- Offer a chat-like assistant UI available from anywhere in the app.
- Support natural language → SQL and natural language → workflow.
- Explain queries, workflows, and lineage in human terms.
- Enable semantic search for datasets, columns, and workflows.

**Non-goals:**

- Acting as the only way to use the product; all actions should remain possible via normal UIs.
- Low-level model management (handled by backend/infra).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **New or occasional users** unfamiliar with datasets or SQL.
- **Analysts** who want to move faster by describing intent.
- **Engineers** using AI help to refactor or optimize complex workflows/queries.

**Key scenarios:**

1. **Natural language → SQL**
   - User asks: "Show me total completed orders per day for the last 7 days".
   - Assistant proposes a SQL query, explains it, and lets the user open it in SQL Workspace.

2. **Natural language → workflow**
   - User asks: "Create a nightly job that syncs customers from CRM to the Gold `customers` table and notifies Slack on errors".
   - Assistant suggests a workflow template or node sequence and opens it in Workflow Designer.

3. **Explaining lineage and metrics**
   - User asks: "Why is the `revenue` metric on the Executive Dashboard higher this week?".
   - Assistant inspects lineage, recent optimizer actions, and cost/usage, then returns an explanation and links.

---

## 3. Content & Layout Overview

Key surfaces:

- **Global assistant entry point**
  - Icon/button in the shell (e.g., bottom-right or in the header).
  - Opens a chat panel or side drawer.

- **Chat interface**
  - Message history with user and assistant turns.
  - Input box with support for references (datasets, queries, workflows).
  - Suggested prompts and follow-up questions.

- **Semantic search bar** (global or in Dataset Explorer)
  - Searches across datasets, columns, workflows, and dashboards using embeddings.

- **Context-aware actions**
  - From SQL Workspace: "Explain this query" or "Optimize this query".
  - From Dataset Detail: "Describe how this dataset is used".
  - From Workflow Designer: "Suggest improvements to this workflow".

---

## 4. UX Requirements – User-Friendly and Valuable

- **Trust & transparency:**
  - Clearly label AI-generated content and provide explanations.
- **Safe operations:**
  - For destructive or expensive actions, require explicit confirmation.
- **Contextual grounding:**
  - Make it easy to see which datasets/queries/workflows the assistant is using as context.
- **Non-blocking:**
  - Assistant runs alongside normal UI; users can ignore it if desired.

---

## 5. Completeness and Real-World Coverage

A robust AI Assistant should:

1. Ground answers in **actual metadata, lineage, and query history**, not hallucinations.
2. Respect security and access control.
3. Log interactions for auditing and model improvement (within policy limits).
4. Integrate with multiple model providers or on-prem models.
5. Provide hooks for domain-specific prompts and tool integrations.

---

## 6. Modern UI/UX Nuances and Features

- **Multi-modal hints (future):**
  - Attach screenshots, query plans, or lineage snippets for context.
- **Inline chips:**
  - Render recognized entities (datasets, workflows) as clickable chips.
- **Session management:**
  - Support multiple conversations and pinned insights.

---

## 7. Coherence with App Creator / Canvas & Platform

- The assistant should be consistent across Data Cloud workspaces (CES, SQL, App Creator, AEP).
- It can help generate **app schemas and flows** in App Creator, similar to workflows here.
- Semantic search should share a unified embedding/index service with other products.

---

## 8. Links to More Detail & Working Entry Points

- SQL Workspace: `14_sql_workspace_page.md`.
- Dataset Explorer: `11_dataset_explorer_list_page.md`, `12_dataset_detail_insights_page.md`.
- Workflow Builder: `05_workflows_page.md`, `06_workflow_designer_canvas.md`.
- Backend & infra: AI/ML services and vector search infrastructure.

---

## 9. Gaps & Enhancement Plan

1. **Model and tool selection:**
   - Decide which models and tools (SQL runner, workflow engine, lineage graph) are exposed to the assistant.

2. **Prompt and safety design:**
   - Design prompts and guardrails to minimize hallucinations and unsafe actions.

3. **Telemetry & improvement loop:**
   - Decide what to log and how to use it for improving suggestions.

4. **UX integration:**
   - Determine default placement, shortcuts, and how it coexists with global search.

---

## 10. Mockup / Expected Layout & Content

```text
[ ? ] Assistant
-----------------------------------------------------------------------------
User: "Show me total completed orders per day for the last 7 days."

Assistant:
- "I found a dataset `orders_dataset` with order status and date columns."
- "Here is a query that answers your question:" [Open in SQL Workspace]

SELECT order_date::date AS day,
       COUNT(*) AS completed_orders
FROM orders_dataset
WHERE status = 'COMPLETED'
  AND order_date >= CURRENT_DATE - INTERVAL '7' DAY
GROUP BY day
ORDER BY day;
```
