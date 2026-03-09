# TutorPutor Admin – Implementation Plan

> **Version:** 0.1  
> **Status:** Design / Not Implemented  
> **Audience:** Product engineers, backend engineers, frontend engineers (web + admin), architects

---

## 1. Context & Goals

TutorPutor already has:

- **Learner/Teacher Web App** (`apps/tutorputor-web`)
- **API Gateway** (`apps/api-gateway`)
- **Backend Services** (learning, content, CMS, marketplace, analytics, SSO, compliance, tenant-config, social, etc.)
- **Admin Console SPA** (`apps/tutorputor-admin`) with **stubbed or mock data** for:
  - Users, SSO, analytics, compliance, tenant summary/usage.

**Goal of this document:**

Design a **coherent, reuse-first, AI/ML-first TutorPutor Admin platform** that allows institutional admins and operators to manage:

- **Tenants** (institution-level overview & usage)
- **Users & roles** (within a tenant)
- **Content & modules** (via existing CMS & content services)
- **Simulations & templates** (via domain loader, simulation manifests & marketplace)
- **Marketplace listings & payments**
- **SSO providers & role mapping**
- **Compliance & audit**

…while **leveraging AI/ML everywhere it makes sense** (advanced analytics, recommendations, anomaly detection, and generative helpers) to turn raw data into guided actions for admins.

…in a way that **surfaces curated content to the learner portal** without duplicating domain models or business logic.

This is a **planning document only** – no immediate implementation; it must stay aligned with:

- `products/tutorputor/docs/TUTORPUTOR_ANALYSIS_AND_ENHANCEMENT_PLAN.md`
- Contracts in `contracts/v1/services.ts` and `contracts/v1/types.ts`
- Reuse-first, no-duplicates policy (prefer calling existing services over re-implementing logic).

---

## 2. Personas & Roles

Map directly to `UserRole` in `contracts/v1/types.ts`:

- **Student (`"student"`)** – end-user in learner web; **no admin access**.
- **Teacher (`"teacher"`)** – uses learner web + teacher tools (classrooms, analytics); limited CMS.
- **Creator (`"creator"`)** – content author; full CMS for modules/simulations but **not** tenant‑level admin.
- **Admin (`"admin"`)** – institutional admin/operator. Has access to **TutorPutor Admin SPA** and selected admin views in learner app.

Additional conceptual personas (mapped onto `UserRole` + services):

- **Platform Operator** – manages multi-tenant hosting; uses backend tooling + InstitutionAdminService.
- **Compliance Officer** – uses compliance/audit pages.
- **Identity/IT Admin** – manages SSO providers, role mapping, user imports.

> Rule of thumb: **TutorPutor Admin SPA is for `admin` role only**. Teacher/creator flows live in learner web (`/teacher`, `/cms`, `/templates`, etc.), guarded by role checks.

---

## 3. High-Level Architecture (Admin View)

### 3.1 Apps

- **Learner/Teacher UI** – `apps/tutorputor-web`

  - Routes like `/dashboard`, `/modules/:slug`, `/teacher`, `/cms`, `/templates`.
  - Talks to `/api/v1/*` and `/api/v1/cms/*` routes in gateway.

- **Admin UI** – `apps/tutorputor-admin`

  - Routes like `/dashboard`, `/users`, `/sso`, `/analytics`, `/compliance`, `/settings` (see `src/App.tsx` & `AdminLayout.tsx`).
  - Currently calls **stubbed endpoints** like `/admin/api/v1/users`, `/admin/api/v1/tenant/summary`, etc.

- **API Gateway** – `apps/api-gateway`

  - Learner routes: `/api/v1/learning/*`, `/api/v1/modules/*`, `/api/v1/cms/*`, `/api/v1/assessments/*`, `/api/v1/marketplace/*`, `/api/v1/analytics/*`, `/api/v1/ai/*`.
  - Admin/compliance routes already present:
    - `sso.ts` → `/auth/*`, `/admin/sso/*` (SSO admin)
    - `compliance.ts` → `/privacy/*`, `/admin/compliance/*`

- **Services** – `services/*` (Node/TS) & `ai-service` (Java)
  - `tutorputor-learning` → `LearningService`
  - `tutorputor-content` → `ContentService`
  - `tutorputor-cms` → `CMSService`
  - `tutorputor-marketplace` → `MarketplaceService` + simulation templates
  - `tutorputor-analytics` → `AnalyticsService`
  - `tutorputor-sso` → `SsoService`
  - `tutorputor-compliance` (via `@tutorputor/compliance`) → `ComplianceService`
  - `tutorputor-tenant-config` / future `tenant-admin` → `InstitutionAdminService`
  - `tutorputor-domain-loader` + `domain-contents/*` → domain concepts, modules, manifests, templates

### 3.2 Reuse-First Principles for Admin

- **Do not introduce new domain models** for modules, users, tenants, templates.
  - Use `Module`, `Enrollment`, `SimulationTemplate`, `MarketplaceListing`, `UserSummary`, `TenantSummary`, etc.
- **Do not duplicate query logic** that already exists in services.
  - Admin endpoints should delegate to `*Service` interfaces from `contracts/v1/services.ts`.
- **Admin-specific aggregation is allowed**, but must:
  - Only shape **responses** (aggregating from multiple services)
  - Not create parallel persistence models.

### 3.3 AI/ML-First Principles for Admin

- **Reuse existing AI services** instead of inventing new ad-hoc AI calls:
  - `tutorputor-ai-proxy` (`AIProxyService`) for explanation, summarization, and recommendation prompts.
  - `tutorputor-analytics` (`AnalyticsService`) for advanced metrics, risk indicators, trends, and forecasts.
- **Treat AI as a decision-support layer, not a hidden side-channel**:
  - Admin UIs should surface _why_ a recommendation is made (e.g., risk factors, usage trends).
  - Keep humans-in-the-loop for actions that change roles, access, pricing, or visibility.
- **Prefer AI-generated insights over bespoke dashboard logic**:
  - Use advanced analytics APIs for at‑risk students, difficulty heatmaps, or usage predictions rather than recomputing in the admin app.
- **Instrument feedback loops**:
  - Where AI suggestions exist (e.g., recommended actions, auto-tagging), capture admin accept/reject signals for future tuning.

---

## 4. Capabilities & Mapping to Existing Services

This section describes _what_ admin must be able to do and _which service_ owns the underlying behavior.

### 4.1 Tenants & Usage (Institution Admin)

Use **`InstitutionAdminService`** and **`AnalyticsService`**.

- **Tenant Summary**

  - Data: `TenantSummary` (total users, active users, total modules, classrooms, subscription tier). See `types.ts`.
  - Service: `InstitutionAdminService.getTenantSummary({ tenantId })`.
  - UI: Admin Dashboard (`tutorputor-admin/DashboardPage.tsx`).

- **Tenant Usage Metrics**
  - Data: `UsageMetrics` (active users, total events, top modules, etc.).
  - Service: `InstitutionAdminService.getTenantUsage({ tenantId, dateRange })`.
  - UI: Dashboard charts + Analytics page.
  - **AI/ML:** Use `AnalyticsService.getAdvancedAnalytics` to surface ML-derived insights (risk indicators, difficulty hotspots, usage forecasts) and show _recommended admin actions_ (e.g., "add capacity for physics", "target support to Classroom A").

### 4.2 Users & Roles

Use **`InstitutionAdminService`** for listing & importing users; use an **Auth/Identity service** for per-user role changes.

