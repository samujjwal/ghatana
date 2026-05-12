/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for LearningLevel enum.
 *
 * @doc.type class
 * @doc.purpose Tests for LearningLevel enum
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("LearningLevel Tests")
class LearningLevelTest {

    @Test
    @DisplayName("L0 should not allow any targets")
    void l0ShouldNotAllowAnyTargets() {
        LearningLevel level = LearningLevel.L0;
        assertThat(level.allows(LearningTarget.EPISODIC_MEMORY)).isFalse();
        assertThat(level.allows(LearningTarget.SEMANTIC_FACT)).isFalse();
        assertThat(level.allows(LearningTarget.PROCEDURAL_SKILL)).isFalse();
        assertThat(level.allows(LearningTarget.MODEL_ADAPTER)).isFalse();
    }

    @Test
    @DisplayName("L1 should only allow EPISODIC_MEMORY")
    void l1ShouldOnlyAllowEpisodicMemory() {
        LearningLevel level = LearningLevel.L1;
        assertThat(level.allows(LearningTarget.EPISODIC_MEMORY)).isTrue();
        assertThat(level.allows(LearningTarget.SEMANTIC_FACT)).isFalse();
        assertThat(level.allows(LearningTarget.PROCEDURAL_SKILL)).isFalse();
    }

    @Test
    @DisplayName("L2 should allow memory and policy targets")
    void l2ShouldAllowMemoryAndPolicyTargets() {
        LearningLevel level = LearningLevel.L2;
        assertThat(level.allows(LearningTarget.EPISODIC_MEMORY)).isTrue();
        assertThat(level.allows(LearningTarget.SEMANTIC_FACT)).isTrue();
        assertThat(level.allows(LearningTarget.RETRIEVAL_POLICY)).isTrue();
        assertThat(level.allows(LearningTarget.ROUTING_POLICY)).isTrue();
        assertThat(level.allows(LearningTarget.PROCEDURAL_SKILL)).isFalse();
    }

    @Test
    @DisplayName("L3 should allow procedural skills and negative knowledge")
    void l3ShouldAllowProceduralSkillsAndNegativeKnowledge() {
        LearningLevel level = LearningLevel.L3;
        assertThat(level.allows(LearningTarget.PROCEDURAL_SKILL)).isTrue();
        assertThat(level.allows(LearningTarget.NEGATIVE_KNOWLEDGE)).isTrue();
        assertThat(level.allows(LearningTarget.PROMPT_TEMPLATE)).isFalse();
    }

    @Test
    @DisplayName("L4 should allow prompt templates and planner policies")
    void l4ShouldAllowPromptTemplatesAndPlannerPolicies() {
        LearningLevel level = LearningLevel.L4;
        assertThat(level.allows(LearningTarget.PROMPT_TEMPLATE)).isTrue();
        assertThat(level.allows(LearningTarget.PLANNER_POLICY)).isTrue();
        assertThat(level.allows(LearningTarget.MODEL_ADAPTER)).isFalse();
    }

    @Test
    @DisplayName("L5 should allow all targets")
    void l5ShouldAllowAllTargets() {
        LearningLevel level = LearningLevel.L5;
        assertThat(level.allows(LearningTarget.MODEL_ADAPTER)).isTrue();
        assertThat(level.allows(LearningTarget.MASTERY_STATE)).isTrue();
    }

    @Test
    @DisplayName("L5 should be offline only")
    void l5ShouldBeOfflineOnly() {
        assertThat(LearningLevel.L5.isOfflineOnly()).isTrue();
        assertThat(LearningLevel.L4.isOfflineOnly()).isFalse();
        assertThat(LearningLevel.L0.isOfflineOnly()).isFalse();
    }

    @Test
    @DisplayName("L2 and above should require provenance")
    void l2AndAboveShouldRequireProvenance() {
        assertThat(LearningLevel.L0.requiresProvenance()).isFalse();
        assertThat(LearningLevel.L1.requiresProvenance()).isFalse();
        assertThat(LearningLevel.L2.requiresProvenance()).isTrue();
        assertThat(LearningLevel.L3.requiresProvenance()).isTrue();
        assertThat(LearningLevel.L4.requiresProvenance()).isTrue();
        assertThat(LearningLevel.L5.requiresProvenance()).isTrue();
    }

    @Test
    @DisplayName("L3 and above should require promotion")
    void l3AndAboveShouldRequirePromotion() {
        assertThat(LearningLevel.L0.requiresPromotion()).isFalse();
        assertThat(LearningLevel.L1.requiresPromotion()).isFalse();
        assertThat(LearningLevel.L2.requiresPromotion()).isFalse();
        assertThat(LearningLevel.L3.requiresPromotion()).isTrue();
        assertThat(LearningLevel.L4.requiresPromotion()).isTrue();
        assertThat(LearningLevel.L5.requiresPromotion()).isTrue();
    }

    @Test
    @DisplayName("All levels except L5 can run online")
    void allLevelsExceptL5CanRunOnline() {
        assertThat(LearningLevel.L0.canRunOnline()).isTrue();
        assertThat(LearningLevel.L1.canRunOnline()).isTrue();
        assertThat(LearningLevel.L2.canRunOnline()).isTrue();
        assertThat(LearningLevel.L3.canRunOnline()).isTrue();
        assertThat(LearningLevel.L4.canRunOnline()).isTrue();
        assertThat(LearningLevel.L5.canRunOnline()).isFalse();
    }

    @Test
    @DisplayName("All levels except L5 can serve responses")
    void allLevelsExceptL5CanServeResponses() {
        assertThat(LearningLevel.L0.canServeResponses()).isTrue();
        assertThat(LearningLevel.L1.canServeResponses()).isTrue();
        assertThat(LearningLevel.L2.canServeResponses()).isTrue();
        assertThat(LearningLevel.L3.canServeResponses()).isTrue();
        assertThat(LearningLevel.L4.canServeResponses()).isTrue();
        assertThat(LearningLevel.L5.canServeResponses()).isFalse();
    }
}
