/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.port;

import io.activej.promise.Promise;

import java.util.Set;

/**
 * Canonical authorization port for tenant-isolated RBAC operations.
 *
 * <p>This is the canonical replacement for the deprecated authorization interfaces:
 * <ul>
 *   <li>{@code com.ghatana.platform.auth.port.AuthorizationService} (async tenant-isolated)</li>
 *   <li>{@code com.ghatana.platform.auth.rbac.AuthorizationService} (sync permission-based)</li>
 * </ul>
 *
 * <p>Combines the rich async API surface from the port variant and the
 * {@code require*} / {@code hasAll*} patterns from the RBAC variant.
 *
 * <p>All operations are tenant-scoped and async ({@code Promise}-based) per ActiveJ conventions.
 *
 * @doc.type interface
 * @doc.purpose Unified RBAC authorization service port
 * @doc.layer platform
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface AuthorizationService {

    // ---- Permission Checks ----

    /**
     * Check if a user has a specific permission within a tenant.
     *
     * @param tenantId the tenant scope
     * @param userId   the user to check
     * @param permission the permission name (e.g. "documents.read")
     * @return true if the user holds the permission
     */
    Promise<Boolean> hasPermission(String tenantId, String userId, String permission);

    /**
     * Check if a user has ANY of the specified permissions.
     *
     * @param tenantId    the tenant scope
     * @param userId      the user to check
     * @param permissions the permissions to check
     * @return true if the user holds at least one
     */
    Promise<Boolean> hasAnyPermission(String tenantId, String userId, Set<String> permissions);

    /**
     * Check if a user has ALL of the specified permissions.
     *
     * @param tenantId    the tenant scope
     * @param userId      the user to check
     * @param permissions the permissions to check
     * @return true if the user holds every specified permission
     */
    Promise<Boolean> hasAllPermissions(String tenantId, String userId, Set<String> permissions);

    // ---- Role Checks ----

    /**
     * Check if a user has a specific role within a tenant.
     *
     * @param tenantId the tenant scope
     * @param userId   the user to check
     * @param role     the role name
     * @return true if the user holds the role
     */
    Promise<Boolean> hasRole(String tenantId, String userId, String role);

    /**
     * Check if a user is an admin within a tenant.
     *
     * @param tenantId the tenant scope
     * @param userId   the user to check
     * @return true if the user is an admin
     */
    Promise<Boolean> isAdmin(String tenantId, String userId);

    // ---- Queries ----

    /**
     * Get all permissions for a user within a tenant.
     *
     * @param tenantId the tenant scope
     * @param userId   the user ID
     * @return the set of permission names the user holds
     */
    Promise<Set<String>> getUserPermissions(String tenantId, String userId);

    /**
     * Get all roles for a user within a tenant.
     *
     * @param tenantId the tenant scope
     * @param userId   the user ID
     * @return the set of role names the user holds
     */
    Promise<Set<String>> getUserRoles(String tenantId, String userId);

    /**
     * Get all permissions granted by a specific role within a tenant.
     *
     * @param tenantId the tenant scope
     * @param role     the role name
     * @return the set of permission names included in the role
     */
    Promise<Set<String>> getPermissionsForRole(String tenantId, String role);

    // ---- Mutations ----

    /**
     * Grant a permission to a user within a tenant.
     *
     * @param tenantId   the tenant scope
     * @param userId     the user ID
     * @param permission the permission name to grant
     * @return true if the operation succeeded
     */
    Promise<Boolean> grantPermission(String tenantId, String userId, String permission);

    /**
     * Revoke a permission from a user within a tenant.
     *
     * @param tenantId   the tenant scope
     * @param userId     the user ID
     * @param permission the permission name to revoke
     * @return true if the operation succeeded
     */
    Promise<Boolean> revokePermission(String tenantId, String userId, String permission);

    /**
     * Grant a role to a user within a tenant.
     *
     * @param tenantId the tenant scope
     * @param userId   the user ID
     * @param role     the role name to grant
     * @return true if the operation succeeded
     */
    Promise<Boolean> grantRole(String tenantId, String userId, String role);

    /**
     * Revoke a role from a user within a tenant.
     *
     * @param tenantId the tenant scope
     * @param userId   the user ID
     * @param role     the role name to revoke
     * @return true if the operation succeeded
     */
    Promise<Boolean> revokeRole(String tenantId, String userId, String role);

    // ---- Cache ----

    /**
     * Invalidate the authorization cache for a tenant.
     *
     * @param tenantId the tenant scope
     * @return completed when the cache is cleared
     */
    Promise<Void> invalidateCache(String tenantId);
}
