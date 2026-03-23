# Owner: Software-Org — Organization Management System

**Team:** Software-Org Team  
**Slack:** #product-software-org  
**On-call:** Software-Org on-call rotation  
**Architecture lead:** Software-Org Tech Lead  
**Boundary audit score:** 4/10 (2026-03-22) — complex structure, client/engine ratio imbalance

## Responsibility

Software-Org is a **comprehensive organization management system** providing:
- Persona-based access control (Owner, Manager, IC, Admin roles)
- Organization hierarchy management (interactive tree view, restructuring, approvals)
- Role and permission management
- Team and department management with metrics
- Individual contributor tools (Kanban, task management)
- Audit trail and approvals workflow

**Domain boundary:** Software-Org owns the organization management domain. It consumes `platform:java:domain` for base domain types and `platform:java:security` for access control. The frontend uses `@ghatana/design-system` for UI components.

## Architecture

```
products/software-org/
├── client/
│   ├── web/     — Full Vite + React Router v7 app (primary client)
│   └── desktop/ — Early-stage desktop client
├── engine/      — Core business logic (Java)
├── services/    — Service layer
└── libs/        — Internal shared libraries
```

**Structural note (2026-03-22 audit):** `client/` (848 items) vs `engine/` (41 items) = 20:1 ratio. The engine is underdeveloped relative to the client surface. This is intentional for the current TypeScript-first phase but the engine gap should be closed as backend business logic grows.

**Rename target:** `client/web/` may be renamed to `apps/web/` in a future cleanup to align with monorepo conventions. Update this file when that rename occurs.

## Consumers

Software-Org is primarily end-user-facing. No other products should depend on Software-Org's internal modules.

## Known Issues

- `OWNER.md` was missing as of the 2026-03-22 boundary audit (score 4/10, accountability gap)
- `engine/` (41 items) should grow to carry more business logic currently embedded in the frontend
- `client/desktop/` is early-stage; either develop or archive in next planning cycle
