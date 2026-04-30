# Owner: YAPPC API Module

**Team:** YAPPC Backend Team
**Slack:** #yappc-core
**Parent ownership:** `products/yappc/core/OWNER.md`
**Last Updated:** 2026-04-29

## Module Purpose

Consolidated YAPPC API module providing the HTTP REST and GraphQL surface. Contains:

- `YappcApiController` — top-level HTTP routing for agents, workflows, RAG, and search
- `AgentController` — agent listing, lookup, execution, and health endpoints
- `WorkflowController` — workflow CRUD, lifecycle transitions (start/pause/resume/stop/delete)
- `SearchController` / `RagController` — semantic search and retrieval-augmented generation
- GraphQL schema and resolvers (under `src/graphql/`)
- OpenAPI parity validation task (`validateOpenApiParity`) wired to `check`

## Ownership Map

| Area | Path | Owned By |
|------|------|---------|
| REST controllers | `src/rest/java/` | YAPPC Backend |
| GraphQL schema/resolvers | `src/graphql/java/` | YAPPC Backend |
| Main controller | `src/main/java/` | YAPPC Backend |
| Integration tests | `src/test/java/` | YAPPC Backend |

## Dependency Rules

- Depends on `yappc-services`, `yappc-domain-impl`, `yappc-shared` for business logic.
- Must not contain business logic — delegates entirely to service interfaces.
- `validateOpenApiParity` task validates that every route in `docs/api/route-manifest.yaml`
  appears in `docs/api/openapi.yaml`. Fails the `check` lifecycle on drift.

## Production Stability Notes

- OpenAPI parity task reads YAML files at build time using line-based parsing.
  For full fidelity, consider migrating to a YAML parser library (e.g., SnakeYAML) in the task.
- All controller methods require `X-Tenant-ID` header for scoped operations; missing headers return 400.
- Audit logging is delegated to `AuditLogger` — verify the binding in `YappcApiModule`.
