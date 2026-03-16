# Consolidated Implementation Plan

**Status:** Active  
**Created:** 2026-03-16  
**Sources:** `CODE_REVIEW_ANALYSIS_REPORT.md` (Section 1–9), `UNIFIED_WORKFLOW_PLATFORM_DESIGN.md` (Sections 5–14)  
**Scope:** `platform/java/*`, `products/app-platform/*`, monorepo-wide fixes  
**Priority:** Security → Platform Foundation → Workflow Unification → Stubs → Testing → Migration

---

## Overview

This plan consolidates **every actionable item** from the Code Review Analysis Report and the Unified Workflow Platform Design into a single, dependency-ordered execution sequence. Work is organized into **8 phases**, each containing independently completable work items with clear acceptance criteria.

**Total work items: 52** across 8 phases.

### Phase Dependency Graph

```
Phase 1 (Security Fixes)
    │
    ▼
Phase 2 (Platform Foundation)
    │
    ▼
Phase 3 (Workflow Module 1 — Enhanced Core API)
    │
    ▼
Phase 4 (Workflow Module 2 — Durable Runtime)
    │
    ▼
Phase 5 (Workflow Module 3 — JDBC State Store)
    │
    ▼
Phase 6 (App-Platform Fixes — Stubs, Hardcodes, Redundancy)
    │
    ╠══▶ Phase 7 (App-Platform → Platform Workflow Migration)
    ║
    ╚══▶ Phase 8 (Test Coverage — Kernel + Domain Packs)
```

---

## Phase 1: Security Fixes (CRITICAL — Do First)

> **Goal:** Close all security vulnerabilities identified in the code review before any feature work.

### 1.1 JWT Validation: Add `nbf`, `iss`, `aud` Claim Checks

**Source:** Code Review §6.2  
**File:** `products/app-platform/kernel/api-gateway/src/main/java/com/ghatana/appplatform/gateway/JwtValidationFilter.java`  
**Severity:** HIGH

**Changes:**

- Validate `nbf` (not-before): reject tokens with `nbf` in the future
- Validate `iss` (issuer): reject tokens not issued by the platform's IAM (`iss` must match configured issuer URL)
- Validate `aud` (audience): reject tokens not intended for this service (`aud` must contain the gateway's audience identifier)
- Validate `jti` (JWT ID): integrate with Redis-backed replay cache to reject reused tokens

**Acceptance Criteria:**

- [ ] `nbf` validated — future tokens rejected with 401
- [ ] `iss` validated — wrong-issuer tokens rejected with 401
- [ ] `aud` validated — wrong-audience tokens rejected with 401
- [ ] `jti` replay detection via Redis (configurable TTL = token lifetime)
- [ ] Tests: `JwtValidationFilterTest` covering all 4 new checks + happy path
- [ ] JavaDoc with `@doc.*` tags

---

### 1.2 Internal Service Bypass: Fix Header Propagation

**Source:** Code Review §6.1  
**File:** `products/app-platform/kernel/api-gateway/src/main/java/com/ghatana/appplatform/gateway/InternalServiceBypassFilter.java`  
**Severity:** HIGH

**Changes:**

- Since ActiveJ's `HttpRequest` is immutable, use a request-scoped context attribute (e.g., `ThreadLocal<Map<String, String>>` or ActiveJ's `HttpRequest.withAttribute()`) to propagate the `X-Internal-Service` flag
- Downstream filters (`TokenBucketRateLimiter`, tenant session checker) read from this attribute instead of HTTP headers

**Acceptance Criteria:**

- [ ] Internal service JWTs bypass rate-limiting and session checks
- [ ] Context propagation works with ActiveJ async model (no ThreadLocal leaks)
- [ ] Tests: `InternalServiceBypassFilterTest` verifying bypass works end-to-end
- [ ] JavaDoc with `@doc.*` tags

---

### 1.3 HSM Key Operations: Enforce Production HSM Mode

**Source:** Code Review §2.3  
**File:** `products/app-platform/kernel/secrets-management/src/main/java/com/ghatana/appplatform/secrets/hsm/HsmKeyOperationsProvider.java`  
**Severity:** HIGH

**Changes:**

- Add a startup validation check: if environment is `production` or `staging`, `useHsm` MUST be `true`
- If `useHsm=false` in non-dev environments, throw `ConfigurationException` at startup (fail-fast)
- Log a WARN when using dev-stub mode in any environment

**Acceptance Criteria:**

- [ ] Production/staging deployments fail fast if not using HSM
- [ ] Dev mode logs clear warning
- [ ] Tests: `HsmKeyOperationsProviderTest` verifying fail-fast and dev-stub modes
- [ ] JavaDoc with `@doc.*` tags

---

### 1.4 Audit Gap: Emit Events for High-Sensitivity Operations

**Source:** Code Review §Mandate 4  
**Severity:** HIGH

**Operations missing audit events (6 total):**

| Operation                 | Module                                                              | What to Add                                       |
| ------------------------- | ------------------------------------------------------------------- | ------------------------------------------------- |
| Break-glass access grant  | `kernel/iam/security/BreakGlassService`                             | Emit `BREAK_GLASS_ACCESS_GRANTED` audit event     |
| Break-glass access revoke | `kernel/iam/security/BreakGlassService`                             | Emit `BREAK_GLASS_ACCESS_REVOKED` audit event     |
| Secret rotation           | `kernel/secrets-management/rotation/SecretRotationScheduler`        | Emit `SECRET_ROTATED` audit event                 |
| Config change             | `kernel/config-engine`                                              | Emit `CONFIG_CHANGED` audit event                 |
| Role grant/revoke         | `kernel/iam/rbac/`                                                  | Emit `ROLE_GRANTED` / `ROLE_REVOKED` audit events |
| Certificate renewal       | `kernel/secrets-management/certificate/CertificateLifecycleService` | Emit `CERTIFICATE_RENEWED` audit event            |

**Acceptance Criteria:**

- [ ] Each operation emits a structured audit event via `AuditService` from `platform:java:audit`
- [ ] Events include: actor, tenantId, timestamp, resource, action, outcome
- [ ] Tests for each audit emission point

---

## Phase 2: Platform Foundation (Infrastructure)

> **Goal:** Establish shared infrastructure that all subsequent phases depend on.

### 2.1 Shared `PlatformObjectMapper` in `platform:java:core`

**Source:** Code Review §4.1, §8.1  
**Severity:** MEDIUM

**File to create:** `platform/java/core/src/main/java/com/ghatana/platform/core/json/PlatformObjectMapper.java`

