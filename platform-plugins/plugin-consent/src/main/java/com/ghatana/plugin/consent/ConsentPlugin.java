package com.ghatana.plugin.consent;

import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;

/**
 * Consent Plugin - Universal consent management framework.
 *
 * <p>Supports multiple consent types:</p>
 * <ul>
 *   <li>Healthcare data consent (PHR/HIPAA)</li>
 *   <li>Financial data sharing (Finance/GDPR)</li>
 *   <li>Marketing consent (general)</li>
 *   <li>Terms acceptance (general)</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Consent management plugin interface
 * @doc.layer platform
 * @doc.pattern Plugin
 * @since 1.0.0
 */
public interface ConsentPlugin extends Plugin {

    /**
     * Records a consent action for a subject.
     *
     * @param subjectId the subject identifier
     * @param purpose the consent purpose
     * @param action the consent action
     * @return Promise containing the consent record
     */
    Promise<ConsentRecord> recordConsent(String subjectId, String purpose, ConsentAction action);

    /**
     * Verifies if consent exists for a purpose.
     *
     * @param subjectId the subject identifier
     * @param purpose the consent purpose
     * @return Promise containing true if consent exists
     */
    Promise<Boolean> verifyConsent(String subjectId, String purpose);

    /**
     * Revokes a consent by ID.
     *
     * @param consentId the consent identifier
     * @return Promise completing when revoked
     */
    Promise<Void> revokeConsent(String consentId);

    /**
     * Gets consent history for a subject.
     *
     * @param subjectId the subject identifier
     * @return Promise containing consent records
     */
    Promise<List<ConsentRecord>> getConsentHistory(String subjectId);

    /**
     * Gets current consent status for a purpose.
     *
     * @param subjectId the subject identifier
     * @param purpose the consent purpose
     * @return Promise containing current consent status
     */
    Promise<ConsentStatus> getCurrentConsent(String subjectId, String purpose);

    /**
     * Hard-deletes all consent records for a subject (GDPR right-to-erasure).
     *
     * <p>Once this method completes, {@link #verifyConsent} must return {@code false}
     * and {@link #getConsentHistory} must return an empty list for the given subject.
     *
     * @param subjectId the subject whose data must be erased
     * @return Promise containing the number of records deleted
     */
    Promise<Integer> deleteAllForSubject(String subjectId);

    /**
     * Consent action.
     */
    enum ConsentAction {
        GRANT,
        DENY,
        WITHDRAW
    }

    /**
     * Consent status.
     */
    enum ConsentStatus {
        GRANTED,
        DENIED,
        EXPIRED,
        REVOKED,
        NOT_REQUESTED
    }

    /**
     * Consent record.
     */
    record ConsentRecord(
        String consentId,
        String subjectId,
        String purpose,
        ConsentStatus status,
        ConsentAction action,
        String legalBasis,
        Instant grantedAt,
        Instant expiresAt,
        Instant revokedAt,
        String metadata
    ) {}
}