- **List Tenant Users**

  - Service: `InstitutionAdminService.listTenantUsers({ tenantId, role?, searchQuery?, pagination })`.
  - UI: Admin `UsersPage` table.

- **Update User Role**

  - New operation, but reuse `UserRole` type; must live in the auth/identity service (e.g. `tutorputor-sso` or a dedicated `tutorputor-identity` service).
  - Exposed as `/admin/api/v1/users/:userId/role` but internally calls `InstitutionAdminService` or `SsoService` extension.

- **Bulk Import Users**
  - Service: `InstitutionAdminService.bulkImportUsers(args)` (already specified in `services.ts`).
  - UI: `ImportUsersModal` in `UsersPage` (already scaffolded; currently posts to `/admin/api/v1/users/import`).
  - **AI/ML:**
    - Use analytics-driven risk indicators to highlight _at-risk_ or _high-impact_ users in the admin UI.
    - For bulk import, optionally call `AIProxyService` to auto-suggest roles, classroom assignments, or initial learning paths based on CSV metadata (subject, grade, department), with admins confirming suggestions.

### 4.3 Content & Modules (CMS)

Use **`CMSService`** and **`ContentService`**.

- **Manage Draft & Published Modules**
  - Existing CMS:
    - `services/tutorputor-cms/src/service.ts` implements `CMSService`.
    - API gateway `routes/cms.ts` exposes `/api/v1/cms/modules*` with `requireRole(["teacher","admin","creator"])`.
  - UI (creator/teacher role): `tutorputor-web`:
    - `CMSModulesPage` (list) → calls `/cms/modules` via `tutorputorClient` (maps to `/api/v1/cms/modules`).
    - `CMSModuleEditorPage` (edit) → create/update/publish modules.

**Decision:**

- **Do not create a separate Admin content surface** that duplicates CMS. Instead:
  - Reuse existing CMS pages in learner app (`/cms`, `/cms/:moduleId`).
  - Gate them by roles `teacher`, `creator`, `admin`.
  - Admin console may link into these pages (deep-link) rather than re-rendering modules.
  - **AI/ML:** Encourage CMS flows to reuse `AIProxyService` for generative assistance (drafting descriptions, objectives, and exercises) and analytics APIs to score content quality/coverage. Admins should see AI suggestions like "modules missing assessments" or "underused simulations" based on usage data.

### 4.4 Simulations & Templates

Use **Domain Loader + Simulation Manifests + Marketplace Simulation Templates**.

- **Domain Content**

  - `domain-contents/*.json` + `tutorputor-domain-loader` populate `DomainConcept` and `Module` + `SimulationManifest` rows.

- **Simulation Templates**
  - DB: `SimulationTemplate` model (see `schema.prisma`, referenced by `Module` & `SimulationTemplate` relations and `tutorputor-marketplace`).
  - Service: `tutorputor-marketplace` implementation already has:
    - `listSimulationTemplates` and `listFeaturedSimulationTemplates` (internal methods) mapping DB → DTO.
  - Gateway: `apps/api-gateway/src/routes/marketplace.ts` exposes `/api/v1/marketplace/templates*`.
  - UI:
    - Learner/Teacher: `SimulationTemplatesPage` + `SimulationTemplateGallery` in `tutorputor-web`.

**Admin Capabilities (planned):**

- Moderate and curate simulation templates per tenant:
  - Approve/reject, set `isPremium`, `isVerified`, adjust tags, domain, difficulty.
- Map domain concepts to simulation templates (reporting only; write is handled by domain loader + CMS).
  - **AI/ML:**
    - Use analytics + ML to recommend which templates to promote (high completion, high satisfaction) and which to retire.
    - Use AI to auto-tag templates and infer difficulty/domain from metadata and simulation manifests, with admins reviewing changes.

**Reuse rule:** All admin actions should operate on existing `SimulationTemplate` entities via **MarketplaceService** or a narrow `SimulationAdmin` façade; no new template tables.

### 4.5 Marketplace Listings & Payments

Use **`MarketplaceService`** and **`BillingService`**.

- Module listings for purchase are already modeled:
  - Prisma: `MarketplaceListing` (module, price, visibility, status).
  - Service: `createMarketplaceService` implements listing CRUD & simulation templates.

Admin must be able to:

- View all listings for a tenant.
- Adjust status (`DRAFT` / `ACTIVE` / `ARCHIVED`) and visibility (`PUBLIC` / `PRIVATE`).
- See high-level performance metrics via `MarketplaceListing.performance` (populated from analytics).
  - **AI/ML:** Leverage analytics to produce AI-driven pricing and promotion suggestions (e.g., "discount this module", "bundle with X"), anomaly detection for suspicious purchase patterns, and automated ranking of listings by predicted impact.

### 4.6 SSO & Identity Providers

Use **`SsoService`** & existing **gateway SSO routes**.

- Service: `SsoService` in `services.ts` (list/get/create/update/delete providers, role mapping, linked users).
- Gateway: `routes/sso.ts` already exposes `/admin/sso/providers*`, `/admin/sso/users*`, `/admin/sso/providers/:id/role-mapping*` etc.
- Admin UI: `SsoConfigPage` in `tutorputor-admin` currently calls `/admin/api/v1/sso/*`.

**Plan:**

- Introduce a thin gateway adapter under `/admin/api/v1/sso/*` that simply delegates to existing `/admin/sso/*` endpoints (no business logic duplication).
- Alternatively (preferred long-term): **update `SsoConfigPage` to call `/admin/sso/*` directly**, matching existing gateway semantics.
  - **AI/ML:** Over time, use analytics to flag anomalous login behavior (sudden spikes, unusual geos) and surface AI-generated security recommendations to admins (e.g., "consider enforcing MFA for this provider").

### 4.7 Compliance & Audit

Use **`ComplianceService`** and **Audit service**.

- Gateway: `routes/compliance.ts` already defines:
  - User privacy center: `/privacy/*`
  - Admin compliance: `/admin/compliance/*` (stats, requests, reports).
- Admin UI: `CompliancePage` in `tutorputor-admin` calls `/admin/api/v1/compliance/*`.
- Audit routes: `routes/audit.ts` (to be wired into admin `AuditPage`).

**Plan:**

- Align admin endpoints under `/admin/api/v1/compliance/*` (adapter over `/admin/compliance/*`) or update Admin SPA to use the existing paths.
- Add `/admin/api/v1/audit/events` that proxies to `ComplianceService.queryAuditEvents` or dedicated audit service.
  - **AI/ML:** Apply anomaly detection to audit streams (e.g., unusual role changes, mass deletions) and use AI summarization to generate human-readable compliance reports (GDPR/FERPA/COPPA) for the `CompliancePage`.

### 4.8 Analytics & Monitoring

Use **`AnalyticsService`** + `InstitutionAdminService.getTenantUsage` (for the `AnalyticsPage` in admin SPA).

- Engagement, content, user, and performance metrics should be aggregated from:
  - `AnalyticsService.getSummary`, `getAdvancedAnalytics`, `getUsageTrends`
  - `InstitutionAdminService.getTenantUsage`

Admin API should **shape data into the exact DTOs** expected by `AnalyticsPage` but **not store** new analytics tables.

- **AI/ML:** Make `AnalyticsPage` the primary surface for ML-driven insights from `getAdvancedAnalytics`—risk scores, difficulty heatmaps, predictive usage curves, and recommended interventions for admins.

### 4.9 Social & Collaboration (Optional / Later)

There is rich social functionality (study groups, forums, chat, peer tutoring). Admin may eventually:

