/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Approval Domain Adapter
 */
package com.ghatana.yappc.services.lifecycle;

import java.time.Instant;
import java.util.List;

/**
 * Adapter that converts the lightweight agent-layer
 * {@link com.ghatana.yappc.agent.ApprovalRequest} into the lifecycle service's
 * full {@link ApprovalRequest} domain record.
 *
 * <h2>Why two models?</h2>
 * <ul>
 *   <li>The agent-layer record is a thin <em>input / command DTO</em> used by
 *       {@link com.ghatana.yappc.agent.HumanInTheLoopCoordinatorAgent} to describe
 *       a gate that needs human sign-off.</li>
 *   <li>The lifecycle-layer record is the <em>full domain aggregate</em> persisted
 *       and managed by {@link HumanApprovalService}; it carries status, timestamps,
 *       and decision fields that only exist after the request is created.</li>
 * </ul>
 *
 * <p>This mapper is the single, tested boundary between the two representations.
 * Any change to either record shape must be reflected here.
 *
 * @doc.type class
 * @doc.purpose Maps agent-layer ApprovalRequest DTO to the lifecycle domain ApprovalRequest record
 * @doc.layer product
 * @doc.pattern Adapter
 * @doc.gaa.lifecycle act
 */
public final class ApprovalRequestMapper {

    private ApprovalRequestMapper() { /* utility — no instances */ }

    /**
     * Converts an agent-layer {@link com.ghatana.yappc.agent.ApprovalRequest} into a
     * lifecycle-layer {@link ApprovalRequest} in {@code PENDING} status.
     *
     * <p>If the agent record's {@code requestId} is blank or {@code null}, the caller
     * is expected to assign a generated ID (UUID) before invoking this method.
     *
     * @param src        agent-layer request; must not be null
     * @param assignedId the persistence ID to assign (typically a UUID); must not be blank
     * @param createdAt  creation timestamp of the new request
     * @return lifecycle-layer request ready for persistence
     * @throws IllegalArgumentException if {@code approvalType} is not a valid enum value
     */
    public static ApprovalRequest toLifecycleRequest(
            com.ghatana.yappc.agent.ApprovalRequest src,
            String assignedId,
            Instant createdAt) {

        ApprovalRequest.ApprovalType type = parseType(src.approvalType());

        List<String> unmetCriteria    = src.unmetCriteria()    != null ? src.unmetCriteria()    : List.of();
        List<String> missingArtifacts = src.missingArtifacts() != null ? src.missingArtifacts() : List.of();

        ApprovalRequest.ApprovalContext context = new ApprovalRequest.ApprovalContext(
                src.fromPhase(),
                src.toPhase(),
                src.blockReason(),
                unmetCriteria,
                missingArtifacts
        );

        return new ApprovalRequest(
                assignedId,
                src.projectId(),
                src.requestingAgentId(),
                type,
                context,
                ApprovalRequest.ApprovalStatus.PENDING,
                src.tenantId(),
                createdAt,
                null,           // decidedAt — not yet decided
                null,           // decidedBy — not yet decided
                src.expiresAt()
        );
    }

    /**
     * Converts a lifecycle-layer {@link ApprovalRequest} back into the minimal
     * agent-layer {@link com.ghatana.yappc.agent.ApprovalRequest} shape.
     *
     * <p>Used when the coordinator agent needs to poll for its own request or
     * reconstruct the input when building a response.
     *
     * @param src lifecycle-layer request; must not be null
     * @return agent-layer request shape (status and decision fields are not preserved)
     */
    public static com.ghatana.yappc.agent.ApprovalRequest toAgentRequest(ApprovalRequest src) {
        ApprovalRequest.ApprovalContext ctx = src.context();
        return new com.ghatana.yappc.agent.ApprovalRequest(
                src.id(),
                src.tenantId(),
                src.projectId(),
                src.requestingAgentId(),
                src.approvalType().name(),
                ctx != null ? ctx.fromPhase()          : null,
                ctx != null ? ctx.toPhase()            : null,
                ctx != null ? ctx.blockReason()        : null,
                ctx != null ? ctx.unmetCriteria()      : List.of(),
                ctx != null ? ctx.missingArtifacts()   : List.of(),
                src.expiresAt()
        );
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private static ApprovalRequest.ApprovalType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("approvalType must not be blank");
        }
        try {
            return ApprovalRequest.ApprovalType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unknown approvalType '" + raw + "'. Valid values: "
                            + java.util.Arrays.toString(ApprovalRequest.ApprovalType.values()), ex);
        }
    }
}
