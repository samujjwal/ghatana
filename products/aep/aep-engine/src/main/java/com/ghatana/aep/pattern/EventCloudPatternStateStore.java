/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Redis-backed implementation of {@link PatternStateStore}.
 *
 * <p>Provides durable pattern state persistence suitable for multi-node deployments
 * and process restarts. State is serialised to bytes by the caller-supplied functions
 * and stored in Redis hashes with tenant-isolated key namespacing.
 *
 * <p>Redis key structure:
 * <pre>
 *   aep:pattern-state:{tenantId}   (HASH)  →  field={patternId}, value=base64(serialized state)
 * </pre>
 *
 * <p>When no {@link JedisPool} is provided the store falls back to an
 * in-memory {@link ConcurrentHashMap}, which is safe for single-node
 * development and testing but does not survive process restarts.
 *
 * @param <S> the serialisable state type
 *
 * @doc.type class
 * @doc.purpose Durable pattern state storage backed by Redis with in-memory fallback
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class EventCloudPatternStateStore<S> implements PatternStateStore<S> {

    private static final Logger log = LoggerFactory.getLogger(EventCloudPatternStateStore.class);
    private static final String KEY_PREFIX = "aep:pattern-state:";

    private final Function<S, byte[]> serializer;
    private final Function<byte[], S> deserializer;
    private final JedisPool jedisPool;   // nullable — null triggers in-memory fallback
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, byte[]>> inMemoryStore;
    private final ExecutorService ioExecutor;

    /**
     * Creates a Redis-backed store.
     *
     * @param jedisPool    Jedis connection pool pointing at a Redis instance
     * @param serializer   converts state to bytes for storage
     * @param deserializer converts bytes back to state
     */
    public EventCloudPatternStateStore(
            JedisPool jedisPool,
            Function<S, byte[]> serializer,
            Function<byte[], S> deserializer) {
        this.jedisPool = jedisPool;
        this.serializer = serializer;
        this.deserializer = deserializer;
        this.inMemoryStore = new ConcurrentHashMap<>();
        this.ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Creates an in-memory-only store (useful in tests and single-node dev).
     *
     * @param serializer   converts state to bytes
     * @param deserializer converts bytes back to state
     */
    public EventCloudPatternStateStore(
            Function<S, byte[]> serializer,
            Function<byte[], S> deserializer) {
        this(null, serializer, deserializer);
    }

    @Override
    public @NotNull Promise<Void> save(@NotNull String tenantId,
                                       @NotNull String patternId,
                                       @NotNull S state) {
        byte[] bytes = serializer.apply(state);
        return Promise.ofBlocking(ioExecutor, () -> {
            if (jedisPool != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.hset(redisKey(tenantId), patternId, Base64.getEncoder().encodeToString(bytes));
                    log.debug("Saved pattern state to Redis: tenant={} pattern={}", tenantId, patternId);
                }
            } else {
                inMemoryStore.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                    .put(patternId, bytes);
                log.debug("Saved pattern state to in-memory store: tenant={} pattern={}", tenantId, patternId);
            }
            return null;
        });
    }

    @Override
    public @NotNull Promise<Optional<S>> load(@NotNull String tenantId,
                                               @NotNull String patternId) {
        return Promise.ofBlocking(ioExecutor, () -> {
            if (jedisPool != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    String encoded = jedis.hget(redisKey(tenantId), patternId);
                    if (encoded == null) {
                        return Optional.empty();
                    }
                    byte[] bytes = Base64.getDecoder().decode(encoded);
                    log.debug("Loaded pattern state from Redis: tenant={} pattern={}", tenantId, patternId);
                    return Optional.of(deserializer.apply(bytes));
                }
            } else {
                ConcurrentHashMap<String, byte[]> tenantMap = inMemoryStore.get(tenantId);
                if (tenantMap == null) {
                    return Optional.empty();
                }
                byte[] bytes = tenantMap.get(patternId);
                return bytes != null ? Optional.of(deserializer.apply(bytes)) : Optional.empty();
            }
        });
    }

    @Override
    public @NotNull Promise<Void> delete(@NotNull String tenantId,
                                          @NotNull String patternId) {
        return Promise.ofBlocking(ioExecutor, () -> {
            if (jedisPool != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.hdel(redisKey(tenantId), patternId);
                    log.debug("Deleted pattern state from Redis: tenant={} pattern={}", tenantId, patternId);
                }
            } else {
                ConcurrentHashMap<String, byte[]> tenantMap = inMemoryStore.get(tenantId);
                if (tenantMap != null) {
                    tenantMap.remove(patternId);
                }
            }
            return null;
        });
    }

    @Override
    public @NotNull Promise<Boolean> exists(@NotNull String tenantId,
                                             @NotNull String patternId) {
        return Promise.ofBlocking(ioExecutor, () -> {
            if (jedisPool != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    return jedis.hexists(redisKey(tenantId), patternId);
                }
            } else {
                ConcurrentHashMap<String, byte[]> tenantMap = inMemoryStore.get(tenantId);
                return tenantMap != null && tenantMap.containsKey(patternId);
            }
        });
    }

    private static String redisKey(String tenantId) {
        return KEY_PREFIX + tenantId;
    }
}
