/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval.mastery;

import com.ghatana.agent.memory.retrieval.mastery.RetrievedContext.MemoryItem;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Ranks memory items based on freshness and evidence strength.
 *
 * <p>Prioritizes recently verified items with strong evidence.
 *
 * @doc.type class
 * @doc.purpose Ranks memory items by freshness and evidence
 * @doc.layer agent-runtime
 * @doc.pattern Ranker
 */
public class FreshnessAwareRanker {

    private final double freshnessWeight;
    private final double evidenceWeight;

    /**
     * Creates a freshness-aware ranker.
     *
     * @param freshnessWeight weight for freshness score (0.0 to 1.0)
     * @param evidenceWeight weight for evidence score (0.0 to 1.0)
     */
    public FreshnessAwareRanker(double freshnessWeight, double evidenceWeight) {
        this.freshnessWeight = freshnessWeight;
        this.evidenceWeight = evidenceWeight;
    }

    /**
     * Creates a default freshness-aware ranker.
     */
    public FreshnessAwareRanker() {
        this(0.6, 0.4);
    }

    /**
     * Ranks memory items by freshness and evidence strength.
     *
     * @param items the items to rank
     * @param now the current time
     * @return ranked items (highest score first)
     */
    @NotNull
    public List<MemoryItem> rank(@NotNull List<MemoryItem> items, @NotNull Instant now) {
        return items.stream()
                .sorted(Comparator.comparingDouble(item -> -calculateScore(item, now)))
                .toList();
    }

    /**
     * Calculates a composite score for a memory item.
     *
     * @param item the memory item
     * @param now the current time
     * @return composite score (higher is better)
     */
    private double calculateScore(@NotNull MemoryItem item, @NotNull Instant now) {
        double freshnessScore = calculateFreshnessScore(item, now);
        double evidenceScore = item.confidence();

        return (freshnessWeight * freshnessScore) + (evidenceWeight * evidenceScore);
    }

    /**
     * Calculates freshness score based on metadata timestamps.
     *
     * @param item the memory item
     * @param now the current time
     * @return freshness score (0.0 to 1.0)
     */
    private double calculateFreshnessScore(@NotNull MemoryItem item, @NotNull Instant now) {
        String lastVerifiedStr = item.metadata().get("lastVerifiedAt");
        if (lastVerifiedStr == null) {
            return 0.5; // Default for items without timestamp
        }

        try {
            Instant lastVerified = Instant.parse(lastVerifiedStr);
            long daysSinceVerification = java.time.Duration.between(lastVerified, now).toDays();

            // Decay score over time (90 days to 0.5)
            if (daysSinceVerification >= 90) {
                return 0.5;
            }
            return 1.0 - (daysSinceVerification / 180.0); // Linear decay over 180 days
        } catch (Exception e) {
            return 0.5; // Default on parse error
        }
    }
}
