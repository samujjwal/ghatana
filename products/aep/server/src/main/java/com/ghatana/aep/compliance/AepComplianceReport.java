/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable report produced by compliance operations (GDPR, CCPA).
 *
 * @doc.type record
 * @doc.purpose Compliance operation result report
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AepComplianceReport(
        String operationType,
        String tenantId,
        String subjectId,
        boolean success,
        String message,
        long total,
        Map<String, Long> breakdown,
        List<String> warnings,
        Instant start,
        Instant end
) {

    /**
     * Creates a failure report for a compliance operation.
     *
     * @param operationType the type of compliance operation
     * @param tenantId      the tenant scope
     * @param subjectId     the data subject identifier
     * @param errorMessage  the error description
     * @return a failure compliance report
     */
    public static AepComplianceReport failure(String operationType, String tenantId,
                                               String subjectId, String errorMessage) {
        return new AepComplianceReport(
                operationType, tenantId, subjectId, false,
                errorMessage, 0L, Map.of(), List.of(),
                Instant.now(), Instant.now()
        );
    }
}
