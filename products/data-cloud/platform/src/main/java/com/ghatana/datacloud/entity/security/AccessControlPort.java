package com.ghatana.datacloud.entity.security;

import io.activej.promise.Promise;

/**
 * Port for access control decision making.
 *
 * <p><b>Purpose</b><br>
 * Abstraction for checking user authorization to perform actions on resources.
 * Enables multiple implementations (RBAC, ABAC, policy-based, etc.).
 *
 * <p><b>Design Pattern</b><br>
 * Hexagonal architecture port - allows swapping implementations:
 * - RoleBasedAccessControl (simple RBAC via roles)
 * - AttributeBasedAccessControl (advanced ABAC policies)
 * - OPABasedAccessControl (Open Policy Agent for Rego evaluation)
 * - CachedAccessControl (wraps implementations with caching layer)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AccessControlPort accessControl = accessControlService;
 *
 * Promise<Boolean> canRead = accessControl.canRead(userContext, collectionId);
 * canRead.then(allowed -> {
 *     if (allowed) {
 *         // Read collection
 *     } else {
 *         throw new AccessDeniedException("User cannot read collection");
 *     }
 * });
 * }</pre>
 *
 * <p><b>Multi-Tenancy</b><br>
 * All access control decisions scoped to UserContext.getTenantId().
 * Cross-tenant access always denied.
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe and reusable.
 * Promise-based - safe for concurrent access.
 *
 * @doc.type interface
 * @doc.purpose Access control decision port (RBAC abstraction)
 * @doc.layer domain
 * @doc.pattern Port
 */
public interface AccessControlPort {

    /**
     * Checks if user can read a collection.
     *
     * @param userContext user security context (required)
     * @param collectionId collection ID to check (required)
     * @return Promise resolving to true if user can read, false otherwise
     * @throws NullPointerException if userContext or collectionId is null
     */
    Promise<Boolean> canReadCollection(UserContext userContext, String collectionId);

    /**
     * Checks if user can write/edit a collection.
     *
     * @param userContext user security context (required)
     * @param collectionId collection ID to check (required)
     * @return Promise resolving to true if user can write, false otherwise
     * @throws NullPointerException if userContext or collectionId is null
     */
    Promise<Boolean> canWriteCollection(UserContext userContext, String collectionId);

    /**
     * Checks if user can delete a collection.
     *
     * @param userContext user security context (required)
     * @param collectionId collection ID to check (required)
     * @return Promise resolving to true if user can delete, false otherwise
     * @throws NullPointerException if userContext or collectionId is null
     */
    Promise<Boolean> canDeleteCollection(UserContext userContext, String collectionId);

    /**
     * Checks if user can read an entity.
     *
     * @param userContext user security context (required)
     * @param collectionId collection ID (required)
     * @param entityId entity ID to check (required)
     * @return Promise resolving to true if user can read, false otherwise
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> canReadEntity(UserContext userContext, String collectionId, String entityId);

    /**
     * Checks if user can write/edit an entity.
     *
     * @param userContext user security context (required)
     * @param collectionId collection ID (required)
     * @param entityId entity ID to check (required)
     * @return Promise resolving to true if user can write, false otherwise
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> canWriteEntity(UserContext userContext, String collectionId, String entityId);

    /**
     * Checks if user can delete an entity.
     *
     * @param userContext user security context (required)
     * @param collectionId collection ID (required)
     * @param entityId entity ID to check (required)
     * @return Promise resolving to true if user can delete, false otherwise
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> canDeleteEntity(UserContext userContext, String collectionId, String entityId);

    /**
     * Checks if user can perform generic action on resource.
     *
     * @param userContext user security context (required)
     * @param resourceType resource type (e.g., "collection", "schema", "policy") (required)
     * @param resourceId resource ID to check (required)
     * @param action action to perform (e.g., "read", "write", "delete") (required)
     * @return Promise resolving to true if user can perform action, false otherwise
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> canPerformAction(
        UserContext userContext,
        String resourceType,
        String resourceId,
        String action);

    /**
     * Checks if user can manage other users (assign roles, permissions).
     *
     * @param userContext user security context (required)
     * @param targetUserId user to manage (required)
     * @return Promise resolving to true if user can manage target user, false otherwise
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> canManageUser(UserContext userContext, String targetUserId);

    /**
     * Checks if user has specific role.
     *
     * <p>This is a convenience method - equivalent to userContext.hasRole(),
     * but provided for consistency with port interface contract.
     *
     * @param userContext user security context (required)
     * @param role role to check (required)
     * @return Promise resolving to true if user has role, false otherwise
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> hasRole(UserContext userContext, UserRole role);

    /**
     * Checks if user has specific permission.
     *
     * <p>This is a convenience method - equivalent to userContext.hasPermission(),
     * but provided for consistency with port interface contract.
     *
     * @param userContext user security context (required)
     * @param resourceType resource type (e.g., "collection") (required)
     * @param action action (e.g., "read") (required)
     * @return Promise resolving to true if user has permission, false otherwise
     * @throws NullPointerException if any parameter is null
     */
    Promise<Boolean> hasPermission(UserContext userContext, String resourceType, String action);
}
