/*
 * Copyright (c) 2026 Ghatana Inc. 
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
        void defaults() { 
            var p = RepetitionPolicy.singleShot("p1");
            assertThat(p.policyId()).isEqualTo("p1");
            assertThat(p.maxIterations()).isEqualTo(1); 
            assertThat(p.maxRetries()).isEqualTo(0); 
            assertThat(p.retryStrategy()).isEqualTo(RetryStrategy.NONE); 
            assertThat(p.terminationConditions()).contains(TerminationCondition.ON_SUCCESS); 
        }
    }

    @Nested
    @DisplayName("iterative factory")
    class Iterative {

        @Test
        @DisplayName("creates policy with given iterations and retries")
        void iterations() { 
            var p = RepetitionPolicy.iterative("p2", 5, 3, Duration.ofSeconds(2)); 
            assertThat(p.maxIterations()).isEqualTo(5); 
            assertThat(p.maxRetries()).isEqualTo(3); 
            assertThat(p.retryStrategy()).isEqualTo(RetryStrategy.EXPONENTIAL_BACKOFF); 
            assertThat(p.retryDelay()).isEqualTo(Duration.ofSeconds(2)); 
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("empty terminationConditions throws")
        void emptyConditions() { 
            assertThatThrownBy(() -> new RepetitionPolicy("p", 1, 1, 0, 
                    RetryStrategy.NONE, Duration.ZERO, Set.of(), 0.0)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("terminationConditions");
        }

        @Test
        @DisplayName("negative maxRetries throws")
        void negativeRetries() { 
            assertThatThrownBy(() -> new RepetitionPolicy("p", 1, 1, -1, 
                    RetryStrategy.NONE, Duration.ZERO,
                    Set.of(TerminationCondition.ON_SUCCESS), 0.0)) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("maxRecursionDepth below 1 throws")
        void recursionDepthMin() { 
            assertThatThrownBy(() -> new RepetitionPolicy("p", 1, 0, 0, 
                    RetryStrategy.NONE, Duration.ZERO,
                    Set.of(TerminationCondition.ON_SUCCESS), 0.0)) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("confidenceThreshold out of range throws")
        void confidenceOutOfRange() { 
            assertThatThrownBy(() -> new RepetitionPolicy("p", 1, 1, 0, 
                    RetryStrategy.NONE, Duration.ZERO,
                    Set.of(TerminationCondition.ON_SUCCESS), 1.5)) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("terminationConditions are immutable")
        void conditionsImmutable() { 
            var p = RepetitionPolicy.singleShot("p3");
            assertThatThrownBy(() -> p.terminationConditions().add(TerminationCondition.ON_TIMEOUT)) 
                    .isInstanceOf(UnsupportedOperationException.class); 
        }
    }
}
