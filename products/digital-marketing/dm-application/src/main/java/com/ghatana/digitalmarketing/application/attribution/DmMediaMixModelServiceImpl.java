package com.ghatana.digitalmarketing.application.attribution;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.attribution.DmMediaMixModel;
import com.ghatana.digitalmarketing.domain.attribution.DmMediaMixModel.DmChannelContribution;
import com.ghatana.digitalmarketing.domain.attribution.DmMediaMixModelStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmMediaMixModelService}.
 *
 * @doc.type class
 * @doc.purpose Manages advanced attribution media mix model lifecycle (DMOS-F5-003)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DmMediaMixModelServiceImpl implements DmMediaMixModelService {

    private final DmMediaMixModelRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmMediaMixModelServiceImpl(
            DmMediaMixModelRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmMediaMixModel> submit(DmOperationContext ctx, SubmitMediaMixModelCommand cmd) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(cmd, "cmd must not be null");
        return kernelAdapter.isAuthorized(ctx, "media-mix-model", "submit")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to submit media mix model"));
                DmMediaMixModel model = DmMediaMixModel.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(cmd.workspaceId() != null ? cmd.workspaceId() : ctx.getWorkspaceId().getValue())
                    .modelName(cmd.modelName())
                    .channelIds(cmd.channelIds())
                    .contributions(List.of())
                    .dataFrom(cmd.dataFrom())
                    .dataTo(cmd.dataTo())
                    .status(DmMediaMixModelStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();
                return repository.save(model);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "media-mix-model.submit",
                Map.of("modelName", (Object) saved.getModelName()))
                .map(__ -> saved));
    }

    @Override
    public Promise<DmMediaMixModel> markFitting(DmOperationContext ctx, String modelId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(modelId, "modelId must not be null");
        return kernelAdapter.isAuthorized(ctx, "media-mix-model", "update")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to update media mix model"));
                return repository.findById(modelId);
            })
            .then(opt -> {
                DmMediaMixModel existing = opt.orElseThrow(() -> new NoSuchElementException("Model not found: " + modelId));
                DmMediaMixModel updated = DmMediaMixModel.builder()
                    .id(existing.getId()).tenantId(existing.getTenantId()).workspaceId(existing.getWorkspaceId())
                    .modelName(existing.getModelName()).channelIds(existing.getChannelIds())
                    .contributions(existing.getContributions()).dataFrom(existing.getDataFrom()).dataTo(existing.getDataTo())
                    .status(DmMediaMixModelStatus.FITTING).rSquared(existing.getRSquared())
                    .createdAt(existing.getCreatedAt()).build();
                return repository.update(updated);
            });
    }

    @Override
    public Promise<DmMediaMixModel> markReady(DmOperationContext ctx, String modelId, double rSquared, List<DmChannelContribution> contributions) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(modelId, "modelId must not be null");
        Objects.requireNonNull(contributions, "contributions must not be null");
        return repository.findById(modelId)
            .then(opt -> {
                DmMediaMixModel existing = opt.orElseThrow(() -> new NoSuchElementException("Model not found: " + modelId));
                DmMediaMixModel updated = DmMediaMixModel.builder()
                    .id(existing.getId()).tenantId(existing.getTenantId()).workspaceId(existing.getWorkspaceId())
                    .modelName(existing.getModelName()).channelIds(existing.getChannelIds())
                    .contributions(contributions).dataFrom(existing.getDataFrom()).dataTo(existing.getDataTo())
                    .status(DmMediaMixModelStatus.READY).rSquared(rSquared).fittedAt(Instant.now())
                    .createdAt(existing.getCreatedAt()).build();
                return repository.update(updated);
            })
            .then(updated -> kernelAdapter.recordAudit(ctx, updated.getId(), "media-mix-model.ready",
                Map.of("rSquared", (Object) updated.getRSquared()))
                .map(__ -> updated));
    }

    @Override
    public Promise<DmMediaMixModel> markFailed(DmOperationContext ctx, String modelId, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(modelId, "modelId must not be null");
        return repository.findById(modelId)
            .then(opt -> {
                DmMediaMixModel existing = opt.orElseThrow(() -> new NoSuchElementException("Model not found: " + modelId));
                DmMediaMixModel updated = DmMediaMixModel.builder()
                    .id(existing.getId()).tenantId(existing.getTenantId()).workspaceId(existing.getWorkspaceId())
                    .modelName(existing.getModelName()).channelIds(existing.getChannelIds())
                    .contributions(existing.getContributions()).dataFrom(existing.getDataFrom()).dataTo(existing.getDataTo())
                    .status(DmMediaMixModelStatus.FAILED).failureReason(reason)
                    .createdAt(existing.getCreatedAt()).build();
                return repository.update(updated);
            });
    }

    @Override
    public Promise<Optional<DmMediaMixModel>> findById(DmOperationContext ctx, String modelId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(modelId, "modelId must not be null");
        return repository.findById(modelId);
    }

    @Override
    public Promise<List<DmMediaMixModel>> listByTenant(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.listByTenant(ctx.getTenantId().getValue());
    }
}
