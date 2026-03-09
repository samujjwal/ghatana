package com.ghatana.platform.testing.chaos;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test as a chaos test.
 *
 * <p>Chaos tests verify system resilience by injecting failures such as:
 * <ul>
 *   <li>Network latency and timeouts</li>
 *   <li>Service unavailability</li>
 *   <li>Resource exhaustion</li>
 *   <li>Data corruption</li>
 *   <li>Concurrent access violations</li>
 * </ul>
 *
 * @doc.type annotation
 * @doc.purpose Marks tests that verify system resilience under failure conditions
 * @doc.layer core
 * @doc.pattern Annotation
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("chaos")
@ExtendWith(ChaosTestExtension.class)
public @interface ChaosTest {

    /**
     * The type of chaos to inject.
     */
    ChaosType value() default ChaosType.RANDOM;

    /**
     * Probability of failure injection (0.0 to 1.0).
     */
    double failureProbability() default 0.5;

    /**
     * Maximum duration for chaos injection in milliseconds.
     */
    long maxDurationMs() default 5000;
}
