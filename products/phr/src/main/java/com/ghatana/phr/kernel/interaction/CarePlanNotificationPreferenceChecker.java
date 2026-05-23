package com.ghatana.phr.kernel.interaction;

import com.ghatana.kernel.interaction.ProductInteractionBroker;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import com.ghatana.kernel.interaction.ProductInteractionStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Service for checking DMOS notification preferences before sending PHR care-plan notifications.
 *
 * <p>This implements the P1-05 requirement: "PHR care-plan notification flow must call
 * DMOS notification preference through the broker."</p>
 *
 * <p>Before sending a care-plan notification to a patient, this service checks the patient's
 * notification preferences in DMOS to ensure the notification channel and timing are appropriate.</p>
 *
 * @doc.type class
 * @doc.purpose Service for checking DMOS notification preferences before PHR care-plan notifications
 * @doc.layer phr
 * @doc.pattern Service
 */
public final class CarePlanNotificationPreferenceChecker {

    private final ProductInteractionBroker interactionBroker;

    /**
     * Constructs a notification preference checker.
     *
     * @param interactionBroker the product interaction broker
     */
    public CarePlanNotificationPreferenceChecker(ProductInteractionBroker interactionBroker) {
        this.interactionBroker = Objects.requireNonNull(interactionBroker, "interactionBroker must not be null");
    }

    /**
     * Checks notification preferences for a patient before sending a care-plan notification.
     *
     * @param patientId the patient identifier
     * @param notificationType the type of notification (e.g., "medication-reminder", "appointment-reminder")
     * @param channel the proposed notification channel (e.g., "sms", "email", "push")
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @return the preference check result
     */
    public Promise<CarePlanNotificationPreferenceResult> check(
            String patientId,
            String notificationType,
            String channel,
            String tenantId,
            String workspaceId
    ) {
        if (patientId == null || patientId.isBlank()) {
            return Promise.of(CarePlanNotificationPreferenceResult.denied(
                    patientId,
                    "patient_id_required",
                    "Patient ID is required for preference check"
            ));
        }

        // Build interaction request for notification preference check
        NotificationPreferenceRequest payload = new NotificationPreferenceRequest(
                patientId,
                notificationType,
                channel);

        ProductInteractionRequest<NotificationPreferenceRequest> request =
                new ProductInteractionRequest<>(
                        "1.0.0",
                        UUID.randomUUID().toString(),
                        "kernel://interactions/digital-marketing.notification-preference.v1",
                        "1.0.0",
                        "digital-marketing",
                        "phr",
                        "phr",
                        tenantId,
                        workspaceId,
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        buildPolicyContext(patientId, notificationType, channel, tenantId, workspaceId),
                        payload
                );

        return interactionBroker.execute(request)
                .map(outcome -> {
                    if (outcome.status() == ProductInteractionStatus.SUCCEEDED) {
                        return CarePlanNotificationPreferenceResult.allowed(
                                patientId,
                                notificationType,
                                channel,
                                "Notification preference check passed"
                        );
                    } else {
                        return CarePlanNotificationPreferenceResult.denied(
                                patientId,
                                outcome.reasonCode(),
                                "Notification preference check denied: " + outcome.reasonCode()
                        );
                    }
                });
    }

    private Map<String, String> buildPolicyContext(
            String patientId,
            String notificationType,
            String channel,
            String tenantId,
            String workspaceId
    ) {
        Map<String, String> context = new HashMap<>();
        context.put("purpose", "care-plan-notification");
        context.put("actor", "phr-system");
        context.put("tenantId", tenantId);
        context.put("workspaceId", workspaceId);
        context.put("subjectId", patientId);
        context.put("notificationType", notificationType);
        context.put("channel", channel);
        return context;
    }

    /**
     * Request payload for notification preference check.
     */
    public record NotificationPreferenceRequest(
            String patientId,
            String notificationType,
            String channel
    ) {}

    /**
     * Preference check result.
     */
    public record CarePlanNotificationPreferenceResult(
            String patientId,
            boolean allowed,
            String reasonCode,
            String message,
            String notificationType,
            String channel
    ) {
        public static CarePlanNotificationPreferenceResult allowed(
                String patientId,
                String notificationType,
                String channel,
                String message
        ) {
            return new CarePlanNotificationPreferenceResult(patientId, true, null, message, notificationType, channel);
        }

        public static CarePlanNotificationPreferenceResult denied(
                String patientId,
                String reasonCode,
                String message
        ) {
            return new CarePlanNotificationPreferenceResult(patientId, false, reasonCode, message, null, null);
        }
    }
}
