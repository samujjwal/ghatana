/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Platform execution trace.
 *
 * @doc.type record
 * @doc.purpose Represents an execution trace from platform services
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record PlatformTrace(
    String traceId,
    String executionId,
    List<TraceSpan> spans,
    Map<String, String> metadata,
    Instant startedAt,
    Instant completedAt
) {
    public record TraceSpan(
        String spanId,
        String parentSpanId,
        String operationName,
        Instant startedAt,
        Instant completedAt,
        Map<String, Object> tags,
        Map<String, Object> logs
    ) {}
}
