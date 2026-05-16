package com.ghatana.yappc.services.artifact.compileback;

import com.ghatana.yappc.domain.artifact.ArtifactGraphQueryResponse;
import com.ghatana.yappc.services.artifact.ArtifactRequestScope;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @doc.type interface
 * @doc.purpose Service for creating and managing change plans for compile-back operations.
 *              Change plans represent the diff between two semantic model versions.
 * @doc.layer service
 * @doc.pattern Service
 *
 * P5: Java-owned ChangePlan service for safe minimal patches and review bundles.
 */
public interface ChangePlanService {

    /**
     * Create a change plan by diffing two semantic model versions.
     *
     * @param scope the request scope
     * @param baseModelId the base model version ID
     * @param targetModelId the target model version ID
     * @return promise of the created change plan
     */
    Promise<ChangePlan> createChangePlan(ArtifactRequestScope scope, String baseModelId, String targetModelId);

    /**
     * Get a change plan by ID.
     *
     * @param planId the change plan ID
     * @param tenantId the tenant for scope validation
     * @return promise of the change plan if found
     */
    Promise<java.util.Optional<ChangePlan>> getChangePlan(String planId, String tenantId);

    /**
     * Validate a change plan for potential conflicts and issues.
     *
     * @param planId the change plan ID
     * @param tenantId the tenant for scope validation
     * @return promise of validation result
     */
    Promise<ValidationResult> validateChangePlan(String planId, String tenantId);

    /**
     * List change plans for a given scope.
     *
     * @param tenantId the tenant ID
     * @param workspaceId optional workspace ID filter
     * @param projectId optional project ID filter
     * @return promise of change plan list
     */
    Promise<List<ChangePlan>> listChangePlans(String tenantId, String workspaceId, String projectId);

    /**
     * Change operation types.
     */
    enum ChangeOpKind {
        ADD_COMPONENT,
        REMOVE_COMPONENT,
        UPDATE_COMPONENT_PROPS,
        ADD_PROP,
        REMOVE_PROP,
        UPDATE_PROP_TYPE,
        ADD_EVENT,
        REMOVE_EVENT,
        ADD_SLOT,
        REMOVE_SLOT,
        RENAME_COMPONENT,
        UPDATE_ACCESSIBILITY,
        ADD_VARIANT,
        REMOVE_VARIANT,
        UNSUPPORTED_OPERATION,
        ADD_PAGE_ROUTE,
        REMOVE_PAGE_ROUTE,
        UPDATE_PAGE_ROUTE,
        ADD_LAYOUT,
        REMOVE_LAYOUT,
        UPDATE_LAYOUT,
        ADD_TOKEN,
        REMOVE_TOKEN,
        UPDATE_TOKEN,
        ADD_API,
        REMOVE_API,
        UPDATE_API,
        ADD_DATA_ENTITY,
        REMOVE_DATA_ENTITY,
        UPDATE_DATA_ENTITY,
        ADD_WORKFLOW,
        REMOVE_WORKFLOW,
        UPDATE_WORKFLOW,
        MANUAL_REVIEW
    }

    /**
     * Individual change operation.
     */
    record ChangeOp(
        String id,
        ChangeOpKind kind,
        String targetElementId,
        String description,
        Object before,
        Object after,
        double autoApplyConfidence,
        boolean requiresReview
    ) {
        public ChangeOp {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(kind, "kind must not be null");
            Objects.requireNonNull(targetElementId, "targetElementId must not be null");
            Objects.requireNonNull(description, "description must not be null");
            if (autoApplyConfidence < 0 || autoApplyConfidence > 1) {
                throw new IllegalArgumentException("autoApplyConfidence must be between 0 and 1");
            }
        }
    }

    /**
     * Change plan representing a set of operations to transform one model to another.
     */
    record ChangePlan(
        String planId,
        String tenantId,
        String workspaceId,
        String projectId,
        String baseModelId,
        String targetModelId,
        List<ChangeOp> operations,
        Instant createdAt,
        String createdBy,
        String description,
        ImpactAssessment impact
    ) {
        public ChangePlan {
            Objects.requireNonNull(planId, "planId must not be null");
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(workspaceId, "workspaceId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            operations = operations != null ? List.copyOf(operations) : List.of();
        }

        public int getOperationCount() {
            return operations.size();
        }

        public int getAutoApplicableCount() {
            return (int) operations.stream()
                .filter(op -> op.autoApplyConfidence() >= 0.7 && !op.requiresReview())
                .count();
        }

        public int getReviewRequiredCount() {
            return (int) operations.stream()
                .filter(op -> op.requiresReview() || op.autoApplyConfidence() < 0.7)
                .count();
        }

        public boolean hasUnsupportedOperations() {
            return operations.stream()
                .anyMatch(op -> op.kind() == ChangeOpKind.UNSUPPORTED_OPERATION);
        }
    }

    /**
     * Impact assessment for a change plan.
     */
    record ImpactAssessment(
        int addedElements,
        int removedElements,
        int modifiedElements,
        int affectedFiles,
        List<String> affectedComponents,
        List<String> riskFlags
    ) {
        public ImpactAssessment {
            affectedComponents = affectedComponents != null ? List.copyOf(affectedComponents) : List.of();
            riskFlags = riskFlags != null ? List.copyOf(riskFlags) : List.of();
        }
    }

    /**
     * Validation result for a change plan.
     */
    record ValidationResult(
        String planId,
        boolean valid,
        List<ValidationError> errors,
        List<ValidationWarning> warnings,
        Instant validatedAt,
        String validatorId
    ) {
        public ValidationResult {
            errors = errors != null ? List.copyOf(errors) : List.of();
            warnings = warnings != null ? List.copyOf(warnings) : List.of();
        }
    }

    record ValidationError(
        String code,
        String message,
        String changeOpId,
        String filePath
    ) {}

    record ValidationWarning(
        String code,
        String message,
        String changeOpId,
        String filePath
    ) {}
}
