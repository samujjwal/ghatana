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
@DisplayName("Agent Configuration Value Objects")
class AgentDefinitionTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentDefinition Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentDefinition")
    class AgentDefinitionTests {

        @Test
        @DisplayName("should build with required fields")
        void shouldBuildWithRequiredFields() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("test-agent")
                    .version("1.0.0")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .systemPrompt("You are a test agent.")
                    .build(); // GH-90000

            assertThat(def.getId()).isEqualTo("test-agent");
            assertThat(def.getVersion()).isEqualTo("1.0.0");
            assertThat(def.getType()).isEqualTo(AgentType.PROBABILISTIC); // GH-90000
            assertThat(def.getName()).isEqualTo("test-agent"); // defaults to id
            assertThat(def.getCanonicalId()).isEqualTo("test-agent:1.0.0");
        }

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("fraud-detector")
                    .version("2.1.0")
                    .name("Fraud Detector")
                    .description("Detects fraudulent transactions")
                    .type(AgentType.HYBRID) // GH-90000
                    .subtype("RULE_LLM")
                    .determinism(DeterminismGuarantee.CONFIG_SCOPED) // GH-90000
                    .stateMutability(StateMutability.LOCAL_STATE) // GH-90000
                    .failureMode(FailureMode.CIRCUIT_BREAKER) // GH-90000
                    .systemPrompt("You are a fraud detection agent.")
                    .maxTokens(8192) // GH-90000
                    .temperature(0.3) // GH-90000
                    .inputContract(new IOContract("TransactionEvent", "JSON", null)) // GH-90000
                    .outputContract(new IOContract("FraudAssessment", "JSON", null)) // GH-90000
                    .addTool(new ToolDeclaration("lookupTransaction", "Look up transaction", // GH-90000
                            Map.of("txId", ParameterSchema.requiredString("Transaction ID"))))
                    .addCapability("fraud-detection")
                    .addCapability("risk-scoring")
                    .timeout(Duration.ofSeconds(10)) // GH-90000
                    .maxCostPerCall(0.05) // GH-90000
                    .maxRetries(5) // GH-90000
                    .label("team", "security") // GH-90000
                    .metadata("priority", "high") // GH-90000
                    .build(); // GH-90000

            assertThat(def.getName()).isEqualTo("Fraud Detector");
            assertThat(def.getCanonicalId()).isEqualTo("fraud-detector:2.1.0");
            assertThat(def.getType()).isEqualTo(AgentType.HYBRID); // GH-90000
            assertThat(def.getTools()).hasSize(1); // GH-90000
            assertThat(def.getCapabilities()).containsExactlyInAnyOrder("fraud-detection", "risk-scoring"); // GH-90000
            assertThat(def.getLabels()).containsEntry("team", "security"); // GH-90000
            assertThat(def.getTimeout()).isEqualTo(Duration.ofSeconds(10)); // GH-90000
        }

        @Test
        @DisplayName("should be immutable — collections are unmodifiable")
        void shouldBeImmutable() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("test")
                    .version("1.0.0")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .label("k", "v") // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> def.getLabels().put("new", "value")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
            assertThatThrownBy(() -> def.getTools().add( // GH-90000
                    new ToolDeclaration("x", "x", Map.of()))) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
            assertThatThrownBy(() -> def.getCapabilities().add("new"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("should use equals/hashCode based on id+version")
        void shouldUseEqualsOnIdVersion() { // GH-90000
            AgentDefinition a = AgentDefinition.builder() // GH-90000
                    .id("agent-a").version("1.0.0").type(AgentType.PROBABILISTIC)
                    .systemPrompt("A").build();
            AgentDefinition b = AgentDefinition.builder() // GH-90000
                    .id("agent-a").version("1.0.0").type(AgentType.DETERMINISTIC)
                    .build(); // GH-90000

            assertThat(a).isEqualTo(b); // GH-90000
            assertThat(a.hashCode()).isEqualTo(b.hashCode()); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentInstance Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentInstance")
    class AgentInstanceTests {

        private AgentDefinition baseDefinition() { // GH-90000
            return AgentDefinition.builder() // GH-90000
                    .id("test-agent")
                    .version("1.0.0")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .systemPrompt("You are a test agent.")
                    .timeout(Duration.ofSeconds(30)) // GH-90000
                    .maxTokens(4096) // GH-90000
                    .temperature(0.7) // GH-90000
                    .maxCostPerCall(0.10) // GH-90000
                    .failureMode(FailureMode.FAIL_FAST) // GH-90000
                    .label("env", "prod") // GH-90000
                    .build(); // GH-90000
        }

        @Test
        @DisplayName("should auto-generate instanceId from tenant+definition")
        void shouldAutoGenerateInstanceId() { // GH-90000
            AgentInstance inst = AgentInstance.builder() // GH-90000
                    .tenantId("acme")
                    .definition(baseDefinition()) // GH-90000
                    .build(); // GH-90000

            assertThat(inst.getInstanceId()).isEqualTo("acme:test-agent:1.0.0");
            assertThat(inst.getTenantId()).isEqualTo("acme");
        }

        @Test
        @DisplayName("should fall back to definition values when no overrides")
        void shouldFallBackToDefinitionDefaults() { // GH-90000
            AgentInstance inst = AgentInstance.builder() // GH-90000
                    .tenantId("acme")
                    .definition(baseDefinition()) // GH-90000
                    .build(); // GH-90000

            assertThat(inst.getEffectiveTimeout()).isEqualTo(Duration.ofSeconds(30)); // GH-90000
            assertThat(inst.getEffectiveTemperature()).isEqualTo(0.7); // GH-90000
            assertThat(inst.getEffectiveMaxTokens()).isEqualTo(4096); // GH-90000
            assertThat(inst.getEffectiveFailureMode()).isEqualTo(FailureMode.FAIL_FAST); // GH-90000
        }

        @Test
        @DisplayName("should apply overrides over definition defaults")
        void shouldApplyOverrides() { // GH-90000
            AgentInstance inst = AgentInstance.builder() // GH-90000
                    .tenantId("globex")
                    .definition(baseDefinition()) // GH-90000
                    .overrides(AgentInstance.Overrides.builder() // GH-90000
                            .model("gpt-4o")
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

            assertThat(inst.getEffectiveModel()).isEqualTo("gpt-4o");
            assertThat(inst.getEffectiveTimeout()).isEqualTo(Duration.ofSeconds(60)); // GH-90000
            assertThat(inst.getEffectiveTemperature()).isEqualTo(0.1); // GH-90000
            assertThat(inst.getEffectiveMaxTokens()).isEqualTo(8192); // GH-90000
            assertThat(inst.getEffectiveFailureMode()).isEqualTo(FailureMode.CIRCUIT_BREAKER); // GH-90000
            assertThat(inst.getEffectiveRateLimit()).isEqualTo(100); // GH-90000
            assertThat(inst.isFeatureEnabled("beta-tools")).isTrue();
            // Label override replaces definition's "env"
            assertThat(inst.getEffectiveLabels()).containsEntry("env", "staging"); // GH-90000
        }

        @Test
        @DisplayName("should support hot reload via applyOverrides")
        void shouldSupportHotReload() { // GH-90000
            AgentInstance inst = AgentInstance.builder() // GH-90000
                    .tenantId("acme")
                    .definition(baseDefinition()) // GH-90000
                    .build(); // GH-90000

            assertThat(inst.getEffectiveModel()).isNull(); // GH-90000

            // Hot reload: switch to a different model
            inst.applyOverrides(AgentInstance.Overrides.builder() // GH-90000
                    .model("claude-3-opus")
                    .build() // GH-90000
            );

            assertThat(inst.getEffectiveModel()).isEqualTo("claude-3-opus");
            assertThat(inst.getLastUpdatedAt()).isAfter(inst.getCreatedAt()); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validator Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentDefinitionValidator")
    class ValidatorTests {

        @Test
        @DisplayName("should pass for a well-formed PROBABILISTIC definition")
        void shouldPassForValidProbabilisticDefinition() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("test-agent")
                    .version("1.0.0")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .subtype("llm")
                    .systemPrompt("You are helpful.")
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(def); // GH-90000
            assertThat(result.isValid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should fail when PROBABILISTIC agent has no systemPrompt")
        void shouldFailProbabilisticWithoutSystemPrompt() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("test-agent")
                    .version("1.0.0")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .subtype("llm")
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(def); // GH-90000
            assertThat(result.isValid()).isFalse(); // GH-90000
            assertThat(result.errors()).anyMatch(e -> e.contains("[semantic]") && e.contains("systemPrompt"));
        }

        @Test
        @DisplayName("should fail when deterministic agent has systemPrompt")
        void shouldFailDeterministicWithSystemPrompt() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("det-agent")
                    .version("1.0.0")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .systemPrompt("Should not be here")
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(def); // GH-90000
            assertThat(result.isValid()).isFalse(); // GH-90000
            assertThat(result.errors()).anyMatch(e -> e.contains("[semantic]") && e.contains("DETERMINISTIC"));
        }

        @Test
        @DisplayName("should fail for excessive cost")
        void shouldFailForExcessiveCost() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("expensive")
                    .version("1.0.0")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .subtype("llm")
                    .systemPrompt("Costly agent")
                    .maxCostPerCall(50.0) // GH-90000
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(def); // GH-90000
            assertThat(result.isValid()).isFalse(); // GH-90000
            assertThat(result.errors()).anyMatch(e -> e.contains("[cost]") && e.contains("maxCostPerCall"));
        }

        @Test
        @DisplayName("should fail for sensitive capabilities without security review")
        void shouldFailForUnreviewedSensitiveCapabilities() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("dangerous")
                    .version("1.0.0")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .subtype("llm")
                    .systemPrompt("Code executor")
                    .addCapability("execute-code")
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(def); // GH-90000
            assertThat(result.isValid()).isFalse(); // GH-90000
            assertThat(result.errors()).anyMatch(e -> // GH-90000
                    e.contains("[security]") && e.contains("execute-code"));
        }

        @Test
        @DisplayName("should pass for reviewed sensitive capabilities")
        void shouldPassForReviewedSensitiveCapabilities() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("safe-executor")
                    .version("1.0.0")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .subtype("llm")
                    .systemPrompt("Code executor")
                    .addCapability("execute-code")
                    .label("security.reviewed", "true") // GH-90000
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(def); // GH-90000
            assertThat(result.isValid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should validate AgentInstance with definition + overrides")
        void shouldValidateInstanceWithOverrides() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("test")
                    .version("1.0.0")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .subtype("llm")
                    .systemPrompt("test")
                    .build(); // GH-90000

            AgentInstance inst = AgentInstance.builder() // GH-90000
                    .tenantId("acme")
                    .definition(def) // GH-90000
                    .overrides(AgentInstance.Overrides.builder() // GH-90000
                            .maxTokens(200_000)  // exceeds limit // GH-90000
                            .build()) // GH-90000
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(inst); // GH-90000
            assertThat(result.isValid()).isFalse(); // GH-90000
            assertThat(result.errors()).anyMatch(e -> e.contains("maxTokens") && e.contains("exceeds"));
        }

        @Test
        @DisplayName("throwIfInvalid should throw for invalid config")
        void shouldThrowIfInvalid() { // GH-90000
            AgentDefinition def = AgentDefinition.builder() // GH-90000
                    .id("bad")
                    .version("1.0.0")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .temperature(5.0) // out of range // GH-90000
                    .build(); // GH-90000

            ValidationResult result = AgentDefinitionValidator.validate(def); // GH-90000
            assertThatThrownBy(result::throwIfInvalid) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("validation failed");
        }
    }
}
