package com.ghatana.virtualorg.planning;

import com.ghatana.virtualorg.v1.TaskTypeProto;
import com.ghatana.virtualorg.v1.TaskPriorityProto;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Atomic work unit in a task execution plan with dependencies and acceptance criteria.
 *
 * <p><b>Purpose</b><br>
 * Value object representing a single executable step in a {@link TaskPlan}.
 * Each step is an atomic unit of work that can be assigned to an agent,
 * tracked for completion, and validated against acceptance criteria.
 *
 * <p><b>Step Properties</b><br>
 * Each step defines:
 * - <b>Identity</b>: Unique ID and descriptive title
 * - <b>Work Description</b>: Detailed description of what needs to be done
 * - <b>Type</b>: Task type (implementation, review, test, documentation, etc.)
 * - <b>Priority</b>: Priority level for scheduling
 * - <b>Dependencies</b>: IDs of steps that must complete first
 * - <b>Effort Estimate</b>: Estimated time in hours
 * - <b>Acceptance Criteria</b>: List of conditions for completion
 * - <b>Required Tools</b>: Tools needed to execute the step
 * - <b>Metadata</b>: Additional step-specific data
 *
 * <p><b>Dependency Management</b><br>
 * Steps form a dependency DAG (Directed Acyclic Graph):
 * - Use {@link #hasDependencies()} to check if step has prerequisites
 * - Use {@link #isReady(List)} to check if dependencies are satisfied
 * - Scheduler should execute steps only when {@link #isReady(List)} returns true
 *
 * <p><b>Acceptance Criteria</b><br>
 * Each step requires at least one acceptance criterion:
 * - "Code compiles without errors"
 * - "All unit tests pass"
 * - "Code coverage ≥ 80%"
 * - "Security scan shows no vulnerabilities"
 * - "Documentation updated"
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Step with dependencies
 * PlanStep step = new PlanStep(
 *     "step-3",
 *     "Create authentication service",
 *     "Implement JWT-based authentication with password hashing",
 *     TaskTypeProto.TASK_TYPE_IMPLEMENTATION,
 *     TaskPriorityProto.TASK_PRIORITY_HIGH,
 *     List.of("step-1", "step-2"),  // depends on schema + model
 *     3.0,  // estimated 3 hours
 *     List.of(
 *         "JWT tokens generated correctly",
 *         "Password hashing uses bcrypt",
 *         "All tests pass"
 *     ),
 *     List.of("jwt", "crypto", "database"),
 *     Map.of("algorithm", "bcrypt", "saltRounds", "10")
 * );
 * 
 * // Check if ready to execute
 * List<String> completed = List.of("step-1", "step-2");
 * if (step.isReady(completed)) {
 *     assignToAgent(step);
 * }
 * }</pre>
 *
 * <p><b>Validation</b><br>
 * Canonical constructor validates:
 * - estimatedEffort: must be non-negative
 * - acceptanceCriteria: must have at least one criterion
 *
 * @param id Unique identifier for this step
 * @param title Short description of the step
 * @param description Detailed description of what needs to be done
 * @param type Type of task (implementation, review, test, etc.)
 * @param priority Priority level
 * @param dependsOn IDs of steps that must complete before this step
 * @param estimatedEffort Estimated effort in hours
 * @param acceptanceCriteria List of criteria that must be met for completion
 * @param requiredTools List of tool names needed to execute this step
 * @param metadata Additional step-specific metadata
 * @see TaskPlan
 * @see TaskPlanner
 * @doc.type record
 * @doc.purpose Atomic task step with dependencies and acceptance criteria
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record PlanStep(
    @NotNull String id,
    @NotNull String title,
    @NotNull String description,
    @NotNull TaskTypeProto type,
    @NotNull TaskPriorityProto priority,
    @NotNull List<String> dependsOn,
    double estimatedEffort,
    @NotNull List<String> acceptanceCriteria,
    @NotNull List<String> requiredTools,
    @NotNull Map<String, String> metadata
) {
    public PlanStep {
        if (estimatedEffort < 0.0) {
            throw new IllegalArgumentException("Estimated effort must be non-negative");
        }
        if (acceptanceCriteria.isEmpty()) {
            throw new IllegalArgumentException("Step must have at least one acceptance criterion");
        }
    }

    /**
     * Check if this step has dependencies
     */
    public boolean hasDependencies() {
        return !dependsOn.isEmpty();
    }

    /**
     * Check if this step is ready to execute given completed step IDs
     */
    public boolean isReady(@NotNull List<String> completedStepIds) {
        return completedStepIds.containsAll(dependsOn);
    }

    /**
     * Get a simplified summary of this step
     */
    @NotNull
    public String summary() {
        return String.format("[%s] %s (%.1fh)", type.name(), title, estimatedEffort);
    }
}
