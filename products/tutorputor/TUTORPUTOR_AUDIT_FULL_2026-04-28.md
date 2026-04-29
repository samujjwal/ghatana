# TutorPutor Full-Stack Product Audit (2026-04-28)

**Scope:** TutorPutor product (web app, admin app, mobile app, API gateway, platform service, content generation service, shared libraries, contracts, tests).  
**Method:** Source-code inspection, end-to-end flow tracing, cross-reference with `CURRENT_STATE.md` and `TUTORPUTOR_DEEP_PRODUCT_REALITY_AUDIT_2026-04-19.md`.

---

## 1. Executive Summary

### Overall Verdict
TutorPutor has strong architectural ambition and broad module coverage, but contains **critical structural and security defects** that block production trust.

### What Truly Works
- Content generation Java service: full lifecycle, health, metrics, graceful shutdown.
- Content worker init: configurable startup with graceful degradation or hard-fail modes.
- Learner web auth context: JWT parsing, refresh, logout client calls, SSO code exchange.
- Admin auth hook: token sync to content studio API, Jotai atom state, dev bypass env-gated.
- Trusted proxy auth: hardened with explicit env flag + IP check + shared secret.
- Mobile app: secure MMKV keychain-backed storage.
- Stripe billing webhook: signature-verified.
- LTI platform admin CRUD, content studio validation + publish gating.

### What Blocks Production Trust
1. **Platform service cannot compile** — `setup.ts` imports from `./plugins/*.js` but `src/plugins/` does not exist.
2. **Consent middleware is dead code** — implemented with tests but never registered in runtime.
3. **Auth platform fallback defaults tenant to `"default"`** — breaks tenant isolation.
4. **Logout is a no-op** — refresh token remains valid in Redis after logout.
5. **Password validation is a no-op** — any password accepted for known email.
6. **RBAC is hardcoded in memory** — no database backing, no per-resource ABAC.

---

## 2. Deep Audit Scorecard

| Dimension | Rating | Justification |
|---|---|---|
| Completeness | Medium | Broad surface but missing plugin wiring, consent registration, RBAC schema, logout invalidation. |
| Simplicity | Medium | Learner IA clean; auth has hidden fallback complexity; admin authoring still manual. |
| Correctness | Medium-Low | Platform won't compile. Auth fallback uses default tenant. Password not verified. Logout no-op. |
| Consistency | Medium | Unified `/api/v1/` routes. Auth patterns differ between `auth/index.ts` and `requestContext.ts`. |
| UI/UX Quality | Medium-High | Well-organized routes, lazy loading, omnipresent AI tutor, good progressive disclosure. |
| Frontend Quality | Medium-High | React Query, error boundaries, auth context with refresh. Some UI duplication. |
| API/Contract Quality | Medium | Route inventory comprehensive. Auth contract mismatch in gateway fallback. Consent not enforced. |
| Backend/Workflow Quality | Medium-Low | Strong schema breadth. Platform setup broken. Worker init solid. Auth gaps. |
| Data/Persistence Quality | Medium-High | 60+ Prisma models. Missing RBAC relations. Missing password hash verification. |
| Observability/Operability | Medium | Health/metrics/tracing exist. Content gen service has /health, /ready, /metrics. |
| Privacy/Security/Trust | Medium-Low | Proxy hardening good. Consent not active. Auth fallback leaks tenant. Password not verified. |
| AI/ML Embedding | Medium | Tutor query, content generation, validation scoring. Queue can degrade silently. Consent not enforced. |
| Accessibility | Medium | Scoring in validation module. No direct app-level a11y evidence. |
| Responsiveness | Medium | Mobile-first layout claimed. Mobile app explicitly not production-ready. |
| End-to-End Quality | Medium | Happy paths work. Platform boot broken. Security fallback leaks. |

---

## 3. Surface-by-Surface and Layer-by-Layer Audit

### 3.1 Learner Web App (`apps/tutorputor-web`)

