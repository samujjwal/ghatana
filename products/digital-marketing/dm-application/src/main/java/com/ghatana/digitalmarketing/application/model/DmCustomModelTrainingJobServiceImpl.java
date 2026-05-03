package com.ghatana.digitalmarketing.application.model;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.model.DmCustomModelTrainingJob;
import com.ghatana.digitalmarketing.domain.model.DmCustomModelTrainingStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmCustomModelTrainingJobService}.
 *
 * @doc.type class
 * @doc.purpose Manages custom model training job lifecycle (DMOS-F5-004)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DmCustomModelTrainingJobServiceImpl implements DmCustomModelTrainingJobService {

    private final DmCustomModelTrainingJobRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmCustomModelTrainingJobServiceImpl(
            DmCustomModelTrainingJobRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmCustomModelTrainingJob> enqueue(DmOperationContext ctx, EnqueueTrainingJobCommand cmd) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(cmd, "cmd must not be null");
        return kernelAdapter.isAuthorized(ctx, "custom-model-training", "enqueue")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to enqueue training job"));
                DmCustomModelTrainingJob job = DmCustomModelTrainingJob.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(cmd.workspaceId() != null ? cmd.workspaceId() : ctx.getWorkspaceId().getValue())
                    .modelName(cmd.modelName())
                    .baseModelId(cmd.baseModelId())
                    .trainingDataRef(cmd.trainingDataRef())
                    .status(DmCustomModelTrainingStatus.QUEUED)
                    .createdAt(Instant.now())
                    .build();
                return repository.save(job);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "training-job.enqueue",
                Map.of("modelName", (Object) saved.getModelName()))
                .map(__ -> saved));
    }

    @Override
    public Promise<DmCustomModelTrainingJob> markTraining(DmOperationContext ctx, String jobId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(jobId, "jobId must not be null");
        return repository.findById(jobId)
            .then(opt -> {
                DmCustomModelTrainingJob existing = opt.orElseThrow(() -> new NoSuchElementException("Training job not found: " + jobId));
                DmCustomModelTrainingJob updated = copyBuilder(existing)
                    .status(DmCustomModelTrainingStatus.TRAINING)
                    .startedAt(Instant.now())
                    .build();
                return repository.update(updated);
            });
    }

    @Override
    public Promise<DmCustomModelTrainingJob> markEvaluating(DmOperationContext ctx, String jobId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(jobId, "jobId must not be null");
        return repository.findById(jobId)
            .then(opt -> {
                DmCustomModelTrainingJob existing = opt.orElseThrow(() -> new NoSuchElementException("Training job not found: " + jobId));
                DmCustomModelTrainingJob updated = copyBuilder(existing)
                    .status(DmCustomModelTrainingStatus.EVALUATING)
                    .build();
                return repository.update(updated);
            });
    }

    @Override
    public Promise<DmCustomModelTrainingJob> markComplete(DmOperationContext ctx, String jobId, double evalScore, String artifactRef) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(jobId, "jobId must not be null");
        return repository.findById(jobId)
            .then(opt -> {
                DmCustomModelTrainingJob existing = opt.orElseThrow(() -> new NoSuchElementException("Training job not found: " + jobId));
                DmCustomModelTrainingJob updated = copyBuilder(existing)
                    .status(DmCustomModelTrainingStatus.COMPLETE)
                    .bestEvalScore(evalScore)
                    .artifactRef(artifactRef)
                    .completedAt(Instant.now())
                    .build();
                return repository.update(updated);
            })
            .then(updated -> kernelAdapter.recordAudit(ctx, updated.getId(), "training-job.complete",
                Map.of("modelName", (Object) updated.getModelName()))
                .map(__ -> updated));
    }

    @Override
    public Promise<DmCustomModelTrainingJob> markFailed(DmOperationContext ctx, String jobId, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(jobId, "jobId must not be null");
        return repository.findById(jobId)
            .then(opt -> {
                DmCustomModelTrainingJob existing = opt.orElseThrow(() -> new NoSuchElementException("Training job not found: " + jobId));
                DmCustomModelTrainingJob updated = copyBuilder(existing)
                    .status(DmCustomModelTrainingStatus.FAILED)
                    .failureReason(reason)
                    .completedAt(Instant.now())
                    .build();
                return repository.update(updated);
            });
    }

    @Override
    public Promise<DmCustomModelTrainingJob> cancel(DmOperationContext ctx, String jobId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(jobId, "jobId must not be null");
        return kernelAdapter.isAuthorized(ctx, "custom-model-training", "cancel")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to cancel training job"));
                return repository.findById(jobId);
            })
            .then(opt -> {
                DmCustomModelTrainingJob existing = opt.orElseThrow(() -> new NoSuchElementException("Training job not found: " + jobId));
                if (!existing.getTenantId().equals(ctx.getTenantId().getValue()))
                    return Promise.ofException(new SecurityException("Training job does not belong to tenant"));
                DmCustomModelTrainingJob updated = copyBuilder(existing)
                    .status(DmCustomModelTrainingStatus.CANCELLED)
                    .build();
                return repository.update(updated);
            });
    }

    @Override
    public Promise<Optional<DmCustomModelTrainingJob>> findById(DmOperationContext ctx, String jobId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(jobId, "jobId must not be null");
        return repository.findById(jobId);
    }

    @Override
    public Promise<List<DmCustomModelTrainingJob>> listByTenant(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.listByTenant(ctx.getTenantId().getValue());
    }

    private static DmCustomModelTrainingJob.Builder copyBuilder(DmCustomModelTrainingJob j) {
        return DmCustomModelTrainingJob.builder()
            .id(j.getId()).tenantId(j.getTenantId()).workspaceId(j.getWorkspaceId())
            .modelName(j.getModelName()).baseModelId(j.getBaseModelId())
            .trainingDataRef(j.getTrainingDataRef())
            .bestEvalScore(j.getBestEvalScore()).failureReason(j.getFailureReason())
            .artifactRef(j.getArtifactRef()).startedAt(j.getStartedAt())
            .completedAt(j.getCompletedAt()).createdAt(j.getCreatedAt());
    }
}
