# Build Logic Phase 1 Parity Gaps - 2026-04-12

## Objective

Track remaining behavior gaps between legacy `buildSrc` conventions and current `build-logic` conventions.

## Parity Matrix

| Legacy plugin | Status in `build-logic` | Gap summary | Action |
|---|---|---|---|
| `com.ghatana.java-conventions` | Covered by `java-module` / `java-application` | Mostly equivalent. Minor behavior drift in test JVM args (`ZGC` flags added in `java-module`). | Keep as-is; document drift as intentional perf default. |
| `com.ghatana.lombok-conventions` | Covered by `java-module` / `java-application` / `protobuf-module` | Equivalent compile/test Lombok wiring present. | No action. |
| `com.ghatana.quality-conventions` | Covered by `java-module` / `java-application` / `protobuf-module` | Equivalent Checkstyle/PMD/JaCoCo/Spotless baseline present. | No action; continue module rollout. |
| `com.ghatana.testing-conventions` | Partially covered by `java-module` | Core test + jacoco behavior covered. Integration profile behavior (`runIntegrationTests` property + system properties) is not centrally modeled in `build-logic`. | Add integration profile support in `java-module` or separate `integration-test-profile` plugin in `build-logic`. |
| `com.ghatana.protobuf-conventions` | Covered by `protobuf-module` | Protobuf + gRPC generation covered; legacy descriptor placeholder generation step is absent. | Confirm descriptor placeholder is obsolete; if required, add explicit descriptor task in `protobuf-module`. |
| `com.ghatana.finance-domain-conventions` | Covered by `finance-domain-module` | Parity plugin created in `build-logic` with platform-sdk API dependency. | Migrate remaining finance modules to `finance-domain-module` (done in this wave). |
| `com.ghatana.integration-test-profile` | Covered by `integration-test-profile` | Dedicated integration profile plugin now available in `build-logic`. | Apply in modules that require integration-tag exclusion semantics. |

## Additional Drift Findings

1. `build-logic` still hardcodes tool/dependency versions in plugin code (`java-module`, `java-application`, `protobuf-module`).
2. Root migration fallback (`build.gradle.kts` `subprojects { ... }`) remains active and should be removed only after legacy plugin count reaches zero.
3. Standalone `products/yappc/settings.gradle.kts` now includes `build-logic` in plugin management.

## Phase 1 Completion Criteria

- [x] `finance-domain` parity plugin exists in `build-logic`
- [x] integration-test-profile parity exists in `build-logic`
- [ ] protobuf descriptor requirement is explicitly resolved (removed by decision or implemented)
- [ ] no required legacy behavior remains undocumented

