/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API - User Context
 * 
 * Represents authenticated user context with roles, permissions, and tenant information.
 * Used throughout the application for authorization and tenant isolation.
 */

package com.ghatana.yappc.api.security;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * User context containing authentication and authorization information.
 * 
 * This class is immutable and represents the current user's security context
 * including their identity, roles, permissions, and tenant association.
  *
 * @doc.type class
 * @doc.purpose user context
 * @doc.layer product
 * @doc.pattern Service
 */
public class UserContext {
    
    private final String userId;
    private final String email;
    private final String userName;
    private final String tenantId;
    private final List<String> roles;
    private final List<Permission> permissions;
    
    private UserContext(@NotNull Builder builder) {
        this.userId = Objects.requireNonNull(builder.userId, "userId");
        this.email = Objects.requireNonNull(builder.email, "email");
        this.userName = Objects.requireNonNull(builder.userName, "userName");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId");
        this.roles = List.copyOf(Objects.requireNonNull(builder.roles, "roles"));
        this.permissions = List.copyOf(Objects.requireNonNull(builder.permissions, "permissions"));
    }
    
    /**
     * Gets the unique user identifier.
     */
    @NotNull
    public String getUserId() {
        return userId;
    }
    
    /**
     * Gets the user's email address.
     */
    @NotNull
    public String getEmail() {
        return email;
    }
    
    /**
     * Gets the user's display name.
     */
    @NotNull
    public String getUserName() {
        return userName;
    }
    
    /**
     * Gets the tenant identifier for tenant isolation.
     */
    @NotNull
    public String getTenantId() {
        return tenantId;
    }
    
    /**
     * Gets the list of roles assigned to the user.
     */
    @NotNull
    public List<String> getRoles() {
        return roles;
    }
    
    /**
     * Gets the list of specific permissions assigned to the user.
     */
    @NotNull
    public List<Permission> getPermissions() {
        return permissions;
    }
    
    /**
     * Checks if the user has the specified role.
     */
    public boolean hasRole(@NotNull String role) {
        return roles.contains(role);
    }
    
    /**
     * Checks if the user has any of the specified roles.
     */
    public boolean hasAnyRole(@NotNull String... roles) {
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if the user has all of the specified roles.
     */
    public boolean hasAllRoles(@NotNull String... roles) {
        for (String role : roles) {
            if (!hasRole(role)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Checks if the user has the specified permission.
     */
    public boolean hasPermission(@NotNull Permission permission) {
        return permissions.contains(permission);
    }
    
    /**
     * Checks if the user has permission for the given path and method.
     */
    public boolean hasPermission(@NotNull String path, @NotNull String method) {
        return permissions.stream()
            .anyMatch(permission -> 
                pathMatches(path, permission.getPathPattern()) &&
                methodMatches(method, permission.getMethods()));
    }
    
    /**
     * Checks if the user is an administrator.
     */
    public boolean isAdmin() {
        return hasRole("admin") || hasRole("system_admin");
    }
    
    /**
     * Checks if the user is a tenant administrator.
     */
    public boolean isTenantAdmin() {
        return hasRole("tenant_admin") || isAdmin();
    }
    
    /**
     * Checks if the path matches the permission pattern.
     */
    private boolean pathMatches(@NotNull String path, @NotNull String pattern) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        }
        return path.equals(pattern) || path.matches(pattern.replace("*", ".*"));
    }
    
    /**
     * Checks if the HTTP method matches allowed methods.
     */
    private boolean methodMatches(@NotNull String method, @NotNull List<String> allowedMethods) {
        return allowedMethods.isEmpty() || allowedMethods.contains(method);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserContext that = (UserContext) o;
        return userId.equals(that.userId) && 
               tenantId.equals(that.tenantId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(userId, tenantId);
    }
    
    @Override
    public String toString() {
        return "UserContext{" +
                "userId='" + userId + '\'' +
                ", email='" + email + '\'' +
                ", userName='" + userName + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", roles=" + roles +
                ", permissions=" + permissions.size() +
                '}';
    }
    
    /**
     * Creates a new builder for UserContext.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for UserContext instances.
     */
    public static class Builder {
        private String userId;
        private String email;
        private String userName;
        private String tenantId;
        private List<String> roles = List.of();
        private List<Permission> permissions = List.of();
        
        public Builder userId(@NotNull String userId) {
            this.userId = userId;
            return this;
        }
        
        public Builder email(@NotNull String email) {
            this.email = email;
            return this;
        }
        
        public Builder userName(@NotNull String userName) {
            this.userName = userName;
            return this;
        }
        
        public Builder tenantId(@NotNull String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder roles(@NotNull List<String> roles) {
            this.roles = roles;
            return this;
        }
        
        public Builder addRole(@NotNull String role) {
            this.roles = List.copyOf(roles);
            return this;
        }
        
        public Builder permissions(@NotNull List<Permission> permissions) {
            this.permissions = permissions;
            return this;
        }
        
        public Builder addPermission(@NotNull Permission permission) {
            this.permissions = List.copyOf(permissions);
            return this;
        }
        
        public UserContext build() {
            return new UserContext(this);
        }
    }
}
