/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import java.util.Set;

/**
 * @doc.type record
 * @doc.purpose Declares what an agent may learn and which targets it may propose changes for
 * @doc.layer agent-core
 * @doc.pattern Record
 */
/**
 * Declares what an agent may learn and which targets it may propose changes for.
 */
public record LearningContract(
        LearningLevel level,
        Set<LearningTarget> allowedTargets,
        boolean provenanceRequired,
        boolean promotionRequired
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
    }

    /**
     * Returns true if the given target is permitted by this contract.
     * 
     * <p>Both the learning level and the allowed targets set must permit the target.
     * Additionally, MASTERY_STATE is never permitted for normal agents as a hard
     * governance boundary.
     * 
     * @param target the learning target to check
     * @return true if permitted, false otherwise
     */
    public boolean permits(LearningTarget target) {
        // Hard governance boundary: MASTERY_STATE is never permitted for normal agents
        if (target == LearningTarget.MASTERY_STATE) {
            return false;
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
