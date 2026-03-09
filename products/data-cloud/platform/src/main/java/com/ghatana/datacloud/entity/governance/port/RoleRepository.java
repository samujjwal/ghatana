package com.ghatana.datacloud.entity.governance.port;

import com.ghatana.datacloud.entity.governance.RoleAssignment;
import io.activej.promise.Promise;
import java.util.*;

/**
 * Port interface for persisting and querying role assignments.
 *
 * <p><b>Purpose</b><br>
 * Decouples role assignment persistence from specific storage implementations (JPA, NoSQL, etc.).
 * Enables unit testing via in-memory implementations and supports multiple storage backends.
 *
 * <p><b>Multi-Tenant Isolation</b><br>
 * All operations are tenant-scoped. Queries implicitly filter by tenant context extracted from
 * security principal. Implementation MUST enforce tenant isolation on every query.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RoleRepository repo = new JpaRoleAssignmentRepository(entityManager);
 *
 * // Save assignment
 * RoleAssignment assignment = RoleAssignment.builder()
 *     .tenantId("tenant-1")
 *     .principalId("user-123")
 *     .roleId("editor")
 *     .build();
 * RoleAssignment saved = Promises.await(repo.save(assignment));
 *
 * // Find active assignments for principal
 * List<RoleAssignment> assignments = Promises.await(
 *     repo.findActiveByTenantAndPrincipal("tenant-1", "user-123")
 * );
 * }</pre>
 *
 * <p><b>Consistency Model</b><br>
 * - Create/update: Immediately visible in same transaction
 * - Revoke: Visible after transaction commit (EventCloud audit event emitted)
 * - Query: Returns committed state (no uncommitted reads)
 * - Concurrent revokes: Last-write-wins (timestamp-based)
 *
 * @see RoleAssignment
 * @see PermissionService
 * @doc.type interface
 * @doc.purpose Role assignment persistence port
 * @doc.layer domain
 * @doc.pattern Port
 */
public interface RoleRepository {

    /**
     * Saves a new role assignment.
     *
     * GIVEN: Valid RoleAssignment with assignmentId, tenantId, principalId, roleId, grantedAt, grantedBy
     * WHEN: save() is called
     * THEN: Assignment is persisted and returned with same properties
     *
     * @param assignment Role assignment to save
     * @return Promise resolving to saved assignment
     * @throws IllegalArgumentException if assignment has assignmentId that already exists
     * @throws NullPointerException if assignment is null
     */
    Promise<RoleAssignment> save(RoleAssignment assignment);

    /**
     * Updates an existing role assignment (non-revocation fields only).
     *
     * GIVEN: Valid RoleAssignment with existing assignmentId
     * WHEN: update() is called
     * THEN: Only metadata and non-audit fields are updated, grantedAt/grantedBy unchanged
     *
     * @param assignment Assignment with updated metadata
     * @return Promise resolving to updated assignment
     * @throws IllegalArgumentException if assignment doesn't exist or is revoked
     * @throws NullPointerException if assignment is null
     */
    Promise<RoleAssignment> update(RoleAssignment assignment);

    /**
     * Revokes a role assignment (marks revoked without deleting).
     *
     * GIVEN: Valid assignment ID and revocation details
     * WHEN: revoke() is called
     * THEN: revokedAt and revokedBy are set, isActive() returns false
     *
     * @param assignmentId Assignment to revoke
     * @param revokedBy Principal revoking the role
     * @return Promise resolving to revoked assignment
     * @throws IllegalArgumentException if assignment doesn't exist or already revoked
     * @throws NullPointerException if assignmentId or revokedBy is null
     */
    Promise<RoleAssignment> revoke(String assignmentId, String revokedBy);

    /**
     * Finds assignment by ID.
     *
     * GIVEN: Valid assignment ID
     * WHEN: findById() is called
     * THEN: Returns assignment if exists (active or revoked), empty if not found
     *
     * @param assignmentId Assignment ID to find
     * @return Promise resolving to Optional<RoleAssignment>
     * @throws NullPointerException if assignmentId is null
     */
    Promise<Optional<RoleAssignment>> findById(String assignmentId);

    /**
     * Finds all active assignments for a principal in a tenant.
     *
     * GIVEN: Valid tenant ID and principal ID
     * WHEN: findActiveByTenantAndPrincipal() is called
     * THEN: Returns list of all active (non-revoked) assignments for principal, empty if none
     *
     * Role inheritance is NOT included (use PermissionService for full permission resolution).
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to list of active assignments (empty if none)
     * @throws NullPointerException if tenantId or principalId is null
     */
    Promise<List<RoleAssignment>> findActiveByTenantAndPrincipal(String tenantId, String principalId);

