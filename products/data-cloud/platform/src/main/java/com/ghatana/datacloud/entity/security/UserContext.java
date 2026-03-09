package com.ghatana.datacloud.entity.security;

import java.util.*;

/**
 * User security context and permissions model.
 *
 * <p><b>Purpose</b><br>
 * Represents authenticated user with their roles, permissions, and tenant context.
 * Used by access control framework to make authorization decisions.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * UserContext context = UserContext.builder()
 *     .userId("user-123")
 *     .tenantId("tenant-abc")
 *     .roles(Set.of(UserRole.EDITOR, UserRole.REVIEWER))
 *     .build();
 *
 * if (context.hasRole(UserRole.ADMIN)) {
 *     // Perform admin action
 * }
 *
 * if (context.hasPermission("collection:read", "orders")) {
 *     // Access collection
 * }
 * }</pre>
 *
 * <p><b>Multi-Tenancy</b><br>
 * User context is always scoped to a tenant. Cross-tenant access is denied.
 * TenantId extracted from JWT claims or request headers.
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - thread-safe.
 *
 * @doc.type class
 * @doc.purpose User security context and authorization data
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class UserContext {

    private final String userId;
    private final String tenantId;
    private final Set<UserRole> roles;
    private final Map<String, Set<String>> permissions;
    private final long issuedAtMs;

    private UserContext(
            String userId,
            String tenantId,
            Set<UserRole> roles,
            Map<String, Set<String>> permissions,
            long issuedAtMs) {
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.roles = Collections.unmodifiableSet(
            Objects.requireNonNull(roles, "roles cannot be null")
        );
        this.permissions = Collections.unmodifiableMap(
            Objects.requireNonNull(permissions, "permissions cannot be null")
        );
        this.issuedAtMs = issuedAtMs;
    }

    /**
     * Gets authenticated user ID.
     *
     * @return user ID (never null)
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Gets tenant ID for this context.
     *
     * @return tenant ID (never null)
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets all roles assigned to user.
     *
     * @return immutable set of roles
     */
    public Set<UserRole> getRoles() {
        return roles;
    }

    /**
     * Gets all permissions assigned to user.
     *
     * <p>Permissions stored as map of resource type → set of actions.
     * Example: "collection" → {"read", "write", "delete"}
     *
     * @return immutable map of permissions
     */
    public Map<String, Set<String>> getPermissions() {
        return permissions;
    }

    /**
     * Checks if user has specific role.
     *
     * @param role role to check (required)
     * @return true if user has role, false otherwise
     * @throws NullPointerException if role is null
     */
    public boolean hasRole(UserRole role) {
        Objects.requireNonNull(role, "role cannot be null");
        return roles.contains(role);
    }

    /**
     * Checks if user has permission to perform action on resource.
     *
     * <p>Admin users bypass permission checks.
     *
     * @param resourceType resource type (e.g., "collection", "schema") (required)
     * @param action action to perform (e.g., "read", "write", "delete") (required)
     * @return true if user has permission, false otherwise
     * @throws NullPointerException if resourceType or action is null
     */
    public boolean hasPermission(String resourceType, String action) {
        Objects.requireNonNull(resourceType, "resourceType cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        // Admin has all permissions
        if (hasRole(UserRole.ADMIN)) {
            return true;
        }

        Set<String> resourceActions = permissions.get(resourceType);
        return resourceActions != null && resourceActions.contains(action);
    }

    /**
     * Checks if context is still valid (not expired).
     *
     * <p>Default TTL is 1 hour from issuance.
     *
     * @return true if context not expired, false otherwise
     */
    public boolean isValid() {
        if (issuedAtMs == 0) {
            return true; // No expiration if not set
        }
        long ttlMs = 3600_000; // 1 hour
        return System.currentTimeMillis() - issuedAtMs < ttlMs;
    }

    /**
     * Creates builder for constructing UserContext.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for UserContext.
     */
    public static final class Builder {
        private String userId;
        private String tenantId;
        private Set<UserRole> roles = Set.of();
        private Map<String, Set<String>> permissions = Map.of();
        private long issuedAtMs = 0;

        /**
         * Sets user ID.
         *
         * @param userId user ID (required)
         * @return this builder
         */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * Sets tenant ID.
         *
         * @param tenantId tenant ID (required)
         * @return this builder
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * Sets user roles.
         *
         * @param roles roles to assign (required)
         * @return this builder
         */
        public Builder roles(Set<UserRole> roles) {
            this.roles = roles != null ? roles : Set.of();
            return this;
        }

        /**
         * Sets permissions map.
         *
         * @param permissions permissions map (required)
         * @return this builder
         */
        public Builder permissions(Map<String, Set<String>> permissions) {
            this.permissions = permissions != null ? permissions : Map.of();
            return this;
        }

        /**
         * Sets issuance timestamp.
         *
         * @param issuedAtMs milliseconds since epoch when context was issued
         * @return this builder
         */
        public Builder issuedAtMs(long issuedAtMs) {
            this.issuedAtMs = issuedAtMs;
            return this;
        }

        /**
         * Builds UserContext.
         *
         * @return new UserContext instance
         * @throws NullPointerException if required fields not set
         */
        public UserContext build() {
            return new UserContext(userId, tenantId, roles, permissions, issuedAtMs);
        }
    }

    @Override
    public String toString() {
        return "UserContext{" +
            "userId='" + userId + '\'' +
            ", tenantId='" + tenantId + '\'' +
            ", roles=" + roles +
            ", permissions=" + permissions +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserContext)) return false;
        UserContext that = (UserContext) o;
        return Objects.equals(userId, that.userId) &&
            Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, tenantId);
    }
}
