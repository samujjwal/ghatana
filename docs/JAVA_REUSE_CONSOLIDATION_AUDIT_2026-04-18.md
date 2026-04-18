# Java Reuse Consolidation Audit

Date: 2026-04-18
Scope: Java-only repository audit
Objective: Identify implementation-ready consolidation opportunities for reusable Java code across platform, kernel, shared services, and products.

## A. Executive Summary

This audit reviewed the handwritten Java code surface across platform modules, platform-kernel, platform-plugins, shared-services, products, and integration test areas. The repository currently contains 9,464 Java files in total, but the highest-value reuse opportunities are concentrated in a much smaller set of cross-cutting clusters.

### Major Duplication Clusters

1. State management abstractions
   - `HybridStateStore` and `SyncStrategy` are duplicated across platform core, AEP, and Data Cloud.

2. Plugin infrastructure
   - `PluginRegistry`, `PluginLoader`, and `PluginContext` exist in kernel, platform plugin framework, YAPPC shared, and YAPPC scaffold.

3. Tenant/security context models
   - Multiple `TenantContext` and `SecurityContext` types represent overlapping request-scope concerns.

4. Validation and HTTP envelopes
   - Parallel `ValidationResult`, `ErrorResponse`, `ApiResponse`, and helper utilities exist at both platform and product levels.

5. Test infrastructure
   - `EventloopTestBase` and `EventloopTestUtil` are exact duplicates.

6. Secret/config/repository helpers
   - The same SPI and transaction-wrapping patterns recur in multiple places.

### Best Consolidation Opportunities

1. Exact duplicate ActiveJ test support in platform-testing and kernel-core tests.
2. AEP state-store classes that overlap directly with platform core state abstractions.
3. YAPPC local `JsonUtils` versus platform `JsonUtils`.
4. PHR duplicate response envelopes.
5. YAPPC scaffold plugin types versus YAPPC shared plugin types.

### Safest Quick Wins

1. `EventloopTestBase` and `EventloopTestUtil`.
2. YAPPC `JsonUtils`.
3. PHR response envelope merge.
4. AEP `HttpHelper` migration onto platform HTTP response builders.
5. YAPPC scaffold plugin registry/loader/context collapse into YAPPC shared.

### Highest-Risk Refactors

1. `TenantContext` canonicalization because the repo mixes immutable value objects with thread-local holders.
2. `SecurityContext` consolidation because kernel, platform security, and security-gateway carry different semantics.
3. `ValidationResult` consolidation because there are two platform-level variants already in live use.
4. `SecretProvider` and Data Cloud adapter DTO convergence because module ownership and dependency direction matter.

### Important Non-Findings

1. `HealthStatus` has already been largely consolidated to platform core according to repository memory; remaining local enums/records are not all candidates for immediate extraction.
2. Kernel `PluginRegistry` and standalone platform `PluginRegistry` are intentionally distinct; cross-merging them would violate boundary intent already documented in repository memory.

## B. Reuse Inventory

### 1. ActiveJ Test Support Duplicated Verbatim

- Title: Duplicate ActiveJ test base and utilities
- Duplicate locations:
  - `platform/java/testing/src/main/java/com/ghatana/platform/testing/activej/EventloopTestBase.java`
  - `platform-kernel/kernel-core/src/test/java/com/ghatana/platform/testing/activej/EventloopTestBase.java`
  - `platform/java/testing/src/main/java/com/ghatana/platform/testing/activej/EventloopTestUtil.java`
  - `platform-kernel/kernel-core/src/test/java/com/ghatana/platform/testing/activej/EventloopTestUtil.java`
- Current intent: reusable JUnit + ActiveJ eventloop test harness.
- Duplication type: exact
- Recommended target shared module: `platform:java:testing`
- Recommended canonical abstraction: concrete base class plus utility, owned only by platform testing.
- Migration plan:
  - Replace kernel-core test imports with platform-testing imports.
  - Delete duplicated kernel test copies.
  - Run kernel and platform-testing test suites.
  - Add a boundary/source-scan guard preventing a second copy under test trees.