- Moderate groups, forums, and posts.
- Configure collaboration settings per tenant.
  - **AI/ML:** Use ML models to detect toxic content or spam in discussions, identify high-value contributors, and recommend study groups or peer tutors that would benefit particular cohorts.

This is **Phase 3+** and outside the initial admin MVP for content visibility.

---

## 5. API Gateway – Admin Surface Design

Introduce an **Admin API namespace** in the gateway, conceptually:

```text
/admin/api/v1/
  tenant/summary
  tenant/usage
  users
  users/:userId/role
  users/import
  analytics/engagement
  analytics/content
  analytics/users
  analytics/performance
  sso/providers...
  compliance/stats, requests...
  audit/events
  marketplace/listings
  simulations/templates (optional; mostly via marketplace)
```

### 5.1 Patterns

- **Authentication**: `fastify.authenticate`
- **Authorization**: `fastify.requireRole(["admin"])` for institution-level endpoints.
- **Tenant Resolution**: `getTenantId(req)` from `requestContext`.
- **Service Delegation**: fetch from `InstitutionAdminService`, `AnalyticsService`, `SsoService`, `ComplianceService`, `MarketplaceService`, `CMSService`, not Prisma directly.

### 5.2 New/Aligned Routes (Conceptual)

1. **Tenant Summary & Usage**

   ```http
   GET /admin/api/v1/tenant/summary
   → InstitutionAdminService.getTenantSummary({ tenantId })

   GET /admin/api/v1/tenant/usage?start=ISO&end=ISO
   → InstitutionAdminService.getTenantUsage({ tenantId, dateRange })
   ```

2. **Users**

   ```http
   GET  /admin/api/v1/users?role=&search=&cursor=&limit=50
   → InstitutionAdminService.listTenantUsers({ tenantId, role, searchQuery, pagination })

   PATCH /admin/api/v1/users/:userId/role
   body: { role: UserRole }
   → Identity/tenant-admin service to update user role

   POST /admin/api/v1/users/import  (multipart/form-data)
   → map CSV/Excel → InstitutionAdminService.bulkImportUsers
   ```

3. **Admin Analytics** (backed by AnalyticsService + InstitutionAdminService):

   - `/admin/api/v1/analytics/engagement?range=7d|30d|90d|1y`
   - `/admin/api/v1/analytics/content?range=...`
   - `/admin/api/v1/analytics/users?range=...`
   - `/admin/api/v1/analytics/performance?range=...`

   Each handler:

   - Calls `AnalyticsService.getAdvancedAnalytics` and/or `InstitutionAdminService.getTenantUsage`.
   - Shapes the data into the DTOs expected by `AnalyticsPage.tsx` (`EngagementMetrics`, `ContentMetrics`, `UserMetrics`, `PerformanceMetrics`).

4. **SSO Management**

   Adapter endpoints under `/admin/api/v1/sso/*` that forward to SSO routes in `sso.ts`:

   - `GET /admin/api/v1/sso/providers` → `SsoService.listProviders`
   - `POST /admin/api/v1/sso/providers` → `SsoService.createProvider`
   - `PATCH /admin/api/v1/sso/providers/:id` → `SsoService.updateProvider`
   - `DELETE /admin/api/v1/sso/providers/:id` → `SsoService.deleteProvider`
   - `POST /admin/api/v1/sso/providers/:id/test` → `SsoService.testProvider`
   - `GET /admin/api/v1/sso/users` → `SsoService.listLinkedUsers`

   **Option B (simpler)**: Update `SsoConfigPage` to call the existing `/admin/sso/*` routes directly instead of introducing aliases.

5. **Compliance & Audit**

   - `/admin/api/v1/compliance/stats` → aggregate via `ComplianceService` (or `ComplianceServiceImpl` facade already used in `routes/compliance.ts`).
   - `/admin/api/v1/compliance/requests?type=export|deletion&status=` → `ComplianceService` / internal `ComplianceServiceImpl`.
   - `/admin/api/v1/compliance/requests/:id/process` → `ComplianceService.processRequest` (already present in `compliance.ts`).
   - `/admin/api/v1/audit/events?...` → `ComplianceService.queryAuditEvents`.

6. **Marketplace & Simulation Templates** (optional admin views)

   - `/admin/api/v1/marketplace/listings` → `MarketplaceService.listListings`.
   - `/admin/api/v1/simulations/templates` → reuse `MarketplaceService.listSimulationTemplates` with broader filters (admin can see all, not only published).

### 5.3 Avoiding Duplicates

- Admin gateway routes must call **existing services**; they should **never** reach Prisma directly.
- Where gateway already has `/admin/sso/*` and `/admin/compliance/*`, either:
  - expose **aliases** under `/admin/api/v1/*`, or
  - refactor admin SPA paths to hit existing routes.

---

## 6. TutorPutor Admin SPA – Feature Mapping

The Admin SPA already has:

- `DashboardPage` – tenant summary & usage (mocked).
- `UsersPage` – users list & import (mocked `/admin/api/v1/users*`).
- `SsoConfigPage` – SSO providers (`/admin/api/v1/sso/*`).
- `AnalyticsPage` – organization analytics (`/admin/api/v1/analytics/*`).
- `CompliancePage` – compliance stats & requests (`/admin/api/v1/compliance/*`).
- `AuditPage` – (not yet wired; likely `/admin/api/v1/audit/events`).
- `SettingsPage` – general admin settings (to be aligned with tenant-config & institution-admin services).

### 6.1 General UI Guidelines

- **Reuse @ghatana/ui and @ghatana/charts** – already in use (`AnalyticsPage`, cards, charts).
- **Do not reimplement learner-facing pages** (modules, CMS, templates) in admin.
  - Instead, deep-link into learner app with appropriate query params (e.g. `/cms?from=admin`).
- **Respect roles from SSO role mapping**:
  - `useAuth` hook in admin SPA should surface `user.role` consistent with `UserRole`.

### 6.2 Page-by-Page Plan

#### DashboardPage

- Implement real calls to:
  - `GET /admin/api/v1/tenant/summary`
  - `GET /admin/api/v1/tenant/usage?start=&end=`
- Map responses to expected `TenantSummary` & `UsageMetrics` shape.
- Remove or confine development-only mock branches.

#### UsersPage

- Wire to:
  - `GET /admin/api/v1/users?role=&search=&limit=50` → `PaginatedResult<UserSummary>`.
  - `PATCH /admin/api/v1/users/:id/role` → updates `UserRole`.
  - `POST /admin/api/v1/users/import` → `InstitutionAdminService.bulkImportUsers`.
- Keep UI as‑is; only align types with contracts (`UserSummary.role` etc.).

#### SsoConfigPage

- Decide on path strategy:
  - **Option A:** change fetch URLs to `/admin/sso/providers*` (existing gateway routes).
  - **Option B:** keep `/admin/api/v1/sso/*` and add alias routes in gateway that forward to SSO handlers.
- Ensure types use `IdentityProviderConfig`, `RoleMappingConfig` from contracts.

#### AnalyticsPage

- Replace mock endpoints with real admin analytics endpoints:
  - `GET /admin/api/v1/analytics/engagement?range=...`
  - `GET /admin/api/v1/analytics/content?range=...`
  - `GET /admin/api/v1/analytics/users?range=...`
  - `GET /admin/api/v1/analytics/performance?range=...`
- Gateway must shape data into the DTOs defined in `AnalyticsPage.tsx` to avoid UI churn.

#### CompliancePage

