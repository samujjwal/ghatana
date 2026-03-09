# Software Org UI Architecture & Flow Simplification Plan

## 1. Goals

- **Unify concepts** so that the Software Org app clearly expresses:
  - A *virtual software organization* (departments, services, workflows, personas, DevSecOps flows, agents/integrations).
  - *Persona/role flows* that let each user track → act → design → develop → test → operate inside the app.
- **Eliminate redundancy** in pages, routes, and components so there is one obvious way to:
  - Land in the product (persona workspace vs control-tower dashboard).
  - Navigate DevSecOps work (board, work items, SRE/Security flows).
  - Configure org, roles/personas, and integrations.
- **Keep UI primitives shareable** so that future "virtual org" or multi-app experiences can reuse the same components and configuration UIs.

---

## 2. Current Surface Area (High Level)

Grounded in the existing app under `apps/web`:

- **Entry & Persona**
  - `HomePage` (`src/pages/HomePage.tsx`)
    - Persona-driven landing, multi-role composition, widgets, DevSecOps summary, My Stories.
  - `PersonasPage` (`src/pages/PersonasPage.tsx`)
    - Persona preference editor: select roles, configure preferences, audit trail.

- **DevSecOps & Work Execution**
  - `DevSecOpsBoardPage` (`src/pages/DevSecOpsBoardPage.tsx`)
    - Multi-persona DevSecOps board (engineer, lead, sre, security, all) backed by Jotai store + mapped work items.
  - Engineer flow pages (`/work-items/:storyId[...]`):
    - `WorkItemPage` – story detail, acceptance, pipeline strip.
    - `WorkItemPlanPage` – planning/implementation.
    - `WorkItemDevPage` – dev workspace (branch/PR, CI), pipeline strip.
    - `WorkItemReviewPage` – PR/review status.
  - `devsecopsEngineerFlow.ts`
    - `ENGINEER_DEVSECOPS_FLOW`, `LEAD_DEVSECOPS_FLOW`, `SRE_DEVSECOPS_FLOW`, `SECURITY_DEVSECOPS_FLOW` define phase → route mappings.

- **Operate / Observe / Analyze**
  - `Dashboard` (`src/features/dashboard/Dashboard.tsx`)
    - Control-tower KPI dashboard, timeline, AI insights, story monitoring + operate-phase strip.
  - `ReportingDashboard` (`src/features/reporting/ReportingDashboard.tsx`)
    - Reporting, trends, filters; now with AI hint + onboarding + DevSecOps drill-downs.
  - `RealTimeMonitor` (`src/features/monitoring/RealTimeMonitor.tsx`)
    - NOC-style SRE view, AI SRE hint.
  - `ModelCatalog` (`src/features/models/ModelCatalog.tsx`)
    - Model registry, compare/test views, deploy-from-story flow and onboarding.
  - `MLObservatory` (`src/features/ml-observatory/MLObservatory.tsx`)
    - Model performance monitoring.

- **Security & Governance**
  - `SecurityCenter` (`src/features/security/SecurityCenter.tsx`)
    - Security posture/compliance view, AI security hint, links into board and reports.

- **Org & Workflow Inventory**
  - `DepartmentList` / `DepartmentDetail` (`src/features/departments/...`)
  - `WorkflowExplorer` (`src/features/workflows/WorkflowExplorer.tsx`)
  - `OrgKpiDashboard` (`src/features/reporting/OrgKpiDashboard.tsx`) – older org KPI view.

- **Settings & Meta**
  - `SettingsPage` (`src/features/settings/SettingsPage.tsx`)
    - User-level settings (theme, notifications, integrations, account).
  - `HelpCenter`, `DataExport`, `HitlConsole`, `EventSimulator`, `AutomationEngine`, `AIIntelligence` (legacy AI hub to be folded into embedded AI experiences).
  - Route configs: `src/app/routes.ts`, `src/lib/routes.config.ts`.

- **Shared Persona & DevSecOps Components**
  - Persona: `PersonaHero`, `QuickActionsGrid`, `DashboardGrid`, `PluginSlot`, etc.
  - DevSecOps: `DevSecOpsPipelineStrip`, `AiHintBanner`, AI/insight cards, etc.

---

## 3. Redundancies & Confusions (To Clean Up)

