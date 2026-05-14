/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.mastery.MasteryEvidenceBundle;
import com.ghatana.agent.mastery.MasteryEvidenceBundleType;
import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Governance policy for promotion operations with approval and verification requirements.
 *
 * <p>Extends PromotionPolicy with governance-specific controls:
 * <ul>
 *   <li>Role-based promotion permissions (who can promote what)</li>
 *   <li>Evidence bundle requirements for promotion</li>
 *   <li>Tenant-specific policy overrides</li>
 *   <li>Audit trail requirements</li>
 *   <li>Approval workflow integration</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Governance policy for promotion operations
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public interface PromotionGovernancePolicy extends PromotionPolicy {

    /**
     * Checks if a user with the given role can promote to the target state.
     *
     * @param role user role (e.g., "admin", "governance", "developer")
     * @param targetState target mastery state
     * @param tenantId tenant identifier
     * @return true if the role has permission for this promotion
     */
    boolean hasPromotionPermission(@NotNull String role, @NotNull MasteryState targetState, @NotNull String tenantId);

    /**
     * Determines if human approval is required for this promotion.
     *
     * @param delta learning delta to promote
     * @param result evaluation result
     * @param targetState target mastery state
     * @return true if human approval is required
     */
    boolean requiresHumanApproval(
            @NotNull LearningDelta delta,
            @NotNull EvaluationResult result,
            @NotNull MasteryState targetState
    );

    /**
     * Determines if verification is required for this promotion.
     *
     * @param delta learning delta to promote
     * @param result evaluation result
     * @param targetState target mastery state
     * @return true if verification is required
     */
    boolean requiresVerification(
            @NotNull LearningDelta delta,
            @NotNull EvaluationResult result,
            @NotNull MasteryState targetState
    );

    /**
     * Checks if an evidence bundle meets the requirements for promotion.
     *
     * @param bundle evidence bundle
     * @param targetState target mastery state
     * @return true if the bundle meets promotion requirements
     */
    boolean meetsEvidenceRequirements(
            @NotNull MasteryEvidenceBundle bundle,
            @NotNull MasteryState targetState
    );

    /**
     * Returns the minimum aggregate weight required for promotion.
     *
     * @param targetState target mastery state
     * @param bundleType evidence bundle type
     * @return minimum required aggregate weight (0.0 to 1.0)
     */
    double minimumAggregateWeight(@NotNull MasteryState targetState, @NotNull MasteryEvidenceBundleType bundleType);

    /**
     * Returns the required evidence bundle types for promotion.
     *
     * @param targetState target mastery state
     * @return set of required bundle types
     */
    @NotNull
    Set<MasteryEvidenceBundleType> requiredBundleTypes(@NotNull MasteryState targetState);

    /**
     * Checks if tenant-specific policy overrides apply.
     *
     * @param tenantId tenant identifier
     * @return true if tenant has custom policy overrides
     */
    boolean hasTenantOverrides(@NotNull String tenantId);

    /**
     * Returns the maximum number of promotion attempts allowed for a delta.
     *
     * @param tenantId tenant identifier
     * @return maximum promotion attempts, or -1 for unlimited
     */
    int maxPromotionAttempts(@NotNull String tenantId);

    /**
     * Determines if audit trail logging is required for this promotion.
     *
     * @param delta learning delta to promote
     * @param targetState target mastery state
     * @param tenantId tenant identifier
     * @return true if audit trail is required
     */
    boolean requiresAuditTrail(
            @NotNull LearningDelta delta,
            @NotNull MasteryState targetState,
            @NotNull String tenantId
    );

    /**
     * Checks if promotion is allowed during the current time window.
     *
     * <p>Some tenants may restrict promotions to business hours or specific maintenance windows.
     *
     * @param tenantId tenant identifier
     * @return true if promotion is allowed at current time
     */
    boolean isPromotionAllowedAtCurrentTime(@NotNull String tenantId);
}
