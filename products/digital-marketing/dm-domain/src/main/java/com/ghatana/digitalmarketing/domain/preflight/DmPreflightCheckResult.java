package com.ghatana.digitalmarketing.domain.preflight;

/**
 * Result of an individual preflight check.
 *
 * @doc.type class
 * @doc.purpose Indicates pass/fail/warning for a single preflight item (DMOS-F2-013)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmPreflightCheckResult {
    PASSED,
    FAILED,
    WARNING,
    SKIPPED
}
