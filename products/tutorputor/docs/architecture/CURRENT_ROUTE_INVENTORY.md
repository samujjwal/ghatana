# TutorPutor Current Route Inventory

> Document type: Route inventory from runtime registration source
> Generated: 2026-04-27T08:48:36-07:00
> Source of truth: `products/tutorputor/services/tutorputor-platform/src/setup.ts`
> Commit SHA: 8098a7043953aafe7bf9d8cb0d8738381d6b2ddd

---

## Module Registration Inventory

| Registered module/router | Registration prefix | Effective route family |
|---|---|---|
| contentModule | `/api` | `/api/v1/modules`, `/api/content-studio/*`, `/api/generation/*` |
| learningModule | `/api/v1` | `/api/v1/learning`, `/api/v1/enrollments`, `/api/v1/pathways`, `/api/v1/assessments` |
| userModule | `/api/v1` | `/api/v1/teacher/*`, `/api/v1/admin/*` |
| collaborationModule | `/api/v1/collaboration` | `/api/v1/collaboration/*` |
| engagementModule | `/api/v1` | `/api/v1/gamification/*`, `/api/v1/social/*`, `/api/v1/credentials/*` |
| integrationModule | `/api/v1/integration` | `/api/v1/integration/*` |
| tenantModule | `/api/v1/tenant` | `/api/v1/tenant/*` |
| authModule | `/api/v1/auth` | `/api/v1/auth/*` |
| aiModule | `/api/v1/ai` | `/api/v1/ai/*` |
| autoRevisionModule | `/api/v1/auto-revision` | `/api/v1/auto-revision/*` |
| contentNeedsModule | `/api/v1/content-needs` | `/api/v1/content-needs/*` |
| simulationModule | internal module prefix | `/api/sim-author/*` |
| searchModule | `/api/v1/search` | `/api/v1/search`, `/api/v1/search/autocomplete` |
| registerKernelRegistryRoutes | internal registration | `/api/v1/plugins/*` |
| vrRoutes | `/api/v1/vr` | `/api/v1/vr/*` |
| notificationRoutes | `/api/v1/notifications` | `/api/v1/notifications/*` |
| paymentRoutes | `/api/v1` | `/api/v1/payments/*` |
| featureFlagsModule | `/api/v1/admin` | `/api/v1/admin/feature-flags*` |
| registerObservabilityRoutes | `/api/v1/admin/observability` | `/api/v1/admin/observability/*` |

---

## Auth Guard Scope

JWT onRequest guard applies to these route families:

- `/api/v1/*`
- `/api/content-studio/*`
- `/api/generation/*`

Auth guard public exemptions configured in setup:

- `/api/v1/auth/sso/*`
- `/api/v1/auth/health`
- `/api/v1/auth/refresh`
- `/api/v1/integration/billing/webhook`
- `/api/content-studio/health`
- LTI public routes:
  - `GET /api/v1/integration/lti/jwks`
  - `GET /api/v1/integration/lti/config/:platformId`
  - `POST /api/v1/integration/lti/launch`
  - `POST /api/v1/integration/lti/deep-linking`
  - `POST /api/v1/integration/lti/grade-passback`

---

## Verification Evidence

Public-route and trusted-proxy behavior is covered by:

- `products/tutorputor/services/tutorputor-platform/src/__tests__/p0-3-fail-closed-auth.integration.test.ts`

Recent run result:

- PASS: 20/20 tests in the suite, including explicit allowlist and trusted-proxy secret checks.

---

## Regeneration Procedure

1. Inspect module registration in `src/setup.ts`.
2. Update this inventory when route prefixes or guarded/public behavior changes.
3. Run:
   - `pnpm exec vitest run src/__tests__/p0-3-fail-closed-auth.integration.test.ts`
4. Sync checklist state in `tutorputor_tasks.md`.
