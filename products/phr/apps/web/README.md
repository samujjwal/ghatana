# PHR Nepal — Web Application (`@ghatana/phr-web`)

**Package:** `@ghatana/phr-web`  
**Status:** Alpha — Core UI Complete  
**Stack:** React 19 · TypeScript (strict) · Vite · Tailwind CSS · Vitest · Playwright

---

## Purpose

The PHR web application provides the patient and clinician-facing UI surface for PHR Nepal. It renders patient dashboards, clinical records, consent management, lab results, medications, appointments, emergency access workflows, and settings — all backed by the PHR Java API.

---

## Routes

| Path                | Description                       |
| ------------------- | --------------------------------- |
| `/login`            | Authentication entry point        |
| `/dashboard`        | Patient overview dashboard        |
| `/records`          | Clinical records list             |
| `/records/:id`      | Clinical record detail            |
| `/appointments`     | Appointment schedule              |
| `/labs`             | Lab results                       |
| `/medications`      | Medications list                  |
| `/consent`          | Consent management                |
| `/emergency-access` | Emergency break-glass access view |
| `/settings`         | User and app settings             |

---

## Tech Stack

- React 19 + TypeScript strict (`strict: true`, `noImplicitAny: true`)
- React Router DOM v6
- Jotai (state management)
- Zod (runtime validation)
- Vite (build)
- Vitest (unit/integration tests)
- Playwright (E2E tests)
- `@ghatana/design-system`, `@ghatana/product-shell`, `@ghatana/platform-utils`, `@ghatana/theme`, `@ghatana/tokens`

---

## Scripts

| Script          | Command                        | Purpose                    |
| --------------- | ------------------------------ | -------------------------- |
| `type-check`    | `tsc --noEmit`                 | TypeScript type validation |
| `build`         | `tsc --noEmit && vite build`   | Production build           |
| `test`          | `vitest run`                   | Unit tests                 |
| `test:coverage` | `vitest run --coverage`        | Test coverage report       |
| `test:e2e`      | `playwright test`              | Playwright E2E tests       |
| `test:e2e:a11y` | `playwright test --grep @a11y` | Accessibility E2E tests    |
| `lint`          | `eslint src --max-warnings=0`  | Lint checks                |
| `dev`           | `vite`                         | Local dev server           |

---

## Lifecycle Commands (from repo root)

```bash
# Via Kernel lifecycle runner (canonical method)
pnpm validate:phr        # Type-check + test
pnpm test:phr            # Run all tests
pnpm build:phr           # Production build

# Directly (for development)
pnpm --filter ./products/phr/apps/web type-check
pnpm --filter ./products/phr/apps/web test
pnpm --filter ./products/phr/apps/web build
```

---

## Directory Structure

```
src/
  api/            API client utilities
  auth/           Auth context and guards
  layout/         App shell layout
  pages/          Route page components
  phrRouteContracts.ts    Route entitlement declarations
  phrRouteElements.tsx    Route-to-component mapping
  routeManifest.ts        Route manifest for kernel consumption
  routes.tsx              Router configuration
  __tests__/      Co-located tests
```

---

## Healthcare Compliance

- **Consent enforcement:** UI hides/shows records based on backend consent gate
- **PII display:** Sensitive PII fields follow consent-level display rules
- **Audit trail:** All sensitive record views are logged server-side
- **Unauthorized access:** Forbidden routes render explicit denial state, never silently empty
- **Accessibility:** Forms have labels, keyboard navigation works, focus states are visible

---

## Local Development

```bash
# From repo root — start local dev server
pnpm --dir products/phr/apps/web dev

# Or via Kernel dev phase
pnpm dev:phr
```

The app connects to the PHR Java backend at `http://localhost:8080` by default. For local dev without backend, the app shows a degraded state — it never shows fake success.
