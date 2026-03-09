package com.ghatana.products.yappc.domain.workflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * AI-generated execution plan for a workflow.
 *
 * @doc.type record
 * @doc.purpose AI plan representation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AiPlan(
    @NotNull String id,
    @NotNull String workflowId,
    @NotNull String tenantId,
    @NotNull String objective,
    @NotNull List<PlanStep> steps,
    @NotNull PlanStatus status,
    @Nullable String generatedBy,
    @NotNull String modelUsed,
    double confidence,
    @Nullable String reasoning,
    @Nullable Map<String, Object> metadata
) {
    /**
     * Plan generation status
     */
    public enum PlanStatus {
        GENERATING,
        PENDING_REVIEW,
        APPROVED,
        REJECTED,
        MODIFIED,
        EXECUTED,
        FAILED
    }

    /**
     * A single step in the AI plan
     */
    public record PlanStep(
        @NotNull String id,
        @NotNull String name,
        @NotNull String description,
        @NotNull StepType type,
        int order,
        @NotNull List<String> dependencies,
        @Nullable String aiInstructions,
        @Nullable Map<String, Object> expectedInputs,
        @Nullable Map<String, Object> expectedOutputs,
        boolean requiresUserReview,
        @Nullable EstimatedDuration estimatedDuration
    ) {
        public enum StepType {
            INTENT_CAPTURE,
            CONTEXT_GATHERING,
            PLAN_GENERATION,
            CODE_GENERATION,
            TEST_GENERATION,
            PREVIEW,
            DEPLOYMENT,
            VERIFICATION,
            DOCUMENTATION,
            CLEANUP
        }

        public record EstimatedDuration(
            int minSeconds,
            int maxSeconds,
            double confidence
        ) {}
    }

    /**
     * Creates a new plan in generating status
     */
    public static AiPlan creating(
        @NotNull String id,
        @NotNull String workflowId,
        @NotNull String tenantId,
        @NotNull String objective,
        @NotNull String modelUsed
    ) {
        return new AiPlan(
            id,
            workflowId,
            tenantId,
            objective,
            List.of(),
            PlanStatus.GENERATING,
            null,
            modelUsed,
            0.0,
            null,
            null
        );
    }

    /**
     * Returns whether the plan is in a modifiable state
     */
    public boolean isModifiable() {
        return status == PlanStatus.PENDING_REVIEW ||
               status == PlanStatus.MODIFIED ||
               status == PlanStatus.REJECTED;
    }

    /**
     * Returns the total estimated duration range
     */
    public EstimatedDurationRange getEstimatedDurationRange() {
        int minTotal = 0;
        int maxTotal = 0;
        for (PlanStep step : steps) {
            if (step.estimatedDuration() != null) {
                minTotal += step.estimatedDuration().minSeconds();
                maxTotal += step.estimatedDuration().maxSeconds();
            }
        }
        return new EstimatedDurationRange(minTotal, maxTotal);
    }

    public record EstimatedDurationRange(int minSeconds, int maxSeconds) {}
}
