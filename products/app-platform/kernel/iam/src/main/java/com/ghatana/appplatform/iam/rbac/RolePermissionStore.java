package com.ghatana.appplatform.iam.rbac;

import java.util.List;
import java.util.Optional;

/**
 * Port for RBAC role and permission management.
 *
 * @doc.type interface
 * @doc.purpose RBAC role-permission store port (STORY-K01-RBAC)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface RolePermissionStore {

    // ── Role Management ───────────────────────────────────────────────────────

    void saveRole(Role role);

    Optional<Role> findRole(String tenantId, String roleName);

    List<Role> listRoles(String tenantId);

    // ── Assignment ────────────────────────────────────────────────────────────

    /** Assign a role to a principal (user or service account). */
    void assignRole(String tenantId, String principalId, String roleName);

    /** Revoke a role from a principal. */
    void revokeRole(String tenantId, String principalId, String roleName);

    /** Get all roles assigned to a principal within a tenant. */
    List<Role> getPrincipalRoles(String tenantId, String principalId);

    // ── Authorization Check ───────────────────────────────────────────────────

    /**
     * Check whether a principal has a specific permission in a tenant.
     * Aggregates permissions across all assigned roles.
     */
    boolean hasPermission(String tenantId, String principalId, String resource, String action);
}
