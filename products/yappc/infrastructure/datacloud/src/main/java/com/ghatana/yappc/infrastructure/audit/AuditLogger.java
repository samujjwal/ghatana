package com.ghatana.yappc.infrastructure.audit;

import io.activej.promise.Promise;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit logger for security and compliance event tracking.
 *
 * <p><b>Purpose</b><br>
 * Records security-relevant events (authentication, authorization, data access,
 * configuration changes) for compliance, forensics, and security monitoring.
 * Persists to Data-Cloud for durability and queryability.
 *
 * <p><b>Event Types</b><br>
 * - AUTHENTICATION: Login, logout, token refresh<br>
 * - AUTHORIZATION: Permission checks, access grants/denials<br>
 * - DATA_ACCESS: CRUD operations on sensitive data<br>
 * - CONFIG_CHANGE: Settings modifications<br>
 * - SECURITY: Security alerts, anomalies<br>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AuditLogger audit = new DataCloudAuditLogger(client, mapper, metrics);
 *
 * // Log authentication event
 * audit.log(AuditEvent.builder()
 *     .type(EventType.AUTHENTICATION)
 *     .action("user.login")
 *     .userId(userId)
 *     .tenantId(tenantId)
 *     .status(Status.SUCCESS)
 *     .metadata(Map.of("ip", clientIp))
 *     .build());
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Security audit logging
 * @doc.layer infrastructure
 * @doc.pattern Port
 */
public interface AuditLogger {

    /**
     * Logs an audit event.
     *
     * @param event the audit event to log
     * @return promise completing when logged
     */
    Promise<Void> log(AuditEvent event);

    /**
     * Logs an audit event from individual components.
     *
     * @param type event type
     * @param action action performed
     * @param userId user identifier
     * @param tenantId tenant identifier
     * @param status outcome status
     * @param metadata additional context
     * @return promise completing when logged
     */
    default Promise<Void> log(
            EventType type,
            String action,
            String userId,
            String tenantId,
            Status status,
            Map<String, Object> metadata) {
        return log(AuditEvent.builder()
            .type(type)
            .action(action)
            .userId(userId)
            .tenantId(tenantId)
            .status(status)
            .metadata(metadata)
            .build());
    }

    /**
     * Creates a no-op audit logger for testing.
     *
     * @return no-op implementation
     */
    static AuditLogger noop() {
        return event -> Promise.complete();
    }

    /**
     * Audit event types.
     */
    enum EventType {
        AUTHENTICATION,
        AUTHORIZATION,
        DATA_ACCESS,
        CONFIG_CHANGE,
        SECURITY,
        SYSTEM,
        BUSINESS
    }

    /**
     * Event outcome status.
     */
    enum Status {
        SUCCESS,
        FAILURE,
        DENIED,
        WARNING,
        INFO
    }

    /**
     * Audit event record.
     */
    class AuditEvent {
        private final UUID id;
        private final Instant timestamp;
        private final EventType type;
        private final String action;
        private final String userId;
        private final String tenantId;
        private final Status status;
        private final String resourceType;
        private final String resourceId;
        private final Map<String, Object> metadata;
        private final String correlationId;

        private AuditEvent(Builder builder) {
            this.id = builder.id != null ? builder.id : UUID.randomUUID();
            this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
            this.type = builder.type;
            this.action = builder.action;
            this.userId = builder.userId;
            this.tenantId = builder.tenantId;
            this.status = builder.status;
            this.resourceType = builder.resourceType;
            this.resourceId = builder.resourceId;
            this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
            this.correlationId = builder.correlationId;
        }

        public UUID id() { return id; }
        public Instant timestamp() { return timestamp; }
        public EventType type() { return type; }
        public String action() { return action; }
        public String userId() { return userId; }
        public String tenantId() { return tenantId; }
        public Status status() { return status; }
        public String resourceType() { return resourceType; }
        public String resourceId() { return resourceId; }
        public Map<String, Object> metadata() { return metadata; }
        public String correlationId() { return correlationId; }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private UUID id;
            private Instant timestamp;
            private EventType type;
            private String action;
            private String userId;
            private String tenantId;
            private Status status;
            private String resourceType;
            private String resourceId;
            private Map<String, Object> metadata;
            private String correlationId;

            public Builder id(UUID id) { this.id = id; return this; }
            public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
            public Builder type(EventType type) { this.type = type; return this; }
            public Builder action(String action) { this.action = action; return this; }
            public Builder userId(String userId) { this.userId = userId; return this; }
            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder status(Status status) { this.status = status; return this; }
            public Builder resourceType(String resourceType) { this.resourceType = resourceType; return this; }
            public Builder resourceId(String resourceId) { this.resourceId = resourceId; return this; }
            public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
            public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }

            public AuditEvent build() {
                return new AuditEvent(this);
            }
        }
    }
}
