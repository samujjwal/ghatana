# TutorPutor Current State — June 2026

> **Document type**: Current State  
> **Last updated**: 2026-06-20 (WS1–WS8 + all partial/planned items implemented)  
> **Status keys**: `implemented` | `partial` | `stubbed` | `disabled` | `planned`

This is the **single source of truth** for what TutorPutor does and does not do today. Architecture goals and aspirational features are documented separately in `TARGET_ARCHITECTURE.md`.

---

## 1. Product Overview

TutorPutor is an AI-powered adaptive learning platform. It is built as a monorepo product with:

- **`tutorputor-platform`** — Fastify/Node.js backend (single monolith replacing 28 former microservices)
- **`tutorputor-web`** — React 19 student-facing SPA
- **`tutorputor-admin`** — React admin dashboard for content authoring
- **`tutorputor-core`** — Shared Prisma schema, generated client, and seed scripts
- **`tutorputor-simulation`** — Simulation engine library
- **`contracts/v1`** — TypeScript type contracts shared between frontend and backend

---

## 2. API Contract State

### Route Prefix Strategy (Canonical as of 2026-03-23)

All API routes are exposed under `/api/v1/`. The legacy mixed `/api/...` and `/api/v1/...` patterns have been unified.

| Route Namespace                                    | Backend Prefix         | Status        |
| -------------------------------------------------- | ---------------------- | ------------- |
| `/api/v1/modules`                                  | `contentModule`        | `implemented` |
| `/api/v1/learning`                                 | `learningModule`       | `implemented` |
| `/api/v1/enrollments`                              | `learningModule`       | `implemented` |
| `/api/v1/pathways`                                 | `learningModule`       | `implemented` |
| `/api/v1/assessments`                              | `learningModule`       | `implemented` |
| `/api/v1/teacher`                                  | `userModule`           | `implemented` |
| `/api/v1/collaboration`                            | `collaborationModule`  | `implemented` |
| `/api/v1/gamification`                             | `engagementModule`     | `implemented` |
| `/api/v1/search`                                   | `searchModule`         | `implemented` |
| `/api/v1/auth`                                     | `authModule`           | `implemented` |
| `/api/v1/ai`                                       | `aiModule`             | `implemented` |
| `/api/sim-author`                                  | `simulationModule`     | `implemented` |
| `/content-studio`                                  | `contentModule`        | `implemented` |
| `/content-studio/experiences/:id/comprehensive`    | `contentModule`        | `implemented` |
| `/content-studio/experiences/:id/examples`         | `contentModule`        | `implemented` |
| `/content-studio/experiences/:id/simulations`      | `contentModule`        | `implemented` |
| `/content-studio/experiences/:id/animations`       | `contentModule`        | `implemented` |
| `/api/sim-author/manifests/:id/link-claim`         | `simulationModule`     | `implemented` |
| `/api/v1/vr/labs`                                  | `vrRoutes`             | `implemented` |
| `/api/v1/vr/sessions`                              | `vrRoutes`             | `implemented` |
| `/api/v1/notifications`                            | `notificationRoutes`   | `implemented` |
| `/api/v1/tenant/sso-providers`                     | `tenantModule`         | `implemented` |
| `/api/v1/plugins`                                  | `kernelRegistryRoutes` | `implemented` |
| `/content-studio/experiences/:id/automation-rules` | `contentModule`        | `implemented` |

---

## 3. Authentication Architecture

### Current Auth Model: JWT + SSO (OIDC)

| Aspect                         | Status        | Notes                                                                                    |
| ------------------------------ | ------------- | ---------------------------------------------------------------------------------------- |
| JWT validation (fastify-jwt)   | `implemented` | HS256, 15-min access tokens                                                              |
| SSO login via OIDC             | `implemented` | Provider config per tenant                                                               |
| JWT-based request identity     | `implemented` | After remediation — no longer raw-header only                                            |
| Tenant isolation in JWT claims | `implemented` | `tenantId` in every token                                                                |
| Role-based access control      | `partial`     | Role in JWT claims; granular permission checks are planned                               |
| Admin dev bypass mode          | `partial`     | Controlled via `VITE_DEV_AUTH_BYPASS=true` env var; not triggerable by production routes |
| Per-tenant identity providers  | `implemented` | `IdentityProvider` model, SSO callbacks                                                  |

### What Changed (WS2 remediation)

- **`request-helpers.ts`** and **`requestContext.ts`** now extract identity from JWT (`request.user`) via fastify-jwt's `authenticate` hook, not from raw `x-user-id`/`x-user-role` headers.
- Missing identity returns `401` (unauthorized), not a default `"anonymous"` value.
- The admin app `useAuth.ts` dev bypass is now gated by `VITE_DEV_AUTH_BYPASS=true` environment variable.
- **`useAuth.ts`** now calls `setAuthToken(token)` from `contentStudioApi` on every auth state transition, ensuring the admin API adapter always holds a fresh token. Logout clears the token and uses the correct `/api/v1/auth/logout` endpoint.

