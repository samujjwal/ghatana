/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.config;

import com.ghatana.agent.AgentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AgentDefinition mastery validation.
 *
 * @doc.type class
 * @doc.purpose Tests for AgentDefinition mastery validation
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("AgentDefinition Mastery Validation Tests")
class AgentDefinitionMasteryValidationTest {

    @Test
    @DisplayName("ADAPTIVE agents must declare learningLevel >= L2")
    void adaptiveAgentsMustDeclareL2OrHigher() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L1")
                .build();

        AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definition);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("learningLevel >= L2"));
    }

    @Test
    @DisplayName("ADAPTIVE agents with L2 should pass validation")
    void adaptiveAgentsWithL2ShouldPass() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L2")
                .skillRefs(List.of("skill-1"))
                .build();

        AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definition);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("PROCEDURAL_SKILL target requires promotionRequired=true")
    void proceduralSkillRequiresPromotion() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .metadata("adaptationTargets", List.of("PROCEDURAL_SKILL"))
                .build();

        AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definition);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("promotionRequired=true"));
    }

    @Test
    @DisplayName("SEMANTIC_FACT target requires provenanceRequired=true")
    void semanticFactRequiresProvenance() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L2")
                .metadata("adaptationTargets", List.of("SEMANTIC_FACT"))
                .build();

        AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definition);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("provenanceRequired=true"));
    }

    @Test
    @DisplayName("MODEL_ADAPTER target requires learningLevel=L5")
    void modelAdapterRequiresL5() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L4")
                .metadata("adaptationTargets", List.of("MODEL_ADAPTER"))
                .build();

        AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definition);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("learningLevel=L5"));
    }

    @Test
    @DisplayName("L5 agents must not be response-serving")
    void l5AgentsMustNotBeResponseServing() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.PROBABILISTIC)
                .learningLevel("L5")
                .systemPrompt("You are a helpful assistant")
                .build();

        AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definition);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("must not be response-serving"));
    }

    @Test
    @DisplayName("ADAPTIVE agents must declare skillRefs")
    void adaptiveAgentsMustDeclareSkillRefs() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .build();

        AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definition);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("skillRefs"));
    }

    @Test
    @DisplayName("skillRefs must not contain blank values")
    void skillRefsMustNotContainBlank() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .skillRefs(List.of("skill-1", "", "skill-2"))
                .build();

        AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definition);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("blank"));
    }

    @Test
    @DisplayName("masteryBindings must include namespace")
    void masteryBindingsMustIncludeNamespace() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .skillRefs(List.of("skill-1"))
                .masteryBindings(Map.of("registryRef", "default-registry"))
                .build();

        AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definition);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("namespace"));
    }

    @Test
    @DisplayName("masteryBindings must include registryRef")
    void masteryBindingsMustIncludeRegistryRef() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .skillRefs(List.of("skill-1"))
                .masteryBindings(Map.of("namespace", "default"))
                .build();

        AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definition);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("registryRef"));
    }

    @Test
    @DisplayName("masteryBindings must include freshnessPolicyRef")
    void masteryBindingsMustIncludeFreshnessPolicyRef() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .skillRefs(List.of("skill-1"))
                .masteryBindings(Map.of(
                        "namespace", "default",
                        "registryRef", "default-registry"
                ))
                .build();

        AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definition);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("freshnessPolicyRef"));
    }

    @Test
    @DisplayName("masteryBindings must include versionCompatibilityPolicyRef")
    void masteryBindingsMustIncludeVersionCompatibilityPolicyRef() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .skillRefs(List.of("skill-1"))
                .masteryBindings(Map.of(
                        "namespace", "default",
                        "registryRef", "default-registry",
                        "freshnessPolicyRef", "default-freshness"
                ))
                .build();

        AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definition);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("versionCompatibilityPolicyRef"));
    }

    @Test
    @DisplayName("High-risk agents must include evaluationRefs")
    void highRiskAgentsMustIncludeEvaluationRefs() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .skillRefs(List.of("skill-1"))
                .label("criticality", "critical")
                .build();

        AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definition);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("evaluationRefs"));
    }

    @Test
    @DisplayName("Valid mastery configuration should pass validation")
    void validMasteryConfigurationShouldPass() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .skillRefs(List.of("skill-1"))
                .masteryBindings(Map.of(
                        "namespace", "default",
                        "registryRef", "default-registry",
                        "freshnessPolicyRef", "default-freshness",
                        "versionCompatibilityPolicyRef", "default-compat"
                ))
                .evaluationRefs(List.of("eval-pack-1"))
                .metadata("adaptationTargets", List.of("PROCEDURAL_SKILL"))
                .metadata("promotionRequired", true)
                .build();

        AgentDefinitionValidator.ValidationResult result = AgentDefinitionValidator.validate(definition);

        assertThat(result.isValid()).isTrue();
    }
}
