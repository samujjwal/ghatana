/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.ingestion;

import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T-09: In-memory {@link IdempotencyStore} for development and test environments.
 *
 * <p>Keys are stored with their expiry timestamp and purged lazily on each
 * {@link #isDuplicate} call. The store is bounded to {@value #MAX_ENTRIES}
 * entries to prevent unbounded growth in long-running dev processes; oldest
 * entries are evicted when the limit is reached.
 *
 * <p><strong>Not suitable for production</strong> — state is lost on restart.
 * Use {@link RedisIdempotencyStore} in production.
 *
 * @doc.type class
 * @doc.purpose In-memory idempotency store for dev/test; not durable
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final int MAX_ENTRIES = 100_000;

    private final Map<String, Instant> store = new LinkedHashMap<>(256, 0.75f, false);

    @Override
    public Promise<Boolean> isDuplicate(String tenantId, String idempotencyKey, Duration ttl) {
        String compositeKey = tenantId + "::" + idempotencyKey;
        Instant now = Instant.now();

        synchronized (store) {
            purgeExpired(now);

            if (store.containsKey(compositeKey)) {
                Instant expiry = store.get(compositeKey);
                if (expiry != null && expiry.isAfter(now)) {
                    return Promise.of(true);
                }
            }

            if (store.size() >= MAX_ENTRIES) {
                Iterator<String> oldest = store.keySet().iterator();
                if (oldest.hasNext()) {
                    oldest.next();
                    oldest.remove();
                }
            }

            store.put(compositeKey, now.plus(ttl));
            return Promise.of(false);
        }
    }

    private void purgeExpired(Instant now) {
        store.entrySet().removeIf(e -> e.getValue() != null && !e.getValue().isAfter(now));
    }
}
