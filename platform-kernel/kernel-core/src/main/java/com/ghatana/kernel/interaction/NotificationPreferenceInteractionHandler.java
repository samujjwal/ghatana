package com.ghatana.kernel.interaction;

import io.activej.promise.Promise;

import java.util.List;

/**
 * Service-backed handler for notification preference interactions.
 *
 * <p>This handler retrieves notification preferences from the real customer preference service,
 * enabling Digital Marketing to respect user communication preferences.</p>
 *
 * @doc.type class
 * @doc.purpose Handle notification preference interactions with real service backing
 * @doc.layer kernel
 * @doc.pattern Handler
 */
public final class NotificationPreferenceInteractionHandler implements ProductInteractionHandler<Object, NotificationPreferenceInteractionHandler.NotificationPreference> {

    private final PreferenceService preferenceService;

    public NotificationPreferenceInteractionHandler(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @Override
    public String contractId() {
        return "kernel.notification-preference.v1";
    }

    @Override
    public Class<Object> requestType() {
        return Object.class;
    }

    @Override
    public Class<NotificationPreference> responseType() {
        return NotificationPreference.class;
    }

    @Override
    public Promise<ProductInteractionOutcome<NotificationPreference>> handle(ProductInteractionRequest<Object> request) {
        String customerId = request.policyContext().get("customerId");
        String preferenceType = request.policyContext().get("preferenceType");

        if (customerId == null || customerId.isBlank()) {
            return Promise.of(ProductInteractionOutcome.failed(
                request.interactionId(),
                ProductInteractionStatus.BLOCKED,
                "preference.customer_id_required",
                List.of()
            ));
        }

        try {
            NotificationPreference preference = preferenceService.getNotificationPreference(
                customerId,
                preferenceType
            );

            if (preference.enabled()) {
                return Promise.of(ProductInteractionOutcome.succeeded(request.interactionId(), List.of(), preference));
            }

            return Promise.of(ProductInteractionOutcome.failed(
                request.interactionId(),
                ProductInteractionStatus.DENIED,
                "preference.disabled",
                List.of()
            ));
        } catch (RuntimeException error) {
            return Promise.of(ProductInteractionOutcome.failed(
                request.interactionId(),
                ProductInteractionStatus.FAILED,
                "preference.lookup_failed",
                List.of()
            ));
        }
    }

    /**
     * Service interface for customer preference operations.
     */
    public interface PreferenceService {
        NotificationPreference getNotificationPreference(String customerId, String preferenceType);
    }

    /**
     * Notification preference result.
     */
    public record NotificationPreference(
        String customerId,
        String preferenceType,
        boolean enabled,
        String channel,
        String updatedAt
    ) {}
}