### 3.1 Route / Landing Page Confusion

- **Two notions of "Dashboard"**:
  - `HomePage` is the persona workspace but nav config (`routes.config.ts`) maps `/` to a `Dashboard` component path.
  - There is also a `Dashboard` feature page at `/dashboard` (control tower) and an older `OrgKpiDashboard` referenced in `App.tsx`.
- **Two routing systems**:
  - Legacy `App.tsx` with `Routes` + `ROUTES` config assumptions.
  - React Router v7 `routes.ts` with lazy-loaded route objects.
- **Plan:**
  - Make `HomePage` the **canonical landing/workspace**.
  - Treat `Dashboard` as **Control Tower** (Operate/Learn phase) at `/dashboard`.
  - Use React Router v7 `routes.ts` as the single runtime router and remove `App.tsx` plus any old `Routes`-based wiring and placeholder pages.
  - Keep `routes.config.ts` as documentation/navigation metadata that mirrors `routes.ts` (no divergent paths/labels).

### 3.2 Org Configuration Scattered

- Org-related configuration is split across:
  - `Departments`, `Workflows`, `PersonasPage`, `SettingsPage`, maybe hidden org-level APIs.
- There is **no single "Organization Builder"** view that:
  - Shows the software org as a graph (departments, services, workflows, personas, tool integrations).
  - Lets users define/adjust flows, responsibilities, and DevSecOps mappings.

### 3.3 Persona Flows Inconsistent at UI Level

- Engineer/Lead/SRE/Security flows are defined in config but **not surfaced consistently** as a first-class UI primitive:
  - Engineer has clear WorkItem pages and pipeline strips.
  - SRE and Security have hints and some drill-downs, but not a dedicated workspace/flow view.
  - Admin has org-wide responsibilities but no single "Admin workspace" connecting org config + persona config + security.

---

## 4. Target Conceptual Model

### 4.1 Virtual Software Org (Configuration Axis)

Represent the org as structured configuration, surfaced via UI:

- **Entities**
  - Org: name, environments, tenants, default time zone.
  - Departments/Teams: owning services and KPIs.
  - Services & Workflows: topology, dependencies, automation workflows.
  - Personas/Roles: capabilities, quick actions, dashboards (via `personaConfig` + PersonasPage).
  - DevSecOps Flows: per-persona phase sequences and mapped routes.
  - Integrations/Agents: observability, CI, security scanning, ticketing, etc.

- **UI Outcomes**
  - A **graphical Org Builder** where users can view and edit this configuration.
  - Configuration drives:
    - Persona dashboards.
    - DevSecOps board phase labels/filters.
    - Which pages/flows are visible per persona.

### 4.2 Persona / Agent Workspaces (Execution Axis)

For each persona/agent, provide:

- **Dedicated workspace entry**
  - e.g. `/workspace/engineer`, `/workspace/lead`, `/workspace/sre`, `/workspace/security`, `/workspace/admin`.
  - Implemented initially as configured variants of `HomePage` (persona dashboard), not separate duplicates.

- **End-to-end flow** surfaced consistently:
  - Pipeline strip that reflects that persona’s DevSecOps flow.
  - Story/incident queues, actions, AI hints, and onboarding.
  - One-click drill-downs into DevSecOps board, reports, monitoring, models, automation, etc.

### 4.3 Operate & Analyze (Cross-Cutting Axis)

- `Dashboard` (Control Tower) + `ReportingDashboard` + `RealTimeMonitor` + `MLObservatory` + `ModelCatalog` become the **Operate & Analyze** surfaces.
- These are
  - Linked into persona flows as specific phases.
  - Driven by the org configuration (which services/metrics show up, per persona).

---

## 5. Future Information Architecture (IA)

### 5.1 Primary Navigation (after cleanup)

Proposed top-level navigation (labels TBD with product):

- **Home** – Persona workspace (`HomePage`), multi-role aware.
- **DevSecOps** – Board + work items (`/devsecops/board`, `/work-items/...`).
- **Operate** – Control tower dashboard (`/dashboard`) + quick links to Real-Time Monitor & ML Observatory.
- **Reports** – Reporting dashboard (`/reports`).
- **Security** – Security center (`/security`) + compliance reports.
- **Models** – Model catalog & observatory (`/models`, `/ml-observatory`).
- **Org Builder** – New org configuration/graph view (`/org` or `/org/builder`).
- **Settings** – User + org settings (`/settings`), linking to Personas preferences.
- **Help** – Help, docs, and tours (`/help`).

