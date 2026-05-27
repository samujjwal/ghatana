# YAPPC Web Application

`products/yappc/frontend/web` is the primary YAPPC browser application.

## Package Identity

| Field | Value |
| --- | --- |
| Package name | `@ghatana/yappc-web-app` |
| Workspace root | `products/yappc/frontend` (`@ghatana/yappc-frontend`) |
| App path | `products/yappc/frontend/web` |
| Runtime | React Router, React, TypeScript |
| Owner | YAPPC Frontend |

## Scope

- Route definitions, pages, shared components, and frontend services.
- Product-local clients, hooks, and utilities for the browser application.
- Web-specific tests, mocks, Playwright suites, and developer tooling.

## Key Areas

- `src/routes` and `src/pages`: route orchestration and user-facing screens.
- `src/components`: reusable product components.
- `src/services`, `src/hooks`, and `src/clients`: frontend data and behavior layers.
- `docs/e2e-matrix.json`: E2E journey coverage map.
- `scripts/`: web-app-specific validation and inventory checks.

## Naming Rules

- Refer to this package as `@ghatana/yappc-web-app` or the YAPPC web app.
- Refer to the path as `frontend/web`.
- Do not use the historical `app-creator` name for new code, docs, scripts, or package references.

## Audit Notes

- Keep route orchestration thin and push reusable behavior into hooks or services.
- Centralize HTTP parsing and error handling rather than duplicating it across pages.
- Prefer platform packages before adding a product-local component or utility.