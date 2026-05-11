/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import com.ghatana.agent.AgentResult;

import java.util.List;
import java.util.Map;

/**
 * Calculates mastery indicators from execution results and learned artifacts.
 */
public final class MasteryMetricsCalculator {

    public MasteryMetrics calculate(List<AgentResult<?>> results, List<LearnedArtifact> artifacts) {
        if (results == null || results.isEmpty()) {
            long rollbacks = artifacts == null ? 0L : artifacts.stream()
                    .filter(a -> a.state() == PromotionState.ROLLED_BACK)
                    .count();
            return new MasteryMetrics(0, 0, 0, 0, 0, 0, 0, rollbacks);
        }
        long successes = results.stream().filter(AgentResult::isSuccess).count();
        double avgCost = averageMetric(results, "cost");
        double avgLatency = results.stream()
                .filter(r -> r.getProcessingTime() != null)
                .mapToLong(r -> r.getProcessingTime().toMillis())
                .average()
                .orElse(0.0);
        double repeatedErrors = results.stream()
                .filter(r -> r.getDiagnostics().containsKey("repeatedError"))
                .count() / (double) results.size();
        double memoryUtility = results.stream()
                .filter(r -> !r.getMemoryRefs().isEmpty())
                .count() / (double) results.size();
        double skillReuse = artifacts == null || artifacts.isEmpty() ? 0.0 : artifacts.stream()
                .filter(a -> a.target() == LearningTarget.PROCEDURAL_SKILL && a.state() == PromotionState.ACTIVE)
                .count() / (double) artifacts.size();
        double drift = averageMetric(results, "driftScore");
        long rollbacks = artifacts == null ? 0L : artifacts.stream()
                .filter(a -> a.state() == PromotionState.ROLLED_BACK)
                .count();
        return new MasteryMetrics(
                successes / (double) results.size(),
                avgCost,
                avgLatency,
                repeatedErrors,
                memoryUtility,
                skillReuse,
                drift,
                rollbacks);
    }

    private static double averageMetric(List<AgentResult<?>> results, String key) {
        return results.stream()
                .map(AgentResult::getMetrics)
                .map(m -> m.get(key))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToDouble(Number::doubleValue)
                .average()
                .orElse(0.0);
    }
}
