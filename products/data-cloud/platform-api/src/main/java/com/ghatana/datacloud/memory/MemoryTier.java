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

import java.time.Duration;
import java.util.Comparator;

/**
 * Memory tier enumeration representing storage hierarchy levels.
 *
 * <p>Each tier has distinct characteristics for latency, capacity, and retention,
 * allowing the system to optimize data placement based on access patterns and
 * importance. Data naturally flows from HOT to ARCHIVE as salience decreases.
 *
 * <h2>Tier Flow</h2>
 * <pre>
 *     HOT ──→ WARM ──→ COLD ──→ ARCHIVE
 *      ↑        ↑        ↑         ↑
 *      │        │        │         │
 *   (promotion on access / salience increase)
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * MemoryTier tier = MemoryTier.fromSalienceScore(0.85);
 * Duration ttl = tier.getDefaultTtl();
 * int priority = tier.getEvictionPriority();
 * }</pre>
 *
 * @doc.type enum
 * @doc.purpose Define memory tier hierarchy with characteristics
 * @doc.layer core
 * @doc.pattern Enumeration, Strategy
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public enum MemoryTier {

    /**
     * Hot tier - highest priority, lowest latency.
     *
     * <p>Used for actively processing data, real-time queries, and items
     * currently in the cognitive spotlight. Limited capacity with aggressive
     * eviction to maintain performance.
     *
     * <ul>
     *   <li>Target Latency: &lt;1ms</li>
     *   <li>Default TTL: 5 minutes</li>
     *   <li>Typical Capacity: 1-10% of total</li>
     *   <li>Storage: In-memory (heap/off-heap)</li>
     * </ul>
     */
    HOT(
            1,                          // Eviction priority (lowest = evict last)
            Duration.ofMinutes(5),      // Default TTL
            0.8,                        // Minimum salience threshold
            1.0,                        // Maximum salience threshold
            "hot"                       // Storage prefix
    ),

    /**
     * Warm tier - frequently accessed recent data.
     *
     * <p>Used for data that has been recently accessed or has moderate salience.
     * Balanced between performance and capacity. Typical target for query results.
     *
     * <ul>
     *   <li>Target Latency: &lt;10ms</li>
     *   <li>Default TTL: 1 hour</li>
     *   <li>Typical Capacity: 10-30% of total</li>
     *   <li>Storage: Fast SSD / Redis</li>
     * </ul>
     */
    WARM(
            2,
            Duration.ofHours(1),
            0.5,
            0.8,
            "warm"
    ),

    /**
     * Cold tier - historical data with occasional access.
     *
     * <p>Used for data that is kept for reference but not frequently accessed.
     * Optimized for storage efficiency over access speed.
     *
     * <ul>
     *   <li>Target Latency: &lt;100ms</li>
     *   <li>Default TTL: 7 days</li>
     *   <li>Typical Capacity: 30-60% of total</li>
     *   <li>Storage: Standard SSD / Object Storage</li>
     * </ul>
     */
    COLD(
            3,
            Duration.ofDays(7),
            0.2,
            0.5,
            "cold"
    ),

    /**
     * Archive tier - long-term storage for compliance and historical analysis.
     *
     * <p>Used for data that must be retained but is rarely accessed. Optimized
     * for cost and durability. May require retrieval delay for access.
     *
     * <ul>
     *   <li>Target Latency: &lt;1s (may require async retrieval)</li>
     *   <li>Default TTL: 365 days (configurable)</li>
     *   <li>Typical Capacity: Unlimited</li>
     *   <li>Storage: Cold Object Storage / Glacier</li>
     * </ul>
     */
    ARCHIVE(
            4,
            Duration.ofDays(365),
            0.0,
            0.2,
            "archive"
    );

    private final int evictionPriority;
    private final Duration defaultTtl;
    private final double minSalienceThreshold;
    private final double maxSalienceThreshold;
    private final String storagePrefix;

    /**
     * Comparator ordering tiers by eviction priority (HOT first, ARCHIVE last).
     */
    public static final Comparator<MemoryTier> BY_EVICTION_PRIORITY =
            Comparator.comparingInt(MemoryTier::getEvictionPriority);

    /**
     * Comparator ordering tiers by salience threshold (ARCHIVE first, HOT last).
     */
    public static final Comparator<MemoryTier> BY_SALIENCE_ASC =
            Comparator.comparingDouble(MemoryTier::getMinSalienceThreshold);

    MemoryTier(
            int evictionPriority,
            Duration defaultTtl,
            double minSalienceThreshold,
            double maxSalienceThreshold,
            String storagePrefix) {
        this.evictionPriority = evictionPriority;
        this.defaultTtl = defaultTtl;
        this.minSalienceThreshold = minSalienceThreshold;
        this.maxSalienceThreshold = maxSalienceThreshold;
        this.storagePrefix = storagePrefix;
    }

    /**
     * Returns the eviction priority for this tier.
     *
     * <p>Lower values indicate higher priority (less likely to be evicted).
     * HOT tier has priority 1, ARCHIVE has priority 4.
     *
     * @return the eviction priority value
     */
    public int getEvictionPriority() {
        return evictionPriority;
    }

    /**
     * Returns the default time-to-live for records in this tier.
     *
     * <p>After TTL expires, records are either demoted to the next tier
     * or evicted entirely based on policy configuration.
     *
     * @return the default TTL duration
     */
    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    /**
     * Returns the minimum salience threshold for this tier.
     *
     * <p>Records with salience below this threshold should not be
     * placed in this tier (they belong in a lower tier).
     *
     * @return minimum salience score (0.0 to 1.0)
     */
    public double getMinSalienceThreshold() {
        return minSalienceThreshold;
    }

    /**
     * Returns the maximum salience threshold for this tier.
     *
     * <p>Records with salience above this threshold should be
     * promoted to a higher tier.
     *
     * @return maximum salience score (0.0 to 1.0)
     */
    public double getMaxSalienceThreshold() {
        return maxSalienceThreshold;
    }

    /**
     * Returns the storage prefix for this tier.
     *
     * <p>Used to namespace keys/paths in the underlying storage backend.
     *
     * @return the storage prefix string
     */
    public String getStoragePrefix() {
        return storagePrefix;
    }

    /**
     * Determines the appropriate tier for a given salience score.
     *
     * @param salienceScore the salience score (0.0 to 1.0)
     * @return the appropriate memory tier
     * @throws IllegalArgumentException if score is outside valid range
     */
    public static MemoryTier fromSalienceScore(double salienceScore) {
        if (salienceScore < 0.0 || salienceScore > 1.0) {
            throw new IllegalArgumentException(
                    "Salience score must be between 0.0 and 1.0, got: " + salienceScore);
        }

        for (MemoryTier tier : values()) {
            if (salienceScore >= tier.minSalienceThreshold
                    && salienceScore < tier.maxSalienceThreshold) {
                return tier;
            }
        }

        // Edge case: salience == 1.0 goes to HOT
        return HOT;
    }

    /**
     * Checks if this tier can accept records with the given salience.
     *
     * @param salienceScore the salience score to check
     * @return true if salience is within this tier's range
     */
    public boolean acceptsSalience(double salienceScore) {
        return salienceScore >= minSalienceThreshold
                && salienceScore < maxSalienceThreshold;
    }

    /**
     * Returns the next lower tier for demotion.
     *
     * @return the next lower tier, or {@link #ARCHIVE} if already at lowest
     */
    public MemoryTier demote() {
        return switch (this) {
            case HOT -> WARM;
            case WARM -> COLD;
            case COLD, ARCHIVE -> ARCHIVE;
        };
    }

    /**
     * Returns the next higher tier for promotion.
     *
     * @return the next higher tier, or {@link #HOT} if already at highest
     */
    public MemoryTier promote() {
        return switch (this) {
            case ARCHIVE -> COLD;
            case COLD -> WARM;
            case WARM, HOT -> HOT;
        };
    }

    /**
     * Checks if this tier is higher priority than another.
     *
     * @param other the tier to compare against
     * @return true if this tier has higher priority (lower eviction number)
     */
    public boolean isHigherPriorityThan(MemoryTier other) {
        return this.evictionPriority < other.evictionPriority;
    }

    /**
     * Checks if data in this tier should be considered for promotion
     * based on the given salience score.
     *
     * @param salienceScore the current salience score
     * @return true if the salience exceeds this tier's maximum threshold
     */
    public boolean shouldPromote(double salienceScore) {
        return salienceScore >= maxSalienceThreshold && this != HOT;
    }

    /**
     * Checks if data in this tier should be considered for demotion
     * based on the given salience score.
     *
     * @param salienceScore the current salience score
     * @return true if the salience is below this tier's minimum threshold
     */
    public boolean shouldDemote(double salienceScore) {
        return salienceScore < minSalienceThreshold && this != ARCHIVE;
    }

    @Override
    public String toString() {
        return String.format("MemoryTier{name=%s, priority=%d, ttl=%s, salience=[%.2f-%.2f)}",
                name(), evictionPriority, defaultTtl, minSalienceThreshold, maxSalienceThreshold);
    }
}
