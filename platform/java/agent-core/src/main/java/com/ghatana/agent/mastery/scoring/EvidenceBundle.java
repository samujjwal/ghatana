/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery.scoring;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bundle of evidence for scoring mastery.
 *
 * @doc.type record
 * @doc.purpose Evidence bundle for mastery scoring
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record EvidenceBundle(
        @NotNull String bundleId,
        @NotNull String masteryId,
        @NotNull Instant observedAt,
        @NotNull List<EvidenceItem> items,
        @NotNull Map<String, Double> weights
) {
    public EvidenceBundle {
        Objects.requireNonNull(bundleId, "bundleId must not be null");
        Objects.requireNonNull(masteryId, "masteryId must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(weights, "weights must not be null");
        items = List.copyOf(items);
        weights = Map.copyOf(weights);
    }

    /**
     * Returns the total weight of all evidence items.
     *
     * @return total weight
     */
    public double totalWeight() {
        return weights.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /**
     * A single evidence item.
     */
    public record EvidenceItem(
            @NotNull String itemId,
            @NotNull String type,
            @NotNull String description,
            double value,
            @NotNull Map<String, String> metadata
    ) {
        public EvidenceItem {
            Objects.requireNonNull(itemId, "itemId must not be null");
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(description, "description must not be null");
            Objects.requireNonNull(metadata, "metadata must not be null");
            metadata = Map.copyOf(metadata);
        }
    }
}
