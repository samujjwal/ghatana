# Shared-Services & Products Audit Report

> **Date:** 2026-02-22  
> **Scope:** `shared-services/` and `products/` directories  
> **Focus:** Platform library reuse, code duplication, architectural compliance

---

## Part 1: shared-services/

### 1.1 ai-inference-service ✅ GOOD

**Dependencies:** Correctly depends on `platform:java:ai-integration`, `platform:java:http`, `platform:java:observability`, `platform:java:core`, `platform:java:database`.

**Source files (3):**
- `AIInferenceHttpAdapter.java` — Uses platform `RoutingServlet`, `ResponseBuilder`, `MetricsCollector`, `JsonUtils`. ✅
- `AIInferenceServiceLauncher.java` — Uses `MetricsCollectorFactory.create()`, platform types. ✅
- `LLMGatewayServiceTest.java` — Extends `EventloopTestBase`, uses `NoopMetricsCollector`. ✅

**Findings:** No issues. This is a model service that properly delegates to platform libs.

---

### 1.2 ai-registry ✅ GOOD

**Dependencies:** Correctly depends on `platform:java:ai-integration`, `platform:java:http`, `platform:java:observability`, `platform:java:core`.

**Source files (1):**
- `AiRegistryServiceLauncher.java` — Uses `HttpServerBuilder`, `ResponseBuilder`, `MetricsCollectorFactory`. ✅

**Findings:** No issues. Clean platform integration.

---

### 1.3 auth-gateway ✅ GOOD

**Dependencies:** Correctly depends on `platform:java:http`, `platform:java:observability`, `platform:java:core`, `platform:java:security`.

**Source files (3):**
- `AuthGatewayLauncher.java` — Uses platform `JwtTokenProvider`, `UserPrincipal`. ✅
- `RateLimiter.java` — Service-specific rate limiter (not duplicating platform). ✅
- `TenantExtractor.java` — Uses platform `JwtTokenProvider`, `UserPrincipal`. ✅

**Findings:** `RateLimiter` and `TenantExtractor` could potentially be promoted to `platform:java:security` or `platform:java:http` since they are common cross-cutting concerns. Currently acceptable as service-specific, but worth considering if other products re-implement them (see yappc findings below).

---

### 1.4 auth-service ⚠️ MINOR ISSUES

**Dependencies:** Correctly depends on `platform:java:security`, `platform:java:config`, `platform:java:http`, `platform:java:database`.

**Source files (1):**
- `AuthService.java` — Uses platform `OAuth2Config`, `OAuth2Provider`, `TokenIntrospector`, `OidcSessionManager`. ✅

**Findings:**
- ⚠️ **Missing `platform:java:testing` test dependency** — No tests exist for this service.
- ⚠️ **Missing `platform:java:observability`** — No metrics instrumentation.

---

### 1.5 feature-store-ingest ✅ GOOD

**Dependencies:** Correctly depends on `platform:java:ai-integration`, `platform:java:event-cloud`, `platform:java:observability`, `platform:java:core`.

**Source files (1):**
- `FeatureStoreIngestLauncher.java` — Uses platform types (`Identifier`, `TenantId`), `MetricsCollectorFactory`. ✅

**Findings:** Stub implementation (mock ingestion mode). No duplication.

---

## Part 2: Products Audit

---

### 2.1 products/yappc/ ❌ CRITICAL — Multiple Duplications

#### 2.1.1 Hardcoded Dependency Versions
**File:** `products/yappc/platform/build.gradle.kts`  
Hardcodes 15+ dependency versions instead of using `libs.versions.toml`:
- `jackson-databind:2.17.0`, `jackson-datatype-jsr310:2.17.0`
- `graphql-java:21.3`
- `HikariCP:5.1.0`, `postgresql:42.7.2`, `jakarta.persistence-api:3.1.0`
- `langchain4j:0.25.0` (conflicting with data-cloud's `0.27.0` and tutorputor's `0.34.0`)
- `javalin:5.6.3` ← **Architecture violation**: Should use ActiveJ HTTP, not Javalin
- `slf4j-api:2.0.9`, `junit-jupiter:5.10.0`, `mockito-core:5.8.0`

