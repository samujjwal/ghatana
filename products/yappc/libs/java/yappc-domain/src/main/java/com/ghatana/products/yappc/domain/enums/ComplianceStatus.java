package com.ghatana.products.yappc.domain.enums;

/**
 * Compliance status enumeration for assessment tracking.
 *
 * <p><b>Purpose</b><br>
 * Defines compliance assessment status for regulatory frameworks and policies.
 *
 * <p><b>Status Levels</b><br>
 * - COMPLIANT: Meets all requirements
 * - PARTIALLY_COMPLIANT: Some requirements met, others in progress
 * - NON_COMPLIANT: Failing requirements, remediation needed
 * - NOT_ASSESSED: No assessment performed yet
 *
 * @see com.ghatana.products.yappc.domain.model.ComplianceAssessment
 * @doc.type enum
 * @doc.purpose Compliance assessment status
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum ComplianceStatus {
    /**
     * Compliant - all requirements met.
     */
    COMPLIANT,

    /**
     * Partially compliant - some requirements met.
     */
    PARTIALLY_COMPLIANT,

    /**
     * Non-compliant - failing requirements.
     */
    NON_COMPLIANT,

    /**
     * Not assessed - no evaluation performed.
     */
    NOT_ASSESSED
}
