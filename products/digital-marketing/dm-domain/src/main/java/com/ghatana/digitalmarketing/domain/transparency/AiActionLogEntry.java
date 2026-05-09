package com.ghatana.digitalmarketing.domain.transparency;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable transparency log entry for AI/system actions.
 *
 * <p>The {@code version} field enables optimistic locking for concurrent updates.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS transparency timeline entry
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AiActionLogEntry(
        String actionId,
        String workspaceId,
        String correlationId,
        AiActionType actionType,
        AiActionStatus status,
        String actor,
        boolean initiatedByAi,
    String provider,
    String modelVersion,
    boolean humanEdited,
        Double confidence,
        List<String> evidenceLinks,
        List<String> policyChecks,
        String summary,
        String details,
        String relatedEntityId,
        Instant occurredAt,
        long version) {

    public AiActionLogEntry(
            String actionId,
            String workspaceId,
            String correlationId,
            AiActionType actionType,
            AiActionStatus status,
            String actor,
            boolean initiatedByAi,
            Double confidence,
            List<String> evidenceLinks,
            List<String> policyChecks,
            String summary,
            String details,
            String relatedEntityId,
            Instant occurredAt,
            long version) {
        this(
            actionId,
            workspaceId,
            correlationId,
            actionType,
            status,
            actor,
            initiatedByAi,
            null,
            null,
            false,
            confidence,
            evidenceLinks,
            policyChecks,
            summary,
            details,
            relatedEntityId,
            occurredAt,
            version
        );
    }

    public AiActionLogEntry {
        Objects.requireNonNull(actionId, "actionId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(actionType, "actionType must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        Objects.requireNonNull(details, "details must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");

        if (actionId.isBlank()) throw new IllegalArgumentException("actionId must not be blank");
        if (workspaceId.isBlank()) throw new IllegalArgumentException("workspaceId must not be blank");
        if (correlationId.isBlank()) throw new IllegalArgumentException("correlationId must not be blank");
        if (actor.isBlank()) throw new IllegalArgumentException("actor must not be blank");
        if (summary.isBlank()) throw new IllegalArgumentException("summary must not be blank");
        if (details.isBlank()) throw new IllegalArgumentException("details must not be blank");
        if (provider != null && provider.isBlank()) throw new IllegalArgumentException("provider must not be blank when provided");
        if (modelVersion != null && modelVersion.isBlank()) throw new IllegalArgumentException("modelVersion must not be blank when provided");

        if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
            throw new IllegalArgumentException("confidence must be between 0 and 1 when provided");
        }

        if (version < 0) throw new IllegalArgumentException("version must be >= 0, got: " + version);

        evidenceLinks = evidenceLinks == null ? List.of() : List.copyOf(evidenceLinks);
        policyChecks = policyChecks == null ? List.of() : List.copyOf(policyChecks);
    }

    /**
     * Returns a redacted copy for principals that cannot view sensitive details.
     */
    public AiActionLogEntry redacted() {
        return new AiActionLogEntry(
            actionId,
            workspaceId,
            correlationId,
            actionType,
            status,
            actor,
            initiatedByAi,
            provider,
            modelVersion,
            humanEdited,
            confidence,
            List.of(),
            policyChecks,
            summary,
            "REDACTED",
            relatedEntityId,
            occurredAt,
            version
        );
    }
}
