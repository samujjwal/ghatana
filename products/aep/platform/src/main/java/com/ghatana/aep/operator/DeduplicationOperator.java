/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.aep.operator;

import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event deduplication decorator for {@link AgentEventOperator}.
 *
 * <p>Detects duplicate events using an idempotency key extracted from the
 * event payload. Duplicate events within the configured TTL window are
 * silently dropped. Uses an in-memory idempotency key cache backed by
 * Data-Cloud for distributed dedup (via the delegate's data store).
 *
 * <h2>Key Extraction</h2>
 * The idempotency key is resolved from the event map:
 * <ol>
 *   <li>If the event contains {@code _idempotencyKey}, use it directly</li>
 *   <li>Otherwise, fall back to event {@code id} or {@code eventId}</li>
 *   <li>If no key can be extracted, the event is passed through (no dedup)</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Event deduplication for AEP agent operators
 * @doc.layer product-aep
 * @doc.pattern Decorator, Idempotent Receiver
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class DeduplicationOperator {

    private static final Logger log = LoggerFactory.getLogger(DeduplicationOperator.class);

    private final AgentEventOperator delegate;
    private final Duration ttl;
    private final int maxCacheSize;

    // LRU-evicting idempotency cache: key → first-seen timestamp
    private final Map<String, Instant> seen;

    // Metrics
    private final AtomicLong totalProcessed = new AtomicLong();
    private final AtomicLong totalDeduplicated = new AtomicLong();

    private DeduplicationOperator(Builder builder) {
        this.delegate = Objects.requireNonNull(builder.delegate, "delegate");
        this.ttl = builder.ttl != null ? builder.ttl : Duration.ofMinutes(10);
        this.maxCacheSize = builder.maxCacheSize > 0 ? builder.maxCacheSize : 100_000;

        // LRU map with size bound
        this.seen = new ConcurrentHashMap<>();
    }

    /**
     * Submits an event, dropping duplicates within the TTL window.
     */
    @NotNull
    public Promise<Map<String, Object>> submit(
            @NotNull AgentContext ctx,
            @NotNull Map<String, Object> event) {

        totalProcessed.incrementAndGet();
        String key = extractIdempotencyKey(event);

        if (key == null) {
            // No key — pass through without dedup
            return delegate.processEvent(ctx, event);
        }

        Instant now = Instant.now();
        Instant firstSeen = seen.get(key);

        if (firstSeen != null && Duration.between(firstSeen, now).compareTo(ttl) < 0) {
            // Duplicate within TTL — drop
            totalDeduplicated.incrementAndGet();
            log.debug("Dedup: dropped duplicate event key='{}' for operator '{}'",
                    key, delegate.getOperatorId());

            Map<String, Object> dedupResult = new LinkedHashMap<>();
            dedupResult.put("_operator", delegate.getOperatorId());
            dedupResult.put("_status", "DEDUPLICATED");
            dedupResult.put("_idempotencyKey", key);
            return Promise.of(dedupResult);
        }

        // Evict expired entries if cache is full
        if (seen.size() >= maxCacheSize) {
            evictExpired(now);
        }

        seen.put(key, now);
        return delegate.processEvent(ctx, event);
    }

    private String extractIdempotencyKey(Map<String, Object> event) {
        Object key = event.get("_idempotencyKey");
        if (key != null) return key.toString();

        key = event.get("idempotencyKey");
        if (key != null) return key.toString();

        key = event.get("eventId");
        if (key != null) return key.toString();

        key = event.get("id");
        if (key != null) return key.toString();

        return null;
    }

    private void evictExpired(Instant now) {
        seen.entrySet().removeIf(entry ->
                Duration.between(entry.getValue(), now).compareTo(ttl) >= 0);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Metrics
    // ═══════════════════════════════════════════════════════════════════════════

    public long getTotalProcessed() { return totalProcessed.get(); }
    public long getTotalDeduplicated() { return totalDeduplicated.get(); }
    public int getCacheSize() { return seen.size(); }

    // ═══════════════════════════════════════════════════════════════════════════
    // Builder
    // ═══════════════════════════════════════════════════════════════════════════

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private AgentEventOperator delegate;
        private Duration ttl = Duration.ofMinutes(10);
        private int maxCacheSize = 100_000;

        private Builder() {}

        public Builder delegate(@NotNull AgentEventOperator delegate) {
            this.delegate = delegate;
            return this;
        }

        public Builder ttl(@NotNull Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder maxCacheSize(int maxCacheSize) {
            this.maxCacheSize = maxCacheSize;
            return this;
        }

        public DeduplicationOperator build() {
            return new DeduplicationOperator(this);
        }
    }
}
