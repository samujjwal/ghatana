/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.workflow;

import com.ghatana.platform.observability.MetricsCollector;

import java.util.Map;
import java.util.Objects;

/**
 * Observability facade for workflow agent execution metrics.
 *
 * <p>Provides domain-specific metrics methods for workflow agent operations across
 * all agent roles (CODE_REVIEWER, PLANNER, VALIDATOR, etc.). All workflow agent metrics
 * follow the {@code workflow_agent.*} namespace convention.</p>
 *
 * <h2>Metric Categories</h2>
 * <ul>
 *   <li><b>Execution metrics:</b> workflow_agent.executions, workflow_agent.execution.duration</li>
 *   <li><b>Completion metrics:</b> workflow_agent.completed, workflow_agent.failed, workflow_agent.cancelled</li>
 *   <li><b>Batch metrics:</b> workflow_agent.batch.executions, workflow_agent.batch.size</li>
 *   <li><b>Health metrics:</b> workflow_agent.health.checks, workflow_agent.health.status</li>
 * </ul>
 *
 * <h2>Standard Tags</h2>
 * <ul>
 *   <li>{@code role} — CODE_REVIEWER, PLANNER, VALIDATOR, etc.</li>
 *   <li>{@code agent_id} — Agent instance identifier</li>
 *   <li>{@code tenant_id} — Tenant for multi-tenant isolation</li>
 *   <li>{@code status} — success, failure, cancelled</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Observability facade for workflow agent metrics
 * @doc.layer core
 * @doc.pattern Facade
 */
public class WorkflowAgentMetrics {

    // ════════════════════════════════════════════════════════════════
    // Metric name constants
    // ════════════════════════════════════════════════════════════════

    public static final String EXECUTIONS = "workflow_agent.executions";
    public static final String EXECUTION_DURATION_MS = "workflow_agent.execution.duration";
    public static final String COMPLETED = "workflow_agent.completed";
    public static final String FAILED = "workflow_agent.failed";
    public static final String CANCELLED = "workflow_agent.cancelled";
    public static final String BATCH_EXECUTIONS = "workflow_agent.batch.executions";
    public static final String BATCH_SIZE = "workflow_agent.batch.size";
    public static final String HEALTH_CHECKS = "workflow_agent.health.checks";
    public static final String HEALTH_STATUS = "workflow_agent.health.status";
    public static final String QUEUE_DEPTH = "workflow_agent.queue.depth";
    public static final String ACTIVE_EXECUTIONS = "workflow_agent.active.executions";

    // ════════════════════════════════════════════════════════════════
    // Tag key constants
    // ════════════════════════════════════════════════════════════════

    public static final String TAG_ROLE = "role";
    public static final String TAG_AGENT_ID = "agent_id";
    public static final String TAG_TENANT_ID = "tenant_id";
    public static final String TAG_STATUS = "status";
    public static final String TAG_REQUEST_ID = "request_id";
    public static final String TAG_ERROR_TYPE = "error_type";

    // ════════════════════════════════════════════════════════════════
    // Fields
    // ════════════════════════════════════════════════════════════════

    private final MetricsCollector collector;

    /**
     * Creates a WorkflowAgentMetrics facade wrapping the given MetricsCollector.
     *
     * @param collector the underlying metrics collector (must not be null)
     */
    public WorkflowAgentMetrics(MetricsCollector collector) {
        this.collector = Objects.requireNonNull(collector, "MetricsCollector must not be null");
    }

    // ════════════════════════════════════════════════════════════════
    // Execution metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records a workflow agent execution event.
     *
     * @param role the agent role (e.g., CODE_REVIEWER)
     * @param agentId the agent ID
     * @param tenantId the tenant ID
     * @param requestId the request ID
     * @param durationMs execution duration in milliseconds
     * @param success whether execution succeeded
     */
    public void recordExecution(String role, String agentId, String tenantId,
                                String requestId, long durationMs, boolean success) {
        String status = success ? "success" : "failure";
        collector.incrementCounter(EXECUTIONS,
                TAG_ROLE, role,
                TAG_AGENT_ID, agentId,
                TAG_TENANT_ID, tenantId,
                TAG_REQUEST_ID, requestId,
                TAG_STATUS, status);
        collector.recordTimer(EXECUTION_DURATION_MS, durationMs,
                TAG_ROLE, role,
                TAG_AGENT_ID, agentId,
                TAG_TENANT_ID, tenantId);
        if (!success) {
            collector.incrementCounter(FAILED,
                    TAG_ROLE, role,
                    TAG_AGENT_ID, agentId,
                    TAG_TENANT_ID, tenantId);
        }
    }

