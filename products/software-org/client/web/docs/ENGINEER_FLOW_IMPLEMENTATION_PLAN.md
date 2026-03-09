# Software Engineer Persona Flow – Implementation Plan

> Draft implementation plan for wiring a real-world Software Engineer flow into the Software Org web app.

## Implementation Status

| Phase | Description | Status |
|-------|-------------|--------|
| Phase 1 | Engineer Dashboard & My Stories | ✅ Complete |
| Phase 2 | Work Item Detail & Planning | ✅ Complete |
| Phase 3 | CI Pipelines & Review Integration | ✅ Complete |
| Phase 4 | Staging Validation & Deploy | ✅ Complete |
| Phase 5 | Production Monitoring & Story Closure | ✅ Complete |
| Enhancement | Work Items List Page (`/work-items`) | ✅ Complete |

### Implemented Routes

- `/persona-dashboard` → Engineer persona dashboard with `MyStoriesCard`
- `/work-items` → Full work items list with filtering and sorting
- `/work-items/:storyId` → Work item detail page
- `/work-items/:storyId/plan` → Implementation planning page
- `/work-items/:storyId/review` → Code review status page
- `/automation?storyId=:id` → CI pipelines filtered by story
- `/reports?view=staging&storyId=:id` → Staging validation
- `/models?action=deploy&storyId=:id` → Production deploy
- `/dashboard?storyId=:id` → Production monitoring & story closure

---

## 1. Goals

- Provide a realistic, end-to-end flow for a Software Engineer:
  - Intake a story/epic.
  - Plan and implement code changes.
  - Run and fix CI.
  - Get review and merge.
  - Validate in staging.
  - Deploy to production.
  - Monitor impact and close the story.
- Map each step to concrete routes, components, and CTAs in the Software Org web app.
- Keep the flow simple, linear, and discoverable from the Engineer persona dashboard.

## 2. High-level Flow (User Perspective)

1. Select Engineer persona.
2. See "My Stories" on Engineer persona dashboard and pick a story.
3. Open story detail to understand requirements and context.
4. Plan implementation (affected services, design notes, flags/rollout).
5. Implement code in external repo/IDE, anchored from the plan page.
6. View CI pipelines for this story; fix failures until green.
7. Review and merge PR; confirm approvals.
8. Validate behavior in staging.
9. Deploy to production.
10. Monitor production impact and close the story.

The sections below map this flow to the current codebase and define the required changes.

---

## 3. Current Codebase Reference

### 3.1 Key Existing Files

- `apps/web/src/app/Router.tsx`
  - Defines routes like `/`, `/dashboard`, `/personas/:workspaceId?`, `/reports`, `/models`, etc.
  - Uses `App` layout and lazy-loaded feature pages.
- `apps/web/src/pages/HomePage.tsx`
  - Persona-aware landing page.
  - For authenticated users, renders persona dashboard (Hero, QuickActions, DashboardGrid, etc.).
  - For anonymous users, renders `GenericLandingPage` hero (Software Organization Platform / AI-First DevSecOps Control Center).
- `apps/web/src/shared/components/TopNavigation.tsx`
  - Top nav bar. Contains `PersonaSelector` and handles persona changes.
  - On persona change, updates Jotai atoms and navigates to `/persona-dashboard`.
- `apps/web/src/shared/components/PersonaSelector.tsx`
  - Persona selection UI. Now built from `PERSONA_CONFIGS` (admin, lead, engineer, viewer).
- `apps/web/src/config/personaConfig.ts`
  - Defines v1 persona configs for `admin`, `lead`, `engineer`, `viewer`.
  - For engineer: quick actions like `create-workflow`, `run-simulation`, `monitor-events`, `deploy-model`, `view-executions`, `debug-failures`.
- `apps/web/src/hooks/usePersonaComposition.ts`
  - Uses v2 persona composition engine; merges multiple roles.
- `apps/web/src/features/dashboard/Dashboard.tsx`
  - "Control Tower" dashboard at `/dashboard` (Org-wide KPIs, AI insights).
- `apps/web/src/features/reporting/ReportingDashboard.tsx`
  - Reporting view at `/reports`.

### 3.2 Existing Routes (Relevant)

From `Router.tsx`:

