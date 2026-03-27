/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.incident;

import io.activej.promise.Promise;

/**
 * Emergency kill-switch service that can immediately halt all agent activity
 * within a tenant or across the entire platform.
 *
 * <p>A kill-switch is a last-resort control activated during active security incidents.
 * Once activated for a tenant, all subsequent agent-action requests should be rejected
 * until the switch is deactivated via {@link #deactivate}.
 *
 * @doc.type interface
 * @doc.purpose Emergency halt of agent activity for incident containment
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface KillSwitchService {

    /**
     * Activate the kill switch for the given tenant, halting all agent activity.
     *
     * @param tenantId   the tenant to halt
     * @param reason     human-readable reason for activation (for audit log)
     * @param incidentId the incident that triggered this activation
     * @return completed promise on success
     */
    Promise<Void> activate(String tenantId, String reason, String incidentId);

    /**
     * Deactivate the kill switch for a tenant, resuming normal agent activity.
     *
     * @param tenantId the tenant to resume
     * @param reason   human-readable reason for deactivation
     * @return completed promise on success
     */
    Promise<Void> deactivate(String tenantId, String reason);

    /**
     * Returns {@code true} if the kill switch is currently active for the tenant.
     *
     * @param tenantId the tenant to check
     * @return promise resolving to the active state
     */
    Promise<Boolean> isActive(String tenantId);

    /**
     * Activate a global platform-wide kill switch, halting ALL tenants.
     *
     * @param reason     human-readable justification
     * @param incidentId the incident that triggered global halt
     * @return completed promise on success
     */
    Promise<Void> activateGlobal(String reason, String incidentId);

    /**
     * Returns {@code true} if the global platform kill switch is active.
     *
     * @return promise resolving to the global active state
     */
    Promise<Boolean> isGlobalActive();
}
