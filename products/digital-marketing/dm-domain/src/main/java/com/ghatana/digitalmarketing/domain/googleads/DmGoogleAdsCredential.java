package com.ghatana.digitalmarketing.domain.googleads;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entity representing Google Ads OAuth credential set for a tenant.
 *
 * <p>PII-safe implementation (DMOS-P1-015): Access and refresh tokens are encrypted at rest
 * using AES-GCM. The domain model stores encrypted tokens, and the repository layer handles
 * encryption/decryption transparently.</p>
 *
 * @doc.type class
 * @doc.purpose Stores Google Ads OAuth tokens and account link (DMOS-F2-007, DMOS-P1-015)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmGoogleAdsCredential {

    private final String id;
    private final String tenantId;
    private final String connectorId;
    private final String accessToken; // Encrypted at rest (DMOS-P1-015)
    private final String refreshToken; // Encrypted at rest (DMOS-P1-015)
    private final Instant expiresAt;
    private final List<String> scopes;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final boolean revoked;
    private final Instant revokedAt;

    private DmGoogleAdsCredential(Builder builder) {
        this.id          = builder.id;
        this.tenantId    = builder.tenantId;
        this.connectorId = builder.connectorId;
        this.accessToken = builder.accessToken;
        this.refreshToken = builder.refreshToken;
        this.expiresAt   = builder.expiresAt;
        this.scopes      = List.copyOf(builder.scopes);
        this.createdAt   = builder.createdAt;
        this.updatedAt   = builder.updatedAt;
        this.revoked     = builder.revoked;
        this.revokedAt   = builder.revokedAt;
    }

    /** Returns a refreshed credential with a new access token. */
    public DmGoogleAdsCredential refresh(String newAccessToken, Instant newExpiresAt) {
        Objects.requireNonNull(newAccessToken, "newAccessToken must not be null");
        Objects.requireNonNull(newExpiresAt, "newExpiresAt must not be null");
        if (newAccessToken.isBlank()) throw new IllegalArgumentException("newAccessToken must not be blank");
        return toBuilder().accessToken(newAccessToken).expiresAt(newExpiresAt)
            .updatedAt(Instant.now()).build();
    }

    /** Returns a revoked credential (DMOS-P1-015). */
    public DmGoogleAdsCredential revoke() {
        if (revoked) {
            throw new IllegalStateException("Credential is already revoked");
        }
        return toBuilder().revoked(true).revokedAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    /** Returns true if the access token is expired. */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /** Returns true if the credential is revoked (DMOS-P1-015). */
    public boolean isRevoked() {
        return revoked;
    }

    /** Returns true if the credential is valid (not expired and not revoked). */
    public boolean isValid() {
        return !isExpired() && !isRevoked();
    }

    public String getId()          { return id; }
    public String getTenantId()    { return tenantId; }
    public String getConnectorId() { return connectorId; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public Instant getExpiresAt()  { return expiresAt; }
    public List<String> getScopes() { return scopes; }
    public Instant getCreatedAt()  { return createdAt; }
    public Instant getUpdatedAt()  { return updatedAt; }
    public Instant getRevokedAt()  { return revokedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmGoogleAdsCredential)) return false;
        return id.equals(((DmGoogleAdsCredential) o).id);
    }

    @Override public int hashCode() { return id.hashCode(); }

    @Override public String toString() {
        // Redact tokens from logs (DMOS-P1-015)
        return "DmGoogleAdsCredential{id='" + id + "', tenantId='" + tenantId + "', revoked=" + revoked + "}";
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).connectorId(connectorId)
            .accessToken(accessToken).refreshToken(refreshToken).expiresAt(expiresAt)
            .scopes(scopes).createdAt(createdAt).updatedAt(updatedAt)
            .revoked(revoked).revokedAt(revokedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String tenantId;
        private String connectorId;
        private String accessToken;
        private String refreshToken;
        private Instant expiresAt;
        private List<String> scopes = List.of();
        private Instant createdAt;
        private Instant updatedAt;
        private boolean revoked = false;
        private Instant revokedAt;

        public Builder id(String id)                 { this.id = id; return this; }
        public Builder tenantId(String t)            { this.tenantId = t; return this; }
        public Builder connectorId(String c)         { this.connectorId = c; return this; }
        public Builder accessToken(String a)         { this.accessToken = a; return this; }
        public Builder refreshToken(String r)        { this.refreshToken = r; return this; }
        public Builder expiresAt(Instant e)          { this.expiresAt = e; return this; }
        public Builder scopes(List<String> s)        { this.scopes = s; return this; }
        public Builder createdAt(Instant t)          { this.createdAt = t; return this; }
        public Builder updatedAt(Instant t)          { this.updatedAt = t; return this; }
        public Builder revoked(boolean r)            { this.revoked = r; return this; }
        public Builder revokedAt(Instant r)         { this.revokedAt = r; return this; }

        public DmGoogleAdsCredential build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (connectorId == null || connectorId.isBlank()) throw new IllegalArgumentException("connectorId must not be blank");
            Objects.requireNonNull(accessToken, "accessToken must not be null");
            Objects.requireNonNull(refreshToken, "refreshToken must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(scopes, "scopes must not be null");
            return new DmGoogleAdsCredential(this);
        }
    }
}
