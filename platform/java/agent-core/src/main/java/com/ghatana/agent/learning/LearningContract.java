/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import java.util.Set;

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
    }

    public boolean permits(LearningTarget target) {
        return allowedTargets.contains(target) && level.allows(target);
    }
}
