# Unified Workflow Platform Design

**Status:** Proposed  
**Authors:** Architecture Review (March 2026)  
**Scope:** `platform:java:workflow` and all products in the Ghatana monorepo  
**Relates to:** CODE_REVIEW_ANALYSIS_REPORT.md — Section 15, Mandate 3

---

## 1. Executive Summary

The Ghatana monorepo currently has **two incompatible workflow systems** that have grown independently:

| Layer | Location | Characteristics |
|-------|----------|-----------------|
| **Platform (ephemeral + partial durable)** | `platform:java:workflow` | `Workflow`, `WorkflowStep`, `WorkflowContext`, `DurableWorkflowEngine` (in-memory state), `Pipeline`/`DAGPipelineExecutor`, `UnifiedOperator` (for AEP) |
| **App-platform (full durable)** | `app-platform/kernel/workflow-orchestration` | 15-service FSM with PostgreSQL persistence, CEL evaluation, WAIT/PARALLEL/SUB_WORKFLOW steps, saga compensation, SLA tracking, event-driven triggers — **not connected to the platform interfaces** |

This divergence means:
- Other products (tutorputor, flashit, audio-video, dcmaar) that need durable workflows must re-invent what app-platform built
- AEP's operator pipelines cannot seamlessly interoperate with app-platform's FSM workflows
- App-platform gains no benefit from platform upgrades to `DurableWorkflowEngine`

**This document designs a unified 3-module workflow platform** that: covers all workflow kinds (ephemeral operator pipelines → fully durable long-running FSMs), is product-agnostic, and allows any product to adopt what it needs at the granularity it needs.

---

## 2. Design Goals

| Goal | Detail |
|------|--------|
| **Single unified API** | One set of types every product understands: `Workflow`, `WorkflowContext`, `WorkflowStep`, `WorkflowRun` |
| **Ephemeral ↔ Durable interop** | An ephemeral pipeline step can invoke a durable workflow; a durable workflow step can run an operator pipeline |
| **Multi-module with clear boundaries** | `workflow` (API + ephemeral), `workflow-runtime` (durable engine), `workflow-jdbc` (PostgreSQL state store) |
| **Pluggable everything** | State stores, expression evaluators, wait coordinators — all are interfaces; implementations are swappable |
| **Backward compatible** | The existing `Workflow`, `WorkflowContext`, `WorkflowStep` and `DurableWorkflowEngine` public API must remain valid |
| **Product-specific extensions** | Products can define their own step implementations, triggers, and expression functions without forking the core |
| **Full lifecycle observability** | Lifecycle events emitted at every phase, consumable by observability-sdk and AEP |

---

## 3. The Unified Workflow Type Hierarchy

```
                          ┌──────────────────────────┐
                          │  WorkflowContext          │  ← shared data/variable bag
                          │  (tenant, correlationId,  │    used by ALL workflow kinds
                          │   variables, step cursor) │
                          └──────────────┬───────────┘
                                         │ passed to
              ┌──────────────────────────▼──────────────────────────┐
              │                  WorkflowStep                        │
              │  @FunctionalInterface                                 │
              │  Promise<WorkflowContext> execute(WorkflowContext)    │
              │  (already exists in platform:java:workflow)           │
              └────────────────┬────────────────────────┬────────────┘
                               │ composes into          │ composes into
         ┌─────────────────────▼──────┐     ┌──────────▼──────────────────────┐
         │   Ephemeral Workflow        │     │   Durable Workflow               │
         │   (Pipeline / DAG)          │     │   (FSM / Saga)                   │
         │                             │     │                                   │
         │ • DAGPipelineExecutor       │     │ • WorkflowDefinition (typed DSL) │
         │ • Promise-chained steps     │     │ • WorkflowInstances (persisted)  │
         │ • In-memory only            │     │ • PENDING→RUNNING→WAITING→       │
         │ • AEP operators             │     │   COMPLETED/FAILED/CANCELLED     │
         │ • Completes in <60s         │     │ • CEL conditions, WAIT steps     │
         │                             │     │ • Sub-workflows, PARALLEL steps  │
         └─────────────────────────────┘     │ • Saga compensation              │
                        ↑                    │ • Survives restarts              │
                        │          interop   └──────────────────────────────────┘
                        └───────── bridge ──────────────────────────────────────
                          (DurableStep wrapping EphemeralPipeline, or
                           EphemeralStep invoking SubWorkflowLaunchPort)
```

The bridge is bidirectional:
- A **durable workflow step** of kind `OPERATOR_PIPELINE` wraps an ephemeral `Pipeline` as a `WorkflowStep` — the SAP clears when the pipeline resolves
- An **ephemeral pipeline node** can invoke a durable workflow via the `SubWorkflowLaunchPort` — the node blocks until the child workflow completes (or fires async)

---

## 4. Multi-Module Architecture

```
platform/java/
├── workflow/              ← MODULE 1 (EXISTS, ENHANCED)
│   ─ Core API + ephemeral runtime + operator model
│   ─ All products ALWAYS depend on this
│
├── workflow-runtime/      ← MODULE 2 (NEW)
│   ─ Full durable FSM engine (generalised from app-platform)
│   ─ Products that need long-running workflows depend on this
│
└── workflow-jdbc/         ← MODULE 3 (NEW)
    ─ PostgreSQL implementations of workflow-runtime SPIs
    ─ Products using PostgreSQL for workflow state depend on this
```

### Dependency graph

```
workflow-jdbc ──depends──▶ workflow-runtime ──depends──▶ workflow
                                                            ▲
products (app-platform, tutorputor, etc.) ─────────────────┘
```

A product that only needs ephemeral operator pipelines (e.g. a simple AEP extension) depends only on `workflow`.  
A product needing durable FSM adds `workflow-runtime` and a state store (e.g. `workflow-jdbc`).

---

## 5. Module 1: `platform:java:workflow` (Enhanced)

### 5.1 What stays (unchanged public API)

```java
// KEEP — unchanged, backward compatible
com.ghatana.platform.workflow.Workflow
com.ghatana.platform.workflow.WorkflowContext
com.ghatana.platform.workflow.WorkflowStep
com.ghatana.platform.workflow.DefaultWorkflowContext
com.ghatana.platform.workflow.engine.DurableWorkflowEngine
com.ghatana.platform.workflow.operator.*     // UnifiedOperator, AbstractOperator, etc.
com.ghatana.platform.workflow.pipeline.*     // Pipeline, DAGPipelineExecutor, etc.
```

