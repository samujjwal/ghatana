package com.ghatana.digitalmarketing.bridge;

import com.ghatana.kernel.interaction.ProductInteractionHandler;
import com.ghatana.kernel.interaction.ProductInteractionOutcome;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;

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
    private static final List<String> NOTIFICATION_PREFERENCE_EVIDENCE_REFS = List.of(
            "products/digital-marketing/lifecycle/evidence/notification-preference.yaml");

    private final NotificationPreferenceService preferenceService;

    /**
     * Constructs a handler with a real preference service.
     *
     * @param preferenceService the preference service for notification lookups
     */
    public NotificationPreferenceInteractionHandler(NotificationPreferenceService preferenceService) {
        this.preferenceService = Objects.requireNonNull(preferenceService, "preferenceService must not be null");
    }

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
                    NOTIFICATION_PREFERENCE_EVIDENCE_REFS));
        }
        return preferenceService.lookup(request)
                .map(response -> ProductInteractionOutcome.succeeded(
                        request.interactionId(),
                        NOTIFICATION_PREFERENCE_EVIDENCE_REFS,
                        response));
    }

    /**
     * Product service boundary used by the Kernel bridge handler.
     *
     * @doc.type interface
     * @doc.purpose Resolve tenant-scoped notification preferences for product interactions
     * @doc.layer product
     * @doc.pattern Port
     */
    @FunctionalInterface
    public interface NotificationPreferenceService {
        Promise<NotificationPreferenceResponse> lookup(
                ProductInteractionRequest<NotificationPreferenceRequest> request);
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
