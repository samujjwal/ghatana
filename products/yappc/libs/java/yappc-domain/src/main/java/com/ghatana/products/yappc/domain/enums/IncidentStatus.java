package com.ghatana.products.yappc.domain.enums;

/**
 * Incident status enumeration for lifecycle tracking.
 *
 * <p><b>Purpose</b><br>
 * Defines the lifecycle states of security incidents from detection to closure.
 *
 * <p><b>Status Flow</b><br>
 * ACTIVE → INVESTIGATING → CONTAINED → RESOLVED → CLOSED
 *
 * <p><b>States</b><br>
 * - ACTIVE: Incident detected, immediate threat active
 * - INVESTIGATING: Team analyzing root cause and impact
 * - CONTAINED: Threat contained but not fully resolved
 * - RESOLVED: Issue resolved, monitoring for recurrence
 * - CLOSED: Incident closed, post-mortem complete
 *
 * @see com.ghatana.products.yappc.domain.model.Incident
 * @doc.type enum
 * @doc.purpose Incident lifecycle status
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum IncidentStatus {
    /**
     * Active - incident ongoing, immediate threat.
     * Requires active response and mitigation.
     */
    ACTIVE,

    /**
     * Investigating - analyzing root cause.
     * Team is working to understand scope and impact.
     */
    INVESTIGATING,

    /**
     * Contained - threat contained but not resolved.
     * Immediate risk mitigated, working toward full resolution.
     */
    CONTAINED,

    /**
     * Resolved - issue fixed, monitoring for recurrence.
     * Fix deployed, watching for any residual effects.
     */
    RESOLVED,

    /**
     * Closed - incident closed, post-mortem complete.
     * All work complete, lessons learned documented.
     */
    CLOSED
}