**Changes:**

- Create a thread-safe, pre-configured `ObjectMapper` singleton with:
  - `JavaTimeModule` (Instant, LocalDate, ZonedDateTime)
  - `Jdk8Module` (Optional)
  - `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false`
  - `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS = false`
- Expose via `PlatformObjectMapper.instance()` static method
- Add `PlatformObjectMapper.copy()` for cases needing customization

**Acceptance Criteria:**

- [ ] Singleton ObjectMapper in `platform:java:core`
- [ ] Pre-registers JavaTimeModule, Jdk8Module
- [ ] Tests: `PlatformObjectMapperTest` verifying Instant, LocalDate, Optional serialization
- [ ] JavaDoc with `@doc.*` tags

---

### 2.2 Shared `DataSourceFactory` in `platform:java:database`

**Source:** Code Review §5.1, §7.5, §8.4  
**Severity:** MEDIUM

**File to create:** `platform/java/database/src/main/java/com/ghatana/platform/database/DataSourceFactory.java`

**Changes:**

- Factory method: `DataSourceFactory.create(DataSourceConfig)` returns `javax.sql.DataSource`
- Production: wraps `HikariDataSource` with proper lifecycle management (Closeable)
- Registers shutdown hook on ActiveJ eventloop for clean pool closure
- All service constructors that accept `HikariDataSource` should accept `DataSource` instead (tracked in Phase 6)

**Acceptance Criteria:**

- [ ] Factory returns `javax.sql.DataSource` (not HikariDataSource)
- [ ] Lifecycle: pool closed on eventloop shutdown
- [ ] Config record: `DataSourceConfig(url, username, password, maxPoolSize, connectionTimeout)`
- [ ] Tests: `DataSourceFactoryTest` verifying lifecycle and config
- [ ] JavaDoc with `@doc.*` tags

---

### 2.3 Shared `RedisClientFactory` in `platform:java:database`

**Source:** Code Review §4.2  
**Severity:** LOW-MEDIUM

**File to create:** `platform/java/database/src/main/java/com/ghatana/platform/database/RedisClientFactory.java`

**Changes:**

- Centralized `JedisPool` creation with configurable pool settings
- Products obtain `JedisPool` from factory instead of creating their own
- Lifecycle-managed: pool closed on eventloop shutdown

**Acceptance Criteria:**

- [ ] Single factory for JedisPool creation
- [ ] Config record: `RedisConfig(host, port, password, maxTotal, maxIdle, timeout)`
- [ ] Tests: `RedisClientFactoryTest`
- [ ] JavaDoc with `@doc.*` tags

---

### 2.4 Shared `AuditBusPort` Interface — Replace 15 Duplicates

**Source:** Code Review §8.8, Mandate 4  
**Severity:** MEDIUM

**File to create:** `platform/java/audit/src/main/java/com/ghatana/platform/audit/AuditBusPort.java`

**Changes:**

- Define a single `AuditBusPort` interface in `platform:java:audit` (if not already there via `AuditService`)
- All 15 inner `AuditPort` definitions in kernel + domain-packs should be replaced by this shared port
- Tracked per-module replacement in Phase 6

**Acceptance Criteria:**

- [ ] Single canonical `AuditBusPort` in platform:java:audit
- [ ] Signature: `void emit(AuditEvent event)` (or uses existing `AuditService.record()`)
- [ ] JavaDoc with `@doc.*` tags

---

### 2.5 `build.properties` Auto-Generation for SDK Metadata

**Source:** Code Review §3.2, §8.5  
**Severity:** LOW-MEDIUM

**Changes:**

- Add Gradle `processResources` task to `platform:java:core` (or a dedicated conventions plugin) that writes `platform.version` and `sdk.version` from Gradle project properties into `META-INF/platform.properties`
- `SdkCoreAbstractionsService` reads from this properties file at startup instead of hardcoded `"1.0.0"`

**Acceptance Criteria:**

- [ ] `platform.properties` generated at build time
- [ ] `SdkCoreAbstractionsService.PLATFORM_VERSION` / `SDK_VERSION` read from file
- [ ] KernelTracingInstrumentation tracer version also reads from properties
- [ ] Tests verifying properties file loading

---

## Phase 3: Workflow Module 1 — Enhanced `platform:java:workflow`

> **Goal:** Add unified types to the existing workflow module per the Unified Workflow Platform Design §5.

### 3.1 `WorkflowKind` Enum

**Source:** Design §5.2  
**Package:** `com.ghatana.platform.workflow`

```java
public enum WorkflowKind { EPHEMERAL, DURABLE }
```

**Acceptance Criteria:**

- [ ] Enum with JavaDoc and `@doc.*` tags
- [ ] Test: `WorkflowKindTest`

---

### 3.2 `WorkflowRunStatus` Enum

**Source:** Design §5.2  
**Package:** `com.ghatana.platform.workflow`

```java
public enum WorkflowRunStatus {
    PENDING, RUNNING, WAITING, SUSPENDED, COMPLETED, FAILED, CANCELLED, COMPENSATING, COMPENSATED
}
```

**Acceptance Criteria:**

- [ ] Enum with all 9 states
- [ ] JavaDoc describing each state's meaning and valid transitions
- [ ] Test: `WorkflowRunStatusTest`

---

### 3.3 `WorkflowOptions` Record

**Source:** Design §5.2  
**Package:** `com.ghatana.platform.workflow`

```java
public record WorkflowOptions(
    @NotNull WorkflowKind kind,
    @Nullable Duration timeout,
    int maxRetries,
    @NotNull SagaPolicy sagaPolicy
) {
    public static WorkflowOptions ephemeral() { ... }
    public static WorkflowOptions durable() { ... }
}
```

Includes `SagaPolicy` enum: `NONE`, `FORWARD_RECOVERY`, `BACKWARD_COMPENSATION`.

**Acceptance Criteria:**

- [ ] Record with validation (maxRetries >= 0, timeout > 0 if present)
- [ ] Factory methods for common configurations
- [ ] Test: `WorkflowOptionsTest`
- [ ] JavaDoc with `@doc.*` tags

---

### 3.4 `WorkflowLifecycleEvent` Record

**Source:** Design §5.2  
**Package:** `com.ghatana.platform.workflow`

