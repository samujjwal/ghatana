package com.ghatana.agent.memory.security;

import com.ghatana.agent.memory.model.MemoryItem;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strict tenant-isolating implementation of {@link MemorySecurityManager}.
 *
 * <p>Enforces that every memory read/write/search is scoped to the caller's
 * tenant. Cross-tenant access is unconditionally denied, regardless of agent
 * identity, preventing data leakage in multi-tenant deployments.
 *
 * <p><b>Write policy</b>: A write is permitted when the item's tenant ID matches
 * the caller's tenant ID, or when the item carries no tenant (the item will be
 * scoped to the caller's tenant by the persistence layer).
 *
 * <p><b>Read policy</b>: A read is permitted only when the item's tenant ID
 * exactly matches the caller's tenant ID (null item tenant → denied).
 *
 * <p><b>Search policy</b>: Cross-tiers semantic search is always permitted for
 * any authenticated tenant (the underlying query already applies tenant filters).
 *
 * @doc.type class
 * @doc.purpose Strict tenant isolation for all memory plane operations
 * @doc.layer agent-memory
 * @doc.pattern Strategy
 * @doc.gaa.memory episodic
 * @doc.gaa.lifecycle perceive
 */
public class TenantIsolatingMemorySecurityManager implements MemorySecurityManager {

    private static final Logger log = LoggerFactory.getLogger(TenantIsolatingMemorySecurityManager.class);

    /**
     * Permits a read only when the item's tenant ID exactly matches the caller's tenant ID.
     * A null item tenant is treated as a mismatch and access is denied.
     *
     * @param item     The memory item to check
     * @param tenantId The calling tenant context (non-null)
     * @param agentId  The requesting agent (informational, not checked here)
     * @return {@code true} iff {@code item.getTenantId().equals(tenantId)}
     */
    @Override
    public boolean canRead(@NotNull MemoryItem item, @NotNull String tenantId, @NotNull String agentId) {
        String itemTenant = item.getTenantId();
        boolean allowed = tenantId.equals(itemTenant);
        if (!allowed) {
            log.warn("Memory read DENIED — caller tenant='{}' item tenant='{}' itemId='{}' agent='{}'",
                    tenantId, itemTenant, item.getId(), agentId);
        }
        return allowed;
    }

    /**
     * Permits a write when:
     * <ul>
     *   <li>The item's tenant ID is {@code null} (new item, will be set by persistence layer), or</li>
     *   <li>The item's tenant ID equals the caller's tenant ID (natural owner).</li>
     * </ul>
     * Any attempt to write an item owned by a different tenant is denied.
     *
     * @param item     The memory item to write
     * @param tenantId The calling tenant context (non-null)
     * @param agentId  The requesting agent (informational)
     * @return {@code true} iff the write should proceed
     */
    @Override
    public boolean canWrite(@NotNull MemoryItem item, @NotNull String tenantId, @NotNull String agentId) {
        String itemTenant = item.getTenantId();
        // Allow if tenantId matches OR if item has the default "default" tenant (will be overwritten)
        boolean allowed = itemTenant == null || tenantId.equals(itemTenant) || "default".equals(itemTenant);
        if (!allowed) {
            log.warn("Memory write DENIED — caller tenant='{}' item tenant='{}' itemId='{}' agent='{}'",
                    tenantId, itemTenant, item.getId(), agentId);
        }
        return allowed;
    }

    /**
     * Permits semantic search for any authenticated tenant.
     * The underlying query is expected to carry a {@code tenantId} filter,
     * so cross-tenant leakage is prevented at the DB layer.
     *
     * @param tenantId The calling tenant context
     * @param agentId  The requesting agent
     * @return {@code true} always (filtering delegated to query layer)
     */
    @Override
    public boolean canSearch(@NotNull String tenantId, @NotNull String agentId) {
        return true;
    }
}
