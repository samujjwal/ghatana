/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.analytics;

import io.activej.promise.Promise;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * In-memory {@link EgressMonitor} with configurable per-agent byte limits.
 *
 * <p>Counters accumulate across the lifetime of this instance (no window expiry).
 * Production deployments should replace this with a sliding-window implementation
 * backed by Redis or a time-series store.
 *
 * @doc.type class
 * @doc.purpose In-memory egress monitor with configurable per-agent limits
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class DefaultEgressMonitor implements EgressMonitor {

    /** Default per-agent limit: 10 MB. */
    public static final long DEFAULT_LIMIT_BYTES = 10L * 1024 * 1024;

    private final long defaultLimitBytes;
    private final Map<String, Long> tenantLimits = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();

    /** Create a monitor using the default 10 MB per-agent limit. */
    public DefaultEgressMonitor() {
        this(DEFAULT_LIMIT_BYTES);
    }

    /**
     * Create a monitor with a custom default limit.
     *
     * @param defaultLimitBytes per-agent byte limit applied to all tenants by default
     */
    public DefaultEgressMonitor(long defaultLimitBytes) {
        this.defaultLimitBytes = defaultLimitBytes;
    }

    /**
     * Set a custom byte limit for a specific tenant.
     *
     * @param tenantId   the tenant to configure
     * @param limitBytes maximum bytes per agent per window
     */
    public void setTenantLimit(String tenantId, long limitBytes) {
        tenantLimits.put(tenantId, limitBytes);
    }

    @Override
    public Promise<Void> record(
            String tenantId, String agentId, String toolName, long bytesCount) {
        String key = tenantId + ":" + agentId;
        LongAdder adder = counters.computeIfAbsent(key, k -> new LongAdder());
        adder.add(bytesCount);

        long total = adder.sum();
        long limit = tenantLimits.getOrDefault(tenantId, defaultLimitBytes);
        if (total > limit) {
            return Promise.ofException(
                new EgressLimitExceededException(tenantId, agentId, limit, total));
        }
        return Promise.complete();
    }

    @Override
    public Promise<Long> currentWindowBytes(String tenantId, String agentId) {
        String key = tenantId + ":" + agentId;
        LongAdder adder = counters.get(key);
        return Promise.of(adder == null ? 0L : adder.sum());
    }
}
