package com.ghatana.yappc.services.artifact.compileback;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type interface
 * @doc.purpose Service for managing review bundles - collections of changes, patches,
 *              and validation results for human review before application.
 * @doc.layer service
 * @doc.pattern Service
 *
 * P5: Java-owned ReviewBundle service for safe compile-back review lifecycle.
 */
public interface ReviewBundleService {

    /**
     * Create a review bundle for a patch set.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param projectId the project ID
     * @param patchSetId the patch set to review
     * @return promise of the created review bundle
     */
    Promise<ReviewBundle> createReviewBundle(String tenantId, String workspaceId, String projectId, String patchSetId);

    /**
     * Get a review bundle by ID.
     *
     * @param bundleId the review bundle ID
     * @param tenantId the tenant for scope validation
     * @return promise of the review bundle if found
     */
    Promise<java.util.Optional<ReviewBundle>> getReviewBundle(String bundleId, String tenantId);

    /**
     * Approve a review bundle.
     *
     * @param bundleId the review bundle ID
     * @param tenantId the tenant for scope validation
     * @param reviewerId the ID of the approving reviewer
     * @param notes optional review notes
     * @return promise of success
     */
    Promise<Boolean> approveReviewBundle(String bundleId, String tenantId, String reviewerId, String notes);

    /**
     * Reject a review bundle.
     *
     * @param bundleId the review bundle ID
     * @param tenantId the tenant for scope validation
     * @param reviewerId the ID of the rejecting reviewer
     * @param reason the rejection reason
     * @return promise of success
     */
    Promise<Boolean> rejectReviewBundle(String bundleId, String tenantId, String reviewerId, String reason);

    /**
     * List review bundles for a given scope.
     *
     * @param tenantId the tenant ID
     * @param workspaceId optional workspace ID filter
     * @param projectId optional project ID filter
     * @param status optional status filter
     * @return promise of review bundle list
     */
    Promise<List<ReviewBundle>> listReviewBundles(
        String tenantId,
        String workspaceId,
        String projectId,
        ReviewStatus status
    );

    /**
     * Review status.
     */
    enum ReviewStatus {
        PENDING,
        APPROVED,
        REJECTED,
        EXPIRED
    }

    /**
     * Validation result summary in review bundle.
     */
    record ValidationSummary(
        boolean valid,
        int errorCount,
        int warningCount,
        List<ValidationItem> items
    ) {
        public ValidationSummary {
            items = items != null ? List.copyOf(items) : List.of();
        }
    }

    record ValidationItem(
        String code,
        String message,
        String severity, // error, warning
        String filePath,
        String changeId
    ) {}

    /**
     * Residual overlap that requires manual review.
     */
    record ResidualOverlap(
        String residualId,
        String changeId,
        String filePath,
        String reason
    ) {}

    /**
     * Model change summary for review.
     */
    record ModelChangeSummary(
        String changeId,
        String elementId,
        String elementName,
        String elementKind,
        ChangePlanService.ChangeOpKind changeKind,
        String description,
        boolean requiresReview,
        double confidence
    ) {}

    /**
     * Patch summary for review.
     */
    record PatchSummary(
        String patchId,
        String relativePath,
        int linesAdded,
        int linesRemoved,
        boolean isNewFile,
        boolean isDeletedFile
    ) {}

    /**
     * Review bundle containing all information needed for human review.
     */
    record ReviewBundle(
        String bundleId,
        String tenantId,
        String workspaceId,
        String projectId,
        String changePlanId,
        String patchSetId,
        ReviewStatus status,
        List<ModelChangeSummary> changes,
        List<PatchSummary> patches,
        ValidationSummary validation,
        List<ResidualOverlap> residualOverlaps,
        String reviewedBy,
        Instant reviewedAt,
        String reviewNotes,
        Instant createdAt,
        Instant expiresAt
    ) {
        public ReviewBundle {
            Objects.requireNonNull(bundleId, "bundleId must not be null");
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(workspaceId, "workspaceId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            changes = changes != null ? List.copyOf(changes) : List.of();
            patches = patches != null ? List.copyOf(patches) : List.of();
            residualOverlaps = residualOverlaps != null ? List.copyOf(residualOverlaps) : List.of();
        }

        public boolean hasResidualOverlaps() {
            return !residualOverlaps.isEmpty();
        }

        public boolean hasValidationErrors() {
            return validation != null && validation.errorCount() > 0;
        }

        public boolean isPending() {
            return status == ReviewStatus.PENDING;
        }

        public boolean isApproved() {
            return status == ReviewStatus.APPROVED;
        }

        public boolean isRejected() {
            return status == ReviewStatus.REJECTED;
        }

        public boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
    }
}
