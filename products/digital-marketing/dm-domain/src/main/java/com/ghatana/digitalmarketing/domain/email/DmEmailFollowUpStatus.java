package com.ghatana.digitalmarketing.domain.email;

/**
 * Status for email follow-up execution.
 *
 * @doc.type class
 * @doc.purpose Tracks email follow-up lifecycle (DMOS-F2-012)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmEmailFollowUpStatus {
    PENDING,
    SENT,
    FAILED,
    CANCELLED
}
