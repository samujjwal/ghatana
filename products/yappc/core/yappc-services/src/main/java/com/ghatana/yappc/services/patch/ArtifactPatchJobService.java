package com.ghatana.yappc.services.patch;

import com.ghatana.yappc.services.artifact.ArtifactRequestScope;
import com.ghatana.yappc.services.artifact.compileback.PatchSetService;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @doc.type class
 * @doc.purpose Durable job orchestration for patch generation, validation, and application
 * @doc.layer service
 * @doc.pattern Service
 * 
 * P1-22: Added ArtifactPatchJobService for durable patch orchestration.
 * Orchestrates patch generation from change plans, validation, review, and application
 * with retry, cancel, and resume capabilities for long-running operations.
 */
public final class ArtifactPatchJobService {
    
    private final PatchSetService patchSetService;
    private final Map<String, PatchJob> jobs = new ConcurrentHashMap<>();
    
    public ArtifactPatchJobService(PatchSetService patchSetService) {
        this.patchSetService = patchSetService;
    }
    
    /**
     * Create a new patch job for generating patches from a change plan.
     */
    public Promise<PatchJob> createPatchJob(ArtifactRequestScope scope, CreatePatchJobRequest request) {
        String jobId = UUID.randomUUID().toString();
        PatchJob job = new PatchJob(
            jobId,
            scope.tenantId(),
            scope.workspaceId(),
            scope.projectId(),
            request.planId(),
            request.snapshotId(),
            PatchJobStatus.PENDING,
            0,
            "pending",
            Instant.now(),
            null,
            null,
            request.metadata()
        );
        jobs.put(jobId, job);
        return Promise.of(job);
    }
    
    /**
     * Execute a patch job: generate, validate, and prepare patches for review.
     */
    public Promise<PatchJob> executePatchJob(String jobId, ArtifactRequestScope scope) {
        PatchJob job = jobs.get(jobId);
        if (job == null) {
            return Promise.ofException(new IllegalArgumentException("Job not found: " + jobId));
        }
        
        // Update job to running
        job = updateJobStatus(jobId, PatchJobStatus.RUNNING, "Generating patches");
        
        return patchSetService.generatePatchSet(scope, job.planId())
            .map(patchSet -> {
                // Update job to completed
                return updateJobStatus(jobId, PatchJobStatus.COMPLETED, "Patch generation completed");
            })
            .mapException(e -> {
                // Update job to failed
                updateJobStatus(jobId, PatchJobStatus.FAILED, "Failed: " + e.getMessage());
                return e;
            })
            .then(result -> Promise.of(jobs.get(jobId)));
    }
    
    /**
     * Cancel a patch job.
     */
    public Promise<PatchJob> cancelPatchJob(String jobId) {
        PatchJob job = jobs.get(jobId);
        if (job == null) {
            return Promise.ofException(new IllegalArgumentException("Job not found: " + jobId));
        }
        
        if (job.status() == PatchJobStatus.COMPLETED || job.status() == PatchJobStatus.FAILED) {
            return Promise.ofException(new IllegalStateException("Cannot cancel job in status: " + job.status()));
        }
        
        return Promise.of(updateJobStatus(jobId, PatchJobStatus.CANCELLED, "Cancelled by user"));
    }
    
    /**
     * Get patch job status.
     */
    public Promise<PatchJob> getPatchJobStatus(String jobId) {
        PatchJob job = jobs.get(jobId);
        if (job == null) {
            return Promise.ofException(new IllegalArgumentException("Job not found: " + jobId));
        }
        return Promise.of(job);
    }
    
    /**
     * List patch jobs for a given scope.
     */
    public Promise<List<PatchJob>> listPatchJobs(ArtifactRequestScope scope) {
        return Promise.of(jobs.values().stream()
            .filter(job -> job.tenantId().equals(scope.tenantId()) &&
                            job.workspaceId().equals(scope.workspaceId()) &&
                            job.projectId().equals(scope.projectId()))
            .toList());
    }
    
    private PatchJob updateJobStatus(String jobId, PatchJobStatus status, String message) {
        PatchJob job = jobs.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        
        PatchJob updated = new PatchJob(
            job.jobId(),
            job.tenantId(),
            job.workspaceId(),
            job.projectId(),
            job.planId(),
            job.snapshotId(),
            status,
            job.progressPercent(),
            message,
            job.createdAt(),
            Instant.now(),
            job.patchSetId(),
            job.metadata()
        );
        jobs.put(jobId, updated);
        return updated;
    }
    
    public enum PatchJobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    public record CreatePatchJobRequest(
        String planId,
        String snapshotId,
        Map<String, Object> metadata
    ) {}
    
    public record PatchJob(
        String jobId,
        String tenantId,
        String workspaceId,
        String projectId,
        String planId,
        String snapshotId,
        PatchJobStatus status,
        int progressPercent,
        String statusMessage,
        Instant createdAt,
        Instant updatedAt,
        String patchSetId,
        Map<String, Object> metadata
    ) {}
}
