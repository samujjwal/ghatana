package com.ghatana.virtualorg.framework.memory;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Interface for agent memory storage and retrieval.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides persistent memory for agents to store and retrieve: - Short-term
 * working memory (current task context) - Long-term episodic memory (past
 * experiences) - Semantic memory (facts and knowledge)
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentMemory memory = new PostgresAgentMemory(dataSource, embeddings);
 *
 * // Store a memory
 * memory.store(MemoryEntry.builder()
 *     .agentId("agent-1")
 *     .type(MemoryType.EPISODIC)
 *     .content("Successfully fixed bug #123")
 *     .metadata(Map.of("bug_id", "123"))
 *     .build());
 *
 * // Search by similarity
 * List<MemoryEntry> similar = memory.searchSimilar(
 *     "agent-1", "bug fix", 5
 * ).getResult();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Agent memory storage
 * @doc.layer product
 * @doc.pattern Repository
 *
 */
public interface AgentMemory {

    /**
     * Stores a memory entry.
     *
     * @param entry Memory entry to store
     * @return Promise of the stored entry with ID
     */
    Promise<MemoryEntry> store(MemoryEntry entry);

    /**
     * Stores multiple memory entries.
     *
     * @param entries Entries to store
     * @return Promise of the stored entries
     */
    Promise<List<MemoryEntry>> storeAll(List<MemoryEntry> entries);

    /**
     * Retrieves a memory entry by ID.
     *
     * @param id Memory entry ID
     * @return Promise of the memory entry, or null if not found
     */
    Promise<MemoryEntry> getById(String id);

    /**
     * Retrieves recent memories for an agent.
     *
     * @param agentId ID of the agent
     * @param limit Maximum number of entries to return
     * @return Promise of recent memory entries
     */
    Promise<List<MemoryEntry>> getRecent(String agentId, int limit);

    /**
     * Retrieves memories by type for an agent.
     *
     * @param agentId ID of the agent
     * @param type Memory type to filter by
     * @param limit Maximum number of entries to return
     * @return Promise of matching memory entries
     */
    Promise<List<MemoryEntry>> getByType(String agentId, MemoryType type, int limit);

    /**
     * Searches for similar memories using vector similarity.
     *
     * @param agentId ID of the agent
     * @param query Search query text
     * @param limit Maximum number of entries to return
     * @return Promise of similar memory entries
     */
    Promise<List<MemoryEntry>> searchSimilar(String agentId, String query, int limit);

    /**
     * Searches for similar memories with a minimum similarity threshold.
     *
     * @param agentId ID of the agent
     * @param query Search query text
     * @param limit Maximum number of entries to return
     * @param minSimilarity Minimum similarity score (0.0 to 1.0)
     * @return Promise of similar memory entries
     */
    Promise<List<MemoryEntry>> searchSimilar(String agentId, String query,
            int limit, double minSimilarity);

    /**
     * Searches memories with filters.
     *
     * @param query Query parameters
     * @return Promise of matching memory entries
     */
    Promise<List<MemoryEntry>> search(MemoryQuery query);

    /**
     * Updates a memory entry.
     *
     * @param entry Updated memory entry
     * @return Promise of the updated entry
     */
    Promise<MemoryEntry> update(MemoryEntry entry);

    /**
     * Deletes a memory entry by ID.
     *
     * @param id Memory entry ID
     * @return Promise indicating success
     */
    Promise<Boolean> delete(String id);

    /**
     * Deletes all memories for an agent.
     *
     * @param agentId ID of the agent
     * @return Promise of the number of deleted entries
     */
    Promise<Integer> deleteAllForAgent(String agentId);

    /**
     * Consolidates memories by summarizing old entries.
     *
     * @param agentId ID of the agent
     * @param olderThanDays Delete/summarize entries older than this
     * @return Promise of the number of consolidated entries
     */
    Promise<Integer> consolidate(String agentId, int olderThanDays);
}
