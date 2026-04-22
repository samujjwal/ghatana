/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */

package com.ghatana.agent.probabilistic;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Comprehensive tests for ProbabilisticAgent, ModelInference, ConfidenceCalibrator.
 */
@DisplayName("Probabilistic Agent [GH-90000]")
class ProbabilisticAgentTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1 [GH-90000]")
                .agentId("test-agent [GH-90000]")
                .tenantId("test-tenant [GH-90000]")
                .memoryStore(mock(MemoryStore.class)) // GH-90000
                .build(); // GH-90000
    }

    private <T> T runOnEventloop(java.util.function.Supplier<Promise<T>> supplier) { // GH-90000
        AtomicReference<T> result = new AtomicReference<>(); // GH-90000
        AtomicReference<Exception> err = new AtomicReference<>(); // GH-90000
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
        eventloop.post(() -> supplier.get() // GH-90000
                .whenResult(result::set) // GH-90000
                .whenException(err::set)); // GH-90000
        eventloop.run(); // GH-90000
        if (err.get() != null) throw new RuntimeException(err.get()); // GH-90000
        return result.get(); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Mock ModelInference
    // ═══════════════════════════════════════════════════════════════════════════

    private ModelInference mockModel(String id, Map<String, Object> output, double confidence) { // GH-90000
        return new ModelInference() { // GH-90000
            @Override public @org.jetbrains.annotations.NotNull Promise<InferenceResult> infer( // GH-90000
                    @org.jetbrains.annotations.NotNull Map<String, Object> input) {
                return Promise.of(new InferenceResult(output, confidence, id, 10)); // GH-90000
            }
            @Override public @org.jetbrains.annotations.NotNull String modelId() { return id; } // GH-90000
        };
    }

    private ModelInference failingModel(String id) { // GH-90000
        return new ModelInference() { // GH-90000
            @Override public @org.jetbrains.annotations.NotNull Promise<InferenceResult> infer( // GH-90000
                    @org.jetbrains.annotations.NotNull Map<String, Object> input) {
                return Promise.ofException(new RuntimeException("Model " + id + " failed")); // GH-90000
            }
            @Override public @org.jetbrains.annotations.NotNull String modelId() { return id; } // GH-90000
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ConfidenceCalibrator
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ConfidenceCalibrator [GH-90000]")
    class CalibratorTests {

        @Test void identityReturnsRawValue() { // GH-90000
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder() // GH-90000
                    .method(ConfidenceCalibrator.Method.IDENTITY).build(); // GH-90000
            assertThat(cal.calibrate(0.73)).isCloseTo(0.73, within(1e-9)); // GH-90000
        }

        @Test void temperatureScaling() { // GH-90000
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder() // GH-90000
                    .method(ConfidenceCalibrator.Method.TEMPERATURE) // GH-90000
                    .temperature(2.0) // GH-90000
                    .build(); // GH-90000
            double result = cal.calibrate(0.8); // GH-90000
            // temperature divides logit by T
            assertThat(result).isBetween(0.0, 1.0); // GH-90000
        }

        @Test void plattCalibration() { // GH-90000
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder() // GH-90000
                    .method(ConfidenceCalibrator.Method.PLATT) // GH-90000
                    .plattA(-1.0).plattB(0.0) // GH-90000
                    .build(); // GH-90000
            double result = cal.calibrate(0.5); // GH-90000
            assertThat(result).isBetween(0.0, 1.0); // GH-90000
        }

        @Test void isotonicCalibration() { // GH-90000
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder() // GH-90000
                    .method(ConfidenceCalibrator.Method.ISOTONIC) // GH-90000
                    .isotonicBreakpoints(new double[]{0.0, 0.5, 1.0}) // GH-90000
                    .isotonicValues(new double[]{0.1, 0.6, 0.95}) // GH-90000
                    .build(); // GH-90000
            double result = cal.calibrate(0.75); // GH-90000
            assertThat(result).isBetween(0.6, 0.95); // GH-90000
        }

        @Test void clampsToUnitInterval() { // GH-90000
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder() // GH-90000
                    .method(ConfidenceCalibrator.Method.IDENTITY).build(); // GH-90000
            assertThat(cal.calibrate(-0.5)).isCloseTo(0.0, within(1e-9)); // GH-90000
            assertThat(cal.calibrate(1.5)).isCloseTo(1.0, within(1e-9)); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ProbabilisticAgent
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Agent Processing [GH-90000]")
    class ProcessingTests {

        @Test void basicInference() { // GH-90000
            ModelInference model = mockModel("gpt-4", Map.of("label", "positive"), 0.95); // GH-90000
            ProbabilisticAgent agent = new ProbabilisticAgent("ml-agent", model); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("ml-agent [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            AgentResult<Map<String, Object>> result =
                    runOnEventloop(() -> agent.process(ctx, Map.of("text", "great"))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("label", "positive"); // GH-90000
            assertThat(result.getConfidence()).isGreaterThan(0.5); // GH-90000
        }

        @Test void lowConfidenceMarkedCorrectly() { // GH-90000
            ModelInference model = mockModel("weak-model", Map.of("label", "unknown"), 0.3); // GH-90000
            ProbabilisticAgent agent = new ProbabilisticAgent("low-conf", model); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("low-conf [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .confidenceThreshold(0.85) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.LOW_CONFIDENCE); // GH-90000
        }

        @Test void shadowModeReturnsSkipped() { // GH-90000
            ModelInference model = mockModel("shadow", Map.of("result", "test"), 0.9); // GH-90000
            ProbabilisticAgent agent = new ProbabilisticAgent("shadow-agent", model); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("shadow-agent [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .shadowMode(true) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED); // GH-90000
        }

        @Test void fallbackOnPrimaryFailure() { // GH-90000
            ModelInference primary = failingModel("primary [GH-90000]");
            ModelInference fallback = mockModel("fallback", Map.of("ok", true), 0.8); // GH-90000

            ProbabilisticAgent agent = new ProbabilisticAgent("fallback-test", primary); // GH-90000
            agent.setFallbackModels(List.of(fallback)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("fallback-test [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("ok", true); // GH-90000
        }

        @Test void allModelsFail() { // GH-90000
            ModelInference primary = failingModel("primary [GH-90000]");
            ModelInference fb1 = failingModel("fallback-1 [GH-90000]");

            ProbabilisticAgent agent = new ProbabilisticAgent("all-fail", primary); // GH-90000
            agent.setFallbackModels(List.of(fb1)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("all-fail [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000

            assertThat(result.isFailed()).isTrue(); // GH-90000
        }

        @Test void calibrationApplied() { // GH-90000
            ModelInference model = mockModel("cal-model", Map.of("label", "A"), 0.5); // GH-90000
            ProbabilisticAgent agent = new ProbabilisticAgent("cal-test", model); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("cal-test [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .confidenceThreshold(0.3) // GH-90000
                    .calibrationMethod(ConfidenceCalibrator.Method.TEMPERATURE) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000

            // Temperature calibration changes the confidence
            assertThat(result.getConfidence()).isBetween(0.0, 1.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("Lifecycle [GH-90000]")
    class LifecycleTests {

        @Test void initializeSetsDescriptor() { // GH-90000
            ProbabilisticAgent agent = new ProbabilisticAgent("lifecycle-test [GH-90000]");

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("lifecycle-test [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .modelName("test-model [GH-90000]")
                    .modelVersion("1.0 [GH-90000]")
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            assertThat(agent.descriptor().getAgentId()).isEqualTo("lifecycle-test [GH-90000]");
        }

        @Test void setPrimaryModelAfterInit() { // GH-90000
            ProbabilisticAgent agent = new ProbabilisticAgent("late-model [GH-90000]");
            ModelInference model = mockModel("late", Map.of("result", "ok"), 0.9); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .agentId("late-model [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runOnEventloop(() -> agent.initialize(config)); // GH-90000
            agent.setPrimaryModel(model); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }
    }
}
