package com.ghatana.phr.kernel.service;

import java.time.Instant;

/**
 * @doc.type record
 * @doc.purpose Represents the compliance review case opened for a break-glass emergency access event
 * @doc.layer product
 * @doc.pattern Domain Record
 */
public record EmergencyAccessReviewCase(
        String caseId,
        String eventId,
        String patientId,
        String accessorId,
        Instant initiatedAt,
        Instant accessExpiresAt,
        Instant reviewDueAt,
        Instant complianceDeadline,
        ReviewCaseStatus status
) {

    public EmergencyAccessReviewCase withStatus(ReviewCaseStatus newStatus) {
        return new EmergencyAccessReviewCase(
            caseId,
            eventId,
            patientId,
            accessorId,
            initiatedAt,
            accessExpiresAt,
            reviewDueAt,
            complianceDeadline,
            newStatus
        );
    }

    public enum ReviewCaseStatus {
        QUEUED,
        REVIEWED,
        ESCALATED
    }
}