| Route | Status | Assessment |
|---|---|---|
| `/login` | Implemented | SSO providers fetched. SSO callback exchanges code. Legacy raw-token-in-URL still supported (security surface). |
| `/dashboard` | Implemented | Calls `/api/v1/learning/dashboard`. Omnipresent AI tutor. |
| `/pathways` | Implemented | Learning paths. |
| `/search` | Implemented | Canonical browse. `/modules` redirects here. |
| `/modules/:slug` | Implemented | Module detail. |
| `/assessments` | Implemented | Assessment list. |
| `/assessments/:id` | Implemented | Assessment detail. |
| `/marketplace` | Implemented | Browse + purchase. Mock checkout in dev. |
| `/collaboration` | Implemented | Threads and posts. |
| `/teacher` | Implemented | Classroom dashboard. |
| `/settings` | Implemented | Settings with privacy sub-route. |
| `/simulations` | Implemented | Simulation catalog. |
| `/simulations/studio/:id?` | Implemented | Simulation studio (lazy). |
| `/learn/:simulationId` | Implemented | Learner simulation flow. |
| `/analytics` | Implemented | Analytics dashboard (lazy). |
| AI Tutor | Implemented | Omnipresent floating component. `/ai-tutor` redirects to `/dashboard`. |

**Issues:**
- `AuthContext` stores `tenantId` in `localStorage` (XSS exposure).
- Legacy raw token params (`accessToken`, `refreshToken`) in URL still parsed on init.
- Multiple analytics entry points (`/analytics`, `/teacher`, dashboard) may confuse users.

### 3.2 Admin Web App (`apps/tutorputor-admin`)

| Surface | Status | Assessment |
|---|---|---|
| Content Studio | Implemented | CRUD, validation, publish gating. |
| Content Generation | Implemented | Rewired to `/api/content-studio/` endpoints. |
| Artifact Queries | Implemented | Examples, simulations, animations via real routes. |
| Auth Token Sync | Implemented | `contentStudioApi.setAuthToken()` on every auth change. |

**Issues:**
- `useAuth` hook has `DEV_TENANT_ID` fallback to `"default"` if env var missing in dev mode.

### 3.3 Mobile App (`apps/tutorputor-mobile`)

| Surface | Status | Assessment |
|---|---|---|
| Core Screens | Implemented | Navigation scaffold present. |
| Offline/Sync | Implemented | SQLite, MMKV, background sync. |
| Production Ready | **Not Ready** | README explicitly states "not a production-ready learner channel." |

### 3.4 API / Contract Layer

**Auth Routes (`/api/v1/auth/*`)**
| Route | Status | Issue |
|---|---|---|
| `/sso/providers` | Implemented | Returns provider list per tenant. |
| `/sso/exchange` | Implemented | Single-use code exchange with Redis. |
| `/me` | Implemented | Returns current user. |
| `/refresh` | Implemented | Refresh token rotation with Redis session store. |
| `/logout` | Implemented | Accepts refresh token but **does not invalidate it**. |
| `/revoke-all-sessions` | Implemented | Admin-only session revocation. |

**Critical API Issues:**
- `AuthService.logout()` is a no-op: does not delete refresh session from Redis.
- Platform gateway fallback in `AuthMiddleware.authenticate()` defaults `tenantId` to `"default"` from header.
- `requireTenantAccess()` compares JWT tenant against raw `x-tenant-id` header.

### 3.5 Backend / Business Logic Layer

**Platform Setup (`services/tutorputor-platform/src/setup.ts`)**
- Imports `setupCorePlugins` from `./plugins/core.js` and similar for content-modules, business-modules, admin-modules, workers.
- **No `plugins/` directory exists under `src/`.** Platform service cannot compile or start.

**Content Worker Init (`src/startup/content-worker-init.ts`)**
- Well-implemented: parses Redis URL, supports `requireContentWorker` hard-fail, graceful degradation, health check, close lifecycle.
- Tests cover disabled, healthy, degraded, and required-fail modes.

**Auth Middleware (`src/auth/index.ts`)**
- `AuthMiddleware.authenticate()` platform gateway fallback: `tenantId: (request.headers["x-tenant-id"] as string) ?? "default"`.
- `requireTenantAccess()` uses `request.headers["x-tenant-id"]` for comparison.
- `RBACManager` hardcodes roles/permissions in constructor memory.
- `PrismaUserRepository` returns empty `roles: []` and `permissions: []` with `// SCHEMA_UPDATE` comments.
- `validateCredentials()` does not verify password hash: `// For now, assume password is valid`.

