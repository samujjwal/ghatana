package com.ghatana.datacloud.infrastructure.governance.service;

import com.ghatana.datacloud.entity.governance.Role;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Set;

/**
 * Service interface for role assignment operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Manages role assignments to principals (users, services, groups). Provides
 * batch assignment/revocation and query operations.
 *
 * @doc.type interface
 * @doc.purpose Service for role assignment management
 * @doc.layer infrastructure
 * @doc.pattern Service
 */
public interface RoleAssignmentService {

    /**
     * Assigns a role to multiple principals.
     *
     * @param tenantId tenant identifier
     * @param roleId role to assign
     * @param principalIds principals to receive the role
     * @return Promise of assignment result
     */
    Promise<AssignmentResult> assignRoleToPrincipals(String tenantId, String roleId, Set<String> principalIds);

    /**
     * Revokes a role from multiple principals.
     *
     * @param tenantId tenant identifier
     * @param roleId role to revoke
     * @param principalIds principals to lose the role
     * @return Promise of revocation result
     */
    Promise<AssignmentResult> revokeRoleFromPrincipals(String tenantId, String roleId, Set<String> principalIds);

    /**
     * Gets all roles assigned to a principal.
     *
     * @param tenantId tenant identifier
     * @param principalId principal to query
     * @return Promise of assigned roles
     */
    Promise<List<Role>> getRolesForPrincipal(String tenantId, String principalId);

    /**
     * Result of an assignment or revocation operation.
     */
    record AssignmentResult(int successCount, int failureCount, List<String> failures) {

        public static AssignmentResult success(int count) {
            return new AssignmentResult(count, 0, List.of());
        }

        public static AssignmentResult partial(int success, int failure, List<String> failedIds) {
            return new AssignmentResult(success, failure, failedIds);
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailureCount() {
            return failureCount;
        }
    }
}
