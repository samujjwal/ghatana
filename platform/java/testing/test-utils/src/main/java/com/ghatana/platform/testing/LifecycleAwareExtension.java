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

import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Extension that provides lifecycle awareness for tests.
 *
 * @doc.type class
 * @doc.purpose JUnit extension managing ActiveJ eventloop lifecycle
 * @doc.layer platform
 * @doc.pattern Service
 */
public class LifecycleAwareExtension implements Extension {
    
    public void beforeAll(ExtensionContext context) {
        // Lifecycle hook
    }
    
    public void afterAll(ExtensionContext context) {
        // Lifecycle hook
    }
}
