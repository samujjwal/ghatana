/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

/**
 * @doc.type enum
 * @doc.purpose Represents the state of a learning candidate through the evaluation pipeline
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum LearningCandidateState {
    PROPOSED,
    EVALUATING,
    REJECTED,
    APPROVED,
    PROMOTED
}
