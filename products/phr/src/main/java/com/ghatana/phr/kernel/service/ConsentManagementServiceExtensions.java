package com.ghatana.phr.kernel.service;

import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;

/**
 * Extension methods for ConsentManagementService for dashboard and route family completion.
 *
 * <p>These methods provide convenience methods for the dashboard and other route families
 * that need aggregated consent data.</p>
 *
 * @doc.type class
 * @doc.purpose Extension methods for ConsentManagementService
 * @doc.layer product
 * @doc.pattern Service Extension
 */
public final class ConsentManagementServiceExtensions {

    private static final Duration EXPIRING_SOON_WINDOW = Duration.ofDays(30);

    private final ConsentManagementService consentService;

    public ConsentManagementServiceExtensions(ConsentManagementService consentService) {
        this.consentService = consentService;
    }

    /**
     * Gets the count of expiring consents for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing the count of expiring consents
     */
    public Promise<Integer> getExpiringConsents(String patientId) {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(EXPIRING_SOON_WINDOW);
        return consentService.getPatientGrants(patientId)
            .map(grants -> Math.toIntExact(grants.stream()
                .filter(grant -> "ACTIVE".equals(grant.getStatus()))
                .filter(grant -> grant.getExpiresAt() != null)
                .filter(grant -> !grant.getExpiresAt().isBefore(now))
                .filter(grant -> !grant.getExpiresAt().isAfter(windowEnd))
                .count()));
    }
}
