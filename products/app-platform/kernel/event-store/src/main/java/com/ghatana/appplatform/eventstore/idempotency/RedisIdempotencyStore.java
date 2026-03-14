package com.ghatana.appplatform.eventstore.idempotency;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Optional;

/**
 * Redis-backed idempotency store using SETNX (SET if Not eXists) semantics.
 * Preferred in production for low-latency lookups.
 *
 * <p>Key pattern: {@code idempotency:{tenantId}:{idempotencyKey}}
 * Value: the response hash. Expiry set via {@code SETEX}.
 *
 * @doc.type class
 * @doc.purpose Redis idempotency store adapter (STORY-K05-013)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private final JedisPool jedisPool;

    public RedisIdempotencyStore(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public boolean claim(String tenantId, String idempotencyKey, String responseHash, int ttlSeconds) {
        String redisKey = redisKey(tenantId, idempotencyKey);
        try (Jedis jedis = jedisPool.getResource()) {
            // SET key value EX ttl NX — atomic set-if-absent with TTL
            String result = jedis.set(redisKey, responseHash, new redis.clients.jedis.params.SetParams()
                .nx()
                .ex(ttlSeconds));
            return "OK".equals(result);
        }
    }

    @Override
    public Optional<String> getResponseHash(String tenantId, String idempotencyKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            return Optional.ofNullable(jedis.get(redisKey(tenantId, idempotencyKey)));
        }
    }

    private String redisKey(String tenantId, String idempotencyKey) {
        return "idempotency:" + tenantId + ":" + idempotencyKey;
    }
}
