# TutorPutor Dependency Hardening Plan (P2-2)

**Date:** 2026-04-27  
**Status:** In Progress  
**Owner:** Platform Engineering

---

## Audit Findings

### 1. Duplicate UI Primitives

**Scope:** `@tutorputor/ui` (`libs/tutorputor-ui`) exports its own primitive components that partially duplicate `@ghatana/design-system`.

| Component | `@tutorputor/ui` | `@ghatana/design-system` | Action |
|---|---|---|---|
| `Button` | `src/components/primitives/Button.tsx` | `atoms/Button` | Migrate to canonical; delete `@tutorputor/ui` version |
| `Badge` | `src/components/primitives/Badge.tsx` | `atoms/Badge` | Migrate to canonical; delete `@tutorputor/ui` version |
| `Input` | `src/components/primitives/Input.tsx` | `atoms/Input` | Migrate to canonical; delete `@tutorputor/ui` version |
| `Spinner` | `src/components/primitives/Spinner.tsx` | `atoms/Spinner` or `atoms/Loader` | Migrate to canonical |
| `Charts` | `src/charts/` | `@ghatana/charts` | Evaluate overlap; migrate or wrap |
| `Assessment utils` | `src/assessment/` | No equivalent | Keep in `@tutorputor/ui/assessment` — product-specific |
| `Testing utils` | `src/testing/` | `@ghatana/platform-testing` | Evaluate overlap |
| `MinimalThemeProvider` | `src/components/MinimalThemeProvider.tsx` | `@ghatana/theme` | Replace with canonical theme provider |

**Migration path:**
1. Update all imports in `tutorputor-web` and `tutorputor-admin` from `@tutorputor/ui/components` → `@ghatana/design-system`
2. Remove the `primitives/` folder from `@tutorputor/ui`
3. Keep only product-specific sections (`assessment`, `charts` if not covered by `@ghatana/charts`)

---

### 2. Per-App API Clients (No Shared API Client from Contracts)

**Scope:** Each TutorPutor app has its own hand-rolled fetch wrapper.

| App | File | Pattern | Problem |
|---|---|---|---|
| `tutorputor-web` | `src/api/tutorputorClient.ts` | Native `fetch` with local types | Duplicates contract types; not generated from contracts |
| `tutorputor-web` | `src/api/contentApi.ts` | Separate fetch wrapper | Overlaps with `tutorputorClient.ts` |
| `tutorputor-web` | `src/api/assessmentApi.ts` | Separate fetch wrapper | Overlaps with `tutorputorClient.ts` |
| `tutorputor-admin` | `src/api/learning-hub.ts` | Separate fetch wrapper | Duplicates web-side API client patterns |

**Recommended consolidation:**

```
libs/tutorputor-core/src/api/
  client.ts            ← Base TutorPutor API client (auth token injection, error handling)
  modules.ts           ← Module-specific API calls
  assessments.ts       ← Assessment-specific API calls
  content-studio.ts    ← Content studio API calls
  billing.ts           ← Billing API calls
  index.ts             ← Re-exports
```

The base client should:
- Accept `baseUrl` and `getToken` as constructor dependencies (testable)
- Use types from `@tutorputor/contracts/v1`
- Handle 401 → token refresh → retry (single implementation)
- Emit structured log entries for requests/errors

**Migration path:**
1. Create `libs/tutorputor-core/src/api/client.ts` with shared client
2. Update web app to import from `@tutorputor/core/api`
3. Update admin app to import from `@tutorputor/core/api`
4. Delete per-app fetch wrappers

---

### 3. Auth Client and Token Lifecycle

**Scope:** Each app manages auth token lifecycle independently (no shared auth client).

**Findings:**
- `tutorputor-web` accesses token via cookie/localStorage pattern in the API client
- `tutorputor-admin` has dev auth bypass header patterns
- Mobile app (`tutorputor-mobile`) has its own token management (AsyncStorage — see P2-3)
- No shared `useAuth()` hook or `AuthProvider` that abstracts token lifecycle

**Recommended consolidation:**

```typescript
// libs/tutorputor-core/src/auth/
//   authClient.ts         ← Token fetch, refresh, logout
//   useAuth.ts            ← React hook for auth state
//   authAtom.ts           ← Jotai atom for auth (web/admin)
//   AuthProvider.tsx      ← React context provider
```

