# Platform TypeScript — Consolidation Guide

**Status:** Active  
**Last Updated:** 2026-04-24  
**Owner:** Platform TypeScript Team  
**Slack:** #platform-typescript  

---

## 1. Purpose

This document catalogues identified duplicate patterns across `platform/typescript/*`
and `products/yappc/frontend/libs/*`, and specifies the canonical implementation
that all packages must migrate to.

---

## 2. State Management Duplication

### 2.1 Duplicate: `@yappc/state` `StateManager` vs `@ghatana/state` atoms

**Problem:** `@yappc/state/src/store/StateManager.ts` re-implements atom registry,
persistence, and derived atom creation that already exists in `@ghatana/state`.

**Canonical:** `@ghatana/state` owns:
- `createAtom(key, initialValue, description)` — registered Jotai atom
- `createPersistentAtom(key, initialValue, options)` — localStorage-backed atom
- `createAsyncAtom(key, fetcher)` — async loadable atom
- `createDerivedAtom(key, deriveFn)` — derived computed atom

**Migration Path:**
1. Replace `StateManager.createAtom(...)` calls with `createAtom(...)` from `@ghatana/state`.
2. Replace `StateManager.createPersistentAtom(...)` with `createPersistentAtom(...)`.
3. Remove `StateManager` class once all callers are migrated.
4. Keep `@yappc/state` as a thin re-export of platform atoms + YAPPC-specific atoms only.

**Tracking:** All new code in `products/yappc/frontend/libs/` must use `@ghatana/state` directly.
`StateManager` is deprecated — do not add new callers.

---

### 2.2 Duplicate: Config hooks in `@yappc/state` vs `@yappc/config-schema`

**Problem:** `@yappc/state/src/config-hooks/` and `@yappc/state/src/hooks/useConfigData.ts`
duplicate config-loading logic that conceptually belongs in `@yappc/config-schema`.

**Canonical:**
- Config Zod schemas: `@yappc/config-schema/src/schemas/`
- Config validation utilities: `@yappc/config-schema/src/validation/`
- Config migration utilities: `@yappc/config-schema/src/migration/`
- React hooks that *use* config: `@yappc/state` (depends on `@yappc/config-schema`)

**Rule:** `@yappc/config-schema` must never import from `@yappc/state`.
Config schema is a pure data-validation layer with no React dependency.

---

## 3. API Client Duplication

### 3.1 Duplicate: `@yappc/api` vs `@yappc/core/api` subpath

**Problem:** `@yappc/core` exposes an `./api` subpath that partially duplicates
`@yappc/api` HTTP helpers.

**Canonical:** `@yappc/api` is the single YAPPC HTTP client package.

**Migration:**
1. `@yappc/core/api` subpath re-exports from `@yappc/api` for backward compatibility.
2. New code must import from `@yappc/api` directly.
3. Remove the `@yappc/core/api` subpath in the next major version.

---

## 4. Auth Duplication

### 4.1 Duplicate: `@yappc/auth` vs direct `@ghatana/sso-client` use

**Problem:** Some YAPPC app code calls `@ghatana/sso-client` directly, bypassing
`@yappc/auth`'s YAPPC-specific session logic and tenant binding.

**Canonical:** All YAPPC auth must go through `@yappc/auth`.
`@ghatana/sso-client` must only be imported within `@yappc/auth` itself.

**Rule:** ESLint `no-restricted-imports` rule in `products/yappc/frontend/` should
flag direct `@ghatana/sso-client` usage. Add this rule to the YAPPC ESLint config:

```json
{
  "rules": {
    "no-restricted-imports": ["error", {
      "paths": [{
        "name": "@ghatana/sso-client",
        "message": "Use @yappc/auth instead of importing @ghatana/sso-client directly in YAPPC app code."
      }]
    }]
  }
}
```

---

## 5. Platform TypeScript Utility Consolidation

### 5.1 Result / Error types

**Problem:** Multiple packages define their own `Result<T, E>` or `AsyncState<T>`
types independently.

**Canonical:** `@ghatana/platform-utils` exports:
- `Result<T, E>` — discriminated union `{ ok: true; value: T } | { ok: false; error: E }`
- `AsyncState<T>` — `loading | loaded | error` state machine

**Migration:** All packages must import from `@ghatana/platform-utils` rather than
defining local variants. Search with:
```bash
grep -r "type Result<\|interface Result<\|AsyncState" \
  platform/typescript products/yappc/frontend \
  --include="*.ts" --include="*.tsx" \
  ! -path "*/node_modules/*" ! -path "*/dist/*"
```

---

### 5.2 Observability naming

**Problem:** Metric and log field names are inconsistently cased across packages
(e.g., `correlationId` vs `correlation_id`).

**Canonical:** Camel-case in TypeScript types (`correlationId`); snake_case only
in JSON wire format and Prometheus metric names.
See `docs/architecture/SHARED_CONTRACTS.md §4` for the full spec.

---

## 6. CI Enforcement

Add the following checks to prevent re-emergence of these duplications:

```json
// .eslintrc — root rules applicable across platform/typescript
{
  "rules": {
    "no-restricted-imports": [
      "warn",
      {
        "patterns": [
          {
            "group": ["*/StateManager"],
            "message": "Use @ghatana/state atom primitives instead of StateManager."
          }
        ]
      }
    ]
  }
}
```

---

## 7. Open Items

| Item | Status | Assignee | Target |
|------|:---:|---|---|
| Migrate all `StateManager` callers to `@ghatana/state` | In progress | YAPPC Frontend | Next sprint |
| Remove `@yappc/core/api` subpath alias | Pending | YAPPC Frontend | Next major release |
| Add ESLint `no-restricted-imports` for `@ghatana/sso-client` | Pending | YAPPC Frontend | This sprint |
| Remove local `Result<T>` re-declarations | Pending | Platform TS | Next sprint |
