/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.authentication.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Generic role definition.
 *
 * <p>Contains role information and associated permissions in a product-agnostic format.
 * Roles can be assigned to principals and used for authorization decisions.</p>
 *
 * @doc.type class
 * @doc.purpose Generic role definition - name, description, permissions
 * @doc.layer kernel
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class Role {

    private final String name;
    private final String description;
    private final Set<Permission> permissions;

    private Role(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name");
        this.description = Objects.requireNonNullElse(builder.description, "");
        this.permissions = Set.copyOf(Objects.requireNonNullElse(builder.permissions, Set.of()));
    }

    /**
     * Gets the role name.
     *
     * @return the role name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the role description.
     *
     * @return the role description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the permissions associated with this role.
     *
     * @return immutable set of permissions
     */
    public Set<Permission> getPermissions() {
        return permissions;
    }

    /**
     * Checks if this role has a specific permission.
     *
     * @param resource the resource
     * @param action   the action
     * @return true if the role has the permission
     */
    public boolean hasPermission(String resource, String action) {
        return permissions.stream()
                .anyMatch(permission -> permission.matches(resource, action));
    }

    /**
     * Creates a new builder for Role.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for Role.
     */
    public static final class Builder {
        private String name;
        private String description;
        private Set<Permission> permissions;

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder permissions(Set<Permission> permissions) {
            this.permissions = permissions;
            return this;
        }

        public Role build() {
            return new Role(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role that = (Role) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(description, that.description) &&
               Objects.equals(permissions, that.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, permissions);
    }

    @Override
    public String toString() {
        return String.format("Role{name='%s', permissions=%d}", name, permissions.size());
    }
}
