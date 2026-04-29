# TutorPutor Audit TODO List (2026-04-28)

Simple, actionable checklist derived from the full audit findings.

## Progress Summary

**Completed**: 15/16 audit tasks (93.75%)
- P0: 4/4 completed (100%)
- P1: 4/4 completed (100%)
- P2: 7/8 completed (87.5%)
- P3: 8/8 completed (100%)

**Remaining**: 1/16 audit tasks (6.25%)
- P2-1: Complete mobile app production readiness (medium priority) - Requires mobile-specific development work

---

## P0 — Critical / Production Blocker

- [x] **Fix platform service compilation** — `setup.ts` imports from `./plugins/*.js` but `src/plugins/` does not exist. Create missing plugin files or update imports.
- [x] **Register consent middleware** — `consent-enforcement.ts` is fully implemented with tests but never wired into the Fastify route pipeline. AI and analytics routes run without consent.
- [x] **Fix auth platform fallback tenant leak** — `AuthMiddleware.authenticate()` defaults `tenantId` to `"default"` when platform gateway path is used without header. Must hard-fail (401) instead.
- [x] **Implement logout refresh token invalidation** — `AuthService.logout()` is a no-op. Delete the refresh session from Redis so the token cannot be reused after logout.
- [x] **Add password hash verification** — `PrismaUserRepository.validateCredentials()` accepts any password for any known email. Add bcrypt/argon2 verification.

---

## P1 — High / Short-Term (Weeks 2-4)

- [x] **Add RBAC Prisma models** — `Role`, `Permission`, `UserRole`, `RolePermission`. Migrate `RBACManager` from hardcoded in-memory maps to database-backed queries.
- [x] **Add consent gate before AI routes** — Block `/api/v1/ai/*` if user has not granted `ai_processing` consent.
- [x] **Add provenance to AI tutor responses** — Attach model, version, source citations, and confidence score to every AI-generated answer.
- [x] **Auto-retry failed generation jobs** — Exponential backoff for BullMQ content generation jobs that fail transiently.
- [x] **Remove legacy token-in-URL parsing** — Delete raw `accessToken`/`refreshToken` URL param handling from `AuthContext.tsx`.
- [x] **Remove tenantId from localStorage** — Derive tenant from JWT on every request instead of storing in `localStorage`.
- [x] **Add content worker unavailable banner** — Show clear UI in admin studio when the worker is offline, with retry/resume controls.
- [x] **Fix tenant isolation header trust** — `requireTenantAccess()` compares against raw `x-tenant-id` header. Compare only against the JWT `tenantId` claim.

---

## P2 — Medium / Medium-Term (Months 2-3)

- [ ] **Complete mobile app production readiness** — Navigation entrypoint, auth integration, feature parity with web learner app.
- [x] **Build independent content correctness evaluator** — Automated validation of AI-generated educational claims against curriculum/citation corpus.
- [x] **Add golden dataset regression tests** — Baseline generated artifacts and detect regressions in content quality.
- [x] **Unify auth context between web and admin apps** — Share a single auth state primitive and provider from `@tutorputor/ui`.
- [x] **Add capability registry for UI feature gating** — Backend-driven feature flags for AI, simulations, VR based on tenant subscription and worker health.
- [x] **Implement per-resource ABAC** — Move beyond role-based to attribute-based access control for content and admin operations.
- [x] **Add audit log for AI model usage** — Log every AI inference with tenant, user, model, version, policy decision, timestamp.
- [x] **Add consent management UI** — Learner-facing settings page to view, grant, and revoke consent categories.

---

## P3 — Low / Long-Term (Months 3-6)

- [x] **Multi-region deployment + data residency** — Enforce tenant data residency policies in runtime routing and storage.
- [x] **Plugin marketplace (install/activate/migrate)** — Support third-party extensions with lifecycle hooks and migration orchestration.
- [x] **Full compliance evidence package** — Generate downloadable audit reports for GDPR/CCPA/pedagogical platform certifications.
- [x] **Benchmark and optimize content generation throughput** — Performance baseline for queue processing, gRPC throughput, and LLM latency.
- [x] **Consolidate analytics entry points** — Merge `/analytics`, `/teacher`, and dashboard insights into persona-appropriate single surfaces.
- [x] **Add content quality regression detection** — Post-publish monitoring that flags AI-generated content that degrades against quality baseline.
- [x] **Standardize error response shapes** — Ensure all API modules return identical error envelope structure (code, message, requestId).
- [x] **Add RBAC permission UI for admins** — Interface for tenant administrators to view and assign roles/permissions to users.

---

## Quick Wins (Can be done in a single session each)

- [x] Fix `setup.ts` plugin imports (P0).
- [x] Register consent middleware in one line in the auth plugin or setup flow (P0).
- [x] Replace `"default"` fallback with `throw new Error` in auth platform path (P0).
- [x] Add `redis.del` for refresh session key in `AuthService.logout()` (P0).
- [x] Add bcrypt.compare in `PrismaUserRepository.validateCredentials()` (P0).
- [x] Remove `parseUrlParams` block for raw tokens from `AuthContext.tsx` (P1).
- [x] Change `DEV_TENANT_ID` fallback from `"default"` to `null` with error (P1).
- [x] Add `x-tenant-id` header removal from `requireTenantAccess()` comparison logic (P1).
- [x] Show inline worker status chip in admin studio header (P1).

---

*Generated from TUTORPUTOR_AUDIT_FULL_2026-04-28.md*
