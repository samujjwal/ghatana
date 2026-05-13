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

import static org.junit.jupiter.api.Assertions.*;

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

        assertEquals(LearningLevel.L0, contract.level());
        assertTrue(contract.allowedTargets().isEmpty());
        assertFalse(contract.provenanceRequired());
        assertFalse(contract.promotionRequired());
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

        assertEquals(LearningLevel.L3, contract.level());
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

        assertEquals(LearningLevel.L2, contract.level());
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

        assertThrows(IllegalStateException.class, definition::toLearningContract);
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

        assertTrue(contract.allowedTargets().contains(LearningTarget.PROCEDURAL_SKILL));
        assertTrue(contract.allowedTargets().contains(LearningTarget.NEGATIVE_KNOWLEDGE));
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

        assertTrue(contract.provenanceRequired());
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

        assertTrue(contract.promotionRequired());
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

        assertTrue(contract.provenanceRequired());
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

        assertTrue(contract.promotionRequired());
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

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Learning level mismatch"));
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

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Invalid adaptation target"));
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
                .build();

        List<String> errors = definition.validateLearningLevelConsistency();

        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Should compile mastery binding from AgentDefinition")
    void shouldCompileMasteryBindingFromAgentDefinition() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .masteryBindings(Map.of(
                        "namespace", "test-namespace",
                        "registryRef", "test-registry"
                ))
                .metadata("masteryPolicyRef", "policy-1")
                .metadata("skillRefs", List.of("skill-1", "skill-2"))
                .build();

        // Verify mastery binding compiles
        var masteryBinding = definition.toMasteryBinding();
        assertNotNull(masteryBinding);
    }

    @Test
    @DisplayName("Should compile version compatibility policy from AgentDefinition")
    void shouldCompileVersionCompatibilityPolicyFromAgentDefinition() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.PROBABILISTIC)
                .metadata("versionCompatibilityPolicy", "strict")
                .build();

        // Verify version compatibility policy compiles
        var versionPolicy = definition.toVersionCompatibilityPolicy();
        assertNotNull(versionPolicy);
    }

    @Test
    @DisplayName("Should compile freshness policy from AgentDefinition")
    void shouldCompileFreshnessPolicyFromAgentDefinition() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.PROBABILISTIC)
                .metadata("freshnessPolicy", "strict")
                .build();

        // Verify freshness policy compiles
        var freshnessPolicy = definition.toFreshnessPolicy();
        assertNotNull(freshnessPolicy);
    }
}
