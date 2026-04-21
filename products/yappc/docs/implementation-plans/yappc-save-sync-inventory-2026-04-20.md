# Yappc Save and Sync Inventory

Date: 2026-04-20
Purpose: Canonical inventory of local fallback repositories, optimistic save paths, and persistence mechanisms that affect user-visible truthfulness.

## Scope rule

- This inventory distinguishes mounted product paths from latent or infrastructure-only code.
- Only mounted paths should drive Track 8 UX work.
- Non-mounted persistence utilities are still listed so they do not get mistaken for production-ready sync behavior.

## Highest-risk mounted save paths

### 1. Canvas backend fallback service

- File: `products/yappc/frontend/web/src/services/canvasBackend.ts`
- Surface: mounted canvas flows
- Behavior:
  - Reads backend first.
  - Falls back to `localStorage` when backend load fails.
  - Writes to `localStorage` when backend save fails.
- Truthfulness risk:
  - User can continue with locally cached state after remote failure.
  - Remote-save failure is logged but not elevated into a first-class product save-state model.
- Risk class: P0

### 2. Canvas persistence service

- File: `products/yappc/frontend/web/src/services/canvas/CanvasPersistence.ts`
- Surface: mounted canvas orchestration and history tooling
- Behavior:
  - Supports `localStorage`, `indexedDB`, and `api` persistence modes.
  - Local history and snapshot storage are persisted into `localStorage`.
  - API persistence does not yet expose a user-facing distinction between remote-saved and local-only recovery states.
- Truthfulness risk:
  - Save-state semantics are not explicit enough for mounted users.
  - Local snapshot/history persistence can be mistaken for authoritative remote save.
- Risk class: P0

## Secondary mounted persistence paths

### 3. Onboarding completion and persona cache

- File: `products/yappc/frontend/web/src/components/workspace/OnboardingFlow.tsx`
- Surface: mounted onboarding flow
- Behavior:
  - Durable workspace/project creation now goes through the API.
  - Local storage still caches `yappc_active_personas`, `yappc_primary_persona`, and `onboarding_complete` after durable success.
- Truthfulness risk:
  - Low after remediation, because these keys now trail durable success instead of replacing it.
- Risk class: P2

### 4. Auth session storage

- File: `products/yappc/frontend/web/src/providers/auth-session.ts`
- Surface: mounted auth/session hydration
- Behavior:
  - Stores refresh/access-session data in `localStorage` for browser continuity.
- Truthfulness risk:
  - Session continuity concern, but not a save/sync false-truth path for workspace/project content.
- Risk class: P2

## Non-mounted or lower-priority persistence utilities

### 5. Offline queue service

- File: `products/yappc/frontend/web/src/services/offline/OfflineService.ts`
- Surface: utility layer, not currently proven as a mounted product workflow dependency
- Behavior:
  - Queues operations locally and attempts replay on reconnect.
- Truthfulness risk:
  - High if mounted without explicit sync-state UX.
  - Current audit priority is to avoid silently expanding this into mounted flows.
- Risk class: P1

### 6. Legacy canvas persistence helpers

- File: `products/yappc/frontend/web/src/services/persistence.ts`
- Surface: legacy/local-only canvas persistence helpers
- Behavior:
  - Uses `localStorage` for canvas save/load/history utilities.
- Truthfulness risk:
  - Medium as latent drift; should not be mistaken for the authoritative mounted save path.
- Risk class: P2

### 7. Persona context local cache

- File: `products/yappc/frontend/web/src/context/PersonaContext.tsx`
- Surface: local persona UX state
- Behavior:
  - Persists persona context to `localStorage`.
- Truthfulness risk:
  - Low for core workspace/project persistence after onboarding durability fix.
- Risk class: P3

## Required product behavior for Track 8.2

Mounted save surfaces should converge on explicit user-facing states:

- `local-only`
- `syncing`
- `remote-saved`
- `remote-failed`

At minimum, the mounted canvas route must stop collapsing backend failure and local fallback into the same user-visible success signal.

## Maintenance rule

- Any new fallback or offline persistence path must be added here before it is exposed in a mounted route.
- Any mounted save flow that uses local fallback must expose explicit UI state before it can be considered production-truthful.