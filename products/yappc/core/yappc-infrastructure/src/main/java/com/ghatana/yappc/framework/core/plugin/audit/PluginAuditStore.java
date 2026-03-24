/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Framework Module
 */
package com.ghatana.yappc.framework.core.plugin.audit;

import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory, tenant-scoped store for plugin invocation audit records.
 *
 * <p>Each write is a {@link Map}{@code <String, Object>} produced by
 * {@link PluginAuditInterceptor}. Records are keyed by {@code pluginId} and
 * sub-keyed by {@code tenantId} so tenants cannot read each other's audit trails.
 *
 * <p>Call {@link #auditSinkFor(String, String)} to obtain a
 * {@link Consumer}{@code <Map<String, Object>>} that can be passed directly to
 * {@link PluginAuditInterceptor#wrap}.
 *
 * @doc.type class
 * @doc.purpose In-memory tenant-scoped sink for plugin audit records
 * @doc.layer product
 * @doc.pattern Repository
 */
public class PluginAuditStore {

    /** pluginId → tenantId → audit records */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, CopyOnWriteArrayList<Map<String, Object>>>> store =
            new ConcurrentHashMap<>();

    /**
     * Returns a sink compatible with {@link PluginAuditInterceptor#wrap}.
     * The sink adds the supplied {@code tenantId} to every record and stores it.
     *
     * @param pluginId plugin identifier
     * @param tenantId tenant isolates records
     * @return audit event sink to pass to the interceptor
     */
    public Consumer<Map<String, Object>> auditSinkFor(String pluginId, String tenantId) {
        return record -> {
            record.put("tenantId", tenantId);
            storeOf(pluginId, tenantId).add(Collections.unmodifiableMap(new java.util.LinkedHashMap<>(record)));
        };
    }

    /**
     * Returns an ActiveJ {@link Promise} that resolves with all audit records for the given
     * plugin and tenant.
     *
     * @param pluginId plugin identifier
     * @param tenantId tenant to filter by
     * @return promise of immutable list of audit records
     */
    public Promise<List<Map<String, Object>>> getRecords(String pluginId, String tenantId) {
        CopyOnWriteArrayList<Map<String, Object>> records = storeOf(pluginId, tenantId);
        return Promise.of(List.copyOf(records));
    }

    /**
     * Total count of stored records for this plugin+tenant combination.
     * Useful for assertions in tests.
     *
     * @param pluginId plugin identifier
     * @param tenantId tenant identifier
     * @return number of stored records
     */
    public int count(String pluginId, String tenantId) {
        return storeOf(pluginId, tenantId).size();
    }

    private CopyOnWriteArrayList<Map<String, Object>> storeOf(String pluginId, String tenantId) {
        return store
                .computeIfAbsent(pluginId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>());
    }
}
