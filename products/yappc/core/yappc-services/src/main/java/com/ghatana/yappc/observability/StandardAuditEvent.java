/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.observability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Standard audit event shape for consistent audit logging across all YAPPC services.
 * Every critical mutation must emit an audit event with this standardized structure.
 *
 * @doc.type class
 * @doc.purpose Standard audit event shape for consistent audit logging across all services
 * @doc.layer product
 * @doc.pattern DTO
 */
public final class StandardAuditEvent {

    private final String eventId;
    private final String actor;
    private final String target;
    private final String phase;
    private final String operation;
    private final String dataClassification;
    private final String previewTrust;
    private final String correlationId;
    private final String traceId;
    private final Instant timestamp;
    private final Map<String, String> additionalMetadata;
    private final String outcome;
    private final String reason;

    private StandardAuditEvent(Builder builder) {
        this.eventId = builder.eventId;
        this.actor = builder.actor;
        this.target = builder.target;
        this.phase = builder.phase;
        this.operation = builder.operation;
        this.dataClassification = builder.dataClassification;
        this.previewTrust = builder.previewTrust;
        this.correlationId = builder.correlationId;
        this.traceId = builder.traceId;
        this.timestamp = builder.timestamp;
        this.additionalMetadata = Map.copyOf(builder.additionalMetadata);
        this.outcome = builder.outcome;
        this.reason = builder.reason;
    }

    public String eventId() {
        return eventId;
    }

    public String actor() {
        return actor;
    }

    public String target() {
        return target;
    }

    public String phase() {
        return phase;
    }

    public String operation() {
        return operation;
    }

    public String dataClassification() {
        return dataClassification;
    }

    public String previewTrust() {
        return previewTrust;
    }

    public String correlationId() {
        return correlationId;
    }

    public String traceId() {
        return traceId;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public Map<String, String> additionalMetadata() {
        return additionalMetadata;
    }

    public String outcome() {
        return outcome;
    }

    public String reason() {
        return reason;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String eventId = UUID.randomUUID().toString();
        private String actor;
        private String target;
        private String phase;
        private String operation;
        private String dataClassification = "INTERNAL";
        private String previewTrust = "REQUIRES_REVIEW";
        private String correlationId;
        private String traceId;
        private Instant timestamp = Instant.now();
        private final Map<String, String> additionalMetadata = new java.util.HashMap<>();
        private String outcome = "SUCCESS";
        private String reason;

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder phase(String phase) {
            this.phase = phase;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder dataClassification(String dataClassification) {
            this.dataClassification = dataClassification;
            return this;
        }

        public Builder previewTrust(String previewTrust) {
            this.previewTrust = previewTrust;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder addMetadata(String key, String value) {
            this.additionalMetadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.additionalMetadata.clear();
            this.additionalMetadata.putAll(metadata);
            return this;
        }

        public Builder outcome(String outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public StandardAuditEvent build() {
            if (actor == null || actor.isBlank()) {
                throw new IllegalArgumentException("actor is required");
            }
            if (target == null || target.isBlank()) {
                throw new IllegalArgumentException("target is required");
            }
            if (operation == null || operation.isBlank()) {
                throw new IllegalArgumentException("operation is required");
            }
            if (correlationId == null || correlationId.isBlank()) {
                throw new IllegalArgumentException("correlationId is required");
            }
            return new StandardAuditEvent(this);
        }
    }

    /**
     * Standard data classification levels.
     */
    public static class DataClassification {
        public static final String PUBLIC = "PUBLIC";
        public static final String INTERNAL = "INTERNAL";
        public static final String CONFIDENTIAL = "CONFIDENTIAL";
        public static final String RESTRICTED = "RESTRICTED";
    }

    /**
     * Standard preview trust levels.
     */
    public static class PreviewTrust {
        public static final String TRUSTED = "TRUSTED";
        public static final String UNTRUSTED = "UNTRUSTED";
        public static final String REQUIRES_REVIEW = "REQUIRES_REVIEW";
        public static final String BLOCKED = "BLOCKED";
    }

    /**
     * Standard outcome values.
     */
    public static class Outcomes {
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILURE = "FAILURE";
        public static final String REJECTED = "REJECTED";
        public static final String APPROVED = "APPROVED";
        public static final String BLOCKED = "BLOCKED";
        public static final String DEGRADED = "DEGRADED";
    }
}
