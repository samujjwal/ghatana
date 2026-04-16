# Data Cloud UI – User Manual

This UI guide is the frontend companion to the product-level manual at `products/data-cloud/USER_MANUAL.md`.

Use the product-level manual for end-to-end workflows that span API, runtime profile, and UI usage. Use this document when you specifically need the current UI navigation model.

## 1. Audience

This guide is for users and operators working in the Data Cloud web UI.

## 2. Current Navigation Model

The current route map is defined in `ui/src/routes.tsx`.

Primary routes:

- `/` for the Intelligent Hub home page
- `/data` for the unified data explorer
- `/pipelines` for workflow and pipeline management
- `/query` for the SQL workspace
- `/trust` for governance and compliance
- `/insights` for analytics and operational insight views
- `/events`, `/entities`, `/memory`, `/fabric`, `/agents`, `/plugins`, `/alerts`, `/settings`

Legacy compatibility routes still exist for older entry patterns such as `/dashboard`, `/collections`, `/datasets`, `/workflows`, `/sql`, `/governance`, and `/dashboards`.

## 3. Typical UI Workflows

### Explore data

1. Open `/data`.
2. Browse or search for the dataset or collection you need.
3. Move into the detail view or related data surface from there.

### Manage pipelines

1. Open `/pipelines`.
2. Review existing workflows.
3. Use `/pipelines/new` to create a new flow.
4. Open a specific pipeline to inspect or edit it.

### Query data

1. Open `/query`.
2. Author and run the query in SQL Workspace.
3. Use the surrounding data and lineage views when you need more context.

### Inspect operations

Use:

- `/events` for event exploration
- `/entities` for entity browsing
- `/memory` for memory-plane inspection
- `/fabric` for data-fabric views
- `/plugins` for plugin lifecycle pages

### Review governance and insight surfaces

Use:

- `/trust` for governance and compliance views
- `/insights` for analytical and operational summary views
- `/alerts` for notifications and issue-focused review

## 4. Important Caveat

Some documents under `ui/docs/web-page-specs/` describe the intended UX direction and planned pages, not only the pages that are fully wired today. When there is a mismatch, trust the actual route map and live application behavior.

## 5. Related Documents

- `products/data-cloud/USER_MANUAL.md` for the main user guide
- `products/data-cloud/REST_API_DOCUMENTATION.md` for backend route inventory
- `ui/docs/web-page-specs/INDEX.md` for page-level design intent
