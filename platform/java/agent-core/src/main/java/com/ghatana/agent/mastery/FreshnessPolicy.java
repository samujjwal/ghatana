/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Policy defining freshness requirements for mastery items.
 *
 * <p>Freshness policies determine when mastery items become stale based on
 * time since last verification, evidence strength, and other factors.
 *
 * @doc.type record
 * @doc.purpose Policy defining freshness requirements for mastery items
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record FreshnessPolicy(
        @NotNull String policyId,
        @NotNull Duration defaultStaleAfter,
        @NotNull Duration maxStaleAfter,
        double minEvidenceStrength,
        boolean requireRecentVerification
) {
    public FreshnessPolicy {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(defaultStaleAfter, "defaultStaleAfter must not be null");
        Objects.requireNonNull(maxStaleAfter, "maxStaleAfter must not be null");
        if (defaultStaleAfter.isNegative() || defaultStaleAfter.isZero()) {
            throw new IllegalArgumentException("defaultStaleAfter must be positive");
        }
        if (maxStaleAfter.isNegative() || maxStaleAfter.isZero()) {
            throw new IllegalArgumentException("maxStaleAfter must be positive");
        }
        if (minEvidenceStrength < 0.0 || minEvidenceStrength > 1.0) {
            throw new IllegalArgumentException("minEvidenceStrength must be in [0.0, 1.0]");
        }
        if (defaultStaleAfter.compareTo(maxStaleAfter) > 0) {
            throw new IllegalArgumentException("defaultStaleAfter must not exceed maxStaleAfter");
        }
    }

    /**
     * Calculates the stale-after timestamp for a mastery item based on this policy.
     *
     * @param lastVerifiedAt the last verification timestamp
     * @param evidenceStrength the current evidence strength (0.0 to 1.0)
     * @return the timestamp after which the item is considered stale
     */
    @NotNull
    public Instant calculateStaleAfter(@NotNull Instant lastVerifiedAt, double evidenceStrength) {
        Objects.requireNonNull(lastVerifiedAt, "lastVerifiedAt must not be null");
        
        // Use maxStaleAfter for high-evidence items, defaultStaleAfter for others
        Duration staleAfter = (evidenceStrength >= minEvidenceStrength) 
                ? maxStaleAfter 
                : defaultStaleAfter;
        
        return lastVerifiedAt.plus(staleAfter);
    }

    /**
     * Checks if a mastery item is fresh according to this policy.
     *
     * @param lastVerifiedAt the last verification timestamp
     * @param evidenceStrength the current evidence strength (0.0 to 1.0)
     * @param now the current time
     * @return true if the item is still fresh
     */
    public boolean isFresh(
            @NotNull Instant lastVerifiedAt,
            double evidenceStrength,
            @NotNull Instant now) {
        Objects.requireNonNull(lastVerifiedAt, "lastVerifiedAt must not be null");
        Objects.requireNonNull(now, "now must not be null");
        
        Instant staleAfter = calculateStaleAfter(lastVerifiedAt, evidenceStrength);
        return now.isBefore(staleAfter);
    }

    /**
     * Creates a default freshness policy with sensible defaults.
     *
     * @return default freshness policy
     */
    @NotNull
    public static FreshnessPolicy defaultPolicy() {
        return new FreshnessPolicy(
                "default",
                Duration.ofDays(30),
                Duration.ofDays(90),
                0.7,
                true
        );
    }
}
