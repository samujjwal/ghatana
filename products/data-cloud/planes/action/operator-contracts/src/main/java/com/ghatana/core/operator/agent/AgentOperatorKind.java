package com.ghatana.core.operator.agent;

/**
 * Canonical AEP agent operator kinds.
 *
 * @doc.type enum
 * @doc.purpose Enumerates supported AgentOperator kinds for PatternSpec and PipelineSpec
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum AgentOperatorKind {
    AGENT_PREDICATE,
    AGENT_ENRICH,
    AGENT_EXTRACT,
    AGENT_PATTERN_SYNTHESIS,
    AGENT_EXPLANATION,
    AGENT_REVIEW,
    AGENT_ACTION,
    AGENT_REFLECTION
}
