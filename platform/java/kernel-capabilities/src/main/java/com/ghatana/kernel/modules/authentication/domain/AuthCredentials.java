/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.authentication.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Generic authentication credentials.
 *
 * <p>Contains authentication information in a product-agnostic format.
 * Supports various authentication methods including password, MFA, SSO, and OAuth.</p>
 *
 * @doc.type class
 * @doc.purpose Generic authentication credentials - password, MFA, SSO, OAuth
 * @doc.layer kernel
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class AuthCredentials {

    private final String principalId;
    private final AuthMethod authMethod;
    private final Map<String, Object> authData;
    private final Instant issuedAt;
    private final String tenantId;

    private AuthCredentials(Builder builder) {
        this.principalId = Objects.requireNonNull(builder.principalId, "principalId");
        this.authMethod = Objects.requireNonNull(builder.authMethod, "authMethod");
        this.authData = Map.copyOf(Objects.requireNonNullElse(builder.authData, Map.of()));
        this.issuedAt = Objects.requireNonNullElse(builder.issuedAt, Instant.now());
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId");
    }

    /**
     * Gets the principal identifier (user ID, service account ID, etc.).
     *
     * @return the principal identifier
     */
    public String getPrincipalId() {
        return principalId;
    }

    /**
     * Gets the authentication method.
     *
     * @return the authentication method
     */
    public AuthMethod getAuthMethod() {
        return authMethod;
    }

    /**
     * Gets the authentication data.
     *
     * @return immutable map of authentication data
     */
    public Map<String, Object> getAuthData() {
        return authData;
    }

    /**
     * Gets when these credentials were issued.
     *
     * @return the issue timestamp
     */
    public Instant getIssuedAt() {
        return issuedAt;
    }

    /**
     * Gets the tenant identifier.
     *
     * @return the tenant identifier
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Creates a new builder for AuthCredentials.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AuthCredentials.
     */
    public static final class Builder {
        private String principalId;
        private AuthMethod authMethod;
        private Map<String, Object> authData;
        private Instant issuedAt;
        private String tenantId;

        private Builder() {}

        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        public Builder authMethod(AuthMethod authMethod) {
            this.authMethod = authMethod;
            return this;
        }

        public Builder authData(Map<String, Object> authData) {
            this.authData = authData;
            return this;
        }

        public Builder issuedAt(Instant issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public AuthCredentials build() {
            return new AuthCredentials(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthCredentials that = (AuthCredentials) o;
        return Objects.equals(principalId, that.principalId) &&
               authMethod == that.authMethod &&
               Objects.equals(authData, that.authData) &&
               Objects.equals(issuedAt, that.issuedAt) &&
               Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(principalId, authMethod, authData, issuedAt, tenantId);
    }

    @Override
    public String toString() {
        return String.format("AuthCredentials{principalId='%s', authMethod=%s, tenantId='%s'}",
            principalId, authMethod, tenantId);
    }

    /**
     * Authentication methods supported by the kernel.
     */
    public enum AuthMethod {
        /** Username/password authentication */
        PASSWORD,
        /** Multi-factor authentication */
        MFA,
        /** Single sign-on */
        SSO,
        /** OAuth 2.0 */
        OAUTH,
        /** JSON Web Token */
        JWT,
        /** API key */
        API_KEY,
        /** Certificate-based authentication */
        CERTIFICATE
    }
}
