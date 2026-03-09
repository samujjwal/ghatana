# CES UI – Web Page Specs Index

These specs document the **Collection & Entity System (CES) UI** pages at the level of:

- **Intention** – why the page exists.
- **Content & layout** – what is shown on the page.
- **User actions & workflows** – what operators can do.
- **Gaps & enhancements** – how to evolve toward richer, canvas-like flows aligned with App Creator and design-to-code goals.

Each spec follows the 10-section structure we use for App Creator, Software Org, AEP UI, and TutorPutor.

---

## 0. Shell & Routing

- `00_shell_and_routing.md`  
  Root React entry, Router, and global layout/suspense.

## 1. Core CES Pages

- `01_dashboard_page.md`  
  Overview dashboard for collections, workflows, executions, audit/compliance, and quick actions.

- `02_collections_page.md`  
  Collections list + detail page (mock API-backed) for viewing and navigating CES collections.

- `03_create_collection_page.md`  
  Create Collection page for defining new collections and schemas.

- `04_edit_collection_page.md`  
  Edit Collection page for updating existing collection metadata and schema.

- `05_workflows_page.md`  
  Workflows list page showing existing workflows and their structure.

- `06_workflow_designer_canvas.md`  
  Workflow Designer page embedding the workflow canvas for building/editing workflows.

- `07_workflow_list_page.md`  
  Simple workflow list stub page (legacy or experimental list view).

- `08_not_found_page.md`  
  404 Not Found page.

## 2. Data Fabric Admin Feature Pages (Modular)

- `09_storage_profiles_admin_page.md`  
  Storage Profiles admin page (data fabric feature) for managing storage backends.

- `10_data_connectors_admin_page.md`  
  Data Connectors admin page (data fabric feature) for managing connectors and sync jobs.

---

## 3. Data Cloud Platform Pages (Implemented)

- `11_dataset_explorer_list_page.md`  
  **✅ Implemented**: Global Dataset Explorer list view with search, filters, and sorting across all Data Cloud datasets.
  - Route: `/datasets`
  - Component: `DatasetExplorerPage.tsx`

- `12_dataset_detail_insights_page.md`  
  Dataset Detail & Insights page showing schema, sample data, quality, cost, usage, and optimizer history.

- `13_lineage_explorer_page.md`  
  **✅ Implemented**: Lineage Explorer canvas for visualizing upstream/downstream data flows and dependencies.
  - Route: `/lineage`
  - Component: `LineageExplorerPage.tsx`

- `14_sql_workspace_page.md`  
  **✅ Implemented**: SQL Workspace for authoring, running, and managing queries with schema-aware assistance.
  - Route: `/sql`
  - Component: `SqlWorkspacePage.tsx`

- `01_dashboard_page.md` (Dashboards & Metrics variant)
  **✅ Implemented**: Dashboards & Metrics page for configuring and viewing custom dashboards.
  - Route: `/dashboards`
  - Component: `DashboardsPage.tsx`

- `15_ai_assistant_and_semantic_search.md`  
  Cross-cutting AI Assistant and semantic search surface for NL→SQL, NL→workflows, and dataset discovery.

## 4. Governance & Alerts (Planned/Future)

- `16_governance_and_security_hub_page.md`  
  Governance & Security hub for roles, permissions, policies, PII scans, and audit logs.

- `17_alerts_and_notifications_page.md`  
  Alerts & Notifications center for viewing alerts and configuring alert rules and channels.

---

## Notes

- These specs are **page-focused**; feature-level details (stores, services, forms) remain in feature docs like `FEATURE_INDEX.md` and `INTEGRATION_GUIDE.md`.
- Several platform-wide pages (sections 3 and 4) are **planned/illustrative** and not yet wired into `App.tsx`; each spec calls this out explicitly.
