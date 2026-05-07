/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.security;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service interface for role-based access control (RBAC).
 *
 * @doc.type interface
 * @doc.purpose Role-based access control management
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface RBACService {

    /**
     * Check if user has permission.
     *
     * @param userId user identifier
     * @param tenantId tenant identifier
     * @param permission permission to check
     * @param resource resource identifier (optional)
     * @return promise of true if permitted
     */
    Promise<Boolean> hasPermission(String userId, String tenantId, Permission permission, String resource);

    /**
     * Get all permissions for user.
     *
     * @param userId user identifier
     * @param tenantId tenant identifier
     * @return promise of permission set
     */
    Promise<Set<Permission>> getUserPermissions(String userId, String tenantId);

    /**
     * Assign role to user.
     *
     * @param userId user identifier
     * @param tenantId tenant identifier
     * @param roleId role identifier
     * @return promise completing when assigned
     */
    Promise<Void> assignRole(String userId, String tenantId, String roleId);

    /**
     * Remove role from user.
     *
     * @param userId user identifier
     * @param tenantId tenant identifier
     * @param roleId role identifier
     * @return promise completing when removed
     */
    Promise<Void> revokeRole(String userId, String tenantId, String roleId);

    /**
     * Create or update role.
     *
     * @param role role definition
     * @return promise of saved role
     */
    Promise<Role> saveRole(Role role);

    /**
     * Get role by ID.
     *
     * @param roleId role identifier
     * @return promise of role if found
     */
    Promise<Optional<Role>> getRole(String roleId);

    /**
     * List roles for tenant.
     *
     * @param tenantId tenant identifier
     * @return promise of role list
     */
    Promise<List<Role>> listRoles(String tenantId);

    /**
     * Delete role.
     *
     * @param roleId role identifier
     * @return promise completing when deleted
     */
    Promise<Void> deleteRole(String roleId);

    /**
     * Get users with role.
     *
     * @param roleId role identifier
     * @return promise of user list
     */
    Promise<List<String>> getUsersWithRole(String roleId);

    /**
     * Get roles for user.
     *
     * @param userId user identifier
     * @param tenantId tenant identifier
     * @return promise of role list
     */
    Promise<List<Role>> getUserRoles(String userId, String tenantId);

    /**
     * Permission definition.
     */
    enum Permission {
        // Entity permissions
        ENTITY_READ, ENTITY_CREATE, ENTITY_UPDATE, ENTITY_DELETE, ENTITY_ADMIN,

        // Collection permissions
        COLLECTION_READ, COLLECTION_CREATE, COLLECTION_UPDATE, COLLECTION_DELETE, COLLECTION_ADMIN,

        // Event permissions
        EVENT_READ, EVENT_PUBLISH, EVENT_SUBSCRIBE, EVENT_ADMIN,

        // Report permissions
        REPORT_READ, REPORT_CREATE, REPORT_DELETE, REPORT_ADMIN,

        // System permissions
        USER_READ, USER_MANAGE, ROLE_READ, ROLE_MANAGE,
        AUDIT_READ, AUDIT_EXPORT,
        SETTINGS_READ, SETTINGS_WRITE,
        TENANT_ADMIN, SUPER_ADMIN
    }

    /**
     * Role definition.
     */
    record Role(
        String id,
        String name,
        String description,
        String tenantId,
        Set<Permission> permissions,
        List<String> inheritsFrom,
        boolean isSystemRole,
        int priority
    ) {
        /**
         * Check if role has permission.
         */
        public boolean hasPermission(Permission permission) {
            return permissions.contains(permission);
        }
    }
}
