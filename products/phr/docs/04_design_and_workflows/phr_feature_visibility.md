# PHR Feature Visibility and Flags

> **Purpose**: Documents which PHR IA items are visible, hidden, or behind feature flags per persona and deployment context.
> **Canonical data**: [`products/phr/config/phr-feature-visibility.json`](../../config/phr-feature-visibility.json)
> **Last updated**: 2026-05-02

---

## 1. Overview

PHR uses a layered visibility model:

1. **Core routes** — always visible for authorized personas; backed by a stable route contract.
2. **Feature-flagged routes** — present in the route manifest but render a `FeatureFlagPage` placeholder until the backing service is promoted. Controlled by `featureFlag: true` in `phrRouteContracts.ts`.
3. **Deferred / not-implemented routes** — absent from the current route manifest; explicitly deferred to Phase 2 or later.
4. **Mobile-only features** — some capabilities (biometric auth, offline cache, push notifications) exist only on mobile and are controlled by feature flags in `phr-feature-visibility.json`.

---

## 2. Feature Flags

Feature flags are defined in [`phr-feature-visibility.json`](../../config/phr-feature-visibility.json) and control availability of major capabilities.

| Flag | Default | Description |
|------|---------|-------------|
| `ENABLE_MOBILE_OFFLINE_PHI` | `true` | AES-256-GCM encrypted offline cache for mobile dashboard PHI |
| `ENABLE_EMERGENCY_ACCESS` | `true` | Break-glass emergency access with biometric gate (mobile) and reason gate (web) |
| `ENABLE_FCHV_ROUTES` | `true` | FCHV community health volunteer routes and dashboard |
| `ENABLE_CAREGIVER_DELEGATED_ACCESS` | `true` | Caregiver delegated access to dependents list |
| `ENABLE_AUDIT_LOG` | `true` | Audit event trail page for patients and admins |
| `ENABLE_HIE_EXPORT` | `true` | Health Information Exchange FHIR bundle export from Settings |
| `ENABLE_PUSH_NOTIFICATIONS` | `true` | Push notification registration and PHI redaction handler on mobile |
| `ENABLE_BIOMETRIC_AUTH` | `true` | Biometric (FaceID/TouchID/Fingerprint) authentication on mobile |

---

## 3. Web Route Visibility Matrix

### 3.1 Core Routes (always visible for authorized persona)

| Route | Patient | Caregiver | Clinician | Admin | Notes |
|-------|---------|-----------|-----------|-------|-------|
| `/dashboard` | ✅ | ✅ | ✅ | ✅ | — |
| `/records` | ✅ | ✅ | ✅ | ✅ | — |
| `/records/:recordId` | ✅ | ✅ | ✅ | ✅ | Consent check per record |
| `/consents` | ✅ | ✅ | ✅ | ✅ | Patient grants/revocations |
| `/appointments` | ✅ | ✅ | ✅ | ✅ | — |
| `/labs` | — | ✅ | ✅ | ✅ | Min role: caregiver |
| `/medications` | — | ✅ | ✅ | ✅ | Min role: caregiver |
| `/emergency` | — | — | ✅ | ✅ | Break-glass; audit-logged |
| `/audit` | — | — | — | ✅ | Admin-only |
| `/release-readiness` | — | — | — | ✅ | Admin-only |
| `/settings` | ✅ | ✅ | ✅ | ✅ | — |
| `/profile` | ✅ | ✅ | ✅ | ✅ | — |
| `/timeline` | ✅ | ✅ | ✅ | ✅ | — |
| `/conditions` | ✅ | ✅ | ✅ | ✅ | — |
| `/observations` | — | ✅ | ✅ | ✅ | Min role: caregiver |
| `/immunizations` | ✅ | ✅ | ✅ | ✅ | — |
| `/documents` | ✅ | ✅ | ✅ | ✅ | — |
| `/documents/upload` | ✅ | ✅ | ✅ | ✅ | — |
| `/documents/:docId/ocr` | ✅ | ✅ | ✅ | ✅ | OCR review workflow |
| `/notifications` | ✅ | ✅ | ✅ | ✅ | — |

### 3.2 Feature-Flagged Routes (render FeatureFlagPage until promoted)

| Route | Min Role | Guarding Flag | Implementation Status |
|-------|----------|---------------|-----------------------|
| `/provider/dashboard` | clinician | `featureFlag: true` | Page component exists; wiring pending |
| `/provider/patients` | clinician | `featureFlag: true` | Page component exists; wiring pending |
| `/caregiver/dependents` | caregiver | `featureFlag: true` | Page component exists; wiring pending |
| `/fchv/dashboard` | clinician | `featureFlag: true` | Page component exists; wiring pending |

### 3.3 Deferred Routes (Phase 2 — not in current manifest)

| Route | Planned Purpose | Deferral Reason |
|-------|----------------|-----------------|
| `/medications/:medId` | Medication detail | Backend not implemented |
| `/referrals` | Referral management | Phase 2 |
| `/imaging` | Imaging viewer | Phase 2 |
| `/insurance` | Insurance/billing | Phase 2 |
| `/payments` | Payment history | Phase 2 |
| `/claims` | Claims tracking | Phase 2 |
| `/voice-input` | Patient voice dictation | Depends on `@ghatana/audio-video-*` |
| `/voice-dictation` | Provider voice dictation | Depends on `@ghatana/audio-video-*` |

---

## 4. Mobile Screen Visibility Matrix

| Screen | Patient | Caregiver | Clinician | Admin | Notes |
|--------|---------|-----------|-----------|-------|-------|
| Dashboard | ✅ | ✅ | ✅ | ✅ | Encrypted offline cache |
| Records | ✅ | ✅ | ✅ | ✅ | — |
| Consents | ✅ | ✅ | — | — | Patient/caregiver only |
| Notifications | ✅ | ✅ | ✅ | ✅ | PHI redacted in push payloads |
| Emergency | ✅ | — | ✅ | ✅ | Biometric gate; audit-logged |
| Settings | ✅ | ✅ | ✅ | ✅ | Logout + session clear |

---

## 5. Visibility Enforcement

### 5.1 Web

- Route access is enforced in `PhrProductShell.tsx` using `phrRouteAccess.isRouteAllowed(route, role)`.
- Feature-flagged routes are intercepted in `phrRouteElements.tsx` and render `<FeatureFlagPage />` when `featureFlag: true`.
- Unauthorized direct URL navigation redirects to `/forbidden` via `<Navigate to="/forbidden" replace />`.

### 5.2 Mobile

- Screen visibility is controlled in `App.tsx` by the active session role.
- Feature flags (e.g., biometric, offline cache) are checked at service call time.

### 5.3 Backend

- All routes check `PhrRouteSupport.canPerform*` methods for consent, role, and tenancy.
- Emergency access routes additionally require audit log creation before returning PHI.
- Backend entitlement manifest (`GET /phr/entitlements`) returns authorized routes for the authenticated session.

---

## 6. How to Promote a Feature-Flagged Route

1. Implement the backing service and page to production quality.
2. Remove `featureFlag: true` from the route in `phrRouteContracts.ts`.
3. Remove the `FeatureFlagPage` override from `phrRouteElements.tsx`.
4. Update `phr-usecase-baseline.json` status from `deferred` to `implemented`.
5. Update this doc and `phr-feature-visibility.json`.
6. Add route-level test coverage in `src/pages/__tests__/`.
7. Submit for Kernel IA coverage gate re-check (`node scripts/check-phr-ia-coverage.mjs`).
