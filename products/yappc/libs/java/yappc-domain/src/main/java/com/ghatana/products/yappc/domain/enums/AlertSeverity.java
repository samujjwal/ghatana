package com.ghatana.products.yappc.domain.enums;

/**
 * Alert severity enumeration for security alert classification.
 *
 * <p><b>Purpose</b><br>
 * Defines severity levels for security alerts using standard CRITICAL-INFO scale.
 * Used to prioritize alert triage and response.
 *
 * <p><b>Severity Levels</b><br>
 * - CRITICAL: Immediate action required, potential active exploit
 * - HIGH: Urgent attention needed, significant risk
 * - MEDIUM: Moderate risk, investigate within SLA
 * - LOW: Minor concern, review when convenient
 * - INFO: Informational, no immediate action needed
 *
 * @see com.ghatana.products.yappc.domain.model.SecurityAlert
 * @doc.type enum
 * @doc.purpose Alert severity classification
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum AlertSeverity {
    /**
     * Critical severity - immediate action required.
     * Potential active exploit, data breach, or system compromise.
     */
    CRITICAL,

    /**
     * High severity - urgent attention needed.
     * Significant security risk requiring prompt investigation.
     */
    HIGH,

    /**
     * Medium severity - moderate risk.
     * Investigate within defined SLA timeframe.
     */
    MEDIUM,

    /**
     * Low severity - minor concern.
     * Review when convenient, low business impact.
     */
    LOW,

    /**
     * Informational - no immediate action.
     * Awareness notification, no direct threat.
     */
    INFO
}
