# Build Logic Baseline - 2026-04-12

## Scope

Phase 0 execution baseline for `buildSrc` -> `build-logic` migration.

## Inventory Snapshot

- Plugin inventory scan date: `2026-04-12`
- Build scripts with migration-related convention plugins: `65`
- Files using legacy plugins (`com.ghatana.*`): `55`
- Files using build-logic plugins (`java-module|java-application|protobuf-module`): `10`

### Legacy convention usage by plugin family

- `com.ghatana.java-conventions`: platform, shared-services, AEP, software-org, yappc, audio-video, data-cloud
- `com.ghatana.protobuf-conventions`: audio-video, virtual-org, tutorputor, yappc refactorer api
- `com.ghatana.finance-domain-conventions`: finance domain modules
- `com.ghatana.testing-conventions` / `quality-conventions`: templates, selected integration/test modules

### Clean `buildSrc` reference scan (`rg` with node/build excludes)

Primary active references found in:

- `products/yappc/settings.gradle.kts` (standalone includeBuild still points to `buildSrc`)
- `.github/workflows/verify-buildsrc-sync.yml`
- `gradle/buildSrc-include.gradle.kts`
- Product Dockerfiles copying `buildSrc` (`products/aep`, `products/data-cloud`, `products/audio-video/*`, `products/tutorputor/libs/content-studio-agents`)
- Historic/docs references under `products/*/docs` and `docs/`

## Validation Runs (executed)

### Configuration cache baseline

1. `./gradlew --no-daemon help --configuration-cache --build-cache --profile`
   - Result: `BUILD SUCCESSFUL`
   - Note: config cache stored with known Kotlin plugin listener warning.
2. Same command (second run)
   - Result: `BUILD SUCCESSFUL`
   - Note: `Reusing configuration cache` confirmed.

### Root sanity check

- `./gradlew --no-daemon help buildHealth --warning-mode=all`
- Result: `FAILED` (pre-existing root task issue)
- Failure details:
  - `:buildHealth` is not configuration-cache safe (`Task.project` access at execution time)
  - NPE in task action (`Cannot invoke "org.gradle.api.Project.getGradle()" ...`)

### Wave A pilot module checks

- Command:
  - `./gradlew --no-daemon :platform:java:agent-core:check :platform:java:ai-integration:check :shared-services:incident-service:check`
- Result: `BUILD SUCCESSFUL`
- Notes:
  - Checkstyle/PMD tasks are active and passing for these modules under current baseline.
  - Large warning volume exists in `agent-core` test compilation (deprecations), but build remained green.

### Root full build/check

- Command: `./gradlew --no-daemon build check`
- Result: `FAILED` (pre-existing cross-repo issue not introduced by Wave A plugin swap)
- Failure details:
  - Configuration-cache serialization failure in `:products:flashit:spotlessMisc`
  - Symbolic link error under `products/flashit/backend/gateway/node_modules/.bin/node-gyp-build`

## Baseline Conclusion

- Wave A pilot plugin migration is valid at module scope.
- Monorepo root is not yet fully green due unrelated pre-existing issues (`buildHealth` task and Flashit spotless/config-cache path).
- Next migration batches should continue with module-level gates plus separate remediation tracks for root-level blockers.

## Latest Update (same day follow-up)

- Broad plugin-ID migration completed across remaining legacy module build scripts.
- `buildHealth` task was stabilized and now executes successfully with `./gradlew --no-daemon help buildHealth --warning-mode=all`.
- Representative multi-product module checks passed after migration:
  - `:products:aep:aep-runtime-core:check`
  - `:products:finance:domains:pricing:check`
  - `:products:yappc:core:yappc-api:check`
  - `:products:tutorputor:libs:content-studio-agents:check`
  - `:products:virtual-org:engine:service:check`
- Remaining blocker for final cutover: root fallback removal is unsafe until fallback-dependent modules are migrated to explicit build-logic plugin IDs.

## Root Build Check (earlier snapshot)

- Command: `./gradlew --no-daemon build check`
- Result: `FAILED`
- Historical top-level failure at that point in the day:
  - `:platform-kernel:kernel-core:compileTestJava` missing Spring test classes in `AppPlatformKernelContractTest`
- Additional environment/build-system constraints still visible:
  - file-watch path duplication under YAPPC local python virtualenv
  - configuration-cache incompatibilities in Spotless/YAPPC custom tasks

This snapshot was superseded later in the day after the kernel-core contract test rewrite and subsequent follow-up reruns.

## Latest Update (YAPPC standalone + fallback reconciliation)

- Root fallback detector now recognizes `finance-domain-module` as a migrated build-logic convention, eliminating unintended legacy fallback re-application for finance domain modules.
- Corrected fallback-dependent source-bearing module count: `84` (excluding `buildSrc` and the `build-logic` included build).
- Standalone YAPPC smoke validation remains green:
  - `cd products/yappc && ../../gradlew --no-daemon help`
- Standalone YAPPC migrated-batch validation is now green when using alias task paths that match the standalone dependency graph:
  - `cd products/yappc && ../../gradlew --no-daemon :products:yappc:core:agents:runtime:check :products:yappc:core:knowledge-graph:check :products:yappc:core:services-lifecycle:check :products:yappc:infrastructure:datacloud:check`
- Shared build-logic quality conventions were fixed to resolve `config/checkstyle/*` and `config/pmd/*` from the monorepo config root during standalone product builds.
- Additional platform fallback migrations completed and validated in follow-up root-blocker remediation:
  - `platform/java/audit/build.gradle.kts`
  - `platform/java/database/build.gradle.kts`
  - `platform/java/config/build.gradle.kts`
- Important standalone caveat documented from validation:
  - direct `:core:*` task invocation in standalone YAPPC can trigger Gradle implicit-dependency validation failures because standalone settings expose both `:core:*` and `:products:yappc:*` aliases to the same directories.
  - use `:products:yappc:*` task paths for standalone validation commands.

## Broad Root Rerun (latest after follow-up fixes)

- Command: `./gradlew --no-daemon help build check --warning-mode=all`
- Result: `FAILED`
- Deterministic blocker progression in this follow-up pass:
  1. `:platform:java:audit:spotlessMiscCheck` (fixed; module check now passes)
  2. `:platform:java:database:spotlessMiscCheck` (fixed; module check now passes)
  3. `:platform:java:config:spotlessMiscCheck` (fixed; module check now passes)
- Current remaining broad-build constraints still visible during reruns:
  - YAPPC Python virtualenv file-watch duplication under `products/yappc/core/refactorer/engine/.tools/python/venv-libcst/...`
  - configuration-cache incompatibilities in Kotlin plugin listener registration, Flashit Spotless tasks, and YAPPC custom governance tasks
- A subsequent broad-root rerun progressed well beyond the fixed `platform:java:config` formatting issue, but the captured log did not reach a terminal Gradle result marker.
- Another broad-root rerun is still required to record the next confirmed blocker after these formatting fixes.

