package com.ghatana.core.operator.agent;

/**
 * Canonical AEP agent capability roles.
 *
 * @doc.type enum
 * @doc.purpose Enumerates supported agent capability roles for PatternSpec and PipelineSpec
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum AgentCapabilityRole {
    AGENT_PREDICATE,
    AGENT_ENRICH,
    AGENT_EXTRACT,
    AGENT_PATTERN_SYNTHESIS,
    AGENT_EXPLANATION,
    AGENT_REVIEW,
    AGENT_ACTION,
    AGENT_REFLECTION
}
