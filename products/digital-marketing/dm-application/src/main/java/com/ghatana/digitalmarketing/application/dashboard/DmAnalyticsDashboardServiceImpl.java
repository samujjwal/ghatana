package com.ghatana.digitalmarketing.application.dashboard;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.dashboard.DmAnalyticsDashboard;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmAnalyticsDashboardService}.
 *
 * @doc.type class
 * @doc.purpose Creates and manages MVP analytics dashboards (DMOS-F2-018)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmAnalyticsDashboardServiceImpl implements DmAnalyticsDashboardService {

    private final DmAnalyticsDashboardRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmAnalyticsDashboardServiceImpl(
            DmAnalyticsDashboardRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmAnalyticsDashboard> create(DmOperationContext ctx, CreateDashboardCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "analytics-dashboards", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to create dashboards"));
                }
                Instant now = Instant.now();
                DmAnalyticsDashboard dashboard = DmAnalyticsDashboard.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .name(command.name())
                    .description(command.description())
                    .widgets(command.widgets())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
                return repository.save(dashboard)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "dashboard-created",
                        Map.of("name", (Object) command.name())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<DmAnalyticsDashboard> update(DmOperationContext ctx, String dashboardId, UpdateDashboardCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(dashboardId, "dashboardId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "analytics-dashboards", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to update dashboards"));
                }
                return loadAndValidateTenant(ctx, dashboardId)
                    .then(existing -> {
                        DmAnalyticsDashboard updated = DmAnalyticsDashboard.builder()
                            .id(existing.getId())
                            .tenantId(existing.getTenantId())
                            .workspaceId(existing.getWorkspaceId())
                            .name(command.name())
                            .description(command.description())
                            .widgets(command.widgets())
                            .createdAt(existing.getCreatedAt())
                            .updatedAt(Instant.now())
                            .build();
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "dashboard-updated",
                                Map.of("name", (Object) command.name())
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<Optional<DmAnalyticsDashboard>> findById(DmOperationContext ctx, String dashboardId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(dashboardId, "dashboardId must not be null");

        return repository.findById(dashboardId)
            .map(opt -> opt.filter(d -> d.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmAnalyticsDashboard>> listByTenant(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "analytics-dashboards", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list dashboards"));
                }
                return repository.listByTenant(ctx.getTenantId().getValue());
            });
    }

    private Promise<DmAnalyticsDashboard> loadAndValidateTenant(DmOperationContext ctx, String dashboardId) {
        return repository.findById(dashboardId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new NoSuchElementException("Dashboard not found: " + dashboardId));
                }
                DmAnalyticsDashboard d = opt.get();
                if (!d.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new SecurityException("Dashboard does not belong to tenant"));
                }
                return Promise.of(d);
            });
    }
}
