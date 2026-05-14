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
     * Skill unit test - verifies individual skill components work correctly.
     */
    SKILL_UNIT,

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
    SECURITY,

    /**
     * Abstention test - verifies ability to abstain appropriately, including refusal.
     */
    ABSTENTION,

    /**
     * Integration test - verifies end-to-end behavior across component boundaries.
     */
    INTEGRATION,

    /**
     * Version compatibility test - verifies the skill works correctly for a specific version constraint.
     */
    VERSION_COMPATIBILITY,

    /**
     * Retrieval quality test - verifies memory/knowledge retrieval produces correct results.
     */
    RETRIEVAL_QUALITY,

    /**
     * Prompt and tool injection test - verifies resistance to injection attacks.
     */
    PROMPT_INJECTION,

    /**
     * Output contract test - verifies output matches expected schema and constraints.
     */
    OUTPUT_CONTRACT,

    /**
     * Trace grade test - verifies trace quality and completeness.
     */
    TRACE_GRADE,

    /**
     * Rollback/recovery test - verifies correct state restoration after failure.
     */
    ROLLBACK_RECOVERY
}
