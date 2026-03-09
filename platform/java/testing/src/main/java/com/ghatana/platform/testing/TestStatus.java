package com.ghatana.platform.testing;

/**
 * Canonical status values for tests across the platform.
 * Consolidates lifecycle and result-oriented states used by various modules.
 * 
 * @doc.type enum
 * @doc.purpose Canonical test lifecycle states for test execution tracking
 * @doc.layer core
 * @doc.pattern Enumeration, Status Tracker
 */
public enum TestStatus {
    /**
     * Test has been registered but not started.
     */
    PENDING,
    /**
     * Test is preparing required resources.
     */
    INITIALIZING,
    /**
     * Test is actively running.
     */
    RUNNING,
    /**
     * Test completed execution (final success/failure captured separately).
     */
    COMPLETED,
    /**
     * Test satisfied all validation criteria.
     */
    PASSED,
    /**
     * Test finished but did not meet expected criteria.
     */
    FAILED,
    /**
     * Test execution encountered unrecoverable errors.
     */
    ERROR,
    /**
     * Test run was skipped prior to execution.
     */
    SKIPPED,
    /**
     * Test was cancelled by an operator or orchestration logic.
     */
    CANCELLED,
    /**
     * Test exceeded the configured execution deadline.
     */
    TIMEOUT
}
