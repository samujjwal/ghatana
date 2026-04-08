/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Versioned evaluation benchmark suite attached to an agent release.
 *
 * <p>An {@code EvaluationPack} records the exact set of benchmark suites and
 * regression gates that must pass before a release can be promoted beyond
 * {@code DRAFT}. It also captures promotion thresholds for key metrics.
 *
 * @param evaluationPackId    unique identifier for this evaluation pack
 * @param version             semantic version string
 * @param benchmarkSuiteIds   IDs of benchmark suites to run
 * @param regressionGateIds   IDs of regression gate checks required
 * @param promotionThresholds map of metric name → minimum threshold (e.g., {@code {"accuracy": 0.95}})
 * @param digest              SHA-256 of this pack's canonical representation
 * @param createdAt           when this evaluation pack was created
 *
 * @doc.type record
 * @doc.purpose Versioned evaluation benchmark suite attached to an agent release
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record EvaluationPack(
        String evaluationPackId,
        String version,
        List<String> benchmarkSuiteIds,
        List<String> regressionGateIds,
        Map<String, Double> promotionThresholds,
        String digest,
        Instant createdAt
) {
    public EvaluationPack {
        benchmarkSuiteIds    = List.copyOf(benchmarkSuiteIds);
        regressionGateIds    = List.copyOf(regressionGateIds);
        promotionThresholds  = Map.copyOf(promotionThresholds);
    }
}
