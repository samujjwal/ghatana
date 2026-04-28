# YAPPC API Surface Canonicalization — GraphQL ↔ REST

> **SIMP-Y9 / K-Y3 / F-Y041 resolution** — declares the canonical transport per domain concept.

---

## Canonical Surface Assignment

| Domain | Canonical | Reasoning |
|---|---|---|
| **Workspaces** | REST | CRUD only; no real-time; already fully in OpenAPI spec |
| **Projects** | REST | CRUD + setup suggestion; no real-time; fully in OpenAPI spec |
| **Authentication / Session** | REST | Stateless token exchange; inappropriate for GraphQL |
| **Lifecycle phases** | REST | Command-style phase advance; simple request/response |
| **Intent capture / analysis** | REST | LLM-backed; streaming over REST SSE where needed |
| **Code generation / validation** | REST | Long-running job model; webhook/SSE for progress |
| **Requirements + Approvals** | GraphQL | Complex nested approval graph; subscription for approval events |
| **Workflow orchestration** | GraphQL | DAG traversal queries; subscription for status changes |
| **AI agents** | GraphQL | Preference graph; subscriptions for agent streaming |
| **DevSecOps** | GraphQL | Graph-shaped data (findings, dependencies, CVE links) |
| **Versioning / diff** | GraphQL | Tree-shaped history; complex field selection |
| **Rate limiting** | GraphQL | Admin graph; upgrade request approval flows |
| **Compliance** | GraphQL | Policy graph queries |
| **Real-time progress** | WebSocket / SSE | Not GraphQL subscription; use existing SSE infrastructure |

---

## Overlap Areas to Eliminate

The following concepts appear in both GraphQL and REST. The canonical surface is declared below:

| Concept | GraphQL location | REST location | Canonical | Action |
|---|---|---|---|---|
| Workflow create/update/cancel | `workflow.graphql` | None in OpenAPI | **GraphQL** | Keep GraphQL; do NOT add REST route for workflows |
| Requirements approval | `requirements-approvals.graphql` | None in OpenAPI | **GraphQL** | Keep GraphQL |
| AI preferences | `ai-agents.graphql` | None in OpenAPI | **GraphQL** | Keep GraphQL |
| Project CRUD | None in GraphQL | `/api/projects` | **REST** | Do NOT add GraphQL project queries |
| Workspace CRUD | None in GraphQL | `/api/workspaces` | **REST** | Do NOT add GraphQL workspace queries |
| Phase advance | None in GraphQL | `/api/v1/lifecycle/advance` | **REST** | Keep REST |

---

## Rules Going Forward

1. **Never add a REST route for a concept already owned by GraphQL** (workflows, approvals, AI preferences, versioning, compliance, devsecops).
2. **Never add a GraphQL type for a concept already owned by REST** (workspaces, projects, auth, lifecycle commands, code generation).
3. **Subscriptions are GraphQL only** — do not implement real-time via REST polling for subscription-owned domains.
4. **New concepts**: default to REST unless the data model is inherently graph-shaped or requires subscriptions.
5. **Generated clients** (SIMP-Y19): the REST surface uses generated OpenAPI clients; the GraphQL surface uses generated TypeScript types from `graphql-codegen`.

---

## Implementation Checklist

- [x] Audit `frontend/apps/api/src/graphql/schemas/` for REST-owned concepts in GraphQL queries — **confirmed clean**: no `project`, `workspace`, `auth`, or `lifecycle` CRUD in any GraphQL schema.
- [x] Audit `docs/api/openapi.yaml` for GraphQL-owned concepts in REST paths — **two residual overlaps documented below; backend removal tracked in BACKEND_IMPLEMENTATION_BACKLOG.md**.
- [x] Enforce in ESLint: no direct `fetch('/api/...')` for GraphQL-owned domains — rule added to `eslint-rules/` as `no-rest-for-graphql-domains`; see SIMP-Y19.
- [x] Document this file path in the API gateway README — see `frontend/apps/api/README.md` §API Surface Decision Record.

## Residual REST ↔ GraphQL Overlaps (backend removal required)

| Route | GraphQL canonical | Action |
|---|---|---|
| `GET /api/v1/approvals/pending` | `approvalRequests(projectId, status)` query | Remove REST route after confirming no non-GraphQL consumers |
| `GET /api/devsecops/*` (overview, ai-insights, anomaly-alerts, items, phases) | `devsecops.graphql` schema | Remove REST routes; migrate any REST consumers to GraphQL |

Both removals are backend tasks tracked in `docs/BACKEND_IMPLEMENTATION_BACKLOG.md` under **SIMP-Y9 REST overlap removal**.
