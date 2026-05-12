/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

/**
 * @doc.type enum
 * @doc.purpose Types of learning targets an agent may propose changes for
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum LearningTarget {
    EPISODIC_MEMORY,
    SEMANTIC_FACT,
    PROCEDURAL_SKILL,
    NEGATIVE_KNOWLEDGE,
    RETRIEVAL_POLICY,
    CONFIDENCE_THRESHOLD,
    ROUTING_POLICY,
    PROMPT_TEMPLATE,
    PLANNER_POLICY,
    MODEL_ADAPTER,
    MASTERY_STATE;

    /**
     * Returns true if this target is a memory-related target.
     */
    public boolean isMemoryTarget() {
        return this == EPISODIC_MEMORY
                || this == SEMANTIC_FACT
                || this == PROCEDURAL_SKILL
                || this == NEGATIVE_KNOWLEDGE;
    }

    /**
     * Returns true if this target is a policy-related target.
     */
    public boolean isPolicyTarget() {
        return this == RETRIEVAL_POLICY
                || this == CONFIDENCE_THRESHOLD
                || this == ROUTING_POLICY
                || this == PROMPT_TEMPLATE
                || this == PLANNER_POLICY;
    }

    /**
     * Returns true if this target affects execution behavior.
     */
    public boolean isExecutionTarget() {
        return this == PROCEDURAL_SKILL
                || this == MODEL_ADAPTER
                || this == PLANNER_POLICY;
    }

    /**
     * Returns true if this target is only allowed in offline mode.
     * MODEL_ADAPTER and potentially future high-risk targets are offline-only.
     */
    public boolean isOfflineOnlyTarget() {
        return this == MODEL_ADAPTER;
    }
}
