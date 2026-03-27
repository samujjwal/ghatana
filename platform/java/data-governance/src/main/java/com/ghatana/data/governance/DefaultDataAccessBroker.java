/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.data.governance;

import io.activej.promise.Promise;

/**
 * Default {@link DataAccessBroker} that composes {@link ConsentManager}
 * and {@link PurposeLimitationEnforcer}.
 *
 * <p>Consent is checked first; if the subject has not consented, a
 * {@link ConsentRequiredException} is propagated immediately without querying
 * purpose bindings. This avoids leaking information about which purposes are
 * configured for a given data asset to callers who have no consent.
 *
 * @doc.type class
 * @doc.purpose Compose ConsentManager + PurposeLimitationEnforcer into one access check
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class DefaultDataAccessBroker implements DataAccessBroker {

    private final ConsentManager consentManager;
    private final PurposeLimitationEnforcer purposeEnforcer;

    /**
     * Construct a broker backed by the given components.
     *
     * @param consentManager  consent verification
     * @param purposeEnforcer purpose-limitation verification
     */
    public DefaultDataAccessBroker(
            ConsentManager consentManager,
            PurposeLimitationEnforcer purposeEnforcer) {
        this.consentManager = consentManager;
        this.purposeEnforcer = purposeEnforcer;
    }

    @Override
    public Promise<Void> checkAccess(
            String tenantId, String subjectId, String dataId, String purpose) {
        return consentManager.enforceConsent(tenantId, subjectId, purpose)
            .then(() -> purposeEnforcer.enforceForPurpose(tenantId, dataId, purpose));
    }
}
