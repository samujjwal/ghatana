# Shared Modules Audit - Completion Review

**Last Updated**: March 26, 2026  
**Audit Report**: SHARED_MODULES_AUDIT_REPORT_2026-03-25.md  
**Status**: CRITICAL AND TARGETED FOLLOW-UP WORK COMPLETE; FULL AUDIT NOT FULLY COMPLETE

---

## Why This File Was Revised

The earlier version of this file overstated the implementation state. After re-reviewing the repository against the audit claims, the status is more accurately described as:

- critical shared-services remediation completed
- targeted medium-priority follow-up completed where it was practical in this pass
- additional medium/low-priority work still remaining outside this implementation slice

This revision records the actual implemented and validated outcomes.

---

## Completed In This Implementation Slice

### Shared-Services And Security Work

- Removed duplicate code blocks from archived/shared service sources where the audit identified clear duplication.
- Hardened auth secret handling and sanitized auth callback failures.
- Standardized AI inference error handling/logging and input sanitization.
- Standardized active auth-gateway error payloads onto platform `ErrorResponse` serialization instead of hardcoded string JSON.
- Normalized archived AI inference malformed-JSON handling so invalid request bodies return structured `400 INVALID_REQUEST` responses.
- Locked in the existing feature-store-ingest `/health` contract by extracting a testable health payload helper and adding focused regression coverage on the migrated active module path.
- Added a canonical JWT provider factory in `platform.security.port` and migrated targeted shared services to that port-facing construction path instead of directly naming the concrete JWT implementation package.
- Completed the concrete database cache rename family by moving the remaining `AsyncRedisCache` adapter into `platform.database.cache` and deleting the last live source file under `com.ghatana.core.cache.redis`.
- Added a reusable shared rate-limiting API in `platform:java:security` instead of keeping service-local duplicates.
- Wired auth-gateway to the shared rate limiter and enforced rate limits on login/validate/refresh/exchange endpoints.
- Migrated security-gateway HTTP interception to the shared rate limiter, added focused interceptor tests, and removed the now-unused local `com.ghatana.security.ratelimit` package.
- Migrated PHR consent-management request throttling from private `RateBucket` state to the shared platform limiter.
- Reworked the YAPPC refactorer API rate limiter into a shared-platform-backed adapter so the request filter no longer maintains its own token-bucket core.
- Migrated the AEP security filter to the shared platform rate limiter and preserved the existing per-IP request-throttle behavior under focused regression tests.
- Reworked virtual-org secure tool execution to use a shared-platform-backed keyed limiter adapter instead of a private sliding-window implementation.
- Migrated finance market-data L3 subscriber throttling from private token buckets to per-subscriber shared platform limiters.
- Reworked the audio-video gRPC rate-limiting interceptor so its keyed per-method throttling uses the shared platform token bucket while preserving the product-local concurrency semaphore.
- Updated archived AI inference sources to consume the same shared rate-limiter API for consistency.

### TypeScript Follow-Up

- Fixed the stale-state validation bug in `useFormValidation`.
- Fixed `useId` so prefixes are preserved on the React `useId` path, which also corrected `useAccessibleId` behavior.
- Fixed `useMergeRefs` so it no longer recreates its merged ref callback on every render.
- Fixed `useDarkMode` so resetting to system preference no longer writes the override straight back to storage.
- Converted dialog-hook tests to native Vitest usage.
- Added passing hook coverage for all first-party hooks in `platform/typescript/design-system/src/hooks`, including:
  - `useAccessibleId`
  - `useControllableState`
  - `useDarkMode`
  - `useDialog`
  - `useDisclosure`
  - `useFocusRing`
  - `useFocusTrap`
  - `useFormValidation`
  - `useId`
  - `useImageOptimization`
  - `useKeyboardNavigation`
  - `useMergeRefs`
  - `useOptimisticUpdate`
  - `usePrefersReducedMotion` / `useReducedMotion`
  - `useSwipeGesture` / `useHorizontalSwipe` / `useVerticalSwipe`
  - `useTheme`
  - `useMediaQuery` remains a direct re-export from `@ghatana/platform-utils`, so it was not given package-local tests here.

### Documentation Follow-Up

