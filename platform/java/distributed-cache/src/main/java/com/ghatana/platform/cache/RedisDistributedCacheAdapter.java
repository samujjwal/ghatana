package com.ghatana.platform.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Redis-backed implementation of {@link DistributedCachePort} using Jedis.
 *
 * <p>All Redis I/O is wrapped in {@code Promise.ofBlocking(executor, ...)} so the
 * ActiveJ event loop is never blocked. Each logical cache namespace maps to a Redis
 * key-prefix pattern {@code "cache:<namespace>:<key>"}, enabling atomic flush of a
 * namespace via {@code SCAN + DEL}.</p>
 *
 * <h3>Thread safety</h3>
 * <p>This class is thread-safe. The underlying {@link JedisPool} handles connection
 * pooling. Each operation borrows and returns a connection within the same lambda
 * body executed by the blocking executor.</p>
 *
 * <h3>Value serialization</h3>
 * <p>Values are serialized to JSON bytes using the supplied {@link ObjectMapper}.
 * The target type is captured at construction time via {@code valueType} so that
 * Jackson can deserialize polymorphic types correctly.</p>
 *
 * @param <K> cache key type — keys are converted to String via {@code toString()}
 * @param <V> cache value type — must be Jackson-serializable
 *
 * @doc.type class
 * @doc.purpose Redis-backed distributed cache adapter with namespace isolation and TTL support
 * @doc.layer platform
 * @doc.pattern Adapter (Hexagonal Architecture), Port Driven
 * @since 1.0.0
 */
public class RedisDistributedCacheAdapter<K, V> implements DistributedCachePort<K, V> {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedCacheAdapter.class);

    private final JedisPool jedisPool;
    private final ObjectMapper mapper;
    private final JavaType valueJavaType;
    private final Executor executor;
    private final String namespace;
    private final Duration defaultTtl;

    /**
     * Constructs a new Redis-backed cache adapter.
     *
     * @param jedisPool   shared connection pool — must not be {@code null}
     * @param mapper      Jackson object mapper — must not be {@code null}
     * @param valueType   runtime class of the value type for deserialization
     * @param executor    blocking executor for Redis I/O — must not be {@code null}
     * @param namespace   logical namespace prefix, e.g. {@code "finance.risk"} or {@code "phr.consent"}
     * @param defaultTtl  default entry time-to-live; must be positive
     */
    public RedisDistributedCacheAdapter(
            JedisPool jedisPool,
            ObjectMapper mapper,
            Class<V> valueType,
            Executor executor,
            String namespace,
            Duration defaultTtl) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.valueJavaType = mapper.getTypeFactory().constructType(
                Objects.requireNonNull(valueType, "valueType must not be null"));
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.namespace = Objects.requireNonNull(namespace, "namespace must not be null");
        this.defaultTtl = Objects.requireNonNull(defaultTtl, "defaultTtl must not be null");
        if (defaultTtl.isNegative() || defaultTtl.isZero()) {
            throw new IllegalArgumentException("defaultTtl must be positive");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // DistributedCachePort implementation
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public Promise<Optional<V>> get(K key) {
        String redisKey = buildKey(key);
        return Promise.ofBlocking(executor, () -> {
            try (var jedis = jedisPool.getResource()) {
                String json = jedis.get(redisKey);
                if (json == null) {
                    return Optional.empty();
                }
                V value = mapper.readValue(json, valueJavaType);
                return Optional.ofNullable(value);
            } catch (Exception e) {
                log.warn("Cache GET failed for key={}: {}", redisKey, e.getMessage());
                return Optional.empty();
            }
        });
    }

    @Override
    public Promise<Void> put(K key, V value) {
        return put(key, value, defaultTtl);
    }

    @Override
    public Promise<Void> put(K key, V value, Duration ttl) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        String redisKey = buildKey(key);
        return Promise.ofBlocking(executor, () -> {
            try (var jedis = jedisPool.getResource()) {
                String json = mapper.writeValueAsString(value);
                jedis.set(redisKey, json, SetParams.setParams().ex(ttl.getSeconds()));
            } catch (Exception e) {
                log.warn("Cache PUT failed for key={}: {}", redisKey, e.getMessage());
                // Swallow to keep cache failure transparent to caller
            }
            return null;
        });
    }

    @Override
    public Promise<V> getOrLoad(K key, Function<K, Promise<V>> loader) {
        return get(key).then(optVal -> {
            if (optVal.isPresent()) {
                return Promise.of(optVal.get());
            }
            return loader.apply(key)
                .then(value -> put(key, value).map($ -> value));
        });
    }

    @Override
    public Promise<Void> invalidate(K key) {
        String redisKey = buildKey(key);
        return Promise.ofBlocking(executor, () -> {
            try (var jedis = jedisPool.getResource()) {
                jedis.del(redisKey);
            } catch (Exception e) {
                log.warn("Cache INVALIDATE failed for key={}: {}", redisKey, e.getMessage());
            }
            return null;
        });
    }

    @Override
    public Promise<Void> invalidateAll() {
        String pattern = "cache:" + namespace + ":*";
        return Promise.ofBlocking(executor, () -> {
            try (var jedis = jedisPool.getResource()) {
                // Scan-based delete to avoid blocking Redis with a single DEL of thousands of keys
                String cursor = "0";
                do {
                    var result = jedis.scan(cursor,
                            new redis.clients.jedis.params.ScanParams().match(pattern).count(200));
                    cursor = result.getCursor();
                    var keys = result.getResult();
                    if (!keys.isEmpty()) {
                        jedis.del(keys.toArray(new String[0]));
                    }
                } while (!"0".equals(cursor));
            } catch (Exception e) {
                log.warn("Cache INVALIDATE_ALL failed for namespace={}: {}", namespace, e.getMessage());
            }
            return null;
        });
    }

    @Override
    public boolean isHealthy() {
        try (var jedis = jedisPool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Key construction
    // ──────────────────────────────────────────────────────────────────────

    private String buildKey(K key) {
        return "cache:" + namespace + ":" + key.toString();
    }
}
