package com.ghatana.aiplatform.training;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.observability.NoopMetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for TrainingPipelineOrchestrator.
 *
 * Tests validate:
 * - Correct topological ordering of task dependencies
 * - Parallel execution simulation (no blocking on independent branches)
 * - Circular dependency detection before execution
 * - Missing dependency detection
 * - Failure isolation (one task failure doesn't cascade immediately)
 * - Multi-tenant training isolation
 * - Model version extraction from artifacts
 * - Metric collection during training
 *
 * @see TrainingPipelineOrchestrator
 */
@DisplayName("Training Pipeline Orchestrator Integration Tests")
class TrainingPipelineOrchestratorIntegrationTest extends EventloopTestBase {

    private TrainingPipelineOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // GIVEN: Training orchestrator with no-op metrics
        orchestrator = new TrainingPipelineOrchestrator(
                "fraud-detection-pipeline",
                NoopMetricsCollector.getInstance()
        );
    }

    /**
     * Verifies that tasks execute in correct dependency order.
     *
     * GIVEN: Three-stage training pipeline (prep → train → evaluate)
     * WHEN: Pipeline is executed
     * THEN: Tasks execute sequentially in dependency order,
     *       each task receives output from previous tasks
     */
    @Test
    @DisplayName("Should execute tasks in correct dependency order")
    void shouldExecuteTasksInCorrectOrder() {
        // GIVEN: Simple sequential pipeline
        StringBuilder executionOrder = new StringBuilder();

        orchestrator.addTask("prepare_data",
                tenantId -> {
                    executionOrder.append("1-prepare,");
                    return "model-artifact-v1-prep";
                },
                Collections.emptyList()
        );

        orchestrator.addTask("train_model",
                tenantId -> {
                    executionOrder.append("2-train,");
                    return "model-artifact-v1-train";
                },
                Arrays.asList("prepare_data")
        );

        orchestrator.addTask("evaluate_model",
                tenantId -> {
                    executionOrder.append("3-eval,");
                    return "model-artifact-v1-eval";
                },
                Arrays.asList("train_model")
        );

        // WHEN: Execute pipeline
        TrainingPipelineOrchestrator.TrainingExecutionPlan plan = orchestrator.validateDAG();
        TrainingPipelineOrchestrator.TrainingExecutionResult result = 
                orchestrator.execute("tenant-123", plan);

        // THEN: Tasks executed in correct order
        assertThat(result.isSuccess())
                .as("Training should succeed")
                .isTrue();
        assertThat(executionOrder.toString())
                .as("Tasks should execute in order: prep→train→eval")
                .isEqualTo("1-prepare,2-train,3-eval,");
        assertThat(result.getCompletedTasks())
                .as("All three tasks should complete")
                .hasSize(3)
                .containsExactly("prepare_data", "train_model", "evaluate_model");
    }

    /**
     * Verifies that parallel independent branches can execute.
     *
     * GIVEN: DAG with multiple independent tasks (feature1, feature2) then convergence (train)
     * WHEN: Plan is created
     * THEN: Independent tasks appear first in execution order (could run parallel)
     */
    @Test
    @DisplayName("Should identify independent branches for parallel execution")
    void shouldIdentifyIndependentBranches() {
        // GIVEN: DAG with independent feature engineering branches
        orchestrator.addTask("feature_eng_1",
                tenantId -> "features-v1",
                Collections.emptyList()
        );

        orchestrator.addTask("feature_eng_2",
                tenantId -> "features-v2",
                Collections.emptyList()
        );

        orchestrator.addTask("train_ensemble",
                tenantId -> "model-ensemble-v1",
                Arrays.asList("feature_eng_1", "feature_eng_2")
        );

        // WHEN: Validate DAG
        TrainingPipelineOrchestrator.TrainingExecutionPlan plan = orchestrator.validateDAG();

        // THEN: Execution order respects dependencies
        // feature_eng_1 and feature_eng_2 can be first (no dependencies)
        // train_ensemble must be last (depends on both)
        assertThat(plan.executionOrder)
                .as("Execution order should respect dependencies")
                .hasSize(3);
        assertThat(plan.executionOrder.get(0))
                .as("First task should have no dependencies")
                .isIn("feature_eng_1", "feature_eng_2");
        assertThat(plan.executionOrder.get(2))
                .as("Train ensemble should be last (depends on features)")
                .isEqualTo("train_ensemble");
    }

    /**
     * Verifies circular dependency detection before execution.
     *
     * GIVEN: Pipeline with circular dependency (A → B → C → A)
     * WHEN: validateDAG() is called
     * THEN: CircularDependencyException thrown immediately (prevents execution)
     */
    @Test
    @DisplayName("Should detect and reject circular dependencies")
    void shouldDetectCircularDependencies() {
        // GIVEN: Circular dependency
        orchestrator.addTask("model_a",
                tenantId -> "artifact-a",
                Arrays.asList("model_c")  // A depends on C
        );

        orchestrator.addTask("model_b",
                tenantId -> "artifact-b",
                Arrays.asList("model_a")  // B depends on A
        );

        orchestrator.addTask("model_c",
                tenantId -> "artifact-c",
                Arrays.asList("model_b")  // C depends on B → CYCLE!
        );

        // WHEN: Validate DAG
        // THEN: CircularDependencyException thrown
        assertThatThrownBy(() -> orchestrator.validateDAG())
                .as("Should reject circular dependencies")
                .isInstanceOf(TrainingPipelineOrchestrator.CircularDependencyException.class)
                .hasMessageContaining("Circular dependency");
    }

    /**
     * Verifies missing dependency detection.
     *
     * GIVEN: Task that depends on non-existent task
     * WHEN: validateDAG() is called
     * THEN: MissingDependencyException thrown with task reference
     */
    @Test
    @DisplayName("Should detect missing dependencies")
    void shouldDetectMissingDependencies() {
        // GIVEN: Task with non-existent dependency
        orchestrator.addTask("train",
                tenantId -> "model-v1",
                Arrays.asList("prepare_data", "validate_features")  // validate_features doesn't exist
        );

        // WHEN: Validate DAG
        // THEN: MissingDependencyException thrown
        assertThatThrownBy(() -> orchestrator.validateDAG())
                .as("Should reject missing dependencies")
                .isInstanceOf(TrainingPipelineOrchestrator.MissingDependencyException.class)
                .hasMessageContaining("validate_features");
    }

    /**
     * Verifies failure isolation and recovery information.
     *
     * GIVEN: Pipeline where middle task fails
     * WHEN: Execute pipeline
     * THEN: Pipeline stops at failure, earlier tasks marked complete,
     *       later tasks skipped, failure details captured
     */
    @Test
    @DisplayName("Should isolate task failures and report details")
    void shouldIsolateTaskFailures() {
        // GIVEN: Pipeline with failure in middle task
        orchestrator.addTask("prepare",
                tenantId -> "prep-artifact",
                Collections.emptyList()
        );

        orchestrator.addTask("train_failing",
                tenantId -> {
                    throw new RuntimeException("GPU out of memory during training");
                },
                Arrays.asList("prepare")
        );

        orchestrator.addTask("evaluate",
                tenantId -> "eval-artifact",
                Arrays.asList("train_failing")
        );

        // WHEN: Execute pipeline
        TrainingPipelineOrchestrator.TrainingExecutionPlan plan = orchestrator.validateDAG();
        TrainingPipelineOrchestrator.TrainingExecutionResult result =
                orchestrator.execute("tenant-123", plan);

        // THEN: Failure isolated, details reported
        assertThat(result.isSuccess())
                .as("Training should fail")
                .isFalse();
        assertThat(result.getCompletedTasks())
                .as("Only prepare task should complete before failure")
                .containsExactly("prepare");
        assertThat(result.getFailedTasks())
                .as("train_failing should be recorded as failed")
                .containsExactly("train_failing");
        assertThat(result.getFailureReason())
                .as("Failure reason should mention GPU memory")
                .contains("GPU out of memory");
    }

    /**
     * Verifies multi-tenant training isolation.
     *
     * GIVEN: Same pipeline executed for two different tenants
     * WHEN: Both execute the same pipeline
     * THEN: Each gets independent task context, metrics tagged with tenant
     */
    @Test
    @DisplayName("Should enforce tenant isolation during training")
    void shouldEnforceTenantIsolationDuringTraining() {
        // GIVEN: Pipeline that tracks tenant context
        orchestrator.addTask("prepare",
                tenantId -> "prep-" + tenantId,  // Include tenant in artifact
                Collections.emptyList()
        );

        orchestrator.addTask("train",
                tenantId -> "model-" + tenantId,  // Include tenant in artifact
                Arrays.asList("prepare")
        );

        // WHEN: Execute for tenant-A
        TrainingPipelineOrchestrator.TrainingExecutionPlan plan = orchestrator.validateDAG();
        TrainingPipelineOrchestrator.TrainingExecutionResult resultA =
                orchestrator.execute("tenant-A", plan);

        // AND: Execute for tenant-B with fresh orchestrator
        TrainingPipelineOrchestrator orchestrator2 = new TrainingPipelineOrchestrator(
                "fraud-detection-pipeline",
                NoopMetricsCollector.getInstance()
        );
        orchestrator2.addTask("prepare",
                tenantId -> "prep-" + tenantId,
                Collections.emptyList()
        );
        orchestrator2.addTask("train",
                tenantId -> "model-" + tenantId,
                Arrays.asList("prepare")
        );
        TrainingPipelineOrchestrator.TrainingExecutionResult resultB =
                orchestrator2.execute("tenant-B", plan);

        // THEN: Each tenant has independent artifacts
        assertThat(resultA.getArtifacts())
                .as("Tenant A artifacts should be tenant-A scoped")
                .containsEntry("train", "model-tenant-A");
        assertThat(resultB.getArtifacts())
                .as("Tenant B artifacts should be tenant-B scoped")
                .containsEntry("train", "model-tenant-B");
    }

    /**
     * Verifies model version extraction from artifact references.
     *
     * GIVEN: Final artifact with version pattern
     * WHEN: Result is created from execution
     * THEN: Model version correctly extracted and available
     */
    @Test
    @DisplayName("Should extract model version from final artifact")
    void shouldExtractModelVersionFromArtifact() {
        // GIVEN: Pipeline ending with versioned artifact
        orchestrator.addTask("prepare",
                tenantId -> "artifacts/prep",
                Collections.emptyList()
        );

        orchestrator.addTask("train",
                tenantId -> "artifacts/model",
                Arrays.asList("prepare")
        );

        orchestrator.addTask("evaluate",
                tenantId -> "model-artifact-v20250115-1630",  // Versioned format
                Arrays.asList("train")
        );

        // WHEN: Execute pipeline
        TrainingPipelineOrchestrator.TrainingExecutionPlan plan = orchestrator.validateDAG();
        TrainingPipelineOrchestrator.TrainingExecutionResult result =
                orchestrator.execute("tenant-123", plan);

        // THEN: Model version extracted
        assertThat(result.getModelVersion())
                .as("Should extract version from artifact")
                .isEqualTo("v20250115-1630");
    }

    /**
     * Verifies execution statistics tracking.
     *
     * GIVEN: Pipeline executed multiple times
     * WHEN: Get stats after execution
     * THEN: Statistics show execution count, failures, average timing
     */
    @Test
    @DisplayName("Should track pipeline execution statistics")
    void shouldTrackExecutionStatistics() {
        // GIVEN: Simple pipeline
        orchestrator.addTask("train",
                tenantId -> "model-v1",
                Collections.emptyList()
        );

        TrainingPipelineOrchestrator.TrainingExecutionPlan plan = orchestrator.validateDAG();

        // WHEN: Execute pipeline
        orchestrator.execute("tenant-123", plan);
        orchestrator.execute("tenant-124", plan);

        // AND: Get stats
        TrainingPipelineOrchestrator.PipelineStats stats = orchestrator.getStats();

        // THEN: Stats should show executions
        assertThat(stats.totalExecutions)
                .as("Should record total executions")
                .isEqualTo(2);
        assertThat(stats.totalFailures)
                .as("Should record failures (none in this case)")
                .isEqualTo(0);
        assertThat(stats.getFailureRate())
                .as("Failure rate should be 0%")
                .isEqualTo(0.0);
    }

    /**
     * Verifies long-running task timing and timeout scenarios.
     *
     * GIVEN: Task that takes significant time
     * WHEN: Execute and monitor duration
     * THEN: Duration tracked accurately, execution completes
     */
    @Test
    @DisplayName("Should track task execution duration accurately")
    void shouldTrackTaskDuration() {
        // GIVEN: Task with intentional delay
        orchestrator.addTask("slow_training",
                tenantId -> {
                    Thread.sleep(100);  // 100ms delay
                    return "model-v1";
                },
                Collections.emptyList()
        );

        // WHEN: Execute pipeline
        TrainingPipelineOrchestrator.TrainingExecutionPlan plan = orchestrator.validateDAG();
        long startTime = System.currentTimeMillis();
        TrainingPipelineOrchestrator.TrainingExecutionResult result =
                orchestrator.execute("tenant-123", plan);
        long endTime = System.currentTimeMillis();

        // THEN: Duration should capture execution time (at least 100ms)
        assertThat(result.getDurationMs())
                .as("Duration should include task execution time")
                .isGreaterThanOrEqualTo(100);
        assertThat(result.isSuccess())
                .as("Task should succeed despite delay")
                .isTrue();
    }
}
