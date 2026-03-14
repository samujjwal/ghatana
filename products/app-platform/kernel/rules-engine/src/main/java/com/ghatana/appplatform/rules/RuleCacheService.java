package com.ghatana.appplatform.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Redis-backed cache layer for OPA policy evaluation results.
 *
 * <p>Cache key: {@code rule:cache:{hex(SHA-256(policyPath + ":" + serialisedInput))}}.
 * On a cache miss the underlying {@link OpaEvaluationService} is called and the
 * result stored in Redis with a configurable TTL.
 *
 * <p>Cache entries are stored as JSON strings (serialised {@link OpaEvaluationService.OpaResult}).
 *
 * @doc.type class
 * @doc.purpose Redis-backed result cache for OPA policy evaluations
 * @doc.layer product
 * @doc.pattern Service
 */
public final class RuleCacheService {

    private static final Logger log = LoggerFactory.getLogger(RuleCacheService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String KEY_PREFIX = "rule:cache:";
    /** Default cache TTL: 60 seconds. */
    public static final int DEFAULT_TTL_SECONDS = 60;

    private final JedisPool jedisPool;
    private final OpaEvaluationService evaluator;
    private final Executor executor;
    private final int ttlSeconds;

    /**
     * Creates a cache service with the default TTL of {@value #DEFAULT_TTL_SECONDS} seconds.
     *
     * @param jedisPool Redis connection pool
     * @param evaluator underlying OPA evaluation service (called on cache miss)
     * @param executor  blocking executor for Redis and OPA calls
     */
    public RuleCacheService(JedisPool jedisPool, OpaEvaluationService evaluator, Executor executor) {
        this(jedisPool, evaluator, executor, DEFAULT_TTL_SECONDS);
    }

    /**
     * Creates a cache service with a custom TTL.
     *
     * @param jedisPool  Redis connection pool
     * @param evaluator  underlying OPA evaluation service
     * @param executor   blocking executor
     * @param ttlSeconds seconds before each cached entry expires
     */
    public RuleCacheService(JedisPool jedisPool, OpaEvaluationService evaluator,
                            Executor executor, int ttlSeconds) {
        this.jedisPool = jedisPool;
        this.evaluator = evaluator;
        this.executor = executor;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Returns a cached evaluation result, or evaluates via OPA and caches the result.
     *
     * @param policyPath OPA policy path (e.g. {@code authz/allow})
     * @param input      input document for the evaluation
     * @return promise resolving to an {@link OpaEvaluationService.OpaResult}
     */
    public Promise<OpaEvaluationService.OpaResult> getOrEvaluate(String policyPath, Map<String, Object> input) {
        return Promise.ofBlocking(executor, () -> {
            String cacheKey = buildCacheKey(policyPath, input);
            try (var jedis = jedisPool.getResource()) {
                String cached = jedis.get(cacheKey);
                if (cached != null) {
                    log.debug("Cache HIT: key={}", cacheKey);
                    return deserialise(cached, policyPath);
                }
            }
            log.debug("Cache MISS: key={} — calling OPA", cacheKey);
            // Block on the inner promise (we are already on the blocking executor)
            OpaEvaluationService.OpaResult result = evaluator
                    .evaluate(policyPath, input)
                    .getResult();
            try (var jedis = jedisPool.getResource()) {
                jedis.setex(cacheKey, ttlSeconds, serialise(result));
            }
            return result;
        });
    }

    /**
     * Removes all cached entries whose cache key was derived from {@code policyPath}.
     *
     * <p>Because the cache key is a SHA-256 hash of policyPath+input, exact key-based
     * invalidation is not possible without re-hashing every evicted combination.
     * This method therefore scans for all keys matching the prefix pattern and deletes them.
     * <strong>For production use, prefer TTL-based expiry.</strong>
     *
     * @param policyPath policy path prefix; only entries whose key matches this prefix
     *                   (before hashing) are candidates — this performs a Redis SCAN
     *                   to find all {@code rule:cache:*} keys
     */
    public Promise<Long> invalidateAll() {
        return Promise.ofBlocking(executor, () -> {
            long deleted = 0L;
            try (var jedis = jedisPool.getResource()) {
                var cursor = redis.clients.jedis.ScanParams.SCAN_POINTER_START;
                var params = new redis.clients.jedis.ScanParams().match(KEY_PREFIX + "*").count(100);
                do {
                    var result = jedis.scan(cursor, params);
                    cursor = result.getCursor();
                    var keys = result.getResult();
                    if (!keys.isEmpty()) {
                        deleted += jedis.del(keys.toArray(new String[0]));
                    }
                } while (!cursor.equals(redis.clients.jedis.ScanParams.SCAN_POINTER_START));
            }
            log.info("Invalidated {} cached rule entries", deleted);
            return deleted;
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String buildCacheKey(String policyPath, Map<String, Object> input) throws Exception {
        String inputJson = MAPPER.writeValueAsString(input);
        String raw = policyPath + ":" + inputJson;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        return KEY_PREFIX + bytesToHex(hash);
    }

    private String serialise(OpaEvaluationService.OpaResult result) throws Exception {
        return MAPPER.writeValueAsString(result);
    }

    @SuppressWarnings("unchecked")
    private OpaEvaluationService.OpaResult deserialise(String json, String policyPath) throws Exception {
        var node = MAPPER.readTree(json);
        boolean allow = node.path("allow").asBoolean(false);
        Map<String, Object> resultMap = MAPPER.convertValue(node.path("result"),
                MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        return new OpaEvaluationService.OpaResult(allow, resultMap, policyPath);
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
