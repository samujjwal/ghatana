/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

/**
 * Type of learning delta representing the kind of change being proposed.
 *
 * @doc.type enum
 * @doc.purpose Type of learning delta
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum LearningDeltaType {
    /**
     * New procedural skill being proposed.
     */
    PROCEDURAL_SKILL,

    /**
     * New semantic fact being proposed.
     */
    SEMANTIC_FACT,

    /**
     * New negative knowledge being proposed.
     */
    NEGATIVE_KNOWLEDGE,

    /**
     * Modification to retrieval policy.
     */
    RETRIEVAL_POLICY,

    /**
     * Modification to routing policy.
     */
    ROUTING_POLICY,

    /**
     * Modification to prompt template.
     */
    PROMPT_TEMPLATE,

    /**
     * Modification to planner policy.
     */
    PLANNER_POLICY,

    /**
     * Modification to model adapter.
     */
    MODEL_ADAPTER,

    /**
     * Modification to confidence threshold.
     */
    CONFIDENCE_THRESHOLD,

    /**
     * Modification to mastery state.
     */
    MASTERY_STATE
}
