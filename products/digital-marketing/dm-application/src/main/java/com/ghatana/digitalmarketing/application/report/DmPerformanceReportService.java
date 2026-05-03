package com.ghatana.digitalmarketing.application.report;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.report.DmPerformanceReport;
import com.ghatana.digitalmarketing.domain.report.DmPerformanceReport.DmReportPeriod;
import com.ghatana.digitalmarketing.domain.report.DmReportStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for performance report lifecycle.
 *
 * @doc.type interface
 * @doc.purpose Basic performance report generation and lifecycle management (DMOS-F2-019)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmPerformanceReportService {

    Promise<DmPerformanceReport> generate(DmOperationContext ctx, GenerateReportCommand command);

    Promise<DmPerformanceReport> markReady(DmOperationContext ctx, String reportId);

    Promise<DmPerformanceReport> markFailed(DmOperationContext ctx, String reportId);

    Promise<Optional<DmPerformanceReport>> findById(DmOperationContext ctx, String reportId);

    Promise<List<DmPerformanceReport>> listByTenant(DmOperationContext ctx);

    Promise<List<DmPerformanceReport>> listByStatus(DmOperationContext ctx, DmReportStatus status);

    /**
     * Command to generate a performance report.
     */
    record GenerateReportCommand(
        String title,
        DmReportPeriod period
    ) {
        public GenerateReportCommand {
            Objects.requireNonNull(title, "title must not be null");
            Objects.requireNonNull(period, "period must not be null");
            if (title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        }
    }
}
