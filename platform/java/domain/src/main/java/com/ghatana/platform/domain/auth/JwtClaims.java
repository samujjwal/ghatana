/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * JWT claims representation.
 *
 * @doc.type class
 * @doc.purpose JWT claims value object
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public class JwtClaims {
    private final String subject;
    private final String issuer;
    private final String audience;
    private final String jwtId;

    public JwtClaims(String subject, String issuer, String audience, String jwtId) {
        this.subject = Objects.requireNonNull(subject, "subject required");
        this.issuer = Objects.requireNonNull(issuer, "issuer required");
        this.audience = Objects.requireNonNull(audience, "audience required");
        this.jwtId = jwtId != null ? jwtId : UUID.randomUUID().toString();
    }

    public String getSubject() { return subject; }
    public String getIssuer() { return issuer; }
    public String getAudience() { return audience; }
    public String getJwtId() { return jwtId; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String subject;
        private String issuer;
        private String audience;
        private String jwtId;

        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder issuer(String issuer) { this.issuer = issuer; return this; }
        public Builder audience(String audience) { this.audience = audience; return this; }
        public Builder jwtId(String jwtId) { this.jwtId = jwtId; return this; }

        public JwtClaims build() {
            return new JwtClaims(subject, issuer, audience, jwtId);
        }
    }
}
