# 1. Intelligent Hub – Home Surface – Deep-Dive Spec

Related routes & files:

- Routes: `/`, `/dashboard`, and `/hub`
- Page: `src/pages/IntelligentHub.tsx`

_Reality note: this spec previously described the old dashboard concept. The current shipped home surface is the Intelligent Hub with outcome-first launchers and shell-role disclosure._

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Give users an **outcome-first home surface** for getting to Data Explorer, Pipelines, Query, and operator workflows without forcing them through a control-tower summary page first.

**Primary goals:**

- Launch users quickly into Data, Pipelines, Query, and operator workflows.
- Surface enough recent activity and health context to resume work without forcing a control-tower-first mental model.
- Offer quick navigation to creating workflows, exploring data, querying, and opening operator-only diagnostics when relevant.
- Act as the **home surface** for Data Cloud rather than a standalone control-tower surface.

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
   - User opens the Intelligent Hub to see lightweight summaries, continue-working links, and the next recommended actions.
   - If trust warnings or runtime issues appear, they click through to operator views rather than staying on a KPI wall.

2. **Resuming recent work**
   - User sees recent collections and workflows, resumes work via deep-link.

3. **Investigating activity**
   - User scans recent activity, lightweight summaries, and role-gated follow-up links for anomalies.

4. **Starting new workflows**
   - Quick actions to create a new workflow or jump to executions list.

---

## 3. Content & Layout Overview

From `IntelligentHub.tsx`:

- State and content are intentionally outcome-oriented:
   - recent activity and continue-working suggestions
   - quick launch cards for data, pipelines, query, trust, and operator diagnostics
   - operator diagnostics should route investigation to Insights, including AI fallback/confidence telemetry when runtime review is needed
   - lightweight summaries rather than a full KPI wall

**Layout:**

1. **Header and role-aware context**
   - Intelligent Hub title and supporting copy.
   - Outcome-first guidance rather than control-tower-heavy chrome.

2. **Quick launch and continue-working sections**
   - Data, Pipelines, Query, and role-gated operator actions.
   - Recent activity and continue-working links into canonical routes.

3. **Lightweight operational summaries**
   - Enough recent state to orient users without claiming a full metrics console.

4. **Planned expansion – higher-fidelity operator insights**
   - Storage Usage: high-level storage consumption by tier (Bronze/Silver/Gold/Cold) and by key collections/datasets.
   - Query Performance: overview of query latency/throughput, top slow queries, and hotspots.
   - Optimizer Actions: feed of recent Data Brain actions (promotions, compactions, tiering changes).
   - Cost Explorer: summary widgets for estimated storage and compute spend, with drill-down links.
   - Dataset Health: aggregated health scorecards based on data quality, freshness, and access anomalies.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clear next actions:**
   - The most important affordances should move users into canonical `/data`, `/pipelines`, and `/query` flows quickly.
- **Role-aware disclosure:**
   - Operator-only diagnostics and trust actions should appear only when shell role calls for them.
- **Error feedback:**
   - If home-surface data load fails, show a readable alert banner with an actionable recovery path.
- **Loading state:**
  - Centered spinner and loading text while data is being fetched.

---

## 5. Completeness and Real-World Coverage

A fuller production home surface should:

1. Keep using **real backend APIs** for recent activity and live launch points.
2. Preserve the simpler intent-first model instead of regressing into a control-tower-first shell.
3. Add richer operator summaries only where launcher-backed telemetry exists.
4. Offer clear navigation into specialized views such as Insights, Query, or Trust instead of inventing new top-level summary pages prematurely.

---

## 6. Modern UI/UX Nuances and Features

- **Responsive layout:**
   - Summary cards and quick actions rearrange well on tablet/mobile.
- **Visual hierarchy:**
   - Outcome launchers and summaries come first, then continue-working and recent activity, then operator follow-up paths.
- **Status colors:**
  - Use consistent color semantics for success/warning/error/compliant/non-compliant.

---

## 7. Coherence with App Creator / Canvas & Platform

- Workflows and executions resemble **runtime plans** that could be edited in a canvas:
   - Summary signals should still map to plan nodes/edges and run history visible in the Workflow Designer.
- This home surface is conceptually parallel to outcome-first launchers in adjacent Ghatana products.
- In the broader Data Cloud context, this page should summarize **storage fabric, compute engine, AI optimizer, and workflow engine** health without pretending to be a full BI or control-tower product.

---

## 8. Links to More Detail & Working Entry Points

- Workflow list: `src/pages/WorkflowsPage.tsx`
- Workflow designer canvas: `src/components/workflow/WorkflowCanvas.tsx`, `src/pages/WorkflowDesigner/index.tsx`
- Data explorer views: `src/pages/DataExplorer.tsx`, `src/pages/CreateCollectionPage.tsx`, `src/pages/EditCollectionPage.tsx`

---

## 9. Gaps & Enhancement Plan

1. **Real data integration:**
   - Continue moving all remaining summary cards and quick actions onto canonical launcher-backed adapters.

2. **Time-range and tenant filters:**
   - Add scoping controls only when launcher-backed summary data genuinely needs them.

3. **Alerting hooks:**
   - Keep alert entry points role-gated and secondary to the primary outcome launchers even though a live operator alerts surface now exists.

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
Intelligent Hub                         [ Explore Data ] [ Create Pipeline ]
"Start from the outcome you need, then drill into operator views only when necessary"

Primary Launchers
-------------------------------------------------------------------------------
[ Data ] [ Pipelines ] [ Query ]

Operator Launchers (role-gated)
-------------------------------------------------------------------------------
[ Insights ] [ Trust ] [ Events ]

Recent Activity / Continue Working
-------------------------------------------------------------------------------
- "Customer Events" → `/data/col-001`
- "Nightly ETL" → `/pipelines/wf-123?mode=advanced`
- "Recent data quality review" → `/data?view=quality`
```
