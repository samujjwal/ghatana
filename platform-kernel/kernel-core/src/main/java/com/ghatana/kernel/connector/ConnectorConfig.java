package com.ghatana.kernel.connector;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable configuration for an external platform connector.
 *
 * @doc.type class
 * @doc.purpose Stores connector configuration and metadata (KERNEL-P1)
 * @doc.layer core
 * @doc.pattern Entity
 */
public final class ConnectorConfig {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String name;
    private final ConnectorType connectorType;
    private final ConnectorStatus status;
    private final Map<String, String> settings;
    private final String externalAccountId;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant lastHealthCheckAt;

    private ConnectorConfig(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.connectorType = Objects.requireNonNull(builder.connectorType, "connectorType must not be null");
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.settings = Map.copyOf(builder.settings);
        this.externalAccountId = builder.externalAccountId;
        this.failureReason = builder.failureReason;
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt must not be null");
        this.lastHealthCheckAt = builder.lastHealthCheckAt;
    }

    public ConnectorConfig activate() {
        if (status == ConnectorStatus.DISABLED) {
            throw new IllegalStateException("Cannot activate a DISABLED connector");
        }
        return toBuilder()
            .status(ConnectorStatus.ACTIVE)
            .lastHealthCheckAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    public ConnectorConfig deactivate() {
        if (status != ConnectorStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE connectors can be deactivated, was: " + status);
        }
        return toBuilder()
            .status(ConnectorStatus.INACTIVE)
            .updatedAt(Instant.now())
            .build();
    }

    public ConnectorConfig markAuthFailed(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return toBuilder()
            .status(ConnectorStatus.AUTH_FAILED)
            .failureReason(reason)
            .updatedAt(Instant.now())
            .build();
    }

    public ConnectorConfig disable() {
        return toBuilder()
            .status(ConnectorStatus.DISABLED)
            .updatedAt(Instant.now())
            .build();
    }

    public boolean isOperational() {
        return status == ConnectorStatus.ACTIVE;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getWorkspaceId() { return workspaceId; }
    public String getName() { return name; }
    public ConnectorType getConnectorType() { return connectorType; }
    public ConnectorStatus getStatus() { return status; }
    public Map<String, String> getSettings() { return settings; }
    public String getExternalAccountId() { return externalAccountId; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastHealthCheckAt() { return lastHealthCheckAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectorConfig)) return false;
        return id.equals(((ConnectorConfig) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "ConnectorConfig{id='" + id + "', type=" + connectorType + ", status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .name(name)
            .connectorType(connectorType)
            .status(status)
            .settings(settings)
            .externalAccountId(externalAccountId)
            .failureReason(failureReason)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .lastHealthCheckAt(lastHealthCheckAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String tenantId;
        private String workspaceId;
        private String name;
        private ConnectorType connectorType;
        private ConnectorStatus status = ConnectorStatus.PENDING;
        private Map<String, String> settings = Map.of();
        private String externalAccountId;
        private String failureReason;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant lastHealthCheckAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder workspaceId(String workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder connectorType(ConnectorType connectorType) { this.connectorType = connectorType; return this; }
        public Builder status(ConnectorStatus status) { this.status = status; return this; }
        public Builder settings(Map<String, String> settings) { this.settings = settings; return this; }
        public Builder externalAccountId(String externalAccountId) { this.externalAccountId = externalAccountId; return this; }
        public Builder failureReason(String failureReason) { this.failureReason = failureReason; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder lastHealthCheckAt(Instant lastHealthCheckAt) { this.lastHealthCheckAt = lastHealthCheckAt; return this; }

        public ConnectorConfig build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (workspaceId == null || workspaceId.isBlank()) throw new IllegalArgumentException("workspaceId must not be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            if (createdAt == null) createdAt = Instant.now();
            if (updatedAt == null) updatedAt = createdAt;
            return new ConnectorConfig(this);
        }
    }
}
