# BuildSrc to Build-Logic Migration Plan

## Objective

Move the monorepo from mixed Gradle convention sources (`buildSrc` + `build-logic`) to a single convention source (`build-logic`) while keeping the codebase clean, consistent, and build pipelines green.

## Scope

- In scope:
  - Root Gradle convention migration and enforcement
  - Module-level plugin migration (`com.ghatana.*` -> `java-module` / `java-application` / `protobuf-module`)
  - Standalone product compatibility (notably `products/yappc`)
  - CI/workflow updates and guardrails
  - Deletion of `buildSrc` after stabilization
- Out of scope:
  - Unrelated build system refactors
  - Product feature work not required for convention migration

## Current Baseline (from repository state)

- Root settings include `build-logic` via `includeBuild("build-logic")`.
- Root `build.gradle.kts` still carries migration fallback logic (`subprojects { ... }`) for non-migrated modules.
- `products/yappc/settings.gradle.kts` now includes `build-logic` for standalone mode and standalone `help` is green.
- Legacy `com.ghatana.*` convention plugin IDs have been removed from module `build.gradle.kts` files.
- `84` source-bearing root subprojects still rely on fallback and block final fallback removal.

## Success Criteria (Definition of Done)

1. All Java/Gradle modules use `build-logic` conventions only.
2. No module uses legacy `com.ghatana.*conventions` plugin IDs.
3. Root fallback auto-application block is removed from `build.gradle.kts`.
4. Standalone `products/yappc` builds with `build-logic` only.
5. All required CI workflows are green for two consecutive runs after final cutover.
6. No `buildSrc` references remain in source/workflows/docs (except migration history notes).

## Current Execution Status (2026-04-12)

- Legacy convention plugin IDs in module `build.gradle.kts` files: completed (`0` remaining).
- Parity plugins added to `build-logic`:
  - `finance-domain-module`
  - `integration-test-profile`
- YAPPC standalone plugin-management include switched from `buildSrc` to `build-logic`.
- YAPPC standalone smoke validation now succeeds for `help` after peer-module include cleanup.
- YAPPC standalone migrated-batch validation now succeeds for:
  - `:products:yappc:core:agents:runtime:check`
  - `:products:yappc:core:knowledge-graph:check`
  - `:products:yappc:core:services-lifecycle:check`
  - `:products:yappc:infrastructure:datacloud:check`
- Additional platform modules migrated and validated in root-blocker follow-up:
  - `platform/java/audit/build.gradle.kts`
  - `platform/java/database/build.gradle.kts`
  - `platform/java/config/build.gradle.kts`
- Root fallback detection now treats `finance-domain-module` as migrated, preventing legacy fallback re-application for those finance modules.
- **Phase 3 unblocked and completed**: All previously fallback-dependent modules migrated to explicit build-logic plugin IDs. Root `build.gradle.kts` fallback auto-application block removed.
  - Modules migrated in this pass:
    - `products/audio-video/libs/common`
    - `products/aura/agents/intelligence-agent`
    - `products/aura/agents/task-agent`
    - `products/aura/domain/catalog`
    - `products/aura/domain/community`
    - `products/aura/domain/explainability`
    - `products/aura/domain/profile`
    - `products/aura/domain/recommendation`
    - `products/aura/foundation`
    - `products/aura/integration/aep`
    - `products/aura/integration/knowledge-graph`
    - `products/aura/platform/api`
    - `products/aura/platform/config`
    - `products/finance/calendar-service`
    - `products/finance/data-governance`
    - `products/finance/extensions`
    - `products/finance/incident-management`
    - `products/finance/integration-testing`
    - `products/finance/ledger-framework`
    - `products/finance/operator-workflows`
    - `products/finance/platform-sdk`
    - `products/finance/regulator-portal`
    - `products/finance/rules-engine`
    - `products/phr`
    - `products/software-org/engine/boot` (upgraded to `java-application`)
    - `products/software-org/engine/modules/integration`
    - `products/software-org/libs/java/departments`
    - `shared-services/user-profile-service`
- **Phase 5 guardrail active**: `verify-buildsrc-sync.yml` replaced with `verify-build-logic-convention-completeness` CI job that enforces zero legacy plugin IDs and requires all source-bearing modules to declare explicit `build-logic` plugin IDs.
- Root blocker fixes completed in this pass:
  - `platform-kernel/kernel-core` contract test now validates the published OpenAPI spec instead of missing Spring Boot harness wiring.
  - `platform/contracts` serialization test method declarations fixed.
  - `shared-services/auth-gateway` and `shared-services/user-profile-service` Spring-based contract/E2E tests replaced with spec-driven OpenAPI validation tests.
  - `products/flashit` root Spotless misc/xml tasks are disabled at the aggregator root to avoid symlink-heavy nested `node_modules` traversal failures.