### 5.2 Secondary Navigation & Contextual Links

- Maintain contextual links inside pages using shared components:
  - `DevSecOpsPipelineStrip` for phase navigation.
  - `AiHintBanner` + onboarding cards for guided flows.
  - `GlobalFilterBar` as a cross-cutting filter context (time range, tenant, environment) for Operate/Analyze surfaces and, selectively, DevSecOps.
  - Breadcrumbs / tabs within complex hubs like Settings and Org Builder.

---

## 6. Org Configuration & Graphical Org Builder Plan

### 6.1 New Shared Types & Components

- **Types (shared)**
  - Define reusable TS interfaces in a shareable location (e.g. `libs/org-config` or `src/shared/types/org`):
    - `OrgConfig`, `DepartmentConfig`, `ServiceConfig`, `WorkflowConfig`, `PersonaBinding`, `IntegrationConfig`, `DevSecOpsFlowConfig` (consolidated shared type that existing `*_DEVSECOPS_FLOW` configs are migrated to, so Org Builder, DevSecOps board, and routing helpers all read/write the same data).

- **Org Graph Canvas (shared component)**
  - New component, in a shared lib (e.g. `src/shared/components/org/OrgGraphCanvas.tsx`):
    - Visualizes departments, services, workflows, integrations/agents, and edges (dependency, ownership, flow).
    - Initial implementation can be static or use basic layout (columns by department, rows by environment).
    - Nodes clickable → open configuration drawers or detail routes.

- **OrgConfigSidebar / Detail Panels**
  - Shared components for editing entity-level config:
    - Department details, team owners.
    - Service metadata (tier, SLOs, risk level).
    - Mappings to workflows and DevSecOps phases.

### 6.2 New Page: Org Builder

- **Route**: `/org` or `/org/builder` (secondary route; surfaced prominently for admins/leads).
- **Page component** (under `src/features/org/OrgBuilderPage.tsx`):
  - Left: filters (environment, department, persona focus).
  - Center: `OrgGraphCanvas`.
  - Right: contextual inspector for selected node, including:
    - Links to departments, workflows, personas, and settings.
    - Quick actions to open relevant pages (DevSecOps board filtered, ReportingDashboard, RealTimeMonitor, etc.).
    - For integration/agent nodes: show which services/departments and personas they support, with links into `SettingsPage` to manage credentials/config.
  - **Config source**:
  - Initially: mock/stub data (similar to DevSecOps mock store), with clear TODOs to wire to backend/virtual-org APIs.

### 6.3 Integration with Existing Pages

- From `Departments`, `Workflows`, `PersonasPage`, and `SettingsPage`, add **"Open in Org Builder"** links to maintain a single graphical source of truth.
- From `HomePage` for admin/lead personas, surface a card that deep-links into Org Builder focusing on their departments or services.

---

## 7. Persona / Agent Workspace Plan

### 7.1 Canonical Persona Workspaces

- Keep `HomePage` as the **unified workspace shell**, but allow **persona-specific views**:
  - URL model: `/workspace/:personaId` → same component with explicit persona context (`engineer`, `lead`, `sre`, `security`, `admin`).
  - When no explicit workspace is requested, default to the multi-role composition already implemented.

- Each workspace preset configures:
  - Quick actions (from `personaConfig` + org config).
  - Metrics widgets.
  - DevSecOps summary card filters (persona/status).
  - AI/onboarding copy.

### 7.2 Surfacing DevSecOps Flows Per Persona

- Use existing `ENGINEER_DEVSECOPS_FLOW`, `LEAD_DEVSECOPS_FLOW`, `SRE_DEVSECOPS_FLOW`, `SECURITY_DEVSECOPS_FLOW` as **single source of truth**.
- Plan shared helpers/components (in shared libs):
  - `PersonaFlowStrip` – builds on `DevSecOpsPipelineStrip` but pulls steps, labels, and routes from a `PersonaFlow` config.
  - `PersonaFlowSidebar` – per-persona checklist of steps with links to underlying pages (work items, reports, monitor, models, dashboard, security, etc.).

