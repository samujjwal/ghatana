# DCMaar – Guardian App – Technical Reference

## 1. Overview

This reference summarizes key technical aspects of the Guardian product workspace.

## 2. Core Concepts (Conceptual)

- Guardian as a multi-app workspace with backend, dashboard(s), and shared libs.
- Unified build/test/deploy scripts that operate across components via pnpm filters.

## 3. Repo Layout and Key Paths

- `apps/` – application entrypoints (parent-dashboard, browser-extension, parent/child mobile, agent-desktop)
- `backend/` – backend services, migrations, and runtime configs
- `packages/` / `libs/` – shared libraries and utilities
- `scripts/` – build/deploy helper scripts
- `docs/` – product docs (this folder)

## 4. Build & Run (Quick Commands)

- Install dependencies (root):

```bash
pnpm install
```

- Build all packages (local):

```bash
pnpm -w build
```

- Build a single package (example: browser extension):

```bash
pnpm --filter @yappc/guardian-browser-extension build
```

## 5. Contracts and Schemas

- Platform contracts (DCMAAR) live under `products/dcmaar/contracts/proto-core/dcmaar/v1`.
- Guardian-specific contracts (if present) live under `apps/guardian/contracts` or `packages/guardian-contracts`.

## 6. CI and Quality Gates

- CI must run workspace linting and tests. Ensure `pnpm -w test` and `pnpm -w lint` are green before merging.

This technical reference now contains practical commands and repo navigation details for engineers consuming the Guardian workspace.
