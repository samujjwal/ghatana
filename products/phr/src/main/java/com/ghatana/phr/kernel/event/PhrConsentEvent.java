package com.ghatana.phr.kernel.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * PHR consent event for tracking consent grants, revocations, and modifications.
 *
 * <p>This event is published when patient consent is granted, revoked, or modified.
 * It enables consent invalidation across distributed cache nodes and provides the
 * evidence trail required for healthcare compliance. Consent events trigger cache
 * invalidation to ensure stale consent data is not used.</p>
 *
 * <p>This class maps to the canonical contract {@link com.ghatana.contracts.events.PhrEventContracts.PhrConsentChangeEvent}
 * in the platform contracts layer. The canonical contract is the single source of truth
 * for the wire format.</p>
 *
 * @doc.type class
 * @doc.purpose PHR consent event contract
 * @doc.layer product
 * @doc.pattern Domain Event
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public final class PhrConsentEvent {

    private final String eventId;
    private final String productId;
    private final String consentType;
    private final String action; // granted, revoked, modified, expired
    private final String patientId;
    private final String recipientId;
    private final String resourceType;
    private final String purpose;
    private final Instant expiresAt;
    private final String tenantId;
    private final Map<String, Object> metadata;
    private final Instant timestamp;
    private final String correlationId;

    private PhrConsentEvent(Builder builder) {
        this.eventId = Objects.requireNonNull(builder.eventId, "eventId must not be null");
        this.productId = Objects.requireNonNull(builder.productId, "productId must not be null");
        this.consentType = Objects.requireNonNull(builder.consentType, "consentType must not be null");
        this.action = Objects.requireNonNull(builder.action, "action must not be null");
        this.patientId = Objects.requireNonNull(builder.patientId, "patientId must not be null");
        this.recipientId = Objects.requireNonNull(builder.recipientId, "recipientId must not be null");
        this.resourceType = Objects.requireNonNull(builder.resourceType, "resourceType must not be null");
        this.purpose = Objects.requireNonNull(builder.purpose, "purpose must not be null");
        this.expiresAt = builder.expiresAt; // Optional for indefinite consent
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
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

    public String consentType() {
        return consentType;
    }

    public String action() {
        return action;
    }

    public String patientId() {
        return patientId;
    }

    public String recipientId() {
        return recipientId;
    }

    public String resourceType() {
        return resourceType;
    }

    public String purpose() {
        return purpose;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String tenantId() {
        return tenantId;
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
        private String consentType;
        private String action;
        private String patientId;
        private String recipientId;
        private String resourceType;
        private String purpose;
        private Instant expiresAt;
        private String tenantId;
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

        public Builder consentType(String consentType) {
            this.consentType = consentType;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder patientId(String patientId) {
            this.patientId = patientId;
            return this;
        }

        public Builder recipientId(String recipientId) {
            this.recipientId = recipientId;
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder purpose(String purpose) {
            this.purpose = purpose;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
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

        public PhrConsentEvent build() {
            if (timestamp == null) {
                timestamp = Instant.now();
            }
            if (eventId == null) {
                eventId = "phr-consent-" + action + "-" + System.currentTimeMillis();
            }
            if (correlationId == null) {
                correlationId = "corr-" + eventId;
            }
            return new PhrConsentEvent(this);
        }
    }
}
