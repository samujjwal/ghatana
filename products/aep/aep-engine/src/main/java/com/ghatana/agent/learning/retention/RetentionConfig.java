package com.ghatana.agent.learning.retention;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Configuration for the retention manager.
 *
 * @doc.type class
 * @doc.purpose Retention policy configuration
 * @doc.layer agent-learning
 */
@Value
@Builder
public class RetentionConfig {

    /** Maximum age before items are eligible for eviction. */
    @Builder.Default
    @NotNull Duration maxAge = Duration.ofDays(90);

    /** Minimum utility score to keep an item. Below this, decay is applied. */
    @Builder.Default
    double minUtility = 0.3;

    /** Utility threshold below which items are evicted entirely. */
    @Builder.Default
    double evictionThreshold = 0.05;

    /** Weight for recency in utility computation. */
    @Builder.Default
    double recencyWeight = 0.4;

    /** Weight for confidence in utility computation. */
    @Builder.Default
    double confidenceWeight = 0.6;

    /** Decay function to use for recency scoring. */
    @Builder.Default
    @NotNull DecayFunction decayFunction = ExponentialDecay.sevenDay();
}
