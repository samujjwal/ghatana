/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @doc.type record
 * @doc.purpose Declares what an agent may learn and which targets it may propose changes for
 * @doc.layer agent-core
 * @doc.pattern Record
 */
/**
 * Declares what an agent may learn and which targets it may propose changes for.
 *
 * <p>This record enforces hard governance boundaries:
 * <ul>
 *   <li>Normal agents ({@code governanceWorkflow=false}) cannot propose {@code MASTERY_STATE} targets</li>
 *   <li>Only governance workflows ({@code governanceWorkflow=true}) at L5 can propose {@code MASTERY_STATE}</li>
 *   <li>L2+ requires provenance, L3+ requires promotion</li>
 * </ul>
 *
 * <p><strong>Instantiation:</strong> Use the named factory methods to make intent explicit:
 * <ul>
 *   <li>{@link #forNormalAgent} — creates a contract for a regular online/offline agent;
 *       {@code MASTERY_STATE} is unconditionally blocked.</li>
 *   <li>{@link #forGovernanceWorkflow} — creates a contract for an approved L5
 *       governance workflow (e.g. PromotionEngine, ObsolescenceDetector) that may propose
 *       mastery-state transitions. Every instantiation of this contract is an auditable
 *       authority boundary decision.</li>
 * </ul>
 *
 * <p><strong>Runtime permission is NOT derived solely from this contract.</strong>
 * The {@code PromotionEngine} is the canonical runtime authority for mastery-state
 * mutations. A governance contract opens the declaration gate; the PromotionEngine
 * enforces the evaluation and promotion gate.
 */
public record LearningContract(
        LearningLevel level,
        Set<LearningTarget> allowedTargets,
        boolean provenanceRequired,
        boolean promotionRequired,
        boolean governanceWorkflow
) {
    public LearningContract {
        level = level == null ? LearningLevel.L0 : level;
        allowedTargets = allowedTargets == null ? Set.of() : Set.copyOf(allowedTargets);

        // Constructor invariant: force provenance/promotion based on level
        if (level.requiresProvenance() && !provenanceRequired) {
            throw new IllegalArgumentException(
                    "LearningLevel " + level + " requires provenanceRequired=true");
        }
        if (level.requiresPromotion() && !promotionRequired) {
            throw new IllegalArgumentException(
                    "LearningLevel " + level + " requires promotionRequired=true");
        }

        // Governance workflows must be L5 to propose MASTERY_STATE
        if (governanceWorkflow && level != LearningLevel.L5) {
            throw new IllegalArgumentException(
                    "Governance workflows require LearningLevel L5");
        }
    }

    // -------------------------------------------------------------------------
    // Named factory methods — explicit governance boundary
    // -------------------------------------------------------------------------

    /**
     * Creates a learning contract for a <strong>normal agent</strong>.
     *
     * <p>Normal agents are unconditionally blocked from proposing {@code MASTERY_STATE}
     * mutations regardless of their declared level. If a normal L5 agent is required,
     * it will be offline-only and cannot serve responses, but still cannot touch mastery
     * state without being reclassified as a governance workflow.
     *
     * @param level              the declared learning level (L0–L5)
     * @param allowedTargets     the set of targets this agent may propose learning updates for
     * @param provenanceRequired true if learned artifacts must carry provenance records
     * @param promotionRequired  true if learned artifacts require promotion evaluation
     * @return a normal-agent learning contract
     */
    @NotNull
    public static LearningContract forNormalAgent(
            @NotNull LearningLevel level,
            @NotNull Set<LearningTarget> allowedTargets,
            boolean provenanceRequired,
            boolean promotionRequired) {
        return new LearningContract(level, allowedTargets, provenanceRequired, promotionRequired, false);
    }

    /**
     * Creates a learning contract for an <strong>approved governance workflow</strong>.
     *
     * <p>Every call to this factory is an auditable decision: only the
     * {@code PromotionEngine}, {@code ObsolescenceDetector}, or a platform-registered
     * governance workflow should create a governance contract. Arbitrary product code
     * must not call this method.
     *
     * <p>Governance workflows are always L5 and always offline-only. They may propose
     * {@code MASTERY_STATE} transitions, subject to further evaluation by the
     * {@code PromotionEngine}.
     *
     * @param allowedTargets the set of targets this governance workflow may propose
     * @return a governance learning contract at L5
     */
    @NotNull
    public static LearningContract forGovernanceWorkflow(@NotNull Set<LearningTarget> allowedTargets) {
        return new LearningContract(
                LearningLevel.L5,
                allowedTargets,
                /* provenanceRequired= */ true,
                /* promotionRequired= */ true,
                /* governanceWorkflow= */ true);
    }

    // -------------------------------------------------------------------------
    // Permission checks
    // -------------------------------------------------------------------------

    /**
     * Returns true if the given target is permitted by this contract.
     *
     * <p>Both the learning level and the allowed targets set must permit the target.
     * {@code MASTERY_STATE} is only permitted for governance workflows at L5 as a hard
     * governance boundary.
     *
     * @param target the learning target to check
     * @return true if permitted, false otherwise
     */
    public boolean permits(LearningTarget target) {
        // Hard governance boundary: MASTERY_STATE only for governance workflows at L5
        if (target == LearningTarget.MASTERY_STATE) {
            return governanceWorkflow && level == LearningLevel.L5;
        }

        return allowedTargets.contains(target) && level.allows(target);
    }

    /**
     * Throws IllegalStateException if the target is not permitted by this contract.
     *
     * @param target the learning target to check
     * @throws IllegalStateException if the target is not permitted
     */
    public void requirePermitted(LearningTarget target) {
        if (!permits(target)) {
            throw new IllegalStateException(
                    "LearningTarget " + target + " is not permitted by this contract");
        }
    }

    /**
     * Returns true if any of the given targets are permitted by this contract.
     *
     * @param targets the set of learning targets to check
     * @return true if at least one target is permitted
     */
    public boolean permitsAny(Set<LearningTarget> targets) {
        return targets.stream().anyMatch(this::permits);
    }

    /**
     * Returns true if the given target requires promotion under this contract.
     *
     * @param target the learning target to check
     * @return true if promotion is required for this target
     */
    public boolean requiresPromotionFor(LearningTarget target) {
        return promotionRequired && permits(target);
    }

    /**
     * Returns true if the given target requires provenance under this contract.
     *
     * @param target the learning target to check
     * @return true if provenance is required for this target
     */
    public boolean requiresProvenanceFor(LearningTarget target) {
        return provenanceRequired && permits(target);
    }
}
