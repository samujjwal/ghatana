# App-Platform Code & Architecture Review Report

**Review Date:** 2026-03-15 (Updated: 2026-03-16)  
**Scope:**  
- **App-Platform Internal:** kernel (25 modules, 524 Java files), domain-packs (14 packs, 289 Java files), libs/typescript (2 packages), admin-portal (React), infra (K8s, Docker)  
- **Monorepo Cross-Reference:** platform/java (30 shared modules), shared-services (7 services), 12 products in `/products/`  
**Reviewer:** GitHub Copilot  

---

## Executive Summary

The app-platform codebase shows a well-reasoned architecture (Hexagonal/Ports-and-Adapters, ActiveJ async, event-sourcing) with strong patterns in the kernel. However, the analysis reveals **critical gaps** in test coverage, a significant number of **production stubs that are not yet replaced by real implementations**, **25 scattered ObjectMapper instances** without a shared singleton, **inconsistent DataSource typing**, and **several security concerns** in the API gateway JWT validation layer. Domain packs in particular have **zero unit tests** across all 14 packs.

**Monorepo-wide analysis** adds further findings: app-platform uses only **17 of 30 available platform:java:\* shared modules** (57%). The shared-services layer has **no integration with app-platform's audit, observability, or resilience modules**. Two patterns — `observability-sdk` (thin wrapper) and `resilience-patterns` (composition) — are the monorepo reference models for platform extension.

**Architectural Mandate Assessment** reveals four cross-cutting concerns:

1. **Event Processing → AEP:** Largely compliant. `dlq-management`, `corporate-actions`, and `regulatory-reporting` correctly use AEP. However, ~10 modules publish events via `EventBusPort` with no AEP-backed adapter wired, and 3 domain-pack services use untyped `Consumer<Object>` that bypasses AEP's schema registry.

2. **Data Handling → data-cloud: Critical violation.** `kernel/data-governance` (K-08) declares `data-cloud:platform` and `data-cloud:spi` dependencies but **all 7 service implementations use direct JDBC** (HikariDataSource, Connection, PreparedStatement) instead. PII masking, lineage tracking, data catalog, classification, retention, and GDPR erasure are all reimplemented from scratch in violation of the mandate.

3. **Workflow → shared: Full design complete.** `platform:java:workflow` already contains `DurableWorkflowEngine` — the platform already started this journey. A full 3-module unified design has been produced: `workflow` (enhanced core API), `workflow-runtime` (FSM engine extracted from app-platform), and `workflow-jdbc` (PostgreSQL state store). Both ephemeral pipelines and durable FSMs share the same `Workflow`/`WorkflowContext`/`WorkflowStep` types. AEP interop is achieved via the new `OPERATOR_PIPELINE` step kind. See [platform/java/workflow/docs/UNIFIED_WORKFLOW_PLATFORM_DESIGN.md](../../../../platform/java/workflow/docs/UNIFIED_WORKFLOW_PLATFORM_DESIGN.md).

4. **Security/Auth/Audit/Governance/Secrets/O11y → shared:** Mixed results. Security, secrets, and observability are compliant. **Critical audit gap:** 15 duplicated `AuditPort` inner interface definitions and 6 high-sensitivity operations (break-glass access, secret rotation, config changes, role grants) that emit no audit events — a compliance failure. `ai-governance` also lacks its `platform:java:governance` dependency.

---

## 1. Test Coverage Gaps

### 1.1 Domain Packs — Complete Absence of Tests

**Severity: CRITICAL**

All 14 domain packs have **zero test source files** in their `src/test` directories.

| Domain Pack | Main Files | Test Files |
|---|---|---|
| compliance | 23 | **0** |
| corporate-actions | 14 | **0** |
| ems | 30 | **0** |
| market-data | 24 | **0** |
| oms | 28 | **0** |
| pms | 13 | **0** |
| post-trade | 18 | **0** |
| pricing | 12 | **0** |
| reconciliation | 18 | **0** |
| reference-data | 28 | **0** |
| regulatory-reporting | 13 | **0** |
| risk-engine | 26 | **0** |
| sanctions | 26 | **0** |
| surveillance | 16 | **0** |

The only file with "Test" in its name under domain-packs is `risk-engine/src/main/.../StressTestingService.java` — a production service, not a test.

**Impact:** Core financials (OMS, PMS, risk-engine, reconciliation, sanctions screening) contain business logic with no automated verification. Correctness rests entirely on manual review.

### 1.2 Kernel Modules With Zero Tests

**Severity: HIGH**

15 of 25 kernel modules have no test files:

| Kernel Module | Main Files | Test Files |
|---|---|---|
| ai-governance | 15 | **0** |
| client-onboarding | 10 | **0** |
| data-governance | 14 | **0** |
| deployment-abstraction | 12 | **0** |
| dlq-management | 15 | **0** |
| incident-management | 12 | **0** |
| integration-testing | 30 | **0** (ironically — the test framework itself has no tests) |
| operator-workflows | 12 | **0** |
| pack-certification | 10 | **0** |
| platform-manifest | 8 | **0** |
| platform-sdk | 15 | **0** |
| plugin-runtime | 28 | **0** |
| regulator-portal | 10 | **0** |
| workflow-orchestration | 15 | **0** |

Notable: `integration-testing` (30 source files, 0 tests) is a module containing chaos-engineering and E2E test orchestrators that have no verification of their own correctness.

### 1.3 Kernel Modules With Critically Low Test Ratios

| Module | Main | Test | Ratio |
|---|---|---|---|
| api-gateway | 15 | 1 | 7% |
| iam | 40 | 2 | 5% |
| secrets-management | 15 | 1 | 7% |

IAM and secrets-management are among the most security-sensitive modules and have the lowest test coverage.

---

## 2. Stubs and Incomplete Implementations in Production Code

### 2.1 `SdkCoreAbstractionsService.resolveEndpointsBlocking()` — Hardcoded Stub JSON

**File:** `kernel/platform-sdk/src/main/java/com/ghatana/appplatform/sdk/SdkCoreAbstractionsService.java` (line 233-236)  
**Severity: HIGH**

```java
// Returning a stub JSON here for the framework skeleton.
return "{\"event_bus\":\"http://event-bus:8080\",\"config\":\"http://config-engine:8080\"," +
       "\"audit\":\"http://audit-trail:8080\",\"rules\":\"http://rules-engine:8080\"," +
       "\"auth\":\"http://iam:8080\"}";
```

This method is documented as "in production this resolves K-02 config keys" but the entire production path returns a hardcoded string. Any service using `SdkCoreAbstractionsService.createClientBundle()` receives endpoints pointing to non-configurable Docker service hostnames regardless of deployment environment. This breaks Kubernetes deployments with different service names, multi-region topologies, or any configuration override.

### 2.2 `CertificateLifecycleService.renew()` — No-op Stub

**File:** `kernel/secrets-management/src/main/java/com/ghatana/appplatform/secrets/certificate/CertificateLifecycleService.java` (line 75-87)  
**Severity: HIGH**

```java
// Stub: production implementation calls Vault PKI `pki/issue/{role}` endpoint
return null;
```

Certificate renewal only logs intent. No actual Vault PKI call, no ACME integration, no new certificate is issued. Automated renewal triggered when `daysUntilExpiry < 30` silently does nothing.

### 2.3 `HsmKeyOperationsProvider` — Dev-Stub Ships in Production Code

**File:** `kernel/secrets-management/src/main/java/com/ghatana/appplatform/secrets/hsm/HsmKeyOperationsProvider.java`  
**Severity: HIGH**

The class ships a software RSA fallback (`useHsm=false`) with no enforcement that production deployments set `useHsm=true`. A misconfigured deployment will silently use in-process software signing with key material in JVM heap — defeating the purpose of HSM.

### 2.4 `GeoIpResolver.InMemoryGeoIpResolver` — Always Returns Null

**File:** `kernel/iam/src/main/java/com/ghatana/appplatform/iam/security/GeoIpResolver.java`  
**Severity: MEDIUM**

```java
public Coordinates resolve(String ipAddress) {
    return null; // No-op GeoIP resolver stub
}
```

`LoginAnomalyDetector` depends on `GeoIpResolver` to detect impossible travel logins. No real MaxMind/ipinfo.io adapter is provided in the codebase. Any geoIP-based anomaly detection logic is inoperative.

### 2.5 `NepalNidAdapter` — Government NID API Mocked in All Environments

**File:** `kernel/iam/src/main/java/com/ghatana/appplatform/iam/adapter/NepalNidAdapter.java`  
**Severity: MEDIUM**

Documentation: _"Connects to Nepal government NID API (mocked in dev/test)."_ No conditional mock path is visible in the code; the mock URL is intended to be injected via K-02 config. No mock/stub implementation exists in the codebase for dev environments — the adapter either fails (wrong URL) or hits a real government endpoint unexpectedly.

### 2.6 `T3NetworkPluginRuntime` — "Intentionally Simple Stub"

**File:** `kernel/plugin-runtime/src/main/java/com/ghatana/appplatform/plugin/runtime/T3NetworkPluginRuntime.java`  
**Severity: MEDIUM**

Javadoc states: _"The stub is intentionally simple: it POSTs a JSON body and reads a JSON response."_ gRPC delegation (referenced in class description) is not implemented. Plugin invocations using gRPC protocol will fail since only HTTP is supported.

### 2.7 `AuditEvidencePdfGenerator` — Digital Signature Block Is a Placeholder

**File:** `kernel/audit-trail/src/main/java/com/ghatana/appplatform/audit/export/AuditEvidencePdfGenerator.java` (line 293)  
**Severity: MEDIUM**

```java
// Digital signature placeholder block
```

Generated audit evidence PDFs lack a cryptographic signature, undermining tamper-evidence claims for regulatory submissions.

---

## 3. Hard-Coded Values

### 3.1 Hardcoded Service URLs in `SdkCoreAbstractionsService`

*(See §2.1 above)*  
`http://event-bus:8080`, `http://config-engine:8080`, `http://audit-trail:8080`, `http://rules-engine:8080`, `http://iam:8080` — hardcoded into production logic.

### 3.2 SDK/Platform Version Constants Hardcoded at Compile-Time

**File:** `kernel/platform-sdk/src/main/java/com/ghatana/appplatform/sdk/SdkCoreAbstractionsService.java` (lines 155-156)  

