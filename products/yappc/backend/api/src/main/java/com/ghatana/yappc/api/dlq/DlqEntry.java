/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.dlq;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable view of a single DLQ entry row from {@code yappc_dlq}.
 *
 * @param id            UUID primary key
 * @param tenantId      owning tenant
 * @param pipelineId    the pipeline that produced the failure
 * @param nodeId        the operator node that failed
 * @param eventType     the input event type
 * @param eventPayload  event payload snapshot at failure time
 * @param failureReason human-readable reason
 * @param retryCount    number of retry attempts so far
 * @param status        {@code PENDING | RETRYING | RESOLVED | ABANDONED}
 * @param correlationId trace correlation ID (nullable)
 * @param createdAt     row creation timestamp
 * @param updatedAt     last-updated timestamp
 * @param resolvedAt    resolution timestamp (nullable)
 *
 * @doc.type record
 * @doc.purpose Immutable DTO for a YAPPC DLQ entry
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DlqEntry(
        UUID id,
        String tenantId,
        String pipelineId,
        String nodeId,
        String eventType,
        Map<String, Object> eventPayload,
        String failureReason,
        int retryCount,
        String status,
        String correlationId,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
) {}
