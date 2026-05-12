/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.mastery.MasteryItem;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Ranker that prioritizes memory items based on freshness and mastery state.
 *
 * @doc.type class
 * @doc.purpose Freshness-aware memory ranking
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class FreshnessAwareRanker {

    /**
     * Ranks memory items by freshness, prioritizing fresher items.
     *
     * @param items memory items to rank
     * @return ranked list of items
     */
    @NotNull
    public static List<MasteryItem> rankByFreshness(@NotNull List<MasteryItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        return items.stream()
                .sorted(Comparator.comparingDouble(FreshnessAwareRanker::freshnessScore).reversed())
                .toList();
    }

    /**
     * Calculates a freshness score for a mastery item.
     *
     * @param item mastery item
     * @return freshness score (higher is fresher)
     */
    private static double freshnessScore(@NotNull MasteryItem item) {
        java.time.Instant now = java.time.Instant.now();
        java.time.Duration age = java.time.Duration.between(item.lastVerifiedAt(), now);

        // Base score from freshness
        double score = item.score().freshness();

        // Decay based on age
        double ageDecay = Math.exp(-age.toDays() / 30.0); // Decay over 30 days

        return score * ageDecay;
    }

    /**
     * Filters items to only those that are fresh.
     *
     * @param items memory items to filter
     * @return list of fresh items
     */
    @NotNull
    public static List<MasteryItem> filterFresh(@NotNull List<MasteryItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        java.time.Instant now = java.time.Instant.now();
        return items.stream()
                .filter(item -> item.isFresh(now))
                .toList();
    }
}
