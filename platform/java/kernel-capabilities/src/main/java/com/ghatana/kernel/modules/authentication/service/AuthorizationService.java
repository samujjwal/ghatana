/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.authentication.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.modules.authentication.domain.Permission;
import com.ghatana.kernel.modules.authentication.domain.Role;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Generic authorization service.
 *
 * <p>Provides product-agnostic role-based access control (RBAC) capabilities.
 * This service contains NO finance-specific logic and can be reused
 * across all products in the Ghatana ecosystem.</p>
 *
 * @doc.type class
 * @doc.purpose Generic RBAC authorization service - role-based access control
 * @doc.layer kernel
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    private final KernelContext context;
    private final Executor executor;
    private volatile boolean started = false;

    // In-memory cache for authorization decisions
    private final ConcurrentHashMap<String, Boolean> authzCache = new ConcurrentHashMap<>();

    /**
     * Creates a new authorization service.
     *
     * @param context the kernel context
     */
    public AuthorizationService(KernelContext context) {
        this.context = context;
        this.executor = context.getExecutor("authorization");
    }

    /**
     * Starts the authorization service.
     */
    public void start() {
        log.info("Starting authorization service");
        started = true;
        log.info("Authorization service started");
    }

    /**
     * Stops the authorization service.
     */
    public void stop() {
        log.info("Stopping authorization service");
        authzCache.clear();
        started = false;
        log.info("Authorization service stopped");
    }

    /**
     * Checks if the service is healthy.
     *
     * @return true if healthy
     */
    public boolean isHealthy() {
        return started;
    }

    /**
     * Checks if a principal is allowed to perform an action on a resource.
     *
     * @param tenantId    tenant identifier
     * @param principalId principal identifier
     * @param resource    resource name
     * @param action      action name
     * @return Promise containing authorization decision
     */
    public Promise<Boolean> isAllowed(String tenantId, String principalId, String resource, String action) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Authorization service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            log.debug("Checking authorization: tenant={}, principal={}, resource={}, action={}",
                tenantId, principalId, resource, action);

            String cacheKey = cacheKey(tenantId, principalId, resource, action);
            
            // Check cache first
            return authzCache.computeIfAbsent(cacheKey, key -> {
                // Cache miss - perform authorization check
                return performAuthorizationCheck(tenantId, principalId, resource, action);
            });
        });
    }

    /**
     * Gets all permissions for a principal.
     *
     * @param tenantId    tenant identifier
     * @param principalId principal identifier
     * @return Promise containing list of permissions
     */
    public Promise<List<Permission>> getPermissions(String tenantId, String principalId) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Authorization service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            log.debug("Getting permissions for: tenant={}, principal={}", tenantId, principalId);

            // Get principal's roles
            Set<Role> roles = getPrincipalRoles(tenantId, principalId);
            
            // Aggregate permissions from all roles
            return roles.stream()
                    .flatMap(role -> role.getPermissions().stream())
                    .distinct()
                    .toList();
        });
    }

    /**
     * Gets all roles for a principal.
     *
     * @param tenantId    tenant identifier
     * @param principalId principal identifier
     * @return Promise containing set of roles
     */
    public Promise<Set<Role>> getRoles(String tenantId, String principalId) {
        if (!started) {
            return Promise.ofException(new IllegalStateException("Authorization service not started"));
        }

        return Promise.ofBlocking(executor, () -> {
            log.debug("Getting roles for: tenant={}, principal={}", tenantId, principalId);
            return getPrincipalRoles(tenantId, principalId);
        });
    }

    /**
     * Invalidates cached authorization decisions for a principal.
     *
     * @param tenantId    tenant identifier
     * @param principalId principal identifier
     */
    public void invalidateForPrincipal(String tenantId, String principalId) {
        log.debug("Invalidating authorization cache for: tenant={}, principal={}", tenantId, principalId);

        String pattern = "authz:" + tenantId + ":" + principalId + ":*";
        authzCache.entrySet().removeIf(entry -> entry.getKey().startsWith(pattern));
        
        log.info("Invalidated authorization cache entries for principal: {}", principalId);
    }

    /**
     * Invalidates all cached authorization decisions for a tenant.
     *
     * @param tenantId tenant identifier
     */
    public void invalidateForTenant(String tenantId) {
        log.debug("Invalidating authorization cache for tenant: {}", tenantId);

        String pattern = "authz:" + tenantId + ":*";
        authzCache.entrySet().removeIf(entry -> entry.getKey().startsWith(pattern));
        
        log.info("Invalidated authorization cache entries for tenant: {}", tenantId);
    }

    // ==================== Private Methods ====================

    private boolean performAuthorizationCheck(String tenantId, String principalId, String resource, String action) {
        // Get principal's permissions
        Set<Role> roles = getPrincipalRoles(tenantId, principalId);
        
        // Check if any role grants the required permission
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission.matches(resource, action));
    }

    private Set<Role> getPrincipalRoles(String tenantId, String principalId) {
        // Generic role retrieval - would integrate with data storage capability
        return context.getCapability("data.storage")
            .map(storage -> retrieveRolesFromStorage(storage, tenantId, principalId))
            .orElse(Set.of());
    }

    private Set<Role> retrieveRolesFromStorage(Object storage, String tenantId, String principalId) {
        // Integration with data storage capability
        // This would query the storage for the principal's roles
        // For now, return a basic authenticated user role
        Set<Permission> permissions = Set.of(
            Permission.builder().resource("user").action("profile").build(),
            Permission.builder().resource("user").action("update").build()
        );
        Role authenticatedRole = Role.builder()
            .name("authenticated")
            .permissions(permissions)
            .build();
        return Set.of(authenticatedRole);
    }

    private static String cacheKey(String tenantId, String principalId, String resource, String action) {
        return "authz:" + tenantId + ":" + principalId + ":" + resource + ":" + action;
    }
}