- Align with gateway `complianceRoutes`:
  - `GET /admin/api/v1/compliance/stats` → `ComplianceService.getStats` (or existing helper).
  - `GET /admin/api/v1/compliance/requests?type=` → `ComplianceService.listRequests`.
  - `POST /admin/api/v1/compliance/requests/:id/process` → `ComplianceService.processRequest`.
- Policy/consent UI can remain local state at first; later connect to tenant-config.

#### AuditPage (not yet opened here)

- Fetch from `/admin/api/v1/audit/events?actorId=&action=&targetType=&start=&end=&cursor=&limit=`.
- Backend call: `ComplianceService.queryAuditEvents({ tenantId, ... })`.

#### SettingsPage

- Consolidate **tenant-level configuration** from:
  - SSO providers
  - Domain packs & simulation quotas (from `tutorputor-tenant-config`)
  - Data retention / consent defaults
- Reuse `InstitutionAdminService` and tenant-config routes; avoid ad‑hoc Prisma updates.

---

## 7. Making Admin-Managed Data Visible in Learner Portal

Key requirement: **actions performed in admin must flow through existing services so that learner UI automatically sees changes.**

### 7.1 Modules & Content

- Publishing a module via CMS (`CMSService.publishModule`) already updates `Module.status = PUBLISHED` and increments `version`.
- Learner Portal uses `ContentService.listModules` and `getModuleBySlug` via:
  - `apps/api-gateway/src/routes/modules.ts` → `/api/v1/modules`, `/api/v1/modules/:slug`.

**Result:** Admin (or teacher/creator) publishing a module immediately affects what is visible on `/search`, `/modules/:slug`, and the dashboard – **no extra admin wiring required**.

### 7.2 Simulation Templates

- Domain Loader + `generateSimulationTemplates` write `SimulationTemplate` rows.
- `tutorputor-marketplace` exposes them via `listSimulationTemplates` → gateway `/api/v1/marketplace/templates`.
- Learner `SimulationTemplateGallery` consumes `/api/v1/marketplace/templates` via `useSimulationTemplates`.

Admin actions on templates (e.g. verifying, marking as premium, changing tags) must:

- Update `SimulationTemplate` via `MarketplaceService` or a small `SimulationAdminService` wrapper.
- The learner gallery will reflect changes through the existing marketplace API.

### 7.3 Users & Roles

- Admin changing a user’s `role` must update the source-of-truth user record used by auth/SSO.
- Learner and admin apps rely on that role to show/hide:
  - Teacher console, CMS, analytics (web app)
  - Admin console entry point (`/admin`).

### 7.4 Tenants & Usage

- Tenant-level settings (SSO providers, quotas, domain packs) are read by:
  - Auth flows (SSO routes).
  - Learning & marketplace services (for quotas/domain availability).

Admin UI must strictly call tenant-config / SSO services, not inline environment variables.

---

## 8. Phased Implementation Plan

### Phase 1 – Wire Core Admin APIs (MVP)

**Objective:** Replace mocks in TutorPutor Admin with real backend calls, without expanding scope.

1. **Implement `InstitutionAdminService` in a dedicated service** (or extend an existing tenant-config service):
   - `getTenantSummary`, `listTenantUsers`, `getTenantUsage`, `bulkImportUsers`.
2. **Add admin routes in API Gateway:**
   - `/admin/api/v1/tenant/summary`, `/tenant/usage` → `InstitutionAdminService`.
   - `/admin/api/v1/users*` → `InstitutionAdminService`.
3. **Adapt Admin SPA:**
   - Update `DashboardPage`, `UsersPage` to use real endpoints.
4. **Align SSO & Compliance paths:**
   - Either add `/admin/api/v1/sso/*` & `/admin/api/v1/compliance/*` adapters or update SPA URLs.

### Phase 2 – Analytics & Marketplace/Simulation Admin

1. **Analytics Adapters:**
   - Add `/admin/api/v1/analytics/*` routes that shape `AnalyticsService` + `InstitutionAdminService` data into `AnalyticsPage` DTOs.
   - Wire `AnalyticsPage` to these routes.
2. **Marketplace & Simulation Templates:**
   - Add admin-only endpoints to list all simulation templates & marketplace listings with filters.
   - Add small admin UI surfaces (either in admin SPA or as extensions to existing Marketplace/Template UI) to change flags (`isPremium`, `isVerified`, price, visibility).
   - **AI/ML focus:** Turn this phase into the primary integration point for advanced analytics—ensure new endpoints expose AI-derived scores (difficulty, risk, popularity) and recommended actions so admin UI can highlight them.

### Phase 3 – Advanced Tenant Controls & Social/Collaboration

1. **Tenant Settings/Quotas/Domain Packs:**
   - Integrate with `tutorputor-tenant-config` for simulation quotas, domain packs, etc.
   - Expose them in `SettingsPage`.
2. **Social & Collaboration Controls:**
   - Add moderation screens for study groups, forums, peer tutoring.
   - Reuse `StudyGroupService`, `ForumService`, `SocialLearning` services defined in contracts.
   - **AI/ML focus:** Apply ML moderation and recommendation services to social data (study groups, forums, tutoring) and expose admin-configurable policies (e.g., sensitivity thresholds, auto-flagging rules).

### Phase 4 – Hardening & Observability

- Enforce RBAC systematically for admin endpoints.
- Add audit events for critical admin actions (role changes, SSO config changes, domain packs).
- Add E2E tests for admin flows.
- Document admin API (`OpenAPI`) and keep docs in `docs/` up to date.

---

## 9. Reuse-First / No-Duplicates Checklist

Before implementing any admin feature, verify:

1. **Is there already a type in `contracts/v1/types.ts`?**
   - Use it; do _not_ create ad-hoc DTOs in services.
2. **Is there already a service interface in `contracts/v1/services.ts`?**
   - Extend or implement that interface; avoid creating parallel services.
3. **Is there already a gateway route that exposes similar data?**
   - Prefer **adapting** it or introducing a thin alias over re-querying Prisma.
4. **Is there already a learner/teacher UI for a concept (modules, CMS, templates)?**
   - Prefer deep-linking or adding admin switches to existing pages rather than duplicating screens in admin.

---

## 10. Admin Journeys & UX Flows (AI/ML-First)

This section enumerates the primary TutorPutor Admin journeys and describes them with a **UI/UX-first mindset**, including:

- Layout and wireframe-style descriptions of each page.
- The step-by-step **user journey**.
- The end-to-end **backend and AI/ML integration** (Admin SPA → API Gateway → Services → DB → AI/ML services).

### 10.0 Implementation Mapping Overview

This table ties each journey to the **actual code structure** so implementation can proceed without inventing new concepts or duplicating models.

