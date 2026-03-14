package com.ghatana.appplatform.gateway.ratelimit;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;

/**
 * Redis-backed token bucket rate limit store using a Lua script for atomicity.
 *
 * <p>The bucket state is stored as two Redis keys:
 * <ul>
 *   <li>{@code rl:{bucketKey}:tokens} — current token count (float string)
 *   <li>{@code rl:{bucketKey}:last_refill} — epoch millis of last refill
 * </ul>
 *
 * <p>A single Lua script performs the refill-and-consume atomically to avoid
 * race conditions without distributed locks.
 *
 * @doc.type class
 * @doc.purpose Redis token bucket rate limit store adapter (STORY-K11)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class RedisRateLimitStore implements RateLimitStore {

    private final JedisPool jedisPool;

    // Lua script: refill tokens proportional to elapsed time, then consume
    private static final String CONSUME_SCRIPT = """
        local tokens_key   = KEYS[1]
        local last_key     = KEYS[2]
        local capacity     = tonumber(ARGV[1])
        local refill_rate  = tonumber(ARGV[2])  -- tokens per millisecond
        local requested    = tonumber(ARGV[3])
        local now          = tonumber(ARGV[4])  -- epoch millis
        local ttl          = tonumber(ARGV[5])  -- seconds

        local last_refill = tonumber(redis.call('GET', last_key) or now)
        local stored      = tonumber(redis.call('GET', tokens_key) or capacity)

        local elapsed = math.max(0, now - last_refill)
        local refilled = elapsed * refill_rate
        local tokens   = math.min(capacity, stored + refilled)

        if tokens < requested then
            local wait_ms = math.ceil((requested - tokens) / refill_rate)
            return {0, math.floor(tokens), wait_ms}
        end

        tokens = tokens - requested
        redis.call('SET', tokens_key, tokens, 'EX', ttl)
        redis.call('SET', last_key,   now,    'EX', ttl)
        return {1, math.floor(tokens), 0}
        """;

    public RedisRateLimitStore(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public ConsumeResult tryConsume(String bucketKey, long capacity, double refillRate, long tokensRequested) {
        String tokensRedisKey = "rl:" + bucketKey + ":tokens";
        String lastRefillKey  = "rl:" + bucketKey + ":last_refill";
        long nowMs = Instant.now().toEpochMilli();
        double refillPerMs = refillRate / 1000.0;
        int ttlSeconds = (int) (capacity / refillRate) * 10; // generous TTL

        try (Jedis jedis = jedisPool.getResource()) {
            Object result = jedis.eval(
                CONSUME_SCRIPT,
                java.util.Arrays.asList(tokensRedisKey, lastRefillKey),
                java.util.Arrays.asList(
                    String.valueOf(capacity),
                    String.valueOf(refillPerMs),
                    String.valueOf(tokensRequested),
                    String.valueOf(nowMs),
                    String.valueOf(ttlSeconds)
                )
            );

            java.util.List<?> res = (java.util.List<?>) result;
            boolean allowed = Long.parseLong(res.get(0).toString()) == 1;
            long tokensLeft = Long.parseLong(res.get(1).toString());
            long retryAfterMs = Long.parseLong(res.get(2).toString());
            return new ConsumeResult(allowed, tokensLeft, retryAfterMs);
        }
    }
}
