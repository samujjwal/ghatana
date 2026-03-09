/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.util.Objects;
import java.util.Set;

/**
 * User principal representing an authenticated user.
 *
 * @doc.type class
 * @doc.purpose Authenticated user principal
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public class UserPrincipal {
    private final String userId;
    private final String email;
    private final String name;
    private final TenantId tenantId;
    private final Set<String> roles;
    private final Set<String> permissions;

    private UserPrincipal(Builder builder) {
        this.userId = Objects.requireNonNull(builder.userId);
        this.email = Objects.requireNonNull(builder.email);
        this.name = builder.name;
        this.tenantId = builder.tenantId;
        this.roles = Set.copyOf(builder.roles);
        this.permissions = Set.copyOf(builder.permissions);
    }

    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public TenantId getTenantId() { return tenantId; }
    public Set<String> getRoles() { return roles; }
    public Set<String> getPermissions() { return permissions; }

    public boolean hasRole(String role) { return roles.contains(role); }
    public boolean hasPermission(String permission) { return permissions.contains(permission); }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String userId;
        private String email;
        private String name;
        private TenantId tenantId;
        private Set<String> roles = Set.of();
        private Set<String> permissions = Set.of();

        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder tenantId(TenantId tenantId) { this.tenantId = tenantId; return this; }
        public Builder roles(Set<String> roles) { this.roles = roles; return this; }
        public Builder addRole(String role) {
            this.roles = new java.util.HashSet<>(this.roles);
            this.roles.add(role);
            return this;
        }
        /** Convenience alias for {@link #addRole(String)}. */
        public Builder role(String role) { return addRole(role); }
        public Builder permissions(Set<String> permissions) { this.permissions = permissions; return this; }
        public Builder addPermission(String permission) {
            this.permissions = new java.util.HashSet<>(this.permissions);
            this.permissions.add(permission);
            return this;
        }
        /** Convenience alias for {@link #addPermission(String)}. */
        public Builder permission(String permission) { return addPermission(permission); }
        public UserPrincipal build() { return new UserPrincipal(this); }
    }
}
