package com.ghatana.aep.pattern.lifecycle;

/**
 * @doc.type enum
 * @doc.purpose Enumerates canonical PatternSpec lifecycle states
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum PatternLifecycleState {
    DRAFT,
    CANDIDATE,
    VALIDATED,
    SHADOW,
    RECOMMENDED,
    APPROVED,
    ACTIVE,
    DEGRADED,
    RETIRED
}
