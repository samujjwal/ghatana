/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.memory;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration policy for a memory tier.
 *
 * <p>Defines capacity limits, TTL settings, eviction strategies, and promotion/demotion
 * thresholds for a specific memory tier. Policies can be customized per tenant or
 * data category.
 *
 * <h2>Policy Components</h2>
 * <ul>
 *   <li><b>Capacity</b>: Maximum records and bytes for the tier</li>
 *   <li><b>TTL</b>: Time-to-live and grace periods</li>
 *   <li><b>Eviction</b>: Strategy and thresholds for capacity management</li>
 *   <li><b>Transitions</b>: Promotion and demotion criteria</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * TierPolicy hotPolicy = TierPolicy.builder()
 *     .tier(MemoryTier.HOT)
 *     .maxRecords(10_000)
 *     .maxBytes(100 * 1024 * 1024) // 100MB
 *     .ttl(Duration.ofMinutes(5))
 *     .evictionStrategy(EvictionStrategy.LRU)
 *     .evictionThreshold(0.9)
 *     .promotionSalienceThreshold(0.95)
 *     .demotionSalienceThreshold(0.7)
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Configuration for tier behavior and limits
 * @doc.layer core
 * @doc.pattern Value Object, Configuration
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 * @see MemoryTier
 * @see MemoryTierRouter
 */
@Value
@Builder(toBuilder = true)
public class TierPolicy {

    /**
     * The memory tier this policy applies to.
     */
    MemoryTier tier;

    /**
     * Maximum number of records allowed in this tier.
     *
     * <p>When exceeded, eviction is triggered based on the configured strategy.
     * A value of -1 indicates unlimited records.
     */
    @Builder.Default
    long maxRecords = -1;

    /**
     * Maximum total bytes allowed in this tier.
     *
     * <p>When exceeded, eviction is triggered. A value of -1 indicates unlimited.
     * Actual enforcement depends on storage backend capabilities.
     */
    @Builder.Default
    long maxBytes = -1;

    /**
     * Time-to-live for records in this tier.
     *
     * <p>After TTL expires, records are eligible for demotion or eviction.
     * If null, uses the tier's default TTL.
     */
    Duration ttl;

    /**
     * Grace period after TTL before actual eviction.
     *
     * <p>Allows recently accessed expired items to remain if accessed again
     * within the grace period.
     */
    @Builder.Default
    Duration ttlGracePeriod = Duration.ZERO;

    /**
     * Strategy for evicting records when capacity is reached.
     */
    @Builder.Default
    EvictionStrategy evictionStrategy = EvictionStrategy.SALIENCE_WEIGHTED_LRU;

    /**
     * Capacity utilization threshold that triggers eviction.
     *
     * <p>When utilization exceeds this threshold (0.0-1.0), eviction begins.
     * Lower values provide more headroom but waste capacity.
     */
    @Builder.Default
    double evictionThreshold = 0.85;

    /**
     * Target utilization after eviction completes.
     *
     * <p>Eviction continues until utilization falls below this level.
     * Should be lower than eviction threshold to prevent thrashing.
     */
    @Builder.Default
    double evictionTarget = 0.70;

    /**
     * Salience threshold above which records should be promoted.
     *
     * <p>Records with salience exceeding this value are candidates for
     * promotion to the next higher tier. Ignored for HOT tier.
     */
    Double promotionSalienceThreshold;

    /**
     * Salience threshold below which records should be demoted.
     *
     * <p>Records with salience below this value are candidates for
     * demotion to the next lower tier. Ignored for ARCHIVE tier.
     */
    Double demotionSalienceThreshold;

    /**
     * Access count threshold for promotion consideration.
     *
     * <p>Records must be accessed at least this many times within the
     * evaluation window to be promoted, regardless of salience.
     */
    @Builder.Default
    int promotionAccessCountThreshold = 3;

    /**
     * Duration window for evaluating access patterns.
     *
     * <p>Access counts and patterns are evaluated within this rolling window
     * for promotion/demotion decisions.
     */
    @Builder.Default
    Duration accessEvaluationWindow = Duration.ofMinutes(15);

    /**
     * Whether to enable automatic tier transitions.
     *
     * <p>When false, records remain in their initial tier until explicitly
     * moved or evicted.
     */
    @Builder.Default
    boolean autoTransitionEnabled = true;

    /**
     * Minimum time a record must stay in this tier before demotion.
     *
     * <p>Prevents thrashing by ensuring records have sufficient time to
     * prove their value in the current tier.
     */
    @Builder.Default
    Duration minimumTierResidency = Duration.ofMinutes(1);

    /**
     * Custom metadata for this policy.
     *
     * <p>Used for tenant-specific overrides or feature flags.
     */
    @Builder.Default
    Map<String, Object> metadata = Map.of();

    /**
     * Returns the effective TTL, falling back to tier default if not set.
     *
     * @return the TTL to apply for records in this tier
     */
    public Duration getEffectiveTtl() {
        return ttl != null ? ttl : tier.getDefaultTtl();
    }

