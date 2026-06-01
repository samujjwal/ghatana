package com.ghatana.aep.pattern.lifecycle;

/**
 * @doc.type enum
 * @doc.purpose Enumerates canonical PatternSpec lifecycle states
 * @doc.layer product
 * @doc.pattern Enumeration
 *
 * <p>WS2: Complete lifecycle states for pattern management including
 * validation, activation, deprecation, and archival phases.
 */
public enum PatternLifecycleState {
    DRAFT,           // Initial pattern definition
    VALIDATING,      // Undergoing validation
    CANDIDATE,       // Validated candidate for deployment
    VALIDATED,       // Fully validated
    SHADOW,          // Running in shadow mode
    RECOMMENDED,     // Recommended for activation
    APPROVED,        // Approved for production
    ACTIVE,          // Actively detecting events
    PAUSED,          // Temporarily paused
    DEGRADED,        // Running with degraded performance
    SUPERSEDED,      // Replaced by newer version
    FAILED,          // Failed validation or execution
    ARCHIVED,        // Archived and no longer active
    RETIRED          // Retired from service
}