```java
public record WorkflowLifecycleEvent(
    @NotNull String runId,
    @NotNull String workflowId,
    @NotNull Phase phase,
    @Nullable String stepId,
    @NotNull Instant timestamp,
    @Nullable String tenantId,
    @Nullable String correlationId,
    @Nullable String errorMessage,
    @Nullable String actorId,
    @NotNull Map<String, Object> attributes
) {
    public enum Phase {
        WORKFLOW_CREATED, WORKFLOW_STARTED, STEP_STARTED, STEP_COMPLETED,
        STEP_FAILED, STEP_RETRYING, WORKFLOW_WAITING, WORKFLOW_RESUMED,
        WORKFLOW_COMPLETED, WORKFLOW_FAILED, WORKFLOW_CANCELLED,
        WORKFLOW_SUSPENDED, WORKFLOW_COMPENSATING, WORKFLOW_COMPENSATED
    }
}
```

**Acceptance Criteria:**

- [ ] Immutable record with all fields
- [ ] `attributes` is an unmodifiable copy
- [ ] Test: `WorkflowLifecycleEventTest`
- [ ] JavaDoc with `@doc.*` tags

---

### 3.5 `WorkflowLifecycleListener` Interface

**Source:** Design §5.2  
**Package:** `com.ghatana.platform.workflow`

```java
@FunctionalInterface
public interface WorkflowLifecycleListener {
    void onEvent(WorkflowLifecycleEvent event);
}
```

**Acceptance Criteria:**

- [ ] Functional interface
- [ ] JavaDoc with `@doc.*` tags

---

### 3.6 `WorkflowRun` Record

**Source:** Design §5.2  
**Package:** `com.ghatana.platform.workflow`

```java
public record WorkflowRun(
    @NotNull String runId,
    @NotNull String workflowId,
    @Nullable String tenantId,
    @NotNull WorkflowKind kind,
    @NotNull WorkflowRunStatus status,
    @NotNull WorkflowOptions options,
    @NotNull Instant startedAt,
    @Nullable Instant completedAt,
    @NotNull String currentStepId,
    @NotNull Map<String, Object> variables,
    @Nullable String errorMessage,
    @Nullable String triggeredBy,
    @NotNull List<WorkflowLifecycleEvent> history
) {}
```

**Acceptance Criteria:**

- [ ] Immutable record with defensive copies for `variables` and `history`
- [ ] Test: `WorkflowRunTest`
- [ ] JavaDoc with `@doc.*` tags

---

### 3.7 `WorkflowStateStore` SPI

**Source:** Design §5.2  
**Package:** `com.ghatana.platform.workflow`

```java
public interface WorkflowStateStore {
    Promise<Void> save(@NotNull WorkflowRun run);
    Promise<Optional<WorkflowRun>> findByRunId(@NotNull String runId);
    Promise<List<WorkflowRun>> findByWorkflowId(@NotNull String workflowId);
    Promise<List<WorkflowRun>> findByStatus(@NotNull WorkflowRunStatus status);
    Promise<List<WorkflowRun>> findExpiredWaits(@NotNull Instant cutoff);
    Promise<Void> updateStatus(@NotNull String runId, @NotNull WorkflowRunStatus status);
    void delete(@NotNull String runId);
}
```

**Acceptance Criteria:**

- [ ] All methods return `Promise` (ActiveJ async)
- [ ] `@NotNull` annotations on all parameters
- [ ] Test: `InMemoryWorkflowStateStoreTest` (in-memory impl for testing)
- [ ] JavaDoc with `@doc.*` tags

---

### 3.8 `WorkflowExpressionEvaluator` SPI

**Source:** Design §5.2  
**Package:** `com.ghatana.platform.workflow`

```java
public interface WorkflowExpressionEvaluator {
    Object evaluate(@NotNull String expression, @NotNull WorkflowContext context);
    boolean evaluateBoolean(@NotNull String expression, @NotNull WorkflowContext context);
    void validate(@NotNull String expression);
}
```

**Acceptance Criteria:**

- [ ] SPI interface
- [ ] `validate()` throws `WorkflowDefinitionException` on invalid expressions
- [ ] JavaDoc with `@doc.*` tags

---

### 3.9 `WorkflowWaitCoordinator` SPI

**Source:** Design §5.2  
**Package:** `com.ghatana.platform.workflow`

```java
public interface WorkflowWaitCoordinator {
    Promise<Void> registerWait(@NotNull String runId, @NotNull WaitCondition condition);
    Promise<Boolean> signal(@NotNull String runId, @NotNull String signalName, @Nullable Map<String, Object> payload);
    Promise<List<String>> findFirableWaits(@NotNull Instant now);
    void cancel(@NotNull String runId);
}
```

With `WaitCondition` record:

```java
public record WaitCondition(
    @NotNull WaitKind kind,          // EVENT, TIMER, MANUAL
    @Nullable String eventType,      // for EVENT waits
    @Nullable String correlationKey, // for EVENT waits
    @Nullable Duration timeout,      // for TIMER waits
    @Nullable Instant fireAt         // computed from timeout
) {}
```

**Acceptance Criteria:**

- [ ] SPI interface with `WaitCondition` value object
- [ ] JavaDoc with `@doc.*` tags

---

### 3.10 `WorkflowDefinitionException`

**Source:** Design §5.2  
**Package:** `com.ghatana.platform.workflow`

```java
public class WorkflowDefinitionException extends RuntimeException {
    public WorkflowDefinitionException(String message) { super(message); }
    public WorkflowDefinitionException(String message, Throwable cause) { super(message, cause); }
}
```

**Acceptance Criteria:**

- [ ] Extends `RuntimeException`
- [ ] Two constructors
- [ ] JavaDoc with `@doc.*` tags

---

### 3.11 Update `DurableWorkflowEngine` to Emit Lifecycle Events

**Source:** Design §5, Roadmap Sprint 1  
**File:** `platform/java/workflow/src/main/java/com/ghatana/platform/workflow/engine/DurableWorkflowEngine.java`

**Changes:**

- Add `List<WorkflowLifecycleListener> listeners` field
- Add `addListener(WorkflowLifecycleListener)` method
- Emit events at: workflow start, step start/complete/fail, workflow complete/fail
- Events are fire-and-forget (listener exceptions are logged, not propagated)

**Acceptance Criteria:**

- [ ] Lifecycle events emitted at all significant phases
- [ ] Listener exceptions isolated (logged, not propagated)
- [ ] Backward compatible (no changes to existing public API)
- [ ] Test: `DurableWorkflowEngineLifecycleTest`

---

### 3.12 `InMemoryWorkflowStateStore` (Test Support + Default Impl)

**Source:** Design Roadmap Sprint 1  
**Package:** `com.ghatana.platform.workflow`

