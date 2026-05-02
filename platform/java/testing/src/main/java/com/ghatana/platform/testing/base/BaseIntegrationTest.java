/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.platform.testing.base;

import com.ghatana.platform.testing.BaseTest;

/**
 * Base integration test class for product-level integration tests.
 *
 * <p>Provides a lightweight foundation for integration tests that don't require
 * external infrastructure (databases, containers, etc.). Extends {@link BaseTest}
 * to inherit lifecycle logging and test infrastructure.
 *
 * <p>For tests requiring PostgreSQL, Redis, or other external dependencies,
 * use {@link com.ghatana.platform.testing.PlatformIntegrationTestBase} instead.
 *
 * @doc.type class
 * @doc.purpose Lightweight integration test base for product tests without external infrastructure
 * @doc.layer platform
 * @doc.pattern Base Class
 */
public abstract class BaseIntegrationTest extends BaseTest {
    // Inherits all lifecycle and logging infrastructure from BaseTest
    // No additional infrastructure needed for these simple integration tests
}
