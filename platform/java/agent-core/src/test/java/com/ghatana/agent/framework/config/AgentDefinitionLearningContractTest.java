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
}
