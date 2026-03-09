package com.ghatana.platform.testing;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark integration tests that require external dependencies
 * such as databases, message queues, or other services.
 * <p>
 * Integration tests are tagged separately from unit tests to allow selective execution.
 * They typically run slower and may require special setup.
 * 
 * @doc.type annotation
 * @doc.purpose Meta-annotation for marking and filtering integration tests
 * @doc.layer core
 * @doc.pattern Annotation, Test Marker
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Test
@Tag("integration")
public @interface IntegrationTest {
}