#### 2.1.2 Duplicate Platform Implementations in yappc/platform/
**Path:** `products/yappc/platform/activej/activej-runtime/src/main/java/com/ghatana/core/activej/`

The entire `yappc/platform/activej/activej-runtime` subtree reimplements platform runtime concerns:
- `AsyncBridge.java` — Duplicates `platform:java:runtime`
- `EventloopManager.java` — Duplicates `platform:java:runtime`
- `ServiceLauncher.java` — Duplicates `platform:java:runtime`
- `UnifiedApplicationLauncher.java` — Should use platform launcher abstractions
- `PromiseCompat.java`, `PromiseUtils.java` — Duplicates `platform:java:core` utilities
- `EventloopTestExtension.java`, `EventloopTestRunner.java` — Duplicates `platform:java:testing`

#### 2.1.3 Duplicate Utility Classes
| File | Duplicates |
|------|-----------|
| `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/common/JsonUtils.java` | Wraps `platform.core.util.JsonUtils` — unnecessary wrapper, adds HTTP-specific body parsing that should be in platform:java:http |
| `products/yappc/core/lifecycle/src/main/java/com/ghatana/yappc/common/JsonMapper.java` | Another wrapper around `platform.core.util.JsonUtils` with custom settings |
| `products/yappc/core/refactorer/refactorer-languages/src/main/java/com/ghatana/refactorer/languages/tsjs/util/JsonUtils.java` | Third duplicate JsonUtils |
| `products/yappc/core/refactorer/refactorer-api/src/main/java/com/ghatana/observability/MetricsCollectorFactory.java` | Reimplements `platform:java:observability` `MetricsCollectorFactory` |
| `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/common/TenantContextExtractor.java` | Duplicates `shared-services/auth-gateway` `TenantExtractor` |
| `products/yappc/core/refactorer/refactorer-api/src/main/java/com/ghatana/refactorer/server/auth/TenantContextStorage.java` | Another tenant context implementation |
| `products/yappc/core/refactorer/refactorer-api/src/main/java/com/ghatana/refactorer/server/auth/TenantContextFilter.java` | Duplicate tenant middleware |
| `products/yappc/core/refactorer/refactorer-api/src/main/java/com/ghatana/refactorer/server/auth/JwtAuthFilter.java` | Reimplements JWT auth filter — should use `platform:java:security` |
| `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/config/DataSourceModule.java` | Reimplements HikariCP DataSource setup — should use `platform:java:database` |

#### 2.1.4 Multiple HTTP Server Implementations
- `products/yappc/core/ai-requirements/api/src/main/java/com/ghatana/requirements/api/http/RequirementsHttpServer.java`
- `products/yappc/core/lifecycle/src/main/java/com/ghatana/yappc/api/YappcHttpServer.java` (extends `HttpServerLauncher`)
- `products/yappc/core/framework/framework-core/src/main/java/com/ghatana/yappc/framework/core/http/FrameworkHttpServer.java`
- `products/yappc/core/refactorer/refactorer-api/src/main/java/com/ghatana/refactorer/server/RefactorerHttpServer.java`
- `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/ApiApplication.java` (extends `HttpServerLauncher`)

Each implements its own server setup patterns rather than using a common platform bootstrap.

#### 2.1.5 Architecture Violation — Javalin Dependency
**File:** `products/yappc/platform/build.gradle.kts` line 63  
```
implementation("io.javalin:javalin:5.6.3")
```
Per copilot-instructions: "Core Domain" MUST use **ActiveJ**. Javalin is forbidden.

---

### 2.2 products/aep/ ⚠️ MODERATE ISSUES

#### 2.2.1 Correct Platform Usage
**File:** `products/aep/platform/build.gradle.kts`  
Properly depends on 10+ platform modules including `:platform:java:core`, `:platform:java:domain`, `:platform:java:workflow`, etc. ✅