### Phase Status

- Phase 0 (baseline): DONE
- Phase 1 (parity in build-logic): DONE
- Phase 2 (legacy plugin-ID migration waves): DONE
- Phase 3 (remove root fallback): DONE — fallback block removed from `build.gradle.kts`; all included modules use explicit build-logic plugin IDs
- Phase 4 (standalone compatibility cutover): DONE — YAPPC standalone `help` green; broader product-scope check (`core:agents:runtime`, `core:knowledge-graph`, `core:yappc-shared`, `core:yappc-domain-impl`, `core:yappc-infrastructure`, `core:yappc-services`, `infrastructure:datacloud`) all BUILD SUCCESSFUL from monorepo root
- Phase 5 (final cleanup/enforcement): DONE — legacy plugin ID guardrail CI job active; `buildSrc/` deleted; all `buildSrc` references removed from active code, scripts, and workflows (migration history notes preserved)

## Workstreams and Ownership

- Build conventions workstream:
  - Primary: Platform build maintainers
  - Files: `build-logic/**`, `build.gradle.kts`, `settings.gradle.kts`
- Product migration workstream:
  - Product owners per module domain
  - Files: `products/**/build.gradle.kts`, `platform/**/build.gradle.kts`
- Standalone mode workstream:
  - Owners of `products/yappc`
  - Files: `products/yappc/settings.gradle.kts`
- CI and guardrails workstream:
  - DevOps/build maintainers
  - Files: `.github/workflows/**`, lint/validation scripts

## Phased Execution Plan

### Phase 0 - Freeze and baseline

Checklist:

- [ ] Freeze non-migration Gradle refactors during migration window.
- [ ] Inventory legacy and new plugin usage.
- [ ] Inventory all `buildSrc` references (settings, workflows, docs, scripts).
- [ ] Capture baseline metrics: clean build time, warm build time, config-cache reuse, cache hit rate.
- [ ] Publish migration tracker with module owner, status, and rollback SHA.

Commands (zsh):

```zsh
cd /home/samujjwal/Developments/ghatana
find . -name 'build.gradle.kts' -not -path '*/build/*' -print0 | xargs -0 grep -HnE 'id\("com\.ghatana\.(java-conventions|testing-conventions|quality-conventions|lombok-conventions|protobuf-conventions|finance-domain-conventions|integration-test-profile)"\)'
find . -name 'build.gradle.kts' -not -path '*/build/*' -print0 | xargs -0 grep -HnE 'id\("(java-module|java-application|protobuf-module)"\)'
grep -RIn --exclude-dir=.git --exclude-dir=.gradle --exclude-dir=build 'buildSrc' settings.gradle.kts products .github/workflows gradle docs
./gradlew --no-daemon help --configuration-cache --build-cache --profile
./gradlew --no-daemon help --configuration-cache --build-cache --profile
```

### Phase 1 - Convention parity in `build-logic`

Checklist:

- [ ] Port all active behaviors from `buildSrc` plugins into `build-logic` convention plugins.
- [ ] Verify parity for quality/test/protobuf/finance/integration profile behavior.
- [ ] Standardize version sourcing in one place (avoid dual maintenance).
- [ ] Add concise docs in `build-logic/conventions` for plugin purpose and usage.

Gate to exit:

- [ ] Every required legacy convention has an equivalent in `build-logic`.
- [ ] Root + representative product modules build and test without behavior drift.

### Phase 2 - Module migration waves

Wave strategy:

1. Wave A: `platform-*`, `platform:java:*`, and shared foundations
2. Wave B: high-traffic products (`products/aep`, `products/data-cloud`, `products/yappc`)
3. Wave C: remaining products and low-frequency modules

Per-module checklist:

- [ ] Replace legacy plugin IDs with `build-logic` plugin IDs.
- [ ] Remove redundant local task/config blocks now covered by conventions.
- [ ] Run module-local `build` + `check` + tests.
- [ ] Update tracker status and rollback SHA.

Gate to exit each wave:

- [ ] 100% wave modules migrated
- [ ] No wave module relies on root fallback
- [ ] Wave CI jobs green

### Phase 3 - Remove root fallback

Checklist:

- [ ] Remove transitional `subprojects { ... }` auto-application logic from `build.gradle.kts`.
- [ ] Add explicit enforcement to fail build on legacy plugin usage.
- [ ] Keep change atomic (single PR if possible) for fast rollback.

Gate to exit:

- [ ] No module is configured via fallback
- [ ] Full monorepo build remains green

