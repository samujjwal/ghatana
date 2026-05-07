/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository for episodic (short-term) memory storage.
 *
 * <p>Manages recent experiences with automatic eviction policies.
 *
 * @doc.type interface
 * @doc.purpose Short-term episodic memory repository
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface EpisodicMemoryRepository {

    /**
     * Save an episodic memory.
     *
     * @param entry memory entry
     * @return promise of saved entry
     */
    Promise<MemoryService.MemoryEntry> save(MemoryService.MemoryEntry entry);

    /**
     * Find memories by pattern.
     *
     * @param agentId agent identifier
     * @param pattern search pattern
     * @param limit max results
     * @return promise of matching memories
     */
    Promise<List<MemoryService.MemoryEntry>> findByPattern(String agentId, String pattern, int limit);

    /**
     * Find memories by time range.
     *
     * @param agentId agent identifier
     * @param startTime start timestamp
     * @param endTime end timestamp
     * @return promise of memories in range
     */
    Promise<List<MemoryService.MemoryEntry>> findByTimeRange(String agentId, long startTime, long endTime);

    /**
     * Get memory by ID.
     *
     * @param memoryId memory identifier
     * @return promise of memory if found
     */
    Promise<Optional<MemoryService.MemoryEntry>> findById(String memoryId);

    /**
     * Update memory.
     *
     * @param memoryId memory identifier
     * @param entry updated entry
     * @return promise of updated entry
     */
    Promise<MemoryService.MemoryEntry> update(String memoryId, MemoryService.MemoryEntry entry);

    /**
     * Delete memory.
     *
     * @param memoryId memory identifier
     * @return promise of true if deleted
     */
    Promise<Boolean> delete(String memoryId);

    /**
     * Delete old memories.
     *
     * @param olderThanMs delete memories older than this
     * @return promise of count deleted
     */
    Promise<Integer> deleteOlderThan(long olderThanMs);

    /**
     * Get least recently used memories for eviction.
     *
     * @param agentId agent identifier
     * @param limit max entries
     * @return promise of LRU memories
     */
    Promise<List<MemoryService.MemoryEntry>> findLRU(String agentId, int limit);

    /**
     * Count memories for agent.
     *
     * @param agentId agent identifier
     * @return promise of count
     */
    Promise<Long> count(String agentId);

    /**
     * Count total size in bytes.
     *
     * @param agentId agent identifier
     * @return promise of size
     */
    Promise<Long> size(String agentId);

    /**
     * Evict least important memories.
     *
     * @param agentId agent identifier
     * @param targetSize target size in bytes
     * @return promise of evicted count
     */
    Promise<Integer> evictToTarget(String agentId, long targetSize);
}
