/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.incident;

import io.activej.promise.Promise;

/**
 * Manages per-tenant graceful-degradation levels during security incidents.
 *
 * <p>Rather than triggering a hard kill switch immediately, the incident-response
 * system can progressively reduce agent capabilities via this manager while
 * investigations are underway.
 *
 * @doc.type interface
 * @doc.purpose Manage progressive capability reduction for incident containment
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface GracefulDegradationManager {

    /**
     * Set the degradation mode for a specific tenant.
     *
     * @param tenantId the tenant to configure
     * @param mode     the degradation level to apply
     * @return completed promise on success
     */
    Promise<Void> setMode(String tenantId, DegradationMode mode);

    /**
     * Get the current degradation mode for a tenant.
     *
     * @param tenantId the tenant to query
     * @return promise resolving to the current mode (defaults to {@link DegradationMode#FULL})
     */
    Promise<DegradationMode> getMode(String tenantId);

    /**
     * Returns {@code true} if the specified action type is permitted under
     * the tenant's current degradation mode.
     *
     * @param tenantId   the tenant to check
     * @param actionType the tool action type (e.g. "WRITE", "NOTIFY", "EXTERNAL_CALL")
     * @return promise resolving to {@code true} if the action is currently allowed
     */
    Promise<Boolean> isActionAllowed(String tenantId, String actionType);
}
