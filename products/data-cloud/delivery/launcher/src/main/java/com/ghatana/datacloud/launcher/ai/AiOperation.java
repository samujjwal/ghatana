/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.ai;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Domain model for an AI-assisted operation within Data Cloud.
 *
 * <p>Captures the full lifecycle of an AI-generated suggestion:
 * <ul>
 *   <li>{@link Suggestion} — the raw AI output and its confidence band</li>
 *   <li>{@link Action} — the concrete operation derived from the suggestion</li>
 *   <li>{@link Provenance} — model, provider, latency, token usage, timestamp</li>
 *   <li>{@link ReviewPolicy} — auto-apply threshold, required approvers, manual-review flag</li>
 *   <li>{@link Lifecycle} — pending / applied / rolled-back with actor and timestamps</li>
 *   <li>{@link AuditEvent} — link to the governance audit trail</li>
 * </ul>
 *
 * <p>Used by {@link com.ghatana.datacloud.launcher.http.handlers.AiAssistHandler}
 * and downstream consumers (Data, Pipelines, Trust, Alerts, Operations) to
 * treat AI suggestions as first-class workflow steps with review gates and
 * reversible application.
 *
 * @doc.type record
 * @doc.purpose AI operations substrate for suggestion-review-apply-rollback lifecycle
 * @doc.layer product
 * @doc.pattern Domain Model, Command
 */
public record AiOperation(
    String operationId,
    String tenantId,
    String targetSurface,
    Suggestion suggestion,
    Action action,
    Provenance provenance,
    ReviewPolicy reviewPolicy,
    Lifecycle lifecycle,
    List<InputFeature> inputFeatures,
    AuditEventLink auditEvent
) {

    /**
     * Confidence band with numeric score and human-readable label.
     */
    public record ConfidenceBand(
        double score,
        String label,
        List<String> factors
    ) {
        public ConfidenceBand {
            if (score < 0.0) score = 0.0;
            if (score > 1.0) score = 1.0;
            factors = factors != null ? List.copyOf(factors) : List.of();
        }

        public static ConfidenceBand of(double score, List<String> factors) {
            String label;
            if (score >= 0.85) {
                label = "HIGH";
            } else if (score >= 0.60) {
                label = "MEDIUM";
            } else {
                label = "LOW";
            }
            return new ConfidenceBand(score, label, factors);
        }
    }

    /**
     * The AI-generated suggestion payload.
     */
    public record Suggestion(
        String type,
        String content,
        ConfidenceBand confidenceBand,
        boolean fallback,
        List<String> alternativeActions
    ) {
        public Suggestion {
            alternativeActions = alternativeActions != null ? List.copyOf(alternativeActions) : List.of();
        }
    }

    /**
     * The concrete action derived from a suggestion.
     */
    public record Action(
        String type,
        String payload,
        Map<String, Object> parameters,
        boolean reversible
    ) {
        public Action {
            parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
        }
    }

    /**
     * Model and execution provenance for auditability.
     */
    public record Provenance(
        String provider,
        String model,
        String modelVersion,
        long latencyMs,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        String finishReason,
        Instant timestamp
    ) {}

    /**
     * Review policy governing whether a suggestion may be auto-applied.
     */
    public record ReviewPolicy(
        boolean requiresManualReview,
        double autoApplyThreshold,
        List<String> requiredApprovers,
        int minApprovals,
        List<String> exemptSurfaces
    ) {
        public ReviewPolicy {
            requiredApprovers = requiredApprovers != null ? List.copyOf(requiredApprovers) : List.of();
            exemptSurfaces = exemptSurfaces != null ? List.copyOf(exemptSurfaces) : List.of();
            if (autoApplyThreshold < 0.0) autoApplyThreshold = 0.0;
            if (autoApplyThreshold > 1.0) autoApplyThreshold = 1.0;
        }

        public boolean canAutoApply(double confidenceScore, String surface) {
            if (requiresManualReview) return false;
            if (exemptSurfaces.contains(surface)) return true;
            return confidenceScore >= autoApplyThreshold;
        }
    }

    /**
     * Lifecycle state of an AI operation.
     */
    public record Lifecycle(
        String status,
        String appliedBy,
        Instant appliedAt,
        String rolledBackBy,
        Instant rolledBackAt,
        String rollbackReason
    ) {
        public static final String STATUS_PENDING = "PENDING";
        public static final String STATUS_APPLIED = "APPLIED";
        public static final String STATUS_ROLLED_BACK = "ROLLED_BACK";
        public static final String STATUS_REJECTED = "REJECTED";
    }

    /**
     * An input feature fed into the model for this operation.
     */
    public record InputFeature(
        String name,
        String type,
        Object value,
        double weight
    ) {}

    /**
     * Link to the governance audit trail entry.
     */
    public record AuditEventLink(
        String eventId,
        String eventType,
        Instant recordedAt
    ) {}

    /**
     * Builder for constructing {@link AiOperation} instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String operationId = java.util.UUID.randomUUID().toString();
        private String tenantId;
        private String targetSurface;
        private Suggestion suggestion;
        private Action action;
        private Provenance provenance;
        private ReviewPolicy reviewPolicy;
        private Lifecycle lifecycle = new Lifecycle(Lifecycle.STATUS_PENDING, null, null, null, null, null);
        private List<InputFeature> inputFeatures = List.of();
        private AuditEventLink auditEvent;

        public Builder operationId(String operationId) {
            this.operationId = operationId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder targetSurface(String targetSurface) {
            this.targetSurface = targetSurface;
            return this;
        }

        public Builder suggestion(Suggestion suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        public Builder action(Action action) {
            this.action = action;
            return this;
        }

        public Builder provenance(Provenance provenance) {
            this.provenance = provenance;
            return this;
        }

        public Builder reviewPolicy(ReviewPolicy reviewPolicy) {
            this.reviewPolicy = reviewPolicy;
            return this;
        }

        public Builder lifecycle(Lifecycle lifecycle) {
            this.lifecycle = lifecycle;
            return this;
        }

        public Builder inputFeatures(List<InputFeature> inputFeatures) {
            this.inputFeatures = inputFeatures != null ? List.copyOf(inputFeatures) : List.of();
            return this;
        }

        public Builder auditEvent(AuditEventLink auditEvent) {
            this.auditEvent = auditEvent;
            return this;
        }

        public AiOperation build() {
            return new AiOperation(
                operationId, tenantId, targetSurface,
                suggestion, action, provenance,
                reviewPolicy, lifecycle, inputFeatures, auditEvent
            );
        }
    }
}
