package com.ghatana.appplatform.compliance.domain;

/**
 * @doc.type    Enum
 * @doc.purpose Result status of a compliance check pipeline evaluation (D07-001).
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public enum ComplianceStatus {
    /** All rule checks passed — order may proceed. */
    PASS,
    /** One or more rules explicitly denied — order must be blocked. */
    FAIL,
    /** Rules flagged the order for manual compliance officer review. */
    REVIEW
}