- Impacted callers: platform-kernel tests and any product tests still referencing the copied kernel versions.
- Expected payoff: immediate single source of truth with effectively zero semantic risk.
- Complexity: low
- Confidence: high

### 2. HybridStateStore and SyncStrategy Split Across Platform Core, AEP, and Data Cloud

- Title: Parallel hybrid state-store abstractions
- Duplicate locations:
  - `platform/java/core/src/main/java/com/ghatana/core/state/HybridStateStore.java`
  - `platform/java/core/src/main/java/com/ghatana/core/state/SyncStrategy.java`
  - `products/aep/aep-engine/src/main/java/com/ghatana/core/state/HybridStateStore.java`
  - `products/aep/aep-engine/src/main/java/com/ghatana/core/state/SyncStrategy.java`
  - `products/aep/aep-engine/src/main/java/com/ghatana/statestore/hybrid/HybridStateStore.java`
  - `products/aep/aep-engine/src/main/java/com/ghatana/statestore/hybrid/SyncStrategy.java`
  - `products/data-cloud/platform-entity/src/main/java/com/ghatana/datacloud/entity/state/HybridStateStore.java`
- Current intent: local-plus-central state coordination with configurable sync policy.
- Duplication type:
  - exact for AEP `com.ghatana.core.state` versus platform core
  - near for AEP `statestore.hybrid`
  - conceptual for Data Cloud partitioned/checkpointed port
- Recommended target shared module: `platform:java:core` for generic behavior and `SyncStrategy`; Data Cloud keeps a product-local partitioned port.
- Recommended canonical abstraction:
  - concrete generic `HybridStateStore<K, V>` and `SyncStrategy` in platform core
  - product-specific ports may extend semantics only when adding partition/checkpoint/recovery behavior
- Migration plan:
  - Replace AEP `com.ghatana.core.state` imports with platform core.
  - Refactor AEP `statestore.hybrid` to extend or wrap the platform abstraction.
  - Extract Data Cloud nested `SyncStrategy` to reuse platform `SyncStrategy` while preserving partition and recovery semantics locally.
- Impacted callers: AEP engine stateful operators, AEP runtime services, Data Cloud entity-state adapters.
- Expected payoff: removes parallel state semantics and reduces drift in sync-policy behavior.
- Complexity: medium for AEP import migration, high for Data Cloud alignment
- Confidence: high

### 3. Immutable TenantContext Value Objects Duplicated While Holder-Style TenantContext Also Exists

- Title: Tenant context split between value objects and context holders
- Duplicate locations:
  - `platform/java/domain/src/main/java/com/ghatana/platform/domain/eventstore/TenantContext.java`
  - `products/data-cloud/spi/src/main/java/com/ghatana/datacloud/spi/TenantContext.java`
  - `platform/java/governance/src/main/java/com/ghatana/platform/governance/security/TenantContext.java`
  - `products/yappc/core/refactorer/api/src/main/java/com/ghatana/refactorer/server/auth/TenantContext.java`
- Current intent: tenant-scoped request or storage context.
- Duplication type:
  - exact between platform-domain eventstore and Data Cloud SPI
  - conceptual across governance holder and YAPPC auth context
- Recommended target shared module: `platform:contracts` or `platform:java:governance` for one immutable tenant-scope value object; governance holder remains separate.
- Recommended canonical abstraction: immutable tenant scope record with `tenantId`, optional `workspaceId`, and metadata.
- Migration plan:
  - Extract a canonical immutable type.
  - Move both exact-record callers to it.
  - Rename or explicitly document governance `TenantContext` as a holder-style context only.
  - Keep the YAPPC auth record local because it adds subject, roles, and claims.
- Impacted callers: event-store contracts, Data Cloud SPI, repository/service entry points that pass tenant scope explicitly.
- Expected payoff: removes exact duplicate record logic and reduces confusion between value objects and thread-local state.
- Complexity: medium
- Confidence: high

