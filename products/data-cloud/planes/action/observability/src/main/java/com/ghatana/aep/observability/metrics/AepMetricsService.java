/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.observability.metrics;

import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Business metrics service for the AEP engine.
 *
 * <p>Exposes fine-grained counters and latency signals for the key AEP flows:
 * pipeline deployments, agent executions, and registry operations. All metric
 * names follow the {@code aep.<domain>.<operation>.<outcome>} convention
 * defined by OBS-001.
 *
 * <p>This class is thread-safe and stateless with respect to metric storage —
 * all values are delegated to the injected {@link MetricsCollector}.
 *
 * @doc.type class
 * @doc.purpose Publish AEP-level business counters and latency metrics to the observability stack
 * @doc.layer observability
 * @doc.pattern Facade
 */
public final class AepMetricsService {

    private static final Logger log = LoggerFactory.getLogger(AepMetricsService.class);

    // ─── Metric name constants ────────────────────────────────────────────────

    /** Total pipeline deployments attempted. */
    public static final String METRIC_PIPELINE_DEPLOYED    = "aep.pipeline.deployed";
    /** Total pipeline deployments that succeeded. */
    public static final String METRIC_PIPELINE_SUCCEEDED   = "aep.pipeline.succeeded";
    /** Total pipeline deployments that failed. */
    public static final String METRIC_PIPELINE_FAILED      = "aep.pipeline.failed";

    /** Total agent execution attempts. */
    public static final String METRIC_AGENT_EXECUTED       = "aep.agent.executed";
    /** Total agent executions that succeeded. */
    public static final String METRIC_AGENT_SUCCEEDED      = "aep.agent.succeeded";
    /** Total agent executions that failed. */
    public static final String METRIC_AGENT_FAILED         = "aep.agent.failed";
    /** Agent execution latency (milliseconds). */
    public static final String METRIC_AGENT_DURATION_MS    = "aep.agent.duration_ms";

    /** Total agents registered. */
    public static final String METRIC_REGISTRY_REGISTERED  = "aep.registry.registered";
    /** Total agents unregistered. */
    public static final String METRIC_REGISTRY_UNREGISTERED = "aep.registry.unregistered";

    // ─── Tag keys ─────────────────────────────────────────────────────────────

    private static final String TAG_TENANT     = "tenant_id";
    private static final String TAG_PIPELINE   = "pipeline_id";
    private static final String TAG_AGENT      = "agent_id";

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final MetricsCollector metricsCollector;

    /**
     * @param metricsCollector platform-standard metrics abstraction; must not be {@code null}
     */
    public AepMetricsService(MetricsCollector metricsCollector) {
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector");
        log.info("AepMetricsService initialized");
    }

    // ─── Pipeline metrics ─────────────────────────────────────────────────────

    /**
     * Records the start of a pipeline deployment attempt.
     *
     * @param pipelineId the pipeline being deployed
     * @param tenantId   the owning tenant
     */
    public void recordPipelineDeploymentStarted(String pipelineId, String tenantId) {
        metricsCollector.incrementCounter(METRIC_PIPELINE_DEPLOYED,
                TAG_PIPELINE, pipelineId, TAG_TENANT, tenantId);
        log.debug("Pipeline deployment started: pipelineId={} tenant={}", pipelineId, tenantId);
    }

    /**
     * Records a successful pipeline deployment.
     *
     * @param pipelineId the pipeline that was deployed
     * @param tenantId   the owning tenant
     */
    public void recordPipelineDeploymentSucceeded(String pipelineId, String tenantId) {
        metricsCollector.incrementCounter(METRIC_PIPELINE_SUCCEEDED,
                TAG_PIPELINE, pipelineId, TAG_TENANT, tenantId);
    }

    /**
     * Records a failed pipeline deployment.
     *
     * @param pipelineId the pipeline whose deployment failed
     * @param tenantId   the owning tenant
     * @param cause      the failure cause
     */
    public void recordPipelineDeploymentFailed(String pipelineId, String tenantId, Exception cause) {
        metricsCollector.incrementCounter(METRIC_PIPELINE_FAILED,
                TAG_PIPELINE, pipelineId, TAG_TENANT, tenantId);
        metricsCollector.recordError(METRIC_PIPELINE_FAILED, cause,
                Map.of(TAG_PIPELINE, pipelineId, TAG_TENANT, tenantId));
        log.warn("Pipeline deployment failed: pipelineId={} tenant={}", pipelineId, tenantId, cause);
    }

    // ─── Agent execution metrics ──────────────────────────────────────────────

    /**
     * Records the start of an agent execution.
     *
     * @param agentId  the agent being executed
     * @param tenantId the owning tenant
     */
    public void recordAgentExecutionStarted(String agentId, String tenantId) {
        metricsCollector.incrementCounter(METRIC_AGENT_EXECUTED,
                TAG_AGENT, agentId, TAG_TENANT, tenantId);
    }

    /**
     * Records a successful agent execution and its latency.
     *
     * @param agentId    the agent that completed
     * @param tenantId   the owning tenant
     * @param durationMs execution latency in milliseconds
     */
    public void recordAgentExecutionSucceeded(String agentId, String tenantId, long durationMs) {
        metricsCollector.incrementCounter(METRIC_AGENT_SUCCEEDED,
                TAG_AGENT, agentId, TAG_TENANT, tenantId);
        metricsCollector.increment(METRIC_AGENT_DURATION_MS, durationMs,
                Map.of(TAG_AGENT, agentId, TAG_TENANT, tenantId));
    }

    /**
     * Records a failed agent execution.
     *
     * @param agentId  the agent that failed
     * @param tenantId the owning tenant
     * @param cause    the failure cause
     */
    public void recordAgentExecutionFailed(String agentId, String tenantId, Exception cause) {
        metricsCollector.incrementCounter(METRIC_AGENT_FAILED,
                TAG_AGENT, agentId, TAG_TENANT, tenantId);
        metricsCollector.recordError(METRIC_AGENT_FAILED, cause,
                Map.of(TAG_AGENT, agentId, TAG_TENANT, tenantId));
        log.warn("Agent execution failed: agentId={} tenant={}", agentId, tenantId, cause);
    }

    // ─── Registry metrics ─────────────────────────────────────────────────────

    /**
     * Records an agent registration event.
     *
     * @param agentId  the registered agent
     * @param tenantId the owning tenant
     */
    public void recordAgentRegistered(String agentId, String tenantId) {
        metricsCollector.incrementCounter(METRIC_REGISTRY_REGISTERED,
                TAG_AGENT, agentId, TAG_TENANT, tenantId);
    }

    /**
     * Records an agent unregistration event.
     *
     * @param agentId  the unregistered agent
     * @param tenantId the owning tenant
     */
    public void recordAgentUnregistered(String agentId, String tenantId) {
        metricsCollector.incrementCounter(METRIC_REGISTRY_UNREGISTERED,
                TAG_AGENT, agentId, TAG_TENANT, tenantId);
    }
}
