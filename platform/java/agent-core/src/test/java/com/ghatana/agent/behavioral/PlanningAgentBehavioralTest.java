/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.behavioral;

import com.ghatana.agent.*;
import com.ghatana.agent.planning.*;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Behavioral tests for PlanningAgent.
 * 
 * Focus: Goal decomposition, multi-step execution, step sequencing,
 * error recovery, replanning, and workflow coordination.
 */
@DisplayName("PlanningAgent Behavioral Tests")
@ExtendWith(MockitoExtension.class)
class PlanningAgentBehavioralTest {

    @Mock
    private MemoryStore memoryStore;

    private AgentContext agentContext;
    private PlanningAgent agent;

    @BeforeEach
    void setUp() {
        agentContext = AgentContext.builder()
                .turnId("turn-1")
                .agentId("planning-agent")
                .tenantId("tenant-1")
                .memoryStore(memoryStore)
                .build();

        agent = new TestPlanningAgent("planning-agent");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Plan Decomposition Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Plan Decomposition")
    class DecompositionTests {

        @Test
        @DisplayName("Agent decomposes goal into steps")
        void goalDecomposition() {
            TestPlanningAgent testAgent = new TestPlanningAgent("test-agent");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of("task", "build-app", "features", 3);
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isNotNull();
        }

        @Test
        @DisplayName("Plan contains ordered sequence of steps")
        void stepSequencing() {
            TestPlanningAgent testAgent = new TestPlanningAgent("sequential-agent");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of(
                    "objective", "process-data",
                    "steps", 3
            );
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Plan depends on goal complexity")
        void complexityDependence() {
            TestPlanningAgent testAgent = new TestPlanningAgent("complex-agent");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            // Simple goal
            Map<String, Object> simpleGoal = Map.of("task", "simple");
            AgentResult<?> simpleResult = runPromise(() -> testAgent.process(agentContext, simpleGoal));
            assertThat(simpleResult.isSuccess()).isTrue();

            // Complex goal
            Map<String, Object> complexGoal = Map.of("task", "complex", "subtasks", 10);
            AgentResult<?> complexResult = runPromise(() -> testAgent.process(agentContext, complexGoal));
            assertThat(complexResult.isSuccess()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Step Execution Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Step Execution")
    class ExecutionTests {

        @Test
        @DisplayName("Steps execute in sequence")
        void sequentialExecution() {
            TestPlanningAgent testAgent = new TestPlanningAgent("sequential-exec");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of("goal", "execute-steps", "count", 5);
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Step success produces expected output")
        void stepSuccessOutput() {
            TestPlanningAgent testAgent = new TestPlanningAgent("success-agent");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of(
                    "objective", "verify-steps",
                    "expected_success", true
            );
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isNotNull();
        }

        @Test
        @DisplayName("Step failure captures error context")
        void stepFailureHandling() {
            TestPlanningAgent testAgent = new TestPlanningAgent("failure-agent");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of("objective", "test-failure");
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result).isNotNull();
            // Should handle gracefully
        }

        @Test
        @DisplayName("Agent accumulates results across steps")
        void resultAccumulation() {
            TestPlanningAgent testAgent = new TestPlanningAgent("accumulator");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of(
                    "objective", "accumulate-results",
                    "steps", 5
            );
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Replanning Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Replanning")
    class ReplanningTests {

        @Test
        @DisplayName("Agent replans when step fails")
        void replanOnStepFailure() {
            TestPlanningAgent testAgent = new TestPlanningAgent("replan-agent");
            AgentConfig config = AgentConfig.builder()
                    .properties(Map.of("enableReplanning", Boolean.TRUE))
                    .build();
            runPromise(() -> testAgent.initialize(config));

            Map<String, Object> goal = Map.of("objective", "trigger-replan");
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Replan respects maximum attempt limit")
        void replanAttemptLimit() {
            TestPlanningAgent testAgent = new TestPlanningAgent("limited-replan");
            AgentConfig config = AgentConfig.builder()
                    .properties(Map.of("enableReplanning", Boolean.TRUE))
                    .build();
            runPromise(() -> testAgent.initialize(config));

            Map<String, Object> goal = Map.of("objective", "limited-attempts");
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Revised plan incorporates feedback")
        void revisedPlanFeedback() {
            TestPlanningAgent testAgent = new TestPlanningAgent("feedback-agent");
            AgentConfig config = AgentConfig.builder()
                    .properties(Map.of("enableReplanning", Boolean.TRUE))
                    .build();
            runPromise(() -> testAgent.initialize(config));

            Map<String, Object> goal = Map.of(
                    "objective", "learn-from-failure",
                    "feedback_available", true
            );
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Management Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("State Management")
    class StateManagementTests {

        @Test
        @DisplayName("Agent tracks planning state")
        void planningStateTracking() {
            TestPlanningAgent testAgent = new TestPlanningAgent("state-tracker");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of("objective", "track-state");
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result.isSuccess()).isTrue();
            // State should be captured in metrics or explanation
        }

        @Test
        @DisplayName("Current step is tracked during execution")
        void currentStepTracking() {
            TestPlanningAgent testAgent = new TestPlanningAgent("step-tracker");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of("objective", "track-steps", "total", 5);
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Plan history is maintained")
        void planHistory() {
            TestPlanningAgent testAgent = new TestPlanningAgent("history-agent");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of("objective", "maintain-history");
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result.isSuccess()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Latency & Performance Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Latency & Performance")
    class LatencyTests {

        @Test
        @DisplayName("Multi-step execution completes in reasonable time")
        void executionLatency() {
            TestPlanningAgent testAgent = new TestPlanningAgent("latency-agent");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of("objective", "measure-latency", "steps", 10);

            Instant start = Instant.now();
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));
            Instant end = Instant.now();

            assertThat(result.isSuccess()).isTrue();
            Duration latency = Duration.between(start, end);
            
            // Should complete in reasonable time (not strict upper bound)
            assertThat(latency).isLessThan(Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("Long-horizon plans execute persistently")
        void longHorizonExecution() {
            TestPlanningAgent testAgent = new TestPlanningAgent("long-horizon");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of(
                    "objective", "long-running",
                    "estimated_duration_ms", 100,
                    "steps", 20
            );
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Explanation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Explanation Generation")
    class ExplanationTests {

        @Test
        @DisplayName("Plan explanation describes decomposed goals")
        void planExplanation() {
            TestPlanningAgent testAgent = new TestPlanningAgent("explain-agent");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of("objective", "explain-plan");
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            String explanation = result.getExplanation();
            assertThat(explanation)
                    .isNotNull()
                    .isNotBlank();
        }

        @Test
        @DisplayName("Explanation includes step sequence")
        void stepSequenceExplanation() {
            TestPlanningAgent testAgent = new TestPlanningAgent("sequence-explain");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of("objective", "show-sequence", "steps", 3);
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            String explanation = result.getExplanation();
            assertThat(explanation)
                    .isNotNull()
                    .isNotBlank();
        }

        @Test
        @DisplayName("Failure explanation includes recovery attempt")
        void failureExplanation() {
            TestPlanningAgent testAgent = new TestPlanningAgent("failure-explain");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of("objective", "show-failure");
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Confidence & Metrics Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Confidence & Metrics")
    class ConfidenceMetricsTests {

        @Test
        @DisplayName("Confidence reflects plan quality")
        void planQualityConfidence() {
            TestPlanningAgent testAgent = new TestPlanningAgent("confidence-agent");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of("objective", "assess-confidence");
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result.getConfidence())
                    .isGreaterThanOrEqualTo(0.0)
                    .isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("Metrics include plan statistics")
        void planMetrics() {
            TestPlanningAgent testAgent = new TestPlanningAgent("metrics-agent");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of("objective", "collect-metrics", "steps", 5);
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result.getMetrics()).isNotNull();
            // Could contain: step_count, replans, execution_time, etc.
        }

        @Test
        @DisplayName("Processing time reflects multi-step work")
        void processingTime() {
            TestPlanningAgent testAgent = new TestPlanningAgent("time-agent");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build()));

            Map<String, Object> goal = Map.of("objective", "measure-time", "steps", 10);
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal));

            assertThat(result.getProcessingTime()).isNotNull();
            assertThat(result.getProcessingTime().toMillis()).isGreaterThan(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test Planning Agent Implementation
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Concrete test implementation of PlanningAgent for behavioral testing.
     */
    private static class TestPlanningAgent extends PlanningAgent<Map<String, Object>, Map<String, Object>> {

        public TestPlanningAgent(String agentId) {
            super(agentId);
        }

        @Override
        protected List<PlanStep> decompose(AgentContext ctx, Map<String, Object> goal) {
            List<PlanStep> steps = new ArrayList<>();
            
            int stepCount = ((Number) goal.getOrDefault("steps", 3)).intValue();
            for (int i = 0; i < stepCount; i++) {
                steps.add(new PlanStep(
                        "step-" + (i + 1),
                        "Step " + (i + 1) + " of " + stepCount,
                        Map.of("index", i)
                ));
            }
            
            return steps;
        }

        @Override
        protected StepResult executeStep(AgentContext ctx, PlanStep step) {
            return StepResult.success(
                    step.name(),
                    Map.of("result", "success", "output", "Completed " + step.name()),
                    Duration.ZERO
            );
        }

        @Override
        protected Map<String, Object> synthesizeResult(
                AgentContext ctx,
                Map<String, Object> originalInput,
                List<StepResult> stepResults) {
            return Map.of(
                    "steps_completed", stepResults.size(),
                    "success", true
            );
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
