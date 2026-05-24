package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.platform.workflow.DefaultWorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine.StepDefinition;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine.WorkflowExecution;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * DMOS bridge adapter wrapping the platform {@link DurableWorkflowEngine}.
 *
 * <p>Translates DMOS-domain workflow requests into platform-level workflow
 * executions with per-step retry, compensation, and observability. Callers
 * interact exclusively with DMOS contracts; the platform engine is an
 * implementation detail hidden behind this adapter.
 *
 * @doc.type class
 * @doc.purpose Bridge adapter wrapping DurableWorkflowEngine for DMOS workflow execution (KERNEL-P1-1)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class DmWorkflowEngineAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(DmWorkflowEngineAdapter.class);

    private static final int DEFAULT_STEP_MAX_RETRIES = 3;
    private static final Duration DEFAULT_STEP_RETRY_BACKOFF = Duration.ofSeconds(2);
    private static final Duration DEFAULT_STEP_TIMEOUT = Duration.ofMinutes(5);

    private final DurableWorkflowEngine engine;

    /**
     * Creates a new adapter backed by the provided engine instance.
     *
     * @param engine the durable workflow engine to delegate to
     */
    public DmWorkflowEngineAdapter(DurableWorkflowEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    /**
     * Submits a named DMOS workflow for durable execution.
     *
     * <p>Each step is wrapped with the configured defaults (3 retries, 2-second
     * backoff, 5-minute timeout). The returned promise resolves when all steps
     * have completed successfully, or fails with the last step exception after
     * exhausting retries (compensation is triggered automatically by the engine).
     *
     * @param ctx        the DMOS operation context carrying tenant and correlation info
     * @param workflowId a unique identifier for this workflow run
     * @param steps      the ordered list of workflow steps to execute
     * @return a promise resolving to {@code true} when the workflow completes
     */
    public Promise<Boolean> submit(DmOperationContext ctx, String workflowId, List<WorkflowStep> steps) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(steps, "steps must not be null");
        if (steps.isEmpty()) {
            LOG.warn("[DMOS] Workflow submitted with no steps: workflowId={} correlationId={}",
                workflowId, ctx.getCorrelationId().getValue());
            return Promise.of(Boolean.TRUE);
        }

        DefaultWorkflowContext workflowCtx = new DefaultWorkflowContext(
            workflowId,
            ctx.getTenantId().getValue(),
            ctx.getCorrelationId().getValue()
        );

        List<StepDefinition> stepDefs = steps.stream()
            .map(step -> StepDefinition.of(step.getStepId(), step)
                .withRetries(DEFAULT_STEP_MAX_RETRIES, DEFAULT_STEP_RETRY_BACKOFF)
                .withTimeout(DEFAULT_STEP_TIMEOUT))
            .toList();

        LOG.info("[DMOS] Submitting durable workflow: workflowId={} steps={} tenantId={} correlationId={}",
            workflowId, steps.size(), ctx.getTenantId().getValue(), ctx.getCorrelationId().getValue());

        WorkflowExecution execution = engine.submit(workflowId, workflowCtx, stepDefs);

        return execution.result()
            .map(resultCtx -> {
                LOG.info("[DMOS] Workflow completed: workflowId={} correlationId={}",
                    workflowId, ctx.getCorrelationId().getValue());
                return true;
            })
            .mapException(ex -> {
                LOG.error("[DMOS] Workflow failed: workflowId={} correlationId={} error={}",
                    workflowId, ctx.getCorrelationId().getValue(), ex.getMessage());
                return ex;
            });
    }

}
