package com.ghatana.virtualorg.planning;

import com.ghatana.virtualorg.v1.TaskProto;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Port interface for planning and decomposing complex tasks into executable steps.
 *
 * <p><b>Purpose</b><br>
 * Provides task planning abstraction enabling automated task decomposition,
 * dependency analysis, effort estimation, and plan refinement. Supports
 * both initial planning and adaptive replanning based on execution feedback.
 *
 * <p><b>Architecture Role</b><br>
 * Port interface in the planning layer for:
 * - Task decomposition: Break complex tasks into atomic work units
 * - Dependency analysis: Identify sequential and parallel execution paths
 * - Effort estimation: Predict time and resources per step
 * - Plan refinement: Adapt plans based on execution progress
 * - Historical learning: Leverage past executions for better plans
 *
 * <p><b>Planning Strategies</b><br>
 * Implementations may use:
 * - <b>LLM-based planning</b>: Use language models for reasoning (see {@link LLMTaskPlanner})
 * - <b>Rule-based planning</b>: Predefined decomposition patterns
 * - <b>Hybrid planning</b>: Combine LLM reasoning with rule validation
 * - <b>Historical planning</b>: Reuse successful past plans
 *
 * <p><b>Plan Quality</b><br>
 * Plans include confidence score (0.0-1.0) indicating:
 * - 0.9-1.0: High confidence - clear decomposition, known patterns
 * - 0.7-0.9: Medium confidence - some uncertainty, may need refinement
 * - <0.7: Low confidence - complex task, recommend human review
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TaskPlanner planner = new LLMTaskPlanner(eventloop, llmClient, memory);
 * 
 * // Create initial plan
 * TaskProto task = TaskProto.newBuilder()
 *     .setTitle("Implement user authentication")
 *     .setType(TaskTypeProto.TASK_TYPE_FEATURE)
 *     .build();
 * 
 * TaskPlan plan = planner.createPlan(task).getResult();
 * logger.info("Plan has {} steps, confidence={}", plan.steps().size(), plan.confidence());
 * 
 * // Refine plan after partial execution
 * List<String> completed = List.of("step-1", "step-2");
 * String feedback = "Step 2 took longer than expected due to schema complexity";
 * TaskPlan refined = planner.refinePlan(plan, completed, feedback).getResult();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe for concurrent planning requests.
 *
 * @see TaskPlan
 * @see PlanStep
 * @see LLMTaskPlanner
 * @doc.type interface
 * @doc.purpose Task planning and decomposition port
 * @doc.layer product
 * @doc.pattern Port
 */
public interface TaskPlanner {

    /**
     * Create a plan for executing the given task.
     *
     * This method analyzes the task and decomposes it into steps using:
     * - Task type and complexity analysis
     * - Historical execution data from agent memory
     * - LLM-powered reasoning about task breakdown
     * - Best practices and patterns from similar tasks
     *
     * @param task The task to plan
     * @return Promise of TaskPlan with decomposed steps
     */
    @NotNull
    Promise<TaskPlan> createPlan(@NotNull TaskProto task);

    /**
     * Refine an existing plan based on feedback or partial execution.
     *
     * This method adjusts the plan based on:
     * - Completed steps and their actual effort
     * - Encountered issues or blockers
     * - Changed requirements or priorities
     * - New information discovered during execution
     *
     * @param originalPlan The original plan
     * @param completedSteps IDs of completed steps
     * @param feedback Textual feedback about plan execution
     * @return Promise of refined TaskPlan
     */
    @NotNull
    Promise<TaskPlan> refinePlan(
        @NotNull TaskPlan originalPlan,
        @NotNull java.util.List<String> completedSteps,
        @NotNull String feedback
    );

    /**
     * Estimate the effort required for a task without creating a full plan.
     *
     * This is useful for quick effort estimates before detailed planning.
     *
     * @param task The task to estimate
     * @return Promise of estimated effort in hours
     */
    @NotNull
    Promise<Double> estimateEffort(@NotNull TaskProto task);

    /**
     * Validate that a plan is executable and well-formed.
     *
     * Checks for:
     * - No circular dependencies
     * - All dependency step IDs exist
     * - Reasonable effort estimates
     * - Valid acceptance criteria
     *
     * @param plan The plan to validate
     * @return Promise of validation result (true if valid)
     */
    @NotNull
    Promise<Boolean> validatePlan(@NotNull TaskPlan plan);
}
