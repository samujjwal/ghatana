/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.governance;

/**
 * Outcome of a governance policy evaluation for an {@link ActionIntent}.
 *
 * @doc.type enum
 * @doc.purpose Classifies policy evaluation outcomes
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum PolicyDecisionType {

    /** Action is permitted without conditions. */
    ALLOW,

    /** Action is denied — must not proceed. */
    DENY,

    /** Action requires escalation to a higher authority for decision. */
    ESCALATE,

    /** Action is allowed only after explicit human or role-based approval. */
    ALLOW_WITH_APPROVAL,

    /** Action is allowed but requires a compensation plan to be registered. */
    ALLOW_WITH_COMPENSATION,

    /** Action is allowed but requires enhanced monitoring/audit mode. */
    ALLOW_WITH_MONITORING;

    /** Returns {@code true} if the action may proceed (possibly with conditions). */
    public boolean isPermitted() {
        return this != DENY && this != ESCALATE;
    }

    /** Returns {@code true} if the action requires additional obligations before proceeding. */
    public boolean hasObligations() {
        return this == ALLOW_WITH_APPROVAL || this == ALLOW_WITH_COMPENSATION
                || this == ALLOW_WITH_MONITORING;
    }
}
