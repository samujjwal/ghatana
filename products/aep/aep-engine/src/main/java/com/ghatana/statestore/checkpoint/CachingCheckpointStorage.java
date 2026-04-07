/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.statestore.checkpoint;

import io.activej.promise.Promise;
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
 * Caching decorator for {@link CheckpointStorage} (AEP-004.3).
 *
 * <p>Wraps a delegate {@link CheckpointStorage} and serves read requests from an
 * in-memory cache, targeting checkpoint operation latency &lt;50ms.  Writes are
 * always written-through to the delegate; the local cache is updated on save and
 * invalidated on delete so consistency is maintained without requiring explicit cache
 * warming.
 *
 * <h3>Latency model</h3>
 * <ul>
 *   <li>Cache HIT:  &lt;1ms (concurrent hash map lookup).</li>
 *   <li>Cache MISS: delegate latency + ~1ms overhead (write to cache after load).</li>
 * </ul>
 *
 * @doc.type    class
 * @doc.purpose Caching decorator for CheckpointStorage targeting &lt;50ms operations
 * @doc.layer   product
 * @doc.pattern Decorator, Cache
 */
public final class CachingCheckpointStorage implements CheckpointStorage {

    private static final Logger LOG = LoggerFactory.getLogger(CachingCheckpointStorage.class);

    private final CheckpointStorage delegate;
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;

    private final ConcurrentHashMap<String, CacheEntry> cache;

    // Metrics
    private final AtomicLong hits   = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    private CachingCheckpointStorage(Builder builder) {
        this.delegate   = builder.delegate;
        this.ttl        = builder.ttl;
        this.maxEntries = builder.maxEntries;
        this.clock      = builder.clock;
        this.cache      = new ConcurrentHashMap<>(Math.min(maxEntries, 512));
    }

    // ── CheckpointStorage ─────────────────────────────────────────────────────

    @Override
    public Promise<CheckpointMetadata> saveCheckpoint(CheckpointMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        return delegate.saveCheckpoint(metadata)
                .map(saved -> { cacheEntry(saved); return saved; });
    }

    @Override
    public Promise<CheckpointMetadata> saveSavepoint(CheckpointMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        return delegate.saveSavepoint(metadata)
                .map(saved -> { cacheEntry(saved); return saved; });
    }

    @Override
    public Promise<CheckpointMetadata> loadCheckpoint(CheckpointId checkpointId) {
        Objects.requireNonNull(checkpointId, "checkpointId cannot be null");

        Optional<CheckpointMetadata> cached = fromCache(checkpointId);
        if (cached.isPresent()) {
            hits.incrementAndGet();
            LOG.trace("Checkpoint cache HIT id={}", checkpointId.getId());
            return Promise.of(cached.get());
        }

        misses.incrementAndGet();
        LOG.debug("Checkpoint cache MISS id={}", checkpointId.getId());
        return delegate.loadCheckpoint(checkpointId)
                .map(loaded -> { if (loaded != null) cacheEntry(loaded); return loaded; });
    }

    @Override
    public Promise<Void> deleteCheckpoint(CheckpointId checkpointId) {
        Objects.requireNonNull(checkpointId, "checkpointId cannot be null");
        cache.remove(checkpointId.getId());
        return delegate.deleteCheckpoint(checkpointId);
    }

    // ── Cache helpers ─────────────────────────────────────────────────────────

    private void cacheEntry(CheckpointMetadata metadata) {
        if (cache.size() >= maxEntries) {
            // Simple eviction: remove oldest
            cache.entrySet().stream()
                    .min(java.util.Comparator.comparing(e -> e.getValue().cachedAt()))
                    .ifPresent(e -> cache.remove(e.getKey()));
        }
        cache.put(metadata.getCheckpointId().getId(),
                new CacheEntry(metadata, clock.instant().plus(ttl)));
    }

    private Optional<CheckpointMetadata> fromCache(CheckpointId id) {
        CacheEntry entry = cache.get(id.getId());
        if (entry == null) return Optional.empty();
        if (entry.expiresAt().isBefore(clock.instant())) {
            cache.remove(id.getId());
            return Optional.empty();
        }
        return Optional.of(entry.metadata());
    }

    /** Returns cumulative cache statistics. */
    public CacheStats stats() {
        long h = hits.get();
        long m = misses.get();
        long total = h + m;
        double hitRate = total == 0 ? 0.0 : (double) h / total;
        return new CacheStats(h, m, cache.size(), hitRate);
    }

    /** Clears the cache (does not affect the delegate store). */
    public void clear() {
        cache.clear();
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    private record CacheEntry(CheckpointMetadata metadata, Instant expiresAt) {
        Instant cachedAt() { return expiresAt; } // proxy for ordering
    }

    /**
     * Cache performance statistics.
     *
     * @param hits     total cache hits
     * @param misses   total cache misses
     * @param size     current live entries in cache
     * @param hitRate  ratio of hits to total reads [0, 1]
     */
    public record CacheStats(long hits, long misses, int size, double hitRate) {}

    // ── Builder ────────────────────────────────────────────────────────────────

    /**
     * Returns a new builder for {@link CachingCheckpointStorage}.
     *
     * @param delegate the checkpoint storage to decorate
     */
    public static Builder builder(CheckpointStorage delegate) {
        return new Builder(delegate);
    }

    /**
     * Builder for {@link CachingCheckpointStorage}.
     */
    public static final class Builder {
        private final CheckpointStorage delegate;
        private Duration ttl    = Duration.ofMinutes(5);
        private int maxEntries  = 1_000;
        private Clock clock     = Clock.systemUTC();

        private Builder(CheckpointStorage delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        }

        /**
         * Entry time-to-live; must be positive.
         *
         * @param ttl TTL duration
         * @return this builder
         */
        public Builder ttl(Duration ttl) {
            Objects.requireNonNull(ttl, "ttl must not be null");
            if (ttl.isNegative() || ttl.isZero()) {
                throw new IllegalArgumentException("ttl must be positive");
            }
            this.ttl = ttl;
            return this;
        }

        /**
         * Maximum number of in-memory entries.
         *
         * @param maxEntries positive integer
         * @return this builder
         */
        public Builder maxEntries(int maxEntries) {
            if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
            this.maxEntries = maxEntries;
            return this;
        }

        /**
         * Clock used for TTL evaluation.
         *
         * @param clock clock instance (injectable for tests)
         * @return this builder
         */
        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock must not be null");
            return this;
        }

        /** Builds the configured storage. */
        public CachingCheckpointStorage build() {
            return new CachingCheckpointStorage(this);
        }
    }
}

