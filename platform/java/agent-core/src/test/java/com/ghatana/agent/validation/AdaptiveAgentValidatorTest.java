/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.validation;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.framework.config.AgentDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for AdaptiveAgentValidator
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("AdaptiveAgentValidator Tests")
class AdaptiveAgentValidatorTest {

    private final AdaptiveAgentValidator validator = new AdaptiveAgentValidator();

    @Test
    @DisplayName("L0 and L1 learning levels fail validation for adaptive agents")
    void l0AndL1FailValidation() {
        AgentDefinition l0Definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L0")
                .metadata(Map.of(
                        "adaptationTargets", List.of("SEMANTIC_FACT"),
                        "driftControls", Map.of("enabled", true)
                ))
                .build();

        AgentDefinition l1Definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L1")
                .metadata(Map.of(
                        "adaptationTargets", List.of("SEMANTIC_FACT"),
                        "driftControls", Map.of("enabled", true)
                ))
                .build();

        List<String> l0Errors = validator.validate(l0Definition);
        List<String> l1Errors = validator.validate(l1Definition);

        assertThat(l0Errors).isNotEmpty();
        assertThat(l0Errors).anyMatch(e -> e.contains("learning level must be L2 or higher"));

        assertThat(l1Errors).isNotEmpty();
        assertThat(l1Errors).anyMatch(e -> e.contains("learning level must be L2 or higher"));
    }

    @Test
    @DisplayName("Missing adaptation targets fails validation")
    void missingAdaptationTargetsFails() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L2")
                .metadata(Map.of(
                        "driftControls", Map.of("enabled", true)
                ))
                .build();

        List<String> errors = validator.validate(definition);

        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> e.contains("adaptation targets are required"));
    }

    @Test
    @DisplayName("Missing drift controls fails validation")
    void missingDriftControlsFails() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L2")
                .metadata(Map.of(
                        "adaptationTargets", List.of("SEMANTIC_FACT")
                ))
                .build();

        List<String> errors = validator.validate(definition);

        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> e.contains("drift controls are required"));
    }

    @Test
    @DisplayName("Invalid adaptation target fails validation")
    void invalidAdaptationTargetFails() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L2")
                .metadata(Map.of(
                        "adaptationTargets", List.of("INVALID_TARGET"),
                        "driftControls", Map.of("enabled", true)
                ))
                .build();

        List<String> errors = validator.validate(definition);

        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> e.contains("invalid adaptation target"));
    }

    @Test
    @DisplayName("L3 requires promotionRequired=true")
    void l3RequiresPromotionRequired() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .metadata(Map.of(
                        "adaptationTargets", List.of("PROCEDURAL_SKILL"),
                        "driftControls", Map.of("enabled", true),
                        "provenanceRequired", true,
                        "promotionRequired", false
                ))
                .build();

        List<String> errors = validator.validate(definition);

        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> e.contains("promotionRequired must be true for L3+"));
    }

    @Test
    @DisplayName("L2 requires provenanceRequired=true")
    void l2RequiresProvenanceRequired() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L2")
                .metadata(Map.of(
                        "adaptationTargets", List.of("SEMANTIC_FACT"),
                        "driftControls", Map.of("enabled", true),
                        "provenanceRequired", false
                ))
                .build();

        List<String> errors = validator.validate(definition);

        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> e.contains("provenanceRequired must be true for L2+"));
    }

    @Test
    @DisplayName("L5 requires offlineOnly=true")
    void l5RequiresOfflineOnly() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L5")
                .metadata(Map.of(
                        "adaptationTargets", List.of("MODEL_ADAPTER"),
                        "driftControls", Map.of("enabled", true),
                        "provenanceRequired", true,
                        "promotionRequired", true,
                        "offlineOnly", false
                ))
                .build();

        List<String> errors = validator.validate(definition);

        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> e.contains("offlineOnly must be true for L5"));
    }

    @Test
    @DisplayName("Valid L2 adaptive agent passes validation")
    void validL2AdaptiveAgentPasses() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L2")
                .metadata(Map.of(
                        "adaptationTargets", List.of("SEMANTIC_FACT", "RETRIEVAL_POLICY"),
                        "driftControls", Map.of("enabled", true),
                        "provenanceRequired", true
                ))
                .build();

        List<String> errors = validator.validate(definition);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Valid L3 adaptive agent passes validation")
    void validL3AdaptiveAgentPasses() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L3")
                .metadata(Map.of(
                        "adaptationTargets", List.of("PROCEDURAL_SKILL", "NEGATIVE_KNOWLEDGE"),
                        "driftControls", Map.of("enabled", true),
                        "provenanceRequired", true,
                        "promotionRequired", true
                ))
                .build();

        List<String> errors = validator.validate(definition);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Valid L5 adaptive agent passes validation")
    void validL5AdaptiveAgentPasses() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L5")
                .metadata(Map.of(
                        "adaptationTargets", List.of("MODEL_ADAPTER"),
                        "driftControls", Map.of("enabled", true),
                        "provenanceRequired", true,
                        "promotionRequired", true,
                        "offlineOnly", true
                ))
                .build();

        List<String> errors = validator.validate(definition);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Drift controls must be a map")
    void driftControlsMustBeMap() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L2")
                .metadata(Map.of(
                        "adaptationTargets", List.of("SEMANTIC_FACT"),
                        "driftControls", "not a map"
                ))
                .build();

        List<String> errors = validator.validate(definition);

        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> e.contains("drift controls must be a map"));
    }

    @Test
    @DisplayName("Drift controls must have enabled field")
    void driftControlsMustHaveEnabledField() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("L2")
                .metadata(Map.of(
                        "adaptationTargets", List.of("SEMANTIC_FACT"),
                        "driftControls", Map.of("someOtherField", true)
                ))
                .build();

        List<String> errors = validator.validate(definition);

        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> e.contains("must have 'enabled' field"));
    }

    @Test
    @DisplayName("Invalid learning level string fails validation")
    void invalidLearningLevelStringFails() {
        AgentDefinition definition = AgentDefinition.builder()
                .id("test-agent")
                .version("1.0.0")
                .type(AgentType.ADAPTIVE)
                .learningLevel("INVALID_LEVEL")
                .metadata(Map.of(
                        "adaptationTargets", List.of("SEMANTIC_FACT"),
                        "driftControls", Map.of("enabled", true)
                ))
                .build();

        List<String> errors = validator.validate(definition);

        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> e.contains("invalid learning level"));
    }
}
