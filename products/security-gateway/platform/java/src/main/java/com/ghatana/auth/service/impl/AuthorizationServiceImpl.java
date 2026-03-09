package com.ghatana.auth.service.impl;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserPrincipal;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory authorization service with role-based permission management.
 *
 * @doc.type class
 * @doc.purpose Authorization service with RBAC permission checking and caching
 * @doc.layer product
 * @doc.pattern Service
 */
public class AuthorizationServiceImpl {

    private final MetricsCollector metrics;
    private final Map<String, Set<String>> rolePermissions;
    private final Map<String, Set<String>> permissionCache = new ConcurrentHashMap<>();

    public AuthorizationServiceImpl(MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics);
        this.rolePermissions = buildDefaultRolePermissions();
    }

    public Promise<Boolean> checkPermission(TenantId tenantId, UserPrincipal userPrincipal, String permission) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(userPrincipal, "userPrincipal cannot be null");
        Objects.requireNonNull(permission, "permission cannot be null");

        boolean hasPermission = userPrincipal.getRoles().stream()
                .anyMatch(role -> getPermissionsForRoleSync(role).contains(permission));
        return Promise.of(hasPermission);
    }

    public Promise<Boolean> checkRole(TenantId tenantId, UserPrincipal userPrincipal, String role) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(userPrincipal, "userPrincipal cannot be null");
        Objects.requireNonNull(role, "role cannot be null");

        boolean hasRole = userPrincipal.getRoles().contains(role);
        return Promise.of(hasRole);
    }

    public Promise<Set<String>> getPermissionsForRole(TenantId tenantId, String role) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(role, "role cannot be null");

        Set<String> permissions = permissionCache.computeIfAbsent(role, this::getPermissionsForRoleSync);
        return Promise.of(permissions);
    }

    public Promise<Boolean> grantPermission(TenantId tenantId, String userId, String permission) {
        return Promise.of(true);
    }

    public Promise<Boolean> revokePermission(TenantId tenantId, String userId, String permission) {
        return Promise.of(true);
    }

    public Promise<Boolean> grantRole(TenantId tenantId, String userId, String role) {
        return Promise.of(true);
    }

    public Promise<Boolean> revokeRole(TenantId tenantId, String userId, String role) {
        return Promise.of(true);
    }

    public Promise<Void> invalidateCache(TenantId tenantId) {
        permissionCache.clear();
        return Promise.of((Void) null);
    }

    public int getCacheSize() {
        return permissionCache.size();
    }

    public void clearAllCaches() {
        permissionCache.clear();
    }

    private Set<String> getPermissionsForRoleSync(String role) {
        return rolePermissions.getOrDefault(role, Set.of());
    }

    private static Map<String, Set<String>> buildDefaultRolePermissions() {
        Map<String, Set<String>> map = new HashMap<>();
        map.put("ADMIN", Set.of(
                "document.read", "document.write", "document.delete",
                "user.read", "user.create", "user.update", "user.delete",
                "role.manage", "settings.admin", "audit.read", "system.config"
        ));
        map.put("EDITOR", Set.of(
                "document.read", "document.write",
                "user.read"
        ));
        map.put("VIEWER", Set.of(
                "document.read", "user.read"
        ));
        return Map.copyOf(map);
    }
}
