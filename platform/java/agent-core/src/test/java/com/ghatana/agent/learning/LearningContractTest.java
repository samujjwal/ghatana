/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for LearningContract learning level permissions
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("LearningContract Tests")
class LearningContractTest {

    @Test
    @DisplayName("L1 permits only episodic memory")
    void l1PermitsOnlyEpisodicMemory() {
        LearningContract contract = new LearningContract(
                LearningLevel.L1,
                Set.of(LearningTarget.EPISODIC_MEMORY),
                true,
                false
        );

        assertThat(contract.permits(LearningTarget.EPISODIC_MEMORY)).isTrue();
        assertThat(contract.permits(LearningTarget.SEMANTIC_FACT)).isFalse();
        assertThat(contract.permits(LearningTarget.PROCEDURAL_SKILL)).isFalse();
        assertThat(contract.permits(LearningTarget.NEGATIVE_KNOWLEDGE)).isFalse();
        assertThat(contract.permits(LearningTarget.RETRIEVAL_POLICY)).isFalse();
        assertThat(contract.permits(LearningTarget.CONFIDENCE_THRESHOLD)).isFalse();
        assertThat(contract.permits(LearningTarget.ROUTING_POLICY)).isFalse();
        assertThat(contract.permits(LearningTarget.PROMPT_TEMPLATE)).isFalse();
        assertThat(contract.permits(LearningTarget.PLANNER_POLICY)).isFalse();
        assertThat(contract.permits(LearningTarget.MODEL_ADAPTER)).isFalse();
    }

    @Test
    @DisplayName("L2 permits semantic, retrieval, confidence, and routing policies")
    void l2PermitsSemanticAndRetrievalPolicies() {
        LearningContract contract = new LearningContract(
                LearningLevel.L2,
                Set.of(
                        LearningTarget.EPISODIC_MEMORY,
                        LearningTarget.SEMANTIC_FACT,
                        LearningTarget.RETRIEVAL_POLICY,
                        LearningTarget.CONFIDENCE_THRESHOLD,
                        LearningTarget.ROUTING_POLICY
                ),
                true,
                false
        );

        assertThat(contract.permits(LearningTarget.EPISODIC_MEMORY)).isTrue();
        assertThat(contract.permits(LearningTarget.SEMANTIC_FACT)).isTrue();
        assertThat(contract.permits(LearningTarget.RETRIEVAL_POLICY)).isTrue();
        assertThat(contract.permits(LearningTarget.CONFIDENCE_THRESHOLD)).isTrue();
        assertThat(contract.permits(LearningTarget.ROUTING_POLICY)).isTrue();
        assertThat(contract.permits(LearningTarget.PROCEDURAL_SKILL)).isFalse();
        assertThat(contract.permits(LearningTarget.NEGATIVE_KNOWLEDGE)).isFalse();
        assertThat(contract.permits(LearningTarget.PROMPT_TEMPLATE)).isFalse();
        assertThat(contract.permits(LearningTarget.PLANNER_POLICY)).isFalse();
        assertThat(contract.permits(LearningTarget.MODEL_ADAPTER)).isFalse();
    }

    @Test
    @DisplayName("L3 permits procedural skills and negative knowledge")
    void l3PermitsProceduralSkillsAndNegativeKnowledge() {
        LearningContract contract = new LearningContract(
                LearningLevel.L3,
                Set.of(
                        LearningTarget.EPISODIC_MEMORY,
                        LearningTarget.SEMANTIC_FACT,
                        LearningTarget.PROCEDURAL_SKILL,
                        LearningTarget.NEGATIVE_KNOWLEDGE,
                        LearningTarget.RETRIEVAL_POLICY,
                        LearningTarget.CONFIDENCE_THRESHOLD,
                        LearningTarget.ROUTING_POLICY
                ),
                true,
                true
        );

        assertThat(contract.permits(LearningTarget.EPISODIC_MEMORY)).isTrue();
        assertThat(contract.permits(LearningTarget.SEMANTIC_FACT)).isTrue();
        assertThat(contract.permits(LearningTarget.PROCEDURAL_SKILL)).isTrue();
        assertThat(contract.permits(LearningTarget.NEGATIVE_KNOWLEDGE)).isTrue();
        assertThat(contract.permits(LearningTarget.RETRIEVAL_POLICY)).isTrue();
        assertThat(contract.permits(LearningTarget.CONFIDENCE_THRESHOLD)).isTrue();
        assertThat(contract.permits(LearningTarget.ROUTING_POLICY)).isTrue();
        assertThat(contract.permits(LearningTarget.PROMPT_TEMPLATE)).isFalse();
        assertThat(contract.permits(LearningTarget.PLANNER_POLICY)).isFalse();
        assertThat(contract.permits(LearningTarget.MODEL_ADAPTER)).isFalse();
    }