| Journey                                | SPA Route(s)                                                          | Main Component(s)                                                       | Key Admin API Endpoints (planned/existing)                                                                                                       | Core Service(s) / Domain Types                                                                                                                             |
| -------------------------------------- | --------------------------------------------------------------------- | ----------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0 – Access Admin Console               | `/admin`, nested routes                                               | `App.tsx`, `AdminLayout`                                                | Auth flows: `/auth/*` (SSO), redirect from `/login?redirect=/admin`                                                                              | `SsoService` (auth), `useAuth` hook; user domain from identity/SSO                                                                                         |
| 1 – Tenant Overview Dashboard          | `/dashboard`                                                          | `DashboardPage`                                                         | `GET /admin/api/v1/tenant/summary`, `GET /admin/api/v1/tenant/usage`                                                                             | `InstitutionAdminService` (`TenantSummary`, `UsageMetrics`), `AnalyticsService`                                                                            |
| 2 – Users & Roles                      | `/users`                                                              | `UsersPage`                                                             | `GET /admin/api/v1/users`, `PATCH /admin/api/v1/users/:id/role`, `POST /admin/api/v1/users/import`                                               | `InstitutionAdminService.listTenantUsers/bulkImportUsers`, identity/SSO service; `UserSummary`, `PaginatedResult<UserSummary>`                             |
| 3 – SSO Providers & Role Mapping       | `/sso`                                                                | `SsoConfigPage`                                                         | `/admin/api/v1/sso/providers*` (or `/admin/sso/*`), `/admin/api/v1/sso/users`                                                                    | `SsoService` with `IdentityProviderConfig`, role mapping config                                                                                            |
| 4 – Analytics                          | `/analytics`                                                          | `AnalyticsPage`                                                         | `/admin/api/v1/analytics/{engagement,content,users,performance}`                                                                                 | `AnalyticsService.getSummary/getAdvancedAnalytics/getUsageTrends`, `InstitutionAdminService.getTenantUsage`; analytics DTOs defined in `AnalyticsPage.tsx` |
| 5 – Compliance & Data Requests         | `/compliance`                                                         | `CompliancePage`                                                        | `/admin/api/v1/compliance/stats`, `/admin/api/v1/compliance/requests`, `/admin/api/v1/compliance/requests/:id/process` (→ `/admin/compliance/*`) | `ComplianceService` (`ComplianceStats`, data export/deletion requests), audit log domain                                                                   |
| 6 – Audit Log Exploration              | `/audit`                                                              | `AuditPage`                                                             | `/admin/api/v1/audit/logs`, `/admin/api/v1/audit/export`, `/admin/api/v1/audit/logs/:id`, `/admin/api/v1/audit/summary`                          | Audit logger (query + export) using `AuditLogQuery`, `AuditLogEntry`                                                                                       |
| 7 – Content & CMS Governance           | `/analytics` → `/cms`                                                 | `AnalyticsPage` (entry), learner CMS pages (`CMSModulesPage`, etc.)     | Content API: `/api/v1/cms/modules*`, `/api/v1/modules*` (learner web, not admin namespace)                                                       | `CMSService`, `ContentService`, `Module` and related content types                                                                                         |
| 8 – Simulation Template & Marketplace  | `/templates-admin` (TBD) or existing `/templates` with admin controls | New admin view or extended `SimulationTemplatesPage` / marketplace page | `/admin/api/v1/marketplace/listings`, `/admin/api/v1/simulations/templates` (admin-scoped views over marketplace APIs)                           | `MarketplaceService` (`SimulationTemplate`, `MarketplaceListing`, existing analytics-backed performance fields)                                            |
| 9 – Tenant Settings & Quotas           | `/settings`                                                           | `SettingsPage`                                                          | `GET /admin/api/v1/tenant/settings`, `PUT/PATCH /admin/api/v1/tenant/settings`                                                                   | Tenant-config service + `InstitutionAdminService`; tenant config tables (quotas, domain packs, feature flags)                                              |
| 10 – Social & Collaboration Moderation | `/social-admin` (TBD)                                                 | New admin view (phase 3+)                                               | `/admin/api/v1/social/groups`, `/admin/api/v1/social/forums`, `/admin/api/v1/social/flags`, moderation actions                                   | `StudyGroupService`, `ForumService`, `SocialLearning` domain types                                                                                         |

### 10.1 Journey 0 – Accessing the Admin Console

- **Persona:** Admin, Identity/IT Admin.
- **Goal:** Securely access the TutorPutor Admin SPA from the main login flow.

**UI/UX Flow**

- Entry points:
  - From main web app header (if `user.role === "admin"`): a link or button: `Admin Console` → `/admin` (served by `tutorputor-admin`).
  - Direct URL: `https://{tenantSlug}.tutorputor.com/admin`.
- Wireframe:
  - If not authenticated:
    - Show minimal loader, then redirect to `/login?redirect=/admin` (learner web app login page).
  - If authenticated but not admin:
    - Simple full‑page message: **Access Denied** with CTA back to `/dashboard`.
  - If authenticated admin:
    - Render `AdminLayout` with sidebar nav and default route redirect to `/dashboard`.

**End-to-End Flow**

1. Admin hits `/admin`.
2. `tutorputor-admin` SPA mounts, `useAuth()` attempts to read current session via API or shared auth context.
3. If no valid session:
   - Browser is redirected to main app `/login?redirect=/admin`.
4. After login via SSO (`/auth/*` handled by `sso.ts` + `SsoService`), gateway sets session cookies.
5. SPA reloads `/admin`, now `useAuth()` returns `{ isAuthenticated: true, isAdmin: true, tenantId, user }`.
6. Admin sidebar and dashboard render.

**AI/ML Angle**

- Use analytics to flag frequent admin login failures or anomalies in SSO flows in the `AnalyticsPage` (but not in the initial access flow itself).

---

### 10.2 Journey 1 – Tenant Overview Dashboard

- **Persona:** Admin, Platform Operator.
- **Goal:** Get a **single view of tenant health**, usage, and AI‑powered insights.

**UI/UX Wireframe**

- Route: `/dashboard` (Admin SPA).
- Layout:
  - **Header bar**:
    - Title: `Admin Dashboard`.
    - Subtitle: `Overview of your organization's TutorPutor usage`.
    - Badge for subscription tier (e.g., `enterprise`, `basic`).
  - **Top stats grid** (4 cards):
    - Total Users.
    - Active Users (30d) + percentage of total.
    - Total Modules.
    - Classrooms.
  - **Charts row**:
    - Left: `Daily Active Users` (line chart).
    - Right: `Top Modules by Enrollment` (bar chart).
  - **Activity row (3 cards)**:
    - Learning Activity (events, assessments, completion rate).
    - User Engagement (DAU/WAU/MAU, DAU/MAU ratio).
    - Quick Actions (Import Users, Configure SSO, View Audit Log, Download Report).
  - **AI Insights panel (optional add‑on)**:
    - Right or bottom card: `AI Insights` summarizing key findings: "Physics modules show high drop‑off" / "At‑risk students concentrated in Class X".

**Journey Steps**

1. Admin selects `Dashboard` from Admin sidebar.
2. Page loads with skeleton states while data is fetched.
3. Once loaded, admin quickly glances at:
   - Active vs total users.
   - Top modules and engagement.
   - AI summary of trends and potential risks.
4. Admin may click a quick action to:
   - Jump to Users (`/users?action=import`).
   - Go to SSO Config (`/sso`).
   - Open Compliance/Audit.

**Backend & AI/ML Integration**

- Admin SPA:
  - `GET /admin/api/v1/tenant/summary` → `TenantSummary`.
  - `GET /admin/api/v1/tenant/usage?start=&end=` → `UsageMetrics`.
  - `GET /admin/api/v1/analytics/engagement?range=30d` → `EngagementMetrics`.
- API Gateway:
  - Delegates to `InstitutionAdminService.getTenantSummary` and `getTenantUsage`.
  - Delegates to `AnalyticsService.getAdvancedAnalytics` / `getUsageTrends` for richer charts.
- Services/DB:
  - `tutorputor-db` Prisma models: `Enrollment`, `LearningEvent`, `AssessmentAttempt`, etc.
  - `AnalyticsService` reads aggregated metrics and ML outputs (e.g., risk scores).
