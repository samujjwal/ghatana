package com.ghatana.aep.observability;

import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Canonical AEP operator and event-intelligence metrics facade.
 *
 * @doc.type class
 * @doc.purpose Centralizes AEP metric names and tag conventions for operators, patterns, agents, and EventCloud
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class AepOperatorMetrics {

    public static final String EVENTS_INGESTED_TOTAL = "aep.events.ingested.total";
    public static final String OPERATOR_PROCESSED_TOTAL = "aep.operator.processed.total";
    public static final String OPERATOR_LATENCY = "aep.operator.latency";
    public static final String PATTERN_MATCHES_TOTAL = "aep.pattern.matches.total";
    public static final String PATTERN_CONFIDENCE = "aep.pattern.confidence";
    public static final String PATTERN_SHADOW_FALSE_POSITIVE = "aep.pattern.shadow.false_positive";
    public static final String AGENT_INVOCATIONS_TOTAL = "aep.agent.invocations.total";
    public static final String AGENT_TOOL_CALLS_TOTAL = "aep.agent.tool_calls.total";
    public static final String AGENT_REPLAY_MODE = "aep.agent.replay.mode";
    public static final String EVENTCLOUD_LAG = "aep.eventcloud.lag";
    public static final String EVENTCLOUD_DLQ_TOTAL = "aep.eventcloud.dlq.total";

    public static final String TAG_TENANT_ID = "tenantId";
    public static final String TAG_OPERATOR_ID = "operatorId";
    public static final String TAG_OPERATOR_KIND = "operatorKind";
    public static final String TAG_PATTERN_ID = "patternId";
    public static final String TAG_AGENT_REF = "agentRef";
    public static final String TAG_REPLAY_MODE = "replayMode";
    public static final String TAG_EVENT_TYPE = "eventType";
    public static final String TAG_STATUS = "status";

    private static final Logger log = LoggerFactory.getLogger(AepOperatorMetrics.class);
    private static final AepOperatorMetrics NOOP = new AepOperatorMetrics(null);

    private final MetricsCollector collector;

    private AepOperatorMetrics(MetricsCollector collector) {
        this.collector = collector;
    }

    public static AepOperatorMetrics of(MetricsCollector collector) {
        return new AepOperatorMetrics(Objects.requireNonNull(collector, "collector"));
    }

    public static AepOperatorMetrics noop() {
        return NOOP;
    }

    public void recordEventIngested(String tenantId, String eventType) {
        increment(EVENTS_INGESTED_TOTAL, TAG_TENANT_ID, safe(tenantId), TAG_EVENT_TYPE, safe(eventType));
    }

    public void recordOperatorProcessed(
            String tenantId,
            String operatorId,
            OperatorKind operatorKind,
            boolean success) {
        increment(
            OPERATOR_PROCESSED_TOTAL,
            TAG_TENANT_ID, safe(tenantId),
            TAG_OPERATOR_ID, safe(operatorId),
            TAG_OPERATOR_KIND, safe(operatorKind),
            TAG_STATUS, success ? "success" : "failure");
    }

    public void recordOperatorLatency(
            String tenantId,
            String operatorId,
            OperatorKind operatorKind,
            long latencyMs) {
        timer(
            OPERATOR_LATENCY,
            latencyMs,
            TAG_TENANT_ID, safe(tenantId),
            TAG_OPERATOR_ID, safe(operatorId),
            TAG_OPERATOR_KIND, safe(operatorKind));
    }

    public void recordPatternMatch(String tenantId, String patternId, double confidence) {
        increment(PATTERN_MATCHES_TOTAL, TAG_TENANT_ID, safe(tenantId), TAG_PATTERN_ID, safe(patternId));
        timer(PATTERN_CONFIDENCE, Math.round(confidence * 1000), TAG_TENANT_ID, safe(tenantId), TAG_PATTERN_ID, safe(patternId));
    }

    public void recordShadowFalsePositive(String tenantId, String patternId) {
        increment(PATTERN_SHADOW_FALSE_POSITIVE, TAG_TENANT_ID, safe(tenantId), TAG_PATTERN_ID, safe(patternId));
    }

    public void recordAgentInvocation(String tenantId, String operatorId, String agentRef) {
        increment(
            AGENT_INVOCATIONS_TOTAL,
            TAG_TENANT_ID, safe(tenantId),
            TAG_OPERATOR_ID, safe(operatorId),
            TAG_AGENT_REF, safe(agentRef));
    }

    public void recordAgentToolCall(String tenantId, String operatorId, String agentRef) {
        increment(
            AGENT_TOOL_CALLS_TOTAL,
            TAG_TENANT_ID, safe(tenantId),
            TAG_OPERATOR_ID, safe(operatorId),
            TAG_AGENT_REF, safe(agentRef));
    }

    public void recordAgentReplayMode(String tenantId, String operatorId, String replayMode) {
        increment(
            AGENT_REPLAY_MODE,
            TAG_TENANT_ID, safe(tenantId),
            TAG_OPERATOR_ID, safe(operatorId),
            TAG_REPLAY_MODE, safe(replayMode));
    }

    public void recordEventCloudLag(String tenantId, String eventType, long lagMs) {
        timer(EVENTCLOUD_LAG, lagMs, TAG_TENANT_ID, safe(tenantId), TAG_EVENT_TYPE, safe(eventType));
    }

    public void recordEventCloudDlq(String tenantId, String eventType) {
        increment(EVENTCLOUD_DLQ_TOTAL, TAG_TENANT_ID, safe(tenantId), TAG_EVENT_TYPE, safe(eventType));
    }

    private void increment(String metricName, String... tags) {
        if (collector == null) {
            return;
        }
        try {
            collector.incrementCounter(metricName, tags);
        } catch (Exception e) {
            log.debug("AepOperatorMetrics increment failed silently: {}", e.getMessage());
        }
    }

    private void timer(String metricName, long value, String... tags) {
        if (collector == null) {
            return;
        }
        try {
            collector.recordTimer(metricName, Math.max(0L, value), tags);
        } catch (Exception e) {
            log.debug("AepOperatorMetrics timer failed silently: {}", e.getMessage());
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String safe(OperatorKind value) {
        return value == null ? "unknown" : value.name();
    }
}
