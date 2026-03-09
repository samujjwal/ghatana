package com.ghatana.platform.testing;

import org.junit.jupiter.api.Tag;
import java.lang.annotation.*;

/**
 * Test category annotations for different types of tests.
 * 
 * @doc.type class
 * @doc.purpose Container for test category annotations (unit, integration, performance)
 * @doc.layer core
 * @doc.pattern Annotation, Category Holder
 */
public class TestCategories {
    /**
     * Marks a test as a unit test.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("unit")
    public @interface UnitTest {}

    /**
     * Marks a test as an integration test.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("integration")
    public @interface IntegrationTest {}

    /**
     * Marks a test as a performance test.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("performance")
    public @interface PerformanceTest {}

    /**
     * Marks a test as a chaos test (resilience testing).
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("chaos")
    public @interface ChaosTest {}

    /**
     * Marks a test as an end-to-end test.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("e2e")
    public @interface E2ETest {}
}
