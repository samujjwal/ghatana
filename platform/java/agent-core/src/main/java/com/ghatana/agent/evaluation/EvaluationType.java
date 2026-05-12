/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

/**
 * Type of evaluation test.
 *
 * @doc.type enum
 * @doc.purpose Type of evaluation test
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum EvaluationType {
    /**
     * Regression test - verifies behavior matches expected output.
     */
    REGRESSION,

    /**
     * Safety test - verifies no unsafe actions or side effects.
     */
    SAFETY,

    /**
     * Recovery test - verifies ability to recover from failures.
     */
    RECOVERY,

    /**
     * Compatibility test - verifies compatibility with target environment.
     */
    COMPATIBILITY,

    /**
     * Transferability test - verifies skill transfers to similar contexts.
     */
    TRANSFERABILITY,

    /**
     * Performance test - verifies performance meets thresholds.
     */
    PERFORMANCE,

    /**
     * Security test - verifies no security vulnerabilities.
     */
    SECURITY
}
