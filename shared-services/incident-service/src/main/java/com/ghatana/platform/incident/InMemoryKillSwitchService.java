/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.incident;

import io.activej.promise.Promise;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory {@link KillSwitchService} backed by a {@link ConcurrentHashMap}.
 *
 * <p>State is lost on restart; this class is intended for single-node deployments
 * and tests. Production deployments should persist switch state to a distributed
 * store (Redis SETEX / database row) for durability and cluster-wide visibility.
 *
 * @doc.type class
 * @doc.purpose In-memory kill-switch with per-tenant and global controls
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class InMemoryKillSwitchService implements KillSwitchService {

    private final Set<String> activeTenants = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean globalSwitch = new AtomicBoolean(false);

    @Override
    public Promise<Void> activate(String tenantId, String reason, String incidentId) {
        activeTenants.add(tenantId);
        return Promise.complete();
    }

    @Override
    public Promise<Void> deactivate(String tenantId, String reason) {
        activeTenants.remove(tenantId);
        return Promise.complete();
    }

    @Override
    public Promise<Boolean> isActive(String tenantId) {
        return Promise.of(globalSwitch.get() || activeTenants.contains(tenantId));
    }

    @Override
    public Promise<Void> activateGlobal(String reason, String incidentId) {
        globalSwitch.set(true);
        return Promise.complete();
    }

    @Override
    public Promise<Boolean> isGlobalActive() {
        return Promise.of(globalSwitch.get());
    }

    /** Resets all state — useful in tests. */
    public void reset() {
        activeTenants.clear();
        globalSwitch.set(false);
    }
}
