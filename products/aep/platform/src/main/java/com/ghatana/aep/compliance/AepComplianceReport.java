/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable compliance report capturing the outcome of a data-subject rights operation
 * or a compliance audit cycle.
 *
 * @doc.type record
 * @doc.purpose Compliance report DTO for GDPR/CCPA/SOC2 operations
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AepComplianceReport(
        /** Type of compliance operation (e.g., {@code GDPR_ERASURE}, {@code CCPA_ACCESS}). */
        String operation,
        /** Tenant in whose scope the operation was executed. */
        String tenantId,
        /** Data-subject identifier the operation targeted (may be null for audit scans). */
        String subjectId,
        /** Whether the operation completed without errors. */
        boolean success,
        /** Human-readable summary of the operation outcome. */
        String summary,
        /** Number of records affected (deleted, exported, redacted, etc.). */
        long recordsAffected,
        /** Per-collection breakdown: collection name → count affected. */
        Map<String, Long> breakdown,
        /** Non-fatal issues encountered during the operation. */
        List<String> warnings,
        /** Timestamp at which the operation started. */
        Instant startedAt,
        /** Timestamp at which the operation completed. */
        Instant completedAt
) {
    /**
     * Constructs a successful single-subject report with no per-collection breakdown.
     */
    public static AepComplianceReport success(String operation, String tenantId,
                                              String subjectId, long recordsAffected,
                                              String summary) {
        return new AepComplianceReport(
                operation, tenantId, subjectId, true, summary,
                recordsAffected, Map.of(), List.of(),
                Instant.now(), Instant.now());
    }

    /**
     * Constructs a failed report.
     */
    public static AepComplianceReport failure(String operation, String tenantId,
                                              String subjectId, String reason) {
        return new AepComplianceReport(
                operation, tenantId, subjectId, false, "FAILED: " + reason,
                0L, Map.of(), List.of(),
                Instant.now(), Instant.now());
    }
}
