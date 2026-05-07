package com.ghatana.digitalmarketing.domain.connector;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable entity representing a YouTube/CTV connector configuration.
 *
 * @doc.type class
 * @doc.purpose Domain entity for YouTube/CTV connector (P3-003)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmYouTubeCtvConnector {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String displayName;
    private final String channelId;
    private final String accessToken;
    private final DmYouTubeCtvConnectorStatus status;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DmYouTubeCtvConnector(Builder b) {
        this.id            = b.id;
        this.tenantId      = b.tenantId;
        this.workspaceId   = b.workspaceId;
        this.displayName   = b.displayName;
        this.channelId     = b.channelId;
        this.accessToken   = b.accessToken;
        this.status        = b.status;
        this.failureReason = b.failureReason;
        this.createdAt     = b.createdAt;
        this.updatedAt     = b.updatedAt;
    }

    public DmYouTubeCtvConnector activate() {
        if (status != DmYouTubeCtvConnectorStatus.PENDING) {
            throw new IllegalStateException("activate requires PENDING status, was: " + status);
        }
        return toBuilder().status(DmYouTubeCtvConnectorStatus.ACTIVE).failureReason(null)
            .updatedAt(Instant.now()).build();
    }

    public DmYouTubeCtvConnector markFailed(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return toBuilder().status(DmYouTubeCtvConnectorStatus.FAILED).failureReason(reason)
            .updatedAt(Instant.now()).build();
    }

    public DmYouTubeCtvConnector suspend() {
        if (status != DmYouTubeCtvConnectorStatus.ACTIVE) {
            throw new IllegalStateException("suspend requires ACTIVE status, was: " + status);
        }
        return toBuilder().status(DmYouTubeCtvConnectorStatus.SUSPENDED)
            .updatedAt(Instant.now()).build();
    }

    public DmYouTubeCtvConnector reactivate() {
        if (status != DmYouTubeCtvConnectorStatus.SUSPENDED) {
            throw new IllegalStateException("reactivate requires SUSPENDED status, was: " + status);
        }
        return toBuilder().status(DmYouTubeCtvConnectorStatus.ACTIVE)
            .updatedAt(Instant.now()).build();
    }

    public DmYouTubeCtvConnector disable() {
        return toBuilder().status(DmYouTubeCtvConnectorStatus.DISABLED)
            .updatedAt(Instant.now()).build();
    }

    public String getId()                { return id; }
    public String getTenantId()          { return tenantId; }
    public String getWorkspaceId()       { return workspaceId; }
    public String getDisplayName()       { return displayName; }
    public String getChannelId()         { return channelId; }
    public String getAccessToken()       { return accessToken; }
    public DmYouTubeCtvConnectorStatus getStatus() { return status; }
    public String getFailureReason()     { return failureReason; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmYouTubeCtvConnector)) return false;
        return id.equals(((DmYouTubeCtvConnector) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmYouTubeCtvConnector{id='" + id + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).workspaceId(workspaceId)
            .displayName(displayName).channelId(channelId)
            .accessToken(accessToken).status(status).failureReason(failureReason)
            .createdAt(createdAt).updatedAt(updatedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, displayName, channelId, accessToken, failureReason;
        private DmYouTubeCtvConnectorStatus status;
        private Instant createdAt, updatedAt;

        public Builder id(String v)                      { this.id = v; return this; }
        public Builder tenantId(String v)                { this.tenantId = v; return this; }
        public Builder workspaceId(String v)             { this.workspaceId = v; return this; }
        public Builder displayName(String v)             { this.displayName = v; return this; }
        public Builder channelId(String v)               { this.channelId = v; return this; }
        public Builder accessToken(String v)             { this.accessToken = v; return this; }
        public Builder status(DmYouTubeCtvConnectorStatus v) { this.status = v; return this; }
        public Builder failureReason(String v)           { this.failureReason = v; return this; }
        public Builder createdAt(Instant v)              { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v)              { this.updatedAt = v; return this; }

        public DmYouTubeCtvConnector build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            Objects.requireNonNull(accessToken, "accessToken must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmYouTubeCtvConnector(this);
        }
    }
}
