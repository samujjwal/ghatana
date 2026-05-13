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

    @Test
    @DisplayName("MASTERY_STATE is never permitted for normal agents")
    void masteryStateIsNeverPermitted() {
        // Even L5 with MASTERY_STATE in allowedTargets should not permit it
        LearningContract contract = new LearningContract(
                LearningLevel.L5,
                Set.of(LearningTarget.MASTERY_STATE),
                true,
                true
        );

        assertThat(contract.permits(LearningTarget.MASTERY_STATE)).isFalse();
    }

    @Test
    @DisplayName("MASTERY_STATE is not permitted by sub-L5 learning levels")
    void masteryStateNotPermittedByAnyLevel() {
        // L5 is the privileged offline-only governance tier; at the level layer it
        // returns true for MASTERY_STATE.  The hard enforcement boundary is in
        // LearningContract.permits(), which blocks MASTERY_STATE for ALL levels
        // (including L5) — verified by masteryStateGovernanceEnforcedEvenInAllowedTargets.
        for (LearningLevel level : LearningLevel.values()) {
            if (level == LearningLevel.L5) {
                continue; // governance enforcement lives at the contract layer for L5
            }
            assertThat(level.allows(LearningTarget.MASTERY_STATE))
                    .as("Level " + level + " should not permit MASTERY_STATE")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("MASTERY_STATE governance is enforced even when in allowedTargets")
    void masteryStateGovernanceEnforcedEvenInAllowedTargets() {
        for (LearningLevel level : LearningLevel.values()) {
            LearningContract contract = new LearningContract(
                    level,
                    Set.of(LearningTarget.MASTERY_STATE),
                    level.requiresProvenance(),
                    level.requiresPromotion()
            );

            assertThat(contract.permits(LearningTarget.MASTERY_STATE))
                    .as("Level " + level + " contract should not permit MASTERY_STATE")
                    .isFalse();
        }
    }
}
