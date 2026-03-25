# Shared Modules Audit Report

## Executive Summary

The Java shared-module estate under `platform`, `io`, and `shared-services` is viable but not yet stable as a reusable platform surface. The strongest parts are the `audit`, `contracts`, `runtime`, `http`, and parts of `core` modules where responsibilities are narrow and naming is mostly coherent. The main risks come from duplicated contracts, parallel abstractions that model the same concern differently, stale compatibility shims that remain on the public surface, and service consumers binding to implementation classes instead of stable ports.

The most urgent issues to resolve are:

1. `platform/java/audit` ships a JPA-backed service that persists `AuditEvent` through `EntityManager`, but `AuditEvent` is not a JPA entity. That implementation is not production-safe as written.
2. `platform/java/core`, `platform/java/config`, and `platform/java/kernel` each expose overlapping `Result` and `ValidationResult` shapes. This creates contract drift and makes shared validation/error handling harder to centralize.
3. `platform/java/kernel` contains two separate `ContractValidator` APIs in neighboring packages (`contract` and `contracts`) with incompatible responsibilities and result models.
4. `platform/java/database`, `platform/java/plugin`, `platform/java/kernel`, and `platform/java/agent-core` each define their own `HealthStatus` semantics, preventing a canonical health contract from emerging.
5. `shared-services/auth-gateway` and `shared-services/user-profile-service` couple directly to the concrete JWT implementation instead of the shared security port, which undermines reuse and replacement.
6. `platform/java/testing` is useful but too broad: it exports a large toolchain and many unrelated helpers through `api`, which encourages accidental transitive coupling and makes ownership blurry.

Overall shared-module health is `mixed`: the codebase contains strong building blocks, but the public shared surface is still noisier and more fragmented than a platform layer should be.

## Scope Reviewed

Reviewed Java code in:

- `/Users/samujjwal/Development/ghatana/platform/contracts`
- `/Users/samujjwal/Development/ghatana/platform/java/agent-core`
- `/Users/samujjwal/Development/ghatana/platform/java/ai-integration`
- `/Users/samujjwal/Development/ghatana/platform/java/audit`
- `/Users/samujjwal/Development/ghatana/platform/java/config`
- `/Users/samujjwal/Development/ghatana/platform/java/connectors`
- `/Users/samujjwal/Development/ghatana/platform/java/core`
- `/Users/samujjwal/Development/ghatana/platform/java/database`
- `/Users/samujjwal/Development/ghatana/platform/java/distributed-cache`
- `/Users/samujjwal/Development/ghatana/platform/java/domain`
- `/Users/samujjwal/Development/ghatana/platform/java/governance`
- `/Users/samujjwal/Development/ghatana/platform/java/http`
- `/Users/samujjwal/Development/ghatana/platform/java/kernel`
- `/Users/samujjwal/Development/ghatana/platform/java/observability`
- `/Users/samujjwal/Development/ghatana/platform/java/plugin`
- `/Users/samujjwal/Development/ghatana/platform/java/runtime`
- `/Users/samujjwal/Development/ghatana/platform/java/security`
- `/Users/samujjwal/Development/ghatana/platform/java/testing`
- `/Users/samujjwal/Development/ghatana/platform/java/workflow`
- `/Users/samujjwal/Development/ghatana/io`
- `/Users/samujjwal/Development/ghatana/shared-services/ai-inference-service`
- `/Users/samujjwal/Development/ghatana/shared-services/auth-gateway`
- `/Users/samujjwal/Development/ghatana/shared-services/feature-store-ingest`
- `/Users/samujjwal/Development/ghatana/shared-services/user-profile-service`

Review method:

- build and settings inventory from `settings.gradle.kts` and module `build.gradle.kts`
- source and test surface inventory
- public API sampling and package-level review
- import and consumer tracing with `rg`
- duplicate-name and duplicate-responsibility analysis
- static review only; full monorepo build and runtime verification were not executed

## Shared Module Inventory

| Module | Path | Main Java files | Test Java files | Notes |
|---|---|---:|---:|---|
| contracts | `platform/contracts` | 2 | 19 | Generator/tooling module, not a broad runtime shared library |
| core | `platform/java/core` | 82 | 23 | Base shared utilities, exceptions, validation, service models |
| domain | `platform/java/domain` | 63 | 10 | Shared domain abstractions |
| database | `platform/java/database` | 40 | 12 | Shared DB, cache, health, migration code |
| http | `platform/java/http` | 17 | 6 | Shared HTTP helpers and API infrastructure |
| observability | `platform/java/observability` | 61 | 14 | Logging, metrics, tracing, health-related infrastructure |
| testing | `platform/java/testing` | 55 | 14 | Shared test helpers and fixtures |
| runtime | `platform/java/runtime` | 9 | 3 | Runtime bootstrap support |
| config | `platform/java/config` | 21 | 2 | Config parsing and validation |
| workflow | `platform/java/workflow` | 49 | 15 | Workflow contracts and runtime pieces |
| plugin | `platform/java/plugin` | 37 | 6 | Plugin API and registry |
| ai-integration | `platform/java/ai-integration` | 52 | 12 | Shared AI client/integration code |
| governance | `platform/java/governance` | 26 | 6 | Governance and policy abstractions |
| security | `platform/java/security` | 63 | 11 | Auth, JWT, ports and implementations |
| agent-core | `platform/java/agent-core` | 156 | 36 | Shared agent framework |
| connectors | `platform/java/connectors` | 15 | 7 | Connector abstractions |
| audit | `platform/java/audit` | 6 | 2 | Shared audit contracts and implementations |
| kernel | `platform/java/kernel` | 76 | 18 | Shared kernel, registry, contracts, module model |
| distributed-cache | `platform/java/distributed-cache` | 6 | 3 | Shared distributed cache surface |
| io shims | `io` | 4 | 0 | Vendored ActiveJ classes in source tree |
| ai-inference-service | `shared-services/ai-inference-service` | 2 | 1 | Thin service consumer |
| auth-gateway | `shared-services/auth-gateway` | 8 | 0 | Security and HTTP consumer |
| feature-store-ingest | `shared-services/feature-store-ingest` | 1 | 0 | Thin service consumer |
| user-profile-service | `shared-services/user-profile-service` | 4 | 1 | Security and data consumer |

## Dependency and Consumer Overview

Shared dependency direction observed:

- `core` is the main foundational dependency and exports exceptions, validation, value types, and utility abstractions.
- `runtime`, `http`, `config`, `security`, `database`, `observability`, `plugin`, `workflow`, and `kernel` build on `core`.
- `shared-services/*` consume shared modules directly, especially `security`, `http`, and `core`.
- `testing` is intended as a shared testing layer but currently acts as a transitive dependency bundle for many third-party test tools.
- `io` is not registered as a Gradle module; it is a source-level vendor area for selected ActiveJ classes used to satisfy API or compatibility gaps.

Important consumer relationships:

- `shared-services/auth-gateway` and `shared-services/user-profile-service` import [`JwtTokenProvider`](/Users/samujjwal/Development/ghatana/platform/java/security/src/main/java/com/ghatana/platform/security/jwt/JwtTokenProvider.java) directly instead of the port [`JwtTokenProvider`](/Users/samujjwal/Development/ghatana/platform/java/security/src/main/java/com/ghatana/platform/security/port/JwtTokenProvider.java).
- `kernel` exposes two validator families: [`com.ghatana.kernel.contract.ContractValidator`](/Users/samujjwal/Development/ghatana/platform/java/kernel/src/main/java/com/ghatana/kernel/contract/ContractValidator.java) and [`com.ghatana.kernel.contracts.ContractValidator`](/Users/samujjwal/Development/ghatana/platform/java/kernel/src/main/java/com/ghatana/kernel/contracts/ContractValidator.java).
- `database`, `plugin`, `kernel`, and `agent-core` each expose a `HealthStatus` concept with incompatible semantics.
- `core` contains duplicate public service endpoint and result abstractions that currently have little or no visible consumer differentiation.

