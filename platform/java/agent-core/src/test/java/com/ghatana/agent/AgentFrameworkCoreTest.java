/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */

package com.ghatana.agent;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import com.ghatana.platform.health.HealthStatus;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Comprehensive tests for the consolidated agent framework types.
 * Covers all enums, value objects, interfaces, and base classes
 * in the unified {@code com.ghatana.agent} package.
 */
@DisplayName("Consolidated Agent Framework Core")
class AgentFrameworkCoreTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentType Enum
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentType")
    class AgentTypeTest {

        @Test
        @DisplayName("has all 9 canonical agent types")
        void shouldHaveAll9Types() { // GH-90000
            assertThat(AgentType.values()).hasSize(9); // GH-90000
            assertThat(AgentType.values()).containsExactly( // GH-90000
                    AgentType.DETERMINISTIC,
                    AgentType.PROBABILISTIC,
                    AgentType.STREAM_PROCESSOR,
                    AgentType.PLANNING,
                    AgentType.HYBRID,
                    AgentType.ADAPTIVE,
                    AgentType.COMPOSITE,
                    AgentType.REACTIVE,
                    AgentType.CUSTOM
            );
        }

        @Test
        @DisplayName("valueOf round-trips correctly")
        void shouldRoundTrip() { // GH-90000
            for (AgentType type : AgentType.values()) { // GH-90000
                assertThat(AgentType.valueOf(type.name())).isSameAs(type); // GH-90000
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DeterminismGuarantee Enum
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DeterminismGuarantee")
    class DeterminismGuaranteeTest {

        @Test
        @DisplayName("has 4 values")
        void shouldHave4Values() { // GH-90000
            assertThat(DeterminismGuarantee.values()).hasSize(4); // GH-90000
            assertThat(DeterminismGuarantee.values()).containsExactly( // GH-90000
                    DeterminismGuarantee.FULL,
                    DeterminismGuarantee.CONFIG_SCOPED,
                    DeterminismGuarantee.NONE,
                    DeterminismGuarantee.EVENTUAL
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // StateMutability Enum
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("StateMutability")
    class StateMutabilityTest {

        @Test
        @DisplayName("has 4 values")
        void shouldHave4Values() { // GH-90000
            assertThat(StateMutability.values()).containsExactly( // GH-90000
                    StateMutability.STATELESS,
                    StateMutability.LOCAL_STATE,
                    StateMutability.EXTERNAL_STATE,
                    StateMutability.HYBRID_STATE
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FailureMode Enum
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FailureMode")
    class FailureModeTest {

        @Test
        @DisplayName("has 6 values")
        void shouldHave6Values() { // GH-90000
            assertThat(FailureMode.values()).hasSize(6); // GH-90000
            assertThat(FailureMode.values()).containsExactly( // GH-90000
                    FailureMode.FAIL_FAST,
                    FailureMode.RETRY,
                    FailureMode.FALLBACK,
                    FailureMode.SKIP,
                    FailureMode.DEAD_LETTER,
                    FailureMode.CIRCUIT_BREAKER
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HealthStatus Enum
    // ═══════════════════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentResultStatus Enum
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentResultStatus")
    class AgentResultStatusTest {

        @Test
        @DisplayName("has 11 values")
        void shouldHave7Values() { // GH-90000
            assertThat(AgentResultStatus.values()).hasSize(11); // GH-90000
            assertThat(AgentResultStatus.values()).containsExactly( // GH-90000
                    AgentResultStatus.SUCCESS,
                    AgentResultStatus.LOW_CONFIDENCE,
                    AgentResultStatus.SKIPPED,
                    AgentResultStatus.FAILED,
                    AgentResultStatus.TIMEOUT,
                    AgentResultStatus.DEGRADED,
                    AgentResultStatus.DELEGATED,
                    AgentResultStatus.PENDING_APPROVAL,
                    AgentResultStatus.DENIED,
                    AgentResultStatus.CANCELLED,
                    AgentResultStatus.ROLLED_BACK
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentDescriptor
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentDescriptor")
    class AgentDescriptorTest {

        @Test
        @DisplayName("builder with all fields")
        void shouldBuildWithAllFields() { // GH-90000
            AgentDescriptor desc = AgentDescriptor.builder() // GH-90000
                    .agentId("fraud-v2")
                    .name("Fraud Detector")
                    .version("2.1.0")
                    .description("Real-time fraud detection")
                    .namespace("security")
                    .type(AgentType.HYBRID) // GH-90000
                    .subtype("ML_FALLBACK")
                    .determinism(DeterminismGuarantee.CONFIG_SCOPED) // GH-90000
                    .latencySla(Duration.ofMillis(50)) // GH-90000
                    .throughputTarget(5000) // GH-90000
                    .stateMutability(StateMutability.LOCAL_STATE) // GH-90000
                    .failureMode(FailureMode.CIRCUIT_BREAKER) // GH-90000
                    .capabilities(Set.of("fraud-detection", "risk-scoring")) // GH-90000
                    .inputEventTypes(Set.of("Transaction"))
                    .outputEventTypes(Set.of("FraudAlert"))
                    .metadata(Map.of("model", "xgboost-v3")) // GH-90000
                    .labels(Map.of("team", "fraud-prevention")) // GH-90000
                    .annotations(Map.of("doc-url", "http://docs/fraud")) // GH-90000
                    .build(); // GH-90000

            assertThat(desc.getAgentId()).isEqualTo("fraud-v2");
            assertThat(desc.getName()).isEqualTo("Fraud Detector");
            assertThat(desc.getVersion()).isEqualTo("2.1.0");
            assertThat(desc.getDescription()).isEqualTo("Real-time fraud detection");
            assertThat(desc.getNamespace()).isEqualTo("security");
            assertThat(desc.getType()).isEqualTo(AgentType.HYBRID); // GH-90000
            assertThat(desc.getSubtype()).isEqualTo("ML_FALLBACK");
            assertThat(desc.getDeterminism()).isEqualTo(DeterminismGuarantee.CONFIG_SCOPED); // GH-90000
            assertThat(desc.getLatencySla()).isEqualTo(Duration.ofMillis(50)); // GH-90000
            assertThat(desc.getThroughputTarget()).isEqualTo(5000); // GH-90000
            assertThat(desc.getStateMutability()).isEqualTo(StateMutability.LOCAL_STATE); // GH-90000
            assertThat(desc.getFailureMode()).isEqualTo(FailureMode.CIRCUIT_BREAKER); // GH-90000
            assertThat(desc.getCapabilities()).containsExactlyInAnyOrder("fraud-detection", "risk-scoring"); // GH-90000
            assertThat(desc.getInputEventTypes()).containsExactly("Transaction");
            assertThat(desc.getOutputEventTypes()).containsExactly("FraudAlert");
            assertThat(desc.getMetadata()).containsEntry("model", "xgboost-v3"); // GH-90000
            assertThat(desc.getLabels()).containsEntry("team", "fraud-prevention"); // GH-90000
        }

        @Test
        @DisplayName("defaults are sensible")
        void shouldHaveSensibleDefaults() { // GH-90000
            AgentDescriptor desc = AgentDescriptor.builder() // GH-90000
                    .agentId("simple")
                    .name("Simple Agent")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .build(); // GH-90000

            assertThat(desc.getVersion()).isEqualTo("1.0.0");
            assertThat(desc.getNamespace()).isEqualTo("default");
            assertThat(desc.getDeterminism()).isEqualTo(DeterminismGuarantee.NONE); // GH-90000
            assertThat(desc.getLatencySla()).isEqualTo(Duration.ofSeconds(5)); // GH-90000
            assertThat(desc.getThroughputTarget()).isEqualTo(1000); // GH-90000
            assertThat(desc.getStateMutability()).isEqualTo(StateMutability.STATELESS); // GH-90000
            assertThat(desc.getFailureMode()).isEqualTo(FailureMode.FAIL_FAST); // GH-90000
            assertThat(desc.getCapabilities()).isEmpty(); // GH-90000
            assertThat(desc.getInputEventTypes()).isEmpty(); // GH-90000
            assertThat(desc.getOutputEventTypes()).isEmpty(); // GH-90000
            assertThat(desc.getMetadata()).isEmpty(); // GH-90000
            assertThat(desc.getLabels()).isEmpty(); // GH-90000
            assertThat(desc.getAnnotations()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("isDeterministic — FULL returns true")
        void shouldDetectFullDeterminism() { // GH-90000
            AgentDescriptor desc = AgentDescriptor.builder() // GH-90000
                    .agentId("det")
                    .name("Det")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .determinism(DeterminismGuarantee.FULL) // GH-90000
                    .build(); // GH-90000
            assertThat(desc.isDeterministic()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isDeterministic — CONFIG_SCOPED returns true")
        void shouldDetectConfigScopedDeterminism() { // GH-90000
            AgentDescriptor desc = AgentDescriptor.builder() // GH-90000
                    .agentId("cs")
                    .name("ConfigScoped")
                    .type(AgentType.HYBRID) // GH-90000
                    .determinism(DeterminismGuarantee.CONFIG_SCOPED) // GH-90000
                    .build(); // GH-90000
            assertThat(desc.isDeterministic()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isDeterministic — NONE returns false")
        void shouldDetectNonDeterminism() { // GH-90000
            AgentDescriptor desc = AgentDescriptor.builder() // GH-90000
                    .agentId("prob")
                    .name("Prob")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .determinism(DeterminismGuarantee.NONE) // GH-90000
                    .build(); // GH-90000
            assertThat(desc.isDeterministic()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isStateless")
        void shouldDetectStateless() { // GH-90000
            AgentDescriptor desc = AgentDescriptor.builder() // GH-90000
                    .agentId("sl")
                    .name("Stateless")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .stateMutability(StateMutability.STATELESS) // GH-90000
                    .build(); // GH-90000
            assertThat(desc.isStateless()).isTrue(); // GH-90000

            AgentDescriptor stateful = desc.toBuilder() // GH-90000
                    .stateMutability(StateMutability.LOCAL_STATE) // GH-90000
                    .build(); // GH-90000
            assertThat(stateful.isStateless()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("hasCapability")
        void shouldDetectCapability() { // GH-90000
            AgentDescriptor desc = AgentDescriptor.builder() // GH-90000
                    .agentId("cap")
                    .name("Cap")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .capabilities(Set.of("fraud", "risk")) // GH-90000
                    .build(); // GH-90000
            assertThat(desc.hasCapability("fraud")).isTrue();
            assertThat(desc.hasCapability("enrichment")).isFalse();
        }

        @Test
        @DisplayName("toBuilder creates independent copy")
        void shouldCopyWithToBuilder() { // GH-90000
            AgentDescriptor original = AgentDescriptor.builder() // GH-90000
                    .agentId("orig")
                    .name("Original")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .build(); // GH-90000

            AgentDescriptor copy = original.toBuilder() // GH-90000
                    .agentId("copy")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .build(); // GH-90000

            assertThat(original.getAgentId()).isEqualTo("orig");
            assertThat(copy.getAgentId()).isEqualTo("copy");
            assertThat(copy.getType()).isEqualTo(AgentType.PROBABILISTIC); // GH-90000
            assertThat(original.getType()).isEqualTo(AgentType.DETERMINISTIC); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentResult
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentResult")
    class AgentResultTest {

        @Test
        @DisplayName("success factory method")
        void shouldCreateSuccessResult() { // GH-90000
            AgentResult<String> result = AgentResult.success("output", "agent-1", Duration.ofMillis(42)); // GH-90000
            assertThat(result.getOutput()).isEqualTo("output");
            assertThat(result.getConfidence()).isEqualTo(1.0); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
            assertThat(result.getAgentId()).isEqualTo("agent-1");
            assertThat(result.getProcessingTime()).isEqualTo(Duration.ofMillis(42)); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.isFailed()).isFalse(); // GH-90000
            assertThat(result.meetsConfidence(0.5)).isTrue(); // GH-90000
            assertThat(result.meetsConfidence(1.0)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("successWithConfidence factory — above threshold")
        void shouldCreateHighConfidenceResult() { // GH-90000
            AgentResult<Integer> result = AgentResult.successWithConfidence( // GH-90000
                    42, 0.85, "ml-agent", Duration.ofMillis(100), "Model v3 inference"); // GH-90000
            assertThat(result.getOutput()).isEqualTo(42); // GH-90000
            assertThat(result.getConfidence()).isEqualTo(0.85); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
            assertThat(result.getExplanation()).isEqualTo("Model v3 inference");
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.meetsConfidence(0.8)).isTrue(); // GH-90000
            assertThat(result.meetsConfidence(0.9)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("successWithConfidence factory — below threshold marks LOW_CONFIDENCE")
        void shouldMarkLowConfidence() { // GH-90000
            AgentResult<String> result = AgentResult.successWithConfidence( // GH-90000
                    "uncertain", 0.3, "low-conf", Duration.ofMillis(50), "Weak signal"); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.LOW_CONFIDENCE); // GH-90000
            assertThat(result.getConfidence()).isEqualTo(0.3); // GH-90000
            assertThat(result.isSuccess()).isFalse(); // LOW_CONFIDENCE != SUCCESS // GH-90000
        }

        @Test
        @DisplayName("failure factory method")
        void shouldCreateFailureResult() { // GH-90000
            RuntimeException error = new RuntimeException("NullPointerException");
            AgentResult<Void> result = AgentResult.failure(error, "agent-2", Duration.ofMillis(10)); // GH-90000
            assertThat(result.getOutput()).isNull(); // GH-90000
            assertThat(result.getConfidence()).isEqualTo(0.0); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED); // GH-90000
            assertThat(result.isFailed()).isTrue(); // GH-90000
            assertThat(result.isSuccess()).isFalse(); // GH-90000
            assertThat(result.getExplanation()).contains("RuntimeException");
            assertThat(result.getExplanation()).contains("NullPointerException");
        }

        @Test
        @DisplayName("failure factory rejects null error")
        void shouldRejectNullError() { // GH-90000
            assertThatThrownBy(() -> AgentResult.failure(null, "a", Duration.ZERO)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("timeout factory method")
        void shouldCreateTimeoutResult() { // GH-90000
            AgentResult<Void> result = AgentResult.timeout("slow-agent", Duration.ofSeconds(5)); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.TIMEOUT); // GH-90000
            assertThat(result.isFailed()).isTrue(); // GH-90000
            assertThat(result.getExplanation()).contains("5000ms");
        }

        @Test
        @DisplayName("skipped factory method")
        void shouldCreateSkippedResult() { // GH-90000
            AgentResult<Void> result = AgentResult.skipped("Input doesn't match", "filter-agent"); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000
            assertThat(result.getExplanation()).isEqualTo("Input doesn't match");
            assertThat(result.getProcessingTime()).isEqualTo(Duration.ZERO); // GH-90000
        }

        @Test
        @DisplayName("delegated factory method")
        void shouldCreateDelegatedResult() { // GH-90000
            AgentResult<Void> result = AgentResult.delegated("ml-agent-v2", "router-agent"); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DELEGATED); // GH-90000
            assertThat(result.getAgentId()).isEqualTo("router-agent");
            assertThat(result.getExplanation()).contains("ml-agent-v2");
            assertThat(result.getMetrics()).containsEntry("delegateAgentId", "ml-agent-v2"); // GH-90000
        }

        @Test
        @DisplayName("toBuilder creates modifiable copy")
        void shouldSupportToBuilder() { // GH-90000
            AgentResult<String> original = AgentResult.success("out", "a1", Duration.ofMillis(1)); // GH-90000
            AgentResult<String> modified = original.toBuilder() // GH-90000
                    .confidence(0.5) // GH-90000
                    .explanation("Overridden")
                    .build(); // GH-90000
            assertThat(original.getConfidence()).isEqualTo(1.0); // GH-90000
            assertThat(modified.getConfidence()).isEqualTo(0.5); // GH-90000
            assertThat(modified.getExplanation()).isEqualTo("Overridden");
        }

        @Test
        @DisplayName("default builder produces SUCCESS with confidence 1.0")
        void shouldDefaultToSuccess() { // GH-90000
            AgentResult<String> result = AgentResult.<String>builder() // GH-90000
                    .output("hello")
                    .agentId("test")
                    .build(); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
            assertThat(result.getConfidence()).isEqualTo(1.0); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentConfig
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentConfig")
    class AgentConfigTest {

        @Test
        @DisplayName("builder with all fields")
        void shouldBuildWithAllFields() { // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("config-test")
                    .type(AgentType.HYBRID) // GH-90000
                    .version("2.0.0")
                    .timeout(Duration.ofMillis(500)) // GH-90000
                    .confidenceThreshold(0.7) // GH-90000
                    .maxRetries(3) // GH-90000
                    .retryBackoff(Duration.ofMillis(200)) // GH-90000
                    .maxRetryBackoff(Duration.ofSeconds(10)) // GH-90000
                    .failureMode(FailureMode.RETRY) // GH-90000
                    .circuitBreakerThreshold(10) // GH-90000
                    .circuitBreakerReset(Duration.ofSeconds(60)) // GH-90000
                    .metricsEnabled(false) // GH-90000
                    .tracingEnabled(false) // GH-90000
                    .tracingSampleRate(0.5) // GH-90000
                    .properties(Map.of("key", "val")) // GH-90000
                    .labels(Map.of("env", "test")) // GH-90000
                    .requiredCapabilities(Set.of("gpu"))
                    .build(); // GH-90000

            assertThat(config.getAgentId()).isEqualTo("config-test");
            assertThat(config.getType()).isEqualTo(AgentType.HYBRID); // GH-90000
            assertThat(config.getVersion()).isEqualTo("2.0.0");
            assertThat(config.getTimeout()).isEqualTo(Duration.ofMillis(500)); // GH-90000
            assertThat(config.getConfidenceThreshold()).isEqualTo(0.7); // GH-90000
            assertThat(config.getMaxRetries()).isEqualTo(3); // GH-90000
            assertThat(config.getRetryBackoff()).isEqualTo(Duration.ofMillis(200)); // GH-90000
            assertThat(config.getMaxRetryBackoff()).isEqualTo(Duration.ofSeconds(10)); // GH-90000
            assertThat(config.getFailureMode()).isEqualTo(FailureMode.RETRY); // GH-90000
            assertThat(config.getCircuitBreakerThreshold()).isEqualTo(10); // GH-90000
            assertThat(config.getCircuitBreakerReset()).isEqualTo(Duration.ofSeconds(60)); // GH-90000
            assertThat(config.isMetricsEnabled()).isFalse(); // GH-90000
            assertThat(config.isTracingEnabled()).isFalse(); // GH-90000
            assertThat(config.getTracingSampleRate()).isEqualTo(0.5); // GH-90000
            assertThat(config.getProperties()).containsEntry("key", "val"); // GH-90000
            assertThat(config.getLabels()).containsEntry("env", "test"); // GH-90000
            assertThat(config.getRequiredCapabilities()).containsExactly("gpu");
        }

        @Test
        @DisplayName("defaults are sensible")
        void shouldHaveSensibleDefaults() { // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("default-test")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getVersion()).isEqualTo("1.0.0");
            assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(5)); // GH-90000
            assertThat(config.getConfidenceThreshold()).isEqualTo(0.5); // GH-90000
            assertThat(config.getMaxRetries()).isEqualTo(0); // GH-90000
            assertThat(config.getRetryBackoff()).isEqualTo(Duration.ofMillis(100)); // GH-90000
            assertThat(config.getMaxRetryBackoff()).isEqualTo(Duration.ofSeconds(5)); // GH-90000
            assertThat(config.getFailureMode()).isEqualTo(FailureMode.FAIL_FAST); // GH-90000
            assertThat(config.getCircuitBreakerThreshold()).isEqualTo(5); // GH-90000
            assertThat(config.getCircuitBreakerReset()).isEqualTo(Duration.ofSeconds(30)); // GH-90000
            assertThat(config.isMetricsEnabled()).isTrue(); // GH-90000
            assertThat(config.isTracingEnabled()).isTrue(); // GH-90000
            assertThat(config.getTracingSampleRate()).isEqualTo(0.1); // GH-90000
            assertThat(config.getProperties()).isEmpty(); // GH-90000
            assertThat(config.getLabels()).isEmpty(); // GH-90000
            assertThat(config.getRequiredCapabilities()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("toBuilder creates independent copy")
        void shouldSupportToBuilder() { // GH-90000
            AgentConfig original = AgentConfig.builder() // GH-90000
                    .agentId("orig")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .build(); // GH-90000
            AgentConfig copy = original.toBuilder() // GH-90000
                    .timeout(Duration.ofMillis(100)) // GH-90000
                    .maxRetries(5) // GH-90000
                    .build(); // GH-90000
            assertThat(original.getTimeout()).isEqualTo(Duration.ofSeconds(5)); // GH-90000
            assertThat(copy.getTimeout()).isEqualTo(Duration.ofMillis(100)); // GH-90000
            assertThat(copy.getMaxRetries()).isEqualTo(5); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Enhanced AgentContext (framework.api — v2.0 additions) // GH-90000
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentContext v2.0 Enhancements")
    class AgentContextV2Test {

        private MemoryStore memoryStore;

        @BeforeEach
        void setup() { // GH-90000
            memoryStore = mock(MemoryStore.class); // GH-90000
        }

        @Test
        @DisplayName("traceId is stored and retrievable")
        void shouldStoreTraceId() { // GH-90000
            AgentContext ctx = AgentContext.builder() // GH-90000
                    .agentId("agent-1")
                    .tenantId("tenant-1")
                    .memoryStore(memoryStore) // GH-90000
                    .traceId("trace-abc-123")
                    .build(); // GH-90000
            assertThat(ctx.getTraceId()).isEqualTo("trace-abc-123");
        }

        @Test
        @DisplayName("traceId defaults to null")
        void shouldDefaultTraceIdToNull() { // GH-90000
            AgentContext ctx = AgentContext.builder() // GH-90000
                    .agentId("agent-1")
                    .tenantId("tenant-1")
                    .memoryStore(memoryStore) // GH-90000
                    .build(); // GH-90000
            assertThat(ctx.getTraceId()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("metadata get/set works")
        void shouldSupportMetadata() { // GH-90000
            AgentContext ctx = AgentContext.builder() // GH-90000
                    .agentId("agent-1")
                    .tenantId("tenant-1")
                    .memoryStore(memoryStore) // GH-90000
                    .build(); // GH-90000

            ctx.setMetadata("pipeline.stage", "enrichment"); // GH-90000
            ctx.setMetadata("count", 42); // GH-90000
            assertThat(ctx.getMetadata()).containsEntry("pipeline.stage", "enrichment"); // GH-90000
            assertThat(ctx.getMetadata()).containsEntry("count", 42); // GH-90000
        }

        @Test
        @DisplayName("metadata initializes from builder")
        void shouldInitMetadataFromBuilder() { // GH-90000
            AgentContext ctx = AgentContext.builder() // GH-90000
                    .agentId("agent-1")
                    .tenantId("tenant-1")
                    .memoryStore(memoryStore) // GH-90000
                    .metadata(Map.of("key", "value")) // GH-90000
                    .build(); // GH-90000

            assertThat(ctx.getMetadata()).containsEntry("key", "value"); // GH-90000
        }

        @Test
        @DisplayName("deriveChild creates new context with child agent ID")
        void shouldDeriveChild() { // GH-90000
            AgentContext parent = AgentContext.builder() // GH-90000
                    .agentId("parent-agent")
                    .tenantId("tenant-1")
                    .memoryStore(memoryStore) // GH-90000
                    .traceId("trace-xyz")
                    .build(); // GH-90000

            AgentContext child = parent.deriveChild("child-agent");

            assertThat(child.getAgentId()).isEqualTo("child-agent");
            assertThat(child.getTenantId()).isEqualTo("tenant-1");
            assertThat(child.getTraceId()).isEqualTo("trace-xyz");
            assertThat(child.getTurnId()).isNotEqualTo(parent.getTurnId()); // New turn // GH-90000
        }

        @Test
        @DisplayName("deriveChild preserves config")
        void shouldPreserveConfigInChild() { // GH-90000
            AgentContext parent = AgentContext.builder() // GH-90000
                    .agentId("parent-agent")
                    .tenantId("tenant-1")
                    .memoryStore(memoryStore) // GH-90000
                    .config(Map.of("key", "value")) // GH-90000
                    .build(); // GH-90000

            AgentContext child = parent.deriveChild("child-agent");
            assertThat(child.getAllConfig()).containsEntry("key", "value"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TypedAgent Interface + AbstractTypedAgent
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TypedAgent / AbstractTypedAgent")
    class AbstractTypedAgentTest {

        private MemoryStore memoryStore;
        private AgentContext ctx;

        @BeforeEach
        void setup() { // GH-90000
            memoryStore = mock(MemoryStore.class); // GH-90000
            ctx = AgentContext.builder() // GH-90000
                    .agentId("ctx-agent")
                    .tenantId("test-tenant")
                    .memoryStore(memoryStore) // GH-90000
                    .build(); // GH-90000
        }

        // Test agent: doubles the input integer
        static class DoublerAgent extends AbstractTypedAgent<Integer, Integer> {
            @Override
            @NotNull
            public AgentDescriptor descriptor() { // GH-90000
                return AgentDescriptor.builder() // GH-90000
                        .agentId("doubler")
                        .name("Doubler")
                        .type(AgentType.DETERMINISTIC) // GH-90000
                        .determinism(DeterminismGuarantee.FULL) // GH-90000
                        .build(); // GH-90000
            }

            @Override
            @NotNull
            protected Promise<AgentResult<Integer>> doProcess(@NotNull AgentContext ctx, @NotNull Integer input) { // GH-90000
                return Promise.of(AgentResult.success(input * 2, "doubler", Duration.ZERO)); // GH-90000
            }
        }

        // Test agent: always fails
        static class FailingAgent extends AbstractTypedAgent<String, String> {
            @Override
            @NotNull
            public AgentDescriptor descriptor() { // GH-90000
                return AgentDescriptor.builder() // GH-90000
                        .agentId("failing")
                        .name("Failing")
                        .type(AgentType.DETERMINISTIC) // GH-90000
                        .build(); // GH-90000
            }

            @Override
            @NotNull
            protected Promise<AgentResult<String>> doProcess(@NotNull AgentContext ctx, @NotNull String input) { // GH-90000
                throw new RuntimeException("Boom!");
            }
        }

        // Test agent: fails asynchronously via failed Promise
        static class AsyncFailingAgent extends AbstractTypedAgent<String, String> {
            @Override
            @NotNull
            public AgentDescriptor descriptor() {
                return AgentDescriptor.builder()
                    .agentId("async-failing")
                    .name("AsyncFailing")
                    .type(AgentType.DETERMINISTIC)
                    .build();
            }

            @Override
            @NotNull
            protected Promise<AgentResult<String>> doProcess(@NotNull AgentContext ctx, @NotNull String input) {
                return Promise.ofException(new IllegalStateException("Async boom"));
            }
        }

        // Test agent: tracks initialize/shutdown calls
        static class LifecycleAgent extends AbstractTypedAgent<String, String> {
            boolean initialized = false;
            boolean shutDown = false;

            @Override
            @NotNull
            public AgentDescriptor descriptor() { // GH-90000
                return AgentDescriptor.builder() // GH-90000
                        .agentId("lifecycle")
                        .name("Lifecycle")
                        .type(AgentType.DETERMINISTIC) // GH-90000
                        .build(); // GH-90000
            }

            @Override
            @NotNull
            protected Promise<Void> doInitialize(@NotNull AgentConfig config) { // GH-90000
                initialized = true;
                return Promise.complete(); // GH-90000
            }

            @Override
            @NotNull
            protected Promise<AgentResult<String>> doProcess(@NotNull AgentContext ctx, @NotNull String input) { // GH-90000
                return Promise.of(AgentResult.success(input.toUpperCase(), "lifecycle", Duration.ZERO)); // GH-90000
            }

            @Override
            @NotNull
            protected Promise<Void> doShutdown() { // GH-90000
                shutDown = true;
                return Promise.complete(); // GH-90000
            }
        }

        static class LifecycleByTypeAgent extends AbstractTypedAgent<String, String> {
            private final AgentType type;
            private final String id;

            LifecycleByTypeAgent(AgentType type) {
                this.type = type;
                this.id = "lifecycle-" + type.name().toLowerCase();
            }

            @Override
            @NotNull
            public AgentDescriptor descriptor() {
                return AgentDescriptor.builder()
                    .agentId(id)
                    .name(id)
                    .type(type)
                    .build();
            }

            @Override
            @NotNull
            protected Promise<AgentResult<String>> doProcess(@NotNull AgentContext ctx, @NotNull String input) {
                return Promise.of(AgentResult.success(type.name() + ":" + input, id, Duration.ZERO));
            }
        }

        private <T> T runOnEventloop(java.util.function.Supplier<Promise<T>> promiseSupplier) { // GH-90000
            Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
            AtomicReference<T> ref = new AtomicReference<>(); // GH-90000
            AtomicReference<Exception> err = new AtomicReference<>(); // GH-90000
            eventloop.post(() -> promiseSupplier.get() // GH-90000
                    .whenResult(ref::set) // GH-90000
                    .whenException(err::set)); // GH-90000
            eventloop.run(); // GH-90000
            if (err.get() != null) { // GH-90000
                throw new RuntimeException(err.get()); // GH-90000
            }
            return ref.get(); // GH-90000
        }

        @Test
        @DisplayName("starts in CREATED state")
        void shouldStartInCreatedState() { // GH-90000
            DoublerAgent agent = new DoublerAgent(); // GH-90000
            assertThat(agent.getState()).isEqualTo(AbstractTypedAgent.State.CREATED); // GH-90000
        }

        @Test
        @DisplayName("descriptor available before initialize")
        void shouldProvideDescriptorBeforeInit() { // GH-90000
            DoublerAgent agent = new DoublerAgent(); // GH-90000
            assertThat(agent.descriptor().getAgentId()).isEqualTo("doubler");
            assertThat(agent.descriptor().getType()).isEqualTo(AgentType.DETERMINISTIC); // GH-90000
        }

        @Test
        @DisplayName("initialize transitions to READY")
        void shouldTransitionToReady() { // GH-90000
            LifecycleAgent agent = new LifecycleAgent(); // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("lifecycle").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            assertThat(agent.getState()).isEqualTo(AbstractTypedAgent.State.READY); // GH-90000
            assertThat(agent.initialized).isTrue(); // GH-90000
            assertThat(agent.getConfig()).isSameAs(config); // GH-90000
        }

        @Test
        @DisplayName("process works after initialize")
        void shouldProcessAfterInit() { // GH-90000
            DoublerAgent agent = new DoublerAgent(); // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("doubler").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            AgentResult<Integer> result = runOnEventloop(() -> agent.process(ctx, 21)); // GH-90000

            assertThat(result.getOutput()).isEqualTo(42); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getAgentId()).isEqualTo("doubler");
            assertThat(result.getProcessingTime()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("process fails when not initialized")
        void shouldFailWhenNotInitialized() { // GH-90000
            DoublerAgent agent = new DoublerAgent(); // GH-90000
            AgentResult<Integer> result = runOnEventloop(() -> agent.process(ctx, 21)); // GH-90000

            assertThat(result.isFailed()).isTrue(); // GH-90000
            assertThat(result.getExplanation()).contains("not ready");
        }

        @Test
        @DisplayName("process catches synchronous exceptions")
        void shouldCatchSyncExceptions() { // GH-90000
            FailingAgent agent = new FailingAgent(); // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("failing").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            AgentResult<String> result = runOnEventloop(() -> agent.process(ctx, "test")); // GH-90000

            assertThat(result.isFailed()).isTrue(); // GH-90000
            assertThat(result.getExplanation()).contains("Boom!");
        }

        @Test
        @DisplayName("shutdown transitions to STOPPED")
        void shouldTransitionToStopped() { // GH-90000
            LifecycleAgent agent = new LifecycleAgent(); // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("lifecycle").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            runOnEventloop(() -> agent.shutdown()); // GH-90000

            assertThat(agent.getState()).isEqualTo(AbstractTypedAgent.State.STOPPED); // GH-90000
            assertThat(agent.shutDown).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("shutdown is idempotent")
        void shouldBeIdempotentShutdown() { // GH-90000
            LifecycleAgent agent = new LifecycleAgent(); // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("lifecycle").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            runOnEventloop(() -> agent.shutdown()); // GH-90000
            runOnEventloop(() -> agent.shutdown()); // Second call should succeed // GH-90000
            assertThat(agent.getState()).isEqualTo(AbstractTypedAgent.State.STOPPED); // GH-90000
        }

        @Test
        @DisplayName("healthCheck returns status based on state")
        void shouldReturnHealthBasedOnState() { // GH-90000
            DoublerAgent agent = new DoublerAgent(); // GH-90000

            // CREATED → UNHEALTHY
            HealthStatus status = runOnEventloop(agent::healthCheck); // GH-90000
            assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.UNHEALTHY); // GH-90000

            // Initialize → READY → HEALTHY
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("doubler").type(AgentType.DETERMINISTIC).build();
            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            status = runOnEventloop(agent::healthCheck); // GH-90000
            assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY); // GH-90000

            // Shutdown → STOPPED → UNHEALTHY
            runOnEventloop(agent::shutdown); // GH-90000
            status = runOnEventloop(agent::healthCheck); // GH-90000
            assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.UNHEALTHY); // GH-90000
        }

        @Test
        @DisplayName("metrics are tracked")
        void shouldTrackMetrics() { // GH-90000
            DoublerAgent agent = new DoublerAgent(); // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("doubler").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            runOnEventloop(() -> agent.process(ctx, 1)); // GH-90000
            runOnEventloop(() -> agent.process(ctx, 2)); // GH-90000
            runOnEventloop(() -> agent.process(ctx, 3)); // GH-90000

            assertThat(agent.getTotalInvocations()).isEqualTo(3); // GH-90000
            assertThat(agent.getSuccessCount()).isEqualTo(3); // GH-90000
            assertThat(agent.getFailureCount()).isEqualTo(0); // GH-90000
            assertThat(agent.getAverageProcessingTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("failure increments failure count")
        void shouldTrackFailures() { // GH-90000
            FailingAgent agent = new FailingAgent(); // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("failing").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            runOnEventloop(() -> agent.process(ctx, "a")); // GH-90000
            runOnEventloop(() -> agent.process(ctx, "b")); // GH-90000

            assertThat(agent.getTotalInvocations()).isEqualTo(2); // GH-90000
            assertThat(agent.getFailureCount()).isEqualTo(2); // GH-90000
            assertThat(agent.getSuccessCount()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("processBatch default delegates to process")
        void shouldProcessBatchViaDefault() { // GH-90000
            DoublerAgent agent = new DoublerAgent(); // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("doubler").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            List<AgentResult<Integer>> results = runOnEventloop( // GH-90000
                    () -> agent.processBatch(ctx, List.of(1, 2, 3))); // GH-90000

            assertThat(results).hasSize(3); // GH-90000
            assertThat(results.get(0).getOutput()).isEqualTo(2); // GH-90000
            assertThat(results.get(1).getOutput()).isEqualTo(4); // GH-90000
            assertThat(results.get(2).getOutput()).isEqualTo(6); // GH-90000
        }

        @Test
        @DisplayName("reconfigure re-initializes agent")
        void shouldReconfigure() { // GH-90000
            LifecycleAgent agent = new LifecycleAgent(); // GH-90000
            AgentConfig config1 = AgentConfig.builder() // GH-90000
                    .agentId("lifecycle").type(AgentType.DETERMINISTIC).build();
            AgentConfig config2 = config1.toBuilder().version("2.0.0").build();

            runOnEventloop(() -> agent.initialize(config1)); // GH-90000
            assertThat(agent.getConfig().getVersion()).isEqualTo("1.0.0");

            runOnEventloop(() -> agent.reconfigure(config2)); // GH-90000
            assertThat(agent.getConfig().getVersion()).isEqualTo("2.0.0");
            assertThat(agent.getState()).isEqualTo(AbstractTypedAgent.State.READY); // GH-90000
        }

        @Test
        @DisplayName("A-3: lifecycle transitions are valid for each canonical agent type")
        void shouldTransitionLifecycleForEveryAgentType() {
            for (AgentType type : AgentType.values()) {
                LifecycleByTypeAgent agent = new LifecycleByTypeAgent(type);
                AgentConfig config = AgentConfig.builder()
                    .agentId(agent.descriptor().getAgentId())
                    .type(type)
                    .build();

                assertThat(agent.getState()).isEqualTo(AbstractTypedAgent.State.CREATED);

                runOnEventloop(() -> agent.initialize(config));
                assertThat(agent.getState()).isEqualTo(AbstractTypedAgent.State.READY);

                AgentResult<String> result = runOnEventloop(() -> agent.process(ctx, "tick"));
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getAgentId()).isEqualTo(agent.descriptor().getAgentId());

                runOnEventloop(agent::shutdown);
                assertThat(agent.getState()).isEqualTo(AbstractTypedAgent.State.STOPPED);
            }
        }

        @Test
        @DisplayName("A-4: two agents execute on the same event-loop without cross-interference")
        void shouldExecuteTwoAgentsOnSameEventLoop() {
            DoublerAgent firstAgent = new DoublerAgent();
            DoublerAgent secondAgent = new DoublerAgent();

            AgentConfig firstConfig = AgentConfig.builder()
                .agentId("doubler-1")
                .type(AgentType.DETERMINISTIC)
                .build();
            AgentConfig secondConfig = AgentConfig.builder()
                .agentId("doubler-2")
                .type(AgentType.DETERMINISTIC)
                .build();

            Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
            AtomicReference<List<AgentResult<Integer>>> results = new AtomicReference<>();
            AtomicReference<Exception> err = new AtomicReference<>();
            AtomicInteger callbacks = new AtomicInteger(0);

            eventloop.post(() ->
                Promises.all(firstAgent.initialize(firstConfig), secondAgent.initialize(secondConfig))
                    .then(() -> Promises.toList(List.of(
                        firstAgent.process(ctx.toBuilder().agentId("ctx-agent-1").build(), 10),
                        secondAgent.process(ctx.toBuilder().agentId("ctx-agent-2").build(), 20)
                    )))
                    .whenResult(r -> {
                        callbacks.incrementAndGet();
                        results.set(r);
                    })
                    .whenException(err::set)
            );

            eventloop.run();

            assertThat(err.get()).isNull();
            assertThat(results.get()).hasSize(2);
            assertThat(results.get().get(0).getOutput()).isEqualTo(20);
            assertThat(results.get().get(1).getOutput()).isEqualTo(40);
            assertThat(firstAgent.getTotalInvocations()).isEqualTo(1);
            assertThat(secondAgent.getTotalInvocations()).isEqualTo(1);
            assertThat(callbacks.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("A-5: failed Promise from doProcess is surfaced as failed AgentResult")
        void shouldSurfaceFailedPromiseAsFailedAgentResult() {
            AsyncFailingAgent agent = new AsyncFailingAgent();
            AgentConfig config = AgentConfig.builder()
                .agentId("async-failing")
                .type(AgentType.DETERMINISTIC)
                .build();

            runOnEventloop(() -> agent.initialize(config));
            AgentResult<String> result = runOnEventloop(() -> agent.process(ctx, "input"));

            assertThat(result.isFailed()).isTrue();
            assertThat(result.getExplanation()).contains("Async boom");
            assertThat(agent.getFailureCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("validateInput default returns true")
        void shouldValidateInputDefault() { // GH-90000
            DoublerAgent agent = new DoublerAgent(); // GH-90000
            assertThat(agent.validateInput(42)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("process rejects null context")
        void shouldRejectNullContext() { // GH-90000
            DoublerAgent agent = new DoublerAgent(); // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("doubler").type(AgentType.DETERMINISTIC).build();
            runOnEventloop(() -> agent.initialize(config)); // GH-90000

            // NPE thrown synchronously before Promise is created
            assertThatThrownBy(() -> agent.process(null, 1)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("context");
        }

        @Test
        @DisplayName("process rejects null input")
        void shouldRejectNullInput() { // GH-90000
            DoublerAgent agent = new DoublerAgent(); // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("doubler").type(AgentType.DETERMINISTIC).build();
            runOnEventloop(() -> agent.initialize(config)); // GH-90000

            // NPE thrown synchronously before Promise is created
            assertThatThrownBy(() -> agent.process(ctx, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("input");
        }

        @Test
        @DisplayName("initialize rejects null config")
        void shouldRejectNullConfig() { // GH-90000
            DoublerAgent agent = new DoublerAgent(); // GH-90000
            // NPE thrown synchronously before Promise is created
            assertThatThrownBy(() -> agent.initialize(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("config");
        }
    }

}