### 3.6 Data / Persistence Layer

**Prisma Schema Strengths:**
- 60+ models: learning, content studio, simulation, collaboration, gamification, tenant, VR, notifications, payments, LTI.
- `DeviceToken`, `Badge`, `BadgeEarned`, `UserPoints`, `KernelPlugin`, `AutomationRule`, `ClaimAnimation`, `ClaimSimulation`, `ClaimExample`.

**Prisma Schema Gaps:**
- No `UserRole` or `RolePermission` relations. `User` has single `role` string field.
- `lastLoginAt` field missing.
- Consent model not visible in schema (tests stub it).

### 3.7 Async / Background Layer

- Content generation queue: BullMQ with processor pipeline.
- Queue disabled by default in local dev (documented).
- Dead letter queue, job deduplication, circuit breaker utilities exist.

### 3.8 Integration Layer

- Stripe: webhook with HMAC signature verification, subscription routes with JWT.
- LTI: launch, JWKS, config, deep-linking, grade passback, platform admin CRUD.
- gRPC: Java content generation service with health/metrics. Node client with TLS option.
- Redis: auth sessions, SSO codes, caching, pub/sub.
- AI: OpenAI + Ollama. Fallback to local Ollama if unconfigured.

### 3.9 Governance / Privacy / Security / Trust

- Trusted proxy auth: hardened with env flag + IP range + shared secret. Production startup assertion prevents accidental enablement.
- Consent middleware: **fully implemented but never registered.** 451 response, minor protection, TTL cache, comprehensive tests — all dead code.
- Tenant isolation: enforced in most routes. Platform gateway fallback path leaks tenant.
- Data residency: not explicitly enforced in current code paths.
- Audit trail: content studio has event/provenance. Auth session store in Redis enables revocation.

---

## 4. End-to-End Flow Review

### Flow 1: Learner Login → Session → Logout

| Step | Evidence | Finding |
|---|---|---|
| Visit `/login` | `LoginRoutePage.tsx` | Fetches SSO providers. |
| SSO callback | `AuthContext.tsx:104-120` | Exchanges `sso_code` for tokens via `/api/v1/auth/sso/exchange`. |
| Token storage | `AuthContext.tsx:115` | `persistTokens()` stores in secure storage. |
| Validation | `AuthContext.tsx:148-170` | Calls `/api/v1/auth/me`, parses JWT fallback. |
| Refresh | `AuthContext.tsx:228-247` | POSTs `/api/v1/auth/refresh`. |
| Logout | `AuthContext.tsx:205-223` | POSTs `/api/v1/auth/logout` with refresh token. |

**Issue:** `AuthService.logout()` does not invalidate the refresh session in Redis. The token remains usable indefinitely.

### Flow 2: Admin Authoring → Validate → Publish

| Step | Evidence | Finding |
|---|---|---|
| Create experience | `content/studio/routes.ts` | POST `/api/content-studio/experiences`. |
| Generate claims | `content/studio/service.ts` | Async BullMQ job. |
| Poll progress | `content/studio/routes.ts` | GET `/api/content-studio/experiences/:id/progress`. |
| Validate | `content/studio/service.ts` | `canPublish` checks artifact coverage + pillar scores. |
| Publish | `content/studio/routes.ts` | Gated by validation. |

**Issue:** If content worker is not running (local dev default), generation jobs enqueue but never process. UI shows stuck progress without error or remediation guidance.

### Flow 3: AI Tutor Query

| Step | Evidence | Finding |
|---|---|---|
| User asks question | `OmnipresentAITutor.tsx` | Floating chat. |
| Frontend sends | `api/v1/ai/tutor/query` | AI module route. |
| Backend resolves tenant | `requestContext.ts` | Uses JWT claim or trusted header. |
| Backend queries AI | `ai/routes.ts` | OpenAI or Ollama. |
| Response | Frontend | Raw text answer. |

**Issue:** No consent check before AI processing. Consent middleware exists but is not registered.

### Flow 4: Stripe Subscription