- `/` → `LandingPage` (GenericLandingPage from HomePage)
- `/persona-dashboard` → `PersonaDashboardPage` (HomePage default export; persona dashboard)
- `/dashboard` → `Dashboard` (Control Tower)
- `/reports` → `ReportingDashboard`
- `/models` → `ModelCatalog` (today: model catalog; can host deploy UI)
- `/automation` → `AutomationEngine` (automation / timeline / executions)
- `/workflows` → `WorkflowExplorer` (workflow views)

We will extend this with story/work-item specific routes.

---

## 4. Detailed Flow to Routes & Components

### Step 0 – Select Engineer Persona

**User goal:** Enter the product as a Software Engineer.

- **Entry points:** Any page with `TopNavigation`.
- **Components:**
  - `TopNavigation` + `PersonaSelector`
  - Jotai atoms: `userRoleAtom`, `userProfileAtom`, `personaConfigAtom`
- **Behavior (already implemented):**
  - Selecting persona = `engineer` updates atoms and navigates to `/persona-dashboard`.

**No change required here beyond ensuring Engineer is present in `PERSONA_CONFIGS`.**

---

### Step 1 – Engineer Persona Dashboard: "My Stories"

**User goal:** See what story/epic to work on.

- **Route:** `/persona-dashboard`
- **Component:** `HomePage` (persona dashboard for authenticated users)
- **Current behavior:**
  - Renders `PersonaHero`, `QuickActionsGrid`, `DashboardGrid`, `RecentActivitiesTimeline`, pinned features, etc., based on composed persona config.
- **Planned change:** Add an Engineer-specific section: **"My Stories"**.

#### 4.1.1 My Stories widget

- **Location:** Inside `HomePage` persona dashboard layout, under hero / quick actions for `engineer`.
- **Data source:**
  - New hook (placeholder): `useMyWorkItems({ role: 'engineer' })` fetching:
    - `id`, `title`, `status`, `priority`, `service`, `updatedAt`.
- **UI:**
  - Card titled "My Stories" with:
    - Table / list of top N items.
    - Badge for status (Ready, In Progress, Blocked, Done).
    - Optional service/domain tag.
  - CTA per row: **"Open"**.

- **Navigation on click:**
  - 🆕 Route: `/work-items/:storyId`
  - Component: `WorkItemPage` (new).

**Implementation tasks:**

1. Inside `HomePage.tsx`, extend the engineer persona layout:
   - Conditionally render `MyStoriesCard` when `personaConfig.role === 'engineer'`.
2. Create `MyStoriesCard` component under `apps/web/src/shared/components/` or `features/work-items/`.
3. Wire card to `useMyWorkItems` (stub with mock data initially if backend not ready).
4. Link each item to `/work-items/{id}` using `useNavigate`.

---

### Step 2 – Work Item Detail: Understand Story & Context

**User goal:** Understand requirement, acceptance criteria, and context for the story.

- **Route:** 🆕 `/work-items/:storyId`
- **Component:** 🆕 `WorkItemPage` (e.g., `apps/web/src/pages/WorkItemPage.tsx`)

#### 4.2.1 WorkItemPage layout

Sections:

1. **Header**
   - Story ID, title, status, priority, assignee.
   - Actions: status dropdown (In Progress, Ready for Review, Done).

2. **Description & Acceptance Criteria**
   - Markdown description from work-item service.
   - Checklist for acceptance criteria with local or server-persisted state.

3. **Context & Impact**
   - Linked incidents, alerts, dashboards, or services.
   - Links to `/dashboard`, `/reports`, `/automation`, `/security` with filters.

4. **Implementation Notes**
   - Textarea for engineer to jot down planned approach (stored via API or local mock).

5. **Primary CTA**
   - Button: **"Plan implementation"** → `/work-items/:storyId/plan`.

**Implementation tasks:**

1. Add route to `Router.tsx`:
   - Lazy-load `WorkItemPage` from `@/pages/WorkItemPage`.
2. Create `apps/web/src/pages/WorkItemPage.tsx` with the sections above.
3. Introduce a basic `workItemsService` (or mock) under `apps/web/src/services/` for fetching story data.
4. Optionally add breadcrumbs via `NavigationContext` so story ID/title appear in nav.

---

