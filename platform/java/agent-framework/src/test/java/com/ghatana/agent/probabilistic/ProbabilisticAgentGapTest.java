/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 * Phase 4 — Task 4.1: Gap-filling tests for ProbabilisticAgent.
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
 * Gap-filling tests for {@link ProbabilisticAgent}.
 *
 * <p>Fills gaps identified in Phase 4 audit:
 * <ul>
 *   <li>Model inference timeout (simulated via exception)</li>
 *   <li>Batch inference (processBatch from AbstractTypedAgent)</li>
 *   <li>Fallback chain exhaustion with 2+ fallbacks</li>
 *   <li>Shadow mode records metrics but returns SKIPPED</li>
 *   <li>No model set → failure</li>
 *   <li>Calibration edge cases (extreme raw values)</li>
 * </ul>
 */
@DisplayName("Probabilistic Agent — Gap Tests")
class ProbabilisticAgentGapTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("prob-gap-test")
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

    // ── Mock helpers ────────────────────────────────────────────────────────

    private ModelInference mockModel(String id, Map<String, Object> output, double confidence) {
        return new ModelInference() {
            @Override
            public @org.jetbrains.annotations.NotNull Promise<InferenceResult> infer(
                    @org.jetbrains.annotations.NotNull Map<String, Object> input) {
                return Promise.of(new InferenceResult(output, confidence, id, 10));
            }
            @Override
            public @org.jetbrains.annotations.NotNull String modelId() { return id; }
        };
    }

    private ModelInference failingModel(String id) {
        return new ModelInference() {
            @Override
            public @org.jetbrains.annotations.NotNull Promise<InferenceResult> infer(
                    @org.jetbrains.annotations.NotNull Map<String, Object> input) {
                return Promise.ofException(new RuntimeException("Model " + id + " failed"));
            }
            @Override
            public @org.jetbrains.annotations.NotNull String modelId() { return id; }
        };
    }

    private ModelInference timeoutModel(String id) {
        return new ModelInference() {
            @Override
            public @org.jetbrains.annotations.NotNull Promise<InferenceResult> infer(
                    @org.jetbrains.annotations.NotNull Map<String, Object> input) {
                return Promise.ofException(new java.util.concurrent.TimeoutException(
                        "Model " + id + " timed out after 500ms"));
            }
            @Override
            public @org.jetbrains.annotations.NotNull String modelId() { return id; }
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Model inference timeout
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Model Inference Timeout")
    class TimeoutTests {

        @Test
        void timeoutFallsToFallbackModel() {
            ModelInference primary = timeoutModel("slow-primary");
            ModelInference fallback = mockModel("fast-fallback", Map.of("label", "ok"), 0.85);

            ProbabilisticAgent agent = new ProbabilisticAgent("timeout-fb", primary);
            agent.setFallbackModels(List.of(fallback));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("timeout-fb")
                    .type(AgentType.PROBABILISTIC)
                    .confidenceThreshold(0.5)
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("label", "ok");
        }

        @Test
        void timeoutWithNoFallbackReturnsFailed() {
            ModelInference primary = timeoutModel("slow-only");

            ProbabilisticAgent agent = new ProbabilisticAgent("timeout-no-fb", primary);
            // No fallback models set

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("timeout-no-fb")
                    .type(AgentType.PROBABILISTIC)
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));

            assertThat(result.isFailed()).isTrue();
        }

        @Test
        void timeoutChainsThroughMultipleFallbacks() {
            ModelInference primary = timeoutModel("primary");
            ModelInference fb1 = failingModel("fallback-1");
            ModelInference fb2 = mockModel("fallback-2", Map.of("recovered", true), 0.75);

            ProbabilisticAgent agent = new ProbabilisticAgent("chain-fb", primary);
            agent.setFallbackModels(List.of(fb1, fb2));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("chain-fb")
                    .type(AgentType.PROBABILISTIC)
                    .confidenceThreshold(0.5)
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("recovered", true);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Batch inference
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Batch Inference")
    class BatchInferenceTests {

        @Test
        void processBatchExecutesForEachInput() {
            ModelInference model = mockModel("batch-model", Map.of("label", "pos"), 0.9);
            ProbabilisticAgent agent = new ProbabilisticAgent("batch-test", model);

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("batch-test")
                    .type(AgentType.PROBABILISTIC)
                    .confidenceThreshold(0.5)
                    .build();

            runOnEventloop(() -> agent.initialize(config));

            List<Map<String, Object>> inputs = List.of(
                    Map.of("text", "good"),
                    Map.of("text", "great"),
                    Map.of("text", "excellent"));

            List<AgentResult<Map<String, Object>>> results =
                    runOnEventloop(() -> agent.processBatch(ctx, inputs));

            assertThat(results).hasSize(3);
            for (AgentResult<Map<String, Object>> r : results) {
                assertThat(r.isSuccess()).isTrue();
                assertThat(r.getOutput()).containsEntry("label", "pos");
            }
        }

        @Test
        void processBatchWithEmptyListReturnsEmpty() {
            ModelInference model = mockModel("batch-empty", Map.of("ok", true), 0.9);
            ProbabilisticAgent agent = new ProbabilisticAgent("batch-empty", model);

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("batch-empty")
                    .type(AgentType.PROBABILISTIC)
                    .confidenceThreshold(0.5)
                    .build();

            runOnEventloop(() -> agent.initialize(config));

            List<AgentResult<Map<String, Object>>> results =
                    runOnEventloop(() -> agent.processBatch(ctx, List.of()));

            assertThat(results).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Shadow mode — enriched observation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Shadow Mode — enriched")
    class ShadowModeEnrichedTests {

        @Test
        void shadowModeOutputContainsModelMetadata() {
            ModelInference model = mockModel("shadow-model", Map.of("label", "A"), 0.92);
            ProbabilisticAgent agent = new ProbabilisticAgent("shadow-enrich", model);

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("shadow-enrich")
                    .type(AgentType.PROBABILISTIC)
                    .shadowMode(true)
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SKIPPED);
            // Even in shadow, output should contain model metadata
            assertThat(result.getOutput()).containsEntry("_model.id", "shadow-model");
            assertThat(result.getOutput()).containsKey("_model.calibratedConfidence");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // No model set
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("No model set")
    class NoModelTests {

        @Test
        void processWithoutPrimaryModelReturnsFailed() {
            ProbabilisticAgent agent = new ProbabilisticAgent("no-model");

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .agentId("no-model")
                    .type(AgentType.PROBABILISTIC)
                    .build();

            runOnEventloop(() -> agent.initialize(config));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));

            assertThat(result.isFailed()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Calibration edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Calibration edge cases")
    class CalibrationEdgeCases {

        @Test
        void extremelyHighRawConfidenceClampedToOne() {
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder()
                    .method(ConfidenceCalibrator.Method.IDENTITY).build();
            assertThat(cal.calibrate(100.0)).isCloseTo(1.0, within(1e-9));
        }

        @Test
        void negativeRawConfidenceClampedToZero() {
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder()
                    .method(ConfidenceCalibrator.Method.IDENTITY).build();
            assertThat(cal.calibrate(-50.0)).isCloseTo(0.0, within(1e-9));
        }

        @Test
        void temperatureScalingWithVeryHighTemperatureCompresses() {
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder()
                    .method(ConfidenceCalibrator.Method.TEMPERATURE)
                    .temperature(100.0)
                    .build();
            double result = cal.calibrate(0.99);
            // Very high temperature compresses confidence toward zero
            assertThat(result).isBetween(0.0, 0.1);
        }

        @Test
        void temperatureScalingWithVeryLowTemperaturePolarizes() {
            ConfidenceCalibrator cal = ConfidenceCalibrator.builder()
                    .method(ConfidenceCalibrator.Method.TEMPERATURE)
                    .temperature(0.01)
                    .build();
            // 0.99 → logit is very positive → dividing by 0.01 makes it huge → sigmoid ≈ 1.0
            double result = cal.calibrate(0.99);
            assertThat(result).isGreaterThan(0.99);
        }
    }
}
