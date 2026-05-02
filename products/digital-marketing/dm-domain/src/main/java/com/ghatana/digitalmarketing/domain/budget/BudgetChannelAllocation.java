package com.ghatana.digitalmarketing.domain.budget;

import java.util.Objects;

/**
 * Immutable recommended budget allocation for a single marketing channel.
 *
 * @doc.type record
 * @doc.purpose Represents per-channel budget allocation within a BudgetRecommendation
 * @doc.layer product
 * @doc.pattern DomainModel
 */
public record BudgetChannelAllocation(
        String channelType,
        double recommendedAmount,
        double dailyCap,
        String rationale) {

    public BudgetChannelAllocation {
        Objects.requireNonNull(channelType, "channelType must not be null");
        if (channelType.isBlank()) {
            throw new IllegalArgumentException("channelType must not be blank");
        }
        if (recommendedAmount < 0) {
            throw new IllegalArgumentException("recommendedAmount must be non-negative");
        }
        if (dailyCap < 0) {
            throw new IllegalArgumentException("dailyCap must be non-negative");
        }
        Objects.requireNonNull(rationale, "rationale must not be null");
        if (rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
    }
}
