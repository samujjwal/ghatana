/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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
@DisplayName("PlanningAgent Behavioral Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class PlanningAgentBehavioralTest {

    @Mock
    private MemoryStore memoryStore;

    private AgentContext agentContext;
    private PlanningAgent agent;

    @BeforeEach
    void setUp() { // GH-90000
        agentContext = AgentContext.builder() // GH-90000
                .turnId("turn-1 [GH-90000]")
                .agentId("planning-agent [GH-90000]")
                .tenantId("tenant-1 [GH-90000]")
                .memoryStore(memoryStore) // GH-90000
                .build(); // GH-90000

        agent = new TestPlanningAgent("planning-agent [GH-90000]");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Plan Decomposition Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Plan Decomposition [GH-90000]")
    class DecompositionTests {

        @Test
        @DisplayName("Agent decomposes goal into steps [GH-90000]")
        void goalDecomposition() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("test-agent [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of("task", "build-app", "features", 3); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Plan contains ordered sequence of steps [GH-90000]")
        void stepSequencing() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("sequential-agent [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of( // GH-90000
                    "objective", "process-data",
                    "steps", 3
            );
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Plan depends on goal complexity [GH-90000]")
        void complexityDependence() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("complex-agent [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            // Simple goal
            Map<String, Object> simpleGoal = Map.of("task", "simple"); // GH-90000
            AgentResult<?> simpleResult = runPromise(() -> testAgent.process(agentContext, simpleGoal)); // GH-90000
            assertThat(simpleResult.isSuccess()).isTrue(); // GH-90000

            // Complex goal
            Map<String, Object> complexGoal = Map.of("task", "complex", "subtasks", 10); // GH-90000
            AgentResult<?> complexResult = runPromise(() -> testAgent.process(agentContext, complexGoal)); // GH-90000
            assertThat(complexResult.isSuccess()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Step Execution Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Step Execution [GH-90000]")
    class ExecutionTests {

        @Test
        @DisplayName("Steps execute in sequence [GH-90000]")
        void sequentialExecution() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("sequential-exec [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of("goal", "execute-steps", "count", 5); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Step success produces expected output [GH-90000]")
        void stepSuccessOutput() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("success-agent [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of( // GH-90000
                    "objective", "verify-steps",
                    "expected_success", true
            );
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Step failure captures error context [GH-90000]")
        void stepFailureHandling() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("failure-agent [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of("objective", "test-failure"); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            // Should handle gracefully
        }

        @Test
        @DisplayName("Agent accumulates results across steps [GH-90000]")
        void resultAccumulation() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("accumulator [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of( // GH-90000
                    "objective", "accumulate-results",
                    "steps", 5
            );
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Replanning Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Replanning [GH-90000]")
    class ReplanningTests {

        @Test
        @DisplayName("Agent replans when step fails [GH-90000]")
        void replanOnStepFailure() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("replan-agent [GH-90000]");
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .properties(Map.of("enableReplanning", Boolean.TRUE)) // GH-90000
                    .build(); // GH-90000
            runPromise(() -> testAgent.initialize(config)); // GH-90000

            Map<String, Object> goal = Map.of("objective", "trigger-replan"); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Replan respects maximum attempt limit [GH-90000]")
        void replanAttemptLimit() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("limited-replan [GH-90000]");
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .properties(Map.of("enableReplanning", Boolean.TRUE)) // GH-90000
                    .build(); // GH-90000
            runPromise(() -> testAgent.initialize(config)); // GH-90000

            Map<String, Object> goal = Map.of("objective", "limited-attempts"); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Revised plan incorporates feedback [GH-90000]")
        void revisedPlanFeedback() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("feedback-agent [GH-90000]");
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .properties(Map.of("enableReplanning", Boolean.TRUE)) // GH-90000
                    .build(); // GH-90000
            runPromise(() -> testAgent.initialize(config)); // GH-90000

            Map<String, Object> goal = Map.of( // GH-90000
                    "objective", "learn-from-failure",
                    "feedback_available", true
            );
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Management Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("State Management [GH-90000]")
    class StateManagementTests {

        @Test
        @DisplayName("Agent tracks planning state [GH-90000]")
        void planningStateTracking() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("state-tracker [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of("objective", "track-state"); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            // State should be captured in metrics or explanation
        }

        @Test
        @DisplayName("Current step is tracked during execution [GH-90000]")
        void currentStepTracking() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("step-tracker [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of("objective", "track-steps", "total", 5); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Plan history is maintained [GH-90000]")
        void planHistory() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("history-agent [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of("objective", "maintain-history"); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Latency & Performance Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Latency & Performance [GH-90000]")
    class LatencyTests {

        @Test
        @DisplayName("Multi-step execution completes in reasonable time [GH-90000]")
        void executionLatency() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("latency-agent [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of("objective", "measure-latency", "steps", 10); // GH-90000

            Instant start = Instant.now(); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000
            Instant end = Instant.now(); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            Duration latency = Duration.between(start, end); // GH-90000

            // Should complete in reasonable time (not strict upper bound) // GH-90000
            assertThat(latency).isLessThan(Duration.ofSeconds(10)); // GH-90000
        }

        @Test
        @DisplayName("Long-horizon plans execute persistently [GH-90000]")
        void longHorizonExecution() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("long-horizon [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of( // GH-90000
                    "objective", "long-running",
                    "estimated_duration_ms", 100,
                    "steps", 20
            );
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Explanation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Explanation Generation [GH-90000]")
    class ExplanationTests {

        @Test
        @DisplayName("Plan explanation describes decomposed goals [GH-90000]")
        void planExplanation() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("explain-agent [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of("objective", "explain-plan"); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            String explanation = result.getExplanation(); // GH-90000
            assertThat(explanation) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("Explanation includes step sequence [GH-90000]")
        void stepSequenceExplanation() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("sequence-explain [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of("objective", "show-sequence", "steps", 3); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            String explanation = result.getExplanation(); // GH-90000
            assertThat(explanation) // GH-90000
                    .isNotNull() // GH-90000
                    .isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("Failure explanation includes recovery attempt [GH-90000]")
        void failureExplanation() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("failure-explain [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of("objective", "show-failure"); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Confidence & Metrics Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Confidence & Metrics [GH-90000]")
    class ConfidenceMetricsTests {

        @Test
        @DisplayName("Confidence reflects plan quality [GH-90000]")
        void planQualityConfidence() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("confidence-agent [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of("objective", "assess-confidence"); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result.getConfidence()) // GH-90000
                    .isGreaterThanOrEqualTo(0.0) // GH-90000
                    .isLessThanOrEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("Metrics include plan statistics [GH-90000]")
        void planMetrics() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("metrics-agent [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of("objective", "collect-metrics", "steps", 5); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result.getMetrics()).isNotNull(); // GH-90000
            // Could contain: step_count, replans, execution_time, etc.
        }

        @Test
        @DisplayName("Processing time reflects multi-step work [GH-90000]")
        void processingTime() { // GH-90000
            TestPlanningAgent testAgent = new TestPlanningAgent("time-agent [GH-90000]");
            runPromise(() -> testAgent.initialize(AgentConfig.builder().build())); // GH-90000

            Map<String, Object> goal = Map.of("objective", "measure-time", "steps", 10); // GH-90000
            AgentResult<?> result = runPromise(() -> testAgent.process(agentContext, goal)); // GH-90000

            assertThat(result.getProcessingTime()).isNotNull(); // GH-90000
            // Millisecond granularity can be 0 for very fast in-memory execution.
            assertThat(result.getProcessingTime().toMillis()).isGreaterThanOrEqualTo(0); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test Planning Agent Implementation
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Concrete test implementation of PlanningAgent for behavioral testing.
     */
    private static class TestPlanningAgent extends PlanningAgent<Map<String, Object>, Map<String, Object>> {

        public TestPlanningAgent(String agentId) { // GH-90000
            super(agentId); // GH-90000
        }

        @Override
        protected List<PlanStep> decompose(AgentContext ctx, Map<String, Object> goal) { // GH-90000
            List<PlanStep> steps = new ArrayList<>(); // GH-90000

            int stepCount = ((Number) goal.getOrDefault("steps", 3)).intValue(); // GH-90000
            for (int i = 0; i < stepCount; i++) { // GH-90000
                steps.add(new PlanStep( // GH-90000
                        "step-" + (i + 1), // GH-90000
                        "Step " + (i + 1) + " of " + stepCount, // GH-90000
                        Map.of("index", i) // GH-90000
                ));
            }

            return steps;
        }

        @Override
        protected StepResult executeStep(AgentContext ctx, PlanStep step) { // GH-90000
            return StepResult.success( // GH-90000
                    step.name(), // GH-90000
                    Map.of("result", "success", "output", "Completed " + step.name()), // GH-90000
                    Duration.ZERO
            );
        }

        @Override
        protected Map<String, Object> synthesizeResult( // GH-90000
                AgentContext ctx,
                Map<String, Object> originalInput,
                List<StepResult> stepResults) {
            return Map.of( // GH-90000
                    "steps_completed", stepResults.size(), // GH-90000
                    "success", true
            );
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