### 5.2 New additions

#### `WorkflowKind.java`
```java
package com.ghatana.platform.workflow;

/**
 * Classifies a workflow by its execution and durability characteristics.
 * EPHEMERAL: in-memory, completes or fails in one continuous execution.
 * DURABLE: persisted, survives restarts, supports WAIT/PARALLEL/SUB_WORKFLOW steps.
 */
public enum WorkflowKind {
    EPHEMERAL,
    DURABLE
}
```

#### `WorkflowOptions.java`
```java
package com.ghatana.platform.workflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.time.Duration;

/**
 * Common execution options applicable to both ephemeral and durable workflows.
 * Products compose these per workflow-definition or per-step.
 */
public record WorkflowOptions(
    @NotNull  WorkflowKind kind,
    @Nullable Duration      timeout,
    @NotNull  RetryPolicy   retryPolicy,
    @NotNull  SagaPolicy    sagaPolicy
) {
    public static WorkflowOptions ephemeral() {
        return new WorkflowOptions(
            WorkflowKind.EPHEMERAL,
            Duration.ofMinutes(5),
            RetryPolicy.none(),
            SagaPolicy.noCompensation()
        );
    }

    public static WorkflowOptions durable() {
        return new WorkflowOptions(
            WorkflowKind.DURABLE,
            null,                // defined per workflow-definition
            RetryPolicy.defaultDurable(),
            SagaPolicy.compensateOnFailure()
        );
    }

    public record RetryPolicy(
        int maxAttempts,
        @NotNull Duration baseBackoff,
        double backoffMultiplier,
        @NotNull Duration maxBackoff
    ) {
        public static RetryPolicy none()           { return new RetryPolicy(0, Duration.ZERO, 1.0, Duration.ZERO); }
        public static RetryPolicy defaultDurable() { return new RetryPolicy(3, Duration.ofSeconds(5), 2.0, Duration.ofMinutes(5)); }
    }

    public record SagaPolicy(boolean compensateOnFailure, boolean continueOnCompensationFailure) {
        public static SagaPolicy noCompensation()  { return new SagaPolicy(false, false); }
        public static SagaPolicy compensateOnFailure() { return new SagaPolicy(true, true); }
    }
}
```

#### `WorkflowLifecycleEvent.java`
```java
package com.ghatana.platform.workflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.time.Instant;
import java.util.Map;

/**
 * Immutable lifecycle event emitted at every significant phase of workflow execution.
 * Consumed by: observability-sdk (Micrometer), AEP (event bus), audit-trail.
 */
public record WorkflowLifecycleEvent(
    @NotNull  String              runId,
    @NotNull  String              workflowId,
              int                 version,
    @NotNull  Phase               phase,
    @Nullable String              stepId,
    @Nullable String              stepKind,
    @NotNull  String              tenantId,
    @Nullable String              correlationId,
    @NotNull  Instant             timestamp,
    @Nullable String              failureReason,
    @NotNull  Map<String, Object> attributes
) {
    public enum Phase {
        STARTED,
        STEP_STARTED,
        STEP_COMPLETED,
        STEP_FAILED,
        STEP_RETRYING,
        STEP_SKIPPED,
        WAITING,         // WAIT step registered
        RESUMED,         // signal received, WAIT step completed
        SUSPENDED,       // manual suspend
        COMPLETED,
        FAILED,
        CANCELLED,
        COMPENSATING,
        COMPENSATED
    }
}
```

#### `WorkflowLifecycleListener.java`
```java
package com.ghatana.platform.workflow;

/**
 * SPI for observing workflow lifecycle events.
 * Multiple listeners can be registered; all are called synchronously in registration order.
 * Implementations: MetricsWorkflowListener (Micrometer), AuditWorkflowListener (audit-trail),
 *                  AepEventWorkflowListener (AEP event bus).
 */
@FunctionalInterface
public interface WorkflowLifecycleListener {
    void onEvent(WorkflowLifecycleEvent event);
}
```

#### `WorkflowRunStatus.java`
```java
package com.ghatana.platform.workflow;

/** Unified FSM state for any workflow instance. */
public enum WorkflowRunStatus {
    PENDING,       // created, not yet started
    RUNNING,       // actively executing steps
    WAITING,       // paused at a WAIT step (event correlation / timer / manual)
    SUSPENDED,     // manually suspended by operator
    COMPLETED,     // all steps completed successfully
    FAILED,        // step failed and no compensation / compensation refused
    CANCELLED,     // operator-initiated cancellation
    COMPENSATING,  // saga rollback in progress
    COMPENSATED    // saga rollback completed
}
```

#### `WorkflowRun.java`
```java
package com.ghatana.platform.workflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents one execution instance of a workflow (one run).
 * Shared by both ephemeral (in-memory) and durable (persisted) workflows.
 */
public record WorkflowRun(
    @NotNull  String                          runId,
    @NotNull  String                          workflowId,
              int                             version,
    @NotNull  WorkflowKind                    kind,
    @NotNull  WorkflowRunStatus               status,
    @Nullable String                          currentStepId,
    @NotNull  Map<String, String>             stepStatuses,  // stepId → PENDING|RUNNING|COMPLETED|FAILED|SKIPPED
    @NotNull  WorkflowContext                 context,
    @NotNull  String                          tenantId,
    @Nullable String                          parentRunId,   // set if this is a sub-workflow
    @Nullable String                          failureReason,
    @NotNull  Instant                         startedAt,
    @Nullable Instant                         completedAt,
    @NotNull  List<WorkflowLifecycleEvent>    history        // event log for this run
) {}
```

#### `WorkflowStateStore.java`
```java
package com.ghatana.platform.workflow;

import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting workflow run state.
 * Implementations:
 *   - InMemoryWorkflowStateStore  (in platform:java:workflow, for ephemeral + testing)
 *   - JdbcWorkflowStateStore      (in platform:java:workflow-jdbc, for production durable)
 *   - RedisWorkflowStateStore     (future, for high-throughput durable)
 */
public interface WorkflowStateStore {

    /** Persist or update a workflow run. Must be idempotent (upsert semantics). */
    void save(@NotNull WorkflowRun run);

    /** Load a run by its unique run ID. */
    @NotNull
    Optional<WorkflowRun> load(@NotNull String runId);

    /** Load all runs for a workflow ID, optionally filtered by status. */
    @NotNull
    List<WorkflowRun> findByWorkflowId(@NotNull String workflowId, WorkflowRunStatus... statuses);

    /** Load runs that are WAITING and whose wait timeout has passed. */
    @NotNull
    List<WorkflowRun> findExpiredWaits();

    /** Delete a run (archival / cleanup use case). */
    void delete(@NotNull String runId);
}
```

