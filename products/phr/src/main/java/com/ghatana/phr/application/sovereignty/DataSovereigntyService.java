package com.ghatana.phr.application.sovereignty;

import com.ghatana.phr.application.patient.PatientOperationContext;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Service interface for data sovereignty evidence workflow.
 *
 * @doc.type class
 * @doc.purpose Defines operations for ensuring patient data sovereignty and residency compliance (PHR-F1-007)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DataSovereigntyService {

    /**
     * Get data sovereignty status for a patient.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return data sovereignty status
     */
    Promise<DataSovereigntyStatus> getDataSovereigntyStatus(PatientOperationContext ctx, String patientId);

    /**
     * Validate data residency.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return validation result
     */
    Promise<DataResidencyValidation> validateDataResidency(PatientOperationContext ctx, String patientId);

    /**
     * Get compliance report.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return compliance report
     */
    Promise<ComplianceReport> getComplianceReport(PatientOperationContext ctx, String patientId);

    // ── Response types ─────────────────────────────────────────────────────────

    record DataSovereigntyStatus(
        String patientId,
        String residencyRegion,
        boolean isCompliant,
        String lastVerifiedAt,
        Map<String, String> dataLocations
    ) {}

    record DataResidencyValidation(
        String patientId,
        boolean isValid,
        List<String> violations,
        String validatedAt
    ) {}

    record ComplianceReport(
        String reportId,
        String patientId,
        String generatedAt,
        Map<String, Object> summary,
        List<ComplianceItem> items
    ) {}

    record ComplianceItem(
        String itemId,
        String category,
        String description,
        boolean isCompliant,
        String details
    ) {}
}
