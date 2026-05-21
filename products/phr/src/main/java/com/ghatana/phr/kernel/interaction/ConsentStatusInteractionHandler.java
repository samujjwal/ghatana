package com.ghatana.phr.kernel.interaction;

import com.ghatana.kernel.interaction.ProductInteractionHandler;
import com.ghatana.kernel.interaction.ProductInteractionOutcome;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import io.activej.promise.Promise;

import java.util.List;

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
                    List.of("products/phr/lifecycle/gate-packs/consent.yaml")));
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
                        List.of(
                                "products/phr/lifecycle/gate-packs/consent.yaml",
                                "products/phr/lifecycle/gate-packs/audit-evidence.yaml"),
                        response)
                : ProductInteractionOutcome.failed(
                        request.interactionId(),
                        ProductInteractionStatus.DENIED,
                        "product_interaction.consent_missing",
                        List.of("products/phr/lifecycle/gate-packs/consent.yaml"));
        return Promise.of(outcome);
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
