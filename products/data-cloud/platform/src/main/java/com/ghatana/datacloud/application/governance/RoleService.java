package com.ghatana.datacloud.application.governance;

import com.ghatana.datacloud.entity.governance.Role;
import com.ghatana.datacloud.entity.governance.RoleAssignment;
import com.ghatana.datacloud.entity.governance.port.RoleRepository;
import com.ghatana.datacloud.entity.governance.port.RoleCatalog;
import com.ghatana.datacloud.entity.governance.service.PermissionService;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * Application service for role and permission management.
 *
 * <p><b>Purpose</b><br>
 * Orchestrates role operations across domain layer (repositories, services, catalogs).
 * Provides use cases for:
 * - Creating/updating roles with validation
 * - Assigning/revoking roles to principals
 * - Permission checking across inheritance chains
 * - Audit logging for all role operations
 *
 * <p><b>Architecture</b><br>
 * RoleService depends on:
 * - RoleCatalog: Role definitions and inheritance
 * - RoleRepository: Role assignments to principals
 * - PermissionService: Permission evaluation
 *
 * <p><b>Multi-Tenant Safety</b><br>
 * All operations are tenant-scoped. Tenant ID must be extracted from security principal
 * and verified on all operations to prevent cross-tenant privilege escalation.
 *
 * <p><b>Transactionality</b><br>
 * Each operation should be treated as an atomic transaction:
 * - Role creation/update: Single write to RoleCatalog
 * - Role assignment: Single write to RoleRepository
 * - Permission check: Reads from both RoleCatalog and RoleRepository (eventual consistency)
 *
 * <p><b>Audit Trail</b><br>
 * All operations (create, update, assign, revoke) are logged with:
 * - Actor (who performed operation)
 * - Action (create/update/assign/revoke)
 * - Resource (role ID, principal ID, tenant ID)
 * - Timestamp (when operation occurred)
 * - Status (success/failure + details)
 * - Metrics (latency, cache hits, queries)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RoleService roleService = new RoleServiceImpl(roleCatalog, roleRepository, permissionService);
 *
 * // Assign role to principal
 * RoleAssignment assignment = roleService.assignRole(
 *     "tenant-1",
 *     "user-123",
 *     "editor",
 *     "admin-456"
 * );
 *
 * // Check permission
 * boolean canRead = roleService.checkPermission(
 *     "tenant-1",
 *     "user-123",
 *     "collection:read"
 * );
 *
 * // Get all principals with a role
 * List<RoleAssignment> assignments = roleService.getPrincipalsWithRole(
 *     "tenant-1",
 *     "admin"
 * );
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Role and permission management application service
 * @doc.layer application
 * @doc.pattern Service
 */
public interface RoleService {

    /**
     * Assigns a role to a principal.
     *
     * GIVEN: Valid tenant ID, principal ID, role ID, and actor
     * WHEN: assignRole() is called
     * THEN: Role assignment is created and audit event is emitted
     *
     * Pre-conditions:
     * - Role must exist in RoleCatalog
     * - Principal must not already have active assignment for that role
     * - Actor must have RBAC:MANAGE permission
     * - Tenant context must match principal's tenant
     *
     * Post-conditions:
     * - RoleAssignment is persisted and audit event emitted
     * - Permission checks immediately reflect new role
     * - Audit log includes actor, action, resource, timestamp
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal to assign role to
     * @param roleId Role to assign
     * @param grantedBy Actor performing the assignment
     * @return Promise resolving to created RoleAssignment
     * @throws IllegalArgumentException if role doesn't exist or already assigned
     * @throws NullPointerException if any parameter is null
     */
    Promise<RoleAssignment> assignRole(String tenantId, String principalId, String roleId, String grantedBy);

    /**
     * Revokes a role from a principal.
     *
     * GIVEN: Valid assignment ID and actor
     * WHEN: revokeRole() is called
     * THEN: Assignment is marked revoked and audit event emitted
     *
     * Pre-conditions:
     * - Assignment must exist and be active
     * - Actor must have RBAC:MANAGE permission
     *
     * Post-conditions:
     * - Assignment revokedAt and revokedBy are set
     * - Permission checks immediately reflect revocation
     * - Audit log includes revocation details
     *
     * Revocation is SOFT (non-destructive) - preserves audit trail.
     *
     * @param assignmentId Assignment to revoke
     * @param revokedBy Actor performing the revocation
     * @return Promise resolving to revoked RoleAssignment
     * @throws IllegalArgumentException if assignment doesn't exist or already revoked
     * @throws NullPointerException if any parameter is null
     */
    Promise<RoleAssignment> revokeRole(String assignmentId, String revokedBy);

    /**
     * Revokes all roles from a principal.
     *
     * GIVEN: Valid tenant ID, principal ID, and actor
     * WHEN: revokeAllRoles() is called
     * THEN: All active role assignments are revoked and audit events emitted
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal to revoke roles from
     * @param revokedBy Actor performing the revocation
     * @return Promise resolving to count of revoked assignments
     * @throws NullPointerException if any parameter is null
     */
    Promise<Long> revokeAllRoles(String tenantId, String principalId, String revokedBy);

