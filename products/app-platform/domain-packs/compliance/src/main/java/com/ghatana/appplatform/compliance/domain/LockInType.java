package com.ghatana.appplatform.compliance.domain;

/**
 * @doc.type    Enum
 * @doc.purpose Reason for a lock-in period on shares.
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public enum LockInType {
    /** Promoter shares locked 3 years from allotment. */
    PROMOTER,
    /** IPO shares locked per SEBON circular. */
    IPO,
    /** Bonus shares — may have lock-in per company resolution. */
    BONUS,
    /** Right shares — specific lock-in per rights prospectus. */
    RIGHTS
}