### Step 3 – Implementation Plan Page

**User goal:** Define affected components, design, and rollout strategy.

- **Route:** 🆕 `/work-items/:storyId/plan`
- **Component:** 🆕 `WorkItemPlanPage` (e.g., `apps/web/src/pages/WorkItemPlanPage.tsx`)

#### 4.3.1 WorkItemPlanPage layout

Sections:

1. **Summary header**
   - Story ID and title (link back to `/work-items/:storyId`).

2. **Affected Services / Modules**
   - Multi-select or tag selector for services / domains (e.g., "payments", "checkout").

3. **Design Notes**
   - Rich text / markdown editor or textarea.
   - Guidance text: "List which endpoints, tables, and modules are affected."

4. **Feature Flags & Rollout**
   - Toggle: "Guard this change with a feature flag".
   - If enabled: input for flag name.
   - Rollout strategy menu: full / canary / % rollout.

5. **Branch & Repo Links**
   - Suggested branch name (e.g., `feature/story-1234-slug`).
   - CTA: **"Open branch in Git provider"** (external link or placeholder link).

6. **Status CTA**
   - Button: **"Plan ready → Start implementation"**.
   - This can:
     - Update story status to `In Implementation` via API.
     - Keep user on page or navigate back to `/work-items/:storyId`.

**Implementation tasks:**

1. Add route to `Router.tsx` for `/work-items/:storyId/plan`.
2. Implement `WorkItemPlanPage` with form fields and basic state management.
3. Integrate with a `workItemsService.updatePlan(storyId, planPayload)` when backend is ready; until then, use mock.

---

### Step 4 – CI Pipelines View for Story

**User goal:** See pipelines/tests associated with the story/branch.

- **Route:** ✅ `/automation`
- **Component:** `AutomationEngine` (already exists)
- **Enhancement:** Accept `storyId` query param and filter pipelines.

#### 4.4.1 Behavior with storyId query

- When navigated from WorkItem or Plan page:
  - Use link: `/automation?storyId=:storyId`.
- In `AutomationEngine`:
  - Read `storyId` from URL.
  - Pass it to pipeline-fetch hook:
    - e.g., `usePipelines({ storyId })`.
  - Filter list or highlight relevant pipelines.

**CTAs:**

- For each pipeline:
  - "View logs" (existing behavior or to add).
  - "View failing tests" if failed.
- Global CTA (when all required checks are green):
  - Button: **"CI all green → Go to review"** → `/work-items/:storyId/review`.

**Implementation tasks:**

1. Update `AutomationEngine` to parse `storyId` from `useSearchParams`.
2. Extend or create pipeline hooks to support filtering by `storyId`.
3. Add CTA button at top when all pipelines for this story pass, linking to `/work-items/:storyId/review`.

---

### Step 5 – Review & Merge View

**User goal:** Track review status and approvals for this story.

- **Route:** 🆕 `/work-items/:storyId/review`
- **Component:** 🆕 `WorkItemReviewPage`

#### 4.5.1 WorkItemReviewPage layout

Sections:

1. **Header**
   - Story ID + title + quick link back to detail.

2. **Linked Pull Requests**
   - List PRs from VCS API or mock service:
     - PR title, status, approvals, CI state.
   - Each PR row: actions: "Open in Git provider".

3. **Review Checklist**
   - All required checks green.
   - Acceptance criteria verified.

4. **Primary CTA**
   - If PR is open and approved: guidance text "Merge in Git".
   - If PR is merged (detected via API):
     - Button: **"Merged → Validate in staging"** → `/reports?view=staging&storyId=:storyId`.

**Implementation tasks:**

1. Add route to `Router.tsx`.
2. Implement `WorkItemReviewPage`.
3. Add placeholder `vcsService.getPullRequestsForStory(storyId)`; mock PR data initially.

---

### Step 6 – Validate in Staging (Reporting)

**User goal:** Confirm behavior and impact in staging.

- **Route:** ✅ `/reports`
- **Component:** `ReportingDashboard`

#### 4.6.1 Behavior with view + storyId

- Navigate from review page as:
  - `/reports?view=staging&storyId=:storyId`.
- In `ReportingDashboard`:
  - Read `view` and `storyId` from search params.
  - Apply filters:
    - Environment: staging.
    - Services/metrics tagged with `storyId` or its impacted services.

