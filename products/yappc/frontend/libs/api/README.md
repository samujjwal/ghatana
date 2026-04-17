# YAPPC Frontend API Library

Shared frontend API hooks, clients, and request utilities for YAPPC applications and packages.

## Scope
- Reusable fetch and GraphQL client logic.
- Shared hooks for server-state integration.
- Product-specific API abstractions used across YAPPC frontend packages.

## Key Areas
- `src/hooks`: reusable request and query hooks.
- `src/graphql`: GraphQL client integration.
- Shared request helpers and types.

## Audit Notes
- Keep network concerns centralized and consistently typed.
- Validate external payloads at the edge and keep consumer APIs narrow.