**Changes:**

- Implement `WorkflowStateStore` backed by `ConcurrentHashMap`
- Useful for ephemeral workflows and testing

**Acceptance Criteria:**

- [ ] All `WorkflowStateStore` methods implemented
- [ ] Thread-safe via ConcurrentHashMap
- [ ] Test: `InMemoryWorkflowStateStoreTest`
- [ ] JavaDoc with `@doc.*` tags

---

## Phase 4: Workflow Module 2 — `platform:java:workflow-runtime` (NEW)

> **Goal:** Create the durable FSM runtime module, generalised from app-platform's workflow-orchestration.

### 4.1 Module Scaffold

**Source:** Design §6.1

**Create:**

- `platform/java/workflow-runtime/build.gradle.kts`
- Package structure under `com.ghatana.platform.workflow.runtime`
- Add to root `settings.gradle.kts`: `include(":platform:java:workflow-runtime")`

**build.gradle.kts:**

```kotlin
plugins { id("java-library") }
group = "com.ghatana.platform"
version = "1.0.0-SNAPSHOT"
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
dependencies {
    api(project(":platform:java:workflow"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:observability"))
    implementation(libs.activej.promise)
    implementation(libs.activej.common)
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.test { useJUnitPlatform() }
```

**Acceptance Criteria:**

- [ ] Module compiles
- [ ] Included in settings.gradle.kts
- [ ] Package structure created

---

### 4.2 Workflow Definition Types

**Source:** Design §6.4  
**Package:** `com.ghatana.platform.workflow.runtime.definition`

**Create:**

- `WorkflowStepKind` enum: `TASK`, `DECISION`, `PARALLEL`, `WAIT`, `SUB_WORKFLOW`, `OPERATOR_PIPELINE`
- `WorkflowTriggerType` enum: `EVENT`, `SCHEDULE`, `MANUAL`, `API_WEBHOOK`
- `WorkflowStepDefinition` record: step DSL node with all fields from Design §6.4
- `WorkflowDefinition` record: versioned workflow blueprint

**Acceptance Criteria:**

- [ ] All types match Design §6.4 specification exactly
- [ ] `WorkflowStepDefinition.ParallelJoinStrategy`: `ALL`, `FIRST`, `N_OF_M`
- [ ] Tests for all records (validation, immutability)
- [ ] JavaDoc with `@doc.*` tags

---

### 4.3 `WorkflowDefinitionRegistry` SPI + In-Memory Impl

**Source:** Design §6.5  
**Package:** `com.ghatana.platform.workflow.runtime.definition`

**Create:**

- `WorkflowDefinitionRegistry` interface with: `register`, `findActive`, `findByVersion`, `listVersions`
- `InMemoryWorkflowDefinitionRegistry` implementation

**Acceptance Criteria:**

- [ ] SPI interface
- [ ] In-memory impl for testing/ephemeral use
- [ ] Tests: `InMemoryWorkflowDefinitionRegistryTest`
- [ ] JavaDoc with `@doc.*` tags

---

### 4.4 `DurableWorkflowRuntime` — Core FSM Engine

**Source:** Design §6.3  
**Package:** `com.ghatana.platform.workflow.runtime`

**Create:**

- Builder pattern construction
- FSM: `PENDING → RUNNING → (WAITING ↔ RUNNING) → COMPLETED | FAILED | CANCELLED | COMPENSATED`
- Methods: `launch()`, `resume()`, `cancel()`, `suspend()`, `signal()`
- Step dispatch: resolves `WorkflowStepDefinition.taskRef` from `DefaultStepRegistry`
- Lifecycle event emission at every state transition

**Acceptance Criteria:**

- [ ] All FSM transitions implemented correctly
- [ ] Wires: `WorkflowStateStore`, `WorkflowExpressionEvaluator`, `WorkflowWaitCoordinator`, `StepRegistry`
- [ ] All operations return `Promise` (ActiveJ async)
- [ ] Tests: `DurableWorkflowRuntimeTest` covering all transitions
- [ ] JavaDoc with `@doc.*` tags

---

### 4.5 `DefaultStepRegistry`

**Source:** Design §6.3  
**Package:** `com.ghatana.platform.workflow.runtime`

```java
public final class DefaultStepRegistry {
    public void register(@NotNull String taskRef, @NotNull WorkflowStep step);
    public Optional<WorkflowStep> resolve(@NotNull String taskRef);
}
```

**Acceptance Criteria:**

- [ ] Thread-safe registration
- [ ] Tests: `DefaultStepRegistryTest`
- [ ] JavaDoc with `@doc.*` tags

---

### 4.6 `CelWorkflowExpressionEvaluator`

**Source:** Design §6.2  
**Package:** `com.ghatana.platform.workflow.runtime.step`

**Changes:**

- Implement `WorkflowExpressionEvaluator` using Google CEL (Common Expression Language)
- Extract and generalise from app-platform's `CelExpressionEvaluatorService`
- Support custom function registration for product-specific extensions

**Acceptance Criteria:**

- [ ] Evaluates CEL expressions against `WorkflowContext` variables
- [ ] `validate()` parsers expression and reports errors
- [ ] Tests: `CelWorkflowExpressionEvaluatorTest` with various CEL expressions
- [ ] JavaDoc with `@doc.*` tags

---

### 4.7 `ParallelStepExecutor`

**Source:** Design §6.2  
**Package:** `com.ghatana.platform.workflow.runtime.step`

**Changes:**

- Execute multiple steps concurrently using `Promises.toList()`
- Support join strategies: `ALL`, `FIRST`, `N_OF_M`
- Context merging from parallel branches

**Acceptance Criteria:**

- [ ] All join strategies implemented
- [ ] Failure handling: configurable (FAIL_FAST, CONTINUE, COMPENSATE)
- [ ] Tests: `ParallelStepExecutorTest`
- [ ] JavaDoc with `@doc.*` tags

---

### 4.8 `WaitStepEngine`

**Source:** Design §6.2  
**Package:** `com.ghatana.platform.workflow.runtime.step`

**Changes:**

- Handles WAIT steps: delegates to `WorkflowWaitCoordinator`
- Transitions workflow to `WAITING` state
- Resumes on signal or timeout

**Acceptance Criteria:**

- [ ] Registers waits via coordinator
- [ ] Resumes workflow on signal
- [ ] Timeout handling
- [ ] Tests: `WaitStepEngineTest`
- [ ] JavaDoc with `@doc.*` tags

---

