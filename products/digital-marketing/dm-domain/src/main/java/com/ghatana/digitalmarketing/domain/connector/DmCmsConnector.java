package com.ghatana.digitalmarketing.domain.connector;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable entity representing a CMS connector configuration.
 *
 * @doc.type class
 * @doc.purpose Domain entity for CMS connector (P3-003)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmCmsConnector {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String displayName;
    private final String cmsType;
    private final String apiUrl;
    private final String accessToken;
    private final Map<String, String> configuration;
    private final DmCmsConnectorStatus status;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DmCmsConnector(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.workspaceId = builder.workspaceId;
        this.displayName = builder.displayName;
        this.cmsType = builder.cmsType;
        this.apiUrl = builder.apiUrl;
        this.accessToken = builder.accessToken;
        this.configuration = Map.copyOf(builder.configuration);
        this.status = builder.status;
        this.failureReason = builder.failureReason;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public DmCmsConnector activate() {
        if (status != DmCmsConnectorStatus.PENDING) {
            throw new IllegalStateException("activate requires PENDING status, was: " + status);
        }
        return toBuilder().status(DmCmsConnectorStatus.ACTIVE).failureReason(null)
            .updatedAt(Instant.now()).build();
    }

    public DmCmsConnector markFailed(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return toBuilder().status(DmCmsConnectorStatus.FAILED).failureReason(reason)
            .updatedAt(Instant.now()).build();
    }

    public DmCmsConnector suspend() {
        if (status != DmCmsConnectorStatus.ACTIVE) {
            throw new IllegalStateException("suspend requires ACTIVE status, was: " + status);
        }
        return toBuilder().status(DmCmsConnectorStatus.SUSPENDED)
            .updatedAt(Instant.now()).build();
    }

    public DmCmsConnector reactivate() {
        if (status != DmCmsConnectorStatus.SUSPENDED) {
            throw new IllegalStateException("reactivate requires SUSPENDED status, was: " + status);
        }
        return toBuilder().status(DmCmsConnectorStatus.ACTIVE)
            .updatedAt(Instant.now()).build();
    }

    public DmCmsConnector disable() {
        return toBuilder().status(DmCmsConnectorStatus.DISABLED)
            .updatedAt(Instant.now()).build();
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getWorkspaceId() { return workspaceId; }
    public String getDisplayName() { return displayName; }
    public String getCmsType() { return cmsType; }
    public String getApiUrl() { return apiUrl; }
    public String getAccessToken() { return accessToken; }
    public Map<String, String> getConfiguration() { return configuration; }
    public DmCmsConnectorStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmCmsConnector)) return false;
        return id.equals(((DmCmsConnector) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmCmsConnector{id='" + id + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).workspaceId(workspaceId)
            .displayName(displayName).cmsType(cmsType).apiUrl(apiUrl)
            .accessToken(accessToken).configuration(configuration).status(status)
            .failureReason(failureReason).createdAt(createdAt).updatedAt(updatedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, displayName, cmsType, apiUrl, accessToken, failureReason;
        private Map<String, String> configuration = Map.of();
        private DmCmsConnectorStatus status;
        private Instant createdAt, updatedAt;

        public Builder id(String v)                      { this.id = v; return this; }
        public Builder tenantId(String v)                { this.tenantId = v; return this; }
        public Builder workspaceId(String v)             { this.workspaceId = v; return this; }
        public Builder displayName(String v)             { this.displayName = v; return this; }
        public Builder cmsType(String v)                  { this.cmsType = v; return this; }
        public Builder apiUrl(String v)                   { this.apiUrl = v; return this; }
        public Builder accessToken(String v)             { this.accessToken = v; return this; }
        public Builder configuration(Map<String, String> v) { this.configuration = v; return this; }
        public Builder status(DmCmsConnectorStatus v)    { this.status = v; return this; }
        public Builder failureReason(String v)            { this.failureReason = v; return this; }
        public Builder createdAt(Instant v)              { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v)              { this.updatedAt = v; return this; }

        public DmCmsConnector build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            if (cmsType == null || cmsType.isBlank()) throw new IllegalArgumentException("cmsType must not be blank");
            Objects.requireNonNull(accessToken, "accessToken must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmCmsConnector(this);
        }
    }
}
