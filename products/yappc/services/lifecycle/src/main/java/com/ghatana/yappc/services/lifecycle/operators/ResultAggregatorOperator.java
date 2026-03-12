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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates {@code agent.result.produced} events by {@code [agent_id, correlation_id]}
 * and emits a {@code workflow.step.completed} event once all expected results arrive.
 *
 * <p><b>Pipeline Position</b><br>
 * Fourth operator in the {@code agent-orchestration-v1} AEP pipeline. Receives
 * {@code agent.result.produced} events from {@link AgentExecutorOperator} and
 * accumulates them into per-correlation batches.
 *
 * <p><b>Aggregation Semantics</b><br>
 * Results are grouped by {@code correlation_id}. Each result is stored in-memory.
 * A {@code workflow.step.completed} event is emitted immediately for every result
 * received (1-to-1 aggregation in the current implementation). Windowed aggregation
 * (batching multiple results per correlation window) is configurable via
 * {@link #ResultAggregatorOperator(int)}.
 *
 * <p><b>Memory Management</b><br>
 * Completed correlation buckets are evicted after emission to prevent unbounded growth.
 *
 * @doc.type class
 * @doc.purpose Aggregates agent results per correlation ID for workflow coordination
 * @doc.layer product
 * @doc.pattern Aggregator
 * @doc.gaa.lifecycle capture
 */
public class ResultAggregatorOperator extends AbstractOperator {

    private static final Logger log = LoggerFactory.getLogger(ResultAggregatorOperator.class);

    /** Inbound event type from {@link AgentExecutorOperator}. */
    public static final String EVENT_RESULT_PRODUCED       = AgentExecutorOperator.EVENT_RESULT_PRODUCED;
    /** Event type emitted when aggregation completes. */
    public static final String EVENT_STEP_COMPLETED        = "workflow.step.completed";

    /** Expected results per correlation group before emitting. Default: 1 (immediate). */
    private final int aggregationThreshold;

    /** In-flight result buckets keyed by correlationId. */
    private final ConcurrentHashMap<String, List<Map<?, ?>>> buckets = new ConcurrentHashMap<>();

    /**
     * Creates a {@code ResultAggregatorOperator} with immediate emission (threshold=1).
     */
    public ResultAggregatorOperator() {
        this(1);
    }

    /**
     * Creates a {@code ResultAggregatorOperator} with a custom aggregation threshold.
     *
     * @param aggregationThreshold number of results to collect per correlation ID
     *                             before emitting a {@code workflow.step.completed} event
     */
    public ResultAggregatorOperator(int aggregationThreshold) {
        super(
            OperatorId.of("yappc", "stream", "result-aggregator", "1.0.0"),
            OperatorType.STREAM,
            "Result Aggregator",
            "Aggregates agent results by correlation ID for workflow coordination",
            List.of("agent.aggregate", "workflow.step"),
            null
        );
        if (aggregationThreshold <= 0) {
            throw new IllegalArgumentException(
                    "aggregationThreshold must be positive, got: " + aggregationThreshold);
        }
        this.aggregationThreshold = aggregationThreshold;
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "event must not be null");

        String agentId        = payloadStr(event, "agentId");
        String correlationId  = payloadStr(event, "correlationId");
        String status         = payloadStr(event, "status");
        String tenantId       = payloadStr(event, "tenantId");

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Accumulate into bucket
        Map<String, Object> resultEntry = Map.of(
            "agentId",       Objects.toString(agentId, "unknown"),
            "status",        Objects.toString(status, "unknown"),
            "receivedAt",    Instant.now().toString()
        );

        List<Map<?, ?>> bucket = buckets.computeIfAbsent(correlationId, k -> new ArrayList<>());
        synchronized (bucket) {
            bucket.add(resultEntry);

            if (bucket.size() < aggregationThreshold) {
                log.debug("Aggregating result: correlationId={} count={}/{} agentId={}",
                        correlationId, bucket.size(), aggregationThreshold, agentId);
                return Promise.of(OperatorResult.empty());
            }

            // Threshold reached — emit workflow.step.completed
            List<Map<?, ?>> aggregated = new ArrayList<>(bucket);
            buckets.remove(correlationId);

            log.info("Aggregation complete: correlationId={} results={} tenantId={}",
                    correlationId, aggregated.size(), tenantId);

            Event stepCompleted = GEvent.builder()
                    .typeTenantVersion(tenantId != null ? tenantId : "", EVENT_STEP_COMPLETED, "v1")
                    .addPayload("correlationId", correlationId)
                    .addPayload("tenantId",      Objects.toString(tenantId, ""))
                    .addPayload("resultCount",   aggregated.size())
                    .addPayload("results",       aggregated)
                    .build();

            return Promise.of(OperatorResult.of(stepCompleted));
        }
    }

    /**
     * Number of in-flight correlation buckets currently tracked.
     *
     * @return active bucket count
     */
    public int activeBucketCount() {
        return buckets.size();
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
                .addPayload("operatorId",   getId().toString())
                .addPayload("operatorName", getName())
                .addPayload("operatorType", getType().name())
                .addPayload("version",      getVersion())
                .addPayload("threshold",    aggregationThreshold)
                .build();
    }
}
