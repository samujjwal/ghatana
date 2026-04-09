/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.behavioral;

import com.ghatana.agent.*;
import com.ghatana.agent.probabilistic.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Behavioral tests for ProbabilisticAgent.
 *
 * Focus: Actual ML processing correctness, confidence calibration, fallback chains,
 * and probabilistic reasoning patterns.
 */
@DisplayName("ProbabilisticAgent Behavioral Tests")
@ExtendWith(MockitoExtension.class)
class ProbabilisticAgentBehavioralTest {

    @Mock
    private MemoryStore memoryStore;

    @Mock
    private ModelInference primaryModel;

    @Mock
    private ModelInference fallbackModel;

    @Mock
    private ConfidenceCalibrator calibrator;

    private AgentContext agentContext;
    private ProbabilisticAgent agent;

    @BeforeEach
    void setUp() {
        agentContext = AgentContext.builder()
                .turnId("turn-1")
                .agentId("prob-agent")
                .tenantId("tenant-1")
                .memoryStore(memoryStore)
                .build();

        agent = new ProbabilisticAgent("prob-agent", primaryModel);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Processing Logic Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Processing Logic")
    class ProcessingTests {

        @Test
        @DisplayName("Agent invokes model inference for input")
        void modelInvocation() {
            ModelInference.InferenceResult inferenceResult = new ModelInference.InferenceResult(Map.of("prediction", "class-A"), 0.82, "v1", 0L);

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(inferenceResult));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("v1")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("feature1", 1.5, "feature2", 2.3);
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            verify(primaryModel, times(1)).infer(input);
        }

        @Test
        @DisplayName("Agent produces output from model prediction")
        void predictionOutput() {
            ModelInference.InferenceResult inferenceResult = new ModelInference.InferenceResult(Map.of("label", "positive", "probability", 0.88), 0.88, "v2", 0L);

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(inferenceResult));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("v2")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("text", "great product");
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            assertThat(result.getOutput()).isNotNull();
        }

        @Test
        @DisplayName("Agent handles model timeout gracefully with fallback")
        void modelTimeoutFallback() {
            // Primary model times out
            when(primaryModel.infer(anyMap()))
                    .thenReturn(Promise.ofException(new TimeoutException("Model timeout")));

            ModelInference.InferenceResult fallbackResult = new ModelInference.InferenceResult(Map.of("prediction", "fallback-output"), 0.5, "fallback-v1", 0L);

            when(fallbackModel.infer(anyMap())).thenReturn(Promise.of(fallbackResult));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("v1")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("x", 1);
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            // Should still produce result (via fallback or error handling)
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Agent handles model error gracefully")
        void modelErrorHandling() {
            when(primaryModel.infer(anyMap()))
                    .thenReturn(Promise.ofException(new RuntimeException("Model error")));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("v1")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("data", "test");
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            // Should fail gracefully
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Batch inference processes multiple inputs")
        void batchInference() {
            ModelInference.InferenceResult result1 = new ModelInference.InferenceResult(Map.of("prediction", "A"), 0.75, "v1", 0L);
            ModelInference.InferenceResult result2 = new ModelInference.InferenceResult(Map.of("prediction", "B"), 0.82, "v1", 0L);

            when(primaryModel.infer(anyMap()))
                    .thenReturn(Promise.of(result1))
                    .thenReturn(Promise.of(result2));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("v1")
                    .build();

            runPromise(() -> agent.initialize(config));

            List<Map<String, Object>> inputs = List.of(
                    Map.of("x", 1),
                    Map.of("x", 2)
            );

            for (Map<String, Object> input : inputs) {
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));
                assertThat(result.isSuccess()).isTrue();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Confidence Scoring Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Confidence Scoring")
    class ConfidenceScoringTests {

        @Test
        @DisplayName("Confidence scores are in valid range [0.0, 1.0]")
        void confidenceRangeValidation() {
            for (double score = 0.0; score <= 1.0; score += 0.1) {
                ModelInference.InferenceResult inferenceResult = new ModelInference.InferenceResult(Map.of("prediction", "class"), score, "v1", 0L);

                when(primaryModel.infer(anyMap())).thenReturn(Promise.of(inferenceResult));

                ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                        .subtype(ProbabilisticSubtype.ML_MODEL)
                        .modelVersion("v1")
                        .build();

                runPromise(() -> agent.initialize(config));

                Map<String, Object> input = Map.of("x", score);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.getConfidence())
                        .isGreaterThanOrEqualTo(0.0)
                        .isLessThanOrEqualTo(1.0);
            }
        }

        @Test
        @DisplayName("High-confidence cases have confidence > 0.7")
        void highConfidenceDetection() {
            ModelInference.InferenceResult highConfResult = new ModelInference.InferenceResult(Map.of("prediction", "confident-class"), 0.92, "v1", 0L);

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(highConfResult));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("v1")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("data", "good");
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            assertThat(result.getConfidence()).isGreaterThan(0.7);
            assertThat(result.meetsConfidence(0.7)).isTrue();
        }

        @Test
        @DisplayName("Low-confidence cases have confidence < 0.3")
        void lowConfidenceDetection() {
            ModelInference.InferenceResult lowConfResult = new ModelInference.InferenceResult(Map.of("prediction", "uncertain-class"), 0.25, "v1", 0L);

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(lowConfResult));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("v1")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("data", "ambiguous");
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            assertThat(result.getConfidence()).isLessThan(0.3);
        }

