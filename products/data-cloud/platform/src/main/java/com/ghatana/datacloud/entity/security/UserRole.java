package com.ghatana.datacloud.entity.security;

import java.util.Set;

/**
 * User roles in collection entity system.
 *
 * <p><b>Purpose</b><br>
 * Defines role-based access control (RBAC) hierarchy for the system.
 * Each role has associated default permissions.
 *
 * <p><b>Role Hierarchy</b><br>
 * - ADMIN: Full system access, can manage users, tenants, policies
 * - CURATOR: Can create, edit, delete collections and manage entities
 * - EDITOR: Can edit existing entities within collections
 * - REVIEWER: Can review and approve entity changes
 * - VIEWER: Read-only access to collections and entities
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Set<UserRole> roles = Set.of(UserRole.EDITOR, UserRole.REVIEWER);
 * UserContext context = UserContext.builder()
 *     .userId("user-1")
 *     .tenantId("tenant-1")
 *     .roles(roles)
 *     .build();
 * }</pre>
 *
 * @doc.type enum
 * @doc.purpose User role enumeration for RBAC
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public enum UserRole {

    /**
     * Administrator - Full system access.
     *
     * Default permissions: All resources, all actions.
     */
    ADMIN("admin", "Administrator with full system access", Set.of(
        "collection:read", "collection:write", "collection:delete", "collection:manage",
        "entity:read", "entity:write", "entity:delete",
        "schema:read", "schema:write", "schema:delete",
        "user:read", "user:write", "user:delete",
        "policy:read", "policy:write", "policy:delete",
        "workflow:read", "workflow:write", "workflow:delete",
        "audit:read", "audit:write"
    )),

    /**
     * Curator - Can manage collections and entities.
     *
     * Default permissions: Create/edit/delete collections, manage entities.
     */
    CURATOR("curator", "Curator with collection and entity management access", Set.of(
        "collection:read", "collection:write", "collection:delete",
        "entity:read", "entity:write", "entity:delete",
        "schema:read",
        "workflow:read",
        "audit:read"
    )),

    /**
     * Editor - Can edit existing entities.
     *
     * Default permissions: Edit entities, create new entities within collections.
     */
    EDITOR("editor", "Editor with entity modification access", Set.of(
        "collection:read",
        "entity:read", "entity:write",
        "schema:read",
        "workflow:read"
    )),

    /**
     * Reviewer - Can review and approve entity changes.
     *
     * Default permissions: Read entities and workflows, approve changes.
     */
    REVIEWER("reviewer", "Reviewer with approval and workflow access", Set.of(
        "collection:read",
        "entity:read",
        "schema:read",
        "workflow:read", "workflow:write"
    )),

    /**
     * Viewer - Read-only access.
     *
     * Default permissions: Read collections, entities, and schemas.
     */
    VIEWER("viewer", "Viewer with read-only access", Set.of(
        "collection:read",
        "entity:read",
        "schema:read"
    ));

    private final String roleId;
    private final String description;
    private final Set<String> defaultPermissions;

    /**
     * Constructs UserRole enum member.
     *
     * @param roleId unique role identifier
     * @param description human-readable description
     * @param defaultPermissions default permissions for this role
     */
    UserRole(String roleId, String description, Set<String> defaultPermissions) {
        this.roleId = roleId;
        this.description = description;
        this.defaultPermissions = Set.copyOf(defaultPermissions);
    }

    /**
     * Gets role identifier.
     *
     * @return role ID (e.g., "admin", "editor")
     */
    public String getRoleId() {
        return roleId;
    }

    /**
     * Gets role description.
     *
     * @return human-readable description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets default permissions for this role.
     *
     * @return immutable set of permission strings
     */
    public Set<String> getDefaultPermissions() {
        return defaultPermissions;
    }

    /**
     * Parses role from string identifier.
     *
     * @param roleId role identifier (case-insensitive)
     * @return UserRole enum member
     * @throws IllegalArgumentException if role ID not found
     * @throws NullPointerException if roleId is null
     */
    public static UserRole fromRoleId(String roleId) {
        if (roleId == null) {
            throw new NullPointerException("roleId cannot be null");
        }
        for (UserRole role : values()) {
            if (role.roleId.equalsIgnoreCase(roleId)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role ID: " + roleId);
    }

    /**
     * Checks if this role includes another role.
     *
     * <p>Role hierarchy:
     * ADMIN > CURATOR > EDITOR/REVIEWER > VIEWER
     *
     * @param other other role to compare
     * @return true if this role has equal or higher privilege
     */
    public boolean includes(UserRole other) {
        if (this == other) return true;
        if (this == ADMIN) return true;
        if (this == CURATOR && (other == EDITOR || other == REVIEWER || other == VIEWER)) {
            return true;
        }
        return false;
    }
}
