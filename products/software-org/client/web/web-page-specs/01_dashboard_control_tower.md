# 1. Dashboard (Control Tower) – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 1. `/` – Control Tower Dashboard](../WEB_PAGE_FEATURE_INVENTORY.md#1---control-tower-dashboard)

**Code file:**

- `src/features/dashboard/Dashboard.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Give a single-screen "control tower" view of software delivery and reliability, so leaders can quickly see how things are going, what changed, and what AI recommends doing next.

**Primary goals:**

- Provide **organization-wide KPIs** (deployments, lead time, MTTR, CFR, security, cost).
- Show **time-based behavior** via a simple event timeline.
- Surface **AI-generated insights** with confidence and clear reasoning.
- Let users **filter by tenant and time range**, and enable period-over-period comparison.

**Non-goals:**

- Detailed per-department or per-service drill-down (those live in Departments, Reports, Real-Time Monitor, etc.).
- Managing workflows or automation (Automation Engine, Workflow Explorer cover those).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **VP/Director of Engineering:** wants health-at-a-glance across all teams.
- **SRE/Platform Lead:** monitors reliability trends and incident posture.
- **Product/Program Manager:** checks whether current initiatives improve delivery.

**Key real-world scenarios:**

1. **Weekly health check**
   - GIVEN: It’s Monday morning.
   - WHEN: The Director opens the Dashboard for `All Tenants` and time range `Last 7 days`.
   - THEN: They see a KPI grid showing deployments/week, lead time, MTTR, CFR, security posture, and cost trends, plus AI insights summarizing key issues (e.g., test flakiness, deployment slowdown).

2. **Investigating a sudden MTTR spike**
   - GIVEN: MTTR looks worse than last week.
   - WHEN: SRE turns on **Compare Mode** and sees MTTR up +25% while change failure rate also increased.
   - THEN: They review AI insights (e.g., “Deployment Optimization” or “Security Posture”), note the reasoning, and decide whether to approve a recommendation or drill into Departments/Reports.

3. **Tenant-specific review**
   - GIVEN: A major customer (Tenant B) complains about instability.
   - WHEN: The PM selects `Tenant B` and `Last 30 days`.
   - THEN: They see KPIs and trends specific to Tenant B and a subset of AI insights that reference that tenant.

---

## 3. Content & Layout Overview

Key elements from `Dashboard.tsx`:

- **Page header:**
  - Title: `Control Tower`.
  - Subtitle: `Organization-wide metrics and AI insights`.

- **Filters bar (sticky):**
  - **Tenant selector** (connected to `selectedTenantAtom`):
    - Values: `All Tenants`, `Tenant A` (`tenant-1`), `Tenant B` (`tenant-2`).
  - **Time range selector** (from `timeRangeAtom`):
    - Values: `Last 7 days` (`7d`), `Last 30 days` (`30d`), `Last 90 days` (`90d`), `Custom` (`custom`).
  - **Compare Mode toggle** (from `compareEnabledAtom`):
    - Simple checkbox + label: `Compare Mode`.

- **Main grid layout:**
  - **Left (2/3 width) column:**
    - Section: **Key Metrics**
      - `KpiGrid` with multiple `KpiCard` components from `useOrgKpis()`.
      - Each card shows `title`, `value`, `unit`, `trend`, `target`, `status`.
    - Section: **Event Timeline**
      - `TimelineChart` with events of types `deploy`, `test`, `feature`, `incident`.
  - **Right (1/3 width) column:**
    - Section: **AI Insights**
      - List of `InsightCard` components, one per AI insight.

- **Conditional comparison info bar:**
  - When `compareEnabled === true`, an info box appears explaining that metrics are being compared period-over-period.

---

## 4. UX Requirements – User-Friendly and Valuable

### 4.1 Filters

- Tenant and Time Range must feel like **global context selectors** for the page.
- Changing a filter:
  - Immediately updates KPIs and timeline (with loading states where needed).
  - Keeps AI insights visually consistent with the chosen period/tenant.

### 4.2 KPIs

- KPI cards must be readable at a glance:
  - Large numeric value.
  - Short unit (e.g., `/week`, `hrs`, `%`).
  - Simple trend indication such as `+12%` with up/down arrow and color.
- Clicking a KPI card should be the natural entry point for drill-down (even if initially it just logs to console, the UX should anticipate navigation to Departments or Reports).

### 4.3 Timeline

- Timeline should visually distinguish different event types (deploy/test/feature/incident) via color or icon.
- Hover or click behavior should show timestamp and basic event info.

### 4.4 AI Insights

- Each insight card must show:
  - Clear title (e.g., `QA Pipeline Analysis`).
  - One-sentence insight (e.g., `15% flakiness detected in auth tests`).
  - Confidence percentage.
  - Short reasoning preview (first ~80 chars).
  - Status badge (Pending/Approved).
- Selecting an insight should expand details (either inline or via a side panel) that reveal full reasoning and recommended action.
- Actions Approve/Defer/Reject must feel safe and reversible once connected to real workflows.

---

## 5. Completeness and Real-World Coverage

The Dashboard must realistically cover:

- **Operational performance:** Deployments/week, MTTR, CFR.
- **Flow efficiency:** Lead time for changes.
- **Security posture:** Summary of critical vulnerabilities/incidents.
- **Cost awareness:** Basic indicator of infra/application cost trends.
- **AI recommendations:** Concrete, contextual suggestions with confidence.

It should be possible to answer questions like:

- "Are we shipping faster this week than last week?"
- "Are incidents down or up vs last period?"
- "Are test flakiness or security scans a bottleneck right now?"

---

## 6. Modern UI/UX Nuances and Features

- **Sticky filter bar** that remains visible as you scroll the rest of the content.
- **Skeleton/loading states** for KPIs and timeline while data is fetching.
- **Graceful error states** for KPI load failures (already present in code).
- **Responsive layout:**
  - One-column on small screens, two-column on larger (as implemented via Tailwind grid).
- **Accessible controls:**
  - Filters and toggle should be reachable via keyboard and clearly labeled.

---

## 7. Coherence and Consistency Across the App

- The `Control Tower` header and subtitle follow the shared pattern defined by the shell spec.
- Tenant/time filters mirror concepts used elsewhere (Reports, Departments, Real-Time Monitor) so users see consistent language.
- AI Insights here should conceptually match AI Intelligence/HITL flows (e.g., similar wording for confidence and status).

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#1---control-tower-dashboard`
- Dashboard implementation: `src/features/dashboard/Dashboard.tsx`
- Shared components:
  - `KpiCard`, `KpiGrid`: `src/shared/components/`
  - `TimelineChart`: `src/shared/components/TimelineChart.tsx`
  - `InsightCard`: `src/shared/components/InsightCard.tsx`

