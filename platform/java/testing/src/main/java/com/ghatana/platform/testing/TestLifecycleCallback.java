package com.ghatana.platform.testing;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Contract for structured lifecycle callbacks that can be wired via lifecycle-aware extensions.
 
 *
 * @doc.type interface
 * @doc.purpose Test lifecycle callback
 * @doc.layer core
 * @doc.pattern Interface
*/
public interface TestLifecycleCallback {

    /**
     * Invoked before the first test method in the current test class executes.
     *
     * @param context the current extension context
     */
    default void beforeAll(ExtensionContext context) {}

    /**
     * Invoked after the last test method in the current test class executes.
     *
     * @param context the current extension context
     */
    default void afterAll(ExtensionContext context) {}

    /**
     * Invoked before each test method executes.
     *
     * @param context the current extension context
     */
    default void beforeEach(ExtensionContext context) {}

    /**
     * Invoked after each test method executes.
     *
     * @param context the current extension context
     */
    default void afterEach(ExtensionContext context) {}
}