#### `WorkflowExpressionEvaluator.java`
```java
package com.ghatana.platform.workflow;

import org.jetbrains.annotations.NotNull;

/**
 * SPI for evaluating conditional expressions in DECISION steps.
 * Default implementation: CelWorkflowExpressionEvaluator (in workflow-runtime, uses cel-java).
 * Products can register domain-specific functions:
 *   evaluator.registerFunction("isRegulatedMarket", ctx -> isRegulated(ctx.get("marketCode")));
 */
public interface WorkflowExpressionEvaluator {

    /**
     * Evaluate an expression against the current workflow context.
     * @param expression  CEL (or other language) expression string
     * @param context     current workflow context
     * @return            the selected branch name (for DECISION steps)
     */
    @NotNull
    String evaluate(@NotNull String expression, @NotNull WorkflowContext context);

    /**
     * Validate an expression at definition-publish time (static type check).
     * Throws {@link WorkflowDefinitionException} if the expression is invalid.
     */
    void validate(@NotNull String expression);
}
```

#### `WorkflowWaitCoordinator.java`
```java
package com.ghatana.platform.workflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * SPI for registering and resolving WAIT steps.
 * WAIT steps pause a durable workflow instance until an external signal arrives.
 *
 * Wait kinds:
 *   EVENT   — wait for an event of a given type matching a correlation expression
 *   TIMER   — wait until a specific instant (or after a duration)
 *   MANUAL  — wait for an explicit API signal from a human operator
 *
 * Default implementation: JdbcWorkflowWaitCoordinator (in workflow-jdbc)
 * AEP-backed implementation: AepWorkflowWaitCoordinator (in an AEP module)
 */
public interface WorkflowWaitCoordinator {

    void registerEventWait(@NotNull String runId,
                           @NotNull String eventType,
                           @NotNull String correlationKey,
                           @Nullable Duration timeout);

    void registerTimerWait(@NotNull String runId,
                           @NotNull Instant fireAt);

    void registerManualWait(@NotNull String runId,
                            @NotNull String signalType);

    /**
     * Called when the awaited signal arrives. The runtime will resume the workflow.
     * @param runId    the run to resume
     * @param payload  signal data merged into WorkflowContext
     */
    void signal(@NotNull String runId,
                @Nullable Map<String, Object> payload);

    void cancel(@NotNull String runId);
}
```

#### `WorkflowDefinitionException.java`
```java
package com.ghatana.platform.workflow;

/** Thrown when a workflow definition fails validation. */
public class WorkflowDefinitionException extends RuntimeException {
    public WorkflowDefinitionException(String message) { super(message); }
    public WorkflowDefinitionException(String message, Throwable cause) { super(message, cause); }
}
```

---

## 6. Module 2: `platform:java:workflow-runtime` (New)

This module extracts and generalises the **durable FSM runtime** that currently lives in `app-platform/kernel/workflow-orchestration`. It becomes the shared engine that every product can use for long-running workflows.

### 6.1 `build.gradle.kts`

```kotlin
plugins { id("java-library") }

group   = "com.ghatana.platform"
version = "1.0.0-SNAPSHOT"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    api(project(":platform:java:workflow"))          // core types (Workflow, WorkflowContext, etc.)
    api(project(":platform:java:observability"))     // MeterRegistry
    api(project(":platform:java:event-cloud"))       // EventBusPort backing (optional via SPI)

    implementation(libs.cel.java)                   // CEL expression evaluation
    implementation(libs.activej.promise)
    compileOnly(libs.jetbrains.annotations)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
```

### 6.2 Package structure

```
com.ghatana.platform.workflow.runtime
├── DurableWorkflowRuntime          ← generalised WorkflowExecutionRuntimeService
├── WorkflowDefinitionManager       ← generalised WorkflowDefinitionService
├── WorkflowVersionManager          ← generalised WorkflowVersionManagementService
├── WorkflowTriggerManager          ← generalised WorkflowTriggerService
├── WorkflowSlaTracker              ← generalised WorkflowMetricsSlaService
com.ghatana.platform.workflow.runtime.step
├── ParallelStepExecutor            ← generalised ParallelStepExecutionService
├── WaitStepEngine                  ← generalised WaitCorrelationStepService
├── SubWorkflowComposer             ← generalised SubWorkflowCompositionService
├── CelWorkflowExpressionEvaluator  ← implements WorkflowExpressionEvaluator via CEL
com.ghatana.platform.workflow.runtime.policy
├── WorkflowRetryPolicy             ← retry/backoff logic, extracted from WorkflowErrorHandlingService
├── WorkflowErrorPolicyEngine       ← catch/finally/compensation DSL execution
com.ghatana.platform.workflow.runtime.definition
├── WorkflowDefinition              ← versioned DSL record  (moved from app-platform)
├── WorkflowStepDefinition          ← step config record    (moved from app-platform)
├── WorkflowStepKind                ← TASK,DECISION,PARALLEL,WAIT,SUB_WORKFLOW,OPERATOR_PIPELINE
├── WorkflowTriggerType             ← EVENT,SCHEDULE,MANUAL,API_WEBHOOK
├── WorkflowDefinitionRegistry      ← SPI (in-memory impl here; JDBC impl in workflow-jdbc)
```

### 6.3 `DurableWorkflowRuntime` — key API

