# Audit Remediation Todo List

**Generated from:** audit-report.md (2026-04-23)
**Last Updated:** 2026-04-24
**Purpose:** Remaining tasks only — completed items have been removed
**Scope:** platform, platform-kernel, platform-plugins, shared-services, products/audio-video, products/data-cloud, products/aep, products/yappc

---

## Remaining Tasks

No remaining tasks. All items from the audit report have been completed.

---

## Completed Tasks (2026-04-24)

### Platform TypeScript

- **Build platform-typescript packages for @ghatana/state migration**
  - Location: `platform/typescript/state`
  - Status: COMPLETED
  - Details:
    - Built `@ghatana/state` package successfully
    - Package is available for imports in YAPPC
    - StateManager migration proceeded successfully

### YAPPC

- **Migrate `StateManager` callers to `@ghatana/state` primitives**
  - Location: `products/yappc/frontend/libs/yappc-state/src/store/StateManager.ts`
  - Status: COMPLETED
  - Details:
    - Replaced all `StateManager.createAtom(...)` calls with `createAtom(...)` from `@ghatana/state`
    - Replaced all `StateManager.createPersistentAtom(...)` calls with `createPersistentAtom(...)`
    - Replaced all `StateManager.createDerivedAtom(...)` calls with `createDerivedAtom(...)`
    - Replaced all `StateManager.createWritableDerivedAtom(...)` calls with `createWritableDerivedAtom(...)`
    - Removed `description` parameters from all atom creation calls
    - Fixed `storage` parameter values (`'local'` → `'localStorage'`, `'session'` → `'sessionStorage'`)
    - Migrated files: atoms.ts, workspaceAtoms.ts, projectAtoms.ts, builderAtoms.ts, aiAtoms.ts, mobile/atoms.ts
    - Removed StateManager exports from index.ts and yappc-ui
    - Added ESLint `no-restricted-imports` rule to flag StateManager usage
    - Added deprecation notice to StateManager.ts
    - Registry-based features (useGlobalState.ts, StateProvider.tsx, HydratorComponent.tsx) kept as compatibility layers since they fundamentally rely on the registry pattern

---

## Notes

- All tasks must follow `coding-instructions.md` guidelines
- Tests are required for all meaningful behavior changes
- Type safety is mandatory for all TypeScript/Java code
- Observability must be part of every feature
