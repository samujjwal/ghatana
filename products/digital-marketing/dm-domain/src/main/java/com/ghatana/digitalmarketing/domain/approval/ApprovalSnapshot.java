package com.ghatana.digitalmarketing.domain.approval;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of an approval request at the moment approval was decided.
 *
 * <p>Snapshots capture the target reference, validation outcome reference, policy context,
 * and approver identity so the audit trail is self-contained even if the underlying
 * entity evolves after approval.</p>
 *
 * @param requestId          stable approval request ID
 * @param targetType         the type of entity being approved
 * @param targetId           the entity identifier (content version ID, strategy ID, etc.)
 * @param targetWorkspaceId  the workspace owning the target
 * @param snapshotSummary    human-readable summary of what was being approved
 * @param validationResultId reference to the most-recent validation result, may be {@code null}
 * @param riskLevel          numeric risk level 1-5 as documented by the routing rule
 * @param requiredApproverRole the role required to approve (e.g. "brand-manager", "exec-sponsor")
 * @param snapshotAt         timestamp when this snapshot was captured
 *
 * @doc.type class
 * @doc.purpose Immutable approval snapshot for DMOS approval workflow
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ApprovalSnapshot(
        String requestId,
        ApprovalTargetType targetType,
        String targetId,
        String targetWorkspaceId,
        String snapshotSummary,
        String validationResultId,
        int riskLevel,
        String requiredApproverRole,
        Instant snapshotAt
) {
    /** Compact constructor — validates required fields and risk range. */
    public ApprovalSnapshot {
        Objects.requireNonNull(requestId,           "requestId must not be null");
        Objects.requireNonNull(targetType,          "targetType must not be null");
        Objects.requireNonNull(targetId,            "targetId must not be null");
        Objects.requireNonNull(targetWorkspaceId,   "targetWorkspaceId must not be null");
        Objects.requireNonNull(snapshotSummary,     "snapshotSummary must not be null");
        Objects.requireNonNull(requiredApproverRole,"requiredApproverRole must not be null");
        Objects.requireNonNull(snapshotAt,          "snapshotAt must not be null");
        if (requestId.isBlank())           throw new IllegalArgumentException("requestId must not be blank");
        if (targetId.isBlank())            throw new IllegalArgumentException("targetId must not be blank");
        if (targetWorkspaceId.isBlank())   throw new IllegalArgumentException("targetWorkspaceId must not be blank");
        if (snapshotSummary.isBlank())     throw new IllegalArgumentException("snapshotSummary must not be blank");
        if (requiredApproverRole.isBlank())throw new IllegalArgumentException("requiredApproverRole must not be blank");
        if (riskLevel < 1 || riskLevel > 5)throw new IllegalArgumentException("riskLevel must be 1-5, got: " + riskLevel);
    }
}
