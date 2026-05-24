/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern.spec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5: Schema registry integration tests for PatternSpec.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Output schema is required for all agent capabilities</li>
 *   <li>Output schema must be a valid schema reference</li>
 *   <li>Emit section requires output schema</li>
 * </ul>
 */
@DisplayName("PatternSpec Schema Registry Tests (Phase 5)")
class PatternSpecSchemaRegistryTest {

    // =========================================================================
    //  Agent Capability Output Schema Requirements
    // =========================================================================

    @Nested
    @DisplayName("Agent Capability Output Schema")
    class AgentCapabilitySchemaTests {

        @Test
        @DisplayName("AGENT_PREDICATE requires outputSchema")
        void agentPredicateRequiresOutputSchema() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "AGENT_PREDICATE",
                "agentRef", "agents/predicate@1.0.0",
                    "capabilityRef", "agents/predicate@1.0.0/capability")));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("outputSchema"));
        }

        @Test
        @DisplayName("AGENT_ENRICH requires outputSchema")
        void agentEnrichRequiresOutputSchema() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "AGENT_ENRICH",
                "agentRef", "agents/enricher@1.0.0",
                    "capabilityRef", "agents/enricher@1.0.0/capability")));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("outputSchema"));
        }

        @Test
        @DisplayName("AGENT_EXTRACT requires outputSchema")
        void agentExtractRequiresOutputSchema() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "AGENT_EXTRACT",
                "agentRef", "agents/extractor@1.0.0",
                    "capabilityRef", "agents/extractor@1.0.0/capability")));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("outputSchema"));
        }

        @Test
        @DisplayName("AGENT_PATTERN_SYNTHESIS requires outputSchema")
        void agentPatternSynthesisRequiresOutputSchema() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "AGENT_PATTERN_SYNTHESIS",
                "agentRef", "agents/synthesizer@1.0.0",
                    "capabilityRef", "agents/synthesizer@1.0.0/capability")));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("outputSchema"));
        }

        @Test
        @DisplayName("AGENT_EXPLANATION requires outputSchema")
        void agentExplanationRequiresOutputSchema() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "AGENT_EXPLANATION",
                "agentRef", "agents/explainer@1.0.0",
                    "capabilityRef", "agents/explainer@1.0.0/capability")));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("outputSchema"));
        }

        @Test
        @DisplayName("AGENT_REVIEW requires outputSchema")
        void agentReviewRequiresOutputSchema() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "AGENT_REVIEW",
                "agentRef", "agents/reviewer@1.0.0",
                    "capabilityRef", "agents/reviewer@1.0.0/capability")));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("outputSchema"));
        }

        @Test
        @DisplayName("AGENT_ACTION requires outputSchema")
        void agentActionRequiresOutputSchema() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "AGENT_ACTION",
                "agentRef", "agents/action@1.0.0",
                    "capabilityRef", "agents/action@1.0.0/capability",
                "toolPolicy", Map.of("allowedTools", List.of("tool1")))));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("outputSchema"));
        }

        @Test
        @DisplayName("AGENT_REFLECTION requires outputSchema")
        void agentReflectionRequiresOutputSchema() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "AGENT_REFLECTION",
                "agentRef", "agents/reflector@1.0.0",
                    "capabilityRef", "agents/reflector@1.0.0/capability")));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("outputSchema"));
        }

        @Test
        @DisplayName("agent capability with outputSchema is valid")
        void agentOperatorWithOutputSchemaIsValid() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "AGENT_PREDICATE",
                "agentRef", "agents/predicate@1.0.0",
                    "capabilityRef", "agents/predicate@1.0.0/capability",
                "outputSchema", "PredicateResult")));

            assertThat(result.valid()).isTrue();
        }
    }

    // =========================================================================
    //  Emit Section Schema Requirements
    // =========================================================================

    @Nested
    @DisplayName("Emit Section Schema")
    class EmitSchemaTests {

        @Test
        @DisplayName("emit section requires outputSchema")
        void emitRequiresOutputSchema() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("emit", Map.of("eventType", "test.matched"));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("outputSchema"));
        }

        @Test
        @DisplayName("emit section with outputSchema is valid")
        void emitWithOutputSchemaIsValid() {
            Map<String, Object> spec = validSpec(Map.of("event", "test.event"));
            spec.put("emit", Map.of("eventType", "test.matched", "outputSchema", "TestOutput"));

            PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

            assertThat(result.valid()).isTrue();
        }
    }

    // =========================================================================
    //  Schema Reference Validation
    // =========================================================================

    @Nested
    @DisplayName("Schema Reference Validation")
    class SchemaReferenceTests {

        @Test
        @DisplayName("outputSchema can be a simple string reference")
        void outputSchemaCanBeStringReference() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "AGENT_PREDICATE",
                "agentRef", "agents/predicate@1.0.0",
                    "capabilityRef", "agents/predicate@1.0.0/capability",
                "outputSchema", "PredicateResult")));

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("outputSchema can be a complex schema object")
        void outputSchemaCanBeComplexObject() {
            PatternSpecValidationResult result = PatternSpecValidator.validate(validSpec(Map.of(
                "operator", "AGENT_PREDICATE",
                "agentRef", "agents/predicate@1.0.0",
                    "capabilityRef", "agents/predicate@1.0.0/capability",
                "outputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of("result", Map.of("type", "boolean"))))));

            assertThat(result.valid()).isTrue();
        }
    }

    // =========================================================================
    //  Helper
    // =========================================================================

    private static Map<String, Object> validSpec(Map<String, Object> pattern) {
        return new java.util.LinkedHashMap<>(Map.of(
            "apiVersion", "aep.ghatana.io/v1",
            "kind", "PatternSpec",
            "metadata", Map.of("name", "test", "tenantId", "tenant-a", "owner", "sre"),
            "semantics", Map.of("timePolicy", Map.of(), "uncertaintyPolicy", Map.of(), "replayPolicy", Map.of()),
            "pattern", pattern,
            "emit", Map.of("eventType", "pattern.matched", "outputSchema", "PatternMatched"),
            "lifecycle", Map.of("state", "SHADOW"),
            "governance", Map.of("reviewPolicy", "human_required"),
            "observability", Map.of("metrics", true, "tracing", true)));
    }
}
