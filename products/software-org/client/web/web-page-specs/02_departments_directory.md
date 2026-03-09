# 2. Departments Directory – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 2. `/departments` – Departments Directory](../WEB_PAGE_FEATURE_INVENTORY.md#2-departments--departments-directory)

**Code file:**

- `src/features/departments/DepartmentList.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a clear, searchable overview of all departments/teams, showing their automation level and agent activity, and let users quickly jump into a department’s detailed view.

**Primary goals:**

- Show a concise list of departments with **key stats** (agents, automation, teams).
- Support **search, filter, and sort** to find relevant departments quickly.
- Serve as the **entry point** to Department Detail pages.

**Non-goals:**

- Showing deep performance metrics per department (that’s handled in Department Detail).
- Editing department metadata (name, description) – could be a separate management flow.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Engineering Director / EM:** wants to see which areas are most/least automated.
- **Platform/SRE Lead:** scans where agents are active and where intervention is needed.
- **Security / Compliance:** looks for departments with many active agents but lower automation maturity.

**Key scenarios:**

1. **Finding a specific department**
   - GIVEN: A director wants to inspect the "Payments" team.
   - WHEN: They type `payments` into the search field.
   - THEN: They see matching departments in the grid and click `Payments Engineering` to open its detail.

2. **Prioritizing automation rollouts**
   - GIVEN: Leadership wants to increase automation where it’s lowest.
   - WHEN: They sort by `Automation Level` ascending.
   - THEN: They see departments with the lowest automation percentage at the top and can click into them to plan playbook work.

3. **Monitoring agent deployment**
   - GIVEN: SRE wants to know where AI agents are heavily used.
   - WHEN: They filter by `Active` status and sort by `Activity`/`Active Agents`.
   - THEN: They see which departments are most agent-heavy and can monitor risk.

---

## 3. Content & Layout Overview

Elements from `DepartmentList.tsx`:

- **Header:**
  - Title: `Departments`.
  - Subtitle-style line showing:
    - Count of departments in the filtered list.
    - Total active agents across all departments.

- **Search & filters card:**
  - Search input: `Search departments` (by name/description).
  - Filter dropdown: `Filter by Status` with `All Departments` / `Active Only`.
  - Sort dropdown: `Sort By` with `Name`, `Activity`, `Automation`.

- **Departments grid:**
  - 1-column on small screens, 2-column on larger.
  - Renders `DepartmentCard` for each item in `filteredDepartments`.
  - Each card clickable: on click → navigate to `/departments/<id>`.

- **States:**
  - Loading skeleton grid.
  - Error message card for load failure.
  - Empty state when no departments match the filters.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Search must be forgiving:**
  - Case-insensitive, matches both name and description.
- **Filters & sort intuitive:**
  - Status filter text should be non-technical ("All Departments", "Active Only").
  - Sort options use plain words ("Name", "Activity Level", "Automation Level").
- **Instant feedback:**
  - Changing search text, filter, or sort should immediately update the grid.
- **Clear click target:**
  - Entire card should be clickable, with hover state indicating interactivity.

---

## 5. Completeness and Real-World Coverage

The directory must support:

- Organizations with dozens of departments.
- Quickly scanning which departments:
  - Have many active agents.
  - Have high or low automation.
  - Are currently active/inactive.

Typical questions it should answer:

- "Which teams are using AI agents most heavily?"
- "Which departments have low automation and should be prioritized?"
- "How many departments are currently onboarded?"

---

## 6. Modern UI/UX Nuances and Features

- **Responsive design:**
  - 1-column on mobile, 2-column card grid on desktop.
- **Skeleton states:**
  - During initial load, show placeholder cards instead of an empty screen.
- **Accessible labels:**
  - Inputs have clear labels and placeholders.
- **Soft errors:**
  - Errors show a friendly message and hint to retry or contact support.

---

## 7. Coherence and Consistency Across the App

- Uses the same typography and card patterns as Dashboard KPIs.
- Filters and status terminology match other pages (e.g., `Active`, `Automation Level`).
- Navigating into a department detail should feel like a natural drill-down of what’s visible here.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#2-departments--departments-directory`
- Implementation: `src/features/departments/DepartmentList.tsx`
- Department detail: `src/features/departments/DepartmentDetail.tsx`
- Department data fetching: `src/features/departments/hooks/useDepartments.ts`

---

## 9. Open Gaps & Enhancement Plan

- Add controls for creating/editing/archiving departments.
- Show a quick risk or health indicator per department (e.g., incidents, SLA breaches).
- Introduce filters by domain (e.g., Platform, Product, Data, Security).

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch

```text
H1: Departments
Subtitle: 8 departments • 37 active agents

[Search & Filters Card]
- Search departments: [ payments platform          ]
- Filter by Status:  [ All Departments ▼ ]
- Sort By:           [ Automation Level ▼ ]

[Departments Grid]
+----------------------------------+   +----------------------------------+
| Payments Engineering             |   | Checkout Experience             |
| Description: Payments services   |   | Description: Web & mobile flows |
|                                  |   |                                  |
| Teams: 4                         |   | Teams: 3                         |
| Active Agents: 10                |   | Active Agents: 6                 |
| Automation Level: 72%            |   | Automation Level: 55%            |
| Status: Active                   |   | Status: Active                   |
+----------------------------------+   +----------------------------------+
| ...more department cards...                                              |
+-------------------------------------------------------------------------+
```

### 10.2 Sample Department Data (Derived from useDepartments)

Example department entries rendered as cards:

1. **Payments Engineering**
   - `id`: `dept-payments`
   - `name`: `Payments Engineering`
   - `description`: `Owns payment processing services and billing pipelines.`
   - `teams`: `4`
   - `activeAgents`: `10`
   - `automationLevel`: `72` (percent)
   - `status`: `active`

2. **Checkout Experience**
   - `id`: `dept-checkout`
   - `name`: `Checkout Experience`
   - `description`: `Responsible for checkout UI and order placement.`
   - `teams`: `3`
   - `activeAgents`: `6`
   - `automationLevel`: `55`
   - `status`: `active`

3. **Security Operations**
   - `id`: `dept-security`
   - `name`: `Security Operations`
   - `description`: `Monitors vulnerabilities, threats, and compliance.`
   - `teams`: `2`
   - `activeAgents`: `8`
   - `automationLevel`: `65`
   - `status`: `active`

4. **Data Platform**
   - `id`: `dept-data`
   - `name`: `Data Platform`
   - `description`: `Manages data lakes, warehouses, and pipelines.`
   - `teams`: `3`
   - `activeAgents`: `5`
   - `automationLevel`: `48`
   - `status`: `active`

### 10.3 Empty & Error States

- **Empty search result:**

```text
No departments found.
Try adjusting your search or filters.
```

- **Error loading departments:**

```text
Failed to load departments. Please try again.
[ Retry ]
```

These examples provide concrete guidance for both UI layout and sample content so that the directory remains consistent, useful, and intuitive.
