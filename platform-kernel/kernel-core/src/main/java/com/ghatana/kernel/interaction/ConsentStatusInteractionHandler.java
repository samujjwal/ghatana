package com.ghatana.kernel.interaction;

import io.activej.promise.Promise;

import java.util.List;

/**
 * Service-backed handler for consent status interactions.
 *
 * <p>This handler retrieves consent status from the real consent domain service,
 * providing accurate and up-to-date consent information for healthcare interactions.</p>
 *
 * @doc.type class
 * @doc.purpose Handle consent status interactions with real service backing
 * @doc.layer kernel
 * @doc.pattern Handler
 */
public final class ConsentStatusInteractionHandler implements ProductInteractionHandler<Object, ConsentStatusInteractionHandler.ConsentStatus> {

    private final ConsentService consentService;

    public ConsentStatusInteractionHandler(ConsentService consentService) {
        this.consentService = consentService;
    }

    @Override
    public String contractId() {
        return "kernel.consent-status.v1";
    }

    @Override
    public Class<Object> requestType() {
        return Object.class;
    }

    @Override
    public Class<ConsentStatus> responseType() {
        return ConsentStatus.class;
    }

    @Override
    public Promise<ProductInteractionOutcome<ConsentStatus>> handle(ProductInteractionRequest<Object> request) {
        String subjectId = request.policyContext().get("subjectId");
        String consentType = request.policyContext().get("consentType");

        if (subjectId == null || subjectId.isBlank()) {
            return Promise.of(ProductInteractionOutcome.failed(
                request.interactionId(),
                ProductInteractionStatus.BLOCKED,
                "consent.subject_id_required",
                List.of()
            ));
        }

        try {
            ConsentStatus status = consentService.getConsentStatus(subjectId, consentType);

            if (status.granted()) {
                return Promise.of(ProductInteractionOutcome.succeeded(request.interactionId(), List.of(), status));
            }

            return Promise.of(ProductInteractionOutcome.failed(
                request.interactionId(),
                ProductInteractionStatus.DENIED,
                "consent.denied",
                List.of()
            ));
        } catch (RuntimeException error) {
            return Promise.of(ProductInteractionOutcome.failed(
                request.interactionId(),
                ProductInteractionStatus.FAILED,
                "consent.lookup_failed",
                List.of()
            ));
        }
    }

    /**
     * Service interface for consent domain operations.
     */
    public interface ConsentService {
        ConsentStatus getConsentStatus(String subjectId, String consentType);
    }

    /**
     * Consent status result.
     */
    public record ConsentStatus(
        String subjectId,
        String consentType,
        boolean granted,
        String grantedAt,
        String expiresAt
    ) {}
}
