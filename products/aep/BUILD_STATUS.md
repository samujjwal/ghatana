# AEP Build Status

**Status:** Partial build-zero verified for co-located AEP contracts/runtime  
**Owner:** AEP maintainers  
**Last reviewed:** 2026-05-23

## Current Status

The repository contains AEP runtime foundations and tests. Current AEP implementation remains co-located under Data-Cloud action-plane modules during migration, so build-zero evidence is recorded against those modules until standalone AEP Gradle wiring exists.

Verified on 2026-05-23:

- `./gradlew :products:data-cloud:planes:action:operator-contracts:test`
- `./gradlew :products:data-cloud:planes:action:operator-contracts:check :products:data-cloud:planes:action:agent-runtime:check`
- `./gradlew :products:data-cloud:integration-tests:test --tests 'com.ghatana.datacloud.integration.SharedLibraryBoundaryArchTest'`

Notes:

- `platform-kernel:kernel-core` compile blockers were resolved for the normal dependency path.
- The touched module checks pass, but they still report pre-existing checkstyle warnings in older action-plane files.
- Standalone `products/aep` build remains a target because `products/aep` is currently documentation/contracts/examples, not an independently wired Gradle product.

## Build-Zero Milestone

Tasks:

- Create or restore standalone AEP Gradle wiring if AEP is meant to build outside the co-located Data-Cloud action-plane path.
- Keep co-located AEP contract/runtime modules green through `check`.
- Remove or baseline pre-existing action-plane checkstyle warnings.
- Confirm no `_legacy_archive` modules are accidentally included.

Validation commands:

```bash
cd products/aep
../../gradlew clean build --continue
../../gradlew test
../../gradlew check
```

Current co-located validation:

```bash
./gradlew :products:data-cloud:planes:action:operator-contracts:test
./gradlew :products:data-cloud:planes:action:operator-contracts:check :products:data-cloud:planes:action:agent-runtime:check
./gradlew :products:data-cloud:integration-tests:test --tests 'com.ghatana.datacloud.integration.SharedLibraryBoundaryArchTest'
```

## Acceptance Criteria

- AEP builds from root.
- AEP builds standalone if standalone mode remains intended.
- CI fails on compile, test, and architecture-rule failures.
- Build documentation reflects verified status, not aspiration.
