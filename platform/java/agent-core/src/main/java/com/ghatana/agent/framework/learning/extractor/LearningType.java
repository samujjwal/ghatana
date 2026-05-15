/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.learning.extractor;

/**
 * Types of learning that can be extracted from episodes.
 * Phase 5 FIX: Typed learning types to replace heuristic synthesis.
 *
 * @doc.type enum
 * @doc.purpose Typed learning type enumeration
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum LearningType {
    /**
     * Semantic facts - stable knowledge about the world.
     */
    SEMANTIC_FACT,

    /**
     * Procedural skills - how to perform specific actions.
     */
    PROCEDURAL_SKILL,

    /**
     * Negative knowledge - what does not work or should be avoided.
     */
    NEGATIVE_KNOWLEDGE,

    /**
     * Failure modes - patterns of failure and their causes.
     */
    FAILURE_MODE
}