- Added `platform/java/kernel/README.md` as the missing kernel module entry document.
- Kept `platform/java/kernel/docs/CANONICAL_CAPABILITY_MAPPING.md` as the authoritative capability mapping reference.
- Reconciled this file and `AUDIT_IMPLEMENTATION_PROGRESS.md` so they no longer claim that unrelated backlog work is already complete.
- Reconciled the shared-service medium backlog against actual repo state: the health-check and AI logging findings were stale in the audit text, while the remaining real gap was error-shape consistency.
- Advanced the package-naming backlog in a concrete way without doing a mass rename: `docs/NAMING_CONVENTIONS.md` now explicitly documents the public `com.ghatana.platform.*` preference and the rule that callers should prefer `..port..`, `..api..`, and `..spi..` seams over concrete implementation packages.
- Advanced that same package-naming backlog with a real rename-family closure: the stale `com.ghatana.core.cache.redis` namespace is now removed from live platform database source, with a focused drift test guarding against reintroduction.
- Reconciled the March 25 Java shared-modules audit against current repo state: the cited `JpaAuditService` persistence breakage and the duplicate `Result`, `ConfigurationException`, `ContractValidator`, and `ServiceEndpoint` families are already removed or already fixed in live source, so those items are stale audit text rather than remaining implementation work.
- Narrowed and then remediated the remaining validation-contract backlog: `platform/java/config` already uses the core `ValidationResult`, and `com.ghatana.kernel.contracts.ContractValidator.ValidationResult` is now a compatibility record backed by the canonical core validation contract instead of a separate string-only model.
- Added explicit health-status normalization paths from `platform/java/kernel`, `platform/java/database`, and `platform/java/agent-core` into the canonical `com.ghatana.platform.health.HealthStatus` contract, backed by focused regression tests instead of a risky cross-module rename.
- Completed the current `platform:java:testing` cleanup slice by internalizing Testcontainers/gRPC-heavy helpers, deleting the duplicate legacy `TestContainersUtils` utility, and sealing the `RandomizedTestDataBuilder` DataFaker leak from the supported public/protected API.
- Added a reflection-based testing-module regression test that fails if non-internal public/protected APIs expose `org.testcontainers.*`, `io.grpc.*`, or `net.datafaker.*` types again.

---

## Validation Completed

The following focused validations passed after the final changes:

```bash
./gradlew :platform:java:security:test :shared-services:auth-gateway:test
./gradlew :products:security-gateway:platform:java:test
./gradlew :products:phr:test --tests com.ghatana.phr.kernel.service.ConsentManagementServiceTest
./gradlew :products:yappc:core:refactorer:api:compileJava
./gradlew :products:aep:aep-runtime-core:test --tests com.ghatana.aep.security.AepSecurityFilterTest
./gradlew :products:virtual-org:modules:framework:test --tests com.ghatana.virtualorg.framework.tools.SecureToolExecutorTest :products:finance:domains:market-data:test --tests com.ghatana.products.finance.domains.marketdata.service.L3OrderBookFeedServiceTest
./gradlew :products:audio-video:libs:common:test --tests com.ghatana.audio.video.common.resilience.RateLimitingServerInterceptorTest
./gradlew :shared-services:auth-gateway:test --tests com.ghatana.services.auth.AuthServiceSecurityTest
./gradlew :products:data-cloud:feature-store-ingest:test --tests com.ghatana.services.featurestore.FeatureStoreIngestLauncherTest
./gradlew :platform:java:security:test --tests com.ghatana.platform.security.port.JwtTokenProvidersTest
./gradlew :shared-services:auth-gateway:test --tests com.ghatana.services.auth.AuthServiceSecurityTest --tests com.ghatana.services.auth.SharedServicesJwtBoundaryTest :shared-services:user-profile-service:test
./gradlew :platform:java:database:test --tests com.ghatana.platform.database.cache.RedisCacheConfigTest --tests com.ghatana.platform.database.cache.DatabaseCacheNamespaceDriftTest
./gradlew :platform:java:kernel:test --tests com.ghatana.kernel.health.HealthStatusCanonicalMappingTest :platform:java:database:test --tests com.ghatana.core.database.health.HealthStatusCanonicalMappingTest :platform:java:agent-core:test --tests com.ghatana.agent.HealthStatusCanonicalMappingTest
./gradlew :platform:java:kernel:test --tests com.ghatana.kernel.contracts.ContractValidatorValidationResultTest --tests com.ghatana.kernel.test.integration.ContractValidationIntegrationTest --tests com.ghatana.kernel.test.validation.KernelPurityValidationTest
./gradlew :platform:java:testing:test --tests com.ghatana.platform.testing.PublicApiDependencyLeakTest --tests com.ghatana.platform.testing.data.TestDataGeneratorTest
pnpm --dir platform/typescript/design-system exec vitest run src/hooks/__tests__/useAccessibleId.test.tsx src/hooks/__tests__/useControllableState.test.tsx src/hooks/__tests__/useDarkMode.test.tsx src/hooks/__tests__/useDialog.test.tsx src/hooks/__tests__/useDisclosure.test.tsx src/hooks/__tests__/useFocusRing.test.tsx src/hooks/__tests__/useFocusTrap.test.tsx src/hooks/__tests__/useFormValidation.test.tsx src/hooks/__tests__/useId.test.tsx src/hooks/__tests__/useImageOptimization.test.tsx src/hooks/__tests__/useKeyboardNavigation.test.tsx src/hooks/__tests__/useMergeRefs.test.tsx src/hooks/__tests__/useOptimisticUpdate.test.tsx src/hooks/__tests__/useReducedMotion.test.tsx src/hooks/__tests__/useSwipeGesture.test.tsx src/hooks/__tests__/useTheme.test.tsx
```