- AI/ML:
  - `AnalyticsService.getAdvancedAnalytics` provides predictions and risk indicators.
  - Optional: `AIProxyService` called with a structured analytics summary to generate human‑readable **"AI Insights"** text displayed in the dashboard panel.

---

### 10.3 Journey 2 – Manage Users & Roles

- **Persona:** Admin, Institution Admin.
- **Goal:** Search, filter, and manage users in the tenant; change roles; bulk import.

**UI/UX Wireframe**

- Route: `/users`.
- Layout:
  - **Header**:
    - Title: `Users`.
    - Subtitle: `Manage users in your organization`.
    - Actions: `Import Users`, `Invite User`.
  - **Filter bar (in a card)**:
    - Search input: "Search by name or email".
    - Role dropdown: `All Roles`, `Admin`, `Teacher`, `Creator`, `Student`.
  - **Table**:
    - Columns: User, Role, Status, SSO Provider, Actions.
    - Each row:
      - Avatar + displayName + email.
      - Role badge (color-coded).
      - Status badge (e.g., Active, Invited, Suspended – later).
      - SSO provider label.
      - Role dropdown in Actions column for quick update.
  - **Footer**:
    - Showing X of Y users, `Load More` if `hasMore`.
  - **Import users modal**:
    - File input (CSV/Excel).
    - Checkbox: `Send welcome emails`.
    - AI option (later): `Preview AI-suggested roles/assignments`.

**Journey Steps**

1. Admin arrives at `/users`.
2. Default list shows first page of users (sorted by name or last activity).
3. Admin can:
   - Filter by role; search for a specific user.
   - Change a user’s role via dropdown.
   - Open Import modal, upload CSV/Excel, toggle `Send invites` and confirm.
4. For AI‑enhanced flows:
   - After upload, show AI-suggested role and classroom for each row, with checkboxes to accept or override.

**Backend & AI/ML Integration**

- Admin SPA:
  - `GET /admin/api/v1/users?role=&search=&cursor=&limit=50` → `PaginatedResult<UserSummary>`.
  - `PATCH /admin/api/v1/users/:id/role` → `{ role: UserRole }`.
  - `POST /admin/api/v1/users/import` (multipart) → import result.
- API Gateway:
  - Delegates to `InstitutionAdminService.listTenantUsers` & `bulkImportUsers`.
  - Delegates role change to identity/SSO/tenant-admin service (e.g., `SsoService` extension).
- Services/DB:
  - Underlying user store (not Prisma in this service) holds `UserSummary` data.
  - `InstitutionAdminService` orchestrates user listing and import.
- AI/ML:
  - During import, gateway or `InstitutionAdminService` calls `AIProxyService` with CSV metadata to infer recommended `role`, `classroomIds`, or learning paths.
  - `AnalyticsService`’s risk signals may be surfaced in the UI (e.g., risk badge by user row).

---

### 10.4 Journey 3 – Configure SSO Providers & Role Mapping

- **Persona:** Identity/IT Admin.
- **Goal:** Configure and maintain identity providers (OIDC/SAML), domains, and default roles; test and monitor health.

**UI/UX Wireframe**

- Route: `/sso`.
- Layout:
  - **Header**:
    - Title: `SSO Configuration`.
    - CTA: `Add Identity Provider`.
  - **Info card**:
    - Brief explanation of SSO support and recommended providers.
  - **Providers list** (cards):
    - Icon, display name, type (OIDC/SAML), status badge, last successful auth timestamp.
    - Allowed domains list.
    - Buttons: `Test`, `Enable/Disable`, `Edit`, `Delete`.
  - **Add/Edit Provider modal**:
    - Type selection (OIDC/SAML).
    - OIDC fields: discovery endpoint, clientId, clientSecret.
    - Allowed domains.
    - Default role dropdown.
    - Later: Role mapping configuration link.

**Journey Steps**

1. Admin lands on `/sso`, sees list of configured providers (or empty state).
2. Clicks `Add Identity Provider` → opens modal.
3. Fills OIDC or SAML config, allowed domains, default role.
4. Saves; provider appears in list.
5. Clicks `Test` on provider to verify discovery document / connection.
6. Uses `Enable`/`Disable` as needed.

**Backend & AI/ML Integration**

- Admin SPA:
  - `GET /admin/api/v1/sso/providers`.
  - `POST /admin/api/v1/sso/providers`.
  - `PATCH /admin/api/v1/sso/providers/:id`.
  - `DELETE /admin/api/v1/sso/providers/:id`.
  - `POST /admin/api/v1/sso/providers/:id/test`.
- API Gateway:
  - Proxies to existing `/admin/sso/*` routes backed by `SsoService`.
- Services/DB:
  - `tutorputor-sso` persisting `IdentityProviderConfig`.
- AI/ML:
  - `AnalyticsService` flags anomaly patterns (unexpected login patterns per provider).
  - Optional: `AIProxyService` generates **human-readable risk summaries** ("Okta provider has a rising error rate; consider verifying certificates"), displayed near provider cards.

---

### 10.5 Journey 4 – Analyze Organization Analytics

- **Persona:** Admin, Program Director, Data Analyst.
- **Goal:** Use rich analytics and AI insights to understand engagement, content performance, user growth, and platform reliability.

**UI/UX Wireframe**

- Route: `/analytics`.
- Layout:
  - **Header**:
    - Title: `Analytics`.
    - Date range selector (7d/30d/90d/1y).
  - **Tabs**:
    - Engagement, Content, Users, Performance.
  - Each tab has:
    - Top metrics grid (cards).
    - 1–2 charts using `@ghatana/charts`.
    - AI insights snippet for that dimension.

**Journey Steps**

1. Admin opens `/analytics`, default tab = Engagement.
2. Selects date range; page reloads relevant data.
3. Switches between tabs to inspect different slices.
4. Reads AI insight text blocks that highlight non‑obvious patterns and suggested interventions.

**Backend & AI/ML Integration**

- Admin SPA:
  - `GET /admin/api/v1/analytics/engagement?range=` → `EngagementMetrics`.
  - `GET /admin/api/v1/analytics/content?range=` → `ContentMetrics`.
  - `GET /admin/api/v1/analytics/users?range=` → `UserMetrics`.
  - `GET /admin/api/v1/analytics/performance?range=` → `PerformanceMetrics`.
- API Gateway:
  - Uses `AnalyticsService.getSummary`, `getAdvancedAnalytics`, `getUsageTrends`.
  - Optionally calls `AIProxyService` with structured analytics payload to get summarized action items.
- Services/DB:
  - Reads from analytical tables built from `LearningEvent`, `AssessmentAttempt`, etc.
- AI/ML:
  - `AnalyticsService.getAdvancedAnalytics` returns **risk indicators**, **difficulty heatmaps**, and **predictions**.
  - `AIProxyService` converts metrics into plain-language recommendations.

---

### 10.6 Journey 5 – Compliance & Data Requests

- **Persona:** Compliance Officer, Admin.
- **Goal:** Track and process data export/deletion requests; adjust retention and consent policies; run reports.

**UI/UX Wireframe**

- Route: `/compliance`.
- Layout:
  - **Header**: `Compliance & Privacy`.
  - **Stats row**: pending exports, pending deletions, completed this month, average processing time.
  - **Tabs**: `Data Requests`, `Policies & Consent`.
  - **Data Requests tab**:
    - Filter buttons: All, Exports, Deletions.
    - Table of requests: user, type, status, requested date, actions (`Process`, `Download`).
  - **Policies & Consent tab**:
    - Form for data retention days.
    - Consent settings checkboxes.
    - Buttons: Save retention policy, Save consent settings.
    - Compliance reports buttons.