### 4. SecurityContext Is Duplicated Across Platform Security and Security-Gateway

- Title: Request security contract drift
- Duplicate locations:
  - `platform/java/security/src/main/java/com/ghatana/platform/security/SecurityContext.java`
  - `products/security-gateway/platform/java/src/main/java/com/ghatana/auth/security/SecurityContext.java`
  - `products/security-gateway/platform/java/src/main/java/com/ghatana/auth/security/SecurityContextHolder.java`
  - `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/security/SecurityContext.java`
  - `products/aep/aep-registry/src/main/java/com/ghatana/aep/domain/agent/registry/SecurityContext.java`
- Current intent: authenticated principal, tenant, roles, permissions, and request security state.
- Duplication type:
  - near between platform security and security-gateway
  - conceptual for kernel and AEP registry
- Recommended target shared module: `platform:java:security`
- Recommended canonical abstraction: one request-scoped `SecurityContext` contract in platform security, with a separate holder utility only if runtime-wide reuse is justified.
- Migration plan:
  - Enrich platform security `SecurityContext` to cover the stable superset needed by security-gateway.
  - Migrate security-gateway code to it.
  - Move holder logic into platform security only if multiple modules need it.
  - Leave kernel `SecurityContext` separate until a compatibility mapping is defined.
- Impacted callers: security-gateway filters/services, platform security consumers, potential auth-gateway convergence.
- Expected payoff: one platform auth contract instead of platform-plus-product divergence.
- Complexity: medium-high
- Confidence: medium

### 5. Platform-Level ValidationResult Is Still Split in Two

- Title: Competing platform validation result types
- Duplicate locations:
  - `platform/java/core/src/main/java/com/ghatana/platform/validation/ValidationResult.java`
  - `platform/java/core/src/main/java/com/ghatana/platform/core/validation/ValidationResult.java`
  - `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/contracts/ContractValidator.java`
- Current intent: typed validation outcome with errors or violations.
- Duplication type: near
- Recommended target shared module: `platform:java:core`
- Recommended canonical abstraction: one `ValidationResult` type only. The violation-oriented `platform.core.validation.ValidationResult` is the better long-term canonical abstraction because it already contains a bridge from the legacy error type.
- Migration plan:
  - Choose the canonical type explicitly.
  - Keep conversion helpers only during migration.
  - Update kernel compatibility wrapper.
  - Migrate platform modules first, then product modules importing the old package.
- Impacted callers: kernel contract validators, core validation framework, product validators depending on `com.ghatana.platform.validation`.
- Expected payoff: removes the current state where platform has two “canonical” validation contracts.
- Complexity: medium
- Confidence: high

### 6. HTTP Response and Error Handling Is Split Between Platform HTTP and Product Helpers

- Title: Response-builder and envelope drift
- Duplicate locations:
  - `platform/java/http/src/main/java/com/ghatana/platform/http/server/response/ErrorResponse.java`
  - `platform/java/http/src/main/java/com/ghatana/platform/http/server/response/ResponseBuilder.java`
  - `platform/java/core/src/main/java/com/ghatana/platform/core/exception/ErrorResponseBuilder.java`
  - `products/aep/server/src/main/java/com/ghatana/aep/server/http/HttpHelper.java`
  - `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/ApiResponse.java`
  - `products/phr/src/main/java/com/ghatana/phr/api/FhirApiResponse.java`
  - `products/phr/src/main/java/com/ghatana/phr/api/NepalHieApiResponse.java`
- Current intent: standard API success/error envelopes and response construction.
- Duplication type:
  - near for error handling
  - exact for the two PHR response records
  - conceptual for Data Cloud success envelope
- Recommended target shared module: `platform:java:http` for generic HTTP success/error primitives; product-local shared modules for domain-specific envelopes.
- Recommended canonical abstraction:
  - platform HTTP owns low-level error body and response builders
  - products own domain-specific success envelopes only when they encode stable contract semantics