    /**
     * Checks if principal has a specific permission.
     *
     * GIVEN: Valid tenant ID, principal ID, and permission
     * WHEN: checkPermission() is called
     * THEN: Returns true if principal has permission (direct or inherited)
     *
     * Permission resolution:
     * 1. Get assigned roles from RoleRepository
     * 2. Resolve permissions via PermissionService (includes inheritance)
     * 3. Check permission with wildcard support
     *
     * Result should be cached with <5 minute TTL for performance.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal to check
     * @param permission Permission required
     * @return Promise resolving to true if permission granted
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> checkPermission(String tenantId, String principalId, String permission);

    /**
     * Gets all permissions for a principal (flattened with inheritance).
     *
     * GIVEN: Valid tenant ID and principal ID
     * WHEN: getPermissions() is called
     * THEN: Returns set of all effective permissions
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to set of all permissions
     * @throws NullPointerException if any parameter is null
     */
    Promise<Set<String>> getPermissions(String tenantId, String principalId);

    /**
     * Gets all active role assignments for a principal.
     *
     * GIVEN: Valid tenant ID and principal ID
     * WHEN: getRoleAssignments() is called
     * THEN: Returns list of active role assignments
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to list of active assignments
     * @throws NullPointerException if any parameter is null
     */
    Promise<List<RoleAssignment>> getRoleAssignments(String tenantId, String principalId);

    /**
     * Gets all principals assigned a specific role.
     *
     * GIVEN: Valid tenant ID and role ID
     * WHEN: getPrincipalsWithRole() is called
     * THEN: Returns list of principals with active assignment to role
     *
     * Used for impact analysis (e.g., "who will be affected by deprecating this role?").
     *
     * @param tenantId Tenant identifier
     * @param roleId Role to find principals for
     * @return Promise resolving to list of role assignments
     * @throws NullPointerException if any parameter is null
     */
    Promise<List<RoleAssignment>> getPrincipalsWithRole(String tenantId, String roleId);

    /**
     * Creates a new role in the catalog.
     *
     * GIVEN: Valid tenant ID, role definition, and actor
     * WHEN: createRole() is called
     * THEN: Role is created and audit event is emitted
     *
     * Pre-conditions:
     * - Role ID must be unique within tenant
     * - Actor must have RBAC:MANAGE permission
     * - Role name should be descriptive
     *
     * @param tenantId Tenant identifier
     * @param role Role definition
     * @param createdBy Actor creating the role
     * @return Promise resolving to created Role
     * @throws IllegalArgumentException if role already exists or invalid
     * @throws NullPointerException if any parameter is null
     */
    Promise<Role> createRole(String tenantId, Role role, String createdBy);

    /**
     * Updates an existing role definition.
     *
     * GIVEN: Valid tenant ID, updated role, and actor
     * WHEN: updateRole() is called
     * THEN: Role is updated and audit event is emitted
     *
     * Can update:
     * - Description
     * - Permissions (add/remove)
     * - Inheritance (add/remove parent roles)
     * - Metadata
     *
     * Cannot update:
     * - Role ID (immutable)
     * - System role flag (unless admin)
     * - Creation timestamp
     *
     * @param tenantId Tenant identifier
     * @param role Updated role definition
     * @param updatedBy Actor updating the role
     * @return Promise resolving to updated Role
     * @throws IllegalArgumentException if role doesn't exist or update invalid
     * @throws NullPointerException if any parameter is null
     */
    Promise<Role> updateRole(String tenantId, Role role, String updatedBy);

    /**
     * Checks if principal is an administrator.
     *
     * GIVEN: Valid tenant ID and principal ID
     * WHEN: isAdministrator() is called
     * THEN: Returns true if principal has admin role or equivalent
     *
     * Admin status is determined by:
     * - Direct admin role OR
     * - Any role with "admin:*" permission
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal to check
     * @return Promise resolving to true if admin
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> isAdministrator(String tenantId, String principalId);

    /**
     * Counts active role assignments in a tenant.
     *
     * GIVEN: Valid tenant ID
     * WHEN: countAssignments() is called
     * THEN: Returns total count of active assignments
     *
     * @param tenantId Tenant identifier
     * @return Promise resolving to count (>= 0)
     * @throws NullPointerException if tenantId is null
     */
    Promise<Long> countAssignments(String tenantId);

    /**
     * Returns role management statistics for monitoring/debugging.
     *
     * GIVEN: Valid tenant ID
     * WHEN: getStatistics() is called
     * THEN: Returns map with:
     *   - "total_roles" (long): Total roles defined
     *   - "total_assignments" (long): Total active assignments
     *   - "unique_principals" (long): Principals with roles
     *   - "avg_roles_per_principal" (double): Average roles assigned
     *   - "max_permissions_in_role" (int): Largest role permission count
     *   - "last_role_operation" (Instant): Last create/update/assign timestamp
     *
     * @param tenantId Tenant identifier
     * @return Promise resolving to statistics map
     * @throws NullPointerException if tenantId is null
     */
    Promise<Map<String, Object>> getStatistics(String tenantId);
}