#### 2.2.2 Duplicate MetricsCollector
**File:** `products/aep/platform/src/main/java/com/ghatana/aep/expertinterface/analytics/MetricsCollector.java`  
This is a **350-line** class that reimplements metrics collection using Micrometer directly instead of using `platform:java:observability`'s `MetricsCollector`. Different package (`com.ghatana.aep.expertinterface.analytics`) but same purpose.

#### 2.2.3 NoOpMetricsCollector Duplication
**File:** `products/aep/platform/src/main/java/com/ghatana/aep/analytics/AnalyticsEngine.java` (line 610)  
Contains `private static class NoOpMetricsCollector implements MetricsCollector` — platform already provides `NoopMetricsCollector`.

#### 2.2.4 Duplicate ConnectionPoolManager
**File:** `products/aep/platform/src/main/java/com/ghatana/aep/platform/database/ConnectionPoolManager.java`  
307-line class reimplements HikariCP connection pool management. This should use `platform:java:database`.

#### 2.2.5 Hardcoded Dependency Versions
**File:** `products/aep/platform/build.gradle.kts`
- `kafka-clients:3.6.0`, `amqp-client:5.20.0`, `sqs:2.20.0`, `s3:2.20.0`
- `langchain4j:0.27.0`, `langchain4j-open-ai:0.27.0`
- `commons-math3:3.6.1`, `commons-lang3:3.14.0`

Should use `libs.versions.toml` aliases.

#### 2.2.6 ScalingMetricsCollector Bypass
**File:** `products/aep/platform/src/main/java/com/ghatana/aep/scaling/integration/ScalingMetricsCollector.java`  
548-line class implementing custom metrics with raw atomics and `ScheduledExecutorService` instead of using Micrometer via `platform:java:observability`.

---

### 2.3 products/data-cloud/ ✅ MOSTLY GOOD

#### 2.3.1 Correct Platform Usage
**File:** `products/data-cloud/platform/build.gradle.kts`  
Properly depends on `:platform:java:core`, `:platform:java:domain`, `:platform:java:audit`, `:platform:java:database`, `:platform:java:http`, `:platform:java:observability`, `:platform:java:security`, `:platform:java:config`, `:platform:java:plugin`. ✅

#### 2.3.2 Minor Issues
- **Duplicate JsonMapper:** `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/knowledgegraph/api/JsonMapper.java` — a local `JsonMapper` utility class.
- **Hardcoded versions** in build.gradle.kts for domain-specific dependencies (Iceberg, TinkerPop, etc.) — these are product-specific so somewhat acceptable, but Kafka (`3.6.0`) duplicates AEP's version and both should reference catalog.
- **LangChain4j version `0.27.0`** conflicts with yappc (`0.25.0`) and tutorputor (`0.34.0`).

---

### 2.4 products/flashit/ ⚠️ MODERATE ISSUES

#### 2.4.1 Missing Platform Dependencies
**File:** `products/flashit/backend/agent/build.gradle.kts`  
Does **NOT** depend on any `platform:java:*` modules. Uses only raw ActiveJ libs directly.

Missing dependencies:
- ❌ No `platform:java:core` — no access to shared types, `JsonUtils`
- ❌ No `platform:java:http` — builds its own HTTP routing
- ❌ No `platform:java:observability` — no metrics integration
- ❌ No `platform:java:security` — no auth integration
- ❌ No `platform:java:testing` — tests don't use `EventloopTestBase`

#### 2.4.2 Custom HTTP Router
**File:** `products/flashit/backend/agent/src/main/java/com/ghatana/flashit/agent/http/AgentHttpRouter.java`  
Implements its own HTTP routing instead of using `platform:java:http`'s `RoutingServlet`.

#### 2.4.3 Direct OpenAI Dependency
**File:** `products/flashit/backend/agent/build.gradle.kts`  
Uses `com.openai:openai-java` directly instead of going through `platform:java:ai-integration`. This bypasses the LLM gateway pattern.

