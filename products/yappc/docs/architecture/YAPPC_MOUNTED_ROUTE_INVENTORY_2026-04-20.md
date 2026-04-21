# Yappc Mounted Route Inventory

Date: 2026-04-20
Authoritative source: `products/yappc/frontend/web/src/routes.ts`
Purpose: Canonical inventory of what the mounted Yappc web product actually ships today.

## Why this exists

- The mounted route tree is small and auditable.
- The page/component surface under `web/src/pages/**` is much larger and must not be treated as shipped product scope.
- This document closes the route-inventory gap called out in the 2026-04-20 product audit and should be updated whenever `web/src/routes.ts` changes.

## Mounted route tree

### Root routes

- `/` -> `routes/dashboard.tsx`
  - Status: mounted
  - Classification: partial, product entry route
- `/login` -> `routes/login.tsx`
  - Status: mounted
  - Classification: working auth route
- `/onboarding` -> `routes/onboarding.tsx`
  - Status: mounted
  - Classification: working bootstrap route with API-backed persistence
- `*` -> `routes/not-found.tsx`
  - Status: mounted
  - Classification: error route

### App shell routes

- `/workspaces` -> `routes/app/workspaces.tsx`
  - Status: mounted
  - Classification: working with partial stats truthfulness
- `/projects` -> `routes/app/projects.tsx`
  - Status: mounted
  - Classification: working after project-create remediation
- `/profile` -> `routes/profile.tsx`
  - Status: mounted
  - Classification: truthful read-only route backed by `/api/auth/me`
- `/settings` -> `routes/settings.tsx`
  - Status: mounted
  - Classification: truthful workspace settings route limited to supported fields

### Project shell routes

- `/p/:projectId` -> `routes/app/project/index.tsx`
  - Status: mounted
  - Classification: canonical project home
- `/p/:projectId/canvas` -> `routes/app/project/canvas.tsx`
  - Status: mounted
  - Classification: mounted authoring route, still carries save/sync truth work
- `/p/:projectId/canvas-workspace` -> `routes/app/project/canvas-workspace.tsx`
  - Status: mounted
  - Classification: mounted secondary canvas surface
- `/p/:projectId/preview` -> `routes/app/project/preview.tsx`
  - Status: mounted
  - Classification: truthful unavailable/external-preview state, not a local preview runtime
- `/p/:projectId/deploy` -> `routes/app/project/deploy.tsx`
  - Status: mounted
  - Classification: release-planning surface, not a live deploy console
- `/p/:projectId/settings` -> `routes/app/project/settings.tsx`
  - Status: mounted
  - Classification: truthful project settings limited to supported fields
- `/p/:projectId/lifecycle` -> `routes/app/project/lifecycle.tsx`
  - Status: mounted
  - Classification: working lifecycle explorer with canonical phase vocabulary

## Mounted route count

- Root-mounted pages: 4
- App-shell pages: 4
- Project-shell pages: 7
- Total mounted route handlers excluding layouts: 15

## Latent page surface not mounted

The repository currently contains a much larger latent UI surface under `products/yappc/frontend/web/src/pages/**`.

See also: `products/yappc/docs/architecture/YAPPC_LATENT_PAGE_SURFACE_2026-04-20.md`

- Count observed during audit: 98 files under `web/src/pages/**`
- These include broad areas such as:
  - `pages/operations/**`
  - `pages/development/**`
  - `pages/security/**`
  - `pages/collaboration/**`
  - `pages/bootstrapping/**`
  - `pages/initialization/**`
  - `pages/admin/**`

These files are not mounted by `web/src/routes.ts` and must not be treated as shipped product behavior without explicit route registration and contract validation.

## Route classifications for remediation tracking

- Complete enough to stay mounted:
  - `/login`
  - `/onboarding`
  - `/projects`
  - `/profile`
  - `/settings`
  - `/p/:projectId`
  - `/p/:projectId/settings`
  - `/p/:projectId/lifecycle`
- Mounted but still under active remediation:
  - `/workspaces`
  - `/p/:projectId/canvas`
  - `/p/:projectId/canvas-workspace`
- Mounted with truthful scope reduction rather than full backing:
  - `/p/:projectId/preview`
  - `/p/:projectId/deploy`
- Hidden/unmounted latent product surface:
  - everything under `web/src/pages/**` that is not referenced by `web/src/routes.ts`

## Maintenance rule

- Any new mounted route must be added to this inventory in the same change as `web/src/routes.ts`.
- Any page under `web/src/pages/**` that becomes product-critical must either be mounted explicitly or archived/documented as latent.
- Keep the latent page inventory document in sync when mounted reachability changes.