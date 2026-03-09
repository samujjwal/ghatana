package com.ghatana.products.yappc.domain.enums;

/**
 * Incident severity enumeration for security incident classification.
 *
 * <p><b>Purpose</b><br>
 * Defines severity levels for security incidents following industry-standard SEV1-SEV4 classification.
 * Higher severity (SEV1) indicates critical incidents requiring immediate response.
 *
 * <p><b>Severity Levels</b><br>
 * - SEV1: Critical - Complete service outage or major security breach
 * - SEV2: High - Partial service degradation or significant security risk
 * - SEV3: Medium - Minor service impact or moderate security concern
 * - SEV4: Low - Minimal impact, informational security finding
 *
 * <p><b>Usage</b><br>
 * Used in Incident entity to prioritize response and escalation.
 *
 * @see com.ghatana.products.yappc.domain.model.Incident
 * @doc.type enum
 * @doc.purpose Incident severity classification
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum IncidentSeverity {
    /**
     * SEV1 - Critical severity.
     * Complete service outage, major security breach, data loss.
     * Response: Immediate 24/7, all hands on deck.
     */
    SEV1,

    /**
     * SEV2 - High severity.
     * Partial service degradation, significant security risk.
     * Response: Urgent, within hours.
     */
    SEV2,

    /**
     * SEV3 - Medium severity.
     * Minor service impact, moderate security concern.
     * Response: Next business day.
     */
    SEV3,

    /**
     * SEV4 - Low severity.
     * Minimal impact, informational finding.
     * Response: Scheduled maintenance window.
     */
    SEV4
}