- Example flows:
  - **Engineer**
    - Start: Engineer workspace (`/workspace/engineer` or HomePage with engineer role).
    - Phases: intake → plan → build/verify → review → staging → deploy → operate → learn.
    - Surfaces: My Stories, WorkItem pages, Automation, Reports (staging), ModelCatalog (deploy), Dashboard (operate).
  - **Lead**
    - Start: Lead workspace.
    - Focus: portfolio view of DevSecOps board (`persona=lead`), ReportingDashboard saved views, approvals.
  - **SRE**
    - Start: SRE workspace.
    - Phases (from `SRE_DEVSECOPS_FLOW`): intake/triage → plan remediation → verify → deploy → operate → learn.
    - Surfaces: RealTimeMonitor, DevSecOps board `persona=sre`, ModelCatalog (deploy fixes), Dashboard & MLObservatory (operate/learn) with incident retros.
  - **Security**
    - Start: Security workspace.
    - Phases: posture review → plan controls → verify scans → operate securely → learn from retros.
    - Surfaces: SecurityCenter, ReportingDashboard (`type=compliance`), DevSecOps board `persona=security`.
  - **Admin**
    - Start: Admin workspace.
    - Surfaces: Org Builder, PersonasPage, SettingsPage, SecurityCenter.
  - For SRE and Security, “incidents” are currently modeled as alerts/events plus linked work items, not a separate Incident entity; RealTimeMonitor, Dashboard, and the DevSecOps board together provide the incident view.

### 7.3 Internal-Only Execution Experience

- Ensure every flow step keeps the user inside Software Org:
  - Replace raw external links (where possible) with internal work items, incidents, and model/test pages.
  - Use shared CTA patterns (`AiHintBanner`, inline cards) for all cross-surface navigation.

---

## 8. Reducing Redundant Pages & Code

### 8.1 Cleanup Targets

- **Legacy routing**
  - Use React Router v7 `routes.ts` as the only runtime entry point.
  - Remove `App.tsx` and any old `Routes`-based routing shell and placeholder pages.
  - Keep `routes.config.ts` as documentation/navigation metadata that mirrors `routes.ts` (paths/labels must stay in sync).

- **OrgKpiDashboard**
  - Fold any unique metrics/visuals into `Dashboard` (Control Tower) if still valuable, then remove `OrgKpiDashboard` and its routes/imports.

- **AIIntelligence hub (`/ai`)**
  - Remove the dedicated AIIntelligence page/route and rely on embedded AI hints, banners, and insights across dashboards, reports, monitoring, models, and security surfaces.

- **Duped AI components and hints**
  - We have already unified banners to `AiHintBanner`; ensure any remaining bespoke hint banners are migrated.

### 8.2 Shared Component Library Hygiene

- Audit `src/shared/components` for overlapping cards/banners and consolidate where reasonable (without breaking existing docs/tests).
- For new Org and Persona Flow components, keep them in shared libs from day one so other apps can embed the same experiences.

---

## 9. Implementation Phasing

### Phase 0 – Discovery & Alignment

- Document runtime entry (React Router v7 + `routes.ts`) and update docs to point to it.
- Decide naming for:
  - Persona workspace vs Control Tower (`Home` vs `Dashboard`).
  - Org Builder page and icon.

### Phase 1 – Routing & Navigation Cleanup

- Align `routes.ts`, `routes.config.ts`, and layout sidebar labels:
  - `/` → `HomePage` (workspace) labeled "Home" or "Workspace".
  - `/dashboard` → `Dashboard` labeled "Control Tower" or "Operate".
- Remove dead routes and placeholder `div>Coming Soon</div>` pages where real implementations exist.
- Remove deprecated components (OrgKpiDashboard, legacy App shell) once the new Dashboard/DevSecOps/Org Builder flows are wired up.

**Status (2025-11-28)**  
- [x] Routes and sidebar labels aligned (`src/app/Router.tsx`, `src/lib/routes.config.ts`, `src/app/Layout.tsx`).
- [x] Legacy `App.tsx` shell and placeholder routes removed.
- [x] `OrgKpiDashboard` and `AiIntelligence` removed; control tower consolidated at `/dashboard`.

### Phase 2 – Org Builder (Configuration Axis)

