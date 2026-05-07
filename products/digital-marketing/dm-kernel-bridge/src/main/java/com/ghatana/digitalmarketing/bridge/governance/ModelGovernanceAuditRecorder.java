package com.ghatana.digitalmarketing.bridge.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * P0-011: Audit event recorder for AI model governance actions.
 * 
 * <p>Records audit events for model registration, experiment definition,
 * metrics recording, approval, and promotion. These events provide
 * traceability for compliance and security auditing.
 *
 * @doc.type class
 * @doc.purpose Audit event recorder for AI model governance actions
 * @doc.layer product
 * @doc.pattern Observer, Audit Trail
 */
public final class ModelGovernanceAuditRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(ModelGovernanceAuditRecorder.class);

    public ModelGovernanceAuditRecorder() {
        // In production, this would integrate with platform audit trail service
        // For now, we log structured audit events
    }

    /**
     * Records an audit event when a model is registered.
     */
    public void recordModelRegistered(String tenantId, String modelName, String version) {
        AuditEvent event = AuditEvent.builder()
                .eventType("MODEL_REGISTERED")
                .tenantId(tenantId)
                .resourceType("MODEL")
                .resourceId(modelName + ":" + version)
                .timestamp(Instant.now())
                .build();
        logAuditEvent(event);
    }

    /**
     * Records an audit event when an experiment is defined.
     */
    public void recordExperimentDefined(String tenantId, String experimentId, 
            String baselineModelRef, String variantModelRef, String splitPercent) {
        AuditEvent event = AuditEvent.builder()
                .eventType("EXPERIMENT_DEFINED")
                .tenantId(tenantId)
                .resourceType("EXPERIMENT")
                .resourceId(experimentId)
                .timestamp(Instant.now())
                .addDetail("baselineModelRef", baselineModelRef)
                .addDetail("variantModelRef", variantModelRef)
                .addDetail("splitPercent", splitPercent)
                .build();
        logAuditEvent(event);
    }

    /**
     * Records an audit event when experiment metrics are recorded.
     */
    public void recordMetricsRecorded(String tenantId, String experimentId, String outcome) {
        AuditEvent event = AuditEvent.builder()
                .eventType("METRICS_RECORDED")
                .tenantId(tenantId)
                .resourceType("EXPERIMENT")
                .resourceId(experimentId)
                .timestamp(Instant.now())
                .addDetail("outcome", outcome)
                .build();
        logAuditEvent(event);
    }

    /**
     * Records an audit event when an experiment is approved.
     */
    public void recordApproval(String tenantId, String experimentId, String approverId) {
        AuditEvent event = AuditEvent.builder()
                .eventType("EXPERIMENT_APPROVED")
                .tenantId(tenantId)
                .resourceType("EXPERIMENT")
                .resourceId(experimentId)
                .timestamp(Instant.now())
                .actorId(approverId)
                .addDetail("approvalState", "APPROVED")
                .build();
        logAuditEvent(event);
    }

    /**
     * Records an audit event when a model is promoted to production.
     */
    public void recordPromotion(String tenantId, String modelName, String version, String experimentId) {
        AuditEvent.AuditEventBuilder builder = AuditEvent.builder()
                .eventType("MODEL_PROMOTED")
                .tenantId(tenantId)
                .resourceType("MODEL")
                .resourceId(modelName + ":" + version)
                .timestamp(Instant.now());
        
        if (experimentId != null) {
            builder.addDetail("experimentId", experimentId);
        }
        
        logAuditEvent(builder.build());
    }

    /**
     * Records an audit event when a model is deprecated.
     */
    public void recordModelDeprecated(String tenantId, String modelName, String version) {
        AuditEvent event = AuditEvent.builder()
                .eventType("MODEL_DEPRECATED")
                .tenantId(tenantId)
                .resourceType("MODEL")
                .resourceId(modelName + ":" + version)
                .timestamp(Instant.now())
                .build();
        logAuditEvent(event);
    }

    private void logAuditEvent(AuditEvent event) {
        LOG.info("[DMOS][AI-Gov-Audit] {} tenant={} resource={} actor={} details={}",
                event.eventType(),
                event.tenantId(),
                event.resourceType() + ":" + event.resourceId(),
                event.actorId() != null ? event.actorId() : "SYSTEM",
                event.details());
    }

    /**
     * Audit event record.
     */
    public static final class AuditEvent {
        private final String eventType;
        private final String tenantId;
        private final String resourceType;
        private final String resourceId;
        private final Instant timestamp;
        private final String actorId;
        private final String details;

        private AuditEvent(Builder builder) {
            this.eventType = builder.eventType;
            this.tenantId = builder.tenantId;
            this.resourceType = builder.resourceType;
            this.resourceId = builder.resourceId;
            this.timestamp = builder.timestamp;
            this.actorId = builder.actorId;
            this.details = builder.detailsBuilder.toString();
        }

        public String eventType() { return eventType; }
        public String tenantId() { return tenantId; }
        public String resourceType() { return resourceType; }
        public String resourceId() { return resourceId; }
        public Instant timestamp() { return timestamp; }
        public String actorId() { return actorId; }
        public String details() { return details; }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String eventType;
            private String tenantId;
            private String resourceType;
            private String resourceId;
            private Instant timestamp;
            private String actorId;
            private final StringBuilder detailsBuilder = new StringBuilder();

            public Builder eventType(String eventType) {
                this.eventType = eventType;
                return this;
            }

            public Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
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

            public Builder timestamp(Instant timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public Builder actorId(String actorId) {
                this.actorId = actorId;
                return this;
            }

            public Builder addDetail(String key, String value) {
                if (detailsBuilder.length() > 0) {
                    detailsBuilder.append(", ");
                }
                detailsBuilder.append(key).append("=").append(value);
                return this;
            }

            public AuditEvent build() {
                return new AuditEvent(this);
            }
        }
    }
}
