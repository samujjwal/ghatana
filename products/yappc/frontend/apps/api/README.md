# YAPPC Frontend API

TypeScript backend for YAPPC frontend-facing routes, GraphQL resolvers, background jobs, and service orchestration.

## Scope
- HTTP and GraphQL entry points for the YAPPC frontend surface.
- Product services, middleware, persistence adapters, and operational jobs.
- Local test harnesses and integration coverage for frontend-facing workflows.

## Key Areas
- `src/routes`: REST and route handlers.
- `src/graphql`: GraphQL resolvers and schema-facing logic.
- `src/services`: product services and orchestration.

## API Surface Decision Record

Before adding a new route or GraphQL operation, consult the canonical surface assignment:

**[docs/API_SURFACE_CANONICALIZATION.md](../../../../docs/API_SURFACE_CANONICALIZATION.md)**

Rules in brief:
- **REST**: workspaces, projects, auth, lifecycle commands, code generation, intent/shape.
- **GraphQL**: workflows, requirements, approvals, AI agents, AI insights, versioning, devsecops, compliance, rate limiting.
- **Never** add a REST route for a GraphQL-owned domain.
- **Never** add a GraphQL type for a REST-owned domain.
- Real-time / streaming: WebSocket or SSE, not GraphQL subscriptions.

## Audit Notes
- Keep transport, service, and persistence boundaries explicit.
- Preserve strict typing and validate upstream/downstream payloads at boundaries.