/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.config;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.learning.LearningContract;
import com.ghatana.agent.learning.LearningLevel;
import com.ghatana.agent.learning.LearningTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for AgentDefinition learning contract materialization.
 *
 * @doc.type class
 * @doc.purpose Tests for AgentDefinition learning contract materialization
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("AgentDefinition Learning Contract Tests")
class AgentDefinitionLearningContractTest {

    @Test
    @DisplayName("Should materialize LearningContract with L0 by default")
    void shouldMaterializeL0ByDefault() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.DETERMINISTIC)
                .build();

        LearningContract contract = definition.toLearningContract();

        assertThat(contract.level()).isEqualTo(LearningLevel.L0);
        assertThat(contract.allowedTargets()).isEmpty();
        assertThat(contract.provenanceRequired()).isFalse();
        assertThat(contract.promotionRequired()).isFalse();
    }

    @Test
    @DisplayName("Should materialize LearningContract from learningLevel field")
    void shouldMaterializeFromLearningLevelField() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .build();

        LearningContract contract = definition.toLearningContract();

        assertThat(contract.level()).isEqualTo(LearningLevel.L3);
    }

    @Test
    @DisplayName("Should materialize LearningContract from metadata learningLevel")
    void shouldMaterializeFromMetadataLearningLevel() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .metadata("learningLevel", "L2")
                .build();

        LearningContract contract = definition.toLearningContract();

        assertThat(contract.level()).isEqualTo(LearningLevel.L2);
    }

    @Test
    @DisplayName("Should throw when learningLevel mismatch between field and metadata")
    void shouldThrowOnLearningLevelMismatch() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .metadata("learningLevel", "L2")
                .build();

        assertThatThrownBy(definition::toLearningContract)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Learning level mismatch");
    }

    @Test
    @DisplayName("Should extract adaptationTargets from metadata")
    void shouldExtractAdaptationTargets() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .metadata("adaptationTargets", List.of("PROCEDURAL_SKILL", "NEGATIVE_KNOWLEDGE"))
                .build();

        LearningContract contract = definition.toLearningContract();

        assertThat(contract.allowedTargets())
                .containsExactlyInAnyOrder(LearningTarget.PROCEDURAL_SKILL, LearningTarget.NEGATIVE_KNOWLEDGE);
    }

    @Test
    @DisplayName("Should set provenanceRequired based on metadata")
    void shouldSetProvenanceRequiredFromMetadata() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .metadata("provenanceRequired", true)
                .build();

        LearningContract contract = definition.toLearningContract();

        assertThat(contract.provenanceRequired()).isTrue();
    }

    @Test
    @DisplayName("Should set promotionRequired based on metadata")
    void shouldSetPromotionRequiredFromMetadata() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .metadata("promotionRequired", true)
                .build();

        LearningContract contract = definition.toLearningContract();

        assertThat(contract.promotionRequired()).isTrue();
    }

    @Test
    @DisplayName("Should default provenanceRequired to true for L2+")
    void shouldDefaultProvenanceRequiredForL2AndAbove() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L2")
                .build();

        LearningContract contract = definition.toLearningContract();

        assertThat(contract.provenanceRequired()).isTrue();
    }

    @Test
    @DisplayName("Should default promotionRequired to true for L3+")
    void shouldDefaultPromotionRequiredForL3AndAbove() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .build();

        LearningContract contract = definition.toLearningContract();

        assertThat(contract.promotionRequired()).isTrue();
    }

    @Test
    @DisplayName("Should validate learning level consistency")
    void shouldValidateLearningLevelConsistency() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .metadata("learningLevel", "L2")
                .build();

        List<String> errors = definition.validateLearningLevelConsistency();

        // The mismatch error must appear; additional errors may appear because L3 also
        // requires masteryPolicyRefs and evaluationRefs which are unset in this case.
        assertThat(errors).anyMatch(e -> e.contains("Learning level mismatch"));
    }

    @Test
    @DisplayName("Should validate invalid adaptation target")
    void shouldValidateInvalidAdaptationTarget() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .metadata("adaptationTargets", List.of("INVALID_TARGET"))
                .build();

        List<String> errors = definition.validateLearningLevelConsistency();

        // The invalid-target error must appear; additional errors may appear because L3 also
        // requires masteryPolicyRefs and evaluationRefs which are unset here.
        assertThat(errors).anyMatch(e -> e.contains("Invalid adaptation target"));
    }

    @Test
    @DisplayName("Should return empty errors for valid configuration")
    void shouldReturnEmptyErrorsForValidConfiguration() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .metadata("adaptationTargets", List.of("PROCEDURAL_SKILL"))
                .masteryPolicyRefs(List.of("mastery-policy-ref"))
                .evaluationRefs(List.of("eval-ref"))
                .build();

        List<String> errors = definition.validateLearningLevelConsistency();

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should reject MASTERY_STATE in adaptationTargets")
    void shouldRejectMasteryStateInAdaptationTargets() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L5")
                .metadata("adaptationTargets", List.of("MASTERY_STATE"))
                .build();

        LearningContract contract = definition.toLearningContract();

        // MASTERY_STATE should not be permitted even if in adaptationTargets
        assertThat(contract.permits(LearningTarget.MASTERY_STATE)).isFalse();
    }

    @Test
    @DisplayName("L5 permits all targets except MASTERY_STATE")
    void l5PermitsAllExceptMasteryState() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L5")
                .metadata("adaptationTargets", List.of(
                        "EPISODIC_MEMORY",
                        "SEMANTIC_FACT",
                        "PROCEDURAL_SKILL",
                        "NEGATIVE_KNOWLEDGE",
                        "RETRIEVAL_POLICY",
                        "CONFIDENCE_THRESHOLD",
                        "ROUTING_POLICY",
                        "PROMPT_TEMPLATE",
                        "PLANNER_POLICY",
                        "MODEL_ADAPTER"
                ))
                .build();

        LearningContract contract = definition.toLearningContract();

        // All normal targets should be permitted
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

        // MASTERY_STATE should never be permitted
        assertThat(contract.permits(LearningTarget.MASTERY_STATE)).isFalse();
    }

    // ---------------------------------------------------------------------------
    // Section 13.3: Governance boundary tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("L5 agent definition materializes an offline-only learning contract")
    void l5AgentDefinitionIsOfflineOnly() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("l5-agent")
                .version("1.0.0")
                .type(AgentType.PLANNING)
                .learningLevel("L5")
                .build();

        LearningContract contract = definition.toLearningContract();

        // L5 is an offline-only governance tier — no online serving allowed
        assertThat(contract.level().isOfflineOnly()).isTrue();
        assertThat(contract.level()).isEqualTo(LearningLevel.L5);
    }

    @Test
    @DisplayName("Governance L5 contract permits MASTERY_STATE via governanceWorkflow flag in metadata")
    void governanceL5ContractPermitsMasteryStateThroughGovernanceWorkflow() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("governance-agent")
                .version("1.0.0")
                .type(AgentType.PLANNING)
                .learningLevel("L5")
                .label("agentType", "governance")
                .metadata("governanceWorkflow", true)
                .metadata("adaptationTargets", List.of("MASTERY_STATE"))
                .build();

        LearningContract contract = definition.toLearningContract();

        assertThat(contract.governanceWorkflow()).isTrue();
        // Only the governance flag unlocks MASTERY_STATE — this is the sole authority path
        assertThat(contract.permits(LearningTarget.MASTERY_STATE)).isTrue();
    }

    @Test
    @DisplayName("Non-governance L5 contract cannot permit MASTERY_STATE even with target in metadata")
    void nonGovernanceL5ContractBlocksMasteryState() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("normal-l5-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L5")
                // governanceWorkflow NOT set — normal agent path
                .metadata("adaptationTargets", List.of("MASTERY_STATE"))
                .build();

        LearningContract contract = definition.toLearningContract();

        assertThat(contract.governanceWorkflow()).isFalse();
        // Without governance workflow, MASTERY_STATE is always blocked
        assertThat(contract.permits(LearningTarget.MASTERY_STATE)).isFalse();
    }

    @Test
    @DisplayName("Governance contract requires promotion for MASTERY_STATE proposals")
    void governanceContractRequiresPromotionForMasteryState() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("governance-agent")
                .version("1.0.0")
                .type(AgentType.PLANNING)
                .learningLevel("L5")
                .label("agentType", "governance")
                .metadata("governanceWorkflow", true)
                .metadata("adaptationTargets", List.of("MASTERY_STATE"))
                .build();

        LearningContract contract = definition.toLearningContract();

        // L5 governance contract must require promotion before MASTERY_STATE can be applied
        assertThat(contract.requiresPromotionFor(LearningTarget.MASTERY_STATE)).isTrue();
    }

    @Test
    @DisplayName("Sub-L5 agents that are online are not offline-only")
    void subL5AgentsAreNotOfflineOnly() {
        for (LearningLevel level : new LearningLevel[]{
                LearningLevel.L0, LearningLevel.L1, LearningLevel.L2,
                LearningLevel.L3, LearningLevel.L4}) {
            assertThat(level.isOfflineOnly())
                    .as("Level " + level + " should not be offline-only (may serve online responses)")
                    .isFalse();
        }
    }
}
