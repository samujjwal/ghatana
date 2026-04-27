# TutorPutor Current Package Inventory

> Document type: Package/module inventory with verification status
> Generated: 2026-04-27T08:48:36-07:00
> Commit SHA: 8098a7043953aafe7bf9d8cb0d8738381d6b2ddd

---

## TypeScript Package Inventory

| Package | Path | Verification command | Current status |
|---|---|---|---|
| @tutorputor/contracts | `products/tutorputor/contracts` | `pnpm exec tsc -p tsconfig.json` | PASS |
| @tutorputor/platform | `products/tutorputor/services/tutorputor-platform` | `pnpm exec tsc --noEmit` | PASS |
| @tutorputor/core | `products/tutorputor/libs/tutorputor-core` | pending full matrix run | NOT VERIFIED IN THIS SESSION |
| @tutorputor/simulation | `products/tutorputor/libs/tutorputor-simulation` | pending full matrix run | NOT VERIFIED IN THIS SESSION |
| @tutorputor/ui | `products/tutorputor/libs/tutorputor-ui` | pending full matrix run | NOT VERIFIED IN THIS SESSION |
| @tutorputor/ai | `products/tutorputor/libs/tutorputor-ai` | pending full matrix run | NOT VERIFIED IN THIS SESSION |
| @tutorputor/web | `products/tutorputor/apps/tutorputor-web` | pending full matrix run | NOT VERIFIED IN THIS SESSION |
| @tutorputor/admin | `products/tutorputor/apps/tutorputor-admin` | pending full matrix run | NOT VERIFIED IN THIS SESSION |
| @tutorputor/mobile | `products/tutorputor/apps/tutorputor-mobile` | pending full matrix run | NOT VERIFIED IN THIS SESSION |
| @tutorputor/content-explorer | `products/tutorputor/apps/content-explorer` | pending full matrix run | NOT VERIFIED IN THIS SESSION |
| @tutorputor/api-gateway | `products/tutorputor/apps/api-gateway` | pending full matrix run | NOT VERIFIED IN THIS SESSION |
| @tutorputor/e2e-tests | `products/tutorputor/tests/e2e` | pending full matrix run | NOT VERIFIED IN THIS SESSION |
| @tutorputor/domain-loader | `products/tutorputor/tools/tutorputor-domain-loader` | pending full matrix run | NOT VERIFIED IN THIS SESSION |

---

## Java/Gradle Module Inventory

| Module | Path | Verification command | Current status |
|---|---|---|---|
| content generation service | `products/tutorputor/services/tutorputor-content-generation` | pending CI build/check | NOT VERIFIED IN THIS SESSION |
| content studio agents | `products/tutorputor/libs/content-studio-agents` | pending CI build/check | NOT VERIFIED IN THIS SESSION |

---

## Targeted Test Verification (Session Evidence)

| Test suite | Path | Result |
|---|---|---|
| P0-3 fail-closed auth/tenant/consent | `src/__tests__/p0-3-fail-closed-auth.integration.test.ts` | PASS (20/20) |
| Consent enforcement integration | `src/__tests__/consent-enforcement.integration.test.ts` | PASS (10/10) |
| P1-5 ABAC generation route matrix | `src/__tests__/p1-5-abac-route-matrix.integration.test.ts` | PASS (5/5) |

---

## Notes

- This file is intentionally conservative: entries are only marked PASS when a command was run in this session and passed.
- Full package matrix validation remains tracked under P0-1 and Phase 0 in `tutorputor_tasks.md`.
- CI workflow source for complete package gates: `products/tutorputor/.gitea/workflows/tutorputor-ci.yml`.