### 4.9 `SubWorkflowComposer`

**Source:** Design §6.2  
**Package:** `com.ghatana.platform.workflow.runtime.step`

**Changes:**

- Handles `SUB_WORKFLOW` steps: launches child workflow, optionally waits for completion
- Max depth enforcement (default: 5)
- Context passing: parent → child (configurable variable subset)

**Acceptance Criteria:**

- [ ] SYNC and ASYNC child invocation modes
- [ ] Max depth enforcement
- [ ] Tests: `SubWorkflowComposerTest`
- [ ] JavaDoc with `@doc.*` tags

---

### 4.10 `PipelineWorkflowStepAdapter` — Ephemeral/Durable Bridge

**Source:** Design §8.1  
**Package:** `com.ghatana.platform.workflow.runtime.step`

**Changes:**

- Wraps an ephemeral `Pipeline` as a `WorkflowStep` for use in durable workflows
- `OPERATOR_PIPELINE` step kind resolves to this adapter

**Acceptance Criteria:**

- [ ] Ephemeral pipeline runs inside durable step
- [ ] Error propagation from pipeline to workflow FSM
- [ ] Tests: `PipelineWorkflowStepAdapterTest`
- [ ] JavaDoc with `@doc.*` tags

---

### 4.11 Retry and Error Policies

**Source:** Design §6.2  
**Package:** `com.ghatana.platform.workflow.runtime.policy`

**Create:**

- `WorkflowRetryPolicy`: configurable retry with exponential backoff, max attempts
- `WorkflowErrorPolicyEngine`: catch/finally/compensation DSL execution

**Acceptance Criteria:**

- [ ] Retry with backoff (configurable: initial delay, multiplier, max delay, max attempts)
- [ ] Error policies: CATCH (handle + continue), FINALLY (always execute), COMPENSATE (saga rollback)
- [ ] Tests: `WorkflowRetryPolicyTest`, `WorkflowErrorPolicyEngineTest`
- [ ] JavaDoc with `@doc.*` tags

---

### 4.12 `MetricsWorkflowListener`

**Source:** Design §13.1  
**Package:** `com.ghatana.platform.workflow.runtime`

**Changes:**

- Implements `WorkflowLifecycleListener`
- Emits Micrometer metrics:
  - `workflow.run.started_total`, `workflow.run.completed_total`, `workflow.run.failed_total`
  - `workflow.run.duration_seconds` (histogram p50, p95, p99)
  - `workflow.step.duration_seconds` (per step)
  - `workflow.step.retry_total`, `workflow.wait.registered_total`
  - `workflow.run.active_count` (gauge)

**Acceptance Criteria:**

- [ ] All metrics from Design §13.1 emitted
- [ ] Tags: workflowId, kind, tenantId, stepId, stepKind
- [ ] Tests: `MetricsWorkflowListenerTest`
- [ ] JavaDoc with `@doc.*` tags

---

### 4.13 `AuditWorkflowListener`

**Source:** Design §13.2  
**Package:** `com.ghatana.platform.workflow.runtime`

**Changes:**

- Implements `WorkflowLifecycleListener`
- Emits audit records via `AuditService` from `platform:java:audit`
- Events: WORKFLOW_STARTED, WORKFLOW_COMPLETED, WORKFLOW_FAILED, WORKFLOW_CANCELLED, WORKFLOW_WAIT_REGISTERED, WORKFLOW_RESUMED, WORKFLOW_COMPENSATING, WORKFLOW_COMPENSATED

**Acceptance Criteria:**

- [ ] All audit events from Design §13.2 emitted
- [ ] Includes: actor, workflowId, runId, tenantId, trigger
- [ ] Tests: `AuditWorkflowListenerTest`
- [ ] JavaDoc with `@doc.*` tags

---

## Phase 5: Workflow Module 3 — `platform:java:workflow-jdbc` (NEW)

> **Goal:** PostgreSQL implementations of all workflow SPIs.

### 5.1 Module Scaffold

**Source:** Design §7.1

**Create:**

- `platform/java/workflow-jdbc/build.gradle.kts`
- Package structure under `com.ghatana.platform.workflow.jdbc`
- Add to root `settings.gradle.kts`: `include(":platform:java:workflow-jdbc")`

**build.gradle.kts:**

```kotlin
plugins { id("java-library") }
group = "com.ghatana.platform"
version = "1.0.0-SNAPSHOT"
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
dependencies {
    api(project(":platform:java:workflow"))
    api(project(":platform:java:workflow-runtime"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:database"))
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.test { useJUnitPlatform() }
```

**Acceptance Criteria:**

- [ ] Module compiles
- [ ] Included in settings.gradle.kts

---

### 5.2 DDL Schema (Flyway Migration)

**Source:** Design §7.3  
**File:** `platform/java/workflow-jdbc/src/main/resources/db/migration/V001__create_workflow_schema.sql`

**Tables:**

- `workflow_definition` — versioned workflow blueprints
- `workflow_run` — execution instances
- `workflow_step_state` — per-step state within a run
- `workflow_wait_correlation` — WAIT step registrations
- `workflow_lifecycle_event` — audit/event log per run

**Acceptance Criteria:**

- [ ] All 5 tables created per Design §7.3
- [ ] Proper indexes for query patterns (status, tenant, workflow_id, fire_at)
- [ ] Foreign key constraints
- [ ] Multi-tenant: `tenant_id` column on all tables

---

### 5.3 `JdbcWorkflowStateStore`

**Source:** Design §7.2  
**Package:** `com.ghatana.platform.workflow.jdbc`

**Changes:**

- Implements `WorkflowStateStore` backed by `workflow_run` + `workflow_step_state` tables
- Uses `javax.sql.DataSource` (not HikariDataSource directly)
- Async: wraps blocking JDBC in `Promise.ofBlocking(executor, ...)`

**Acceptance Criteria:**

- [ ] All `WorkflowStateStore` methods implemented
- [ ] Uses parameterized queries (no SQL injection)
- [ ] Tests: `JdbcWorkflowStateStoreTest` with TestContainers PostgreSQL
- [ ] JavaDoc with `@doc.*` tags

---

### 5.4 `JdbcWorkflowDefinitionRegistry`

**Source:** Design §7.2  
**Package:** `com.ghatana.platform.workflow.jdbc`

**Changes:**

- Implements `WorkflowDefinitionRegistry` backed by `workflow_definition` table
- Version management: DRAFT → ACTIVE → DEPRECATED

**Acceptance Criteria:**

