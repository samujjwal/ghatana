package com.ghatana.datacloud.entity.governance.port;

import com.ghatana.datacloud.entity.governance.Role;
import io.activej.promise.Promise;
import java.util.*;

/**
 * Port interface for managing role definitions and metadata.
 *
 * <p><b>Purpose</b><br>
 * Central catalog of all roles available in a tenant. Provides:
 * - Role CRUD operations
 * - Role discovery and enumeration
 * - Role inheritance graph traversal
 * - Role versioning and activation
 *
 * <p><b>Multi-Tenant Isolation</b><br>
 * All operations are tenant-scoped. Tenants cannot access or modify roles outside
 * their tenant. System roles (isSystemRole=true) cannot be deleted or deactivated.
 *
 * <p><b>Role Lifecycle</b><br>
 * Roles follow this state machine:
 * 1. CREATED: New role, inactive
 * 2. ACTIVE: Role can be assigned to principals
 * 3. DEPRECATED: Role not assigned to new principals, but existing assignments remain
 * 4. DELETED: Role and all assignments are removed (irreversible)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RoleCatalog catalog = new JpaRoleCatalog(entityManager);
 *
 * // Create a role
 * Role admin = Role.builder("admin")
 *     .permission("collection:*")
 *     .permission("audit:*")
 *     .asSystemRole()
 *     .build();
 * Role savedRole = Promises.await(catalog.createRole("tenant-1", admin));
 *
 * // Find by ID
 * Optional<Role> role = Promises.await(catalog.findById("tenant-1", "admin"));
 *
 * // Get all active roles
 * List<Role> activeRoles = Promises.await(catalog.findAllActive("tenant-1"));
 *
 * // Resolve inheritance
 * Set<String> allPermissions = Promises.await(
 *     catalog.resolvePermissions("tenant-1", "editor")
 * );
 * }</pre>
 *
 * <p><b>Permission Resolution with Inheritance</b><br>
 * Roles support inheritance: a role can inherit permissions from other roles.
 * Inheritance is recursive and supports multiple levels. Circular dependencies
 * are prevented by implementation.
 *
 * @see Role
 * @see RoleAssignment
 * @doc.type interface
 * @doc.purpose Role catalog and definition management
 * @doc.layer domain
 * @doc.pattern Port
 */
public interface RoleCatalog {

    /**
     * Creates a new role in the tenant catalog.
     *
     * GIVEN: Valid tenant ID and Role with unique roleId
     * WHEN: createRole() is called
     * THEN: Role is persisted and returned with creation timestamp
     *
     * @param tenantId Tenant identifier
     * @param role Role definition with roleId, name, permissions
     * @return Promise resolving to created role
     * @throws IllegalArgumentException if role already exists or roleId is invalid
     * @throws NullPointerException if tenantId or role is null
     */
    Promise<Role> createRole(String tenantId, Role role);

    /**
     * Updates an existing role (all fields except roleId).
     *
     * GIVEN: Valid tenant ID and updated Role
     * WHEN: updateRole() is called
     * THEN: Role is updated and returned with new updatedAt timestamp
     *
     * System roles can be modified but not deleted.
     *
     * @param tenantId Tenant identifier
     * @param role Updated role definition
     * @return Promise resolving to updated role
     * @throws IllegalArgumentException if role doesn't exist or roleId is invalid
     * @throws NullPointerException if tenantId or role is null
     */
    Promise<Role> updateRole(String tenantId, Role role);

    /**
     * Adds a permission to an existing role.
     *
     * GIVEN: Valid tenant ID, role ID, and permission string
     * WHEN: addPermission() is called
     * THEN: Permission is added (if not already present) and role is returned
     *
     * @param tenantId Tenant identifier
     * @param roleId Role identifier
     * @param permission Permission to add (format: "resource:action")
     * @return Promise resolving to updated role
     * @throws IllegalArgumentException if role doesn't exist or permission format invalid
     * @throws NullPointerException if any parameter is null
     */
    Promise<Role> addPermission(String tenantId, String roleId, String permission);

    /**
     * Removes a permission from a role.
     *
     * GIVEN: Valid tenant ID, role ID, and permission string
     * WHEN: removePermission() is called
     * THEN: Permission is removed and role is returned
     *
     * @param tenantId Tenant identifier
     * @param roleId Role identifier
     * @param permission Permission to remove
     * @return Promise resolving to updated role
     * @throws IllegalArgumentException if role doesn't exist
     * @throws NullPointerException if any parameter is null
     */
    Promise<Role> removePermission(String tenantId, String roleId, String permission);

    /**
     * Makes a role inherit permissions from another role.
     *
     * GIVEN: Valid tenant ID, child role ID, and parent role ID
     * WHEN: addInheritance() is called
     * THEN: Parent role is added to inheritance chain
     *
     * Circular dependencies are prevented by implementation.
     *
     * @param tenantId Tenant identifier
     * @param roleId Role to inherit permissions
     * @param parentRoleId Parent role to inherit from
     * @return Promise resolving to updated role
     * @throws IllegalArgumentException if roles don't exist or cycle would be created
     * @throws NullPointerException if any parameter is null
     */
    Promise<Role> addInheritance(String tenantId, String roleId, String parentRoleId);

    /**
     * Removes role inheritance.
     *
     * GIVEN: Valid tenant ID, child role ID, and parent role ID
     * WHEN: removeInheritance() is called
     * THEN: Parent role is removed from inheritance chain
     *
     * @param tenantId Tenant identifier
     * @param roleId Role to remove inheritance from
     * @param parentRoleId Parent role to remove
     * @return Promise resolving to updated role
     * @throws IllegalArgumentException if roles don't exist or inheritance doesn't exist
     * @throws NullPointerException if any parameter is null
     */
    Promise<Role> removeInheritance(String tenantId, String roleId, String parentRoleId);

