/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.service;

import com.ghatana.appplatform.iam.rbac.Permission;
import com.ghatana.appplatform.iam.rbac.Role;
import com.ghatana.appplatform.iam.rbac.RolePermissionStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Redis-cached RBAC authorization service (STORY-K01-010).
 *
 * <p>Checks whether a principal has a given permission. The look-up order:
 * <ol>
 *   <li>Redis cache — key: {@code authz:{tenantId}:{principalId}:{resource}:{action}} (TTL 60s)</li>
 *   <li>Database via {@link RolePermissionStore} on cache miss</li>
 * </ol>
 *
 * <p>Cache is invalidated reactively whenever a {@code RoleUpdated} event is
 * consumed from K-05 — see {@link #invalidateForPrincipal(String, String)}.
 *
 * @doc.type  class
 * @doc.purpose Redis-cached RBAC authorization check for all protected endpoints (K01-010)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    /** Redis TTL for cached authorization decisions (60 seconds). */
    static final int CACHE_TTL_SECONDS = 60;

    private static final String ALLOW  = "1";
    private static final String DENY   = "0";

    private final RolePermissionStore store;
    private final JedisPool jedisPool;
    private final Executor executor;

    /**
     * @param store     RBAC role-permission store (PostgreSQL-backed in production)
     * @param jedisPool Redis pool for caching authorization decisions
     * @param executor  blocking executor for all I/O
     */
    public AuthorizationService(RolePermissionStore store,
                                JedisPool jedisPool,
                                Executor executor) {
        this.store     = Objects.requireNonNull(store,     "store");
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool");
        this.executor  = Objects.requireNonNull(executor,  "executor");
    }

    // ── Authorization check ──────────────────────────────────────────────────

    /**
     * Returns {@code true} if the principal is allowed to perform {@code action}
     * on {@code resource} within the tenant.
     *
     * <p>Result is cached in Redis for {@value CACHE_TTL_SECONDS} seconds.
     *
     * @param tenantId    tenant scope
     * @param principalId user or service-account ID
     * @param resource    resource name (e.g. {@code orders})
     * @param action      action name (e.g. {@code create})
     */
    public Promise<Boolean> isAllowed(String tenantId, String principalId,
                                      String resource, String action) {
        Objects.requireNonNull(tenantId,    "tenantId");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(resource,    "resource");
        Objects.requireNonNull(action,      "action");

        return Promise.ofBlocking(executor, () -> {
            String cacheKey = cacheKey(tenantId, principalId, resource, action);
            try (var jedis = jedisPool.getResource()) {
                String cached = jedis.get(cacheKey);
                if (cached != null) {
                    return ALLOW.equals(cached);
                }
            }
            // Cache miss — query database
            boolean allowed = store.hasPermission(tenantId, principalId, resource, action);
            cacheDecision(cacheKey, allowed);
            return allowed;
        });
    }

    /**
     * Returns all permissions for a principal, aggregated across all assigned roles.
     *
     * @param tenantId    tenant scope
     * @param principalId user or service-account ID
     */
    public Promise<List<Permission>> getPermissions(String tenantId, String principalId) {
        Objects.requireNonNull(tenantId,    "tenantId");
        Objects.requireNonNull(principalId, "principalId");

        return Promise.ofBlocking(executor, () -> {
            List<Role> roles = store.getPrincipalRoles(tenantId, principalId);
            return roles.stream()
                    .flatMap(r -> r.permissions().stream())
                    .distinct()
                    .toList();
        });
    }

    // ── Cache invalidation ───────────────────────────────────────────────────

    /**
     * Invalidates all cached authorization decisions for a principal.
     * Called when a {@code RoleUpdated} or {@code RoleRevoked} event is received.
     *
     * @param tenantId    tenant scope
     * @param principalId user or service-account ID whose permissions changed
     */
    public void invalidateForPrincipal(String tenantId, String principalId) {
        Objects.requireNonNull(tenantId,    "tenantId");
        Objects.requireNonNull(principalId, "principalId");

        String pattern = "authz:" + tenantId + ":" + principalId + ":*";
        try (var jedis = jedisPool.getResource()) {
            var keys = jedis.keys(pattern);
            if (!keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
                log.debug("Invalidated {} authz cache entries for principal={}", keys.size(), principalId);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate authz cache for principal={}: {}", principalId, e.getMessage());
        }
    }

    /**
     * Invalidates all cached authorization decisions for all principals in a tenant.
     * Used when a role definition changes.
     *
     * @param tenantId tenant scope
     */
    public void invalidateForTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");

        String pattern = "authz:" + tenantId + ":*";
        try (var jedis = jedisPool.getResource()) {
            var keys = jedis.keys(pattern);
            if (!keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
                log.debug("Invalidated {} authz cache entries for tenant={}", keys.size(), tenantId);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate authz cache for tenant={}: {}", tenantId, e.getMessage());
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private static String cacheKey(String tenantId, String principalId,
                                   String resource, String action) {
        return "authz:" + tenantId + ":" + principalId + ":" + resource + ":" + action;
    }

    private void cacheDecision(String key, boolean allowed) {
        try (var jedis = jedisPool.getResource()) {
            jedis.setex(key, CACHE_TTL_SECONDS, allowed ? ALLOW : DENY);
        } catch (Exception e) {
            log.warn("Failed to cache authz decision for key={}: {}", key, e.getMessage());
        }
    }
}
