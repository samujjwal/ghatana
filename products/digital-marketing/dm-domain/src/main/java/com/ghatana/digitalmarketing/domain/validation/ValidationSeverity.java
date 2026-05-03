package com.ghatana.digitalmarketing.domain.validation;

/**
 * Severity level of a content validation finding.
 *
 * @doc.type enum
 * @doc.purpose DMOS content validation severity levels for compliance and brand checks
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum ValidationSeverity {

    /** The finding does not block approval but is informational. */
    INFO,

    /** The finding is a warning; human review is recommended before approval. */
    WARN,

    /** The finding blocks approval until remediated. */
    FAIL
}
