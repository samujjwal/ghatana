# 1. Dashboard Page – CES Overview – Deep-Dive Spec

Related routes & files:

- Routes: `/` and `/dashboard`
- Page: `src/pages/DashboardPage.tsx`
- Mock API: `src/lib/mock-api-client.ts`, `src/lib/mock-data.ts`
- Cards: `src/components/cards/DashboardCard.tsx`, `DashboardKPI`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Give operators a **control-tower style overview** of collections, workflows, executions, audit logs, and compliance status, with quick actions into deeper CES functionality.

**Primary goals:**

- Show key metrics: total/active workflows, executions, success rate, average execution time.
- Surface recent collections and workflows.
- Display high-level **audit log activity** and **compliance** status.
- Offer quick navigation to creating workflows, viewing executions, and exploring API docs.
 - Act as the **home dashboard** for Data Cloud’s CES slice, and a foundation for richer storage/query/optimizer/cost/health views described in the frontend TODOs.

**Non-goals:**

- Detailed run timelines (belong to an Executions page).
- Full compliance policy editing (belongs to compliance settings/console).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Data platform engineer / operator** overseeing the data collection and workflow system.
- **SRE / on-call** scanning for failures and anomalies.
- **Compliance / security partner** monitoring compliance scores.

**Key scenarios:**

1. **Health check at a glance**
   - User opens dashboard to see counts of workflows, executions, success rate, and compliance score.
   - If success rate drops or compliance warnings appear, they click through to details.

2. **Resuming recent work**
   - User sees recent collections and workflows, resumes work via deep-link.

3. **Investigating activity**
   - User scans recent audit log entries and compliance statuses for anomalies.

4. **Starting new workflows**
   - Quick actions to create a new workflow or jump to executions list.

---

## 3. Content & Layout Overview

From `DashboardPage.tsx`:

- State:
  - Collections, workflows, executions (mock data).
  - `stats`: totalWorkflows, activeWorkflows, totalExecutions, successRate, avgExecutionTime, auditEvents24h, complianceScore.
  - `auditLogs`, `complianceStatuses`, `recentActivity`.

**Layout:**

1. **Header bar**
   - Title `Dashboard` and subtitle.
   - Primary action: `New Workflow` → `/workflows/new`.

2. **Stats grid (KPI cards)**
   - Total Workflows.
   - Active Workflows.
   - Total Executions.
   - Success Rate.
   - Avg Execution Time.

3. **Collections & Workflows sections**
   - Left: Collections overview (total count + recent collections list).
   - Right: Workflows overview (active vs total, recent workflows, recent executions table).

4. **Audit & Compliance overview**
   - Audit Logs: last 24h count + recent events with icons/status and deep-links.
   - Compliance: overall score + small status list with badges.

5. **Quick actions**
   - Create New Workflow.
   - View All Executions.
   - Explore API Docs.

6. **Planned expansion – Data Cloud dashboards alignment**
   - Storage Usage: high-level storage consumption by tier (Bronze/Silver/Gold/Cold) and by key collections/datasets.
   - Query Performance: overview of query latency/throughput, top slow queries, and hotspots.
   - Optimizer Actions: feed of recent Data Brain actions (promotions, compactions, tiering changes).
   - Cost Explorer: summary widgets for estimated storage and compute spend, with drill-down links.
   - Dataset Health: aggregated health scorecards based on data quality, freshness, and access anomalies.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clear labels and statuses:**
  - Use plain language for metrics (e.g., "Total Workflows", "Success Rate").
- **Clickable cards:**
  - KPI cards link to relevant pages (e.g., `/workflows`, `/executions`).
- **Error feedback:**
  - If dashboard data load fails, show a red alert banner with a readable error message.
- **Loading state:**
  - Centered spinner and loading text while data is being fetched.

---

## 5. Completeness and Real-World Coverage

A full production dashboard should:

