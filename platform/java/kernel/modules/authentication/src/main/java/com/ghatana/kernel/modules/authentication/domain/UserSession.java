/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.authentication.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Generic user session.
 *
 * <p>Contains session information in a product-agnostic format.
 * Includes session metadata, roles, and expiration information.</p>
 *
 * @doc.type class
 * @doc.purpose Generic user session - metadata, roles, expiration
 * @doc.layer kernel
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class UserSession {

    private final String sessionId;
    private final String tenantId;
    private final String principalId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Set<String> roles;
    private final Set<String> permissions;

    private UserSession(Builder builder) {
        this.sessionId = Objects.requireNonNull(builder.sessionId, "sessionId");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId");
        this.principalId = Objects.requireNonNull(builder.principalId, "principalId");
        this.createdAt = Objects.requireNonNullElse(builder.createdAt, Instant.now());
        this.expiresAt = Objects.requireNonNull(builder.expiresAt, "expiresAt");
        this.roles = Set.copyOf(Objects.requireNonNullElse(builder.roles, Set.of()));
        this.permissions = Set.copyOf(Objects.requireNonNullElse(builder.permissions, Set.of()));
    }

    /**
     * Gets the session identifier.
     *
     * @return the session ID
     */
    public String getSessionId() {
        return sessionId;
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
     * Gets the principal identifier.
     *
     * @return the principal ID
     */
    public String getPrincipalId() {
        return principalId;
    }

    /**
     * Gets when the session was created.
     *
     * @return the creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets when the session expires.
     *
     * @return the expiration timestamp
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Gets the user's roles.
     *
     * @return immutable set of roles
     */
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * Gets the user's permissions.
     *
     * @return immutable set of permissions
     */
    public Set<String> getPermissions() {
        return permissions;
    }

    /**
     * Checks if the session is expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if the session has a specific role.
     *
     * @param role the role to check
     * @return true if the session has the role
     */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /**
     * Checks if the session has a specific permission.
     *
     * @param permission the permission to check
     * @return true if the session has the permission
     */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    /**
     * Creates a new builder for UserSession.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for UserSession.
     */
    public static final class Builder {
        private String sessionId;
        private String tenantId;
        private String principalId;
        private Instant createdAt;
        private Instant expiresAt;
        private Set<String> roles;
        private Set<String> permissions;

        private Builder() {}

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
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

        public UserSession build() {
            return new UserSession(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserSession that = (UserSession) o;
        return Objects.equals(sessionId, that.sessionId) &&
               Objects.equals(tenantId, that.tenantId) &&
               Objects.equals(principalId, that.principalId) &&
               Objects.equals(createdAt, that.createdAt) &&
               Objects.equals(expiresAt, that.expiresAt) &&
               Objects.equals(roles, that.roles) &&
               Objects.equals(permissions, that.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, tenantId, principalId, createdAt, expiresAt, roles, permissions);
    }

    @Override
    public String toString() {
        return String.format("UserSession{sessionId='%s', tenantId='%s', principalId='%s', expiresAt=%s}",
            sessionId, tenantId, principalId, expiresAt);
    }
}
