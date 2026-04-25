# Data Cloud Route Truth Matrix

**Generated**: 2026-04-17  
**Purpose**: Canonical record of all Data Cloud UI routes, their audience, and deployment lifecycle state.  
**Source of truth**: `products/data-cloud/ui/src/routes.tsx` + `products/data-cloud/ui/src/layouts/DefaultLayout.tsx`

---

## Primary Routes

| Route | Audience | Lifecycle | Notes |
|---|---|---|---|
| `/data` | primary-user | `live` | Unified Data Explorer — table, lineage, quality views via `?view=` |
| `/data/new` | primary-user | `live` | Create Collection wizard |
| `/data/:id` | primary-user | `live` | Collection detail view |
| `/data/:id/edit` | primary-user | `live` | Edit Collection metadata |
| `/data/:id/:view` | primary-user | `live` | Collection detail with explicit view |
| `/pipelines` | primary-user | `live` | Workflow / Pipeline list |
| `/pipelines/new` | primary-user | `live` | Create workflow |
| `/pipelines/:id` | primary-user | `live` | Workflow detail |
| `/pipelines/:id/edit` | primary-user | `live` | Edit workflow |
| `/query` | primary-user | `live` | SQL Workspace |
| `/trust` | primary-user | `live` | Trust Center (governance & compliance) |
| `/insights` | primary-user | `live` | Insights & operational analytics |

## Operator Routes

| Route | Audience | Lifecycle | Notes |
|---|---|---|---|
| `/alerts` | operator | `live` | Alert management — operator-scoped, not in global search |
| `/operations` | operator | `live` | Operations Console |
| `/events` | operator | `live` | Event stream browser |
| `/memory` | operator | `live` | Memory management |
| `/entities` | operator | `live` | Entity browser |
| `/context` | operator | `live` | Context management |
| `/fabric` | operator | `preview` | Data Fabric — operator-scoped, preview deployment |
| `/agents` | operator | `live` | Agent management |

## Settings

| Route | Audience | Lifecycle | Notes |
|---|---|---|---|
| `/settings` | primary-user | `live` | User & account settings |

## Redirect Aliases (Legacy Compatibility)

| Route | Redirects To | Notes |
|---|---|---|
| `/lineage` | `/data?view=lineage` | Legacy lineage route — redirected to DataExplorer |
| `/quality` | `/data?view=quality` | Legacy quality route — redirected to DataExplorer |

---

## Navigation Visibility Rules

- **Primary nav (DefaultLayout shell)**: `data`, `pipelines`, `query`, `trust`, `insights`, `alerts` (operator-gated), `settings`
- **Global Search (`nav-query`, `nav-trust`)**: Visible; `nav-alerts` excluded (operator-only, not discoverable)
- **Fabric**: Never in primary nav — operator preview only, accessed directly

---

## Lifecycle Definitions

| Value | Meaning |
|---|---|
| `live` | Fully deployed, documented, and stable |
| `preview` | Available but not GA — operator-accessed, may change |
| `disabled` | Route exists but feature-flagged off |
| `deprecated` | Redirect alias; will be removed |
