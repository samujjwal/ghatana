package com.ghatana.auth.adapter.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.security.port.SessionStore;
import com.ghatana.platform.domain.auth.Session;
import com.ghatana.platform.domain.auth.SessionId;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserId;

import io.activej.promise.Promise;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Redis-backed SessionStore for distributed session management.
 *
 * <p>
 * <b>Purpose</b><br>
 * Stores authenticated sessions in Redis with TTL-based expiration. Supports
 * cross-instance session access and distributed logout.
 *
 * <p>
 * <b>Key Format</b><br>
 * All session keys: `{tenant_id}:session:{session_id}` Example:
 * `tenant-123:session:550e8400-e29b-41d4-a716-446655440000`
 *
 * <p>
 * <b>TTL</b><br>
 * Default session TTL: 1 hour (configurable) Sessions expire automatically from
 * Redis after TTL.
 *
 * <p>
 * <b>Async Model</b><br>
 * All Redis operations wrapped in Promise.ofBlocking() to avoid blocking
 * eventloop.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * JedisPool jedisPool = new JedisPool("localhost", 6379);
 * SessionStore sessions = new RedisSessionStore(jedisPool, Duration.ofHours(1));
 *
 * TenantId tenant = TenantId.of("tenant-1");
 * Session session = Session.create(userId, Instant.now().plus(Duration.ofHours(1)));
 *
 * sessions.store(tenant, session)
 *     .whenComplete((stored, error) -> {
 *         if (error != null) {
 *             log.error("Store failed", error);
 *         } else {
 *             log.info("Session stored: {}", session.getId());
 *         }
 *     });
 * }</pre>
 *
 * @see SessionStore for interface contract
 * @doc.type class
 * @doc.purpose Redis session store adapter
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class RedisSessionStore implements SessionStore {

    private final JedisPool jedisPool;
    private final Duration defaultTtl;
    private final ObjectMapper objectMapper;
    private static final ForkJoinPool REDIS_POOL = ForkJoinPool.commonPool();

    /**
     * Create Redis session store.
     *
     * @param jedisPool Redis connection pool
     * @param defaultTtl Default session TTL (e.g., 1 hour)
     * @param objectMapper Jackson ObjectMapper for serialization
     */
    public RedisSessionStore(
            JedisPool jedisPool,
            Duration defaultTtl,
            ObjectMapper objectMapper
    ) {
        this.jedisPool = jedisPool;
        this.defaultTtl = defaultTtl;
        this.objectMapper = objectMapper;
    }

    @Override
    public Promise<Void> store(Session session) {
        if (session == null) {
            return Promise.ofException(
                    new IllegalArgumentException("session must not be null")
            );
        }

        return Promise.ofBlocking(REDIS_POOL, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String key = buildKey(session.getTenantId(), session.getSessionId());
                String value = objectMapper.writeValueAsString(session);
                long ttlSeconds = java.time.Duration.between(
                        Instant.now(),
                        session.getExpiresAt()
                ).getSeconds();
                if (ttlSeconds > 0) {
                    jedis.setex(key, (int) ttlSeconds, value);
                } else {
                    jedis.set(key, value);
                }
                return (Void) null;
            } catch (Exception e) {
                throw e;
            }
        });
    }

    @Override
    public Promise<Optional<Session>> findById(TenantId tenantId, SessionId sessionId) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }
        if (sessionId == null) {
            return Promise.ofException(
                    new IllegalArgumentException("sessionId must not be null")
            );
        }

        return Promise.ofBlocking(REDIS_POOL, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String key = buildKey(tenantId, sessionId);
                String value = jedis.get(key);

                if (value == null) {
                    return Optional.empty();
                }

                Session session = objectMapper.readValue(value, Session.class);
                return Optional.of(session);
            } catch (Exception e) {
                throw e;
            }
        });
    }

    @Override
    public Promise<Void> invalidate(TenantId tenantId, SessionId sessionId) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }
        if (sessionId == null) {
            return Promise.ofException(
                    new IllegalArgumentException("sessionId must not be null")
            );
        }

        return Promise.ofBlocking(REDIS_POOL, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String key = buildKey(tenantId, sessionId);
                jedis.del(key);
                return null;
            } catch (Exception e) {
                throw e;
            }
        });
    }

    public Promise<Session> extend(TenantId tenantId, SessionId sessionId, Duration additionalTime) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }
        if (sessionId == null) {
            return Promise.ofException(
                    new IllegalArgumentException("sessionId must not be null")
            );
        }
        if (additionalTime == null) {
            return Promise.ofException(
                    new IllegalArgumentException("additionalTime must not be null")
            );
        }

        return Promise.ofBlocking(REDIS_POOL, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String key = buildKey(tenantId, sessionId);
                String value = jedis.get(key);
                if (value == null) {
                    throw new IllegalArgumentException("Session not found: " + sessionId);
                }
                Session session = objectMapper.readValue(value, Session.class);
                jedis.expire(key, (int) additionalTime.getSeconds());
                return session;
            } catch (Exception e) {
                throw e;
            }
        });
    }

    @Override
    public Promise<Optional<Session>> findByUserId(TenantId tenantId, UserId userId) {
        // Implementation stub for now - full multi-tenant session retrieval not implemented
        return Promise.of(Optional.empty());
    }

    @Override
    public Promise<Void> touch(TenantId tenantId, SessionId sessionId) {
        // Sliding expiry - extend session with default TTL
        return extend(tenantId, sessionId, defaultTtl).map(session -> null);
    }

    @Override
    public Promise<Integer> invalidateAllForUser(TenantId tenantId, UserId userId) {
        // Implementation stub for now - full session revocation not implemented
        return Promise.of(0);
    }

    public Promise<Integer> cleanupExpired(TenantId tenantId) {
        // Implementation stub for now - Redis handles TTL automatically
        return Promise.of(0);
    }

    /**
     * Build Redis key from tenant and session ID.
     *
     * @param tenantId tenant identifier
     * @param sessionId session identifier
     * @return formatted key: "{tenant}:session:{sessionId}"
     */
    private String buildKey(TenantId tenantId, SessionId sessionId) {
        return tenantId.value() + ":session:" + sessionId.value();
    }
}
