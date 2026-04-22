/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */

package com.ghatana.agent.framework.config;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.FailureMode;
import com.ghatana.agent.StateMutability;
import com.ghatana.agent.framework.config.AgentDefinition.IOContract;
import com.ghatana.agent.framework.config.AgentDefinition.ParameterSchema;
import com.ghatana.agent.framework.config.AgentDefinition.ToolDeclaration;
import com.ghatana.agent.framework.config.AgentDefinitionValidator.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AgentDefinition}, {@link AgentInstance}, and {@link AgentDefinitionValidator}.
 */
@DisplayName("Agent Configuration Value Objects [GH-90000]")
class AgentDefinitionTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentDefinition Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentDefinition [GH-90000]")
    class AgentDefinitionTests {

        @Test
        @DisplayName("should build with required fields [GH-90000]")
        void shouldBuildWithRequiredFields() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("test-agent [GH-90000]")
                    .version("1.0.0 [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .systemPrompt("You are a test agent. [GH-90000]")
                    .build(); // GH-90000

            assertThat(def.getId()).isEqualTo("test-agent [GH-90000]");
            assertThat(def.getVersion()).isEqualTo("1.0.0 [GH-90000]");
            assertThat(def.getType()).isEqualTo(AgentType.PROBABILISTIC); // GH-90000
            assertThat(def.getName()).isEqualTo("test-agent [GH-90000]"); // defaults to id
            assertThat(def.getCanonicalId()).isEqualTo("test-agent:1.0.0 [GH-90000]");
        }

        @Test
        @DisplayName("should build with all fields [GH-90000]")
        void shouldBuildWithAllFields() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("fraud-detector [GH-90000]")
                    .version("2.1.0 [GH-90000]")
                    .name("Fraud Detector [GH-90000]")
                    .description("Detects fraudulent transactions [GH-90000]")
                    .type(AgentType.HYBRID) // GH-90000
                    .subtype("RULE_LLM [GH-90000]")
                    .determinism(DeterminismGuarantee.CONFIG_SCOPED) // GH-90000
                    .stateMutability(StateMutability.LOCAL_STATE) // GH-90000
                    .failureMode(FailureMode.CIRCUIT_BREAKER) // GH-90000
                    .systemPrompt("You are a fraud detection agent. [GH-90000]")
                    .maxTokens(8192) // GH-90000
                    .temperature(0.3) // GH-90000
                    .inputContract(new IOContract("TransactionEvent", "JSON", null)) // GH-90000
                    .outputContract(new IOContract("FraudAssessment", "JSON", null)) // GH-90000
                    .addTool(new ToolDeclaration("lookupTransaction", "Look up transaction", // GH-90000
                            Map.of("txId", ParameterSchema.requiredString("Transaction ID [GH-90000]"))))
                    .addCapability("fraud-detection [GH-90000]")
                    .addCapability("risk-scoring [GH-90000]")
                    .timeout(Duration.ofSeconds(10)) // GH-90000
                    .maxCostPerCall(0.05) // GH-90000
                    .maxRetries(5) // GH-90000
                    .label("team", "security") // GH-90000
                    .metadata("priority", "high") // GH-90000
                    .build(); // GH-90000

            assertThat(def.getName()).isEqualTo("Fraud Detector [GH-90000]");
            assertThat(def.getCanonicalId()).isEqualTo("fraud-detector:2.1.0 [GH-90000]");
            assertThat(def.getType()).isEqualTo(AgentType.HYBRID); // GH-90000
            assertThat(def.getTools()).hasSize(1); // GH-90000
            assertThat(def.getCapabilities()).containsExactlyInAnyOrder("fraud-detection", "risk-scoring"); // GH-90000
            assertThat(def.getLabels()).containsEntry("team", "security"); // GH-90000
            assertThat(def.getTimeout()).isEqualTo(Duration.ofSeconds(10)); // GH-90000
        }

        @Test
        @DisplayName("should be immutable — collections are unmodifiable [GH-90000]")
        void shouldBeImmutable() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("test [GH-90000]")
                    .version("1.0.0 [GH-90000]")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .label("k", "v") // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> def.getLabels().put("new", "value")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
            assertThatThrownBy(() -> def.getTools().add( // GH-90000
                    new ToolDeclaration("x", "x", Map.of()))) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
            assertThatThrownBy(() -> def.getCapabilities().add("new [GH-90000]"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("should use equals/hashCode based on id+version [GH-90000]")
        void shouldUseEqualsOnIdVersion() { // GH-90000
            AgentDefinition a = AgentDefinition.builder() // GH-90000
                    .id("agent-a [GH-90000]").version("1.0.0 [GH-90000]").type(AgentType.PROBABILISTIC)
                    .systemPrompt("A [GH-90000]").build();
            AgentDefinition b = AgentDefinition.builder() // GH-90000
                    .id("agent-a [GH-90000]").version("1.0.0 [GH-90000]").type(AgentType.DETERMINISTIC)
                    .build(); // GH-90000

            assertThat(a).isEqualTo(b); // GH-90000
            assertThat(a.hashCode()).isEqualTo(b.hashCode()); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentInstance Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentInstance [GH-90000]")
    class AgentInstanceTests {

        private AgentDefinition baseDefinition() { // GH-90000
            return AgentDefinition.builder() // GH-90000
                    .id("test-agent [GH-90000]")
                    .version("1.0.0 [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .systemPrompt("You are a test agent. [GH-90000]")
                    .timeout(Duration.ofSeconds(30)) // GH-90000
                    .maxTokens(4096) // GH-90000
                    .temperature(0.7) // GH-90000
                    .maxCostPerCall(0.10) // GH-90000
                    .failureMode(FailureMode.FAIL_FAST) // GH-90000
                    .label("env", "prod") // GH-90000
                    .build(); // GH-90000
        }

        @Test
        @DisplayName("should auto-generate instanceId from tenant+definition [GH-90000]")
        void shouldAutoGenerateInstanceId() { // GH-90000
            AgentInstance inst = AgentInstance.builder() // GH-90000
                    .tenantId("acme [GH-90000]")
                    .definition(baseDefinition()) // GH-90000
                    .build(); // GH-90000

            assertThat(inst.getInstanceId()).isEqualTo("acme:test-agent:1.0.0 [GH-90000]");
            assertThat(inst.getTenantId()).isEqualTo("acme [GH-90000]");
        }

        @Test
        @DisplayName("should fall back to definition values when no overrides [GH-90000]")
        void shouldFallBackToDefinitionDefaults() { // GH-90000
            AgentInstance inst = AgentInstance.builder() // GH-90000
                    .tenantId("acme [GH-90000]")
                    .definition(baseDefinition()) // GH-90000
                    .build(); // GH-90000

            assertThat(inst.getEffectiveTimeout()).isEqualTo(Duration.ofSeconds(30)); // GH-90000
            assertThat(inst.getEffectiveTemperature()).isEqualTo(0.7); // GH-90000
            assertThat(inst.getEffectiveMaxTokens()).isEqualTo(4096); // GH-90000
            assertThat(inst.getEffectiveFailureMode()).isEqualTo(FailureMode.FAIL_FAST); // GH-90000
        }

        @Test
        @DisplayName("should apply overrides over definition defaults [GH-90000]")
        void shouldApplyOverrides() { // GH-90000
            AgentInstance inst = AgentInstance.builder() // GH-90000
                    .tenantId("globex [GH-90000]")
                    .definition(baseDefinition()) // GH-90000
                    .overrides(AgentInstance.Overrides.builder() // GH-90000
                            .model("gpt-4o [GH-90000]")
                            .timeout(Duration.ofSeconds(60)) // GH-90000
                            .temperature(0.1) // GH-90000
                            .maxTokens(8192) // GH-90000
                            .failureMode(FailureMode.CIRCUIT_BREAKER) // GH-90000
                            .rateLimitPerSecond(100) // GH-90000
                            .label("env", "staging") // GH-90000
                            .featureFlag("beta-tools", true) // GH-90000
                            .build() // GH-90000
                    )
                    .build(); // GH-90000

            assertThat(inst.getEffectiveModel()).isEqualTo("gpt-4o [GH-90000]");
            assertThat(inst.getEffectiveTimeout()).isEqualTo(Duration.ofSeconds(60)); // GH-90000
            assertThat(inst.getEffectiveTemperature()).isEqualTo(0.1); // GH-90000
            assertThat(inst.getEffectiveMaxTokens()).isEqualTo(8192); // GH-90000
            assertThat(inst.getEffectiveFailureMode()).isEqualTo(FailureMode.CIRCUIT_BREAKER); // GH-90000
            assertThat(inst.getEffectiveRateLimit()).isEqualTo(100); // GH-90000
            assertThat(inst.isFeatureEnabled("beta-tools [GH-90000]")).isTrue();
            // Label override replaces definition's "env"
            assertThat(inst.getEffectiveLabels()).containsEntry("env", "staging"); // GH-90000
        }

        @Test
        @DisplayName("should support hot reload via applyOverrides [GH-90000]")
        void shouldSupportHotReload() { // GH-90000
            AgentInstance inst = AgentInstance.builder() // GH-90000
                    .tenantId("acme [GH-90000]")
                    .definition(baseDefinition()) // GH-90000
                    .build(); // GH-90000

            assertThat(inst.getEffectiveModel()).isNull(); // GH-90000

            // Hot reload: switch to a different model
            inst.applyOverrides(AgentInstance.Overrides.builder() // GH-90000
                    .model("claude-3-opus [GH-90000]")
                    .build() // GH-90000
            );

            assertThat(inst.getEffectiveModel()).isEqualTo("claude-3-opus [GH-90000]");
            assertThat(inst.getLastUpdatedAt()).isAfter(inst.getCreatedAt()); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validator Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentDefinitionValidator [GH-90000]")
    class ValidatorTests {

        @Test
        @DisplayName("should pass for a well-formed PROBABILISTIC definition [GH-90000]")
        void shouldPassForValidProbabilisticDefinition() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("test-agent [GH-90000]")
                    .version("1.0.0 [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .subtype("llm [GH-90000]")
                    .systemPrompt("You are helpful. [GH-90000]")
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(def); // GH-90000
            assertThat(result.isValid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should fail when PROBABILISTIC agent has no systemPrompt [GH-90000]")
        void shouldFailProbabilisticWithoutSystemPrompt() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("test-agent [GH-90000]")
                    .version("1.0.0 [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .subtype("llm [GH-90000]")
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(def); // GH-90000
            assertThat(result.isValid()).isFalse(); // GH-90000
            assertThat(result.errors()).anyMatch(e -> e.contains("[semantic] [GH-90000]") && e.contains("systemPrompt [GH-90000]"));
        }

        @Test
        @DisplayName("should fail when deterministic agent has systemPrompt [GH-90000]")
        void shouldFailDeterministicWithSystemPrompt() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("det-agent [GH-90000]")
                    .version("1.0.0 [GH-90000]")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .systemPrompt("Should not be here [GH-90000]")
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(def); // GH-90000
            assertThat(result.isValid()).isFalse(); // GH-90000
            assertThat(result.errors()).anyMatch(e -> e.contains("[semantic] [GH-90000]") && e.contains("DETERMINISTIC [GH-90000]"));
        }

        @Test
        @DisplayName("should fail for excessive cost [GH-90000]")
        void shouldFailForExcessiveCost() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("expensive [GH-90000]")
                    .version("1.0.0 [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .subtype("llm [GH-90000]")
                    .systemPrompt("Costly agent [GH-90000]")
                    .maxCostPerCall(50.0) // GH-90000
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(def); // GH-90000
            assertThat(result.isValid()).isFalse(); // GH-90000
            assertThat(result.errors()).anyMatch(e -> e.contains("[cost] [GH-90000]") && e.contains("maxCostPerCall [GH-90000]"));
        }

        @Test
        @DisplayName("should fail for sensitive capabilities without security review [GH-90000]")
        void shouldFailForUnreviewedSensitiveCapabilities() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("dangerous [GH-90000]")
                    .version("1.0.0 [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .subtype("llm [GH-90000]")
                    .systemPrompt("Code executor [GH-90000]")
                    .addCapability("execute-code [GH-90000]")
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(def); // GH-90000
            assertThat(result.isValid()).isFalse(); // GH-90000
            assertThat(result.errors()).anyMatch(e -> // GH-90000
                    e.contains("[security] [GH-90000]") && e.contains("execute-code [GH-90000]"));
        }

        @Test
        @DisplayName("should pass for reviewed sensitive capabilities [GH-90000]")
        void shouldPassForReviewedSensitiveCapabilities() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("safe-executor [GH-90000]")
                    .version("1.0.0 [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .subtype("llm [GH-90000]")
                    .systemPrompt("Code executor [GH-90000]")
                    .addCapability("execute-code [GH-90000]")
                    .label("security.reviewed", "true") // GH-90000
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(def); // GH-90000
            assertThat(result.isValid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should validate AgentInstance with definition + overrides [GH-90000]")
        void shouldValidateInstanceWithOverrides() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("test [GH-90000]")
                    .version("1.0.0 [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .subtype("llm [GH-90000]")
                    .systemPrompt("test [GH-90000]")
                    .build(); // GH-90000

            AgentInstance inst = AgentInstance.builder() // GH-90000
                    .tenantId("acme [GH-90000]")
                    .definition(def) // GH-90000
                    .overrides(AgentInstance.Overrides.builder() // GH-90000
                            .maxTokens(200_000)  // exceeds limit // GH-90000
                            .build()) // GH-90000
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(inst); // GH-90000
            assertThat(result.isValid()).isFalse(); // GH-90000
            assertThat(result.errors()).anyMatch(e -> e.contains("maxTokens [GH-90000]") && e.contains("exceeds [GH-90000]"));
        }

        @Test
        @DisplayName("throwIfInvalid should throw for invalid config [GH-90000]")
        void shouldThrowIfInvalid() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("bad [GH-90000]")
                    .version("1.0.0 [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .temperature(5.0) // out of range // GH-90000
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(def); // GH-90000
            assertThatThrownBy(result::throwIfInvalid) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("validation failed [GH-90000]");
        }
    }
}
