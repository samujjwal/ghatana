/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.ingestion;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.Objects;

/**
 * T-09: Redis-backed {@link IdempotencyStore} for production environments.
 *
 * <p>Uses the Redis {@code SET key value NX PX millis} command to perform an
 * atomic check-and-set.  If the command returns {@code "OK"} the key was freshly
 * inserted (not a duplicate); if it returns {@code null} the key already existed.
 *
 * <p>Connection failures are caught and logged; on error the store optimistically
 * returns {@code false} (not a duplicate) so the ingestion path is not blocked by
 * a Redis outage.  This is a deliberate safety trade-off: a brief Redis outage
 * may allow a small number of duplicates through rather than halting all ingestion.
 *
 * @doc.type class
 * @doc.purpose Redis-backed idempotency store for production event deduplication
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class RedisIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyStore.class);
    private static final String KEY_PREFIX = "aep:idempotency:";
    private static final String SENTINEL_VALUE = "1";

    private final JedisPool jedisPool;

    /**
     * @param jedisPool the shared Jedis connection pool (required)
     */
    public RedisIdempotencyStore(JedisPool jedisPool) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool required");
    }

    @Override
    public Promise<Boolean> isDuplicate(String tenantId, String idempotencyKey, Duration ttl) {
        String redisKey = KEY_PREFIX + tenantId + ":" + idempotencyKey;
        long ttlMs = ttl.toMillis();

        try (var jedis = jedisPool.getResource()) {
            String result = jedis.set(redisKey, SENTINEL_VALUE,
                SetParams.setParams().nx().px(ttlMs));
            // "OK" means key was freshly set → not a duplicate
            // null means key already existed → duplicate
            boolean isDuplicate = result == null;
            return Promise.of(isDuplicate);
        } catch (Exception e) {
            log.error("[idempotency] Redis check failed for tenantId={} key={}: {} — allowing through",
                    tenantId, idempotencyKey, e.getMessage(), e);
            // Fail-open: allow the event through on Redis error rather than halting ingestion
            return Promise.of(false);
        }
    }
}