**CTAs:**

- When metrics look good and validation checklist is done:
  - Button: **"Ready → Promote to production"** → `/models?action=deploy&storyId=:storyId`.

**Implementation tasks:**

1. Update `ReportingDashboard` to respect `view` and `storyId` params.
2. Add top-level CTA for "Promote to production" when conditions are met (can start as a simple button with no gating logic).

---

### Step 7 – Deploy to Production

**User goal:** Deploy the story's changes safely to production.

- **Route:** ✅ `/models`
- **Component:** `ModelCatalog` (today) or a new `DeployPage` if more appropriate.

#### 4.7.1 Deploy integration

- Navigate as: `/models?action=deploy&storyId=:storyId`.
- Desired behavior:
  - Prefill selection with the artifact/version associated with this story.
  - Show environment = production.
- CTA:
  - Button: **"Deploy"**.
- On success:
  - Show confirmation and CTA: **"Monitor production impact"** → `/dashboard?storyId=:storyId`.

**Implementation tasks (minimal):**

1. Extend `ModelCatalog` to read `action` and `storyId` and show a simple "Deploy" panel when `action=deploy`.
2. Add success handler that links to `/dashboard?storyId=:storyId`.

---

### Step 8 – Monitor Production Impact & Close Story

**User goal:** Ensure production is healthy and then close the story.

- **Route:** ✅ `/dashboard`
- **Component:** `Dashboard` (Control Tower)

#### 4.8.1 Dashboard filters

- Navigate as: `/dashboard?storyId=:storyId`.
- In `Dashboard`:
  - Parse `storyId` and, if present, highlight:
    - KPIs for affected services.
    - Any alerts/incidents linked to this story.

**CTAs:**

- If metrics stable after a window:
  - Button: **"Mark story as Done"**.
    - Calls work-item API to set story status = Done.
    - Redirects to `/persona-dashboard`.

**Implementation tasks:**

1. Update `Dashboard` to parse `storyId` and optionally adjust filters.
2. Add a simple "Mark story as Done" button (initially can be a no-op or mock).

---

## 5. Phased Implementation Plan (with Embedded Enhancements)

This section turns the gaps/enhancements into a concrete phased implementation plan. Each phase has:

- A **goal** (what a user can reliably do).
- **Required work** to reach that goal.
- **Enhancements** that build on top once the basics are working.

### Phase 1 – Persona Entry & Engineer Dashboard MVP

**Goal:** An engineer can select their persona and see a basic list of their active stories on `/persona-dashboard`.

**Required work:**

- Verify persona selection flow:
  - Ensure `TopNavigation` + `PersonaSelector` correctly set `userRoleAtom = 'engineer'` and navigate to `/persona-dashboard`.
- Add **My Stories** widget for the Engineer persona in `HomePage.tsx`:
  - Conditionally render `MyStoriesCard` when `personaConfig.role === 'engineer'`.
  - Implement `MyStoriesCard` with a simple list/table and an **Open** CTA.
  - Introduce `useMyWorkItems` using an in-memory or mock `workItemsService`.
- Wire row clicks to `/work-items/:storyId`.

**Enhancements (same phase or later):**

- Replace the mock `workItemsService` with a real **work-item backend** API:
  - List stories for the current user/tenant.
  - Include status/priority/service metadata for better sorting and filtering.
- Add basic **permissions** checks if certain stories should be hidden or read-only for some personas.

### Phase 2 – Work Item Detail & Plan Pages

**Goal:** An engineer can open a story, understand the requirement, and capture an implementation plan and rollout strategy.

**Required work:**

- Implement `/work-items/:storyId` → `WorkItemPage`:
  - Fetch a single work item via `workItemsService.get(storyId)` (mock first).
  - Render header, description, acceptance criteria, context links, and implementation notes.
  - Add CTA: **"Plan implementation"** → `/work-items/:storyId/plan`.
- Implement `/work-items/:storyId/plan` → `WorkItemPlanPage`:
  - Provide form fields for affected services/modules, design notes, feature flag checkbox/name, and rollout strategy.
  - Use `workItemsService.updatePlan(storyId, planPayload)` (mock first) to store the plan.
  - Add CTA: **"Plan ready → Start implementation"** (update story status to `In Implementation`).

