package com.ghatana.yappc.platform.observability;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.inject.annotation.Inject;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Business metrics for YAPPC agent execution.
 *
 * <p>Records KPIs for every agent dispatch: start/completion counts, error
 * rates, and latency distributions — all tagged by {@code agent_id},
 * {@code tenant}, and {@code status}. These feed directly into the Grafana
 * YAPPC dashboard and the Alertmanager rule set.
 *
 * <p>Metric naming follows OBS-001 conventions:
 * {@code yappc.<domain>.<operation>.<outcome>}, lowercase, dot-separated.
 *
 * <p>Inject this class wherever agent dispatches are initiated (e.g., the
 * YAPPC HTTP handler layer or the {@code AgentRuntimePort} adapter).
 *
 * @doc.type class
 * @doc.purpose Publish business-level agent-execution counters, timers, and
 *              error rates to the YAPPC observability stack.
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class AgentExecutionMetrics {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionMetrics.class);

    private static final String PREFIX = "yappc.agent.execution";

    // Counter metric names
    static final String METRIC_DISPATCHED   = PREFIX + ".dispatched";
    static final String METRIC_SUCCEEDED    = PREFIX + ".succeeded";
    static final String METRIC_FAILED       = PREFIX + ".failed";
    static final String METRIC_DURATION     = PREFIX + ".duration";
    static final String METRIC_REGISTERED   = "yappc.agent.registry.registered";
    static final String METRIC_UNREGISTERED = "yappc.agent.registry.unregistered";

    private final MetricsCollector metricsCollector;
    private final MeterRegistry meterRegistry;

    /**
     * @param metricsCollector platform-standard metrics abstraction
     * @param meterRegistry    Micrometer registry (for Timer operations)
     */
    @Inject
    public AgentExecutionMetrics(MetricsCollector metricsCollector, MeterRegistry meterRegistry) {
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector");
        this.meterRegistry    = Objects.requireNonNull(meterRegistry,    "meterRegistry");
        log.info("AgentExecutionMetrics initialized");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dispatch lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call immediately before dispatching an agent.
     *
     * @param agentId  canonical agent identifier (e.g., "expert.java")
     * @param tenantId tenant that issued the dispatch
     * @return an opaque {@link Timer.Sample} to be passed to
     *         {@link #recordDispatchSuccess} or {@link #recordDispatchFailure}
     */
    public Timer.Sample startDispatch(String agentId, String tenantId) {
        metricsCollector.incrementCounter(
                METRIC_DISPATCHED,
                "agent_id", agentId,
                "tenant",   tenantId);
        return Timer.start(meterRegistry);
    }

    /**
     * Call after a successful agent dispatch.
     *
     * @param sample   the sample returned by {@link #startDispatch}
     * @param agentId  canonical agent identifier
     * @param tenantId tenant that issued the dispatch
     */
    public void recordDispatchSuccess(Timer.Sample sample, String agentId, String tenantId) {
        metricsCollector.incrementCounter(
                METRIC_SUCCEEDED,
                "agent_id", agentId,
                "tenant",   tenantId);
        stopTimer(sample, agentId, tenantId, "success");
    }

    /**
     * Call after a failed agent dispatch.
     *
     * @param sample    the sample returned by {@link #startDispatch}
     * @param agentId   canonical agent identifier
     * @param tenantId  tenant that issued the dispatch
     * @param errorType short classification of the error (e.g., "timeout",
     *                  "validation", "llm_error")
     * @param cause     the exception (may be {@code null} for non-exception failures)
     */
    public void recordDispatchFailure(
            Timer.Sample sample,
            String agentId,
            String tenantId,
            String errorType,
            Exception cause) {
        metricsCollector.incrementCounter(
                METRIC_FAILED,
                "agent_id",  agentId,
                "tenant",    tenantId,
                "error_type", errorType);
        if (cause != null) {
            metricsCollector.recordError(METRIC_FAILED, cause,
                    java.util.Map.of("agent_id", agentId, "tenant", tenantId));
        }
        stopTimer(sample, agentId, tenantId, "failure");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Registry lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Increment registry registration counter.
     *
     * @param agentId  the newly registered agent
     * @param tenantId registering tenant
     */
    public void recordAgentRegistered(String agentId, String tenantId) {
        metricsCollector.incrementCounter(
                METRIC_REGISTERED,
                "agent_id", agentId,
                "tenant",   tenantId);
    }

    /**
     * Increment registry un-registration counter.
     *
     * @param agentId  the removed agent
     * @param tenantId owning tenant
     */
    public void recordAgentUnregistered(String agentId, String tenantId) {
        metricsCollector.incrementCounter(
                METRIC_UNREGISTERED,
                "agent_id", agentId,
                "tenant",   tenantId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────────

    private void stopTimer(Timer.Sample sample, String agentId, String tenantId, String status) {
        if (sample == null) {
            log.warn("stopTimer called with null sample for agent={} tenant={}", agentId, tenantId);
            return;
        }
        try {
            sample.stop(Timer.builder(METRIC_DURATION)
                    .tags(Tags.of(
                            "agent_id", agentId,
                            "tenant",   tenantId,
                            "status",   status))
                    .register(meterRegistry));
        } catch (Exception e) {
            log.warn("Failed to record agent execution duration for agent={}", agentId, e);
        }
    }
}
