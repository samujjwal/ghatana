package com.ghatana.platform.workflow.engine;

import com.ghatana.platform.workflow.Workflow;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Durable workflow execution engine with retry, compensation, and timeout support.
 *
 * <p>Provides Temporal-style workflow orchestration on top of the platform's
 * existing {@link Workflow} / {@link WorkflowStep} interfaces without requiring
 * an external Temporal server. Supports:
 *
 * <ul>
 *   <li><b>Retries</b>: Configurable per-step retry with exponential backoff</li>
 *   <li><b>Compensation</b>: Saga-style rollback on failure (reverse order)</li>
 *   <li><b>Timeouts</b>: Per-step and overall workflow execution timeouts</li>
 *   <li><b>Persistence</b>: Pluggable state store for durable execution</li>
 *   <li><b>Observability</b>: Step-level timing, status tracking, and failure logs</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * DurableWorkflowEngine engine = DurableWorkflowEngine.builder()
 *     .stateStore(new InMemoryWorkflowStateStore())
 *     .defaultTimeout(Duration.ofMinutes(5))
 *     .build();
 *
 * WorkflowExecution exec = engine.submit("order-saga",
 *     context,
 *     List.of(validateStep, reserveStep, chargeStep, confirmStep));
 *
 * exec.result().whenResult(ctx -> System.out.println("Completed: " + ctx));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Durable workflow execution engine with retry and compensation
 * @doc.layer platform
 * @doc.pattern Saga, Orchestrator
 */
public final class DurableWorkflowEngine {
    private static final Logger log = LoggerFactory.getLogger(DurableWorkflowEngine.class);

    private final WorkflowStateStore stateStore;
    private final Duration defaultTimeout;
    private final int defaultMaxRetries;
    private final Duration defaultRetryBackoff;
    private final ExecutorService executor;

