package com.ghatana.datacloud.entity.governance.port;

import com.ghatana.datacloud.entity.governance.Role;
import com.ghatana.datacloud.entity.governance.RoleAssignment;
import io.activej.promise.Promise;
import java.util.*;

/**
 * Port interface for role management service.
 *
 * <p>
 * <b>Purpose</b><br>
 * Orchestrates role operations across repository and catalog, providing a
 * unified interface for role management.
 *
 * @doc.type interface
 * @doc.purpose Role management service port
 * @doc.layer domain
 * @doc.pattern Port
 */
public interface RoleService {

    /**
     * Assigns a role to a principal.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal to assign role to
     * @param roleId Role to assign
     * @param assignedBy Actor performing the assignment
     * @return Promise resolving to created RoleAssignment
     */
    Promise<RoleAssignment> assignRole(String tenantId, String principalId, String roleId, String assignedBy);

    /**
     * Revokes a role from a principal.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal to revoke role from
     * @param roleId Role to revoke
     * @param revokedBy Actor performing the revocation
     * @return Promise resolving when revocation completes
     */
    Promise<Void> revokeRole(String tenantId, String principalId, String roleId, String revokedBy);

    /**
     * Gets role by ID.
     *
     * @param tenantId Tenant identifier
     * @param roleId Role identifier
     * @return Promise resolving to Optional<Role>
     */
    Promise<Optional<Role>> getRole(String tenantId, String roleId);

    /**
     * Checks if principal has a specific permission.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal to check
     * @param permission Permission required
     * @return Promise resolving to true if permission granted
     */
    Promise<Boolean> checkPermission(String tenantId, String principalId, String permission);

    /**
     * Gets all permissions for a principal.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to set of all permissions
     */
    Promise<Set<String>> getPermissions(String tenantId, String principalId);

    /**
     * Gets all active role assignments for a principal.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to list of active assignments
     */
    Promise<List<RoleAssignment>> getRoleAssignments(String tenantId, String principalId);

    /**
     * Gets all principals assigned a specific role.
     *
     * @param tenantId Tenant identifier
     * @param roleId Role to find principals for
     * @return Promise resolving to list of role assignments
     */
    Promise<List<RoleAssignment>> getPrincipalsWithRole(String tenantId, String roleId);

    /**
     * Checks if principal is an administrator.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal to check
     * @return Promise resolving to true if admin
     */
    Promise<Boolean> isAdministrator(String tenantId, String principalId);

    /**
     * Counts active role assignments in a tenant.
     *
     * @param tenantId Tenant identifier
     * @return Promise resolving to count
     */
    Promise<Long> countAssignments(String tenantId);

    /**
     * Returns role management statistics.
     *
     * @param tenantId Tenant identifier
     * @return Promise resolving to statistics map
     */
    Promise<Map<String, Object>> getStatistics(String tenantId);
}