**Journey Steps**

1. Compliance officer opens `/compliance`.
2. Reviews KPIs in stats row.
3. Filters data requests to pending deletion and clicks `Process` as needed.
4. Switches to Policies tab to adjust retention windows or consent defaults.
5. Downloads compliance reports as needed.

**Backend & AI/ML Integration**

- Admin SPA:
  - `GET /admin/api/v1/compliance/stats`.
  - `GET /admin/api/v1/compliance/requests?type=&status=`.
  - `POST /admin/api/v1/compliance/requests/:id/process`.
  - Future: `GET /admin/api/v1/compliance/reports/:type`.
- API Gateway:
  - Proxies to `ComplianceService` and related infrastructure (via `ComplianceServiceImpl`).
- Services/DB:
  - Prisma models for deletion/export requests, consent records, audit logs.
- AI/ML:
  - Anomaly detection over compliance & audit events (e.g., mass deletion requests).
  - AI-generated, human‑readable report summaries and risk narratives.

---

### 10.7 Journey 6 – Audit Log Exploration

- **Persona:** Compliance Officer, Security Engineer.
- **Goal:** Inspect and filter audit events (role changes, SSO config edits, deletions, etc.) to investigate incidents.

**UI/UX Wireframe**

- Route: `/audit`.
- Layout:
  - **Header**: `Audit Log`.
  - **Filter panel**:
    - Date range.
    - Actor (search by user).
    - Action type.
    - Target type (user, tenant, sso_config, module, classroom).
  - **Results list/table**:
    - Timestamp, actor, action, target, brief metadata.
  - **Details drawer**:
    - When clicking an event, slide-out or modal with full metadata, JSON view if needed, and AI-generated summary.

**Backend & AI/ML Integration**

- Admin SPA:
  - `GET /admin/api/v1/audit/events?actorId=&action=&targetType=&start=&end=&cursor=&limit=`.
- API Gateway:
  - Delegates to `ComplianceService.queryAuditEvents`.
- Services/DB:
  - `AuditEvent` model (see `types.ts`).
- AI/ML:
  - `AIProxyService` can summarise clusters of events or a single event with context ("This change updated SSO config for Okta and affected 250 users").
  - Anomaly models used to highlight suspicious events.

---

### 10.8 Journey 7 – Content & CMS Governance

- **Persona:** Admin, Creator Lead.
- **Goal:** Oversee content health and delegate authoring, while using existing CMS UI.

**UI/UX Pattern**

- Admin does **not** have a separate content UI in Admin SPA; instead:
  - From Admin Dashboard or Analytics, quick actions link to learner web `/cms` and `/cms/:moduleId`.
  - CMS UI (`CMSModulesPage`, `CMSModuleEditorPage`) remains the single authoring surface.

**Journey Steps**

1. Admin identifies content gaps via `AnalyticsPage` (e.g., modules without assessments, low completion modules).
2. Clicks a module in `Top Modules` or in a "Needs attention" list → navigates to learner web `/cms/:moduleId` (with SSO session shared).
3. Works with creators/teachers to adjust content using CMS editor.

**Backend & AI/ML Integration**

- Admin SPA and learner web share auth & tenant context.
- CMS routes:
  - `GET /api/v1/cms/modules` & `POST/PATCH/POST /api/v1/cms/modules/:id/publish` in gateway.
  - Delegate to `CMSService` and `ContentService`.
- AI/ML:
  - `AIProxyService` used heavily in CMS editor for generative authoring.
  - `AnalyticsService` drives content governance recommendations shown in Admin Analytics and Dashboard.

---

### 10.9 Journey 8 – Simulation Template & Marketplace Curation

- **Persona:** Admin, Simulation Lead.
- **Goal:** Curate which simulations and templates are emphasized, ensure quality and pricing, surface best content to marketplace and template galleries.

**UI/UX Wireframe**

- Could live either as:
  - A new `Templates` or `Marketplace` section in Admin SPA, or
  - Additional admin controls on existing learner `SimulationTemplatesPage`.
- Layout (Admin SPA variant):
  - Filters: domain, difficulty, tags, premium/verified, min rating.
  - List/grid of templates:
    - Title, domain, difficulty, tags, stats (uses, rating), flags (`isPremium`, `isVerified`).
  - Sidebar or inline controls for:
    - Toggling `isPremium`/`isVerified`.
    - Editing tags and descriptions.

**Journey Steps**

1. Admin navigates to `Templates` section (route TBD: `/templates-admin` or extension of `/templates`).
2. Filters by domain (e.g., Physics) and sorts by usage or rating.
3. For each template:
   - Reviews AI‑suggested badge ("high impact", "underperforming").
   - Toggles `isVerified` or `isPremium`.
   - Optionally updates tags.
4. Changes propagate to learner `SimulationTemplateGallery`.

**Backend & AI/ML Integration**

- Admin SPA:
  - `GET /admin/api/v1/marketplace/listings` or `/simulations/templates` for an admin view.
  - `PATCH` endpoints for updating template metadata/flags.
- API Gateway:
  - Delegates to `MarketplaceService` and simulation template methods.
- Services/DB:
  - `SimulationTemplate` Prisma model; marketplace listing models.
- AI/ML:
  - Analytics computes **template scores** (based on completions, satisfaction, learning gains).
  - AI labels templates and suggests which to promote or retire.

---

### 10.10 Journey 9 – Tenant Settings, Quotas & Domain Packs

- **Persona:** Admin, Platform Operator.
- **Goal:** Configure tenant-level settings such as simulation quotas, enabled domains, and feature flags.

**UI/UX Wireframe**

- Route: `/settings`.
- Layout:
  - Sections:
    - General Info (tenant name, ID, plan).
    - Simulation Quotas (max concurrent sessions, per-month run limits).
    - Domain Packs (enabled subject domains: Physics, Chemistry, etc.).
    - Feature Flags (social learning, peer tutoring, VR labs, etc.).
  - Simple forms with save buttons per section.

**Backend & AI/ML Integration**

- Admin SPA:
  - `GET /admin/api/v1/tenant/settings`.
  - `PUT /admin/api/v1/tenant/settings`.
- API Gateway:
  - Integrates with `tutorputor-tenant-config` and possibly `InstitutionAdminService`.
- Services/DB:
  - Tenant configuration tables in tenant-config service.
- AI/ML:
  - Analytics can suggest quota adjustments based on usage trends.
  - AI could propose enabling/disabling feature flags for low‑usage modules.

---

### 10.11 Journey 10 – Social & Collaboration Moderation (Phase 3+)

- **Persona:** Admin, Community Manager.
- **Goal:** Moderate study groups, forums, peer tutoring, and social interactions.

**UI/UX Wireframe**

- Route: e.g., `/social-admin` or as sub-tabs under `Collaboration`.
- Layout:
  - Filters: group/forum type, status, flagged items only.
  - Lists:
    - Study groups with member counts, activity, flags.
    - Forum topics/posts with flags.
  - Detail panel with AI-generated moderation suggestions.

**Backend & AI/ML Integration**

- Admin SPA:
  - `GET /admin/api/v1/social/groups`, `/forums`, `/flags` (design TBD).
  - Actions: `POST /admin/api/v1/social/groups/:id/archive`, `POST /admin/api/v1/forums/:id/lock`, etc.
- API Gateway:
  - Delegates to `StudyGroupService`, `ForumService`, and social services.
