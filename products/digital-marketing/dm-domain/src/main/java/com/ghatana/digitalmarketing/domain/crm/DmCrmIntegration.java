package com.ghatana.digitalmarketing.domain.crm;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable entity representing an external CRM integration configuration.
 *
 * @doc.type class
 * @doc.purpose Domain entity for external CRM integration (DMOS-F4-002)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmCrmIntegration {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String crmProvider;
    private final String displayName;
    private final String apiEndpoint;
    private final String credentialRef;
    private final DmCrmIntegrationStatus status;
    private final String failureReason;
    private final Instant lastSyncAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DmCrmIntegration(Builder b) {
        this.id             = b.id;
        this.tenantId       = b.tenantId;
        this.workspaceId    = b.workspaceId;
        this.crmProvider    = b.crmProvider;
        this.displayName    = b.displayName;
        this.apiEndpoint    = b.apiEndpoint;
        this.credentialRef  = b.credentialRef;
        this.status         = b.status;
        this.failureReason  = b.failureReason;
        this.lastSyncAt     = b.lastSyncAt;
        this.createdAt      = b.createdAt;
        this.updatedAt      = b.updatedAt;
    }

    public DmCrmIntegration activate() {
        return toBuilder().status(DmCrmIntegrationStatus.ACTIVE).failureReason(null)
            .updatedAt(Instant.now()).build();
    }

    public DmCrmIntegration recordSync() {
        return toBuilder().lastSyncAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    public DmCrmIntegration markFailed(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return toBuilder().status(DmCrmIntegrationStatus.FAILED).failureReason(reason)
            .updatedAt(Instant.now()).build();
    }

    public String getId()               { return id; }
    public String getTenantId()         { return tenantId; }
    public String getWorkspaceId()      { return workspaceId; }
    public String getCrmProvider()      { return crmProvider; }
    public String getDisplayName()      { return displayName; }
    public String getApiEndpoint()      { return apiEndpoint; }
    public String getCredentialRef()    { return credentialRef; }
    public DmCrmIntegrationStatus getStatus() { return status; }
    public String getFailureReason()    { return failureReason; }
    public Instant getLastSyncAt()      { return lastSyncAt; }
    public Instant getCreatedAt()       { return createdAt; }
    public Instant getUpdatedAt()       { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmCrmIntegration)) return false;
        return id.equals(((DmCrmIntegration) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmCrmIntegration{id='" + id + "', provider='" + crmProvider + "'}";
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).workspaceId(workspaceId)
            .crmProvider(crmProvider).displayName(displayName).apiEndpoint(apiEndpoint)
            .credentialRef(credentialRef).status(status).failureReason(failureReason)
            .lastSyncAt(lastSyncAt).createdAt(createdAt).updatedAt(updatedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, crmProvider, displayName, apiEndpoint, credentialRef, failureReason;
        private DmCrmIntegrationStatus status;
        private Instant lastSyncAt, createdAt, updatedAt;

        public Builder id(String v)                   { this.id = v; return this; }
        public Builder tenantId(String v)             { this.tenantId = v; return this; }
        public Builder workspaceId(String v)          { this.workspaceId = v; return this; }
        public Builder crmProvider(String v)          { this.crmProvider = v; return this; }
        public Builder displayName(String v)          { this.displayName = v; return this; }
        public Builder apiEndpoint(String v)          { this.apiEndpoint = v; return this; }
        public Builder credentialRef(String v)        { this.credentialRef = v; return this; }
        public Builder status(DmCrmIntegrationStatus v) { this.status = v; return this; }
        public Builder failureReason(String v)        { this.failureReason = v; return this; }
        public Builder lastSyncAt(Instant v)          { this.lastSyncAt = v; return this; }
        public Builder createdAt(Instant v)           { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v)           { this.updatedAt = v; return this; }

        public DmCrmIntegration build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (crmProvider == null || crmProvider.isBlank()) throw new IllegalArgumentException("crmProvider must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmCrmIntegration(this);
        }
    }
}