| Step | Evidence | Finding |
|---|---|---|
| Browse marketplace | `MarketplacePage.tsx` | Lists products. |
| Checkout | Frontend | Stripe checkout or mock in dev. |
| Webhook callback | `integration/billing/webhook` | Signature verified. |
| Subscription updated | `paymentRoutes.ts` | JWT identity. |

**Assessment:** Well-implemented with signature verification and JWT identity.

---

## 5. Comprehensive Findings Catalog

| ID | Severity | Category | Title | Dimension | Evidence |
|---|---|---|---|---|---|
| F-001 | **Critical** | Backend / Build | Platform service references non-existent plugin files | Correctness | `setup.ts` imports from `./plugins/*.js`; no `src/plugins/` directory |
| F-002 | **Critical** | Privacy / Security | Consent middleware implemented but not registered | Correctness | `consent-enforcement.ts` exists with tests; grep finds zero runtime registration |
| F-003 | **High** | Security / Tenant Isolation | Auth platform fallback defaults tenant to `"default"` | Correctness | `auth/index.ts:484` |
| F-004 | **High** | Security / Session | Logout does not invalidate refresh token | Correctness | `auth/index.ts:632-635` |
| F-005 | **High** | Security / Authentication | Password validation is a no-op | Correctness | `auth/index.ts:131-133` |
| F-006 | **High** | Security / Authorization | RBAC hardcoded in memory, not schema-backed | Correctness | `auth/index.ts:296-421` |
| F-007 | Medium | Security / Frontend | AuthContext stores tenantId in localStorage | Correctness / Privacy | `AuthContext.tsx:160,192` |
| F-008 | Medium | Security / UX | Legacy token-in-URL path still active | Security / Simplicity | `AuthContext.tsx:124-144` |
| F-009 | Medium | Operations / UX | Content worker queue default disabled in dev | Completeness | `docker-compose.yml`, `bin/ttr-dev` |
| F-010 | Medium | Security / Frontend | Admin useAuth DEV_TENANT_ID fallback to `"default"` | Correctness | `useAuth.ts:44-46` |
| F-011 | Medium | Security / Tenant Isolation | Auth module tenant check uses raw header | Correctness | `auth/index.ts:548-549` |
| F-012 | Medium | Product / UX | Mobile app not production-ready | Completeness | README explicit statement |
| F-013 | Medium | AI / Trust | AI tutor responses lack provenance metadata | Correctness / Trust | `OmnipresentAITutor.tsx`, AI routes |
| F-014 | Low | UX / Simplicity | Overlapping analytics entry points | Simplicity | `/analytics`, `/teacher`, `/dashboard` |
| F-015 | Low | Testing | E2E tests use trusted headers instead of full JWT | Correctness | `tests/e2e/ContentStudio.spec.ts` |

---

## 6. Completeness Gap Inventory

### Missing Runtime Wiring
- [ ] `src/plugins/core.js` and sibling plugin files referenced by `setup.ts` do not exist.
- [ ] Consent middleware preHandler is never attached to any route or plugin.
- [ ] `UserConsent` Prisma model may not exist (tests stub it).

### Missing Schema Relations
- [ ] `UserRole` / `RolePermission` relations for RBAC.
- [ ] `User.lastLoginAt` field.

### Missing Auth Behaviors
- [ ] Refresh token invalidation on logout.
- [ ] Password hash verification.
- [ ] Token blacklist / revocation list.

### Missing Mobile Surface
- [ ] Production-ready learner app shell and navigation entrypoint.

### Missing AI/Trust Surfaces
- [ ] Provenance metadata on AI tutor responses.
- [ ] Confidence scores and model version display.
- [ ] Consent gate before AI processing.

### Missing Operational Surfaces
- [ ] Content worker offline / unavailable banner in admin studio.
- [ ] Automatic retry/resume for stuck generation jobs.

---

## 7. Simplification Plan

### Remove
- [ ] Legacy raw-token-in-URL parsing from `AuthContext.tsx` (line 124-144).
- [ ] `DEV_TENANT_ID` `"default"` fallback from `useAuth.ts`.
- [ ] Redundant analytics entry points — consolidate under persona-specific routes.

