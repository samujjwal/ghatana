package com.ghatana.virtualorg.planning;

import com.ghatana.virtualorg.v1.TaskProto;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Execution plan for a complex task with decomposed steps and dependencies.
 *
 * <p><b>Purpose</b><br>
 * Value object representing the output of task planning:
 * - Ordered list of executable steps with dependencies
 * - Effort estimates and acceptance criteria
 * - Confidence score and reasoning
 * - Alternative approaches considered
 *
 * <p><b>Plan Structure</b><br>
 * Each plan contains:
 * - <b>Original Task</b>: The task that was decomposed
 * - <b>Steps</b>: Ordered list of {@link PlanStep} (atomic work units)
 * - <b>Total Estimated Effort</b>: Sum of all step estimates (hours)
 * - <b>Confidence</b>: 0.0-1.0 score indicating plan quality
 * - <b>Reasoning</b>: Explanation of decomposition strategy
 * - <b>Alternatives</b>: Other approaches considered but not chosen
 *
 * <p><b>Dependency Graph</b><br>
 * Steps form a DAG (Directed Acyclic Graph):
 * - Each step references its dependencies via {@link PlanStep#dependsOn()}
 * - Use {@link #getReadySteps(List)} to find steps ready for execution
 * - Use {@link #isComplete(List)} to check if all steps are done
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Example: Plan for "Implement user authentication"
 * TaskPlan plan = TaskPlan.builder()
 *     .originalTask(authTask)
 *     .addStep(PlanStep.of("step-1", "Design database schema", 2.0))
 *     .addStep(PlanStep.of("step-2", "Implement user model", 1.0, List.of("step-1")))
 *     .addStep(PlanStep.of("step-3", "Create auth service", 3.0, List.of("step-2")))
 *     .confidence(0.85f)
 *     .reasoning("Standard layered approach with DB-first design")
 *     .alternative("Could use NoSQL instead of relational DB")
 *     .build();
 * 
 * // Execute plan
 * List<String> completed = new ArrayList<>();
 * while (!plan.isComplete(completed)) {
 *     List<PlanStep> ready = plan.getReadySteps(completed);
 *     for (PlanStep step : ready) {
 *         executeStep(step);
 *         completed.add(step.id());
 *     }
 * }
 * }</pre>
 *
 * <p><b>Validation</b><br>
 * Canonical constructor validates:
 * - steps: non-empty
 * - confidence: 0.0 ≤ confidence ≤ 1.0
 * - totalEstimatedEffort: ≥ 0.0
 *
 * @param originalTask The original task that was decomposed
 * @param steps List of execution steps in dependency order
 * @param totalEstimatedEffort Total estimated effort in hours
 * @param confidence Confidence score (0.0-1.0) in the plan's success
 * @param reasoning Explanation of why this plan was chosen
 * @param alternatives Alternative approaches considered but not chosen
 * @see PlanStep
 * @see TaskPlanner
 * @doc.type record
 * @doc.purpose Task execution plan with decomposed steps
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record TaskPlan(
    @NotNull TaskProto originalTask,
    @NotNull List<PlanStep> steps,
    double totalEstimatedEffort,
    float confidence,
    @NotNull String reasoning,
    @NotNull List<String> alternatives
) {
    public TaskPlan {
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Plan must have at least one step");
        }
        if (confidence < 0.0f || confidence > 1.0f) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
        if (totalEstimatedEffort < 0.0) {
            throw new IllegalArgumentException("Total estimated effort must be non-negative");
        }
    }

    /**
     * Get the first step in the plan
     */
    @NotNull
    public PlanStep firstStep() {
        return steps.get(0);
    }

    /**
     * Get the last step in the plan
     */
    @NotNull
    public PlanStep lastStep() {
        return steps.get(steps.size() - 1);
    }

    /**
     * Check if the plan has multiple steps
     */
    public boolean isMultiStep() {
        return steps.size() > 1;
    }

    /**
     * Get steps that are ready to execute (no pending dependencies)
     */
    @NotNull
    public List<PlanStep> getReadySteps(@NotNull List<String> completedStepIds) {
        return steps.stream()
            .filter(step -> !completedStepIds.contains(step.id()))
            .filter(step -> completedStepIds.containsAll(step.dependsOn()))
            .toList();
    }

    /**
     * Check if all steps are completed
     */
    public boolean isComplete(@NotNull List<String> completedStepIds) {
        return steps.stream()
            .map(PlanStep::id)
            .allMatch(completedStepIds::contains);
    }

    /**
     * Calculate progress percentage
     */
    public double getProgress(@NotNull List<String> completedStepIds) {
        if (steps.isEmpty()) {
            return 100.0;
        }
        long completed = steps.stream()
            .map(PlanStep::id)
            .filter(completedStepIds::contains)
            .count();
        return (completed * 100.0) / steps.size();
    }
}