## Findings

### F-001

- Finding ID: `F-001`
- Severity: `critical`
- File path: `/Users/samujjwal/Development/ghatana/platform/java/audit/src/main/java/com/ghatana/platform/audit/JpaAuditService.java`
- Module or package name: `platform/java/audit`
- Problem to resolve: `JpaAuditService` persists and queries `AuditEvent` via JPA, but [`AuditEvent`](/Users/samujjwal/Development/ghatana/platform/java/audit/src/main/java/com/ghatana/platform/audit/AuditEvent.java) is a plain immutable object with no JPA mapping annotations.
- Why it matters: The shared production implementation is likely unusable in real deployments and can fail at startup or first write, making the module unsafe to reuse.
- Evidence: [`JpaAuditService`](/Users/samujjwal/Development/ghatana/platform/java/audit/src/main/java/com/ghatana/platform/audit/JpaAuditService.java) calls `entityManager.persist(event)` and uses JPQL `"SELECT e FROM AuditEvent e..."`; [`AuditEvent`](/Users/samujjwal/Development/ghatana/platform/java/audit/src/main/java/com/ghatana/platform/audit/AuditEvent.java) contains no `@Entity`, `@Id`, or field mapping.
- Consumer impact: Any service wiring `JpaAuditService` as the documented production implementation will get broken persistence behavior.
- Duplication type if applicable: `none`
- Consolidation recommendation if applicable: Split domain event and persistence entity, or make `AuditEvent` the canonical persistence-backed aggregate with explicit mapping.
- Target location for consolidated code if applicable: `platform/java/audit`
- Migration notes: Introduce `AuditEventEntity` plus mapper first, keep `AuditService` and `AuditQueryService` stable, then swap `JpaAuditService` internals.
- Exact fix recommendation: Add a real JPA entity model with `@Entity`, `@Table`, `@Id`, supported field types, and serialized `details`; alternatively remove `JpaAuditService` from the shared public API until the persistence model exists.
- Test gaps: Missing integration test that boots JPA and verifies record, query, and tenant isolation behavior.
- Documentation gaps: Current class Javadoc claims production readiness without documenting the persistence prerequisites or current incompatibility.

### F-002

- Finding ID: `F-002`
- Severity: `high`
- File path: `/Users/samujjwal/Development/ghatana/platform/java/core/src/main/java/com/ghatana/platform/core/common/Result.java`
- Module or package name: `platform/java/core`
- Problem to resolve: `core` exposes two different `Result` abstractions: [`core/common/Result`](/Users/samujjwal/Development/ghatana/platform/java/core/src/main/java/com/ghatana/platform/core/common/Result.java) and [`core/util/Result`](/Users/samujjwal/Development/ghatana/platform/java/core/src/main/java/com/ghatana/platform/core/util/Result.java).
- Why it matters: Shared consumers cannot tell which is canonical. The two types model failure differently, support different operations, and invite long-term drift in error-handling code.
- Evidence: One type is a simple `Result<T>` wrapper with `ErrorCode` and message; the other is a sealed functional `Result<T,E>` with `map`, `flatMap`, and `mapError`. Both are public and live in `core`.
- Consumer impact: New consumers can pick different styles, forcing adapters or parallel support in shared APIs later.
- Duplication type if applicable: `code`
- Consolidation recommendation if applicable: Standardize on one canonical result abstraction and deprecate the other.
- Target location for consolidated code if applicable: `platform/java/core/src/main/java/com/ghatana/platform/core/result`
- Migration notes: Introduce a final package and adapter methods, deprecate both old types, migrate internal shared modules first, then remove old types after consumer cleanup.
- Exact fix recommendation: Keep the richer generic result if the platform wants functional composition; otherwise move to exception-based shared contracts and eliminate both wrappers from the public surface.
- Test gaps: No compatibility or migration tests proving old and new result semantics align.
- Documentation gaps: Neither class clearly states canonical status or when a consumer should choose it over the other.

### F-003

- Finding ID: `F-003`
- Severity: `high`
- File path: `/Users/samujjwal/Development/ghatana/platform/java/config/src/main/java/com/ghatana/config/runtime/engine/ConfigurationException.java`
- Module or package name: `platform/java/config`
- Problem to resolve: A deprecated duplicate `ConfigurationException` still exists even though the canonical replacement already lives in `core`.
- Why it matters: Shared exception contracts remain fragmented, and consumers can keep importing the stale type instead of converging on the platform-wide one.
- Evidence: [`core/exception/ConfigurationException`](/Users/samujjwal/Development/ghatana/platform/java/core/src/main/java/com/ghatana/platform/core/exception/ConfigurationException.java) explicitly states it replaces deprecated copies; [`config/runtime/engine/ConfigurationException`](/Users/samujjwal/Development/ghatana/platform/java/config/src/main/java/com/ghatana/config/runtime/engine/ConfigurationException.java) remains present.
- Consumer impact: Exception handling, documentation, and migration guidance stay inconsistent across modules.
- Duplication type if applicable: `code`
- Consolidation recommendation if applicable: Remove or isolate the deprecated type behind a compatibility package with clear removal timing.
- Target location for consolidated code if applicable: `platform/java/core/src/main/java/com/ghatana/platform/core/exception`
- Migration notes: Add compile-time deprecation warnings and update all in-repo imports before deletion.
- Exact fix recommendation: Migrate remaining imports to the core exception, add a removal issue/version target, and delete the deprecated config copy once no consumers remain.
- Test gaps: No architectural guard test preventing new modules from importing the deprecated config exception.
- Documentation gaps: The deprecated copy does not tell consumers when it will be removed or what exact import to switch to.

### F-004

- Finding ID: `F-004`
- Severity: `high`
- File path: `/Users/samujjwal/Development/ghatana/platform/java/kernel/src/main/java/com/ghatana/kernel/contract/ContractValidator.java`
- Module or package name: `platform/java/kernel`
- Problem to resolve: `kernel` exposes two distinct `ContractValidator` APIs in adjacent packages with overlapping names and different responsibilities.
- Why it matters: This is a contract-stability risk in a core shared module. Consumers can choose the wrong package and end up coupled to the wrong registration or validation model.
- Evidence: [`kernel/contract/ContractValidator`](/Users/samujjwal/Development/ghatana/platform/java/kernel/src/main/java/com/ghatana/kernel/contract/ContractValidator.java) validates capabilities, modules, APIs, and schemas with structured warnings/errors; [`kernel/contracts/ContractValidator`](/Users/samujjwal/Development/ghatana/platform/java/kernel/src/main/java/com/ghatana/kernel/contracts/ContractValidator.java) is a pluggable validator over `KernelContract` with `List<String>` errors.
- Consumer impact: New kernel integrations must understand package nuance instead of a single validator contract, increasing onboarding cost and miswiring risk.
- Duplication type if applicable: `ownership`
- Consolidation recommendation if applicable: Define one canonical validator SPI and make the other package internal or deprecated.
- Target location for consolidated code if applicable: `platform/java/kernel/src/main/java/com/ghatana/kernel/contracts`
- Migration notes: Preserve the richer validator details in a unified result model, then bridge the simpler validators through adapters during migration.
- Exact fix recommendation: Pick one package namespace for public kernel contract validation, rename the non-canonical package to internal infrastructure if needed, and collapse result types into one shared record.
- Test gaps: Missing architectural tests that enforce a single public validator SPI.
- Documentation gaps: Package-level documentation does not explain why both validator families exist or which is public.