#### 2.4.4 Platform Module is Hollow
**File:** `products/flashit/platform/build.gradle.kts`  
Only depends on `platform:java:core`. No Java source files exist in the platform directory. The actual business logic in `flashit/backend/agent` completely bypasses this module.

---

### 2.5 products/dcmaar/ ✅ GOOD

**Source files in `libs/java/` (4):**
- `GuardianAgentAdapter.java` — Adapter for AI platform integration
- `GuardianAgentAdapterIntegrationTest.java` — Test
- `GuardianThreatServiceLauncher.java` — Service launcher
- `GuardianThreatServiceLauncherTest.java` — Test

Small Java footprint. Primary codebase is TypeScript/React Native. No platform library duplication detected.

---

### 2.6 products/tutorputor/ ⚠️ MODERATE ISSUES

#### 2.6.1 Duplicate AI Gateway Interface
**File:** `products/tutorputor/services/tutorputor-ai-agents/src/main/java/com/ghatana/ai/gateway/AIGateway.java`  
Defines its own `AIGateway` interface (stub) instead of using `platform:java:ai-integration`'s `LLMGateway`.

#### 2.6.2 Duplicate LLM Provider Interface
**File:** `products/tutorputor/services/tutorputor-ai-agents/src/main/java/com/ghatana/patternlearning/llm/LlmProvider.java`  
Defines its own `LlmProvider` interface using **`CompletableFuture`** instead of ActiveJ `Promise`. This violates the concurrency rules: *"NEVER use `CompletableFuture` mixed with ActiveJ."*

#### 2.6.3 Conflicting LangChain4j Versions
**File:** `products/tutorputor/services/tutorputor-ai-agents/build.gradle.kts`  
Uses `langchain4j-open-ai:0.34.0` and `langchain4j-ollama:0.34.0` — differs from AEP (`0.27.0`), data-cloud (`0.27.0`), and yappc (`0.25.0`).

---

### 2.7 products/software-org/ ✅ MOSTLY GOOD

**File:** `products/software-org/build.gradle.kts`  
Properly depends on `platform:java:domain`, `platform:java:http`, `platform:java:testing`. ✅

No duplicate utility classes found. Department modules are properly scoped business logic. Clean architecture.

---

### 2.8 products/virtual-org/ ⚠️ MODERATE ISSUES

#### 2.8.1 Correct Platform Usage
**File:** `products/virtual-org/build.gradle.kts`  
Properly depends on `platform:java:database`, `platform:java:ai-integration`, `platform:java:observability`, `platform:java:core`, `platform:java:workflow`, `platform:java:domain`. ✅

#### 2.8.2 Duplicate LLM Client Implementations
**Files:**
- `products/virtual-org/engine/service/src/main/java/com/ghatana/virtualorg/llm/LLMClient.java` — Custom LLM interface
- `products/virtual-org/engine/service/src/main/java/com/ghatana/virtualorg/llm/impl/OpenAILLMClient.java` — 279-line OpenAI implementation using LangChain4j directly
- `products/virtual-org/engine/service/src/main/java/com/ghatana/virtualorg/llm/impl/AnthropicLLMClient.java` — Anthropic implementation using LangChain4j directly

These should use `platform:java:ai-integration`'s `LLMGateway` / `CompletionService` / `EmbeddingService` instead of reimplementing LLM clients.

#### 2.8.3 Mock MetricsCollector in Tests
**Files:**
- `products/virtual-org/modules/framework/src/test/java/.../InMemoryApprovalGatewayTest.java` — `MockMetricsCollector`
- `products/virtual-org/modules/framework/src/test/java/.../DefaultAgentRuntimeTest.java` — `MockMetricsCollector`

Should use `NoopMetricsCollector` from `platform:java:observability` instead of reimplementing mocks.

---

### 2.9 products/security-gateway/ ⚠️ SIGNIFICANT OVERLAP

#### 2.9.1 Correct Platform Usage
**File:** `products/security-gateway/platform/java/build.gradle.kts`  
Depends on 9 platform modules including `:platform:java:security`, `:platform:java:governance`, `:platform:java:audit`. ✅

