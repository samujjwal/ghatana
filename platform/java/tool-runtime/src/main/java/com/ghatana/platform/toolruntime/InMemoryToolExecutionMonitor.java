/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime;

import io.activej.promise.Promise;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * In-memory {@link ToolExecutionMonitor} using atomic counters.
 *
 * <p>Suitable for single-node deployments and tests. Production deployments should
 * delegate to Micrometer metrics via {@code libs:observability}.
 *
 * @doc.type class
 * @doc.purpose In-memory tool execution statistics tracker
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class InMemoryToolExecutionMonitor implements ToolExecutionMonitor {

    private record Key(String tenantId, String toolName) {}

    private static final class Accumulator {
        final LongAdder total         = new LongAdder();
        final LongAdder success       = new LongAdder();
        final LongAdder failure       = new LongAdder();
        final LongAdder totalDurationMs = new LongAdder();
    }

    private final Map<Key, Accumulator> accumulators = new ConcurrentHashMap<>();

    @Override
    public Promise<Void> record(
            String tenantId, String agentId, String toolName,
            Duration duration, long outputBytes, boolean success) {
        Accumulator acc = accumulators.computeIfAbsent(new Key(tenantId, toolName), k -> new Accumulator());
        acc.total.increment();
        acc.totalDurationMs.add(duration.toMillis());
        if (success) acc.success.increment(); else acc.failure.increment();
        return Promise.complete();
    }

    @Override
    public Promise<ToolExecutionStats> getStats(String tenantId, String toolName) {
        Accumulator acc = accumulators.get(new Key(tenantId, toolName));
        if (acc == null) {
            return Promise.of(new ToolExecutionStats(tenantId, toolName, 0, 0, 0, 0.0));
        }
        long total = acc.total.sum();
        double avg = total == 0 ? 0.0 : (double) acc.totalDurationMs.sum() / total;
        return Promise.of(new ToolExecutionStats(
            tenantId, toolName, total, acc.success.sum(), acc.failure.sum(), avg));
    }
}
