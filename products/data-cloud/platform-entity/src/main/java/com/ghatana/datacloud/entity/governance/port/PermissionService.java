package com.ghatana.datacloud.entity.governance.port;

import com.ghatana.datacloud.entity.governance.Role;
import com.ghatana.datacloud.entity.governance.RoleAssignment;
import io.activej.promise.Promise;
import java.util.*;

/**
 * Port interface for permission checking and authorization.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides centralized permission evaluation logic including: - Direct
 * permission checking - Inherited permission resolution - Wildcard permission
 * matching
 *
 * @doc.type interface
 * @doc.purpose Permission checking port
 * @doc.layer domain
 * @doc.pattern Port
 */
public interface PermissionService {

    /**
     * Checks if principal has a specific permission.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @param permission Permission to check
     * @return Promise resolving to true if permission granted
     */
    Promise<Boolean> checkPermission(String tenantId, String principalId, String permission);

    /**
     * Checks if principal has ANY of the specified permissions.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @param permissions Collection of permissions to check
     * @return Promise resolving to true if any permission matches
     */
    Promise<Boolean> checkAnyPermission(String tenantId, String principalId, Collection<String> permissions);

    /**
     * Checks if principal has ALL of the specified permissions.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @param permissions Collection of permissions required
     * @return Promise resolving to true if all permissions granted
     */
    Promise<Boolean> checkAllPermissions(String tenantId, String principalId, Collection<String> permissions);

    /**
     * Returns all effective permissions for a principal.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to set of all effective permissions
     */
    Promise<Set<String>> getAllPermissions(String tenantId, String principalId);

    /**
     * Returns all active roles assigned to a principal.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to list of active role assignments
     */
    Promise<List<RoleAssignment>> getAssignedRoles(String tenantId, String principalId);

    /**
     * Returns all roles in principal's permission chain.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to set of role IDs
     */
    Promise<Set<String>> getEffectiveRoles(String tenantId, String principalId);

    /**
     * Checks if principal is an administrator.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to true if principal is admin
     */
    Promise<Boolean> isAdministrator(String tenantId, String principalId);

    /**
     * Adds permissions to a role.
     *
     * @param tenantId Tenant identifier
     * @param roleId Role identifier
     * @param permissions Permissions to add
     * @return Promise resolving to Optional<Role>
     */
    Promise<Optional<Role>> addPermissionsToRole(String tenantId, String roleId, Set<String> permissions);
}
