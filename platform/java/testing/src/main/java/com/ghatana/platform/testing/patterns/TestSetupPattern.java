/**
 * Standard Test Setup/Teardown Patterns
 *
 * Provides base classes and utilities for consistent test lifecycle management.
 *
 * @doc.type class
 * @doc.purpose Standardized test setup patterns
 * @doc.layer platform
 * @doc.pattern TestPattern
 */

package com.ghatana.platform.testing.patterns;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base pattern for test setup and teardown.
 * Provides consistent lifecycle hooks across all test types.
 */
public abstract class TestSetupPattern {

    @BeforeEach
    void baseSetUp() {
        initializeTestContext();
    }

    @AfterEach
    void baseTearDown() {
        cleanupTestContext();
    }

    /** Override to add custom initialization */
    protected abstract void initializeTestContext();

    /** Override to add custom cleanup */
    protected abstract void cleanupTestContext();
}
