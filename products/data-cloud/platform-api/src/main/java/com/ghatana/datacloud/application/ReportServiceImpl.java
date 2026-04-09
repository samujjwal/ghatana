/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.application;

import com.ghatana.datacloud.analytics.Report;
import com.ghatana.datacloud.analytics.ReportRepository;
import com.ghatana.datacloud.analytics.ReportService;
import com.ghatana.datacloud.analytics.ReportTemplate;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of ReportService for report generation and management.
 *
 * @doc.type class
 * @doc.purpose Concrete implementation of report operations
 * @doc.layer application
 * @doc.pattern Service Implementation
 */
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private final ReportRepository repository;
    private final MetricsCollector metrics;

    public ReportServiceImpl(ReportRepository repository, MetricsCollector metrics) {
        this.repository = Objects.requireNonNull(repository, "Repository required");
        this.metrics = Objects.requireNonNull(metrics, "Metrics required");
    }

    @Override
    public Promise<Report> generateReport(String tenantId, ReportTemplate template) {
        validateTenantId(tenantId);
        Objects.requireNonNull(template, "Template required");

        Report report = new Report()
            .withId(UUID.randomUUID().toString())
            .withTenantId(tenantId)
            .withName(template.getName())
            .withFormat(template.getFormat())
            .withStatus("PENDING")
            .withCreatedAt(System.currentTimeMillis())
            .withCreatedBy("system");

        return repository.save(report)
            .then(saved -> executeReportGeneration(saved, template))
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("report.generate.success",
                        "tenant", tenantId, "format", template.getFormat());
                } else {
                    metrics.incrementCounter("report.generate.error",
                        "tenant", tenantId, "error", ex.getClass().getSimpleName());
                }
            });
    }

    @Override
    public Promise<Report> getReport(String tenantId, String reportId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(reportId, "Report ID required");

        return repository.findById(tenantId, reportId)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    if (result != null) {
                        metrics.incrementCounter("report.get.success", "tenant", tenantId);
                    } else {
                        metrics.incrementCounter("report.get.not_found", "tenant", tenantId);
                    }
                } else {
                    metrics.incrementCounter("report.get.error",
                        "tenant", tenantId, "error", ex.getClass().getSimpleName());
                }
            });
    }

    @Override
    public Promise<List<Report>> listReports(String tenantId) {
        validateTenantId(tenantId);

        return repository.findAllByTenant(tenantId)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.increment("report.list.count", result.size(), Map.of("tenant", tenantId));
                } else {
                    metrics.incrementCounter("report.list.error",
                        "tenant", tenantId, "error", ex.getClass().getSimpleName());
                }
            });
    }

    @Override
    public Promise<Report> updateReport(String tenantId, String reportId,
                                         Map<String, String> updates) {
        validateTenantId(tenantId);
        Objects.requireNonNull(reportId, "Report ID required");
        Objects.requireNonNull(updates, "Updates required");

        return repository.findById(tenantId, reportId)
            .then(existing -> {
                if (existing == null) {
                    return Promise.ofException(
                        new IllegalArgumentException("Report not found: " + reportId));
                }

                Report updated = new Report()
                    .withId(existing.getId())
                    .withTenantId(existing.getTenantId())
                    .withName(updates.getOrDefault("name", existing.getName()))
                    .withFormat(updates.getOrDefault("format", existing.getFormat()))
                    .withStatus(updates.getOrDefault("status", existing.getStatus()))
                    .withCreatedAt(existing.getCreatedAt())
                    .withCreatedBy(existing.getCreatedBy());

                return repository.save(updated);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("report.update.success", "tenant", tenantId);
                } else {
                    metrics.incrementCounter("report.update.error",
                        "tenant", tenantId, "error", ex.getClass().getSimpleName());
                }
            });
    }

    @Override
    public Promise<Void> deleteReport(String tenantId, String reportId) {
        validateTenantId(tenantId);
        Objects.requireNonNull(reportId, "Report ID required");

        return repository.delete(tenantId, reportId)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("report.delete.success", "tenant", tenantId);
                } else {
                    metrics.incrementCounter("report.delete.error",
                        "tenant", tenantId, "error", ex.getClass().getSimpleName());
                }
            });
    }

    @Override
    public Promise<byte[]> downloadReport(String tenantId, String reportId, String format) {
        validateTenantId(tenantId);
        Objects.requireNonNull(reportId, "Report ID required");
        Objects.requireNonNull(format, "Format required");

        return repository.findById(tenantId, reportId)
            .then(report -> {
                if (report == null) {
                    return Promise.ofException(
                        new IllegalArgumentException("Report not found: " + reportId));
                }

                // Generate binary based on format
                byte[] data = generateReportBinary(report, format);
                return Promise.of(data);
            })
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    metrics.incrementCounter("report.download.success",
                        "tenant", tenantId, "format", format);
                } else {
                    metrics.incrementCounter("report.download.error",
                        "tenant", tenantId, "error", ex.getClass().getSimpleName());
                }
            });
    }

    private Promise<Report> executeReportGeneration(Report report, ReportTemplate template) {
        // Simulate async report generation
        return Promise.of(report.withStatus("COMPLETED"));
    }

    private byte[] generateReportBinary(Report report, String format) {
        // Mock implementation - would generate actual report bytes
        return ("Report: " + report.getName() + " in " + format).getBytes();
    }

    private void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID required");
        }
    }
}
