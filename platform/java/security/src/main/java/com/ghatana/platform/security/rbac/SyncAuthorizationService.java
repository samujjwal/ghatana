/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.rbac;

import com.ghatana.platform.security.model.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Synchronous authorization service for permission checking and access control.
 *
 * <p>Provides a synchronous wrapper around {@link RolePermissionRegistry} for
 * use cases where async Promise-based authorization is not needed. For async
 * usage, see {@link com.ghatana.platform.security.port.AuthorizationService}.
 *
 * @doc.type class
 * @doc.purpose Synchronous authorization service for role-based permission checking
 * @doc.layer security
 * @doc.pattern Service
 */
public class SyncAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(SyncAuthorizationService.class);

    private final RolePermissionRegistry permissionRegistry;

    /**
     * Creates a new SyncAuthorizationService.
     *
     * @param permissionRegistry the role-permission registry
     */
    public SyncAuthorizationService(@NotNull RolePermissionRegistry permissionRegistry) {
        this.permissionRegistry = Objects.requireNonNull(permissionRegistry, "permissionRegistry is required");
    }

    /**
     * Check if a user has a specific permission.
     *
     * @param user the user to check
     * @param permission the permission string
     * @return true if the user has the permission via any of their roles
     */
    public boolean hasPermission(@Nullable User user, @Nullable String permission) {
        if (user == null || permission == null) {
            return false;
        }

        return user.getRoles().stream()
            .anyMatch(role -> permissionRegistry.hasPermission(role, permission));
    }

    /**
     * Check if a user has any of the specified permissions.
     *
     * @param user the user to check
     * @param permissions the permissions to check
     * @return true if the user has at least one of the permissions
     */
    public boolean hasAnyPermission(@Nullable User user, @Nullable String... permissions) {
        if (user == null || permissions == null || permissions.length == 0) {
            return false;
        }

        return user.getRoles().stream()
            .anyMatch(role -> {
                Set<String> rolePerms = permissionRegistry.getPermissions(role);
                if (rolePerms == null) {
                    return false;
                }
                for (String perm : permissions) {
                    if (rolePerms.contains(perm)) {
                        return true;
                    }
                }
                return false;
            });
    }

    /**
     * Check if a user has all of the specified permissions.
     *
     * @param user the user to check
     * @param permissions the permissions to check
     * @return true if the user has all of the permissions
     */
    public boolean hasAllPermissions(@Nullable User user, @Nullable String... permissions) {
        if (user == null || permissions == null || permissions.length == 0) {
            return false;
        }

        Set<String> allUserPerms = getAllPermissions(user);
        for (String perm : permissions) {
            if (!allUserPerms.contains(perm)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Require a specific permission, throwing AccessDeniedException if not present.
     *
     * @param user the user to check
     * @param permission the required permission
     * @throws AccessDeniedException if the user lacks the permission
     */
    public void requirePermission(@Nullable User user, @NotNull String permission) {
        if (!hasPermission(user, permission)) {
            String message = String.format(
                "User '%s' lacks permission: %s",
                user != null ? user.getUserId() : "unknown",
                permission
            );
            log.warn("Authorization denied: {}", message);
            throw new AccessDeniedException(message);
        }
    }

    /**
     * Require any of the specified permissions.
     *
     * @param user the user to check
     * @param permissions the required permissions (at least one)
     * @throws AccessDeniedException if the user lacks all specified permissions
     */
    public void requireAnyPermission(@Nullable User user, @NotNull String... permissions) {
        if (!hasAnyPermission(user, permissions)) {
            String permList = String.join(", ", permissions);
            String message = String.format(
                "User '%s' lacks any of required permissions: %s",
                user != null ? user.getUserId() : "unknown",
                permList
            );
            log.warn("Authorization denied: {}", message);
            throw new AccessDeniedException(message);
        }
    }

    /**
     * Require all of the specified permissions.
     *
     * @param user the user to check
     * @param permissions the required permissions (all)
     * @throws AccessDeniedException if the user lacks any of the specified permissions
     */
    public void requireAllPermissions(@Nullable User user, @NotNull String... permissions) {
        if (!hasAllPermissions(user, permissions)) {
            String permList = String.join(", ", permissions);
            String message = String.format(
                "User '%s' lacks all required permissions: %s",
                user != null ? user.getUserId() : "unknown",
                permList
            );
            log.warn("Authorization denied: {}", message);
            throw new AccessDeniedException(message);
        }
    }

    /**
     * Get all permissions for a user (union of all role permissions).
     *
     * @param user the user
     * @return set of all permission strings the user has
     */
    public Set<String> getAllPermissions(@Nullable User user) {
        if (user == null) {
            return Set.of();
        }

        Set<String> allPermissions = new HashSet<>();
        for (String role : user.getRoles()) {
            Set<String> rolePerms = permissionRegistry.getPermissions(role);
            if (rolePerms != null) {
                allPermissions.addAll(rolePerms);
            }
        }
        return allPermissions;
    }

    /**
     * Check if user is an admin.
     *
     * @param user the user to check
     * @return true if the user has OWNER or ADMIN role
     */
    public boolean isAdmin(@Nullable User user) {
        if (user == null) {
            return false;
        }
        return user.hasRole("OWNER") || user.hasRole("ADMIN");
    }
}
