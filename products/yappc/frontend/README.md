# YAPPC Frontend Workspace

`products/yappc/frontend` is the TypeScript workspace for YAPPC browser applications and product-local frontend libraries.

## Canonical Names

| Layer | Path | Package name | Owner | Purpose |
| --- | --- | --- | --- | --- |
| Frontend workspace | `products/yappc/frontend` | `@ghatana/yappc-frontend` | YAPPC Frontend | pnpm workspace root for web app, frontend libs, packages, examples, and shared tooling. |
| Primary web app | `products/yappc/frontend/web` | `@ghatana/yappc-web-app` | YAPPC Frontend | React Router web application for YAPPC product workflows. |
| Product-local libraries | `products/yappc/frontend/libs/*` | mixed product-local package names | Owning feature teams | YAPPC-only frontend libraries that are not platform packages. |
| Platform libraries | `platform/typescript/*` | `@ghatana/*` | Platform | Shared packages used across products; prefer these before creating product-local equivalents. |

`app-creator` is a historical name. New docs, scripts, package references, and code paths must use `frontend` and `frontend/web`.

## Quick Start

```bash
# From the repository root
cd products/yappc/frontend
pnpm install
pnpm dev
```

The web app package can also be targeted directly:

```bash
pnpm --filter @ghatana/yappc-web-app dev
pnpm --filter @ghatana/yappc-web-app build
```

## Common Commands

| Command | Scope |
| --- | --- |
| `pnpm dev` | Starts the primary web app through the workspace root. |
| `pnpm build:web` | Builds `@ghatana/yappc-web-app`. |
| `pnpm test` | Runs the frontend workspace verification slice. |
| `pnpm --filter @ghatana/yappc-web-app test:regression` | Runs the web app grouped regression suite. |
| `pnpm --filter @ghatana/yappc-web-app test:contract` | Runs route, E2E matrix, API, and i18n contract checks. |

## Ownership Rules

- Use `frontend/web` for routes, app shell, page components, browser services, generated clients, and web-specific tests.
- Use `frontend/libs/*` only for reusable YAPPC product-local code with more than one consumer.
- Use `platform/typescript/*` for cross-product primitives, design system, canvas, forms, API helpers, theme, tokens, i18n, and platform utilities.
- Do not introduce new `app-creator` paths or package names.
- Do not publish YAPPC product-local libraries under new `@ghatana/*` platform package names.