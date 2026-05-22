package com.ghatana.phr.domain.consent;

import com.ghatana.kernel.interaction.ConsentStatusInteractionHandler.ConsentService;
import com.ghatana.kernel.interaction.ConsentStatusInteractionHandler.ConsentStatus;

/**
 * Real consent domain service implementation for PHR.
 *
 * <p>This service provides actual consent status lookups from the PHR consent domain,
 * integrating with the ConsentStatusInteractionHandler for governed interactions.</p>
 *
 * @doc.type class
 * @doc.purpose Real consent domain service implementation for PHR
 * @doc.layer phr
 * @doc.pattern Service
 */
public final class PhrConsentDomainService implements ConsentService {

    private final ConsentRepository consentRepository;

    public PhrConsentDomainService(ConsentRepository consentRepository) {
        this.consentRepository = consentRepository;
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

        return new ConsentStatus(
            subjectId,
            consentType,
            record.isGranted(),
            record.getGrantedAt(),
            record.getExpiresAt()
        );
    }

    /**
     * Repository interface for consent records.
     */
    public interface ConsentRepository {
        ConsentRecord findBySubjectIdAndType(String subjectId, String consentType);
    }

    /**
     * Consent record.
     */
    public record ConsentRecord(
        String subjectId,
        String consentType,
        boolean granted,
        String grantedAt,
        String expiresAt
    ) {}
}
