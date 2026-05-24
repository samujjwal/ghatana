package com.ghatana.phr.kernel.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * PHR audit event for tracking healthcare data access and modifications.
 *
 * <p>This event is published for all PHI (Protected Health Information) access,
 * consent changes, emergency access, and data modifications. It enables compliance
 * with Nepal healthcare regulations (Directive 2081, Privacy Act 2075) and provides
 * the audit trail required for regulated healthcare systems.</p>
 *
 * <p>This class maps to the canonical contract {@link com.ghatana.contracts.events.PhrEventContracts.PhrAuditTrailEvent}
 * in the platform contracts layer. The canonical contract is the single source of truth
 * for the wire format.</p>
 *
 * @doc.type class
 * @doc.purpose PHR audit event contract
 * @doc.layer product
 * @doc.pattern Domain Event
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public final class PhrAuditEvent {

    private final String eventId;
    private final String productId;
    private final String auditType;
    private final String action;
    private final String resourceType;
    private final String resourceId;
    private final String actorId;
    private final String actorRole;
    private final String tenantId;
    private final String patientId;
    private final Map<String, Object> metadata;
    private final Instant timestamp;
    private final String correlationId;

    private PhrAuditEvent(Builder builder) {
        this.eventId = Objects.requireNonNull(builder.eventId, "eventId must not be null");
        this.productId = Objects.requireNonNull(builder.productId, "productId must not be null");
        this.auditType = Objects.requireNonNull(builder.auditType, "auditType must not be null");
        this.action = Objects.requireNonNull(builder.action, "action must not be null");
        this.resourceType = Objects.requireNonNull(builder.resourceType, "resourceType must not be null");
        this.resourceId = Objects.requireNonNull(builder.resourceId, "resourceId must not be null");
        this.actorId = Objects.requireNonNull(builder.actorId, "actorId must not be null");
        this.actorRole = Objects.requireNonNull(builder.actorRole, "actorRole must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.patientId = builder.patientId; // Optional for system-level actions
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
        this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp must not be null");
        this.correlationId = Objects.requireNonNull(builder.correlationId, "correlationId must not be null");
    }

    public String eventId() {
        return eventId;
    }

    public String productId() {
        return productId;
    }

    public String auditType() {
        return auditType;
    }

    public String action() {
        return action;
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }

    public String actorId() {
        return actorId;
    }

    public String actorRole() {
        return actorRole;
    }

    public String tenantId() {
        return tenantId;
    }

    public String patientId() {
        return patientId;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String correlationId() {
        return correlationId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String eventId;
        private String productId = "phr";
        private String auditType;
        private String action;
        private String resourceType;
        private String resourceId;
        private String actorId;
        private String actorRole;
        private String tenantId;
        private String patientId;
        private Map<String, Object> metadata;
        private Instant timestamp;
        private String correlationId;

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder auditType(String auditType) {
            this.auditType = auditType;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder actorId(String actorId) {
            this.actorId = actorId;
            return this;
        }

        public Builder actorRole(String actorRole) {
            this.actorRole = actorRole;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder patientId(String patientId) {
            this.patientId = patientId;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public PhrAuditEvent build() {
            if (timestamp == null) {
                timestamp = Instant.now();
            }
            if (eventId == null) {
                eventId = "phr-audit-" + auditType + "-" + System.currentTimeMillis();
            }
            if (correlationId == null) {
                correlationId = "corr-" + eventId;
            }
            return new PhrAuditEvent(this);
        }
    }
}