    @Test
    @DisplayName("L4 permits prompt templates and planner policies")
    void l4PermitsPromptAndPlannerPolicies() {
        LearningContract contract = new LearningContract(
                LearningLevel.L4,
                Set.of(
                        LearningTarget.EPISODIC_MEMORY,
                        LearningTarget.SEMANTIC_FACT,
                        LearningTarget.PROCEDURAL_SKILL,
                        LearningTarget.NEGATIVE_KNOWLEDGE,
                        LearningTarget.RETRIEVAL_POLICY,
                        LearningTarget.CONFIDENCE_THRESHOLD,
                        LearningTarget.ROUTING_POLICY,
                        LearningTarget.PROMPT_TEMPLATE,
                        LearningTarget.PLANNER_POLICY
                ),
                true,
                true
        );

        assertThat(contract.permits(LearningTarget.PROMPT_TEMPLATE)).isTrue();
        assertThat(contract.permits(LearningTarget.PLANNER_POLICY)).isTrue();
        assertThat(contract.permits(LearningTarget.MODEL_ADAPTER)).isFalse();
    }

    @Test
    @DisplayName("L5 permits all targets including model adapter")
    void l5PermitsAllTargets() {
        LearningContract contract = new LearningContract(
                LearningLevel.L5,
                Set.of(
                        LearningTarget.EPISODIC_MEMORY,
                        LearningTarget.SEMANTIC_FACT,
                        LearningTarget.PROCEDURAL_SKILL,
                        LearningTarget.NEGATIVE_KNOWLEDGE,
                        LearningTarget.RETRIEVAL_POLICY,
                        LearningTarget.CONFIDENCE_THRESHOLD,
                        LearningTarget.ROUTING_POLICY,
                        LearningTarget.PROMPT_TEMPLATE,
                        LearningTarget.PLANNER_POLICY,
                        LearningTarget.MODEL_ADAPTER
                ),
                true,
                true
        );

        assertThat(contract.permits(LearningTarget.EPISODIC_MEMORY)).isTrue();
        assertThat(contract.permits(LearningTarget.SEMANTIC_FACT)).isTrue();
        assertThat(contract.permits(LearningTarget.PROCEDURAL_SKILL)).isTrue();
        assertThat(contract.permits(LearningTarget.NEGATIVE_KNOWLEDGE)).isTrue();
        assertThat(contract.permits(LearningTarget.RETRIEVAL_POLICY)).isTrue();
        assertThat(contract.permits(LearningTarget.CONFIDENCE_THRESHOLD)).isTrue();
        assertThat(contract.permits(LearningTarget.ROUTING_POLICY)).isTrue();
        assertThat(contract.permits(LearningTarget.PROMPT_TEMPLATE)).isTrue();
        assertThat(contract.permits(LearningTarget.PLANNER_POLICY)).isTrue();
        assertThat(contract.permits(LearningTarget.MODEL_ADAPTER)).isTrue();
    }

    @Test
    @DisplayName("L5 is offline-only")
    void l5IsOfflineOnly() {
        LearningLevel l5 = LearningLevel.L5;
        assertThat(l5.isOfflineOnly()).isTrue();

        for (LearningLevel level : LearningLevel.values()) {
            if (level != LearningLevel.L5) {
                assertThat(level.isOfflineOnly()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("L2 agent cannot write procedural skills even if in allowedTargets")
    void l2CannotWriteProceduralSkills() {
        LearningContract contract = new LearningContract(
                LearningLevel.L2,
                Set.of(LearningTarget.PROCEDURAL_SKILL), // In allowedTargets but level doesn't permit
                true,
                false
        );

        // The level check takes precedence
        assertThat(contract.permits(LearningTarget.PROCEDURAL_SKILL)).isFalse();
    }

    @Test
    @DisplayName("L3 agent can only propose procedural skills, not self-activate")
    void l3CanOnlyProposeProceduralSkills() {
        LearningContract contract = new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                true
        );

        // L3 permits the target
        assertThat(contract.permits(LearningTarget.PROCEDURAL_SKILL)).isTrue();

        // But promotion is required (cannot self-activate)
        assertThat(contract.promotionRequired()).isTrue();
    }

    @Test
    @DisplayName("Contract requires both level permission and target in allowedTargets")
    void contractRequiresBothLevelPermissionAndTargetInAllowedTargets() {
        // Level permits but target not in allowedTargets
        LearningContract contract1 = new LearningContract(
                LearningLevel.L3,
                Set.of(), // Empty allowed targets
                true,
                true
        );
        assertThat(contract1.permits(LearningTarget.PROCEDURAL_SKILL)).isFalse();

        // Target in allowedTargets but level doesn't permit
        LearningContract contract2 = new LearningContract(
                LearningLevel.L2,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                false
        );
        assertThat(contract2.permits(LearningTarget.PROCEDURAL_SKILL)).isFalse();

        // Both conditions met
        LearningContract contract3 = new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                true
        );
        assertThat(contract3.permits(LearningTarget.PROCEDURAL_SKILL)).isTrue();
    }

    @Test
    @DisplayName("L0 permits nothing")
    void l0PermitsNothing() {
        LearningContract contract = new LearningContract(
                LearningLevel.L0,
                Set.of(),
                false,
                false
        );

        for (LearningTarget target : LearningTarget.values()) {
            assertThat(contract.permits(target)).isFalse();
        }
    }
}
