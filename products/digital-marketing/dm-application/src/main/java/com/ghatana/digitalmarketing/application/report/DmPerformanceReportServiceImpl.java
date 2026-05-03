package com.ghatana.digitalmarketing.application.report;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.report.DmPerformanceReport;
import com.ghatana.digitalmarketing.domain.report.DmReportStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmPerformanceReportService}.
 *
 * @doc.type class
 * @doc.purpose Generates and manages performance reports (DMOS-F2-019)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmPerformanceReportServiceImpl implements DmPerformanceReportService {

    private final DmPerformanceReportRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmPerformanceReportServiceImpl(
            DmPerformanceReportRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmPerformanceReport> generate(DmOperationContext ctx, GenerateReportCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "performance-reports", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to generate reports"));
                }
                DmPerformanceReport report = DmPerformanceReport.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .title(command.title())
                    .period(command.period())
                    .sections(List.of())
                    .status(DmReportStatus.PENDING)
                    .generatedByActor(ctx.getActor().getPrincipalId())
                    .createdAt(Instant.now())
                    .build();
                return repository.save(report)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "performance-report-generated",
                        Map.of("title", (Object) command.title())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<DmPerformanceReport> markReady(DmOperationContext ctx, String reportId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(reportId, "reportId must not be null");

        return kernelAdapter.isAuthorized(ctx, "performance-reports", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to update reports"));
                }
                return loadAndValidateTenant(ctx, reportId)
                    .then(existing -> {
                        if (existing.getStatus() == DmReportStatus.READY) {
                            return Promise.ofException(new IllegalStateException("Report is already READY"));
                        }
                        DmPerformanceReport updated = DmPerformanceReport.builder()
                            .id(existing.getId())
                            .tenantId(existing.getTenantId())
                            .workspaceId(existing.getWorkspaceId())
                            .title(existing.getTitle())
                            .period(existing.getPeriod())
                            .sections(existing.getSections())
                            .status(DmReportStatus.READY)
                            .generatedByActor(existing.getGeneratedByActor())
                            .generatedAt(Instant.now())
                            .createdAt(existing.getCreatedAt())
                            .build();
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "performance-report-ready", Map.of()
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<DmPerformanceReport> markFailed(DmOperationContext ctx, String reportId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(reportId, "reportId must not be null");

        return kernelAdapter.isAuthorized(ctx, "performance-reports", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to update reports"));
                }
                return loadAndValidateTenant(ctx, reportId)
                    .then(existing -> {
                        DmPerformanceReport updated = DmPerformanceReport.builder()
                            .id(existing.getId())
                            .tenantId(existing.getTenantId())
                            .workspaceId(existing.getWorkspaceId())
                            .title(existing.getTitle())
                            .period(existing.getPeriod())
                            .sections(existing.getSections())
                            .status(DmReportStatus.FAILED)
                            .generatedByActor(existing.getGeneratedByActor())
                            .generatedAt(existing.getGeneratedAt())
                            .createdAt(existing.getCreatedAt())
                            .build();
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "performance-report-failed", Map.of()
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<Optional<DmPerformanceReport>> findById(DmOperationContext ctx, String reportId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(reportId, "reportId must not be null");

        return repository.findById(reportId)
            .map(opt -> opt.filter(r -> r.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmPerformanceReport>> listByTenant(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "performance-reports", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list reports"));
                }
                return repository.listByTenant(ctx.getTenantId().getValue());
            });
    }

    @Override
    public Promise<List<DmPerformanceReport>> listByStatus(DmOperationContext ctx, DmReportStatus status) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(status, "status must not be null");

        return kernelAdapter.isAuthorized(ctx, "performance-reports", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list reports"));
                }
                return repository.listByStatus(ctx.getTenantId().getValue(), status);
            });
    }

    private Promise<DmPerformanceReport> loadAndValidateTenant(DmOperationContext ctx, String reportId) {
        return repository.findById(reportId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new NoSuchElementException("Report not found: " + reportId));
                }
                DmPerformanceReport r = opt.get();
                if (!r.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new SecurityException("Report does not belong to tenant"));
                }
                return Promise.of(r);
            });
    }
}
