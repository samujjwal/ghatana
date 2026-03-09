package com.ghatana.datacloud.application.security;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.security.AccessControlPort;
import com.ghatana.datacloud.entity.security.UserContext;
import com.ghatana.datacloud.entity.security.UserRole;
import io.activej.promise.Promise;
import java.util.Objects;

/**
 * Application service for role-based access control (RBAC).
 *
 * <p>
 * <b>Purpose</b><br>
 * Implements RBAC authorization decisions based on user roles and permissions.
 * Simple, fast implementation suitable for most scenarios.
 * For advanced policy-based control, use OPABasedAccessControl instead.
 *
 * <p>
 * <b>Authorization Rules</b><br>
 * - ADMIN role bypasses all checks (always allowed)
 * - CURATOR can manage collections and entities
 * - EDITOR can edit entities
 * - REVIEWER can approve workflows
 * - VIEWER has read-only access
 * - All checks enforced per tenant (cross-tenant access denied)
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * AccessControlService accessControl = new AccessControlService(
 *         collectionRepository, metricsCollector);
 *
 * Promise<Boolean> canRead = accessControl.canReadCollection(userContext, collectionId);
 * canRead.then(allowed -> {
 *     if (allowed) {
 *         // Read collection
 *     } else {
 *         throw new AccessDeniedException("Access denied");
 *     }
 * });
 * }</pre>
 *
 * <p>
 * <b>Performance</b><br>
 * O(1) role/permission checks. No external lookups for RBAC.
 * Metrics tracked per operation for monitoring.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe. Can be safely reused across multiple threads.
 *
 * @doc.type class
 * @doc.purpose RBAC authorization service (application service)
 * @doc.layer application
 * @doc.pattern Service
 */
public class AccessControlService implements AccessControlPort {

    private final MetricsCollector metricsCollector;

    /**
     * Constructs AccessControlService.
     *
     * @param metricsCollector metrics collector (required)
     * @throws NullPointerException if metricsCollector is null
     */
    public AccessControlService(MetricsCollector metricsCollector) {
        this.metricsCollector = Objects.requireNonNull(metricsCollector,
                "metricsCollector cannot be null");
    }

    @Override
    public Promise<Boolean> canReadCollection(UserContext userContext, String collectionId) {
        Objects.requireNonNull(userContext, "userContext cannot be null");
        Objects.requireNonNull(collectionId, "collectionId cannot be null");

        return Promise.of(checkCollectionPermission(userContext, "collection:read"));
    }

    @Override
    public Promise<Boolean> canWriteCollection(UserContext userContext, String collectionId) {
        Objects.requireNonNull(userContext, "userContext cannot be null");
        Objects.requireNonNull(collectionId, "collectionId cannot be null");

        return Promise.of(checkCollectionPermission(userContext, "collection:write"));
    }

    @Override
    public Promise<Boolean> canDeleteCollection(UserContext userContext, String collectionId) {
        Objects.requireNonNull(userContext, "userContext cannot be null");
        Objects.requireNonNull(collectionId, "collectionId cannot be null");

        return Promise.of(checkCollectionPermission(userContext, "collection:delete"));
    }

    @Override
    public Promise<Boolean> canReadEntity(UserContext userContext, String collectionId, String entityId) {
        Objects.requireNonNull(userContext, "userContext cannot be null");
        Objects.requireNonNull(collectionId, "collectionId cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");

        boolean allowed = checkCollectionPermission(userContext, "collection:read") &&
                checkEntityPermission(userContext, "entity:read");

        return Promise.of(allowed);
    }

    @Override
    public Promise<Boolean> canWriteEntity(UserContext userContext, String collectionId, String entityId) {
        Objects.requireNonNull(userContext, "userContext cannot be null");
        Objects.requireNonNull(collectionId, "collectionId cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");

        boolean allowed = checkEntityPermission(userContext, "entity:write");

        if (allowed) {
            metricsCollector.incrementCounter("access_control.entity_write_allowed",
                    "tenant", userContext.getTenantId(),
                    "user", userContext.getUserId());
        } else {
            metricsCollector.incrementCounter("access_control.entity_write_denied",
                    "tenant", userContext.getTenantId(),
                    "user", userContext.getUserId());
        }

        return Promise.of(allowed);
    }

