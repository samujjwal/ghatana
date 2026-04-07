/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * General-purpose query result cache for AEP database operations (AEP-004.2).
 *
 * <p>Reduces database load for frequently-repeated queries (pattern lookups,
 * checkpoint reads, tenant config fetches) targeting a cache hit rate of &gt;80%.
 * Uses a time-based TTL eviction strategy backed by a lock-free
 * {@link ConcurrentHashMap}; stale entries are lazily evicted on access.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * AepQueryResultCache cache = AepQueryResultCache.builder()
 *     .ttl(Duration.ofSeconds(30))
 *     .maxSize(5_000)
 *     .build();
 *
 * String tenantConf = cache.get("tenant:acme:config",
 *         () -> db.loadTenantConfig("acme"));
 * }</pre>
 *
 * @param <V> cached value type
 * @doc.type    class
 * @doc.purpose TTL-based query result cache targeting &gt;80% hit rate
 * @doc.layer   product
 * @doc.pattern Cache
 */
public final class AepQueryResultCache<V> {

    private static final Logger LOG = LoggerFactory.getLogger(AepQueryResultCache.class);

    private final Duration ttl;
    private final int maxSize;
    private final Clock clock;

    private final ConcurrentHashMap<String, CacheEntry<V>> store;

    // Metrics
    private final AtomicLong hits   = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    private AepQueryResultCache(Builder<V> builder) {
        this.ttl     = builder.ttl;
        this.maxSize = builder.maxSize;
        this.clock   = builder.clock;
        this.store   = new ConcurrentHashMap<>(Math.min(maxSize, 1_024));
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns a cached value or loads one from {@code loader}.
     *
     * @param key    cache key (must not be null)
     * @param loader called on cache miss to produce the canonical value
     * @return cached or freshly-loaded value
     */
    public V get(String key, Supplier<V> loader) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(loader, "loader must not be null");

        Instant now = clock.instant();
        CacheEntry<V> entry = store.get(key);

        if (entry != null && entry.expiresAt().isAfter(now)) {
            hits.incrementAndGet();
            LOG.trace("Cache hit key={}", key);
            return entry.value();
        }

        // Miss – load and cache
        misses.incrementAndGet();
        V loaded = loader.get();
        if (loaded != null) {
            ensureCapacity();
            store.put(key, new CacheEntry<>(loaded, now.plus(ttl)));
        }
        LOG.debug("Cache miss key={}", key);
        return loaded;
    }

    /**
     * Returns a cached value without loading on a miss.
     *
     * @param key cache key
     * @return cached value if present and not expired, otherwise empty
     */
    public Optional<V> peek(String key) {
        Objects.requireNonNull(key, "key must not be null");
        Instant now = clock.instant();
        CacheEntry<V> entry = store.get(key);
        if (entry != null && entry.expiresAt().isAfter(now)) {
            hits.incrementAndGet();
            return Optional.of(entry.value());
        }
        return Optional.empty();
    }

    /**
     * Explicitly places a value in the cache with the configured TTL.
     *
     * @param key   cache key
     * @param value value to store (must not be null)
     */
    public void put(String key, V value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        ensureCapacity();
        store.put(key, new CacheEntry<>(value, clock.instant().plus(ttl)));
    }

    /**
     * Invalidates a single cache entry.
     *
     * @param key cache key to remove
     */
    public void invalidate(String key) {
        Objects.requireNonNull(key, "key must not be null");
        store.remove(key);
        LOG.debug("Invalidated cache key={}", key);
    }

    /**
     * Invalidates all entries whose keys start with the given prefix.
     *
     * @param prefix key prefix
     * @return number of entries removed
     */
    public int invalidateByPrefix(String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        long removed = store.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .peek(e -> store.remove(e.getKey()))
                .count();
        evictions.addAndGet(removed);
        LOG.debug("Invalidated {} entries with prefix={}", removed, prefix);
        return (int) removed;
    }

    /** Removes all entries. */
    public void clear() {
        int size = store.size();
        store.clear();
        evictions.addAndGet(size);
        LOG.info("Cache cleared ({} entries removed)", size);
    }

    // ── Statistics ─────────────────────────────────────────────────────────────

    /** Returns accumulated cache statistics. */
    public Stats stats() {
        long h = hits.get();
        long m = misses.get();
        long total = h + m;
        double hitRate = total == 0 ? 0.0 : (double) h / total;
        return new Stats(h, m, evictions.get(), store.size(), hitRate);
    }

    // ── Internal helpers ────────────────────────────────────────────────────────

    private void ensureCapacity() {
        if (store.size() >= maxSize) {
            Instant now = clock.instant();
            long removed = store.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now))
                    ? 0 : 0;
            // If still over limit, remove arbitrary entries
            if (store.size() >= maxSize) {
                int toRemove = store.size() - maxSize + 1;
                store.entrySet().stream()
                        .limit(toRemove)
                        .forEach(e -> store.remove(e.getKey()));
                evictions.addAndGet(toRemove);
            }
            evictions.addAndGet(removed);
        }
    }

    // ── Nested types ────────────────────────────────────────────────────────────

    private record CacheEntry<V>(V value, Instant expiresAt) {}

    /**
     * Immutable cache statistics snapshot.
     *
     * @param hits     total cache hits
     * @param misses   total cache misses
     * @param evictions total evictions
     * @param size     current number of live entries
     * @param hitRate  ratio of hits to total accesses [0, 1]
     */
    public record Stats(long hits, long misses, long evictions, int size, double hitRate) {
        /** Returns whether the hit rate meets the AEP-004.2 target of &gt;80%. */
        public boolean meetsTarget() {
            return hitRate >= 0.80;
        }
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    /**
     * Returns a new builder for {@link AepQueryResultCache}.
     *
     * @param <V> cached value type
     */
    public static <V> Builder<V> builder() {
        return new Builder<>();
    }

    /**
     * Builder for {@link AepQueryResultCache}.
     *
     * @param <V> cached value type
     */
    public static final class Builder<V> {
        private Duration ttl    = Duration.ofSeconds(30);
        private int maxSize     = 5_000;
        private Clock clock     = Clock.systemUTC();

        private Builder() {}

        /**
         * Cache entry time-to-live (must be positive).
         *
         * @param ttl TTL duration
         * @return this builder
         */
        public Builder<V> ttl(Duration ttl) {
            Objects.requireNonNull(ttl, "ttl must not be null");
            if (ttl.isNegative() || ttl.isZero()) {
                throw new IllegalArgumentException("ttl must be positive");
            }
            this.ttl = ttl;
            return this;
        }

        /**
         * Maximum number of cache entries before eviction.
         *
         * @param maxSize positive integer
         * @return this builder
         */
        public Builder<V> maxSize(int maxSize) {
            if (maxSize <= 0) {
                throw new IllegalArgumentException("maxSize must be positive");
            }
            this.maxSize = maxSize;
            return this;
        }

        /**
         * Clock used for TTL evaluation (injectable for tests).
         *
         * @param clock clock instance
         * @return this builder
         */
        public Builder<V> clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock must not be null");
            return this;
        }

        /** Builds the configured cache. */
        public AepQueryResultCache<V> build() {
            return new AepQueryResultCache<>(this);
        }
    }
}