    /**
     * Finds role by ID.
     *
     * GIVEN: Valid tenant ID and role ID
     * WHEN: findById() is called
     * THEN: Returns role if exists, empty if not found
     *
     * @param tenantId Tenant identifier
     * @param roleId Role identifier
     * @return Promise resolving to Optional<Role>
     * @throws NullPointerException if tenantId or roleId is null
     */
    Promise<Optional<Role>> findById(String tenantId, String roleId);

    /**
     * Finds role by name (case-sensitive).
     *
     * GIVEN: Valid tenant ID and role name
     * WHEN: findByName() is called
     * THEN: Returns role if name matches, empty if not found
     *
     * @param tenantId Tenant identifier
     * @param name Role name to search
     * @return Promise resolving to Optional<Role>
     * @throws NullPointerException if tenantId or name is null
     */
    Promise<Optional<Role>> findByName(String tenantId, String name);

    /**
     * Returns all active roles in a tenant.
     *
     * GIVEN: Valid tenant ID
     * WHEN: findAllActive() is called
     * THEN: Returns list of all active (non-deleted) roles, empty if none
     *
     * @param tenantId Tenant identifier
     * @return Promise resolving to list of active roles
     * @throws NullPointerException if tenantId is null
     */
    Promise<List<Role>> findAllActive(String tenantId);

    /**
     * Returns all system roles in a tenant (roles that cannot be deleted).
     *
     * GIVEN: Valid tenant ID
     * WHEN: findAllSystemRoles() is called
     * THEN: Returns list of all system roles, typically: admin, viewer, editor
     *
     * @param tenantId Tenant identifier
     * @return Promise resolving to list of system roles
     * @throws NullPointerException if tenantId is null
     */
    Promise<List<Role>> findAllSystemRoles(String tenantId);

    /**
     * Resolves all effective permissions for a role (direct + inherited).
     *
     * GIVEN: Valid tenant ID and role ID
     * WHEN: resolvePermissions() is called
     * THEN: Returns set of all permissions (direct + inherited via inheritance chain)
     *
     * Inheritance is resolved recursively:
     * 1. Get direct permissions from role
     * 2. For each inherited role, recursively resolve its permissions
     * 3. Return deduplicated set
     *
     * @param tenantId Tenant identifier
     * @param roleId Role identifier
     * @return Promise resolving to set of effective permissions
     * @throws NullPointerException if any parameter is null
     */
    Promise<Set<String>> resolvePermissions(String tenantId, String roleId);

    /**
     * Resolves all roles in the inheritance chain for a role.
     *
     * GIVEN: Valid tenant ID and role ID
     * WHEN: resolveInheritedRoles() is called
     * THEN: Returns set of all roles (direct + inherited recursively)
     *
     * @param tenantId Tenant identifier
     * @param roleId Role identifier
     * @return Promise resolving to set of role IDs
     * @throws NullPointerException if any parameter is null
     */
    Promise<Set<String>> resolveInheritedRoles(String tenantId, String roleId);

    /**
     * Checks if role has a specific permission (direct or inherited).
     *
     * GIVEN: Valid tenant ID, role ID, and permission
     * WHEN: hasPermission() is called
     * THEN: Returns true if role has permission directly or via inheritance
     *
     * @param tenantId Tenant identifier
     * @param roleId Role identifier
     * @param permission Permission to check
     * @return Promise resolving to true if permission exists
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> hasPermission(String tenantId, String roleId, String permission);

    /**
     * Checks for circular dependency before adding inheritance.
     *
     * GIVEN: Valid tenant ID, child role ID, and parent role ID
     * WHEN: wouldCreateCycle() is called
     * THEN: Returns true if adding inheritance would create a cycle
     *
     * @param tenantId Tenant identifier
     * @param roleId Role to inherit permissions
     * @param parentRoleId Potential parent role
     * @return Promise resolving to true if cycle would be created
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> wouldCreateCycle(String tenantId, String roleId, String parentRoleId);

    /**
     * Deletes a role permanently.
     *
     * GIVEN: Valid tenant ID and role ID
     * WHEN: deleteRole() is called
     * THEN: Role is permanently removed
     *
     * WARNING: Cannot delete system roles. All role assignments are also deleted.
     *
     * @param tenantId Tenant identifier
     * @param roleId Role identifier
     * @return Promise resolving when delete completes
     * @throws IllegalArgumentException if role doesn't exist or is system role
     * @throws NullPointerException if any parameter is null
     */
    Promise<Void> deleteRole(String tenantId, String roleId);

    /**
     * Returns count of roles in a tenant.
     *
     * GIVEN: Valid tenant ID
     * WHEN: countRoles() is called
     * THEN: Returns total count of roles (active + inactive)
     *
     * @param tenantId Tenant identifier
     * @return Promise resolving to count (>= 0)
     * @throws NullPointerException if tenantId is null
     */
    Promise<Long> countRoles(String tenantId);

    /**
     * Returns storage statistics for monitoring/debugging.
     *
     * GIVEN: Valid tenant ID
     * WHEN: getStatistics() is called
     * THEN: Returns map with:
     *   - "total_roles" (long): Total roles in tenant
     *   - "system_roles" (long): System roles count
     *   - "active_roles" (long): Active roles count
     *   - "total_permissions" (long): Total unique permissions in tenant
     *   - "max_inheritance_depth" (int): Maximum inheritance chain depth
     *   - "created_at" (Instant): Catalog creation time
     *
     * @param tenantId Tenant identifier
     * @return Promise resolving to statistics map
     * @throws NullPointerException if tenantId is null
     */
    Promise<Map<String, Object>> getStatistics(String tenantId);
}
