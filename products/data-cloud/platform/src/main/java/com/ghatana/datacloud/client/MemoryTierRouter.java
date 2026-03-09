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

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.attention.SalienceScore;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service Provider Interface for routing records to appropriate memory tiers.
 *
 * <p>The Memory Tier Router is responsible for determining the optimal tier
 * placement for data records based on their salience, access patterns, and
 * configured policies. It coordinates with storage backends to execute
 * tier transitions.
 *
 * <h2>Core Responsibilities</h2>
 * <ul>
 *   <li>Initial tier placement based on salience scoring</li>
 *   <li>Promotion/demotion decisions based on access patterns</li>
 *   <li>Capacity management and eviction coordination</li>
 *   <li>Cross-tier query routing</li>
 *   <li>Metrics and observability</li>
 * </ul>
 *
 * <h2>Routing Strategy</h2>
 * <pre>
 *     Incoming Record
 *           │
 *           ▼
 *    ┌─────────────┐
 *    │   Salience  │
 *    │   Scoring   │
 *    └──────┬──────┘
 *           │
 *           ▼
 *    ┌─────────────┐     ┌─────────────┐
 *    │    Tier     │────▶│   Capacity  │
 *    │  Selection  │     │    Check    │
 *    └──────┬──────┘     └──────┬──────┘
 *           │                   │
 *           ▼                   ▼
 *    ┌─────────────┐     ┌─────────────┐
 *    │   Store in  │◀────│   Evict if  │
 *    │    Tier     │     │   Needed    │
 *    └─────────────┘     └─────────────┘
 * </pre>
 *
 * <h2>Implementation Requirements</h2>
 * <ul>
 *   <li>Thread-safe for concurrent access</li>
 *   <li>Non-blocking operations using ActiveJ Promise</li>
 *   <li>Configurable per tenant</li>
 *   <li>Observable via metrics</li>
 * </ul>
 *
 * @param <R> the type of data record being routed
 *
 * @doc.type interface
 * @doc.purpose SPI for tier-based memory routing
 * @doc.layer core
 * @doc.pattern Strategy, Service Provider Interface
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 * @see MemoryTier
 * @see TierPolicy
 * @see SalienceScore
 */
public interface MemoryTierRouter<R extends DataRecord> {

    /**
     * Routes a record to an appropriate tier based on its salience.
     *
     * <p>This is the primary entry point for new records. The router evaluates
     * the salience score and places the record in the appropriate tier,
     * handling any necessary eviction.
     *
     * @param record the record to route
     * @param salienceScore the calculated salience for the record
     * @return Promise containing the routing result with tier placement
     */
    Promise<RoutingResult<R>> route(R record, SalienceScore salienceScore);

    /**
     * Routes multiple records in a batch.
     *
     * <p>More efficient than individual routing for high-throughput scenarios.
     * Batch operations can optimize tier capacity checks and eviction.
     *
     * @param records map of records to their salience scores
     * @return Promise containing batch routing results
     */
    Promise<BatchRoutingResult<R>> routeBatch(Map<R, SalienceScore> records);

    /**
     * Retrieves a record by ID, searching across tiers.
     *
     * <p>Searches from HOT to ARCHIVE tier, returning the first match.
     * The search order can be optimized based on access patterns.
     *
     * @param recordId the record ID to find
     * @param tenantId the tenant ID
     * @return Promise containing the record if found
     */
    Promise<Optional<R>> get(String recordId, String tenantId);

    /**
     * Retrieves a record from a specific tier.
     *
     * @param recordId the record ID
     * @param tenantId the tenant ID
     * @param tier the specific tier to search
     * @return Promise containing the record if found in that tier
     */
    Promise<Optional<R>> getFromTier(String recordId, String tenantId, MemoryTier tier);

    /**
     * Records an access to a record, updating its metadata.
     *
     * <p>Access recording is used to track patterns for promotion/demotion
     * decisions. May trigger asynchronous promotion if the record's access
     * pattern warrants it.
     *
     * @param recordId the accessed record ID
     * @param tenantId the tenant ID
     * @return Promise containing updated tier entry
     */
    Promise<Optional<TierEntry>> recordAccess(String recordId, String tenantId);

    /**
     * Evaluates a record for tier transition.
     *
     * <p>Checks whether the record should be promoted or demoted based on
     * its current salience and access patterns. Does not actually move
     * the record - use {@link #executeTransition} for that.
     *
     * @param recordId the record ID to evaluate
     * @param tenantId the tenant ID
     * @param currentSalience the record's current salience
     * @return Promise containing transition recommendation
     */
    Promise<TransitionRecommendation> evaluateTransition(
            String recordId,
            String tenantId,
            double currentSalience);

    /**
     * Executes a tier transition for a record.
     *
     * @param recordId the record ID to move
     * @param tenantId the tenant ID
     * @param targetTier the destination tier
     * @return Promise containing the transition result
     */
    Promise<TransitionResult> executeTransition(
            String recordId,
            String tenantId,
            MemoryTier targetTier);

    /**
     * Evicts records from a tier to meet capacity constraints.
     *
     * <p>Uses the configured eviction strategy to select victims.
     * Evicted records may be demoted to lower tiers or removed entirely.
     *
     * @param tier the tier to evict from
     * @param targetUtilization target utilization after eviction
     * @return Promise containing eviction results
     */
    Promise<EvictionResult> evict(MemoryTier tier, double targetUtilization);

    /**
     * Gets the current tier entry for a record.
     *
     * @param recordId the record ID
     * @param tenantId the tenant ID
     * @return Promise containing the tier entry if found
     */
    Promise<Optional<TierEntry>> getTierEntry(String recordId, String tenantId);

