package com.ghatana.refactorer.server.auth;

import com.ghatana.platform.governance.rbac.Role;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Role-Based Access Control (RBAC) implementation for the Polyfix service. Manages permissions and

 * access control decisions based on user roles and resource permissions.

 *

 * @doc.type class

 * @doc.purpose Describe how authentication/authorization is applied across incoming requests.

 * @doc.layer product

 * @doc.pattern Policy

 */

public final class RoleBasedAccessControl {

    /**
 * Predefined system roles backed by the canonical governance {@link Role}. */
    public enum PredefinedRole {
        ADMIN(createRole("admin", Permission.ALL_PERMISSIONS)),
        USER(createRole(
                "user",
                EnumSet.of(
                        Permission.JOB_CREATE,
                        Permission.JOB_READ,
                        Permission.JOB_CANCEL,
                        Permission.DIAGNOSTICS_READ,
                        Permission.METRICS_READ))),
        VIEWER(createRole(
                "viewer",
                EnumSet.of(
                        Permission.JOB_READ,
                        Permission.DIAGNOSTICS_READ,
                        Permission.METRICS_READ))),
        SERVICE(createRole(
                "service",
                EnumSet.of(
                        Permission.JOB_CREATE,
                        Permission.JOB_READ,
                        Permission.JOB_UPDATE,
                        Permission.JOB_DELETE,
                        Permission.DIAGNOSTICS_READ,
                        Permission.DIAGNOSTICS_WRITE,
                        Permission.METRICS_READ,
                        Permission.SYSTEM_READ)));

        private final Role role;

        PredefinedRole(Role role) {
            this.role = role;
        }

        public Role toRole() {
            return role;
        }
    }

    /**
 * System permissions that can be granted to roles. */
    public enum Permission {
        // Job management
        JOB_CREATE,
        JOB_READ,
        JOB_UPDATE,
        JOB_DELETE,
        JOB_CANCEL,

        // Diagnostics
        DIAGNOSTICS_READ,
        DIAGNOSTICS_WRITE,

        // Metrics and monitoring
        METRICS_READ,
        METRICS_WRITE,

        // System operations
        SYSTEM_READ,
        SYSTEM_WRITE,
        SYSTEM_ADMIN,

        // Configuration
        CONFIG_READ,
        CONFIG_WRITE,

        // User management
        USER_READ,
        USER_WRITE,
        USER_ADMIN;

        public static final Set<Permission> ALL_PERMISSIONS = EnumSet.allOf(Permission.class);
    }

    /**
 * Resource types that can be protected by RBAC. */
    public enum Resource {
        JOB("job"),
        DIAGNOSTIC("diagnostic"),
        METRIC("metric"),
        SYSTEM("system"),
        CONFIG("config"),
        USER("user");

        private final String name;

        Resource(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /**
 * Actions that can be performed on resources. */
    public enum Action {
        CREATE,
        READ,
        UPDATE,
        DELETE,
        CANCEL,
        ADMIN
    }

    private final Map<String, Set<Role>> userRoles = new ConcurrentHashMap<>();
    private final Map<String, Set<Permission>> customPermissions = new ConcurrentHashMap<>();

    /**
     * Assigns a role to a user.
     *
     * @param userId the user ID
     * @param role the role to assign
     */
    public void assignRole(String userId, PredefinedRole role) {
        assignRole(userId, role.toRole());
    }

    public void assignRole(String userId, Role role) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(role, "Role cannot be null");

        userRoles.computeIfAbsent(userId, k -> new HashSet<>()).add(role);
    }

    /**
     * Removes a role from a user.
     *
     * @param userId the user ID
     * @param role the role to remove
     */
    public void removeRole(String userId, PredefinedRole role) {
        removeRole(userId, role.toRole());
    }

    public void removeRole(String userId, Role role) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(role, "Role cannot be null");

        Set<Role> roles = userRoles.get(userId);
        if (roles != null) {
            roles.remove(role);
            if (roles.isEmpty()) {
                userRoles.remove(userId);
            }
        }
    }

