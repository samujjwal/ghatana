package com.ghatana.digitalmarketing.bridge;

import com.ghatana.kernel.interaction.ProductInteractionHandler;
import com.ghatana.kernel.interaction.ProductInteractionOutcome;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import io.activej.promise.Promise;

import java.util.List;

/**
 * DMOS provider handler for notification preference lookups.
 *
 * @doc.type class
 * @doc.purpose Product bridge handler for notification preference product interactions
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class NotificationPreferenceInteractionHandler
        implements ProductInteractionHandler<
                NotificationPreferenceInteractionHandler.NotificationPreferenceRequest,
                NotificationPreferenceInteractionHandler.NotificationPreferenceResponse> {

    public static final String CONTRACT_ID =
            "kernel://interactions/digital-marketing.notification-preference.v1";

    @Override
    public String contractId() {
        return CONTRACT_ID;
    }

    @Override
    public Class<NotificationPreferenceRequest> requestType() {
        return NotificationPreferenceRequest.class;
    }

    @Override
    public Class<NotificationPreferenceResponse> responseType() {
        return NotificationPreferenceResponse.class;
    }

    @Override
    public Promise<ProductInteractionOutcome<NotificationPreferenceResponse>> handle(
            ProductInteractionRequest<NotificationPreferenceRequest> request) {
        if (request.tenantId() == null || request.tenantId().isBlank()) {
            return Promise.of(ProductInteractionOutcome.failed(
                    request.interactionId(),
                    ProductInteractionStatus.BLOCKED,
                    "product_interaction.policy_denied",
                    List.of("products/digital-marketing/lifecycle/evidence/notification-preference.yaml")));
        }
        NotificationPreferenceResponse response = new NotificationPreferenceResponse(
                request.payload().subjectId(),
                true,
                false,
                "tenant-scoped-preference");
        return Promise.of(ProductInteractionOutcome.succeeded(
                request.interactionId(),
                List.of("products/digital-marketing/lifecycle/evidence/notification-preference.yaml"),
                response));
    }

    public record NotificationPreferenceRequest(
            String subjectId,
            String purpose
    ) {}

    public record NotificationPreferenceResponse(
            String subjectId,
            boolean smsEnabled,
            boolean emailEnabled,
            String source
    ) {}
}
