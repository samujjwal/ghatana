# YAPPC Web Application

Primary web application for YAPPC product workflows, canvas experiences, and operational interfaces.

## Scope
- Route definitions, pages, shared components, and frontend services.
- Product-local clients, hooks, and utilities for the browser application.
- Web-specific tests, mocks, and developer tooling.

## Key Areas
- `src/routes` and `src/pages`: route orchestration and user-facing screens.
- `src/components`: reusable product components.
- `src/services`, `src/hooks`, and `src/clients`: frontend data and behavior layers.

## Audit Notes
- Keep route orchestration thin and push reusable behavior into hooks or services.
- Centralize HTTP parsing and error handling rather than duplicating it across pages.