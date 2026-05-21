package com.ghatana.phr.kernel.interaction;

import com.ghatana.kernel.interaction.ProductInteractionHandler;
import com.ghatana.kernel.interaction.ProductInteractionOutcome;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import com.ghatana.phr.kernel.consent.ConsentService;
import com.ghatana.phr.kernel.policy.PhrDataClassification;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * PHR provider handler for cross-product consent status checks.
 *
 * @doc.type class
 * @doc.purpose Product bridge handler for PHR consent status interactions
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class ConsentStatusInteractionHandler
        implements ProductInteractionHandler<
                ConsentStatusInteractionHandler.ConsentStatusRequest,
                ConsentStatusInteractionHandler.ConsentStatusResponse> {

    public static final String CONTRACT_ID = "kernel://interactions/phr.consent-status.v1";
    private static final List<String> CONSENT_EVIDENCE_REFS = List.of(
            "products/phr/lifecycle/gate-packs/consent.yaml",
            "products/phr/lifecycle/gate-packs/audit-evidence.yaml");
    private static final List<String> DENY_EVIDENCE_REFS = List.of(
            "products/phr/lifecycle/gate-packs/consent.yaml");

    private final ConsentService consentService;

    public ConsentStatusInteractionHandler() {
        this.consentService = null;
    }

    public ConsentStatusInteractionHandler(ConsentService consentService) {
        this.consentService = Objects.requireNonNull(consentService, "consentService must not be null");
    }

    @Override
    public String contractId() {
        return CONTRACT_ID;
    }

    @Override
    public Class<ConsentStatusRequest> requestType() {
        return ConsentStatusRequest.class;
    }

    @Override
    public Class<ConsentStatusResponse> responseType() {
        return ConsentStatusResponse.class;
    }

    @Override
    public Promise<ProductInteractionOutcome<ConsentStatusResponse>> handle(
            ProductInteractionRequest<ConsentStatusRequest> request) {
        if (request.tenantId() == null || request.tenantId().isBlank()) {
            return Promise.of(ProductInteractionOutcome.failed(
                    request.interactionId(),
                    ProductInteractionStatus.BLOCKED,
                    "product_interaction.policy_denied",
                    DENY_EVIDENCE_REFS));
        }
        if (consentService != null) {
            return consentService.checkAccess(toConsentCheckRequest(request))
                    .map(decision -> toInteractionOutcome(request, decision));
        }
        boolean allowed = "campaign-activation".equals(request.payload().purpose())
                || "consent-verification".equals(request.payload().purpose());
        ConsentStatusResponse response = new ConsentStatusResponse(
                request.payload().subjectId(),
                allowed ? "allowed" : "denied",
                allowed ? null : "product_interaction.consent_missing");
        ProductInteractionOutcome<ConsentStatusResponse> outcome = allowed
                ? ProductInteractionOutcome.succeeded(
                        request.interactionId(),
                        CONSENT_EVIDENCE_REFS,
                        response)
                : ProductInteractionOutcome.failed(
                        request.interactionId(),
                        ProductInteractionStatus.DENIED,
                        "product_interaction.consent_missing",
                        DENY_EVIDENCE_REFS);
        return Promise.of(outcome);
    }

    private ConsentService.ConsentCheckRequest toConsentCheckRequest(
            ProductInteractionRequest<ConsentStatusRequest> request) {
        ConsentStatusRequest payload = request.payload();
        return new ConsentService.ConsentCheckRequest(
                request.interactionId(),
                request.tenantId(),
                new ConsentService.ActorContext(
                        request.consumerProductId(),
                        ConsentService.ActorType.ADMIN,
                        null,
                        null,
                        request.consumerProductId(),
                        Set.of(payload.purpose())),
                new ConsentService.TargetResource(
                        payload.subjectId(),
                        "Consent",
                        payload.subjectId(),
                        PhrDataClassification.C3),
                ConsentService.ConsentAction.PATIENT_READ,
                ConsentService.PurposeOfUse.ELIGIBILITY_CHECK,
                null);
    }

    private ProductInteractionOutcome<ConsentStatusResponse> toInteractionOutcome(
            ProductInteractionRequest<ConsentStatusRequest> request,
            ConsentService.ConsentAccessDecision decision) {
        ConsentStatusResponse response = new ConsentStatusResponse(
                request.payload().subjectId(),
                decision.allowed() ? "allowed" : "denied",
                decision.allowed() ? null : "product_interaction.consent_missing");
        if (decision.allowed()) {
            return ProductInteractionOutcome.succeeded(request.interactionId(), CONSENT_EVIDENCE_REFS, response);
        }
        return ProductInteractionOutcome.failed(
                request.interactionId(),
                ProductInteractionStatus.DENIED,
                "product_interaction.consent_missing",
                DENY_EVIDENCE_REFS);
    }

    public record ConsentStatusRequest(
            String subjectId,
            String purpose
    ) {}

    public record ConsentStatusResponse(
            String subjectId,
            String status,
            String reasonCode
    ) {}
}