```java
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.workflow.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Platform-generic durable workflow FSM engine.
 *
 * Products register their WorkflowDefinitions via WorkflowDefinitionRegistry,
 * then launch instances by workflowId + version.
 *
 * The runtime:
 *   1. Persists the WorkflowRun to the WorkflowStateStore on every state change.
 *   2. Dispatches steps to registered WorkflowStep implementations by stepId/taskRef.
 *   3. Evaluates DECISION step conditions via the WorkflowExpressionEvaluator.
 *   4. Fans out PARALLEL branches via ParallelStepExecutor.
 *   5. Pauses at WAIT steps via WorkflowWaitCoordinator.
 *   6. Invokes child workflows via SubWorkflowComposer.
 *   7. Emits WorkflowLifecycleEvents to all registered listeners after each phase.
 *
 * Products extend this by:
 *   a) Registering custom WorkflowStep implementations per taskRef name.
 *   b) Registering WorkflowLifecycleListeners (e.g. audit, metrics, domain events).
 *   c) Providing a product-specific WorkflowExpressionEvaluator with custom functions.
 */
public final class DurableWorkflowRuntime {

    public interface StepRegistry {
        /** Look up a WorkflowStep by its taskRef name. */
        @NotNull WorkflowStep find(@NotNull String taskRef);
        void register(@NotNull String taskRef, @NotNull WorkflowStep step);
    }

    /**
     * Launch a new workflow instance.
     * @param workflowId  definition ID (latest ACTIVE version is resolved)
     * @param context     initial context (caller populates trigger payload)
     * @param tenantId    tenant scope
     * @return WorkflowRun representing the launched (and possibly completed) instance
     */
    public Promise<WorkflowRun> launch(
        @NotNull String workflowId,
        @NotNull WorkflowContext context,
        @NotNull String tenantId
    ) { /* ... */ return null; }

    /** Resume a WAITING workflow after a signal is received. */
    public Promise<WorkflowRun> resume(
        @NotNull String runId,
        @NotNull String signal,
        @NotNull WorkflowContext signalPayload
    ) { /* ... */ return null; }

    /** Cancel a running or waiting workflow (triggers compensation if configured). */
    public Promise<WorkflowRun> cancel(
        @NotNull String runId,
        @NotNull String reason
    ) { /* ... */ return null; }

    /** Suspend a running workflow (no compensation). */
    public Promise<WorkflowRun> suspend(@NotNull String runId) { /* ... */ return null; }

    /** Query a workflow run by its run ID. */
    public Promise<WorkflowRun> getInstance(@NotNull String runId) { /* ... */ return null; }

    /** Builder for DurableWorkflowRuntime. */
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private WorkflowStateStore            stateStore;
        private WorkflowDefinitionRegistry    definitionRegistry;
        private WorkflowExpressionEvaluator   expressionEvaluator;
        private WorkflowWaitCoordinator       waitCoordinator;
        private StepRegistry                  stepRegistry;
        private java.util.List<WorkflowLifecycleListener> listeners = new java.util.ArrayList<>();
        private io.micrometer.core.instrument.MeterRegistry meterRegistry;

        public Builder stateStore(WorkflowStateStore s)                         { this.stateStore = s; return this; }
        public Builder definitionRegistry(WorkflowDefinitionRegistry r)         { this.definitionRegistry = r; return this; }
        public Builder expressionEvaluator(WorkflowExpressionEvaluator e)       { this.expressionEvaluator = e; return this; }
        public Builder waitCoordinator(WorkflowWaitCoordinator w)               { this.waitCoordinator = w; return this; }
        public Builder stepRegistry(StepRegistry r)                             { this.stepRegistry = r; return this; }
        public Builder addListener(WorkflowLifecycleListener l)                 { this.listeners.add(l); return this; }
        public Builder meterRegistry(io.micrometer.core.instrument.MeterRegistry m) { this.meterRegistry = m; return this; }

        public DurableWorkflowRuntime build() {
            java.util.Objects.requireNonNull(stateStore,         "stateStore is required");
            java.util.Objects.requireNonNull(definitionRegistry, "definitionRegistry is required");
            java.util.Objects.requireNonNull(stepRegistry,       "stepRegistry is required");
            return new DurableWorkflowRuntime(this);
        }
    }

    private DurableWorkflowRuntime(Builder b) { /* wire fields */ }
}
```

### 6.4 `WorkflowDefinition` and step types

```java
package com.ghatana.platform.workflow.runtime.definition;

import com.ghatana.platform.workflow.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Step kind taxonomy — covers all current app-platform use cases + future AEP integration. */
public enum WorkflowStepKind {
    TASK,              // invoke a registered WorkflowStep by taskRef
    DECISION,          // evaluate CEL expression, select branch
    PARALLEL,          // fan out N branches, join with strategy (ALL/FIRST/N_OF_M)
    WAIT,              // pause until event/timer/manual signal
    SUB_WORKFLOW,      // invoke a child workflow (sync or async)
    OPERATOR_PIPELINE  // run an ephemeral Pipeline as a step (AEP interop)
}

public enum WorkflowTriggerType { EVENT, SCHEDULE, MANUAL, API_WEBHOOK }

/** A single step within a workflow definition (the DSL node). */
public record WorkflowStepDefinition(
    @NotNull  String                  stepId,
    @NotNull  String                  name,
    @NotNull  WorkflowStepKind        kind,
    @Nullable String                  taskRef,             // TASK: registered step name
    @Nullable String                  condition,           // DECISION: CEL expression
    @Nullable List<String>            parallelBranchIds,   // PARALLEL: step IDs to fan out
    @Nullable ParallelJoinStrategy    joinStrategy,        // PARALLEL: ALL | FIRST | N_OF_M
    @Nullable Integer                 joinN,               // PARALLEL: N (for N_OF_M)
    @Nullable String                  waitEventType,       // WAIT: event type to await
    @Nullable String                  waitCorrelationKey,  // WAIT: context variable for correlation
    @Nullable Duration                waitTimeout,         // WAIT: expiry
    @Nullable String                  waitTimeoutAction,   // WAIT: CONTINUE|FAIL|COMPENSATE
    @Nullable String                  subWorkflowId,       // SUB_WORKFLOW: target definition
    @Nullable Integer                 subWorkflowVersion,  // SUB_WORKFLOW: null = latest active
    @Nullable String                  subWorkflowMode,     // SUB_WORKFLOW: SYNC | ASYNC
    @Nullable String                  pipelineRef,         // OPERATOR_PIPELINE: pipeline bean name
    @NotNull  WorkflowOptions.RetryPolicy retryPolicy,
    @Nullable Duration                timeout,
    @Nullable String                  compensationStepId,  // step to run on failure
    @Nullable String                  nextStepId,          // happy path override
    @Nullable String                  onErrorStepId        // explicit error handler step
) {
    public enum ParallelJoinStrategy { ALL, FIRST, N_OF_M }
}

/** Versioned workflow definition (immutable once ACTIVE). */
public record WorkflowDefinition(
    @NotNull  String                       definitionId,
    @NotNull  String                       workflowId,
    @NotNull  String                       name,
              int                          version,
    @NotNull  WorkflowTriggerType          triggerType,
    @Nullable String                       triggerConfig,       // JSON (topic/cron/webhook)
    @NotNull  List<WorkflowStepDefinition> steps,
    @Nullable String                       errorHandlingConfig, // JSON top-level catch/finally
    @Nullable Duration                     timeout,
    @NotNull  String                       status,              // DRAFT|ACTIVE|DEPRECATED|ARCHIVED
    @NotNull  Instant                      createdAt,
    @NotNull  String                       createdBy
) {}
```

