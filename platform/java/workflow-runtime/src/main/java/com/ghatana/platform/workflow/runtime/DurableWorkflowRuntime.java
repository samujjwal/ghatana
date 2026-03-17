/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.workflow.*;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Definition-driven durable workflow runtime.
 *
 * <p>Resolves {@link WorkflowDefinition} from a {@link WorkflowDefinitionRegistry},
 * then walks through the step graph respecting step kinds (ACTION, DECISION, WAIT,
 * PARALLEL, SUB_WORKFLOW). Emits {@link WorkflowLifecycleEvent lifecycle events}
 * at each transition.
 *
 * <p>Uses the pluggable {@link WorkflowStateStore} for durable persistence and
 * {@link StepOperatorRegistry} for operator resolution.
 *
 * <p>Thread-safe: all mutable state is either in the state store or local to a
 * single run execution on a virtual thread.
 *
 * @doc.type class
 * @doc.purpose Definition-driven durable workflow runtime with step-graph execution
 * @doc.layer platform
 * @doc.pattern Orchestrator
 */
public final class DurableWorkflowRuntime {
    private static final Logger log = LoggerFactory.getLogger(DurableWorkflowRuntime.class);

    private final WorkflowDefinitionRegistry definitionRegistry;
    private final WorkflowStateStore stateStore;
    private final StepOperatorRegistry operatorRegistry;
    private final WorkflowExpressionEvaluator expressionEvaluator;
    private final List<WorkflowLifecycleListener> listeners;
    private final ExecutorService executor;
    private final Duration defaultTimeout;
    private final int defaultMaxRetries;

    private DurableWorkflowRuntime(Builder builder) {
        this.definitionRegistry = Objects.requireNonNull(builder.definitionRegistry, "definitionRegistry");
        this.stateStore = Objects.requireNonNull(builder.stateStore, "stateStore");
        this.operatorRegistry = Objects.requireNonNull(builder.operatorRegistry, "operatorRegistry");
        this.expressionEvaluator = builder.expressionEvaluator;
        this.listeners = new CopyOnWriteArrayList<>(builder.listeners);
        this.executor = builder.executor != null
            ? builder.executor
            : Executors.newVirtualThreadPerTaskExecutor();
        this.defaultTimeout = builder.defaultTimeout;
        this.defaultMaxRetries = builder.defaultMaxRetries;
    }

    /**
     * Starts a workflow run for the given workflow ID.
     *
     * @param workflowId     the workflow definition ID
     * @param tenantId       tenant isolation ID
     * @param correlationId  optional correlation ID for tracing
     * @param initialContext  initial context data
     * @return a promise of the completed {@link WorkflowRun}
     */
    public Promise<WorkflowRun> start(
            @NotNull String workflowId,
            @NotNull String tenantId,
            @NotNull String correlationId,
            @NotNull Map<String, Object> initialContext) {

        return definitionRegistry.findLatest(workflowId)
            .then(optDef -> {
                if (optDef.isEmpty()) {
                    return Promise.ofException(
                        new WorkflowDefinitionException("Workflow not found: " + workflowId));
                }

                WorkflowDefinition def = optDef.get();
                if (!def.enabled()) {
                    return Promise.ofException(
                        new WorkflowDefinitionException("Workflow is disabled: " + workflowId));
                }

                String runId = UUID.randomUUID().toString();
                WorkflowRun run = new WorkflowRun(
                    runId, workflowId, tenantId, WorkflowKind.DURABLE,
                    WorkflowRunStatus.PENDING, WorkflowOptions.durable(),
                    Instant.now(), null, def.entryStepId(), initialContext,
                    null, correlationId, List.of());

                return stateStore.save(run)
                    .then(v -> executeRun(run, def));
            });
    }

    /**
     * Signals a waiting workflow run to resume.
     *
     * @param runId   the run to signal
     * @param signal  signal payload merged into context
     * @return a promise of the resumed run
     */
    public Promise<WorkflowRun> signal(@NotNull String runId, @NotNull Map<String, Object> signal) {
        return stateStore.findByRunId(runId)
            .then(optRun -> {
                if (optRun.isEmpty()) {
                    return Promise.ofException(
                        new WorkflowDefinitionException("Run not found: " + runId));
                }
                WorkflowRun run = optRun.get();
                if (run.status() != WorkflowRunStatus.WAITING) {
                    return Promise.ofException(
                        new IllegalStateException("Run is not in WAITING state: " + run.status()));
                }

                // Merge signal into context
                Map<String, Object> mergedCtx = new HashMap<>(run.variables());
                mergedCtx.putAll(signal);

                WorkflowRun resumed = new WorkflowRun(
                    run.runId(), run.workflowId(), run.tenantId(), run.kind(),
                    WorkflowRunStatus.RUNNING, run.options(),
                    run.startedAt(), null, run.currentStepId(), mergedCtx,
                    null, run.triggeredBy(), run.history());

                return stateStore.save(resumed)
                    .then(v -> definitionRegistry.findLatest(run.workflowId()))
                    .then(optDef -> {
                        if (optDef.isEmpty()) {
                            return Promise.ofException(
                                new WorkflowDefinitionException(
                                    "Workflow not found: " + run.workflowId()));
                        }
                        return executeRun(resumed, optDef.get());
                    });
            });
    }

