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
 *
 * <h2>Canonical Learning Level Semantics</h2>
 * <ul>
 *   <li><b>L0</b>: No learning - agent operates with static configuration only</li>
 *   <li><b>L1</b>: Episodic memory - can capture and replay execution traces</li>
 *   <li><b>L2</b>: Semantic/retrieval/confidence/routing - can learn semantic facts,
 *       retrieval policies, confidence thresholds, and routing policies</li>
 *   <li><b>L3</b>: Procedural/negative knowledge - can learn procedural skills and
 *       negative knowledge (what not to do)</li>
 *   <li><b>L4</b>: Prompt/planner - can learn prompt templates and planner policies</li>
 *   <li><b>L5</b>: Offline governance/model adapter/mastery-state workflows -
 *       can learn model adapters and participate in offline governance workflows
 *       including mastery state transitions. L5 is offline-only and cannot serve
 *       responses directly.</li>
 * </ul>
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
        // L5 is the privileged offline-only governance level: it permits all targets
        // including MASTERY_STATE (used exclusively by PromotionEngine, ObsolescenceDetector,
        // and other approved governance workflows).
        if (this == L5) {
            return true;
        }

        // Hard governance boundary: sub-L5 agents cannot directly learn MASTERY_STATE.
        // Only PromotionEngine, ObsolescenceDetector, or approved governance workflows
        // (running at L5) should emit mastery transitions.
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
            default -> false;
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

    /**
     * Returns true if this learning level requires evaluation references for learning.
     * L3 and above require evaluation refs to validate learned artifacts.
     *
     * @return true if evaluation refs are required
     */
    public boolean requiresEvaluationRefs() {
        return this.ordinal() >= L3.ordinal();
    }

    /**
     * Returns true if the given learning target requires human review by default
     * at this learning level.
     *
     * <p>Certain targets are high-risk and require human review regardless of level:
     * <ul>
     *   <li>PROMPT_TEMPLATE - affects all subsequent reasoning</li>
     *   <li>PLANNER_POLICY - affects task decomposition and strategy</li>
     *   <li>MODEL_ADAPTER - affects which model is used for execution</li>
     *   <li>MASTERY_STATE - affects execution permissions and mode selection</li>
     * </ul>
     *
     * @param target the learning target to check
     * @return true if human review is required by default
     */
    public boolean requiresHumanReviewByDefault(LearningTarget target) {
        return switch (target) {
            case PROMPT_TEMPLATE, PLANNER_POLICY, MODEL_ADAPTER, MASTERY_STATE -> true;
            default -> false;
        };
    }

    /**
     * Returns true if this learning level — and <em>only</em> this level — permits
     * the target, with no hierarchical inheritance from lower levels.
     *
     * <p>Use this method when you need to know whether a target is exclusively
     * introduced at a specific level (for audit, UI presentation, or documentation
     * purposes), rather than whether it is cumulatively allowed.
     *
     * <p>This is distinct from {@link #allows(LearningTarget)}, which is the
     * declaration-time check for whether a configured learning level permits a
     * target at all. {@code allowsAtLevelOnly} does <em>not</em> grant runtime
     * permission — use {@code LearningContract#permits} (backed by
     * {@code PromotionEngine}) for runtime enforcement.
     *
     * <p><strong>Runtime permission is NOT derived from this method alone.</strong>
     * Even if {@code L5.allowsAtLevelOnly(MASTERY_STATE)} returns {@code true},
     * normal agents are still blocked by {@code LearningContract}. Only the
     * {@code PromotionEngine} and approved governance workflows may act on
     * {@code MASTERY_STATE}.
     *
     * @param target the learning target to check
     * @return true if this level exclusively introduces permission for the target
     */
    public boolean allowsAtLevelOnly(@org.jetbrains.annotations.NotNull LearningTarget target) {
        return switch (this) {
            case L0 -> false;
            case L1 -> target == LearningTarget.EPISODIC_MEMORY;
            case L2 -> target == LearningTarget.SEMANTIC_FACT
                    || target == LearningTarget.RETRIEVAL_POLICY
                    || target == LearningTarget.CONFIDENCE_THRESHOLD
                    || target == LearningTarget.ROUTING_POLICY;
            case L3 -> target == LearningTarget.PROCEDURAL_SKILL
                    || target == LearningTarget.NEGATIVE_KNOWLEDGE;
            case L4 -> target == LearningTarget.PROMPT_TEMPLATE
                    || target == LearningTarget.PLANNER_POLICY;
            // L5 exclusively introduces offline governance targets.
            // MASTERY_STATE is gated here by design — allowsAtLevelOnly is for
            // declaration/documentation purposes only, not runtime permission.
            case L5 -> target == LearningTarget.MODEL_ADAPTER
                    || target == LearningTarget.MASTERY_STATE;
        };
    }
}