#### 2.9.2 Duplicate JwtTokenProvider
**File:** `products/security-gateway/platform/java/src/main/java/com/ghatana/security/jwt/JwtTokenProvider.java`  
195-line JWT provider using Nimbus JOSE — potentially duplicates `platform:java:security`'s `JwtTokenProvider`. Also has:
- `products/security-gateway/platform/java/src/main/java/com/ghatana/auth/service/impl/JwtTokenProviderImpl.java` — Another implementation

**Two JWT providers in same product** — architecture confusion.

#### 2.9.3 Large Auth/Security Stack
The product contains 40+ Java source files implementing a full auth/security stack:
- `AuthenticationService`, `AuthorizationService`, `JwtKeyRotationService`, `PolicyEvaluator`, `RbacPolicyEvaluator`
- `SecurityContext`, `SecurityContextHolder`, `JwtAuthenticationFilter`
- `InMemoryTokenStore`, `RedisTokenStore`, `JpaTokenRepository`
- `PasswordHasher`, `EncryptedStorageService`, `TlsConfigurationService`

**Question:** Is this effectively the same responsibility as `platform:java:security` + `shared-services/auth-service` + `shared-services/auth-gateway`? There appears to be significant functional overlap between these three modules.

---

### 2.10 products/audio-video/ ❌ CRITICAL — Fully Isolated

#### 2.10.1 No Platform Dependencies
**File:** `products/audio-video/modules/speech/stt-service/build.gradle.kts`  
Uses **zero** `project()` dependencies. All dependencies are raw Maven coordinates with hardcoded versions. This product is completely disconnected from the platform.

#### 2.10.2 Duplicate Platform Stubs
The STT service creates `com.ghatana.platform.*` stubs to fake platform integration:
- `products/audio-video/modules/speech/stt-service/src/main/java/com/ghatana/platform/observability/MetricsCollector.java` — **Fake stub** for `platform:java:observability`
- `products/audio-video/modules/speech/stt-service/src/main/java/com/ghatana/platform/auth/JwtTokenProvider.java` — **Fake stub** for `platform:java:security` that always returns "anonymous"

These stubs use the **same package names** as platform classes but provide dummy implementations. This is extremely dangerous — if the module ever gets the real platform on the classpath, there will be class conflicts.

#### 2.10.3 Yet Another Custom MetricsCollector
**File:** `products/audio-video/modules/speech/stt-service/src/main/java/com/ghatana/stt/core/metrics/MetricsCollector.java`  
201-line custom metrics implementation with raw `AtomicLong`/`AtomicInteger` counters instead of Micrometer.

---

## Summary: Cross-Cutting Issues

### Issue 1: LangChain4j Version Sprawl
| Product | Version |
|---------|---------|
| yappc | 0.25.0 |
| aep | 0.27.0 |
| data-cloud | 0.27.0 |
| tutorputor | 0.34.0 |
| virtual-org | (via engine, version unknown) |

**Fix:** Centralize in `libs.versions.toml`, all products should use `platform:java:ai-integration` which wraps LangChain4j.

### Issue 2: Hardcoded Versions vs Version Catalog
Products using raw `implementation("group:artifact:version")` instead of `libs.*` catalog aliases:
- **yappc/platform** — 15 hardcoded versions
- **aep/platform** — 8 hardcoded versions
- **audio-video** — All dependencies hardcoded
- **data-cloud/platform** — 10+ hardcoded versions (domain-specific ones acceptable)

### Issue 3: MetricsCollector Proliferation
| Location | Type |
|----------|------|
| `platform:java:observability` | **CANONICAL** `MetricsCollector` interface |
| `aep/expertinterface/analytics/MetricsCollector` | Duplicate class (350 lines) |
| `aep/analytics/AnalyticsEngine.NoOpMetricsCollector` | Duplicate no-op |
| `aep/scaling/ScalingMetricsCollector` | Duplicate (548 lines, raw atomics) |
| `audio-video/stt-service/platform/observability/MetricsCollector` | Fake stub |
| `audio-video/stt-service/core/metrics/MetricsCollector` | Duplicate (201 lines) |
| `yappc/refactorer/observability/MetricsCollectorFactory` | Duplicate factory |
| `virtual-org (tests)` | 2x `MockMetricsCollector` inner classes |

