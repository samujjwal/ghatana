package com.ghatana.agent.memory.security;

import com.ghatana.agent.memory.model.MemoryItem;
import org.jetbrains.annotations.NotNull;

/**
 * Controls access to memory operations based on data classification,
 * tenant isolation, and user permissions.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Tenant isolation — cannot read/write another tenant's memory</li>
 *   <li>Data classification — PII/PHI access requires elevated permissions</li>
 *   <li>Agent scope — agents can only access their own memory (unless shared)</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Memory access control
 * @doc.layer agent-memory
 */
public interface MemorySecurityManager {

    /**
     * Checks if the current context is authorized to read the given item.
     *
     * @param item The memory item to check
     * @param tenantId The tenant context
     * @param agentId The requesting agent
     * @return true if access is allowed
     */
    boolean canRead(@NotNull MemoryItem item, @NotNull String tenantId, @NotNull String agentId);

    /**
     * Checks if the current context is authorized to write the given item.
     *
     * @param item The memory item to write
     * @param tenantId The tenant context
     * @param agentId The requesting agent
     * @return true if write is allowed
     */
    boolean canWrite(@NotNull MemoryItem item, @NotNull String tenantId, @NotNull String agentId);

    /**
     * Checks if the current context can perform semantic search across tiers.
     *
     * @param tenantId The tenant context
     * @param agentId The requesting agent
     * @return true if search is allowed
     */
    boolean canSearch(@NotNull String tenantId, @NotNull String agentId);
}
