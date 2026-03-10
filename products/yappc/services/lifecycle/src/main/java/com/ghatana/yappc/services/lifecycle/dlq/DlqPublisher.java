/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Services
 */
package com.ghatana.yappc.services.lifecycle.dlq;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * SPI for publishing failed AEP pipeline events to the Dead-Letter Queue.
 *
 * <p>Implementations write DLQ entries to the {@code yappc_dlq} table. The default
 * production implementation is {@code JdbcDlqPublisher} in {@code backend/api}.
 *
 * <p>Wire into {@link com.ghatana.yappc.services.lifecycle.YappcAepPipelineBootstrapper}
 * via {@link com.ghatana.yappc.services.lifecycle.LifecycleServiceModule} {@code @Provides}.
 *
 * @doc.type interface
 * @doc.purpose SPI for publishing failed pipeline events to the Dead-Letter Queue
 * @doc.layer product
 * @doc.pattern SPI
 */
public interface DlqPublisher {

    /**
     * Publishes a failed pipeline event to the DLQ for later inspection and retry.
     *
     * @param tenantId       the tenant that owns the failed event
     * @param pipelineId     the pipeline ID (e.g., "lifecycle-management-v1")
     * @param nodeId         the operator node ID that failed
     * @param eventType      the event type that caused the failure
     * @param eventPayload   the full event payload at the time of failure
     * @param failureReason  human-readable failure description (e.g., "INVALID_TRANSITION")
     * @param correlationId  tracing correlation ID (nullable)
     * @return Promise completing when the DLQ entry has been persisted
     */
    Promise<Void> publish(
            String tenantId,
            String pipelineId,
            String nodeId,
            String eventType,
            Map<String, Object> eventPayload,
            String failureReason,
            String correlationId);

    /**
     * No-operation implementation for use in tests or when DLQ is not configured.
     *
     * @return a {@link DlqPublisher} that logs and discards
     */
    static DlqPublisher noop() {
        return (tenantId, pipelineId, nodeId, eventType, eventPayload, failureReason, correlationId) -> {
            org.slf4j.LoggerFactory.getLogger(DlqPublisher.class)
                    .warn("[DLQ-NOOP] Dropped failed event: pipeline={} node={} eventType={} reason={}",
                            pipelineId, nodeId, eventType, failureReason);
            return Promise.complete();
        };
    }
}
