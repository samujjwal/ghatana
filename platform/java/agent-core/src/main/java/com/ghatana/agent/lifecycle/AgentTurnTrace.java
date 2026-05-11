/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.lifecycle;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Trace envelope for one governed agent turn
 * @doc.layer agent-core
 * @doc.pattern Record
 */
/**
 * Trace envelope for one governed agent turn.
 */
public record AgentTurnTrace(
        String traceId,
        String turnId,
        String agentId,
        Instant startedAt,
        Instant endedAt,
        String status,
        List<AgentPhaseTrace> phases,
        Map<String, Object> metrics
) {
    public AgentTurnTrace {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(turnId, "turnId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(endedAt, "endedAt must not be null");
        status = status == null || status.isBlank() ? "SUCCESS" : status;
        phases = phases == null ? List.of() : List.copyOf(phases);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }
}