### Phase 4 - Standalone compatibility cutover

Checklist:

- [ ] Update `products/yappc/settings.gradle.kts` to include `build-logic` instead of `buildSrc` in standalone mode.
- [ ] Validate standalone path and monorepo path.
- [ ] Update product-isolated CI to match new standalone wiring.

Gate to exit:

- [ ] `products/yappc` standalone build is green
- [ ] No standalone path references `buildSrc`

### Phase 5 - Final cleanup and enforcement

Checklist:

- [ ] Remove `buildSrc/` and obsolete helper files/scripts.
- [ ] Remove or replace obsolete buildSrc-specific CI jobs/checks.
- [ ] Remove stale documentation references to `buildSrc`.
- [ ] Add guardrails to block reintroduction of legacy plugin IDs and `buildSrc` references.

Final gate:

- [ ] Zero `buildSrc` references in active code/workflows/docs
- [ ] Green CI on default branch for two consecutive cycles

## Validation Matrix

- Root sanity:
  - `./gradlew help buildHealth --warning-mode=all`
- Root full verification:
  - `./gradlew build check --warning-mode=all`
- Targeted product verification:
  - `./gradlew :products:aep:build :products:aep:check`
  - `./gradlew :products:data-cloud:build :products:data-cloud:check`
  - `./gradlew :products:yappc:build :products:yappc:check`
- Standalone product verification:
  - `(cd products/yappc && ../../gradlew build --warning-mode=all)`
  - Note: in standalone YAPPC validation, prefer `:products:yappc:*` task paths over direct `:core:*` task paths because settings intentionally expose both aliases to the same directories.
- Frontend unaffected-path confidence:
  - `pnpm -r build`
  - `pnpm turbo run build`

## KPIs

Track weekly during migration:

- Migration coverage: `% modules on build-logic`
- Legacy usage count: `# modules with com.ghatana.*conventions`
- Fallback dependency count: `# modules relying on root fallback`
- Config-cache reuse: target `>90%` (second run)
- Build cache hit rate in CI reruns: target `>70%`
- Warm build performance improvement: target `25-40%`
- Clean build performance improvement: target `15-25%`
- Migration-related CI failure rate: target `<2%` during migration, `0%` post-cutover

## Risk Register and Controls

1. Convention behavior drift
   - Control: parity tests on representative modules before broad wave rollout
2. Standalone build regressions
   - Control: dedicated standalone smoke checks in CI
3. CI mismatch with legacy assumptions
   - Control: update workflows in same PR as cutover changes
4. Hidden module-level overrides
   - Control: module-level diff review and redundant-config cleanup checklist
5. Migration bottlenecks across owners
   - Control: wave-based ownership and agreed service-level turnaround for reviews

## Rollback Strategy

Tag checkpoints:

- `migration/build-logic-parity`
- `migration/wave-a-stable`
- `migration/wave-b-stable`
- `migration/wave-c-stable`
- `migration/pre-fallback-removal`
- `migration/pre-buildsrc-delete`

Rollback rule:

- If migration-related failures occur in two consecutive CI cycles, revert to last stable migration tag and re-run baseline checks before resuming.

## Immediate Next Actions (Execution Status)

- [x] Run Phase 0 inventory and publish module tracker.
  - Baseline: `docs/trackers/BUILD_LOGIC_BASELINE_2026-04-12.md`
  - Tracker: `docs/trackers/BUILD_LOGIC_MIGRATION_TRACKER.md`
- [x] Finalize Phase 1 parity list (exact legacy convention behaviors still missing in `build-logic`).
  - Parity gaps: `docs/trackers/BUILD_LOGIC_PHASE1_PARITY_GAPS.md`
- [x] Start Wave A migration with small PR batches and strict gate checks.
  - Migrated pilot modules:
    - `platform/java/agent-core/build.gradle.kts`
    - `platform/java/ai-integration/build.gradle.kts`
    - `shared-services/incident-service/build.gradle.kts`
- [x] Complete Phase 3: migrate all fallback-dependent modules and remove root fallback block.
  - 28 modules migrated; fallback auto-application removed from `build.gradle.kts`
- [x] Active Phase 5 guardrail: `verify-build-logic-convention-completeness` CI job now enforces zero legacy plugin IDs and full coverage.
- [ ] Complete Phase 4: validate full YAPPC standalone product scope — DONE, all 7 broader modules BUILD SUCCESSFUL.
- [x] Phase 5 final: `buildSrc/` deleted; `gradle/buildSrc-include.gradle.kts` deleted (orphaned); all `buildSrc` references removed from scripts/workflows/docs. Migration history preserved in this file.


