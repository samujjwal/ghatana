package com.ghatana.auth.core.port;

import com.ghatana.platform.domain.auth.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Rich JWT claims value object for auth-gateway token operations.
 *
 * <p>Extends the minimal platform JwtClaims with tenant, user-profile,
 * role, and permission information embedded in tokens.
 *
 * @doc.type class
 * @doc.purpose Rich JWT claims for auth-gateway tokens
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public class JwtClaims {

    private final String tokenId;
    private final String subject;
    private final String issuer;
    private final String audience;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final TenantId tenantId;
    private final String email;
    private final String name;
    private final Set<String> roles;
    private final Set<String> permissions;

    private JwtClaims(Builder builder) {
        this.tokenId = builder.tokenId;
        this.subject = Objects.requireNonNull(builder.subject, "subject required");
        this.issuer = builder.issuer;
        this.audience = builder.audience;
        this.issuedAt = builder.issuedAt;
        this.expiresAt = builder.expiresAt;
        this.tenantId = builder.tenantId;
        this.email = builder.email;
        this.name = builder.name;
        this.roles = builder.roles != null ? Set.copyOf(builder.roles) : Set.of();
        this.permissions = builder.permissions != null ? Set.copyOf(builder.permissions) : Set.of();
    }

    public String getTokenId() { return tokenId; }
    public String getSubject() { return subject; }
    /** Alias for {@link #getSubject()}. */
    public String getUserId() { return subject; }
    public String getIssuer() { return issuer; }
    public String getAudience() { return audience; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public TenantId getTenantId() { return tenantId; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public Set<String> getRoles() { return roles; }
    public Set<String> getPermissions() { return permissions; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String tokenId;
        private String subject;
        private String issuer;
        private String audience;
        private Instant issuedAt;
        private Instant expiresAt;
        private TenantId tenantId;
        private String email;
        private String name;
        private Set<String> roles;
        private Set<String> permissions;

        public Builder tokenId(String tokenId) { this.tokenId = tokenId; return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder issuer(String issuer) { this.issuer = issuer; return this; }
        public Builder audience(String audience) { this.audience = audience; return this; }
        public Builder issuedAt(Instant issuedAt) { this.issuedAt = issuedAt; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder tenantId(TenantId tenantId) { this.tenantId = tenantId; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder roles(Set<String> roles) { this.roles = roles; return this; }
        public Builder permissions(Set<String> permissions) { this.permissions = permissions; return this; }

        public JwtClaims build() { return new JwtClaims(this); }
    }
}