### F-005

- Finding ID: `F-005`
- Severity: `high`
- File path: `/Users/samujjwal/Development/ghatana/platform/java/core/src/main/java/com/ghatana/platform/validation/ValidationResult.java`
- Module or package name: `platform/java/core`, `platform/java/config`, `platform/java/kernel`
- Problem to resolve: The codebase exposes multiple incompatible `ValidationResult` contracts for shared concerns.
- Why it matters: Validation is a classic cross-cutting shared concern. Fragmented result models cause duplicated adapters, inconsistent error payloads, and harder standardization across modules.
- Evidence: [`core/validation/ValidationResult`](/Users/samujjwal/Development/ghatana/platform/java/core/src/main/java/com/ghatana/platform/validation/ValidationResult.java) stores `ValidationError` objects; [`config/validation/ValidationResult`](/Users/samujjwal/Development/ghatana/platform/java/config/src/main/java/com/ghatana/platform/config/validation/ValidationResult.java) stores `List<String>`; both kernel validator packages define their own nested `ValidationResult` records.
- Consumer impact: Consumers cannot rely on one validation contract when moving between config, kernel, and core validation layers.
- Duplication type if applicable: `logic`
- Consolidation recommendation if applicable: Centralize a single shared validation result model with typed errors and optional warnings, then layer module-specific adapters on top.
- Target location for consolidated code if applicable: `platform/java/core/src/main/java/com/ghatana/platform/validation`
- Migration notes: Start by making config and kernel validators emit the core result internally while preserving their external signatures through adapter methods.
- Exact fix recommendation: Promote `core` validation types as canonical, add warning/context support if needed, and remove duplicate nested/string-only result models from other modules.
- Test gaps: No cross-module tests prove that validation behavior and error semantics are consistent across shared modules.
- Documentation gaps: No module states which validation result is the platform standard.

### F-006

- Finding ID: `F-006`
- Severity: `high`
- File path: `/Users/samujjwal/Development/ghatana/shared-services/auth-gateway/src/main/java/com/ghatana/services/auth/AuthService.java`
- Module or package name: `shared-services/auth-gateway`, `shared-services/user-profile-service`, `platform/java/security`
- Problem to resolve: Shared services depend on the concrete JWT implementation instead of the security port.
- Why it matters: This bypasses the platform’s own abstraction boundary and makes replacement, testing, and policy enforcement harder.
- Evidence: [`AuthService`](/Users/samujjwal/Development/ghatana/shared-services/auth-gateway/src/main/java/com/ghatana/services/auth/AuthService.java), [`AuthGatewayLauncher`](/Users/samujjwal/Development/ghatana/shared-services/auth-gateway/src/main/java/com/ghatana/services/auth/AuthGatewayLauncher.java), [`TenantExtractor`](/Users/samujjwal/Development/ghatana/shared-services/auth-gateway/src/main/java/com/ghatana/services/auth/TenantExtractor.java), and [`UserProfileService`](/Users/samujjwal/Development/ghatana/shared-services/user-profile-service/src/main/java/com/ghatana/services/userprofile/UserProfileService.java) import [`platform.security.jwt.JwtTokenProvider`](/Users/samujjwal/Development/ghatana/platform/java/security/src/main/java/com/ghatana/platform/security/jwt/JwtTokenProvider.java) directly, even though [`platform.security.port.JwtTokenProvider`](/Users/samujjwal/Development/ghatana/platform/java/security/src/main/java/com/ghatana/platform/security/port/JwtTokenProvider.java) exists.
- Consumer impact: Alternate token providers, mocks, and policy-specific implementations require touching service code instead of wiring.
- Duplication type if applicable: `ownership`
- Consolidation recommendation if applicable: Make the port the only consumer-facing dependency and keep the concrete provider behind DI or factory wiring.
- Target location for consolidated code if applicable: `platform/java/security/src/main/java/com/ghatana/platform/security/port`
- Migration notes: Update constructors and DI bindings first, then block new imports of the implementation package outside `security`.
- Exact fix recommendation: Refactor service constructors and launchers to depend on `com.ghatana.platform.security.port.JwtTokenProvider`, bind the concrete provider at composition boundaries, and add import-boundary tests.
- Test gaps: Missing service tests proving the services run against a fake or alternate `JwtTokenProvider` implementation.
- Documentation gaps: Security module docs do not clearly say the port is the only supported consumer entry point.

### F-007

- Finding ID: `F-007`
- Severity: `medium`
- File path: `/Users/samujjwal/Development/ghatana/platform/java/core/src/main/java/com/ghatana/platform/service/ServiceEndpoint.java`
- Module or package name: `platform/java/core`
- Problem to resolve: `core` contains two public `ServiceEndpoint` records with the same name and nearly the same responsibility.
- Why it matters: Duplicate endpoint contracts create dead surface area and confusion for any module trying to model shared service targets.
- Evidence: [`platform/service/ServiceEndpoint`](/Users/samujjwal/Development/ghatana/platform/java/core/src/main/java/com/ghatana/platform/service/ServiceEndpoint.java) and [`platform/core/common/service/ServiceEndpoint`](/Users/samujjwal/Development/ghatana/platform/java/core/src/main/java/com/ghatana/platform/core/common/service/ServiceEndpoint.java) both exist; repository-wide search found no clear differentiated consumer base.
- Consumer impact: Future consumers may choose arbitrarily, entrenching duplication that currently appears mostly unused.
- Duplication type if applicable: `code`
- Consolidation recommendation if applicable: Keep one endpoint record and delete or deprecate the other.
- Target location for consolidated code if applicable: `platform/java/core/src/main/java/com/ghatana/platform/service`
- Migration notes: Mark one type deprecated immediately and add a temporary conversion method if external consumers exist.
- Exact fix recommendation: Audit actual external use, declare one canonical endpoint package, and remove the duplicate after a short deprecation window.
- Test gaps: No architectural or usage tests ensure only one endpoint model remains in use.
- Documentation gaps: The two classes do not explain which one is legacy or canonical.

### F-008

- Finding ID: `F-008`
- Severity: `medium`
- File path: `/Users/samujjwal/Development/ghatana/platform/java/database/src/main/java/com/ghatana/core/cache/redis/RedisCacheConfig.java`
- Module or package name: `platform/java/database`
- Problem to resolve: Two `RedisCacheConfig` implementations exist in different packages with overlapping responsibility.
- Why it matters: Shared cache configuration is exactly the kind of concern that should be canonical. Parallel config types force duplicate validation and migration work.
- Evidence: [`core/cache/redis/RedisCacheConfig`](/Users/samujjwal/Development/ghatana/platform/java/database/src/main/java/com/ghatana/core/cache/redis/RedisCacheConfig.java) and [`platform/database/cache/RedisCacheConfig`](/Users/samujjwal/Development/ghatana/platform/java/database/src/main/java/com/ghatana/platform/database/cache/RedisCacheConfig.java) both exist; test coverage only visibly targets the `platform.database.cache` variant.
- Consumer impact: Redis integrations may diverge on timeout, database index, and builder semantics depending on which class they select.
- Duplication type if applicable: `code`
- Consolidation recommendation if applicable: Define one canonical Redis cache config type and adapt existing cache implementations to it.
- Target location for consolidated code if applicable: `platform/java/database/src/main/java/com/ghatana/platform/database/cache`
- Migration notes: Keep the better-validated builder, add a converter from the legacy type if necessary, and then remove the duplicate.
- Exact fix recommendation: Standardize on the `platform.database.cache` package, migrate `AsyncRedisCache` and references, and deprecate the legacy `com.ghatana.core.cache.redis` config.
- Test gaps: Missing parity tests that compare old and new config semantics during migration.
- Documentation gaps: Neither package explains why both Redis config types coexist.

