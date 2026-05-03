package com.ghatana.digitalmarketing.domain.preflight;

/**
 * Overall preflight evaluation status.
 *
 * @doc.type class
 * @doc.purpose Indicates whether campaign passed preflight checks (DMOS-F2-013)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmPreflightStatus {
    PENDING,
    PASSED,
    BLOCKED,
    WARNING
}
