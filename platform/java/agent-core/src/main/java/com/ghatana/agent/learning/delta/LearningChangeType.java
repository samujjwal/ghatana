/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning.delta;

/**
 * Type of learning change represented by a delta.
 *
 * @doc.type enum
 * @doc.purpose Type of learning change
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum LearningChangeType {
    /**
     * Create a new learning artifact.
     */
    CREATE,

    /**
     * Update an existing learning artifact.
     */
    UPDATE,

    /**
     * Deprecate a learning artifact.
     */
    DEPRECATE,

    /**
     * Retire a learning artifact.
     */
    RETIRE,

    /**
     * Quarantine a learning artifact due to safety concerns.
     */
    QUARANTINE,

    /**
     * Merge multiple learning artifacts.
     */
    MERGE,

    /**
     * Split a learning artifact into multiple artifacts.
     */
    SPLIT
}
