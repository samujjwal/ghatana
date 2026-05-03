package com.ghatana.digitalmarketing.application.budget;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.budget.DmBudgetAlert;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmBudgetAlertService}.
 *
 * @doc.type class
 * @doc.purpose Fires and acknowledges budget pacing alerts (DMOS-F3-002)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmBudgetAlertServiceImpl implements DmBudgetAlertService {

    private final DmBudgetAlertRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmBudgetAlertServiceImpl(
            DmBudgetAlertRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmBudgetAlert> fire(DmOperationContext ctx, FireBudgetAlertCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "budget-alerts", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to fire budget alerts"));
                }
                DmBudgetAlert alert = DmBudgetAlert.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .campaignId(command.campaignId())
                    .totalBudgetMicros(command.totalBudgetMicros())
                    .spentMicros(command.spentMicros())
                    .pacingRatio(command.pacingRatio())
                    .level(command.level())
                    .message(command.message())
                    .acknowledged(false)
                    .firedAt(Instant.now())
                    .build();
                return repository.save(alert)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "budget-alert-fired",
                        Map.of("campaignId", (Object) command.campaignId(), "level", (Object) command.level().name())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<DmBudgetAlert> acknowledge(DmOperationContext ctx, String alertId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(alertId, "alertId must not be null");

        return kernelAdapter.isAuthorized(ctx, "budget-alerts", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to acknowledge budget alerts"));
                }
                return loadAndValidateTenant(ctx, alertId)
                    .then(existing -> {
                        DmBudgetAlert acknowledged = existing.acknowledge();
                        return repository.update(acknowledged)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "budget-alert-acknowledged", Map.of()
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<Optional<DmBudgetAlert>> findById(DmOperationContext ctx, String alertId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.findById(alertId)
            .map(opt -> opt.filter(a -> a.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmBudgetAlert>> listByCampaign(DmOperationContext ctx, String campaignId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");

        return kernelAdapter.isAuthorized(ctx, "budget-alerts", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list budget alerts"));
                }
                return repository.listByCampaign(ctx.getTenantId().getValue(), campaignId);
            });
    }

    @Override
    public Promise<List<DmBudgetAlert>> listUnacknowledged(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "budget-alerts", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list budget alerts"));
                }
                return repository.listUnacknowledged(ctx.getTenantId().getValue());
            });
    }

    private Promise<DmBudgetAlert> loadAndValidateTenant(DmOperationContext ctx, String alertId) {
        return repository.findById(alertId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new NoSuchElementException("Alert not found: " + alertId));
                }
                DmBudgetAlert a = opt.get();
                if (!a.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new SecurityException("Alert does not belong to tenant"));
                }
                return Promise.of(a);
            });
    }
}
