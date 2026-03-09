# App Creator Web Page Specs – Index

> **Document Version:** 2.1.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Added simplification and reuse-first guidance

This index lists all App Creator deep-dive page specs under `web-page-specs/` and points back to their inventory entries in `APP_CREATOR_PAGE_SPECS.md`.

> Inventory summary: `../APP_CREATOR_PAGE_SPECS.md`

---

## 🚨 CRITICAL: Before Creating New Components

**READ THESE FIRST:**

1. [APP_CREATOR_GLOBAL_CONCEPTS_AND_UX_CONTRACTS.md](./APP_CREATOR_GLOBAL_CONCEPTS_AND_UX_CONTRACTS.md) – Section 10: REUSE-FIRST Principles
2. [SIMPLIFICATION_AND_REUSE_PLAN.md](./SIMPLIFICATION_AND_REUSE_PLAN.md) – Duplicate consolidation plan
3. [IMPLEMENTATION_CHANGES_PROPOSAL.md](./IMPLEMENTATION_CHANGES_PROPOSAL.md) – Prioritized fixes

**Reuse Hierarchy:** `@yappc/ui` → `@yappc/store` → `routes/*/components/` → Create new

---

## 0. Global Shell & Root

- **00 – Global Shell & Root**  
  Spec: `00_global_shell_and_root.md`  
  Implementation: `src/routes/_root.tsx`, `src/App.tsx`  
  Inventory: `APP_CREATOR_PAGE_SPECS.md – 0. Global Shell & Root`

---

## 1. Core App Creator Area – `/app`

- **01 – `/app` Shell & Workspace Entry**  
  Spec: `01_app_shell_and_workspace_entry.md`  
  Implementation: `src/routes/app/_shell.tsx`, `src/routes/app/workspaces.tsx`, `src/routes/app/projects*.tsx`  
  Inventory: `APP_CREATOR_PAGE_SPECS.md – 1. Core App Creator Area – /app Shell`

---

## 2. Project Tabs (`/app/w/:workspaceId/p/:projectId/*`)

- **02 – Project Overview Dashboard**  
  Spec: `02_project_overview_dashboard.md`  
  Implementation: `src/routes/app/project/overview.tsx`  
  Route: `/app/w/:workspaceId/p/:projectId/overview`

- **03 – Implement (Canvas)**  
  Spec: `03_project_canvas_implement.md`  
  Implementation: `src/routes/app/project/canvas/CanvasRoute.tsx`, `CanvasScene.tsx`  
  Route: `/app/w/:workspaceId/p/:projectId/canvas`

- **04 – Backlog (Kanban)**  
  Spec: `04_project_backlog_kanban.md`  
  Implementation: `src/routes/app/project/backlog.tsx`  
  Route: `/app/w/:workspaceId/p/:projectId/backlog`

- **05 – Design System & Components**  
  Spec: `05_project_design_system.md`  
  Implementation: `src/routes/app/project/design.tsx`  
  Route: `/app/w/:workspaceId/p/:projectId/design`

- **06 – Build Pipeline**  
  Spec: `06_project_build_pipeline.md`  
  Implementation: `src/routes/app/project/build.tsx`  
  Route: `/app/w/:workspaceId/p/:projectId/build`

- **07 – Test Hub (Placeholder)**  
  Spec: `07_project_test_hub.md`  
  Implementation: `src/routes/app/project/test.tsx`  
  Route: `/app/w/:workspaceId/p/:projectId/test`  
  Status: **Placeholder** – `PlaceholderRoute` component

- **08 – Deploy Pipeline**  
  Spec: `08_project_deploy_pipeline.md`  
  Implementation: `src/routes/app/project/deploy.tsx`  
  Route: `/app/w/:workspaceId/p/:projectId/deploy`

- **09 – Monitor / Observability Console**  
  Spec: `09_project_monitor_observability.md`  
  Implementation: `src/routes/app/project/monitor.tsx`  
  Route: `/app/w/:workspaceId/p/:projectId/monitor`

- **10 – Versions & Snapshots**  
  Spec: `10_project_versions_snapshots.md`  
  Implementation: `src/routes/app/project/versions.tsx`  
  Route: `/app/w/:workspaceId/p/:projectId/versions`

- **11 – Project Settings**  
  Spec: `11_project_settings.md`  
  Implementation: `src/routes/app/project/settings.tsx`  
  Route: `/app/w/:workspaceId/p/:projectId/settings`

---

## 3. Canvas Demos & Onboarding

- **12 – Canvas Demos & Onboarding**  
  Spec: `12_canvas_demos_and_onboarding.md`  
  Implementation: Various canvas demo routes in `src/routes.tsx`  
  Inventory: `APP_CREATOR_PAGE_SPECS.md – 3. Canvas Demos & Onboarding`

---

## 4. DevSecOps Area – `/devsecops`

All DevSecOps routes use the `DevSecOpsLayout` shell from `src/routes/devsecops/_layout.tsx`.

- **13 – DevSecOps Dashboard**  
  Spec: `13_devsecops_dashboard.md`  
  Implementation: `src/routes/devsecops/index.tsx`  
  Route: `/devsecops` (index)

- **14 – DevSecOps Phase Board**  
  Spec: `14_devsecops_phase_board.md`  
  Implementation: `src/routes/devsecops/phase/$phaseId.tsx`  
  Route: `/devsecops/phase/:phaseId`

- **15 – DevSecOps Persona Dashboard**  
  Spec: `15_devsecops_persona_dashboard.md`  
  Implementation: `src/routes/devsecops/persona/$slug.tsx`  
  Route: `/devsecops/persona/:slug`

