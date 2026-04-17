package com.ghatana.plugin.approval;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable request to initiate a human approval workflow.
 *
 * <p>Includes all information a reviewer needs to make an informed decision.
 * The caller is responsible for generating a stable, unique {@code requestId}
 * (typically a UUID). Passing the same {@code requestId} twice is idempotent.</p>
 *
 * @param requestId   stable UUID identifying this approval request
 * @param subjectId   identifier of the subject being acted upon (patient ID, trade ID, etc.)
 * @param requestedBy identifier of the principal triggering the approval
 * @param action      the operation requiring approval (e.g. {@code "patient-data-export"})
 * @param purpose     human-readable business justification for the operation
 * @param context     additional structured data the reviewer may need; may be empty but not null
 * @param requestedAt timestamp when the request was initiated
 * @param expiresAt   optional deadline; {@code null} means no expiry
 *
 * @doc.type class
 * @doc.purpose Immutable value object carrying human-approval request data
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ApprovalRequest(
        String requestId,
        String subjectId,
        String requestedBy,
        String action,
        String purpose,
        Map<String, Object> context,
        Instant requestedAt,
        Instant expiresAt
) {
    /**
     * Compact canonical constructor with precondition validation.
     */
    public ApprovalRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(subjectId,  "subjectId must not be null");
        Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        Objects.requireNonNull(action,     "action must not be null");
        Objects.requireNonNull(purpose,    "purpose must not be null");
        Objects.requireNonNull(context,    "context must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");

        if (requestId.isBlank())   throw new IllegalArgumentException("requestId must not be blank");
        if (subjectId.isBlank())   throw new IllegalArgumentException("subjectId must not be blank");
        if (requestedBy.isBlank()) throw new IllegalArgumentException("requestedBy must not be blank");
        if (action.isBlank())      throw new IllegalArgumentException("action must not be blank");
        if (purpose.isBlank())     throw new IllegalArgumentException("purpose must not be blank");

        // Defensive copy — callers cannot mutate the map after construction
        context = Map.copyOf(context);
    }
}
