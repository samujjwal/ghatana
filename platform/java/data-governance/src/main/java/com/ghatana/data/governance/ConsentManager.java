/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.data.governance;

import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Tracks consent status for data subjects within a tenant.
 *
 * <p>All writes are async. Implementations may persist state in a database or
 * an event log; the default is in-memory for testing.
 *
 * @doc.type interface
 * @doc.purpose Record and query per-subject consent status for data processing
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface ConsentManager {

    /**
     * Record that a data subject has given consent for the specified purpose.
     *
     * @param tenantId  the tenant scope
     * @param subjectId the data subject (e.g. user ID)
     * @param purpose   the processing purpose (e.g. "marketing", "analytics")
     */
    Promise<Void> recordConsent(String tenantId, String subjectId, String purpose);

    /**
     * Record that the data subject has withdrawn consent for a purpose.
     *
     * @param tenantId  tenant scope
     * @param subjectId the data subject
     * @param purpose   the purpose being withdrawn
     */
    Promise<Void> withdrawConsent(String tenantId, String subjectId, String purpose);

    /**
     * Check whether the data subject has given consent for a purpose.
     *
     * @param tenantId  tenant scope
     * @param subjectId the data subject
     * @param purpose   the purpose to check
     * @return {@code true} if consent is currently active
     */
    Promise<Boolean> hasConsent(String tenantId, String subjectId, String purpose);

    /**
     * Enforce consent — throws {@link ConsentRequiredException} if consent is absent.
     * Callers should invoke this before processing data for the given purpose.
     *
     * @param tenantId  tenant scope
     * @param subjectId the data subject
     * @param purpose   the processing purpose
     */
    Promise<Void> enforceConsent(String tenantId, String subjectId, String purpose);
}