### Merge
- [ ] Merge overlapping analytics/insights surfaces into persona-specific dashboards.
- [ ] Consolidate duplicated UI components between app-local and shared `@tutorputor/ui` package.

### Automate
- [ ] Auto-detect content worker unavailability and show clear banner instead of stuck progress.
- [ ] Auto-retry failed generation jobs with exponential backoff.
- [ ] Auto-suggest missing claim/task/artifact links before publish gating blocks.

### Infer / Prefetch
- [ ] Derive tenantId from JWT on every request instead of storing in localStorage.
- [ ] Prefetch dashboard data during login flow to reduce perceived latency.

### Hide / Defer
- [ ] Hide simulation studio and animation editor behind capability gates until worker is healthy.
- [ ] Move advanced authoring controls behind "expert mode" toggle.

---

## 8. Correctness Review Register

### Misleading / Incorrect UI States
- [ ] Dashboard progress indicators may show stale data if React Query cache is not invalidated after content updates.
- [ ] Content generation progress bar can be stuck at 0% forever if worker is offline with no error state.

### Incorrect Workflow Logic
- [ ] Logout workflow completes successfully while the session remains valid in Redis.
- [ ] Content publish gate blocks on missing artifacts but does not auto-suggest remediation steps.

### Incorrect Validations
- [ ] Password validation accepts any string for any known email address.
- [ ] Tenant isolation check trusts raw `x-tenant-id` header instead of JWT claim alone.

### Incorrect API Semantics
- [ ] Platform gateway fallback creates a user with `tenantId: "default"` when header is missing.
- [ ] Logout endpoint returns `{ success: true }` without performing invalidation.

### Backend Logic Mismatches
- [ ] `RBACManager` claims to enforce permissions but `PrismaUserRepository` returns empty arrays.
- [ ] `AuthMiddleware.authorize()` checks against in-memory permissions that may not match JWT roles.

### Data State Mismatches
- [ ] `User.role` is a string in Prisma but `RBACManager` expects a role object with permissions array.

### Incorrect Async Behavior
- [ ] Content generation jobs enqueue successfully but may never process if worker is disabled.

### Incorrect AI/ML Placement
- [ ] AI tutor operates without consent gate, violating intended privacy policy.
- [ ] AI responses carry no provenance, making correctness unverifiable.

---

## 9. Consistency Review Register

### Terminology Drift
- [ ] `auth/index.ts` uses `AuthJwtClaims` with `sub` field; `requestContext.ts` uses `JwtUser` with `id/sub/userId` alternatives.
- [ ] `auth/index.ts` uses `role` (singular string); `RBACManager` uses `roles` (array).

### State/Status Drift
- [ ] `AuthContext` stores auth in React state; `useAuth` (admin) stores in Jotai atom. Different persistence patterns.
- [ ] Content generation progress state in frontend may not match actual BullMQ job state.

### Component Drift
- [ ] Admin and learner apps both implement auth context logic rather than sharing a single auth provider from `@tutorputor/ui`.

### API Pattern Drift
- [ ] `auth/index.ts` returns plain JSON replies; `requestContext.ts` throws `HttpError` objects with codes.
- [ ] Some routes use `config: { public: true }` for unguarded routes; others rely on global middleware skip logic.

### Validation Drift
- [ ] `auth/index.ts` uses inline zod imports; other modules may use different validation patterns.
- [ ] `requestContext.ts` uses `getTrustedHeader` with array flattening; `auth/index.ts` does not normalize header arrays consistently.

---

## 10. API / Backend / Data Review

### Contract Quality
- Route prefix unified under `/api/v1/` — good.
- Auth contract has refresh/logout routes — good.
- Platform gateway fallback leaks tenant context — bad.
- Consent contract exists but is not enforced — bad.

### Workflow Support
- Content generation queue + polling + validation + publish gating is a complete workflow.
- Auth login + refresh is complete.
- Auth logout is incomplete (no invalidation).
- LTI launch + grade passback is complete.

### Business Logic Soundness
- Content validation pillar scoring (educational/experiential/technical/safety/a11y) is well-designed.
- `canPublish` gate enforces artifact coverage.
- Password login accepts any password — unsound.
- Tenant isolation trusts raw header — unsound.

