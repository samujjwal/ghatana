package com.ghatana.virtualorg.security;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Service for authorization and access control.
 *
 * <p><b>Purpose</b><br>
 * Port interface defining authorization capabilities using Role-Based Access Control
 * (RBAC) with support for resource and attribute-based policies.
 *
 * <p><b>Architecture Role</b><br>
 * Security port interface. Implementations provide:
 * - Role-based permissions (admin, agent, user)
 * - Resource-based access control (task:read, agent:start)
 * - Action-based permissions (create, read, update, delete)
 * - Attribute-based access control (ABAC)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AuthorizationService authz = new RBACAuthorizationService(config);
 *
 * // Check if principal can perform action on resource
 * boolean canRead = authz.isAuthorized(
 *     principal,
 *     "task:read",
 *     "task-123"
 * ).getResult();
 *
 * if (canRead) {
 *     // Allow access to resource
 * }
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Authorization port for RBAC and resource access control
 * @doc.layer product
 * @doc.pattern Port
 */
public interface AuthorizationService {

    /**
     * Check if principal is authorized to perform an action on a resource.
     *
     * @param principal The principal requesting access
     * @param action The action to perform (e.g., "task:read", "agent:start")
     * @param resourceId The resource identifier (optional, can be null)
     * @return Promise of true if authorized, false otherwise
     */
    @NotNull
    Promise<Boolean> isAuthorized(
        @NotNull Principal principal,
        @NotNull String action,
        @NotNull String resourceId
    );

    /**
     * Check if principal has a specific permission.
     *
     * @param principal The principal
     * @param permission The permission to check
     * @return Promise of true if has permission, false otherwise
     */
    @NotNull
    Promise<Boolean> hasPermission(
        @NotNull Principal principal,
        @NotNull String permission
    );

    /**
     * Get all permissions for a principal.
     *
     * @param principal The principal
     * @return Promise of set of permissions
     */
    @NotNull
    Promise<java.util.Set<String>> getPermissions(@NotNull Principal principal);

    /**
     * Grant a role to a principal.
     *
     * @param principalId Principal identifier
     * @param role Role to grant
     * @return Promise of true if successful
     */
    @NotNull
    Promise<Boolean> grantRole(@NotNull String principalId, @NotNull String role);

    /**
     * Revoke a role from a principal.
     *
     * @param principalId Principal identifier
     * @param role Role to revoke
     * @return Promise of true if successful
     */
    @NotNull
    Promise<Boolean> revokeRole(@NotNull String principalId, @NotNull String role);

    /**
     * Check if principal can escalate to another role.
     *
     * Used for agents that need to escalate decisions to higher authority.
     *
     * @param principal The principal
     * @param targetRole The role to escalate to
     * @return Promise of true if escalation is allowed
     */
    @NotNull
    Promise<Boolean> canEscalate(@NotNull Principal principal, @NotNull String targetRole);
}
