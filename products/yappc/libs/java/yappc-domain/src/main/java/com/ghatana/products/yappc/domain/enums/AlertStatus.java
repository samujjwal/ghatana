package com.ghatana.products.yappc.domain.enums;

/**
 * Alert status enumeration for alert lifecycle tracking.
 *
 * <p><b>Purpose</b><br>
 * Defines lifecycle states for security alerts from detection to resolution.
 *
 * <p><b>Status Flow</b><br>
 * OPEN → TRIAGING → ACKNOWLEDGED → INVESTIGATING → RESOLVED → FALSE_POSITIVE
 *
 * @see com.ghatana.products.yappc.domain.model.SecurityAlert
 * @doc.type enum
 * @doc.purpose Alert lifecycle status
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum AlertStatus {
    /**
     * Open - alert triggered, awaiting triage.
     */
    OPEN,

    /**
     * Triaging - analyst reviewing alert context.
     */
    TRIAGING,

    /**
     * Acknowledged - legitimate alert, assigned for investigation.
     */
    ACKNOWLEDGED,

    /**
     * Investigating - actively investigating root cause.
     */
    INVESTIGATING,

    /**
     * Resolved - issue resolved, alert closed.
     */
    RESOLVED,

    /**
     * False positive - alert incorrectly triggered.
     */
    FALSE_POSITIVE
}
