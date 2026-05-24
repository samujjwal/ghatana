package com.ghatana.core.operator.agent;

/**
 * Side-effect classification for agent operators.
 *
 * @doc.type enum
 * @doc.purpose Defines side-effect levels used by AgentOperator governance and replay policy
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum AgentSideEffectProfile {
    PURE_INFERENCE,
    READ_ONLY_TOOL_USE,
    PROPOSE_ACTION,
    SIDE_EFFECTING
}
