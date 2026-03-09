# DCMaar – Guardian App – User Manual

## 1. Audience

This manual is for engineers and operators working with the Guardian parental-control system within the DCMaar ecosystem.

## 2. Basic Usage

1. Use `pnpm` workspace commands from the Guardian root to build, test, and run components.
2. For dashboards and apps, follow component-specific docs under `apps/*`.
3. For backend services, follow service-specific docs under `backend` and deployment scripts.
4. For end-to-end deployment (server-side and client-side, parent vs child), see `docs/guides/GUARDIAN_DEPLOYMENT.md`.

## 3. Best Practices

- Treat this workspace as the coordination point for Guardian builds and deployments.
- Reuse shared libraries in `packages/*` / `libs/*` rather than duplicating logic.

## 4. Developer Quickstart

1. Checkout the repo and install dependencies:

```bash
git clone <repo-url>
cd products/dcmaar/apps/guardian
pnpm install
```

2. Build and run the local development stack (development environment):

```bash
cp .env.example .env
pnpm build
pnpm deploy:dev
```

3. Open the Parent Dashboard at `http://localhost:8080` and the backend API at `http://localhost:3000` (defaults).

4. Load the browser extension for local testing:

- Build the extension: `pnpm --filter @yappc/guardian-browser-extension build`
- In Chrome, open `chrome://extensions` → Developer mode → Load unpacked → select `apps/guardian/apps/browser-extension/dist/chrome`.

## 5. How Parents Use Guardian (operator-facing summary)

- Parents authenticate using the Parent Dashboard or Parent Mobile app.
- They create child profiles and register devices (mobile, desktop, browser extension).
- Policies are created and scheduled via the dashboard; enforcement happens on agents and extensions.

## 6. Support and Contacts

- For operational issues, consult `docs/operations/OPERATIONS.md` and `docs/usage/KNOWN_ISSUES_TROUBLESHOOTING.md`.
- For contract or integration questions, see `docs/GUARDIAN_ARCHITECTURE_AND_CONTRACTS.md` and raise issues/PRs in the repository.

This user manual now contains developer quickstart steps, extension load instructions, and support pointers.
