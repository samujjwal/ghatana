/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.incident;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Redis-backed {@link GracefulDegradationManager} with PostgreSQL fallback.
 *
 * <p>The current degradation mode for each tenant is stored in Redis as a
 * plain string under the key {@code aep:degradation:{tenantId}}.
 * If the Redis operation fails (or the key is absent) the manager reads
 * from the {@code degradation_modes} table in PostgreSQL instead, ensuring
 * the system degrades gracefully even when Redis is unavailable.
 *
 * @doc.type class
 * @doc.purpose Redis-backed graceful-degradation manager with Postgres fallback
 * @doc.layer shared-service
 * @doc.pattern Repository
 */
public final class RedisGracefulDegradationManager implements GracefulDegradationManager {

    private static final Logger log = LoggerFactory.getLogger(RedisGracefulDegradationManager.class);
    private static final String KEY_PREFIX = "aep:degradation:";

    // Same action-permission map as InMemoryGracefulDegradationManager
    private static final Map<DegradationMode, Set<String>> ALLOWED_ACTIONS = Map.of(
        DegradationMode.FULL,               Set.of(),
        DegradationMode.READ_ONLY,          Set.of("READ", "QUERY", "LIST", "GET"),
        DegradationMode.NOTIFICATIONS_ONLY, Set.of("NOTIFY"),
        DegradationMode.OFFLINE,            Set.of()
    );

    private final JedisPool jedisPool;
    private final DataSource dataSource;
    private final Executor executor;

    /**
     * @param jedisPool  Jedis connection pool; never {@code null}
     * @param dataSource JDBC data source for Postgres fallback; never {@code null}
     * @param executor   blocking-I/O thread pool; never {@code null}
     */
    public RedisGracefulDegradationManager(JedisPool jedisPool, DataSource dataSource, Executor executor) {
        this.jedisPool  = Objects.requireNonNull(jedisPool,    "jedisPool");
        this.dataSource = Objects.requireNonNull(dataSource,   "dataSource");
        this.executor   = Objects.requireNonNull(executor,     "executor");
    }

    /**
     * Convenience constructor that creates a small dedicated thread pool.
     */
    public RedisGracefulDegradationManager(JedisPool jedisPool, DataSource dataSource) {
        this(jedisPool, dataSource, Executors.newFixedThreadPool(4,
            r -> { Thread t = new Thread(r, "degradation-io"); t.setDaemon(true); return t; }));
    }

    // ---- GracefulDegradationManager ----------------------------------------

    @Override
    public Promise<Void> setMode(String tenantId, DegradationMode mode) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(mode,     "mode");

        return Promise.ofBlocking(executor, () -> {
            // Write-through: update Redis first, then persist to Postgres
            String redisKey = KEY_PREFIX + tenantId;
            try (var jedis = jedisPool.getResource()) {
                jedis.set(redisKey, mode.name());
            } catch (Exception e) {
                log.warn("[degradation] Redis write failed for tenant={} — persisting to Postgres only", tenantId, e);
            }
            // Always write to Postgres as durable backing store
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO degradation_modes (tenant_id, mode, updated_at)
                     VALUES (?, ?, NOW())
                     ON CONFLICT (tenant_id) DO UPDATE SET mode = EXCLUDED.mode, updated_at = NOW()
                     """)) {
                ps.setString(1, tenantId);
                ps.setString(2, mode.name());
                ps.executeUpdate();
            }
            log.info("[degradation] setMode tenant={} mode={}", tenantId, mode);
            return null;
        });
    }

    @Override
    public Promise<DegradationMode> getMode(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");

        return Promise.ofBlocking(executor, () -> {
            // Try Redis first (fast path)
            try (var jedis = jedisPool.getResource()) {
                String value = jedis.get(KEY_PREFIX + tenantId);
                if (value != null) {
                    return DegradationMode.valueOf(value);
                }
            } catch (Exception e) {
                log.warn("[degradation] Redis read failed for tenant={} — falling back to Postgres", tenantId, e);
            }
            // Postgres fallback
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT mode FROM degradation_modes WHERE tenant_id = ?")) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        DegradationMode mode = DegradationMode.valueOf(rs.getString("mode"));
                        // Warm the cache
                        warmRedisCache(tenantId, mode);
                        return mode;
                    }
                }
            }
            return DegradationMode.FULL;
        });
    }

    @Override
    public Promise<Boolean> isActionAllowed(String tenantId, String actionType) {
        return getMode(tenantId).map(mode -> switch (mode) {
            case FULL    -> true;
            case OFFLINE -> false;
            default      -> ALLOWED_ACTIONS.get(mode).contains(actionType.toUpperCase());
        });
    }

    // ---- Helpers -----------------------------------------------------------

    private void warmRedisCache(String tenantId, DegradationMode mode) {
        try (var jedis = jedisPool.getResource()) {
            jedis.set(KEY_PREFIX + tenantId, mode.name());
        } catch (Exception e) {
            log.debug("[degradation] Redis cache warm failed for tenant={}", tenantId, e);
        }
    }
}
