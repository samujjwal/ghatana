/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.incident;

import java.time.Instant;

/**
 * Immutable record representing a platform security incident.
 *
 * @param incidentId  globally unique incident identifier
 * @param tenantId    affected tenant
 * @param agentId     agent involved in the incident (may be null for infrastructure incidents)
 * @param type        incident type classification from {@link IncidentType}
 * @param severity    severity level (CRITICAL, HIGH, MEDIUM, LOW)
 * @param description human-readable incident summary
 * @param detectedAt  timestamp when the incident was first detected
 * @param resolvedAt  timestamp when the incident was resolved (null if still open)
 *
 * @doc.type record
 * @doc.purpose Immutable incident record for the platform security incident log
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record Incident(
    String incidentId,
    String tenantId,
    String agentId,
    IncidentType type,
    Severity severity,
    String description,
    Instant detectedAt,
    Instant resolvedAt
) {
    /**
     * Severity levels — align with PagerDuty / OpsGenie severity tiers.
     *
     * @doc.type enum
     * @doc.purpose Four-level incident severity taxonomy
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW }

    /** Returns {@code true} if this incident has been resolved. */
    public boolean isResolved() { return resolvedAt != null; }
}