---

## 4. Backend Module State

| Module            | File                       | Status        | Notes                                                      |
| ----------------- | -------------------------- | ------------- | ---------------------------------------------------------- |
| Content (modules) | `modules/content/`         | `implemented` | CRUD, CMS                                                  |
| Content Studio    | `modules/content/studio/`  | `implemented` | Experience CRUD, AI generation, validation, publish        |
| Learning          | `modules/learning/`        | `implemented` | Dashboard, enrollment, pathways, assessments, analytics    |
| Collaboration     | `modules/collaboration/`   | `implemented` | Threads, posts                                             |
| User / Teacher    | `modules/user/`            | `implemented` | Classroom management                                       |
| Engagement        | `modules/engagement/`      | `implemented` | Gamification, social, credentials                          |
| Search            | `modules/search/`          | `implemented` | Full-text search, autocomplete (previously not registered) |
| Auth              | `modules/auth/`            | `implemented` | SSO, JWT, current-user                                     |
| AI                | `modules/ai/`              | `implemented` | Tutor query, question generation                           |
| Simulation        | `modules/simulation/`      | `implemented` | NL authoring, manifest generation/refinement               |
| Auto Revision     | `modules/auto-revision/`   | `partial`     | Worker-based, may not have all paths stable                |
| Content Needs     | `modules/content-needs/`   | `implemented` | `batch-analyze` now parallel via `Promise.all`             |
| Integration       | `modules/integration/`     | `partial`     | External platform connectors                               |
| Tenant            | `modules/tenant/`          | `implemented` | Tenant config + full SSO provider CRUD                     |
| Kernel Registry   | `modules/kernel-registry/` | `implemented` | Persistent via `KernelPlugin` Prisma model (was in-memory) |
| VR                | `modules/vr/`              | `implemented` | Labs, sessions, analytics via `vr-routes.ts`               |
| Notifications     | `modules/notifications/`   | `implemented` | Read, mark-read, preferences via `notifications/index.ts`  |
| Payments / LTI    | `modules/payments`, `lti`  | `planned`     | Infrastructure modeled, routes minimal                     |

---

## 5. Frontend Application State

### tutorputor-web (Student App)

| Page / Feature               | Status        | Notes                                              |
| ---------------------------- | ------------- | -------------------------------------------------- |
| Dashboard                    | `implemented` | Calls `/api/v1/learning/dashboard`                 |
| Module list                  | `implemented` | Calls `/api/v1/modules`                            |
| Module detail                | `implemented` | Calls `/api/v1/modules/:slug`                      |
| Pathways                     | `implemented` | Calls `/api/v1/pathways`                           |
| Assessments                  | `implemented` | Calls `/api/v1/assessments`                        |
| Search                       | `implemented` | Calls `/api/v1/search`                             |
| Marketplace                  | `partial`     | UI exists, backend commerce routes partial         |
| Collaboration                | `implemented` | Threads and posts                                  |
| Gamification                 | `implemented` | Progress, leaderboard, achievements                |
| AI Tutor                     | `implemented` | Calls `/api/v1/ai/tutor/query`                     |
| Content Generation (student) | `implemented` | `useContentGeneration` hook now calls real job API |
| Simulation Studio            | `implemented` | lazy route **re-enabled** (was disabled)           |
| Animation Editor             | `implemented` | lazy route **re-enabled** (was disabled)           |
| Content Explorer             | `implemented` | lazy route **re-enabled** (was disabled)           |
| Assessment Builder           | `implemented` | lazy route **re-enabled** (was disabled)           |
| Analytics Dashboard          | `implemented` | lazy route **re-enabled** (was disabled)           |
| Learning Path Designer       | `implemented` | lazy route **re-enabled** (was disabled)           |
| Settings / Profile           | `implemented` | lazy routes **re-enabled** (were disabled)         |

### tutorputor-admin (Admin App)

| Page / Feature     | Status        | Notes                                                                                      |
| ------------------ | ------------- | ------------------------------------------------------------------------------------------ |
| Content Studio     | `implemented` | CRUD, **evidence-based validation**, publish                                               |
| Content Generation | `implemented` | **Rewired to correct `/api/content-studio/` endpoints** — was using wrong `/api/v1/claims` |
| Artifact Queries   | `implemented` | `getExamples/Simulations/Animations()` helpers backed by real routes                       |
| Auth token sync    | `implemented` | `contentStudioApi.setAuthToken()` called on every auth state change from `useAuth`         |
| Authoring Page     | `partial`     | Loading from API, local states remain for complex editors                                  |

---

## 6. Content Generation Pipeline State

