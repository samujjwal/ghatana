package com.ghatana.digitalmarketing.domain.connector;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable entity representing an e-signature connector configuration.
 *
 * @doc.type class
 * @doc.purpose Domain entity for e-signature connector (P3-003)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmEsignatureConnector {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String displayName;
    private final String esignatureProvider;
    private final String apiUrl;
    private final String accessToken;
    private final Map<String, String> configuration;
    private final DmEsignatureConnectorStatus status;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DmEsignatureConnector(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.workspaceId = builder.workspaceId;
        this.displayName = builder.displayName;
        this.esignatureProvider = builder.esignatureProvider;
        this.apiUrl = builder.apiUrl;
        this.accessToken = builder.accessToken;
        this.configuration = Map.copyOf(builder.configuration);
        this.status = builder.status;
        this.failureReason = builder.failureReason;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public DmEsignatureConnector activate() {
        if (status != DmEsignatureConnectorStatus.PENDING) {
            throw new IllegalStateException("activate requires PENDING status, was: " + status);
        }
        return toBuilder().status(DmEsignatureConnectorStatus.ACTIVE).failureReason(null)
            .updatedAt(Instant.now()).build();
    }

    public DmEsignatureConnector markFailed(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return toBuilder().status(DmEsignatureConnectorStatus.FAILED).failureReason(reason)
            .updatedAt(Instant.now()).build();
    }

    public DmEsignatureConnector suspend() {
        if (status != DmEsignatureConnectorStatus.ACTIVE) {
            throw new IllegalStateException("suspend requires ACTIVE status, was: " + status);
        }
        return toBuilder().status(DmEsignatureConnectorStatus.SUSPENDED)
            .updatedAt(Instant.now()).build();
    }

    public DmEsignatureConnector reactivate() {
        if (status != DmEsignatureConnectorStatus.SUSPENDED) {
            throw new IllegalStateException("reactivate requires SUSPENDED status, was: " + status);
        }
        return toBuilder().status(DmEsignatureConnectorStatus.ACTIVE)
            .updatedAt(Instant.now()).build();
    }

    public DmEsignatureConnector disable() {
        return toBuilder().status(DmEsignatureConnectorStatus.DISABLED)
            .updatedAt(Instant.now()).build();
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getWorkspaceId() { return workspaceId; }
    public String getDisplayName() { return displayName; }
    public String getEsignatureProvider() { return esignatureProvider; }
    public String getApiUrl() { return apiUrl; }
    public String getAccessToken() { return accessToken; }
    public Map<String, String> getConfiguration() { return configuration; }
    public DmEsignatureConnectorStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmEsignatureConnector)) return false;
        return id.equals(((DmEsignatureConnector) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmEsignatureConnector{id='" + id + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).workspaceId(workspaceId)
            .displayName(displayName).esignatureProvider(esignatureProvider).apiUrl(apiUrl)
            .accessToken(accessToken).configuration(configuration).status(status)
            .failureReason(failureReason).createdAt(createdAt).updatedAt(updatedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, displayName, esignatureProvider, apiUrl, accessToken, failureReason;
        private Map<String, String> configuration = Map.of();
        private DmEsignatureConnectorStatus status;
        private Instant createdAt, updatedAt;

        public Builder id(String v)                      { this.id = v; return this; }
        public Builder tenantId(String v)                { this.tenantId = v; return this; }
        public Builder workspaceId(String v)             { this.workspaceId = v; return this; }
        public Builder displayName(String v)             { this.displayName = v; return this; }
        public Builder esignatureProvider(String v)        { this.esignatureProvider = v; return this; }
        public Builder apiUrl(String v)                   { this.apiUrl = v; return this; }
        public Builder accessToken(String v)             { this.accessToken = v; return this; }
        public Builder configuration(Map<String, String> v) { this.configuration = v; return this; }
        public Builder status(DmEsignatureConnectorStatus v) { this.status = v; return this; }
        public Builder failureReason(String v)            { this.failureReason = v; return this; }
        public Builder createdAt(Instant v)              { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v)              { this.updatedAt = v; return this; }

        public DmEsignatureConnector build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            if (esignatureProvider == null || esignatureProvider.isBlank()) throw new IllegalArgumentException("esignatureProvider must not be blank");
            Objects.requireNonNull(accessToken, "accessToken must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmEsignatureConnector(this);
        }
    }
}