    /**
     * Finds all active assignments for a role in a tenant.
     *
     * GIVEN: Valid tenant ID and role ID
     * WHEN: findActiveByTenantAndRole() is called
     * THEN: Returns list of all active assignments for role, empty if none
     *
     * @param tenantId Tenant identifier
     * @param roleId Role identifier
     * @return Promise resolving to list of active assignments
     * @throws NullPointerException if tenantId or roleId is null
     */
    Promise<List<RoleAssignment>> findActiveByTenantAndRole(String tenantId, String roleId);

    /**
     * Finds all assignments (active and revoked) for a principal.
     *
     * GIVEN: Valid tenant ID and principal ID
     * WHEN: findAllByTenantAndPrincipal() is called
     * THEN: Returns list of all assignments (including revoked), empty if none
     *
     * Used for audit trails and history queries.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to list of all assignments (active + revoked)
     * @throws NullPointerException if tenantId or principalId is null
     */
    Promise<List<RoleAssignment>> findAllByTenantAndPrincipal(String tenantId, String principalId);

    /**
     * Finds all assignments in a tenant by principal type.
     *
     * GIVEN: Valid tenant ID and principal type
     * WHEN: findActiveByTenantAndPrincipalType() is called
     * THEN: Returns all active assignments for principals of that type, empty if none
     *
     * @param tenantId Tenant identifier
     * @param principalType Type of principal (USER, SERVICE_ACCOUNT, GROUP)
     * @return Promise resolving to list of active assignments
     * @throws NullPointerException if tenantId or principalType is null
     */
    Promise<List<RoleAssignment>> findActiveByTenantAndPrincipalType(
            String tenantId,
            RoleAssignment.PrincipalType principalType
    );

    /**
     * Counts active role assignments in a tenant.
     *
     * GIVEN: Valid tenant ID
     * WHEN: countActiveByTenant() is called
     * THEN: Returns total count of active assignments in tenant
     *
     * @param tenantId Tenant identifier
     * @return Promise resolving to count (>= 0)
     * @throws NullPointerException if tenantId is null
     */
    Promise<Long> countActiveByTenant(String tenantId);

    /**
     * Counts active assignments for a principal.
     *
     * GIVEN: Valid tenant ID and principal ID
     * WHEN: countActiveByPrincipal() is called
     * THEN: Returns count of active roles assigned to principal
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to count (>= 0)
     * @throws NullPointerException if tenantId or principalId is null
     */
    Promise<Long> countActiveByPrincipal(String tenantId, String principalId);

    /**
     * Deletes an assignment record (hard delete - use revoke for soft delete).
     *
     * GIVEN: Valid assignment ID
     * WHEN: delete() is called
     * THEN: Assignment is permanently removed from storage
     *
     * WARNING: Hard delete loses audit trail. Use revoke() for operational revocations.
     *
     * @param assignmentId Assignment ID to delete
     * @return Promise resolving when delete completes
     * @throws IllegalArgumentException if assignment doesn't exist
     * @throws NullPointerException if assignmentId is null
     */
    Promise<Void> delete(String assignmentId);

    /**
     * Deletes all assignments for a principal (hard delete).
     *
     * GIVEN: Valid tenant ID and principal ID
     * WHEN: deleteAllByPrincipal() is called
     * THEN: All assignments for principal are permanently removed
     *
     * WARNING: Use with caution. Prefer revoking individual assignments for audit trail.
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @return Promise resolving to count of deleted assignments
     * @throws NullPointerException if tenantId or principalId is null
     */
    Promise<Long> deleteAllByPrincipal(String tenantId, String principalId);

    /**
     * Checks if a principal has an active assignment to a role.
     *
     * GIVEN: Valid tenant ID, principal ID, and role ID
     * WHEN: hasActiveAssignment() is called
     * THEN: Returns true if principal has active assignment to role (ignoring inheritance)
     *
     * @param tenantId Tenant identifier
     * @param principalId Principal identifier
     * @param roleId Role identifier
     * @return Promise resolving to true if active assignment exists
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> hasActiveAssignment(String tenantId, String principalId, String roleId);

    /**
     * Returns storage statistics for debugging/monitoring.
     *
     * GIVEN: Valid tenant ID
     * WHEN: getStatistics() is called
     * THEN: Returns map with keys:
     *   - "total_assignments" (long): Total assignments in tenant
     *   - "active_assignments" (long): Active assignments
     *   - "revoked_assignments" (long): Revoked assignments
     *   - "unique_principals" (long): Unique principals
     *   - "unique_roles" (long): Unique roles
     *   - "created_at" (Instant): Repository creation time
     *
     * @param tenantId Tenant identifier
     * @return Promise resolving to statistics map
     * @throws NullPointerException if tenantId is null
     */
    Promise<Map<String, Object>> getStatistics(String tenantId);
}
