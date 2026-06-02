package com.ghatana.platform.security.service;

import io.activej.promise.Promise;

/**
 * Kernel emergency break-glass primitive for PHI access override.
 *
 * <p>This service provides a standardized mechanism for emergency PHI access
 * across all products in the Ghatana platform. Emergency break-glass access
 * is a security-critical operation that requires:</p>
 *
 * <ul>
 *   <li>Explicit justification from an authorized role (clinician or admin)</li>
 *   <li>Strict audit trail logging</li>
 *   <li>Post-hoc review requirements</li>
 *   <li>Patient notification when applicable</li>
 * </ul>
 *
 * <p>The break-glass primitive ensures that emergency access is:</p>
 *
 * <ul>
 *   <li>Only available to authorized roles</li>
 *   <li>Always audited with immutable records</li>
 *   <li>Requires explicit justification</li>
 *   <li>Triggers post-access review workflows</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Provides Kernel-level emergency break-glass primitive for PHI access override
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface EmergencyBreakGlassService {

    /**
     * Request emergency break-glass access to PHI.
     *
     * <p>This method evaluates whether the requester is eligible for emergency
     * access and, if approved, returns an audited decision that includes
     * post-hoc review requirements.</p>
     *
     * @param request the emergency access request
     * @return Promise containing the emergency access decision
     */
    Promise<EmergencyAccessDecision> requestEmergencyAccess(EmergencyAccessRequest request);

    /**
     * Verify that emergency access was properly authorized and audited.
     *
     * <p>This method is called by routes before returning PHI to ensure that
     * the emergency access decision is valid and has been properly audited.</p>
     *
     * @param decisionId the emergency access decision ID
     * @return Promise containing true if the decision is valid, false otherwise
     */
    Promise<Boolean> verifyEmergencyAccess(String decisionId);

    /**
     * Record post-hoc review of emergency access.
     *
     * <p>This method is called by reviewers to document their review of
     * emergency access events.</p>
     *
     * @param review the post-hoc review record
     * @return Promise containing true if the review was recorded successfully
     */
    Promise<Boolean> recordPostHocReview(EmergencyAccessReview review);

    /**
     * Emergency access request.
     */
    record EmergencyAccessRequest(
        String tenantId,
        String principalId,
        String role,
        String patientId,
        String resourceType,
        String action,
        String justification,
        String correlationId
    ) {
        public EmergencyAccessRequest {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId is required");
            }
            if (principalId == null || principalId.isBlank()) {
                throw new IllegalArgumentException("principalId is required");
            }
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("role is required");
            }
            if (patientId == null || patientId.isBlank()) {
                throw new IllegalArgumentException("patientId is required");
            }
            if (justification == null || justification.isBlank()) {
                throw new IllegalArgumentException("justification is required");
            }
        }
    }

    /**
     * Emergency access decision.
     */
    record EmergencyAccessDecision(
        String decisionId,
        boolean allowed,
        String reasonCode,
        String reasonMessage,
        boolean requiresPostHocReview,
        boolean requiresPatientNotification,
        long expiresAt
    ) {
        public EmergencyAccessDecision {
            if (decisionId == null || decisionId.isBlank()) {
                throw new IllegalArgumentException("decisionId is required");
            }
            if (reasonCode == null || reasonCode.isBlank()) {
                throw new IllegalArgumentException("reasonCode is required");
            }
        }
    }

    /**
     * Post-hoc review of emergency access.
     */
    record EmergencyAccessReview(
        String decisionId,
        String reviewerId,
        String reviewerRole,
        ReviewOutcome outcome,
        String reviewComments,
        long reviewedAt
    ) {
        public EmergencyAccessReview {
            if (decisionId == null || decisionId.isBlank()) {
                throw new IllegalArgumentException("decisionId is required");
            }
            if (reviewerId == null || reviewerId.isBlank()) {
                throw new IllegalArgumentException("reviewerId is required");
            }
            if (outcome == null) {
                throw new IllegalArgumentException("outcome is required");
            }
        }
    }

    /**
     * Review outcome for post-hoc review.
     */
    enum ReviewOutcome {
        APPROVED,
        DENIED,
        NEEDS_INVESTIGATION
    }
}