- Migration plan:
  - Migrate AEP `HttpHelper` to delegate to platform `ResponseBuilder` and platform `ErrorResponse`.
  - Merge the two PHR response records into one product-local transport response class.
  - Keep Data Cloud `ApiResponse` local because it contains product-specific metadata and AI fields, but align its error block with platform error handling.
- Impacted callers: AEP controllers, PHR controllers, Data Cloud launcher handlers.
- Expected payoff: one error-handling path and fewer manual JSON helpers.
- Complexity: low for PHR and AEP, medium for Data Cloud alignment
- Confidence: high

### 7. YAPPC Plugin Infrastructure Is Still Duplicated Between yappc-shared and Scaffold

- Title: Intra-product plugin API duplication in YAPPC
- Duplicate locations:
  - `products/yappc/core/yappc-shared/src/main/java/com/ghatana/yappc/plugin/PluginRegistry.java`
  - `products/yappc/core/scaffold/generators/src/main/java/com/ghatana/yappc/core/plugin/PluginRegistry.java`
  - `products/yappc/core/scaffold/generators/src/main/java/com/ghatana/yappc/core/plugin/PluginLoader.java`
  - `products/yappc/core/yappc-shared/src/main/java/com/ghatana/yappc/plugin/PluginContext.java`
  - `products/yappc/core/scaffold/generators/src/main/java/com/ghatana/yappc/core/plugin/PluginContext.java`
- Current intent: plugin registration, lookup, and loader/context support for YAPPC.
- Duplication type: near
- Recommended target shared module: `products:yappc:core:yappc-shared`
- Recommended canonical abstraction: YAPPC product-local shared plugin API.
- Migration plan:
  - Move scaffold and generators remaining live consumers onto yappc-shared plugin types.
  - Delete scaffold-local duplicates.
  - Add a boundary test preventing new plugin API types under scaffold packages.
- Impacted callers: YAPPC scaffold generators/core and any YAPPC infrastructure modules still importing the older `core.plugin` package.
- Expected payoff: resolves intra-product duplication without leaking YAPPC concerns into global platform.
- Complexity: low-medium
- Confidence: high

### 8. SecretProvider SPI Is Repeated Across Kernel, Agent-Core, and Data Cloud Event SPI

- Title: Secret provider SPI fragmentation
- Duplicate locations:
  - `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/security/SecretProvider.java`
  - `platform/java/agent-core/src/main/java/com/ghatana/agent/security/SecretProvider.java`
  - `products/data-cloud/platform-event/src/main/java/com/ghatana/datacloud/event/spi/secrets/SecretProvider.java`
- Current intent: named secret lookup and secret material access.
- Duplication type: conceptual to near
- Recommended target shared module: `platform:java:security` or `platform:contracts`, depending on whether the SPI remains minimal.
- Recommended canonical abstraction: one small secret-access SPI owned by platform security, with module-specific adapters as needed.
- Migration plan:
  - Compare the three interfaces and extract the minimal common operations.
  - Adapt kernel and Data Cloud implementations instead of copying interfaces.
  - Keep only module-specific extensions local.
- Impacted callers: kernel security services, agent-core secret consumers, Data Cloud event integrations.
- Expected payoff: avoids three incompatible secret contracts for the same capability.
- Complexity: high
- Confidence: medium

### 9. Repository Transaction Support Is Split Between Platform Database and AEP Orchestrator

- Title: Manual EntityManager transaction wrapper duplicated locally
- Duplicate locations:
  - `platform/java/database/src/main/java/com/ghatana/core/database/repository/JpaRepository.java`
  - `products/aep/orchestrator/src/main/java/com/ghatana/orchestrator/store/AbstractRepository.java`
- Current intent: reusable JPA transaction wrapper and CRUD support.
- Duplication type: conceptual
- Recommended target shared module: `platform:java:database`
- Recommended canonical abstraction: repository support split into:
  - a CRUD-oriented `JpaRepository`
  - a factory-backed `TransactionalRepositorySupport` for manual `EntityManager` lifecycle