### 6.5 `WorkflowDefinitionRegistry` SPI

```java
package com.ghatana.platform.workflow.runtime.definition;

import com.ghatana.platform.workflow.WorkflowDefinitionException;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Optional;

/**
 * SPI for registering and looking up workflow definitions.
 * Implementations:
 *   - InMemoryWorkflowDefinitionRegistry  (in workflow-runtime, for testing + products
 *                                          that define workflows in code / classpath YAML)
 *   - JdbcWorkflowDefinitionRegistry      (in workflow-jdbc, for operator-managed defs)
 */
public interface WorkflowDefinitionRegistry {

    /**
     * Register a new version of a workflow definition (in DRAFT status).
     * Throws {@link WorkflowDefinitionException} if validation fails.
     */
    void register(@NotNull WorkflowDefinition definition);

    /** Activate a definition version (makes it the target for new launches). */
    void activate(@NotNull String workflowId, int version);

    /** Deprecate a version (in-flight instances continue; no new launches). */
    void deprecate(@NotNull String workflowId, int version);

    /** Find an exact version. */
    @NotNull Optional<WorkflowDefinition> find(@NotNull String workflowId, int version);

    /** Find the latest ACTIVE version for a workflow ID. */
    @NotNull Optional<WorkflowDefinition> findLatestActive(@NotNull String workflowId);

    /** List all versions of a workflow ID. */
    @NotNull List<WorkflowDefinition> listVersions(@NotNull String workflowId);
}
```

### 6.6 Built-in Lifecycle Listeners (provided in `workflow-runtime`)

```java
// Micrometer metrics listener (auto-wired when MeterRegistry is available)
MetricsWorkflowListener    implements WorkflowLifecycleListener

// Structured audit listener (delegates to platform AuditPort)
AuditWorkflowListener      implements WorkflowLifecycleListener

// AEP event-bus listener (publishes WorkflowLifecycleEvent as an AEP cloud event)
// Lives in an AEP extension module, not workflow-runtime itself
// AepWorkflowLifecycleListener  implements WorkflowLifecycleListener
```

---

## 7. Module 3: `platform:java:workflow-jdbc` (New)

PostgreSQL implementations of the SPIs defined in `workflow` and `workflow-runtime`.

### 7.1 `build.gradle.kts`

```kotlin
plugins { id("java-library") }

group   = "com.ghatana.platform"
version = "1.0.0-SNAPSHOT"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    api(project(":platform:java:workflow"))          // WorkflowStateStore, WorkflowRun
    api(project(":platform:java:workflow-runtime"))  // WorkflowDefinitionRegistry
    implementation(project(":platform:java:database"))
    implementation(libs.hikaricp)
    implementation(libs.jackson.databind)
    compileOnly(libs.jetbrains.annotations)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

### 7.2 Classes

```java
// WorkflowStateStore backed by PostgreSQL workflow_run + workflow_step_state tables
JdbcWorkflowStateStore implements WorkflowStateStore

// WorkflowDefinitionRegistry backed by PostgreSQL workflow_definition table
JdbcWorkflowDefinitionRegistry implements WorkflowDefinitionRegistry

// WaitCoordinator backed by PostgreSQL workflow_wait_correlation table + scheduled poller
JdbcWorkflowWaitCoordinator implements WorkflowWaitCoordinator
```

### 7.3 DDL Schema (canonical)

```sql
-- Moved from app-platform; becomes the platform-canonical schema.
-- Products apply these as part of their own Flyway/Liquibase migrations.

CREATE TABLE workflow_definition (
    definition_id    VARCHAR(64)  PRIMARY KEY,
    workflow_id      VARCHAR(64)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    version          INTEGER      NOT NULL,
    trigger_type     VARCHAR(32)  NOT NULL,
    trigger_config   JSONB,
    steps            JSONB        NOT NULL,
    error_handling   JSONB,
    timeout_iso      VARCHAR(32),
    status           VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       VARCHAR(128) NOT NULL,
    UNIQUE (workflow_id, version)
);

CREATE TABLE workflow_run (
    run_id           VARCHAR(64)  PRIMARY KEY,
    workflow_id      VARCHAR(64)  NOT NULL,
    version          INTEGER      NOT NULL,
    kind             VARCHAR(16)  NOT NULL,      -- EPHEMERAL | DURABLE
    status           VARCHAR(32)  NOT NULL,
    current_step_id  VARCHAR(64),
    context          JSONB        NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL,
    parent_run_id    VARCHAR(64),
    failure_reason   TEXT,
    started_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ,
    FOREIGN KEY (workflow_id, version) REFERENCES workflow_definition(workflow_id, version)
);
CREATE INDEX idx_workflow_run_workflow_id  ON workflow_run(workflow_id);
CREATE INDEX idx_workflow_run_status       ON workflow_run(status);
CREATE INDEX idx_workflow_run_tenant       ON workflow_run(tenant_id);

CREATE TABLE workflow_step_state (
    run_id           VARCHAR(64)  NOT NULL,
    step_id          VARCHAR(64)  NOT NULL,
    step_kind        VARCHAR(32)  NOT NULL,
    status           VARCHAR(32)  NOT NULL,  -- PENDING|RUNNING|COMPLETED|FAILED|SKIPPED
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    input_json       JSONB,
    output_json      JSONB,
    attempt          INTEGER      NOT NULL DEFAULT 0,
    error_message    TEXT,
    PRIMARY KEY (run_id, step_id)
);

