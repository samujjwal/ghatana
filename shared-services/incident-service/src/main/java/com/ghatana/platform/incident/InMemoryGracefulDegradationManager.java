/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.incident;

import io.activej.promise.Promise;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link GracefulDegradationManager}.
 *
 * <p>Action-type permissions per degradation mode:
 * <ul>
 *   <li>{@code FULL} — all actions permitted.</li>
 *   <li>{@code READ_ONLY} — only "READ", "QUERY" permitted.</li>
 *   <li>{@code NOTIFICATIONS_ONLY} — only "NOTIFY" permitted.</li>
 *   <li>{@code OFFLINE} — no actions permitted.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose In-memory graceful-degradation manager
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class InMemoryGracefulDegradationManager implements GracefulDegradationManager {

    private static final Map<DegradationMode, Set<String>> ALLOWED_ACTIONS = Map.of(
        DegradationMode.FULL,               Set.of(), // empty = all allowed
        DegradationMode.READ_ONLY,          Set.of("READ", "QUERY", "LIST", "GET"),
        DegradationMode.NOTIFICATIONS_ONLY, Set.of("NOTIFY"),
        DegradationMode.OFFLINE,            Set.of()  // empty under OFFLINE = none allowed
    );

    private final Map<String, DegradationMode> tenantModes = new ConcurrentHashMap<>();

    @Override
    public Promise<Void> setMode(String tenantId, DegradationMode mode) {
        if (mode == DegradationMode.FULL) {
            tenantModes.remove(tenantId);
        } else {
            tenantModes.put(tenantId, mode);
        }
        return Promise.complete();
    }

    @Override
    public Promise<DegradationMode> getMode(String tenantId) {
        return Promise.of(tenantModes.getOrDefault(tenantId, DegradationMode.FULL));
    }

    @Override
    public Promise<Boolean> isActionAllowed(String tenantId, String actionType) {
        DegradationMode mode = tenantModes.getOrDefault(tenantId, DegradationMode.FULL);
        boolean allowed = switch (mode) {
            case FULL -> true;
            case OFFLINE -> false;
            default -> ALLOWED_ACTIONS.get(mode).contains(actionType.toUpperCase());
        };
        return Promise.of(allowed);
    }
}
