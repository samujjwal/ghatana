/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.probabilistic;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Comprehensive tests for ProbabilisticAgent, ModelInference, ConfidenceCalibrator.
 */
@DisplayName("Probabilistic Agent")
class ProbabilisticAgentTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("test-agent")
                .tenantId("test-tenant")
                .memoryStore(mock(MemoryStore.class))
                .build();
    }

    private <T> T runOnEventloop(java.util.function.Supplier<Promise<T>> supplier) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
        eventloop.post(() -> supplier.get()
                .whenResult(result::set)
                .whenException(err::set));
        eventloop.run();
        if (err.get() != null) throw new RuntimeException(err.get());
        return result.get();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Mock ModelInference
    // ═══════════════════════════════════════════════════════════════════════════

    private ModelInference mockModel(String id, Map<String, Object> output, double confidence) {
        return new ModelInference() {
            @Override public @org.jetbrains.annotations.NotNull Promise<InferenceResult> infer(
                    @org.jetbrains.annotations.NotNull Map<String, Object> input) {
                return Promise.of(new InferenceResult(output, confidence, id, 10));
            }
            @Override public @org.jetbrains.annotations.NotNull String modelId() { return id; }
        };
    }

    private ModelInference failingModel(String id) {
        return new ModelInference() {
            @Override public @org.jetbrains.annotations.NotNull Promise<InferenceResult> infer(
                    @org.jetbrains.annotations.NotNull Map<String, Object> input) {
                return Promise.ofException(new RuntimeException("Model " + id + " failed"));
            }
            @Override public @org.jetbrains.annotations.NotNull String modelId() { return id; }
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ConfidenceCalibrator
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ConfidenceCalibrator")
    class CalibratorTests {

        @Test void identityReturnsRawValue() {
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder()
                    .method(ConfidenceCalibrator.Method.IDENTITY).build();
            assertThat(cal.calibrate(0.73)).isCloseTo(0.73, within(1e-9));
        }

        @Test void temperatureScaling() {
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder()
                    .method(ConfidenceCalibrator.Method.TEMPERATURE)
                    .temperature(2.0)
                    .build();
            double result = cal.calibrate(0.8);
            // temperature divides logit by T
            assertThat(result).isBetween(0.0, 1.0);
        }

        @Test void plattCalibration() {
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder()
                    .method(ConfidenceCalibrator.Method.PLATT)
                    .plattA(-1.0).plattB(0.0)
                    .build();
            double result = cal.calibrate(0.5);
            assertThat(result).isBetween(0.0, 1.0);
        }

        @Test void isotonicCalibration() {
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder()
                    .method(ConfidenceCalibrator.Method.ISOTONIC)
                    .isotonicBreakpoints(new double[]{0.0, 0.5, 1.0})
                    .isotonicValues(new double[]{0.1, 0.6, 0.95})
                    .build();
            double result = cal.calibrate(0.75);
            assertThat(result).isBetween(0.6, 0.95);
        }

        @Test void clampsToUnitInterval() {
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder()
                    .method(ConfidenceCalibrator.Method.IDENTITY).build();
            assertThat(cal.calibrate(-0.5)).isCloseTo(0.0, within(1e-9));
            assertThat(cal.calibrate(1.5)).isCloseTo(1.0, within(1e-9));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ProbabilisticAgent
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Agent Processing")
    class ProcessingTests {

        @Test void basicInference() {
            ModelInference model = mockModel("gpt-4", Map.of("label", "positive"), 0.95);
            ProbabilisticAgent agent = new ProbabilisticAgent("ml-agent", model);

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("ml-agent")
                    .type(AgentType.PROBABILISTIC)
                    .confidenceThreshold(0.5)
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            AgentResult<Map<String, Object>> result =
                    runOnEventloop(() -> agent.process(ctx, Map.of("text", "great")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("label", "positive");
            assertThat(result.getConfidence()).isGreaterThan(0.5);
        }

        @Test void lowConfidenceMarkedCorrectly() {
            ModelInference model = mockModel("weak-model", Map.of("label", "unknown"), 0.3);
            ProbabilisticAgent agent = new ProbabilisticAgent("low-conf", model);

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("low-conf")
                    .type(AgentType.PROBABILISTIC)
                    .confidenceThreshold(0.85)
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.LOW_CONFIDENCE);
        }

        @Test void shadowModeReturnsSkipped() {
            ModelInference model = mockModel("shadow", Map.of("result", "test"), 0.9);
            ProbabilisticAgent agent = new ProbabilisticAgent("shadow-agent", model);

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("shadow-agent")
                    .type(AgentType.PROBABILISTIC)
                    .shadowMode(true)
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);
        }

        @Test void fallbackOnPrimaryFailure() {
            ModelInference primary = failingModel("primary");
            ModelInference fallback = mockModel("fallback", Map.of("ok", true), 0.8);

            ProbabilisticAgent agent = new ProbabilisticAgent("fallback-test", primary);
            agent.setFallbackModels(List.of(fallback));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("fallback-test")
                    .type(AgentType.PROBABILISTIC)
                    .confidenceThreshold(0.5)
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("ok", true);
        }

        @Test void allModelsFail() {
            ModelInference primary = failingModel("primary");
            ModelInference fb1 = failingModel("fallback-1");

            ProbabilisticAgent agent = new ProbabilisticAgent("all-fail", primary);
            agent.setFallbackModels(List.of(fb1));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("all-fail")
                    .type(AgentType.PROBABILISTIC)
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));

            assertThat(result.isFailed()).isTrue();
        }

        @Test void calibrationApplied() {
            ModelInference model = mockModel("cal-model", Map.of("label", "A"), 0.5);
            ProbabilisticAgent agent = new ProbabilisticAgent("cal-test", model);

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("cal-test")
                    .type(AgentType.PROBABILISTIC)
                    .confidenceThreshold(0.3)
                    .calibrationMethod(ConfidenceCalibrator.Method.TEMPERATURE)
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));

            // Temperature calibration changes the confidence
            assertThat(result.getConfidence()).isBetween(0.0, 1.0);
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test void initializeSetsDescriptor() {
            ProbabilisticAgent agent = new ProbabilisticAgent("lifecycle-test");

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("lifecycle-test")
                    .type(AgentType.PROBABILISTIC)
                    .modelName("test-model")
                    .modelVersion("1.0")
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            assertThat(agent.descriptor().getAgentId()).isEqualTo("lifecycle-test");
        }

        @Test void setPrimaryModelAfterInit() {
            ProbabilisticAgent agent = new ProbabilisticAgent("late-model");
            ModelInference model = mockModel("late", Map.of("result", "ok"), 0.9);

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("late-model")
                    .type(AgentType.PROBABILISTIC)
                    .confidenceThreshold(0.5)
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            agent.setPrimaryModel(model);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            assertThat(result.isSuccess()).isTrue();
        }
    }
}
