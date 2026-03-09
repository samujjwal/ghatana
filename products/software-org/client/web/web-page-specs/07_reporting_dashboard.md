# 7. Reporting Dashboard – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 7. `/reports` – Reporting Dashboard](../WEB_PAGE_FEATURE_INVENTORY.md#7-reports--reporting-dashboard)

**Code file:**

- `src/features/reporting/ReportingDashboard.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a hub for running, previewing, scheduling, and exporting pre-built reports that summarize key metrics for different audiences.

**Primary goals:**

- Present a **catalog of report templates** (Executive, Security, Engineering, Operations).
- Show **metrics and trends** for the selected report.
- Allow users to **export** or **schedule** those reports.

**Non-goals:**

- Building fully custom reports from arbitrary data (beyond scope here).
- Real-time monitoring (handled by Dashboard and Real-Time Monitor).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Executives / Directors:** weekly/monthly KPI summaries.
- **Security leads:** compliance and findings reports.
- **Engineering managers:** deployment trends and team performance.

**Scenarios:**

1. **Weekly executive KPI report**
   - GIVEN: VP Engineering wants a snapshot of last week’s KPIs.
   - WHEN: They select `Weekly KPIs` and click `Export PDF`.
   - THEN: They download a neat, shareable report of high-level metrics.

2. **Security findings overview**
   - GIVEN: Security team has a review meeting.
   - WHEN: They select `Security Findings` and quickly view critical/high/medium counts and trends.
   - THEN: They can decide on remediation priorities.

3. **Deployment trends analysis**
   - GIVEN: Platform team is tuning CI/CD.
   - WHEN: They open `Deployment Trends`, see average deploy time, success and rollback rates.
   - THEN: They adjust pipelines accordingly.

---

## 3. Content & Layout Overview

From `ReportingDashboard.tsx`:

- **Header:**
  - Title: `Reporting Dashboard`.
  - Subtitle: `Run, schedule and preview reports for the organization`.

- **Main grid (4 columns on desktop):**
  - Left (1 column): **Report Templates** sidebar.
  - Right (3 columns): **Report Viewer** (header + metrics + chart placeholder).

- **Report Templates sidebar:**
  - List of report buttons with:
    - Title, category, last updated.

- **Report viewer (for selected report):**
  - Header card with:
    - Report title.
    - Category badge.
    - `Updated <time>` badge.
    - Actions: `Export PDF`, `Download CSV`, `Schedule`.
  - Metrics grid: list of metric cards (label, value, trend text).
  - Chart placeholder section.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Template selection:**
  - Selected report is clearly highlighted in the sidebar.
- **Action clarity:**
  - Export and Schedule buttons should be labeled in plain terms and grouped together.
- **Metric readability:**
  - Big numeric values, short labels, clear trend text (e.g., `+12%`, `✓ Clear`).
- **Chart placeholder:**
  - Should clearly convey that a more advanced chart can be plugged in (e.g., `Chart placeholder – integrate charting library`).

---

## 5. Completeness and Real-World Coverage

Example reports from mock data:

- `Weekly KPIs` (Executive).
- `Security Findings` (Security).
- `Deployment Trends` (Engineering).
- `Team Performance` (Operations).

Together they address:

- High-level delivery performance.
- Security posture.
- Deployment pipeline health.
- Team efficiency and on-call performance.

---

## 6. Modern UI/UX Nuances and Features

- Clear card-based metrics layout.
- Responsive design (sidebar collapses or moves above on smaller screens).
- Buttons with consistent style to other CTAs (Primary vs secondary).
- Potential toast feedback when scheduling/exporting (future enhancement).

---

## 7. Coherence and Consistency Across the App

- Metric labels and trends should align with Dashboard KPIs.
- Security report terms (Critical/High/Medium) align with Security Dashboard.
- Deployment metrics match those in Automation Engine/Real-Time Monitor.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#7-reports--reporting-dashboard`
- Implementation: `src/features/reporting/ReportingDashboard.tsx`

Potential future links:

- From a report card → open a more detailed report builder.
- From a metric → jump to live Dashboard/Monitor view.

---

## 9. Open Gaps & Enhancement Plan

- Wire exports and scheduling to a real report service.
- Add time range and tenant filters to the report viewer.
- Surface links to underlying raw data or APIs.

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch

```text
H1: Reporting Dashboard
Subtitle: Run, schedule and preview reports for the organization

+---------------------------+--------------------------------------------+
| Report Templates          | Selected Report                            |
| (left, 1/4 width)        | (right, 3/4 width)                         |
+---------------------------+--------------------------------------------+
```

### 10.2 Sample Report Templates

1. **Weekly KPIs**
   - Category: `Executive`
   - Updated: `2h ago`

2. **Security Findings**
   - Category: `Security`
   - Updated: `1d ago`

3. **Deployment Trends**
   - Category: `Engineering`
   - Updated: `3d ago`

4. **Team Performance**
   - Category: `Operations`
   - Updated: `4h ago`

### 10.3 Example Selected Report – Weekly KPIs

**Header card:**

```text
Weekly KPIs           [Executive]   [Updated 2h ago]

[ Export PDF ]  [ Download CSV ]  [ Schedule ]
```

**Metrics grid:**

1. **Deployments**
   - Value: `42`
   - Trend: `+12%` (green)

2. **Uptime**
   - Value: `99.98%`
   - Trend: `+0.02%` (green)

3. **Incidents**
   - Value: `2`
   - Trend: `-50%` (green, fewer incidents)

4. **Avg Response Time**
   - Value: `245ms`
   - Trend: `-18%` (green)

**Chart placeholder:**

```text
[ Detailed Trends ]
(Chart placeholder – integrate charting library such as recharts/plotly)
```

### 10.4 Example Selected Report – Security Findings

**Metrics:**

1. **Critical Issues**
   - Value: `0`
   - Trend: `✓ Clear`

2. **High Severity**
   - Value: `3`
   - Trend: `-2` (improved)

3. **Medium Severity**
   - Value: `12`
   - Trend: `+1` (slightly worse)

4. **CVE Coverage**
   - Value: `100%`
   - Trend: `Compliant`

This mockup describes not only the layout but also realistic values and trends across the key report types.
