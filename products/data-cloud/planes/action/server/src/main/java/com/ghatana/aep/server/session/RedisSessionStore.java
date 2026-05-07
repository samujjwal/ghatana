/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.session;

import com.ghatana.aep.security.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.Objects;

/**
 * T-20: Redis-backed {@link SessionStore} for production environments.
 *
 * <p>Stores session tokens in Redis using {@code SET NX PX} (atomic
 * set-if-absent with millisecond TTL). This provides:
 * <ul>
 *   <li>Durable sessions that survive server restarts.</li>
 *   <li>Shared state across multiple server instances (horizontal scaling).</li>
 *   <li>Automatic expiry via Redis TTL — no manual eviction needed.</li>
 * </ul>
 *
 * <p>On Redis errors, operations fail open (session treated as absent),
 * so a Redis outage degrades gracefully rather than blocking all traffic.
 *
 * @doc.type class
 * @doc.purpose Redis-backed session store for production
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class RedisSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionStore.class);
    private static final String KEY_PREFIX = "aep:session:";

    private final JedisPool pool;

    /**
     * @param pool the Jedis connection pool (must not be null)
     */
    public RedisSessionStore(JedisPool pool) {
        this.pool = Objects.requireNonNull(pool, "jedisPool must not be null");
    }

    @Override
    public void put(String token, Duration ttl) {
        String key = KEY_PREFIX + token;
        long ttlMs = ttl.toMillis();
        try (Jedis jedis = pool.getResource()) {
            jedis.set(key, "1", SetParams.setParams().nx().px(ttlMs));
        } catch (Exception e) {
            log.warn("[session-store] Redis put failed for token prefix={}: {}",
                    token.substring(0, Math.min(8, token.length())), e.getMessage());
        }
    }

    @Override
    public boolean isValid(String token) {
        String key = KEY_PREFIX + token;
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(key);
        } catch (Exception e) {
            log.warn("[session-store] Redis isValid failed for token prefix={}: {}",
                    token.substring(0, Math.min(8, token.length())), e.getMessage());
            return false;
        }
    }

    @Override
    public void remove(String token) {
        String key = KEY_PREFIX + token;
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key);
        } catch (Exception e) {
            log.warn("[session-store] Redis remove failed for token prefix={}: {}",
                    token.substring(0, Math.min(8, token.length())), e.getMessage());
        }
    }
}