    /**
     * Returns the effective promotion threshold.
     *
     * @return promotion threshold, or tier's max salience if not set
     */
    public double getEffectivePromotionThreshold() {
        return promotionSalienceThreshold != null
                ? promotionSalienceThreshold
                : tier.getMaxSalienceThreshold();
    }

    /**
     * Returns the effective demotion threshold.
     *
     * @return demotion threshold, or tier's min salience if not set
     */
    public double getEffectiveDemotionThreshold() {
        return demotionSalienceThreshold != null
                ? demotionSalienceThreshold
                : tier.getMinSalienceThreshold();
    }

    /**
     * Checks if a record should be promoted based on salience.
     *
     * @param salienceScore the record's current salience
     * @return true if promotion is warranted
     */
    public boolean shouldPromote(double salienceScore) {
        return tier != MemoryTier.HOT
                && autoTransitionEnabled
                && salienceScore >= getEffectivePromotionThreshold();
    }

    /**
     * Checks if a record should be demoted based on salience.
     *
     * @param salienceScore the record's current salience
     * @return true if demotion is warranted
     */
    public boolean shouldDemote(double salienceScore) {
        return tier != MemoryTier.ARCHIVE
                && autoTransitionEnabled
                && salienceScore < getEffectiveDemotionThreshold();
    }

    /**
     * Creates a default policy for the given tier.
     *
     * @param tier the memory tier
     * @return a default policy for that tier
     */
    public static TierPolicy defaultFor(MemoryTier tier) {
        return switch (tier) {
            case HOT -> TierPolicy.builder()
                    .tier(MemoryTier.HOT)
                    .maxRecords(10_000)
                    .maxBytes(100 * 1024 * 1024L)  // 100MB
                    .evictionThreshold(0.90)
                    .evictionTarget(0.75)
                    .minimumTierResidency(Duration.ofSeconds(30))
                    .build();

            case WARM -> TierPolicy.builder()
                    .tier(MemoryTier.WARM)
                    .maxRecords(100_000)
                    .maxBytes(1024 * 1024 * 1024L)  // 1GB
                    .evictionThreshold(0.85)
                    .evictionTarget(0.70)
                    .minimumTierResidency(Duration.ofMinutes(5))
                    .build();

            case COLD -> TierPolicy.builder()
                    .tier(MemoryTier.COLD)
                    .maxRecords(1_000_000)
                    .maxBytes(10L * 1024 * 1024 * 1024)  // 10GB
                    .evictionThreshold(0.80)
                    .evictionTarget(0.65)
                    .minimumTierResidency(Duration.ofMinutes(30))
                    .build();

            case ARCHIVE -> TierPolicy.builder()
                    .tier(MemoryTier.ARCHIVE)
                    .maxRecords(-1)  // Unlimited
                    .maxBytes(-1)    // Unlimited
                    .evictionThreshold(0.95)
                    .evictionTarget(0.90)
                    .autoTransitionEnabled(false)  // No auto-eviction from archive
                    .minimumTierResidency(Duration.ofDays(1))
                    .build();
        };
    }

    /**
     * Strategy for evicting records when tier capacity is exceeded.
     */
    public enum EvictionStrategy {

        /**
         * Least Recently Used - evicts records with oldest access time.
         */
        LRU,

        /**
         * Least Frequently Used - evicts records with fewest accesses.
         */
        LFU,

        /**
         * First In First Out - evicts oldest records by insertion time.
         */
        FIFO,

        /**
         * Salience-weighted LRU - considers both recency and salience.
         *
         * <p>Combines access recency with salience score to make eviction
         * decisions. Lower salience records are evicted first, with recency
         * as a tiebreaker.
         */
        SALIENCE_WEIGHTED_LRU,

        /**
         * Time-To-Live based - evicts expired records first.
         *
         * <p>Prioritizes eviction of expired or near-expired records,
         * falling back to LRU for unexpired items.
         */
        TTL_FIRST,

        /**
         * Random eviction - selects victims randomly.
         *
         * <p>Simple but unpredictable. Useful as a baseline or when
         * access patterns are uniform.
         */
        RANDOM
    }

    /**
     * Validates this policy configuration.
     *
     * @return Optional containing error message if invalid, empty if valid
     */
    public Optional<String> validate() {
        if (tier == null) {
            return Optional.of("Tier must not be null");
        }
        if (evictionThreshold < 0 || evictionThreshold > 1) {
            return Optional.of("Eviction threshold must be between 0 and 1");
        }
        if (evictionTarget < 0 || evictionTarget > 1) {
            return Optional.of("Eviction target must be between 0 and 1");
        }
        if (evictionTarget >= evictionThreshold) {
            return Optional.of("Eviction target must be lower than threshold");
        }
        if (promotionSalienceThreshold != null
                && (promotionSalienceThreshold < 0 || promotionSalienceThreshold > 1)) {
            return Optional.of("Promotion threshold must be between 0 and 1");
        }
        if (demotionSalienceThreshold != null
                && (demotionSalienceThreshold < 0 || demotionSalienceThreshold > 1)) {
            return Optional.of("Demotion threshold must be between 0 and 1");
        }
        if (promotionAccessCountThreshold < 0) {
            return Optional.of("Promotion access count must be non-negative");
        }
        return Optional.empty();
    }
}