**Enhancements:**

- **Feature flags:**
  - Connect the feature flag and rollout fields to a real feature-flag/rollout system.
  - Surface flag state later in `/reports`, `/models`, and `/dashboard` for visibility.
- **Richer context:**
  - Auto-populate "Impacted services" from telemetry or ownership metadata (e.g., based on previous incidents or changed files).

### Phase 3 – CI Pipelines & Review Integration

**Goal:** Engineers can see pipelines and review status for their story directly in Software Org, and follow a guided path from CI → review → staging.

**Required work:**

  - Parse `storyId` from search params.
  - Filter or highlight pipelines tagged with that story/branch (mock tagging first).
  - Add a top-level CTA: **"CI all green → Go to review"** → `/work-items/:storyId/review`.
- Implement `/work-items/:storyId/review` → `WorkItemReviewPage`:
  - Start with `vcsService.getPullRequestsForStory(storyId)` returning mocked PR data.
  - Show PR list, CI status, approvals, and a checklist.
  - If PR is merged (mocked field), show CTA: **"Merged → Validate in staging"** → `/reports?view=staging&storyId=:storyId`.

**Enhancements:**

- **VCS/PR integration:**
  - Replace mock `vcsService` with real GitHub/GitLab/Bitbucket integration.
  - Use labels, branch naming conventions, or linked issues to map stories → PRs.
- **CI integration:**
  - Use real pipeline metadata (from your CI/CD system) to:
    - Tag runs with `storyId`.
    - Distinguish required vs optional checks.
  - Provide direct links to logs and test reports.

### Phase 4 – Staging Validation & Deploy

**Goal:** Engineers can validate story behavior in staging, then trigger a production deploy for the associated artifact.

**Required work:**

- Enhance `/reports` → `ReportingDashboard`:
  - Parse `view=staging` and `storyId` from search params.
  - Apply environment and service filters based on story metadata (mock mapping first).
  - Add CTA: **"Ready → Promote to production"** → `/models?action=deploy&storyId=:storyId`.
- Enhance `/models` → `ModelCatalog` (or similar deploy UI):
  - Parse `action=deploy` and `storyId`.
  - Show a simple deploy panel for the artifact/version tied to that story (mock linkage first).
  - CTA: **"Deploy"**, then on success link to `/dashboard?storyId=:storyId`.

**Enhancements:**

- **Story → artifact mapping:**
  - Add metadata in CI/CD so that artifacts are tagged with `storyId` and service identifiers.
  - Use this metadata to pre-select the correct artifact in the deploy UI.
- **Validation gating:**
  - Gate the "Promote to production" CTA on minimal conditions (e.g., staging metrics within thresholds, no open critical incidents).

### Phase 5 – Production Monitoring & Story Closure

**Goal:** Engineers can monitor impact in production and close the story from within Software Org.

**Required work:**

- Enhance `/dashboard` → `Dashboard`:
  - Parse `storyId` from search params.
  - Filter/highlight KPIs and alerts for the services associated with this story.
  - Add CTA: **"Mark story as Done"** that calls `workItemsService.complete(storyId)` (mock first) and redirects to `/persona-dashboard`.

**Enhancements:**

- **Deeper observability:**
  - Link from the story context into traces/logs for key transactions (e.g., via `/realtime-monitor` or an observability backend).
- **Permissions:**
  - Require appropriate persona/role permissions to:
    - Deploy to production.
    - Mark stories as Done.
  - Use existing persona/permission framework to guard these CTAs.
- **Smart closure suggestions:**
  - Suggest stories for closure when metrics and deployments indicate they are "done" for a certain window of time.

This phased plan lets you ship a working end-to-end Engineer flow early (with mocks), while making the path to full, production-grade integrations explicit and incremental.

## 6. DevSecOps Core Library and Flow Definition Package

The phases and routes above already implement a realistic end-to-end Engineer flow. To make this **DevSecOps by default**, reusable across personas, and easier to evolve, we will extract the underlying model into two layers:

1. A **DevSecOps Core Library** (state, types, KPIs, items, phases).
2. A **DevSecOps Flow Definition package** that maps phases → concrete pages, CTAs, and tool integrations for each persona (starting with Software Engineer).

