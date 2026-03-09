/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: D
 * OWNER: @infra-team
 * MIGRATED: 2026-02-04
 * DEPENDS_ON: platform:java:testing
 */
package com.ghatana.platform.testing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Base test class providing standardized test lifecycle and logging infrastructure.
 *
 * @doc.type class
 * @doc.purpose Abstract base test class with standardized lifecycle hooks and logging
 * @doc.layer platform
 * @doc.pattern Utility
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(LifecycleAwareExtension.class)
public abstract class BaseTest {

    @BeforeEach
    void baseBeforeEach(TestInfo testInfo) {
        // Lifecycle hook for subclasses
    }

    @AfterEach
    void baseAfterEach(TestInfo testInfo) {
        // Lifecycle hook for subclasses
    }
}