The shared auth client should use `@ghatana/sso-client` as the SSO adapter and:
- Handle token refresh automatically on 401
- Expose `isAuthenticated`, `user`, `tenantId`, `role` from a single source of truth
- Work in web (cookie) and admin (header) modes via adapter

**Migration path:**
1. Create `libs/tutorputor-core/src/auth/` with shared auth utilities
2. Update web app to use shared `AuthProvider` and `useAuth`
3. Update admin app to use same
4. Mobile auth handled separately (see P2-3)

---

### 4. Utility Functions (`cn`, formatters)

**Scope:** Both web and admin apps import `clsx` and `tailwind-merge` directly instead of using canonical `cn()` from `@ghatana/platform-utils`.

**Evidence:**
- `tutorputor-web/package.json`: depends on `clsx`, `tailwind-merge` directly
- `tutorputor-admin/package.json`: depends on `tailwind-merge`, `clsx` directly

**Migration path:**
```typescript
// Before
import { clsx } from "clsx";
import { twMerge } from "tailwind-merge";
const cn = (...classes: string[]) => twMerge(clsx(classes));

// After
import { cn } from "@ghatana/platform-utils";
```

Remove `clsx` and `tailwind-merge` from direct dependencies in both apps after migration.

---

### 5. Validation Schemas (No Shared Package)

**Scope:** Zod schemas for API payloads are duplicated across:
- Platform service routes (Zod schemas in route handlers)
- Web app API clients (TypeScript interfaces only)
- Contracts package (`@tutorputor/contracts`) exports TS types but not Zod runtime validators

**Recommended consolidation:**

Add Zod validation schemas to `@tutorputor/contracts` as a separate export path:

```
contracts/src/
  v1/
    schemas/           ← NEW: Zod schemas for runtime validation
      module.ts
      assessment.ts
      enrollment.ts
      billing.ts
      ai.ts
    types/             ← Existing TS types
    ...
```

Both the platform service and the web/admin API clients should import and use these schemas.

**Migration path:**
1. Add `zod` as a peer dependency to `@tutorputor/contracts`
2. Create `contracts/src/v1/schemas/` with canonical Zod schemas
3. Export from contracts via `"./schemas"` export path
4. Update platform routes to import schemas from contracts instead of defining locally
5. Update web/admin API clients to parse responses with canonical schemas

---

### 6. Telemetry / Event Contracts

**Scope:** There is no shared telemetry contract package for TutorPutor.

**Findings:**
- Platform service emits custom events/metrics using local string literals
- No shared `TutorPutorEvent` type or `TutorPutorMetric` enum

**Recommended consolidation:**
- Add canonical event schema types to `@tutorputor/contracts/v1/events`
- Add canonical metric name constants to `@tutorputor/contracts/v1/observability`

---

## Prioritized Action Items

| Priority | Item | Effort | Impact |
|---|---|---|---|
| P1 | Migrate UI primitives to `@ghatana/design-system` | Medium | High (reduces bundle, consistency) |
| P1 | Create shared API client in `@tutorputor/core` | Medium | High (auth token refresh, error handling) |
| P2 | Create shared auth client/hook in `@tutorputor/core` | Medium | High (token lifecycle correctness) |
| P2 | Migrate `cn()` usage to `@ghatana/platform-utils` | Low | Low |
| P3 | Add Zod schemas to `@tutorputor/contracts` | High | Medium (runtime safety) |
| P3 | Add telemetry event contracts to contracts package | Low | Medium (observability) |

---

## Current State (Before Migration)

```
tutorputor-web
  ├── @ghatana/design-system    ✅ canonical
  ├── @tutorputor/ui            ⚠️  has overlapping primitives
  ├── clsx + tailwind-merge     ⚠️  use @ghatana/platform-utils cn() instead
  ├── src/api/tutorputorClient  ⚠️  hand-rolled, not from contracts
  └── src/api/contentApi        ⚠️  separate wrapper, overlaps above

tutorputor-admin
  ├── @ghatana/design-system    ✅ canonical
  ├── @tutorputor/ui            ⚠️  has overlapping primitives
  ├── tailwind-merge            ⚠️  use @ghatana/platform-utils cn() instead
  └── src/api/learning-hub      ⚠️  hand-rolled, not from contracts

tutorputor-mobile
  ├── No @ghatana/* packages    ⚠️  no platform UI reuse
  ├── AsyncStorage for tokens   ❌  insecure (see P2-3)
  └── No shared API client      ⚠️  duplicates web patterns
```

---

*Last updated: 2026-04-27*
