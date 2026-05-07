# Data Cloud UI – Web Page Specs Index

_Reality note: several filenames retain historical labels such as "dashboard", "collections", or "workflows", but the shipped primary journeys now center on Intelligent Hub, Data Explorer, Pipelines, and Query. Treat the current route and status notes inside each page as the source of truth._

These specs document the **Data Cloud UI** pages at the level of:

- **Intention** – why the page exists.
- **Content & layout** – what is shown on the page.
- **User actions & workflows** – what operators can do.
- **Gaps & enhancements** – how to evolve toward richer, canvas-like flows aligned with App Creator and design-to-code goals.

Each spec follows the 10-section structure we use for App Creator, Software Org, AEP UI, and TutorPutor.

---

## 0. Shell & Routing

- `00_shell_and_routing.md`  
  Root React entry, Router, and global layout/suspense.

## 1. Core Data Cloud Pages

- `01_dashboard_page.md`  
  Intelligent Hub home surface with outcome-first launchers and role-aware disclosure notes.

- `02_collections_page.md`  
  Data Explorer list + detail surface for canonical `/data` collection navigation.

- `03_create_collection_page.md`  
  Create Collection page for canonical `/data/new` collection creation.

- `04_edit_collection_page.md`  
  Edit Collection page for canonical `/data/:id/edit` collection updates.

- `05_workflows_page.md`  
  Pipelines list page showing existing workflows and their structure.

- `06_workflow_designer_canvas.md`  
  Pipeline editing flow, including intent capture in Smart Workflow Builder and advanced editing in Workflow Designer.

- `07_workflow_list_page.md`  
  Legacy workflow-list stub page that redirects readers toward the canonical Pipelines surface.

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
  Planned broader Dataset Explorer. Current implemented collection-centric surface is the canonical `/data` Data Explorer.

- `12_dataset_detail_insights_page.md`  
  Dataset Detail & Insights page showing schema, sample data, quality, cost, usage, and optimizer history.

- `13_lineage_explorer_page.md`  
  Planned richer Lineage Explorer. Current implemented lineage entry point is the `/data?view=lineage` preview.

- `14_sql_workspace_page.md`  
  Current SQL Workspace reality for canonical `/query`, with live analytics execution and partial NLQ assistance.

- `01_dashboard_page.md` (historical home-surface filename)
  Historical dashboard material is now consolidated under Intelligent Hub and the unified Insights surface.

- `15_ai_assistant_and_semantic_search.md`  
  Cross-cutting AI Assistant and semantic search surface for NL→SQL, NL→workflows, and dataset discovery.

## 4. Governance & Alerts (Planned/Future)

- `16_governance_and_security_hub_page.md`  
  Trust Center reality plus longer-term governance hub direction.

- `17_alerts_and_notifications_page.md`  
  Alerts reality and roadmap spec; current product route is a live operator-only triage surface with broader incident-management work still pending.

---

## Notes

- These specs are **page-focused**; feature-level details (stores, services, forms) remain in feature docs like `FEATURE_INDEX.md` and `INTEGRATION_GUIDE.md`.
- Several platform-wide pages (sections 3 and 4) are **planned/illustrative** and not yet wired into `App.tsx`; each spec calls this out explicitly.
