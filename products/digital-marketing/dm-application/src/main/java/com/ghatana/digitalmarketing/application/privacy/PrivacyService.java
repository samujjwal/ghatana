package com.ghatana.digitalmarketing.application.privacy;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

/**
 * P1-013: Privacy service for PII handling, consent, and DSAR.
 *
 * <p>Enforces privacy requirements:</p>
 * <ul>
 *   <li>PII hashing (HMAC-SHA256) for identifiers</li>
 *   <li>PII encryption (AES-GCM) for contact data</li>
 *   <li>Consent capture and enforcement</li>
 *   <li>Suppression list enforcement before export</li>
 *   <li>DSAR support: export, delete, anonymize</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Privacy service for PII handling, consent, and DSAR (P1-013)
 * @doc.layer product
 * @doc.pattern ApplicationService, Privacy
 */
public interface PrivacyService {

    /**
     * Hashes a PII identifier using HMAC-SHA256.
     *
     * @param identifier the identifier to hash (email, phone, etc.)
     * @return the hashed identifier
     */
    String hashIdentifier(String identifier);

    /**
     * Encrypts PII contact data using AES-GCM.
     *
     * @param plaintext the plaintext data
     * @return the encrypted data (base64-encoded)
     */
    String encryptPii(String plaintext);

    /**
     * Decrypts PII contact data.
     *
     * @param ciphertext the encrypted data (base64-encoded)
     * @return the plaintext data
     */
    String decryptPii(String ciphertext);

    /**
     * Checks if an identifier is on the suppression list.
     *
     * @param ctx the operation context
     * @param identifier the identifier to check (email, phone, etc.)
     * @return true if suppressed
     */
    Promise<Boolean> isSuppressed(DmOperationContext ctx, String identifier);

    /**
     * Records consent for a contact.
     *
     * @param ctx the operation context
     * @param contactId the contact identifier
     * @param consentType the type of consent (marketing, analytics, etc.)
     * @param granted whether consent was granted
     * @param consentSource the source of consent (form, API, etc.)
     * @return promise resolving when consent is recorded
     */
    Promise<Void> recordConsent(DmOperationContext ctx, String contactId, String consentType, boolean granted, String consentSource);

    /**
     * Checks if consent has been granted for a contact.
     *
     * @param ctx the operation context
     * @param contactId the contact identifier
     * @param consentType the type of consent to check
     * @return true if consent is granted
     */
    Promise<Boolean> hasConsent(DmOperationContext ctx, String contactId, String consentType);

    /**
     * Revokes consent for a contact.
     *
     * @param ctx the operation context
     * @param contactId the contact identifier
     * @param consentType the type of consent to revoke
     * @return promise resolving when consent is revoked
     */
    Promise<Void> revokeConsent(DmOperationContext ctx, String contactId, String consentType);

    /**
     * DSAR: Exports all data for a contact.
     *
     * @param ctx the operation context
     * @param contactId the contact identifier
     * @return promise resolving to the export data
     */
    Promise<ConsentDsarExport> exportContactData(DmOperationContext ctx, String contactId);

    /**
     * DSAR: Deletes all data for a contact.
     *
     * @param ctx the operation context
     * @param contactId the contact identifier
     * @return promise resolving when data is deleted
     */
    Promise<Void> deleteContactData(DmOperationContext ctx, String contactId);

    /**
     * DSAR: Anonymizes a contact (preserves data for analytics but removes PII).
     *
     * @param ctx the operation context
     * @param contactId the contact identifier
     * @return promise resolving when data is anonymized
     */
    Promise<Void> anonymizeContactData(DmOperationContext ctx, String contactId);

    /**
     * DSAR export result.
     */
    record ConsentDsarExport(
        String contactId,
        String exportedAt,
        java.util.Map<String, Object> personalData,
        java.util.List<ConsentRecord> consents,
        java.util.List<String> suppressionStatus
    ) {}

    /**
     * Consent record.
     */
    record ConsentRecord(
        String consentType,
        boolean granted,
        String consentSource,
        java.time.Instant grantedAt,
        java.time.Instant revokedAt
    ) {}
}
