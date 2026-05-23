package com.ghatana.phr.application.emergency;

import com.ghatana.phr.application.patient.PatientOperationContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for emergency/break-glass workflow.
 *
 * @doc.type class
 * @doc.purpose Defines operations for emergency access to patient data when consent cannot be obtained (PHR-F1-008)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface EmergencyAccessService {

    /**
     * Request emergency access.
     *
     * @param ctx     operation context
     * @param request emergency access request
     * @return the emergency access
     */
    Promise<EmergencyAccess> requestEmergencyAccess(PatientOperationContext ctx, EmergencyAccessRequest request);

    /**
     * Fetch emergency access by ID.
     *
     * @param ctx            operation context
     * @param emergencyAccessId emergency access ID
     * @return optional emergency access
     */
    Promise<Optional<EmergencyAccess>> getEmergencyAccess(PatientOperationContext ctx, String emergencyAccessId);

    /**
     * Extend emergency access.
     *
     * @param ctx            operation context
     * @param emergencyAccessId emergency access ID
     * @param extensionMinutes minutes to extend
     * @return updated emergency access
     */
    Promise<EmergencyAccess> extendEmergencyAccess(PatientOperationContext ctx, String emergencyAccessId, int extensionMinutes);

    /**
     * List emergency access for a patient.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return list of emergency access
     */
    Promise<List<EmergencyAccess>> listEmergencyAccess(PatientOperationContext ctx, String patientId);

    /**
     * Complete review for emergency access.
     *
     * @param ctx            operation context
     * @param emergencyAccessId emergency access ID
     * @param reviewResult  review result
     * @return updated emergency access
     */
    Promise<EmergencyAccess> completeReview(PatientOperationContext ctx, String emergencyAccessId, ReviewResult reviewResult);

    // ── Request/Response types ───────────────────────────────────────────

    record EmergencyAccessRequest(
        String patientId,
        String accessorId,
        String justification,
        String reason
    ) {}

    record EmergencyAccess(
        String emergencyAccessId,
        String patientId,
        String accessorId,
        String justification,
        String reason,
        Instant accessedAt,
        Instant accessExpiresAt,
        Instant reviewDueAt,
        EmergencyAccessStatus status,
        String reviewCaseId
    ) {}

    enum EmergencyAccessStatus {
        ACTIVE,
        EXPIRED,
        REVIEWED,
        ESCALATED
    }

    record ReviewResult(
        String reviewStatus,
        String reviewedBy,
        String notes
    ) {}
}
