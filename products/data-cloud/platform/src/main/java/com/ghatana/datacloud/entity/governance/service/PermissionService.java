package com.ghatana.datacloud.entity.governance.service;

import com.ghatana.datacloud.entity.governance.Role;
import com.ghatana.datacloud.entity.governance.RoleAssignment;
import io.activej.promise.Promise;
import java.util.*;

/**
 * Domain service for permission checking and authorization.
 *
 * <p><b>Purpose</b><br>
 * Provides centralized permission evaluation logic including:
 * - Direct permission checking (role → permission)
 * - Inherited permission resolution (role inheritance chains)
 * - Wildcard permission matching
 * - Multi-tenant permission isolation
 * - Audit-safe permission verification
 *
 * <p><b>Architecture</b><br>
 * Service composes two ports:
 * - RoleRepository: Role assignment lookup
 * - RoleCatalog: Role metadata and inheritance structure
 *
 * All permission checks are performed against CURRENT state (eventual consistency).
 * Cached results should be short-lived (<5 minutes) to reflect revocations.
 *
 * <p><b>Permission Format</b><br>
 * Permissions are "resource:action" pairs with wildcard support:
 * - "collection:read" - specific permission
 * - "collection:*" - all actions on collection resource
 * - "admin:*" - administrator full access (special case)
 *
 * <p><b>Multi-Tenant Security</b><br>
 * All permission checks MUST include tenantId parameter. Implementation enforces
 * tenant isolation at every layer to prevent cross-tenant authorization.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PermissionService permissionService = new PermissionServiceImpl(roleRepo, roleCatalog);
 *
 * // Check if principal has permission
 * boolean canRead = Promises.await(
 *     permissionService.checkPermission(
 *         "tenant-1",
 *         "user-123",
 *         "collection:read"
 *     )
 * );
 *
 * // Get all permissions for principal (flattened with inheritance)
 * Set<String> permissions = Promises.await(
 *     permissionService.getAllPermissions("tenant-1", "user-123")
 * );
 * }</pre>
 *
 * <p><b>Performance Characteristics</b><br>
 * - Single permission check: O(r) where r = number of assigned roles (typically <10)
 * - Inheritance resolution: O(r × h) where h = inheritance depth (typically <5)
 * - Full permission resolution: O(r × (h + p)) where p = permissions per role
 *
 * Recommend caching results with <5 minute TTL to reduce database queries.
 *
 * @see Role
 * @see RoleAssignment
 * @see RoleCatalog
 * @doc.type interface
 * @doc.purpose Domain service for permission checking and authorization
 * @doc.layer domain
 * @doc.pattern Service
 */
public interface PermissionService {

    /**
     * Checks if principal has a specific permission.
     *
     * GIVEN: Valid tenant ID, principal ID, and permission string
     * WHEN: checkPermission() is called
     * THEN: Returns true if principal (directly or via inheritance) has permission
     *
     * Permission matching logic:
     * 1. Check direct permissions in assigned roles
     * 2. If not found, traverse inheritance chain recursively
     * 3. Support wildcard matching: "admin:*" matches "admin:anything"
     *
     * @param tenantId Tenant identifier (enforces isolation)
     * @param principalId Principal identifier (user, service account, group)
     * @param permission Permission to check (format: "resource:action")
     * @return Promise resolving to true if permission granted, false otherwise
     * @throws IllegalArgumentException if permission format is invalid
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> checkPermission(String tenantId, String principalId, String permission);

    /**
     * Checks if principal has ANY of the specified permissions.
     *
     * GIVEN: Valid tenant ID, principal ID, and collection of permissions
     * WHEN: checkAnyPermission() is called
     * THEN: Returns true if principal has at least one of the permissions
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @param permissions Collection of permissions to check
     * @return Promise resolving to true if any permission matches
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> checkAnyPermission(String tenantId, String principalId, Collection<String> permissions);

    /**
     * Checks if principal has ALL of the specified permissions.
     *
     * GIVEN: Valid tenant ID, principal ID, and collection of permissions
     * WHEN: checkAllPermissions() is called
     * THEN: Returns true only if principal has all specified permissions
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @param permissions Collection of permissions required
     * @return Promise resolving to true if all permissions granted
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> checkAllPermissions(String tenantId, String principalId, Collection<String> permissions);

    /**
     * Returns all effective permissions for a principal (flattened with inheritance).
     *
     * GIVEN: Valid tenant ID and principal ID
     * WHEN: getAllPermissions() is called
     * THEN: Returns set of all permissions (direct + inherited, with duplicates removed)
     *
     * Resolves inheritance chain:
     * 1. Get all roles assigned to principal (active only)
     * 2. For each role, recursively resolve inherited roles
     * 3. Collect all permissions from all roles
     * 4. Return deduplicated set
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to set of all effective permissions
     * @throws NullPointerException if any parameter is null
     */
    Promise<Set<String>> getAllPermissions(String tenantId, String principalId);

