# PHR Route Visibility and States

> **Purpose**: Documents how PHR routes are visible, hidden, blocked, or stable across web and mobile surfaces.
> **Canonical data**: [`products/phr/config/phr-route-contract.json`](../../config/phr-route-contract.json)
> **Last updated**: 2026-05-28

---

## 1. Overview

PHR uses the canonical route contract for route state. The web app imports a generated projection from that JSON contract, and backend entitlements read the same contract. There is no product-local feature-visibility file for route access.

| State | Behavior |
|---|---|
| `stable` | Listed for authorized users, has `apiEndpoint`, `policyId`, and `testId`, and must map to a real page component |
| `preview` | Present in the contract but not promoted to stable behavior |
| `hidden` | Excluded from navigation and renders `NotFoundPage` on direct links |
| `blocked` | Excluded from normal use and renders `ForbiddenPage` on direct links |

---

## 2. Web Enforcement

- `products/phr/apps/web/src/phrRouteContracts.ts` projects the canonical JSON contract.
- `products/phr/apps/web/src/phrRouteElements.tsx` maps stable routes to real components.
- Hidden routes render `NotFoundPage` even when directly linked.
- Blocked routes render `ForbiddenPage` even when directly linked.
- `scripts/check-phr-route-contract-parity.mjs` and `scripts/check-phr-feature-flag-enforcement.mjs` validate route-state parity.

---

## 3. Mobile Enforcement

Mobile screens use session role, secure session state, and service-level authorization. Mobile-only capabilities such as encrypted offline PHI, biometric prompts, and cache clearing are enforced by the mobile services and tests rather than route feature flags.

---

## 4. Promotion Flow

1. Implement the backend API and policy path.
2. Implement the web page or mobile screen behavior.
3. Set the route state in `phr-route-contract.json`.
4. Add or update route, page, and API tests.
5. Run route parity, API contract, i18n, a11y, and privacy checks.
