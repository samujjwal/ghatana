/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.cache;

import com.ghatana.aep.AepEngine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Small tenant-scoped cache for resolved pattern snapshots (AEP-023).
 *
 * <p>The underlying registry remains the source of truth. This cache stores an
 * immutable list snapshot per tenant so the processing loop can avoid repeated
 * view construction under sustained event load.
 *
 * @doc.type class
 * @doc.purpose Cache immutable tenant pattern snapshots for event processing
 * @doc.layer product
 * @doc.pattern Cache
 */
public final class AepPatternCache {

    public static final String TTL_SECONDS_KEY = "patternCacheTtlSeconds";

    private final Duration ttl;
    private final Clock clock;
    private final ConcurrentHashMap<String, CacheEntry> snapshots = new ConcurrentHashMap<>();

    private AepPatternCache(Builder builder) {
        this.ttl = builder.ttl;
        this.clock = builder.clock;
    }

    /**
     * Returns a cached snapshot or loads and caches a fresh one.
     *
     * @param tenantId tenant identifier
     * @param loader   source-of-truth loader
     * @return immutable pattern snapshot
     */
    public List<AepEngine.Pattern> get(String tenantId, Supplier<List<AepEngine.Pattern>> loader) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(loader, "loader must not be null");

        Instant now = clock.instant();
        CacheEntry cached = snapshots.get(tenantId);
        if (cached != null && !cached.expiresAt().isBefore(now)) {
            return cached.patterns();
        }

        List<AepEngine.Pattern> loaded = List.copyOf(loader.get());
        snapshots.put(tenantId, new CacheEntry(loaded, now.plus(ttl)));
        return loaded;
    }

    /**
     * Replaces the snapshot for the tenant.
     *
     * @param tenantId tenant identifier
     * @param patterns pattern collection to cache
     */
    public void put(String tenantId, Collection<AepEngine.Pattern> patterns) {
        snapshots.put(tenantId, new CacheEntry(List.copyOf(patterns), clock.instant().plus(ttl)));
    }

    /**
     * Invalidates the tenant's cached pattern snapshot.
     *
     * @param tenantId tenant identifier
     */
    public void invalidateTenant(String tenantId) {
        snapshots.remove(tenantId);
    }

    /**
     * Clears all cached snapshots.
     */
    public void clear() {
        snapshots.clear();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link AepPatternCache}.
     */
    public static final class Builder {
        private Duration ttl = Duration.ofSeconds(30);
        private Clock clock = Clock.systemUTC();

        private Builder() {
        }

        public Builder ttl(Duration ttl) {
            this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock must not be null");
            return this;
        }

        public AepPatternCache build() {
            if (ttl.isNegative() || ttl.isZero()) {
                throw new IllegalArgumentException("ttl must be positive");
            }
            return new AepPatternCache(this);
        }
    }

    private record CacheEntry(List<AepEngine.Pattern> patterns, Instant expiresAt) {
    }
}
