package com.ghatana.kernel.connector;

import java.time.Instant;
import java.util.Objects;

/**
 * Health check result for a connector.
 *
 * @doc.type class
 * @doc.purpose Health check result for connector monitoring (KERNEL-P1)
 * @doc.layer core
 * @doc.pattern Value Object
 */
public final class ConnectorHealth {

    private final String connectorId;
    private final boolean healthy;
    private final String status;
    private final String message;
    private final Instant checkedAt;
    private final long latencyMs;

    private ConnectorHealth(Builder builder) {
        this.connectorId = Objects.requireNonNull(builder.connectorId, "connectorId must not be null");
        this.healthy = builder.healthy;
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.message = builder.message;
        this.checkedAt = Objects.requireNonNull(builder.checkedAt, "checkedAt must not be null");
        this.latencyMs = builder.latencyMs;
    }

    public String getConnectorId() { return connectorId; }
    public boolean isHealthy() { return healthy; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getCheckedAt() { return checkedAt; }
    public long getLatencyMs() { return latencyMs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectorHealth)) return false;
        return connectorId.equals(((ConnectorHealth) o).connectorId) && checkedAt.equals(((ConnectorHealth) o).checkedAt);
    }

    @Override
    public int hashCode() { return Objects.hash(connectorId, checkedAt); }

    @Override
    public String toString() {
        return "ConnectorHealth{connectorId='" + connectorId + "', healthy=" + healthy + ", status='" + status + "'}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String connectorId;
        private boolean healthy = true;
        private String status = "OK";
        private String message;
        private Instant checkedAt = Instant.now();
        private long latencyMs;

        public Builder connectorId(String connectorId) { this.connectorId = connectorId; return this; }
        public Builder healthy(boolean healthy) { this.healthy = healthy; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder checkedAt(Instant checkedAt) { this.checkedAt = checkedAt; return this; }
        public Builder latencyMs(long latencyMs) { this.latencyMs = latencyMs; return this; }

        public ConnectorHealth build() {
            if (connectorId == null || connectorId.isBlank()) {
                throw new IllegalArgumentException("connectorId must not be blank");
            }
            return new ConnectorHealth(this);
        }
    }

    /**
     * Create a healthy health check result.
     */
    public static ConnectorHealth healthy(String connectorId) {
        return builder()
            .connectorId(connectorId)
            .healthy(true)
            .status("OK")
            .build();
    }

    /**
     * Create an unhealthy health check result.
     */
    public static ConnectorHealth unhealthy(String connectorId, String reason) {
        return builder()
            .connectorId(connectorId)
            .healthy(false)
            .status("UNHEALTHY")
            .message(reason)
            .build();
    }
}