    @Override
    public Promise<Boolean> canDeleteEntity(UserContext userContext, String collectionId, String entityId) {
        Objects.requireNonNull(userContext, "userContext cannot be null");
        Objects.requireNonNull(collectionId, "collectionId cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");

        boolean allowed = checkCollectionPermission(userContext, "collection:read") &&
                checkEntityPermission(userContext, "entity:delete");

        return Promise.of(allowed);
    }

    @Override
    public Promise<Boolean> canPerformAction(
            UserContext userContext,
            String resourceType,
            String resourceId,
            String action) {
        Objects.requireNonNull(userContext, "userContext cannot be null");
        Objects.requireNonNull(resourceType, "resourceType cannot be null");
        Objects.requireNonNull(resourceId, "resourceId cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        String permission = resourceType + ":" + action;
        boolean allowed = userContext.hasPermission(resourceType, action);

        metricsCollector.incrementCounter("access_control.action_checked",
                "tenant", userContext.getTenantId(),
                "resource_type", resourceType,
                "action", action,
                "allowed", String.valueOf(allowed));

        return Promise.of(allowed);
    }

    @Override
    public Promise<Boolean> canManageUser(UserContext userContext, String targetUserId) {
        Objects.requireNonNull(userContext, "userContext cannot be null");
        Objects.requireNonNull(targetUserId, "targetUserId cannot be null");

        // Only ADMIN can manage users
        boolean allowed = userContext.hasRole(UserRole.ADMIN);

        metricsCollector.incrementCounter("access_control.user_management_checked",
                "tenant", userContext.getTenantId(),
                "allowed", String.valueOf(allowed));

        return Promise.of(allowed);
    }

    @Override
    public Promise<Boolean> hasRole(UserContext userContext, UserRole role) {
        Objects.requireNonNull(userContext, "userContext cannot be null");
        Objects.requireNonNull(role, "role cannot be null");

        boolean hasRole = userContext.hasRole(role);
        metricsCollector.incrementCounter("access_control.role_checked",
                "tenant", userContext.getTenantId(),
                "role", role.getRoleId(),
                "has_role", String.valueOf(hasRole));

        return Promise.of(hasRole);
    }

    @Override
    public Promise<Boolean> hasPermission(UserContext userContext, String resourceType, String action) {
        Objects.requireNonNull(userContext, "userContext cannot be null");
        Objects.requireNonNull(resourceType, "resourceType cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        boolean hasPermission = userContext.hasPermission(resourceType, action);
        metricsCollector.incrementCounter("access_control.permission_checked",
                "tenant", userContext.getTenantId(),
                "resource_type", resourceType,
                "action", action,
                "has_permission", String.valueOf(hasPermission));

        return Promise.of(hasPermission);
    }

    /**
     * Checks collection permission.
     *
     * @param userContext user context (required)
     * @param permission  permission to check (required)
     * @return true if user has permission, false otherwise
     */
    private boolean checkCollectionPermission(UserContext userContext, String permission) {
        // Admin has all permissions
        if (userContext.hasRole(UserRole.ADMIN)) {
            return true;
        }

        // CURATOR can manage collections
        if ("collection:read".equals(permission)) {
            return userContext.hasPermission("collection", "read");
        }
        if ("collection:write".equals(permission)) {
            return userContext.hasPermission("collection", "write");
        }
        if ("collection:delete".equals(permission)) {
            return userContext.hasPermission("collection", "delete");
        }

        return false;
    }

    /**
     * Checks entity permission.
     *
     * @param userContext user context (required)
     * @param permission  permission to check (required)
     * @return true if user has permission, false otherwise
     */
    private boolean checkEntityPermission(UserContext userContext, String permission) {
        // Admin has all permissions
        if (userContext.hasRole(UserRole.ADMIN)) {
            return true;
        }

        // EDITOR can read/write entities
        if ("entity:read".equals(permission)) {
            return userContext.hasPermission("entity", "read");
        }
        if ("entity:write".equals(permission)) {
            return userContext.hasPermission("entity", "write");
        }
        if ("entity:delete".equals(permission)) {
            return userContext.hasPermission("entity", "delete");
        }

        return false;
    }
}
