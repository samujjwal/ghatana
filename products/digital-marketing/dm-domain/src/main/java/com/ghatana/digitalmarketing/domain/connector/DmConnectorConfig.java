package com.ghatana.digitalmarketing.domain.connector;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable entity representing a connector configuration for an external marketing platform.
 *
 * @doc.type class
 * @doc.purpose Stores connector credentials and status for external platform integrations (DMOS-F2-006)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmConnectorConfig {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String name;
    private final DmConnectorType connectorType;
    private final DmConnectorStatus status;
    private final Map<String, String> settings;
    private final String externalAccountId;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant lastHealthCheckAt;

    private DmConnectorConfig(Builder builder) {
        this.id                 = builder.id;
        this.tenantId           = builder.tenantId;
        this.workspaceId        = builder.workspaceId;
        this.name               = builder.name;
        this.connectorType      = builder.connectorType;
        this.status             = builder.status;
        this.settings           = Map.copyOf(builder.settings);
        this.externalAccountId  = builder.externalAccountId;
        this.failureReason      = builder.failureReason;
        this.createdAt          = builder.createdAt;
        this.updatedAt          = builder.updatedAt;
        this.lastHealthCheckAt  = builder.lastHealthCheckAt;
    }

    /** Activate the connector after successful health check. */
    public DmConnectorConfig activate() {
        if (status == DmConnectorStatus.DISABLED) {
            throw new IllegalStateException("Cannot activate a DISABLED connector");
        }
        return toBuilder().status(DmConnectorStatus.ACTIVE)
            .lastHealthCheckAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    /** Suspend the connector (temporarily disable). */
    public DmConnectorConfig suspend() {
        if (status != DmConnectorStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE connectors can be suspended, was: " + status);
        }
        return toBuilder().status(DmConnectorStatus.SUSPENDED).updatedAt(Instant.now()).build();
    }

    /** Re-enable a suspended connector. */
    public DmConnectorConfig reactivate() {
        if (status != DmConnectorStatus.SUSPENDED) {
            throw new IllegalStateException("Only SUSPENDED connectors can be reactivated, was: " + status);
        }
        return toBuilder().status(DmConnectorStatus.ACTIVE).updatedAt(Instant.now()).build();
    }

    /** Mark as auth-failed with a reason. */
    public DmConnectorConfig markAuthFailed(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return toBuilder().status(DmConnectorStatus.AUTH_FAILED)
            .failureReason(reason).updatedAt(Instant.now()).build();
    }

    /** Permanently disable the connector. */
    public DmConnectorConfig disable() {
        return toBuilder().status(DmConnectorStatus.DISABLED).updatedAt(Instant.now()).build();
    }

    /** Whether the connector can process requests. */
    public boolean isOperational() {
        return status == DmConnectorStatus.ACTIVE;
    }

    public String getId()                    { return id; }
    public String getTenantId()              { return tenantId; }
    public String getWorkspaceId()           { return workspaceId; }
    public String getName()                  { return name; }
    public DmConnectorType getConnectorType() { return connectorType; }
    public DmConnectorStatus getStatus()     { return status; }
    public Map<String, String> getSettings() { return settings; }
    public String getExternalAccountId()     { return externalAccountId; }
    public String getFailureReason()         { return failureReason; }
    public Instant getCreatedAt()            { return createdAt; }
    public Instant getUpdatedAt()            { return updatedAt; }
    public Instant getLastHealthCheckAt()    { return lastHealthCheckAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmConnectorConfig)) return false;
        return id.equals(((DmConnectorConfig) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "DmConnectorConfig{id='" + id + "', type=" + connectorType + ", status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id).tenantId(tenantId).workspaceId(workspaceId).name(name)
            .connectorType(connectorType).status(status).settings(settings)
            .externalAccountId(externalAccountId).failureReason(failureReason)
            .createdAt(createdAt).updatedAt(updatedAt).lastHealthCheckAt(lastHealthCheckAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String tenantId;
        private String workspaceId;
        private String name;
        private DmConnectorType connectorType;
        private DmConnectorStatus status;
        private Map<String, String> settings = Map.of();
        private String externalAccountId;
        private String failureReason;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant lastHealthCheckAt;

        public Builder id(String id)                                  { this.id = id; return this; }
        public Builder tenantId(String t)                             { this.tenantId = t; return this; }
        public Builder workspaceId(String w)                          { this.workspaceId = w; return this; }
        public Builder name(String n)                                 { this.name = n; return this; }
        public Builder connectorType(DmConnectorType t)               { this.connectorType = t; return this; }
        public Builder status(DmConnectorStatus s)                    { this.status = s; return this; }
        public Builder settings(Map<String, String> s)                { this.settings = s; return this; }
        public Builder externalAccountId(String e)                    { this.externalAccountId = e; return this; }
        public Builder failureReason(String r)                        { this.failureReason = r; return this; }
        public Builder createdAt(Instant t)                           { this.createdAt = t; return this; }
        public Builder updatedAt(Instant t)                           { this.updatedAt = t; return this; }
        public Builder lastHealthCheckAt(Instant t)                   { this.lastHealthCheckAt = t; return this; }

        public DmConnectorConfig build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            Objects.requireNonNull(connectorType, "connectorType must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(settings, "settings must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmConnectorConfig(this);
        }
    }
}
