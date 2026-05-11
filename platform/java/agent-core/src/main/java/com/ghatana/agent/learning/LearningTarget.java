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
    MODEL_ADAPTER
}
