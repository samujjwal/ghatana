/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.lifecycle;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable trace entry for one lifecycle phase.
 */
public record AgentPhaseTrace(
        String phaseTraceId,
        AgentLifecyclePhase phase,
        Instant startedAt,
        Instant endedAt,
        String status,
        String error,
        Map<String, Object> metrics
) {
    public AgentPhaseTrace {
        Objects.requireNonNull(phaseTraceId, "phaseTraceId must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(endedAt, "endedAt must not be null");
        status = status == null || status.isBlank() ? "SUCCESS" : status;
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }

    public Duration duration() {
        return Duration.between(startedAt, endedAt);
    }
}