### F-009

- Finding ID: `F-009`
- Severity: `medium`
- File path: `/Users/samujjwal/Development/ghatana/platform/java/kernel/src/main/java/com/ghatana/kernel/health/HealthStatus.java`
- Module or package name: `platform/java/kernel`, `platform/java/plugin`, `platform/java/database`, `platform/java/agent-core`
- Problem to resolve: Shared modules model health state independently instead of sharing a canonical contract.
- Why it matters: Health checks are cross-cutting and often aggregated centrally. Divergent health enums and payloads make dashboards, probes, and orchestration logic harder to unify.
- Evidence: [`kernel/health/HealthStatus`](/Users/samujjwal/Development/ghatana/platform/java/kernel/src/main/java/com/ghatana/kernel/health/HealthStatus.java), [`plugin/HealthStatus`](/Users/samujjwal/Development/ghatana/platform/java/plugin/src/main/java/com/ghatana/platform/plugin/HealthStatus.java), [`database/health/HealthStatus`](/Users/samujjwal/Development/ghatana/platform/java/database/src/main/java/com/ghatana/core/database/health/HealthStatus.java), and [`agent-core/HealthStatus`](/Users/samujjwal/Development/ghatana/platform/java/agent-core/src/main/java/com/ghatana/agent/HealthStatus.java) expose incompatible states and payload structures.
- Consumer impact: Any aggregator or management plane must write adapters instead of depending on one shared status model.
- Duplication type if applicable: `logic`
- Consolidation recommendation if applicable: Centralize a minimal canonical health model and let specialized modules attach details separately.
- Target location for consolidated code if applicable: `platform/java/observability` or `platform/java/core`
- Migration notes: Start with an adapter layer and explicit mapping table for each existing health type before collapsing public APIs.
- Exact fix recommendation: Define one platform health contract with status, message, timestamp, and details; keep module-specific detail objects separate from the top-level status model.
- Test gaps: Missing contract tests ensuring module-specific health outputs map correctly to the canonical health surface.
- Documentation gaps: No shared documentation explains the intended relationship among the current health types.

### F-010

- Finding ID: `F-010`
- Severity: `medium`
- File path: `/Users/samujjwal/Development/ghatana/platform/java/testing/build.gradle.kts`
- Module or package name: `platform/java/testing`
- Problem to resolve: The testing module exposes an oversized public API and third-party dependency bundle via `api`.
- Why it matters: Shared test helpers should reduce duplication, not create a transitive dependency magnet. Over-exposed test infrastructure raises upgrade cost and blurs ownership boundaries.
- Evidence: [`testing/build.gradle.kts`](/Users/samujjwal/Development/ghatana/platform/java/testing/build.gradle.kts) exports JUnit, AssertJ, Mockito, Testcontainers, PostgreSQL JDBC, Awaitility, Log4j, gRPC, JSON Path, DataFaker, and ArchUnit as `api`; source layout also mixes ActiveJ helpers, container managers, data builders, chaos helpers, HTTP server helpers, and base classes.
- Consumer impact: Product tests can accidentally depend on many transitive tools and internal utilities without declaring intent, making cleanup or version changes expensive.
- Duplication type if applicable: `ownership`
- Consolidation recommendation if applicable: Split `testing` into narrower modules or reduce most third-party dependencies to `implementation` with focused exported facades.
- Target location for consolidated code if applicable: `platform/java/testing`, plus possible submodules `testing-core`, `testing-containers`, `testing-activej`
- Migration notes: Start with package-level ownership boundaries and dependency visibility cleanup before physically splitting modules.
- Exact fix recommendation: Keep only deliberately supported test APIs public, move bulk dependencies to `implementation` where possible, and separate unrelated helpers into cohesive packages or modules.
- Test gaps: Missing self-tests that verify intended public testing utilities without relying on transitive dependencies.
- Documentation gaps: The module lacks a package/module guide that states which helpers are supported public API versus internal convenience code.

### F-011

- Finding ID: `F-011`
- Severity: `medium`
- File path: `/Users/samujjwal/Development/ghatana/io/activej/common/time/Stopwatch.java`
- Module or package name: `io`
- Problem to resolve: The repository vendors upstream ActiveJ classes directly under `io.activej.*` without an explicit ownership or upgrade policy.
- Why it matters: Source vendoring under the original package name creates a maintenance trap. It is hard to tell whether these files are intentional forks, temporary compatibility shims, or stale copies.
- Evidence: `/Users/samujjwal/Development/ghatana/io/activej/common/time/Stopwatch.java`, `/Users/samujjwal/Development/ghatana/io/activej/eventloop/inspector/EventloopInspector.java`, `/Users/samujjwal/Development/ghatana/io/activej/promise/AbstractPromise.java`, and `/Users/samujjwal/Development/ghatana/io/activej/promise/SettablePromise.java` are copied upstream-style classes with ActiveJ copyright headers.
- Consumer impact: Future dependency upgrades can silently diverge from the vendored source, and consumers may not realize they are depending on forked behavior.
- Duplication type if applicable: `workflow`
- Consolidation recommendation if applicable: Either replace these with the official dependency version or formalize them as clearly named internal shims with documented reason and sync policy.
- Target location for consolidated code if applicable: `platform/java/runtime` or a dedicated `platform/java/activej-compat`
- Migration notes: First document all current consumers, then choose between removal, shading, or formal internal compatibility packaging.
- Exact fix recommendation: Add module-level documentation, mark the vendored area internal-only, and create a tracked decision record for whether the code is temporary or intentionally forked.
- Test gaps: Missing compatibility tests proving these vendored classes behave as expected relative to the ActiveJ version in use.
- Documentation gaps: There is no README or package-info explaining why these upstream classes live in-tree.

### F-012

- Finding ID: `F-012`
- Severity: `low`
- File path: `/Users/samujjwal/Development/ghatana/platform/java/plugin/src/main/java/com/ghatana/platform/plugin/PluginRegistry.java`
- Module or package name: `platform/java/plugin`, `platform/java/kernel`
- Problem to resolve: Plugin ownership is split between the public plugin module and kernel registry internals with similar names and lifecycle concerns.
- Why it matters: Even if scopes differ, overlapping names like `PluginRegistry` make the architecture harder to learn and can encourage responsibility creep.
- Evidence: Public [`platform/plugin/PluginRegistry`](/Users/samujjwal/Development/ghatana/platform/java/plugin/src/main/java/com/ghatana/platform/plugin/PluginRegistry.java) coexists with kernel-internal [`kernel/registry/PluginRegistry`](/Users/samujjwal/Development/ghatana/platform/java/kernel/src/main/java/com/ghatana/kernel/registry/PluginRegistry.java); kernel docs indicate consumers should prefer `KernelRegistry`, but the shared source still exposes both concepts.
- Consumer impact: Low immediate breakage risk, but medium long-term ownership confusion.
- Duplication type if applicable: `ownership`
- Consolidation recommendation if applicable: Clarify one public plugin lifecycle API and make the internal registry unmistakably internal by package and naming.
- Target location for consolidated code if applicable: `platform/java/plugin` for public API, `platform/java/kernel/.../internal` for internal registry infrastructure
- Migration notes: Rename or relocate internal registry types before further feature expansion.
- Exact fix recommendation: Keep `platform.plugin.PluginRegistry` public, move the kernel registry behind `internal` naming or package-private implementation where feasible, and document the boundary.
- Test gaps: Missing boundary tests that prevent external modules from using kernel-internal registry classes.
- Documentation gaps: Package docs do not explain the relationship between the two registry layers.