### Issue 4: JWT/Auth Duplication
| Location | Implementation |
|----------|---------------|
| `platform:java:security` | **CANONICAL** `JwtTokenProvider` |
| `shared-services/auth-gateway` | Uses platform ✅ |
| `shared-services/auth-service` | Uses platform ✅ |
| `security-gateway/jwt/JwtTokenProvider` | **Duplicate** (195 lines) |
| `security-gateway/auth/impl/JwtTokenProviderImpl` | **Second duplicate** |
| `audio-video/stt-service/platform/auth/JwtTokenProvider` | **Fake stub** |
| `yappc/refactorer/auth/JwtAuthFilter` | **Duplicate filter** |

### Issue 5: TenantContext/Extractor Duplication
| Location | Implementation |
|----------|---------------|
| `shared-services/auth-gateway/TenantExtractor` | Service-level |
| `yappc/api/common/TenantContextExtractor` | Duplicate |
| `yappc/refactorer/auth/TenantContextStorage` | Duplicate |
| `yappc/refactorer/auth/TenantContextFilter` | Duplicate |
| `aep/ingress/TenantContextPropagator` | Stub |

---

## Severity Rankings

| Severity | Product | Key Issues |
|----------|---------|-----------|
| ❌ CRITICAL | **audio-video** | Zero platform deps, fake platform stubs with conflicting package names |
| ❌ CRITICAL | **yappc** | 15+ hardcoded versions, Javalin violation, 5 duplicate HTTP servers, 3 JsonUtils, 3 tenant extractors, duplicate metrics factory, duplicate eventloop/runtime |
| ⚠️ SIGNIFICANT | **security-gateway** | Two JwtTokenProvider implementations, massive auth stack overlapping platform:java:security |
| ⚠️ MODERATE | **aep** | 3 duplicate MetricsCollectors, ConnectionPoolManager duplication, hardcoded versions |
| ⚠️ MODERATE | **virtual-org** | 2 duplicate LLM clients (should use platform ai-integration) |
| ⚠️ MODERATE | **flashit** | Backend agent has zero platform deps, custom HTTP routing, direct OpenAI dep |
| ⚠️ MODERATE | **tutorputor** | Duplicate AI interfaces, CompletableFuture violation, LangChain4j version mismatch |
| ✅ MINOR | **data-cloud** | 1 JsonMapper duplicate, some hardcoded versions |
| ✅ GOOD | **software-org** | Clean platform usage |
| ✅ GOOD | **dcmaar** | Minimal Java footprint, no duplication |
| ✅ GOOD | **shared-services** | All 5 services properly use platform libs |

---

## Recommended Actions

1. **Immediate:** Migrate `audio-video/stt-service` platform stubs to real platform dependencies (or at minimum rename packages to avoid `com.ghatana.platform.*` conflicts)
2. **Immediate:** Move all hardcoded dependency versions to `gradle/libs.versions.toml`
3. **Immediate:** Remove Javalin dependency from yappc
4. **Short-term:** Consolidate `TenantExtractor` / `TenantContextExtractor` into `platform:java:http` or `platform:java:security`
5. **Short-term:** Remove all duplicate `MetricsCollector` implementations; use platform's canonical one
6. **Short-term:** Add `platform:java:*` dependencies to `flashit/backend/agent`
7. **Medium-term:** Make `virtual-org` LLM clients use `platform:java:ai-integration`
8. **Medium-term:** Resolve `security-gateway` vs `platform:java:security` overlap — clarify which is canonical
9. **Medium-term:** Consolidate yappc's 5+ HTTP server classes into a shared bootstrap pattern
10. **Medium-term:** Unify LangChain4j version across all products via version catalog
