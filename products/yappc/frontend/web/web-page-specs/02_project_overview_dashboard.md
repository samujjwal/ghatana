# 2. Project Overview – Project Dashboard – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Updated with actual implementation file paths  
> **⚠️ IMPLEMENTATION STATUS:** Contains debug code - needs implementation

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 2.1 Overview](../APP_CREATOR_PAGE_SPECS.md#21-overview--appwworkspaceidpprojectidoverview-overview-renamedtsx)

**Code files:**

- `src/routes/app/project/overview.tsx` – Main overview route (⚠️ contains debug code)
- `src/routes/app/project/_shell.tsx` – Project shell with tabs

---

> **🚨 IMPLEMENTATION BUG:** The current `overview.tsx` (lines 17-27) contains temporary debug code:
> ```tsx
> // TEMPORARY DEBUG CODE - TO BE REMOVED
> return (
>   <Box>
>     <Typography variant="h1">Overview Debug</Typography>
>     <Typography>If you see this, the route is working...</Typography>
>   </Box>
> );
> ```
> This must be replaced with the actual dashboard implementation described below.

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Act as the **single project home page** that answers: "Is this project healthy? What changed recently? What should I do next?".

**Primary goals:**

- **Surface overall health:** Show a compact view of build/deploy/monitoring status so users can assess risk in seconds.
- **Highlight recent activity:** Summarize commits, deployments, incidents, or canvas changes that matter.
- **Provide clear next actions:** Offer obvious shortcuts into deeper tabs (Canvas, Build, Deploy, Monitor, Backlog).
- **Respect project context:** Always make it clear which workspace and project the user is looking at.

**Non-goals:**

- Replace detailed CI/CD views (Build/Deploy tabs) or monitoring consoles (Monitor tab).
- Manage backlog tasks in depth (that’s the Backlog tab).
- Own configuration (that belongs to Settings).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Feature Developer:** Wants quick confirmation that their last change is healthy.
- **Tech Lead / Engineering Manager:** Checks overall status across several projects.
- **SRE / Ops Engineer:** Uses overview as an entry point into problematic projects.
- **Platform Engineer / DevEx:** Verifies that new templates and pipelines behave correctly.

**Key scenarios:**

1. **Morning status check**
   - Tech lead opens the project overview.
   - Sees a **health summary** (e.g., last build status, last deploy environment, open incidents).
   - Clicks through to **Build** if recent builds are failing, or **Monitor** if error rates are elevated.

2. **Post-deployment verification**
   - Developer finishes a deployment.
   - Opens Overview to confirm latest deploy details and basic health indicators.
   - If anything looks off, navigates into **Deploy** or **Monitor** from contextual links.

3. **Onboarding to an existing project**
   - New team member is added to a project.
   - Overview explains what the project is, shows recent activity, and links to design/docs.
   - They follow links to Canvas, Backlog, and Design to understand how the project is structured.

---

## 3. Content & Layout Overview

> Note: As of now, the route is partially implemented and still contains a debug block used during development. The intended layout is described here.

**Planned sections (based on existing code and comments):**

1. **Project header (from project shell):**
   - Project name, ID, avatar.
   - Status bar: last deploy time, environment, basic health icons.

2. **KPI cards row:**
   - Examples:
     - **Build Success Rate** (e.g., last 20 runs).
     - **Deployment Frequency** (e.g., releases/week).
     - **Open Incidents / Alerts**.
     - **Lead Time / Cycle Time** (future).
   - Each card shows a trend or small sparkline where possible.

3. **Recent activity feed:**
   - List of recent events:
     - Builds started/completed.
     - Deployments promoted/rolled back.
     - Incidents opened/resolved.
     - Canvas or configuration changes (future: summarized from versions/history).
   - Each entry links to the relevant tab (Build, Deploy, Monitor, Versions).

4. **Key panels:**
   - **Project details panel:**
     - Description, owner, tags, repository links.
   - **Environment summary:**
     - Small table of environments (Dev/Staging/Prod) with last deploy status.
   - **Quality & risk summary:**
     - High-level metrics: failing tests, open security findings, etc. (to be wired to DevSecOps data).

5. **Quick actions / CTA strip:**
   - Buttons like:
     - **Open Canvas** → Implement tab.
     - **View latest build** → Build tab with latest run selected.
     - **View incidents** → Monitor or DevSecOps Reports.

**Current state vs target:**

- Current route: dominated by a temporary debug block that just proves the file is wired.
- Target: fully-fledged dashboard as described above.

---

## 4. UX Requirements – User-Friendly and Valuable

### 4.1 Clarity & Orientation

- **Explicit project name and workspace:**
  - The header must clearly show: `Workspace / Project Name`.
- **One-line subtitle:**
  - Directly under the page title, e.g.:
  - "High-level health, activity, and suggested next actions for this project."

### 4.2 Information Hierarchy

- **Top row:** high-level KPIs.
- **Middle:** recent activity and environment/quality summary.
- **Bottom:** details and documentation links.
- Avoid overloading the page with dense tables; keep it scannable.

### 4.3 Actionability

- Every section should:
  - Either be **clickable** (navigates to a detailed tab) or
  - Provide clear next steps (e.g., "No builds yet – create your first pipeline").

### 4.4 Empty and error states

- **New project with no history:**
  - Show friendly empty states: "No builds yet", "No deployments yet".
  - Suggest next steps: "Open Canvas", "Configure CI/CD".
- **Data fetch errors:**
  - Show a non-technical error message and a retry button.

---

## 5. Completeness and Real-World Coverage

The overview page should realistically cover:

1. **Build health:**
   - Enough metrics to know whether recent work is stable.
2. **Deploy health:**
   - Visibility into whether changes have reached each environment.
3. **Production health:**
   - High-level signal from the Monitor tab (e.g., open critical alerts).
4. **Change history:**
   - Recent builds/deploys/incidents to understand what changed.
5. **Next steps:**
   - Clear entry points into the rest of the lifecycle.

---

## 6. Modern UI/UX Nuances and Features

- **Card-based layout:**
  - Use responsive cards for KPIs and environment summaries.
- **Trend indicators:**
  - Arrows or color changes to show improving vs degrading metrics.
- **Microcopy:**
  - Plain-language labels like "Build Success Rate" instead of internal metric names.
- **Responsiveness:**
  - Cards should stack on narrow screens; tables should remain legible.

---

## 7. Coherence and Consistency Across the App

- **Header pattern:**
  - Use the same `h1 + subtitle + CTA buttons` pattern as other App Creator pages.
- **Severity and priority semantics:**
  - Use the same severity/priority labels as DevSecOps and Monitor (e.g., Critical/Warning/Info, P0/P1/P2).
- **Navigation links:**
  - Quick action buttons should route to existing tabs, not introduce new entry points.

---

## 8. Links to More Detail & Working Entry Points

**Docs & inventory:**

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#21-overview--appwworkspaceidpprojectidoverview-overview-renamedtsx`

**Code entry points:**

| File | Purpose | Status |
|------|---------|--------|
| [overview.tsx](../src/routes/app/project/overview.tsx) | Overview route | ⚠️ Debug code - needs real implementation |
| [_shell.tsx](../src/routes/app/project/_shell.tsx) | Project shell with tabs | ✅ Working |
| [useProjectHealth.ts](../src/hooks/useProjectHealth.ts) | Health metrics hook | 📋 To be created |
| [useRecentActivity.ts](../src/hooks/useRecentActivity.ts) | Activity feed hook | 📋 To be created |

**Routes:**

| Route | File | Purpose |
|-------|------|---------|
| `/app/w/:workspaceId/p/:projectId/overview` | `overview.tsx` | Project dashboard home |

---

## 9. Open Gaps & Enhancement Plan

### 9.1 Critical: Fix Debug Code (Priority: P0)

**Issue:** The current `overview.tsx` returns debug content instead of the actual dashboard.

**Fix Required:**
1. Remove the debug block (lines 17-27)
2. Implement the dashboard layout per mockup in section 10
3. Wire to data hooks (GraphQL or mock data initially)

### 9.2 Wire to Real Data (Priority: P1)

- Connect KPIs, activity, and environment panels to GraphQL queries.

### 9.3 Integrate DevSecOps Signals (Priority: P2)

- Pull in key risk/quality metrics (e.g., open security findings) from DevSecOps.

### 9.4 Define Canonical KPIs (Priority: P2)

- Standardize which KPIs appear here vs DevSecOps and Build/Deploy.

### 9.5 Add Contextual Quick Actions (Priority: P3)

- Ensure each main lifecycle area has at least one CTA from Overview.

---

## 10. State Management

### Proposed Hooks

```typescript
// Hook for project health metrics
function useProjectHealth(projectId: string) {
  return useQuery({
    queryKey: ['project-health', projectId],
    queryFn: () => fetchProjectHealth(projectId),
  });
}

// Hook for recent activity
function useRecentActivity(projectId: string, limit = 10) {
  return useQuery({
    queryKey: ['project-activity', projectId, limit],
    queryFn: () => fetchRecentActivity(projectId, limit),
  });
}

// Hook for environment status
function useEnvironments(projectId: string) {
  return useQuery({
    queryKey: ['project-environments', projectId],
    queryFn: () => fetchEnvironments(projectId),
  });
}
```

### Data Types

```typescript
interface ProjectHealth {
  buildSuccessRate: number;
  deploymentFrequency: number;
  openIncidents: { critical: number; warning: number; info: number };
  lastDeployTime: Date;
  lastDeployEnvironment: string;
}

interface ActivityEvent {
  id: string;
  type: 'build' | 'deploy' | 'incident' | 'config';
  title: string;
  timestamp: Date;
  link: string;
}

interface EnvironmentStatus {
  name: string;
  lastDeployTime: Date;
  health: 'healthy' | 'warning' | 'critical';
  version: string;
}
```

---

## 11. Testing Contracts

### E2E Tests (Playwright)

```typescript
test.describe('Project Overview', () => {
  test('displays project health KPIs', async ({ page }) => {
    await page.goto('/app/w/workspace-1/p/project-1/overview');
    await expect(page.getByTestId('kpi-build-success')).toBeVisible();
    await expect(page.getByTestId('kpi-deploy-frequency')).toBeVisible();
    await expect(page.getByTestId('kpi-open-incidents')).toBeVisible();
  });

  test('shows recent activity feed', async ({ page }) => {
    await page.goto('/app/w/workspace-1/p/project-1/overview');
    await expect(page.getByTestId('activity-feed')).toBeVisible();
  });

  test('quick actions navigate to correct tabs', async ({ page }) => {
    await page.goto('/app/w/workspace-1/p/project-1/overview');
    await page.getByRole('button', { name: /open canvas/i }).click();
    await expect(page).toHaveURL(/\/canvas$/);
  });
});
```

---

## 12. Mockup / Expected Layout & Content

```text
Project Shell Header (from _shell.tsx)
--------------------------------------------------------------
[Back to Workspace]   [Project Avatar]  Project Name  (ID)
Status: Healthy • Last Deploy: 2h ago to Staging • 0 open incidents

Page Content (Overview)
--------------------------------------------------------------
H1: Project Overview
Subtitle: High-level health, activity, and suggested next actions.

[ KPI Cards Row ]
+------------------+  +--------------------+  +----------------------+
| Build Success    |  | Deployment Frequency|  | Open Incidents       |
| 95% (last 20)    |  | 5 per week         |  | 0 Critical, 2 Low    |
+------------------+  +--------------------+  +----------------------+

[ Recent Activity ]             [ Environment Summary ]
- Build #1234 passed (main)     Dev:   Deployed 1h ago, Healthy
- Deploy to Staging succeeded   Stg:   Deployed 2h ago, Healthy
- Incident #567 resolved        Prod:  Deployed 3d ago, Needs attention

[ Quality & Risk Summary ]
- Tests: 2 failing suites (link to Test)
- Security: 1 medium finding (link to DevSecOps)

[ Quick Actions ]
[ Open Canvas ]  [ View latest build ]  [ View incidents ]
```

This mockup should be treated as the baseline contract for what the Project Overview page should communicate and how it should connect users to the rest of the project lifecycle.