| Step                      | Status        | Notes                                                                                                                |
| ------------------------- | ------------- | -------------------------------------------------------------------------------------------------------------------- |
| Experience creation       | `implemented` | POST `/content-studio/experiences`                                                                                   |
| Claims generation (async) | `implemented` | BullMQ queue; fallback no-op in test mode                                                                            |
| Generation job polling    | `implemented` | GET `/content-studio/experiences/:id/progress`                                                                       |
| Validation                | `implemented` | **Evidence-based** per-claim artifact + task checks; pillar scoring (educational/experiential/technical/safety/a11y) |
| Publish flow              | `implemented` | **Gated**: validates first, throws with actionable error if `canPublish=false`                                       |
| Animations (linked)       | `implemented` | `ClaimAnimation` DB model wired; routes `GET/POST /experiences/:id/animations`                                       |
| Simulations (linked)      | `implemented` | `ClaimSimulation` upsert via `POST /experiences/:id/simulations` and `POST /sim-author/manifests/:id/link-claim`     |
| Comprehensive view        | `implemented` | `GET /content-studio/experiences/:id/comprehensive` returns experience + all claim artifacts                         |
| Example content (linked)  | `implemented` | `ClaimExample` queries via `GET /content-studio/experiences/:id/examples`                                            |

---

## 7. Simulation Authoring State

| Feature                   | Status        | Notes                                                                                         |
| ------------------------- | ------------- | --------------------------------------------------------------------------------------------- |
| NL Manifest Generation    | `implemented` | POST `/api/sim-author/generate`                                                               |
| NL Manifest Refinement    | `implemented` | POST `/api/sim-author/refine`                                                                 |
| Parameter Suggestions     | `implemented` | POST `/api/sim-author/suggest`                                                                |
| Domain validation         | `implemented` | Contract-valid `SimulationDomain` values enforced                                             |
| Frontend `useNLAuthoring` | `implemented` | API path bug fixed (was `/api/api/sim-author/...`)                                            |
| Manifest persistence      | `implemented` | `SimulationManifest` model; claim linkage via `POST /api/sim-author/manifests/:id/link-claim` |

---

## 8. Database Schema State

The Prisma schema (`libs/tutorputor-core/prisma/schema.prisma`) contains **60+ models**.

| Domain                                              | Status        |
| --------------------------------------------------- | ------------- | --------------------------------------------------------------------------- |
| Core learning (modules, enrollments, assessments)   | `implemented` |
| Content Studio (LearningExperience, claims)         | `implemented` |
| Simulation manifests and templates                  | `implemented` |
| Collaboration (threads, posts)                      | `implemented` |
| Social learning (study groups, tutoring, chat)      | `implemented` |
| Gamification (in memory / service; no model)        | `partial`     |
| Tenant + SSO federation                             | `implemented` |
| VR Labs                                             | `implemented` |
| Kernel plugins (`KernelPlugin`)                     | `implemented` |
| Automation rules (`AutomationRule`)                 | `implemented` |
| Notification preferences (`NotificationPreference`) | `implemented` |
| Curriculum / domain concepts                        | `implemented` |
| Example content artifacts (linked to claims)        | `implemented` | `ClaimExample` queryable via `GET /content-studio/experiences/:id/examples` |

---

## 9. CI / Quality Gates State

| Check                        | Status        | Notes                                                                                                        |
| ---------------------------- | ------------- | ------------------------------------------------------------------------------------------------------------ |
| TypeScript typecheck         | `implemented` | `--noEmit` failures now block CI                                                                             |
| ESLint                       | `implemented` | Lint failures now block CI; **admin `--max-warnings 0`** added                                               |
| Unit tests (vitest)          | `implemented` | 90/85/90/90 coverage thresholds enforced; **`validateExperience` and `publishExperience` test suites added** |
| E2E tests (playwright)       | `implemented` | API paths updated to `/api/v1/`                                                                              |
| Integration tests            | `implemented` | **Import path fixed** (`../../services/...`); `createServer` wraps `setupPlatform`                           |
| Spotless / Checkstyle (Java) | `n/a`         | Not applicable to Node.js services                                                                           |

---

## 10. Known Gaps (Planned but not implemented)

| Gap                                                | Target Workstream                                                              |
| -------------------------------------------------- | ------------------------------------------------------------------------------ |
| Payments / commerce routes                         | Planned (Milestone 6+)                                                         |
| LTI integration routes                             | Planned                                                                        |
| Granular per-resource RBAC permission checks       | Planned (WS9+)                                                                 |
| External notification delivery (email/push)        | Planned (Milestone 6+)                                                         |
| `prisma.config.ts` `__dirname` ESM incompatibility | Non-critical (Prisma CLI only; runtime uses `TUTORPUTOR_DATABASE_URL` env var) |

---

## 11. Maintenance Rule

> Every feature PR that changes observable behavior **must** update this document to reflect the new state.  
> PRs that don't update `CURRENT_STATE.md` when they change implemented/stubbed/disabled status will be flagged in code review.