1. Use **real backend APIs**, not mock data.
2. Support **filtering by tenant/project/time range** (future work).
3. Allow direct drill-down from KPIs to filtered history views.
4. Align metrics with observability surfaces (AEP, Software Org dashboards).
5. Include dedicated **panels or tabs** for storage usage, query performance, optimizer actions, cost explorer, and dataset health scoring, powered by backend metrics and telemetry.
6. Offer clear navigation from these high-level panels into specialized pages (e.g., Storage Usage dashboard, Query Performance dashboard) if/when those are broken out.

---

## 6. Modern UI/UX Nuances and Features

- **Responsive layout:**
  - KPIs and cards rearrange well on tablet/mobile.
- **Visual hierarchy:**
  - KPIs at the top, then collections/workflows, then audit/compliance, then quick actions.
- **Status colors:**
  - Use consistent color semantics for success/warning/error/compliant/non-compliant.

---

## 7. Coherence with App Creator / Canvas & Platform

- Workflows and executions resemble **runtime plans** that could be edited in a canvas:
  - Dashboard metrics should map to plan nodes/edges and run history visible in the Workflow Designer.
- This dashboard is conceptually parallel to control-tower pages in App Creator and AEP.
- In the broader Data Cloud context, this page should aggregate metrics from **storage fabric, compute engine, AI Optimizer, and workflow engine**, providing a single-glance view before users dive into specialized dashboards or canvases.

---

## 8. Links to More Detail & Working Entry Points

- Workflow list: `src/pages/WorkflowsPage.tsx`
- Workflow designer canvas: `src/components/workflow/WorkflowCanvas.tsx`, `src/pages/WorkflowDesigner/index.tsx`
- Collections views: `src/pages/CollectionsPage.tsx`, `src/pages/CreateCollectionPage.tsx`, `src/pages/EditCollectionPage.tsx`

---

## 9. Gaps & Enhancement Plan

1. **Real data integration:**
   - Replace `mockApiClient` with real CES API integration via `@ghatana/api`.

2. **Time-range and tenant filters:**
   - Top-level filters to scope dashboard metrics.

3. **Alerting hooks:**
   - Integrate with observability (alerts/logs) and data-fabric status to show ingestion issues.

4. **Storage usage & cost surfaces:**
   - Add tiles/sections for storage by tier and by dataset, plus estimated storage cost, with links into a dedicated cost explorer.

5. **Query performance overview:**
   - Surface high-level query latency/throughput charts and highlight slow/expensive queries, with links into a deeper SQL/compute performance view.

6. **Optimizer action feed:**
   - Show recent Data Brain optimization actions (e.g., promotions, compactions, tiering) with impact summaries and links to the affected datasets/workflows.

7. **Dataset health scoring:**
   - Introduce a health score per key collection/dataset, aggregating freshness, quality, and anomaly signals into a simple, explainable metric.

---

## 10. Mockup / Expected Layout & Content

```text
Header
-------------------------------------------------------------------------------
Dashboard                              [ New Workflow ]
"Overview of your workflows and executions"

KPI Row
-------------------------------------------------------------------------------
| Total Workflows: 12 | Active Workflows: 8 | Total Executions: 3,452 |
| Success Rate: 97.5% | Avg. Execution Time: 2.3s                     |

Collections & Workflows
-------------------------------------------------------------------------------
[ Collections card ]                         [ Workflows card ]
- Total Collections: 7                       - Active Workflows: 5 / 12
- Recent Collections:                        - Recent Activities:
  • "Orders" – 1,234 entities, Active         • "Nightly ETL" – last run today, completed
  • "Customers" – 542 entities, Active        • "User Sync" – last run yesterday, failed

Audit & Compliance
-------------------------------------------------------------------------------
Audit Logs (Last 24h: 37)                   Compliance (Score: 92%)
- "Created workflow Data Export" (success)  - Data Retention – Compliant
- "Updated collection Products" (success)   - Access Control – Compliant
- "Failed login" (failed)                   - Audit Logging – Warning

Quick Actions
-------------------------------------------------------------------------------
[ Create New Workflow ]  [ View All Executions ]  [ Explore API Docs ]
```
