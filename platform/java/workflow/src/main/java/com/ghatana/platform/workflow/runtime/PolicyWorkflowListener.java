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
 * Workflow listener that enforces governance policy context propagation.
 *
 * <p>Ensures that policy-relevant attributes (tenant ID, autonomy level,
 * action governance constraints) are present in workflow events. Logs
 * warnings when required attributes are missing, enabling detection of
 * governance configuration gaps.
 *
 * @doc.type class
 * @doc.purpose Governance policy context propagation listener
 * @doc.layer platform
 * @doc.pattern Observer
 */
public final class PolicyWorkflowListener implements WorkflowLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(PolicyWorkflowListener.class);

    private static final String ATTR_TENANT_ID = "__tenantId";
    private static final String ATTR_AUTONOMY_LEVEL = "__autonomyLevel";
    private static final String ATTR_MAX_DELEGATION_DEPTH = "__maxDelegationDepth";

    @Override
    public void onEvent(@NotNull WorkflowLifecycleEvent event) {
        switch (event.phase()) {
            case WORKFLOW_STARTED -> validateGovernanceContext(event);
            case STEP_STARTED -> validateStepGovernance(event);
            default -> { /* no policy action needed */ }
        }
    }

    private void validateGovernanceContext(WorkflowLifecycleEvent event) {
        Map<String, Object> attrs = event.attributes();

        if (event.tenantId() == null || event.tenantId().isBlank()) {
            log.warn("POLICY workflow_started runId={} workflowId={} — missing tenantId, "
                    + "governance policies cannot be scoped",
                    event.runId(), event.workflowId());
        }

        if (!attrs.containsKey(ATTR_AUTONOMY_LEVEL)) {
            log.info("POLICY workflow_started runId={} workflowId={} — no autonomyLevel "
                    + "in context, default policy will apply",
                    event.runId(), event.workflowId());
        }

        if (!attrs.containsKey(ATTR_MAX_DELEGATION_DEPTH)) {
            log.info("POLICY workflow_started runId={} workflowId={} — no maxDelegationDepth "
                    + "in context, using platform default",
                    event.runId(), event.workflowId());
        }

        log.debug("POLICY governance_context_validated runId={} workflowId={} tenantId={} "
                + "autonomy={} maxDepth={}",
                event.runId(), event.workflowId(), event.tenantId(),
                attrs.getOrDefault(ATTR_AUTONOMY_LEVEL, "unset"),
                attrs.getOrDefault(ATTR_MAX_DELEGATION_DEPTH, "unset"));
    }

    private void validateStepGovernance(WorkflowLifecycleEvent event) {
        // Ensure tenant context is carried through to steps
        if (event.tenantId() == null || event.tenantId().isBlank()) {
            log.warn("POLICY step_started runId={} stepId={} — tenant context lost at step level",
                    event.runId(), event.stepId());
        }
    }
}
