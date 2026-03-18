package com.ghatana.platform.testing.contract;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test method as a cross-module contract verifier.
 *
 * <p>Apply this annotation in addition to (or instead of) {@code @Test} on any
 * test that verifies a boundary contract between two platform or product modules.
 * The {@code producerModule} and {@code consumerModule} attributes document exactly
 * which boundary is under test, making the contract inventory machine-readable.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @ContractTest(
 *     producerModule = "platform:java:event-cloud",
 *     consumerModule = "products:data-cloud"
 * )
 * void appendedEventIsVisible_fromConsumer() { ... }
 * }</pre>
 *
 * <h2>CI integration</h2>
 * <p>All methods carrying this annotation are automatically tagged {@code "contract"}
 * (via {@link PlatformContractTestBase}), so they can be run in isolation:
 * <pre>{@code ./gradlew test -Ptest.tags=contract}</pre>
 *
 * @doc.type interface
 * @doc.purpose Annotation that identifies and documents a cross-module contract test
 * @doc.layer platform
 * @doc.pattern Annotation, Contract
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Test
public @interface ContractTest {

    /**
     * Gradle module path of the component that PRODUCES the API under test.
     * Example: {@code "platform:java:event-cloud"}.
     */
    String producerModule();

    /**
     * Gradle module path of the component that CONSUMES the API under test.
     * Example: {@code "products:data-cloud"}.
     */
    String consumerModule();

    /**
     * Optional human-readable description of the contract being verified.
     * Defaults to empty (test method name is used as the description).
     */
    String description() default "";
}
