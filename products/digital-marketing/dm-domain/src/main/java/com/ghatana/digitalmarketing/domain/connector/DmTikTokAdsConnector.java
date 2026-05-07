package com.ghatana.digitalmarketing.domain.connector;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable entity representing a TikTok Ads connector configuration.
 *
 * @doc.type class
 * @doc.purpose Domain entity for TikTok Ads connector (P3-003)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmTikTokAdsConnector {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String displayName;
    private final String advertiserId;
    private final String accessToken;
    private final DmTikTokAdsConnectorStatus status;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DmTikTokAdsConnector(Builder b) {
        this.id            = b.id;
        this.tenantId      = b.tenantId;
        this.workspaceId   = b.workspaceId;
        this.displayName   = b.displayName;
        this.advertiserId  = b.advertiserId;
        this.accessToken   = b.accessToken;
        this.status        = b.status;
        this.failureReason = b.failureReason;
        this.createdAt     = b.createdAt;
        this.updatedAt     = b.updatedAt;
    }

    public DmTikTokAdsConnector activate() {
        if (status != DmTikTokAdsConnectorStatus.PENDING) {
            throw new IllegalStateException("activate requires PENDING status, was: " + status);
        }
        return toBuilder().status(DmTikTokAdsConnectorStatus.ACTIVE).failureReason(null)
            .updatedAt(Instant.now()).build();
    }

    public DmTikTokAdsConnector markFailed(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return toBuilder().status(DmTikTokAdsConnectorStatus.FAILED).failureReason(reason)
            .updatedAt(Instant.now()).build();
    }

    public DmTikTokAdsConnector suspend() {
        if (status != DmTikTokAdsConnectorStatus.ACTIVE) {
            throw new IllegalStateException("suspend requires ACTIVE status, was: " + status);
        }
        return toBuilder().status(DmTikTokAdsConnectorStatus.SUSPENDED)
            .updatedAt(Instant.now()).build();
    }

    public DmTikTokAdsConnector reactivate() {
        if (status != DmTikTokAdsConnectorStatus.SUSPENDED) {
            throw new IllegalStateException("reactivate requires SUSPENDED status, was: " + status);
        }
        return toBuilder().status(DmTikTokAdsConnectorStatus.ACTIVE)
            .updatedAt(Instant.now()).build();
    }

    public DmTikTokAdsConnector disable() {
        return toBuilder().status(DmTikTokAdsConnectorStatus.DISABLED)
            .updatedAt(Instant.now()).build();
    }

    public String getId()                { return id; }
    public String getTenantId()          { return tenantId; }
    public String getWorkspaceId()       { return workspaceId; }
    public String getDisplayName()       { return displayName; }
    public String getAdvertiserId()      { return advertiserId; }
    public String getAccessToken()       { return accessToken; }
    public DmTikTokAdsConnectorStatus getStatus() { return status; }
    public String getFailureReason()     { return failureReason; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmTikTokAdsConnector)) return false;
        return id.equals(((DmTikTokAdsConnector) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmTikTokAdsConnector{id='" + id + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).workspaceId(workspaceId)
            .displayName(displayName).advertiserId(advertiserId)
            .accessToken(accessToken).status(status).failureReason(failureReason)
            .createdAt(createdAt).updatedAt(updatedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, displayName, advertiserId, accessToken, failureReason;
        private DmTikTokAdsConnectorStatus status;
        private Instant createdAt, updatedAt;

        public Builder id(String v)                      { this.id = v; return this; }
        public Builder tenantId(String v)                { this.tenantId = v; return this; }
        public Builder workspaceId(String v)             { this.workspaceId = v; return this; }
        public Builder displayName(String v)             { this.displayName = v; return this; }
        public Builder advertiserId(String v)            { this.advertiserId = v; return this; }
        public Builder accessToken(String v)             { this.accessToken = v; return this; }
        public Builder status(DmTikTokAdsConnectorStatus v) { this.status = v; return this; }
        public Builder failureReason(String v)           { this.failureReason = v; return this; }
        public Builder createdAt(Instant v)              { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v)              { this.updatedAt = v; return this; }

        public DmTikTokAdsConnector build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            Objects.requireNonNull(accessToken, "accessToken must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmTikTokAdsConnector(this);
        }
    }
}
