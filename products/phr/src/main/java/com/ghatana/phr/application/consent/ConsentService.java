package com.ghatana.phr.application.consent;

import com.ghatana.phr.application.patient.PatientOperationContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for consent management and sharing authorization.
 *
 * @doc.type class
 * @doc.purpose Defines operations for managing patient consent (PHR-F1-004)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ConsentService {

    /**
     * Request consent.
     *
     * @param ctx     operation context
     * @param request consent request
     * @return the consent request
     */
    Promise<ConsentRequest> requestConsent(PatientOperationContext ctx, RequestConsentRequest request);

    /**
     * Fetch a consent by ID.
     *
     * @param ctx       operation context
     * @param consentId consent ID
     * @return optional consent
     */
    Promise<Optional<Consent>> getConsent(PatientOperationContext ctx, String consentId);

    /**
     * Update consent.
     *
     * @param ctx       operation context
     * @param consentId consent ID
     * @param request   update request
     * @return updated consent
     */
    Promise<Consent> updateConsent(PatientOperationContext ctx, String consentId, UpdateConsentRequest request);

    /**
     * Revoke consent.
     *
     * @param ctx       operation context
     * @param consentId consent ID
     * @return updated consent
     */
    Promise<Consent> revokeConsent(PatientOperationContext ctx, String consentId);

    /**
     * List consents for a patient.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return list of consents
     */
    Promise<List<Consent>> listConsents(PatientOperationContext ctx, String patientId);

    /**
     * Get consent audit trail.
     *
     * @param ctx       operation context
     * @param patientId patient ID
     * @return consent audit trail
     */
    Promise<ConsentAuditTrail> getConsentAuditTrail(PatientOperationContext ctx, String patientId);

    // ── Request/Response types ───────────────────────────────────────────

    record RequestConsentRequest(
        String patientId,
        String consentType,
        String scope,
        String purpose,
        Instant expiresAt
    ) {}

    record UpdateConsentRequest(
        String scope,
        Instant expiresAt
    ) {}

    record ConsentRequest(
        String requestId,
        String patientId,
        String consentType,
        String scope,
        String purpose,
        String status,
        String requestedAt
    ) {}

    record Consent(
        String consentId,
        String patientId,
        String consentType,
        String scope,
        String purpose,
        ConsentStatus status,
        String grantedAt,
        Instant expiresAt,
        String revokedAt
    ) {}

    enum ConsentStatus {
        PENDING,
        GRANTED,
        REVOKED,
        EXPIRED
    }

    record ConsentAuditTrail(
        String patientId,
        List<ConsentAuditEntry> entries
    ) {}

    record ConsentAuditEntry(
        String entryId,
        String timestamp,
        String action,
        String consentType,
        String performedBy,
        String details
    ) {}
}
