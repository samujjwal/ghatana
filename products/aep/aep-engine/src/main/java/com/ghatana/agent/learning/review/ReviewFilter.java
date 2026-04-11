/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.learning.review;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Filter criteria for querying the {@link HumanReviewQueue}.
 *
 * @doc.type record
 * @doc.purpose Review queue query filter
 * @doc.layer agent-learning
 * @doc.pattern ValueObject
 *
 * @param tenantId      filter by tenant (null = all tenants)
 * @param itemType      filter by item type (null = all types)
 * @param maxConfidence filter items with confidence ≤ this value (null = no filter)
 * @param assignedTo    filter by assigned reviewer (null = all)
 * @param limit         max items to return (0 = unlimited)
 *
 * @since 2.4.0
 */
public record ReviewFilter(
        @Nullable String tenantId,
        @Nullable ReviewItemType itemType,
        @Nullable Double maxConfidence,
        @Nullable String assignedTo,
        int limit
) {
    /** Returns a filter for all pending items in a tenant. */
    public static ReviewFilter forTenant(@NotNull String tenantId) {
        return new ReviewFilter(tenantId, null, null, null, 100);
    }

    /** Returns a filter for low-confidence items across all tenants. */
    public static ReviewFilter lowConfidence(double threshold) {
        return new ReviewFilter(null, null, threshold, null, 100);
    }
}
