/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.approval;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Consolidated decision for execution gate checks (approval and verification).
 *
 * <p>This record combines approval and verification decisions into a single type
 * to simplify execution gate logic in GovernedAgentDispatcher. It encapsulates
 * whether execution is allowed, what gates passed/failed, and the associated proofs.
 *
 * @doc.type record
 * @doc.purpose Consolidated execution gate decision
 * @doc.layer agent-core
 * @doc.pattern ValueObject
 */
public record ExecutionGateDecision(
        @NotNull String decisionId,
        @NotNull String tenantId,
        @NotNull String agentId,
        @Nullable String releaseId,
        @Nullable String skillId,
        @Nullable String taskId,
        @NotNull ExecutionGateResult result,
        @Nullable ApprovalProof approvalProof,
        @Nullable VerificationProof verificationProof,
        @NotNull Instant decidedAt,
        @NotNull Map<String, String> metadata
) {
    public ExecutionGateDecision {
        Objects.requireNonNull(decisionId, "decisionId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        metadata = Map.copyOf(metadata);
    }

    /**
     * Returns true if execution is allowed based on this decision.
     *
     * @return true if execution is allowed
     */
    public boolean isAllowed() {
        return result == ExecutionGateResult.ALLOWED;
    }

    /**
     * Returns true if approval is required for this decision.
     *
     * @return true if approval required
     */
    public boolean requiresApproval() {
        return result == ExecutionGateResult.REQUIRES_APPROVAL;
    }

    /**
     * Returns true if verification is required for this decision.
     *
     * @return true if verification required
     */
    public boolean requiresVerification() {
        return result == ExecutionGateResult.REQUIRES_VERIFICATION;
    }

    /**
     * Returns true if both approval and verification are required.
     *
     * @return true if both required
     */
    public boolean requiresBoth() {
        return result == ExecutionGateResult.REQUIRES_BOTH;
    }

    /**
     * Creates a decision that allows execution.
     *
     * @param tenantId tenant ID
     * @param agentId agent ID
     * @return allowed decision
     */
    @NotNull
    public static ExecutionGateDecision allowed(
            @NotNull String tenantId,
            @NotNull String agentId) {
        return new ExecutionGateDecision(
                java.util.UUID.randomUUID().toString(),
                tenantId,
                agentId,
                null,
                null,
                null,
                ExecutionGateResult.ALLOWED,
                null,
                null,
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Creates a decision that requires approval.
     *
     * @param tenantId tenant ID
     * @param agentId agent ID
     * @param skillId skill ID
     * @param approvalProof approval proof (may be null if not yet provided)
     * @return decision requiring approval
     */
    @NotNull
    public static ExecutionGateDecision requiresApproval(
            @NotNull String tenantId,
            @NotNull String agentId,
            @Nullable String skillId,
            @Nullable ApprovalProof approvalProof) {
        return new ExecutionGateDecision(
                java.util.UUID.randomUUID().toString(),
                tenantId,
                agentId,
                null,
                skillId,
                null,
                ExecutionGateResult.REQUIRES_APPROVAL,
                approvalProof,
                null,
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Creates a decision that requires verification.
     *
     * @param tenantId tenant ID
     * @param agentId agent ID
     * @param skillId skill ID
     * @param verificationProof verification proof (may be null if not yet provided)
     * @return decision requiring verification
     */
    @NotNull
    public static ExecutionGateDecision requiresVerification(
            @NotNull String tenantId,
            @NotNull String agentId,
            @Nullable String skillId,
            @Nullable VerificationProof verificationProof) {
        return new ExecutionGateDecision(
                java.util.UUID.randomUUID().toString(),
                tenantId,
                agentId,
                null,
                skillId,
                null,
                ExecutionGateResult.REQUIRES_VERIFICATION,
                null,
                verificationProof,
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Creates a decision that denies execution.
     *
     * @param tenantId tenant ID
     * @param agentId agent ID
     * @param reason denial reason
     * @return denied decision
     */
    @NotNull
    public static ExecutionGateDecision denied(
            @NotNull String tenantId,
            @NotNull String agentId,
            @NotNull String reason) {
        return new ExecutionGateDecision(
                java.util.UUID.randomUUID().toString(),
                tenantId,
                agentId,
                null,
                null,
                null,
                ExecutionGateResult.DENIED,
                null,
                null,
                Instant.now(),
                Map.of("reason", reason)
        );
    }

    /**
     * Execution gate result enumeration.
     */
    public enum ExecutionGateResult {
        ALLOWED,
        REQUIRES_APPROVAL,
        REQUIRES_VERIFICATION,
        REQUIRES_BOTH,
        DENIED
    }
}
