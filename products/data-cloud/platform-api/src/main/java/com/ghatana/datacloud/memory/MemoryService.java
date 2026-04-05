/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for tiered memory operations.
 *
 * <p>Provides operations for managing memory across three tiers:
 * <ul>
 *   <li>Episodic: Short-term, recent experiences</li>
 *   <li>Semantic: Long-term knowledge and facts</li>
 *   <li>Procedural: Skills and behavioral patterns</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Tiered memory service for episodic, semantic, and procedural memory
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface MemoryService {

    /**
     * Memory tiers.
     */
    enum MemoryTier {
        EPISODIC,
        SEMANTIC,
        PROCEDURAL
    }

    /**
     * Store a memory entry.
     *
     * @param agentId agent identifier
     * @param tier memory tier
     * @param entry memory entry to store
     * @return promise of stored entry with ID
     */
    Promise<MemoryEntry> store(String agentId, MemoryTier tier, MemoryEntry entry);

    /**
     * Recall memories matching query.
     *
     * @param agentId agent identifier
     * @param tier memory tier
     * @param query recall query
     * @return promise of matching memories
     */
    Promise<List<MemoryEntry>> recall(String agentId, MemoryTier tier, RecallQuery query);

    /**
     * Get a specific memory by ID.
     *
     * @param agentId agent identifier
     * @param memoryId memory identifier
     * @return promise of memory if found
     */
    Promise<Optional<MemoryEntry>> get(String agentId, String memoryId);

    /**
     * Update an existing memory.
     *
     * @param agentId agent identifier
     * @param memoryId memory identifier
     * @param entry updated entry
     * @return promise of updated entry
     */
    Promise<MemoryEntry> update(String agentId, String memoryId, MemoryEntry entry);

    /**
     * Delete a memory.
     *
     * @param agentId agent identifier
     * @param memoryId memory identifier
     * @return promise completing when deleted
     */
    Promise<Void> delete(String agentId, String memoryId);

    /**
     * Consolidate memories from episodic to semantic.
     *
     * @param agentId agent identifier
     * @param criteria consolidation criteria
     * @return promise of consolidated memories
     */
    Promise<ConsolidationResult> consolidate(String agentId, ConsolidationCriteria criteria);

    /**
     * Get memory statistics.
     *
     * @param agentId agent identifier
     * @return promise of memory stats
     */
    Promise<MemoryStats> getStats(String agentId);

    /**
     * Memory entry.
     */
    record MemoryEntry(
        String id,
        String agentId,
        MemoryTier tier,
        String content,
        Object metadata,
        double importance,
        long timestamp,
        long accessCount,
        long lastAccessed
    ) {
        /**
         * Create new entry without ID (for storage).
         */
        public static MemoryEntry create(String agentId, MemoryTier tier, String content,
                                         Object metadata, double importance) {
            long now = System.currentTimeMillis();
            return new MemoryEntry(null, agentId, tier, content, metadata, importance, now, 0, now);
        }

        /**
         * Update last accessed timestamp.
         */
        public MemoryEntry accessed() {
            return new MemoryEntry(id, agentId, tier, content, metadata, importance,
                timestamp, accessCount + 1, System.currentTimeMillis());
        }
    }

    /**
     * Recall query.
     */
    record RecallQuery(
        String pattern,
        MemoryTier tier,
        Double minImportance,
        Long since,
        Long until,
        Integer limit,
        Double minRelevance
    ) {
        /**
         * Builder for recall query.
         */
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String pattern;
            private MemoryTier tier;
            private Double minImportance;
            private Long since;
            private Long until;
            private Integer limit = 10;
            private Double minRelevance = 0.5;

            public Builder pattern(String pattern) {
                this.pattern = pattern;
                return this;
            }

            public Builder tier(MemoryTier tier) {
                this.tier = tier;
                return this;
            }

            public Builder minImportance(double minImportance) {
                this.minImportance = minImportance;
                return this;
            }

            public Builder since(long since) {
                this.since = since;
                return this;
            }

            public Builder until(long until) {
                this.until = until;
                return this;
            }

            public Builder limit(int limit) {
                this.limit = limit;
                return this;
            }

            public Builder minRelevance(double minRelevance) {
                this.minRelevance = minRelevance;
                return this;
            }

            public RecallQuery build() {
                return new RecallQuery(pattern, tier, minImportance, since, until, limit, minRelevance);
            }
        }
    }

    /**
     * Consolidation criteria.
     */
    record ConsolidationCriteria(
        double minImportance,
        long olderThanMs,
        int minAccessCount,
        int batchSize
    ) {
        public static ConsolidationCriteria defaultCriteria() {
            return new ConsolidationCriteria(0.5, 86400000, 2, 100); // 24 hours
        }
    }

    /**
     * Consolidation result.
     */
    record ConsolidationResult(
        int processed,
        int consolidated,
        int failed,
        List<String> memoryIds
    ) {
        public boolean isSuccessful() {
            return failed == 0;
        }
    }

    /**
     * Memory statistics.
     */
    record MemoryStats(
        long totalCount,
        long episodicCount,
        long semanticCount,
        long proceduralCount,
        double avgImportance,
        long totalSizeBytes,
        double hitRate,
        int evictionCount
    ) {}
}
