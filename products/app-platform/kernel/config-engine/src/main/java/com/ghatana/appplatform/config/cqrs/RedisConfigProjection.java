package com.ghatana.appplatform.config.cqrs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import com.ghatana.appplatform.config.domain.ConfigValue;
import com.ghatana.appplatform.config.notification.ConfigChangeListener;
import com.ghatana.appplatform.config.port.ConfigStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * CQRS read-side projection for config resolution backed by Redis (K02-017).
 *
 * <p>Implements a read-through cache: on cache miss, the canonical {@link ConfigStore}
 * is queried, and the result is stored in Redis with a configurable TTL. When a change
 * notification arrives via {@link ConfigChangeListener}, all cached entries for the
 * affected namespace are invalidated using Redis {@code SCAN + DEL}.
 *
 * <h2>Key format</h2>
 * {@code config:{namespace}:{tenantId}:{userId}:{sessionId}:{jurisdiction}}
 * Null context dimensions are represented as {@code -} to keep the key stable.
 *
 * <h2>Invalidation strategy</h2>
 * On any change to a namespace, all keys matching {@code config:{namespace}:*} are
 * deleted via SCAN (non-blocking, 100 keys per cursor page). This is safe because
 * config changes are low-frequency write operations.
 *
 * @doc.type class
 * @doc.purpose Redis-backed read projection for config resolution (K02-017)
 * @doc.layer product
 * @doc.pattern Adapter, ReadModel
 */
public final class RedisConfigProjection implements ConfigChangeListener {

    private static final Logger log = LoggerFactory.getLogger(RedisConfigProjection.class);

    /** Prefix for all projection keys in Redis. */
    private static final String KEY_PREFIX = "config:";

    /** TTL for cached resolution results. 60 seconds provides freshness without hammering PostgreSQL. */
    private static final int DEFAULT_TTL_SECONDS = 60;

    private final ConfigStore source;
    private final JedisPool jedisPool;
    private final ObjectMapper mapper;
    private final int ttlSeconds;
    private final Executor blockingExecutor;

    /**
     * @param source           canonical config store used on cache miss
     * @param jedisPool        Redis connection pool
     * @param blockingExecutor executor for blocking {@code source.resolve()} calls
     */
    public RedisConfigProjection(ConfigStore source, JedisPool jedisPool, Executor blockingExecutor) {
        this(source, jedisPool, blockingExecutor, DEFAULT_TTL_SECONDS);
    }

    /**
     * @param source           canonical config store used on cache miss
     * @param jedisPool        Redis connection pool
     * @param blockingExecutor executor for blocking {@code source.resolve()} calls
     * @param ttlSeconds       cache TTL in seconds
     */
    public RedisConfigProjection(ConfigStore source, JedisPool jedisPool,
                                  Executor blockingExecutor, int ttlSeconds) {
        this.source          = Objects.requireNonNull(source, "source");
        this.jedisPool       = Objects.requireNonNull(jedisPool, "jedisPool");
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor");
        this.ttlSeconds      = ttlSeconds;
        this.mapper          = new ObjectMapper();
    }

