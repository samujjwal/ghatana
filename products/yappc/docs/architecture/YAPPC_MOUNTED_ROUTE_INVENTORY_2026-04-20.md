# Yappc Mounted Route Inventory

Date: 2026-04-21
Authoritative source: `products/yappc/frontend/web/src/routes.ts`
Purpose: Canonical inventory of what the mounted Yappc web product actually ships today.

## Why this exists

- The mounted route tree is small and auditable.
- The page/component surface under `web/src/pages/**` is much larger and must not be treated as shipped product scope.
- This document closes the route-inventory gap called out in the 2026-04-20 product audit and should be updated whenever `web/src/routes.ts` changes.

## Mounted route tree

### Standalone routes (outside layout wrappers)

- `/preview/builder` -> `routes/preview-builder.tsx`
  - Status: mounted
  - Classification: standalone builder preview runtime, loaded in iframe by LivePreviewPanel

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

### Project shell routes (under `/p/:projectId`)

**8-Phase Lifecycle Navigation (canonical IA routes):**
- `/p/:projectId` (index) -> `routes/app/project/index.tsx`
  - Status: mounted
  - Classification: canonical project home, redirects to intent phase
- `/p/:projectId/intent` -> `routes/app/project/intent.tsx`
  - Status: mounted
  - Classification: Phase 1 — capture goals and problems
- `/p/:projectId/shape` -> `routes/app/project/shape.tsx`
  - Status: mounted
  - Classification: Phase 2 — define solution via canvas
- `/p/:projectId/validate` -> `routes/app/project/validate.tsx`
  - Status: mounted
  - Classification: Phase 3 — review and gate requirements
- `/p/:projectId/generate` -> `routes/app/project/generate.tsx`
  - Status: mounted
  - Classification: Phase 4 — AI-powered code and doc generation
- `/p/:projectId/run` -> `routes/app/project/run.tsx`
  - Status: mounted
  - Classification: Phase 5 — execute pipelines and deployments
- `/p/:projectId/observe` -> `routes/app/project/observe.tsx`
  - Status: mounted
  - Classification: Phase 6 — metrics, incidents, and live signals
- `/p/:projectId/learn` -> `routes/app/project/learn.tsx`
  - Status: mounted
  - Classification: Phase 7 — retrospectives and AI insights
- `/p/:projectId/evolve` -> `routes/app/project/evolve.tsx`
  - Status: mounted
  - Classification: Phase 8 — plan the next cycle

**Project Configuration:**
- `/p/:projectId/settings` -> `routes/app/project/settings.tsx`
  - Status: mounted
  - Classification: truthful project settings limited to supported fields, accessible via settings icon

**Legacy Routes (preserved for deep-links, may be removed in future cycle):**
- `/p/:projectId/canvas` -> `routes/app/project/canvas.tsx`
  - Status: mounted (legacy)
  - Classification: mounted authoring route, still carries save/sync truth work
- `/p/:projectId/preview` -> `routes/app/project/preview.tsx`
  - Status: mounted (legacy)
  - Classification: truthful unavailable/external-preview state, not a local preview runtime
- `/p/:projectId/deploy` -> `routes/app/project/deploy.tsx`
  - Status: mounted (legacy)
  - Classification: release-planning surface, not a live deploy console
- `/p/:projectId/lifecycle` -> `routes/app/project/lifecycle.tsx`
  - Status: mounted (legacy)
  - Classification: working lifecycle explorer with canonical phase vocabulary

### Admin routes (OWNER/ADMIN only, capability-gated)

- `/admin/prompt-versions` -> `routes/app/admin/prompt-versions.tsx`
  - Status: mounted
  - Classification: admin route, capability-gated via useCapabilityGate
- `/admin/ab-testing` -> `routes/app/admin/ab-testing.tsx`
  - Status: mounted
  - Classification: admin route, capability-gated via useCapabilityGate
- `/admin/feature-flags` -> `routes/app/admin/feature-flags.tsx`
  - Status: mounted
  - Classification: admin route, capability-gated via useCapabilityGate

## Mounted route count

- Standalone routes: 1
- Root-mounted pages: 4
- App-shell pages: 4
- Project-shell pages: 13 (9 canonical IA phases + 1 settings + 3 legacy)
- Admin pages: 3
- Total mounted route handlers excluding layouts: 25

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
  - `/p/:projectId` (index, redirects to intent)
  - `/p/:projectId/intent`
  - `/p/:projectId/shape`
  - `/p/:projectId/validate`
  - `/p/:projectId/generate`
  - `/p/:projectId/run`
  - `/p/:projectId/observe`
  - `/p/:projectId/learn`
  - `/p/:projectId/evolve`
  - `/p/:projectId/settings`
  - `/admin/prompt-versions`
  - `/admin/ab-testing`
  - `/admin/feature-flags`
- Mounted but still under active remediation:
  - `/workspaces`
- Legacy routes (preserved for deep-links, may be removed in future cycle):
  - `/p/:projectId/canvas`
  - `/p/:projectId/preview`
  - `/p/:projectId/deploy`
  - `/p/:projectId/lifecycle`
- Hidden/unmounted latent product surface:
  - everything under `web/src/pages/**` that is not referenced by `web/src/routes.ts`

## Maintenance rule

- Any new mounted route must be added to this inventory in the same change as `web/src/routes.ts`.
- Any page under `web/src/pages/**` that becomes product-critical must either be mounted explicitly or archived/documented as latent.
- Keep the latent page inventory document in sync when mounted reachability changes.