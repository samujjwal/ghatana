/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

/**
 * Type of obsolescence signal indicating the cause of potential obsolescence.
 *
 * @doc.type enum
 * @doc.purpose Obsolescence signal type classification
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum ObsolescenceSignalType {
    /**
     * Version dependency mismatch detected.
     */
    VERSION_MISMATCH,

    /**
     * Performance degradation observed.
     */
    PERFORMANCE_DEGRADATION,

    /**
     * Semantic drift detected in task understanding.
     */
    SEMANTIC_DRIFT,

    /**
     * New version of the skill is available.
     */
    NEW_VERSION_AVAILABLE,

    /**
     * Manual deprecation request.
     */
    MANUAL_DEPRECATION,

    /**
     * Evaluation failure detected.
     */
    EVALUATION_FAILURE,

    /**
     * Security vulnerability detected.
     */
    SECURITY_VULNERABILITY,

    /**
     * Lack of recent usage.
     */
    LACK_OF_USAGE,

    /**
     * Environmental incompatibility.
     */
    ENVIRONMENTAL_INCOMPATIBILITY
}
