/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.agent.mastery.MasteryEvidenceBundle;
import com.ghatana.agent.mastery.MasteryEvidenceBundleStatus;
import com.ghatana.agent.mastery.MasteryEvidenceBundleType;
import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of PromotionGovernancePolicy.
 *
 * <p>Governance rules:
 * <ul>
 *   <li>MASTERED promotion requires "governance" or "admin" role</li>
 *   <li>COMPETENT promotion requires "developer" role or higher</li>
 *   <li>PROMOTION bundles require minimum aggregate weight of 0.8 for MASTERED</li>
 *   <li>EVALUATION and SAFETY bundles are required for all promotions</li>
 *   <li>Audit trail is required for all MASTERED promotions</li>
 *   <li>Max 3 promotion attempts per delta (configurable per tenant)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default implementation of PromotionGovernancePolicy
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public final class DefaultPromotionGovernancePolicy implements PromotionGovernancePolicy {

    private final DefaultPromotionPolicy basePolicy;
    private final Map<String, TenantOverride> tenantOverrides;

    public DefaultPromotionGovernancePolicy() {
        this.basePolicy = new DefaultPromotionPolicy();
        this.tenantOverrides = new ConcurrentHashMap<>();
    }

    @Override
    public boolean canPromote(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        return basePolicy.canPromote(delta, result);
    }

    @Override
    @NotNull
    public MasteryState targetState(@NotNull LearningDelta delta, @NotNull EvaluationResult result) {
        return basePolicy.targetState(delta, result);
    }

    @Override
    public boolean canRetireFromMastered(@NotNull MasteryState currentState) {
        return basePolicy.canRetireFromMastered(currentState);
    }

    @Override
    public boolean hasPromotionPermission(@NotNull String role, @NotNull MasteryState targetState, @NotNull String tenantId) {
        TenantOverride override = tenantOverrides.get(tenantId);
        if (override != null && override.rolePermissions() != null) {
            Set<String> allowedRoles = override.rolePermissions().get(targetState);
            if (allowedRoles != null) {
                return allowedRoles.contains(role);
            }
        }

        // Default role permissions
        return switch (targetState) {
            case MASTERED -> role.equals("governance") || role.equals("admin");
            case COMPETENT -> role.equals("developer") || role.equals("governance") || role.equals("admin");
            case PRACTICED, OBSERVED -> true; // Any role can promote to lower states
            default -> false;
        };
    }

    @Override
    public boolean requiresHumanApproval(
            @NotNull LearningDelta delta,
            @NotNull EvaluationResult result,
            @NotNull MasteryState targetState
    ) {
        // Policy targets always require human approval for MASTERED
        if (delta.target() == LearningTarget.PLANNER_POLICY && targetState == MasteryState.MASTERED) {
            return true;
        }

        // PROCEDURAL_SKILL requires approval for MASTERED
        if (delta.target() == LearningTarget.PROCEDURAL_SKILL && targetState == MasteryState.MASTERED) {
            return true;
        }

        // RETRIEVAL_POLICY requires approval for MASTERED
        if (delta.target() == LearningTarget.RETRIEVAL_POLICY && targetState == MasteryState.MASTERED) {
            return true;
        }

        return false;
    }

    @Override
    public boolean requiresVerification(
            @NotNull LearningDelta delta,
            @NotNull EvaluationResult result,
            @NotNull MasteryState targetState
    ) {
        // All MASTERED promotions require verification
        if (targetState == MasteryState.MASTERED) {
            return true;
        }

        // COMPETENT promotions for policy targets require verification
        if (targetState == MasteryState.COMPETENT && isPolicyTarget(delta.target())) {
            return true;
        }

        return false;
    }

    @Override
    public boolean meetsEvidenceRequirements(
            @NotNull MasteryEvidenceBundle bundle,
            @NotNull MasteryState targetState
    ) {
        // Bundle must be approved
        if (bundle.status() != MasteryEvidenceBundleStatus.APPROVED) {
            return false;
        }

        // Check aggregate weight meets minimum
        double minWeight = minimumAggregateWeight(targetState, bundle.type());
        if (bundle.aggregateWeight() < minWeight) {
            return false;
        }

        // Check required bundle types are present (for multi-bundle scenarios)
        Set<MasteryEvidenceBundleType> required = requiredBundleTypes(targetState);
        if (!required.isEmpty() && !required.contains(bundle.type())) {
            return false;
        }

        return true;
    }

    @Override
    public double minimumAggregateWeight(@NotNull MasteryState targetState, @NotNull MasteryEvidenceBundleType bundleType) {
        return switch (targetState) {
            case MASTERED -> 0.8; // High confidence required for MASTERED
            case COMPETENT -> 0.6;
            case PRACTICED -> 0.4;
            case OBSERVED -> 0.2;
            default -> 0.0;
        };
    }

    @Override
    @NotNull
    public Set<MasteryEvidenceBundleType> requiredBundleTypes(@NotNull MasteryState targetState) {
        return switch (targetState) {
            case MASTERED -> EnumSet.of(
                    MasteryEvidenceBundleType.EVALUATION,
                    MasteryEvidenceBundleType.SAFETY,
                    MasteryEvidenceBundleType.REGRESSION
            );
            case COMPETENT -> EnumSet.of(
                    MasteryEvidenceBundleType.EVALUATION,
                    MasteryEvidenceBundleType.SAFETY
            );
            default -> EnumSet.noneOf(MasteryEvidenceBundleType.class);
        };
    }

    @Override
    public boolean hasTenantOverrides(@NotNull String tenantId) {
        return tenantOverrides.containsKey(tenantId);
    }

    @Override
    public int maxPromotionAttempts(@NotNull String tenantId) {
        TenantOverride override = tenantOverrides.get(tenantId);
        if (override != null && override.maxPromotionAttempts() > 0) {
            return override.maxPromotionAttempts();
        }
        return 3; // Default: 3 attempts
    }

    @Override
    public boolean requiresAuditTrail(
            @NotNull LearningDelta delta,
            @NotNull MasteryState targetState,
            @NotNull String tenantId
    ) {
        // All MASTERED promotions require audit trail
        if (targetState == MasteryState.MASTERED) {
            return true;
        }

        // Tenant override
        TenantOverride override = tenantOverrides.get(tenantId);
        if (override != null && override.auditAllPromotions()) {
            return true;
        }

        return false;
    }

    @Override
    public boolean isPromotionAllowedAtCurrentTime(@NotNull String tenantId) {
        TenantOverride override = tenantOverrides.get(tenantId);
        if (override != null && override.restrictToBusinessHours()) {
            return isBusinessHours(override.timeZone());
        }
        return true; // No time restrictions by default
    }

    /**
     * Adds tenant-specific policy overrides.
     *
     * @param tenantId tenant identifier
     * @param override tenant override configuration
     */
    public void setTenantOverride(@NotNull String tenantId, @NotNull TenantOverride override) {
        tenantOverrides.put(tenantId, override);
    }

    /**
     * Removes tenant-specific policy overrides.
     *
     * @param tenantId tenant identifier
     */
    public void removeTenantOverride(@NotNull String tenantId) {
        tenantOverrides.remove(tenantId);
    }

    private boolean isPolicyTarget(@NotNull LearningTarget target) {
        return target == LearningTarget.RETRIEVAL_POLICY
                || target == LearningTarget.CONFIDENCE_THRESHOLD
                || target == LearningTarget.ROUTING_POLICY
                || target == LearningTarget.PROMPT_TEMPLATE
                || target == LearningTarget.PLANNER_POLICY;
    }

    private boolean isBusinessHours(@NotNull String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone);
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.now(), zoneId);
        DayOfWeek day = now.getDayOfWeek();
        int hour = now.getHour();

        // Monday to Friday, 9 AM to 5 PM
        return day != DayOfWeek.SATURDAY
                && day != DayOfWeek.SUNDAY
                && hour >= 9
                && hour < 17;
    }

    /**
     * Tenant-specific policy override configuration.
     */
    public record TenantOverride(
            Map<MasteryState, Set<String>> rolePermissions,
            int maxPromotionAttempts,
            boolean auditAllPromotions,
            boolean restrictToBusinessHours,
            String timeZone
    ) {
        public TenantOverride {
            if (rolePermissions != null) {
                rolePermissions = Map.copyOf(rolePermissions);
            }
            if (timeZone == null || timeZone.isBlank()) {
                timeZone = "UTC";
            }
        }
    }
}