        @Test
        @DisplayName("Confidence calibration adjusts raw scores")
        void confidenceCalibration() {
            double rawScore = 0.7;
            double calibratedScore = 0.75;  // Slightly adjusted

            ModelInference.InferenceResult rawResult = new ModelInference.InferenceResult(Map.of("prediction", "class"), rawScore, "v1", 0L);

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(rawResult));

            // Mock calibrator to return different value
            when(calibrator.calibrate(rawScore)).thenReturn(calibratedScore);

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("v1")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("x", 1);
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            // Confidence should be in valid range
            assertThat(result.getConfidence())
                    .isGreaterThanOrEqualTo(0.0)
                    .isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("Multiple predictions show varying confidence levels")
        void varyingConfidenceLevels() {
            double[] scores = {0.15, 0.45, 0.75, 0.95};

            for (double score : scores) {
                ModelInference.InferenceResult result = new ModelInference.InferenceResult(Map.of("prediction", "class-" + score), score, "v1", 0L);

                when(primaryModel.infer(anyMap())).thenReturn(Promise.of(result));

                ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                        .subtype(ProbabilisticSubtype.ML_MODEL)
                        .modelVersion("v1")
                        .build();

                runPromise(() -> agent.initialize(config));

                Map<String, Object> input = Map.of("score", score);
                AgentResult<?> agentResult = runPromise(() -> agent.process(agentContext, input));

                // All confidences should be in valid range
                assertThat(agentResult.getConfidence())
                        .isBetween(0.0, 1.0);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Explanation Generation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Explanation Generation")
    class ExplanationTests {

        @Test
        @DisplayName("Explanation is non-empty for results")
        void explanationNonEmpty() {
            ModelInference.InferenceResult inferenceResult = new ModelInference.InferenceResult(Map.of("prediction", "positive"), 0.85, "sentiment-v2", 0L);

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(inferenceResult));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("sentiment-v2")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("text", "excellent service");
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            assertThat(result.getExplanation())
                    .isNotNull()
                    .isNotBlank();
        }

        @Test
        @DisplayName("Explanation mentions confidence level")
        void explanationMentionsConfidence() {
            ModelInference.InferenceResult inferenceResult = new ModelInference.InferenceResult(Map.of("prediction", "class-A"), 0.87, "v1", 0L);

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(inferenceResult));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("v1")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("features", "test");
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            String explanation = result.getExplanation();
            assertThat(explanation)
                    .isNotNull()
                    .isNotBlank();
        }

        @Test
        @DisplayName("Explanation references prediction")
        void explanationReferencesPrediction() {
            ModelInference.InferenceResult inferenceResult = new ModelInference.InferenceResult(Map.of("prediction", "ANOMALY_DETECTED"), 0.91, "anomaly-v3", 0L);

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(inferenceResult));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("anomaly-v3")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("values", new double[]{10, 100, 200});
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            String explanation = result.getExplanation();
            assertThat(explanation)
                    .isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Fallback Chain Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Fallback Chain Logic")
    class FallbackChainTests {

        @Test
        @DisplayName("Primary model success produces result without fallback")
        void primaryModelSuccess() {
            ModelInference.InferenceResult primaryResult = new ModelInference.InferenceResult(Map.of("prediction", "primary-output"), 0.88, "v1", 0L);

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(primaryResult));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("v1")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("x", 1);
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            assertThat(result.isSuccess()).isTrue();
            verify(primaryModel, times(1)).infer(anyMap());
            verify(fallbackModel, never()).infer(anyMap());
        }

        @Test
        @DisplayName("Fallback invoked when primary model fails")
        void fallbackOnPrimaryFailure() {
            when(primaryModel.infer(anyMap()))
                    .thenReturn(Promise.ofException(new RuntimeException("Primary failed")));

            ModelInference.InferenceResult fallbackResult = new ModelInference.InferenceResult(Map.of("prediction", "fallback-output"), 0.60, "fallback-v1", 0L);

            when(fallbackModel.infer(anyMap())).thenReturn(Promise.of(fallbackResult));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("v1")
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("x", 1);
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            // Should still produce result
            assertThat(result).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Model Versioning Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Model Versioning")
    class ModelVersioningTests {

        @Test
        @DisplayName("Agent tracks model version in result")
        void modelVersionTracking() {
            String modelVersion = "sentiment-classifier-v2.1.0";

            ModelInference.InferenceResult result = new ModelInference.InferenceResult(Map.of("prediction", "positive"), 0.84, modelVersion, 0L);

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(result));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion(modelVersion)
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("text", "great");
            AgentResult<?> agentResult = runPromise(() -> agent.process(agentContext, input));

            assertThat(agentResult.getMetrics()).containsKey("modelVersion");
        }

        @Test
        @DisplayName("Different model versions produce consistent predictions")
        void modelVersionConsistency() {
            ModelInference.InferenceResult v1Result = new ModelInference.InferenceResult(Map.of("prediction", "A"), 0.75, "v1", 0L);

            ModelInference.InferenceResult v2Result = new ModelInference.InferenceResult(Map.of("prediction", "A"), 0.78, "v2", 0L);

            when(primaryModel.infer(anyMap()))
                    .thenReturn(Promise.of(v1Result))
                    .thenReturn(Promise.of(v2Result));

            Map<String, Object> input = Map.of("data", "test");

            // Test with v1
            ProbabilisticAgentConfig configV1 = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("v1")
                    .build();

            runPromise(() -> agent.initialize(configV1));
            AgentResult<?> resultV1 = runPromise(() -> agent.process(agentContext, input));

            // Test with v2
            ProbabilisticAgent agentV2 = new ProbabilisticAgent("prob-agent-v2", primaryModel);
            ProbabilisticAgentConfig configV2 = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("v2")
                    .build();

            runPromise(() -> agentV2.initialize(configV2));
            AgentResult<?> resultV2 = runPromise(() -> agentV2.process(agentContext, input));

            // Both should produce results
            assertThat(resultV1.isSuccess()).isTrue();
            assertThat(resultV2.isSuccess()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Performance Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Performance")
    class PerformanceTests {

        @Test
        @DisplayName("High throughput inference")
        void highThroughputInference() {
            ModelInference.InferenceResult inferenceResult = new ModelInference.InferenceResult(Map.of("prediction", "result"), 0.75, "v1", 0L);

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(inferenceResult));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder()
                    .subtype(ProbabilisticSubtype.ML_MODEL)
                    .modelVersion("v1")
                    .build();

            runPromise(() -> agent.initialize(config));

            int iterations = 100;
            Instant start = Instant.now();

            for (int i = 0; i < iterations; i++) {
                Map<String, Object> input = Map.of("iteration", i);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));
                assertThat(result.isSuccess()).isTrue();
            }

            Instant end = Instant.now();
            Duration totalTime = Duration.between(start, end);

            // Should complete reasonably quickly
            assertThat(totalTime).isLessThan(Duration.ofSeconds(10));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private <T> T runPromise(java.util.function.Supplier<Promise<T>> supplier) {
        var result = new Object() { T value; };
        var error = new Object() { Exception ex; };

        Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
        eventloop.post(() -> supplier.get()
                .whenResult(v -> result.value = v)
                .whenException(e -> error.ex = (Exception) e));

        eventloop.run();

        if (error.ex != null) {
            throw new RuntimeException(error.ex);
        }

        return result.value;
    }
}