    /**
     * Returns resolved config for the given context, serving from Redis when possible.
     *
     * <p>On a cache hit the result is deserialized and returned immediately.
     * On a miss the source store is queried via {@code Promise.ofBlocking},
     * the result cached in Redis, and then returned.
     *
     * @param namespace    config namespace to resolve
     * @param tenantId     tenant scope (null allowed)
     * @param userId       user scope (null allowed)
     * @param sessionId    session scope (null allowed)
     * @param jurisdiction jurisdiction scope (null allowed)
     * @return promise resolving to a map of key → {@link ConfigValue}
     */
    public Promise<Map<String, ConfigValue>> get(
            String namespace,
            String tenantId,
            String userId,
            String sessionId,
            String jurisdiction) {

        String redisKey = buildKey(namespace, tenantId, userId, sessionId, jurisdiction);

        return Promise.ofBlocking(blockingExecutor, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String cached = jedis.get(redisKey);
                if (cached != null) {
                    log.debug("Config cache HIT for key={}", redisKey);
                    return deserialize(cached);
                }
            }
            return null;
        }).then(cached -> {
            if (cached != null) {
                return Promise.of(cached);
            }
            // Cache miss: query source, store result
            return source.resolve(namespace, tenantId, userId, sessionId, jurisdiction)
                .then(resolved -> {
                    Promise.ofBlocking(blockingExecutor, () -> {
                        try (Jedis jedis = jedisPool.getResource()) {
                            jedis.setex(redisKey, ttlSeconds, serialize(resolved));
                        }
                        return null;
                    }).whenException(e ->
                        log.warn("Failed to cache config for key={}: {}", redisKey, e.getMessage())
                    );
                    return Promise.of(resolved);
                });
        });
    }

    /**
     * Invalidates all cached resolution results for the given namespace.
     *
     * <p>Uses Redis SCAN to iterate keys matching {@code config:{namespace}:*}
     * and deletes them in batches. Called when a {@link ConfigChangeListener}
     * notification arrives for this namespace.
     *
     * @param namespace the namespace whose cache should be cleared
     */
    public void invalidateNamespace(String namespace) {
        String pattern = KEY_PREFIX + namespace + ":*";
        int deleted = 0;
        try (Jedis jedis = jedisPool.getResource()) {
            String cursor = "0";
            ScanParams params = new ScanParams().match(pattern).count(100);
            do {
                ScanResult<String> result = jedis.scan(cursor, params);
                cursor = result.getCursor();
                List<String> keys = result.getResult();
                if (!keys.isEmpty()) {
                    jedis.del(keys.toArray(new String[0]));
                    deleted += keys.size();
                }
            } while (!"0".equals(cursor));
        } catch (Exception e) {
            log.error("Failed to invalidate cache for namespace={}: {}", namespace, e.getMessage(), e);
        }
        if (deleted > 0) {
            log.debug("Invalidated {} cache entries for namespace={}", deleted, namespace);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Called by {@link com.ghatana.appplatform.config.notification.ConfigChangeNotifier}
     * when any config key changes. Invalidates the entire namespace cache so the next
     * read re-resolves from the canonical store.
     */
    @Override
    public void onConfigChange(String namespace, String key, String level, String levelId) {
        log.info("Config change detected namespace={} key={} level={}/{} — invalidating cache",
            namespace, key, level, levelId);
        invalidateNamespace(namespace);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Builds the Redis cache key for a resolution context.
     * Null dimensions are encoded as {@code -} to produce a stable key.
     */
    static String buildKey(String namespace, String tenantId, String userId,
                            String sessionId, String jurisdiction) {
        return KEY_PREFIX
            + namespace                         + ":"
            + nvl(tenantId)    + ":"
            + nvl(userId)      + ":"
            + nvl(sessionId)   + ":"
            + nvl(jurisdiction);
    }

    private static String nvl(String s) {
        return s != null ? s : "-";
    }

    private String serialize(Map<String, ConfigValue> values) {
        // Serialize as a map of key → ConfigValue fields for compact storage
        Map<String, Object> serializable = new HashMap<>(values.size());
        for (Map.Entry<String, ConfigValue> e : values.entrySet()) {
            ConfigValue cv = e.getValue();
            serializable.put(e.getKey(), Map.of(
                "value", cv.value(),
                "level", cv.resolvedFromLevel().name(),
                "levelId", cv.resolvedFromLevelId()
            ));
        }
        try {
            return mapper.writeValueAsString(serializable);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize config values to JSON", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, ConfigValue> deserialize(String json) {
        try {
            Map<String, Object> raw = mapper.readValue(json,
                new TypeReference<Map<String, Object>>() {});
            Map<String, ConfigValue> result = new HashMap<>(raw.size());
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                Map<String, String> cv = (Map<String, String>) entry.getValue();
                result.put(entry.getKey(), new ConfigValue(
                    entry.getKey(),
                    cv.get("value"),
                    ConfigHierarchyLevel.valueOf(cv.get("level")),
                    cv.get("levelId")
                ));
            }
            return result;
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached config, will re-resolve: {}", e.getMessage());
            return null;
        }
    }
}
