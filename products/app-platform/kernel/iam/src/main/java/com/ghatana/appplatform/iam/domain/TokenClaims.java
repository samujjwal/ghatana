/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Claims carried within a GhatanaJWT token (K01-001).
 *
 * <p>Maps directly to the JWT payload. Callers use {@link #builder()} to construct.
 *
 * @doc.type record
 * @doc.purpose JWT claims for Ghatana tokens (K01-001)
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record TokenClaims(
        String subject,       // sub: client_id_str or user identifier
        UUID tenantId,
        List<String> roles,
        List<String> permissions,
        String issuer,        // iss: e.g., "https://auth.ghatana.io"
        String audience,      // aud: e.g., "ghatana-api"
        Instant issuedAt,
        Instant expiresAt,
        String jwtId          // jti: UUID for token tracking
) {
    public TokenClaims {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(jwtId, "jwtId");
        roles = roles != null ? List.copyOf(roles) : List.of();
        permissions = permissions != null ? List.copyOf(permissions) : List.of();
    }

    /** Returns true if the token has expired. */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience factory for short-lived authorization-code access tokens.
     * TTL is 1 hour; no roles or permissions embedded (caller resolves from session).
     */
    public static TokenClaims forAuthCode(String clientId, String tenantId) {
        Instant now = Instant.now();
        return new TokenClaims(clientId,
            tenantId != null ? java.util.UUID.fromString(tenantId) : null,
            List.of(), List.of(),
            "https://auth.ghatana.io", "ghatana-api",
            now, now.plusSeconds(3600), java.util.UUID.randomUUID().toString());
    }

    public static final class Builder {
        private String subject;
        private UUID tenantId;
        private List<String> roles = List.of();
        private List<String> permissions = List.of();
        private String issuer;
        private String audience;
        private Instant issuedAt = Instant.now();
        private Instant expiresAt;
        private String jwtId = UUID.randomUUID().toString();

        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public Builder roles(List<String> roles) { this.roles = roles; return this; }
        public Builder permissions(List<String> permissions) { this.permissions = permissions; return this; }
        public Builder issuer(String issuer) { this.issuer = issuer; return this; }
        public Builder audience(String audience) { this.audience = audience; return this; }
        public Builder issuedAt(Instant issuedAt) { this.issuedAt = issuedAt; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder jwtId(String jwtId) { this.jwtId = jwtId; return this; }

        public TokenClaims build() {
            return new TokenClaims(subject, tenantId, roles, permissions,
                    issuer, audience, issuedAt, expiresAt, jwtId);
        }
    }
}
