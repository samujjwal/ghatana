/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.authentication.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generic token claims.
 *
 * <p>Contains token claims information in a product-agnostic format.
 * Used for JWT token generation and validation.</p>
 *
 * @doc.type class
 * @doc.purpose Generic token claims - principal, tenant, roles, permissions, metadata
 * @doc.layer kernel
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class TokenClaims {

    private final String principalId;
    private final String tenantId;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final Instant issuedAt;
    private final Map<String, Object> metadata;

    private TokenClaims(Builder builder) {
        this.principalId = Objects.requireNonNull(builder.principalId, "principalId");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId");
        this.roles = Set.copyOf(Objects.requireNonNullElse(builder.roles, Set.of()));
        this.permissions = Set.copyOf(Objects.requireNonNullElse(builder.permissions, Set.of()));
        this.issuedAt = Objects.requireNonNullElse(builder.issuedAt, Instant.now());
        this.metadata = Map.copyOf(Objects.requireNonNullElse(builder.metadata, Map.of()));
    }

    /**
     * Gets the principal identifier.
     *
     * @return the principal ID
     */
    public String getPrincipalId() {
        return principalId;
    }

    /**
     * Gets the tenant identifier.
     *
     * @return the tenant ID
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the roles.
     *
     * @return immutable set of roles
     */
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * Gets the permissions.
     *
     * @return immutable set of permissions
     */
    public Set<String> getPermissions() {
        return permissions;
    }

    /**
     * Gets when the token was issued.
     *
     * @return the issue timestamp
     */
    public Instant getIssuedAt() {
        return issuedAt;
    }

    /**
     * Gets the metadata.
     *
     * @return immutable map of metadata
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Checks if the token has a specific role.
     *
     * @param role the role to check
     * @return true if the token has the role
     */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /**
     * Checks if the token has a specific permission.
     *
     * @param permission the permission to check
     * @return true if the token has the permission
     */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    /**
     * Gets metadata value with type safety.
     *
     * @param key the metadata key
     * @param type the expected type
     * @param <T> the type parameter
     * @return the metadata value or null
     */
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value == null) return null;
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    /**
     * Creates a new builder for TokenClaims.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for TokenClaims.
     */
    public static final class Builder {
        private String principalId;
        private String tenantId;
        private Set<String> roles;
        private Set<String> permissions;
        private Instant issuedAt;
        private Map<String, Object> metadata;

        private Builder() {}

        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder roles(Set<String> roles) {
            this.roles = roles;
            return this;
        }

        public Builder permissions(Set<String> permissions) {
            this.permissions = permissions;
            return this;
        }

        public Builder issuedAt(Instant issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public TokenClaims build() {
            return new TokenClaims(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenClaims that = (TokenClaims) o;
        return Objects.equals(principalId, that.principalId) &&
               Objects.equals(tenantId, that.tenantId) &&
               Objects.equals(roles, that.roles) &&
               Objects.equals(permissions, that.permissions) &&
               Objects.equals(issuedAt, that.issuedAt) &&
               Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(principalId, tenantId, roles, permissions, issuedAt, metadata);
    }

    @Override
    public String toString() {
        return String.format("TokenClaims{principalId='%s', tenantId='%s', roles=%s, permissions=%s}",
            principalId, tenantId, roles, permissions);
    }
}
