/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

/**
 * @doc.type record
 * @doc.purpose Aggregate mastery indicators for an agent or release
 * @doc.layer agent-core
 * @doc.pattern Record
 */
/**
 * Aggregate mastery indicators for an agent or release.
 */
public record MasteryMetrics(
        double successRate,
        double averageCost,
        double averageLatencyMs,
        double repeatedErrorRate,
        double memoryUtility,
        double skillReuseRate,
        double driftScore,
        long rollbackCount
) {
    public MasteryMetrics {
        successRate = clamp(successRate);
        repeatedErrorRate = clamp(repeatedErrorRate);
        memoryUtility = clamp(memoryUtility);
        skillReuseRate = clamp(skillReuseRate);
        driftScore = clamp(driftScore);
        if (averageCost < 0.0) averageCost = 0.0;
        if (averageLatencyMs < 0.0) averageLatencyMs = 0.0;
        if (rollbackCount < 0) rollbackCount = 0;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
