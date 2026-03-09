/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.ingress.api.HealthController;
import com.ghatana.ingress.api.ratelimit.RateLimitStorage;
import com.ghatana.ingress.api.ratelimit.RedisRateLimitStorage;
import com.ghatana.ingress.app.IdempotencyService;
import io.activej.eventloop.Eventloop;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

/**
 * ActiveJ DI module for AEP ingress components.
 *
 * <p>Provides the event ingress pipeline — rate limiting, idempotency
 * deduplication, health endpoints, and Redis connectivity:
 * <ul>
 *   <li>{@link RateLimitStorage} — distributed sliding window rate limiter</li>
 *   <li>{@link IdempotencyService} — Redis-backed idempotency for at-most-once delivery</li>
 *   <li>{@link HealthController} — /health/live and /health/ready endpoints</li>
 *   <li>{@link JedisPool} — shared Redis connection pool</li>
 * </ul>
 *
 * <p><b>Dependencies:</b> Requires {@link Eventloop} from {@link AepCoreModule}.
 *
 * <p><b>Redis Defaults:</b>
 * <ul>
 *   <li>Host: {@code localhost:6379}</li>
 *   <li>Pool: max 16 connections, 8 idle, 5s timeout</li>
 *   <li>Rate limit: 1000 requests per 60-second window</li>
 *   <li>Idempotency TTL: 24 hours</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Injector injector = Injector.of(
 *     new AepCoreModule(),
 *     new AepIngressModule()
 * );
 * RateLimitStorage rateLimiter = injector.getInstance(RateLimitStorage.class);
 * IdempotencyService idempotency = injector.getInstance(IdempotencyService.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for ingress rate limiting, idempotency, and health
 * @doc.layer product
 * @doc.pattern Module
 * @see RateLimitStorage
 * @see IdempotencyService
 * @see HealthController
 */
public class AepIngressModule extends AbstractModule {

    private static final String DEFAULT_REDIS_HOST = "localhost";
    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final int DEFAULT_MAX_POOL_SIZE = 16;
    private static final int DEFAULT_MAX_IDLE = 8;
    private static final Duration DEFAULT_POOL_TIMEOUT = Duration.ofSeconds(5);

    private static final String RATE_LIMIT_KEY_PREFIX = "rl:";
    private static final int DEFAULT_MAX_REQUESTS = 1000;
    private static final long DEFAULT_WINDOW_MS = 60_000L;

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final Duration DEFAULT_IDEMPOTENCY_TTL = Duration.ofHours(24);

    /**
     * Provides a shared Redis connection pool.
     *
     * <p>The pool is configured for production use with bounded connections, 
     * idle eviction, and fairness. Shared by both the rate limiter and
     * idempotency service to minimize Redis connection overhead.
     *
     * @return Jedis connection pool
     */
    @Provides
    JedisPool jedisPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(DEFAULT_MAX_POOL_SIZE);
        poolConfig.setMaxIdle(DEFAULT_MAX_IDLE);
        poolConfig.setMinIdle(2);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setBlockWhenExhausted(true);
        return new JedisPool(poolConfig, DEFAULT_REDIS_HOST, DEFAULT_REDIS_PORT,
                (int) DEFAULT_POOL_TIMEOUT.toMillis());
    }

    /**
     * Provides the rate limit storage bound to its interface.
     *
     * <p>Uses a Redis-backed sliding window algorithm. Default configuration
     * allows 1000 requests per 60-second window per key.
     *
     * @param jedisPool shared Redis connection pool
     * @return rate limit storage
     */
    @Provides
    RateLimitStorage rateLimitStorage(JedisPool jedisPool) {
        return new RedisRateLimitStorage(jedisPool, RATE_LIMIT_KEY_PREFIX,
                DEFAULT_MAX_REQUESTS, DEFAULT_WINDOW_MS);
    }

    /**
     * Provides the idempotency service.
     *
     * <p>Uses Redis with a 24-hour default TTL for idempotency keys.
     * Keys are prefixed with {@code idempotency:} and scoped by tenant.
     *
     * @param jedisPool shared Redis connection pool
     * @return idempotency service
     */
    @Provides
    IdempotencyService idempotencyService(JedisPool jedisPool) {
        return new IdempotencyService(jedisPool, IDEMPOTENCY_KEY_PREFIX, DEFAULT_IDEMPOTENCY_TTL);
    }

    /**
     * Provides the health controller for liveness and readiness probes.
     *
     * <p>Exposes {@code /health/live} (always UP) and {@code /health/ready}
     * endpoints via ActiveJ HTTP routing.
     *
     * @param eventloop ActiveJ event loop
     * @return health controller
     */
    @Provides
    HealthController healthController(Eventloop eventloop) {
        return new HealthController(eventloop);
    }
}
