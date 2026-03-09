package com.ghatana.platform.testing;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation for unit tests.
 * 
 * <p>Tests annotated with {@code @UnitTest} will be tagged with "unit" and can be selectively run.
 * 
 * <p>Example usage:
 * <pre>{@code
 * @UnitTest
 * class MyUnitTest {
 *     // test methods
 * }
 * }</pre>
 * 
 * @doc.type annotation
 * @doc.purpose Meta-annotation for marking and filtering unit tests
 * @doc.layer core
 * @doc.pattern Annotation, Test Marker
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("unit")
public @interface UnitTest {
}