    /**
     * Records a successful workflow agent completion.
     *
     * @param role the agent role
     * @param agentId the agent ID
     * @param tenantId the tenant ID
     * @param durationMs execution duration in milliseconds
     */
    public void recordCompleted(String role, String agentId, String tenantId, long durationMs) {
        collector.incrementCounter(COMPLETED,
                TAG_ROLE, role,
                TAG_AGENT_ID, agentId,
                TAG_TENANT_ID, tenantId,
                TAG_STATUS, "success");
        collector.recordTimer(EXECUTION_DURATION_MS, durationMs,
                TAG_ROLE, role,
                TAG_AGENT_ID, agentId,
                TAG_TENANT_ID, tenantId);
    }

    /**
     * Records a failed workflow agent execution.
     *
     * @param role the agent role
     * @param agentId the agent ID
     * @param tenantId the tenant ID
     * @param error the exception that caused failure
     */
    public void recordFailed(String role, String agentId, String tenantId, Exception error) {
        collector.incrementCounter(FAILED,
                TAG_ROLE, role,
                TAG_AGENT_ID, agentId,
                TAG_TENANT_ID, tenantId,
                TAG_STATUS, "failure",
                TAG_ERROR_TYPE, error.getClass().getSimpleName());
        collector.recordError(FAILED, error,
                Map.of(TAG_ROLE, role, TAG_AGENT_ID, agentId, TAG_TENANT_ID, tenantId));
    }

    /**
     * Records a cancelled workflow agent execution.
     *
     * @param role the agent role
     * @param agentId the agent ID
     * @param tenantId the tenant ID
     */
    public void recordCancelled(String role, String agentId, String tenantId) {
        collector.incrementCounter(CANCELLED,
                TAG_ROLE, role,
                TAG_AGENT_ID, agentId,
                TAG_TENANT_ID, tenantId,
                TAG_STATUS, "cancelled");
    }

    // ════════════════════════════════════════════════════════════════
    // Batch metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records a batch execution event.
     *
     * @param role the agent role
     * @param tenantId the tenant ID
     * @param batchSize the number of requests in the batch
     */
    public void recordBatchExecution(String role, String tenantId, int batchSize) {
        collector.incrementCounter(BATCH_EXECUTIONS,
                TAG_ROLE, role,
                TAG_TENANT_ID, tenantId);
        collector.recordGauge(BATCH_SIZE, batchSize);
    }

    // ════════════════════════════════════════════════════════════════
    // Health metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records a health check event.
     *
     * @param agentId the agent ID
     * @param role the agent role
     * @param healthy whether the agent is healthy
     */
    public void recordHealthCheck(String agentId, String role, boolean healthy) {
        collector.incrementCounter(HEALTH_CHECKS,
                TAG_AGENT_ID, agentId,
                TAG_ROLE, role,
                TAG_STATUS, healthy ? "healthy" : "unhealthy");
        collector.recordGauge(HEALTH_STATUS, healthy ? 1.0 : 0.0);
    }

    // ════════════════════════════════════════════════════════════════
    // Queue metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records the current queue depth.
     *
     * @param tenantId the tenant ID
     * @param depth the current queue depth
     */
    public void recordQueueDepth(String tenantId, int depth) {
        collector.recordGauge(QUEUE_DEPTH, depth);
    }

    /**
     * Records the number of active executions.
     *
     * @param tenantId the tenant ID
     * @param count the number of active executions
     */
    public void recordActiveExecutions(String tenantId, int count) {
        collector.recordGauge(ACTIVE_EXECUTIONS, count);
    }

    /**
     * Returns the underlying MetricsCollector.
     *
     * @return the metrics collector
     */
    public MetricsCollector getCollector() {
        return collector;
    }
}