```java
private static final String PLATFORM_VERSION = "1.0.0";
private static final String SDK_VERSION      = "1.0.0";
```

These are written to the `sdk_registrations` table during service startup. Version bumps require code changes rather than build-time injection (`build.properties`, Gradle `processResources`, or environment variables).

### 3.3 Hardcoded Tracer Version

**File:** `kernel/observability-sdk/src/main/java/com/ghatana/appplatform/observability/KernelTracingInstrumentation.java` (line 49)  
`GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE, "1.0.0")` — instrument version hardcoded.

### 3.4 FIX Protocol Timing Constants

**File:** `domain-packs/ems/src/main/java/com/ghatana/appplatform/ems/service/FixProtocolService.java`  
```java
private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
private static final int SESSION_TIMEOUT_SECONDS    = 60;
```
FIX session parameters must be negotiated per-counterparty. Hardcoding them prevents per-connection configuration, which is required by most exchange FIX specifications.

### 3.5 T3 Plugin Default Sidecar Port

**File:** `kernel/plugin-runtime/src/main/java/com/ghatana/appplatform/plugin/runtime/T3NetworkPluginRuntime.java`  
`DEFAULT_SIDECAR_PORT = 7070` — not read from plugin manifest or config.

### 3.6 Docker Compose / Dev Database Credentials

**File:** `docker-compose.yml` (lines 22, 42, 48)  
```yaml
POSTGRES_PASSWORD: app_pass
redis-server --requirepass redis_pass
```
Plaintext credentials in version-controlled docker-compose. While labelled as dev-only, there is no `.env` file convention, no `docker-compose.override.yml` separation, and no indication preventing these values from being promoted to staging.

**File:** `scripts/init-db.sql`  
```sql
CREATE ROLE app_user LOGIN PASSWORD 'app_pass';
```
Same credentials hardcoded in SQL init script.

---

## 4. Redundancy and ObjectMapper Proliferation

### 4.1 25 Separate `new ObjectMapper()` Instantiations

**Severity: MEDIUM**

`ObjectMapper` is expensive to instantiate and should be a shared singleton. 25 separate instances are created across the codebase — many with inconsistent module registration:

| Class | Module Registered |
|---|---|
| `NepseCdscAdapter` | _none_ (no JavaTimeModule) |
| `InstrumentService` | _none_ |
| `RuleCacheService` | _none_ |
| `OpaEvaluationService` | _none_ |
| `EventSchemaCodeGenerationService` | _none_ |
| `RedisConfigProjection` | _none_ |
| `ConfigChangeNotifier` | _none_ |
| `PostgresAggregateEventStore` | `JavaTimeModule` |
| `KafkaEventPublisher` | `JavaTimeModule` (duplicated — two constructors) |
| `EventReplayEngine` | _none_ |
| `HashChainService` | _none_ |
| ... | ... |

Classes without `JavaTimeModule` will serialize/deserialize `Instant`, `LocalDate`, `ZonedDateTime` as numeric timestamps or throw exceptions, causing subtle data corruption bugs across domain boundaries.

**`KafkaEventPublisher`** is particularly notable — it creates `new ObjectMapper().registerModule(new JavaTimeModule())` in _two separate constructors_, each producing a distinct mapper instance.

### 4.2 Duplicate Jedis/Redis Client Library Usage

Redis is accessed via Jedis (`redis.clients.jedis.JedisPool`) throughout: `RuleCacheService`, `RedisRateLimitStore`, `RedisConfigProjection`, `LoginAnomalyDetector`, `BreakGlassService`, `BruteForceGuard`, `SigningKeyRotator`, `AuthorizationService`, `RefreshTokenGrant`, `AuthorizationCodeGrant`. While consistent in choice of library, there is no shared `JedisPool` factory — each consumer constructs its own pool independently.

---

## 5. Inconsistency

### 5.1 `DataSource` Typing: Interface vs Concrete Class

**Severity: MEDIUM**

| Usage | Count |
|---|---|
| `javax.sql.DataSource` (interface — correct) | 99 files |
| `com.zaxxer.hikari.HikariDataSource` (concrete — leaks implementation) | 163 files |

Injecting `HikariDataSource` directly into services violates the Hexagonal Architecture principle the platform espouses: services should depend on the `DataSource` interface, not the Hikari concrete class. This makes it impossible to swap connection pool implementations (e.g., switching to PgBouncer transaction pooling or testing with H2) without modifying service constructors.

### 5.2 Async Model Mixing: ActiveJ `Promise` vs Raw `ScheduledExecutorService`

**Severity: MEDIUM**

The platform chooses ActiveJ's `Promise` and event-loop model for all async I/O. However, 15+ files create raw `ScheduledExecutorService` instances via `Executors.newSingleThreadScheduledExecutor()` outside of ActiveJ's lifecycle:

- `ems/FixProtocolService` — owns its own heartbeat scheduler
- `market-data/MarketDataFeedAdapterRegistry` — owns its own polling scheduler
- `sanctions/BatchReScreeningService` — owns its own re-screening scheduler
- `reference-data/RefDataFeedAdapterRegistry` — owns its own polling scheduler
- `kernel/dlq-management/BulkReplayService`
- `kernel/event-store/SagaTimeoutMonitor`
- `kernel/event-store/KafkaEventOutboxRelay`
- `kernel/secrets-management/SecretRotationScheduler`

**Consequence:** These background threads are not managed by the ActiveJ eventloop. They cannot be properly shut down on eventloop stop, they cannot participate in ActiveJ's graceful shutdown lifecycle, and they bypass the platform's structured concurrency model. Unhandled exceptions in these threads will silently die without surfacing to the platform's incident management.

### 5.3 Inconsistent Exception Wrapping

**Severity: MEDIUM**

475 `catch (Exception e)` usages across the codebase. The pattern varies:

- Many JDBC adapters catch `Exception` and rethrow as `new RuntimeException("Failed to ...", e)` — this is acceptable for infrastructure adapters.
- Some domain services (e.g., `compliance/LockInPeriodService`, `compliance/JurisdictionRuleRouterService`, `compliance/RestrictedListService`) catch `Exception` from rule engine calls and convert to domain-neutral outcomes, silently masking underlying errors.
- `T2RuleSandbox` catches `Exception` from sandboxed rule execution with a generic fallback — sandbox escape or JVM errors could be silently treated as rule evaluation failures.

There is no platform-level `PlatformException` hierarchy to distinguish domain errors, infrastructure errors, and system errors.

### 5.4 Domain Pack Schema Evolution: Single V001 Migration Per Pack

All 14 domain packs have exactly one migration file: `V001__create_<pack>_schema.sql`. In contrast, the kernel has multiple versioned migrations (audit-trail has V001–V005, event-store has V001–V008). This indicates domain pack schemas are treated as immutable initial definitions rather than living artifacts subject to iterative evolution — a significant constraint for production operation.

### 5.5 `@doc.*` Annotation Style Not Enforced Uniformly

The kernel uses structured Javadoc tags (`@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern`) consistently. Domain packs use the same convention for top-level classes, but inner interfaces and port definitions within those classes are inconsistently documented — some have informal comments, some have none.

---

## 6. Security Issues

### 6.1 `InternalServiceBypassFilter` — Bypass Signal Is Never Propagated

**Severity: HIGH — Security Defect**

**File:** `kernel/api-gateway/src/main/java/com/ghatana/appplatform/gateway/InternalServiceBypassFilter.java` (lines 66-70)

```java
// Mutate request by adding an internal marker header via a wrapped request
log.debug("Internal service bypass granted: sub={}", sub);
// ActiveJ does not support mutable request headers directly;
// store signal in a thread-local or attach as request attribute.
// We add a custom header on the response path for downstream awareness.
return next.serve(request);
```

The filter identifies an internal service account JWT but **cannot propagate `X-Internal-Service` because ActiveJ's `HttpRequest` is immutable**. The comment acknowledges the gap but provides no resolution. Downstream rate-limiters and tenant session checkers that check for this header will never see it. This means:
1. Internal service-to-service calls are **not actually bypassed** from rate-limiting and session checks, causing latency and false throttling.
2. The bypass mechanism is documented as a feature but is functionally broken.

**Resolution Options:** Wrap the request in a custom mutable request adapter, use a `ThreadLocal<Set<String>>` context propagation (requires care for async code), or switch to request attributes via a context object passed through the `FilterChain`.

### 6.2 JWT Validation Missing `nbf`, `iss`, and `aud` Claims

**Severity: HIGH — Security Gap**

**File:** `kernel/api-gateway/src/main/java/com/ghatana/appplatform/gateway/JwtValidationFilter.java`

`JwtValidationFilter` validates:
- ✅ `Authorization: Bearer` header presence
- ✅ RS256 algorithm
- ✅ Signature (RSA public key)
- ✅ Expiry (`exp`)

`JwtValidationFilter` does **NOT** validate:
- ❌ `nbf` (not-before) — tokens with a future issue time are accepted
- ❌ `iss` (issuer) — any token signed with the correct key is accepted regardless of issuer
- ❌ `aud` (audience) — tokens for other services are accepted
- ❌ `jti` (JWT ID) — no replay prevention

Without `iss` and `aud` validation, a token issued for a different service in the platform (e.g., a token issued for `audit-trail`) would be accepted by `api-gateway`, enabling cross-service token reuse attacks.

### 6.3 Zookeeper Anonymous Login Enabled in docker-compose

**File:** `docker-compose.yml`  
```yaml
ALLOW_ANONYMOUS_LOGIN: "yes"
```
Acceptable for local dev, but requires a clear comment that this is dev-only and must not appear in staging/production Compose overrides.

---

## 7. Missing Infrastructure / Architectural Gaps

### 7.1 No Root-Level `settings.gradle.kts` for App-Platform

There is no `settings.gradle.kts` within `app-platform/` itself. The platform is likely included as a sub-project from the monorepo root, but local build and IDE resolution requires a self-contained build descriptor. This makes it hard to build or test a single app-platform module independently.

### 7.2 Integration-Testing Module Has Zero Own Tests

`kernel/integration-testing/` (30 source files) contains chaos test runners, E2E suite orchestrators, performance baseline tools — all as production services wired via port interfaces. However, the test-harness module itself has no unit tests. If `LedgerDoubleEntryIntegrityTestSuiteService` has a bug in its assertion logic, the verification is not caught.