- Services/DB:
  - Models: `StudyGroup`, `Forum`, `ForumTopic`, `ForumPost`, `PostReaction`, etc.
- AI/ML:
  - ML classifiers for toxicity/spam detection run on social content.
  - `AIProxyService` can generate human-readable rationales for why content was flagged.

---

### 10.12 Admin View DTOs (Non-Persisted)

To keep the domain model clean and avoid schema duplication, some admin screens will use **view-layer DTOs** that are _composed from existing domain types_ but are **not** new persisted tables. They are shaped purely for UI needs.

Examples:

- **SimulationTemplateAdminView**

  - **Purpose:** Drive the Templates/Marketplace admin table/grid.
  - **Composed from:** [SimulationTemplate](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/apps/api-gateway/src/routes/marketplace.ts:39:0-56:1), [MarketplaceListing](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/contracts/v1/types.ts:419:0-434:1), analytics aggregates.
  - **Fields (view-only):**
    - `templateId: string` (from `SimulationTemplate.id`)
    - `title: string`
    - `domain: string`
    - `difficulty: string`
    - `tags: string[]`
    - `isPremium: boolean`
    - `isVerified: boolean`
    - `usageCount: number` (derived from analytics)
    - `completionRate: number` (derived)
    - `averageRating: number` (derived)
    - `aiImpactScore?: number` (ML-derived; not stored as a separate column)
    - `lastUpdatedAt: string`
  - **Notes:** Backed only by existing [SimulationTemplate](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/apps/api-gateway/src/routes/marketplace.ts:39:0-56:1) / [MarketplaceListing](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/contracts/v1/types.ts:419:0-434:1) rows and analytics views; no new DB model.

- **MarketplaceListingAdminView**

  - **Purpose:** Show listings plus performance metrics in admin Marketplace screens.
  - **Composed from:** [MarketplaceListing](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/contracts/v1/types.ts:419:0-434:1) + analytics.
  - **Fields (view-only):**
    - `listingId: string`
    - `moduleId: string`
    - `title: string`
    - `status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'`
    - `visibility: 'PUBLIC' | 'PRIVATE'`
    - `priceCents: number`
    - `currency: string`
    - `purchases: number`
    - `revenueCents: number`
    - `conversionRate: number`
    - `aiPromotionSuggestion?: 'promote' | 'discount' | 'bundle' | null` (from analytics/AI)
  - **Notes:** Computed join of [MarketplaceListing](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/contracts/v1/types.ts:419:0-434:1) with analytics aggregates.

- **TenantSettingsView**

  - **Purpose:** Power the `/settings` page tabs (General, Branding, Features, Notifications, Quotas, Domain Packs).
  - **Composed from:** Tenant-config tables in `tutorputor-tenant-config` + [InstitutionAdminService](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/contracts/v1/services.ts:808:0-864:1) read models.
  - **Fields (view-only, conceptual):**
    - `tenantId: string`
    - `organizationName: string`
    - `subdomain: string`
    - `logoUrl?: string`
    - `primaryColor: string`
    - `allowPublicRegistration: boolean`
    - `requireEmailVerification: boolean`
    - `defaultUserRole: UserRole`
    - `maxUsersPerClassroom: number`
    - `enabledFeatures: string[]`
    - `enabledDomainPacks: string[]` (e.g., `['physics', 'chemistry']`)
    - `simulationQuotas: { maxConcurrentSessions: number; monthlyRuns: number }`
    - `supportEmail: string`
    - `timezone: string`
  - **Notes:** Maps 1:1 onto existing tenant-config schema; this DTO is purely a transport shape for the admin SPA.

- **SocialModerationItemView** (Phase 3+)
  - **Purpose:** Drive the Social/Collaboration moderation queue.
  - **Composed from:** [StudyGroup](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/contracts/v1/types.ts:1058:0-1068:1), `Forum`, `ForumTopic`, `ForumPost`, `PostReaction`, and ML flags.
  - **Fields (view-only):**
    - `id: string`
    - `type: 'study_group' | 'forum_topic' | 'forum_post'`
    - `title?: string`
    - `snippet: string` (short content preview)
    - `authorId: string`
    - `authorDisplayName: string`
    - `createdAt: string`
    - `flagCount: number`
    - `aiToxicityScore?: number`
    - `aiSpamScore?: number`
    - `status: 'pending' | 'approved' | 'removed'`
  - **Notes:** Underlying content is stored in existing social tables; ML scores are derived, not a new persistence model.

**Rule:** These view DTOs **must not** be turned into new Prisma models or persisted entities. They are aggregation/composition layers over existing domain types, shaped for admin UI and AI/ML presentation only.

### 10.13 Using Admin View DTOs in Implementation

When implementing the admin journeys, these DTOs should be used strictly as **projection/view models** over existing domain entities.

- **TypeScript-only interfaces**

  - Define these DTOs as TypeScript interfaces in a shared contracts package or in `apps/tutorputor-admin/src/types/adminViews.ts`.
  - Do **not** create corresponding Prisma models or database tables.
  - Treat them as read/write payload shapes for the Admin SPA only.

- **API Gateway**

  - Admin endpoints such as:
    - `/admin/api/v1/simulations/templates`
    - `/admin/api/v1/marketplace/listings`
    - `/admin/api/v1/tenant/settings`
    - `/admin/api/v1/social/*`
  - Should:
    - Call existing services ([MarketplaceService](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/contracts/v1/services.ts:203:0-228:1), tenant-config service, [InstitutionAdminService](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/contracts/v1/services.ts:808:0-864:1), [StudyGroupService](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/contracts/v1/services.ts:900:0-1135:1), [ForumService](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/contracts/v1/services.ts:1140:0-1366:1), etc.).
    - Aggregate/join results and map them into the DTO shapes (e.g., `SimulationTemplateAdminView`, `TenantSettingsView`, `SocialModerationItemView`).
    - Return these DTOs as JSON to the Admin SPA.

- **Services**

  - Persist only the existing domain entities:
    - [SimulationTemplate](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/apps/api-gateway/src/routes/marketplace.ts:39:0-56:1), [MarketplaceListing](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/contracts/v1/types.ts:419:0-434:1), tenant-config models, [StudyGroup](cci:2://file:///home/samujjwal/Developments/ghatana/products/tutorputor/contracts/v1/types.ts:1058:0-1068:1), `Forum`, `ForumTopic`, `ForumPost`, `PostReaction`, etc.
  - Any helper methods that produce admin views should:
    - Compose DTOs from existing repositories and analytics/ML outputs.
    - Avoid introducing new write paths or dedicated “admin” tables.

- **Admin SPA**
  - Use these DTO interfaces as the client-side types for:
    - Templates/Marketplace admin screens.
    - `/settings` tabs (tenant settings, quotas, domain packs, feature flags).
    - Social moderation queues and detail panels (Phase 3+).
  - UI should:
    - Display AI-derived fields (`aiImpactScore`, `aiPromotionSuggestion`, `aiToxicityScore`, etc.) when present.
    - Degrade gracefully when these optional AI fields are `null`/`undefined` (e.g., before ML models are wired up or if disabled per tenant).

**Rule:** Always reuse existing domain models and analytics/ML aggregates. Admin view DTOs are a **projection layer** for admin UX and AI/ML insights, not new sources of truth.

---

Following this plan will allow us to bring TutorPutor Admin to production readiness while staying consistent with the broader platform architecture and ensuring everything admins manage is naturally surfaced to the learner portal through existing services and routes.
