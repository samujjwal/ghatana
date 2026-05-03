package com.ghatana.digitalmarketing.application.report;

import com.ghatana.digitalmarketing.domain.report.DmPerformanceReport;
import com.ghatana.digitalmarketing.domain.report.DmReportStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for performance report persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for performance report storage (DMOS-F2-019)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmPerformanceReportRepository {

    Promise<DmPerformanceReport> save(DmPerformanceReport report);

    Promise<DmPerformanceReport> update(DmPerformanceReport report);

    Promise<Optional<DmPerformanceReport>> findById(String id);

    Promise<List<DmPerformanceReport>> listByTenant(String tenantId);

    Promise<List<DmPerformanceReport>> listByStatus(String tenantId, DmReportStatus status);
}