### Data Model Alignment
- Prisma schema supports content studio workflows well.
- Schema does not support RBAC or consent.
- `User` model lacks `lastLoginAt`.

---

## 11. AI/ML Embedding Plan

### Where AI Is First-Class
- Tutor query routes (`/api/v1/ai/tutor/query`).
- Content generation gRPC service and worker processors.
- Content validation scoring (structural checks).

### Where AI Is Too Shallow
- AI tutor responses have no provenance, confidence, or citation metadata.
- No consent gate before AI processing.
- No human review trigger for low-confidence educational content.

### Where Automation Should Increase
- Auto-complete missing claim-to-artifact links before publish block.
- Auto-retry failed generation jobs.
- Auto-detect content quality regressions post-publish.

### Where AI Exposure Should Reduce
- Remove low-level generation controls from primary learner flows.
- Hide model/provider selection from non-admin users.

### Trust/Fallback/Escalation
- Worker optionalization silently disables generation capabilities.
- No explicit capability registry for AI feature gating in UI.

### Privacy/Security Implications
- AI routes process data without consent enforcement.
- No audit log of which model/version generated which educational content.

| Opportunity | Function | Mode | Confidence | Priority |
|---|---|---|---|---|
| AI tutor provenance | Attach model/version/citations | Auto | High | P1 |
| Consent gate for AI | Block AI routes without consent | Enforce | High | P0 |
| Auto-link artifacts | Suggest missing claim links | Assist | Medium | P1 |
| Content quality regression | Detect post-publish degradation | Auto | Medium | P2 |
| Generation job auto-retry | Exponential backoff for failed jobs | Auto | High | P1 |

---

## 12. Trust / Privacy / Security / Observability Review

### User-Facing Visibility
- [ ] No visible consent management UI in learner app.
- [ ] No visible audit trail for AI-generated answers.
- [ ] No visible tenant indicator in UI (operator/admin only).

### Operational Transparency
- [ ] Content worker health not surfaced in admin UI.
- [ ] Queue depth / job status not visible in admin studio.

### Auditability
- [ ] Content studio has event/provenance records.
- [ ] Auth session store in Redis enables revocation tracking.
- [ ] No audit log for consent grants/revocations.

### Permission Clarity
- [ ] Role names (`student`, `instructor`, `admin`) are clear but not customizable per tenant.
- [ ] No UI for viewing or managing permissions.

### Sensitive Action Handling
- [ ] Publish gate requires validation pass — good.
- [ ] Session revocation requires admin role — good.
- [ ] Logout does not actually end session — bad.

### Safe Defaults
- [ ] Trusted proxy auth requires explicit env + IP + secret — good.
- [ ] `assertTrustedProxyNotEnabledInProduction()` throws on misconfig — good.
- [ ] Content worker defaults to enabled in `server.ts` but dev topology may disable — acceptable if documented.

### Diagnosability
- [ ] Health endpoints at `/health`, `/metrics`.
- [ ] Content generation service has `/health`, `/ready`, `/metrics`.
- [ ] Structured logging with Pino.
- [ ] Correlation IDs via tracing middleware.

---

## 13. Design System / Reuse / Abstraction Review

### Inconsistent Components
- [ ] Auth context logic duplicated between `tutorputor-web` and `tutorputor-admin`.
- [ ] `AuthContext` uses React Context; `useAuth` (admin) uses Jotai. No shared auth state primitive.

### Duplicated Patterns
- [ ] Token read/write/storage utilities partially duplicated across apps despite `@tutorputor/ui` shared package.
- [ ] Error handling patterns differ between `auth/index.ts` (reply.send) and `requestContext.ts` (throw HttpError).

### Missing Shared Abstractions
- [ ] No shared auth provider component used by both web and admin.
- [ ] No shared consent management component.
- [ ] No shared capability registry UI gating primitive.

### Standardization Opportunities
- [ ] Unify auth middleware patterns: always use JWT claims, never trust raw headers for identity/tenant.
- [ ] Unify error response shape across all route modules.
- [ ] Unify loading/empty/error state components across learner and admin apps.

---

## 14. Prioritized Remediation Roadmap

