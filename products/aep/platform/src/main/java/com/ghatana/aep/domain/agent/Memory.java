package com.ghatana.aep.domain.agent;

import java.util.Map;

/**
 * Canonical Memory/State representation used across the platform.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a persistent, multi-level memory abstraction for agents and
 * workflows. Memory can be short-term (execution cache), medium-term (workflow
 * state), or long-term (historical data). Supports key-value storage with
 * optional TTL and versioning.
 *
 * <p>
 * <b>Usage</b><br>
 * Memory is typically used to: - Store workflow/agent state during execution -
 * Share context between agents - Cache computation results - Maintain audit
 * trail of decisions
 *
 * Product-specific implementations extend this interface for domain-specific
 * memory models (e.g., VirtualOrgMemory for org state, SoftwareOrgMemory for
 * dev state).
 *
 * <p>
 * <b>Architecture Role</b><br>
 * - Part of agent runtime: agents read/write state to memory - Cross-cutting:
 * workflows and multiple agents may share memory - Versioned: supports rollback
 * and audit - Multi-level: local cache, distributed state, archived history
 *
 * @doc.type interface
 * @doc.layer domain
 * @doc.purpose canonical memory/state contract for agent and workflow state
 * management
 * @doc.pattern facade (provides stable interface over various storage backends)
 *
 * @see com.ghatana.agent.Agent (agents read/write memory)
 */
public interface Memory {

    /**
     * Unique identifier for this memory instance (e.g., agent ID, execution ID,
     * or tenant ID).
     */
    String getId();

    /**
     * Store a value with optional TTL.
     *
     * @param key the key
     * @param value the value
     * @param ttlMs time-to-live in milliseconds (0 or negative = no expiration)
     */
    void put(String key, Object value, long ttlMs);

    /**
     * Store a value without expiration.
     */
    default void put(String key, Object value) {
        put(key, value, 0);
    }

    /**
     * Retrieve a value.
     */
    Object get(String key);

    /**
     * Retrieve a value with a default if not found.
     */
    default Object getOrDefault(String key, Object defaultValue) {
        Object value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Remove a value.
     */
    void remove(String key);

    /**
     * Check if a key exists.
     */
    boolean containsKey(String key);

    /**
     * Get all keys (filtered by optional prefix).
     */
    java.util.Set<String> keys(String prefix);

    /**
     * Get all key-value pairs (snapshot).
     */
    Map<String, Object> snapshot();

    /**
     * Clear all entries.
     */
    void clear();

    /**
     * Get current size (number of key-value pairs).
     */
    int size();

    /**
     * Save current state to persistent storage (if supported).
     */
    void persist();

    /**
     * Restore state from persistent storage (if supported).
     */
    void restore();

    /**
     * Create a versioned checkpoint for rollback/audit.
     *
     * @return checkpoint ID
     */
    String checkpoint();

    /**
     * Rollback to a previous checkpoint.
     */
    void rollback(String checkpointId);

    /**
     * Get list of available checkpoints.
     */
    java.util.List<String> listCheckpoints();
}
