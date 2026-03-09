/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.DefaultAgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        @DisplayName("has all 8 agent types")
        void shouldHaveAll6Types() {
            assertThat(AgentType.values()).hasSize(8);
            assertThat(AgentType.values()).containsExactly(
                    AgentType.DETERMINISTIC,
                    AgentType.PROBABILISTIC,
                    AgentType.HYBRID,
                    AgentType.ADAPTIVE,
                    AgentType.COMPOSITE,
                    AgentType.REACTIVE,
                    AgentType.LLM,
                    AgentType.CUSTOM
            );
        }

        @Test
        @DisplayName("valueOf round-trips correctly")
        void shouldRoundTrip() {
            for (AgentType type : AgentType.values()) {
                assertThat(AgentType.valueOf(type.name())).isSameAs(type);
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
        void shouldHave4Values() {
            assertThat(DeterminismGuarantee.values()).hasSize(4);
            assertThat(DeterminismGuarantee.values()).containsExactly(
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
        void shouldHave4Values() {
            assertThat(StateMutability.values()).containsExactly(
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
        void shouldHave6Values() {
            assertThat(FailureMode.values()).hasSize(6);
            assertThat(FailureMode.values()).containsExactly(
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

    @Nested
    @DisplayName("HealthStatus")
    class HealthStatusTest {

        @Test
        @DisplayName("has 6 values")
        void shouldHave6Values() {
            assertThat(HealthStatus.values()).hasSize(6);
            assertThat(HealthStatus.values()).containsExactly(
                    HealthStatus.HEALTHY,
                    HealthStatus.DEGRADED,
                    HealthStatus.UNHEALTHY,
                    HealthStatus.STARTING,
                    HealthStatus.STOPPING,
                    HealthStatus.UNKNOWN
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AgentResultStatus Enum
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentResultStatus")
    class AgentResultStatusTest {

        @Test
        @DisplayName("has 7 values")
        void shouldHave7Values() {
            assertThat(AgentResultStatus.values()).hasSize(7);
            assertThat(AgentResultStatus.values()).containsExactly(
                    AgentResultStatus.SUCCESS,
                    AgentResultStatus.LOW_CONFIDENCE,
                    AgentResultStatus.SKIPPED,
                    AgentResultStatus.FAILED,
                    AgentResultStatus.TIMEOUT,
                    AgentResultStatus.DEGRADED,
                    AgentResultStatus.DELEGATED
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
        void shouldBuildWithAllFields() {
            AgentDescriptor desc = AgentDescriptor.builder()
                    .agentId("fraud-v2")
                    .name("Fraud Detector")
                    .version("2.1.0")
                    .description("Real-time fraud detection")
                    .namespace("security")
                    .type(AgentType.HYBRID)
                    .subtype("ML_FALLBACK")
                    .determinism(DeterminismGuarantee.CONFIG_SCOPED)
                    .latencySla(Duration.ofMillis(50))
                    .throughputTarget(5000)
                    .stateMutability(StateMutability.LOCAL_STATE)
                    .failureMode(FailureMode.CIRCUIT_BREAKER)
                    .capabilities(Set.of("fraud-detection", "risk-scoring"))
                    .inputEventTypes(Set.of("Transaction"))
                    .outputEventTypes(Set.of("FraudAlert"))
                    .metadata(Map.of("model", "xgboost-v3"))
                    .labels(Map.of("team", "fraud-prevention"))
                    .annotations(Map.of("doc-url", "http://docs/fraud"))
                    .build();

            assertThat(desc.getAgentId()).isEqualTo("fraud-v2");
            assertThat(desc.getName()).isEqualTo("Fraud Detector");
            assertThat(desc.getVersion()).isEqualTo("2.1.0");
            assertThat(desc.getDescription()).isEqualTo("Real-time fraud detection");
            assertThat(desc.getNamespace()).isEqualTo("security");
            assertThat(desc.getType()).isEqualTo(AgentType.HYBRID);
            assertThat(desc.getSubtype()).isEqualTo("ML_FALLBACK");
            assertThat(desc.getDeterminism()).isEqualTo(DeterminismGuarantee.CONFIG_SCOPED);
            assertThat(desc.getLatencySla()).isEqualTo(Duration.ofMillis(50));
            assertThat(desc.getThroughputTarget()).isEqualTo(5000);
            assertThat(desc.getStateMutability()).isEqualTo(StateMutability.LOCAL_STATE);
            assertThat(desc.getFailureMode()).isEqualTo(FailureMode.CIRCUIT_BREAKER);
            assertThat(desc.getCapabilities()).containsExactlyInAnyOrder("fraud-detection", "risk-scoring");
            assertThat(desc.getInputEventTypes()).containsExactly("Transaction");
            assertThat(desc.getOutputEventTypes()).containsExactly("FraudAlert");
            assertThat(desc.getMetadata()).containsEntry("model", "xgboost-v3");
            assertThat(desc.getLabels()).containsEntry("team", "fraud-prevention");
        }

        @Test
        @DisplayName("defaults are sensible")
        void shouldHaveSensibleDefaults() {
            AgentDescriptor desc = AgentDescriptor.builder()
                    .agentId("simple")
                    .name("Simple Agent")
                    .type(AgentType.DETERMINISTIC)
                    .build();

            assertThat(desc.getVersion()).isEqualTo("1.0.0");
            assertThat(desc.getNamespace()).isEqualTo("default");
            assertThat(desc.getDeterminism()).isEqualTo(DeterminismGuarantee.NONE);
            assertThat(desc.getLatencySla()).isEqualTo(Duration.ofSeconds(5));
            assertThat(desc.getThroughputTarget()).isEqualTo(1000);
            assertThat(desc.getStateMutability()).isEqualTo(StateMutability.STATELESS);
            assertThat(desc.getFailureMode()).isEqualTo(FailureMode.FAIL_FAST);
            assertThat(desc.getCapabilities()).isEmpty();
            assertThat(desc.getInputEventTypes()).isEmpty();
            assertThat(desc.getOutputEventTypes()).isEmpty();
            assertThat(desc.getMetadata()).isEmpty();
            assertThat(desc.getLabels()).isEmpty();
            assertThat(desc.getAnnotations()).isEmpty();
        }

        @Test
        @DisplayName("isDeterministic — FULL returns true")
        void shouldDetectFullDeterminism() {
            AgentDescriptor desc = AgentDescriptor.builder()
                    .agentId("det")
                    .name("Det")
                    .type(AgentType.DETERMINISTIC)
                    .determinism(DeterminismGuarantee.FULL)
                    .build();
            assertThat(desc.isDeterministic()).isTrue();
        }

        @Test
        @DisplayName("isDeterministic — CONFIG_SCOPED returns true")
        void shouldDetectConfigScopedDeterminism() {
            AgentDescriptor desc = AgentDescriptor.builder()
                    .agentId("cs")
                    .name("ConfigScoped")
                    .type(AgentType.HYBRID)
                    .determinism(DeterminismGuarantee.CONFIG_SCOPED)
                    .build();
            assertThat(desc.isDeterministic()).isTrue();
        }

        @Test
        @DisplayName("isDeterministic — NONE returns false")
        void shouldDetectNonDeterminism() {
            AgentDescriptor desc = AgentDescriptor.builder()
                    .agentId("prob")
                    .name("Prob")
                    .type(AgentType.PROBABILISTIC)
                    .determinism(DeterminismGuarantee.NONE)
                    .build();
            assertThat(desc.isDeterministic()).isFalse();
        }

        @Test
        @DisplayName("isStateless")
        void shouldDetectStateless() {
            AgentDescriptor desc = AgentDescriptor.builder()
                    .agentId("sl")
                    .name("Stateless")
                    .type(AgentType.DETERMINISTIC)
                    .stateMutability(StateMutability.STATELESS)
                    .build();
            assertThat(desc.isStateless()).isTrue();

            AgentDescriptor stateful = desc.toBuilder()
                    .stateMutability(StateMutability.LOCAL_STATE)
                    .build();
            assertThat(stateful.isStateless()).isFalse();
        }

        @Test
        @DisplayName("hasCapability")
        void shouldDetectCapability() {
            AgentDescriptor desc = AgentDescriptor.builder()
                    .agentId("cap")
                    .name("Cap")
                    .type(AgentType.DETERMINISTIC)
                    .capabilities(Set.of("fraud", "risk"))
                    .build();
            assertThat(desc.hasCapability("fraud")).isTrue();
            assertThat(desc.hasCapability("enrichment")).isFalse();
        }

        @Test
        @DisplayName("toCapabilities bridges to legacy AgentCapabilities")
        void shouldBridgeToCapabilities() {
            AgentDescriptor desc = AgentDescriptor.builder()
                    .agentId("bridge")
                    .name("Bridge Agent")
                    .description("A bridged agent")
                    .type(AgentType.HYBRID)
                    .capabilities(Set.of("cap1", "cap2"))
                    .build();

            AgentCapabilities caps = desc.toCapabilities();
            assertThat(caps.name()).isEqualTo("Bridge Agent");
            assertThat(caps.role()).isEqualTo("HYBRID");
            assertThat(caps.description()).isEqualTo("A bridged agent");
            assertThat(caps.supportedTaskTypes()).containsExactlyInAnyOrder("cap1", "cap2");
        }

        @Test
        @DisplayName("toBuilder creates independent copy")
        void shouldCopyWithToBuilder() {
            AgentDescriptor original = AgentDescriptor.builder()
                    .agentId("orig")
                    .name("Original")
                    .type(AgentType.DETERMINISTIC)
                    .build();

            AgentDescriptor copy = original.toBuilder()
                    .agentId("copy")
                    .type(AgentType.PROBABILISTIC)
                    .build();

            assertThat(original.getAgentId()).isEqualTo("orig");
            assertThat(copy.getAgentId()).isEqualTo("copy");
            assertThat(copy.getType()).isEqualTo(AgentType.PROBABILISTIC);
            assertThat(original.getType()).isEqualTo(AgentType.DETERMINISTIC);
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
        void shouldCreateSuccessResult() {
            AgentResult<String> result = AgentResult.success("output", "agent-1", Duration.ofMillis(42));
            assertThat(result.getOutput()).isEqualTo("output");
            assertThat(result.getConfidence()).isEqualTo(1.0);
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);
            assertThat(result.getAgentId()).isEqualTo("agent-1");
            assertThat(result.getProcessingTime()).isEqualTo(Duration.ofMillis(42));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isFailed()).isFalse();
            assertThat(result.meetsConfidence(0.5)).isTrue();
            assertThat(result.meetsConfidence(1.0)).isTrue();
        }

        @Test
        @DisplayName("successWithConfidence factory — above threshold")
        void shouldCreateHighConfidenceResult() {
            AgentResult<Integer> result = AgentResult.successWithConfidence(
                    42, 0.85, "ml-agent", Duration.ofMillis(100), "Model v3 inference");
            assertThat(result.getOutput()).isEqualTo(42);
            assertThat(result.getConfidence()).isEqualTo(0.85);
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);
            assertThat(result.getExplanation()).isEqualTo("Model v3 inference");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.meetsConfidence(0.8)).isTrue();
            assertThat(result.meetsConfidence(0.9)).isFalse();
        }

        @Test
        @DisplayName("successWithConfidence factory — below threshold marks LOW_CONFIDENCE")
        void shouldMarkLowConfidence() {
            AgentResult<String> result = AgentResult.successWithConfidence(
                    "uncertain", 0.3, "low-conf", Duration.ofMillis(50), "Weak signal");
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.LOW_CONFIDENCE);
            assertThat(result.getConfidence()).isEqualTo(0.3);
            assertThat(result.isSuccess()).isFalse(); // LOW_CONFIDENCE != SUCCESS
        }

        @Test
        @DisplayName("failure factory method")
        void shouldCreateFailureResult() {
            RuntimeException error = new RuntimeException("NullPointerException");
            AgentResult<Void> result = AgentResult.failure(error, "agent-2", Duration.ofMillis(10));
            assertThat(result.getOutput()).isNull();
            assertThat(result.getConfidence()).isEqualTo(0.0);
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED);
            assertThat(result.isFailed()).isTrue();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getExplanation()).contains("RuntimeException");
            assertThat(result.getExplanation()).contains("NullPointerException");
        }

        @Test
        @DisplayName("failure factory rejects null error")
        void shouldRejectNullError() {
            assertThatThrownBy(() -> AgentResult.failure(null, "a", Duration.ZERO))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("timeout factory method")
        void shouldCreateTimeoutResult() {
            AgentResult<Void> result = AgentResult.timeout("slow-agent", Duration.ofSeconds(5));
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.TIMEOUT);
            assertThat(result.isFailed()).isTrue();
            assertThat(result.getExplanation()).contains("5000ms");
        }

        @Test
        @DisplayName("skipped factory method")
        void shouldCreateSkippedResult() {
            AgentResult<Void> result = AgentResult.skipped("Input doesn't match", "filter-agent");
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);
            assertThat(result.getExplanation()).isEqualTo("Input doesn't match");
            assertThat(result.getProcessingTime()).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("delegated factory method")
        void shouldCreateDelegatedResult() {
            AgentResult<Void> result = AgentResult.delegated("ml-agent-v2", "router-agent");
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DELEGATED);
            assertThat(result.getAgentId()).isEqualTo("router-agent");
            assertThat(result.getExplanation()).contains("ml-agent-v2");
            assertThat(result.getMetrics()).containsEntry("delegateAgentId", "ml-agent-v2");
        }

        @Test
        @DisplayName("toBuilder creates modifiable copy")
        void shouldSupportToBuilder() {
            AgentResult<String> original = AgentResult.success("out", "a1", Duration.ofMillis(1));
            AgentResult<String> modified = original.toBuilder()
                    .confidence(0.5)
                    .explanation("Overridden")
                    .build();
            assertThat(original.getConfidence()).isEqualTo(1.0);
            assertThat(modified.getConfidence()).isEqualTo(0.5);
            assertThat(modified.getExplanation()).isEqualTo("Overridden");
        }

        @Test
        @DisplayName("default builder produces SUCCESS with confidence 1.0")
        void shouldDefaultToSuccess() {
            AgentResult<String> result = AgentResult.<String>builder()
                    .output("hello")
                    .agentId("test")
                    .build();
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);
            assertThat(result.getConfidence()).isEqualTo(1.0);
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
        void shouldBuildWithAllFields() {
            AgentConfig config = AgentConfig.builder()
                    .agentId("config-test")
                    .type(AgentType.HYBRID)
                    .version("2.0.0")
                    .timeout(Duration.ofMillis(500))
                    .confidenceThreshold(0.7)
                    .maxRetries(3)
                    .retryBackoff(Duration.ofMillis(200))
                    .maxRetryBackoff(Duration.ofSeconds(10))
                    .failureMode(FailureMode.RETRY)
                    .circuitBreakerThreshold(10)
                    .circuitBreakerReset(Duration.ofSeconds(60))
                    .metricsEnabled(false)
                    .tracingEnabled(false)
                    .tracingSampleRate(0.5)
                    .properties(Map.of("key", "val"))
                    .labels(Map.of("env", "test"))
                    .requiredCapabilities(Set.of("gpu"))
                    .build();

            assertThat(config.getAgentId()).isEqualTo("config-test");
            assertThat(config.getType()).isEqualTo(AgentType.HYBRID);
            assertThat(config.getVersion()).isEqualTo("2.0.0");
            assertThat(config.getTimeout()).isEqualTo(Duration.ofMillis(500));
            assertThat(config.getConfidenceThreshold()).isEqualTo(0.7);
            assertThat(config.getMaxRetries()).isEqualTo(3);
            assertThat(config.getRetryBackoff()).isEqualTo(Duration.ofMillis(200));
            assertThat(config.getMaxRetryBackoff()).isEqualTo(Duration.ofSeconds(10));
            assertThat(config.getFailureMode()).isEqualTo(FailureMode.RETRY);
            assertThat(config.getCircuitBreakerThreshold()).isEqualTo(10);
            assertThat(config.getCircuitBreakerReset()).isEqualTo(Duration.ofSeconds(60));
            assertThat(config.isMetricsEnabled()).isFalse();
            assertThat(config.isTracingEnabled()).isFalse();
            assertThat(config.getTracingSampleRate()).isEqualTo(0.5);
            assertThat(config.getProperties()).containsEntry("key", "val");
            assertThat(config.getLabels()).containsEntry("env", "test");
            assertThat(config.getRequiredCapabilities()).containsExactly("gpu");
        }

        @Test
        @DisplayName("defaults are sensible")
        void shouldHaveSensibleDefaults() {
            AgentConfig config = AgentConfig.builder()
                    .agentId("default-test")
                    .type(AgentType.DETERMINISTIC)
                    .build();

            assertThat(config.getVersion()).isEqualTo("1.0.0");
            assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(config.getConfidenceThreshold()).isEqualTo(0.5);
            assertThat(config.getMaxRetries()).isEqualTo(0);
            assertThat(config.getRetryBackoff()).isEqualTo(Duration.ofMillis(100));
            assertThat(config.getMaxRetryBackoff()).isEqualTo(Duration.ofSeconds(5));
            assertThat(config.getFailureMode()).isEqualTo(FailureMode.FAIL_FAST);
            assertThat(config.getCircuitBreakerThreshold()).isEqualTo(5);
            assertThat(config.getCircuitBreakerReset()).isEqualTo(Duration.ofSeconds(30));
            assertThat(config.isMetricsEnabled()).isTrue();
            assertThat(config.isTracingEnabled()).isTrue();
            assertThat(config.getTracingSampleRate()).isEqualTo(0.1);
            assertThat(config.getProperties()).isEmpty();
            assertThat(config.getLabels()).isEmpty();
            assertThat(config.getRequiredCapabilities()).isEmpty();
        }

        @Test
        @DisplayName("toBuilder creates independent copy")
        void shouldSupportToBuilder() {
            AgentConfig original = AgentConfig.builder()
                    .agentId("orig")
                    .type(AgentType.DETERMINISTIC)
                    .build();
            AgentConfig copy = original.toBuilder()
                    .timeout(Duration.ofMillis(100))
                    .maxRetries(5)
                    .build();
            assertThat(original.getTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(copy.getTimeout()).isEqualTo(Duration.ofMillis(100));
            assertThat(copy.getMaxRetries()).isEqualTo(5);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Enhanced AgentContext (framework.api — v2.0 additions)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentContext v2.0 Enhancements")
    class AgentContextV2Test {

        private MemoryStore memoryStore;

        @BeforeEach
        void setup() {
            memoryStore = mock(MemoryStore.class);
        }

        @Test
        @DisplayName("traceId is stored and retrievable")
        void shouldStoreTraceId() {
            AgentContext ctx = AgentContext.builder()
                    .agentId("agent-1")
                    .tenantId("tenant-1")
                    .memoryStore(memoryStore)
                    .traceId("trace-abc-123")
                    .build();
            assertThat(ctx.getTraceId()).isEqualTo("trace-abc-123");
        }

        @Test
        @DisplayName("traceId defaults to null")
        void shouldDefaultTraceIdToNull() {
            AgentContext ctx = AgentContext.builder()
                    .agentId("agent-1")
                    .tenantId("tenant-1")
                    .memoryStore(memoryStore)
                    .build();
            assertThat(ctx.getTraceId()).isNull();
        }

        @Test
        @DisplayName("metadata get/set works")
        void shouldSupportMetadata() {
            AgentContext ctx = AgentContext.builder()
                    .agentId("agent-1")
                    .tenantId("tenant-1")
                    .memoryStore(memoryStore)
                    .build();

            ctx.setMetadata("pipeline.stage", "enrichment");
            ctx.setMetadata("count", 42);
            assertThat(ctx.getMetadata()).containsEntry("pipeline.stage", "enrichment");
            assertThat(ctx.getMetadata()).containsEntry("count", 42);
        }

        @Test
        @DisplayName("metadata initializes from builder")
        void shouldInitMetadataFromBuilder() {
            AgentContext ctx = AgentContext.builder()
                    .agentId("agent-1")
                    .tenantId("tenant-1")
                    .memoryStore(memoryStore)
                    .metadata(Map.of("key", "value"))
                    .build();

            assertThat(ctx.getMetadata()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("deriveChild creates new context with child agent ID")
        void shouldDeriveChild() {
            AgentContext parent = AgentContext.builder()
                    .agentId("parent-agent")
                    .tenantId("tenant-1")
                    .memoryStore(memoryStore)
                    .traceId("trace-xyz")
                    .build();

            AgentContext child = parent.deriveChild("child-agent");

            assertThat(child.getAgentId()).isEqualTo("child-agent");
            assertThat(child.getTenantId()).isEqualTo("tenant-1");
            assertThat(child.getTraceId()).isEqualTo("trace-xyz");
            assertThat(child.getTurnId()).isNotEqualTo(parent.getTurnId()); // New turn
        }

        @Test
        @DisplayName("deriveChild preserves config")
        void shouldPreserveConfigInChild() {
            AgentContext parent = AgentContext.builder()
                    .agentId("parent-agent")
                    .tenantId("tenant-1")
                    .memoryStore(memoryStore)
                    .config(Map.of("key", "value"))
                    .build();

            AgentContext child = parent.deriveChild("child-agent");
            assertThat(child.getAllConfig()).containsEntry("key", "value");
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
        void setup() {
            memoryStore = mock(MemoryStore.class);
            ctx = AgentContext.builder()
                    .agentId("ctx-agent")
                    .tenantId("test-tenant")
                    .memoryStore(memoryStore)
                    .build();
        }

        // Test agent: doubles the input integer
        static class DoublerAgent extends AbstractTypedAgent<Integer, Integer> {
            @Override
            @NotNull
            public AgentDescriptor descriptor() {
                return AgentDescriptor.builder()
                        .agentId("doubler")
                        .name("Doubler")
                        .type(AgentType.DETERMINISTIC)
                        .determinism(DeterminismGuarantee.FULL)
                        .build();
            }

            @Override
            @NotNull
            protected Promise<AgentResult<Integer>> doProcess(@NotNull AgentContext ctx, @NotNull Integer input) {
                return Promise.of(AgentResult.success(input * 2, "doubler", Duration.ZERO));
            }
        }

        // Test agent: always fails
        static class FailingAgent extends AbstractTypedAgent<String, String> {
            @Override
            @NotNull
            public AgentDescriptor descriptor() {
                return AgentDescriptor.builder()
                        .agentId("failing")
                        .name("Failing")
                        .type(AgentType.DETERMINISTIC)
                        .build();
            }

            @Override
            @NotNull
            protected Promise<AgentResult<String>> doProcess(@NotNull AgentContext ctx, @NotNull String input) {
                throw new RuntimeException("Boom!");
            }
        }

        // Test agent: tracks initialize/shutdown calls
        static class LifecycleAgent extends AbstractTypedAgent<String, String> {
            boolean initialized = false;
            boolean shutDown = false;

            @Override
            @NotNull
            public AgentDescriptor descriptor() {
                return AgentDescriptor.builder()
                        .agentId("lifecycle")
                        .name("Lifecycle")
                        .type(AgentType.DETERMINISTIC)
                        .build();
            }

            @Override
            @NotNull
            protected Promise<Void> doInitialize(@NotNull AgentConfig config) {
                initialized = true;
                return Promise.complete();
            }

            @Override
            @NotNull
            protected Promise<AgentResult<String>> doProcess(@NotNull AgentContext ctx, @NotNull String input) {
                return Promise.of(AgentResult.success(input.toUpperCase(), "lifecycle", Duration.ZERO));
            }

            @Override
            @NotNull
            protected Promise<Void> doShutdown() {
                shutDown = true;
                return Promise.complete();
            }
        }

        private <T> T runOnEventloop(java.util.function.Supplier<Promise<T>> promiseSupplier) {
            Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
            AtomicReference<T> ref = new AtomicReference<>();
            AtomicReference<Exception> err = new AtomicReference<>();
            eventloop.post(() -> promiseSupplier.get()
                    .whenResult(ref::set)
                    .whenException(err::set));
            eventloop.run();
            if (err.get() != null) {
                throw new RuntimeException(err.get());
            }
            return ref.get();
        }

        @Test
        @DisplayName("starts in CREATED state")
        void shouldStartInCreatedState() {
            DoublerAgent agent = new DoublerAgent();
            assertThat(agent.getState()).isEqualTo(AbstractTypedAgent.State.CREATED);
        }

        @Test
        @DisplayName("descriptor available before initialize")
        void shouldProvideDescriptorBeforeInit() {
            DoublerAgent agent = new DoublerAgent();
            assertThat(agent.descriptor().getAgentId()).isEqualTo("doubler");
            assertThat(agent.descriptor().getType()).isEqualTo(AgentType.DETERMINISTIC);
        }

        @Test
        @DisplayName("initialize transitions to READY")
        void shouldTransitionToReady() {
            LifecycleAgent agent = new LifecycleAgent();
            AgentConfig config = AgentConfig.builder()
                    .agentId("lifecycle").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config));
            assertThat(agent.getState()).isEqualTo(AbstractTypedAgent.State.READY);
            assertThat(agent.initialized).isTrue();
            assertThat(agent.getConfig()).isSameAs(config);
        }

        @Test
        @DisplayName("process works after initialize")
        void shouldProcessAfterInit() {
            DoublerAgent agent = new DoublerAgent();
            AgentConfig config = AgentConfig.builder()
                    .agentId("doubler").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config));
            AgentResult<Integer> result = runOnEventloop(() -> agent.process(ctx, 21));

            assertThat(result.getOutput()).isEqualTo(42);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAgentId()).isEqualTo("doubler");
            assertThat(result.getProcessingTime()).isNotNull();
        }

        @Test
        @DisplayName("process fails when not initialized")
        void shouldFailWhenNotInitialized() {
            DoublerAgent agent = new DoublerAgent();
            AgentResult<Integer> result = runOnEventloop(() -> agent.process(ctx, 21));

            assertThat(result.isFailed()).isTrue();
            assertThat(result.getExplanation()).contains("not ready");
        }

        @Test
        @DisplayName("process catches synchronous exceptions")
        void shouldCatchSyncExceptions() {
            FailingAgent agent = new FailingAgent();
            AgentConfig config = AgentConfig.builder()
                    .agentId("failing").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config));
            AgentResult<String> result = runOnEventloop(() -> agent.process(ctx, "test"));

            assertThat(result.isFailed()).isTrue();
            assertThat(result.getExplanation()).contains("Boom!");
        }

        @Test
        @DisplayName("shutdown transitions to STOPPED")
        void shouldTransitionToStopped() {
            LifecycleAgent agent = new LifecycleAgent();
            AgentConfig config = AgentConfig.builder()
                    .agentId("lifecycle").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config));
            runOnEventloop(() -> agent.shutdown());

            assertThat(agent.getState()).isEqualTo(AbstractTypedAgent.State.STOPPED);
            assertThat(agent.shutDown).isTrue();
        }

        @Test
        @DisplayName("shutdown is idempotent")
        void shouldBeIdempotentShutdown() {
            LifecycleAgent agent = new LifecycleAgent();
            AgentConfig config = AgentConfig.builder()
                    .agentId("lifecycle").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config));
            runOnEventloop(() -> agent.shutdown());
            runOnEventloop(() -> agent.shutdown()); // Second call should succeed
            assertThat(agent.getState()).isEqualTo(AbstractTypedAgent.State.STOPPED);
        }

        @Test
        @DisplayName("healthCheck returns status based on state")
        void shouldReturnHealthBasedOnState() {
            DoublerAgent agent = new DoublerAgent();

            // CREATED → UNHEALTHY
            HealthStatus status = runOnEventloop(agent::healthCheck);
            assertThat(status).isEqualTo(HealthStatus.UNHEALTHY);

            // Initialize → READY → HEALTHY
            AgentConfig config = AgentConfig.builder()
                    .agentId("doubler").type(AgentType.DETERMINISTIC).build();
            runOnEventloop(() -> agent.initialize(config));
            status = runOnEventloop(agent::healthCheck);
            assertThat(status).isEqualTo(HealthStatus.HEALTHY);

            // Shutdown → STOPPED → UNHEALTHY
            runOnEventloop(agent::shutdown);
            status = runOnEventloop(agent::healthCheck);
            assertThat(status).isEqualTo(HealthStatus.UNHEALTHY);
        }

        @Test
        @DisplayName("metrics are tracked")
        void shouldTrackMetrics() {
            DoublerAgent agent = new DoublerAgent();
            AgentConfig config = AgentConfig.builder()
                    .agentId("doubler").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config));
            runOnEventloop(() -> agent.process(ctx, 1));
            runOnEventloop(() -> agent.process(ctx, 2));
            runOnEventloop(() -> agent.process(ctx, 3));

            assertThat(agent.getTotalInvocations()).isEqualTo(3);
            assertThat(agent.getSuccessCount()).isEqualTo(3);
            assertThat(agent.getFailureCount()).isEqualTo(0);
            assertThat(agent.getAverageProcessingTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("failure increments failure count")
        void shouldTrackFailures() {
            FailingAgent agent = new FailingAgent();
            AgentConfig config = AgentConfig.builder()
                    .agentId("failing").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config));
            runOnEventloop(() -> agent.process(ctx, "a"));
            runOnEventloop(() -> agent.process(ctx, "b"));

            assertThat(agent.getTotalInvocations()).isEqualTo(2);
            assertThat(agent.getFailureCount()).isEqualTo(2);
            assertThat(agent.getSuccessCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("processBatch default delegates to process")
        void shouldProcessBatchViaDefault() {
            DoublerAgent agent = new DoublerAgent();
            AgentConfig config = AgentConfig.builder()
                    .agentId("doubler").type(AgentType.DETERMINISTIC).build();

            runOnEventloop(() -> agent.initialize(config));
            List<AgentResult<Integer>> results = runOnEventloop(
                    () -> agent.processBatch(ctx, List.of(1, 2, 3)));

            assertThat(results).hasSize(3);
            assertThat(results.get(0).getOutput()).isEqualTo(2);
            assertThat(results.get(1).getOutput()).isEqualTo(4);
            assertThat(results.get(2).getOutput()).isEqualTo(6);
        }

        @Test
        @DisplayName("reconfigure re-initializes agent")
        void shouldReconfigure() {
            LifecycleAgent agent = new LifecycleAgent();
            AgentConfig config1 = AgentConfig.builder()
                    .agentId("lifecycle").type(AgentType.DETERMINISTIC).build();
            AgentConfig config2 = config1.toBuilder().version("2.0.0").build();

            runOnEventloop(() -> agent.initialize(config1));
            assertThat(agent.getConfig().getVersion()).isEqualTo("1.0.0");

            runOnEventloop(() -> agent.reconfigure(config2));
            assertThat(agent.getConfig().getVersion()).isEqualTo("2.0.0");
            assertThat(agent.getState()).isEqualTo(AbstractTypedAgent.State.READY);
        }

        @Test
        @DisplayName("validateInput default returns true")
        void shouldValidateInputDefault() {
            DoublerAgent agent = new DoublerAgent();
            assertThat(agent.validateInput(42)).isTrue();
        }

        @Test
        @DisplayName("process rejects null context")
        void shouldRejectNullContext() {
            DoublerAgent agent = new DoublerAgent();
            AgentConfig config = AgentConfig.builder()
                    .agentId("doubler").type(AgentType.DETERMINISTIC).build();
            runOnEventloop(() -> agent.initialize(config));

            // NPE thrown synchronously before Promise is created
            assertThatThrownBy(() -> agent.process(null, 1))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("context");
        }

        @Test
        @DisplayName("process rejects null input")
        void shouldRejectNullInput() {
            DoublerAgent agent = new DoublerAgent();
            AgentConfig config = AgentConfig.builder()
                    .agentId("doubler").type(AgentType.DETERMINISTIC).build();
            runOnEventloop(() -> agent.initialize(config));

            // NPE thrown synchronously before Promise is created
            assertThatThrownBy(() -> agent.process(ctx, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("input");
        }

        @Test
        @DisplayName("initialize rejects null config")
        void shouldRejectNullConfig() {
            DoublerAgent agent = new DoublerAgent();
            // NPE thrown synchronously before Promise is created
            assertThatThrownBy(() -> agent.initialize(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("config");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Backward Compatibility: Old Agent interface still works
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Backward Compatibility")
    class BackwardCompatibilityTest {

        @Test
        @DisplayName("old Agent interface is still available")
        void shouldHaveOldAgentInterface() {
            // Verifies the untyped Agent interface was NOT removed
            assertThat(Agent.class).isInterface();
            assertThat(Agent.class.getMethods()).isNotEmpty();
        }

        @Test
        @DisplayName("old AgentCapabilities record is still available")
        void shouldHaveOldAgentCapabilities() {
            AgentCapabilities caps = new AgentCapabilities(
                    "TestAgent", "WORKER", "A test agent",
                    Set.of("task-a"), Set.of("tool-b"));
            assertThat(caps.name()).isEqualTo("TestAgent");
            assertThat(caps.role()).isEqualTo("WORKER");
            assertThat(caps.supportedTaskTypes()).containsExactly("task-a");
        }

        @Test
        @DisplayName("AgentDescriptor.toCapabilities bridges new→old")
        void shouldBridgeNewToOld() {
            AgentDescriptor desc = AgentDescriptor.builder()
                    .agentId("bridged")
                    .name("Bridged")
                    .type(AgentType.COMPOSITE)
                    .capabilities(Set.of("cap1"))
                    .build();
            AgentCapabilities caps = desc.toCapabilities();
            assertThat(caps.role()).isEqualTo("COMPOSITE");
            assertThat(caps.supportedTaskTypes()).containsExactly("cap1");
        }
    }
}
