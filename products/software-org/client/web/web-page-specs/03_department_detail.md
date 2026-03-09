# 3. Department Detail – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 3. `/departments/:id` – Department Detail](../WEB_PAGE_FEATURE_INVENTORY.md#3-departmentsid--department-detail)

**Code file:**

- `src/features/departments/DepartmentDetail.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a focused, comprehensive view of a single department’s health, automation, and configuration, with clear paths to adjust automation and playbooks.

**Primary goals:**

- Surface **key metrics** (deployments, lead time, MTTR, CFR, agents, teams) for this department.
- Clearly show **automation status** and provide controls to enable/disable automation.
- Prepare space for **agents, workflows, and playbooks** tabs.

**Non-goals:**

- Editing core department identity (name, ID) – belongs to an admin flow.
- Managing individual agents in depth (could be its own page later).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Engineering Manager for this department** (e.g., Payments EM).
- **Platform/SRE** responsible for enabling automation.
- **Security Lead** monitoring automation risk per department.

**Scenarios:**

1. **Checking department performance**
   - GIVEN: Payments team had a rough week.
   - WHEN: EM opens `Payments Engineering` department detail.
   - THEN: They see KPIs like Deployment Frequency, Lead Time, MTTR, CFR and automation status in one glance.

2. **Deciding to enable automation**
   - GIVEN: Department is currently manually handling incidents.
   - WHEN: SRE toggles `Automation Status` to enabled.
   - THEN: They understand from text that automated workflows will now run for this department, and can open the Playbook drawer.

3. **Planning playbooks**
   - GIVEN: Team wants to add auto-remediation runbooks.
   - WHEN: They navigate to the `Playbooks` tab and click `Add Playbook`.
   - THEN: They open a Playbook editor/drawer to define actions.

---

## 3. Content & Layout Overview

From `DepartmentDetail.tsx`:

- **Header section:**
  - Back link: `← Back to Departments` (navigates to `/departments`).
  - Title: `<department.name>`.
  - Description: `<department.description>`.
  - Primary CTA: `Configure Playbook` button (opens `PlaybookDrawer`).

- **Quick stats strip (4 columns):**
  - `Teams`
  - `Active Agents`
  - `Automation` (percentage)
  - `Status` (Active)

- **Tabs:**
  - `Overview`
  - `Agents`
  - `Workflows`
  - `Playbooks`

- **Overview tab content:**
  - `KpiGrid` with `KpiCard` for:
    - Deployment Frequency (`/week`)
    - Lead Time
    - Mean Time to Recovery
    - Change Failure Rate
    - Team Size
    - Active Agents
  - Automation Status card with toggle.

- **Other tabs:**
  - `Agents`: placeholder text about `X agents active`.
  - `Workflows`: placeholder text about workflow explorer.
  - `Playbooks`: placeholder + `Add Playbook` button.

- **Playbook Drawer:**
  - Appears when `showPlaybookDrawer` is true.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Clear hierarchy:**
  - Department name is the largest text, description just below, KPIs and controls further down.
- **Back navigation:**
  - Back link is easy to find and returns to the same filter state on `/departments` if possible.
- **Automation toggle copy:**
  - Explain what enabling automation does in simple terms (no jargon).
- **Tabs must feel real, not "coming soon":**
  - Even if content is stubbed initially, copy should explain what will eventually appear.

---

## 5. Completeness and Real-World Coverage

This page should be sufficient for a manager to:

- Check whether their department is performing well vs expectations.
- Know how many agents and teams they have.
- See whether automation is on and what that implies.
- Start configuring or reviewing playbooks.

Over time, the Agents/Workflows/Playbooks tabs will show:

- Lists of agents, their status, and coverage.
- Workflows bound to this department and their health.
- List of automation playbooks and their stages.

---

## 6. Modern UI/UX Nuances and Features

- Tab switching without full page reload.
- Smooth animations when opening/closing Playbook Drawer.
- Responsiveness: stats and KPIs wrap appropriately on small screens.
- Semantic headings for sections (e.g., "Key Performance Indicators", "Automation Status").

---

## 7. Coherence and Consistency Across the App

- Uses `KpiCard`/`KpiGrid` patterns consistent with Dashboard.
- Follows the page header/subtitle conventions from the shell.
- Automation concepts here must align with Automation Engine terminology.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#3-departmentsid--department-detail`
- Department detail implementation: `src/features/departments/DepartmentDetail.tsx`
- Playbook drawer: `src/features/departments/components/PlaybookDrawer.tsx`

---

## 9. Open Gaps & Enhancement Plan

- Fill in `Agents` tab with a real agent list (status, last activity, coverage).
- Fill in `Workflows` tab with workflows bound to this department.
- Fill in `Playbooks` tab with existing playbooks and states.
- Wire automation toggle and playbook actions to real backend state.

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch

```text
← Back to Departments

H1: Payments Engineering
Subtitle: Owns payment processing services and billing pipelines.

[Configure Playbook]

[Quick Stats - 4 columns]
- Teams: 4
- Active Agents: 10
- Automation: 72%
- Status: Active

[Tabs]
[ Overview ]  [ Agents ]  [ Workflows ]  [ Playbooks ]

[Overview Tab Content]
- Section: Key Performance Indicators (2x3 grid of KPI cards)
- Section: Automation Status (toggle card)
```

### 10.2 Sample KPI Cards (Overview Tab)

Assume `Payments Engineering` has the following KPIs:

1. **Deployment Frequency**
   - Value: `14`
   - Unit: `/week`
   - Trend: `+3 vs last week`
   - Status: `success`

2. **Lead Time**
   - Value: `2.8`
   - Unit: `days`
   - Trend: `-0.7 days`
   - Status: `success`

3. **Mean Time to Recovery**
   - Value: `35`
   - Unit: `minutes`
   - Trend: `-10 minutes`
   - Status: `success`

4. **Change Failure Rate**
   - Value: `5.1`
   - Unit: `%`
   - Trend: `-1.2 pts`
   - Status: `success`

5. **Team Size**
   - Value: `4`
   - Unit: `teams`
   - Status: `neutral`

6. **Active Agents**
   - Value: `10`
   - Unit: `running`
   - Status: `neutral`

### 10.3 Automation Status Card Content

Copy example:

```text
Automation Status

[✓] Enable automated workflows for this department

When enabled, approved playbooks can automatically remediate incidents,
handle routine tasks, and trigger alerts without manual intervention.
```

UI behavior:

- Checkbox toggles an internal boolean state `automationEnabled`.
- When checked, supporting text changes to: `Automated workflows are enabled`.

### 10.4 Other Tabs (Conceptual Mockup)

- **Agents tab:**

```text
Agents

10 agents active in this department.

[Agent list coming soon]
This will show each agent, its purpose, and last activity.
```

- **Workflows tab:**

```text
Workflows

[Workflow explorer coming soon]
This will show workflows bound to this department and their status.
```

- **Playbooks tab:**

```text
Playbooks

No playbooks configured yet.

[ Add Playbook ]
```

Clicking `Add Playbook` or `Configure Playbook` opens the `PlaybookDrawer` with department name pre-filled.

These mockups provide concrete guidance and sample values so the Department Detail page can be implemented in a way that is realistic, complete, and easy to understand for non-experts.
