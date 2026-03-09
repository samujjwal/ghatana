package com.ghatana.agent.memory.observability;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Memory-specific metrics collector for the memory plane.
 * Tracks read/write counts, latencies, cache hit rates, and memory tier utilization.
 *
 * <p>Integrates with the platform's observability module (MetricsCollector).
 *
 * @doc.type class
 * @doc.purpose Memory plane metrics
 * @doc.layer agent-memory
 */
public class MemoryMetrics {

    private static final Logger log = LoggerFactory.getLogger(MemoryMetrics.class);

    private final AtomicLong readCount = new AtomicLong(0);
    private final AtomicLong writeCount = new AtomicLong(0);
    private final AtomicLong searchCount = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalReadLatencyNanos = new AtomicLong(0);
    private final AtomicLong totalWriteLatencyNanos = new AtomicLong(0);
    private final AtomicLong totalSearchLatencyNanos = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> tierWriteCounts = new ConcurrentHashMap<>();

    public void recordRead(long latencyNanos) {
        readCount.incrementAndGet();
        totalReadLatencyNanos.addAndGet(latencyNanos);
    }

    public void recordWrite(@NotNull String tier, long latencyNanos) {
        writeCount.incrementAndGet();
        totalWriteLatencyNanos.addAndGet(latencyNanos);
        tierWriteCounts.computeIfAbsent(tier, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordSearch(long latencyNanos) {
        searchCount.incrementAndGet();
        totalSearchLatencyNanos.addAndGet(latencyNanos);
    }

    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    public long getReadCount() {
        return readCount.get();
    }

    public long getWriteCount() {
        return writeCount.get();
    }

    public long getSearchCount() {
        return searchCount.get();
    }

    public double getAverageReadLatencyMs() {
        long count = readCount.get();
        return count > 0 ? (totalReadLatencyNanos.get() / (double) count) / 1_000_000.0 : 0.0;
    }

    public double getAverageWriteLatencyMs() {
        long count = writeCount.get();
        return count > 0 ? (totalWriteLatencyNanos.get() / (double) count) / 1_000_000.0 : 0.0;
    }

    public double getAverageSearchLatencyMs() {
        long count = searchCount.get();
        return count > 0 ? (totalSearchLatencyNanos.get() / (double) count) / 1_000_000.0 : 0.0;
    }

    public double getCacheHitRate() {
        long total = cacheHits.get() + cacheMisses.get();
        return total > 0 ? (double) cacheHits.get() / total : 0.0;
    }

    @NotNull
    public Map<String, Long> getTierWriteCounts() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        tierWriteCounts.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    /**
     * Exports metrics as a map suitable for the platform MetricsCollector.
     */
    @NotNull
    public Map<String, Object> toMap() {
        return Map.of(
                "memory.reads", readCount.get(),
                "memory.writes", writeCount.get(),
                "memory.searches", searchCount.get(),
                "memory.avg_read_latency_ms", getAverageReadLatencyMs(),
                "memory.avg_write_latency_ms", getAverageWriteLatencyMs(),
                "memory.avg_search_latency_ms", getAverageSearchLatencyMs(),
                "memory.cache_hit_rate", getCacheHitRate()
        );
    }

    /**
     * Resets all counters. Typically called after metrics export.
     */
    public void reset() {
        readCount.set(0);
        writeCount.set(0);
        searchCount.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        totalReadLatencyNanos.set(0);
        totalWriteLatencyNanos.set(0);
        totalSearchLatencyNanos.set(0);
        tierWriteCounts.clear();
    }
}
