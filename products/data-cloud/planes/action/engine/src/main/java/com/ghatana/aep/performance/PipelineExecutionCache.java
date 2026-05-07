/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.performance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pipeline execution result cache for AEP (AEP-006.1).
 *
 * <p>Caches the deterministic output of pipeline executions keyed by a
 * composite cache key (tenantId + pipelineId + input fingerprint).  Idempotent
 * and deterministic pipelines benefit most; pipelines that produce side effects
 * should opt out via {@code cacheable = false} in their spec.
 *
 * <p>The cache uses a TTL-based eviction strategy and tracks hit/miss metrics
 * to allow operators to tune TTL and maxSize per workload.
 *
 * @doc.type    class
 * @doc.purpose Pipeline execution result cache for performance (AEP-006.1)
 * @doc.layer   product
 * @doc.pattern Cache
 */
public final class PipelineExecutionCache {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineExecutionCache.class);

    private final Duration ttl;
    private final int maxSize;
    private final Clock clock;

    private final ConcurrentHashMap<String, CacheEntry> store;
    private final AtomicLong hits   = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    private PipelineExecutionCache(Builder builder) {
        this.ttl     = builder.ttl;
        this.maxSize = builder.maxSize;
        this.clock   = builder.clock;
        this.store   = new ConcurrentHashMap<>(Math.min(maxSize, 1_024));
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Looks up a cached pipeline result.
     *
     * @param tenantId    tenant identifier
     * @param pipelineId  pipeline identifier
     * @param inputHash   fingerprint of the pipeline input (e.g., SHA-256 hex)
     * @return cached result bytes, or empty if not cached / expired
     */
    public Optional<byte[]> get(String tenantId, String pipelineId, String inputHash) {
        String key = cacheKey(tenantId, pipelineId, inputHash);
        Instant now = clock.instant();
        CacheEntry entry = store.get(key);

        if (entry != null && entry.expiresAt().isAfter(now)) {
            hits.incrementAndGet();
            LOG.trace("Pipeline cache HIT key={}", key);
            return Optional.of(entry.result());
        }

        misses.incrementAndGet();
        LOG.debug("Pipeline cache MISS key={}", key);
        return Optional.empty();
    }

    /**
     * Stores a pipeline execution result.
     *
     * @param tenantId   tenant identifier
     * @param pipelineId pipeline identifier
     * @param inputHash  fingerprint of the pipeline input
     * @param result     serialized pipeline result bytes (must not be null)
     */
    public void put(String tenantId, String pipelineId, String inputHash, byte[] result) {
        Objects.requireNonNull(result, "result must not be null");
        String key = cacheKey(tenantId, pipelineId, inputHash);
        ensureCapacity();
        store.put(key, new CacheEntry(result, clock.instant().plus(ttl)));
        LOG.trace("Pipeline cache STORE key={} size={}B", key, result.length);
    }

    /**
     * Invalidates all cached results for a given pipeline.
     *
     * @param tenantId   tenant identifier
     * @param pipelineId pipeline identifier
     * @return number of entries removed
     */
    public int invalidatePipeline(String tenantId, String pipelineId) {
        String prefix = tenantId + ":" + pipelineId + ":";
        long removed = store.keySet().removeIf(k -> k.startsWith(prefix)) ? 1 : 0;
        // More accurate count:
        removed = store.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .peek(e -> store.remove(e.getKey()))
                .count();
        evictions.addAndGet(removed);
        return (int) removed;
    }

    /** Clears the entire cache. */
    public void clear() {
        int size = store.size();
        store.clear();
        evictions.addAndGet(size);
        LOG.info("Pipeline execution cache cleared ({} entries)", size);
    }

    /** Returns accumulated cache statistics. */
    public Stats stats() {
        long h = hits.get(), m = misses.get();
        double hitRate = (h + m) == 0 ? 0.0 : (double) h / (h + m);
        return new Stats(h, m, evictions.get(), store.size(), hitRate);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static String cacheKey(String tenantId, String pipelineId, String inputHash) {
        return tenantId + ":" + pipelineId + ":" + inputHash;
    }

    private void ensureCapacity() {
        if (store.size() >= maxSize) {
            Instant now = clock.instant();
            store.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
            if (store.size() >= maxSize) {
                String toRemove = store.keySet().iterator().next();
                store.remove(toRemove);
                evictions.incrementAndGet();
            }
        }
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    private record CacheEntry(byte[] result, Instant expiresAt) {}

    /**
     * Cache performance statistics.
     *
     * @param hits      total cache hits
     * @param misses    total cache misses
     * @param evictions total evictions
     * @param size      current live entries
     * @param hitRate   ratio of hits to total lookups [0, 1]
     */
    public record Stats(long hits, long misses, long evictions, int size, double hitRate) {}

    // ── Builder ────────────────────────────────────────────────────────────────

    /** Returns a new builder for {@link PipelineExecutionCache}. */
    public static Builder builder() { return new Builder(); }

    /**
     * Builder for {@link PipelineExecutionCache}.
     */
    public static final class Builder {
        private Duration ttl  = Duration.ofMinutes(10);
        private int maxSize   = 2_000;
        private Clock clock   = Clock.systemUTC();

        private Builder() {}

        public Builder ttl(Duration ttl) {
            Objects.requireNonNull(ttl, "ttl must not be null");
            if (ttl.isNegative() || ttl.isZero())
                throw new IllegalArgumentException("ttl must be positive");
            this.ttl = ttl;
            return this;
        }

        public Builder maxSize(int size) {
            if (size <= 0) throw new IllegalArgumentException("maxSize must be positive");
            this.maxSize = size;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock must not be null");
            return this;
        }

        public PipelineExecutionCache build() { return new PipelineExecutionCache(this); }
    }
}
