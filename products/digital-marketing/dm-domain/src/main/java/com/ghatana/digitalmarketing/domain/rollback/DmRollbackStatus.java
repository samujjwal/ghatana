package com.ghatana.digitalmarketing.domain.rollback;

/**
 * Status of a rollback action.
 *
 * @doc.type class
 * @doc.purpose Lifecycle status for compensating rollback actions (DMOS-F2-014)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmRollbackStatus {
    PENDING,
    COMPLETED,
    FAILED,
    SKIPPED
}