## Module-by-Module Review

### contracts

- Name and path: `contracts` at `/Users/samujjwal/Development/ghatana/platform/contracts`
- Purpose: Schema and POJO generation tooling.
- Main exports and responsibilities: `JsonSchemaBundleToPojoGenerator`, `ProtoToJsonSchemaGenerator`.
- Main consumers: Build and generation workflows, not broad runtime consumers.
- Key dependencies: Jackson, protobuf-related generation tooling.
- Review status: reviewed.
- Findings found in that unit: no material runtime API issue found.
- Duplicates or overlaps found: none confirmed in-scope.
- Consolidation opportunities: keep as tooling-only; avoid expanding it into a runtime contracts dumping ground.
- Test gaps: generator integration expectations should remain covered as schemas evolve.
- Documentation gaps: module purpose is implicit; add a short README explaining generation inputs and outputs.
- Naming concerns: none material.
- Maintainability concerns: low.
- Brief statement if no material issue was found: no material issue found; scope is narrow and cohesive.

### core

- Name and path: `core` at `/Users/samujjwal/Development/ghatana/platform/java/core`
- Purpose: foundational shared abstractions, exceptions, validation, utility types.
- Main exports and responsibilities: result types, exceptions, validation, service endpoint models, common helpers.
- Main consumers: nearly all platform modules.
- Key dependencies: minimal base dependencies plus annotations.
- Review status: reviewed.
- Findings found in that unit: `F-002`, `F-005`, `F-007`.
- Duplicates or overlaps found: duplicate `Result`, duplicate `ServiceEndpoint`, overlapping validation result patterns.
- Consolidation opportunities: centralize canonical result and validation contracts; prune legacy compat types.
- Test gaps: add architectural tests for canonical imports.
- Documentation gaps: canonical-vs-legacy status is unclear across several packages.
- Naming concerns: `common`, `util`, and `service` packages overlap semantically.
- Maintainability concerns: medium due to public-surface sprawl.

### domain

- Name and path: `domain` at `/Users/samujjwal/Development/ghatana/platform/java/domain`
- Purpose: shared domain abstractions and reusable model logic.
- Main exports and responsibilities: domain model base types and shared policies.
- Main consumers: platform and product modules.
- Key dependencies: `core`.
- Review status: sampled and structurally reviewed.
- Findings found in that unit: no material issue confirmed from the shared-module audit.
- Duplicates or overlaps found: none confirmed.
- Consolidation opportunities: monitor for overlap with `kernel` and `workflow` as those modules evolve.
- Test gaps: keep consumer contract tests aligned with real product use.
- Documentation gaps: package-level overview would help because domain boundaries are broad.
- Naming concerns: none material in sampled surface.
- Maintainability concerns: low to medium due to size.
- Brief statement if no material issue was found: no material issue confirmed in this review.

### database

- Name and path: `database` at `/Users/samujjwal/Development/ghatana/platform/java/database`
- Purpose: shared database access, migrations, cache, and DB health support.
- Main exports and responsibilities: Redis cache config and client helpers, Flyway migration wrapper, DB health model.
- Main consumers: platform services and persistence-heavy modules.
- Key dependencies: JDBC, Redis, Flyway, Jackson.
- Review status: reviewed.
- Findings found in that unit: `F-008`, contributes to `F-009`.
- Duplicates or overlaps found: duplicate `RedisCacheConfig`, module-specific `HealthStatus`, nested migration validation result.
- Consolidation opportunities: unify cache config and move toward a shared health contract.
- Test gaps: add migration-path tests for Redis config consolidation.
- Documentation gaps: canonical cache config is not documented.
- Naming concerns: `com.ghatana.core.database` and `com.ghatana.platform.database` packages coexist.
- Maintainability concerns: medium due to mixed legacy/current package roots.

### http

- Name and path: `http` at `/Users/samujjwal/Development/ghatana/platform/java/http`
- Purpose: shared HTTP-layer utilities and contracts.
- Main exports and responsibilities: API and request/response support.
- Main consumers: services and gateway modules.
- Key dependencies: runtime and core.
- Review status: sampled and structurally reviewed.
- Findings found in that unit: no material issue confirmed from this audit.
- Duplicates or overlaps found: none confirmed.
- Consolidation opportunities: keep as a narrow transport layer.
- Test gaps: ensure service-facing behavior remains covered.
- Documentation gaps: package overview would still help new consumers.
- Naming concerns: none material.
- Maintainability concerns: low.
- Brief statement if no material issue was found: no material issue confirmed in this review.

### observability

- Name and path: `observability` at `/Users/samujjwal/Development/ghatana/platform/java/observability`
- Purpose: shared logging, metrics, tracing, and health-related infrastructure.
- Main exports and responsibilities: observability wiring and monitoring helpers.
- Main consumers: runtime services and platform modules.
- Key dependencies: runtime, config, http.
- Review status: reviewed structurally.
- Findings found in that unit: contributes to `F-009`.
- Duplicates or overlaps found: observability-related health ownership overlaps with database/kernel/plugin-specific health models.
- Consolidation opportunities: own the canonical health envelope if the platform wants observability to be the aggregation layer.
- Test gaps: add adapter tests if a canonical health model is introduced.
- Documentation gaps: clarify whether observability owns health contracts or only reports on them.
- Naming concerns: none material in sampled surface.
- Maintainability concerns: medium due to cross-cutting reach.

### testing

- Name and path: `testing` at `/Users/samujjwal/Development/ghatana/platform/java/testing`
- Purpose: shared testing infrastructure.
- Main exports and responsibilities: base test classes, ActiveJ test helpers, container helpers, data builders, HTTP test helpers, chaos helpers.
- Main consumers: product and platform test suites.
- Key dependencies: JUnit, AssertJ, Mockito, Testcontainers, Awaitility, gRPC, logging, ArchUnit.
- Review status: reviewed.
- Findings found in that unit: `F-010`.
- Duplicates or overlaps found: multiple clusters of helpers with different ownership expectations are bundled together.
- Consolidation opportunities: split by concern or reduce API exposure.
- Test gaps: verify supported public testing APIs independently from transitive third-party exports.
- Documentation gaps: no module guide for supported test utilities.
- Naming concerns: several classes use local or placeholder-style names such as `MyActiveJTest`, `MyBaseActiveJTest`, `MyActiveJBaseStyleTest`.
- Maintainability concerns: high relative to size because of scope sprawl.

### runtime

- Name and path: `runtime` at `/Users/samujjwal/Development/ghatana/platform/java/runtime`
- Purpose: runtime bootstrap and shared runtime support.
- Main exports and responsibilities: runtime composition primitives.
- Main consumers: observability, services, and bootstrap code.
- Key dependencies: core.
- Review status: sampled and structurally reviewed.
- Findings found in that unit: no standalone material issue confirmed.
- Duplicates or overlaps found: none confirmed.
- Consolidation opportunities: potential home for formalized compatibility shims currently in `io`.
- Test gaps: keep bootstrap behavior covered.
- Documentation gaps: package-level purpose statement would help.
- Naming concerns: none material.
- Maintainability concerns: low.
- Brief statement if no material issue was found: no material issue confirmed in this review.

### config

