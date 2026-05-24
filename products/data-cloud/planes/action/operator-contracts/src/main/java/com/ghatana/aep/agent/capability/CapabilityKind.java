package com.ghatana.aep.agent.capability;

/**
 * @doc.type enum
 * @doc.purpose Classifies agent capabilities without making agents inherit operator types
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum CapabilityKind {
    DETERMINISTIC,
    PROBABILISTIC,
    STREAM_PROCESSOR,
    PLANNING,
    EXTERNAL,
    HUMAN_REVIEW,
    EVENT_OPERATOR
}
