/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Immutable result envelope returned by agent processing.
 *
 * @param <O> output type
 *
 * @doc.type class
 * @doc.purpose Agent result wrapper with confidence and metrics
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record AgentResult<O>(
    O output,
    double confidence,
    AgentResultStatus status,
    String explanation,
    String agentId,
    Map<String, Object> metrics,
    Duration processingTime,
    Instant startedAt,
    String traceId
) {

    /**
     * Creates a successful result.
     */
    public static <O> AgentResult<O> success(O output, double confidence, String agentId) {
        return new AgentResult<>(output, confidence, AgentResultStatus.SUCCESS,
            null, agentId, Map.of(), Duration.ZERO, Instant.now(), null);
    }

    /**
     * Creates a failed result.
     */
    public static <O> AgentResult<O> failure(String explanation, String agentId) {
        return new AgentResult<>(null, 0.0, AgentResultStatus.FAILED,
            explanation, agentId, Map.of(), Duration.ZERO, Instant.now(), null);
    }
}