- Name and path: `config` at `/Users/samujjwal/Development/ghatana/platform/java/config`
- Purpose: shared configuration parsing and validation.
- Main exports and responsibilities: config validation, config source handling, runtime config support.
- Main consumers: runtime services and modules with external configuration.
- Key dependencies: core.
- Review status: reviewed.
- Findings found in that unit: `F-003`, contributes to `F-005`.
- Duplicates or overlaps found: duplicate `ConfigurationException`, string-only `ValidationResult`.
- Consolidation opportunities: align on core exception and validation model.
- Test gaps: architectural tests to ban imports of deprecated exception.
- Documentation gaps: canonical exception and validation contract are not clearly stated.
- Naming concerns: legacy `config.runtime.engine` package remains visible.
- Maintainability concerns: medium due to coexistence of current and deprecated paths.

### workflow

- Name and path: `workflow` at `/Users/samujjwal/Development/ghatana/platform/java/workflow`
- Purpose: shared workflow contracts and execution support.
- Main exports and responsibilities: workflow-related abstractions and runtime helpers.
- Main consumers: workflow-enabled services and platform modules.
- Key dependencies: core, runtime.
- Review status: sampled and structurally reviewed.
- Findings found in that unit: no material issue confirmed from this audit.
- Duplicates or overlaps found: none confirmed.
- Consolidation opportunities: monitor overlap with agent and kernel orchestration concepts.
- Test gaps: keep end-to-end workflow contract coverage aligned with consumers.
- Documentation gaps: package-level purpose overview would help.
- Naming concerns: none material.
- Maintainability concerns: medium due to concept breadth.
- Brief statement if no material issue was found: no material issue confirmed in this review.

### plugin

- Name and path: `plugin` at `/Users/samujjwal/Development/ghatana/platform/java/plugin`
- Purpose: public plugin API and plugin lifecycle management.
- Main exports and responsibilities: `Plugin`, `PluginRegistry`, plugin health and metadata.
- Main consumers: kernel integrations and plugin-bearing modules.
- Key dependencies: core, runtime.
- Review status: reviewed.
- Findings found in that unit: contributes to `F-009`, `F-012`.
- Duplicates or overlaps found: plugin-specific health model; naming overlap with kernel plugin registry.
- Consolidation opportunities: keep public plugin API here and push kernel-only plumbing behind internal boundaries.
- Test gaps: boundary tests for public vs internal plugin APIs.
- Documentation gaps: relationship to kernel plugin layer is under-explained.
- Naming concerns: `PluginRegistry` name conflicts conceptually with kernel internals.
- Maintainability concerns: medium.

### ai-integration

- Name and path: `ai-integration` at `/Users/samujjwal/Development/ghatana/platform/java/ai-integration`
- Purpose: shared AI integration and client abstractions.
- Main exports and responsibilities: AI client and integration support.
- Main consumers: AI-enabled services.
- Key dependencies: http, core, runtime.
- Review status: sampled and structurally reviewed.
- Findings found in that unit: no material issue confirmed from this audit.
- Duplicates or overlaps found: none confirmed in reviewed surface.
- Consolidation opportunities: keep provider adapters centralized here rather than in services.
- Test gaps: maintain provider contract tests.
- Documentation gaps: module README would help consumers choose supported entry points.
- Naming concerns: none material.
- Maintainability concerns: medium due to likely provider growth.
- Brief statement if no material issue was found: no material issue confirmed in this review.

### governance

- Name and path: `governance` at `/Users/samujjwal/Development/ghatana/platform/java/governance`
- Purpose: shared governance and policy abstractions.
- Main exports and responsibilities: governance contracts, policy models.
- Main consumers: policy-aware modules.
- Key dependencies: core.
- Review status: sampled and structurally reviewed.
- Findings found in that unit: no material issue confirmed from this audit.
- Duplicates or overlaps found: none confirmed.
- Consolidation opportunities: keep policy contracts centralized here.
- Test gaps: maintain contract tests for policy evaluation semantics.
- Documentation gaps: short package overview recommended.
- Naming concerns: none material.
- Maintainability concerns: low.
- Brief statement if no material issue was found: no material issue confirmed in this review.

### security

- Name and path: `security` at `/Users/samujjwal/Development/ghatana/platform/java/security`
- Purpose: shared security contracts and implementations.
- Main exports and responsibilities: auth providers, JWT generation/verification, security ports.
- Main consumers: auth-facing services and shared security integrations.
- Key dependencies: core, config, runtime.
- Review status: reviewed.
- Findings found in that unit: contributes to `F-006`.
- Duplicates or overlaps found: port and implementation are both public, but consumer guidance is too weak.
- Consolidation opportunities: tighten composition boundary around the implementation package.
- Test gaps: add architectural tests that services import ports, not implementations.
- Documentation gaps: port-first usage guidance is missing.
- Naming concerns: none material.
- Maintainability concerns: medium because improper usage is already present in consumers.

### agent-core

- Name and path: `agent-core` at `/Users/samujjwal/Development/ghatana/platform/java/agent-core`
- Purpose: shared agent framework.
- Main exports and responsibilities: typed agent contracts, lifecycle, runtime support.
- Main consumers: agent implementations and AI-related components.
- Key dependencies: core, runtime.
- Review status: reviewed structurally.
- Findings found in that unit: contributes to `F-009`.
- Duplicates or overlaps found: agent-specific `HealthStatus` diverges from other platform health types.
- Consolidation opportunities: map agent health to a canonical shared health contract while keeping agent lifecycle-specific states separate.
- Test gaps: add mapping tests if health is standardized.
- Documentation gaps: clarify how agent health should integrate with platform health reporting.
- Naming concerns: none material.
- Maintainability concerns: medium due to module size.

### connectors

- Name and path: `connectors` at `/Users/samujjwal/Development/ghatana/platform/java/connectors`
- Purpose: shared connector abstractions.
- Main exports and responsibilities: connector models and adapters.
- Main consumers: integration-bearing modules.
- Key dependencies: core.
- Review status: sampled and structurally reviewed.
- Findings found in that unit: no material issue confirmed from this audit.
- Duplicates or overlaps found: none confirmed.
- Consolidation opportunities: keep connectors centralized here rather than service-local wrappers.
- Test gaps: maintain integration contract tests.
- Documentation gaps: package overview recommended.
- Naming concerns: none material.
- Maintainability concerns: low.
- Brief statement if no material issue was found: no material issue confirmed in this review.

### audit

- Name and path: `audit` at `/Users/samujjwal/Development/ghatana/platform/java/audit`
- Purpose: shared audit event contracts and implementations.
- Main exports and responsibilities: `AuditBusPort`, `AuditEvent`, `AuditService`, `AuditQueryService`, in-memory and JPA-backed implementations.
- Main consumers: services needing audit recording/querying.
- Key dependencies: core, JPA, ActiveJ promises.
- Review status: reviewed.
- Findings found in that unit: `F-001`.
- Duplicates or overlaps found: the module is otherwise well-consolidated; `AuditBusPort` already reflects prior deduplication work.
- Consolidation opportunities: keep audit ownership centralized here and remove ad hoc audit ports elsewhere.
- Test gaps: JPA integration coverage is missing.
- Documentation gaps: the production implementation overstates readiness.
- Naming concerns: none material.
- Maintainability concerns: low after persistence model is fixed.

### kernel

- Name and path: `kernel` at `/Users/samujjwal/Development/ghatana/platform/java/kernel`
- Purpose: shared kernel, module registry, and contract infrastructure.
- Main exports and responsibilities: kernel registry, module contracts, plugin integration, health, contract validation.
- Main consumers: plugin-bearing modules and kernel integrations.
- Key dependencies: core, plugin, runtime.
- Review status: reviewed.
- Findings found in that unit: `F-004`, contributes to `F-005`, `F-009`, `F-012`.
- Duplicates or overlaps found: two contract validator families, internal/public plugin registry naming overlap, kernel-specific health model.
- Consolidation opportunities: collapse validator SPI, clarify public vs internal package boundaries.
- Test gaps: add architecture tests to enforce one public contract-validation entry point.
- Documentation gaps: package relationships are not explicit enough.
- Naming concerns: `contract` vs `contracts` is too subtle for public APIs.
- Maintainability concerns: high relative to other shared modules because core ownership is fragmented.

