package com.ghatana.phr.kernel.interaction;

import com.ghatana.kernel.interaction.ProductInteractionHandler;
import com.ghatana.kernel.interaction.ProductInteractionOutcome;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import com.ghatana.phr.kernel.consent.ConsentService;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * PHR provider handler for consent revocation interactions.
 *
 * @doc.type class
 * @doc.purpose Product bridge handler for PHR consent revocation
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class ConsentRevokeInteractionHandler
        implements ProductInteractionHandler<
                ConsentRevokeInteractionHandler.ConsentRevokeRequest,
                ConsentRevokeInteractionHandler.ConsentRevokeResponse> {

    public static final String CONTRACT_ID = "kernel://interactions/phr.consent-revoke.v1";
    private static final List<String> REVOKE_EVIDENCE_REFS = List.of(
            "products/phr/lifecycle/gate-packs/consent.yaml",
            "products/phr/lifecycle/gate-packs/audit-evidence.yaml");
    private static final List<String> DENY_EVIDENCE_REFS = List.of(
            "products/phr/lifecycle/gate-packs/consent.yaml");

    private final ConsentService consentService;

    /**
     * Constructs a handler with a real consent service.
     *
     * @param consentService the consent service for revocation
     */
    public ConsentRevokeInteractionHandler(ConsentService consentService) {
        this.consentService = Objects.requireNonNull(consentService, "consentService must not be null");
    }

    @Override
    public String contractId() {
        return CONTRACT_ID;
    }

    @Override
    public Class<ConsentRevokeRequest> requestType() {
        return ConsentRevokeRequest.class;
    }

    @Override
    public Class<ConsentRevokeResponse> responseType() {
        return ConsentRevokeResponse.class;
    }

    @Override
    public Promise<ProductInteractionOutcome<ConsentRevokeResponse>> handle(
            ProductInteractionRequest<ConsentRevokeRequest> request) {
        if (request.tenantId() == null || request.tenantId().isBlank()) {
            return Promise.of(ProductInteractionOutcome.failed(
                    request.interactionId(),
                    ProductInteractionStatus.BLOCKED,
                    "product_interaction.policy_denied",
                    DENY_EVIDENCE_REFS));
        }
        return consentService.revokeConsent(toRevokeRequest(request))
                .map(result -> toInteractionOutcome(request, result));
    }

    private ConsentService.ConsentRevokeRequest toRevokeRequest(
            ProductInteractionRequest<ConsentRevokeRequest> request) {
        ConsentRevokeRequest payload = request.payload();
        return new ConsentService.ConsentRevokeRequest(
                request.interactionId(),
                request.tenantId(),
                new ConsentService.ActorContext(
                        request.consumerProductId(),
                        ConsentService.ActorType.PATIENT,
                        payload.subjectId(),
                        null,
                        request.consumerProductId(),
                        Set.of("CONSENT_REVOKE")),
                new ConsentService.TargetResource(
                        payload.subjectId(),
                        "Consent",
                        payload.consentId(),
                        com.ghatana.phr.kernel.policy.PhrDataClassification.C3),
                payload.reasonCode(),
                payload.effectiveDate());
    }

    private ProductInteractionOutcome<ConsentRevokeResponse> toInteractionOutcome(
            ProductInteractionRequest<ConsentRevokeRequest> request,
            ConsentService.ConsentRevokeResult result) {
        ConsentRevokeResponse response = new ConsentRevokeResponse(
                request.payload().subjectId(),
                request.payload().consentId(),
                result.revoked() ? "revoked" : "failed",
                result.revoked() ? null : "product_interaction.revoke_failed");
        if (result.revoked()) {
            return ProductInteractionOutcome.succeeded(request.interactionId(), REVOKE_EVIDENCE_REFS, response);
        }
        return ProductInteractionOutcome.failed(
                request.interactionId(),
                ProductInteractionStatus.DENIED,
                "product_interaction.revoke_failed",
                DENY_EVIDENCE_REFS);
    }

    public record ConsentRevokeRequest(
            String subjectId,
            String consentId,
            String reasonCode,
            String effectiveDate
    ) {}

    public record ConsentRevokeResponse(
            String subjectId,
            String consentId,
            String status,
            String reasonCode
    ) {}
}