    /**
     * Returns all active roles assigned to a principal.
     *
     * GIVEN: Valid tenant ID and principal ID
     * WHEN: getAssignedRoles() is called
     * THEN: Returns list of active role assignments for principal
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to list of active role assignments
     * @throws NullPointerException if any parameter is null
     */
    Promise<List<RoleAssignment>> getAssignedRoles(String tenantId, String principalId);

    /**
     * Returns all roles in principal's permission chain (direct + inherited).
     *
     * GIVEN: Valid tenant ID and principal ID
     * WHEN: getEffectiveRoles() is called
     * THEN: Returns set of all roles (direct assignment + inheritance chain)
     *
     * Resolves the complete role hierarchy:
     * 1. Get directly assigned roles
     * 2. For each assigned role, recursively resolve inherited roles
     * 3. Return deduplicated set of all role IDs
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to set of role IDs (direct + inherited)
     * @throws NullPointerException if any parameter is null
     */
    Promise<Set<String>> getEffectiveRoles(String tenantId, String principalId);

    /**
     * Checks if principal is an administrator.
     *
     * GIVEN: Valid tenant ID and principal ID
     * WHEN: isAdministrator() is called
     * THEN: Returns true if principal has 'admin' role or equivalent
     *
     * Admin status is determined by:
     * - Direct admin role assignment OR
     * - Any role with "admin:*" permission
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to true if principal is admin
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> isAdministrator(String tenantId, String principalId);

    /**
     * Validates that permission string format is valid.
     *
     * GIVEN: Permission string
     * WHEN: validatePermissionFormat() is called
     * THEN: Returns true if format matches "resource:action" pattern
     *
     * Valid examples:
     * - "collection:read" ✓
     * - "collection:*" ✓
     * - "admin:*" ✓
     * - "invalid-format" ✗
     * - "" ✗
     * - null ✗
     *
     * @param permission Permission string to validate
     * @return true if format is valid
     */
    boolean validatePermissionFormat(String permission);

    /**
     * Validates that role ID is valid.
     *
     * GIVEN: Role ID string
     * WHEN: validateRoleId() is called
     * THEN: Returns true if format is valid (non-empty, alphanumeric + hyphens)
     *
     * @param roleId Role identifier to validate
     * @return true if format is valid
     */
    boolean validateRoleId(String roleId);

    /**
     * Returns debug information for permission resolution (internal use only).
     *
     * GIVEN: Valid tenant ID and principal ID
     * WHEN: debugPermissions() is called
     * THEN: Returns structured map showing:
     *   - "assigned_roles" (List<String>): Directly assigned roles
     *   - "effective_roles" (List<String>): Roles from inheritance chain
     *   - "all_permissions" (Set<String>): Flattened permissions
     *   - "resolved_at" (Instant): When resolution was performed
     *   - "inheritance_depth" (int): Maximum inheritance depth
     *   - "total_permissions_evaluated" (int): Count of permission lookups
     *
     * Used for audit logging and troubleshooting permission issues.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to debug information map
     * @throws NullPointerException if any parameter is null
     */
    Promise<Map<String, Object>> debugPermissions(String tenantId, String principalId);
}