CREATE TABLE workflow_wait_correlation (
    run_id           VARCHAR(64)  NOT NULL PRIMARY KEY,
    wait_kind        VARCHAR(16)  NOT NULL,  -- EVENT | TIMER | MANUAL
    event_type       VARCHAR(128),
    correlation_key  VARCHAR(255),
    fire_at          TIMESTAMPTZ,
    signal_type      VARCHAR(64),
    expires_at       TIMESTAMPTZ,
    timeout_action   VARCHAR(32)  NOT NULL DEFAULT 'FAIL',
    registered_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_wf_wait_fire_at ON workflow_wait_correlation(fire_at) WHERE fire_at IS NOT NULL;

CREATE TABLE workflow_lifecycle_event (
    event_id         VARCHAR(64)  PRIMARY KEY,
    run_id           VARCHAR(64)  NOT NULL,
    workflow_id      VARCHAR(64)  NOT NULL,
    version          INTEGER      NOT NULL,
    phase            VARCHAR(32)  NOT NULL,
    step_id          VARCHAR(64),
    step_kind        VARCHAR(32),
    tenant_id        VARCHAR(64)  NOT NULL,
    correlation_id   VARCHAR(64),
    failure_reason   TEXT,
    attributes       JSONB,
    recorded_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_wf_event_run_id ON workflow_lifecycle_event(run_id);
```

---

## 8. Interoperability: Ephemeral ↔ Durable

### 8.1 Running an ephemeral Pipeline as a durable step (`OPERATOR_PIPELINE` step kind)

```java
// In a product's workflow configuration:
WorkflowStepDefinition enrichmentStep = new WorkflowStepDefinition(
    /* stepId */         "enrich-instrument",
    /* name */           "AI Instrument Enrichment",
    /* kind */           WorkflowStepKind.OPERATOR_PIPELINE,
    /* taskRef */        null,
    // ... other fields null ...
    /* pipelineRef */    "instrumentEnrichmentPipeline",  // Spring/ActiveJ bean name
    /* retryPolicy */    WorkflowOptions.RetryPolicy.defaultDurable(),
    /* timeout */        Duration.ofSeconds(30),
    // ...
);

// The runtime resolves "instrumentEnrichmentPipeline" via the StepRegistry,
// which wraps the Pipeline into a WorkflowStep adapter:
class PipelineWorkflowStepAdapter implements WorkflowStep {
    private final Pipeline pipeline;
    private final DAGPipelineExecutor executor;

    @Override
    public Promise<WorkflowContext> execute(WorkflowContext context) {
        return executor.execute(pipeline, context.getVariables())
                       .map(result -> { context.setVariable("pipelineResult", result); return context; });
    }
}
```

### 8.2 Invoking a durable workflow from an ephemeral pipeline node

```java
// An AEP operator (or ephemeral WorkflowStep) that needs to trigger a durable workflow:
class TradeSettlementTriggerOperator extends AbstractOperator {
    private final DurableWorkflowRuntime runtime;

    @Override
    public Promise<OperatorResult> process(Object event, WorkflowContext ctx) {
        WorkflowContext wfCtx = WorkflowContext.forWorkflow("trade-settlement", ctx.getTenantId());
        wfCtx.setVariable("tradeId", ctx.get("tradeId"));

        return runtime.launch("trade-settlement", wfCtx, ctx.getTenantId())
                      .map(run -> OperatorResult.single(Map.of("runId", run.runId())));
        // The operator completes immediately (fire-and-forget ASYNC mode).
        // The durable workflow runs to completion independently.
    }
}
```

### 8.3 Sub-workflow cross-kind composition

```
Durable "onboarding-workflow" (weeks)
  ├── TASK: verify-kyc                          → calls KycVerificationStep
  ├── WAIT: await-document-approval (days)      → pauses until operator signals
  ├── OPERATOR_PIPELINE: assess-risk            → runs ephemeral AI pipeline
  ├── SUB_WORKFLOW: provision-accounts (SYNC)   → child durable workflow
  └── TASK: send-welcome                        → calls NotificationStep
```

This composition is valid because:
- All step kinds resolve to a `WorkflowStep` at execution time
- The parent's `WorkflowRun` remains RUNNING/WAITING across all child resolutions
- Context flows top-down and results flow bottom-up via `WorkflowContext.setVariable`

---

## 9. App-Platform Migration Plan

### 9.1 Before (current state)

```
app-platform/kernel/workflow-orchestration/
├── WorkflowExecutionRuntimeService      ← monolithic FSM (NOT implementing Workflow)
├── WorkflowDefinitionService            ← definition management (app-platform only)
├── CelExpressionEvaluatorService        ← CEL eval (app-platform only)
├── WaitCorrelationStepService           ← WAIT steps (app-platform only)
├── ParallelStepExecutionService         ← PARALLEL steps (app-platform only)
├── SubWorkflowCompositionService        ← sub-workflows (app-platform only)
├── WorkflowVersionManagementService     ← versioning (app-platform only)
├── WorkflowMetricsSlaService            ← SLA/metrics (app-platform only)
├── WorkflowErrorHandlingService         ← error policies (app-platform only)
└── WorkflowTriggerService               ← triggers (app-platform only)
```

**Problems:**
- 15 inner `AuditPort` / `EventBusPort` definitions (one per service) — duplicated
- `EventBusPort` not wired to AEP
- No connection to `platform:java:workflow` at all
- Not reusable by other products

### 9.2 After (target state) — 3-phase migration

#### Phase 1 — Platform modules built (no app-platform changes required)
1. Create `platform:java:workflow-runtime` with `DurableWorkflowRuntime` and all sub-engines
2. Create `platform:java:workflow-jdbc` with `JdbcWorkflowStateStore` and `JdbcWorkflowDefinitionRegistry`
3. Enhance `platform:java:workflow` with the new types defined in Section 5.2
4. Build and publish both new modules (existing code unaffected)

#### Phase 2 — App-platform adopts platform runtime (incremental, per-service)
```kotlin
// app-platform/kernel/workflow-orchestration/build.gradle.kts (add)
implementation(project(":platform:java:workflow-runtime"))
implementation(project(":platform:java:workflow-jdbc"))
```

For each service, migrate in this order:
1. **`WorkflowDefinitionService`** → replace with `JdbcWorkflowDefinitionRegistry` (definitions registered at startup)
2. **`CelExpressionEvaluatorService`** → replace with `CelWorkflowExpressionEvaluator` from workflow-runtime
3. **`WorkflowExecutionRuntimeService`** → wire existing JDBC store as `JdbcWorkflowStateStore`; delegate step dispatch to `DurableWorkflowRuntime`
4. **`WaitCorrelationStepService`** → replace with `JdbcWorkflowWaitCoordinator`
5. **`ParallelStepExecutionService`** → replace with `ParallelStepExecutor` from workflow-runtime
6. **`SubWorkflowCompositionService`** → replace with `SubWorkflowComposer` from workflow-runtime
7. **Inner `AuditPort` duplicates** → replaced by `AuditWorkflowListener` registered on `DurableWorkflowRuntime`
8. **Inner `EventBusPort` duplicates** → replaced by `AepWorkflowLifecycleListener` (wires to AEP)

#### Phase 3 — Register app-platform's workflow business steps in `StepRegistry`
```java
// In app-platform's launcher/composition root:
StepRegistry registry = new DefaultStepRegistry();
registry.register("kyc.verify",             kycVerificationStep);
registry.register("trade.dvp",              dvpSettlementStep);
registry.register("ledger.post",            ledgerPostingStep);
registry.register("notification.send",      notificationSendStep);
// ... all existing TASK references ...

DurableWorkflowRuntime runtime = DurableWorkflowRuntime.builder()
    .stateStore(jdbcWorkflowStateStore)
    .definitionRegistry(jdbcWorkflowDefinitionRegistry)
    .expressionEvaluator(celWorkflowExpressionEvaluator)
    .waitCoordinator(jdbcWorkflowWaitCoordinator)
    .stepRegistry(registry)
    .addListener(new MetricsWorkflowListener(meterRegistry))
    .addListener(new AuditWorkflowListener(auditPort))
    .addListener(new AepWorkflowLifecycleListener(eventBus))
    .meterRegistry(meterRegistry)
    .build();
```

### 9.3 Existing DSL compatibility

The current app-platform workflow DSL (YAML/JSON with `stepId`, `taskRef`, `condition`, etc.) maps 1:1 to `WorkflowStepDefinition`. The only new field is `kind: OPERATOR_PIPELINE` for AEP interop. No existing definitions need to be rewritten.

---

## 10. AEP Integration Pattern

AEP's operator pipelines are an **ephemeral workflow** specialisation: they run inside the `DAGPipelineExecutor` using `UnifiedOperator` steps. The integration points are:

### 10.1 AEP triggers a durable workflow when persistence is needed

```java
// AEP operator that hands off to a durable workflow:
class PersistToDataCloudOperator extends AbstractOperator {
    private final DurableWorkflowRuntime wfRuntime;

    @Override
    public Promise<OperatorResult> process(Object event, WorkflowContext ctx) {
        // Fire a durable workflow for the ingest-and-catalog pipeline
        return wfRuntime.launch("data-cloud.ingest", ctx, ctx.getTenantId())
                        .map(run -> OperatorResult.single(Map.of("ingestRunId", run.runId())));
    }
}
```

### 10.2 AEP receives lifecycle events from durable workflows

Wire `AepWorkflowLifecycleListener` as a listener on `DurableWorkflowRuntime`:
```java
// AEP publishes these as cloud events into the AEP event bus.
// Other AEP operators can subscribe to workflow.lifecycle.* events.
runtime.addListener(event ->
    aepEventBus.publish("workflow.lifecycle." + event.phase().name().toLowerCase(), event));
```

### 10.3 Product-specific AEP operators registered via `OPERATOR_PIPELINE` step kind

App-platform registers its AI enrichment pipelines in the `StepRegistry` under named pipeline refs:
```java
registry.register("pipeline:instrument-enrichment", new PipelineWorkflowStepAdapter(
    PipelineBuilder.of("instrument-enrichment")
        .source("classifier", new AiInstrumentClassifierOperator())
        .then("enricher",     new AiInstrumentEnrichmentOperator())
        .sink("publisher",    new AepEventPublisherOperator())
        .build(),
    dagPipelineExecutor
));
```

---

## 11. Other Products: Usage Patterns

### 11.1 A product needing only ephemeral pipelines (e.g. simple AEP extension)

```kotlin
// build.gradle.kts
implementation(project(":platform:java:workflow"))   // only this
```

Use `PipelineBuilder` + `DAGPipelineExecutor` directly. No database needed.

### 11.2 A product needing durable workflows with no custom expression language

```kotlin
implementation(project(":platform:java:workflow"))
implementation(project(":platform:java:workflow-runtime"))
implementation(project(":platform:java:workflow-jdbc"))
```

Use `DurableWorkflowRuntime` with `JdbcWorkflowStateStore` and the built-in `CelWorkflowExpressionEvaluator`. Register steps in `StepRegistry`, define workflows as `WorkflowDefinition` records or YAML loaded at startup.

### 11.3 A product needing durable workflows with custom expressions (e.g. tutorputor curriculum sequencing)

Implement `WorkflowExpressionEvaluator` with domain-specific functions:
```java
class CurriculumExpressionEvaluator implements WorkflowExpressionEvaluator {
    @Override
    public String evaluate(String expression, WorkflowContext context) {
        // Register custom functions: studentLevel(), completionRate(), prerequisitesMet()
        // Delegate to CEL with these custom functions registered
    }
}
```

### 11.4 A product needing AEP-backed wait coordination (event-driven resume)

Implement `WorkflowWaitCoordinator` that registers event subscriptions in AEP:
```java
class AepWorkflowWaitCoordinator implements WorkflowWaitCoordinator {
    private final AepEventCloudClient aepClient;

    @Override
    public void registerEventWait(String runId, String eventType, String correlationKey, Duration timeout) {
        aepClient.subscribe(eventType, event -> {
            if (event.getCorrelationKey().equals(correlationKey)) {
                signal(runId, Map.of("eventPayload", event));
            }
        });
    }
}
```

This eliminates the JDBC polling loop for event-driven workflows.

---

## 12. What Does NOT Move to the Platform

These remain product-specific, in the product codebase:

| Concern | Stays In | Reason |
|---------|---------|--------|
| `TradeSettlementWorkflowService` | `app-platform/domain-packs/...` | Domain business logic, not generic |
| `ReconciliationOrchestrationWorkflowService` | `app-platform/kernel/...` | Finance-domain specific |
| `CorporateActionWorkflowService` | `app-platform/kernel/...` | Finance-domain specific |
| `RegulatoryReportSubmissionWorkflowService` | `app-platform/kernel/...` | Finance-domain specific |
| `BundleImportWorkflowService` | `app-platform/domain-packs/sanctions/...` | Sanctions-domain specific |
| `ApprovalWorkflowService` (OMS) | `app-platform/domain-packs/oms/...` | OMS-domain specific |
| Workflow YAML/JSON definitions | Each product's resources | Vary per product business process |
| Product-specific `WorkflowStep` implementations | Each product | Domain logic (KYC, ledger, etc.) |

These all **use** `DurableWorkflowRuntime` from the platform but are not part of the platform.

---

## 13. Reporting and Observability

### 13.1 Built-in `MetricsWorkflowListener`

Provided in `workflow-runtime`, automatically instruments:
```
workflow.run.started_total             {workflowId, kind, tenantId}
workflow.run.completed_total           {workflowId, kind, tenantId}
workflow.run.failed_total              {workflowId, kind, tenantId, failureReason}
workflow.run.duration_seconds          {workflowId, kind} histogram (p50, p95, p99)
workflow.step.duration_seconds         {workflowId, stepId, stepKind} histogram
workflow.step.retry_total              {workflowId, stepId}
workflow.wait.registered_total         {workflowId, waitKind}
workflow.run.active_count              gauge (per workflowId)
workflow.sla.breach_total              {workflowId} (emitted when p95 > SLA threshold)
```

### 13.2 Built-in `AuditWorkflowListener`

Emits a structured audit record to `AuditPort` for:
- `WORKFLOW_STARTED` — actor, workflowId, runId, tenantId, trigger
- `WORKFLOW_COMPLETED` / `WORKFLOW_FAILED` / `WORKFLOW_CANCELLED`
- `WORKFLOW_WAIT_REGISTERED` — audit the pause
- `WORKFLOW_RESUMED` — audit who/what signaled the resume
- `WORKFLOW_COMPENSATING` / `WORKFLOW_COMPENSATED` — saga reversal audit trail

### 13.3 Structured step log

The `workflow_lifecycle_event` table (Section 7.3) provides a full, queryable step execution
history per run. Products query this for: run replay, SLA reporting, debugging failed runs.

---

## 14. Implementation Roadmap

### Sprint 1 (2 weeks) — Platform foundation
- [ ] Add new types to `platform:java:workflow`: `WorkflowKind`, `WorkflowOptions`, `WorkflowLifecycleEvent`, `WorkflowLifecycleListener`, `WorkflowRunStatus`, `WorkflowRun`, `WorkflowStateStore`, `WorkflowExpressionEvaluator`, `WorkflowWaitCoordinator`
- [ ] Update `DurableWorkflowEngine` to emit `WorkflowLifecycleEvent` to registered listeners
- [ ] Extend `WorkflowStateStore` with `findByWorkflowId` and `findExpiredWaits` methods
- [ ] Create `platform:java:workflow-runtime` module scaffold (build.gradle.kts, package structure)
- [ ] Implement `WorkflowDefinition`, `WorkflowStepDefinition`, `WorkflowStepKind`
- [ ] Implement `InMemoryWorkflowDefinitionRegistry`

### Sprint 2 (3 weeks) — Durable runtime
- [ ] Implement `DurableWorkflowRuntime` FSM (launch, resume, cancel, suspend)
- [ ] Implement `CelWorkflowExpressionEvaluator` (extract from app-platform's `CelExpressionEvaluatorService`)
- [ ] Implement `ParallelStepExecutor` (extract from app-platform's `ParallelStepExecutionService`)
- [ ] Implement `WaitStepEngine` (extract from app-platform's `WaitCorrelationStepService`)
- [ ] Implement `SubWorkflowComposer` (extract from app-platform's `SubWorkflowCompositionService`)
- [ ] Implement `MetricsWorkflowListener` and `AuditWorkflowListener`
- [ ] Unit test suite for durable runtime

### Sprint 3 (2 weeks) — JDBC state store
- [ ] Create `platform:java:workflow-jdbc` module
- [ ] Implement `JdbcWorkflowStateStore`
- [ ] Implement `JdbcWorkflowDefinitionRegistry`
- [ ] Implement `JdbcWorkflowWaitCoordinator` (with scheduled expiry poller)
- [ ] Write canonical DDL schema (Section 7.3)
- [ ] Integration tests with Testcontainers PostgreSQL

### Sprint 4 (2 weeks) — App-platform migration
- [ ] Add `workflow-runtime` and `workflow-jdbc` to `workflow-orchestration/build.gradle.kts`
- [ ] Wire `JdbcWorkflowStateStore` (replace direct JDBC in `WorkflowExecutionRuntimeService`)
- [ ] Wire `DurableWorkflowRuntime` (replace `WorkflowExecutionRuntimeService` FSM dispatch)
- [ ] Register all `TASK` step refs in `StepRegistry`
- [ ] Delete 15 inner `AuditPort` / `EventBusPort` duplicates
- [ ] Wire `AuditWorkflowListener` (all 6 missing audit events now covered automatically)
- [ ] Wire `AepWorkflowLifecycleListener` (replaces `EventBusPort` with AEP)

### Sprint 5 (1 week) — AEP interop
- [ ] Implement `PipelineWorkflowStepAdapter` (OPERATOR_PIPELINE step type)
- [ ] Implement `AepWorkflowWaitCoordinator` (event-driven resume via AEP)
- [ ] Implement `AepWorkflowLifecycleListener` (publish lifecycle events to AEP)
- [ ] Register app-platform AI enrichment pipelines as OPERATOR_PIPELINE steps

### Future
- [ ] `RedisWorkflowStateStore` (for high-throughput ephemeral workflows with TTL)
- [ ] `WorkflowDslParser` for YAML/JSON definition files (products ship .yaml in classpath)
- [ ] Workflow UI integration (definition browser + run monitor) using workflow_run + workflow_lifecycle_event tables
- [ ] `platform:typescript:workflow-client` for UI products to query run status via REST

---

## 15. Summary: Compatibility Matrix

| Product | Ephemeral Pipeline | Durable FSM | WAIT Steps | AEP Interop | JDBC State |
|---------|:-----------------:|:-----------:|:----------:|:-----------:|:----------:|
| **app-platform** | via `workflow` | via `workflow-runtime` | ✓ | ✓ (Phase 2) | via `workflow-jdbc` |
| **AEP** | ✓ (native Pipeline) | via `workflow-runtime` | via AepWaitCoordinator | ✓ (native) | optional |
| **audio-video** (transcoding) | ✓ (short pipeline) | not needed | — | optional | — |
| **tutorputor** (curriculum) | ✓ (assessment pipeline) | via `workflow-runtime` | ✓ (human checkpoint) | optional | via `workflow-jdbc` |
| **flashit** (spaced repetition) | ✓ (review pipeline) | not needed | — | optional | — |
| **dcmaar** (compliance) | ✓ | via `workflow-runtime` | ✓ | ✓ | via `workflow-jdbc` |
| **software-org** (CI/CD) | ✓ (build pipeline) | via `workflow-runtime` | ✓ (approval gate) | ✓ | via `workflow-jdbc` |
| **data-cloud** (ingest) | ✓ | via `workflow-runtime` | optional | ✓ | via `workflow-jdbc` |
