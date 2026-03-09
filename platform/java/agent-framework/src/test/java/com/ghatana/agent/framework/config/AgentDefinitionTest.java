/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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
        void shouldBuildWithRequiredFields() {
            AgentDefinition def = AgentDefinition.builder()
                    .id("test-agent")
                    .version("1.0.0")
                    .type(AgentType.LLM)
                    .systemPrompt("You are a test agent.")
                    .build();

            assertThat(def.getId()).isEqualTo("test-agent");
            assertThat(def.getVersion()).isEqualTo("1.0.0");
            assertThat(def.getType()).isEqualTo(AgentType.LLM);
            assertThat(def.getName()).isEqualTo("test-agent"); // defaults to id
            assertThat(def.getCanonicalId()).isEqualTo("test-agent:1.0.0");
        }

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() {
            AgentDefinition def = AgentDefinition.builder()
                    .id("fraud-detector")
                    .version("2.1.0")
                    .name("Fraud Detector")
                    .description("Detects fraudulent transactions")
                    .type(AgentType.HYBRID)
                    .subtype("RULE_LLM")
                    .determinism(DeterminismGuarantee.CONFIG_SCOPED)
                    .stateMutability(StateMutability.LOCAL_STATE)
                    .failureMode(FailureMode.CIRCUIT_BREAKER)
                    .systemPrompt("You are a fraud detection agent.")
                    .maxTokens(8192)
                    .temperature(0.3)
                    .inputContract(new IOContract("TransactionEvent", "JSON", null))
                    .outputContract(new IOContract("FraudAssessment", "JSON", null))
                    .addTool(new ToolDeclaration("lookupTransaction", "Look up transaction",
                            Map.of("txId", ParameterSchema.requiredString("Transaction ID"))))
                    .addCapability("fraud-detection")
                    .addCapability("risk-scoring")
                    .timeout(Duration.ofSeconds(10))
                    .maxCostPerCall(0.05)
                    .maxRetries(5)
                    .label("team", "security")
                    .metadata("priority", "high")
                    .build();

            assertThat(def.getName()).isEqualTo("Fraud Detector");
            assertThat(def.getCanonicalId()).isEqualTo("fraud-detector:2.1.0");
            assertThat(def.getType()).isEqualTo(AgentType.HYBRID);
            assertThat(def.getTools()).hasSize(1);
            assertThat(def.getCapabilities()).containsExactlyInAnyOrder("fraud-detection", "risk-scoring");
            assertThat(def.getLabels()).containsEntry("team", "security");
            assertThat(def.getTimeout()).isEqualTo(Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("should be immutable — collections are unmodifiable")
        void shouldBeImmutable() {
            AgentDefinition def = AgentDefinition.builder()
                    .id("test")
                    .version("1.0.0")
                    .type(AgentType.DETERMINISTIC)
                    .label("k", "v")
                    .build();

            assertThatThrownBy(() -> def.getLabels().put("new", "value"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> def.getTools().add(
                    new ToolDeclaration("x", "x", Map.of())))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> def.getCapabilities().add("new"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should use equals/hashCode based on id+version")
        void shouldUseEqualsOnIdVersion() {
            AgentDefinition a = AgentDefinition.builder()
                    .id("agent-a").version("1.0.0").type(AgentType.LLM)
                    .systemPrompt("A").build();
            AgentDefinition b = AgentDefinition.builder()
                    .id("agent-a").version("1.0.0").type(AgentType.DETERMINISTIC)
                    .build();

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentInstance Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentInstance")
    class AgentInstanceTests {

        private AgentDefinition baseDefinition() {
            return AgentDefinition.builder()
                    .id("test-agent")
                    .version("1.0.0")
                    .type(AgentType.LLM)
                    .systemPrompt("You are a test agent.")
                    .timeout(Duration.ofSeconds(30))
                    .maxTokens(4096)
                    .temperature(0.7)
                    .maxCostPerCall(0.10)
                    .failureMode(FailureMode.FAIL_FAST)
                    .label("env", "prod")
                    .build();
        }

        @Test
        @DisplayName("should auto-generate instanceId from tenant+definition")
        void shouldAutoGenerateInstanceId() {
            AgentInstance inst = AgentInstance.builder()
                    .tenantId("acme")
                    .definition(baseDefinition())
                    .build();

            assertThat(inst.getInstanceId()).isEqualTo("acme:test-agent:1.0.0");
            assertThat(inst.getTenantId()).isEqualTo("acme");
        }

        @Test
        @DisplayName("should fall back to definition values when no overrides")
        void shouldFallBackToDefinitionDefaults() {
            AgentInstance inst = AgentInstance.builder()
                    .tenantId("acme")
                    .definition(baseDefinition())
                    .build();

            assertThat(inst.getEffectiveTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(inst.getEffectiveTemperature()).isEqualTo(0.7);
            assertThat(inst.getEffectiveMaxTokens()).isEqualTo(4096);
            assertThat(inst.getEffectiveFailureMode()).isEqualTo(FailureMode.FAIL_FAST);
        }

        @Test
        @DisplayName("should apply overrides over definition defaults")
        void shouldApplyOverrides() {
            AgentInstance inst = AgentInstance.builder()
                    .tenantId("globex")
                    .definition(baseDefinition())
                    .overrides(AgentInstance.Overrides.builder()
                            .model("gpt-4o")
                            .timeout(Duration.ofSeconds(60))
                            .temperature(0.1)
                            .maxTokens(8192)
                            .failureMode(FailureMode.CIRCUIT_BREAKER)
                            .rateLimitPerSecond(100)
                            .label("env", "staging")
                            .featureFlag("beta-tools", true)
                            .build()
                    )
                    .build();

            assertThat(inst.getEffectiveModel()).isEqualTo("gpt-4o");
            assertThat(inst.getEffectiveTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(inst.getEffectiveTemperature()).isEqualTo(0.1);
            assertThat(inst.getEffectiveMaxTokens()).isEqualTo(8192);
            assertThat(inst.getEffectiveFailureMode()).isEqualTo(FailureMode.CIRCUIT_BREAKER);
            assertThat(inst.getEffectiveRateLimit()).isEqualTo(100);
            assertThat(inst.isFeatureEnabled("beta-tools")).isTrue();
            // Label override replaces definition's "env"
            assertThat(inst.getEffectiveLabels()).containsEntry("env", "staging");
        }

        @Test
        @DisplayName("should support hot reload via applyOverrides")
        void shouldSupportHotReload() {
            AgentInstance inst = AgentInstance.builder()
                    .tenantId("acme")
                    .definition(baseDefinition())
                    .build();

            assertThat(inst.getEffectiveModel()).isNull();

            // Hot reload: switch to a different model
            inst.applyOverrides(AgentInstance.Overrides.builder()
                    .model("claude-3-opus")
                    .build()
            );

            assertThat(inst.getEffectiveModel()).isEqualTo("claude-3-opus");
            assertThat(inst.getLastUpdatedAt()).isAfter(inst.getCreatedAt());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validator Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentDefinitionValidator")
    class ValidatorTests {

        @Test
        @DisplayName("should pass for a well-formed LLM definition")
        void shouldPassForValidLlmDefinition() {
            AgentDefinition def = AgentDefinition.builder()
                    .id("test-agent")
                    .version("1.0.0")
                    .type(AgentType.LLM)
                    .systemPrompt("You are helpful.")
                    .build();

            ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should fail when LLM agent has no systemPrompt")
        void shouldFailLlmWithoutSystemPrompt() {
            AgentDefinition def = AgentDefinition.builder()
                    .id("test-agent")
                    .version("1.0.0")
                    .type(AgentType.LLM)
                    .build();

            ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("[semantic]") && e.contains("systemPrompt"));
        }

        @Test
        @DisplayName("should fail when deterministic agent has systemPrompt")
        void shouldFailDeterministicWithSystemPrompt() {
            AgentDefinition def = AgentDefinition.builder()
                    .id("det-agent")
                    .version("1.0.0")
                    .type(AgentType.DETERMINISTIC)
                    .systemPrompt("Should not be here")
                    .build();

            ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("[semantic]") && e.contains("DETERMINISTIC"));
        }

        @Test
        @DisplayName("should fail for excessive cost")
        void shouldFailForExcessiveCost() {
            AgentDefinition def = AgentDefinition.builder()
                    .id("expensive")
                    .version("1.0.0")
                    .type(AgentType.LLM)
                    .systemPrompt("Costly agent")
                    .maxCostPerCall(50.0)
                    .build();

            ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("[cost]") && e.contains("maxCostPerCall"));
        }

        @Test
        @DisplayName("should fail for sensitive capabilities without security review")
        void shouldFailForUnreviewedSensitiveCapabilities() {
            AgentDefinition def = AgentDefinition.builder()
                    .id("dangerous")
                    .version("1.0.0")
                    .type(AgentType.LLM)
                    .systemPrompt("Code executor")
                    .addCapability("execute-code")
                    .build();

            ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e ->
                    e.contains("[security]") && e.contains("execute-code"));
        }

        @Test
        @DisplayName("should pass for reviewed sensitive capabilities")
        void shouldPassForReviewedSensitiveCapabilities() {
            AgentDefinition def = AgentDefinition.builder()
                    .id("safe-executor")
                    .version("1.0.0")
                    .type(AgentType.LLM)
                    .systemPrompt("Code executor")
                    .addCapability("execute-code")
                    .label("security.reviewed", "true")
                    .build();

            ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should validate AgentInstance with definition + overrides")
        void shouldValidateInstanceWithOverrides() {
            AgentDefinition def = AgentDefinition.builder()
                    .id("test")
                    .version("1.0.0")
                    .type(AgentType.LLM)
                    .systemPrompt("test")
                    .build();

            AgentInstance inst = AgentInstance.builder()
                    .tenantId("acme")
                    .definition(def)
                    .overrides(AgentInstance.Overrides.builder()
                            .maxTokens(200_000)  // exceeds limit
                            .build())
                    .build();

            ValidationResult result = AgentDefinitionValidator.validate(inst);
            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("maxTokens") && e.contains("exceeds"));
        }

        @Test
        @DisplayName("throwIfInvalid should throw for invalid config")
        void shouldThrowIfInvalid() {
            AgentDefinition def = AgentDefinition.builder()
                    .id("bad")
                    .version("1.0.0")
                    .type(AgentType.LLM)
                    .temperature(5.0) // out of range
                    .build();

            ValidationResult result = AgentDefinitionValidator.validate(def);
            assertThatThrownBy(result::throwIfInvalid)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("validation failed");
        }
    }
}
