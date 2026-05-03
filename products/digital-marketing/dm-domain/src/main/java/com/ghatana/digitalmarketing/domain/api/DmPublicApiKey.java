package com.ghatana.digitalmarketing.domain.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entity representing a public API credential (API key).
 *
 * @doc.type class
 * @doc.purpose Domain entity for public API platform credentials (DMOS-F5-002)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmPublicApiKey {

    private final String id;
    private final String tenantId;
    private final String displayName;
    private final String keyHash;
    private final List<String> scopes;
    private final DmPublicApiKeyStatus status;
    private final Instant expiresAt;
    private final Instant lastUsedAt;
    private final Instant createdAt;
    private final Instant revokedAt;

    private DmPublicApiKey(Builder b) {
        this.id          = b.id;
        this.tenantId    = b.tenantId;
        this.displayName = b.displayName;
        this.keyHash     = b.keyHash;
        this.scopes      = List.copyOf(b.scopes);
        this.status      = b.status;
        this.expiresAt   = b.expiresAt;
        this.lastUsedAt  = b.lastUsedAt;
        this.createdAt   = b.createdAt;
        this.revokedAt   = b.revokedAt;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public DmPublicApiKey revoke() {
        if (status == DmPublicApiKeyStatus.REVOKED) {
            throw new IllegalStateException("Key already revoked");
        }
        return toBuilder().status(DmPublicApiKeyStatus.REVOKED).revokedAt(Instant.now()).build();
    }

    public DmPublicApiKey recordUsage() {
        return toBuilder().lastUsedAt(Instant.now()).build();
    }

    public String getId()                  { return id; }
    public String getTenantId()            { return tenantId; }
    public String getDisplayName()         { return displayName; }
    public String getKeyHash()             { return keyHash; }
    public List<String> getScopes()        { return scopes; }
    public DmPublicApiKeyStatus getStatus() { return status; }
    public Instant getExpiresAt()          { return expiresAt; }
    public Instant getLastUsedAt()         { return lastUsedAt; }
    public Instant getCreatedAt()          { return createdAt; }
    public Instant getRevokedAt()          { return revokedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmPublicApiKey)) return false;
        return id.equals(((DmPublicApiKey) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmPublicApiKey{id='" + id + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).displayName(displayName).keyHash(keyHash)
            .scopes(scopes).status(status).expiresAt(expiresAt).lastUsedAt(lastUsedAt)
            .createdAt(createdAt).revokedAt(revokedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, displayName, keyHash;
        private List<String> scopes = List.of();
        private DmPublicApiKeyStatus status;
        private Instant expiresAt, lastUsedAt, createdAt, revokedAt;

        public Builder id(String v)                   { this.id = v; return this; }
        public Builder tenantId(String v)             { this.tenantId = v; return this; }
        public Builder displayName(String v)          { this.displayName = v; return this; }
        public Builder keyHash(String v)              { this.keyHash = v; return this; }
        public Builder scopes(List<String> v)         { this.scopes = v; return this; }
        public Builder status(DmPublicApiKeyStatus v) { this.status = v; return this; }
        public Builder expiresAt(Instant v)           { this.expiresAt = v; return this; }
        public Builder lastUsedAt(Instant v)          { this.lastUsedAt = v; return this; }
        public Builder createdAt(Instant v)           { this.createdAt = v; return this; }
        public Builder revokedAt(Instant v)           { this.revokedAt = v; return this; }

        public DmPublicApiKey build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            Objects.requireNonNull(keyHash, "keyHash must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmPublicApiKey(this);
        }
    }
}
