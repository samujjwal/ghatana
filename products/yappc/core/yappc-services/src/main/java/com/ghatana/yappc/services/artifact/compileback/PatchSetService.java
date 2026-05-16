package com.ghatana.yappc.services.artifact.compileback;

import com.ghatana.yappc.services.artifact.ArtifactRequestScope;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type interface
 * @doc.purpose Service for managing patch sets - file-level text patches generated from change plans.
 *              Handles patch generation, storage, and lifecycle for compile-back operations.
 * @doc.layer service
 * @doc.pattern Service
 *
 * P5: Java-owned PatchSet orchestration for safe minimal patches.
 */
public interface PatchSetService {

    /**
     * Generate a patch set from a change plan.
     *
     * @param scope the request scope
     * @param planId the change plan ID
     * @return promise of the generated patch set
     */
    Promise<PatchSet> generatePatchSet(ArtifactRequestScope scope, String planId);

    /**
     * Get a patch set by ID.
     *
     * @param patchSetId the patch set ID
     * @param tenantId the tenant for scope validation
     * @return promise of the patch set if found
     */
    Promise<java.util.Optional<PatchSet>> getPatchSet(String patchSetId, String tenantId);

    /**
     * Apply a patch set to the working tree.
     *
     * @param patchSetId the patch set ID
     * @param tenantId the tenant for scope validation
     * @param dryRun if true, only validate without applying
     * @return promise of application result
     */
    Promise<PatchApplicationResult> applyPatchSet(String patchSetId, String tenantId, boolean dryRun);

    /**
     * Rollback a previously applied patch set.
     *
     * @param patchSetId the patch set ID
     * @param tenantId the tenant for scope validation
     * @param reason the reason for rollback
     * @return promise of rollback result
     */
    Promise<RollbackResult> rollbackPatchSet(String patchSetId, String tenantId, String reason);

    /**
     * List patch sets for a given scope.
     *
     * @param tenantId the tenant ID
     * @param workspaceId optional workspace ID filter
     * @param projectId optional project ID filter
     * @return promise of patch set list
     */
    Promise<List<PatchSet>> listPatchSets(String tenantId, String workspaceId, String projectId);

    /**
     * Patch validation status.
     */
    enum PatchValidationStatus {
        PENDING,
        VALIDATED,
        REVIEW_REQUIRED,
        CONFLICTED
    }

    /**
     * Patch set status.
     */
    enum PatchSetStatus {
        PENDING,
        REVIEW_REQUIRED,
        APPROVED,
        REJECTED,
        APPLIED,
        ROLLED_BACK
    }

    /**
     * Individual text patch.
     */
    record TextPatch(
        String patchId,
        String relativePath,
        String diff, // Unified diff format
        List<PatchRange> ranges,
        boolean isAtomic,
        String sourceChangeOpId,
        String emitterId,
        String baseChecksum,
        String targetChecksum,
        PatchValidationStatus validationStatus
    ) {
        public TextPatch {
            Objects.requireNonNull(patchId, "patchId must not be null");
            Objects.requireNonNull(relativePath, "relativePath must not be null");
            Objects.requireNonNull(diff, "diff must not be null");
            ranges = ranges != null ? List.copyOf(ranges) : List.of();
        }
    }

    /**
     * Patch range for precise application.
     */
    record PatchRange(
        int startLine,
        int startColumn,
        int endLine,
        int endColumn,
        String nodeType
    ) {}

    /**
     * Patch set containing multiple patches.
     */
    record PatchSet(
        String patchSetId,
        String tenantId,
        String workspaceId,
        String projectId,
        String planId,
        String snapshotId,
        PatchSetStatus status,
        List<TextPatch> patches,
        List<String> preservedResiduals,
        List<String> reviewRequiredPatches,
        PatchStats stats,
        Instant createdAt,
        String createdBy,
        Instant appliedAt,
        String appliedBy
    ) {
        public PatchSet {
            Objects.requireNonNull(patchSetId, "patchSetId must not be null");
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(workspaceId, "workspaceId must not be null");
            Objects.requireNonNull(projectId, "projectId must not be null");
            patches = patches != null ? List.copyOf(patches) : List.of();
            preservedResiduals = preservedResiduals != null ? List.copyOf(preservedResiduals) : List.of();
            reviewRequiredPatches = reviewRequiredPatches != null ? List.copyOf(reviewRequiredPatches) : List.of();
        }

        public boolean requiresReview() {
            return status == PatchSetStatus.REVIEW_REQUIRED ||
                   !reviewRequiredPatches.isEmpty() ||
                   patches.stream().anyMatch(p -> p.validationStatus() == PatchValidationStatus.REVIEW_REQUIRED);
        }

        public int getAutoApplicableCount() {
            return (int) patches.stream()
                .filter(p -> p.validationStatus() == PatchValidationStatus.VALIDATED)
                .count();
        }
    }

    /**
     * Patch statistics.
     */
    record PatchStats(
        int totalPatches,
        int autoApplicable,
        int requiresReview,
        int conflicted,
        int preservedResiduals
    ) {}

    /**
     * Patch application result.
     */
    record PatchApplicationResult(
        String patchSetId,
        boolean success,
        boolean dryRun,
        List<AppliedPatch> appliedPatches,
        List<PatchFailure> failures,
        Instant appliedAt,
        String appliedBy,
        Map<String, Object> summary
    ) {
        public PatchApplicationResult {
            appliedPatches = appliedPatches != null ? List.copyOf(appliedPatches) : List.of();
            failures = failures != null ? List.copyOf(failures) : List.of();
            summary = summary != null ? Map.copyOf(summary) : Map.of();
        }
    }

    record AppliedPatch(
        String patchId,
        String relativePath,
        String appliedChecksum
    ) {}

    record PatchFailure(
        String patchId,
        String relativePath,
        String errorCode,
        String errorMessage
    ) {}

    /**
     * Rollback result.
     */
    record RollbackResult(
        String patchSetId,
        boolean success,
        String originalPatchSetId,
        String rollbackPatchSetId,
        String rolledBackBy,
        Instant rolledBackAt,
        String reason,
        String error
    ) {}
}
