package com.ghatana.phr.application.audit;

import com.ghatana.phr.application.patient.PatientOperationContext;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Service interface for access audit history workflow.
 *
 * @doc.type class
 * @doc.purpose Defines operations for tracking and auditing patient data access (PHR-F1-005)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface AccessAuditService {

    /**
     * Get access log for a patient.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return access log
     */
    Promise<AccessLog> getAccessLog(PatientOperationContext ctx, String patientId);

    /**
     * Get access summary.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return access summary
     */
    Promise<AccessSummary> getAccessSummary(PatientOperationContext ctx, String patientId);

    /**
     * Get access anomalies.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return access anomalies
     */
    Promise<List<AccessAnomaly>> getAccessAnomalies(PatientOperationContext ctx, String patientId);

    /**
     * Generate audit report.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return audit report
     */
    Promise<AuditReport> generateAuditReport(PatientOperationContext ctx, String patientId);

    // ── Response types ─────────────────────────────────────────────────────────

    record AccessLog(
        String patientId,
        List<AccessEvent> events,
        String generatedAt
    ) {}

    record AccessEvent(
        String eventId,
        String timestamp,
        String userId,
        String action,
        String resource,
        String ipAddress,
        String userAgent,
        boolean authorized
    ) {}

    record AccessSummary(
        String patientId,
        int totalAccesses,
        int authorizedAccesses,
        int unauthorizedAccesses,
        Map<String, Integer> accessByUser,
        Map<String, Integer> accessByAction,
        String lastAccessAt
    ) {}

    record AccessAnomaly(
        String anomalyId,
        String timestamp,
        String type,
        String description,
        String userId,
        String severity
    ) {}

    record AuditReport(
        String reportId,
        String patientId,
        String generatedAt,
        String generatedBy,
        Map<String, Object> summary,
        List<AccessEvent> events,
        List<AccessAnomaly> anomalies
    ) {}
}
