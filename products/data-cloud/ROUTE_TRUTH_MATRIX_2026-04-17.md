# Data Cloud Route Truth Matrix

**Updated:** 2026-04-17  
**Scope:** `products/data-cloud/ui/src/routes.tsx` primary top-level destinations  
**Purpose:** runtime-truth inventory for primary and operator-facing route exposure

| Route | Audience | Runtime Truth | Canonical Backend Surface | Notes |
|---|---|---|---|---|
| `/` | primary-user | `partial` | Mixed launcher-backed summaries | Outcome hub is live, but some downstream cards still depend on partial product areas. |
| `/data` | primary-user | `live` | `/api/v1/entities/dc_collections`, `/api/v1/entities/{collection}` | Canonical collection and entity workflows are launcher-backed. |
| `/data/new` | primary-user | `live` | `/api/v1/entities/dc_collections` | Collection creation now uses the same canonical data workspace instead of sending users through the legacy `/collections/new` alias. |
| `/data/:id/edit` | primary-user | `live` | `/api/v1/entities/dc_collections` | Collection editing now returns to canonical `/data/:id` detail routes after save or cancel. |
| `/pipelines` | primary-user | `live` | `/api/v1/pipelines`, `/api/v1/pipelines/{id}`, `/api/v1/pipelines/{id}/execute` | CRUD and execution creation are real; deep execution detail remains unsupported. |
| `/query` | primary-user | `partial` | `/api/v1/analytics/query`, `/api/v1/queries/federated`, `/api/v1/analytics/suggest` | Query execution is real; NLQ and recommendation UX still require tighter confidence and scope handling. |
| `/trust` | operator | `partial` | `/governance/compliance/summary`, `/governance/privacy/pii-fields`, `/governance/retention/classify`, `/governance/privacy/redact`, `/governance/retention/purge` | Trust Center now drives live retention classification, redaction, compliance refresh, and two-step purge flows. General policy mutation and access-review approvals remain intentionally boundary-limited, and the shell still discloses this surface only in operator mode. |
| `/insights` | operator | `partial` | Launcher-backed metrics plus fallback summaries | Core insight panels render, but some AI/operator diagnostics are still consolidating. |
| `/alerts` | operator | `live` | `/api/v1/alerts`, `/api/v1/alerts/{id}/acknowledge`, `/api/v1/alerts/{id}/resolve`, `/api/v1/alerts/groups`, `/api/v1/alerts/suggestions`, `/api/v1/alerts/rules`, `/api/v1/alerts/stream` | Launcher-backed alert list, lifecycle, grouping, suggestion, rule, and stream routes are live. The route remains operator-facing and is still hidden from primary-user discovery. |
| `/events` | operator | `live` | Event listing and stream parsing routes | Event exploration uses canonical event contracts. |
| `/memory` | operator | `partial` | Memory summary and browse routes | Usable for supported memory endpoints, but still depends on explicit agent and tenant context. |
| `/entities` | operator | `live` | Canonical entity and namespace routes | Read and delete flows are launcher-backed. |
| `/context` | operator | `live` | Canonical context and schema routes | Context explorer aligns with current launcher-backed surfaces. |
| `/fabric` | operator | `preview` | No live fabric metrics route exposed | Topology page is intentionally preview-only and should not be promoted as a primary workflow. |
| `/agents` | operator | `read-only` | Read-only agent catalog surface | Control-plane mutation remains owned outside this product surface. |
| `/settings` | admin | `operator-only` | No user-settings mutation API | Exposed as an explicit admin-only boundary page rather than a fake writable settings experience. |
| `/plugins` | operator | `partial` | Bundled plugin inventory, detail, and toggle routes plus explicit unsupported marketplace/runtime mutations | Listing and toggles work, but marketplace browsing, runtime install/upload, and hot-swap flows remain boundary-guarded. |

## Discovery Rules

- Primary-user discovery should promote only: `/`, `/data`, `/data/new`, `/pipelines`, and `/query`.
- Operator disclosure adds: `/insights`, `/trust`, and `/events`.
- Admin disclosure adds: `/settings` on top of the operator set.
- Operator-only or preview routes such as `/alerts` and `/fabric` must remain direct-link accessible but hidden from primary-user discovery.
- Global search should follow the same shell-role disclosure rules and prefer canonical route names (`Data`, `Pipelines`, `Query`, `Insights`, `Trust`) instead of compatibility aliases (`Collections`, `Workflows`, `SQL Workspace`, `Governance`).
- Compatibility aliases such as `/collections`, `/workflows`, `/dashboard`, and `/sql` remain deep-link-only paths and should not be described as primary workflows in current docs or UX copy.
- Compatibility handoffs stay explicit: `/lineage` redirects to `/data?view=lineage`, `/quality` redirects to `/data?view=quality`, and compatibility wording should point users back to the canonical `/data` workspace instead of inventing separate lineage or quality products.