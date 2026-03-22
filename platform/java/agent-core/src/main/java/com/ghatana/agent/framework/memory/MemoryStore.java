package com.ghatana.agent.framework.memory;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Interface for agent memory storage supporting all GAA memory types:
 * <ul>
 *   <li><b>Episodic</b>: What happened (events, experiences)</li>
 *   <li><b>Semantic</b>: What I know (facts, knowledge)</li>
 *   <li><b>Procedural</b>: How to do things (skills, policies)</li>
 *   <li><b>Preference</b>: What I prefer (settings, biases)</li>
 * </ul>
 * 
 * <p>ALL memory operations MUST use event sourcing - append events to EventLog.
 * 
 * <p><b>Implementation Notes:</b>
 * <ul>
 *   <li>Use {@code Promise.ofBlocking(executor, ...)} for blocking operations</li>
 *   <li>Apply governance (redaction, retention) BEFORE persisting</li>
 *   <li>Support multi-tenant isolation via tenant ID</li>
 *   <li>Enable temporal queries (point-in-time, time ranges)</li>
 * </ul>
 * 
 * @doc.type interface
 * @doc.purpose Agent memory storage with event sourcing
 * @doc.layer framework
 * @doc.pattern Repository
 * @doc.gaa.memory episodic|semantic|procedural|preference
 */
public interface MemoryStore {

    /**
     * Returns a no-op {@code MemoryStore} that stores nothing and always
     * returns empty results. Useful for workflow contexts, tests, and
     * legacy agent adapters that do not require memory.
     *
     * @return a no-op memory store singleton (never null)
     * @since 2.4.0
     */
    @NotNull
    static MemoryStore noOp() {
        return NoOpMemoryStore.INSTANCE;
    }
    
    // ========== EPISODIC MEMORY ==========
    
    /**
     * Stores an episode (experience) in memory.
     * Appends event to EventLog.
     * 
     * @param episode Episode to store
     * @return Promise of stored episode with ID
     */
    @NotNull
    Promise<Episode> storeEpisode(@NotNull Episode episode);
    
    /**
     * Retrieves recent episodes, optionally filtered.
     * 
     * @param filter Filter criteria (time range, tags, etc.)
     * @param limit Maximum number of episodes
     * @return Promise of episode list
     */
    @NotNull
    Promise<List<Episode>> queryEpisodes(@NotNull MemoryFilter filter, int limit);
    
    /**
     * Searches episodes by semantic similarity.
     * 
     * @param query Search query
     * @param limit Maximum results
     * @return Promise of relevant episodes
     */
    @NotNull
    Promise<List<Episode>> searchEpisodes(@NotNull String query, int limit);
    
    // ========== SEMANTIC MEMORY ==========
    
    /**
     * Stores a fact in semantic memory.
     * 
     * @param fact Fact to store
     * @return Promise of stored fact with ID
     */
    @NotNull
    Promise<Fact> storeFact(@NotNull Fact fact);
    
    /**
     * Queries facts by predicate.
     * 
     * @param subject Subject of fact (can be null for wildcard)
     * @param predicate Predicate to match
     * @param object Object of fact (can be null for wildcard)
     * @return Promise of matching facts
     */
    @NotNull
    Promise<List<Fact>> queryFacts(
        @Nullable String subject, 
        @NotNull String predicate, 
        @Nullable String object);
    
    /**
     * Searches semantic memory by concept.
     * 
     * @param concept Concept to search for
     * @param limit Maximum results
     * @return Promise of related facts
     */
    @NotNull
    Promise<List<Fact>> searchFacts(@NotNull String concept, int limit);
    
    // ========== PROCEDURAL MEMORY ==========
    
    /**
     * Stores a learned policy.
     * 
     * @param policy Policy to store
     * @return Promise of stored policy with ID
     */
    @NotNull
    Promise<Policy> storePolicy(@NotNull Policy policy);
    
    /**
     * Queries policies matching situation.
     * 
     * @param situation Situation description
     * @param minConfidence Minimum confidence threshold
     * @return Promise of applicable policies, ordered by confidence
     */
    @NotNull
    Promise<List<Policy>> queryPolicies(@NotNull String situation, double minConfidence);
    
    /**
     * Gets policy by ID.
     * 
     * @param policyId Policy ID
     * @return Promise of policy, or null if not found
     */
    @NotNull
    Promise<Policy> getPolicy(@NotNull String policyId);
    
    // ========== PREFERENCE MEMORY ==========
    
    /**
     * Stores a preference.
     * 
     * @param preference Preference to store
     * @return Promise of stored preference
     */
    @NotNull
    Promise<Preference> storePreference(@NotNull Preference preference);
    
    /**
     * Gets preference value by key.
     * 
     * @param key Preference key
     * @return Promise of preference value, or null if not set
     */
    @NotNull
    Promise<String> getPreference(@NotNull String key);
    
    /**
     * Gets all preferences in a namespace.
     * 
     * @param namespace Preference namespace
     * @return Promise of preferences map
     */
    @NotNull
    Promise<Map<String, String>> getPreferences(@NotNull String namespace);
    
    // ========== MEMORY MANAGEMENT ==========
    
    /**
     * Applies memory governance (redaction, retention).
     * Should be called periodically by background job.
     * 
     * @param policy Governance policy
     * @return Promise of operation result
     */
    @NotNull
    Promise<GovernanceResult> applyGovernance(@NotNull GovernancePolicy policy);
    
    /**
     * Clears all memory for this agent (dangerous!).
     * Used for testing and privacy compliance.
     * 
     * @return Promise of cleared memory count
     */
    @NotNull
    Promise<Integer> clearMemory();
    
    /**
     * Gets memory statistics.
     * 
     * @return Promise of memory stats
     */
    @NotNull
    Promise<MemoryStats> getStats();

    // =========================================================================
    // v2.1 — bridge to MemoryPlane
    // =========================================================================

    /**
     * Returns the underlying memory plane if this store is backed by one.
     * Default returns null — overridden by MemoryStoreAdapter.
     *
     * <p>The return type is {@code Object} to avoid a compile-time dependency
     * on the agent-memory module. Cast to {@code MemoryPlane} at the call site.
     *
     * @return Memory plane object, or null if not backed by one
     * @since 2.1.0
     */
    @Nullable
    default Object asMemoryPlane() {
        return null;
    }
}
