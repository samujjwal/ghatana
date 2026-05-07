/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository for semantic (long-term) memory storage.
 *
 * <p>Manages consolidated knowledge and facts with durable persistence.
 *
 * @doc.type interface
 * @doc.purpose Long-term semantic memory repository
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface SemanticMemoryRepository {

    /**
     * Save a semantic memory.
     *
     * @param entry memory entry
     * @return promise of saved entry
     */
    Promise<MemoryService.MemoryEntry> save(MemoryService.MemoryEntry entry);

    /**
     * Find memories by content similarity.
     *
     * @param agentId agent identifier
     * @param embedding vector embedding
     * @param threshold similarity threshold (0-1)
     * @param limit max results
     * @return promise of similar memories
     */
    Promise<List<MemoryService.MemoryEntry>> findSimilar(String agentId, float[] embedding,
                                                         double threshold, int limit);

    /**
     * Find memories by keyword.
     *
     * @param agentId agent identifier
     * @param keyword search keyword
     * @param limit max results
     * @return promise of matching memories
     */
    Promise<List<MemoryService.MemoryEntry>> findByKeyword(String agentId, String keyword, int limit);

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
     * Get memories by importance threshold.
     *
     * @param agentId agent identifier
     * @param minImportance minimum importance (0-1)
     * @return promise of important memories
     */
    Promise<List<MemoryService.MemoryEntry>> findByImportance(String agentId, double minImportance);

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
     * Reindex all memories for agent.
     *
     * @param agentId agent identifier
     * @return promise completing when done
     */
    Promise<Void> reindex(String agentId);

    /**
     * Consolidate episodic memories into semantic.
     *
     * @param entries episodic entries to consolidate
     * @return promise of consolidated entries
     */
    Promise<List<MemoryService.MemoryEntry>> consolidate(List<MemoryService.MemoryEntry> entries);
}