Potential navigation targets:

- From KPI card → `/reports` or `/departments` with appropriate filters.
- From incident-centric timeline period → `/realtime-monitor`.
- From AI insight → `/ai` or `/automation-engine`/`/hitl` depending on decision type.

---

## 9. Open Gaps & Enhancement Plan

- Link KPI cards to deeper drill-down views.
- Connect AI Insights approval to HITL / Automation Engine.
- Add clear explanation text near Compare Mode describing exactly what is being compared.
- Introduce a tenant/environment badge near the title to make context obvious.

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch

```text
Header: AI-First DevSecOps            [Tenant: All Tenants ▼]  [Theme: Dark ▼]  [User]

H1: Control Tower
Subtitle: Organization-wide metrics and AI insights

[Sticky Filter Bar]
- Tenant: [All Tenants ▼]
- Time Range: [Last 7 days ▼]
- [ ] Compare Mode

[Main Grid]
+---------------------------------------------+-------------------------+
| Key Metrics (left, 2/3 width)              | AI Insights (right, 1/3)|
|                                             |                         |
|  [KPI cards in 2x3 grid]                    |  [Insight cards stacked]|
|                                             |                         |
|  [Event Timeline chart]                     |                         |
+---------------------------------------------+-------------------------+

[Optional Comparison Info Bar when enabled]
```

### 10.2 Sample KPI Content

Assuming `Last 7 days`, `All Tenants`:

- **Deployment Frequency**
  - Value: `42`
  - Unit: `deploys/week`
  - Trend: `+12% vs last week`
  - Target: `>= 35`
  - Status: `success`

- **Lead Time for Changes**
  - Value: `3.4`
  - Unit: `days`
  - Trend: `-18% vs last week`
  - Target: `<= 5`
  - Status: `success`

- **Mean Time to Recovery (MTTR)**
  - Value: `28`
  - Unit: `minutes`
  - Trend: `-15% vs last week`
  - Status: `success`

- **Change Failure Rate**
  - Value: `4.2`
  - Unit: `%`
  - Trend: `-1.3 pts vs last week`
  - Status: `success`

- **Security Incidents (Critical)**
  - Value: `0`
  - Unit: `open`
  - Trend: `unchanged`
  - Status: `success`

- **Infrastructure Cost**
  - Value: `$124k`
  - Unit: `/month`
  - Trend: `+3% vs last month`
  - Status: `warning`

### 10.3 Sample Event Timeline

Events for the last hour (from `timelineEvents` idea):

- `10:15` – `deployment.completed` (green marker)
- `10:30` – `test.suite_completed` (blue marker)
- `10:45` – `feature.flag_enabled` (purple marker)
- `11:00` – `incident.created` (red marker)

Hovering `incident.created` shows:

- "P1 incident: Checkout failures – 502 errors spiking".

### 10.4 Sample AI Insights Content

Based on the static `insights` array in `Dashboard.tsx`:

1. **Card 1 – QA Pipeline Analysis**
   - Title: `QA Pipeline Analysis`
   - Insight text: `15% flakiness detected in auth tests`
   - Confidence: `92%`
   - Reasoning preview: `Based on 1000 test runs in last 7 days. Root cause: race condition...`
   - Status: `Pending`

   **Details (expanded):**
   - Full reasoning: `Based on 1000 test runs in last 7 days. Root cause: race condition in token validation.`
   - Recommended action: `Increase timeouts and stabilize auth test harness; prioritize fix in next sprint.`
   - Buttons: `[Approve]  [Defer]  [Reject]`.

2. **Card 2 – Deployment Optimization**
   - Title: `Deployment Optimization`
   - Insight text: `Deploy time increased 18% vs last week`
   - Confidence: `87%`
   - Reasoning: `Infrastructure scaling needed. Recommend auto-scaling policy adjustment.`
   - Status: `Pending`

3. **Card 3 – Security Posture**
   - Title: `Security Posture`
   - Insight text: `0 critical vulnerabilities - all systems healthy`
   - Confidence: `99%`
   - Reasoning: `Last scan completed 2h ago. No new issues detected.`
   - Status: `Approved` (no action buttons, show `✓ Approved`).

### 10.5 Comparison Mode Info Bar

When `Compare Mode` is checked:

```text
[Comparison Mode Active]
Viewing metrics compared to the previous period of the same length.
Trends show period-over-period changes relative to the selected time range.
```

This mockup, combined with the behavioral spec above, should give a clear, concrete picture of what the Dashboard must display and how it should behave.