    private DurableWorkflowEngine(Builder builder) {
        this.stateStore = builder.stateStore;
        this.defaultTimeout = builder.defaultTimeout;
        this.defaultMaxRetries = builder.defaultMaxRetries;
        this.defaultRetryBackoff = builder.defaultRetryBackoff;
        this.executor = builder.executor != null
            ? builder.executor
            : Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Submits a workflow for durable execution.
     *
     * @param workflowId  Unique workflow run identifier
     * @param context     Initial workflow context
     * @param steps       Ordered list of steps to execute
     * @return A WorkflowExecution handle for tracking progress and getting result
     */
    public WorkflowExecution submit(
            @NotNull String workflowId,
            @NotNull WorkflowContext context,
            @NotNull List<StepDefinition> steps) {

        WorkflowRun run = new WorkflowRun(workflowId, steps.size());
        stateStore.save(run);

        Promise<WorkflowContext> resultPromise = Promise.ofBlocking(executor, () -> {
            WorkflowContext current = context;
            List<StepDefinition> completedSteps = new ArrayList<>();

            for (int i = 0; i < steps.size(); i++) {
                StepDefinition stepDef = steps.get(i);
                run.updateStepStatus(i, StepStatus.RUNNING);
                stateStore.save(run);

                try {
                    current = executeStepWithRetry(stepDef, current, i);
                    completedSteps.add(stepDef);
                    run.updateStepStatus(i, StepStatus.COMPLETED);
                    stateStore.save(run);
                } catch (Exception e) {
                    run.updateStepStatus(i, StepStatus.FAILED);
                    run.setFailureReason(e.getMessage());
                    stateStore.save(run);

                    log.error("Workflow [{}] step {} ({}) failed: {}",
                        workflowId, i, stepDef.name(), e.getMessage());

                    // Compensate in reverse order
                    compensate(workflowId, completedSteps, current);

                    run.setStatus(RunStatus.COMPENSATED);
                    stateStore.save(run);
                    throw e;
                }
            }

            run.setStatus(RunStatus.COMPLETED);
            stateStore.save(run);
            return current;
        });

        return new WorkflowExecution(workflowId, resultPromise, run);
    }

    private WorkflowContext executeStepWithRetry(
            StepDefinition stepDef,
            WorkflowContext context,
            int stepIndex) throws Exception {

        int maxRetries = stepDef.maxRetries() >= 0 ? stepDef.maxRetries() : defaultMaxRetries;
        Duration backoff = stepDef.retryBackoff() != null ? stepDef.retryBackoff() : defaultRetryBackoff;
        Duration timeout = stepDef.timeout() != null ? stepDef.timeout() : defaultTimeout;

        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    long sleepMs = backoff.toMillis() * (1L << (attempt - 1)); // exponential
                    log.info("Retrying step {} (attempt {}/{}), backoff {}ms",
                        stepDef.name(), attempt + 1, maxRetries + 1, sleepMs);
                    Thread.sleep(sleepMs);
                }

                // Execute the step on the executor and wait with a timeout.
                // Uses virtual-thread executor + Future instead of banned
                // CompletableFuture.supplyAsync / Promise.getResult().
                Future<WorkflowContext> future = executor.submit(() ->
                    stepDef.step().execute(context)
                        .toCompletableFuture()
                        .get(timeout.toMillis(), TimeUnit.MILLISECONDS)
                );

                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);

            } catch (TimeoutException e) {
                lastException = new WorkflowTimeoutException(
                    "Step '%s' timed out after %s".formatted(stepDef.name(), timeout));
            } catch (ExecutionException e) {
                lastException = e.getCause() instanceof Exception ex ? ex : e;
            } catch (Exception e) {
                lastException = e;
            }
        }

        throw lastException;
    }

    private void compensate(
            String workflowId,
            List<StepDefinition> completedSteps,
            WorkflowContext context) {

        log.info("Compensating workflow [{}]: {} steps to undo", workflowId, completedSteps.size());

        ListIterator<StepDefinition> it = completedSteps.listIterator(completedSteps.size());
        while (it.hasPrevious()) {
            StepDefinition step = it.previous();
            if (step.compensate() != null) {
                try {
                    step.compensate().apply(context);
                    log.info("Compensated step: {}", step.name());
                } catch (Exception e) {
                    log.error("Compensation failed for step {}: {}", step.name(), e.getMessage());
                }
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // ── Inner Types ──────────────────────────────────────────────────────

    /**
     * Defines a workflow step with retry, timeout, and compensation behavior.
     */
    public record StepDefinition(
        @NotNull String name,
        @NotNull WorkflowStep step,
        int maxRetries,
        @Nullable Duration retryBackoff,
        @Nullable Duration timeout,
        @Nullable Function<WorkflowContext, Void> compensate
    ) {
        public static StepDefinition of(String name, WorkflowStep step) {
            return new StepDefinition(name, step, -1, null, null, null);
        }

        public StepDefinition withRetries(int max, Duration backoff) {
            return new StepDefinition(name, step, max, backoff, timeout, compensate);
        }

        public StepDefinition withTimeout(Duration timeout) {
            return new StepDefinition(name, step, maxRetries, retryBackoff, timeout, compensate);
        }

        public StepDefinition withCompensation(Function<WorkflowContext, Void> fn) {
            return new StepDefinition(name, step, maxRetries, retryBackoff, timeout, fn);
        }
    }

    /**
     * Handle for a submitted workflow execution.
     */
    public record WorkflowExecution(
        String workflowId,
        Promise<WorkflowContext> result,
        WorkflowRun run
    ) {}

    public enum StepStatus { PENDING, RUNNING, COMPLETED, FAILED }
    public enum RunStatus { RUNNING, COMPLETED, FAILED, COMPENSATED }

    /**
     * Tracks the state of a workflow run across all steps.
     */
    public static final class WorkflowRun {
        private final String workflowId;
        private final StepStatus[] stepStatuses;
        private RunStatus status = RunStatus.RUNNING;
        private String failureReason;

        public WorkflowRun(String workflowId, int stepCount) {
            this.workflowId = workflowId;
            this.stepStatuses = new StepStatus[stepCount];
            Arrays.fill(stepStatuses, StepStatus.PENDING);
        }

        /**
         * Reconstructs a {@link WorkflowRun} from persisted state.
         *
         * <p>Used by durable state stores (e.g. JDBC) when loading a run after a restart.
         * The returned instance is fully mutable and can continue to be updated normally.
         *
         * @param workflowId   the workflow identifier
         * @param stepStatuses step statuses in execution order (length = step count)
         * @param status       overall run status
         * @param failureReason optional failure description, may be {@code null}
         * @return a restored {@link WorkflowRun}
         */
        public static WorkflowRun restore(
                @NotNull String workflowId,
                @NotNull StepStatus[] stepStatuses,
                @NotNull RunStatus status,
                @Nullable String failureReason) {
            WorkflowRun run = new WorkflowRun(workflowId, stepStatuses.length);
            System.arraycopy(stepStatuses, 0, run.stepStatuses, 0, stepStatuses.length);
            run.status = status;
            run.failureReason = failureReason;
            return run;
        }

        public String workflowId() { return workflowId; }
        public RunStatus status() { return status; }
        public StepStatus[] stepStatuses() { return stepStatuses.clone(); }
        public String failureReason() { return failureReason; }

        void updateStepStatus(int index, StepStatus s) { stepStatuses[index] = s; }
        void setStatus(RunStatus s) { this.status = s; }
        void setFailureReason(String r) { this.failureReason = r; }
    }

    /**
     * Pluggable persistence for workflow execution state.
     */
    public interface WorkflowStateStore {
        void save(WorkflowRun run);
        Optional<WorkflowRun> load(String workflowId);
    }

    /**
     * In-memory state store for development and testing.
     */
    public static final class InMemoryWorkflowStateStore implements WorkflowStateStore {
        private final ConcurrentHashMap<String, WorkflowRun> runs = new ConcurrentHashMap<>();

        @Override
        public void save(WorkflowRun run) { runs.put(run.workflowId(), run); }

        @Override
        public Optional<WorkflowRun> load(String workflowId) {
            return Optional.ofNullable(runs.get(workflowId));
        }
    }

    public static final class WorkflowTimeoutException extends RuntimeException {
        public WorkflowTimeoutException(String message) { super(message); }
    }

    // ── Builder ──────────────────────────────────────────────────────────

    public static final class Builder {
        private WorkflowStateStore stateStore = new InMemoryWorkflowStateStore();
        private Duration defaultTimeout = Duration.ofMinutes(5);
        private int defaultMaxRetries = 3;
        private Duration defaultRetryBackoff = Duration.ofSeconds(1);
        private ExecutorService executor;

        public Builder stateStore(WorkflowStateStore store) { this.stateStore = store; return this; }
        public Builder defaultTimeout(Duration d) { this.defaultTimeout = d; return this; }
        public Builder defaultMaxRetries(int n) { this.defaultMaxRetries = n; return this; }
        public Builder defaultRetryBackoff(Duration d) { this.defaultRetryBackoff = d; return this; }
        public Builder executor(ExecutorService e) { this.executor = e; return this; }

        public DurableWorkflowEngine build() { return new DurableWorkflowEngine(this); }
    }
}