    // ── Core execution ───────────────────────────────────────────────────

    private Promise<WorkflowRun> executeRun(WorkflowRun run, WorkflowDefinition def) {
        return Promise.ofBlocking(executor, () -> {
            emitEvent(WorkflowLifecycleEvent.of(
                run.runId(), run.workflowId(),
                WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED));

            WorkflowRun current = run.withStatus(WorkflowRunStatus.RUNNING);
            // awaitBlocking is safe here: we are on a virtual thread inside Promise.ofBlocking,
            // so the eventloop continues pumping while this thread waits on the latch.
            awaitBlocking(stateStore.save(current));

            String stepId = current.currentStepId();
            Map<String, Object> ctx = new HashMap<>(current.variables());

            while (stepId != null) {
                Optional<WorkflowStepDefinition> optStep = def.findStep(stepId);
                if (optStep.isEmpty()) {
                    throw new WorkflowDefinitionException("Step not found: " + stepId);
                }

                WorkflowStepDefinition stepDef = optStep.get();

                emitEvent(WorkflowLifecycleEvent.forStep(
                    current.runId(), current.workflowId(),
                    WorkflowLifecycleEvent.Phase.STEP_STARTED, stepDef.stepId()));

                try {
                    stepId = executeStep(current, stepDef, ctx, def);

                    emitEvent(WorkflowLifecycleEvent.forStep(
                        current.runId(), current.workflowId(),
                        WorkflowLifecycleEvent.Phase.STEP_COMPLETED, stepDef.stepId()));

                    // Update run with new step and current variables
                    current = current.withCurrentStep(stepId).withVariables(ctx);
                    awaitBlocking(stateStore.save(current));

                } catch (WaitSuspendException e) {
                    // WAIT step — persist and return
                    current = current.withStatus(WorkflowRunStatus.WAITING)
                                     .withCurrentStep(e.nextStepId())
                                     .withVariables(ctx);
                    awaitBlocking(stateStore.save(current));

                    emitEvent(WorkflowLifecycleEvent.forStep(
                        current.runId(), current.workflowId(),
                        WorkflowLifecycleEvent.Phase.WORKFLOW_WAITING, stepDef.stepId()));

                    return current;

                } catch (Exception e) {
                    emitEvent(WorkflowLifecycleEvent.forStep(
                        current.runId(), current.workflowId(),
                        WorkflowLifecycleEvent.Phase.STEP_FAILED, stepDef.stepId()));

                    current = current.withStatus(WorkflowRunStatus.FAILED)
                                     .withError(e.getMessage());
                    awaitBlocking(stateStore.save(current));

                    emitEvent(WorkflowLifecycleEvent.of(
                        current.runId(), current.workflowId(),
                        WorkflowLifecycleEvent.Phase.WORKFLOW_FAILED));

                    return current;
                }
            }

            // All steps done
            current = current.withVariables(ctx)
                             .withCompleted(WorkflowRunStatus.COMPLETED, Instant.now());
            awaitBlocking(stateStore.save(current));

            emitEvent(WorkflowLifecycleEvent.of(
                current.runId(), current.workflowId(),
                WorkflowLifecycleEvent.Phase.WORKFLOW_COMPLETED));

            return current;
        });
    }

    private String executeStep(
            WorkflowRun run,
            WorkflowStepDefinition stepDef,
            Map<String, Object> ctx,
            WorkflowDefinition def) throws Exception {

        return switch (stepDef.kind()) {
            case ACTION -> executeAction(stepDef, ctx);
            case DECISION -> executeDecision(stepDef, ctx);
            case WAIT -> executeWait(stepDef);
            case END -> null; // Terminal
            case PARALLEL -> executeParallel(stepDef, ctx, def);
            case SUB_WORKFLOW -> executeSubWorkflow(stepDef, ctx);
            case LOOP -> executeLoop(stepDef, ctx, def);
            case COMPENSATION -> executeAction(stepDef, ctx); // Compensation runs like action
        };
    }

