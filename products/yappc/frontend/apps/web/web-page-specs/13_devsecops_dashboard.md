# 13. DevSecOps Dashboard – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Updated to reflect actual implementation with persona-based dashboards

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 4. DevSecOps Area](../APP_CREATOR_PAGE_SPECS.md#4-devsecops-area--devsecops)

**Code files:**

- `src/routes/devsecops/index.tsx` – Main dashboard route
- `src/routes/devsecops/_layout.tsx` – DevSecOps shell with TopNav
- `src/components/dashboard/MainDevSecOpsDashboard.tsx` – Enhanced dashboard component
- `src/routes/devsecops/components/PersonaSelector.tsx` – Persona switching
- `src/routes/devsecops/components/UnifiedPersonaDashboard.tsx` – Persona-specific views
- `src/routes/devsecops/components/OperationsDashboard.tsx` – Operations view
- `src/routes/devsecops/components/YappcAdminConsole.tsx` – Admin console

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a **persona-aware DevSecOps dashboard** with KPIs, phases, and activity, allowing users to switch between role-specific views while acting as the entry point into DevSecOps workflows.

**Primary goals:**

- Summarize DevSecOps health for a given context
- Support **persona-based views** (Executive, Developer, Security Lead, Operations, Admin)
- Provide navigation into phase boards, persona dashboards, canvas, and reports via TopNav
- Highlight recent DevSecOps activity
- Initialize and manage mock data for demonstration

**Non-goals:**

- Replace project-level Build/Deploy/Monitor views (those live under `/app`)
- Show every detail of each phase/persona (those have dedicated pages)

---

## 2. Users, Personas, and Real-World Scenarios

**Built-in Personas (via PersonaSelector):**

- **Executive:** High-level KPIs, portfolio health, strategic metrics
- **Developer:** Task-focused, code quality, PR status
- **Security Lead:** Vulnerabilities, compliance, risk posture
- **Operations (SRE):** Alerts, SLOs, incident metrics
- **Admin (YAPPC):** System configuration, tenant management

**Key scenarios:**

1. **Posture review**
   - Security lead opens DevSecOps dashboard
   - Switches to Security persona via PersonaSelector
   - Scans KPIs and phase summaries
   - Drills into phases or detailed reports

2. **Executive summary**
   - Director selects Executive persona
   - Views portfolio-level metrics
   - Reviews cross-team status

3. **Release readiness check**
   - Manager reviews key metrics and recent activity to decide whether to proceed with a release

---

## 3. Content & Layout Overview

### 3.1 DevSecOps Layout (`_layout.tsx`)

The DevSecOps dashboard is wrapped in a dedicated layout that provides:

- **TopNav component** (`@yappc/ui/components/DevSecOps`)
  - User info display (currently hardcoded: "John Doe", "Executive")
  - Navigation handler for switching between pages
  - Current page indicator

- **Desktop shell detection**
  - Checks for `__TAURI__` in window for Tauri desktop app
  - Shows online/offline status indicator when running in desktop shell

- **Mock data initialization**
  - On mount, loads mock data via `initializeMockData()`
  - Populates Jotai atoms: `itemsAtom`, `phasesAtom`, `milestonesAtom`, `activityAtom`, `personaDashboardsAtom`
  - Syncs to store: `setStoreItems`, `setStorePhases`, `setStoreMilestones`

- **Error boundary**
  - Catches errors and provides "Back to Dashboard" recovery

### 3.2 Dashboard Index (`index.tsx`)

**Current implementation includes:**

- **PersonaSelector:**
  - Switch between persona types: `'executive' | 'developer' | 'security' | 'operations' | 'admin'`
  
- **View components by persona:**
  - `MainDevSecOpsDashboard` – Default/Legacy view
  - `UnifiedPersonaDashboard` – Developer, Security, Executive views
  - `OperationsDashboard` – Operations/SRE view
  - `YappcAdminConsole` – Admin view

- **DevSecOpsTopBar:**
  - Additional top bar component for dashboard controls

- **WorkflowAutomationWidget:**
  - Widget for workflow automation actions

- **LegacyDevSecOpsView:**
  - Fallback view with:
    - KPI cards from `useKpiStats()` hook
    - Phase overview cards with progress bars
    - Links to phase boards

### 3.3 Navigation via TopNav

Navigation handlers route to:
- `'dashboard'` → `/devsecops` (index)
- `'task-board'` → `/devsecops/task-board`
- `'phases'` → `/devsecops/phase/:phaseId` (first phase)
- `'persona'` → `/devsecops/persona/:slug`
- `'reports'` → `/devsecops/reports`
- `'canvas'` → `/devsecops/canvas`
- `'workflows'` → `/workflows` (top-level)
- `'settings'` → `/devsecops/settings`

- **Recent activity feed:**
  - Stream of latest DevSecOps events (e.g., risks opened/closed, policy changes).
- **CTA to DevSecOps canvas:**
  - Entry point into `/devsecops/canvas` for visual modeling.

---

## 4. State Management Architecture

### 4.1 Jotai Atoms (Client State)

Located in `src/routes/devsecops/atoms.ts`:

```typescript
// Core data atoms
export const itemsAtom = atom<DevSecOpsItem[]>([]);
export const phasesAtom = atom<Phase[]>([]);
export const milestonesAtom = atom<Milestone[]>([]);
export const activityAtom = atom<ActivityEvent[]>([]);
export const personaDashboardsAtom = atom<PersonaDashboard[]>([]);

// UI state atoms
export const selectedPersonaAtom = atom<PersonaType>('developer');
export const viewModeAtom = atom<'dashboard' | 'board' | 'list'>('dashboard');
```

### 4.2 Store Integration (`@yappc/store`)

```typescript
// Dashboard uses shared store for cross-area data
import { setStoreItems, setStorePhases, setStoreMilestones } from '@yappc/store';

// On mock data init, syncs to store
setStoreItems(mockItems);
setStorePhases(mockPhases);
setStoreMilestones(mockMilestones);
```

### 4.3 Hooks

| Hook | Purpose |
|------|---------|
| `useKpiStats()` | Derives KPI metrics from items atom |
| `usePhaseProgress()` | Calculates phase completion percentages |
| `usePersonaDashboard(personaId)` | Gets persona-specific dashboard config |

---

## 5. UX Requirements – User-Friendly and Valuable

- **Plain-language KPIs:**
  - Use labels like "Open risks" or "Compliant pipelines" instead of internal jargon.
- **Persona-aware views:**
  - Each persona sees relevant metrics for their role
  - Switching personas should be instant (client-side state)
- **Scan-ability:**
  - Dashboard must be readable at a glance; most important KPIs at top.
- **Navigation clarity:**
  - Cards and tiles should clearly indicate where they lead (phase, persona, reports, canvas).

---

## 6. Completeness and Real-World Coverage

The dashboard should cover:

1. **Phases** across the delivery lifecycle.
2. **Personas** (Security, SRE, Developer, Executive, Admin).
3. **Recent changes** that might impact risk.
4. **Workflow automation** entry points.

---

## 7. Modern UI/UX Nuances and Features

- **Responsive layout:**
  - KPI cards and tiles should reflow on smaller screens.
  - Uses `@yappc/ui` Stack and Box for flexible layouts.
- **Visual hierarchy:**
  - Use size/color to emphasize the most critical metrics.
  - KPICard component from `@yappc/ui/components/DevSecOps`.
- **Microcopy:**
  - Short, descriptive subtitles under main heading.
- **Desktop shell support:**
  - Tauri detection for desktop-specific features.
  - Online/offline indicator when in desktop mode.

---

## 8. Coherence and Consistency Across the App

- Severity and status semantics must align with project Monitor and any shared severity model.
- Terminology (phases, personas) must match what is used in phase/persona pages and templates.
- TopNav navigation handler must be consistent with sidebar navigation in `/app` area.
- Theme and styling must use `@yappc/ui` components consistently.

---

## 9. Links to More Detail & Working Entry Points

### Implementation Files

| File | Purpose |
|------|---------|
| [index.tsx](../src/routes/devsecops/index.tsx) | Main dashboard route |
| [_layout.tsx](../src/routes/devsecops/_layout.tsx) | DevSecOps shell layout |
| [atoms.ts](../src/routes/devsecops/atoms.ts) | Jotai state atoms |
| [mockData.ts](../src/routes/devsecops/mockData.ts) | Demo data initialization |

### Related Specs

- DevSecOps Phase Board: [14_devsecops_phase_board.md](./14_devsecops_phase_board.md)
- DevSecOps Persona Dashboard: [15_devsecops_persona_dashboard.md](./15_devsecops_persona_dashboard.md)
- DevSecOps Canvas: [16_devsecops_canvas.md](./16_devsecops_canvas.md)
- Workflows: [29_workflows.md](./29_workflows.md)

### Routes

| Route | Component | Purpose |
|-------|-----------|---------|
| `/devsecops` | `index.tsx` | Dashboard with persona selector |
| `/devsecops/phase/:phaseId` | `phase.$phaseId.tsx` | Phase board detail |
| `/devsecops/persona/:slug` | `persona.$slug.tsx` | Persona dashboard |
| `/devsecops/task-board` | `task-board.tsx` | Cross-phase task board |
| `/devsecops/canvas` | `canvas.tsx` | Visual modeling |
| `/devsecops/reports` | `reports.tsx` | Analytics & reports |
| `/devsecops/settings` | `settings.tsx` | DevSecOps settings |

---

## 10. Testing Contracts

### E2E Tests (Playwright)

```typescript
// Dashboard loads with persona selector
test('dashboard shows persona selector', async ({ page }) => {
  await page.goto('/devsecops');
  await expect(page.getByTestId('persona-selector')).toBeVisible();
});

// Persona switching changes view
test('persona switch updates dashboard', async ({ page }) => {
  await page.goto('/devsecops');
  await page.getByRole('button', { name: /operations/i }).click();
  await expect(page.getByTestId('operations-dashboard')).toBeVisible();
});

// TopNav navigation works
test('topnav navigates to task board', async ({ page }) => {
  await page.goto('/devsecops');
  await page.getByRole('link', { name: /task board/i }).click();
  await expect(page).toHaveURL('/devsecops/task-board');
});
```

### Component Tests

```typescript
// KPI cards render with correct data
test('KPI cards display metrics', () => {
  render(<LegacyDevSecOpsView />);
  expect(screen.getByText(/open risks/i)).toBeInTheDocument();
});
```

---

## 9. Open Gaps & Enhancement Plan

1. Link metrics clearly back to specific App Creator projects.
2. Add filters (e.g., by workspace, environment).
3. Surface trend indicators (improving vs worsening posture).

---

## 10. Mockup / Expected Layout & Content

```text
H1: DevSecOps Dashboard
Subtitle: Health and risk overview across phases and personas.

[ Filters: Workspace ▼  |  Environment ▼ (Prod)  |  Time range ▼ (Last 7 days) ]

[ KPI Row ]
-------------------------------------------------------------------------------
|  Open Risks      |  Compliant Pipelines  |  Active Alerts   |  MTTR         |
|  Critical: 3     |  18 / 22 (82%)        |  Critical: 1     |  24 min       |
|  High: 7         |                      |  High: 4         |  (last 30d)   |
-------------------------------------------------------------------------------

[ Phase Overview ]
-------------------------------------------------------------------------------
| Plan        | Code        | Build                | Test           | Run       |
-------------------------------------------------------------------------------
| 3 items     | 6 items     | 5 failing checks     | 2 flaky suites | 1 major   |
| 1 overdue   | 2 blocked   | 1 policy exception   |               | incident  |
| [View]      | [View]      | [View]               | [View]         | [View]    |
-------------------------------------------------------------------------------

[ Personas ]
-------------------------------------------------------------------------------
| Security Lead      | SRE / Ops            | Engineering Manager              |
-------------------------------------------------------------------------------
| Focus: vulns &     | Focus: alerts & SLOs | Focus: delivery + compliance     |
| policies           |                      |                                  |
| [Open persona]     | [Open persona]       | [Open persona]                   |
-------------------------------------------------------------------------------

[ Recent Activity ]
- [Policy] Updated branch protection rules for `main` in workspace "Team Alpha".
- [Risk]   Closed: "Outdated TLS config" on payments-service (High → Resolved).
- [Alert]  New: "Error rate spike" in checkout-service (Critical, Run phase).
```
