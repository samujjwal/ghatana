package com.ghatana.appplatform.oms.domain;

/**
 * @doc.type    Enum
 * @doc.purpose 9-state order lifecycle per D01-004.
 * @doc.layer   Domain
 * @doc.pattern State Machine States
 *
 * <p>Allowed transitions:
 * <pre>
 *   DRAFT            → PENDING
 *   PENDING          → PENDING_APPROVAL | APPROVED | REJECTED
 *   PENDING_APPROVAL → APPROVED | REJECTED
 *   APPROVED         → ROUTED | REJECTED
 *   ROUTED           → PARTIALLY_FILLED | FILLED | CANCELLED | REJECTED
 *   PARTIALLY_FILLED → FILLED | CANCELLED
 *   FILLED           → (terminal)
 *   CANCELLED        → (terminal)
 *   REJECTED         → (terminal)
 * </pre>
 */
public enum OrderStatus {
    DRAFT,
    PENDING,
    PENDING_APPROVAL,
    APPROVED,
    ROUTED,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED;

    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED;
    }
}