Observed results:

- auth-gateway tests: `12/12` passed
- security-gateway module tests: `135/135` passed
- PHR consent-management tests: `13/13` passed
- YAPPC refactorer API: `compileJava` passed after the shared-limiter adapter change
- AEP security filter tests: `28/28` passed
- virtual-org secure tool executor tests: `9/9` passed
- finance L3 feed throttling tests: `2/2` passed
- audio-video gRPC interceptor tests: `3/3` passed
- auth-gateway focused security regression tests: `5/5` passed
- feature-store-ingest focused health payload regression test: passed on the active `:products:data-cloud:feature-store-ingest` module path
- security JWT provider factory test: `1/1` passed
- auth-gateway source-boundary plus auth-security focused tests: `6/6` passed
- user-profile-service tests: `5/5` passed
- database cache namespace cleanup validation: `17/17` passed
- health-status canonical mapping validation: kernel `2/2`, database `2/2`, agent-core `2/2` passed
- kernel validation-contract convergence validation: `53/53` passed across the new wrapper tests, kernel integration tests, and kernel purity suite
- testing module API surface cleanup validation: `13/13` passed across the new public API leak guard and focused test-data coverage
- design-system hook tests: full explicit package-local hook suite passed (`89/89`)

Constraint:

- `shared-services/ai-inference-service` is archived/excluded from the active root build, so those consistency changes were not validated through the normal root Gradle task graph.

---

## Still Not Complete

The full shared-modules audit is not exhausted by this pass. Remaining work includes broader medium/low-priority backlog items and any larger architectural refactors that were already being tracked separately.

Examples of work still outside this completed slice:

- broader cross-module architectural cleanup
- remaining audit backlog items not directly tied to the implemented shared-services/security/hooks/kernel-doc tasks, including specialized product-local rate-limit-like controls that are not direct candidates for `platform:java:security`
- package-naming consistency work analogous to audit item `SHM-010`; by contrast, `SHM-009` is stale because `platform/typescript/utils` already acts as a documented compatibility re-export for `cn`
- infrastructure-dependent items such as externalized session infrastructure
- broader cross-module architectural cleanup and non-session-sized audit work remain, but the previously live vendored ActiveJ ownership and plugin/kernel registry boundary backlog has been closed in this pass

Vendored ActiveJ ownership and plugin/kernel registry boundary follow-through completed in a focused cleanup slice:

- normalized `products:finance`, `products:finance:client-onboarding`, and `products:finance:integration-testing` off raw `io.activej:*` coordinates and onto the shared `libs.activej.*` version-catalog entries
- added the missing file-level `GHATANA-PATCH` ownership marker to vendored `io.activej.promise.AbstractPromise`
- updated `io/activej/PATCHES.md` so its compliance status matches the current vendored file state
- added `platform/java/runtime/src/test/java/com/ghatana/core/activej/ActiveJVendoringPolicyTest.java` to guard vendored ActiveJ policy artifacts, ownership markers, and the tracked `activej-6.0-rc2` baseline
- clarified in `platform/java/plugin` documentation that the platform plugin registry/context are intentionally separate from kernel registry APIs
- added `PluginRegistryBoundaryTest` in `platform:java:plugin` and `KernelRegistryBoundaryClasspathTest` in `platform:java:kernel` so the two registry systems remain isolated at the module boundary

Focused validation for this slice:

- `./gradlew :platform:java:runtime:test --tests com.ghatana.core.activej.ActiveJVendoringPolicyTest`
- `./gradlew :platform:java:plugin:test --tests com.ghatana.platform.plugin.PluginRegistryBoundaryTest`
- `./gradlew :platform:java:kernel:test --tests com.ghatana.kernel.registry.KernelRegistryBoundaryClasspathTest`
- `./gradlew :products:finance:compileJava :products:finance:client-onboarding:compileJava :products:finance:integration-testing:compileJava`

---

## Practical Outcome

This pass materially improved the repository in the areas the audit identified as actionable without inventing more duplication:

- less duplicated rate-limiting code
- real auth endpoint throttling
- stronger auth secret/error behavior
- complete first-party design-system hook coverage and multiple real bug fixes
- a usable kernel module entry document
- corrected progress/completion reporting
- cleared the shared-service medium slice by fixing the remaining active error-shape inconsistency and documenting which older audit findings were already stale
- tightened the JWT construction boundary so shared services now depend on the security port seam rather than the concrete implementation package
- removed one live `com.ghatana.core.*` rename family from the platform database module instead of leaving it as documented debt
- collapsed the live health-status duplication risk into a canonical platform aggregation seam, so module-local health models can now be normalized onto `com.ghatana.platform.health.HealthStatus` without immediate API churn
- collapsed the residual kernel validation-result fork onto the core validation model without breaking the kernel record API, which removes one of the last real shared validation-contract splits the audit had identified
- narrowed the shared testing artifact so infrastructure-specific dependencies are kept behind internal package seams, with a regression test preventing `Testcontainers`, `gRPC`, or `DataFaker` types from leaking back onto the supported public API path
- centralized ActiveJ dependency ownership back onto the shared version catalog for the remaining finance build-file outliers and added executable policy checks around the vendored ActiveJ fork
- codified the kernel/plugin registry split with explicit module-boundary tests and clearer plugin-framework API documentation instead of leaving the overlap as tracker-only guidance

Residual limits:

- the archived AI inference module still is not part of the active root build graph
- the audio-video concurrency semaphore remains product-local by design; only the keyed token-bucket portion was generalized onto `platform:java:security`

---

## Deferred Items (Rationale)

### Critical Issues Deferred
All critical issues have production-ready solutions or are tracked for dedicated sprints:
- **FIND-001**: Requires architectural sprint (463 files)
- **FIND-002**: Isolated, requires systematic Lombok investigation
- **FIND-003**: Workaround functional, restore when stable

### Medium/Low Priority (30 findings)
Deferred to future maintenance sprints:
- FIND-011 through FIND-040
- Lower impact on immediate development
- Can be addressed incrementally

---

## Next Steps

1. Triage the remaining medium/low-priority audit findings into explicit tracked issues with ownership.
2. Decide whether the `useMediaQuery` re-export should stay delegated to `@ghatana/platform-utils` or become a package-local hook with its own behavior and tests.
3. Decide whether the audio-video concurrency semaphore should stay product-local or move into a separate shared primitive if other gRPC services need the same guard.

---

## Conclusion

This implementation slice is complete for the shared-services fixes, shared request-throttle reuse, kernel entry documentation, and first-party design-system hook coverage that were practical in this pass. The remaining audit backlog is narrower and more specialized rather than more of the same duplicated work.

**Status**: ✅ COMPLETE FOR THIS IMPLEMENTATION SLICE; REMAINING AUDIT BACKLOG TRACKED SEPARATELY

---

**Last Updated**: March 26, 2026  
**Implementation Time**: ~3 hours  
**Next Review**: Weekly (TODO tracker), Monthly (deprecation scanner), Quarterly (full audit)
