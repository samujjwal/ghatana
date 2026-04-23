/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.framework.repetition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RepetitionPolicy value type")
class RepetitionPolicyTest {

    @Nested
    @DisplayName("singleShot factory")
    class SingleShot {

        @Test
        @DisplayName("creates policy with correct defaults")
        void defaults() { // GH-90000
            var p = RepetitionPolicy.singleShot("p1");
            assertThat(p.policyId()).isEqualTo("p1");
            assertThat(p.maxIterations()).isEqualTo(1); // GH-90000
            assertThat(p.maxRetries()).isEqualTo(0); // GH-90000
            assertThat(p.retryStrategy()).isEqualTo(RetryStrategy.NONE); // GH-90000
            assertThat(p.terminationConditions()).contains(TerminationCondition.ON_SUCCESS); // GH-90000
        }
    }

    @Nested
    @DisplayName("iterative factory")
    class Iterative {

        @Test
        @DisplayName("creates policy with given iterations and retries")
        void iterations() { // GH-90000
            var p = RepetitionPolicy.iterative("p2", 5, 3, Duration.ofSeconds(2)); // GH-90000
            assertThat(p.maxIterations()).isEqualTo(5); // GH-90000
            assertThat(p.maxRetries()).isEqualTo(3); // GH-90000
            assertThat(p.retryStrategy()).isEqualTo(RetryStrategy.EXPONENTIAL_BACKOFF); // GH-90000
            assertThat(p.retryDelay()).isEqualTo(Duration.ofSeconds(2)); // GH-90000
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("empty terminationConditions throws")
        void emptyConditions() { // GH-90000
            assertThatThrownBy(() -> new RepetitionPolicy("p", 1, 1, 0, // GH-90000
                    RetryStrategy.NONE, Duration.ZERO, Set.of(), 0.0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("terminationConditions");
        }

        @Test
        @DisplayName("negative maxRetries throws")
        void negativeRetries() { // GH-90000
            assertThatThrownBy(() -> new RepetitionPolicy("p", 1, 1, -1, // GH-90000
                    RetryStrategy.NONE, Duration.ZERO,
                    Set.of(TerminationCondition.ON_SUCCESS), 0.0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("maxRecursionDepth below 1 throws")
        void recursionDepthMin() { // GH-90000
            assertThatThrownBy(() -> new RepetitionPolicy("p", 1, 0, 0, // GH-90000
                    RetryStrategy.NONE, Duration.ZERO,
                    Set.of(TerminationCondition.ON_SUCCESS), 0.0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("confidenceThreshold out of range throws")
        void confidenceOutOfRange() { // GH-90000
            assertThatThrownBy(() -> new RepetitionPolicy("p", 1, 1, 0, // GH-90000
                    RetryStrategy.NONE, Duration.ZERO,
                    Set.of(TerminationCondition.ON_SUCCESS), 1.5)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("terminationConditions are immutable")
        void conditionsImmutable() { // GH-90000
            var p = RepetitionPolicy.singleShot("p3");
            assertThatThrownBy(() -> p.terminationConditions().add(TerminationCondition.ON_TIMEOUT)) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }
}
