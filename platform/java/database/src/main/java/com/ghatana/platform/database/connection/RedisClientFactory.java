/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.database.connection;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Factory for creating lifecycle-managed {@link JedisPool} instances.
 *
 * <p>Centralizes Jedis connection pool creation so that all consumers share
 * consistent pool settings. Pools created through this factory are tracked
 * and closed on JVM shutdown.
 *
 * <pre>{@code
 * RedisConfig config = RedisConfig.builder()
 *     .host("redis.internal")
 *     .port(6379)
 *     .maxTotal(16)
 *     .build();
 *
 * JedisPool pool = RedisClientFactory.create(config);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Factory for lifecycle-managed Jedis connection pools
 * @doc.layer platform
 * @doc.pattern Factory
 */
public final class RedisClientFactory {

    private static final Logger log = LoggerFactory.getLogger(RedisClientFactory.class);
    private static final List<JedisPool> pools = new CopyOnWriteArrayList<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(RedisClientFactory::closeAll, "redis-shutdown"));
    }

    private RedisClientFactory() {}

    /**
     * Creates a new {@link JedisPool} from the given configuration.
     *
     * <p>The pool is tracked for orderly shutdown when the JVM exits or
     * when {@link #closeAll()} is called explicitly.
     *
     * @param config Redis connection configuration
     * @return a managed Jedis pool
     */
    public static @NotNull JedisPool create(@NotNull RedisConfig config) {
        Objects.requireNonNull(config, "config");

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.maxTotal());
        poolConfig.setMaxIdle(config.maxIdle());

        JedisPool pool = config.password() != null
            ? new JedisPool(poolConfig, config.host(), config.port(),
                (int) config.timeout().toMillis(), config.password())
            : new JedisPool(poolConfig, config.host(), config.port(),
                (int) config.timeout().toMillis());

        pools.add(pool);
        log.info("Created managed JedisPool {}:{} (maxTotal={})",
            config.host(), config.port(), config.maxTotal());
        return pool;
    }

    /**
     * Closes all Jedis pools created through this factory.
     */
    public static void closeAll() {
        for (JedisPool pool : pools) {
            try {
                if (!pool.isClosed()) {
                    pool.close();
                }
            } catch (Exception e) {
                log.warn("Error closing JedisPool: {}", e.getMessage());
            }
        }
        pools.clear();
    }

    /**
     * Returns the number of active managed pools.
     */
    public static int poolCount() {
        return pools.size();
    }
}
