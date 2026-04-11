/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.assurance;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Promotion gate that determines whether an agent version can advance
 * to the next deployment stage.
 *
 * <p>Gates enforce a minimum bar for agent quality through evaluation packs,
 * shadow testing, and human review sign-off.
 *
 * @doc.type record
 * @doc.purpose Promotion gate for controlled agent deployment
 * @doc.layer agent-runtime
 * @doc.pattern ValueObject
 */
public record PromotionGate(
        /** Unique gate identifier. */
        @NotNull String gateId,

        /** Target deployment stage (e.g., "staging", "canary", "production"). */
        @NotNull String targetStage,

        /** Required evaluation packs that must pass. */
        @NotNull List<String> requiredEvalPackIds,

        /** Minimum pass rate across all packs (0.0 - 1.0). */
        double minimumPassRate,

        /** Whether human sign-off is required. */
        boolean requiresHumanSignoff,

        /** Required shadow testing duration before promotion. */
        @NotNull java.time.Duration shadowTestDuration,

        /** Maximum allowed regression from previous version. */
        double maxRegressionPercent
) {

    /**
     * Compact constructor with validation.
     */
    public PromotionGate {
        Objects.requireNonNull(gateId, "gateId");
        Objects.requireNonNull(targetStage, "targetStage");
        Objects.requireNonNull(shadowTestDuration, "shadowTestDuration");
        requiredEvalPackIds = requiredEvalPackIds == null
                ? List.of() : List.copyOf(requiredEvalPackIds);
        if (minimumPassRate < 0.0 || minimumPassRate > 1.0) {
            throw new IllegalArgumentException(
                    "minimumPassRate must be between 0.0 and 1.0, got " + minimumPassRate);
        }
    }

    /**
     * Evaluates whether the given results satisfy this promotion gate.
     *
     * @param results evaluation results from running the required packs
     * @return a decision record
     */
    @NotNull
    public PromotionDecision evaluate(@NotNull List<EvaluationResult> results) {
        // Check all required packs are present
        for (String requiredPack : requiredEvalPackIds) {
            boolean found = results.stream()
                    .anyMatch(r -> r.packId().equals(requiredPack));
            if (!found) {
                return new PromotionDecision(gateId, targetStage, false,
                        "Missing required evaluation pack: " + requiredPack,
                        Instant.now());
            }
        }

        // Check pass rates
        for (EvaluationResult result : results) {
            if (result.passRate() < minimumPassRate) {
                return new PromotionDecision(gateId, targetStage, false,
                        String.format("Pack %s pass rate %.2f%% below minimum %.2f%%",
                                result.packId(),
                                result.passRate() * 100,
                                minimumPassRate * 100),
                        Instant.now());
            }
        }

        if (requiresHumanSignoff) {
            return new PromotionDecision(gateId, targetStage, false,
                    "Awaiting human sign-off", Instant.now());
        }

        return new PromotionDecision(gateId, targetStage, true,
                "All checks passed", Instant.now());
    }

    /**
     * Result of a promotion gate evaluation.
     */
    public record PromotionDecision(
            @NotNull String gateId,
            @NotNull String targetStage,
            boolean approved,
            @NotNull String reason,
            @NotNull Instant decidedAt
    ) {
        public PromotionDecision {
            Objects.requireNonNull(gateId, "gateId");
            Objects.requireNonNull(targetStage, "targetStage");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(decidedAt, "decidedAt");
        }
    }
}