### Immediate (Week 1)

| # | Task | Effort | Impact | Owner |
|---|---|---|---|---|
| 1 | **Create missing plugin files or fix `setup.ts` imports** | Medium | Critical — platform unbootable | Backend |
| 2 | **Register consent middleware in runtime** | Low | Critical — legal/compliance risk | Backend |
| 3 | **Fix auth platform fallback default tenant** | Low | High — tenant isolation | Backend |
| 4 | **Implement refresh token invalidation in logout** | Low | High — session security | Backend |
| 5 | **Add password hash verification** | Low | High — auth bypass | Backend |

### Short-Term (Weeks 2-4)

| # | Task | Effort | Impact | Owner |
|---|---|---|---|---|
| 6 | Add RBAC Prisma models and migrate RBACManager | Medium | High — enterprise readiness | Backend / Data |
| 7 | Remove legacy token-in-URL parsing from web app | Low | Medium — security surface | Frontend |
| 8 | Remove tenantId from localStorage; derive from JWT | Low | Medium — XSS reduction | Frontend |
| 9 | Add content worker unavailable banner in admin UI | Low | Medium — UX clarity | Frontend |
| 10 | Add provenance metadata to AI tutor responses | Medium | Medium — trust | Backend / AI |
| 11 | Add consent gate before AI routes | Low | High — compliance | Backend |
| 12 | Add auto-retry for failed generation jobs | Medium | Medium — reliability | Backend |

### Medium-Term (Months 2-3)

| # | Task | Effort | Impact | Owner |
|---|---|---|---|---|
| 13 | Complete mobile app production readiness | High | Medium — channel expansion | Mobile |
| 14 | Build independent content correctness evaluator | High | Medium — pedagogical trust | Backend / ML |
| 15 | Add golden dataset regression tests for generated artifacts | High | Medium — quality | Backend / QA |
| 16 | Unify auth context between web and admin apps | Medium | Medium — consistency | Frontend |
| 17 | Add capability registry for UI feature gating | Medium | Medium — runtime truth | Backend |
| 18 | Implement per-resource ABAC checks | High | High — granular security | Backend |

### Long-Term (Months 3-6)

| # | Task | Effort | Impact | Owner |
|---|---|---|---|---|
| 19 | Multi-region deployment and data residency enforcement | High | Medium — sovereignty | Platform / SRE |
| 20 | Plugin marketplace with install/activate/migrate | High | Medium — ecosystem | Backend |
| 21 | Full compliance evidence package generation | High | Medium — enterprise sales | Backend / Legal |
| 22 | Benchmark and optimize content generation throughput | Medium | Medium — scale | Backend / ML |

---

## 15. Final Ideal Product Experience Vision

### What the User Experiences
- A learner opens the app, sees personalized pathways, and starts learning immediately. AI tutor is available with one tap, and every answer shows a confidence score and source citation.
- An educator opens the admin studio, creates an experience, and AI auto-generates claims, examples, simulations, and animations. The publish gate shows exactly what is missing and offers one-click auto-fix.
- An operator deploys the platform with a single command. Health dashboards show real-time system status. If a content worker is offline, the UI shows a clear banner and queues jobs for retry.

### What the System Handles Automatically
- Tenant isolation enforced cryptographically via JWT on every request.
- Consent checked before any AI or analytics processing.
- Passwords verified with modern hashing (argon2).
- Sessions invalidated completely on logout.
- Content generation retried automatically with exponential backoff.
- Low-confidence or risky AI outputs routed to human review.

### How Trust Is Maintained
- Every AI-generated educational claim carries provenance: model, version, source, confidence.
- Every data processing action is logged with tenant, user, policy decision, and timestamp.
- Users can view and revoke consent at any time from `/settings/privacy`.
- Security defaults are fail-closed: no auth bypass, no default tenant, no unverified password.

### How the Full Stack Coheres
- One auth system shared across web, admin, and mobile.
- One API contract with consistent error shapes, pagination, and tenant scoping.
- One database schema with proper RBAC relations, consent records, and audit events.
- One deployment topology where every service health is visible and every dependency failure is handled gracefully or fails loudly.

---

## 16. Executive Summary Lists

