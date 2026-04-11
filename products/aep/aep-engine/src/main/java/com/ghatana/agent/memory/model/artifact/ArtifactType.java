package com.ghatana.agent.memory.model.artifact;

/**
 * Enumerates the types of typed artifacts stored in memory.
 *
 * @doc.type enum
 * @doc.purpose Artifact type classification
 * @doc.layer agent-memory
 */
public enum ArtifactType {

    /** A decision made by the agent with rationale and alternatives. */
    DECISION,

    /** A tool invocation with input, output, and timing. */
    TOOL_USE,

    /** An observation extracted from the environment. */
    OBSERVATION,

    /** An error encountered during execution. */
    ERROR,

    /** A lesson learned from experience. */
    LESSON,

    /** A named entity extracted from context. */
    ENTITY,

    /** A plan or strategy formulated by the agent. */
    PLAN,

    /** A hypothesis to be tested. */
    HYPOTHESIS
}
