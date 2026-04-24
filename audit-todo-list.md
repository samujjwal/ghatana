# Audit Remediation Todo List

**Generated from:** audit-report.md (2026-04-23)  
**Last Updated:** 2026-04-24  
**Purpose:** Remaining tasks only — completed items have been removed  
**Scope:** platform, platform-kernel, platform-plugins, shared-services, products/audio-video, products/data-cloud, products/aep, products/yappc

---

## Remaining Tasks

All P0, the majority of P1, P2, and cross-cutting tasks have been completed and verified.
The items below remain open and require engineering effort before they can be closed.

---

### YAPPC

- [ ] **Migrate `StateManager` callers to `@ghatana/state` primitives**
  - Location: `products/yappc/frontend/libs/yappc-state/src/store/StateManager.ts`
  - Tasks:
    - [ ] Replace all `StateManager.createAtom(...)` calls with `createAtom(...)` from `@ghatana/state`
    - [ ] Replace all `StateManager.createPersistentAtom(...)` calls with `createPersistentAtom(...)`
    - [ ] Remove `StateManager` class once all callers are migrated
    - [ ] Add ESLint `no-restricted-imports` rule to flag `StateManager` usage
  - Reference: `platform/typescript/CONSOLIDATION_GUIDE.md §2.1`

- [ ] **Remove `@yappc/core/api` subpath alias**
  - Location: `products/yappc/frontend/libs/yappc-core/package.json`
  - Tasks:
    - [ ] Migrate all callers of `@yappc/core/api` to import from `@yappc/api` directly
    - [ ] Remove the `./api` subpath export from `@yappc/core/package.json`
  - Reference: `platform/typescript/CONSOLIDATION_GUIDE.md §3.1`
  - Target: Next major release

- [ ] **Add ESLint `no-restricted-imports` for `@ghatana/sso-client`**
  - Location: `products/yappc/frontend/` ESLint config
  - Tasks:
    - [ ] Add rule to flag direct `@ghatana/sso-client` usage in YAPPC app code
    - [ ] Fix any existing violations
  - Reference: `platform/typescript/CONSOLIDATION_GUIDE.md §4.1`

- [ ] **Add YAPPC canvas rendering benchmark**
  - Location: `products/yappc/frontend/libs/yappc-ui/src/components/__tests__/`
  - Tasks:
    - [ ] Extend `performance-benchmarks.test.tsx` with canvas render scenarios
    - [ ] Add workflow rendering benchmark (multi-phase YAPPC lifecycle render)
    - [ ] Add live-preview render benchmark

- [ ] **Resolve `DataStorePort` adapter seam (knowledge-graph → data-cloud)**
  - Location: `products/yappc/core/knowledge-graph/build.gradle.kts`
  - Tasks:
    - [ ] Create `DataStorePort` interface in `yappc-shared`
    - [ ] Implement `DataCloudDataStoreAdapter` in `yappc-infrastructure`
    - [ ] Remove direct `products/data-cloud:platform-launcher` dependency from `knowledge-graph`
  - Reference: `TODO(ADAPTER-SEAM)` comment in `knowledge-graph/build.gradle.kts:29`

---

### Platform TypeScript

- [ ] **Remove local `Result<T>` re-declarations**
  - Location: `platform/typescript/*`, `products/yappc/frontend/libs/*`
  - Tasks:
    - [ ] Run consolidation audit: `grep -r "type Result<\|interface Result<" platform/typescript products/yappc/frontend --include="*.ts" --include="*.tsx"`
    - [ ] Migrate all local `Result<T>` types to `@ghatana/platform-utils`
    - [ ] Verify no duplicate `AsyncState<T>` declarations remain
  - Reference: `platform/typescript/CONSOLIDATION_GUIDE.md §5.1`

---

### Products Audio-Video

- [ ] **Replace placeholder test assertions with fixture-driven tests**
  - Location: `products/audio-video/modules/audio-processing/src/test/java/com/ghatana/audio/processing/AudioProcessingTest.java`
  - Tasks:
    - [ ] Load `test-fixtures/media-contract-fixtures.json` in audio processing tests
    - [ ] Replace `assertThat(sourceFormat).isNotNull()` stubs with real transcoding/format-conversion assertions
    - [ ] Add STT scenario validation tests using the `sttScenarios` fixture entries
    - [ ] Add retention scenario tests using the `retentionScenarios` fixture entries
  - Reference: `products/audio-video/test-fixtures/media-contract-fixtures.json`

---

### Cross-Cutting

- [ ] **Add `no-restricted-imports` ESLint rule to root config**
  - Location: `eslint.config.js`
  - Tasks:
    - [ ] Add rule flagging direct `@ghatana/sso-client` in product code
    - [ ] Add rule flagging `StateManager` import as deprecated
    - [ ] Verify rule runs in CI tier-0

---

## Notes

- All tasks must follow `coding-instructions.md` guidelines
- Tests are required for all meaningful behavior changes
- Type safety is mandatory for all TypeScript/Java code
- Observability must be part of every feature
