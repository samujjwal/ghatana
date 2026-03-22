/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.workflow.WorkflowLifecycleEvent;
import com.ghatana.platform.workflow.WorkflowLifecycleListener;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Workflow listener that monitors runtime invariants during workflow execution.
 *
 * <p>Checks for common safety violations at significant lifecycle boundaries:
 * <ul>
 *   <li><b>Workflow start</b>: Validates cost caps and timeout bounds are declared</li>
 *   <li><b>Step completion</b>: Checks accumulated cost against budget</li>
 *   <li><b>Workflow failure</b>: Records invariant context for post-mortem analysis</li>
 * </ul>
 *
 * <p>Violations are logged at WARN level. In production, downstream systems
 * (alert-manager, PagerDuty) should trigger on these log patterns.
 *
 * @doc.type class
 * @doc.purpose Runtime invariant monitoring for workflow execution
 * @doc.layer platform
 * @doc.pattern Observer
 */
public final class InvariantWorkflowListener implements WorkflowLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(InvariantWorkflowListener.class);

    private static final String ATTR_COST_CAP = "__costCapUsd";
    private static final String ATTR_ACCUMULATED_COST = "__accumulatedCostUsd";
    private static final String ATTR_MAX_STEPS = "__maxSteps";
    private static final String ATTR_STEP_COUNT = "__stepCount";

    @Override
    public void onEvent(@NotNull WorkflowLifecycleEvent event) {
        switch (event.phase()) {
            case WORKFLOW_STARTED -> checkBoundsPresent(event);
            case STEP_COMPLETED -> checkStepInvariants(event);
            case WORKFLOW_FAILED -> recordFailureContext(event);
            default -> { /* no invariant check needed */ }
        }
    }

    private void checkBoundsPresent(WorkflowLifecycleEvent event) {
        Map<String, Object> attrs = event.attributes();
        if (!attrs.containsKey(ATTR_COST_CAP)) {
            log.info("INVARIANT workflow_started runId={} — no cost cap declared, "
                    + "budget invariant will not be enforced", event.runId());
        }
        if (!attrs.containsKey(ATTR_MAX_STEPS)) {
            log.info("INVARIANT workflow_started runId={} — no max steps declared, "
                    + "step budget invariant will not be enforced", event.runId());
        }
    }

    private void checkStepInvariants(WorkflowLifecycleEvent event) {
        Map<String, Object> attrs = event.attributes();

        // Check cost budget
        Object costCapObj = attrs.get(ATTR_COST_CAP);
        Object accumulatedObj = attrs.get(ATTR_ACCUMULATED_COST);
        if (costCapObj instanceof Number costCap && accumulatedObj instanceof Number accumulated) {
            if (accumulated.doubleValue() > costCap.doubleValue()) {
                log.warn("INVARIANT budget_exceeded runId={} stepId={} "
                        + "accumulated=${} cap=${}",
                        event.runId(), event.stepId(),
                        accumulated.doubleValue(), costCap.doubleValue());
            }
        }

        // Check step count
        Object maxStepsObj = attrs.get(ATTR_MAX_STEPS);
        Object stepCountObj = attrs.get(ATTR_STEP_COUNT);
        if (maxStepsObj instanceof Number maxSteps && stepCountObj instanceof Number stepCount) {
            if (stepCount.intValue() > maxSteps.intValue()) {
                log.warn("INVARIANT step_budget_exceeded runId={} stepId={} "
                        + "count={} max={}",
                        event.runId(), event.stepId(),
                        stepCount.intValue(), maxSteps.intValue());
            }
        }
    }

    private void recordFailureContext(WorkflowLifecycleEvent event) {
        Map<String, Object> attrs = event.attributes();
        log.warn("INVARIANT workflow_failed_context runId={} workflowId={} tenantId={} "
                + "error={} accumulatedCost={} stepCount={}",
                event.runId(), event.workflowId(), event.tenantId(),
                event.errorMessage(),
                attrs.getOrDefault(ATTR_ACCUMULATED_COST, "unknown"),
                attrs.getOrDefault(ATTR_STEP_COUNT, "unknown"));
    }
}