    private String executeAction(WorkflowStepDefinition stepDef, Map<String, Object> ctx)
            throws Exception {
        String operatorId = stepDef.operatorId();
        if (operatorId == null) {
            throw new WorkflowDefinitionException(
                "ACTION step '%s' has no operatorId".formatted(stepDef.stepId()));
        }

        StepOperatorRegistry.StepOperator operator = operatorRegistry.find(operatorId);
        if (operator == null) {
            throw new WorkflowDefinitionException(
                "Operator not found: '%s' for step '%s'".formatted(operatorId, stepDef.stepId()));
        }

        int maxRetries = stepDef.maxRetries() > 0 ? stepDef.maxRetries() : defaultMaxRetries;
        Duration backoff = stepDef.retryBackoff() != null
            ? stepDef.retryBackoff()
            : Duration.ofSeconds(1);

        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    long sleepMs = backoff.toMillis() * (1L << (attempt - 1));
                    log.info("Retrying step {} (attempt {}/{}), backoff {}ms",
                        stepDef.stepId(), attempt + 1, maxRetries + 1, sleepMs);
                    Thread.sleep(sleepMs);
                }

                Map<String, Object> result = awaitBlocking(operator.execute(ctx, stepDef.config()));
                if (result != null) {
                    ctx.putAll(result);
                }
                return stepDef.nextStep();

            } catch (Exception e) {
                lastException = e;
            }
        }
        throw lastException;
    }

    private String executeDecision(WorkflowStepDefinition stepDef, Map<String, Object> ctx) {
        if (expressionEvaluator == null) {
            throw new WorkflowDefinitionException(
                "DECISION step '%s' requires an expression evaluator".formatted(stepDef.stepId()));
        }
        if (stepDef.celCondition() == null) {
            throw new WorkflowDefinitionException(
                "DECISION step '%s' has no celCondition".formatted(stepDef.stepId()));
        }

        WorkflowContext wfCtx = new MapWorkflowContext("", "", "", ctx);
        boolean result = expressionEvaluator.evaluateBoolean(stepDef.celCondition(), wfCtx);
        return result ? stepDef.nextStepOnTrue() : stepDef.nextStepOnFalse();
    }

    private String executeWait(WorkflowStepDefinition stepDef) throws WaitSuspendException {
        // Suspend the run — the caller (signal()) will resume later
        throw new WaitSuspendException(stepDef.nextStep());
    }

    private String executeParallel(
            WorkflowStepDefinition stepDef,
            Map<String, Object> ctx,
            WorkflowDefinition def) throws Exception {
        // Parallel steps are defined via config["parallelStepIds"] as a comma-separated list
        Object parallelIds = stepDef.config().get("parallelStepIds");
        if (parallelIds == null) {
            log.warn("PARALLEL step {} has no parallelStepIds config", stepDef.stepId());
            return stepDef.nextStep();
        }

        String[] stepIds = parallelIds.toString().split(",");
        List<Promise<Void>> promises = new ArrayList<>();
        for (String sid : stepIds) {
            String trimmed = sid.trim();
            Optional<WorkflowStepDefinition> child = def.findStep(trimmed);
            if (child.isEmpty()) {
                throw new WorkflowDefinitionException("Parallel child step not found: " + trimmed);
            }
            // Execute each child as a blocking action on the same context (thread-safe copy)
            Map<String, Object> childCtx = new HashMap<>(ctx);
            promises.add(Promise.ofBlocking(executor, () -> {
                executeAction(child.get(), childCtx);
                synchronized (ctx) {
                    ctx.putAll(childCtx);
                }
                return (Void) null;
            }));
        }

        // Join all — block this virtual thread until all parallel branches complete
        awaitBlocking(Promises.toList(promises));
        return stepDef.nextStep();
    }

    private String executeSubWorkflow(WorkflowStepDefinition stepDef, Map<String, Object> ctx)
            throws Exception {
        if (stepDef.subWorkflowId() == null) {
            throw new WorkflowDefinitionException(
                "SUB_WORKFLOW step '%s' has no subWorkflowId".formatted(stepDef.stepId()));
        }

        // Start sub-workflow and block until complete (we are already on a virtual thread)
        WorkflowRun subRun = awaitBlocking(start(
            stepDef.subWorkflowId(),
            "sub-tenant", // inherit from parent in production
            UUID.randomUUID().toString(),
            ctx));

        if (subRun.status() == WorkflowRunStatus.COMPLETED) {
            ctx.putAll(subRun.variables());
        } else if (subRun.status() == WorkflowRunStatus.FAILED) {
            throw new RuntimeException(
                "Sub-workflow '%s' failed: %s".formatted(stepDef.subWorkflowId(), subRun.errorMessage()));
        }
        return stepDef.nextStep();
    }

    private String executeLoop(
            WorkflowStepDefinition stepDef,
            Map<String, Object> ctx,
            WorkflowDefinition def) {
        if (expressionEvaluator == null || stepDef.celCondition() == null) {
            throw new WorkflowDefinitionException(
                "LOOP step '%s' requires an expression evaluator and celCondition"
                    .formatted(stepDef.stepId()));
        }

        int maxIterations = 1000; // Safety cap
        int i = 0;
        WorkflowContext wfCtx = new MapWorkflowContext("", "", "", ctx);
        while (expressionEvaluator.evaluateBoolean(stepDef.celCondition(), wfCtx) && i++ < maxIterations) {
            // Execute the loop body (config["bodyStepId"])
            String bodyStepId = (String) stepDef.config().get("bodyStepId");
            if (bodyStepId == null) break;
            Optional<WorkflowStepDefinition> body = def.findStep(bodyStepId);
            if (body.isEmpty()) break;
            try {
                executeAction(body.get(), ctx);
            } catch (Exception e) {
                throw new RuntimeException("Loop body step failed: " + e.getMessage(), e);
            }
        }
        return stepDef.nextStep();
    }

    // ── Event emission ───────────────────────────────────────────────────

    private void emitEvent(WorkflowLifecycleEvent event) {
        for (WorkflowLifecycleListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.warn("Lifecycle listener failed for event [{}]: {}",
                    event.phase(), e.getMessage(), e);
            }
        }
    }

    // ── Blocking bridge helper ────────────────────────────────────────────

    /**
     * Blocks the current non-eventloop thread (a virtual thread inside
     * {@code Promise.ofBlocking}) until the given Promise completes, then
     * returns its result or rethrows its exception.
     *
     * <p><strong>MUST NOT</strong> be called from the ActiveJ eventloop thread —
     * only from threads dispatched by {@code Promise.ofBlocking}.
     *
     * @param promise the promise to await
     * @param <T>     result type
     * @return the completed result
     * @throws Exception if the promise completes exceptionally or times out
     */
    private static <T> T awaitBlocking(Promise<T> promise) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        // whenComplete callback is invoked on the eventloop thread when the promise settles.
        // The CountDownLatch signals the waiting virtual thread with proper memory visibility.
        promise.whenComplete((result, error) -> {
            resultRef.set(result);
            errorRef.set(error);
            latch.countDown();
        });

        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new RuntimeException(
                "awaitBlocking: Promise did not complete within 60 seconds — possible deadlock");
        }

        Throwable error = errorRef.get();
        if (error != null) {
            if (error instanceof Exception ex) throw ex;
            throw new RuntimeException("Promise completed with non-Exception throwable", error);
        }
        return resultRef.get();
    }

    // ── Exception for WAIT suspension ────────────────────────────────────

    private static final class WaitSuspendException extends Exception {
        private final String nextStepId;

        WaitSuspendException(String nextStepId) {
            super("Wait suspension");
            this.nextStepId = nextStepId;
        }

        String nextStepId() { return nextStepId; }
    }

    // ── Builder ──────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private WorkflowDefinitionRegistry definitionRegistry;
        private WorkflowStateStore stateStore;
        private StepOperatorRegistry operatorRegistry;
        private WorkflowExpressionEvaluator expressionEvaluator;
        private final List<WorkflowLifecycleListener> listeners = new ArrayList<>();
        private ExecutorService executor;
        private Duration defaultTimeout = Duration.ofMinutes(5);
        private int defaultMaxRetries = 3;

        public Builder definitionRegistry(WorkflowDefinitionRegistry r) { this.definitionRegistry = r; return this; }
        public Builder stateStore(WorkflowStateStore s) { this.stateStore = s; return this; }
        public Builder operatorRegistry(StepOperatorRegistry r) { this.operatorRegistry = r; return this; }
        public Builder expressionEvaluator(WorkflowExpressionEvaluator e) { this.expressionEvaluator = e; return this; }
        public Builder addListener(WorkflowLifecycleListener l) { this.listeners.add(l); return this; }
        public Builder executor(ExecutorService e) { this.executor = e; return this; }
        public Builder defaultTimeout(Duration d) { this.defaultTimeout = d; return this; }
        public Builder defaultMaxRetries(int n) { this.defaultMaxRetries = n; return this; }

        public DurableWorkflowRuntime build() {
            return new DurableWorkflowRuntime(this);
        }
    }
}
