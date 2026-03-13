/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.operators;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.types.identity.OperatorId;
import com.ghatana.platform.workflow.operator.AbstractOperator;
import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.platform.workflow.operator.OperatorType;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Terminal operator in the {@code agent-orchestration-v1} pipeline that collects
 * execution metrics from agent result events and emits {@code agent.metrics.updated}
 * events for observability consumers.
 *
 * <p><b>Pipeline Position</b><br>
 * Fifth and final operator in the {@code agent-orchestration-v1} AEP pipeline.
 * Acts as a pass-through for all event types — it is primarily a side-effect operator
 * that publishes metrics without transforming the event stream.
 *
 * <p><b>Metrics Emitted</b>
 * <ul>
 *   <li>{@code agent_executions_total} — running counter of agent executions</li>
 *   <li>{@code agent_executions_by_status} — last observed status for the agent</li>
 *   <li>{@code agent_id} — which agent the metric applies to</li>
 *   <li>{@code tenant_id} — multi-tenancy tag</li>
 * </ul>
 *
 * <p>All metric events are published to the {@code agent.metrics.updated} topic
 * so that the Data-Cloud analytics pipeline can aggregate and visualise them.
 *
 * @doc.type class
 * @doc.purpose Collects and publishes agent execution metrics for observability
 * @doc.layer product
 * @doc.pattern Metrics, Observer
 * @doc.gaa.lifecycle capture
 */
public class MetricsCollectorOperator extends AbstractOperator {

    private static final Logger log = LoggerFactory.getLogger(MetricsCollectorOperator.class);

    /** Event type consumed by this operator (pass-through). */
    public static final String EVENT_RESULT_PRODUCED  = AgentExecutorOperator.EVENT_RESULT_PRODUCED;
    /** Event type emitted with aggregated metrics snapshot. */
    public static final String EVENT_METRICS_UPDATED  = "agent.metrics.updated";

    /** Running count of all agent executions seen by this operator. */
    private final AtomicLong totalExecutions = new AtomicLong(0);

    private final MeterRegistry meterRegistry;

    /**
     * Creates a {@code MetricsCollectorOperator} with Micrometer metrics registration.
     *
     * @param meterRegistry Micrometer registry to publish metrics to
     */
    public MetricsCollectorOperator(MeterRegistry meterRegistry) {
        super(
            OperatorId.of("yappc", "stream", "metrics-collector", "1.0.0"),
            OperatorType.STREAM,
            "Metrics Collector",
            "Collects agent execution metrics and emits agent.metrics.updated events",
            List.of("agent.metrics", "observability"),
            null
        );
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        // Register gauge for the running execution counter
        io.micrometer.core.instrument.Gauge.builder("agent_executions_total", totalExecutions, AtomicLong::get)
                .description("Running total of agent executions observed by this lifecycle pipeline operator")
                .register(meterRegistry);
    }

    /**
     * Creates a {@code MetricsCollectorOperator} with a no-op registry (test / standalone use).
     */
    public MetricsCollectorOperator() {
        this(new SimpleMeterRegistry());
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "event must not be null");

        String agentId   = payloadStr(event, "agentId");
        String status    = payloadStr(event, "status");
        String tenantId  = payloadStr(event, "tenantId");

        long execCount = totalExecutions.incrementAndGet();

        log.debug("Collecting metrics: agentId={} status={} totalExecutions={}",
                agentId, status, execCount);

        // Record Micrometer counter for agent dispatch failures
        if ("error".equalsIgnoreCase(status)) {
            String reason = payloadStr(event, "_error");
            Counter.builder("agent_dispatch_failures_total")
                    .description("Total agent dispatch failures observed by the lifecycle pipeline")
                    .tags(
                        "tenant",  Objects.toString(tenantId, "unknown"),
                        "agentId", Objects.toString(agentId,  "unknown"),
                        "reason",  Objects.toString(reason,   "unknown"))
                    .register(meterRegistry)
                    .increment();
        }

        Event metricsEvent = GEvent.builder()
                .typeTenantVersion(tenantId != null ? tenantId : "", EVENT_METRICS_UPDATED, "v1")
                .addPayload("agentId",                Objects.toString(agentId, "unknown"))
                .addPayload("tenantId",               Objects.toString(tenantId, ""))
                .addPayload("status",                 Objects.toString(status, "unknown"))
                .addPayload("agent_executions_total", execCount)
                .addPayload("collectedAt",            Instant.now().toString())
                .build();

        return Promise.of(OperatorResult.of(metricsEvent));
    }

    /**
     * Total agent executions observed since this operator started.
     *
     * @return running execution count
     */
    public long getTotalExecutions() {
        return totalExecutions.get();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String payloadStr(Event event, String key) {
        Object v = event.getPayload(key);
        return v != null ? v.toString() : null;
    }

    @Override
    public Event toEvent() {
        return GEvent.builder()
                .type("operator.registered")
                .addPayload("operatorId",        getId().toString())
                .addPayload("operatorName",      getName())
                .addPayload("operatorType",      getType().name())
                .addPayload("version",           getVersion())
                .addPayload("totalExecutions",   totalExecutions.get())
                .build();
    }
}
