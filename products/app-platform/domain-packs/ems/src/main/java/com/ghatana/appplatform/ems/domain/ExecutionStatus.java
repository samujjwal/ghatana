package com.ghatana.appplatform.ems.domain;

/**
 * @doc.type      Enum
 * @doc.purpose   Lifecycle state of an EMS execution order.
 * @doc.layer     Domain
 * @doc.pattern   State Machine Support
 */
public enum ExecutionStatus {
    PENDING_ROUTE,
    ROUTED,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED,
    EXPIRED
}