### Top 10 Critical Issues
1. F-001: Platform service references non-existent plugin files — cannot compile/start.
2. F-002: Consent middleware implemented but not registered — AI/analytics run without consent.
3. F-003: Auth platform fallback defaults tenant to `"default"` — tenant isolation breach.
4. F-004: Logout does not invalidate refresh token — session hijacking persists after logout.
5. F-005: Password validation is a no-op — any password accepted.
6. F-006: RBAC hardcoded in memory — no database-backed permissions.
7. F-009: Content worker disabled by default in dev — stuck generation jobs with no UX feedback.
8. F-011: Tenant check uses raw header — spoofable isolation boundary.
9. Missing `UserRole`/`Permission` Prisma models — blocks enterprise RBAC.
10. Missing refresh token blacklist — logout is ineffective.

### Top 10 Completeness Gaps
1. Missing plugin files for platform setup.
2. Consent middleware not wired to runtime.
3. `UserConsent` Prisma model not confirmed in schema.
4. No password hash verification.
5. No refresh token invalidation.
6. No RBAC schema relations.
7. Mobile app not production-ready.
8. AI tutor responses lack provenance.
9. No content worker offline banner in admin UI.
10. No automatic retry for failed generation jobs.

### Top 10 Simplification Opportunities
1. Remove legacy token-in-URL parsing.
2. Remove `DEV_TENANT_ID` fallback.
3. Consolidate analytics entry points by persona.
4. Merge auth context logic into shared `@tutorputor/ui` provider.
5. Auto-suggest missing artifacts before publish block.
6. Auto-retry failed generation jobs.
7. Derive tenantId from JWT instead of localStorage.
8. Show worker health status inline in studio.
9. Hide advanced authoring controls behind expert toggle.
10. Consolidate error response shapes across all modules.

### Top 10 Correctness Issues
1. Platform setup imports missing files.
2. Consent middleware never executes.
3. Auth fallback default tenant.
4. Logout no-op.
5. Password verification missing.
6. RBAC returns empty permissions.
7. Tenant isolation trusts raw header.
8. Content generation can silently stall.
9. AI operates without consent check.
10. `PrismaUserRepository` returns mock `isActive: true`.

### Top 10 Consistency Issues
1. Auth context uses React Context vs Jotai in admin.
2. `role` (string) vs `roles` (array) naming.
3. `AuthJwtClaims` vs `JwtUser` shape differences.
4. Error handling: `reply.send` vs `throw HttpError`.
5. Header array normalization inconsistent.
6. Inline zod imports vs centralized validation.
7. Token storage: secure storage vs localStorage vs MMKV.
8. Dev bypass patterns differ between web and admin.
9. Route public access: `config.public` vs middleware skip.
10. Progress state in frontend may not match BullMQ job state.

### Top 10 API / Backend / Data Issues
1. Missing plugin files block platform boot.
2. Consent middleware unregistered.
3. Platform gateway fallback tenant leak.
4. Logout endpoint ineffective.
5. Password hash unverified.
6. RBAC not schema-backed.
7. `User` model lacks `lastLoginAt`.
8. `UserConsent` model missing from schema.
9. Tenant isolation checks raw header.
10. `PrismaUserRepository` returns hardcoded `isActive: true`.

### Top 10 AI/ML Opportunities
1. Add provenance metadata to every AI tutor response.
2. Enforce consent before AI processing.
3. Auto-link orphan claim artifacts.
4. Auto-retry failed generation jobs.
5. Add confidence scoring and human review trigger.
6. Content quality regression detection post-publish.
7. Model version audit log for all generated content.
8. Capability registry gating for AI features in UI.
9. Independent correctness evaluator for generated claims.
10. Golden dataset regression tests.

### Top 10 Trust / Privacy / Security / Observability Improvements
1. Register consent middleware.
2. Fix auth fallback tenant leak.
3. Implement logout token invalidation.
4. Add password hash verification.
5. Add RBAC schema + enforcement.
6. Remove tenantId from localStorage.
7. Remove legacy token-in-URL support.
8. Add audit log for AI model usage.
9. Add consent management UI in settings.
10. Add content worker health dashboard.

---

*End of audit.*
