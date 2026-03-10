/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.memory;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.security.MemorySecurityManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * YAPPC implementation of {@link MemorySecurityManager} that enforces strict tenant
 * isolation on all memory read and write operations.
 *
 * <h2>Policy</h2>
 * <ul>
 *   <li><b>Read</b>: only allowed when {@code item.tenantId == callerTenantId}</li>
 *   <li><b>Write</b>: only allowed when {@code item.tenantId == callerTenantId}</li>
 *   <li><b>Search</b>: always allowed within a tenant scope (search is already tenant-filtered)</li>
 * </ul>
 *
 * <p>Cross-tenant reads are rejected with a warning log. This is the primary
 * defense against data leakage between YAPPC tenants.
 *
 * @doc.type class
 * @doc.purpose Enforces tenant isolation on all YAPPC memory operations (9.2.2)
 * @doc.layer product
 * @doc.pattern Security, Strategy
 * @doc.gaa.memory episodic|semantic|procedural|preference
 */
public class TenantIsolatedMemorySecurityManager implements MemorySecurityManager {

    private static final Logger log = LoggerFactory.getLogger(TenantIsolatedMemorySecurityManager.class);

    @Override
    public boolean canRead(
            @NotNull MemoryItem item,
            @NotNull String tenantId,
            @NotNull String agentId) {
        boolean allowed = tenantId.equals(item.getTenantId());
        if (!allowed) {
            log.warn("MEMORY_ACCESS_DENIED: agent '{}' in tenant '{}' attempted to READ item '{}' "
                    + "owned by tenant '{}'",
                    agentId, tenantId, item.getId(), item.getTenantId());
        }
        return allowed;
    }

    @Override
    public boolean canWrite(
            @NotNull MemoryItem item,
            @NotNull String tenantId,
            @NotNull String agentId) {
        boolean allowed = tenantId.equals(item.getTenantId());
        if (!allowed) {
            log.warn("MEMORY_ACCESS_DENIED: agent '{}' in tenant '{}' attempted to WRITE item '{}' "
                    + "owned by tenant '{}'",
                    agentId, tenantId, item.getId(), item.getTenantId());
        }
        return allowed;
    }

    @Override
    public boolean canSearch(
            @NotNull String tenantId,
            @NotNull String agentId) {
        // Search is always permitted — the query layer must filter by tenantId
        return true;
    }
}
