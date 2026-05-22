package com.ghatana.digitalmarketing.application.preference;

import com.ghatana.digitalmarketing.bridge.NotificationPreferenceInteractionHandler;
import com.ghatana.digitalmarketing.domain.preference.CustomerPreference;
import com.ghatana.digitalmarketing.domain.preference.CustomerPreferenceRepository;
import com.ghatana.kernel.interaction.ProductInteractionRequest;
import io.activej.promise.Promise;

import java.util.Objects;

/**
 * Real domain service for customer notification preferences.
 *
 * <p>This service provides tenant-scoped notification preference lookups
 * for cross-product interactions with Digital Marketing. It respects
 * customer opt-in/opt-out decisions for SMS and email channels.</p>
 *
 * @doc.type class
 * @doc.purpose Real domain service for notification preference lookups
 * @doc.layer product
 * @doc.pattern Service
 */
public final class NotificationPreferenceService
        implements NotificationPreferenceInteractionHandler.NotificationPreferenceService {

    private final CustomerPreferenceRepository preferenceRepository;

    /**
     * Constructs a preference service with a real repository.
     *
     * @param preferenceRepository the repository for preference lookups
     */
    public NotificationPreferenceService(CustomerPreferenceRepository preferenceRepository) {
        this.preferenceRepository = Objects.requireNonNull(preferenceRepository, "preferenceRepository must not be null");
    }

    @Override
    public Promise<NotificationPreferenceInteractionHandler.NotificationPreferenceResponse> lookup(
            ProductInteractionRequest<NotificationPreferenceInteractionHandler.NotificationPreferenceRequest> request) {
        String subjectId = request.payload().subjectId();
        String tenantId = request.tenantId();

        return preferenceRepository.findBySubjectIdAndTenantId(subjectId, tenantId)
                .map(preference -> {
                    if (preference.isEmpty()) {
                        // Default to enabled if no preference exists
                        return new NotificationPreferenceInteractionHandler.NotificationPreferenceResponse(
                                subjectId,
                                true,
                                true,
                                "default"
                        );
                    }
                    CustomerPreference pref = preference.get();
                    return new NotificationPreferenceInteractionHandler.NotificationPreferenceResponse(
                            subjectId,
                            pref.smsEnabled(),
                            pref.emailEnabled(),
                            "customer-preference"
                    );
                });
    }
}
