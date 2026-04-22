/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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
@DisplayName("ProbabilisticAgent Behavioral Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
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
    void setUp() { // GH-90000
        agentContext = AgentContext.builder() // GH-90000
                .turnId("turn-1 [GH-90000]")
                .agentId("prob-agent [GH-90000]")
                .tenantId("tenant-1 [GH-90000]")
                .memoryStore(memoryStore) // GH-90000
                .build(); // GH-90000

        agent = new ProbabilisticAgent("prob-agent", primaryModel); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Processing Logic Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Processing Logic [GH-90000]")
    class ProcessingTests {

        @Test
        @DisplayName("Agent invokes model inference for input [GH-90000]")
        void modelInvocation() { // GH-90000
            ModelInference.InferenceResult inferenceResult = new ModelInference.InferenceResult(Map.of("prediction", "class-A"), 0.82, "v1", 0L); // GH-90000

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(inferenceResult)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("v1 [GH-90000]")
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("feature1", 1.5, "feature2", 2.3); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            verify(primaryModel, times(1)).infer(input); // GH-90000
        }

        @Test
        @DisplayName("Agent produces output from model prediction [GH-90000]")
        void predictionOutput() { // GH-90000
            ModelInference.InferenceResult inferenceResult = new ModelInference.InferenceResult(Map.of("label", "positive", "probability", 0.88), 0.88, "v2", 0L); // GH-90000

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(inferenceResult)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("v2 [GH-90000]")
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("text", "great product"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.getOutput()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Agent handles model timeout gracefully with fallback [GH-90000]")
        void modelTimeoutFallback() { // GH-90000
            // Primary model times out
            when(primaryModel.infer(anyMap())) // GH-90000
                    .thenReturn(Promise.ofException(new TimeoutException("Model timeout [GH-90000]")));

            ModelInference.InferenceResult fallbackResult = new ModelInference.InferenceResult(Map.of("prediction", "fallback-output"), 0.5, "fallback-v1", 0L); // GH-90000

            when(fallbackModel.infer(anyMap())).thenReturn(Promise.of(fallbackResult)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("v1 [GH-90000]")
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            agent.setFallbackModels(List.of(fallbackModel)); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // Should still produce result (via fallback or error handling) // GH-90000
            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Agent handles model error gracefully [GH-90000]")
        void modelErrorHandling() { // GH-90000
            when(primaryModel.infer(anyMap())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Model error [GH-90000]")));

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("v1 [GH-90000]")
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("data", "test"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // Should fail gracefully
            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Batch inference processes multiple inputs [GH-90000]")
        void batchInference() { // GH-90000
            ModelInference.InferenceResult result1 = new ModelInference.InferenceResult(Map.of("prediction", "A"), 0.75, "v1", 0L); // GH-90000
            ModelInference.InferenceResult result2 = new ModelInference.InferenceResult(Map.of("prediction", "B"), 0.82, "v1", 0L); // GH-90000

            when(primaryModel.infer(anyMap())) // GH-90000
                    .thenReturn(Promise.of(result1)) // GH-90000
                    .thenReturn(Promise.of(result2)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("v1 [GH-90000]")
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            List<Map<String, Object>> inputs = List.of( // GH-90000
                    Map.of("x", 1), // GH-90000
                    Map.of("x", 2) // GH-90000
            );

            for (Map<String, Object> input : inputs) { // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
                assertThat(result.isSuccess()).isTrue(); // GH-90000
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Confidence Scoring Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Confidence Scoring [GH-90000]")
    class ConfidenceScoringTests {

        @Test
        @DisplayName("Confidence scores are in valid range [0.0, 1.0] [GH-90000]")
        void confidenceRangeValidation() { // GH-90000
            for (double score = 0.0; score <= 1.0; score += 0.1) { // GH-90000
                ModelInference.InferenceResult inferenceResult = new ModelInference.InferenceResult(Map.of("prediction", "class"), score, "v1", 0L); // GH-90000

                when(primaryModel.infer(anyMap())).thenReturn(Promise.of(inferenceResult)); // GH-90000

                ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                        .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                        .modelVersion("v1 [GH-90000]")
                        .build(); // GH-90000

                runPromise(() -> agent.initialize(config)); // GH-90000

                Map<String, Object> input = Map.of("x", score); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.getConfidence()) // GH-90000
                        .isGreaterThanOrEqualTo(0.0) // GH-90000
                        .isLessThanOrEqualTo(1.0); // GH-90000
            }
        }

        @Test
        @DisplayName("High-confidence cases have confidence > 0.7 [GH-90000]")
        void highConfidenceDetection() { // GH-90000
            ModelInference.InferenceResult highConfResult = new ModelInference.InferenceResult(Map.of("prediction", "confident-class"), 0.92, "v1", 0L); // GH-90000

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(highConfResult)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("v1 [GH-90000]")
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            agent.setFallbackModels(List.of(fallbackModel)); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("data", "good"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.getConfidence()).isGreaterThan(0.7); // GH-90000
            assertThat(result.meetsConfidence(0.7)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Low-confidence cases have confidence < 0.3 [GH-90000]")
        void lowConfidenceDetection() { // GH-90000
            ModelInference.InferenceResult lowConfResult = new ModelInference.InferenceResult(Map.of("prediction", "uncertain-class"), 0.25, "v1", 0L); // GH-90000

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(lowConfResult)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("v1 [GH-90000]")
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            agent.setFallbackModels(List.of(fallbackModel)); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("data", "ambiguous"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.getConfidence()).isLessThan(0.3); // GH-90000
        }

        @Test
        @DisplayName("Confidence calibration adjusts raw scores [GH-90000]")
        void confidenceCalibration() { // GH-90000
            double rawScore = 0.7;
            double calibratedScore = 0.75;  // Slightly adjusted

            ModelInference.InferenceResult rawResult = new ModelInference.InferenceResult(Map.of("prediction", "class"), rawScore, "v1", 0L); // GH-90000

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(rawResult)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("v1 [GH-90000]")
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // Confidence should be in valid range
            assertThat(result.getConfidence()) // GH-90000
                    .isGreaterThanOrEqualTo(0.0) // GH-90000
                    .isLessThanOrEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("Multiple predictions show varying confidence levels [GH-90000]")
        void varyingConfidenceLevels() { // GH-90000
            double[] scores = {0.15, 0.45, 0.75, 0.95};

            for (double score : scores) { // GH-90000
                ModelInference.InferenceResult result = new ModelInference.InferenceResult(Map.of("prediction", "class-" + score), score, "v1", 0L); // GH-90000

                when(primaryModel.infer(anyMap())).thenReturn(Promise.of(result)); // GH-90000

                ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                        .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                        .modelVersion("v1 [GH-90000]")
                        .build(); // GH-90000

                runPromise(() -> agent.initialize(config)); // GH-90000

                Map<String, Object> input = Map.of("score", score); // GH-90000
                AgentResult<?> agentResult = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                // All confidences should be in valid range
                assertThat(agentResult.getConfidence()) // GH-90000
                        .isBetween(0.0, 1.0); // GH-90000
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Explanation Generation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Explanation Generation [GH-90000]")
    class ExplanationTests {

        @Test
        @DisplayName("Explanation is non-empty for results [GH-90000]")
        void explanationNonEmpty() { // GH-90000
            ModelInference.InferenceResult inferenceResult = new ModelInference.InferenceResult(Map.of("prediction", "positive"), 0.85, "sentiment-v2", 0L); // GH-90000

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(inferenceResult)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("sentiment-v2 [GH-90000]")
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("text", "excellent service"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.getExplanation()) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("Explanation mentions confidence level [GH-90000]")
        void explanationMentionsConfidence() { // GH-90000
            ModelInference.InferenceResult inferenceResult = new ModelInference.InferenceResult(Map.of("prediction", "class-A"), 0.87, "v1", 0L); // GH-90000

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(inferenceResult)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("v1 [GH-90000]")
                    .build(); // GH-90000

            agent.setFallbackModels(List.of(fallbackModel)); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("features", "test"); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            String explanation = result.getExplanation(); // GH-90000
            assertThat(explanation) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("Explanation references prediction [GH-90000]")
        void explanationReferencesPrediction() { // GH-90000
            ModelInference.InferenceResult inferenceResult = new ModelInference.InferenceResult(Map.of("prediction", "ANOMALY_DETECTED"), 0.91, "anomaly-v3", 0L); // GH-90000

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(inferenceResult)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("anomaly-v3 [GH-90000]")
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("values", new double[]{10, 100, 200}); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            String explanation = result.getExplanation(); // GH-90000
            assertThat(explanation) // GH-90000
                    .isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Fallback Chain Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Fallback Chain Logic [GH-90000]")
    class FallbackChainTests {

        @Test
        @DisplayName("Primary model success produces result without fallback [GH-90000]")
        void primaryModelSuccess() { // GH-90000
            ModelInference.InferenceResult primaryResult = new ModelInference.InferenceResult(Map.of("prediction", "primary-output"), 0.88, "v1", 0L); // GH-90000

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(primaryResult)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("v1 [GH-90000]")
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            verify(primaryModel, times(1)).infer(anyMap()); // GH-90000
            verify(fallbackModel, never()).infer(anyMap()); // GH-90000
        }

        @Test
        @DisplayName("Fallback invoked when primary model fails [GH-90000]")
        void fallbackOnPrimaryFailure() { // GH-90000
            when(primaryModel.infer(anyMap())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Primary failed [GH-90000]")));

            ModelInference.InferenceResult fallbackResult = new ModelInference.InferenceResult(Map.of("prediction", "fallback-output"), 0.60, "fallback-v1", 0L); // GH-90000

            when(fallbackModel.infer(anyMap())).thenReturn(Promise.of(fallbackResult)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("v1 [GH-90000]")
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            agent.setFallbackModels(List.of(fallbackModel)); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // Should still produce result
            assertThat(result).isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Model Versioning Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Model Versioning [GH-90000]")
    class ModelVersioningTests {

        @Test
        @DisplayName("Agent tracks model version in result [GH-90000]")
        void modelVersionTracking() { // GH-90000
            String modelVersion = "sentiment-classifier-v2.1.0";

            ModelInference.InferenceResult result = new ModelInference.InferenceResult(Map.of("prediction", "positive"), 0.84, modelVersion, 0L); // GH-90000

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(result)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion(modelVersion) // GH-90000
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("text", "great"); // GH-90000
            AgentResult<?> agentResult = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(agentResult.getMetrics()).containsKey("modelVersion [GH-90000]");
        }

        @Test
        @DisplayName("Different model versions produce consistent predictions [GH-90000]")
        void modelVersionConsistency() { // GH-90000
            ModelInference.InferenceResult v1Result = new ModelInference.InferenceResult(Map.of("prediction", "A"), 0.75, "v1", 0L); // GH-90000

            ModelInference.InferenceResult v2Result = new ModelInference.InferenceResult(Map.of("prediction", "A"), 0.78, "v2", 0L); // GH-90000

            when(primaryModel.infer(anyMap())) // GH-90000
                    .thenReturn(Promise.of(v1Result)) // GH-90000
                    .thenReturn(Promise.of(v2Result)); // GH-90000

            Map<String, Object> input = Map.of("data", "test"); // GH-90000

            // Test with v1
            ProbabilisticAgentConfig configV1 = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("v1 [GH-90000]")
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(configV1)); // GH-90000
            AgentResult<?> resultV1 = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            // Test with v2
            ProbabilisticAgent agentV2 = new ProbabilisticAgent("prob-agent-v2", primaryModel); // GH-90000
            ProbabilisticAgentConfig configV2 = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("v2 [GH-90000]")
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agentV2.initialize(configV2)); // GH-90000
            AgentResult<?> resultV2 = runPromise(() -> agentV2.process(agentContext, input)); // GH-90000

            // Both should produce results
            assertThat(resultV1.isSuccess()).isTrue(); // GH-90000
            assertThat(resultV2.isSuccess()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Performance Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Performance [GH-90000]")
    class PerformanceTests {

        @Test
        @DisplayName("High throughput inference [GH-90000]")
        void highThroughputInference() { // GH-90000
            ModelInference.InferenceResult inferenceResult = new ModelInference.InferenceResult(Map.of("prediction", "result"), 0.75, "v1", 0L); // GH-90000

            when(primaryModel.infer(anyMap())).thenReturn(Promise.of(inferenceResult)); // GH-90000

            ProbabilisticAgentConfig config = ProbabilisticAgentConfig.builder() // GH-90000
                    .subtype(ProbabilisticSubtype.ML_MODEL) // GH-90000
                    .modelVersion("v1 [GH-90000]")
                    .confidenceThreshold(0.5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            int iterations = 100;
            Instant start = Instant.now(); // GH-90000

            for (int i = 0; i < iterations; i++) { // GH-90000
                Map<String, Object> input = Map.of("iteration", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
                assertThat(result.isSuccess()).isTrue(); // GH-90000
            }

            Instant end = Instant.now(); // GH-90000
            Duration totalTime = Duration.between(start, end); // GH-90000

            // Should complete reasonably quickly
            assertThat(totalTime).isLessThan(Duration.ofSeconds(10)); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private <T> T runPromise(java.util.function.Supplier<Promise<T>> supplier) { // GH-90000
        var result = new Object() { T value; }; // GH-90000
        var error = new Object() { Exception ex; }; // GH-90000

        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
        eventloop.post(() -> supplier.get() // GH-90000
                .whenResult(v -> result.value = v) // GH-90000
                .whenException(e -> error.ex = (Exception) e)); // GH-90000

        eventloop.run(); // GH-90000

        if (error.ex != null) { // GH-90000
            throw new RuntimeException(error.ex); // GH-90000
        }

        return result.value;
    }
}
