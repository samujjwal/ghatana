/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

/**
 * @doc.type enum
 * @doc.purpose Declared learning authority for an agent release
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
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

    /**
     * Returns true if this learning level permits the given target.
     * 
     * <p>IMPORTANT: MASTERY_STATE is never permitted for normal agents.
     * Only PromotionEngine, ObsolescenceDetector, or approved governance workflows
     * should emit mastery transitions. This is a hard governance boundary.
     * 
     * @param target the learning target to check
     * @return true if permitted, false otherwise
     */
    public boolean allows(LearningTarget target) {
        // Hard governance boundary: no agent level can directly learn MASTERY_STATE
        if (target == LearningTarget.MASTERY_STATE) {
            return false;
        }
        
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

    /**
     * Returns true if this learning level requires provenance for learned artifacts.
     * L2 and above require provenance.
     */
    public boolean requiresProvenance() {
        return this.ordinal() >= L2.ordinal();
    }

    /**
     * Returns true if this learning level requires promotion for learned artifacts.
     * L3 and above require promotion.
     */
    public boolean requiresPromotion() {
        return this.ordinal() >= L3.ordinal();
    }

    /**
     * Returns true if this learning level can run in online mode.
     * All levels except L5 can run online.
     */
    public boolean canRunOnline() {
        return this != L5;
    }

    /**
     * Returns true if this learning level can serve responses directly.
     * All levels except L5 can serve responses.
     */
    public boolean canServeResponses() {
        return this != L5;
    }
}
