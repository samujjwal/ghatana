package com.ghatana.phr.domain.consent;

import com.ghatana.kernel.interaction.ConsentStatusInteractionHandler.ConsentService;
import com.ghatana.kernel.interaction.ConsentStatusInteractionHandler.ConsentStatus;
import java.time.Instant;
import java.util.Set;

/**
 * Real consent domain service implementation for PHR.
 *
 * <p>This service provides actual consent status lookups from the PHR consent domain,
 * integrating with the ConsentStatusInteractionHandler for governed interactions.</p>
 *
 * <p>Consent model includes:</p>
 * <ul>
 *   <li>Grant ID for tracking individual consent grants</li>
 *   <li>Subject, grantee, and purpose for consent scope</li>
 *   <li>Expiry time for time-boxed consent</li>
 *   <li>Revocation with reason and timestamp</li>
 *   <li>Audit trail for all consent state changes</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Real consent domain service implementation for PHR
 * @doc.layer phr
 * @doc.pattern Service
 */
public final class PhrConsentDomainService implements ConsentService {

    private final ConsentRepository consentRepository;
    private final ConsentAuditLogger auditLogger;

    public PhrConsentDomainService(ConsentRepository consentRepository, ConsentAuditLogger auditLogger) {
        this.consentRepository = consentRepository;
        this.auditLogger = auditLogger;
    }

    @Override
    public ConsentStatus getConsentStatus(String subjectId, String consentType) {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("Subject ID is required");
        }

        ConsentRecord record = consentRepository.findBySubjectIdAndType(subjectId, consentType);
        
        if (record == null) {
            // Default to denied if no consent record exists
            return new ConsentStatus(
                subjectId,
                consentType,
                false,
                null,
                null
            );
        }

        // Check if consent is expired
        if (record.expiresAt() != null && Instant.parse(record.expiresAt()).isBefore(Instant.now())) {
            return new ConsentStatus(
                subjectId,
                consentType,
                false,
                record.grantedAt(),
                record.expiresAt()
            );
        }

        // Check if consent is revoked
        if (record.revokedAt() != null) {
            return new ConsentStatus(
                subjectId,
                consentType,
                false,
                record.grantedAt(),
                record.expiresAt()
            );
        }

        return new ConsentStatus(
            subjectId,
            consentType,
            record.granted(),
            record.grantedAt(),
            record.expiresAt()
        );
    }

    /**
     * Grants consent for a subject to a grantee for a specific purpose.
     *
     * @param subjectId the patient/subject ID
     * @param granteeId the provider/caregiver ID receiving consent
     * @param purpose the purpose of the consent (e.g., CARE_DELIVERY, RESEARCH)
     * @param consentType the type of consent (e.g., PATIENT_READ, DATA_SHARING)
     * @param expiresAt when the consent expires (null for indefinite)
     * @return the granted consent record
     */
    public ConsentRecord grantConsent(
            String subjectId,
            String granteeId,
            String purpose,
            String consentType,
            Instant expiresAt) {
        ConsentRecord record = new ConsentRecord(
            java.util.UUID.randomUUID().toString(),
            subjectId,
            granteeId,
            purpose,
            consentType,
            true,
            Instant.now().toString(),
            expiresAt != null ? expiresAt.toString() : null,
            null,
            null
        );
        
        consentRepository.save(record);
        auditLogger.logConsentGranted(record);
        
        return record;
    }

    /**
     * Revokes an existing consent grant.
     *
     * @param grantId the ID of the consent grant to revoke
     * @param reason the reason for revocation
     * @return the revoked consent record
     */
    public ConsentRecord revokeConsent(String grantId, String reason) {
        ConsentRecord record = consentRepository.findById(grantId);
        if (record == null) {
            throw new IllegalArgumentException("Consent grant not found: " + grantId);
        }
        
        ConsentRecord revoked = new ConsentRecord(
            record.grantId(),
            record.subjectId(),
            record.granteeId(),
            record.purpose(),
            record.consentType(),
            false,
            record.grantedAt(),
            record.expiresAt(),
            Instant.now().toString(),
            reason
        );
        
        consentRepository.save(revoked);
        auditLogger.logConsentRevoked(revoked, reason);
        
        return revoked;
    }

    /**
     * Repository interface for consent records.
     */
    public interface ConsentRepository {
        ConsentRecord findBySubjectIdAndType(String subjectId, String consentType);
        ConsentRecord findById(String grantId);
        void save(ConsentRecord record);
        Set<ConsentRecord> findAllForSubject(String subjectId);
    }

    /**
     * Audit logger for consent state changes.
     */
    public interface ConsentAuditLogger {
        void logConsentGranted(ConsentRecord record);
        void logConsentRevoked(ConsentRecord record, String reason);
    }

    /**
     * Consent record with full lifecycle tracking.
     */
    public record ConsentRecord(
        String grantId,
        String subjectId,
        String granteeId,
        String purpose,
        String consentType,
        boolean granted,
        String grantedAt,
        String expiresAt,
        String revokedAt,
        String revocationReason
    ) {}
}