This section outlines the target architecture and incremental steps, following the reuse-first policy.

### 6.1 Objectives

- **Single DevSecOps model** that both Software Org and yappc app-creator share.
- **Declarative flow definitions** for personas (engineer, SRE, security, lead) rather than hard-coded route wiring.
- **Pluggable integrations** (CI, VCS, security, observability) described as configuration rather than scattered across components.
- **Reuse-first**: build on top of the existing `@yappc/store/devsecops` and `@yappc/types/devsecops` instead of creating a parallel implementation.
 - **Yappc-owned, Software Org–consumed**: DevSecOps libraries and flow definitions are authored and governed in the Yappc app-creator product; Software Org consumes them as a runtime app and only adds domain mappings (e.g., `WorkItem` ↔ DevSecOps `Item`) and UI surfaces (pages, CTAs).

### 6.2 DevSecOps Core Library (Reuse-First)

**Existing foundation (yappc app-creator):**

- `@yappc/store/devsecops`
  - Jotai-based store and hooks for DevSecOps items, phases, milestones, filters, derived KPIs, and view state.
  - Examples (from `libs/store/src/devsecops`):
    - Core atoms: `itemsAtom`, `phasesAtom`, `milestonesAtom`, `filterConfigAtom`, `currentPhaseAtom`, `kpiStatsAtom`, `phaseKpiStatsAtom`, etc.
    - Hooks: `useItems`, `usePhases`, `useItemsByPhase`, `useKpiStats`, `usePhaseKpiStats`, `useRecentActivity`, `useItemManagement`, `useViewManagement`, etc.
- `@yappc/types/devsecops`
  - Core types: `Item`, `Phase`, `Milestone`, `ItemStatus`, `Priority`, and related domain types.

**Target role of the DevSecOps Core Library:**

- Be the **source of truth** for DevSecOps lifecycle concepts across products:
  - Phases (ideation → plan → build → test → deploy → operate → learn).
  - Items (work items/stories/incidents) and their status/priority.
  - Milestones and KPIs per phase.
- Provide **framework utilities**:
  - Generic hooks for querying items by phase, status, assignee, tag.
  - KPI derivations (per phase and global).
  - View/state helpers (kanban, timeline, table, filters).

**Mapping Software Org WorkItems into DevSecOps Items:**

- Introduce a thin mapping layer that connects Software Org `WorkItem` to DevSecOps `Item`:
  - `devsecopsItem.id` ↔ `workItem.id`.
  - `devsecopsItem.title` ↔ `workItem.title`.
  - `devsecopsItem.description` ↔ `workItem.description`.
  - `devsecopsItem.priority` ↔ `workItem.priority`.
  - `devsecopsItem.status` ↔ mapped from `WorkItemStatus` (backlog, ready, in-progress, in-review, staging, deployed, done, blocked) to DevSecOps `ItemStatus` (not-started, in-progress, in-review, completed, archived, blocked).
  - `devsecopsItem.phaseId` ↔ derived from the current position in the Engineer flow (e.g., `plan`, `build`, `test`, `review`, `staging`, `deploy`, `operate`).
- This mapping can live in a shared TypeScript module (initially as part of the Software Org app), and later move into a cross-product package once stable.

**Incremental steps (core):**

- Start by **consuming** `@yappc/types/devsecops` and (optionally) `@yappc/store/devsecops` from Software Org where appropriate.
- Add a mapping utility module (e.g., `mapWorkItemToDevSecOpsItem.ts`) that converts between `WorkItem` and DevSecOps `Item` and centralizes the status/phase translation.
- Once stable, consider a neutral alias (e.g., `@ghatana/devsecops-core`) implemented inside the Yappc app-creator workspace that re-exports the existing store and types without duplicating implementation.

### 6.3 DevSecOps Flow Definition Package

The flow definition package captures **how** personas traverse the DevSecOps lifecycle: which pages they use, in what order, and which external tools are involved at each step.

**Core concepts:**

