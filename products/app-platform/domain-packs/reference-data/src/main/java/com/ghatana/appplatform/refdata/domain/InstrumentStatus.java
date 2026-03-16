package com.ghatana.appplatform.refdata.domain;

/**
 * @doc.type       Enum
 * @doc.purpose    Lifecycle state of an instrument in the reference data master.
 *                 Drives pre-trade validation: only ACTIVE instruments may be traded.
 * @doc.layer      Domain
 * @doc.pattern    State Machine (allowed transitions in InstrumentLifecycleService)
 */
public enum InstrumentStatus {
    PENDING_APPROVAL,
    ACTIVE,
    SUSPENDED,
    DELISTED
}
