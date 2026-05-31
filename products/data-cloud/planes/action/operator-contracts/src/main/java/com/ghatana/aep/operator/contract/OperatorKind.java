package com.ghatana.aep.operator.contract;

/**
 * @doc.type enum
 * @doc.purpose Enumerates standard and agent AEP operator kinds for PatternSpec and PipelineSpec
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum OperatorKind {
    // Standard pattern operators
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
    SOURCE,           // P4-02: Source/Input operator
    SINK,             // P4-02: Sink/Output operator
    ENRICH,           // P4-02: Enrichment operator
    AGGREGATE,        // P4-02: Aggregation operator
    CUSTOM,           // P4-02: Custom operator kind

    // Learning and AI operators
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
