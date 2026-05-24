package com.ghatana.aep.operator.contract;

/**
 * @doc.type enum
 * @doc.purpose Enumerates standard and agent AEP operator kinds for PatternSpec and PipelineSpec
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum OperatorKind {
    EVENT_REF,
    AND,
    OR,
    NOT,
    SEQ,
    WITHIN,
    TIMES,
    REPEAT,
    WINDOW,
    ABSENCE,
    FILTER,
    TRANSFORM,
    LEARNING,
    AGENT_PREDICATE,
    AGENT_ENRICH,
    AGENT_EXTRACT,
    AGENT_PATTERN_SYNTHESIS,
    AGENT_EXPLANATION,
    AGENT_REVIEW,
    AGENT_ACTION,
    AGENT_REFLECTION
}
