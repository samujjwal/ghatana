package com.ghatana.digitalmarketing.domain.connector;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable entity representing a Meta Ads connector configuration.
 *
 * @doc.type class
 * @doc.purpose Domain entity for Meta Ads connector (DMOS-F4-001)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmMetaAdsConnector {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String displayName;
    private final String appId;
    private final String accountId;
    private final String accessToken;
    private final DmMetaAdsConnectorStatus status;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DmMetaAdsConnector(Builder b) {
        this.id            = b.id;
        this.tenantId      = b.tenantId;
        this.workspaceId   = b.workspaceId;
        this.displayName   = b.displayName;
        this.appId         = b.appId;
        this.accountId     = b.accountId;
        this.accessToken   = b.accessToken;
        this.status        = b.status;
        this.failureReason = b.failureReason;
        this.createdAt     = b.createdAt;
        this.updatedAt     = b.updatedAt;
    }

    public DmMetaAdsConnector activate() {
        if (status != DmMetaAdsConnectorStatus.PENDING) {
            throw new IllegalStateException("activate requires PENDING status, was: " + status);
        }
        return toBuilder().status(DmMetaAdsConnectorStatus.ACTIVE).failureReason(null)
            .updatedAt(Instant.now()).build();
    }

    public DmMetaAdsConnector markFailed(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return toBuilder().status(DmMetaAdsConnectorStatus.FAILED).failureReason(reason)
            .updatedAt(Instant.now()).build();
    }

    public String getId()                { return id; }
    public String getTenantId()          { return tenantId; }
    public String getWorkspaceId()       { return workspaceId; }
    public String getDisplayName()       { return displayName; }
    public String getAppId()             { return appId; }
    public String getAccountId()         { return accountId; }
    public String getAccessToken()       { return accessToken; }
    public DmMetaAdsConnectorStatus getStatus() { return status; }
    public String getFailureReason()     { return failureReason; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmMetaAdsConnector)) return false;
        return id.equals(((DmMetaAdsConnector) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmMetaAdsConnector{id='" + id + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).workspaceId(workspaceId)
            .displayName(displayName).appId(appId).accountId(accountId)
            .accessToken(accessToken).status(status).failureReason(failureReason)
            .createdAt(createdAt).updatedAt(updatedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, displayName, appId, accountId, accessToken, failureReason;
        private DmMetaAdsConnectorStatus status;
        private Instant createdAt, updatedAt;

        public Builder id(String v)                      { this.id = v; return this; }
        public Builder tenantId(String v)                { this.tenantId = v; return this; }
        public Builder workspaceId(String v)             { this.workspaceId = v; return this; }
        public Builder displayName(String v)             { this.displayName = v; return this; }
        public Builder appId(String v)                   { this.appId = v; return this; }
        public Builder accountId(String v)               { this.accountId = v; return this; }
        public Builder accessToken(String v)             { this.accessToken = v; return this; }
        public Builder status(DmMetaAdsConnectorStatus v) { this.status = v; return this; }
        public Builder failureReason(String v)           { this.failureReason = v; return this; }
        public Builder createdAt(Instant v)              { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v)              { this.updatedAt = v; return this; }

        public DmMetaAdsConnector build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            Objects.requireNonNull(accessToken, "accessToken must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmMetaAdsConnector(this);
        }
    }
}
