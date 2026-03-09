package com.ghatana.products.yappc.domain.workflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an AI-assisted workflow instance.
 *
 * @doc.type record
 * @doc.purpose Workflow entity for AI-generated plans
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AiWorkflowInstance(
    @NotNull String id,
    @NotNull String tenantId,
    @NotNull String name,
    @NotNull String description,
    @NotNull WorkflowType type,
    @NotNull WorkflowStatus status,
    @NotNull String currentStepId,
    int currentStepIndex,
    int totalSteps,
    @NotNull Map<String, Object> context,
    @NotNull Map<String, AiWorkflowStepResult> stepResults,
    @Nullable String aiPlanId,
    @Nullable String createdBy,
    @NotNull Instant createdAt,
    @NotNull Instant updatedAt,
    @Nullable Instant completedAt,
    @Nullable String errorMessage
) {
    /**
     * Workflow types supported by the AI workflow engine
     */
    public enum WorkflowType {
        APP_CREATION,
        FEATURE_DEVELOPMENT,
        BUG_FIX,
        REFACTORING,
        TESTING,
        DEPLOYMENT,
        CUSTOM
    }

    /**
     * Workflow execution status
     */
    public enum WorkflowStatus {
        DRAFT,
        PENDING,
        IN_PROGRESS,
        PAUSED,
        AWAITING_REVIEW,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * Result of a single workflow step
     */
    public record AiWorkflowStepResult(
        @NotNull String stepId,
        @NotNull String stepName,
        @NotNull StepStatus status,
        @Nullable Object output,
        @Nullable String aiGenerated,
        @Nullable String userModified,
        @NotNull Instant startedAt,
        @Nullable Instant completedAt,
        @Nullable String errorMessage,
        @Nullable Map<String, Object> metadata
    ) {
        public enum StepStatus {
            PENDING,
            IN_PROGRESS,
            AWAITING_USER_INPUT,
            AWAITING_AI_REVIEW,
            COMPLETED,
            SKIPPED,
            FAILED
        }
    }

    /**
     * Creates a new workflow in draft status
     */
    public static AiWorkflowInstance create(
        @NotNull String id,
        @NotNull String tenantId,
        @NotNull String name,
        @NotNull String description,
        @NotNull WorkflowType type
    ) {
        Instant now = Instant.now();
        return new AiWorkflowInstance(
            id,
            tenantId,
            name,
            description,
            type,
            WorkflowStatus.DRAFT,
            "",
            0,
            0,
            Map.of(),
            Map.of(),
            null,
            null,
            now,
            now,
            null,
            null
        );
    }

    /**
     * Returns whether the workflow is in a terminal state
     */
    public boolean isTerminal() {
        return status == WorkflowStatus.COMPLETED ||
               status == WorkflowStatus.FAILED ||
               status == WorkflowStatus.CANCELLED;
    }

    /**
     * Returns the progress percentage
     */
    public double getProgress() {
        if (totalSteps == 0) return 0.0;
        return ((double) currentStepIndex / totalSteps) * 100;
    }
}
