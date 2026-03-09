package com.ghatana.virtualorg.framework.memory;

import java.util.List;
import java.util.Optional;

/**
 * Interface for organizational memory system.
 *
 * <p><b>Purpose</b><br>
 * Provides agents with access to organizational context, decisions, and history.
 * Supports both short-term (in-memory) and long-term (persistent) storage.
 * Enables semantic search and context retrieval.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * OrganizationalMemory memory = new InMemoryOrganizationalMemory();
 * 
 * // Store a decision
 * memory.store(MemoryEntry.of(
 *     "sprint-decision",
 *     "Sprint Planning",
 *     "Decided to prioritize feature X",
 *     "ProductManager"
 * ));
 * 
 * // Retrieve recent decisions
 * List<MemoryEntry> decisions = memory.search("sprint", 10);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Part of virtual-org-framework product module. Provides memory services for
 * agents and workflows.
 *
 * @see MemoryEntry
 * @see InMemoryOrganizationalMemory
 * @doc.type interface
 * @doc.purpose Organizational memory contract
 * @doc.layer product
 * @doc.pattern Port
 */
public interface OrganizationalMemory {
    
    /**
     * Stores a memory entry.
     *
     * @param entry the memory entry to store
     */
    void store(MemoryEntry entry);
    
    /**
     * Retrieves a memory entry by ID.
     *
     * @param id the entry ID
     * @return optional containing the entry if found
     */
    Optional<MemoryEntry> retrieve(String id);
    
    /**
     * Searches memory entries by query.
     *
     * @param query the search query
     * @param limit maximum number of results
     * @return list of matching entries
     */
    List<MemoryEntry> search(String query, int limit);
    
    /**
     * Searches memory entries by category.
     *
     * @param category the category to search
     * @param limit maximum number of results
     * @return list of entries in category
     */
    List<MemoryEntry> searchByCategory(String category, int limit);
    
    /**
     * Searches memory entries by actor.
     *
     * @param actor the actor who created the entry
     * @param limit maximum number of results
     * @return list of entries created by actor
     */
    List<MemoryEntry> searchByActor(String actor, int limit);
    
    /**
     * Gets recent memory entries.
     *
     * @param limit maximum number of entries
     * @return list of recent entries
     */
    List<MemoryEntry> getRecent(int limit);
    
    /**
     * Clears all memory entries.
     */
    void clear();
    
    /**
     * Gets the total number of entries.
     */
    long getSize();
}