### distributed-cache

- Name and path: `distributed-cache` at `/Users/samujjwal/Development/ghatana/platform/java/distributed-cache`
- Purpose: shared distributed cache surface.
- Main exports and responsibilities: distributed cache contracts and support.
- Main consumers: cache-aware services/modules.
- Key dependencies: core, database.
- Review status: sampled and structurally reviewed.
- Findings found in that unit: no standalone material issue confirmed.
- Duplicates or overlaps found: likely adjacent to database cache ownership, but no concrete duplicate beyond `F-008`.
- Consolidation opportunities: align cache configuration ownership with `database`.
- Test gaps: keep cache contract behavior covered.
- Documentation gaps: module intent relative to `database` should be clearer.
- Naming concerns: none material.
- Maintainability concerns: low to medium.
- Brief statement if no material issue was found: no standalone material issue confirmed in this review.

### io shims

- Name and path: `io shims` at `/Users/samujjwal/Development/ghatana/io`
- Purpose: vendored ActiveJ compatibility classes.
- Main exports and responsibilities: `Stopwatch`, `AbstractPromise`, `SettablePromise`, `EventloopInspector`.
- Main consumers: ActiveJ-based runtime or test helpers.
- Key dependencies: ActiveJ APIs.
- Review status: reviewed.
- Findings found in that unit: `F-011`.
- Duplicates or overlaps found: in-repo duplicates of upstream library classes.
- Consolidation opportunities: formalize as compat module or remove in favor of dependency-provided classes.
- Test gaps: missing compatibility tests.
- Documentation gaps: no ownership or sync policy.
- Naming concerns: using upstream package names hides local ownership.
- Maintainability concerns: medium.

### ai-inference-service

- Name and path: `ai-inference-service` at `/Users/samujjwal/Development/ghatana/shared-services/ai-inference-service`
- Purpose: service consumer of shared platform libraries.
- Main exports and responsibilities: thin service bootstrap and AI inference usage.
- Main consumers: service runtime only.
- Key dependencies: ai-integration and runtime modules.
- Review status: sampled as consumer.
- Findings found in that unit: no shared-module-specific issue confirmed.
- Duplicates or overlaps found: none confirmed.
- Consolidation opportunities: continue pushing reusable logic into shared modules instead of local helpers.
- Test gaps: thin service tests are sparse.
- Documentation gaps: none material for shared-module audit.
- Naming concerns: none material.
- Maintainability concerns: low.
- Brief statement if no material issue was found: no material issue confirmed in this review.

### auth-gateway

- Name and path: `auth-gateway` at `/Users/samujjwal/Development/ghatana/shared-services/auth-gateway`
- Purpose: auth-oriented service consumer.
- Main exports and responsibilities: auth service and gateway launch wiring.
- Main consumers: service runtime only.
- Key dependencies: security, http, runtime.
- Review status: reviewed as consumer.
- Findings found in that unit: contributes to `F-006`.
- Duplicates or overlaps found: direct dependency on concrete JWT provider.
- Consolidation opportunities: depend on security port only.
- Test gaps: mockable port-based tests are missing.
- Documentation gaps: service wiring does not document the intended security abstraction boundary.
- Naming concerns: none material.
- Maintainability concerns: medium due to direct implementation coupling.

### feature-store-ingest

- Name and path: `feature-store-ingest` at `/Users/samujjwal/Development/ghatana/shared-services/feature-store-ingest`
- Purpose: thin service consumer for feature ingestion.
- Main exports and responsibilities: service-specific ingestion entry points.
- Main consumers: service runtime only.
- Key dependencies: shared platform modules.
- Review status: sampled as consumer.
- Findings found in that unit: no material issue confirmed from this audit.
- Duplicates or overlaps found: none confirmed.
- Consolidation opportunities: keep reusable ingestion helpers outside the service if they appear.
- Test gaps: service-local tests are sparse.
- Documentation gaps: none material for shared-module audit.
- Naming concerns: none material.
- Maintainability concerns: low.
- Brief statement if no material issue was found: no material issue confirmed in this review.

### user-profile-service

- Name and path: `user-profile-service` at `/Users/samujjwal/Development/ghatana/shared-services/user-profile-service`
- Purpose: user-profile service consumer.
- Main exports and responsibilities: profile service logic and bootstrap.
- Main consumers: service runtime only.
- Key dependencies: security and shared data modules.
- Review status: reviewed as consumer.
- Findings found in that unit: contributes to `F-006`.
- Duplicates or overlaps found: direct dependency on concrete JWT provider.
- Consolidation opportunities: depend on the security port.
- Test gaps: port-substitution tests are missing.
- Documentation gaps: none material beyond the abstraction-boundary issue.
- Naming concerns: none material.
- Maintainability concerns: medium due to implementation coupling.

## Contract and API Risks

- `core` does not clearly declare canonical contracts for result handling, validation results, and service endpoint modeling.
- `kernel` public API naming is too subtle. `contract` and `contracts` are not meaningfully distinguishable at call sites.
- `security` has the right abstraction in place, but the API boundary is not enforced strongly enough to prevent implementation imports.
- `audit` exposes a documented production implementation whose persistence contract is incomplete.
- `io` vendored source under upstream package names makes API provenance ambiguous.

## Duplicate Code and Logic

- Duplicate `Result` abstractions in `core`: one simple compatibility wrapper, one generic functional sealed interface.
- Duplicate `ConfigurationException` in `config` and `core`.
- Duplicate `ValidationResult` families in `core`, `config`, `kernel`, and nested `database` migration validation.
- Duplicate `ServiceEndpoint` records in `core`.
- Duplicate `RedisCacheConfig` classes in `database`.
- Duplicate `ContractValidator` concepts in `kernel`.
- Duplicate health-state modeling across `kernel`, `plugin`, `database`, and `agent-core`.

## Duplicate Effort and Overlapping Responsibilities

- Validation logic ownership is split across `core`, `config`, and `kernel` instead of reusing one typed result model.
- Health aggregation concerns are independently implemented per subsystem rather than standardized at the observability boundary.
- Plugin lifecycle ownership is split between `plugin` and `kernel` naming surfaces.
- Test infrastructure ownership is blurred because `testing` bundles containers, ActiveJ helpers, data builders, chaos tooling, and assertion libraries together.
- Source vendoring in `io` duplicates upstream maintenance effort without explicit governance.

## Sprawled Modules and Fragmented Ownership

- `platform/java/testing` is the clearest sprawled module. It currently acts as test framework, test dependency BOM, ActiveJ helper library, fixture system, HTTP test utility package, and chaos toolkit.
- `platform/java/kernel` is the clearest fragmented-ownership module. Contract validation, registry, health, plugin adjacency, and contract families are not cleanly separated between public and internal layers.
- `platform/java/core` has some public-surface sprawl through compatibility leftovers and parallel abstractions.

## Consolidation Opportunities

1. Consolidate result handling into one package and one canonical public type.
2. Consolidate validation results around `core` typed validation errors with optional warnings/context.
3. Consolidate kernel contract validation to one public SPI and one result model.
4. Consolidate cache configuration around one `RedisCacheConfig`.
5. Consolidate health reporting around one platform health envelope with module-specific detail payloads.
6. Consolidate security consumption on ports rather than implementation classes.
7. Consolidate testing utilities into cohesive subareas with narrower exported APIs.
8. Consolidate vendored ActiveJ code into either a documented compat module or remove it.