### 7.3 No Shared ObjectMapper / Configuration Bean

No platform-level Jackson configuration exists. Each module instantiates `new ObjectMapper()` with or without `JavaTimeModule` independently. A platform should provide a pre-configured `ObjectMapper` via its `platform:java:core` dependency for consistent serialization behavior across all modules.

### 7.4 No Connection Pool Lifecycle Management

`HikariDataSource` instances are created inside service constructors without consistent lifecycle management. `HikariDataSource` implements `Closeable` and must be closed on shutdown to release connections. No shutdown hook or ActiveJ lifecycle cleanup is wired for these pools in the observed code.

### 7.5 `HikariDataSource` vs `javax.sql.DataSource` Contract Violation

163 service classes accept `HikariDataSource` in their constructors instead of the `DataSource` interface. This couples services to the Hikari implementation and makes it impossible to inject an alternative `DataSource` (e.g., `c3p0`, `PgBouncer`, or an in-memory H2 `DataSource` for tests) without changing service signatures.

### 7.6 Domain Pack Missing Integration With Platform Event Bus

Many domain pack services accept a `Consumer<Object> eventPublisher` parameter rather than the strong-typed `EventBusPort` from the kernel SDK. This bypasses schema validation in `ValidatingAggregateEventStore`, means published events bypass the Kafka outbox relay, and produces untyped events that cannot be introspected by the event schema registry.

---

## 8. Refactoring Opportunities

### 8.1 Extract Shared `ObjectMapper` Configuration to `platform:java:core`

**Impact: High — consistency, correctness**

Provide a single `PlatformObjectMapper` class in `platform:java:core` that pre-registers `JavaTimeModule`, `Jdk8Module`, and any custom serializers. All 25 instantiation sites should reference this. A static `MAPPER` constant in each class should be replaced.

### 8.2 Introduce Platform-Level Exception Hierarchy

**Impact: Medium — observability, error classification**

