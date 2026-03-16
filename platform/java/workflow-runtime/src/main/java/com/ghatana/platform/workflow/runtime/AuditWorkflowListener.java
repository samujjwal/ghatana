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

/**
 * Lifecycle listener that emits structured audit log entries for workflow events.
 *
 * <p>Produces INFO-level log entries for normal transitions and WARN-level for
 * failures and compensations. Log entries include run ID, workflow ID, step ID,
 * tenant ID, and correlation ID for traceability.
 *
 * <p>In production, these log entries are expected to be captured by the
 * centralized logging pipeline (Loki / OpenTelemetry) and made searchable
 * in Grafana.
 *
 * @doc.type class
 * @doc.purpose Structured audit logging for workflow lifecycle events
 * @doc.layer platform
 * @doc.pattern Observer
 */
public final class AuditWorkflowListener implements WorkflowLifecycleListener {
    private static final Logger log = LoggerFactory.getLogger(AuditWorkflowListener.class);

    @Override
    public void onEvent(@NotNull WorkflowLifecycleEvent event) {
        switch (event.phase()) {
            case WORKFLOW_STARTED -> log.info(
                "AUDIT workflow_started runId={} workflowId={} tenantId={} correlationId={}",
                event.runId(), event.workflowId(), event.tenantId(), event.correlationId());

            case WORKFLOW_COMPLETED -> log.info(
                "AUDIT workflow_completed runId={} workflowId={} tenantId={} correlationId={}",
                event.runId(), event.workflowId(), event.tenantId(), event.correlationId());

            case WORKFLOW_FAILED -> log.warn(
                "AUDIT workflow_failed runId={} workflowId={} tenantId={} error={}",
                event.runId(), event.workflowId(), event.tenantId(), event.errorMessage());

            case WORKFLOW_COMPENSATING -> log.warn(
                "AUDIT workflow_compensating runId={} workflowId={} tenantId={}",
                event.runId(), event.workflowId(), event.tenantId());

            case WORKFLOW_COMPENSATED -> log.warn(
                "AUDIT workflow_compensated runId={} workflowId={} tenantId={}",
                event.runId(), event.workflowId(), event.tenantId());

            case STEP_STARTED -> log.info(
                "AUDIT step_started runId={} workflowId={} stepId={} tenantId={}",
                event.runId(), event.workflowId(), event.stepId(), event.tenantId());

            case STEP_COMPLETED -> log.info(
                "AUDIT step_completed runId={} workflowId={} stepId={} tenantId={}",
                event.runId(), event.workflowId(), event.stepId(), event.tenantId());

            case STEP_FAILED -> log.warn(
                "AUDIT step_failed runId={} workflowId={} stepId={} error={}",
                event.runId(), event.workflowId(), event.stepId(), event.errorMessage());

            case STEP_RETRYING -> log.info(
                "AUDIT step_retrying runId={} workflowId={} stepId={}",
                event.runId(), event.workflowId(), event.stepId());

            default -> log.debug(
                "AUDIT {} runId={} workflowId={} stepId={}",
                event.phase(), event.runId(), event.workflowId(), event.stepId());
        }
    }
}
