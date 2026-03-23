package com.ghatana.eventprocessing.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Observability instrumentation for event publishing operations.
 *
 * <p>
 * Tracks metrics for pattern and pipeline event publishing to EventCloud.
 *
 * @doc.type class
 * @doc.purpose Observability for event publisher operations
 * @doc.layer product
 * @doc.pattern Observability
 */
@Slf4j
@RequiredArgsConstructor
public class EventPublisherObservability {

    public void recordPatternPublishStart(String tenantId, String operation, String patternId) {
        // No-op for now - metrics handled by MetricsCollector
    }

    public void recordPatternPublishSuccess(String patternId, long durationMs) {
        // No-op for now - metrics handled by MetricsCollector
    }

    public void recordPatternPublishError(String patternId, Exception e) {
        log.error("Pattern publish error: {}", patternId, e);
    }

    public void recordPipelinePublishStart(String tenantId, String operation, String pipelineId) {
        // No-op for now - metrics handled by MetricsCollector
    }

    public void recordPipelinePublishSuccess(String pipelineId, long durationMs) {
        // No-op for now - metrics handled by MetricsCollector
    }

    public void recordPipelinePublishError(String pipelineId, Exception e) {
        log.error("Pipeline publish error: {}", pipelineId, e);
    }
}
