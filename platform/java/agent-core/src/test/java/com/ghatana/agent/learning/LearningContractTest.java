/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                false,
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
                false,
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
                true,
                false
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
                true,
                false
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
                true,
                false
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
                false,
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
                true,
                false
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
                true,
                false
        );
        assertThat(contract1.permits(LearningTarget.PROCEDURAL_SKILL)).isFalse();

        // Target in allowedTargets but level doesn't permit
        LearningContract contract2 = new LearningContract(
                LearningLevel.L2,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                false,
                false
        );
        assertThat(contract2.permits(LearningTarget.PROCEDURAL_SKILL)).isFalse();

        // Both conditions met
        LearningContract contract3 = new LearningContract(
                LearningLevel.L3,
                Set.of(LearningTarget.PROCEDURAL_SKILL),
                true,
                true,
                false
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
        // Even L5 with MASTERY_STATE in allowedTargets should not permit it without governanceWorkflow
        LearningContract contract = new LearningContract(
                LearningLevel.L5,
                Set.of(LearningTarget.MASTERY_STATE),
                true,
                true,
                false
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
                    level.requiresPromotion(),
                    false // non-governance contracts never permit MASTERY_STATE
            );

            assertThat(contract.permits(LearningTarget.MASTERY_STATE))
                    .as("Level " + level + " contract should not permit MASTERY_STATE")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("Governance workflow at L5 may permit MASTERY_STATE")
    void governanceWorkflowAtL5PermitsMasteryState() {
        LearningContract contract = new LearningContract(
                LearningLevel.L5,
                Set.of(LearningTarget.MASTERY_STATE),
                true,
                true,
                true // governanceWorkflow — only path that permits MASTERY_STATE
        );

        assertThat(contract.permits(LearningTarget.MASTERY_STATE)).isTrue();
        assertThat(contract.governanceWorkflow()).isTrue();
        assertThat(contract.level()).isEqualTo(LearningLevel.L5);
        assertThat(LearningLevel.L5.isOfflineOnly()).isTrue();
    }

    @Test
    @DisplayName("Governance workflow requires L5 — constructor rejects sub-L5 governance flag")
    void governanceWorkflowRequiresL5() {
        for (LearningLevel level : LearningLevel.values()) {
            if (level == LearningLevel.L5) {
                continue; // L5 with governanceWorkflow=true is valid
            }
            final LearningLevel finalLevel = level;
            assertThatThrownBy(() -> new LearningContract(
                    finalLevel,
                    Set.of(),
                    finalLevel.requiresProvenance(),
                    finalLevel.requiresPromotion(),
                    true // governanceWorkflow=true at sub-L5 is forbidden
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Governance workflows require LearningLevel L5");
        }
    }

    @Test
    @DisplayName("L2+ requires provenanceRequired=true — constructor enforces invariant")
    void l2PlusRequiresProvenanceRequired() {
        assertThatThrownBy(() -> new LearningContract(
                LearningLevel.L2,
                Set.of(),
                false, // provenanceRequired=false violates L2 invariant
                false,
                false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires provenanceRequired=true");
    }

    @Test
    @DisplayName("L3+ requires promotionRequired=true — constructor enforces invariant")
    void l3PlusRequiresPromotionRequired() {
        assertThatThrownBy(() -> new LearningContract(
                LearningLevel.L3,
                Set.of(),
                true,
                false, // promotionRequired=false violates L3 invariant
                false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires promotionRequired=true");
    }
}