- `DevSecOpsPhaseId` – canonical IDs (e.g., `intake`, `plan`, `build`, `verify`, `review`, `staging`, `deploy`, `operate`, `learn`).
- `FlowStep` – a typed object describing one concrete step in the flow:
  - `phaseId` – link to DevSecOps phase.
  - `stepId` – stable identifier for this step.
  - `label` / `description` – user-facing copy.
  - `route` – route template (e.g., `/work-items/:storyId/plan`, `/automation?storyId=:storyId`).
  - `componentKey` – reference to the UI surface (`WorkItemPage`, `WorkItemPlanPage`, `AutomationEngine`, `ReportingDashboard`, `ModelCatalog`, `Dashboard`).
  - `toolIntegrations` – declarative hints about CI, VCS, feature flags, security scanners, observability, etc. (e.g., `ciRequired: true`, `securityChecks: ['sast','dast']`).
- `PersonaFlow` – collection of `FlowStep`s plus metadata for a persona (start step, allowed transitions, gating rules).

**Engineer DevSecOps Flow (Software Org):**

The existing implementation in Sections 4 and 5 can be expressed as a `PersonaFlow` for the Software Engineer:

- Phases:
  - `intake` → `/persona-dashboard` + `MyStoriesCard`.
  - `plan` → `/work-items/:storyId`, `/work-items/:storyId/plan`.
  - `build` → external IDE/Git provider (anchored from `WorkItemPlanPage` branch/PR links).
  - `verify` → `/automation?storyId=:storyId`.
  - `review` → `/work-items/:storyId/review`.
  - `staging` → `/reports?view=staging&storyId=:storyId`.
  - `deploy` → `/models?action=deploy&storyId=:storyId`.
  - `operate` → `/dashboard?storyId=:storyId`.
  - `learn` → (future) retrospectives, post-incident reviews, etc.
- Each route and CTA already implemented in this plan becomes a **configured `FlowStep`**, rather than implicit wiring.

**Incremental steps (flow definitions):**

1. Create a TypeScript module in Software Org (initially local, e.g., `apps/web/src/config/devsecopsEngineerFlow.ts`) that:
  - Defines `DevSecOpsPhaseId`, `FlowStep`, and `PersonaFlow` types.
  - Declares `ENGINEER_DEVSECOPS_FLOW` mapping the existing routes in this document into typed `FlowStep`s.
2. Update key Engineer pages (`WorkItemPage`, `WorkItemPlanPage`, `AutomationEngine`, `WorkItemReviewPage`, `ReportingDashboard`, `ModelCatalog`, `Dashboard`) to **consume** the flow definition where appropriate:
  - Derive the current phase for a `storyId` from `ENGINEER_DEVSECOPS_FLOW`.
  - Use the flow config to render “next step” CTAs instead of hard-coding target routes.
3. Once the pattern is stable, extract the flow types and persona configs into a dedicated package under the Yappc app-creator product (e.g., `devsecops-flows`) so that Yappc remains the source of truth for DevSecOps flows and other products (including Software Org) can define or select persona flows using the same model.

### 6.4 Reuse for Other Personas and Products

With the core library and flow definitions in place, other personas can plug into the same DevSecOps model:

- **Lead / Manager persona** – flow focused on portfolio-level phases (backlog health, release readiness, operational health) using yappc DevSecOps dashboards and reports.
- **SRE / Operations persona** – flow centered on `deploy`, `operate`, and `learn` phases, integrating more deeply with observability and incident response tooling.
- **Security persona** – flow that emphasizes `plan`, `build`, and `verify` phases with strong security gates (SAST/DAST, dependency scanning, policy-as-code).

Each persona would get its own `PersonaFlow` configuration, while still reusing:

- The same DevSecOps phases and item model from the core library.
- The same underlying Jotai store, KPIs, and phase statistics from `@yappc/store/devsecops`.
- The same set of shared tools and integrations, configured differently per persona.

### 6.5 Summary

- The **Engineer flow** described in this document is the first, concrete instantiation of the DevSecOps model.
- By introducing a DevSecOps Core Library and a Flow Definition package, we:
  - Make the flow **declarative, reusable, and persona-aware**.
  - Align Software Org and yappc app-creator around a single DevSecOps lifecycle.
  - Enable future personas to plug into the same model with minimal duplication.
- Implementation can proceed incrementally, starting with local TypeScript modules in Software Org, and later extracting them into shared packages once the domain model has stabilized.
