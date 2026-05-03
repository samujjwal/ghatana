package com.ghatana.digitalmarketing.application.workflow;

import com.ghatana.digitalmarketing.application.DmosObservability;
import com.ghatana.digitalmarketing.application.command.DmCommandDispatcher;
import com.ghatana.digitalmarketing.application.command.DmCommandRepository;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandStatus;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowExecution;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowStatus;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowStep;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowStepStatus;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * DMOS workflow worker — drives step execution for active workflow executions.
 *
 * <p>The worker is designed to be called periodically (e.g., by a scheduled tick on the
 * ActiveJ event loop). On each tick it:</p>
 * <ol>
 *   <li>Loads up to {@code batchSize} active workflows for the given tenant.</li>
 *   <li>For each PENDING workflow, starts it (PENDING → RUNNING).</li>
 *   <li>For each RUNNING workflow, executes the current step via the {@link DmCommandDispatcher}.</li>
 *   <li>On step success, advances the workflow. When all steps complete the workflow is marked COMPLETED.</li>
 *   <li>On step failure, marks the workflow FAILED and records the failure reason.</li>
 * </ol>
 *
 * <p>The worker is single-threaded on the event loop. Step execution delegates blocking work to
 * the dispatcher/handler layer which wraps I/O in {@code Promise.ofBlocking(executor, ...)}.</p>
 *
 * <p>The worker does not retry commands exceeding {@link DmCommand#MAX_ATTEMPTS} — that is
 * governed by the command store. A step whose command is permanently failed causes the workflow
 * to fail.</p>
 *
 * <p>Emits structured logs and metrics for observability (DMOS-P1-011).</p>
 *
 * @doc.type class
 * @doc.purpose Drives step-by-step execution of active DMOS workflow executions (DMOS-P1-007)
 * @doc.layer product
 * @doc.pattern Worker, Scheduler
 */
public final class DmWorkflowWorker {

    private static final Logger LOG = LoggerFactory.getLogger(DmWorkflowWorker.class);

    private static final int DEFAULT_BATCH_SIZE = 20;

    private final DmWorkflowRepository workflowRepository;
    private final DmCommandRepository commandRepository;
    private final DmCommandDispatcher dispatcher;
    private final DmosObservability observability;
    private final int batchSize;

    public DmWorkflowWorker(
            DmWorkflowRepository workflowRepository,
            DmCommandRepository commandRepository,
            DmCommandDispatcher dispatcher,
            DmosObservability observability) {
        this(workflowRepository, commandRepository, dispatcher, observability, DEFAULT_BATCH_SIZE);
    }

    public DmWorkflowWorker(
            DmWorkflowRepository workflowRepository,
            DmCommandRepository commandRepository,
            DmCommandDispatcher dispatcher,
            DmosObservability observability,
            int batchSize) {
        this.workflowRepository = Objects.requireNonNull(workflowRepository, "workflowRepository must not be null");
        this.commandRepository  = Objects.requireNonNull(commandRepository, "commandRepository must not be null");
        this.dispatcher         = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        this.observability     = Objects.requireNonNull(observability, "observability must not be null");
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be > 0");
        this.batchSize = batchSize;
    }

    /**
     * Runs one tick: loads PENDING and active (RUNNING/PAUSED) workflows for {@code tenantId}
     * and drives each one forward.
     *
     * <p>Errors in individual workflows are caught and logged — a failure in one workflow
     * does not prevent the others from being processed.</p>
     *
     * @param tenantId tenant scope for this tick
     * @return a Promise that completes when all workflows in this tick have been processed
     */
    public Promise<Void> tick(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (tenantId.isBlank()) return Promise.ofException(
            new IllegalArgumentException("tenantId must not be blank"));

        MDC.put("tenantId", tenantId);

        // Create span for workflow worker tick (DMOS-P1-011)
        Span span = observability.createSpan("WORKFLOW_WORKER_TICK", "tenant.id", tenantId);
        Instant tickStartTime = Instant.now();

        try (Scope scope = span.makeCurrent()) {
            return Promises.toList(
                    workflowRepository.findByStatus(tenantId, DmWorkflowStatus.PENDING, batchSize),
                    workflowRepository.findActive(tenantId, batchSize))
                .then(lists -> {
                    java.util.LinkedHashSet<DmWorkflowExecution> seen = new java.util.LinkedHashSet<>();
                    lists.forEach(seen::addAll);
                    List<DmWorkflowExecution> workflows = List.copyOf(seen);

                    span.setAttribute("workflow.count", workflows.size());

                    if (workflows.isEmpty()) {
                        return Promise.of(null);
                    }
                    LOG.debug("[DMOS-WORKER] tick tenant={} workflows-to-process={}", tenantId, workflows.size());
                    List<Promise<Void>> tasks = workflows.stream()
                        .map(wf -> processWorkflow(wf)
                            .whenException(e -> LOG.error(
                                "[DMOS-WORKER] error processing workflow id={} tenant={}: {}",
                                wf.getId(), tenantId, e.getMessage(), e)))
                        .toList();
                    return Promises.all(tasks).toVoid();
                })
                .whenComplete((result, e) -> {
                    long duration = ChronoUnit.MILLIS.between(tickStartTime, Instant.now());
                    span.setAttribute("duration.ms", duration);
                    LOG.debug("[DMOS-WORKER] tick completed in {}ms", duration);
                    span.end();
                    MDC.clear();
                })
                .whenException(e -> {
                    long duration = ChronoUnit.MILLIS.between(tickStartTime, Instant.now());
                    span.recordException(e);
                    span.setAttribute("duration.ms", duration);
                    LOG.error("[DMOS-WORKER] tick failed in {}ms: {}", duration, e.getMessage(), e);
                    span.end();
                    MDC.clear();
                });
        } catch (Exception e) {
            span.recordException(e);
            span.end();
            MDC.clear();
            return Promise.ofException(e);
        }
    }

    // ── Private step execution logic ──────────────────────────────────────────

    private Promise<Void> processWorkflow(DmWorkflowExecution workflow) {
        MDC.put("workflowId", workflow.getId());
        MDC.put("workflowName", workflow.getName());

        Instant workflowStartTime = Instant.now();

        if (workflow.getStatus() == DmWorkflowStatus.PENDING) {
            return startWorkflow(workflow)
                .then(wf -> {
                    long duration = ChronoUnit.MILLIS.between(workflowStartTime, Instant.now());
                    observability.recordWorkflowDuration(workflow.getName(), duration);
                    return executeCurrentStep(wf).map(__ -> null);
                });
        }
        if (workflow.getStatus() == DmWorkflowStatus.RUNNING) {
            return executeCurrentStep(workflow)
                .whenComplete((result, e) -> {
                    long duration = ChronoUnit.MILLIS.between(workflowStartTime, Instant.now());
                    observability.recordWorkflowDuration(workflow.getName(), duration);
                });
        }
        return Promise.of(null);
    }

    private Promise<DmWorkflowExecution> startWorkflow(DmWorkflowExecution workflow) {
        LOG.info("[DMOS-WORKER] starting workflow id={} name={}", workflow.getId(), workflow.getName());
        return workflowRepository.update(workflow.start())
            .then(updated -> Promise.of(updated));
    }

    private Promise<Void> executeCurrentStep(DmWorkflowExecution workflow) {
        DmWorkflowStep step = workflow.currentStep();
        if (step == null || workflow.getCurrentStepIndex() >= workflow.getSteps().size()) {
            return completeWorkflow(workflow);
        }

        LOG.info("[DMOS-WORKER] executing step '{}' (index={}) for workflow id={}",
            step.getName(), workflow.getCurrentStepIndex(), workflow.getId());

        DmWorkflowStep executing = step.markExecuting();
        return workflowRepository.update(advanceStepInPlace(workflow, executing))
            .then(persisted -> resolveCommandForStep(step, persisted)
                .then(command -> dispatchAndTrack(command, persisted, executing)))
            .whenException(e -> {
                String reason = "Step '" + step.getName() + "' failed: " + e.getMessage();
                workflowRepository.update(workflow.fail(reason))
                    .whenException(re -> LOG.error(
                        "[DMOS-WORKER] failed to persist workflow failure id={}: {}",
                        workflow.getId(), re.getMessage(), re));
            });
    }

    private Promise<Void> dispatchAndTrack(
            DmCommand command,
            DmWorkflowExecution workflow,
            DmWorkflowStep executingStep) {

        return commandRepository.update(command.markExecuting())
            .then(executing -> dispatcher.dispatch(executing)
                .then(ignored -> commandRepository.update(executing.markSucceeded())
                    .then(succeeded -> {
                        DmWorkflowStep completed = executingStep.markCompleted();
                        DmWorkflowExecution advanced = workflow.advanceStep(completed);
                        boolean allDone = advanced.getCurrentStepIndex() >= advanced.getSteps().size();
                        if (allDone) {
                            return completeWorkflow(advanced);
                        }
                        return workflowRepository.update(advanced).toVoid();
                    }))
                .whenException(e -> {
                    DmWorkflowStep failed = executingStep.markFailed(e.getMessage());
                    String reason = "Step '" + executingStep.getName() + "' failed: " + e.getMessage();
                    commandRepository.update(command.markFailed(reason))
                        .whenException(re -> LOG.error(
                            "[DMOS-WORKER] failed to mark command failed id={}: {}",
                            command.getId(), re.getMessage(), re));
                    workflowRepository.update(workflow.fail(reason))
                        .whenException(re -> LOG.error(
                            "[DMOS-WORKER] failed to mark workflow failed id={}: {}",
                            workflow.getId(), re.getMessage(), re));
                }));
    }

    private Promise<Void> completeWorkflow(DmWorkflowExecution workflow) {
        if (workflow.isTerminal()) {
            return Promise.of(null);
        }
        LOG.info("[DMOS-WORKER] completing workflow id={} name={}", workflow.getId(), workflow.getName());
        return workflowRepository.update(workflow.complete()).toVoid();
    }

    /**
     * Resolves the command record for a step. The step name is used as a correlation handle
     * to find an existing PENDING command for this workflow's correlationId. If none is found
     * the step is treated as a no-op (already completed by a prior successful run).
     *
     * <p>Step names must match command correlationId patterns issued by the workflow initiator.</p>
     */
    private Promise<DmCommand> resolveCommandForStep(DmWorkflowStep step, DmWorkflowExecution workflow) {
        return commandRepository.findPending(workflow.getTenantId(), 100)
            .then(pending -> {
                DmCommand matched = pending.stream()
                    .filter(c -> workflow.getCorrelationId().equals(c.getCorrelationId())
                        && c.getStatus() == DmCommandStatus.PENDING)
                    .findFirst()
                    .orElse(null);

                if (matched != null) {
                    return Promise.of(matched);
                }

                LOG.warn("[DMOS-WORKER] no PENDING command for step='{}' workflow={} correlationId={}; treating as no-op",
                    step.getName(), workflow.getId(), workflow.getCorrelationId());
                return Promise.ofException(new IllegalStateException(
                    "No PENDING command for step '" + step.getName()
                    + "' in workflow " + workflow.getId()));
            });
    }

    /**
     * Returns an updated workflow with {@code updatedStep} replacing the step at
     * {@code workflow.getCurrentStepIndex()} without advancing the index.
     */
    private static DmWorkflowExecution advanceStepInPlace(
            DmWorkflowExecution workflow, DmWorkflowStep updatedStep) {
        java.util.List<DmWorkflowStep> steps = new java.util.ArrayList<>(workflow.getSteps());
        steps.set(workflow.getCurrentStepIndex(), updatedStep);
        return workflow.toBuilder().steps(steps).build();
    }
}