    /**
     * Lists all entries in a tier.
     *
     * @param tier the tier to list
     * @param tenantId the tenant ID (null for all tenants)
     * @param limit maximum entries to return
     * @return Promise containing tier entries
     */
    Promise<List<TierEntry>> listTierEntries(MemoryTier tier, String tenantId, int limit);

    /**
     * Gets current statistics for a tier.
     *
     * @param tier the tier
     * @return Promise containing tier statistics
     */
    Promise<TierStatistics> getTierStatistics(MemoryTier tier);

    /**
     * Gets statistics for all tiers.
     *
     * @return Promise containing map of tier to statistics
     */
    Promise<Map<MemoryTier, TierStatistics>> getAllTierStatistics();

    /**
     * Gets the policy for a tier.
     *
     * @param tier the tier
     * @return the tier's policy
     */
    TierPolicy getPolicy(MemoryTier tier);

    /**
     * Updates the policy for a tier.
     *
     * @param tier the tier
     * @param policy the new policy
     */
    void updatePolicy(MemoryTier tier, TierPolicy policy);

    /**
     * Pins a record, preventing eviction.
     *
     * @param recordId the record ID
     * @param tenantId the tenant ID
     * @param reason the reason for pinning
     * @return Promise indicating success
     */
    Promise<Boolean> pin(String recordId, String tenantId, String reason);

    /**
     * Unpins a record, allowing eviction.
     *
     * @param recordId the record ID
     * @param tenantId the tenant ID
     * @return Promise indicating success
     */
    Promise<Boolean> unpin(String recordId, String tenantId);

    /**
     * Removes a record from all tiers.
     *
     * @param recordId the record ID
     * @param tenantId the tenant ID
     * @return Promise indicating if record was found and removed
     */
    Promise<Boolean> remove(String recordId, String tenantId);

    // ═══════════════════════════════════════════════════════════════════════════
    // Result Types
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of a routing operation.
     *
     * @param <R> the record type
     */
    record RoutingResult<R extends DataRecord>(
            R record,
            MemoryTier targetTier,
            TierEntry entry,
            boolean evictionTriggered,
            RoutingOutcome outcome,
            String message
    ) {
        /**
         * Possible outcomes of a routing operation.
         */
        public enum RoutingOutcome {
            /** Record placed in target tier successfully */
            PLACED,
            /** Record already exists, entry updated */
            UPDATED,
            /** Placed after evicting other records */
            PLACED_AFTER_EVICTION,
            /** Could not place due to capacity */
            REJECTED_CAPACITY,
            /** Could not place due to policy */
            REJECTED_POLICY,
            /** Routing failed with error */
            FAILED
        }

        /**
         * Checks if routing was successful.
         *
         * @return true if record was placed or updated
         */
        public boolean isSuccess() {
            return outcome == RoutingOutcome.PLACED
                    || outcome == RoutingOutcome.UPDATED
                    || outcome == RoutingOutcome.PLACED_AFTER_EVICTION;
        }
    }

    /**
     * Result of a batch routing operation.
     *
     * @param <R> the record type
     */
    record BatchRoutingResult<R extends DataRecord>(
            List<RoutingResult<R>> results,
            int successCount,
            int failureCount,
            Map<MemoryTier, Integer> tierDistribution,
            long processingTimeMs
    ) {
        /**
         * Checks if all records were routed successfully.
         *
         * @return true if no failures
         */
        public boolean allSucceeded() {
            return failureCount == 0;
        }
    }

    /**
     * Recommendation for tier transition.
     */
    record TransitionRecommendation(
            String recordId,
            MemoryTier currentTier,
            MemoryTier recommendedTier,
            TransitionType type,
            double confidence,
            String reason
    ) {
        /**
         * Types of tier transitions.
         */
        public enum TransitionType {
            /** Keep in current tier */
            NONE,
            /** Move to higher priority tier */
            PROMOTE,
            /** Move to lower priority tier */
            DEMOTE,
            /** Remove from all tiers */
            EVICT
        }

        /**
         * Checks if a transition is recommended.
         *
         * @return true if not staying in current tier
         */
        public boolean hasTransition() {
            return type != TransitionType.NONE;
        }
    }

    /**
     * Result of a tier transition.
     */
    record TransitionResult(
            String recordId,
            MemoryTier sourceTier,
            MemoryTier targetTier,
            boolean success,
            TierEntry updatedEntry,
            String message
    ) {}

    /**
     * Result of an eviction operation.
     */
    record EvictionResult(
            MemoryTier tier,
            int recordsEvicted,
            int recordsDemoted,
            long bytesFreed,
            double previousUtilization,
            double currentUtilization,
            long processingTimeMs
    ) {}

    /**
     * Statistics for a memory tier.
     */
    record TierStatistics(
            MemoryTier tier,
            long recordCount,
            long totalBytes,
            double utilization,
            long hitCount,
            long missCount,
            double hitRate,
            long promotionCount,
            long demotionCount,
            long evictionCount,
            java.time.Instant lastEvictionTime,
            java.time.Duration avgResidencyTime
    ) {
        /**
         * Creates empty statistics for a tier.
         *
         * @param tier the tier
         * @return empty statistics
         */
        public static TierStatistics empty(MemoryTier tier) {
            return new TierStatistics(
                    tier, 0, 0, 0.0, 0, 0, 0.0, 0, 0, 0, null,
                    java.time.Duration.ZERO
            );
        }
    }
}
