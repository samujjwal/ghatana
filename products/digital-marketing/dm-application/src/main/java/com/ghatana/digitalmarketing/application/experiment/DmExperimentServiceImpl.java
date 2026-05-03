package com.ghatana.digitalmarketing.application.experiment;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.experiment.DmExperiment;
import com.ghatana.digitalmarketing.domain.experiment.DmExperimentStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmExperimentService}.
 *
 * @doc.type class
 * @doc.purpose Creates and manages A/B experiment lifecycle (DMOS-F3-003)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmExperimentServiceImpl implements DmExperimentService {

    private final DmExperimentRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmExperimentServiceImpl(
            DmExperimentRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmExperiment> create(DmOperationContext ctx, CreateExperimentCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "experiments", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to create experiments"));
                }
                DmExperiment experiment = DmExperiment.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .name(command.name())
                    .hypothesis(command.hypothesis())
                    .variants(command.variants())
                    .status(DmExperimentStatus.DRAFT)
                    .createdAt(Instant.now())
                    .build();
                return repository.save(experiment)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "experiment-created",
                        Map.of("name", (Object) command.name())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<DmExperiment> start(DmOperationContext ctx, String experimentId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "experiments", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to start experiments"));
                }
                return loadAndValidateTenant(ctx, experimentId)
                    .then(existing -> {
                        DmExperiment started = existing.start();
                        return repository.update(started)
                            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "experiment-started", Map.of()).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<DmExperiment> conclude(DmOperationContext ctx, String experimentId, String winnerVariantId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(winnerVariantId, "winnerVariantId must not be null");

        return kernelAdapter.isAuthorized(ctx, "experiments", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to conclude experiments"));
                }
                return loadAndValidateTenant(ctx, experimentId)
                    .then(existing -> {
                        DmExperiment concluded = existing.conclude(winnerVariantId);
                        return repository.update(concluded)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "experiment-concluded",
                                Map.of("winner", (Object) winnerVariantId)
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<DmExperiment> cancel(DmOperationContext ctx, String experimentId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "experiments", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to cancel experiments"));
                }
                return loadAndValidateTenant(ctx, experimentId)
                    .then(existing -> {
                        DmExperiment cancelled = existing.toBuilder().status(DmExperimentStatus.CANCELLED).build();
                        return repository.update(cancelled)
                            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "experiment-cancelled", Map.of()).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<Optional<DmExperiment>> findById(DmOperationContext ctx, String experimentId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.findById(experimentId)
            .map(opt -> opt.filter(e -> e.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmExperiment>> listByTenant(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "experiments", "read")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(new SecurityException("Actor not authorised to list experiments"));
                return repository.listByTenant(ctx.getTenantId().getValue());
            });
    }

    @Override
    public Promise<List<DmExperiment>> listByStatus(DmOperationContext ctx, DmExperimentStatus status) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "experiments", "read")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(new SecurityException("Actor not authorised to list experiments"));
                return repository.listByStatus(ctx.getTenantId().getValue(), status);
            });
    }

    private Promise<DmExperiment> loadAndValidateTenant(DmOperationContext ctx, String id) {
        return repository.findById(id)
            .then(opt -> {
                if (opt.isEmpty()) return Promise.ofException(new NoSuchElementException("Experiment not found: " + id));
                DmExperiment e = opt.get();
                if (!e.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new SecurityException("Experiment does not belong to tenant"));
                }
                return Promise.of(e);
            });
    }
}