- Migration plan:
  - Extract generic `execute` and transaction pattern into platform database.
  - Make AEP orchestrator repositories extend or delegate to that support class.
  - Keep AEP-specific query methods local.
- Impacted callers: AEP orchestrator persistence layer and future product repositories using manual `EntityManagerFactory` access.
- Expected payoff: one place for transaction wrapping, rollback handling, and repository logging policy.
- Complexity: medium
- Confidence: medium-high

### 10. YAPPC Local JsonUtils Is a Subset Duplicate of Platform JsonUtils

- Title: Product-local JSON helper duplicates platform utility
- Duplicate locations:
  - `platform/java/core/src/main/java/com/ghatana/platform/core/util/JsonUtils.java`
  - `products/yappc/core/refactorer/engine/src/main/java/com/ghatana/refactorer/languages/tsjs/util/JsonUtils.java`
- Current intent: Jackson serialization/deserialization helpers.
- Duplication type: near
- Recommended target shared module: `platform:java:core`
- Recommended canonical abstraction: platform `JsonUtils` only.
- Migration plan:
  - Replace YAPPC imports with platform `JsonUtils`.
  - Confirm the YAPPC code does not rely on a materially different mapper contract.
  - Delete the local utility.
- Impacted callers: YAPPC TS/JS refactorer engine.
- Expected payoff: trivial code reduction and fewer mapper-configuration drifts.
- Complexity: low
- Confidence: high

### 11. Kernel Data Cloud Adapter DTOs Are Parallel Transport Models

- Title: Kernel-local Data Cloud transport DTO duplication
- Duplicate locations:
  - `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/adapter/datacloud/DataReadRequest.java`
  - `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/adapter/datacloud/DataWriteRequest.java`
  - `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/adapter/datacloud/SchemaCreateRequest.java`
  - `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/adapter/datacloud/QueryResult.java`
  - `products/data-cloud/spi/src/main/java/com/ghatana/datacloud/spi/EntityStore.java`
  - `products/data-cloud/spi/src/main/java/com/ghatana/datacloud/spi/StoragePlugin.java`
- Current intent: Data Cloud CRUD/query transport between kernel and Data Cloud capabilities.
- Duplication type: conceptual
- Recommended target shared module: Data Cloud-owned shared contract surface, not kernel-local DTOs.
- Recommended canonical abstraction: Data Cloud-owned request/response contracts or adapter-facing SPI types, with kernel depending on that surface instead of maintaining its own parallel DTO package.
- Migration plan:
  - Identify exact overlapping operations.
  - Extract or reuse Data Cloud-owned contracts.
  - Update `DataCloudKernelAdapter` to consume them.
  - Delete kernel-local DTOs.
- Impacted callers: kernel Data Cloud adapter, integration tests, kernel-side clients.
- Expected payoff: a single contract owner for Data Cloud operations.
- Complexity: high
- Confidence: medium

### 12. Event Publishing Contracts Are Reimplemented Per Product Instead of Anchored to Platform Messaging

- Title: Repeated event-publishing transport logic
- Duplicate locations:
  - `products/data-cloud/platform-entity/src/main/java/com/ghatana/datacloud/entity/event/DomainEventPublisher.java`
  - `products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/infrastructure/event/InMemoryDomainEventPublisher.java`
  - `products/aep/orchestrator/src/main/java/com/ghatana/orchestrator/deployment/service/DeploymentEventPublisher.java`
  - `products/aep/orchestrator/src/main/java/com/ghatana/orchestrator/deployment/service/EventCloudDeploymentEventPublisher.java`
  - `platform/java/messaging/build.gradle.kts`
- Current intent: async event publication, local dispatch, and infrastructure-backed publishing.
- Duplication type: conceptual
- Recommended target shared module: `platform:java:messaging` for infrastructure-level publisher semantics; product-local domain event ports stay local.
- Recommended canonical abstraction:
  - platform messaging owns generic publisher/sink contracts and retry/error instrumentation helpers
  - product modules keep domain event types and domain ports but delegate transport mechanics to platform messaging