- Introduce shared org-config types.
- Build `OrgGraphCanvas` + detail panels as shared components with mocked data.
- Implement `OrgBuilderPage` at `/org` using these components.
- Add "Open in Org Builder" links from Departments, Workflows, Personas, and Settings.

**Status (2025-11-28)**  
- [x] Shared org-config types created in `src/shared/types/org.ts` and exported via `src/shared/types/index.ts`.
- [x] `OrgGraphCanvas` and `OrgNodeInspector` implemented in `src/shared/components/org/` with mock data in `src/features/org/mockOrgData.ts`.
- [x] `OrgBuilderPage` implemented at `/org` (`src/features/org/OrgBuilderPage.tsx`) with sidebar entry and route metadata in `routes.config.ts`.
- [x] "Open in Org Builder" links added to DepartmentList, WorkflowExplorer, PersonasPage, and SettingsPage with `?type=` query param support.

### Phase 3 – Persona Workspaces & Flows

- Add optional persona-aware routes (e.g. `/workspace/:personaId`) mapped to `HomePage` with explicit persona context.
- Build `PersonaFlowStrip` and `PersonaFlowSidebar` in shared components.
- Wire Engineer, Lead, SRE, Security, Admin workspaces to their respective flows using existing DevSecOps flow configs.

**Status (2025-11-28)**  
- [x] `/workspace/:personaId?` route added in `src/app/Router.tsx` pointing to `HomePage` (persona dashboard shell).
- [x] `PersonaFlowStrip` implemented in `src/shared/components/PersonaFlowStrip.tsx` and integrated into `HomePage` DevSecOps summary.
- [x] Persona workspace card/grid primitives created in `src/shared/components/PersonaWorkspaceCard.tsx` and embedded into `HomePage` for multi-role users.
- [x] `PersonaFlowSidebar` implemented in `src/shared/components/PersonaFlowSidebar.tsx` with persona-specific workflow checklists.
- [x] Workspace route now uses `personaId` param to override persona context via `personaOverrideAtom` in `src/state/jotai/atoms.ts`.
- [x] Per-persona workspace presets implemented in `src/config/workspacePresets.ts` with onboarding, AI guidance, metrics, workflow steps, and tips for all 6 personas (Engineer, Lead, SRE, Security, Admin, Viewer).
- [x] `WorkspaceOnboardingBanner`, `WorkspaceTipCard`, and `WorkspaceMetricHighlights` components created in `src/shared/components/WorkspaceOnboardingBanner.tsx` and integrated into `HomePage`.

### Phase 4 – Deep Integration & Refinement

- Make Org Builder read/write from real virtual-org APIs when available.
- Refine drill-downs across dashboards, reports, monitoring, security, and DevSecOps board.
- Harden AI hints, onboarding, and guidance around the new flows.
- Add smoke tests and basic persona-flow tests for all primary nav destinations and each persona workspace (Engineer, Lead, SRE, Security, Admin).

**Status (2025-11-28)**  
- [x] Frontend infrastructure for backend integration complete:
  - API client functions in `src/services/api/virtualOrgApi.ts` for all org entities (departments, services, workflows, integrations, persona bindings)
  - React Query hooks in `src/hooks/useVirtualOrg.ts` with automatic mock fallback when backend unavailable
  - OrgBuilderPage updated to use hooks with loading states
- [x] Cross-surface drill-downs implemented: GlobalFilterBar added to Dashboard, ReportingDashboard, RealTimeMonitor with persona/department/environment filters.
- [x] ContextualHints component created and integrated into ReportingDashboard, RealTimeMonitor, SecurityCenter for consistent cross-page navigation.
- [x] Automated smoke tests added in `src/__tests__/navigation.smoke.test.tsx` covering all primary nav destinations and persona workspaces.
- [ ] Backend virtual-org APIs not yet implemented (requires backend work)

---

## 10. Success Criteria

- **Clarity**: New users understand where to:
  - Configure the software org (Org Builder).
  - Work in their persona workspace.
  - Monitor and operate the system.
- **Cohesion**: DevSecOps flows for all personas are visible and navigable using consistent UI primitives.
- **Maintainability**: Routes, navigation config, and persona/org config live in a small number of authoritative modules with minimal duplication.
- **Shareability**: Org config and persona flow components are designed as shared primitives suitable for reuse in other apps and a future "virtual org" experience.
