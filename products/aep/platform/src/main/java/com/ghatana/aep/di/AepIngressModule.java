/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.ingress.api.HealthController;
import com.ghatana.ingress.api.ratelimit.RateLimitStorage;
import com.ghatana.ingress.api.ratelimit.RedisRateLimitStorage;
import com.ghatana.ingress.app.IdempotencyService;
import com.ghatana.aep.config.EnvConfig;
import io.activej.eventloop.Eventloop;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
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

    private static final Logger log = LoggerFactory.getLogger(AepIngressModule.class);

    private static final int DEFAULT_MAX_POOL_SIZE = 16;
    private static final int DEFAULT_MAX_IDLE = 8;
    private static final Duration DEFAULT_POOL_TIMEOUT = Duration.ofSeconds(5);

    private static final String RATE_LIMIT_KEY_PREFIX = "rl:";
    private static final int DEFAULT_MAX_REQUESTS = 1000;
    private static final long DEFAULT_WINDOW_MS = 60_000L;

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final Duration DEFAULT_IDEMPOTENCY_TTL = Duration.ofHours(24);

    /**
     * Provides a shared Redis connection pool with startup health check.
     *
     * <p>The pool is configured for production use with bounded connections,
     * idle eviction, borrowing/returning validation, and fairness. A PING
     * is issued immediately after pool creation to fail fast when Redis is
     * unreachable in non-development environments. Shared by both the rate
     * limiter and idempotency service to minimise Redis connection overhead.
     *
     * @return Jedis connection pool (verified reachable)
     * @throws IllegalStateException if Redis is unreachable outside dev mode
     */
    @Provides
    JedisPool jedisPool() {
        EnvConfig env = EnvConfig.fromSystem();
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(DEFAULT_MAX_POOL_SIZE);
        poolConfig.setMaxIdle(DEFAULT_MAX_IDLE);
        poolConfig.setMinIdle(2);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setBlockWhenExhausted(true);
        JedisPool pool = new JedisPool(poolConfig, env.redisHost(), env.redisPort(),
                (int) DEFAULT_POOL_TIMEOUT.toMillis());
        verifyRedisReachable(pool, env);
        return pool;
    }

    /**
     * Verifies Redis is reachable by issuing a PING immediately after pool creation.
     *
     * <p>In development ({@code APP_ENV=development}) a failure is only logged as a
     * warning so that local development without Redis still works. In all other
     * environments the application fails fast with a clear error message.
     *
     * @param pool the newly created pool
     * @param env  AEP environment config (checked for dev mode)
     * @throws IllegalStateException if Redis is unreachable in non-dev mode
     */
    private void verifyRedisReachable(JedisPool pool, EnvConfig env) {
        try (Jedis jedis = pool.getResource()) {
            String pong = jedis.ping();
            log.info("Redis health check OK: host={}:{} response={}",
                    env.redisHost(), env.redisPort(), pong);
        } catch (Exception e) {
            if (env.isDevelopment()) {
                log.warn("Redis unreachable at {}:{} — running in dev mode, continuing without Redis. "
                        + "Rate-limiting and idempotency will not function. Error: {}",
                        env.redisHost(), env.redisPort(), e.getMessage());
            } else {
                throw new IllegalStateException(
                        "AEP startup failed: Redis unreachable at " + env.redisHost()
                        + ":" + env.redisPort() + ". Set APP_ENV=development to skip this check.", e);
            }
        }
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