- **16 – DevSecOps Canvas**  
  Spec: `16_devsecops_canvas.md`  
  Implementation: `src/routes/devsecops/canvas.tsx`  
  Route: `/devsecops/canvas`

- **17 – DevSecOps Reports Hub**  
  Spec: `17_devsecops_reports_hub.md`  
  Implementation: `src/routes/devsecops/reports.tsx`  
  Route: `/devsecops/reports`

- **18 – DevSecOps Report Detail**  
  Spec: `18_devsecops_report_detail.md`  
  Implementation: `src/routes/devsecops/reports/$reportId.tsx`  
  Route: `/devsecops/reports/:reportId`

- **19 – DevSecOps Settings & Governance**  
  Spec: `19_devsecops_settings_governance.md`  
  Implementation: `src/routes/devsecops/settings.tsx`  
  Route: `/devsecops/settings`

- **20 – DevSecOps Templates & Playbooks**  
  Spec: `20_devsecops_templates.md`  
  Implementation: `src/routes/devsecops/templates.tsx`  
  Route: `/devsecops/templates`

- **21 – DevSecOps Item Detail**  
  Spec: `21_devsecops_item_detail.md`  
  Implementation: `src/routes/devsecops/item/$itemId.tsx`  
  Route: `/devsecops/item/:itemId`

- **22 – DevSecOps Diagram Viewer**  
  Spec: `22_devsecops_diagram_viewer.md`  
  Implementation: `src/routes/devsecops/diagram/$diagramId.tsx`  
  Route: `/devsecops/diagram/:diagramId`

### Additional DevSecOps Routes (Not Yet Spec'd)

The following routes exist in implementation but don't have dedicated spec files:

| Route | Implementation | Suggested Action |
|-------|---------------|------------------|
| `/devsecops/domains` | `domains.tsx` | Add spec or merge into existing |
| `/devsecops/domains/:domainId` | `detail-pages.tsx` | Add spec |
| `/devsecops/phases` | `phases.tsx` | Add spec |
| `/devsecops/phases/:phaseId` | `detail-pages.tsx` | Add spec |
| `/devsecops/workflows` | `workflows.tsx` | Add spec |
| `/devsecops/workflows/:workflowId` | `detail-pages.tsx` | Add spec |
| `/devsecops/tasks` | `tasks.tsx` | Add spec |
| `/devsecops/task/:taskId` | `task/$taskId.tsx` | Add spec |
| `/devsecops/team-board` | `team-board.tsx` | Add spec |
| `/devsecops/task-board` | `task-board.tsx` | Add spec |
| `/devsecops/admin` | `admin.tsx` | Add spec |
| `/devsecops/operations` | `operations.tsx` | Add spec |

---

## 5. Workflows Area – `/workflows`

A standalone workflows section exists but needs specs:

| Route | Implementation | Status |
|-------|---------------|--------|
| `/workflows` | `routes/workflows/index.tsx` | **Needs Spec** |
| `/workflows/new` | `routes/workflows/new.tsx` | **Needs Spec** |
| `/workflows/:workflowId` | `routes/workflows/$workflowId.tsx` | **Needs Spec** |

---

## 6. Mobile Shell & Views – `/mobile`

- **23 – Mobile Shell**  
  Spec: `23_mobile_shell.md`  
  Implementation: `src/routes/mobile/_shell.tsx`  
  Route: `/mobile/*` (shell layout)

- **24 – Mobile Projects**  
  Spec: `24_mobile_projects.md`  
  Implementation: `src/routes/mobile/projects.tsx`  
  Route: `/mobile/projects`

- **25 – Mobile Overview**  
  Spec: `25_mobile_overview.md`  
  Implementation: `src/routes/mobile/overview.tsx`  
  Routes: `/mobile/overview`, `/mobile/p/:projectId/overview`

- **26 – Mobile Backlog & Notifications (Placeholders)**  
  Spec: `26_mobile_backlog_and_notifications.md`  
  Implementation: `src/routes/mobile/backlog.tsx`, `notifications.tsx`  
  Routes: `/mobile/p/:projectId/backlog`, `/mobile/notifications`  
  Status: **Placeholder** – `PlaceholderRoute` components

---

## 7. Login & Page Builder

- **27 – Login Page**  
  Spec: `27_login_page.md`  
  Implementation: `src/routes/login.tsx`  
  Route: `/login`

- **28 – Page Builder Manual Test**  
  Spec: `28_page_builder_manual_test.md`  
  Implementation: `src/routes/PageBuilderManualTest.tsx`, `PageBuilderManualTest.lazy.tsx`  
  Route: `/page-designer`

---

## 8. Global Contracts

- **App Creator – Global Concepts & UX Contracts**  
  Spec: `APP_CREATOR_GLOBAL_CONCEPTS_AND_UX_CONTRACTS.md`  
  Purpose: Cross-cutting UX contracts, state management, testing guidelines, route configuration reference

---

## Implementation Coverage Summary

| Area | Total Routes | Specs | Coverage |
|------|-------------|-------|----------|
| Global/Root | 1 | 1 | 100% |
| App Shell (`/app`) | 12 | 11 | 92% |
| DevSecOps (`/devsecops`) | 18 | 10 | 56% |
| Workflows (`/workflows`) | 3 | 0 | 0% |
| Mobile (`/mobile`) | 5 | 4 | 80% |
| Other (login, page-builder) | 3 | 2 | 67% |
| **Total** | **42** | **28** | **67%** |

### Priority Spec Gaps to Address

1. **Workflows area** – No specs exist; needs `29_workflows.md`
2. **DevSecOps additional routes** – 8 routes without dedicated specs
3. **Task Board routes** – Important but not spec'd

Use this index as the starting point when exploring or editing any App Creator page spec.