- [ ] All `WorkflowDefinitionRegistry` methods implemented
- [ ] Version lifecycle enforced
- [ ] Tests: `JdbcWorkflowDefinitionRegistryTest` with TestContainers PostgreSQL
- [ ] JavaDoc with `@doc.*` tags

---

### 5.5 `JdbcWorkflowWaitCoordinator`

**Source:** Design §7.2  
**Package:** `com.ghatana.platform.workflow.jdbc`

**Changes:**

- Implements `WorkflowWaitCoordinator` backed by `workflow_wait_correlation` table
- Scheduled poller for timer-based waits (using `findFirableWaits`)

**Acceptance Criteria:**

- [ ] All `WorkflowWaitCoordinator` methods implemented
- [ ] Timer-based resume via polling
- [ ] Tests: `JdbcWorkflowWaitCoordinatorTest` with TestContainers PostgreSQL
- [ ] JavaDoc with `@doc.*` tags

---

## Phase 6: App-Platform Fixes (Stubs, Hardcodes, Redundancy)

> **Goal:** Fix all production stubs, hardcoded values, and code redundancy identified in the Code Review.

### 6.1 Replace `SdkCoreAbstractionsService.resolveEndpointsBlocking()` Stub

**Source:** Code Review §2.1, §8.6  
**File:** `kernel/platform-sdk/.../SdkCoreAbstractionsService.java`

**Changes:**

- Replace hardcoded JSON with a real `ServiceRegistryPort` interface
- Implementation reads from config-engine (K-02) config keys
- Fallback: configurable default endpoints via environment variables

**Acceptance Criteria:**

- [ ] `ServiceRegistryPort` interface defined
- [ ] Config-backed implementation reads from K-02
- [ ] Tests: `SdkCoreAbstractionsServiceTest` verifying config-based resolution

---

### 6.2 Implement `CertificateLifecycleService.renew()`

**Source:** Code Review §2.2  
**File:** `kernel/secrets-management/.../CertificateLifecycleService.java`

**Changes:**

- Replace `return null` with a `CertificateRenewalPort` interface
- Default implementation calls Vault PKI `pki/issue/{role}` endpoint
- Emits `CERTIFICATE_RENEWED` audit event (connected from Phase 1.4)

**Acceptance Criteria:**

- [ ] `CertificateRenewalPort` SPI with Vault PKI adapter
- [ ] Audit event emitted on successful renewal
- [ ] Tests: `CertificateLifecycleServiceTest`

---

### 6.3 Implement `GeoIpResolver` Real Adapter

**Source:** Code Review §2.4  
**File:** `kernel/iam/.../GeoIpResolver.java`

**Changes:**

- Create `MaxMindGeoIpResolver` implementing `GeoIpResolver`
- Uses MaxMind GeoIP2 database (embedded or remote API)
- `InMemoryGeoIpResolver` remains as test/dev fallback

**Acceptance Criteria:**