Create `PlatformException`, `DomainException`, `InfrastructureException`, and `ExternalServiceException` in `platform:java:core`. Catch blocks across 475 `catch (Exception)` sites can then distinguish transient errors (retry) from domain violations (don't retry) from infrastructure failures (circuit break).

### 8.3 Replace Raw `ScheduledExecutorService` with ActiveJ `Eventloop`-Aware Scheduling

**Impact: Medium — lifecycle consistency**

The 15+ classes that create their own thread pools should use the platform's provided `Executor` (already injected in most constructors) wrapped with ActiveJ's `ScheduledRunnable` or `Eventloop.schedule()` mechanisms. This grounds scheduled tasks in the platform's managed lifecycle.

### 8.4 Introduce `DataSourceFactory` and Standardize on `javax.sql.DataSource`

**Impact: Medium — testability, flexibility**

Create a `DataSourceFactory` in infra or `platform:java:core` that supplies a configured `DataSource` (Hikari in production, H2 in tests). All service constructors should accept `javax.sql.DataSource`. This enables unit testing with an embedded database without changing service signatures.

### 8.5 Versioned SDK/Platform Metadata via `build.properties`

**Impact: Low-Medium — release correctness**

Add a Gradle `processResources` task to write `sdk.version` and `platform.version` into `platform.properties`. `SdkCoreAbstractionsService` reads them at startup instead of using hardcoded `"1.0.0"` constants.

### 8.6 Extract `resolveEndpointsBlocking()` Into a Real `ServiceRegistryPort`

**Impact: High — correctness**

The stub JSON return in `SdkCoreAbstractionsService.resolveEndpointsBlocking()` must be replaced by a real `ConfigStore.get()` call using K-02 config keys. A `ServiceRegistryPort` interface already exists conceptually (referenced in Javadoc); its concrete adapter needs to be implemented.

### 8.7 Consolidate FIX Protocol Session Parameters to Config

**Impact: Medium — operational flexibility**

FIX heartbeat interval and session timeout should be configurable per-session via the K-02 config engine, not hardcoded constants. This is a requirement for exchange connectivity where FIX session parameters are specified in the broker agreement.

### 8.8 Deduplicate Inner `AuditPort` / Event Publisher Port Definitions

Each of the 14 domain packs and several kernel modules defines its own `AuditPort` interface inline as an inner interface. These are structurally identical. They should be replaced by a single shared `AuditBusPort` from the SDK with a consistent signature.

---

## 9. Monorepo Context: Platform Shared Libraries

### 9.1 Ghatana Monorepo Architecture Overview

The Ghatana monorepo comprises three pillars:

| Pillar | Location | Contents |
|---|---|---|
| **Platform** | `/platform/java/` (30 modules), `/platform/typescript/` (16 packages), `/platform/contracts/` | Shared libraries: core, runtime, http, database, security, audit, config, observability, event-cloud, schema-registry, plugin, testing, workflow, domain, governance, connectors, ingestion, yaml-template, 6 agent modules, 3 AI modules |
| **Shared Services** | `/shared-services/` | 7 deployable services: auth-gateway, auth-service, ai-inference-service, ai-registry, feature-store-ingest, user-profile-service, infrastructure |
| **Products** | `/products/` | 12 products: aep, **app-platform**, audio-video, aura, data-cloud, dcmaar, flashit, security-gateway, software-org, tutorputor, virtual-org, yappc |

### 9.2 Platform Java Library Dependency Graph

Platform shared libraries follow a layered architecture:

**Layer 0 (Foundation):** `core` — Zero platform dependencies. Provides ActiveJ Promise integration, Jackson JSON, Nimbus JOSE JWT, Protobuf, Jakarta Validation, SLF4J, exception hierarchy, preconditions.

**Layer 1 (Infrastructure):**
- `runtime` → core — ActiveJ event loop, Launcher, ServiceGraph DI
- `domain` → core, contracts — Shared domain models (User, Role, Permission, TenantId, Agent, Event)
- `config` → core, runtime — Multi-source config with HOCON, interpolation, validation, watchers
- `database` → core, observability — HikariCP, JPA/Hibernate, Flyway, Redis (Jedis + Lettuce), connection routing
- `http` → core, observability — OkHttp client, ActiveJ server, filters, security filters
- `observability` → core, runtime, config — Micrometer, OpenTelemetry, health checks, SLO tracking
- `event-cloud` → core, domain — Append-only immutable event log, real-time tailing, multi-tenant isolation
- `audit` → core, domain, event-cloud, observability — AuditService port, AuditEvent model, JPA implementation
- `testing` → core, runtime — JUnit 5 extensions, TestContainers, AssertJ, Mockito, DataFaker, Awaitility, ArchUnit

**Layer 2 (Advanced):**
- `security` → core, config, domain, observability, governance, http — JWT, RBAC, ABAC, OAuth2, encryption, session mgmt, API key auth
- `governance` → core, contracts — Data classification, retention policies, compatibility policies, RBAC policies
- `plugin` → core, domain, event-cloud, ai-integration, governance — Plugin lifecycle, hot reload, capability checking, inter-plugin bus
- `workflow` → core, domain, observability — Generic pipeline-based workflow execution
- `schema-registry` → (standalone) — Schema storage, JSON validation, BACKWARD/FORWARD/FULL compatibility
- `connectors` → core, governance, domain, event-cloud — External system connectors (Postgres, Kafka, HTTP, File)
- `ingestion` → core, domain, governance, connectors, event-cloud — Schema validation, rate limiting, enrichment, batch/stream API

### 9.3 App-Platform's Platform Module Adoption

**Used (17/30 — 57%):**

| Platform Module | App-Platform Consumers | Usage Pattern |
|---|---|---|
| `core` | ALL 25 kernel + ALL 14 domain-packs | Foundation — ubiquitous |
| `database` | 21+ modules | Connection pooling, JDBC |
| `observability` | ALL 25 kernel + ALL 14 domain-packs | Metrics, tracing |
| `testing` | 12 kernel modules | Test fixtures |
| `security` | 5 modules (IAM, pack-certification, plugin-runtime, platform-manifest, regulator-portal) | JWT, RBAC |
| `http` | 4 modules (api-gateway, regulator-portal, platform-sdk) | HTTP client/server |
| `governance` | 6 modules (api-gateway, client-onboarding, corporate-actions, operator-workflows, regulator-portal, regulatory-reporting) | Policy enforcement |
| `audit` | 1 module (audit-trail) | Audit port/interface |
| `runtime` | 1 module (deployment-abstraction) | ActiveJ lifecycle |
| `ai-integration` | 5 modules (ai-governance, dlq-management, incident-management, operator-workflows, platform-sdk) | AI/ML integration |
| `agent-framework` | 3 modules (ai-governance, operator-workflows, workflow-orchestration, platform-sdk) | Agent SDK |
| `agent-learning` | 1 module (ai-governance) | Agent learning |
| `connectors` | 1 module (dlq-management) | External connectors |
| `plugin` | 3 modules (pack-certification, platform-sdk) | Plugin framework |
| `schema-registry` | 1 module (platform-sdk) | Schema validation |
| `observability-http` | 1 module (platform-sdk) | HTTP observability |
| `agent-resilience` | 1 module (platform-sdk) | Agent resilience |

**Not Used (13/30 — 43%):**

| Platform Module | Reason | Risk |
|---|---|---|
| `config` | App-platform reimplements via `config-engine` (K-02) | ⚠️ Potential missed reuse |
| `workflow` | App-platform reimplements via `workflow-orchestration` (W-01) | ✅ Justified: different execution model |
| `event-cloud` | App-platform uses own `event-store` (K-05) | ✅ Justified: DDD aggregate sourcing vs streaming |
| `domain` | App-platform defines own domain models | ⚠️ Should assess shared User/Role/TenantId reuse |
| `ingestion` | Not applicable to finance domain packs | ✅ Justified |
| `yaml-template` | Not needed | ✅ Justified |
| `ai-api` | Not relevant to current features | ✅ Justified |
| `ai-experimental` | Not relevant | ✅ Justified |
| `agent-memory` | Not used yet | ✅ Justified (future need) |
| `agent-registry` | Not used yet | ✅ Justified |
| `agent-dispatch` | Not used yet | ✅ Justified |
| `observability-clickhouse` | ClickHouse not in stack | ✅ Justified |
| `connectors` (full) | Only dlq-management uses it | Partial adoption |

---

## 10. Cross-Product Duplication Analysis

### 10.1 config-engine (K-02) vs platform:java:config

**Verdict: EXTENSION — Low Duplication (5–10%), Keep Separate**

| Aspect | platform:java:config | app-platform config-engine |
|---|---|---|
| Config model | Flat multi-source aggregation (ENV, file, system props, YAML) | Hierarchical 5-level: GLOBAL → JURISDICTION → TENANT → USER → SESSION |
| Persistence | In-memory, file-based | PostgreSQL + Redis CQRS |
| Validation | Network-NT JSON Schema | Versioned JSON Schema registry (`ConfigSchema`) |
| Hot-reload | File watcher | PostgreSQL LISTEN/NOTIFY (`ConfigChangeNotifier`) |
| Interpolation | Yes (variable substitution) | No (relies on hierarchy override) |

App-platform's `ConfigMerger` with priority-based hierarchical resolution (GLOBAL → JURISDICTION → TENANT → USER → SESSION) and `RedisConfigProjection` CQRS pattern are finance-domain-specific requirements not served by platform's flat config model.

**Key classes unique to app-platform:** `PostgresConfigStore`, `ConfigMerger`, `ConfigEntry`, `ConfigSchema`, `ConfigChangeNotifier`, `RedisConfigProjection`

**Recommendation:** Keep separate. If multi-tenant hierarchical config becomes a cross-product pattern, extract `ConfigHierarchyLevel` and `ConfigMerger` abstractions to `platform:java:config` as an extension point.

### 10.2 workflow-orchestration (W-01) vs platform:java:workflow

**Verdict: INDEPENDENT — 0% Overlap**

| Aspect | platform:java:workflow | app-platform workflow-orchestration |
|---|---|---|
| Execution model | Simple interface pipeline (`Workflow.execute()`) | Stateful FSM: PENDING → RUNNING → WAITING → COMPLETED |
| Step types | Generic `WorkflowStep` | TASK, DECISION, PARALLEL, WAIT, SUB_WORKFLOW |
| Decision logic | None (sequential) | CEL expression evaluation |
| Persistence | None (in-memory) | PostgreSQL (event-sourced) |
| Correlation | None | Wait-correlation with SLA tracking |
| DSL | None | YAML/JSON workflow definitions |

These represent fundamentally different architectures: platform provides a lightweight handler chain; app-platform provides a full workflow runtime with 40+ domain-specific service integrations. No practical reuse path exists.

**Recommendation:** Keep independent. Document architectural divergence. If platform needs stateful workflows later, adopt app-platform's model.

### 10.3 event-store (K-05) vs platform:java:event-cloud

**Verdict: EXTENSION — 15% Overlap, Different Addressing Models**

| Aspect | platform:java:event-cloud | app-platform event-store |
|---|---|---|
| Addressing | `(partition_id, offset)` — stream-oriented | `(aggregate_id, sequence_number)` — DDD aggregate-scoped |
| Purpose | General streaming with tenant isolation | DDD aggregate event sourcing for finance |
| Concurrency | Append-only with idempotency keys | Optimistic concurrency via unique constraint |
| Calendar | Standard timestamps | Dual-calendar (UTC + Bikram Sambat) |
| Replay | Stream tailing | `EventReplayEngine` for projection rebuilding |
| Schema | EventTypeRef with versioning | Finance-specific event schemas |

Both are append-only, but they serve different concerns (stream fan-out vs. aggregate reconstruction). No direct reuse without a bridging adapter.

**Recommendation:** Keep separate. Define an integration contract (outbox + relay pattern) for publishing aggregate events to `event-cloud` when cross-product streaming is needed.

### 10.4 IAM (K-01) + platform:java:security — Proper Reuse with Intentional Divergence

**Verdict: EXTENSION — 40% Reuse**

App-platform's `iam` module correctly depends on `platform:java:security` and extends it with:
- RS256 JWT signing (vs platform's HMAC-SHA256) using HSM key storage via `secrets-management`
- Finance-specific IAM: MFA (TOTP), beneficial ownership tracking, login anomaly detection
- Nepal-specific NID verification adapter

**Divergence requiring documentation:** Platform's `JwtTokenProvider` uses HMAC-SHA256; app-platform uses RS256 with HSM keys. This is intentional for regulatory compliance but undocumented.

### 10.5 audit-trail (K-07) + platform:java:audit — Model Extension Pattern

**Verdict: PROPER REUSE — 30% Foundation Reuse**

App-platform correctly depends on `platform:java:audit` and extends it with:
- Cryptographic SHA-256 hash-chain storage (`PostgresAuditTrailStore`)
- Tamper detection and immutability enforcement
- Finance-specific audit entries with dual calendar timestamps
- Event publishing to event-store (`AuditLogCreatedEvent`)

**This is the correct extension pattern.** Platform provides the `AuditService` interface; app-platform implements financial-grade cryptographic audit.

### 10.6 observability-sdk (K-06) + platform:java:observability — Best Practice Model

**Verdict: 100% REUSE — Exemplary Thin Wrapper**

The `observability-sdk` demonstrates the ideal platform extension pattern:
- Zero reimplementation of platform observability code
- Adds only: `FinanceMetricNames` (constants), `FinanceSloRegistry` (SLO definitions), `LedgerMetrics` (domain meters), `KernelHealthCheckRegistrar` (health checks)
- All underlying metrics, tracing, and health check infrastructure comes from platform

**All modules should follow this pattern when extending platform libraries.**

### 10.7 resilience-patterns (K-18) + platform:java:core — Composition Pattern

**Verdict: 0% Duplication — Excellent Composition**

`KernelResilienceFactory` explicitly states: _"This factory does NOT re-implement the resilience algorithms; it composes the platform primitives with domain-appropriate parameters."_

Composes platform's `CircuitBreaker`, `Bulkhead`, and `RetryPolicy` into finance-specific profiles:
- `CompositeResilienceProfile` — combined patterns
- `DependencyHealthAggregator` — cross-service health monitoring
- `TimeoutBudgetPropagator` — SLA-aware timeout inheritance

**Note:** Missing explicit `api(project(":platform:java:core"))` in build.gradle.kts — should be added for clarity.

### 10.8 plugin-runtime (K-04) vs platform:java:plugin — Integration Gap

**Verdict: ⚠️ INVESTIGATION NEEDED**

App-platform's plugin-runtime does **not** depend on `platform:java:plugin` despite both providing plugin lifecycle management:

| Feature | platform:java:plugin | app-platform plugin-runtime |
|---|---|---|
| Plugin interface | `Plugin` interface with lifecycle states | T1/T2/T3 tier classification |
| Registry | `PluginRegistry` | Custom registry + hot swap |
| Capabilities | `PluginCapability`, `PluginCompatibility` | `PluginCapabilityVerifier` |
| Health | `PluginHealthCheck` | Resource enforcement (`PluginResourceEnforcer`) |
| Communication | `PluginInteractionBus` | Not implemented |
| Manifest | `PluginMetadata` (versioning) | `PluginManifest` (Ed25519 signed) |
| Hot reload | `HotReloadPluginManager` | `PluginHotSwapService` |

Both modules provide overlapping plugin lifecycle management. App-platform adds finance-specific security (Ed25519 manifest signing, resource quotas, sandbox tiers) but reimplements basic plugin registry and lifecycle that platform already provides.

**Recommendation:** Refactor app-platform's plugin-runtime to extend `platform:java:plugin` as a foundation layer, adding tier classification and security on top.

### 10.9 Cross-Product Dependencies in App-Platform

App-platform has direct dependencies on other products:

| Module | Dependency | Reuse % | Purpose |
|---|---|---|---|
| data-governance (K-08) | `:products:data-cloud:platform` + `:products:data-cloud:spi` | 95% | Catalog, lineage, PII masking |
| dlq-management (K-19) | `:products:aep:platform` | 90% | Dead-letter handling, ML classification, replay |
| client-onboarding (K-W02) | `:products:aep:platform` | Partial | Agent-driven KYC workflow |
| corporate-actions | `:products:aep:platform` | Partial | Agent event processing |
| regulatory-reporting | `:products:aep:platform` | Partial | Report generation agents |

These cross-product dependencies are **intentional** and well-structured — app-platform reuses data-cloud's governance platform and AEP's agent execution platform rather than reimplementing them.

---

## 11. Shared Services Integration Gaps

### 11.1 Shared Services Overview

| Service | Port | Purpose | Platform Modules Used |
|---|---|---|---|
| auth-gateway | 8081 | JWT validation + rate limiting | security, http |
| auth-service | 8082 | OIDC/OAuth2 authentication server | security, database |
| ai-inference-service | 8083 | LLM gateway with multi-provider routing | observability |
| ai-registry | 8084 | Model lifecycle management | domain, observability |
| feature-store-ingest | 8085 | Real-time feature extraction from EventCloud | event-cloud, connectors |
| user-profile-service | — | User preferences & profile management | database |

### 11.2 Authentication Layer Gap

**Severity: MEDIUM**

App-platform's IAM module implements only OAuth2 `client_credentials` grant. The shared `auth-service` provides full OIDC with authorization_code flow, PKCE, and session management. App-platform does not integrate with `auth-service` for end-user authentication.

**Impact:** App-platform deploys its own authentication logic that may diverge from the monorepo's standardized auth. Changes to OIDC policies in `auth-service` (MFA enforcement, session duration, token rotation) do not propagate to app-platform.

**Recommendation:** Evaluate whether app-platform should delegate end-user authentication to `auth-service` and keep only service-to-service `client_credentials` in its IAM module.

### 11.3 Missing Shared Service Integrations

The following app-platform capabilities are absent from shared services:

| Capability | App-Platform Module | Shared Service | Gap |
|---|---|---|---|
| Audit trail | `audit-trail` (K-07) | None | Shared services have no audit logging — compliance blind spot |
| Observability | `observability-sdk` (K-06) | None | Shared services lack structured metrics/tracing integration |
| Resilience | `resilience-patterns` (K-18) | None | Shared services use no circuit breakers or retry patterns |
| Config management | `config-engine` (K-02) | None | Shared services use raw environment variables |
| Secrets rotation | `secrets-management` (K-14) | None | Shared services have hardcoded fallbacks |
| Rate limiting | Redis-backed (app-platform) | Guava Cache (auth-gateway) | Single-instance vs. distributed — inconsistent |

**Recommendation:** Shared services should integrate `platform:java:audit`, `platform:java:observability`, and `platform:java:config` at minimum. App-platform's patterns (observability-sdk thin wrapper, resilience composition) should be the reference model.

### 11.4 AI Platform Convergence Opportunity

Three separate AI-related modules exist across the stack:
- `app-platform/kernel/ai-governance` — Model governance, SHAP/LIME explainability, drift detection
- `shared-services/ai-inference-service` — LLM gateway with multi-provider routing and caching
- `shared-services/ai-registry` — Model lifecycle management (DEVELOPMENT → STAGING → PRODUCTION)

These are complementary but uncoordinated:
- `ai-registry` tracks model lifecycle → should feed governance decisions in `ai-governance`
- `ai-inference-service` executes inference → should report drift metrics to `ai-governance`
- `ai-governance` enforces policies → should gate `ai-registry` promotions

**Recommendation:** Define integration contracts between these three systems. Consider unifying into a single AI platform with governance, execution, and registry as layers.

---

## 12. Cross-Product Pattern Comparison

### 12.1 How Other Products Consume Platform Libraries

| Product | Platform Modules Used | Pattern |
|---|---|---|
| **data-cloud** | core, domain, audit, database, observability, http, security, config, plugin | **Heavy platform reuse (95%)** — thin domain layer on top |
| **AEP** | core, domain, workflow, agent-*, plugin, observability, audit, security, database, http, config, schema-registry | **Full platform stack (90%)** — leverages all agent modules |
| **security-gateway** | core, domain, observability, http, database, config, security, governance, audit | **Pure composition (80%)** — minimal domain logic, assembles platform modules |
| **audio-video** | governance, security, observability | **Selective reuse** — gRPC services use only governance/security/observability |
| **flashit** | governance, testing | **Minimal Java reuse** — primarily TypeScript/PNPM |
| **dcmaar** | http, core, observability, domain, governance | **Adapter pattern** — Java modules are adapters for TS/Rust system |
| **software-org** | domain, http, testing | **Minimal** — delegates to virtual-org framework |

### 12.2 App-Platform vs Peer Products

**Compared to data-cloud and AEP** (the two highest-reuse products), app-platform:
- ✅ Uses `core`, `database`, `observability` universally (same as peers)
- ⚠️ Does NOT use `platform:java:config` (data-cloud and AEP both do)
- ⚠️ Does NOT use `platform:java:domain` (data-cloud and AEP both do — shared User/Role/TenantId)
- ⚠️ Does NOT use `platform:java:workflow` (AEP does; app-platform has justified divergence)
- ⚠️ Does NOT use `platform:java:event-cloud` (data-cloud uses SPI; app-platform has justified divergence)
- ✅ Uses `platform:java:audit` correctly (same as data-cloud)
- ✅ Cross-product dependencies to data-cloud and AEP are well-structured

### 12.3 Extraction Candidates from App-Platform to Platform

Code in app-platform that could benefit other products if extracted to platform:

| Module | Extract What | Target | Effort | Trigger |
|---|---|---|---|---|
| config-engine | `ConfigHierarchyLevel`, `ConfigMerger` abstractions | `platform:java:config` | HIGH | When another product needs multi-tenant hierarchical config |
| rules-engine | `OpaEvaluationService` (OPA REST client + caching) | `platform:java:governance` | MEDIUM | When another product needs OPA policy evaluation |
| secrets-management | `SecretProvider` port + `VaultSecretProvider` | New `platform:java:secrets` | MEDIUM | When another product needs Vault integration |
| audit-trail | Hash-chain algorithm (`HashChainService`) | `platform:java:audit` | LOW | When another product needs tamper-evident audit |
| plugin-runtime | T1/T2/T3 tier classification model | `platform:java:plugin` | HIGH | When plugin tiers become cross-product standard |

### 12.4 Generic Code in App-Platform That Should Be Product-Scoped Libraries

These kernel modules contain reusable patterns that could serve multiple domain-packs but are currently kernel-level:

| Pattern | Current Location | Consumers | Action |
|---|---|---|---|
| Dual-calendar (UTC + BS) timestamps | `event-store`, `audit-trail`, `calendar-service` | All domain packs | Already encapsulated in `calendar-service` — ensure all domain packs use it consistently |
| Jurisdiction-based routing | `rules-engine` (`JurisdictionPolicyRouter`) | compliance, regulatory-reporting, sanctions | Consider app-platform-level shared lib if pattern grows |
| Redis caching patterns | Scattered across `rules-engine`, `config-engine`, `iam` | Many modules | Extract `AppPlatformCacheFactory` to standardize Jedis pool creation |

---

## 13. Summary Table

| Category | Count | Severity |
|---|---|---|
| Domain packs with zero tests | 14/14 | CRITICAL |
| Kernel modules with zero tests | 15/25 | HIGH |
| Production code stubs not yet implemented | 7 | HIGH |
| Security: broken `InternalServiceBypassFilter` | 1 | HIGH |
| Security: incomplete JWT validation (`nbf`/`iss`/`aud`) | 4 missing claims | HIGH |
| Hardcoded service URLs in production logic | 1 | HIGH |
| **Mandate 2: data-governance bypasses data-cloud (7 services, direct JDBC)** | 7 services | HIGH |
| **Mandate 4/Audit: 15 duplicate AuditPort definitions** | 15 sites | HIGH |
| **Mandate 4/Audit: missing audit on break-glass, config, role changes** | 6 operation types | HIGH |
| Platform modules not adopted (config, domain, plugin integration) | 3 | MEDIUM |
| **Mandate 1: untyped `Consumer<Object>` event publishers** | 3 domain-pack services | MEDIUM |
| **Mandate 1: EventBusPort not wired to AEP** | ~10 modules using EventBusPort | MEDIUM |
| **Mandate 3: workflow-orchestration (W-01) not implementing platform:java:workflow.Workflow** | 1 module | DESIGN COMPLETE → implement |
| **Mandate 4/Governance: ai-governance missing platform:java:governance dependency** | 1 module | MEDIUM |
| Shared services integration gaps (audit, o11y, resilience, config, secrets, rate limiting) | 6 capabilities | MEDIUM |
| Inconsistent DataSource typing (HikariDataSource vs javax.sql.DataSource) | 163 violations | MEDIUM |
| Raw `ScheduledExecutorService` bypassing ActiveJ lifecycle | 15+ files | MEDIUM |
| ObjectMapper proliferation with inconsistent JavaTimeModule registration | 25 instances | MEDIUM |
| Bare `catch (Exception)` in domain services | 475 occurrences | MEDIUM |
| Hardcoded credentials in docker-compose | 3 values | MEDIUM (dev) |
| Single SQL migration per domain pack (no schema evolution) | 14/14 | MEDIUM |
| Undocumented intentional platform divergences | 3 (JWT alg, config model, workflow model) | LOW |
| Hardcoded version constants (SDK, platform, tracer) | 3 | LOW |
| Missing explicit build dependency declarations (`resilience-patterns` → `core`) | 1 | LOW |

---

## 14. Priority Remediation Roadmap

### Immediate (Sprint 1) — Security & Critical Fixes
1. Fix `InternalServiceBypassFilter` — implement request context propagation using a thread-local or context wrapper to propagate the `X-Internal-Service` flag to downstream filters.
2. Add `nbf`, `iss`, and `aud` validation to `JwtValidationFilter`; add a comment documenting why the gateway reimplements JWT validation instead of delegating to `platform:java:security`.
3. Move docker-compose credentials to `.env` file with `.env.example` template; add `.env` to `.gitignore`.
4. Replace `SdkCoreAbstractionsService.resolveEndpointsBlocking()` stub with real K-02 config resolution.
5. **[Mandate 4/Audit]** Add `BREAK_GLASS_ACCESS_GRANTED` audit event to `BreakGlassSecretAccessService` (highest priority — emergency access with no audit trail is a compliance failure).
6. **[Mandate 4/Audit]** Add `SECRET_ROTATED` audit event to `SecretRotationScheduler`.

### Short-Term (Sprint 2–3) — Audit Consolidation & Testing
7. Add unit tests for `kernel/iam` (authentication/authorization flows).
8. Add unit tests for `kernel/secrets-management` (HSM adapter, certificate lifecycle).
9. Create shared `PlatformObjectMapper` in `platform:java:core`; replace 25 instantiation sites.
10. Standardize all service constructors to accept `javax.sql.DataSource` (not `HikariDataSource`).
11. Wire `CertificateLifecycleService.renew()` to Vault PKI or add configuration-time fail-fast check.
12. Add explicit `api(project(":platform:java:core"))` dependency to `resilience-patterns/build.gradle.kts`.
13. **[Mandate 4/Audit]** Export `AuditBusPort` from `platform-sdk` as a public top-level interface; delete all 15 inner `AuditPort` inner interface duplicates across kernel modules and domain packs.
14. **[Mandate 4/Audit]** Add `CONFIG_CHANGED` audit events to all write paths in `PostgresConfigStore`.
15. **[Mandate 4/Audit]** Add `ROLE_GRANTED` / `PERMISSION_GRANTED` audit events to all write paths in `PostgresRolePermissionStore`.

### Medium-Term (Sprint 4–6) — Mandate Alignment & Platform Integration
16. **[Mandate 2/data-cloud — CRITICAL]** Migrate all 7 services in `kernel/data-governance/` from direct JDBC to `data-cloud:platform` APIs:
    - `DynamicDataMaskingService` → data-cloud masking APIs
    - `DataLineageService` → data-cloud lineage tracking
    - `DataCatalogService` → data-cloud catalog registration
    - `DataClassificationService` → data-cloud classification engine
    - `DataRetentionPolicyService` → data-cloud lifecycle management
    - `DataStewardshipService` → data-cloud stewardship APIs
    - `RightToErasureHandlerService` → data-cloud erasure workflow
17. **[Mandate 2/data-cloud]** Implement 3 app-platform data-cloud plugins (`FinanceMaskingRulesPlugin`, `FinanceClassificationPlugin`, `FinanceRetentionPlugin`) registered via `data-cloud:spi`.
18. **[Mandate 1/AEP]** Create `AepEventBusAdapter` implementing `EventBusPort`; wire as the default `EventBusPort` implementation in the platform-sdk launcher. This connects `workflow-orchestration`, `operator-workflows`, and `ai-governance` event publishing to AEP's event pipeline.
19. **[Mandate 1/AEP]** Replace `Consumer<Object> eventPublisher` in `MlEntityResolutionService`, `AiInstrumentClassificationService`, and `InstrumentLifecycleService` (reference-data) with typed `EventBusPort` from SDK.
20. **[Mandate 1/AEP]** Define finance-domain AEP operator extensions: `CorporateActionEntitlementOperator`, `DlqMlClassificationOperator`, `WorkflowTriggerOperator` in their respective app-platform modules; register with AEP operator catalog.
21. **[Mandate 3/Workflow — Phase 1]** Enhance `platform:java:workflow` with new types: `WorkflowKind`, `WorkflowOptions`, `WorkflowLifecycleEvent`, `WorkflowLifecycleListener`, `WorkflowRunStatus`, `WorkflowRun`, extended `WorkflowStateStore` SPI, `WorkflowExpressionEvaluator`, `WorkflowWaitCoordinator`. Backward compatible — no existing code changes.

22. **[Mandate 3/Workflow — Phase 2]** Create `platform:java:workflow-runtime` module: `DurableWorkflowRuntime` (generalized FSM engine from app-platform), `CelWorkflowExpressionEvaluator`, `ParallelStepExecutor`, `WaitStepEngine`, `SubWorkflowComposer`, `MetricsWorkflowListener`, `AuditWorkflowListener`. Full unit tests with `InMemoryWorkflowStateStore`.

23. **[Mandate 3/Workflow — Phase 3]** Create `platform:java:workflow-jdbc` module: `JdbcWorkflowStateStore`, `JdbcWorkflowDefinitionRegistry`, `JdbcWorkflowWaitCoordinator` with canonical DDL schema. Integration-tested with Testcontainers.

24. **[Mandate 3/Workflow — Phase 4]** Migrate app-platform: add `workflow-runtime` + `workflow-jdbc` to `workflow-orchestration/build.gradle.kts`; wire `DurableWorkflowRuntime` as FSM dispatcher; register all TASK step refs in `StepRegistry`; delete 15 inner AuditPort/EventBusPort duplicates; wire `AuditWorkflowListener` (covers 6 missing audit events).

25. **[Mandate 3/Workflow — Phase 5]** AEP interop: implement `PipelineWorkflowStepAdapter`, `AepWorkflowWaitCoordinator`, `AepWorkflowLifecycleListener`. Enable `OPERATOR_PIPELINE` step kind in app-platform workflow definitions.
22. **[Mandate 4/Governance]** Add `api(project(":platform:java:governance"))` to `kernel/ai-governance/build.gradle.kts`; replace custom RBAC checks in `AiGovernancePolicyEngineService` with `platform:java:security.PolicyEvaluator`.
23. **[Mandate 4/Audit]** Add audit events for sensitive transitions in domain packs — focus on: trade execution (`oms`), settlement completion (`post-trade`), KYC state changes (`client-onboarding`), bundle import approvals (`sanctions`).
24. Add unit test baseline (minimum 3 tests per service) for all 14 domain packs — focus on OMS, risk-engine, sanctions, and reconciliation first.
25. Replace all raw `ScheduledExecutorService` in domain packs with injected `Executor` / ActiveJ lifecycle-aware scheduling.
26. Implement `GeoIpResolver` MaxMind adapter; make `InMemoryGeoIpResolver` test-only.
27. Add V002+ schema migration files for domain packs needing schema evolution (OMS, PMS, reconciliation).
28. Enforce `useHsm=true` in production deployment manifests; add startup assertion in `HsmKeyOperationsProvider`.
29. **Document platform divergences:** Create ADR explaining why config-engine, workflow-orchestration, and event-store diverge from platform equivalents.
30. **Evaluate `platform:java:domain` adoption:** Assess whether app-platform should use shared `User`, `Role`, `Permission`, `TenantId` models from `platform:java:domain`.

### Long-Term (Sprint 7+) — Cross-Product & Platform Generalization
31. Implement digital signature block in `AuditEvidencePdfGenerator`.
32. Add gRPC support to `T3NetworkPluginRuntime`; integrate with `platform:java:plugin` lifecycle.
33. Implement real Nepal NID API adapter with WireMock-based dev profile integration test.
34. Introduce `PlatformException` hierarchy in `platform:java:core`; replace `new RuntimeException(...)` wrapping.
35. Add SDK/Platform version injection from `build.properties` via Gradle `processResources`.
36. **[Mandate 3/Workflow — See Design Doc]** Full design produced. See [platform/java/workflow/docs/UNIFIED_WORKFLOW_PLATFORM_DESIGN.md](../../../../platform/java/workflow/docs/UNIFIED_WORKFLOW_PLATFORM_DESIGN.md) for complete 3-module architecture, interoperability model, migration plan, AEP integration patterns, and per-product compatibility matrix.
37. **[Mandate 4/Secrets → Platform]** Extract `SecretProvider` port + `VaultSecretProvider` to a new `platform:java:secrets` shared module; update K-14 to depend on it. Enables other products (data-cloud, yappc) to adopt proper secret management.
38. **[Mandate 1/AEP]** Investigate `platform:java:plugin` integration for `plugin-runtime` (K-04); make K-04 extend `platform:java:plugin` as foundation, adding tier classification and Ed25519 manifest signing on top.
39. **Integrate shared auth-service:** Evaluate delegating end-user OIDC authentication to `shared-services/auth-service`; keep only `client_credentials` in IAM.
40. **Define AI platform integration contracts:** Connect `ai-governance` ↔ `ai-inference-service` ↔ `ai-registry` with drift metrics, governance gates, and lifecycle events.
41. **Promote shared services to use platform modules:** Push `platform:java:audit`, `platform:java:observability`, and `platform:java:config` adoption into shared services.
42. **Create event-store → event-cloud bridge:** Define outbox + relay pattern for publishing aggregate events from app-platform's event-store into platform's event-cloud for cross-product consumption.

---

## 15. Architectural Mandates Assessment

This section evaluates app-platform against four explicit architectural mandates established for the Ghatana monorepo. Each mandate is assessed for current compliance, gaps, and a concrete migration plan.

---

### Mandate 1: All Event Processing via AEP

**Principle:** All event processing must go through AEP (Agent Execution Platform). Product-specific behavior belongs in AEP operator extensions. Generic enhancements go back into AEP itself.

**Overall Status: ✅ LARGELY COMPLIANT — Migration opportunities remain**

#### Current Compliance

| Component | Status | Details |
|---|---|---|
| `dlq-management` (K-19) | ✅ AEP-integrated | Depends on `:products:aep:platform` (90%); DLQ alerts via `EventPort` |
| `corporate-actions` | ✅ AEP-integrated | Depends on `:products:aep:platform`; all lifecycle events via `EventPort` |
| `regulatory-reporting` | ✅ AEP-integrated | Depends on `:products:aep:platform`; report submission events |
| `event-store` (K-05) | ✅ Acceptable | `KafkaEventPublisher` + `KafkaEventOutboxRelay` use transactional outbox pattern; Kafka is infrastructure, not business logic |
| `ledger-framework` | ✅ Acceptable | `OutboxRelay` + `OutboxEventPublisher` port — exactly-once via two-phase commit |
| `workflow-orchestration` (W-01) | ⚠️ Port-based only | Uses `EventBusPort`, `EventBusSubscriberPort` — not wired to AEP |
| `operator-workflows` | ⚠️ Port-based only | Uses `EventPublishPort` — not wired to AEP |
| `reference-data` | ⚠️ Weak typing | Uses `Consumer<Object> eventPublisher` — untyped, schema-unsafe |
| `ai-governance` | ⚠️ Port-based only | Uses `EventPort` — not wired to AEP |

#### Gaps and Required Actions

**Gap 1: Untyped `Consumer<Object>` event publishers in domain packs**

Several domain pack services accept `Consumer<Object> eventPublisher` as constructor parameter:
- `MlEntityResolutionService` (`reference-data`)
- `AiInstrumentClassificationService` (`reference-data`)
- `InstrumentLifecycleService` (`reference-data`)

Using `Consumer<Object>` bypasses AEP's schema registry, type validation, and routing. Events published this way are opaque to the platform.

**Action:** Replace `Consumer<Object> eventPublisher` with the typed `EventBusPort` from the SDK (available via `SdkCoreAbstractionsService`). Wire `EventBusPort` implementations to AEP's `EventCloud` adapter.

**Gap 2: Port-based event publishers not wired to AEP**

`WorkflowExecutionRuntimeService`, `WorkflowDefinitionService`, `TenantRegistryService`, `LicenseFeatureGateService`, and `ModelLifecycleService` all publish events via port interfaces (`EventBusPort`, `EventPublishPort`) — but there is no AEP-backed implementation wired at the composition root. Events are only published if a concrete adapter is injected at startup.

**Action:** Create an `AepEventBusAdapter` (implementing `EventBusPort`) that delegates to AEP's event processing pipeline. Register as the default implementation at the platform launcher level.

**Gap 3: Product-specific event operators should extend AEP**

Finance-domain event processing logic (corporate action entitlement calculations, DLQ ML classification, workflow trigger evaluation) currently lives partly in app-platform and partly in AEP. Define these as **AEP Operator extensions** owned by app-platform:
- `CorporateActionEntitlementOperator` — owned in `domain-packs/corporate-actions/`
- `DlqMlClassificationOperator` — owned in `kernel/dlq-management/`
- `WorkflowTriggerOperator` — owned in `kernel/workflow-orchestration/`

Register these operators with AEP's operator catalog at startup rather than embedding the logic in service constructors.

#### AEP Extension Pattern (Recommended)

```
AEP Operator Catalog
  └── Finance Operators (owned by app-platform)
        ├── CorporateActionEntitlementOperator  
        ├── DlqMlClassificationOperator
        ├── WorkflowTriggerOperator
        └── RegulatoryReportingOperator

AEP Event Bus (platform:java:event-cloud routing)
  └── AepEventBusAdapter (injected as EventBusPort)
        ├── workflow-orchestration (publishes WorkflowCompleted, WorkflowFailed)
        ├── operator-workflows (publishes TenantCreated, TenantSuspended)
        └── ai-governance (publishes ModelDeployed, DriftDetected)
```

---

### Mandate 2: All Data Handling via data-cloud

**Principle:** All data handling and management (catalog, lineage, PII masking, classification, retention) must go through data-cloud platform. Product-specific data plugins are defined and owned by app-platform.

**Overall Status: ⚠️ PARTIAL — Critical violation in data-governance module**

#### Legitimate Domain-Specific Stores (Compliant)

These modules implement domain-specific data models and correctly own their persistence. They should NOT be migrated to data-cloud:

| Module | Stores | Justification |
|---|---|---|
| `ledger-framework` | `PostgresLedgerStore`, `PostgresAccountStore`, `PostgresBalanceSnapshotStore` | Domain-specific double-entry accounting models |
| `iam` | `PostgresRolePermissionStore`, `PostgresBeneficialOwnershipStore` | Domain-specific IAM models |
| `config-engine` | `PostgresConfigStore` | Domain-specific hierarchical config storage |
| `audit-trail` | `PostgresAuditTrailStore` | Domain-specific cryptographic audit chain |
| `reference-data` | `InstrumentStore`, `EntityStore`, `BenchmarkStore` | Domain-specific financial master data |
| `sanctions` | `SanctionsEntryStore`, `ScreeningResultStore` | Domain-specific regulatory data |
| `market-data` | `MarketDataStore`, `TickBackfillStore` | Domain-specific time-series data |
| All domain packs | Schema migrations (`V001*.sql`) | Domain-owned schemas — correct pattern |

#### Critical Violation: data-governance (K-08) Bypasses data-cloud

**Severity: HIGH**

`kernel/data-governance/` declares `data-cloud:platform` and `data-cloud:spi` dependencies in its `build.gradle.kts` but **all 7 service implementations use direct JDBC (HikariDataSource, Connection, PreparedStatement) and never call data-cloud APIs**:

| Service | What It Does | Should Use | Gap |
|---|---|---|---|
| `DynamicDataMaskingService` | PII masking via direct SQL `masking_rule_configs` table | `data-cloud:platform` masking APIs | Direct JDBC — never calls data-cloud |
| `DataLineageService` | DAG construction via direct SQL `lineage_edges` + `data_catalog` tables | `data-cloud:platform` lineage tracking | Direct JDBC — never calls data-cloud |
| `DataCatalogService` | Asset registration via direct SQL `data_catalog` table | `data-cloud:platform` catalog registration | Direct JDBC — never calls data-cloud |
| `DataClassificationService` | PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED classification | `data-cloud:platform` classification engine | Direct JDBC — never calls data-cloud |
| `DataRetentionPolicyService` | Retention rules via direct PostgreSQL | `data-cloud:platform` lifecycle management | Direct JDBC — never calls data-cloud |
| `DataStewardshipService` | Stewardship assignment via direct SQL | `data-cloud:platform` stewardship APIs | Direct JDBC — never calls data-cloud |
| `RightToErasureHandlerService` | GDPR erasure via direct SQL | `data-cloud:platform` erasure workflow | Direct JDBC — never calls data-cloud |

**Root cause:** The build.gradle.kts declares data-cloud dependencies but they are unused dead declarations. The implementation was built independently.

**Migration plan:**
1. Map each service's operations to data-cloud:platform APIs (data-cloud exposes catalog, lineage, classification, masking, retention)
2. Replace `HikariDataSource` injection in each service with the corresponding data-cloud port interface
3. Implement app-platform-specific extensions as data-cloud **plugins** (implementing `data-cloud:spi`) for finance-specific masking rules and classification schemes
4. Remove the duplicate schema tables (`masking_rule_configs`, `lineage_edges`, `data_catalog`) — data-cloud manages these
5. Add proper data-cloud:spi plugin registrations at the platform-sdk launcher

**Estimated scope:** 7 service files, ~400-600 LOC replacement, plus 4-5 data-cloud plugin wrappers.

#### App-Platform Data Plugins for data-cloud (Pattern)

```
data-cloud:platform
  └── Plugin Registry
        └── app-platform plugins (owned in kernel/data-governance/)
              ├── FinanceMaskingRulesPlugin   (masking rules for account numbers, NID, etc.)
              ├── FinanceClassificationPlugin  (SEBON/NRB data classification rules)
              └── FinanceRetentionPlugin        (regulatory retention schedules per jurisdiction)
```

---

### Mandate 3: Workflow from Shared Place Consistently

**Principle:** Try to use or update the shared workflow module (`platform:java:workflow`) consistently where possible. If app-platform's workflow needs are unique, contribute enhancements back to platform.

**Overall Status: ✅ DESIGN COMPLETE — Full 3-module unified platform designed and documented**

> **Full Design Document:** [platform/java/workflow/docs/UNIFIED_WORKFLOW_PLATFORM_DESIGN.md](../../../../platform/java/workflow/docs/UNIFIED_WORKFLOW_PLATFORM_DESIGN.md)  
> A comprehensive multi-module design has been produced covering all workflow kinds, interoperability, app-platform migration, AEP integration, and an 11-sprint roadmap.

#### Architecture Comparison (As-Is)

| Dimension | platform:java:workflow | AEP's workflow usage | app-platform workflow-orchestration (W-01) |
|---|---|---|---|
| Execution model | Lightweight in-memory pipeline | Short-lived agent task chains | Persistent FSM (PENDING→RUNNING→WAITING→COMPLETED) |
| Persistence | In-memory (`DurableWorkflowEngine` has `InMemoryWorkflowStateStore`) | AEP event log backed | PostgreSQL event-sourced state |
| Step types | Generic `WorkflowStep` functional interface | Operator-based (`UnifiedOperator`) | TASK, DECISION, PARALLEL, WAIT, SUB_WORKFLOW |
| Decision logic | None | Agent scoring | CEL expression evaluation |
| Long-running | ⚠️ (in-memory, no WAIT step) | ❌ (autonomous) | ✅ (days/weeks — trade settlement, onboarding) |
| DSL | None | YAML operator catalogs | YAML/JSON workflow definitions |
| Event correlation | None | AEP event routing | Wait-correlation with SLA tracking |
| Sub-workflows | None | None | Yes (max 5 levels deep) |

**Key finding:** `platform:java:workflow` already has `DurableWorkflowEngine` with retry, compensation, timeout, and a pluggable `WorkflowStateStore` SPI — but only provides an `InMemoryWorkflowStateStore`. The durable FSM runtime that app-platform built is the natural, production-grade continuation of what the platform already started. The gap is not architectural incompatibility — it is **missing shared components** that should live in platform.

#### The Target Architecture (To-Be)

Three Gradle modules deliver full coverage:

```
platform/java/
├── workflow/           ← EXISTS (enhanced) — Core API + ephemeral pipelines + operator model
│                          Add: WorkflowKind, WorkflowOptions, WorkflowLifecycleEvent,
│                               WorkflowLifecycleListener, WorkflowRunStatus, WorkflowRun,
│                               WorkflowStateStore (extended SPI), WorkflowExpressionEvaluator,
│                               WorkflowWaitCoordinator
│
├── workflow-runtime/   ← NEW — Full durable FSM runtime (extracted from app-platform)
│                          Provides: DurableWorkflowRuntime, WorkflowDefinitionManager,
│                                    CelWorkflowExpressionEvaluator, ParallelStepExecutor,
│                                    WaitStepEngine, SubWorkflowComposer, WorkflowSlaTracker,
│                                    MetricsWorkflowListener, AuditWorkflowListener
│
└── workflow-jdbc/      ← NEW — PostgreSQL state store implementations
                           Provides: JdbcWorkflowStateStore, JdbcWorkflowDefinitionRegistry,
                                     JdbcWorkflowWaitCoordinator + canonical DDL schema
```

#### Ephemeral ↔ Durable Interoperability

The unified type hierarchy shares `Workflow`, `WorkflowContext`, and `WorkflowStep` across both kinds. Interop bridges:

- **`OPERATOR_PIPELINE` step kind:** A durable FSM step can run an AEP `Pipeline` via `PipelineWorkflowStepAdapter implements WorkflowStep`
- **Durable trigger from AEP operator:** An `AbstractOperator.process()` calls `DurableWorkflowRuntime.launch()` (fire-and-forget)
- **Sub-workflow cross-kind composition:** A durable "onboarding" workflow (weeks) can include an OPERATOR_PIPELINE step that runs an AI enrichment pipeline (seconds)

#### App-Platform Migration Path (4 phases)

| Phase | Action | Sprint |
|-------|--------|--------|
| **1** | Build platform modules; no app-platform changes | 1–3 |
| **2** | Add `workflow-runtime` + `workflow-jdbc` to `workflow-orchestration/build.gradle.kts`; wire `JdbcWorkflowStateStore`, replace FSM dispatch with `DurableWorkflowRuntime` | 4 |
| **3** | Register all TASK step refs in `StepRegistry`; delete 15 inner AuditPort/EventBusPort duplicates; wire `AuditWorkflowListener` and `AepWorkflowLifecycleListener` | 4 |
| **4** | Implement `PipelineWorkflowStepAdapter` + `AepWorkflowWaitCoordinator` for full AEP interop | 5 |

**Existing workflow YAML/JSON definitions require zero changes** — all current DSL fields map 1:1 to `WorkflowStepDefinition` in the new platform type.

#### What stays product-specific (does NOT move to platform)

`TradeSettlementWorkflowService`, `ReconciliationOrchestrationWorkflowService`, `CorporateActionWorkflowService`, `RegulatoryReportSubmissionWorkflowService`, `BundleImportWorkflowService`, `ApprovalWorkflowService` — domain business logic that **uses** `DurableWorkflowRuntime` but is not part of the platform.

---

### Mandate 4: Security, Auth, Audit, Governance, Secrets, Observability — Shared

**Principle:** All cross-cutting concerns (security, auth, audit, governance, secrets, observability) should use shared platform modules. Generalize where it makes sense.

#### 4a. Security — JWT and Access Control

**Status: ✅ COMPLIANT with one documented exception**

| Module | Dependency | Status | Notes |
|---|---|---|---|
| `iam` (K-01) | `platform:java:security` | ✅ Proper extension | RS256 + HSM; intentional divergence from platform's HMAC JWT |
| `pack-certification` | `platform:java:security` | ✅ Uses platform | |
| `plugin-runtime` | `platform:java:security` | ✅ Uses platform | |
| `platform-manifest` | `platform:java:security` | ✅ Uses platform | |
| `regulator-portal` | `platform:java:security` | ✅ Uses platform | |
| `api-gateway` | Does NOT use platform:java:security | ⚠️ Exception | Gateway-level JWT reimplemented with Nimbus JOSE for performance; acceptable |

**Action:** Add a comment block in `JwtValidationFilter.java` documenting why the gateway reimplements JWT validation rather than delegating to `platform:java:security.JwtTokenProvider` — performance rationale, algorithm pinning, and deployment topology independence.

#### 4b. Auth — Authentication Endpoints

**Status: ✅ COMPLIANT with one justified exception**

App-platform IAM properly implements `client_credentials` OAuth2 grant via `platform:java:security`. The `regulator-portal`'s custom `RegulatorAuthService` (TOTP MFA + 4-hour READ_ONLY sessions) is a justified exception:
- Regulators mandate TOTP; shared auth-service does not enforce it
- Regulator sessions are always READ_ONLY — structural requirement
- K-07 audit integration is mandatory for every regulator login
- Network topology may isolate regulator portal from internal auth-service

**Longer-term:** When `shared-services/auth-service` supports TOTP MFA enforcement and role-scoped sessions, migrate regulator auth to it.

#### 4c. Audit — CRITICAL GAPS

**Status: ❌ HIGH GAPS — 15 duplicate AuditPort definitions + missing coverage on sensitive operations**

**Problem 1: 15 duplicated `AuditPort` inner interface definitions**

Every module and domain pack defines its own `AuditPort` inner interface with identical signatures:
```java
// Repeated in WorkflowExecutionRuntimeService, KycWorkflowService, MlDeadLetterClassifierService,
// AiGovernancePolicyEngineService, UpgradeHistoryAuditService, and all 14 domain packs:
@FunctionalInterface
interface AuditPort {
    void record(String action, String resourceId, String outcome, Map<String, Object> details);
}
```

`SdkCoreAbstractionsService` already defines `AuditBusPort` at line 62 as a consolidated version — but **nothing in the codebase uses it**. It is dead code.

**Required change:** Delete all 15 inner `AuditPort` definitions. Export `AuditBusPort` as a public top-level interface from `platform-sdk`. Wire its implementation to the K-07 `AuditTrailService` which wraps `platform:java:audit`.

**Problem 2: Missing audit events on high-sensitivity operations**

| Operation | File | Severity | Audit Required |
|---|---|---|---|
| Break-glass secret access | `BreakGlassSecretAccessService.java` L119 | 🔴 CRITICAL | `BREAK_GLASS_ACCESS_GRANTED` with actor, secret name, justification, timestamp |
| Secret rotation | `SecretRotationScheduler.java` L91 | 🔴 HIGH | `SECRET_ROTATED` with secret name, old/new version |
| Config value change | All `PostgresConfigStore` write paths | 🔴 HIGH | `CONFIG_CHANGED` with key, old value hash, new value hash, actor |
| Role/permission grant | All `PostgresRolePermissionStore` write paths | 🔴 HIGH | `ROLE_GRANTED` / `PERMISSION_GRANTED` with principal, grantor |
| Bundle import approval | `BundleImportWorkflowService.java` L55-80 | 🟠 MEDIUM | State transitions: SUBMITTED → APPROVED / REJECTED |
| Domain pack sensitive transitions | All domain packs | 🟠 MEDIUM | Trade executions, settlement completions, KYC decisions |

**Required change:** Add `AuditBusPort` injection to the 6 modules above and emit typed audit events at every sensitive state transition.

#### 4d. Governance — Policy Evaluation

**Status: ⚠️ MEDIUM GAP — ai-governance missing platform:java:governance dependency**

`kernel/ai-governance` implements AI governance policies (prerequisites checking, bias monitoring, drift detection) but does **not** depend on `platform:java:governance`. This means:
- AI governance policies cannot compose with platform's `PolicyEvaluator` abstraction
- AI governance cannot leverage platform's `DataClassification` or `RetentionPolicy` types
- RBAC checks in `AiGovernancePolicyEngineService` are custom rather than using `platform:java:security.AuthorizationService`

**Required changes:**
1. Add `api(project(":platform:java:governance"))` to `kernel/ai-governance/build.gradle.kts`
2. Replace custom RBAC checks in `AiGovernancePolicyEngineService` with `platform:java:security.PolicyEvaluator`
3. Map AI model risk levels to `platform:java:governance.DataClassification` tiers

#### 4e. Secrets — Secret Management

**Status: ✅ FULLY COMPLIANT**

App-platform correctly routes all secret access through `kernel/secrets-management` (K-14):
- `VaultSecretProvider` — HashiCorp Vault KV v2
- `LocalFileSecretProvider` — AES-256-GCM + Argon2id (dev/test)
- `SecretRotationScheduler` — automated rotation with version history
- `BreakGlassSecretAccessService` — emergency access pattern

No direct `System.getenv()` or `System.getProperty()` calls found in app-platform for secret retrieval.

**Generalization opportunity:** Other products (`data-cloud`, `yappc`) bypass secrets management using `System.getenv()`. App-platform's `SecretProvider` port + `VaultSecretProvider` implementation should be extracted to `platform:java:secrets` (new shared module) so all products benefit. App-platform's K-14 would then depend on this platform module rather than implementing from scratch.

#### 4f. Observability — Metrics, Tracing, Health

**Status: ✅ EXEMPLARY — Reference implementation for the monorepo**

App-platform's `observability-sdk` (K-06) is the best-practice model:
- Zero reimplementation of platform metrics or tracing machinery
- Adds only: `FinanceMetricNames` (metric name constants), `FinanceSloRegistry` (SLO definitions), `LedgerMetrics` (domain meters), `KernelHealthCheckRegistrar` (health registrations), `KernelTracingInstrumentation` (trace context enrichment with tenant/jurisdiction)
- All modules inject `MeterRegistry` via DI — no static `GlobalOpenTelemetry` access in domain code
- Pattern propagates correctly: domain packs receive `MetricsCollector` from `observability-sdk` wrapper

**Action:** Document `observability-sdk` as the canonical pattern. Other products that use Micrometer or OpenTelemetry directly should implement a similar thin-wrapper following this model. Share the pattern in the platform documentation.

---

### Mandate Compliance Summary

| Mandate | Status | Critical Gaps | Actions Required |
|---|---|---|---|
| **1. Event processing via AEP** | ✅ Largely compliant | `Consumer<Object>` untyped publishers in 3 domain-pack services; EventBusPort not wired to AEP | Create `AepEventBusAdapter`; migrate untyped publishers; define finance AEP operators |
| **2. Data handling via data-cloud** | ❌ Violation found | data-governance (K-08): 7 services bypass data-cloud with direct JDBC despite declaring dependency | Migrate 7 services to data-cloud:platform APIs; implement 3 app-platform data-cloud plugins |
| **3. Workflow from shared place** | ✅ Design complete | 3-module unified platform designed | Implement per 5-phase roadmap in UNIFIED_WORKFLOW_PLATFORM_DESIGN.md |
| **4. Security/Auth/Audit/Governance/Secrets/O11y shared** | Mixed | CRITICAL: 15 duplicate AuditPorts + 6 missing break-glass/config/role audit events; MEDIUM: ai-governance not using platform:java:governance | Consolidate AuditPort; add 6 missing audit event sites; add governance dependency |

---

## Appendix A: Platform Module Reuse Matrix (App-Platform)

| Platform Module | Status | Classification | Overlap % | Notes |
|---|---|---|---|---|
| `core` | ✅ Universal | Foundation | — | Used by all 39 modules |
| `database` | ✅ Heavy | Foundation | — | 21+ modules |
| `observability` | ✅ Universal | Foundation | — | Used by all 39 modules |
| `testing` | ✅ Partial | Foundation | — | 12 kernel modules |
| `security` | ✅ Targeted | Extension | 40% | 5 modules; IAM extends with RS256/HSM |
| `http` | ✅ Targeted | Composition | — | 4 modules |
| `governance` | ✅ Targeted | Composition | — | 6 modules |
| `audit` | ✅ Proper | Extension | 30% | audit-trail extends with SHA-256 hash-chain |
| `runtime` | ✅ Minimal | Composition | — | 1 module |
| `ai-integration` | ✅ Targeted | Extension | — | 5 modules |
| `agent-framework` | ✅ Targeted | Extension | — | 3 modules |
| `config` | ❌ Not used | Reimplemented | 5-10% | config-engine has justified domain extension |
| `workflow` | ❌ Not used | Independent | 0% | Fundamentally different execution models |
| `event-cloud` | ❌ Not used | Independent | 15% | DDD aggregates vs. streaming — different addressing |
| `domain` | ❌ Not used | Should evaluate | — | Shared User/Role/TenantId models available |
| `plugin` | ⚠️ Not integrated | Overlapping | ~40% | Both provide plugin lifecycle; should integrate |
| `schema-registry` | ✅ Minimal | Composition | — | Only via platform-sdk |

## Appendix B: Best Practice Patterns Identified

### B.1 Thin Wrapper Pattern (observability-sdk)
Zero reimplementation; only adds domain-specific constants, SLO definitions, and health registrations. All other modules extending platform should follow this pattern.

### B.2 Composition Pattern (resilience-patterns)
`KernelResilienceFactory` explicitly composes platform primitives (`CircuitBreaker`, `Bulkhead`, `RetryPolicy`) with domain-appropriate parameters. No reimplementation.

### B.3 Extension Pattern (audit-trail)
Depends on `platform:java:audit` interface; implements domain-specific adapter (`PostgresAuditTrailStore` with SHA-256 hash chain) while preserving interface compatibility.

### B.4 Cross-Product Reuse Pattern (data-governance, dlq-management)
Depends on other products (`:products:data-cloud:platform`, `:products:aep:platform`) via SPI interfaces rather than coupling to internal implementations.