- Migration plan:
  - Add generic async event sink/publisher support to platform messaging.
  - Migrate AEP and Data Cloud implementations to it.
  - Keep `DomainEventPublisher` product-local as a domain port.
- Impacted callers: Data Cloud in-memory dispatchers, AEP orchestrator publishers, future products emitting infrastructure-managed events.
- Expected payoff: less repeated serialization, logging, and transport error handling.
- Complexity: medium-high
- Confidence: medium

## C. Module Placement Plan

### Remain Local

1. `products/yappc/core/refactorer/api/src/main/java/com/ghatana/refactorer/server/auth/TenantContext.java`
   - Carries subject, roles, and claims specific to the YAPPC refactorer API.

2. `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/security/SecurityContext.java`
   - Models kernel execution metadata, not just request authentication.

3. `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/ApiResponse.java`
   - Contains product-level meta and AI payload semantics.

4. `products/data-cloud/platform-entity/src/main/java/com/ghatana/datacloud/entity/event/DomainEventPublisher.java`
   - Domain ownership should remain with Data Cloud.

### Move to Product-Local Shared Module

1. YAPPC plugin registry/loader/context into YAPPC shared only.
2. PHR FHIR and Nepal HIE response envelopes into a single PHR-local shared transport type.
3. Data Cloud partitioned `HybridStateStore` port remains Data Cloud-owned even after reusing platform `SyncStrategy`.
4. Kernel-to-Data Cloud contract convergence should target Data Cloud-owned shared contracts, not platform global modules.

### Move to Global Shared Module

1. `EventloopTestBase` and `EventloopTestUtil` into platform testing only.
2. Generic `HybridStateStore` implementation and `SyncStrategy` into platform core.
3. Canonical immutable tenant-scope value object into shared platform contracts or governance.
4. Shared request-scoped `SecurityContext` contract into platform security.
5. Shared `SecretProvider` SPI into platform security or platform contracts.
6. Generic transaction/repository support into platform database.
7. JSON helper into platform core.
8. Generic HTTP error/response building into platform HTTP.
9. Generic event transport helper APIs into platform messaging.

## D. Refactor Sequence

1. Define the canonical abstraction in the correct owning module.
2. Add or update regression tests around current behavior before moving callers.
3. Migrate the easiest callers first inside the same bounded context.
4. Add compatibility adapters only where needed for atomic migration.
5. Move cross-module or cross-product consumers after the abstraction is stable.
6. Delete duplicate implementations immediately after the last caller moves.
7. Add boundary tests or source-scan guards preventing the duplicate pattern from reappearing.
8. Run module-scoped compile, tests, and architecture checks before widening rollout.

### Recommended Execution Order

1. Eventloop test support
2. YAPPC `JsonUtils`
3. PHR response envelope merge
4. AEP `HttpHelper` onto platform HTTP
5. YAPPC plugin API cleanup
6. AEP `HybridStateStore` import collapse onto platform core
7. `ValidationResult` platform cleanup
8. `TenantContext` immutable contract extraction
9. `SecurityContext` platform/security-gateway convergence
10. Repository support extraction
11. `SecretProvider` SPI convergence
12. Kernel Data Cloud DTO convergence and event-publishing transport cleanup

## E. Exact Code Changes

### Extract

1. Shared ActiveJ test harness from:
   - `platform/java/testing/src/main/java/com/ghatana/platform/testing/activej/EventloopTestBase.java`
   - `platform/java/testing/src/main/java/com/ghatana/platform/testing/activej/EventloopTestUtil.java`

2. Generic transaction wrapper from:
   - `products/aep/orchestrator/src/main/java/com/ghatana/orchestrator/store/AbstractRepository.java`

