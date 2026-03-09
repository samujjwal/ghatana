# 4. Workflow Explorer – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 4. `/workflows` – Workflow Explorer](../WEB_PAGE_FEATURE_INVENTORY.md#4-workflows--workflow-explorer)

**Code file:**

- `src/features/workflows/WorkflowExplorer.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a clear overview of all automation workflows/pipelines, their health and performance, and let users select, inspect, and run them.

**Primary goals:**

- Show **active pipelines** with key stats (status, last run, duration, success rate, runs).
- Allow **filtering by health status** (healthy, running, degraded).
- Provide a **detail panel** with more information and quick actions (Run, Edit).

**Non-goals:**

- Editing individual workflow steps visually (that belongs to Automation Engine / builder).
- Full execution history (only high-level stats here).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **SRE / DevOps engineer:** monitors release workflows.
- **Release engineer:** runs and inspects deployment pipelines.
- **Platform engineer:** tunes and manages orchestrations.

**Scenarios:**

1. **Checking workflow health**
   - GIVEN: There were failed overnight runs.
   - WHEN: SRE filters to `Degraded` pipelines.
   - THEN: They see which workflows are degraded and drill into details.

2. **Triggering a production deploy**
   - GIVEN: A change is approved and ready.
   - WHEN: Release engineer selects `Production Deploy` and clicks `Run Now`.
   - THEN: A new execution is triggered (once wired) and details are visible in the side panel.

3. **Comparing pipelines within a department**
   - GIVEN: The Security team wants to know if their `Security Scan` workflow is slower than others.
   - WHEN: They filter or visually scan for `Security` department entries and compare duration/success rate.
   - THEN: They see where tuning is needed.

---

## 3. Content & Layout Overview

From `WorkflowExplorer.tsx`:

- **Header:**
  - Title: `Workflow Explorer`.
  - Subtitle: `Orchestrations, pipelines and recent activity`.

- **Stats bar (4 cards):**
  - Active Pipelines – count of all pipelines.
  - Healthy – number with `status === 'healthy'`.
  - Running – number with `status === 'running'`.
  - Avg Success Rate – static or computed.

- **Filters row:**
  - Status filter buttons: `All`, `Healthy`, `Running`, `Degraded`.

- **Main grid:**
  - Left (2/3): pipeline list.
  - Right (1/3): details for selected pipeline.

- **Pipeline list items:**
  - Name, department.
  - Status badge.
  - Mini-metrics: Last Run, Duration, Success %, Runs.

- **Detail panel:**
  - Selected pipeline name and department.
  - Status, success rate, total runs, average duration.
  - Buttons: `Run Now`, `Edit`.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clear status signals:**
  - Color-coded status badges: healthy (green), running (blue), degraded (yellow/red).
- **Discoverability:**
  - Clicking a pipeline row must clearly highlight it and update the detail panel.
- **Safe actions:**
  - `Run Now` and `Edit` buttons should be visually distinct and not too easy to mis-click.
- **Responsive behavior:**
  - On smaller screens, list and detail may stack vertically.

---

## 5. Completeness and Real-World Coverage

The explorer should answer:

- Which workflows do we have and what do they do?
- Which ones are healthy vs degraded?
- How often do they run, and how successful are they overall?

It lays the groundwork for deeper integration with execution history and Automation Engine while still being useful in isolation.

---

## 6. Modern UI/UX Nuances and Features

- Status filter buttons behave like tabs with clear active state.
- Pipeline cards highlight on hover and selection.
- Detail panel shows skeleton/loading states if/when wired to real data.
- Keyboard navigation: users can tab into list and move selection with arrow keys (future enhancement).

---

## 7. Coherence and Consistency Across the App

- Status and health concepts must align with Automation Engine and Real-Time Monitor.
- Departments referenced here should match Departments pages.
- Uses the same design language (cards, status badges) as Dashboard/Reports.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#4-workflows--workflow-explorer`
- Implementation: `src/features/workflows/WorkflowExplorer.tsx`

Future navigation:

- `Edit` → open workflow in Automation Engine/builder (`/automation-engine` with workflow ID).
- `Run Now` → open execution monitor panel or route.

---

## 9. Open Gaps & Enhancement Plan

- Add search by name/department.
- Integrate with real workflow API (`/api/v1/workflows`).
- Show per-workflow success trends over time.
- Link to execution history for each pipeline.

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch

```text
H1: Workflow Explorer
Subtitle: Orchestrations, pipelines and recent activity

[Stats Bar]
- Active Pipelines: 4
- Healthy: 2
- Running: 1
- Avg Success Rate: 94.2%

[Filters]
[ All ]  [ Healthy ]  [ Running ]  [ Degraded ]

[Main Grid]
+--------------------------------------------+---------------------------+
| Pipeline List (left, 2/3)                 | Pipeline Details (right)  |
+--------------------------------------------+---------------------------+
```

### 10.2 Sample Pipelines (From Mock Data)

1. **wf-001 – Production Deploy**
   - Department: `DevOps`
   - Status: `healthy`
   - Last Run: `3m ago`
   - Duration: `2m 45s`
   - Success Rate: `98.5%`
   - Runs: `245`

2. **wf-002 – QA Regression Tests**
   - Department: `QA`
   - Status: `running`
   - Last Run: `now`
   - Duration: `1h 12m`
   - Success Rate: `92.1%`
   - Runs: `183`

3. **wf-003 – Security Scan**
   - Department: `Security`
   - Status: `degraded`
   - Last Run: `12m ago`
   - Duration: `8m 30s`
   - Success Rate: `87.3%`
   - Runs: `156`

4. **wf-004 – Nightly Build & Release**
   - Department: `Engineering`
   - Status: `healthy`
   - Last Run: `1h ago`
   - Duration: `45m 22s`
   - Success Rate: `99.1%`
   - Runs: `412`

### 10.3 Sample List & Detail Rendering

**List row (for wf-001):**

```text
Production Deploy                         [HEALTHY]
DevOps
Last Run: 3m ago   Duration: 2m 45s   Success: 98.5%   Runs: 245
```

**Detail panel (for selected wf-003 – Security Scan):**

```text
Security Scan
Department: Security

Status: [DEGRADED]
Success Rate: 87.3%
Total Runs: 156
Avg Duration: 8m 30s

[ Run Now ]  [ Edit ]
```

This mockup gives concrete layout and content expectations while staying aligned with the existing mock data.
