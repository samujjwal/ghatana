package com.ghatana.digitalmarketing.domain.preference;

import io.activej.promise.Promise;

/**
 * Repository for customer notification preferences.
 *
 * <p>Provides tenant-scoped preference lookups for cross-product interactions.</p>
 *
 * @doc.type interface
 * @doc.purpose Repository for customer notification preferences
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface CustomerPreferenceRepository {

    /**
     * Finds a customer preference by subject ID and tenant ID.
     *
     * @param subjectId the customer subject ID
     * @param tenantId the tenant ID
     * @return a Promise resolving to an Optional containing the preference, or empty if not found
     */
    Promise<java.util.Optional<CustomerPreference>> findBySubjectIdAndTenantId(
            String subjectId,
            String tenantId
    );
}
