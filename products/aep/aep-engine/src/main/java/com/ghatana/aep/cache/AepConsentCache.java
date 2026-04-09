/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.cache;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.consent.ConsentService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TTL-bounded cache for consent decisions (AEP-023).
 *
 * <p>The cache key intentionally includes tenant, subject identity, event type,
 * version, headers, payload, and consent context to avoid serving stale or overly
 * broad decisions when consent evaluation logic changes or becomes more specific.
 *
 * @doc.type class
 * @doc.purpose Cache repeated consent evaluations with TTL-based eviction
 * @doc.layer product
 * @doc.pattern Cache
 */
public final class AepConsentCache {

    public static final String TTL_SECONDS_KEY = "consentCacheTtlSeconds";
    public static final String MAX_ENTRIES_KEY = "consentCacheMaxEntries";

    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final ConcurrentHashMap<ConsentKey, CacheEntry> entries = new ConcurrentHashMap<>();

    private AepConsentCache(Builder builder) {
        this.ttl = builder.ttl;
        this.maxEntries = builder.maxEntries;
        this.clock = builder.clock;
    }

    /**
     * Returns a cached consent decision if present and not expired.
     *
     * @param tenantId tenant identifier
     * @param event    event being evaluated
     * @return cached decision when available
     */
    public Optional<ConsentService.ConsentDecision> get(String tenantId, AepEngine.Event event) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(event, "event must not be null");

        purgeExpired();
        ConsentKey key = ConsentKey.from(tenantId, event);
        CacheEntry entry = entries.get(key);
        if (entry == null || entry.expiresAt().isBefore(clock.instant())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.decision());
    }

    /**
     * Stores a consent decision for subsequent matching events.
     *
     * @param tenantId tenant identifier
     * @param event    evaluated event
     * @param decision computed decision
     */
    public void put(String tenantId, AepEngine.Event event, ConsentService.ConsentDecision decision) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(decision, "decision must not be null");

        purgeExpired();
        if (entries.size() >= maxEntries) {
            evictOldest(entries.size() - maxEntries + 1);
        }
        entries.put(ConsentKey.from(tenantId, event),
            new CacheEntry(decision, clock.instant().plus(ttl), clock.instant()));
    }

    /**
     * Invalidates all cached decisions for a tenant.
     *
     * @param tenantId tenant identifier
     */
    public void invalidateTenant(String tenantId) {
        entries.keySet().removeIf(key -> key.tenantId().equals(tenantId));
    }

    /**
     * Clears the cache.
     */
    public void clear() {
        entries.clear();
    }

    /**
     * @return number of cached decisions currently held
     */
    public int size() {
        purgeExpired();
        return entries.size();
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private void evictOldest(int count) {
        List<Map.Entry<ConsentKey, CacheEntry>> candidates = new ArrayList<>(entries.entrySet());
        candidates.stream()
            .sorted(Comparator.comparing(entry -> entry.getValue().createdAt()))
            .limit(Math.max(0, count))
            .forEach(entry -> entries.remove(entry.getKey(), entry.getValue()));
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link AepConsentCache}.
     */
    public static final class Builder {
        private Duration ttl = Duration.ofMinutes(5);
        private int maxEntries = 10_000;
        private Clock clock = Clock.systemUTC();

        private Builder() {
        }

        public Builder ttl(Duration ttl) {
            this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
            return this;
        }

        public Builder maxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock must not be null");
            return this;
        }

        public AepConsentCache build() {
            if (ttl.isZero() || ttl.isNegative()) {
                throw new IllegalArgumentException("ttl must be positive");
            }
            if (maxEntries < 1) {
                throw new IllegalArgumentException("maxEntries must be >= 1");
            }
            return new AepConsentCache(this);
        }
    }

    private record ConsentKey(
        String tenantId,
        String eventType,
        String subjectId,
        String version,
        int payloadHash,
        int headersHash,
        AepEngine.ConsentStatus status,
        AepEngine.RetentionPolicy retentionPolicy,
        List<String> allowedPurposes
    ) {
        private static ConsentKey from(String tenantId, AepEngine.Event event) {
            String subjectId = event.identityContext().stitchedId()
                .or(() -> event.identityContext().userId())
                .or(() -> event.identityContext().anonymousId())
                .or(() -> event.identityContext().sessionId())
                .orElse("anonymous");
            return new ConsentKey(
                tenantId,
                event.type(),
                subjectId,
                event.version(),
                event.payload().hashCode(),
                event.headers().hashCode(),
                event.consentContext().status(),
                event.consentContext().retentionPolicy(),
                List.copyOf(event.consentContext().allowedPurposes())
            );
        }
    }

    private record CacheEntry(
        ConsentService.ConsentDecision decision,
        Instant expiresAt,
        Instant createdAt
    ) {
    }
}