- [ ] MaxMind adapter resolves IP → coordinates
- [ ] Graceful degradation: returns null on lookup failure (doesn't crash)
- [ ] Tests: `MaxMindGeoIpResolverTest`

---

### 6.4 Replace 25 ObjectMapper Instances

**Source:** Code Review §4.1

**Changes:**

- In each of the 25 affected classes, replace `new ObjectMapper()` with `PlatformObjectMapper.instance()`
- For classes needing `JavaTimeModule` already: still switch to shared instance (it includes it)
- For `KafkaEventPublisher` (2 constructors): consolidate to single mapper reference

**Affected Modules:** event-store, market-data, reference-data, sanctions, compliance, oms, ems, pms, post-trade, pricing, corporate-actions, reconciliation, regulatory-reporting, risk-engine, surveillance, plugin-runtime, config-engine, iam, secrets-management, platform-sdk

**Acceptance Criteria:**

- [ ] All 25 `new ObjectMapper()` replaced with `PlatformObjectMapper.instance()`
- [ ] No module has its own ObjectMapper instantiation
- [ ] Existing tests still pass (serialization behavior unchanged)

---

### 6.5 Standardize `DataSource` Typing (163 Files)

**Source:** Code Review §5.1, §7.5

**Changes:**

- Replace `HikariDataSource` constructor parameters with `javax.sql.DataSource` across 163 files
- Use `DataSourceFactory` (from Phase 2.2) at composition roots
- **Incremental**: Start with kernel modules, then domain packs

**Acceptance Criteria:**

- [ ] All service constructors accept `javax.sql.DataSource`
- [ ] `HikariDataSource` used only in `DataSourceFactory` implementation
- [ ] Existing tests still pass

---

### 6.6 Replace Raw `ScheduledExecutorService` with ActiveJ Scheduling

**Source:** Code Review §5.2, §8.3

**Affected classes (8):**

- `ems/FixProtocolService` — heartbeat scheduler
- `market-data/MarketDataFeedAdapterRegistry` — polling scheduler
- `sanctions/BatchReScreeningService` — re-screening scheduler
- `reference-data/RefDataFeedAdapterRegistry` — polling scheduler
- `kernel/dlq-management/BulkReplayService`
- `kernel/event-store/SagaTimeoutMonitor`
- `kernel/event-store/KafkaEventOutboxRelay`
- `kernel/secrets-management/SecretRotationScheduler`

**Changes:**

- Replace `Executors.newSingleThreadScheduledExecutor()` with ActiveJ `Eventloop.schedule()` or `Eventloop.delay()`
- Register in ActiveJ's lifecycle for proper shutdown

**Acceptance Criteria:**

- [ ] All 8 classes use ActiveJ-managed scheduling
- [ ] Proper shutdown on eventloop stop
- [ ] Tests verifying scheduled tasks execute and stop cleanly

---

### 6.7 FIX Protocol: Make Session Parameters Configurable

**Source:** Code Review §3.4, §8.7  
**File:** `domain-packs/ems/.../FixProtocolService.java`

**Changes:**

- Replace hardcoded `HEARTBEAT_INTERVAL_SECONDS = 30` and `SESSION_TIMEOUT_SECONDS = 60` with config-engine lookups
- Per-counterparty configuration via config keys

**Acceptance Criteria:**

- [ ] Session params read from config
- [ ] Per-counterparty override support
- [ ] Tests: `FixProtocolServiceTest`

---

### 6.8 Replace AuditPort Duplicates with Shared `AuditBusPort`

**Source:** Code Review §8.8

**Changes:**

- In each of the 15 modules with inner `AuditPort` interfaces, replace with the shared `AuditBusPort` from `platform:java:audit` (created in Phase 2.4)
- Update constructors and wiring

**Acceptance Criteria:**

- [ ] Zero inner `AuditPort` definitions remain
- [ ] All modules use `AuditBusPort` from platform:java:audit
- [ ] Existing audit behavior preserved

---

### 6.9 Wire Domain Packs to Platform Event Bus (Replace `Consumer<Object>`)

**Source:** Code Review §7.6, Mandate 1

**Changes:**

- Replace `Consumer<Object> eventPublisher` parameters in 3 domain-pack services with typed `EventCloud` or `EventBusPort` from platform
- Events go through schema validation and Kafka outbox

**Acceptance Criteria:**

- [ ] No `Consumer<Object>` event publishers remain
- [ ] Events published via `EventCloud.append()` or platform EventBusPort
- [ ] Schema validation active

---

### 6.10 `ai-governance` Dependency Fix

**Source:** Code Review Mandate 4  
**File:** `kernel/ai-governance/build.gradle.kts`

**Changes:**

- Add missing `implementation(project(":platform:java:governance"))` dependency
- Wire `PolicyEngine` from platform governance into ai-governance services

**Acceptance Criteria:**

- [ ] Dependency added
- [ ] PolicyEngine integrated
- [ ] Tests verifying policy evaluation

---

### 6.11 `data-governance` Mandate Violation Fix

**Source:** Code Review Mandate 2  
**File:** `kernel/data-governance/`

**Changes:**

- Replace direct JDBC (HikariDataSource, Connection, PreparedStatement) in all 7 service implementations with `data-cloud` platform APIs
- PII masking → data-cloud masking API
- Lineage tracking → data-cloud lineage API
- Data catalog → data-cloud catalog API

**Acceptance Criteria:**

- [ ] Zero direct JDBC in data-governance
- [ ] All data operations go through data-cloud platform
- [ ] Tests verifying data-cloud integration

---

## Phase 7: App-Platform → Platform Workflow Migration

> **Goal:** Migrate app-platform's workflow-orchestration to use the new platform workflow modules.

### 7.1 Add Platform Workflow Dependencies

**Source:** Design §9.2  
**File:** `products/app-platform/kernel/workflow-orchestration/build.gradle.kts`

**Changes:**

```kotlin
implementation(project(":platform:java:workflow-runtime"))
implementation(project(":platform:java:workflow-jdbc"))
```

**Acceptance Criteria:**

- [ ] Dependencies added and compile

---

### 7.2 Migrate WorkflowDefinitionService → JdbcWorkflowDefinitionRegistry

**Source:** Design §9.2, Phase 2 Step 1

**Changes:**

- Replace `WorkflowDefinitionService` internals with calls to `JdbcWorkflowDefinitionRegistry`
- Keep the service class as a thin facade for app-platform-specific behavior
- Move YAML/JSON DSL parsing to use `WorkflowDefinition` record from workflow-runtime

**Acceptance Criteria:**

- [ ] Definitions stored via `JdbcWorkflowDefinitionRegistry`
- [ ] Existing YAML/JSON schemas unchanged (backward compatible)
- [ ] Tests: `WorkflowDefinitionMigrationTest`

---

### 7.3 Migrate CelExpressionEvaluatorService → Platform CelWorkflowExpressionEvaluator

**Source:** Design §9.2, Phase 2 Step 2

**Acceptance Criteria:**

- [ ] App-platform's CEL evaluation delegates to platform `CelWorkflowExpressionEvaluator`
- [ ] Custom functions still registered (product-specific extensions preserved)

---

### 7.4 Migrate WorkflowExecutionRuntimeService → DurableWorkflowRuntime

**Source:** Design §9.2, Phase 2 Step 3

**Changes:**

- Wire existing PostgreSQL tables via `JdbcWorkflowStateStore`
- Delegate step dispatch to `DurableWorkflowRuntime`
- Register platform lifecycle listeners (`MetricsWorkflowListener`, `AuditWorkflowListener`)

**Acceptance Criteria:**

- [ ] All workflow execution goes through platform `DurableWorkflowRuntime`
- [ ] Existing workflow instances continue to work (data migration for running instances)
- [ ] Tests: `WorkflowExecutionMigrationTest`

---

### 7.5 Migrate Remaining Services

**Source:** Design §9.2, Phase 2 Steps 4–8

| App-Platform Service            | Replacement                                         |
| ------------------------------- | --------------------------------------------------- |
| `WaitCorrelationStepService`    | `JdbcWorkflowWaitCoordinator`                       |
| `ParallelStepExecutionService`  | `ParallelStepExecutor`                              |
| `SubWorkflowCompositionService` | `SubWorkflowComposer`                               |
| Inner `AuditPort` duplicates    | `AuditWorkflowListener` on `DurableWorkflowRuntime` |
| Inner `EventBusPort` duplicates | `WorkflowLifecycleListener` → AEP event bus         |

**Acceptance Criteria:**

- [ ] All 5 services migrated
- [ ] Existing behavior preserved
- [ ] Old service classes either removed or thin facades

---

### 7.6 Register Business Steps in StepRegistry

**Source:** Design §9.3

**Changes:**

- At app-platform startup, register all domain workflow steps in `DefaultStepRegistry`:
  - `kyc.verify`, `trade.dvp`, `ledger.post`, `notification.send`, etc.
- Construct `DurableWorkflowRuntime` with all dependencies wired

**Acceptance Criteria:**

- [ ] All existing taskRef values resolve via StepRegistry
- [ ] No broken step references

---

## Phase 8: Test Coverage — Kernel + Domain Packs

> **Goal:** Achieve meaningful test coverage across all untested modules.

### 8.1 Kernel Module Tests (15 Untested Modules)

**Source:** Code Review §1.2

For each module, create test files following the `EventloopTestBase` pattern:

| Module                     | Priority           | Minimum Tests                                                 |
| -------------------------- | ------------------ | ------------------------------------------------------------- |
| **api-gateway**            | HIGH (security)    | JwtValidationFilter, InternalServiceBypass, RateLimitStore    |
| **iam**                    | HIGH (security)    | LoginAnomalyDetector, BruteForceGuard, BreakGlassService, MFA |
| **secrets-management**     | HIGH (security)    | SecretRotationScheduler, CertificateLifecycle, HSM            |
| **platform-sdk**           | MEDIUM             | SdkCoreAbstractions, ServiceRegistry                          |
| **plugin-runtime**         | MEDIUM             | T2SandboxPluginRuntime, ManifestVerification, TierEnforcer    |
| **workflow-orchestration** | MEDIUM (migrating) | Basic FSM transitions                                         |
| **dlq-management**         | MEDIUM             | BulkReplay, DLQ routing                                       |
| **ai-governance**          | MEDIUM             | Policy evaluation                                             |
| **data-governance**        | MEDIUM             | After mandate fix (Phase 6.11)                                |
| **integration-testing**    | LOW                | ChaosTestRunner correctness                                   |
| **client-onboarding**      | LOW                | Onboarding flow                                               |
| **deployment-abstraction** | LOW                | Deployment strategies                                         |
| **incident-management**    | LOW                | Incident creation/escalation                                  |
| **operator-workflows**     | LOW                | Operator task flows                                           |
| **pack-certification**     | LOW                | Pack validation                                               |
| **platform-manifest**      | LOW                | Manifest parsing                                              |
| **regulator-portal**       | LOW                | Portal queries                                                |

**Acceptance Criteria:**

- [ ] Every HIGH module has >= 5 test files
- [ ] Every MEDIUM module has >= 3 test files
- [ ] All tests extend `EventloopTestBase` for async code
- [ ] All tests use `TestDataBuilders` where applicable

---

### 8.2 Increase Low-Ratio Module Coverage

**Source:** Code Review §1.3

| Module             | Current      | Target             | Additional Tests Needed                                       |
| ------------------ | ------------ | ------------------ | ------------------------------------------------------------- |
| api-gateway        | 1 test (7%)  | >= 8 tests (50%+)  | JwtValidation, RateLimitStore, GeoHeader, JurisdictionRouting |
| iam                | 2 tests (5%) | >= 10 tests (25%+) | Anomaly, BruteForce, BreakGlass, MFA, RBAC, Session           |
| secrets-management | 1 test (7%)  | >= 5 tests (33%+)  | HSM, Certificate, Rotation, Provider                          |

**Acceptance Criteria:**

- [ ] Each module reaches target test count
- [ ] Security-critical paths have positive + negative test cases

---

### 8.3 Domain Pack Tests (14 Packs)

**Source:** Code Review §1.1  
**Priority order** (by business risk):

| Priority      | Packs                                                  | Min Tests Each |
| ------------- | ------------------------------------------------------ | -------------- |
| P0 (Critical) | oms, risk-engine, sanctions, compliance                | 10+ per pack   |
| P1 (High)     | pms, reconciliation, post-trade, pricing               | 8+ per pack    |
| P2 (Medium)   | ems, market-data, reference-data, regulatory-reporting | 5+ per pack    |
| P3 (Lower)    | corporate-actions, surveillance                        | 3+ per pack    |

**Test pattern for each pack:**

1. Service unit tests (mock ports, verify business logic)
2. Value object tests (domain models, validation)
3. Port adapter tests (DB queries, external service calls)

**Acceptance Criteria:**

- [ ] All 14 packs have test files
- [ ] P0 packs have >= 10 tests each
- [ ] All domain services have at least one happy-path and one error-path test

---

## Appendix A: Files to Create (Summary)

| Phase    | File/Module                                                        | Type          |
| -------- | ------------------------------------------------------------------ | ------------- |
| 2.1      | `platform/java/core/.../json/PlatformObjectMapper.java`            | New class     |
| 2.2      | `platform/java/database/.../DataSourceFactory.java`                | New class     |
| 2.3      | `platform/java/database/.../RedisClientFactory.java`               | New class     |
| 2.4      | `platform/java/audit/.../AuditBusPort.java`                        | New interface |
| 3.1–3.10 | `platform/java/workflow/src/main/java/...` (10 files)              | New types     |
| 3.11     | `platform/java/workflow/.../DurableWorkflowEngine.java`            | Modified      |
| 3.12     | `platform/java/workflow/.../InMemoryWorkflowStateStore.java`       | New class     |
| 4.1      | `platform/java/workflow-runtime/` (new module)                     | New module    |
| 4.2–4.13 | `platform/java/workflow-runtime/src/main/java/...` (~15 files)     | New classes   |
| 5.1      | `platform/java/workflow-jdbc/` (new module)                        | New module    |
| 5.2      | `platform/java/workflow-jdbc/.../V001__create_workflow_schema.sql` | New DDL       |
| 5.3–5.5  | `platform/java/workflow-jdbc/src/main/java/...` (3 files)          | New classes   |

**Total new files: ~35 production, ~40+ test files**

## Appendix B: Files to Modify (Summary)

| Phase | File                               | Change                                    |
| ----- | ---------------------------------- | ----------------------------------------- |
| 1.1   | `JwtValidationFilter.java`         | Add nbf/iss/aud/jti validation            |
| 1.2   | `InternalServiceBypassFilter.java` | Fix header propagation                    |
| 1.3   | `HsmKeyOperationsProvider.java`    | Add env check                             |
| 1.4   | 6 service files                    | Add audit event emissions                 |
| 2.5   | `SdkCoreAbstractionsService.java`  | Read version from properties              |
| 3.11  | `DurableWorkflowEngine.java`       | Add lifecycle events                      |
| 6.1   | `SdkCoreAbstractionsService.java`  | Replace stub with ServiceRegistryPort     |
| 6.2   | `CertificateLifecycleService.java` | Implement renewal                         |
| 6.3   | `GeoIpResolver.java`               | Add MaxMind adapter                       |
| 6.4   | 25 files                           | Replace ObjectMapper instances            |
| 6.5   | 163 files                          | Replace HikariDataSource with DataSource  |
| 6.6   | 8 files                            | Replace ScheduledExecutorService          |
| 6.7   | `FixProtocolService.java`          | Make params configurable                  |
| 6.8   | 15 files                           | Replace inner AuditPort                   |
| 6.9   | 3 files                            | Replace Consumer<Object> event publishers |
| 6.10  | `ai-governance/build.gradle.kts`   | Add governance dependency                 |
| 6.11  | 7 files in data-governance         | Replace direct JDBC                       |
|       | `settings.gradle.kts`              | Add workflow-runtime, workflow-jdbc       |

## Appendix C: Commands

```bash
# Build all
./gradlew clean build

# Test all
./gradlew test

# Format
./gradlew spotlessApply

# Check style
./gradlew checkstyleMain pmdMain

# Build specific module
./gradlew :platform:java:workflow:build
./gradlew :platform:java:workflow-runtime:build
./gradlew :platform:java:workflow-jdbc:build
```

---

**End of Consolidated Implementation Plan**
