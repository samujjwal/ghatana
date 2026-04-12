/**
 * Shared Fixture Factory for Test Data
 *
 * Provides standardized test data fixtures for reuse across tests.
 *
 * @doc.type class
 * @doc.purpose Shared test fixture factory for platform modules
 * @doc.layer platform
 * @doc.pattern TestFixture
 */

package com.ghatana.platform.testing.fixtures;

import java.time.Instant;
import java.util.UUID;

/**
 * Factory for creating standardized test fixtures.
 * Provides consistent test data across all test suites.
 */
public final class FixtureFactory {

    private FixtureFactory() {}

    /** Generate a unique test ID */
    public static String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** Create a test tenant ID */
    public static String tenantId() {
        return "test-tenant-" + generateId();
    }

    /** Create a test user ID */
    public static String userId() {
        return "test-user-" + generateId();
    }

    /** Create a test timestamp */
    public static long timestamp() {
        return Instant.now().toEpochMilli();
    }

    /** Create test email */
    public static String email() {
        return "test-" + generateId() + "@example.com";
    }

    /** Create test correlation ID */
    public static String correlationId() {
        return "corr-" + generateId();
    }
}
