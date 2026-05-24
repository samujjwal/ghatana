# AEP Build and Test Runbook

**Status:** Co-located validation runbook  
**Owner:** AEP maintainers

## Purpose

Use this runbook to verify AEP build-zero health before claiming implementation readiness. While AEP code remains co-located in Data-Cloud action-plane modules, use the co-located validation commands as the source of build evidence.

## Commands

```bash
cd products/aep
../../gradlew clean build --continue
../../gradlew test
../../gradlew check
```

If AEP modules are still wired through Data-Cloud action-plane paths during migration, run the focused Data-Cloud action-plane build and record the command and outcome in build evidence.

## Current Co-Located Commands

```bash
./gradlew :products:data-cloud:planes:action:operator-contracts:test
./gradlew :products:data-cloud:planes:action:operator-contracts:check :products:data-cloud:planes:action:agent-runtime:check
./gradlew :products:data-cloud:integration-tests:test --tests 'com.ghatana.datacloud.integration.SharedLibraryBoundaryArchTest'
```

Expected result as of 2026-05-23:

- Commands complete successfully.
- Existing action-plane checkstyle warnings may be reported; treat them as remaining hardening work until removed or explicitly baselined.

## Required Evidence

- Compile result.
- Unit and integration test result.
- Architecture-rule result.
- List of skipped tests or excluded modules.
- Confirmation that `_legacy_archive` modules are not included.