3. Shared immutable tenant-scope record from duplicated logic in:
   - `platform/java/domain/src/main/java/com/ghatana/platform/domain/eventstore/TenantContext.java`
   - `products/data-cloud/spi/src/main/java/com/ghatana/datacloud/spi/TenantContext.java`

### Merge

1. `platform-kernel/kernel-core/src/test/java/com/ghatana/platform/testing/activej/EventloopTestBase.java`
   into
   `platform/java/testing/src/main/java/com/ghatana/platform/testing/activej/EventloopTestBase.java`

2. `platform-kernel/kernel-core/src/test/java/com/ghatana/platform/testing/activej/EventloopTestUtil.java`
   into
   `platform/java/testing/src/main/java/com/ghatana/platform/testing/activej/EventloopTestUtil.java`

3. `products/phr/src/main/java/com/ghatana/phr/api/FhirApiResponse.java`
   and
   `products/phr/src/main/java/com/ghatana/phr/api/NepalHieApiResponse.java`
   into one PHR-local response type

4. AEP duplicate state-store classes in:
   - `products/aep/aep-engine/src/main/java/com/ghatana/core/state/HybridStateStore.java`
   - `products/aep/aep-engine/src/main/java/com/ghatana/core/state/SyncStrategy.java`
   into platform core usage

### Move

1. Remaining YAPPC scaffold plugin API users from:
   - `products/yappc/core/scaffold/generators/src/main/java/com/ghatana/yappc/core/plugin/PluginRegistry.java`
   - `products/yappc/core/scaffold/generators/src/main/java/com/ghatana/yappc/core/plugin/PluginLoader.java`
   - `products/yappc/core/scaffold/generators/src/main/java/com/ghatana/yappc/core/plugin/PluginContext.java`
   to the YAPPC shared equivalents

2. YAPPC refactorer JSON helpers from:
   - `products/yappc/core/refactorer/engine/src/main/java/com/ghatana/refactorer/languages/tsjs/util/JsonUtils.java`
   to platform `JsonUtils`

### Rename or Disambiguate

1. Governance holder-style `TenantContext` in:
   - `platform/java/governance/src/main/java/com/ghatana/platform/governance/security/TenantContext.java`
   so it is not confused with immutable transport/value objects

2. Potentially rename the AEP `statestore.hybrid` class after wrapping platform core behavior, to avoid two public `HybridStateStore` types in the same product area

### Remove

1. Kernel duplicate `EventloopTestBase` and `EventloopTestUtil` test files
2. AEP exact duplicate `HybridStateStore` and `SyncStrategy` files under `com.ghatana.core.state` after imports move
3. YAPPC local `JsonUtils` after platform migration
4. YAPPC scaffold plugin registry/context/loader duplicates after yappc-shared migration
5. One of the two platform-level `ValidationResult` types after compatibility migration completes

### Replace Usages Of

1. `products/aep/server/src/main/java/com/ghatana/aep/server/http/HttpHelper.java`
   with platform HTTP `ResponseBuilder` and `ErrorResponse`

2. security-gateway `SecurityContext` imports with the platform security contract once the canonical API is widened

3. kernel Data Cloud adapter local DTOs with Data Cloud-owned shared contracts once extracted

## F. Risk Register

1. Over-generalization
   - `HybridStateStore`, `SecurityContext`, and `TenantContext` each have one truly generic core and several product-specific extensions. Extract only the stable common core.

2. Domain leakage
   - Data Cloud state partitioning, AI metadata envelopes, and kernel execution context should not be pushed into global platform modules.

3. Module coupling
   - `SecretProvider` and kernel Data Cloud DTO convergence can easily create upward dependencies if the canonical owner is chosen incorrectly.

4. Cyclic dependencies
   - Kernel should not start depending directly on product-local modules. Use contracts or product-owned shared SPI modules.

5. Serialization compatibility risk
   - Merging `ValidationResult` and API envelopes can break wire contracts if JSON shape changes. Freeze JSON contract tests before refactoring.

6. Persistence/query behavior change risk
   - `AbstractRepository` extraction must preserve transaction lifetime, rollback behavior, and `EntityManager` ownership.

