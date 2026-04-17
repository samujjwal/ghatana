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

## Audit Notes
- Keep transport, service, and persistence boundaries explicit.
- Preserve strict typing and validate upstream/downstream payloads at boundaries.