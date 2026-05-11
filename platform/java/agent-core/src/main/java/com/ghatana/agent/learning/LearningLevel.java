/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

/**
 * Declared learning authority for an agent release.
 */
public enum LearningLevel {
    L0,
    L1,
    L2,
    L3,
    L4,
    L5;

    public boolean allows(LearningTarget target) {
        return switch (this) {
            case L0 -> false;
            case L1 -> target == LearningTarget.EPISODIC_MEMORY;
            case L2 -> target == LearningTarget.EPISODIC_MEMORY
                    || target == LearningTarget.SEMANTIC_FACT
                    || target == LearningTarget.RETRIEVAL_POLICY
                    || target == LearningTarget.CONFIDENCE_THRESHOLD
                    || target == LearningTarget.ROUTING_POLICY;
            case L3 -> L2.allows(target)
                    || target == LearningTarget.PROCEDURAL_SKILL
                    || target == LearningTarget.NEGATIVE_KNOWLEDGE;
            case L4 -> L3.allows(target)
                    || target == LearningTarget.PROMPT_TEMPLATE
                    || target == LearningTarget.PLANNER_POLICY;
            case L5 -> true;
        };
    }

    public boolean isOfflineOnly() {
        return this == L5;
    }
}