    /**
     * Gets all roles assigned to a user.
     *
     * @param userId the user ID
     * @return the set of roles, or empty set if user has no roles
     */
    public Set<Role> getUserRoles(String userId) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        return Collections.unmodifiableSet(userRoles.getOrDefault(userId, Collections.emptySet()));
    }

    /**
     * Grants a custom permission to a user.
     *
     * @param userId the user ID
     * @param permission the permission to grant
     */
    public void grantPermission(String userId, Permission permission) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(permission, "Permission cannot be null");

        customPermissions
                .computeIfAbsent(userId, k -> EnumSet.noneOf(Permission.class))
                .add(permission);
    }

    /**
     * Revokes a custom permission from a user.
     *
     * @param userId the user ID
     * @param permission the permission to revoke
     */
    public void revokePermission(String userId, Permission permission) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(permission, "Permission cannot be null");

        Set<Permission> permissions = customPermissions.get(userId);
        if (permissions != null) {
            permissions.remove(permission);
            if (permissions.isEmpty()) {
                customPermissions.remove(userId);
            }
        }
    }

    /**
     * Gets all effective permissions for a user (from roles and custom grants).
     *
     * @param userId the user ID
     * @return the set of effective permissions
     */
    public Set<Permission> getUserPermissions(String userId) {
        Objects.requireNonNull(userId, "User ID cannot be null");

        Set<Permission> allPermissions = EnumSet.noneOf(Permission.class);

        // Add permissions from roles
        Set<Role> roles = getUserRoles(userId);
        for (Role role : roles) {
            allPermissions.addAll(permissionsFromRole(role));
        }

        // Add custom permissions
        Set<Permission> custom = customPermissions.get(userId);
        if (custom != null) {
            allPermissions.addAll(custom);
        }

        return Collections.unmodifiableSet(allPermissions);
    }

    /**
     * Checks if a user has a specific permission.
     *
     * @param userId the user ID
     * @param permission the permission to check
     * @return true if the user has the permission
     */
    public boolean hasPermission(String userId, Permission permission) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(permission, "Permission cannot be null");

        return getUserPermissions(userId).contains(permission);
    }

    /**
     * Checks if a user has a specific role.
     *
     * @param userId the user ID
     * @param role the role to check
     * @return true if the user has the role
     */
    public boolean hasRole(String userId, PredefinedRole role) {
        return hasRole(userId, role.toRole());
    }

    public boolean hasRole(String userId, Role role) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(role, "Role cannot be null");

        return getUserRoles(userId).contains(role);
    }

    /**
     * Checks if a user can perform an action on a resource.
     *
     * @param userId the user ID
     * @param resource the resource type
     * @param action the action to perform
     * @return true if the action is allowed
     */
    public boolean canPerform(String userId, Resource resource, Action action) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(resource, "Resource cannot be null");
        Objects.requireNonNull(action, "Action cannot be null");

        Permission requiredPermission = mapToPermission(resource, action);
        return requiredPermission != null && hasPermission(userId, requiredPermission);
    }

    /**
     * Checks if a user can perform an action on a specific resource instance.
     *
     * @param userId the user ID
     * @param resource the resource type
     * @param resourceId the specific resource ID
     * @param action the action to perform
     * @return true if the action is allowed
     */
    public boolean canPerform(String userId, Resource resource, String resourceId, Action action) {
        // Basic implementation - can be extended for resource-specific access control
        return canPerform(userId, resource, action);
    }

    /**
     * Creates an access control context for a user.
     *
     * @param userId the user ID
     * @return an access control context
     */
    public AccessContext createContext(String userId) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        return new AccessContext(userId, this);
    }

    private static Role createRole(String name, Set<Permission> permissions) {
        Role.RoleBuilder builder = Role.builder().name(name);
        permissions.stream()
                .map(Permission::name)
                .map(String::toLowerCase)
                .forEach(builder::permission);
        return builder.build();
    }

    private static Set<Permission> permissionsFromRole(Role role) {
        Set<Permission> mapped = EnumSet.noneOf(Permission.class);
        for (String permissionName : role.getPermissions()) {
            Permission permission = fromPermissionName(permissionName);
            if (permission != null) {
                mapped.add(permission);
            }
        }
        return mapped;
    }

    private static Permission fromPermissionName(String name) {
        if (name == null) {
            return null;
        }
        try {
            return Permission.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Permission mapToPermission(Resource resource, Action action) {
        return switch (resource) {
            case JOB ->
                    switch (action) {
                        case CREATE -> Permission.JOB_CREATE;
                        case READ -> Permission.JOB_READ;
                        case UPDATE -> Permission.JOB_UPDATE;
                        case DELETE -> Permission.JOB_DELETE;
                        case CANCEL -> Permission.JOB_CANCEL;
                        case ADMIN -> Permission.SYSTEM_ADMIN;
                    };
            case DIAGNOSTIC ->
                    switch (action) {
                        case READ -> Permission.DIAGNOSTICS_READ;
                        case CREATE, UPDATE, DELETE -> Permission.DIAGNOSTICS_WRITE;
                        case ADMIN -> Permission.SYSTEM_ADMIN;
                        default -> null;
                    };
            case METRIC ->
                    switch (action) {
                        case READ -> Permission.METRICS_READ;
                        case CREATE, UPDATE, DELETE -> Permission.METRICS_WRITE;
                        case ADMIN -> Permission.SYSTEM_ADMIN;
                        default -> null;
                    };
            case SYSTEM ->
                    switch (action) {
                        case READ -> Permission.SYSTEM_READ;
                        case CREATE, UPDATE, DELETE -> Permission.SYSTEM_WRITE;
                        case ADMIN -> Permission.SYSTEM_ADMIN;
                        default -> null;
                    };
            case CONFIG ->
                    switch (action) {
                        case READ -> Permission.CONFIG_READ;
                        case CREATE, UPDATE, DELETE -> Permission.CONFIG_WRITE;
                        case ADMIN -> Permission.SYSTEM_ADMIN;
                        default -> null;
                    };
            case USER ->
                    switch (action) {
                        case READ -> Permission.USER_READ;
                        case CREATE, UPDATE, DELETE -> Permission.USER_WRITE;
                        case ADMIN -> Permission.USER_ADMIN;
                        default -> null;
                    };
        };
    }

    /**
 * Access control context for a specific user. */
    public static final class AccessContext {
        private final String userId;
        private final RoleBasedAccessControl rbac;

        private AccessContext(String userId, RoleBasedAccessControl rbac) {
            this.userId = userId;
            this.rbac = rbac;
        }

        public String getUserId() {
            return userId;
        }

        public Set<Role> getRoles() {
            return rbac.getUserRoles(userId);
        }

        public Set<Permission> getPermissions() {
            return rbac.getUserPermissions(userId);
        }

        public boolean hasPermission(Permission permission) {
            return rbac.hasPermission(userId, permission);
        }

        public boolean hasRole(Role role) {
            return rbac.hasRole(userId, role);
        }

        public boolean hasRole(PredefinedRole role) {
            return rbac.hasRole(userId, role);
        }

        public boolean canPerform(Resource resource, Action action) {
            return rbac.canPerform(userId, resource, action);
        }

        public boolean canPerform(Resource resource, String resourceId, Action action) {
            return rbac.canPerform(userId, resource, resourceId, action);
        }

        public void requirePermission(Permission permission) {
            if (!hasPermission(permission)) {
                throw new AccessDeniedException(
                        "User " + userId + " lacks permission: " + permission);
            }
        }

        public void requireRole(Role role) {
            if (!hasRole(role)) {
                throw new AccessDeniedException("User " + userId + " lacks role: " + role.getName());
            }
        }

        public void requireRole(PredefinedRole role) {
            if (!hasRole(role)) {
                throw new AccessDeniedException("User " + userId + " lacks role: " + role.name());
            }
        }

        public void requireAccess(Resource resource, Action action) {
            if (!canPerform(resource, action)) {
                throw new AccessDeniedException(
                        "User " + userId + " cannot perform " + action + " on " + resource);
            }
        }
    }

    /**
 * Exception thrown when access is denied. */
    public static final class AccessDeniedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public AccessDeniedException(String message) {
            super(message);
        }
    }
}