## Recommended Simplifications

- Replace `common` and `util` overlap in `core` with clearer domain-oriented packages.
- Remove or quarantine deprecated compatibility types from normal discovery paths.
- Make internal-only kernel types visibly internal by package naming.
- Prefer one public abstraction plus adapters over many public near-equivalents.
- Turn the `testing` module into a supported toolkit instead of a transitive dependency bucket.

## Naming and Documentation Issues

- `contract` vs `contracts` in `kernel` is too ambiguous for public APIs.
- `core/common/Result` vs `core/util/Result` and two `ServiceEndpoint` records are naming-level red flags because the names communicate no canonical path.
- `testing` contains placeholder-style names such as `MyActiveJTest`, which read like transitional scaffolding rather than supported public APIs.
- `io` needs documentation explaining that vendored ActiveJ classes are local ownership, not just imported library source.
- Several modules need a short module README or `package-info.java` explaining purpose, primary consumers, and canonical entry points.

## Dead Code and Redundant Abstractions

- One of the two `ServiceEndpoint` records is almost certainly redundant based on current in-repo usage.
- The deprecated config `ConfigurationException` is redundant once consumers move to `core`.
- One of the `RedisCacheConfig` classes appears legacy and should be retired.
- The simpler `core/common/Result` is a likely compatibility leftover if the richer generic `Result` is meant to be the future direction.

## Performance Concerns

- No major algorithmic performance defect was confirmed in the shared modules reviewed.
- The main performance-related architectural risk is duplication: health, validation, and result adapters will add repeated translation and maintenance overhead if not consolidated.
- `JpaAuditService` should not be considered performance-valid until the persistence model is fixed and integration-tested.

## Missing Test Coverage

- No integration test validates `JpaAuditService` against a real JPA provider.
- No architecture test prevents services from importing `security.jwt` implementation classes.
- No architecture test prevents new imports of deprecated exception or duplicate endpoint/result types.
- No cross-module tests validate a canonical health or validation contract because those contracts are not yet centralized.
- `io` vendored classes have no visible compatibility guard tests in-scope.

## Full Remediation Plan

### Phase 1: Safety and contract correctness

1. Fix `audit` persistence by introducing a mapped entity or removing `JpaAuditService` from the supported public API until it is valid.
2. Refactor `auth-gateway` and `user-profile-service` to depend on `security.port.JwtTokenProvider`.
3. Add architecture tests:
   - ban imports of `com.ghatana.platform.security.jwt` outside `security`
   - ban imports of deprecated config exception
   - ban new uses of non-canonical duplicate types once selected

### Phase 2: Canonical shared contracts

1. Choose one canonical `Result` type and deprecate the other.
2. Choose one canonical `ValidationResult` type centered in `core`.
3. Choose one canonical public `ContractValidator` SPI in `kernel`.
4. Choose one canonical `RedisCacheConfig`.

### Phase 3: Ownership cleanup

1. Define a canonical platform health envelope and add adapters from plugin, kernel, database, and agent health models.
2. Rename or relocate kernel-internal registry APIs so public ownership boundaries are obvious.
3. Add package or module READMEs for `core`, `kernel`, `security`, `testing`, and `io`.

### Phase 4: Surface reduction

1. Split or narrow `testing` APIs.
2. Remove redundant endpoint, exception, and config types after migration.
3. Resolve whether `io` vendored ActiveJ classes remain, move, or disappear.

## All Unresolved Findings By Severity

### critical

- `F-001` `platform/java/audit`: JPA audit implementation persists a non-entity `AuditEvent`.

### high

- `F-002` `platform/java/core`: duplicate public `Result` abstractions.
- `F-003` `platform/java/config`: deprecated duplicate `ConfigurationException` still present.
- `F-004` `platform/java/kernel`: duplicate `ContractValidator` APIs.
- `F-005` `platform/java/core`, `config`, `kernel`: fragmented `ValidationResult` contracts.
- `F-006` `shared-services/auth-gateway`, `user-profile-service`, `platform/java/security`: services depend on concrete JWT implementation instead of port.

### medium

- `F-007` `platform/java/core`: duplicate `ServiceEndpoint` records.
- `F-008` `platform/java/database`: duplicate `RedisCacheConfig`.
- `F-009` `platform/java/kernel`, `plugin`, `database`, `agent-core`: fragmented `HealthStatus` ownership.
- `F-010` `platform/java/testing`: oversized public API and dependency surface.
- `F-011` `io`: vendored ActiveJ classes lack explicit ownership and upgrade policy.

### low

- `F-012` `platform/java/plugin`, `kernel`: overlapping plugin registry ownership/naming.

## All Unresolved Findings By Module

### platform/contracts

- No unresolved material issue confirmed.

### platform/java/core

- `F-002`
- `F-005`
- `F-007`

### platform/java/config

- `F-003`
- `F-005`

### platform/java/database

- `F-008`
- `F-009`

### platform/java/audit

- `F-001`

### platform/java/kernel

- `F-004`
- `F-005`
- `F-009`
- `F-012`

### platform/java/plugin

- `F-009`
- `F-012`

### platform/java/security

- `F-006`

### platform/java/testing

- `F-010`

### platform/java/agent-core

- `F-009`

### io

- `F-011`

### shared-services/auth-gateway

- `F-006`

### shared-services/user-profile-service

- `F-006`

### platform/java/domain

- No unresolved material issue confirmed.

### platform/java/http

- No unresolved material issue confirmed.

### platform/java/observability

- No standalone unresolved material issue confirmed beyond shared health ownership.

### platform/java/runtime

- No unresolved material issue confirmed.

### platform/java/workflow

- No unresolved material issue confirmed.

### platform/java/ai-integration

- No unresolved material issue confirmed.

### platform/java/governance

- No unresolved material issue confirmed.

### platform/java/connectors

- No unresolved material issue confirmed.

### platform/java/distributed-cache

- No standalone unresolved material issue confirmed.

### shared-services/ai-inference-service

- No unresolved material issue confirmed.

### shared-services/feature-store-ingest

- No unresolved material issue confirmed.

## Assumptions and Limitations

- This was a static audit of source, manifests, imports, and tests. I did not run the full Gradle build, integration tests, or service boot flows.
- Severity reflects likely platform risk based on source evidence, not observed production incidents.
- Some modules were reviewed structurally rather than exhaustively line-by-line due to scope size; findings focus on confirmed issues in shared contracts and consumer coupling.
- `io` was treated as in-scope because it contains Java shared source under the repository root, even though it is not declared as a Gradle module.
- Consumer impact was assessed from in-repo imports and obvious wiring, not from external repositories that may also depend on these modules.

Overall assessment of shared module health: the shared Java layer has solid foundations but needs targeted consolidation before it can be considered a clean, low-surprise platform surface. The remediation priority should be: fix the broken audit persistence path, enforce port-based security consumption, collapse duplicate contract/result/validation models, then reduce module sprawl in `testing`, `kernel`, and the vendored `io` area.

Complete prioritized remediation plan: execute Phase 1 and Phase 2 first, because they remove immediate correctness and contract-selection risks; use Phase 3 to clarify ownership and documentation; finish with Phase 4 surface reduction once migrations are in place.

Consolidation roadmap: centralize correctness-sensitive contracts in `core` and `security`, centralize kernel public validation in one namespace, centralize health reporting in one platform envelope, and aggressively retire duplicate compatibility types after adding migration adapters and architectural guard tests.

All unresolved issues grouped by severity and module: see `## All Unresolved Findings By Severity` and `## All Unresolved Findings By Module`.

Assumptions and limitations: see `## Assumptions and Limitations`.
