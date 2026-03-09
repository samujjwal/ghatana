# DCMaar – Guardian App – Design & Architecture

## 1. Purpose

`@dcmaar/guardian` is the **parental control system** built on the DCMaar platform. It orchestrates backend services and dashboard/frontends to monitor and manage child activity according to policy.

## 2. Responsibilities

- Coordinate Guardian backend services and dashboards.
- Provide build, test, and deployment workflows for Guardian components.
- Integrate with the wider DCMaar ecosystem (agent, server, desktop, extension) via shared contracts.

## 3. Architectural Position

- Monorepo umbrella package with workspaces for:
  - `apps/*` – frontend applications (e.g., dashboards).
  - `packages/*` and `libs/*` – shared libraries and services.
  - `backend` – Guardian backend services.
- Uses scripts (`build.sh`, `deploy.sh`) to build and deploy specific components.

This document is self-contained and summarizes the architecture and role of the Guardian app umbrella.

## 4. Components (concise)

- `apps/backend`: Guardian backend services (policy engine, ingest adapters, API).
- `apps/parent-dashboard`: Parent-facing web UI (React + Vite).
- `apps/browser-extension`: Browser extension for child devices (multi-browser artifacts).
- `apps/parent-mobile` / `apps/child-mobile`: Mobile applications built with React Native.
- `apps/agent-desktop`: Desktop agent plugin (Rust crate) integrated into DCMAAR agent-daemon.
- `packages/*` / `libs/*`: Shared libraries, connectors and contract adapters.

## 5. Quickstart (developer)

1. Clone the repository and change to the Guardian workspace:

```bash
git clone <repo-url>
cd products/dcmaar/apps/guardian
```

2. Install workspace dependencies (pnpm workspace):

```bash
pnpm install
```

3. Build the extension and dashboard for local testing:

```bash
pnpm --filter @yappc/guardian-browser-extension build
pnpm --filter @yappc/guardian-parent-dashboard build
```

4. Run local compose stack for development:

```bash
cp .env.example .env
pnpm deploy:dev
```

## 6. Maintenance Notes

- Keep `docs/GUARDIAN_ARCHITECTURE_AND_CONTRACTS.md` and integration guides in sync with any contract changes.
- When adding a new service or agent, update the Components list and the Quickstart commands above.
