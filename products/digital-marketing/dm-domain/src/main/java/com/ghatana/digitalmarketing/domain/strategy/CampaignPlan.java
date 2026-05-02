package com.ghatana.digitalmarketing.domain.strategy;

import java.util.List;
import java.util.Objects;

/**
 * A channel-specific campaign plan within a 30-day strategy.
 *
 * @doc.type class
 * @doc.purpose Represents a per-channel plan with objective, budget, key messages, and target keywords
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CampaignPlan(
    StrategyChannel channelType,
    String objective,
    int estimatedBudget,
    List<String> keyMessages,
    List<String> targetKeywords
) {
    /**
     * Compact constructor that validates all fields.
     */
    public CampaignPlan {
        Objects.requireNonNull(channelType, "channelType must not be null");
        Objects.requireNonNull(objective, "objective must not be null");
        if (objective.isBlank()) {
            throw new IllegalArgumentException("objective must not be blank");
        }
        if (estimatedBudget < 0) {
            throw new IllegalArgumentException("estimatedBudget must not be negative");
        }
        Objects.requireNonNull(keyMessages, "keyMessages must not be null");
        Objects.requireNonNull(targetKeywords, "targetKeywords must not be null");
        keyMessages = List.copyOf(keyMessages);
        targetKeywords = List.copyOf(targetKeywords);
    }
}