7. Concurrency/threading risk
   - `TenantContext` and `SecurityContext` holder refactors must preserve thread-local cleanup and async propagation semantics.

8. Test coverage gaps
   - Eventloop test support is well-covered. `SecretProvider`, `SecurityContext`, and kernel Data Cloud adapter contract convergence need additional contract tests before migration.

9. Boundary regression risk
   - Plugin framework classes with the same names are not always the same abstraction. Kernel and standalone platform plugin systems must remain separate unless semantics are unified first.

## Top 10 Immediate Java Consolidations

1. Remove the exact duplicate ActiveJ test harness in kernel-core and standardize on platform-testing.
2. Replace AEP `com.ghatana.core.state` `HybridStateStore` and `SyncStrategy` with the platform core versions.
3. Collapse YAPPC refactorer `JsonUtils` onto platform `JsonUtils`.
4. Merge `FhirApiResponse` and `NepalHieApiResponse` into one local response type.
5. Refactor AEP `HttpHelper` to delegate to platform `ResponseBuilder` and `ErrorResponse`.
6. Finish YAPPC scaffold-to-yappc-shared plugin API migration for `PluginRegistry`, `PluginLoader`, and `PluginContext`.
7. Extract a single immutable tenant-scope record from the duplicated platform-domain and Data Cloud SPI `TenantContext` classes.
8. Unify security-gateway request `SecurityContext` with platform security `SecurityContext`.
9. Extract the AEP `AbstractRepository` transaction wrapper into platform database support.
10. Finish the platform `ValidationResult` convergence so only one platform-wide type remains.

## Top 10 Strategic Module Consolidations

1. Canonical tenant-scope contracts under a shared platform contract module.
2. Canonical request-scoped security contract under platform security.
3. One generic state-store core in platform with product-local state extensions only where needed.
4. One YAPPC plugin API surface in yappc-shared.
5. One secret-access SPI for kernel, agent-core, and product integrations.
6. One transaction/repository support layer in platform database.
7. One platform HTTP error/response foundation used by product handlers.
8. One platform messaging transport helper layer for publisher/error/retry wiring.
9. One Data Cloud-owned contract surface consumed by kernel adapters instead of kernel-local DTOs.
10. One platform validation-result contract with compatibility wrappers only during migration.

## Do Not Abstract Yet

1. Kernel `PluginRegistry` versus standalone platform `PluginRegistry`
   - Same name, different boundary and lifecycle intent.

2. Data Cloud `ApiResponse` success envelope
   - Its AI and meta payload are product semantics, not platform defaults.

3. Kernel `SecurityContext` versus platform request `SecurityContext`
   - The kernel model carries execution and session concerns beyond normal request auth.

4. YAPPC auth `TenantContext`
   - It includes subject, roles, and claims specific to that API boundary.

5. `QueryResult` as a global type
   - The repo has many unrelated local `QueryResult` records/classes; only merge when the underlying contract is actually shared.

6. Local nested `ValidationResult` records inside validators/services
   - Many are method-local result carriers, not reusable contracts.

7. Remaining local `HealthStatus` enums nested inside isolated services
   - Only extract when they are part of a cross-module API, not just internal implementation detail.

## Evidence Notes

This report is based on:

1. Repository-wide Java name-frequency and duplication scans.
2. Targeted source inspection of the strongest duplicate clusters.
3. Existing repository memory on prior consolidations and boundary decisions, including:
   - shared-modules audit follow-through
   - shared-modules audit remediation
   - earlier AEP controller extraction

The report does not assume that every identical class name represents the same abstraction. Findings were only promoted when source inspection showed exact, near, or structurally meaningful conceptual duplication.

## Recommended Next Step

Convert the immediate items into tracked execution tickets in this order:

1. Test support duplication
2. AEP state-store collapse
3. YAPPC plugin and JSON helper cleanup
4. Platform validation and tenant/security contract convergence
5. Repository and event transport consolidation