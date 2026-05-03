package com.ghatana.digitalmarketing.domain.report;

/**
 * Status of a performance report.
 *
 * @doc.type class
 * @doc.purpose Lifecycle status for generated reports (DMOS-F2-019)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmReportStatus {
    PENDING,
    GENERATING,
    READY,
    FAILED
}
